from pathlib import Path
import gzip, re, shutil

ROOT = Path('WakeGuard')
JAVA = ROOT/'app/src/main/java/jp/wakeguard/alarm'
ASSETS = ROOT/'app/src/main/assets'
GRADLE = ROOT/'app/build.gradle.kts'

# Build compact offline city -> IANA timezone catalog from GeoNames cities15000.
# Expected source path is provided by the workflow.
src_candidates = [Path('/tmp/cities15000.txt'), Path('cities15000.txt')]
src = next((p for p in src_candidates if p.exists()), None)
if src is None:
    raise SystemExit('GeoNames cities15000.txt not found')
ASSETS.mkdir(parents=True, exist_ok=True)
rows=[]
with src.open('r', encoding='utf-8', errors='replace') as f:
    for line in f:
        parts=line.rstrip('\n').split('\t')
        if len(parts)<19: continue
        name, ascii_name, alternates = parts[1], parts[2], parts[3]
        country, population, zone = parts[8], parts[14], parts[17]
        if not zone or '/' not in zone: continue
        try: pop=int(population or '0')
        except: pop=0
        def clean(x): return (x or '').replace('\t',' ').replace('\r',' ').replace('\n',' ').strip()
        rows.append((pop, clean(name), clean(ascii_name), clean(country), clean(zone), clean(alternates)))
rows.sort(key=lambda r:(-r[0], r[1].lower()))
out_asset=ASSETS/'world_cities_gz.dat'
with gzip.open(out_asset,'wt',encoding='utf-8',compresslevel=9,newline='\n') as g:
    for pop,name,ascii_name,country,zone,alternates in rows:
        g.write(f'{name}\t{ascii_name}\t{country}\t{zone}\t{pop}\t{alternates}\n')
print('world city rows',len(rows),'asset',out_asset.stat().st_size)

world_city_catalog = r'''package jp.wakeguard.alarm;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** Offline city aliases for world clock selection. Data snapshot is generated from GeoNames cities15000. */
public final class WorldCityCatalog {
    private WorldCityCatalog(){}
    private static final char SEP='\u001F';
    private static volatile ArrayList<Entry> CACHE;

    public static final class Entry {
        public final String name,asciiName,countryCode,zoneId,search;
        public final long population;
        Entry(String n,String a,String c,String z,long p,String s){name=n;asciiName=a;countryCode=c;zoneId=z;population=p;search=s;}
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

    private static ArrayList<Entry> load(Context c){
        ArrayList<Entry> got=CACHE;if(got!=null)return got;
        synchronized(WorldCityCatalog.class){
            got=CACHE;if(got!=null)return got;
            ArrayList<Entry> list=new ArrayList<>();
            try(BufferedReader br=new BufferedReader(new InputStreamReader(new GZIPInputStream(c.getAssets().open("world_cities_gz.dat")),"UTF-8"),32768)){
                String line;
                while((line=br.readLine())!=null){
                    String[] p=line.split("\\t",6);if(p.length<5)continue;
                    String n=p[0],a=p[1],cc=p[2],z=p[3],alts=p.length>5?p[5]:"";long pop=0;try{pop=Long.parseLong(p[4]);}catch(Throwable ignored){}
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
        String q=normalize(raw);ArrayList<Entry> out=new ArrayList<>();if(q.isEmpty())return popular(c,limit);
        ArrayList<Scored> scored=new ArrayList<>();
        for(Entry e:load(c)){
            int score=score(e,q);if(score<99)scored.add(new Scored(e,score));
        }
        Collections.sort(scored,new Comparator<Scored>(){public int compare(Scored x,Scored y){int d=Integer.compare(x.score,y.score);if(d!=0)return d;d=Long.compare(y.e.population,x.e.population);if(d!=0)return d;return x.e.name.compareToIgnoreCase(y.e.name);}});
        for(Scored s:scored){out.add(s.e);if(out.size()>=limit)break;}return out;
    }
    private static final class Scored{final Entry e;final int score;Scored(Entry x,int s){e=x;score=s;}}
    private static int score(Entry e,String q){
        String n=normalize(e.name),a=normalize(e.asciiName);
        if(n.equals(q)||a.equals(q))return 0;
        if(n.startsWith(q)||a.startsWith(q))return 1;
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
'''
(JAVA/'WorldCityCatalog.java').write_text(world_city_catalog,encoding='utf-8')

