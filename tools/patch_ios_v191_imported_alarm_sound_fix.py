from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# Stamp 1.9.1 / build 20.
plist = root / 'Info.plist'
s = plist.read_text(encoding='utf-8')
s = re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+(</string>)', r'\g<1>1.9.1\g<2>', s, count=1)
s = re.sub(r'(<key>CFBundleVersion</key>\s*<string>)[^<]+(</string>)', r'\g<1>20\g<2>', s, count=1)
plist.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = re.sub(r'CFBundleShortVersionString: "[^"]+"', 'CFBundleShortVersionString: "1.9.1"', s)
s = re.sub(r'CFBundleVersion: "[^"]+"', 'CFBundleVersion: "20"', s)
project.write_text(s, encoding='utf-8')

# Replace the 1.9.0 catalog with a robust Library/Sounds-backed implementation.
# Imported MP3/M4A/etc. are transcoded to a <=25s PCM CAF before AlarmKit sees them.
catalog = root / 'AlarmSoundCatalog.swift'
catalog.write_text(r'''import Foundation
import ActivityKit
import AVFoundation

struct IgnidoAlarmSoundOption: Identifiable, Hashable {
    let id: String
    let titleKey: String
    let detailKey: String
    let fileName: String?
}

enum IgnidoAlarmSoundCatalog {
    static let systemDefaultID = "system.default"
    static let emberID = "ignido.ember"
    static let blazeID = "ignido.blaze"
    static let pulseID = "ignido.pulse"
    static let sirenID = "ignido.siren"
    static let dawnID = "ignido.dawn"
    static let deepID = "ignido.deep"
    static let customPrefix = "custom:"

    static let options: [IgnidoAlarmSoundOption] = [
        .init(id: systemDefaultID, titleKey: "sound.system", detailKey: "sound.system.detail", fileName: nil),
        .init(id: emberID, titleKey: "sound.ember", detailKey: "sound.ember.detail", fileName: "ignido_ember.wav"),
        .init(id: blazeID, titleKey: "sound.blaze", detailKey: "sound.blaze.detail", fileName: "ignido_blaze.wav"),
        .init(id: pulseID, titleKey: "sound.pulse", detailKey: "sound.pulse.detail", fileName: "ignido_pulse.wav"),
        .init(id: sirenID, titleKey: "sound.siren", detailKey: "sound.siren.detail", fileName: "ignido_siren.wav"),
        .init(id: dawnID, titleKey: "sound.dawn", detailKey: "sound.dawn.detail", fileName: "ignido_dawn.wav"),
        .init(id: deepID, titleKey: "sound.deep", detailKey: "sound.deep.detail", fileName: "ignido_deep.wav")
    ]

    static func isCustom(_ raw: String) -> Bool { raw.hasPrefix(customPrefix) }

    static func customFileName(from raw: String) -> String? {
        guard isCustom(raw) else { return nil }
        let name = String(raw.dropFirst(customPrefix.count))
        return name.isEmpty ? nil : name
    }

    static func normalizedSelection(_ raw: String) -> String {
        if options.contains(where: { $0.id == raw }) { return raw }
        if let file = customFileName(from: raw), IgnidoAlarmSoundLibrary.soundExists(file) { return raw }
        return systemDefaultID
    }

    static func option(for raw: String) -> IgnidoAlarmSoundOption {
        let id = normalizedSelection(raw)
        if let file = customFileName(from: id) {
            return .init(id: id, titleKey: "sound.custom", detailKey: "sound.custom.detail", fileName: file)
        }
        return options.first(where: { $0.id == id }) ?? options[0]
    }

    static func alertSound(for raw: String) -> AlertConfiguration.AlertSound {
        IgnidoAlarmSoundLibrary.installBundledSounds()
        let option = option(for: raw)
        guard let fileName = option.fileName, IgnidoAlarmSoundLibrary.soundExists(fileName) else { return .default }
        return .named(fileName)
    }

    static func displayName(for raw: String) -> String {
        if let file = customFileName(from: normalizedSelection(raw)) {
            return String(format: NSLocalizedString("sound.custom.named", comment: "custom alarm sound filename"), file)
        }
        return NSLocalizedString(option(for: raw).titleKey, comment: "alarm sound name")
    }
}

enum IgnidoAlarmSoundLibrary {
    private static let bundled = [
        "ignido_ember.wav", "ignido_blaze.wav", "ignido_pulse.wav",
        "ignido_siren.wav", "ignido_dawn.wav", "ignido_deep.wav"
    ]

    static func soundsDirectory() throws -> URL {
        let library = try FileManager.default.url(for: .libraryDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let dir = library.appendingPathComponent("Sounds", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func soundExists(_ fileName: String) -> Bool {
        if let dir = try? soundsDirectory(), FileManager.default.fileExists(atPath: dir.appendingPathComponent(fileName).path) { return true }
        let base = (fileName as NSString).deletingPathExtension
        let ext = (fileName as NSString).pathExtension
        return Bundle.main.url(forResource: base, withExtension: ext)?.isFileURL == true
    }

    static func installBundledSounds() {
        guard let dir = try? soundsDirectory() else { return }
        for file in bundled {
            let dst = dir.appendingPathComponent(file)
            if FileManager.default.fileExists(atPath: dst.path) { continue }
            let base = (file as NSString).deletingPathExtension
            let ext = (file as NSString).pathExtension
            guard let src = Bundle.main.url(forResource: base, withExtension: ext) else { continue }
            try? FileManager.default.copyItem(at: src, to: dst)
        }
    }

    /// Convert any AVAudioFile-readable input (including MP3/M4A on iOS) into
    /// a linear-PCM CAF that AlarmKit can load from Library/Sounds.
    /// We cap it at 25 seconds, safely below Apple's 30-second custom-alert limit.
    static func importAsAlarmSound(from sourceURL: URL) throws -> String {
        let scoped = sourceURL.startAccessingSecurityScopedResource()
        defer { if scoped { sourceURL.stopAccessingSecurityScopedResource() } }

        let input = try AVAudioFile(forReading: sourceURL)
        let format = input.processingFormat
        guard format.sampleRate > 0, format.channelCount > 0 else {
            throw NSError(domain: "IGNIDOAlarmSound", code: 1, userInfo: [NSLocalizedDescriptionKey: "この音声形式を読み込めません"])
        }

        let dir = try soundsDirectory()
        let fileName = "ignido_user_\(UUID().uuidString.lowercased()).caf"
        let outputURL = dir.appendingPathComponent(fileName)
        let output = try AVAudioFile(forWriting: outputURL, settings: format.settings, commonFormat: format.commonFormat, interleaved: format.isInterleaved)

        let maxFrames = AVAudioFramePosition(format.sampleRate * 25.0)
        var remaining = min(input.length, maxFrames)
        let chunk: AVAudioFrameCount = 16384
        while remaining > 0 {
            let count = AVAudioFrameCount(min(AVAudioFramePosition(chunk), remaining))
            guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: count) else { break }
            try input.read(into: buffer, frameCount: count)
            if buffer.frameLength == 0 { break }
            try output.write(from: buffer)
            remaining -= AVAudioFramePosition(buffer.frameLength)
        }

        let attrs = try FileManager.default.attributesOfItem(atPath: outputURL.path)
        let size = (attrs[.size] as? NSNumber)?.intValue ?? 0
        if size <= 44 {
            try? FileManager.default.removeItem(at: outputURL)
            throw NSError(domain: "IGNIDOAlarmSound", code: 2, userInfo: [NSLocalizedDescriptionKey: "アラーム音への変換に失敗しました"])
        }
        return fileName
    }
}

@MainActor
final class IgnidoAlarmSoundPreview: ObservableObject {
    static let shared = IgnidoAlarmSoundPreview()
    private var player: AVAudioPlayer?

    func play(selection raw: String) {
        stop()
        let option = IgnidoAlarmSoundCatalog.option(for: raw)
        guard let fileName = option.fileName else { return }
        let url: URL?
        if let dir = try? IgnidoAlarmSoundLibrary.soundsDirectory(), FileManager.default.fileExists(atPath: dir.appendingPathComponent(fileName).path) {
            url = dir.appendingPathComponent(fileName)
        } else {
            url = Bundle.main.url(forResource: (fileName as NSString).deletingPathExtension, withExtension: (fileName as NSString).pathExtension)
        }
        guard let url else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.duckOthers])
            try session.setActive(true)
            let p = try AVAudioPlayer(contentsOf: url)
            p.numberOfLoops = 0
            p.volume = 1.0
            p.prepareToPlay()
            p.play()
            player = p
        } catch { player = nil }
    }

    func stop() { player?.stop(); player = nil }
}
''', encoding='utf-8')

