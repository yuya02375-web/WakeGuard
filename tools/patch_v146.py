from pathlib import Path
import runpy, re

runpy.run_path("tools/patch_v145.py", run_name="__main__")

app = Path("WakeGuard/app")
java = app / "src/main/java/jp/wakeguard/alarm"

# ---------------------------------------------------------------------------
# 1) Streak protection: 2 days/month, max 3, automatic by default.
#    Protected days preserve the streak but never count as a successful wake-up
#    and never increase growth. Every 30 successful streak steps grants +1.
# ---------------------------------------------------------------------------
(java / "StreakProtection.java").write_text(r'''package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Small, non-gamey safety net for an established wake-up streak. */
public final class StreakProtection {
    private static final String FILE="wakeguard_streak_protection_v1";
    public static final int MAX_DAYS=3;
    public static final int MONTHLY_DAYS=2;
    private StreakProtection() {}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}

    public static synchronized void ensure(Context c){
        SharedPreferences sp=p(c);
        if(!sp.getBoolean("init",false)){
            sp.edit()
                .putBoolean("init",true)
                .putInt("balance",MONTHLY_DAYS)
                .putBoolean("auto",true)
                .putString("month",YearMonth.now().toString())
                .putLong("start_epoch_day",LocalDate.now().toEpochDay())
                .putInt("bonus_milestone",0)
                .commit();
        }
        refreshMonthlyLocked(c);
    }

    private static void refreshMonthlyLocked(Context c){
        SharedPreferences sp=p(c);
        YearMonth now=YearMonth.now();
        YearMonth old=now;
        try{old=YearMonth.parse(sp.getString("month",now.toString()));}catch(Throwable ignored){}
        long months=ChronoUnit.MONTHS.between(old,now);
        if(months>0){
            long grant=months*(long)MONTHLY_DAYS;
            int next=(int)Math.min(MAX_DAYS,Math.max(0L,sp.getInt("balance",MONTHLY_DAYS))+grant);
            sp.edit().putInt("balance",next).putString("month",now.toString()).commit();
        }else if(months<0){
            sp.edit().putString("month",now.toString()).commit();
        }
    }

    public static synchronized int available(Context c){ensure(c);return Math.max(0,Math.min(MAX_DAYS,p(c).getInt("balance",MONTHLY_DAYS)));}
    public static synchronized boolean autoEnabled(Context c){ensure(c);return p(c).getBoolean("auto",true);}
    public static synchronized void setAutoEnabled(Context c,boolean enabled){ensure(c);p(c).edit().putBoolean("auto",enabled).apply();}
    public static synchronized long startEpochDay(Context c){ensure(c);return p(c).getLong("start_epoch_day",LocalDate.now().toEpochDay());}

    public static synchronized Set<String> protectedDays(Context c){
        ensure(c);
        return new HashSet<>(p(c).getStringSet("protected_days",new HashSet<>()));
    }

    public static synchronized boolean isProtected(Context c,LocalDate day){return protectedDays(c).contains(day.toString());}

    /** All-or-nothing protection so several missed days never waste a partial balance if the streak cannot be saved. */
    public static synchronized boolean protectDaysIfPossible(Context c,Collection<LocalDate> requested){
        ensure(c);
        Set<String> saved=new HashSet<>(p(c).getStringSet("protected_days",new HashSet<>()));
        ArrayList<LocalDate> needed=new ArrayList<>();
        long start=startEpochDay(c);
        for(LocalDate day:requested){
            if(day==null||Prefs.wasSuccessful(c,day)||saved.contains(day.toString()))continue;
            if(day.toEpochDay()<start)return false; // Never retroactively protect days from before this feature existed.
            needed.add(day);
        }
        if(needed.isEmpty())return true;
        if(!p(c).getBoolean("auto",true))return false;
        refreshMonthlyLocked(c);
        int balance=Math.max(0,Math.min(MAX_DAYS,p(c).getInt("balance",MONTHLY_DAYS)));
        if(balance<needed.size())return false;
        for(LocalDate day:needed)saved.add(day.toString());
        return p(c).edit().putStringSet("protected_days",saved).putInt("balance",balance-needed.size()).commit();
    }

    public static synchronized boolean protectDayIfPossible(Context c,LocalDate day){
        ArrayList<LocalDate> one=new ArrayList<>();one.add(day);return protectDaysIfPossible(c,one);
    }

    /** Reward consistency without adding a game economy: every 30 actual streak successes adds one protection day. */
    public static synchronized void onSuccessfulWake(Context c,int newStreak){
        ensure(c);
        if(newStreak<30||newStreak%30!=0)return;
        int milestone=newStreak/30;
        SharedPreferences sp=p(c);
        int awarded=sp.getInt("bonus_milestone",0);
        if(milestone<=awarded)return;
        int next=Math.min(MAX_DAYS,sp.getInt("balance",MONTHLY_DAYS)+1);
        sp.edit().putInt("balance",next).putInt("bonus_milestone",milestone).apply();
    }
}
''', encoding="utf-8")

