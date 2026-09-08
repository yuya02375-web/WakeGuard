from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# Version 1.8.7 / build 16.
plist = root / 'Info.plist'
s = plist.read_text(encoding='utf-8')
s = re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+(</string>)', r'\g<1>1.8.7\g<2>', s, count=1)
s = re.sub(r'(<key>CFBundleVersion</key>\s*<string>)[^<]+(</string>)', r'\g<1>16\g<2>', s, count=1)
plist.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = re.sub(r'CFBundleShortVersionString: "[^"]+"', 'CFBundleShortVersionString: "1.8.7"', s)
s = re.sub(r'CFBundleVersion: "[^"]+"', 'CFBundleVersion: "16"', s)
project.write_text(s, encoding='utf-8')

clock = root / 'ClockViews.swift'
s = clock.read_text(encoding='utf-8')
start = s.index('private struct AddTimeZoneView: View {')
end = s.index('\nprivate struct FullWorldClockView: View {', start)

new_view = r'''private struct AddTimeZoneView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WorldClockStore
    @State private var query = ""
    @State private var results: [IOSWorldCityEntry] = []
    @State private var searching = false
    @State private var generation = 0
    @FocusState private var searchFocused: Bool

    var body: some View {
        NavigationStack {
            ZStack {
                IgnidoScreenBackground()
                VStack(spacing: 0) {
                    searchHeader
                    Divider().overlay(IgnidoTheme.border)
                    resultList
                }
            }
            .navigationTitle("都市 / タイムゾーンを追加")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("閉じる") { searchFocused = false; dismiss() }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完了") { searchFocused = false }
                }
            }
            .task { runSearch(immediate: true) }
            .onChange(of: query) { _, _ in runSearch(immediate: false) }
        }
    }

    private var searchHeader: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.headline)
                    .foregroundStyle(IgnidoTheme.secondaryText)

                TextField("日本語・English・한국어で都市 / 国 / タイムゾーンを検索", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .submitLabel(.search)
                    .focused($searchFocused)
                    .foregroundStyle(IgnidoTheme.text)
                    .onSubmit { runSearch(immediate: true) }

                if !query.isEmpty {
                    Button {
                        query = ""
                        searchFocused = true
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(IgnidoTheme.secondaryText)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 50)
            .background(IgnidoTheme.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(IgnidoTheme.border, lineWidth: 1))

            HStack(spacing: 8) {
                Text(query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "主要都市" : "検索結果")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(IgnidoTheme.secondaryText)
                if searching { ProgressView().controlSize(.small) }
                Spacer()
                Text("都市・国・地域・IANAタイムゾーン")
                    .font(.caption2)
                    .foregroundStyle(IgnidoTheme.secondaryText)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 10)
        .background(IgnidoTheme.background.opacity(0.98))
    }

    private var resultList: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if !searching && results.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 32))
                            .foregroundStyle(IgnidoTheme.secondaryText)
                        Text("見つかりません")
                            .font(.headline)
                            .foregroundStyle(IgnidoTheme.text)
                        Text("日本語・English・한국어の都市名、国名、地域名、またはタイムゾーン名で検索できます。")
                            .font(.caption)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(IgnidoTheme.secondaryText)
                    }
                    .padding(.vertical, 48)
                    .padding(.horizontal, 28)
                } else {
                    ForEach(results) { entry in
                        searchResultRow(entry)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func searchResultRow(_ entry: IOSWorldCityEntry) -> some View {
        let alreadyAdded = store.items.contains(where: { $0.timeZoneIdentifier == entry.zoneId })
        return Button {
            if alreadyAdded {
                return
            }
            store.add(entry.zoneId, displayName: entry.name)
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 7) {
                        Text(entry.name)
                            .font(.headline)
                            .foregroundStyle(IgnidoTheme.text)
                            .lineLimit(1)
                        if !entry.countryCode.isEmpty {
                            Text(countryLabel(entry.countryCode))
                                .font(.caption)
                                .foregroundStyle(IgnidoTheme.secondaryText)
                                .lineLimit(1)
                        }
                    }
                    Text(entry.zoneId)
                        .font(.caption.monospaced())
                        .foregroundStyle(IgnidoTheme.secondaryText)
                        .lineLimit(1)
                    Text(zoneDetail(entry.zoneId))
                        .font(.caption2)
                        .foregroundStyle(IgnidoTheme.secondaryText)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 6) {
                    Text(localTime(entry.zoneId))
                        .font(.title3.monospacedDigit().weight(.medium))
                        .foregroundStyle(IgnidoTheme.text)
                    Image(systemName: alreadyAdded ? "checkmark.circle.fill" : "plus.circle.fill")
                        .font(.title2)
                        .foregroundStyle(alreadyAdded ? Color.green : IgnidoTheme.ember)
                }
            }
            .padding(14)
            .background(IgnidoTheme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(alreadyAdded ? Color.green.opacity(0.45) : IgnidoTheme.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(alreadyAdded)
        .accessibilityLabel(alreadyAdded ? "\(entry.name)、追加済み" : "\(entry.name)を追加")
    }

    private func runSearch(immediate: Bool) {
        generation += 1
        let currentGeneration = generation
        let currentQuery = query
        searching = true
        Task {
            if !immediate && !currentQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                try? await Task.sleep(for: .milliseconds(18))
            }
            guard currentGeneration == generation else { return }
            let found = await Task.detached(priority: .userInitiated) {
                IOSWorldCityCatalog.shared.search(currentQuery, limit: 80)
            }.value
            guard currentGeneration == generation, currentQuery == query else { return }
            results = found
            searching = false
        }
    }

    private func countryLabel(_ code: String) -> String {
        Locale.current.localizedString(forRegionCode: code) ?? code
    }

    private func localTime(_ identifier: String) -> String {
        guard let zone = TimeZone(identifier: identifier) else { return "--:--" }
        let f = DateFormatter()
        f.timeZone = zone
        f.dateFormat = "HH:mm"
        return f.string(from: Date())
    }

    private func zoneDetail(_ identifier: String) -> String {
        guard let zone = TimeZone(identifier: identifier) else { return identifier }
        let now = Date()
        let offset = zone.secondsFromGMT(for: now)
        let sign = offset >= 0 ? "+" : "-"
        let absolute = abs(offset)
        let h = absolute / 3600
        let m = (absolute % 3600) / 60
        let dst = zone.isDaylightSavingTime(for: now) ? "  DST" : ""
        return String(format: "UTC%@%02d:%02d%@", sign, h, m, dst)
    }
}
'''

