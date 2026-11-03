package jp.wakeguard.alarm;

import android.content.Context;
import java.time.*;
import java.util.ArrayList;

/** Tracks consecutive successful scheduled wake-ups. Test alarms never count. */
public final class StreakTracker {
    private StreakTracker() {}

    private static boolean selected(LocalDate date,int mask){
        int idx=date.getDayOfWeek().getValue()-1;
        return (mask&(1<<idx))!=0;
    }

    /** Protect every selected missed date between two successful mornings, or none if there is not enough balance. */
    private static boolean coverGap(Context c,LocalDate lastSuccess,LocalDate currentSuccess,int mask){
        if(lastSuccess==null||currentSuccess==null||currentSuccess.isBefore(lastSuccess))return false;
        ArrayList<LocalDate> missed=new ArrayList<>();
        for(LocalDate d=lastSuccess.plusDays(1);d.isBefore(currentSuccess);d=d.plusDays(1)){
            if(selected(d,mask)&&!Prefs.wasSuccessful(c,d)&&!StreakProtection.isProtected(c,d))missed.add(d);
        }
        return StreakProtection.protectDaysIfPossible(c,missed);
    }

    public static void recordWakeSuccess(Context c){
        if(Prefs.sessionIsTest(c))return;
        recordWakeSuccess(c,Prefs.activeAlarmId(c),Prefs.sessionEpochDay(c));
    }

    public static synchronized void recordWakeSuccess(Context c,long alarmId,long epochDay){
        if(epochDay<=0)return;
        LocalDate day=LocalDate.ofEpochDay(epochDay);
        long last=Prefs.lastSuccessEpochDay(c);if(last==epochDay)return;

        int mask=AlarmProfiles.get(c,alarmId).dayMask;
        boolean continues=false;
        if(last>0){
            LocalDate lastDay=LocalDate.ofEpochDay(last);
            continues=coverGap(c,lastDay,day,mask);
        }
        int next=continues?Prefs.streak(c)+1:1;

        int oldBest=Prefs.bestStreak(c);
        Prefs.streak(c,next);
        if(next>oldBest){Prefs.bestStreak(c,next);if(oldBest>0)Prefs.pendingRecordStreak(c,next);}
        Prefs.totalWakeups(c,Prefs.totalWakeups(c)+1);
        Prefs.lastSuccessEpochDay(c,epochDay);
        Prefs.addSuccessDay(c,day);
        StreakGrowth.onWakeSuccess(c);
        StreakProtection.onSuccessfulWake(c,next);
        try{WidgetSuite.refreshAll(c);}catch(Throwable ignored){}
    }

    /**
     * A missed scheduled morning can be covered automatically by a protection day.
     * The stored streak value is unchanged on a protected day; the next real success adds one.
     */
    public static int displayCurrent(Context c){
        int stored=Prefs.streak(c);long last=Prefs.lastSuccessEpochDay(c);
        if(stored<=0||last<=0)return 0;
        StreakProtection.ensure(c);

        ZoneId zone=ZoneId.systemDefault();ZonedDateTime now=ZonedDateTime.now(zone);
        AlarmStore.Entry activeAlarm=AlarmProfiles.get(c,Prefs.activeAlarmId(c));
        int mask=activeAlarm.dayMask;
        LocalDate lastDay=LocalDate.ofEpochDay(last);
        ArrayList<LocalDate> missed=new ArrayList<>();

        for(LocalDate d=lastDay.plusDays(1);!d.isAfter(now.toLocalDate());d=d.plusDays(1)){
            if(!selected(d,mask))continue;
            ZonedDateTime due=d.atTime(activeAlarm.hour,activeAlarm.minute).atZone(zone);
            if(due.isAfter(now))continue;
            long dueDay=d.toEpochDay();
            if(Prefs.wasSuccessful(c,d)||StreakProtection.isProtected(c,d))continue;
            if(Prefs.active(c)&&!Prefs.sessionIsTest(c)&&Prefs.sessionEpochDay(c)==dueDay)continue;
            missed.add(d);
        }
        if(missed.isEmpty())return stored;
        return StreakProtection.protectDaysIfPossible(c,missed)?stored:0;
    }
}
