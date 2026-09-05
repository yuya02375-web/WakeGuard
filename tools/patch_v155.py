from pathlib import Path
import re, runpy

runpy.run_path('tools/patch_v154.py', run_name='__main__')
app=Path('WakeGuard/app')
j=app/'src/main/java/jp/wakeguard/alarm'
res=app/'src/main/res'

# v1.5.5
p=app/'build.gradle.kts'; s=p.read_text(encoding='utf-8')
s=re.sub(r'versionCode = \d+','versionCode = 66',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.5.5"',s)
p.write_text(s,encoding='utf-8')

world_provider=r'''package jp.wakeguard.alarm;

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

    static void saveZone(Context c,int appWidgetId,String zoneId){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_ZONE+appWidgetId,zoneId).apply();
    }
    static void saveStyle(Context c,int appWidgetId,String style){
        if(!"analog".equals(style))style="digital";
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_STYLE+appWidgetId,style).apply();
    }
    static void saveConfig(Context c,int appWidgetId,String zoneId,String style){
        if(!"analog".equals(style))style="digital";
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString(KEY_ZONE+appWidgetId,zoneId)
                .putString(KEY_STYLE+appWidgetId,style).apply();
    }

    static String loadZone(Context c,int appWidgetId){
        String z=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_ZONE+appWidgetId,"");
        if(z==null||z.isEmpty())z=ZoneId.systemDefault().getId();
        try{ZoneId.of(z);}catch(Throwable t){z=ZoneId.systemDefault().getId();}
        return z;
    }
    static String loadStyle(Context c,int appWidgetId){
        String x=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_STYLE+appWidgetId,"digital");
        return "analog".equals(x)?"analog":"digital";
    }

    static String zoneLabel(Context c,String zoneId){
        try{
            String x=android.icu.text.TimeZoneNames.getInstance(I18n.locale(c)).getExemplarLocationName(zoneId);
            if(x!=null&&!x.trim().isEmpty())return x.trim();
        }catch(Throwable ignored){}
        String last=zoneId.contains("/")?zoneId.substring(zoneId.lastIndexOf('/')+1):zoneId;
        return last.replace('_',' ');
    }

    private static int width(AppWidgetManager m,int id){
        try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180));}
        catch(Throwable t){return 180;}
    }
    private static int height(AppWidgetManager m,int id){
        try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110));}
        catch(Throwable t){return 110;}
    }
    private static int chooseLayout(boolean analog,int w,int h){
        boolean tall=h>w*1.22f;
        boolean wide=w>h*1.28f;
        if(analog){
            if(tall)return R.layout.widget_world_clock_analog_tall;
            if(wide)return R.layout.widget_world_clock_analog_wide;
            return R.layout.widget_world_clock_analog_square;
        }
        if(tall)return R.layout.widget_world_clock_digital_tall;
        if(wide)return R.layout.widget_world_clock_digital_wide;
        return R.layout.widget_world_clock_digital_square;
    }

    static void updateAppWidget(Context c,AppWidgetManager manager,int appWidgetId){
        String zone=loadZone(c,appWidgetId);
        String style=loadStyle(c,appWidgetId);
        boolean analog="analog".equals(style) && Build.VERSION.SDK_INT>=31;
        boolean h24=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE).getBoolean("use_24h",true);
        RemoteViews rv=new RemoteViews(c.getPackageName(),chooseLayout(analog,width(manager,appWidgetId),height(manager,appWidgetId)));
        rv.setTextViewText(R.id.widget_city,zoneLabel(c,zone));
        rv.setTextViewText(R.id.widget_zone,zone);
        rv.setString(R.id.widget_date,"setTimeZone",zone);
        rv.setCharSequence(R.id.widget_date,"setFormat24Hour","M/d EEE");
        rv.setCharSequence(R.id.widget_date,"setFormat12Hour","M/d EEE");
        if(analog){
            rv.setString(R.id.widget_analog,"setTimeZone",zone);
        }else{
            rv.setString(R.id.widget_time,"setTimeZone",zone);
            String timeFmt=h24?"HH:mm":"hh:mm a";
            rv.setCharSequence(R.id.widget_time,"setFormat24Hour",timeFmt);
            rv.setCharSequence(R.id.widget_time,"setFormat12Hour",timeFmt);
        }

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
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){updateAppWidget(c,m,id);}
    @Override public void onDeleted(Context c,int[] ids){
        android.content.SharedPreferences.Editor e=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();
        for(int id:ids){e.remove(KEY_ZONE+id);e.remove(KEY_STYLE+id);}e.apply();
    }
}
'''
(j/'WorldClockWidgetProvider.java').write_text(world_provider,encoding='utf-8')

config=r'''package jp.wakeguard.alarm;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;

public class WorldClockWidgetConfigActivity extends Activity {
    private int appWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private final ArrayList<String> ordered=new ArrayList<>(),visible=new ArrayList<>();
    private BaseAdapter adapter;
    private String selectedZone;
    private String selectedStyle="digital";
    private Button digitalButton,analogButton;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);Ui.prepareActivity(this);setResult(RESULT_CANCELED);
        appWidgetId=getIntent()==null?AppWidgetManager.INVALID_APPWIDGET_ID:getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(appWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        selectedZone=WorldClockWidgetProvider.loadZone(this,appWidgetId);
        selectedStyle=WorldClockWidgetProvider.loadStyle(this,appWidgetId);
        if(Build.VERSION.SDK_INT<31)selectedStyle="digital";
        buildZoneList();buildUi();
    }

    private void buildZoneList(){
        LinkedHashSet<String> first=new LinkedHashSet<>();
        first.add(selectedZone);first.add(ZoneId.systemDefault().getId());
        String raw=getSharedPreferences("clock_tools",MODE_PRIVATE).getString("world_zones","");
        if(raw!=null)for(String z:raw.split("\\n"))if(valid(z))first.add(z);
        for(String z:new String[]{"Asia/Tokyo","Asia/Seoul","America/New_York","America/Los_Angeles","Europe/London","Europe/Paris","Asia/Singapore","Australia/Sydney"})if(valid(z))first.add(z);
        ArrayList<String> rest=new ArrayList<>();
        for(String z:ZoneId.getAvailableZoneIds())if(z.contains("/")&&!z.startsWith("Etc/")&&!first.contains(z))rest.add(z);
        Collections.sort(rest);ordered.addAll(first);ordered.addAll(rest);visible.addAll(ordered);
    }
    private boolean valid(String z){if(z==null||z.isEmpty())return false;try{ZoneId.of(z);return true;}catch(Throwable t){return false;}}
    private String norm(String x){return x==null?"":x.toLowerCase(Locale.ROOT).replace('_',' ').trim();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);root.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,18));
        TextView title=Ui.title(this,"世界時計ウィジェット",26);root.addView(title);
        TextView sizeNote=Ui.text(this,"正方形・縦長・横長にサイズ変更できます",12,Ui.MUTED);root.addView(sizeNote,Ui.gapTop(this,4));

        TextView styleLabel=Ui.text(this,I18n.tr(this,"表示")+" · "+I18n.tr(this,"デジタル / アナログ"),13,Ui.MUTED);styleLabel.setTypeface(null,Typeface.BOLD);root.addView(styleLabel,Ui.gapTop(this,14));
        LinearLayout styles=new LinearLayout(this);styles.setOrientation(LinearLayout.HORIZONTAL);
        digitalButton=Ui.button(this,"デジタル",false);analogButton=Ui.button(this,"アナログ",false);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);styles.addView(digitalButton,bp);LinearLayout.LayoutParams bp2=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);bp2.setMarginStart(Ui.dp(this,8));styles.addView(analogButton,bp2);root.addView(styles,Ui.gapTop(this,6));
        digitalButton.setOnClickListener(v->{selectedStyle="digital";refreshStyleButtons();});
        analogButton.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=31){selectedStyle="analog";refreshStyleButtons();}});
        if(Build.VERSION.SDK_INT<31){analogButton.setEnabled(false);analogButton.setAlpha(.45f);}
        refreshStyleButtons();

        TextView sub=Ui.text(this,"表示するタイムゾーンを選択",14,Ui.MUTED);root.addView(sub,Ui.gapTop(this,14));
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED_2);search.setHint(I18n.tr(this,"タイムゾーンを検索"));search.setTextSize(16);search.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));search.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);root.addView(search,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));

        ListView list=new ListView(this);list.setDividerHeight(0);list.setBackgroundColor(Ui.BG);list.setClipToPadding(false);list.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));
        adapter=new BaseAdapter(){
            @Override public int getCount(){return visible.size();}
            @Override public Object getItem(int pos){return visible.get(pos);}
            @Override public long getItemId(int pos){return pos;}
            @Override public View getView(int pos,View convert,ViewGroup parent){
                LinearLayout row;TextView city,zone;
                if(convert instanceof LinearLayout&&convert.getTag() instanceof TextView[]){row=(LinearLayout)convert;TextView[] h=(TextView[])row.getTag();city=h[0];zone=h[1];}
                else{row=new LinearLayout(WorldClockWidgetConfigActivity.this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(WorldClockWidgetConfigActivity.this,14),Ui.dp(WorldClockWidgetConfigActivity.this,10),Ui.dp(WorldClockWidgetConfigActivity.this,14),Ui.dp(WorldClockWidgetConfigActivity.this,10));city=Ui.text(WorldClockWidgetConfigActivity.this,"",16,Ui.TEXT);city.setTypeface(null,Typeface.BOLD);zone=Ui.text(WorldClockWidgetConfigActivity.this,"",11,Ui.MUTED);row.addView(city);row.addView(zone,Ui.gapTop(WorldClockWidgetConfigActivity.this,2));row.setTag(new TextView[]{city,zone});}
                String id=visible.get(pos);boolean selected=id.equals(selectedZone);String label=WorldClockWidgetProvider.zoneLabel(WorldClockWidgetConfigActivity.this,id);if(id.equals(ZoneId.systemDefault().getId()))label=I18n.tr(WorldClockWidgetConfigActivity.this,"現在のタイムゾーン")+" · "+label;city.setText((selected?"✓  ":"")+label);zone.setText(id);row.setBackground(Ui.roundStroke(Ui.SURFACE,selected?Ui.ACCENT:Ui.BORDER,14,WorldClockWidgetConfigActivity.this));
                android.widget.AbsListView.LayoutParams lp=new android.widget.AbsListView.LayoutParams(-1,Ui.dp(WorldClockWidgetConfigActivity.this,66));row.setLayoutParams(lp);return row;
            }
        };
        list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,pos,id)->{selectedZone=visible.get(pos);adapter.notifyDataSetChanged();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter(s==null?"":s.toString());}public void afterTextChanged(Editable e){}});

        Button save=Ui.button(this,"保存",true);save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));
        setContentView(root);Ui.applySystemBarInsets(this,root);
    }

    private void refreshStyleButtons(){
        boolean d=!"analog".equals(selectedStyle);
        digitalButton.setBackground(d?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));
        digitalButton.setTextColor(d?0xFF0D1B2A:Ui.TEXT);
        boolean a=!d;
        analogButton.setBackground(a?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));
        analogButton.setTextColor(a?0xFF0D1B2A:Ui.TEXT);
    }
    private void filter(String q){String n=norm(q);visible.clear();if(n.isEmpty())visible.addAll(ordered);else for(String z:ordered){String label=WorldClockWidgetProvider.zoneLabel(this,z);if(norm(z).contains(n)||norm(label).contains(n))visible.add(z);}adapter.notifyDataSetChanged();}
    private void save(){
        if(!valid(selectedZone))selectedZone=ZoneId.systemDefault().getId();
        WorldClockWidgetProvider.saveConfig(this,appWidgetId,selectedZone,selectedStyle);
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
'''
(j/'NextAlarmWidgetProvider.java').write_text(next_provider,encoding='utf-8')

(res/'layout').mkdir(parents=True,exist_ok=True)
(res/'xml').mkdir(parents=True,exist_ok=True)

layouts={
'widget_world_clock_digital_wide.xml':r'''<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center_vertical" android:background="@drawable/widget_bg"><TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#FFFFFFFF" android:textSize="13sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end"/><TextClock android:id="@+id/widget_time" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#FFFFFFFF" android:textSize="34sp" android:textStyle="bold" android:fontFamily="monospace"/><LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal"><TextClock android:id="@+id/widget_date" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textColor="#CCFFFFFF" android:textSize="11sp"/><TextView android:id="@+id/widget_zone" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:layout_marginStart="8dp" android:textColor="#99FFFFFF" android:textSize="10sp" android:maxLines="1" android:ellipsize="end"/></LinearLayout></LinearLayout>''',
'widget_world_clock_digital_square.xml':r'''<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:background="@drawable/widget_bg"><TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="14sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end"/><TextClock android:id="@+id/widget_time" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="32sp" android:textStyle="bold" android:fontFamily="monospace"/><TextClock android:id="@+id/widget_date" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#CCFFFFFF" android:textSize="11sp"/><TextView android:id="@+id/widget_zone" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#99FFFFFF" android:textSize="9sp" android:maxLines="1" android:ellipsize="end"/></LinearLayout>''',
'widget_world_clock_digital_tall.xml':r'''<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:background="@drawable/widget_bg"><TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="15sp" android:textStyle="bold" android:maxLines="2"/><TextClock android:id="@+id/widget_time" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="38sp" android:textStyle="bold" android:fontFamily="monospace"/><TextClock android:id="@+id/widget_date" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#CCFFFFFF" android:textSize="12sp"/><TextView android:id="@+id/widget_zone" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#99FFFFFF" android:textSize="10sp" android:maxLines="2"/></LinearLayout>''',
'widget_world_clock_analog_wide.xml':r'''<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="horizontal" android:gravity="center" android:background="@drawable/widget_bg"><AnalogClock android:id="@+id/widget_analog" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="0.45" android:dialTint="#FFE8EAED" android:hand_hourTint="#FFFFFFFF" android:hand_minuteTint="#FFFFFFFF" android:hand_secondTint="#FF8AB4F8"/><LinearLayout android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="0.55" android:orientation="vertical" android:gravity="center_vertical" android:paddingStart="8dp"><TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#FFFFFFFF" android:textSize="15sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end"/><TextClock android:id="@+id/widget_date" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#CCFFFFFF" android:textSize="11sp"/><TextView android:id="@+id/widget_zone" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#99FFFFFF" android:textSize="9sp" android:maxLines="2"/></LinearLayout></LinearLayout>''',
'widget_world_clock_analog_square.xml':r'''<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:background="@drawable/widget_bg"><TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="14sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end"/><AnalogClock android:id="@+id/widget_analog" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:dialTint="#FFE8EAED" android:hand_hourTint="#FFFFFFFF" android:hand_minuteTint="#FFFFFFFF" android:hand_secondTint="#FF8AB4F8"/><TextClock android:id="@+id/widget_date" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#CCFFFFFF" android:textSize="11sp"/><TextView android:id="@+id/widget_zone" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#99FFFFFF" android:textSize="9sp" android:maxLines="1" android:ellipsize="end"/></LinearLayout>''',
'widget_world_clock_analog_tall.xml':r'''<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:background="@drawable/widget_bg"><TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="15sp" android:textStyle="bold" android:maxLines="2"/><AnalogClock android:id="@+id/widget_analog" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:dialTint="#FFE8EAED" android:hand_hourTint="#FFFFFFFF" android:hand_minuteTint="#FFFFFFFF" android:hand_secondTint="#FF8AB4F8"/><TextClock android:id="@+id/widget_date" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#CCFFFFFF" android:textSize="12sp"/><TextView android:id="@+id/widget_zone" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#99FFFFFF" android:textSize="10sp" android:maxLines="2"/></LinearLayout>''',
'widget_next_alarm_wide.xml':r'''<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/next_alarm_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center_vertical" android:background="@drawable/widget_bg"><TextView android:id="@+id/next_alarm_label" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#CCFFFFFF" android:textSize="13sp" android:textStyle="bold"/><TextView android:id="@+id/next_alarm_time" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#FFFFFFFF" android:textSize="34sp" android:textStyle="bold" android:fontFamily="monospace"/><TextView android:id="@+id/next_alarm_detail" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#B3FFFFFF" android:textSize="11sp"/></LinearLayout>''',
'widget_next_alarm_square.xml':r'''<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/next_alarm_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:background="@drawable/widget_bg"><TextView android:id="@+id/next_alarm_label" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#CCFFFFFF" android:textSize="12sp" android:textStyle="bold"/><TextView android:id="@+id/next_alarm_time" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="32sp" android:textStyle="bold" android:fontFamily="monospace"/><TextView android:id="@+id/next_alarm_detail" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#B3FFFFFF" android:textSize="11sp"/></LinearLayout>''',
'widget_next_alarm_tall.xml':r'''<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/next_alarm_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:background="@drawable/widget_bg"><TextView android:id="@+id/next_alarm_label" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#CCFFFFFF" android:textSize="13sp" android:textStyle="bold"/><TextView android:id="@+id/next_alarm_time" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="36sp" android:textStyle="bold" android:fontFamily="monospace"/><TextView android:id="@+id/next_alarm_detail" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#B3FFFFFF" android:textSize="12sp"/></LinearLayout>'''
}
for name,text in layouts.items():(res/'layout'/name).write_text(text,encoding='utf-8')

# Keep legacy layout names as valid previews while using responsive variants at runtime.
(res/'layout/widget_world_clock.xml').write_text(layouts['widget_world_clock_digital_square.xml'],encoding='utf-8')
(res/'layout/widget_next_alarm.xml').write_text(layouts['widget_next_alarm_square.xml'],encoding='utf-8')

(res/'xml/world_clock_widget_info.xml').write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="60dp" android:minHeight="60dp" android:minResizeWidth="60dp" android:minResizeHeight="60dp"
    android:targetCellWidth="2" android:targetCellHeight="2" android:maxResizeWidth="420dp" android:maxResizeHeight="480dp"
    android:updatePeriodMillis="0" android:initialLayout="@layout/widget_world_clock" android:previewLayout="@layout/widget_world_clock"
    android:configure="jp.wakeguard.alarm.WorldClockWidgetConfigActivity" android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen|keyguard" android:widgetFeatures="reconfigurable" />
''',encoding='utf-8')
(res/'xml/next_alarm_widget_info.xml').write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="60dp" android:minHeight="60dp" android:minResizeWidth="60dp" android:minResizeHeight="60dp"
    android:targetCellWidth="2" android:targetCellHeight="2" android:maxResizeWidth="420dp" android:maxResizeHeight="480dp"
    android:updatePeriodMillis="1800000" android:initialLayout="@layout/widget_next_alarm" android:previewLayout="@layout/widget_next_alarm"
    android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen|keyguard" />
''',encoding='utf-8')

