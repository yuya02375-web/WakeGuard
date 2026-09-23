from pathlib import Path

r=Path('ios/IGNIDOWake')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='Info.plist';s=read(p);s=s.replace('<string>2.0.0</string>','<string>2.0.1</string>',1).replace('<string>100</string>','<string>101</string>',1);write(p,s)
py=Path('ios/project.yml');s=py.read_text().replace('CFBundleShortVersionString: "2.0.0"','CFBundleShortVersionString: "2.0.1"').replace('CFBundleVersion: "100"','CFBundleVersion: "101"');py.write_text(s)

p='ClockViews.swift';s=read(p)
start=s.index('struct WorldClockView: View {')
end=s.index('private struct AddTimeZoneView: View {',start)
replacement=r'''struct WorldClockView: View {
    @EnvironmentObject private var store: WorldClockStore
    @State private var showAdd = false
    @State private var enlarged: WorldClockItem?
    @State private var showClockSettings = false
    @AppStorage("ignido.worldclock.overview.v201") private var overviewMode = true

    var body: some View {
        NavigationStack {
            Group {
                if overviewMode {
                    WorldClockOverviewGrid(enlarged: $enlarged)
                } else {
                    List {
                        Section {
                            Picker("表示", selection: $store.displayMode) {
                                Text("デジタル").tag(0); Text("アナログ").tag(1); Text("両方").tag(2)
                            }.pickerStyle(.segmented)
                            Toggle("24時間表示", isOn: $store.use24Hour)
                        }
                        Section {
                            ForEach(store.items) { item in
                                WorldClockRow(item: item)
                                    .contentShape(Rectangle())
                                    .onTapGesture { store.displayMode = (store.displayMode + 1) % 3 }
                                    .onLongPressGesture { enlarged = item }
                            }.onDelete(perform: store.remove)
                        }
                    }
                }
            }
            .navigationTitle("世界時計")
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button {
                        overviewMode.toggle()
                    } label: {
                        Image(systemName: overviewMode ? "list.bullet" : "rectangle.grid.2x2.fill")
                    }
                    .accessibilityLabel(overviewMode ? "詳細表示に切り替え" : "一覧表示に切り替え")
                    Button { showClockSettings = true } label: { Image(systemName: "gearshape.fill") }
                    Button { showAdd = true } label: { Image(systemName: "plus") }
                }
            }
        }
        .sheet(isPresented: $showAdd) { AddTimeZoneView() }
        .sheet(isPresented: $showClockSettings) { ClockSettingsView() }
        .fullScreenCover(item: $enlarged) { item in FullWorldClockView(item: item) }
    }
}

private struct WorldClockOverviewGrid: View {
    @EnvironmentObject private var store: WorldClockStore
    @Binding var enlarged: WorldClockItem?

    var body: some View {
        GeometryReader { geo in
            let count = max(store.items.count, 1)
            let columns = columnCount(for: count)
            let rows = max(1, Int(ceil(Double(count) / Double(columns))))
            let spacing: CGFloat = rows >= 6 ? 4 : 7
            let horizontalPadding: CGFloat = columns >= 4 ? 6 : 10
            let verticalPadding: CGFloat = rows >= 6 ? 5 : 9
            let availableHeight = max(1, geo.size.height - verticalPadding * 2 - spacing * CGFloat(max(0, rows - 1)))
            let cellHeight = max(24, availableHeight / CGFloat(rows))
            let gridColumns = Array(repeating: GridItem(.flexible(minimum: 1), spacing: spacing), count: columns)

            LazyVGrid(columns: gridColumns, alignment: .center, spacing: spacing) {
                ForEach(store.items) { item in
                    WorldClockOverviewCell(item: item, columns: columns, cellHeight: cellHeight)
                        .frame(height: cellHeight)
                        .contentShape(Rectangle())
                        .onTapGesture { enlarged = item }
                }
            }
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, verticalPadding)
            .frame(width: geo.size.width, height: geo.size.height, alignment: .top)
        }
        .background(IgnidoTheme.background)
    }

    private func columnCount(for count: Int) -> Int {
        if count <= 4 { return 2 }
        if count <= 9 { return 3 }
        if count <= 16 { return 4 }
        return 5
    }
}

private struct WorldClockOverviewCell: View {
    @EnvironmentObject private var store: WorldClockStore
    let item: WorldClockItem
    let columns: Int
    let cellHeight: CGFloat

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            VStack(spacing: max(1, min(5, cellHeight * 0.05))) {
                Text(item.displayName)
                    .font(.system(size: cityFontSize, weight: .semibold, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.55)
                Text(time(context.date))
                    .font(.system(size: timeFontSize, weight: .medium, design: .rounded))
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.48)
                Text(dayLabel(context.date))
                    .font(.system(size: detailFontSize, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.horizontal, columns >= 4 ? 3 : 6)
            .padding(.vertical, 2)
            .background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: columns >= 4 ? 10 : 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: columns >= 4 ? 10 : 14, style: .continuous).stroke(IgnidoTheme.border.opacity(0.75), lineWidth: 1))
        }
    }

    private var cityFontSize: CGFloat { min(columns <= 2 ? 16 : columns == 3 ? 14 : 12, max(8, cellHeight * 0.18)) }
    private var timeFontSize: CGFloat { min(columns <= 2 ? 31 : columns == 3 ? 25 : columns == 4 ? 21 : 18, max(10, cellHeight * 0.34)) }
    private var detailFontSize: CGFloat { min(columns <= 2 ? 11 : 10, max(7, cellHeight * 0.13)) }

    private func time(_ date: Date) -> String {
        let f = DateFormatter(); f.timeZone = TimeZone(identifier: item.timeZoneIdentifier); f.dateFormat = store.use24Hour ? "HH:mm" : "h:mm a"; return f.string(from: date)
    }

    private func dayLabel(_ date: Date) -> String {
        let zone = TimeZone(identifier: item.timeZoneIdentifier) ?? .current
        let delta = TimeInterval(zone.secondsFromGMT(for: date) - TimeZone.current.secondsFromGMT(for: date))
        let shifted = date.addingTimeInterval(delta)
        let cal = Calendar.current
        let d = cal.dateComponents([.day], from: cal.startOfDay(for: date), to: cal.startOfDay(for: shifted)).day ?? 0
        let day: String
        if d == 0 { day = AppText.localized("今日") }
        else if d == 1 { day = AppText.localized("明日") }
        else if d == -1 { day = AppText.localized("昨日") }
        else { let f = DateFormatter(); f.timeZone = zone; f.dateFormat = "M/d"; day = f.string(from: date) }
        let offset = zone.secondsFromGMT(for: date); let h = offset / 3600; let m = abs(offset % 3600) / 60
        return "\(day) · UTC\(h >= 0 ? "+" : "")\(h):\(String(format: "%02d", m))"
    }
}

private struct WorldClockRow: View {
    @EnvironmentObject private var store: WorldClockStore
    let item: WorldClockItem
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.displayName).font(.headline)
                    Text(detail(context.date)).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer()
                if store.displayMode == 0 || store.displayMode == 2 {
                    Text(time(context.date)).font(.title2.monospacedDigit())
                }
                if store.displayMode == 1 || store.displayMode == 2 {
                    IgnidoAnalogClockFace(date: context.date, timeZone: TimeZone(identifier: item.timeZoneIdentifier) ?? .current).frame(width: 70, height: 70)
                }
            }.padding(.vertical, 5)
        }
    }
    private func time(_ date: Date) -> String {
        let f = DateFormatter(); f.timeZone = TimeZone(identifier: item.timeZoneIdentifier); f.dateFormat = store.use24Hour ? "HH:mm:ss" : "h:mm:ss a"; return f.string(from: date)
    }
    private func detail(_ date: Date) -> String {
        let zone = TimeZone(identifier: item.timeZoneIdentifier) ?? .current
        let f = DateFormatter(); f.timeZone = zone; f.locale = Locale(identifier: "ja_JP"); f.dateFormat = "M/d(E)"
        let offset = zone.secondsFromGMT(for: date); let h = offset / 3600; let m = abs(offset % 3600) / 60
        let currentOffset = TimeZone.current.secondsFromGMT(for: date)
        let diff = Double(offset - currentOffset) / 3600.0
        let dst = zone.isDaylightSavingTime(for: date) ? " DST" : ""
        return "\(f.string(from: date))  UTC\(h >= 0 ? "+" : "")\(h):\(String(format: "%02d", m))  \(AppText.localized("現在地"))\(diff >= 0 ? "+" : "")\(String(format: "%.1f", diff))h\(dst)"
    }
}

'''
s=s[:start]+replacement+s[end:]
write(p,s)

assert '<string>2.0.1</string>' in read('Info.plist')
assert 'overviewMode = true' in read('ClockViews.swift')
assert 'WorldClockOverviewGrid' in read('ClockViews.swift')
assert 'rectangle.grid.2x2.fill' in read('ClockViews.swift')
print('iOS 2.0.1 one-screen world clock patch applied')
