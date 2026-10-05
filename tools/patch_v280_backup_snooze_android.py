from pathlib import Path

root=Path("WakeGuard/app")
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

# Version
p="build.gradle.kts"; s=read(p)
s=s.replace('versionCode = 170','versionCode = 180',1).replace('versionName = "2.7.0"','versionName = "2.8.0"',1)
write(p,s)

# Manifest: custom sanitized key/value backup agent.
p="src/main/AndroidManifest.xml"; s=read(p)
old='''    <application
        android:name=".WakeGuardApp"
        android:allowBackup="true"
        android:label="@string/app_name"'''
new='''    <application
        android:name=".WakeGuardApp"
        android:allowBackup="true"
        android:backupAgent=".IgnidoBackupAgent"
        android:fullBackupOnly="false"
        android:restoreAnyVersion="true"
        android:label="@string/app_name"'''
if old not in s: raise SystemExit("manifest application marker missing")
s=s.replace(old,new,1)
write(p,s)

backup_vault=r'''package jp.wakeguard.alarm;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Durable user-state backup. Runtime ringing/snooze sessions are deliberately excluded. */
public final class BackupVault {
    private BackupVault() {}

    static final String ENTITY_KEY = "ignido_state_v1";
    private static final int FORMAT = 1;

    private static final String[] PREF_FILES = {
            "wake_guard",
            "wake_guard_multi_alarms",
            "time_log_v1",
            "wakeguard_streak_growth_v1",
            "wakeguard_streak_protection_v1",
            "wakeguard_streak_game_v1",
            "clock_tools",
            "wakeguard_i18n"
    };

    private static final Set<String> WAKE_GUARD_RUNTIME_KEYS = new HashSet<>(Arrays.asList(
            "alarm_active", "step_baseline", "current_steps", "detector_mode", "step_sensor_available",
            "session_test", "session_epoch_day", "session_expected_at", "session_started_at_ms",
            "session_silenced", "active_alarm_id", "session_mission_type", "mission_progress",
            "mission_complete", "original_alarm_volume", "last_fatal_error", "last_alarm_error"
    ));
    private static final Set<String> TIME_LOG_RUNTIME_KEYS = new HashSet<>(Arrays.asList(
            "active_folder", "active_start"
    ));
    private static final Set<String> CLOCK_RUNTIME_KEYS = new HashSet<>(Arrays.asList(
            "sw_running", "sw_base", "sw_elapsed", "sw_laps", "sw_lap_walls", "sw_stopped_wall"
    ));

    private static final List<SharedPreferences> watchedPreferences = new ArrayList<>();
    private static final List<SharedPreferences.OnSharedPreferenceChangeListener> listeners = new ArrayList<>();
    private static boolean installed = false;
    private static boolean restoring = false;

    public static synchronized void installAutoBackup(Context context) {
        if (installed) return;
        installed = true;
        Context app = context.getApplicationContext();
        for (String file : PREF_FILES) {
            SharedPreferences prefs = app.getSharedPreferences(file, Context.MODE_PRIVATE);
            SharedPreferences.OnSharedPreferenceChangeListener listener = (sp, key) -> {
                if (restoring || !shouldBackupKey(file, key)) return;
                requestBackup(app);
            };
            prefs.registerOnSharedPreferenceChangeListener(listener);
            watchedPreferences.add(prefs);
            listeners.add(listener);
        }
        requestBackup(app);
    }

    public static void requestBackup(Context context) {
        if (restoring) return;
        try { new BackupManager(context.getApplicationContext()).dataChanged(); }
        catch (Throwable ignored) {}
    }

    private static boolean shouldBackupKey(String file, String key) {
        if (key == null) return false;
        if ("wake_guard".equals(file) && WAKE_GUARD_RUNTIME_KEYS.contains(key)) return false;
        if ("time_log_v1".equals(file) && TIME_LOG_RUNTIME_KEYS.contains(key)) return false;
        if ("clock_tools".equals(file) && CLOCK_RUNTIME_KEYS.contains(key)) return false;
        return true;
    }

    public static byte[] snapshotBytes(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("createdAt", System.currentTimeMillis());
        JSONObject allPrefs = new JSONObject();

        for (String file : PREF_FILES) {
            SharedPreferences prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE);
            JSONObject fileObject = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                String key = entry.getKey();
                if (!shouldBackupKey(file, key)) continue;
                JSONObject encoded = encodeValue(entry.getValue());
                if (encoded != null) fileObject.put(key, encoded);
            }
            allPrefs.put(file, fileObject);
        }
        root.put("prefs", allPrefs);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static void writeBackup(Context context, OutputStream out) throws Exception {
        byte[] bytes = snapshotBytes(context);
        out.write(bytes);
        out.flush();
        requestBackup(context);
    }

    public static void restore(Context context, InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[32 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        restoreBytes(context, out.toByteArray());
    }

    public static synchronized void restoreBytes(Context context, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("空のバックアップです");
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        int format = root.optInt("format", -1);
        if (format != FORMAT) throw new IllegalArgumentException("未対応のバックアップ形式です");
        JSONObject allPrefs = root.optJSONObject("prefs");
        if (allPrefs == null) throw new IllegalArgumentException("バックアップ内容がありません");

        restoring = true;
        try {
            for (String file : PREF_FILES) {
                JSONObject fileObject = allPrefs.optJSONObject(file);
                if (fileObject == null) continue;
                SharedPreferences.Editor editor = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear();
                JSONArray names = fileObject.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String key = names.optString(i, "");
                        if (key.isEmpty() || !shouldBackupKey(file, key)) continue;
                        JSONObject encoded = fileObject.optJSONObject(key);
                        if (encoded != null) decodeInto(editor, key, encoded);
                    }
                }
                editor.commit();
            }
        } finally {
            restoring = false;
        }
        clearRuntimeState(context);
        requestBackup(context);
    }

    public static void afterRestore(Context context) {
        clearRuntimeState(context);
        try { context.stopService(new android.content.Intent(context, AlarmService.class)); } catch (Throwable ignored) {}
        try { SnoozeScheduler.cancelAll(context); } catch (Throwable ignored) {}
        try { AlarmScheduler.reschedule(context); } catch (Throwable ignored) {}
        try { WidgetSuite.refreshAll(context); } catch (Throwable ignored) {}
        requestBackup(context);
    }

    private static void clearRuntimeState(Context context) {
        SharedPreferences.Editor e = context.getSharedPreferences("wake_guard", Context.MODE_PRIVATE).edit();
        for (String key : WAKE_GUARD_RUNTIME_KEYS) e.remove(key);
        e.commit();
        SharedPreferences.Editor t = context.getSharedPreferences("time_log_v1", Context.MODE_PRIVATE).edit();
        for (String key : TIME_LOG_RUNTIME_KEYS) t.remove(key);
        t.commit();
        context.getSharedPreferences("clock_tools", Context.MODE_PRIVATE).edit()
                .remove("sw_running").remove("sw_base").commit();
    }

    private static JSONObject encodeValue(Object value) throws Exception {
        if (value == null) return null;
        JSONObject out = new JSONObject();
        if (value instanceof String) {
            out.put("t", "s"); out.put("v", value);
        } else if (value instanceof Integer) {
            out.put("t", "i"); out.put("v", value);
        } else if (value instanceof Long) {
            out.put("t", "l"); out.put("v", value);
        } else if (value instanceof Float) {
            out.put("t", "f"); out.put("v", ((Float) value).doubleValue());
        } else if (value instanceof Boolean) {
            out.put("t", "b"); out.put("v", value);
        } else if (value instanceof Set) {
            out.put("t", "ss");
            JSONArray a = new JSONArray();
            for (Object item : (Set<?>) value) if (item instanceof String) a.put(item);
            out.put("v", a);
        } else {
            return null;
        }
        return out;
    }

    private static void decodeInto(SharedPreferences.Editor editor, String key, JSONObject encoded) {
        String type = encoded.optString("t", "");
        switch (type) {
            case "s": editor.putString(key, encoded.optString("v", "")); break;
            case "i": editor.putInt(key, encoded.optInt("v", 0)); break;
            case "l": editor.putLong(key, encoded.optLong("v", 0L)); break;
            case "f": editor.putFloat(key, (float) encoded.optDouble("v", 0.0)); break;
            case "b": editor.putBoolean(key, encoded.optBoolean("v", false)); break;
            case "ss":
                JSONArray a = encoded.optJSONArray("v");
                Set<String> set = new HashSet<>();
                if (a != null) for (int i = 0; i < a.length(); i++) set.add(a.optString(i, ""));
                set.remove("");
                editor.putStringSet(key, set);
                break;
            default: break;
        }
    }
}
'''
write("src/main/java/jp/wakeguard/alarm/BackupVault.java",backup_vault)

