from pathlib import Path
import re

ROOT=Path('WakeGuard')
JAVA=ROOT/'app/src/main/java/jp/wakeguard/alarm'
GRADLE=ROOT/'app/build.gradle.kts'

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
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldClockWidgetConfigActivity extends Activity {
    private int appWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private final ArrayList<String> visible=new ArrayList<>();
    private BaseAdapter adapter;
    private String selectedZone,selectedLabel,selectedStyle="digital";
    private Button digitalButton,analogButton;
    private EditText search;
    private ListView list;
    private TextView searchStatus;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private final Handler main=new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);Ui.prepareActivity(this);setResult(RESULT_CANCELED);
        appWidgetId=getIntent()==null?AppWidgetManager.INVALID_APPWIDGET_ID:getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(appWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        selectedZone=WorldClockWidgetProvider.loadZone(this,appWidgetId);
        selectedLabel=WorldClockWidgetProvider.loadLabel(this,appWidgetId);
        selectedStyle=WorldClockWidgetProvider.loadStyle(this,appWidgetId);
        if(Build.VERSION.SDK_INT<31)selectedStyle="digital";
        buildUi();request("");
    }
    @Override protected void onDestroy(){generation.incrementAndGet();worker.shutdownNow();super.onDestroy();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);root.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,18));
        root.addView(Ui.title(this,"世界時計ウィジェット",26));
        root.addView(Ui.text(this,"都市名で検索して、その都市の正しいタイムゾーンを選べます",12,Ui.MUTED),Ui.gapTop(this,4));

        TextView styleLabel=Ui.text(this,I18n.tr(this,"表示")+" · "+I18n.tr(this,"デジタル / アナログ"),13,Ui.MUTED);styleLabel.setTypeface(null,Typeface.BOLD);root.addView(styleLabel,Ui.gapTop(this,14));
        LinearLayout styles=new LinearLayout(this);styles.setOrientation(LinearLayout.HORIZONTAL);
        digitalButton=Ui.button(this,"デジタル",false);analogButton=Ui.button(this,"アナログ",false);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);styles.addView(digitalButton,bp);
        LinearLayout.LayoutParams bp2=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);bp2.setMarginStart(Ui.dp(this,8));styles.addView(analogButton,bp2);
        root.addView(styles,Ui.gapTop(this,6));
        digitalButton.setOnClickListener(v->{selectedStyle="digital";refreshStyleButtons();});
        analogButton.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=31){selectedStyle="analog";refreshStyleButtons();}});
        if(Build.VERSION.SDK_INT<31){analogButton.setEnabled(false);analogButton.setAlpha(.45f);}refreshStyleButtons();

        TextView sub=Ui.text(this,"都市名またはタイムゾーンを検索",14,Ui.MUTED);root.addView(sub,Ui.gapTop(this,14));
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED_2);search.setHint("例：シアトル / Seattle / Los_Angeles");search.setTextSize(16);search.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));search.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);root.addView(search,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));
        searchStatus=Ui.text(this,"",11,Ui.MUTED);root.addView(searchStatus,Ui.gapTop(this,6));

        list=new ListView(this);list.setDividerHeight(0);list.setBackgroundColor(Ui.BG);list.setClipToPadding(false);list.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,8));
        adapter=new BaseAdapter(){
            public int getCount(){return visible.size();}
            public Object getItem(int p){return visible.get(p);}
            public long getItemId(int p){return p;}
            public View getView(int pos,View convert,ViewGroup parent){
                LinearLayout row;TextView city,zone;
                if(convert instanceof LinearLayout&&convert.getTag() instanceof TextView[]){row=(LinearLayout)convert;TextView[] h=(TextView[])row.getTag();city=h[0];zone=h[1];}
                else{row=new LinearLayout(WorldClockWidgetConfigActivity.this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(WorldClockWidgetConfigActivity.this,14),Ui.dp(WorldClockWidgetConfigActivity.this,10),Ui.dp(WorldClockWidgetConfigActivity.this,14),Ui.dp(WorldClockWidgetConfigActivity.this,10));city=Ui.text(WorldClockWidgetConfigActivity.this,"",16,Ui.TEXT);city.setTypeface(null,Typeface.BOLD);zone=Ui.text(WorldClockWidgetConfigActivity.this,"",11,Ui.MUTED);row.addView(city);row.addView(zone,Ui.gapTop(WorldClockWidgetConfigActivity.this,2));row.setTag(new TextView[]{city,zone});}
                String item=visible.get(pos),z=WorldCityCatalog.zoneOf(item),fallback=WorldClockWidgetProvider.zoneLabel(WorldClockWidgetConfigActivity.this,z),label=WorldCityCatalog.labelOf(item,fallback),cc=WorldCityCatalog.countryOf(item,"");
                boolean sel=z.equals(selectedZone)&&WorldCityCatalog.normalize(label).equals(WorldCityCatalog.normalize(selectedLabel));
                city.setText((sel?"✓  ":"")+label);
                String detail=(cc.isEmpty()?"":new Locale("",cc).getDisplayCountry(I18n.locale(WorldClockWidgetConfigActivity.this))+"  ·  ")+z;zone.setText(detail);
                row.setBackground(Ui.roundStroke(Ui.SURFACE,sel?Ui.ACCENT:Ui.BORDER,14,WorldClockWidgetConfigActivity.this));
                row.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,Ui.dp(WorldClockWidgetConfigActivity.this,66)));return row;
            }
        };
        list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,pos,id)->{String item=visible.get(pos),z=WorldCityCatalog.zoneOf(item);selectedZone=z;selectedLabel=WorldCityCatalog.labelOf(item,WorldClockWidgetProvider.zoneLabel(this,z));adapter.notifyDataSetChanged();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){request(s==null?"":s.toString());}public void afterTextChanged(Editable e){}});
        Button save=Ui.button(this,"保存",true);save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));
        setContentView(root);Ui.applySystemBarInsets(this,root);
    }

    private String keyOf(String item){
        String z=WorldCityCatalog.zoneOf(item);String label=WorldCityCatalog.labelOf(item,WorldClockWidgetProvider.zoneLabel(this,z));
        return WorldCityCatalog.normalize(label)+"|"+z;
    }
    private void addUnique(ArrayList<String> out,HashSet<String> keys,String item){
        if(item==null||item.trim().isEmpty())return;String k=keyOf(item);if(keys.add(k))out.add(item);
    }
    private ArrayList<String> recommended(){
        ArrayList<String> out=new ArrayList<>();HashSet<String> keys=new HashSet<>();
        addUnique(out,keys,WorldCityCatalog.encode(selectedLabel,selectedZone,""));
        String[][] cities={
            {"東京","Asia/Tokyo","JP"},{"ソウル","Asia/Seoul","KR"},{"シアトル","America/Los_Angeles","US"},{"ロサンゼルス","America/Los_Angeles","US"},{"サンフランシスコ","America/Los_Angeles","US"},{"バンクーバー","America/Vancouver","CA"},
            {"ニューヨーク","America/New_York","US"},{"ワシントンD.C.","America/New_York","US"},{"ボストン","America/New_York","US"},{"トロント","America/Toronto","CA"},{"シカゴ","America/Chicago","US"},{"デンバー","America/Denver","US"},{"ホノルル","Pacific/Honolulu","US"},
            {"ロンドン","Europe/London","GB"},{"パリ","Europe/Paris","FR"},{"ベルリン","Europe/Berlin","DE"},{"ローマ","Europe/Rome","IT"},{"マドリード","Europe/Madrid","ES"},{"モスクワ","Europe/Moscow","RU"},
            {"北京","Asia/Shanghai","CN"},{"上海","Asia/Shanghai","CN"},{"香港","Asia/Hong_Kong","HK"},{"台北","Asia/Taipei","TW"},{"シンガポール","Asia/Singapore","SG"},{"バンコク","Asia/Bangkok","TH"},{"デリー","Asia/Kolkata","IN"},{"ドバイ","Asia/Dubai","AE"},
            {"シドニー","Australia/Sydney","AU"},{"メルボルン","Australia/Melbourne","AU"},{"オークランド","Pacific/Auckland","NZ"},{"サンパウロ","America/Sao_Paulo","BR"},{"メキシコシティ","America/Mexico_City","MX"},{"カイロ","Africa/Cairo","EG"},{"ヨハネスブルグ","Africa/Johannesburg","ZA"}
        };
        for(String[] c:cities)addUnique(out,keys,WorldCityCatalog.encode(c[0],c[1],c[2]));
        return out;
    }

    private void applyResults(int g,ArrayList<String> done,String status){
        if(g!=generation.get())return;visible.clear();visible.addAll(done);adapter.notifyDataSetChanged();searchStatus.setText(status);if(list!=null)list.setSelection(0);
    }
    private void request(String raw){
        final int g=generation.incrementAndGet();final String q=WorldCityCatalog.normalize(raw);
        if(q.isEmpty()){
            applyResults(g,recommended(),"おすすめのみ表示中。都市名を入力すると世界の都市候補を検索します");
            return;
        }
        searchStatus.setText("検索中…");
        worker.execute(()->{
            ArrayList<String> out=new ArrayList<>();HashSet<String> keys=new HashSet<>();
            ArrayList<WorldCityCatalog.Entry> cities=WorldCityCatalog.search(this,q,70);
            for(WorldCityCatalog.Entry e:cities)addUnique(out,keys,WorldCityCatalog.encode(e));
            if(raw.contains("/")||cities.isEmpty()){
                for(String z:ZoneId.getAvailableZoneIds()){
                    if(!z.contains("/")||z.startsWith("Etc/"))continue;
                    String n=WorldCityCatalog.normalize(z+" "+WorldClockWidgetProvider.zoneLabel(this,z));
                    if(n.contains(q)){addUnique(out,keys,z);if(out.size()>=100)break;}
                }
            }
            ArrayList<String> done=new ArrayList<>(out);
            main.post(()->applyResults(g,done,done.isEmpty()?"該当する都市・タイムゾーンがありません":done.size()+"件の候補"));
        });
    }

    private void refreshStyleButtons(){boolean d=!"analog".equals(selectedStyle);digitalButton.setBackground(d?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));digitalButton.setTextColor(d?0xFF0D1B2A:Ui.TEXT);boolean a=!d;analogButton.setBackground(a?Ui.round(Ui.ACCENT,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));analogButton.setTextColor(a?0xFF0D1B2A:Ui.TEXT);}
    private void save(){try{ZoneId.of(selectedZone);}catch(Throwable t){selectedZone=ZoneId.systemDefault().getId();selectedLabel=WorldClockWidgetProvider.zoneLabel(this,selectedZone);}WorldClockWidgetProvider.saveConfig(this,appWidgetId,selectedZone,selectedStyle,selectedLabel);WorldClockWidgetProvider.updateAppWidget(this,AppWidgetManager.getInstance(this),appWidgetId);Intent result=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,appWidgetId);setResult(RESULT_OK,result);finish();}
}
'''
(JAVA/'WorldClockWidgetConfigActivity.java').write_text(config,encoding='utf-8')

gradle=GRADLE.read_text(encoding='utf-8')
gradle=re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 69', gradle, count=1)
gradle=re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.5.8"', gradle, count=1)
GRADLE.write_text(gradle,encoding='utf-8')
print('WakeGuard v1.5.8 widget city picker UX patch applied')
