package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;

import androidx.browser.customtabs.CustomTabsIntent;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClockActivity extends Activity {
    private static java.lang.ref.WeakReference<ClockActivity> visibleInstance=new java.lang.ref.WeakReference<>(null);
    private static final String PREF = "clock_tools";
    private static final String KEY_ZONES = "world_zones";
    private static final String KEY_24H = "use_24h";
    private static final String KEY_SW_RUNNING = "sw_running";
    private static final String KEY_SW_BASE = "sw_base";
    private static final String KEY_SW_ELAPSED = "sw_elapsed";
    private static final String KEY_SW_LAPS = "sw_laps";
    private static final String KEY_SW_LAP_WALLS = "sw_lap_walls";
    private static final String KEY_SW_STOPPED_WALL = "sw_stopped_wall";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root, body, stickyWorld;
    private FrameLayout localClockStage;
    private TextView localClock, localDetail, screenTitle, overviewLocalTime, overviewLocalDate;
    private ClockFaceActivity.AnalogFace localAnalog;
    private ImageButton screenAction, worldLayoutAction;
    private Button alarmTab, worldTab, stopwatchTab, timerTab, streakTab;
    private Dialog zonePickerDialog;
    private EditText zoneSearchBox;
    private String googleClipboardBefore = "";
    private String mode = "world";
    private final LinkedHashMap<String, WorldRow> worldRows = new LinkedHashMap<>();

    private TextView stopwatchDisplay, stopwatchStateHint, lapList;
    private Button stopwatchStartPause, stopwatchLapReset, stopwatchImport, stopwatchLapImport;
    private FrameLayout stopwatchStage;
    private ClockFaceActivity.AnalogFace stopwatchAnalog;

    private long pendingTimerSoundId = -1L;
    private static final int REQ_TIMER_SOUND = 7116;
    private static final int REQ_TIMER_NOTIFICATION = 7117;
    private static final int REQ_TIMER_VIDEO = 7118;
    private LinearLayout timerList;
    private TextView timerEmpty;
    private EditText quickHours, quickMinutes, quickSeconds;
    private Switch quickSave;
    private boolean normalizingQuickDuration = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try { updateLiveUi(); } catch (Throwable ignored) {}
            handler.postDelayed(this, 100L);
        }
    };

    private static final class WorldRow {
        FrameLayout stage;
        TextView time;
        ClockFaceActivity.AnalogFace analog;
        TextView detail;
        WorldRow(FrameLayout s,TextView t,ClockFaceActivity.AnalogFace a,TextView d) { stage=s; time=t; analog=a; detail=d; }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.prepareActivity(this);
        TimerReceiver.ensureChannel(this);
        String requested = getIntent() == null ? null : getIntent().getStringExtra("mode");
        if (requested != null) mode = requested;
        if(isIncomingTextIntent(getIntent())) mode="world";
        buildShell();
        showMode(mode);
        handleIncomingSearchIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(isIncomingTextIntent(intent)||intent.getBooleanExtra("use_clipboard",false)||intent.hasExtra("import_text")){
            showMode("world");
            handleIncomingSearchIntent(intent);
        }
    }

    private boolean isIncomingTextIntent(Intent i){
        if(i==null)return false;String a=i.getAction();
        return Intent.ACTION_PROCESS_TEXT.equals(a)||Intent.ACTION_SEND.equals(a);
    }

    private String incomingText(Intent i){
        if(i==null)return "";
        CharSequence x=null;
        if(Intent.ACTION_PROCESS_TEXT.equals(i.getAction()))x=i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        else if(Intent.ACTION_SEND.equals(i.getAction()))x=i.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if((x==null||x.length()==0)&&i.hasExtra("import_text"))x=i.getCharSequenceExtra("import_text");
        return x==null?"":x.toString().trim();
    }

    private void openZonePickerWithText(String text){
        if(text==null||text.trim().isEmpty())return;
        if(zonePickerDialog==null||!zonePickerDialog.isShowing())showZonePicker();
        if(zoneSearchBox!=null){zoneSearchBox.setText(text.trim());zoneSearchBox.setSelection(zoneSearchBox.length());}
    }

    private void handleIncomingSearchIntent(Intent i){
        if(i==null)return;
        String direct=incomingText(i);
        if(!direct.isEmpty()){handler.post(()->openZonePickerWithText(direct));return;}
        if(i.getBooleanExtra("use_clipboard",false)){
            handler.postDelayed(()->{String clip=clipboardText();if(!clip.isEmpty())openZonePickerWithText(clip);else Toast.makeText(this,I18n.tr(this,"コピーした文字がありません"),Toast.LENGTH_SHORT).show();},120L);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        visibleInstance=new java.lang.ref.WeakReference<>(this);
        requestTimerNotificationPermissionIfNeeded();
        refreshRunningTimerNotifications();
        syncStopwatchNotification();
        applyGoogleClipboardSearchIfNeeded();
        if("world".equals(mode))applyWorldDisplayMode();
        if("timer".equals(mode))renderTimers();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        ClockActivity current=visibleInstance.get();if(current==this)visibleInstance.clear();
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    static boolean showTimerVideoIfVisible(TimerStore.Entry e){
        ClockActivity a=visibleInstance.get();
        if(a==null||e==null)return false;
        try{if(a.isFinishing()||(Build.VERSION.SDK_INT>=17&&a.isDestroyed()))return false;}catch(Throwable ignored){}
        try{
            Intent open=new Intent(a,TimerVideoActivity.class).putExtra("videoUri",e.soundUri==null?"":e.soundUri).putExtra("label",e.label==null?"":e.label).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
            a.runOnUiThread(()->{try{a.startActivity(open);a.overridePendingTransition(0,0);}catch(Throwable ignored){}});
            return true;
        }catch(Throwable t){return false;}
    }

    @Override public void onBackPressed() { Ui.finishNoAnimation(this); }

    private android.content.SharedPreferences p() {
        return getSharedPreferences(PREF, MODE_PRIVATE);
    }

    private TextView text(String s, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(I18n.tr(this,s)); v.setTextSize(sp); v.setTextColor(color);
        return v;
    }

    private Button button(String s) { return Ui.button(this, s, false); }

    private void buildShell() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(Ui.screenGradient(this));

        LinearLayout top = Ui.row(this);
        top.setPadding(Ui.dp(this,22),Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,12));
        screenTitle = Ui.title(this, "世界時計", 27);
        top.addView(screenTitle,new LinearLayout.LayoutParams(0,-2,1));
        worldLayoutAction = Ui.iconButton(this,R.drawable.ic_v292_action_grid,false,"時計のレイアウトを切り替え");
        worldLayoutAction.setVisibility(View.GONE);
        worldLayoutAction.setContentDescription(I18n.tr(this,"一覧 / 詳細を切り替え"));
        worldLayoutAction.setOnClickListener(v -> { p().edit().putBoolean("world_overview_v201", !worldOverview()).apply(); showMode("world"); });
        top.addView(worldLayoutAction);
        screenAction = Ui.iconButton(this,R.drawable.ic_v292_action_add,true,"追加する");
        screenAction.setVisibility(View.GONE);
        screenAction.setOnClickListener(v -> showAddTimerDialog());
        top.addView(screenAction);
        outer.addView(top);

        stickyWorld=new LinearLayout(this);stickyWorld.setOrientation(LinearLayout.VERTICAL);stickyWorld.setBackgroundColor(Ui.BG);stickyWorld.setPadding(Ui.dp(this,22),Ui.dp(this,0),Ui.dp(this,22),Ui.dp(this,8));outer.addView(stickyWorld);

        localClock = new TextView(this); localClock.setVisibility(View.GONE);
        localDetail = new TextView(this); localDetail.setVisibility(View.GONE);
        localAnalog = new ClockFaceActivity.AnalogFace(this); localAnalog.setVisibility(View.GONE);

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this,18), Ui.dp(this,14), Ui.dp(this,18), Ui.dp(this,28));
        scroll.addView(body);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(Ui.dp(this,9),Ui.dp(this,7),Ui.dp(this,9),Ui.dp(this,8));nav.setBackgroundColor(Ui.SURFACE);
        alarmTab=Ui.bottomTab(this,"アラーム",false);
        worldTab=Ui.bottomTab(this,"時計","world".equals(mode));
        timerTab=Ui.bottomTab(this,"タイマー","timer".equals(mode));
        stopwatchTab=Ui.bottomTab(this,"ストップウォッチ","stopwatch".equals(mode));
        streakTab=Ui.bottomTab(this,"ストリーク",false);
        nav.addView(alarmTab,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));
        nav.addView(worldTab,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));
        nav.addView(timerTab,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));
        nav.addView(stopwatchTab,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));
        nav.addView(streakTab,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));
        outer.addView(nav);
        alarmTab.setOnClickListener(v -> Ui.finishNoAnimation(this));
        worldTab.setOnClickListener(v -> showMode("world"));
        timerTab.setOnClickListener(v -> showMode("timer"));
        stopwatchTab.setOnClickListener(v -> showMode("stopwatch"));
        streakTab.setOnClickListener(v -> Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class)));

        root = outer;
        setContentView(outer);
        Ui.applySystemBarInsets(this, outer);
    }

    private void openClockFace(String clockMode,String zone,long timerId){
        Intent i=new Intent(this,ClockFaceActivity.class).putExtra("clock_mode",clockMode);
        if(zone!=null)i.putExtra("zone",zone); if(timerId>=0)i.putExtra("timer_id",timerId);
        startActivity(i); overridePendingTransition(0,0);
    }

    private void showMode(String next) {
        mode = next;
        body.removeAllViews();
        worldRows.clear();
        if(stickyWorld!=null){stickyWorld.removeAllViews();stickyWorld.setVisibility("world".equals(mode)?View.VISIBLE:View.GONE);}
        if(screenTitle!=null) screenTitle.setText(I18n.tr(this,"stopwatch".equals(mode)?"ストップウォッチ":"timer".equals(mode)?"タイマー":"世界時計"));
        if(worldLayoutAction!=null){
            boolean world="world".equals(mode);worldLayoutAction.setVisibility(world?View.VISIBLE:View.GONE);
            if(world){worldLayoutAction.setImageResource(worldOverview()?R.drawable.ic_v292_action_list:R.drawable.ic_v292_action_grid);worldLayoutAction.setContentDescription(I18n.tr(this,worldOverview()?"詳細表示に切り替え":"一覧表示に切り替え"));}
        }
        if(screenAction!=null){
            boolean hasAdd = "world".equals(mode) || "timer".equals(mode);
            screenAction.setVisibility(hasAdd?View.VISIBLE:View.GONE);
            screenAction.setOnClickListener(v -> { if("world".equals(mode)) showZonePicker(); else if("timer".equals(mode)) showAddTimerDialog(); });
        }
        if(worldTab!=null){
            Ui.updateBottomTab(this,worldTab,"world".equals(mode));
            Ui.updateBottomTab(this,timerTab,"timer".equals(mode));
            Ui.updateBottomTab(this,stopwatchTab,"stopwatch".equals(mode));
        }
        if ("stopwatch".equals(mode)) buildStopwatch();
        else if ("timer".equals(mode)) buildTimer();
        else buildWorldClock();
        updateLiveUi();
    }

    private void updateLocalClock() {
        if(localClock==null||localDetail==null)return;
        ZonedDateTime now = ZonedDateTime.now();
        boolean h24 = p().getBoolean(KEY_24H, true);
        DateTimeFormatter tf = DateTimeFormatter.ofPattern(h24 ? "HH:mm:ss" : "hh:mm:ss a", I18n.locale(this));
        localClock.setText(now.format(tf));
        String offset = formatOffset(now.getOffset().getTotalSeconds());
        if(overviewLocalTime!=null){
            overviewLocalTime.setText(now.format(DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "hh:mm a", I18n.locale(this))));
            overviewLocalDate.setText(now.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"full"), I18n.locale(this))));
        }
        localDetail.setText(now.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"full"), I18n.locale(this)))
                + "  ·  " + friendlyZoneName(now.getZone().getId())
                + "  ·  " + now.getZone().getId()
                + "  ·  UTC" + offset);
        if(localAnalog!=null){
            localAnalog.configure(p().getBoolean("analog_numbers",true),p().getBoolean("analog_ticks",true),p().getBoolean("analog_seconds",true),p().getInt("analog_number_scale",1));
            localAnalog.setHands(now.getHour()%12+now.getMinute()/60d,now.getMinute()+now.getSecond()/60d,now.getSecond()+now.getNano()/1_000_000_000d);
        }
    }

    private boolean worldOverview(){return p().getBoolean("world_overview_v201",true);}

    private void buildWorldClock() {
        if(worldOverview()){
            if(stickyWorld!=null)stickyWorld.setVisibility(View.GONE);
            body.setPadding(Ui.dp(this,10),Ui.dp(this,4),Ui.dp(this,10),Ui.dp(this,6));
            buildWorldOverview();
            return;
        }
        if(stickyWorld!=null)stickyWorld.setVisibility(View.VISIBLE);
        body.setPadding(Ui.dp(this,22),Ui.dp(this,6),Ui.dp(this,22),Ui.dp(this,36));
        buildStickyWorldHeader();

        LinearLayout setting=Ui.row(this);
        setting.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));
        TextView label=text("24時間表示",15,Ui.TEXT); setting.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch h24=new Switch(this); h24.setChecked(p().getBoolean(KEY_24H,true));
        h24.setOnCheckedChangeListener((v,checked)->{p().edit().putBoolean(KEY_24H,checked).apply();updateLiveUi();WorldClockWidgetProvider.refreshAll(this);});
        setting.addView(h24); body.addView(setting); body.addView(Ui.divider(this));

        TextView hint=Ui.text(this,"時計をタップするとデジタル / アナログが一括で切り替わります",12,Ui.MUTED);
        hint.setPadding(0,Ui.dp(this,10),0,0);body.addView(hint);

        TextView cityHeader=Ui.sectionHeader(this,"登録した都市"); cityHeader.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,6)); body.addView(cityHeader);
        LinkedHashSet<String> zones=loadZones();
        for(String zone:zones)addWorldCard(zone);
        applyWorldDisplayMode();
    }

    private void buildWorldOverview(){
        LinkedHashSet<String> zones=loadZones();
        LinearLayout localCard=Ui.card(this);
        localCard.setPadding(Ui.dp(this,22),Ui.dp(this,18),Ui.dp(this,22),Ui.dp(this,18));
        localCard.addView(Ui.text(this,"現在の時刻",13,Ui.MUTED));
        overviewLocalTime=Ui.title(this,"--:--",42);
        overviewLocalTime.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        localCard.addView(overviewLocalTime,Ui.gapTop(this,4));
        overviewLocalDate=Ui.text(this,"",13,Ui.MUTED);
        localCard.addView(overviewLocalDate,Ui.gapTop(this,3));
        body.addView(localCard,Ui.cardParams(this));
        TextView citiesHeader=Ui.sectionHeader(this,"登録した都市");
        citiesHeader.setPadding(Ui.dp(this,8),Ui.dp(this,8),0,Ui.dp(this,12));
        body.addView(citiesHeader);
        int count=Math.max(1,zones.size());
        int columns=count<=4?2:3;
        int rows=(count+columns-1)/columns;
        int gap=Ui.dp(this,6);
        int screenW=getResources().getDisplayMetrics().widthPixels;
        int screenH=getResources().getDisplayMetrics().heightPixels;
        int usableW=Math.max(Ui.dp(this,220),screenW-Ui.dp(this,20)-gap*(columns-1));
        int cardW=Math.max(Ui.dp(this,54),usableW/columns);
        int cardH=Ui.dp(this,columns<=2?184:164);
        GridLayout grid=new GridLayout(this);grid.setColumnCount(columns);grid.setRowCount(rows);grid.setUseDefaultMargins(false);
        int index=0;for(String zone:zones){addWorldOverviewCard(grid,zone,index++,columns,cardW,cardH,gap);}
        body.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    }

    private void addWorldOverviewCard(GridLayout grid,String zoneId,int index,int columns,int cardW,int cardH,int gap){
        try{ZoneId.of(zoneId);}catch(Throwable t){return;}
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,12));card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,18,this));
        String nameText=WorldCityCatalog.savedWorldLabel(this,zoneId,friendlyZoneName(zoneId));
        TextView name=text(nameText,columns<=2?18:columns==3?15:columns==4?13:11,Ui.TEXT);name.setTypeface(null,Typeface.BOLD);name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setMaxLines(1);name.setEllipsize(null);name.setAutoSizeTextTypeUniformWithConfiguration(columns<=2?12:10,columns<=2?18:columns==3?15:columns==4?13:11,1,android.util.TypedValue.COMPLEX_UNIT_SP);card.addView(name,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout stage=new FrameLayout(this);TextView time=text("--:--",columns<=2?36:columns==3?30:columns==4?24:20,Ui.TEXT);time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);time.setGravity(Gravity.CENTER);time.setSingleLine(true);time.setMaxLines(1);time.setEllipsize(null);time.setAutoSizeTextTypeUniformWithConfiguration(columns<=2?24:columns==3?20:16,columns<=2?36:columns==3?30:columns==4?24:20,1,android.util.TypedValue.COMPLEX_UNIT_SP);ClockFaceActivity.AnalogFace analog=new ClockFaceActivity.AnalogFace(this);time.setVisibility(worldAnalog()?View.GONE:View.VISIBLE);analog.setVisibility(worldAnalog()?View.VISIBLE:View.GONE);stage.addView(time,new FrameLayout.LayoutParams(-1,-1));stage.addView(analog,new FrameLayout.LayoutParams(-1,-1));card.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        TextView detail=text("",columns<=2?13:columns==3?11:columns==4?10:9,Ui.MUTED);detail.setGravity(Gravity.CENTER);detail.setSingleLine(true);detail.setMaxLines(1);detail.setEllipsize(null);detail.setAutoSizeTextTypeUniformWithConfiguration(8,columns<=2?13:columns==3?11:columns==4?10:9,1,android.util.TypedValue.COMPLEX_UNIT_SP);card.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        card.setContentDescription(I18n.tr(this,"タップでデジタル / アナログ切替。長押しで拡大表示"));card.setOnClickListener(v->toggleWorldClockMode());card.setOnLongClickListener(v->{openClockFace("world",zoneId,-1L);return true;});
        worldRows.put(zoneId,new WorldRow(stage,time,analog,detail));
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=cardW;lp.height=cardH;lp.columnSpec=GridLayout.spec(index%columns);lp.rowSpec=GridLayout.spec(index/columns);lp.setMargins(index%columns==0?0:gap/2,index/columns==0?0:gap/2,index%columns==columns-1?0:gap/2,index/columns==rowsForGrid(grid)-1?0:gap/2);grid.addView(card,lp);
    }

    private int rowsForGrid(GridLayout grid){return Math.max(1,grid.getRowCount());}

    private String compactDayLabel(ZonedDateTime remote,ZonedDateTime local){
        long d=java.time.temporal.ChronoUnit.DAYS.between(local.toLocalDate(),remote.toLocalDate());
        if(d==0)return I18n.tr(this,"今日");if(d==1)return I18n.tr(this,"明日");if(d==-1)return I18n.tr(this,"昨日");
        return remote.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this)));
    }

    private void buildStickyWorldHeader(){
        if(stickyWorld==null)return;
        stickyWorld.removeAllViews();
        TextView here=Ui.text(this,"現在のタイムゾーン",12,Ui.MUTED);stickyWorld.addView(here);

        localClockStage=new FrameLayout(this);
        localClock=new TextView(this);localClock.setTextColor(Ui.TEXT);localClock.setTextSize(52);localClock.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);localClock.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);localClock.setPadding(0,0,0,0);
        localAnalog=new ClockFaceActivity.AnalogFace(this);
        localClockStage.addView(localClock,new FrameLayout.LayoutParams(-1,-1));localClockStage.addView(localAnalog,new FrameLayout.LayoutParams(-1,-1));
        localClockStage.setClickable(true);localClockStage.setOnClickListener(v->toggleWorldClockMode());localClockStage.setOnLongClickListener(v->{openClockFace("world",ZoneId.systemDefault().getId(),-1L);return true;});
        stickyWorld.addView(localClockStage,new LinearLayout.LayoutParams(-1,Ui.dp(this,68)));

        localDetail=new TextView(this);localDetail.setTextColor(Ui.MUTED);localDetail.setTextSize(12);localDetail.setLineSpacing(0,1.08f);localDetail.setPadding(0,Ui.dp(this,2),0,Ui.dp(this,8));stickyWorld.addView(localDetail);
        TextView tapHint=Ui.text(this,"タップで表示切替  ·  長押しで大きく表示",11,Ui.MUTED_2);stickyWorld.addView(tapHint);
        View stickyDivider=Ui.divider(this); LinearLayout.LayoutParams stickyDividerLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,1)); stickyDividerLp.setMargins(0,Ui.dp(this,8),0,0); stickyWorld.addView(stickyDivider,stickyDividerLp);
        updateLocalClock();
    }

    private boolean worldAnalog(){return p().getBoolean("clock_face_analog",false);}
    private void toggleWorldClockMode(){p().edit().putBoolean("clock_face_analog",!worldAnalog()).apply();applyWorldDisplayMode();updateWorldRows();updateLocalClock();}
    private void applyWorldDisplayMode(){
        boolean analog=worldAnalog();
        if(worldOverview()){for(WorldRow r:worldRows.values()){r.time.setVisibility(analog?View.GONE:View.VISIBLE);if(r.analog!=null)r.analog.setVisibility(analog?View.VISIBLE:View.GONE);}return;}
        if(localClockStage!=null){
            localClock.setVisibility(analog?View.GONE:View.VISIBLE);localAnalog.setVisibility(analog?View.VISIBLE:View.GONE);
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)localClockStage.getLayoutParams();lp.height=Ui.dp(this,analog?190:68);localClockStage.setLayoutParams(lp);
        }
        for(WorldRow r:worldRows.values()){
            r.time.setVisibility(analog?View.GONE:View.VISIBLE);r.analog.setVisibility(analog?View.VISIBLE:View.GONE);
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)r.stage.getLayoutParams();lp.height=Ui.dp(this,analog?160:48);r.stage.setLayoutParams(lp);
        }
    }

    private LinkedHashSet<String> loadZones() {
        String raw = p().getString(KEY_ZONES, "");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null && !raw.isEmpty()) {
            for (String s : raw.split("\\n")) if (!s.isEmpty()) out.add(s);
        }
        if (out.isEmpty()) {
            out.add("Asia/Tokyo");
            out.add("Asia/Seoul");
            out.add("America/New_York");
            out.add("Europe/London");
            saveZones(out);
        }
        return out;
    }

    private void saveZones(Collection<String> zones) {
        StringBuilder b = new StringBuilder();
        for (String z : zones) { if (b.length() > 0) b.append('\n'); b.append(z); }
        p().edit().putString(KEY_ZONES, b.toString()).apply();
    }

    private void addWorldCard(String zoneId) {
        ZoneId zone; try{zone=ZoneId.of(zoneId);}catch(Throwable t){return;}
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(Ui.dp(this,15),Ui.dp(this,13),Ui.dp(this,15),Ui.dp(this,13));row.setBackground(Ui.round(Ui.SURFACE,16,this));
        LinearLayout titleRow=new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);TextView name=text(WorldCityCatalog.savedWorldLabel(this,zoneId,friendlyZoneName(zoneId)),17,Ui.TEXT); name.setTypeface(null,Typeface.BOLD);names.addView(name);String country=localizedCountryName(zoneId);if(country!=null&&!country.isEmpty()){TextView countryView=new TextView(this);countryView.setText(country);countryView.setTextSize(11);countryView.setTextColor(Ui.ACCENT);names.addView(countryView,Ui.gapTop(this,1));}titleRow.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        TextView remove=timerTextAction("削除",13,Ui.MUTED); remove.setOnClickListener(v->{LinkedHashSet<String> zones=loadZones();zones.remove(zoneId);saveZones(zones);WorldCityCatalog.saveWorldLabel(this,zoneId,null);showMode("world");}); titleRow.addView(remove);
        row.addView(titleRow);

        FrameLayout stage=new FrameLayout(this);
        TextView t=text("--:--:--",33,Ui.TEXT);t.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);t.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        ClockFaceActivity.AnalogFace analog=new ClockFaceActivity.AnalogFace(this);analog.configure(p().getBoolean("analog_numbers",true),p().getBoolean("analog_ticks",true),p().getBoolean("analog_seconds",true),0);
        stage.addView(t,new FrameLayout.LayoutParams(-1,-1));stage.addView(analog,new FrameLayout.LayoutParams(-1,-1));
        stage.setClickable(true);stage.setOnClickListener(v->toggleWorldClockMode());stage.setOnLongClickListener(v->{openClockFace("world",zoneId,-1L);return true;});
        row.addView(stage,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));

        TextView d=text(zoneId,13,Ui.MUTED); row.addView(d,Ui.gapTop(this,2)); worldRows.put(zoneId,new WorldRow(stage,t,analog,d));
        body.addView(row,Ui.cardParams(this));
    }

    private void updateWorldRows() {
        if (!"world".equals(mode)) return;
        ZonedDateTime local = ZonedDateTime.now();
        int localOffset = local.getOffset().getTotalSeconds();
        boolean h24 = p().getBoolean(KEY_24H, true);
        DateTimeFormatter tf = DateTimeFormatter.ofPattern(h24 ? "HH:mm:ss" : "hh:mm:ss a", I18n.locale(this));
        DateTimeFormatter compactTf = DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", I18n.locale(this));
        DateTimeFormatter df = DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"), I18n.locale(this));
        for (Map.Entry<String, WorldRow> e : worldRows.entrySet()) {
            try {
                ZoneId z = ZoneId.of(e.getKey());
                ZonedDateTime now = ZonedDateTime.now(z);
                int offset = now.getOffset().getTotalSeconds();
                int diff = offset - localOffset;
                boolean dst = z.getRules().isDaylightSavings(now.toInstant());
                WorldRow r=e.getValue();
                r.time.setText(now.format(worldOverview()?compactTf:tf));
                if(r.analog!=null){r.analog.configure(p().getBoolean("analog_numbers",true),p().getBoolean("analog_ticks",true),p().getBoolean("analog_seconds",true),0);r.analog.setHands(now.getHour()%12+now.getMinute()/60d,now.getMinute()+now.getSecond()/60d,now.getSecond()+now.getNano()/1_000_000_000d);}
                if(worldOverview())r.detail.setText(compactDayLabel(now,local)+"  ·  UTC"+formatOffset(offset));
                else r.detail.setText(now.format(df)
                        + "  •  " + z.getId()
                        + "\nUTC" + formatOffset(offset)
                        + "  •  " + I18n.tr(this,"端末との差")+" " + formatDiff(diff)
                        + "  •  " + I18n.tr(this,"夏時間")+" " + (dst ? "ON" : "OFF"));
            } catch (Throwable ignored) {}
        }
    }

    private String friendlyZoneName(String id) {
        try {
            String x=android.icu.text.TimeZoneNames.getInstance(I18n.locale(this)).getExemplarLocationName(id);
            if(x!=null&&!x.trim().isEmpty())return x.trim();
        } catch(Throwable ignored) {}
        String last=id.contains("/")?id.substring(id.lastIndexOf('/')+1):id;last=last.replace('_',' ');
        Map<String,String> names=new HashMap<>();names.put("Asia/Tokyo","東京");names.put("Asia/Seoul","ソウル");names.put("America/New_York","ニューヨーク");names.put("America/Los_Angeles","ロサンゼルス");names.put("America/Chicago","シカゴ");names.put("Europe/London","ロンドン");names.put("Europe/Paris","パリ");names.put("Europe/Berlin","ベルリン");names.put("Asia/Dubai","ドバイ");names.put("Asia/Kolkata","デリー / インド");names.put("Asia/Bangkok","バンコク");names.put("Asia/Singapore","シンガポール");names.put("Asia/Hong_Kong","香港");names.put("Asia/Shanghai","上海");names.put("Australia/Sydney","シドニー");names.put("Pacific/Honolulu","ホノルル");
        return names.containsKey(id)?I18n.tr(this,names.get(id)):last;
    }

    private String countryCodeForZone(String id){
        try{
            String code=android.icu.util.TimeZone.getRegion(id);
            if(code!=null&&code.matches("[A-Z]{2}"))return code;
        }catch(Throwable ignored){}
        return "";
    }

    private String countryName(String code,Locale locale){
        if(code==null||code.length()!=2)return "";
        try{return new Locale("",code).getDisplayCountry(locale);}catch(Throwable ignored){return code;}
    }

    private String localizedCountryName(String zoneId){return countryName(countryCodeForZone(zoneId),I18n.locale(this));}

    private String normalizeSearch(String x){
        if(x==null)return "";
        try{x=java.text.Normalizer.normalize(x,java.text.Normalizer.Form.NFKC);}catch(Throwable ignored){}
        StringBuilder kana=new StringBuilder(x.length());
        for(int i=0;i<x.length();i++){char c=x.charAt(i);if(c>=0x3041&&c<=0x3096)c=(char)(c+0x60);kana.append(c);}
        x=kana.toString();
        try{x=java.text.Normalizer.normalize(x,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","");}catch(Throwable ignored){}
        return x.toLowerCase(Locale.ROOT).replace('_',' ').replaceAll("\\s+"," ").trim();
    }

    private String lightweightCityName(String id){
        if(id==null||id.isEmpty())return "";
        String last=id.contains("/")?id.substring(id.lastIndexOf('/')+1):id;
        return last.replace('_',' ');
    }

    private ArrayList<String> fastSelectableZones(){
        ArrayList<String> out=new ArrayList<>();
        try{
            Set<String> ids=android.icu.util.TimeZone.getAvailableIDs(android.icu.util.TimeZone.SystemTimeZoneType.CANONICAL_LOCATION,null,null);
            out.addAll(ids);
        }catch(Throwable ignored){
            for(String id:ZoneId.getAvailableZoneIds())if(id.contains("/")&&!id.startsWith("Etc/"))out.add(id);
        }
        return out;
    }

    private String countryAliases(String code){
        if("US".equals(code))return "usa us america united states アメリカ 米国 미국 미합중국";
        if("KR".equals(code))return "korea south korea republic of korea 韓国 한국 대한민국";
        if("JP".equals(code))return "japan 日本 일본";
        if("GB".equals(code))return "uk united kingdom britain england イギリス 英国 영국";
        if("CN".equals(code))return "china 中国 중국";
        if("TW".equals(code))return "taiwan 台湾 대만";
        if("AU".equals(code))return "australia オーストラリア 호주";
        if("CA".equals(code))return "canada カナダ 캐나다";
        if("IN".equals(code))return "india インド 인도";
        return "";
    }

    private String zoneSearchText(String id){
        String code=countryCodeForZone(id);
        StringBuilder b=new StringBuilder(id).append(' ').append(friendlyZoneName(id)).append(' ').append(code);
        b.append(' ').append(countryName(code,I18n.locale(this)));
        b.append(' ').append(countryName(code,Locale.JAPAN));
        b.append(' ').append(countryName(code,Locale.KOREA));
        b.append(' ').append(countryName(code,Locale.ENGLISH));
        b.append(' ').append(countryAliases(code));
        return normalizeSearch(b.toString());
    }

    private ArrayList<String> selectableZones(){
        ArrayList<String> out=new ArrayList<>();
        try{
            Set<String> ids=android.icu.util.TimeZone.getAvailableIDs(android.icu.util.TimeZone.SystemTimeZoneType.CANONICAL_LOCATION,null,null);
            out.addAll(ids);
        }catch(Throwable ignored){
            for(String id:ZoneId.getAvailableZoneIds())if(id.contains("/")&&!id.startsWith("Etc/"))out.add(id);
        }
        final HashMap<String,String> countryCache=new HashMap<>(),cityCache=new HashMap<>();
        Collections.sort(out,(a,b)->{
            String ca=countryCache.computeIfAbsent(a,k->localizedCountryName(k));
            String cb=countryCache.computeIfAbsent(b,k->localizedCountryName(k));
            int c=ca.compareToIgnoreCase(cb);if(c!=0)return c;
            String aa=cityCache.computeIfAbsent(a,k->friendlyZoneName(k));
            String bb=cityCache.computeIfAbsent(b,k->friendlyZoneName(k));
            return aa.compareToIgnoreCase(bb);
        });
        return out;
    }

    private ArrayList<String> countryTerms(String code){
        ArrayList<String> out=new ArrayList<>();
        if(code==null||code.isEmpty())return out;
        out.add(normalizeSearch(code));
        for(Locale l:new Locale[]{I18n.locale(this),Locale.JAPAN,Locale.KOREA,Locale.ENGLISH}){
            String n=normalizeSearch(countryName(code,l));if(!n.isEmpty()&&!out.contains(n))out.add(n);
        }
        String aliases=normalizeSearch(countryAliases(code));
        if(!aliases.isEmpty()){
            out.add(aliases);
            for(String x:aliases.split("\\s+"))if(x.length()>1&&!out.contains(x))out.add(x);
        }
        return out;
    }

    private int editDistance(String a,String b){
        a=normalizeSearch(a);b=normalizeSearch(b);if(a.equals(b))return 0;if(a.isEmpty())return b.length();if(b.isEmpty())return a.length();
        int[] prev=new int[b.length()+1],cur=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;
        for(int i=1;i<=a.length();i++){cur[0]=i;char ca=a.charAt(i-1);for(int j=1;j<=b.length();j++){int cost=ca==b.charAt(j-1)?0:1;cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+cost);}int[] t=prev;prev=cur;cur=t;}
        return prev[b.length()];
    }

    private int countryGuessScore(String q,String code){
        q=normalizeSearch(q);if(q.isEmpty())return 999;
        String full=normalizeSearch(code+" "+countryName(code,I18n.locale(this))+" "+countryName(code,Locale.JAPAN)+" "+countryName(code,Locale.KOREA)+" "+countryName(code,Locale.ENGLISH)+" "+countryAliases(code));
        if(full.equals(q))return 0;if(full.contains(q))return 1;
        int best=999;
        for(String term:countryTerms(code)){
            if(term.equals(q))return 0;
            if(term.startsWith(q)||q.startsWith(term))best=Math.min(best,1+Math.abs(term.length()-q.length())/3);
            best=Math.min(best,editDistance(q,term));
        }
        return best;
    }

    private ArrayList<String> guessCountryCodes(String q,ArrayList<String> all,int limit){
        LinkedHashSet<String> unique=new LinkedHashSet<>();for(String id:all){String c=countryCodeForZone(id);if(!c.isEmpty())unique.add(c);}
        ArrayList<String> codes=new ArrayList<>(unique);codes.sort((a,b)->Integer.compare(countryGuessScore(q,a),countryGuessScore(q,b)));
        ArrayList<String> out=new ArrayList<>();int threshold=Math.max(2,normalizeSearch(q).length()/3);
        for(String c:codes){int score=countryGuessScore(q,c);if(score<=threshold||out.size()<2){out.add(c);if(out.size()>=limit)break;}}
        return out;
    }

    private LinkedHashSet<String> regionCountryCodes(String q){
        q=normalizeSearch(q);LinkedHashSet<String> out=new LinkedHashSet<>();
        String[][] groups={
            {"asia アジア 아시아","JP","KR","CN","TW","HK","SG","TH","VN","PH","MY","ID","IN"},
            {"europe ヨーロッパ 欧州 유럽","GB","FR","DE","IT","ES","NL","BE","CH","AT","SE","NO","DK","FI","PL","GR","PT","IE"},
            {"north america 北米 북미","US","CA","MX"},
            {"south america 南米 남미","BR","AR","CL","PE","CO","UY"},
            {"africa アフリカ 아프리카","ZA","EG","MA","KE","NG","GH","ET"},
            {"oceania オセアニア 大洋州 오세아니아","AU","NZ","FJ","PG"},
            {"middle east 中東 중동","AE","SA","QA","IL","TR","JO","KW","OM","BH"}
        };
        for(String[] g:groups){for(String key:g[0].split(" ")){if(!key.isEmpty()&&(q.contains(normalizeSearch(key))||normalizeSearch(key).contains(q))){for(int i=1;i<g.length;i++)out.add(g[i]);return out;}}}
        return out;
    }

    private void addCountryZones(LinkedHashSet<String> target,Collection<String> codes,ArrayList<String> all,int perCountry,int total){
        HashMap<String,Integer> used=new HashMap<>();for(String id:all){String c=countryCodeForZone(id);if(!codes.contains(c))continue;int n=used.getOrDefault(c,0);if(n>=perCountry)continue;target.add(id);used.put(c,n+1);if(target.size()>=total)return;}
    }

    private ArrayList<String> fuzzyCityMatches(String q,ArrayList<String> all,int limit){
        q=normalizeSearch(q);if(q.length()<2)return new ArrayList<>();
        HashMap<String,Integer> score=new HashMap<>();ArrayList<String> copy=new ArrayList<>(all);
        for(String id:copy){String n=normalizeSearch(friendlyZoneName(id));score.put(id,n.contains(q)?0:editDistance(q,n));}
        copy.sort(Comparator.comparingInt(id->score.getOrDefault(id,999)));
        ArrayList<String> out=new ArrayList<>();int threshold=Math.max(2,q.length()/3);
        for(String id:copy){int d=score.getOrDefault(id,999);if(d<=threshold||out.size()<4){out.add(id);if(out.size()>=limit)break;}}return out;
    }

    private ArrayList<String> featuredZones(){
        return new ArrayList<>(Arrays.asList("Asia/Tokyo","Asia/Seoul","Asia/Shanghai","Asia/Singapore","Asia/Bangkok","Asia/Dubai","Europe/London","Europe/Paris","Europe/Berlin","Europe/Rome","America/New_York","America/Chicago","America/Denver","America/Los_Angeles","America/Toronto","America/Vancouver","America/Mexico_City","America/Sao_Paulo","America/Argentina/Buenos_Aires","Australia/Sydney","Australia/Perth","Pacific/Auckland","Africa/Cairo","Africa/Johannesburg"));
    }

    private String clipboardText(){
        try{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null&&cm.hasPrimaryClip()){ClipData d=cm.getPrimaryClip();if(d!=null&&d.getItemCount()>0){CharSequence x=d.getItemAt(0).coerceToText(this);return x==null?"":x.toString().trim();}}}catch(Throwable ignored){}return "";
    }

    private android.graphics.Bitmap googleUseIcon(){
        int n=Ui.dp(this,48);android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(n,n,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c=new android.graphics.Canvas(b);android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(Math.max(3f,n*.07f));p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        float m=n*.23f;c.drawRect(m,n*.18f,n*.70f,n*.70f,p);c.drawLine(n*.36f,n*.80f,n*.80f,n*.80f,p);c.drawLine(n*.80f,n*.80f,n*.68f,n*.68f,p);c.drawLine(n*.80f,n*.80f,n*.68f,n*.92f,p);return b;
    }

    private void openGoogleCountrySearch(String query){
        googleClipboardBefore=clipboardText();String q=query==null?"":query.trim();if(q.isEmpty())q=I18n.tr(this,"国名が分からない場所");
        String url="https://www.google.com/search?q="+android.net.Uri.encode(q+" "+I18n.tr(this,"国名"));
        Intent back=new Intent(this,ClockActivity.class).putExtra("mode","world").putExtra("use_clipboard",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent useClip=PendingIntent.getActivity(this,9127,back,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{
            int h=(int)(getResources().getDisplayMetrics().heightPixels*.72f);
            CustomTabsIntent tab=new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setInitialActivityHeightPx(h,CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
                    .setActionButton(googleUseIcon(),I18n.tr(this,"コピーした文字を使う"),useClip,true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                    .build();
            tab.launchUrl(this,android.net.Uri.parse(url));
        }catch(Throwable t){
            try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Throwable ignored){Toast.makeText(this,I18n.tr(this,"ブラウザを開けません"),Toast.LENGTH_SHORT).show();}
        }
    }

    private void applyGoogleClipboardSearchIfNeeded(){
        if(zonePickerDialog==null||!zonePickerDialog.isShowing()||zoneSearchBox==null)return;
        String now=clipboardText();if(now.isEmpty()||now.equals(googleClipboardBefore)||now.length()>80)return;
        googleClipboardBefore=now;zoneSearchBox.setText(now);zoneSearchBox.setSelection(zoneSearchBox.length());
    }

    private void applyResolvedCountries(List<Address> addresses,EditText search,Button online){
        LinkedHashMap<String,String> found=new LinkedHashMap<>();
        if(addresses!=null)for(Address a:addresses){
            if(a==null)continue;String code=a.getCountryCode();if(code==null||code.trim().isEmpty())continue;code=code.trim().toUpperCase(Locale.ROOT);
            String label=countryName(code,I18n.locale(this));if(label==null||label.isEmpty())label=a.getCountryName();if(label==null||label.isEmpty())label=code;found.put(code,label);
        }
        online.setEnabled(true);online.setText(I18n.tr(this,"オンラインで場所を特定"));
        if(found.isEmpty()){Toast.makeText(this,I18n.tr(this,"オンライン候補が見つかりませんでした"),Toast.LENGTH_SHORT).show();return;}
        if(found.size()==1){String label=found.values().iterator().next();search.setText(label);search.setSelection(search.length());return;}
        ArrayList<String> codes=new ArrayList<>(found.keySet());String[] labels=new String[codes.size()];for(int i=0;i<codes.size();i++)labels[i]=found.get(codes.get(i));
        new AlertDialog.Builder(this).setTitle(I18n.tr(this,"国を選択")).setItems(labels,(d,which)->{search.setText(labels[which]);search.setSelection(search.length());}).setNegativeButton(I18n.tr(this,"キャンセル"),null).show();
    }

    private void onlinePlaceLookup(String raw,EditText search,Button online){
        String q=raw==null?"":raw.trim();if(q.isEmpty()){Toast.makeText(this,I18n.tr(this,"場所を入力してください"),Toast.LENGTH_SHORT).show();return;}
        if(!Geocoder.isPresent()){Toast.makeText(this,I18n.tr(this,"この端末ではオンライン場所検索を利用できません"),Toast.LENGTH_SHORT).show();return;}
        online.setEnabled(false);online.setText(I18n.tr(this,"検索中…"));Geocoder g=new Geocoder(this,I18n.locale(this));
        if(Build.VERSION.SDK_INT>=33){
            try{g.getFromLocationName(q,5,new Geocoder.GeocodeListener(){
                @Override public void onGeocode(List<Address> addresses){runOnUiThread(()->applyResolvedCountries(addresses,search,online));}
                @Override public void onError(String errorMessage){runOnUiThread(()->{online.setEnabled(true);online.setText(I18n.tr(ClockActivity.this,"オンラインで場所を特定"));Toast.makeText(ClockActivity.this,I18n.tr(ClockActivity.this,"オンライン候補が見つかりませんでした"),Toast.LENGTH_SHORT).show();});}
            });}catch(Throwable t){online.setEnabled(true);online.setText(I18n.tr(this,"オンラインで場所を特定"));}
        }else{
            Executors.newSingleThreadExecutor().execute(()->{List<Address> a=null;try{a=g.getFromLocationName(q,5);}catch(Throwable ignored){}final List<Address> r=a;runOnUiThread(()->applyResolvedCountries(r,search,online));});
        }
    }

    private boolean hasValidatedInternet(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            if(cm==null)return false;
            android.net.Network network=cm.getActiveNetwork();
            if(network==null)return false;
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Throwable ignored){return false;}
    }

    private ArrayList<String> resolvedCountryCodes(List<Address> addresses){
        LinkedHashSet<String> codes=new LinkedHashSet<>();
        if(addresses!=null)for(Address a:addresses){
            if(a==null)continue;String code=a.getCountryCode();if(code==null)continue;code=code.trim().toUpperCase(Locale.ROOT);if(!code.isEmpty())codes.add(code);
        }
        return new ArrayList<>(codes);
    }

    private void resolveOnlineCountryHints(String raw,java.util.function.Consumer<ArrayList<String>> done){
        String q=raw==null?"":raw.trim();
        if(q.isEmpty()||!hasValidatedInternet()||!Geocoder.isPresent()){done.accept(new ArrayList<>());return;}
        Geocoder g=new Geocoder(this,I18n.locale(this));
        if(Build.VERSION.SDK_INT>=33){
            try{g.getFromLocationName(q,5,new Geocoder.GeocodeListener(){
                @Override public void onGeocode(List<Address> addresses){ArrayList<String> codes=resolvedCountryCodes(addresses);runOnUiThread(()->done.accept(codes));}
                @Override public void onError(String errorMessage){runOnUiThread(()->done.accept(new ArrayList<>()));}
            });}catch(Throwable t){done.accept(new ArrayList<>());}
        }else{
            new Thread(()->{List<Address> addresses=null;try{addresses=g.getFromLocationName(q,5);}catch(Throwable ignored){}ArrayList<String> codes=resolvedCountryCodes(addresses);runOnUiThread(()->done.accept(codes));},"WakeGuard-Geocoder").start();
        }
    }

    private String formatOffset(int totalSec) {
        int abs = Math.abs(totalSec); int h = abs / 3600; int m = (abs % 3600) / 60;
        return String.format(Locale.US, "%s%02d:%02d", totalSec >= 0 ? "+" : "-", h, m);
    }

    private String formatDiff(int sec) {
        if (sec == 0) return "±0:00";
        int abs = Math.abs(sec); int h = abs / 3600; int m = (abs % 3600) / 60;
        return String.format(Locale.US, "%s%d:%02d", sec > 0 ? "+" : "-", h, m);
    }

    private void showZonePicker() {
        final Dialog dialog=new Dialog(this);zonePickerDialog=dialog;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,14));box.setBackground(Ui.round(Ui.SURFACE,22,this));

        TextView title=text("都市 / タイムゾーンを追加",21,Ui.TEXT);title.setTypeface(null,Typeface.BOLD);box.addView(title);
        TextView subtitle=text("SeattleのようにIANAの代表都市名にない都市も検索できます。正式な全タイムゾーンも表示します",12,Ui.MUTED);subtitle.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,12));box.addView(subtitle);

        LinearLayout searchRow=Ui.row(this);
        EditText search=new EditText(this);zoneSearchBox=search;search.setHint(I18n.tr(this,"日本語・English・한국어で都市 / 国 / タイムゾーンを検索"));search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED_2);search.setSingleLine(true);search.setTextSize(16);search.setPadding(Ui.dp(this,15),0,Ui.dp(this,15),0);search.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,16,this));searchRow.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));
        Button paste=Ui.ghostButton(this,"貼付");paste.setTextSize(12);paste.setOnClickListener(v->{String clip=clipboardText();if(clip.isEmpty())Toast.makeText(this,I18n.tr(this,"コピーした文字がありません"),Toast.LENGTH_SHORT).show();else{search.setText(clip);search.setSelection(search.length());}});LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(Ui.dp(this,68),Ui.dp(this,54));pp.setMargins(Ui.dp(this,8),0,0,0);searchRow.addView(paste,pp);box.addView(searchRow);

        HorizontalScrollView chipScroll=new HorizontalScrollView(this);chipScroll.setHorizontalScrollBarEnabled(false);LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);chips.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,7));chipScroll.addView(chips);box.addView(chipScroll,new LinearLayout.LayoutParams(-1,-2));
        for(String code:new String[]{"JP","KR","US","GB","AU","CA"}){
            String label=countryName(code,I18n.locale(this));if(label==null||label.isEmpty())continue;
            TextView chip=new TextView(this);chip.setText(label);chip.setTextColor(Ui.ACCENT);chip.setTextSize(12);chip.setGravity(Gravity.CENTER);chip.setPadding(Ui.dp(this,12),Ui.dp(this,7),Ui.dp(this,12),Ui.dp(this,7));chip.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));chip.setClickable(true);chip.setOnClickListener(v->search.setText(label));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMargins(0,0,Ui.dp(this,8),0);chips.addView(chip,cp);
        }

        TextView suggestionTitle=text("おすすめ",12,Ui.MUTED);suggestionTitle.setTypeface(null,Typeface.BOLD);box.addView(suggestionTitle,Ui.gapTop(this,2));
        HorizontalScrollView suggestionScroll=new HorizontalScrollView(this);suggestionScroll.setHorizontalScrollBarEnabled(false);LinearLayout suggestionChips=new LinearLayout(this);suggestionChips.setOrientation(LinearLayout.HORIZONTAL);suggestionChips.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,7));suggestionScroll.addView(suggestionChips);box.addView(suggestionScroll,new LinearLayout.LayoutParams(-1,-2));

        ListView list=new ListView(this);list.setBackgroundColor(Color.TRANSPARENT);list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(Ui.dp(this,8));list.setClipToPadding(false);list.setPadding(0,0,0,Ui.dp(this,6));box.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView hybridHint=text("日本語・English・한국어の都市名を高速オフライン検索。正式なIANAタイムゾーンも検索します",10,Ui.MUTED_2);hybridHint.setGravity(Gravity.CENTER);hybridHint.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,5));box.addView(hybridHint);
        Button google=Ui.button(this,"Googleで確認（アプリ内）",false);google.setOnClickListener(v->openGoogleCountrySearch(search.getText().toString()));box.addView(google,new LinearLayout.LayoutParams(-1,Ui.dp(this,46)));
        TextView googleHint=text("GoogleはIGNIDO Wakeの上に開きます。上の検索欄を残したまま確認でき、文字をコピーしてツールバーのボタンを押すか、選択した文字をIGNIDO Wakeへ直接渡せます",10,Ui.MUTED_2);googleHint.setGravity(Gravity.CENTER);googleHint.setPadding(0,Ui.dp(this,3),0,Ui.dp(this,6));box.addView(googleHint);
        Button cancel=Ui.button(this,"閉じる",false);cancel.setOnClickListener(v->dialog.dismiss());box.addView(cancel,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));
        dialog.setContentView(box);

        final ArrayList<String> all=new ArrayList<>();
        final ConcurrentHashMap<String,ArrayList<String>> onlineHintCache=new ConcurrentHashMap<>();
        final Set<String> onlineLookupRunning=ConcurrentHashMap.newKeySet();
        final Runnable[] pendingOnlineSearch=new Runnable[1];
        final HashMap<String,String> searchCache=new HashMap<>(),cityCache=new HashMap<>(),countryCache=new HashMap<>(),countryLabelCache=new HashMap<>(),displayCityCache=new HashMap<>();
        final HashMap<String,ArrayList<String>> countryTermsCache=new HashMap<>();
        final HashMap<String,ArrayList<String>> zonesByCountry=new HashMap<>();
        final ArrayList<String> filtered=new ArrayList<>();
        for(String id:featuredZones())filtered.add(id);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,filtered){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){
                LinearLayout card;TextView city,countryView,nowText,detail;Object[] holder=null;
                if(convertView instanceof LinearLayout && convertView.getTag() instanceof Object[]){
                    holder=(Object[])convertView.getTag();card=(LinearLayout)convertView;city=(TextView)holder[0];countryView=(TextView)holder[1];nowText=(TextView)holder[2];detail=(TextView)holder[3];
                }else{
                    card=new LinearLayout(ClockActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(ClockActivity.this,15),Ui.dp(ClockActivity.this,12),Ui.dp(ClockActivity.this,15),Ui.dp(ClockActivity.this,12));card.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,16,ClockActivity.this));
                    LinearLayout top=new LinearLayout(ClockActivity.this);top.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout names=new LinearLayout(ClockActivity.this);names.setOrientation(LinearLayout.VERTICAL);
                    city=new TextView(ClockActivity.this);city.setTextSize(17);city.setTextColor(Ui.TEXT);city.setTypeface(null,Typeface.BOLD);names.addView(city);
                    countryView=new TextView(ClockActivity.this);countryView.setTextSize(12);countryView.setTextColor(Ui.ACCENT);names.addView(countryView,Ui.gapTop(ClockActivity.this,1));
                    top.addView(names,new LinearLayout.LayoutParams(0,-2,1));
                    nowText=new TextView(ClockActivity.this);nowText.setTextColor(Ui.TEXT);nowText.setTextSize(18);nowText.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);nowText.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);top.addView(nowText);card.addView(top);
                    detail=new TextView(ClockActivity.this);detail.setTextSize(11);detail.setTextColor(Ui.MUTED_2);card.addView(detail,Ui.gapTop(ClockActivity.this,5));
                    holder=new Object[]{city,countryView,nowText,detail};card.setTag(holder);
                }
                String item=getItem(position);
                String id=WorldCityCatalog.zoneOf(item);
                String cityLabel=WorldCityCatalog.labelOf(item,friendlyZoneName(id));city.setText(cityLabel);
                String code=WorldCityCatalog.countryOf(item,countryCache.getOrDefault(id,""));if(code==null||code.isEmpty())code=countryCodeForZone(id);String country=countryLabelCache.get(code);if(country==null||country.isEmpty())country=countryName(code,I18n.locale(ClockActivity.this));countryView.setText(country==null||country.isEmpty()?code:country);
                try{ZonedDateTime z=ZonedDateTime.now(ZoneId.of(id));nowText.setText(z.format(DateTimeFormatter.ofPattern(p().getBoolean(KEY_24H,true)?"HH:mm":"hh:mm a",I18n.locale(ClockActivity.this))));detail.setText("UTC"+formatOffset(z.getOffset().getTotalSeconds())+"  ·  "+id);}catch(Throwable t){nowText.setText("");detail.setText(id);}
                return card;
            }
        };
        list.setAdapter(adapter);

        final ExecutorService searchExecutor=Executors.newSingleThreadExecutor();final AtomicInteger searchGeneration=new AtomicInteger();final Runnable[] pendingSearch=new Runnable[1];final java.util.concurrent.atomic.AtomicBoolean indexReady=new java.util.concurrent.atomic.AtomicBoolean(false);final String[] queuedQuery=new String[]{""};
        final class SearchResult { final ArrayList<String> zones,codes; final String title; SearchResult(ArrayList<String> z,ArrayList<String> c,String t){zones=z;codes=c;title=t;} }
        final Runnable buildIndex=()->{
            ArrayList<String> built=fastSelectableZones();LinkedHashSet<String> uniqueCodes=new LinkedHashSet<>();
            for(String id:built){String code=countryCodeForZone(id);countryCache.put(id,code);if(!code.isEmpty()){uniqueCodes.add(code);zonesByCountry.computeIfAbsent(code,k->new ArrayList<>()).add(id);}cityCache.put(id,normalizeSearch(lightweightCityName(id)));}
            for(String code:uniqueCodes){String local=countryName(code,I18n.locale(this)),ja=countryName(code,Locale.JAPAN),ko=countryName(code,Locale.KOREA),en=countryName(code,Locale.ENGLISH);countryLabelCache.put(code,local);ArrayList<String> terms=new ArrayList<>();for(String x:new String[]{code,local,ja,ko,en}){String n=normalizeSearch(x);if(!n.isEmpty()&&!terms.contains(n))terms.add(n);}String aliases=normalizeSearch(countryAliases(code));if(!aliases.isEmpty()){if(!terms.contains(aliases))terms.add(aliases);for(String x:aliases.split("\\s+"))if(x.length()>1&&!terms.contains(x))terms.add(x);}countryTermsCache.put(code,terms);}
            for(String id:built){String code=countryCache.getOrDefault(id,"");StringBuilder b=new StringBuilder(id).append(' ').append(lightweightCityName(id)).append(' ').append(code);for(String term:countryTermsCache.getOrDefault(code,new ArrayList<>()))b.append(' ').append(term);searchCache.put(id,normalizeSearch(b.toString()));}
            all.addAll(built);indexReady.set(true);
        };
        searchExecutor.execute(buildIndex);
        final java.util.function.Consumer<String> requestSearch=(raw)->{
            final String q=normalizeSearch(raw);queuedQuery[0]=q;final int generation=searchGeneration.incrementAndGet();
            searchExecutor.execute(()->{
                if(!indexReady.get()){return;}
                if(generation!=searchGeneration.get())return;
                LinkedHashSet<String> results=new LinkedHashSet<>();ArrayList<String> guessed=new ArrayList<>();String titleKey="おすすめ";LinkedHashSet<String> regionCodes=regionCountryCodes(q);ArrayList<String> onlineCodes=onlineHintCache.get(q);if(onlineCodes==null)onlineCodes=new ArrayList<>();
                if(q.isEmpty()){
                    for(String id:featuredZones())if(all.contains(id))results.add(id);
                }else{
                    ArrayList<String> direct=new ArrayList<>();for(String id:all){String cached=searchCache.get(id);if(cached!=null&&cached.contains(q))direct.add(id);}
                    direct.sort((a,b)->{String ca=normalizeSearch(countryLabelCache.getOrDefault(countryCache.getOrDefault(a,""),"")),cb=normalizeSearch(countryLabelCache.getOrDefault(countryCache.getOrDefault(b,""),""));boolean ae=ca.equals(q),be=cb.equals(q);if(ae!=be)return ae?-1:1;String aa=cityCache.getOrDefault(a,""),bb=cityCache.getOrDefault(b,"");boolean as=aa.startsWith(q),bs=bb.startsWith(q);if(as!=bs)return as?-1:1;return aa.compareTo(bb);});
                    results.addAll(direct);
                    if(!onlineCodes.isEmpty()){for(String code:onlineCodes){if(!guessed.contains(code))guessed.add(code);ArrayList<String> zs=zonesByCountry.get(code);if(zs==null)continue;int n=0;for(String id:zs){results.add(id);if(++n>=12||results.size()>=90)break;}if(results.size()>=90)break;}}
                    if(!regionCodes.isEmpty()){
                        titleKey="地域からの候補";for(String code:regionCodes){ArrayList<String> zs=zonesByCountry.get(code);if(zs==null)continue;int n=0;for(String id:zs){results.add(id);if(++n>=4||results.size()>=80)break;}if(results.size()>=80)break;}guessed.addAll(regionCodes);
                    }else{
                        HashMap<String,Integer> scores=new HashMap<>();ArrayList<String> codes=new ArrayList<>(countryTermsCache.keySet());int threshold=Math.max(2,q.length()/3);
                        for(String code:codes){int best=999;for(String term:countryTermsCache.getOrDefault(code,new ArrayList<>())){if(term.equals(q)){best=0;break;}if(term.contains(q))best=Math.min(best,1);else if(term.startsWith(q)||q.startsWith(term))best=Math.min(best,1+Math.abs(term.length()-q.length())/3);if(q.length()>=2)best=Math.min(best,editDistance(q,term));}scores.put(code,best);}
                        codes.sort(Comparator.comparingInt(c->scores.getOrDefault(c,999)));for(String code:codes){int score=scores.getOrDefault(code,999);if(score<=threshold||guessed.size()<2){guessed.add(code);if(guessed.size()>=4)break;}}
                        boolean exactCountry=false;for(String c:guessed){for(String term:countryTermsCache.getOrDefault(c,new ArrayList<>()))if(term.equals(q)){exactCountry=true;break;}if(exactCountry)break;}
                        titleKey=exactCountry?"関連する国":"もしかして";
                        if(direct.size()<35){for(String code:guessed){ArrayList<String> zs=zonesByCountry.get(code);if(zs==null)continue;int n=0;for(String id:zs){results.add(id);if(++n>=8||results.size()>=90)break;}if(results.size()>=90)break;}}
                        if(q.length()>=2&&direct.size()<12){ArrayList<String> candidates=new ArrayList<>(all);HashMap<String,Integer> dists=new HashMap<>();for(String id:candidates){String n=cityCache.getOrDefault(id,"");dists.put(id,n.contains(q)?0:editDistance(q,n));}candidates.sort(Comparator.comparingInt(id->dists.getOrDefault(id,999)));int lim=0;for(String id:candidates){int d=dists.getOrDefault(id,999);if(d<=threshold||lim<4){results.add(id);if(++lim>=10)break;}}}
                    }
                    if(results.size()<16)for(String id:featuredZones())if(all.contains(id))results.add(id);
                }
                ArrayList<String> out=new ArrayList<>();int limit=q.isEmpty()?34:70;
                if(q.isEmpty()){for(WorldCityCatalog.Entry e:WorldCityCatalog.popular(this,12)){out.add(WorldCityCatalog.encode(e));if(out.size()>=limit)break;}}
                else{for(WorldCityCatalog.Entry e:WorldCityCatalog.search(this,q,52)){out.add(WorldCityCatalog.encode(e));if(out.size()>=limit)break;}}
                for(String id:results){if(out.size()>=limit)break;out.add(id);}
                SearchResult result=new SearchResult(out,new ArrayList<>(guessed),titleKey);
                handler.post(()->{if(generation!=searchGeneration.get()||zonePickerDialog!=dialog)return;filtered.clear();filtered.addAll(result.zones);adapter.notifyDataSetChanged();suggestionChips.removeAllViews();suggestionTitle.setText(I18n.tr(this,result.title));int chipCount=0;for(String code:result.codes){if(chipCount>=6)break;String label=countryLabelCache.get(code);if(label==null||label.isEmpty())label=countryName(code,I18n.locale(this));if(label==null||label.isEmpty())continue;TextView chip=new TextView(this);chip.setText(label);chip.setTextColor(Ui.ACCENT);chip.setTextSize(12);chip.setTypeface(null,Typeface.BOLD);chip.setPadding(Ui.dp(this,11),Ui.dp(this,6),Ui.dp(this,11),Ui.dp(this,6));chip.setBackground(Ui.roundStroke(Ui.SURFACE_3,Ui.BORDER,13,this));final String chipLabel=label;chip.setOnClickListener(v->search.setText(chipLabel));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMargins(0,0,Ui.dp(this,7),0);suggestionChips.addView(chip,cp);chipCount++;}
                    if(q.isEmpty()){for(String code:new String[]{"US","KR","JP","GB","FR","DE","AU","CA"}){String label=countryLabelCache.get(code);if(label==null||label.isEmpty())label=countryName(code,I18n.locale(this));TextView chip=new TextView(this);chip.setText(label);chip.setTextColor(Ui.ACCENT);chip.setTextSize(12);chip.setPadding(Ui.dp(this,11),Ui.dp(this,6),Ui.dp(this,11),Ui.dp(this,6));chip.setBackground(Ui.roundStroke(Ui.SURFACE_3,Ui.BORDER,13,this));final String chipLabel=label;chip.setOnClickListener(v->search.setText(chipLabel));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMargins(0,0,Ui.dp(this,7),0);suggestionChips.addView(chip,cp);}}
                    suggestionTitle.setVisibility(q.isEmpty()||chipCount>0?View.VISIBLE:View.GONE);suggestionScroll.setVisibility(q.isEmpty()||chipCount>0?View.VISIBLE:View.GONE);
                });
            });
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){if(pendingSearch[0]!=null)handler.removeCallbacks(pendingSearch[0]);if(pendingOnlineSearch[0]!=null)handler.removeCallbacks(pendingOnlineSearch[0]);final String q=s==null?"":s.toString();final String onlineKey=normalizeSearch(q);pendingSearch[0]=()->{if(indexReady.get())requestSearch.accept(q);else{queuedQuery[0]=normalizeSearch(q);final int generation=searchGeneration.incrementAndGet();searchExecutor.execute(()->{if(!indexReady.get())return;final String latest=queuedQuery[0];handler.post(()->{if(generation==searchGeneration.get()&&zonePickerDialog==dialog)requestSearch.accept(latest);});});}};handler.postDelayed(pendingSearch[0],q.trim().isEmpty()?0L:18L);if(onlineKey.length()>=2&&!onlineHintCache.containsKey(onlineKey)){pendingOnlineSearch[0]=()->{if(zonePickerDialog!=dialog||!hasValidatedInternet()||!Geocoder.isPresent()||onlineHintCache.containsKey(onlineKey)||!onlineLookupRunning.add(onlineKey))return;resolveOnlineCountryHints(q,codes->{onlineLookupRunning.remove(onlineKey);if(zonePickerDialog!=dialog||!normalizeSearch(search.getText().toString()).equals(onlineKey))return;if(codes!=null&&!codes.isEmpty()){onlineHintCache.put(onlineKey,codes);requestSearch.accept(q);}});};handler.postDelayed(pendingOnlineSearch[0],350L);}}public void afterTextChanged(Editable e){}});
        list.setOnItemClickListener((a,v,pos,id)->{String item=filtered.get(pos),z=WorldCityCatalog.zoneOf(item),label=WorldCityCatalog.labelOf(item,friendlyZoneName(z));LinkedHashSet<String> zones=loadZones();zones.add(z);saveZones(zones);WorldCityCatalog.saveWorldLabel(this,z,label);dialog.dismiss();showMode("world");});
        dialog.setOnDismissListener(d->{searchGeneration.incrementAndGet();searchExecutor.shutdownNow();if(pendingSearch[0]!=null)handler.removeCallbacks(pendingSearch[0]);if(pendingOnlineSearch[0]!=null)handler.removeCallbacks(pendingOnlineSearch[0]);if(zonePickerDialog==dialog){zonePickerDialog=null;zoneSearchBox=null;}});
        dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));w.setDimAmount(.72f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94f),(int)(getResources().getDisplayMetrics().heightPixels*.91f));}
        final int initialGeneration=searchGeneration.incrementAndGet();searchExecutor.execute(()->{if(!indexReady.get())return;handler.post(()->{if(initialGeneration==searchGeneration.get()&&zonePickerDialog==dialog)requestSearch.accept(queuedQuery[0]);});});
    }

    private boolean stopwatchAnalog(){ return p().getBoolean("stopwatch_face_analog",false); }
    private void toggleStopwatchFace(){ p().edit().putBoolean("stopwatch_face_analog",!stopwatchAnalog()).apply(); applyStopwatchFaceMode(); updateStopwatch(); }
    private void applyStopwatchFaceMode(){
        if(stopwatchStage==null||stopwatchDisplay==null||stopwatchAnalog==null)return;
        boolean analog=stopwatchAnalog();
        stopwatchDisplay.setVisibility(analog?View.GONE:View.VISIBLE);
        stopwatchAnalog.setVisibility(analog?View.VISIBLE:View.GONE);
        android.view.ViewGroup.LayoutParams raw=stopwatchStage.getLayoutParams();
        if(raw!=null){raw.height=Ui.dp(this,analog?240:172);stopwatchStage.setLayoutParams(raw);}
    }

    private void buildStopwatch() {
        stopwatchStage=new FrameLayout(this);
        stopwatchDisplay=text("00:00:00.00",43,Ui.TEXT);
        stopwatchDisplay.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); stopwatchDisplay.setGravity(Gravity.CENTER); stopwatchDisplay.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,14));
        stopwatchAnalog=new ClockFaceActivity.AnalogFace(this);
        stopwatchAnalog.configure(p().getBoolean("analog_numbers",true),p().getBoolean("analog_ticks",true),p().getBoolean("analog_seconds",true),p().getInt("analog_number_scale",1));
        stopwatchStage.addView(stopwatchDisplay,new FrameLayout.LayoutParams(-1,-1));
        stopwatchStage.addView(stopwatchAnalog,new FrameLayout.LayoutParams(-1,-1));
        stopwatchStage.setClickable(true);stopwatchStage.setLongClickable(true);
        stopwatchStage.setOnClickListener(v->toggleStopwatchFace());
        stopwatchStage.setOnLongClickListener(v->{openClockFace("stopwatch",null,-1L);return true;});
        body.addView(new View(this),new LinearLayout.LayoutParams(-1,Ui.dp(this,16)));
        stopwatchStage.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,24,this));
        body.addView(stopwatchStage,new LinearLayout.LayoutParams(-1,Ui.dp(this,192)));
        stopwatchStateHint=Ui.text(this,"停止中  ·  タップで表示切替、長押しで全画面",11,Ui.MUTED_2);stopwatchStateHint.setGravity(Gravity.CENTER);body.addView(stopwatchStateHint,Ui.gapTop(this,8));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,Ui.dp(this,24),0,0);
        stopwatchLapReset=Ui.button(this,"リセット",false); row.addView(stopwatchLapReset,new LinearLayout.LayoutParams(0,Ui.dp(this,56),1));row.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,12),1));
        stopwatchStartPause=Ui.button(this,"スタート",true); row.addView(stopwatchStartPause,new LinearLayout.LayoutParams(0,Ui.dp(this,56),1)); body.addView(row);
        stopwatchStartPause.setOnClickListener(v->toggleStopwatch()); stopwatchLapReset.setOnClickListener(v->lapOrReset());
        TextView logHeader=Ui.sectionHeader(this,"記録");
        body.addView(logHeader,Ui.gapTop(this,28));
        LinearLayout importRow=new LinearLayout(this);importRow.setGravity(Gravity.CENTER_VERTICAL);importRow.setPadding(0,Ui.dp(this,6),0,0);
        stopwatchImport=Ui.button(this,"時間記録に追加",false);stopwatchImport.setOnClickListener(v->importStoppedStopwatch());importRow.addView(stopwatchImport,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        stopwatchLapImport=Ui.ghostButton(this,"ラップから追加");stopwatchLapImport.setOnClickListener(v->showLapImportPicker());importRow.addView(stopwatchLapImport,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));body.addView(importRow);
        Button timeLog=Ui.ghostButton(this,"時間記録を開く");timeLog.setOnClickListener(v->startActivity(new Intent(this,TimeLogActivity.class)));LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,Ui.dp(this,46));tlp.setMargins(0,Ui.dp(this,4),0,0);body.addView(timeLog,tlp);
        lapList=text("",15,Ui.MUTED); lapList.setTypeface(Typeface.MONOSPACE); lapList.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,8)); body.addView(lapList); renderLaps();
        applyStopwatchFaceMode();updateStopwatch();
    }

    private long stopwatchElapsed() {
        android.content.SharedPreferences sp = p();
        long saved = sp.getLong(KEY_SW_ELAPSED, 0L);
        if (!sp.getBoolean(KEY_SW_RUNNING, false)) return saved;
        long base = sp.getLong(KEY_SW_BASE, SystemClock.elapsedRealtime());
        long now = SystemClock.elapsedRealtime();
        if (base > now) {
            sp.edit().putBoolean(KEY_SW_RUNNING,false).putLong(KEY_SW_BASE,0L).apply();
            return saved;
        }
        return saved + Math.max(0L, now - base);
    }

    private void toggleStopwatch() {
        android.content.SharedPreferences sp = p();
        if (sp.getBoolean(KEY_SW_RUNNING, false)) {
            long elapsed = stopwatchElapsed();
            sp.edit().putBoolean(KEY_SW_RUNNING,false).putLong(KEY_SW_ELAPSED,elapsed).putLong(KEY_SW_BASE,0L).putLong(KEY_SW_STOPPED_WALL,System.currentTimeMillis()).apply();
        } else {
            sp.edit().putBoolean(KEY_SW_RUNNING,true).putLong(KEY_SW_BASE,SystemClock.elapsedRealtime()).apply();
        }
        updateStopwatch(); syncStopwatchNotification(); try{WidgetSuite.refreshAll(this);}catch(Throwable ignored){}
    }

    private void lapOrReset() {
        android.content.SharedPreferences sp = p();
        if (sp.getBoolean(KEY_SW_RUNNING,false)) {
            ArrayList<Long> laps = loadLaps(); laps.add(stopwatchElapsed()); saveLaps(laps);
            ArrayList<Long> walls=loadLapWalls();walls.add(System.currentTimeMillis());saveLapWalls(walls);renderLaps();
        } else {
            sp.edit().putLong(KEY_SW_ELAPSED,0L).putLong(KEY_SW_BASE,0L).remove(KEY_SW_LAPS).remove(KEY_SW_LAP_WALLS).remove(KEY_SW_STOPPED_WALL).apply(); renderLaps();
        }
        updateStopwatch(); syncStopwatchNotification(); try{WidgetSuite.refreshAll(this);}catch(Throwable ignored){}
    }

    private ArrayList<Long> loadLaps() {
        ArrayList<Long> out = new ArrayList<>(); String raw = p().getString(KEY_SW_LAPS, "");
        if (raw != null && !raw.isEmpty()) for (String s : raw.split(",")) try { out.add(Long.parseLong(s)); } catch (Exception ignored) {}
        return out;
    }
    private void saveLaps(ArrayList<Long> laps) {
        StringBuilder b = new StringBuilder(); for (long v : laps) { if (b.length()>0) b.append(','); b.append(v); }
        p().edit().putString(KEY_SW_LAPS,b.toString()).apply();
    }
    private ArrayList<Long> loadLapWalls() {
        ArrayList<Long> out=new ArrayList<>();String raw=p().getString(KEY_SW_LAP_WALLS,"");
        if(raw!=null&&!raw.isEmpty())for(String x:raw.split(","))try{out.add(Long.parseLong(x));}catch(Exception ignored){}
        return out;
    }
    private void saveLapWalls(ArrayList<Long> values){StringBuilder b=new StringBuilder();for(long v:values){if(b.length()>0)b.append(',');b.append(v);}p().edit().putString(KEY_SW_LAP_WALLS,b.toString()).apply();}
    private void openTimeLogImport(long durationMs,long endWallMs,String label){
        if(durationMs<=0L)return;Intent i=new Intent(this,TimeLogActivity.class).putExtra("importDurationMs",durationMs).putExtra("importEndWallMs",endWallMs>0L?endWallMs:System.currentTimeMillis()).putExtra("importLabel",label==null?"ストップウォッチ":label);startActivity(i);
    }
    private void importStoppedStopwatch(){
        if(p().getBoolean(KEY_SW_RUNNING,false)){Toast.makeText(this,"ストップウォッチを停止してから追加してください",Toast.LENGTH_SHORT).show();return;}
        long elapsed=stopwatchElapsed();if(elapsed<=0L)return;long end=p().getLong(KEY_SW_STOPPED_WALL,System.currentTimeMillis());openTimeLogImport(elapsed,end,"ストップウォッチ");
    }
    private void showLapImportPicker(){
        ArrayList<Long> laps=loadLaps();if(laps.isEmpty()){Toast.makeText(this,"ラップがありません",Toast.LENGTH_SHORT).show();return;}
        ArrayList<Long> walls=loadLapWalls();String[] items=new String[laps.size()];
        for(int i=0;i<laps.size();i++){long total=laps.get(i),prev=i==0?0L:laps.get(i-1),split=Math.max(0L,total-prev);items[i]="ラップ "+(i+1)+"   "+formatStopwatch(split);}
        new AlertDialog.Builder(this).setTitle("時間記録に追加するラップ").setItems(items,(d,which)->{
            long total=laps.get(which),prev=which==0?0L:laps.get(which-1),split=Math.max(0L,total-prev);long end=which<walls.size()?walls.get(which):0L;
            if(end<=0L){long ref=p().getBoolean(KEY_SW_RUNNING,false)?System.currentTimeMillis():p().getLong(KEY_SW_STOPPED_WALL,System.currentTimeMillis());end=ref-Math.max(0L,stopwatchElapsed()-total);}
            openTimeLogImport(split,end,"ラップ "+(which+1));
        }).show();
    }

    private void renderLaps() {
        if (lapList == null) return; ArrayList<Long> laps=loadLaps(); StringBuilder b=new StringBuilder(); long prev=0;
        for (int i=laps.size()-1;i>=0;i--) { long total=laps.get(i); long split=total-(i==0?0:laps.get(i-1));
            b.append(String.format(Locale.US,"Lap %02d   %s   合計 %s\n", i+1, formatStopwatch(split), formatStopwatch(total))); }
        lapList.setText(b.length()==0 ? I18n.tr(this,"ラップはまだありません") : b.toString());
    }

    private boolean storeTimerVideo(TimerStore.Entry e,android.net.Uri uri,String display){
        java.io.File out=null;try{
            String lower=display==null?"":display.toLowerCase(java.util.Locale.ROOT);String ext=".mp4";String[] xs={".mp4",".m4v",".3gp",".3gpp",".webm",".mkv",".ts"};for(String x:xs)if(lower.endsWith(x)){ext=x;break;}
            java.io.File dir=new java.io.File(getFilesDir(),"timer_videos");if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("mkdir");out=new java.io.File(dir,"timer_"+e.id+"_"+System.currentTimeMillis()+ext);
            try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.OutputStream os=new java.io.FileOutputStream(out)){if(in==null)throw new java.io.IOException("open");byte[]buf=new byte[131072];int n;while((n=in.read(buf))>0)os.write(buf,0,n);}
            String old=e.soundUri;e.soundUri=out.getAbsolutePath();e.soundName=display;TimerStore.update(this,e);cleanupTimerVideo(old);return true;
        }catch(Throwable t){if(out!=null)try{out.delete();}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"この動画は読み込めません"),Toast.LENGTH_LONG).show();return false;}
    }
    private void cleanupTimerVideo(String raw){try{if(raw==null||raw.isEmpty())return;java.io.File f=new java.io.File(raw),dir=new java.io.File(getFilesDir(),"timer_videos");if(f.getParentFile()!=null&&f.getParentFile().equals(dir)&&f.exists())f.delete();}catch(Throwable ignored){}}

    private String formatStopwatch(long ms) {
        long h=ms/3600000L; long m=(ms/60000L)%60; long s=(ms/1000L)%60; long cs=(ms/10L)%100;
        return String.format(Locale.US,"%02d:%02d:%02d.%02d",h,m,s,cs);
    }
    private void updateStopwatch() {
        if (!"stopwatch".equals(mode) || stopwatchDisplay==null) return;
        boolean running=p().getBoolean(KEY_SW_RUNNING,false);
        long elapsed=stopwatchElapsed();
        stopwatchDisplay.setText(formatStopwatch(elapsed));
        if(stopwatchAnalog!=null){
            stopwatchAnalog.configure(p().getBoolean("analog_numbers",true),p().getBoolean("analog_ticks",true),p().getBoolean("analog_seconds",true),p().getInt("analog_number_scale",1));
            double sec=(elapsed/1000d)%60d,min=(elapsed/60000d)%60d,hr=(elapsed/3600000d)%12d;stopwatchAnalog.setHands(hr,min,sec);
        }
        stopwatchStartPause.setText(I18n.tr(this,running?"一時停止":"スタート"));
        stopwatchLapReset.setText(I18n.tr(this,running?"ラップ":"リセット"));
        if(stopwatchStateHint!=null){stopwatchStateHint.setText(I18n.tr(this,running?"計測中":"停止中")+"  ·  タップで表示切替、長押しで全画面");stopwatchStateHint.setTextColor(running?Ui.ACCENT:Ui.MUTED_2);}
        if(stopwatchImport!=null){stopwatchImport.setEnabled(!running&&elapsed>0L);stopwatchImport.setAlpha((!running&&elapsed>0L)?1f:.45f);}
        if(stopwatchLapImport!=null){boolean hasLap=!loadLaps().isEmpty();stopwatchLapImport.setEnabled(hasLap);stopwatchLapImport.setAlpha(hasLap?1f:.45f);}
    }

    private void syncStopwatchNotification(){
        try{if(p().getBoolean(KEY_SW_RUNNING,false))StopwatchNotification.show(this,stopwatchElapsed());else StopwatchNotification.cancel(this);}catch(Throwable ignored){}
    }

    private void buildTimer() {
        TextView quickTitle=text("時間を設定",18,Ui.TEXT); quickTitle.setTypeface(null,Typeface.BOLD); body.addView(quickTitle);

        LinearLayout durationRow=new LinearLayout(this); durationRow.setGravity(Gravity.CENTER_VERTICAL); durationRow.setPadding(0,Ui.dp(this,12),0,0);
        quickHours=quickNumber("時"); quickMinutes=quickNumber("分"); quickSeconds=quickNumber("秒");
        installQuickDurationNormalization();
        durationRow.addView(quickHours,new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));
        TextView c1=text(":",27,Ui.MUTED);c1.setGravity(Gravity.CENTER);durationRow.addView(c1,new LinearLayout.LayoutParams(Ui.dp(this,24),-1));
        durationRow.addView(quickMinutes,new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));
        TextView c2=text(":",27,Ui.MUTED);c2.setGravity(Gravity.CENTER);durationRow.addView(c2,new LinearLayout.LayoutParams(Ui.dp(this,24),-1));
        durationRow.addView(quickSeconds,new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));
        LinearLayout durationCard=Ui.card(this);
        durationCard.setPadding(Ui.dp(this,14),Ui.dp(this,9),Ui.dp(this,14),Ui.dp(this,15));
        durationCard.addView(durationRow);
        body.addView(durationCard,Ui.gapTop(this,12));

        TextView presetLabel=text("よく使う時間",12,Ui.MUTED); body.addView(presetLabel,Ui.gapTop(this,12));
        HorizontalScrollView presetsScroll=new HorizontalScrollView(this);presetsScroll.setHorizontalScrollBarEnabled(false); LinearLayout presets=new LinearLayout(this);
        String[] presetNames={"30秒","1分","3分","5分","10分","30分","1時間"}; long[] presetMs={30000L,60000L,180000L,300000L,600000L,1800000L,3600000L};
        for(int i=0;i<presetNames.length;i++){final long value=presetMs[i];TextView a=timerTextAction(presetNames[i],13,Ui.TEXT);a.setOnClickListener(v->setQuickDuration(value));presets.addView(a);} presetsScroll.addView(presets);body.addView(presetsScroll,Ui.gapTop(this,2));

        TextView adjustLabel=text("細かく調整  ▾",14,Ui.MUTED);adjustLabel.setClickable(true);adjustLabel.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8)); body.addView(adjustLabel,Ui.gapTop(this,8));
        HorizontalScrollView adjustScroll=new HorizontalScrollView(this);adjustScroll.setHorizontalScrollBarEnabled(false);LinearLayout adjust=new LinearLayout(this);
        String[] labels={"−1秒","＋1秒","−10秒","＋10秒","−30秒","＋30秒","−1分","＋1分","−5分","＋5分","−10分","＋10分","−1時間","＋1時間"}; long[] deltas={-1000L,1000L,-10000L,10000L,-30000L,30000L,-60000L,60000L,-300000L,300000L,-600000L,600000L,-3600000L,3600000L};
        for(int i=0;i<labels.length;i++){final long delta=deltas[i];TextView a=timerTextAction(labels[i],13,Ui.TEXT);a.setOnClickListener(v->adjustQuickDuration(delta));adjust.addView(a);} adjustScroll.addView(adjust);body.addView(adjustScroll,Ui.gapTop(this,2));
        adjustScroll.setVisibility(View.GONE);
        adjustLabel.setOnClickListener(v->{boolean open=adjustScroll.getVisibility()!=View.VISIBLE;adjustScroll.setVisibility(open?View.VISIBLE:View.GONE);adjustLabel.setText(I18n.tr(this,open?"細かく調整  ▴":"細かく調整  ▾"));});

        LinearLayout saveRow=Ui.row(this); saveRow.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,8)); TextView saveLabel=text("この時間を保存",15,Ui.TEXT);saveRow.addView(saveLabel,new LinearLayout.LayoutParams(0,-2,1)); quickSave=new Switch(this);quickSave.setChecked(false);Ui.styleSwitch(quickSave,this);saveRow.addView(quickSave);body.addView(saveRow);
        Button start=Ui.button(this,"開始",true);start.setOnClickListener(v->startQuickEditor());body.addView(start,new LinearLayout.LayoutParams(-1,Ui.dp(this,56))); body.addView(Ui.divider(this),Ui.dividerTop(this,18));

        TextView listTitle=text("タイマー",16,Ui.TEXT);listTitle.setTypeface(null,Typeface.BOLD);listTitle.setPadding(0,Ui.dp(this,20),0,Ui.dp(this,4));body.addView(listTitle);
        timerList=new LinearLayout(this);timerList.setOrientation(LinearLayout.VERTICAL);body.addView(timerList);renderTimers();
    }

    private EditText quickNumber(String hint){
        EditText e=new EditText(this); e.setHint(I18n.tr(this,hint)); e.setHintTextColor(Ui.MUTED_2); e.setTextColor(Ui.TEXT); e.setTextSize(27);
        e.setGravity(Gravity.CENTER); e.setSingleLine(true); e.setSelectAllOnFocus(true); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),0);
        e.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));
        return e;
    }

    private void installQuickDurationNormalization(){
        View.OnFocusChangeListener onBlur=(v,hasFocus)->{if(!hasFocus)normalizeQuickDurationFields();};
        quickHours.setOnFocusChangeListener(onBlur); quickMinutes.setOnFocusChangeListener(onBlur); quickSeconds.setOnFocusChangeListener(onBlur);
        quickHours.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        quickMinutes.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        quickSeconds.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        quickSeconds.setOnEditorActionListener((v,actionId,event)->{
            boolean enter=event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_UP;
            if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_DONE||enter){
                normalizeQuickDurationFields();
                try{android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(imm!=null)imm.hideSoftInputFromWindow(v.getWindowToken(),0);}catch(Throwable ignored){}
                v.clearFocus(); return true;
            }
            return false;
        });
    }

    private boolean normalizeQuickDurationFields(){
        if(normalizingQuickDuration||quickHours==null||quickMinutes==null||quickSeconds==null)return false;
        long h=parseLong(quickHours),m=parseLong(quickMinutes),sec=parseLong(quickSeconds);
        if(m<60L&&sec<60L)return false;
        long totalSeconds;
        try{totalSeconds=Math.addExact(Math.addExact(Math.multiplyExact(h,3600L),Math.multiplyExact(m,60L)),sec);}catch(ArithmeticException ex){totalSeconds=Long.MAX_VALUE/1000L;}
        totalSeconds=Math.max(0L,Math.min(totalSeconds,Long.MAX_VALUE/1000L));
        normalizingQuickDuration=true;
        try{setQuickDuration(totalSeconds*1000L);}finally{normalizingQuickDuration=false;}
        return true;
    }

    private TextView timerTextAction(String label,float size,int color){
        TextView t=text(label,size,color); t.setGravity(Gravity.CENTER); t.setClickable(true); t.setFocusable(true);
        t.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
        t.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,12,this),
                Ui.round(android.graphics.Color.WHITE,12,this)));
        t.setMinHeight(Ui.dp(this,44));
        return t;
    }

    private long quickDuration(){
        return Math.max(0L,(parseLong(quickHours)*3600L+parseLong(quickMinutes)*60L+parseLong(quickSeconds))*1000L);
    }

    private void setQuickDuration(long ms){
        long sec=Math.max(0L,ms/1000L); long h=sec/3600L, m=(sec/60L)%60L, x=sec%60L;
        quickHours.setText(h==0?"":String.valueOf(h)); quickMinutes.setText(m==0?"":String.valueOf(m)); quickSeconds.setText(x==0?"":String.valueOf(x));
    }

    private void adjustQuickDuration(long delta){ setQuickDuration(Math.max(0L,quickDuration()+delta)); }

    private void startQuickEditor(){
        if(normalizeQuickDurationFields()){
            Toast.makeText(this,I18n.tr(this,"時間表記に直しました。確認してから開始してください"),Toast.LENGTH_SHORT).show();
            return;
        }
        long total=quickDuration(); if(total<=0){Toast.makeText(this,I18n.tr(this,"時間を入力してください"),Toast.LENGTH_SHORT).show();return;}
        boolean saved=quickSave!=null&&quickSave.isChecked(); TimerStore.Entry e=saved?TimerStore.addSaved(this,"",total):TimerStore.addTemporary(this,"",total); startTimerEntry(e); renderTimers();
    }

    private boolean timerAnalog(){ return p().getBoolean("timer_face_analog",false); }
    private void toggleTimerFace(){ p().edit().putBoolean("timer_face_analog",!timerAnalog()).apply(); renderTimers(); }

    private void renderTimers() {
        if(!"timer".equals(mode)||timerList==null)return;
        timerList.removeAllViews();
        List<TimerStore.Entry> timers=TimerStore.all(this);
        timers.sort((a,b)->{if(a.saved!=b.saved)return a.saved?1:-1;return Long.compare(a.id,b.id);});
        if(timers.isEmpty()){
            timerEmpty=text("保存したタイマーはありません",14,Ui.MUTED); timerEmpty.setGravity(Gravity.CENTER);
            timerEmpty.setPadding(0,Ui.dp(this,28),0,Ui.dp(this,18)); timerList.addView(timerEmpty); return;
        }
        for(TimerStore.Entry timer:timers)addTimerRow(timer);
    }

    private void addTimerRow(TimerStore.Entry e) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,14));row.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,18,this));
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        String label=e.label==null||e.label.isEmpty()?durationLabel(e.durationMs):e.label;TextView name=text(label,15,Ui.TEXT);titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView sound=timerTextAction(timerSoundText(e),13,Ui.MUTED);sound.setOnClickListener(v->pickTimerSound(e.id));titleRow.addView(sound);
        TextView more=timerTextAction("︙",22,Ui.MUTED);more.setOnClickListener(v->showTimerMenu(more,e.id));titleRow.addView(more);row.addView(titleRow);
        boolean analogMode=timerAnalog();
        FrameLayout timerStage=new FrameLayout(this);timerStage.setTag("timer_stage_"+e.id);timerStage.setClickable(true);timerStage.setLongClickable(true);
        TextView remain=text(formatTimer(e.remaining()),38,Ui.TEXT);remain.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);remain.setGravity(Gravity.CENTER_VERTICAL);remain.setTag("timer_remaining_"+e.id);
        ClockFaceActivity.AnalogFace timerFace=new ClockFaceActivity.AnalogFace(this);timerFace.configure(p().getBoolean("analog_numbers",true),p().getBoolean("analog_ticks",true),p().getBoolean("analog_seconds",true),p().getInt("analog_number_scale",1));timerFace.setTag("timer_analog_"+e.id);
        timerStage.addView(remain,new FrameLayout.LayoutParams(-1,-1));timerStage.addView(timerFace,new FrameLayout.LayoutParams(-1,-1));remain.setVisibility(analogMode?View.GONE:View.VISIBLE);timerFace.setVisibility(analogMode?View.VISIBLE:View.GONE);
        timerStage.setOnClickListener(v->toggleTimerFace());timerStage.setOnLongClickListener(v->{openClockFace("timer",null,e.id);return true;});
        LinearLayout.LayoutParams timerStageParams=new LinearLayout.LayoutParams(-1,Ui.dp(this,analogMode?220:72));timerStageParams.setMargins(0,Ui.dp(this,2),0,0);row.addView(timerStage,timerStageParams);
        TextView state=text(timerStateText(e),12,Ui.MUTED);state.setTextColor(e.running?Ui.ACCENT:(e.finished()?Ui.SUCCESS:Ui.MUTED));state.setTag("timer_state_"+e.id);row.addView(state,Ui.gapTop(this,1));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(0,Ui.dp(this,4),0,0);
        String startText=e.running?"一時停止":(e.finished()?"もう一度":(e.remainingMs<e.durationMs?"再開":"スタート"));TextView startPause=timerTextAction(startText,14,Ui.ACCENT);startPause.setOnClickListener(v->toggleTimer(e.id));controls.addView(startPause);
        TextView plus=timerTextAction("＋1分",14,Ui.TEXT);plus.setOnClickListener(v->addMinute(e.id));controls.addView(plus);row.addView(controls);LinearLayout.LayoutParams rowLp=Ui.cardParams(this);rowLp.setMargins(0,0,0,Ui.dp(this,10));timerList.addView(row,rowLp);
    }

    private void showTimerMenu(View anchor,long id){
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,6));box.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,12,this));
        PopupWindow popup=new PopupWindow(box,Ui.dp(this,176),WindowManager.LayoutParams.WRAP_CONTENT,true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setFocusable(true);if(Build.VERSION.SDK_INT>=21)popup.setElevation(Ui.dp(this,10));
        if(!e.saved)addTimerMenuItem(box,popup,"保存",()->{TimerStore.makeSaved(this,id);renderTimers();});
        addTimerMenuItem(box,popup,"リセット",()->resetTimer(id));
        addTimerMenuItem(box,popup,"削除",()->{TimerReceiver.cancel(this,id);TimerStore.delete(this,id);renderTimers();});
        popup.showAsDropDown(anchor,-Ui.dp(this,146),-Ui.dp(this,8));
    }

    private void addTimerMenuItem(LinearLayout box,PopupWindow popup,String label,Runnable action){
        TextView item=timerTextAction(label,15,Ui.TEXT);item.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);item.setMinHeight(Ui.dp(this,48));item.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);
        item.setOnClickListener(v->{popup.dismiss();action.run();});box.addView(item,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
    }

    private String timerSoundText(TimerStore.Entry e){
        String n=e==null?"":e.soundName;
        if(e==null||e.soundUri==null||e.soundUri.trim().isEmpty()||n==null||n.trim().isEmpty())return "音: 標準";
        n=n.trim(); if(n.length()>14)n=n.substring(0,13)+"…";
        return isVideoMedia(e.soundUri)?"動画: "+n:"音: "+n;
    }

    private boolean isVideoMedia(String raw){
        if(raw==null||raw.trim().isEmpty())return false;
        try{String type=getContentResolver().getType(android.net.Uri.parse(raw));if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}catch(Throwable ignored){}
        String x=raw.toLowerCase(java.util.Locale.ROOT);return x.matches(".*\\.(mp4|m4v|3gp|3gpp|webm|mkv|ts)([?#].*)?$");
    }

    private void pickTimerSound(long id){
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;
        new AlertDialog.Builder(this)
            .setTitle(I18n.tr(this,"タイマーの音・動画"))
            .setItems(new String[]{I18n.tr(this,"音声を選択"),I18n.tr(this,"動画を選択"),I18n.tr(this,"標準に戻す")},(d,which)->{
                if(which==0)pickTimerMedia(id,false);
                else if(which==1)pickTimerMedia(id,true);
                else {e.soundUri="";e.soundName="";TimerStore.update(this,e);renderTimers();}
            }).show();
    }

    private void pickTimerMedia(long id,boolean video){
        pendingTimerSoundId=id;
        android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        i.setType(video?"video/*":"audio/*");
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION|android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,video?REQ_TIMER_VIDEO:REQ_TIMER_SOUND);}catch(Throwable t){Toast.makeText(this,I18n.tr(this,video?"動画ファイルを開けません":"音声ファイルを開けません"),Toast.LENGTH_SHORT).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        boolean video=requestCode==REQ_TIMER_VIDEO;
        if((requestCode!=REQ_TIMER_SOUND&&!video)||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        long id=pendingTimerSoundId; pendingTimerSoundId=-1L; if(id<1000)return;
        android.net.Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
        String display=I18n.tr(this,video?"選択した動画":"選択した音");
        android.database.Cursor cur=null;
        try{cur=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null);if(cur!=null&&cur.moveToFirst()){int ix=cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(ix>=0){String x=cur.getString(ix);if(x!=null&&!x.trim().isEmpty())display=x.trim();}}}catch(Throwable ignored){}finally{if(cur!=null)try{cur.close();}catch(Throwable ignored){}}
        TimerStore.Entry e=TimerStore.find(this,id); if(e==null)return;
        if(video){if(!storeTimerVideo(e,uri,display))return;promptTimerVideoFullScreenPermissionIfNeeded();}else{e.soundUri=uri.toString();e.soundName=display;TimerStore.update(this,e);}
        renderTimers();Toast.makeText(this,I18n.tr(this,(video?"タイマー動画: ":"タイマー音: ")+display),Toast.LENGTH_SHORT).show();
    }

    private String timerStateText(TimerStore.Entry e) {
        String prefix=e.saved?"":I18n.tr(this,"今回だけ")+"  ·  ";
        if(e.running)return prefix+I18n.tr(this,"終了予定")+"  "+new java.text.SimpleDateFormat("HH:mm:ss",I18n.locale(this)).format(new java.util.Date(e.endMs));
        if(e.finished())return prefix+I18n.tr(this,"終了");
        if(e.remainingMs<e.durationMs)return prefix+I18n.tr(this,"一時停止中");
        return prefix+I18n.tr(this,durationLabel(e.durationMs));
    }

    private String durationLabel(long ms) {
        long sec=Math.max(1L,ms/1000L),h=sec/3600,m=(sec/60)%60,x=sec%60;
        if(h>0)return h+"時間 "+m+"分"; if(m>0&&x>0)return m+"分 "+x+"秒"; if(m>0)return m+"分"; return x+"秒";
    }

    private void showAddTimerDialog() {
        if(!"timer".equals(mode))return;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),0);
        EditText label=new EditText(this);label.setHint(I18n.tr(this,"名前（任意）"));label.setSingleLine(true);box.addView(label);
        TextView caption=text("時間",13,Ui.MUTED);caption.setPadding(0,Ui.dp(this,14),0,0);box.addView(caption);
        LinearLayout inputs=new LinearLayout(this);EditText h=numberBox("時"),m=numberBox("分"),sec=numberBox("秒");inputs.addView(h,new LinearLayout.LayoutParams(0,-2,1));inputs.addView(m,new LinearLayout.LayoutParams(0,-2,1));inputs.addView(sec,new LinearLayout.LayoutParams(0,-2,1));box.addView(inputs);
        Switch save=new Switch(this);save.setText(I18n.tr(this,"このタイマーを保存"));save.setTextColor(Ui.TEXT);save.setChecked(true);box.addView(save,Ui.gapTop(this,14));
        AlertDialog d=new AlertDialog.Builder(this).setTitle(I18n.tr(this,"タイマーを作成")).setView(box).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"開始"),null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->createTimerFromDialog(d,label,h,m,sec,save.isChecked())));d.show();
    }

    private void createTimerFromDialog(AlertDialog d,EditText label,EditText h,EditText m,EditText sec,boolean saved){
        long total=(parseLong(h)*3600L+parseLong(m)*60L+parseLong(sec))*1000L;
        if(total<=0){Toast.makeText(this,I18n.tr(this,"時間を設定してください"),Toast.LENGTH_SHORT).show();return;}
        TimerStore.Entry e=saved?TimerStore.addSaved(this,label.getText().toString().trim(),total):TimerStore.addTemporary(this,label.getText().toString().trim(),total);
        startTimerEntry(e); d.dismiss(); renderTimers();
    }

    private void startQuickTimer(long durationMs){TimerStore.Entry e=TimerStore.addTemporary(this,"",durationMs);startTimerEntry(e);renderTimers();}
    private void startTimerEntry(TimerStore.Entry e){
        if(e==null)return;
        if(isVideoMedia(e.soundUri))promptTimerVideoFullScreenPermissionIfNeeded();
        e.remainingMs=e.durationMs; e.endMs=System.currentTimeMillis()+e.durationMs; e.running=true;
        TimerStore.update(this,e);
        requestTimerNotificationPermissionIfNeeded();
        TimerReceiver.schedule(this,e.id,e.endMs);
    }

    private EditText numberBox(String hint) {
        EditText e=new EditText(this); e.setHint(I18n.tr(this,hint)); e.setHintTextColor(Ui.MUTED_2); e.setTextColor(Ui.TEXT);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); e.setGravity(Gravity.CENTER); e.setTextSize(18); e.setSingleLine(true);
        e.setPadding(Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10));
        try{e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.MUTED_2));}catch(Throwable ignored){} return e;
    }
    private long parseLong(EditText e){try{return Long.parseLong(e.getText().toString().trim());}catch(Exception x){return 0L;}}

    private void toggleTimer(long id) {
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;
        if(e.running){
            e.remainingMs=e.remaining(); e.running=false; e.endMs=0L;
            TimerStore.update(this,e); TimerReceiver.cancel(this,id);
        } else {
            long rem=e.remainingMs>0?e.remainingMs:e.durationMs; e.remainingMs=rem; e.endMs=System.currentTimeMillis()+rem; e.running=true;
            TimerStore.update(this,e);
            requestTimerNotificationPermissionIfNeeded();
            TimerReceiver.schedule(this,id,e.endMs);
        }
        renderTimers();
    }

    private void addMinute(long id) {
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;e.durationMs+=60000L;
        if(e.running){e.endMs+=60000L;e.remainingMs=e.remaining();TimerReceiver.schedule(this,id,e.endMs);}else if(e.finished())e.remainingMs=60000L;else e.remainingMs+=60000L;
        TimerStore.update(this,e);renderTimers();
    }

    private void resetTimer(long id) {TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;TimerReceiver.cancel(this,id);e.running=false;e.endMs=0L;e.remainingMs=e.durationMs;TimerStore.update(this,e);renderTimers();}
    private String formatTimer(long ms){long total=(ms+999)/1000L,h=total/3600,m=(total/60)%60,x=total%60;return String.format(Locale.US,"%02d:%02d:%02d",h,m,x);}

    private boolean canUseTimerVideoFullScreen(){
        if(Build.VERSION.SDK_INT<34)return true;try{android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);return nm==null||nm.canUseFullScreenIntent();}catch(Throwable ignored){return false;}
    }
    private void promptTimerVideoFullScreenPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT<34||canUseTimerVideoFullScreen())return;
        try{new AlertDialog.Builder(this).setTitle(I18n.tr(this,"動画タイマーの全画面表示"))
            .setMessage(I18n.tr(this,"動画をタイマー終了時に画面いっぱいに表示するには、全画面通知の許可をオンにしてください。"))
            .setNegativeButton(I18n.tr(this,"あとで"),null)
            .setPositiveButton(I18n.tr(this,"設定を開く"),(d,w)->{try{android.content.Intent x=new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,android.net.Uri.parse("package:"+getPackageName()));startActivity(x);}catch(Throwable ignored){}}).show();}catch(Throwable ignored){}
    }

    private boolean timerNotificationsAllowed(){
        if(Build.VERSION.SDK_INT<33)return true;
        try{return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED;}catch(Throwable ignored){return false;}
    }

    private void requestTimerNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT<33||timerNotificationsAllowed())return;
        try{requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},REQ_TIMER_NOTIFICATION);}catch(Throwable ignored){}
    }

    private void refreshRunningTimerNotifications(){
        if(!timerNotificationsAllowed())return;
        for(TimerStore.Entry e:TimerStore.all(this))if(e.running)TimerReceiver.showRunning(this,e);
    }

    private void showTimerNotificationSettingsDialog(){
        try{
            new AlertDialog.Builder(this)
                    .setTitle(I18n.tr(this,"時計ツールの通知を表示できません"))
                    .setMessage(I18n.tr(this,"タイマーとストップウォッチの実行中表示を通知に出すには、IGNIDO Wakeの通知を許可してください。"))
                    .setNegativeButton(I18n.tr(this,"閉じる"),null)
                    .setPositiveButton(I18n.tr(this,"通知設定を開く"),(d,w)->{
                        try{
                            android.content.Intent i=new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                            i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
                            startActivity(i);
                        }catch(Throwable ignored){}
                    }).show();
        }catch(Throwable ignored){}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode!=REQ_TIMER_NOTIFICATION)return;
        if(timerNotificationsAllowed()){refreshRunningTimerNotifications();syncStopwatchNotification();} else showTimerNotificationSettingsDialog();
    }

    private void updateTimers() {
        if(!"timer".equals(mode)||timerList==null)return;boolean rebuild=false;
        for(TimerStore.Entry e:TimerStore.all(this)){
            long rem=e.remaining();if(e.running&&rem<=0){TimerReceiver.complete(this,e.id);rebuild=true;continue;}
            TextView time=timerList.findViewWithTag("timer_remaining_"+e.id),state=timerList.findViewWithTag("timer_state_"+e.id);
            ClockFaceActivity.AnalogFace analog=timerList.findViewWithTag("timer_analog_"+e.id);
            if(time!=null)time.setText(formatTimer(rem));if(analog!=null){double sec=(rem/1000d)%60d,min=(rem/60000d)%60d,hr=(rem/3600000d)%12d;analog.setHands(hr,min,sec);}if(state!=null){state.setText(timerStateText(e));state.setTextColor(e.running?Ui.ACCENT:(e.finished()?Ui.SUCCESS:Ui.MUTED));}
        }
        if(rebuild)renderTimers();
    }

    private void updateLiveUi() {
        updateLocalClock();
        if("world".equals(mode)) updateWorldRows();
        else if("stopwatch".equals(mode)) updateStopwatch();
        else if("timer".equals(mode)) updateTimers();
    }
}