agent=r'''package jp.wakeguard.alarm;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

/** Sanitized key/value cloud backup used for reinstall/device restore. */
public final class IgnidoBackupAgent extends BackupAgent {
    @Override public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
        try {
            byte[] bytes = BackupVault.snapshotBytes(this);
            data.writeEntityHeader(BackupVault.ENTITY_KEY, bytes.length);
            data.writeEntityData(bytes, bytes.length);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
        while (data.readNextHeader()) {
            int size = data.getDataSize();
            byte[] bytes = new byte[Math.max(0, size)];
            int offset = 0;
            while (offset < bytes.length) {
                int n = data.readEntityData(bytes, offset, bytes.length - offset);
                if (n <= 0) break;
                offset += n;
            }
            if (BackupVault.ENTITY_KEY.equals(data.getKey()) && offset == bytes.length) {
                try { BackupVault.restoreBytes(this, bytes); }
                catch (Exception e) { throw new IOException(e); }
            }
        }
    }

    @Override public void onRestoreFinished() {
        super.onRestoreFinished();
        BackupVault.afterRestore(this);
    }
}
'''
write("src/main/java/jp/wakeguard/alarm/IgnidoBackupAgent.java",agent)

snooze=r'''package jp.wakeguard.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tracks each pending snooze independently so one or all can be cancelled without disabling alarms. */
public final class SnoozeScheduler {
    private static final String FILE = "ignido_snooze_v200";
    private SnoozeScheduler() {}

    private static SharedPreferences p(Context c){ return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    private static String key(long id){ return "at_" + id; }
    private static String dayKey(long id){ return "day_" + id; }

    static int requestCode(long id){ return 0x50000000 | ((int)(id ^ (id >>> 32)) & 0x0fffffff); }

    static PendingIntent pi(Context c,long id,long at,int flags){
        Intent i = new Intent(c,SnoozeReceiver.class)
                .setAction("jp.wakeguard.alarm.SNOOZE_FIRE")
                .putExtra("alarmId",id)
                .putExtra("expectedAt",at);
        return PendingIntent.getBroadcast(c,requestCode(id),i,flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c,long id,int min){
        cancel(c,id);
        long at = System.currentTimeMillis() + Math.max(1,Math.min(60,min)) * 60000L;
        long epochDay = Prefs.sessionEpochDay(c);
        p(c).edit().putLong(key(id),at).putLong(dayKey(id),epochDay).commit();
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent op = pi(c,id,at,PendingIntent.FLAG_UPDATE_CURRENT);
        try{
            if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,op);
            else am.setExact(AlarmManager.RTC_WAKEUP,at,op);
        }catch(SecurityException e){
            if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,op);
            else am.set(AlarmManager.RTC_WAKEUP,at,op);
        }
        try{ WidgetSuite.refreshAll(c); }catch(Throwable ignored){}
    }

    public static long scheduledAt(Context c,long id){
        long at=p(c).getLong(key(id),-1L);
        if(at<=System.currentTimeMillis()) return -1L;
        return at;
    }

    public static long occurrenceEpochDay(Context c,long id){ return p(c).getLong(dayKey(id),-1L); }

    public static Map<Long,Long> active(Context c){
        LinkedHashMap<Long,Long> out=new LinkedHashMap<>();
        Map<String,?> all=p(c).getAll();
        long now=System.currentTimeMillis();
        SharedPreferences.Editor cleanup=null;
        for(Map.Entry<String,?> e:all.entrySet()){
            String k=e.getKey();
            if(!k.startsWith("at_") || !(e.getValue() instanceof Number))continue;
            try{
                long id=Long.parseLong(k.substring(3));
                long at=((Number)e.getValue()).longValue();
                if(at>now && at<=now+3660000L) out.put(id,at);
                else{
                    if(cleanup==null)cleanup=p(c).edit();
                    cleanup.remove(k).remove(dayKey(id));
                }
            }catch(Throwable ignored){}
        }
        if(cleanup!=null)cleanup.apply();
        return out;
    }

    public static int activeCount(Context c){ return active(c).size(); }

    public static long nextAt(Context c){
        long best=Long.MAX_VALUE;
        for(long at:active(c).values()) best=Math.min(best,at);
        return best==Long.MAX_VALUE?-1L:best;
    }

    /** Cancel only this snooze. The base alarm stays enabled and recurring. */
    public static void cancel(Context c,long id){
        long at=p(c).getLong(key(id),-1L);
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null)try{
            PendingIntent op=pi(c,id,Math.max(0,at),PendingIntent.FLAG_NO_CREATE);
            if(op!=null){am.cancel(op);op.cancel();}
        }catch(Throwable ignored){}
        p(c).edit().remove(key(id)).remove(dayKey(id)).commit();
        try{ WidgetSuite.refreshAll(c); }catch(Throwable ignored){}
    }

    public static void cancelAll(Context c){
        for(Long id:active(c).keySet()) cancel(c,id);
    }

    /** Cancel the snooze and count its original occurrence as a successful wake-up. */
    public static void markAwake(Context c,long id){
        long day=occurrenceEpochDay(c,id);
        cancel(c,id);
        if(day>0) StreakTracker.recordWakeSuccess(c,id,day);
    }

    static boolean consumeIfExpected(Context c,long id,long at){
        long saved=p(c).getLong(key(id),-1L),now=System.currentTimeMillis();
        if(saved<=0||saved!=at||now<at-60000L||now>at+600000L){
            if(saved==at)p(c).edit().remove(key(id)).remove(dayKey(id)).commit();
            return false;
        }
        p(c).edit().remove(key(id)).remove(dayKey(id)).commit();
        return true;
    }

    public static void restoreAll(Context c){
        long now=System.currentTimeMillis();
        for(Map.Entry<Long,Long> e:active(c).entrySet()){
            long id=e.getKey(),at=e.getValue();
            if(at<=now)continue;
            try{
                AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
                if(am==null)continue;
                PendingIntent op=pi(c,id,at,PendingIntent.FLAG_UPDATE_CURRENT);
                if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,op);
                else am.setExact(AlarmManager.RTC_WAKEUP,at,op);
            }catch(Throwable ignored){}
        }
    }
}
'''
write("src/main/java/jp/wakeguard/alarm/SnoozeScheduler.java",snooze)