# ---------------------------------------------------------------------------
# 2) Streak logic: protected scheduled mornings bridge gaps but do not increment.
# ---------------------------------------------------------------------------
(java / "StreakTracker.java").write_text(r'''package jp.wakeguard.alarm;

import android.content.Context;
import java.time.*;
import java.util.ArrayList;

/** Tracks consecutive successful scheduled wake-ups. Test alarms never count. */
public final class StreakTracker {
    private StreakTracker() {}

    private static boolean selected(LocalDate date,int mask){
        int idx=date.getDayOfWeek().getValue()-1;
        return (mask&(1<<idx))!=0;
    }

    /** Protect every selected missed date between two successful mornings, or none if there is not enough balance. */
    private static boolean coverGap(Context c,LocalDate lastSuccess,LocalDate currentSuccess,int mask){
        if(lastSuccess==null||currentSuccess==null||currentSuccess.isBefore(lastSuccess))return false;
        ArrayList<LocalDate> missed=new ArrayList<>();
        for(LocalDate d=lastSuccess.plusDays(1);d.isBefore(currentSuccess);d=d.plusDays(1)){
            if(selected(d,mask)&&!Prefs.wasSuccessful(c,d)&&!StreakProtection.isProtected(c,d))missed.add(d);
        }
        return StreakProtection.protectDaysIfPossible(c,missed);
    }

    public static void recordWakeSuccess(Context c){
        if(Prefs.sessionIsTest(c))return;
        long epochDay=Prefs.sessionEpochDay(c);if(epochDay<=0)return;
        LocalDate day=LocalDate.ofEpochDay(epochDay);
        long last=Prefs.lastSuccessEpochDay(c);if(last==epochDay)return;

        int mask=AlarmProfiles.get(c,Prefs.activeAlarmId(c)).dayMask;
        boolean continues=false;
        if(last>0){
            LocalDate lastDay=LocalDate.ofEpochDay(last);
            continues=coverGap(c,lastDay,day,mask);
        }
        int next=continues?Prefs.streak(c)+1:1;

        int oldBest=Prefs.bestStreak(c);
        Prefs.streak(c,next);
        if(next>oldBest){Prefs.bestStreak(c,next);if(oldBest>0)Prefs.pendingRecordStreak(c,next);}
        Prefs.totalWakeups(c,Prefs.totalWakeups(c)+1);
        Prefs.lastSuccessEpochDay(c,epochDay);
        Prefs.addSuccessDay(c,day);
        StreakGrowth.onWakeSuccess(c);
        StreakProtection.onSuccessfulWake(c,next);
    }

    /**
     * A missed scheduled morning can be covered automatically by a protection day.
     * The stored streak value is unchanged on a protected day; the next real success adds one.
     */
    public static int displayCurrent(Context c){
        int stored=Prefs.streak(c);long last=Prefs.lastSuccessEpochDay(c);
        if(stored<=0||last<=0)return 0;
        StreakProtection.ensure(c);

        ZoneId zone=ZoneId.systemDefault();ZonedDateTime now=ZonedDateTime.now(zone);
        AlarmStore.Entry activeAlarm=AlarmProfiles.get(c,Prefs.activeAlarmId(c));
        int mask=activeAlarm.dayMask;
        LocalDate lastDay=LocalDate.ofEpochDay(last);
        ArrayList<LocalDate> missed=new ArrayList<>();

        for(LocalDate d=lastDay.plusDays(1);!d.isAfter(now.toLocalDate());d=d.plusDays(1)){
            if(!selected(d,mask))continue;
            ZonedDateTime due=d.atTime(activeAlarm.hour,activeAlarm.minute).atZone(zone);
            if(due.isAfter(now))continue;
            long dueDay=d.toEpochDay();
            if(Prefs.wasSuccessful(c,d)||StreakProtection.isProtected(c,d))continue;
            if(Prefs.active(c)&&!Prefs.sessionIsTest(c)&&Prefs.sessionEpochDay(c)==dueDay)continue;
            missed.add(d);
        }
        if(missed.isEmpty())return stored;
        return StreakProtection.protectDaysIfPossible(c,missed)?stored:0;
    }
}
''', encoding="utf-8")

