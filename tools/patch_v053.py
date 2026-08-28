from pathlib import Path
import re
import runpy

# First apply all v0.5.2 fixes (durable audio + autosave + step crash safety).
runpy.run_path("tools/patch_v052.py", run_name="__main__")

root = Path("WakeGuard")
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
receiver_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmReceiver.java"
main_path = root / "app/src/main/java/jp/wakeguard/alarm/MainActivity.java"
prefs_path = root / "app/src/main/java/jp/wakeguard/alarm/Prefs.java"
build_path = root / "app/build.gradle.kts"

# ---------------------------------------------------------------------------
# Prefs: persist the last startup failure instead of allowing a process crash to
# leave the user with no explanation.
# ---------------------------------------------------------------------------
prefs = prefs_path.read_text(encoding="utf-8")
needle = '    public static void sessionEpochDay(Context c, long v) { p(c).edit().putLong("session_epoch_day", v).apply(); }\n'
insert = needle + '''\n    public static String lastAlarmError(Context c) { return p(c).getString("last_alarm_error", ""); }\n    public static void lastAlarmError(Context c, String v) { p(c).edit().putString("last_alarm_error", v == null ? "" : v).apply(); }\n'''
if 'lastAlarmError(Context c)' not in prefs:
    if needle not in prefs:
        raise SystemExit("Prefs insertion point not found")
    prefs = prefs.replace(needle, insert, 1)