provider = r'''package jp.wakeguard.alarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.time.ZoneId;

public class WorldClockWidgetProvider extends AppWidgetProvider {
    private static final String PREF="wakeguard_world_clock_widgets";
    private static final String KEY_ZONE="zone_";
    private static final String KEY_STYLE="style_";
    private static final String KEY_LABEL="label_";

    static void saveZone(Context c,int appWidgetId,String zoneId){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_ZONE+appWidgetId,zoneId).apply();}
    static void saveStyle(Context c,int appWidgetId,String style){if(!"analog".equals(style))style="digital";c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_STYLE+appWidgetId,style).apply();}
    static void saveConfig(Context c,int appWidgetId,String zoneId,String style){saveConfig(c,appWidgetId,zoneId,style,zoneLabel(c,zoneId));}
    static void saveConfig(Context c,int appWidgetId,String zoneId,String style,String label){
        if(!"analog".equals(style))style="digital";if(label==null||label.trim().isEmpty())label=zoneLabel(c,zoneId);
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_ZONE+appWidgetId,zoneId).putString(KEY_STYLE+appWidgetId,style).putString(KEY_LABEL+appWidgetId,label.trim()).apply();
    }

    static String loadZone(Context c,int appWidgetId){String z=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_ZONE+appWidgetId,"");if(z==null||z.isEmpty())z=ZoneId.systemDefault().getId();try{ZoneId.of(z);}catch(Throwable t){z=ZoneId.systemDefault().getId();}return z;}
    static String loadStyle(Context c,int appWidgetId){String x=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_STYLE+appWidgetId,"digital");return "analog".equals(x)?"analog":"digital";}
    static String loadLabel(Context c,int appWidgetId){String zone=loadZone(c,appWidgetId);String x=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_LABEL+appWidgetId,"");return x==null||x.trim().isEmpty()?zoneLabel(c,zone):x.trim();}

    static String zoneLabel(Context c,String zoneId){
        try{String x=android.icu.text.TimeZoneNames.getInstance(I18n.locale(c)).getExemplarLocationName(zoneId);if(x!=null&&!x.trim().isEmpty())return x.trim();}catch(Throwable ignored){}
        String last=zoneId.contains("/")?zoneId.substring(zoneId.lastIndexOf('/')+1):zoneId;return last.replace('_',' ');
    }

    private static int width(AppWidgetManager m,int id){try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180));}catch(Throwable t){return 180;}}
    private static int height(AppWidgetManager m,int id){try{return Math.max(1,m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110));}catch(Throwable t){return 110;}}
    private static int chooseLayout(boolean analog,int w,int h){boolean tall=h>w*1.22f,wide=w>h*1.28f;if(analog){if(tall)return R.layout.widget_world_clock_analog_tall;if(wide)return R.layout.widget_world_clock_analog_wide;return R.layout.widget_world_clock_analog_square;}if(tall)return R.layout.widget_world_clock_digital_tall;if(wide)return R.layout.widget_world_clock_digital_wide;return R.layout.widget_world_clock_digital_square;}

    static void updateAppWidget(Context c,AppWidgetManager manager,int appWidgetId){
        String zone=loadZone(c,appWidgetId),style=loadStyle(c,appWidgetId),label=loadLabel(c,appWidgetId);boolean analog="analog".equals(style)&&Build.VERSION.SDK_INT>=31;boolean h24=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE).getBoolean("use_24h",true);
        RemoteViews rv=new RemoteViews(c.getPackageName(),chooseLayout(analog,width(manager,appWidgetId),height(manager,appWidgetId)));rv.setTextViewText(R.id.widget_city,label);rv.setTextViewText(R.id.widget_zone,zone);rv.setString(R.id.widget_date,"setTimeZone",zone);rv.setCharSequence(R.id.widget_date,"setFormat24Hour","M/d EEE");rv.setCharSequence(R.id.widget_date,"setFormat12Hour","M/d EEE");
        if(analog)rv.setString(R.id.widget_analog,"setTimeZone",zone);else{rv.setString(R.id.widget_time,"setTimeZone",zone);String timeFmt=h24?"HH:mm":"hh:mm a";rv.setCharSequence(R.id.widget_time,"setFormat24Hour",timeFmt);rv.setCharSequence(R.id.widget_time,"setFormat12Hour",timeFmt);}
        Intent open=new Intent(c,ClockFaceActivity.class).putExtra("clock_mode","world").putExtra("zone",zone).setData(Uri.parse("wakeguard://widget/world/"+appWidgetId));PendingIntent pi=PendingIntent.getActivity(c,appWidgetId,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);rv.setOnClickPendingIntent(R.id.widget_root,pi);manager.updateAppWidget(appWidgetId,rv);
    }
    static void refreshAll(Context c){try{AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,WorldClockWidgetProvider.class));for(int id:ids)updateAppWidget(c,m,id);}catch(Throwable ignored){}}
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)updateAppWidget(c,m,id);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){updateAppWidget(c,m,id);}
    @Override public void onDeleted(Context c,int[] ids){android.content.SharedPreferences.Editor e=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();for(int id:ids){e.remove(KEY_ZONE+id);e.remove(KEY_STYLE+id);e.remove(KEY_LABEL+id);}e.apply();}
}
'''
(JAVA/'WorldClockWidgetProvider.java').write_text(provider,encoding='utf-8')

