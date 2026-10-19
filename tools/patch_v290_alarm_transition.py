from pathlib import Path
root=Path('WakeGuard/app')
def change(rel, f):
 p=root/rel; old=p.read_text(encoding='utf-8'); new=f(old); assert old!=new,rel; p.write_text(new,encoding='utf-8')

def version(src):
 assert 'versionCode = 189' in src and 'versionName = "2.8.9"' in src
 return src.replace('versionCode = 189','versionCode = 190',1).replace('versionName = "2.8.9"','versionName = "2.9.0"',1)
change('build.gradle.kts',version)

def activity(src):
 old='''        if (Settings.canDrawOverlays(c)) {
            try { c.startActivity(i); }
            catch (Throwable t) { try { Prefs.lastAlarmError(c, "DirectLockUi: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }
        }

        if (Build.VERSION.SDK_INT >= 34) {'''
 new='''        // Never launch twice. Previously, overlay permission caused a direct
        // startActivity followed by a PendingIntent send (and sometimes a third
        // startActivity). Repeated NEW_TASK/CLEAR_TOP can replace a live ring UI.
        if (visible) return;
        if (Settings.canDrawOverlays(c)) {
            try { c.startActivity(i); return; }
            catch (Throwable t) { try { Prefs.lastAlarmError(c, "DirectLockUi: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }
        }

        if (Build.VERSION.SDK_INT >= 34) {'''
 assert old in src
 src=src.replace(old,new,1)
 old='''        if (!AlarmScheduler.isExpectedFire(this, alarmId, epochDay, expectedAt)
                || AlarmOccurrenceGuard.wasDelivered(this, alarmId, expectedAt, epochDay)) {
            try { Prefs.lastAlarmError(this, "Blocked stale alarm delivery id=" + alarmId); } catch (Throwable ignored) {}
            i.setAction(null); finish(); return;
        }'''
 new='''        // An AlarmClock/FSI/OEM may re-deliver the same Activity intent after
        // AlarmService has already marked the occurrence delivered. Never call
        // finish() on an existing, actively ringing Activity in that case.
        if (Prefs.active(this) && AlarmService.running) {
            boolean same = Prefs.activeAlarmId(this) == alarmId
                    && Prefs.sessionEpochDay(this) == epochDay
                    && (expectedAt <= 0L || Prefs.sessionExpectedAt(this) == expectedAt);
            android.util.Log.i("IGNIDO_ALARM", "Reentered scheduled UI: same=" + same);
            i.setAction(null);
            return;
        }
        if (!AlarmScheduler.isExpectedFire(this, alarmId, epochDay, expectedAt)
                || AlarmOccurrenceGuard.wasDelivered(this, alarmId, expectedAt, epochDay)) {
            try { Prefs.lastAlarmError(this, "Blocked stale alarm delivery id=" + alarmId); } catch (Throwable ignored) {}
            i.setAction(null); finish(); return;
        }'''
 assert old in src
 src=src.replace(old,new,1)
 # Distinguish the only legitimate onResume finish state for bug diagnosis.
 old='''@Override protected void onResume() { super.onResume(); if(!Prefs.active(this)){try{finishAndRemoveTask();}catch(Throwable ignored){finish();}return;} visible = true; render();syncVideoState(); }'''
 new='''@Override protected void onResume() { super.onResume(); if(!Prefs.active(this)){android.util.Log.w("IGNIDO_ALARM", "Alarm screen finished because alarm_active=false");try{finishAndRemoveTask();}catch(Throwable ignored){finish();}return;} visible = true; android.util.Log.i("IGNIDO_ALARM", "Ring screen resumed alarmId="+Prefs.activeAlarmId(this));render();syncVideoState(); }'''
 assert old in src
 src=src.replace(old,new,1)
 return src
change('src/main/java/jp/wakeguard/alarm/AlarmActivity.java',activity)

def service(src):
 old='''        Prefs.lastAlarmError(this, "");
        String action = intent == null ? null : intent.getAction();'''
 new='''        String action = intent == null ? null : intent.getAction();
        android.util.Log.i("IGNIDO_ALARM", "Service action=" + action + " running=" + running + " active=" + Prefs.active(this));
        // Only a new alarm attempt clears a previous error. Auxiliary updates
        // must not erase the evidence of a disappearing ring screen.
        if (ACTION_FIRE_NEW.equals(action) || ACTION_FIRE_TEST.equals(action)) Prefs.lastAlarmError(this, "");'''
 assert old in src; return src.replace(old,new,1)
change('src/main/java/jp/wakeguard/alarm/AlarmService.java',service)
print('IGNIDO Android 2.9.0 alarm Activity duplicate-launch/re-entry patch applied')
