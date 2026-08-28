from pathlib import Path
import re

root = Path("WakeGuard")
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
activity_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmActivity.java"
build_path = root / "app/build.gradle.kts"

service = service_path.read_text(encoding="utf-8")

if "import android.Manifest;" not in service:
    service = service.replace("import android.app.*;\n", "import android.Manifest;\nimport android.app.*;\n", 1)
if "import android.content.pm.PackageManager;" not in service:
    service = service.replace("import android.content.*;\n", "import android.content.*;\nimport android.content.pm.PackageManager;\n", 1)

old = '''    private void startStepSensor() {
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

new = '''    private void startStepSensor() {
        // STEP_COUNTER / STEP_DETECTOR require the runtime ACTIVITY_RECOGNITION
        // permission on Android 10+. Never let a missing/revoked permission crash
        // the foreground alarm service: audio + vibration must keep running.
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
        } catch (SecurityException | RuntimeException e) {
            // Defensive recovery for OEM/Android permission or sensor failures.
            // The alarm must continue ringing instead of killing the process.
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

if old not in service:
    raise SystemExit("Expected v0.5.0 startStepSensor() block was not found; refusing unsafe patch")
service = service.replace(old, new, 1)
service_path.write_text(service, encoding="utf-8")

activity = activity_path.read_text(encoding="utf-8")
activity = activity.replace(
    '"歩数センサーを利用できません"',
    '"歩数の権限またはセンサーを利用できません\\n音・振動は停止せず継続しています"'
)
activity_path.write_text(activity, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 6", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.5.1"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"Version bump failed: versionCode matches={code_n}, versionName matches={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.5.1 crash-safety patch")
