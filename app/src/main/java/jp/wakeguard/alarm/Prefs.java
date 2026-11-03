package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public final class Prefs {
    private static final String FILE = "wake_guard";
    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) { return p(c).getBoolean("enabled", false); }
    public static void enabled(Context c, boolean v) { p(c).edit().putBoolean("enabled", v).apply(); }
    public static int hour(Context c) { return p(c).getInt("hour", 7); }
    public static int minute(Context c) { return p(c).getInt("minute", 0); }
    public static void time(Context c, int h, int m) { p(c).edit().putInt("hour", h).putInt("minute", m).apply(); }
    public static int dayMask(Context c) { return p(c).getInt("days", 0); }
    public static void dayMask(Context c, int v) { p(c).edit().putInt("days", v).apply(); }
    /** True only when the legacy primary alarm was actually configured by an older build.
     * Fresh installs must not show or schedule the historical 05:30 placeholder. */
    public static boolean primaryConfigured(Context c) {
        SharedPreferences sp=p(c);
        return sp.contains("hour") || sp.contains("minute") || sp.contains("days") || sp.contains("enabled")
                || sp.contains("primary_label") || sp.contains("primary_mission_type") || sp.contains("primary_mission_count");
    }
    public static int steps(Context c) { return p(c).getInt("steps", 50); }
    public static void steps(Context c, int v) { p(c).edit().putInt("steps", v).commit(); }
    public static int volume(Context c) { return p(c).getInt("volume", 15); }
    public static void volume(Context c, int v) { p(c).edit().putInt("volume", v).apply(); }
    public static String vibration(Context c) { return p(c).getString("vibration", "IRREGULAR"); }
    public static void vibration(Context c, String v) { p(c).edit().putString("vibration", v).apply(); }
    public static String soundUri(Context c) { return p(c).getString("sound_uri", ""); }
    public static void soundUri(Context c, String v) { p(c).edit().putString("sound_uri", v).commit(); }
    public static String soundName(Context c) { return p(c).getString("sound_name", ""); }
    public static void soundName(Context c, String v) { p(c).edit().putString("sound_name", v == null ? "" : v).commit(); }
    public static long soundBytes(Context c) { return p(c).getLong("sound_bytes", -1L); }
    public static void soundBytes(Context c, long v) { p(c).edit().putLong("sound_bytes", v).commit(); }
    public static long soundDurationMs(Context c) { return p(c).getLong("sound_duration_ms", -1L); }
    public static void soundDurationMs(Context c, long v) { p(c).edit().putLong("sound_duration_ms", v).commit(); }
    public static int ringDurationSec(Context c) { return Math.max(0,Math.min(86400,p(c).getInt("ring_duration_sec",0))); }
    public static void ringDurationSec(Context c, int v) { p(c).edit().putInt("ring_duration_sec",Math.max(0,Math.min(86400,v))).apply(); }
    public static int fullStopDurationSec(Context c) { return Math.max(0,Math.min(86400,p(c).getInt("full_stop_duration_sec",0))); }
    public static void fullStopDurationSec(Context c, int v) { p(c).edit().putInt("full_stop_duration_sec",Math.max(0,Math.min(86400,v))).apply(); }
    public static int preNotifyMin(Context c) { return Math.max(0,Math.min(1440,p(c).getInt("pre_notify_min",30))); }
    public static void preNotifyMin(Context c, int v) { p(c).edit().putInt("pre_notify_min",Math.max(0,Math.min(1440,v))).apply(); }
    public static int snoozeMinutes(Context c) { return Math.max(1,Math.min(60,p(c).getInt("snooze_minutes",5))); }
    public static void snoozeMinutes(Context c, int v) { p(c).edit().putInt("snooze_minutes",Math.max(1,Math.min(60,v))).apply(); }
    public static boolean active(Context c) { return p(c).getBoolean("alarm_active", false); }
    public static void active(Context c, boolean v) { p(c).edit().putBoolean("alarm_active", v).apply(); }
    public static float baseline(Context c) { return p(c).getFloat("step_baseline", -1f); }
    public static void baseline(Context c, float v) { p(c).edit().putFloat("step_baseline", v).apply(); }
    public static int currentSteps(Context c) { return p(c).getInt("current_steps", 0); }
    public static void currentSteps(Context c, int v) { p(c).edit().putInt("current_steps", v).apply(); }
    public static boolean detectorMode(Context c) { return p(c).getBoolean("detector_mode", false); }
    public static void detectorMode(Context c, boolean v) { p(c).edit().putBoolean("detector_mode", v).apply(); }
    public static boolean stepSensorAvailable(Context c) { return p(c).getBoolean("step_sensor_available", true); }
    public static void stepSensorAvailable(Context c, boolean v) { p(c).edit().putBoolean("step_sensor_available", v).apply(); }
    public static boolean sessionIsTest(Context c) { return p(c).getBoolean("session_test", false); }
    public static void sessionIsTest(Context c, boolean v) { p(c).edit().putBoolean("session_test", v).apply(); }
    public static long sessionEpochDay(Context c) { return p(c).getLong("session_epoch_day", -1L); }
    public static void sessionEpochDay(Context c, long v) { p(c).edit().putLong("session_epoch_day", v).apply(); }
    public static long sessionExpectedAt(Context c) { return p(c).getLong("session_expected_at", -1L); }
    public static void sessionExpectedAt(Context c, long v) { p(c).edit().putLong("session_expected_at", v).apply(); }
    public static long sessionStartedAtMs(Context c) { return p(c).getLong("session_started_at_ms",0L); }
    public static void sessionStartedAtMs(Context c,long v) { p(c).edit().putLong("session_started_at_ms",Math.max(0L,v)).apply(); }
    public static boolean sessionSilenced(Context c) { return p(c).getBoolean("session_silenced",false); }
    public static void sessionSilenced(Context c,boolean v) { p(c).edit().putBoolean("session_silenced",v).apply(); }
    public static long activeAlarmId(Context c) { return p(c).getLong("active_alarm_id", 1L); }
    public static void activeAlarmId(Context c, long v) { p(c).edit().putLong("active_alarm_id", v).apply(); }
    public static String sessionMissionType(Context c) { return p(c).getString("session_mission_type", ""); }
    public static void sessionMissionType(Context c, String v) { p(c).edit().putString("session_mission_type", v == null ? "" : v).apply(); }
    public static int missionProgress(Context c) { return p(c).getInt("mission_progress", 0); }
    public static void missionProgress(Context c, int v) { p(c).edit().putInt("mission_progress", Math.max(0,v)).apply(); }
    public static boolean missionComplete(Context c) { return p(c).getBoolean("mission_complete", false); }
    public static void missionComplete(Context c, boolean v) { p(c).edit().putBoolean("mission_complete", v).apply(); }
    public static String primaryLabel(Context c) { return p(c).getString("primary_label", "メインアラーム"); }
    public static void primaryLabel(Context c, String v) { p(c).edit().putString("primary_label", v == null ? "メインアラーム" : v).apply(); }
    public static String primaryMissionType(Context c) { return AlarmStore.normalizeMission(p(c).getString("primary_mission_type", "STEPS")); }
    public static void primaryMissionType(Context c, String v) { p(c).edit().putString("primary_mission_type", AlarmStore.normalizeMission(v)).apply(); }
    public static int primaryMissionCount(Context c) { return Math.max(1,p(c).getInt("primary_mission_count", 3)); }
    public static void primaryMissionCount(Context c, int v) { p(c).edit().putInt("primary_mission_count", Math.max(1,Math.min(500,v))).apply(); }
    public static int originalAlarmVolume(Context c) { return p(c).getInt("original_alarm_volume", -1); }
    public static void originalAlarmVolume(Context c, int v) { p(c).edit().putInt("original_alarm_volume", v).apply(); }

    public static String lastFatalError(Context c) { return p(c).getString("last_fatal_error", ""); }
    public static void lastFatalError(Context c, String v) { p(c).edit().putString("last_fatal_error", v == null ? "" : v).commit(); }

    public static String lastAlarmError(Context c) { return p(c).getString("last_alarm_error", ""); }
    public static void lastAlarmError(Context c, String v) { p(c).edit().putString("last_alarm_error", v == null ? "" : v).commit(); }

    public static int streak(Context c) { return p(c).getInt("streak", 0); }
    public static void streak(Context c, int v) { p(c).edit().putInt("streak", v).apply(); }
    public static int bestStreak(Context c) { return p(c).getInt("best_streak", 0); }
    public static void bestStreak(Context c, int v) { p(c).edit().putInt("best_streak", v).apply(); }
    public static int totalWakeups(Context c) { return p(c).getInt("total_wakeups", 0); }
    public static void totalWakeups(Context c, int v) { p(c).edit().putInt("total_wakeups", v).apply(); }
    public static long lastSuccessEpochDay(Context c) { return p(c).getLong("last_success_epoch_day", -1L); }
    public static void lastSuccessEpochDay(Context c, long v) { p(c).edit().putLong("last_success_epoch_day", v).apply(); }


    public static long statsStartEpochDay(Context c) {
        long stored = p(c).getLong("stats_start_epoch_day", -1L);
        if (stored > 0) return stored;
        long earliest = Long.MAX_VALUE;
        for (String raw : successDays(c)) {
            try { earliest = Math.min(earliest, LocalDate.parse(raw).toEpochDay()); } catch (Exception ignored) {}
        }
        long value = earliest == Long.MAX_VALUE ? LocalDate.now().toEpochDay() : earliest;
        p(c).edit().putLong("stats_start_epoch_day", value).apply();
        return value;
    }
    public static void ensureStatsStart(Context c) { statsStartEpochDay(c); }

    public static int pendingRecordStreak(Context c) { return p(c).getInt("pending_record_streak", 0); }
    public static void pendingRecordStreak(Context c, int v) { p(c).edit().putInt("pending_record_streak", v).apply(); }

    /** Persistent successful wake-up dates. SharedPreferences requires copying the returned set before editing. */
    public static Set<String> successDays(Context c) {
        return new HashSet<>(p(c).getStringSet("success_days", new HashSet<>()));
    }

    public static void addSuccessDay(Context c, LocalDate day) {
        Set<String> days = successDays(c);
        days.add(day.toString());
        p(c).edit().putStringSet("success_days", days).apply();
    }

    public static boolean wasSuccessful(Context c, LocalDate day) {
        return successDays(c).contains(day.toString());
    }
}
