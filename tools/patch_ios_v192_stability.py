from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# Version 1.9.2 / build 21
for path in [root/'Info.plist']:
    s=path.read_text(encoding='utf-8')
    s=re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+(</string>)',r'\g<1>1.9.2\g<2>',s,count=1)
    s=re.sub(r'(<key>CFBundleVersion</key>\s*<string>)[^<]+(</string>)',r'\g<1>21\g<2>',s,count=1)
    path.write_text(s,encoding='utf-8')
p=Path('ios/project.yml'); s=p.read_text(encoding='utf-8'); s=re.sub(r'CFBundleShortVersionString: "[^"]+"','CFBundleShortVersionString: "1.9.2"',s); s=re.sub(r'CFBundleVersion: "[^"]+"','CFBundleVersion: "21"',s); p.write_text(s,encoding='utf-8')

# 1) Timestamp OpenAlarmIntent requests so an old request can never start media
# merely because the screen/app becomes active later.
p=root/'AlarmIntents.swift'; s=p.read_text(encoding='utf-8')
start=s.index('private enum WakeIntentBridge {'); end=s.index('\npublic struct OpenAlarmIntent:',start)
bridge=r'''struct WakePendingAlarmRequest: Codable, Hashable {
    let alarmID: String
    let createdAt: Date
}

private enum WakeIntentBridge {
    static let legacyPendingAlarmQueueKey = "ignido.intent.pendingAlarmQueue"
    static let requestKey = "ignido.intent.pendingAlarmRequests.v2"
    static let pendingTimerKey = "ignido.intent.pendingTimer"
    static let pendingTimerQueueKey = "ignido.intent.pendingTimerQueue"
    static let streakKey = "ignido.streak.dates.v2"

    static func enqueueAlarm(_ alarmID: String) {
        guard !alarmID.isEmpty else { return }
        let d=UserDefaults.standard
        d.removeObject(forKey: legacyPendingAlarmQueueKey)
        var q=alarmRequests().filter { Date().timeIntervalSince($0.createdAt) <= 300 }
        q.removeAll { $0.alarmID == alarmID }
        q.append(.init(alarmID: alarmID, createdAt: Date()))
        save(q)
    }
    static func dequeueAlarm() -> WakePendingAlarmRequest? {
        let d=UserDefaults.standard
        d.removeObject(forKey: legacyPendingAlarmQueueKey)
        var q=alarmRequests().filter { Date().timeIntervalSince($0.createdAt) <= 300 }
        guard !q.isEmpty else { save([]); return nil }
        let x=q.removeFirst(); save(q); return x
    }
    static func clearExpiredAlarmRequests() {
        UserDefaults.standard.removeObject(forKey: legacyPendingAlarmQueueKey)
        save(alarmRequests().filter { Date().timeIntervalSince($0.createdAt) <= 300 })
    }
    private static func alarmRequests() -> [WakePendingAlarmRequest] {
        guard let data=UserDefaults.standard.data(forKey: requestKey), let q=try? JSONDecoder().decode([WakePendingAlarmRequest].self,from:data) else { return [] }
        return q
    }
    private static func save(_ q:[WakePendingAlarmRequest]) {
        if q.isEmpty { UserDefaults.standard.removeObject(forKey: requestKey) }
        else if let data=try? JSONEncoder().encode(q) { UserDefaults.standard.set(data,forKey:requestKey) }
    }

    static func enqueueTimer(_ timerID: String) {
        guard !timerID.isEmpty else { UserDefaults.standard.set(true, forKey: pendingTimerKey); return }
        let d=UserDefaults.standard; var q=d.stringArray(forKey: pendingTimerQueueKey) ?? []
        if !q.contains(timerID) { q.append(timerID) }; d.set(q,forKey:pendingTimerQueueKey)
    }
    static func dequeueTimer() -> String? {
        let d=UserDefaults.standard; var q=d.stringArray(forKey: pendingTimerQueueKey) ?? []
        guard !q.isEmpty else { return nil }; let x=q.removeFirst(); d.set(q,forKey:pendingTimerQueueKey); return x
    }
    static func markWakeCompleted() {
        let d=UserDefaults.standard; var dates:[Date]=[]
        if let data=d.data(forKey: streakKey), let x=try? JSONDecoder().decode([Date].self,from:data) { dates=x }
        let c=Calendar.current, today=c.startOfDay(for:Date())
        if !dates.contains(where:{c.isDate($0,inSameDayAs:today)}) { dates.append(today); if let data=try? JSONEncoder().encode(dates){d.set(data,forKey:streakKey)} }
    }
}
'''
s=s[:start]+bridge+s[end:]
s=s.replace('''    static func consumeAlarmID() -> UUID? {
        guard let raw = WakeIntentBridge.dequeueAlarm() else { return nil }
        return UUID(uuidString: raw)
    }''','''    static func consumeAlarmRequest() -> WakePendingAlarmRequest? { WakeIntentBridge.dequeueAlarm() }
    static func clearExpiredAlarmRequests() { WakeIntentBridge.clearExpiredAlarmRequests() }''')
