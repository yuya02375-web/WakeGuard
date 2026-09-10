from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# Version 1.9.3 / build 22.
p=root/'Info.plist'; s=p.read_text(); s=re.sub(r'(<key>CFBundleShortVersionString</key><string>)[^<]+',r'\g<1>1.9.3',s,1); s=re.sub(r'(<key>CFBundleVersion</key><string>)[^<]+',r'\g<1>22',s,1); p.write_text(s)
p=Path('ios/project.yml'); s=p.read_text(); s=re.sub(r'CFBundleShortVersionString: "[^"]+"','CFBundleShortVersionString: "1.9.3"',s); s=re.sub(r'CFBundleVersion: "[^"]+"','CFBundleVersion: "22"',s); p.write_text(s)

# The v1.9.2 intent timestamp was the time the Open button was tapped, not the
# time the alarm fired. A 07:00 alarm left alerting could therefore create a
# fresh request at 16:00 and open the 07:00 mission. v1.9.3 validates the
# configured wall-clock occurrence inside the intent as well as in RootView.
(root/'AlarmIntents.swift').write_text(r'''import Foundation
import AppIntents

struct WakePendingAlarmRequest: Codable, Hashable {
    let alarmID: String
    let createdAt: Date
}

private enum WakeIntentBridge {
    static let legacyPendingAlarmQueueKey = "ignido.intent.pendingAlarmQueue"
    static let oldRequestKey = "ignido.intent.pendingAlarmRequests.v2"
    static let requestKey = "ignido.intent.pendingAlarmRequests.v3"
    static let pendingTimerKey = "ignido.intent.pendingTimer"
    static let pendingTimerQueueKey = "ignido.intent.pendingTimerQueue"
    static let streakKey = "ignido.streak.dates.v2"

    static func clearLegacyAlarmRequests() {
        let d = UserDefaults.standard
        d.removeObject(forKey: legacyPendingAlarmQueueKey)
        d.removeObject(forKey: oldRequestKey)
    }

    static func enqueueAlarm(_ alarmID: String) {
        guard !alarmID.isEmpty else { return }
        clearLegacyAlarmRequests()
        var q = alarmRequests().filter { Date().timeIntervalSince($0.createdAt) <= 120 }
        q.removeAll { $0.alarmID == alarmID }
        q.append(.init(alarmID: alarmID, createdAt: Date()))
        save(q)
    }

    static func dequeueAlarm() -> WakePendingAlarmRequest? {
        clearLegacyAlarmRequests()
        var q = alarmRequests().filter { Date().timeIntervalSince($0.createdAt) <= 120 }
        guard !q.isEmpty else { save([]); return nil }
        let x = q.removeFirst(); save(q); return x
    }

    static func clearExpiredAlarmRequests() {
        clearLegacyAlarmRequests()
        save(alarmRequests().filter { Date().timeIntervalSince($0.createdAt) <= 120 })
    }

    static func wallClockIsNear(hour: Int, minute: Int, weekdaysMask: Int, now: Date = Date(), maxLate: TimeInterval = 1800) -> Bool {
        let c = Calendar.current
        for dayOffset in [0, -1] {
            guard let day = c.date(byAdding: .day, value: dayOffset, to: now),
                  let fire = c.date(bySettingHour: hour, minute: minute, second: 0, of: day) else { continue }
            let delta = now.timeIntervalSince(fire)
            guard delta >= -60 && delta <= maxLate else { continue }
            if weekdaysMask == 0 { return true }
            let sw = c.component(.weekday, from: fire)
            let userDay = sw == 1 ? 7 : sw - 1
            let bit = 1 << (userDay - 1)
            if weekdaysMask & bit != 0 { return true }
        }
        return false
    }

    private static func alarmRequests() -> [WakePendingAlarmRequest] {
        guard let data = UserDefaults.standard.data(forKey: requestKey),
              let q = try? JSONDecoder().decode([WakePendingAlarmRequest].self, from: data) else { return [] }
        return q
    }
    private static func save(_ q: [WakePendingAlarmRequest]) {
        if q.isEmpty { UserDefaults.standard.removeObject(forKey: requestKey) }
        else if let data = try? JSONEncoder().encode(q) { UserDefaults.standard.set(data, forKey: requestKey) }
    }

    static func enqueueTimer(_ timerID: String) {
        guard !timerID.isEmpty else { UserDefaults.standard.set(true, forKey: pendingTimerKey); return }
        let d = UserDefaults.standard
        var q = d.stringArray(forKey: pendingTimerQueueKey) ?? []
        if !q.contains(timerID) { q.append(timerID) }
        d.set(q, forKey: pendingTimerQueueKey)
    }
    static func dequeueTimer() -> String? {
        let d = UserDefaults.standard
        var q = d.stringArray(forKey: pendingTimerQueueKey) ?? []
        guard !q.isEmpty else { return nil }
        let x = q.removeFirst(); d.set(q, forKey: pendingTimerQueueKey); return x
    }
    static func markWakeCompleted() {
        let d = UserDefaults.standard
        var dates: [Date] = []
        if let data = d.data(forKey: streakKey), let x = try? JSONDecoder().decode([Date].self, from: data) { dates = x }
        let c = Calendar.current, today = c.startOfDay(for: Date())
        if !dates.contains(where: { c.isDate($0, inSameDayAs: today) }) {
            dates.append(today)
            if let data = try? JSONEncoder().encode(dates) { d.set(data, forKey: streakKey) }
        }
    }
}

public struct OpenAlarmIntent: LiveActivityIntent {
    public static let title: LocalizedStringResource = "IGNIDO Wakeを開く"
    public static let description = IntentDescription("アラームの解除画面を開きます")
    public static let openAppWhenRun = true

    @Parameter(title: "alarmID") public var alarmID: String
    @Parameter(title: "hour") public var hour: Int
    @Parameter(title: "minute") public var minute: Int
    @Parameter(title: "weekdaysMask") public var weekdaysMask: Int

    public init(alarmID: String, hour: Int, minute: Int, weekdaysMask: Int) {
        self.alarmID = alarmID; self.hour = hour; self.minute = minute; self.weekdaysMask = weekdaysMask
    }
    public init() { alarmID = ""; hour = 0; minute = 0; weekdaysMask = 0 }

    public func perform() async throws -> some IntentResult {
        guard WakeIntentBridge.wallClockIsNear(hour: hour, minute: minute, weekdaysMask: weekdaysMask) else { return .result() }
        WakeIntentBridge.enqueueAlarm(alarmID)
        return .result()
    }
}

public struct StopWakeIntent: LiveActivityIntent {
    public static let title: LocalizedStringResource = "アラーム停止"
    public static let description = IntentDescription("アラーム停止を起床記録へ反映します")
    @Parameter(title: "alarmID") public var alarmID: String
    public init(alarmID: String) { self.alarmID = alarmID }
    public init() { alarmID = "" }
    public func perform() async throws -> some IntentResult { WakeIntentBridge.markWakeCompleted(); return .result() }
}

public struct OpenTimerIntent: LiveActivityIntent {
    public static let title: LocalizedStringResource = "IGNIDO Wakeを開く"
    public static let description = IntentDescription("タイマー終了画面を開きます")
    public static let openAppWhenRun = true
    @Parameter(title: "timerID") public var timerID: String
    public init(timerID: String = "") { self.timerID = timerID }
    public init() { timerID = "" }
    public func perform() async throws -> some IntentResult { WakeIntentBridge.enqueueTimer(timerID); return .result() }
}

enum WakeIntentState {
    static func consumeAlarmRequest() -> WakePendingAlarmRequest? { WakeIntentBridge.dequeueAlarm() }
    static func clearExpiredAlarmRequests() { WakeIntentBridge.clearExpiredAlarmRequests() }
    static func consumeTimerID() -> UUID? { guard let raw = WakeIntentBridge.dequeueTimer() else { return nil }; return UUID(uuidString: raw) }
    static func consumeTimer() -> Bool {
        let d = UserDefaults.standard, pending = d.bool(forKey: WakeIntentBridge.pendingTimerKey)
        if pending { d.removeObject(forKey: WakeIntentBridge.pendingTimerKey) }
        return pending
    }
}
''', encoding='utf-8')

