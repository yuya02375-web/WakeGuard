from pathlib import Path
import re
import runpy

# Apply all previous fixes first.
runpy.run_path("tools/patch_v054.py", run_name="__main__")

root = Path("WakeGuard")
main_path = root / "app/src/main/java/jp/wakeguard/alarm/MainActivity.java"
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
prefs_path = root / "app/src/main/java/jp/wakeguard/alarm/Prefs.java"
manifest_path = root / "app/src/main/AndroidManifest.xml"
build_path = root / "app/build.gradle.kts"
app_path = root / "app/src/main/java/jp/wakeguard/alarm/WakeGuardApp.java"

# ---------------------------------------------------------------------------
# Prefs: make the user-visible step target synchronous and remember the system
# alarm-stream volume so WakeGuard can restore it after the alarm ends.
# ---------------------------------------------------------------------------
prefs = prefs_path.read_text(encoding="utf-8")
prefs = prefs.replace(
    '    public static void steps(Context c, int v) { p(c).edit().putInt("steps", v).apply(); }',
    '    public static void steps(Context c, int v) { p(c).edit().putInt("steps", v).commit(); }',
    1)
needle = '    public static void sessionEpochDay(Context c, long v) { p(c).edit().putLong("session_epoch_day", v).apply(); }\n'
extra = '''    public static int originalAlarmVolume(Context c) { return p(c).getInt("original_alarm_volume", -1); }
    public static void originalAlarmVolume(Context c, int v) { p(c).edit().putInt("original_alarm_volume", v).apply(); }

    public static String lastFatalError(Context c) { return p(c).getString("last_fatal_error", ""); }
    public static void lastFatalError(Context c, String v) { p(c).edit().putString("last_fatal_error", v == null ? "" : v).commit(); }
'''
if 'originalAlarmVolume(Context c)' not in prefs:
    if needle not in prefs:
        raise SystemExit("Prefs insertion point not found")
    prefs = prefs.replace(needle, needle + extra, 1)
prefs_path.write_text(prefs, encoding="utf-8")

# ---------------------------------------------------------------------------
# MainActivity: save the step target immediately as the user types. Previous
# versions only saved on lifecycle transitions and clamped values below 10,
# which made test values such as 1/3/5 look as if they were never saved.
# ---------------------------------------------------------------------------
main = main_path.read_text(encoding="utf-8")
if 'import android.text.*;' not in main:
    main = main.replace('import android.provider.Settings;\n', 'import android.provider.Settings;\nimport android.text.*;\n', 1)

old_steps_create = '        steps = new EditText(this); steps.setInputType(2); root.addView(steps);\n'
new_steps_create = '''        steps = new EditText(this); steps.setInputType(2); root.addView(steps);
        steps.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable e) {
                try {
                    String raw = e == null ? "" : e.toString().trim();
                    if (!raw.isEmpty()) {
                        int v = Math.max(1, Math.min(9999, Integer.parseInt(raw)));
                        Prefs.steps(MainActivity.this, v);
                    }
                } catch (Throwable ignored) {}
            }
        });
'''
if old_steps_create not in main:
    raise SystemExit("MainActivity steps creation line not found")
main = main.replace(old_steps_create, new_steps_create, 1)

main = main.replace(
    'if (!raw.isEmpty()) st = Math.max(10, Math.min(500, Integer.parseInt(raw)));',
    'if (!raw.isEmpty()) st = Math.max(1, Math.min(9999, Integer.parseInt(raw)));',
    1)

old_volume = '        volume = new SeekBar(this); volume.setMax(100); root.addView(volume);\n'
new_volume = '''        volume = new SeekBar(this); volume.setMax(100); root.addView(volume);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) Prefs.volume(MainActivity.this, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
'''
if old_volume not in main:
    raise SystemExit("MainActivity volume line not found")
main = main.replace(old_volume, new_volume, 1)

