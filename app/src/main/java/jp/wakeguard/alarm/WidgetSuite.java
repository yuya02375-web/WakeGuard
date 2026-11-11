package jp.wakeguard.alarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class WidgetSuite {
    private WidgetSuite(){}
    private static String lastPaletteWidgetKey;
    static int w(AppWidgetManager m,int id){try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180));}catch(Throwable t){return 180;}}
    static int h(AppWidgetManager m,int id){try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110));}catch(Throwable t){return 110;}}
    static boolean tiny(AppWidgetManager m,int id){return w(m,id)<100||h(m,id)<72;}
    static boolean large(AppWidgetManager m,int id){return w(m,id)>=260&&h(m,id)>=180;}
    static PendingIntent open(Context c,Class<?> cls,String key,String value,int request){Intent i=new Intent(c,cls);if(key!=null)i.putExtra(key,value);i.setData(Uri.parse("wakeguard://widget/open/"+request+"/"+System.currentTimeMillis()));return PendingIntent.getActivity(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static PendingIntent action(Context c,String action,long value,int request){Intent i=new Intent(c,WidgetActionReceiver.class).setAction(action).putExtra("value",value).setData(Uri.parse("wakeguard://widget/action/"+request+"/"+value));return PendingIntent.getBroadcast(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static String fmtTimer(long ms){long t=Math.max(0,(ms+999)/1000),hh=t/3600,mm=(t/60)%60,ss=t%60;return hh>0?String.format(Locale.US,"%02d:%02d:%02d",hh,mm,ss):String.format(Locale.US,"%02d:%02d",mm,ss);}
    static String date(Context c,long when){return Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d EEE",I18n.locale(c)));}
    /** Applies the saved theme accent to palette-highlighted labels in themed widgets. */
    static void applyPaletteAccent(Context c,RemoteViews views,int... viewIds){
        if(c==null||views==null||viewIds==null)return;
        int accent=readablePaletteAccent(AppThemes.current(c).accent);
        for(int viewId:viewIds)views.setTextColor(viewId,accent);
    }
    /** Re-renders existing palette-aware widgets when the selected theme is applied. */
    static synchronized void refreshPaletteWidgets(Context c){
        if(c==null)return;
        AppThemes.Theme theme=AppThemes.current(c);
        String key=AppThemes.key(c)+":"+(theme.dark?"dark":"light");
        if(key.equals(lastPaletteWidgetKey))return;
        refresh(c,StreakWidgetProvider.class,(m,id)->StreakWidgetProvider.update(c,m,id));
        refresh(c,TodayWidgetProvider.class,(m,id)->TodayWidgetProvider.update(c,m,id));
        refresh(c,WakeStatsWidgetProvider.class,(m,id)->WakeStatsWidgetProvider.update(c,m,id));
        lastPaletteWidgetKey=key;
    }
    private static int readablePaletteAccent(int accent){
        // widget_bg is translucent (#F2101A26); use a slightly brighter conservative
        // surface bound so the accent remains readable over varied home-screen wallpapers.
        final int widgetSurface=0xFF1E2732;
        if(contrastRatio(accent,widgetSurface)>=4.5)return accent;
        float[] hsv=new float[3];
        Color.colorToHSV(accent,hsv);
        for(int step=(int)Math.ceil(hsv[2]*100);step<=100;step++){
            hsv[2]=step/100f;
            int candidate=Color.HSVToColor(hsv);
            if(contrastRatio(candidate,widgetSurface)>=4.5)return candidate;
        }
        return Color.WHITE;
    }
    private static double contrastRatio(int foreground,int background){
        double first=luminance(foreground),second=luminance(background);
        return (Math.max(first,second)+.05)/(Math.min(first,second)+.05);
    }
    private static double luminance(int color){
        double r=linear(Color.red(color)/255.0),g=linear(Color.green(color)/255.0),b=linear(Color.blue(color)/255.0);
        return .2126*r+.7152*g+.0722*b;
    }
    private static double linear(double channel){
        return channel<=.04045?channel/12.92:Math.pow((channel+.055)/1.055,2.4);
    }
    static void refreshAll(Context c){
        try{WorldClockWidgetProvider.refreshAll(c);}catch(Throwable ignored){}
        refresh(c,NextAlarmWidgetProvider.class,(m,id)->NextAlarmWidgetProvider.updateAppWidget(c,m,id));
        refresh(c,LocalClockWidgetProvider.class,(m,id)->LocalClockWidgetProvider.update(c,m,id));
        refresh(c,MultiWorldClockWidgetProvider.class,(m,id)->MultiWorldClockWidgetProvider.update(c,m,id));
        refresh(c,AlarmListWidgetProvider.class,(m,id)->AlarmListWidgetProvider.update(c,m,id));
        refresh(c,AlarmCountdownWidgetProvider.class,(m,id)->AlarmCountdownWidgetProvider.update(c,m,id));
        refresh(c,TimerWidgetProvider.class,(m,id)->TimerWidgetProvider.update(c,m,id));
        refresh(c,TimerPresetWidgetProvider.class,(m,id)->TimerPresetWidgetProvider.update(c,m,id));
        refresh(c,StopwatchWidgetProvider.class,(m,id)->StopwatchWidgetProvider.update(c,m,id));
        refresh(c,StreakWidgetProvider.class,(m,id)->StreakWidgetProvider.update(c,m,id));
        refresh(c,WakeStatsWidgetProvider.class,(m,id)->WakeStatsWidgetProvider.update(c,m,id));
        refresh(c,QuickToolsWidgetProvider.class,(m,id)->QuickToolsWidgetProvider.update(c,m,id));
        refresh(c,TodayWidgetProvider.class,(m,id)->TodayWidgetProvider.update(c,m,id));
        try{WidgetVariantProviders.refreshNonWorld(c);}catch(Throwable ignored){}
    }
    interface U{void x(AppWidgetManager m,int id);}static void refresh(Context c,Class<?> cls,U u){try{AppWidgetManager m=AppWidgetManager.getInstance(c);for(int id:m.getAppWidgetIds(new ComponentName(c,cls)))u.x(m,id);}catch(Throwable ignored){}}
    static List<AlarmStore.Entry> enabledAlarms(Context c){ArrayList<AlarmStore.Entry> x=new ArrayList<>();for(AlarmStore.Entry e:AlarmProfiles.all(c))if(e.enabled)x.add(e);x.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));return x;}
    static String alarmLabel(AlarmStore.Entry e){return e.label==null||e.label.trim().isEmpty()?"アラーム":e.label.trim();}
}
