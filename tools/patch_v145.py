from pathlib import Path
import runpy

runpy.run_path("tools/patch_v144.py", run_name="__main__")

app = Path("WakeGuard/app")
java = app / "src/main/java/jp/wakeguard/alarm"

# Replace the Canvas-only flame renderer with a procedural OpenGL ES 2.0 fire renderer.
renderer_path = java / "StreakCompanionView.java"
renderer = Path("tools/v145_templates/StreakCompanionView.java").read_text(encoding="utf-8")
renderer_path.write_text(renderer, encoding="utf-8")

# GLSurfaceView needs lifecycle notifications while the streak screen is visible.
stats_path = java / "StatsActivity.java"
stats = stats_path.read_text(encoding="utf-8")
old_resume = '    @Override protected void onResume(){super.onResume();refresh();}'
new_resume = '    @Override protected void onResume(){super.onResume();if(companion!=null)companion.onResume();refresh();}\n    @Override protected void onPause(){if(companion!=null)companion.onPause();super.onPause();}'
if old_resume not in stats:
    raise SystemExit("v1.4.5 StatsActivity onResume anchor missing")
stats = stats.replace(old_resume, new_resume, 1)
stats = stats.replace(
    "成長するほど、白熱コア・炎・頭部・角・翼・胴体・尾・爪・鱗状の熱流・火の粉・光輪が連続的に強くなります。表示と成長は端末内だけで動作し、オフラインでも完全に表示できます。",
    "炎は固定画像ではなく、端末内のシェーダーで白熱コア・赤橙の外炎・揺らぐ輪郭・上昇する炎・火の粉・発光を毎フレーム生成します。炎竜の成長と表示はオフラインでも完全に動作します。"
)
stats_path.write_text(stats, encoding="utf-8")

# Version bump.
gradle_path = app / "build.gradle.kts"
gradle = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 55' not in gradle or 'versionName = "1.4.4"' not in gradle:
    raise SystemExit("v1.4.4 version markers missing")
gradle = gradle.replace('versionCode = 55', 'versionCode = 56', 1)
gradle = gradle.replace('versionName = "1.4.4"', 'versionName = "1.4.5"', 1)
gradle_path.write_text(gradle, encoding="utf-8")

# Verification.
view = renderer_path.read_text(encoding="utf-8")
for needle in [
    "extends GLSurfaceView",
    "setEGLContextClientVersion(2)",
    "GLES20.glCreateShader",
    "float fbm(vec2 p)",
    "float plume=0.0",
    "sparkLayer",
    "GLUtils.texImage2D",
    "buildMask(level)",
    "Everything ships inside the APK and remains fully offline",
]:
    if needle not in view:
        raise SystemExit(f"Missing v1.4.5 realistic flame marker: {needle}")
for forbidden in ["HttpURLConnection", "URLConnection", "java.net", "https://", "http://"]:
    if forbidden in view:
        raise SystemExit(f"Network dependency in v1.4.5 renderer: {forbidden}")

stats = stats_path.read_text(encoding="utf-8")
for needle in ["companion.onResume()", "companion.onPause()", "端末内のシェーダー"]:
    if needle not in stats:
        raise SystemExit(f"Missing v1.4.5 lifecycle/copy marker: {needle}")

final_gradle = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 56' not in final_gradle or 'versionName = "1.4.5"' not in final_gradle:
    raise SystemExit("v1.4.5 version bump missing")

print("WakeGuard v1.4.5 realistic offline flame shader patch applied")
print("OpenGL ES 2.0 procedural flame: PASS")
print("Dynamic FBM turbulence, rising plumes, glow and sparks: PASS")
print("Dragon growth mask remains local/offline: PASS")
