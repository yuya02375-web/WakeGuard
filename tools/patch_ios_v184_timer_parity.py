from pathlib import Path

root = Path('ios/IGNIDOWake')

# v1.8.3 is applied first by generate_ios_world_city_catalog.py. Keep that
# localization/sound patch intact, then stamp the timer-parity build as 1.8.4.
info = root / 'Info.plist'
s = info.read_text(encoding='utf-8')
s = s.replace('<key>CFBundleShortVersionString</key><string>1.8.3</string>',
              '<key>CFBundleShortVersionString</key><string>1.8.4</string>')
s = s.replace('<key>CFBundleVersion</key><string>12</string>',
              '<key>CFBundleVersion</key><string>13</string>')
info.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = s.replace('CFBundleShortVersionString: "1.8.3"', 'CFBundleShortVersionString: "1.8.4"')
s = s.replace('CFBundleVersion: "12"', 'CFBundleVersion: "13"')
project.write_text(s, encoding='utf-8')

multi = (root / 'MultiTimer.swift').read_text(encoding='utf-8')
required = [
    'func createAndStart(label: String, duration: TimeInterval, saved: Bool)',
    'private let quickMinutes = [1, 3, 5, 10, 15, 30]',
    'adjustmentButton("+1秒", 1)',
    '230分 → 3:50:00',
    '@AppStorage("ignido.timer.displayMode")',
    '.exclusively(before: TapGesture())',
    'Button("+1分") { Task { await store.addTime(item, seconds: 60) } }',
    'if items[index].saved {',
    'items.remove(at: index)',
]
for token in required:
    if token not in multi:
        raise SystemExit(f'missing iOS timer parity token: {token}')

if '<key>CFBundleShortVersionString</key><string>1.8.4</string>' not in info.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist version was not updated to 1.8.4')
if '<key>CFBundleVersion</key><string>13</string>' not in info.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist build was not updated to 13')
if 'CFBundleShortVersionString: "1.8.4"' not in project.read_text(encoding='utf-8'):
    raise SystemExit('project.yml version was not updated to 1.8.4')
if 'CFBundleVersion: "13"' not in project.read_text(encoding='utf-8'):
    raise SystemExit('project.yml build was not updated to 13')

print('IGNIDO Wake iOS v1.8.4 timer parity patch applied')
