from pathlib import Path
import re
root=Path('ios/IGNIDOWake')

# 1.9.6 / build 25 + background audio only while an alarm session is active.
p=root/'Info.plist'; s=p.read_text()
s=re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+',r'\g<1>1.9.6',s,count=1)
s=re.sub(r'(<key>CFBundleVersion</key>\s*<string>)[^<]+',r'\g<1>25',s,count=1)
if '<key>UIBackgroundModes</key>' not in s:
    marker='<key>UILaunchScreen</key>'
    if marker not in s:
        raise SystemExit('Info.plist UILaunchScreen insertion point not found')
    s=s.replace(marker,'<key>UIBackgroundModes</key>\n  <array><string>audio</string></array>\n  '+marker,1)
p.write_text(s)

p=Path('ios/project.yml'); s=p.read_text()
s=re.sub(r'CFBundleShortVersionString: "[^"]+"','CFBundleShortVersionString: "1.9.6"',s)
s=re.sub(r'CFBundleVersion: "[^"]+"','CFBundleVersion: "25"',s)
if 'UIBackgroundModes:' not in s:
    marker='        NSMotionUsageDescription: "アラーム解除の歩数・シェイク判定にiPhoneのモーションセンサーを使用します。"\n'
    if marker not in s:
        raise SystemExit('project.yml NSMotionUsageDescription insertion point not found')
    s=s.replace(marker,marker+'        UIBackgroundModes:\n          - audio\n',1)
p.write_text(s)

p=root/'IGNIDOWakeApp.swift'; s=p.read_text(); s=s.replace('        AlarmRuntime.preparePlaybackSession()\n',''); p.write_text(s)

p=root/'AlarmSoundCatalog.swift'; s=p.read_text()
if 'final class IgnidoAlarmForegroundSoundGuardian' not in s:
    s += r'''

@MainActor
final class IgnidoAlarmForegroundSoundGuardian: NSObject, ObservableObject, AVAudioPlayerDelegate {
    static let shared = IgnidoAlarmForegroundSoundGuardian()
    @Published private(set) var activeAlarmID: UUID?
    private var player: AVAudioPlayer?
    private var watchdog: Timer?
    private var desiredURL: URL?
    private var desiredVolume: Float = 1

    func start(alarm: WakeAlarm) {
        if activeAlarmID == alarm.id, player?.isPlaying == true { return }
        stop(deactivateSession: false)
        guard let url = playbackURL(for: alarm) else { return }
        desiredURL = url
        desiredVolume = Float(min(max(alarm.volume, 0), 1))
        activeAlarmID = alarm.id
        startPlayer()
        watchdog = Timer.scheduledTimer(withTimeInterval: 0.45, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.activeAlarmID != nil else { return }
                if self.player?.isPlaying != true { self.startPlayer() }
            }
        }
    }

    func stop() { stop(deactivateSession: true) }

    private func stop(deactivateSession: Bool) {
        watchdog?.invalidate(); watchdog=nil
        player?.stop(); player?.delegate=nil; player=nil
        desiredURL=nil; activeAlarmID=nil
        if deactivateSession { try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation]) }
    }

    private func startPlayer() {
        guard let url=desiredURL, activeAlarmID != nil else { return }
        do {
            let session=AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.duckOthers])
            try session.setActive(true)
            let p=try AVAudioPlayer(contentsOf: url)
            p.delegate=self; p.numberOfLoops = -1; p.volume=desiredVolume; p.prepareToPlay()
            guard p.play() else { return }
            player=p
        } catch { player=nil }
    }

    private func playbackURL(for alarm: WakeAlarm) -> URL? {
        let option=IgnidoAlarmSoundCatalog.option(for: IgnidoAlarmSoundCatalog.normalizedSelection(alarm.soundName))
        if let file=option.fileName {
            if let dir=try? IgnidoAlarmSoundLibrary.soundsDirectory() {
                let u=dir.appendingPathComponent(file)
                if FileManager.default.fileExists(atPath: u.path) { return u }
            }
            let base=(file as NSString).deletingPathExtension, ext=(file as NSString).pathExtension
            if let u=Bundle.main.url(forResource: base, withExtension: ext) { return u }
        }
        if let dir=try? IgnidoAlarmSoundLibrary.soundsDirectory() {
            let u=dir.appendingPathComponent("ignido_ember.wav")
            if FileManager.default.fileExists(atPath:u.path) { return u }
        }
        return Bundle.main.url(forResource:"ignido_ember",withExtension:"wav")
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor [weak self] in guard let self, self.activeAlarmID != nil else { return }; self.startPlayer() }
    }
    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: (any Error)?) {
        Task { @MainActor [weak self] in guard let self, self.activeAlarmID != nil else { return }; self.startPlayer() }
    }
}
'''
p.write_text(s)

assert '<string>1.9.6</string>' in (root/'Info.plist').read_text()
assert '<string>25</string>' in (root/'Info.plist').read_text()
assert '<key>UIBackgroundModes</key>' in (root/'Info.plist').read_text()
assert '<string>audio</string>' in (root/'Info.plist').read_text()
assert 'p.numberOfLoops = -1' in (root/'AlarmSoundCatalog.swift').read_text()
assert 'AlarmRuntime.preparePlaybackSession()' not in (root/'IGNIDOWakeApp.swift').read_text()
print('v196a audio guard applied')