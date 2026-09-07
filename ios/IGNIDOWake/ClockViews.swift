import SwiftUI
import Combine
import UniformTypeIdentifiers

struct TimerView: View {
    @EnvironmentObject private var store: TimerStore
    @State private var hours = 0
    @State private var minutes = 5
    @State private var seconds = 0
    @State private var displayMode = 0
    @State private var fullScreen = false
    @State private var importingMedia = false
    @State private var showMedia = false
    @State private var importError: String?
    private let ticker = Timer.publish(every: 0.2, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    Group {
                        if displayMode == 0 {
                            Text(formatDuration(store.remaining))
                                .font(.system(size: 58, weight: .light, design: .rounded)).monospacedDigit()
                        } else {
                            AnalogProgressFace(remaining: store.remaining, total: max(store.originalDuration, store.remaining, 1))
                                .frame(width: 240, height: 240)
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { displayMode = displayMode == 0 ? 1 : 0 }
                    .onLongPressGesture { fullScreen = true }

                    if !store.running && !store.paused {
                        HStack(spacing: 8) {
                            NumberField(title: "時", value: $hours, range: 0...99)
                            NumberField(title: "分", value: $minutes, range: 0...59)
                            NumberField(title: "秒", value: $seconds, range: 0...59)
                        }
                        HStack {
                            ForEach([1,3,5,10], id: \.self) { n in
                                Button("\(n)分") { hours = 0; minutes = n; seconds = 0 }.buttonStyle(.bordered)
                            }
                        }
                    }

                    if store.running, let end = store.endDate {
                        Text("終了予定 \(end.formatted(date: .omitted, time: .shortened))").foregroundStyle(.secondary)
                    }

                    HStack(spacing: 16) {
                        if store.running {
                            Button("一時停止") { store.pause() }.buttonStyle(.borderedProminent)
                        } else if store.paused {
                            Button("再開") { Task { await store.resume() } }.buttonStyle(.borderedProminent)
                        } else {
                            Button("開始") { Task { await start() } }.buttonStyle(.borderedProminent)
                        }
                        Button("リセット") { store.reset() }.buttonStyle(.bordered)
                    }

                    Divider()
                    VStack(alignment: .leading, spacing: 12) {
                        Text("終了時の動画 / 音声").font(.headline)
                        HStack {
                            Text(store.mediaFileName == nil ? "標準アラーム" : "カスタムメディア")
                            Spacer()
                            Button("選択") { importingMedia = true }
                        }
                        if store.mediaFileName != nil {
                            Button("選択を解除", role: .destructive) { store.mediaFileName = nil }
                        }
                        Text("埋め込み字幕トラックがある動画はAVKitの字幕機能で表示します。")
                            .font(.caption).foregroundStyle(.secondary)
                        if let importError { Text(importError).font(.caption).foregroundStyle(.red) }
                        if let error = store.lastError { Text(error).font(.caption).foregroundStyle(.red) }
                    }
                    Spacer(minLength: 30)
                }.padding()
            }
            .navigationTitle("タイマー")
        }
        .fileImporter(isPresented: $importingMedia, allowedContentTypes: [.movie,.audio], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first else { return }
                store.mediaFileName = try MediaLibrary.importFile(from: url); importError = nil
            } catch { importError = error.localizedDescription }
        }
        .fullScreenCover(isPresented: $fullScreen) {
            FullTimerView(displayMode: $displayMode)
        }
        .fullScreenCover(isPresented: $showMedia) {
            if let url = MediaLibrary.url(for: store.mediaFileName) {
                TimerMediaScreen(url: url) { showMedia = false }
            } else {
                VStack { Text("タイマー終了").font(.largeTitle.bold()); Button("停止") { showMedia = false }.buttonStyle(.borderedProminent) }
            }
        }
        .onReceive(ticker) { _ in
            if store.finished {
                store.markFinished()
                showMedia = true
            }
        }
    }

    private func start() async {
        let total = TimeInterval(hours * 3600 + minutes * 60 + seconds)
        await store.start(seconds: total)
    }
}

private struct NumberField: View {
    let title: String
    @Binding var value: Int
    let range: ClosedRange<Int>
    var body: some View {
        VStack {
            TextField("0", value: $value, format: .number).keyboardType(.numberPad).multilineTextAlignment(.center)
                .font(.title2.monospacedDigit()).padding(10).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
            Text(title).font(.caption).foregroundStyle(.secondary)
        }
        .onChange(of: value) { _, newValue in value = min(max(newValue, range.lowerBound), range.upperBound) }
    }
}