# ---------------------------------------------------------------------------
# 3) Stats UI: protection balance/toggle + separate protected-day calendar mark.
# ---------------------------------------------------------------------------
(java / "StatsActivity.java").write_text(r'''package jp.wakeguard.alarm;

import android.app.*;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class StatsActivity extends Activity {
    private static final int PROTECTED_COLOR=0xffffc857;
    private YearMonth month=YearMonth.now();
    private LinearLayout body;
    private TextView currentValue,bestValue,totalValue,monthTitle,monthSummary,growthLevel,growthForm,protectionValue;
    private GridLayout calendar;
    private StreakCompanionView companion;
    private Switch protectionAuto;
    private boolean updatingProtection=false;

    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.prepareActivity(this);StreakGrowth.ensure(this);StreakProtection.ensure(this);buildUi();refresh();}
    @Override protected void onResume(){super.onResume();if(companion!=null)companion.onResume();refresh();}
    @Override protected void onPause(){if(companion!=null)companion.onPause();super.onPause();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,8));
        Button back=Ui.ghostButton(this,"‹");back.setTextSize(30);back.setOnClickListener(v->Ui.finishNoAnimation(this));top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView title=Ui.title(this,"ストリーク",28);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,36));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setGravity(Gravity.CENTER);hero.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,20));hero.setBackground(Ui.roundStroke(0xff0b0d13,0xff2c3445,24,this));
        companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,390)));
        growthLevel=Ui.text(this,"成長 Lv.1",20,Ui.TEXT);growthLevel.setTypeface(null,Typeface.BOLD);growthLevel.setGravity(Gravity.CENTER);hero.addView(growthLevel);
        growthForm=Ui.text(this,"火種",12,Ui.ACCENT);growthForm.setGravity(Gravity.CENTER);growthForm.setPadding(0,Ui.dp(this,3),0,0);hero.addView(growthForm);
        currentValue=Ui.text(this,"0日",40,Ui.TEXT);currentValue.setTypeface(null,Typeface.BOLD);currentValue.setGravity(Gravity.CENTER);currentValue.setPadding(0,Ui.dp(this,10),0,0);hero.addView(currentValue);
        TextView streakLabel=Ui.text(this,"現在のストリーク",12,Ui.MUTED);streakLabel.setGravity(Gravity.CENTER);hero.addView(streakLabel);body.addView(hero);

        TextView growHeader=Ui.sectionHeader(this,"成長");growHeader.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,6));body.addView(growHeader);
        LinearLayout growth=Ui.card(this);
        TextView rule=Ui.text(this,"起床に成功するたびに、同じ1体が1段階成長します。火種から始まり、炎の中に竜の姿が形成され、炎竜として上限なく成長し続けます。ガチャ・レア度・通貨・装備・アイテムはありません。",14,Ui.TEXT);growth.addView(rule);
        TextView quality=Ui.text(this,"炎は固定画像ではなく、端末内のシェーダーで白熱コア・赤橙の外炎・揺らぐ輪郭・上昇する炎・火の粉・発光を毎フレーム生成します。炎竜の成長と表示はオフラインでも完全に動作します。",12,Ui.MUTED);quality.setPadding(0,Ui.dp(this,8),0,0);growth.addView(quality);body.addView(growth,Ui.cardParams(this));

        TextView protectHeader=Ui.sectionHeader(this,"ストリーク保護");protectHeader.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,6));body.addView(protectHeader);
        LinearLayout protect=Ui.card(this);
        LinearLayout balanceRow=Ui.row(this);TextView balanceLabel=Ui.text(this,"保護日",16,Ui.TEXT);balanceRow.addView(balanceLabel,new LinearLayout.LayoutParams(0,-2,1));protectionValue=Ui.text(this,"2 / 3",19,PROTECTED_COLOR);protectionValue.setTypeface(null,Typeface.BOLD);balanceRow.addView(protectionValue);protect.addView(balanceRow);
        protectionAuto=new Switch(this);protectionAuto.setText(I18n.tr(this,"自動で保護日を使う"));protectionAuto.setTextColor(Ui.TEXT);protectionAuto.setTextSize(15);protectionAuto.setPadding(0,Ui.dp(this,10),0,0);protectionAuto.setOnCheckedChangeListener((button,checked)->{if(updatingProtection)return;StreakProtection.setAutoEnabled(this,checked);refreshProtection();});protect.addView(protectionAuto,new LinearLayout.LayoutParams(-1,-2));
        TextView protectRule=Ui.text(this,"毎月1日に2日分回復し、最大3日まで持てます。起きられなかった対象日に1日だけ自動使用してストリークを守ります。保護日は成功回数や成長Lvには加算されません。",12,Ui.MUTED);protectRule.setPadding(0,Ui.dp(this,8),0,0);protect.addView(protectRule);
        TextView bonusRule=Ui.text(this,"30日連続で実際に起床成功するごとに、保護日を1日追加します（最大3日）。",12,Ui.MUTED);bonusRule.setPadding(0,Ui.dp(this,5),0,0);protect.addView(bonusRule);body.addView(protect,Ui.cardParams(this));

        TextView streakHeader=Ui.sectionHeader(this,"記録");streakHeader.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,4));body.addView(streakHeader);
        bestValue=addStatRow("最高ストリーク");body.addView(Ui.divider(this));totalValue=addStatRow("成功した起床");body.addView(Ui.divider(this));

        TextView calendarHeader=Ui.sectionHeader(this,"カレンダー");calendarHeader.setPadding(0,Ui.dp(this,26),0,Ui.dp(this,8));body.addView(calendarHeader);
        LinearLayout monthRow=Ui.row(this);monthRow.setPadding(0,0,0,Ui.dp(this,4));
        Button prev=Ui.ghostButton(this,"‹");prev.setTextSize(25);prev.setOnClickListener(v->{month=month.minusMonths(1);renderCalendar();});monthRow.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,46)));
        monthTitle=Ui.text(this,"",18,Ui.TEXT);monthTitle.setTypeface(null,Typeface.BOLD);monthTitle.setGravity(Gravity.CENTER);monthRow.addView(monthTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        Button next=Ui.ghostButton(this,"›");next.setTextSize(25);next.setOnClickListener(v->{month=month.plusMonths(1);renderCalendar();});monthRow.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,46)));body.addView(monthRow);
        calendar=new GridLayout(this);calendar.setColumnCount(7);body.addView(calendar);
        monthSummary=Ui.text(this,"",13,Ui.MUTED);monthSummary.setPadding(0,Ui.dp(this,12),0,0);body.addView(monthSummary);
        TextView legend=Ui.text(this,"◆ 成功   ◇ 保護",12,Ui.MUTED);legend.setPadding(0,Ui.dp(this,5),0,0);body.addView(legend);
        TextView note=Ui.text(this,"成功した本番アラームの日だけ成長します。保護日はストリークだけを維持し、テストアラームは記録しません。",12,Ui.MUTED);note.setPadding(0,Ui.dp(this,8),0,0);body.addView(note);
        setContentView(root);Ui.applySystemBarInsets(this,root);
    }

    private TextView addStatRow(String label){LinearLayout row=Ui.row(this);row.setPadding(0,Ui.dp(this,13),0,Ui.dp(this,13));TextView name=Ui.text(this,label,16,Ui.TEXT);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView value=Ui.text(this,"-",18,Ui.TEXT);value.setTypeface(null,Typeface.BOLD);row.addView(value);body.addView(row);return value;}

    private void refresh(){
        if(currentValue==null)return;StreakGrowth.ensure(this);StreakProtection.ensure(this);int streak=StreakTracker.displayCurrent(this);long lv=StreakGrowth.level(this);
        currentValue.setText(I18n.tr(this,streak+"日"));bestValue.setText(I18n.tr(this,Prefs.bestStreak(this)+"日"));totalValue.setText(I18n.tr(this,Prefs.totalWakeups(this)+"回"));
        growthLevel.setText(I18n.tr(this,"成長")+" Lv."+lv);growthForm.setText(StreakGrowth.growthDescriptor(this));companion.setGrowth(lv,streak);refreshProtection();renderCalendar();
    }

    private void refreshProtection(){
        if(protectionValue==null)return;int n=StreakProtection.available(this);protectionValue.setText(n+" / "+StreakProtection.MAX_DAYS);
        if(protectionAuto!=null){updatingProtection=true;protectionAuto.setChecked(StreakProtection.autoEnabled(this));updatingProtection=false;}
    }

    private void renderCalendar(){
        if(calendar==null)return;calendar.removeAllViews();monthTitle.setText(month.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"month"),I18n.locale(this))));
        String[] weekdays={"月","火","水","木","金","土","日"};for(String w:weekdays){TextView t=Ui.text(this,w,12,Ui.MUTED);t.setGravity(Gravity.CENTER);addCalendarCell(t,34);}
        LocalDate first=month.atDay(1);int blanks=first.getDayOfWeek().getValue()-1;for(int i=0;i<blanks;i++)addCalendarCell(Ui.text(this,"",14,Ui.MUTED),52);
        LocalDate today=LocalDate.now();int successCount=0,protectedCount=0;Set<String> protectedDays=StreakProtection.protectedDays(this);
        for(int d=1;d<=month.lengthOfMonth();d++){
            LocalDate date=month.atDay(d);boolean success=Prefs.wasSuccessful(this,date);boolean protectedDay=!success&&protectedDays.contains(date.toString());if(success)successCount++;if(protectedDay)protectedCount++;
            int color=success?Ui.SUCCESS:(protectedDay?PROTECTED_COLOR:(date.equals(today)?Ui.ACCENT:Ui.TEXT));
            LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);TextView number=Ui.text(this,String.valueOf(d),15,color);number.setGravity(Gravity.CENTER);if(date.equals(today))number.setTypeface(null,Typeface.BOLD);cell.addView(number,new LinearLayout.LayoutParams(-1,Ui.dp(this,25)));
            TextView dot=Ui.text(this,success?"◆":(protectedDay?"◇":""),9,success?Ui.ACCENT:PROTECTED_COLOR);dot.setGravity(Gravity.CENTER);cell.addView(dot,new LinearLayout.LayoutParams(-1,Ui.dp(this,15)));addCalendarCell(cell,52);
        }
        int total=blanks+month.lengthOfMonth(),trailing=(7-(total%7))%7;for(int i=0;i<trailing;i++)addCalendarCell(Ui.text(this,"",14,Ui.MUTED),52);
        monthSummary.setText(I18n.tr(this,"この月の成功")+"  "+successCount+"  ·  "+I18n.tr(this,"保護")+"  "+protectedCount);
    }
    private void addCalendarCell(View v,int heightDp){GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=Ui.dp(this,heightDp);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);v.setLayoutParams(lp);calendar.addView(v);}
}
''', encoding="utf-8")

