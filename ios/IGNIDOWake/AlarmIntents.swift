import Foundation
import AppIntents

private enum WakeIntentBridge {
    static let pendingAlarmQueueKey = "ignido.intent.pendingAlarmQueue"
    static let pendingTimerKey = "ignido.intent.pendingTimer"
    static let streakKey = "ignido.streak.dates.v2"

    static func enqueueAlarm(_ alarmID: String) {
        let defaults = UserDefaults.standard
        var queue = defaults.stringArray(forKey: pendingAlarmQueueKey) ?? []
        if !queue.contains(alarmID) { queue.append(alarmID) }
        defaults.set(queue, forKey: pendingAlarmQueueKey)
    }

    static func dequeueAlarm() -> String? {
        let defaults = UserDefaults.standard
        var queue = defaults.stringArray(forKey: pendingAlarmQueueKey) ?? []
        guard !queue.isEmpty else { return nil }
        let first = queue.removeFirst()
        defaults.set(queue, forKey: pendingAlarmQueueKey)
        return first
    }

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
    public static let title: LocalizedStringResource = "IGNIDO Wakeを開く"
    public static let description = IntentDescription("アラームの解除画面を開きます")
    public static let openAppWhenRun = true

    @Parameter(title: "alarmID")
    public var alarmID: String

    public init(alarmID: String) { self.alarmID = alarmID }
    public init() { self.alarmID = "" }

    public func perform() async throws -> some IntentResult {
        WakeIntentBridge.enqueueAlarm(alarmID)
        return .result()
    }
}

public struct StopWakeIntent: LiveActivityIntent {
    public static let title: LocalizedStringResource = "アラーム停止"
    public static let description = IntentDescription("アラーム停止を起床記録へ反映します")

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
    public static let title: LocalizedStringResource = "IGNIDO Wakeを開く"
    public static let description = IntentDescription("タイマー終了画面を開きます")
    public static let openAppWhenRun = true

    public init() {}

    public func perform() async throws -> some IntentResult {
        UserDefaults.standard.set(true, forKey: WakeIntentBridge.pendingTimerKey)
        return .result()
    }
}

enum WakeIntentState {
    static func consumeAlarmID() -> UUID? {
        guard let raw = WakeIntentBridge.dequeueAlarm() else { return nil }
        return UUID(uuidString: raw)
    }

    static func consumeTimer() -> Bool {
        let defaults = UserDefaults.standard
        let pending = defaults.bool(forKey: WakeIntentBridge.pendingTimerKey)
        if pending { defaults.removeObject(forKey: WakeIntentBridge.pendingTimerKey) }
        return pending
    }
}
