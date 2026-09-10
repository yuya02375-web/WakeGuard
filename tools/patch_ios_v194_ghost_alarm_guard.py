from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# Version 1.9.4 / build 23.
p = root / 'Info.plist'
s = p.read_text(encoding='utf-8')
s = re.sub(r'(<key>CFBundleShortVersionString</key><string>)[^<]+', r'\g<1>1.9.4', s, count=1)
s = re.sub(r'(<key>CFBundleVersion</key><string>)[^<]+', r'\g<1>23', s, count=1)
p.write_text(s, encoding='utf-8')

p = Path('ios/project.yml')
s = p.read_text(encoding='utf-8')
s = re.sub(r'CFBundleShortVersionString: "[^"]+"', 'CFBundleShortVersionString: "1.9.4"', s)
s = re.sub(r'CFBundleVersion: "[^"]+"', 'CFBundleVersion: "23"', s)
p.write_text(s, encoding='utf-8')

# AlarmKit reports from production apps include rare midnight misfires. Apple forum
# reports point to excessive cancel/re-schedule churn as one contributor. v1.9.4
# keeps stable IDs, diffs updates, removes .fixed() even from the 5-second test,
# and cleans only daemon alarms that no longer belong to any current IGNIDO model.
p = root / 'AlarmStore.swift'
s = p.read_text(encoding='utf-8')

s = s.replace(
    '    private let v193MigrationKey = "ignido.alarm.coreMigration.193"\n',
    '    private let v193MigrationKey = "ignido.alarm.coreMigration.193"\n'
    '    private let testExpiryKey = "ignido.alarm.testExpiresAt.v194"\n'
)

s = s.replace('        await schedule(item)\n', '        await schedule(item, replacingExisting: false)\n', 1)

old_update = '''    func update(_ item: WakeAlarm) async {
        guard let index = alarms.firstIndex(where: { $0.id == item.id }) else { return }
        alarms[index] = item
        save()
        if item.enabled { await schedule(item) } else { await cancelScheduling(for: item.id) }
    }
'''
new_update = '''    func update(_ item: WakeAlarm) async {
        guard let index = alarms.firstIndex(where: { $0.id == item.id }) else { return }
        let previous = alarms[index]
        alarms[index] = item
        save()
        if !item.enabled {
            await cancelScheduling(for: item.id)
        } else if !previous.enabled {
            await schedule(item, replacingExisting: false)
        } else if requiresAlarmKitReplacement(previous, item) {
            await schedule(item, replacingExisting: true)
        } else {
            // Presentation/schedule is unchanged. Only refresh the passive pre-alert;
            // do not churn the AlarmKit daemon for volume, vibration, mission target, etc.
            await schedulePreAlert(for: item)
        }
    }

    private func requiresAlarmKitReplacement(_ old: WakeAlarm, _ new: WakeAlarm) -> Bool {
        old.hour != new.hour ||
        old.minute != new.minute ||
        old.weekdays != new.weekdays ||
        old.displayLabel != new.displayLabel ||
        old.mission != new.mission ||
        old.soundName != new.soundName ||
        old.mediaFileName != new.mediaFileName
    }
'''
if old_update not in s:
    raise SystemExit('v194 update block not found')
s = s.replace(old_update, new_update, 1)

s = s.replace('            await schedule(updated)\n', '            await schedule(updated, replacingExisting: false)\n', 1)

s = s.replace('    func schedule(_ item: WakeAlarm) async {\n', '    func schedule(_ item: WakeAlarm, replacingExisting: Bool = true) async {\n', 1)
s = s.replace('''        do {
            try? AlarmManager.shared.cancel(id: item.id)
            let scheduled = try await register(item, schedule: alarmSchedule, defaultSound: false)
''', '''        do {
            if replacingExisting { try? AlarmManager.shared.cancel(id: item.id) }
            let scheduled = try await register(item, schedule: alarmSchedule, defaultSound: false)
''', 1)

# Replace the fixed-date 5-second test with AlarmKit's timer configuration so there
# are no .fixed(Date) alarm registrations left in IGNIDO's alarm path.
m = re.search(r'    func scheduleTest\(_ item: WakeAlarm, after seconds: TimeInterval = 5\) async \{.*?\n    \}\n\n    func rescheduleAll', s, re.S)
if not m:
    raise SystemExit('v194 scheduleTest block not found')