config = r'''package jp.wakeguard.alarm;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldClockWidgetConfigActivity extends Activity {
    private int appWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private final ArrayList<String> visible=new ArrayList<>();
    private BaseAdapter adapter;private String selectedZone,selectedLabel,selectedStyle="digital";private Button digitalButton,analogButton;private EditText search;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final AtomicInteger generation=new AtomicInteger();private final Handler main=new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.prepareActivity(this);setResult(RESULT_CANCELED);appWidgetId=getIntent()==null?AppWidgetManager.INVALID_APPWIDGET_ID:getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);if(appWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}selectedZone=WorldClockWidgetProvider.loadZone(this,appWidgetId);selectedLabel=WorldClockWidgetProvider.loadLabel(this,appWidgetId);selectedStyle=WorldClockWidgetProvider.loadStyle(this,appWidgetId);if(Build.VERSION.SDK_INT<31)selectedStyle="digital";buildUi();request("");}
    @Override protected void onDestroy(){generation.incrementAndGet();worker.shutdownNow();super.onDestroy();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);root.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,18));root.addView(Ui.title(this,"世界時計ウィジェット",26));root.addView(Ui.text(this,"正式な全タイムゾーン + 世界の主要都市名から選べます",12,Ui.MUTED),Ui.gapTop(this,4));
        TextView styleLabel=Ui.text(this,I18n.tr(this,"表示")+" · "+I18n.tr(this,"デジタル / アナログ"),13,Ui.MUTED);styleLabel.setTypeface(null,Typeface.BOLD);root.addView(styleLabel,Ui.gapTop(this,14));LinearLayout styles=new LinearLayout(this);styles.setOrientation(LinearLayout.HORIZONTAL);digitalButton=Ui.button(this,"デジタル",false);analogButton=Ui.button(this,"アナログ",false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);styles.addView(digitalButton,bp);LinearLayout.LayoutParams bp2=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);bp2.setMarginStart(Ui.dp(this,8));styles.addView(analogButton,bp2);root.addView(styles,Ui.gapTop(this,6));digitalButton.setOnClickListener(v->{selectedStyle="digital";refreshStyleButtons();});analogButton.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=31){selectedStyle="analog";refreshStyleButtons();}});if(Build.VERSION.SDK_INT<31){analogButton.setEnabled(false);analogButton.setAlpha(.45f);}refreshStyleButtons();
        TextView sub=Ui.text(this,"都市名またはタイムゾーンを検索",14,Ui.MUTED);root.addView(sub,Ui.gapTop(this,14));search=new EditText(this);search.setSingleLine(true);search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED_2);search.setHint("Seattle / シアトル / America/Los_Angeles");search.setTextSize(16);search.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));search.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);root.addView(search,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));
        ListView list=new ListView(this);list.setDividerHeight(0);list.setBackgroundColor(Ui.BG);list.setClipToPadding(false);list.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));adapter=new BaseAdapter(){public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}public View getView(int pos,View convert,ViewGroup parent){LinearLayout row;TextView city,zone;if(convert instanceof LinearLayout&&convert.getTag() instanceof TextView[]){row=(LinearLayout)convert;TextView[] h=(TextView[])row.getTag();city=h[0];zone=h[1];}else{row=new LinearLayout(WorldClockWidgetConfigActivity.this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(WorldClockWidgetConfigActivity.this,14),Ui.dp(WorldClockWidgetConfigActivity.this,10),Ui.dp(WorldClockWidgetConfigActivity.this,14),Ui.dp(WorldClockWidgetConfigActivity.this,10));city=Ui.text(WorldClockWidgetConfigActivity.this,"",16,Ui.TEXT);city.setTypeface(null,Typeface.BOLD);zone=Ui.text(WorldClockWidgetConfigActivity.this,"",11,Ui.MUTED);row.addView(city);row.addView(zone,Ui.gapTop(WorldClockWidgetConfigActivity.this,2));row.setTag(new TextView[]{city,zone});}String item=visible.get(pos),z=WorldCityCatalog.zoneOf(item),fallback=WorldClockWidgetProvider.zoneLabel(WorldClockWidgetConfigActivity.this,z),label=WorldCityCatalog.labelOf(item,fallback),cc=WorldCityCatalog.countryOf(item,"");boolean sel=z.equals(selectedZone)&&label.equals(selectedLabel);city.setText((sel?"✓  ":"")+label);String detail=(cc.isEmpty()?"":new Locale("",cc).getDisplayCountry(I18n.locale(WorldClockWidgetConfigActivity.this))+"  ·  ")+z;zone.setText(detail);row.setBackground(Ui.roundStroke(Ui.SURFACE,sel?Ui.ACCENT:Ui.BORDER,14,WorldClockWidgetConfigActivity.this));row.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,Ui.dp(WorldClockWidgetConfigActivity.this,66)));return row;}};list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));list.setOnItemClickListener((p,v,pos,id)->{String item=visible.get(pos),z=WorldCityCatalog.zoneOf(item);selectedZone=z;selectedLabel=WorldCityCatalog.labelOf(item,WorldClockWidgetProvider.zoneLabel(this,z));adapter.notifyDataSetChanged();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){request(s==null?"":s.toString());}public void afterTextChanged(Editable e){}});Button save=Ui.button(this,"保存",true);save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));setContentView(root);Ui.applySystemBarInsets(this,root);
    }
    private void request(String raw){final int g=generation.incrementAndGet();final String q=WorldCityCatalog.normalize(raw);worker.execute(()->{LinkedHashSet<String> out=new LinkedHashSet<>();if(q.isEmpty()){out.add(WorldCityCatalog.encode(selectedLabel,selectedZone,""));out.add(ZoneId.systemDefault().getId());for(String z:new String[]{"Asia/Tokyo","Asia/Seoul","America/New_York","America/Los_Angeles","Europe/London","Europe/Paris","Asia/Singapore","Australia/Sydney"})out.add(z);for(WorldCityCatalog.Entry e:WorldCityCatalog.popular(this,28))out.add(WorldCityCatalog.encode(e));}else{for(WorldCityCatalog.Entry e:WorldCityCatalog.search(this,q,70))out.add(WorldCityCatalog.encode(e));for(String z:ZoneId.getAvailableZoneIds()){if(!z.contains("/")||z.startsWith("Etc/"))continue;String n=WorldCityCatalog.normalize(z+" "+WorldClockWidgetProvider.zoneLabel(this,z));if(n.contains(q)){out.add(z);if(out.size()>=100)break;}}}ArrayList<String> done=new ArrayList<>(out);main.post(()->{if(g!=generation.get())return;visible.clear();visible.addAll(done);adapter.notifyDataSetChanged();});});}
    private void refreshStyleButtons(){boolean d=!"analog".equals(selectedStyle);digitalButton.setBackground(d?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));digitalButton.setTextColor(d?0xFF0D1B2A:Ui.TEXT);boolean a=!d;analogButton.setBackground(a?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));analogButton.setTextColor(a?0xFF0D1B2A:Ui.TEXT);}
    private void save(){try{ZoneId.of(selectedZone);}catch(Throwable t){selectedZone=ZoneId.systemDefault().getId();selectedLabel=WorldClockWidgetProvider.zoneLabel(this,selectedZone);}WorldClockWidgetProvider.saveConfig(this,appWidgetId,selectedZone,selectedStyle,selectedLabel);WorldClockWidgetProvider.updateAppWidget(this,AppWidgetManager.getInstance(this),appWidgetId);Intent result=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,appWidgetId);setResult(RESULT_OK,result);finish();}
}
'''
(JAVA/'WorldClockWidgetConfigActivity.java').write_text(config,encoding='utf-8')

