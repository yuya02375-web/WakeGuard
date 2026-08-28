from pathlib import Path
import re
import runpy

# Apply all previous fixes first.
runpy.run_path("tools/patch_v057.py", run_name="__main__")

root = Path("WakeGuard")
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
scheduler_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmScheduler.java"
receiver_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmReceiver.java"
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
build_path = root / "app/build.gradle.kts"

# ---------------------------------------------------------------------------
# AlarmActivity: realme/ColorOS is crashing inside PhoneWindow.getInsetsController
# while the Activity is being launched. Completely remove WindowInsetsController
# use; hiding the system bars is cosmetic and must never be allowed to kill the
# alarm process. Start the foreground service before touching any optional window
# APIs, and do not try to dismiss the keyguard -- show on top of it instead.
# ---------------------------------------------------------------------------
activity = activity_path.read_text(encoding="utf-8")
old_prefix = '''    @Override protected void onCreate(Bundle b) {\n        super.onCreate(b);\n        setVolumeControlStream(AudioManager.STREAM_ALARM);\n        setShowWhenLocked(true);\n        setTurnScreenOn(true);\n        getWindow().addFlags(\n                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON\n                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED\n                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON\n                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);\n        handleLaunchIntent(getIntent());\n        hideSystemBars();\n        buildUi();\n'''
new_prefix = '''    @Override protected void onCreate(Bundle b) {\n        super.onCreate(b);\n\n        // Critical path first: start alarm audio/vibration before any OEM window work.\n        handleLaunchIntent(getIntent());\n\n        setVolumeControlStream(AudioManager.STREAM_ALARM);\n        try { setShowWhenLocked(true); } catch (Throwable ignored) {}\n        try { setTurnScreenOn(true); } catch (Throwable ignored) {}\n        try {\n            getWindow().addFlags(\n                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON\n                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED\n                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON\n                            | WindowManager.LayoutParams.FLAG_FULLSCREEN);\n        } catch (Throwable ignored) {}\n        buildUi();\n'''
if old_prefix not in activity:
    raise SystemExit("AlarmActivity onCreate prefix not found")
activity = activity.replace(old_prefix, new_prefix, 1)

start = activity.find('    private void hideSystemBars() {')
end = activity.find('    private void buildUi() {', start)
if start < 0 or end < 0:
    raise SystemExit("AlarmActivity hideSystemBars block not found")
