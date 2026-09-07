from pathlib import Path
import hashlib, re, shutil

app = Path("WakeGuard/app")
res = app / "src/main/res"
src_dir = Path("tools/v172_icon_assets")

def rd(p):
    return p.read_text(encoding="utf-8")

def wr(p, s):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(s, encoding="utf-8")

def sha256(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

# v1.7.2: restore the original textured IGNIDO Wake mark and keep the entire symbol
# inside Android's guaranteed adaptive-icon safe region.
p = app / "build.gradle.kts"
s = rd(p)
s = re.sub(r'versionCode = \d+', 'versionCode = 83', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.7.2"', s)
wr(p, s)

full_src = src_dir / "ic_ignido_wake_full_v172.webp"
assert full_src.exists()
assert sha256(full_src) == "0bc991473cc8b15463d9722b9e58189cac417dede65c81638d29bce387b1a1cc"

dst = res / "drawable-nodpi/ic_ignido_wake_full_v172.webp"
dst.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(full_src, dst)

# The complete textured art is one full-bleed layer. The visible mark inside it was pre-scaled
# so its alpha/shape bounds fit within the 66x66dp safe zone of a 108x108dp adaptive icon.
wr(res / "drawable/ic_ignido_wake_full_layer.xml",
   '<?xml version="1.0" encoding="utf-8"?>'
   '<bitmap xmlns:android="http://schemas.android.com/apk/res/android" '
   'android:src="@drawable/ic_ignido_wake_full_v172" android:gravity="fill" />')

# Keep a separate dark background so parallax/mask motion can never expose transparency.
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
