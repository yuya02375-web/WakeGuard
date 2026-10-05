from pathlib import Path

root=Path("ios")
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

# Version
p="IGNIDOWake/Info.plist"; s=read(p)
s=s.replace('<string>2.7.0</string>','<string>2.8.0</string>',1).replace('<string>170</string>','<string>180</string>',1)
write(p,s)
p="project.yml"; s=read(p)
s=s.replace('CFBundleShortVersionString: "2.7.0"','CFBundleShortVersionString: "2.8.0"',1).replace('CFBundleVersion: "170"','CFBundleVersion: "180"',1)
write(p,s)

backup=r'''import Foundation
import Security
import SwiftUI
import UniformTypeIdentifiers

/// Durable backup for user-owned state. Runtime alarm sessions, snooze bridges, and active timer
/// sessions are excluded so a reinstall cannot resurrect stale ringing state.
enum IgnidoBackupVault {
    static let formatVersion = 1
    static let markerKey = "ignido.backup.initialized.v1"
    static let cloudKey = "ignido.backup.state.v1"
    private static let keychainService = "jp.ignido.wake.backup"
    private static let keychainAccount = "full-state-v1"

    private static let excludedExact: Set<String> = [
        "ignido.alarm.testExpiresAt.v194",
        "ignido.alarm.escapeGuards.v196",
        "ignido.timeLog.activeFolder",
        "ignido.timeLog.activeStart",
        "ignido.stopwatch.v2",
        "ignido.timer.v2",
        "ignido.multiTimers.v1"
    ]

    private static func shouldBackUp(_ key: String) -> Bool {
        guard key.hasPrefix("ignido.") else { return false }
        if excludedExact.contains(key) { return false }
        if key.hasPrefix("ignido.intent.") { return false }
        return true
    }

    static func restoreIfNeeded() {
        let defaults = UserDefaults.standard
        guard defaults.bool(forKey: markerKey) == false else { return }

        var candidate = readKeychain()
        if candidate == nil {
            let cloud = NSUbiquitousKeyValueStore.default
            _ = cloud.synchronize()
            candidate = cloud.data(forKey: cloudKey)
        }
        if let candidate { _ = try? apply(candidate) }
        defaults.set(true, forKey: markerKey)
    }

    static func persistNow() {
        UserDefaults.standard.set(true, forKey: markerKey)
        guard let data = try? snapshotData() else { return }
        writeKeychain(data)
        // Opportunistic second copy when the signed app has iCloud KVS capability.
        if data.count < 900_000 {
            let cloud = NSUbiquitousKeyValueStore.default
            cloud.set(data, forKey: cloudKey)
            _ = cloud.synchronize()
        }
    }

    static func snapshotData() throws -> Data {
        var saved: [String: Any] = [:]
        for (key, value) in UserDefaults.standard.dictionaryRepresentation() where shouldBackUp(key) {
            let probe = [key: value]
            if PropertyListSerialization.propertyList(probe, isValidFor: .binary) { saved[key] = value }
        }
        let root: [String: Any] = [
            "format": formatVersion,
            "createdAt": Date(),
            "defaults": saved
        ]
        return try PropertyListSerialization.data(fromPropertyList: root, format: .xml, options: 0)
    }

    @discardableResult
    static func apply(_ data: Data) throws -> Int {
        guard let root = try PropertyListSerialization.propertyList(from: data, options: [], format: nil) as? [String: Any],
              let format = root["format"] as? Int,
              format == formatVersion,
              let saved = root["defaults"] as? [String: Any] else {
            throw BackupError.invalidFormat
        }

        let defaults = UserDefaults.standard
        for key in defaults.dictionaryRepresentation().keys where shouldBackUp(key) { defaults.removeObject(forKey: key) }
        var count = 0
        for (key, value) in saved where shouldBackUp(key) {
            defaults.set(value, forKey: key)
            count += 1
        }
        defaults.set(true, forKey: markerKey)
        persistNow()
        return count
    }

    private static func readKeychain() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { return nil }
        return item as? Data
    }

    private static func writeKeychain(_ data: Data) {
        let lookup: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount
        ]
        let update: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(lookup as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var add = lookup
            add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    enum BackupError: LocalizedError {
        case invalidFormat
        var errorDescription: String? { "IGNIDO Wakeのバックアップファイルではありません" }
    }
}

struct IgnidoBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [UTType(filenameExtension: "ignidobackup") ?? .data] }
    var data: Data

    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else { throw CocoaError(.fileReadCorruptFile) }
        self.data = data
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
'''
write("IGNIDOWake/BackupVault.swift",backup)

