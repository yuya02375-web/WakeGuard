import SwiftUI

struct RootView: View {
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var alarmStore: AlarmStore
    @EnvironmentObject private var timerStore: TimerStore
    @EnvironmentObject private var streakStore: StreakStore
    @State private var activeAlarm: WakeAlarm?
    @State private var showTimerResult = false

    var body: some View {
        TabView {
            AlarmListView()
                .tabItem { Label("アラーム", systemImage: "alarm.fill") }
            TimerView()
                .tabItem { Label("タイマー", systemImage: "timer") }
            StopwatchView()
                .tabItem { Label("ストップウォッチ", systemImage: "stopwatch.fill") }
            WorldClockView()
                .tabItem { Label("世界時計", systemImage: "globe") }
            StreakView()
                .tabItem { Label("ストリーク", systemImage: "flame.fill") }
        }
        .tint(Color(red: 0.95, green: 0.18, blue: 0.08))
        .onAppear { consumeSystemActions() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                streakStore.reload()
                consumeSystemActions()
            }
        }
        .fullScreenCover(item: $activeAlarm, onDismiss: { consumeSystemActions() }) { alarm in
            alarmDestination(alarm)
        }
        .fullScreenCover(isPresented: $showTimerResult) {
            if let url = MediaLibrary.url(for: timerStore.mediaFileName) {
                TimerMediaScreen(url: url) { showTimerResult = false }
            } else {
                ZStack {
                    Color.black.ignoresSafeArea()
                    VStack(spacing: 24) {
                        Image(systemName: "timer").font(.system(size: 64)).foregroundStyle(.red)
                        Text("タイマー終了").font(.largeTitle.bold()).foregroundStyle(.white)
                        Button("停止") { showTimerResult = false }.buttonStyle(.borderedProminent)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func alarmDestination(_ alarm: WakeAlarm) -> some View {
        if let url = MediaLibrary.url(for: alarm.mediaFileName) {
            MediaAlarmScreen(alarm: alarm, url: url) { completeAlarm(alarm) }
        } else if alarm.mission != .none {
            MissionView(alarm: alarm) { completeAlarm(alarm) }
        } else {
            ZStack {
                Color.black.ignoresSafeArea()
                VStack(spacing: 24) {
                    Image(systemName: "alarm.fill").font(.system(size: 64)).foregroundStyle(.red)
                    Text(alarm.label).font(.largeTitle.bold()).foregroundStyle(.white)
                    Text(alarm.timeText).font(.system(size: 62, weight: .light, design: .rounded)).monospacedDigit().foregroundStyle(.white)
                    Button("停止") { completeAlarm(alarm) }.buttonStyle(.borderedProminent)
                }
            }
        }
    }

    private func completeAlarm(_ alarm: WakeAlarm) {
        streakStore.recordWake()
        activeAlarm = nil
        DispatchQueue.main.async { consumeSystemActions() }
    }

    private func consumeSystemActions() {
        if activeAlarm == nil, let id = WakeIntentState.consumeAlarmID(), let alarm = alarmStore.alarm(id: id) {
            activeAlarm = alarm
            return
        }
        if !showTimerResult, WakeIntentState.consumeTimer() {
            showTimerResult = true
        }
    }
}

struct StreakView: View {
    @EnvironmentObject private var store: StreakStore
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 5), count: 7)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    FlameCompanionView(stage: store.flameStage)
                        .frame(height: 210)
                    HStack(spacing: 40) {
                        stat("現在", value: store.currentStreak)
                        stat("最高", value: store.bestStreak)
                    }
                    Text("続けるほど炎が成長します")
                        .font(.headline)
                    wakeCalendar
                    Button("今日の起床を記録") { store.recordWake() }
                        .buttonStyle(.borderedProminent)
                    NavigationLink("アプリ情報・iOS版の仕様") { AboutView() }
                        .buttonStyle(.bordered)
                }.padding()
            }
            .navigationTitle("ストリーク")
        }
    }

    private func stat(_ title: String, value: Int) -> some View {
        VStack(spacing: 5) {
            Text("\(value)").font(.system(size: 42, weight: .bold, design: .rounded)).monospacedDigit()
            Text(title).foregroundStyle(.secondary)
        }
    }

    private var wakeCalendar: some View {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let days = (0..<35).reversed().compactMap { calendar.date(byAdding: .day, value: -$0, to: today) }
        return VStack(alignment: .leading, spacing: 10) {
            Text("起床カレンダー").font(.headline)
            LazyVGrid(columns: columns, spacing: 6) {
                ForEach(days, id: \.self) { date in
                    let hit = store.wakeDates.contains { calendar.isDate($0, inSameDayAs: date) }
                    VStack(spacing: 3) {
                        Text("\(calendar.component(.day, from: date))").font(.caption2)
                        Circle().fill(hit ? Color.red : Color.secondary.opacity(0.2)).frame(width: 22, height: 22)
                    }
                }
            }
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
    }
}

struct FlameCompanionView: View {
    let stage: Int
    var body: some View {
        ZStack {
            Circle().fill(Color.red.opacity(0.08)).frame(width: 190, height: 190)
            Image(systemName: "flame.fill")
                .resizable().scaledToFit()
                .foregroundStyle(
                    LinearGradient(colors: [Color(red: 0.55, green: 0.05, blue: 0.04), .red, Color(red: 0.82, green: 0.42, blue: 0.16)], startPoint: .bottomLeading, endPoint: .topTrailing)
                )
                .shadow(color: .red.opacity(stage >= 2 ? 0.4 : 0.15), radius: CGFloat(4 + stage * 4))
                .frame(width: CGFloat(80 + stage * 18), height: CGFloat(105 + stage * 18))
                .overlay(alignment: .bottom) {
                    if stage >= 3 {
                        HStack(spacing: 18) {
                            Circle().fill(.black.opacity(0.75)).frame(width: 8, height: 8)
                            Circle().fill(.black.opacity(0.75)).frame(width: 8, height: 8)
                        }.padding(.bottom, 42)
                    }
                }
            if stage >= 4 {
                Image(systemName: "sparkles").font(.system(size: 36)).foregroundStyle(.orange).offset(x: 60, y: -65)
            }
        }
        .accessibilityLabel("炎キャラクター レベル\(stage + 1)")
    }
}

struct AboutView: View {
    var body: some View {
        List {
            Section {
                HStack {
                    Image(systemName: "flame.circle.fill").font(.system(size: 54)).foregroundStyle(.red, .orange)
                    VStack(alignment: .leading) {
                        Text("IGNIDO Wake").font(.title2.bold())
                        Text("iOS 1.7.2 parity beta").foregroundStyle(.secondary)
                    }
                }
            }
            Section("実装済み") {
                Text("アラーム / 13種解除ミッション / 繰り返し / 事前通知 / 音声・動画選択 / 動画字幕 / タイマー / システムのタイマー表示 / ストップウォッチ / ラップ / 世界時計 / デジタル・アナログ切替 / 拡大表示 / ストリーク / 起床カレンダー / 重複アラームのアプリ内キュー")
            }
            Section("iOS固有") {
                Text("システムアラームはAlarmKitを使用します。解除ミッションや動画を設定したアラームにはロック画面の「解除」ボタンを追加し、押すと該当アラームのIGNIDO画面を開きます。ただしiOSはシステム提供の停止ボタン自体を削除できないため、Android版のようにミッション完了まで停止操作そのものを完全禁止することはできません。また、バックグラウンドから動画画面を無操作で強制表示することもiOSではできません。")
            }
        }
        .navigationTitle("アプリ情報")
    }
}