# Record a snoozed occurrence even though the service has stopped.
p="src/main/java/jp/wakeguard/alarm/StreakTracker.java"; s=read(p)
old='''    public static void recordWakeSuccess(Context c){
        if(Prefs.sessionIsTest(c))return;
        long epochDay=Prefs.sessionEpochDay(c);if(epochDay<=0)return;
        LocalDate day=LocalDate.ofEpochDay(epochDay);
        long last=Prefs.lastSuccessEpochDay(c);if(last==epochDay)return;

        int mask=AlarmProfiles.get(c,Prefs.activeAlarmId(c)).dayMask;
        boolean continues=false;
        if(last>0){
            LocalDate lastDay=LocalDate.ofEpochDay(last);
            continues=coverGap(c,lastDay,day,mask);
        }
        int next=continues?Prefs.streak(c)+1:1;

        int oldBest=Prefs.bestStreak(c);
        Prefs.streak(c,next);
        if(next>oldBest){Prefs.bestStreak(c,next);if(oldBest>0)Prefs.pendingRecordStreak(c,next);}
        Prefs.totalWakeups(c,Prefs.totalWakeups(c)+1);
        Prefs.lastSuccessEpochDay(c,epochDay);
        Prefs.addSuccessDay(c,day);
        StreakGrowth.onWakeSuccess(c);
        StreakProtection.onSuccessfulWake(c,next);
        try{WidgetSuite.refreshAll(c);}catch(Throwable ignored){}
    }
'''
new='''    public static void recordWakeSuccess(Context c){
        if(Prefs.sessionIsTest(c))return;
        recordWakeSuccess(c,Prefs.activeAlarmId(c),Prefs.sessionEpochDay(c));
    }

    public static synchronized void recordWakeSuccess(Context c,long alarmId,long epochDay){
        if(epochDay<=0)return;
        LocalDate day=LocalDate.ofEpochDay(epochDay);
        long last=Prefs.lastSuccessEpochDay(c);if(last==epochDay)return;

        int mask=AlarmProfiles.get(c,alarmId).dayMask;
        boolean continues=false;
        if(last>0){
            LocalDate lastDay=LocalDate.ofEpochDay(last);
            continues=coverGap(c,lastDay,day,mask);
        }
        int next=continues?Prefs.streak(c)+1:1;

        int oldBest=Prefs.bestStreak(c);
        Prefs.streak(c,next);
        if(next>oldBest){Prefs.bestStreak(c,next);if(oldBest>0)Prefs.pendingRecordStreak(c,next);}
        Prefs.totalWakeups(c,Prefs.totalWakeups(c)+1);
        Prefs.lastSuccessEpochDay(c,epochDay);
        Prefs.addSuccessDay(c,day);
        StreakGrowth.onWakeSuccess(c);
        StreakProtection.onSuccessfulWake(c,next);
        try{WidgetSuite.refreshAll(c);}catch(Throwable ignored){}
    }
'''
if old not in s: raise SystemExit("StreakTracker record block missing")
s=s.replace(old,new,1); write(p,s)

