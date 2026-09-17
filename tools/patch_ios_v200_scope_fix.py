from pathlib import Path

p = Path('ios/IGNIDOWake/AlarmViews.swift')
s = p.read_text()

# The editor UI and media importer live in different SwiftUI types. Keep the
# formatter at file scope so both inserted Stepper labels resolve reliably.
s = s.replace('autoStopText(draft.ringDurationSec)', 'ignidoAutoStopText(draft.ringDurationSec)')
s = s.replace('autoStopText(draft.fullStopDurationSec)', 'ignidoAutoStopText(draft.fullStopDurationSec)')

helper = '''private func ignidoAutoStopText(_ seconds: Int) -> String {
    if seconds <= 0 { return AppText.localized("オフ") }
    if seconds % 60 == 0 { return AppText.format("%d分", seconds / 60) }
    return AppText.format("%d秒", seconds)
}
'''

if 'private func ignidoAutoStopText(' not in s:
    anchor = 'import SwiftUI\n'
    if anchor not in s:
        raise SystemExit('AlarmViews.swift: import SwiftUI anchor missing')
    s = s.replace(anchor, anchor + '\n' + helper + '\n', 1)

p.write_text(s)
print('iOS 2.0.0 AlarmViews scope fix applied')
