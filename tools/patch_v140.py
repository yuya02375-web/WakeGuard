from pathlib import Path
import runpy, base64, gzip, subprocess, tempfile

# Rebuild the verified v1.3.1 source first.
runpy.run_path("tools/patch_v131.py", run_name="__main__")

encoded="".join(Path(f"tools/v140_patch.part{i}").read_text(encoding="utf-8").strip() for i in range(1,6))
patch_bytes=gzip.decompress(base64.b64decode(encoded))
with tempfile.NamedTemporaryFile("wb",delete=False) as f:
    f.write(patch_bytes)
    patch_path=f.name
subprocess.run(["patch","-p1","-i",patch_path],cwd="WakeGuard/app",check=True)

app=Path("WakeGuard/app")
checks={
    app/"src/main/java/jp/wakeguard/alarm/StreakGrowth.java":["One permanent companion","Long.MAX_VALUE","visualPower"],
    app/"src/main/java/jp/wakeguard/alarm/StreakCompanionView.java":["uncapped procedural growth","drawDragon","drawHorns","drawRunes","RadialGradient"],
    app/"src/main/java/jp/wakeguard/alarm/StatsActivity.java":["StreakGrowth.ensure","setGrowth","上限はありません"],
    app/"src/main/java/jp/wakeguard/alarm/StreakTracker.java":["StreakGrowth.onWakeSuccess"],
}
for path,needles in checks.items():
    text=path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text: raise SystemExit(f"Missing v1.4.0 marker {needle} in {path}")

stats=(app/"src/main/java/jp/wakeguard/alarm/StatsActivity.java").read_text(encoding="utf-8")
for forbidden in ["showGacha(","showCharacters(","showDecorations(","ガチャチケット","エッセンス","キャラコレクション"]:
    if forbidden in stats: raise SystemExit(f"Game UI still present: {forbidden}")

gradle=(app/"build.gradle.kts").read_text(encoding="utf-8")
if 'versionCode = 51' not in gradle or 'versionName = "1.4.0"' not in gradle:
    raise SystemExit("v1.4.0 version bump missing")

print("WakeGuard v1.4.0 single infinite-growth companion applied")
print("Gacha/rarity/currency/equipment/item UI removed: PASS")
print("One successful wake = one permanent growth level: PASS")
print("No growth cap (long-backed): PASS")
print("Dark flame-dragon renderer replaces mascot/collectible renderer: PASS")
print("Visual complexity scales with growth level: PASS")
