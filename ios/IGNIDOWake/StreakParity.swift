import SwiftUI

@MainActor
final class StreakParityStore: ObservableObject {
    static let maxProtection = 3
    static let monthlyGrant = 2

    @Published private(set) var balance = 0
    @Published var autoProtection = true { didSet { if !loading { save() } } }
    @Published private(set) var protectedDates: Set<String> = []
    @Published private(set) var growthLevel: Int = 1
    @Published private(set) var storedStreak: Int = 0
    @Published private(set) var bestStreak: Int = 0
    @Published private(set) var totalWakeups: Int = 0

    private let defaults = UserDefaults.standard
    private let prefix = "ignido.streak.parity.v1."
    private let legacyDatesKey = "ignido.streak.dates.v2"
    private var loading = false

    init() { loadAndRefresh() }

    var growthDescriptor: String {
        switch growthLevel {
        case ..<4: return "火種"
        case 4..<12: return "小さな炎"
        case 12..<30: return "炎竜の兆し"
        case 30..<80: return "幼炎竜"
        case 80..<180: return "炎竜"
        case 180..<400: return "天炎竜"
        case 400..<1000: return "恒星炎竜"
        default: return "無限炎竜"
        }
    }

    var visualPower: Double { log1p(Double(max(1, growthLevel))) / log(2.0) }

    func reload() { loadAndRefresh() }

    func recordWake(alarms: [WakeAlarm]) {
        refreshMonthly()
        var dates = wakeDates()
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        if dates.contains(where: { calendar.isDate($0, inSameDayAs: today) }) { return }

        let last = dates.max()
        var continues = false
        if let last {
            let missed = scheduledMissedDays(after: last, before: today, alarms: alarms, successes: dates)
            if missed.isEmpty { continues = true }
            else if autoProtection && balance >= missed.count {
                for d in missed { protectedDates.insert(dayKey(d)) }
                balance -= missed.count
                continues = true
            }
        }

        dates.append(today)
        writeWakeDates(dates)
        storedStreak = continues ? max(1, storedStreak) + 1 : 1
        bestStreak = max(bestStreak, storedStreak)
        totalWakeups += 1
        growthLevel = min(Int.max - 1, max(1, growthLevel) + 1)

        let milestone = storedStreak / 30
        let awarded = defaults.integer(forKey: prefix + "bonusMilestone")
        if milestone > awarded {
            balance = min(Self.maxProtection, balance + 1)
            defaults.set(milestone, forKey: prefix + "bonusMilestone")
        }
        save()
    }

    func displayCurrent(alarms: [WakeAlarm]) -> Int {
        refreshMonthly()
        guard storedStreak > 0, let last = wakeDates().max() else { return 0 }
        let today = Calendar.current.startOfDay(for: Date())
        let missed = scheduledMissedDays(after: last, through: today, alarms: alarms, successes: wakeDates())
        guard !missed.isEmpty else { return storedStreak }
        if autoProtection && balance >= missed.count {
            for d in missed { protectedDates.insert(dayKey(d)) }
            balance -= missed.count
            save()
            return storedStreak
        }
        return 0
    }

    func isProtected(_ date: Date) -> Bool { protectedDates.contains(dayKey(date)) }
    func isSuccessful(_ date: Date) -> Bool { wakeDates().contains { Calendar.current.isDate($0, inSameDayAs: date) } }

    func monthStats(monthDate: Date, alarms: [WakeAlarm]) -> (wins: Int, losses: Int, pending: Int, protected: Int) {
        let cal = Calendar.current
        guard let interval = cal.dateInterval(of: .month, for: monthDate) else { return (0,0,0,0) }
        let now = Date()
        var wins=0, losses=0, pending=0, protected=0
        var day = interval.start
        while day < interval.end {
            if isSuccessful(day) { wins += 1 }
            else if isProtected(day) { protected += 1 }
            else if isScheduled(day, alarms: alarms) {
                let due = earliestDue(on: day, alarms: alarms) ?? cal.date(bySettingHour: 7, minute: 0, second: 0, of: day)!
                if due < now { losses += 1 } else { pending += 1 }
            }
            guard let next = cal.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
        }
        return (wins,losses,pending,protected)
    }

