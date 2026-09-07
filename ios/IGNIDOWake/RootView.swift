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
        .tint(IgnidoTheme.flame)
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
                    VStack(spacing: 20) {
                        IgnidoFlameMark()
                            .frame(width: 48, height: 68)
                        Text("タイマー終了")
                            .font(.system(size: 34, weight: .bold))
                            .foregroundStyle(IgnidoTheme.text)
                        Button("停止") { showTimerResult = false }
                            .buttonStyle(.borderedProminent)
                            .tint(IgnidoTheme.flame)
                            .controlSize(.large)
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
                VStack(spacing: 18) {
                    IgnidoFlameMark()
                        .frame(width: 46, height: 64)
                    Text(alarm.label)
                        .font(.title.bold())
                        .foregroundStyle(IgnidoTheme.text)
                    Text(alarm.timeText)
                        .font(.system(size: 68, weight: .light, design: .default))
                        .monospacedDigit()
                        .foregroundStyle(IgnidoTheme.text)
                    Rectangle()
                        .fill(IgnidoTheme.flame)
                        .frame(width: 44, height: 2)
                    Button("停止") { completeAlarm(alarm) }
                        .buttonStyle(.borderedProminent)
                        .tint(IgnidoTheme.flame)
                        .controlSize(.large)
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
                    VStack(spacing: 22) {
                        FlameCompanionView(stage: store.flameStage)
                            .frame(height: 235)
                            .frame(maxWidth: .infinity)

                        streakSummary

                        HStack(spacing: 0) {
                            stat("現在", value: store.currentStreak)
                            Rectangle()
                                .fill(IgnidoTheme.border.opacity(0.7))
                                .frame(width: 1, height: 52)
                            stat("最高", value: store.bestStreak)
                        }
                        .padding(.vertical, 14)
                        .background(IgnidoTheme.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(IgnidoTheme.border.opacity(0.58), lineWidth: 0.8)
                        )

                        wakeCalendar

                        Button("今日の起床を記録") { store.recordWake() }
                            .buttonStyle(.borderedProminent)
                            .tint(IgnidoTheme.flame)
                            .controlSize(.large)
                            .frame(maxWidth: .infinity)

                        NavigationLink("アプリ情報・iOS版の仕様") { AboutView() }
                            .foregroundStyle(IgnidoTheme.muted)
                    }
                    .padding()
                }
            }
            .navigationTitle("ストリーク")
        }
    }

    private var streakSummary: some View {
        VStack(spacing: 4) {
            Text("\(store.currentStreak)")
                .font(.system(size: 58, weight: .black, design: .default))
                .monospacedDigit()
                .foregroundStyle(IgnidoTheme.text)
            Text("DAY STREAK")
                .font(.caption.bold())
                .tracking(1.5)
                .foregroundStyle(IgnidoTheme.flame)
            Text("続けるほど炎が大きくなる")
                .font(.subheadline)
                .foregroundStyle(IgnidoTheme.muted)
                .padding(.top, 2)
        }
    }

    private func stat(_ title: String, value: Int) -> some View {
        VStack(spacing: 3) {
            Text("\(value)")
                .font(.system(size: 32, weight: .bold, design: .default))
                .monospacedDigit()
                .foregroundStyle(IgnidoTheme.text)
            Text(title)
                .font(.caption)
                .foregroundStyle(IgnidoTheme.muted)
        }
        .frame(maxWidth: .infinity)
    }

    private var wakeCalendar: some View {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let days = (0..<35).reversed().compactMap { calendar.date(byAdding: .day, value: -$0, to: today) }
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("起床カレンダー").font(.headline)
                Spacer()
                IgnidoFlameMark()
                    .frame(width: 14, height: 20)
            }
            LazyVGrid(columns: columns, spacing: 9) {
                ForEach(days, id: \.self) { date in
                    let hit = store.wakeDates.contains { calendar.isDate($0, inSameDayAs: date) }
                    VStack(spacing: 4) {
                        Text("\(calendar.component(.day, from: date))")
                            .font(.caption2)
                            .foregroundStyle(hit ? IgnidoTheme.text : IgnidoTheme.muted)
                        ZStack {
                            Circle()
                                .fill(hit ? IgnidoTheme.flame : IgnidoTheme.surface2)
                                .frame(width: 22, height: 22)
                            if hit {
                                IgnidoFlameShape()
                                    .fill(IgnidoTheme.hot)
                                    .frame(width: 8, height: 11)
                            }
                        }
                        .overlay(Circle().stroke(hit ? IgnidoTheme.ember.opacity(0.75) : IgnidoTheme.border, lineWidth: 1))
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
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            ZStack {
                if stage >= 2 {
                    IgnidoFlameShape()
                        .fill(IgnidoTheme.ember.opacity(0.72))
                        .frame(width: size * 0.34, height: size * 0.54)
                        .rotationEffect(.degrees(-13))
                        .offset(x: -size * 0.23, y: size * 0.10)
                    IgnidoFlameShape()
                        .fill(IgnidoTheme.flame.opacity(0.72))
                        .frame(width: size * 0.30, height: size * 0.49)
                        .rotationEffect(.degrees(12))
                        .offset(x: size * 0.24, y: size * 0.12)
                }

                IgnidoFlameShape()
                    .fill(IgnidoTheme.flameGradient)
                    .frame(width: size * CGFloat(0.52 + Double(stage) * 0.035),
                           height: size * CGFloat(0.72 + Double(stage) * 0.035))

                IgnidoFlameShape()
                    .fill(IgnidoTheme.hot.opacity(stage >= 3 ? 0.98 : 0.86))
                    .frame(width: size * 0.22, height: size * 0.34)
                    .offset(y: size * 0.17)

                HStack(spacing: size * 0.08) {
                    Capsule()
                        .fill(Color.black.opacity(0.82))
                        .frame(width: size * 0.065, height: size * 0.018)
                        .rotationEffect(.degrees(10))
                    Capsule()
                        .fill(Color.black.opacity(0.82))
                        .frame(width: size * 0.065, height: size * 0.018)
                        .rotationEffect(.degrees(-10))
                }
                .offset(y: size * 0.16)

                Capsule()
                    .fill(Color.black.opacity(0.70))
                    .frame(width: size * 0.08, height: size * 0.012)
                    .offset(y: size * 0.22)

                if stage >= 3 {
                    Circle().fill(IgnidoTheme.hot).frame(width: 6, height: 6).offset(x: size * 0.30, y: -size * 0.20)
                    Circle().fill(IgnidoTheme.flame).frame(width: 4, height: 4).offset(x: -size * 0.32, y: -size * 0.10)
                }
                if stage >= 4 {
                    Circle().fill(IgnidoTheme.ember).frame(width: 5, height: 5).offset(x: size * 0.36, y: size * 0.02)
                    Circle().fill(IgnidoTheme.hot).frame(width: 3, height: 3).offset(x: -size * 0.28, y: -size * 0.26)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .accessibilityLabel("炎キャラクター レベル\(stage + 1)")
    }
}

struct AboutView: View {
    var body: some View {
        List {
            Section {
                HStack(spacing: 14) {
                    IgnidoFlameMark()
                        .frame(width: 34, height: 48)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("IGNIDO Wake").font(.title2.bold())
                        Text("iOS 1.7.4 beta").foregroundStyle(IgnidoTheme.muted)
                    }
                }
            }
            Section("実装済み") {
                Text("アラーム / 13種解除ミッション / 繰り返し / 事前通知 / 音声・動画選択 / 動画字幕 / タイマー / システムのタイマー表示 / ストップウォッチ / ラップ / 世界時計 / デジタル・アナログ切替 / 拡大表示 / ストリーク / 起床カレンダー / 重複アラームのアプリ内キュー")
            }
            Section("デザイン") {
                Text("焦げた黒を土台にして、炎の赤・橙・黄はアクティブ状態とブランドマークに集中させています。丸いカードや発光を乱用せず、炎の輪郭・焼けたエッジ・細い火線でIGNIDOらしさを出しています。")
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