app=r'''import SwiftUI

@main
@MainActor
struct IGNIDOWakeApp: App {
    @StateObject private var alarmStore: AlarmStore
    @StateObject private var timerStore: TimerStore
    @StateObject private var multiTimerStore: MultiTimerStore
    @StateObject private var stopwatchStore: StopwatchStore
    @StateObject private var worldClockStore: WorldClockStore
    @StateObject private var streakStore: StreakStore
    @StateObject private var streakParityStore: StreakParityStore
    @StateObject private var languageStore: AppLanguageStore

    init() {
        // Restore before any store reads UserDefaults so reinstall starts with recovered state.
        IgnidoBackupVault.restoreIfNeeded()
        InitialState.migrate()
        IgnidoAppearance.configure()
        IgnidoAlarmSoundLibrary.installBundledSounds()

        _alarmStore = StateObject(wrappedValue: AlarmStore())
        _timerStore = StateObject(wrappedValue: TimerStore())
        _multiTimerStore = StateObject(wrappedValue: MultiTimerStore())
        _stopwatchStore = StateObject(wrappedValue: StopwatchStore())
        _worldClockStore = StateObject(wrappedValue: WorldClockStore())
        _streakStore = StateObject(wrappedValue: StreakStore())
        _streakParityStore = StateObject(wrappedValue: StreakParityStore())
        _languageStore = StateObject(wrappedValue: AppLanguageStore())
        IgnidoBackupVault.persistNow()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(alarmStore)
                .environmentObject(timerStore)
                .environmentObject(multiTimerStore)
                .environmentObject(stopwatchStore)
                .environmentObject(worldClockStore)
                .environmentObject(streakStore)
                .environmentObject(streakParityStore)
                .environmentObject(languageStore)
                .environment(\.locale, languageStore.locale)
                .tint(IgnidoTheme.ember)
                .preferredColorScheme(.dark)
                .task {
                    await alarmStore.bootstrap()
                    var ids = multiTimerStore.alarmKitIdentifiers
                    ids.formUnion(timerStore.alarmKitIdentifiers)
                    await alarmStore.reconcileDaemonOwnership(knownTimerIDs: ids)
                    IgnidoBackupVault.persistNow()
                }
        }
    }
}
'''
write("IGNIDOWake/IGNIDOWakeApp.swift",app)

# AlarmStore: reschedule restored alarms, snooze cancellation, backup persistence.
p="IGNIDOWake/AlarmStore.swift"; s=read(p)
old='''        await performV193MigrationIfNeeded()
        refreshSystemState()
    }
'''
new='''        await performV193MigrationIfNeeded()
        refreshSystemState()
        await restoreMissingEnabledAlarms()
    }
'''
if old not in s: raise SystemExit("AlarmStore bootstrap marker missing")
s=s.replace(old,new,1)

old='''    func snoozeSystemAlarm(_ item: WakeAlarm) -> Bool { refreshSystemState(); guard systemAlarmStates[item.id] == .alerting else { return false }; do { try AlarmManager.shared.countdown(id: item.id); refreshSystemState(); return true } catch { lastError = error.localizedDescription; return false } }
'''
new='''    func snoozeSystemAlarm(_ item: WakeAlarm) -> Bool { refreshSystemState(); guard systemAlarmStates[item.id] == .alerting else { return false }; do { try AlarmManager.shared.countdown(id: item.id); refreshSystemState(); return true } catch { lastError = error.localizedDescription; return false } }

    func isSystemAlarmSnoozing(id: UUID) -> Bool {
        let state = systemAlarmStates[id]
        return state == .countdown || state == .paused
    }

    var snoozingAlarms: [WakeAlarm] { alarms.filter { isSystemAlarmSnoozing(id: $0.id) } }

    /// Cancels only the snooze/countdown. Repeating schedules are immediately registered again.
    func cancelSnooze(_ item: WakeAlarm) async {
        refreshSystemState()
        guard isSystemAlarmSnoozing(id: item.id) else { return }
        try? AlarmManager.shared.cancel(id: item.id)
        systemAlarmStates[item.id] = nil
        if item.weekdays.isEmpty {
            if let index = alarms.firstIndex(where: { $0.id == item.id }) { alarms[index].enabled = false; save() }
            center.removePendingNotificationRequests(withIdentifiers: preAlertIdentifiers(for: item.id))
            center.removeDeliveredNotifications(withIdentifiers: preAlertIdentifiers(for: item.id))
        } else if item.enabled {
            await schedule(item, replacingExisting: false)
        }
        refreshSystemState()
    }

    func cancelAllSnoozes() async {
        let targets = snoozingAlarms
        for item in targets { await cancelSnooze(item) }
    }
'''
if old not in s: raise SystemExit("AlarmStore snooze marker missing")
s=s.replace(old,new,1)

