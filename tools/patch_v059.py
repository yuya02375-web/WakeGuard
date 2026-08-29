from pathlib import Path
import re
import runpy

runpy.run_path("tools/patch_v058.py", run_name="__main__")

root = Path("WakeGuard")
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
scheduler_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmScheduler.java"
build_path = root / "app/build.gradle.kts"

# Scheduled alarms now use the same proven path as the working foreground test:
# AlarmManager -> BroadcastReceiver -> foreground AlarmService -> full-screen notification.
scheduler_path.write_text(r'''package jp.wakeguard.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.time.*;

public final class AlarmScheduler {
    private AlarmScheduler() {}
    private static final int RECOVERY_REQUEST = 299999;

    private static int requestCode(LocalDate d) {
        return 300000 + (int)(d.toEpochDay() % 100000);
    }

    private static PendingIntent fireBroadcastPi(Context c, LocalDate date, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra("epochDay", date.toEpochDay());
        return PendingIntent.getBroadcast(c, requestCode(date), i,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    // v0.5.7/v0.5.8 used a direct Activity PendingIntent. Keep only to cancel
    // alarms already registered by those versions.
    private static PendingIntent oldActivityPi(Context c, LocalDate date, int flags) {
        Intent i = new Intent(c, AlarmActivity.class)
                .setAction(AlarmActivity.ACTION_SCHEDULED_FIRE)
                .putExtra(AlarmService.EXTRA_EPOCH_DAY, date.toEpochDay())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, requestCode(date), i,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent backupFirePi(Context c, LocalDate date, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(AlarmReceiver.ACTION_BACKUP_FIRE)
                .putExtra("epochDay", date.toEpochDay());
        return PendingIntent.getBroadcast(c, requestCode(date) + 200000, i,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean selected(LocalDate date, int mask) {
        int idx = date.getDayOfWeek().getValue() - 1;
        return (mask & (1 << idx)) != 0;
    }

    public static boolean canScheduleExact(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return false;
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    public static void reschedule(Context c) {
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            if (am == null || !canScheduleExact(c)) return;
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);

            for (int i = -2; i <= 120; i++) {
                LocalDate d = today.plusDays(i);
                PendingIntent fire = fireBroadcastPi(c, d, PendingIntent.FLAG_NO_CREATE);
                if (fire != null) am.cancel(fire);
                PendingIntent oldActivity = oldActivityPi(c, d, PendingIntent.FLAG_NO_CREATE);
                if (oldActivity != null) am.cancel(oldActivity);
                PendingIntent backup = backupFirePi(c, d, PendingIntent.FLAG_NO_CREATE);
                if (backup != null) am.cancel(backup);
            }
            if (!Prefs.enabled(c)) return;

            int mask = Prefs.dayMask(c);
            int scheduled = 0;
            Instant now = Instant.now();
            for (int i = 0; i <= 120 && scheduled < 14; i++) {
                LocalDate d = today.plusDays(i);
                if (!selected(d, mask)) continue;
                ZonedDateTime when = d.atTime(Prefs.hour(c), Prefs.minute(c)).atZone(zone);
                if (!when.toInstant().isAfter(now.plusSeconds(2))) continue;

                PendingIntent fire = fireBroadcastPi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);
                Intent show = new Intent(c, MainActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(c, requestCode(d) + 100000,
                        show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(
                        when.toInstant().toEpochMilli(), showPi);
                am.setAlarmClock(info, fire);

                PendingIntent backup = backupFirePi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);
                long backupAt = when.toInstant().toEpochMilli() + 2000L;
                if (Build.VERSION.SDK_INT >= 23)
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, backupAt, backup);
                else
                    am.setExact(AlarmManager.RTC_WAKEUP, backupAt, backup);
                scheduled++;
            }
        } catch (Throwable t) {
            try {
                Prefs.lastAlarmError(c, "AlarmScheduler: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            } catch (Throwable ignored) {}
        }
    }

    public static void scheduleRecoveryIfActive(Context c) {
        try {
            if (!Prefs.active(c) || !canScheduleExact(c)) return;
            AlarmManager am = c.getSystemService(AlarmManager.class);
            if (am == null) return;
            Intent i = new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_RECOVER);
            PendingIntent pi = PendingIntent.getBroadcast(c, RECOVERY_REQUEST, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = System.currentTimeMillis() + 3000L;
            Intent show = new Intent(c, MainActivity.class);
            PendingIntent showPi = PendingIntent.getActivity(c, RECOVERY_REQUEST + 1, show,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showPi), pi);
        } catch (Throwable t) {
            try {
                Prefs.lastAlarmError(c, "RecoveryAlarm: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            } catch (Throwable ignored) {}
        }
    }

    public static long nextTriggerMillis(Context c) {
        if (!Prefs.enabled(c)) return -1;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        int mask = Prefs.dayMask(c);
        for (int i = 0; i <= 120; i++) {
            LocalDate d = today.plusDays(i);
            if (!selected(d, mask)) continue;
            ZonedDateTime when = d.atTime(Prefs.hour(c), Prefs.minute(c)).atZone(zone);
            if (when.toInstant().isAfter(Instant.now())) return when.toInstant().toEpochMilli();
        }
        return -1;
    }
}
''', encoding="utf-8")

