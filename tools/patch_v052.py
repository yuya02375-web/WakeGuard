from pathlib import Path
import re

root = Path("WakeGuard")
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
main_path = root / "app/src/main/java/jp/wakeguard/alarm/MainActivity.java"
build_path = root / "app/build.gradle.kts"

# ---------------------------------------------------------------------------
# AlarmService: keep the alarm process alive when step permission/sensor fails.
# ---------------------------------------------------------------------------
service = service_path.read_text(encoding="utf-8")

if "import android.Manifest;" not in service:
    service = service.replace("import android.app.*;\n", "import android.Manifest;\nimport android.app.*;\n", 1)
if "import android.content.pm.PackageManager;" not in service:
    service = service.replace("import android.content.*;\n", "import android.content.*;\nimport android.content.pm.PackageManager;\n", 1)

old_step = '''    private void startStepSensor() {
        if (sensorManager != null) return;
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        Sensor use = stepCounter != null ? stepCounter : stepDetector;
        Prefs.detectorMode(this, stepCounter == null && stepDetector != null);
        Prefs.stepSensorAvailable(this, use != null);
        if (use != null) sensorManager.registerListener(this, use, SensorManager.SENSOR_DELAY_NORMAL);
        updateViews();
    }
'''

new_step = '''    private void startStepSensor() {
        // STEP_COUNTER / STEP_DETECTOR require ACTIVITY_RECOGNITION on Android 10+.
        // A permission/OEM sensor failure must never kill alarm audio or vibration.
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            Prefs.stepSensorAvailable(this, false);
            sensorManager = null;
            stepCounter = null;
            stepDetector = null;
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
        } catch (RuntimeException e) {
            try {
                if (sensorManager != null) sensorManager.unregisterListener(this);
            } catch (Throwable ignored) {}
            sensorManager = null;
            stepCounter = null;
            stepDetector = null;
            Prefs.stepSensorAvailable(this, false);
        }
        updateViews();
    }
'''

if old_step not in service:
    raise SystemExit("Expected v0.5.0 startStepSensor() block was not found")
service = service.replace(old_step, new_step, 1)

# Support a copied app-private alarm file in addition to legacy content:// URIs.
old_player = '''            Uri uri = custom == null || custom.isEmpty()
                    ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    : Uri.parse(custom);
            p.setDataSource(this, uri);
'''
new_player = '''            if (custom == null || custom.isEmpty()) {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                p.setDataSource(this, uri);
            } else if (custom.startsWith("/")) {
                // v0.5.2 copies the selected audio into app-private storage so the
                // alarm does not depend on a temporary Storage Access Framework grant.
                p.setDataSource(custom);
            } else {
                // Backward compatibility with v0.5.1 content:// selections.
                p.setDataSource(this, Uri.parse(custom));
            }
'''
if old_player not in service:
    raise SystemExit("Expected v0.5.0 MediaPlayer data-source block was not found")
service = service.replace(old_player, new_player, 1)
service_path.write_text(service, encoding="utf-8")

# ---------------------------------------------------------------------------
# MainActivity: auto-save every setting on pause and make custom audio durable.
# ---------------------------------------------------------------------------
main = main_path.read_text(encoding="utf-8")
if "import java.io.*;" not in main:
    main = main.replace("import java.text.DateFormat;\n", "import java.io.*;\nimport java.text.DateFormat;\n", 1)

old_picker = '''        soundButton.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, REQ_AUDIO);
        });
'''
new_picker = '''        soundButton.setOnClickListener(v -> {
            // Save every field before leaving this Activity for the file picker.
            // This prevents volume/steps/vibration/etc. from snapping back on return.
            persistUiState();
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("audio/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQ_AUDIO);
        });
'''
if old_picker not in main:
    raise SystemExit("Expected v0.5.0 audio picker block was not found")
main = main.replace(old_picker, new_picker, 1)

