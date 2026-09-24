import SwiftUI

struct TimeLogFolder: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var name: String
    var createdAt: Date = Date()
}

struct TimeLogEntry: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var folderID: UUID
    var start: Date
    var end: Date
}

@MainActor
final class TimeLogStore: ObservableObject {
    @Published var folders: [TimeLogFolder] = [] { didSet { save() } }
    @Published var entries: [TimeLogEntry] = [] { didSet { save() } }
    private let key = "ignido.timeLog.v1"
    private var loading = true

    init() { load(); loading = false }

    private struct Payload: Codable { var folders: [TimeLogFolder]; var entries: [TimeLogEntry] }
    private func load() {
        guard let data = UserDefaults.standard.data(forKey: key), let value = try? JSONDecoder().decode(Payload.self, from: data) else { return }
        folders = value.folders; entries = value.entries.filter { $0.end > $0.start }
    }
    private func save() {
        guard !loading, let data = try? JSONEncoder().encode(Payload(folders: folders, entries: entries)) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    func addFolder(_ name: String) { folders.append(TimeLogFolder(name: name)) }
    func renameFolder(_ id: UUID, _ name: String) { guard let i = folders.firstIndex(where: { $0.id == id }) else { return }; folders[i].name = name }
    func deleteFolder(_ id: UUID) { folders.removeAll { $0.id == id }; entries.removeAll { $0.folderID == id } }
    func addEntry(folderID: UUID, start: Date, end: Date) { entries.append(TimeLogEntry(folderID: folderID, start: start, end: end)) }
    func updateEntry(_ entry: TimeLogEntry) { guard let i = entries.firstIndex(where: { $0.id == entry.id }) else { return }; entries[i] = entry }
    func deleteEntry(_ id: UUID) { entries.removeAll { $0.id == id } }
    func entries(for folderID: UUID) -> [TimeLogEntry] { entries.filter { $0.folderID == folderID }.sorted { $0.start > $1.start } }

    private var cal: Calendar { var c = Calendar.autoupdatingCurrent; c.firstWeekday = 2; return c }
    func duration(folderID: UUID, from start: Date, to end: Date) -> TimeInterval {
        entries.reduce(0) { sum, e in
            guard e.folderID == folderID else { return sum }
            let lo = max(e.start, start), hi = min(e.end, end)
            return sum + max(0, hi.timeIntervalSince(lo))
        }
    }
    func total(_ id: UUID) -> TimeInterval { entries.filter { $0.folderID == id }.reduce(0) { $0 + max(0, $1.end.timeIntervalSince($1.start)) } }
    func today(_ id: UUID, now: Date = Date()) -> TimeInterval { let a = cal.startOfDay(for: now); return duration(folderID: id, from: a, to: cal.date(byAdding: .day, value: 1, to: a)!) }
    func week(_ id: UUID, now: Date = Date()) -> TimeInterval {
        let day = cal.startOfDay(for: now); let weekday = cal.component(.weekday, from: day); let delta = (weekday - 2 + 7) % 7
        let a = cal.date(byAdding: .day, value: -delta, to: day)!; return duration(folderID: id, from: a, to: cal.date(byAdding: .day, value: 7, to: a)!)
    }
    func day(_ id: UUID, _ date: Date) -> TimeInterval { let a = cal.startOfDay(for: date); return duration(folderID: id, from: a, to: cal.date(byAdding: .day, value: 1, to: a)!) }
    func averageDay(_ id: UUID) -> TimeInterval {
        let list = entries(for: id); guard !list.isEmpty else { return 0 }
        var days = Set<Date>()
        for e in list {
            var d = cal.startOfDay(for: e.start); let last = cal.startOfDay(for: e.end.addingTimeInterval(-0.001))
            while d <= last { if day(id, d) > 0 { days.insert(d) }; d = cal.date(byAdding: .day, value: 1, to: d)! }
        }
        return days.isEmpty ? 0 : total(id) / Double(days.count)
    }
}

struct TimeLogView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var store = TimeLogStore()
    @State private var folderEditor: FolderEditorState?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if store.folders.isEmpty {
                        ContentUnavailableView("フォルダーがありません", systemImage: "folder", description: Text("右上の＋から、勉強・バイト・運動などのフォルダーを作成できます。"))
                    } else {
                        ForEach(store.folders.sorted { $0.createdAt > $1.createdAt }) { folder in
                            NavigationLink { TimeLogFolderDetail(store: store, folderID: folder.id) } label: { FolderSummaryRow(store: store, folder: folder) }
                        }
                        .onDelete { offsets in
                            let sorted = store.folders.sorted { $0.createdAt > $1.createdAt }
                            for i in offsets { store.deleteFolder(sorted[i].id) }
                        }
                    }
                } header: { Text("フォルダー") } footer: { Text("各フォルダー内で開始・終了時刻を記録すると、1件の時間・日合計・週合計・全期間合計・1日平均を自動計算します。") }
            }
            .navigationTitle("時間記録")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("閉じる") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) { Button { folderEditor = FolderEditorState(folderID: nil, name: "") } label: { Image(systemName: "plus") } }
            }
        }
        .sheet(item: $folderEditor) { state in FolderEditorSheet(initialName: state.name) { name in store.addFolder(name) } }
    }
}

