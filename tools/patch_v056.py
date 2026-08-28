from pathlib import Path
import re
import runpy

# Apply all previous fixes first.
runpy.run_path("tools/patch_v055.py", run_name="__main__")

root = Path("WakeGuard")
main_path = root / "app/src/main/java/jp/wakeguard/alarm/MainActivity.java"
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
prefs_path = root / "app/src/main/java/jp/wakeguard/alarm/Prefs.java"
app_path = root / "app/src/main/java/jp/wakeguard/alarm/WakeGuardApp.java"
build_path = root / "app/build.gradle.kts"

# ---------------------------------------------------------------------------
# Prefs: store human-readable metadata for the selected audio. Use commit() for
# the selected file fields so a process death immediately after selection cannot
# leave the UI claiming a file was saved when only part of the state reached disk.
# ---------------------------------------------------------------------------
prefs = prefs_path.read_text(encoding="utf-8")
prefs = prefs.replace(
    '    public static void soundUri(Context c, String v) { p(c).edit().putString("sound_uri", v).apply(); }',
    '    public static void soundUri(Context c, String v) { p(c).edit().putString("sound_uri", v).commit(); }',
    1)
needle = '    public static void soundUri(Context c, String v) { p(c).edit().putString("sound_uri", v).commit(); }\n'
extra = '''    public static String soundName(Context c) { return p(c).getString("sound_name", ""); }\n    public static void soundName(Context c, String v) { p(c).edit().putString("sound_name", v == null ? "" : v).commit(); }\n    public static long soundBytes(Context c) { return p(c).getLong("sound_bytes", -1L); }\n    public static void soundBytes(Context c, long v) { p(c).edit().putLong("sound_bytes", v).commit(); }\n    public static long soundDurationMs(Context c) { return p(c).getLong("sound_duration_ms", -1L); }\n    public static void soundDurationMs(Context c, long v) { p(c).edit().putLong("sound_duration_ms", v).commit(); }\n'''
if 'soundName(Context c)' not in prefs:
    if needle not in prefs:
        raise SystemExit("Prefs sound insertion point not found")
    prefs = prefs.replace(needle, needle + extra, 1)
prefs = prefs.replace(
    '    public static void lastAlarmError(Context c, String v) { p(c).edit().putString("last_alarm_error", v == null ? "" : v).apply(); }',
    '    public static void lastAlarmError(Context c, String v) { p(c).edit().putString("last_alarm_error", v == null ? "" : v).commit(); }',
    1)
prefs_path.write_text(prefs, encoding="utf-8")

# ---------------------------------------------------------------------------
# MainActivity: show the actual selected file name/size/duration and only mark
# it saved after copying to app-private storage AND verifying MediaPlayer can
# prepare it. New selections use a new private file first, so a bad selection
# never destroys the previous working alarm sound.
# ---------------------------------------------------------------------------
main = main_path.read_text(encoding="utf-8")
if 'import android.database.Cursor;' not in main:
    main = main.replace('import android.content.pm.PackageManager;\n', 'import android.content.pm.PackageManager;\nimport android.database.Cursor;\n', 1)
if 'import android.media.MediaPlayer;' not in main:
    main = main.replace('import android.net.Uri;\n', 'import android.net.Uri;\nimport android.media.MediaPlayer;\n', 1)
if 'import android.provider.OpenableColumns;' not in main:
    main = main.replace('import android.provider.Settings;\n', 'import android.provider.Settings;\nimport android.provider.OpenableColumns;\n', 1)

main = main.replace(
    '    private TextView next, streakView, badgeView, monthlyStatsView, monthTitle;',
    '    private TextView next, streakView, badgeView, monthlyStatsView, monthTitle, soundStatusView, diagnosticView;',
    1)

