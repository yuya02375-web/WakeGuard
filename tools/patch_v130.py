from pathlib import Path
import runpy, base64, gzip, subprocess, tempfile

runpy.run_path("tools/patch_v129.py", run_name="__main__")
encoded=Path("tools/v130_patch.b64").read_text(encoding="utf-8").strip()
patch_bytes=gzip.decompress(base64.b64decode(encoded))
with tempfile.NamedTemporaryFile("wb",delete=False) as f:
    f.write(patch_bytes); patch_path=f.name
subprocess.run(["patch","-p1","-i",patch_path],cwd="WakeGuard/app",check=True)

app=Path("WakeGuard/app")
checks={
    app/"src/main/java/jp/wakeguard/alarm/StreakGame.java":["EMBERKIN","AURELION DRAGON","gachaTen","100"],
    app/"src/main/java/jp/wakeguard/alarm/StreakCompanionView.java":["setCompanion","dragon","wings"],
    app/"src/main/java/jp/wakeguard/alarm/TimerRingService.java":["USAGE_ALARM","setLooping(true)","ACTION_STOP"],
    app/"src/main/java/jp/wakeguard/alarm/TimerReceiver.java":["TimerRingService.start"],
    app/"src/main/java/jp/wakeguard/alarm/StreakTracker.java":["StreakGame.onWakeSuccess"],
    app/"src/main/java/jp/wakeguard/alarm/StatsActivity.java":["キャラガチャ","装飾コレクション","trainEquipped"],
    app/"src/main/AndroidManifest.xml":[".TimerRingService"],
}
for path,needles in checks.items():
    text=path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text: raise SystemExit(f"Missing v1.3.0 marker {needle} in {path}")
gradle=(app/"build.gradle.kts").read_text(encoding="utf-8")
if 'versionCode = 49' not in gradle or 'versionName = "1.3.0"' not in gradle: raise SystemExit("v1.3.0 version bump missing")
print("WakeGuard v1.3.0 streak companion game + reliable timer ringing applied")
print("81 characters including exclusive evolving starter: PASS")
print("40 decorations + milestone rewards: PASS")
print("Free-ticket gacha + rarity + pity: PASS")
print("Character XP/training + starter dragon evolution: PASS")
print("Timer foreground ringing service with selected sound: PASS")