multi = r'''package jp.wakeguard.alarm;
import android.appwidget.*;import android.content.*;import android.widget.RemoteViews;import java.time.ZoneId;import java.util.*;
public class MultiWorldClockWidgetProvider extends AppWidgetProvider{
 static void update(Context c,AppWidgetManager m,int id){RemoteViews r=new RemoteViews(c.getPackageName(),R.layout.widget_multi_world_clock);String raw=c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE).getString("world_zones","");ArrayList<String> z=new ArrayList<>();if(raw!=null)for(String x:raw.split("\\n"))try{ZoneId.of(x);if(!z.contains(x))z.add(x);}catch(Throwable ignored){}for(String d:new String[]{"Asia/Tokyo","Asia/Seoul","America/New_York","Europe/London"})if(z.size()<4&&!z.contains(d))z.add(d);int[] city={R.id.city1,R.id.city2,R.id.city3,R.id.city4},time={R.id.time1,R.id.time2,R.id.time3,R.id.time4};for(int i=0;i<4;i++){String x=z.get(i),fallback=WorldClockWidgetProvider.zoneLabel(c,x);r.setTextViewText(city[i],WorldCityCatalog.savedWorldLabel(c,x,fallback));r.setString(time[i],"setTimeZone",x);r.setCharSequence(time[i],"setFormat24Hour","HH:mm");r.setCharSequence(time[i],"setFormat12Hour","hh:mm a");}r.setOnClickPendingIntent(R.id.widget_root,WidgetSuite.open(c,ClockActivity.class,"mode","world",22000+id));m.updateAppWidget(id,r);}public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)update(c,m,id);}public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle b){update(c,m,id);}}
'''
(JAVA/'MultiWorldClockWidgetProvider.java').write_text(multi,encoding='utf-8')

