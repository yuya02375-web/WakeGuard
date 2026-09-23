from pathlib import Path
import re

r = Path('WakeGuard/app')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=re.sub(r'versionCode = \d+', 'versionCode = 101', s, count=1)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "2.0.1"', s, count=1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/ClockActivity.java'; s=read(p)
s=s.replace('private Button screenAction;','private Button screenAction, worldLayoutAction;',1)
old='''        screenAction = Ui.ghostButton(this,"＋");
        screenAction.setTextSize(27); screenAction.setMinWidth(Ui.dp(this,52)); screenAction.setVisibility(View.GONE);
        screenAction.setOnClickListener(v -> showAddTimerDialog());
        top.addView(screenAction);
'''
new='''        worldLayoutAction = Ui.ghostButton(this,"▦");
        worldLayoutAction.setTextSize(22); worldLayoutAction.setMinWidth(Ui.dp(this,48)); worldLayoutAction.setVisibility(View.GONE);
        worldLayoutAction.setContentDescription(I18n.tr(this,"一覧 / 詳細を切り替え"));
        worldLayoutAction.setOnClickListener(v -> { p().edit().putBoolean("world_overview_v201", !worldOverview()).apply(); showMode("world"); });
        top.addView(worldLayoutAction);
        screenAction = Ui.ghostButton(this,"＋");
        screenAction.setTextSize(27); screenAction.setMinWidth(Ui.dp(this,52)); screenAction.setVisibility(View.GONE);
        screenAction.setOnClickListener(v -> showAddTimerDialog());
        top.addView(screenAction);
'''
if old not in s: raise SystemExit('buildShell action block not found')
s=s.replace(old,new,1)

old='''        if(screenAction!=null){
            boolean hasAdd = "world".equals(mode) || "timer".equals(mode);
            screenAction.setVisibility(hasAdd?View.VISIBLE:View.GONE);
            screenAction.setOnClickListener(v -> { if("world".equals(mode)) showZonePicker(); else if("timer".equals(mode)) showAddTimerDialog(); });
        }
'''
new='''        if(worldLayoutAction!=null){
            boolean world="world".equals(mode);worldLayoutAction.setVisibility(world?View.VISIBLE:View.GONE);
            if(world){worldLayoutAction.setText(worldOverview()?"☷":"▦");worldLayoutAction.setContentDescription(I18n.tr(this,worldOverview()?"詳細表示に切り替え":"一覧表示に切り替え"));}
        }
        if(screenAction!=null){
            boolean hasAdd = "world".equals(mode) || "timer".equals(mode);
            screenAction.setVisibility(hasAdd?View.VISIBLE:View.GONE);
            screenAction.setOnClickListener(v -> { if("world".equals(mode)) showZonePicker(); else if("timer".equals(mode)) showAddTimerDialog(); });
        }
'''
if old not in s: raise SystemExit('showMode action block not found')
s=s.replace(old,new,1)

start=s.index('    private void buildWorldClock() {')
end=s.index('    private void buildStickyWorldHeader(){',start)
new='''    private boolean worldOverview(){return p().getBoolean("world_overview_v201",true);}

    private void buildWorldClock() {
        if(worldOverview()){
            if(stickyWorld!=null)stickyWorld.setVisibility(View.GONE);
            body.setPadding(Ui.dp(this,10),Ui.dp(this,4),Ui.dp(this,10),Ui.dp(this,6));
            buildWorldOverview();
            return;
        }
        if(stickyWorld!=null)stickyWorld.setVisibility(View.VISIBLE);
        body.setPadding(Ui.dp(this,22),Ui.dp(this,6),Ui.dp(this,22),Ui.dp(this,36));
        buildStickyWorldHeader();

        LinearLayout setting=Ui.row(this);
        setting.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));
        TextView label=text("24時間表示",15,Ui.TEXT); setting.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch h24=new Switch(this); h24.setChecked(p().getBoolean(KEY_24H,true));
        h24.setOnCheckedChangeListener((v,checked)->{p().edit().putBoolean(KEY_24H,checked).apply();updateLiveUi();WorldClockWidgetProvider.refreshAll(this);});
        setting.addView(h24); body.addView(setting); body.addView(Ui.divider(this));

        TextView hint=Ui.text(this,"時計をタップするとデジタル / アナログが一括で切り替わります",12,Ui.MUTED);
        hint.setPadding(0,Ui.dp(this,10),0,0);body.addView(hint);

        TextView cityHeader=Ui.sectionHeader(this,"登録した都市"); cityHeader.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,6)); body.addView(cityHeader);
        LinkedHashSet<String> zones=loadZones();
        for(String zone:zones)addWorldCard(zone);
        applyWorldDisplayMode();
    }

    private void buildWorldOverview(){
        LinkedHashSet<String> zones=loadZones();
        int count=Math.max(1,zones.size());
        int columns=count<=4?2:count<=9?3:count<=16?4:5;
        int rows=(count+columns-1)/columns;
        int gap=Ui.dp(this,6);
        int screenW=getResources().getDisplayMetrics().widthPixels;
        int screenH=getResources().getDisplayMetrics().heightPixels;
        int usableW=Math.max(Ui.dp(this,220),screenW-Ui.dp(this,20)-gap*(columns-1));
        int cardW=Math.max(Ui.dp(this,54),usableW/columns);
        int usableH=Math.max(Ui.dp(this,240),screenH-Ui.dp(this,205)-gap*Math.max(0,rows-1));
        int cardH=Math.max(Ui.dp(this,34),usableH/rows);
        GridLayout grid=new GridLayout(this);grid.setColumnCount(columns);grid.setRowCount(rows);grid.setUseDefaultMargins(false);
        int index=0;for(String zone:zones){addWorldOverviewCard(grid,zone,index++,columns,cardW,cardH,gap);}
        body.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    }

    private void addWorldOverviewCard(GridLayout grid,String zoneId,int index,int columns,int cardW,int cardH,int gap){
        try{ZoneId.of(zoneId);}catch(Throwable t){return;}
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(Ui.dp(this,6),Ui.dp(this,4),Ui.dp(this,6),Ui.dp(this,4));card.setBackground(Ui.round(Ui.SURFACE,14,this));
        String nameText=WorldCityCatalog.savedWorldLabel(this,zoneId,friendlyZoneName(zoneId));
        TextView name=text(nameText,columns<=2?15:columns==3?13:11,Ui.TEXT);name.setTypeface(null,Typeface.BOLD);name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);card.addView(name,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout stage=new FrameLayout(this);TextView time=text("--:--",columns<=2?30:columns==3?25:columns==4?21:18,Ui.TEXT);time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);time.setGravity(Gravity.CENTER);stage.addView(time,new FrameLayout.LayoutParams(-1,-1));card.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        TextView detail=text("",columns<=2?11:10,Ui.MUTED);detail.setGravity(Gravity.CENTER);detail.setSingleLine(true);detail.setEllipsize(TextUtils.TruncateAt.END);card.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        card.setOnClickListener(v->openClockFace("world",zoneId,-1L));
        worldRows.put(zoneId,new WorldRow(stage,time,null,detail));
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=cardW;lp.height=cardH;lp.columnSpec=GridLayout.spec(index%columns);lp.rowSpec=GridLayout.spec(index/columns);lp.setMargins(index%columns==0?0:gap/2,index/columns==0?0:gap/2,index%columns==columns-1?0:gap/2,index/columns==rowsForGrid(grid)-1?0:gap/2);grid.addView(card,lp);
    }

    private int rowsForGrid(GridLayout grid){return Math.max(1,grid.getRowCount());}

    private String compactDayLabel(ZonedDateTime remote,ZonedDateTime local){
        long d=java.time.temporal.ChronoUnit.DAYS.between(local.toLocalDate(),remote.toLocalDate());
        if(d==0)return I18n.tr(this,"今日");if(d==1)return I18n.tr(this,"明日");if(d==-1)return I18n.tr(this,"昨日");
        return remote.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this)));
    }

'''
s=s[:start]+new+s[end:]

