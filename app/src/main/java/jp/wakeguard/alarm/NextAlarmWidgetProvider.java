package jp.wakeguard.alarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class NextAlarmWidgetProvider extends AppWidgetProvider {
    private static int dim(AppWidgetManager m,int id,String key,int fallback){try{return Math.max(1,m.getAppWidgetOptions(id).getInt(key,fallback));}catch(Throwable t){return fallback;}}
    private static int layout(AppWidgetManager m,int id){
        int w=dim(m,id,AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180),h=dim(m,id,AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110);
        if(h>w*1.22f)return R.layout.widget_next_alarm_tall;
        if(w>h*1.28f)return R.layout.widget_next_alarm_wide;
        return R.layout.widget_next_alarm_square;
    }
    static void updateAppWidget(Context c,AppWidgetManager manager,int appWidgetId){
        RemoteViews rv=new RemoteViews(c.getPackageName(),layout(manager,appWidgetId));
        rv.setTextViewText(R.id.next_alarm_label,I18n.tr(c,"次のアラーム"));
        long at=AlarmScheduler.nextTriggerMillis(c);
        if(at>0){
            ZoneId zone=ZoneId.systemDefault();ZonedDateTime z=Instant.ofEpochMilli(at).atZone(zone);LocalDate today=LocalDate.now(zone);
            rv.setTextViewText(R.id.next_alarm_time,z.format(DateTimeFormatter.ofPattern("HH:mm",I18n.locale(c))));
            String day=z.toLocalDate().equals(today)?I18n.tr(c,"今日"):z.toLocalDate().equals(today.plusDays(1))?I18n.tr(c,"明日"):z.format(DateTimeFormatter.ofPattern("M/d EEE",I18n.locale(c)));
            rv.setTextViewText(R.id.next_alarm_detail,day);
        }else{rv.setTextViewText(R.id.next_alarm_time,"--:--");rv.setTextViewText(R.id.next_alarm_detail,I18n.tr(c,"アラームなし"));}
        Intent open=new Intent(c,MainActivity.class).setData(Uri.parse("wakeguard://widget/next-alarm/"+appWidgetId));
        PendingIntent pi=PendingIntent.getActivity(c,50000+appWidgetId,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);rv.setOnClickPendingIntent(R.id.next_alarm_root,pi);
        manager.updateAppWidget(appWidgetId,rv);
    }
    static void refreshAll(Context c){try{AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,NextAlarmWidgetProvider.class));for(int id:ids)updateAppWidget(c,m,id);}catch(Throwable ignored){}}
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)updateAppWidget(c,m,id);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle options){updateAppWidget(c,m,id);}
}
