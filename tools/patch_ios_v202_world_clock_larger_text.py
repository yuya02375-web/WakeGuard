from pathlib import Path

r=Path('ios/IGNIDOWake')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='Info.plist'
s=read(p).replace('<string>2.0.1</string>','<string>2.0.2</string>',1).replace('<string>101</string>','<string>102</string>',1)
write(p,s)

py=Path('ios/project.yml')
s=py.read_text().replace('CFBundleShortVersionString: "2.0.1"','CFBundleShortVersionString: "2.0.2"').replace('CFBundleVersion: "101"','CFBundleVersion: "102"')
py.write_text(s)

p='ClockViews.swift'
s=read(p)
repls={
'private var cityFontSize: CGFloat { min(columns <= 2 ? 16 : columns == 3 ? 14 : 12, max(8, cellHeight * 0.18)) }':'private var cityFontSize: CGFloat { min(columns <= 2 ? 20 : columns == 3 ? 17 : columns == 4 ? 15 : 13, max(9, cellHeight * 0.20)) }',
'private var timeFontSize: CGFloat { min(columns <= 2 ? 31 : columns == 3 ? 25 : columns == 4 ? 21 : 18, max(10, cellHeight * 0.34)) }':'private var timeFontSize: CGFloat { min(columns <= 2 ? 42 : columns == 3 ? 34 : columns == 4 ? 28 : 23, max(12, cellHeight * 0.38)) }',
'private var detailFontSize: CGFloat { min(columns <= 2 ? 11 : 10, max(7, cellHeight * 0.13)) }':'private var detailFontSize: CGFloat { min(columns <= 2 ? 14 : columns == 3 ? 13 : columns == 4 ? 12 : 11, max(8, cellHeight * 0.15)) }'
}
for a,b in repls.items():
    if a not in s: raise SystemExit('iOS font target missing: '+a)
    s=s.replace(a,b,1)
write(p,s)

assert '<string>2.0.2</string>' in read('Info.plist')
assert '<string>102</string>' in read('Info.plist')
assert 'columns == 3 ? 34' in read(p)
assert 'columns == 3 ? 17' in read(p)
assert 'columns == 3 ? 13' in read(p)
print('iOS 2.0.2 larger world-clock text patch applied')
