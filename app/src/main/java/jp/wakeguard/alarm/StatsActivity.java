package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Set;

public class StatsActivity extends Activity {
    private YearMonth month=YearMonth.now();
    private LinearLayout body;
    private LinearLayout recentWeek;
    private TextView currentValue,bestValue,totalValue,monthTitle,monthSummary,growthLevel,growthNext,protectionValue,protectionMetricValue;
    private GridLayout calendar;
    private StreakCompanionView companion;

    private Switch protectionAuto;
    private boolean updatingProtection=false;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        Ui.prepareActivity(this);
        if(getIntent()!=null&&"time".equals(getIntent().getStringExtra("record_mode"))){
            Ui.launchNoAnimation(this,new Intent(this,TimeLogActivity.class).putExtra("record_mode","time"));
            Ui.finishNoAnimation(this);
            return;
        }

        StreakGrowth.ensure(this);
        StreakProtection.ensure(this);
        buildUi();
        refresh();
    }

    @Override protected void onResume(){super.onResume();if(companion!=null)companion.onResume();refresh();}
    @Override protected void onPause(){if(companion!=null)companion.onPause();super.onPause();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.screenGradient(this));

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(Ui.dp(this,22),Ui.dp(this,15),Ui.dp(this,22),Ui.dp(this,10));
        top.addView(Ui.overline(this,"記録"));
        TextView title=Ui.title(this,"起床と時間の記録",27);
        title.setPadding(0,Ui.dp(this,3),0,0);
        top.addView(title);
        root.addView(top);

        addRecordSegment(root,true);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,28));
        scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(bottomNav());
        setContentView(root);
        Ui.applySystemBarInsets(this,root);

        addStreakCard();
        addMetrics();
        addProtectionCard();
        addCalendar();
    }

    private void addRecordSegment(LinearLayout parent,boolean streakSelected){
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setPadding(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4));
        shell.setBackground(Ui.roundStroke(Ui.BG_2,Ui.BORDER,18,this));
        shell.setElevation(0f);
        Button streak=segmentButton("起床",streakSelected);
        Button time=segmentButton("時間",!streakSelected);
        shell.addView(streak,new LinearLayout.LayoutParams(0,-2,1));
        shell.addView(time,new LinearLayout.LayoutParams(0,-2,1));
        streak.setOnClickListener(v->{if(!streakSelected)openRecords("streak");});
        time.setOnClickListener(v->{if(streakSelected)openRecords("time");});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(Ui.dp(this,18),0,Ui.dp(this,18),0);
        parent.addView(shell,lp);
    }

    private Button segmentButton(String label,boolean selected){
        Button b=new Button(this);
        b.setText(I18n.tr(this,label));
        b.setSelected(selected);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setIncludeFontPadding(false);
        b.setTypeface(Ui.designTypeface(selected?Typeface.BOLD:Typeface.NORMAL));
        b.setTextColor(selected?Ui.ACCENT_2:Ui.MUTED);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(this,46));
        b.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);
        b.setBackground(selected?Ui.roundStroke(Ui.SURFACE,Ui.withAlpha(Ui.ACCENT_2,0x55),15,this):Ui.round(0x00ffffff,15,this));
        if(android.os.Build.VERSION.SDK_INT>=21)b.setElevation(selected?Ui.dp(this,1):0);
        return b;
    }

    private void openRecords(String mode){
        Intent next;
        if("time".equals(mode))next=new Intent(this,TimeLogActivity.class).putExtra("record_mode","time");
        else next=new Intent(this,StatsActivity.class).putExtra("record_mode","streak");
        Ui.launchNoAnimation(this,next);
        Ui.finishNoAnimation(this);
    }

    private void addStreakCard(){
        LinearLayout card=Ui.card(this);
        card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,20,this));
        card.setPadding(Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16));
        int primary=Ui.readableTextColor(Ui.TEXT,Ui.SURFACE);
        int secondary=Ui.readableTextColor(Ui.MUTED,Ui.SURFACE);

        LinearLayout highlight=new LinearLayout(this);
        highlight.setGravity(Gravity.CENTER_VERTICAL);
        companion=new StreakCompanionView(this);
        companion.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout streakCopy=new LinearLayout(this);
        streakCopy.setOrientation(LinearLayout.VERTICAL);
        streakCopy.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=Ui.text(this,"現在のストリーク",13,secondary);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        streakCopy.addView(label);
        currentValue=Ui.title(this,"",42);
        currentValue.setTextColor(primary);
        streakCopy.addView(currentValue);
        LinearLayout.LayoutParams companionLp=new LinearLayout.LayoutParams(Ui.dp(this,88),Ui.dp(this,88));
        companionLp.setMarginEnd(Ui.dp(this,12));
        highlight.addView(companion,companionLp);
        highlight.addView(streakCopy,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(highlight);
        growthLevel=Ui.title(this,"",16);
        growthLevel.setTextColor(primary);
        card.addView(growthLevel,Ui.gapTop(this,5));
        growthNext=Ui.text(this,"",13,secondary);
        card.addView(growthNext,Ui.gapTop(this,3));

        View divider=new View(this);
        divider.setBackgroundColor(Ui.BORDER);
        LinearLayout.LayoutParams dividerLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,1));
        dividerLp.setMargins(0,Ui.dp(this,14),0,Ui.dp(this,10));
        card.addView(divider,dividerLp);
        TextView weekLabel=Ui.title(this,"直近7日",14);
        weekLabel.setTextColor(primary);
        weekLabel.setAccessibilityHeading(true);
        card.addView(weekLabel);
        recentWeek=new LinearLayout(this);
        recentWeek.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(recentWeek,Ui.gapTop(this,7));
        String legend="● "+I18n.tr(this,"成功")+"    ○ "+I18n.tr(this,"保護")+"    — "+I18n.tr(this,"記録なし");
        TextView weekLegend=Ui.text(this,legend,12,secondary);
        weekLegend.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(weekLegend,Ui.gapTop(this,7));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,Ui.dp(this,14));
        body.addView(card,lp);
    }

    private void addMetrics(){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(compactTextLayout()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        bestValue=addMetric(row,"最高","0日");
        totalValue=addMetric(row,"成功","0回");
        protectionMetricValue=addMetric(row,"保護","0 / 3");
        body.addView(row);
    }

    private TextView addMetric(LinearLayout row,String label,String value){
        LinearLayout cell=Ui.card(this);
        boolean stacked=row.getOrientation()==LinearLayout.VERTICAL;
        cell.setOrientation(stacked?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(Ui.dp(this,11),Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12));
        TextView name=Ui.text(this,label,12,Ui.MUTED);
        if(stacked)cell.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        else cell.addView(name);
        TextView number=Ui.title(this,value,18);
        number.setPadding(0,stacked?0:Ui.dp(this,4),0,0);
        cell.addView(number);
        LinearLayout.LayoutParams lp=stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1);
        if(stacked)lp.topMargin=Ui.dp(this,4);
        else lp.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);
        row.addView(cell,lp);
        return number;
    }

    private boolean compactTextLayout(){
        Configuration config=getResources().getConfiguration();
        return config.screenWidthDp/Math.max(1f,config.fontScale)<310f;
    }

    private void addProtectionCard(){
        TextView header=Ui.sectionHeader(this,"ストリーク保護");
        header.setPadding(Ui.dp(this,2),Ui.dp(this,23),0,Ui.dp(this,8));
        body.addView(header);

        LinearLayout card=Ui.card(this);
        LinearLayout balance=Ui.row(this);
        balance.setPadding(0,0,0,Ui.dp(this,8));
        TextView label=Ui.title(this,"保護日",16);
        balance.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        protectionValue=Ui.title(this,"0 / 3",19);
        protectionValue.setTextColor(Ui.ACCENT);
        balance.addView(protectionValue);
        card.addView(balance);

        protectionAuto=new Switch(this);
        protectionAuto.setText(I18n.tr(this,"自動で保護日を使う"));
        protectionAuto.setTextColor(Ui.TEXT);
        protectionAuto.setTextSize(15);
        protectionAuto.setMinHeight(Ui.dp(this,48));
        protectionAuto.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,4));
        protectionAuto.setOnCheckedChangeListener((button,checked)->{
            if(updatingProtection)return;
            StreakProtection.setAutoEnabled(this,checked);
            refreshProtection();
        });
        card.addView(protectionAuto,new LinearLayout.LayoutParams(-1,-2));
        TextView rule=Ui.text(this,I18n.tr(this,"毎月1日に2日分回復し、最大3日まで持てます。起きられなかった対象日に1日だけ自動使用してストリークを守ります。保護日はストリークを維持しますが、成功回数には加算されません。"),13,Ui.MUTED);
        rule.setPadding(0,Ui.dp(this,6),0,0);
        card.addView(rule);
        TextView bonus=Ui.text(this,"30日連続で実際に起床成功するごとに、保護日を1日追加します（最大3日）。",13,Ui.MUTED);
        bonus.setPadding(0,Ui.dp(this,7),0,0);
        card.addView(bonus);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        body.addView(card,lp);
    }

    private void addCalendar(){
        TextView header=Ui.sectionHeader(this,"カレンダー");
        header.setPadding(Ui.dp(this,2),Ui.dp(this,23),0,Ui.dp(this,7));
        body.addView(header);

        LinearLayout monthRow=new LinearLayout(this);
        monthRow.setGravity(Gravity.CENTER_VERTICAL);
        monthRow.setPadding(Ui.dp(this,3),0,Ui.dp(this,3),Ui.dp(this,4));
        Button previous=monthButton("‹","前の月");
        previous.setOnClickListener(v->{month=month.minusMonths(1);renderCalendar();});
        monthRow.addView(previous,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        monthTitle=Ui.title(this,"",19);
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setMinimumHeight(Ui.dp(this,48));
        monthRow.addView(monthTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button next=monthButton("›","次の月");
        next.setOnClickListener(v->{month=month.plusMonths(1);renderCalendar();});
        monthRow.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        body.addView(monthRow);

        LinearLayout card=Ui.card(this);
        card.setPadding(Ui.dp(this,8),Ui.dp(this,9),Ui.dp(this,8),Ui.dp(this,12));
        calendar=new GridLayout(this);
        calendar.setColumnCount(7);
        calendar.setUseDefaultMargins(false);
        card.addView(calendar,new LinearLayout.LayoutParams(-1,-2));
        monthSummary=Ui.text(this,"",13,Ui.MUTED);
        monthSummary.setPadding(Ui.dp(this,7),Ui.dp(this,9),0,0);
        card.addView(monthSummary);
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2);
        body.addView(card,clp);

        LinearLayout legend=new LinearLayout(this);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setPadding(Ui.dp(this,6),Ui.dp(this,8),Ui.dp(this,4),0);
        addLegendItem(legend,true,"成功");
        View gap=new View(this);
        legend.addView(gap,new LinearLayout.LayoutParams(Ui.dp(this,18),1));
        addLegendItem(legend,false,"保護");
        body.addView(legend);
        TextView note=Ui.text(this,I18n.tr(this,"起床に成功した本番アラームを記録します。保護日はストリークだけを維持し、テストアラームは記録しません。"),12,Ui.MUTED);
        note.setPadding(Ui.dp(this,6),Ui.dp(this,8),Ui.dp(this,6),0);
        body.addView(note);
    }

    private Button monthButton(String glyph,String description){
        Button b=new Button(this);
        b.setText(glyph);
        b.setTextSize(28);
        b.setTextColor(Ui.TEXT);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setContentDescription(I18n.tr(this,description));
        b.setBackground(Ui.round(Ui.SURFACE_2,16,this));
        return b;
    }

    private void addLegendItem(LinearLayout legend,boolean success,String label){
        CalendarMarker marker=new CalendarMarker(this,success?CalendarMarker.SUCCESS:CalendarMarker.PROTECTED);
        legend.addView(marker,new LinearLayout.LayoutParams(Ui.dp(this,12),Ui.dp(this,12)));
        TextView text=Ui.text(this,label,13,Ui.MUTED);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
        lp.setMargins(Ui.dp(this,6),0,0,0);
        legend.addView(text,lp);
    }

    private LinearLayout bottomNav(){
        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(Ui.dp(this,7),Ui.dp(this,5),Ui.dp(this,7),Ui.dp(this,7));
        nav.setBackground(Ui.navigationSurface(this));
        LinearLayout alarm=Ui.bottomTab(this,"アラーム",false);
        LinearLayout clock=Ui.bottomTab(this,"時計",false);
        LinearLayout records=Ui.bottomTab(this,"記録",true);
        LinearLayout settings=Ui.bottomTab(this,"設定",false);
        nav.addView(alarm,new LinearLayout.LayoutParams(0,-2,1));
        nav.addView(clock,new LinearLayout.LayoutParams(0,-2,1));
        nav.addView(records,new LinearLayout.LayoutParams(0,-2,1));
        nav.addView(settings,new LinearLayout.LayoutParams(0,-2,1));
        alarm.setOnClickListener(v->navigateRoot("alarm"));
        clock.setOnClickListener(v->navigateRoot("clock"));
        settings.setOnClickListener(v->navigateRoot("settings"));
        return nav;
    }

    private void navigateRoot(String destination){
        Intent next=null;
        if("alarm".equals(destination))next=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        else if("clock".equals(destination))next=new Intent(this,ClockActivity.class).putExtra("mode","world").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        else if("settings".equals(destination))next=new Intent(this,SystemSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(next!=null)Ui.launchNoAnimation(this,next);
        Ui.finishNoAnimation(this);
    }

    @Override public void onBackPressed(){navigateRoot("alarm");}

    private void refresh(){
        if(currentValue==null)return;
        StreakGrowth.ensure(this);
        StreakProtection.ensure(this);
        int streak=StreakTracker.displayCurrent(this);
        long level=StreakGrowth.level(this);
        String count=String.valueOf(streak);
        String unit=("en".equals(I18n.language(this))?" ":"")+streakUnitLabel(streak);
        SpannableString days=new SpannableString(count+unit);
        days.setSpan(new RelativeSizeSpan(.48f),count.length(),days.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        currentValue.setText(days);
        currentValue.setContentDescription(I18n.tr(this,"現在のストリーク")+" "+days);
        bestValue.setText(I18n.tr(this,Prefs.bestStreak(this)+"日"));
        totalValue.setText(I18n.tr(this,Prefs.totalWakeups(this)+"回"));
        growthLevel.setText(StreakGrowth.growthDescriptor(this,level)+"  ·  Lv."+level);
        long next=StreakGrowth.nextMilestone(level);
        if(next==0L){
            growthNext.setText(I18n.tr(this,"最終段階で成長中"));
        }else{
            String nextName=StreakGrowth.growthDescriptor(this,next);
            long remaining=next-level;
            String key=remaining==1L?"次の起床成功で%1$sへ":"あと%2$d回の起床成功で%1$sへ";
            growthNext.setText(String.format(I18n.locale(this),I18n.tr(this,key),nextName,remaining));
        }
        companion.setGrowth(level,streak);
        renderRecentWeek();
        refreshProtection();
        renderCalendar();
    }

    private void renderRecentWeek(){
        if(recentWeek==null)return;
        recentWeek.removeAllViews();
        LocalDate today=LocalDate.now();
        Set<String> protectedDays=StreakProtection.protectedDays(this);
        TextStyle weekdayStyle=compactTextLayout()?TextStyle.NARROW:TextStyle.SHORT;
        DateTimeFormatter dateFormat=DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this));
        for(int offset=6;offset>=0;offset--){
            LocalDate date=today.minusDays(offset);
            boolean success=Prefs.wasSuccessful(this,date);
            boolean protectedDay=!success&&protectedDays.contains(date.toString());
            LinearLayout cell=new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setMinimumHeight(Ui.dp(this,56));
            cell.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,5));
            boolean isToday=date.equals(today);
            int surface=isToday?Ui.SURFACE_2:Ui.SURFACE;
            if(isToday)cell.setBackground(Ui.roundStroke(surface,Ui.ACCENT,11,this));
            int primary=Ui.readableTextColor(Ui.TEXT,surface);
            int secondary=Ui.readableTextColor(Ui.MUTED,surface);
            TextView weekday=Ui.text(this,date.getDayOfWeek().getDisplayName(weekdayStyle,I18n.locale(this)),11,secondary);
            weekday.setGravity(Gravity.CENTER);
            weekday.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(weekday,new LinearLayout.LayoutParams(-1,-2));
            TextView number=Ui.text(this,String.valueOf(date.getDayOfMonth()),14,primary);
            number.setGravity(Gravity.CENTER);
            number.setTypeface(Ui.designTypeface(isToday?Typeface.BOLD:Typeface.NORMAL));
            number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(number,new LinearLayout.LayoutParams(-1,-2));

            int kind=success?CalendarMarker.SUCCESS:(protectedDay?CalendarMarker.PROTECTED:CalendarMarker.NO_RECORD);
            CalendarMarker marker=new CalendarMarker(this,kind);
            LinearLayout.LayoutParams markerParams=new LinearLayout.LayoutParams(Ui.dp(this,12),Ui.dp(this,12));
            markerParams.topMargin=Ui.dp(this,3);
            cell.addView(marker,markerParams);
            cell.setContentDescription(dateDescription(date,today,success,protectedDay,dateFormat));
            cell.setScreenReaderFocusable(true);
            recentWeek.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        }
    }

    private String streakUnitLabel(int streak) {
        String language = I18n.language(this);
        if ("en".equals(language)) return streak == 1 ? "day" : "days";
        return "ko".equals(language) ? "일" : "日";
    }

    private String dateDescription(LocalDate date,LocalDate today,boolean success,boolean protectedDay,DateTimeFormatter format){
        String description=date.format(format);
        if(date.equals(today))description+=", "+I18n.tr(this,"今日");
        if(success||protectedDay||!date.isAfter(today)){
            description+=", "+I18n.tr(this,success?"成功":(protectedDay?"保護":"記録なし"));
        }
        return description;
    }
    private void refreshProtection(){
        if(protectionValue==null||protectionMetricValue==null)return;
        String available=StreakProtection.available(this)+" / "+StreakProtection.MAX_DAYS;
        protectionValue.setText(available);
        protectionMetricValue.setText(available);
        if(protectionAuto!=null){updatingProtection=true;protectionAuto.setChecked(StreakProtection.autoEnabled(this));updatingProtection=false;}
    }

    private void renderCalendar(){
        if(calendar==null)return;
        calendar.removeAllViews();
        monthTitle.setText(month.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"month"),I18n.locale(this))));
        TextStyle weekdayStyle=compactTextLayout()?TextStyle.NARROW:TextStyle.SHORT;
        for(DayOfWeek weekday:DayOfWeek.values()){
            TextView label=Ui.text(this,weekday.getDisplayName(weekdayStyle,I18n.locale(this)),12,Ui.MUTED);
            label.setGravity(Gravity.CENTER);
            addCalendarCell(label,34);
        }
        LocalDate first=month.atDay(1);
        int blanks=first.getDayOfWeek().getValue()-1;
        for(int i=0;i<blanks;i++)addCalendarCell(new View(this),52);

        LocalDate today=LocalDate.now();
        int successCount=0,protectedCount=0;
        DateTimeFormatter dateFormat=DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this));
        Set<String> protectedDays=StreakProtection.protectedDays(this);
        for(int day=1;day<=month.lengthOfMonth();day++){
            LocalDate date=month.atDay(day);
            boolean success=Prefs.wasSuccessful(this,date);
            boolean protectedDay=!success&&protectedDays.contains(date.toString());
            if(success)successCount++;
            if(protectedDay)protectedCount++;
            addDateCell(date,today,success,protectedDay,dateFormat);
        }
        int total=blanks+month.lengthOfMonth();
        int trailing=(7-(total%7))%7;
        for(int i=0;i<trailing;i++)addCalendarCell(new View(this),52);
        monthSummary.setText(I18n.tr(this,"この月の成功")+"  "+successCount+"  ·  "+I18n.tr(this,"保護")+"  "+protectedCount);
    }

    private void addDateCell(LocalDate date,LocalDate today,boolean success,boolean protectedDay,DateTimeFormatter dateFormat){
        FrameLayout frame=new FrameLayout(this);
        frame.setBackground(date.equals(today)?Ui.roundStroke(Ui.SURFACE,Ui.ACCENT,11,this):Ui.round(0x00ffffff,11,this));
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        TextView number=Ui.text(this,String.valueOf(date.getDayOfMonth()),15,success?Ui.ACCENT_2:(protectedDay?Ui.ACCENT:Ui.TEXT));
        number.setGravity(Gravity.CENTER);
        if(date.equals(today))number.setTypeface(null,Typeface.BOLD);
        content.addView(number,new LinearLayout.LayoutParams(-1,-2));
        CalendarMarker marker=new CalendarMarker(this,success?CalendarMarker.SUCCESS:(protectedDay?CalendarMarker.PROTECTED:CalendarMarker.NONE));
        LinearLayout.LayoutParams markerLp=new LinearLayout.LayoutParams(Ui.dp(this,10),Ui.dp(this,10));
        markerLp.topMargin=Ui.dp(this,1);
        content.addView(marker,markerLp);
        frame.addView(content,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        frame.setContentDescription(dateDescription(date,today,success,protectedDay,dateFormat));
        frame.setScreenReaderFocusable(true);
        addCalendarCell(frame,52);
    }

    private void addCalendarCell(View view,int heightDp){
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
        lp.width=0;
        lp.height=GridLayout.LayoutParams.WRAP_CONTENT;
        view.setMinimumHeight(Ui.dp(this,heightDp));
        lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        view.setLayoutParams(lp);
        calendar.addView(view);
    }

    private static final class CalendarMarker extends View {
        static final int NONE=0, SUCCESS=1, PROTECTED=2, NO_RECORD=3;
        private final int kind;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        CalendarMarker(Context context,int kind){super(context);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(kind==NONE)return;
            float radius=Math.min(getWidth(),getHeight())*.34f;
            int color=kind==SUCCESS?Ui.ACCENT_2:(kind==PROTECTED?Ui.ACCENT:Ui.MUTED);
            paint.setColor(Ui.readableTextColor(color,Ui.SURFACE));
            paint.setStyle(kind==SUCCESS?Paint.Style.FILL:Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f,getResources().getDisplayMetrics().density*1.5f));
            if(kind==NO_RECORD){
                float x=getWidth()/2f,y=getHeight()/2f;
                canvas.drawLine(x-radius,y,x+radius,y,paint);
                return;
            }
            canvas.drawCircle(getWidth()/2f,getHeight()/2f,radius,paint);
        }
    }
}
