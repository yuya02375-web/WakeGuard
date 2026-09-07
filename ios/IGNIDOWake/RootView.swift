import SwiftUI
import AlarmKit

struct RootView: View {
    var body: some View {
        TabView {
            AlarmListView()
                .tabItem { Label("アラーム", systemImage: "alarm.fill") }
            StopwatchView()
                .tabItem { Label("ストップウォッチ", systemImage: "stopwatch.fill") }
            WorldClockView()
                .tabItem { Label("世界時計", systemImage: "globe") }
            AboutView()
                .tabItem { Label("IGNIDO", systemImage: "flame.fill") }
        }
        .tint(Color(red: 0.95, green: 0.18, blue: 0.08))
    }
}

struct AlarmListView: View {
    @EnvironmentObject private var store: AlarmStore
    @State private var showingAdd = false

    var body: some View {
        NavigationStack {
            List {
                if store.authorizationState != .authorized {
                    Section {
                        Button("アラーム権限を許可") {
                            Task { _ = await store.requestAuthorization() }
                        }
                    } footer: {
                        Text("iOS 26のAlarmKitを使い、集中モードや消音中でも目立つアラームを表示します。")
                    }
                }

                Section {
                    ForEach(store.alarms) { alarm in
                        AlarmRow(alarm: alarm)
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
            .sheet(isPresented: $showingAdd) { AddAlarmView() }
        }
    }
}

struct AlarmRow: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: WakeAlarm

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(alarm.timeText).font(.system(size: 42, weight: .light, design: .rounded)).monospacedDigit()
                Text(alarm.label).font(.headline)
                Text(alarm.repeatText).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Toggle("", isOn: Binding(
                get: { alarm.enabled },
                set: { value in Task { await store.setEnabled(alarm, enabled: value) } }
            )).labelsHidden()
        }
        .padding(.vertical, 6)
    }
}

struct AddAlarmView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: AlarmStore
    @State private var time = Date()
    @State private var label = "起床"
    @State private var weekdays: Set<Int> = []

    var body: some View {
        NavigationStack {
            Form {
                DatePicker("時刻", selection: $time, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                TextField("ラベル", text: $label)
                Section("繰り返し") {
                    HStack {
                        ForEach(Array(zip(1...7, ["月","火","水","木","金","土","日"])), id: \.0) { day, name in
                            Button(name) {
                                if weekdays.contains(day) { weekdays.remove(day) } else { weekdays.insert(day) }
                            }
                            .buttonStyle(.bordered)
                            .tint(weekdays.contains(day) ? .red : .gray)
                        }
                    }
                }
            }
            .navigationTitle("アラームを追加")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("キャンセル") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let c = Calendar.current.dateComponents([.hour, .minute], from: time)
                        Task {
                            await store.add(hour: c.hour ?? 7, minute: c.minute ?? 0, label: label, weekdays: weekdays)
                            dismiss()
                        }
                    }
                }
            }
        }
    }
}

struct StopwatchView: View {
    @State private var running = false
    @State private var accumulated: TimeInterval = 0
    @State private var startedAt: Date?

    var body: some View {
        NavigationStack {
            VStack(spacing: 36) {
                TimelineView(.periodic(from: .now, by: 0.03)) { _ in
                    Text(format(elapsed))
                        .font(.system(size: 54, weight: .light, design: .rounded))
                        .monospacedDigit()
                }
                HStack(spacing: 24) {
                    Button(running ? "停止" : "開始") { toggle() }.buttonStyle(.borderedProminent)
                    Button("リセット") { running = false; accumulated = 0; startedAt = nil }.buttonStyle(.bordered)
                }
                Spacer()
            }
            .padding(.top, 70)
            .navigationTitle("ストップウォッチ")
        }
    }

    private var elapsed: TimeInterval { accumulated + (running ? Date().timeIntervalSince(startedAt ?? Date()) : 0) }
    private func toggle() {
        if running {
            accumulated += Date().timeIntervalSince(startedAt ?? Date())
            startedAt = nil
        } else { startedAt = Date() }
        running.toggle()
    }
    private func format(_ t: TimeInterval) -> String {
        let cs = Int(t * 100) % 100, sec = Int(t) % 60, min = Int(t) / 60
        return String(format: "%02d:%02d.%02d", min, sec, cs)
    }
}

struct WorldClockView: View {
    private let zones = ["Asia/Tokyo", "America/Los_Angeles", "America/New_York", "Europe/London", "Asia/Seoul"]
    var body: some View {
        NavigationStack {
            List(zones, id: \.self) { zone in
                HStack {
                    Text(city(zone))
                    Spacer()
                    TimelineView(.periodic(from: .now, by: 30)) { _ in
                        Text(time(zone)).font(.title2).monospacedDigit()
                    }
                }
            }.navigationTitle("世界時計")
        }
    }
    private func city(_ zone: String) -> String { zone.split(separator: "/").last.map(String.init)?.replacingOccurrences(of: "_", with: " ") ?? zone }
    private func time(_ zone: String) -> String {
        let f = DateFormatter(); f.dateFormat = "HH:mm"; f.timeZone = TimeZone(identifier: zone); return f.string(from: Date())
    }
}

struct AboutView: View {
    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                Image(systemName: "flame.circle.fill").font(.system(size: 90)).foregroundStyle(.red, .orange)
                Text("IGNIDO Wake").font(.largeTitle.bold())
                Text("iOS alpha 0.1.0").foregroundStyle(.secondary)
                Text("Android版v1.7.2を基準にしたiPhone移植の最初の実機テスト版。AlarmKitのアラーム、ストップウォッチ、世界時計を先行実装しています。")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary).padding()
                Spacer()
            }.padding(.top, 60).navigationTitle("IGNIDO")
        }
    }
}