private struct FolderSummaryRow: View {
    @ObservedObject var store: TimeLogStore
    let folder: TimeLogFolder
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack { Label(folder.name, systemImage: "folder.fill").font(.headline); Spacer(); Text("\(store.entries(for: folder.id).count)件").font(.caption).foregroundStyle(.secondary) }
            HStack(spacing: 14) {
                mini("今日", store.today(folder.id)); mini("今週", store.week(folder.id)); mini("合計", store.total(folder.id))
            }
        }.padding(.vertical, 5)
    }
    @ViewBuilder private func mini(_ label: String, _ t: TimeInterval) -> some View { VStack(alignment: .leading, spacing: 2) { Text(label).font(.caption2).foregroundStyle(.secondary); Text(timeLogDuration(t)).font(.caption.monospacedDigit()) } }
}

private struct FolderEditorState: Identifiable { let id = UUID(); var folderID: UUID?; var name: String }

private struct FolderEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State var name: String
    let onSave: (String) -> Void
    init(initialName: String, onSave: @escaping (String) -> Void) { _name = State(initialValue: initialName); self.onSave = onSave }
    var body: some View {
        NavigationStack {
            Form { TextField("例：勉強、バイト、運動", text: $name) }
                .navigationTitle(name.isEmpty ? "フォルダーを作成" : "フォルダー名")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("キャンセル") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("保存") { let n = name.trimmingCharacters(in: .whitespacesAndNewlines); guard !n.isEmpty else { return }; onSave(n); dismiss() }.disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
                }
        }
    }
}

private struct TimeLogFolderDetail: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store: TimeLogStore
    let folderID: UUID
    @State private var editEntry: EntryEditorState?
    @State private var rename = false
    @State private var deleteFolderConfirm = false

    private var folder: TimeLogFolder? { store.folders.first { $0.id == folderID } }
    private var entries: [TimeLogEntry] { store.entries(for: folderID) }
    private var groups: [(Date, [TimeLogEntry])] {
        let cal = Calendar.autoupdatingCurrent
        let dict = Dictionary(grouping: entries) { cal.startOfDay(for: $0.start) }
        return dict.keys.sorted(by: >).map { ($0, dict[$0] ?? []) }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                statsGrid
                if entries.isEmpty { ContentUnavailableView("まだ記録がありません", systemImage: "clock.badge.plus", description: Text("右上の＋から開始・終了時刻を追加してください。")) .padding(.top, 40) }
                else {
                    ForEach(groups.indices, id: \.self) { index in
                        let day = groups[index].0
                        let rows = groups[index].1
                        VStack(alignment: .leading, spacing: 8) {
                            HStack { Text(day.formatted(.dateTime.year().month().day().weekday(.abbreviated))).font(.headline); Spacer(); Text("合計 \(timeLogDuration(store.day(folderID, day)))").font(.subheadline).foregroundStyle(IgnidoTheme.flame) }
                            ForEach(rows) { e in EntryRow(entry: e).onTapGesture { editEntry = EntryEditorState(entry: e) }.contextMenu { Button("編集") { editEntry = EntryEditorState(entry: e) }; Button("削除", role: .destructive) { store.deleteEntry(e.id) } } }
                        }
                    }
                }
            }.padding()
        }
        .background(IgnidoTheme.background)
        .navigationTitle(folder?.name ?? "時間記録")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { editEntry = EntryEditorState(entry: nil) } label: { Image(systemName: "plus") }
                Menu {
                    Button("名称変更") { rename = true }
                    Button("フォルダーを削除", role: .destructive) { deleteFolderConfirm = true }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
        .sheet(item: $editEntry) { state in EntryEditorSheet(existing: state.entry) { start, end in if var e = state.entry { e.start = start; e.end = end; store.updateEntry(e) } else { store.addEntry(folderID: folderID, start: start, end: end) } } onDelete: { id in store.deleteEntry(id) } }
        .sheet(isPresented: $rename) { FolderEditorSheet(initialName: folder?.name ?? "") { store.renameFolder(folderID, $0) } }
        .confirmationDialog("フォルダーを削除しますか？", isPresented: $deleteFolderConfirm, titleVisibility: .visible) { Button("削除", role: .destructive) { store.deleteFolder(folderID); dismiss() } }
    }

    private var statsGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 9) {
            StatCard(label: "合計", value: store.total(folderID)); StatCard(label: "今日", value: store.today(folderID)); StatCard(label: "今週", value: store.week(folderID)); StatCard(label: "1日平均", value: store.averageDay(folderID))
        }
    }
}