p.write_text(s,encoding='utf-8')

# 2) AlarmKit must own the alarm lifecycle. Use a concrete fixed future date for
# one-shot alarms, verify schedule success, fallback to system sound if custom
# registration throws, and never blindly recreate one-shots on app launch.
p=root/'AlarmStore.swift'; s=p.read_text(encoding='utf-8')
s=s.replace('    @Published var lastError: String?\n','    @Published var lastError: String?\n    @Published private(set) var systemAlarmStates: [UUID: Alarm.State] = [:]\n',1)
s=s.replace('    private let center = UNUserNotificationCenter.current()\n','    private let center = UNUserNotificationCenter.current()\n    private let testAlarmID = UUID(uuidString: "42DE9142-42A1-4E31-9A12-190200000021")!\n',1)
m=re.search(r'    func bootstrap\(\) async \{.*?\n    \}\n\n    func requestAuthorization',s,re.S); assert m
s=s[:m.start()]+'''    func bootstrap() async {
        authorizationState = AlarmManager.shared.authorizationState
        WakeIntentState.clearExpiredAlarmRequests()
        Task { [weak self] in guard let self else { return }; for await state in AlarmManager.shared.authorizationUpdates { await MainActor.run { self.authorizationState = state } } }
        Task { [weak self] in guard let self else { return }; for await alarms in AlarmManager.shared.alarmUpdates { await MainActor.run { self.applySystemAlarms(alarms) } } }
        await reconcileWithSystem()
    }

    func requestAuthorization'''+s[m.end():]

m=re.search(r'    func schedule\(_ item: WakeAlarm\) async \{.*?\n    \}\n\n    func scheduleTest',s,re.S); assert m
schedule=r'''    func schedule(_ item: WakeAlarm) async {
        guard item.enabled else { return }
        if authorizationState != .authorized { guard await requestAuthorization() else { return } }
        let alarmSchedule: Alarm.Schedule
        if item.weekdays.isEmpty {
            guard let fire = nextAlarmDate(hour: item.hour, minute: item.minute) else { return }
            alarmSchedule = .fixed(fire)
        } else {
            let t=Alarm.Schedule.Relative.Time(hour:item.hour,minute:item.minute)
            alarmSchedule = .relative(.init(time:t,repeats:.weekly(localeWeekdays(item.weekdays))))
        }
        do {
            try? AlarmManager.shared.cancel(id:item.id)
            let a=try await register(item,schedule:alarmSchedule,defaultSound:false)
            systemAlarmStates[a.id]=a.state
            guard a.state == .scheduled else { throw NSError(domain:"IGNIDOAlarm",code:192,userInfo:[NSLocalizedDescriptionKey:"システムにアラームを登録できませんでした"]) }
            await schedulePreAlert(for:item); lastError=nil
        } catch {
            if IgnidoAlarmSoundCatalog.normalizedSelection(item.soundName) != IgnidoAlarmSoundCatalog.systemDefaultID {
                do {
                    try? AlarmManager.shared.cancel(id:item.id)
                    let a=try await register(item,schedule:alarmSchedule,defaultSound:true)
                    systemAlarmStates[a.id]=a.state
                    guard a.state == .scheduled else { throw error }
                    await schedulePreAlert(for:item)
                    lastError=AppText.localized("選択した音を登録できなかったため、標準音でアラームを確保しました")
                    return
                } catch { }
            }
            lastError=error.localizedDescription
        }
    }

    private func register(_ item: WakeAlarm, schedule: Alarm.Schedule, defaultSound: Bool) async throws -> Alarm {
        let needsOpen=item.mission != .none || item.mediaFileName != nil
        let button=needsOpen ? AlarmButton(text:LocalizedStringResource(stringLiteral:AppText.localized("解除")),textColor:.white,systemImageName:"arrow.right.circle.fill") : nil
        let alert=AlarmPresentation.Alert(title:LocalizedStringResource(stringLiteral:"\(item.timeText)  \(item.displayLabel)"),secondaryButton:button,secondaryButtonBehavior:needsOpen ? .custom:nil)
        let attrs=AlarmAttributes<EmptyWakeMetadata>(presentation:AlarmPresentation(alert:alert),metadata:EmptyWakeMetadata(),tintColor:IgnidoTheme.ember)
        let intent:(any LiveActivityIntent)?=needsOpen ? OpenAlarmIntent(alarmID:item.id.uuidString):nil
        let config=AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.alarm(schedule:schedule,attributes:attrs,stopIntent:StopWakeIntent(alarmID:item.id.uuidString),secondaryIntent:intent,sound:defaultSound ? .default : IgnidoAlarmSoundCatalog.alertSound(for:item.soundName))
        return try await AlarmManager.shared.schedule(id:item.id,configuration:config)
    }

    func scheduleTest'''
