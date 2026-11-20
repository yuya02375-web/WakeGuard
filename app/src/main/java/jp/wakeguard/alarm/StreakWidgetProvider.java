package jp.wakeguard.alarm;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

public class StreakWidgetProvider extends AppWidgetProvider {
    private static String dayUnit(Context context, int days) {
        String language = I18n.language(context);
        if ("en".equals(language)) return days == 1 ? " day" : " days";
        return "ko".equals(language) ? "일" : "日";
    }

    static void update(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_streak);
        int streak = StreakTracker.displayCurrent(context);
        views.setTextViewText(R.id.streak_value, streak + dayUnit(context, streak));
        int best = Prefs.bestStreak(context);
        views.setTextViewText(R.id.streak_detail,
                I18n.tr(context, "最高") + " " + best + dayUnit(context, best)
                        + "  ·  " + I18n.tr(context, "成功") + " " + Prefs.totalWakeups(context) + I18n.tr(context, "回"));
        views.setOnClickPendingIntent(R.id.widget_root,
                WidgetSuite.open(context, StatsActivity.class, null, null, 28000 + id));
        manager.updateAppWidget(id, views);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                   int id, android.os.Bundle options) {
        update(context, manager, id);
    }
}