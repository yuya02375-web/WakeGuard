from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v113.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"

stats = r'''package jp.wakeguard.alarm;

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
    private TextView currentValue,bestValue,totalValue,monthTitle,monthSummary;
    private GridLayout calendar;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        Ui.prepareActivity(this);
        buildUi();
        refresh();
    }

    @Override protected void onResume(){
        super.onResume();
        refresh();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout top=Ui.row(this);
        top.setPadding(Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,8));
        Button back=Ui.ghostButton(this,"‹");
        back.setTextSize(30);
        back.setOnClickListener(v->Ui.finishNoAnimation(this));
        top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView title=Ui.title(this,"起床記録",28);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top);

        ScrollView sv=new ScrollView(this);
        body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this,22),Ui.dp(this,6),Ui.dp(this,22),Ui.dp(this,36));
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        TextView streakHeader=Ui.sectionHeader(this,"ストリーク");
        body.addView(streakHeader);
        currentValue=addStatRow("現在のストリーク");
        body.addView(Ui.divider(this));
        bestValue=addStatRow("最高ストリーク");
        body.addView(Ui.divider(this));
        totalValue=addStatRow("成功した起床");
        body.addView(Ui.divider(this));

        TextView calendarHeader=Ui.sectionHeader(this,"カレンダー");
        calendarHeader.setPadding(0,Ui.dp(this,26),0,Ui.dp(this,8));
        body.addView(calendarHeader);

        LinearLayout monthRow=Ui.row(this);
        monthRow.setPadding(0,0,0,Ui.dp(this,4));
        Button prev=Ui.ghostButton(this,"‹");
        prev.setTextSize(25);
        prev.setOnClickListener(v->{month=month.minusMonths(1);renderCalendar();});
        monthRow.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,46)));
        monthTitle=Ui.text(this,"",18,Ui.TEXT);
        monthTitle.setTypeface(null,Typeface.BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        monthRow.addView(monthTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        Button next=Ui.ghostButton(this,"›");
        next.setTextSize(25);
        next.setOnClickListener(v->{month=month.plusMonths(1);renderCalendar();});
        monthRow.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,46)));
        body.addView(monthRow);

        calendar=new GridLayout(this);
        calendar.setColumnCount(7);
        body.addView(calendar);

        monthSummary=Ui.text(this,"",13,Ui.MUTED);
        monthSummary.setPadding(0,Ui.dp(this,12),0,0);
        body.addView(monthSummary);

        TextView note=Ui.text(this,"成功した本番アラームの日だけ印がつきます。テストアラームは記録しません。",12,Ui.MUTED);
        note.setPadding(0,Ui.dp(this,8),0,0);
        body.addView(note);

        setContentView(root);
        Ui.applySystemBarInsets(this,root);
    }

    private TextView addStatRow(String label){
        LinearLayout row=Ui.row(this);
        row.setPadding(0,Ui.dp(this,13),0,Ui.dp(this,13));
        TextView name=Ui.text(this,label,16,Ui.TEXT);
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView value=Ui.text(this,"-",18,Ui.TEXT);
        value.setTypeface(null,Typeface.BOLD);
        row.addView(value);
        body.addView(row);
        return value;
    }

    private void refresh(){
        if(currentValue==null)return;
        currentValue.setText(StreakTracker.displayCurrent(this)+"日");
        bestValue.setText(Prefs.bestStreak(this)+"日");
        totalValue.setText(Prefs.totalWakeups(this)+"回");
        renderCalendar();
    }

    private void renderCalendar(){
        if(calendar==null)return;
        calendar.removeAllViews();
        monthTitle.setText(month.format(DateTimeFormatter.ofPattern("yyyy年 M月",Locale.JAPAN)));

        String[] weekdays={"月","火","水","木","金","土","日"};
        for(String w:weekdays){
            TextView t=Ui.text(this,w,12,Ui.MUTED);
            t.setGravity(Gravity.CENTER);
            addCalendarCell(t,34);
        }

        LocalDate first=month.atDay(1);
        int blanks=first.getDayOfWeek().getValue()-1;
        for(int i=0;i<blanks;i++){
            TextView blank=Ui.text(this,"",14,Ui.MUTED);
            addCalendarCell(blank,52);
        }

        LocalDate today=LocalDate.now();
        int successCount=0;
        for(int d=1;d<=month.lengthOfMonth();d++){
            LocalDate date=month.atDay(d);
            boolean success=Prefs.wasSuccessful(this,date);
            if(success)successCount++;

            LinearLayout cell=new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            TextView number=Ui.text(this,String.valueOf(d),15,success?Ui.SUCCESS:(date.equals(today)?Ui.ACCENT:Ui.TEXT));
            number.setGravity(Gravity.CENTER);
            if(date.equals(today))number.setTypeface(null,Typeface.BOLD);
            cell.addView(number,new LinearLayout.LayoutParams(-1,Ui.dp(this,25)));

            TextView dot=Ui.text(this,success?"●":"",9,Ui.SUCCESS);
            dot.setGravity(Gravity.CENTER);
            cell.addView(dot,new LinearLayout.LayoutParams(-1,Ui.dp(this,15)));
            addCalendarCell(cell,52);
        }

        int total=blanks+month.lengthOfMonth();
        int trailing=(7-(total%7))%7;
        for(int i=0;i<trailing;i++){
            TextView blank=Ui.text(this,"",14,Ui.MUTED);
            addCalendarCell(blank,52);
        }

        monthSummary.setText("この月の成功  "+successCount+"回");
    }

    private void addCalendarCell(View v,int heightDp){
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
        lp.width=0;
        lp.height=Ui.dp(this,heightDp);
        lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        v.setLayoutParams(lp);
        calendar.addView(v);
    }
}
'''
(java/"StatsActivity.java").write_text(stats,encoding="utf-8")