    func resetProtectionForDebug() {
        balance=0; protectedDates=[]; defaults.set(0,forKey:prefix+"bonusMilestone"); save()
    }

    private func loadAndRefresh() {
        loading=true
        if !defaults.bool(forKey: prefix+"initialized") {
            defaults.set(true,forKey:prefix+"initialized")
            defaults.set(0,forKey:prefix+"balance")
            defaults.set(true,forKey:prefix+"auto")
            defaults.set(monthKey(Date()),forKey:prefix+"month")
            defaults.set(Date().timeIntervalSince1970,forKey:prefix+"start")
            let legacyCount = wakeDates().count
            defaults.set(max(1,legacyCount+1),forKey:prefix+"growth")
            defaults.set(legacyCount,forKey:prefix+"total")
            defaults.set(legacyContiguousStreak(),forKey:prefix+"streak")
            defaults.set(legacyContiguousStreak(),forKey:prefix+"best")
        }
        balance=min(Self.maxProtection,max(0,defaults.integer(forKey:prefix+"balance")))
        autoProtection=defaults.object(forKey:prefix+"auto") as? Bool ?? true
        protectedDates=Set(defaults.stringArray(forKey:prefix+"protected") ?? [])
        growthLevel=max(1,defaults.integer(forKey:prefix+"growth"))
        storedStreak=max(0,defaults.integer(forKey:prefix+"streak"))
        bestStreak=max(storedStreak,defaults.integer(forKey:prefix+"best"))
        totalWakeups=max(wakeDates().count,defaults.integer(forKey:prefix+"total"))
        loading=false
        refreshMonthly()
    }

    private func save() {
        defaults.set(balance,forKey:prefix+"balance")
        defaults.set(autoProtection,forKey:prefix+"auto")
        defaults.set(Array(protectedDates).sorted(),forKey:prefix+"protected")
        defaults.set(growthLevel,forKey:prefix+"growth")
        defaults.set(storedStreak,forKey:prefix+"streak")
        defaults.set(bestStreak,forKey:prefix+"best")
        defaults.set(totalWakeups,forKey:prefix+"total")
    }

    private func refreshMonthly() {
        let current=monthKey(Date())
        let old=defaults.string(forKey:prefix+"month") ?? current
        guard old != current else { return }
        let f=DateFormatter(); f.dateFormat="yyyy-MM"; f.locale=Locale(identifier:"en_US_POSIX"); f.timeZone=.current
        if let oldDate=f.date(from:old), let newDate=f.date(from:current) {
            let months=max(0,Calendar.current.dateComponents([.month],from:oldDate,to:newDate).month ?? 0)
            if months>0 { balance=min(Self.maxProtection,balance+months*Self.monthlyGrant) }
        }
        defaults.set(current,forKey:prefix+"month")
        save()
    }

    private func wakeDates() -> [Date] {
        guard let data=defaults.data(forKey:legacyDatesKey), let dates=try? JSONDecoder().decode([Date].self,from:data) else{return []}
        return dates.map{Calendar.current.startOfDay(for:$0)}
    }
    private func writeWakeDates(_ dates:[Date]) { if let data=try? JSONEncoder().encode(dates){defaults.set(data,forKey:legacyDatesKey)} }
    private func dayKey(_ d:Date)->String { let f=DateFormatter();f.calendar=Calendar(identifier:.gregorian);f.locale=Locale(identifier:"en_US_POSIX");f.timeZone=.current;f.dateFormat="yyyy-MM-dd";return f.string(from:d) }
    private func monthKey(_ d:Date)->String { let f=DateFormatter();f.locale=Locale(identifier:"en_US_POSIX");f.timeZone=.current;f.dateFormat="yyyy-MM";return f.string(from:d) }

    private func legacyContiguousStreak()->Int {
        let cal=Calendar.current;let set=Set(wakeDates().map{cal.startOfDay(for:$0)});var cursor=cal.startOfDay(for:Date());if !set.contains(cursor),let y=cal.date(byAdding:.day,value:-1,to:cursor),set.contains(y){cursor=y};var count=0;while set.contains(cursor){count+=1;guard let p=cal.date(byAdding:.day,value:-1,to:cursor)else{break};cursor=p};return count
    }

