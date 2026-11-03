package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

public final class StopwatchNotification {
    private static final String CHANNEL="stopwatch_running";
    private static final int ID=42017;
    private StopwatchNotification(){}

    private static boolean allowed(Context c){
        return Build.VERSION.SDK_INT<33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }
    private static void ensureChannel(Context c){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;
        NotificationChannel ch=new NotificationChannel(CHANNEL,I18n.tr(c,"ストップウォッチ"),NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(I18n.tr(c,"ストップウォッチの計測中時間を表示します"));
        ch.setSound(null,null);ch.enableVibration(false);ch.setShowBadge(false);nm.createNotificationChannel(ch);
    }
    public static void show(Context c,long elapsedMs){
        if(!allowed(c))return;ensureChannel(c);
        Intent open=new Intent(c,ClockActivity.class).setAction("jp.wakeguard.alarm.OPEN_STOPWATCH").putExtra("mode","stopwatch").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        long startedAt=System.currentTimeMillis()-Math.max(0L,elapsedMs);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_ignido_notification).setContentTitle(I18n.tr(c,"ストップウォッチ")).setContentText(I18n.tr(c,"計測中")).setContentIntent(pi)
                .setCategory(Notification.CATEGORY_STATUS).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(true).setWhen(startedAt).setUsesChronometer(true).setVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm!=null)nm.notify(ID,b.build());
    }
    public static void cancel(Context c){NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm!=null)nm.cancel(ID);}
}