prefs_path.write_text(prefs, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmService: create the notification channel as soon as the app opens, and
# make startup fail-safe so one OEM/API exception does not kill the whole app.
# ---------------------------------------------------------------------------
service = service_path.read_text(encoding="utf-8")
service = service.replace('    private static final String CHANNEL = "wake_guard_alarm";',
                          '    public static final String CHANNEL = "wake_guard_alarm";', 1)

old_channel = '''    private void createChannel() {\n        NotificationManager nm = getSystemService(NotificationManager.class);\n        NotificationChannel ch = new NotificationChannel(CHANNEL, "起床アラーム", NotificationManager.IMPORTANCE_HIGH);\n        ch.setDescription("歩数ミッション式の起床アラーム");\n        ch.setSound(null, null); // Actual alarm audio is controlled by this service.\n        ch.enableVibration(false);\n        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);\n        nm.createNotificationChannel(ch);\n    }\n'''
new_channel = '''    public static void ensureNotificationChannel(Context context) {\n        try {\n            NotificationManager nm = context.getSystemService(NotificationManager.class);\n            if (nm == null) return;\n            NotificationChannel ch = new NotificationChannel(\n                    CHANNEL, "起床アラーム", NotificationManager.IMPORTANCE_HIGH);\n            ch.setDescription("歩数ミッション式の起床アラーム");\n            ch.setSound(null, null); // Alarm audio is controlled by AlarmService.\n            ch.enableVibration(false);\n            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);\n            nm.createNotificationChannel(ch);\n        } catch (Throwable ignored) {\n            // Settings must remain usable even on OEMs with notification quirks.\n        }\n    }\n\n    private void createChannel() {\n        ensureNotificationChannel(this);\n    }\n'''
if old_channel not in service:
    raise SystemExit("AlarmService createChannel block not found")
service = service.replace(old_channel, new_channel, 1)

old_sig = '    @Override public int onStartCommand(Intent intent, int flags, int startId) {\n'
new_sig = '''    @Override public int onStartCommand(Intent intent, int flags, int startId) {\n        try {\n            return handleStartCommand(intent, flags, startId);\n        } catch (Throwable t) {\n            String msg = t.getClass().getSimpleName();\n            if (t.getMessage() != null && !t.getMessage().isEmpty()) msg += ": " + t.getMessage();\n            Prefs.lastAlarmError(this, msg);\n            Prefs.active(this, false);\n            running = false;\n            try { cleanupOutputs(); } catch (Throwable ignored) {}\n            try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) {}\n            try { stopSelf(); } catch (Throwable ignored) {}\n            return START_NOT_STICKY;\n        }\n    }\n\n    private int handleStartCommand(Intent intent, int flags, int startId) {\n        Prefs.lastAlarmError(this, "");\n'''
if old_sig not in service:
    raise SystemExit("AlarmService onStartCommand signature not found")
service = service.replace(old_sig, new_sig, 1)

# Make individual output components best-effort as well. A vibrator/wakelock OEM
# exception must not take down audio, the UI, and the whole process.
service = service.replace(
'''        if (wakeLock == null) {\n            PowerManager pm = getSystemService(PowerManager.class);\n            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeGuard:Alarm");\n            wakeLock.setReferenceCounted(false);\n            wakeLock.acquire(); // No timeout: user explicitly requested no time-based auto-stop.\n        }\n''',
'''        if (wakeLock == null) {\n            try {\n                PowerManager pm = getSystemService(PowerManager.class);\n                if (pm != null) {\n                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeGuard:Alarm");\n                    wakeLock.setReferenceCounted(false);\n                    wakeLock.acquire();\n                }\n            } catch (Throwable ignored) { wakeLock = null; }\n        }\n''', 1)

old_vib = '''        if (vibrator == null) vibrator = getSystemService(Vibrator.class);\n        if (vibrator != null && vibrator.hasVibrator()) {\n            String mode = Prefs.vibration(this);\n            long[] timings;\n            int[] amps;\n            if ("STRONG".equals(mode)) {\n                timings = new long[]{0, 1400, 180, 1400, 180};\n                amps = new int[]{0, 255, 0, 255, 0};\n            } else {\n                timings = new long[]{0, 350, 90, 850, 160, 1500, 70, 280, 220, 1100, 130};\n                amps = new int[]{0, 255, 0, 220, 0, 255, 0, 180, 0, 255, 0};\n            }\n            vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, 0));\n        }\n'''
new_vib = '''        try {\n            if (vibrator == null) vibrator = getSystemService(Vibrator.class);\n            if (vibrator != null && vibrator.hasVibrator()) {\n                String mode = Prefs.vibration(this);\n                long[] timings;\n                int[] amps;\n                if ("STRONG".equals(mode)) {\n                    timings = new long[]{0, 1400, 180, 1400, 180};\n                    amps = new int[]{0, 255, 0, 255, 0};\n                } else {\n                    timings = new long[]{0, 350, 90, 850, 160, 1500, 70, 280, 220, 1100, 130};\n                    amps = new int[]{0, 255, 0, 220, 0, 255, 0, 180, 0, 255, 0};\n                }\n                vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, 0));\n            }\n        } catch (Throwable ignored) { vibrator = null; }\n'''
if old_vib not in service:
    raise SystemExit("AlarmService vibration block not found")
service = service.replace(old_vib, new_vib, 1)

# cleanupOutputs itself must be safe if startup only got halfway through.
service = service.replace('        guardHandler.removeCallbacks(guard);',
                          '        if (guardHandler != null) guardHandler.removeCallbacks(guard);', 1)
service = service.replace('        if (sensorManager != null) sensorManager.unregisterListener(this);',
                          '        if (sensorManager != null) { try { sensorManager.unregisterListener(this); } catch (Throwable ignored) {} }', 1)
service_path.write_text(service, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmReceiver: a synchronous foreground-service launch denial should be
# recorded, not allowed to crash the receiver process.
# ---------------------------------------------------------------------------
receiver = receiver_path.read_text(encoding="utf-8")
receiver = receiver.replace(
'''            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s); else context.startService(s);\n            return;\n''',
'''            try {\n                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s); else context.startService(s);\n            } catch (Throwable t) {\n                Prefs.lastAlarmError(context, t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));\n                Prefs.active(context, false);\n            }\n            return;\n''', 1)
receiver = receiver.replace(
'''        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s); else context.startService(s);\n''',
'''        try {\n            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s); else context.startService(s);\n        } catch (Throwable t) {\n            Prefs.lastAlarmError(context, t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));\n            Prefs.active(context, false);\n        }\n''', 1)
receiver_path.write_text(receiver, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmActivity: lifecycle cleanup should never cause a secondary crash.
# ---------------------------------------------------------------------------
activity = activity_path.read_text(encoding="utf-8")
activity = activity.replace(
'        if (updates != null) unregisterReceiver(updates);',
'        if (updates != null) { try { unregisterReceiver(updates); } catch (Throwable ignored) {} }', 1)
activity_path.write_text(activity, encoding="utf-8")

# ---------------------------------------------------------------------------
# MainActivity: create channel BEFORE any alarm rings, add a direct lock-screen
# notification settings button, and catch test-start failures in the UI.
# ---------------------------------------------------------------------------
main = main_path.read_text(encoding="utf-8")
main = main.replace(
'''    @Override protected void onCreate(Bundle b) {\n        super.onCreate(b);\n        buildUi();\n''',
'''    @Override protected void onCreate(Bundle b) {\n        super.onCreate(b);\n        // Create the alarm notification channel immediately. This makes the OEM\n        // lock-screen notification controls available before the first alarm rings.\n        AlarmService.ensureNotificationChannel(this);\n        buildUi();\n''', 1)

old_buttons = '''        addSettingsButton("① 正確なアラームの状態を確認", this::openExactAlarm);\n        addSettingsButton("② 全画面アラームを許可", this::openFullScreenIntent);\n        addSettingsButton("③ 他のアプリの上に表示を許可", this::openOverlay);\n        addSettingsButton("④ バッテリー最適化から除外", this::openBatteryOptimization);\n        addSettingsButton("⑤ アプリ情報を開く → realmeの『ロック画面に表示』『バックグラウンドでポップアップ』を許可", this::openAppDetails);\n'''
new_buttons = '''        addSettingsButton("① 正確なアラームの状態を確認", this::openExactAlarm);\n        addSettingsButton("② ロック画面通知を設定（鳴らす前に設定できます）", this::openAlarmNotificationChannel);\n        addSettingsButton("③ 全画面アラームを許可", this::openFullScreenIntent);\n        addSettingsButton("④ 他のアプリの上に表示を許可", this::openOverlay);\n        addSettingsButton("⑤ バッテリー最適化から除外", this::openBatteryOptimization);\n        addSettingsButton("⑥ アプリ情報を開く → realmeの『ロック画面に表示』『バックグラウンドでポップアップ』を許可", this::openAppDetails);\n'''
if old_buttons not in main:
    raise SystemExit("MainActivity settings button block not found")
main = main.replace(old_buttons, new_buttons, 1)

old_test = '''        test.setOnClickListener(v -> {\n            saveAll();\n            Intent s = new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_FIRE_TEST);\n            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);\n        });\n'''
new_test = '''        test.setOnClickListener(v -> {\n            saveAll();\n            AlarmService.ensureNotificationChannel(this);\n            Prefs.lastAlarmError(this, "");\n            Intent s = new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_FIRE_TEST);\n            try {\n                if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);\n            } catch (Throwable t) {\n                String msg = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());\n                Prefs.lastAlarmError(this, msg);\n                Toast.makeText(this, "アラーム開始に失敗: " + msg, Toast.LENGTH_LONG).show();\n            }\n        });\n'''
if old_test not in main:
    raise SystemExit("MainActivity test block not found")
main = main.replace(old_test, new_test, 1)

insert_before = '''    private void openFullScreenIntent() {\n'''
channel_method = '''    private void openAlarmNotificationChannel() {\n        AlarmService.ensureNotificationChannel(this);\n        try {\n            Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);\n            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());\n            i.putExtra(Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL);\n            startActivity(i);\n        } catch (Throwable t) {\n            openAppDetails();\n        }\n    }\n\n'''
if channel_method.strip() not in main:
    if insert_before not in main:
        raise SystemExit("MainActivity full-screen method insertion point not found")
    main = main.replace(insert_before, channel_method + insert_before, 1)

# When returning to the app after a failed test, show the captured exception.
old_resume = '''        if (timeButton != null) {\n            refresh();\n            showRecordCelebrationIfNeeded();\n        }\n'''
new_resume = '''        if (timeButton != null) {\n            refresh();\n            showRecordCelebrationIfNeeded();\n            String err = Prefs.lastAlarmError(this);\n            if (err != null && !err.isEmpty()) {\n                Toast.makeText(this, "前回のアラーム開始エラー: " + err, Toast.LENGTH_LONG).show();\n            }\n        }\n'''
if old_resume not in main:
    raise SystemExit("MainActivity onResume block not found")
main = main.replace(old_resume, new_resume, 1)
main_path.write_text(main, encoding="utf-8")

# v0.5.3: same permanent signing key, higher versionCode for in-place update.
build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 8", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.5.3"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"Version bump failed: versionCode matches={code_n}, versionName matches={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.5.3 startup crash containment + pre-alarm lock-screen settings patch")
