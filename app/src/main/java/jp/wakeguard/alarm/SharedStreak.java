package jp.wakeguard.alarm;

import android.content.Context;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** A calendar day counts once when either app has a successful activity. */
final class SharedStreak {
    private SharedStreak() {}

    static Summary summarize(Context context, LocalDate today) {
        Set<String> values = new HashSet<>(Prefs.successDays(context));
        values.addAll(StreakGrowth.linkedTrainingDays(context));
        TreeSet<LocalDate> days = new TreeSet<>();
        for (String value : values) {
            LocalDate day = parseDay(value);
            if (day != null && !day.isAfter(today)) days.add(day);
        }

        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int current = 0;
        while (days.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }

        LocalDate previous = null;
        int best = 0;
        int run = 0;
        for (LocalDate day : days) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            best = Math.max(best, run);
            previous = day;
        }
        return new Summary(current, best);
    }

    static LocalDate parseDay(String value) {
        if (value == null || value.length() != 10) return null;
        try {
            LocalDate day = LocalDate.parse(value);
            return day.toEpochDay() > 0 && day.toString().equals(value) ? day : null;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    static final class Summary {
        final int current;
        final int best;

        Summary(int current, int best) {
            this.current = current;
            this.best = best;
        }
    }
}
