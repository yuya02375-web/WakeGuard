package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** A calendar day counts once when either app has a successful activity. */
final class SharedStreak {
    private static final String LINK_FILE = "wakeguard_rep_lift_link_v1";
    private static final String LINKED = "linked";

    private SharedStreak() {}

    private static SharedPreferences linkPrefs(Context context) {
        return context.getSharedPreferences(LINK_FILE, Context.MODE_PRIVATE);
    }

    static boolean isLinked(Context context) {
        return linkPrefs(context).getBoolean(LINKED, false);
    }

    static void setLinked(Context context, boolean linked) {
        SharedPreferences prefs = linkPrefs(context);
        if (prefs.getBoolean(LINKED, false) != linked) prefs.edit().putBoolean(LINKED, linked).apply();
    }

    static Summary summarize(Context context, LocalDate today) {
        Set<String> values = new HashSet<>(Prefs.successDays(context));
        values.addAll(StreakGrowth.linkedTrainingDays(context));
        TreeSet<LocalDate> days = new TreeSet<>();
        for (String value : values) {
            LocalDate day = parseDay(value);
            if (day != null && !day.isAfter(today)) days.add(day);
        }

        LocalDate currentEnd = days.contains(today) ? today : today.minusDays(1);
        LocalDate cursor = currentEnd;
        int current = 0;
        while (days.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        LocalDate currentStart = current > 0 ? cursor.plusDays(1) : null;

        LocalDate previous = null;
        LocalDate runStart = null;
        LocalDate bestStart = null;
        LocalDate bestEnd = null;
        int best = 0;
        int run = 0;
        for (LocalDate day : days) {
            if (previous != null && previous.plusDays(1).equals(day)) {
                run++;
            } else {
                run = 1;
                runStart = day;
            }
            if (run >= best) {
                best = run;
                bestStart = runStart;
                bestEnd = day;
            }
            previous = day;
        }
        return new Summary(current, best, currentStart, current > 0 ? currentEnd : null, bestStart, bestEnd);
    }

    static String displayText(Context context, Summary summary) {
        Locale locale = I18n.locale(context);
        int year = LocalDate.now().getYear();
        String currentLabel = summary.current == 1 ? "現在の連続 1日" : "現在の連続 %d日";
        String bestLabel = summary.best == 1 ? "過去最長 1日" : "過去最長 %d日";
        return I18n.tr(context, "起床・運動の共通ストリーク") + "\n" +
                displayLine(String.format(locale, I18n.tr(context, currentLabel), summary.current),
                        summary.currentStart, summary.currentEnd, locale, year) + "\n" +
                displayLine(String.format(locale, I18n.tr(context, bestLabel), summary.best),
                        summary.bestStart, summary.bestEnd, locale, year) + "\n" +
                I18n.tr(context, "起床成功または運動を記録した連続日数です。同じ日は1日と数えます。");
    }

    private static String displayLine(String value, LocalDate start, LocalDate end, Locale locale, int year) {
        if (start == null || end == null) return value;
        boolean includeYear = start.getYear() != year || end.getYear() != year;
        String language = locale.getLanguage();
        String pattern;
        switch (language) {
            case "ja": pattern = includeYear ? "uuuu年M月d日" : "M月d日"; break;
            case "ko": pattern = includeYear ? "uuuu년 M월 d일" : "M월 d일"; break;
            default: pattern = includeYear ? "MMM d, uuuu" : "MMM d"; break;
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern, locale);
        String dates = start.format(format);
        if (!start.equals(end)) dates += ("ja".equals(language) ? "〜" : " – ") + end.format(format);
        return value + " · " + dates;
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
        final LocalDate currentStart;
        final LocalDate currentEnd;
        final LocalDate bestStart;
        final LocalDate bestEnd;

        Summary(int current, int best, LocalDate currentStart, LocalDate currentEnd,
                LocalDate bestStart, LocalDate bestEnd) {
            this.current = current;
            this.best = best;
            this.currentStart = currentStart;
            this.currentEnd = currentEnd;
            this.bestStart = bestStart;
            this.bestEnd = bestEnd;
        }
    }
}