s=s[:m.start()]+schedule+s[m.end():]

m=re.search(r'    func scheduleTest\(_ item: WakeAlarm, after seconds: TimeInterval = 5\) async \{.*?\n    \}\n\n    func rescheduleAll',s,re.S); assert m
test=r'''    func scheduleTest(_ item: WakeAlarm, after seconds: TimeInterval = 5) async {
        if authorizationState != .authorized { guard await requestAuthorization() else { return } }
        let when:Alarm.Schedule = .fixed(Date().addingTimeInterval(max(2,seconds)))
        let alert=AlarmPresentation.Alert(title:LocalizedStringResource(stringLiteral:"TEST  \(item.displayLabel)"))
        let attrs=AlarmAttributes<EmptyWakeMetadata>(presentation:AlarmPresentation(alert:alert),metadata:EmptyWakeMetadata(),tintColor:IgnidoTheme.ember)
        do {
            try? AlarmManager.shared.cancel(id:testAlarmID)
            let cfg=AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.alarm(schedule:when,attributes:attrs,stopIntent:nil,secondaryIntent:nil,sound:IgnidoAlarmSoundCatalog.alertSound(for:item.soundName))
            let a=try await AlarmManager.shared.schedule(id:testAlarmID,configuration:cfg)
            guard a.state == .scheduled else { throw NSError(domain:"IGNIDOAlarm",code:193,userInfo:[NSLocalizedDescriptionKey:"テストを登録できませんでした"]) }
            lastError=nil
        } catch {
            do { try? AlarmManager.shared.cancel(id:testAlarmID); let cfg=AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.alarm(schedule:when,attributes:attrs,stopIntent:nil,secondaryIntent:nil,sound:.default); _=try await AlarmManager.shared.schedule(id:testAlarmID,configuration:cfg); lastError=AppText.localized("カスタム音に失敗したためテストは標準音にしました") }
            catch { lastError=error.localizedDescription }
        }
    }

    func rescheduleAll'''
s=s[:m.start()]+test+s[m.end():]

m=re.search(r'    func rescheduleAll\(\) async \{.*?\n    \}\n\n    private func cancelScheduling',s,re.S); assert m
reconcile=r'''    func rescheduleAll() async { await reconcileWithSystem() }

    func reconcileWithSystem() async {
        guard authorizationState == .authorized else { return }
        do {
            let system=try AlarmManager.shared.alarms; applySystemAlarms(system); let ids=Set(system.map(\.id))
            // AlarmKit persists one-shot alarms in its daemon. If a one-shot is missing,
            // it may already have fired, so never resurrect it just because the app opened.
            for item in alarms where item.enabled && !item.weekdays.isEmpty && !ids.contains(item.id) { await schedule(item) }
        } catch { lastError=error.localizedDescription }
    }
    func refreshSystemState(){ if let x=try? AlarmManager.shared.alarms { applySystemAlarms(x) } }
    func isSystemAlarmAlerting(id:UUID)->Bool { refreshSystemState(); return systemAlarmStates[id] == .alerting }
    func isAlarmDueNow(_ item:WakeAlarm,tolerance:TimeInterval=180)->Bool {
        let now=Date(), c=Calendar.current, comps=c.dateComponents([.hour,.minute,.weekday,.second],from:now)
        let current=Double((comps.hour ?? 0)*3600+(comps.minute ?? 0)*60+(comps.second ?? 0)), target=Double(item.hour*3600+item.minute*60)
        if !item.weekdays.isEmpty { let sw=comps.weekday ?? 1, ud=sw==1 ? 7:sw-1; if !item.weekdays.contains(ud){return false} }
        return abs(current-target) <= tolerance
    }
    func completeSystemAlarm(_ item:WakeAlarm){ try? AlarmManager.shared.stop(id:item.id); systemAlarmStates[item.id]=nil }
    private func applySystemAlarms(_ incoming:[Alarm]){ systemAlarmStates=Dictionary(uniqueKeysWithValues:incoming.map{($0.id,$0.state)}) }

    private func cancelScheduling'''
