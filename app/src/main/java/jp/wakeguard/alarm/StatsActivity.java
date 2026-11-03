package jp.wakeguard.alarm;

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
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(Ui.screenGradient(this));
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,8));
        android.widget.ImageButton back=Ui.iconButton(this,R.drawable.ic_v292_action_back,false,"戻る");back.setOnClickListener(v->Ui.finishNoAnimation(this));top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView title=Ui.title(this,"ストリーク",27);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout hero=Ui.card(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,22,this));
        hero.setPadding(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,8));
        companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,248)));
        LinearLayout summary=Ui.row(this);summary.setGravity(Gravity.BOTTOM);summary.setPadding(0,Ui.dp(this,2),0,Ui.dp(this,12));
        LinearLayout currentBox=new LinearLayout(this);currentBox.setOrientation(LinearLayout.VERTICAL);
        currentValue=Ui.text(this,"0日",40,Ui.TEXT);currentValue.setTypeface(null,Typeface.BOLD);currentBox.addView(currentValue);
        TextView streakLabel=Ui.text(this,"現在のストリーク",13,Ui.MUTED);currentBox.addView(streakLabel);
        summary.addView(currentBox,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout growthBox=new LinearLayout(this);growthBox.setOrientation(LinearLayout.VERTICAL);growthBox.setGravity(Gravity.END);
        growthLevel=Ui.text(this,"Lv.1",21,Ui.TEXT);growthLevel.setTypeface(null,Typeface.BOLD);growthLevel.setGravity(Gravity.END);growthBox.addView(growthLevel);
        growthForm=Ui.text(this,"火種",13,Ui.ACCENT);growthForm.setGravity(Gravity.END);growthForm.setPadding(0,Ui.dp(this,3),0,0);growthBox.addView(growthForm);
        summary.addView(growthBox);
        hero.addView(summary);body.addView(hero,Ui.cardParams(this));

        LinearLayout metrics=Ui.row(this);metrics.setPadding(Ui.dp(this,2),Ui.dp(this,11),Ui.dp(this,2),Ui.dp(this,11));
        LinearLayout bestBox=new LinearLayout(this);bestBox.setOrientation(LinearLayout.VERTICAL);bestBox.addView(Ui.text(this,"最高",12,Ui.MUTED));bestValue=Ui.text(this,"-",20,Ui.TEXT);bestValue.setTypeface(null,Typeface.BOLD);bestBox.addView(bestValue);metrics.addView(bestBox,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout totalBox=new LinearLayout(this);totalBox.setOrientation(LinearLayout.VERTICAL);totalBox.addView(Ui.text(this,"成功",12,Ui.MUTED));totalValue=Ui.text(this,"-",20,Ui.TEXT);totalValue.setTypeface(null,Typeface.BOLD);totalBox.addView(totalValue);metrics.addView(totalBox,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout protectMetric=new LinearLayout(this);protectMetric.setOrientation(LinearLayout.VERTICAL);protectMetric.addView(Ui.text(this,"保護",12,Ui.MUTED));TextView protectMetricValue=Ui.text(this,"最大3日",20,Ui.TEXT);protectMetricValue.setTypeface(null,Typeface.BOLD);protectMetric.addView(protectMetricValue);metrics.addView(protectMetric,new LinearLayout.LayoutParams(0,-2,1));
        metrics.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,18,this));
        body.addView(metrics,Ui.cardParams(this));

        TextView protectHeader=Ui.sectionHeader(this,"ストリーク保護");protectHeader.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,6));body.addView(protectHeader);
        LinearLayout protect=Ui.card(this);
        LinearLayout balanceRow=Ui.row(this);TextView balanceLabel=Ui.text(this,"保護日",16,Ui.TEXT);balanceRow.addView(balanceLabel,new LinearLayout.LayoutParams(0,-2,1));protectionValue=Ui.text(this,"0 / 3",19,PROTECTED_COLOR);protectionValue.setTypeface(null,Typeface.BOLD);balanceRow.addView(protectionValue);protect.addView(balanceRow);
        protectionAuto=new Switch(this);protectionAuto.setText(I18n.tr(this,"自動で保護日を使う"));protectionAuto.setTextColor(Ui.TEXT);protectionAuto.setTextSize(15);protectionAuto.setPadding(0,Ui.dp(this,10),0,0);protectionAuto.setOnCheckedChangeListener((button,checked)->{if(updatingProtection)return;StreakProtection.setAutoEnabled(this,checked);refreshProtection();});protect.addView(protectionAuto,new LinearLayout.LayoutParams(-1,-2));
        TextView protectRule=Ui.text(this,"最大3日。起きられなかった対象日に1日使ってストリークを維持します。",12,Ui.MUTED);protectRule.setPadding(0,Ui.dp(this,8),0,0);protect.addView(protectRule);
        TextView bonusRule=Ui.text(this,"30日連続の成功で1日追加。",12,Ui.MUTED);bonusRule.setPadding(0,Ui.dp(this,5),0,0);protect.addView(bonusRule);body.addView(protect,Ui.cardParams(this));

        TextView calendarHeader=Ui.sectionHeader(this,"カレンダー");calendarHeader.setPadding(0,Ui.dp(this,24),0,Ui.dp(this,8));body.addView(calendarHeader);
        LinearLayout monthRow=Ui.row(this);monthRow.setPadding(0,0,0,Ui.dp(this,4));
        android.widget.ImageButton prev=Ui.iconButton(this,R.drawable.ic_v293_chevron_left,false,"前の月");prev.setOnClickListener(v->{month=month.minusMonths(1);renderCalendar();});monthRow.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,46)));
        monthTitle=Ui.text(this,"",18,Ui.TEXT);monthTitle.setTypeface(null,Typeface.BOLD);monthTitle.setGravity(Gravity.CENTER);monthRow.addView(monthTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        android.widget.ImageButton next=Ui.iconButton(this,R.drawable.ic_v293_chevron_right,false,"次の月");next.setOnClickListener(v->{month=month.plusMonths(1);renderCalendar();});monthRow.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,46)));body.addView(monthRow);
        calendar=new GridLayout(this);calendar.setColumnCount(7);body.addView(calendar);
        monthSummary=Ui.text(this,"",13,Ui.MUTED);monthSummary.setPadding(0,Ui.dp(this,12),0,0);body.addView(monthSummary);
        TextView legend=Ui.text(this,"◆ 成功   ◇ 保護",12,Ui.MUTED);legend.setPadding(0,Ui.dp(this,5),0,0);body.addView(legend);
        TextView note=Ui.text(this,"成功した本番アラームの日だけ成長します。保護日はストリークだけを維持し、テストアラームは記録しません。",12,Ui.MUTED);note.setPadding(0,Ui.dp(this,8),0,0);body.addView(note);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,9),Ui.dp(this,7),Ui.dp(this,9),Ui.dp(this,8));nav.setBackgroundColor(Ui.SURFACE);
        Button alarms=Ui.bottomTab(this,"アラーム",false),clock=Ui.bottomTab(this,"時計",false),timer=Ui.bottomTab(this,"タイマー",false),sw=Ui.bottomTab(this,"ストップウォッチ",false),streak=Ui.bottomTab(this,"ストリーク",true);
        nav.addView(alarms,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(clock,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(timer,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(sw,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(streak,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));
        alarms.setOnClickListener(v->Ui.launchNoAnimation(this,new android.content.Intent(this,MainActivity.class)));clock.setOnClickListener(v->Ui.launchNoAnimation(this,new android.content.Intent(this,ClockActivity.class).putExtra("mode","world")));timer.setOnClickListener(v->Ui.launchNoAnimation(this,new android.content.Intent(this,ClockActivity.class).putExtra("mode","timer")));sw.setOnClickListener(v->Ui.launchNoAnimation(this,new android.content.Intent(this,ClockActivity.class).putExtra("mode","stopwatch")));root.addView(nav);
        setContentView(root);Ui.applySystemBarInsets(this,root);
    }

    private TextView addStatRow(String label){LinearLayout row=Ui.row(this);row.setPadding(0,Ui.dp(this,13),0,Ui.dp(this,13));TextView name=Ui.text(this,label,16,Ui.TEXT);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView value=Ui.text(this,"-",18,Ui.TEXT);value.setTypeface(null,Typeface.BOLD);row.addView(value);body.addView(row);return value;}

    private void refresh(){
        if(currentValue==null)return;StreakGrowth.ensure(this);StreakProtection.ensure(this);int streak=StreakTracker.displayCurrent(this);long lv=StreakGrowth.level(this);
        currentValue.setText(I18n.tr(this,streak+"日"));bestValue.setText(I18n.tr(this,Prefs.bestStreak(this)+"日"));totalValue.setText(I18n.tr(this,Prefs.totalWakeups(this)+"回"));
        growthLevel.setText("Lv."+lv);growthForm.setText(StreakGrowth.appearanceDescriptor(this,lv,streak));companion.setGrowth(lv,streak);refreshProtection();renderCalendar();
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
