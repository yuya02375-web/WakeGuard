from pathlib import Path

root = Path('ios/IGNIDOWake')

# v1.8.4 is applied first. Keep all previous fixes and move to the
# visibility + always-visible direct-input timer screen.
info = root / 'Info.plist'
s = info.read_text(encoding='utf-8')
s = s.replace('<key>CFBundleShortVersionString</key><string>1.8.4</string>',
              '<key>CFBundleShortVersionString</key><string>1.8.5</string>')
s = s.replace('<key>CFBundleVersion</key><string>13</string>',
              '<key>CFBundleVersion</key><string>14</string>')
info.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = s.replace('CFBundleShortVersionString: "1.8.4"', 'CFBundleShortVersionString: "1.8.5"')
s = s.replace('CFBundleVersion: "13"', 'CFBundleVersion: "14"')
project.write_text(s, encoding='utf-8')

root_view = root / 'RootView.swift'
s = root_view.read_text(encoding='utf-8')
s = s.replace('            MultiTimerView()\n                .tabItem { Label("タイマー", systemImage: "timer") }',
              '            MultiTimerViewV185()\n                .tabItem { Label("タイマー", systemImage: "timer") }', 1)
root_view.write_text(s, encoding='utf-8')

screen = (root / 'MultiTimerMainV185.swift').read_text(encoding='utf-8')
required = [
    'struct MultiTimerViewV185: View',
    'Text("タイマー")',
    '.foregroundStyle(Color.white)',
    '.toolbar(.hidden, for: .navigationBar)',
    'sectionTitle("時間を入力")',
    'directField("時", text: $hoursText)',
    'directField("分", text: $minutesText)',
    'directField("秒", text: $secondsText)',
    'Label("開始", systemImage: "play.fill")',
    'Label("追加", systemImage: "plus")',
    'Button {\n                adding = true',
    '詳細を設定して追加',
]
for token in required:
    if token not in screen:
        raise SystemExit(f'missing v1.8.5 timer UI token: {token}')

if 'MultiTimerViewV185()' not in root_view.read_text(encoding='utf-8'):
    raise SystemExit('RootView was not switched to MultiTimerViewV185')
if '<key>CFBundleShortVersionString</key><string>1.8.5</string>' not in info.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist version was not updated to 1.8.5')
if '<key>CFBundleVersion</key><string>14</string>' not in info.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist build was not updated to 14')
if 'CFBundleShortVersionString: "1.8.5"' not in project.read_text(encoding='utf-8'):
    raise SystemExit('project.yml version was not updated to 1.8.5')
if 'CFBundleVersion: "14"' not in project.read_text(encoding='utf-8'):
    raise SystemExit('project.yml build was not updated to 14')

print('IGNIDO Wake iOS v1.8.5 visible-title + inline timer input patch applied')
