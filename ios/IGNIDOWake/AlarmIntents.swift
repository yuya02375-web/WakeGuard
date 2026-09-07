import Foundation
import AppIntents

private enum WakeIntentBridge {
    static let pendingAlarmKey = "ignido.intent.pendingAlarmID"
    static let pendingTimerKey = "ignido.intent.pendingTimer"
    static let streakKey = "ignido.streak.dates.v2"

    static func markWakeCompleted() {
        let defaults = UserDefaults.standard
        var dates: [Date] = []
        if let data = defaults.data(forKey: streakKey), let decoded = try? JSONDecoder().decode([Date].self, from: data) {
            dates = decoded
        }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        if !dates.contains(where: { calendar.isDate($0, inSameDayAs: today) }) {
            dates.append(today)
            if let data = try? JSONEncoder().encode(dates) { defaults.set(data, forKey: streakKey) }
        }
    }
}

public struct OpenAlarmIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "IGNIDO Wakeを開く"
    public static var description = IntentDescription("アラームの解除画面を開きます")
    public static var openAppWhenRun = true

    @Parameter(title: "alarmID")
    public var alarmID: String

    public init(alarmID: String) { self.alarmID = alarmID }
    public init() { self.alarmID = "" }

    public func perform() async throws -> some IntentResult {
        UserDefaults.standard.set(alarmID, forKey: WakeIntentBridge.pendingAlarmKey)
        return .result()
    }
}

public struct StopWakeIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "アラーム停止"
    public static var description = IntentDescription("アラーム停止を起床記録へ反映します")

    @Parameter(title: "alarmID")
    public var alarmID: String

    public init(alarmID: String) { self.alarmID = alarmID }
    public init() { self.alarmID = "" }

    public func perform() async throws -> some IntentResult {
        WakeIntentBridge.markWakeCompleted()
        return .result()
    }
}

public struct OpenTimerIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "IGNIDO Wakeを開く"
    public static var description = IntentDescription("タイマー終了画面を開きます")
    public static var openAppWhenRun = true

    public init() {}

    public func perform() async throws -> some IntentResult {
        UserDefaults.standard.set(true, forKey: WakeIntentBridge.pendingTimerKey)
        return .result()
    }
}

enum WakeIntentState {
    static func consumeAlarmID() -> UUID? {
        let defaults = UserDefaults.standard
        guard let raw = defaults.string(forKey: WakeIntentBridge.pendingAlarmKey) else { return nil }
        defaults.removeObject(forKey: WakeIntentBridge.pendingAlarmKey)
        return UUID(uuidString: raw)
    }

    static func consumeTimer() -> Bool {
        let defaults = UserDefaults.standard
        let pending = defaults.bool(forKey: WakeIntentBridge.pendingTimerKey)
        if pending { defaults.removeObject(forKey: WakeIntentBridge.pendingTimerKey) }
        return pending
    }
}