# AlarmKit owns the lifecycle. Use Apple's clock-style relative schedule for both
# one-shot (.never) and weekly alarms, avoid double cancellation, and verify the
# daemon accepted the registration.
p=root/'AlarmStore.swift'; s=p.read_text(encoding='utf-8')
s=s.replace('    private let testAlarmID = UUID(uuidString: "42DE9142-42A1-4E31-9A12-190200000021")!\n', '    private let testAlarmID = UUID(uuidString: "42DE9142-42A1-4E31-9A12-190200000021")!\n    private let v193MigrationKey = "ignido.alarm.coreMigration.193"\n')

m=re.search(r'    func bootstrap\(\) async \{.*?\n    \}\n\n    func requestAuthorization',s,re.S); assert m
s=s[:m.start()]+'''    func bootstrap() async {
        authorizationState = AlarmManager.shared.authorizationState
        WakeIntentState.clearExpiredAlarmRequests()
        Task { [weak self] in
            guard let self else { return }
            for await state in AlarmManager.shared.authorizationUpdates {
                await MainActor.run { self.authorizationState = state }
            }
        }
        Task { [weak self] in
            guard let self else { return }
            for await incoming in AlarmManager.shared.alarmUpdates {
                await MainActor.run { self.applySystemAlarms(incoming) }
            }
        }
        await performV193MigrationIfNeeded()
        refreshSystemState()
    }

    func requestAuthorization'''+s[m.end():]

