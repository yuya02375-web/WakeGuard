import Foundation
import SwiftUI
import AlarmKit

@MainActor
final class StopwatchStore: ObservableObject {
    @Published var running = false { didSet { save() } }
    @Published var accumulated: TimeInterval = 0 { didSet { save() } }
    @Published var startedAt: Date? { didSet { save() } }
    @Published var laps: [StopwatchLap] = [] { didSet { save() } }
    private let key = "ignido.stopwatch.v2"

    init() { load() }

    var elapsed: TimeInterval {
        accumulated + (running ? Date().timeIntervalSince(startedAt ?? Date()) : 0)
    }

    func toggle() {
        if running {
            accumulated += Date().timeIntervalSince(startedAt ?? Date())
            startedAt = nil
        } else {
            startedAt = Date()
        }
        running.toggle()
    }

    func lap() {
        guard running else { return }
        let total = elapsed
        let previous = laps.last?.total ?? 0
        laps.append(StopwatchLap(index: laps.count + 1, lap: total - previous, total: total))
    }

    func reset() {
        running = false
        accumulated = 0
        startedAt = nil
        laps = []
    }

    private struct Snapshot: Codable {
        var running: Bool
        var accumulated: TimeInterval
        var startedAt: Date?
        var laps: [StopwatchLap]
    }
    private func save() {
        let snap = Snapshot(running: running, accumulated: accumulated, startedAt: startedAt, laps: laps)
        if let data = try? JSONEncoder().encode(snap) { UserDefaults.standard.set(data, forKey: key) }
    }
    private func load() {
        guard let data = UserDefaults.standard.data(forKey: key), let snap = try? JSONDecoder().decode(Snapshot.self, from: data) else { return }
        running = snap.running
        accumulated = snap.accumulated
        startedAt = snap.startedAt
        laps = snap.laps
    }
}

@MainActor
final class TimerStore: ObservableObject {
    @Published var running = false { didSet { save() } }
    @Published var paused = false { didSet { save() } }
    @Published var endDate: Date? { didSet { save() } }
    @Published var pausedRemaining: TimeInterval = 0 { didSet { save() } }
    @Published var originalDuration: TimeInterval = 0 { didSet { save() } }
    @Published var mediaFileName: String? { didSet { save() } }
    @Published var lastError: String?
    private var systemAlarmID: UUID
    private let key = "ignido.timer.v2"

    init() {
        if let raw = UserDefaults.standard.string(forKey: "ignido.timer.alarm.id"), let id = UUID(uuidString: raw) { systemAlarmID = id }
        else {
            let id = UUID(); systemAlarmID = id; UserDefaults.standard.set(id.uuidString, forKey: "ignido.timer.alarm.id")
        }
        load()
    }

    var remaining: TimeInterval {
        if running, let endDate { return max(0, endDate.timeIntervalSinceNow) }
        return max(0, pausedRemaining)
    }

    var finished: Bool { running && remaining <= 0.1 }

    func start(seconds: TimeInterval) async {
        guard seconds > 0 else { return }
        originalDuration = seconds
        pausedRemaining = seconds
        running = true
        paused = false
        endDate = Date().addingTimeInterval(seconds)
        await scheduleSystemAlarm(after: seconds)
    }

    func pause() {
        guard running else { return }
        pausedRemaining = remaining
        running = false
        paused = true
        endDate = nil
        try? AlarmManager.shared.cancel(id: systemAlarmID)
    }

    func resume() async {
        guard pausedRemaining > 0 else { return }
        running = true
        paused = false
        endDate = Date().addingTimeInterval(pausedRemaining)
        await scheduleSystemAlarm(after: pausedRemaining)
    }

    func reset() {
        running = false
        paused = false
        endDate = nil
        pausedRemaining = 0
        originalDuration = 0
        try? AlarmManager.shared.cancel(id: systemAlarmID)
    }

    func markFinished() {
        if finished {
            running = false
            paused = false
            endDate = nil
            pausedRemaining = 0
        }
    }

    private func scheduleSystemAlarm(after seconds: TimeInterval) async {
        do {
            if AlarmManager.shared.authorizationState != .authorized { _ = try await AlarmManager.shared.requestAuthorization() }
            try? AlarmManager.shared.cancel(id: systemAlarmID)
            let schedule: Alarm.Schedule = .fixed(Date().addingTimeInterval(seconds))
            let alert = AlarmPresentation.Alert(title: "タイマー終了")
            let attributes = AlarmAttributes<EmptyWakeMetadata>(
                presentation: AlarmPresentation(alert: alert),
                metadata: EmptyWakeMetadata(),
                tintColor: Color(red: 0.95, green: 0.16, blue: 0.08)
            )
            let configuration = AlarmManager.AlarmConfiguration.alarm(schedule: schedule, attributes: attributes, sound: .default)
            _ = try await AlarmManager.shared.schedule(id: systemAlarmID, configuration: configuration)
            lastError = nil
        } catch { lastError = error.localizedDescription }
    }