test = r'''    func scheduleTest(_ item: WakeAlarm, after seconds: TimeInterval = 5) async {
        if authorizationState != .authorized { guard await requestAuthorization() else { return } }
        let duration = max(2, seconds)
        let alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: "TEST  \(item.displayLabel)"))
        let countdown = AlarmPresentation.Countdown(title: LocalizedStringResource(stringLiteral: "TEST"))
        let attrs = AlarmAttributes<EmptyWakeMetadata>(presentation: AlarmPresentation(alert: alert, countdown: countdown, paused: nil), metadata: EmptyWakeMetadata(), tintColor: IgnidoTheme.ember)
        UserDefaults.standard.set(Date().addingTimeInterval(duration + 120), forKey: testExpiryKey)
        do {
            try? AlarmManager.shared.cancel(id: testAlarmID)
            let cfg = AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer(duration: duration, attributes: attrs, stopIntent: nil, secondaryIntent: nil, sound: IgnidoAlarmSoundCatalog.alertSound(for: item.soundName))
            let a = try await AlarmManager.shared.schedule(id: testAlarmID, configuration: cfg)
            guard a.state == .countdown || a.state == .scheduled else { throw NSError(domain: "IGNIDOAlarm", code: 194, userInfo: [NSLocalizedDescriptionKey: "テストを登録できませんでした"]) }
            lastError = nil
        } catch {
            do {
                try? AlarmManager.shared.cancel(id: testAlarmID)
                let cfg = AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer(duration: duration, attributes: attrs, stopIntent: nil, secondaryIntent: nil, sound: .default)
                _ = try await AlarmManager.shared.schedule(id: testAlarmID, configuration: cfg)
                lastError = AppText.localized("カスタム音に失敗したためテストは標準音にしました")
            } catch { lastError = error.localizedDescription }
        }
    }

    func rescheduleAll'''
s = s[:m.start()] + test + s[m.end():]

# v1.9.3 migration already canceled every persisted alarm. Do not cancel each one
# a second time from schedule(). One deliberate replacement is enough.
s = s.replace(
    '        for item in alarms where item.enabled { await schedule(item) }\n',
    '        for item in alarms where item.enabled { await schedule(item, replacingExisting: false) }\n',
    1
)

# Diff current daemon contents against all model IDs. AlarmManager.shared.alarms
# is scoped to this client, so unknown IDs are legacy/orphan IGNIDO registrations,
# including old random 5-second-test IDs. Disabled alarm IDs are also purged.
insert_at = s.index('    private func applySystemAlarms(_ incoming: [Alarm]) {')
reconcile = r'''    func reconcileDaemonOwnership(knownTimerIDs: Set<UUID>) async {
        guard authorizationState == .authorized else { return }
        do {
            let daemon = try AlarmManager.shared.alarms
            let allAlarmIDs = Set(alarms.map(\.id))
            let enabledAlarmIDs = Set(alarms.filter(\.enabled).map(\.id))
            var allowed = allAlarmIDs.union(knownTimerIDs)
            if let expiry = UserDefaults.standard.object(forKey: testExpiryKey) as? Date, expiry > Date() {
                allowed.insert(testAlarmID)
            } else {
                UserDefaults.standard.removeObject(forKey: testExpiryKey)
            }

            for alarm in daemon {
                let isDisabledPersistedAlarm = allAlarmIDs.contains(alarm.id) && !enabledAlarmIDs.contains(alarm.id)
                if isDisabledPersistedAlarm || !allowed.contains(alarm.id) {
                    try? AlarmManager.shared.cancel(id: alarm.id)
                }
            }
            refreshSystemState()
        } catch {
            lastError = error.localizedDescription
        }
    }

'''
s = s[:insert_at] + reconcile + s[insert_at:]

p.write_text(s, encoding='utf-8')

# Expose the IDs that belong to timer models so AlarmStore can safely diff its
# client-scoped AlarmKit daemon state without deleting legitimate timers.
p = root / 'FeatureStores.swift'
s = p.read_text(encoding='utf-8')
needle = '''    var finished: Bool { running && remaining <= 0.1 }
'''
replacement = '''    var finished: Bool { running && remaining <= 0.1 }
    var alarmKitIdentifier: UUID { systemAlarmID }
'''
if needle not in s:
    raise SystemExit('v194 TimerStore insertion point not found')
