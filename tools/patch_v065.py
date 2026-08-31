from pathlib import Path
import re
import runpy

runpy.run_path("tools/patch_v064.py", run_name="__main__")

root = Path("WakeGuard")
service_path = root / "app/src/main/java/jp/wakeguard/alarm/AlarmService.java"
build_path = root / "app/build.gradle.kts"

service = service_path.read_text(encoding="utf-8")

old_fields = '''    private long lastAcceptedStepElapsed = 0L;\n    private float rawCounterLast = -1f;\n'''
new_fields = '''    private long lastAcceptedStepElapsed = 0L;\n    private float rawCounterLast = -1f;\n    private long lastCounterCallbackElapsed = 0L;\n    private long lastStrongLinearElapsed = 0L;\n    private long lastStrongGyroElapsed = 0L;\n    private long lastConfirmedShakeElapsed = 0L;\n'''
if old_fields not in service:
    raise SystemExit("v065: step fields not found")
service = service.replace(old_fields, new_fields, 1)

old_reg = '''            stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);\n            stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);\n            Sensor use = stepCounter != null ? stepCounter : stepDetector;\n            Prefs.detectorMode(this, stepCounter == null && stepDetector != null);\n\n            boolean registered = false;\n            if (use != null) {\n                registered = sensorManager.registerListener(\n                        this, use, SensorManager.SENSOR_DELAY_NORMAL);\n            }\n            Prefs.stepSensorAvailable(this, use != null && registered);\n'''
new_reg = '''            stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);\n            stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);\n            linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);\n            if (linearAcceleration == null)\n                linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);\n            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);\n            Sensor use = stepCounter != null ? stepCounter : stepDetector;\n            Prefs.detectorMode(this, stepCounter == null && stepDetector != null);\n\n            boolean registered = false;\n            if (use != null) {\n                registered = sensorManager.registerListener(\n                        this, use, SensorManager.SENSOR_DELAY_NORMAL);\n            }\n\n            // Auxiliary sensors are anti-shake evidence only. They never add steps and\n            // only veto an extreme hand-shake burst. Thresholds are deliberately high\n            // so ordinary walking/swinging does not recreate the old under-count bug.\n            try {\n                if (linearAcceleration != null)\n                    sensorManager.registerListener(this, linearAcceleration, SensorManager.SENSOR_DELAY_GAME);\n            } catch (Throwable ignored) {}\n            try {\n                if (gyroscope != null)\n                    sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);\n            } catch (Throwable ignored) {}\n\n            Prefs.stepSensorAvailable(this, use != null && registered);\n'''
if old_reg not in service:
    raise SystemExit("v065: sensor registration block not found")
service = service.replace(old_reg, new_reg, 1)

old_start = '''            int type = event.sensor.getType();\n            int accepted = Prefs.currentSteps(this);\n\n            if (type == Sensor.TYPE_STEP_COUNTER) {\n'''
new_start = '''            int type = event.sensor.getType();\n            int accepted = Prefs.currentSteps(this);\n            long now = SystemClock.elapsedRealtime();\n\n            // Detect only very aggressive hand shaking: strong translation AND strong\n            // rotation within a short window. A single walking jolt is not enough.\n            if (type == Sensor.TYPE_LINEAR_ACCELERATION || type == Sensor.TYPE_ACCELEROMETER) {\n                if (event.values.length < 3) return;\n                double x = event.values[0], y = event.values[1], z = event.values[2];\n                double magnitude = Math.sqrt(x * x + y * y + z * z);\n                double threshold = type == Sensor.TYPE_ACCELEROMETER ? 24.0 : 16.0;\n                if (magnitude >= threshold) {\n                    lastStrongLinearElapsed = now;\n                    if (lastStrongGyroElapsed != 0L\n                            && Math.abs(lastStrongLinearElapsed - lastStrongGyroElapsed) <= 450L)\n                        lastConfirmedShakeElapsed = now;\n                }\n                return;\n            }\n            if (type == Sensor.TYPE_GYROSCOPE) {\n                if (event.values.length < 3) return;\n                double x = event.values[0], y = event.values[1], z = event.values[2];\n                double magnitude = Math.sqrt(x * x + y * y + z * z);\n                if (magnitude >= 4.8) {\n                    lastStrongGyroElapsed = now;\n                    if (lastStrongLinearElapsed != 0L\n                            && Math.abs(lastStrongGyroElapsed - lastStrongLinearElapsed) <= 450L)\n                        lastConfirmedShakeElapsed = now;\n                }\n                return;\n            }\n\n            if (type == Sensor.TYPE_STEP_COUNTER) {\n'''
if old_start not in service:
    raise SystemExit("v065: onSensorChanged start not found")
