from pathlib import Path
import re
root=Path('ios/IGNIDOWake'); p=root/'AlarmStore.swift'; s=p.read_text()
if 'private struct IgnidoEscapeGuardRecord' not in s:
    s=s.replace('import AppIntents\n\n','''import AppIntents

private struct IgnidoEscapeGuardRecord: Codable, Hashable {
    let alarmID: UUID
    let guardID: UUID
    let expiresAt: Date
}

''',1)
s=s.replace('    private let testExpiryKey = "ignido.alarm.testExpiresAt.v194"\n','    private let testExpiryKey = "ignido.alarm.testExpiresAt.v194"\n    private let escapeGuardKey = "ignido.alarm.escapeGuards.v196"\n    private var testGeneration = 0\n',1)
old='''        let needsOpen = item.mission != .none || isVideoMedia(item.mediaFileName)
        let button = needsOpen ? AlarmButton(text: LocalizedStringResource(stringLiteral: AppText.localized("解除")), textColor: .white, systemImageName: "arrow.right.circle.fill") : nil
        let alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: "\\(item.timeText)  \\(item.displayLabel)"), secondaryButton: button, secondaryButtonBehavior: needsOpen ? .custom : nil)
        let attrs = AlarmAttributes<EmptyWakeMetadata>(presentation: AlarmPresentation(alert: alert), metadata: EmptyWakeMetadata(), tintColor: IgnidoTheme.ember)
        let mask = weekdaysMask(item.weekdays)
        let intent: (any LiveActivityIntent)? = needsOpen ? OpenAlarmIntent(alarmID: item.id.uuidString, hour: item.hour, minute: item.minute, weekdaysMask: mask) : nil
        let config = AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.alarm(schedule: schedule, attributes: attrs, stopIntent: StopWakeIntent(alarmID: item.id.uuidString), secondaryIntent: intent, sound: defaultSound ? .default : IgnidoAlarmSoundCatalog.alertSound(for: item.soundName))
'''
new='''        let button = AlarmButton(text: LocalizedStringResource(stringLiteral: AppText.localized("解除")), textColor: .white, systemImageName: "arrow.right.circle.fill")
        let alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: "\\(item.timeText)  \\(item.displayLabel)"), secondaryButton: button, secondaryButtonBehavior: .custom)
        let attrs = AlarmAttributes<EmptyWakeMetadata>(presentation: AlarmPresentation(alert: alert), metadata: EmptyWakeMetadata(), tintColor: IgnidoTheme.ember)
        let mask = weekdaysMask(item.weekdays)
        let intent: (any LiveActivityIntent)? = OpenAlarmIntent(alarmID: item.id.uuidString, hour: item.hour, minute: item.minute, weekdaysMask: mask, systemAlarmID: item.id.uuidString)
        let stopIntent = StopWakeIntent(alarmID: item.id.uuidString, hour: item.hour, minute: item.minute, weekdaysMask: mask, systemAlarmID: item.id.uuidString)
        let config = AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.alarm(schedule: schedule, attributes: attrs, stopIntent: stopIntent, secondaryIntent: intent, sound: defaultSound ? .default : IgnidoAlarmSoundCatalog.alertSound(for: item.soundName))
'''
assert old in s; s=s.replace(old,new,1)

