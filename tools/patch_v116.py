from pathlib import Path
import re, runpy, shutil

runpy.run_path("tools/patch_v115.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"

for name in ["TimerStore.java","TimerReceiver.java","StatsActivity.java","StreakFlameView.java"]:
    shutil.copyfile("tools/v116_templates/"+name, java/name)

p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")

# Timer sound picker state.
field='    private long pendingTimerSoundId = -1L;\n    private static final int REQ_TIMER_SOUND = 7116;\n'
anchor='    private LinearLayout timerList;'
if field.strip() not in s:
    if anchor not in s: raise SystemExit("timerList field not found")
    s=s.replace(anchor,field+anchor,1)

# Add a visible sound action to each timer row next to the overflow menu.
old='''        TextView more=timerTextAction("︙",22,Ui.MUTED);more.setOnClickListener(v->showTimerMenu(more,e.id));titleRow.addView(more);row.addView(titleRow);'''
new='''        TextView sound=timerTextAction(timerSoundText(e),13,Ui.MUTED);sound.setOnClickListener(v->pickTimerSound(e.id));titleRow.addView(sound);
        TextView more=timerTextAction("︙",22,Ui.MUTED);more.setOnClickListener(v->showTimerMenu(more,e.id));titleRow.addView(more);row.addView(titleRow);'''
if old not in s: raise SystemExit("current timer title row not found")
s=s.replace(old,new,1)

insert_at=s.index('    private String timerStateText')
helpers=r'''    private String timerSoundText(TimerStore.Entry e){
        String n=e==null?"":e.soundName;
        if(n==null||n.trim().isEmpty())return "音: 標準";
        n=n.trim(); if(n.length()>14)n=n.substring(0,13)+"…"; return "音: "+n;
    }

    private void pickTimerSound(long id){
        pendingTimerSoundId=id;
        android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION|android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,REQ_TIMER_SOUND);}catch(Throwable t){Toast.makeText(this,"音声ファイルを開けません",Toast.LENGTH_SHORT).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_TIMER_SOUND||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        long id=pendingTimerSoundId; pendingTimerSoundId=-1L; if(id<1000)return;
        android.net.Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
        String display="選択した音";
        android.database.Cursor cur=null;
        try{cur=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null);if(cur!=null&&cur.moveToFirst()){int ix=cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(ix>=0){String x=cur.getString(ix);if(x!=null&&!x.trim().isEmpty())display=x.trim();}}}catch(Throwable ignored){}finally{if(cur!=null)try{cur.close();}catch(Throwable ignored){}}
        TimerStore.Entry e=TimerStore.find(this,id); if(e==null)return;
        e.soundUri=uri.toString(); e.soundName=display; TimerStore.update(this,e); renderTimers();
        Toast.makeText(this,"タイマー音: "+display,Toast.LENGTH_SHORT).show();
    }

'''
s=s[:insert_at]+helpers+s[insert_at:]
p.write_text(s,encoding="utf-8")

# Make the home shortcut to streak feel like a fire streak, while keeping the full statistics page.
p=java/"MainActivity.java"
s=p.read_text(encoding="utf-8")
s=s.replace('TextView statsLabel=Ui.text(this,"起床記録",15,Ui.TEXT);','TextView statsLabel=Ui.text(this,"🔥  ストリーク",15,Ui.TEXT);')
s=s.replace('statsText.setText("連続 "+StreakTracker.displayCurrent(this)+"日  ·  最高 "+Prefs.bestStreak(this)+"日")','statsText.setText("🔥 "+StreakTracker.displayCurrent(this)+"日  ·  最高 "+Prefs.bestStreak(this)+"日")')
p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+','versionCode = 35',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.1.6"',s)
p.write_text(s,encoding="utf-8")

print("WakeGuard v1.1.6: ongoing timer notifications + selectable timer sound + evolving streak flame mascot")