p=java/"MainActivity.java"
s=p.read_text(encoding="utf-8")
s=s.replace('    private TextView nextText;','    private TextView nextText,statsText;')
s=s.replace(
'''        nextText=Ui.text(this,"",13,Ui.MUTED);nextText.setPadding(Ui.dp(this,22),0,Ui.dp(this,22),Ui.dp(this,10));root.addView(nextText);

        ScrollView sv=new ScrollView(this);''',
'''        nextText=Ui.text(this,"",13,Ui.MUTED);nextText.setPadding(Ui.dp(this,22),0,Ui.dp(this,22),Ui.dp(this,10));root.addView(nextText);

        LinearLayout statsRow=Ui.row(this);
        statsRow.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,16),Ui.dp(this,10));
        TextView statsLabel=Ui.text(this,"起床記録",15,Ui.TEXT);
        statsRow.addView(statsLabel,new LinearLayout.LayoutParams(0,-2,1));
        statsText=Ui.text(this,"",13,Ui.MUTED);
        statsRow.addView(statsText);
        TextView statsArrow=Ui.text(this,"  ›",22,Ui.MUTED);
        statsRow.addView(statsArrow);
        statsRow.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class)));
        root.addView(statsRow);
        root.addView(Ui.divider(this));

        ScrollView sv=new ScrollView(this);'''
)
old_render='''    private void render(){
        list.removeAllViews();List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);
        alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));
        if(alarms.isEmpty()){
            TextView empty=Ui.text(this,"アラームはありません。右上の＋から追加できます。",15,Ui.MUTED);empty.setPadding(0,Ui.dp(this,40),0,0);list.addView(empty);return;
        }
        for(int i=0;i<alarms.size();i++){addAlarmRow(alarms.get(i));if(i<alarms.size()-1)list.addView(Ui.divider(this));}
        refreshNext();
    }'''
new_render='''    private void render(){
        list.removeAllViews();
        refreshNext();
        refreshStats();
        List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);
        alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));
        if(alarms.isEmpty()){
            TextView empty=Ui.text(this,"アラームはありません。右上の＋から追加できます。",15,Ui.MUTED);empty.setPadding(0,Ui.dp(this,40),0,0);list.addView(empty);return;
        }
        for(int i=0;i<alarms.size();i++){addAlarmRow(alarms.get(i));if(i<alarms.size()-1)list.addView(Ui.divider(this));}
    }'''
if old_render not in s:
    raise SystemExit("MainActivity render block not found")
s=s.replace(old_render,new_render)
old_next='''    private void refreshNext(){long ms=AlarmScheduler.nextTriggerMillis(this);if(ms<=0){nextText.setText("次のアラームはありません");return;}ZonedDateTime z=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());nextText.setText("次は "+z.format(DateTimeFormatter.ofPattern("M月d日(E) H:mm",Locale.JAPAN)));}'''
new_next=old_next+'''\n    private void refreshStats(){if(statsText!=null)statsText.setText("連続 "+StreakTracker.displayCurrent(this)+"日  ·  最高 "+Prefs.bestStreak(this)+"日");}'''
if old_next not in s:
    raise SystemExit("MainActivity refreshNext block not found")
s=s.replace(old_next,new_next)
p.write_text(s,encoding="utf-8")

p=root/"app/src/main/AndroidManifest.xml"
s=p.read_text(encoding="utf-8")
needle='        <activity android:name=".SystemSettingsActivity" android:exported="false" />'
if needle not in s:
    raise SystemExit("manifest activity insertion point not found")
s=s.replace(needle,needle+'\n        <activity android:name=".StatsActivity" android:exported="false" />')
p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+','versionCode = 33',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.1.4"',s)
p.write_text(s,encoding="utf-8")

print("WakeGuard v1.1.4: wake streak + success calendar restored")