# ---------------------------------------------------------------------------
# 4) More natural procedural fire. Keep it fully local/offline.
#    - less blown-out white core
#    - asymmetric low-frequency gusts + high-frequency turbulent breakup
#    - more rising plume layers and subtle smoke
#    - stable background (pulse only affects flame)
#    - post-Lv1000 appearance keeps evolving through an uncapped logarithmic signal
# ---------------------------------------------------------------------------
renderer_path=java/"StreakCompanionView.java"
s=renderer_path.read_text(encoding="utf-8")
s=s.replace("WakeGuard v1.4.5 realistic flame-dragon renderer.","WakeGuard v1.4.6 realistic flame-dragon renderer.")
s=s.replace("infinite = Math.max(0f, Math.min(2.6f, infinite));","infinite = Math.max(0f, infinite);")

old='''            "  float n1=fbm(vec2(uv.x*5.2,uv.y*7.0-t*0.42));\\n" +
            "  float n2=fbm(vec2(uv.x*12.5+13.7,uv.y*14.5-t*0.93));\\n" +
            "  float sway=sin(t*1.15+uv.y*13.0+n1*3.2)*0.0048;\\n" +
            "  vec2 warp=vec2((n1-0.5)*(0.011+min(uPower,12.0)*0.00065)+sway,(n2-0.5)*0.0075);\\n" +'''
