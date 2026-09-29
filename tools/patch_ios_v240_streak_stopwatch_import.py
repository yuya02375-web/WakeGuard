from pathlib import Path
root=Path('ios')
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='IGNIDOWake/Info.plist'; s=read(p)
s=s.replace('<string>2.3.0</string>','<string>2.4.0</string>',1).replace('<string>130</string>','<string>140</string>',1)
write(p,s)
p='project.yml'; s=read(p)
s=s.replace('CFBundleShortVersionString: "2.3.0"','CFBundleShortVersionString: "2.4.0"',1).replace('CFBundleVersion: "130"','CFBundleVersion: "140"',1)
write(p,s)

p='IGNIDOWake/AlarmModel.swift'; s=read(p)
old='''struct StopwatchLap: Identifiable, Codable, Hashable {
    var id = UUID()
    var index: Int
    var lap: TimeInterval
    var total: TimeInterval
}'''
new='''struct StopwatchLap: Identifiable, Codable, Hashable {
    var id = UUID()
    var index: Int
    var lap: TimeInterval
    var total: TimeInterval
    var capturedAt: Date? = nil
}'''
if old not in s: raise SystemExit('StopwatchLap model not found')
s=s.replace(old,new,1)
write(p,s)

p='IGNIDOWake/FeatureStores.swift'; s=read(p)
s=s.replace('@Published var laps: [StopwatchLap] = [] { didSet { save() } }',
'''@Published var laps: [StopwatchLap] = [] { didSet { save() } }
    @Published var stoppedAt: Date? = nil { didSet { save() } }''',1)
old='''    func toggle() {
        if running {
            accumulated += Date().timeIntervalSince(startedAt ?? Date())
            startedAt = nil
        } else {
            startedAt = Date()
        }
        running.toggle()
    }

    func lap() {
        guard running else { return }
        let total = elapsed
        let previous = laps.last?.total ?? 0
        laps.append(StopwatchLap(index: laps.count + 1, lap: total - previous, total: total))
    }

    func reset() {
        running = false
        accumulated = 0
        startedAt = nil
        laps = []
    }'''
new='''    func toggle() {
        let now = Date()
        if running {
            accumulated += now.timeIntervalSince(startedAt ?? now)
            startedAt = nil
            stoppedAt = now
        } else {
            startedAt = now
        }
        running.toggle()
    }

    func lap() {
        guard running else { return }
        let now = Date()
        let total = elapsed
        let previous = laps.last?.total ?? 0
        laps.append(StopwatchLap(index: laps.count + 1, lap: total - previous, total: total, capturedAt: now))
    }

    func reset() {
        running = false
        accumulated = 0
        startedAt = nil
        stoppedAt = nil
        laps = []
    }

    func importEnd(for lap: StopwatchLap? = nil) -> Date {
        if let lap, let capturedAt = lap.capturedAt { return capturedAt }
        if let lap {
            let reference = running ? Date() : (stoppedAt ?? Date())
            return reference.addingTimeInterval(-max(0, elapsed - lap.total))
        }
        return running ? Date() : (stoppedAt ?? Date())
    }'''
if old not in s: raise SystemExit('Stopwatch methods block missing')
s=s.replace(old,new,1)
old='''    private struct Snapshot: Codable {
        var running: Bool
        var accumulated: TimeInterval
        var startedAt: Date?
        var laps: [StopwatchLap]
    }
    private func save() {
        let snap = Snapshot(running: running, accumulated: accumulated, startedAt: startedAt, laps: laps)'''
new='''    private struct Snapshot: Codable {
        var running: Bool
        var accumulated: TimeInterval
        var startedAt: Date?
        var laps: [StopwatchLap]
        var stoppedAt: Date?
        enum CodingKeys: String, CodingKey { case running, accumulated, startedAt, laps, stoppedAt }
        init(running: Bool, accumulated: TimeInterval, startedAt: Date?, laps: [StopwatchLap], stoppedAt: Date?) { self.running = running; self.accumulated = accumulated; self.startedAt = startedAt; self.laps = laps; self.stoppedAt = stoppedAt }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            running = try c.decodeIfPresent(Bool.self, forKey: .running) ?? false
            accumulated = try c.decodeIfPresent(TimeInterval.self, forKey: .accumulated) ?? 0
            startedAt = try c.decodeIfPresent(Date.self, forKey: .startedAt)
            laps = try c.decodeIfPresent([StopwatchLap].self, forKey: .laps) ?? []
            stoppedAt = try c.decodeIfPresent(Date.self, forKey: .stoppedAt)
        }
    }
    private func save() {
        let snap = Snapshot(running: running, accumulated: accumulated, startedAt: startedAt, laps: laps, stoppedAt: stoppedAt)'''
