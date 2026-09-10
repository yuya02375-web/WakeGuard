from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# v1.9.5 / build 24
plist = root / 'Info.plist'
s = plist.read_text(encoding='utf-8')
s = re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+(</string>)', r'\g<1>1.9.5\g<2>', s, count=1)
s = re.sub(r'(<key>CFBundleVersion</key>\s*<string>)[^<]+(</string>)', r'\g<1>24\g<2>', s, count=1)
plist.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = re.sub(r'CFBundleShortVersionString: "[^"]+"', 'CFBundleShortVersionString: "1.9.5"', s)
s = re.sub(r'CFBundleVersion: "[^"]+"', 'CFBundleVersion: "24"', s)
project.write_text(s, encoding='utf-8')

# v1.9.3 removed automatic preview playback, but the replacement also left the
# visible preview button with an empty action. Rebuild preview ownership so a
# first tap starts the selected sound, a second tap explicitly stops it, and the
# AVAudioSession is always deactivated when preview ends.
catalog = root / 'AlarmSoundCatalog.swift'
s = catalog.read_text(encoding='utf-8')
start = s.find('@MainActor\nfinal class IgnidoAlarmSoundPreview:')
if start < 0:
    raise SystemExit('preview class not found')
s = s[:start] + r'''@MainActor
final class IgnidoAlarmSoundPreview: NSObject, ObservableObject, AVAudioPlayerDelegate {
    static let shared = IgnidoAlarmSoundPreview()

    @Published private(set) var playingSelection: String?
    private var player: AVAudioPlayer?

    var isPlaying: Bool { player?.isPlaying == true }

    func isPlaying(_ selection: String) -> Bool {
        isPlaying && playingSelection == IgnidoAlarmSoundCatalog.normalizedSelection(selection)
    }

    func toggle(selection raw: String) {
        let normalized = IgnidoAlarmSoundCatalog.normalizedSelection(raw)
        if isPlaying(normalized) {
            stop()
        } else {
            play(selection: normalized)
        }
    }

    func play(selection raw: String) {
        stop()
        let normalized = IgnidoAlarmSoundCatalog.normalizedSelection(raw)
        let option = IgnidoAlarmSoundCatalog.option(for: normalized)
        guard let fileName = option.fileName else { return }

        let url: URL?
        if let dir = try? IgnidoAlarmSoundLibrary.soundsDirectory(),
           FileManager.default.fileExists(atPath: dir.appendingPathComponent(fileName).path) {
            url = dir.appendingPathComponent(fileName)
        } else {
            url = Bundle.main.url(
                forResource: (fileName as NSString).deletingPathExtension,
                withExtension: (fileName as NSString).pathExtension
            )
        }
        guard let url else { return }

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.duckOthers])
            try session.setActive(true)
            let p = try AVAudioPlayer(contentsOf: url)
            p.delegate = self
            p.numberOfLoops = 0
            p.volume = 1.0
            p.prepareToPlay()
            guard p.play() else {
                try? session.setActive(false, options: [.notifyOthersOnDeactivation])
                return
            }
            player = p
            playingSelection = normalized
        } catch {
            player = nil
            playingSelection = nil
            try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
        }
    }

    func stop() {
        player?.stop()
        player?.delegate = nil
        player = nil
        playingSelection = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor [weak self] in
            self?.finishPlayback()
        }
    }

    private func finishPlayback() {
        player?.delegate = nil
        player = nil
        playingSelection = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
    }
}
'''
catalog.write_text(s, encoding='utf-8')

views = root / 'AlarmViews.swift'
s = views.read_text(encoding='utf-8')

# Replace the fragile navigationLink-style Picker with an explicit stable
# navigation destination. This keeps the chosen value visible and prevents the
# picker row from becoming an invalid/empty selection after switching sounds.
section_start = s.find('            Section(NSLocalizedString("sound.section", comment: "alarm sound section")) {')
section_end = s.find('\n            Section("音・動画") {', section_start)
if section_start < 0 or section_end < 0:
    raise SystemExit('alarm sound section not found')
new_section = r'''            Section(NSLocalizedString("sound.section", comment: "alarm sound section")) {
                NavigationLink {
                    AlarmSoundSelectionView(selection: $draft.soundName)
                } label: {
                    HStack {
                        Text(NSLocalizedString("sound.picker", comment: "alarm sound picker"))
                        Spacer()
                        Text(IgnidoAlarmSoundCatalog.displayName(for: draft.soundName))
                            .foregroundStyle(IgnidoTheme.secondaryText)
                            .lineLimit(1)
                    }
                }

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
                        SoundPreviewButton(selection: draft.soundName)
                    }
                }

                Text(NSLocalizedString("sound.test.help", comment: "alarm sound test help"))
                    .font(.caption)
                    .foregroundStyle(IgnidoTheme.secondaryText)
            }
'''
s = s[:section_start] + new_section + s[section_end:]

# Ensure changing/importing sound never starts or stops preview implicitly.
# Preview is now controlled only by the explicit preview button.
s = s.replace('                    IgnidoAlarmSoundPreview.shared.play(selection: draft.soundName)\n', '')

