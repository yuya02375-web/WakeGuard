package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class TimerReceiver extends BroadcastReceiver {
    public static final String ACTION_TIMER="jp.wakeguard.alarm.TIMER_DONE", EXTRA_TIMER_ID="timerId";
    private static final String RUN_CHANNEL="wakeguard_timer_running_v1";
    private static final int BASE_REQUEST=82000,BASE_NOTIFY=92000,BASE_RUNNING=102000;

    @Override public void onReceive(Context context,Intent intent){long id=intent==null?-1L:intent.getLongExtra(EXTRA_TIMER_ID,-1L);TimerStore.Entry e=id>=1000?TimerStore.find(context,id):TimerStore.firstRunning(context);if(e==null||!e.running)return;long remaining=e.remaining();if(remaining>1500L){schedule(context,e.id,e.endMs);return;}complete(context,e.id);}
    private static int requestCode(long id){return BASE_REQUEST+(int)Math.floorMod(id,1000000L);} private static int notifyId(long id){return BASE_NOTIFY+(int)Math.floorMod(id,1000000L);} private static int runningId(long id){return BASE_RUNNING+(int)Math.floorMod(id,1000000L);}
    static PendingIntent pending(Context c,long id,int flags){Intent i=new Intent(c,TimerReceiver.class).setAction(ACTION_TIMER).putExtra(EXTRA_TIMER_ID,id);return PendingIntent.getBroadcast(c,requestCode(id),i,flags|PendingIntent.FLAG_IMMUTABLE);}
    private static PendingIntent openTimer(Context c,long id){Intent open=new Intent(c,ClockActivity.class).putExtra("mode","timer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);return PendingIntent.getActivity(c,requestCode(id)+1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}

    public static void schedule(Context c,long id,long when){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;PendingIntent pi=pending(c,id,PendingIntent.FLAG_UPDATE_CURRENT);try{if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.setExact(AlarmManager.RTC_WAKEUP,when,pi);}catch(SecurityException ex){if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.set(AlarmManager.RTC_WAKEUP,when,pi);}TimerStore.Entry e=TimerStore.find(c,id);if(e!=null&&e.running)showRunning(c,e);}
    public static void cancel(Context c,long id){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null)try{PendingIntent pi=pending(c,id,PendingIntent.FLAG_NO_CREATE);if(pi!=null)am.cancel(pi);}catch(Throwable ignored){}hideRunning(c,id);}

    private static void ensureRunningChannel(Context c){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;NotificationChannel ch=new NotificationChannel(RUN_CHANNEL,"実行中のタイマー",NotificationManager.IMPORTANCE_LOW);ch.setDescription("タイマーの残り時間を常に表示します");ch.setSound(null,null);ch.enableVibration(false);ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);nm.createNotificationChannel(ch);}
    public static void showRunning(Context c,TimerStore.Entry e){if(e==null||!e.running)return;ensureRunningChannel(c);String label=e.label==null||e.label.isEmpty()?"タイマー":e.label;Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,RUN_CHANNEL):new Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(label).setContentText("タイマー実行中").setCategory(Notification.CATEGORY_PROGRESS).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(openTimer(c,e.id)).setOnlyAlertOnce(true).setOngoing(true).setWhen(e.endMs).setUsesChronometer(true);if(Build.VERSION.SDK_INT>=24)b.setChronometerCountDown(true);NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(runningId(e.id),b.build());}
    public static void hideRunning(Context c,long id){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.cancel(runningId(id));}

    private static Uri chosenSound(TimerStore.Entry e){if(e!=null&&e.soundUri!=null&&!e.soundUri.isEmpty())try{return Uri.parse(e.soundUri);}catch(Throwable ignored){}return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);}
    private static String soundChannelId(TimerStore.Entry e){String key=(e==null||e.soundUri==null||e.soundUri.isEmpty())?"default":e.soundUri;return "wakeguard_timer_sound_"+Integer.toHexString(key.hashCode());}
    private static void ensureDoneChannel(Context c,TimerStore.Entry e){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;String id=soundChannelId(e);NotificationChannel ch=new NotificationChannel(id,"タイマー終了",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("WakeGuardのタイマー終了音");ch.enableVibration(true);ch.setVibrationPattern(new long[]{0,500,250,500,250,900});AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();ch.setSound(chosenSound(e),aa);ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);nm.createNotificationChannel(ch);}

    public static void complete(Context c,long id){TimerStore.Entry e=TimerStore.find(c,id);if(e==null)return;e.running=false;e.remainingMs=0L;e.endMs=0L;if(e.saved)TimerStore.update(c,e);else TimerStore.delete(c,id);cancel(c,id);ensureDoneChannel(c,e);String label=e.label==null||e.label.isEmpty()?"タイマー終了":e.label;String channel=soundChannelId(e);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(label).setContentText("設定した時間になりました").setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(openTimer(c,id)).setAutoCancel(true);if(Build.VERSION.SDK_INT<26)b.setSound(chosenSound(e));NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(notifyId(id),b.build());}
}
