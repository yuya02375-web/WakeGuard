from pathlib import Path
import re

ROOT=Path('WakeGuard')
JAVA=ROOT/'app/src/main/java/jp/wakeguard/alarm'
XML=ROOT/'app/src/main/res/xml'
MANIFEST=ROOT/'app/src/main/AndroidManifest.xml'
GRADLE=ROOT/'app/build.gradle.kts'

# Hide the 39 size-only variants from the launcher picker. They remain valid
# AppWidget providers so WakeGuard can pin a chosen size programmatically.
BASE={
'world_clock_widget_info.xml','local_clock_widget_info.xml','multi_world_clock_widget_info.xml',
'next_alarm_widget_info.xml','alarm_countdown_widget_info.xml','alarm_list_widget_info.xml',
'timer_widget_info.xml','timer_preset_widget_info.xml','stopwatch_widget_info.xml',
'streak_widget_info.xml','wake_stats_widget_info.xml','quick_tools_widget_info.xml','today_widget_info.xml'
}
for p in XML.glob('*_widget_info.xml'):
    if p.name in BASE: continue
    text=p.read_text(encoding='utf-8')
    m=re.search(r'android:widgetFeatures="([^"]*)"',text)
    if m:
        vals=[x for x in m.group(1).split('|') if x]
        if 'hide_from_picker' not in vals: vals.append('hide_from_picker')
        text=text[:m.start(1)]+'|'.join(vals)+text[m.end(1):]
    else:
        text=text.replace('/>','    android:widgetFeatures="hide_from_picker" />',1)
    p.write_text(text,encoding='utf-8')

# Reliable callback after requestPinAppWidget. This is especially important for
# world clocks because requestPinAppWidget does not launch the configure Activity.
(JAVA/'WidgetPinResultReceiver.java').write_text(r'''package jp.wakeguard.alarm;

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
''',encoding='utf-8')

# Make WorldClockWidgetConfigActivity work in two modes:
# 1. normal launcher configuration with an appWidgetId
# 2. WakeGuard pre-configuration, then requestPinAppWidget for the chosen size.
p=JAVA/'WorldClockWidgetConfigActivity.java'
s=p.read_text(encoding='utf-8')
s=s.replace('import android.content.Intent;','import android.content.Intent;\nimport android.content.ComponentName;\nimport android.app.PendingIntent;')
s=s.replace('import android.widget.TextView;','import android.widget.TextView;\nimport android.widget.Toast;')
s=s.replace('private int appWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID;','private int appWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID;\n    private String pinProviderClass=null;')
old='''appWidgetId=getIntent()==null?AppWidgetManager.INVALID_APPWIDGET_ID:getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);\n        if(appWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}\n        selectedZone=WorldClockWidgetProvider.loadZone(this,appWidgetId);\n        selectedLabel=WorldClockWidgetProvider.loadLabel(this,appWidgetId);\n        selectedStyle=WorldClockWidgetProvider.loadStyle(this,appWidgetId);'''
new='''appWidgetId=getIntent()==null?AppWidgetManager.INVALID_APPWIDGET_ID:getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);\n        pinProviderClass=getIntent()==null?null:getIntent().getStringExtra("pin_provider_class");\n        if(appWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID&&(pinProviderClass==null||pinProviderClass.isEmpty())){finish();return;}\n        if(appWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID){\n            selectedZone=java.time.ZoneId.systemDefault().getId();\n            selectedLabel=WorldClockWidgetProvider.zoneLabel(this,selectedZone);\n            selectedStyle="digital";\n        }else{\n            selectedZone=WorldClockWidgetProvider.loadZone(this,appWidgetId);\n            selectedLabel=WorldClockWidgetProvider.loadLabel(this,appWidgetId);\n            selectedStyle=WorldClockWidgetProvider.loadStyle(this,appWidgetId);\n        }'''
if old not in s: raise SystemExit('WorldClock config onCreate anchor missing')
s=s.replace(old,new)
oldsave='''private void save(){try{ZoneId.of(selectedZone);}catch(Throwable t){selectedZone=ZoneId.systemDefault().getId();selectedLabel=WorldClockWidgetProvider.zoneLabel(this,selectedZone);}WorldClockWidgetProvider.saveConfig(this,appWidgetId,selectedZone,selectedStyle,selectedLabel);WorldClockWidgetProvider.updateAppWidget(this,AppWidgetManager.getInstance(this),appWidgetId);Intent result=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,appWidgetId);setResult(RESULT_OK,result);finish();}'''
newsave='''private void save(){\n        try{ZoneId.of(selectedZone);}catch(Throwable t){selectedZone=ZoneId.systemDefault().getId();selectedLabel=WorldClockWidgetProvider.zoneLabel(this,selectedZone);}\n        if(appWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID&&pinProviderClass!=null){pinConfiguredWorld();return;}\n        WorldClockWidgetProvider.saveConfig(this,appWidgetId,selectedZone,selectedStyle,selectedLabel);WorldClockWidgetProvider.updateAppWidget(this,AppWidgetManager.getInstance(this),appWidgetId);Intent result=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,appWidgetId);setResult(RESULT_OK,result);finish();\n    }\n    private void pinConfiguredWorld(){\n        try{\n            if(Build.VERSION.SDK_INT<26){Toast.makeText(this,I18n.tr(this,"ホーム画面のウィジェット一覧から追加してください"),Toast.LENGTH_LONG).show();return;}\n            Class<?> cls=Class.forName(pinProviderClass);\n            AppWidgetManager m=AppWidgetManager.getInstance(this);\n            if(!m.isRequestPinAppWidgetSupported()){Toast.makeText(this,I18n.tr(this,"このホーム画面ではアプリから直接追加できません。ホーム画面を長押し → ウィジェット → WakeGuard から追加してください"),Toast.LENGTH_LONG).show();return;}\n            Intent done=new Intent(this,WidgetPinResultReceiver.class).setAction("jp.wakeguard.WIDGET_PIN_RESULT").putExtra("kind","world").putExtra("zone",selectedZone).putExtra("style",selectedStyle).putExtra("label",selectedLabel);\n            int req=(int)(System.nanoTime()&0x7fffffff);\n            PendingIntent cb=PendingIntent.getBroadcast(this,req,done,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);\n            boolean ok=m.requestPinAppWidget(new ComponentName(this,cls),null,cb);\n            if(ok){Toast.makeText(this,I18n.tr(this,"ホーム画面に追加してください"),Toast.LENGTH_SHORT).show();finish();}\n            else Toast.makeText(this,I18n.tr(this,"このホーム画面では直接追加できません"),Toast.LENGTH_LONG).show();\n        }catch(Throwable t){Toast.makeText(this,I18n.tr(this,"ウィジェット追加に失敗しました"),Toast.LENGTH_LONG).show();}\n    }'''
if oldsave not in s: raise SystemExit('WorldClock config save anchor missing')
s=s.replace(oldsave,newsave)
p.write_text(s,encoding='utf-8')

