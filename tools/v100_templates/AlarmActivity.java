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
    private long lastShakeMs=0L, holdStartMs=0L;
    private Runnable holdTicker;
    private String expectedPhrase="";
    private int expectedAnswer=0;
    private String expectedCode="";

    public static void launch(Context c) {
        if (!Prefs.active(c)) return;
        Intent i = new Intent(c, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        if (Settings.canDrawOverlays(c)) {
            try { c.startActivity(i); }
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
                .putExtra("missionType", chosenMission);
        try {
            if (!alreadyRunning) { if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s); }
        } catch (Throwable t) { try { Prefs.lastAlarmError(this, "ScheduledStart: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }
        i.setAction(null);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        handleLaunchIntent(getIntent());
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        try { setShowWhenLocked(true); } catch (Throwable ignored) {}
        try { setTurnScreenOn(true); } catch (Throwable ignored) {}
        try { getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN); } catch (Throwable ignored) {}
        buildUi();
        updates = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) { render(); } };
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE), Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(updates, new IntentFilter(AlarmService.ACTION_UPDATE));
        } catch (Throwable t) { updates = null; }
        render();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleLaunchIntent(intent); try { render(); } catch (Throwable ignored) {} }

    private void buildUi() {
        Ui.statusBar(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,24), Ui.dp(this,36), Ui.dp(this,24), Ui.dp(this,30));
        root.setBackgroundColor(Ui.BG);

        TextView now = Ui.title(this, java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), 60);
        now.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL); now.setGravity(Gravity.CENTER); root.addView(now);
        TextView title = Ui.text(this, AlarmProfiles.label(this, Prefs.activeAlarmId(this)), 16, Ui.MUTED); title.setGravity(Gravity.CENTER); root.addView(title,Ui.gapTop(this,4));
        root.addView(Ui.divider(this),Ui.gapTop(this,24));

        String type=sessionType();
        missionName=Ui.title(this,AlarmProfiles.missionName(type),24);missionName.setGravity(Gravity.CENTER);root.addView(missionName,Ui.gapTop(this,28));
        TextView instruction=Ui.text(this,"このミッションを完了するとアラームを解除できます",13,Ui.MUTED);instruction.setGravity(Gravity.CENTER);root.addView(instruction,Ui.gapTop(this,6));

        missionCard = new LinearLayout(this); missionCard.setOrientation(LinearLayout.VERTICAL); missionCard.setGravity(Gravity.CENTER_HORIZONTAL);
        missionCard.setPadding(0,Ui.dp(this,28),0,Ui.dp(this,16)); root.addView(missionCard,new LinearLayout.LayoutParams(-1,0,1)); buildMission(type);

        stop = Ui.button(this, "ミッションを完了してください", true); stop.setEnabled(false);
        stop.setOnClickListener(v -> { if (missionDone()) { try { startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP)); } catch (Throwable ignored) {} try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); } } });
        root.addView(stop);
        TextView footer = Ui.text(this, "戻る・ホームでは停止しません", 12, Ui.MUTED); footer.setGravity(Gravity.CENTER); root.addView(footer, Ui.gapTop(this,12));
        setContentView(root);
    }

    private String sessionType(){ String t=Prefs.sessionMissionType(this); if(t==null||t.isEmpty())t=AlarmProfiles.resolveSessionMission(this,Prefs.activeAlarmId(this)); if("RANDOM".equals(t))t="STEPS"; return AlarmStore.normalizeMission(t); }
    private int targetCount(){ String t=sessionType(); long id=Prefs.activeAlarmId(this); if("STEPS".equals(t)) return AlarmProfiles.steps(this,id); int n=AlarmProfiles.missionCount(this,id); if("HOLD".equals(t)) return Math.max(2,Math.min(30,n)); if("TAP".equals(t) && "RANDOM".equals(AlarmProfiles.missionType(this,id))) return Math.max(20,n*10); return n; }

    private void buildMission(String type){
        missionCard.removeAllViews(); count=Ui.title(this,"",38);count.setGravity(Gravity.CENTER);missionCard.addView(count);
        prompt=Ui.text(this,"",19,Ui.TEXT);prompt.setGravity(Gravity.CENTER);prompt.setPadding(0,Ui.dp(this,18),0,0);missionCard.addView(prompt);
        feedback=Ui.text(this,"",13,Ui.MUTED);feedback.setGravity(Gravity.CENTER);feedback.setPadding(0,Ui.dp(this,10),0,0);missionCard.addView(feedback); action=null;answer=null;
        if("STEPS".equals(type)){ prompt.setText("スマホを持って歩いてください"); }
        else if("TAP".equals(type)){ action=Ui.button(this,"タップ",false);action.setTextSize(22);action.setOnClickListener(v->{if(missionDone())return;int p=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,p);if(p>=targetCount())completeMission();render();});missionCard.addView(action,Ui.gapTop(this,22));prompt.setText("ボタンを繰り返しタップ"); }
        else if("SHAKE".equals(type)){ prompt.setText("スマホを大きく振ってください"); startShakeMission(); }
        else if("HOLD".equals(type)){ prompt.setText("ボタンを離さず押し続けてください"); action=Ui.button(this,"長押し",false);action.setTextSize(20);action.setOnTouchListener((v,e)->{if(missionDone())return true;if(e.getAction()==MotionEvent.ACTION_DOWN){holdStartMs=SystemClock.elapsedRealtime();startHoldTicker();feedback.setText("そのまま押し続けてください");return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){holdStartMs=0;if(holdTicker!=null)missionHandler.removeCallbacks(holdTicker);feedback.setText("離すと最初からです");render();return true;}return true;});missionCard.addView(action,Ui.gapTop(this,22)); }
        else {
            answer=new EditText(this);answer.setTextColor(Ui.TEXT);answer.setHintTextColor(Ui.MUTED_2);answer.setGravity(Gravity.CENTER);answer.setTextSize(22);answer.setSingleLine(true);answer.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));answer.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,10,this));
            if("MATH".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);action=Ui.button(this,"回答",false);action.setOnClickListener(v->submitMath());newMath();}
            else if("CODE".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);action=Ui.button(this,"確認",false);action.setOnClickListener(v->submitCode());newCode();}
            else if("MEMORY".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);action=Ui.button(this,"回答",false);action.setOnClickListener(v->submitMemory());newMemory();}
            else {answer.setInputType(android.text.InputType.TYPE_CLASS_TEXT);action=Ui.button(this,"確認",false);action.setOnClickListener(v->submitType());newPhrase();}
            missionCard.addView(answer,Ui.gapTop(this,22));missionCard.addView(action,Ui.gapTop(this,10));
        }
        render();
    }

    private void newMath(){ java.util.Random r=new java.util.Random();int op=r.nextInt(3),a,b; if(op==2){a=2+r.nextInt(11);b=2+r.nextInt(11);expectedAnswer=a*b;prompt.setText(a+" × "+b+" = ?");} else if(op==1){a=10+r.nextInt(70);b=r.nextInt(a+1);expectedAnswer=a-b;prompt.setText(a+" − "+b+" = ?");} else {a=5+r.nextInt(70);b=5+r.nextInt(70);expectedAnswer=a+b;prompt.setText(a+" + "+b+" = ?");} if(answer!=null)answer.setText(""); }
    private void submitMath(){ if(answer==null||missionDone())return;try{int v=Integer.parseInt(answer.getText().toString().trim());if(v!=expectedAnswer){feedback.setText("違います。もう一度。");answer.setText("");return;}advanceRound();if(!missionDone())newMath();render();}catch(Throwable ignored){feedback.setText("答えを入力してください");} }
    private void newCode(){ expectedCode=String.format(java.util.Locale.US,"%06d",new java.util.Random().nextInt(1000000));prompt.setText(expectedCode);if(answer!=null){answer.setText("");answer.setHint("この6桁を入力");} }
    private void submitCode(){ if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedCode.equals(v)){feedback.setText("コードが違います");answer.setText("");return;}advanceRound();if(!missionDone())newCode();render(); }

    private void newMemory(){
        expectedCode=String.format(java.util.Locale.US,"%04d",new java.util.Random().nextInt(10000));
        prompt.setText("覚えてください\n"+expectedCode); answer.setText("");answer.setEnabled(false);action.setEnabled(false);feedback.setText("2秒後に消えます");
        missionHandler.postDelayed(()->{if(isFinishing()||missionDone())return;prompt.setText("覚えた4桁を入力");answer.setEnabled(true);action.setEnabled(true);answer.requestFocus();feedback.setText("");},2000);
    }
    private void submitMemory(){if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedCode.equals(v)){feedback.setText("違います。もう一度表示します");missionHandler.postDelayed(this::newMemory,600);return;}advanceRound();if(!missionDone())newMemory();render();}

    private void newPhrase(){String[] p={"二度寝しない","朝の準備を始める","起きて一日を始める","目を覚まして動く","今日も起きる"};expectedPhrase=p[new java.util.Random().nextInt(p.length)];prompt.setText(expectedPhrase);answer.setText("");answer.setHint("同じ文章を入力");}
    private void submitType(){if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedPhrase.equals(v)){feedback.setText("文章が一致しません");answer.setText("");return;}advanceRound();if(!missionDone())newPhrase();render();}

    private void startShakeMission(){
        try{sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);accelerometer=sensorManager==null?null:sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);if(accelerometer==null){feedback.setText("加速度センサーを利用できません");return;}shakeListener=new SensorEventListener(){public void onAccuracyChanged(Sensor s,int a){}public void onSensorChanged(SensorEvent e){if(missionDone())return;float x=e.values[0],y=e.values[1],z=e.values[2];double g=Math.sqrt(x*x+y*y+z*z)/SensorManager.GRAVITY_EARTH;long now=SystemClock.elapsedRealtime();if(g>2.2&&now-lastShakeMs>450){lastShakeMs=now;int p=Prefs.missionProgress(AlarmActivity.this)+1;Prefs.missionProgress(AlarmActivity.this,p);if(p>=targetCount())completeMission();render();}}};sensorManager.registerListener(shakeListener,accelerometer,SensorManager.SENSOR_DELAY_GAME);}catch(Throwable t){feedback.setText("シェイクを検出できません");}
    }

    private void startHoldTicker(){
        if(holdTicker==null)holdTicker=new Runnable(){public void run(){if(holdStartMs<=0||missionDone())return;long elapsed=SystemClock.elapsedRealtime()-holdStartMs;if(elapsed>=targetCount()*1000L){completeMission();render();return;}render();missionHandler.postDelayed(this,100);}};
        missionHandler.removeCallbacks(holdTicker);missionHandler.post(holdTicker);
    }

    private void advanceRound(){int p=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,p);feedback.setText("OK");if(p>=targetCount())completeMission();}
    private void completeMission(){Prefs.missionComplete(this,true);feedback.setText("完了しました");if(action!=null)action.setEnabled(false);if(answer!=null)answer.setEnabled(false);if(sensorManager!=null&&shakeListener!=null)try{sensorManager.unregisterListener(shakeListener);}catch(Throwable ignored){} }
    private boolean missionDone(){ if("STEPS".equals(sessionType()))return Prefs.stepSensorAvailable(this)&&Prefs.currentSteps(this)>=AlarmProfiles.steps(this,Prefs.activeAlarmId(this)); return Prefs.missionComplete(this); }

    private void render() {
        if (count == null || stop == null) return; String type=sessionType();int target=targetCount();boolean done=missionDone();
        if("STEPS".equals(type)){ int s=Prefs.currentSteps(this);boolean available=Prefs.stepSensorAvailable(this);count.setText(available?(s+" / "+target+" 歩"):"歩数センサーを利用できません");if(available&&s>=target){Prefs.missionComplete(this,true);done=true;}if(feedback!=null&&!done)feedback.setText(available?"あと "+Math.max(0,target-s)+" 歩":"身体活動権限を確認してください"); }
        else if("HOLD".equals(type)){long e=holdStartMs<=0?0:SystemClock.elapsedRealtime()-holdStartMs;float sec=Math.min(target,e/1000f);count.setText(String.format(java.util.Locale.JAPAN,"%.1f / %d 秒",sec,target));}
        else { int p=Prefs.missionProgress(this);count.setText(p+" / "+target);if("TAP".equals(type)&&action!=null)action.setText(done?"完了":"タップ  あと "+Math.max(0,target-p)); }
        stop.setEnabled(done);stop.setText(done?"アラームを停止":"ミッションを完了してください");
    }

    @Override protected void onResume() { super.onResume(); visible = true; render(); }
    @Override protected void onPause() { visible = false; super.onPause(); }
    @Override protected void onDestroy() { visible = false; missionHandler.removeCallbacksAndMessages(null); if(sensorManager!=null&&shakeListener!=null)try{sensorManager.unregisterListener(shakeListener);}catch(Throwable ignored){} if (updates != null) { try { unregisterReceiver(updates); } catch (Throwable ignored) {} } super.onDestroy(); }
    @Override public void onBackPressed() { if (!Prefs.active(this)) super.onBackPressed(); }
}
