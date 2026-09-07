import Foundation

struct WakeAlarm: Identifiable, Codable, Hashable {
    var id: UUID
    var hour: Int
    var minute: Int
    var label: String
    var enabled: Bool
    var weekdays: Set<Int>

    init(id: UUID = UUID(), hour: Int, minute: Int, label: String = "アラーム", enabled: Bool = true, weekdays: Set<Int> = []) {
        self.id = id
        self.hour = hour
        self.minute = minute
        self.label = label
        self.enabled = enabled
        self.weekdays = weekdays
    }

    var timeText: String { String(format: "%02d:%02d", hour, minute) }

    var repeatText: String {
        guard !weekdays.isEmpty else { return "1回" }
        let names = [1:"月", 2:"火", 3:"水", 4:"木", 5:"金", 6:"土", 7:"日"]
        return weekdays.sorted().compactMap { names[$0] }.joined(separator: " ")
    }
}
