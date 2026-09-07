import SwiftUI
import UniformTypeIdentifiers
import AlarmKit

struct AlarmListView: View {
    @EnvironmentObject private var store: AlarmStore
    @State private var showingAdd = false

    var body: some View {
        NavigationStack {
            List {
                if store.authorizationState != .authorized {
                    Section {
                        Button("アラーム権限を許可") { Task { _ = await store.requestAuthorization() } }
                    } footer: {
                        Text("AlarmKitを使って消音・集中モード中でもシステムのアラームとして鳴らします。")
                    }
                }
                Section {
                    ForEach(store.alarms) { alarm in
                        NavigationLink {
                            AlarmEditorView(existing: alarm)
                        } label: {
                            AlarmRow(alarm: alarm)
                        }
                    }
                    .onDelete(perform: store.delete)
                }
                if let error = store.lastError {
                    Section("エラー") { Text(error).foregroundStyle(.red) }
                }
            }
            .navigationTitle("IGNIDO Wake")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showingAdd = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showingAdd) { AlarmEditorView(existing: nil) }
        }
    }
}

private struct AlarmRow: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: WakeAlarm
    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(alarm.timeText).font(.system(size: 40, weight: .light, design: .rounded)).monospacedDigit()
                Text(alarm.label).font(.headline)
                HStack(spacing: 8) {
                    Text(alarm.repeatText)
                    if alarm.mission != .none { Text(alarm.mission.title) }
                    if alarm.preAlertMinutes > 0 { Text("\(alarm.preAlertMinutes)分前") }
                }.font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Toggle("", isOn: Binding(
                get: { alarm.enabled },
                set: { value in Task { await store.setEnabled(alarm, enabled: value) } }
            )).labelsHidden()
        }.padding(.vertical, 5)
    }
}