# Add one new localized explanatory string.
p=j/'I18n.java'; s=p.read_text(encoding='utf-8')
needle='put("世界時計ウィジェット","World clock widget","세계 시계 위젯");'
addition=' put("正方形・縦長・横長にサイズ変更できます","Resize it as square, tall, or wide","정사각형·세로형·가로형으로 크기를 바꿀 수 있습니다");'
if needle in s and '正方形・縦長・横長にサイズ変更できます' not in s:s=s.replace(needle,needle+addition,1)
p.write_text(s,encoding='utf-8')

# Validation
checks=[
    (j/'WorldClockWidgetProvider.java','widget_world_clock_analog_square'),
    (j/'WorldClockWidgetProvider.java','OPTION_APPWIDGET_MIN_WIDTH'),
    (j/'WorldClockWidgetConfigActivity.java','selectedStyle="analog"'),
    (res/'layout/widget_world_clock_analog_square.xml','<AnalogClock'),
    (res/'layout/widget_world_clock_digital_tall.xml','widget_time'),
    (res/'xml/world_clock_widget_info.xml','android:minResizeWidth="60dp"'),
    (res/'xml/world_clock_widget_info.xml','android:resizeMode="horizontal|vertical"'),
    (j/'NextAlarmWidgetProvider.java','widget_next_alarm_tall')
]
for path,needle in checks:
    if needle not in path.read_text(encoding='utf-8'):raise SystemExit('validation missing: '+str(path)+' :: '+needle)
print('WakeGuard v1.5.5 responsive square/tall/wide + digital/analog widgets applied')
