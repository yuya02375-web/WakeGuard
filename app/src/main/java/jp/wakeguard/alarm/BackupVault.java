package jp.wakeguard.alarm;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Durable user-state backup. Runtime ringing/snooze sessions are deliberately excluded. */
public final class BackupVault {
    private BackupVault() {}

    static final String ENTITY_KEY = "ignido_state_v1";
    private static final int FORMAT = 1;

    private static final String[] PREF_FILES = {
            "wake_guard",
            "wake_guard_multi_alarms",
            "time_log_v1",
            "wakeguard_streak_growth_v1",
            "wakeguard_streak_protection_v1",
            "wakeguard_streak_game_v1",
            "clock_tools",
            "wakeguard_i18n"
    };

    private static final Set<String> WAKE_GUARD_RUNTIME_KEYS = new HashSet<>(Arrays.asList(
            "alarm_active", "step_baseline", "current_steps", "detector_mode", "step_sensor_available",
            "session_test", "session_epoch_day", "session_expected_at", "session_started_at_ms",
            "session_silenced", "active_alarm_id", "session_mission_type", "mission_progress",
            "mission_complete", "original_alarm_volume", "last_fatal_error", "last_alarm_error"
    ));
    private static final Set<String> TIME_LOG_RUNTIME_KEYS = new HashSet<>(Arrays.asList(
            "active_folder", "active_start"
    ));
    private static final Set<String> CLOCK_RUNTIME_KEYS = new HashSet<>(Arrays.asList(
            "sw_running", "sw_base", "sw_elapsed", "sw_laps", "sw_lap_walls", "sw_stopped_wall"
    ));

    private static final List<SharedPreferences> watchedPreferences = new ArrayList<>();
    private static final List<SharedPreferences.OnSharedPreferenceChangeListener> listeners = new ArrayList<>();
    private static boolean installed = false;
    private static boolean restoring = false;

    public static synchronized void installAutoBackup(Context context) {
        if (installed) return;
        installed = true;
        Context app = context.getApplicationContext();
        for (String file : PREF_FILES) {
            SharedPreferences prefs = app.getSharedPreferences(file, Context.MODE_PRIVATE);
            SharedPreferences.OnSharedPreferenceChangeListener listener = (sp, key) -> {
                if (restoring || !shouldBackupKey(file, key)) return;
                requestBackup(app);
            };
            prefs.registerOnSharedPreferenceChangeListener(listener);
            watchedPreferences.add(prefs);
            listeners.add(listener);
        }
        requestBackup(app);
    }

    public static void requestBackup(Context context) {
        if (restoring) return;
        try { new BackupManager(context.getApplicationContext()).dataChanged(); }
        catch (Throwable ignored) {}
    }

    private static boolean shouldBackupKey(String file, String key) {
        if (key == null) return false;
        if ("wake_guard".equals(file) && WAKE_GUARD_RUNTIME_KEYS.contains(key)) return false;
        if ("time_log_v1".equals(file) && TIME_LOG_RUNTIME_KEYS.contains(key)) return false;
        if ("clock_tools".equals(file) && CLOCK_RUNTIME_KEYS.contains(key)) return false;
        return true;
    }

