from pathlib import Path
import re
root=Path('ios')

def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='IGNIDOWake/Info.plist'; s=read(p)
s=s.replace('<string>2.2.2</string>','<string>2.3.0</string>',1).replace('<string>122</string>','<string>130</string>',1)
write(p,s)
p='project.yml'; s=read(p)
s=s.replace('CFBundleShortVersionString: "2.2.2"','CFBundleShortVersionString: "2.3.0"',1).replace('CFBundleVersion: "122"','CFBundleVersion: "130"',1)
write(p,s)

p='IGNIDOWake/DesignSystem.swift'; s=read(p)
repls={
'    static let background = Color(red: 0.031, green: 0.027, blue: 0.024)':'    static let background = Color(red: 0.043, green: 0.047, blue: 0.055)',
'    static let background2 = Color(red: 0.051, green: 0.043, blue: 0.035)':'    static let background2 = Color(red: 0.055, green: 0.059, blue: 0.067)',
'    static let surface = Color(red: 0.094, green: 0.076, blue: 0.059)':'    static let surface = Color(red: 0.082, green: 0.086, blue: 0.098)',
'    static let surface2 = Color(red: 0.126, green: 0.098, blue: 0.070)':'    static let surface2 = Color(red: 0.110, green: 0.114, blue: 0.129)',
'    static let border = Color(red: 0.340, green: 0.235, blue: 0.158)':'    static let border = Color(red: 0.180, green: 0.188, blue: 0.208)',
'    static let ember = Color(red: 1.0, green: 0.227, blue: 0.078)':'    static let ember = Color(red: 1.0, green: 0.345, blue: 0.120)',
'    static let flame = Color(red: 1.0, green: 0.478, blue: 0.0)':'    static let flame = Color(red: 1.0, green: 0.414, blue: 0.0)',
'    static let hot = Color(red: 1.0, green: 0.784, blue: 0.341)':'    static let hot = Color(red: 1.0, green: 0.630, blue: 0.300)',
'    static let text = Color(red: 1.0, green: 0.973, blue: 0.941)':'    static let text = Color(red: 0.965, green: 0.965, blue: 0.975)',
'    static let muted = Color(red: 0.815, green: 0.768, blue: 0.724)':'    static let muted = Color(red: 0.650, green: 0.660, blue: 0.700)',
'    static let chrome = Color(red: 0.055, green: 0.045, blue: 0.035)':'    static let chrome = Color(red: 0.047, green: 0.051, blue: 0.059)',
}
for a,b in repls.items():
    if a not in s: raise SystemExit('Design constant missing: '+a)
    s=s.replace(a,b,1)
old='''struct IgnidoScreenBackground: View {
    var body: some View {
        IgnidoTheme.screenGradient
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(IgnidoTheme.ember.opacity(0.22))
                    .frame(height: 1)
            }
            .ignoresSafeArea()
    }
}'''
new='''struct IgnidoScreenBackground: View {
    var body: some View {
        IgnidoTheme.background
            .ignoresSafeArea()
    }
}'''
if old not in s: raise SystemExit('Screen bg missing')
s=s.replace(old,new,1)
old='''        content
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(IgnidoTheme.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(IgnidoTheme.border.opacity(0.78), lineWidth: 0.9)
                    )
            )'''
new='''        content
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(IgnidoTheme.surface)
            )'''
if old not in s: raise SystemExit('Card modifier missing')
s=s.replace(old,new,1)
s=s.replace('let background = UIColor(red: 0.031, green: 0.027, blue: 0.024, alpha: 1)','let background = UIColor(red: 0.043, green: 0.047, blue: 0.055, alpha: 1)',1)
s=s.replace('let chrome = UIColor(red: 0.055, green: 0.045, blue: 0.035, alpha: 0.99)','let chrome = UIColor(red: 0.047, green: 0.051, blue: 0.059, alpha: 1)',1)
s=s.replace('let flame = UIColor(red: 1.0, green: 0.478, blue: 0.0, alpha: 1)','let flame = UIColor(red: 1.0, green: 0.414, blue: 0.0, alpha: 1)',1)
s=s.replace('let muted = UIColor(red: 0.815, green: 0.768, blue: 0.724, alpha: 1)','let muted = UIColor(red: 0.650, green: 0.660, blue: 0.700, alpha: 1)',1)
s=s.replace('nav.shadowColor = UIColor.white.withAlphaComponent(0.08)','nav.shadowColor = .clear',1)
s=s.replace('tab.shadowColor = UIColor.white.withAlphaComponent(0.08)','tab.shadowColor = .clear',1)
write(p,s)

