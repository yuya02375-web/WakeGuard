import SwiftUI
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

    func add(label: String, duration: TimeInterval, saved: Bool = true) -> WakeTimerItem {
        let d = max(1, duration)
        let item = WakeTimerItem(label: label, duration: d, remaining: d, saved: saved)
        items.append(item)
        save()
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

    func finish(_ id: UUID) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        items[index].running = false
        items[index].remaining = 0
        items[index].endDate = nil
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
    @State private var temporaryDuration: TimeInterval = 300
    private let ticker = Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            ZStack {
                IgnidoScreenBackground()
                if store.items.isEmpty {
                    VStack(spacing: 18) {
                        IgnidoFlameMark().frame(width: 48, height: 68)
                        Text("タイマーなし").font(.title2.bold()).foregroundStyle(IgnidoTheme.text)
                        Text("複数のタイマーを保存して同時に動かせます。")
                            .foregroundStyle(IgnidoTheme.secondaryText)
                        Button("タイマーを追加") { adding = true }.buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                    }.padding(30)
                } else {
                    List {
                        ForEach(store.items) { item in
                            NavigationLink {
                                MultiTimerEditor(item: item)
                            } label: {
                                MultiTimerRow(item: item)
                            }
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) { store.delete(item) } label: { Label("削除", systemImage: "trash") }
                            }
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("タイマー")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button { adding = true } label: { Image(systemName: "plus") } }
            }
            .sheet(isPresented: $adding) { MultiTimerCreateView() }
            .onReceive(ticker) { _ in store.refreshFinished() }
        }
    }
}

private struct MultiTimerRow: View {
    @EnvironmentObject private var store: MultiTimerStore
    let item: WakeTimerItem
    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.2)) { _ in
            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(formatDuration(item.currentRemaining))
                        .font(.system(size: 38, weight: .light, design: .rounded)).monospacedDigit().foregroundStyle(IgnidoTheme.text)
                    Text(item.label.isEmpty ? "タイマー" : item.label).font(.headline).foregroundStyle(IgnidoTheme.text)
                    if !item.mediaDisplayName.isEmpty { Text(item.mediaDisplayName).font(.caption).foregroundStyle(IgnidoTheme.secondaryText).lineLimit(1) }
                }
                Spacer()
                if item.running {
                    Button { store.pause(item) } label: { Image(systemName: "pause.fill") }.buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                } else {
                    Button { Task { await store.start(item) } } label: { Image(systemName: "play.fill") }.buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                }
            }
            .ignidoCard()
            .padding(.vertical, 4)
        }
    }
}

struct MultiTimerCreateView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: MultiTimerStore
    @State private var label = ""
    @State private var hours = 0
    @State private var minutes = 5
    @State private var seconds = 0

    var body: some View {
        NavigationStack {
            Form {
                TextField("名前", text: $label)
                HStack {
                    timerNumber("時", $hours, 0...99)
                    timerNumber("分", $minutes, 0...59)
                    timerNumber("秒", $seconds, 0...59)
                }
                HStack {
                    ForEach([1,3,5,10,15,30,60], id: \.self) { n in
                        if n <= 15 { Button("\(n)分") { hours=n/60; minutes=n%60; seconds=0 }.buttonStyle(.bordered) }
                    }
                }
            }
            .navigationTitle("タイマーを追加")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("キャンセル") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("追加") {
                        let duration = TimeInterval(hours*3600 + minutes*60 + seconds)
                        guard duration > 0 else { return }
                        _ = store.add(label: label, duration: duration)
                        dismiss()
                    }
                }
            }
        }
    }

    private func timerNumber(_ title: String, _ binding: Binding<Int>, _ range: ClosedRange<Int>) -> some View {
        VStack {
            TextField("0", value: binding, format: .number).keyboardType(.numberPad).multilineTextAlignment(.center)
                .onChange(of: binding.wrappedValue) { _, n in binding.wrappedValue=min(max(n,range.lowerBound),range.upperBound) }
            Text(title).font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
        }
    }
}

struct MultiTimerEditor: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: MultiTimerStore
    @State private var draft: WakeTimerItem
    @State private var importing = false
    @State private var importError: String?
    @State private var displayMode = 0
    @State private var enlarged = false

    init(item: WakeTimerItem) { _draft = State(initialValue: item) }

    var body: some View {
        Form {
            Section {
                TextField("名前", text: $draft.label)
                Group {
                    if displayMode == 0 {
                        Text(formatDuration(draft.currentRemaining)).font(.system(size: 54, weight: .light, design: .rounded)).monospacedDigit()
                    } else {
                        IgnidoProgressFace(remaining: draft.currentRemaining, total: max(draft.duration, 1)).frame(width: 230, height: 230)
                    }
                }
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
                .onTapGesture { displayMode = displayMode == 0 ? 1 : 0 }
                .onLongPressGesture { enlarged = true }
            }
            Section("音・動画") {
                HStack { Text("選択中"); Spacer(); Text(draft.mediaDisplayName.isEmpty ? "標準アラーム音" : draft.mediaDisplayName).foregroundStyle(IgnidoTheme.secondaryText) }
                Button("音声 / 動画ファイルを選ぶ") { importing = true }
                if draft.mediaFileName != nil { Button("選択を解除", role: .destructive) { draft.mediaFileName=nil; draft.mediaDisplayName="" } }
                if let importError { Text(importError).foregroundStyle(.red).font(.caption) }
            }
            Section {
                if draft.running { Button("一時停止") { store.update(draft); store.pause(draft); dismiss() } }
                else { Button("開始") { store.update(draft); Task { await store.start(draft); dismiss() } } }
                Button("リセット") { store.update(draft); store.reset(draft); dismiss() }
            }
            Section { Button("削除", role: .destructive) { store.delete(draft); dismiss() } }
        }
        .navigationTitle("タイマー設定")
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("保存") { store.update(draft); dismiss() } } }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.movie,.audio], allowsMultipleSelection: false) { result in
            do {
                guard let url=try result.get().first else{return}
                draft.mediaFileName=try MediaLibrary.importFile(from:url)
                draft.mediaDisplayName=url.lastPathComponent
                importError=nil
            } catch { importError=error.localizedDescription }
        }
        .fullScreenCover(isPresented: $enlarged) {
            ZStack {
                Color.black.ignoresSafeArea()
                VStack(spacing: 24) {
                    TimelineView(.periodic(from: .now, by: 0.1)) { _ in
                        Group {
                            if displayMode==0 { Text(formatDuration(draft.currentRemaining)).font(.system(size: 76,weight:.light,design:.rounded)).monospacedDigit().foregroundStyle(.white) }
                            else { IgnidoProgressFace(remaining:draft.currentRemaining,total:max(draft.duration,1)).frame(width:330,height:330) }
                        }.onTapGesture { displayMode = displayMode == 0 ? 1 : 0 }
                    }
                    Button("閉じる") { enlarged=false }.buttonStyle(.borderedProminent)
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
                    .font(.headline).foregroundStyle(.white).padding(12).background(.black.opacity(0.58), in: Capsule()).padding()
            }
        } else {
            ZStack {
                IgnidoScreenBackground()
                VStack(spacing: 20) {
                    IgnidoFlameMark().frame(width:48,height:68)
                    Text(item.label.isEmpty ? "タイマー終了" : item.label).font(.largeTitle.bold()).foregroundStyle(IgnidoTheme.text)
                    Button("停止",action:onDismiss).buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                }.ignidoCard().padding(28)
            }
        }
    }
}