old='''        save()
        await cancelScheduling(for: item.id)
        if item.enabled { await schedule(item) }
'''
assert old in s
s=s.replace(old,'''        save()
        if item.enabled { await schedule(item) } else { await cancelScheduling(for: item.id) }
''',1)

m=re.search(r'    func schedule\(_ item: WakeAlarm\) async \{.*?\n    \}\n\n    private func register\(_ item: WakeAlarm, schedule: Alarm.Schedule, defaultSound: Bool\) async throws -> Alarm \{.*?\n    \}\n',s,re.S); assert m
schedule=r'''    func schedule(_ item: WakeAlarm) async {
        guard item.enabled else { return }
        if authorizationState != .authorized { guard await requestAuthorization() else { return } }

        let time = Alarm.Schedule.Relative.Time(hour: item.hour, minute: item.minute)
        let recurrence: Alarm.Schedule.Relative.Recurrence = item.weekdays.isEmpty ? .never : .weekly(localeWeekdays(item.weekdays))
        let alarmSchedule: Alarm.Schedule = .relative(.init(time: time, repeats: recurrence))

        do {
            try? AlarmManager.shared.cancel(id: item.id)
            let scheduled = try await register(item, schedule: alarmSchedule, defaultSound: false)
            try await verifySystemRegistration(id: item.id, initial: scheduled)
            await schedulePreAlert(for: item)
            lastError = nil
        } catch {
            if IgnidoAlarmSoundCatalog.normalizedSelection(item.soundName) != IgnidoAlarmSoundCatalog.systemDefaultID {
                do {
                    try? AlarmManager.shared.cancel(id: item.id)
                    let fallback = try await register(item, schedule: alarmSchedule, defaultSound: true)
                    try await verifySystemRegistration(id: item.id, initial: fallback)
                    await schedulePreAlert(for: item)
                    lastError = AppText.localized("選択した音を登録できなかったため、標準音でアラームを確保しました")
                    return
                } catch { }
            }
            lastError = error.localizedDescription
        }
    }

    private func register(_ item: WakeAlarm, schedule: Alarm.Schedule, defaultSound: Bool) async throws -> Alarm {
        let needsOpen = item.mission != .none || isVideoMedia(item.mediaFileName)
        let button = needsOpen ? AlarmButton(text: LocalizedStringResource(stringLiteral: AppText.localized("解除")), textColor: .white, systemImageName: "arrow.right.circle.fill") : nil
        let alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: "\(item.timeText)  \(item.displayLabel)"), secondaryButton: button, secondaryButtonBehavior: needsOpen ? .custom : nil)
        let attrs = AlarmAttributes<EmptyWakeMetadata>(presentation: AlarmPresentation(alert: alert), metadata: EmptyWakeMetadata(), tintColor: IgnidoTheme.ember)
        let mask = weekdaysMask(item.weekdays)
        let intent: (any LiveActivityIntent)? = needsOpen ? OpenAlarmIntent(alarmID: item.id.uuidString, hour: item.hour, minute: item.minute, weekdaysMask: mask) : nil
        let config = AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.alarm(schedule: schedule, attributes: attrs, stopIntent: StopWakeIntent(alarmID: item.id.uuidString), secondaryIntent: intent, sound: defaultSound ? .default : IgnidoAlarmSoundCatalog.alertSound(for: item.soundName))
        return try await AlarmManager.shared.schedule(id: item.id, configuration: config)
    }

    private func verifySystemRegistration(id: UUID, initial: Alarm) async throws {
        systemAlarmStates[id] = initial.state
        if initial.state == .scheduled { return }
        try? await Task.sleep(for: .milliseconds(220))
        refreshSystemState()
        guard systemAlarmStates[id] == .scheduled else {
            throw NSError(domain: "IGNIDOAlarm", code: 193, userInfo: [NSLocalizedDescriptionKey: AppText.localized("システムにアラームを登録できませんでした")])
        }
    }

    private func isVideoMedia(_ fileName: String?) -> Bool {
        guard let fileName, let url = MediaLibrary.url(for: fileName) else { return false }
        return ["mp4", "mov", "m4v", "avi", "webm"].contains(url.pathExtension.lowercased())
    }

    private func weekdaysMask(_ days: Set<Int>) -> Int {
        days.reduce(0) { partial, day in
            guard (1...7).contains(day) else { return partial }
            return partial | (1 << (day - 1))
        }
    }
'''
s=s[:m.start()]+schedule+s[m.end():]

