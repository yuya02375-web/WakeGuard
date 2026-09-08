from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# Version 1.8.8 / build 17.
plist = root / 'Info.plist'
s = plist.read_text(encoding='utf-8')
s = re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+(</string>)', r'\g<1>1.8.8\g<2>', s, count=1)
s = re.sub(r'(<key>CFBundleVersion</key>\s*<string>)[^<]+(</string>)', r'\g<1>17\g<2>', s, count=1)
plist.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = re.sub(r'CFBundleShortVersionString: "[^"]+"', 'CFBundleShortVersionString: "1.8.8"', s)
s = re.sub(r'CFBundleVersion: "[^"]+"', 'CFBundleVersion: "17"', s)
project.write_text(s, encoding='utf-8')

# Replace the catalog with a country-aware index. The v1.8.7 catalog only
# indexed GeoNames city aliases + ISO country codes, so a broad Japanese
# country query such as "アメリカ" could accidentally match only a city alias
# like American Fork. This version indexes localized country names and aliases
# before city matching, and adds a conservative fuzzy-country fallback.
catalog = root / 'WorldCityCatalog.swift'
catalog.write_text(r'''import Foundation

struct IOSWorldCityEntry: Identifiable, Hashable, Sendable {
    let name: String
    let asciiName: String
    let countryCode: String
    let zoneId: String
    let population: Int64
    let search: String
    let nameNorm: String
    let asciiNorm: String
    var id: String { zoneId + "\u{1F}" + name }
}

final class IOSWorldCityCatalog: @unchecked Sendable {
    static let shared = IOSWorldCityCatalog()
    private let lock = NSLock()
    private var entries: [IOSWorldCityEntry]?
    private var queryCache: [String: [IOSWorldCityEntry]] = [:]
    private var cacheOrder: [String] = []
    private var cachedCountryIndex: [String: [String]]?
    private init() {}

    func preload() { _ = allEntries() }

    func normalize(_ raw: String) -> String {
        let compatibility = raw.precomposedStringWithCompatibilityMapping
        var kanaScalars = String.UnicodeScalarView()
        kanaScalars.reserveCapacity(compatibility.unicodeScalars.count)
        for scalar in compatibility.unicodeScalars {
            let v = scalar.value
            if v >= 0x3041 && v <= 0x3096, let mapped = UnicodeScalar(v + 0x60) {
                kanaScalars.append(mapped)
            } else {
                kanaScalars.append(scalar)
            }
        }
        let folded = String(kanaScalars).folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: Locale(identifier: "en_US_POSIX"))
        var out = String.UnicodeScalarView()
        out.reserveCapacity(folded.unicodeScalars.count)
        var lastWasSpace = true
        for scalar in folded.unicodeScalars {
            if CharacterSet.alphanumerics.contains(scalar) {
                out.append(scalar)
                lastWasSpace = false
            } else if !lastWasSpace {
                out.append(" ")
                lastWasSpace = true
            }
        }
        return String(out).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func search(_ raw: String, limit: Int = 80) -> [IOSWorldCityEntry] {
        let q = normalize(raw)
        let cacheKey = q + "\u{1E}" + String(limit)
        lock.lock()
        if let cached = queryCache[cacheKey] { lock.unlock(); return cached }
        lock.unlock()

        if q.isEmpty {
            let popular = Array(allEntries().prefix(limit))
            remember(popular, for: cacheKey)
            return popular
        }

        let countryMatches = matchedCountries(for: q)
        var scored: [(IOSWorldCityEntry, Int)] = []
        scored.reserveCapacity(max(256, limit * 4))

        for e in allEntries() {
            var best: Int? = nil
            if let countryScore = countryMatches[e.countryCode] {
                best = countryScore
            }
            let cityScore: Int?
            if e.nameNorm == q || e.asciiNorm == q { cityScore = 10 }
            else if e.nameNorm.hasPrefix(q) || e.asciiNorm.hasPrefix(q) { cityScore = 11 }
            else if e.search.contains(q) { cityScore = 12 }
            else { cityScore = nil }
            if let cityScore {
                best = min(best ?? cityScore, cityScore)
            }
            if let best { scored.append((e, best)) }
        }

        scored.sort {
            if $0.1 != $1.1 { return $0.1 < $1.1 }
            if $0.0.population != $1.0.population { return $0.0.population > $1.0.population }
            return $0.0.name.localizedCaseInsensitiveCompare($1.0.name) == .orderedAscending
        }

        var out: [IOSWorldCityEntry] = []
        var seen = Set<String>()
        for pair in scored {
            if seen.insert(pair.0.id).inserted { out.append(pair.0) }
            if out.count >= limit { break }
        }

        // Keep raw IANA identifiers searchable too.
        if out.count < limit {
            for id in TimeZone.knownTimeZoneIdentifiers {
                let label = id.split(separator: "/").last.map(String.init)?.replacingOccurrences(of: "_", with: " ") ?? id
                let searchable = normalize(id + " " + label)
                guard searchable.contains(q) else { continue }
                let item = IOSWorldCityEntry(name: label, asciiName: label, countryCode: "", zoneId: id, population: -1, search: searchable, nameNorm: normalize(label), asciiNorm: normalize(label))
                if seen.insert(item.id).inserted { out.append(item) }
                if out.count >= limit { break }
            }
        }

        remember(out, for: cacheKey)
        return out
    }

    private func matchedCountries(for q: String) -> [String: Int] {
        let index = countryIndex()
        var direct: [String: Int] = [:]
        for (code, terms) in index {
            var best: Int? = nil
            for term in terms where !term.isEmpty {
                let score: Int?
                if term == q { score = 0 }
                else if term.hasPrefix(q) || q.hasPrefix(term) { score = 1 }
                else if term.contains(q) || q.contains(term) { score = 2 }
                else { score = nil }
                if let score { best = min(best ?? score, score) }
            }
            if let best { direct[code] = best }
        }
        if !direct.isEmpty { return direct }

        // Conservative typo repair for country names only. This intentionally
        // does not fuzzy-match every city in the 30k+ city catalog.
        let qCount = q.unicodeScalars.count
        guard qCount >= 4 else { return [:] }
        let threshold = qCount >= 7 ? 2 : 1
        var fuzzy: [String: Int] = [:]
        var globalBest = Int.max
        for (code, terms) in index {
            var codeBest = Int.max
            for term in terms {
                let n = term.unicodeScalars.count
                guard abs(n - qCount) <= threshold else { continue }
                let d = editDistance(q, term, cutoff: threshold)
                codeBest = min(codeBest, d)
            }
            if codeBest <= threshold {
                globalBest = min(globalBest, codeBest)
                fuzzy[code] = codeBest
            }
        }
        return fuzzy.filter { $0.value == globalBest }.mapValues { _ in 3 }
    }

    private func countryIndex() -> [String: [String]] {
        lock.lock()
        if let cachedCountryIndex { lock.unlock(); return cachedCountryIndex }
        lock.unlock()

        let codes = Set(allEntries().map(\.countryCode).filter { !$0.isEmpty })
        let locales = [Locale(identifier: "ja_JP"), Locale(identifier: "en_US"), Locale(identifier: "ko_KR"), Locale.current]
        let manual: [String: [String]] = [
            "US": ["US", "USA", "America", "United States", "United States of America", "アメリカ", "アメリカ合衆国", "米国", "미국", "아메리카"],
            "GB": ["UK", "U.K.", "Britain", "Great Britain", "United Kingdom", "イギリス", "英国", "영국"],
            "KR": ["Korea", "South Korea", "Republic of Korea", "韓国", "大韓民国", "한국", "대한민국"],
            "JP": ["Japan", "日本", "にほん", "にっぽん", "일본"],
            "AU": ["Australia", "オーストラリア", "豪州", "호주", "오스트레일리아"],
            "CA": ["Canada", "カナダ", "캐나다"]
        ]
        var built: [String: [String]] = [:]
        for code in codes {
            var terms = [normalize(code)]
            for locale in locales {
                if let name = locale.localizedString(forRegionCode: code) { terms.append(normalize(name)) }
            }
            if let aliases = manual[code] { terms.append(contentsOf: aliases.map(normalize)) }
            built[code] = Array(Set(terms.filter { !$0.isEmpty }))
        }
        lock.lock()
        if cachedCountryIndex == nil { cachedCountryIndex = built }
        let result = cachedCountryIndex ?? built
        lock.unlock()
        return result
    }

    private func editDistance(_ a: String, _ b: String, cutoff: Int) -> Int {
        let aa = Array(a.unicodeScalars)
        let bb = Array(b.unicodeScalars)
        if abs(aa.count - bb.count) > cutoff { return cutoff + 1 }
        if aa.isEmpty { return min(bb.count, cutoff + 1) }
        var previous = Array(0...bb.count)
        for i in 1...aa.count {
            var current = Array(repeating: 0, count: bb.count + 1)
            current[0] = i
            var rowBest = current[0]
            for j in 1...bb.count {
                let cost = aa[i - 1] == bb[j - 1] ? 0 : 1
                current[j] = min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                rowBest = min(rowBest, current[j])
            }
            if rowBest > cutoff { return cutoff + 1 }
            previous = current
        }
        return previous[bb.count]
    }

    private func remember(_ value: [IOSWorldCityEntry], for key: String) {
        lock.lock(); defer { lock.unlock() }
        queryCache[key] = value
        cacheOrder.removeAll { $0 == key }
        cacheOrder.append(key)
        while cacheOrder.count > 48 {
            let old = cacheOrder.removeFirst()
            queryCache.removeValue(forKey: old)
        }
    }

    private func allEntries() -> [IOSWorldCityEntry] {
        lock.lock()
        if let entries { lock.unlock(); return entries }
        lock.unlock()

        var built: [IOSWorldCityEntry] = []
        if let url = Bundle.main.url(forResource: "world_cities", withExtension: "tsv"),
           let text = try? String(contentsOf: url, encoding: .utf8) {
            built.reserveCapacity(36_000)
            for line in text.split(whereSeparator: \.isNewline) {
                let p = line.split(separator: "\t", maxSplits: 5, omittingEmptySubsequences: false)
                guard p.count >= 5 else { continue }
                let name = String(p[0]), ascii = String(p[1]), cc = String(p[2]), zone = String(p[3])
                let pop = Int64(p[4]) ?? 0
                let alts = p.count > 5 ? String(p[5]) : ""
                let nameNorm = normalize(name), asciiNorm = normalize(ascii)
                let search = normalize(name + " " + ascii + " " + alts + " " + cc + " " + zone)
                built.append(IOSWorldCityEntry(name: name, asciiName: ascii, countryCode: cc, zoneId: zone, population: pop, search: search, nameNorm: nameNorm, asciiNorm: asciiNorm))
            }
        }
        lock.lock()
        if entries == nil { entries = built }
        let result = entries ?? built
        lock.unlock()
        return result
    }
}
''', encoding='utf-8')