# Main alarm list: individual / all snooze cancellation + awake action.
p="src/main/java/jp/wakeguard/alarm/MainActivity.java"; s=read(p)
old='''        if(alarms.isEmpty()){
            TextView empty=Ui.text(this,"アラームはありません。右上の＋から追加できます。",15,Ui.MUTED);empty.setPadding(0,Ui.dp(this,40),0,0);list.addView(empty);return;
        }
        for(int i=0;i<alarms.size();i++){addAlarmRow(alarms.get(i));}
'''
new='''        if(alarms.isEmpty()){
            TextView empty=Ui.text(this,"アラームはありません。右上の＋から追加できます。",15,Ui.MUTED);empty.setPadding(0,Ui.dp(this,40),0,0);list.addView(empty);return;
        }
        Map<Long,Long> snoozes=SnoozeScheduler.active(this);
        if(snoozes.size()>1){
            LinearLayout allSnoozes=Ui.row(this);allSnoozes.setGravity(Gravity.CENTER_VERTICAL);allSnoozes.setPadding(Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8));
            TextView count=Ui.text(this,"スヌーズ中  "+snoozes.size()+"件",14,Ui.TEXT);allSnoozes.addView(count,new LinearLayout.LayoutParams(0,-2,1));
            Button cancelAll=Ui.ghostButton(this,"すべて取消");cancelAll.setTextColor(Ui.ACCENT_2);cancelAll.setOnClickListener(v->{SnoozeScheduler.cancelAll(this);render();});allSnoozes.addView(cancelAll);
            list.addView(allSnoozes);list.addView(Ui.divider(this));
        }
        for(int i=0;i<alarms.size();i++){addAlarmRow(alarms.get(i));}
'''
if old not in s: raise SystemExit("MainActivity render block missing")
s=s.replace(old,new,1)

