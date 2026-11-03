package jp.wakeguard.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tracks each pending snooze independently so one or all can be cancelled without disabling alarms. */
public final class SnoozeScheduler {
    private static final String FILE = "ignido_snooze_v200";
    private SnoozeScheduler() {}

    private static SharedPreferences p(Context c){ return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    private static String key(long id){ return "at_" + id; }
    private static String dayKey(long id){ return "day_" + id; }

    static int requestCode(long id){ return 0x50000000 | ((int)(id ^ (id >>> 32)) & 0x0fffffff); }

    static PendingIntent pi(Context c,long id,long at,int flags){
        Intent i = new Intent(c,SnoozeReceiver.class)
                .setAction("jp.wakeguard.alarm.SNOOZE_FIRE")
                .putExtra("alarmId",id)
                .putExtra("expectedAt",at);
        return PendingIntent.getBroadcast(c,requestCode(id),i,flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c,long id,int min){
        cancel(c,id);
        long at = System.currentTimeMillis() + Math.max(1,Math.min(60,min)) * 60000L;
        long epochDay = Prefs.sessionEpochDay(c);
        p(c).edit().putLong(key(id),at).putLong(dayKey(id),epochDay).commit();
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent op = pi(c,id,at,PendingIntent.FLAG_UPDATE_CURRENT);
        try{
            if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,op);
            else am.setExact(AlarmManager.RTC_WAKEUP,at,op);
        }catch(SecurityException e){
            if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,op);
            else am.set(AlarmManager.RTC_WAKEUP,at,op);
        }
        try{ WidgetSuite.refreshAll(c); }catch(Throwable ignored){}
    }

    public static long scheduledAt(Context c,long id){
        long at=p(c).getLong(key(id),-1L);
        if(at<=System.currentTimeMillis()) return -1L;
        return at;
    }

    public static long occurrenceEpochDay(Context c,long id){ return p(c).getLong(dayKey(id),-1L); }

    public static Map<Long,Long> active(Context c){
        LinkedHashMap<Long,Long> out=new LinkedHashMap<>();
        Map<String,?> all=p(c).getAll();
        long now=System.currentTimeMillis();
        SharedPreferences.Editor cleanup=null;
        for(Map.Entry<String,?> e:all.entrySet()){
            String k=e.getKey();
            if(!k.startsWith("at_") || !(e.getValue() instanceof Number))continue;
            try{
                long id=Long.parseLong(k.substring(3));
                long at=((Number)e.getValue()).longValue();
                if(at>now && at<=now+3660000L) out.put(id,at);
                else{
                    if(cleanup==null)cleanup=p(c).edit();
                    cleanup.remove(k).remove(dayKey(id));
                }
            }catch(Throwable ignored){}
        }
        if(cleanup!=null)cleanup.apply();
        return out;
    }

    public static int activeCount(Context c){ return active(c).size(); }

    public static long nextAt(Context c){
        long best=Long.MAX_VALUE;
        for(long at:active(c).values()) best=Math.min(best,at);
        return best==Long.MAX_VALUE?-1L:best;
    }

    /** Cancel only this snooze. The base alarm stays enabled and recurring. */
    public static void cancel(Context c,long id){
        long at=p(c).getLong(key(id),-1L);
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null)try{
            PendingIntent op=pi(c,id,Math.max(0,at),PendingIntent.FLAG_NO_CREATE);
            if(op!=null){am.cancel(op);op.cancel();}
        }catch(Throwable ignored){}
        p(c).edit().remove(key(id)).remove(dayKey(id)).commit();
        try{ WidgetSuite.refreshAll(c); }catch(Throwable ignored){}
    }

    public static void cancelAll(Context c){
        for(Long id:active(c).keySet()) cancel(c,id);
    }

    /** Cancel the snooze and count its original occurrence as a successful wake-up. */
    public static void markAwake(Context c,long id){
        long day=occurrenceEpochDay(c,id);
        cancel(c,id);
        if(day>0) StreakTracker.recordWakeSuccess(c,id,day);
    }

    static boolean consumeIfExpected(Context c,long id,long at){
        long saved=p(c).getLong(key(id),-1L),now=System.currentTimeMillis();
        if(saved<=0||saved!=at||now<at-60000L||now>at+600000L){
            if(saved==at)p(c).edit().remove(key(id)).remove(dayKey(id)).commit();
            return false;
        }
        p(c).edit().remove(key(id)).remove(dayKey(id)).commit();
        return true;
    }

    public static void restoreAll(Context c){
        long now=System.currentTimeMillis();
        for(Map.Entry<Long,Long> e:active(c).entrySet()){
            long id=e.getKey(),at=e.getValue();
            if(at<=now)continue;
            try{
                AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
                if(am==null)continue;
                PendingIntent op=pi(c,id,at,PendingIntent.FLAG_UPDATE_CURRENT);
                if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,op);
                else am.setExact(AlarmManager.RTC_WAKEUP,at,op);
            }catch(Throwable ignored){}
        }
    }
}
