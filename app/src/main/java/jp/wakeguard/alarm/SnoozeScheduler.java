package jp.wakeguard.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tracks each pending snooze independently so one or all can be cancelled without disabling alarms. */
public final class SnoozeScheduler {
    private static final String FILE = "ignido_snooze_v200";
    private static final long DELIVERY_GRACE_MS = 10 * 60_000L;
    private static final long EARLY_DELIVERY_GRACE_MS = 60_000L;
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

    public static synchronized void schedule(Context c,long id,int min){
        long at = System.currentTimeMillis() + Math.max(1,Math.min(60,min)) * 60_000L;
        if (!register(c, id, at, at)) return;
        p(c).edit().putLong(key(id),at).putLong(dayKey(id),Prefs.sessionEpochDay(c)).commit();
        refreshWidgets(c);
    }

    private static boolean register(Context c, long id, long expectedAt, long triggerAt) {
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        if (manager == null) return false;
        PendingIntent operation = pi(c,id,expectedAt,PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        } catch (SecurityException denied) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        }
        return true;
    }

    public static long scheduledAt(Context c,long id){
        long at=p(c).getLong(key(id),-1L);
        return at > System.currentTimeMillis() ? at : -1L;
    }

    public static long occurrenceEpochDay(Context c,long id){ return p(c).getLong(dayKey(id),-1L); }

    public static synchronized Map<Long,Long> active(Context c){
        long now = System.currentTimeMillis();
        Map<Long,Long> pending = savedSnoozes(c, now);
        pending.values().removeIf(at -> at <= now);
        return pending;
    }

    // A due snooze may still be waiting on an AlarmManager broadcast, so upcoming
    // UI must not discard it as soon as the countdown reaches zero.
    private static Map<Long,Long> savedSnoozes(Context c, long now){
        LinkedHashMap<Long,Long> out = new LinkedHashMap<>();
        SharedPreferences prefs = p(c);
        SharedPreferences.Editor cleanup = null;
        for (Map.Entry<String,?> entry : prefs.getAll().entrySet()) {
            String k = entry.getKey();
            if (!k.startsWith("at_") || !(entry.getValue() instanceof Number)) continue;
            long id;
            try { id = Long.parseLong(k.substring(3)); }
            catch (NumberFormatException invalidId) { continue; }
            long at = ((Number)entry.getValue()).longValue();
            if (at > 0 && at >= now - DELIVERY_GRACE_MS) {
                out.put(id,at);
            } else {
                if (cleanup == null) cleanup = prefs.edit();
                cleanup.remove(k).remove(dayKey(id));
            }
        }
        if (cleanup != null) cleanup.apply();
        return out;
    }

    public static int activeCount(Context c){ return active(c).size(); }

    public static long nextAt(Context c){
        long best=Long.MAX_VALUE;
        for(long at:active(c).values()) best=Math.min(best,at);
        return best==Long.MAX_VALUE?-1L:best;
    }

    /** Cancel only this snooze. The base alarm stays enabled and recurring. */
    public static synchronized void cancel(Context c,long id){
        long at=p(c).getLong(key(id),-1L);
        AlarmManager am=c.getSystemService(AlarmManager.class);
        if(am!=null)try{
            PendingIntent op=pi(c,id,Math.max(0,at),PendingIntent.FLAG_NO_CREATE);
            if(op!=null){am.cancel(op);op.cancel();}
        }catch(Throwable ignored){}
        p(c).edit().remove(key(id)).remove(dayKey(id)).commit();
        refreshWidgets(c);
    }

    public static synchronized void cancelAll(Context c){
        for(Long id:savedSnoozes(c,System.currentTimeMillis()).keySet()) cancel(c,id);
    }

    /** Cancel the snooze and count its original occurrence as a successful wake-up. */
    public static synchronized void markAwake(Context c,long id){
        long day=occurrenceEpochDay(c,id);
        cancel(c,id);
        if(day>0) StreakTracker.recordWakeSuccess(c,id,day);
    }

    static synchronized boolean isExpectedDelivery(Context c, long id, long at) {
        long now = System.currentTimeMillis();
        return at > 0 && p(c).getLong(key(id),-1L) == at
                && now >= at - EARLY_DELIVERY_GRACE_MS && now <= at + DELIVERY_GRACE_MS;
    }

    static synchronized void clearDelivered(Context c, long id, long at) {
        SharedPreferences prefs = p(c);
        if (prefs.getLong(key(id),-1L) != at) return;
        prefs.edit().remove(key(id)).remove(dayKey(id)).commit();
        refreshWidgets(c);
    }

    public static synchronized void restoreAll(Context c){
        long now = System.currentTimeMillis();
        for (Map.Entry<Long,Long> entry : savedSnoozes(c,now).entrySet()) {
            long id = entry.getKey(), at = entry.getValue();
            try {
                register(c,id,at,Math.max(at,now + 1000L));
            } catch (RuntimeException failure) {
                Prefs.lastAlarmError(c,"SnoozeRestore: " + failure.getClass().getSimpleName());
            }
        }
    }

    private static void refreshWidgets(Context c) {
        try { WidgetSuite.refreshAll(c); } catch(Throwable ignored) {}
    }
}
