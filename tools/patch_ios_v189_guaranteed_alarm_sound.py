from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# Version 1.8.9 / build 18.
plist = root / 'Info.plist'
s = plist.read_text(encoding='utf-8')
s = re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+(</string>)', r'\g<1>1.8.9\g<2>', s, count=1)
s = re.sub(r'(<key>CFBundleVersion</key>\s*<string>)[^<]+(</string>)', r'\g<1>18\g<2>', s, count=1)
plist.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = re.sub(r'CFBundleShortVersionString: "[^"]+"', 'CFBundleShortVersionString: "1.8.9"', s)
s = re.sub(r'CFBundleVersion: "[^"]+"', 'CFBundleVersion: "18"', s)
project.write_text(s, encoding='utf-8')

# AlarmKit custom AlertSound.named is unreliable on iOS 26.x on real devices.
# The system default AlarmKit sound is the supported fallback and is designed
# to alert through Silent Mode / Focus once AlarmKit authorization is granted.
# Use .default for the actual system alarm and test alarm so a missing/broken
# custom sound can never make an alarm silent.
alarm_store = root / 'AlarmStore.swift'
s = alarm_store.read_text(encoding='utf-8')
old_schedule = '''            let soundName = AlarmRuntime.alarmKitSoundName(for: item.mediaFileName)
            let configuration = AlarmManager.AlarmConfiguration.alarm(
                schedule: schedule,
                attributes: attributes,
                stopIntent: StopWakeIntent(alarmID: item.id.uuidString),
                secondaryIntent: secondaryIntent,
                sound: .named(soundName)
            )'''
new_schedule = '''            let configuration = AlarmManager.AlarmConfiguration.alarm(
                schedule: schedule,
                attributes: attributes,
                stopIntent: StopWakeIntent(alarmID: item.id.uuidString),
                secondaryIntent: secondaryIntent,
                sound: .default
            )'''
if old_schedule not in s:
    raise SystemExit('AlarmStore regular schedule sound block not found')
s = s.replace(old_schedule, new_schedule, 1)

old_test = '''            let soundName = AlarmRuntime.alarmKitSoundName(for: item.mediaFileName)
            let configuration = AlarmManager.AlarmConfiguration.alarm(
                schedule: schedule,
                attributes: attributes,
                stopIntent: nil,
                secondaryIntent: secondaryIntent,
                sound: .named(soundName)
            )'''
new_test = '''            let configuration = AlarmManager.AlarmConfiguration.alarm(
                schedule: schedule,
                attributes: attributes,
                stopIntent: nil,
                secondaryIntent: secondaryIntent,
                sound: .default
            )'''
if old_test not in s:
    raise SystemExit('AlarmStore test schedule sound block not found')
s = s.replace(old_test, new_test, 1)
alarm_store.write_text(s, encoding='utf-8')

# Apply the same guaranteed-sound policy to timers, since they use the same
# AlarmKit AlertSound mechanism and would otherwise fail in the same way.
timer = root / 'MultiTimer.swift'
s = timer.read_text(encoding='utf-8')
old_timer = '''            let soundName = AlarmRuntime.alarmKitSoundName(for: item.mediaFileName)
            let configuration = AlarmManager.AlarmConfiguration.timer(
                duration: seconds,
                attributes: attributes,
                stopIntent: nil,
                secondaryIntent: OpenTimerIntent(timerID: item.id.uuidString),
                sound: .named(soundName)
            )'''
new_timer = '''            let configuration = AlarmManager.AlarmConfiguration.timer(
                duration: seconds,
                attributes: attributes,
                stopIntent: nil,
                secondaryIntent: OpenTimerIntent(timerID: item.id.uuidString),
                sound: .default
            )'''
if old_timer not in s:
    raise SystemExit('MultiTimer AlarmKit sound block not found')
s = s.replace(old_timer, new_timer, 1)
timer.write_text(s, encoding='utf-8')

# Make the editor explicit: selected files remain usable for in-app media
# playback/tests, but the system wake-up alert itself uses AlarmKit's reliable
# system sound so the alarm cannot become silent because a custom AlertSound fails.
alarm_views = root / 'AlarmViews.swift'
s = alarm_views.read_text(encoding='utf-8')
needle = '''                if let importError {
                    Text(importError)
                        .foregroundStyle(Color(red: 1.0, green: 0.50, blue: 0.43))
                        .font(.caption)
                }
'''
replacement = '''                Text("システムアラーム本体は確実に鳴るAlarmKit標準音を使用します。選択した音声 / 動画はアプリ内メディア用です。")
                    .font(.caption)
                    .foregroundStyle(IgnidoTheme.secondaryText)
                if let importError {
                    Text(importError)
                        .foregroundStyle(Color(red: 1.0, green: 0.50, blue: 0.43))
                        .font(.caption)
                }
'''
if needle not in s:
    raise SystemExit('AlarmViews media section insertion point not found')
s = s.replace(needle, replacement, 1)
alarm_views.write_text(s, encoding='utf-8')

# Static validation.
for path in [alarm_store, timer]:
    text = path.read_text(encoding='utf-8')
    if 'sound: .named(soundName)' in text:
        raise SystemExit(f'custom AlarmKit sound still active in {path}')

alarm_text = alarm_store.read_text(encoding='utf-8')
if alarm_text.count('sound: .default') < 2:
    raise SystemExit('AlarmStore does not contain default sound for both normal and test alarms')
if 'sound: .default' not in timer.read_text(encoding='utf-8'):
    raise SystemExit('MultiTimer does not contain default AlarmKit sound')
if '<string>1.8.9</string>' not in plist.read_text(encoding='utf-8') or '<string>18</string>' not in plist.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist version/build not stamped to 1.8.9/18')

print('IGNIDO Wake iOS 1.8.9 guaranteed AlarmKit default sound patch applied')
