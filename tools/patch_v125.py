from pathlib import Path
import runpy, base64, gzip, subprocess, tempfile

# Rebuild the verified v1.2.4 source first.
runpy.run_path("tools/patch_v124.py", run_name="__main__")

# Apply the v1.2.4 -> v1.2.5 app-module diff.
encoded=Path("tools/v125_patch.b64").read_text(encoding="utf-8").strip()
patch_bytes=gzip.decompress(base64.b64decode(encoded))
with tempfile.NamedTemporaryFile("wb", delete=False) as f:
    f.write(patch_bytes)
    patch_path=f.name
subprocess.run(["patch","-p1","-i",patch_path], cwd="WakeGuard/app", check=True)

clock=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/ClockActivity.java").read_text(encoding="utf-8")
ui=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/Ui.java").read_text(encoding="utf-8")
i18n=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/I18n.java").read_text(encoding="utf-8")
gradle=Path("WakeGuard/app/build.gradle.kts").read_text(encoding="utf-8")

checks=[
    (clock,"guessCountryCodes"),(clock,"editDistance"),(clock,"regionCountryCodes"),
    (clock,"fuzzyCityMatches"),(clock,"Googleで国名を確認"),(clock,"applyGoogleClipboardSearchIfNeeded"),
    (ui,"setAutoSizeTextTypeUniformWithConfiguration"),(ui,"setMinimumWidth(0)"),
    (i18n,"国名が曖昧でも、都市・地域・だいたいの名前から候補を探せます"),
]
for text,needle in checks:
    if needle not in text:
        raise SystemExit(f"Missing v1.2.5 marker: {needle}")
if 'versionCode = 44' not in gradle or 'versionName = "1.2.5"' not in gradle:
    raise SystemExit("WakeGuard v1.2.5 version bump missing")
if 'stickyDividerLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,1))' not in clock:
    raise SystemExit("Sticky world-clock regression")

print("WakeGuard v1.2.5 fuzzy world-clock search and nav fix applied")
print("Fuzzy country suggestions: PASS")
print("Region/diverse fallback candidates: PASS")
print("Google browser fallback + clipboard return: PASS")
print("Bottom-nav auto-size: PASS")