    private struct Snapshot: Codable {
        var running: Bool
        var paused: Bool
        var endDate: Date?
        var pausedRemaining: TimeInterval
        var originalDuration: TimeInterval
        var mediaFileName: String?
    }
    private func save() {
        let snap = Snapshot(running: running, paused: paused, endDate: endDate, pausedRemaining: pausedRemaining, originalDuration: originalDuration, mediaFileName: mediaFileName)
        if let data = try? JSONEncoder().encode(snap) { UserDefaults.standard.set(data, forKey: key) }
    }
    private func load() {
        guard let data = UserDefaults.standard.data(forKey: key), let snap = try? JSONDecoder().decode(Snapshot.self, from: data) else { return }
        running = snap.running
        paused = snap.paused
        endDate = snap.endDate
        pausedRemaining = snap.pausedRemaining
        originalDuration = snap.originalDuration
        mediaFileName = snap.mediaFileName
        if running, let endDate, endDate <= Date() {
            running = false; paused = false; pausedRemaining = 0; self.endDate = nil
        }
    }
}

@MainActor
final class WorldClockStore: ObservableObject {
    @Published var items: [WorldClockItem] = [] { didSet { save() } }
    @Published var use24Hour = true { didSet { UserDefaults.standard.set(use24Hour, forKey: "ignido.worldclock.24h") } }
    @Published var displayMode = 0 { didSet { UserDefaults.standard.set(displayMode, forKey: "ignido.worldclock.mode") } } // 0 digital, 1 analog, 2 both
    private let key = "ignido.worldclock.items.v2"

    init() {
        use24Hour = UserDefaults.standard.object(forKey: "ignido.worldclock.24h") as? Bool ?? true
        displayMode = UserDefaults.standard.integer(forKey: "ignido.worldclock.mode")
        if let data = UserDefaults.standard.data(forKey: key), let decoded = try? JSONDecoder().decode([WorldClockItem].self, from: data) { items = decoded }
        if items.isEmpty { items = [WorldClockItem(timeZoneIdentifier: TimeZone.current.identifier, displayName: "現在地")] }
    }

    func add(_ identifier: String) {
        guard !items.contains(where: { $0.timeZoneIdentifier == identifier }) else { return }
        items.append(WorldClockItem(timeZoneIdentifier: identifier))
    }
    func remove(at offsets: IndexSet) { items.remove(atOffsets: offsets) }
    private func save() { if let data = try? JSONEncoder().encode(items) { UserDefaults.standard.set(data, forKey: key) } }
}

@MainActor
final class StreakStore: ObservableObject {
    @Published private(set) var wakeDates: [Date] = []
    private let key = "ignido.streak.dates.v2"
    init() { load() }

    var currentStreak: Int {
        let cal = Calendar.current
        let days = Set(wakeDates.map { cal.startOfDay(for: $0) })
        var cursor = cal.startOfDay(for: Date())
        if !days.contains(cursor), let yesterday = cal.date(byAdding: .day, value: -1, to: cursor), days.contains(yesterday) { cursor = yesterday }
        var count = 0
        while days.contains(cursor) {
            count += 1
            guard let prev = cal.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = prev
        }
        return count
    }

    var bestStreak: Int {
        let cal = Calendar.current
        let days = Array(Set(wakeDates.map { cal.startOfDay(for: $0) })).sorted()
        guard !days.isEmpty else { return 0 }
        var best = 1, current = 1
        for i in 1..<days.count {
            let diff = cal.dateComponents([.day], from: days[i-1], to: days[i]).day ?? 0
            if diff == 1 { current += 1; best = max(best, current) } else if diff > 1 { current = 1 }
        }
        return best
    }

    var flameStage: Int {
        switch currentStreak {
        case 0...2: return 0
        case 3...6: return 1
        case 7...13: return 2
        case 14...29: return 3
        default: return 4
        }
    }

    func recordWake() {
        let today = Calendar.current.startOfDay(for: Date())
        guard !wakeDates.contains(where: { Calendar.current.isDate($0, inSameDayAs: today) }) else { return }
        wakeDates.append(today)
        save()
    }
    private func save() { if let data = try? JSONEncoder().encode(wakeDates) { UserDefaults.standard.set(data, forKey: key) } }
    private func load() { if let data = UserDefaults.standard.data(forKey: key), let d = try? JSONDecoder().decode([Date].self, from: data) { wakeDates = d } }
}

enum MediaLibrary {
    static func importFile(from url: URL) throws -> String {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let fm = FileManager.default
        let dir = fm.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("WakeMedia", isDirectory: true)
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        let ext = url.pathExtension.isEmpty ? "bin" : url.pathExtension
        let name = "\(UUID().uuidString).\(ext)"
        let dst = dir.appendingPathComponent(name)
        if fm.fileExists(atPath: dst.path) { try fm.removeItem(at: dst) }
        try fm.copyItem(at: url, to: dst)
        return name
    }

    static func url(for fileName: String?) -> URL? {
        guard let fileName else { return nil }
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("WakeMedia", isDirectory: true)
        let url = base.appendingPathComponent(fileName)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }
}
