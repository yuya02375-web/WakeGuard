from pathlib import Path
import re
import runpy

runpy.run_path("tools/patch_v059.py", run_name="__main__")

root = Path("WakeGuard")
scheduler_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmScheduler.java"
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
main_path = root / "app/src/main/java/jp/wakeguard/alarm/MainActivity.java"
build_path = root / "app/build.gradle.kts"

# ---------------------------------------------------------------------------
# v0.6.0 lock-screen reliability:
# Keep the working scheduled path (AlarmManager -> Receiver -> foreground service),
# but add a separate Activity PendingIntent shortly AFTER the service starts.
# v0.5.8 tried to make the Activity responsible for starting the service; if the
# OEM blocked that Activity, the alarm itself could fail. Here audio/vibration is
# already alive before the UI attempt, so UI failure cannot silence the alarm.
# ---------------------------------------------------------------------------
scheduler = scheduler_path.read_text(encoding="utf-8")

needle = '''    private static PendingIntent fireBroadcastPi(Context c, LocalDate date, int flags) {\n        Intent i = new Intent(c, AlarmReceiver.class)\n                .setAction(AlarmReceiver.ACTION_FIRE)\n                .putExtra("epochDay", date.toEpochDay());\n        return PendingIntent.getBroadcast(c, requestCode(date), i,\n                flags | PendingIntent.FLAG_IMMUTABLE);\n    }\n'''
extra = needle + '''\n    private static PendingIntent showAlarmUiPi(Context c, LocalDate date, int flags) {\n        Intent i = new Intent(c, AlarmActivity.class)\n                .setAction("jp.wakeguard.alarm.SHOW_RUNNING_ALARM")\n                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK\n                        | Intent.FLAG_ACTIVITY_SINGLE_TOP\n                        | Intent.FLAG_ACTIVITY_CLEAR_TOP\n                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION);\n        return PendingIntent.getActivity(c, requestCode(date) + 400000, i,\n                flags | PendingIntent.FLAG_IMMUTABLE);\n    }\n'''
if needle not in scheduler:
    raise SystemExit("v060: fireBroadcastPi block not found")
scheduler = scheduler.replace(needle, extra, 1)

cancel_needle = '''                PendingIntent backup = backupFirePi(c, d, PendingIntent.FLAG_NO_CREATE);\n                if (backup != null) am.cancel(backup);\n'''
cancel_repl = cancel_needle + '''                PendingIntent showUi = showAlarmUiPi(c, d, PendingIntent.FLAG_NO_CREATE);\n                if (showUi != null) am.cancel(showUi);\n'''
if cancel_needle not in scheduler:
    raise SystemExit("v060: scheduler cancel block not found")
scheduler = scheduler.replace(cancel_needle, cancel_repl, 1)

schedule_needle = '''                am.setAlarmClock(info, fire);\n\n                PendingIntent backup = backupFirePi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n'''
schedule_repl = '''                am.setAlarmClock(info, fire);\n\n                // OEM-independent UI attempt after the foreground service has had\n                // time to start sound/vibration. The Activity only displays the\n                // mission; it is no longer responsible for starting the alarm.\n                PendingIntent showUi = showAlarmUiPi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n                long showUiAt = when.toInstant().toEpochMilli() + 650L;\n                if (Build.VERSION.SDK_INT >= 23)\n                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showUiAt, showUi);\n                else\n                    am.setExact(AlarmManager.RTC_WAKEUP, showUiAt, showUi);\n\n                PendingIntent backup = backupFirePi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n'''
if schedule_needle not in scheduler:
    raise SystemExit("v060: scheduler setAlarmClock block not found")
scheduler = scheduler.replace(schedule_needle, schedule_repl, 1)
scheduler_path.write_text(scheduler, encoding="utf-8")

# ---------------------------------------------------------------------------
# Service: fresh high-importance channel + one more best-effort direct Activity
# launch after service startup. Background launch restrictions may ignore this on
# some Android builds, so it is only an additional fallback; the AlarmManager UI
# PendingIntent and full-screen notification remain the primary mechanisms.
# ---------------------------------------------------------------------------
service = service_path.read_text(encoding="utf-8")
service = service.replace(
    '    public static final String CHANNEL = "wake_guard_alarm_v4";',
    '    public static final String CHANNEL = "wake_guard_alarm_v5";',
    1)

needle2 = '''        if (testSession) {\n            try { AlarmActivity.launch(this); } catch (Throwable ignored) {}\n        }\n        if (Settings.canDrawOverlays(this)) ensureOverlay();\n'''
repl2 = '''        if (testSession) {\n            try { AlarmActivity.launch(this); } catch (Throwable ignored) {}\n        } else if (scheduledSession) {\n            // Secondary best-effort path for OEMs that suppress full-screen intents.\n            // The AlarmManager Activity PendingIntent above is still the main fallback.\n            new Handler(Looper.getMainLooper()).postDelayed(() -> {\n                try {\n                    if (!Prefs.active(AlarmService.this)) return;\n                    KeyguardManager km = getSystemService(KeyguardManager.class);\n                    PowerManager pm = getSystemService(PowerManager.class);\n                    boolean locked = km != null && km.isKeyguardLocked();\n                    boolean screenOff = pm != null && !pm.isInteractive();\n                    if (locked || screenOff) AlarmActivity.launch(AlarmService.this);\n                } catch (Throwable t) {\n                    try { Prefs.lastAlarmError(AlarmService.this,\n                            "LockUiFallback: " + t.getClass().getSimpleName()\n                                    + (t.getMessage() == null ? "" : ": " + t.getMessage())); }\n                    catch (Throwable ignored) {}\n                }\n            }, 1300L);\n        }\n        if (Settings.canDrawOverlays(this)) ensureOverlay();\n'''
if needle2 not in service:
    raise SystemExit("v060: service test/scheduled launch block not found")
service = service.replace(needle2, repl2, 1)
service_path.write_text(service, encoding="utf-8")

# ---------------------------------------------------------------------------
# MainActivity: make the actual Android full-screen-intent state explicit instead
# of relying on the user remembering whether the settings toggle was granted.
# Keep it in diagnostics so a screenshot immediately proves the permission state.
# ---------------------------------------------------------------------------
main = main_path.read_text(encoding="utf-8")
old = '''                    if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {\n                        if (diag.length() > 0) diag.append("\\n");\n                        diag.append("⚠ 全画面アラームが未許可です。③を押して許可してください");\n                    }\n'''
new = '''                    if (Build.VERSION.SDK_INT >= 34) {\n                        if (diag.length() > 0) diag.append("\\n");\n                        if (nm.canUseFullScreenIntent())\n                            diag.append("✅ 全画面アラーム権限：許可済み");\n                        else\n                            diag.append("⚠ 全画面アラーム権限：未許可。③を押してください");\n                    } else {\n                        if (diag.length() > 0) diag.append("\\n");\n                        diag.append("✅ 全画面アラーム：Android標準許可");\n                    }\n'''
if old not in main:
    raise SystemExit("v060: MainActivity FSI diagnostic block not found")
main = main.replace(old, new, 1)
main_path.write_text(main, encoding="utf-8")

# Same permanent signing key; version bump permits in-place update.
build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 15", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.6.0"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"v060: version bump failed code={code_n} name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.6.0 multi-path lock-screen UI fixes")
