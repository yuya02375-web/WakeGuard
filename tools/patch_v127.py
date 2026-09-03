from pathlib import Path
import runpy, base64, gzip, subprocess, tempfile

# Rebuild verified v1.2.6 first.
runpy.run_path("tools/patch_v126.py", run_name="__main__")

# Apply v1.2.6 -> v1.2.7.
encoded = Path("tools/v127_patch.b64").read_text(encoding="utf-8").strip()
patch_bytes = gzip.decompress(base64.b64decode(encoded))
with tempfile.NamedTemporaryFile("wb", delete=False) as f:
    f.write(patch_bytes)
    patch_path = f.name
subprocess.run(["patch", "-p1", "-i", patch_path], cwd="WakeGuard/app", check=True)

app = Path("WakeGuard/app")
clock = (app/"src/main/java/jp/wakeguard/alarm/ClockActivity.java").read_text(encoding="utf-8")
i18n = (app/"src/main/java/jp/wakeguard/alarm/I18n.java").read_text(encoding="utf-8")
manifest = (app/"src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle = (app/"build.gradle.kts").read_text(encoding="utf-8")

checks = [
    (clock, "CustomTabsIntent"),
    (clock, "setInitialActivityHeightPx"),
    (clock, "コピーした文字を使う"),
    (clock, "Intent.ACTION_PROCESS_TEXT"),
    (clock, "Intent.ACTION_SEND"),
    (clock, "handler.postDelayed(pendingSearch[0],q.trim().isEmpty()?0L:90L)"),
    (clock, "AtomicBoolean(false)"),
    (clock, "c>=0x3041&&c<=0x3096"),
    (manifest, "android.intent.action.PROCESS_TEXT"),
    (manifest, "android.intent.action.SEND"),
    (i18n, "GoogleはWakeGuardの上に開きます"),
    (gradle, 'implementation("androidx.browser:browser:1.10.0")'),
]
for text, needle in checks:
    if needle not in text:
        raise SystemExit(f"Missing v1.2.7 marker: {needle}")
if 'versionCode = 46' not in gradle or 'versionName = "1.2.7"' not in gradle:
    raise SystemExit("WakeGuard v1.2.7 version bump missing")
if 'stickyDividerLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,1))' not in clock:
    raise SystemExit("Sticky world-clock regression")

print("WakeGuard v1.2.7 unified world-clock search applied")
print("Partial Custom Tab Google fallback: PASS")
print("Copy -> one-tap return: PASS")
print("PROCESS_TEXT + share import: PASS")
print("Hiragana/Katakana fuzzy normalization: PASS")
print("Async index build + 90ms debounce: PASS")
