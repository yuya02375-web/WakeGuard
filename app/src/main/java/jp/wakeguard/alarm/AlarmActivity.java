package jp.wakeguard.alarm;

import android.app.PendingIntent;
import android.app.ActivityOptions;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.hardware.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class AlarmActivity extends Activity {
    public static final String ACTION_SCHEDULED_FIRE = "jp.wakeguard.alarm.UI_SCHEDULED_FIRE";
    public static final String EXTRA_ALARM_ID = "alarmId";
    public static volatile boolean visible = false;
    private TextView count, prompt, feedback, missionName;
    private EditText answer;
    private Button stop, action;
    private LinearLayout missionCard;
    private BroadcastReceiver updates;
    private final Handler missionHandler = new Handler(Looper.getMainLooper());
    private SensorManager sensorManager; private Sensor accelerometer; private SensorEventListener shakeListener;
    private long lastShakeMs=0L, holdStartMs=0L; private float swipeStartX=0f; private int orderNext=1;
    private Runnable holdTicker;
    private String expectedPhrase="";
    private int expectedAnswer=0;
    private String expectedCode="";
    private androidx.media3.ui.PlayerView alarmVideoView; private androidx.media3.exoplayer.ExoPlayer alarmVideoPlayer; private boolean alarmVideoMode=false,alarmVideoPrepared=false,activityStarted=false;
    private long uiAlarmId=Long.MIN_VALUE; private String uiMissionType="";

    public static void launch(Context c) {
        if (!Prefs.active(c)) return;
        Intent i = new Intent(c, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        // Never launch twice. Previously, overlay permission caused a direct
        // startActivity followed by a PendingIntent send (and sometimes a third
        // startActivity). Repeated NEW_TASK/CLEAR_TOP can replace a live ring UI.
        if (visible) return;
        if (Settings.canDrawOverlays(c)) {
            try { c.startActivity(i); return; }
            catch (Throwable t) { try { Prefs.lastAlarmError(c, "DirectLockUi: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }
        }

        if (Build.VERSION.SDK_INT >= 34) {
            try {
                ActivityOptions creator = ActivityOptions.makeBasic();
                creator.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                PendingIntent pi = PendingIntent.getActivity(c, 4299, i,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE, creator.toBundle());
                ActivityOptions sender = ActivityOptions.makeBasic();
                sender.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pi.send(c, 0, null, null, null, null, sender.toBundle());
                return;
            } catch (Throwable t) { try { Prefs.lastAlarmError(c, "PendingLockUi: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }
        }

        try { c.startActivity(i); }
        catch (Throwable t) { try { Prefs.lastAlarmError(c, "StartLockUi: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }
    }

    private void handleLaunchIntent(Intent i) {
        if (i == null || !ACTION_SCHEDULED_FIRE.equals(i.getAction())) return;
        long epochDay = i.getLongExtra(AlarmService.EXTRA_EPOCH_DAY, -1L);
        long alarmId = i.getLongExtra(EXTRA_ALARM_ID, AlarmScheduler.PRIMARY_ALARM_ID);
        long expectedAt = i.getLongExtra(AlarmScheduler.EXTRA_EXPECTED_AT, -1L);
        // An AlarmClock/FSI/OEM may re-deliver the same Activity intent after
        // AlarmService has already marked the occurrence delivered. Never call
        // finish() on an existing, actively ringing Activity in that case.
        if (Prefs.active(this) && AlarmService.running) {
            boolean same = Prefs.activeAlarmId(this) == alarmId
                    && Prefs.sessionEpochDay(this) == epochDay
                    && (expectedAt <= 0L || Prefs.sessionExpectedAt(this) == expectedAt);
            android.util.Log.i("IGNIDO_ALARM", "Reentered scheduled UI: same=" + same);
            i.setAction(null);
            return;
        }
        if (!AlarmScheduler.isExpectedFire(this, alarmId, epochDay, expectedAt)
                || AlarmOccurrenceGuard.wasDelivered(this, alarmId, expectedAt, epochDay)) {
            try { Prefs.lastAlarmError(this, "Blocked stale alarm delivery id=" + alarmId); } catch (Throwable ignored) {}
            i.setAction(null); finish(); return;
        }
        try {
            AlarmStore.markFiredIfOneShot(this, alarmId);
            if (alarmId == AlarmScheduler.PRIMARY_ALARM_ID && Prefs.dayMask(this) == 0) Prefs.enabled(this, false);
        } catch (Throwable ignored) {}
        boolean alreadyRunning = Prefs.active(this) && AlarmService.running;
        Prefs.active(this, true);
        String chosenMission = Prefs.sessionMissionType(this);
        if (!alreadyRunning) {
            Prefs.activeAlarmId(this, alarmId);
            chosenMission = AlarmProfiles.resolveSessionMission(this, alarmId);
            Prefs.sessionMissionType(this, chosenMission);
            Prefs.missionProgress(this, 0);
            Prefs.missionComplete(this, false);
        }
        try { AlarmScheduler.reschedule(this); } catch (Throwable ignored) {}
        Intent s = new Intent(this, AlarmService.class)
                .setAction(AlarmService.ACTION_FIRE_NEW)
                .putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay)
                .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmService.EXTRA_EXPECTED_AT, expectedAt)
                .putExtra("missionType", chosenMission);
        try {
            // Always forward the fire. If another alarm is already active, AlarmService queues
            // this alarm instead of replacing the current session or losing the new one.
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        } catch (Throwable t) { try { Prefs.lastAlarmError(this, "ScheduledStart: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }
        i.setAction(null);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        handleLaunchIntent(getIntent());
        if (isFinishing()) return;
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        try { setShowWhenLocked(true); } catch (Throwable ignored) {}
        try { setTurnScreenOn(true); } catch (Throwable ignored) {}
        try { getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN); } catch (Throwable ignored) {}
        buildUi();
        updates = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) { refreshUiForActiveSession(); } };
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE), Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE));
        } catch (Throwable t) { updates = null; }
        render();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleLaunchIntent(intent); try { refreshUiForActiveSession(); } catch (Throwable ignored) {} }

    private void refreshUiForActiveSession(){
        if(!Prefs.active(this)){render();return;}
        long id=Prefs.activeAlarmId(this);String type=sessionType();
        if(id==uiAlarmId && type.equals(uiMissionType)){render();return;}
        missionHandler.removeCallbacksAndMessages(null);
        if(sensorManager!=null&&shakeListener!=null)try{sensorManager.unregisterListener(shakeListener);}catch(Throwable ignored){}
        sensorManager=null;accelerometer=null;shakeListener=null;lastShakeMs=0L;holdStartMs=0L;
        releaseAlarmVideo();
        buildUi();
        render();
    }

    private void buildUi() {
        uiAlarmId=Prefs.activeAlarmId(this);uiMissionType=sessionType();
        Ui.statusBar(this);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Ui.BG);alarmVideoMode=isVideoAlarm();
        if(alarmVideoMode){alarmVideoView=(androidx.media3.ui.PlayerView)getLayoutInflater().inflate(R.layout.view_alarm_video_texture,frame,false);alarmVideoView.setBackgroundColor(Color.TRANSPARENT);alarmVideoView.setShutterBackgroundColor(Color.TRANSPARENT);alarmVideoView.setUseController(false);alarmVideoView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);alarmVideoView.setKeepScreenOn(true);frame.addView(alarmVideoView,new FrameLayout.LayoutParams(-1,-1));}
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,22), Ui.dp(this,20), Ui.dp(this,22), Ui.dp(this,18));
        root.setBackgroundColor(alarmVideoMode?Color.TRANSPARENT:Ui.BG);

        if(!alarmVideoMode){
            LinearLayout masthead=Ui.row(this);masthead.setGravity(Gravity.CENTER_VERTICAL);
            TextView brand=Ui.overline(this,"WakeGuard");brand.setTextColor(Ui.ACCENT);masthead.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
            TextView status=Ui.accentPill(this,"アラームが鳴っています");
            status.setContentDescription(I18n.tr(this,"アラームが鳴っています"));
            masthead.addView(status);root.addView(masthead);
        }

        LinearLayout timeCard=new LinearLayout(this);timeCard.setOrientation(LinearLayout.VERTICAL);timeCard.setGravity(Gravity.CENTER);
        timeCard.setPadding(Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,18),Ui.dp(this,16));
        if(!alarmVideoMode)timeCard.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,24,this));
        else timeCard.setBackground(Ui.roundStroke(0xB30D1118,0x77FFFFFF,24,this));
        TextView now = Ui.title(this, java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), alarmVideoMode?52:64);
        if(alarmVideoMode){now.setTextColor(Color.WHITE);now.setShadowLayer(10f,0,2,Color.BLACK);}
        else now.setTextColor(Ui.TEXT);
        now.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));
        now.setGravity(Gravity.CENTER);now.setIncludeFontPadding(false);
        if(android.os.Build.VERSION.SDK_INT>=21)now.setLetterSpacing(-0.025f);
        timeCard.addView(now,new LinearLayout.LayoutParams(-1,-2));
        TextView title = Ui.text(this, AlarmProfiles.label(this, Prefs.activeAlarmId(this)), 15, alarmVideoMode?Color.WHITE:Ui.MUTED);
        if(alarmVideoMode)title.setShadowLayer(8f,0,2,Color.BLACK);
        title.setGravity(Gravity.CENTER);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        timeCard.addView(title,Ui.gapTop(this,4));
        root.addView(timeCard,Ui.gapTop(this,alarmVideoMode?18:16));

        String type=sessionType();
        boolean directVideoStop=alarmVideoMode&&"NONE".equals(type);
        missionCard = new LinearLayout(this); missionCard.setOrientation(LinearLayout.VERTICAL); missionCard.setGravity(Gravity.CENTER_HORIZONTAL);
        if(directVideoStop){
            Prefs.missionComplete(this,true);count=null;prompt=null;feedback=null;missionName=null;
            root.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));
        }else{
            LinearLayout missionIntro=new LinearLayout(this);missionIntro.setOrientation(LinearLayout.VERTICAL);missionIntro.setGravity(Gravity.CENTER);
            missionName=Ui.title(this,AlarmProfiles.missionName(type),22);
            if(alarmVideoMode){missionName.setTextColor(Color.WHITE);missionName.setShadowLayer(8f,0,2,Color.BLACK);}
            missionName.setGravity(Gravity.CENTER);missionName.setMaxLines(2);missionName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            missionIntro.addView(missionName,new LinearLayout.LayoutParams(-1,-2));
            TextView instruction=Ui.text(this,"NONE".equals(type)?"停止ボタンを押すとアラームを停止します":"このミッションを完了するとアラームを解除できます",13,alarmVideoMode?Color.WHITE:Ui.MUTED);
            if(alarmVideoMode)instruction.setShadowLayer(7f,0,2,Color.BLACK);
            instruction.setGravity(Gravity.CENTER);missionIntro.addView(instruction,Ui.gapTop(this,5));
            root.addView(missionIntro,Ui.gapTop(this,18));
            missionCard.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,16));
            if(!alarmVideoMode)missionCard.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,22,this));
            else missionCard.setBackground(Ui.roundStroke(0xB30D1118,0x77FFFFFF,22,this));
            LinearLayout.LayoutParams missionParams=new LinearLayout.LayoutParams(-1,0,1);
            missionParams.setMargins(0,Ui.dp(this,14),0,0);
            root.addView(missionCard,missionParams);buildMission(type);
        }

        int snoozeMinutes=AlarmProfiles.snoozeMinutes(this,Prefs.activeAlarmId(this));Button snooze=Ui.button(this,"スヌーズ "+snoozeMinutes+"分",false);snooze.setContentDescription(I18n.tr(this,"スヌーズ "+snoozeMinutes+"分"));snooze.setOnClickListener(v->{try{startService(new Intent(this,AlarmService.class).setAction(AlarmService.ACTION_SNOOZE));}catch(Throwable ignored){}try{finishAndRemoveTask();}catch(Throwable ignored){finish();}});if(Prefs.sessionIsTest(this)||snoozeMinutes<=0)snooze.setVisibility(View.GONE);
        stop = Ui.button(this, "NONE".equals(type)?"アラームを停止":"ミッションを完了してください", true); stop.setContentDescription(I18n.tr(this,"NONE".equals(type)?"アラームを停止":"ミッションを完了してください"));stop.setEnabled("NONE".equals(type));stop.setVisibility(View.VISIBLE);stop.setMinHeight(Ui.dp(this,"NONE".equals(type)?68:64));stop.setTextSize("NONE".equals(type)?17:14);
        stop.setOnClickListener(v -> { String current=sessionType();if("NONE".equals(current))Prefs.missionComplete(this,true);if("NONE".equals(current)||missionDone()) { try { startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP)); } catch (Throwable ignored) {} try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); } } });
        if(directVideoStop){
            stop.setText(I18n.tr(this,"停止"));
            stop.setMinHeight(Ui.dp(this,48));
            stop.setTextSize(16);
        }else{
            LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams stopParams=new LinearLayout.LayoutParams(0,Ui.dp(this,"NONE".equals(type)?66:64),snooze.getVisibility()==View.VISIBLE?1.45f:1f);
            controls.addView(stop,stopParams);
            if(snooze.getVisibility()==View.VISIBLE){LinearLayout.LayoutParams snoozeParams=new LinearLayout.LayoutParams(0,Ui.dp(this,60),1f);snoozeParams.setMargins(Ui.dp(this,10),0,0,0);controls.addView(snooze,snoozeParams);}
            root.addView(controls,Ui.gapTop(this,14));stop.bringToFront();
        }
        if(!alarmVideoMode){
            TextView footer = Ui.text(this, "戻る・ホームでは停止しません", 12, Ui.MUTED);footer.setGravity(Gravity.CENTER);root.addView(footer, Ui.gapTop(this,10));
        }
        frame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        if(directVideoStop){
            FrameLayout.LayoutParams stopLp=new FrameLayout.LayoutParams(Ui.dp(this,86),Ui.dp(this,48),Gravity.TOP|Gravity.END);
            stopLp.setMargins(0,Ui.dp(this,28),Ui.dp(this,16),0);
            frame.addView(stop,stopLp);stop.bringToFront();
            if(!Prefs.sessionIsTest(this)){FrameLayout.LayoutParams snoozeLp=new FrameLayout.LayoutParams(Ui.dp(this,140),Ui.dp(this,48),Gravity.TOP|Gravity.START);snoozeLp.setMargins(Ui.dp(this,16),Ui.dp(this,28),0,0);frame.addView(snooze,snoozeLp);snooze.bringToFront();}
        }
        setContentView(frame);Ui.applySystemBarInsets(this,frame);if(alarmVideoMode)startAlarmVideo();
    }
    private String alarmMediaRaw(){String x=AlarmProfiles.soundUri(this,Prefs.activeAlarmId(this));return x==null?"":x;}
    private boolean isVideoAlarm(){String raw=alarmMediaRaw();String name=AlarmProfiles.soundName(this,Prefs.activeAlarmId(this));if(raw.trim().isEmpty())return false;try{if(raw.startsWith("content:")){String t=getContentResolver().getType(android.net.Uri.parse(raw));if(t!=null&&t.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}}catch(Throwable ignored){}String x=raw.toLowerCase(java.util.Locale.ROOT);if(x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts"))return true;String n=name==null?"":name.toLowerCase(java.util.Locale.ROOT);return n.endsWith(".mp4")||n.endsWith(".m4v")||n.endsWith(".3gp")||n.endsWith(".3gpp")||n.endsWith(".webm")||n.endsWith(".mkv")||n.endsWith(".ts");}
    private void startAlarmVideo(){
        if(alarmVideoView==null)return;String raw=alarmMediaRaw();
        try{
            releaseAlarmVideo();
            alarmVideoPlayer=new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
            androidx.media3.common.AudioAttributes aa=new androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_ALARM).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build();
            alarmVideoPlayer.setAudioAttributes(aa,true);
            alarmVideoPlayer.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);
            alarmVideoPlayer.setPlayWhenReady(false);
            alarmVideoView.setPlayer(alarmVideoPlayer);
            alarmVideoPlayer.addListener(new androidx.media3.common.Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){if(state==androidx.media3.common.Player.STATE_READY){alarmVideoPrepared=true;syncVideoState();}}
                @Override public void onPlayerError(androidx.media3.common.PlaybackException error){alarmVideoPrepared=false;notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);Toast.makeText(AlarmActivity.this,I18n.tr(AlarmActivity.this,"動画を再生できません"),Toast.LENGTH_LONG).show();}
            });
            android.net.Uri u=raw.startsWith("/")?android.net.Uri.fromFile(new java.io.File(raw)):android.net.Uri.parse(raw);
            alarmVideoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(u));
            alarmVideoPlayer.prepare();
        }catch(Throwable t){alarmVideoPrepared=false;notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);Toast.makeText(this,I18n.tr(this,"動画を再生できません"),Toast.LENGTH_LONG).show();}
    }
    private void releaseAlarmVideo(){alarmVideoPrepared=false;if(alarmVideoView!=null)try{alarmVideoView.setPlayer(null);}catch(Throwable ignored){}if(alarmVideoPlayer!=null){try{alarmVideoPlayer.release();}catch(Throwable ignored){}alarmVideoPlayer=null;}}
    private void notifyVideoService(String a){try{startService(new Intent(this,AlarmService.class).setAction(a));}catch(Throwable ignored){}}
    private void syncVideoState(){if(!alarmVideoMode||alarmVideoPlayer==null||!alarmVideoPrepared)return;if(!Prefs.active(this)||Prefs.sessionSilenced(this)){try{alarmVideoPlayer.pause();}catch(Throwable ignored){}return;}if(activityStarted){try{alarmVideoPlayer.setPlayWhenReady(true);alarmVideoPlayer.play();}catch(Throwable ignored){}notifyVideoService(AlarmService.ACTION_VIDEO_READY);}}

    private String sessionType(){ String t=Prefs.sessionMissionType(this); if(t==null||t.isEmpty())t=AlarmProfiles.resolveSessionMission(this,Prefs.activeAlarmId(this)); if("RANDOM".equals(t))t="STEPS"; return AlarmStore.normalizeMission(t); }
    private int targetCount(){ String t=sessionType(); long id=Prefs.activeAlarmId(this); if("STEPS".equals(t)) return AlarmProfiles.steps(this,id); int n=AlarmProfiles.missionCount(this,id); if("HOLD".equals(t)) return Math.max(2,Math.min(30,n)); if("TAP".equals(t) && "RANDOM".equals(AlarmProfiles.missionType(this,id))) return Math.max(20,n*10); return n; }

    private void buildMission(String type){
        missionCard.removeAllViews(); count=Ui.title(this,"",38);count.setGravity(Gravity.CENTER);count.setTextColor(alarmVideoMode?Color.WHITE:Ui.ACCENT_2);missionCard.addView(count);
        prompt=Ui.text(this,"",19,alarmVideoMode?Color.WHITE:Ui.TEXT);prompt.setGravity(Gravity.CENTER);prompt.setPadding(0,Ui.dp(this,18),0,0);missionCard.addView(prompt);
        feedback=Ui.text(this,"",13,alarmVideoMode?Color.WHITE:Ui.MUTED);feedback.setGravity(Gravity.CENTER);feedback.setPadding(0,Ui.dp(this,10),0,0);if(alarmVideoMode){count.setShadowLayer(8f,0,2,Color.BLACK);prompt.setShadowLayer(8f,0,2,Color.BLACK);feedback.setShadowLayer(7f,0,2,Color.BLACK);}missionCard.addView(feedback); action=null;answer=null;
        if("NONE".equals(type)){Prefs.missionComplete(this,true);prompt.setText(I18n.tr(this,"追加の解除操作はありません"));feedback.setText(I18n.tr(this,"下の停止ボタンを押してください"));}
        else if("STEPS".equals(type)){ prompt.setText(I18n.tr(this,"スマホを持って歩いてください")); }
        else if("TAP".equals(type)){ action=Ui.button(this,"タップ",false);action.setTextSize(22);action.setOnClickListener(v->{if(missionDone())return;int p=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,p);if(p>=targetCount())completeMission();render();});missionCard.addView(action,Ui.gapTop(this,22));prompt.setText(I18n.tr(this,"ボタンを繰り返しタップ")); }
        else if("SHAKE".equals(type)){ prompt.setText(I18n.tr(this,"スマホを大きく振ってください")); startShakeMission(); }
        else if("HOLD".equals(type)){ prompt.setText(I18n.tr(this,"ボタンを離さず押し続けてください")); action=Ui.button(this,"長押し",false);action.setTextSize(20);action.setOnTouchListener((v,e)->{if(missionDone())return true;if(e.getAction()==MotionEvent.ACTION_DOWN){holdStartMs=SystemClock.elapsedRealtime();startHoldTicker();feedback.setText(I18n.tr(this,"そのまま押し続けてください"));return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){holdStartMs=0;if(holdTicker!=null)missionHandler.removeCallbacks(holdTicker);feedback.setText(I18n.tr(this,"離すと最初からです"));render();return true;}return true;});missionCard.addView(action,Ui.gapTop(this,22)); }
        else if("SWIPE".equals(type)){prompt.setText(I18n.tr(this,"下のボタンを左右に大きくスワイプ"));action=Ui.button(this,"↔  スワイプ  ↔",false);action.setTextSize(20);action.setOnTouchListener((v,e)->{if(missionDone())return true;if(e.getAction()==MotionEvent.ACTION_DOWN){swipeStartX=e.getX();return true;}if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getX()-swipeStartX;if(Math.abs(dx)>=Ui.dp(this,100)){int q=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,q);feedback.setText(I18n.tr(this,"スワイプを認識しました"));if(q>=targetCount())completeMission();render();}else feedback.setText(I18n.tr(this,"もっと大きくスワイプしてください"));return true;}return true;});missionCard.addView(action,new LinearLayout.LayoutParams(-1,Ui.dp(this,80)));}
        else if("ORDER".equals(type)){prompt.setText(I18n.tr(this,"1 → 6 の順番でタップ"));buildOrderRound();}
        else {
            answer=new EditText(this);answer.setTextColor(Ui.TEXT);answer.setHintTextColor(Ui.MUTED_2);answer.setGravity(Gravity.CENTER);answer.setTextSize(22);answer.setSingleLine(true);answer.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));answer.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,10,this));
            if("MATH".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);action=Ui.button(this,"回答",false);action.setOnClickListener(v->submitMath());newMath();}
            else if("CODE".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);action=Ui.button(this,"確認",false);action.setOnClickListener(v->submitCode());newCode();}
            else if("MEMORY".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);action=Ui.button(this,"回答",false);action.setOnClickListener(v->submitMemory());newMemory();}
            else if("REVERSE".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);action=Ui.button(this,"確認",false);action.setOnClickListener(v->submitReverse());newReverse();}
            else {answer.setInputType(android.text.InputType.TYPE_CLASS_TEXT);action=Ui.button(this,"確認",false);action.setOnClickListener(v->submitType());newPhrase();}
            missionCard.addView(answer,Ui.gapTop(this,22));missionCard.addView(action,Ui.gapTop(this,10));
        }
        render();
    }

    private void newMath(){ java.util.Random r=new java.util.Random();int op=r.nextInt(3),a,b; if(op==2){a=2+r.nextInt(11);b=2+r.nextInt(11);expectedAnswer=a*b;prompt.setText(a+" × "+b+" = ?");} else if(op==1){a=10+r.nextInt(70);b=r.nextInt(a+1);expectedAnswer=a-b;prompt.setText(a+" − "+b+" = ?");} else {a=5+r.nextInt(70);b=5+r.nextInt(70);expectedAnswer=a+b;prompt.setText(a+" + "+b+" = ?");} if(answer!=null)answer.setText(""); }
    private void submitMath(){ if(answer==null||missionDone())return;try{int v=Integer.parseInt(answer.getText().toString().trim());if(v!=expectedAnswer){feedback.setText(I18n.tr(this,"違います。もう一度。"));answer.setText("");return;}advanceRound();if(!missionDone())newMath();render();}catch(Throwable ignored){feedback.setText(I18n.tr(this,"答えを入力してください"));} }
    private void newCode(){ expectedCode=String.format(java.util.Locale.US,"%06d",new java.util.Random().nextInt(1000000));prompt.setText(expectedCode);if(answer!=null){answer.setText("");answer.setHint(I18n.tr(this,"この6桁を入力"));} }
    private void submitCode(){ if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedCode.equals(v)){feedback.setText(I18n.tr(this,"コードが違います"));answer.setText("");return;}advanceRound();if(!missionDone())newCode();render(); }

    private void newMemory(){
        expectedCode=String.format(java.util.Locale.US,"%04d",new java.util.Random().nextInt(10000));
        prompt.setText(I18n.tr(this,"覚えてください\n"+expectedCode)); answer.setText("");answer.setEnabled(false);action.setEnabled(false);feedback.setText(I18n.tr(this,"2秒後に消えます"));
        missionHandler.postDelayed(()->{if(isFinishing()||missionDone())return;prompt.setText(I18n.tr(this,"覚えた4桁を入力"));answer.setEnabled(true);action.setEnabled(true);answer.requestFocus();feedback.setText("");},2000);
    }
    private void submitMemory(){if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedCode.equals(v)){feedback.setText(I18n.tr(this,"違います。もう一度表示します"));missionHandler.postDelayed(this::newMemory,600);return;}advanceRound();if(!missionDone())newMemory();render();}

    private void newPhrase(){String[] p={"二度寝しない","朝の準備を始める","起きて一日を始める","目を覚まして動く","今日も起きる"};expectedPhrase=I18n.tr(this,p[new java.util.Random().nextInt(p.length)]);prompt.setText(expectedPhrase);answer.setText("");answer.setHint(I18n.tr(this,"同じ文章を入力"));}
    private void submitType(){if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedPhrase.equals(v)){feedback.setText(I18n.tr(this,"文章が一致しません"));answer.setText("");return;}advanceRound();if(!missionDone())newPhrase();render();}

    private void newReverse(){String shown=String.format(java.util.Locale.US,"%05d",new java.util.Random().nextInt(100000));expectedCode=new StringBuilder(shown).reverse().toString();prompt.setText(I18n.tr(this,"逆の順番で入力")+"\n"+shown);if(answer!=null){answer.setText("");answer.setHint(I18n.tr(this,"逆順の5桁を入力"));}}
    private void submitReverse(){if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedCode.equals(v)){feedback.setText(I18n.tr(this,"逆順が違います"));answer.setText("");return;}advanceRound();if(!missionDone())newReverse();render();}

    private void buildOrderRound(){
        final LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,Ui.dp(this,12),0,0);orderNext=1;java.util.ArrayList<Integer> nums=new java.util.ArrayList<>();for(int i=1;i<=6;i++)nums.add(i);java.util.Collections.shuffle(nums);for(int r=0;r<2;r++){LinearLayout row=Ui.row(this);for(int c=0;c<3;c++){final int n=nums.get(r*3+c);Button b=Ui.button(this,String.valueOf(n),false);b.setTextSize(22);b.setOnClickListener(v->tapOrder(n,box));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,60),1);if(c>0)lp.setMargins(Ui.dp(this,6),0,0,0);row.addView(b,lp);}box.addView(row,Ui.gapTop(this,r==0?0:6));}missionCard.addView(box,Ui.gapTop(this,10));feedback.setText(I18n.tr(this,"まず1をタップ"));}
    private void tapOrder(int n,LinearLayout box){if(missionDone())return;if(n!=orderNext){feedback.setText(I18n.tr(this,"順番が違います。1からやり直し"));missionCard.removeView(box);buildOrderRound();return;}if(orderNext<6){orderNext++;feedback.setText(I18n.tr(this,"次は")+" "+orderNext);return;}advanceRound();missionCard.removeView(box);if(!missionDone())buildOrderRound();render();}

    private void startShakeMission(){
        try{sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);accelerometer=sensorManager==null?null:sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);if(accelerometer==null){feedback.setText(I18n.tr(this,"加速度センサーを利用できません"));return;}shakeListener=new SensorEventListener(){public void onAccuracyChanged(Sensor s,int a){}public void onSensorChanged(SensorEvent e){if(missionDone())return;float x=e.values[0],y=e.values[1],z=e.values[2];double g=Math.sqrt(x*x+y*y+z*z)/SensorManager.GRAVITY_EARTH;long now=SystemClock.elapsedRealtime();if(g>2.2&&now-lastShakeMs>450){lastShakeMs=now;int p=Prefs.missionProgress(AlarmActivity.this)+1;Prefs.missionProgress(AlarmActivity.this,p);if(p>=targetCount())completeMission();render();}}};sensorManager.registerListener(shakeListener,accelerometer,SensorManager.SENSOR_DELAY_GAME);}catch(Throwable t){feedback.setText(I18n.tr(this,"シェイクを検出できません"));}
    }

    private void startHoldTicker(){
        if(holdTicker==null)holdTicker=new Runnable(){public void run(){if(holdStartMs<=0||missionDone())return;long elapsed=SystemClock.elapsedRealtime()-holdStartMs;if(elapsed>=targetCount()*1000L){completeMission();render();return;}render();missionHandler.postDelayed(this,100);}};
        missionHandler.removeCallbacks(holdTicker);missionHandler.post(holdTicker);
    }

    private void advanceRound(){int p=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,p);feedback.setText("OK");if(p>=targetCount())completeMission();}
    private void completeMission(){Prefs.missionComplete(this,true);feedback.setText(I18n.tr(this,"完了しました"));if(action!=null)action.setEnabled(false);if(answer!=null)answer.setEnabled(false);if(sensorManager!=null&&shakeListener!=null)try{sensorManager.unregisterListener(shakeListener);}catch(Throwable ignored){} }
    private boolean missionDone(){ String t=sessionType();if("NONE".equals(t)){if(!Prefs.missionComplete(this))Prefs.missionComplete(this,true);return true;} if("STEPS".equals(t))return Prefs.stepSensorAvailable(this)&&Prefs.currentSteps(this)>=AlarmProfiles.steps(this,Prefs.activeAlarmId(this)); return Prefs.missionComplete(this); }

    private void render() {
        if(!Prefs.active(this)){try{finishAndRemoveTask();}catch(Throwable ignored){finish();}return;}
        if (stop == null) return; String type=sessionType();int target=targetCount();boolean done=missionDone();
        if("NONE".equals(type)){Prefs.missionComplete(this,true);stop.setVisibility(View.VISIBLE);stop.setEnabled(true);stop.setAlpha(1f);stop.setText(I18n.tr(this,"アラームを停止"));stop.setContentDescription(I18n.tr(this,"アラームを停止"));stop.bringToFront();if(count!=null)count.setText(I18n.tr(this,"解除できます"));if(feedback!=null)feedback.setText(I18n.tr(this,"下の停止ボタンを押してください"));syncVideoState();return;}
        if(count==null)return;
        if("STEPS".equals(type)){ int s=Prefs.currentSteps(this);boolean available=Prefs.stepSensorAvailable(this);count.setText(available?I18n.tr(this,s+" / "+target+" 歩"):I18n.tr(this,"歩数センサーを利用できません"));if(available&&s>=target){Prefs.missionComplete(this,true);done=true;}if(feedback!=null&&!done)feedback.setText(available?I18n.tr(this,"あと "+Math.max(0,target-s)+" 歩"):I18n.tr(this,"身体活動権限を確認してください")); }
        else if("HOLD".equals(type)){long e=holdStartMs<=0?0:SystemClock.elapsedRealtime()-holdStartMs;float sec=Math.min(target,e/1000f);count.setText(I18n.tr(this,String.format(java.util.Locale.US,"%.1f / %d 秒",sec,target)));}
        else { int p=Prefs.missionProgress(this);count.setText(p+" / "+target);if("TAP".equals(type)&&action!=null)action.setText(I18n.tr(this,done?"完了":"タップ  あと "+Math.max(0,target-p))); }
        stop.setEnabled(done);stop.setText(I18n.tr(this,done?"アラームを停止":"ミッションを完了してください"));stop.setContentDescription(I18n.tr(this,done?"アラームを停止":"ミッションを完了してください"));syncVideoState();
    }

    @Override protected void onStart() { super.onStart(); activityStarted=true;visible=true;if(Prefs.active(this))syncVideoState(); }
    @Override protected void onResume() { super.onResume(); if(!Prefs.active(this)){android.util.Log.w("IGNIDO_ALARM", "Alarm screen finished because alarm_active=false");try{finishAndRemoveTask();}catch(Throwable ignored){finish();}return;} visible = true; android.util.Log.i("IGNIDO_ALARM", "Ring screen resumed alarmId="+Prefs.activeAlarmId(this));render();syncVideoState(); }
    @Override protected void onPause() { /* Keep alarm video alive across OEM/keyguard pause transitions. */ super.onPause(); }
    @Override protected void onStop() { activityStarted=false;visible=false;if(alarmVideoMode&&alarmVideoPlayer!=null){try{alarmVideoPlayer.setPlayWhenReady(false);alarmVideoPlayer.pause();}catch(Throwable ignored){}if(Prefs.active(this)&&!Prefs.sessionSilenced(this))notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);}super.onStop(); }
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus&&activityStarted)syncVideoState();}
    @Override protected void onDestroy() { activityStarted=false;visible = false; releaseAlarmVideo(); missionHandler.removeCallbacksAndMessages(null); if(sensorManager!=null&&shakeListener!=null)try{sensorManager.unregisterListener(shakeListener);}catch(Throwable ignored){} if (updates != null) { try { unregisterReceiver(updates); } catch (Throwable ignored) {} } super.onDestroy(); }
    @Override public void onBackPressed() { if (!Prefs.active(this)) super.onBackPressed(); }
}
