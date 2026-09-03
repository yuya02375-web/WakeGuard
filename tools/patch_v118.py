from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v117.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"

# Restore already-running timers after app update / reboot so their AlarmManager
# trigger and ongoing notification are recreated even though the user did not press Start again.
p=java/"RescheduleReceiver.java"
p.write_text('''package jp.wakeguard.alarm;\n\nimport android.content.BroadcastReceiver;\nimport android.content.Context;\nimport android.content.Intent;\n\npublic class RescheduleReceiver extends BroadcastReceiver {\n    @Override public void onReceive(Context context, Intent intent) {\n        AlarmScheduler.reschedule(context);\n        AlarmScheduler.scheduleRecoveryIfActive(context);\n\n        String action=intent==null?null:intent.getAction();\n        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)){\n            restoreRunningTimers(context);\n        }\n    }\n\n    private static void restoreRunningTimers(Context context){\n        for(TimerStore.Entry e:TimerStore.all(context)){\n            if(e==null||!e.running)continue;\n            long remaining=e.remaining();\n            if(remaining<=1500L){\n                TimerReceiver.complete(context,e.id);\n            }else{\n                TimerReceiver.schedule(context,e.id,e.endMs);\n            }\n        }\n    }\n}\n''',encoding="utf-8")

# Also repair notifications whenever the clock screen is opened/resumed. This covers
# devices where notification permission was granted only after the package-replaced broadcast.
p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")
old='''    @Override protected void onResume() {\n        super.onResume();\n        handler.removeCallbacks(ticker);\n        handler.post(ticker);\n    }\n'''
new='''    @Override protected void onResume() {\n        super.onResume();\n        requestTimerNotificationPermissionIfNeeded();\n        refreshRunningTimerNotifications();\n        handler.removeCallbacks(ticker);\n        handler.post(ticker);\n    }\n'''
if old not in s: raise SystemExit("ClockActivity onResume block not found")
s=s.replace(old,new,1)
p.write_text(s,encoding="utf-8")

# Main screen also refreshes running timer notifications after update / permission flow.
p=java/"MainActivity.java"
s=p.read_text(encoding="utf-8")
old='''    @Override protected void onResume(){super.onResume();AlarmScheduler.reschedule(this);render();}\n'''
new='''    @Override protected void onResume(){super.onResume();AlarmScheduler.reschedule(this);for(TimerStore.Entry e:TimerStore.all(this))if(e.running&&e.remaining()>1500L)TimerReceiver.schedule(this,e.id,e.endMs);render();}\n'''
if old not in s: raise SystemExit("MainActivity onResume block not found")
s=s.replace(old,new,1)
p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+','versionCode = 37',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.1.8"',s)
p.write_text(s,encoding="utf-8")

print("WakeGuard v1.1.8: restore pre-update running timer notifications after package replacement/reboot")
