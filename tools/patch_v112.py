from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v111.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"

p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")

# Quick editor state: direct HH:MM:SS input plus increment/decrement actions.
s=s.replace('    private LinearLayout timerList;\n    private TextView timerEmpty;',
'''    private LinearLayout timerList;\n    private TextView timerEmpty;\n    private EditText quickHours, quickMinutes, quickSeconds;''')

start=s.index('    private void buildTimer() {')
end=s.index('    private void renderTimers() {', start)
quick_block=r'''    private void buildTimer() {
        // Compact, conventional timer editor: editable duration + small text actions.
        TextView quickTitle=text("すぐ使う",16,Ui.TEXT); quickTitle.setTypeface(null,Typeface.BOLD); body.addView(quickTitle);
        TextView quickHint=text("時間を入力するか、下の＋ボタンで調整",12,Ui.MUTED); body.addView(quickHint,Ui.gapTop(this,3));

        LinearLayout durationRow=new LinearLayout(this); durationRow.setGravity(Gravity.CENTER_VERTICAL); durationRow.setPadding(0,Ui.dp(this,10),0,0);
        quickHours=quickNumber("時"); quickMinutes=quickNumber("分"); quickSeconds=quickNumber("秒");
        durationRow.addView(quickHours,new LinearLayout.LayoutParams(0,Ui.dp(this,58),1));
        TextView c1=text(":",28,Ui.MUTED); c1.setGravity(Gravity.CENTER); durationRow.addView(c1,new LinearLayout.LayoutParams(Ui.dp(this,24),-1));
        durationRow.addView(quickMinutes,new LinearLayout.LayoutParams(0,Ui.dp(this,58),1));
        TextView c2=text(":",28,Ui.MUTED); c2.setGravity(Gravity.CENTER); durationRow.addView(c2,new LinearLayout.LayoutParams(Ui.dp(this,24),-1));
        durationRow.addView(quickSeconds,new LinearLayout.LayoutParams(0,Ui.dp(this,58),1));
        body.addView(durationRow);

        HorizontalScrollView adjustScroll=new HorizontalScrollView(this); adjustScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout adjust=new LinearLayout(this); adjust.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels={"−1分","＋10秒","＋30秒","＋1分","＋5分","＋10分"};
        long[] deltas={-60000L,10000L,30000L,60000L,300000L,600000L};
        for(int i=0;i<labels.length;i++){
            final long delta=deltas[i]; TextView a=timerTextAction(labels[i],14,Ui.TEXT);
            a.setOnClickListener(v->adjustQuickDuration(delta)); adjust.addView(a);
        }
        adjustScroll.addView(adjust); body.addView(adjustScroll,Ui.gapTop(this,8));

        LinearLayout presets=new LinearLayout(this); presets.setGravity(Gravity.CENTER_VERTICAL);
        int[] mins={1,3,5,10,15,30};
        for(int value:mins){ TextView a=timerTextAction(value+"分",13,Ui.MUTED); a.setOnClickListener(v->setQuickDuration(value*60000L)); presets.addView(a,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1)); }
        body.addView(presets,Ui.gapTop(this,3));

        LinearLayout launch=new LinearLayout(this); launch.setGravity(Gravity.CENTER_VERTICAL);
        TextView once=timerTextAction("今回だけ開始",15,Ui.ACCENT); once.setTypeface(null,Typeface.BOLD); once.setGravity(Gravity.CENTER);
        once.setOnClickListener(v->startQuickEditor(false)); launch.addView(once,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
        TextView save=timerTextAction("保存して開始",15,Ui.TEXT); save.setGravity(Gravity.CENTER);
        save.setOnClickListener(v->startQuickEditor(true)); launch.addView(save,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
        body.addView(launch,Ui.gapTop(this,8));
        body.addView(Ui.divider(this),Ui.gapTop(this,8));

        LinearLayout listHeading=new LinearLayout(this); listHeading.setGravity(Gravity.CENTER_VERTICAL); listHeading.setPadding(0,Ui.dp(this,20),0,Ui.dp(this,2));
        TextView listTitle=text("タイマー",16,Ui.TEXT); listTitle.setTypeface(null,Typeface.BOLD); listHeading.addView(listTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView addMore=timerTextAction("詳細設定",13,Ui.ACCENT); addMore.setOnClickListener(v->showAddTimerDialog()); listHeading.addView(addMore);
        body.addView(listHeading);

        timerList=new LinearLayout(this); timerList.setOrientation(LinearLayout.VERTICAL); body.addView(timerList); renderTimers();
    }

    private EditText quickNumber(String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(Ui.MUTED_2); e.setTextColor(Ui.TEXT); e.setTextSize(27);
        e.setGravity(Gravity.CENTER); e.setSingleLine(true); e.setSelectAllOnFocus(true); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),0);
        try{e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.MUTED_2));}catch(Throwable ignored){}
        return e;
    }

    private TextView timerTextAction(String label,float size,int color){
        TextView t=text(label,size,color); t.setGravity(Gravity.CENTER); t.setClickable(true); t.setFocusable(true);
        t.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
        try{android.util.TypedValue v=new android.util.TypedValue(); if(getTheme().resolveAttribute(android.R.attr.selectableItemBackground,v,true))t.setBackgroundResource(v.resourceId); else t.setBackgroundColor(android.graphics.Color.TRANSPARENT);}catch(Throwable ignored){t.setBackgroundColor(android.graphics.Color.TRANSPARENT);}
        return t;
    }

    private long quickDuration(){
        return Math.max(0L,(parseLong(quickHours)*3600L+parseLong(quickMinutes)*60L+parseLong(quickSeconds))*1000L);
    }

    private void setQuickDuration(long ms){
        long sec=Math.max(0L,ms/1000L); long h=sec/3600L, m=(sec/60L)%60L, x=sec%60L;
        quickHours.setText(h==0?"":String.valueOf(h)); quickMinutes.setText(m==0?"":String.valueOf(m)); quickSeconds.setText(x==0?"":String.valueOf(x));
    }

    private void adjustQuickDuration(long delta){ setQuickDuration(Math.max(0L,quickDuration()+delta)); }

    private void startQuickEditor(boolean saved){
        long total=quickDuration(); if(total<=0){Toast.makeText(this,"時間を入力してください",Toast.LENGTH_SHORT).show();return;}
        TimerStore.Entry e=saved?TimerStore.addSaved(this,"",total):TimerStore.addTemporary(this,"",total); startTimerEntry(e); renderTimers();
    }

'''
s=s[:start]+quick_block+s[end:]

