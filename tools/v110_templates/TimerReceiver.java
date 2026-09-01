package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class TimerReceiver extends BroadcastReceiver {
    public static final String ACTION_TIMER = "jp.wakeguard.alarm.TIMER_DONE";
    public static final String EXTRA_TIMER_ID = "timerId";
    private static final String CHANNEL = "wakeguard_timer_v2";
    private static final int BASE_REQUEST = 82000;
    private static final int BASE_NOTIFY = 92000;

    @Override public void onReceive(Context context, Intent intent) {
        long id=intent==null?-1L:intent.getLongExtra(EXTRA_TIMER_ID,-1L);
        TimerStore.Entry e=id>=1000?TimerStore.find(context,id):TimerStore.firstRunning(context);
        if(e==null||!e.running)return;
        long remaining=e.remaining();
        if(remaining>1500L){ schedule(context,e.id,e.endMs); return; }
        complete(context,e.id);
    }

    private static int requestCode(long id){ return BASE_REQUEST+(int)Math.floorMod(id,1000000L); }
    private static int notifyId(long id){ return BASE_NOTIFY+(int)Math.floorMod(id,1000000L); }

    static PendingIntent pending(Context c,long id,int flags){
        Intent i=new Intent(c,TimerReceiver.class).setAction(ACTION_TIMER).putExtra(EXTRA_TIMER_ID,id);
        return PendingIntent.getBroadcast(c,requestCode(id),i,flags|PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c,long id,long when){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); if(am==null)return;
        PendingIntent pi=pending(c,id,PendingIntent.FLAG_UPDATE_CURRENT);
        try{
            if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi); else am.setExact(AlarmManager.RTC_WAKEUP,when,pi);
        }catch(SecurityException ex){
            if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi); else am.set(AlarmManager.RTC_WAKEUP,when,pi);
        }
    }

    public static void cancel(Context c,long id){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); if(am==null)return;
        try{PendingIntent pi=pending(c,id,PendingIntent.FLAG_NO_CREATE);if(pi!=null)am.cancel(pi);}catch(Throwable ignored){}
    }

    public static void ensureChannel(Context c){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null)return;
        NotificationChannel ch=new NotificationChannel(CHANNEL,"タイマー",NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("WakeGuardのタイマー終了通知"); ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0,500,250,500,250,900});
        Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        ch.setSound(sound,aa); ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); nm.createNotificationChannel(ch);
    }

    public static void complete(Context c,long id){
        TimerStore.Entry e=TimerStore.find(c,id); if(e==null)return;
        e.running=false; e.remainingMs=0L; e.endMs=0L; TimerStore.update(c,e); cancel(c,id); ensureChannel(c);
        Intent open=new Intent(c,ClockActivity.class).putExtra("mode","timer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
        PendingIntent content=PendingIntent.getActivity(c,requestCode(id)+1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String label=e.label==null||e.label.isEmpty()?"タイマー終了":e.label;
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        Notification n=b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(label).setContentText("設定した時間になりました")
                .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(content).setAutoCancel(true).build();
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(notifyId(id),n);
    }
}
