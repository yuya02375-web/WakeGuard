from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# Version 1.8.2.
plist = root / 'Info.plist'
s = plist.read_text(encoding='utf-8')
s = re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+(</string>)', r'\g<1>1.8.2\g<2>', s, count=1)
s = re.sub(r'(<key>CFBundleVersion</key>\s*<string>)[^<]+(</string>)', r'\g<1>11\g<2>', s, count=1)
plist.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = re.sub(r'CFBundleShortVersionString: "[^"]+"', 'CFBundleShortVersionString: "1.8.2"', s)
s = re.sub(r'CFBundleVersion: "[^"]+"', 'CFBundleVersion: "11"', s)
project.write_text(s, encoding='utf-8')

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

        var scored: [(IOSWorldCityEntry, Int)] = []
        scored.reserveCapacity(max(64, limit * 2))
        for e in allEntries() {
            let score: Int
            if e.nameNorm == q || e.asciiNorm == q { score = 0 }
            else if e.nameNorm.hasPrefix(q) || e.asciiNorm.hasPrefix(q) { score = 1 }
            else if e.search.contains(q) { score = 2 }
            else { continue }
            scored.append((e, score))
        }
        scored.sort {
            if $0.1 != $1.1 { return $0.1 < $1.1 }
            if $0.0.population != $1.0.population { return $0.0.population > $1.0.population }
            return $0.0.name.localizedCaseInsensitiveCompare($1.0.name) == .orderedAscending
        }
        var out = Array(scored.prefix(limit).map(\.0))

        // Keep raw IANA identifiers searchable too.
        if out.count < limit {
            var existing = Set(out.map { $0.zoneId + "\u{1F}" + $0.name })
            for id in TimeZone.knownTimeZoneIdentifiers {
                let label = id.split(separator: "/").last.map(String.init)?.replacingOccurrences(of: "_", with: " ") ?? id
                let searchable = normalize(id + " " + label)
                guard searchable.contains(q) else { continue }
                let item = IOSWorldCityEntry(name: label, asciiName: label, countryCode: "", zoneId: id, population: -1, search: searchable, nameNorm: normalize(label), asciiNorm: normalize(label))
                let key = item.zoneId + "\u{1F}" + item.name
                if existing.insert(key).inserted { out.append(item) }
                if out.count >= limit { break }
            }
        }
        remember(out, for: cacheKey)
        return out
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

store = root / 'FeatureStores.swift'
s = store.read_text(encoding='utf-8')
old = '''    func add(_ identifier: String) {
        guard !items.contains(where: { $0.timeZoneIdentifier == identifier }) else { return }
        items.append(WorldClockItem(timeZoneIdentifier: identifier))
    }'''
new = '''    func add(_ identifier: String, displayName: String? = nil) {
        guard !items.contains(where: { $0.timeZoneIdentifier == identifier }) else { return }
        items.append(WorldClockItem(timeZoneIdentifier: identifier, displayName: displayName))
    }'''
assert old in s, 'WorldClockStore.add anchor missing'
s = s.replace(old, new, 1)
store.write_text(s, encoding='utf-8')

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

    var body: some View {
        NavigationStack {
            List {
                if searching && results.isEmpty {
                    HStack { Spacer(); ProgressView(); Spacer() }
                }
                ForEach(results) { entry in
                    Button {
                        store.add(entry.zoneId, displayName: entry.name)
                        dismiss()
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text(entry.name).font(.headline)
                                Spacer()
                                if !entry.countryCode.isEmpty {
                                    Text(Locale.current.localizedString(forRegionCode: entry.countryCode) ?? entry.countryCode)
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Text(entry.zoneId).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                if !searching && results.isEmpty {
                    ContentUnavailableView("見つかりません", systemImage: "magnifyingglass", description: Text("日本語・English・한국어で都市名、国名、タイムゾーンを検索できます"))
                }
            }
            .searchable(text: $query, prompt: "日本語・English・한국어で検索")
            .task(id: query) { await runSearch() }
            .navigationTitle("都市を追加")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("閉じる") { dismiss() } } }
        }
    }

    @MainActor
    private func runSearch() async {
        searching = true
        let q = query
        if !q.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            try? await Task.sleep(for: .milliseconds(18))
        }
        guard !Task.isCancelled else { searching = false; return }
        let found = await Task.detached(priority: .userInitiated) {
            IOSWorldCityCatalog.shared.search(q, limit: 80)
        }.value
        guard !Task.isCancelled, q == query else { return }
        results = found
        searching = false
    }
}
'''
s = s[:start] + new_view + s[end:]
clock.write_text(s, encoding='utf-8')

rv = root / 'RootView.swift'
s = rv.read_text(encoding='utf-8').replace('iOS 1.8.1 beta', 'iOS 1.8.2 beta')
rv.write_text(s, encoding='utf-8')

assert '<string>1.8.2</string>' in plist.read_text(encoding='utf-8')
assert '<string>11</string>' in plist.read_text(encoding='utf-8')
assert 'CFBundleShortVersionString: "1.8.2"' in project.read_text(encoding='utf-8')
assert 'IOSWorldCityCatalog.shared.search' in clock.read_text(encoding='utf-8')
assert '日本語・English・한국어で検索' in clock.read_text(encoding='utf-8')
assert 'displayName: entry.name' in clock.read_text(encoding='utf-8')
assert 'func add(_ identifier: String, displayName: String? = nil)' in store.read_text(encoding='utf-8')
print('IGNIDO Wake iOS 1.8.2 fast trilingual city search patch applied')