s=s.replace('''    private void applyWorldDisplayMode(){
        boolean analog=worldAnalog();
''','''    private void applyWorldDisplayMode(){
        if(worldOverview())return;
        boolean analog=worldAnalog();
''',1)

old='''        DateTimeFormatter tf = DateTimeFormatter.ofPattern(h24 ? "HH:mm:ss" : "hh:mm:ss a", I18n.locale(this));
        DateTimeFormatter df = DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"), I18n.locale(this));
'''
new='''        DateTimeFormatter tf = DateTimeFormatter.ofPattern(h24 ? "HH:mm:ss" : "hh:mm:ss a", I18n.locale(this));
        DateTimeFormatter compactTf = DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", I18n.locale(this));
        DateTimeFormatter df = DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"), I18n.locale(this));
'''
if old not in s: raise SystemExit('world formatter block not found')
s=s.replace(old,new,1)

pattern=r'''                r\.time\.setText\(now\.format\(tf\)\);\n                r\.analog\.configure\(p\(\)\.getBoolean\("analog_numbers",true\),p\(\)\.getBoolean\("analog_ticks",true\),p\(\)\.getBoolean\("analog_seconds",true\),0\);\n                r\.analog\.setHands\(now\.getHour\(\)%12\+now\.getMinute\(\)/60d,now\.getMinute\(\)\+now\.getSecond\(\)/60d,now\.getSecond\(\)\+now\.getNano\(\)/1_000_000_000d\);\n                r\.detail\.setText\(now\.format\(df\)\n                        \+ "  •  " \+ z\.getId\(\)\n                        \+ "\\nUTC" \+ formatOffset\(offset\)\n                        \+ "  •  " \+ I18n\.tr\(this,"端末との差"\)\+" " \+ formatDiff\(diff\)\n                        \+ "  •  " \+ I18n\.tr\(this,"夏時間"\)\+" " \+ \(dst \? "ON" : "OFF"\)\);'''
new='''                r.time.setText(now.format(worldOverview()?compactTf:tf));
                if(r.analog!=null){r.analog.configure(p().getBoolean("analog_numbers",true),p().getBoolean("analog_ticks",true),p().getBoolean("analog_seconds",true),0);r.analog.setHands(now.getHour()%12+now.getMinute()/60d,now.getMinute()+now.getSecond()/60d,now.getSecond()+now.getNano()/1_000_000_000d);}
                if(worldOverview())r.detail.setText(compactDayLabel(now,local)+"  ·  UTC"+formatOffset(offset));
                else r.detail.setText(now.format(df)
                        + "  •  " + z.getId()
                        + "\\nUTC" + formatOffset(offset)
                        + "  •  " + I18n.tr(this,"端末との差")+" " + formatDiff(diff)
                        + "  •  " + I18n.tr(this,"夏時間")+" " + (dst ? "ON" : "OFF"));'''
s2,n=re.subn(pattern,lambda m:new,s,count=1)
if n!=1: raise SystemExit('world row update block not found')
s=s2
write(p,s)

assert 'versionName = "2.0.1"' in read('build.gradle.kts')
assert 'world_overview_v201' in read(p)
assert 'buildWorldOverview()' in read(p)
assert 'compactDayLabel' in read(p)
print('Android 2.0.1 one-screen world clock patch applied')