m=re.search(r'    func rescheduleAll\(\) async \{.*?\n    private func applySystemAlarms\(_ incoming:\[Alarm\]\)\{.*?\}\n',s,re.S); assert m
state=r'''    func rescheduleAll() async { refreshSystemState() }
    func reconcileWithSystem() async { refreshSystemState() }

    func refreshSystemState() {
        if let x = try? AlarmManager.shared.alarms { applySystemAlarms(x) }
    }
    func isSystemAlarmAlerting(id: UUID) -> Bool {
        refreshSystemState()
        return systemAlarmStates[id] == .alerting
    }

    func isWithinExpectedAlertWindow(_ item: WakeAlarm, now: Date = Date(), maxLate: TimeInterval = 1800) -> Bool {
        let c = Calendar.current
        for dayOffset in [0, -1] {
            guard let day = c.date(byAdding: .day, value: dayOffset, to: now),
                  let fire = c.date(bySettingHour: item.hour, minute: item.minute, second: 0, of: day) else { continue }
            let delta = now.timeIntervalSince(fire)
            guard delta >= -60 && delta <= maxLate else { continue }
            if item.weekdays.isEmpty { return true }
            let sw = c.component(.weekday, from: fire)
            let userDay = sw == 1 ? 7 : sw - 1
            if item.weekdays.contains(userDay) { return true }
        }
        return false
    }

    func dismissStaleAlertingAlarm(_ item: WakeAlarm) async {
        refreshSystemState()
        guard systemAlarmStates[item.id] == .alerting, !isWithinExpectedAlertWindow(item) else { return }
        try? AlarmManager.shared.stop(id: item.id)
        systemAlarmStates[item.id] = nil
        if item.weekdays.isEmpty {
            if let index = alarms.firstIndex(where: { $0.id == item.id }) { alarms[index].enabled = false; save() }
        } else {
            await schedule(item)
        }
    }

    func clearStaleAlertingAlarms() async {
        refreshSystemState()
        for item in alarms where systemAlarmStates[item.id] == .alerting && !isWithinExpectedAlertWindow(item) {
            await dismissStaleAlertingAlarm(item)
        }
    }

    func completeSystemAlarm(_ item: WakeAlarm) {
        try? AlarmManager.shared.stop(id: item.id)
        systemAlarmStates[item.id] = nil
        if item.weekdays.isEmpty, let index = alarms.firstIndex(where: { $0.id == item.id }) {
            alarms[index].enabled = false
            save()
        }
    }

    private func performV193MigrationIfNeeded() async {
        let defaults = UserDefaults.standard
        guard defaults.bool(forKey: v193MigrationKey) == false else { return }
        for item in alarms { try? AlarmManager.shared.cancel(id: item.id) }
        WakeIntentState.clearExpiredAlarmRequests()
        for index in alarms.indices {
            if !isVideoMedia(alarms[index].mediaFileName), alarms[index].mediaFileName != nil, IgnidoAlarmSoundCatalog.isCustom(alarms[index].soundName) {
                alarms[index].mediaFileName = nil
            }
        }
        save()
        for item in alarms where item.enabled { await schedule(item) }
        defaults.set(true, forKey: v193MigrationKey)
    }

    private func applySystemAlarms(_ incoming: [Alarm]) {
        systemAlarmStates = Dictionary(uniqueKeysWithValues: incoming.map { ($0.id, $0.state) })
    }
'''
s=s[:m.start()]+state+s[m.end():]
p.write_text(s,encoding='utf-8')

