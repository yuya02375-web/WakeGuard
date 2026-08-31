package jp.wakeguard.alarm;

import android.app.PendingIntent;
import android.app.ActivityOptions;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
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
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this,24), Ui.dp(this,46), Ui.dp(this,24), Ui.dp(this,36)); root.setBackground(Ui.screenGradient(this));

        TextView eyebrow = Ui.overline(this, "WAKE PROTOCOL // ACTIVE"); root.addView(eyebrow);
        TextView title = Ui.title(this, AlarmProfiles.label(this, Prefs.activeAlarmId(this)), 29); title.setGravity(Gravity.CENTER); root.addView(title, Ui.gapTop(this,8));
        TextView now = Ui.title(this, java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), 54); now.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD); root.addView(now, Ui.gapTop(this,12));

        String type=sessionType(); LinearLayout badgeRow=new LinearLayout(this);badgeRow.setGravity(Gravity.CENTER);
        missionName=Ui.accentPill(this,AlarmProfiles.missionIcon(type)+"  "+AlarmProfiles.missionName(type));badgeRow.addView(missionName);root.addView(badgeRow,Ui.gapTop(this,16));

        missionCard = Ui.glowCard(this); missionCard.setGravity(Gravity.CENTER_HORIZONTAL); root.addView(missionCard, Ui.gapTop(this,18)); buildMission(type);

        stop = Ui.button(this, "ミッション完了後に解除できます", true); stop.setEnabled(false);
        stop.setOnClickListener(v -> { if (missionDone()) { try { startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP)); } catch (Throwable ignored) {} try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); } } });
        root.addView(stop, Ui.gapTop(this,20));
        TextView footer = Ui.text(this, "Home / 戻るでは解除されません", 12, Ui.MUTED); footer.setGravity(Gravity.CENTER); root.addView(footer, Ui.gapTop(this,16));
        setContentView(root);
    }

    private String sessionType(){ String t=Prefs.sessionMissionType(this); if(t==null||t.isEmpty())t=AlarmProfiles.resolveSessionMission(this,Prefs.activeAlarmId(this)); if("RANDOM".equals(t))t="STEPS"; return AlarmStore.normalizeMission(t); }
    private int targetCount(){ String t=sessionType(); long id=Prefs.activeAlarmId(this); if("STEPS".equals(t)) return AlarmProfiles.steps(this,id); int n=AlarmProfiles.missionCount(this,id); if("TAP".equals(t) && "RANDOM".equals(AlarmProfiles.missionType(this,id))) return Math.max(20,n*10); return n; }

    private void buildMission(String type){
        missionCard.removeAllViews(); count=Ui.title(this,"",36);count.setGravity(Gravity.CENTER);missionCard.addView(count);
        prompt=Ui.text(this,"",18,Ui.TEXT);prompt.setGravity(Gravity.CENTER);prompt.setPadding(0,Ui.dp(this,14),0,0);missionCard.addView(prompt);
        feedback=Ui.text(this,"",13,Ui.MUTED);feedback.setGravity(Gravity.CENTER);feedback.setPadding(0,Ui.dp(this,8),0,0);missionCard.addView(feedback); action=null;answer=null;
        if("STEPS".equals(type)){ prompt.setText("スマホを持って実際に歩いてください"); }
        else if("TAP".equals(type)){ action=Ui.button(this,"TAP",true);action.setTextSize(24);action.setOnClickListener(v->{if(missionDone())return;int p=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,p);if(p>=targetCount())completeMission();render();});missionCard.addView(action,Ui.gapTop(this,18));prompt.setText("大きなボタンを連打"); }
        else {
            answer=new EditText(this);answer.setTextColor(Ui.TEXT);answer.setHintTextColor(Ui.MUTED_2);answer.setGravity(Gravity.CENTER);answer.setTextSize(24);answer.setSingleLine(true);answer.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10));answer.setBackground(Ui.roundStroke(Ui.SURFACE_2,0xFF3A4A71,16,this));
            if("MATH".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);action=Ui.button(this,"回答する",true);action.setOnClickListener(v->submitMath());newMath();}
            else {answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);action=Ui.button(this,"コードを確認",true);action.setOnClickListener(v->submitCode());newCode();}
            missionCard.addView(answer,Ui.gapTop(this,18));missionCard.addView(action,Ui.gapTop(this,10));
        }
        render();
    }

    private void newMath(){ java.util.Random r=new java.util.Random();int op=r.nextInt(3),a,b; if(op==2){a=2+r.nextInt(11);b=2+r.nextInt(11);expectedAnswer=a*b;prompt.setText(a+" × "+b+" = ?");} else if(op==1){a=10+r.nextInt(70);b=r.nextInt(a+1);expectedAnswer=a-b;prompt.setText(a+" − "+b+" = ?");} else {a=5+r.nextInt(70);b=5+r.nextInt(70);expectedAnswer=a+b;prompt.setText(a+" + "+b+" = ?");} if(answer!=null)answer.setText(""); }
    private void submitMath(){ if(answer==null||missionDone())return;try{int v=Integer.parseInt(answer.getText().toString().trim());if(v!=expectedAnswer){feedback.setText("違います。もう一度。");answer.setText("");return;}int p=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,p);feedback.setText("正解  ✓");if(p>=targetCount())completeMission();else newMath();render();}catch(Throwable ignored){feedback.setText("答えを入力してください");} }
    private void newCode(){ expectedCode=String.format(java.util.Locale.US,"%06d",new java.util.Random().nextInt(1000000));prompt.setText(expectedCode);if(answer!=null){answer.setText("");answer.setHint("この6桁を入力");} }
    private void submitCode(){ if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedCode.equals(v)){feedback.setText("コードが違います");answer.setText("");return;}int p=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,p);feedback.setText("一致  ✓");if(p>=targetCount())completeMission();else newCode();render(); }
    private void completeMission(){Prefs.missionComplete(this,true);feedback.setText("MISSION COMPLETE  ✓");if(action!=null)action.setEnabled(false);if(answer!=null)answer.setEnabled(false);}
    private boolean missionDone(){ if("STEPS".equals(sessionType()))return Prefs.stepSensorAvailable(this)&&Prefs.currentSteps(this)>=AlarmProfiles.steps(this,Prefs.activeAlarmId(this)); return Prefs.missionComplete(this); }

    private void render() {
        if (count == null || stop == null) return; String type=sessionType();int target=targetCount();boolean done=missionDone();
        if("STEPS".equals(type)){ int s=Prefs.currentSteps(this);boolean available=Prefs.stepSensorAvailable(this);count.setText(available?(s+" / "+target+" 歩"):"歩数センサーを利用できません");if(available&&s>=target){Prefs.missionComplete(this,true);done=true;}if(feedback!=null&&!done)feedback.setText(available?"あと "+Math.max(0,target-s)+" 歩":"身体活動権限を確認してください"); }
        else { int p=Prefs.missionProgress(this);count.setText(p+" / "+target);if("TAP".equals(type)&&action!=null)action.setText(done?"完了":"TAP  •  あと "+Math.max(0,target-p)); }
        stop.setEnabled(done);stop.setText(done?"アラームを解除":"ミッションを完了してください");
    }

    @Override protected void onResume() { super.onResume(); visible = true; render(); }
    @Override protected void onPause() { visible = false; super.onPause(); }
    @Override protected void onDestroy() { visible = false; if (updates != null) { try { unregisterReceiver(updates); } catch (Throwable ignored) {} } super.onDestroy(); }
    @Override public void onBackPressed() { if (!Prefs.active(this)) super.onBackPressed(); }
}