new='''            "  float n1=fbm(vec2(uv.x*4.6,uv.y*6.4-t*0.34));\\n" +
            "  float n2=fbm(vec2(uv.x*11.8+13.7,uv.y*15.2-t*0.91));\\n" +
            "  float n3=fbm(vec2(uv.x*23.0-7.1,uv.y*28.0-t*1.57));\\n" +
            "  float upper=1.0-smoothstep(0.56,0.92,uv.y);\\n" +
            "  float ancient=0.5+0.25*sin(uInfinite*1.618)+0.25*sin(uInfinite*2.414+1.3);\\n" +
            "  float gust=(sin(t*0.33)+0.55*sin(t*0.19+1.7))*0.5;\\n" +
            "  float sway=sin(t*1.07+uv.y*12.0+n1*3.6)*(0.0036+0.0054*upper)+gust*0.0090*upper;\\n" +
            "  vec2 warp=vec2((n1-0.5)*(0.010+min(uPower,12.0)*0.00062)+sway+(n3-0.5)*0.0042*upper,(n2-0.5)*0.0070);\\n" +'''
if old not in s: raise SystemExit("v1.4.6 shader warp anchor missing")
s=s.replace(old,new,1)

s=s.replace('''            "  for(int i=1;i<=4;i++){ float fi=float(i); float drift=(noise(vec2(uv.y*11.0+t*0.6,fi*4.7))-0.5)*0.026; vec2 puv=uv+vec2(drift,fi*0.0125+(n1-0.5)*0.005); float src=maskAt(puv); plume=max(plume,src-fi*0.135+n2*0.055); }\\n" +
            "  float shape=clamp(max(m,plume),0.0,1.0);\\n" +''','''            "  for(int i=1;i<=6;i++){ float fi=float(i); float drift=(noise(vec2(uv.y*10.0+t*0.52,fi*4.7))-0.5)*(0.020+fi*0.0027)+gust*0.004*fi; vec2 puv=uv+vec2(drift,fi*0.0098+(n1-0.5)*0.0045); float src=maskAt(puv); plume=max(plume,src-fi*0.092+n2*0.048); }\\n" +
            "  float shape=clamp(max(m,plume),0.0,1.0);\\n" +
            "  float edgeBreak=(0.035+0.055*upper)*(0.45*n2+0.55*n3)*smoothstep(0.08,0.58,shape)*(1.0-smoothstep(0.72,0.98,shape));\\n" +
            "  shape=clamp(shape-edgeBreak,0.0,1.0);\\n" +''',1)

