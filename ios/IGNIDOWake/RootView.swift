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
        .tint(IgnidoTheme.ember)
        .toolbarBackground(IgnidoTheme.chrome, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .background(IgnidoTheme.background.ignoresSafeArea())
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
                    IgnidoScreenBackground()
                    VStack(spacing: 24) {
                        Image(systemName: "timer")
                            .font(.system(size: 64, weight: .medium))
                            .foregroundStyle(IgnidoTheme.emberGradient)
                        Text("タイマー終了").font(.largeTitle.bold()).foregroundStyle(IgnidoTheme.text)
                        Button("停止") { showTimerResult = false }
                            .buttonStyle(.borderedProminent)
                            .tint(IgnidoTheme.ember)
                    }
                    .ignidoCard()
                    .padding(28)
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
                IgnidoScreenBackground()
                VStack(spacing: 24) {
                    Image(systemName: "alarm.fill")
                        .font(.system(size: 64, weight: .medium))
                        .foregroundStyle(IgnidoTheme.emberGradient)
                    Text(alarm.label).font(.largeTitle.bold()).foregroundStyle(IgnidoTheme.text)
                    Text(alarm.timeText)
                        .font(.system(size: 64, weight: .light, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(IgnidoTheme.text)
                    Button("停止") { completeAlarm(alarm) }
                        .buttonStyle(.borderedProminent)
                        .tint(IgnidoTheme.ember)
                }
                .ignidoCard()
                .padding(26)
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
            ZStack {
                IgnidoScreenBackground()
                ScrollView {
                    VStack(spacing: 24) {
                        VStack(spacing: 10) {
                            Text("IGNIDO FLAME")
                                .font(.caption.bold())
                                .tracking(2.4)
                                .foregroundStyle(IgnidoTheme.amber)
                            FlameCompanionView(stage: store.flameStage)
                                .frame(height: 210)
                        }
                        .frame(maxWidth: .infinity)
                        .ignidoCard()

                        HStack(spacing: 12) {
                            stat("現在", value: store.currentStreak)
                            stat("最高", value: store.bestStreak)
                        }

                        Text("続けるほど炎が成長します")
                            .font(.headline)
                            .foregroundStyle(IgnidoTheme.text)
                        wakeCalendar
                        Button("今日の起床を記録") { store.recordWake() }
                            .buttonStyle(.borderedProminent)
                            .tint(IgnidoTheme.ember)
                            .controlSize(.large)
                        NavigationLink("アプリ情報・iOS版の仕様") { AboutView() }
                            .buttonStyle(.bordered)
                            .tint(IgnidoTheme.amber)
                    }
                    .padding()
                }
            }
            .navigationTitle("ストリーク")
        }
    }

    private func stat(_ title: String, value: Int) -> some View {
        VStack(spacing: 5) {
            Text("\(value)")
                .font(.system(size: 42, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(IgnidoTheme.text)
            Text(title).foregroundStyle(IgnidoTheme.muted)
        }
        .frame(maxWidth: .infinity)
        .ignidoCard()
    }

    private var wakeCalendar: some View {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let days = (0..<35).reversed().compactMap { calendar.date(byAdding: .day, value: -$0, to: today) }
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("起床カレンダー").font(.headline)
                Spacer()
                Image(systemName: "flame.fill").foregroundStyle(IgnidoTheme.ember)
            }
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(days, id: \.self) { date in
                    let hit = store.wakeDates.contains { calendar.isDate($0, inSameDayAs: date) }
                    VStack(spacing: 4) {
                        Text("\(calendar.component(.day, from: date))")
                            .font(.caption2)
                            .foregroundStyle(hit ? IgnidoTheme.text : IgnidoTheme.muted)
                        Circle()
                            .fill(hit ? IgnidoTheme.emberGradient : LinearGradient(colors: [IgnidoTheme.surface2], startPoint: .top, endPoint: .bottom))
                            .frame(width: 23, height: 23)
                            .overlay(Circle().stroke(hit ? IgnidoTheme.amber.opacity(0.5) : IgnidoTheme.border, lineWidth: 1))
                    }
                }
            }
        }
        .ignidoCard()
    }
}

struct FlameCompanionView: View {
    let stage: Int
    var body: some View {
        ZStack {
            Circle()
                .fill(
                    RadialGradient(colors: [IgnidoTheme.ember.opacity(0.22), IgnidoTheme.surface.opacity(0.1)], center: .center, startRadius: 10, endRadius: 95)
                )
                .frame(width: 190, height: 190)
            Image(systemName: "flame.fill")
                .resizable().scaledToFit()
                .foregroundStyle(IgnidoTheme.emberGradient)
                .shadow(color: IgnidoTheme.ember.opacity(stage >= 2 ? 0.48 : 0.22), radius: CGFloat(6 + stage * 4))
                .frame(width: CGFloat(80 + stage * 18), height: CGFloat(105 + stage * 18))
                .overlay(alignment: .bottom) {
                    if stage >= 3 {
                        HStack(spacing: 18) {
                            Circle().fill(.black.opacity(0.78)).frame(width: 8, height: 8)
                            Circle().fill(.black.opacity(0.78)).frame(width: 8, height: 8)
                        }.padding(.bottom, 42)
                    }
                }
            if stage >= 4 {
                Image(systemName: "sparkles")
                    .font(.system(size: 36))
                    .foregroundStyle(IgnidoTheme.amber)
                    .offset(x: 60, y: -65)
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
                    Image(systemName: "flame.circle.fill")
                        .font(.system(size: 54))
                        .foregroundStyle(IgnidoTheme.emberGradient)
                    VStack(alignment: .leading) {
                        Text("IGNIDO Wake").font(.title2.bold())
                        Text("iOS 1.7.3 Ember Night beta").foregroundStyle(IgnidoTheme.muted)
                    }
                }
            }
            Section("実装済み") {
                Text("アラーム / 13種解除ミッション / 繰り返し / 事前通知 / 音声・動画選択 / 動画字幕 / タイマー / システムのタイマー表示 / ストップウォッチ / ラップ / 世界時計 / デジタル・アナログ切替 / 拡大表示 / ストリーク / 起床カレンダー / 重複アラームのアプリ内キュー")
            }
            Section("デザイン") {
                Text("Ember Night: グラファイト系の黒を基調に、操作・選択だけ赤橙の炎色で強調するIGNIDO専用テーマです。睡眠直後でも時刻と状態を最優先で読み取れるよう、装飾は強調箇所に限定しています。")
            }
            Section("iOS固有") {
                Text("システムアラームはAlarmKitを使用します。解除ミッションや動画を設定したアラームにはロック画面の「解除」ボタンを追加し、押すと該当アラームのIGNIDO画面を開きます。ただしiOSはシステム提供の停止ボタン自体を削除できないため、Android版のようにミッション完了まで停止操作そのものを完全禁止することはできません。また、バックグラウンドから動画画面を無操作で強制表示することもiOSではできません。")
            }
        }
        .scrollContentBackground(.hidden)
        .background(IgnidoTheme.background)
        .navigationTitle("アプリ情報")
    }
}
