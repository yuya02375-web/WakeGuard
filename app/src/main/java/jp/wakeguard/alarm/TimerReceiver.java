package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.widget.RemoteViews;

public class TimerReceiver extends BroadcastReceiver {
    public static final String ACTION_TIMER="jp.wakeguard.alarm.TIMER_DONE", EXTRA_TIMER_ID="timerId", EXTRA_EXPECTED_AT="expectedAt";
    private static final String RUN_CHANNEL="wakeguard_timer_running_v2";
    private static final int BASE_REQUEST=82000,BASE_NOTIFY=92000,BASE_RUNNING=102000;

    @Override public void onReceive(Context context,Intent intent){long id=intent==null?-1L:intent.getLongExtra(EXTRA_TIMER_ID,-1L);long expectedAt=intent==null?-1L:intent.getLongExtra(EXTRA_EXPECTED_AT,-1L);TimerStore.Entry e=id>=1000?TimerStore.find(context,id):TimerStore.firstRunning(context);if(e==null||!e.running)return;if(expectedAt>0L&&Math.abs(e.endMs-expectedAt)>2000L){schedule(context,e.id,e.endMs);return;}long remaining=e.remaining();if(remaining>1500L){schedule(context,e.id,e.endMs);return;}complete(context,e.id);}
    private static int requestCode(long id){return BASE_REQUEST+(int)Math.floorMod(id,1000000L);} private static int notifyId(long id){return BASE_NOTIFY+(int)Math.floorMod(id,1000000L);} private static int runningId(long id){return BASE_RUNNING+(int)Math.floorMod(id,1000000L);}
    static PendingIntent pending(Context c,long id,long expectedAt,int flags){Intent i=new Intent(c,TimerReceiver.class).setAction(ACTION_TIMER).putExtra(EXTRA_TIMER_ID,id).putExtra(EXTRA_EXPECTED_AT,expectedAt);return PendingIntent.getBroadcast(c,requestCode(id),i,flags|PendingIntent.FLAG_IMMUTABLE);}
    private static PendingIntent openTimer(Context c,long id){Intent open=new Intent(c,ClockActivity.class).putExtra("mode","timer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);return PendingIntent.getActivity(c,requestCode(id)+1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}

    public static void schedule(Context c,long id,long when){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;PendingIntent pi=pending(c,id,when,PendingIntent.FLAG_UPDATE_CURRENT);try{if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.setExact(AlarmManager.RTC_WAKEUP,when,pi);}catch(SecurityException ex){if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.set(AlarmManager.RTC_WAKEUP,when,pi);}TimerStore.Entry e=TimerStore.find(c,id);if(e!=null&&e.running)showRunning(c,e);}
    public static void cancel(Context c,long id){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null)try{PendingIntent pi=pending(c,id,0L,PendingIntent.FLAG_NO_CREATE);if(pi!=null)am.cancel(pi);}catch(Throwable ignored){}hideRunning(c,id);}

    private static void ensureRunningChannel(Context c){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;NotificationChannel ch=new NotificationChannel(RUN_CHANNEL,I18n.tr(c,"実行中のタイマー"),NotificationManager.IMPORTANCE_DEFAULT);ch.setDescription(I18n.tr(c,"タイマーの残り時間を常に表示します"));ch.setSound(null,null);ch.enableVibration(false);ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);nm.createNotificationChannel(ch);}
    public static void ensureChannel(Context c){ensureRunningChannel(c);ensureDoneChannel(c,null);}
    public static void showRunning(Context c,TimerStore.Entry e){
        if(e==null||!e.running)return;
        ensureRunningChannel(c);
        String label=e.label==null||e.label.isEmpty()?I18n.tr(c,"タイマー"):e.label;
        long remaining=Math.max(0L,e.endMs-System.currentTimeMillis());
        long chronoBase=SystemClock.elapsedRealtime()+remaining;
        PendingIntent open=openTimer(c,e.id);

        RemoteViews compact=new RemoteViews(c.getPackageName(),R.layout.notification_timer_running);
        compact.setTextViewText(R.id.timer_notification_label,label);
        compact.setChronometer(R.id.timer_notification_chronometer,chronoBase,null,true);
        if(Build.VERSION.SDK_INT>=24)compact.setChronometerCountDown(R.id.timer_notification_chronometer,true);
        compact.setOnClickPendingIntent(R.id.timer_notification_root,open);

        RemoteViews big=new RemoteViews(c.getPackageName(),R.layout.notification_timer_running_big);
        big.setTextViewText(R.id.timer_notification_label,label);
        big.setChronometer(R.id.timer_notification_chronometer,chronoBase,null,true);
        if(Build.VERSION.SDK_INT>=24)big.setChronometerCountDown(R.id.timer_notification_chronometer,true);
        big.setOnClickPendingIntent(R.id.timer_notification_root,open);

        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,RUN_CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_ignido_notification)
         .setContentTitle(label).setContentText(I18n.tr(c,"残り時間"))
         .setCategory(Notification.CATEGORY_PROGRESS).setVisibility(Notification.VISIBILITY_PUBLIC)
         .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
         .setWhen(e.endMs).setUsesChronometer(true)
         .setCustomContentView(compact).setCustomBigContentView(big)
         .setPriority(Notification.PRIORITY_DEFAULT);
        if(Build.VERSION.SDK_INT>=24){b.setChronometerCountDown(true);b.setStyle(new Notification.DecoratedCustomViewStyle());}
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)try{nm.notify(runningId(e.id),b.build());}catch(SecurityException ignored){}
    }
    public static void hideRunning(Context c,long id){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.cancel(runningId(id));}

    private static boolean isVideo(Context c,TimerStore.Entry e){
        if(e==null||e.soundUri==null||e.soundUri.isEmpty())return false;
        try{String type=c.getContentResolver().getType(Uri.parse(e.soundUri));if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}catch(Throwable ignored){}
        String x=e.soundUri.toLowerCase(java.util.Locale.ROOT);
        int q=x.indexOf('?');if(q>=0)x=x.substring(0,q);int h=x.indexOf('#');if(h>=0)x=x.substring(0,h);
        return x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts");
    }
    private static Uri chosenSound(Context c,TimerStore.Entry e){if(!isVideo(c,e)&&e!=null&&e.soundUri!=null&&!e.soundUri.isEmpty())try{return Uri.parse(e.soundUri);}catch(Throwable ignored){}return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);}
    private static Intent videoIntent(Context c,TimerStore.Entry e){return new Intent(c,TimerVideoActivity.class).setData(Uri.parse("wakeguard://timer/"+e.id+"/video")).putExtra("videoUri",e.soundUri).putExtra("label",e.label==null?"":e.label).putExtra(EXTRA_TIMER_ID,e.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);}
    private static PendingIntent videoPendingIntent(Context c,TimerStore.Entry e,int flags){Intent i=videoIntent(c,e);int f=flags|PendingIntent.FLAG_IMMUTABLE;if(Build.VERSION.SDK_INT>=34){ActivityOptions creator=ActivityOptions.makeBasic();creator.setPendingIntentCreatorBackgroundActivityStartMode(Build.VERSION.SDK_INT>=36?ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS:ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);return PendingIntent.getActivity(c,requestCode(e.id)+2,i,f,creator.toBundle());}return PendingIntent.getActivity(c,requestCode(e.id)+2,i,f);}
    private static PendingIntent openCompleted(Context c,TimerStore.Entry e){if(isVideo(c,e))return videoPendingIntent(c,e,PendingIntent.FLAG_UPDATE_CURRENT);return openTimer(c,e.id);}
    private static boolean launchTimerVideo(Context c,TimerStore.Entry e){if(e==null||!isVideo(c,e))return false;if(ClockActivity.showTimerVideoIfVisible(e))return true;try{PendingIntent pi=videoPendingIntent(c,e,PendingIntent.FLAG_UPDATE_CURRENT);if(Build.VERSION.SDK_INT>=34){ActivityOptions sender=ActivityOptions.makeBasic();sender.setPendingIntentBackgroundActivityStartMode(Build.VERSION.SDK_INT>=36?ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS:ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);pi.send(c,0,null,null,null,null,sender.toBundle());}else pi.send();return true;}catch(Throwable ignored){}try{c.startActivity(videoIntent(c,e));return true;}catch(Throwable ignored){return false;}}
    private static String soundChannelId(TimerStore.Entry e){String key=(e==null||e.soundUri==null||e.soundUri.isEmpty())?"default":e.soundUri;return "wakeguard_timer_sound_"+Integer.toHexString(key.hashCode());}
    private static void ensureDoneChannel(Context c,TimerStore.Entry e){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;String id=soundChannelId(e);NotificationChannel ch=new NotificationChannel(id,I18n.tr(c,"タイマー終了"),NotificationManager.IMPORTANCE_HIGH);ch.setDescription(I18n.tr(c,"IGNIDO Wakeのタイマー終了音"));ch.enableVibration(true);ch.setVibrationPattern(new long[]{0,500,250,500,250,900});AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();ch.setSound(chosenSound(c,e),aa);ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);nm.createNotificationChannel(ch);}

    public static void complete(Context c,long id){TimerStore.Entry e=TimerStore.find(c,id);if(e==null)return;TimerStore.Entry ring=new TimerStore.Entry(e.id,e.label,e.durationMs,0L,0L,false,e.saved,e.soundUri,e.soundName);e.running=false;e.remainingMs=0L;e.endMs=0L;if(e.saved)TimerStore.update(c,e);else TimerStore.delete(c,id);cancel(c,id);boolean started=TimerRingService.start(c,ring);if(isVideo(c,ring))launchTimerVideo(c,ring);if(started)return;ensureDoneChannel(c,ring);String label=ring.label==null||ring.label.isEmpty()?I18n.tr(c,"タイマー終了"):ring.label;String channel=soundChannelId(ring);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);PendingIntent open=openCompleted(c,ring);b.setSmallIcon(R.drawable.ic_ignido_notification).setContentTitle(label).setContentText(I18n.tr(c,isVideo(c,ring)?"タップして動画を再生":"設定した時間になりました")).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open).setAutoCancel(true);if(isVideo(c,ring))b.setFullScreenIntent(open,true);if(Build.VERSION.SDK_INT<26)b.setSound(chosenSound(c,ring));NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(notifyId(id),b.build());}
}
