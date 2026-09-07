from pathlib import Path
import re

app = Path("WakeGuard/app")
java = app / "src/main/java/jp/wakeguard/alarm"

p = app / "build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = re.sub(r'versionCode = \d+', 'versionCode = 86', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.7.5"', s)
p.write_text(s, encoding="utf-8")

clock_face = java / "ClockFaceActivity.java"
s = clock_face.read_text(encoding="utf-8")
old = 'public AnalogFace(Context c){super(c);p.setStrokeCap(Paint.Cap.ROUND);setBackgroundColor(Ui.BG);}'
new = 'public AnalogFace(Context c){super(c);p.setStrokeCap(Paint.Cap.ROUND);setBackgroundColor(Color.TRANSPARENT);}'
assert old in s, "AnalogFace background assignment not found"
s = s.replace(old, new)
clock_face.write_text(s, encoding="utf-8")

result = clock_face.read_text(encoding="utf-8")
assert 'versionName = "1.7.5"' in p.read_text(encoding="utf-8")
assert 'versionCode = 86' in p.read_text(encoding="utf-8")
assert new in result
assert old not in result
print("IGNIDO Wake v1.7.5 transparent analog clock surface patch applied")
