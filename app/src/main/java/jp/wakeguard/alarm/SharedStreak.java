package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Combines WakeGuard wake success dates with saved training days without rewriting alarms. */
public final class SharedStreak {
    private static final String FILE = "wakeguard_training_days_v1";
    private SharedStreak() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Adds a completed training or run date. Imported history is retained without dragon farming. */
    public static synchronized boolean recordActivityDay(Context c, String value) {
        if (value == null || value.length() != 10) return false;
        final LocalDate day;
        try { day = LocalDate.parse(value); }
        catch (DateTimeParseException e) { return false; }
        if (day.isAfter(LocalDate.now())) return false;
        Set<String> saved = new HashSet<>(prefs(c).getStringSet("days", new HashSet<>()));
        if (!saved.add(day.toString())) return false;
        if (!prefs(c).edit().putStringSet("days", saved).commit()) return false;
        if (day.equals(LocalDate.now())) StreakGrowth.onActivitySuccess(c, day.toEpochDay());
        return true;
    }

    private static TreeSet<LocalDate> allDays(Context c) {
        Set<String> values = new HashSet<>(Prefs.successDays(c));
        values.addAll(prefs(c).getStringSet("days", new HashSet<>()));
        TreeSet<LocalDate> dates = new TreeSet<>();
        for (String value : values) {
            try { dates.add(LocalDate.parse(value)); }
            catch (DateTimeParseException ignored) {}
        }
        return dates;
    }

    /** One shared day is counted if WakeGuard succeeded or training was saved on that date. */
    public static int current(Context c) {
        TreeSet<LocalDate> dates = allDays(c);
        LocalDate cursor = LocalDate.now();
        if (!dates.contains(cursor)) cursor = cursor.minusDays(1);
        int count = 0;
        while (dates.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    public static int best(Context c) {
        int best = 0, run = 0;
        LocalDate previous = null;
        for (LocalDate date : allDays(c)) {
            run = previous != null && previous.plusDays(1).equals(date) ? run + 1 : 1;
            best = Math.max(best, run);
            previous = date;
        }
        return best;
    }

    public static int thisWeek(Context c) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        int count = 0;
        for (LocalDate date : allDays(c)) {
            if (!date.isBefore(monday) && !date.isAfter(today)) count++;
        }
        return count;
    }
}