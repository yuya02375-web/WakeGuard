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
    private static void save(Context c,List<Entry> list){save(c,list,null);}
    private static void save(Context c,List<Entry> list,List<String> folderNames){
        JSONArray array=new JSONArray();
        for(Entry e:list)try{array.put(json(e));}catch(Exception ignored){}
        SharedPreferences.Editor editor=p(c).edit().putString(KEY_TIMERS,array.toString());
        if(folderNames!=null) editor.putString(KEY_FOLDERS,folderJson(folderNames));
        editor.commit();
        try{WidgetSuite.refreshAll(c);}catch(Throwable ignored){}
    }

    private static Entry addInternal(Context c,String label,long durationMs,boolean saved,String folder){
        List<Entry> list=all(c);
        long id=Math.max(1000L,p(c).getLong(KEY_NEXT_ID,1000L));
        p(c).edit().putLong(KEY_NEXT_ID,id+1).commit();
        Entry entry=new Entry(id,label,durationMs,durationMs,0L,false,saved,"","");
        List<String> folderNames=saved?folders(c):null;
        if(saved) entry.folder=selectOrAddFolder(folderNames,folder);
        list.add(entry);
        save(c,list,folderNames);
        return entry;
    }
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
    public static synchronized List<String> folders(Context c){
        List<String> names=new ArrayList<>();
        names.add(UNFILED);
        try{
            JSONArray stored=new JSONArray(p(c).getString(KEY_FOLDERS,"[]"));
            for(int i=0;i<stored.length();i++) selectOrAddFolder(names,stored.optString(i,""));
        }catch(Exception ignored){}
        for(Entry entry:all(c)) if(entry.saved) selectOrAddFolder(names,entry.folder);
        return names;
    }

    public static synchronized void createFolder(Context c,String name){
        List<String> names=folders(c);
        int previousSize=names.size();
        selectOrAddFolder(names,name);
        if(names.size()!=previousSize) p(c).edit().putString(KEY_FOLDERS,folderJson(names)).commit();
    }

    public static synchronized void renameFolder(Context c,String oldName,String newName){
        String old=normalizeFolder(oldName),next=normalizeFolder(newName);
        if(UNFILED.equals(old)||UNFILED.equals(next)) return;
        List<String> names=folders(c);
        int oldIndex=folderIndex(names,old),newIndex=folderIndex(names,next);
        if(oldIndex<0||(newIndex>=0&&newIndex!=oldIndex)) return;
        names.set(oldIndex,next);
        List<Entry> entries=all(c);
        for(Entry entry:entries) if(entry.saved&&entry.folder.equalsIgnoreCase(old)) entry.folder=next;
        save(c,entries,names);
    }

    public static synchronized void deleteFolder(Context c,String name){
        String target=normalizeFolder(name);
        if(UNFILED.equals(target)) return;
        List<String> names=folders(c);
        names.removeIf(folder->folder.equalsIgnoreCase(target));
        List<Entry> entries=all(c);
        for(Entry entry:entries) if(entry.saved&&entry.folder.equalsIgnoreCase(target)) entry.folder=UNFILED;
        save(c,entries,names);
    }

    public static synchronized void moveToFolder(Context c,long id,String folder){
        List<Entry> entries=all(c);
        List<String> names=folders(c);
        for(Entry entry:entries){
            if(entry.id!=id||!entry.saved) continue;
            entry.folder=selectOrAddFolder(names,folder);
            save(c,entries,names);
            return;
        }
    }

    static String normalizeFolder(String name){
        String normalized=name==null?"":name.trim().replaceAll("\\s+"," ");
        if(normalized.isEmpty()) return UNFILED;
        if(normalized.codePointCount(0,normalized.length())>28){
            normalized=normalized.substring(0,normalized.offsetByCodePoints(0,28)).trim();
        }
        return normalized;
    }

    private static int folderIndex(List<String> names,String folder){
        for(int i=0;i<names.size();i++) if(names.get(i).equalsIgnoreCase(folder)) return i;
        return -1;
    }

    private static String selectOrAddFolder(List<String> names,String folder){
        String normalized=normalizeFolder(folder);
        int index=folderIndex(names,normalized);
        if(index>=0) return names.get(index);
        names.add(normalized);
        return normalized;
    }

    private static String folderJson(List<String> names){
        JSONArray array=new JSONArray();
        for(String name:names) if(!UNFILED.equals(name)) array.put(name);
        return array.toString();
    }
    public static synchronized void delete(Context c,long id){List<Entry> list=all(c);list.removeIf(e->e.id==id);save(c,list);}
    private static void migrateLegacy(Context c){SharedPreferences sp=p(c);if(sp.getBoolean(KEY_MIGRATED,false))return;sp.edit().putBoolean(KEY_MIGRATED,true).commit();SharedPreferences old=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE);boolean running=old.getBoolean("timer_running",false);long end=old.getLong("timer_end",0L),remaining=old.getLong("timer_remaining",0L),duration=old.getLong("timer_duration",0L);if(running)remaining=Math.max(0L,end-System.currentTimeMillis());if(duration<=0)duration=remaining;if(duration<=0)return;long id=Math.max(1000L,sp.getLong(KEY_NEXT_ID,1000L));sp.edit().putLong(KEY_NEXT_ID,id+1).commit();Entry e=new Entry(id,"",duration,remaining,end,running&&remaining>0,true,"","");ArrayList<Entry> list=new ArrayList<>();list.add(e);save(c,list);old.edit().putBoolean("timer_running",false).putLong("timer_end",0L).putLong("timer_remaining",0L).putLong("timer_duration",0L).apply();}
}
