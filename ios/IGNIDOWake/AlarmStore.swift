import Foundation
import SwiftUI
import AlarmKit
import UserNotifications

@MainActor
final class AlarmStore: ObservableObject {
    @Published private(set) var alarms: [WakeAlarm] = []
    @Published var authorizationState: AlarmManager.AuthorizationState = AlarmManager.shared.authorizationState
    @Published var lastError: String?

    private let storageKey = "ignido.ios.alarms.v1"
    private let center = UNUserNotificationCenter.current()

    init() { load() }

    func bootstrap() async {
        authorizationState = AlarmManager.shared.authorizationState
        Task { [weak self] in
            guard let self else { return }
            for await state in AlarmManager.shared.authorizationUpdates {
                await MainActor.run { self.authorizationState = state }
            }
        }
        await rescheduleAll()
    }

    func requestAuthorization() async -> Bool {
        do {
            let state = try await AlarmManager.shared.requestAuthorization()
            authorizationState = state
            return state == .authorized
        } catch {
            lastError = error.localizedDescription
            return false
        }
    }

    func add(_ item: WakeAlarm) async {
        alarms.append(item)
        save()
        await schedule(item)
    }

    func add(hour: Int, minute: Int, label: String, weekdays: Set<Int>) async {
        await add(WakeAlarm(hour: hour, minute: minute, label: label.isEmpty ? "アラーム" : label, weekdays: weekdays))
    }

    func update(_ item: WakeAlarm) async {
        guard let index = alarms.firstIndex(where: { $0.id == item.id }) else { return }
        alarms[index] = item
        save()
        await cancelScheduling(for: item.id)
        if item.enabled { await schedule(item) }
    }

    func alarm(id: UUID) -> WakeAlarm? { alarms.first { $0.id == id } }

    func delete(at offsets: IndexSet) {
        let targets = offsets.compactMap { alarms.indices.contains($0) ? alarms[$0] : nil }
        alarms.remove(atOffsets: offsets)
        save()
        Task {
            for item in targets { await cancelScheduling(for: item.id) }
        }
    }

    func delete(_ item: WakeAlarm) {
        guard let index = alarms.firstIndex(where: { $0.id == item.id }) else { return }
        alarms.remove(at: index)
        save()
        Task { await cancelScheduling(for: item.id) }
    }

    func setEnabled(_ item: WakeAlarm, enabled: Bool) async {
        guard let index = alarms.firstIndex(where: { $0.id == item.id }) else { return }
        alarms[index].enabled = enabled
        let updated = alarms[index]
        save()
        if enabled {
            await schedule(updated)
        } else {
            await cancelScheduling(for: updated.id)
        }
    }

