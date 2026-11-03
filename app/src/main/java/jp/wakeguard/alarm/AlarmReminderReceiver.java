package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class AlarmReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_REMIND="jp.wakeguard.alarm.REMIND";
    private static final String CHANNEL="alarm_pre_reminders";
    @Override public void onReceive(Context c,Intent i){
        if(i==null||!ACTION_REMIND.equals(i.getAction()))return;
        long alarmId=i.getLongExtra("alarmId",AlarmScheduler.PRIMARY_ALARM_ID),alarmAt=i.getLongExtra("alarmAt",-1L);
        if(alarmAt<=System.currentTimeMillis())return;
        if(alarmId!=AlarmScheduler.PRIMARY_ALARM_ID&&AlarmStore.find(c,alarmId)==null)return;
        AlarmStore.Entry e=AlarmProfiles.get(c,alarmId);if(e==null||!e.enabled||e.preNotifyMin<=0)return;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        ensureChannel(c);
        long remaining=Math.max(1L,(alarmAt-System.currentTimeMillis()+59999L)/60000L);
        String time;try{boolean h24=android.text.format.DateFormat.is24HourFormat(c);time=Instant.ofEpochMilli(alarmAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(h24?"HH:mm":"h:mm",I18n.locale(c)));}catch(Throwable t){time="";}
        String title=remaining+I18n.tr(c,"分後にアラーム")+(time.isEmpty()?"":"  "+time);
        String label=AlarmProfiles.label(c,alarmId);
        Intent open=new Intent(c,AlarmEditorActivity.class).putExtra("alarmId",alarmId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,notificationId(alarmId,alarmAt),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_ignido_notification).setContentTitle(title).setContentText(label).setContentIntent(pi).setAutoCancel(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm!=null)nm.notify(notificationId(alarmId,alarmAt),b.build());
    }
    private static int notificationId(long id,long at){return 53000+(int)Math.floorMod(id*37L+(at/60000L),100000000L);}
    private static void ensureChannel(Context c){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;NotificationChannel ch=new NotificationChannel(CHANNEL,I18n.tr(c,"アラーム事前通知"),NotificationManager.IMPORTANCE_LOW);ch.setDescription(I18n.tr(c,"アラームの前にサイレント通知で知らせます"));ch.setSound(null,null);ch.enableVibration(false);nm.createNotificationChannel(ch);}
}
