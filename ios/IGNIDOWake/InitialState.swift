import Foundation

enum InitialState {
    static let migrationKey = "ignido.ios.v176.cleanInitialState"
    static let alarmStorageKey = "ignido.ios.alarms.v1"
    static let protectionBalanceKey = "ignido.streak.protection.balance.v1"
    static let protectionAutoKey = "ignido.streak.protection.auto.v1"
    static let protectionMonthKey = "ignido.streak.protection.month.v1"
    static let protectionStartKey = "ignido.streak.protection.start.v1"

    static func migrate() {
        let defaults = UserDefaults.standard
        guard !defaults.bool(forKey: migrationKey) else { return }

        // A fresh iOS install has no alarm rows at all. Persist the empty state explicitly
        // so no future fallback or sample alarm can materialize at launch.
        if defaults.data(forKey: alarmStorageKey) == nil,
           let empty = try? JSONEncoder().encode([WakeAlarm]()) {
            defaults.set(empty, forKey: alarmStorageKey)
        }

        // Keep the streak-protection initial state aligned with Android v1.7.6:
        // 0 / 3 on first use, automatic protection enabled, with future monthly grants
        // handled by the protection feature rather than preloading two days.
        if defaults.object(forKey: protectionBalanceKey) == nil {
            defaults.set(0, forKey: protectionBalanceKey)
        }
        if defaults.object(forKey: protectionAutoKey) == nil {
            defaults.set(true, forKey: protectionAutoKey)
        }
        if defaults.string(forKey: protectionMonthKey) == nil {
            let f = DateFormatter()
            f.calendar = Calendar(identifier: .gregorian)
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyy-MM"
            defaults.set(f.string(from: Date()), forKey: protectionMonthKey)
        }
        if defaults.object(forKey: protectionStartKey) == nil {
            defaults.set(Date().timeIntervalSince1970, forKey: protectionStartKey)
        }

        defaults.set(true, forKey: migrationKey)
    }
}