# Replace the single pin button behavior with a 4-size chooser for every widget.
p=JAVA/'ClockSettingsActivity.java'
s=p.read_text(encoding='utf-8')
s=s.replace('private void addWidgetButton(String label,Class<?> provider){Button b=Ui.button(this,label,false);b.setOnClickListener(v->pinWidget(provider,label));body.addView(b,Ui.gapTop(this,8));}\n    private void pinWidget(Class<?> provider,String name){try{if(android.os.Build.VERSION.SDK_INT>=26){android.appwidget.AppWidgetManager m=android.appwidget.AppWidgetManager.getInstance(this);if(m.isRequestPinAppWidgetSupported()){boolean ok=m.requestPinAppWidget(new ComponentName(this,provider),null,null);Toast.makeText(this,I18n.tr(this,ok?"追加画面を開きました":"このホーム画面では直接追加できません"),Toast.LENGTH_SHORT).show();return;}}}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"ホーム画面を長押し → ウィジェット → WakeGuard から追加してください"),Toast.LENGTH_LONG).show();}',r'''private void addWidgetButton(String label,Class<?> provider){Button b=Ui.button(this,label,false);b.setOnClickListener(v->showSizePicker(provider,label));body.addView(b,Ui.gapTop(this,8));}
    private void showSizePicker(Class<?> provider,String name){
        Class<?>[] v=variants(provider);String[] labels=sizeLabels(provider);
        new AlertDialog.Builder(this).setTitle(I18n.tr(this,name)+" ・ "+I18n.tr(this,"サイズ")).setItems(labels,(d,which)->{
            Class<?> chosen=v[Math.max(0,Math.min(which,v.length-1))];
            if(provider==WorldClockWidgetProvider.class){Intent i=new Intent(this,WorldClockWidgetConfigActivity.class).putExtra("pin_provider_class",chosen.getName());startActivity(i);}
            else pinWidget(chosen,name);
        }).setNegativeButton(I18n.tr(this,"キャンセル"),null).show();
    }
    private String[] sizeLabels(Class<?> p){
        if(p==WorldClockWidgetProvider.class||p==LocalClockWidgetProvider.class||p==StreakWidgetProvider.class)return new String[]{"2×1  コンパクト","2×2  正方形","4×2  横長","2×3  縦長"};
        if(p==NextAlarmWidgetProvider.class||p==AlarmCountdownWidgetProvider.class)return new String[]{"2×1  コンパクト","2×2  正方形","4×1  横長","2×3  縦長"};
        if(p==TimerWidgetProvider.class||p==StopwatchWidgetProvider.class)return new String[]{"2×1  コンパクト","3×1  標準","2×2  正方形","4×1  横長"};
        return new String[]{"3×2  コンパクト","4×2  横長","3×3  縦・大","4×3  大"};
    }
    private Class<?>[] variants(Class<?> p){
        if(p==WorldClockWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.WorldClockCompactWidgetProvider.class,WorldClockWidgetProvider.class,WidgetVariantProviders.WorldClockWideWidgetProvider.class,WidgetVariantProviders.WorldClockTallWidgetProvider.class};
        if(p==LocalClockWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.LocalClockCompactWidgetProvider.class,LocalClockWidgetProvider.class,WidgetVariantProviders.LocalClockWideWidgetProvider.class,WidgetVariantProviders.LocalClockTallWidgetProvider.class};
        if(p==MultiWorldClockWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.MultiWorldClockCompactWidgetProvider.class,MultiWorldClockWidgetProvider.class,WidgetVariantProviders.MultiWorldClockTallWidgetProvider.class,WidgetVariantProviders.MultiWorldClockLargeWidgetProvider.class};
        if(p==NextAlarmWidgetProvider.class)return new Class<?>[]{NextAlarmWidgetProvider.class,WidgetVariantProviders.NextAlarmSquareWidgetProvider.class,WidgetVariantProviders.NextAlarmWideWidgetProvider.class,WidgetVariantProviders.NextAlarmTallWidgetProvider.class};
        if(p==AlarmCountdownWidgetProvider.class)return new Class<?>[]{AlarmCountdownWidgetProvider.class,WidgetVariantProviders.AlarmCountdownSquareWidgetProvider.class,WidgetVariantProviders.AlarmCountdownWideWidgetProvider.class,WidgetVariantProviders.AlarmCountdownTallWidgetProvider.class};
        if(p==AlarmListWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.AlarmListCompactWidgetProvider.class,AlarmListWidgetProvider.class,WidgetVariantProviders.AlarmListTallWidgetProvider.class,WidgetVariantProviders.AlarmListLargeWidgetProvider.class};
        if(p==TimerWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.TimerCompactWidgetProvider.class,TimerWidgetProvider.class,WidgetVariantProviders.TimerSquareWidgetProvider.class,WidgetVariantProviders.TimerWideWidgetProvider.class};
        if(p==TimerPresetWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.TimerPresetCompactWidgetProvider.class,TimerPresetWidgetProvider.class,WidgetVariantProviders.TimerPresetTallWidgetProvider.class,WidgetVariantProviders.TimerPresetLargeWidgetProvider.class};
        if(p==StopwatchWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.StopwatchCompactWidgetProvider.class,StopwatchWidgetProvider.class,WidgetVariantProviders.StopwatchSquareWidgetProvider.class,WidgetVariantProviders.StopwatchWideWidgetProvider.class};
        if(p==StreakWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.StreakCompactWidgetProvider.class,StreakWidgetProvider.class,WidgetVariantProviders.StreakWideWidgetProvider.class,WidgetVariantProviders.StreakTallWidgetProvider.class};
        if(p==WakeStatsWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.WakeStatsCompactWidgetProvider.class,WakeStatsWidgetProvider.class,WidgetVariantProviders.WakeStatsTallWidgetProvider.class,WidgetVariantProviders.WakeStatsLargeWidgetProvider.class};
        if(p==QuickToolsWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.QuickToolsCompactWidgetProvider.class,QuickToolsWidgetProvider.class,WidgetVariantProviders.QuickToolsTallWidgetProvider.class,WidgetVariantProviders.QuickToolsLargeWidgetProvider.class};
        if(p==TodayWidgetProvider.class)return new Class<?>[]{WidgetVariantProviders.TodayCompactWidgetProvider.class,TodayWidgetProvider.class,WidgetVariantProviders.TodayTallWidgetProvider.class,WidgetVariantProviders.TodayLargeWidgetProvider.class};
        return new Class<?>[]{p,p,p,p};
    }
    private void pinWidget(Class<?> provider,String name){try{if(android.os.Build.VERSION.SDK_INT>=26){android.appwidget.AppWidgetManager m=android.appwidget.AppWidgetManager.getInstance(this);if(m.isRequestPinAppWidgetSupported()){boolean ok=m.requestPinAppWidget(new ComponentName(this,provider),null,null);Toast.makeText(this,I18n.tr(this,ok?"ホーム画面に追加してください":"このホーム画面では直接追加できません"),Toast.LENGTH_SHORT).show();return;}}}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"ホーム画面を長押し → ウィジェット → WakeGuard から追加してください"),Toast.LENGTH_LONG).show();}''')
if 'showSizePicker' not in s: raise SystemExit('ClockSettings pin flow anchor missing')
p.write_text(s,encoding='utf-8')

# Register callback receiver.
s=MANIFEST.read_text(encoding='utf-8')
anchor='<receiver android:name=".WidgetActionReceiver" android:exported="false" />'
if anchor not in s: raise SystemExit('manifest receiver anchor missing')
s=s.replace(anchor,'<receiver android:name=".WidgetPinResultReceiver" android:exported="false" />\n        '+anchor,1)
MANIFEST.write_text(s,encoding='utf-8')

s=GRADLE.read_text(encoding='utf-8')
s=re.sub(r'versionCode\s*=\s*\d+','versionCode = 72',s,count=1)
s=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "1.6.1"',s,count=1)
GRADLE.write_text(s,encoding='utf-8')

print('WakeGuard v1.6.1 widget add flow fixed')
