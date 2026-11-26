package jp.wakeguard.alarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private String builtAppearanceKey;
    private Boolean exactAlarmsAvailable;
    private LinearLayout list;
    private LinearLayout readinessCard;
    private TextView readinessMessage, dateText;
    private android.widget.GridLayout tileGrid;
    private int tileColumnCount=2;
    private TextView nextText,nextMeta;
    private LinearLayout heroContent;
    private TextView emptyHeading;
    private StreakCompanionView dragonCompanion;
    private TextView dragonFormText,dragonLevelText,dragonProgressText;
    private FrameLayout dragonProgressTrack;
    private View dragonProgressFill;

    private FrameLayout horizonTrack;
    private View horizonDot;
    private final Handler countdownHandler=new Handler(Looper.getMainLooper());
    private final List<Runnable> alarmCountdowns = new ArrayList<>();
    private Map<Long, Boolean> renderedAlarmStates = Collections.emptyMap();
    private Map<Long, Long> renderedSnoozes = Collections.emptyMap();
    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            List<AlarmStore.Entry> alarms = AlarmProfiles.all(MainActivity.this);
            if (!renderedAlarmStates.equals(alarmStates(alarms))
                    || !renderedSnoozes.equals(SnoozeScheduler.active(MainActivity.this))) {
                render();
            } else {
                refreshOverview();
                for (Runnable countdown : alarmCountdowns) countdown.run();
            }
            countdownHandler.postDelayed(this, 30000L);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this); builtAppearanceKey=appearanceKey(); AlarmService.ensureNotificationChannel(this);
        buildUi(); requestRuntimePermissions();
    }
    @Override protected void onResume(){
        super.onResume();
        String currentAppearance=appearanceKey();
        if(builtAppearanceKey!=null&&!builtAppearanceKey.equals(currentAppearance)){recreate();return;}
        if(dragonCompanion!=null)dragonCompanion.onResume();
        boolean canSchedule = AlarmScheduler.canScheduleExact(this);
        if (exactAlarmsAvailable == null || exactAlarmsAvailable != canSchedule) {
            exactAlarmsAvailable = canSchedule;
            AlarmScheduler.reschedule(this);
        }
        for(TimerStore.Entry e:TimerStore.all(this))if(e.running&&e.remaining()>1500L)TimerReceiver.schedule(this,e.id,e.endMs);
        render();countdownHandler.removeCallbacks(countdownTick);countdownHandler.postDelayed(countdownTick,30000L);
    }
    @Override protected void onPause(){countdownHandler.removeCallbacks(countdownTick);if(dragonCompanion!=null)dragonCompanion.onPause();super.onPause();}

    private String appearanceKey(){
        return UiProfiles.id(this)+":"+VisualDesigns.id(this)+":"+AppThemes.key(this);
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(Ui.screenGradient(this));
        root.setClipChildren(true);root.setClipToPadding(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,5));
        LinearLayout brandLine=new LinearLayout(this);brandLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=Ui.overline(this,"IGNIDO  /  WAKE");
        brandLine.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        dateText=Ui.text(this,"",12,Ui.readableTextColor(Ui.MUTED_2, Ui.SURFACE_2));
        dateText.setGravity(Gravity.CENTER);
        dateText.setSingleLine(true);
        dateText.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,10),Ui.dp(this,5));
        dateText.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,11,this));
        brandLine.addView(dateText,new LinearLayout.LayoutParams(-2,-2));top.addView(brandLine);
        TextView title=Ui.title(this,"アラーム",HomeMetrics.HEADER_TITLE_SP);
        title.setAccessibilityHeading(true);
        title.setPadding(0,Ui.dp(this,3),0,0);top.addView(title);content.addView(top);

        LinearLayout nextCard=new LinearLayout(this);
        nextCard.setOrientation(LinearLayout.VERTICAL);
        nextCard.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,12));
        nextCard.setBackground(Ui.gradientRound(this,24,Ui.HERO_START,Ui.HERO_END));
        LinearLayout overview = new LinearLayout(this);
        overview.setGravity(Gravity.CENTER_VERTICAL);
        nextCard.addView(overview, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout schedule = new LinearLayout(this);
        schedule.setOrientation(LinearLayout.VERTICAL);
        overview.addView(schedule, new LinearLayout.LayoutParams(0, -2, 1));
        TextView nextLabel=Ui.overline(this,"次の起床");
        nextLabel.setTextColor(Ui.HERO_TEXT);
        schedule.addView(nextLabel);
        heroContent=new LinearLayout(this);heroContent.setOrientation(LinearLayout.VERTICAL);
        schedule.addView(heroContent, new LinearLayout.LayoutParams(-1, -2));
        nextText=Ui.title(this,"",HomeMetrics.HERO_TIME_SP);nextText.setTextColor(Ui.HERO_TEXT);
        nextText.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));
        nextText.setIncludeFontPadding(false);
        nextText.setSingleLine(true);
        nextText.setAutoSizeTextTypeUniformWithConfiguration(24,HomeMetrics.HERO_TIME_SP,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        nextText.setPadding(0,Ui.dp(this,2),0,0);
        heroContent.addView(nextText,new LinearLayout.LayoutParams(-1,-2));
        nextMeta=Ui.text(this,"",14,Ui.HERO_TEXT);
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
        emptyHeading=Ui.title(this,"",19);
        emptyHeading.setTextColor(Ui.HERO_TEXT);
        schedule.addView(emptyHeading, Ui.gapTop(this, 6));

        dragonCompanion=new StreakCompanionView(this);
        dragonCompanion.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int artworkSize = getResources().getConfiguration().screenWidthDp < 360 ? 84 : 110;
        LinearLayout.LayoutParams dragonLp = new LinearLayout.LayoutParams(Ui.dp(this,artworkSize), Ui.dp(this,artworkSize));
        dragonLp.setMarginStart(Ui.dp(this,8));
        overview.addView(dragonCompanion, dragonLp);
        addDragonGrowthSummary(nextCard);
        LinearLayout.LayoutParams nextLp=new LinearLayout.LayoutParams(-1,-2);
        nextLp.setMargins(Ui.dp(this,16),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,5));
        content.addView(nextCard,nextLp);
        addReadinessCard(content);

        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),Ui.dp(this,16));
        content.addView(list,new LinearLayout.LayoutParams(-1,-2));

        Button add=Ui.button(this,"アラームを追加",true);
        android.graphics.drawable.Drawable addIcon=getDrawable(R.drawable.ic_v292_action_add).mutate();
        addIcon.setTint(Ui.ON_ACCENT);int addIconSize=Ui.dp(this,20);
        addIcon.setBounds(0,0,addIconSize,addIconSize);
        add.setCompoundDrawablePadding(Ui.dp(this,8));add.setCompoundDrawables(addIcon,null,null,null);
        add.setOnClickListener(v->edit(-1));
        LinearLayout addAction=new LinearLayout(this);
        addAction.setBackground(Ui.navigationSurface(this));
        addAction.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),Ui.dp(this,4));
        addAction.addView(add,new LinearLayout.LayoutParams(-1,-2));root.addView(addAction);
        root.addView(bottomNav());
        setContentView(root);
        Ui.applySystemBarInsets(this,root);
    }

    private LinearLayout bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,9),Ui.dp(this,2),Ui.dp(this,9),Ui.dp(this,2));nav.setBackground(Ui.navigationSurface(this));
        LinearLayout alarms=Ui.bottomTab(this,"アラーム",true);LinearLayout clock=Ui.bottomTab(this,"時計",false);LinearLayout records=Ui.bottomTab(this,"記録",false);LinearLayout training=Ui.bottomTab(this,"筋トレ",false);LinearLayout settings=Ui.bottomTab(this,"設定",false);
        nav.addView(alarms,new LinearLayout.LayoutParams(0,-2,1));nav.addView(clock,new LinearLayout.LayoutParams(0,-2,1));nav.addView(records,new LinearLayout.LayoutParams(0,-2,1));nav.addView(training,new LinearLayout.LayoutParams(0,-2,1));nav.addView(settings,new LinearLayout.LayoutParams(0,-2,1));
        clock.setOnClickListener(v->openClock("world"));records.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class)));training.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,WorkoutActivity.class)));settings.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,SystemSettingsActivity.class)));return nav;
    }

    private void openClock(String mode){
        Ui.launchNoAnimation(this, new Intent(this,ClockActivity.class).putExtra("mode",mode));
    }
    private void edit(long id){startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",id));}

    private void addReadinessCard(LinearLayout content) {
        readinessCard = Ui.card(this);
        readinessCard.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,10));
        TextView title = Ui.title(this, localized("端末設定を確認", "Check device settings", "기기 설정 확인"), 16);
        title.setAccessibilityHeading(true);
        readinessCard.addView(title);
        readinessMessage = Ui.text(this, "", 14, Ui.readableTextColor(Ui.TEXT, Ui.SURFACE));
        readinessCard.addView(readinessMessage, Ui.gapTop(this, 4));
        Button settings = Ui.ghostButton(this, localized("設定を開く", "Open settings", "설정 열기"));
        settings.setOnClickListener(v ->
                Ui.launchNoAnimation(this, new Intent(this, SystemSettingsActivity.class)));
        readinessCard.addView(settings, Ui.gapTop(this, 4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(Ui.dp(this,16), Ui.dp(this,5), Ui.dp(this,16), Ui.dp(this,4));
        content.addView(readinessCard, params);
    }

    private void refreshReadiness() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        List<String> issues = new ArrayList<>();
        if (!AlarmScheduler.canScheduleExact(this)) {
            issues.add(localized("正確なアラームが許可されていません。", "Exact alarms are disabled.", "정확한 알람이 허용되지 않았습니다."));
        }
        if (manager != null && !manager.areNotificationsEnabled()) {
            issues.add(localized("アプリの通知がオフです。", "App notifications are off.", "앱 알림이 꺼져 있습니다."));
        } else if (Build.VERSION.SDK_INT >= 34 && manager != null && !manager.canUseFullScreenIntent()) {
            issues.add(localized("ロック画面の全画面表示が許可されていません。", "Full-screen alerts on the lock screen are disabled.", "잠금 화면의 전체 화면 알림이 허용되지 않았습니다."));
        }
        readinessCard.setVisibility(issues.isEmpty() ? View.GONE : View.VISIBLE);
        readinessMessage.setText(String.join("\n", issues));
    }

    private static Map<Long, Boolean> alarmStates(List<AlarmStore.Entry> alarms) {
        Map<Long, Boolean> states = new HashMap<>();
        for (AlarmStore.Entry alarm : alarms) states.put(alarm.id, alarm.enabled);
        return states;
    }

    private void refreshOverview() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("M/d · EEE",I18n.locale(this)));
        if (!date.contentEquals(dateText.getText())) dateText.setText(date);
        boolean hasNext = refreshNext();
        heroContent.setVisibility(hasNext ? View.VISIBLE : View.GONE);
        emptyHeading.setVisibility(hasNext ? View.GONE : View.VISIBLE);
        if (!hasNext) {
            boolean anyEnabled = renderedAlarmStates.containsValue(true);
            emptyHeading.setText(!anyEnabled && !renderedAlarmStates.isEmpty()
                    ? localized("アラームはオフです", "Alarms are off", "알람이 꺼져 있습니다")
                    : localized("予定はありません", "No alarm scheduled", "예정된 알람이 없습니다"));
        }
    }

    private void render(){
        list.removeAllViews();
        alarmCountdowns.clear();
        List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);
        renderedAlarmStates = alarmStates(alarms);
        renderedSnoozes = SnoozeScheduler.active(this);
        alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));
        int enabled=0;for(AlarmStore.Entry e:alarms)if(e.enabled)enabled++;
        refreshOverview();
        refreshReadiness();
        refreshDragonGrowth();
        if(alarms.isEmpty()){
            LinearLayout empty = Ui.card(this);
            TextView hint = Ui.text(this, localized(
                    "起床する時刻と曜日を決めて、最初のアラームを追加できます。",
                    "Choose a time and days for your first alarm.",
                    "시간과 요일을 정해 첫 알람을 추가할 수 있습니다."),
                    15, Ui.readableTextColor(Ui.MUTED, Ui.SURFACE));
            empty.addView(hint);
            list.addView(empty, Ui.cardParams(this));
            return;
        }
        LinearLayout listHeading=Ui.row(this);
        listHeading.setPadding(Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,3));
        TextView listTitle=Ui.sectionHeader(this,I18n.tr(this,"アラーム一覧"));
        listTitle.setAccessibilityHeading(true);
        listTitle.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,5));
        listHeading.addView(listTitle,new LinearLayout.LayoutParams(0,-2,1));
        listHeading.addView(Ui.pill(this,I18n.tr(this,"有効なアラーム")+"  "+enabled));list.addView(listHeading);
        Map<Long,Long> snoozes=renderedSnoozes;
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
            int screenWidthDp=getResources().getConfiguration().screenWidthDp;
            boolean largeText = getResources().getConfiguration().fontScale >= 1.3f;
            tileColumnCount=screenWidthDp<380 || (largeText && screenWidthDp<600) ? 1 : (screenWidthDp>=720 && !largeText ? 3 : 2);
            tileGrid=new android.widget.GridLayout(this);tileGrid.setColumnCount(tileColumnCount);tileGrid.setUseDefaultMargins(false);
            tileGrid.setPadding(0,Ui.dp(this,2),0,0);
            list.addView(tileGrid,new LinearLayout.LayoutParams(-1,-2));
            for(int i=0;i<alarms.size();i++)addAlarmTile(alarms.get(i),i);
            return;
        }
        tileGrid=null;
        for(AlarmStore.Entry e:alarms)addAlarmRow(e);
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
        TextView countdown=Ui.text(this,"",13,Ui.readableTextColor(Ui.ACCENT_2, Ui.SURFACE));
        countdown.setPadding(0,Ui.dp(this,3),0,0);
        tile.addView(countdown);
        bindCountdown(countdown, e);
        TextView mission=Ui.text(this,AlarmProfiles.missionIcon(e.missionType)+"  "+I18n.tr(this,AlarmProfiles.missionName(e.missionType)),12,Ui.MUTED);mission.setSingleLine(true);mission.setEllipsize(android.text.TextUtils.TruncateAt.END);mission.setPadding(0,Ui.dp(this,6),0,0);tile.addView(mission);
        addSnoozeActions(tile, e);
        on.setContentDescription(I18n.tr(this,label)+"  "+I18n.tr(this,"アラーム"));
        on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled){SnoozeScheduler.cancel(this,e.id);if(e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);}AlarmScheduler.reschedule(this);render();});
        android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(index/Math.max(1,tileColumnCount)),android.widget.GridLayout.spec(index%Math.max(1,tileColumnCount),1f));
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
        TextView countdown=Ui.text(this,"",13,Ui.readableTextColor(Ui.ACCENT_2, Ui.SURFACE));
        details.addView(countdown, Ui.gapTop(this,2));
        bindCountdown(countdown, e);
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

        addSnoozeActions(row, e);
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

    private void bindCountdown(TextView countdown, AlarmStore.Entry alarm) {
        Runnable update = () -> {
            long now = System.currentTimeMillis();
            long next = alarm.enabled ? AlarmScheduler.nextTriggerMillis(this, alarm) : -1L;
            countdown.setVisibility(next > now ? View.VISIBLE : View.GONE);
            if (next > now) {
                String text = String.format(I18n.locale(this), I18n.tr(this,"あと %s"),relativeText(next-now));
                if (!text.contentEquals(countdown.getText())) countdown.setText(text);
            }
        };
        update.run();
        if (alarm.enabled) alarmCountdowns.add(update);
    }

    private void addSnoozeActions(LinearLayout card, AlarmStore.Entry alarm) {
        Long at = renderedSnoozes.get(alarm.id);
        if (at == null) return;
        TextView status = Ui.text(this, "", 13, Ui.readableTextColor(Ui.ACCENT_2, Ui.SURFACE));
        card.addView(status, Ui.gapTop(this, 7));
        Runnable update = () -> {
            long remaining = at - System.currentTimeMillis();
            String value = remaining > 0
                    ? String.format(I18n.locale(this),I18n.tr(this,"スヌーズ中  ·  あと %s"),relativeText(remaining))
                    : I18n.tr(this,"スヌーズ中");
            if (!value.contentEquals(status.getText())) status.setText(value);
        };
        update.run();
        alarmCountdowns.add(update);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button awake = Ui.ghostButton(this, "起きた");
        awake.setTextSize(13);
        awake.setTextColor(Ui.readableTextColor(Ui.SUCCESS, Ui.SURFACE_2));
        awake.setOnClickListener(v -> { SnoozeScheduler.markAwake(this,alarm.id); render(); });
        actions.addView(awake, new LinearLayout.LayoutParams(0,-2,1));
        Button cancel = Ui.ghostButton(this, "取消");
        cancel.setTextSize(13);
        cancel.setOnClickListener(v -> { SnoozeScheduler.cancel(this,alarm.id); render(); });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0,-2,1);
        cancelParams.setMarginStart(Ui.dp(this,6));
        actions.addView(cancel, cancelParams);
        card.addView(actions, Ui.gapTop(this, 4));
    }

    private void confirmDelete(AlarmStore.Entry e){
        if(e==null||e.id<1000)return;
        Ui.dialogBuilder(this)
                .setTitle(I18n.tr(this,"アラームを削除"))
                .setMessage(I18n.tr(this,"このアラームだけ削除します。"))
                .setNegativeButton(I18n.tr(this,"キャンセル"),null)
                .setPositiveButton(I18n.tr(this,"削除"),(d,w)->{
                    SnoozeScheduler.cancel(this,e.id);
                    AlarmScheduler.cancelExtraAlarm(this,e.id);
                    AlarmProfiles.delete(this,e.id);
                    AlarmScheduler.reschedule(this);
                    render();
                }).show();
    }

    private void addDragonGrowthSummary(LinearLayout nextCard){
        LinearLayout summary=new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(Ui.dp(this,14),Ui.dp(this,9),Ui.dp(this,14),Ui.dp(this,9));
        int summaryInk = Ui.readableTextColor(Ui.TEXT, Ui.SURFACE);
        int summaryDetail = Ui.readableTextColor(Ui.MUTED, Ui.SURFACE);
        summary.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,16,this));

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        dragonFormText=Ui.text(this,"竜の卵",15,summaryInk);
        dragonFormText.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD));
        titleRow.addView(dragonFormText,new LinearLayout.LayoutParams(0,-2,1));
        dragonLevelText=Ui.text(this,"",13,Ui.readableTextColor(Ui.ACCENT_2, Ui.SURFACE_2));
        dragonLevelText.setTypeface(null,android.graphics.Typeface.BOLD);
        dragonLevelText.setGravity(Gravity.CENTER);
        dragonLevelText.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4));
        dragonLevelText.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,10,this));
        LinearLayout.LayoutParams levelLp=new LinearLayout.LayoutParams(-2,-2);
        levelLp.setMarginStart(Ui.dp(this,8));
        titleRow.addView(dragonLevelText,levelLp);
        summary.addView(titleRow);

        dragonProgressText=Ui.text(this,"",13,summaryDetail);
        dragonProgressText.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,7));
        summary.addView(dragonProgressText);

        dragonProgressTrack=new FrameLayout(this);
        dragonProgressTrack.setBackground(Ui.round(Ui.SURFACE_3,4,this));
        dragonProgressTrack.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        dragonProgressFill=new View(this);
        dragonProgressFill.setBackground(Ui.round(Ui.readableTextColor(Ui.ACCENT_2, Ui.SURFACE_3),4,this));
        dragonProgressTrack.addView(dragonProgressFill,new FrameLayout.LayoutParams(0,-1,Gravity.START));
        summary.addView(dragonProgressTrack,new LinearLayout.LayoutParams(-1,Ui.dp(this,5)));

        nextCard.addView(summary,Ui.gapTop(this,9));
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
            int offset = Math.round(usable*(minuteOfDay/1440f));
            if (params.getMarginStart() != offset) {
                params.setMarginStart(offset);
                horizonDot.setLayoutParams(params);
            }
        });
        String when=z.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this)));
        String prefix=(snooze>0&&ms==snooze)?I18n.tr(this,"スヌーズ中")+"  ·  ":"";
        String remaining=String.format(I18n.locale(this),I18n.tr(this,"あと %s"),relativeText(ms-System.currentTimeMillis()));nextMeta.setText(when+"  ·  "+prefix+remaining);
        return true;
    }
    private String relativeText(long ms){long mins=Math.max(1,(long)Math.ceil(ms/60000.0));long h=mins/60,m=mins%60;String duration;if(h==0)duration=m+"分";else if(m==0)duration=h+"時間";else duration=h+"時間"+m+"分";return I18n.tr(this,duration);}
    private String repeatText(int m){if(m==0)return I18n.tr(this,"1回のみ");if((m&127)==127)return I18n.tr(this,"毎日");String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0){if(b.length()>0)b.append(" ");b.append(I18n.tr(this,n[i]));}return b.toString();}

    private void requestRuntimePermissions(){try{ArrayList<String> req=new ArrayList<>();if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.POST_NOTIFICATIONS);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),41);}catch(Throwable ignored){}}
}
