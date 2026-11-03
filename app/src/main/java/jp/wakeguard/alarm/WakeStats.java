package jp.wakeguard.alarm;

import android.content.Context;
import java.time.*;

/** Calendar-based wake-up statistics for scheduled mornings that have already become due. */
public final class WakeStats {
    private WakeStats() {}

    public static final class MonthStats {
        public final int wins;
        public final int losses;
        public final int pending;
        MonthStats(int wins, int losses, int pending) {
            this.wins = wins; this.losses = losses; this.pending = pending;
        }
        public int decided() { return wins + losses; }
        public int ratePercent() {
            return decided() == 0 ? -1 : (int)Math.round(wins * 100.0 / decided());
        }
    }

    private static boolean selected(LocalDate date, int mask) {
        int idx = date.getDayOfWeek().getValue() - 1;
        return (mask & (1 << idx)) != 0;
    }

    public static MonthStats forMonth(Context c, YearMonth month) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        LocalDate start = LocalDate.ofEpochDay(Prefs.statsStartEpochDay(c));
        int mask = Prefs.dayMask(c);
        int wins = 0, losses = 0, pending = 0;

        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            if (date.isBefore(start) || !selected(date, mask)) continue;
            if (Prefs.wasSuccessful(c, date)) {
                wins++;
                continue;
            }
            ZonedDateTime due = date.atTime(Prefs.hour(c), Prefs.minute(c)).atZone(zone);
            boolean active = Prefs.active(c) && !Prefs.sessionIsTest(c) && Prefs.sessionEpochDay(c) == date.toEpochDay();
            if (active) pending++;
            else if (due.isBefore(now)) losses++;
            else pending++;
        }
        return new MonthStats(wins, losses, pending);
    }
}
