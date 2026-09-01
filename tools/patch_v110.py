from pathlib import Path
import re, runpy, shutil

runpy.run_path("tools/patch_v101.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"

# Multiple independent timers replace the old single timer state.
shutil.copyfile("tools/v110_templates/TimerStore.java", java/"TimerStore.java")
shutil.copyfile("tools/v110_templates/TimerReceiver.java", java/"TimerReceiver.java")

p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")
for line in [
'    private static final String KEY_TIMER_RUNNING = "timer_running";\n',
'    private static final String KEY_TIMER_END = "timer_end";\n',
'    private static final String KEY_TIMER_REMAINING = "timer_remaining";\n',
'    private static final String KEY_TIMER_DURATION = "timer_duration";\n']:
    s=s.replace(line,'')
s=s.replace('    private TextView localClock, localDetail, screenTitle;', '    private TextView localClock, localDetail, screenTitle;\n    private Button screenAction;')
s=s.replace('    private TextView timerDisplay, timerState;\n    private EditText timerHours, timerMinutes, timerSeconds;\n    private Button timerStartPause, timerReset;', '    private LinearLayout timerList;\n    private TextView timerEmpty;')
s=s.replace(
'''        screenTitle = Ui.title(this, "世界時計", 30);
        top.addView(screenTitle,new LinearLayout.LayoutParams(0,-2,1));
        outer.addView(top);''',
'''        screenTitle = Ui.title(this, "世界時計", 30);
        top.addView(screenTitle,new LinearLayout.LayoutParams(0,-2,1));
        screenAction = Ui.ghostButton(this,"＋");
        screenAction.setTextSize(27); screenAction.setMinWidth(Ui.dp(this,52)); screenAction.setVisibility(View.GONE);
        screenAction.setOnClickListener(v -> showAddTimerDialog());
        top.addView(screenAction);
        outer.addView(top);''')
s=s.replace(
'        if(screenTitle!=null) screenTitle.setText("stopwatch".equals(mode)?"ストップウォッチ":"timer".equals(mode)?"タイマー":"世界時計");\n        if(worldTab!=null){',
'        if(screenTitle!=null) screenTitle.setText("stopwatch".equals(mode)?"ストップウォッチ":"timer".equals(mode)?"タイマー":"世界時計");\n        if(screenAction!=null) screenAction.setVisibility("timer".equals(mode)?View.VISIBLE:View.GONE);\n        if(worldTab!=null){')
s=s.replace('worldTab=Ui.bottomTab(this,"世界時計","world".equals(mode));','worldTab=Ui.bottomTab(this,"時計","world".equals(mode));')
s=s.replace('        TextView heading = text("ストップウォッチ", 21, Ui.TEXT); heading.setTypeface(null, Typeface.BOLD); body.addView(heading);\n','')

start=s.index('    private void buildTimer() {')
end=s.index('    private void updateLiveUi() {',start)
new_timer=r'''    private void buildTimer() {
        timerList = new LinearLayout(this);
        timerList.setOrientation(LinearLayout.VERTICAL);
        body.addView(timerList);
        renderTimers();
    }

    private void renderTimers() {
        if (!"timer".equals(mode) || timerList == null) return;
        timerList.removeAllViews();
        List<TimerStore.Entry> timers = TimerStore.all(this);
        timers.sort(Comparator.comparingLong(e -> e.id));
        if (timers.isEmpty()) {
            timerEmpty = text("タイマーはありません\n右上の＋から追加できます", 15, Ui.MUTED);
            timerEmpty.setGravity(Gravity.CENTER); timerEmpty.setPadding(0, Ui.dp(this,56), 0, 0);
            timerList.addView(timerEmpty); return;
        }
        for (int i=0;i<timers.size();i++) { addTimerRow(timers.get(i)); if (i < timers.size()-1) timerList.addView(Ui.divider(this)); }
    }

    private void addTimerRow(TimerStore.Entry e) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0, Ui.dp(this,18), 0, Ui.dp(this,18));
        LinearLayout titleRow = new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        String label = e.label==null||e.label.isEmpty()?durationLabel(e.durationMs):e.label;
        TextView name = text(label, 16, Ui.TEXT); name.setTypeface(null, Typeface.BOLD); titleRow.addView(name, new LinearLayout.LayoutParams(0,-2,1));
        Button delete = Ui.ghostButton(this,"削除"); delete.setTextSize(13); delete.setTextColor(Ui.MUTED);
        delete.setOnClickListener(v -> { TimerReceiver.cancel(this,e.id); TimerStore.delete(this,e.id); renderTimers(); });
        titleRow.addView(delete); row.addView(titleRow);

        TextView remain = text(formatTimer(e.remaining()), 44, Ui.TEXT); remain.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL); remain.setTag("timer_remaining_"+e.id); row.addView(remain, Ui.gapTop(this,6));
        TextView state = text(timerStateText(e), 13, Ui.MUTED); state.setTag("timer_state_"+e.id); row.addView(state, Ui.gapTop(this,3));

        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        String startText=e.running?"一時停止":(e.finished()?"もう一度":(e.remainingMs<e.durationMs?"再開":"スタート"));
        Button startPause = Ui.ghostButton(this,startText); startPause.setTextColor(Ui.ACCENT); startPause.setOnClickListener(v -> toggleTimer(e.id));
        controls.addView(startPause,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        Button plus = Ui.ghostButton(this,"＋1分"); plus.setOnClickListener(v -> addMinute(e.id)); controls.addView(plus,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        Button reset = Ui.ghostButton(this,"リセット"); reset.setOnClickListener(v -> resetTimer(e.id)); controls.addView(reset,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        row.addView(controls, Ui.gapTop(this,8)); timerList.addView(row);
    }

    private String timerStateText(TimerStore.Entry e) {
        if (e.running) return "終了予定  " + new java.text.SimpleDateFormat("HH:mm:ss",Locale.JAPAN).format(new java.util.Date(e.endMs));
        if (e.finished()) return "終了";
        if (e.remainingMs < e.durationMs) return "一時停止中";
        return durationLabel(e.durationMs);
    }

    private String durationLabel(long ms) {
        long sec=Math.max(1L,ms/1000L), h=sec/3600, m=(sec/60)%60, x=sec%60;
        if(h>0)return h+"時間 "+m+"分"; if(m>0&&x>0)return m+"分 "+x+"秒"; if(m>0)return m+"分"; return x+"秒";
    }

    private void showAddTimerDialog() {
        if (!"timer".equals(mode)) return;
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),0);
        EditText label=new EditText(this); label.setHint("名前（任意）"); label.setSingleLine(true); box.addView(label);
        TextView caption=text("時間",13,Ui.MUTED); caption.setPadding(0,Ui.dp(this,14),0,0); box.addView(caption);
        LinearLayout inputs=new LinearLayout(this); EditText h=numberBox("時"), m=numberBox("分"), sec=numberBox("秒");
        inputs.addView(h,new LinearLayout.LayoutParams(0,-2,1)); inputs.addView(m,new LinearLayout.LayoutParams(0,-2,1)); inputs.addView(sec,new LinearLayout.LayoutParams(0,-2,1)); box.addView(inputs);
        LinearLayout quick=new LinearLayout(this); int[] mins={1,3,5,10};
        for(int value:mins){ Button b=Ui.ghostButton(this,value+"分"); b.setTextSize(13); b.setOnClickListener(v->{h.setText("");m.setText(String.valueOf(value));sec.setText("");}); quick.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1)); }
        box.addView(quick,Ui.gapTop(this,8));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("タイマーを追加").setView(box).setNegativeButton("キャンセル",null).setPositiveButton("追加",null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long total=(parseLong(h)*3600L+parseLong(m)*60L+parseLong(sec))*1000L;
            if(total<=0){Toast.makeText(this,"時間を設定してください",Toast.LENGTH_SHORT).show();return;}
            TimerStore.add(this,label.getText().toString().trim(),total); d.dismiss(); renderTimers();
        })); d.show();
    }

    private EditText numberBox(String hint) {
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(Ui.MUTED_2); e.setTextColor(Ui.TEXT);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); e.setGravity(Gravity.CENTER); e.setTextSize(18); e.setSingleLine(true);
        e.setPadding(Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10)); e.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,10,this)); return e;
    }
    private long parseLong(EditText e){ try{return Long.parseLong(e.getText().toString().trim());}catch(Exception x){return 0L;} }

    private void toggleTimer(long id) {
        TimerStore.Entry e=TimerStore.find(this,id); if(e==null)return;
        if(e.running){ e.remainingMs=e.remaining(); e.running=false; e.endMs=0L; TimerReceiver.cancel(this,id); }
        else { long rem=e.remainingMs>0?e.remainingMs:e.durationMs; e.remainingMs=rem; e.endMs=System.currentTimeMillis()+rem; e.running=true; TimerReceiver.schedule(this,id,e.endMs); }
        TimerStore.update(this,e); renderTimers();
    }

    private void addMinute(long id) {
        TimerStore.Entry e=TimerStore.find(this,id); if(e==null)return;
        e.durationMs += 60000L;
        if(e.running){ e.endMs += 60000L; e.remainingMs=e.remaining(); TimerReceiver.schedule(this,id,e.endMs); }
        else if(e.finished()) e.remainingMs=60000L; else e.remainingMs += 60000L;
        TimerStore.update(this,e); renderTimers();
    }

    private void resetTimer(long id) {
        TimerStore.Entry e=TimerStore.find(this,id); if(e==null)return;
        TimerReceiver.cancel(this,id); e.running=false; e.endMs=0L; e.remainingMs=e.durationMs; TimerStore.update(this,e); renderTimers();
    }

    private String formatTimer(long ms){ long total=(ms+999)/1000L; long h=total/3600; long m=(total/60)%60; long x=total%60; return String.format(Locale.US,"%02d:%02d:%02d",h,m,x); }

    private void updateTimers() {
        if(!"timer".equals(mode)||timerList==null)return; boolean rebuild=false;
        for(TimerStore.Entry e:TimerStore.all(this)){
            long rem=e.remaining(); if(e.running&&rem<=0){ TimerReceiver.complete(this,e.id); rebuild=true; continue; }
            TextView time=timerList.findViewWithTag("timer_remaining_"+e.id); TextView state=timerList.findViewWithTag("timer_state_"+e.id);
            if(time!=null)time.setText(formatTimer(rem)); if(state!=null)state.setText(timerStateText(e));
        }
        if(rebuild)renderTimers();
    }

'''
s=s[:start]+new_timer+s[end:]
s=s.replace('        else if("timer".equals(mode)) updateTimer();','        else if("timer".equals(mode)) updateTimers();')
p.write_text(s,encoding="utf-8")

# Compact, single-line bottom navigation instead of oversized text blocks.
p=java/"Ui.java"
s=p.read_text(encoding="utf-8")
s=s.replace('b.setAllCaps(false); b.setText(text); b.setTextSize(12);', 'b.setAllCaps(false); b.setText(text); b.setTextSize(10.5f); b.setSingleLine(true);')
p.write_text(s,encoding="utf-8")

p=java/"MainActivity.java"
s=p.read_text(encoding="utf-8").replace('Ui.bottomTab(this,"世界時計",false)','Ui.bottomTab(this,"時計",false)')
p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+', 'versionCode = 29', s)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "1.1.0"', s)
p.write_text(s,encoding="utf-8")

print("WakeGuard v1.1.0: conventional timer list + unlimited independent timers applied")