if old not in s: raise SystemExit('Snapshot block missing')
s=s.replace(old,new,1)
old='''        running = snap.running
        accumulated = snap.accumulated
        startedAt = snap.startedAt
        laps = snap.laps'''
new='''        running = snap.running
        accumulated = snap.accumulated
        startedAt = snap.startedAt
        laps = snap.laps
        stoppedAt = snap.stoppedAt'''
if old not in s: raise SystemExit('Snapshot load missing')
s=s.replace(old,new,1)
write(p,s)

p='IGNIDOWake/ClockViews.swift'; s=read(p)
s=s.replace('@State private var showTimeLog = false',
'''@State private var showTimeLog = false
    @State private var importRequest: TimeLogImportRequest?''',1)
old='''                Button { showTimeLog = true } label: {
                    Label("時間記録・集計", systemImage: "folder.badge.clock")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                List {
                    ForEach(store.laps.reversed()) { lap in
                        HStack {
                            Text(AppText.format("ラップ %d", lap.index))
                            Spacer()
                            Text(formatStopwatch(lap.lap)).monospacedDigit()
                            Text(formatStopwatch(lap.total)).monospacedDigit().foregroundStyle(.secondary)
                        }
                    }
                }.listStyle(.plain)'''
new='''                HStack(spacing: 10) {
                    Button {
                        let duration = store.elapsed
                        guard !store.running, duration > 0 else { return }
                        importRequest = TimeLogImportRequest(title: "ストップウォッチ", duration: duration, end: store.importEnd())
                    } label: {
                        Label("時間記録に追加", systemImage: "plus.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(store.running || store.elapsed <= 0)

                    Button { showTimeLog = true } label: { Image(systemName: "folder") }
                        .buttonStyle(.bordered)
                        .accessibilityLabel("時間記録を開く")
                }

                List {
                    ForEach(store.laps.reversed()) { lap in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(AppText.format("ラップ %d", lap.index))
                                Text(formatStopwatch(lap.lap)).monospacedDigit()
                            }
                            Spacer()
                            Text(formatStopwatch(lap.total)).monospacedDigit().foregroundStyle(.secondary)
                            Button {
                                importRequest = TimeLogImportRequest(title: AppText.format("ラップ %d", lap.index), duration: lap.lap, end: store.importEnd(for: lap))
                            } label: { Image(systemName: "plus.circle") }
                                .buttonStyle(.plain)
                                .accessibilityLabel("このラップを時間記録に追加")
                        }
                    }
                }.listStyle(.plain)'''
if old not in s: raise SystemExit('Stopwatch UI block missing')
s=s.replace(old,new,1)
s=s.replace('.fullScreenCover(isPresented: $showTimeLog) { TimeLogView() }',
            '.fullScreenCover(isPresented: $showTimeLog) { TimeLogView() }\n        .sheet(item: $importRequest) { TimeLogImportSheet(request: $0) }',1)
write(p,s)

p='IGNIDOWake/TimeLogView.swift'; s=read(p)
insert='''struct TimeLogImportRequest: Identifiable {
    let id = UUID()
    let title: String
    let duration: TimeInterval
    let end: Date
}

struct TimeLogImportSheet: View {
    @Environment(\\.dismiss) private var dismiss
    @StateObject private var store = TimeLogStore()
    @State private var createFolder = false
    let request: TimeLogImportRequest

    var body: some View {
        NavigationStack {
            List {
                Section("追加する時間") {
                    HStack { Text(request.title); Spacer(); Text(timeLogDuration(request.duration)).monospacedDigit() }
                }
                Section("追加先") {
                    if store.folders.isEmpty {
                        ContentUnavailableView("フォルダーがありません", systemImage: "folder")
                        Button("フォルダーを作成") { createFolder = true }
                    } else {
                        ForEach(store.folders.sorted { $0.createdAt > $1.createdAt }) { folder in
                            Button {
                                store.addEntry(folderID: folder.id, start: request.end.addingTimeInterval(-request.duration), end: request.end)
                                dismiss()
                            } label: {
                                HStack { Label(folder.name, systemImage: "folder"); Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary) }
                            }
                            .foregroundStyle(.primary)
                        }
                        Button { createFolder = true } label: { Label("新しいフォルダー", systemImage: "folder.badge.plus") }
                    }
                }
            }
            .navigationTitle("時間記録に追加")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("キャンセル") { dismiss() } } }
        }
        .sheet(isPresented: $createFolder) {
            FolderEditorSheet(initialName: "") { name in
                store.addFolder(name)
                if let folder = store.folders.last {
                    store.addEntry(folderID: folder.id, start: request.end.addingTimeInterval(-request.duration), end: request.end)
                    dismiss()
                }
            }
        }
    }
}

'''
marker='struct TimeLogView: View {'
if marker not in s: raise SystemExit('TimeLogView marker missing')
s=s.replace(marker,insert+marker,1)
write(p,s)