# Patch ClockActivity minimally: display and save alias city labels; picker accepts city alias items.
p = JAVA/'ClockActivity.java'
s = p.read_text(encoding='utf-8')

s=s.replace('TextView name=text(friendlyZoneName(zoneId),17,Ui.TEXT);', 'TextView name=text(WorldCityCatalog.savedWorldLabel(this,zoneId,friendlyZoneName(zoneId)),17,Ui.TEXT);')
s=s.replace('remove.setOnClickListener(v->{LinkedHashSet<String> zones=loadZones();zones.remove(zoneId);saveZones(zones);showMode("world");});', 'remove.setOnClickListener(v->{LinkedHashSet<String> zones=loadZones();zones.remove(zoneId);saveZones(zones);WorldCityCatalog.saveWorldLabel(this,zoneId,null);showMode("world");});')

old='''                String id=getItem(position);\n                String cityLabel=displayCityCache.get(id);if(cityLabel==null){cityLabel=friendlyZoneName(id);displayCityCache.put(id,cityLabel);}city.setText(cityLabel);\n                String code=countryCache.get(id);if(code==null||code.isEmpty())code=countryCodeForZone(id);String country=countryLabelCache.get(code);if(country==null||country.isEmpty())country=countryName(code,I18n.locale(ClockActivity.this));countryView.setText(country==null||country.isEmpty()?code:country);\n                try{ZonedDateTime z=ZonedDateTime.now(ZoneId.of(id));nowText.setText(z.format(DateTimeFormatter.ofPattern(p().getBoolean(KEY_24H,true)?"HH:mm":"hh:mm a",I18n.locale(ClockActivity.this))));detail.setText("UTC"+formatOffset(z.getOffset().getTotalSeconds())+"  ·  "+id);}catch(Throwable t){nowText.setText("");detail.setText(id);}\n'''
new='''                String item=getItem(position);\n                String id=WorldCityCatalog.zoneOf(item);\n                String cityLabel=WorldCityCatalog.labelOf(item,friendlyZoneName(id));city.setText(cityLabel);\n                String code=WorldCityCatalog.countryOf(item,countryCache.getOrDefault(id,""));if(code==null||code.isEmpty())code=countryCodeForZone(id);String country=countryLabelCache.get(code);if(country==null||country.isEmpty())country=countryName(code,I18n.locale(ClockActivity.this));countryView.setText(country==null||country.isEmpty()?code:country);\n                try{ZonedDateTime z=ZonedDateTime.now(ZoneId.of(id));nowText.setText(z.format(DateTimeFormatter.ofPattern(p().getBoolean(KEY_24H,true)?"HH:mm":"hh:mm a",I18n.locale(ClockActivity.this))));detail.setText("UTC"+formatOffset(z.getOffset().getTotalSeconds())+"  ·  "+id);}catch(Throwable t){nowText.setText("");detail.setText(id);}\n'''
if old not in s: raise SystemExit('Clock picker adapter block not found')
s=s.replace(old,new)

