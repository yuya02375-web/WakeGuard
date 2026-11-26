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
    private String newestEntryId=null;
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
    private EditText folderSearch;
    private LinearLayout folderResults;

    static final class Folder {
        String id,name; long createdAt;
        Folder(String id,String name,long createdAt){this.id=id;this.name=name;this.createdAt=createdAt;}
    }
    static final class Entry {
        String id,folderId; long start,end,createdAt;
        Entry(String id,String folderId,long start,long end,long createdAt){this.id=id;this.folderId=folderId;this.start=start;this.end=end;this.createdAt=createdAt;}
    }

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        Ui.prepareActivity(this);
        load();
        Intent intent=getIntent();
        if(intent!=null) returnToClockWhenBack=!intent.hasExtra("record_mode");
        if(state!=null){
            pendingImportDurationMs=state.getLong("importDurationMs");
            pendingImportEndWallMs=state.getLong("importEndWallMs");
            pendingImportLabel=state.getString("importLabel");
        }else if(intent!=null){
            pendingImportDurationMs=Math.max(0L,intent.getLongExtra("importDurationMs",0L));
            pendingImportEndWallMs=intent.getLongExtra("importEndWallMs",0L);
            pendingImportLabel=intent.getStringExtra("importLabel");
        }
        if(pendingImportLabel==null||pendingImportLabel.trim().isEmpty()) pendingImportLabel="ストップウォッチ";
        buildShell();
        String folderId=state==null?null:state.getString("selectedFolderId");
        if(findFolder(folderId)!=null) showFolder(folderId);
        else showFolders();
        if(pendingImportDurationMs>0L) body.post(this::showImportFolderPicker);
    }

    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);
        state.putLong("importDurationMs",pendingImportDurationMs);
        state.putLong("importEndWallMs",pendingImportEndWallMs);
        state.putString("importLabel",pendingImportLabel);
        state.putString("selectedFolderId",selectedFolderId);
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
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.HORIZONTAL);shell.setPadding(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4));shell.setBackground(Ui.roundStroke(Ui.BG_2,Ui.BORDER,18,this));
        streakSegment=segmentButton("起床",false);timeSegment=segmentButton("時間",true);
        streakSegment.setEnabled(pendingImportDurationMs<=0L);
        shell.addView(streakSegment,new LinearLayout.LayoutParams(0,-1,1));shell.addView(timeSegment,new LinearLayout.LayoutParams(0,-1,1));
        streakSegment.setOnClickListener(v->{if(pendingImportDurationMs<=0L)openStreakRecords();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(Ui.dp(this,18),0,Ui.dp(this,18),0);parent.addView(shell,lp);
    }

    private Button segmentButton(String label,boolean selected){
        Button button = new Button(this);
        button.setText(I18n.tr(this, label));
        button.setSelected(selected);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setIncludeFontPadding(false);
        button.setTypeface(Ui.designTypeface(selected ? Typeface.BOLD : Typeface.NORMAL));
        button.setTextColor(Ui.readableTextColor(selected ? Ui.ACCENT_2 : Ui.MUTED, selected ? Ui.SURFACE : Ui.BG_2));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(Ui.dp(this, 46));
        button.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        button.setBackground(selected ? Ui.roundStroke(Ui.SURFACE, Ui.withAlpha(Ui.ACCENT_2, 0x55), 15, this)
                : Ui.round(android.graphics.Color.TRANSPARENT, 15, this));
        button.setElevation(selected ? Ui.dp(this, 1) : 0);
        return button;
    }

    private LinearLayout bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,7),Ui.dp(this,5),Ui.dp(this,7),Ui.dp(this,7));nav.setBackground(Ui.navigationSurface(this));
        LinearLayout alarm=Ui.bottomTab(this,"アラーム",false),clock=Ui.bottomTab(this,"時計",false),records=Ui.bottomTab(this,"記録",true),settings=Ui.bottomTab(this,"設定",false);
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
        LinearLayout headingRow=Ui.row(this);headingRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=Ui.sectionHeader(this,"フォルダー");heading.setPadding(Ui.dp(this,3),Ui.dp(this,16),0,Ui.dp(this,8));
        headingRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView order=Ui.pill(this,I18n.tr(this,"最近の記録順"));
        LinearLayout.LayoutParams orderLp=new LinearLayout.LayoutParams(-2,-2);orderLp.setMargins(Ui.dp(this,8),Ui.dp(this,7),0,0);headingRow.addView(order,orderLp);
        body.addView(headingRow);
        folderSearch=new EditText(this);folderSearch.setSingleLine(true);folderSearch.setTextSize(15);folderSearch.setHint(I18n.tr(this,"フォルダー名で検索"));folderSearch.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);Ui.styleInput(folderSearch,this,Ui.SURFACE_2,14);
        body.addView(folderSearch,Ui.gapTop(this,4));
        folderResults=new LinearLayout(this);folderResults.setOrientation(LinearLayout.VERTICAL);body.addView(folderResults,Ui.gapTop(this,12));
        folderSearch.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){renderFolderResults();}
            public void afterTextChanged(android.text.Editable e){}
        });
        renderFolderResults();
    }

    private void renderFolderResults(){
        if(folderResults==null)return;
        folderResults.removeAllViews();
        ArrayList<Folder> sorted=foldersByRecentActivity();
        HashMap<String,Entry> latestEntries=newestEntriesByFolder();
        String newestRecordFolderId=mostRecentFolderId(latestEntries);
        String query=folderSearch==null?"":folderSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        int matches=0;
        for(Folder f:sorted){
            if(!query.isEmpty()&&!f.name.toLowerCase(Locale.ROOT).contains(query))continue;
            addFolderCard(folderResults,f,latestEntries.get(f.id),f.id.equals(newestRecordFolderId));matches++;
        }
        if(matches==0){
            LinearLayout empty=Ui.card(this);empty.setOrientation(LinearLayout.VERTICAL);empty.setPadding(Ui.dp(this,17),Ui.dp(this,16),Ui.dp(this,17),Ui.dp(this,15));empty.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,18,this));
            TextView message=Ui.title(this,I18n.tr(this,"一致するフォルダーがありません"),16);empty.addView(message);
            TextView hint=text(I18n.tr(this,"別の名前で検索するか、入力を消してください。"),13,Ui.MUTED);hint.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,10));empty.addView(hint);
            Button clear=Ui.ghostButton(this,I18n.tr(this,"検索をクリア"));clear.setOnClickListener(v->{if(folderSearch!=null)folderSearch.setText("");});empty.addView(clear,new LinearLayout.LayoutParams(-1,Ui.dp(this,44)));
            folderResults.addView(empty);
        }
    }

    private boolean needsRoomForText() {
        android.content.res.Configuration config = getResources().getConfiguration();
        return config.screenWidthDp / Math.max(1f, config.fontScale) < 345f;
    }

    private void addOverviewCard(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(this,20),Ui.dp(this,17),Ui.dp(this,20),Ui.dp(this,16));card.setBackground(Ui.gradientRound(this,24,Ui.HERO_START,Ui.HERO_END));
        LinearLayout header=Ui.row(this);header.setPadding(0,0,0,0);TextView label=Ui.overline(this,"記録した時間");label.setTextColor(Ui.HERO_TEXT);header.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView count=text(entries.size()+I18n.tr(this,"件"),13,Ui.HERO_TEXT);header.addView(count);card.addView(header);
        TextView scope=text(I18n.tr(this,"すべてのフォルダー"),12,Ui.HERO_TEXT);scope.setPadding(0,Ui.dp(this,8),0,0);card.addView(scope);
        TextView total=Ui.title(this,fmt(totalDurationAll()),34);total.setTextColor(Ui.HERO_TEXT);total.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));total.setPadding(0,Ui.dp(this,5),0,0);card.addView(total);
        TextView note=text(I18n.tr(this,"全期間の合計"),13,Ui.HERO_TEXT);note.setPadding(0,Ui.dp(this,2),0,0);card.addView(note);
        View divider=new View(this);LinearLayout.LayoutParams dividerLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,1));dividerLp.setMargins(0,Ui.dp(this,14),0,Ui.dp(this,12));divider.setBackgroundColor(Ui.withAlpha(Ui.HERO_TEXT,0x48));card.addView(divider,dividerLp);
        boolean stacked = needsRoomForText();
        LinearLayout todayRow = Ui.row(this);
        todayRow.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        todayRow.setGravity(Gravity.CENTER_VERTICAL);
        todayRow.setPadding(Ui.dp(this, 14), Ui.dp(this, 11), Ui.dp(this, 14), Ui.dp(this, 11));
        todayRow.setBackground(Ui.round(Ui.SURFACE, 15, this));
        TextView todayLabel = Ui.text(this, "今日の合計", 14, Ui.readableTextColor(Ui.MUTED, Ui.SURFACE));
        todayRow.addView(todayLabel, stacked ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1f));
        TextView todayValue = Ui.title(this, fmt(durationForDayAll(LocalDate.now())), 20);
        todayValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        todayRow.addView(todayValue, new LinearLayout.LayoutParams(-2, -2));
        card.addView(todayRow);
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

    private void addFolderCard(LinearLayout parent,Folder f,Entry latestEntry,boolean mostRecentRecord){
        boolean tracking=isTrackingFolder(f.id);
        LinearLayout card=Ui.card(this);card.setPadding(Ui.dp(this,17),Ui.dp(this,13),Ui.dp(this,14),Ui.dp(this,13));
        LinearLayout titleRow=Ui.row(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=Ui.title(this,f.name,19);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        android.widget.ImageView open=new android.widget.ImageView(this);open.setImageResource(R.drawable.ic_v293_chevron_right);
        open.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.MUTED));open.setContentDescription(I18n.tr(this,"フォルダーを開く"));
        LinearLayout.LayoutParams openLp=new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22));openLp.setMargins(Ui.dp(this,8),0,0,0);titleRow.addView(open,openLp);card.addView(titleRow);
        LinearLayout badges=Ui.row(this);badges.setGravity(Gravity.CENTER_VERTICAL);badges.setPadding(0,Ui.dp(this,6),0,0);
        if(tracking||mostRecentRecord){
            TextView status=tracking?Ui.accentPill(this,I18n.tr(this,"計測中")):Ui.pill(this,I18n.tr(this,"最近記録"));
            badges.addView(status);
        }
        TextView count=Ui.pill(this,entryCount(f.id)+I18n.tr(this,"件"));
        LinearLayout.LayoutParams countLp=new LinearLayout.LayoutParams(-2,-2);countLp.setMargins((tracking||mostRecentRecord)?Ui.dp(this,6):0,0,0,0);badges.addView(count,countLp);card.addView(badges);
        TextView lastRecord=text(lastRecordSummary(latestEntry),12,latestEntry==null?Ui.MUTED:(mostRecentRecord?Ui.ACCENT_2:Ui.MUTED));
        lastRecord.setSingleLine(true);lastRecord.setEllipsize(android.text.TextUtils.TruncateAt.END);lastRecord.setPadding(0,Ui.dp(this,5),0,0);card.addView(lastRecord);
        LocalDate now=LocalDate.now();
        long today=durationForDay(f.id,now);long week=durationForRange(f.id,weekStart(now),weekStart(now).plusDays(7));long total=totalDuration(f.id);
        LinearLayout details=new LinearLayout(this);
        details.setOrientation(needsRoomForText() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        details.setPadding(0,Ui.dp(this,11),0,0);
        addFolderMetric(details,"今日",fmt(today));addFolderMetric(details,"今週",fmt(week));addFolderMetric(details,"合計",fmt(total));card.addView(details);
        LinearLayout averageRow=Ui.row(this);averageRow.setGravity(Gravity.CENTER_VERTICAL);averageRow.setPadding(0,Ui.dp(this,9),0,0);
        TextView averageLabel=text(I18n.tr(this,"1日平均"),12,Ui.MUTED);averageRow.addView(averageLabel,new LinearLayout.LayoutParams(0,-2,1));
        TextView average=text(fmt(averagePerRecordedDay(f.id)),13,Ui.TEXT);average.setTypeface(Ui.designTypeface(Typeface.BOLD));averageRow.addView(average);card.addView(averageRow);
        card.setContentDescription(f.name+", "+lastRecordSummary(latestEntry)+", "+entryCount(f.id)+I18n.tr(this,"件")+". "+I18n.tr(this,"フォルダーを開く"));
        card.setFocusable(true);card.setOnClickListener(v->{selectedFolderId=f.id;showFolder(f.id);});
        if(android.os.Build.VERSION.SDK_INT>=21){
            android.graphics.drawable.Drawable content=card.getBackground();
            card.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Ui.withAlpha(Ui.TEXT,0x16)),content,Ui.round(Ui.TEXT,20,this)));
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(this,9));parent.addView(card,lp);
    }

    private void addFolderMetric(LinearLayout parent,String label,String value){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(Ui.dp(this,9),Ui.dp(this,7),Ui.dp(this,7),Ui.dp(this,7));
        item.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,13,this));
        TextView key = text(label, 11, Ui.MUTED);
        item.addView(key);
        TextView number = Ui.title(this, value, 14);
        number.setPadding(0, Ui.dp(this, 2), 0, 0);
        item.addView(number);
        boolean stacked = parent.getOrientation() == LinearLayout.VERTICAL;
        LinearLayout.LayoutParams params = stacked ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1f);
        if (parent.getChildCount() > 0) {
            if (stacked) params.topMargin = Ui.dp(this, 6);
            else params.setMarginStart(Ui.dp(this, 6));
        }
        parent.addView(item, params);
    }

    private void showFolder(String folderId){
        Folder f=findFolder(folderId);if(f==null){showFolders();return;}selectedFolderId=folderId;body.removeAllViews();title.setText(f.name);action.setText("＋");action.setContentDescription(I18n.tr(this,"時間を追加"));action.setOnClickListener(v->showEntryDialog(f,null));
        addTrackingCard(f);
        addStatsGrid(f);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,8));
        Button rename=Ui.ghostButton(this,"名称変更");rename.setOnClickListener(v->showFolderDialog(f));actions.addView(rename,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        Button delete=Ui.ghostButton(this,"削除");delete.setTextColor(Ui.DANGER);delete.setOnClickListener(v->confirmDeleteFolder(f));actions.addView(delete,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));body.addView(actions);
        ArrayList<Entry> list=entriesFor(folderId);
        list.sort((a,b)->Long.compare(recordedAt(b),recordedAt(a)));
        newestEntryId=list.isEmpty()?null:list.get(0).id;
        if(list.isEmpty()){
            addEmptyEntries();return;
        }
        TextView history=Ui.sectionHeader(this,"記録履歴");history.setPadding(Ui.dp(this,3),Ui.dp(this,13),0,Ui.dp(this,4));body.addView(history);
        ArrayList<LocalDate> days=daysWithEntries(folderId);
        days.sort((a,b)->{
            int newestOrder=Long.compare(newestRecordedAt(folderId,b),newestRecordedAt(folderId,a));
            return newestOrder!=0?newestOrder:b.compareTo(a);
        });
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
        LinearLayout card=Ui.card(this);
        String active=p().getString(KEY_ACTIVE_FOLDER,null);long started=p().getLong(KEY_ACTIVE_START,0L);
        boolean mine=f.id.equals(active)&&started>0L;
        boolean other=active!=null&&!mine&&started>0L;
        card.setBackground(mine?Ui.gradientRound(this,22,Ui.HERO_START,Ui.HERO_END)
                :other?Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER_HOT,22,this):Ui.gradientRound(this,22,Ui.SURFACE,Ui.SURFACE_2));
        TextView label=Ui.overline(this,mine?"計測中":"時間を記録");label.setTextColor(mine?Ui.HERO_TEXT:Ui.ACCENT_2);card.addView(label);
        trackingClock=Ui.title(this,"00:00:00",38);trackingClock.setTextColor(mine?Ui.HERO_TEXT:Ui.TEXT);trackingClock.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));trackingClock.setGravity(Gravity.CENTER);trackingClock.setPadding(0,Ui.dp(this,11),0,Ui.dp(this,13));
        trackingClock.setSingleLine(true);
        trackingClock.setAutoSizeTextTypeUniformWithConfiguration(21,38,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        card.addView(trackingClock,new LinearLayout.LayoutParams(-1,Ui.dp(this,76)));
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
        if(start>0L&&end>start){entries.add(new Entry(UUID.randomUUID().toString(),f.id,start,end,end));save();Toast.makeText(this,I18n.tr(this,"計測時間を登録しました"),Toast.LENGTH_SHORT).show();}
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
        GridLayout grid=new GridLayout(this);grid.setColumnCount(needsRoomForText() ? 1 : 2);grid.setUseDefaultMargins(false);
        LocalDate today=LocalDate.now();
        addStat(grid,"合計",totalDuration(f.id),0);addStat(grid,"今日",durationForDay(f.id,today),1);addStat(grid,"今週",durationForRange(f.id,weekStart(today),weekStart(today).plusDays(7)),2);addStat(grid,"1日平均",averagePerRecordedDay(f.id),3);
        body.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addStat(GridLayout grid,String label,long value,int index){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(Ui.dp(this,15),Ui.dp(this,13),Ui.dp(this,14),Ui.dp(this,13));c.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,17,this));
        TextView l=Ui.overline(this,label);c.addView(l);TextView v=Ui.title(this,fmt(value),20);v.setPadding(0,Ui.dp(this,5),0,0);c.addView(v);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.columnSpec=GridLayout.spec(index%grid.getColumnCount(),1f);lp.rowSpec=GridLayout.spec(index/grid.getColumnCount());lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3));grid.addView(c,lp);
    }

    private void addEntryRow(Folder f,Entry e,LocalDate displayDay){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(Ui.dp(this,15),Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,10));card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,16,this));
        long dayStart=displayDay.atStartOfDay(zone).toInstant().toEpochMilli(),dayEnd=displayDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long segStart=Math.max(e.start,dayStart),segEnd=Math.min(e.end,dayEnd);DateTimeFormatter tf=DateTimeFormatter.ofPattern("HH:mm");
        ZonedDateTime a=Instant.ofEpochMilli(segStart).atZone(zone),b=Instant.ofEpochMilli(segEnd).atZone(zone);
        String endLabel=segEnd==dayEnd?"24:00":b.format(tf);String times=a.format(tf)+" – "+endLabel;
        boolean split=e.start<dayStart||e.end>dayEnd;
        String durationLabel=split?I18n.tr(this,"この日")+" "+fmt(segEnd-segStart)+"  ·  "+I18n.tr(this,"記録全体")+" "+fmt(e.end-e.start):fmt(e.end-e.start);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        LocalDate sessionStart=Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate();
        if(e.id.equals(newestEntryId)&&displayDay.equals(sessionStart)){
            String latestLabel="en".equals(I18n.language(this))?"NEW":"ko".equals(I18n.language(this))?"최신":"最新";
            TextView latest=Ui.accentPill(this,latestLabel);
            latest.setTextSize(10);
            latest.setPadding(Ui.dp(this,8),Ui.dp(this,3),Ui.dp(this,8),Ui.dp(this,3));
            info.addView(latest,new LinearLayout.LayoutParams(-2,-2));
        }
        TextView t=Ui.title(this,times,18);LinearLayout.LayoutParams timeParams=new LinearLayout.LayoutParams(-1,-2);if(e.id.equals(newestEntryId)&&displayDay.equals(sessionStart))timeParams.topMargin=Ui.dp(this,4);info.addView(t,timeParams);TextView dur=text(durationLabel,13,Ui.ACCENT);dur.setPadding(0,Ui.dp(this,4),0,0);info.addView(dur);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=Ui.ghostButton(this,"編集");edit.setOnClickListener(v->showEntryDialog(f,e));card.addView(edit,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,44)));
        Button del=Ui.ghostButton(this,"削除");del.setTextColor(Ui.DANGER);del.setContentDescription(I18n.tr(this,"時間記録を削除"));del.setOnClickListener(v->confirmDeleteEntry(f,e));card.addView(del,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,44)));
        card.setOnLongClickListener(v->{confirmDeleteEntry(f,e);return true;});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(this,8));body.addView(card,lp);
    }

    private void showImportFolderPicker(){
        if(pendingImportDurationMs<=0L||isFinishing()||isDestroyed())return;
        if(folders.isEmpty()){
            Ui.dialogBuilder(this)
                    .setTitle(I18n.tr(this,"時間記録に追加"))
                    .setMessage(I18n.tr(this,"追加先のフォルダーを作成してください。"))
                    .setNegativeButton(I18n.tr(this,"キャンセル"),(d,w)->clearPendingImport())
                    .setPositiveButton(I18n.tr(this,"フォルダー作成"),(d,w)->showFolderDialog(null))
                    .setOnCancelListener(d->clearPendingImport())
                    .show();
            return;
        }
        ArrayList<Folder> sorted=foldersByRecentActivity();String[] names=new String[sorted.size()+1];for(int i=0;i<sorted.size();i++)names[i]=sorted.get(i).name;names[sorted.size()]=I18n.tr(this,"＋ 新しいフォルダー");
        Ui.dialogBuilder(this).setTitle(I18n.tr(this,pendingImportLabel+"を追加"))
                .setItems(names,(d,which)->{if(which==sorted.size())showFolderDialog(null);else addPendingImport(sorted.get(which));})
                .setNegativeButton(I18n.tr(this,"キャンセル"),(d,w)->clearPendingImport())
                .setOnCancelListener(d->clearPendingImport())
                .show();
    }
    private void addPendingImport(Folder folder){
        if(folder==null||pendingImportDurationMs<=0L)return;long end=pendingImportEndWallMs>0L?pendingImportEndWallMs:System.currentTimeMillis();long start=end-pendingImportDurationMs;if(start>=end)return;long recordedAt=System.currentTimeMillis();entries.add(new Entry(UUID.randomUUID().toString(),folder.id,start,end,recordedAt));save();String label=pendingImportLabel;clearPendingImport();Toast.makeText(this,I18n.tr(this,label+"を「"+folder.name+"」に追加しました"),Toast.LENGTH_SHORT).show();showFolder(folder.id);
    }
    private void clearPendingImport(){
        pendingImportDurationMs=0L;
        pendingImportEndWallMs=0L;
        pendingImportLabel="ストップウォッチ";
        Intent intent=getIntent();
        if(intent!=null){
            intent.removeExtra("importDurationMs");
            intent.removeExtra("importEndWallMs");
            intent.removeExtra("importLabel");
        }
        if(streakSegment!=null) streakSegment.setEnabled(true);
    }

    private void showFolderDialog(Folder existing){
        EditText name=new EditText(this);name.setHint(I18n.tr(this,"例：勉強、バイト、運動"));name.setTextSize(17);name.setSingleLine(true);name.setText(existing==null?"":existing.name);name.setSelectAllOnFocus(true);name.setPadding(Ui.dp(this,14),Ui.dp(this,9),Ui.dp(this,14),Ui.dp(this,9));Ui.styleInput(name,this,Ui.SURFACE_2,14);
        boolean importing=existing==null&&pendingImportDurationMs>0L;
        AlertDialog dialog=Ui.dialogBuilder(this)
                .setTitle(I18n.tr(this,existing==null?"フォルダーを作成":"フォルダー名を変更"))
                .setView(name)
                .setNegativeButton(I18n.tr(this,"キャンセル"),(d,w)->{if(importing) clearPendingImport();})
                .setPositiveButton(I18n.tr(this,"保存"),null)
                .setOnCancelListener(d->{if(importing) clearPendingImport();})
                .create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=name.getText().toString().trim();
            if(value.isEmpty()){
                name.setError(I18n.tr(this,"名前を入力してください"));
                return;
            }
            if(existing==null){
                Folder created=new Folder(UUID.randomUUID().toString(),value,System.currentTimeMillis());
                folders.add(created);
                if(importing) addPendingImport(created);
                else{
                    save();
                    showFolders();
                }
            }else{
                existing.name=value;
                save();
                showFolder(existing.id);
            }
            dialog.dismiss();
        }));
        Ui.showDialog(dialog,this);
    }

    private void showEntryDialog(Folder folder,Entry existing){
        if(folder==null) return;
        ZonedDateTime originalStart=existing==null?ZonedDateTime.now(zone).withSecond(0).withNano(0)
                :Instant.ofEpochMilli(existing.start).atZone(zone);
        ZonedDateTime originalEnd=existing==null?originalStart.plusHours(1)
                :Instant.ofEpochMilli(existing.end).atZone(zone);
        long originalDayOffset=originalEnd.toLocalDate().toEpochDay()-originalStart.toLocalDate().toEpochDay();
        LocalDate[] day={originalStart.toLocalDate()};
        LocalTime[] startTime={originalStart.toLocalTime()};
        LocalTime[] endTime={originalEnd.toLocalTime()};
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,22),Ui.dp(this,8),Ui.dp(this,22),0);
        Button date=Ui.ghostButton(this,"");Button start=Ui.ghostButton(this,"");Button end=Ui.ghostButton(this,"");box.addView(date);box.addView(start);box.addView(end);
        DateTimeFormatter dateFormat=DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this));
        DateTimeFormatter timeFormat=DateTimeFormatter.ofPattern("HH:mm");
        java.util.function.LongSupplier endDayOffset=()->{
            // Keep the original day span for recorded intervals beyond the manual overnight range.
            boolean measuredLongInterval=originalDayOffset>1L
                    ||(originalDayOffset==1L&&originalEnd.toLocalTime().isAfter(originalStart.toLocalTime()));
            boolean timesUnchanged=startTime[0].equals(originalStart.toLocalTime())
                    &&endTime[0].equals(originalEnd.toLocalTime());
            if(measuredLongInterval||timesUnchanged) return originalDayOffset;
            return endTime[0].isAfter(startTime[0])?0L:1L;
        };
        Runnable labels=()->{
            date.setText(I18n.tr(this,"日付")+"  "+day[0].format(dateFormat));
            start.setText(I18n.tr(this,"開始")+"  "+startTime[0].format(timeFormat));
            long offset=endDayOffset.getAsLong();
            String suffix=offset==0L?"":"  ("+(offset==1L?I18n.tr(this,"翌日"):day[0].plusDays(offset).format(dateFormat))+")";
            end.setText(I18n.tr(this,"終了")+"  "+endTime[0].format(timeFormat)+suffix);
        };
        labels.run();
        date.setOnClickListener(v->new DatePickerDialog(this,(picker,year,month,dayOfMonth)->{
            day[0]=LocalDate.of(year,month+1,dayOfMonth);
            labels.run();
        },day[0].getYear(),day[0].getMonthValue()-1,day[0].getDayOfMonth()).show());
        start.setOnClickListener(v->showEntryTimePicker(startTime,labels));
        end.setOnClickListener(v->showEntryTimePicker(endTime,labels));
        AlertDialog.Builder builder=Ui.dialogBuilder(this).setTitle(I18n.tr(this,existing==null?"時間を追加":"時間を編集"))
                .setView(box).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"保存"),null);
        if(existing!=null) builder.setNeutralButton(I18n.tr(this,"削除"),(d,w)->confirmDeleteEntry(folder,existing));
        AlertDialog dialog=builder.create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            ZonedDateTime startAt=ZonedDateTime.ofLocal(day[0].atTime(startTime[0]),zone,originalStart.getOffset());
            ZonedDateTime endAt=ZonedDateTime.ofLocal(day[0].plusDays(endDayOffset.getAsLong()).atTime(endTime[0]),zone,originalEnd.getOffset());
            long startMs=startAt.toInstant().toEpochMilli(),endMs=endAt.toInstant().toEpochMilli();
            if(endMs<=startMs){
                Toast.makeText(this,I18n.tr(this,"終了時刻を確認してください"),Toast.LENGTH_SHORT).show();
                return;
            }
            if(existing==null) entries.add(new Entry(UUID.randomUUID().toString(),folder.id,startMs,endMs,System.currentTimeMillis()));
            else{
                existing.start=startMs;
                existing.end=endMs;
            }
            save();
            dialog.dismiss();
            showFolder(folder.id);
        }));
        Ui.showDialog(dialog,this);
        if(existing!=null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Ui.DANGER);
    }

    private void showEntryTimePicker(LocalTime[] time,Runnable onChange){
        new TimePickerDialog(this,(picker,hour,minute)->{
            if(hour!=time[0].getHour()||minute!=time[0].getMinute()) time[0]=LocalTime.of(hour,minute);
            onChange.run();
        },time[0].getHour(),time[0].getMinute(),true).show();
    }

    private void confirmDeleteFolder(Folder f){Ui.dialogBuilder(this).setTitle(I18n.tr(this,"フォルダーを削除")).setMessage(f.name+"\n"+I18n.tr(this,"フォルダーと中の時間記録をすべて削除しますか？")).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"削除"),(d,w)->{if(isTrackingFolder(f.id))p().edit().remove(KEY_ACTIVE_FOLDER).remove(KEY_ACTIVE_START).apply();folders.remove(f);entries.removeIf(e->f.id.equals(e.folderId));save();selectedFolderId=null;showFolders();}).show();}
    private void confirmDeleteEntry(Folder f,Entry e){Ui.dialogBuilder(this).setTitle(I18n.tr(this,"記録を削除")).setMessage(I18n.tr(this,"この時間記録を削除しますか？")).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"削除"),(d,w)->{entries.remove(e);save();showFolder(f.id);}).show();}

    private ArrayList<LocalDate> daysWithEntries(String folderId){TreeSet<LocalDate> days=new TreeSet<>(Comparator.reverseOrder());for(Entry e:entries){if(!folderId.equals(e.folderId)||e.end<=e.start)continue;LocalDate a=Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate(),b=Instant.ofEpochMilli(e.end-1).atZone(zone).toLocalDate();for(LocalDate d=a;!d.isAfter(b);d=d.plusDays(1))days.add(d);}return new ArrayList<>(days);}
    private long recordedAt(Entry e){return e.createdAt>0L?e.createdAt:e.end;}
    private HashMap<String,Entry> newestEntriesByFolder(){
        HashMap<String,Entry> newest=new HashMap<>();
        for(Entry e:entries){
            Entry previous=newest.get(e.folderId);
            if(previous==null||recordedAt(e)>recordedAt(previous)||(recordedAt(e)==recordedAt(previous)&&e.end>previous.end))newest.put(e.folderId,e);
        }
        return newest;
    }
    private String mostRecentFolderId(HashMap<String,Entry> newestEntries){
        String newestFolderId=null;long latest=Long.MIN_VALUE;
        for(Map.Entry<String,Entry> item:newestEntries.entrySet()){
            long at=recordedAt(item.getValue());
            if(at>latest){latest=at;newestFolderId=item.getKey();}
        }
        return newestFolderId;
    }
    private String lastRecordSummary(Entry entry){
        if(entry==null)return I18n.tr(this,"まだ記録がありません");
        ZonedDateTime time=Instant.ofEpochMilli(recordedAt(entry)).atZone(zone);
        LocalDate date=time.toLocalDate(),today=LocalDate.now(zone);
        String day=date.equals(today)?I18n.tr(this,"今日"):(date.equals(today.minusDays(1))?I18n.tr(this,"昨日"):date.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this))));
        return I18n.tr(this,"最終記録")+" · "+day+" "+time.format(DateTimeFormatter.ofPattern("HH:mm",I18n.locale(this)));
    }
    private ArrayList<Folder> foldersByRecentActivity(){
        HashMap<String,Long> activityAt=new HashMap<>();
        for(Folder f:folders)activityAt.put(f.id,f.createdAt);
        for(Entry e:entries){
            long at=recordedAt(e);
            Long previous=activityAt.get(e.folderId);
            if(previous==null||at>previous)activityAt.put(e.folderId,at);
        }
        ArrayList<Folder> sorted=new ArrayList<>(folders);
        sorted.sort((a,b)->{
            int activityOrder=Long.compare(activityAt.get(b.id),activityAt.get(a.id));
            return activityOrder!=0?activityOrder:Long.compare(b.createdAt,a.createdAt);
        });
        return sorted;
    }
    private long newestRecordedAt(String folderId,LocalDate day){long a=day.atStartOfDay(zone).toInstant().toEpochMilli(),b=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),latest=Long.MIN_VALUE;for(Entry e:entries)if(folderId.equals(e.folderId)&&e.end>a&&e.start<b)latest=Math.max(latest,e.createdAt>0L?e.createdAt:e.end);return latest;}
    private ArrayList<Entry> entriesForDay(String folderId,LocalDate day){long a=day.atStartOfDay(zone).toInstant().toEpochMilli(),b=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();ArrayList<Entry> out=new ArrayList<>();for(Entry e:entries)if(folderId.equals(e.folderId)&&e.end>a&&e.start<b)out.add(e);out.sort((x,y)->{long xc=x.createdAt>0L?x.createdAt:x.end,yc=y.createdAt>0L?y.createdAt:y.end;int order=Long.compare(yc,xc);return order!=0?order:Long.compare(y.start,x.start);});return out;}
    private Folder findFolder(String id){for(Folder f:folders)if(f.id.equals(id))return f;return null;}
    private ArrayList<Entry> entriesFor(String folderId){ArrayList<Entry> a=new ArrayList<>();for(Entry e:entries)if(folderId.equals(e.folderId))a.add(e);return a;}
    private int entryCount(String folderId){int n=0;for(Entry e:entries)if(folderId.equals(e.folderId))n++;return n;}
    private long totalDuration(String folderId){long n=0;for(Entry e:entries)if(folderId.equals(e.folderId))n+=Math.max(0,e.end-e.start);return n;}
    private long totalDurationAll(){long n=0;for(Entry e:entries)n+=Math.max(0,e.end-e.start);return n;}
    private LocalDate weekStart(LocalDate d){return d.minusDays((d.getDayOfWeek().getValue()+6)%7);}
    private long durationForDay(String folderId,LocalDate day){return durationForRange(folderId,day,day.plusDays(1));}
    private long durationForDayAll(LocalDate day){long a=day.atStartOfDay(zone).toInstant().toEpochMilli(),b=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),sum=0;for(Entry e:entries){long lo=Math.max(a,e.start),hi=Math.min(b,e.end);if(hi>lo)sum+=hi-lo;}return sum;}
    private long durationForRange(String folderId,LocalDate start,LocalDate end){long a=start.atStartOfDay(zone).toInstant().toEpochMilli(),b=end.atStartOfDay(zone).toInstant().toEpochMilli(),sum=0;for(Entry e:entries){if(!folderId.equals(e.folderId))continue;long lo=Math.max(a,e.start),hi=Math.min(b,e.end);if(hi>lo)sum+=hi-lo;}return sum;}
    private long averagePerRecordedDay(String folderId){ArrayList<LocalDate> days=daysWithEntries(folderId);return days.isEmpty()?0:totalDuration(folderId)/days.size();}
    private String fmt(long ms){
        if(ms>0L&&ms<60000L){
            return (ms<1000L?"<1":String.valueOf(ms/1000L))+I18n.tr(this,"秒");
        }
        long minutes = Math.max(0, Math.round(ms / 60000.0));
        long hours = minutes / 60;
        long remainder = minutes % 60;
        if (hours == 0) return I18n.tr(this, remainder + "分");
        if (remainder == 0) return I18n.tr(this, hours + "時間");
        return I18n.tr(this, hours + "時間" + remainder + "分");
    }

    private void load(){folders.clear();entries.clear();try{JSONArray a=new JSONArray(p().getString(KEY_FOLDERS,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);folders.add(new Folder(o.getString("id"),o.optString("name","フォルダー"),o.optLong("createdAt",0)));}}catch(Throwable ignored){}try{JSONArray a=new JSONArray(p().getString(KEY_ENTRIES,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);long s=o.optLong("start",0),e=o.optLong("end",0),created=o.optLong("createdAt",e);if(e>s)entries.add(new Entry(o.getString("id"),o.getString("folderId"),s,e,created>0L?created:e));}}catch(Throwable ignored){}}
    private void save(){try{JSONArray fa=new JSONArray();for(Folder f:folders){JSONObject o=new JSONObject();o.put("id",f.id);o.put("name",f.name);o.put("createdAt",f.createdAt);fa.put(o);}JSONArray ea=new JSONArray();for(Entry e:entries){JSONObject o=new JSONObject();o.put("id",e.id);o.put("folderId",e.folderId);o.put("start",e.start);o.put("end",e.end);o.put("createdAt",e.createdAt>0L?e.createdAt:e.end);ea.put(o);}p().edit().putString(KEY_FOLDERS,fa.toString()).putString(KEY_ENTRIES,ea.toString()).apply();}catch(Throwable ignored){}}
}


