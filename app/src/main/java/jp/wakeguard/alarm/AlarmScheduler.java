package jp.wakeguard.alarm;

import android.app.AlarmManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import java.time.*;
import java.util.*;

public final class AlarmScheduler {
    private AlarmScheduler() {}
    private static final int RECOVERY_REQUEST = 299999;
    public static final long PRIMARY_ALARM_ID = 1L;
    public static final String EXTRA_EXPECTED_AT = "expectedAt";
    private static final long FIRE_GRACE_MS = 10L * 60L * 1000L;

    private static int code(long alarmId, LocalDate d, int kind) {
        long x = alarmId * 1103515245L + d.toEpochDay() * 2654435761L + kind * 1000003L;
        return (int)(Math.abs(x) % 2000000000L);
    }

    private static Uri data(long alarmId, LocalDate d, String kind) {
        return Uri.parse("wakeguard://alarm/" + alarmId + "/" + d.toEpochDay() + "/" + kind);
    }

    private static PendingIntent activityPi(Context c, int requestCode, Intent i, int flags) {
        int f = flags | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions ao = ActivityOptions.makeBasic();
            ao.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            return PendingIntent.getActivity(c, requestCode, i, f, ao.toBundle());
        }
        return PendingIntent.getActivity(c, requestCode, i, f);
    }

    private static PendingIntent scheduledActivityPi(Context c, long alarmId, LocalDate date, long expectedAt, int flags) {
        Intent i = new Intent(c, AlarmActivity.class)
                .setAction(AlarmActivity.ACTION_SCHEDULED_FIRE)
                .setData(data(alarmId,date,"fire"))
                .putExtra(AlarmService.EXTRA_EPOCH_DAY, date.toEpochDay())
                .putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_EXPECTED_AT, expectedAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return activityPi(c, code(alarmId,date,1), i, flags);
    }

    private static PendingIntent showAlarmUiPi(Context c, long alarmId, LocalDate date, long expectedAt, int flags) {
        Intent i = new Intent(c, AlarmActivity.class)
                .setAction("jp.wakeguard.alarm.SHOW_RUNNING_ALARM")
                .setData(data(alarmId,date,"show"))
                .putExtra(AlarmService.EXTRA_EPOCH_DAY, date.toEpochDay())
                .putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_EXPECTED_AT, expectedAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        return activityPi(c, code(alarmId,date,2), i, flags);
    }

    private static PendingIntent backupFirePi(Context c, long alarmId, LocalDate date, long expectedAt, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(AlarmReceiver.ACTION_BACKUP_FIRE)
                .setData(data(alarmId,date,"backup"))
                .putExtra("epochDay", date.toEpochDay())
                .putExtra("alarmId", alarmId)
                .putExtra(EXTRA_EXPECTED_AT, expectedAt);
        return PendingIntent.getBroadcast(c, code(alarmId,date,3), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent reminderPi(Context c,long alarmId,LocalDate date,long alarmAt,int flags){
        Intent i=new Intent(c,AlarmReminderReceiver.class).setAction(AlarmReminderReceiver.ACTION_REMIND).setData(data(alarmId,date,"remind")).putExtra("alarmId",alarmId).putExtra("alarmAt",alarmAt);
        return PendingIntent.getBroadcast(c,code(alarmId,date,5),i,flags|PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean selected(LocalDate date, int mask) {
        int idx=date.getDayOfWeek().getValue()-1;
        return (mask & (1<<idx))!=0;
    }

    public static boolean canScheduleExact(Context c) {
        AlarmManager am=c.getSystemService(AlarmManager.class);
        return am!=null && (Build.VERSION.SDK_INT<31 || am.canScheduleExactAlarms());
    }

    private static void cancelPair(Context c, AlarmManager am, long id, LocalDate d) {
        PendingIntent fire=scheduledActivityPi(c,id,d,0L,PendingIntent.FLAG_NO_CREATE); if(fire!=null)am.cancel(fire);
        PendingIntent show=showAlarmUiPi(c,id,d,0L,PendingIntent.FLAG_NO_CREATE); if(show!=null)am.cancel(show);
        PendingIntent backup=backupFirePi(c,id,d,0L,PendingIntent.FLAG_NO_CREATE); if(backup!=null)am.cancel(backup);
        PendingIntent reminder=reminderPi(c,id,d,0L,PendingIntent.FLAG_NO_CREATE); if(reminder!=null)am.cancel(reminder);
    }

    /** Cancel the v0.6/v0.7 primary PendingIntents that did not carry alarm ids. */
    private static void cancelLegacyPrimary(Context c, AlarmManager am, LocalDate today) {
        for(int i=-2;i<=120;i++) {
            LocalDate d=today.plusDays(i);
            int old=300000+(int)(d.toEpochDay()%100000);
            Intent fireI=new Intent(c,AlarmActivity.class).setAction(AlarmActivity.ACTION_SCHEDULED_FIRE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent fire=activityPi(c,old,fireI,PendingIntent.FLAG_NO_CREATE); if(fire!=null)am.cancel(fire);
            Intent showI=new Intent(c,AlarmActivity.class).setAction("jp.wakeguard.alarm.SHOW_RUNNING_ALARM")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            PendingIntent show=activityPi(c,old+400000,showI,PendingIntent.FLAG_NO_CREATE); if(show!=null)am.cancel(show);
            Intent backupI=new Intent(c,AlarmReceiver.class).setAction(AlarmReceiver.ACTION_BACKUP_FIRE);
            PendingIntent backup=PendingIntent.getBroadcast(c,old+200000,backupI,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE); if(backup!=null)am.cancel(backup);
        }
    }

    public static void cancelExtraAlarm(Context c,long alarmId) {
        try {
            AlarmManager am=c.getSystemService(AlarmManager.class); if(am==null)return;
            ZoneId zone=ZoneId.systemDefault(); LocalDate today=LocalDate.now(zone);
            for(int i=-2;i<=120;i++) cancelPair(c,am,alarmId,today.plusDays(i));
        } catch(Throwable ignored) {}
    }

    private static int scheduleSpec(Context c, AlarmManager am, long id, int hour, int minute, int mask, boolean enabled, boolean primary, int preNotifyMin, long fixedAt, ZoneId zone, LocalDate today, Instant now) {
        if(!enabled)return 0;
        if(fixedAt>0L&&mask==0){
            Instant target=Instant.ofEpochMilli(fixedAt);
            if(!target.isAfter(now.plusSeconds(2)))return 0;
            ZonedDateTime when=target.atZone(zone);LocalDate d=when.toLocalDate();long expectedAt=fixedAt;
            PendingIntent fire=scheduledActivityPi(c,id,d,expectedAt,PendingIntent.FLAG_UPDATE_CURRENT);
            Intent open=new Intent(c, primary?MainActivity.class:MultiAlarmActivity.class);open.setData(data(id,d,"open"));
            PendingIntent showPi=activityPi(c,code(id,d,4),open,PendingIntent.FLAG_UPDATE_CURRENT);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(expectedAt,showPi),fire);
            PendingIntent show=showAlarmUiPi(c,id,d,expectedAt,PendingIntent.FLAG_UPDATE_CURRENT);
            if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,expectedAt+900L,show); else am.setExact(AlarmManager.RTC_WAKEUP,expectedAt+900L,show);
            PendingIntent backup=backupFirePi(c,id,d,expectedAt,PendingIntent.FLAG_UPDATE_CURRENT);
            if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,expectedAt+1500L,backup); else am.setExact(AlarmManager.RTC_WAKEUP,expectedAt+1500L,backup);
            if(preNotifyMin>0){long remindAt=Math.max(System.currentTimeMillis()+1500L,expectedAt-preNotifyMin*60000L);if(remindAt<expectedAt){PendingIntent reminder=reminderPi(c,id,d,expectedAt,PendingIntent.FLAG_UPDATE_CURRENT);if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,remindAt,reminder);else am.setExact(AlarmManager.RTC_WAKEUP,remindAt,reminder);}}
            return 1;
        }
        int count=0;
        for(int i=0;i<=120 && count<14;i++) {
            LocalDate d=today.plusDays(i);
            if(mask!=0 && !selected(d,mask))continue;
            ZonedDateTime when=d.atTime(hour,minute).atZone(zone);
            if(!when.toInstant().isAfter(now.plusSeconds(2)))continue;

            long expectedAt=when.toInstant().toEpochMilli();
            PendingIntent fire=scheduledActivityPi(c,id,d,expectedAt,PendingIntent.FLAG_UPDATE_CURRENT);
            Intent open=new Intent(c, primary?MainActivity.class:MultiAlarmActivity.class);
            open.setData(data(id,d,"open"));
            PendingIntent showPi=activityPi(c,code(id,d,4),open,PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager.AlarmClockInfo info=new AlarmManager.AlarmClockInfo(when.toInstant().toEpochMilli(),showPi);
            am.setAlarmClock(info,fire);

            PendingIntent show=showAlarmUiPi(c,id,d,expectedAt,PendingIntent.FLAG_UPDATE_CURRENT);
            long showAt=when.toInstant().toEpochMilli()+900L;
            if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,showAt,show); else am.setExact(AlarmManager.RTC_WAKEUP,showAt,show);

            PendingIntent backup=backupFirePi(c,id,d,expectedAt,PendingIntent.FLAG_UPDATE_CURRENT);
            long backupAt=when.toInstant().toEpochMilli()+1500L;
            if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,backupAt,backup); else am.setExact(AlarmManager.RTC_WAKEUP,backupAt,backup);
            if(preNotifyMin>0){long alarmAt=when.toInstant().toEpochMilli();long remindAt=Math.max(System.currentTimeMillis()+1500L,alarmAt-preNotifyMin*60000L);if(remindAt<alarmAt){PendingIntent reminder=reminderPi(c,id,d,alarmAt,PendingIntent.FLAG_UPDATE_CURRENT);if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,remindAt,reminder);else am.setExact(AlarmManager.RTC_WAKEUP,remindAt,reminder);}}
            count++;
            if(mask==0)break; // additional alarm with no selected weekday = one-shot
        }
        return count;
    }

    private static long configuredOccurrenceAt(Context c,long alarmId,LocalDate date) {
        try {
            int hour,minute,mask; boolean enabled;
            if(alarmId==PRIMARY_ALARM_ID){
                if(!Prefs.primaryConfigured(c))return -1L;
                hour=Prefs.hour(c);minute=Prefs.minute(c);mask=Prefs.dayMask(c);enabled=Prefs.enabled(c);
            }else{
                AlarmStore.Entry e=AlarmStore.find(c,alarmId);if(e==null)return -1L;
                hour=e.hour;minute=e.minute;mask=e.dayMask;enabled=e.enabled;
                if(enabled&&mask==0&&e.fixedAt>0L){
                    LocalDate fixedDate=Instant.ofEpochMilli(e.fixedAt).atZone(ZoneId.systemDefault()).toLocalDate();
                    return fixedDate.equals(date)?e.fixedAt:-1L;
                }
            }
            if(!enabled)return -1L;
            if(mask!=0&&!selected(date,mask))return -1L;
            return date.atTime(hour,minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }catch(Throwable ignored){return -1L;}
    }

    /** Reject stale/orphan AlarmManager deliveries before they can start a new alarm session. */
    public static boolean isExpectedFire(Context c,long alarmId,long epochDay,long expectedAt) {
        try {
            ZoneId zone=ZoneId.systemDefault();
            LocalDate date=epochDay>=0?LocalDate.ofEpochDay(epochDay):LocalDate.now(zone);
            long configuredAt=configuredOccurrenceAt(c,alarmId,date);
            if(configuredAt<=0L)return false;
            long now=System.currentTimeMillis();
            if(Math.abs(now-configuredAt)>FIRE_GRACE_MS)return false;
            if(expectedAt>0L){
                if(Math.abs(expectedAt-configuredAt)>3000L)return false;
                if(Math.abs(now-expectedAt)>FIRE_GRACE_MS)return false;
            }
            return true;
        }catch(Throwable ignored){return false;}
    }

    public static void reschedule(Context c) {
        try {
            AlarmManager am=c.getSystemService(AlarmManager.class); if(am==null||!canScheduleExact(c))return;
            ZoneId zone=ZoneId.systemDefault(); LocalDate today=LocalDate.now(zone); Instant now=Instant.now();
            cancelLegacyPrimary(c,am,today);
            // New identities can be cancelled deterministically. This also clears old times after editing.
            for(int i=-2;i<=120;i++) cancelPair(c,am,PRIMARY_ALARM_ID,today.plusDays(i));
            for(long id:AlarmStore.knownIds(c)) for(int i=-2;i<=120;i++) cancelPair(c,am,id,today.plusDays(i));

            if(Prefs.primaryConfigured(c)) scheduleSpec(c,am,PRIMARY_ALARM_ID,Prefs.hour(c),Prefs.minute(c),Prefs.dayMask(c),Prefs.enabled(c),true,Prefs.preNotifyMin(c),0L,zone,today,now);
            for(AlarmStore.Entry e:AlarmStore.all(c))
                scheduleSpec(c,am,e.id,e.hour,e.minute,e.dayMask,e.enabled,false,e.preNotifyMin,e.fixedAt,zone,today,now);
        } catch(Throwable t) {
            try{Prefs.lastAlarmError(c,"AlarmScheduler: "+t.getClass().getSimpleName()+(t.getMessage()==null?"":": "+t.getMessage()));}catch(Throwable ignored){}
        }
        try{WidgetSuite.refreshAll(c);}catch(Throwable ignored){}
    }

    public static void scheduleRecoveryIfActive(Context c) {
        try {
            if(!Prefs.active(c)||!canScheduleExact(c))return;
            AlarmManager am=c.getSystemService(AlarmManager.class); if(am==null)return;
            Intent i=new Intent(c,AlarmReceiver.class).setAction(AlarmReceiver.ACTION_RECOVER);
            PendingIntent pi=PendingIntent.getBroadcast(c,RECOVERY_REQUEST,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            long at=System.currentTimeMillis()+3000L;
            Intent show=new Intent(c,MainActivity.class);
            PendingIntent showPi=activityPi(c,RECOVERY_REQUEST+1,show,PendingIntent.FLAG_UPDATE_CURRENT);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at,showPi),pi);
        } catch(Throwable t) { try{Prefs.lastAlarmError(c,"RecoveryAlarm: "+t.getClass().getSimpleName());}catch(Throwable ignored){} }
    }

    private static long nextFor(int h,int m,int mask,boolean enabled,long fixedAt,ZoneId zone,LocalDate today,Instant now) {
        if(!enabled)return -1L;
        if(mask==0&&fixedAt>0L)return fixedAt>now.toEpochMilli()?fixedAt:-1L;
        for(int i=0;i<=120;i++) {
            LocalDate d=today.plusDays(i); if(mask!=0&&!selected(d,mask))continue;
            Instant x=d.atTime(h,m).atZone(zone).toInstant(); if(x.isAfter(now))return x.toEpochMilli();
            if(mask==0 && i>0)break;
        }
        return -1L;
    }

    public static long nextTriggerMillis(Context c, AlarmStore.Entry e) {
        if(e==null)return -1L; ZoneId zone=ZoneId.systemDefault(); Instant now=Instant.now();
        // Display-only calculation: OFF alarms still show when their configured time next arrives.
        return nextFor(e.hour,e.minute,e.dayMask,true,e.fixedAt,zone,LocalDate.now(zone),now);
    }

    public static long nextTriggerMillis(Context c) {
        ZoneId zone=ZoneId.systemDefault(); LocalDate today=LocalDate.now(zone); Instant now=Instant.now(); long best=-1;
        if(Prefs.primaryConfigured(c)){long p=nextFor(Prefs.hour(c),Prefs.minute(c),Prefs.dayMask(c),Prefs.enabled(c),0L,zone,today,now); if(p>0)best=p;}
        for(AlarmStore.Entry e:AlarmStore.all(c)) {
            long x=nextFor(e.hour,e.minute,e.dayMask,e.enabled,e.fixedAt,zone,today,now); if(x>0&&(best<0||x<best))best=x;
        }
        return best;
    }
}
