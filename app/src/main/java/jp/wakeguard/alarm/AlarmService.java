package jp.wakeguard.alarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class AlarmService extends Service implements SensorEventListener {
    public static final String ACTION_FIRE_NEW = "jp.wakeguard.alarm.FIRE_NEW";
    public static final String ACTION_FIRE_TEST = "jp.wakeguard.alarm.FIRE_TEST";
    public static final String ACTION_RESTORE = "jp.wakeguard.alarm.RESTORE";
    public static final String EXTRA_EPOCH_DAY = "epochDay";
    public static final String EXTRA_ALARM_ID = "alarmId";
    public static final String EXTRA_EXPECTED_AT = "expectedAt";
    public static final String ACTION_STOP = "jp.wakeguard.alarm.STOP";
    public static final String ACTION_SNOOZE = "jp.wakeguard.alarm.SNOOZE";
    public static final String ACTION_VIDEO_READY = "jp.wakeguard.alarm.VIDEO_READY";
    public static final String ACTION_VIDEO_FALLBACK = "jp.wakeguard.alarm.VIDEO_FALLBACK";
    public static final String ACTION_UPDATE = "jp.wakeguard.alarm.UPDATE";
    public static final String CHANNEL = "wake_guard_alarm_v5";
    public static volatile boolean running = false;

    private MediaPlayer player;
    private ToneGenerator fallbackTone;
    private Handler toneHandler;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private PowerManager.WakeLock screenWakeLock;
    private SensorManager sensorManager;
    private Sensor stepCounter;
    private Sensor stepDetector;
    private Sensor linearAcceleration;
    private Sensor gyroscope;
    private long rejectStepsUntilElapsed = 0L;
    private long lastAcceptedStepElapsed = 0L;
    private float rawCounterLast = -1f;
    private long lastCounterCallbackElapsed = 0L;
    private long lastStrongLinearElapsed = 0L;
    private long lastStrongGyroElapsed = 0L;
    private long lastConfirmedShakeElapsed = 0L;
    private long strongLinearBurstStartElapsed = 0L;
    private int strongLinearBurstCount = 0;
    private long strongGyroBurstStartElapsed = 0L;
    private int strongGyroBurstCount = 0;
    private Handler guardHandler;
    private Handler ringHandler;
    private Handler fullStopHandler;
    private Handler videoFallbackHandler;
    private boolean videoPlaybackReady=false;
    private WindowManager windowManager;
    private View overlayView;
    private TextView overlayCount;
    private Button overlayStop;

    private final Runnable guard = new Runnable() {
        @Override public void run() {
            try {
                if (!running || !Prefs.active(AlarmService.this)) return;
                if (Settings.canDrawOverlays(AlarmService.this)) {
                    if (!AlarmActivity.visible) {
                        try { AlarmActivity.launch(AlarmService.this); } catch (Throwable ignored) {}
                    }
                    if (isActiveVideo() || AlarmActivity.visible) removeOverlay();
                    else ensureOverlay();
                }
            } catch (Throwable t) {
                try {
                    Prefs.lastAlarmError(AlarmService.this, "Guard: " + t.getClass().getSimpleName()
                            + (t.getMessage() == null ? "" : ": " + t.getMessage()));
                } catch (Throwable ignored) {}
            }
            try {
                if (guardHandler != null && running && Prefs.active(AlarmService.this))
                    guardHandler.postDelayed(this, 3000);
            } catch (Throwable ignored) {}
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        guardHandler = new Handler(Looper.getMainLooper());
        ringHandler = new Handler(Looper.getMainLooper());
        fullStopHandler = new Handler(Looper.getMainLooper());
        videoFallbackHandler = new Handler(Looper.getMainLooper());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            return handleStartCommand(intent, flags, startId);
        } catch (Throwable t) {
            String msg = t.getClass().getSimpleName();
            if (t.getMessage() != null && !t.getMessage().isEmpty()) msg += ": " + t.getMessage();
            Prefs.lastAlarmError(this, msg);
            Prefs.active(this, false);
            running = false;
            try { cleanupOutputs(); } catch (Throwable ignored) {}
            try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) {}
            try { stopSelf(); } catch (Throwable ignored) {}
            return START_NOT_STICKY;
        }
    }

    private int handleStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        android.util.Log.i("IGNIDO_ALARM", "Service action=" + action + " running=" + running + " active=" + Prefs.active(this));
        // Only a new alarm attempt clears a previous error. Auxiliary updates
        // must not erase the evidence of a disappearing ring screen.
        if (ACTION_FIRE_NEW.equals(action) || ACTION_FIRE_TEST.equals(action)) Prefs.lastAlarmError(this, "");
        if (ACTION_SNOOZE.equals(action)) {
            int snoozeMinutes=AlarmProfiles.snoozeMinutes(this,Prefs.activeAlarmId(this));
            if(Prefs.sessionIsTest(this)){stopAlarm();}
            else if(snoozeMinutes>0){SnoozeScheduler.schedule(this,Prefs.activeAlarmId(this),snoozeMinutes);stopAlarm();}
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(action)) {
            if (Prefs.missionComplete(this)) {
                StreakTracker.recordWakeSuccess(this);
                stopAlarm();
            }
            return START_NOT_STICKY;
        }

        if(ACTION_VIDEO_READY.equals(action)){videoPlaybackReady=true;cancelVideoFallback();removeOverlay();if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))silenceServiceAudioForVideo();return START_STICKY;}
        if(ACTION_VIDEO_FALLBACK.equals(action)){videoPlaybackReady=false;cancelVideoFallback();if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))startVideoFallbackAudio();return START_STICKY;}

        boolean scheduledSession = ACTION_FIRE_NEW.equals(action);
        boolean testSession = ACTION_FIRE_TEST.equals(action);
        boolean restoreSession = ACTION_RESTORE.equals(action) || (intent == null && Prefs.active(this));
        boolean newSession = scheduledSession || testSession;

        if (newSession) {
            long requestedAlarmId = intent == null ? AlarmScheduler.PRIMARY_ALARM_ID
                    : intent.getLongExtra(EXTRA_ALARM_ID, AlarmScheduler.PRIMARY_ALARM_ID);
            long requestedEpochDay = scheduledSession && intent != null ? intent.getLongExtra(EXTRA_EPOCH_DAY, -1L) : -1L;
            long requestedExpectedAt = scheduledSession && intent != null ? intent.getLongExtra(EXTRA_EXPECTED_AT, -1L) : -1L;
            if (scheduledSession && running && Prefs.active(this)) {
                boolean sameActive = Prefs.activeAlarmId(this) == requestedAlarmId
                        && Prefs.sessionEpochDay(this) == requestedEpochDay
                        && (requestedExpectedAt <= 0L || Prefs.sessionExpectedAt(this) == requestedExpectedAt);
                if (!sameActive && !AlarmOccurrenceGuard.wasDelivered(this, requestedAlarmId, requestedExpectedAt, requestedEpochDay))
                    PendingAlarmQueue.enqueue(this, requestedAlarmId, requestedEpochDay, requestedExpectedAt);
                try {
                    NotificationManager nm=getSystemService(NotificationManager.class);
                    if(nm!=null)nm.notify(42,buildNotification());
                    sendBroadcast(new Intent(ACTION_UPDATE).setPackage(getPackageName()));
                } catch(Throwable ignored) {}
                return START_STICKY;
            }
            if (scheduledSession && AlarmOccurrenceGuard.wasDelivered(this, requestedAlarmId, requestedExpectedAt, requestedEpochDay)) {
                if (!Prefs.active(this)) stopSelf();
                return START_NOT_STICKY;
            }
            if (scheduledSession) AlarmOccurrenceGuard.markDelivered(this, requestedAlarmId, requestedExpectedAt, requestedEpochDay);
            Prefs.activeAlarmId(this, requestedAlarmId);
            String chosenMission = intent == null ? "" : intent.getStringExtra("missionType");
            if (chosenMission == null || chosenMission.isEmpty()) chosenMission = AlarmProfiles.resolveSessionMission(this, requestedAlarmId);
            Prefs.sessionMissionType(this, chosenMission);
            Prefs.missionProgress(this, 0);
            Prefs.missionComplete(this, "NONE".equals(AlarmStore.normalizeMission(chosenMission)));
            // Repeated tests must start from a clean service state. A previous session
            // can still be alive even if its Activity disappeared or the UI crashed.
            running = false;
            try { cleanupOutputs(); } catch (Throwable ignored) {}
            try { restoreAlarmVolume(); } catch (Throwable ignored) {}
            Prefs.active(this, true);
            Prefs.baseline(this, -1f);
            Prefs.currentSteps(this, 0);
            rejectStepsUntilElapsed = 0L;
            lastAcceptedStepElapsed = 0L;
            rawCounterLast = -1f;
            strongLinearBurstStartElapsed = 0L; strongLinearBurstCount = 0;
            strongGyroBurstStartElapsed = 0L; strongGyroBurstCount = 0;
            Prefs.sessionIsTest(this, testSession);
            Prefs.sessionStartedAtMs(this,System.currentTimeMillis());
            Prefs.sessionSilenced(this,false);
            videoPlaybackReady=false;
            cancelVideoFallback();
            Prefs.sessionEpochDay(this, requestedEpochDay);
            Prefs.sessionExpectedAt(this, requestedExpectedAt);
        } else if (!restoreSession || !Prefs.active(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(42, buildNotification());
        wakeScreenForAlarm();
        if(applyAutoStopState()) return START_NOT_STICKY;
        if(!Prefs.sessionSilenced(this)) startAlarmOutputs();
        scheduleAutoStops();
        if ("STEPS".equals(Prefs.sessionMissionType(this))) startStepSensor();
        else { Prefs.stepSensorAvailable(this, true); }
        running = true;
        if (guardHandler != null) guardHandler.removeCallbacks(guard);
        guardHandler.post(guard);

        // Scheduled/background alarms use Notification.setFullScreenIntent.
        // Foreground tests keep the direct Activity launch that already works.
        if (testSession) {
            try { AlarmActivity.launch(this); } catch (Throwable ignored) {}
        } else if (scheduledSession) {
            // Secondary best-effort path for OEMs that suppress full-screen intents.
            // The AlarmManager Activity PendingIntent above is still the main fallback.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (!Prefs.active(AlarmService.this)) return;
                    KeyguardManager km = getSystemService(KeyguardManager.class);
                    PowerManager pm = getSystemService(PowerManager.class);
                    boolean locked = km != null && km.isKeyguardLocked();
                    boolean screenOff = pm != null && !pm.isInteractive();
                    if (locked || screenOff) AlarmActivity.launch(AlarmService.this);
                } catch (Throwable t) {
                    try { Prefs.lastAlarmError(AlarmService.this,
                            "LockUiFallback: " + t.getClass().getSimpleName()
                                    + (t.getMessage() == null ? "" : ": " + t.getMessage())); }
                    catch (Throwable ignored) {}
                }
            }, 1300L);
        }
        if (Settings.canDrawOverlays(this)) ensureOverlay();
        return START_STICKY;
    }

    private Notification buildNotification() {
        Intent full = new Intent(this, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        final int fullFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullPi;
        if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions ao = ActivityOptions.makeBasic();
            ao.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            fullPi = PendingIntent.getActivity(this, 4200, full, fullFlags, ao.toBundle());
        } else {
            fullPi = PendingIntent.getActivity(this, 4200, full, fullFlags);
        }
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_ignido_notification)
                .setContentTitle(AlarmProfiles.label(this, Prefs.activeAlarmId(this)))
                .setContentText(notificationMissionText())
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true);
        if (Build.VERSION.SDK_INT >= 31) {
            try { b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE); }
            catch (Throwable ignored) {}
        }
        return b.build();
    }

    public static void ensureNotificationChannel(Context context) {
        try {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, I18n.tr(context,"起床アラーム"), NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(I18n.tr(context,"ミッション式の起床アラーム"));
            ch.setSound(null, null); // Alarm audio is controlled by AlarmService.
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {
            // Settings must remain usable even on OEMs with notification quirks.
        }
    }

    private String notificationMissionText() {
        String t = AlarmStore.normalizeMission(Prefs.sessionMissionType(this));
        long id = Prefs.activeAlarmId(this);
        int n = AlarmProfiles.missionCount(this,id);
        String suffix = PendingAlarmQueue.size(this)>0 ? I18n.tr(this," ・ 待機中 " + PendingAlarmQueue.size(this) + "件") : "";
        if ("NONE".equals(t)) return I18n.tr(this,"そのまま停止できます") + suffix;
        if ("STEPS".equals(t)) return I18n.tr(this,"あと " + Math.max(0, AlarmProfiles.steps(this,id) - Prefs.currentSteps(this)) + " 歩で解除") + suffix;
        if ("MATH".equals(t)) return I18n.tr(this,"計算 " + n + "問で解除") + suffix;
        if ("TAP".equals(t)) return I18n.tr(this,"連打 " + n + "回で解除") + suffix;
        if ("CODE".equals(t)) return I18n.tr(this,"コード入力 " + n + "回で解除") + suffix;
        if ("SHAKE".equals(t)) return I18n.tr(this,"シェイク " + n + "回で解除") + suffix;
        if ("MEMORY".equals(t)) return I18n.tr(this,"記憶問題 " + n + "問で解除") + suffix;
        if ("TYPE".equals(t)) return I18n.tr(this,"文章入力 " + n + "文で解除") + suffix;
        if ("HOLD".equals(t)) return I18n.tr(this,"長押し " + Math.max(2,Math.min(30,n)) + "秒で解除") + suffix;
        if ("SWIPE".equals(t)) return I18n.tr(this,"スワイプ " + n + "回で解除") + suffix;
        if ("ORDER".equals(t)) return I18n.tr(this,"順番タップ " + n + "回で解除") + suffix;
        if ("REVERSE".equals(t)) return I18n.tr(this,"逆順入力 " + n + "問で解除") + suffix;
        return I18n.tr(this,"ミッションを完了すると解除できます") + suffix;
    }

    private void createChannel() {
        ensureNotificationChannel(this);
    }

    private void wakeScreenForAlarm() {
        try {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm == null) return;
            if (screenWakeLock != null) {
                try { if (screenWakeLock.isHeld()) screenWakeLock.release(); } catch (Throwable ignored) {}
            }
            screenWakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "WakeGuard:ScreenAlarm");
            screenWakeLock.setReferenceCounted(false);
            screenWakeLock.acquire(30000L);
        } catch (Throwable t) {
            try { Prefs.lastAlarmError(this, "ScreenWake: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}
        }
    }

    private boolean applyAutoStopState() {
        long started=Prefs.sessionStartedAtMs(this);
        if(started<=0L){started=System.currentTimeMillis();Prefs.sessionStartedAtMs(this,started);}
        long elapsed=Math.max(0L,System.currentTimeMillis()-started);
        int full=AlarmProfiles.fullStopDurationSec(this,Prefs.activeAlarmId(this));
        if(full>0&&elapsed>=full*1000L){stopAlarm();return true;}
        int ring=AlarmProfiles.ringDurationSec(this,Prefs.activeAlarmId(this));
        Prefs.sessionSilenced(this,ring>0&&elapsed>=ring*1000L);
        return false;
    }

    private void scheduleAutoStops() {
        if(ringHandler==null)ringHandler=new Handler(Looper.getMainLooper());
        if(fullStopHandler==null)fullStopHandler=new Handler(Looper.getMainLooper());
        ringHandler.removeCallbacksAndMessages(null);
        fullStopHandler.removeCallbacksAndMessages(null);
        long started=Prefs.sessionStartedAtMs(this);
        if(started<=0L){started=System.currentTimeMillis();Prefs.sessionStartedAtMs(this,started);}
        long elapsed=Math.max(0L,System.currentTimeMillis()-started);

        int ring=AlarmProfiles.ringDurationSec(this,Prefs.activeAlarmId(this));
        if(ring>0&&!Prefs.sessionSilenced(this)){
            long remain=ring*1000L-elapsed;
            if(remain<=0L)silenceRingingOutputs();
            else ringHandler.postDelayed(()->{if(Prefs.active(AlarmService.this))silenceRingingOutputs();},remain);
        }

        int full=AlarmProfiles.fullStopDurationSec(this,Prefs.activeAlarmId(this));
        if(full>0){
            long remain=full*1000L-elapsed;
            if(remain<=0L)stopAlarm();
            else fullStopHandler.postDelayed(()->{if(Prefs.active(AlarmService.this))stopAlarm();},remain);
        }
    }

    private void silenceRingingOutputs() {
        Prefs.sessionSilenced(this,true);
        if(player!=null){try{player.stop();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;}
        stopFallbackTone();
        if(vibrator!=null){try{vibrator.cancel();}catch(Throwable ignored){}vibrator=null;}
        restoreAlarmVolume();
        try{sendBroadcast(new Intent(ACTION_UPDATE).setPackage(getPackageName()));}catch(Throwable ignored){}
    }

    private boolean isActiveVideo(){return isVideoMedia(AlarmProfiles.soundUri(this,Prefs.activeAlarmId(this)),AlarmProfiles.soundName(this,Prefs.activeAlarmId(this)));}
    private boolean isVideoMedia(String raw,String name){
        if(raw!=null&&!raw.trim().isEmpty()){
            try{if(raw.startsWith("content:")){String t=getContentResolver().getType(Uri.parse(raw));if(t!=null&&t.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}}catch(Throwable ignored){}
            String x=raw.toLowerCase(java.util.Locale.ROOT);if(x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts"))return true;
        }
        String n=name==null?"":name.toLowerCase(java.util.Locale.ROOT);return n.endsWith(".mp4")||n.endsWith(".m4v")||n.endsWith(".3gp")||n.endsWith(".3gpp")||n.endsWith(".webm")||n.endsWith(".mkv")||n.endsWith(".ts");
    }
    private void cancelVideoFallback(){try{if(videoFallbackHandler!=null)videoFallbackHandler.removeCallbacksAndMessages(null);}catch(Throwable ignored){}}
    private void scheduleVideoFallback(){cancelVideoFallback();if(videoFallbackHandler==null)videoFallbackHandler=new Handler(Looper.getMainLooper());videoFallbackHandler.postDelayed(()->{if(Prefs.active(AlarmService.this)&&isActiveVideo()&&!Prefs.sessionSilenced(AlarmService.this)&&!videoPlaybackReady)startVideoFallbackAudio();},5000L);}
    private void silenceServiceAudioForVideo(){if(player!=null){try{player.stop();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;}stopFallbackTone();}
    private void startVideoFallbackAudio(){if(videoPlaybackReady)return;prepareAlarmVolume();if(player==null)player=createPlayer("");boolean ok=false;try{ok=player!=null&&player.isPlaying();}catch(Throwable ignored){}if(!ok)startFallbackTone();}

    private void startAlarmOutputs() {
        if (wakeLock == null) {
            try {
                PowerManager pm = getSystemService(PowerManager.class);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeGuard:Alarm");
                    wakeLock.setReferenceCounted(false);
                    wakeLock.acquire();
                }
            } catch (Throwable ignored) { wakeLock = null; }
        }

        prepareAlarmVolume();

        if(isActiveVideo()){
            videoPlaybackReady=false;
            silenceServiceAudioForVideo();
            scheduleVideoFallback();
        }else{
            cancelVideoFallback();
            if (player == null) {
                String chosen=AlarmProfiles.soundUri(this, Prefs.activeAlarmId(this));
                player = createPlayer(chosen);
                if (player == null) player = createPlayer("");
            }
            boolean playing = false;
            try { playing = player != null && player.isPlaying(); } catch (Throwable ignored) {}
            if (!playing) startFallbackTone();
        }

        try {
            if (vibrator == null) vibrator = getSystemService(Vibrator.class);
            if (vibrator != null && vibrator.hasVibrator()) {
                String mode = AlarmProfiles.vibration(this, Prefs.activeAlarmId(this));
                if ("OFF".equals(mode)) return;
                long[] timings;
                int[] amps;
                if ("STRONG".equals(mode)) {
                    timings = new long[]{0, 1400, 180, 1400, 180};
                    amps = new int[]{0, 255, 0, 255, 0};
                } else {
                    timings = new long[]{0, 350, 90, 850, 160, 1500, 70, 280, 220, 1100, 130};
                    amps = new int[]{0, 255, 0, 220, 0, 255, 0, 180, 0, 255, 0};
                }
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, 0));
            }
        } catch (Throwable ignored) { vibrator = null; }
    }

    private MediaPlayer createPlayer(String custom) {
        MediaPlayer p = new MediaPlayer();
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        try {
            if (custom == null || custom.isEmpty()) {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                p.setDataSource(this, uri);
            } else if (custom.startsWith("/")) {
                // v0.5.2 copies the selected audio into app-private storage so the
                // alarm does not depend on a temporary Storage Access Framework grant.
                p.setDataSource(custom);
            } else {
                // Backward compatibility with v0.5.1 content:// selections.
                p.setDataSource(this, Uri.parse(custom));
            }
            p.setLooping(true);
            p.prepare();
            p.setVolume(1f, 1f);
            p.start();
            return p;
        } catch (Throwable e) {
            try {
                if (custom != null && !custom.isEmpty())
                    Prefs.lastAlarmError(this, "AudioPlay: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            } catch (Throwable ignored) {}
            try { p.release(); } catch (Throwable ignored) {}
            return null;
        }
    }

    private void prepareAlarmVolume() {
        try {
            AudioManager am = getSystemService(AudioManager.class);
            if (am == null || am.isVolumeFixed()) return;
            int original = Prefs.originalAlarmVolume(this);
            if (original < 0) {
                original = am.getStreamVolume(AudioManager.STREAM_ALARM);
                Prefs.originalAlarmVolume(this, original);
            }
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_ALARM));
            int percent = AlarmProfiles.volume(this, Prefs.activeAlarmId(this));
            int target = percent == 0 ? 0 : Math.max(1, Math.round(max * (percent / 100f)));
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
        } catch (Throwable t) {
            try { Prefs.lastAlarmError(this, "AlarmVolume: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}
        }
    }

    private void restoreAlarmVolume() {
        try {
            int original = Prefs.originalAlarmVolume(this);
            if (original < 0) return;
            AudioManager am = getSystemService(AudioManager.class);
            if (am != null && !am.isVolumeFixed()) am.setStreamVolume(AudioManager.STREAM_ALARM, original, 0);
        } catch (Throwable ignored) {
        } finally {
            try { Prefs.originalAlarmVolume(this, -1); } catch (Throwable ignored) {}
        }
    }

    private void startFallbackTone() {
        if (fallbackTone != null) return;
        try {
            fallbackTone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            if (toneHandler == null) toneHandler = new Handler(Looper.getMainLooper());
            Runnable[] loop = new Runnable[1];
            loop[0] = () -> {
                try {
                    if (!running && !Prefs.active(AlarmService.this)) return;
                    if (fallbackTone != null) fallbackTone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700);
                    if (toneHandler != null) toneHandler.postDelayed(loop[0], 1000);
                } catch (Throwable ignored) {}
            };
            toneHandler.post(loop[0]);
        } catch (Throwable t) {
            try { Prefs.lastAlarmError(this, "FallbackTone: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}
        }
    }

    private void stopFallbackTone() {
        try { if (toneHandler != null) toneHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}
        toneHandler = null;
        try { if (fallbackTone != null) { fallbackTone.stopTone(); fallbackTone.release(); } } catch (Throwable ignored) {}
        fallbackTone = null;
    }

    private void startStepSensor() {
        // Prefer Android's dedicated hardware STEP_COUNTER. Android documents this
        // sensor as the more accurate aggregate step source (it may batch updates).
        // Do not veto it with accelerometer/gyro motion: normal walking was being
        // rejected by the old anti-shake filter, causing severe under-counting.
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            Prefs.stepSensorAvailable(this, false);
            sensorManager = null;
            stepCounter = null;
            stepDetector = null;
            updateViews();
            return;
        }

        if (sensorManager != null) return;
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager == null) {
                Prefs.stepSensorAvailable(this, false);
                updateViews();
                return;
            }

            stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            if (linearAcceleration == null)
                linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            Sensor use = stepCounter != null ? stepCounter : stepDetector;
            Prefs.detectorMode(this, stepCounter == null && stepDetector != null);

            boolean registered = false;
            if (use != null) {
                registered = sensorManager.registerListener(
                        this, use, SensorManager.SENSOR_DELAY_NORMAL);
            }

            // Auxiliary sensors are anti-shake evidence only. They never add steps and
            // only veto an extreme hand-shake burst. Thresholds are deliberately high
            // so ordinary walking/swinging does not recreate the old under-count bug.
            try {
                if (linearAcceleration != null)
                    sensorManager.registerListener(this, linearAcceleration, SensorManager.SENSOR_DELAY_GAME);
            } catch (Throwable ignored) {}
            try {
                if (gyroscope != null)
                    sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            } catch (Throwable ignored) {}

            Prefs.stepSensorAvailable(this, use != null && registered);
        } catch (Throwable e) {
            try {
                if (sensorManager != null) sensorManager.unregisterListener(this);
            } catch (Throwable ignored) {}
            sensorManager = null;
            stepCounter = null;
            stepDetector = null;
            Prefs.stepSensorAvailable(this, false);
        }
        updateViews();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        try {
            if (!running && !Prefs.active(this)) return;
            if (event == null || event.sensor == null || event.values == null || event.values.length == 0) return;

            int type = event.sensor.getType();
            int accepted = Prefs.currentSteps(this);
            long now = SystemClock.elapsedRealtime();

            // Detect only very aggressive hand shaking: strong translation AND strong
            // rotation within a short window. A single walking jolt is not enough.
            if (type == Sensor.TYPE_LINEAR_ACCELERATION || type == Sensor.TYPE_ACCELEROMETER) {
                if (event.values.length < 3) return;
                double x = event.values[0], y = event.values[1], z = event.values[2];
                double magnitude = Math.sqrt(x * x + y * y + z * z);
                double threshold = type == Sensor.TYPE_ACCELEROMETER ? 22.0 : 14.0;
                if (magnitude >= threshold) {
                    lastStrongLinearElapsed = now;
                    if (strongLinearBurstStartElapsed == 0L || now-strongLinearBurstStartElapsed>900L) {
                        strongLinearBurstStartElapsed=now; strongLinearBurstCount=1;
                    } else strongLinearBurstCount++;
                    if (lastStrongGyroElapsed != 0L
                            && Math.abs(lastStrongLinearElapsed - lastStrongGyroElapsed) <= 650L)
                        lastConfirmedShakeElapsed = now;
                    if (strongLinearBurstCount >= 5) lastConfirmedShakeElapsed = now;
                }
                return;
            }
            if (type == Sensor.TYPE_GYROSCOPE) {
                if (event.values.length < 3) return;
                double x = event.values[0], y = event.values[1], z = event.values[2];
                double magnitude = Math.sqrt(x * x + y * y + z * z);
                if (magnitude >= 3.8) {
                    lastStrongGyroElapsed = now;
                    if (strongGyroBurstStartElapsed == 0L || now-strongGyroBurstStartElapsed>900L) {
                        strongGyroBurstStartElapsed=now; strongGyroBurstCount=1;
                    } else strongGyroBurstCount++;
                    if (lastStrongLinearElapsed != 0L
                            && Math.abs(lastStrongGyroElapsed - lastStrongLinearElapsed) <= 650L)
                        lastConfirmedShakeElapsed = now;
                    if (strongGyroBurstCount >= 5) lastConfirmedShakeElapsed = now;
                }
                return;
            }

            if (type == Sensor.TYPE_STEP_COUNTER) {
                float raw = event.values[0];
                if (rawCounterLast < 0f) {
                    // Establish the mission baseline. STEP_COUNTER is cumulative since
                    // boot/activation; only subsequent deltas belong to this alarm.
                    rawCounterLast = raw;
                    lastCounterCallbackElapsed = now;
                    return;
                }

                int delta = Math.max(0, Math.round(raw - rawCounterLast));
                rawCounterLast = raw;
                long interval = lastCounterCallbackElapsed == 0L
                        ? 1000L : Math.max(250L, now - lastCounterCallbackElapsed);
                lastCounterCallbackElapsed = now;
                if (delta <= 0) return;

                // If the phone was just violently translated+rotated, discard the
                // resulting hardware false-positive batch. Because rawCounterLast is
                // advanced above, those fake steps cannot leak back in later.
                if (lastConfirmedShakeElapsed != 0L
                        && now - lastConfirmedShakeElapsed <= 2400L) return;

                // OEM step counters can batch or jump after vigorous phone movement. Never allow
                // one delayed callback to cash in an arbitrarily long interval. A 2.5 s credit
                // window still permits normal/brisk walking while bounding one callback to 9 steps.
                long creditInterval = Math.min(interval, 2500L);
                int physicallyPlausible = Math.max(1,
                        (int)Math.ceil((creditInterval / 1000.0) * 3.2) + 1);
                accepted += Math.min(delta, physicallyPlausible);
            } else if (type == Sensor.TYPE_STEP_DETECTOR) {
                // Fallback for devices without STEP_COUNTER. The detector emits one
                // 1.0 event per detected step. Only reject physically impossible event
                // rates; do not use gyro/acceleration vetoes that also reject walking.
                if (lastConfirmedShakeElapsed != 0L
                        && now - lastConfirmedShakeElapsed <= 2200L) return;
                if (lastAcceptedStepElapsed != 0L && now - lastAcceptedStepElapsed < 180L) return;
                lastAcceptedStepElapsed = now;
                accepted += 1;
            } else {
                return;
            }

            Prefs.currentSteps(this, accepted);
            if (accepted >= AlarmProfiles.steps(this, Prefs.activeAlarmId(this))) Prefs.missionComplete(this, true);
            updateViews();
            Intent u = new Intent(ACTION_UPDATE).setPackage(getPackageName()).putExtra("steps", accepted);
            sendBroadcast(u);
        } catch (Throwable t) {
            try {
                Prefs.lastAlarmError(this, "Sensor: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            } catch (Throwable ignored) {}
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void ensureOverlay() {
        // Do not cover AlarmActivity. It already renders the mission UI, and for video
        // alarms its PlayerView must stay visible behind that UI.
        if (!"STEPS".equals(AlarmStore.normalizeMission(Prefs.sessionMissionType(this)))
                || isActiveVideo() || AlarmActivity.visible) { removeOverlay(); return; }
        if (!Prefs.active(this) || !Settings.canDrawOverlays(this) || overlayView != null) return;
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) return;

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(44, 64, 44, 64);
            root.setBackgroundColor(Color.BLACK);

            TextView title = new TextView(this);
            title.setText(AlarmProfiles.label(this, Prefs.activeAlarmId(this)));
            title.setTextColor(Color.WHITE);
            title.setTextSize(28);
            title.setGravity(Gravity.CENTER);
            root.addView(title);

            TextView note = new TextView(this);
            note.setText(I18n.tr(this,"歩数ミッション完了までアラームは停止しません"));
            note.setTextColor(0xFFBBBBBB);
            note.setTextSize(16);
            note.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.setMargins(0, 24, 0, 40);
            root.addView(note, np);

            overlayCount = new TextView(this);
            overlayCount.setTextColor(Color.WHITE);
            overlayCount.setTextSize(52);
            overlayCount.setGravity(Gravity.CENTER);
            root.addView(overlayCount);

            overlayStop = new Button(this);
            overlayStop.setAllCaps(false);
            overlayStop.setEnabled(false);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.setMargins(0, 48, 0, 0);
            root.addView(overlayStop, sp);
            overlayStop.setOnClickListener(v -> requestStopIfComplete());

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                    android.graphics.PixelFormat.OPAQUE);
            lp.gravity = Gravity.TOP | Gravity.START;
            overlayView = root;
            windowManager.addView(overlayView, lp);
            updateViews();
        } catch (Throwable t) {
            removeOverlay();
        }
    }

    private void updateViews() {
        String t = AlarmStore.normalizeMission(Prefs.sessionMissionType(this));
        boolean done = Prefs.missionComplete(this);
        String countText;
        String buttonText;
        if ("STEPS".equals(t)) {
            int s = Prefs.currentSteps(this);
            int target = AlarmProfiles.steps(this, Prefs.activeAlarmId(this));
            boolean available = Prefs.stepSensorAvailable(this);
            countText = available ? I18n.tr(this,s + " / " + target + " 歩") : I18n.tr(this,"歩数センサーを利用できません");
            done = available && s >= target;
            if (done) Prefs.missionComplete(this, true);
            buttonText = done ? I18n.tr(this,"停止する") : (available ? I18n.tr(this,"あと " + Math.max(0, target - s) + " 歩") : I18n.tr(this,"アプリを開いて確認"));
        } else {
            countText = I18n.tr(this,AlarmProfiles.missionName(t)) + (I18n.language(this).equals("ko")?"을(를) 완료하세요":I18n.language(this).equals("en")?" — complete it": "を完了してください");
            buttonText = done ? I18n.tr(this,"停止する") : I18n.tr(this,"ミッション画面で解除");
        }
        if (overlayCount != null) overlayCount.setText(countText);
        if (overlayStop != null) { overlayStop.setEnabled(done); overlayStop.setText(buttonText); }
    }

    private void requestStopIfComplete() {
        try {
            if (Prefs.missionComplete(this)) {
                Intent stop = new Intent(this, AlarmService.class).setAction(ACTION_STOP);
                startService(stop);
            }
        } catch (Throwable t) {
            try { Prefs.lastAlarmError(this, "StopRequest: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}
        }
    }

    private void removeOverlay() {
        if (windowManager != null && overlayView != null) {
            try { windowManager.removeViewImmediate(overlayView); } catch (Throwable ignored) {}
        }
        overlayView = null;
        overlayCount = null;
        overlayStop = null;
        windowManager = null;
    }

    private void cleanupOutputs() {
        try { if (guardHandler != null) guardHandler.removeCallbacks(guard); } catch (Throwable ignored) {}
        try { if (ringHandler != null) ringHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}
        try { if (fullStopHandler != null) fullStopHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}
        cancelVideoFallback();
        videoPlaybackReady=false;
        if (sensorManager != null) {
            try { sensorManager.unregisterListener(this); } catch (Throwable ignored) {}
        }
        sensorManager = null;
        stepCounter = null;
        stepDetector = null;
        linearAcceleration = null;
        gyroscope = null;
        rawCounterLast = -1f;
        rejectStepsUntilElapsed = 0L;
        lastAcceptedStepElapsed = 0L;
        lastCounterCallbackElapsed = 0L;
        lastStrongLinearElapsed = 0L;
        lastStrongGyroElapsed = 0L;
        lastConfirmedShakeElapsed = 0L;
        strongLinearBurstStartElapsed = 0L; strongLinearBurstCount = 0;
        strongGyroBurstStartElapsed = 0L; strongGyroBurstCount = 0;
        removeOverlay();

        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        stopFallbackTone();
        if (vibrator != null) { try { vibrator.cancel(); } catch (Throwable ignored) {} }
        vibrator = null;
        if (wakeLock != null) { try { if (wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {} }
        wakeLock = null;
        if (screenWakeLock != null) { try { if (screenWakeLock.isHeld()) screenWakeLock.release(); } catch (Throwable ignored) {} }
        screenWakeLock = null;
    }

    private void stopAlarm() {
        running = false;
        Prefs.active(this, false);
        Prefs.sessionSilenced(this,false);
        Prefs.sessionStartedAtMs(this,0L);
        Prefs.sessionExpectedAt(this,-1L);
        cleanupOutputs();
        restoreAlarmVolume();

        PendingAlarmQueue.Item next = PendingAlarmQueue.poll(this);
        if (next != null) {
            Intent nextIntent = new Intent(this, AlarmService.class)
                    .setAction(ACTION_FIRE_NEW)
                    .putExtra(EXTRA_ALARM_ID, next.alarmId)
                    .putExtra(EXTRA_EPOCH_DAY, next.epochDay)
                    .putExtra(EXTRA_EXPECTED_AT, next.expectedAt);
            // Re-enter synchronously while this foreground service instance is alive. This keeps
            // the queued alarm reliable even under background-start restrictions.
            handleStartCommand(nextIntent, 0, 0);
            try { sendBroadcast(new Intent(ACTION_UPDATE).setPackage(getPackageName())); } catch (Throwable ignored) {}
            return;
        }

        try { sendBroadcast(new Intent(ACTION_UPDATE).setPackage(getPackageName())); } catch (Throwable ignored) {}
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Do not stop. The foreground service + START_STICKY keeps outputs alive.
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        boolean shouldRestore = Prefs.active(this);
        running = false;
        cleanupOutputs();
        super.onDestroy();
        // START_STICKY lets Android recreate the service when possible. Persisted active state
        // tells the null-intent restart path to resume rather than silently ending the alarm.
        if (!shouldRestore) Prefs.active(this, false);
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
