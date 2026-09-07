import Foundation
import SwiftUI
import AlarmKit

@MainActor
final class AlarmStore: ObservableObject {
    @Published private(set) var alarms: [WakeAlarm] = []
    @Published var authorizationState: AlarmManager.AuthorizationState = AlarmManager.shared.authorizationState
    @Published var lastError: String?

    private let manager = AlarmManager.shared
    private let storageKey = "ignido.ios.alarms.v1"

    init() {
        load()
    }

    func bootstrap() async {
        authorizationState = manager.authorizationState
        Task { [weak self] in
            guard let self else { return }
            for await state in manager.authorizationUpdates {
                await MainActor.run { self.authorizationState = state }
            }
        }
    }

    func requestAuthorization() async -> Bool {
        do {
            let state = try await manager.requestAuthorization()
            authorizationState = state
            return state == .authorized
        } catch {
            lastError = error.localizedDescription
            return false
        }
    }

    func add(hour: Int, minute: Int, label: String, weekdays: Set<Int>) async {
        let item = WakeAlarm(hour: hour, minute: minute, label: label.isEmpty ? "アラーム" : label, weekdays: weekdays)
        alarms.append(item)
        save()
        await schedule(item)
    }

    func delete(at offsets: IndexSet) {
        let targets = offsets.map { alarms[$0] }
        for item in targets { try? manager.cancel(id: item.id) }
        alarms.remove(atOffsets: offsets)
        save()
    }

    func setEnabled(_ item: WakeAlarm, enabled: Bool) async {
        guard let index = alarms.firstIndex(where: { $0.id == item.id }) else { return }
        alarms[index].enabled = enabled
        let updated = alarms[index]
        save()
        if enabled {
            await schedule(updated)
        } else {
            try? manager.cancel(id: updated.id)
        }
    }

    func schedule(_ item: WakeAlarm) async {
        guard item.enabled else { return }
        if authorizationState != .authorized {
            guard await requestAuthorization() else { return }
        }
        do {
            try? manager.cancel(id: item.id)
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
            _ = try await manager.schedule(id: item.id, configuration: configuration)
            lastError = nil
        } catch {
            lastError = error.localizedDescription
        }
    }

    func rescheduleAll() async {
        for alarm in alarms where alarm.enabled { await schedule(alarm) }
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
