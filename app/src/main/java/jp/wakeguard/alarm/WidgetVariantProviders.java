package jp.wakeguard.alarm;

public final class WidgetVariantProviders {
    private WidgetVariantProviders(){}
    public static class WorldClockCompactWidgetProvider extends WorldClockWidgetProvider {}
    public static class WorldClockWideWidgetProvider extends WorldClockWidgetProvider {}
    public static class WorldClockTallWidgetProvider extends WorldClockWidgetProvider {}
    public static class LocalClockCompactWidgetProvider extends LocalClockWidgetProvider {}
    public static class LocalClockWideWidgetProvider extends LocalClockWidgetProvider {}
    public static class LocalClockTallWidgetProvider extends LocalClockWidgetProvider {}
    public static class MultiWorldClockCompactWidgetProvider extends MultiWorldClockWidgetProvider {}
    public static class MultiWorldClockTallWidgetProvider extends MultiWorldClockWidgetProvider {}
    public static class MultiWorldClockLargeWidgetProvider extends MultiWorldClockWidgetProvider {}
    public static class NextAlarmSquareWidgetProvider extends NextAlarmWidgetProvider {}
    public static class NextAlarmWideWidgetProvider extends NextAlarmWidgetProvider {}
    public static class NextAlarmTallWidgetProvider extends NextAlarmWidgetProvider {}
    public static class AlarmCountdownSquareWidgetProvider extends AlarmCountdownWidgetProvider {}
    public static class AlarmCountdownWideWidgetProvider extends AlarmCountdownWidgetProvider {}
    public static class AlarmCountdownTallWidgetProvider extends AlarmCountdownWidgetProvider {}
    public static class AlarmListCompactWidgetProvider extends AlarmListWidgetProvider {}
    public static class AlarmListTallWidgetProvider extends AlarmListWidgetProvider {}
    public static class AlarmListLargeWidgetProvider extends AlarmListWidgetProvider {}
    public static class TimerCompactWidgetProvider extends TimerWidgetProvider {}
    public static class TimerSquareWidgetProvider extends TimerWidgetProvider {}
    public static class TimerWideWidgetProvider extends TimerWidgetProvider {}
    public static class TimerPresetCompactWidgetProvider extends TimerPresetWidgetProvider {}
    public static class TimerPresetTallWidgetProvider extends TimerPresetWidgetProvider {}
    public static class TimerPresetLargeWidgetProvider extends TimerPresetWidgetProvider {}
    public static class StopwatchCompactWidgetProvider extends StopwatchWidgetProvider {}
    public static class StopwatchSquareWidgetProvider extends StopwatchWidgetProvider {}
    public static class StopwatchWideWidgetProvider extends StopwatchWidgetProvider {}
    public static class StreakCompactWidgetProvider extends StreakWidgetProvider {}
    public static class StreakWideWidgetProvider extends StreakWidgetProvider {}
    public static class StreakTallWidgetProvider extends StreakWidgetProvider {}
    public static class WakeStatsCompactWidgetProvider extends WakeStatsWidgetProvider {}
    public static class WakeStatsTallWidgetProvider extends WakeStatsWidgetProvider {}
    public static class WakeStatsLargeWidgetProvider extends WakeStatsWidgetProvider {}
    public static class QuickToolsCompactWidgetProvider extends QuickToolsWidgetProvider {}
    public static class QuickToolsTallWidgetProvider extends QuickToolsWidgetProvider {}
    public static class QuickToolsLargeWidgetProvider extends QuickToolsWidgetProvider {}
    public static class TodayCompactWidgetProvider extends TodayWidgetProvider {}
    public static class TodayTallWidgetProvider extends TodayWidgetProvider {}
    public static class TodayLargeWidgetProvider extends TodayWidgetProvider {}

    static void refreshWorld(android.content.Context c){
        WidgetSuite.refresh(c,WorldClockCompactWidgetProvider.class,(m,id)->WorldClockWidgetProvider.updateAppWidget(c,m,id));
        WidgetSuite.refresh(c,WorldClockWideWidgetProvider.class,(m,id)->WorldClockWidgetProvider.updateAppWidget(c,m,id));
        WidgetSuite.refresh(c,WorldClockTallWidgetProvider.class,(m,id)->WorldClockWidgetProvider.updateAppWidget(c,m,id));
    }
    static void refreshNonWorld(android.content.Context c){
        WidgetSuite.refresh(c,LocalClockCompactWidgetProvider.class,(m,id)->LocalClockWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,LocalClockWideWidgetProvider.class,(m,id)->LocalClockWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,LocalClockTallWidgetProvider.class,(m,id)->LocalClockWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,MultiWorldClockCompactWidgetProvider.class,(m,id)->MultiWorldClockWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,MultiWorldClockTallWidgetProvider.class,(m,id)->MultiWorldClockWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,MultiWorldClockLargeWidgetProvider.class,(m,id)->MultiWorldClockWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,NextAlarmSquareWidgetProvider.class,(m,id)->NextAlarmWidgetProvider.updateAppWidget(c,m,id));
        WidgetSuite.refresh(c,NextAlarmWideWidgetProvider.class,(m,id)->NextAlarmWidgetProvider.updateAppWidget(c,m,id));
        WidgetSuite.refresh(c,NextAlarmTallWidgetProvider.class,(m,id)->NextAlarmWidgetProvider.updateAppWidget(c,m,id));
        WidgetSuite.refresh(c,AlarmCountdownSquareWidgetProvider.class,(m,id)->AlarmCountdownWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,AlarmCountdownWideWidgetProvider.class,(m,id)->AlarmCountdownWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,AlarmCountdownTallWidgetProvider.class,(m,id)->AlarmCountdownWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,AlarmListCompactWidgetProvider.class,(m,id)->AlarmListWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,AlarmListTallWidgetProvider.class,(m,id)->AlarmListWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,AlarmListLargeWidgetProvider.class,(m,id)->AlarmListWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,TimerCompactWidgetProvider.class,(m,id)->TimerWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,TimerSquareWidgetProvider.class,(m,id)->TimerWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,TimerWideWidgetProvider.class,(m,id)->TimerWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,TimerPresetCompactWidgetProvider.class,(m,id)->TimerPresetWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,TimerPresetTallWidgetProvider.class,(m,id)->TimerPresetWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,TimerPresetLargeWidgetProvider.class,(m,id)->TimerPresetWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,StopwatchCompactWidgetProvider.class,(m,id)->StopwatchWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,StopwatchSquareWidgetProvider.class,(m,id)->StopwatchWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,StopwatchWideWidgetProvider.class,(m,id)->StopwatchWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,StreakCompactWidgetProvider.class,(m,id)->StreakWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,StreakWideWidgetProvider.class,(m,id)->StreakWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,StreakTallWidgetProvider.class,(m,id)->StreakWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,WakeStatsCompactWidgetProvider.class,(m,id)->WakeStatsWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,WakeStatsTallWidgetProvider.class,(m,id)->WakeStatsWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,WakeStatsLargeWidgetProvider.class,(m,id)->WakeStatsWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,QuickToolsCompactWidgetProvider.class,(m,id)->QuickToolsWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,QuickToolsTallWidgetProvider.class,(m,id)->QuickToolsWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,QuickToolsLargeWidgetProvider.class,(m,id)->QuickToolsWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,TodayCompactWidgetProvider.class,(m,id)->TodayWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,TodayTallWidgetProvider.class,(m,id)->TodayWidgetProvider.update(c,m,id));
        WidgetSuite.refresh(c,TodayLargeWidgetProvider.class,(m,id)->TodayWidgetProvider.update(c,m,id));
    }
}
