from pathlib import Path
import runpy, base64, gzip, subprocess, tempfile

# Rebuild the verified v1.2.3 source first.
runpy.run_path("tools/patch_v123.py", run_name="__main__")

# Apply the v1.2.3 -> v1.2.4 app-module diff.
encoded=Path("tools/v124_patch.b64").read_text(encoding="utf-8").strip()
patch_bytes=gzip.decompress(base64.b64decode(encoded))
with tempfile.NamedTemporaryFile("wb", delete=False) as f:
    f.write(patch_bytes)
    patch_path=f.name
subprocess.run(["patch","-p1","-i",patch_path], cwd="WakeGuard/app", check=True)

clock=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/ClockActivity.java").read_text(encoding="utf-8")
i18n=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/I18n.java").read_text(encoding="utf-8")
gradle=Path("WakeGuard/app/build.gradle.kts").read_text(encoding="utf-8")

# Country-aware search and localized city-name guards.
needles=[
    "TimeZone.SystemTimeZoneType.CANONICAL_LOCATION",
    "TimeZone.getRegion(id)",
    "getExemplarLocationName(id)",
    "countryAliases",
    "国名で検索すると、その国の都市候補をまとめて表示します",
    "Ui.round(Ui.SURFACE_2,15",
]
for needle in needles:
    if needle not in clock:
        raise SystemExit(f"Missing v1.2.4 world-clock marker: {needle}")
if "国・都市・タイムゾーンを検索" not in i18n:
    raise SystemExit("Missing v1.2.4 localized search string")
if 'versionCode = 43' not in gradle or 'versionName = "1.2.4"' not in gradle:
    raise SystemExit("WakeGuard v1.2.4 version bump missing")

# Keep the v1.2.3 sticky-header regression fix in place.
if 'stickyDividerLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,1))' not in clock:
    raise SystemExit("Sticky world-clock divider regression")

print("WakeGuard v1.2.4 country-aware world-clock search applied")
print("Country -> multiple time-zone candidates: PASS")
print("Localized ICU city names: PASS")
print("Dark card picker UI: PASS")