private struct FullTimerView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: TimerStore
    @Binding var displayMode: Int
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 30) {
                Group {
                    if displayMode == 0 {
                        TimelineView(.periodic(from: .now, by: 0.1)) { _ in
                            Text(formatDuration(store.remaining)).font(.system(size: 78, weight: .light, design: .rounded)).monospacedDigit().foregroundStyle(.white)
                        }
                    } else {
                        TimelineView(.periodic(from: .now, by: 0.1)) { _ in
                            AnalogProgressFace(remaining: store.remaining, total: max(store.originalDuration, 1)).frame(width: 320, height: 320)
                        }
                    }
                }.onTapGesture { displayMode = displayMode == 0 ? 1 : 0 }
                Button("閉じる") { dismiss() }.buttonStyle(.borderedProminent)
            }
        }
    }
}

struct StopwatchView: View {
    @EnvironmentObject private var store: StopwatchStore
    @State private var displayMode = 0
    @State private var fullScreen = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Group {
                    if displayMode == 0 {
                        TimelineView(.periodic(from: .now, by: 0.02)) { _ in
                            Text(formatStopwatch(store.elapsed)).font(.system(size: 54, weight: .light, design: .rounded)).monospacedDigit()
                        }
                    } else {
                        TimelineView(.periodic(from: .now, by: 0.02)) { _ in
                            AnalogStopwatchFace(elapsed: store.elapsed).frame(width: 250, height: 250)
                        }
                    }
                }
                .contentShape(Rectangle()).onTapGesture { displayMode = displayMode == 0 ? 1 : 0 }.onLongPressGesture { fullScreen = true }

                HStack(spacing: 14) {
                    Button(store.running ? "停止" : (store.accumulated > 0 ? "再開" : "開始")) { store.toggle() }.buttonStyle(.borderedProminent)
                    Button("ラップ") { store.lap() }.buttonStyle(.bordered).disabled(!store.running)
                    Button("リセット") { store.reset() }.buttonStyle(.bordered)
                }

                List {
                    ForEach(store.laps.reversed()) { lap in
                        HStack {
                            Text("ラップ \(lap.index)")
                            Spacer()
                            Text(formatStopwatch(lap.lap)).monospacedDigit()
                            Text(formatStopwatch(lap.total)).monospacedDigit().foregroundStyle(.secondary)
                        }
                    }
                }.listStyle(.plain)
            }
            .padding(.top, 24)
            .navigationTitle("ストップウォッチ")
        }
        .fullScreenCover(isPresented: $fullScreen) { FullStopwatchView(displayMode: $displayMode) }
    }
}

private struct FullStopwatchView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: StopwatchStore
    @Binding var displayMode: Int
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 30) {
                Group {
                    if displayMode == 0 {
                        TimelineView(.periodic(from: .now, by: 0.02)) { _ in Text(formatStopwatch(store.elapsed)).font(.system(size: 76, weight: .light, design: .rounded)).monospacedDigit().foregroundStyle(.white) }
                    } else {
                        TimelineView(.periodic(from: .now, by: 0.02)) { _ in AnalogStopwatchFace(elapsed: store.elapsed).frame(width: 330, height: 330) }
                    }
                }.onTapGesture { displayMode = displayMode == 0 ? 1 : 0 }
                Button("閉じる") { dismiss() }.buttonStyle(.borderedProminent)
            }
        }
    }
}

struct WorldClockView: View {
    @EnvironmentObject private var store: WorldClockStore
    @State private var showAdd = false
    @State private var enlarged: WorldClockItem?

    var body: some View {
        NavigationStack {
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
            .navigationTitle("世界時計")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showAdd = true } label: { Image(systemName: "plus") } } }
        }
        .sheet(isPresented: $showAdd) { AddTimeZoneView() }
        .fullScreenCover(item: $enlarged) { item in FullWorldClockView(item: item) }
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
                    AnalogClockFace(date: context.date, timeZone: TimeZone(identifier: item.timeZoneIdentifier) ?? .current).frame(width: 70, height: 70)
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
        return "\(f.string(from: date))  UTC\(h >= 0 ? "+" : "")\(h):\(String(format: "%02d", m))  現在地\(diff >= 0 ? "+" : "")\(String(format: "%.1f", diff))h\(dst)"
    }
}

private struct AddTimeZoneView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WorldClockStore
    @State private var query = ""
    var filtered: [String] {
        query.isEmpty ? TimeZone.knownTimeZoneIdentifiers : TimeZone.knownTimeZoneIdentifiers.filter { $0.localizedCaseInsensitiveContains(query) || $0.replacingOccurrences(of: "_", with: " ").localizedCaseInsensitiveContains(query) }
    }
    var body: some View {
        NavigationStack {
            List(filtered.prefix(300), id: \.self) { id in
                Button { store.add(id); dismiss() } label: { HStack { Text(id.split(separator: "/").last.map(String.init)?.replacingOccurrences(of: "_", with: " ") ?? id); Spacer(); Text(id).font(.caption).foregroundStyle(.secondary) } }
            }
            .searchable(text: $query, prompt: "都市 / タイムゾーン")
            .navigationTitle("都市を追加")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("閉じる") { dismiss() } } }
        }
    }
}