    func schedule(_ item: WakeAlarm) async {
        guard item.enabled else { return }
        if authorizationState != .authorized {
            guard await requestAuthorization() else { return }
        }
        do {
            try? AlarmManager.shared.cancel(id: item.id)
            let time = Alarm.Schedule.Relative.Time(hour: item.hour, minute: item.minute)
            let recurrence: Alarm.Schedule.Relative.Recurrence = item.weekdays.isEmpty ? .never : .weekly(localeWeekdays(item.weekdays))
            let schedule: Alarm.Schedule = .relative(.init(time: time, repeats: recurrence))
            let alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: item.label))
            let presentation = AlarmPresentation(alert: alert)
            let attributes = AlarmAttributes<EmptyWakeMetadata>(
                presentation: presentation,
                metadata: EmptyWakeMetadata(),
                tintColor: Color(red: 0.95, green: 0.16, blue: 0.08)
            )
            let configuration = AlarmManager.AlarmConfiguration.alarm(
                schedule: schedule,
                attributes: attributes,
                sound: .default
            )
            _ = try await AlarmManager.shared.schedule(id: item.id, configuration: configuration)
            await schedulePreAlert(for: item)
            lastError = nil
        } catch {
            lastError = error.localizedDescription
        }
    }

    func scheduleTest(_ item: WakeAlarm, after seconds: TimeInterval = 5) async {
        if authorizationState != .authorized {
            guard await requestAuthorization() else { return }
        }
        do {
            let id = UUID()
            let schedule: Alarm.Schedule = .fixed(Date().addingTimeInterval(seconds))
            let alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: "テスト: \(item.label)"))
            let attributes = AlarmAttributes<EmptyWakeMetadata>(
                presentation: AlarmPresentation(alert: alert),
                metadata: EmptyWakeMetadata(),
                tintColor: Color(red: 0.95, green: 0.16, blue: 0.08)
            )
            let configuration = AlarmManager.AlarmConfiguration.alarm(schedule: schedule, attributes: attributes, sound: .default)
            _ = try await AlarmManager.shared.schedule(id: id, configuration: configuration)
            lastError = nil
        } catch {
            lastError = error.localizedDescription
        }
    }

    func rescheduleAll() async {
        for alarm in alarms where alarm.enabled { await schedule(alarm) }
    }

    private func cancelScheduling(for id: UUID) async {
        try? AlarmManager.shared.cancel(id: id)
        center.removePendingNotificationRequests(withIdentifiers: preAlertIdentifiers(for: id))
        center.removeDeliveredNotifications(withIdentifiers: preAlertIdentifiers(for: id))
    }

    private func schedulePreAlert(for item: WakeAlarm) async {
        let ids = preAlertIdentifiers(for: item.id)
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
        guard item.preAlertMinutes > 0 else { return }
        do {
            _ = try await center.requestAuthorization(options: [.alert, .badge])
            if item.weekdays.isEmpty {
                guard let alarmDate = nextAlarmDate(hour: item.hour, minute: item.minute) else { return }
                let preDate = alarmDate.addingTimeInterval(TimeInterval(-item.preAlertMinutes * 60))
                guard preDate > Date() else { return }
                let content = preAlertContent(item: item, alarmDate: alarmDate)
                let trigger = UNCalendarNotificationTrigger(dateMatching: Calendar.current.dateComponents([.year,.month,.day,.hour,.minute], from: preDate), repeats: false)
                try await center.add(UNNotificationRequest(identifier: "pre-\(item.id.uuidString)-one", content: content, trigger: trigger))
            } else {
                for userDay in item.weekdays.sorted() {
                    guard let alarmDate = nextDate(forUserWeekday: userDay, hour: item.hour, minute: item.minute) else { continue }
                    let preDate = alarmDate.addingTimeInterval(TimeInterval(-item.preAlertMinutes * 60))
                    let comps = Calendar.current.dateComponents([.weekday,.hour,.minute], from: preDate)
                    let content = preAlertContent(item: item, alarmDate: alarmDate)
                    let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
                    try await center.add(UNNotificationRequest(identifier: "pre-\(item.id.uuidString)-\(userDay)", content: content, trigger: trigger))
                }
            }
        } catch {
            lastError = error.localizedDescription
        }
    }

    private func preAlertContent(item: WakeAlarm, alarmDate: Date) -> UNMutableNotificationContent {
        let c = UNMutableNotificationContent()
        c.title = "IGNIDO Wake"
        c.body = "\(item.preAlertMinutes)分後にアラーム　\(item.timeText)"
        c.sound = nil
        c.interruptionLevel = .passive
        c.userInfo = ["alarmID": item.id.uuidString]
        return c
    }

    private func preAlertIdentifiers(for id: UUID) -> [String] {
        ["pre-\(id.uuidString)-one"] + (1...7).map { "pre-\(id.uuidString)-\($0)" }
    }

    private func nextAlarmDate(hour: Int, minute: Int) -> Date? {
        Calendar.current.nextDate(after: Date(), matching: DateComponents(hour: hour, minute: minute), matchingPolicy: .nextTime, direction: .forward)
    }

    private func nextDate(forUserWeekday userDay: Int, hour: Int, minute: Int) -> Date? {
        let calendarWeekday = userDay == 7 ? 1 : userDay + 1
        return Calendar.current.nextDate(after: Date(), matching: DateComponents(weekday: calendarWeekday, hour: hour, minute: minute), matchingPolicy: .nextTime, direction: .forward)
    }

    private func localeWeekdays(_ days: Set<Int>) -> [Locale.Weekday] {
        days.sorted().compactMap {
            switch $0 {
            case 1: return .monday
            case 2: return .tuesday
            case 3: return .wednesday
            case 4: return .thursday
            case 5: return .friday
            case 6: return .saturday
            case 7: return .sunday
            default: return nil
            }
        }
    }

    private func save() {
        if let data = try? JSONEncoder().encode(alarms) { UserDefaults.standard.set(data, forKey: storageKey) }
    }

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([WakeAlarm].self, from: data) else { return }
        alarms = decoded
    }
}

struct EmptyWakeMetadata: AlarmMetadata {}
