from pathlib import Path
import re

ROOT = Path('WakeGuard')
JAVA = ROOT/'app/src/main/java/jp/wakeguard/alarm'
LAYOUT = ROOT/'app/src/main/res/layout'
XML = ROOT/'app/src/main/res/xml'
MANIFEST = ROOT/'app/src/main/AndroidManifest.xml'
GRADLE = ROOT/'app/build.gradle.kts'

# -----------------------------------------------------------------------------
# World clock: digital / analog / both
# -----------------------------------------------------------------------------
provider = (JAVA/'WorldClockWidgetProvider.java').read_text(encoding='utf-8')
provider = provider.replace('static void saveStyle(Context c,int appWidgetId,String style){if(!"analog".equals(style))style="digital";c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_STYLE+appWidgetId,style).apply();}',
'''static void saveStyle(Context c,int appWidgetId,String style){if(!"analog".equals(style)&&!"both".equals(style))style="digital";c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_STYLE+appWidgetId,style).apply();}''')
provider = provider.replace('if(!"analog".equals(style))style="digital";if(label==null||label.trim().isEmpty())label=zoneLabel(c,zoneId);',
                            'if(!"analog".equals(style)&&!"both".equals(style))style="digital";if(label==null||label.trim().isEmpty())label=zoneLabel(c,zoneId);')
provider = provider.replace('static String loadStyle(Context c,int appWidgetId){String x=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_STYLE+appWidgetId,"digital");return "analog".equals(x)?"analog":"digital";}',
'''static String loadStyle(Context c,int appWidgetId){String x=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_STYLE+appWidgetId,"digital");return "analog".equals(x)?"analog":"both".equals(x)?"both":"digital";}''')
old_choose = 'private static int chooseLayout(boolean analog,int w,int h){boolean tall=h>w*1.22f,wide=w>h*1.28f;if(analog){if(tall)return R.layout.widget_world_clock_analog_tall;if(wide)return R.layout.widget_world_clock_analog_wide;return R.layout.widget_world_clock_analog_square;}if(tall)return R.layout.widget_world_clock_digital_tall;if(wide)return R.layout.widget_world_clock_digital_wide;return R.layout.widget_world_clock_digital_square;}'
new_choose = '''private static int chooseLayout(String style,int w,int h){boolean tall=h>w*1.22f,wide=w>h*1.28f;if("both".equals(style)){if(tall)return R.layout.widget_world_clock_both_tall;if(wide)return R.layout.widget_world_clock_both_wide;return R.layout.widget_world_clock_both_square;}if("analog".equals(style)){if(tall)return R.layout.widget_world_clock_analog_tall;if(wide)return R.layout.widget_world_clock_analog_wide;return R.layout.widget_world_clock_analog_square;}if(tall)return R.layout.widget_world_clock_digital_tall;if(wide)return R.layout.widget_world_clock_digital_wide;return R.layout.widget_world_clock_digital_square;}'''
if old_choose not in provider: raise SystemExit('world chooseLayout anchor not found')
provider = provider.replace(old_choose,new_choose)
old_update = 'String zone=loadZone(c,appWidgetId),style=loadStyle(c,appWidgetId),label=loadLabel(c,appWidgetId);boolean analog="analog".equals(style)&&Build.VERSION.SDK_INT>=31;boolean h24=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE).getBoolean("use_24h",true);\n        RemoteViews rv=new RemoteViews(c.getPackageName(),chooseLayout(analog,width(manager,appWidgetId),height(manager,appWidgetId)));rv.setTextViewText(R.id.widget_city,label);rv.setTextViewText(R.id.widget_zone,zone);rv.setString(R.id.widget_date,"setTimeZone",zone);rv.setCharSequence(R.id.widget_date,"setFormat24Hour","M/d EEE");rv.setCharSequence(R.id.widget_date,"setFormat12Hour","M/d EEE");\n        if(analog)rv.setString(R.id.widget_analog,"setTimeZone",zone);else{rv.setString(R.id.widget_time,"setTimeZone",zone);String timeFmt=h24?"HH:mm":"hh:mm a";rv.setCharSequence(R.id.widget_time,"setFormat24Hour",timeFmt);rv.setCharSequence(R.id.widget_time,"setFormat12Hour",timeFmt);}'
new_update = '''String zone=loadZone(c,appWidgetId),style=loadStyle(c,appWidgetId),label=loadLabel(c,appWidgetId);if(Build.VERSION.SDK_INT<31&&(\"analog\".equals(style)||\"both\".equals(style)))style=\"digital\";boolean analog=\"analog\".equals(style),both=\"both\".equals(style);boolean h24=c.getSharedPreferences(\"clock_tools\",Context.MODE_PRIVATE).getBoolean(\"use_24h\",true);\n        RemoteViews rv=new RemoteViews(c.getPackageName(),chooseLayout(style,width(manager,appWidgetId),height(manager,appWidgetId)));rv.setTextViewText(R.id.widget_city,label);rv.setTextViewText(R.id.widget_zone,zone);rv.setString(R.id.widget_date,\"setTimeZone\",zone);rv.setCharSequence(R.id.widget_date,\"setFormat24Hour\",\"M/d EEE\");rv.setCharSequence(R.id.widget_date,\"setFormat12Hour\",\"M/d EEE\");\n        if(analog||both)rv.setString(R.id.widget_analog,\"setTimeZone\",zone);if(!analog){rv.setString(R.id.widget_time,\"setTimeZone\",zone);String timeFmt=h24?\"HH:mm\":\"hh:mm a\";rv.setCharSequence(R.id.widget_time,\"setFormat24Hour\",timeFmt);rv.setCharSequence(R.id.widget_time,\"setFormat12Hour\",timeFmt);}'''
if old_update not in provider: raise SystemExit('world update anchor not found')
provider = provider.replace(old_update,new_update)
provider = provider.replace('static void refreshAll(Context c){try{AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,WorldClockWidgetProvider.class));for(int id:ids)updateAppWidget(c,m,id);}catch(Throwable ignored){}}',
'''static void refreshAll(Context c){try{AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,WorldClockWidgetProvider.class));for(int id:ids)updateAppWidget(c,m,id);}catch(Throwable ignored){}try{WidgetVariantProviders.refreshWorld(c);}catch(Throwable ignored){}}''')
(JAVA/'WorldClockWidgetProvider.java').write_text(provider,encoding='utf-8')