s=s[:m.start()]+reconcile+s[m.end():]
p.write_text(s,encoding='utf-8')

# 3) Do not auto-play imported audio. That preview was app-owned audio, so it
# stopped when the app was swiped away and could resume at unrelated times.
p=root/'AlarmViews.swift'; s=p.read_text(encoding='utf-8')
s=s.replace('                    IgnidoAlarmSoundPreview.shared.play(selection: draft.soundName)\n','')
if '.onDisappear { IgnidoAlarmSoundPreview.shared.stop() }' not in s:
    s=s.replace('        .fullScreenCover(isPresented: $showMissionTest) {','        .onDisappear { IgnidoAlarmSoundPreview.shared.stop() }\n        .fullScreenCover(isPresented: $showMissionTest) {',1)
p.write_text(s,encoding='utf-8')

# 4) Only open mission/media for a fresh system alarm request. Completing the
# mission explicitly stops AlarmKit. Closing the app itself no longer owns sound.
p=root/'RootView.swift'; s=p.read_text(encoding='utf-8')
s=s.replace('.onAppear { consumeSystemActions() }','.onAppear { Task { await consumeSystemActions() } }',1)
s=s.replace('                consumeSystemActions()\n','                Task { await consumeSystemActions() }\n',1)
s=s.replace('.fullScreenCover(item: $activeAlarm, onDismiss: { consumeSystemActions() })','.fullScreenCover(item: $activeAlarm, onDismiss: { Task { await consumeSystemActions() } })',1)
s=s.replace('.fullScreenCover(item: $activeTimer, onDismiss: { consumeSystemActions() })','.fullScreenCover(item: $activeTimer, onDismiss: { Task { await consumeSystemActions() } })',1)
s=s.replace('        DispatchQueue.main.async { consumeSystemActions() }','        Task { alarmStore.completeSystemAlarm(alarm); await consumeSystemActions() }',1)
m=re.search(r'    private func consumeSystemActions\(\) \{.*?\n    \}\n(?=\})',s,re.S); assert m
new='''    @MainActor
    private func consumeSystemActions() async {
        if activeAlarm == nil, let request=WakeIntentState.consumeAlarmRequest(), Date().timeIntervalSince(request.createdAt) <= 180, let id=UUID(uuidString:request.alarmID), let alarm=alarmStore.alarm(id:id) {
            var valid=alarmStore.isSystemAlarmAlerting(id:id)
            if !valid { for _ in 0..<4 where !valid { try? await Task.sleep(for:.milliseconds(180)); valid=alarmStore.isSystemAlarmAlerting(id:id) } }
            if valid || alarmStore.isAlarmDueNow(alarm,tolerance:180) { activeAlarm=alarm; return }
        }
        if activeTimer == nil, let timerID=WakeIntentState.consumeTimerID(), let item=multiTimerStore.item(id:timerID) { multiTimerStore.finish(timerID); activeTimer=item; return }
        if !showTimerResult, WakeIntentState.consumeTimer(){ showTimerResult=true }
    }
'''
s=s[:m.start()]+new+s[m.end():]; p.write_text(s,encoding='utf-8')

