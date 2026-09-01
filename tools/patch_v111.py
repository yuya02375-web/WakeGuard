from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v110.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"

# TimerStore: keep active temporary timers internally so they survive app backgrounding,
# but mark them as unsaved and remove them automatically after they finish.
p=java/"TimerStore.java"
s=p.read_text(encoding="utf-8")
s=s.replace('        public boolean running;','        public boolean running, saved;')
s=s.replace('        public Entry(long id,String label,long durationMs,long remainingMs,long endMs,boolean running){','        public Entry(long id,String label,long durationMs,long remainingMs,long endMs,boolean running,boolean saved){')
s=s.replace('            this.endMs=Math.max(0L,endMs); this.running=running;','            this.endMs=Math.max(0L,endMs); this.running=running; this.saved=saved;')
s=s.replace('o.optBoolean("running",false));','o.optBoolean("running",false),o.optBoolean("saved",true));')
s=s.replace('        o.put("remaining",e.remainingMs); o.put("end",e.endMs); o.put("running",e.running);','        o.put("remaining",e.remainingMs); o.put("end",e.endMs); o.put("running",e.running); o.put("saved",e.saved);')
old='''    public static synchronized Entry add(Context c,String label,long durationMs){
        long id=Math.max(1000L,p(c).getLong(KEY_NEXT_ID,1000L));
        p(c).edit().putLong(KEY_NEXT_ID,id+1).commit();
        Entry e=new Entry(id,label,durationMs,durationMs,0L,false);
        List<Entry> list=all(c); list.add(e); save(c,list); return e;
    }
'''
new='''    private static synchronized Entry addInternal(Context c,String label,long durationMs,boolean saved){
        long id=Math.max(1000L,p(c).getLong(KEY_NEXT_ID,1000L));
        p(c).edit().putLong(KEY_NEXT_ID,id+1).commit();
        Entry e=new Entry(id,label,durationMs,durationMs,0L,false,saved);
        List<Entry> list=all(c); list.add(e); save(c,list); return e;
    }

    public static synchronized Entry add(Context c,String label,long durationMs){ return addSaved(c,label,durationMs); }
    public static synchronized Entry addSaved(Context c,String label,long durationMs){ return addInternal(c,label,durationMs,true); }
    public static synchronized Entry addTemporary(Context c,String label,long durationMs){ return addInternal(c,label,durationMs,false); }
'''
s=s.replace(old,new)
s=s.replace('    public static synchronized void delete(Context c,long id){ List<Entry> list=all(c); list.removeIf(e->e.id==id); save(c,list); }','''    public static synchronized void makeSaved(Context c,long id){ Entry e=find(c,id); if(e==null)return; e.saved=true; update(c,e); }
    public static synchronized void delete(Context c,long id){ List<Entry> list=all(c); list.removeIf(e->e.id==id); save(c,list); }''')
s=s.replace('Entry e=new Entry(id,"",duration,remaining,end,running&&remaining>0);','Entry e=new Entry(id,"",duration,remaining,end,running&&remaining>0,true);')
p.write_text(s,encoding="utf-8")

# Unsaved timers notify normally, then disappear from the reusable timer list.
p=java/"TimerReceiver.java"
s=p.read_text(encoding="utf-8")
s=s.replace('        e.running=false; e.remainingMs=0L; e.endMs=0L; TimerStore.update(c,e); cancel(c,id); ensureChannel(c);','''        e.running=false; e.remainingMs=0L; e.endMs=0L;
        if(e.saved) TimerStore.update(c,e); else TimerStore.delete(c,id);
        cancel(c,id); ensureChannel(c);''')
p.write_text(s,encoding="utf-8")