combo = {
'widget_world_clock_both_square.xml': r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:padding="8dp" android:background="@drawable/widget_bg">
  <TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="14sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end"/>
  <LinearLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:orientation="horizontal" android:gravity="center">
    <AnalogClock android:id="@+id/widget_analog" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:dial="@drawable/widget_analog_dial" android:hand_hour="@drawable/widget_analog_hour" android:hand_minute="@drawable/widget_analog_minute" android:hand_second="@drawable/widget_analog_second"/>
    <TextClock android:id="@+id/widget_time" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="25sp" android:textStyle="bold" android:fontFamily="monospace"/>
  </LinearLayout>
  <TextClock android:id="@+id/widget_date" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#CCFFFFFF" android:textSize="10sp"/>
  <TextView android:id="@+id/widget_zone" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#99FFFFFF" android:textSize="8sp" android:maxLines="1" android:ellipsize="end"/>
</LinearLayout>''',
'widget_world_clock_both_wide.xml': r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="horizontal" android:gravity="center" android:padding="10dp" android:background="@drawable/widget_bg">
  <AnalogClock android:id="@+id/widget_analog" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:dial="@drawable/widget_analog_dial" android:hand_hour="@drawable/widget_analog_hour" android:hand_minute="@drawable/widget_analog_minute" android:hand_second="@drawable/widget_analog_second"/>
  <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1.45" android:orientation="vertical" android:paddingStart="12dp" android:gravity="center_vertical">
    <TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#FFFFFFFF" android:textSize="15sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end"/>
    <TextClock android:id="@+id/widget_time" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#FFFFFFFF" android:textSize="31sp" android:textStyle="bold" android:fontFamily="monospace"/>
    <TextClock android:id="@+id/widget_date" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#CCFFFFFF" android:textSize="11sp"/>
    <TextView android:id="@+id/widget_zone" android:layout_width="match_parent" android:layout_height="wrap_content" android:textColor="#99FFFFFF" android:textSize="9sp" android:maxLines="1" android:ellipsize="end"/>
  </LinearLayout>
</LinearLayout>''',
'widget_world_clock_both_tall.xml': r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:padding="9dp" android:background="@drawable/widget_bg">
  <TextView android:id="@+id/widget_city" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="15sp" android:textStyle="bold" android:maxLines="1" android:ellipsize="end"/>
  <AnalogClock android:id="@+id/widget_analog" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:dial="@drawable/widget_analog_dial" android:hand_hour="@drawable/widget_analog_hour" android:hand_minute="@drawable/widget_analog_minute" android:hand_second="@drawable/widget_analog_second"/>
  <TextClock android:id="@+id/widget_time" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#FFFFFFFF" android:textSize="28sp" android:textStyle="bold" android:fontFamily="monospace"/>
  <TextClock android:id="@+id/widget_date" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#CCFFFFFF" android:textSize="11sp"/>
  <TextView android:id="@+id/widget_zone" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textColor="#99FFFFFF" android:textSize="9sp" android:maxLines="1" android:ellipsize="end"/>
</LinearLayout>'''
}
for name, text in combo.items(): (LAYOUT/name).write_text(text,encoding='utf-8')

# Config: add third style choice.
cfgp = JAVA/'WorldClockWidgetConfigActivity.java'
cfg = cfgp.read_text(encoding='utf-8')
cfg = cfg.replace('private Button digitalButton,analogButton;','private Button digitalButton,analogButton,bothButton;')
cfg = cfg.replace('I18n.tr(this,"デジタル / アナログ")','I18n.tr(this,"デジタル / アナログ / 両方")')
cfg = cfg.replace('digitalButton=Ui.button(this,"デジタル",false);analogButton=Ui.button(this,"アナログ",false);',
                  'digitalButton=Ui.button(this,"デジタル",false);analogButton=Ui.button(this,"アナログ",false);bothButton=Ui.button(this,"両方",false);')
cfg = cfg.replace('LinearLayout.LayoutParams bp2=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);bp2.setMarginStart(Ui.dp(this,8));styles.addView(analogButton,bp2);',
'''LinearLayout.LayoutParams bp2=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);bp2.setMarginStart(Ui.dp(this,8));styles.addView(analogButton,bp2);\n        LinearLayout.LayoutParams bp3=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);bp3.setMarginStart(Ui.dp(this,8));styles.addView(bothButton,bp3);''')
cfg = cfg.replace('analogButton.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=31){selectedStyle="analog";refreshStyleButtons();}});',
'''analogButton.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=31){selectedStyle="analog";refreshStyleButtons();}});\n        bothButton.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=31){selectedStyle="both";refreshStyleButtons();}});''')
cfg = cfg.replace('if(Build.VERSION.SDK_INT<31){analogButton.setEnabled(false);analogButton.setAlpha(.45f);}refreshStyleButtons();',
'''if(Build.VERSION.SDK_INT<31){analogButton.setEnabled(false);analogButton.setAlpha(.45f);bothButton.setEnabled(false);bothButton.setAlpha(.45f);}refreshStyleButtons();''')
old_refresh = 'private void refreshStyleButtons(){boolean d=!"analog".equals(selectedStyle);digitalButton.setBackground(d?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));digitalButton.setTextColor(d?0xFF0D1B2A:Ui.TEXT);boolean a=!d;analogButton.setBackground(a?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));analogButton.setTextColor(a?0xFF0D1B2A:Ui.TEXT);}'
new_refresh = '''private void refreshStyleButtons(){boolean d=\"digital\".equals(selectedStyle),a=\"analog\".equals(selectedStyle),b=\"both\".equals(selectedStyle);digitalButton.setBackground(d?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));digitalButton.setTextColor(d?0xFF0D1B2A:Ui.TEXT);analogButton.setBackground(a?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));analogButton.setTextColor(a?0xFF0D1B2A:Ui.TEXT);bothButton.setBackground(b?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));bothButton.setTextColor(b?0xFF0D1B2A:Ui.TEXT);}'''
if old_refresh not in cfg: raise SystemExit('config style refresh anchor not found')
cfg = cfg.replace(old_refresh,new_refresh)
cfgp.write_text(cfg,encoding='utf-8')

# -----------------------------------------------------------------------------
# Multiple picker sizes for every widget.
# Original provider remains one size; three additional provider entries are added.
# -----------------------------------------------------------------------------
# key: (base class, base xml, base label, original WxH, extra variants)
WIDGETS = {
 'world': ('WorldClockWidgetProvider','world_clock_widget_info.xml','世界時計',(2,2), [('Compact','2×1',2,1),('Wide','4×2',4,2),('Tall','2×3',2,3)]),
 'local': ('LocalClockWidgetProvider','local_clock_widget_info.xml','時計',(2,2), [('Compact','2×1',2,1),('Wide','4×2',4,2),('Tall','2×3',2,3)]),
 'multi': ('MultiWorldClockWidgetProvider','multi_world_clock_widget_info.xml','世界時計 4都市',(4,2), [('Compact','3×2',3,2),('Tall','3×3',3,3),('Large','4×3',4,3)]),
 'next': ('NextAlarmWidgetProvider','next_alarm_widget_info.xml','次のアラーム',(2,1), [('Square','2×2',2,2),('Wide','4×1',4,1),('Tall','2×3',2,3)]),
 'countdown': ('AlarmCountdownWidgetProvider','alarm_countdown_widget_info.xml','次のアラームまで',(2,1), [('Square','2×2',2,2),('Wide','4×1',4,1),('Tall','2×3',2,3)]),
 'alarmlist': ('AlarmListWidgetProvider','alarm_list_widget_info.xml','アラーム一覧',(4,2), [('Compact','3×2',3,2),('Tall','3×3',3,3),('Large','4×3',4,3)]),
 'timer': ('TimerWidgetProvider','timer_widget_info.xml','タイマー操作',(3,1), [('Compact','2×1',2,1),('Square','2×2',2,2),('Wide','4×1',4,1)]),
 'preset': ('TimerPresetWidgetProvider','timer_preset_widget_info.xml','タイマープリセット',(4,2), [('Compact','3×2',3,2),('Tall','3×3',3,3),('Large','4×3',4,3)]),
 'stopwatch': ('StopwatchWidgetProvider','stopwatch_widget_info.xml','ストップウォッチ',(3,1), [('Compact','2×1',2,1),('Square','2×2',2,2),('Wide','4×1',4,1)]),
 'streak': ('StreakWidgetProvider','streak_widget_info.xml','ストリーク',(2,2), [('Compact','2×1',2,1),('Wide','4×2',4,2),('Tall','2×3',2,3)]),
 'stats': ('WakeStatsWidgetProvider','wake_stats_widget_info.xml','起床成績',(4,2), [('Compact','3×2',3,2),('Tall','3×3',3,3),('Large','4×3',4,3)]),
 'tools': ('QuickToolsWidgetProvider','quick_tools_widget_info.xml','時計ツール',(4,2), [('Compact','3×2',3,2),('Tall','3×3',3,3),('Large','4×3',4,3)]),
 'today': ('TodayWidgetProvider','today_widget_info.xml','今日',(4,2), [('Compact','3×2',3,2),('Tall','3×3',3,3),('Large','4×3',4,3)]),
}

def dp_width(cells): return 57 + 73*(cells-1)
def dp_height(cells): return 110*cells

def sized_xml(base_name,cw,ch):
    text=(XML/base_name).read_text(encoding='utf-8')
    text=re.sub(r'android:minWidth="[^"]+"',f'android:minWidth="{dp_width(cw)}dp"',text,count=1)
    text=re.sub(r'android:minHeight="[^"]+"',f'android:minHeight="{dp_height(ch)}dp"',text,count=1)
    text=re.sub(r'android:targetCellWidth="[^"]+"',f'android:targetCellWidth="{cw}"',text,count=1)
    text=re.sub(r'android:targetCellHeight="[^"]+"',f'android:targetCellHeight="{ch}"',text,count=1)
    text=re.sub(r'android:minResizeWidth="[^"]+"','android:minResizeWidth="40dp"',text,count=1)
    text=re.sub(r'android:minResizeHeight="[^"]+"','android:minResizeHeight="40dp"',text,count=1)
    text=re.sub(r'android:maxResizeWidth="[^"]+"','android:maxResizeWidth="1200dp"',text,count=1)
    text=re.sub(r'android:maxResizeHeight="[^"]+"','android:maxResizeHeight="1200dp"',text,count=1)
    return text

# Nested public classes: Android can instantiate these receivers independently.
classes=['package jp.wakeguard.alarm;','','public final class WidgetVariantProviders {','    private WidgetVariantProviders(){}']
world_classes=[]; groups=[]; receiver_lines=[]
for key,(base,base_xml,label,orig,extras) in WIDGETS.items():
    group=[]
    for suffix,size_label,cw,ch in extras:
        cls=base.replace('WidgetProvider','')+suffix+'WidgetProvider'
        classes.append(f'    public static class {cls} extends {base} {{}}')
        group.append(cls)
        if key=='world': world_classes.append(cls)
        info=f'{key}_{suffix.lower()}_widget_info.xml'
        (XML/info).write_text(sized_xml(base_xml,cw,ch),encoding='utf-8')
        receiver_lines.append(f'        <receiver android:name=".WidgetVariantProviders${cls}" android:exported="true" android:label="{label} {size_label}"><intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter><meta-data android:name="android.appwidget.provider" android:resource="@xml/{info[:-4]}" /></receiver>')
    groups.append((key,base,group))

classes.append('')
classes.append('    static void refreshWorld(android.content.Context c){')
for cls in world_classes:
    classes.append(f'        WidgetSuite.refresh(c,{cls}.class,(m,id)->WorldClockWidgetProvider.updateAppWidget(c,m,id));')
classes.append('    }')
classes.append('    static void refreshNonWorld(android.content.Context c){')
for key,base,group in groups:
    if key=='world': continue
    updater = 'NextAlarmWidgetProvider.updateAppWidget' if base=='NextAlarmWidgetProvider' else base+'.update'
    for cls in group:
        classes.append(f'        WidgetSuite.refresh(c,{cls}.class,(m,id)->{updater}(c,m,id));')
classes.append('    }')
classes.append('}')
(JAVA/'WidgetVariantProviders.java').write_text('\n'.join(classes)+'\n',encoding='utf-8')

# Refresh all variant providers after state changes.
wsp=JAVA/'WidgetSuite.java'; ws=wsp.read_text(encoding='utf-8')
anchor='        refresh(c,TodayWidgetProvider.class,(m,id)->TodayWidgetProvider.update(c,m,id));\n'
if anchor not in ws: raise SystemExit('WidgetSuite refresh anchor not found')
ws=ws.replace(anchor,anchor+'        try{WidgetVariantProviders.refreshNonWorld(c);}catch(Throwable ignored){}\n')
wsp.write_text(ws,encoding='utf-8')

# Manifest labels expose the default drop size clearly and add all variants.
man=MANIFEST.read_text(encoding='utf-8')
for key,(base,base_xml,label,orig,extras) in WIDGETS.items():
    size=f'{orig[0]}×{orig[1]}'
    man=re.sub(rf'(<receiver android:name="\.{re.escape(base)}"[^>]*android:label=")[^"]+("[^>]*>)',rf'\g<1>{label} {size}\2',man,count=1)
insert='\n'.join(receiver_lines)+'\n'
marker='        <receiver android:name=".WidgetActionReceiver" android:exported="false" />'
if marker not in man: raise SystemExit('manifest widget marker not found')
man=man.replace(marker,insert+marker)
MANIFEST.write_text(man,encoding='utf-8')

# Version bump.
g=GRADLE.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 71',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "1.6.0"',g,count=1)
GRADLE.write_text(g,encoding='utf-8')

print('WakeGuard v1.6.0 applied: 4 picker sizes per widget family (original + 3 variants), world clock digital/analog/both')
print('variant receivers',len(receiver_lines))
