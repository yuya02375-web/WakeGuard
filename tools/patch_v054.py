from pathlib import Path
import re
import runpy

# Apply all previous fixes first.
runpy.run_path("tools/patch_v053.py", run_name="__main__")

root = Path("WakeGuard")
main_path = root / "app/src/main/java/jp/wakeguard/alarm/MainActivity.java"
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
scheduler_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmScheduler.java"
build_path = root / "app/build.gradle.kts"

# ---------------------------------------------------------------------------
# AlarmScheduler: no settings/navigation action may crash the app just because
# rescheduling fails on an OEM. Android requires permission checks before exact
# alarms, but some devices still throw synchronously, so guard the whole method.
# ---------------------------------------------------------------------------
scheduler = scheduler_path.read_text(encoding="utf-8")
old_reschedule = '''    public static void reschedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null || !canScheduleExact(c)) return;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        // Clear a broad window so time/day edits never leave stale registrations.
        // 120 days is enough to cover 14 occurrences even for a once-a-week alarm.
        for (int i = -2; i <= 120; i++) {
            LocalDate d = today.plusDays(i);
            PendingIntent pi = firePi(c, d, PendingIntent.FLAG_NO_CREATE);
            if (pi != null) am.cancel(pi);
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

            PendingIntent fire = firePi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);
            Intent show = new Intent(c, MainActivity.class);
            PendingIntent showPi = PendingIntent.getActivity(c, requestCode(d) + 100000,
                    show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(
                    when.toInstant().toEpochMilli(), showPi);
            am.setAlarmClock(info, fire);
            scheduled++;
        }
    }
'''
new_reschedule = '''    public static void reschedule(Context c) {
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            if (am == null || !canScheduleExact(c)) return;
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);

            for (int i = -2; i <= 120; i++) {
                LocalDate d = today.plusDays(i);
                PendingIntent pi = firePi(c, d, PendingIntent.FLAG_NO_CREATE);
                if (pi != null) am.cancel(pi);
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

                PendingIntent fire = firePi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);
                Intent show = new Intent(c, MainActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(c, requestCode(d) + 100000,
                        show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(
                        when.toInstant().toEpochMilli(), showPi);
                am.setAlarmClock(info, fire);
                scheduled++;
            }
        } catch (Throwable t) {
            // Never crash MainActivity/onPause merely because an OEM rejected an alarm.
            try {
                Prefs.lastAlarmError(c, "AlarmScheduler: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            } catch (Throwable ignored) {}
        }
    }
'''
if old_reschedule not in scheduler:
    raise SystemExit("AlarmScheduler reschedule block not found")
scheduler = scheduler.replace(old_reschedule, new_reschedule, 1)

old_recovery = '''    public static void scheduleRecoveryIfActive(Context c) {
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
    }
'''
new_recovery = '''    public static void scheduleRecoveryIfActive(Context c) {
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
'''
if old_recovery not in scheduler:
    raise SystemExit("AlarmScheduler recovery block not found")
scheduler = scheduler.replace(old_recovery, new_recovery, 1)
scheduler_path.write_text(scheduler, encoding="utf-8")

# ---------------------------------------------------------------------------
# MainActivity: ColorOS/realme may not expose lock-screen controls until the app
# has actually posted a notification. Post a silent primer notification first,
# then open app-level notification settings (more compatible than channel-only
# settings on OEM builds). Every settings intent is guarded.
# ---------------------------------------------------------------------------
main = main_path.read_text(encoding="utf-8")
main = main.replace(
    'addSettingsButton("② ロック画面通知を設定（鳴らす前に設定できます）", this::openAlarmNotificationChannel);',
    'addSettingsButton("② ロック画面通知を準備して設定", this::prepareLockScreenNotificationSettings);',
    1)