struct AlarmEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: AlarmStore
    private let isNew: Bool
    @State private var draft: WakeAlarm
    @State private var time: Date
    @State private var importingMedia = false
    @State private var showMissionTest = false
    @State private var showMediaTest = false
    @State private var importError: String?

    init(existing: WakeAlarm?) {
        let alarm = existing ?? WakeAlarm(hour: 7, minute: 0, label: "起床")
        isNew = existing == nil
        _draft = State(initialValue: alarm)
        var c = DateComponents(); c.hour = alarm.hour; c.minute = alarm.minute
        _time = State(initialValue: Calendar.current.date(from: c) ?? Date())
    }

    var body: some View {
        Form {
            Section {
                DatePicker("時刻", selection: $time, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel).labelsHidden().frame(maxWidth: .infinity)
                TextField("名前", text: $draft.label)
            }

            Section("繰り返し") {
                HStack {
                    ForEach(Array(zip(1...7, ["月","火","水","木","金","土","日"])), id: \.0) { day, name in
                        Button(name) {
                            if draft.weekdays.contains(day) { draft.weekdays.remove(day) } else { draft.weekdays.insert(day) }
                        }
                        .buttonStyle(.bordered)
                        .tint(draft.weekdays.contains(day) ? .red : .gray)
                    }
                }
            }

            Section("解除方法") {
                Picker("ミッション", selection: $draft.mission) {
                    ForEach(AlarmMission.allCases) { mission in Text(mission.title).tag(mission) }
                }
                if needsTarget(draft.mission) {
                    Stepper(targetLabel, value: $draft.missionTarget, in: 1...300)
                }
                if draft.mission == .code {
                    TextField("解除コード", text: $draft.unlockCode).textInputAutocapitalization(.never).autocorrectionDisabled()
                }
                if draft.mission == .sentence {
                    TextField("入力する文章", text: $draft.unlockSentence)
                }
                Button("解除方法を今すぐテスト") { showMissionTest = true }
            }

            Section("音・動画") {
                HStack {
                    Text("選択中")
                    Spacer()
                    Text(draft.mediaFileName == nil ? "標準アラーム音" : "カスタムメディア")
                        .foregroundStyle(.secondary)
                }
                Button("音声 / 動画ファイルを選ぶ") { importingMedia = true }
                if draft.mediaFileName != nil {
                    Button("選択を解除", role: .destructive) { draft.mediaFileName = nil }
                    Button("メディアをテスト") { showMediaTest = true }
                }
                HStack { Text("音量"); Slider(value: $draft.volume, in: 0...1); Text("\(Int(draft.volume * 100))%").monospacedDigit() }
                Picker("振動", selection: $draft.vibration) {
                    ForEach(VibrationMode.allCases) { mode in Text(mode.title).tag(mode) }
                }
                if let importError { Text(importError).foregroundStyle(.red).font(.caption) }
            }

            Section("事前通知") {
                Picker("何分前", selection: $draft.preAlertMinutes) {
                    Text("オフ").tag(0)
                    ForEach([5,10,15,30,60,120], id: \.self) { n in Text("\(n)分前").tag(n) }
                    if ![0,5,10,15,30,60,120].contains(draft.preAlertMinutes) { Text("\(draft.preAlertMinutes)分前").tag(draft.preAlertMinutes) }
                }
                Stepper("カスタム: \(draft.preAlertMinutes)分前", value: $draft.preAlertMinutes, in: 0...1440)
                Text("事前通知は音・振動なしで表示します。").font(.caption).foregroundStyle(.secondary)
            }

            Section("スヌーズ") {
                Stepper("\(draft.snoozeMinutes)分", value: $draft.snoozeMinutes, in: 1...60)
            }

            Section {
                Button("5秒後にシステムアラームをテスト") { Task { await store.scheduleTest(draft) } }
            }

            if !isNew {
                Section { Button("このアラームを削除", role: .destructive) { store.delete(draft); dismiss() } }
            }
        }
        .navigationTitle(isNew ? "アラームを追加" : "アラーム設定")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { if isNew { Button("キャンセル") { dismiss() } } }
            ToolbarItem(placement: .confirmationAction) { Button("保存") { save() } }
        }
        .fileImporter(isPresented: $importingMedia, allowedContentTypes: [.movie, .audio], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first else { return }
                draft.mediaFileName = try MediaLibrary.importFile(from: url)
                importError = nil
            } catch { importError = error.localizedDescription }
        }
        .fullScreenCover(isPresented: $showMissionTest) {
            MissionView(alarm: draft) { showMissionTest = false }
        }
        .fullScreenCover(isPresented: $showMediaTest) {
            if let url = MediaLibrary.url(for: draft.mediaFileName) {
                MediaAlarmScreen(alarm: draft, url: url) { showMediaTest = false }
            } else {
                Button("閉じる") { showMediaTest = false }
            }
        }
    }

    private var targetLabel: String {
        switch draft.mission {
        case .steps: return "歩数: \(draft.missionTarget)歩"
        case .math: return "問題数: \(draft.missionTarget)問"
        case .taps: return "連打: \(draft.missionTarget)回"
        case .shake: return "シェイク: \(draft.missionTarget)回"
        case .hold: return "長押し: \(draft.missionTarget)秒"
        case .swipe: return "スワイプ: \(draft.missionTarget)回"
        default: return "回数: \(draft.missionTarget)"
        }
    }

    private func needsTarget(_ mission: AlarmMission) -> Bool {
        [.steps,.math,.taps,.shake,.hold,.swipe].contains(mission)
    }

    private func save() {
        let c = Calendar.current.dateComponents([.hour,.minute], from: time)
        draft.hour = c.hour ?? 7; draft.minute = c.minute ?? 0
        if draft.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { draft.label = "アラーム" }
        if draft.missionTarget <= 0 { draft.missionTarget = max(1, draft.mission.defaultTarget) }
        Task {
            if isNew { await store.add(draft) } else { await store.update(draft) }
            dismiss()
        }
    }
}
