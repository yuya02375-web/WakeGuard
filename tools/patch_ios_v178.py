from pathlib import Path

# Clock parity.
p = Path('ios/IGNIDOWake/ClockViews.swift')
s = p.read_text(encoding='utf-8')
s = s.replace('AnalogClockFace(', 'IgnidoAnalogClockFace(')
s = s.replace('AnalogStopwatchFace(', 'IgnidoStopwatchFace(')
s = s.replace('AnalogProgressFace(', 'IgnidoProgressFace(')
needle = '    @State private var enlarged: WorldClockItem?\n'
if needle in s and 'showClockSettings' not in s:
    s = s.replace(needle, needle + '    @State private var showClockSettings = false\n', 1)
old_toolbar = '            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showAdd = true } label: { Image(systemName: "plus") } } }'
new_toolbar = '''            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { showClockSettings = true } label: { Image(systemName: "gearshape.fill") }
                    Button { showAdd = true } label: { Image(systemName: "plus") }
                }
            }'''
if old_toolbar in s:
    s = s.replace(old_toolbar, new_toolbar, 1)
elif 'showClockSettings = true' not in s:
    raise SystemExit('WorldClock toolbar insertion point not found')
needle_sheet = '        .sheet(isPresented: $showAdd) { AddTimeZoneView() }\n'
if needle_sheet in s and '.sheet(isPresented: $showClockSettings)' not in s:
    s = s.replace(needle_sheet, needle_sheet + '        .sheet(isPresented: $showClockSettings) { ClockSettingsView() }\n', 1)
p.write_text(s, encoding='utf-8')
assert 'IgnidoAnalogClockFace(' in s
assert 'IgnidoStopwatchFace(' in s
assert 'IgnidoProgressFace(' in s
assert 'showClockSettings = true' in s
assert '.sheet(isPresented: $showClockSettings)' in s

# Alarm editor: preserve and show the actual selected filename instead of a generic label.
p = Path('ios/IGNIDOWake/AlarmViews.swift')
s = p.read_text(encoding='utf-8')
s = s.replace('Text(draft.mediaFileName == nil ? "標準アラーム音" : "カスタムメディア")',
              'Text(draft.mediaFileName == nil ? "標準アラーム音" : (draft.soundName.isEmpty ? "カスタムメディア" : draft.soundName))')
old = 'draft.mediaFileName = try MediaLibrary.importFile(from: url)\n                importError = nil'
new = 'draft.mediaFileName = try MediaLibrary.importFile(from: url)\n                draft.soundName = url.lastPathComponent\n                importError = nil'
if old in s:
    s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
assert 'draft.soundName = url.lastPathComponent' in s
assert 'draft.soundName.isEmpty ? "カスタムメディア" : draft.soundName' in s

print('iOS 1.7.8 parity patch applied')
