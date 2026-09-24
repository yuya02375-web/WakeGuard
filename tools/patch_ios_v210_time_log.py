from pathlib import Path
import shutil

r=Path('ios/IGNIDOWake')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='Info.plist'; s=read(p)
s=s.replace('<string>2.0.3</string>','<string>2.1.0</string>',1).replace('<string>103</string>','<string>110</string>',1)
write(p,s)

py=Path('ios/project.yml'); s=py.read_text()
s=s.replace('CFBundleShortVersionString: "2.0.3"','CFBundleShortVersionString: "2.1.0"').replace('CFBundleVersion: "103"','CFBundleVersion: "110"')
py.write_text(s)

p='ClockViews.swift'; s=read(p)
stopwatch_marker='struct StopwatchView: View {'
idx=s.index(stopwatch_marker)
state_idx=s.index('@State private var fullScreen = false',idx)
s=s[:state_idx]+s[state_idx:].replace('@State private var fullScreen = false','@State private var fullScreen = false\\n    @State private var showTimeLog = false',1)
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

shutil.copy2(Path('tools/v210/TimeLogView.swift'),Path('ios/IGNIDOWake/TimeLogView.swift'))

assert '<string>2.1.0</string>' in read('Info.plist')
assert '<string>110</string>' in read('Info.plist')
assert '時間記録・集計' in read('ClockViews.swift')
assert Path('ios/IGNIDOWake/TimeLogView.swift').exists()
print('iOS 2.1.0 time log folders patch applied')