marker='''    func reconcileDaemonOwnership(knownTimerIDs: Set<UUID>) async {
'''
insert='''    func restoreMissingEnabledAlarms() async {
        guard authorizationState == .authorized else { return }
        refreshSystemState()
        for item in alarms where item.enabled && systemAlarmStates[item.id] == nil {
            await schedule(item, replacingExisting: false)
        }
        refreshSystemState()
    }

    func reloadFromBackupAndReschedule() async {
        let oldIDs = alarms.map(\\.id)
        for id in oldIDs {
            try? AlarmManager.shared.cancel(id: id)
            center.removePendingNotificationRequests(withIdentifiers: preAlertIdentifiers(for: id))
            center.removeDeliveredNotifications(withIdentifiers: preAlertIdentifiers(for: id))
        }
        load()
        if authorizationState == .authorized {
            for item in alarms where item.enabled {
                try? AlarmManager.shared.cancel(id: item.id)
                await schedule(item, replacingExisting: false)
            }
        }
        refreshSystemState()
    }

'''
if marker not in s: raise SystemExit("AlarmStore reconcile marker missing")
s=s.replace(marker,insert+marker,1)

old='''    private func save() {
        if let data = try? JSONEncoder().encode(alarms) { UserDefaults.standard.set(data, forKey: storageKey) }
    }
'''
new='''    private func save() {
        if let data = try? JSONEncoder().encode(alarms) { UserDefaults.standard.set(data, forKey: storageKey) }
        IgnidoBackupVault.persistNow()
    }
'''
if old not in s: raise SystemExit("AlarmStore save marker missing")
s=s.replace(old,new,1)
write(p,s)

# Alarm list UI: per-snooze awake/cancel + cancel-all.
p="IGNIDOWake/AlarmViews.swift"; s=read(p)
s=s.replace('''struct AlarmListView: View {
    @EnvironmentObject private var store: AlarmStore
    @State private var showingAdd = false
''','''struct AlarmListView: View {
    @EnvironmentObject private var store: AlarmStore
    @EnvironmentObject private var streakStore: StreakStore
    @EnvironmentObject private var streakParityStore: StreakParityStore
    @State private var showingAdd = false
''',1)

old='''                    Section {
                        ForEach(store.alarms) { alarm in
                            NavigationLink {
                                AlarmEditorView(existing: alarm)
                            } label: {
                                AlarmRow(alarm: alarm)
                            }
                            .listRowBackground(IgnidoTheme.background)
                            .listRowSeparator(.visible)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) {
                                    store.delete(alarm)
                                } label: {
                                    Label("削除", systemImage: "trash")
                                }
                            }
                            .contextMenu {
                                Button(role: .destructive) {
                                    store.delete(alarm)
                                } label: {
                                    Label("削除", systemImage: "trash")
                                }
                            }
                            .accessibilityAction(named: "削除") {
                                store.delete(alarm)
                            }
                        }
                        .onDelete(perform: store.delete)
                    }
'''
new='''                    if store.snoozingAlarms.count > 1 {
                        Section {
                            HStack {
                                Label("スヌーズ中  \\(store.snoozingAlarms.count)件", systemImage: "repeat.circle")
                                Spacer()
                                Button("すべて取消") { Task { await store.cancelAllSnoozes() } }
                                    .buttonStyle(.borderless)
                                    .foregroundStyle(IgnidoTheme.ember)
                            }
                        }
                        .listRowBackground(IgnidoTheme.surface)
                    }
                    Section {
                        ForEach(store.alarms) { alarm in
                            VStack(spacing: 0) {
                                NavigationLink {
                                    AlarmEditorView(existing: alarm)
                                } label: {
                                    AlarmRow(alarm: alarm)
                                }
                                if store.isSystemAlarmSnoozing(id: alarm.id) {
                                    Divider().opacity(0.55)
                                    HStack(spacing: 12) {
                                        Label("スヌーズ中", systemImage: "repeat.circle.fill")
                                            .font(.caption.weight(.semibold))
                                            .foregroundStyle(IgnidoTheme.ember)
                                        Spacer()
                                        Button("起きた") {
                                            streakStore.recordWake()
                                            streakParityStore.recordWake(alarms: store.alarms)
                                            Task { await store.cancelSnooze(alarm) }
                                        }
                                        .buttonStyle(.borderless)
                                        .foregroundStyle(.green)
                                        Button("取消") { Task { await store.cancelSnooze(alarm) } }
                                            .buttonStyle(.borderless)
                                            .foregroundStyle(IgnidoTheme.ember)
                                    }
                                    .padding(.vertical, 9)
                                }
                            }
                            .listRowBackground(IgnidoTheme.background)
                            .listRowSeparator(.visible)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) {
                                    store.delete(alarm)
                                } label: {
                                    Label("削除", systemImage: "trash")
                                }
                            }
                            .contextMenu {
                                Button(role: .destructive) {
                                    store.delete(alarm)
                                } label: {
                                    Label("削除", systemImage: "trash")
                                }
                            }
                            .accessibilityAction(named: "削除") {
                                store.delete(alarm)
                            }
                        }
                        .onDelete(perform: store.delete)
                    }
'''
if old not in s: raise SystemExit("AlarmViews ForEach block missing")
s=s.replace(old,new,1)
write(p,s)

