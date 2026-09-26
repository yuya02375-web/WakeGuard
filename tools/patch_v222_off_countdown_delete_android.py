from pathlib import Path

root=Path('WakeGuard/app')
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=s.replace('versionCode = 121','versionCode = 122',1).replace('versionName = "2.2.1"','versionName = "2.2.2"',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/AlarmScheduler.java'; s=read(p)
old='''    public static long nextTriggerMillis(Context c, AlarmStore.Entry e) {
        if(e==null)return -1L; ZoneId zone=ZoneId.systemDefault(); Instant now=Instant.now();
        return nextFor(e.hour,e.minute,e.dayMask,e.enabled,e.fixedAt,zone,LocalDate.now(zone),now);
    }'''
new='''    public static long nextTriggerMillis(Context c, AlarmStore.Entry e) {
        if(e==null)return -1L; ZoneId zone=ZoneId.systemDefault(); Instant now=Instant.now();
        // Display-only calculation: OFF alarms still show when their configured time next arrives.
        return nextFor(e.hour,e.minute,e.dayMask,true,e.fixedAt,zone,LocalDate.now(zone),now);
    }'''
if old not in s: raise SystemExit('Android per-alarm next trigger block not found')
s=s.replace(old,new,1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/MainActivity.java'; s=read(p)
old='''        String scheduleText=repeatText(e.dayMask);long now=System.currentTimeMillis();long next=AlarmScheduler.nextTriggerMillis(this,e);String remaining=(e.enabled&&next>now)?"あと "+relativeText(next-now):"";'''
new='''        String scheduleText=repeatText(e.dayMask);long now=System.currentTimeMillis();long next=AlarmScheduler.nextTriggerMillis(this,e);String remaining=(next>now)?"あと "+relativeText(next-now):"";'''
if old not in s: raise SystemExit('Android countdown visibility block not found')
s=s.replace(old,new,1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/TimeLogActivity.java'; s=read(p)
old='''        Button edit=Ui.ghostButton(this,"編集");edit.setOnClickListener(v->showEntryDialog(f,e));card.addView(edit,new LinearLayout.LayoutParams(Ui.dp(this,70),Ui.dp(this,44)));
        card.setOnLongClickListener(v->{confirmDeleteEntry(f,e);return true;});'''
new='''        Button edit=Ui.ghostButton(this,"編集");edit.setOnClickListener(v->showEntryDialog(f,e));card.addView(edit,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,44)));
        Button del=Ui.ghostButton(this,"削除");del.setTextColor(Ui.DANGER);del.setContentDescription("時間記録を削除");del.setOnClickListener(v->confirmDeleteEntry(f,e));card.addView(del,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,44)));
        card.setOnLongClickListener(v->{confirmDeleteEntry(f,e);return true;});'''
if old not in s: raise SystemExit('Android time-log row action block not found')
s=s.replace(old,new,1)
write(p,s)

assert 'versionName = "2.2.2"' in read('build.gradle.kts') and 'versionCode = 122' in read('build.gradle.kts')
assert 'nextFor(e.hour,e.minute,e.dayMask,true,e.fixedAt' in read('src/main/java/jp/wakeguard/alarm/AlarmScheduler.java')
assert '(next>now)?"あと "+relativeText(next-now)' in read('src/main/java/jp/wakeguard/alarm/MainActivity.java')
assert 'Button del=Ui.ghostButton(this,"削除")' in read('src/main/java/jp/wakeguard/alarm/TimeLogActivity.java')
print('Android 2.2.2 OFF-countdown + visible time-record delete applied')
