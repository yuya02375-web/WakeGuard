package jp.wakeguard.alarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.time.ZoneId;

public class WorldClockWidgetProvider extends AppWidgetProvider {
    private static final String PREF="wakeguard_world_clock_widgets";
    private static final String KEY_ZONE="zone_";
    private static final String KEY_STYLE="style_";
    private static final String KEY_LABEL="label_";

    static void saveZone(Context c,int appWidgetId,String zoneId){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_ZONE+appWidgetId,zoneId).apply();}
    static void saveStyle(Context c,int appWidgetId,String style){if(!"analog".equals(style)&&!"both".equals(style))style="digital";c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_STYLE+appWidgetId,style).apply();}
    static void saveConfig(Context c,int appWidgetId,String zoneId,String style){saveConfig(c,appWidgetId,zoneId,style,zoneLabel(c,zoneId));}
    static void saveConfig(Context c,int appWidgetId,String zoneId,String style,String label){
        if(!"analog".equals(style)&&!"both".equals(style))style="digital";if(label==null||label.trim().isEmpty())label=zoneLabel(c,zoneId);
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_ZONE+appWidgetId,zoneId).putString(KEY_STYLE+appWidgetId,style).putString(KEY_LABEL+appWidgetId,label.trim()).apply();
    }

    static String loadZone(Context c,int appWidgetId){String z=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_ZONE+appWidgetId,"");if(z==null||z.isEmpty())z=ZoneId.systemDefault().getId();try{ZoneId.of(z);}catch(Throwable t){z=ZoneId.systemDefault().getId();}return z;}
    static String loadStyle(Context c,int appWidgetId){String x=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_STYLE+appWidgetId,"digital");return "analog".equals(x)?"analog":"both".equals(x)?"both":"digital";}
    static String loadLabel(Context c,int appWidgetId){String zone=loadZone(c,appWidgetId);String x=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_LABEL+appWidgetId,"");return x==null||x.trim().isEmpty()?zoneLabel(c,zone):x.trim();}

    static String zoneLabel(Context c,String zoneId){
        try{String x=android.icu.text.TimeZoneNames.getInstance(I18n.locale(c)).getExemplarLocationName(zoneId);if(x!=null&&!x.trim().isEmpty())return x.trim();}catch(Throwable ignored){}
        String last=zoneId.contains("/")?zoneId.substring(zoneId.lastIndexOf('/')+1):zoneId;return last.replace('_',' ');
    }

    private static int width(AppWidgetManager m,int id){try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180));}catch(Throwable t){return 180;}}
    private static int height(AppWidgetManager m,int id){try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110));}catch(Throwable t){return 110;}}
    private static int chooseLayout(String style,int w,int h){boolean tall=h>w*1.22f,wide=w>h*1.28f;if("both".equals(style)){if(tall)return R.layout.widget_world_clock_both_tall;if(wide)return R.layout.widget_world_clock_both_wide;return R.layout.widget_world_clock_both_square;}if("analog".equals(style)){if(tall)return R.layout.widget_world_clock_analog_tall;if(wide)return R.layout.widget_world_clock_analog_wide;return R.layout.widget_world_clock_analog_square;}if(tall)return R.layout.widget_world_clock_digital_tall;if(wide)return R.layout.widget_world_clock_digital_wide;return R.layout.widget_world_clock_digital_square;}

    static void updateAppWidget(Context c,AppWidgetManager manager,int appWidgetId){
        String zone=loadZone(c,appWidgetId),style=loadStyle(c,appWidgetId),label=loadLabel(c,appWidgetId);if(Build.VERSION.SDK_INT<31&&("analog".equals(style)||"both".equals(style)))style="digital";boolean analog="analog".equals(style),both="both".equals(style);boolean h24=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE).getBoolean("use_24h",true);
        RemoteViews rv=new RemoteViews(c.getPackageName(),chooseLayout(style,width(manager,appWidgetId),height(manager,appWidgetId)));rv.setTextViewText(R.id.widget_city,label);rv.setTextViewText(R.id.widget_zone,zone);rv.setString(R.id.widget_date,"setTimeZone",zone);rv.setCharSequence(R.id.widget_date,"setFormat24Hour","M/d EEE");rv.setCharSequence(R.id.widget_date,"setFormat12Hour","M/d EEE");
        if(analog||both)rv.setString(R.id.widget_analog,"setTimeZone",zone);if(!analog){rv.setString(R.id.widget_time,"setTimeZone",zone);String timeFmt=h24?"HH:mm":"hh:mm a";rv.setCharSequence(R.id.widget_time,"setFormat24Hour",timeFmt);rv.setCharSequence(R.id.widget_time,"setFormat12Hour",timeFmt);}
        Intent open=new Intent(c,ClockFaceActivity.class).putExtra("clock_mode","world").putExtra("zone",zone).setData(Uri.parse("wakeguard://widget/world/"+appWidgetId));PendingIntent pi=PendingIntent.getActivity(c,appWidgetId,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);rv.setOnClickPendingIntent(R.id.widget_root,pi);manager.updateAppWidget(appWidgetId,rv);
    }
    static void refreshAll(Context c){try{AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,WorldClockWidgetProvider.class));for(int id:ids)updateAppWidget(c,m,id);}catch(Throwable ignored){}try{WidgetVariantProviders.refreshWorld(c);}catch(Throwable ignored){}}
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)updateAppWidget(c,m,id);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){updateAppWidget(c,m,id);}
    @Override public void onDeleted(Context c,int[] ids){android.content.SharedPreferences.Editor e=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();for(int id:ids){e.remove(KEY_ZONE+id);e.remove(KEY_STYLE+id);e.remove(KEY_LABEL+id);}e.apply();}
}