# Timer screen: quick one-tap timers at the top, plus an explicit save / don't-save choice
# for custom timers. Temporary running timers can also be promoted with a Save button.
p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")
start=s.index('    private void buildTimer() {')
end=s.index('    private void updateLiveUi() {',start)
new_timer=r'''    private void buildTimer() {
        TextView quickTitle=text("すぐ使う",15,Ui.TEXT); quickTitle.setTypeface(null,Typeface.BOLD); body.addView(quickTitle);
        TextView quickHint=text("タップすると保存せず、そのまま開始します",12,Ui.MUTED); body.addView(quickHint,Ui.gapTop(this,3));
        LinearLayout quick=new LinearLayout(this); quick.setGravity(Gravity.CENTER_VERTICAL);
        int[] mins={1,3,5,10};
        for(int value:mins){
            Button b=Ui.ghostButton(this,value+"分"); b.setTextSize(14);
            b.setOnClickListener(v -> startQuickTimer(value*60000L));
            quick.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        }
        body.addView(quick,Ui.gapTop(this,10));

        TextView listTitle=text("タイマー",15,Ui.TEXT); listTitle.setTypeface(null,Typeface.BOLD); listTitle.setPadding(0,Ui.dp(this,24),0,0); body.addView(listTitle);
        timerList=new LinearLayout(this); timerList.setOrientation(LinearLayout.VERTICAL); body.addView(timerList); renderTimers();
    }

    private void renderTimers() {
        if(!"timer".equals(mode)||timerList==null)return;
        timerList.removeAllViews();
        List<TimerStore.Entry> timers=TimerStore.all(this);
        timers.sort((a,b)->{if(a.saved!=b.saved)return a.saved?1:-1;return Long.compare(a.id,b.id);});
        if(timers.isEmpty()){
            timerEmpty=text("保存したタイマーはありません",14,Ui.MUTED); timerEmpty.setGravity(Gravity.CENTER);
            timerEmpty.setPadding(0,Ui.dp(this,34),0,Ui.dp(this,16)); timerList.addView(timerEmpty); return;
        }
        for(int i=0;i<timers.size();i++){addTimerRow(timers.get(i));if(i<timers.size()-1)timerList.addView(Ui.divider(this));}
    }

    private void addTimerRow(TimerStore.Entry e) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,16));
        LinearLayout titleRow=new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        String label=e.label==null||e.label.isEmpty()?durationLabel(e.durationMs):e.label;
        TextView name=text(label,16,Ui.TEXT); name.setTypeface(null,Typeface.BOLD); titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if(!e.saved){Button save=Ui.ghostButton(this,"保存");save.setTextSize(13);save.setTextColor(Ui.ACCENT);save.setOnClickListener(v->{TimerStore.makeSaved(this,e.id);renderTimers();});titleRow.addView(save);}
        Button delete=Ui.ghostButton(this,"削除"); delete.setTextSize(13); delete.setTextColor(Ui.MUTED);
        delete.setOnClickListener(v->{TimerReceiver.cancel(this,e.id);TimerStore.delete(this,e.id);renderTimers();}); titleRow.addView(delete); row.addView(titleRow);

        TextView remain=text(formatTimer(e.remaining()),42,Ui.TEXT); remain.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); remain.setTag("timer_remaining_"+e.id); row.addView(remain,Ui.gapTop(this,5));
        TextView state=text(timerStateText(e),13,Ui.MUTED); state.setTag("timer_state_"+e.id); row.addView(state,Ui.gapTop(this,2));

        LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        String startText=e.running?"一時停止":(e.finished()?"もう一度":(e.remainingMs<e.durationMs?"再開":"スタート"));
        Button startPause=Ui.ghostButton(this,startText); startPause.setTextColor(Ui.ACCENT); startPause.setOnClickListener(v->toggleTimer(e.id));
        controls.addView(startPause,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        Button plus=Ui.ghostButton(this,"＋1分"); plus.setOnClickListener(v->addMinute(e.id)); controls.addView(plus,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        Button reset=Ui.ghostButton(this,"リセット"); reset.setOnClickListener(v->resetTimer(e.id)); controls.addView(reset,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        row.addView(controls,Ui.gapTop(this,7)); timerList.addView(row);
    }

    private String timerStateText(TimerStore.Entry e) {
        String prefix=e.saved?"":"今回だけ  ·  ";
        if(e.running)return prefix+"終了予定  "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.JAPAN).format(new java.util.Date(e.endMs));
        if(e.finished())return prefix+"終了";
        if(e.remainingMs<e.durationMs)return prefix+"一時停止中";
        return prefix+durationLabel(e.durationMs);
    }

    private String durationLabel(long ms) {
        long sec=Math.max(1L,ms/1000L),h=sec/3600,m=(sec/60)%60,x=sec%60;
        if(h>0)return h+"時間 "+m+"分"; if(m>0&&x>0)return m+"分 "+x+"秒"; if(m>0)return m+"分"; return x+"秒";
    }

    private void showAddTimerDialog() {
        if(!"timer".equals(mode))return;
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),0);
        EditText label=new EditText(this); label.setHint("名前（任意）"); label.setSingleLine(true); box.addView(label);
        TextView caption=text("時間",13,Ui.MUTED); caption.setPadding(0,Ui.dp(this,14),0,0); box.addView(caption);
        LinearLayout inputs=new LinearLayout(this); EditText h=numberBox("時"),m=numberBox("分"),sec=numberBox("秒");
        inputs.addView(h,new LinearLayout.LayoutParams(0,-2,1));inputs.addView(m,new LinearLayout.LayoutParams(0,-2,1));inputs.addView(sec,new LinearLayout.LayoutParams(0,-2,1));box.addView(inputs);
        LinearLayout quick=new LinearLayout(this);int[] mins={1,3,5,10};
        for(int value:mins){Button b=Ui.ghostButton(this,value+"分");b.setTextSize(13);b.setOnClickListener(v->{h.setText("");m.setText(String.valueOf(value));sec.setText("");});quick.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));}
        box.addView(quick,Ui.gapTop(this,8));
        TextView explain=text("今回だけ：終了後に一覧から消えます\n保存：次回も使えるタイマーとして残します",12,Ui.MUTED); box.addView(explain,Ui.gapTop(this,12));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("タイマーを作成").setView(box).setNegativeButton("キャンセル",null).setNeutralButton("保存して開始",null).setPositiveButton("今回だけ開始",null).create();
        d.setOnShowListener(x->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->createTimerFromDialog(d,label,h,m,sec,false));
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->createTimerFromDialog(d,label,h,m,sec,true));
        }); d.show();
    }

    private void createTimerFromDialog(AlertDialog d,EditText label,EditText h,EditText m,EditText sec,boolean saved){
        long total=(parseLong(h)*3600L+parseLong(m)*60L+parseLong(sec))*1000L;
        if(total<=0){Toast.makeText(this,"時間を設定してください",Toast.LENGTH_SHORT).show();return;}
        TimerStore.Entry e=saved?TimerStore.addSaved(this,label.getText().toString().trim(),total):TimerStore.addTemporary(this,label.getText().toString().trim(),total);
        startTimerEntry(e); d.dismiss(); renderTimers();
    }

    private void startQuickTimer(long durationMs){TimerStore.Entry e=TimerStore.addTemporary(this,"",durationMs);startTimerEntry(e);renderTimers();}
    private void startTimerEntry(TimerStore.Entry e){if(e==null)return;e.remainingMs=e.durationMs;e.endMs=System.currentTimeMillis()+e.durationMs;e.running=true;TimerStore.update(this,e);TimerReceiver.schedule(this,e.id,e.endMs);}

    private EditText numberBox(String hint) {
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(Ui.MUTED_2); e.setTextColor(Ui.TEXT);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); e.setGravity(Gravity.CENTER); e.setTextSize(18); e.setSingleLine(true);
        e.setPadding(Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10)); e.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,10,this)); return e;
    }
    private long parseLong(EditText e){try{return Long.parseLong(e.getText().toString().trim());}catch(Exception x){return 0L;}}

    private void toggleTimer(long id) {
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;
        if(e.running){e.remainingMs=e.remaining();e.running=false;e.endMs=0L;TimerReceiver.cancel(this,id);}
        else{long rem=e.remainingMs>0?e.remainingMs:e.durationMs;e.remainingMs=rem;e.endMs=System.currentTimeMillis()+rem;e.running=true;TimerReceiver.schedule(this,id,e.endMs);}
        TimerStore.update(this,e);renderTimers();
    }

    private void addMinute(long id) {
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;e.durationMs+=60000L;
        if(e.running){e.endMs+=60000L;e.remainingMs=e.remaining();TimerReceiver.schedule(this,id,e.endMs);}else if(e.finished())e.remainingMs=60000L;else e.remainingMs+=60000L;
        TimerStore.update(this,e);renderTimers();
    }

    private void resetTimer(long id) {TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;TimerReceiver.cancel(this,id);e.running=false;e.endMs=0L;e.remainingMs=e.durationMs;TimerStore.update(this,e);renderTimers();}
    private String formatTimer(long ms){long total=(ms+999)/1000L,h=total/3600,m=(total/60)%60,x=total%60;return String.format(Locale.US,"%02d:%02d:%02d",h,m,x);}

    private void updateTimers() {
        if(!"timer".equals(mode)||timerList==null)return;boolean rebuild=false;
        for(TimerStore.Entry e:TimerStore.all(this)){
            long rem=e.remaining();if(e.running&&rem<=0){TimerReceiver.complete(this,e.id);rebuild=true;continue;}
            TextView time=timerList.findViewWithTag("timer_remaining_"+e.id),state=timerList.findViewWithTag("timer_state_"+e.id);
            if(time!=null)time.setText(formatTimer(rem));if(state!=null)state.setText(timerStateText(e));
        }
        if(rebuild)renderTimers();
    }

'''
s=s[:start]+new_timer+s[end:]
p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+','versionCode = 30',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.1.1"',s)
p.write_text(s,encoding="utf-8")
print("WakeGuard v1.1.1: quick temporary timers + save-or-not flow applied")