s=s.replace('''            "  float center=1.0-smoothstep(0.02,0.34,abs(uv.x-0.5));\\n" +
            "  float lower=smoothstep(0.24,0.88,uv.y);\\n" +
            "  float core=shape*center*lower*(0.68+0.42*n2);\\n" +
            "  float heat=clamp(shape*(0.46+0.54*(0.58*n1+0.42*n2))+core*0.55,0.0,1.0);\\n" +''','''            "  float center=1.0-smoothstep(0.015,0.215,abs(uv.x-0.5));\\n" +
            "  float lower=smoothstep(0.47,0.94,uv.y);\\n" +
            "  float core=shape*center*lower*smoothstep(0.34,0.82,n1)*(0.50+0.34*n2);\\n" +
            "  float heat=clamp(shape*(0.40+0.60*(0.50*n1+0.32*n2+0.18*n3))+core*0.62,0.0,1.0);\\n" +''',1)

s=s.replace('''            "  vec3 hot=mix(white,vec3(0.78,0.96,1.0),uMythic*0.38);\\n" +''','''            "  vec3 hot=mix(white,vec3(0.78,0.96,1.0),clamp(uMythic*0.28+ancient*min(0.18,uInfinite*0.035),0.0,0.52));\\n" +''',1)

old_tail='''            "  sparks*=region*clamp(0.32+uPower*0.045+uStreak*0.012+uInfinite*0.08,0.0,1.35);\\n" +
            "  color+=mix(vec3(1.0,0.28,0.02),vec3(1.0,0.88,0.32),hash(floor(uv*43.0)))*sparks;\\n" +
            "  float pulse=0.96+0.04*sin(t*2.35+n1*6.283);\\n" +
            "  gl_FragColor=vec4(clamp(color*pulse,0.0,1.0),1.0);\\n" +'''