clock = root / 'ClockViews.swift'
s = clock.read_text(encoding='utf-8')
for import_line in ['import MapKit', 'import WebKit', 'import UIKit']:
    if import_line not in s:
        s = s.replace('import UniformTypeIdentifiers\n', 'import UniformTypeIdentifiers\n' + import_line + '\n', 1)

start = s.index('private struct AddTimeZoneView: View {')
end = s.index('\nprivate struct FullWorldClockView: View {', start)

new_view = r'''private struct AddTimeZoneView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WorldClockStore
    @State private var query = ""
    @State private var results: [IOSWorldCityEntry] = []
    @State private var searching = false
    @State private var onlineSearching = false
    @State private var generation = 0
    @State private var showGoogle = false
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
            .sheet(isPresented: $showGoogle) {
                IOSGoogleSearchPanel(initialQuery: query) { text in
                    query = text
                    searchFocused = true
                    runSearch(immediate: true)
                }
                .presentationDetents([.fraction(0.78), .large])
                .presentationDragIndicator(.visible)
            }
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
            .background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(IgnidoTheme.border, lineWidth: 1))

            HStack(spacing: 8) {
                Text(query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "主要都市" : "検索結果")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(IgnidoTheme.secondaryText)
                if searching { ProgressView().controlSize(.small) }
                if onlineSearching {
                    Label("オンライン補助", systemImage: "network")
                        .font(.caption2)
                        .foregroundStyle(IgnidoTheme.secondaryText)
                }
                Spacer()
                Text("オフライン + オンライン")
                    .font(.caption2)
                    .foregroundStyle(IgnidoTheme.secondaryText)
            }

            if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                HStack(spacing: 10) {
                    Button {
                        searchFocused = false
                        showGoogle = true
                    } label: {
                        Label("Googleで確認", systemImage: "globe")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(IgnidoTheme.ember)

                    Button {
                        useClipboardText()
                    } label: {
                        Label("コピーした文字を使う", systemImage: "doc.on.clipboard")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
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
                if !searching && !onlineSearching && results.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 32))
                            .foregroundStyle(IgnidoTheme.secondaryText)
                        Text("見つかりません")
                            .font(.headline)
                            .foregroundStyle(IgnidoTheme.text)
                        Text("候補がない場合は「Googleで確認」で調べ、名前をコピーしてWakeGuardへ戻せます。")
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
            if alreadyAdded { return }
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
            .background(IgnidoTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
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
        onlineSearching = false

        Task {
            if !immediate && !currentQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                try? await Task.sleep(for: .milliseconds(18))
            }
            guard currentGeneration == generation else { return }

            let local = await Task.detached(priority: .userInitiated) {
                IOSWorldCityCatalog.shared.search(currentQuery, limit: 80)
            }.value
            guard currentGeneration == generation, currentQuery == query else { return }
            results = local
            searching = false

            let trimmed = currentQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return }
            onlineSearching = true
            try? await Task.sleep(for: .milliseconds(350))
            guard currentGeneration == generation, currentQuery == query else { return }

            let remote = await IOSOnlineWorldClockSearch.search(trimmed)
            guard currentGeneration == generation, currentQuery == query else { return }
            results = merge(local: results, remote: remote, limit: 80)
            onlineSearching = false
        }
    }

    private func merge(local: [IOSWorldCityEntry], remote: [IOSWorldCityEntry], limit: Int) -> [IOSWorldCityEntry] {
        var out: [IOSWorldCityEntry] = []
        var seen = Set<String>()
        for item in local + remote {
            if seen.insert(item.id).inserted { out.append(item) }
            if out.count >= limit { break }
        }
        return out
    }

    private func useClipboardText() {
        guard let text = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return }
        query = text
        searchFocused = true
        runSearch(immediate: true)
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

@MainActor
private enum IOSOnlineWorldClockSearch {
    static func search(_ raw: String) async -> [IOSWorldCityEntry] {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = raw
        let search = MKLocalSearch(request: request)
        let response: MKLocalSearch.Response? = await withCheckedContinuation { continuation in
            search.start { response, _ in
                continuation.resume(returning: response)
            }
        }
        guard let response else { return [] }

        var out: [IOSWorldCityEntry] = []
        var seen = Set<String>()
        for item in response.mapItems {
            let placemark = item.placemark
            guard let zone = placemark.timeZone?.identifier else { continue }
            let name = placemark.locality ?? placemark.subAdministrativeArea ?? placemark.administrativeArea ?? item.name ?? raw
            let code = placemark.isoCountryCode ?? ""
            let normalized = IOSWorldCityCatalog.shared.normalize(name + " " + code + " " + zone)
            let entry = IOSWorldCityEntry(
                name: name,
                asciiName: name,
                countryCode: code,
                zoneId: zone,
                population: -2,
                search: normalized,
                nameNorm: IOSWorldCityCatalog.shared.normalize(name),
                asciiNorm: IOSWorldCityCatalog.shared.normalize(name)
            )
            if seen.insert(entry.id).inserted { out.append(entry) }
            if out.count >= 24 { break }
        }
        return out
    }
}

private struct IOSGoogleSearchPanel: View {
    @Environment(\.dismiss) private var dismiss
    @State private var webQuery: String
    @State private var loadedQuery: String
    let onUseText: (String) -> Void

    init(initialQuery: String, onUseText: @escaping (String) -> Void) {
        _webQuery = State(initialValue: initialQuery)
        _loadedQuery = State(initialValue: initialQuery)
        self.onUseText = onUseText
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    TextField("Google検索", text: $webQuery)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .submitLabel(.search)
                        .onSubmit { submitGoogleSearch() }
                    Button("検索") { submitGoogleSearch() }
                        .buttonStyle(.borderedProminent)
                        .tint(IgnidoTheme.ember)
                }
                .padding(12)
                .background(IgnidoTheme.background)

                IOSGoogleWebView(query: loadedQuery)
                    .id(loadedQuery)
            }
            .navigationTitle("Googleで確認")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("閉じる") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("コピーした文字を使う") {
                        guard let text = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return }
                        onUseText(text)
                        dismiss()
                    }
                }
            }
        }
    }

    private func submitGoogleSearch() {
        let trimmed = webQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        loadedQuery = trimmed
    }
}

private struct IOSGoogleWebView: UIViewRepresentable {
    let query: String

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.allowsBackForwardNavigationGestures = true
        load(query, in: webView)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {}

    private func load(_ query: String, in webView: WKWebView) {
        var components = URLComponents(string: "https://www.google.com/search")!
        components.queryItems = [URLQueryItem(name: "q", value: query)]
        guard let url = components.url else { return }
        webView.load(URLRequest(url: url))
    }
}
'''