private struct StatCard: View {
    let label: String; let value: TimeInterval
    var body: some View { VStack(alignment: .leading, spacing: 5) { Text(label).font(.caption).foregroundStyle(.secondary); Text(timeLogDuration(value)).font(.title3.bold()).monospacedDigit().minimumScaleFactor(0.7).lineLimit(1) }.frame(maxWidth: .infinity, alignment: .leading).padding(13).background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 13)).overlay(RoundedRectangle(cornerRadius: 13).stroke(IgnidoTheme.border, lineWidth: 1)) }
}

private struct EntryRow: View {
    let entry: TimeLogEntry
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) { Text(range).font(.headline).monospacedDigit(); Text(timeLogDuration(entry.end.timeIntervalSince(entry.start))).font(.subheadline).foregroundStyle(IgnidoTheme.flame) }
            Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }.padding(13).background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).stroke(IgnidoTheme.border, lineWidth: 1))
    }
    private var range: String { let a = entry.start.formatted(date: .omitted, time: .shortened), b = entry.end.formatted(date: .omitted, time: .shortened); return Calendar.autoupdatingCurrent.isDate(entry.start, inSameDayAs: entry.end) ? "\(a) – \(b)" : "\(a) – \(b)  翌日" }
}

private struct EntryEditorState: Identifiable { let id = UUID(); var entry: TimeLogEntry? }

private struct EntryEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    let existing: TimeLogEntry?
    let onSave: (Date, Date) -> Void
    let onDelete: (UUID) -> Void
    @State private var day: Date
    @State private var startTime: Date
    @State private var endTime: Date

    init(existing: TimeLogEntry?, onSave: @escaping (Date, Date) -> Void, onDelete: @escaping (UUID) -> Void) {
        self.existing = existing; self.onSave = onSave; self.onDelete = onDelete
        let base = existing?.start ?? Date(); let end = existing?.end ?? base.addingTimeInterval(3600)
        _day = State(initialValue: base); _startTime = State(initialValue: base); _endTime = State(initialValue: end)
    }

    var body: some View {
        NavigationStack {
            Form {
                DatePicker("日付", selection: $day, displayedComponents: .date)
                DatePicker("開始", selection: $startTime, displayedComponents: .hourAndMinute)
                DatePicker("終了", selection: $endTime, displayedComponents: .hourAndMinute)
                if !Calendar.autoupdatingCurrent.isDate(resolvedStart, inSameDayAs: resolvedEnd) { Text("終了は翌日として記録されます").foregroundStyle(.secondary) }
                if let e = existing { Button("この記録を削除", role: .destructive) { onDelete(e.id); dismiss() } }
            }
            .navigationTitle(existing == nil ? "時間を追加" : "時間を編集")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("キャンセル") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("保存") { onSave(resolvedStart, resolvedEnd); dismiss() } }
            }
        }
    }

    private var resolvedStart: Date { combine(day: day, time: startTime) }
    private var resolvedEnd: Date { let s = resolvedStart, e0 = combine(day: day, time: endTime); return e0 > s ? e0 : Calendar.autoupdatingCurrent.date(byAdding: .day, value: 1, to: e0)! }
    private func combine(day: Date, time: Date) -> Date { let c = Calendar.autoupdatingCurrent; let d = c.dateComponents([.year,.month,.day], from: day), t = c.dateComponents([.hour,.minute], from: time); var x = DateComponents(); x.year=d.year;x.month=d.month;x.day=d.day;x.hour=t.hour;x.minute=t.minute;return c.date(from:x) ?? day }
}

private func timeLogDuration(_ seconds: TimeInterval) -> String {
    let minutes = max(0, Int((seconds / 60).rounded()))
    let h = minutes / 60, m = minutes % 60
    if h == 0 { return "\(m)分" }; if m == 0 { return "\(h)時間" }; return "\(h)時間\(m)分"
}
