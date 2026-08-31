from pathlib import Path
import re, runpy, shutil

runpy.run_path("tools/patch_v080.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"
templates=Path("tools/v090_templates")

for name in ["Ui.java","AlarmStore.java","AlarmProfiles.java","MainActivity.java","MultiAlarmActivity.java","AlarmEditorActivity.java","AlarmActivity.java"]:
    shutil.copyfile(templates/name, java/name)

# Mission state and migrated primary-alarm mission settings.
p=java/"Prefs.java"
s=p.read_text(encoding="utf-8")
needle='''    public static long activeAlarmId(Context c) { return p(c).getLong("active_alarm_id", 1L); }
    public static void activeAlarmId(Context c, long v) { p(c).edit().putLong("active_alarm_id", v).apply(); }
    public static String primaryLabel(Context c) { return p(c).getString("primary_label", "メインアラーム"); }
    public static void primaryLabel(Context c, String v) { p(c).edit().putString("primary_label", v == null ? "メインアラーム" : v).apply(); }'''
replacement='''    public static long activeAlarmId(Context c) { return p(c).getLong("active_alarm_id", 1L); }
    public static void activeAlarmId(Context c, long v) { p(c).edit().putLong("active_alarm_id", v).apply(); }
    public static String sessionMissionType(Context c) { return p(c).getString("session_mission_type", ""); }
    public static void sessionMissionType(Context c, String v) { p(c).edit().putString("session_mission_type", v == null ? "" : v).apply(); }
    public static int missionProgress(Context c) { return p(c).getInt("mission_progress", 0); }
    public static void missionProgress(Context c, int v) { p(c).edit().putInt("mission_progress", Math.max(0,v)).apply(); }
    public static boolean missionComplete(Context c) { return p(c).getBoolean("mission_complete", false); }
    public static void missionComplete(Context c, boolean v) { p(c).edit().putBoolean("mission_complete", v).apply(); }
    public static String primaryLabel(Context c) { return p(c).getString("primary_label", "メインアラーム"); }
    public static void primaryLabel(Context c, String v) { p(c).edit().putString("primary_label", v == null ? "メインアラーム" : v).apply(); }
    public static String primaryMissionType(Context c) { return AlarmStore.normalizeMission(p(c).getString("primary_mission_type", "STEPS")); }
    public static void primaryMissionType(Context c, String v) { p(c).edit().putString("primary_mission_type", AlarmStore.normalizeMission(v)).apply(); }
    public static int primaryMissionCount(Context c) { return Math.max(1,p(c).getInt("primary_mission_count", 3)); }
    public static void primaryMissionCount(Context c, int v) { p(c).edit().putInt("primary_mission_count", Math.max(1,Math.min(500,v))).apply(); }'''
if needle not in s: raise SystemExit('Prefs mission insertion point missing')
s=s.replace(needle,replacement)
p.write_text(s,encoding="utf-8")

# AlarmService: output engine stays foreground, while the mission can now be steps, math, tap, code, or random.
p=java/"AlarmService.java"
s=p.read_text(encoding="utf-8")
s=s.replace('''        if (ACTION_STOP.equals(action)) {
            if (Prefs.currentSteps(this) >= AlarmProfiles.steps(this, Prefs.activeAlarmId(this))) {
                StreakTracker.recordWakeSuccess(this);
                stopAlarm();
            }
            return START_NOT_STICKY;
        }''','''        if (ACTION_STOP.equals(action)) {
            if (Prefs.missionComplete(this)) {
                StreakTracker.recordWakeSuccess(this);
                stopAlarm();
            }
            return START_NOT_STICKY;
        }''')
s=s.replace('''            Prefs.activeAlarmId(this, requestedAlarmId);
            // Repeated tests must start from a clean service state.''','''            Prefs.activeAlarmId(this, requestedAlarmId);
            String chosenMission = intent == null ? "" : intent.getStringExtra("missionType");
            if (chosenMission == null || chosenMission.isEmpty()) chosenMission = AlarmProfiles.resolveSessionMission(this, requestedAlarmId);
            Prefs.sessionMissionType(this, chosenMission);
            Prefs.missionProgress(this, 0);
            Prefs.missionComplete(this, false);
            // Repeated tests must start from a clean service state.''')
s=s.replace('''        startAlarmOutputs();
        startStepSensor();
        running = true;''','''        startAlarmOutputs();
        if ("STEPS".equals(Prefs.sessionMissionType(this))) startStepSensor();
        else { Prefs.stepSensorAvailable(this, true); }
        running = true;''')
s=s.replace('''                .setContentTitle(AlarmProfiles.label(this, Prefs.activeAlarmId(this)))
                .setContentText("あと " + Math.max(0, AlarmProfiles.steps(this, Prefs.activeAlarmId(this)) - Prefs.currentSteps(this)) + " 歩で解除")''','''                .setContentTitle(AlarmProfiles.label(this, Prefs.activeAlarmId(this)))
                .setContentText(notificationMissionText())''')
mark='''    private void createChannel() {
        ensureNotificationChannel(this);
    }
'''
helper='''    private String notificationMissionText() {
        String t = Prefs.sessionMissionType(this);
        long id = Prefs.activeAlarmId(this);
        if ("MATH".equals(t)) return "計算ミッション " + AlarmProfiles.missionCount(this,id) + "問で解除";
        if ("TAP".equals(t)) {
            int n=AlarmProfiles.missionCount(this,id);
            if ("RANDOM".equals(AlarmProfiles.missionType(this,id))) n=Math.max(20,n*10);
            return "連打ミッション " + n + "回で解除";
        }
        if ("CODE".equals(t)) return "コード入力 " + AlarmProfiles.missionCount(this,id) + "回で解除";
        return "あと " + Math.max(0, AlarmProfiles.steps(this,id) - Prefs.currentSteps(this)) + " 歩で解除";
    }

    private void createChannel() {
        ensureNotificationChannel(this);
    }
'''
if mark not in s: raise SystemExit('service createChannel marker missing')
s=s.replace(mark,helper)
s=s.replace('''    private void ensureOverlay() {
        if (!Prefs.active(this) || !Settings.canDrawOverlays(this) || overlayView != null) return;''','''    private void ensureOverlay() {
        if (!"STEPS".equals(Prefs.sessionMissionType(this))) { removeOverlay(); return; }
        if (!Prefs.active(this) || !Settings.canDrawOverlays(this) || overlayView != null) return;''')
s=s.replace('''        boolean done = available && s >= target;

        if (overlayCount != null) overlayCount.setText(countText);''','''        boolean done = available && s >= target;
        if (done) Prefs.missionComplete(this, true);

        if (overlayCount != null) overlayCount.setText(countText);''')
s=s.replace('''            Prefs.currentSteps(this, accepted);
            updateViews();''','''            Prefs.currentSteps(this, accepted);
            if (accepted >= AlarmProfiles.steps(this, Prefs.activeAlarmId(this))) Prefs.missionComplete(this, true);
            updateViews();''')
s=s.replace('''            if (Prefs.stepSensorAvailable(this) && Prefs.currentSteps(this) >= AlarmProfiles.steps(this, Prefs.activeAlarmId(this))) {
                Intent stop = new Intent(this, AlarmService.class).setAction(ACTION_STOP);
                startService(stop);
            }''','''            if (Prefs.missionComplete(this)) {
                Intent stop = new Intent(this, AlarmService.class).setAction(ACTION_STOP);
                startService(stop);
            }''')
s=s.replace('ch.setDescription("歩数ミッション式の起床アラーム");','ch.setDescription("ミッション式の起床アラーム");')
p.write_text(s,encoding="utf-8")

# Version bump.
p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+', 'versionCode = 25', s)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "0.9.0"', s)
p.write_text(s,encoding="utf-8")
print("WakeGuard v0.9.0: multi-mission engine + AI-style UI + hidden diagnostics applied")