resume_needle = '''            String err = Prefs.lastAlarmError(this);
            if (err != null && !err.isEmpty()) {
                Toast.makeText(this, "前回のアラーム開始エラー: " + err, Toast.LENGTH_LONG).show();
            }
'''
resume_extra = resume_needle + '''            String fatal = Prefs.lastFatalError(this);
            if (fatal != null && !fatal.isEmpty()) {
                Toast.makeText(this, "前回クラッシュ: " + fatal, Toast.LENGTH_LONG).show();
                Prefs.lastFatalError(this, "");
            }
'''
if resume_needle not in main:
    raise SystemExit("MainActivity error display block not found")
main = main.replace(resume_needle, resume_extra, 1)
main_path.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmService: make sound independent of a muted/zero alarm stream by mapping
# WakeGuard's own 0-100 volume to STREAM_ALARM while active, and restore the
# user's previous system alarm volume after successful stop. Add a ToneGenerator
# fallback so a broken custom/default URI cannot produce a silent alarm.
# ---------------------------------------------------------------------------
service = service_path.read_text(encoding="utf-8")
field_needle = '    private MediaPlayer player;\n'
field_extra = '''    private MediaPlayer player;
    private ToneGenerator fallbackTone;
    private Handler toneHandler;
'''
if 'private ToneGenerator fallbackTone;' not in service:
    if field_needle not in service:
        raise SystemExit("AlarmService player field not found")
    service = service.replace(field_needle, field_extra, 1)

notification_needle = '''                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullPi, true)
                .build();
'''
notification_new = '''                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .build();
'''
if notification_needle not in service:
    raise SystemExit("AlarmService notification tail not found")
service = service.replace(notification_needle, notification_new, 1)

start_player_needle = '''        if (player == null) {
            player = createPlayer(Prefs.soundUri(this));
            if (player == null) player = createPlayer("");
        }
'''
start_player_new = '''        prepareAlarmVolume();

        if (player == null) {
            player = createPlayer(Prefs.soundUri(this));
            if (player == null) player = createPlayer("");
        }
        boolean playing = false;
        try { playing = player != null && player.isPlaying(); } catch (Throwable ignored) {}
        if (!playing) startFallbackTone();
'''
if start_player_needle not in service:
    raise SystemExit("AlarmService player startup block not found")
service = service.replace(start_player_needle, start_player_new, 1)

service = service.replace(
'''            float v = Math.max(0f, Math.min(1f, Prefs.volume(this) / 100f));
            p.setVolume(v, v);
            p.start();
''',
'''            p.setVolume(1f, 1f);
            p.start();
''',
    1)

insert_before = '    private void startStepSensor() {\n'
helpers = '''    private void prepareAlarmVolume() {
        try {
            AudioManager am = getSystemService(AudioManager.class);
            if (am == null || am.isVolumeFixed()) return;
            int original = Prefs.originalAlarmVolume(this);
            if (original < 0) {
                original = am.getStreamVolume(AudioManager.STREAM_ALARM);
                Prefs.originalAlarmVolume(this, original);
            }
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_ALARM));
            int percent = Math.max(0, Math.min(100, Prefs.volume(this)));
            int target = percent == 0 ? 0 : Math.max(1, Math.round(max * (percent / 100f)));
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
        } catch (Throwable t) {
            try { Prefs.lastAlarmError(this, "AlarmVolume: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}
        }
    }

    private void restoreAlarmVolume() {
        try {
            int original = Prefs.originalAlarmVolume(this);
            if (original < 0) return;
            AudioManager am = getSystemService(AudioManager.class);
            if (am != null && !am.isVolumeFixed()) am.setStreamVolume(AudioManager.STREAM_ALARM, original, 0);
        } catch (Throwable ignored) {
        } finally {
            try { Prefs.originalAlarmVolume(this, -1); } catch (Throwable ignored) {}
        }
    }

    private void startFallbackTone() {
        if (fallbackTone != null) return;
        try {
            fallbackTone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            if (toneHandler == null) toneHandler = new Handler(Looper.getMainLooper());
            Runnable[] loop = new Runnable[1];
            loop[0] = () -> {
                try {
                    if (!running && !Prefs.active(AlarmService.this)) return;
                    if (fallbackTone != null) fallbackTone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700);
                    if (toneHandler != null) toneHandler.postDelayed(loop[0], 1000);
                } catch (Throwable ignored) {}
            };
            toneHandler.post(loop[0]);
        } catch (Throwable t) {
            try { Prefs.lastAlarmError(this, "FallbackTone: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}
        }
    }

    private void stopFallbackTone() {
        try { if (toneHandler != null) toneHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}
        toneHandler = null;
        try { if (fallbackTone != null) { fallbackTone.stopTone(); fallbackTone.release(); } } catch (Throwable ignored) {}
        fallbackTone = null;
    }

'''
if helpers.strip() not in service:
    if insert_before not in service:
        raise SystemExit("AlarmService helper insertion point not found")
    service = service.replace(insert_before, helpers + insert_before, 1)

