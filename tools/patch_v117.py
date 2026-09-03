from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v116.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"

# ClockActivity: request notification permission in the actual timer-start context,
# refresh running notifications immediately after permission is granted, and fix
# the resume path so TimerStore is updated before TimerReceiver tries to display it.
p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")

s=s.replace('    private static final int REQ_TIMER_SOUND = 7116;\n',
'''    private static final int REQ_TIMER_SOUND = 7116;\n    private static final int REQ_TIMER_NOTIFICATION = 7117;\n''',1)

old='''    private void startQuickTimer(long durationMs){TimerStore.Entry e=TimerStore.addTemporary(this,"",durationMs);startTimerEntry(e);renderTimers();}\n    private void startTimerEntry(TimerStore.Entry e){if(e==null)return;e.remainingMs=e.durationMs;e.endMs=System.currentTimeMillis()+e.durationMs;e.running=true;TimerStore.update(this,e);TimerReceiver.schedule(this,e.id,e.endMs);}\n'''
new='''    private void startQuickTimer(long durationMs){TimerStore.Entry e=TimerStore.addTemporary(this,"",durationMs);startTimerEntry(e);renderTimers();}\n    private void startTimerEntry(TimerStore.Entry e){\n        if(e==null)return;\n        e.remainingMs=e.durationMs; e.endMs=System.currentTimeMillis()+e.durationMs; e.running=true;\n        TimerStore.update(this,e);\n        requestTimerNotificationPermissionIfNeeded();\n        TimerReceiver.schedule(this,e.id,e.endMs);\n    }\n'''
if old not in s: raise SystemExit("startTimerEntry block not found")
s=s.replace(old,new,1)

old_toggle='''    private void toggleTimer(long id) {\n        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;\n        if(e.running){e.remainingMs=e.remaining();e.running=false;e.endMs=0L;TimerReceiver.cancel(this,id);}\n        else{long rem=e.remainingMs>0?e.remainingMs:e.durationMs;e.remainingMs=rem;e.endMs=System.currentTimeMillis()+rem;e.running=true;TimerReceiver.schedule(this,id,e.endMs);}\n        TimerStore.update(this,e);renderTimers();\n    }\n'''
new_toggle='''    private void toggleTimer(long id) {\n        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;\n        if(e.running){\n            e.remainingMs=e.remaining(); e.running=false; e.endMs=0L;\n            TimerStore.update(this,e); TimerReceiver.cancel(this,id);\n        } else {\n            long rem=e.remainingMs>0?e.remainingMs:e.durationMs; e.remainingMs=rem; e.endMs=System.currentTimeMillis()+rem; e.running=true;\n            TimerStore.update(this,e);\n            requestTimerNotificationPermissionIfNeeded();\n            TimerReceiver.schedule(this,id,e.endMs);\n        }\n        renderTimers();\n    }\n'''
if old_toggle not in s: raise SystemExit("toggleTimer block not found")
s=s.replace(old_toggle,new_toggle,1)

insert_at=s.index('    private void updateTimers()')
permission_helpers=r'''    private boolean timerNotificationsAllowed(){
        if(Build.VERSION.SDK_INT<33)return true;
        try{return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED;}catch(Throwable ignored){return false;}
    }

    private void requestTimerNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT<33||timerNotificationsAllowed())return;
        try{requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},REQ_TIMER_NOTIFICATION);}catch(Throwable ignored){}
    }

    private void refreshRunningTimerNotifications(){
        if(!timerNotificationsAllowed())return;
        for(TimerStore.Entry e:TimerStore.all(this))if(e.running)TimerReceiver.showRunning(this,e);
    }

    private void showTimerNotificationSettingsDialog(){
        try{
            new AlertDialog.Builder(this)
                    .setTitle("タイマー通知を表示できません")
                    .setMessage("タイマー実行中の残り時間を通知に常時表示するには、WakeGuardの通知を許可してください。")
                    .setNegativeButton("閉じる",null)
                    .setPositiveButton("通知設定を開く",(d,w)->{
                        try{
                            android.content.Intent i=new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                            i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
                            startActivity(i);
                        }catch(Throwable ignored){}
                    }).show();
        }catch(Throwable ignored){}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode!=REQ_TIMER_NOTIFICATION)return;
        if(timerNotificationsAllowed())refreshRunningTimerNotifications(); else showTimerNotificationSettingsDialog();
    }

'''
s=s[:insert_at]+permission_helpers+s[insert_at:]
p.write_text(s,encoding="utf-8")

# TimerReceiver: use a fresh, more visible silent channel. Existing Android channels
# are immutable after creation, so a new channel ID is required to escape a previously
# lowered/blocked channel configuration. Also tolerate permission denial without crashing.
p=java/"TimerReceiver.java"
s=p.read_text(encoding="utf-8")
s=s.replace('private static final String RUN_CHANNEL="wakeguard_timer_running_v1";',
            'private static final String RUN_CHANNEL="wakeguard_timer_running_v2";',1)
s=s.replace('NotificationManager.IMPORTANCE_LOW);ch.setDescription("タイマーの残り時間を常に表示します");',
            'NotificationManager.IMPORTANCE_DEFAULT);ch.setDescription("タイマーの残り時間を常に表示します");',1)
s=s.replace('if(Build.VERSION.SDK_INT>=24)b.setChronometerCountDown(true);NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(runningId(e.id),b.build());}',
'''if(Build.VERSION.SDK_INT>=24)b.setChronometerCountDown(true);b.setPriority(Notification.PRIORITY_DEFAULT);NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)try{nm.notify(runningId(e.id),b.build());}catch(SecurityException ignored){}}''',1)
p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \\d+','versionCode = 36',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.1.7"',s)
p.write_text(s,encoding="utf-8")

print("WakeGuard v1.1.7: timer notification permission + resume notification reliability fix")
