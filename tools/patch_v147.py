from pathlib import Path
import runpy

runpy.run_path("tools/patch_v146.py", run_name="__main__")

app = Path("WakeGuard/app")
java = app / "src/main/java/jp/wakeguard/alarm"

# Timer fine-adjustment symmetry: every positive adjustment gets an equal negative counterpart.
p = java / "ClockActivity.java"
s = p.read_text(encoding="utf-8")
old = '        String[] labels={"−1分","＋1秒","＋10秒","＋30秒","＋1分","＋5分","＋10分","＋1時間"}; long[] deltas={-60000L,1000L,10000L,30000L,60000L,300000L,600000L,3600000L};'
new = '        String[] labels={"−1秒","＋1秒","−10秒","＋10秒","−30秒","＋30秒","−1分","＋1分","−5分","＋5分","−10分","＋10分","−1時間","＋1時間"}; long[] deltas={-1000L,1000L,-10000L,10000L,-30000L,30000L,-60000L,60000L,-300000L,300000L,-600000L,600000L,-3600000L,3600000L};'
if old not in s:
    raise SystemExit("v1.4.7 timer adjustment anchor missing")
s = s.replace(old, new, 1)
p.write_text(s, encoding="utf-8")

# Version bump.
gradle_path = app / "build.gradle.kts"
gradle = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 57' not in gradle or 'versionName = "1.4.6"' not in gradle:
    raise SystemExit("v1.4.6 version markers missing")
gradle = gradle.replace('versionCode = 57', 'versionCode = 58', 1)
gradle = gradle.replace('versionName = "1.4.6"', 'versionName = "1.4.7"', 1)
gradle_path.write_text(gradle, encoding="utf-8")

# Verification.
clock = p.read_text(encoding="utf-8")
for needle in [
    '"−1秒","＋1秒"',
    '"−10秒","＋10秒"',
    '"−30秒","＋30秒"',
    '"−1分","＋1分"',
    '"−5分","＋5分"',
    '"−10分","＋10分"',
    '"−1時間","＋1時間"',
    '-3600000L,3600000L',
    'Math.max(0L,quickDuration()+delta)',
]:
    if needle not in clock:
        raise SystemExit(f"Missing v1.4.7 symmetric timer adjustment marker: {needle}")
final_gradle = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 58' not in final_gradle or 'versionName = "1.4.7"' not in final_gradle:
    raise SystemExit("v1.4.7 version bump missing")

print("WakeGuard v1.4.7 symmetric timer adjustment patch applied")
print("±1 sec, ±10 sec, ±30 sec: PASS")
print("±1 min, ±5 min, ±10 min, ±1 hour: PASS")
print("Negative adjustment clamps safely at zero: PASS")