# 5) Stable step counter: persistent CMPedometer object + live updates + one-second
# historical query fallback. Preserve start time so reopening the mission resumes.
p=root/'MissionView.swift'; s=p.read_text(encoding='utf-8')
s=s.replace('import CoreMotion\n','import CoreMotion\nimport UIKit\n',1)
s=s.replace('case .steps: StepMissionView(target: max(1, alarm.missionTarget), onComplete: finish)','case .steps: StepMissionView(alarmID: alarm.id, target: max(1, alarm.missionTarget), onComplete: finish)',1)
a=s.index('struct StepMissionView: View {'); b=s.index('\nstruct MathMissionView: View {',a)
step=r'''@MainActor
final class StepMissionCounter: ObservableObject {
    @Published var steps=0; @Published var errorText:String?; @Published var permissionDenied=false
    private let pedometer=CMPedometer(); private var timer:Timer?; private var startDate=Date(); private var target=1; private var alarmID=UUID(); private var done=false; private var completion:(()->Void)?
    func start(alarmID:UUID,target:Int,onComplete:@escaping()->Void){
        suspend(); self.alarmID=alarmID; self.target=max(1,target); completion=onComplete; done=false; steps=0; errorText=nil; permissionDenied=false
        guard CMPedometer.isStepCountingAvailable() else { errorText=AppText.localized("この端末では歩数計を利用できません"); return }
        let st=CMPedometer.authorizationStatus(); if st == .denied || st == .restricted { permissionDenied=true; errorText=AppText.localized("モーションとフィットネスの権限を許可してください"); return }
        let k=key(alarmID); if let d=UserDefaults.standard.object(forKey:k) as? Date, Date().timeIntervalSince(d) >= 0, Date().timeIntervalSince(d) <= 43200 { startDate=d } else { startDate=Date(); UserDefaults.standard.set(startDate,forKey:k) }
        pedometer.startUpdates(from:startDate){[weak self] data,error in Task{@MainActor in guard let self else{return}; if let error{self.errorText=error.localizedDescription;self.permissionDenied=CMPedometer.authorizationStatus()==.denied;return};self.accept(data?.numberOfSteps.intValue ?? 0)}}
        query(); timer=Timer.scheduledTimer(withTimeInterval:1,repeats:true){[weak self]_ in Task{@MainActor in self?.query()}}
    }
    func suspend(){pedometer.stopUpdates();timer?.invalidate();timer=nil}
    private func query(){let from=startDate;pedometer.queryPedometerData(from:from,to:Date()){[weak self] data,error in Task{@MainActor in guard let self else{return};if let error{if self.errorText==nil{self.errorText=error.localizedDescription};self.permissionDenied=CMPedometer.authorizationStatus()==.denied;return};self.accept(data?.numberOfSteps.intValue ?? 0)}}}
    private func accept(_ n:Int){guard !done else{return};steps=max(steps,n);if steps>=target{done=true;UserDefaults.standard.removeObject(forKey:key(alarmID));suspend();completion?()}}
    private func key(_ id:UUID)->String{"ignido.stepMission.start.\(id.uuidString)"}
}
struct StepMissionView:View{
    let alarmID:UUID;let target:Int;let onComplete:()->Void;@StateObject private var counter=StepMissionCounter()
    var body:some View{VStack(spacing:18){ProgressLabel(value:counter.steps,target:target,title:"スマホを持って歩く");Text("ライブ歩数と履歴歩数の両方で確認します。アプリを閉じても、同じ解除画面へ戻れば開始時点から復元します。").font(.caption).foregroundStyle(IgnidoTheme.secondaryText).multilineTextAlignment(.center);if let e=counter.errorText{Text(e).font(.caption).foregroundStyle(.red)};if counter.permissionDenied,let u=URL(string:UIApplication.openSettingsURLString){Link("iPhoneの設定を開く",destination:u).buttonStyle(.borderedProminent)}}.onAppear{counter.start(alarmID:alarmID,target:target,onComplete:onComplete)}.onDisappear{counter.suspend()}}
}
'''
s=s[:a]+step+s[b:]; p.write_text(s,encoding='utf-8')

# Release preview audio session.
p=root/'AlarmSoundCatalog.swift'; s=p.read_text(encoding='utf-8')
s=s.replace('    func stop() { player?.stop(); player = nil }\n','    func stop() { player?.stop(); player=nil; try? AVAudioSession.sharedInstance().setActive(false,options:[.notifyOthersOnDeactivation]) }\n',1)
p.write_text(s,encoding='utf-8')

# Static checks
checks={root/'AlarmIntents.swift':['pendingAlarmRequests.v2','consumeAlarmRequest'],root/'AlarmStore.swift':['.fixed(fire)','alarmUpdates','completeSystemAlarm','register(_ item'],root/'RootView.swift':['request.createdAt','isSystemAlarmAlerting','completeSystemAlarm'],root/'MissionView.swift':['StepMissionCounter','queryPedometerData','CMPedometer.authorizationStatus'],root/'AlarmViews.swift':['onDisappear { IgnidoAlarmSoundPreview.shared.stop() }']}
for p,tokens in checks.items():
    text=p.read_text(encoding='utf-8')
    for token in tokens:
        if token not in text: raise SystemExit(f'missing {token} in {p}')
if 'IgnidoAlarmSoundPreview.shared.play(selection: draft.soundName)' in (root/'AlarmViews.swift').read_text(encoding='utf-8'): raise SystemExit('import still auto previews')
print('IGNIDO Wake iOS 1.9.2 stability patch applied')