# Install bundled tones into Library/Sounds on every launch as well as keeping them
# in the main bundle. This removes ambiguity about where AlarmKit resolves .named().
app = root / 'IGNIDOWakeApp.swift'
s = app.read_text(encoding='utf-8')
needle = '        IgnidoAppearance.configure()\n'
replacement = '        IgnidoAppearance.configure()\n        IgnidoAlarmSoundLibrary.installBundledSounds()\n'
if needle not in s:
    raise SystemExit('IGNIDOWakeApp init insertion point not found')
s = s.replace(needle, replacement, 1)
app.write_text(s, encoding='utf-8')

# Wire imported AUDIO into the actual AlarmKit sound selection. 1.9.0 only stored
# the file for in-app media playback, leaving soundName unchanged, so AlarmKit kept
# playing its prior/default sound. Audio imports are now transcoded to PCM CAF and
# soundName points to that Library/Sounds filename.
views = root / 'AlarmViews.swift'
s = views.read_text(encoding='utf-8')

# If a custom imported sound is selected, show it in the picker instead of an invalid selection.
picker_needle = '''                    ForEach(IgnidoAlarmSoundCatalog.options) { option in
                        Text(NSLocalizedString(option.titleKey, comment: "alarm sound name"))
                            .tag(option.id)
                    }
'''
picker_replacement = '''                    ForEach(IgnidoAlarmSoundCatalog.options) { option in
                        Text(NSLocalizedString(option.titleKey, comment: "alarm sound name"))
                            .tag(option.id)
                    }
                    if IgnidoAlarmSoundCatalog.isCustom(draft.soundName) {
                        Text(IgnidoAlarmSoundCatalog.displayName(for: draft.soundName))
                            .tag(draft.soundName)
                    }
'''
if picker_needle not in s:
    raise SystemExit('sound picker block not found')
