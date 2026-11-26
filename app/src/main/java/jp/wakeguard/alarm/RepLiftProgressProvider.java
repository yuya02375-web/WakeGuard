package jp.wakeguard.alarm;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Shares only calendar-day completion and a summary with the signed REP LIFT app. */
public final class RepLiftProgressProvider extends ContentProvider {
    private static final String CLIENT_PACKAGE = "jp.replift.training";
    private static final String LINK_PERMISSION = "jp.wakeguard.alarm.permission.REP_LIFT_LINK";
    private static final int MAX_RESTORE_DAYS = 10_000;

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) throw new IllegalStateException("WakeGuard is unavailable");
        context.enforceCallingPermission(LINK_PERMISSION, "REP LIFT link permission is required");
        if (!CLIENT_PACKAGE.equals(getCallingPackage()) ||
                context.getPackageManager().checkSignatures(Binder.getCallingUid(), Process.myUid())
                        != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException("The signed REP LIFT app is required");
        }

        LocalDate today = LocalDate.now();
        switch (method) {
            case "progress":
                break;
            case "record":
                LocalDate date = SharedStreak.parseDay(extras == null ? null : extras.getString("day"));
                if (!today.equals(date)) throw new IllegalArgumentException("A completion must be for today");
                if (!StreakGrowth.recordTrainingDay(context, date)) {
                    throw new IllegalStateException("Could not save the completion");
                }
                break;
            case "restore":
                restore(context, today, extras);
                break;
            case "unlink":
                break;
            default:
                throw new IllegalArgumentException("Unknown REP LIFT method");
        }
        SharedStreak.setLinked(context, !"unlink".equals(method));

        SharedStreak.Summary summary = SharedStreak.summarize(context, today);
        long level = StreakGrowth.level(context);
        Bundle result = new Bundle();
        result.putInt("current", summary.current);
        result.putInt("best", summary.best);
        result.putString("currentStart", isoDate(summary.currentStart));
        result.putString("currentEnd", isoDate(summary.currentEnd));
        result.putString("bestStart", isoDate(summary.bestStart));
        result.putString("bestEnd", isoDate(summary.bestEnd));
        result.putLong("level", level);
        result.putString("form", StreakGrowth.growthDescriptor(context, level));
        result.putLong("remaining", StreakGrowth.remainingToNext(level));
        result.putInt("stage", StreakGrowth.artStage(level));
        return result;
    }

    private static String isoDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static void restore(Context context, LocalDate today, Bundle extras) {
        ArrayList<String> dates = extras == null ? null : extras.getStringArrayList("days");
        if (dates == null || dates.size() > MAX_RESTORE_DAYS) {
            throw new IllegalArgumentException("A list of activity days is required");
        }
        Set<String> restored = new HashSet<>();
        for (String value : dates) {
            LocalDate date = SharedStreak.parseDay(value);
            if (date != null && !date.isAfter(today)) restored.add(value);
        }
        if (!StreakGrowth.restoreTrainingDays(context, restored)) {
            throw new IllegalStateException("Could not save the activity dates");
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override public String getType(Uri uri) {
        return null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