s = s.replace(needle, replacement, 1)
p.write_text(s, encoding='utf-8')

p = root / 'MultiTimer.swift'
s = p.read_text(encoding='utf-8')
needle = '''    func item(id: UUID) -> WakeTimerItem? { items.first { $0.id == id } }
'''
replacement = '''    func item(id: UUID) -> WakeTimerItem? { items.first { $0.id == id } }
    var alarmKitIdentifiers: Set<UUID> { Set(items.map(\\.id)) }
'''
if needle not in s:
    raise SystemExit('v194 MultiTimer insertion point not found')
s = s.replace(needle, replacement, 1)
p.write_text(s, encoding='utf-8')

# On launch and whenever the app becomes active, perform a no-churn daemon diff:
# cancel only unknown/disabled registrations. This catches old random test alarms
# that persisted across prior versions without recreating valid alarms.
p = root / 'RootView.swift'
s = p.read_text(encoding='utf-8')
old = '''            if phase == .active {
                streakStore.reload()
                Task { await alarmStore.clearStaleAlertingAlarms(); await consumeSystemActions() }
            }
'''
new = '''            if phase == .active {
                streakStore.reload()
                Task {
                    await reconcileDaemonOwnership()
                    await alarmStore.clearStaleAlertingAlarms()
                    await consumeSystemActions()
                }
            }
'''
if old not in s:
    raise SystemExit('v194 RootView scenePhase block not found')
s = s.replace(old, new, 1)

marker = '    @MainActor\n    private func consumeSystemActions() async {'
idx = s.index(marker)
helper = '''    @MainActor
    private func reconcileDaemonOwnership() async {
        var ids = multiTimerStore.alarmKitIdentifiers
        ids.insert(timerStore.alarmKitIdentifier)
        await alarmStore.reconcileDaemonOwnership(knownTimerIDs: ids)
    }

'''
s = s[:idx] + helper + s[idx:]
p.write_text(s, encoding='utf-8')

# Run the same diff once immediately after AlarmStore's migration/bootstrap.
p = root / 'IGNIDOWakeApp.swift'
s = p.read_text(encoding='utf-8')
old = '                .task { await alarmStore.bootstrap() }\n'
new = '''                .task {
                    await alarmStore.bootstrap()
                    var ids = multiTimerStore.alarmKitIdentifiers
                    ids.insert(timerStore.alarmKitIdentifier)
                    await alarmStore.reconcileDaemonOwnership(knownTimerIDs: ids)
                }
'''
if old not in s:
    raise SystemExit('v194 app bootstrap task not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# Static invariants.
checks = {
    root/'AlarmStore.swift': [
        'func schedule(_ item: WakeAlarm, replacingExisting: Bool = true)',
        'requiresAlarmKitReplacement',
        'AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer(duration: duration',
        'reconcileDaemonOwnership(knownTimerIDs: Set<UUID>)',
        'for item in alarms where item.enabled { await schedule(item, replacingExisting: false) }',
    ],
    root/'FeatureStores.swift': ['var alarmKitIdentifier: UUID { systemAlarmID }'],
    root/'MultiTimer.swift': ['var alarmKitIdentifiers: Set<UUID>'],
    root/'RootView.swift': ['await reconcileDaemonOwnership()', 'alarmStore.reconcileDaemonOwnership(knownTimerIDs: ids)'],
    root/'IGNIDOWakeApp.swift': ['await alarmStore.reconcileDaemonOwnership(knownTimerIDs: ids)'],
}
for path, tokens in checks.items():
    text = path.read_text(encoding='utf-8')
    for token in tokens:
        if token not in text:
            raise SystemExit(f'missing {token} in {path}')

alarm_store = (root/'AlarmStore.swift').read_text(encoding='utf-8')
if '.fixed(Date().addingTimeInterval' in alarm_store or '.fixed(fire)' in alarm_store:
    raise SystemExit('fixed-date alarm path remains in AlarmStore')
print('IGNIDO Wake iOS 1.9.4 ghost-alarm guard applied')