s = s.replace(picker_needle, picker_replacement, 1)

import_needle = '''        .fileImporter(isPresented: $importingMedia, allowedContentTypes: [.movie, .audio], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first else { return }
                draft.mediaFileName = try MediaLibrary.importFile(from: url)
                importError = nil
            } catch { importError = error.localizedDescription }
        }
'''
import_replacement = '''        .fileImporter(isPresented: $importingMedia, allowedContentTypes: [.movie, .audio], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first else { return }
                draft.mediaFileName = try MediaLibrary.importFile(from: url)
                if let type = UTType(filenameExtension: url.pathExtension), type.conforms(to: .audio) {
                    let alarmFile = try IgnidoAlarmSoundLibrary.importAsAlarmSound(from: url)
                    draft.soundName = IgnidoAlarmSoundCatalog.customPrefix + alarmFile
                    IgnidoAlarmSoundPreview.shared.play(selection: draft.soundName)
                }
                importError = nil
            } catch { importError = error.localizedDescription }
        }
'''
if import_needle not in s:
    raise SystemExit('AlarmViews fileImporter block not found')
s = s.replace(import_needle, import_replacement, 1)

# Make the UI explicit about what happens to imported audio.
media_needle = '                Button("音声 / 動画ファイルを選ぶ") { importingMedia = true }\n                    .tint(IgnidoTheme.ember)\n'
media_replacement = '''                Button("音声 / 動画ファイルを選ぶ") { importingMedia = true }
                    .tint(IgnidoTheme.ember)
                Text(NSLocalizedString("sound.import.help", comment: "imported audio AlarmKit behavior"))
                    .font(.caption)
                    .foregroundStyle(IgnidoTheme.secondaryText)
'''
if media_needle not in s:
    raise SystemExit('AlarmViews media import button not found')