p='IGNIDOWake/StreakParity.swift'; s=read(p)
old='''                    VStack(spacing:18) {
                        GrowthCompanionView(level:store.growthLevel, streak:store.displayCurrent(alarms:alarmStore.alarms)).frame(height:300)
                        VStack(spacing:3){Text(AppText.format("成長 Lv.%d", store.growthLevel)).font(.title2.bold());Text(store.growthDescriptor).foregroundStyle(IgnidoTheme.ember)}
                        HStack { stat("現在",store.displayCurrent(alarms:alarmStore.alarms));stat("最高",store.bestStreak);stat("成功",store.totalWakeups) }.ignidoCard()
                        protectionCard
                        calendarCard
                        Button("今日の起床を記録") { store.recordWake(alarms:alarmStore.alarms) }.buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                        NavigationLink("アプリ情報") { AboutView() }.foregroundStyle(IgnidoTheme.secondaryText)
                    }.padding()'''
new='''                    VStack(spacing:20) {
                        HStack(spacing:18) {
                            GrowthCompanionView(level:store.growthLevel, streak:store.displayCurrent(alarms:alarmStore.alarms))
                                .frame(width:150,height:190)
                            VStack(alignment:.leading,spacing:5) {
                                Text("\\(store.displayCurrent(alarms:alarmStore.alarms))日")
                                    .font(.system(size:52,weight:.bold,design:.default)).monospacedDigit()
                                Text("現在のストリーク").font(.caption).foregroundStyle(.secondary)
                                Text("Lv.\\(store.growthLevel)").font(.headline).padding(.top,14)
                                Text(store.growthDescriptor).font(.caption).foregroundStyle(IgnidoTheme.ember)
                            }
                            Spacer(minLength:0)
                        }
                        Divider()
                        HStack { stat("最高",store.bestStreak);stat("成功",store.totalWakeups);stat("保護",store.balance) }
                        protectionCard
                        calendarCard
                        Button("今日の起床を記録") { store.recordWake(alarms:alarmStore.alarms) }.buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                        NavigationLink("アプリ情報") { AboutView() }.foregroundStyle(IgnidoTheme.secondaryText)
                    }.padding(.horizontal,18).padding(.vertical,10)'''
if old not in s: raise SystemExit('Streak dashboard block missing')
s=s.replace(old,new,1)
s=s.replace('''            Text("毎月1日に2日分回復し、最大3日。対象日に起きられなかった場合に1日使ってストリークを守ります。30日連続の実起床成功ごとに1日追加します。")
                .font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
        }.ignidoCard()''','''            Text("最大3日。失敗した対象日に1日使ってストリークを維持します。")
                .font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
        }
        .padding(14)
        .background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius:10))''',1)
s=s.replace('''            Text(AppText.format("成功 %d ・ 保護 %d ・ 失敗 %d ・ 予定 %d", stats.wins, stats.protected, stats.losses, stats.pending)).font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
        }.ignidoCard()''','''            Text(AppText.format("成功 %d ・ 保護 %d ・ 失敗 %d ・ 予定 %d", stats.wins, stats.protected, stats.losses, stats.pending)).font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
        }
        .padding(14)
        .background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius:10))''',1)
write(p,s)

assert '<string>2.4.0</string>' in read('IGNIDOWake/Info.plist')
assert 'var capturedAt: Date? = nil' in read('IGNIDOWake/AlarmModel.swift')
assert 'TimeLogImportSheet(request:' in read('IGNIDOWake/ClockViews.swift')
assert 'struct TimeLogImportSheet' in read('IGNIDOWake/TimeLogView.swift')
assert '.frame(width:150,height:190)' in read('IGNIDOWake/StreakParity.swift')
print('iOS 2.4.0 patch applied')
