package jp.wakeguard.alarm;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class StatsActivity extends Activity {
    private YearMonth month=YearMonth.now();
    private LinearLayout body;
    private TextView currentValue,bestValue,totalValue,monthTitle,monthSummary,flameLevel,flameHint;
    private GridLayout calendar;
    private StreakFlameView flame;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); Ui.prepareActivity(this); buildUi(); refresh(); }
    @Override protected void onResume(){ super.onResume(); refresh(); }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this); top.setPadding(Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,8));
        Button back=Ui.ghostButton(this,"‹"); back.setTextSize(30); back.setOnClickListener(v->Ui.finishNoAnimation(this)); top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView title=Ui.title(this,"ストリーク",28); top.addView(title,new LinearLayout.LayoutParams(0,-2,1)); root.addView(top);
        ScrollView sv=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(Ui.dp(this,22),Ui.dp(this,4),Ui.dp(this,22),Ui.dp(this,36)); sv.addView(body); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER); hero.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,22)); hero.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,22,this));
        flame=new StreakFlameView(this); hero.addView(flame,new LinearLayout.LayoutParams(Ui.dp(this,220),Ui.dp(this,235)));
        currentValue=Ui.text(this,"0日",42,Ui.TEXT); currentValue.setTypeface(null,Typeface.BOLD); currentValue.setGravity(Gravity.CENTER); hero.addView(currentValue);
        flameLevel=Ui.text(this,"EMBER",14,Ui.ACCENT); flameLevel.setTypeface(null,Typeface.BOLD); flameLevel.setGravity(Gravity.CENTER); flameLevel.setPadding(0,Ui.dp(this,3),0,0); hero.addView(flameLevel);
        flameHint=Ui.text(this,"最初の1日で火が灯る",12,Ui.MUTED); flameHint.setGravity(Gravity.CENTER); flameHint.setPadding(0,Ui.dp(this,8),0,0); hero.addView(flameHint); body.addView(hero);

        TextView streakHeader=Ui.sectionHeader(this,"記録"); streakHeader.setPadding(0,Ui.dp(this,24),0,Ui.dp(this,4)); body.addView(streakHeader);
        bestValue=addStatRow("最高ストリーク"); body.addView(Ui.divider(this)); totalValue=addStatRow("成功した起床"); body.addView(Ui.divider(this));

        TextView evolution=Ui.text(this,"成長段階  1日 → 3日 → 7日 → 14日 → 30日",12,Ui.MUTED); evolution.setPadding(0,Ui.dp(this,14),0,0); body.addView(evolution);

        TextView calendarHeader=Ui.sectionHeader(this,"カレンダー"); calendarHeader.setPadding(0,Ui.dp(this,26),0,Ui.dp(this,8)); body.addView(calendarHeader);
        LinearLayout monthRow=Ui.row(this); monthRow.setPadding(0,0,0,Ui.dp(this,4));
        Button prev=Ui.ghostButton(this,"‹"); prev.setTextSize(25); prev.setOnClickListener(v->{month=month.minusMonths(1);renderCalendar();}); monthRow.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,46)));
        monthTitle=Ui.text(this,"",18,Ui.TEXT); monthTitle.setTypeface(null,Typeface.BOLD); monthTitle.setGravity(Gravity.CENTER); monthRow.addView(monthTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        Button next=Ui.ghostButton(this,"›"); next.setTextSize(25); next.setOnClickListener(v->{month=month.plusMonths(1);renderCalendar();}); monthRow.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,46)));
        body.addView(monthRow); calendar=new GridLayout(this); calendar.setColumnCount(7); body.addView(calendar);
        monthSummary=Ui.text(this,"",13,Ui.MUTED); monthSummary.setPadding(0,Ui.dp(this,12),0,0); body.addView(monthSummary);
        TextView note=Ui.text(this,"成功した本番アラームの日だけ記録します。テストアラームはストリークに入りません。",12,Ui.MUTED); note.setPadding(0,Ui.dp(this,8),0,0); body.addView(note);
        setContentView(root); Ui.applySystemBarInsets(this,root);
    }

    private TextView addStatRow(String label){ LinearLayout row=Ui.row(this); row.setPadding(0,Ui.dp(this,13),0,Ui.dp(this,13)); TextView name=Ui.text(this,label,16,Ui.TEXT); row.addView(name,new LinearLayout.LayoutParams(0,-2,1)); TextView value=Ui.text(this,"-",18,Ui.TEXT); value.setTypeface(null,Typeface.BOLD); row.addView(value); body.addView(row); return value; }

    private void refresh(){ if(currentValue==null)return; int streak=StreakTracker.displayCurrent(this); currentValue.setText(streak+"日"); bestValue.setText(Prefs.bestStreak(this)+"日"); totalValue.setText(Prefs.totalWakeups(this)+"回"); flame.setStreak(streak); flameLevel.setText(flame.levelName()); flameHint.setText(nextGrowthText(streak)); renderCalendar(); }
    private String nextGrowthText(int s){ if(s<=0)return "最初の1日で火が灯る"; if(s<3)return "あと"+(3-s)+"日で成長"; if(s<7)return "あと"+(7-s)+"日でBLAZEへ"; if(s<14)return "あと"+(14-s)+"日でINFERNOへ"; if(s<30)return "あと"+(30-s)+"日でLEGENDへ"; return "伝説の炎を維持中"; }

    private void renderCalendar(){
        if(calendar==null)return; calendar.removeAllViews(); monthTitle.setText(month.format(DateTimeFormatter.ofPattern("yyyy年 M月",Locale.JAPAN)));
        String[] weekdays={"月","火","水","木","金","土","日"}; for(String w:weekdays){TextView t=Ui.text(this,w,12,Ui.MUTED);t.setGravity(Gravity.CENTER);addCalendarCell(t,34);}
        LocalDate first=month.atDay(1); int blanks=first.getDayOfWeek().getValue()-1; for(int i=0;i<blanks;i++)addCalendarCell(Ui.text(this,"",14,Ui.MUTED),52);
        LocalDate today=LocalDate.now(); int successCount=0;
        for(int d=1;d<=month.lengthOfMonth();d++){
            LocalDate date=month.atDay(d); boolean success=Prefs.wasSuccessful(this,date); if(success)successCount++;
            LinearLayout cell=new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
            TextView number=Ui.text(this,String.valueOf(d),15,success?Ui.SUCCESS:(date.equals(today)?Ui.ACCENT:Ui.TEXT)); number.setGravity(Gravity.CENTER); if(date.equals(today))number.setTypeface(null,Typeface.BOLD); cell.addView(number,new LinearLayout.LayoutParams(-1,Ui.dp(this,25)));
            TextView dot=Ui.text(this,success?"◆":"",9,success?Ui.ACCENT:Ui.SUCCESS); dot.setGravity(Gravity.CENTER); cell.addView(dot,new LinearLayout.LayoutParams(-1,Ui.dp(this,15))); addCalendarCell(cell,52);
        }
        int total=blanks+month.lengthOfMonth(), trailing=(7-(total%7))%7; for(int i=0;i<trailing;i++)addCalendarCell(Ui.text(this,"",14,Ui.MUTED),52);
        monthSummary.setText("この月の成功  "+successCount+"回");
    }

    private void addCalendarCell(View v,int heightDp){ GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=Ui.dp(this,heightDp); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); v.setLayoutParams(lp); calendar.addView(v); }
}
