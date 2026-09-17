from pathlib import Path
r=Path('ios/IGNIDOWake')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)
def repl(p,a,b,count=1):
    s=read(p)
    if a not in s: raise SystemExit(f'missing {p}: {a[:120]!r}')
    write(p,s.replace(a,b,count))
repl('Info.plist','<string>1.9.7</string>','<string>2.0.0</string>');repl('Info.plist','<string>26</string>','<string>100</string>')
py=Path('ios/project.yml');s=py.read_text().replace('CFBundleShortVersionString: "1.9.7"','CFBundleShortVersionString: "2.0.0"').replace('CFBundleVersion: "26"','CFBundleVersion: "100"');py.write_text(s)
p='AlarmModel.swift';s=read(p);s=s.replace('    var snoozeMinutes: Int\n    var createdAt: Date\n','    var snoozeMinutes: Int\n    var ringDurationSec: Int\n    var fullStopDurationSec: Int\n    var createdAt: Date\n',1).replace('        snoozeMinutes: Int = 5, createdAt: Date = Date()\n','        snoozeMinutes: Int = 5, ringDurationSec: Int = 0, fullStopDurationSec: Int = 0, createdAt: Date = Date()\n',1).replace('        self.snoozeMinutes = snoozeMinutes\n        self.createdAt = createdAt\n','        self.snoozeMinutes = max(1, min(60, snoozeMinutes))\n        self.ringDurationSec = max(0, min(86400, ringDurationSec))\n        self.fullStopDurationSec = max(0, min(86400, fullStopDurationSec))\n        self.createdAt = createdAt\n',1).replace('             snoozeMinutes, createdAt\n','             snoozeMinutes, ringDurationSec, fullStopDurationSec, createdAt\n',1).replace('        snoozeMinutes = try c.decodeIfPresent(Int.self, forKey: .snoozeMinutes) ?? 5\n        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()\n','        snoozeMinutes = max(1, min(60, try c.decodeIfPresent(Int.self, forKey: .snoozeMinutes) ?? 5))\n        ringDurationSec = max(0, min(86400, try c.decodeIfPresent(Int.self, forKey: .ringDurationSec) ?? 0))\n        fullStopDurationSec = max(0, min(86400, try c.decodeIfPresent(Int.self, forKey: .fullStopDurationSec) ?? 0))\n        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()\n',1);write(p,s)
p='AlarmViews.swift';s=read(p);needle='            Section("スヌーズ") {\n                Stepper(AppText.format("%d分", draft.snoozeMinutes), value: $draft.snoozeMinutes, in: 1...60)\n            }\n\n';s=s.replace(needle,needle+'            Section("自動停止") {\n                Stepper("音・振動だけ自動停止: \\(autoStopText(draft.ringDurationSec))", value: $draft.ringDurationSec, in: 0...3600, step: 30)\n                Stepper("アラームを完全自動停止: \\(autoStopText(draft.fullStopDurationSec))", value: $draft.fullStopDurationSec, in: 0...3600, step: 30)\n                Text("iPhoneではIGNIDO Wakeの解除画面が前面にある間に適用します。ロック画面のAlarmKit表示はiOSが管理します。").font(.caption).foregroundStyle(IgnidoTheme.secondaryText)\n            }\n\n',1);marker='    private func importMedia(_ url: URL) {\n';s=s.replace(marker,'    private func autoStopText(_ seconds: Int) -> String { if seconds <= 0 { return AppText.localized("オフ") }; if seconds % 60 == 0 { return AppText.format("%d分", seconds / 60) }; return AppText.format("%d秒", seconds) }\n\n'+marker,1);write(p,s)
p='AlarmStore.swift';s=read(p);start=s.index('    private func register(_ item: WakeAlarm, schedule: Alarm.Schedule, defaultSound: Bool) async throws -> Alarm {');end=s.index('    private func verifySystemRegistration',start);new='''    private func register(_ item: WakeAlarm, schedule: Alarm.Schedule, defaultSound: Bool) async throws -> Alarm {
        let stopButton = AlarmButton(text: LocalizedStringResource(stringLiteral: AppText.localized("解除")), textColor: .white, systemImageName: "xmark.circle.fill")
        let snoozeButton = AlarmButton(text: LocalizedStringResource(stringLiteral: AppText.localized("スヌーズ")), textColor: .white, systemImageName: "repeat.circle.fill")
        let alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: "\(item.timeText)  \(item.displayLabel)"), stopButton: stopButton, secondaryButton: snoozeButton, secondaryButtonBehavior: .countdown)
        let countdown = AlarmPresentation.Countdown(title: LocalizedStringResource(stringLiteral: AppText.localized("スヌーズ中")))
        let attrs = AlarmAttributes<EmptyWakeMetadata>(presentation: AlarmPresentation(alert: alert, countdown: countdown, paused: nil), metadata: EmptyWakeMetadata(), tintColor: IgnidoTheme.ember)
        let mask = weekdaysMask(item.weekdays)
        let stopIntent = StopWakeIntent(alarmID: item.id.uuidString, hour: item.hour, minute: item.minute, weekdaysMask: mask, systemAlarmID: item.id.uuidString)
        let duration = Alarm.CountdownDuration(preAlert: 0, postAlert: TimeInterval(max(1, min(60, item.snoozeMinutes)) * 60))
        let config = AlarmManager.AlarmConfiguration<EmptyWakeMetadata>(countdownDuration: duration, schedule: schedule, attributes: attrs, stopIntent: stopIntent, secondaryIntent: nil, sound: defaultSound ? .default : IgnidoAlarmSoundCatalog.alertSound(for: item.soundName))
        return try await AlarmManager.shared.schedule(id: item.id, configuration: config)
    }

''';s=s[:start]+new+s[end:];marker='    func isSystemAlarmAlerting(id: UUID) -> Bool {\n';s=s.replace(marker,'    func snoozeSystemAlarm(_ item: WakeAlarm) -> Bool { refreshSystemState(); guard systemAlarmStates[item.id] == .alerting else { return false }; do { try AlarmManager.shared.countdown(id: item.id); refreshSystemState(); return true } catch { lastError = error.localizedDescription; return false } }\n\n'+marker,1);write(p,s)
p='MultiTimer.swift';s=read(p);old='''    func refreshFinished() {
        var changed = false
        for i in items.indices where items[i].running && items[i].currentRemaining <= 0.05 {
            items[i].running = false
            items[i].remaining = 0
            items[i].endDate = nil
            changed = true
        }
        if changed { save() }
    }
''';s=s.replace(old,'    func refreshFinished() { }\n\n    func firstAlertingTimer(in states: [UUID: Alarm.State]) -> WakeTimerItem? { items.first { $0.running && states[$0.id] == .alerting } }\n',1);write(p,s)
p='FeatureStores.swift';s=read(p);s=s.replace('    var alarmKitIdentifiers: Set<UUID> { running ? [systemAlarmID] : [] }\n','    var alarmKitIdentifiers: Set<UUID> { running ? [systemAlarmID] : [] }\n    var alarmKitIdentifier: UUID { systemAlarmID }\n',1);start=s.index('    func markFinished() {');end=s.index('    private func scheduleSystemAlarm',start);s=s[:start]+'    func markFinished() { }\n\n    func consumeFinishedAlert() { try? AlarmManager.shared.cancel(id: systemAlarmID); running = false; paused = false; endDate = nil; pausedRemaining = 0 }\n\n'+s[end:];write(p,s)
p='RootView.swift';s=read(p);s=s.replace('    @State private var showTimerResult = false\n','    @State private var showTimerResult = false\n    @State private var alarmSessionToken = UUID()\n',1);s=s.replace('''        TabView {
            AlarmListView()
                .tabItem { Label("アラーム", systemImage: "alarm.fill") }
            MultiTimerViewV186()
                .tabItem { Label("タイマー", systemImage: "timer") }
            StopwatchView()
                .tabItem { Label("ストップウォッチ", systemImage: "stopwatch.fill") }
            WorldClockView()
                .tabItem { Label("世界時計", systemImage: "globe") }
            StreakParityDashboard()
                .tabItem { Label("ストリーク", systemImage: "flame.fill") }
        }
''','''        TabView {
            AlarmListView().tabItem { Label("アラーム", systemImage: "alarm.fill") }
            WorldClockView().tabItem { Label("時計", systemImage: "clock.fill") }
            MultiTimerViewV186().tabItem { Label("タイマー", systemImage: "timer") }
            StopwatchView().tabItem { Label("ストップウォッチ", systemImage: "stopwatch.fill") }
            StreakParityDashboard().tabItem { Label("ストリーク", systemImage: "flame.fill") }
        }
''',1);old='''        .onReceive(alarmStore.$systemAlarmStates) { _ in
            guard scenePhase == .active, activeAlarm == nil else { return }
            if let alarm = alarmStore.firstExpectedAlertingAlarm() {
                startForegroundAlarmAudio(alarm)
                activeAlarm = alarm
                Task { await alarmStore.beginForegroundAlarmSession(alarm, sourceSystemID: alarm.id, sourceAlreadyStopped: false) }
            } else {
                Task { await alarmStore.clearStaleAlertingAlarms() }
            }
        }
''';new='''        .onReceive(alarmStore.$systemAlarmStates) { states in
            guard scenePhase == .active else { return }
            if activeAlarm == nil, let alarm = alarmStore.firstExpectedAlertingAlarm() { startForegroundAlarmAudio(alarm); activeAlarm = alarm; armForegroundAutoStop(alarm); Task { await alarmStore.beginForegroundAlarmSession(alarm, sourceSystemID: alarm.id, sourceAlreadyStopped: false) }; return }
            if activeAlarm == nil, activeTimer == nil, let item = multiTimerStore.firstAlertingTimer(in: states) { let snapshot=item; multiTimerStore.finish(item.id); activeTimer=snapshot; return }
            if activeAlarm == nil, activeTimer == nil, !showTimerResult, timerStore.running, states[timerStore.alarmKitIdentifier] == .alerting { timerStore.consumeFinishedAlert(); showTimerResult=true; return }
            if activeAlarm == nil { Task { await alarmStore.clearStaleAlertingAlarms() } }
        }
''';s=s.replace(old,new,1);s=s.replace('    private func alarmDestination(_ alarm: WakeAlarm) -> some View {\n','    private func alarmDestination(_ alarm: WakeAlarm) -> some View {\n        ZStack(alignment: .bottomLeading) {\n',1);needle='        }\n    }\n\n    private func completeAlarm(_ alarm: WakeAlarm) {\n';s=s.replace(needle,'        }\n            if alarmStore.isSystemAlarmAlerting(id: alarm.id) { Button("スヌーズ \\(alarm.snoozeMinutes)分") { snoozeAlarm(alarm) }.buttonStyle(.borderedProminent).tint(IgnidoTheme.secondaryText.opacity(0.9)).padding(20) }\n        }\n    }\n\n    private func completeAlarm(_ alarm: WakeAlarm) {\n',1);s=s.replace('    private func completeAlarm(_ alarm: WakeAlarm) {\n        IgnidoAlarmForegroundSoundGuardian.shared.stop()\n','    private func completeAlarm(_ alarm: WakeAlarm) {\n        alarmSessionToken = UUID()\n        IgnidoAlarmForegroundSoundGuardian.shared.stop()\n',1);marker='    private func startForegroundAlarmAudio(_ alarm: WakeAlarm) {\n';helpers='''    private func snoozeAlarm(_ alarm: WakeAlarm) { guard alarmStore.snoozeSystemAlarm(alarm) else { return }; alarmSessionToken=UUID(); IgnidoAlarmForegroundSoundGuardian.shared.stop(); AlarmHaptics.shared.stop(); activeAlarm=nil }
    private func armForegroundAutoStop(_ alarm: WakeAlarm) { let token=UUID(); alarmSessionToken=token; if alarm.ringDurationSec>0 && !isVideoAlarm(alarm) { Task { @MainActor in try? await Task.sleep(for:.seconds(alarm.ringDurationSec)); guard alarmSessionToken==token, activeAlarm?.id==alarm.id else{return}; IgnidoAlarmForegroundSoundGuardian.shared.stop(); AlarmHaptics.shared.stop() } }; if alarm.fullStopDurationSec>0 { Task { @MainActor in try? await Task.sleep(for:.seconds(alarm.fullStopDurationSec)); guard alarmSessionToken==token, activeAlarm?.id==alarm.id else{return}; completeAlarm(alarm) } } }

''';s=s.replace(marker,helpers+marker,1);s=s.replace('startForegroundAlarmAudio(alarm); activeAlarm=alarm\n                await alarmStore.beginForegroundAlarmSession','startForegroundAlarmAudio(alarm); activeAlarm=alarm; armForegroundAutoStop(alarm)\n                await alarmStore.beginForegroundAlarmSession',1).replace('if !showTimerResult, WakeIntentState.consumeTimer() { showTimerResult = true }','if !showTimerResult, WakeIntentState.consumeTimer() { timerStore.consumeFinishedAlert(); showTimerResult = true }',1);write(p,s)
assert '<string>2.0.0</string>' in read('Info.plist');assert 'secondaryButtonBehavior: .countdown' in read('AlarmStore.swift');assert 'firstAlertingTimer' in read('RootView.swift');assert 'Label("時計"' in read('RootView.swift');print('iOS 2.0.0 parity patch applied')
