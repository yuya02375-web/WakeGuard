package jp.wakeguard.alarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private String builtAppearanceKey;
    private LinearLayout list;
    private LinearLayout addAction;
    private android.widget.GridLayout tileGrid;
    private TextView nextText,nextMeta;
    private FrameLayout nextCard;
    private android.graphics.drawable.Drawable scheduledHeroBackground, emptyHeroBackground;
    private LinearLayout heroContent, emptyHero;
    private TextView emptyHeading, emptyNote;
    private View sunriseAccent;
    private StreakCompanionView dragonCompanion;
    private TextView dragonFormText,dragonLevelText,dragonProgressText;
    private FrameLayout dragonProgressTrack;
    private View dragonProgressFill;

    private FrameLayout horizonTrack;
    private View horizonDot;
    private final Handler countdownHandler=new Handler(Looper.getMainLooper());
    private final Runnable countdownTick=new Runnable(){@Override public void run(){render();countdownHandler.postDelayed(this,30000L);}};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this); builtAppearanceKey=appearanceKey(); AlarmService.ensureNotificationChannel(this);
        buildUi(); requestRuntimePermissions(); AlarmScheduler.reschedule(this);
    }
    @Override protected void onResume(){
        super.onResume();
        String currentAppearance=appearanceKey();
        if(builtAppearanceKey!=null&&!builtAppearanceKey.equals(currentAppearance)){recreate();return;}
        if(dragonCompanion!=null)dragonCompanion.onResume();
        AlarmScheduler.reschedule(this);
        for(TimerStore.Entry e:TimerStore.all(this))if(e.running&&e.remaining()>1500L)TimerReceiver.schedule(this,e.id,e.endMs);
        render();countdownHandler.removeCallbacks(countdownTick);countdownHandler.postDelayed(countdownTick,30000L);
    }
    @Override protected void onPause(){countdownHandler.removeCallbacks(countdownTick);if(dragonCompanion!=null)dragonCompanion.onPause();super.onPause();}

    private String appearanceKey(){
        return UiProfiles.id(this)+":"+VisualDesigns.id(this)+":"+AppThemes.key(this);
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(Ui.screenGradient(this));
        // Keep the fixed hero, scrolling list, and footer inside their own viewports.
        // Allowing overflow here let the list paint through the hero and footer while scrolling.
        root.setClipChildren(true);root.setClipToPadding(true);

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,5));
        LinearLayout brandLine=new LinearLayout(this);brandLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=Ui.overline(this,"IGNIDO  /  WAKE");
        brandLine.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        String today=LocalDate.now().format(DateTimeFormatter.ofPattern("M/d · EEE",I18n.locale(this)));
        TextView date=Ui.text(this,today,11,Ui.MUTED_2);date.setGravity(Gravity.CENTER);
        date.setSingleLine(true);date.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,10),Ui.dp(this,5));
        date.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,11,this));
        brandLine.addView(date,new LinearLayout.LayoutParams(-2,-2));top.addView(brandLine);
        TextView title=Ui.title(this,"アラーム",HomeMetrics.HEADER_TITLE_SP);
        title.setPadding(0,Ui.dp(this,3),0,0);top.addView(title);root.addView(top);

        nextCard=new FrameLayout(this);nextCard.setClipChildren(true);nextCard.setClipToPadding(true);
        nextCard.setMinimumHeight(Ui.dp(this,HomeMetrics.HERO_HEIGHT_DP));
        scheduledHeroBackground=Ui.gradientRound(this,24,Ui.HERO_START,Ui.HERO_END);
        emptyHeroBackground=Ui.gradientRoundPlain(this,24,Ui.HERO_START,Ui.HERO_END);
        nextCard.setBackground(scheduledHeroBackground);
        if ("none".equals(Ui.DESIGN_PATTERN) || "rules".equals(Ui.DESIGN_PATTERN)) {
            sunriseAccent=new SunriseAccentView(this);
            nextCard.addView(sunriseAccent,
                    new FrameLayout.LayoutParams(Ui.dp(this,136),Ui.dp(this,HomeMetrics.HERO_HEIGHT_DP),
                            Gravity.END|Gravity.CENTER_VERTICAL));
        }
        View edge=new View(this);edge.setBackgroundColor(Ui.withAlpha(Ui.HERO_TEXT,31));
        FrameLayout.LayoutParams edgeLp=new FrameLayout.LayoutParams(-1,Ui.dp(this,1),Gravity.TOP);
        edgeLp.setMargins(Ui.dp(this,HomeMetrics.HERO_SIDE_DP),0,Ui.dp(this,HomeMetrics.HERO_SIDE_DP),0);
        nextCard.addView(edge,edgeLp);
        heroContent=new LinearLayout(this);heroContent.setOrientation(LinearLayout.VERTICAL);
        heroContent.setPadding(Ui.dp(this,HomeMetrics.HERO_SIDE_DP),Ui.dp(this,HomeMetrics.HERO_TOP_DP),
                Ui.dp(this,136),Ui.dp(this,11));
        TextView nextLabel=Ui.overline(this,"次の起床");nextLabel.setTextColor(Ui.HERO_TEXT);heroContent.addView(nextLabel);
        nextText=Ui.title(this,"--:--",HomeMetrics.HERO_TIME_SP);nextText.setTextColor(Ui.HERO_TEXT);
        nextText.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));
        nextText.setIncludeFontPadding(false);
        nextText.setAutoSizeTextTypeUniformWithConfiguration(34,HomeMetrics.HERO_TIME_SP,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        nextText.setPadding(0,Ui.dp(this,2),0,0);
        heroContent.addView(nextText,new LinearLayout.LayoutParams(-2,-2));
        nextMeta=Ui.text(this,"次のアラームはありません",13,Ui.HERO_TEXT);
        nextMeta.setSingleLine(true);nextMeta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroContent.addView(nextMeta);
        horizonTrack=new FrameLayout(this);horizonTrack.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        View horizonLine=new View(this);horizonLine.setBackgroundColor(Ui.HORIZON_LINE);
        FrameLayout.LayoutParams lineLp=new FrameLayout.LayoutParams(-1,Ui.dp(this,1),Gravity.CENTER_VERTICAL);
        horizonTrack.addView(horizonLine,lineLp);
        horizonDot=new View(this);horizonDot.setBackground(Ui.round(Ui.HERO_TEXT,8,this));
        FrameLayout.LayoutParams dotLp=new FrameLayout.LayoutParams(Ui.dp(this,8),Ui.dp(this,8),Gravity.START|Gravity.CENTER_VERTICAL);
        horizonTrack.addView(horizonDot,dotLp);
        LinearLayout.LayoutParams trackLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,10));
        trackLp.topMargin=Ui.dp(this,4);heroContent.addView(horizonTrack,trackLp);
        nextCard.addView(heroContent,new FrameLayout.LayoutParams(-1,-2));
        emptyHero=buildEmptyContent(true);
        emptyHero.setMinimumHeight(Ui.dp(this,HomeMetrics.HERO_EMPTY_HEIGHT_DP));
        emptyHero.setVisibility(View.GONE);
        nextCard.addView(emptyHero,new FrameLayout.LayoutParams(-1,-2));
        addDragonGrowthOverlay();
        LinearLayout.LayoutParams nextLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,HomeMetrics.HERO_HEIGHT_DP));
        nextLp.setMargins(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,3));root.addView(nextCard,nextLp);

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(true);sv.setClipToPadding(true);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        // Leave a little breathing room after the final alarm; the ScrollView clips its
        // own contents before the fixed add action and navigation are drawn.
        list.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),Ui.dp(this,24));sv.addView(list);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        Button add=Ui.button(this,"アラームを追加",true);
        android.graphics.drawable.Drawable addIcon=getDrawable(R.drawable.ic_v292_action_add).mutate();
        addIcon.setTint(Ui.ON_ACCENT);int addIconSize=Ui.dp(this,20);
        addIcon.setBounds(0,0,addIconSize,addIconSize);
        add.setCompoundDrawablePadding(Ui.dp(this,8));add.setCompoundDrawables(addIcon,null,null,null);
        add.setOnClickListener(v->edit(-1));
        addAction=new LinearLayout(this);addAction.setPadding(Ui.dp(this,18),Ui.dp(this,5),Ui.dp(this,18),Ui.dp(this,5));
        addAction.addView(add,new LinearLayout.LayoutParams(-1,-2));root.addView(addAction);
        root.addView(bottomNav());
        setContentView(root);
        Ui.applySystemBarInsets(this,root);
    }

    private LinearLayout bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,9),Ui.dp(this,2),Ui.dp(this,9),Ui.dp(this,2));nav.setBackground(Ui.navigationSurface(this));
        LinearLayout alarms=Ui.bottomTab(this,"アラーム",true);LinearLayout clock=Ui.bottomTab(this,"時計",false);LinearLayout records=Ui.bottomTab(this,"記録",false);LinearLayout settings=Ui.bottomTab(this,"設定",false);
        nav.addView(alarms,new LinearLayout.LayoutParams(0,Ui.dp(this,HomeMetrics.NAV_TAB_HEIGHT_DP),1));nav.addView(clock,new LinearLayout.LayoutParams(0,Ui.dp(this,HomeMetrics.NAV_TAB_HEIGHT_DP),1));nav.addView(records,new LinearLayout.LayoutParams(0,Ui.dp(this,HomeMetrics.NAV_TAB_HEIGHT_DP),1));nav.addView(settings,new LinearLayout.LayoutParams(0,Ui.dp(this,HomeMetrics.NAV_TAB_HEIGHT_DP),1));
        clock.setOnClickListener(v->openClock("world"));records.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class)));settings.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,SystemSettingsActivity.class)));return nav;
    }

    private void openClock(String mode){
        Ui.launchNoAnimation(this, new Intent(this,ClockActivity.class).putExtra("mode",mode));
    }
    private void edit(long id){startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",id));}

    private void render(){
        list.removeAllViews();
        list.setGravity(Gravity.TOP);
        list.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),Ui.dp(this,24));
        boolean hasNext=refreshNext();
        List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);
        alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));
        int enabled=0;for(AlarmStore.Entry e:alarms)if(e.enabled)enabled++;
        boolean noUpcomingAlarm=!hasNext;
        addAction.setVisibility(alarms.isEmpty()?View.GONE:View.VISIBLE);
        showEmptyHero(noUpcomingAlarm);
        refreshDragonGrowth();
        if(noUpcomingAlarm)updateEmptyHero(alarms.isEmpty(),enabled==0);
        if(alarms.isEmpty()){
            list.setGravity(Gravity.CENTER);
            list.setPadding(Ui.dp(this,18),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,18));
            list.addView(buildEmptyContent(false),new LinearLayout.LayoutParams(-1,-2));
            return;
        }
        LinearLayout listHeading=Ui.row(this);
        listHeading.setPadding(Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,3));
        TextView listTitle=Ui.sectionHeader(this,I18n.tr(this,"アラーム一覧"));
        listTitle.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,5));
        listHeading.addView(listTitle,new LinearLayout.LayoutParams(0,-2,1));
        listHeading.addView(Ui.pill(this,I18n.tr(this,"有効なアラーム")+"  "+enabled));list.addView(listHeading);
        Map<Long,Long> snoozes=SnoozeScheduler.active(this);
        if(snoozes.size()>1){
            LinearLayout allSnoozes=Ui.row(this);allSnoozes.setGravity(Gravity.CENTER_VERTICAL);
            allSnoozes.setPadding(Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8));
            String countText=String.format(I18n.locale(this),I18n.tr(this,"スヌーズ中  %d件"),snoozes.size());
            TextView count=Ui.text(this,countText,14,Ui.TEXT);
            allSnoozes.addView(count,new LinearLayout.LayoutParams(0,-2,1));
            Button cancelAll=Ui.ghostButton(this,I18n.tr(this,"すべて取消"));
            cancelAll.setTextColor(Ui.ACCENT_2);
            cancelAll.setOnClickListener(v->{SnoozeScheduler.cancelAll(this);render();});
            allSnoozes.addView(cancelAll);
            list.addView(allSnoozes);list.addView(Ui.divider(this));
        }
        if("tiles".equals(Ui.LAYOUT_MODE)){
            tileGrid=new android.widget.GridLayout(this);tileGrid.setColumnCount(2);tileGrid.setUseDefaultMargins(false);
            tileGrid.setPadding(0,Ui.dp(this,2),0,0);
            list.addView(tileGrid,new LinearLayout.LayoutParams(-1,-2));
            for(int i=0;i<alarms.size();i++)addAlarmTile(alarms.get(i),i);
            return;
        }
        tileGrid=null;
        for(AlarmStore.Entry e:alarms)addAlarmRow(e);
    }

    private LinearLayout buildEmptyContent(boolean onHero){
        LinearLayout content=onHero?new LinearLayout(this):Ui.card(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(onHero?Gravity.CENTER_VERTICAL:Gravity.CENTER);
        content.setPadding(Ui.dp(this,onHero?16:20),Ui.dp(this,onHero?10:22),
                Ui.dp(this,onHero?136:20),Ui.dp(this,onHero?10:20));
        if(onHero)content.setMinimumHeight(Ui.dp(this,HomeMetrics.HERO_EMPTY_HEIGHT_DP));
        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        if(!onHero)copy.setGravity(Gravity.CENTER_HORIZONTAL);
        if(!onHero){
            FrameLayout iconPlate=new FrameLayout(this);
            iconPlate.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,20,this));
            android.widget.ImageView icon=new android.widget.ImageView(this);
            android.graphics.drawable.Drawable alarmIcon=getDrawable(R.drawable.ic_v292_nav_alarm_outline).mutate();
            alarmIcon.setTint(Ui.ACCENT_2);icon.setImageDrawable(alarmIcon);
            int iconSize=Ui.dp(this,28);
            iconPlate.addView(icon,new FrameLayout.LayoutParams(iconSize,iconSize,Gravity.CENTER));
            LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,56));
            iconLp.bottomMargin=Ui.dp(this,14);content.addView(iconPlate,iconLp);
        }
        content.addView(copy,new LinearLayout.LayoutParams(-1,-2));
        TextView heading=Ui.title(this,localized(
                onHero?"最初のアラームを追加しましょう":"アラームを設定しましょう",
                onHero?"Add your first alarm":"Set up your alarm",
                onHero?"첫 알람을 추가해 보세요":"알람을 설정해 보세요"),onHero?16:19);
        if(onHero)emptyHeading=heading;
        heading.setTextColor(onHero?Ui.HERO_TEXT:Ui.TEXT);
        heading.setMaxLines(2);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if(!onHero)heading.setGravity(Gravity.CENTER);
        copy.addView(heading);
        TextView note=Ui.text(this,localized(
                onHero?"時刻と曜日を決めると、ここに一覧で表示されます。":"起床する時刻と曜日を決めると、ここに表示されます。",
                onHero?"Set a time and days to see it listed here.":"Choose a wake-up time and days to see it here.",
                onHero?"시간과 요일을 정하면 여기에 목록으로 표시됩니다.":"기상 시간과 요일을 정하면 여기에 표시됩니다."),onHero?12:13,onHero?Ui.HERO_TEXT:Ui.MUTED);
        if(onHero)emptyNote=note;
        note.setMaxLines(2);
        note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        note.setPadding(0,Ui.dp(this,3),0,0);
        if(onHero)note.setAlpha(.82f);
        else note.setGravity(Gravity.CENTER);
        copy.addView(note);
        if(!onHero){
            Button create=Ui.button(this,localized("最初のアラームを作成","Create your first alarm","첫 알람 만들기"),true);
            create.setOnClickListener(v->edit(-1));
            LinearLayout.LayoutParams createLp=new LinearLayout.LayoutParams(-1,-2);
            createLp.topMargin=Ui.dp(this,18);content.addView(create,createLp);
        }
        return content;
    }

    private void updateEmptyHero(boolean noSavedAlarms,boolean allAlarmsDisabled){
        if(emptyHeading==null||emptyNote==null)return;
        if(noSavedAlarms){
            emptyHeading.setText(localized(
                    "次の起床予定はありません",
                    "No upcoming wake-up",
                    "예정된 기상 시간이 없습니다"));
            emptyNote.setText(localized(
                    "中央のボタンから、最初のアラームを作成できます。",
                    "Create your first alarm using the button below.",
                    "아래 버튼으로 첫 알람을 만들 수 있습니다."));
        }else if(allAlarmsDisabled){
            emptyHeading.setText(localized(
                    "アラームがオフです",
                    "Alarms are off",
                    "알람이 꺼져 있습니다"));
            emptyNote.setText(localized(
                    "一覧のスイッチをオンにすると、次の時刻がここに表示されます。",
                    "Turn on an alarm below to see the next wake-up time here.",
                    "아래에서 알람을 켜면 다음 기상 시간이 여기에 표시됩니다."));
        }else{
            emptyHeading.setText(localized(
                    "次の起床予定がありません",
                    "No upcoming alarm",
                    "예정된 알람이 없습니다"));
            emptyNote.setText(localized(
                    "一覧でアラームの時刻や曜日を確認してください。",
                    "Check the alarm times and days in the list below.",
                    "아래 목록에서 알람 시간과 요일을 확인하세요."));
        }
    }

    private String localized(String ja,String en,String ko){
        String language=I18n.language(this);
        return "ko".equals(language)?ko:"en".equals(language)?en:ja;
    }

    private void addAlarmTile(AlarmStore.Entry e,int index){
        LinearLayout tile=Ui.card(this);tile.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10));
        tile.setMinimumHeight(Ui.dp(this,HomeMetrics.TILE_MIN_HEIGHT_DP));tile.setClickable(true);tile.setFocusable(true);tile.setForeground(Ui.rippleForeground(this,18));tile.setOnClickListener(v->edit(e.id));
        if(e.id>=1000)tile.setOnLongClickListener(v->{confirmDelete(e);return true;});
        LinearLayout head=Ui.row(this);head.setPadding(0,0,0,Ui.dp(this,4));
        TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),28);
        time.setTextColor(e.enabled?Ui.ACCENT_2:Ui.MUTED_2);time.setIncludeFontPadding(false);head.addView(time,new LinearLayout.LayoutParams(0,-2,1));
        Switch on=new Switch(this);on.setChecked(e.enabled);Ui.styleSwitch(on,this);head.addView(on);tile.addView(head);
        String label=(e.label==null||e.label.trim().isEmpty())?I18n.tr(this,"アラーム"):e.label.trim();
        TextView name=Ui.title(this,label,15);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(name);
        TextView schedule=Ui.text(this,repeatText(e.dayMask),12,e.enabled?Ui.ACCENT:Ui.MUTED);schedule.setSingleLine(true);schedule.setEllipsize(android.text.TextUtils.TruncateAt.END);schedule.setPadding(0,Ui.dp(this,4),0,0);tile.addView(schedule);
        TextView mission=Ui.text(this,AlarmProfiles.missionIcon(e.missionType)+"  "+I18n.tr(this,AlarmProfiles.missionName(e.missionType)),12,Ui.MUTED);mission.setSingleLine(true);mission.setEllipsize(android.text.TextUtils.TruncateAt.END);mission.setPadding(0,Ui.dp(this,6),0,0);tile.addView(mission);
        long snoozeAt=SnoozeScheduler.scheduledAt(this,e.id);
        if(snoozeAt>System.currentTimeMillis()){
            TextView snooze=Ui.text(this,I18n.tr(this,"スヌーズ中")+"  ·  "+relativeText(snoozeAt-System.currentTimeMillis()),11,Ui.ACCENT_2);
            snooze.setSingleLine(true);snooze.setEllipsize(android.text.TextUtils.TruncateAt.END);snooze.setPadding(0,Ui.dp(this,4),0,0);tile.addView(snooze);
        }
        on.setContentDescription(I18n.tr(this,label)+"  "+I18n.tr(this,"アラーム"));
        on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled){SnoozeScheduler.cancel(this,e.id);if(e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);}AlarmScheduler.reschedule(this);render();});
        android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(index/2),android.widget.GridLayout.spec(index%2,1f));
        lp.width=0;lp.height=-2;lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,8));tileGrid.addView(tile,lp);
    }

    private void addAlarmRow(AlarmStore.Entry e){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,12));
        row.setBackground(Ui.alarmCard(this,e.enabled));
        if(Build.VERSION.SDK_INT>=21)row.setElevation(Ui.cardElevationPx(this));
        row.setOnClickListener(v->edit(e.id));
        if(e.id>=1000)row.setOnLongClickListener(v->{confirmDelete(e);return true;});

        LinearLayout first=new LinearLayout(this);first.setGravity(Gravity.CENTER_VERTICAL);
        TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),HomeMetrics.ALARM_TIME_SP);
        time.setTextColor(e.enabled?Ui.ACCENT_2:Ui.MUTED_2);
        time.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        android.graphics.drawable.GradientDrawable statusDot=new android.graphics.drawable.GradientDrawable();
        statusDot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        statusDot.setColor(e.enabled?Ui.ACCENT_2:Ui.MUTED_2);
        int statusDotSize=Ui.dp(this,6);statusDot.setBounds(0,0,statusDotSize,statusDotSize);
        time.setCompoundDrawablePadding(Ui.dp(this,8));time.setCompoundDrawables(statusDot,null,null,null);
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(Ui.dp(this,12),0,Ui.dp(this,5),0);
        String label=(e.label==null||e.label.trim().isEmpty())?"アラーム":e.label.trim();
        TextView name=Ui.title(this,label,15);
        name.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD));
        name.setTextColor(e.enabled?Ui.TEXT:Ui.MUTED);
        name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(name);
        String scheduleText=repeatText(e.dayMask);
        long now=System.currentTimeMillis();
        long next=AlarmScheduler.nextTriggerMillis(this,e);
        String remaining=(e.enabled&&next>now)?String.format(I18n.locale(this),I18n.tr(this,"あと %s"),relativeText(next-now)):"";
        if(!remaining.isEmpty()){
            TextView countdown=Ui.text(this,remaining,11,Ui.ACCENT_2);
            countdown.setSingleLine(true);countdown.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams countdownLp=new LinearLayout.LayoutParams(-1,-2);
            countdownLp.topMargin=Ui.dp(this,2);details.addView(countdown,countdownLp);
        }
        TextView schedule=Ui.text(this,scheduleText,11.5f,e.enabled?Ui.ACCENT_2:Ui.MUTED_2);
        schedule.setSingleLine(true);schedule.setEllipsize(android.text.TextUtils.TruncateAt.END);
        schedule.setMaxWidth(Ui.dp(this,260));
        schedule.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4));
        schedule.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,9,this));
        first.addView(time,new LinearLayout.LayoutParams(-2,-2));
        first.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        Switch on=new Switch(this);on.setChecked(e.enabled);Ui.styleSwitch(on,this);
        on.setContentDescription(I18n.tr(this,label)+"  "+I18n.tr(this,"アラーム"));
        if(e.id>=1000){
            Button del=Ui.ghostButton(this,I18n.tr(this,"削除"));
            del.setTextSize(11);del.setTextColor(Ui.DANGER);
            del.setPadding(0,0,0,0);
            del.setMinWidth(Math.round(48f*getResources().getDisplayMetrics().density));
            del.setContentDescription(I18n.tr(this,"アラームを削除"));
            del.setOnClickListener(v->confirmDelete(e));
            first.addView(del);
        }
        first.addView(on);row.addView(first);

        LinearLayout scheduleRow=new LinearLayout(this);
        scheduleRow.setGravity(Gravity.CENTER_VERTICAL);
        scheduleRow.setPadding(Ui.dp(this,2),Ui.dp(this,7),0,0);
        scheduleRow.addView(schedule,new LinearLayout.LayoutParams(-2,-2));
        row.addView(scheduleRow);

        String missionText=I18n.tr(this,AlarmProfiles.missionName(e.missionType))+
                "  ·  "+I18n.tr(this,AlarmProfiles.missionSummary(this,e.id));
        LinearLayout missionRow=new LinearLayout(this);missionRow.setGravity(Gravity.CENTER_VERTICAL);
        missionRow.setPadding(Ui.dp(this,2),Ui.dp(this,4),0,0);
        TextView missionIcon=Ui.text(this,AlarmProfiles.missionIcon(e.missionType),11,e.enabled?Ui.ACCENT_2:Ui.MUTED);
        missionIcon.setGravity(Gravity.CENTER);missionIcon.setTypeface(null,android.graphics.Typeface.BOLD);
        missionIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        missionRow.addView(missionIcon,new LinearLayout.LayoutParams(Ui.dp(this,16),Ui.dp(this,16)));
        TextView detail=Ui.text(this,missionText,12,Ui.MUTED);
        detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(0,-2,1);
        detailLp.setMargins(Ui.dp(this,7),0,0,0);missionRow.addView(detail,detailLp);
        row.addView(missionRow);

        long snoozeAt=SnoozeScheduler.scheduledAt(this,e.id);
        if(snoozeAt>now){
            LinearLayout snoozeRow=new LinearLayout(this);snoozeRow.setGravity(Gravity.CENTER_VERTICAL);
            snoozeRow.setPadding(0,Ui.dp(this,4),0,0);snoozeRow.setOnClickListener(v->{});
            String snoozeText=String.format(I18n.locale(this),I18n.tr(this,"スヌーズ中  ·  あと %s"),relativeText(snoozeAt-now));
            TextView snoozeState=Ui.text(this,snoozeText,12,Ui.ACCENT_2);
            snoozeRow.addView(snoozeState,new LinearLayout.LayoutParams(0,-2,1));
            Button awake=Ui.ghostButton(this,I18n.tr(this,"起きた"));awake.setTextSize(12);
            awake.setTextColor(Ui.SUCCESS);awake.setOnClickListener(v->{SnoozeScheduler.markAwake(this,e.id);render();});
            snoozeRow.addView(awake);
            Button cancel=Ui.ghostButton(this,I18n.tr(this,"取消"));cancel.setTextSize(12);
            cancel.setTextColor(Ui.ACCENT_2);cancel.setOnClickListener(v->{SnoozeScheduler.cancel(this,e.id);render();});
            snoozeRow.addView(cancel);row.addView(snoozeRow);
        }
        on.setOnClickListener(v->{
            e.enabled=on.isChecked();AlarmProfiles.save(this,e);
            if(!e.enabled){SnoozeScheduler.cancel(this,e.id);if(e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);}
            AlarmScheduler.reschedule(this);render();
        });
        if("timeline".equals(Ui.LAYOUT_MODE)){
            LinearLayout event=new LinearLayout(this);event.setOrientation(LinearLayout.HORIZONTAL);
            event.setGravity(Gravity.TOP);event.setPadding(Ui.dp(this,5),0,0,0);
            View marker=new View(this);marker.setBackground(Ui.round(Ui.ACCENT_2,9,this));
            LinearLayout.LayoutParams markerLp=new LinearLayout.LayoutParams(Ui.dp(this,9),Ui.dp(this,9));
            markerLp.setMargins(0,Ui.dp(this,18),Ui.dp(this,8),0);event.addView(marker,markerLp);
            LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(0,-2,1);
            cardLp.bottomMargin=Ui.dp(this,10);event.addView(row,cardLp);
            list.addView(event,new LinearLayout.LayoutParams(-1,-2));
        }else{
            LinearLayout.LayoutParams cardLp=Ui.cardParams(this);cardLp.bottomMargin=Ui.dp(this,10);
            list.addView(row,cardLp);
        }
    }

    private void confirmDelete(AlarmStore.Entry e){
        if(e==null||e.id<1000)return;
        Ui.dialogBuilder(this)
                .setTitle(I18n.tr(this,"アラームを削除"))
                .setMessage(I18n.tr(this,"このアラームだけ削除します。"))
                .setNegativeButton(I18n.tr(this,"キャンセル"),null)
                .setPositiveButton(I18n.tr(this,"削除"),(d,w)->{
                    AlarmScheduler.cancelExtraAlarm(this,e.id);
                    AlarmProfiles.delete(this,e.id);
                    AlarmScheduler.reschedule(this);
                    render();
                }).show();
    }

    private void showEmptyHero(boolean empty){
        int height=Ui.dp(this,empty?HomeMetrics.HERO_EMPTY_HEIGHT_DP:HomeMetrics.HERO_HEIGHT_DP);
        nextCard.setMinimumHeight(height);
        nextCard.setBackground(empty?emptyHeroBackground:scheduledHeroBackground);
        LinearLayout.LayoutParams cardParams=(LinearLayout.LayoutParams)nextCard.getLayoutParams();
        if(cardParams.height!=height){cardParams.height=height;nextCard.setLayoutParams(cardParams);}
        if(sunriseAccent!=null){
            FrameLayout.LayoutParams accentParams=(FrameLayout.LayoutParams)sunriseAccent.getLayoutParams();
            if(accentParams.height!=height){accentParams.height=height;sunriseAccent.setLayoutParams(accentParams);}
            sunriseAccent.setVisibility(empty?View.GONE:View.VISIBLE);
        }
        heroContent.setVisibility(empty?View.GONE:View.VISIBLE);
        emptyHero.setVisibility(empty?View.VISIBLE:View.GONE);

    }

    private void addDragonGrowthOverlay(){
        dragonCompanion=new StreakCompanionView(this);
        FrameLayout.LayoutParams dragonLp=new FrameLayout.LayoutParams(
                Ui.dp(this,112),Ui.dp(this,124),Gravity.END|Gravity.TOP);
        dragonLp.rightMargin=Ui.dp(this,8);
        dragonLp.topMargin=Ui.dp(this,8);
        nextCard.addView(dragonCompanion,dragonLp);

        LinearLayout summary=new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(Ui.dp(this,14),Ui.dp(this,9),Ui.dp(this,14),Ui.dp(this,9));
        summary.setBackground(Ui.roundStroke(Ui.withAlpha(0xff08111a,0xC8),Ui.withAlpha(Ui.HERO_TEXT,0x50),14,this));

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        dragonFormText=Ui.text(this,I18n.tr(this,"竜の卵"),13,Ui.HERO_TEXT);
        dragonFormText.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD));
        dragonFormText.setSingleLine(true);
        dragonFormText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(dragonFormText,new LinearLayout.LayoutParams(0,-2,1));
        dragonLevelText=Ui.text(this,"Lv.1",12,0xFFFFD28A);
        dragonLevelText.setTypeface(null,android.graphics.Typeface.BOLD);dragonLevelText.setGravity(Gravity.CENTER);dragonLevelText.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4));dragonLevelText.setBackground(Ui.roundStroke(Ui.withAlpha(0xFFFFD28A,0x20),Ui.withAlpha(0xFFFFD28A,0x80),8,this));
        LinearLayout.LayoutParams levelLp=new LinearLayout.LayoutParams(-2,-2);levelLp.leftMargin=Ui.dp(this,8);titleRow.addView(dragonLevelText,levelLp);
        summary.addView(titleRow);

        dragonProgressText=Ui.text(this,"",11,Ui.HERO_TEXT);
        dragonProgressText.setSingleLine(true);
        dragonProgressText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dragonProgressText.setAlpha(1f);
        dragonProgressText.setPadding(0,Ui.dp(this,1),0,Ui.dp(this,3));
        summary.addView(dragonProgressText);

        dragonProgressTrack=new FrameLayout(this);
        dragonProgressTrack.setBackground(Ui.round(Ui.withAlpha(Ui.HERO_TEXT,0x42),4,this));
        dragonProgressFill=new View(this);
        dragonProgressFill.setBackground(Ui.round(0xffffc66d,4,this));
        dragonProgressTrack.addView(dragonProgressFill,new FrameLayout.LayoutParams(0,-1,Gravity.START));
        summary.addView(dragonProgressTrack,new LinearLayout.LayoutParams(-1,Ui.dp(this,5)));

        FrameLayout.LayoutParams summaryLp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.START);
        summaryLp.setMargins(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,9));
        nextCard.addView(summary,summaryLp);
    }

    private void refreshDragonGrowth(){
        if(dragonCompanion==null)return;
        StreakGrowth.ensure(this);
        long level=StreakGrowth.level(this);
        dragonCompanion.setGrowth(level,StreakTracker.displayCurrent(this));
        dragonFormText.setText(StreakGrowth.growthDescriptor(this,level));
        dragonLevelText.setText("Lv."+level);
        long remaining=StreakGrowth.remainingToNext(level);
        if(remaining>0L){
            dragonProgressText.setText(String.format(I18n.locale(this),
                    I18n.tr(this,"次の成長段階まであと %d 回"),remaining));
        }else{
            dragonProgressText.setText(I18n.tr(this,"最終段階で成長中"));
        }
        float progress=StreakGrowth.progressToNext(level);
        dragonProgressTrack.post(()->{
            int width=Math.round(dragonProgressTrack.getWidth()*progress);
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)dragonProgressFill.getLayoutParams();
            if(lp.width!=width){lp.width=width;dragonProgressFill.setLayoutParams(lp);}
        });
    }

    private boolean refreshNext(){
        long regular=AlarmScheduler.nextTriggerMillis(this),snooze=SnoozeScheduler.nextAt(this);long ms=regular;if(snooze>0&&(ms<=0||snooze<ms))ms=snooze;
        if(ms<=0){nextText.setText("--:--");nextMeta.setText(I18n.tr(this,"次のアラームはありません"));horizonDot.setVisibility(View.INVISIBLE);return false;}
        ZonedDateTime z=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());nextText.setText(z.format(DateTimeFormatter.ofPattern("HH:mm",I18n.locale(this))));
        horizonDot.setVisibility(View.VISIBLE);
        int minuteOfDay=z.getHour()*60+z.getMinute();
        horizonTrack.post(()->{
            int usable=Math.max(0,horizonTrack.getWidth()-horizonDot.getWidth());
            android.widget.FrameLayout.LayoutParams params=(android.widget.FrameLayout.LayoutParams)horizonDot.getLayoutParams();
            params.leftMargin=Math.round(usable*(minuteOfDay/1440f));
            params.gravity=Gravity.START|Gravity.CENTER_VERTICAL;
            horizonDot.setLayoutParams(params);
        });
        String when=z.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this)));
        String prefix=(snooze>0&&ms==snooze)?I18n.tr(this,"スヌーズ中")+"  ·  ":"";
        String remaining=String.format(I18n.locale(this),I18n.tr(this,"あと %s"),relativeText(ms-System.currentTimeMillis()));nextMeta.setText(when+"  ·  "+prefix+remaining);
        return true;
    }
    private String relativeText(long ms){long mins=Math.max(1,(long)Math.ceil(ms/60000.0));long h=mins/60,m=mins%60;String duration;if(h==0)duration=m+"分";else if(m==0)duration=h+"時間";else duration=h+"時間"+m+"分";return I18n.tr(this,duration);}
    private String repeatText(int m){if(m==0)return I18n.tr(this,"1回のみ");if((m&127)==127)return I18n.tr(this,"毎日");String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0){if(b.length()>0)b.append(" ");b.append(I18n.tr(this,n[i]));}return b.toString();}

    /** A quiet sunrise mark gives the next-alarm card a recognizable, non-character focal point. */
    private static final class SunriseAccentView extends View {
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SunriseAccentView(Activity activity) {
            super(activity);
            density = activity.getResources().getDisplayMetrics().density;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * .68f;
            float cy = getHeight() * .50f;
            float r = 31f * density;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, r * 1.65f,
                    new int[]{Ui.withAlpha(Ui.HERO_TEXT, 0x26), Ui.withAlpha(Ui.HERO_TEXT, 0x00)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 1.65f, paint);
            paint.setShader(null);
            paint.setColor(Ui.withAlpha(Ui.HERO_TEXT, 0x16));
            canvas.drawCircle(cx, cy - 5f * density, r * .45f, paint);
            paint.setColor(Ui.withAlpha(Ui.HERO_TEXT, 0x35));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.1f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            float horizon = cy + 9f * density;
            canvas.drawLine(cx-r*.82f, horizon, cx+r*.82f, horizon, paint);
            for (int i = 0; i < 3; i++) {
                float half = r * (.24f + .22f*i);
                float y = horizon + (9f + 8f*i) * density;
                canvas.drawLine(cx-half, y, cx+half, y, paint);
            }
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void requestRuntimePermissions(){try{ArrayList<String> req=new ArrayList<>();if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.POST_NOTIFICATIONS);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),41);}catch(Throwable ignored){}}
}