activity = activity[:start] + '''    // Deliberately no WindowInsetsController here. On this realme/ColorOS build\n    // PhoneWindow.getInsetsController() can throw during Activity launch and kill\n    // the whole process, which also stops alarm audio and vibration.\n\n''' + activity[end:]
activity = activity.replace('        hideSystemBars();\n        render();', '        render();', 1)
activity_path.write_text(activity, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmReceiver: add a backup trigger. If the lock-screen Activity itself is
# killed by an OEM bug before the foreground service survives, AlarmManager will
# fire this broadcast shortly afterwards and restart the alarm service in a fresh
# process. It is idempotent when the service is already alive.
# ---------------------------------------------------------------------------
receiver_path.write_text(r'''package jp.wakeguard.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "jp.wakeguard.alarm.FIRE";
    public static final String ACTION_RECOVER = "jp.wakeguard.alarm.RECOVER";
    public static final String ACTION_BACKUP_FIRE = "jp.wakeguard.alarm.BACKUP_FIRE";

    private void startAlarmService(Context context, Intent s) {
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s);
            else context.startService(s);
        } catch (Throwable t) {
            try {
                Prefs.lastAlarmError(context, "ReceiverStart: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            } catch (Throwable ignored) {}
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_BACKUP_FIRE.equals(action)) {
            if (AlarmService.running && Prefs.active(context)) return;
            long epochDay = intent == null ? -1L : intent.getLongExtra("epochDay", -1L);
            Intent s;
            if (Prefs.active(context)) {
                s = new Intent(context, AlarmService.class).setAction(AlarmService.ACTION_RESTORE);
            } else {
                s = new Intent(context, AlarmService.class)
                        .setAction(AlarmService.ACTION_FIRE_NEW)
                        .putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay);
            }
            startAlarmService(context, s);
            return;
        }

        if (ACTION_RECOVER.equals(action)) {
            if (!Prefs.active(context)) return;
            Intent s = new Intent(context, AlarmService.class).setAction(AlarmService.ACTION_RESTORE);
            startAlarmService(context, s);
            return;
        }

        // Legacy broadcast trigger kept for upgrades from older scheduled alarms.
        try { AlarmScheduler.reschedule(context); } catch (Throwable ignored) {}
        long epochDay = intent == null ? -1L : intent.getLongExtra("epochDay", -1L);
        Intent s = new Intent(context, AlarmService.class)
                .setAction(AlarmService.ACTION_FIRE_NEW)
                .putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay);
        startAlarmService(context, s);
    }
}
''', encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmScheduler: schedule a backup broadcast 1.5s after each primary Activity
# alarm. This protects sound/vibration even if the OEM fails the Activity launch.
# ---------------------------------------------------------------------------
scheduler = scheduler_path.read_text(encoding="utf-8")
needle = '''    private static PendingIntent activityFirePi(Context c, LocalDate date, int flags) {\n        Intent i = new Intent(c, AlarmActivity.class)\n                .setAction(AlarmActivity.ACTION_SCHEDULED_FIRE)\n                .putExtra(AlarmService.EXTRA_EPOCH_DAY, date.toEpochDay())\n                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK\n                        | Intent.FLAG_ACTIVITY_SINGLE_TOP\n                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);\n        return PendingIntent.getActivity(c, requestCode(date), i,\n                flags | PendingIntent.FLAG_IMMUTABLE);\n    }\n'''
extra = needle + '''\n    private static PendingIntent backupFirePi(Context c, LocalDate date, int flags) {\n        Intent i = new Intent(c, AlarmReceiver.class)\n                .setAction(AlarmReceiver.ACTION_BACKUP_FIRE)\n                .putExtra("epochDay", date.toEpochDay());\n        return PendingIntent.getBroadcast(c, requestCode(date) + 200000, i,\n                flags | PendingIntent.FLAG_IMMUTABLE);\n    }\n'''
if needle not in scheduler:
    raise SystemExit("AlarmScheduler activityFirePi block not found")
scheduler = scheduler.replace(needle, extra, 1)

old_cancel = '''                PendingIntent newPi = activityFirePi(c, d, PendingIntent.FLAG_NO_CREATE);\n                if (newPi != null) am.cancel(newPi);\n'''
new_cancel = old_cancel + '''                PendingIntent backupPi = backupFirePi(c, d, PendingIntent.FLAG_NO_CREATE);\n                if (backupPi != null) am.cancel(backupPi);\n'''
if old_cancel not in scheduler:
    raise SystemExit("AlarmScheduler cancel block not found")
scheduler = scheduler.replace(old_cancel, new_cancel, 1)

old_schedule = '''                am.setAlarmClock(info, fire);\n                scheduled++;\n'''
new_schedule = '''                am.setAlarmClock(info, fire);\n\n                // Independent backup for OEM Activity-launch failures.\n                PendingIntent backup = backupFirePi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);\n                long backupAt = when.toInstant().toEpochMilli() + 1500L;\n                if (Build.VERSION.SDK_INT >= 23)\n                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, backupAt, backup);\n                else\n                    am.setExact(AlarmManager.RTC_WAKEUP, backupAt, backup);\n                scheduled++;\n'''
if old_schedule not in scheduler:
    raise SystemExit("AlarmScheduler schedule block not found")
scheduler = scheduler.replace(old_schedule, new_schedule, 1)
scheduler_path.write_text(scheduler, encoding="utf-8")

# Fresh notification channel because Android channel importance/lock-screen
# behavior cannot be raised after the user/system has created the old channel.
service = service_path.read_text(encoding="utf-8")
service = service.replace(
    '    public static final String CHANNEL = "wake_guard_alarm_v2";',
    '    public static final String CHANNEL = "wake_guard_alarm_v3";',
    1)
service_path.write_text(service, encoding="utf-8")

# v0.5.8: same permanent signing key, higher versionCode for in-place update.
build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 13", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.5.8"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"Version bump failed: code={code_n}, name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.5.8 realme lockscreen crash + backup alarm fixes")