    private func userWeekday(_ date: Date) -> Int {
        let apple=Calendar.current.component(.weekday,from:date)
        return apple==1 ? 7 : apple-1
    }

    private func isScheduled(_ date: Date, alarms: [WakeAlarm]) -> Bool {
        let day=userWeekday(date)
        return alarms.contains { a in a.enabled && !a.weekdays.isEmpty && a.weekdays.contains(day) }
    }

    private func earliestDue(on date:Date, alarms:[WakeAlarm])->Date? {
        let day=userWeekday(date);let cal=Calendar.current
        return alarms.filter{$0.enabled && !$0.weekdays.isEmpty && $0.weekdays.contains(day)}.compactMap{cal.date(bySettingHour:$0.hour,minute:$0.minute,second:0,of:date)}.min()
    }

    private func scheduledMissedDays(after last: Date, before current: Date, alarms:[WakeAlarm], successes:[Date])->[Date] {
        let cal=Calendar.current;var result:[Date]=[];var d=cal.date(byAdding:.day,value:1,to:cal.startOfDay(for:last))!
        let end=cal.startOfDay(for:current)
        while d < end {
            if isScheduled(d,alarms:alarms) && !successes.contains(where:{cal.isDate($0,inSameDayAs:d)}) && !isProtected(d){result.append(d)}
            d=cal.date(byAdding:.day,value:1,to:d)!
        }
        return result
    }

    private func scheduledMissedDays(after last:Date, through current:Date, alarms:[WakeAlarm], successes:[Date])->[Date] {
        let cal=Calendar.current;var result=scheduledMissedDays(after:last,before:current,alarms:alarms,successes:successes)
        let today=cal.startOfDay(for:current)
        if isScheduled(today,alarms:alarms), !successes.contains(where:{cal.isDate($0,inSameDayAs:today)}), !isProtected(today), let due=earliestDue(on:today,alarms:alarms), due<Date() { result.append(today) }
        return result
    }
}

struct StreakParityDashboard: View {
    @EnvironmentObject private var store: StreakParityStore
    @EnvironmentObject private var alarmStore: AlarmStore
    @State private var month = Date()
    private let columns=Array(repeating:GridItem(.flexible(),spacing:5),count:7)

    var body: some View {
        NavigationStack {
            ZStack {
                IgnidoScreenBackground()
                ScrollView {
                    VStack(spacing:18) {
                        GrowthCompanionView(level:store.growthLevel, streak:store.displayCurrent(alarms:alarmStore.alarms)).frame(height:300)
                        VStack(spacing:3){Text("成長 Lv.\(store.growthLevel)").font(.title2.bold());Text(store.growthDescriptor).foregroundStyle(IgnidoTheme.ember)}
                        HStack { stat("現在",store.displayCurrent(alarms:alarmStore.alarms));stat("最高",store.bestStreak);stat("成功",store.totalWakeups) }.ignidoCard()
                        protectionCard
                        calendarCard
                        Button("今日の起床を記録") { store.recordWake(alarms:alarmStore.alarms) }.buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                        NavigationLink("アプリ情報") { AboutView() }.foregroundStyle(IgnidoTheme.secondaryText)
                    }.padding()
                }
            }.navigationTitle("ストリーク")
        }.onAppear{store.reload()}
    }

    private func stat(_ title:String,_ value:Int)->some View { VStack{Text("\(value)").font(.title2.bold()).monospacedDigit();Text(title).font(.caption).foregroundStyle(IgnidoTheme.secondaryText)}.frame(maxWidth:.infinity) }

    private var protectionCard: some View {
        VStack(alignment:.leading,spacing:12){
            HStack{Text("ストリーク保護").font(.headline);Spacer();Text("\(store.balance) / 3").font(.title3.bold()).foregroundStyle(IgnidoTheme.hot)}
            Toggle("自動で保護日を使う",isOn:$store.autoProtection).tint(IgnidoTheme.ember)
            Text("毎月1日に2日分回復し、最大3日。対象日に起きられなかった場合に1日使ってストリークを守ります。30日連続の実起床成功ごとに1日追加します。")
                .font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
        }.ignidoCard()
    }

