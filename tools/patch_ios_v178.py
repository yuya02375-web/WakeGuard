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

# Root navigation: use Android-parity multi timers and streak protection/growth dashboard.
p = Path('ios/IGNIDOWake/RootView.swift')
s = p.read_text(encoding='utf-8')
if '@EnvironmentObject private var multiTimerStore: MultiTimerStore' not in s:
    s=s.replace('@EnvironmentObject private var timerStore: TimerStore\n', '@EnvironmentObject private var timerStore: TimerStore\n    @EnvironmentObject private var multiTimerStore: MultiTimerStore\n',1)
if '@EnvironmentObject private var streakParityStore: StreakParityStore' not in s:
    s=s.replace('@EnvironmentObject private var streakStore: StreakStore\n', '@EnvironmentObject private var streakStore: StreakStore\n    @EnvironmentObject private var streakParityStore: StreakParityStore\n',1)
if '@State private var activeTimer: WakeTimerItem?' not in s:
    s=s.replace('@State private var activeAlarm: WakeAlarm?\n', '@State private var activeAlarm: WakeAlarm?\n    @State private var activeTimer: WakeTimerItem?\n',1)
s=s.replace('            TimerView()\n                .tabItem { Label("タイマー", systemImage: "timer") }','            MultiTimerView()\n                .tabItem { Label("タイマー", systemImage: "timer") }',1)
s=s.replace('            StreakView()\n                .tabItem { Label("ストリーク", systemImage: "flame.fill") }','            StreakParityDashboard()\n                .tabItem { Label("ストリーク", systemImage: "flame.fill") }',1)
needle_cover='''        .fullScreenCover(item: $activeAlarm, onDismiss: { consumeSystemActions() }) { alarm in
            alarmDestination(alarm)
        }
'''
if needle_cover in s and '.fullScreenCover(item: $activeTimer' not in s:
    s=s.replace(needle_cover,needle_cover+'''        .fullScreenCover(item: $activeTimer, onDismiss: { consumeSystemActions() }) { item in
            MultiTimerResultView(item: item) {
                multiTimerStore.finish(item.id)
                activeTimer = nil
            }
        }
''',1)
s=s.replace('        streakStore.recordWake()\n        activeAlarm = nil','        streakStore.recordWake()\n        streakParityStore.recordWake(alarms: alarmStore.alarms)\n        activeAlarm = nil',1)
needle_consume='''    private func consumeSystemActions() {
        if activeAlarm == nil, let id = WakeIntentState.consumeAlarmID(), let alarm = alarmStore.alarm(id: id) {
            activeAlarm = alarm
            return
        }
'''
if needle_consume in s and 'WakeIntentState.consumeTimerID()' not in s:
    s=s.replace(needle_consume,needle_consume+'''        if activeTimer == nil, let timerID = WakeIntentState.consumeTimerID(), let item = multiTimerStore.item(id: timerID) {
            multiTimerStore.finish(timerID)
            activeTimer = item
            return
        }
''',1)
p.write_text(s,encoding='utf-8')
assert 'MultiTimerView()' in s
assert 'StreakParityDashboard()' in s
assert 'WakeIntentState.consumeTimerID()' in s
assert 'streakParityStore.recordWake' in s

print('iOS parity patch applied')
