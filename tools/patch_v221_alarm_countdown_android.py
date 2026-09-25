from pathlib import Path
import re

root=Path('WakeGuard/app')

def read(rel): return (root/rel).read_text()
def write(rel,s): (root/rel).write_text(s)

p='build.gradle.kts'; s=read(p)
s=s.replace('versionCode = 120','versionCode = 121',1).replace('versionName = "2.2.0"','versionName = "2.2.1"',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/AlarmEditorActivity.java'; s=read(p)
s=s.replace('private EditText label,steps,missionCount; private Button time,afterButton,sound,missionPicker,ringDuration,fullStopDuration,preNotify,snooze,preview;',
            'private EditText label,steps,missionCount; private Button time,sound,missionPicker,ringDuration,fullStopDuration,preNotify,snooze,preview;',1)
old='''        time=Ui.ghostButton(this,"07:00");time.setTextSize(52);time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);time.setGravity(Gravity.CENTER);time.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{draft.fixedAt=0L;draft.hour=h;draft.minute=m;time.setText(String.format(Locale.JAPAN,"%02d:%02d",h,m));renderAfterButton();},draft.hour,draft.minute,true).show());body.addView(time,new LinearLayout.LayoutParams(-1,Ui.dp(this,86)));
        afterButton=Ui.button(this,"○時間後で設定",false);afterButton.setOnClickListener(v->showAfterDialog());if(alarmId==AlarmScheduler.PRIMARY_ALARM_ID)afterButton.setVisibility(View.GONE);LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,Ui.dp(this,50));alp.setMargins(0,0,0,Ui.dp(this,6));body.addView(afterButton,alp);'''
new='''        time=Ui.ghostButton(this,"07:00");time.setTextSize(52);time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);time.setGravity(Gravity.CENTER);time.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{draft.fixedAt=0L;draft.hour=h;draft.minute=m;time.setText(String.format(Locale.JAPAN,"%02d:%02d",h,m));},draft.hour,draft.minute,true).show());body.addView(time,new LinearLayout.LayoutParams(-1,Ui.dp(this,86)));'''
if old not in s: raise SystemExit('Android editor relative UI block not found')
s=s.replace(old,new,1)
start=s.find('\n    private void showAfterDialog(){')
end=s.find('\n    private void render(){',start)
if start<0 or end<0: raise SystemExit('Android relative methods block not found')
s=s[:start]+s[end:]
s=s.replace('renderSound();renderPreNotify();renderSnooze();renderAutoStop();renderAfterButton();refreshMissionFields();',
            'renderSound();renderPreNotify();renderSnooze();renderAutoStop();refreshMissionFields();',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/AlarmScheduler.java'; s=read(p)
needle='''    public static long nextTriggerMillis(Context c) {
        ZoneId zone=ZoneId.systemDefault(); LocalDate today=LocalDate.now(zone); Instant now=Instant.now(); long best=-1;'''
replacement='''    public static long nextTriggerMillis(Context c, AlarmStore.Entry e) {
        if(e==null)return -1L; ZoneId zone=ZoneId.systemDefault(); Instant now=Instant.now();
        return nextFor(e.hour,e.minute,e.dayMask,e.enabled,e.fixedAt,zone,LocalDate.now(zone),now);
    }

    public static long nextTriggerMillis(Context c) {
        ZoneId zone=ZoneId.systemDefault(); LocalDate today=LocalDate.now(zone); Instant now=Instant.now(); long best=-1;'''
if needle not in s: raise SystemExit('Android scheduler insertion point not found')
s=s.replace(needle,replacement,1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/MainActivity.java'; s=read(p)
s=s.replace('''    private LinearLayout list;
    private TextView nextText,statsText;''','''    private LinearLayout list;
    private TextView nextText,statsText;
    private final Handler countdownHandler=new Handler(Looper.getMainLooper());
    private final Runnable countdownTick=new Runnable(){@Override public void run(){render();countdownHandler.postDelayed(this,30000L);}};''',1)
s=s.replace('''    @Override protected void onResume(){super.onResume();AlarmScheduler.reschedule(this);for(TimerStore.Entry e:TimerStore.all(this))if(e.running&&e.remaining()>1500L)TimerReceiver.schedule(this,e.id,e.endMs);render();}''',
'''    @Override protected void onResume(){super.onResume();AlarmScheduler.reschedule(this);for(TimerStore.Entry e:TimerStore.all(this))if(e.running&&e.remaining()>1500L)TimerReceiver.schedule(this,e.id,e.endMs);render();countdownHandler.removeCallbacks(countdownTick);countdownHandler.postDelayed(countdownTick,30000L);}
    @Override protected void onPause(){countdownHandler.removeCallbacks(countdownTick);super.onPause();}''',1)
old='''        String scheduleText=(e.dayMask==0&&e.fixedAt>System.currentTimeMillis())?"あと "+relativeText(e.fixedAt-System.currentTimeMillis()):repeatText(e.dayMask);
        TextView detail=Ui.text(this,scheduleText+"   ·   "+I18n.tr(this,AlarmProfiles.missionName(e.missionType))+" "+I18n.tr(this,AlarmProfiles.missionSummary(this,e.id)),13,Ui.MUTED);row.addView(detail,Ui.gapTop(this,4));'''
new='''        String scheduleText=repeatText(e.dayMask);long now=System.currentTimeMillis();long next=AlarmScheduler.nextTriggerMillis(this,e);String remaining=(e.enabled&&next>now)?"あと "+relativeText(next-now):"";
        String detailText=scheduleText+(remaining.isEmpty()?"":"   ·   "+remaining)+"   ·   "+I18n.tr(this,AlarmProfiles.missionName(e.missionType))+" "+I18n.tr(this,AlarmProfiles.missionSummary(this,e.id));
        TextView detail=Ui.text(this,detailText,13,Ui.MUTED);row.addView(detail,Ui.gapTop(this,4));'''
if old not in s: raise SystemExit('Android list countdown block not found')
s=s.replace(old,new,1)
write(p,s)

assert 'versionName = "2.2.1"' in read('build.gradle.kts') and 'versionCode = 121' in read('build.gradle.kts')
assert '○時間後で設定' not in read('src/main/java/jp/wakeguard/alarm/AlarmEditorActivity.java')
assert 'showAfterDialog' not in read('src/main/java/jp/wakeguard/alarm/AlarmEditorActivity.java')
assert 'nextTriggerMillis(Context c, AlarmStore.Entry e)' in read('src/main/java/jp/wakeguard/alarm/AlarmScheduler.java')
assert 'あと "+relativeText(next-now)' in read('src/main/java/jp/wakeguard/alarm/MainActivity.java')
print('Android 2.2.1 alarm countdown display patch applied')
