package jp.wakeguard.alarm;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public final class AlarmProfiles {
    private AlarmProfiles() {}

    public static AlarmStore.Entry primary(Context c) {
        return new AlarmStore.Entry(
                AlarmScheduler.PRIMARY_ALARM_ID,
                Prefs.hour(c), Prefs.minute(c), Prefs.dayMask(c), Prefs.enabled(c),
                Prefs.primaryLabel(c), Prefs.steps(c), Prefs.primaryMissionType(c), Prefs.primaryMissionCount(c),
                Prefs.volume(c), Prefs.vibration(c), Prefs.soundUri(c), Prefs.soundName(c), Prefs.soundBytes(c), Prefs.soundDurationMs(c), Prefs.ringDurationSec(c), Prefs.fullStopDurationSec(c), Prefs.preNotifyMin(c), Prefs.snoozeMinutes(c));
    }

    public static AlarmStore.Entry defaults(Context c) {
        AlarmStore.Entry p = primary(c);
        return new AlarmStore.Entry(-1L, 7, 0, 0, true, "アラーム",
                10, "NONE", p.missionCount, p.volume, p.vibration, p.soundUri, p.soundName, p.soundBytes, p.soundDurationMs, 0, 0, 30, 0);
    }

    public static List<AlarmStore.Entry> all(Context c) {
        ArrayList<AlarmStore.Entry> out = new ArrayList<>();
        if(Prefs.primaryConfigured(c)) out.add(primary(c));
        out.addAll(AlarmStore.all(c)); return out;
    }

    public static AlarmStore.Entry get(Context c, long id) {
        if (id == AlarmScheduler.PRIMARY_ALARM_ID) return primary(c);
        AlarmStore.Entry e = AlarmStore.find(c,id);
        return e == null ? primary(c) : e;
    }

    public static AlarmStore.Entry save(Context c, AlarmStore.Entry e) {
        if (e.id == AlarmScheduler.PRIMARY_ALARM_ID) {
            Prefs.enabled(c,e.enabled); Prefs.time(c,e.hour,e.minute); Prefs.dayMask(c,e.dayMask);
            Prefs.primaryLabel(c,e.label); Prefs.steps(c,e.steps); Prefs.primaryMissionType(c,e.missionType); Prefs.primaryMissionCount(c,e.missionCount);
            Prefs.volume(c,e.volume); Prefs.vibration(c,e.vibration); Prefs.soundUri(c,e.soundUri); Prefs.soundName(c,e.soundName);
            Prefs.soundBytes(c,e.soundBytes); Prefs.soundDurationMs(c,e.soundDurationMs); Prefs.ringDurationSec(c,e.ringDurationSec); Prefs.fullStopDurationSec(c,e.fullStopDurationSec); Prefs.preNotifyMin(c,e.preNotifyMin); Prefs.snoozeMinutes(c,e.snoozeMinutes);
            return primary(c);
        }
        if (e.id < 1000) return AlarmStore.add(c,e);
        AlarmStore.update(c,e); return e;
    }

    public static void setEnabled(Context c, long id, boolean enabled) { AlarmStore.Entry e=get(c,id); e.enabled=enabled; save(c,e); if(!enabled) SnoozeScheduler.cancel(c,id); }
    public static void delete(Context c, long id) { SnoozeScheduler.cancel(c,id); if (id == AlarmScheduler.PRIMARY_ALARM_ID) { Prefs.enabled(c,false); return; } AlarmStore.delete(c,id); }

    public static int steps(Context c, long id) { return Math.max(1,get(c,id).steps); }
    public static int missionCount(Context c,long id){ return Math.max(1,get(c,id).missionCount); }
    public static String missionType(Context c,long id){ return AlarmStore.normalizeMission(get(c,id).missionType); }
    public static int volume(Context c, long id) { return Math.max(0,Math.min(100,get(c,id).volume)); }
    public static String vibration(Context c, long id) { String v=get(c,id).vibration; return v==null||v.isEmpty()?"IRREGULAR":v; }
    public static String soundUri(Context c,long id) { String s=get(c,id).soundUri; return s==null?"":s; }
    public static String soundName(Context c,long id) { String s=get(c,id).soundName; return s==null?"":s; }
    public static String label(Context c,long id) { String s=get(c,id).label; if(s==null||s.trim().isEmpty())return I18n.tr(c,"アラーム");s=s.trim();return ("アラーム".equals(s)||"メインアラーム".equals(s))?I18n.tr(c,s):s; }
    public static int ringDurationSec(Context c,long id){ return Math.max(0,Math.min(86400,get(c,id).ringDurationSec)); }
    public static int fullStopDurationSec(Context c,long id){ return Math.max(0,Math.min(86400,get(c,id).fullStopDurationSec)); }
    public static int preNotifyMin(Context c,long id){ return Math.max(0,Math.min(1440,get(c,id).preNotifyMin)); }
    public static int snoozeMinutes(Context c,long id){ return Math.max(0,Math.min(60,get(c,id).snoozeMinutes)); }

    public static String resolveSessionMission(Context c,long id){
        String t=missionType(c,id);
        if(!"RANDOM".equals(t)) return t;
        String[] pool={"STEPS","MATH","TAP","CODE","SHAKE","MEMORY","TYPE","HOLD","SWIPE","ORDER","REVERSE"};
        long seed=System.nanoTime() ^ System.currentTimeMillis() ^ id;
        return pool[(int)Math.floorMod(seed,pool.length)];
    }

    public static String missionName(String type){
        switch(AlarmStore.normalizeMission(type)){
            case "NONE": return "なし";
            case "MATH": return "計算";
            case "TAP": return "連打";
            case "CODE": return "コード入力";
            case "SHAKE": return "シェイク";
            case "MEMORY": return "記憶";
            case "TYPE": return "文章入力";
            case "HOLD": return "長押し";
            case "SWIPE": return "スワイプ";
            case "ORDER": return "順番タップ";
            case "REVERSE": return "逆順入力";
            case "RANDOM": return "ランダム";
            default: return "歩数";
        }
    }

    public static String missionIcon(String type){
        switch(AlarmStore.normalizeMission(type)){
            case "NONE": return "✓";
            case "MATH": return "＋";
            case "TAP": return "●";
            case "CODE": return "#";
            case "SHAKE": return "↕";
            case "MEMORY": return "◇";
            case "TYPE": return "Aa";
            case "HOLD": return "◉";
            case "SWIPE": return "↔";
            case "ORDER": return "123";
            case "REVERSE": return "⇄";
            case "RANDOM": return "?";
            default: return "歩";
        }
    }

    public static String missionSummary(Context c,long id){
        AlarmStore.Entry e=get(c,id);
        String t=AlarmStore.normalizeMission(e.missionType);
        if("NONE".equals(t)) return "そのまま停止";
        if("STEPS".equals(t)) return e.steps+"歩";
        if("TAP".equals(t)) return e.missionCount+"回";
        if("MATH".equals(t)) return e.missionCount+"問";
        if("CODE".equals(t)) return e.missionCount+"回";
        if("SHAKE".equals(t)) return e.missionCount+"回";
        if("MEMORY".equals(t)) return e.missionCount+"問";
        if("TYPE".equals(t)) return e.missionCount+"文";
        if("HOLD".equals(t)) return Math.max(2,Math.min(30,e.missionCount))+"秒";
        if("SWIPE".equals(t)) return e.missionCount+"回";
        if("ORDER".equals(t)) return e.missionCount+"回";
        if("REVERSE".equals(t)) return e.missionCount+"問";
        return "毎回変更";
    }
}
