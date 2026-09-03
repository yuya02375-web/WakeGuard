package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class TimerStore {
    private static final String FILE="wakeguard_timers_v2";
    private static final String KEY_TIMERS="timers";
    private static final String KEY_NEXT_ID="next_id";
    private static final String KEY_MIGRATED="migrated_legacy";
    private TimerStore() {}

    public static final class Entry {
        public long id, durationMs, remainingMs, endMs;
        public boolean running;
        public String label, soundUri, soundName;
        public Entry(long id,String label,long durationMs,long remainingMs,long endMs,boolean running){
            this(id,label,durationMs,remainingMs,endMs,running,"","");
        }
        public Entry(long id,String label,long durationMs,long remainingMs,long endMs,boolean running,String soundUri,String soundName){
            this.id=id; this.label=label==null?"":label.trim();
            this.durationMs=Math.max(1000L,durationMs);
            this.remainingMs=Math.max(0L,remainingMs);
            this.endMs=Math.max(0L,endMs); this.running=running;
            this.soundUri=soundUri==null?"":soundUri;
            this.soundName=soundName==null?"":soundName;
        }
        public long remaining(){ return running ? Math.max(0L,endMs-System.currentTimeMillis()) : Math.max(0L,remainingMs); }
        public boolean finished(){ return !running && remainingMs<=0; }
    }

    private static SharedPreferences p(Context c){ return c.getSharedPreferences(FILE,Context.MODE_PRIVATE); }

    public static synchronized List<Entry> all(Context c){
        migrateLegacy(c);
        ArrayList<Entry> out=new ArrayList<>();
        String raw=p(c).getString(KEY_TIMERS,"[]");
        try{
            JSONArray a=new JSONArray(raw==null?"[]":raw);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i); if(o==null)continue;
                long id=o.optLong("id",-1L); if(id<1000)continue;
                Entry e=new Entry(id,o.optString("label",""),o.optLong("duration",60000L),o.optLong("remaining",0L),o.optLong("end",0L),o.optBoolean("running",false),o.optString("soundUri",""),o.optString("soundName",""));
                if(e.running && e.endMs<=0){ e.running=false; e.remainingMs=e.durationMs; }
                out.add(e);
            }
        }catch(Throwable ignored){}
        return out;
    }

    private static JSONObject json(Entry e)throws Exception{
        JSONObject o=new JSONObject();
        o.put("id",e.id); o.put("label",e.label); o.put("duration",e.durationMs);
        o.put("remaining",e.remainingMs); o.put("end",e.endMs); o.put("running",e.running);
        o.put("soundUri",e.soundUri==null?"":e.soundUri); o.put("soundName",e.soundName==null?"":e.soundName);
        return o;
    }

    private static synchronized void save(Context c,List<Entry> list){
        JSONArray a=new JSONArray();
        for(Entry e:list)try{a.put(json(e));}catch(Throwable ignored){}
        p(c).edit().putString(KEY_TIMERS,a.toString()).commit();
    }

    public static synchronized Entry add(Context c,String label,long durationMs){
        long id=Math.max(1000L,p(c).getLong(KEY_NEXT_ID,1000L));
        p(c).edit().putLong(KEY_NEXT_ID,id+1).commit();
        Entry e=new Entry(id,label,durationMs,durationMs,0L,false,"","");
        List<Entry> list=all(c); list.add(e); save(c,list); return e;
    }

    public static synchronized Entry find(Context c,long id){ for(Entry e:all(c))if(e.id==id)return e; return null; }
    public static synchronized Entry firstRunning(Context c){ for(Entry e:all(c))if(e.running)return e; return null; }

    public static synchronized void update(Context c,Entry changed){
        List<Entry> list=all(c); boolean found=false;
        for(int i=0;i<list.size();i++)if(list.get(i).id==changed.id){list.set(i,changed);found=true;break;}
        if(!found&&changed.id>=1000)list.add(changed); save(c,list);
    }

    public static synchronized void delete(Context c,long id){ List<Entry> list=all(c); list.removeIf(e->e.id==id); save(c,list); }

    private static void migrateLegacy(Context c){
        SharedPreferences sp=p(c); if(sp.getBoolean(KEY_MIGRATED,false))return;
        sp.edit().putBoolean(KEY_MIGRATED,true).commit();
        SharedPreferences old=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE);
        boolean running=old.getBoolean("timer_running",false);
        long end=old.getLong("timer_end",0L);
        long remaining=old.getLong("timer_remaining",0L);
        long duration=old.getLong("timer_duration",0L);
        if(running) remaining=Math.max(0L,end-System.currentTimeMillis());
        if(duration<=0) duration=remaining;
        if(duration<=0) return;
        long id=Math.max(1000L,sp.getLong(KEY_NEXT_ID,1000L));
        sp.edit().putLong(KEY_NEXT_ID,id+1).commit();
        Entry e=new Entry(id,"",duration,remaining,end,running&&remaining>0,"","");
        ArrayList<Entry> list=new ArrayList<>(); list.add(e); save(c,list);
        old.edit().putBoolean("timer_running",false).putLong("timer_end",0L).putLong("timer_remaining",0L).putLong("timer_duration",0L).apply();
    }
}
