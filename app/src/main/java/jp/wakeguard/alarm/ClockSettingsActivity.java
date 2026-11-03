package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

public class ClockSettingsActivity extends Activity {
    private LinearLayout body; private android.content.SharedPreferences p;
    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.prepareActivity(this);p=getSharedPreferences("clock_tools",MODE_PRIVATE);build();}
    @Override public void onBackPressed(){Ui.finishNoAnimation(this);}
    private void build(){
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(Ui.BG);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),Ui.dp(this,46));sv.addView(body);
        LinearLayout top=Ui.row(this);top.setPadding(0,0,0,Ui.dp(this,4));Button back=Ui.ghostButton(this,"‹");back.setTextSize(28);back.setOnClickListener(v->Ui.finishNoAnimation(this));top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,50)));TextView title=Ui.title(this,"時計の詳細設定",24);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));body.addView(top);
        section("表示");addSwitch("24時間表示","use_24h",true);addSwitch("日付・タイムゾーン情報を表示","clock_show_detail",true);addSwitch("画面を点灯したままにする","clock_keep_on",false);
        TextView init=Ui.sectionHeader(this,"初期表示");body.addView(init,Ui.gapTop(this,18));LinearLayout seg=new LinearLayout(this);Button d=Ui.button(this,"デジタル",!p.getBoolean("clock_face_analog",false));Button a=Ui.button(this,"アナログ",p.getBoolean("clock_face_analog",false));d.setOnClickListener(v->{p.edit().putBoolean("clock_face_analog",false).apply();recreate();});a.setOnClickListener(v->{p.edit().putBoolean("clock_face_analog",true).apply();recreate();});seg.addView(d,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));seg.addView(a,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));body.addView(seg);
        section("アナログ時計");addSwitch("文字盤に数字を表示","analog_numbers",true);addSwitch("分の目盛りを表示","analog_ticks",true);addSwitch("秒針を表示","analog_seconds",true);addNumberScale();
        section("ウィジェット");TextView wh=Ui.text(this,"IGNIDO Wakeの時計・アラーム・タイマー・ストップウォッチ・世界時計・ストリークをホーム画面から直接使えます。ほぼ1マスから大サイズまで縦横にリサイズできます。",12,Ui.MUTED);body.addView(wh,Ui.gapTop(this,4));
        addWidgetButton("世界時計（デジタル / アナログ）",WorldClockWidgetProvider.class);
        addWidgetButton("時計（端末の時刻）",LocalClockWidgetProvider.class);
        addWidgetButton("世界時計 4都市",MultiWorldClockWidgetProvider.class);
        addWidgetButton("次のアラーム",NextAlarmWidgetProvider.class);
        addWidgetButton("次のアラームまでのカウントダウン",AlarmCountdownWidgetProvider.class);
        addWidgetButton("アラーム一覧",AlarmListWidgetProvider.class);
        addWidgetButton("タイマー操作",TimerWidgetProvider.class);
        addWidgetButton("タイマープリセット 1〜60分",TimerPresetWidgetProvider.class);
        addWidgetButton("ストップウォッチ",StopwatchWidgetProvider.class);
        addWidgetButton("ストリーク",StreakWidgetProvider.class);
        addWidgetButton("今月の起床成績",WakeStatsWidgetProvider.class);
        addWidgetButton("時計ツールショートカット",QuickToolsWidgetProvider.class);
        addWidgetButton("今日（日付・次アラーム・ストリーク）",TodayWidgetProvider.class);
        Button reset=Ui.button(this,"時計設定をリセット",false);reset.setOnClickListener(v->{p.edit().remove("use_24h").remove("clock_show_detail").remove("clock_keep_on").remove("clock_face_analog").remove("analog_numbers").remove("analog_ticks").remove("analog_seconds").remove("analog_number_scale").apply();Toast.makeText(this,I18n.tr(this,"リセットしました"),Toast.LENGTH_SHORT).show();recreate();});body.addView(reset,Ui.gapTop(this,28));
        setContentView(sv);Ui.applySystemBarInsets(this,sv);
    }
    private void section(String s){TextView h=Ui.title(this,s,18);body.addView(h,Ui.gapTop(this,22));}
    private void addSwitch(String label,String key,boolean def){LinearLayout row=Ui.row(this);TextView t=Ui.text(this,label,15,Ui.TEXT);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(p.getBoolean(key,def));sw.setOnCheckedChangeListener((v,x)->{p.edit().putBoolean(key,x).apply();if("use_24h".equals(key))WorldClockWidgetProvider.refreshAll(this);});row.addView(sw);body.addView(row);body.addView(Ui.divider(this));}
    private void addWidgetButton(String label,Class<?> provider){Button b=Ui.button(this,label,false);b.setOnClickListener(v->showSizePicker(provider,label));body.addView(b,Ui.gapTop(this,8));}
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
    private void pinWidget(Class<?> provider,String name){try{if(android.os.Build.VERSION.SDK_INT>=26){android.appwidget.AppWidgetManager m=android.appwidget.AppWidgetManager.getInstance(this);if(m.isRequestPinAppWidgetSupported()){boolean ok=m.requestPinAppWidget(new ComponentName(this,provider),null,null);Toast.makeText(this,I18n.tr(this,ok?"ホーム画面に追加してください":"このホーム画面では直接追加できません"),Toast.LENGTH_SHORT).show();return;}}}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"ホーム画面を長押し → ウィジェット → IGNIDO Wake から追加してください"),Toast.LENGTH_LONG).show();}
    private void addNumberScale(){LinearLayout row=Ui.row(this);TextView t=Ui.text(this,"数字の大きさ",15,Ui.TEXT);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));String[] raw={"小","中","大"};int cur=Math.max(0,Math.min(2,p.getInt("analog_number_scale",1)));Button size=Ui.ghostButton(this,I18n.tr(this,raw[cur])+"  ›");size.setOnClickListener(v->{int n=(p.getInt("analog_number_scale",1)+1)%3;p.edit().putInt("analog_number_scale",n).apply();size.setText(I18n.tr(this,raw[n])+"  ›");});row.addView(size);body.addView(row);body.addView(Ui.divider(this));}
}
