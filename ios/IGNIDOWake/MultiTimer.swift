import SwiftUI
import Combine
import AlarmKit
import UniformTypeIdentifiers

struct WakeTimerItem: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var label: String = ""
    var duration: TimeInterval = 300
    var remaining: TimeInterval = 300
    var endDate: Date?
    var running = false
    var saved = true
    var mediaFileName: String?
    var mediaDisplayName: String = ""

    var currentRemaining: TimeInterval {
        running ? max(0, endDate?.timeIntervalSinceNow ?? remaining) : max(0, remaining)
    }
}

@MainActor
final class MultiTimerStore: ObservableObject {
    @Published private(set) var items: [WakeTimerItem] = []
    @Published var lastError: String?
    private let key = "ignido.multiTimers.v1"

    init() { load() }

    @discardableResult
    func add(label: String, duration: TimeInterval, saved: Bool = true) -> WakeTimerItem {
        let d = max(1, duration)
        let item = WakeTimerItem(label: label, duration: d, remaining: d, saved: saved)
        items.append(item)
        save()
        return item
    }

    @discardableResult
    func createAndStart(label: String, duration: TimeInterval, saved: Bool) async -> WakeTimerItem {
        let item = add(label: label, duration: duration, saved: saved)
        await start(item)
        return item
    }

    func update(_ item: WakeTimerItem) {
        if let index = items.firstIndex(where: { $0.id == item.id }) { items[index] = item }
        else { items.append(item) }
        save()
    }

    func delete(_ item: WakeTimerItem) {
        items.removeAll { $0.id == item.id }
        try? AlarmManager.shared.cancel(id: item.id)
        save()
    }

    func item(id: UUID) -> WakeTimerItem? { items.first { $0.id == id } }

    func start(_ item: WakeTimerItem) async {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        let seconds = max(1, items[index].currentRemaining > 0 ? items[index].currentRemaining : items[index].duration)
        items[index].remaining = seconds
        items[index].endDate = Date().addingTimeInterval(seconds)
        items[index].running = true
        save()
        await schedule(items[index], after: seconds)
    }

    func pause(_ item: WakeTimerItem) {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        items[index].remaining = items[index].currentRemaining
        items[index].endDate = nil
        items[index].running = false
        try? AlarmManager.shared.cancel(id: item.id)
        save()
    }

    func reset(_ item: WakeTimerItem) {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        items[index].running = false
        items[index].remaining = items[index].duration
        items[index].endDate = nil
        try? AlarmManager.shared.cancel(id: item.id)
        save()
    }

    func addTime(_ item: WakeTimerItem, seconds delta: TimeInterval) async {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        let oldRemaining = items[index].currentRemaining
        let newRemaining = max(1, oldRemaining + delta)
        items[index].duration = max(1, items[index].duration + delta)
        items[index].remaining = newRemaining
        if items[index].running {
            items[index].endDate = Date().addingTimeInterval(newRemaining)
            save()
            await schedule(items[index], after: newRemaining)
        } else {
            save()
        }
    }

    func finish(_ id: UUID) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        if items[index].saved {
            items[index].running = false
            items[index].remaining = 0
            items[index].endDate = nil
        } else {
            items.remove(at: index)
        }
        save()
    }

    func makeSaved(_ item: WakeTimerItem) {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        items[index].saved = true
        save()
    }

    func refreshFinished() {
        var changed = false
        for i in items.indices where items[i].running && items[i].currentRemaining <= 0.05 {
            items[i].running = false
            items[i].remaining = 0
            items[i].endDate = nil
            changed = true
        }
        if changed { save() }
    }

    private func schedule(_ item: WakeTimerItem, after seconds: TimeInterval) async {
        do {
            if AlarmManager.shared.authorizationState != .authorized { _ = try await AlarmManager.shared.requestAuthorization() }
            try? AlarmManager.shared.cancel(id: item.id)
            let title = item.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "タイマー終了" : item.label
            let alert = AlarmPresentation.Alert(
                title: LocalizedStringResource(stringLiteral: title),
                secondaryButton: AlarmButton(text: "開く", textColor: .white, systemImageName: "arrow.right.circle.fill"),
                secondaryButtonBehavior: .custom
            )
            let countdown = AlarmPresentation.Countdown(title: LocalizedStringResource(stringLiteral: title))
            let attributes = AlarmAttributes<EmptyWakeMetadata>(
                presentation: AlarmPresentation(alert: alert, countdown: countdown, paused: nil),
                metadata: EmptyWakeMetadata(),
                tintColor: IgnidoTheme.ember
            )
            let soundName = AlarmRuntime.alarmKitSoundName(for: item.mediaFileName)
            let configuration = AlarmManager.AlarmConfiguration.timer(
                duration: seconds,
                attributes: attributes,
                stopIntent: nil,
                secondaryIntent: OpenTimerIntent(timerID: item.id.uuidString),
                sound: .named(soundName)
            )
            _ = try await AlarmManager.shared.schedule(id: item.id, configuration: configuration)
            lastError = nil
        } catch { lastError = error.localizedDescription }
    }

    private func save() {
        if let data = try? JSONEncoder().encode(items) { UserDefaults.standard.set(data, forKey: key) }
    }

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode([WakeTimerItem].self, from: data) else { return }
        items = decoded
        refreshFinished()
    }
}

