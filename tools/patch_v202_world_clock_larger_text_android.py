from pathlib import Path
import re

r=Path('WakeGuard/app')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=re.sub(r'versionCode = \d+', 'versionCode = 102', s, count=1)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "2.0.2"', s, count=1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/ClockActivity.java'; s=read(p)
repls={
'TextView name=text(nameText,columns<=2?15:columns==3?13:11,Ui.TEXT);':'TextView name=text(nameText,columns<=2?20:columns==3?17:columns==4?15:13,Ui.TEXT);',
'TextView time=text("--:--",columns<=2?30:columns==3?25:columns==4?21:18,Ui.TEXT);':'TextView time=text("--:--",columns<=2?42:columns==3?34:columns==4?28:23,Ui.TEXT);',
'TextView detail=text("",columns<=2?11:10,Ui.MUTED);':'TextView detail=text("",columns<=2?14:columns==3?13:columns==4?12:11,Ui.MUTED);'
}
for a,b in repls.items():
    if a not in s: raise SystemExit('Android font target missing: '+a)
    s=s.replace(a,b,1)
write(p,s)

assert 'versionName = "2.0.2"' in read('build.gradle.kts')
assert 'versionCode = 102' in read('build.gradle.kts')
assert 'columns==3?34' in read(p)
assert 'columns==3?17' in read(p)
assert 'columns==3?13' in read(p)
print('Android 2.0.2 larger world-clock text patch applied')
