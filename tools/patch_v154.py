from pathlib import Path
import re, runpy
runpy.run_path('tools/patch_v153.py', run_name='__main__')
app=Path('WakeGuard/app')
j=app/'src/main/java/jp/wakeguard/alarm'
res=app/'src/main/res'

# version
p=app/'build.gradle.kts'; s=p.read_text(encoding='utf-8')
s=re.sub(r'versionCode = \d+','versionCode = 65',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.5.4"',s)
p.write_text(s,encoding='utf-8')

world_provider=r'''package jp.wakeguard.alarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import java.time.ZoneId;
import java.util.Locale;

public class WorldClockWidgetProvider extends AppWidgetProvider {
    private static final String PREF="wakeguard_world_clock_widgets";
    private static final String KEY_PREFIX="zone_";

    static void saveZone(Context c,int appWidgetId,String zoneId){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_PREFIX+appWidgetId,zoneId).apply();
    }

    static String loadZone(Context c,int appWidgetId){
        String z=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_PREFIX+appWidgetId,"");
        if(z==null||z.isEmpty())z=ZoneId.systemDefault().getId();
        try{ZoneId.of(z);}catch(Throwable t){z=ZoneId.systemDefault().getId();}
        return z;
    }

    static String zoneLabel(Context c,String zoneId){
        try{
            String x=android.icu.text.TimeZoneNames.getInstance(I18n.locale(c)).getExemplarLocationName(zoneId);
            if(x!=null&&!x.trim().isEmpty())return x.trim();
        }catch(Throwable ignored){}
        String last=zoneId.contains("/")?zoneId.substring(zoneId.lastIndexOf('/')+1):zoneId;
        return last.replace('_',' ');
    }

    static void updateAppWidget(Context c,AppWidgetManager manager,int appWidgetId){
        String zone=loadZone(c,appWidgetId);
        boolean h24=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE).getBoolean("use_24h",true);
        RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.widget_world_clock);
        rv.setTextViewText(R.id.widget_city,zoneLabel(c,zone));
        rv.setTextViewText(R.id.widget_zone,zone);
        rv.setString(R.id.widget_time,"setTimeZone",zone);
        rv.setString(R.id.widget_date,"setTimeZone",zone);
        String timeFmt=h24?"HH:mm":"hh:mm a";
        rv.setCharSequence(R.id.widget_time,"setFormat24Hour",timeFmt);
        rv.setCharSequence(R.id.widget_time,"setFormat12Hour",timeFmt);
        rv.setCharSequence(R.id.widget_date,"setFormat24Hour","M/d EEE");
        rv.setCharSequence(R.id.widget_date,"setFormat12Hour","M/d EEE");

        Intent open=new Intent(c,ClockFaceActivity.class)
                .putExtra("clock_mode","world").putExtra("zone",zone)
                .setData(Uri.parse("wakeguard://widget/world/"+appWidgetId));
        PendingIntent pi=PendingIntent.getActivity(c,appWidgetId,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_root,pi);
        manager.updateAppWidget(appWidgetId,rv);
    }

    static void refreshAll(Context c){
        try{
            AppWidgetManager m=AppWidgetManager.getInstance(c);
            int[] ids=m.getAppWidgetIds(new ComponentName(c,WorldClockWidgetProvider.class));
            for(int id:ids)updateAppWidget(c,m,id);
        }catch(Throwable ignored){}
    }

    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)updateAppWidget(c,m,id);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle options){updateAppWidget(c,m,id);}
    @Override public void onDeleted(Context c,int[] ids){
        android.content.SharedPreferences.Editor e=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();
        for(int id:ids)e.remove(KEY_PREFIX+id);e.apply();
    }
}
'''
(j/'WorldClockWidgetProvider.java').write_text(world_provider,encoding='utf-8')

config=r'''package jp.wakeguard.alarm;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class WorldClockWidgetConfigActivity extends Activity {
    private int appWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private final ArrayList<String> ordered=new ArrayList<>(),visible=new ArrayList<>();
    private BaseAdapter adapter;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);Ui.prepareActivity(this);setResult(RESULT_CANCELED);
        appWidgetId=getIntent()==null?AppWidgetManager.INVALID_APPWIDGET_ID:getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(appWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        buildZoneList();buildUi();
    }

    private void buildZoneList(){
        LinkedHashSet<String> first=new LinkedHashSet<>();
        first.add(ZoneId.systemDefault().getId());
        String raw=getSharedPreferences("clock_tools",MODE_PRIVATE).getString("world_zones","");
        if(raw!=null)for(String z:raw.split("\\n"))if(valid(z))first.add(z);
        for(String z:new String[]{"Asia/Tokyo","Asia/Seoul","America/New_York","America/Los_Angeles","Europe/London","Europe/Paris","Asia/Singapore","Australia/Sydney"})if(valid(z))first.add(z);
        ArrayList<String> rest=new ArrayList<>();
        for(String z:ZoneId.getAvailableZoneIds())if(z.contains("/")&&!z.startsWith("Etc/")&&!first.contains(z))rest.add(z);
        Collections.sort(rest);
        ordered.addAll(first);ordered.addAll(rest);visible.addAll(ordered);
    }

    private boolean valid(String z){if(z==null||z.isEmpty())return false;try{ZoneId.of(z);return true;}catch(Throwable t){return false;}}
    private String norm(String x){return x==null?"":x.toLowerCase(Locale.ROOT).replace('_',' ').trim();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);root.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,18));
        TextView title=Ui.title(this,"世界時計ウィジェット",26);root.addView(title);
        TextView sub=Ui.text(this,"表示するタイムゾーンを選択",14,Ui.MUTED);root.addView(sub,Ui.gapTop(this,4));
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED_2);search.setHint(I18n.tr(this,"タイムゾーンを検索"));search.setTextSize(16);search.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));search.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);root.addView(search,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));
        TextView note=Ui.text(this,"24時間表示は世界時計の設定と連動します",11,Ui.MUTED_2);root.addView(note,Ui.gapTop(this,6));

        ListView list=new ListView(this);list.setDividerHeight(0);list.setBackgroundColor(Ui.BG);list.setClipToPadding(false);list.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));
        adapter=new BaseAdapter(){
            @Override public int getCount(){return visible.size();}
            @Override public Object getItem(int pos){return visible.get(pos);}
            @Override public long getItemId(int pos){return pos;}
            @Override public View getView(int pos,View convert,ViewGroup parent){
                LinearLayout row;TextView city,zone;
                if(convert instanceof LinearLayout&&convert.getTag() instanceof TextView[]){row=(LinearLayout)convert;TextView[] h=(TextView[])row.getTag();city=h[0];zone=h[1];}
                else{row=new LinearLayout(WorldClockWidgetConfigActivity.this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(WorldClockWidgetConfigActivity.this,14),Ui.dp(WorldClockWidgetConfigActivity.this,11),Ui.dp(WorldClockWidgetConfigActivity.this,14),Ui.dp(WorldClockWidgetConfigActivity.this,11));row.setBackground(Ui.round(Ui.SURFACE,14,WorldClockWidgetConfigActivity.this));city=Ui.text(WorldClockWidgetConfigActivity.this,"",16,Ui.TEXT);city.setTypeface(null,Typeface.BOLD);zone=Ui.text(WorldClockWidgetConfigActivity.this,"",11,Ui.MUTED);row.addView(city);row.addView(zone,Ui.gapTop(WorldClockWidgetConfigActivity.this,2));row.setTag(new TextView[]{city,zone});}
                String id=visible.get(pos);String label=WorldClockWidgetProvider.zoneLabel(WorldClockWidgetConfigActivity.this,id);if(id.equals(ZoneId.systemDefault().getId()))label=I18n.tr(WorldClockWidgetConfigActivity.this,"現在のタイムゾーン")+" · "+label;city.setText(label);zone.setText(id);
                android.widget.AbsListView.LayoutParams lp=new android.widget.AbsListView.LayoutParams(-1,Ui.dp(WorldClockWidgetConfigActivity.this,70));row.setLayoutParams(lp);return row;
            }
        };
        list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,pos,id)->select(visible.get(pos)));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter(s==null?"":s.toString());}public void afterTextChanged(Editable e){}});
        setContentView(root);Ui.applySystemBarInsets(this,root);
    }

    private void filter(String q){
        String n=norm(q);visible.clear();
        if(n.isEmpty())visible.addAll(ordered);else for(String z:ordered){String label=WorldClockWidgetProvider.zoneLabel(this,z);if(norm(z).contains(n)||norm(label).contains(n))visible.add(z);}
        adapter.notifyDataSetChanged();
    }

    private void select(String zone){
        WorldClockWidgetProvider.saveZone(this,appWidgetId,zone);
        WorldClockWidgetProvider.updateAppWidget(this,AppWidgetManager.getInstance(this),appWidgetId);
        Intent result=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,appWidgetId);setResult(RESULT_OK,result);finish();
    }
}
'''
(j/'WorldClockWidgetConfigActivity.java').write_text(config,encoding='utf-8')

next_provider=r'''package jp.wakeguard.alarm;

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
    static void updateAppWidget(Context c,AppWidgetManager manager,int appWidgetId){
        RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.widget_next_alarm);
        rv.setTextViewText(R.id.next_alarm_label,I18n.tr(c,"次のアラーム"));
        long at=AlarmScheduler.nextTriggerMillis(c);
        if(at>0){
            ZoneId zone=ZoneId.systemDefault();ZonedDateTime z=Instant.ofEpochMilli(at).atZone(zone);LocalDate today=LocalDate.now(zone);
            rv.setTextViewText(R.id.next_alarm_time,z.format(DateTimeFormatter.ofPattern("HH:mm",I18n.locale(c))));
            String day=z.toLocalDate().equals(today)?I18n.tr(c,"今日"):z.toLocalDate().equals(today.plusDays(1))?I18n.tr(c,"明日"):z.format(DateTimeFormatter.ofPattern("M/d EEE",I18n.locale(c)));
            rv.setTextViewText(R.id.next_alarm_detail,day);
        }else{
            rv.setTextViewText(R.id.next_alarm_time,"--:--");rv.setTextViewText(R.id.next_alarm_detail,I18n.tr(c,"アラームなし"));
        }
        Intent open=new Intent(c,MainActivity.class).setData(Uri.parse("wakeguard://widget/next-alarm/"+appWidgetId));
        PendingIntent pi=PendingIntent.getActivity(c,50000+appWidgetId,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);rv.setOnClickPendingIntent(R.id.next_alarm_root,pi);
        manager.updateAppWidget(appWidgetId,rv);
    }
    static void refreshAll(Context c){try{AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,NextAlarmWidgetProvider.class));for(int id:ids)updateAppWidget(c,m,id);}catch(Throwable ignored){}}
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)updateAppWidget(c,m,id);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle options){updateAppWidget(c,m,id);}
}
'''
(j/'NextAlarmWidgetProvider.java').write_text(next_provider,encoding='utf-8')

(res/'drawable').mkdir(parents=True,exist_ok=True)
(res/'layout').mkdir(parents=True,exist_ok=True)
(res/'xml').mkdir(parents=True,exist_ok=True)
(res/'drawable/widget_bg.xml').write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#E61B1D22" />
    <stroke android:width="1dp" android:color="#33FFFFFF" />
    <corners android:radius="22dp" />
    <padding android:left="14dp" android:top="10dp" android:right="14dp" android:bottom="10dp" />
</shape>
''',encoding='utf-8')
(res/'layout/widget_world_clock.xml').write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:gravity="center_vertical" android:background="@drawable/widget_bg">
    <TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="東京" android:textColor="#FFFFFFFF" android:textSize="14sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end" />
    <TextClock android:id="@+id/widget_time" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:textColor="#FFFFFFFF" android:textSize="34sp" android:textStyle="bold" android:fontFamily="monospace"
        android:format24Hour="HH:mm" android:format12Hour="hh:mm a" />
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical">
        <TextClock android:id="@+id/widget_date" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textColor="#CCFFFFFF" android:textSize="11sp" android:format24Hour="M/d EEE" android:format12Hour="M/d EEE" />
        <TextView android:id="@+id/widget_zone" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
            android:layout_marginStart="8dp" android:text="Asia/Tokyo" android:textColor="#99FFFFFF" android:textSize="10sp" android:maxLines="1" android:ellipsize="end" />
    </LinearLayout>
</LinearLayout>
''',encoding='utf-8')
(res/'layout/widget_next_alarm.xml').write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/next_alarm_root" android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:gravity="center_vertical" android:background="@drawable/widget_bg">
    <TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:id="@+id/next_alarm_label" android:text="次のアラーム"
        android:textColor="#CCFFFFFF" android:textSize="13sp" android:textStyle="bold" />
    <TextView android:id="@+id/next_alarm_time" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="06:30" android:textColor="#FFFFFFFF" android:textSize="34sp" android:textStyle="bold" android:fontFamily="monospace" />
    <TextView android:id="@+id/next_alarm_detail" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="明日" android:textColor="#B3FFFFFF" android:textSize="11sp" />
