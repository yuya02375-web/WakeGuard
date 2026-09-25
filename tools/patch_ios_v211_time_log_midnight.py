from pathlib import Path
p=Path('ios/IGNIDOWake/TimeLogView.swift')
s=p.read_text()
old='''    private var groups: [(Date, [TimeLogEntry])] {
        let cal = Calendar.autoupdatingCurrent
        let dict = Dictionary(grouping: entries) { cal.startOfDay(for: $0.start) }
        return dict.keys.sorted(by: >).map { ($0, dict[$0] ?? []) }
    }
'''
new='''    private var groups: [(Date, [TimeLogEntry])] {
        let cal = Calendar.autoupdatingCurrent
        var days = Set<Date>()
        for entry in entries where entry.end > entry.start {
            var day = cal.startOfDay(for: entry.start)
            let last = cal.startOfDay(for: entry.end.addingTimeInterval(-0.001))
            while day <= last { days.insert(day); day = cal.date(byAdding: .day, value: 1, to: day) ?? day.addingTimeInterval(86400) }
        }
        return days.sorted(by: >).map { day in
            let next = cal.date(byAdding: .day, value: 1, to: day) ?? day.addingTimeInterval(86400)
            return (day, entries.filter { $0.end > day && $0.start < next }.sorted { $0.start > $1.start })
        }
    }
'''
if old not in s: raise SystemExit('iOS grouping block not found')
s=s.replace(old,new).replace('EntryRow(entry: e).onTapGesture','EntryRow(entry: e, day: day).onTapGesture')
old='''private struct EntryRow: View {
    let entry: TimeLogEntry
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) { Text(range).font(.headline).monospacedDigit(); Text(timeLogDuration(entry.end.timeIntervalSince(entry.start))).font(.subheadline).foregroundStyle(IgnidoTheme.flame) }
            Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }.padding(13).background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).stroke(IgnidoTheme.border, lineWidth: 1))
    }
    private var range: String { let a = entry.start.formatted(date: .omitted, time: .shortened), b = entry.end.formatted(date: .omitted, time: .shortened); return Calendar.autoupdatingCurrent.isDate(entry.start, inSameDayAs: entry.end) ? "\\(a) – \\(b)" : "\\(a) – \\(b)  翌日" }
}
'''
new='''private struct EntryRow: View {
    let entry: TimeLogEntry
    let day: Date
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) { Text(range).font(.headline).monospacedDigit(); Text(durationLabel).font(.subheadline).foregroundStyle(IgnidoTheme.flame) }
            Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }.padding(13).background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).stroke(IgnidoTheme.border, lineWidth: 1))
    }
    private var segment: (Date, Date, Date) { let c=Calendar.autoupdatingCurrent; let a=c.startOfDay(for: day); let b=c.date(byAdding:.day,value:1,to:a) ?? a.addingTimeInterval(86400); return (max(entry.start,a),min(entry.end,b),b) }
    private var isSplit: Bool { let s=segment; return entry.start < s.0 || entry.end > s.1 }
    private var range: String { let s=segment; return "\\(time24(s.0)) – \\(abs(s.1.timeIntervalSince(s.2)) < 0.5 ? "24:00" : time24(s.1))" }
    private var durationLabel: String { let s=segment; let d=timeLogDuration(s.1.timeIntervalSince(s.0)); return isSplit ? "この日 \\(d)  ・  記録全体 \\(timeLogDuration(entry.end.timeIntervalSince(entry.start)))" : d }
    private func time24(_ date: Date) -> String { let f=DateFormatter(); f.locale=Locale(identifier:"ja_JP"); f.dateFormat="HH:mm"; return f.string(from:date) }
}
'''
if old not in s: raise SystemExit('iOS row block not found')
s=s.replace(old,new)
p.write_text(s)
plist=Path('ios/IGNIDOWake/Info.plist'); t=plist.read_text().replace('<string>2.1.0</string>','<string>2.1.1</string>').replace('<string>110</string>','<string>111</string>'); plist.write_text(t)
proj=Path('ios/project.yml'); t=proj.read_text().replace('CFBundleShortVersionString: "2.1.0"','CFBundleShortVersionString: "2.1.1"').replace('CFBundleVersion: "110"','CFBundleVersion: "111"'); proj.write_text(t)
print('iOS 2.1.1 midnight time-log fix applied')