s = s[:start] + new_view + s[end:]
clock.write_text(s, encoding='utf-8')

final_clock = clock.read_text(encoding='utf-8')
required = [
    'import MapKit',
    'import WebKit',
    'Googleで確認',
    'コピーした文字を使う',
    'IOSOnlineWorldClockSearch.search(trimmed)',
    'MKLocalSearch.Request()',
    'WKWebView',
    '.presentationDetents([.fraction(0.78), .large])',
    'Task.sleep(for: .milliseconds(350))',
]
for token in required:
    if token not in final_clock:
        raise SystemExit(f'missing iOS 1.8.8 search fallback token: {token}')

final_catalog = catalog.read_text(encoding='utf-8')
for token in ['"US": ["US", "USA", "America"', 'Locale(identifier: "ja_JP")', 'matchedCountries(for: q)', 'editDistance(q, term, cutoff: threshold)']:
    if token not in final_catalog:
        raise SystemExit(f'missing iOS 1.8.8 country-search token: {token}')

if '<string>1.8.8</string>' not in plist.read_text(encoding='utf-8') or '<string>17</string>' not in plist.read_text(encoding='utf-8'):
    raise SystemExit('Info.plist version/build not stamped to 1.8.8/17')
if 'CFBundleShortVersionString: "1.8.8"' not in project.read_text(encoding='utf-8') or 'CFBundleVersion: "17"' not in project.read_text(encoding='utf-8'):
    raise SystemExit('project.yml version/build not stamped to 1.8.8/17')

print('IGNIDO Wake iOS 1.8.8 country-aware + online + in-app Google search patch applied')
