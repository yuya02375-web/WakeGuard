import SwiftUI
import AlarmKit
import UserNotifications
import UIKit

@MainActor
final class AppLanguageStore: ObservableObject {
    @Published var language: String {
        didSet { UserDefaults.standard.set(language, forKey: "ignido.app.language") }
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
        switch language { case "ja": return "日本語"; case "en": return "English"; case "ko": return "한국어"; default: return "システムに合わせる" }
    }
}

struct AppSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var languageStore: AppLanguageStore
    @EnvironmentObject private var alarmStore: AlarmStore
    @State private var notificationStatus = "確認中"

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
                    HStack { Text("AlarmKit"); Spacer(); Text(alarmKitStatus).foregroundStyle(statusColor(alarmKitStatus)) }
                    HStack { Text("通知"); Spacer(); Text(notificationStatus).foregroundStyle(statusColor(notificationStatus)) }
                    Button("アラーム権限を確認 / 許可") { Task { _ = await alarmStore.requestAuthorization() } }
                    Button("iPhoneのIGNIDO Wake設定を開く") { openAppSettings() }
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
    }

    private var alarmKitStatus: String {
        switch alarmStore.authorizationState { case .authorized: return "設定済み"; case .denied: return "許可されていません"; default: return "確認が必要です" }
    }
    private func statusColor(_ s:String)->Color { s=="設定済み" ? .green : IgnidoTheme.hot }
    private func refreshNotificationStatus() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        notificationStatus = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional ? "設定済み" : "確認が必要です"
    }
    private func openAppSettings() {
        guard let url=URL(string:UIApplication.openSettingsURLString) else{return}
        UIApplication.shared.open(url)
    }
}
