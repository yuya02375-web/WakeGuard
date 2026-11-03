package jp.wakeguard.alarm;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WidgetPinResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i){
        if(i==null)return;
        int id=i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID)return;
        if("world".equals(i.getStringExtra("kind"))){
            String zone=i.getStringExtra("zone"),style=i.getStringExtra("style"),label=i.getStringExtra("label");
            if(zone==null||zone.isEmpty())zone=java.time.ZoneId.systemDefault().getId();
            if(label==null||label.trim().isEmpty())label=WorldClockWidgetProvider.zoneLabel(c,zone);
            WorldClockWidgetProvider.saveConfig(c,id,zone,style,label);
            WorldClockWidgetProvider.updateAppWidget(c,AppWidgetManager.getInstance(c),id);
        }
    }
}
