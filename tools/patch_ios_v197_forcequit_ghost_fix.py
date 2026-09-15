from pathlib import Path
r=Path('ios/IGNIDOWake')

def repl(path,a,b):
 p=r/path; s=p.read_text(); assert a in s,(path,a[:80]); p.write_text(s.replace(a,b,1))

def replace_between(path,start,end,new):
 p=r/path; s=p.read_text(); i=s.index(start); j=s.index(end,i); p.write_text(s[:i]+new+s[j:])

repl('Info.plist','<string>1.9.6</string>','<string>1.9.7</string>')
repl('Info.plist','<string>25</string>','<string>26</string>')
repl('AlarmStore.swift','        WakeIntentState.clearExpiredAlarmRequests()\n','        WakeIntentState.clearExpiredAlarmRequests()\n        purgeLegacyEscapeGuards()\n')

replace_between('AlarmStore.swift','    func beginForegroundAlarmSession(','    func isSystemAlarmAlerting',r'''    func beginForegroundAlarmSession(_ item: WakeAlarm, sourceSystemID: UUID, sourceAlreadyStopped: Bool) async {
        purgeLegacyEscapeGuards()
        if sourceSystemID != item.id {
            try? AlarmManager.shared.cancel(id: sourceSystemID)
            systemAlarmStates[sourceSystemID] = nil
        }
        refreshSystemState()
    }

    func disarmEscapeGuard(for item: WakeAlarm) {
        removeEscapeGuardRecord(for:item.id,cancelSystemAlarm:true)
        refreshSystemState()
    }
    func cancelSystemAlarm(id: UUID) { try? AlarmManager.shared.cancel(id:id); systemAlarmStates[id]=nil }
    private func escapeGuardID(for id: UUID) -> UUID { var u=id.uuid; withUnsafeMutableBytes(of:&u){ $0[0]^=0xA7; $0[15]^=0x5C }; return UUID(uuid:u) }
    private func escapeGuardRecords() -> [IgnidoEscapeGuardRecord] { guard let d=UserDefaults.standard.data(forKey:escapeGuardKey), let x=try? JSONDecoder().decode([IgnidoEscapeGuardRecord].self,from:d) else { return [] }; return x }
    private func saveEscapeGuardRecords(_ x:[IgnidoEscapeGuardRecord]) { if x.isEmpty { UserDefaults.standard.removeObject(forKey:escapeGuardKey) } else if let d=try? JSONEncoder().encode(x) { UserDefaults.standard.set(d,forKey:escapeGuardKey) } }
    private func removeEscapeGuardRecord(for alarmID:UUID,cancelSystemAlarm:Bool) { let x=escapeGuardRecords(), targets=x.filter{$0.alarmID==alarmID}; if cancelSystemAlarm { for v in targets { try? AlarmManager.shared.cancel(id:v.guardID) }; try? AlarmManager.shared.cancel(id:escapeGuardID(for:alarmID)) }; saveEscapeGuardRecords(x.filter{$0.alarmID != alarmID}) }
    private func purgeLegacyEscapeGuards() { for v in escapeGuardRecords() { try? AlarmManager.shared.cancel(id:v.guardID) }; for a in alarms { try? AlarmManager.shared.cancel(id:escapeGuardID(for:a.id)) }; saveEscapeGuardRecords([]) }

''')

repl('AlarmStore.swift',r'''    func completeSystemAlarm(_ item: WakeAlarm) async {
        disarmEscapeGuard(for:item); refreshSystemState()
        if item.weekdays.isEmpty {
            try? AlarmManager.shared.cancel(id:item.id); systemAlarmStates[item.id]=nil
            if let index=alarms.firstIndex(where:{$0.id==item.id}) { alarms[index].enabled=false; save() }
        } else if systemAlarmStates[item.id] != .scheduled { await schedule(item,replacingExisting:true) }
    }
''',r'''    func completeSystemAlarm(_ item: WakeAlarm) async {
        disarmEscapeGuard(for:item)
        try? AlarmManager.shared.stop(id:item.id)
        refreshSystemState()
        if item.weekdays.isEmpty {
            try? AlarmManager.shared.cancel(id:item.id); systemAlarmStates[item.id]=nil
            if let index=alarms.firstIndex(where:{$0.id==item.id}) { alarms[index].enabled=false; save() }
        } else if systemAlarmStates[item.id] != .scheduled { await schedule(item,replacingExisting:true) }
    }
''')
repl('AlarmStore.swift',r'''            var allowed = allAlarmIDs.union(knownTimerIDs)
            allowed.formUnion(liveEscapeGuardIDs())
''',r'''            purgeLegacyEscapeGuards()
            var allowed = enabledAlarmIDs.union(knownTimerIDs)
''')
repl('AlarmStore.swift','''    private func cancelScheduling(for id: UUID) async {
        try? AlarmManager.shared.cancel(id: id)
''','''    private func cancelScheduling(for id: UUID) async {
        removeEscapeGuardRecord(for:id,cancelSystemAlarm:true)
        try? AlarmManager.shared.cancel(id: id)
''')