private struct FullWorldClockView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WorldClockStore
    let item: WorldClockItem
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            TimelineView(.periodic(from: .now, by: 1)) { context in
                VStack(spacing: 30) {
                    Text(item.displayName).font(.largeTitle.bold()).foregroundStyle(.white)
                    AnalogClockFace(date: context.date, timeZone: TimeZone(identifier: item.timeZoneIdentifier) ?? .current).frame(width: 310, height: 310)
                    Text(clockTime(context.date)).font(.system(size: 58, weight: .light, design: .rounded)).monospacedDigit().foregroundStyle(.white)
                    Button("閉じる") { dismiss() }.buttonStyle(.borderedProminent)
                }
            }
        }
    }
    private func clockTime(_ date: Date) -> String { let f = DateFormatter(); f.timeZone = TimeZone(identifier: item.timeZoneIdentifier); f.dateFormat = store.use24Hour ? "HH:mm:ss" : "h:mm:ss a"; return f.string(from: date) }
}

struct AnalogClockFace: View {
    let date: Date
    let timeZone: TimeZone
    var body: some View {
        GeometryReader { geo in
            let s = geo.size.width
            let cal = Calendar(identifier: .gregorian)
            let comps = cal.dateComponents(in: timeZone, from: date)
            let sec = Double(comps.second ?? 0); let min = Double(comps.minute ?? 0) + sec/60; let hour = Double(comps.hour ?? 0) + min/60
            ZStack {
                Circle().fill(.thinMaterial).overlay(Circle().stroke(Color.secondary.opacity(0.35), lineWidth: 1))
                ForEach(0..<12, id: \.self) { i in Capsule().fill(i % 3 == 0 ? Color.primary : Color.secondary).frame(width: i % 3 == 0 ? 3 : 2, height: i % 3 == 0 ? s*0.08 : s*0.05).offset(y: -s*0.41).rotationEffect(.degrees(Double(i)*30)) }
                hand(length: s*0.23, width: max(2,s*0.035), angle: hour*30)
                hand(length: s*0.33, width: max(1.5,s*0.025), angle: min*6)
                hand(length: s*0.37, width: max(1,s*0.012), angle: sec*6).foregroundStyle(.red)
                Circle().fill(.red).frame(width: s*0.06, height: s*0.06)
            }
        }.aspectRatio(1, contentMode: .fit)
    }
    private func hand(length: CGFloat, width: CGFloat, angle: Double) -> some View { Capsule().frame(width: width, height: length).offset(y: -length/2).rotationEffect(.degrees(angle)) }
}

struct AnalogStopwatchFace: View {
    let elapsed: TimeInterval
    var body: some View {
        ZStack {
            Circle().fill(.thinMaterial).overlay(Circle().stroke(Color.secondary.opacity(0.35)))
            ForEach(0..<60, id: \.self) { i in Capsule().fill(i % 5 == 0 ? Color.primary : Color.secondary.opacity(0.6)).frame(width: i % 5 == 0 ? 3 : 1, height: i % 5 == 0 ? 14 : 7).offset(y: -108).rotationEffect(.degrees(Double(i)*6)) }
            Capsule().fill(.red).frame(width: 3, height: 92).offset(y: -46).rotationEffect(.degrees((elapsed.truncatingRemainder(dividingBy: 60))*6))
            Circle().fill(.red).frame(width: 12, height: 12)
        }
    }
}

struct AnalogProgressFace: View {
    let remaining: TimeInterval
    let total: TimeInterval
    var body: some View {
        ZStack {
            Circle().fill(.thinMaterial).overlay(Circle().stroke(Color.secondary.opacity(0.35)))
            Circle().trim(from: 0, to: CGFloat(min(max(remaining / max(total,1),0),1))).stroke(Color.red, style: StrokeStyle(lineWidth: 10, lineCap: .round)).rotationEffect(.degrees(-90))
            Capsule().fill(.red).frame(width: 3, height: 86).offset(y: -43).rotationEffect(.degrees((1 - min(max(remaining / max(total,1),0),1))*360))
            Circle().fill(.red).frame(width: 12, height: 12)
        }.padding(8)
    }
}

func formatDuration(_ t: TimeInterval) -> String {
    let value = max(0, Int(t.rounded(.up))); let h = value / 3600, m = (value % 3600) / 60, s = value % 60
    return h > 0 ? String(format: "%02d:%02d:%02d", h,m,s) : String(format: "%02d:%02d", m,s)
}

func formatStopwatch(_ t: TimeInterval) -> String {
    let cs = Int(max(0,t) * 100) % 100, sec = Int(max(0,t)) % 60, min = (Int(max(0,t)) / 60) % 60, hour = Int(max(0,t)) / 3600
    return hour > 0 ? String(format: "%02d:%02d:%02d.%02d", hour,min,sec,cs) : String(format: "%02d:%02d.%02d", min,sec,cs)
}
