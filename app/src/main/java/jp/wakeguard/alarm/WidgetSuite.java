package jp.wakeguard.alarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
    static int w(AppWidgetManager m,int id){try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180));}catch(Throwable t){return 180;}}
    static int h(AppWidgetManager m,int id){try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110));}catch(Throwable t){return 110;}}
    static boolean tiny(AppWidgetManager m,int id){return w(m,id)<100||h(m,id)<72;}
    static boolean large(AppWidgetManager m,int id){return w(m,id)>=260&&h(m,id)>=180;}
    static PendingIntent open(Context c,Class<?> cls,String key,String value,int request){Intent i=new Intent(c,cls);if(key!=null)i.putExtra(key,value);i.setData(Uri.parse("wakeguard://widget/open/"+request+"/"+System.currentTimeMillis()));return PendingIntent.getActivity(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static PendingIntent action(Context c,String action,long value,int request){Intent i=new Intent(c,WidgetActionReceiver.class).setAction(action).putExtra("value",value).setData(Uri.parse("wakeguard://widget/action/"+request+"/"+value));return PendingIntent.getBroadcast(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static String fmtTimer(long ms){long t=Math.max(0,(ms+999)/1000),hh=t/3600,mm=(t/60)%60,ss=t%60;return hh>0?String.format(Locale.US,"%02d:%02d:%02d",hh,mm,ss):String.format(Locale.US,"%02d:%02d",mm,ss);}
    static String date(Context c,long when){return Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d EEE",I18n.locale(c)));}
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