cleanup_needle = '''        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
'''
cleanup_new = '''        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        stopFallbackTone();
'''
if cleanup_needle not in service:
    raise SystemExit("AlarmService cleanup player block not found")
service = service.replace(cleanup_needle, cleanup_new, 1)

stop_needle = '''    private void stopAlarm() {
        running = false;
        Prefs.active(this, false);
        cleanupOutputs();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
'''
stop_new = '''    private void stopAlarm() {
        running = false;
        Prefs.active(this, false);
        cleanupOutputs();
        restoreAlarmVolume();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
'''
if stop_needle not in service:
    raise SystemExit("AlarmService stopAlarm block not found")
service = service.replace(stop_needle, stop_new, 1)

service = service.replace(
'''                if (Settings.canDrawOverlays(AlarmService.this)) {
                    ensureOverlay();
                } else if (!AlarmActivity.visible) {
                    try { AlarmActivity.launch(AlarmService.this); } catch (Throwable ignored) {}
                }
''',
'''                if (Settings.canDrawOverlays(AlarmService.this)) {
                    ensureOverlay();
                }
''',
    1)
service = service.replace('guardHandler.postDelayed(this, 700)', 'guardHandler.postDelayed(this, 3000)', 1)
service_path.write_text(service, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmActivity: make the single escape-recovery attempt less race-prone and
# direct hardware volume buttons to the alarm stream.
# ---------------------------------------------------------------------------
activity = activity_path.read_text(encoding="utf-8")
activity = activity.replace(
'''        super.onCreate(b);
        setShowWhenLocked(true);
''',
'''        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        setShowWhenLocked(true);
''',
    1)
if 'import android.media.AudioManager;' not in activity:
    activity = activity.replace('import android.graphics.Color;\n', 'import android.graphics.Color;\nimport android.media.AudioManager;\n', 1)
activity = activity.replace('            }, 250);', '            }, 1000);', 1)
activity_path.write_text(activity, encoding="utf-8")

# ---------------------------------------------------------------------------
# App-wide crash recorder. If a vendor-specific fatal exception remains, the
# next launch shows its class/message instead of leaving us blind.
# ---------------------------------------------------------------------------
app_path.write_text('''package jp.wakeguard.alarm;

import android.app.Application;

public class WakeGuardApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String msg = error == null ? "Unknown" : error.getClass().getSimpleName();
                if (error != null && error.getMessage() != null && !error.getMessage().isEmpty())
                    msg += ": " + error.getMessage();
                Prefs.lastFatalError(this, msg);
            } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
''', encoding="utf-8")

manifest = manifest_path.read_text(encoding="utf-8")
if 'android.permission.MODIFY_AUDIO_SETTINGS' not in manifest:
    manifest = manifest.replace(
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />\n',
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />\n    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n',
        1)
manifest = manifest.replace(
'''    <application
        android:allowBackup="true"
''',
'''    <application
        android:name=".WakeGuardApp"
        android:allowBackup="true"
''',
    1)
manifest_path.write_text(manifest, encoding="utf-8")

# v0.5.5: same permanent signing key, higher versionCode for in-place update.
build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 10", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.5.5"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"Version bump failed: code={code_n}, name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.5.5 step-save + sound + stability fixes")