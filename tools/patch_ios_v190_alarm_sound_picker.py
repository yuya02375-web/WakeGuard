from pathlib import Path
import re, math, struct, wave

root = Path('ios/IGNIDOWake')

# Stamp 1.9.0 / build 19.
plist = root / 'Info.plist'
s = plist.read_text(encoding='utf-8')
s = re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+(</string>)', r'\g<1>1.9.0\g<2>', s, count=1)
s = re.sub(r'(<key>CFBundleVersion</key>\s*<string>)[^<]+(</string>)', r'\g<1>19\g<2>', s, count=1)
plist.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = re.sub(r'CFBundleShortVersionString: "[^"]+"', 'CFBundleShortVersionString: "1.9.0"', s)
s = re.sub(r'CFBundleVersion: "[^"]+"', 'CFBundleVersion: "19"', s)
project.write_text(s, encoding='utf-8')

# Generate six short, PCM WAV alarm tones. Apple documents custom alert sounds as
# Linear PCM / IMA4 / µLaw / aLaw in AIFF/WAV/CAF and under 30 seconds.
rate = 44100
seconds = 8.0

def write_tone(name, generator):
    out = root / name
    frames = []
    total = int(rate * seconds)
    for i in range(total):
        t = i / rate
        v = max(-0.94, min(0.94, generator(t)))
        frames.append(struct.pack('<h', int(v * 32767)))
    with wave.open(str(out), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b''.join(frames))
    if out.stat().st_size < 100000:
        raise SystemExit(f'generated alarm sound too small: {name}')

# Distinct patterns, intentionally simple and loud enough for alarm use.
def gated_sine(t, f, period, duty, amp=0.82):
    phase = t % period
    if phase > period * duty:
        return 0.0
    attack = min(1.0, phase / 0.025)
    release_start = period * duty - 0.05
    release = 1.0 if phase <= release_start else max(0.0, (period * duty - phase) / 0.05)
    return amp * attack * release * math.sin(2 * math.pi * f * t)

write_tone('ignido_ember.wav', lambda t: gated_sine(t, 760, 0.72, 0.70, 0.82))
write_tone('ignido_blaze.wav', lambda t: gated_sine(t, 980 if int(t * 2) % 2 == 0 else 820, 0.48, 0.78, 0.86))
write_tone('ignido_pulse.wav', lambda t: gated_sine(t, 660, 0.36, 0.55, 0.88))
write_tone('ignido_siren.wav', lambda t: 0.76 * math.sin(2 * math.pi * (680 + 260 * (0.5 + 0.5 * math.sin(2 * math.pi * 0.55 * t))) * t))
write_tone('ignido_dawn.wav', lambda t: 0.74 * (math.sin(2 * math.pi * 523.25 * t) + 0.45 * math.sin(2 * math.pi * 659.25 * t)) / 1.45 * (0.35 + 0.65 * min(1.0, (t % 1.4) / 0.18)))
write_tone('ignido_deep.wav', lambda t: gated_sine(t, 430 if int(t) % 2 == 0 else 520, 0.90, 0.74, 0.90))

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

    static let options: [IgnidoAlarmSoundOption] = [
        .init(id: systemDefaultID, titleKey: "sound.system", detailKey: "sound.system.detail", fileName: nil),
        .init(id: emberID, titleKey: "sound.ember", detailKey: "sound.ember.detail", fileName: "ignido_ember.wav"),
        .init(id: blazeID, titleKey: "sound.blaze", detailKey: "sound.blaze.detail", fileName: "ignido_blaze.wav"),
        .init(id: pulseID, titleKey: "sound.pulse", detailKey: "sound.pulse.detail", fileName: "ignido_pulse.wav"),
        .init(id: sirenID, titleKey: "sound.siren", detailKey: "sound.siren.detail", fileName: "ignido_siren.wav"),
        .init(id: dawnID, titleKey: "sound.dawn", detailKey: "sound.dawn.detail", fileName: "ignido_dawn.wav"),
        .init(id: deepID, titleKey: "sound.deep", detailKey: "sound.deep.detail", fileName: "ignido_deep.wav")
    ]

    static func normalizedSelection(_ raw: String) -> String {
        if options.contains(where: { $0.id == raw }) { return raw }
        // Existing installs used Japanese/English labels such as デフォルト/default.
        return systemDefaultID
    }

    static func option(for raw: String) -> IgnidoAlarmSoundOption {
        let id = normalizedSelection(raw)
        return options.first(where: { $0.id == id }) ?? options[0]
    }

    static func alertSound(for raw: String) -> AlertConfiguration.AlertSound {
        let option = option(for: raw)
        guard let fileName = option.fileName else { return .default }
        return .named(fileName)
    }

    static func displayName(for raw: String) -> String {
        NSLocalizedString(option(for: raw).titleKey, comment: "alarm sound name")
    }
}