# Add stable selection UI. Selecting a row changes the draft but does not close
# the screen; the user can audition multiple sounds and press Back when done.
append = r'''

private struct SoundPreviewButton: View {
    @ObservedObject private var preview = IgnidoAlarmSoundPreview.shared
    let selection: String

    var body: some View {
        let active = preview.isPlaying(selection)
        Button(active ? NSLocalizedString("sound.stopPreview", comment: "stop preview") : NSLocalizedString("sound.preview", comment: "preview sound")) {
            preview.toggle(selection: selection)
        }
        .buttonStyle(.bordered)
        .tint(active ? .red : IgnidoTheme.ember)
    }
}

private struct AlarmSoundSelectionView: View {
    @Binding var selection: String
    @ObservedObject private var preview = IgnidoAlarmSoundPreview.shared

    var body: some View {
        List {
            Section {
                ForEach(IgnidoAlarmSoundCatalog.options) { option in
                    soundRow(id: option.id)
                }
                if IgnidoAlarmSoundCatalog.isCustom(selection) {
                    soundRow(id: selection)
                }
            } footer: {
                Text(NSLocalizedString("sound.selection.help", comment: "sound selection help"))
                    .foregroundStyle(IgnidoTheme.secondaryText)
            }
        }
        .scrollContentBackground(.hidden)
        .background(IgnidoTheme.background)
        .navigationTitle(NSLocalizedString("sound.section", comment: "alarm sound section"))
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear { preview.stop() }
    }

    @ViewBuilder
    private func soundRow(id: String) -> some View {
        let normalized = IgnidoAlarmSoundCatalog.normalizedSelection(id)
        let option = IgnidoAlarmSoundCatalog.option(for: normalized)
        let selected = IgnidoAlarmSoundCatalog.normalizedSelection(selection) == normalized

        HStack(spacing: 12) {
            Button {
                preview.stop()
                selection = normalized
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(selected ? IgnidoTheme.ember : IgnidoTheme.secondaryText)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(IgnidoAlarmSoundCatalog.displayName(for: normalized))
                            .foregroundStyle(IgnidoTheme.text)
                        Text(NSLocalizedString(option.detailKey, comment: "alarm sound detail"))
                            .font(.caption)
                            .foregroundStyle(IgnidoTheme.secondaryText)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if option.fileName != nil {
                Button {
                    if !selected {
                        selection = normalized
                    }
                    preview.toggle(selection: normalized)
                } label: {
                    Image(systemName: preview.isPlaying(normalized) ? "stop.fill" : "play.fill")
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.bordered)
                .tint(preview.isPlaying(normalized) ? .red : IgnidoTheme.ember)
                .accessibilityLabel(preview.isPlaying(normalized) ? NSLocalizedString("sound.stopPreview", comment: "stop preview") : NSLocalizedString("sound.preview", comment: "preview sound"))
            }
        }
        .padding(.vertical, 4)
    }
}
'''
if 'private struct AlarmSoundSelectionView:' not in s:
    s += append
views.write_text(s, encoding='utf-8')

# Add missing localized strings without changing existing ones.
translations = {
    'ja': {
        'sound.stopPreview': '試聴を停止',
        'sound.selection.help': '音を選んでも画面は閉じません。右の再生ボタンで何度でも試聴し、決まったら戻って保存してください。',
    },
    'en': {
        'sound.stopPreview': 'Stop Preview',
        'sound.selection.help': 'Selecting a sound keeps this screen open. Preview any sound with the play button, then go back and save.',
    },
    'ko': {
        'sound.stopPreview': '미리 듣기 중지',
        'sound.selection.help': '소리를 선택해도 화면이 닫히지 않습니다. 재생 버튼으로 여러 번 들어본 뒤 돌아가서 저장하세요.',
    },
}
for lang, pairs in translations.items():
    p = root / f'{lang}.lproj' / 'Localizable.strings'
    text = p.read_text(encoding='utf-8')
    text += '\n/* IGNIDO Wake 1.9.5 sound picker stability */\n'
    for key, value in pairs.items():
        if f'"{key}" =' in text:
            continue
        escaped = value.replace('\\', '\\\\').replace('"', '\\"')
        text += f'"{key}" = "{escaped}";\n'
    p.write_text(text, encoding='utf-8')

# Static invariants.
view_text = views.read_text(encoding='utf-8')
cat_text = catalog.read_text(encoding='utf-8')
checks = [
    ('AlarmViews', 'AlarmSoundSelectionView(selection: $draft.soundName)', view_text),
    ('AlarmViews', 'SoundPreviewButton(selection: draft.soundName)', view_text),
    ('AlarmViews', 'preview.toggle(selection: normalized)', view_text),
    ('AlarmViews', 'selection = normalized', view_text),
    ('Catalog', '@Published private(set) var playingSelection', cat_text),
    ('Catalog', 'p.delegate = self', cat_text),
    ('Catalog', 'audioPlayerDidFinishPlaying', cat_text),
    ('Catalog', 'notifyOthersOnDeactivation', cat_text),
]
for name, token, text in checks:
    if token not in text:
        raise SystemExit(f'missing {name} invariant: {token}')

# The specific v1.9.4 regression: visible preview button with an empty action.
if re.search(r'Button\([^\n]+sound\.preview[^\n]*\)\s*\{\s*\}', view_text, re.S):
    raise SystemExit('empty preview action still present')

if '<string>1.9.5</string>' not in plist.read_text(encoding='utf-8') or '<string>24</string>' not in plist.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist version/build not stamped to 1.9.5/24')
if 'CFBundleShortVersionString: "1.9.5"' not in project.read_text(encoding='utf-8') or 'CFBundleVersion: "24"' not in project.read_text(encoding='utf-8'):
    raise SystemExit('project.yml version/build not stamped to 1.9.5/24')

print('IGNIDO Wake iOS 1.9.5 sound picker/preview stability patch applied')
