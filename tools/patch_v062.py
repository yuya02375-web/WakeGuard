from pathlib import Path
import re
import runpy

runpy.run_path("tools/patch_v061.py", run_name="__main__")

root = Path("WakeGuard")
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
scheduler_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmScheduler.java"
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
build_path = root / "app/build.gradle.kts"

# Android 15+ / targetSdk 35+ changed PendingIntent background-activity-launch
# behavior: PendingIntent creators no longer delegate BAL privileges by default.
# WakeGuard targets API 36, so a getActivity() PendingIntent can legitimately be
# delivered by AlarmManager/SystemUI while the screen is locked yet still be
# prevented from bringing AlarmActivity to the foreground unless the creator
# explicitly opts in. This exactly matches the observed state: alarm notification
# appears on the lock screen, sound/vibration run, but AlarmActivity never covers
# the keyguard.

# ---------------------------------------------------------------------------
# AlarmService full-screen notification PendingIntent: creator-side BAL opt-in.
# ---------------------------------------------------------------------------
service = service_path.read_text(encoding="utf-8")
if "import android.app.ActivityOptions;" not in service:
    service = service.replace("import android.app.AlarmManager;", "import android.app.AlarmManager;\nimport android.app.ActivityOptions;", 1) if "import android.app.AlarmManager;" in service else service.replace("import android.app.*;", "import android.app.*;", 1)

old_full = '''        PendingIntent fullPi = PendingIntent.getActivity(this, 4200, full,\n                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n'''
new_full = '''        final int fullFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;\n        PendingIntent fullPi;\n        if (Build.VERSION.SDK_INT >= 34) {\n            ActivityOptions ao = ActivityOptions.makeBasic();\n            ao.setPendingIntentCreatorBackgroundActivityStartMode(\n                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);\n            fullPi = PendingIntent.getActivity(this, 4200, full, fullFlags, ao.toBundle());\n        } else {\n            fullPi = PendingIntent.getActivity(this, 4200, full, fullFlags);\n        }\n'''
if old_full not in service:
    raise SystemExit("v062: AlarmService fullPi block not found")
service = service.replace(old_full, new_full, 1)
service_path.write_text(service, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmScheduler Activity PendingIntents fired by AlarmManager: same creator opt-in.
# Centralize all Activity PendingIntents through one helper so both the dedicated
# lock-screen UI trigger and AlarmClockInfo show intents get the correct options.
# ---------------------------------------------------------------------------
scheduler = scheduler_path.read_text(encoding="utf-8")
if "import android.app.ActivityOptions;" not in scheduler:
    scheduler = scheduler.replace("import android.app.AlarmManager;", "import android.app.AlarmManager;\nimport android.app.ActivityOptions;", 1)

insert_after = '''    private static int requestCode(LocalDate d) {\n        return 300000 + (int)(d.toEpochDay() % 100000);\n    }\n'''
helper = insert_after + '''\n    private static PendingIntent activityPi(Context c, int requestCode, Intent i, int flags) {\n        int f = flags | PendingIntent.FLAG_IMMUTABLE;\n        if (Build.VERSION.SDK_INT >= 34) {\n            ActivityOptions ao = ActivityOptions.makeBasic();\n            ao.setPendingIntentCreatorBackgroundActivityStartMode(\n                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);\n            return PendingIntent.getActivity(c, requestCode, i, f, ao.toBundle());\n        }\n        return PendingIntent.getActivity(c, requestCode, i, f);\n    }\n'''
if insert_after not in scheduler:
    raise SystemExit("v062: requestCode block not found")
scheduler = scheduler.replace(insert_after, helper, 1)

scheduler = scheduler.replace(
'''        return PendingIntent.getActivity(c, requestCode(date) + 400000, i,\n                flags | PendingIntent.FLAG_IMMUTABLE);\n''',
'''        return activityPi(c, requestCode(date) + 400000, i, flags);\n''', 1)
scheduler = scheduler.replace(
'''        return PendingIntent.getActivity(c, requestCode(date), i,\n                flags | PendingIntent.FLAG_IMMUTABLE);\n''',
'''        return activityPi(c, requestCode(date), i, flags);\n''', 1)
scheduler = scheduler.replace(
'''                PendingIntent showPi = PendingIntent.getActivity(c, requestCode(d) + 100000,\n                        show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n''',
'''                PendingIntent showPi = activityPi(c, requestCode(d) + 100000,\n                        show, PendingIntent.FLAG_UPDATE_CURRENT);\n''', 1)
scheduler = scheduler.replace(
'''            PendingIntent showPi = PendingIntent.getActivity(c, RECOVERY_REQUEST + 1, show,\n                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n''',
'''            PendingIntent showPi = activityPi(c, RECOVERY_REQUEST + 1, show,\n                    PendingIntent.FLAG_UPDATE_CURRENT);\n''', 1)
scheduler_path.write_text(scheduler, encoding="utf-8")

# ---------------------------------------------------------------------------
# Service fallback AlarmActivity.launch(): when the app itself sends a
# PendingIntent from background on API 34+, sender-side BAL opt-in is also needed.
# Keep Context.startActivity for older Android versions.
# ---------------------------------------------------------------------------
activity = activity_path.read_text(encoding="utf-8")
if "import android.app.ActivityOptions;" not in activity:
    activity = activity.replace("import android.app.Activity;", "import android.app.Activity;\nimport android.app.ActivityOptions;", 1)
old_launch = '''    public static void launch(Context c) {\n        if (!Prefs.active(c)) return;\n        Intent i = new Intent(c, AlarmActivity.class)\n                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK\n                        | Intent.FLAG_ACTIVITY_SINGLE_TOP\n                        | Intent.FLAG_ACTIVITY_CLEAR_TOP\n                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);\n        c.startActivity(i);\n    }\n'''
new_launch = '''    public static void launch(Context c) {\n        if (!Prefs.active(c)) return;\n        Intent i = new Intent(c, AlarmActivity.class)\n                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK\n                        | Intent.FLAG_ACTIVITY_SINGLE_TOP\n                        | Intent.FLAG_ACTIVITY_CLEAR_TOP\n                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);\n        if (Build.VERSION.SDK_INT >= 34) {\n            try {\n                ActivityOptions creator = ActivityOptions.makeBasic();\n                creator.setPendingIntentCreatorBackgroundActivityStartMode(\n                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);\n                PendingIntent pi = PendingIntent.getActivity(c, 4299, i,\n                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,\n                        creator.toBundle());\n                ActivityOptions sender = ActivityOptions.makeBasic();\n                sender.setPendingIntentBackgroundActivityStartMode(\n                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);\n                pi.send(c, 0, null, null, null, null, sender.toBundle());\n                return;\n            } catch (Throwable ignored) {}\n        }\n        c.startActivity(i);\n    }\n'''
if old_launch not in activity:
    raise SystemExit("v062: AlarmActivity.launch block not found")
activity = activity.replace(old_launch, new_launch, 1)
activity_path.write_text(activity, encoding="utf-8")

# Same permanent signing key; higher versionCode for in-place update.
build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 17", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.6.2"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"v062: version bump failed code={code_n} name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.6.2 Android 15+ PendingIntent BAL opt-in fixes")
