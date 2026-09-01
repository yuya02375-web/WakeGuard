package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AlarmStore {
    private static final String FILE="wake_guard_multi_alarms", KEY_ALARMS="alarms_json", KEY_NEXT_ID="next_id", KEY_KNOWN_IDS="known_ids";
    private AlarmStore() {}

    public static final class Entry {
        public long id; public int hour,minute,dayMask,steps,missionCount,volume; public boolean enabled;
        public String label,missionType,vibration,soundUri,soundName; public long soundBytes,soundDurationMs;
        public Entry(long id,int hour,int minute,int dayMask,boolean enabled,String label,
                     int steps,String missionType,int missionCount,int volume,String vibration,
                     String soundUri,String soundName,long soundBytes,long soundDurationMs) {
            this.id=id;this.hour=hour;this.minute=minute;this.dayMask=dayMask;this.enabled=enabled;this.label=label==null?"":label;
            this.steps=Math.max(1,Math.min(9999,steps));
            this.missionType=normalizeMission(missionType);
            this.missionCount=Math.max(1,Math.min(500,missionCount));
            this.volume=Math.max(0,Math.min(100,volume));
            this.vibration=vibration==null?"IRREGULAR":vibration;this.soundUri=soundUri==null?"":soundUri;this.soundName=soundName==null?"":soundName;
            this.soundBytes=soundBytes;this.soundDurationMs=soundDurationMs;
        }
        public boolean oneShot(){return dayMask==0;}
    }

    public static String normalizeMission(String s){
        if("MATH".equals(s)||"TAP".equals(s)||"CODE".equals(s)||"SHAKE".equals(s)||"MEMORY".equals(s)||"TYPE".equals(s)||"HOLD".equals(s)||"RANDOM".equals(s)) return s;
        return "STEPS";
    }

    private static SharedPreferences p(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    private static Entry read(Context c,JSONObject o){
        long id=o.optLong("id",-1); if(id<1000)return null;
        return new Entry(id,Math.max(0,Math.min(23,o.optInt("hour",7))),Math.max(0,Math.min(59,o.optInt("minute",0))),o.optInt("days",0)&0x7F,
                o.optBoolean("enabled",true),o.optString("label",""),
                o.has("steps")?o.optInt("steps",50):Prefs.steps(c),
                o.optString("missionType","STEPS"),o.optInt("missionCount",3),
                o.has("volume")?o.optInt("volume",15):Prefs.volume(c),
                o.has("vibration")?o.optString("vibration","IRREGULAR"):Prefs.vibration(c),
                o.has("soundUri")?o.optString("soundUri",""):Prefs.soundUri(c),o.has("soundName")?o.optString("soundName",""):Prefs.soundName(c),
                o.has("soundBytes")?o.optLong("soundBytes",-1):Prefs.soundBytes(c),o.has("soundDurationMs")?o.optLong("soundDurationMs",-1):Prefs.soundDurationMs(c));
    }
    public static synchronized List<Entry> all(Context c){
        ArrayList<Entry> out=new ArrayList<>(); String raw=p(c).getString(KEY_ALARMS,"[]");
        try{JSONArray a=new JSONArray(raw==null?"[]":raw);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;Entry e=read(c,o);if(e!=null)out.add(e);}}catch(Throwable ignored){}
        return out;
    }
    private static JSONObject json(Entry e)throws Exception{
        JSONObject o=new JSONObject();o.put("id",e.id);o.put("hour",e.hour);o.put("minute",e.minute);o.put("days",e.dayMask&0x7F);o.put("enabled",e.enabled);o.put("label",e.label);
        o.put("steps",e.steps);o.put("missionType",normalizeMission(e.missionType));o.put("missionCount",e.missionCount);
        o.put("volume",e.volume);o.put("vibration",e.vibration);o.put("soundUri",e.soundUri);o.put("soundName",e.soundName);o.put("soundBytes",e.soundBytes);o.put("soundDurationMs",e.soundDurationMs);return o;
    }
    private static synchronized void save(Context c,List<Entry> list){
        JSONArray a=new JSONArray();Set<String> known=new HashSet<>(p(c).getStringSet(KEY_KNOWN_IDS,new HashSet<>()));
        for(Entry e:list){try{a.put(json(e));known.add(String.valueOf(e.id));}catch(Throwable ignored){}}
        p(c).edit().putString(KEY_ALARMS,a.toString()).putStringSet(KEY_KNOWN_IDS,known).commit();
    }
    public static synchronized Entry add(Context c,Entry draft){
        SharedPreferences sp=p(c);long id=Math.max(1000L,sp.getLong(KEY_NEXT_ID,1000L));sp.edit().putLong(KEY_NEXT_ID,id+1).commit();
        Entry e=new Entry(id,draft.hour,draft.minute,draft.dayMask,draft.enabled,draft.label,draft.steps,draft.missionType,draft.missionCount,draft.volume,draft.vibration,draft.soundUri,draft.soundName,draft.soundBytes,draft.soundDurationMs);
        List<Entry> list=all(c);list.add(e);save(c,list);return e;
    }
    public static synchronized void update(Context c,Entry changed){List<Entry> list=all(c);boolean found=false;for(int i=0;i<list.size();i++)if(list.get(i).id==changed.id){list.set(i,changed);found=true;break;}if(!found&&changed.id>=1000)list.add(changed);save(c,list);}
    public static synchronized void delete(Context c,long id){List<Entry> list=all(c);list.removeIf(e->e.id==id);save(c,list);}
    public static synchronized Entry find(Context c,long id){for(Entry e:all(c))if(e.id==id)return e;return null;}
    public static synchronized void markFiredIfOneShot(Context c,long id){if(id<1000)return;Entry e=find(c,id);if(e!=null&&e.enabled&&e.oneShot()){e.enabled=false;update(c,e);}}
    public static synchronized long[] knownIds(Context c){Set<String>s=p(c).getStringSet(KEY_KNOWN_IDS,new HashSet<>());long[]out=new long[s.size()];int n=0;for(String x:s){try{out[n++]=Long.parseLong(x);}catch(Throwable ignored){}}if(n==out.length)return out;long[]t=new long[n];System.arraycopy(out,0,t,0,n);return t;}
}
