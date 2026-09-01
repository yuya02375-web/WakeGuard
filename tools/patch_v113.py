from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v112.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"

# Make common actions feel like normal Android controls rather than large synthetic tiles.
p=java/"Ui.java"
s=p.read_text(encoding="utf-8")
s=s.replace('''    public static Button ghostButton(Activity a, String s) {
        Button b = button(a,s,false);
        b.setBackgroundColor(0x00000000);
        b.setTextColor(TEXT);
        return b;
    }''','''    public static Button ghostButton(Activity a, String s) {
        Button b = new Button(a);
        b.setText(s); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(TEXT); b.setBackgroundColor(0x00000000);
        b.setMinHeight(dp(a,44)); b.setMinWidth(0);
        b.setPadding(dp(a,10),0,dp(a,10),0);
        return b;
    }''')
s=s.replace('l.setBackground(round(SURFACE, 16, a));','l.setBackground(round(SURFACE, 12, a));')
s=s.replace('b.setAllCaps(false); b.setText(text); b.setTextSize(10.5f); b.setSingleLine(true);','b.setAllCaps(false); b.setText(text); b.setTextSize(11.5f); b.setSingleLine(true);')
p.write_text(s,encoding="utf-8")

p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")
s=s.replace('    private EditText quickHours, quickMinutes, quickSeconds;', '    private EditText quickHours, quickMinutes, quickSeconds;\n    private Switch quickSave;')
s=s.replace('screenTitle = Ui.title(this, "世界時計", 30);','screenTitle = Ui.title(this, "世界時計", 28);')
s=s.replace('''        if(screenAction!=null) screenAction.setVisibility("timer".equals(mode)?View.VISIBLE:View.GONE);''','''        if(screenAction!=null){
            boolean hasAdd = "world".equals(mode) || "timer".equals(mode);
            screenAction.setVisibility(hasAdd?View.VISIBLE:View.GONE);
            screenAction.setOnClickListener(v -> { if("world".equals(mode)) showZonePicker(); else if("timer".equals(mode)) showAddTimerDialog(); });
        }''')

# World clock: flat list rows, no large cards and no oversized add button.
start=s.index('    private void buildWorldClock() {')
end=s.index('    private LinkedHashSet<String> loadZones()',start)
s=s[:start]+r'''    private void buildWorldClock() {
        LinearLayout setting=Ui.row(this);
        TextView label=text("24時間表示",15,Ui.TEXT); setting.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch h24=new Switch(this); h24.setChecked(p().getBoolean(KEY_24H,true));
        h24.setOnCheckedChangeListener((v,checked)->{p().edit().putBoolean(KEY_24H,checked).apply();updateLiveUi();});
        setting.addView(h24); body.addView(setting); body.addView(Ui.divider(this));

        TextView cityHeader=Ui.sectionHeader(this,"都市"); cityHeader.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,6)); body.addView(cityHeader);
        LinkedHashSet<String> zones=loadZones();
        for(String zone:zones)addWorldCard(zone);
    }

'''+s[end:]

start=s.index('    private void addWorldCard(String zoneId) {')
end=s.index('    private void updateWorldRows()',start)
s=s[:start]+r'''    private void addWorldCard(String zoneId) {
        ZoneId zone; try{zone=ZoneId.of(zoneId);}catch(Throwable t){return;}
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,14));
        LinearLayout titleRow=new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(friendlyZoneName(zoneId),17,Ui.TEXT); name.setTypeface(null,Typeface.BOLD); titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView remove=timerTextAction("削除",13,Ui.MUTED); remove.setOnClickListener(v->{LinkedHashSet<String> zones=loadZones();zones.remove(zoneId);saveZones(zones);showMode("world");}); titleRow.addView(remove);
        row.addView(titleRow);
        TextView t=text("--:--:--",33,Ui.TEXT); t.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); row.addView(t,Ui.gapTop(this,3));
        TextView d=text(zoneId,13,Ui.MUTED); row.addView(d,Ui.gapTop(this,2)); worldRows.put(zoneId,new WorldRow(t,d));
        body.addView(row); body.addView(Ui.divider(this));
    }

'''+s[end:]
s=s.replace('return names.containsKey(id) ? names.get(id) + "  (" + id + ")" : last + "  (" + id + ")";','return names.containsKey(id) ? names.get(id) : last;')

# Stopwatch: one clear primary action, one quiet secondary action.
start=s.index('    private void buildStopwatch() {')
end=s.index('    private long stopwatchElapsed()',start)
s=s[:start]+r'''    private void buildStopwatch() {
        stopwatchDisplay=text("00:00:00.00",46,Ui.TEXT);
        stopwatchDisplay.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); stopwatchDisplay.setGravity(Gravity.CENTER); stopwatchDisplay.setPadding(0,Ui.dp(this,34),0,Ui.dp(this,28)); body.addView(stopwatchDisplay);
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        stopwatchLapReset=Ui.ghostButton(this,"リセット"); row.addView(stopwatchLapReset,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        stopwatchStartPause=Ui.button(this,"スタート",true); row.addView(stopwatchStartPause,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1)); body.addView(row);
        stopwatchStartPause.setOnClickListener(v->toggleStopwatch()); stopwatchLapReset.setOnClickListener(v->lapOrReset());
        lapList=text("",15,Ui.MUTED); lapList.setTypeface(Typeface.MONOSPACE); lapList.setPadding(0,Ui.dp(this,24),0,Ui.dp(this,8)); body.addView(lapList); renderLaps();
    }

'''+s[end:]

