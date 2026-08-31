from pathlib import Path
import re
import runpy

runpy.run_path("tools/patch_v063.py", run_name="__main__")

root = Path("WakeGuard")
scheduler_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmScheduler.java"
build_path = root / "app/build.gradle.kts"

scheduler = scheduler_path.read_text(encoding="utf-8")

# v0.5.7 proved on the user's realme that AlarmManager -> Activity can actually
# reach AlarmActivity while locked (it then crashed in WindowInsets code, which
# has since been removed). Later versions changed the primary trigger to a
# BroadcastReceiver and tried to surface the UI afterwards; ColorOS keeps
# suppressing that secondary Activity launch. Restore the proven path: make the
# exact alarm's primary PendingIntent the AlarmActivity itself. AlarmActivity's
# ACTION_SCHEDULED_FIRE immediately marks the session active and starts the
# foreground AlarmService, so sound/vibration and mission UI start together.

old_comment = '''    // v0.5.7/v0.5.8 used a direct Activity PendingIntent. Keep only to cancel\n    // alarms already registered by those versions.\n    private static PendingIntent oldActivityPi(Context c, LocalDate date, int flags) {\n'''
new_comment = '''    // Primary scheduled alarm route: AlarmManager launches AlarmActivity directly.\n    // This is the route that already reached the lock-screen UI on the user's realme.\n    private static PendingIntent scheduledActivityPi(Context c, LocalDate date, int flags) {\n'''
if old_comment not in scheduler:
    raise SystemExit("v064: oldActivityPi declaration not found")
scheduler = scheduler.replace(old_comment, new_comment, 1)
scheduler = scheduler.replace("oldActivityPi(c, d, PendingIntent.FLAG_NO_CREATE)",
                              "scheduledActivityPi(c, d, PendingIntent.FLAG_NO_CREATE)", 1)

old_schedule = '''                PendingIntent fire = fireBroadcastPi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n                Intent show = new Intent(c, MainActivity.class);\n                PendingIntent showPi = activityPi(c, requestCode(d) + 100000,\n                        show, PendingIntent.FLAG_UPDATE_CURRENT);\n                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(\n                        when.toInstant().toEpochMilli(), showPi);\n                am.setAlarmClock(info, fire);\n\n                // OEM-independent UI attempt after the foreground service has had\n                // time to start sound/vibration. The Activity only displays the\n                // mission; it is no longer responsible for starting the alarm.\n                PendingIntent showUi = showAlarmUiPi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n                long showUiAt = when.toInstant().toEpochMilli() + 650L;\n                if (Build.VERSION.SDK_INT >= 23)\n                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showUiAt, showUi);\n                else\n                    am.setExact(AlarmManager.RTC_WAKEUP, showUiAt, showUi);\n\n                PendingIntent backup = backupFirePi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n                long backupAt = when.toInstant().toEpochMilli() + 2000L;\n'''
new_schedule = '''                PendingIntent fireActivity = scheduledActivityPi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n                Intent show = new Intent(c, MainActivity.class);\n                PendingIntent showPi = activityPi(c, requestCode(d) + 100000,\n                        show, PendingIntent.FLAG_UPDATE_CURRENT);\n                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(\n                        when.toInstant().toEpochMilli(), showPi);\n                am.setAlarmClock(info, fireActivity);\n\n                // Independent second UI attempt in case the OEM drops the first draw.\n                // It does not start a new alarm session; it only resurfaces the mission.\n                PendingIntent showUi = showAlarmUiPi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n                long showUiAt = when.toInstant().toEpochMilli() + 900L;\n                if (Build.VERSION.SDK_INT >= 23)\n                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showUiAt, showUi);\n                else\n                    am.setExact(AlarmManager.RTC_WAKEUP, showUiAt, showUi);\n\n                // Broadcast fallback starts/restores the foreground service if ColorOS\n                // somehow suppresses the Activity PendingIntent entirely.\n                PendingIntent backup = backupFirePi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n                long backupAt = when.toInstant().toEpochMilli() + 1500L;\n'''
if old_schedule not in scheduler:
    raise SystemExit("v064: schedule block not found")
scheduler = scheduler.replace(old_schedule, new_schedule, 1)
scheduler_path.write_text(scheduler, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 19", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.6.4"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"v064: version bump failed code={code_n} name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.6.4 direct AlarmManager->AlarmActivity primary route")