@MainActor
final class IgnidoAlarmSoundPreview: ObservableObject {
    static let shared = IgnidoAlarmSoundPreview()
    private var player: AVAudioPlayer?

    func play(selection raw: String) {
        stop()
        let option = IgnidoAlarmSoundCatalog.option(for: raw)
        guard let fileName = option.fileName,
              let url = Bundle.main.url(forResource: (fileName as NSString).deletingPathExtension,
                                        withExtension: (fileName as NSString).pathExtension) else { return }
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
        } catch {
            player = nil
        }
    }

    func stop() {
        player?.stop()
        player = nil
    }
}
''', encoding='utf-8')

# Route AlarmKit scheduled and 5-second test alarms through the selected sound.
alarm_store = root / 'AlarmStore.swift'
s = alarm_store.read_text(encoding='utf-8')
count_default = s.count('sound: .default')
if count_default < 2:
    raise SystemExit(f'expected at least 2 AlarmStore .default sound entries, found {count_default}')
s = s.replace('sound: .default', 'sound: IgnidoAlarmSoundCatalog.alertSound(for: item.soundName)', 2)
alarm_store.write_text(s, encoding='utf-8')

# Add picker + preview to the alarm editor. Keep the existing media section intact.
alarm_views = root / 'AlarmViews.swift'
s = alarm_views.read_text(encoding='utf-8')
needle = '            Section("音・動画") {\n'
replacement = '''            Section(NSLocalizedString("sound.section", comment: "alarm sound section")) {
                Picker(NSLocalizedString("sound.picker", comment: "alarm sound picker"), selection: $draft.soundName) {
                    ForEach(IgnidoAlarmSoundCatalog.options) { option in
                        Text(NSLocalizedString(option.titleKey, comment: "alarm sound name"))
                            .tag(option.id)
                    }
                }
                .pickerStyle(.navigationLink)

                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(IgnidoAlarmSoundCatalog.displayName(for: draft.soundName))
                            .font(.headline)
                        Text(NSLocalizedString(IgnidoAlarmSoundCatalog.option(for: draft.soundName).detailKey, comment: "alarm sound detail"))
                            .font(.caption)
                            .foregroundStyle(IgnidoTheme.secondaryText)
                    }
                    Spacer()
                    if IgnidoAlarmSoundCatalog.option(for: draft.soundName).fileName != nil {
                        Button(NSLocalizedString("sound.preview", comment: "preview sound")) {
                            IgnidoAlarmSoundPreview.shared.play(selection: draft.soundName)
                        }
                        .buttonStyle(.bordered)
                        .tint(IgnidoTheme.ember)
                    }
                }

                Text(NSLocalizedString("sound.test.help", comment: "alarm sound test help"))
                    .font(.caption)
                    .foregroundStyle(IgnidoTheme.secondaryText)
            }

            Section("音・動画") {
'''
if needle not in s:
    raise SystemExit('AlarmViews sound/media section not found')
s = s.replace(needle, replacement, 1)

# Make existing test button explicitly test the currently selected sound.
s = s.replace('Button("5秒後にシステムアラームをテスト")',
              'Button(NSLocalizedString("sound.test5", comment: "test selected alarm sound"))', 1)
alarm_views.write_text(s, encoding='utf-8')

# Add localized strings for Japanese, English and Korean.
translations = {
    'ja': {
        'sound.section': 'アラーム音',
        'sound.picker': 'サウンド',
        'sound.system': 'システム標準',
        'sound.system.detail': 'iPhoneのAlarmKit標準音',
        'sound.ember': 'IGNIDO Ember',
        'sound.ember.detail': '明るく鋭い点火音',
        'sound.blaze': 'IGNIDO Blaze',
        'sound.blaze.detail': '強めの高音アラーム',
        'sound.pulse': 'IGNIDO Pulse',
        'sound.pulse.detail': '短い連続パルス',
        'sound.siren': 'IGNIDO Siren',
        'sound.siren.detail': '上下するサイレン音',
        'sound.dawn': 'IGNIDO Dawn',
        'sound.dawn.detail': '少し柔らかい起床音',
        'sound.deep': 'IGNIDO Deep',
        'sound.deep.detail': '低めで重いアラーム',
        'sound.preview': '試聴',
        'sound.test.help': 'IGNIDO音はここで試聴できます。実際のAlarmKit動作は下の5秒テストで確認できます。',
        'sound.test5': '選択したアラーム音を5秒後にテスト',
    },
    'en': {
        'sound.section': 'Alarm Sound',
        'sound.picker': 'Sound',
        'sound.system': 'System Default',
        'sound.system.detail': 'Default iPhone AlarmKit sound',
        'sound.ember': 'IGNIDO Ember',
        'sound.ember.detail': 'Bright, sharp ignition tone',
        'sound.blaze': 'IGNIDO Blaze',
        'sound.blaze.detail': 'Stronger high-pitched alarm',
        'sound.pulse': 'IGNIDO Pulse',
        'sound.pulse.detail': 'Short repeating pulses',
        'sound.siren': 'IGNIDO Siren',
        'sound.siren.detail': 'Rising and falling siren',
        'sound.dawn': 'IGNIDO Dawn',
        'sound.dawn.detail': 'Softer wake-up tone',
        'sound.deep': 'IGNIDO Deep',
        'sound.deep.detail': 'Lower, heavier alarm tone',
        'sound.preview': 'Preview',
        'sound.test.help': 'Preview IGNIDO tones here. Use the 5-second test below to verify the actual AlarmKit alert.',
        'sound.test5': 'Test selected alarm sound in 5 seconds',
    },
    'ko': {
        'sound.section': '알람 소리',
        'sound.picker': '사운드',
        'sound.system': '시스템 기본',
        'sound.system.detail': 'iPhone AlarmKit 기본 소리',
        'sound.ember': 'IGNIDO Ember',
        'sound.ember.detail': '밝고 날카로운 점화음',
        'sound.blaze': 'IGNIDO Blaze',
        'sound.blaze.detail': '강한 고음 알람',
        'sound.pulse': 'IGNIDO Pulse',
        'sound.pulse.detail': '짧게 반복되는 펄스',
        'sound.siren': 'IGNIDO Siren',
        'sound.siren.detail': '오르내리는 사이렌',
        'sound.dawn': 'IGNIDO Dawn',
        'sound.dawn.detail': '조금 부드러운 기상음',
        'sound.deep': 'IGNIDO Deep',
        'sound.deep.detail': '낮고 묵직한 알람',
        'sound.preview': '미리 듣기',
        'sound.test.help': 'IGNIDO 소리는 여기서 미리 들을 수 있습니다. 실제 AlarmKit 동작은 아래 5초 테스트로 확인하세요.',
        'sound.test5': '선택한 알람 소리를 5초 후 테스트',
    },
}

for lang, pairs in translations.items():
    p = root / f'{lang}.lproj' / 'Localizable.strings'
    if not p.exists():
        raise SystemExit(f'missing localization file: {p}')
    text = p.read_text(encoding='utf-8')
    text += '\n/* IGNIDO Wake 1.9.0 alarm sounds */\n'
    for key, value in pairs.items():
        escaped = value.replace('\\', '\\\\').replace('"', '\\"')
        text += f'"{key}" = "{escaped}";\n'
    p.write_text(text, encoding='utf-8')

# Static verification.
for name in ['ignido_ember.wav','ignido_blaze.wav','ignido_pulse.wav','ignido_siren.wav','ignido_dawn.wav','ignido_deep.wav']:
    if not (root / name).exists():
        raise SystemExit(f'missing generated sound: {name}')

alarm_text = alarm_store.read_text(encoding='utf-8')
if alarm_text.count('IgnidoAlarmSoundCatalog.alertSound(for: item.soundName)') < 2:
    raise SystemExit('selected alarm sound is not wired to both regular and test AlarmKit schedules')
views_text = alarm_views.read_text(encoding='utf-8')
for token in ['IgnidoAlarmSoundCatalog.options', 'IgnidoAlarmSoundPreview.shared.play', 'sound.test5']:
    if token not in views_text:
        raise SystemExit(f'missing alarm sound UI token: {token}')
if '<string>1.9.0</string>' not in plist.read_text(encoding='utf-8') or '<string>19</string>' not in plist.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist version/build not stamped to 1.9.0/19')

print('IGNIDO Wake iOS 1.9.0 multi-sound alarm picker patch applied')