old='''        TextView detail=Ui.text(this,detailText,13,Ui.MUTED);row.addView(detail,Ui.gapTop(this,4));
        on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled){SnoozeScheduler.cancel(this,e.id);if(e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);}AlarmScheduler.reschedule(this);render();});
        list.addView(row,Ui.cardParams(this));
'''
new='''        TextView detail=Ui.text(this,detailText,13,Ui.MUTED);row.addView(detail,Ui.gapTop(this,4));
        long snoozeAt=SnoozeScheduler.scheduledAt(this,e.id);
        if(snoozeAt>now){
            LinearLayout snoozeRow=new LinearLayout(this);snoozeRow.setGravity(Gravity.CENTER_VERTICAL);snoozeRow.setPadding(0,Ui.dp(this,10),0,0);snoozeRow.setOnClickListener(v->{});
            TextView snoozeState=Ui.text(this,"スヌーズ中  ·  あと "+relativeText(snoozeAt-now),13,Ui.ACCENT_2);snoozeRow.addView(snoozeState,new LinearLayout.LayoutParams(0,-2,1));
            Button awake=Ui.ghostButton(this,"起きた");awake.setTextSize(12);awake.setTextColor(Ui.SUCCESS);awake.setOnClickListener(v->{SnoozeScheduler.markAwake(this,e.id);render();});snoozeRow.addView(awake);
            Button cancel=Ui.ghostButton(this,"取消");cancel.setTextSize(12);cancel.setTextColor(Ui.ACCENT_2);cancel.setOnClickListener(v->{SnoozeScheduler.cancel(this,e.id);render();});snoozeRow.addView(cancel);
            row.addView(snoozeRow);
        }
        on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled){SnoozeScheduler.cancel(this,e.id);if(e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);}AlarmScheduler.reschedule(this);render();});
        list.addView(row,Ui.cardParams(this));
'''
if old not in s: raise SystemExit("MainActivity alarm detail block missing")
s=s.replace(old,new,1)