</LinearLayout>
''',encoding='utf-8')
(res/'xml/world_clock_widget_info.xml').write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp" android:minHeight="90dp" android:minResizeWidth="120dp" android:minResizeHeight="70dp"
    android:targetCellWidth="2" android:targetCellHeight="1" android:maxResizeWidth="360dp" android:maxResizeHeight="200dp"
    android:updatePeriodMillis="0" android:initialLayout="@layout/widget_world_clock" android:previewLayout="@layout/widget_world_clock"
    android:configure="jp.wakeguard.alarm.WorldClockWidgetConfigActivity" android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen|keyguard" android:widgetFeatures="reconfigurable" />
''',encoding='utf-8')
(res/'xml/next_alarm_widget_info.xml').write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp" android:minHeight="90dp" android:minResizeWidth="120dp" android:minResizeHeight="70dp"
    android:targetCellWidth="2" android:targetCellHeight="1" android:maxResizeWidth="360dp" android:maxResizeHeight="200dp"
    android:updatePeriodMillis="1800000" android:initialLayout="@layout/widget_next_alarm" android:previewLayout="@layout/widget_next_alarm"
    android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen|keyguard" />
''',encoding='utf-8')

# Manifest
p=res.parent/'AndroidManifest.xml'; s=p.read_text(encoding='utf-8')
s=s.replace('<activity android:name=".ClockFaceActivity" android:exported="false" />','<activity android:name=".ClockFaceActivity" android:exported="false" android:showWhenLocked="true" />',1)
anchor='''        <activity android:name=".ClockSettingsActivity" android:exported="false" />'''
insert=anchor+'''\n        <activity android:name=".WorldClockWidgetConfigActivity" android:exported="true" android:excludeFromRecents="true">\n            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" /></intent-filter>\n        </activity>'''
if anchor not in s: raise SystemExit('manifest clock settings anchor missing')
s=s.replace(anchor,insert,1)
anchor='''        <receiver android:name=".AlarmReceiver" android:exported="false" />'''
insert='''        <receiver android:name=".WorldClockWidgetProvider" android:exported="true" android:label="世界時計">\n            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>\n            <meta-data android:name="android.appwidget.provider" android:resource="@xml/world_clock_widget_info" />\n        </receiver>\n        <receiver android:name=".NextAlarmWidgetProvider" android:exported="true" android:label="次のアラーム">\n            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>\n            <meta-data android:name="android.appwidget.provider" android:resource="@xml/next_alarm_widget_info" />\n        </receiver>\n\n'''+anchor
if anchor not in s: raise SystemExit('manifest receiver anchor missing')
s=s.replace(anchor,insert,1)
p.write_text(s,encoding='utf-8')

