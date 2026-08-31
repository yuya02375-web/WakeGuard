from pathlib import Path
import re
import runpy

runpy.run_path("tools/patch_v062.py", run_name="__main__")

root = Path("WakeGuard")
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
build_path = root / "app/build.gradle.kts"

# Android explicitly lists SYSTEM_ALERT_WINDOW as a background-activity-launch
# exception. On the tested realme/ColorOS build, PendingIntent.send() can return
# normally while the Activity is still suppressed behind the keyguard. WakeGuard
# already asks the user for overlay permission, so use that granted capability for
# a direct Activity launch and keep the PendingIntent path as a second attempt.
activity = activity_path.read_text(encoding="utf-8")
if "import android.provider.Settings;" not in activity:
    activity = activity.replace("import android.os.*;", "import android.os.*;\nimport android.provider.Settings;", 1)

old_launch = '''    public static void launch(Context c) {\n        if (!Prefs.active(c)) return;\n        Intent i = new Intent(c, AlarmActivity.class)\n                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK\n                        | Intent.FLAG_ACTIVITY_SINGLE_TOP\n                        | Intent.FLAG_ACTIVITY_CLEAR_TOP\n                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);\n        if (Build.VERSION.SDK_INT >= 34) {\n            try {\n                ActivityOptions creator = ActivityOptions.makeBasic();\n                creator.setPendingIntentCreatorBackgroundActivityStartMode(\n                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);\n                PendingIntent pi = PendingIntent.getActivity(c, 4299, i,\n                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,\n                        creator.toBundle());\n                ActivityOptions sender = ActivityOptions.makeBasic();\n                sender.setPendingIntentBackgroundActivityStartMode(\n                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);\n                pi.send(c, 0, null, null, null, null, sender.toBundle());\n                return;\n            } catch (Throwable ignored) {}\n        }\n        c.startActivity(i);\n    }\n'''
new_launch = '''    public static void launch(Context c) {\n        if (!Prefs.active(c)) return;\n        Intent i = new Intent(c, AlarmActivity.class)\n                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK\n                        | Intent.FLAG_ACTIVITY_SINGLE_TOP\n                        | Intent.FLAG_ACTIVITY_CLEAR_TOP\n                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);\n\n        // SYSTEM_ALERT_WINDOW is an Android-documented BAL exception. Prefer a\n        // direct launch when the user granted it. ColorOS/realme can accept a\n        // PendingIntent.send() call without actually surfacing the Activity.\n        if (Settings.canDrawOverlays(c)) {\n            try {\n                c.startActivity(i);\n            } catch (Throwable t) {\n                try { Prefs.lastAlarmError(c, "DirectLockUi: " + t.getClass().getSimpleName()\n                        + (t.getMessage() == null ? "" : ": " + t.getMessage())); }\n                catch (Throwable ignored) {}\n            }\n        }\n\n        // Keep the Android 14+/15+ creator + sender BAL opt-in as an independent\n        // second path. Duplicate delivery is harmless because AlarmActivity is\n        // singleTop/CLEAR_TOP.\n        if (Build.VERSION.SDK_INT >= 34) {\n            try {\n                ActivityOptions creator = ActivityOptions.makeBasic();\n                creator.setPendingIntentCreatorBackgroundActivityStartMode(\n                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);\n                PendingIntent pi = PendingIntent.getActivity(c, 4299, i,\n                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,\n                        creator.toBundle());\n                ActivityOptions sender = ActivityOptions.makeBasic();\n                sender.setPendingIntentBackgroundActivityStartMode(\n                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);\n                pi.send(c, 0, null, null, null, null, sender.toBundle());\n                return;\n            } catch (Throwable t) {\n                try { Prefs.lastAlarmError(c, "PendingLockUi: " + t.getClass().getSimpleName()\n                        + (t.getMessage() == null ? "" : ": " + t.getMessage())); }\n                catch (Throwable ignored) {}\n            }\n        }\n\n        try { c.startActivity(i); }\n        catch (Throwable t) {\n            try { Prefs.lastAlarmError(c, "StartLockUi: " + t.getClass().getSimpleName()\n                    + (t.getMessage() == null ? "" : ": " + t.getMessage())); }\n            catch (Throwable ignored) {}\n        }\n    }\n'''
if old_launch not in activity:
    raise SystemExit("v063: AlarmActivity.launch block not found")
activity = activity.replace(old_launch, new_launch, 1)
activity_path.write_text(activity, encoding="utf-8")

# The alarm guard already runs while the mission is active. If ColorOS drops the
# first lock-screen launch, retry the direct Activity path until it is actually
# resumed. This also restores the mission screen if Home/Back leaves it.
service = service_path.read_text(encoding="utf-8")
old_guard = '''            try {\n                if (!running || !Prefs.active(AlarmService.this)) return;\n                if (Settings.canDrawOverlays(AlarmService.this)) {\n                    ensureOverlay();\n                }\n            } catch (Throwable t) {\n'''
new_guard = '''            try {\n                if (!running || !Prefs.active(AlarmService.this)) return;\n                if (Settings.canDrawOverlays(AlarmService.this)) {\n                    if (!AlarmActivity.visible) {\n                        try { AlarmActivity.launch(AlarmService.this); } catch (Throwable ignored) {}\n                    }\n                    ensureOverlay();\n                }\n            } catch (Throwable t) {\n'''
if old_guard not in service:
    raise SystemExit("v063: guard block not found")
service = service.replace(old_guard, new_guard, 1)
service_path.write_text(service, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 18", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.6.3"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"v063: version bump failed code={code_n} name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.6.3 realme lockscreen direct-launch fallback")