old='''    private void refreshNext(){long ms=AlarmScheduler.nextTriggerMillis(this);if(ms<=0){nextText.setText(I18n.tr(this,"次のアラームはありません"));return;}ZonedDateTime z=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());nextText.setText(I18n.tr(this,"次は "+z.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"next"),I18n.locale(this)))));}
'''
new='''    private void refreshNext(){long regular=AlarmScheduler.nextTriggerMillis(this),snooze=SnoozeScheduler.nextAt(this);long ms=regular;if(snooze>0&&(ms<=0||snooze<ms))ms=snooze;if(ms<=0){nextText.setText(I18n.tr(this,"次のアラームはありません"));return;}ZonedDateTime z=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());String prefix=(snooze>0&&ms==snooze)?"次のスヌーズ ":"次は ";nextText.setText(I18n.tr(this,prefix+z.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"next"),I18n.locale(this)))));}
'''
if old not in s: raise SystemExit("MainActivity refreshNext missing")
s=s.replace(old,new,1); write(p,s)

# Start backup-change monitoring at app startup.
p="src/main/java/jp/wakeguard/alarm/WakeGuardApp.java"; s=read(p)
old='''    @Override public void onCreate() {
        super.onCreate();
        new Thread(() -> {
'''
new='''    @Override public void onCreate() {
        super.onCreate();
        BackupVault.installAutoBackup(this);
        new Thread(() -> {
'''
if old not in s: raise SystemExit("WakeGuardApp marker missing")
s=s.replace(old,new,1); write(p,s)

