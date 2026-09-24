package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TimeLogActivity extends Activity {
    private static final String PREF="time_log_v1";
    private static final String KEY_FOLDERS="folders";
    private static final String KEY_ENTRIES="entries";
    private final ArrayList<Folder> folders=new ArrayList<>();
    private final ArrayList<Entry> entries=new ArrayList<>();
    private LinearLayout body;
    private TextView title;
    private Button action;
    private String selectedFolderId=null;
    private final ZoneId zone=ZoneId.systemDefault();

    static final class Folder {
        String id,name; long createdAt;
        Folder(String id,String name,long createdAt){this.id=id;this.name=name;this.createdAt=createdAt;}
    }
    static final class Entry {
        String id,folderId; long start,end;
        Entry(String id,String folderId,long start,long end){this.id=id;this.folderId=folderId;this.start=start;this.end=end;}
    }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this); load(); buildShell(); showFolders();
    }

    private android.content.SharedPreferences p(){return getSharedPreferences(PREF,MODE_PRIVATE);}
    private TextView text(String s,float sp,int color){return Ui.text(this,s,sp,color);}

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(Ui.screenGradient(this));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,8));
        Button back=Ui.ghostButton(this,"‹");back.setTextSize(30);back.setOnClickListener(v->{if(selectedFolderId!=null){selectedFolderId=null;showFolders();}else finish();});top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        title=Ui.title(this,"時間記録",27);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        action=Ui.ghostButton(this,"＋");action.setTextSize(26);top.addView(action,new LinearLayout.LayoutParams(Ui.dp(this,54),Ui.dp(this,48)));
        root.addView(top);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,36));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void showFolders(){
        selectedFolderId=null;body.removeAllViews();title.setText("時間記録");action.setText("＋");action.setOnClickListener(v->showFolderDialog(null));
        TextView intro=text("フォルダーごとに時間を記録・集計",13,Ui.MUTED);intro.setPadding(0,0,0,Ui.dp(this,14));body.addView(intro);
        if(folders.isEmpty()){
            TextView empty=text("まだフォルダーがありません。右上の＋から作成できます。",15,Ui.MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,Ui.dp(this,80),0,0);body.addView(empty);return;
        }
        ArrayList<Folder> sorted=new ArrayList<>(folders);sorted.sort((a,b)->Long.compare(b.createdAt,a.createdAt));
        for(Folder f:sorted) addFolderCard(f);
    }

    private void addFolderCard(Folder f){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,14));card.setBackground(Ui.burnCard(this,false));
        TextView name=Ui.title(this,f.name,20);card.addView(name);
        LocalDate now=LocalDate.now();
        long today=durationForDay(f.id,now);long week=durationForRange(f.id,weekStart(now),weekStart(now).plusDays(7));long total=totalDuration(f.id);
        TextView stats=text("今日  "+fmt(today)+"    今週  "+fmt(week)+"\n合計  "+fmt(total)+"    1日平均  "+fmt(averagePerRecordedDay(f.id)),13,Ui.MUTED);stats.setPadding(0,Ui.dp(this,7),0,0);card.addView(stats);
        int count=entryCount(f.id);TextView n=text(count+"件",12,Ui.MUTED_2);n.setGravity(Gravity.END);card.addView(n);
        card.setOnClickListener(v->{selectedFolderId=f.id;showFolder(f.id);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(this,10));body.addView(card,lp);
    }

    private void showFolder(String folderId){
        Folder f=findFolder(folderId);if(f==null){showFolders();return;}selectedFolderId=folderId;body.removeAllViews();title.setText(f.name);action.setText("＋");action.setOnClickListener(v->showEntryDialog(f,null));
        addStatsGrid(f);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,10));
        Button rename=Ui.ghostButton(this,"名称変更");rename.setOnClickListener(v->showFolderDialog(f));actions.addView(rename,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        Button delete=Ui.ghostButton(this,"削除");delete.setTextColor(Ui.DANGER);delete.setOnClickListener(v->confirmDeleteFolder(f));actions.addView(delete,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));body.addView(actions);
        ArrayList<Entry> list=entriesFor(folderId);list.sort((a,b)->Long.compare(b.start,a.start));
        if(list.isEmpty()){
            TextView empty=text("まだ記録がありません。右上の＋から開始・終了時刻を追加できます。",14,Ui.MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,Ui.dp(this,60),0,0);body.addView(empty);return;
        }
        LinkedHashMap<LocalDate,ArrayList<Entry>> groups=new LinkedHashMap<>();
        for(Entry e:list){LocalDate d=Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate();groups.computeIfAbsent(d,k->new ArrayList<>()).add(e);}
        DateTimeFormatter df=DateTimeFormatter.ofPattern("yyyy/MM/dd (E)",Locale.JAPAN);
        for(Map.Entry<LocalDate,ArrayList<Entry>> g:groups.entrySet()){
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,7));
            TextView d=Ui.title(this,g.getKey().format(df),16);head.addView(d,new LinearLayout.LayoutParams(0,-2,1));
            TextView sum=text("合計 "+fmt(durationForDay(folderId,g.getKey())),13,Ui.ACCENT_2);head.addView(sum);body.addView(head);
            for(Entry e:g.getValue()) addEntryRow(f,e);
        }
    }

    private void addStatsGrid(Folder f){
        GridLayout grid=new GridLayout(this);grid.setColumnCount(2);grid.setUseDefaultMargins(false);
        LocalDate today=LocalDate.now();
        addStat(grid,"合計",totalDuration(f.id),0);addStat(grid,"今日",durationForDay(f.id,today),1);addStat(grid,"今週",durationForRange(f.id,weekStart(today),weekStart(today).plusDays(7)),2);addStat(grid,"1日平均",averagePerRecordedDay(f.id),3);
        body.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addStat(GridLayout grid,String label,long value,int index){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));c.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,12,this));
        TextView l=text(label,12,Ui.MUTED);c.addView(l);TextView v=Ui.title(this,fmt(value),20);v.setPadding(0,Ui.dp(this,3),0,0);c.addView(v);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.columnSpec=GridLayout.spec(index%2,1f);lp.rowSpec=GridLayout.spec(index/2);lp.setMargins(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4));grid.addView(c,lp);
    }

    private void addEntryRow(Folder f,Entry e){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(Ui.dp(this,14),Ui.dp(this,11),Ui.dp(this,8),Ui.dp(this,11));card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,10,this));
        ZonedDateTime a=Instant.ofEpochMilli(e.start).atZone(zone),b=Instant.ofEpochMilli(e.end).atZone(zone);DateTimeFormatter tf=DateTimeFormatter.ofPattern("HH:mm");
        String times=a.format(tf)+" – "+b.format(tf)+(a.toLocalDate().equals(b.toLocalDate())?"":"  翌日");
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);TextView t=Ui.title(this,times,17);info.addView(t);TextView dur=text(fmt(e.end-e.start),14,Ui.ACCENT_2);dur.setPadding(0,Ui.dp(this,3),0,0);info.addView(dur);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=Ui.ghostButton(this,"編集");edit.setOnClickListener(v->showEntryDialog(f,e));card.addView(edit,new LinearLayout.LayoutParams(Ui.dp(this,70),Ui.dp(this,44)));
        card.setOnLongClickListener(v->{confirmDeleteEntry(f,e);return true;});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(this,7));body.addView(card,lp);
    }

    private void showFolderDialog(Folder existing){
        EditText name=new EditText(this);name.setHint("例：勉強、バイト、運動");name.setSingleLine(true);name.setText(existing==null?"":existing.name);name.setSelectAllOnFocus(true);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(existing==null?"フォルダーを作成":"フォルダー名を変更").setView(name).setNegativeButton("キャンセル",null).setPositiveButton("保存",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("名前を入力してください");return;}if(existing==null)folders.add(new Folder(UUID.randomUUID().toString(),n,System.currentTimeMillis()));else existing.name=n;save();d.dismiss();if(existing==null)showFolders();else showFolder(existing.id);}));d.show();
    }

    private void showEntryDialog(Folder folder,Entry existing){
        Calendar seed=Calendar.getInstance();if(existing!=null)seed.setTimeInMillis(existing.start);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,22),Ui.dp(this,8),Ui.dp(this,22),0);
        Button date=Ui.ghostButton(this,"");Button start=Ui.ghostButton(this,"");Button end=Ui.ghostButton(this,"");box.addView(date);box.addView(start);box.addView(end);
        final LocalDate[] d={Instant.ofEpochMilli(seed.getTimeInMillis()).atZone(zone).toLocalDate()};
        final LocalTime[] st={existing==null?LocalTime.of(seed.get(Calendar.HOUR_OF_DAY),seed.get(Calendar.MINUTE)):Instant.ofEpochMilli(existing.start).atZone(zone).toLocalTime().withSecond(0).withNano(0)};
        final LocalTime[] en={existing==null?st[0].plusHours(1):Instant.ofEpochMilli(existing.end).atZone(zone).toLocalTime().withSecond(0).withNano(0)};
        Runnable labels=()->{date.setText("日付  "+d[0].format(DateTimeFormatter.ofPattern("yyyy/MM/dd")));start.setText("開始  "+st[0].format(DateTimeFormatter.ofPattern("HH:mm")));end.setText("終了  "+en[0].format(DateTimeFormatter.ofPattern("HH:mm"))+( !en[0].isAfter(st[0]) ? "  (翌日)":""));};labels.run();
        date.setOnClickListener(v->new DatePickerDialog(this,(x,y,m,day)->{d[0]=LocalDate.of(y,m+1,day);labels.run();},d[0].getYear(),d[0].getMonthValue()-1,d[0].getDayOfMonth()).show());
        start.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{st[0]=LocalTime.of(h,m);labels.run();},st[0].getHour(),st[0].getMinute(),true).show());
        end.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{en[0]=LocalTime.of(h,m);labels.run();},en[0].getHour(),en[0].getMinute(),true).show());
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(existing==null?"時間を追加":"時間を編集").setView(box).setNegativeButton("キャンセル",null).setPositiveButton("保存",null).create();
        dlg.setOnShowListener(x->{dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ZonedDateTime a=ZonedDateTime.of(d[0],st[0],zone);LocalDate endDate=en[0].isAfter(st[0])?d[0]:d[0].plusDays(1);ZonedDateTime b=ZonedDateTime.of(endDate,en[0],zone);long sm=a.toInstant().toEpochMilli(),em=b.toInstant().toEpochMilli();if(em<=sm){Toast.makeText(this,"終了時刻を確認してください",Toast.LENGTH_SHORT).show();return;}if(existing==null)entries.add(new Entry(UUID.randomUUID().toString(),folder.id,sm,em));else{existing.start=sm;existing.end=em;}save();dlg.dismiss();showFolder(folder.id);});if(existing!=null){dlg.setButton(AlertDialog.BUTTON_NEUTRAL,"削除",(v,w)->{});dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Ui.DANGER);dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{entries.remove(existing);save();dlg.dismiss();showFolder(folder.id);});}});dlg.show();
    }

    private void confirmDeleteFolder(Folder f){new AlertDialog.Builder(this).setTitle("フォルダーを削除").setMessage("「"+f.name+"」と中の時間記録をすべて削除しますか？").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{folders.remove(f);entries.removeIf(e->f.id.equals(e.folderId));save();selectedFolderId=null;showFolders();}).show();}
    private void confirmDeleteEntry(Folder f,Entry e){new AlertDialog.Builder(this).setTitle("記録を削除").setMessage("この時間記録を削除しますか？").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{entries.remove(e);save();showFolder(f.id);}).show();}

    private Folder findFolder(String id){for(Folder f:folders)if(f.id.equals(id))return f;return null;}
    private ArrayList<Entry> entriesFor(String folderId){ArrayList<Entry> a=new ArrayList<>();for(Entry e:entries)if(folderId.equals(e.folderId))a.add(e);return a;}
    private int entryCount(String folderId){int n=0;for(Entry e:entries)if(folderId.equals(e.folderId))n++;return n;}
    private long totalDuration(String folderId){long n=0;for(Entry e:entries)if(folderId.equals(e.folderId))n+=Math.max(0,e.end-e.start);return n;}
    private LocalDate weekStart(LocalDate d){return d.minusDays((d.getDayOfWeek().getValue()+6)%7);}
    private long durationForDay(String folderId,LocalDate day){return durationForRange(folderId,day,day.plusDays(1));}
    private long durationForRange(String folderId,LocalDate start,LocalDate end){long a=start.atStartOfDay(zone).toInstant().toEpochMilli(),b=end.atStartOfDay(zone).toInstant().toEpochMilli(),sum=0;for(Entry e:entries){if(!folderId.equals(e.folderId))continue;long lo=Math.max(a,e.start),hi=Math.min(b,e.end);if(hi>lo)sum+=hi-lo;}return sum;}
    private long averagePerRecordedDay(String folderId){TreeSet<LocalDate> days=new TreeSet<>();for(Entry e:entries){if(!folderId.equals(e.folderId))continue;LocalDate a=Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate(),b=Instant.ofEpochMilli(Math.max(e.start,e.end-1)).atZone(zone).toLocalDate();for(LocalDate d=a;!d.isAfter(b);d=d.plusDays(1))if(durationForDay(folderId,d)>0)days.add(d);}return days.isEmpty()?0:totalDuration(folderId)/days.size();}
    private String fmt(long ms){long mins=Math.max(0,Math.round(ms/60000.0));long h=mins/60,m=mins%60;if(h==0)return m+"分";if(m==0)return h+"時間";return h+"時間"+m+"分";}

    private void load(){folders.clear();entries.clear();try{JSONArray a=new JSONArray(p().getString(KEY_FOLDERS,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);folders.add(new Folder(o.getString("id"),o.optString("name","フォルダー"),o.optLong("createdAt",0)));}}catch(Throwable ignored){}try{JSONArray a=new JSONArray(p().getString(KEY_ENTRIES,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);long s=o.optLong("start",0),e=o.optLong("end",0);if(e>s)entries.add(new Entry(o.getString("id"),o.getString("folderId"),s,e));}}catch(Throwable ignored){}}
    private void save(){try{JSONArray fa=new JSONArray();for(Folder f:folders){JSONObject o=new JSONObject();o.put("id",f.id);o.put("name",f.name);o.put("createdAt",f.createdAt);fa.put(o);}JSONArray ea=new JSONArray();for(Entry e:entries){JSONObject o=new JSONObject();o.put("id",e.id);o.put("folderId",e.folderId);o.put("start",e.start);o.put("end",e.end);ea.put(o);}p().edit().putString(KEY_FOLDERS,fa.toString()).putString(KEY_ENTRIES,ea.toString()).apply();}catch(Throwable ignored){}}
}