old_sound_ui = '''        root.addView(label("アラーム音"));\n        soundButton = new Button(this); soundButton.setText("MP3 / 音声ファイルを選ぶ"); root.addView(soundButton);\n        soundButton.setOnClickListener(v -> {\n'''
new_sound_ui = '''        root.addView(label("アラーム音"));\n        soundButton = new Button(this); soundButton.setText("MP3 / 音声ファイルを選ぶ"); root.addView(soundButton);\n        soundStatusView = new TextView(this);\n        soundStatusView.setTextSize(14);\n        soundStatusView.setPadding(8, 8, 8, 16);\n        root.addView(soundStatusView);\n        soundButton.setOnClickListener(v -> {\n'''
if old_sound_ui not in main:
    raise SystemExit("MainActivity sound UI block not found")
main = main.replace(old_sound_ui, new_sound_ui, 1)

# Add persistent diagnostics to the screen; toasts can disappear before a screenshot.
old_next = '        next = new TextView(this); next.setTextSize(16); next.setPadding(0,20,0,20); root.addView(next);\n\n'
new_next = '''        next = new TextView(this); next.setTextSize(16); next.setPadding(0,20,0,20); root.addView(next);\n\n        diagnosticView = new TextView(this);\n        diagnosticView.setTextSize(13);\n        diagnosticView.setTextColor(0xFFB3261E);\n        diagnosticView.setPadding(12, 8, 12, 16);\n        diagnosticView.setVisibility(View.GONE);\n        root.addView(diagnosticView);\n\n'''
if old_next not in main:
    raise SystemExit("MainActivity next view insertion point not found")
main = main.replace(old_next, new_next, 1)

old_refresh_sound = '        String uri=Prefs.soundUri(this); soundButton.setText(uri.isEmpty()?"MP3 / 音声ファイルを選ぶ":"選択済みの音源を変更");\n'
new_refresh_sound = '''        String uri=Prefs.soundUri(this);\n        File audioFile = (uri != null && uri.startsWith("/")) ? new File(uri) : null;\n        boolean audioExists = audioFile != null && audioFile.isFile() && audioFile.length() > 0;\n        String audioName = Prefs.soundName(this);\n        if (audioName == null || audioName.isEmpty()) audioName = audioExists ? "保存済み音源（旧バージョン）" : "";\n        soundButton.setText(audioExists ? "音源を変更" : "MP3 / 音声ファイルを選ぶ");\n        if (soundStatusView != null) {\n            if (audioExists) {\n                long bytes = audioFile.length();\n                long duration = Prefs.soundDurationMs(this);\n                soundStatusView.setText("✅ 保存済み: " + audioName + "\\n" + formatBytes(bytes)\n                        + (duration >= 0 ? "  •  " + formatDuration(duration) : ""));\n                soundStatusView.setTextColor(0xFF146C2E);\n            } else if (uri != null && !uri.isEmpty()) {\n                soundStatusView.setText("⚠ 保存した音源ファイルが見つかりません。もう一度選んでください。");\n                soundStatusView.setTextColor(0xFFB3261E);\n            } else {\n                soundStatusView.setText("未選択（端末の標準アラーム音を使用）");\n                soundStatusView.setTextColor(0xFF666666);\n            }\n        }\n\n        if (diagnosticView != null) {\n            String fatal = Prefs.lastFatalError(this);\n            String alarmErr = Prefs.lastAlarmError(this);\n            StringBuilder diag = new StringBuilder();\n            if (fatal != null && !fatal.isEmpty()) diag.append("前回クラッシュ: ").append(fatal);\n            if (alarmErr != null && !alarmErr.isEmpty()) {\n                if (diag.length() > 0) diag.append("\\n");\n                diag.append("アラームエラー: ").append(alarmErr);\n            }\n            diagnosticView.setText(diag.toString());\n            diagnosticView.setVisibility(diag.length() == 0 ? View.GONE : View.VISIBLE);\n        }\n'''
if old_refresh_sound not in main:
    raise SystemExit("MainActivity refresh sound line not found")
main = main.replace(old_refresh_sound, new_refresh_sound, 1)

