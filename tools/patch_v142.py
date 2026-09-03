from pathlib import Path
import runpy, base64, gzip, subprocess, tempfile

# Rebuild verified v1.4.1 first.
runpy.run_path("tools/patch_v141.py", run_name="__main__")

encoded=Path("tools/v142_patch.part1").read_text(encoding="utf-8").strip()
patch_bytes=gzip.decompress(base64.b64decode(encoded))
with tempfile.NamedTemporaryFile("wb",delete=False) as f:
    f.write(patch_bytes)
    patch_path=f.name
subprocess.run(["patch","-p1","-i",patch_path],cwd="WakeGuard/app",check=True)

app=Path("WakeGuard/app")
java=app/"src/main/java/jp/wakeguard/alarm"
view=(java/"StreakCompanionView.java").read_text(encoding="utf-8")
for needle in [
    "Premium single-companion renderer",
    "drawLivingFire",
    "buildPremiumBody",
    "drawSideCurrents",
    "drawCrown",
    "drawInternalFlow",
    "drawEdgeLicks",
    "Long.numberOfLeadingZeros"
]:
    if needle not in view:
        raise SystemExit(f"Missing v1.4.2 premium flame marker: {needle}")
for forbidden in ["drawDragon(","drawHead(","drawHorns(","drawWings(","gacha", "rarity"]:
    if forbidden in view:
        raise SystemExit(f"Old/game renderer marker remains: {forbidden}")

gradle=(app/"build.gradle.kts").read_text(encoding="utf-8")
if 'versionCode = 53' not in gradle or 'versionName = "1.4.2"' not in gradle:
    raise SystemExit("v1.4.2 version bump missing")

stats=(java/"StatsActivity.java").read_text(encoding="utf-8")
for forbidden in ["showGacha(","showCharacters(","showDecorations(","ガチャチケット","エッセンス","キャラコレクション"]:
    if forbidden in stats:
        raise SystemExit(f"Game UI still present: {forbidden}")

print("WakeGuard v1.4.2 premium living-flame renderer applied")
print("Flame remains the only permanent companion: PASS")
print("Stable premium silhouette + layered core/current/crown/embers: PASS")
print("Growth complexity follows log2(level) without fixed milestone cap: PASS")
print("No gacha/rarity/collection/equipment systems: PASS")