# Timer list rows: remove the three boxed controls and use normal text actions with separators.
row_start=s.index('    private void addTimerRow(TimerStore.Entry e) {')
row_end=s.index('    private String timerStateText', row_start)
row_block=r'''    private void addTimerRow(TimerStore.Entry e) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,16));
        LinearLayout titleRow=new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        String label=e.label==null||e.label.isEmpty()?durationLabel(e.durationMs):e.label;
        TextView name=text(label,16,Ui.TEXT); name.setTypeface(null,Typeface.BOLD); titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if(!e.saved){TextView save=timerTextAction("保存",13,Ui.ACCENT);save.setOnClickListener(v->{TimerStore.makeSaved(this,e.id);renderTimers();});titleRow.addView(save);}
        TextView delete=timerTextAction("削除",13,Ui.MUTED); delete.setOnClickListener(v->{TimerReceiver.cancel(this,e.id);TimerStore.delete(this,e.id);renderTimers();}); titleRow.addView(delete); row.addView(titleRow);

        TextView remain=text(formatTimer(e.remaining()),40,Ui.TEXT); remain.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); remain.setTag("timer_remaining_"+e.id); row.addView(remain,Ui.gapTop(this,4));
        TextView state=text(timerStateText(e),13,Ui.MUTED); state.setTag("timer_state_"+e.id); row.addView(state,Ui.gapTop(this,1));

        LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL); controls.setPadding(0,Ui.dp(this,5),0,0);
        String startText=e.running?"一時停止":(e.finished()?"もう一度":(e.remainingMs<e.durationMs?"再開":"スタート"));
        TextView startPause=timerTextAction(startText,14,Ui.ACCENT); startPause.setOnClickListener(v->toggleTimer(e.id)); controls.addView(startPause);
        TextView plus=timerTextAction("＋1分",14,Ui.TEXT); plus.setOnClickListener(v->addMinute(e.id)); controls.addView(plus);
        TextView reset=timerTextAction("リセット",14,Ui.MUTED); reset.setOnClickListener(v->resetTimer(e.id)); controls.addView(reset);
        row.addView(controls); timerList.addView(row);
    }

'''
s=s[:row_start]+row_block+s[row_end:]

# Dialog duration fields should look like regular Android inputs instead of synthetic rounded boxes.
s=s.replace('        e.setPadding(Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10)); e.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,10,this)); return e;',
'''        e.setPadding(Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10));
        try{e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.MUTED_2));}catch(Throwable ignored){} return e;''')

p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+','versionCode = 31',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.1.2"',s)
p.write_text(s,encoding="utf-8")
print("WakeGuard v1.1.2: detailed quick timer editor + flatter native-style timer controls applied")