p='IGNIDOWake/AlarmViews.swift'; s=read(p)
s=s.replace('.listRowBackground(Color.clear)\n                            .listRowSeparator(.hidden)', '.listRowBackground(IgnidoTheme.background)\n                            .listRowSeparator(.visible)',1)
s=s.replace('.navigationTitle("IGNIDO Wake")','.navigationTitle("アラーム")',1)
s=s.replace('Image(systemName: "gearshape.fill")','Image(systemName: "gearshape")',1)
old='''        HStack(spacing: 14) {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(alarm.enabled ? IgnidoTheme.emberGradient : LinearGradient(colors: [IgnidoTheme.secondaryText.opacity(0.50)], startPoint: .top, endPoint: .bottom))
                .frame(width: 4)
            VStack(alignment: .leading, spacing: 5) {
                Text(alarm.timeText)
                    .font(.system(size: 42, weight: .light, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(IgnidoTheme.text)
                Text(alarm.displayLabel)
                    .font(.headline)
                    .foregroundStyle(alarm.enabled ? IgnidoTheme.text : IgnidoTheme.secondaryText)
                HStack(spacing: 8) {
                    Text(alarm.repeatText)
                    TimelineView(.periodic(from: .now, by: 30)) { context in
                        if let remaining = alarm.remainingText(at: context.date) {
                            Text(remaining)
                                .foregroundStyle(alarm.enabled ? IgnidoTheme.ember : IgnidoTheme.secondaryText)
                        }
                    }
                    if alarm.mission != .none { Text(alarm.mission.title) }
                    if alarm.preAlertMinutes > 0 { Text(AppText.format("%d分前", alarm.preAlertMinutes)) }
                }
                .font(.caption)
                .foregroundStyle(IgnidoTheme.secondaryText)
            }
            Spacer()
            Toggle("", isOn: Binding(
                get: { alarm.enabled },
                set: { value in Task { await store.setEnabled(alarm, enabled: value) } }
            ))
            .labelsHidden()
            .tint(IgnidoTheme.ember)
        }
        .contentShape(Rectangle())
        .ignidoCard()
        .padding(.vertical, 4)'''
new='''        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(alarm.timeText)
                    .font(.system(size: 40, weight: .regular, design: .default))
                    .monospacedDigit()
                    .foregroundStyle(alarm.enabled ? IgnidoTheme.text : IgnidoTheme.secondaryText)
                HStack(spacing: 8) {
                    Text(alarm.displayLabel)
                        .font(.subheadline.weight(.medium))
                    Text(alarm.repeatText)
                    TimelineView(.periodic(from: .now, by: 30)) { context in
                        if let remaining = alarm.remainingText(at: context.date) {
                            Text(remaining)
                                .foregroundStyle(alarm.enabled ? IgnidoTheme.ember : IgnidoTheme.secondaryText)
                        }
                    }
                }
                .font(.caption)
                .foregroundStyle(IgnidoTheme.secondaryText)
            }
            Spacer(minLength: 8)
            Toggle("", isOn: Binding(
                get: { alarm.enabled },
                set: { value in Task { await store.setEnabled(alarm, enabled: value) } }
            ))
            .labelsHidden()
            .tint(IgnidoTheme.ember)
        }
        .contentShape(Rectangle())
        .padding(.vertical, 8)'''
if old not in s: raise SystemExit('AlarmRow block missing')
s=s.replace(old,new,1)
s=s.replace('Text("AlarmKitを使って消音・集中モード中でもシステムのアラームとして鳴らします。")','Text("システムアラームとして鳴らすために必要です。")',1)
write(p,s)

