from pathlib import Path
import re

app=Path('WakeGuard/app'); j=app/'src/main/java/jp/wakeguard/alarm'

def read(p): return p.read_text(encoding='utf-8')
def write(p,s): p.write_text(s,encoding='utf-8')
def rep(s,old,new,name):
    if old not in s: raise SystemExit(name+' anchor missing')
    return s.replace(old,new,1)

# v1.6.8
p=app/'build.gradle.kts'; s=read(p)
s=re.sub(r'versionCode = \d+','versionCode = 79',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.6.8"',s)
write(p,s)

# The v1.6.7 foreground check in TimerReceiver used `c instanceof Activity`.
# A BroadcastReceiver receives a receiver/application Context, so that branch can never be true.
# Track the actually visible ClockActivity and let the timer receiver ask that Activity to open
# TimerVideoActivity directly. This is allowed because the Activity itself is visible.
p=j/'ClockActivity.java'; s=read(p)
anchor='''public class ClockActivity extends Activity {\n    private static final String PREF = "clock_tools";'''
replacement='''public class ClockActivity extends Activity {\n    private static java.lang.ref.WeakReference<ClockActivity> visibleInstance=new java.lang.ref.WeakReference<>(null);\n    private static final String PREF = "clock_tools";'''
s=rep(s,anchor,replacement,'clock visible instance field')

anchor='''    @Override protected void onResume() {\n        super.onResume();'''
replacement='''    @Override protected void onResume() {\n        super.onResume();\n        visibleInstance=new java.lang.ref.WeakReference<>(this);'''
s=rep(s,anchor,replacement,'clock resume visible')

anchor='''    @Override protected void onPause() {\n        super.onPause();\n        handler.removeCallbacks(ticker);\n    }'''
replacement='''    @Override protected void onPause() {\n        ClockActivity current=visibleInstance.get();if(current==this)visibleInstance.clear();\n        super.onPause();\n        handler.removeCallbacks(ticker);\n    }'''
s=rep(s,anchor,replacement,'clock pause visible')

anchor='''    @Override public void onBackPressed() { Ui.finishNoAnimation(this); }'''
helper='''    static boolean showTimerVideoIfVisible(TimerStore.Entry e){\n        ClockActivity a=visibleInstance.get();\n        if(a==null||e==null)return false;\n        try{if(a.isFinishing()||(Build.VERSION.SDK_INT>=17&&a.isDestroyed()))return false;}catch(Throwable ignored){}\n        try{\n            Intent open=new Intent(a,TimerVideoActivity.class).putExtra("videoUri",e.soundUri==null?"":e.soundUri).putExtra("label",e.label==null?"":e.label).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);\n            a.runOnUiThread(()->{try{a.startActivity(open);a.overridePendingTransition(0,0);}catch(Throwable ignored){}});\n            return true;\n        }catch(Throwable t){return false;}\n    }\n\n    @Override public void onBackPressed() { Ui.finishNoAnimation(this); }'''
s=rep(s,anchor,helper,'clock direct timer video helper')
write(p,s)

# Start the ring service first, then ask the *visible Activity* to open the video.
# Do not test `Context instanceof Activity`: it is false here by construction.
p=j/'TimerReceiver.java'; s=read(p)
old='''boolean started=TimerRingService.start(c,ring);if(started){if(isVideo(c,ring)&&c instanceof Activity)try{((Activity)c).startActivity(videoIntent(c,ring));}catch(Throwable ignored){}return;}ensureDoneChannel(c,ring);'''
new='''boolean started=TimerRingService.start(c,ring);if(isVideo(c,ring))ClockActivity.showTimerVideoIfVisible(ring);if(started)return;ensureDoneChannel(c,ring);'''
s=rep(s,old,new,'timer receiver impossible Activity check')
write(p,s)

# Fresh high-importance channel so installs that already created an older timer channel do not
# inherit a stale/low channel configuration while testing this fix.
p=j/'TimerRingService.java'; s=read(p)
s=s.replace('private static final String CHANNEL="wakeguard_timer_ring_v2";','private static final String CHANNEL="wakeguard_timer_ring_v3";',1)
write(p,s)

# Validation
assert 'versionName = "1.6.8"' in read(app/'build.gradle.kts')
cs=read(j/'ClockActivity.java')
assert 'visibleInstance' in cs and 'showTimerVideoIfVisible' in cs and 'startActivity(open)' in cs
tr=read(j/'TimerReceiver.java')
assert 'ClockActivity.showTimerVideoIfVisible(ring)' in tr and 'c instanceof Activity' not in tr
trs=read(j/'TimerRingService.java')
assert 'wakeguard_timer_ring_v3' in trs
print('WakeGuard v1.6.8 visible timer video launch fix applied')
