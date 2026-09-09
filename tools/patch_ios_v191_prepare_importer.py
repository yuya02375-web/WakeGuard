from pathlib import Path

p = Path('ios/IGNIDOWake/AlarmViews.swift')
s = p.read_text(encoding='utf-8')
needle = '                draft.soundName = url.lastPathComponent\n'
if needle not in s:
    raise SystemExit('legacy imported-file soundName assignment not found')
s = s.replace(needle, '', 1)
p.write_text(s, encoding='utf-8')
print('IGNIDO Wake iOS 1.9.1 importer prepatch applied')
