from pathlib import Path
import base64, hashlib, re

app = Path("WakeGuard/app")
res = app / "src/main/res"
parts = Path("tools/v172_icon_parts")

def rd(p):
    return p.read_text(encoding="utf-8")

def wr(p, s):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(s, encoding="utf-8")

def sha256(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

# v1.7.2: restore the textured IGNIDO Wake art and keep the whole visible mark
# inside Android's 66x66dp adaptive-icon safe region. The WebP is reconstructed
# from small text chunks so the exact bytes are deterministic in GitHub Actions.
p = app / "build.gradle.kts"
s = rd(p)
s = re.sub(r'versionCode = \d+', 'versionCode = 83', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.7.2"', s)
wr(p, s)

names = ["part00.txt", "part01.txt", "part02a.txt", "part02b.txt", "part03.txt", "part04.txt", "part05.txt", "part06.txt"]
expected_lengths = [3000, 3000, 1500, 1500, 3000, 3000, 3000, 1416]
chunks = []
for name, expected_len in zip(names, expected_lengths):
    chunk = (parts / name).read_text(encoding="utf-8").strip()
    assert len(chunk) == expected_len, (name, len(chunk), expected_len)
    chunks.append(chunk)
encoded = "".join(chunks)
assert len(encoded) == 19416
raw = base64.b64decode(encoded, validate=True)
assert len(raw) == 14562
assert hashlib.sha256(raw).hexdigest() == "0bc991473cc8b15463d9722b9e58189cac417dede65c81638d29bce387b1a1cc"

dst = res / "drawable-nodpi/ic_ignido_wake_full_v172.webp"
dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_bytes(raw)

# The full textured art is precomposed: deep-midnight background plus the original
# ember/copper Wake mark scaled so all meaningful geometry stays inside the safe zone.
wr(res / "drawable/ic_ignido_wake_full_layer.xml",
   '<?xml version="1.0" encoding="utf-8"?>'
   '<bitmap xmlns:android="http://schemas.android.com/apk/res/android" '
   'android:src="@drawable/ic_ignido_wake_full_v172" android:gravity="fill" />')

a26 = ('<?xml version="1.0" encoding="utf-8"?>'
       '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">'
       '<background android:drawable="@color/ignido_midnight"/>'
       '<foreground android:drawable="@drawable/ic_ignido_wake_full_layer"/>'
       '</adaptive-icon>')
a33 = ('<?xml version="1.0" encoding="utf-8"?>'
       '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">'
       '<background android:drawable="@color/ignido_midnight"/>'
       '<foreground android:drawable="@drawable/ic_ignido_wake_full_layer"/>'
       '<monochrome android:drawable="@drawable/ic_ignido_wake_mono"/>'
       '</adaptive-icon>')

for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    wr(res / "mipmap-anydpi-v26" / name, a26)
    wr(res / "mipmap-anydpi-v33" / name, a33)

assert sha256(dst) == "0bc991473cc8b15463d9722b9e58189cac417dede65c81638d29bce387b1a1cc"
assert 'versionCode = 83' in rd(app / "build.gradle.kts")
assert 'versionName = "1.7.2"' in rd(app / "build.gradle.kts")
assert '@drawable/ic_ignido_wake_full_layer' in rd(res / "mipmap-anydpi-v26/ic_launcher.xml")
assert '<monochrome' in rd(res / "mipmap-anydpi-v33/ic_launcher.xml")

print("IGNIDO Wake v1.7.2 textured safe-zone icon patch applied")