# ClockActivity: refresh widgets when 24h preference changes.
p=j/'ClockActivity.java'; s=p.read_text(encoding='utf-8')
old='''h24.setOnCheckedChangeListener((v,checked)->{p().edit().putBoolean(KEY_24H,checked).apply();updateLiveUi();});'''
new='''h24.setOnCheckedChangeListener((v,checked)->{p().edit().putBoolean(KEY_24H,checked).apply();updateLiveUi();WorldClockWidgetProvider.refreshAll(this);});'''
if old not in s: raise SystemExit('clock 24h anchor missing')
s=s.replace(old,new,1);p.write_text(s,encoding='utf-8')

# Clock settings: discoverable one-tap pin actions.
p=j/'ClockSettingsActivity.java'; s=p.read_text(encoding='utf-8')
old='''        section("アナログ時計");addSwitch("文字盤に数字を表示","analog_numbers",true);addSwitch("分の目盛りを表示","analog_ticks",true);addSwitch("秒針を表示","analog_seconds",true);addNumberScale();\n        Button reset='''
new='''        section("アナログ時計");addSwitch("文字盤に数字を表示","analog_numbers",true);addSwitch("分の目盛りを表示","analog_ticks",true);addSwitch("秒針を表示","analog_seconds",true);addNumberScale();\n        section("ウィジェット");TextView wh=Ui.text(this,"ホーム画面に世界時計や次のアラームを置けます。対応するAndroidでは同じウィジェットをロック画面にも追加できます。",12,Ui.MUTED);body.addView(wh,Ui.gapTop(this,4));Button ww=Ui.button(this,"世界時計ウィジェットを追加",false);ww.setOnClickListener(v->pinWidget(WorldClockWidgetProvider.class,"世界時計"));body.addView(ww,Ui.gapTop(this,10));Button aw=Ui.button(this,"次のアラームウィジェットを追加",false);aw.setOnClickListener(v->pinWidget(NextAlarmWidgetProvider.class,"次のアラーム"));body.addView(aw,Ui.gapTop(this,8));\n        Button reset='''
if old not in s: raise SystemExit('clock settings section anchor missing')
s=s.replace(old,new,1)
old='''    private void addSwitch(String label,String key,boolean def){LinearLayout row=Ui.row(this);TextView t=Ui.text(this,label,15,Ui.TEXT);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(p.getBoolean(key,def));sw.setOnCheckedChangeListener((v,x)->p.edit().putBoolean(key,x).apply());row.addView(sw);body.addView(row);body.addView(Ui.divider(this));}\n'''
new='''    private void addSwitch(String label,String key,boolean def){LinearLayout row=Ui.row(this);TextView t=Ui.text(this,label,15,Ui.TEXT);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(p.getBoolean(key,def));sw.setOnCheckedChangeListener((v,x)->{p.edit().putBoolean(key,x).apply();if("use_24h".equals(key))WorldClockWidgetProvider.refreshAll(this);});row.addView(sw);body.addView(row);body.addView(Ui.divider(this));}\n    private void pinWidget(Class<?> provider,String name){try{if(android.os.Build.VERSION.SDK_INT>=26){android.appwidget.AppWidgetManager m=android.appwidget.AppWidgetManager.getInstance(this);if(m.isRequestPinAppWidgetSupported()){boolean ok=m.requestPinAppWidget(new ComponentName(this,provider),null,null);Toast.makeText(this,I18n.tr(this,ok?"追加画面を開きました":"このホーム画面では直接追加できません"),Toast.LENGTH_SHORT).show();return;}}}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"ホーム画面を長押し → ウィジェット → WakeGuard から追加してください"),Toast.LENGTH_LONG).show();}\n'''
if old not in s: raise SystemExit('clock settings switch anchor missing')
s=s.replace(old,new,1);p.write_text(s,encoding='utf-8')

