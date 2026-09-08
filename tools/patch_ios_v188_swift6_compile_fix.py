from pathlib import Path

p = Path('ios/IGNIDOWake/ClockViews.swift')
s = p.read_text(encoding='utf-8')
old = '''        let response: MKLocalSearch.Response? = await withCheckedContinuation { continuation in
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
'''
new = '''        return await withCheckedContinuation { continuation in
            search.start { response, _ in
                guard let response else {
                    continuation.resume(returning: [])
                    return
                }

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
                continuation.resume(returning: out)
            }
        }
'''
if old not in s:
    raise SystemExit('expected v1.8.8 MKLocalSearch continuation block not found')
s = s.replace(old, new, 1)
if 'continuation.resume(returning: response)' in s:
    raise SystemExit('unsafe MKLocalSearch.Response continuation still present')
if 'continuation.resume(returning: out)' not in s:
    raise SystemExit('Sendable result continuation missing')
p.write_text(s, encoding='utf-8')
print('IGNIDO Wake iOS 1.8.8 Swift 6 MapKit compile fix applied')