old_save = '''    private void saveAll() {
        Prefs.enabled(this, enabled.isChecked());
        int mask=0; for(int i=0;i<7;i++) if(days[i].isChecked()) mask |= 1<<i;
        if(mask==0) mask=0b1111111;
        Prefs.dayMask(this, mask);
        int st=50; try { st=Math.max(10, Math.min(500, Integer.parseInt(steps.getText().toString()))); } catch(Exception ignored){}
        Prefs.steps(this, st);
        Prefs.volume(this, volume.getProgress());
        Prefs.vibration(this, vibration.getSelectedItemPosition()==0 ? "IRREGULAR" : "STRONG");
        Prefs.ensureStatsStart(this);
        AlarmScheduler.reschedule(this);
        refresh();
        if (AlarmScheduler.canScheduleExact(this)) {
            Toast.makeText(this,"保存しました。最大14回分を先に予約済みです。",Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this,"正確なアラームの許可が必要です。①を確認してください。",Toast.LENGTH_LONG).show();
        }
    }
'''
new_save = '''    private void persistUiState() {
        // Persist exactly what is currently visible. Do not require the Save button.
        if (enabled == null || steps == null || volume == null || vibration == null) return;
        Prefs.enabled(this, enabled.isChecked());

        int mask = 0;
        for (int i = 0; i < 7; i++) if (days[i] != null && days[i].isChecked()) mask |= 1 << i;
        // Zero is a valid choice (no repeat days). Do not silently turn it back into every day.
        Prefs.dayMask(this, mask);

        // If the user is temporarily editing an empty/non-numeric field, keep the last
        // valid value instead of resetting it to the old hard-coded default of 50.
        int st = Prefs.steps(this);
        try {
            String raw = steps.getText().toString().trim();
            if (!raw.isEmpty()) st = Math.max(10, Math.min(500, Integer.parseInt(raw)));
        } catch (Exception ignored) {}
        Prefs.steps(this, st);

        Prefs.volume(this, volume.getProgress());
        Prefs.vibration(this, vibration.getSelectedItemPosition() == 0 ? "IRREGULAR" : "STRONG");
        Prefs.ensureStatsStart(this);
    }

    private void saveAll() {
        persistUiState();
        AlarmScheduler.reschedule(this);
        refresh();
        if (AlarmScheduler.canScheduleExact(this)) {
            Toast.makeText(this,"保存しました。最大14回分を先に予約済みです。",Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this,"正確なアラームの許可が必要です。①を確認してください。",Toast.LENGTH_LONG).show();
        }
    }
'''
if old_save not in main:
    raise SystemExit("Expected v0.5.0 saveAll() block was not found")
main = main.replace(old_save, new_save, 1)

old_result = '''    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_AUDIO && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored){}
            Prefs.soundUri(this,uri.toString()); refresh();
        }
    }

    @Override protected void onResume() {
'''
new_result = '''    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_AUDIO && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri = data.getData();

            // Keep the provider grant where possible (use the exact flags returned by
            // the picker), but do not depend on it: copy the audio into app-private
            // storage so it still works after reboot/provider cleanup.
            int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (takeFlags != 0) {
                try { getContentResolver().takePersistableUriPermission(uri, takeFlags); }
                catch (Exception ignored) {}
            }

            File dst = new File(getFilesDir(), "wakeguard_alarm_audio");
            boolean copied = false;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dst, false)) {
                if (in == null) throw new IOException("Unable to open selected audio");
                byte[] buf = new byte[8192];
                int n;
                long total = 0;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    total += n;
                    // Prevent accidentally copying an enormous non-audio file forever.
                    if (total > 100L * 1024L * 1024L) throw new IOException("Audio file is too large");
                }
                out.flush();
                if (total <= 0) throw new IOException("Selected audio is empty");
                Prefs.soundUri(this, dst.getAbsolutePath());
                copied = true;
            } catch (Exception e) {
                try { dst.delete(); } catch (Throwable ignored) {}
            }

            if (copied) {
                Toast.makeText(this, "アラーム音を保存しました", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "この音声を読み込めませんでした。別の音声を選んでください。", Toast.LENGTH_LONG).show();
            }
            refresh();
        }
    }

    @Override protected void onPause() {
        // Settings are auto-saved whenever the screen is left (Home, Recents, file
        // picker, Android settings, app close). The Save button is no longer required
        // just to keep volume/steps/vibration/day selections.
        if (enabled != null) {
            persistUiState();
            AlarmScheduler.reschedule(this);
        }
        super.onPause();
    }

    @Override protected void onResume() {
'''
if old_result not in main:
    raise SystemExit("Expected v0.5.0 onActivityResult/onResume block was not found")
main = main.replace(old_result, new_result, 1)
main_path.write_text(main, encoding="utf-8")

# Keep the alarm activity wording aligned with the crash-safe step handling.
activity = activity_path.read_text(encoding="utf-8")
activity = activity.replace(
    '"歩数センサーを利用できません"',
    '"歩数の権限またはセンサーを利用できません\\n音・振動は停止せず継続しています"'
)
activity_path.write_text(activity, encoding="utf-8")

# v0.5.2 is the next update signed with the permanent WakeGuard signing key.
build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 7", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.5.2"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"Version bump failed: versionCode matches={code_n}, versionName matches={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.5.2 persistence + audio reliability patch")