# Replace the v0.5.2/v0.5.5 file copy block with verified, metadata-aware import.
start = main.find('    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {')
end = main.find('    @Override protected void onPause() {', start)
if start < 0 or end < 0:
    raise SystemExit("MainActivity onActivityResult block not found")
new_result = '''    private String formatBytes(long bytes) {\n        if (bytes < 0) return "サイズ不明";\n        if (bytes < 1024) return bytes + " B";\n        if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024.0);\n        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));\n    }\n\n    private String formatDuration(long ms) {\n        long sec = Math.max(0, ms / 1000L);\n        return String.format("%d:%02d", sec / 60L, sec % 60L);\n    }\n\n    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {\n        super.onActivityResult(requestCode,resultCode,data);\n        if(requestCode==REQ_AUDIO && resultCode==RESULT_OK && data!=null && data.getData()!=null) {\n            Uri uri = data.getData();\n            String displayName = uri.getLastPathSegment();\n            long providerSize = -1L;\n            try (Cursor c = getContentResolver().query(uri,\n                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},\n                    null, null, null)) {\n                if (c != null && c.moveToFirst()) {\n                    int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);\n                    int si = c.getColumnIndex(OpenableColumns.SIZE);\n                    if (ni >= 0 && !c.isNull(ni)) displayName = c.getString(ni);\n                    if (si >= 0 && !c.isNull(si)) providerSize = c.getLong(si);\n                }\n            } catch (Throwable ignored) {}\n            if (displayName == null || displayName.trim().isEmpty()) displayName = "選択した音源";\n\n            int takeFlags = data.getFlags()\n                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);\n            if (takeFlags != 0) {\n                try { getContentResolver().takePersistableUriPermission(uri, takeFlags); }\n                catch (Throwable ignored) {}\n            }\n\n            File candidate = new File(getFilesDir(), "wakeguard_alarm_audio_" + System.currentTimeMillis() + ".bin");\n            boolean copied = false;\n            long copiedBytes = 0L;\n            long durationMs = -1L;\n            String failure = "";\n            try (InputStream in = getContentResolver().openInputStream(uri);\n                 FileOutputStream out = new FileOutputStream(candidate, false)) {\n                if (in == null) throw new IOException("音源を開けません");\n                byte[] buf = new byte[8192];\n                int n;\n                while ((n = in.read(buf)) != -1) {\n                    out.write(buf, 0, n);\n                    copiedBytes += n;\n                    if (copiedBytes > 100L * 1024L * 1024L) throw new IOException("音源が100MBを超えています");\n                }\n                out.flush();\n                if (copiedBytes <= 0) throw new IOException("音源が空です");\n                copied = true;\n            } catch (Throwable t) {\n                failure = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());\n                try { candidate.delete(); } catch (Throwable ignored) {}\n            }\n\n            // Do not claim success until Android itself can prepare the copied file.\n            if (copied) {\n                MediaPlayer probe = new MediaPlayer();\n                try {\n                    probe.setDataSource(candidate.getAbsolutePath());\n                    probe.prepare();\n                    durationMs = Math.max(0, probe.getDuration());\n                } catch (Throwable t) {\n                    copied = false;\n                    failure = "再生確認: " + t.getClass().getSimpleName()\n                            + (t.getMessage() == null ? "" : ": " + t.getMessage());\n                } finally {\n                    try { probe.release(); } catch (Throwable ignored) {}\n                }\n            }\n\n            if (copied) {\n                String old = Prefs.soundUri(this);\n                Prefs.soundUri(this, candidate.getAbsolutePath());\n                Prefs.soundName(this, displayName);\n                Prefs.soundBytes(this, copiedBytes > 0 ? copiedBytes : providerSize);\n                Prefs.soundDurationMs(this, durationMs);\n                if (old != null && old.startsWith(getFilesDir().getAbsolutePath())\n                        && !old.equals(candidate.getAbsolutePath())) {\n                    try { new File(old).delete(); } catch (Throwable ignored) {}\n                }\n                Toast.makeText(this, "保存・再生確認OK: " + displayName, Toast.LENGTH_LONG).show();\n            } else {\n                try { candidate.delete(); } catch (Throwable ignored) {}\n                Prefs.lastAlarmError(this, "AudioImport: " + failure);\n                Toast.makeText(this, "音源を保存できませんでした: " + failure, Toast.LENGTH_LONG).show();\n            }\n            refresh();\n        }\n    }\n\n'''
main = main[:start] + new_result + main[end:]