start=s.index('    func scheduleTest(_ item: WakeAlarm, after seconds: TimeInterval = 5) async {'); end=s.index('\n    func rescheduleAll',start)
s=s[:start]+r'''    func scheduleTest(_ item: WakeAlarm, after seconds: TimeInterval = 5) async {
        if authorizationState != .authorized { guard await requestAuthorization() else { return } }
        let duration=max(2,seconds); testGeneration += 1; let generation=testGeneration
        try? AlarmManager.shared.cancel(id:testAlarmID); UserDefaults.standard.removeObject(forKey:testExpiryKey)
        let alert=AlarmPresentation.Alert(title:LocalizedStringResource(stringLiteral:"TEST  \(item.displayLabel)"))
        let countdown=AlarmPresentation.Countdown(title:LocalizedStringResource(stringLiteral:"TEST"))
        let attrs=AlarmAttributes<EmptyWakeMetadata>(presentation:AlarmPresentation(alert:alert,countdown:countdown,paused:nil),metadata:EmptyWakeMetadata(),tintColor:IgnidoTheme.ember)
        UserDefaults.standard.set(Date().addingTimeInterval(duration+20),forKey:testExpiryKey)
        do {
            let cfg=AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer(duration:duration,attributes:attrs,stopIntent:nil,secondaryIntent:nil,sound:IgnidoAlarmSoundCatalog.alertSound(for:item.soundName))
            let a=try await AlarmManager.shared.schedule(id:testAlarmID,configuration:cfg)
            guard a.state == .countdown || a.state == .scheduled else { throw NSError(domain:"IGNIDOAlarm",code:196,userInfo:[NSLocalizedDescriptionKey:"テストを登録できませんでした"]) }
            lastError=nil
        } catch {
            do { try? AlarmManager.shared.cancel(id:testAlarmID); let cfg=AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer(duration:duration,attributes:attrs,stopIntent:nil,secondaryIntent:nil,sound:.default); _=try await AlarmManager.shared.schedule(id:testAlarmID,configuration:cfg); lastError=AppText.localized("カスタム音に失敗したためテストは標準音にしました") }
            catch { lastError=error.localizedDescription }
        }
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64((duration+20)*1_000_000_000))
            guard let self, self.testGeneration == generation else { return }
            self.cancelTestAlarm()
        }
    }

    func cancelTestAlarm() {
        testGeneration += 1
        try? AlarmManager.shared.cancel(id:testAlarmID)
        UserDefaults.standard.removeObject(forKey:testExpiryKey)
        refreshSystemState()
    }
'''+s[end:]