# Time-log backup snapshots after durable changes.
p="IGNIDOWake/TimeLogView.swift"; s=read(p)
old='''    private func save() {
        guard !loading, let data = try? JSONEncoder().encode(Payload(folders: folders, entries: entries)) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
'''
new='''    private func save() {
        guard !loading, let data = try? JSONEncoder().encode(Payload(folders: folders, entries: entries)) else { return }
        UserDefaults.standard.set(data, forKey: key)
        IgnidoBackupVault.persistNow()
    }
'''
if old not in s: raise SystemExit("TimeLog save block missing")
s=s.replace(old,new,1); write(p,s)

# Legacy streak dates are also durable.
p="IGNIDOWake/FeatureStores.swift"; s=read(p)
old='''    private func save() { if let data = try? JSONEncoder().encode(wakeDates) { UserDefaults.standard.set(data, forKey: key) } }
'''
new='''    private func save() { if let data = try? JSONEncoder().encode(wakeDates) { UserDefaults.standard.set(data, forKey: key) }; IgnidoBackupVault.persistNow() }
'''
if old not in s: raise SystemExit("FeatureStores streak save missing")
s=s.replace(old,new,1); write(p,s)

# Parity streak/growth/protection backup.
p="IGNIDOWake/StreakParity.swift"; s=read(p)
old='''        defaults.set(totalWakeups,forKey:prefix+"total")
    }
'''
new='''        defaults.set(totalWakeups,forKey:prefix+"total")
        IgnidoBackupVault.persistNow()
    }
'''
if old not in s: raise SystemExit("StreakParity save marker missing")
s=s.replace(old,new,1); write(p,s)

