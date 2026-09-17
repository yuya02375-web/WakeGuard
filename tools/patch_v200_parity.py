from pathlib import Path
r=Path('WakeGuard/app')

def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)
def repl(p,a,b,count=1):
    s=read(p)
    if a not in s: raise SystemExit(f'missing {p}: {a[:100]!r}')
    write(p,s.replace(a,b,count))

repl('build.gradle.kts','versionCode = 91','versionCode = 100')
repl('build.gradle.kts','versionName = "1.8.0"','versionName = "2.0.0"')

p='src/main/java/jp/wakeguard/alarm/Prefs.java'; s=read(p)
needle='    public static void preNotifyMin(Context c, int v) { p(c).edit().putInt("pre_notify_min",Math.max(0,Math.min(1440,v))).apply(); }\n'
if 'snoozeMinutes(Context c)' not in s:
    s=s.replace(needle,needle+'    public static int snoozeMinutes(Context c) { return Math.max(1,Math.min(60,p(c).getInt("snooze_minutes",5))); }\n    public static void snoozeMinutes(Context c, int v) { p(c).edit().putInt("snooze_minutes",Math.max(1,Math.min(60,v))).apply(); }\n',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/AlarmStore.java'; s=read(p)
s=s.replace('public int hour,minute,dayMask,steps,missionCount,volume,ringDurationSec,fullStopDurationSec,preNotifyMin;','public int hour,minute,dayMask,steps,missionCount,volume,ringDurationSec,fullStopDurationSec,preNotifyMin,snoozeMinutes;',1)
s=s.replace('String soundUri,String soundName,long soundBytes,long soundDurationMs,int ringDurationSec,int fullStopDurationSec,int preNotifyMin) {','String soundUri,String soundName,long soundBytes,long soundDurationMs,int ringDurationSec,int fullStopDurationSec,int preNotifyMin,int snoozeMinutes) {',1)
s=s.replace('this.preNotifyMin=Math.max(0,Math.min(1440,preNotifyMin));','this.preNotifyMin=Math.max(0,Math.min(1440,preNotifyMin));this.snoozeMinutes=Math.max(1,Math.min(60,snoozeMinutes));',1)
s=s.replace('o.optInt("fullStopDurationSec",0),o.has("preNotifyMin")?o.optInt("preNotifyMin",30):30);','o.optInt("fullStopDurationSec",0),o.has("preNotifyMin")?o.optInt("preNotifyMin",30):30,o.has("snoozeMinutes")?o.optInt("snoozeMinutes",5):5);',1)
s=s.replace('o.put("fullStopDurationSec",e.fullStopDurationSec);o.put("preNotifyMin",e.preNotifyMin);return o;','o.put("fullStopDurationSec",e.fullStopDurationSec);o.put("preNotifyMin",e.preNotifyMin);o.put("snoozeMinutes",e.snoozeMinutes);return o;',1)
s=s.replace('draft.ringDurationSec,draft.fullStopDurationSec,draft.preNotifyMin);','draft.ringDurationSec,draft.fullStopDurationSec,draft.preNotifyMin,draft.snoozeMinutes);',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/AlarmProfiles.java'; s=read(p)
s=s.replace('Prefs.ringDurationSec(c), Prefs.fullStopDurationSec(c), Prefs.preNotifyMin(c));','Prefs.ringDurationSec(c), Prefs.fullStopDurationSec(c), Prefs.preNotifyMin(c), Prefs.snoozeMinutes(c));',1)
s=s.replace('p.soundDurationMs, 0, 0, 30);','p.soundDurationMs, 0, 0, 30, 5);',1)
s=s.replace('Prefs.fullStopDurationSec(c,e.fullStopDurationSec); Prefs.preNotifyMin(c,e.preNotifyMin);','Prefs.fullStopDurationSec(c,e.fullStopDurationSec); Prefs.preNotifyMin(c,e.preNotifyMin); Prefs.snoozeMinutes(c,e.snoozeMinutes);',1)
needle='    public static int preNotifyMin(Context c,long id){ return Math.max(0,Math.min(1440,get(c,id).preNotifyMin)); }\n'
if 'snoozeMinutes(Context c,long id)' not in s:s=s.replace(needle,needle+'    public static int snoozeMinutes(Context c,long id){ return Math.max(1,Math.min(60,get(c,id).snoozeMinutes)); }\n',1)
s=s.replace('public static void setEnabled(Context c, long id, boolean enabled) { AlarmStore.Entry e=get(c,id); e.enabled=enabled; save(c,e); }','public static void setEnabled(Context c, long id, boolean enabled) { AlarmStore.Entry e=get(c,id); e.enabled=enabled; save(c,e); if(!enabled) SnoozeScheduler.cancel(c,id); }',1)
s=s.replace('public static void delete(Context c, long id) { if (id == AlarmScheduler.PRIMARY_ALARM_ID) { Prefs.enabled(c,false); return; } AlarmStore.delete(c,id); }','public static void delete(Context c, long id) { SnoozeScheduler.cancel(c,id); if (id == AlarmScheduler.PRIMARY_ALARM_ID) { Prefs.enabled(c,false); return; } AlarmStore.delete(c,id); }',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/AlarmEditorActivity.java'; s=read(p)
s=s.replace('private EditText label,steps,missionCount; private Button time,sound,missionPicker,ringDuration,fullStopDuration,preNotify;','private EditText label,steps,missionCount; private Button time,sound,missionPicker,ringDuration,fullStopDuration,preNotify,snooze,preview;',1)
s=s.replace('private Spinner vibration; private String selectedMission="STEPS";','private Spinner vibration; private String selectedMission="STEPS"; private MediaPlayer previewPlayer;',1)
pre='        body.addView(Ui.text(this,"アラームの前に音・振動なしの通知で知らせます",12,Ui.MUTED),Ui.gapTop(this,2));\n'
s=s.replace(pre,pre+'        LinearLayout snoozeRow=Ui.row(this);snoozeRow.setPadding(0,Ui.dp(this,14),0,0);TextView snoozeLabel=Ui.text(this,"スヌーズ",15,Ui.TEXT);snoozeRow.addView(snoozeLabel,new LinearLayout.LayoutParams(0,-2,1));snooze=Ui.ghostButton(this,"5分  ›");snooze.setTextColor(Ui.ACCENT);snooze.setOnClickListener(v->showSnoozeDialog());snoozeRow.addView(snooze);body.addView(snoozeRow);\n',1)
soundline='        soundStatus=Ui.text(this,"",13,Ui.MUTED);body.addView(soundStatus,Ui.gapTop(this,2));\n'
s=s.replace(soundline,soundline+'        preview=Ui.ghostButton(this,"▶  試聴");preview.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);preview.setOnClickListener(v->togglePreview());body.addView(preview,Ui.gapTop(this,4));\n',1)
s=s.replace('renderSound();renderPreNotify();renderAutoStop();refreshMissionFields();','renderSound();renderPreNotify();renderSnooze();renderAutoStop();refreshMissionFields();',1)
marker='    private void showCustomPreNotifyDialog(){\n'
methods='''    private void renderSnooze(){if(snooze!=null)snooze.setText(draft.snoozeMinutes+I18n.tr(this,"分")+"  ›");}
    private void showSnoozeDialog(){final int[] values={1,3,5,10,15,20,30,45,60};String[] labels=new String[values.length];int checked=0;for(int i=0;i<values.length;i++){labels[i]=values[i]+I18n.tr(this,"分");if(values[i]==draft.snoozeMinutes)checked=i;}new AlertDialog.Builder(this).setTitle(I18n.tr(this,"スヌーズ")).setSingleChoiceItems(labels,checked,(d,w)->{draft.snoozeMinutes=values[w];renderSnooze();d.dismiss();}).setNegativeButton(I18n.tr(this,"キャンセル"),null).show();}
    private void togglePreview(){if(previewPlayer!=null){stopPreview();return;}try{String raw=draft.soundUri==null?"":draft.soundUri.trim();Uri uri;if(raw.isEmpty())uri=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);else if(raw.startsWith("content:")||raw.startsWith("file:"))uri=Uri.parse(raw);else uri=Uri.fromFile(new File(raw));previewPlayer=new MediaPlayer();previewPlayer.setAudioStreamType(android.media.AudioManager.STREAM_ALARM);previewPlayer.setDataSource(this,uri);previewPlayer.setLooping(false);float v=Math.max(0f,Math.min(1f,draft.volume/100f));previewPlayer.setVolume(v,v);previewPlayer.setOnCompletionListener(x->stopPreview());previewPlayer.prepare();previewPlayer.start();if(preview!=null)preview.setText("■  "+I18n.tr(this,"停止"));}catch(Throwable t){stopPreview();Toast.makeText(this,I18n.tr(this,"この音源は試聴できません"),Toast.LENGTH_SHORT).show();}}
    private void stopPreview(){MediaPlayer p=previewPlayer;previewPlayer=null;if(p!=null){try{p.stop();}catch(Throwable ignored){}try{p.release();}catch(Throwable ignored){}}if(preview!=null)preview.setText("▶  "+I18n.tr(this,"試聴"));}
    @Override protected void onStop(){stopPreview();super.onStop();}

'''
if 'private void renderSnooze()' not in s:s=s.replace(marker,methods+marker,1)
s=s.replace('draft=AlarmProfiles.save(this,draft);alarmId=draft.id;AlarmScheduler.reschedule(this);','draft=AlarmProfiles.save(this,draft);alarmId=draft.id;if(!draft.enabled)SnoozeScheduler.cancel(this,alarmId);AlarmScheduler.reschedule(this);',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/AlarmActivity.java'; s=read(p)
needle='        stop = Ui.button(this, "NONE".equals(type)?"アラームを停止":"ミッションを完了してください", true); stop.setEnabled("NONE".equals(type));stop.setVisibility(View.VISIBLE);stop.setMinHeight(Ui.dp(this,"NONE".equals(type)?72:58));stop.setTextSize("NONE".equals(type)?19:16);\n'
s=s.replace(needle,'        Button snooze=Ui.button(this,"スヌーズ "+AlarmProfiles.snoozeMinutes(this,Prefs.activeAlarmId(this))+"分",false);snooze.setOnClickListener(v->{try{startService(new Intent(this,AlarmService.class).setAction(AlarmService.ACTION_SNOOZE));}catch(Throwable ignored){}try{finishAndRemoveTask();}catch(Throwable ignored){finish();}});if(Prefs.sessionIsTest(this))snooze.setVisibility(View.GONE);\n'+needle,1)
s=s.replace('        }else{\n            root.addView(stop);stop.bringToFront();\n        }\n','        }else{\n            root.addView(snooze,Ui.gapTop(this,6));\n            root.addView(stop,Ui.gapTop(this,6));stop.bringToFront();\n        }\n',1)
s=s.replace('            frame.addView(stop,stopLp);stop.bringToFront();\n','            frame.addView(stop,stopLp);stop.bringToFront();\n            if(!Prefs.sessionIsTest(this)){FrameLayout.LayoutParams snoozeLp=new FrameLayout.LayoutParams(Ui.dp(this,140),Ui.dp(this,48),Gravity.TOP|Gravity.START);snoozeLp.setMargins(Ui.dp(this,16),Ui.dp(this,28),0,0);frame.addView(snooze,snoozeLp);snooze.bringToFront();}\n',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/AlarmService.java'; s=read(p)
s=s.replace('public static final String ACTION_STOP = "jp.wakeguard.alarm.STOP";','public static final String ACTION_STOP = "jp.wakeguard.alarm.STOP";\n    public static final String ACTION_SNOOZE = "jp.wakeguard.alarm.SNOOZE";',1)
needle='        if (ACTION_STOP.equals(action)) {\n'
s=s.replace(needle,'        if (ACTION_SNOOZE.equals(action)) {\n            if(!Prefs.sessionIsTest(this)) SnoozeScheduler.schedule(this,Prefs.activeAlarmId(this),AlarmProfiles.snoozeMinutes(this,Prefs.activeAlarmId(this)));\n            stopAlarm();\n            return START_NOT_STICKY;\n        }\n\n'+needle,1)
write(p,s)

(r/'src/main/java/jp/wakeguard/alarm/SnoozeScheduler.java').write_text('''package jp.wakeguard.alarm;
import android.app.*;import android.content.*;import android.os.Build;import java.util.*;
public final class SnoozeScheduler {private static final String FILE="ignido_snooze_v200";private SnoozeScheduler(){}private static android.content.SharedPreferences p(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}private static String key(long id){return "at_"+id;}static int requestCode(long id){return 0x50000000|((int)(id^(id>>>32))&0x0fffffff);}static PendingIntent pi(Context c,long id,long at,int flags){Intent i=new Intent(c,SnoozeReceiver.class).setAction("jp.wakeguard.alarm.SNOOZE_FIRE").putExtra("alarmId",id).putExtra("expectedAt",at);return PendingIntent.getBroadcast(c,requestCode(id),i,flags|PendingIntent.FLAG_IMMUTABLE);}public static void schedule(Context c,long id,int min){cancel(c,id);long at=System.currentTimeMillis()+Math.max(1,Math.min(60,min))*60000L;p(c).edit().putLong(key(id),at).commit();AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;PendingIntent op=pi(c,id,at,PendingIntent.FLAG_UPDATE_CURRENT);try{if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,op);else am.setExact(AlarmManager.RTC_WAKEUP,at,op);}catch(SecurityException e){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,op);}}public static void cancel(Context c,long id){long at=p(c).getLong(key(id),-1L);AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null)try{PendingIntent op=pi(c,id,Math.max(0,at),PendingIntent.FLAG_NO_CREATE);if(op!=null){am.cancel(op);op.cancel();}}catch(Throwable ignored){}p(c).edit().remove(key(id)).commit();}static boolean consumeIfExpected(Context c,long id,long at){long saved=p(c).getLong(key(id),-1L),now=System.currentTimeMillis();if(saved<=0||saved!=at||now<at-60000L||now>at+600000L){if(saved==at)p(c).edit().remove(key(id)).commit();return false;}p(c).edit().remove(key(id)).commit();return true;}public static void restoreAll(Context c){Map<String,?> a=p(c).getAll();long now=System.currentTimeMillis();for(String k:a.keySet()){if(!k.startsWith("at_"))continue;try{long id=Long.parseLong(k.substring(3)),at=((Number)a.get(k)).longValue();if(at<=now||at>now+3660000L){p(c).edit().remove(k).commit();continue;}AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)continue;PendingIntent op=pi(c,id,at,PendingIntent.FLAG_UPDATE_CURRENT);am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,op);}catch(Throwable ignored){}}}}
''')
(r/'src/main/java/jp/wakeguard/alarm/SnoozeReceiver.java').write_text('''package jp.wakeguard.alarm;import android.content.*;import android.os.Build;import java.time.LocalDate;public class SnoozeReceiver extends BroadcastReceiver{@Override public void onReceive(Context c,Intent i){long id=i==null?-1:i.getLongExtra("alarmId",-1),at=i==null?-1:i.getLongExtra("expectedAt",-1);if(id<0||at<=0||!SnoozeScheduler.consumeIfExpected(c,id,at))return;if(id>=1000&&AlarmStore.find(c,id)==null)return;Intent s=new Intent(c,AlarmService.class).setAction(AlarmService.ACTION_FIRE_NEW).putExtra(AlarmService.EXTRA_ALARM_ID,id).putExtra(AlarmService.EXTRA_EPOCH_DAY,LocalDate.now().toEpochDay()).putExtra(AlarmService.EXTRA_EXPECTED_AT,at);try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);}catch(Throwable ignored){}}}
''')

p='src/main/java/jp/wakeguard/alarm/RescheduleReceiver.java'; s=read(p);s=s.replace('            restoreRunningTimers(context);\n','            restoreRunningTimers(context);\n            SnoozeScheduler.restoreAll(context);\n',1);write(p,s)
p='src/main/AndroidManifest.xml'; s=read(p);s=s.replace('<receiver android:name=".AlarmReceiver" android:exported="false" />','<receiver android:name=".AlarmReceiver" android:exported="false" />\n        <receiver android:name=".SnoozeReceiver" android:exported="false" />',1);write(p,s)

p='src/main/java/jp/wakeguard/alarm/MainActivity.java'; s=read(p)
s=s.replace('Button alarms=Ui.bottomTab(this,"アラーム",true);Button world=Ui.bottomTab(this,"時計",false);Button timer=Ui.bottomTab(this,"タイマー",false);Button sw=Ui.bottomTab(this,"ストップウォッチ",false);','Button alarms=Ui.bottomTab(this,"アラーム",true);Button world=Ui.bottomTab(this,"時計",false);Button timer=Ui.bottomTab(this,"タイマー",false);Button sw=Ui.bottomTab(this,"ストップウォッチ",false);Button streak=Ui.bottomTab(this,"ストリーク",false);',1)
s=s.replace('nav.addView(sw,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));','nav.addView(sw,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));nav.addView(streak,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));',1)
s=s.replace('sw.setOnClickListener(v->openClock("stopwatch"));return nav;','sw.setOnClickListener(v->openClock("stopwatch"));streak.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class)));return nav;',1)
s=s.replace('on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled&&e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);AlarmScheduler.reschedule(this);render();});','on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled){SnoozeScheduler.cancel(this,e.id);if(e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);}AlarmScheduler.reschedule(this);render();});',1);write(p,s)

p='src/main/java/jp/wakeguard/alarm/ClockActivity.java'; s=read(p)
s=s.replace('private Button alarmTab, worldTab, stopwatchTab, timerTab;','private Button alarmTab, worldTab, stopwatchTab, timerTab, streakTab;',1)
s=s.replace('stopwatchTab=Ui.bottomTab(this,"ストップウォッチ","stopwatch".equals(mode));','stopwatchTab=Ui.bottomTab(this,"ストップウォッチ","stopwatch".equals(mode));\n        streakTab=Ui.bottomTab(this,"ストリーク",false);',1)
s=s.replace('nav.addView(stopwatchTab,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));','nav.addView(stopwatchTab,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));\n        nav.addView(streakTab,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));',1)
s=s.replace('stopwatchTab.setOnClickListener(v -> showMode("stopwatch"));','stopwatchTab.setOnClickListener(v -> showMode("stopwatch"));\n        streakTab.setOnClickListener(v -> Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class)));',1);write(p,s)

p='src/main/java/jp/wakeguard/alarm/StatsActivity.java'; s=read(p);needle='        setContentView(root);Ui.applySystemBarInsets(this,root);\n    }\n'
if 'Ui.bottomTab(this,"ストリーク",true)' not in s:s=s.replace(needle,'        root.addView(Ui.divider(this));\n        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),Ui.dp(this,6));\n        Button alarms=Ui.bottomTab(this,"アラーム",false),clock=Ui.bottomTab(this,"時計",false),timer=Ui.bottomTab(this,"タイマー",false),sw=Ui.bottomTab(this,"ストップウォッチ",false),streak=Ui.bottomTab(this,"ストリーク",true);\n        nav.addView(alarms,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));nav.addView(clock,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));nav.addView(timer,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));nav.addView(sw,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));nav.addView(streak,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));\n        alarms.setOnClickListener(v->Ui.launchNoAnimation(this,new android.content.Intent(this,MainActivity.class)));clock.setOnClickListener(v->Ui.launchNoAnimation(this,new android.content.Intent(this,ClockActivity.class).putExtra("mode","world")));timer.setOnClickListener(v->Ui.launchNoAnimation(this,new android.content.Intent(this,ClockActivity.class).putExtra("mode","timer")));sw.setOnClickListener(v->Ui.launchNoAnimation(this,new android.content.Intent(this,ClockActivity.class).putExtra("mode","stopwatch")));root.addView(nav);\n'+needle,1);write(p,s)

checks={'build.gradle.kts':['versionCode = 100','versionName = "2.0.0"'],'src/main/java/jp/wakeguard/alarm/AlarmEditorActivity.java':['togglePreview()','showSnoozeDialog()'],'src/main/java/jp/wakeguard/alarm/AlarmService.java':['ACTION_SNOOZE','SnoozeScheduler.schedule'],'src/main/java/jp/wakeguard/alarm/AlarmScheduler.java':['isExpectedFire'],'src/main/java/jp/wakeguard/alarm/TimerReceiver.java':['launchTimerVideo']}
for p,ns in checks.items():
    t=read(p)
    for n in ns: assert n in t,(p,n)
print('Android 2.0.0 parity patch applied')