    private var calendarCard: some View {
        let cal=Calendar.current
        let interval=cal.dateInterval(of:.month,for:month)!
        let first=interval.start
        let days=cal.range(of:.day,in:.month,for:month)!
        let firstWeekday=(cal.component(.weekday,from:first)+5)%7
        let stats=store.monthStats(monthDate:month,alarms:alarmStore.alarms)
        return VStack(spacing:10){
            HStack{Button{"month".isEmpty ? () : (month=cal.date(byAdding:.month,value:-1,to:month)!)}label:{Image(systemName:"chevron.left")};Spacer();Text(month.formatted(.dateTime.year().month(.wide))).font(.headline);Spacer();Button{month=cal.date(byAdding:.month,value:1,to:month)!}label:{Image(systemName:"chevron.right")}}
            LazyVGrid(columns:columns,spacing:7){
                ForEach(["月","火","水","木","金","土","日"],id:\.self){Text($0).font(.caption).foregroundStyle(IgnidoTheme.secondaryText)}
                ForEach(0..<firstWeekday,id:\.self){_ in Color.clear.frame(height:34)}
                ForEach(Array(days),id:\.self){d in
                    let date=cal.date(byAdding:.day,value:d-1,to:first)!
                    let success=store.isSuccessful(date), protected=store.isProtected(date)
                    VStack(spacing:2){Text("\(d)").font(.caption).foregroundStyle(success ? IgnidoTheme.text : (protected ? IgnidoTheme.hot : IgnidoTheme.secondaryText));Text(success ? "◆" : (protected ? "◇" : " ")).font(.caption2).foregroundStyle(success ? IgnidoTheme.ember : IgnidoTheme.hot)}.frame(height:38)
                }
            }
            Text("成功 \(stats.wins) ・ 保護 \(stats.protected) ・ 失敗 \(stats.losses) ・ 予定 \(stats.pending)").font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
        }.ignidoCard()
    }
}

struct GrowthCompanionView: View {
    let level:Int
    let streak:Int
    var body: some View {
        GeometryReader{geo in
            let s=min(geo.size.width,geo.size.height)
            let power=log1p(Double(max(1,level)))/log(2.0)
            ZStack{
                ForEach(0..<min(7,max(1,Int(power))),id:\.self){i in
                    IgnidoFlameShape().fill(i%2==0 ? IgnidoTheme.ember.opacity(0.55):IgnidoTheme.flame.opacity(0.48)).frame(width:s*(0.25+CGFloat(i)*0.035),height:s*(0.45+CGFloat(i)*0.045)).rotationEffect(.degrees(Double(i-3)*9)).offset(x:CGFloat(i-3)*s*0.055,y:s*0.10)
                }
                IgnidoFlameShape().fill(IgnidoTheme.flameGradient).frame(width:s*0.56,height:s*0.82)
                IgnidoFlameShape().fill(IgnidoTheme.hot.opacity(0.96)).frame(width:s*0.23,height:s*0.38).offset(y:s*0.18)
                if level>=12 {
                    Path{p in p.move(to:CGPoint(x:s*0.34,y:s*0.55));p.addCurve(to:CGPoint(x:s*0.66,y:s*0.55),control1:CGPoint(x:s*0.44,y:s*0.38),control2:CGPoint(x:s*0.56,y:s*0.38));p.addCurve(to:CGPoint(x:s*0.50,y:s*0.72),control1:CGPoint(x:s*0.64,y:s*0.66),control2:CGPoint(x:s*0.55,y:s*0.72));p.closeSubpath()}.stroke(Color.black.opacity(0.62),lineWidth:max(2,s*0.018))
                }
                HStack(spacing:s*0.08){Circle().fill(.black.opacity(0.8)).frame(width:s*0.035,height:s*0.035);Circle().fill(.black.opacity(0.8)).frame(width:s*0.035,height:s*0.035)}.offset(y:s*0.12)
                if streak>=30 { Circle().fill(IgnidoTheme.hot).frame(width:7,height:7).offset(x:s*0.33,y:-s*0.24) }
            }.frame(maxWidth:.infinity,maxHeight:.infinity)
        }
    }
}
