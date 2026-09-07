from pathlib import Path
import re

app = Path('WakeGuard/app')
java = app / 'src/main/java/jp/wakeguard/alarm'

# Version bump.
p = app / 'build.gradle.kts'
s = p.read_text(encoding='utf-8')
s = re.sub(r'versionCode = \d+', 'versionCode = 90', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.7.9"', s)
p.write_text(s, encoding='utf-8')

catalog = java / 'WorldCityCatalog.java'
catalog.write_text(r'''package jp.wakeguard.alarm;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Fast offline city aliases for world clock selection. GeoNames aliases include Japanese, English and Korean names. */
public final class WorldCityCatalog {
    private WorldCityCatalog(){}
    private static final char SEP='\u001F';
    private static volatile ArrayList<Entry> CACHE;
    private static final Object QUERY_LOCK=new Object();
    private static final LinkedHashMap<String,ArrayList<Entry>> QUERY_CACHE=new LinkedHashMap<String,ArrayList<Entry>>(48,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,ArrayList<Entry>> e){return size()>48;}
    };

    public static final class Entry {
        public final String name,asciiName,countryCode,zoneId,search,nameNorm,asciiNorm;
        public final long population;
        Entry(String n,String a,String c,String z,long p,String s){
            name=n;asciiName=a;countryCode=c;zoneId=z;population=p;
            nameNorm=normalize(n);asciiNorm=normalize(a);search=s;
        }
    }

    public static String normalize(String x){
        if(x==null)return "";
        try{x=Normalizer.normalize(x,Normalizer.Form.NFKC);}catch(Throwable ignored){}
        StringBuilder kana=new StringBuilder(x.length());
        for(int i=0;i<x.length();i++){char c=x.charAt(i);if(c>=0x3041&&c<=0x3096)c=(char)(c+0x60);kana.append(c);}
        x=kana.toString();
        try{x=Normalizer.normalize(x,Normalizer.Form.NFD).replaceAll("\\p{M}+","");}catch(Throwable ignored){}
        return x.toLowerCase(Locale.ROOT).replace('_',' ').replaceAll("\\s+"," ").trim();
    }

    public static void preload(Context c){load(c.getApplicationContext());}

    private static ArrayList<Entry> load(Context c){
        ArrayList<Entry> got=CACHE;if(got!=null)return got;
        synchronized(WorldCityCatalog.class){
            got=CACHE;if(got!=null)return got;
            ArrayList<Entry> list=new ArrayList<>(36000);
            try(BufferedReader br=new BufferedReader(new InputStreamReader(new GZIPInputStream(c.getAssets().open("world_cities_gz.dat")),"UTF-8"),65536)){
                String line;
                while((line=br.readLine())!=null){
                    String[] p=line.split("\\t",6);if(p.length<5)continue;
                    String n=p[0],a=p[1],cc=p[2],z=p[3],alts=p.length>5?p[5]:"";long pop=0;try{pop=Long.parseLong(p[4]);}catch(Throwable ignored){}
                    // Normalize once at load time. The old implementation re-normalized names for every keystroke.
                    String s=normalize(n+" "+a+" "+alts+" "+cc+" "+z);
                    list.add(new Entry(n,a,cc,z,pop,s));
                }
            }catch(Throwable ignored){}
            CACHE=list;return list;
        }
    }

    public static ArrayList<Entry> popular(Context c,int limit){
        ArrayList<Entry> all=load(c),out=new ArrayList<>();
        for(int i=0;i<all.size()&&out.size()<limit;i++)out.add(all.get(i));
        return out;
    }

    public static ArrayList<Entry> search(Context c,String raw,int limit){
        String q=normalize(raw);if(q.isEmpty())return popular(c,limit);
        String key=q+'\u001E'+limit;
        synchronized(QUERY_LOCK){ArrayList<Entry> hit=QUERY_CACHE.get(key);if(hit!=null)return new ArrayList<>(hit);}

        ArrayList<Scored> scored=new ArrayList<>(Math.max(32,limit*2));
        for(Entry e:load(c)){
            int score=score(e,q);if(score<99)scored.add(new Scored(e,score));
        }
        Collections.sort(scored,new Comparator<Scored>(){public int compare(Scored x,Scored y){int d=Integer.compare(x.score,y.score);if(d!=0)return d;d=Long.compare(y.e.population,x.e.population);if(d!=0)return d;return x.e.name.compareToIgnoreCase(y.e.name);}});
        ArrayList<Entry> out=new ArrayList<>(Math.min(limit,scored.size()));
        for(Scored s:scored){out.add(s.e);if(out.size()>=limit)break;}
        synchronized(QUERY_LOCK){QUERY_CACHE.put(key,new ArrayList<>(out));}
        return out;
    }
    private static final class Scored{final Entry e;final int score;Scored(Entry x,int s){e=x;score=s;}}
    private static int score(Entry e,String q){
        if(e.nameNorm.equals(q)||e.asciiNorm.equals(q))return 0;
        if(e.nameNorm.startsWith(q)||e.asciiNorm.startsWith(q))return 1;
        if(e.search.contains(q))return 2;
        return 99;
    }

    public static String encode(Entry e){return encode(e.name,e.zoneId,e.countryCode);}
    public static String encode(String label,String zone,String country){return (label==null?"":label)+SEP+(zone==null?"":zone)+SEP+(country==null?"":country);}
    public static boolean isAlias(String item){return item!=null&&item.indexOf(SEP)>=0;}
    public static String zoneOf(String item){if(item==null)return "";int a=item.indexOf(SEP);if(a<0)return item;int b=item.indexOf(SEP,a+1);return b<0?item.substring(a+1):item.substring(a+1,b);}
    public static String labelOf(String item,String fallback){if(item==null)return fallback;int a=item.indexOf(SEP);if(a<0)return fallback;String x=item.substring(0,a);return x.isEmpty()?fallback:x;}
    public static String countryOf(String item,String fallback){if(item==null)return fallback;int a=item.indexOf(SEP);if(a<0)return fallback;int b=item.indexOf(SEP,a+1);if(b<0||b+1>=item.length())return fallback;String x=item.substring(b+1);return x.isEmpty()?fallback:x;}

    public static String savedWorldLabel(Context c,String zone,String fallback){
        String x=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE).getString("world_label_"+zone,"");return x==null||x.trim().isEmpty()?fallback:x.trim();
    }
    public static void saveWorldLabel(Context c,String zone,String label){
        android.content.SharedPreferences.Editor e=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE).edit();
        if(label==null||label.trim().isEmpty())e.remove("world_label_"+zone);else e.putString("world_label_"+zone,label.trim());e.apply();
    }
}
''', encoding='utf-8')

# Start warming the city catalog as soon as the app starts, off the UI thread.
wa = java / 'WakeGuardApp.java'
s = wa.read_text(encoding='utf-8')
needle = '        super.onCreate();\n'
insert = '''        super.onCreate();
        new Thread(() -> {
            try { Thread.currentThread().setPriority(Thread.MIN_PRIORITY); WorldCityCatalog.preload(getApplicationContext()); }
            catch (Throwable ignored) {}
        }, "IGNIDO-CityIndex").start();
'''
assert needle in s, 'WakeGuardApp onCreate anchor missing'
s = s.replace(needle, insert, 1)
wa.write_text(s, encoding='utf-8')

# Make the supported search languages explicit and reduce UI debounce latency.
clock = java / 'ClockActivity.java'
s = clock.read_text(encoding='utf-8')
s = s.replace('search.setHint(I18n.tr(this,"国・都市・地域・タイムゾーンを検索"));',
              'search.setHint(I18n.tr(this,"日本語・English・한국어で都市 / 国 / タイムゾーンを検索"));', 1)
s = s.replace('TextView hybridHint=text("世界の主要都市データをオフライン検索し、正式なIANAタイムゾーンもすべて検索します",10,Ui.MUTED_2);',
              'TextView hybridHint=text("日本語・English・한국어の都市名を高速オフライン検索。正式なIANAタイムゾーンも検索します",10,Ui.MUTED_2);', 1)
assert 'q.trim().isEmpty()?0L:45L' in s, 'search debounce anchor missing'
s = s.replace('q.trim().isEmpty()?0L:45L', 'q.trim().isEmpty()?0L:18L', 1)
clock.write_text(s, encoding='utf-8')

# Add translations for the new prompt so the surrounding UI still follows app language.
i18n = java / 'I18n.java'
s = i18n.read_text(encoding='utf-8')
anchor = 'put("都市 / タイムゾーンを追加","Add city / time zone","도시 / 시간대 추가");'
if anchor in s and '日本語・English・한국어で都市 / 国 / タイムゾーンを検索' not in s:
    s = s.replace(anchor, anchor+' put("日本語・English・한국어で都市 / 国 / タイムゾーンを検索","Search cities / countries / time zones in Japanese, English, or Korean","일본어・English・한국어로 도시 / 국가 / 시간대 검색");', 1)
i18n.write_text(s, encoding='utf-8')

# Validation.
assert 'versionName = "1.7.9"' in p.read_text(encoding='utf-8')
assert 'versionCode = 90' in p.read_text(encoding='utf-8')
cs = catalog.read_text(encoding='utf-8')
assert 'nameNorm=normalize(n)' in cs and 'QUERY_CACHE' in cs and 'public static void preload' in cs
assert 'IGNIDO-CityIndex' in wa.read_text(encoding='utf-8')
assert 'q.trim().isEmpty()?0L:18L' in clock.read_text(encoding='utf-8')
print('IGNIDO Wake Android v1.7.9 fast trilingual search patch applied')