struct MultiTimerView: View {
    @EnvironmentObject private var store: MultiTimerStore
    @State private var adding = false
    @State private var editing: WakeTimerItem?
    @State private var enlarged: WakeTimerItem?
    @AppStorage("ignido.timer.displayMode") private var displayMode = 0
    private let ticker = Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()
    private let quickMinutes = [1, 3, 5, 10, 15, 30]

    var body: some View {
        NavigationStack {
            ZStack {
                IgnidoScreenBackground()
                List {
                    Section("すぐ使う") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach(quickMinutes, id: \.self) { n in
                                    Button("\(n)分") {
                                        Task { _ = await store.createAndStart(label: "", duration: TimeInterval(n * 60), saved: false) }
                                    }
                                    .buttonStyle(.bordered)
                                    .tint(IgnidoTheme.ember)
                                }
                            }
                            .padding(.vertical, 2)
                        }
                        Button {
                            adding = true
                        } label: {
                            Label("時間を入力して開始 / 保存", systemImage: "plus.circle")
                        }
                    }
                    .listRowBackground(Color.clear)

                    Section("タイマー") {
                        if store.items.isEmpty {
                            VStack(spacing: 10) {
                                IgnidoFlameMark().frame(width: 38, height: 52)
                                Text("タイマーなし").font(.headline).foregroundStyle(IgnidoTheme.text)
                                Text("上のプリセットは保存せずすぐ開始できます。")
                                    .font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 22)
                            .listRowBackground(Color.clear)
                        } else {
                            ForEach(store.items) { item in
                                MultiTimerRow(
                                    item: item,
                                    displayMode: $displayMode,
                                    onEdit: { editing = item },
                                    onEnlarge: { enlarged = item }
                                )
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    Button(role: .destructive) { store.delete(item) } label: { Label("削除", systemImage: "trash") }
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("タイマー")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { adding = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $adding) { MultiTimerCreateView() }
            .sheet(item: $editing) { item in
                NavigationStack { MultiTimerEditor(item: item) }
            }
            .fullScreenCover(item: $enlarged) { item in
                MultiTimerExpandedView(timerID: item.id, displayMode: $displayMode)
            }
            .onReceive(ticker) { _ in store.refreshFinished() }
        }
    }
}

private struct MultiTimerRow: View {
    @EnvironmentObject private var store: MultiTimerStore
    let item: WakeTimerItem
    @Binding var displayMode: Int
    let onEdit: () -> Void
    let onEnlarge: () -> Void

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.2)) { _ in
            VStack(spacing: 12) {
                HStack(spacing: 14) {
                    timerDisplay
                    Spacer(minLength: 8)
                    Button(action: onEdit) {
                        Image(systemName: "slider.horizontal.3")
                            .font(.title3)
                    }
                    .buttonStyle(.borderless)
                    .foregroundStyle(IgnidoTheme.secondaryText)
                }

                HStack(spacing: 10) {
                    if item.running {
                        Button { store.pause(item) } label: { Label("一時停止", systemImage: "pause.fill") }
                            .buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                    } else {
                        Button { Task { await store.start(item) } } label: { Label("開始", systemImage: "play.fill") }
                            .buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                    }
                    Button("+1分") { Task { await store.addTime(item, seconds: 60) } }
                        .buttonStyle(.bordered)
                    Button("リセット") { store.reset(item) }
                        .buttonStyle(.bordered)
                    if !item.saved {
                        Button("保存") { store.makeSaved(item) }
                            .buttonStyle(.bordered)
                    }
                }
                .font(.caption)
            }
            .ignidoCard()
            .padding(.vertical, 4)
        }
    }

    @ViewBuilder
    private var timerDisplay: some View {
        VStack(alignment: .leading, spacing: 5) {
            Group {
                if displayMode == 0 {
                    Text(formatDuration(item.currentRemaining))
                        .font(.system(size: 38, weight: .light, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(IgnidoTheme.text)
                } else {
                    IgnidoProgressFace(remaining: item.currentRemaining, total: max(item.duration, 1))
                        .frame(width: 96, height: 96)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                LongPressGesture(minimumDuration: 0.45)
                    .exclusively(before: TapGesture())
                    .onEnded { value in
                        switch value {
                        case .first:
                            onEnlarge()
                        case .second:
                            displayMode = displayMode == 0 ? 1 : 0
                        }
                    }
            )

            Text(item.label.isEmpty ? "タイマー" : item.label)
                .font(.headline).foregroundStyle(IgnidoTheme.text)
            if item.running, let end = item.endDate {
                Text("終了予定 \(end.formatted(date: .omitted, time: .shortened))")
                    .font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
            } else if !item.saved {
                Text("今回だけ").font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
            }
            if !item.mediaDisplayName.isEmpty {
                Text(item.mediaDisplayName).font(.caption).foregroundStyle(IgnidoTheme.secondaryText).lineLimit(1)
            }
        }
    }
}

struct MultiTimerCreateView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: MultiTimerStore
    @State private var label = ""
    @State private var hoursText = "0"
    @State private var minutesText = "5"
    @State private var secondsText = "0"
    private let maximumSeconds = 99 * 3600 + 59 * 60 + 59

    var body: some View {
        NavigationStack {
            Form {
                Section("名前") {
                    TextField("名前（任意）", text: $label)
                }
                Section("時間") {
                    HStack(spacing: 10) {
                        timerInput("時", text: $hoursText)
                        timerInput("分", text: $minutesText)
                        timerInput("秒", text: $secondsText)
                    }
                    Text("分・秒に60以上を入れると自動で時:分:秒へ直します。例：230分 → 3:50:00")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section("調整") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            adjustmentButton("−1分", -60)
                            adjustmentButton("+1秒", 1)
                            adjustmentButton("+10秒", 10)
                            adjustmentButton("+30秒", 30)
                            adjustmentButton("+1分", 60)
                            adjustmentButton("+5分", 300)
                            adjustmentButton("+10分", 600)
                        }
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach([1, 3, 5, 10, 15, 30], id: \.self) { n in
                                Button("\(n)分") { setTotalSeconds(n * 60) }.buttonStyle(.bordered)
                            }
                        }
                    }
                }
                Section {
                    Button("今回だけ開始") { begin(saved: false) }
                        .buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                    Button("保存して開始") { begin(saved: true) }
                        .buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                    Button("保存だけ") {
                        normalizeOverflow()
                        let duration = TimeInterval(totalSeconds)
                        guard duration > 0 else { return }
                        _ = store.add(label: label, duration: duration, saved: true)
                        dismiss()
                    }
                }
            }
            .navigationTitle("タイマーを追加")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("キャンセル") { dismiss() } } }
        }
    }

    private func timerInput(_ title: String, text: Binding<String>) -> some View {
        VStack(spacing: 5) {
            TextField("0", text: text)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .font(.title2.monospacedDigit())
                .onChange(of: text.wrappedValue) { _, _ in normalizeOverflow() }
            Text(title).font(.caption).foregroundStyle(.secondary)
        }
    }

    private func adjustmentButton(_ title: String, _ delta: Int) -> some View {
        Button(title) { setTotalSeconds(max(0, min(maximumSeconds, totalSeconds + delta))) }
            .buttonStyle(.bordered)
    }

    private var totalSeconds: Int {
        let h = max(0, Int(hoursText) ?? 0)
        let m = max(0, Int(minutesText) ?? 0)
        let s = max(0, Int(secondsText) ?? 0)
        return min(maximumSeconds, h * 3600 + m * 60 + s)
    }

    private func normalizeOverflow() {
        let h = max(0, Int(hoursText) ?? 0)
        let m = max(0, Int(minutesText) ?? 0)
        let s = max(0, Int(secondsText) ?? 0)
        if h > 99 || m >= 60 || s >= 60 {
            setTotalSeconds(min(maximumSeconds, h * 3600 + m * 60 + s))
        }
    }

    private func setTotalSeconds(_ value: Int) {
        let v = max(0, min(maximumSeconds, value))
        hoursText = String(v / 3600)
        minutesText = String((v % 3600) / 60)
        secondsText = String(v % 60)
    }

    private func begin(saved: Bool) {
        normalizeOverflow()
        let duration = TimeInterval(totalSeconds)
        guard duration > 0 else { return }
        Task {
            _ = await store.createAndStart(label: label, duration: duration, saved: saved)
            dismiss()
        }
    }
}