old='''                ArrayList<String> out=new ArrayList<>();int limit=q.isEmpty()?28:44;for(String id:results){out.add(id);if(out.size()>=limit)break;}\n'''
new='''                ArrayList<String> out=new ArrayList<>();int limit=q.isEmpty()?34:70;\n                if(q.isEmpty()){for(WorldCityCatalog.Entry e:WorldCityCatalog.popular(this,12)){out.add(WorldCityCatalog.encode(e));if(out.size()>=limit)break;}}\n                else{for(WorldCityCatalog.Entry e:WorldCityCatalog.search(this,q,52)){out.add(WorldCityCatalog.encode(e));if(out.size()>=limit)break;}}\n                for(String id:results){if(out.size()>=limit)break;out.add(id);}\n'''
if old not in s: raise SystemExit('Clock final results block not found')
s=s.replace(old,new)

old='''        list.setOnItemClickListener((a,v,pos,id)->{String z=filtered.get(pos);LinkedHashSet<String> zones=loadZones();zones.add(z);saveZones(zones);dialog.dismiss();showMode("world");});\n'''
new='''        list.setOnItemClickListener((a,v,pos,id)->{String item=filtered.get(pos),z=WorldCityCatalog.zoneOf(item),label=WorldCityCatalog.labelOf(item,friendlyZoneName(z));LinkedHashSet<String> zones=loadZones();zones.add(z);saveZones(zones);WorldCityCatalog.saveWorldLabel(this,z,label);dialog.dismiss();showMode("world");});\n'''
if old not in s: raise SystemExit('Clock click block not found')
s=s.replace(old,new)

# Improve picker description to make the new behavior explicit.
s=s.replace('国名が曖昧でも、都市・地域・だいたいの名前から候補を探せます','SeattleのようにIANAの代表都市名にない都市も検索できます。正式な全タイムゾーンも表示します')
s=s.replace('オフライン検索を常に使い、接続中はオンライン候補も自動で追加します','世界の主要都市データをオフライン検索し、正式なIANAタイムゾーンもすべて検索します')
p.write_text(s,encoding='utf-8')

# Version bump.
g=GRADLE.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s*=\s*\d+','versionCode = 68',g,count=1)
g=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "1.5.7"',g,count=1)
GRADLE.write_text(g,encoding='utf-8')

# Attribution file for bundled GeoNames-derived city catalog.
license_text='''WakeGuard world-city timezone catalog\n\nCity names, alternate names, country codes and IANA timezone IDs are derived from GeoNames Gazetteer data (cities15000 snapshot used at build time).\nGeoNames: https://www.geonames.org/\nLicense: Creative Commons Attribution 4.0 International (CC BY 4.0)\nhttps://creativecommons.org/licenses/by/4.0/\n'''
(ASSETS/'GEONAMES_ATTRIBUTION.txt').write_text(license_text,encoding='utf-8')

# Assertions for build-time verification.
assert 'WorldCityCatalog.search(this,q,52)' in p.read_text(encoding='utf-8')
assert 'versionName = "1.5.7"' in GRADLE.read_text(encoding='utf-8')
assert out_asset.exists() and out_asset.stat().st_size>100
print('WakeGuard v1.5.7 city alias patch applied')