# Settings: manual export/import fallback that survives uninstall because user chooses external storage.
p="src/main/java/jp/wakeguard/alarm/SystemSettingsActivity.java"; s=read(p)
s=s.replace('import android.widget.*;','import android.widget.*;\nimport java.io.InputStream;\nimport java.io.OutputStream;',1)
old='''        TextView device=Ui.title(this,"端末設定",19);body.addView(device,Ui.gapTop(this,28));TextView note=Ui.text(this,"ロック画面でアラーム画面・動画を確実に表示するために必要な端末側の設定です。",13,Ui.MUTED);body.addView(note,Ui.gapTop(this,8));
'''
new='''        TextView backup=Ui.title(this,"バックアップ",19);body.addView(backup,Ui.gapTop(this,28));
        TextView backupNote=Ui.text(this,"アラーム・時間記録・ストリークはAndroidのバックアップへ自動保存します。手動ファイルも作成できます。",13,Ui.MUTED);body.addView(backupNote,Ui.gapTop(this,8));
        addButton("バックアップを書き出す",this::exportBackup);addButton("バックアップを読み込む",this::importBackup);
        TextView device=Ui.title(this,"端末設定",19);body.addView(device,Ui.gapTop(this,28));TextView note=Ui.text(this,"ロック画面でアラーム画面・動画を確実に表示するために必要な端末側の設定です。",13,Ui.MUTED);body.addView(note,Ui.gapTop(this,8));
'''
if old not in s: raise SystemExit("SystemSettings device section missing")
s=s.replace(old,new,1)
insert='''    private static final int REQ_EXPORT_BACKUP=801;
    private static final int REQ_IMPORT_BACKUP=802;

    private void exportBackup(){
        try{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"IGNIDO-Wake-backup.json");
            startActivityForResult(i,REQ_EXPORT_BACKUP);
        }catch(Throwable t){Toast.makeText(this,"バックアップ画面を開けませんでした",Toast.LENGTH_SHORT).show();}
    }
    private void importBackup(){
        try{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
            startActivityForResult(i,REQ_IMPORT_BACKUP);
        }catch(Throwable t){Toast.makeText(this,"バックアップ画面を開けませんでした",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQ_EXPORT_BACKUP){
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IllegalStateException();BackupVault.writeBackup(this,out);Toast.makeText(this,"バックアップを書き出しました",Toast.LENGTH_SHORT).show();}
            catch(Throwable t){Toast.makeText(this,"バックアップを書き出せませんでした",Toast.LENGTH_LONG).show();}
        }else if(requestCode==REQ_IMPORT_BACKUP){
            new AlertDialog.Builder(this).setTitle("バックアップを復元").setMessage("現在のアラーム・時間記録・ストリークをバックアップ内容で置き換えます。")
                    .setNegativeButton("キャンセル",null).setPositiveButton("復元",(d,w)->{
                        try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IllegalStateException();BackupVault.restore(this,in);BackupVault.afterRestore(this);Toast.makeText(this,"復元しました",Toast.LENGTH_SHORT).show();renderStatus();}
                        catch(Throwable t){Toast.makeText(this,"バックアップを復元できませんでした",Toast.LENGTH_LONG).show();}
                    }).show();
        }
    }

'''
marker='''    private void addLanguageRow(){'''
if marker not in s: raise SystemExit("SystemSettings insertion marker missing")
s=s.replace(marker,insert+marker,1)
write(p,s)

assert 'versionName = "2.8.0"' in read("build.gradle.kts")
assert 'android:backupAgent=".IgnidoBackupAgent"' in read("src/main/AndroidManifest.xml")
assert 'BackupManager(context.getApplicationContext()).dataChanged()' in read("src/main/java/jp/wakeguard/alarm/BackupVault.java")
assert 'すべて取消' in read("src/main/java/jp/wakeguard/alarm/MainActivity.java")
assert 'markAwake' in read("src/main/java/jp/wakeguard/alarm/SnoozeScheduler.java")
assert 'バックアップを書き出す' in read("src/main/java/jp/wakeguard/alarm/SystemSettingsActivity.java")
print("Android 2.8.0 backup + snooze cancellation patch applied")
