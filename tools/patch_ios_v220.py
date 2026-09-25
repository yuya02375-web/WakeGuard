from pathlib import Path
import shutil

r=Path('ios/IGNIDOWake')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='Info.plist'; s=read(p)
s=s.replace('<string>2.0.3</string>','<string>2.2.0</string>',1).replace('<string>103</string>','<string>120</string>',1)
write(p,s)

py=Path('ios/project.yml'); s=py.read_text()
s=s.replace('CFBundleShortVersionString: "2.0.3"','CFBundleShortVersionString: "2.2.0"').replace('CFBundleVersion: "103"','CFBundleVersion: "120"')
py.write_text(s)

p='ClockViews.swift'; s=read(p)
stopwatch_marker='struct StopwatchView: View {'
idx=s.index(stopwatch_marker)
state_idx=s.index('@State private var fullScreen = false',idx)
s=s[:state_idx]+s[state_idx:].replace('@State private var fullScreen = false','@State private var fullScreen = false\n    @State private var showTimeLog = false',1)
needle='''                HStack(spacing: 14) {
                    Button(store.running ? "停止" : (store.accumulated > 0 ? "再開" : "開始")) { store.toggle() }.buttonStyle(.borderedProminent)
                    Button("ラップ") { store.lap() }.buttonStyle(.bordered).disabled(!store.running)
                    Button("リセット") { store.reset() }.buttonStyle(.bordered)
                }

                List {'''
replacement='''                HStack(spacing: 14) {
                    Button(store.running ? "停止" : (store.accumulated > 0 ? "再開" : "開始")) { store.toggle() }.buttonStyle(.borderedProminent)
                    Button("ラップ") { store.lap() }.buttonStyle(.bordered).disabled(!store.running)
                    Button("リセット") { store.reset() }.buttonStyle(.bordered)
                }

                Button { showTimeLog = true } label: {
                    Label("時間記録・集計", systemImage: "folder.badge.clock")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                List {'''
if needle not in s: raise SystemExit('Stopwatch button insertion point not found')
s=s.replace(needle,replacement,1)
s=s.replace('.fullScreenCover(isPresented: $fullScreen) { FullStopwatchView(displayMode: $displayMode) }',
            '.fullScreenCover(isPresented: $fullScreen) { FullStopwatchView(displayMode: $displayMode) }\n        .fullScreenCover(isPresented: $showTimeLog) { TimeLogView() }',1)
write(p,s)

shutil.copy2(Path('tools/v220/TimeLogView.swift'),Path('ios/IGNIDOWake/TimeLogView.swift'))

p='AlarmViews.swift'; s=read(p)
s=s.replace('''    @State private var showMediaTest = false
    @State private var importError: String?
''','''    @State private var showMediaTest = false
    @State private var showAfterPicker = false
    @State private var importError: String?
''',1)
s=s.replace('''                TextField("名前", text: $draft.label)
            }

            Section("繰り返し") {''','''                TextField("名前", text: $draft.label)
            }

            Section("○時間後に設定") {
                Button { showAfterPicker = true } label: {
                    HStack { Label("今から○時間○分後", systemImage: "timer"); Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(.secondary) }
                }
                Text("1分〜23時間59分後まで。設定すると1回だけ鳴るアラームになります。")
                    .font(.caption).foregroundStyle(.secondary)
            }

            Section("繰り返し") {''',1)
s=s.replace('''.onDisappear { IgnidoAlarmSoundPreview.shared.stop() }
        .fullScreenCover(isPresented: $showMissionTest) {''','''.onDisappear { IgnidoAlarmSoundPreview.shared.stop() }
        .sheet(isPresented: $showAfterPicker) {
            RelativeAlarmAfterSheet { minutes in
                let target = Date().addingTimeInterval(TimeInterval(minutes * 60))
                time = target
                draft.weekdays = []
                showAfterPicker = false
            }
        }
        .fullScreenCover(isPresented: $showMissionTest) {''',1)
marker='private struct SoundPreviewButton: View {'
sheet='''private struct RelativeAlarmAfterSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var hours = 1
    @State private var minutes = 0
    let onSet: (Int) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("プリセット") {
                    HStack {
                        Button("15分") { onSet(15); dismiss() }.buttonStyle(.bordered)
                        Button("30分") { onSet(30); dismiss() }.buttonStyle(.bordered)
                        Button("1時間") { onSet(60); dismiss() }.buttonStyle(.bordered)
                        Button("2時間") { onSet(120); dismiss() }.buttonStyle(.bordered)
                    }
                    HStack {
                        Button("3時間") { onSet(180); dismiss() }.buttonStyle(.bordered)
                        Button("6時間") { onSet(360); dismiss() }.buttonStyle(.bordered)
                        Button("12時間") { onSet(720); dismiss() }.buttonStyle(.bordered)
                    }
                }
                Section("カスタム") {
                    Stepper("\(hours)時間", value: $hours, in: 0...23)
                    Stepper("\(minutes)分", value: $minutes, in: 0...59)
                    let total = hours * 60 + minutes
                    if total > 0 {
                        Text("今から \(hours > 0 ? "\(hours)時間" : "")\(minutes > 0 ? "\(minutes)分" : "") 後")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("○時間後に設定")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("キャンセル") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("設定") { let total = hours * 60 + minutes; guard total > 0 else { return }; onSet(total); dismiss() }
                        .disabled(hours == 0 && minutes == 0)
                }
            }
        }
    }
}

'''
if marker not in s: raise SystemExit('relative sheet insertion point not found')
s=s.replace(marker,sheet+marker,1)
write(p,s)

assert '<string>2.2.0</string>' in read('Info.plist')
assert '<string>120</string>' in read('Info.plist')
assert '時間記録・集計' in read('ClockViews.swift')
assert 'ストップウォッチで記録' in read('TimeLogView.swift')
assert '○時間後に設定' in read('AlarmViews.swift')
print('iOS 2.2.0 patch applied')