struct MultiTimerEditor: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: MultiTimerStore
    @State private var draft: WakeTimerItem
    @State private var importing = false
    @State private var importError: String?
    @AppStorage("ignido.timer.displayMode") private var displayMode = 0
    @State private var enlarged = false

    init(item: WakeTimerItem) { _draft = State(initialValue: item) }

    var body: some View {
        Form {
            Section {
                TextField("名前", text: $draft.label)
                Group {
                    if displayMode == 0 {
                        Text(formatDuration(draft.currentRemaining))
                            .font(.system(size: 54, weight: .light, design: .rounded)).monospacedDigit()
                    } else {
                        IgnidoProgressFace(remaining: draft.currentRemaining, total: max(draft.duration, 1)).frame(width: 230, height: 230)
                    }
                }
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
                .gesture(
                    LongPressGesture(minimumDuration: 0.45)
                        .exclusively(before: TapGesture())
                        .onEnded { value in
                            switch value {
                            case .first: enlarged = true
                            case .second: displayMode = displayMode == 0 ? 1 : 0
                            }
                        }
                )
            }
            Section("音・動画") {
                HStack {
                    Text("選択中")
                    Spacer()
                    Text(draft.mediaDisplayName.isEmpty ? "標準アラーム音" : draft.mediaDisplayName)
                        .foregroundStyle(IgnidoTheme.secondaryText)
                }
                Button("音声 / 動画ファイルを選ぶ") { importing = true }
                if draft.mediaFileName != nil {
                    Button("選択を解除", role: .destructive) { draft.mediaFileName = nil; draft.mediaDisplayName = "" }
                }
                if let importError { Text(importError).foregroundStyle(.red).font(.caption) }
            }
            Section("操作") {
                if draft.running {
                    Button("一時停止") { store.update(draft); store.pause(draft); dismiss() }
                } else {
                    Button("開始") { store.update(draft); Task { await store.start(draft); dismiss() } }
                }
                Button("+1分") { store.update(draft); Task { await store.addTime(draft, seconds: 60); dismiss() } }
                Button("リセット") { store.update(draft); store.reset(draft); dismiss() }
                if !draft.saved {
                    Button("このタイマーを保存") { draft.saved = true; store.update(draft) }
                }
            }
            Section {
                Button("削除", role: .destructive) { store.delete(draft); dismiss() }
            }
        }
        .navigationTitle("タイマー設定")
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("保存") { store.update(draft); dismiss() } } }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.movie, .audio], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first else { return }
                draft.mediaFileName = try MediaLibrary.importFile(from: url)
                draft.mediaDisplayName = url.lastPathComponent
                importError = nil
            } catch { importError = error.localizedDescription }
        }
        .fullScreenCover(isPresented: $enlarged) {
            MultiTimerExpandedView(timerID: draft.id, displayMode: $displayMode)
        }
    }
}