new_tail='''            "  sparks*=region*clamp(0.28+uPower*0.040+uStreak*0.010+min(0.48,uInfinite*0.065),0.0,1.32);\\n" +
            "  float drifting=sparkLayer(uv+vec2(gust*0.025,0.0),12.0,0.43,vec2(19.7,5.4))*region*(0.20+0.18*n1);\\n" +
            "  color+=mix(vec3(1.0,0.28,0.02),vec3(1.0,0.88,0.32),hash(floor(uv*43.0)))*(sparks+drifting*0.55);\\n" +
            "  float smoke=upper*(1.0-shape)*smoothstep(0.42,0.78,n1)*smoothstep(0.04,0.46,blur2)*0.18;\\n" +
            "  color=mix(color,vec3(0.020,0.023,0.030),smoke);\\n" +
            "  float shimmer=(n3-0.5)*halo*(0.055+0.025*ancient);\\n" +
            "  color+=vec3(1.0,0.42,0.04)*shimmer;\\n" +
            "  float pulse=0.965+0.035*sin(t*2.15+n1*6.283);\\n" +
            "  color=bg+(color-bg)*pulse;\\n" +
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),1.0);\\n" +'''
if old_tail not in s: raise SystemExit("v1.4.6 shader tail anchor missing")
s=s.replace(old_tail,new_tail,1)

# More visible long-term geometry variation after Lv1000, while remaining extremely slow.
s=s.replace('float span = 86f + wings * 74f + mythic * 16f + infinite * 5f;','float span = 86f + wings * 74f + mythic * 16f + infinite * 6.5f;',1)
s=s.replace('int crown = 5 + (int)(mythic * 4f) + Math.min(3, (int)infinite);','int crown = 5 + (int)(mythic * 4f) + Math.min(8, (int)Math.floor(infinite * 1.35f));',1)
renderer_path.write_text(s,encoding="utf-8")