s = s.replace(media_needle, media_replacement, 1)
views.write_text(s, encoding='utf-8')

# Add localized UI text.
translations = {
    'ja': {
        'sound.custom': '自分の音',
        'sound.custom.detail': 'AlarmKit用に変換済みの音声',
        'sound.custom.named': '自分の音（%@）',
        'sound.import.help': 'MP3/M4Aなどの音声は選択時に25秒以内のPCM CAFへ変換して、実際のシステムアラーム音として使います。動画は従来どおりアプリ内再生用です。',
    },
    'en': {
        'sound.custom': 'My Sound',
        'sound.custom.detail': 'Audio converted for AlarmKit',
        'sound.custom.named': 'My Sound (%@)',
        'sound.import.help': 'MP3/M4A and other audio is converted to a PCM CAF under 25 seconds and used as the actual system alarm sound. Video remains for in-app playback.',
    },
    'ko': {
        'sound.custom': '내 소리',
        'sound.custom.detail': 'AlarmKit용으로 변환된 오디오',
        'sound.custom.named': '내 소리 (%@)',
        'sound.import.help': 'MP3/M4A 등의 오디오는 선택할 때 25초 이하 PCM CAF로 변환되어 실제 시스템 알람 소리로 사용됩니다. 동영상은 기존처럼 앱 내 재생용입니다.',
    },
}
for lang, pairs in translations.items():
    p = root / f'{lang}.lproj' / 'Localizable.strings'
    text = p.read_text(encoding='utf-8')
    text += '\n/* IGNIDO Wake 1.9.1 imported alarm sound fix */\n'
    for key, value in pairs.items():
        escaped = value.replace('\\', '\\\\').replace('"', '\\"')
        text += f'"{key}" = "{escaped}";\n'
    p.write_text(text, encoding='utf-8')

# Static validation.
cat = catalog.read_text(encoding='utf-8')
for token in [
    'Library/Sounds', 'importAsAlarmSound(from sourceURL: URL)', '25.0',
    'customPrefix = "custom:"', 'return .named(fileName)', 'installBundledSounds()'
]:
    if token not in cat:
        raise SystemExit(f'missing v1.9.1 alarm sound token: {token}')
view_text = views.read_text(encoding='utf-8')
for token in [
    'IgnidoAlarmSoundLibrary.importAsAlarmSound(from: url)',
    'draft.soundName = IgnidoAlarmSoundCatalog.customPrefix + alarmFile',
    'type.conforms(to: .audio)', 'sound.import.help'
]:
    if token not in view_text:
        raise SystemExit(f'missing v1.9.1 import wiring token: {token}')
if 'IgnidoAlarmSoundLibrary.installBundledSounds()' not in app.read_text(encoding='utf-8'):
    raise SystemExit('bundled alarm sound Library/Sounds installation missing')
if '<string>1.9.1</string>' not in plist.read_text(encoding='utf-8') or '<string>20</string>' not in plist.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist not stamped 1.9.1/20')

print('IGNIDO Wake iOS 1.9.1 imported MP3/custom AlarmKit sound fix applied')
