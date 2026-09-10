from pathlib import Path
root=Path('ios/IGNIDOWake'); p=root/'AlarmIntents.swift'; s=p.read_text()
s=s.replace('''struct WakePendingAlarmRequest: Codable, Hashable {
    let alarmID: String
    let createdAt: Date
}
''','''struct WakePendingAlarmRequest: Codable, Hashable {
    let alarmID: String
    let systemAlarmID: String?
    let allowStoppedSource: Bool?
    let createdAt: Date
}
''',1)
old='''    static func enqueueAlarm(_ alarmID: String) {
        guard !alarmID.isEmpty else { return }
        clearLegacyAlarmRequests()
        var q = alarmRequests().filter { Date().timeIntervalSince($0.createdAt) <= 120 }
        q.removeAll { $0.alarmID == alarmID }
        q.append(.init(alarmID: alarmID, createdAt: Date()))
        save(q)
    }
'''
new='''    static func enqueueAlarm(_ alarmID: String, systemAlarmID: String? = nil, allowStoppedSource: Bool = false) {
        guard !alarmID.isEmpty else { return }
        clearLegacyAlarmRequests()
        var q = alarmRequests().filter { Date().timeIntervalSince($0.createdAt) <= 120 }
        q.removeAll { $0.alarmID == alarmID }
        q.append(.init(alarmID: alarmID, systemAlarmID: systemAlarmID ?? alarmID, allowStoppedSource: allowStoppedSource, createdAt: Date()))
        save(q)
    }
'''
assert old in s; s=s.replace(old,new,1)
old='''    @Parameter(title: "weekdaysMask") public var weekdaysMask: Int

    public init(alarmID: String, hour: Int, minute: Int, weekdaysMask: Int) {
        self.alarmID = alarmID; self.hour = hour; self.minute = minute; self.weekdaysMask = weekdaysMask
    }
    public init() { alarmID = ""; hour = 0; minute = 0; weekdaysMask = 0 }

    public func perform() async throws -> some IntentResult {
        guard WakeIntentBridge.wallClockIsNear(hour: hour, minute: minute, weekdaysMask: weekdaysMask) else { return .result() }
        WakeIntentBridge.enqueueAlarm(alarmID)
        return .result()
    }
'''
new='''    @Parameter(title: "weekdaysMask") public var weekdaysMask: Int
    @Parameter(title: "systemAlarmID") public var systemAlarmID: String

    public init(alarmID: String, hour: Int, minute: Int, weekdaysMask: Int, systemAlarmID: String? = nil) {
        self.alarmID = alarmID; self.hour = hour; self.minute = minute; self.weekdaysMask = weekdaysMask
        self.systemAlarmID = systemAlarmID ?? alarmID
    }
    public init() { alarmID = ""; hour = 0; minute = 0; weekdaysMask = 0; systemAlarmID = "" }

    public func perform() async throws -> some IntentResult {
        guard WakeIntentBridge.wallClockIsNear(hour: hour, minute: minute, weekdaysMask: weekdaysMask) else { return .result() }
        WakeIntentBridge.enqueueAlarm(alarmID, systemAlarmID: systemAlarmID, allowStoppedSource: false)
        return .result()
    }
'''
assert old in s; s=s.replace(old,new,1)
start=s.index('public struct StopWakeIntent: LiveActivityIntent {'); end=s.index('\npublic struct OpenTimerIntent:',start)
s=s[:start]+r'''public struct StopWakeIntent: LiveActivityIntent {
    public static let title: LocalizedStringResource = "IGNIDO Wakeで解除"
    public static let description = IntentDescription("システム音を止めてもIGNIDO Wakeの解除画面を開きます")
    public static let openAppWhenRun = true
    @Parameter(title: "alarmID") public var alarmID: String
    @Parameter(title: "hour") public var hour: Int
    @Parameter(title: "minute") public var minute: Int
    @Parameter(title: "weekdaysMask") public var weekdaysMask: Int
    @Parameter(title: "systemAlarmID") public var systemAlarmID: String
    public init(alarmID: String, hour: Int, minute: Int, weekdaysMask: Int, systemAlarmID: String? = nil) {
        self.alarmID=alarmID; self.hour=hour; self.minute=minute; self.weekdaysMask=weekdaysMask; self.systemAlarmID=systemAlarmID ?? alarmID
    }
    public init() { alarmID=""; hour=0; minute=0; weekdaysMask=0; systemAlarmID="" }
    public func perform() async throws -> some IntentResult {
        guard WakeIntentBridge.wallClockIsNear(hour: hour, minute: minute, weekdaysMask: weekdaysMask) else { return .result() }
        WakeIntentBridge.enqueueAlarm(alarmID, systemAlarmID: systemAlarmID, allowStoppedSource: true)
        return .result()
    }
}
'''+s[end:]
p.write_text(s)
t=p.read_text(); assert 'allowStoppedSource: true' in t and 'systemAlarmID' in t and 'markWakeCompleted(); return .result()' not in t
print('v196b intent handoff applied')