# Keep fatal diagnostics on screen instead of deleting them after one transient toast.
main = main.replace('                Prefs.lastFatalError(this, "");\n', '', 1)
main_path.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmService: v0.5.5 still had two unguarded cleanup calls and one narrow
# RuntimeException catch in the ACTIVITY_RECOGNITION path. Those can crash during
# service teardown/restart on vendor Android builds. Also reset all old outputs
# before starting a new test/session so repeated tests cannot reuse stale player,
# sensor, overlay, or WakeLock state.
# ---------------------------------------------------------------------------
service = service_path.read_text(encoding="utf-8")

new_session_needle = '''        if (newSession) {\n            Prefs.active(this, true);\n'''
new_session_repl = '''        if (newSession) {\n            // Repeated tests must start from a clean service state. A previous session\n            // can still be alive even if its Activity disappeared or the UI crashed.\n            running = false;\n            try { cleanupOutputs(); } catch (Throwable ignored) {}\n            try { restoreAlarmVolume(); } catch (Throwable ignored) {}\n            Prefs.active(this, true);\n'''
if new_session_needle not in service:
    raise SystemExit("AlarmService newSession block not found")
service = service.replace(new_session_needle, new_session_repl, 1)

service = service.replace(
    '        } catch (RuntimeException e) {\n            try {',
    '        } catch (Throwable e) {\n            try {',
    1)

old_cleanup = '''    private void cleanupOutputs() {\n        guardHandler.removeCallbacks(guard);\n        if (sensorManager != null) sensorManager.unregisterListener(this);\n        sensorManager = null;\n'''
new_cleanup = '''    private void cleanupOutputs() {\n        try { if (guardHandler != null) guardHandler.removeCallbacks(guard); } catch (Throwable ignored) {}\n        if (sensorManager != null) {\n            try { sensorManager.unregisterListener(this); } catch (Throwable ignored) {}\n        }\n        sensorManager = null;\n'''
if old_cleanup not in service:
    raise SystemExit("AlarmService cleanup head not found")
service = service.replace(old_cleanup, new_cleanup, 1)

service = service.replace(
'''    private void requestStopIfComplete() {\n        if (Prefs.stepSensorAvailable(this) && Prefs.currentSteps(this) >= Prefs.steps(this)) {\n            Intent stop = new Intent(this, AlarmService.class).setAction(ACTION_STOP);\n            startService(stop);\n        }\n    }\n''',
'''    private void requestStopIfComplete() {\n        try {\n            if (Prefs.stepSensorAvailable(this) && Prefs.currentSteps(this) >= Prefs.steps(this)) {\n                Intent stop = new Intent(this, AlarmService.class).setAction(ACTION_STOP);\n                startService(stop);\n            }\n        } catch (Throwable t) {\n            try { Prefs.lastAlarmError(this, "StopRequest: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}\n        }\n    }\n''', 1)

# Record why a selected custom file could not be played instead of silently losing the clue.
old_player_catch = '''        } catch (Exception e) {\n            try { p.release(); } catch (Exception ignored) {}\n            return null;\n        }\n'''
new_player_catch = '''        } catch (Throwable e) {\n            try {\n                if (custom != null && !custom.isEmpty())\n                    Prefs.lastAlarmError(this, "AudioPlay: " + e.getClass().getSimpleName()\n                            + (e.getMessage() == null ? "" : ": " + e.getMessage()));\n            } catch (Throwable ignored) {}\n            try { p.release(); } catch (Throwable ignored) {}\n            return null;\n        }\n'''
if old_player_catch not in service:
    raise SystemExit("AlarmService createPlayer catch not found")
