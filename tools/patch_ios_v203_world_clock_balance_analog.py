from pathlib import Path

r=Path('ios/IGNIDOWake')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='Info.plist'
s=read(p).replace('<string>2.0.2</string>','<string>2.0.3</string>',1).replace('<string>102</string>','<string>103</string>',1)
write(p,s)

py=Path('ios/project.yml')
s=py.read_text().replace('CFBundleShortVersionString: "2.0.2"','CFBundleShortVersionString: "2.0.3"').replace('CFBundleVersion: "102"','CFBundleVersion: "103"')
py.write_text(s)

p='ClockViews.swift'
s=read(p)

s=s.replace('.onTapGesture { enlarged = item }',
            '.onTapGesture { store.displayMode = (store.displayMode + 1) % 3 }\n                        .onLongPressGesture { enlarged = item }',1)

old='''                Text(time(context.date))
                    .font(.system(size: timeFontSize, weight: .medium, design: .rounded))
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.48)
'''
new='''                Group {
                    if store.displayMode == 0 {
                        Text(time(context.date))
                            .font(.system(size: timeFontSize, weight: .medium, design: .rounded))
                            .monospacedDigit()
                            .lineLimit(1)
                            .minimumScaleFactor(0.72)
                            .allowsTightening(true)
                    } else if store.displayMode == 1 {
                        IgnidoAnalogClockFace(date: context.date, timeZone: TimeZone(identifier: item.timeZoneIdentifier) ?? .current)
                            .frame(width: analogSize, height: analogSize)
                    } else {
                        VStack(spacing: 2) {
                            IgnidoAnalogClockFace(date: context.date, timeZone: TimeZone(identifier: item.timeZoneIdentifier) ?? .current)
                                .frame(width: analogSize * 0.72, height: analogSize * 0.72)
                            Text(time(context.date))
                                .font(.system(size: max(16, timeFontSize * 0.72), weight: .medium, design: .rounded))
                                .monospacedDigit()
                                .lineLimit(1)
                                .minimumScaleFactor(0.75)
                                .allowsTightening(true)
                        }
                    }
                }
'''
if old not in s: raise SystemExit('overview digital block missing')
s=s.replace(old,new,1)

s=s.replace('private var cityFontSize: CGFloat { min(columns <= 2 ? 20 : columns == 3 ? 17 : columns == 4 ? 15 : 13, max(9, cellHeight * 0.20)) }',
            'private var cityFontSize: CGFloat { min(columns <= 2 ? 18 : columns == 3 ? 15 : columns == 4 ? 13 : 11, max(9, cellHeight * 0.18)) }',1)
s=s.replace('private var timeFontSize: CGFloat { min(columns <= 2 ? 42 : columns == 3 ? 34 : columns == 4 ? 28 : 23, max(12, cellHeight * 0.38)) }',
            'private var timeFontSize: CGFloat { min(columns <= 2 ? 36 : columns == 3 ? 30 : columns == 4 ? 24 : 20, max(12, cellHeight * 0.34)) }',1)
s=s.replace('private var detailFontSize: CGFloat { min(columns <= 2 ? 14 : columns == 3 ? 13 : columns == 4 ? 12 : 11, max(8, cellHeight * 0.15)) }',
            'private var detailFontSize: CGFloat { min(columns <= 2 ? 13 : columns == 3 ? 11 : columns == 4 ? 10 : 9, max(8, cellHeight * 0.12)) }',1)

needle='    private var detailFontSize: CGFloat { min(columns <= 2 ? 13 : columns == 3 ? 11 : columns == 4 ? 10 : 9, max(8, cellHeight * 0.12)) }\n'
if needle not in s: raise SystemExit('detail font line missing')
s=s.replace(needle,needle+'    private var analogSize: CGFloat { min(columns <= 2 ? 108 : columns == 3 ? 88 : columns == 4 ? 70 : 58, max(42, cellHeight * 0.42)) }\n',1)

s=s.replace('.minimumScaleFactor(0.55)','.minimumScaleFactor(0.68)\n                    .allowsTightening(true)',1)
s=s.replace('.minimumScaleFactor(0.6)','.minimumScaleFactor(0.72)\n                    .allowsTightening(true)',1)

write(p,s)

assert '<string>2.0.3</string>' in read('Info.plist')
assert '<string>103</string>' in read('Info.plist')
assert 'store.displayMode = (store.displayMode + 1) % 3' in read(p)
assert 'IgnidoAnalogClockFace' in read(p)
assert 'columns == 3 ? 30' in read(p)
assert 'analogSize' in read(p)
print('iOS 2.0.3 balanced text + overview analog patch applied')