s = s[:start] + new_view + s[end:]
clock.write_text(s, encoding='utf-8')

# Validate that the iOS view now follows the Android search flow rather than the
# old separate SwiftUI .searchable-only interaction.
final_clock = clock.read_text(encoding='utf-8')
required = [
    'TextField("日本語・English・한국어で都市 / 国 / タイムゾーンを検索"',
    '都市・国・地域・IANAタイムゾーン',
    'IOSWorldCityCatalog.shared.search(currentQuery, limit: 80)',
    'Task.sleep(for: .milliseconds(18))',
    'store.add(entry.zoneId, displayName: entry.name)',
    'checkmark.circle.fill',
    'plus.circle.fill',
    '.scrollDismissesKeyboard(.interactively)',
    'Button("完了") { searchFocused = false }',
]
for token in required:
    if token not in final_clock:
        raise SystemExit(f'missing iOS 1.8.7 Android-search parity token: {token}')

segment = final_clock[final_clock.index('private struct AddTimeZoneView: View {'):final_clock.index('\nprivate struct FullWorldClockView: View {')]
if '.searchable(' in segment:
    raise SystemExit('old iOS searchable modifier remains in AddTimeZoneView')

if '<string>1.8.7</string>' not in plist.read_text(encoding='utf-8') or '<string>16</string>' not in plist.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist version/build not stamped to 1.8.7/16')
if 'CFBundleShortVersionString: "1.8.7"' not in project.read_text(encoding='utf-8') or 'CFBundleVersion: "16"' not in project.read_text(encoding='utf-8'):
    raise SystemExit('project.yml version/build not stamped to 1.8.7/16')

print('IGNIDO Wake iOS 1.8.7 Android-style world clock search parity patch applied')
