import SwiftUI

@main
struct IGNIDOWakeApp: App {
    @StateObject private var alarmStore = AlarmStore()
    @StateObject private var timerStore = TimerStore()
    @StateObject private var stopwatchStore = StopwatchStore()
    @StateObject private var worldClockStore = WorldClockStore()
    @StateObject private var streakStore = StreakStore()

    init() {
        InitialState.migrate()
        IgnidoAppearance.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(alarmStore)
                .environmentObject(timerStore)
                .environmentObject(stopwatchStore)
                .environmentObject(worldClockStore)
                .environmentObject(streakStore)
                .tint(IgnidoTheme.ember)
                .preferredColorScheme(.dark)
                .task { await alarmStore.bootstrap() }
        }
    }
}
