package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Iterator;

/** Persistent de-duplication for a concrete scheduled alarm occurrence. */
public final class AlarmOccurrenceGuard {
    private static final String FILE="wake_guard_occurrence_guard_v1";
    private static final String KEY="delivered";
    private static final long RETAIN_MS=3L*24L*60L*60L*1000L;
    private AlarmOccurrenceGuard() {}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    private static String key(long alarmId,long expectedAt,long epochDay){
        long minute=expectedAt>0L?expectedAt/60000L:epochDay;
        return alarmId+":"+minute;
    }
    private static JSONObject load(Context c){try{return new JSONObject(p(c).getString(KEY,"{}"));}catch(Throwable t){return new JSONObject();}}
    private static void prune(JSONObject o,long now){
        try{
            Iterator<String> it=o.keys();
            java.util.ArrayList<String> stale=new java.util.ArrayList<>();
            while(it.hasNext()){String k=it.next();long ts=o.optLong(k,0L);if(ts<=0L||now-ts>RETAIN_MS)stale.add(k);}
            for(String k:stale)o.remove(k);
        }catch(Throwable ignored){}
    }
    public static synchronized boolean wasDelivered(Context c,long alarmId,long expectedAt,long epochDay){
        long now=System.currentTimeMillis();JSONObject o=load(c);prune(o,now);boolean hit=o.has(key(alarmId,expectedAt,epochDay));
        try{p(c).edit().putString(KEY,o.toString()).commit();}catch(Throwable ignored){}
        return hit;
    }
    public static synchronized void markDelivered(Context c,long alarmId,long expectedAt,long epochDay){
        long now=System.currentTimeMillis();JSONObject o=load(c);prune(o,now);try{o.put(key(alarmId,expectedAt,epochDay),now);p(c).edit().putString(KEY,o.toString()).commit();}catch(Throwable ignored){}
    }
}