# Never open a mission on a merely-near wall clock. It must be a currently
# alerting AlarmKit alarm and still be within the expected occurrence window.
p=root/'RootView.swift'; s=p.read_text(encoding='utf-8')
s=s.replace('                Task { await consumeSystemActions() }','                Task { await alarmStore.clearStaleAlertingAlarms(); await consumeSystemActions() }',1)
m=re.search(r'    @MainActor\n    private func consumeSystemActions\(\) async \{.*?\n    \}\n(?=\})',s,re.S); assert m
consume='''    @MainActor
    private func consumeSystemActions() async {
        if activeAlarm == nil,
           let request = WakeIntentState.consumeAlarmRequest(),
           Date().timeIntervalSince(request.createdAt) <= 120,
           let id = UUID(uuidString: request.alarmID),
           let alarm = alarmStore.alarm(id: id) {
            var alerting = alarmStore.isSystemAlarmAlerting(id: id)
            if !alerting {
                for _ in 0..<4 where !alerting {
                    try? await Task.sleep(for: .milliseconds(150))
                    alerting = alarmStore.isSystemAlarmAlerting(id: id)
                }
            }
            if alerting && alarmStore.isWithinExpectedAlertWindow(alarm) {
                activeAlarm = alarm
                return
            }
            if alerting { await alarmStore.dismissStaleAlertingAlarm(alarm) }
        }
        if activeTimer == nil, let timerID = WakeIntentState.consumeTimerID(), let item = multiTimerStore.item(id: timerID) {
            multiTimerStore.finish(timerID); activeTimer = item; return
        }
        if !showTimerResult, WakeIntentState.consumeTimer() { showTimerResult = true }
    }
'''
s=s[:m.start()]+consume+s[m.end():]
p.write_text(s,encoding='utf-8')

# Imported audio is the AlarmKit sound, not a second app-owned AVPlayer. This is
# what makes a selected MP3 independent of the app process after conversion.
p=root/'AlarmViews.swift'; s=p.read_text(encoding='utf-8')
old='''                guard let url = try result.get().first else { return }
                draft.mediaFileName = try MediaLibrary.importFile(from: url)
                if let type = UTType(filenameExtension: url.pathExtension), type.conforms(to: .audio) {
                    let alarmFile = try IgnidoAlarmSoundLibrary.importAsAlarmSound(from: url)
                    draft.soundName = IgnidoAlarmSoundCatalog.customPrefix + alarmFile
                }
                importError = nil
'''
assert old in s
s=s.replace(old,'''                guard let url = try result.get().first else { return }
                if let type = UTType(filenameExtension: url.pathExtension), type.conforms(to: .audio) {
                    let alarmFile = try IgnidoAlarmSoundLibrary.importAsAlarmSound(from: url)
                    draft.soundName = IgnidoAlarmSoundCatalog.customPrefix + alarmFile
                    draft.mediaFileName = nil
                } else {
                    draft.mediaFileName = try MediaLibrary.importFile(from: url)
                }
                importError = nil
''',1)
p.write_text(s,encoding='utf-8')