p='IGNIDOWake/TimeLogView.swift'; s=read(p)
s=s.replace('ContentUnavailableView("フォルダーがありません", systemImage: "folder", description: Text("右上の＋から、勉強・バイト・運動などのフォルダーを作成できます。"))','ContentUnavailableView("フォルダーがありません", systemImage: "folder")',1)
old='''                } header: { Text("フォルダー") } footer: { Text("各フォルダー内で開始・終了時刻を記録すると、1件の時間・日合計・週合計・全期間合計・1日平均を自動計算します。") }'''
new='''                } header: { Text("フォルダー") }'''
if old not in s: raise SystemExit('TimeLog folder footer missing')
s=s.replace(old,new,1)
s=s.replace('ContentUnavailableView("まだ記録がありません", systemImage: "clock.badge.plus", description: Text("上のストップウォッチで計測するか、右上の＋から開始・終了時刻を追加してください。")) .padding(.top, 40)','ContentUnavailableView("まだ記録がありません", systemImage: "clock.badge.plus") .padding(.top, 40)',1)
s=s.replace('HStack { Label("ストップウォッチ計測", systemImage: "stopwatch.fill").font(.headline); Spacer() }','HStack { Label("計測", systemImage: "stopwatch").font(.subheadline.weight(.semibold)); Spacer() }',1)
s=s.replace('.font(.system(size: 38, weight: .semibold, design: .monospaced))','.font(.system(size: 34, weight: .medium, design: .monospaced))',1)
s=s.replace('''        .padding(14)
        .background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(IgnidoTheme.border, lineWidth: 1))''','''        .padding(14)
        .background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 10))''',1)
old='''    var body: some View { VStack(alignment: .leading, spacing: 5) { Text(label).font(.caption).foregroundStyle(.secondary); Text(timeLogDuration(value)).font(.title3.bold()).monospacedDigit().minimumScaleFactor(0.7).lineLimit(1) }.frame(maxWidth: .infinity, alignment: .leading).padding(13).background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 13)).overlay(RoundedRectangle(cornerRadius: 13).stroke(IgnidoTheme.border, lineWidth: 1)) }'''
new='''    var body: some View { VStack(alignment: .leading, spacing: 5) { Text(label).font(.caption).foregroundStyle(.secondary); Text(timeLogDuration(value)).font(.title3.weight(.semibold)).monospacedDigit().minimumScaleFactor(0.7).lineLimit(1) }.frame(maxWidth: .infinity, alignment: .leading).padding(12).background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 10)) }'''
if old not in s: raise SystemExit('StatCard block missing')
s=s.replace(old,new,1)
old='''.buttonStyle(.bordered)
            .tint(.red)
            .accessibilityLabel("時間記録を削除")'''
new='''.buttonStyle(.plain)
            .foregroundStyle(.red)
            .accessibilityLabel("時間記録を削除")'''
if old not in s: raise SystemExit('Delete button style missing')
s=s.replace(old,new,1)
old='''        HStack {
            VStack(alignment: .leading, spacing: 4) { Text(range).font(.headline).monospacedDigit(); Text(durationLabel).font(.subheadline).foregroundStyle(IgnidoTheme.flame) }
            Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }.padding(13).background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).stroke(IgnidoTheme.border, lineWidth: 1))'''
new='''        HStack {
            VStack(alignment: .leading, spacing: 3) { Text(range).font(.headline).monospacedDigit(); Text(durationLabel).font(.subheadline).foregroundStyle(.secondary) }
            Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 2)'''
if old not in s: raise SystemExit('EntryRow style missing')
s=s.replace(old,new,1)
write(p,s)

p='IGNIDOWake/RootView.swift'; s=read(p)
s=s.replace('Label("アラーム", systemImage: "alarm.fill")','Label("アラーム", systemImage: "alarm")',1)
s=s.replace('Label("時計", systemImage: "clock.fill")','Label("時計", systemImage: "clock")',1)
s=s.replace('Label("ストップウォッチ", systemImage: "stopwatch.fill")','Label("ストップウォッチ", systemImage: "stopwatch")',1)
s=s.replace('Label("ストリーク", systemImage: "flame.fill")','Label("ストリーク", systemImage: "flame")',1)
write(p,s)

assert '<string>2.3.0</string>' in read('IGNIDOWake/Info.plist')
assert '<string>130</string>' in read('IGNIDOWake/Info.plist')
assert '.navigationTitle("アラーム")' in read('IGNIDOWake/AlarmViews.swift')
assert '.ignidoCard()' not in read('IGNIDOWake/AlarmViews.swift').split('struct AlarmEditorView')[0]
assert '各フォルダー内で開始・終了時刻' not in read('IGNIDOWake/TimeLogView.swift')
assert '.buttonStyle(.plain)' in read('IGNIDOWake/TimeLogView.swift')
print('iOS 2.3.0 native-polish patch applied')