private struct MultiTimerExpandedView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: MultiTimerStore
    let timerID: UUID
    @Binding var displayMode: Int

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            TimelineView(.periodic(from: .now, by: 0.1)) { _ in
                VStack(spacing: 28) {
                    if let item = store.item(id: timerID) {
                        Group {
                            if displayMode == 0 {
                                Text(formatDuration(item.currentRemaining))
                                    .font(.system(size: 76, weight: .light, design: .rounded))
                                    .monospacedDigit().foregroundStyle(.white)
                            } else {
                                IgnidoProgressFace(remaining: item.currentRemaining, total: max(item.duration, 1))
                                    .frame(width: 330, height: 330)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { displayMode = displayMode == 0 ? 1 : 0 }
                        Text(item.label.isEmpty ? "タイマー" : item.label)
                            .font(.headline).foregroundStyle(.white.opacity(0.88))
                    } else {
                        Text("タイマー終了").font(.largeTitle.bold()).foregroundStyle(.white)
                    }
                    Button("閉じる") { dismiss() }.buttonStyle(.borderedProminent)
                }
            }
        }
    }
}

struct MultiTimerResultView: View {
    let item: WakeTimerItem
    let onDismiss: () -> Void
    var body: some View {
        if let url = MediaLibrary.url(for: item.mediaFileName) {
            ZStack(alignment: .topLeading) {
                TimerMediaScreen(url: url, onDismiss: onDismiss)
                Text(item.label.isEmpty ? "タイマー終了" : item.label)
                    .font(.headline).foregroundStyle(.white).padding(12)
                    .background(.black.opacity(0.58), in: Capsule()).padding()
            }
        } else {
            ZStack {
                IgnidoScreenBackground()
                VStack(spacing: 20) {
                    IgnidoFlameMark().frame(width: 48, height: 68)
                    Text(item.label.isEmpty ? "タイマー終了" : item.label)
                        .font(.largeTitle.bold()).foregroundStyle(IgnidoTheme.text)
                    Button("停止", action: onDismiss).buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                }
                .ignidoCard().padding(28)
            }
        }
    }
}
