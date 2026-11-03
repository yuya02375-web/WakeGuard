package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Persistent FIFO for scheduled alarms that fire while another alarm is already ringing. */
public final class PendingAlarmQueue {
    private static final String FILE="wake_guard_pending_alarms";
    private static final String KEY="queue";
    private static final long MAX_AGE_MS=12L*60L*60L*1000L;
    private PendingAlarmQueue() {}

    public static final class Item {
        public final long alarmId, epochDay, expectedAt, queuedAtMs;
        Item(long alarmId,long epochDay,long expectedAt,long queuedAtMs){this.alarmId=alarmId;this.epochDay=epochDay;this.expectedAt=expectedAt;this.queuedAtMs=queuedAtMs;}
    }

    private static SharedPreferences p(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    private static JSONArray load(Context c){try{return new JSONArray(p(c).getString(KEY,"[]"));}catch(Throwable t){return new JSONArray();}}
    private static void save(Context c,JSONArray a){p(c).edit().putString(KEY,a.toString()).commit();}
    private static boolean fresh(long ts){return ts<=0L || System.currentTimeMillis()-ts<=MAX_AGE_MS;}

    public static synchronized boolean enqueue(Context c,long alarmId,long epochDay,long expectedAt){
        JSONArray old=load(c), out=new JSONArray(); boolean exists=false;
        for(int i=0;i<old.length();i++){
            JSONObject o=old.optJSONObject(i); if(o==null)continue;
            long ts=o.optLong("queuedAtMs",0L); if(!fresh(ts))continue;
            long id=o.optLong("alarmId",-1L), day=o.optLong("epochDay",-1L), at=o.optLong("expectedAt",-1L);
            if(id==alarmId && day==epochDay && (expectedAt<=0L||at==expectedAt))exists=true;
            out.put(o);
        }
        if(!exists){
            try{JSONObject o=new JSONObject();o.put("alarmId",alarmId);o.put("epochDay",epochDay);o.put("expectedAt",expectedAt);o.put("queuedAtMs",System.currentTimeMillis());out.put(o);}catch(Throwable ignored){}
        }
        save(c,out); return !exists;
    }

    public static synchronized Item poll(Context c){
        JSONArray old=load(c), out=new JSONArray(); Item first=null;
        for(int i=0;i<old.length();i++){
            JSONObject o=old.optJSONObject(i); if(o==null)continue;
            long ts=o.optLong("queuedAtMs",0L); if(!fresh(ts))continue;
            if(first==null){first=new Item(o.optLong("alarmId",AlarmScheduler.PRIMARY_ALARM_ID),o.optLong("epochDay",-1L),o.optLong("expectedAt",-1L),ts);continue;}
            out.put(o);
        }
        save(c,out); return first;
    }

    public static synchronized int size(Context c){
        JSONArray old=load(c), out=new JSONArray(); int n=0;
        for(int i=0;i<old.length();i++){JSONObject o=old.optJSONObject(i);if(o==null)continue;long ts=o.optLong("queuedAtMs",0L);if(!fresh(ts))continue;out.put(o);n++;}
        if(out.length()!=old.length())save(c,out); return n;
    }

    public static synchronized void clear(Context c){save(c,new JSONArray());}
}