insert=s.index('    func isSystemAlarmAlerting(id: UUID) -> Bool {')
helpers=r'''    func beginForegroundAlarmSession(_ item: WakeAlarm, sourceSystemID: UUID, sourceAlreadyStopped: Bool) async {
        removeEscapeGuardRecord(for:item.id,cancelSystemAlarm:false)
        if !sourceAlreadyStopped { try? AlarmManager.shared.stop(id:sourceSystemID) }
        else if sourceSystemID != item.id { try? AlarmManager.shared.cancel(id:sourceSystemID) }
        systemAlarmStates[sourceSystemID]=nil
        if sourceSystemID == item.id, !item.weekdays.isEmpty { await schedule(item,replacingExisting:false) }
        refreshSystemState()
    }

    func armEscapeGuard(for item: WakeAlarm, after seconds: TimeInterval = 3) async {
        guard authorizationState == .authorized else { return }
        let duration=max(2,seconds), guardID=escapeGuardID(for:item.id), mask=weekdaysMask(item.weekdays)
        try? AlarmManager.shared.cancel(id:guardID)
        let button=AlarmButton(text:LocalizedStringResource(stringLiteral:AppText.localized("解除")),textColor:.white,systemImageName:"arrow.right.circle.fill")
        let alert=AlarmPresentation.Alert(title:LocalizedStringResource(stringLiteral:AppText.localized("解除が完了していません")),secondaryButton:button,secondaryButtonBehavior:.custom)
        let countdown=AlarmPresentation.Countdown(title:LocalizedStringResource(stringLiteral:AppText.localized("アラームを再開します")))
        let attrs=AlarmAttributes<EmptyWakeMetadata>(presentation:AlarmPresentation(alert:alert,countdown:countdown,paused:nil),metadata:EmptyWakeMetadata(),tintColor:IgnidoTheme.ember)
        let open=OpenAlarmIntent(alarmID:item.id.uuidString,hour:item.hour,minute:item.minute,weekdaysMask:mask,systemAlarmID:guardID.uuidString)
        let stop=StopWakeIntent(alarmID:item.id.uuidString,hour:item.hour,minute:item.minute,weekdaysMask:mask,systemAlarmID:guardID.uuidString)
        do { let cfg=AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer(duration:duration,attributes:attrs,stopIntent:stop,secondaryIntent:open,sound:IgnidoAlarmSoundCatalog.alertSound(for:item.soundName)); _=try await AlarmManager.shared.schedule(id:guardID,configuration:cfg); upsertEscapeGuardRecord(.init(alarmID:item.id,guardID:guardID,expiresAt:Date().addingTimeInterval(1800))) }
        catch { do { let cfg=AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer(duration:duration,attributes:attrs,stopIntent:stop,secondaryIntent:open,sound:.default); _=try await AlarmManager.shared.schedule(id:guardID,configuration:cfg); upsertEscapeGuardRecord(.init(alarmID:item.id,guardID:guardID,expiresAt:Date().addingTimeInterval(1800))) } catch { lastError=error.localizedDescription } }
        refreshSystemState()
    }

    func disarmEscapeGuard(for item: WakeAlarm) { removeEscapeGuardRecord(for:item.id,cancelSystemAlarm:true); refreshSystemState() }
    func cancelSystemAlarm(id: UUID) { try? AlarmManager.shared.cancel(id:id); systemAlarmStates[id]=nil }
    private func escapeGuardID(for id: UUID) -> UUID { var u=id.uuid; withUnsafeMutableBytes(of:&u){ $0[0]^=0xA7; $0[15]^=0x5C }; return UUID(uuid:u) }
    private func escapeGuardRecords() -> [IgnidoEscapeGuardRecord] { guard let d=UserDefaults.standard.data(forKey:escapeGuardKey), let x=try? JSONDecoder().decode([IgnidoEscapeGuardRecord].self,from:d) else { return [] }; return x }
    private func saveEscapeGuardRecords(_ x:[IgnidoEscapeGuardRecord]) { if x.isEmpty { UserDefaults.standard.removeObject(forKey:escapeGuardKey) } else if let d=try? JSONEncoder().encode(x) { UserDefaults.standard.set(d,forKey:escapeGuardKey) } }
    private func upsertEscapeGuardRecord(_ r:IgnidoEscapeGuardRecord) { var x=escapeGuardRecords().filter{$0.alarmID != r.alarmID && $0.guardID != r.guardID}; x.append(r); saveEscapeGuardRecords(x) }
    private func removeEscapeGuardRecord(for alarmID:UUID,cancelSystemAlarm:Bool) { let x=escapeGuardRecords(), targets=x.filter{$0.alarmID==alarmID}; if cancelSystemAlarm { for r in targets { try? AlarmManager.shared.cancel(id:r.guardID) } }; saveEscapeGuardRecords(x.filter{$0.alarmID != alarmID}) }
    private func liveEscapeGuardIDs(now:Date=Date()) -> Set<UUID> { let x=escapeGuardRecords(); var live:[IgnidoEscapeGuardRecord]=[]; for r in x { if r.expiresAt>now { live.append(r) } else { try? AlarmManager.shared.cancel(id:r.guardID) } }; if live.count != x.count { saveEscapeGuardRecords(live) }; return Set(live.map(\.guardID)) }

'''
s=s[:insert]+helpers+s[insert:]
old='''    func completeSystemAlarm(_ item: WakeAlarm) {
        try? AlarmManager.shared.stop(id: item.id)
        systemAlarmStates[item.id] = nil
        if item.weekdays.isEmpty, let index = alarms.firstIndex(where: { $0.id == item.id }) {
            alarms[index].enabled = false
            save()
        }
    }
'''
new='''    func completeSystemAlarm(_ item: WakeAlarm) async {
        disarmEscapeGuard(for:item); refreshSystemState()
        if item.weekdays.isEmpty {
            try? AlarmManager.shared.cancel(id:item.id); systemAlarmStates[item.id]=nil
            if let index=alarms.firstIndex(where:{$0.id==item.id}) { alarms[index].enabled=false; save() }
        } else if systemAlarmStates[item.id] != .scheduled { await schedule(item,replacingExisting:true) }
    }
'''
assert old in s; s=s.replace(old,new,1)
old='''            var allowed = allAlarmIDs.union(knownTimerIDs)
            if let expiry = UserDefaults.standard.object(forKey: testExpiryKey) as? Date, expiry > Date() {
                allowed.insert(testAlarmID)
            } else {
                UserDefaults.standard.removeObject(forKey: testExpiryKey)
            }
'''
new='''            var allowed = allAlarmIDs.union(knownTimerIDs)
            allowed.formUnion(liveEscapeGuardIDs())
            if let expiry = UserDefaults.standard.object(forKey: testExpiryKey) as? Date, expiry > Date() { allowed.insert(testAlarmID) }
            else { try? AlarmManager.shared.cancel(id:testAlarmID); UserDefaults.standard.removeObject(forKey:testExpiryKey) }
'''
assert old in s; s=s.replace(old,new,1)
p.write_text(s)
t=p.read_text();
for x in ['beginForegroundAlarmSession','armEscapeGuard(for item: WakeAlarm','testGeneration += 1','liveEscapeGuardIDs()','StopWakeIntent(alarmID: item.id.uuidString, hour: item.hour']: assert x in t,x
print('v196c alarm store applied')