settings=r'''import SwiftUI
import AlarmKit
import UserNotifications
import UIKit
import UniformTypeIdentifiers

@MainActor
final class AppLanguageStore: ObservableObject {
    @Published var language: String {
        didSet { UserDefaults.standard.set(language, forKey: "ignido.app.language"); IgnidoBackupVault.persistNow() }
    }
    init() { language = UserDefaults.standard.string(forKey: "ignido.app.language") ?? "system" }
    var locale: Locale {
        switch language {
        case "ja": return Locale(identifier: "ja_JP")
        case "en": return Locale(identifier: "en_US")
        case "ko": return Locale(identifier: "ko_KR")
        default: return .autoupdatingCurrent
        }
    }
    var label: String {
        switch language { case "ja": return "日本語"; case "en": return "English"; case "ko": return "한국어"; default: return AppText.localized("システムに合わせる") }
    }
}

struct AppSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var languageStore: AppLanguageStore
    @EnvironmentObject private var alarmStore: AlarmStore
    @EnvironmentObject private var streakStore: StreakStore
    @EnvironmentObject private var streakParityStore: StreakParityStore
    @State private var notificationStatusKey = "確認中"
    @State private var backupDocument: IgnidoBackupDocument?
    @State private var showingBackupExporter = false
    @State private var showingBackupImporter = false
    @State private var backupMessage = ""
    @State private var showingBackupMessage = false

    var body: some View {
        NavigationStack {
            Form {
                Section("アプリ設定") {
                    Picker("アプリの言語", selection: $languageStore.language) {
                        Text("システムに合わせる").tag("system")
                        Text("日本語").tag("ja")
                        Text("English").tag("en")
                        Text("한국어").tag("ko")
                    }
                    NavigationLink("時計の詳細設定") { ClockSettingsView() }
                }
                Section("アラーム") {
                    HStack { Text("AlarmKit"); Spacer(); Text(AppText.localized(alarmKitStatusKey)).foregroundStyle(statusColor(alarmKitStatusKey)) }
                    HStack { Text("通知"); Spacer(); Text(AppText.localized(notificationStatusKey)).foregroundStyle(statusColor(notificationStatusKey)) }
                    Button("アラーム権限を確認 / 許可") { Task { _ = await alarmStore.requestAuthorization() } }
                    Button("iPhoneのIGNIDO Wake設定を開く") { openAppSettings() }
                }
                Section("バックアップ") {
                    Text("アラーム・時間記録・ストリークを自動保存します。アプリ削除に備えて、手動バックアップをファイルにも保存できます。")
                        .font(.caption)
                        .foregroundStyle(IgnidoTheme.secondaryText)
                    Button("バックアップを書き出す") { prepareBackupExport() }
                    Button("バックアップを読み込む") { showingBackupImporter = true }
                }
                Section("iOSでAndroidと異なる部分") {
                    Text("iOSのシステムアラーム画面・ロック画面表示・システム振動はAlarmKitが管理します。アプリを開いた後の動画・音声・解除ミッション・強い/不規則の振動はIGNIDO Wake側で制御します。")
                        .font(.caption).foregroundStyle(IgnidoTheme.secondaryText)
                }
            }
            .scrollContentBackground(.hidden)
            .background(IgnidoTheme.background)
            .foregroundStyle(IgnidoTheme.text)
            .tint(IgnidoTheme.ember)
            .navigationTitle("設定")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完了") { dismiss() } } }
        }
        .task { await refreshNotificationStatus() }
        .fileExporter(
            isPresented: $showingBackupExporter,
            document: backupDocument,
            contentType: IgnidoBackupDocument.readableContentTypes[0],
            defaultFilename: "IGNIDO-Wake-backup.ignidobackup"
        ) { result in
            if case .failure(let error) = result { showBackupMessage(error.localizedDescription) }
            else { showBackupMessage("バックアップを書き出しました") }
        }
        .fileImporter(
            isPresented: $showingBackupImporter,
            allowedContentTypes: IgnidoBackupDocument.readableContentTypes,
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                restoreBackup(from: url)
            case .failure(let error): showBackupMessage(error.localizedDescription)
            }
        }
        .alert("バックアップ", isPresented: $showingBackupMessage) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(backupMessage)
        }
    }

    private func prepareBackupExport() {
        do {
            backupDocument = IgnidoBackupDocument(data: try IgnidoBackupVault.snapshotData())
            IgnidoBackupVault.persistNow()
            showingBackupExporter = true
        } catch { showBackupMessage(error.localizedDescription) }
    }

    private func restoreBackup(from url: URL) {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            let count = try IgnidoBackupVault.apply(data)
            languageStore.language = UserDefaults.standard.string(forKey: "ignido.app.language") ?? "system"
            Task { @MainActor in
                await alarmStore.reloadFromBackupAndReschedule()
                streakStore.reload()
                streakParityStore.reload()
                showBackupMessage("バックアップを復元しました（\(count)項目）")
            }
        } catch { showBackupMessage(error.localizedDescription) }
    }

    private func showBackupMessage(_ message: String) {
        backupMessage = message
        showingBackupMessage = true
    }

    private var alarmKitStatusKey: String {
        switch alarmStore.authorizationState { case .authorized: return "設定済み"; case .denied: return "許可されていません"; default: return "確認が必要です" }
    }
    private func statusColor(_ s:String)->Color { s=="設定済み" ? .green : IgnidoTheme.hot }
    private func refreshNotificationStatus() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        notificationStatusKey = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional ? "設定済み" : "確認が必要です"
    }
    private func openAppSettings() {
        guard let url=URL(string:UIApplication.openSettingsURLString) else{return}
        UIApplication.shared.open(url)
    }
}
'''
write("IGNIDOWake/AppSettingsView.swift",settings)

assert '<string>2.8.0</string>' in read("IGNIDOWake/Info.plist")
assert 'IgnidoBackupVault.restoreIfNeeded()' in read("IGNIDOWake/IGNIDOWakeApp.swift")
assert 'SecItemCopyMatching' in read("IGNIDOWake/BackupVault.swift")
assert 'cancelAllSnoozes' in read("IGNIDOWake/AlarmStore.swift")
assert 'すべて取消' in read("IGNIDOWake/AlarmViews.swift")
assert 'バックアップを書き出す' in read("IGNIDOWake/AppSettingsView.swift")
print("iOS 2.8.0 backup + snooze cancellation patch applied")