old_channel_method = '''    private void openAlarmNotificationChannel() {
        AlarmService.ensureNotificationChannel(this);
        try {
            Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            i.putExtra(Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL);
            startActivity(i);
        } catch (Throwable t) {
            openAppDetails();
        }
    }

'''
new_channel_method = '''    private void prepareLockScreenNotificationSettings() {
        AlarmService.ensureNotificationChannel(this);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
            Toast.makeText(this, "通知を許可したあと、②をもう一度押してください", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                Notification primer = new Notification.Builder(this, AlarmService.CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("起床ガード")
                        .setContentText("ロック画面通知の設定を準備しました")
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true)
                        .build();
                nm.notify(43, primer);
                // Keep it around briefly so ColorOS/realme registers the channel as used.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { nm.cancel(43); } catch (Throwable ignored) {}
                }, 15000L);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "通知準備で失敗: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::openAppNotificationSettings, 350L);
    }

    private void openAppNotificationSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            if (i.resolveActivity(getPackageManager()) != null) {
                startActivity(i);
                return;
            }
        } catch (Throwable ignored) {}
        openAppDetails();
    }

'''
if old_channel_method not in main:
    raise SystemExit("v0.5.3 channel settings method not found")
main = main.replace(old_channel_method, new_channel_method, 1)

# Replace the raw settings launchers with guarded versions so an OEM missing a
# Settings activity cannot kill WakeGuard.
old_settings = '''    private void openFullScreenIntent() {
        if (Build.VERSION.SDK_INT >= 34) startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:"+getPackageName())));
        else openAppDetails();
    }
    private void openOverlay() {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())));
    }
    private void openBatteryOptimization() {
        try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+getPackageName()))); }
        catch(Exception e) { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
    }
    private void openAppDetails() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())));
    }
'''
new_settings = '''    private void openFullScreenIntent() {
        if (Build.VERSION.SDK_INT < 34) { openAppDetails(); return; }
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.canUseFullScreenIntent()) {
                Toast.makeText(this, "全画面アラーム：許可済み", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName()));
            if (i.resolveActivity(getPackageManager()) != null) { startActivity(i); return; }
        } catch (Throwable ignored) {}
        openAppDetails();
    }
    private void openOverlay() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            if (i.resolveActivity(getPackageManager()) != null) { startActivity(i); return; }
        } catch (Throwable ignored) {}
        openAppDetails();
    }
    private void openBatteryOptimization() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            if (i.resolveActivity(getPackageManager()) != null) { startActivity(i); return; }
        } catch (Throwable ignored) {}
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (i.resolveActivity(getPackageManager()) != null) { startActivity(i); return; }
        } catch (Throwable ignored) {}
        openAppDetails();
    }
    private void openAppDetails() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            if (i.resolveActivity(getPackageManager()) != null) startActivity(i);
            else Toast.makeText(this, "端末の設定アプリからWakeGuardを開いてください", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "設定画面を開けません: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }
'''
if old_settings not in main:
    raise SystemExit("v0.5.3 settings launcher block not found")
main = main.replace(old_settings, new_settings, 1)

# Guard exact-alarm settings as well.
old_exact = '''    private void openExactAlarm() {
        if (AlarmScheduler.canScheduleExact(this)) {
            Toast.makeText(this, "正確なアラーム：許可済み", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT <= 32) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName())));
        } else {
            openAppDetails();
        }
    }
'''
new_exact = '''    private void openExactAlarm() {
        try {
            if (AlarmScheduler.canScheduleExact(this)) {
                Toast.makeText(this, "正確なアラーム：許可済み", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT <= 32) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                if (i.resolveActivity(getPackageManager()) != null) { startActivity(i); return; }
            }
        } catch (Throwable ignored) {}
        openAppDetails();
    }
'''
if old_exact not in main:
    raise SystemExit("v0.5.3 exact alarm method not found")