# Timer: direct input + small adjustments + one Save switch + one Start action.
start=s.index('    private void buildTimer() {')
end=s.index('    private EditText quickNumber',start)
s=s[:start]+r'''    private void buildTimer() {
        TextView quickTitle=text("すぐ使う",16,Ui.TEXT); quickTitle.setTypeface(null,Typeface.BOLD); body.addView(quickTitle);

        LinearLayout durationRow=new LinearLayout(this); durationRow.setGravity(Gravity.CENTER_VERTICAL); durationRow.setPadding(0,Ui.dp(this,12),0,0);
        quickHours=quickNumber("時"); quickMinutes=quickNumber("分"); quickSeconds=quickNumber("秒");
        durationRow.addView(quickHours,new LinearLayout.LayoutParams(0,Ui.dp(this,62),1));
        TextView c1=text(":",27,Ui.MUTED);c1.setGravity(Gravity.CENTER);durationRow.addView(c1,new LinearLayout.LayoutParams(Ui.dp(this,24),-1));
        durationRow.addView(quickMinutes,new LinearLayout.LayoutParams(0,Ui.dp(this,62),1));
        TextView c2=text(":",27,Ui.MUTED);c2.setGravity(Gravity.CENTER);durationRow.addView(c2,new LinearLayout.LayoutParams(Ui.dp(this,24),-1));
        durationRow.addView(quickSeconds,new LinearLayout.LayoutParams(0,Ui.dp(this,62),1)); body.addView(durationRow);

        TextView presetLabel=text("よく使う時間",12,Ui.MUTED); body.addView(presetLabel,Ui.gapTop(this,12));
        HorizontalScrollView presetsScroll=new HorizontalScrollView(this);presetsScroll.setHorizontalScrollBarEnabled(false); LinearLayout presets=new LinearLayout(this);
        String[] presetNames={"30秒","1分","3分","5分","10分","30分","1時間"}; long[] presetMs={30000L,60000L,180000L,300000L,600000L,1800000L,3600000L};
        for(int i=0;i<presetNames.length;i++){final long value=presetMs[i];TextView a=timerTextAction(presetNames[i],13,Ui.TEXT);a.setOnClickListener(v->setQuickDuration(value));presets.addView(a);} presetsScroll.addView(presets);body.addView(presetsScroll,Ui.gapTop(this,2));

        TextView adjustLabel=text("調整",12,Ui.MUTED); body.addView(adjustLabel,Ui.gapTop(this,8));
        HorizontalScrollView adjustScroll=new HorizontalScrollView(this);adjustScroll.setHorizontalScrollBarEnabled(false);LinearLayout adjust=new LinearLayout(this);
        String[] labels={"−1分","＋10秒","＋30秒","＋1分","＋5分","＋10分","＋1時間"}; long[] deltas={-60000L,10000L,30000L,60000L,300000L,600000L,3600000L};
        for(int i=0;i<labels.length;i++){final long delta=deltas[i];TextView a=timerTextAction(labels[i],13,Ui.TEXT);a.setOnClickListener(v->adjustQuickDuration(delta));adjust.addView(a);} adjustScroll.addView(adjust);body.addView(adjustScroll,Ui.gapTop(this,2));

        LinearLayout saveRow=Ui.row(this); saveRow.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,8)); TextView saveLabel=text("この時間を保存",15,Ui.TEXT);saveRow.addView(saveLabel,new LinearLayout.LayoutParams(0,-2,1)); quickSave=new Switch(this);quickSave.setChecked(false);saveRow.addView(quickSave);body.addView(saveRow);
        Button start=Ui.button(this,"開始",true);start.setOnClickListener(v->startQuickEditor());body.addView(start,new LinearLayout.LayoutParams(-1,Ui.dp(this,50))); body.addView(Ui.divider(this),Ui.gapTop(this,18));

        TextView listTitle=text("タイマー",16,Ui.TEXT);listTitle.setTypeface(null,Typeface.BOLD);listTitle.setPadding(0,Ui.dp(this,20),0,Ui.dp(this,4));body.addView(listTitle);
        timerList=new LinearLayout(this);timerList.setOrientation(LinearLayout.VERTICAL);body.addView(timerList);renderTimers();
    }

'''+s[end:]
s=s.replace('''    private void startQuickEditor(boolean saved){
        long total=quickDuration(); if(total<=0){Toast.makeText(this,"時間を入力してください",Toast.LENGTH_SHORT).show();return;}
        TimerStore.Entry e=saved?TimerStore.addSaved(this,"",total):TimerStore.addTemporary(this,"",total); startTimerEntry(e); renderTimers();
    }''','''    private void startQuickEditor(){
        long total=quickDuration(); if(total<=0){Toast.makeText(this,"時間を入力してください",Toast.LENGTH_SHORT).show();return;}
        boolean saved=quickSave!=null&&quickSave.isChecked(); TimerStore.Entry e=saved?TimerStore.addSaved(this,"",total):TimerStore.addTemporary(this,"",total); startTimerEntry(e); renderTimers();
    }''')

