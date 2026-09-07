import Foundation
import AVFoundation
import AudioToolbox
import UIKit

enum AlarmRuntime {
    static let bundledAlarmSoundName = "ignido_alarm.wav"

    /// AlarmKit can play named sounds only from the app bundle or Library/Sounds.
    /// Preserve compatible user-selected alert files there; otherwise use the
    /// bundled IGNIDO alarm sound and play the selected media after the user opens IGNIDO.
    static func alarmKitSoundName(for mediaFileName: String?) -> String {
        guard let mediaFileName,
              let source = MediaLibrary.url(for: mediaFileName) else {
            return bundledAlarmSoundName
        }
        let ext = source.pathExtension.lowercased()
        guard ["wav", "caf", "aiff", "aif"].contains(ext) else {
            return bundledAlarmSoundName
        }
        do {
            let fm = FileManager.default
            let library = try fm.url(for: .libraryDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            let sounds = library.appendingPathComponent("Sounds", isDirectory: true)
            try fm.createDirectory(at: sounds, withIntermediateDirectories: true)
            let safeName = "ignido-user-\(abs(mediaFileName.hashValue)).\(ext)"
            let destination = sounds.appendingPathComponent(safeName)
            if fm.fileExists(atPath: destination.path) { try fm.removeItem(at: destination) }
            try fm.copyItem(at: source, to: destination)
            return safeName
        } catch {
            return bundledAlarmSoundName
        }
    }

    @MainActor
    static func preparePlaybackSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .moviePlayback, options: [.duckOthers])
            try session.setActive(true)
        } catch {
            // AlarmKit still provides the system alert sound even if this fails.
        }
    }
}

@MainActor
final class AlarmHaptics {
    static let shared = AlarmHaptics()
    private var timer: Timer?
    private var mode: VibrationMode = .off
    private var irregularFlip = false

    private init() {}

    func start(_ mode: VibrationMode) {
        stop()
        self.mode = mode
        guard mode != .off else { return }
        pulse()
        let interval = mode == .strong ? 0.62 : 0.95
        timer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.pulse() }
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        mode = .off
    }

    private func pulse() {
        guard mode != .off else { return }
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        if mode == .strong {
            let generator = UINotificationFeedbackGenerator()
            generator.prepare()
            generator.notificationOccurred(.warning)
        } else {
            irregularFlip.toggle()
            let generator = UIImpactFeedbackGenerator(style: irregularFlip ? .heavy : .medium)
            generator.prepare()
            generator.impactOccurred(intensity: irregularFlip ? 1.0 : 0.65)
        }
    }
}
