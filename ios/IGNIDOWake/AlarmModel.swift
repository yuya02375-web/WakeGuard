import Foundation

enum AlarmMission: String, Codable, CaseIterable, Identifiable {
    case none, steps, math, taps, code, shake, memory, sentence, hold, swipe, order, reverse, random
    var id: String { rawValue }
    var title: String {
        switch self {
        case .none: return "なし"
        case .steps: return "歩数"
        case .math: return "計算"
        case .taps: return "連打"
        case .code: return "コード入力"
        case .shake: return "シェイク"
        case .memory: return "記憶"
        case .sentence: return "文章入力"
        case .hold: return "長押し"
        case .swipe: return "スワイプ"
        case .order: return "順番タップ"
        case .reverse: return "逆順入力"
        case .random: return "ランダム"
        }
    }
    var defaultTarget: Int {
        switch self {
        case .none: return 0
        case .steps: return 20
        case .math: return 3
        case .taps: return 30
        case .code: return 1
        case .shake: return 20
        case .memory: return 1
        case .sentence: return 1
        case .hold: return 5
        case .swipe: return 5
        case .order: return 1
        case .reverse: return 1
        case .random: return 1
        }
    }
    static var randomCandidates: [AlarmMission] {
        allCases.filter { $0 != .none && $0 != .random }
    }
}

enum VibrationMode: String, Codable, CaseIterable, Identifiable {
    case off, irregular, strong
    var id: String { rawValue }
    var title: String {
        switch self {
        case .off: return "オフ"
        case .irregular: return "不規則"
        case .strong: return "強い連続パルス"
        }
    }
}

struct WakeAlarm: Identifiable, Codable, Hashable {
    var id: UUID
    var hour: Int
    var minute: Int
    var label: String
    var enabled: Bool
    var weekdays: Set<Int>
    var mission: AlarmMission
    var missionTarget: Int
    var unlockCode: String
    var unlockSentence: String
    var soundName: String
    var mediaFileName: String?
    var volume: Double
    var vibration: VibrationMode
    var preAlertMinutes: Int
    var snoozeMinutes: Int
    var createdAt: Date

    init(
        id: UUID = UUID(), hour: Int, minute: Int, label: String = "アラーム",
        enabled: Bool = true, weekdays: Set<Int> = [], mission: AlarmMission = .none,
        missionTarget: Int? = nil, unlockCode: String = "1234",
        unlockSentence: String = "起きました", soundName: String = "デフォルト",
        mediaFileName: String? = nil, volume: Double = 1.0,
        vibration: VibrationMode = .strong, preAlertMinutes: Int = 30,
        snoozeMinutes: Int = 5, createdAt: Date = Date()
    ) {
        self.id = id
        self.hour = hour
        self.minute = minute
        self.label = label
        self.enabled = enabled
        self.weekdays = weekdays
        self.mission = mission
        self.missionTarget = missionTarget ?? mission.defaultTarget
        self.unlockCode = unlockCode
        self.unlockSentence = unlockSentence
        self.soundName = soundName
        self.mediaFileName = mediaFileName
        self.volume = volume
        self.vibration = vibration
        self.preAlertMinutes = preAlertMinutes
        self.snoozeMinutes = snoozeMinutes
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id, hour, minute, label, enabled, weekdays, mission, missionTarget, unlockCode,
             unlockSentence, soundName, mediaFileName, volume, vibration, preAlertMinutes,
             snoozeMinutes, createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        hour = try c.decodeIfPresent(Int.self, forKey: .hour) ?? 7
        minute = try c.decodeIfPresent(Int.self, forKey: .minute) ?? 0
        label = try c.decodeIfPresent(String.self, forKey: .label) ?? "アラーム"
        enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        weekdays = try c.decodeIfPresent(Set<Int>.self, forKey: .weekdays) ?? []
        mission = try c.decodeIfPresent(AlarmMission.self, forKey: .mission) ?? .none
        missionTarget = try c.decodeIfPresent(Int.self, forKey: .missionTarget) ?? mission.defaultTarget
        unlockCode = try c.decodeIfPresent(String.self, forKey: .unlockCode) ?? "1234"
        unlockSentence = try c.decodeIfPresent(String.self, forKey: .unlockSentence) ?? "起きました"
        soundName = try c.decodeIfPresent(String.self, forKey: .soundName) ?? "デフォルト"
        mediaFileName = try c.decodeIfPresent(String.self, forKey: .mediaFileName)
        volume = try c.decodeIfPresent(Double.self, forKey: .volume) ?? 1.0
        vibration = try c.decodeIfPresent(VibrationMode.self, forKey: .vibration) ?? .strong
        preAlertMinutes = try c.decodeIfPresent(Int.self, forKey: .preAlertMinutes) ?? 30
        snoozeMinutes = try c.decodeIfPresent(Int.self, forKey: .snoozeMinutes) ?? 5
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
    }

    var timeText: String { String(format: "%02d:%02d", hour, minute) }

    var repeatText: String {
        guard !weekdays.isEmpty else { return "1回" }
        let names = [1:"月", 2:"火", 3:"水", 4:"木", 5:"金", 6:"土", 7:"日"]
        return weekdays.sorted().compactMap { names[$0] }.joined(separator: " ")
    }
}

struct WorldClockItem: Identifiable, Codable, Hashable {
    var id = UUID()
    var timeZoneIdentifier: String
    var displayName: String
    init(timeZoneIdentifier: String, displayName: String? = nil) {
        self.timeZoneIdentifier = timeZoneIdentifier
        self.displayName = displayName ?? timeZoneIdentifier.split(separator: "/").last.map(String.init)?.replacingOccurrences(of: "_", with: " ") ?? timeZoneIdentifier
    }
}

struct StopwatchLap: Identifiable, Codable, Hashable {
    var id = UUID()
    var index: Int
    var lap: TimeInterval
    var total: TimeInterval
}