service = service.replace(old_player_catch, new_player_catch, 1)
service_path.write_text(service, encoding="utf-8")

# ---------------------------------------------------------------------------
# AlarmActivity: remove the self-relaunch loop from onPause. It was deliberately
# aggressive, but on some OEMs repeated background Activity launches are unstable.
# The foreground service continues audio/vibration, notification taps reopen the
# Activity, and the overlay remains the persistent layer when permission is granted.
# Guard receiver registration and stop-service dispatch too.
# ---------------------------------------------------------------------------
activity = activity_path.read_text(encoding="utf-8")
old_reg = '''        if (Build.VERSION.SDK_INT >= 33) {\n            registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE), Context.RECEIVER_NOT_EXPORTED);\n        } else {\n            registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE));\n        }\n        render();\n'''
new_reg = '''        try {\n            if (Build.VERSION.SDK_INT >= 33) {\n                registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE), Context.RECEIVER_NOT_EXPORTED);\n            } else {\n                registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE));\n            }\n        } catch (Throwable t) {\n            updates = null;\n            try { Prefs.lastAlarmError(this, "AlarmUIReceiver: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}\n        }\n        render();\n'''
if old_reg not in activity:
    raise SystemExit("AlarmActivity receiver registration block not found")
activity = activity.replace(old_reg, new_reg, 1)

activity = activity.replace(
'''                startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP));\n                finishAndRemoveTask();\n''',
'''                try { startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP)); }\n                catch (Throwable t) { try { Prefs.lastAlarmError(this, "StopUI: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }\n                try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); }\n''', 1)

old_pause = '''    @Override protected void onPause() {\n        visible = false;\n        super.onPause();\n        if (Prefs.active(this)) {\n            new Handler(Looper.getMainLooper()).postDelayed(() -> {\n                if (Prefs.active(this) && !visible) {\n                    try { launch(this); } catch (Throwable ignored) {}\n                }\n            }, 1000);\n        }\n    }\n'''
new_pause = '''    @Override protected void onPause() {\n        visible = false;\n        super.onPause();\n        // Do not start Activities repeatedly from the background. The alarm service\n        // keeps sound/vibration alive; notification/overlay bring the user back.\n    }\n'''
if old_pause not in activity:
    raise SystemExit("AlarmActivity onPause relaunch block not found")
activity = activity.replace(old_pause, new_pause, 1)
activity_path.write_text(activity, encoding="utf-8")

# ---------------------------------------------------------------------------
# Crash recorder: include the first stack frame so the next screenshot identifies
# the exact class/method/line instead of only an exception class.
# ---------------------------------------------------------------------------
app = app_path.read_text(encoding="utf-8")
old_msg = '''                if (error != null && error.getMessage() != null && !error.getMessage().isEmpty())\n                    msg += ": " + error.getMessage();\n                Prefs.lastFatalError(this, msg);\n'''
new_msg = '''                if (error != null && error.getMessage() != null && !error.getMessage().isEmpty())\n                    msg += ": " + error.getMessage();\n                if (error != null && error.getStackTrace() != null && error.getStackTrace().length > 0) {\n                    StackTraceElement f = error.getStackTrace()[0];\n                    msg += " @ " + f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber();\n                }\n                Prefs.lastFatalError(this, msg);\n'''
if old_msg not in app:
    raise SystemExit("WakeGuardApp crash message block not found")
app = app.replace(old_msg, new_msg, 1)
app_path.write_text(app, encoding="utf-8")

# v0.5.6: same permanent signing key, higher versionCode for in-place update.
build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 11", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.5.6"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"Version bump failed: code={code_n}, name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.5.6 verified-audio + crash hardening fixes")
