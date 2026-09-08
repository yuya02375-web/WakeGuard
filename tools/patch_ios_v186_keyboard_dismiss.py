from pathlib import Path

root = Path('ios/IGNIDOWake')

# v1.8.5 runs first. Keep all earlier fixes, then stamp v1.8.6.
info = root / 'Info.plist'
s = info.read_text(encoding='utf-8')
s = s.replace('<key>CFBundleShortVersionString</key><string>1.8.5</string>',
              '<key>CFBundleShortVersionString</key><string>1.8.6</string>')
s = s.replace('<key>CFBundleVersion</key><string>14</string>',
              '<key>CFBundleVersion</key><string>15</string>')
info.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = s.replace('CFBundleShortVersionString: "1.8.5"', 'CFBundleShortVersionString: "1.8.6"')
s = s.replace('CFBundleVersion: "14"', 'CFBundleVersion: "15"')
project.write_text(s, encoding='utf-8')

# Switch the main timer screen to the keyboard-safe implementation.
root_view = root / 'RootView.swift'
s = root_view.read_text(encoding='utf-8')
s = s.replace('            MultiTimerViewV185()\n                .tabItem { Label("タイマー", systemImage: "timer") }',
              '            MultiTimerViewV186()\n                .tabItem { Label("タイマー", systemImage: "timer") }', 1)
root_view.write_text(s, encoding='utf-8')

# Detailed add screen also uses numberPad. Add an explicit Done control there too,
# so no timer input path can trap the user behind a number pad without Return.
multi_path = root / 'MultiTimer.swift'
s = multi_path.read_text(encoding='utf-8')
needle = '    @State private var secondsText = "0"\n    private let maximumSeconds = 99 * 3600 + 59 * 60 + 59\n'
replacement = '    @State private var secondsText = "0"\n    @FocusState private var timerInputFocused: Bool\n    private let maximumSeconds = 99 * 3600 + 59 * 60 + 59\n'
if needle in s and '@FocusState private var timerInputFocused' not in s:
    s = s.replace(needle, replacement, 1)

field_needle = '''            TextField("0", text: text)\n                .keyboardType(.numberPad)\n                .multilineTextAlignment(.center)\n'''
field_replacement = '''            TextField("0", text: text)\n                .keyboardType(.numberPad)\n                .focused($timerInputFocused)\n                .multilineTextAlignment(.center)\n'''
if field_needle in s and '.focused($timerInputFocused)' not in s:
    s = s.replace(field_needle, field_replacement, 1)

toolbar_needle = '''            .navigationTitle("タイマーを追加")\n            .navigationBarTitleDisplayMode(.inline)\n            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("キャンセル") { dismiss() } } }\n'''
toolbar_replacement = '''            .navigationTitle("タイマーを追加")\n            .navigationBarTitleDisplayMode(.inline)\n            .scrollDismissesKeyboard(.interactively)\n            .toolbar {\n                ToolbarItem(placement: .cancellationAction) {\n                    Button("キャンセル") {\n                        timerInputFocused = false\n                        dismiss()\n                    }\n                }\n                ToolbarItemGroup(placement: .keyboard) {\n                    Spacer()\n                    Button("完了") { timerInputFocused = false }\n                        .fontWeight(.semibold)\n                }\n            }\n'''
if toolbar_needle in s and 'Button("完了") { timerInputFocused = false }' not in s:
    s = s.replace(toolbar_needle, toolbar_replacement, 1)

multi_path.write_text(s, encoding='utf-8')

screen = (root / 'MultiTimerMainV186.swift').read_text(encoding='utf-8')
required = [
    'struct MultiTimerViewV186: View',
    '@FocusState private var focusedField: InputField?',
    'ToolbarItemGroup(placement: .keyboard)',
    'Button("完了")',
    '.focused($focusedField, equals: field)',
    '.scrollDismissesKeyboard(.interactively)',
    'focusedField = nil',
    'Label("開始", systemImage: "play.fill")',
    'Label("追加", systemImage: "plus")',
]
for token in required:
    if token not in screen:
        raise SystemExit(f'missing v1.8.6 keyboard-dismiss token: {token}')

multi = multi_path.read_text(encoding='utf-8')
for token in [
    '@FocusState private var timerInputFocused: Bool',
    '.focused($timerInputFocused)',
    'Button("完了") { timerInputFocused = false }',
    '.scrollDismissesKeyboard(.interactively)',
]:
    if token not in multi:
        raise SystemExit(f'missing detailed timer keyboard-dismiss token: {token}')

if 'MultiTimerViewV186()' not in root_view.read_text(encoding='utf-8'):
    raise SystemExit('RootView was not switched to MultiTimerViewV186')
if '<key>CFBundleShortVersionString</key><string>1.8.6</string>' not in info.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist version was not updated to 1.8.6')
if '<key>CFBundleVersion</key><string>15</string>' not in info.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist build was not updated to 15')
if 'CFBundleShortVersionString: "1.8.6"' not in project.read_text(encoding='utf-8'):
    raise SystemExit('project.yml version was not updated to 1.8.6')
if 'CFBundleVersion: "15"' not in project.read_text(encoding='utf-8'):
    raise SystemExit('project.yml build was not updated to 15')

print('IGNIDO Wake iOS v1.8.6 number-pad dismissal patch applied')
