import SwiftUI

@main
struct IGNIDOWakeApp: App {
    @StateObject private var alarmStore = AlarmStore()
    @StateObject private var timerStore = TimerStore()
    @StateObject private var stopwatchStore = StopwatchStore()
    @StateObject private var worldClockStore = WorldClockStore()
    @StateObject private var streakStore = StreakStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(alarmStore)
                .environmentObject(timerStore)
                .environmentObject(stopwatchStore)
                .environmentObject(worldClockStore)
                .environmentObject(streakStore)
                .preferredColorScheme(.dark)
                .task { await alarmStore.bootstrap() }
        }
    }
}