# Generic missions must not take over AVAudioSession; AlarmKit remains the owner
# of the audible alert. Video views still configure their own AVPlayer session.
p=root/'MissionView.swift'; s=p.read_text(encoding='utf-8')
s=s.replace('            AlarmRuntime.preparePlaybackSession()\n','',1)
a=s.index('@MainActor\nfinal class StepMissionCounter: ObservableObject {'); b=s.index('\nstruct MathMissionView: View {',a)
step=r'''@MainActor
final class StepMissionCounter: ObservableObject {
    @Published var steps = 0
    @Published var errorText: String?
    @Published var permissionDenied = false
    @Published var waitingForPermission = false
    private let livePedometer = CMPedometer()
    private let historyPedometer = CMPedometer()
    private var timer: Timer?
    private var startDate = Date()
    private var target = 1
    private var alarmID = UUID()
    private var done = false
    private var completion: (() -> Void)?

    func start(alarmID: UUID, target: Int, onComplete: @escaping () -> Void) {
        suspend(); self.alarmID = alarmID; self.target = max(1, target); completion = onComplete
        done = false; steps = 0; errorText = nil; permissionDenied = false
        waitingForPermission = CMPedometer.authorizationStatus() == .notDetermined
        guard CMPedometer.isStepCountingAvailable() else { errorText = AppText.localized("この端末では歩数計を利用できません"); return }
        let status = CMPedometer.authorizationStatus()
        if status == .denied || status == .restricted {
            permissionDenied = true; waitingForPermission = false
            errorText = AppText.localized("モーションとフィットネスの権限を許可してください"); return
        }
        let k = key(alarmID)
        if let d = UserDefaults.standard.object(forKey: k) as? Date, Date().timeIntervalSince(d) >= 0, Date().timeIntervalSince(d) <= 7200 {
            startDate = d
        } else {
            startDate = Date(); UserDefaults.standard.set(startDate, forKey: k)
        }
        queryHistory()
        livePedometer.startUpdates(from: startDate) { [weak self] data, error in
            Task { @MainActor in guard let self else { return }; self.handle(data: data, error: error) }
        }
        timer = Timer.scheduledTimer(withTimeInterval: 0.8, repeats: true) { [weak self] _ in Task { @MainActor in self?.queryHistory() } }
    }

    func suspend() { livePedometer.stopUpdates(); timer?.invalidate(); timer = nil }

    private func queryHistory() {
        let from = startDate
        historyPedometer.queryPedometerData(from: from, to: Date()) { [weak self] data, error in
            Task { @MainActor in guard let self else { return }; self.handle(data: data, error: error) }
        }
    }
    private func handle(data: CMPedometerData?, error: Error?) {
        let status = CMPedometer.authorizationStatus()
        waitingForPermission = status == .notDetermined
        permissionDenied = status == .denied || status == .restricted
        if let error { errorText = error.localizedDescription; return }
        errorText = nil; accept(data?.numberOfSteps.intValue ?? 0)
    }
    private func accept(_ value: Int) {
        guard !done else { return }; steps = max(steps, value)
        if steps >= target { done = true; UserDefaults.standard.removeObject(forKey: key(alarmID)); suspend(); completion?() }
    }
    private func key(_ id: UUID) -> String { "ignido.stepMission.start.\(id.uuidString)" }
}

struct StepMissionView: View {
    let alarmID: UUID; let target: Int; let onComplete: () -> Void
    @StateObject private var counter = StepMissionCounter()
    var body: some View {
        VStack(spacing: 18) {
            ProgressLabel(value: counter.steps, target: target, title: "スマホを持って歩く")
            Text("iPhoneのCMPedometerが記録した実歩数だけを使います。アプリを終了しても、戻ったときに履歴から復元します。")
                .font(.caption).foregroundStyle(IgnidoTheme.secondaryText).multilineTextAlignment(.center)
            if counter.waitingForPermission { Text("モーションとフィットネスの許可を確認しています…").font(.caption).foregroundStyle(IgnidoTheme.secondaryText) }
            if let e = counter.errorText { Text(e).font(.caption).foregroundStyle(.red) }
            if counter.permissionDenied, let u = URL(string: UIApplication.openSettingsURLString) { Link("iPhoneの設定を開く", destination: u).buttonStyle(.borderedProminent) }
        }
        .onAppear { counter.start(alarmID: alarmID, target: target, onComplete: onComplete) }
        .onDisappear { counter.suspend() }
    }
}
'''
s=s[:a]+step+s[b:]
p.write_text(s,encoding='utf-8')

checks={
 root/'AlarmStore.swift':['.relative(.init(time: time, repeats: recurrence))','performV193MigrationIfNeeded','isWithinExpectedAlertWindow','OpenAlarmIntent(alarmID: item.id.uuidString, hour: item.hour, minute: item.minute, weekdaysMask: mask)','verifySystemRegistration'],
 root/'AlarmIntents.swift':['pendingAlarmRequests.v3','wallClockIsNear','weekdaysMask'],
 root/'RootView.swift':['alerting && alarmStore.isWithinExpectedAlertWindow(alarm)','dismissStaleAlertingAlarm'],
 root/'MissionView.swift':['private let livePedometer = CMPedometer()','private let historyPedometer = CMPedometer()','queryPedometerData','startUpdates(from: startDate)'],
 root/'AlarmViews.swift':['draft.soundName = IgnidoAlarmSoundCatalog.customPrefix + alarmFile','draft.mediaFileName = nil']}
for path,tokens in checks.items():
    text=path.read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f'missing {token} in {path}')
if '.fixed(fire)' in (root/'AlarmStore.swift').read_text(): raise SystemExit('old fixed one-shot schedule remains')
if 'valid || alarmStore.isAlarmDueNow' in (root/'RootView.swift').read_text(): raise SystemExit('old permissive alarm-open condition remains')
mission_head=(root/'MissionView.swift').read_text().split('@MainActor\nfinal class StepMissionCounter',1)[0]
if 'AlarmRuntime.preparePlaybackSession()' in mission_head: raise SystemExit('generic mission still takes AVAudioSession')
print('IGNIDO Wake iOS 1.9.3 alarm-core rebuild applied')