    public static byte[] snapshotBytes(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("createdAt", System.currentTimeMillis());
        JSONObject allPrefs = new JSONObject();

        for (String file : PREF_FILES) {
            SharedPreferences prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE);
            JSONObject fileObject = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                String key = entry.getKey();
                if (!shouldBackupKey(file, key)) continue;
                JSONObject encoded = encodeValue(entry.getValue());
                if (encoded != null) fileObject.put(key, encoded);
            }
            allPrefs.put(file, fileObject);
        }
        root.put("prefs", allPrefs);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static void writeBackup(Context context, OutputStream out) throws Exception {
        byte[] bytes = snapshotBytes(context);
        out.write(bytes);
        out.flush();
        requestBackup(context);
    }

    public static void restore(Context context, InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[32 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        restoreBytes(context, out.toByteArray());
    }

    public static synchronized void restoreBytes(Context context, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("空のバックアップです");
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        int format = root.optInt("format", -1);
        if (format != FORMAT) throw new IllegalArgumentException("未対応のバックアップ形式です");
        JSONObject allPrefs = root.optJSONObject("prefs");
        if (allPrefs == null) throw new IllegalArgumentException("バックアップ内容がありません");

        restoring = true;
        try {
            for (String file : PREF_FILES) {
                JSONObject fileObject = allPrefs.optJSONObject(file);
                if (fileObject == null) continue;
                SharedPreferences.Editor editor = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear();
                JSONArray names = fileObject.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String key = names.optString(i, "");
                        if (key.isEmpty() || !shouldBackupKey(file, key)) continue;
                        JSONObject encoded = fileObject.optJSONObject(key);
                        if (encoded != null) decodeInto(editor, key, encoded);
                    }
                }
                editor.commit();
            }
        } finally {
            restoring = false;
        }
        clearRuntimeState(context);
        requestBackup(context);
    }

    public static void afterRestore(Context context) {
        clearRuntimeState(context);
        try { context.stopService(new android.content.Intent(context, AlarmService.class)); } catch (Throwable ignored) {}
        try { SnoozeScheduler.cancelAll(context); } catch (Throwable ignored) {}
        try { AlarmScheduler.reschedule(context); } catch (Throwable ignored) {}
        try { WidgetSuite.refreshAll(context); } catch (Throwable ignored) {}
        requestBackup(context);
    }

    private static void clearRuntimeState(Context context) {
        SharedPreferences.Editor e = context.getSharedPreferences("wake_guard", Context.MODE_PRIVATE).edit();
        for (String key : WAKE_GUARD_RUNTIME_KEYS) e.remove(key);
        e.commit();
        SharedPreferences.Editor t = context.getSharedPreferences("time_log_v1", Context.MODE_PRIVATE).edit();
        for (String key : TIME_LOG_RUNTIME_KEYS) t.remove(key);
        t.commit();
        context.getSharedPreferences("clock_tools", Context.MODE_PRIVATE).edit()
                .remove("sw_running").remove("sw_base").commit();
    }

    private static JSONObject encodeValue(Object value) throws Exception {
        if (value == null) return null;
        JSONObject out = new JSONObject();
        if (value instanceof String) {
            out.put("t", "s"); out.put("v", value);
        } else if (value instanceof Integer) {
            out.put("t", "i"); out.put("v", value);
        } else if (value instanceof Long) {
            out.put("t", "l"); out.put("v", value);
        } else if (value instanceof Float) {
            out.put("t", "f"); out.put("v", ((Float) value).doubleValue());
        } else if (value instanceof Boolean) {
            out.put("t", "b"); out.put("v", value);
        } else if (value instanceof Set) {
            out.put("t", "ss");
            JSONArray a = new JSONArray();
            for (Object item : (Set<?>) value) if (item instanceof String) a.put(item);
            out.put("v", a);
        } else {
            return null;
        }
        return out;
    }

    private static void decodeInto(SharedPreferences.Editor editor, String key, JSONObject encoded) {
        String type = encoded.optString("t", "");
        switch (type) {
            case "s": editor.putString(key, encoded.optString("v", "")); break;
            case "i": editor.putInt(key, encoded.optInt("v", 0)); break;
            case "l": editor.putLong(key, encoded.optLong("v", 0L)); break;
            case "f": editor.putFloat(key, (float) encoded.optDouble("v", 0.0)); break;
            case "b": editor.putBoolean(key, encoded.optBoolean("v", false)); break;
            case "ss":
                JSONArray a = encoded.optJSONArray("v");
                Set<String> set = new HashSet<>();
                if (a != null) for (int i = 0; i < a.length(); i++) set.add(a.optString(i, ""));
                set.remove("");
                editor.putStringSet(key, set);
                break;
            default: break;
        }
    }
}
