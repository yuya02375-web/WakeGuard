package jp.wakeguard.alarm;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** One source of truth for per-alarm behavior. Alarm id=1 is the migrated original alarm. */
public final class AlarmProfiles {
    private AlarmProfiles() {}

    public static AlarmStore.Entry primary(Context c) {
        return new AlarmStore.Entry(
                AlarmScheduler.PRIMARY_ALARM_ID,
                Prefs.hour(c), Prefs.minute(c), Prefs.dayMask(c), Prefs.enabled(c),
                Prefs.primaryLabel(c), Prefs.steps(c), Prefs.volume(c), Prefs.vibration(c),
                Prefs.soundUri(c), Prefs.soundName(c), Prefs.soundBytes(c), Prefs.soundDurationMs(c));
    }

    public static AlarmStore.Entry defaults(Context c) {
        AlarmStore.Entry p = primary(c);
        return new AlarmStore.Entry(-1L, 7, 0, 0, true, "アラーム",
                p.steps, p.volume, p.vibration, p.soundUri, p.soundName, p.soundBytes, p.soundDurationMs);
    }

    public static List<AlarmStore.Entry> all(Context c) {
        ArrayList<AlarmStore.Entry> out = new ArrayList<>();
        out.add(primary(c)); out.addAll(AlarmStore.all(c)); return out;
    }

    public static AlarmStore.Entry get(Context c, long id) {
        if (id == AlarmScheduler.PRIMARY_ALARM_ID) return primary(c);
        AlarmStore.Entry e = AlarmStore.find(c,id);
        return e == null ? primary(c) : e;
    }

    public static AlarmStore.Entry save(Context c, AlarmStore.Entry e) {
        if (e.id == AlarmScheduler.PRIMARY_ALARM_ID) {
            Prefs.enabled(c,e.enabled); Prefs.time(c,e.hour,e.minute); Prefs.dayMask(c,e.dayMask);
            Prefs.primaryLabel(c,e.label); Prefs.steps(c,e.steps); Prefs.volume(c,e.volume);
            Prefs.vibration(c,e.vibration); Prefs.soundUri(c,e.soundUri); Prefs.soundName(c,e.soundName);
            Prefs.soundBytes(c,e.soundBytes); Prefs.soundDurationMs(c,e.soundDurationMs);
            return primary(c);
        }
        if (e.id < 1000) return AlarmStore.add(c,e);
        AlarmStore.update(c,e); return e;
    }

    public static void setEnabled(Context c, long id, boolean enabled) {
        AlarmStore.Entry e=get(c,id); e.enabled=enabled; save(c,e);
    }

    public static void delete(Context c, long id) {
        if (id == AlarmScheduler.PRIMARY_ALARM_ID) { Prefs.enabled(c,false); return; }
        AlarmStore.delete(c,id);
    }

    public static int steps(Context c, long id) { return Math.max(1,get(c,id).steps); }
    public static int volume(Context c, long id) { return Math.max(0,Math.min(100,get(c,id).volume)); }
    public static String vibration(Context c, long id) {
        String v=get(c,id).vibration; return v==null||v.isEmpty()?"IRREGULAR":v;
    }
    public static String soundUri(Context c,long id) { String s=get(c,id).soundUri; return s==null?"":s; }
    public static String soundName(Context c,long id) { String s=get(c,id).soundName; return s==null?"":s; }
    public static String label(Context c,long id) {
        String s=get(c,id).label; return s==null||s.trim().isEmpty()?"アラーム":s.trim();
    }
}
