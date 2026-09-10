from pathlib import Path
root=Path('ios/IGNIDOWake')
p=root/'RootView.swift'; s=p.read_text()
old='''        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                streakStore.reload()
                Task {
                    await reconcileDaemonOwnership()
                    await alarmStore.clearStaleAlertingAlarms()
                    await consumeSystemActions()
                }
            }
        }
'''
new='''        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                streakStore.reload()
                if let alarm = activeAlarm { alarmStore.disarmEscapeGuard(for: alarm); startForegroundAlarmAudio(alarm) }
                else { Task { await reconcileDaemonOwnership(); await alarmStore.clearStaleAlertingAlarms(); await consumeSystemActions() } }
            } else if phase == .background, let alarm = activeAlarm {
                IgnidoAlarmForegroundSoundGuardian.shared.stop()
                Task { await alarmStore.armEscapeGuard(for: alarm, after: 3) }
            }
        }
'''
assert old in s; s=s.replace(old,new,1)
s=s.replace('''        .fullScreenCover(item: $activeAlarm, onDismiss: { Task { await consumeSystemActions() } }) { alarm in
            alarmDestination(alarm)
        }
''','''        .fullScreenCover(item: $activeAlarm, onDismiss: { Task { await consumeSystemActions() } }) { alarm in
            alarmDestination(alarm).interactiveDismissDisabled(true)
        }
''',1)
old='''    private func completeAlarm(_ alarm: WakeAlarm) {
        streakStore.recordWake()
        streakParityStore.recordWake(alarms: alarmStore.alarms)
        activeAlarm = nil
        Task { alarmStore.completeSystemAlarm(alarm); await consumeSystemActions() }
    }
'''
new='''    private func completeAlarm(_ alarm: WakeAlarm) {
        IgnidoAlarmForegroundSoundGuardian.shared.stop()
        streakStore.recordWake(); streakParityStore.recordWake(alarms: alarmStore.alarms); activeAlarm=nil
        Task { await alarmStore.completeSystemAlarm(alarm); await consumeSystemActions() }
    }
    private func startForegroundAlarmAudio(_ alarm: WakeAlarm) {
        if isVideoAlarm(alarm) { IgnidoAlarmForegroundSoundGuardian.shared.stop() }
        else { IgnidoAlarmForegroundSoundGuardian.shared.start(alarm: alarm) }
    }
    private func isVideoAlarm(_ alarm: WakeAlarm) -> Bool {
        guard let n=alarm.mediaFileName, let u=MediaLibrary.url(for:n) else { return false }
        return ["mp4","mov","m4v","avi","webm"].contains(u.pathExtension.lowercased())
    }
'''
assert old in s; s=s.replace(old,new,1)
old='''        if activeAlarm == nil,
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
'''
new='''        if activeAlarm == nil,
           let request = WakeIntentState.consumeAlarmRequest(),
           Date().timeIntervalSince(request.createdAt) <= 120,
           let id = UUID(uuidString: request.alarmID),
           let alarm = alarmStore.alarm(id: id) {
            let sourceID=UUID(uuidString:request.systemAlarmID ?? request.alarmID) ?? id
            var alerting=alarmStore.isSystemAlarmAlerting(id:sourceID)
            if !alerting && request.allowStoppedSource != true {
                for _ in 0..<4 where !alerting { try? await Task.sleep(for:.milliseconds(150)); alerting=alarmStore.isSystemAlarmAlerting(id:sourceID) }
            }
            if alarmStore.isWithinExpectedAlertWindow(alarm) && (alerting || request.allowStoppedSource == true) {
                startForegroundAlarmAudio(alarm); activeAlarm=alarm
                await alarmStore.beginForegroundAlarmSession(alarm,sourceSystemID:sourceID,sourceAlreadyStopped:request.allowStoppedSource == true)
                return
            }
            if alerting { alarmStore.cancelSystemAlarm(id:sourceID) }
        }
'''
assert old in s; s=s.replace(old,new,1); p.write_text(s)

p=root/'MediaPlayerView.swift'; s=p.read_text(); old='''        .onAppear {
            AlarmRuntime.preparePlaybackSession()
            AlarmHaptics.shared.start(alarm.vibration)
        }
'''; new='''        .onAppear {
            IgnidoAlarmForegroundSoundGuardian.shared.stop()
            AlarmRuntime.preparePlaybackSession()
            AlarmHaptics.shared.start(alarm.vibration)
        }
'''; assert old in s; p.write_text(s.replace(old,new,1))

p=root/'AlarmViews.swift'; s=p.read_text(); old='''            Section {
                Button(NSLocalizedString("sound.test5", comment: "test selected alarm sound")) { Task { await store.scheduleTest(draft) } }
                    .tint(IgnidoTheme.amber)
            }
'''; new='''            Section {
                Button(NSLocalizedString("sound.test5", comment: "test selected alarm sound")) { Task { await store.scheduleTest(draft) } }
                    .tint(IgnidoTheme.amber)
                Button("5秒テストを停止・消去") { store.cancelTestAlarm() }
                    .tint(IgnidoTheme.secondaryText)
                Text("5秒テストは毎回同じテストIDを使い、終了後に自動消去します。")
                    .font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
            }
'''; assert old in s; p.write_text(s.replace(old,new,1))

assert 'armEscapeGuard(for: alarm, after: 3)' in (root/'RootView.swift').read_text()
assert '5秒テストを停止・消去' in (root/'AlarmViews.swift').read_text()
print('v196d root/ui applied')