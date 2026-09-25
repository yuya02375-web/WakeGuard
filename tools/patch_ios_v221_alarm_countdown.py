from pathlib import Path
import re

root=Path('ios')
def read(rel): return (root/rel).read_text()
def write(rel,s): (root/rel).write_text(s)

p='IGNIDOWake/Info.plist'; s=read(p)
s=s.replace('<string>2.2.0</string>','<string>2.2.1</string>',1).replace('<string>120</string>','<string>121</string>',1)
write(p,s)
p='project.yml'; s=read(p)
s=s.replace('CFBundleShortVersionString: "2.2.0"','CFBundleShortVersionString: "2.2.1"',1).replace('CFBundleVersion: "120"','CFBundleVersion: "121"',1)
write(p,s)

p='IGNIDOWake/AlarmViews.swift'; s=read(p)
s=s.replace('''    @State private var useRelative: Bool
    @State private var relativeHours: Int
    @State private var relativeMinutes: Int
''','',1)
s=re.sub(r'''\n        let remaining = max\(60, Int\(\(alarm\.fixedDate\?\.timeIntervalSinceNow \?\? 3600\)\.rounded\(\)\)\)\n        _useRelative = State\(initialValue: alarm\.fixedDate != nil && \(alarm\.fixedDate \?\? \.distantPast\) > Date\(\)\)\n        _relativeHours = State\(initialValue: min\(168, remaining / 3600\)\)\n        _relativeMinutes = State\(initialValue: min\(59, \(remaining % 3600\) / 60\)\)''','',s,count=1)
old='''                Picker("設定方法", selection: $useRelative) {
                    Text("時刻指定").tag(false)
                    Text("○時間後").tag(true)
                }.pickerStyle(.segmented)
                if useRelative {
                    Stepper("\\(relativeHours)時間後", value: $relativeHours, in: 0...168)
                    Stepper("＋ \\(relativeMinutes)分", value: $relativeMinutes, in: 0...59)
                    Text(relativePreview).font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
                } else {
                    DatePicker("時刻", selection: $time, displayedComponents: .hourAndMinute)
                        .datePickerStyle(.wheel)
                        .labelsHidden()
                        .frame(maxWidth: .infinity)
                        .tint(IgnidoTheme.ember)
                }'''
new='''                DatePicker("時刻", selection: $time, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .frame(maxWidth: .infinity)
                    .tint(IgnidoTheme.ember)'''
if old not in s: raise SystemExit('iOS relative editor UI block not found')
s=s.replace(old,new,1)
s=s.replace('''                }.disabled(useRelative).opacity(useRelative ? 0.45 : 1)
                if useRelative { Text("○時間後で設定したアラームは1回だけ鳴ります。").font(.caption).foregroundStyle(IgnidoTheme.secondaryText) }''','''                }''',1)
start=s.find('\n    private var relativePreview: String {')
end=s.find('\n    private func save() {',start)
if start<0 or end<0: raise SystemExit('iOS relative preview block not found')
s=s[:start]+s[end:]
start=s.find('    private func save() {')
body_start=s.find('\n        if useRelative {',start)
body_end=s.find('\n        if draft.label.trimmingCharacters',body_start)
if body_start<0 or body_end<0: raise SystemExit('iOS save relative block not found')
replacement='''
        draft.fixedDate = nil
        let c = Calendar.current.dateComponents([.hour,.minute], from: time)
        draft.hour = c.hour ?? 7
        draft.minute = c.minute ?? 0'''
s=s[:body_start]+replacement+s[body_end:]

old='''                HStack(spacing: 8) {
                    Text(alarm.repeatText)
                    if alarm.mission != .none { Text(alarm.mission.title) }
                    if alarm.preAlertMinutes > 0 { Text(AppText.format("%d分前", alarm.preAlertMinutes)) }
                }
                .font(.caption)
                .foregroundStyle(IgnidoTheme.secondaryText)'''