service = service_path.read_text(encoding="utf-8")
service = service.replace(
    '    private Sensor stepCounter;\n    private Sensor stepDetector;\n',
    '''    private Sensor stepCounter;\n    private Sensor stepDetector;\n    private Sensor linearAcceleration;\n    private Sensor gyroscope;\n    private long rejectStepsUntilElapsed = 0L;\n    private long lastAcceptedStepElapsed = 0L;\n    private float rawCounterLast = -1f;\n''',
    1)
service = service.replace(
    '    public static final String CHANNEL = "wake_guard_alarm_v3";',
    '    public static final String CHANNEL = "wake_guard_alarm_v4";',
    1)
service = service.replace(
'''            Prefs.active(this, true);\n            Prefs.baseline(this, -1f);\n            Prefs.currentSteps(this, 0);\n''',
'''            Prefs.active(this, true);\n            Prefs.baseline(this, -1f);\n            Prefs.currentSteps(this, 0);\n            rejectStepsUntilElapsed = 0L;\n            lastAcceptedStepElapsed = 0L;\n            rawCounterLast = -1f;\n''',
    1)
service = service.replace(
'''        // Full-screen alarm activity is still the first presentation on the lock screen.\n        // The overlay is the persistent anti-escape layer after the user navigates away.\n        try { AlarmActivity.launch(this); } catch (Throwable ignored) {}\n        if (Settings.canDrawOverlays(this)) ensureOverlay();\n''',
'''        // Scheduled/background alarms use Notification.setFullScreenIntent.\n        // Foreground tests keep the direct Activity launch that already works.\n        if (testSession) {\n            try { AlarmActivity.launch(this); } catch (Throwable ignored) {}\n        }\n        if (Settings.canDrawOverlays(this)) ensureOverlay();\n''',
    1)

old_build = '''        return new Notification.Builder(this, CHANNEL)\n                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)\n                .setContentTitle("起床ガード")\n                .setContentText("歩数ミッション完了まで停止しません")\n                .setCategory(Notification.CATEGORY_ALARM)\n                .setVisibility(Notification.VISIBILITY_PUBLIC)\n                .setPriority(Notification.PRIORITY_MAX)\n                .setOngoing(true)\n                .setAutoCancel(false)\n                .setOnlyAlertOnce(true)\n                .setContentIntent(fullPi)\n                .setFullScreenIntent(fullPi, true)\n                .build();\n'''
new_build = '''        Notification.Builder b = new Notification.Builder(this, CHANNEL)\n                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)\n                .setContentTitle("起床ガード")\n                .setContentText("歩数ミッション完了まで停止しません")\n                .setCategory(Notification.CATEGORY_ALARM)\n                .setVisibility(Notification.VISIBILITY_PUBLIC)\n                .setPriority(Notification.PRIORITY_MAX)\n                .setOngoing(true)\n                .setAutoCancel(false)\n                .setOnlyAlertOnce(true)\n                .setContentIntent(fullPi)\n                .setFullScreenIntent(fullPi, true);\n        if (Build.VERSION.SDK_INT >= 31) {\n            try { b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE); }\n            catch (Throwable ignored) {}\n        }\n        return b.build();\n'''
if old_build not in service:
    raise SystemExit("AlarmService notification build block not found")
service = service.replace(old_build, new_build, 1)

start = service.find('    private void startStepSensor() {')
end = service.find('    @Override public void onAccuracyChanged', start)
if start < 0 or end < 0:
    raise SystemExit("AlarmService sensor block not found")