service = service.replace(old_start, new_start, 1)

old_counter = '''                float raw = event.values[0];\n                if (rawCounterLast < 0f) {\n                    // Establish the mission baseline. STEP_COUNTER is cumulative since\n                    // boot/activation; only subsequent deltas belong to this alarm.\n                    rawCounterLast = raw;\n                    return;\n                }\n\n                int delta = Math.max(0, Math.round(raw - rawCounterLast));\n                rawCounterLast = raw;\n                if (delta <= 0) return;\n\n                // Credit the complete hardware-reported delta. STEP_COUNTER may batch\n                // several real steps into one callback, so cadence caps lose valid steps.\n                accepted += delta;\n'''
new_counter = '''                float raw = event.values[0];\n                if (rawCounterLast < 0f) {\n                    // Establish the mission baseline. STEP_COUNTER is cumulative since\n                    // boot/activation; only subsequent deltas belong to this alarm.\n                    rawCounterLast = raw;\n                    lastCounterCallbackElapsed = now;\n                    return;\n                }\n\n                int delta = Math.max(0, Math.round(raw - rawCounterLast));\n                rawCounterLast = raw;\n                long interval = lastCounterCallbackElapsed == 0L\n                        ? 1000L : Math.max(250L, now - lastCounterCallbackElapsed);\n                lastCounterCallbackElapsed = now;\n                if (delta <= 0) return;\n\n                // If the phone was just violently translated+rotated, discard the\n                // resulting hardware false-positive batch. Because rawCounterLast is\n                // advanced above, those fake steps cannot leak back in later.\n                if (lastConfirmedShakeElapsed != 0L\n                        && now - lastConfirmedShakeElapsed <= 1400L\n                        && delta >= 2) return;\n\n                // STEP_COUNTER may legitimately batch callbacks, so the cap is based on\n                // elapsed time since the previous callback rather than callback count.\n                // 3.2 steps/s (~192 spm) is above normal brisk walking; +1 tolerance\n                // avoids shaving real steps at callback boundaries. This prevents an OEM\n                // pedometer from granting e.g. +11 in one instant after a hand shake.\n                int physicallyPlausible = Math.max(1,\n                        (int)Math.ceil((interval / 1000.0) * 3.2) + 1);\n                accepted += Math.min(delta, physicallyPlausible);\n'''
if old_counter not in service:
    raise SystemExit("v065: step counter block not found")
service = service.replace(old_counter, new_counter, 1)

old_detector = '''                long now = SystemClock.elapsedRealtime();\n                if (lastAcceptedStepElapsed != 0L && now - lastAcceptedStepElapsed < 180L) return;\n                lastAcceptedStepElapsed = now;\n                accepted += 1;\n'''
new_detector = '''                if (lastConfirmedShakeElapsed != 0L\n                        && now - lastConfirmedShakeElapsed <= 900L) return;\n                if (lastAcceptedStepElapsed != 0L && now - lastAcceptedStepElapsed < 180L) return;\n                lastAcceptedStepElapsed = now;\n                accepted += 1;\n'''
if old_detector not in service:
    raise SystemExit("v065: step detector block not found")
service = service.replace(old_detector, new_detector, 1)

old_cleanup = '''        rawCounterLast = -1f;\n        rejectStepsUntilElapsed = 0L;\n        lastAcceptedStepElapsed = 0L;\n'''
new_cleanup = '''        rawCounterLast = -1f;\n        rejectStepsUntilElapsed = 0L;\n        lastAcceptedStepElapsed = 0L;\n        lastCounterCallbackElapsed = 0L;\n        lastStrongLinearElapsed = 0L;\n        lastStrongGyroElapsed = 0L;\n        lastConfirmedShakeElapsed = 0L;\n'''
if old_cleanup not in service:
    raise SystemExit("v065: cleanup block not found")
service = service.replace(old_cleanup, new_cleanup, 1)
service_path.write_text(service, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build, code_n = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 20", build, count=1)
build, name_n = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.6.5"', build, count=1)
if code_n != 1 or name_n != 1:
    raise SystemExit(f"v065: version bump failed code={code_n} name={name_n}")
build_path.write_text(build, encoding="utf-8")

print("Applied WakeGuard v0.6.5 balanced anti-shake step validation")