# Running/saved timers: flat row plus overflow menu instead of a matrix of equal buttons.
start=s.index('    private void addTimerRow(TimerStore.Entry e) {')
end=s.index('    private String timerStateText',start)
s=s[:start]+r'''    private void addTimerRow(TimerStore.Entry e) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(0,Ui.dp(this,15),0,Ui.dp(this,15));
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        String label=e.label==null||e.label.isEmpty()?durationLabel(e.durationMs):e.label;TextView name=text(label,15,Ui.TEXT);titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=timerTextAction("︙",22,Ui.MUTED);more.setOnClickListener(v->showTimerMenu(more,e.id));titleRow.addView(more);row.addView(titleRow);
        TextView remain=text(formatTimer(e.remaining()),38,Ui.TEXT);remain.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);remain.setTag("timer_remaining_"+e.id);row.addView(remain,Ui.gapTop(this,2));
        TextView state=text(timerStateText(e),12,Ui.MUTED);state.setTag("timer_state_"+e.id);row.addView(state,Ui.gapTop(this,1));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(0,Ui.dp(this,4),0,0);
        String startText=e.running?"一時停止":(e.finished()?"もう一度":(e.remainingMs<e.durationMs?"再開":"スタート"));TextView startPause=timerTextAction(startText,14,Ui.ACCENT);startPause.setOnClickListener(v->toggleTimer(e.id));controls.addView(startPause);
        TextView plus=timerTextAction("＋1分",14,Ui.TEXT);plus.setOnClickListener(v->addMinute(e.id));controls.addView(plus);row.addView(controls);timerList.addView(row);
    }

    private void showTimerMenu(View anchor,long id){
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;PopupMenu menu=new PopupMenu(this,anchor);
        if(!e.saved)menu.getMenu().add("保存");menu.getMenu().add("リセット");menu.getMenu().add("削除");
        menu.setOnMenuItemClickListener(item->{String x=String.valueOf(item.getTitle());if("保存".equals(x)){TimerStore.makeSaved(this,id);renderTimers();return true;}if("リセット".equals(x)){resetTimer(id);return true;}if("削除".equals(x)){TimerReceiver.cancel(this,id);TimerStore.delete(this,id);renderTimers();return true;}return false;});menu.show();
    }

'''+s[end:]

# Detailed timer dialog follows the same single Start + Save switch pattern.
start=s.index('    private void showAddTimerDialog() {')
end=s.index('    private void createTimerFromDialog',start)
s=s[:start]+r'''    private void showAddTimerDialog() {
        if(!"timer".equals(mode))return;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),0);
        EditText label=new EditText(this);label.setHint("名前（任意）");label.setSingleLine(true);box.addView(label);
        TextView caption=text("時間",13,Ui.MUTED);caption.setPadding(0,Ui.dp(this,14),0,0);box.addView(caption);
        LinearLayout inputs=new LinearLayout(this);EditText h=numberBox("時"),m=numberBox("分"),sec=numberBox("秒");inputs.addView(h,new LinearLayout.LayoutParams(0,-2,1));inputs.addView(m,new LinearLayout.LayoutParams(0,-2,1));inputs.addView(sec,new LinearLayout.LayoutParams(0,-2,1));box.addView(inputs);
        Switch save=new Switch(this);save.setText("このタイマーを保存");save.setTextColor(Ui.TEXT);save.setChecked(true);box.addView(save,Ui.gapTop(this,14));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("タイマーを作成").setView(box).setNegativeButton("キャンセル",null).setPositiveButton("開始",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->createTimerFromDialog(d,label,h,m,sec,save.isChecked())));d.show();
    }

'''+s[end:]
p.write_text(s,encoding="utf-8")

# Alarm editor fields should use native underline inputs, not rounded generated-looking boxes.
p=java/"AlarmEditorActivity.java"
s=p.read_text(encoding="utf-8")
s=s.replace('''private EditText input(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Ui.MUTED_2);e.setTextColor(Ui.TEXT);e.setTextSize(17);e.setInputType(type);e.setSingleLine(true);e.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));e.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,10,this));return e;}''','''private EditText input(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Ui.MUTED_2);e.setTextColor(Ui.TEXT);e.setTextSize(17);e.setInputType(type);e.setSingleLine(true);e.setPadding(0,Ui.dp(this,9),0,Ui.dp(this,9));try{e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.MUTED_2));}catch(Throwable ignored){}return e;}''')
p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+','versionCode = 32',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.1.3"',s)
p.write_text(s,encoding="utf-8")
print("WakeGuard v1.1.3: flatter native clock UI, contextual actions, and timer cleanup applied")