new_sensor = r'''    private void startStepSensor() {
        // Only Android's dedicated step sensors may advance the mission.
        // Accelerometer/gyro are NEVER counted as steps; they only veto violent shaking.
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            Prefs.stepSensorAvailable(this, false);
            sensorManager = null;
            stepCounter = null;
            stepDetector = null;
            linearAcceleration = null;
            gyroscope = null;
            updateViews();
            return;
        }

        if (sensorManager != null) return;
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager == null) {
                Prefs.stepSensorAvailable(this, false);
                updateViews();
                return;
            }

            stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            Sensor use = stepCounter != null ? stepCounter : stepDetector;
            Prefs.detectorMode(this, stepCounter == null && stepDetector != null);

            boolean registered = false;
            if (use != null) {
                registered = sensorManager.registerListener(
                        this, use, SensorManager.SENSOR_DELAY_NORMAL);
            }
            Prefs.stepSensorAvailable(this, use != null && registered);

            try {
                linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
                if (linearAcceleration != null)
                    sensorManager.registerListener(this, linearAcceleration, SensorManager.SENSOR_DELAY_GAME);
            } catch (Throwable ignored) { linearAcceleration = null; }
            try {
                gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                if (gyroscope != null)
                    sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            } catch (Throwable ignored) { gyroscope = null; }
        } catch (Throwable e) {
            try {
                if (sensorManager != null) {
                    try { sensorManager.unregisterListener(this); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            sensorManager = null;
            stepCounter = null;
            stepDetector = null;
            linearAcceleration = null;
            gyroscope = null;
            Prefs.stepSensorAvailable(this, false);
        }
        updateViews();
    }

    private static float vectorMagnitude(float[] v) {
        if (v == null || v.length < 3) return 0f;
        return (float)Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private boolean stepAllowedNow(long now) {
        if (now < rejectStepsUntilElapsed) return false;
        return lastAcceptedStepElapsed == 0L || now - lastAcceptedStepElapsed >= 275L;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        try {
            if (!running && !Prefs.active(this)) return;
            if (event == null || event.sensor == null || event.values == null || event.values.length == 0) return;

            int type = event.sensor.getType();
            long now = SystemClock.elapsedRealtime();

            if (type == Sensor.TYPE_LINEAR_ACCELERATION) {
                if (vectorMagnitude(event.values) >= 8.5f)
                    rejectStepsUntilElapsed = Math.max(rejectStepsUntilElapsed, now + 1200L);
                return;
            }
            if (type == Sensor.TYPE_GYROSCOPE) {
                if (vectorMagnitude(event.values) >= 3.5f)
                    rejectStepsUntilElapsed = Math.max(rejectStepsUntilElapsed, now + 1200L);
                return;
            }

            int accepted = Prefs.currentSteps(this);
            if (type == Sensor.TYPE_STEP_COUNTER) {
                float raw = event.values[0];
                if (rawCounterLast < 0f) {
                    rawCounterLast = raw;
                    return;
                }
                int delta = Math.max(0, Math.round(raw - rawCounterLast));
                rawCounterLast = raw;
                if (delta <= 0) return;
                if (now < rejectStepsUntilElapsed) return;

                long elapsed = lastAcceptedStepElapsed == 0L ? 275L : Math.max(0L, now - lastAcceptedStepElapsed);
                int cadenceCap = Math.max(1, (int)(elapsed / 275L));
                int credit = Math.min(delta, cadenceCap);
                if (credit <= 0) return;
                accepted += credit;
                lastAcceptedStepElapsed = now;
            } else if (type == Sensor.TYPE_STEP_DETECTOR) {
                if (!stepAllowedNow(now)) return;
                accepted += 1;
                lastAcceptedStepElapsed = now;
            } else {
                return;
            }

            Prefs.currentSteps(this, accepted);
            updateViews();
            Intent u = new Intent(ACTION_UPDATE).setPackage(getPackageName()).putExtra("steps", accepted);
            sendBroadcast(u);
        } catch (Throwable t) {
            try {
                Prefs.lastAlarmError(this, "Sensor: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            } catch (Throwable ignored) {}
        }
    }

'''
service = service[:start] + new_sensor + service[end:]
service = service.replace(
'''        sensorManager = null;\n        stepCounter = null;\n        stepDetector = null;\n        removeOverlay();\n''',
'''        sensorManager = null;\n        stepCounter = null;\n        stepDetector = null;\n        linearAcceleration = null;\n        gyroscope = null;\n        rawCounterLast = -1f;\n        rejectStepsUntilElapsed = 0L;\n        lastAcceptedStepElapsed = 0L;\n        removeOverlay();\n''',
    1)
service_path.write_text(service, encoding="utf-8")

activity = activity_path.read_text(encoding="utf-8")
activity = activity.replace(
    '        note.setText("タップでは歩数になりません。端末の歩数センサーだけを使用します。");',
    '        note.setText("端末の歩数センサーだけを使用します。強い振りや不自然に速い動きは歩数から除外します。");',
    1)
activity_path.write_text(activity, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 14", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.5.9"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"Version bump failed: code={code_n}, name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.5.9 scheduled-FSI + anti-shake step fixes")
