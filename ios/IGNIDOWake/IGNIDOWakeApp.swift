import SwiftUI

@main
struct IGNIDOWakeApp: App {
    @StateObject private var alarmStore = AlarmStore()
    @StateObject private var timerStore = TimerStore()
    @StateObject private var multiTimerStore = MultiTimerStore()
    @StateObject private var stopwatchStore = StopwatchStore()
    @StateObject private var worldClockStore = WorldClockStore()
    @StateObject private var streakStore = StreakStore()
    @StateObject private var streakParityStore = StreakParityStore()
    @StateObject private var languageStore = AppLanguageStore()

    init() {
        InitialState.migrate()
        IgnidoAppearance.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(alarmStore)
                .environmentObject(timerStore)
                .environmentObject(multiTimerStore)
                .environmentObject(stopwatchStore)
                .environmentObject(worldClockStore)
                .environmentObject(streakStore)
                .environmentObject(streakParityStore)
                .environmentObject(languageStore)
                .environment(\.locale, languageStore.locale)
                .tint(IgnidoTheme.ember)
                .preferredColorScheme(.dark)
                .task { await alarmStore.bootstrap() }
        }
    }
}
