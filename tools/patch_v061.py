from pathlib import Path
import re
import runpy

runpy.run_path("tools/patch_v060.py", run_name="__main__")

root = Path("WakeGuard")
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
build_path = root / "app/build.gradle.kts"

service = service_path.read_text(encoding="utf-8")

start = service.find('    private void startStepSensor() {')
end = service.find('    @Override public void onAccuracyChanged', start)
if start < 0 or end < 0:
    raise SystemExit("v061: sensor block not found")

new_sensor = r'''    private void startStepSensor() {
        // Prefer Android's dedicated hardware STEP_COUNTER. Android documents this
        // sensor as the more accurate aggregate step source (it may batch updates).
        // Do not veto it with accelerometer/gyro motion: normal walking was being
        // rejected by the old anti-shake filter, causing severe under-counting.
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
        } catch (Throwable e) {
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

    @Override public void onSensorChanged(SensorEvent event) {
        try {
            if (!running && !Prefs.active(this)) return;
            if (event == null || event.sensor == null || event.values == null || event.values.length == 0) return;

            int type = event.sensor.getType();
            int accepted = Prefs.currentSteps(this);

            if (type == Sensor.TYPE_STEP_COUNTER) {
                float raw = event.values[0];
                if (rawCounterLast < 0f) {
                    // Establish the mission baseline. STEP_COUNTER is cumulative since
                    // boot/activation; only subsequent deltas belong to this alarm.
                    rawCounterLast = raw;
                    return;
                }

                int delta = Math.max(0, Math.round(raw - rawCounterLast));
                rawCounterLast = raw;
                if (delta <= 0) return;

                // Credit the complete hardware-reported delta. STEP_COUNTER may batch
                // several real steps into one callback, so cadence caps lose valid steps.
                accepted += delta;
            } else if (type == Sensor.TYPE_STEP_DETECTOR) {
                // Fallback for devices without STEP_COUNTER. The detector emits one
                // 1.0 event per detected step. Only reject physically impossible event
                // rates; do not use gyro/acceleration vetoes that also reject walking.
                long now = SystemClock.elapsedRealtime();
                if (lastAcceptedStepElapsed != 0L && now - lastAcceptedStepElapsed < 180L) return;
                lastAcceptedStepElapsed = now;
                accepted += 1;
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
service_path.write_text(service, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 16", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.6.1"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"v061: version bump failed code={code_n} name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.6.1 accurate hardware step counting")