new='''                HStack(spacing: 8) {
                    Text(alarm.repeatText)
                    if alarm.enabled {
                        TimelineView(.periodic(from: .now, by: 30)) { context in
                            if let remaining = alarm.remainingText(at: context.date) {
                                Text(remaining).foregroundStyle(IgnidoTheme.ember)
                            }
                        }
                    }
                    if alarm.mission != .none { Text(alarm.mission.title) }
                    if alarm.preAlertMinutes > 0 { Text(AppText.format("%d分前", alarm.preAlertMinutes)) }
                }
                .font(.caption)
                .foregroundStyle(IgnidoTheme.secondaryText)'''
if old not in s: raise SystemExit('iOS alarm row schedule block not found')
s=s.replace(old,new,1)
write(p,s)

p='IGNIDOWake/AlarmModel.swift'; s=read(p)
old='''    var repeatText: String {
        if weekdays.isEmpty, let fixedDate, fixedDate > Date() {
            let minutes = max(1, Int(ceil(fixedDate.timeIntervalSinceNow / 60)))
            let h = minutes / 60, m = minutes % 60
            if h == 0 { return AppText.format("あと %d分", m) }
            if m == 0 { return AppText.format("あと %d時間", h) }
            return AppText.format("あと %d時間%d分", h, m)
        }
        guard !weekdays.isEmpty else { return AppText.localized("1回") }
        let names = [1:"月", 2:"火", 3:"水", 4:"木", 5:"金", 6:"土", 7:"日"]
        return weekdays.sorted().compactMap { names[$0].map(AppText.localized) }.joined(separator: " ")
    }'''
new='''    var repeatText: String {
        guard !weekdays.isEmpty else { return AppText.localized("1回") }
        let names = [1:"月", 2:"火", 3:"水", 4:"木", 5:"金", 6:"土", 7:"日"]
        return weekdays.sorted().compactMap { names[$0].map(AppText.localized) }.joined(separator: " ")
    }

    func nextFireDate(after now: Date = Date()) -> Date? {
        if weekdays.isEmpty, let fixedDate { return fixedDate > now ? fixedDate : nil }
        let cal = Calendar.autoupdatingCurrent
        if weekdays.isEmpty {
            let start = cal.startOfDay(for: now)
            if let today = cal.date(bySettingHour: hour, minute: minute, second: 0, of: start), today > now { return today }
            guard let tomorrow = cal.date(byAdding: .day, value: 1, to: start) else { return nil }
            return cal.date(bySettingHour: hour, minute: minute, second: 0, of: tomorrow)
        }
        let start = cal.startOfDay(for: now)
        for offset in 0...7 {
            guard let day = cal.date(byAdding: .day, value: offset, to: start) else { continue }
            let appleWeekday = cal.component(.weekday, from: day)
            let userDay = ((appleWeekday + 5) % 7) + 1
            guard weekdays.contains(userDay), let candidate = cal.date(bySettingHour: hour, minute: minute, second: 0, of: day), candidate > now else { continue }
            return candidate
        }
        return nil
    }

    func remainingText(at now: Date = Date()) -> String? {
        guard let next = nextFireDate(after: now) else { return nil }
        let minutes = max(1, Int(ceil(next.timeIntervalSince(now) / 60)))
        let h = minutes / 60, m = minutes % 60
        if h == 0 { return AppText.format("あと %d分", m) }
        if m == 0 { return AppText.format("あと %d時間", h) }
        return AppText.format("あと %d時間%d分", h, m)
    }'''
if old not in s: raise SystemExit('iOS repeatText block not found')
s=s.replace(old,new,1)
write(p,s)

assert '<string>2.2.1</string>' in read('IGNIDOWake/Info.plist') and '<string>121</string>' in read('IGNIDOWake/Info.plist')
assert 'useRelative' not in read('IGNIDOWake/AlarmViews.swift')
assert '○時間後' not in read('IGNIDOWake/AlarmViews.swift')
assert 'TimelineView(.periodic' in read('IGNIDOWake/AlarmViews.swift')
assert 'func remainingText(at now:' in read('IGNIDOWake/AlarmModel.swift')
print('iOS 2.2.1 alarm countdown display patch applied')