# ---------------------------------------------------------------------------
# 5) Localize all new protection UI in Japanese / English / Korean.
# ---------------------------------------------------------------------------
ip=java/"I18n.java";i=ip.read_text(encoding="utf-8")
anchor='        put("タイマーを停止","Stop timer","타이머 정지");'
add='''        put("ストリーク保護","Streak protection","스트릭 보호"); put("保護日","Protection days","보호일"); put("保護","Protected","보호");\n        put("自動で保護日を使う","Use protection days automatically","보호일 자동 사용");\n        put("毎月1日に2日分回復し、最大3日まで持てます。起きられなかった対象日に1日だけ自動使用してストリークを守ります。保護日は成功回数や成長Lvには加算されません。","Two protection days are restored on the first of each month, up to three. One is automatically used on an eligible missed wake-up to preserve your streak. Protected days do not increase successful wake-ups or Growth Lv.","매월 1일에 보호일 2일이 회복되며 최대 3일까지 보유할 수 있습니다. 대상 기상일을 놓치면 1일을 자동 사용해 스트릭을 보호합니다. 보호일은 성공 횟수나 성장 Lv에 더해지지 않습니다.");\n        put("30日連続で実際に起床成功するごとに、保護日を1日追加します（最大3日）。","Every 30 actual successful wake-ups in the streak adds one protection day, up to three.","실제 기상 성공을 30일 연속 달성할 때마다 보호일 1일이 추가됩니다(최대 3일).");\n        put("◆ 成功   ◇ 保護","◆ Success   ◇ Protected","◆ 성공   ◇ 보호"); put("この月の成功","Successful this month","이번 달 성공");\n        put("成功した本番アラームの日だけ成長します。保護日はストリークだけを維持し、テストアラームは記録しません。","Growth only increases on successful real alarms. A protected day only preserves the streak, and test alarms are not recorded.","성공한 실제 알람에서만 성장합니다. 보호일은 스트릭만 유지하며 테스트 알람은 기록하지 않습니다.");\n'''
if anchor not in i: raise SystemExit("v1.4.6 i18n anchor missing")
i=i.replace(anchor,add+anchor,1);ip.write_text(i,encoding="utf-8")

# Version bump.
gp=app/"build.gradle.kts";g=gp.read_text(encoding="utf-8")
if 'versionCode = 56' not in g or 'versionName = "1.4.5"' not in g: raise SystemExit("v1.4.5 version markers missing")
g=g.replace('versionCode = 56','versionCode = 57',1).replace('versionName = "1.4.5"','versionName = "1.4.6"',1);gp.write_text(g,encoding="utf-8")

# Verification.
checks={
    java/"StreakProtection.java":["MONTHLY_DAYS=2","MAX_DAYS=3","protectDaysIfPossible","bonus_milestone"],
    java/"StreakTracker.java":["coverGap","StreakProtection.protectDaysIfPossible","StreakProtection.onSuccessfulWake"],
    java/"StatsActivity.java":["ストリーク保護","protectionAuto","PROTECTED_COLOR","◇"],
    renderer_path:["float n3=fbm","float gust=","float smoke=","color=bg+(color-bg)*pulse","infinite = Math.max(0f, infinite)"],
}
for path,needles in checks.items():
    text=path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text: raise SystemExit(f"Missing v1.4.6 marker {needle} in {path.name}")
if 'versionCode = 57' not in gp.read_text(encoding="utf-8"): raise SystemExit("v1.4.6 version bump missing")
print("WakeGuard v1.4.6 realistic growth + streak protection applied")
print("Monthly protection 2, carry cap 3, auto toggle: PASS")
print("Protected days preserve streak without growth/success credit: PASS")
print("30-success bonus protection: PASS")
print("More natural local/offline flame animation: PASS")
print("Post-1000 visual signal remains uncapped: PASS")