# Next alarm widget refresh whenever alarm schedule changes.
p=j/'AlarmScheduler.java'; s=p.read_text(encoding='utf-8')
old='''        } catch(Throwable t) {\n            try{Prefs.lastAlarmError(c,"AlarmScheduler: "+t.getClass().getSimpleName()+(t.getMessage()==null?"":": "+t.getMessage()));}catch(Throwable ignored){}\n        }\n    }\n\n    public static void scheduleRecoveryIfActive'''
new='''        } catch(Throwable t) {\n            try{Prefs.lastAlarmError(c,"AlarmScheduler: "+t.getClass().getSimpleName()+(t.getMessage()==null?"":": "+t.getMessage()));}catch(Throwable ignored){}\n        }\n        try{NextAlarmWidgetProvider.refreshAll(c);}catch(Throwable ignored){}\n    }\n\n    public static void scheduleRecoveryIfActive'''
if old not in s: raise SystemExit('scheduler anchor missing')
s=s.replace(old,new,1);p.write_text(s,encoding='utf-8')

# Basic I18n entries for new user-facing labels.
p=j/'I18n.java'; s=p.read_text(encoding='utf-8')
needle='put("停止","Stop","중지");'
extra=''' put("世界時計ウィジェット","World clock widget","세계 시계 위젯"); put("表示するタイムゾーンを選択","Choose a time zone to display","표시할 시간대를 선택하세요"); put("タイムゾーンを検索","Search time zones","시간대 검색"); put("24時間表示は世界時計の設定と連動します","24-hour display follows World Clock settings","24시간 표시는 세계 시계 설정과 연동됩니다"); put("現在のタイムゾーン","Current time zone","현재 시간대"); put("ウィジェット","Widgets","위젯"); put("世界時計ウィジェットを追加","Add world clock widget","세계 시계 위젯 추가"); put("次のアラームウィジェットを追加","Add next alarm widget","다음 알람 위젯 추가"); put("次のアラーム","Next alarm","다음 알람"); put("アラームなし","No alarms","알람 없음"); put("今日","Today","오늘"); put("明日","Tomorrow","내일"); put("追加画面を開きました","Opened widget add screen","위젯 추가 화면을 열었습니다"); put("このホーム画面では直接追加できません","This launcher cannot add it directly","이 홈 화면에서는 직접 추가할 수 없습니다"); put("ホーム画面を長押し → ウィジェット → WakeGuard から追加してください","Long-press the Home screen → Widgets → WakeGuard","홈 화면 길게 누르기 → 위젯 → WakeGuard에서 추가하세요");'''
if needle in s and 'put("世界時計ウィジェット"' not in s:s=s.replace(needle,needle+extra,1)
p.write_text(s,encoding='utf-8')

# validations
for f in [j/'WorldClockWidgetProvider.java',j/'WorldClockWidgetConfigActivity.java',j/'NextAlarmWidgetProvider.java',res/'layout/widget_world_clock.xml',res/'xml/world_clock_widget_info.xml']:
    if not f.exists(): raise SystemExit('missing '+str(f))
print('WakeGuard v1.5.4 home/lock-screen widgets applied')
