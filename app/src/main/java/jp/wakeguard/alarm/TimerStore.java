package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class TimerStore {
    private static final String FILE="wakeguard_timers_v2";
    private static final String KEY_TIMERS="timers", KEY_NEXT_ID="next_id", KEY_MIGRATED="migrated_legacy", KEY_FOLDERS="folders";
    public static final String UNFILED="未分類";
    private TimerStore() {}

    public static final class Entry {
        public long id,durationMs,remainingMs,endMs;
        public boolean running,saved;
        public String label,soundUri,soundName,folder;
        public Entry(long id,String label,long durationMs,long remainingMs,long endMs,boolean running,boolean saved,String soundUri,String soundName){
            this.id=id;this.label=label==null?"":label.trim();this.durationMs=Math.max(1000L,durationMs);this.remainingMs=Math.max(0L,remainingMs);this.endMs=Math.max(0L,endMs);this.running=running;this.saved=saved;this.soundUri=soundUri==null?"":soundUri;this.soundName=soundName==null?"":soundName;this.folder=saved?UNFILED:"";
        }
        public long remaining(){return running?Math.max(0L,endMs-System.currentTimeMillis()):Math.max(0L,remainingMs);}
        public boolean finished(){return !running&&remainingMs<=0;}
    }

    private static SharedPreferences p(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    public static synchronized List<Entry> all(Context c){
        migrateLegacy(c);ArrayList<Entry> out=new ArrayList<>();String raw=p(c).getString(KEY_TIMERS,"[]");
        try{JSONArray a=new JSONArray(raw==null?"[]":raw);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;long id=o.optLong("id",-1L);if(id<1000)continue;Entry e=new Entry(id,o.optString("label",""),o.optLong("duration",60000L),o.optLong("remaining",0L),o.optLong("end",0L),o.optBoolean("running",false),o.optBoolean("saved",true),o.optString("soundUri",""),o.optString("soundName",""));e.folder=e.saved?normalizeFolder(o.optString("folder",UNFILED)):"";if(e.running&&e.endMs<=0){e.running=false;e.remainingMs=e.durationMs;}out.add(e);}}catch(Throwable ignored){}return out;
    }
    private static JSONObject json(Entry e)throws Exception{JSONObject o=new JSONObject();o.put("id",e.id);o.put("label",e.label);o.put("duration",e.durationMs);o.put("remaining",e.remainingMs);o.put("end",e.endMs);o.put("running",e.running);o.put("saved",e.saved);o.put("soundUri",e.soundUri);o.put("soundName",e.soundName);o.put("folder",e.saved?normalizeFolder(e.folder):"");return o;}
    private static synchronized void save(Context c,List<Entry> list){JSONArray a=new JSONArray();for(Entry e:list)try{a.put(json(e));}catch(Throwable ignored){}p(c).edit().putString(KEY_TIMERS,a.toString()).commit();try{WidgetSuite.refreshAll(c);}catch(Throwable ignored){}}
    private static synchronized Entry addInternal(Context c,String label,long durationMs,boolean saved,String folder){long id=Math.max(1000L,p(c).getLong(KEY_NEXT_ID,1000L));p(c).edit().putLong(KEY_NEXT_ID,id+1).commit();Entry e=new Entry(id,label,durationMs,durationMs,0L,false,saved,"","");e.folder=saved?normalizeFolder(folder):"";List<Entry> list=all(c);list.add(e);save(c,list);if(saved&&!UNFILED.equals(e.folder))rememberFolder(c,e.folder);return e;}
    public static synchronized Entry add(Context c,String label,long durationMs){return addSaved(c,label,durationMs);}
    public static synchronized Entry addSaved(Context c,String label,long durationMs){return addSaved(c,label,durationMs,UNFILED);}
    public static synchronized Entry addSaved(Context c,String label,long durationMs,String folder){return addInternal(c,label,durationMs,true,folder);}
    public static synchronized Entry addTemporary(Context c,String label,long durationMs){return addInternal(c,label,durationMs,false,"");}
    public static synchronized Entry addTemporaryFrom(Context c,Entry preset){if(preset==null)return null;Entry e=addTemporary(c,preset.label,preset.durationMs);e.soundUri=preset.soundUri;e.soundName=preset.soundName;update(c,e);return e;}
    public static synchronized Entry duplicateAsSaved(Context c,Entry source,String folder){if(source==null)return null;Entry e=addSaved(c,source.label,source.durationMs,folder);e.soundUri=source.soundUri;e.soundName=source.soundName;update(c,e);return e;}
    public static synchronized Entry find(Context c,long id){for(Entry e:all(c))if(e.id==id)return e;return null;}
    public static synchronized Entry firstRunning(Context c){for(Entry e:all(c))if(e.running)return e;return null;}
    public static synchronized void update(Context c,Entry changed){List<Entry> list=all(c);boolean found=false;for(int i=0;i<list.size();i++)if(list.get(i).id==changed.id){list.set(i,changed);found=true;break;}if(!found&&changed.id>=1000)list.add(changed);save(c,list);}
    public static synchronized void makeSaved(Context c,long id){Entry e=find(c,id);if(e==null)return;e.saved=true;e.folder=UNFILED;update(c,e);}
    public static synchronized List<String> folders(Context c){ArrayList<String> out=new ArrayList<>();out.add(UNFILED);String raw=p(c).getString(KEY_FOLDERS,"[]");try{JSONArray a=new JSONArray(raw==null?"[]":raw);for(int i=0;i<a.length();i++){String n=normalizeFolder(a.optString(i,""));if(!UNFILED.equals(n)&&!containsFolder(out,n))out.add(n);}}catch(Throwable ignored){}for(Entry e:all(c))if(e.saved){String n=normalizeFolder(e.folder);if(!containsFolder(out,n))out.add(n);}return out;}
    public static synchronized void createFolder(Context c,String name){String n=normalizeFolder(name);if(UNFILED.equals(n))return;ArrayList<String> out=new ArrayList<>(folders(c));if(!containsFolder(out,n)){out.add(n);storeFolders(c,out);}}
    public static synchronized void renameFolder(Context c,String oldName,String newName){String old=normalizeFolder(oldName),next=normalizeFolder(newName);if(UNFILED.equals(old)||UNFILED.equals(next)||old.equals(next))return;ArrayList<String> names=new ArrayList<>(folders(c));if(containsFolder(names,next))return;for(int i=0;i<names.size();i++)if(names.get(i).equals(old)){names.set(i,next);break;}List<Entry> entries=all(c);for(Entry e:entries)if(e.saved&&normalizeFolder(e.folder).equals(old))e.folder=next;save(c,entries);storeFolders(c,names);}
    public static synchronized void deleteFolder(Context c,String name){String target=normalizeFolder(name);if(UNFILED.equals(target))return;ArrayList<String> names=new ArrayList<>(folders(c));names.removeIf(n->n.equals(target));List<Entry> entries=all(c);for(Entry e:entries)if(e.saved&&normalizeFolder(e.folder).equals(target))e.folder=UNFILED;save(c,entries);storeFolders(c,names);}
    public static synchronized void moveToFolder(Context c,long id,String folder){Entry e=find(c,id);if(e==null||!e.saved)return;e.folder=normalizeFolder(folder);if(!UNFILED.equals(e.folder))rememberFolder(c,e.folder);update(c,e);}
    private static String normalizeFolder(String name){String n=name==null?"":name.trim().replaceAll("\\s+"," ");if(n.isEmpty())return UNFILED;return n.length()>28?n.substring(0,28).trim():n;}
    private static boolean containsFolder(List<String> values,String name){for(String v:values)if(v.equalsIgnoreCase(name))return true;return false;}
    private static void rememberFolder(Context c,String name){ArrayList<String> list=new ArrayList<>(folders(c));if(!containsFolder(list,name)){list.add(name);storeFolders(c,list);}}
    private static void storeFolders(Context c,List<String> names){JSONArray a=new JSONArray();for(String n:names)if(!UNFILED.equals(n))a.put(n);p(c).edit().putString(KEY_FOLDERS,a.toString()).commit();}
    public static synchronized void delete(Context c,long id){List<Entry> list=all(c);list.removeIf(e->e.id==id);save(c,list);}
    private static void migrateLegacy(Context c){SharedPreferences sp=p(c);if(sp.getBoolean(KEY_MIGRATED,false))return;sp.edit().putBoolean(KEY_MIGRATED,true).commit();SharedPreferences old=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE);boolean running=old.getBoolean("timer_running",false);long end=old.getLong("timer_end",0L),remaining=old.getLong("timer_remaining",0L),duration=old.getLong("timer_duration",0L);if(running)remaining=Math.max(0L,end-System.currentTimeMillis());if(duration<=0)duration=remaining;if(duration<=0)return;long id=Math.max(1000L,sp.getLong(KEY_NEXT_ID,1000L));sp.edit().putLong(KEY_NEXT_ID,id+1).commit();Entry e=new Entry(id,"",duration,remaining,end,running&&remaining>0,true,"","");ArrayList<Entry> list=new ArrayList<>();list.add(e);save(c,list);old.edit().putBoolean("timer_running",false).putLong("timer_end",0L).putLong("timer_remaining",0L).putLong("timer_duration",0L).apply();}
}
