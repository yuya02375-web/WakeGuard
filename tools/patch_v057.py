from pathlib import Path
import re
import runpy

runpy.run_path("tools/patch_v056.py", run_name="__main__")

root = Path("WakeGuard")
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
scheduler_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmScheduler.java"
main_path = root / "app/src/main/java/jp/wakeguard/alarm/MainActivity.java"
manifest_path = root / "app/src/main/AndroidManifest.xml"
build_path = root / "app/build.gradle.kts"

# ---------------------------------------------------------------------------
# AlarmScheduler: let AlarmManager launch the full-screen AlarmActivity directly.
# This is more reliable on modern Android/ColorOS than starting an Activity later
# from a background Service. Keep cancellation support for old broadcast PIs.
# ---------------------------------------------------------------------------
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

    private static PendingIntent oldBroadcastPi(Context c, LocalDate date, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra("epochDay", date.toEpochDay());
        return PendingIntent.getBroadcast(c, requestCode(date), i,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent activityFirePi(Context c, LocalDate date, int flags) {
        Intent i = new Intent(c, AlarmActivity.class)
                .setAction(AlarmActivity.ACTION_SCHEDULED_FIRE)
                .putExtra(AlarmService.EXTRA_EPOCH_DAY, date.toEpochDay())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, requestCode(date), i,
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
                PendingIntent oldPi = oldBroadcastPi(c, d, PendingIntent.FLAG_NO_CREATE);
                if (oldPi != null) am.cancel(oldPi);
                PendingIntent newPi = activityFirePi(c, d, PendingIntent.FLAG_NO_CREATE);
                if (newPi != null) am.cancel(newPi);
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

                PendingIntent fire = activityFirePi(c, d, PendingIntent.FLAG_UPDATE_CURRENT);
                Intent show = new Intent(c, MainActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(c, requestCode(d) + 100000,
                        show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(
                        when.toInstant().toEpochMilli(), showPi);
                am.setAlarmClock(info, fire);
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

# ---------------------------------------------------------------------------
# AlarmActivity: direct AlarmManager entry point + stronger lock-screen flags.
# ---------------------------------------------------------------------------
activity_path.write_text(r'''package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.widget.*;

public class AlarmActivity extends Activity {
    public static final String ACTION_SCHEDULED_FIRE = "jp.wakeguard.alarm.UI_SCHEDULED_FIRE";
    public static volatile boolean visible = false;
    private TextView count;
    private Button stop;
    private BroadcastReceiver updates;

    public static void launch(Context c) {
        if (!Prefs.active(c)) return;
        Intent i = new Intent(c, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        c.startActivity(i);
    }

    private void handleLaunchIntent(Intent i) {
        if (i == null || !ACTION_SCHEDULED_FIRE.equals(i.getAction())) return;
        long epochDay = i.getLongExtra(AlarmService.EXTRA_EPOCH_DAY, -1L);
        // Mark active before the Service is created so Back/Home handling cannot race
        // the foreground-service startup on slower OEM builds.
        Prefs.active(this, true);
        try { AlarmScheduler.reschedule(this); } catch (Throwable ignored) {}
        Intent s = new Intent(this, AlarmService.class)
                .setAction(AlarmService.ACTION_FIRE_NEW)
                .putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        } catch (Throwable t) {
            try {
                Prefs.lastAlarmError(this, "ScheduledStart: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            } catch (Throwable ignored) {}
        }
        // Prevent recreation/onNewIntent from starting the same session twice.
        i.setAction(null);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        handleLaunchIntent(getIntent());
        hideSystemBars();
        buildUi();
        updates = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { render(); }
        };
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE));
            }
        } catch (Throwable t) {
            updates = null;
            try { Prefs.lastAlarmError(this, "AlarmUIReceiver: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}
        }
        render();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
        try { render(); } catch (Throwable ignored) {}
    }

    private void hideSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController ic = getWindow().getInsetsController();
                if (ic != null) {
                    ic.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    ic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } catch (Throwable ignored) {}
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 60, 40, 60);
        root.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("起きるまで止めません");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("タップでは歩数になりません。端末の歩数センサーだけを使用します。");
        note.setTextColor(0xFFBBBBBB);
        note.setTextSize(16);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.setMargins(0, 24, 0, 40);
        root.addView(note, np);

        count = new TextView(this);
        count.setTextColor(Color.WHITE);
        count.setTextSize(52);
        count.setGravity(Gravity.CENTER);
        root.addView(count);

        stop = new Button(this);
        stop.setText("歩数達成後に停止");
        stop.setEnabled(false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, 48, 0, 0);
        root.addView(stop, sp);
        stop.setOnClickListener(v -> {
            if (Prefs.currentSteps(this) >= Prefs.steps(this)) {
                try { startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP)); }
                catch (Throwable t) { try { Prefs.lastAlarmError(this, "StopUI: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }
                try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); }
            }
        });
        setContentView(root);
    }

    private void render() {
        if (count == null || stop == null) return;
        int s = Prefs.currentSteps(this);
        int target = Prefs.steps(this);
        boolean available = Prefs.stepSensorAvailable(this);
        count.setText(available ? (s + " / " + target + " 歩") : "歩数の権限またはセンサーを利用できません\n音・振動は停止せず継続しています");
        boolean done = available && s >= target;
        stop.setEnabled(done);
        stop.setText(done ? "停止する" : (available ? "あと " + Math.max(0, target - s) + " 歩" : "設定から強制停止してください"));
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        hideSystemBars();
        render();
    }

    @Override protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override protected void onDestroy() {
        visible = false;
        if (updates != null) { try { unregisterReceiver(updates); } catch (Throwable ignored) {} }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (!Prefs.active(this)) super.onBackPressed();
    }
}
''', encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmService: use a fresh high-importance channel, actively wake the display,
# and keep notification taps as "open alarm screen" only (no app stop action).
# ---------------------------------------------------------------------------
service = service_path.read_text(encoding="utf-8")
service = service.replace(
    '    public static final String CHANNEL = "wake_guard_alarm";',
    '    public static final String CHANNEL = "wake_guard_alarm_v2";',
    1)
service = service.replace(
    '    private PowerManager.WakeLock wakeLock;\n',
    '    private PowerManager.WakeLock wakeLock;\n    private PowerManager.WakeLock screenWakeLock;\n',
    1)
service = service.replace(
    '        startForeground(42, buildNotification());\n        startAlarmOutputs();',
    '        startForeground(42, buildNotification());\n        wakeScreenForAlarm();\n        startAlarmOutputs();',
    1)
service = service.replace(
    '                .setVisibility(Notification.VISIBILITY_PUBLIC)\n                .setOngoing(true)',
    '                .setVisibility(Notification.VISIBILITY_PUBLIC)\n                .setPriority(Notification.PRIORITY_MAX)\n                .setOngoing(true)',
    1)
insert_before = '    private void startAlarmOutputs() {\n'
wake_helper = '''    private void wakeScreenForAlarm() {\n        try {\n            PowerManager pm = getSystemService(PowerManager.class);\n            if (pm == null) return;\n            if (screenWakeLock != null) {\n                try { if (screenWakeLock.isHeld()) screenWakeLock.release(); } catch (Throwable ignored) {}\n            }\n            screenWakeLock = pm.newWakeLock(\n                    PowerManager.FULL_WAKE_LOCK\n                            | PowerManager.ACQUIRE_CAUSES_WAKEUP\n                            | PowerManager.ON_AFTER_RELEASE,\n                    "WakeGuard:ScreenAlarm");\n            screenWakeLock.setReferenceCounted(false);\n            screenWakeLock.acquire(30000L);\n        } catch (Throwable t) {\n            try { Prefs.lastAlarmError(this, "ScreenWake: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}\n        }\n    }\n\n'''
if wake_helper.strip() not in service:
    if insert_before not in service:
        raise SystemExit("AlarmService startAlarmOutputs insertion point not found")
    service = service.replace(insert_before, wake_helper + insert_before, 1)
cleanup_needle = '''        if (wakeLock != null) { try { if (wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {} }\n        wakeLock = null;\n'''
cleanup_repl = '''        if (wakeLock != null) { try { if (wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {} }\n        wakeLock = null;\n        if (screenWakeLock != null) { try { if (screenWakeLock.isHeld()) screenWakeLock.release(); } catch (Throwable ignored) {} }\n        screenWakeLock = null;\n'''
if cleanup_needle not in service:
    raise SystemExit("AlarmService wakelock cleanup block not found")
service = service.replace(cleanup_needle, cleanup_repl, 1)
service_path.write_text(service, encoding="utf-8")

# ---------------------------------------------------------------------------
# MainActivity diagnostics: show exactly why full-screen lock-screen display is
# unavailable instead of silently failing.
# ---------------------------------------------------------------------------
main = main_path.read_text(encoding="utf-8")
diag_needle = '''            if (alarmErr != null && !alarmErr.isEmpty()) {\n                if (diag.length() > 0) diag.append("\\n");\n                diag.append("アラームエラー: ").append(alarmErr);\n            }\n            diagnosticView.setText(diag.toString());\n'''
diag_repl = '''            if (alarmErr != null && !alarmErr.isEmpty()) {\n                if (diag.length() > 0) diag.append("\\n");\n                diag.append("アラームエラー: ").append(alarmErr);\n            }\n            try {\n                NotificationManager nm = getSystemService(NotificationManager.class);\n                if (nm != null) {\n                    if (Build.VERSION.SDK_INT >= 24 && !nm.areNotificationsEnabled()) {\n                        if (diag.length() > 0) diag.append("\\n");\n                        diag.append("⚠ 通知がOFFです。②から通知を許可してください");\n                    }\n                    if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {\n                        if (diag.length() > 0) diag.append("\\n");\n                        diag.append("⚠ 全画面アラームが未許可です。③を押して許可してください");\n                    }\n                    if (Build.VERSION.SDK_INT >= 26) {\n                        NotificationChannel ch = nm.getNotificationChannel(AlarmService.CHANNEL);\n                        if (ch != null && ch.getImportance() < NotificationManager.IMPORTANCE_HIGH) {\n                            if (diag.length() > 0) diag.append("\\n");\n                            diag.append("⚠ 起床アラーム通知の重要度が低いです。②から最優先にしてください");\n                        }\n                    }\n                }\n            } catch (Throwable ignored) {}\n            diagnosticView.setText(diag.toString());\n'''
if diag_needle not in main:
    raise SystemExit("MainActivity diagnostic block not found")
main = main.replace(diag_needle, diag_repl, 1)
main_path.write_text(main, encoding="utf-8")

# Manifest: avoid singleTask lock-screen task reuse bugs seen on some OEMs.
manifest = manifest_path.read_text(encoding="utf-8")
manifest = manifest.replace('android:launchMode="singleTask"', 'android:launchMode="singleTop"', 1)
manifest_path.write_text(manifest, encoding="utf-8")

# v0.5.7: same permanent signing key, higher versionCode for in-place update.
build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 12", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.5.7"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"Version bump failed: code={code_n}, name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.5.7 lock-screen/full-screen reliability fixes")