repl('AlarmStore.swift','''    func isSystemAlarmAlerting(id: UUID) -> Bool {
        refreshSystemState()
        return systemAlarmStates[id] == .alerting
    }
''','''    func isSystemAlarmAlerting(id: UUID) -> Bool {
        refreshSystemState()
        return systemAlarmStates[id] == .alerting
    }

    func firstExpectedAlertingAlarm() -> WakeAlarm? {
        alarms.first { systemAlarmStates[$0.id] == .alerting && isWithinExpectedAlertWindow($0) }
    }
''')

repl('RootView.swift',r'''        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                streakStore.reload()
                if let alarm = activeAlarm { alarmStore.disarmEscapeGuard(for: alarm); startForegroundAlarmAudio(alarm) }
                else { Task { await reconcileDaemonOwnership(); await alarmStore.clearStaleAlertingAlarms(); await consumeSystemActions() } }
            } else if phase == .background, let alarm = activeAlarm {
                IgnidoAlarmForegroundSoundGuardian.shared.stop()
                Task { await alarmStore.armEscapeGuard(for: alarm, after: 3) }
            }
        }
''',r'''        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                streakStore.reload()
                if let alarm = activeAlarm { startForegroundAlarmAudio(alarm) }
                else { Task { await reconcileDaemonOwnership(); await alarmStore.clearStaleAlertingAlarms(); await consumeSystemActions() } }
            }
        }
''')
repl('RootView.swift','''        .fullScreenCover(item: $activeAlarm, onDismiss: { Task { await consumeSystemActions() } }) { alarm in
''','''        .onReceive(alarmStore.$systemAlarmStates) { _ in
            guard scenePhase == .active, activeAlarm == nil else { return }
            if let alarm = alarmStore.firstExpectedAlertingAlarm() {
                startForegroundAlarmAudio(alarm)
                activeAlarm = alarm
                Task { await alarmStore.beginForegroundAlarmSession(alarm, sourceSystemID: alarm.id, sourceAlreadyStopped: false) }
            } else {
                Task { await alarmStore.clearStaleAlertingAlarms() }
            }
        }
        .fullScreenCover(item: $activeAlarm, onDismiss: { Task { await consumeSystemActions() } }) { alarm in
''')

repl('FeatureStores.swift','    var alarmKitIdentifier: UUID { systemAlarmID }','    var alarmKitIdentifiers: Set<UUID> { running ? [systemAlarmID] : [] }')
repl('FeatureStores.swift','''    func markFinished() {
        if finished {
            running = false
''','''    func markFinished() {
        if finished {
            try? AlarmManager.shared.cancel(id: systemAlarmID)
            running = false
''')
repl('MultiTimer.swift','    var alarmKitIdentifiers: Set<UUID> { Set(items.map(\\.id)) }','    var alarmKitIdentifiers: Set<UUID> { Set(items.filter(\\.running).map(\\.id)) }')
repl('MultiTimer.swift','''    func finish(_ id: UUID) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
''','''    func finish(_ id: UUID) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        try? AlarmManager.shared.cancel(id:id)
''')
for f in ['RootView.swift','IGNIDOWakeApp.swift']:
 p=r/f; s=p.read_text(); s=s.replace('ids.insert(timerStore.alarmKitIdentifier)','ids.formUnion(timerStore.alarmKitIdentifiers)'); p.write_text(s)

A=(r/'AlarmStore.swift').read_text(); R=(r/'RootView.swift').read_text()
assert 'func armEscapeGuard(for item:' not in A
assert 'liveEscapeGuardIDs' not in A
assert 'var allowed = enabledAlarmIDs.union(knownTimerIDs)' in A
assert 'armEscapeGuard(for: alarm' not in R
assert 'firstExpectedAlertingAlarm' in A and '.onReceive(alarmStore.$systemAlarmStates)' in R
assert '<string>1.9.7</string>' in (r/'Info.plist').read_text()
print('v197 applied')
