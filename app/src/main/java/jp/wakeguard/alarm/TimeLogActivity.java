package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
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
    private static final String KEY_ACTIVE_FOLDER="active_folder";
    private static final String KEY_ACTIVE_START="active_start";
    private final Handler ticker=new Handler(Looper.getMainLooper());
    private TextView trackingClock;
    private Runnable trackingTick;
    private long pendingImportDurationMs=0L;
    private long pendingImportEndWallMs=0L;
    private String pendingImportLabel="ストップウォッチ";
    private boolean returnToClockWhenBack=false;
    private Button streakSegment,timeSegment;

    static final class Folder {
        String id,name; long createdAt;
        Folder(String id,String name,long createdAt){this.id=id;this.name=name;this.createdAt=createdAt;}
    }
    static final class Entry {
        String id,folderId; long start,end;
        Entry(String id,String folderId,long start,long end){this.id=id;this.folderId=folderId;this.start=start;this.end=end;}
    }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this); load();
        Intent intent=getIntent();if(intent!=null){pendingImportDurationMs=Math.max(0L,intent.getLongExtra("importDurationMs",0L));pendingImportEndWallMs=intent.getLongExtra("importEndWallMs",0L);pendingImportLabel=intent.getStringExtra("importLabel");if(pendingImportLabel==null||pendingImportLabel.trim().isEmpty())pendingImportLabel="ストップウォッチ";returnToClockWhenBack=!intent.hasExtra("record_mode");}
        buildShell(); showFolders();if(pendingImportDurationMs>0L)new Handler(Looper.getMainLooper()).post(this::showImportFolderPicker);
    }

    private android.content.SharedPreferences p(){return getSharedPreferences(PREF,MODE_PRIVATE);}
    private TextView text(String s,float sp,int color){return Ui.text(this,s,sp,color);}

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(Ui.screenGradient(this));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(Ui.dp(this,12),Ui.dp(this,13),Ui.dp(this,16),Ui.dp(this,9));
        android.widget.ImageButton back=Ui.iconButton(this,R.drawable.ic_v292_action_back,false,"戻る");back.setOnClickListener(v->handleBack());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(Ui.dp(this,7),0,0,0);
        heading.addView(Ui.overline(this,"記録"));title=Ui.title(this,"時間記録",24);heading.addView(title);
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        action=Ui.ghostButton(this,"＋");action.setTextSize(25);action.setContentDescription(I18n.tr(this,"時間を追加"));top.addView(action,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,48)));
        root.addView(top);
        addRecordSegment(root);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,36));scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(bottomNav(),new LinearLayout.LayoutParams(-1,-2));setContentView(root);Ui.applySystemBarInsets(this,root);
    }

    private void addRecordSegment(LinearLayout parent){
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.HORIZONTAL);shell.setPadding(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4));shell.setBackground(Ui.round(Ui.BG_2,18,this));
        streakSegment=segmentButton("起床",false);timeSegment=segmentButton("時間",true);
        streakSegment.setEnabled(pendingImportDurationMs<=0L);
        shell.addView(streakSegment,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));shell.addView(timeSegment,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        streakSegment.setOnClickListener(v->{if(pendingImportDurationMs<=0L)openStreakRecords();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(Ui.dp(this,18),0,Ui.dp(this,18),0);parent.addView(shell,lp);
    }

    private Button segmentButton(String label,boolean selected){
        Button b=new Button(this);b.setText(I18n.tr(this,label));b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,selected?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);b.setTextColor(selected?Ui.TEXT:Ui.MUTED);b.setGravity(Gravity.CENTER);b.setMinHeight(Ui.dp(this,46));b.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);b.setBackground(selected?Ui.roundStroke(Ui.SURFACE,Ui.BORDER,15,this):Ui.round(0x00ffffff,15,this));return b;
    }

    private LinearLayout bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,7),Ui.dp(this,5),Ui.dp(this,7),Ui.dp(this,7));nav.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,0,this));
        Button alarm=Ui.bottomTab(this,"アラーム",false),clock=Ui.bottomTab(this,"時計",false),records=Ui.bottomTab(this,"記録",true),settings=Ui.bottomTab(this,"設定",false);
        nav.addView(alarm,new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));nav.addView(clock,new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));nav.addView(records,new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));nav.addView(settings,new LinearLayout.LayoutParams(0,Ui.dp(this,64),1));
        alarm.setOnClickListener(v->navigateRoot("alarm"));clock.setOnClickListener(v->navigateRoot("clock"));settings.setOnClickListener(v->navigateRoot("settings"));return nav;
    }

    private void navigateRoot(String destination){
        Intent next=null;
        if("clock".equals(destination))next=new Intent(this,ClockActivity.class).putExtra("mode","world").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        else if("settings".equals(destination))next=new Intent(this,SystemSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        else if("alarm".equals(destination))next=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(next!=null)Ui.launchNoAnimation(this,next);
        Ui.finishNoAnimation(this);
    }

    private void openStreakRecords(){Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class).putExtra("record_mode","streak"));Ui.finishNoAnimation(this);}

    private void showFolders(){
        stopTrackingTicker();
        selectedFolderId=null;body.removeAllViews();title.setText(I18n.tr(this,"時間記録"));action.setText("＋");action.setContentDescription(I18n.tr(this,"フォルダーを作成"));action.setOnClickListener(v->showFolderDialog(null));
        addOverviewCard();
        if(folders.isEmpty()){
            addEmptyFolders();return;
        }
        TextView heading=Ui.sectionHeader(this,"フォルダー");heading.setPadding(Ui.dp(this,3),Ui.dp(this,17),0,Ui.dp(this,8));body.addView(heading);
        ArrayList<Folder> sorted=new ArrayList<>(folders);sorted.sort((a,b)->Long.compare(b.createdAt,a.createdAt));
        for(Folder f:sorted) addFolderCard(f);
    }

    private void addOverviewCard(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(this,20),Ui.dp(this,17),Ui.dp(this,20),Ui.dp(this,16));card.setBackground(Ui.gradientRound(this,24,Ui.HERO_START,Ui.HERO_END));
        LinearLayout header=Ui.row(this);header.setPadding(0,0,0,0);TextView label=Ui.overline(this,"記録した時間");label.setTextColor(Ui.HERO_TEXT);header.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView count=text(entries.size()+I18n.tr(this,"件"),13,Ui.HERO_TEXT);header.addView(count);card.addView(header);
        TextView total=Ui.title(this,fmt(totalDurationAll()),34);total.setTextColor(Ui.HERO_TEXT);total.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));total.setPadding(0,Ui.dp(this,5),0,0);card.addView(total);
        TextView note=text("すべてのフォルダーの合計",13,Ui.HERO_TEXT);note.setPadding(0,Ui.dp(this,2),0,0);card.addView(note);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(this,5));body.addView(card,lp);
    }

    private void addEmptyFolders(){
        LinearLayout card=Ui.card(this);card.setGravity(Gravity.CENTER);card.setPadding(Ui.dp(this,22),Ui.dp(this,24),Ui.dp(this,22),Ui.dp(this,22));card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,22,this));
        TextView icon=Ui.title(this,"＋",32);icon.setTextColor(Ui.ACCENT_2);icon.setGravity(Gravity.CENTER);card.addView(icon,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        TextView message=Ui.title(this,"まだフォルダーがありません",19);message.setGravity(Gravity.CENTER);card.addView(message);
        TextView note=text("フォルダーを作ると、時間の記録を整理できます。",14,Ui.MUTED);note.setGravity(Gravity.CENTER);note.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,14));card.addView(note);
        Button create=Ui.button(this,"フォルダーを作成",true);create.setOnClickListener(v->showFolderDialog(null));card.addView(create,new LinearLayout.LayoutParams(-1,-2));
        body.addView(card);
    }

    private void addFolderCard(Folder f){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(this,17),Ui.dp(this,15),Ui.dp(this,17),Ui.dp(this,14));card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,20,this));
        LinearLayout titleRow=Ui.row(this);titleRow.setPadding(0,0,0,0);TextView name=Ui.title(this,f.name,19);titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView count=text(entryCount(f.id)+I18n.tr(this,"件"),12,Ui.HERO_TEXT);count.setGravity(Gravity.CENTER);count.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,10),Ui.dp(this,5));count.setBackground(Ui.round(Ui.HERO_START,14,this));titleRow.addView(count);card.addView(titleRow);
        LocalDate now=LocalDate.now();
        long today=durationForDay(f.id,now);long week=durationForRange(f.id,weekStart(now),weekStart(now).plusDays(7));long total=totalDuration(f.id);
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.HORIZONTAL);details.setPadding(0,Ui.dp(this,13),0,0);
        addFolderMetric(details,"今日",fmt(today));addFolderMetric(details,"今週",fmt(week));addFolderMetric(details,"合計",fmt(total));card.addView(details);
        TextView average=text(I18n.tr(this,"1日平均")+"  "+fmt(averagePerRecordedDay(f.id)),13,Ui.MUTED);average.setPadding(0,Ui.dp(this,10),0,0);card.addView(average);
        card.setOnClickListener(v->{selectedFolderId=f.id;showFolder(f.id);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(this,9));body.addView(card,lp);
    }

    private void addFolderMetric(LinearLayout parent,String label,String value){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);TextView key=text(label,11,Ui.MUTED);item.addView(key);TextView number=Ui.title(this,value,14);number.setPadding(0,Ui.dp(this,3),0,0);item.addView(number);parent.addView(item,new LinearLayout.LayoutParams(0,-2,1));
    }

    private void showFolder(String folderId){
        Folder f=findFolder(folderId);if(f==null){showFolders();return;}selectedFolderId=folderId;body.removeAllViews();title.setText(f.name);action.setText("＋");action.setContentDescription(I18n.tr(this,"時間を追加"));action.setOnClickListener(v->showEntryDialog(f,null));
        addTrackingCard(f);
        addStatsGrid(f);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,8));
        Button rename=Ui.ghostButton(this,"名称変更");rename.setOnClickListener(v->showFolderDialog(f));actions.addView(rename,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        Button delete=Ui.ghostButton(this,"削除");delete.setTextColor(Ui.DANGER);delete.setOnClickListener(v->confirmDeleteFolder(f));actions.addView(delete,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));body.addView(actions);
        ArrayList<Entry> list=entriesFor(folderId);list.sort((a,b)->Long.compare(b.start,a.start));
        if(list.isEmpty()){
            addEmptyEntries();return;
        }
        TextView history=Ui.sectionHeader(this,"記録履歴");history.setPadding(Ui.dp(this,3),Ui.dp(this,13),0,Ui.dp(this,4));body.addView(history);
        ArrayList<LocalDate> days=daysWithEntries(folderId);
        DateTimeFormatter df=DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this));
        for(LocalDate day:days){
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,7));
            TextView d=Ui.title(this,day.format(df),16);head.addView(d,new LinearLayout.LayoutParams(0,-2,1));
            TextView sum=text(I18n.tr(this,"合計")+"  "+fmt(durationForDay(folderId,day)),13,Ui.ACCENT);head.addView(sum);body.addView(head);
            for(Entry e:entriesForDay(folderId,day)) addEntryRow(f,e,day);
        }
    }

    private void addEmptyEntries(){
        LinearLayout card=Ui.card(this);card.setGravity(Gravity.CENTER);card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,20,this));
        TextView message=Ui.title(this,"まだ時間記録がありません",18);message.setGravity(Gravity.CENTER);card.addView(message);
        TextView note=text("計測するか、時間を手動で追加できます。",14,Ui.MUTED);note.setGravity(Gravity.CENTER);note.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,13));card.addView(note);
        Button add=Ui.button(this,"時間を追加",true);add.setOnClickListener(v->showEntryDialog(findFolder(selectedFolderId),null));card.addView(add,new LinearLayout.LayoutParams(-1,-2));body.addView(card);
    }

    private void addTrackingCard(Folder f){
        LinearLayout card=Ui.card(this);card.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,22,this));
        String active=p().getString(KEY_ACTIVE_FOLDER,null);long started=p().getLong(KEY_ACTIVE_START,0L);
        boolean mine=f.id.equals(active)&&started>0L;
        boolean other=active!=null&&!mine&&started>0L;
        TextView label=Ui.overline(this,mine?"計測中":"時間を記録");card.addView(label);
        trackingClock=Ui.title(this,"00:00:00",38);trackingClock.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));trackingClock.setGravity(Gravity.CENTER);trackingClock.setPadding(0,Ui.dp(this,11),0,Ui.dp(this,13));card.addView(trackingClock);
        if(other){
            Folder af=findFolder(active);
            TextView note=text("「"+(af==null?I18n.tr(this,"別のフォルダー"):af.name)+"」で計測中です。停止してから開始してください。",13,Ui.MUTED);note.setGravity(Gravity.CENTER);note.setPadding(0,0,0,Ui.dp(this,8));card.addView(note);
        }
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        Button main=Ui.button(this,mine?"停止して登録":"計測開始",true);
        main.setEnabled(!other);
        main.setOnClickListener(v->{if(isTrackingFolder(f.id))stopAndRegisterTracking(f);else startTracking(f);});
        row.addView(main,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        if(mine){
            View gap=new View(this);row.addView(gap,new LinearLayout.LayoutParams(Ui.dp(this,8),1));
            Button cancel=Ui.ghostButton(this,"キャンセル");cancel.setTextColor(Ui.DANGER);cancel.setOnClickListener(v->cancelTracking(f));row.addView(cancel,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        }
        card.addView(row);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,Ui.dp(this,10));body.addView(card,cp);
        if(mine)startTrackingTicker(f.id);else updateTrackingClock(0L);
    }

    private boolean isTrackingFolder(String folderId){return folderId!=null&&folderId.equals(p().getString(KEY_ACTIVE_FOLDER,null))&&p().getLong(KEY_ACTIVE_START,0L)>0L;}
    private void startTracking(Folder f){
        if(f==null)return;
        String active=p().getString(KEY_ACTIVE_FOLDER,null);long started=p().getLong(KEY_ACTIVE_START,0L);
        if(active!=null&&started>0L&&!active.equals(f.id)){Toast.makeText(this,I18n.tr(this,"別のフォルダーで計測中です"),Toast.LENGTH_SHORT).show();return;}
        p().edit().putString(KEY_ACTIVE_FOLDER,f.id).putLong(KEY_ACTIVE_START,System.currentTimeMillis()).apply();
        showFolder(f.id);
    }
    private void stopAndRegisterTracking(Folder f){
        if(f==null||!isTrackingFolder(f.id))return;
        long start=p().getLong(KEY_ACTIVE_START,0L),end=System.currentTimeMillis();
        p().edit().remove(KEY_ACTIVE_FOLDER).remove(KEY_ACTIVE_START).apply();
        if(start>0L&&end>start){entries.add(new Entry(UUID.randomUUID().toString(),f.id,start,end));save();Toast.makeText(this,I18n.tr(this,"計測時間を登録しました"),Toast.LENGTH_SHORT).show();}
        showFolder(f.id);
    }
    private void cancelTracking(Folder f){
        p().edit().remove(KEY_ACTIVE_FOLDER).remove(KEY_ACTIVE_START).apply();stopTrackingTicker();if(f!=null)showFolder(f.id);
    }
    private void startTrackingTicker(String folderId){
        stopTrackingTicker();
        trackingTick=new Runnable(){@Override public void run(){if(selectedFolderId==null||!selectedFolderId.equals(folderId)||!isTrackingFolder(folderId)){return;}long start=p().getLong(KEY_ACTIVE_START,0L);updateTrackingClock(Math.max(0L,System.currentTimeMillis()-start));ticker.postDelayed(this,1000L);}};
        ticker.post(trackingTick);
    }
    private void stopTrackingTicker(){if(trackingTick!=null)ticker.removeCallbacks(trackingTick);trackingTick=null;}
    private void updateTrackingClock(long ms){
        if(trackingClock==null)return;long total=Math.max(0,ms/1000L);long h=total/3600L,m=(total%3600L)/60L,sec=total%60L;trackingClock.setText(String.format(Locale.US,"%02d:%02d:%02d",h,m,sec));
    }
    private void handleBack(){
        if(selectedFolderId!=null){selectedFolderId=null;showFolders();return;}
        if(returnToClockWhenBack){Ui.finishNoAnimation(this);return;}
        openStreakRecords();
    }
    @Override public void onBackPressed(){handleBack();}
    @Override protected void onDestroy(){stopTrackingTicker();super.onDestroy();}

    private void addStatsGrid(Folder f){
        GridLayout grid=new GridLayout(this);grid.setColumnCount(2);grid.setUseDefaultMargins(false);
        LocalDate today=LocalDate.now();
        addStat(grid,"合計",totalDuration(f.id),0);addStat(grid,"今日",durationForDay(f.id,today),1);addStat(grid,"今週",durationForRange(f.id,weekStart(today),weekStart(today).plusDays(7)),2);addStat(grid,"1日平均",averagePerRecordedDay(f.id),3);
        body.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addStat(GridLayout grid,String label,long value,int index){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(Ui.dp(this,15),Ui.dp(this,13),Ui.dp(this,14),Ui.dp(this,13));c.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,17,this));
        TextView l=Ui.overline(this,label);c.addView(l);TextView v=Ui.title(this,fmt(value),20);v.setSingleLine(true);v.setPadding(0,Ui.dp(this,5),0,0);c.addView(v);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.columnSpec=GridLayout.spec(index%2,1f);lp.rowSpec=GridLayout.spec(index/2);lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3));grid.addView(c,lp);
    }

    private void addEntryRow(Folder f,Entry e,LocalDate displayDay){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(Ui.dp(this,15),Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,10));card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,16,this));
        long dayStart=displayDay.atStartOfDay(zone).toInstant().toEpochMilli(),dayEnd=displayDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long segStart=Math.max(e.start,dayStart),segEnd=Math.min(e.end,dayEnd);DateTimeFormatter tf=DateTimeFormatter.ofPattern("HH:mm");
        ZonedDateTime a=Instant.ofEpochMilli(segStart).atZone(zone),b=Instant.ofEpochMilli(segEnd).atZone(zone);
        String endLabel=segEnd==dayEnd?"24:00":b.format(tf);String times=a.format(tf)+" – "+endLabel;
        boolean split=e.start<dayStart||e.end>dayEnd;
        String durationLabel=split?I18n.tr(this,"この日")+" "+fmt(segEnd-segStart)+"  ·  "+I18n.tr(this,"記録全体")+" "+fmt(e.end-e.start):fmt(e.end-e.start);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);TextView t=Ui.title(this,times,18);info.addView(t);TextView dur=text(durationLabel,13,Ui.ACCENT);dur.setPadding(0,Ui.dp(this,4),0,0);info.addView(dur);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=Ui.ghostButton(this,"編集");edit.setOnClickListener(v->showEntryDialog(f,e));card.addView(edit,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,44)));
        Button del=Ui.ghostButton(this,"削除");del.setTextColor(Ui.DANGER);del.setContentDescription(I18n.tr(this,"時間記録を削除"));del.setOnClickListener(v->confirmDeleteEntry(f,e));card.addView(del,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,44)));
        card.setOnLongClickListener(v->{confirmDeleteEntry(f,e);return true;});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(this,8));body.addView(card,lp);
    }

    private void showImportFolderPicker(){
        if(pendingImportDurationMs<=0L)return;
        if(folders.isEmpty()){
            new AlertDialog.Builder(this).setTitle(I18n.tr(this,"時間記録に追加")).setMessage(I18n.tr(this,"追加先のフォルダーを作成してください。")).setNegativeButton(I18n.tr(this,"キャンセル"),(d,w)->clearPendingImport()).setPositiveButton(I18n.tr(this,"フォルダー作成"),(d,w)->showFolderDialog(null)).show();return;
        }
        ArrayList<Folder> sorted=new ArrayList<>(folders);sorted.sort((a,b)->Long.compare(b.createdAt,a.createdAt));String[] names=new String[sorted.size()+1];for(int i=0;i<sorted.size();i++)names[i]=sorted.get(i).name;names[sorted.size()]=I18n.tr(this,"＋ 新しいフォルダー");
        new AlertDialog.Builder(this).setTitle(I18n.tr(this,pendingImportLabel+"を追加")).setItems(names,(d,which)->{if(which==sorted.size())showFolderDialog(null);else addPendingImport(sorted.get(which));}).setNegativeButton(I18n.tr(this,"キャンセル"),(d,w)->clearPendingImport()).show();
    }
    private void addPendingImport(Folder folder){
        if(folder==null||pendingImportDurationMs<=0L)return;long end=pendingImportEndWallMs>0L?pendingImportEndWallMs:System.currentTimeMillis();long start=end-pendingImportDurationMs;if(start>=end)return;entries.add(new Entry(UUID.randomUUID().toString(),folder.id,start,end));save();String label=pendingImportLabel;clearPendingImport();Toast.makeText(this,I18n.tr(this,label+"を「"+folder.name+"」に追加しました"),Toast.LENGTH_SHORT).show();showFolder(folder.id);
    }
    private void clearPendingImport(){pendingImportDurationMs=0L;pendingImportEndWallMs=0L;pendingImportLabel="ストップウォッチ";if(streakSegment!=null)streakSegment.setEnabled(true);}

    private void showFolderDialog(Folder existing){
        EditText name=new EditText(this);name.setHint(I18n.tr(this,"例：勉強、バイト、運動"));name.setSingleLine(true);name.setText(existing==null?"":existing.name);name.setSelectAllOnFocus(true);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(I18n.tr(this,existing==null?"フォルダーを作成":"フォルダー名を変更")).setView(name).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"保存"),null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError(I18n.tr(this,"名前を入力してください"));return;}if(existing==null){Folder created=new Folder(UUID.randomUUID().toString(),n,System.currentTimeMillis());folders.add(created);save();d.dismiss();if(pendingImportDurationMs>0L)addPendingImport(created);else showFolders();}else{existing.name=n;save();d.dismiss();showFolder(existing.id);}}));d.show();
    }

    private void showEntryDialog(Folder folder,Entry existing){
        Calendar seed=Calendar.getInstance();if(existing!=null)seed.setTimeInMillis(existing.start);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,22),Ui.dp(this,8),Ui.dp(this,22),0);
        Button date=Ui.ghostButton(this,"");Button start=Ui.ghostButton(this,"");Button end=Ui.ghostButton(this,"");box.addView(date);box.addView(start);box.addView(end);
        final LocalDate[] d={Instant.ofEpochMilli(seed.getTimeInMillis()).atZone(zone).toLocalDate()};
        final LocalTime[] st={existing==null?LocalTime.of(seed.get(Calendar.HOUR_OF_DAY),seed.get(Calendar.MINUTE)):Instant.ofEpochMilli(existing.start).atZone(zone).toLocalTime().withSecond(0).withNano(0)};
        final LocalTime[] en={existing==null?st[0].plusHours(1):Instant.ofEpochMilli(existing.end).atZone(zone).toLocalTime().withSecond(0).withNano(0)};
        Runnable labels=()->{date.setText(I18n.tr(this,"日付")+"  "+d[0].format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this))));start.setText(I18n.tr(this,"開始")+"  "+st[0].format(DateTimeFormatter.ofPattern("HH:mm")));end.setText(I18n.tr(this,"終了")+"  "+en[0].format(DateTimeFormatter.ofPattern("HH:mm"))+( !en[0].isAfter(st[0]) ? "  ("+I18n.tr(this,"翌日")+")":""));};labels.run();
        date.setOnClickListener(v->new DatePickerDialog(this,(x,y,m,day)->{d[0]=LocalDate.of(y,m+1,day);labels.run();},d[0].getYear(),d[0].getMonthValue()-1,d[0].getDayOfMonth()).show());
        start.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{st[0]=LocalTime.of(h,m);labels.run();},st[0].getHour(),st[0].getMinute(),true).show());
        end.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{en[0]=LocalTime.of(h,m);labels.run();},en[0].getHour(),en[0].getMinute(),true).show());
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(I18n.tr(this,existing==null?"時間を追加":"時間を編集")).setView(box).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"保存"),null).create();
        dlg.setOnShowListener(x->{dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ZonedDateTime a=ZonedDateTime.of(d[0],st[0],zone);LocalDate endDate=en[0].isAfter(st[0])?d[0]:d[0].plusDays(1);ZonedDateTime b=ZonedDateTime.of(endDate,en[0],zone);long sm=a.toInstant().toEpochMilli(),em=b.toInstant().toEpochMilli();if(em<=sm){Toast.makeText(this,I18n.tr(this,"終了時刻を確認してください"),Toast.LENGTH_SHORT).show();return;}if(existing==null)entries.add(new Entry(UUID.randomUUID().toString(),folder.id,sm,em));else{existing.start=sm;existing.end=em;}save();dlg.dismiss();showFolder(folder.id);});if(existing!=null){dlg.setButton(AlertDialog.BUTTON_NEUTRAL,I18n.tr(this,"削除"),(v,w)->{});dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Ui.DANGER);dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{entries.remove(existing);save();dlg.dismiss();showFolder(folder.id);});}});dlg.show();
    }

    private void confirmDeleteFolder(Folder f){new AlertDialog.Builder(this).setTitle(I18n.tr(this,"フォルダーを削除")).setMessage(f.name+"\n"+I18n.tr(this,"フォルダーと中の時間記録をすべて削除しますか？")).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"削除"),(d,w)->{if(isTrackingFolder(f.id))p().edit().remove(KEY_ACTIVE_FOLDER).remove(KEY_ACTIVE_START).apply();folders.remove(f);entries.removeIf(e->f.id.equals(e.folderId));save();selectedFolderId=null;showFolders();}).show();}
    private void confirmDeleteEntry(Folder f,Entry e){new AlertDialog.Builder(this).setTitle(I18n.tr(this,"記録を削除")).setMessage(I18n.tr(this,"この時間記録を削除しますか？")).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"削除"),(d,w)->{entries.remove(e);save();showFolder(f.id);}).show();}

    private ArrayList<LocalDate> daysWithEntries(String folderId){TreeSet<LocalDate> days=new TreeSet<>(Comparator.reverseOrder());for(Entry e:entries){if(!folderId.equals(e.folderId)||e.end<=e.start)continue;LocalDate a=Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate(),b=Instant.ofEpochMilli(e.end-1).atZone(zone).toLocalDate();for(LocalDate d=a;!d.isAfter(b);d=d.plusDays(1))days.add(d);}return new ArrayList<>(days);}
    private ArrayList<Entry> entriesForDay(String folderId,LocalDate day){long a=day.atStartOfDay(zone).toInstant().toEpochMilli(),b=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();ArrayList<Entry> out=new ArrayList<>();for(Entry e:entries)if(folderId.equals(e.folderId)&&e.end>a&&e.start<b)out.add(e);out.sort((x,y)->Long.compare(y.start,x.start));return out;}
    private Folder findFolder(String id){for(Folder f:folders)if(f.id.equals(id))return f;return null;}
    private ArrayList<Entry> entriesFor(String folderId){ArrayList<Entry> a=new ArrayList<>();for(Entry e:entries)if(folderId.equals(e.folderId))a.add(e);return a;}
    private int entryCount(String folderId){int n=0;for(Entry e:entries)if(folderId.equals(e.folderId))n++;return n;}
    private long totalDuration(String folderId){long n=0;for(Entry e:entries)if(folderId.equals(e.folderId))n+=Math.max(0,e.end-e.start);return n;}
    private long totalDurationAll(){long n=0;for(Entry e:entries)n+=Math.max(0,e.end-e.start);return n;}
    private LocalDate weekStart(LocalDate d){return d.minusDays((d.getDayOfWeek().getValue()+6)%7);}
    private long durationForDay(String folderId,LocalDate day){return durationForRange(folderId,day,day.plusDays(1));}
    private long durationForRange(String folderId,LocalDate start,LocalDate end){long a=start.atStartOfDay(zone).toInstant().toEpochMilli(),b=end.atStartOfDay(zone).toInstant().toEpochMilli(),sum=0;for(Entry e:entries){if(!folderId.equals(e.folderId))continue;long lo=Math.max(a,e.start),hi=Math.min(b,e.end);if(hi>lo)sum+=hi-lo;}return sum;}
    private long averagePerRecordedDay(String folderId){TreeSet<LocalDate> days=new TreeSet<>();for(Entry e:entries){if(!folderId.equals(e.folderId))continue;LocalDate a=Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate(),b=Instant.ofEpochMilli(Math.max(e.start,e.end-1)).atZone(zone).toLocalDate();for(LocalDate d=a;!d.isAfter(b);d=d.plusDays(1))if(durationForDay(folderId,d)>0)days.add(d);}return days.isEmpty()?0:totalDuration(folderId)/days.size();}
    private String fmt(long ms){long mins=Math.max(0,Math.round(ms/60000.0));long h=mins/60,m=mins%60;String hour=I18n.tr(this,"時間単位"),minute=I18n.tr(this,"分単位");if(h==0)return m+minute;if(m==0)return h+hour;return h+hour+m+minute;}

    private void load(){folders.clear();entries.clear();try{JSONArray a=new JSONArray(p().getString(KEY_FOLDERS,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);folders.add(new Folder(o.getString("id"),o.optString("name","フォルダー"),o.optLong("createdAt",0)));}}catch(Throwable ignored){}try{JSONArray a=new JSONArray(p().getString(KEY_ENTRIES,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);long s=o.optLong("start",0),e=o.optLong("end",0);if(e>s)entries.add(new Entry(o.getString("id"),o.getString("folderId"),s,e));}}catch(Throwable ignored){}}
    private void save(){try{JSONArray fa=new JSONArray();for(Folder f:folders){JSONObject o=new JSONObject();o.put("id",f.id);o.put("name",f.name);o.put("createdAt",f.createdAt);fa.put(o);}JSONArray ea=new JSONArray();for(Entry e:entries){JSONObject o=new JSONObject();o.put("id",e.id);o.put("folderId",e.folderId);o.put("start",e.start);o.put("end",e.end);ea.put(o);}p().edit().putString(KEY_FOLDERS,fa.toString()).putString(KEY_ENTRIES,ea.toString()).apply();}catch(Throwable ignored){}}
}