main = main.replace(old_exact, new_exact, 1)
main_path.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmService: harden callbacks that run later on the main thread. The previous
# onStartCommand catch cannot catch exceptions thrown from Handler/sensor callbacks.
# ---------------------------------------------------------------------------
service = service_path.read_text(encoding="utf-8")
old_guard = '''    private final Runnable guard = new Runnable() {
        @Override public void run() {
            if (!running || !Prefs.active(AlarmService.this)) return;

            // If overlay permission is granted, this window follows the user across Home/Recents.
            // Otherwise keep trying the lock-screen activity as a fallback.
            if (Settings.canDrawOverlays(AlarmService.this)) {
                ensureOverlay();
            } else if (!AlarmActivity.visible) {
                try { AlarmActivity.launch(AlarmService.this); } catch (Throwable ignored) {}
            }
            guardHandler.postDelayed(this, 700);
        }
    };
'''
new_guard = '''    private final Runnable guard = new Runnable() {
        @Override public void run() {
            try {
                if (!running || !Prefs.active(AlarmService.this)) return;
                if (Settings.canDrawOverlays(AlarmService.this)) {
                    ensureOverlay();
                } else if (!AlarmActivity.visible) {
                    try { AlarmActivity.launch(AlarmService.this); } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                try {
                    Prefs.lastAlarmError(AlarmService.this, "Guard: " + t.getClass().getSimpleName()
                            + (t.getMessage() == null ? "" : ": " + t.getMessage()));
                } catch (Throwable ignored) {}
            }
            try {
                if (guardHandler != null && running && Prefs.active(AlarmService.this))
                    guardHandler.postDelayed(this, 700);
            } catch (Throwable ignored) {}
        }
    };
'''
if old_guard not in service:
    raise SystemExit("AlarmService guard block not found")
service = service.replace(old_guard, new_guard, 1)

# startStepSensor caught only RuntimeException before; use Throwable for vendor bugs.
service = service.replace('        } catch (RuntimeException e) {\n            // Includes SecurityException.',
                          '        } catch (Throwable e) {\n            // Includes SecurityException and OEM sensor failures.', 1)

old_sensor = '''    @Override public void onSensorChanged(SensorEvent event) {
        if (!running && !Prefs.active(this)) return;
        int steps;
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            float base = Prefs.baseline(this);
            if (base < 0) {
                Prefs.baseline(this, event.values[0]);
                steps = 0;
            } else {
                steps = Math.max(0, Math.round(event.values[0] - base));
            }
        } else {
            steps = Prefs.currentSteps(this) + 1;
        }
        Prefs.currentSteps(this, steps);
        updateViews();
        Intent u = new Intent(ACTION_UPDATE).setPackage(getPackageName()).putExtra("steps", steps);
        sendBroadcast(u);
    }
'''
new_sensor = '''    @Override public void onSensorChanged(SensorEvent event) {
        try {
            if (!running && !Prefs.active(this)) return;
            if (event == null || event.sensor == null || event.values == null || event.values.length == 0) return;
            int steps;
            if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                float base = Prefs.baseline(this);
                if (base < 0) {
                    Prefs.baseline(this, event.values[0]);
                    steps = 0;
                } else {
                    steps = Math.max(0, Math.round(event.values[0] - base));
                }
            } else {
                steps = Prefs.currentSteps(this) + 1;
            }
            Prefs.currentSteps(this, steps);
            updateViews();
            Intent u = new Intent(ACTION_UPDATE).setPackage(getPackageName()).putExtra("steps", steps);
            sendBroadcast(u);
        } catch (Throwable t) {
            try {
                Prefs.lastAlarmError(this, "Sensor: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            } catch (Throwable ignored) {}
        }
    }
'''
if old_sensor not in service:
    raise SystemExit("AlarmService onSensorChanged block not found")
service = service.replace(old_sensor, new_sensor, 1)

# Make final cleanup totally best-effort.
service = service.replace('        if (vibrator != null) vibrator.cancel();',
                          '        if (vibrator != null) { try { vibrator.cancel(); } catch (Throwable ignored) {} }', 1)
service = service.replace('        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();',
                          '        if (wakeLock != null) { try { if (wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {} }', 1)
service_path.write_text(service, encoding="utf-8")

# v0.5.4: same permanent signing key, higher versionCode for in-place update.
build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 9", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.5.4"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"Version bump failed: code={code_n}, name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.5.4 OEM settings + callback crash fixes")
