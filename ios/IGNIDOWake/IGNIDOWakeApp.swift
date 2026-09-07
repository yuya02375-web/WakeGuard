import SwiftUI

@main
struct IGNIDOWakeApp: App {
    @StateObject private var alarmStore = AlarmStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(alarmStore)
                .preferredColorScheme(.dark)
                .task { await alarmStore.bootstrap() }
        }
    }
}
