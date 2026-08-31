from pathlib import Path
import re, runpy, shutil

runpy.run_path("tools/patch_v072.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"
templates=Path("tools/v080_templates")

for name in ["Ui.java","AlarmProfiles.java","AlarmStore.java","MainActivity.java","MultiAlarmActivity.java","AlarmEditorActivity.java","SystemSettingsActivity.java"]:
    shutil.copyfile(templates/name, java/name)

# Persist active alarm identity and a label for the migrated original alarm.
p=java/"Prefs.java"
s=p.read_text(encoding="utf-8")
s=s.replace(
'''    public static long sessionEpochDay(Context c) { return p(c).getLong("session_epoch_day", -1L); }
    public static void sessionEpochDay(Context c, long v) { p(c).edit().putLong("session_epoch_day", v).apply(); }''',
'''    public static long sessionEpochDay(Context c) { return p(c).getLong("session_epoch_day", -1L); }
    public static void sessionEpochDay(Context c, long v) { p(c).edit().putLong("session_epoch_day", v).apply(); }
    public static long activeAlarmId(Context c) { return p(c).getLong("active_alarm_id", 1L); }
    public static void activeAlarmId(Context c, long v) { p(c).edit().putLong("active_alarm_id", v).apply(); }
    public static String primaryLabel(Context c) { return p(c).getString("primary_label", "メインアラーム"); }
    public static void primaryLabel(Context c, String v) { p(c).edit().putString("primary_label", v == null ? "メインアラーム" : v).apply(); }''')
p.write_text(s,encoding="utf-8")

# AlarmService must resolve mission/audio/vibration/volume from the alarm that actually fired.
p=java/"AlarmService.java"
s=p.read_text(encoding="utf-8")
s=s.replace('    public static final String EXTRA_EPOCH_DAY = "epochDay";', '    public static final String EXTRA_EPOCH_DAY = "epochDay";\n    public static final String EXTRA_ALARM_ID = "alarmId";')
s=s.replace('if (Prefs.currentSteps(this) >= Prefs.steps(this)) {', 'if (Prefs.currentSteps(this) >= AlarmProfiles.steps(this, Prefs.activeAlarmId(this))) {')
s=s.replace(
'''        if (newSession) {
            // Repeated tests must start from a clean service state.''',
'''        if (newSession) {
            long requestedAlarmId = intent == null ? AlarmScheduler.PRIMARY_ALARM_ID
                    : intent.getLongExtra(EXTRA_ALARM_ID, AlarmScheduler.PRIMARY_ALARM_ID);
            Prefs.activeAlarmId(this, requestedAlarmId);
            // Repeated tests must start from a clean service state.''')
s=s.replace('player = createPlayer(Prefs.soundUri(this));', 'player = createPlayer(AlarmProfiles.soundUri(this, Prefs.activeAlarmId(this)));')
s=s.replace('String mode = Prefs.vibration(this);', 'String mode = AlarmProfiles.vibration(this, Prefs.activeAlarmId(this));')
s=s.replace('int percent = Math.max(0, Math.min(100, Prefs.volume(this)));', 'int percent = AlarmProfiles.volume(this, Prefs.activeAlarmId(this));')
s=s.replace('int target = Prefs.steps(this);', 'int target = AlarmProfiles.steps(this, Prefs.activeAlarmId(this));')
s=s.replace('Prefs.currentSteps(this) >= Prefs.steps(this)', 'Prefs.currentSteps(this) >= AlarmProfiles.steps(this, Prefs.activeAlarmId(this))')
s=s.replace('.setContentTitle("起床ガード")\n                .setContentText("歩数ミッション完了まで停止しません")', '.setContentTitle(AlarmProfiles.label(this, Prefs.activeAlarmId(this)))\n                .setContentText("あと " + Math.max(0, AlarmProfiles.steps(this, Prefs.activeAlarmId(this)) - Prefs.currentSteps(this)) + " 歩で解除")')
s=s.replace('title.setText("起きるまで止めません");', 'title.setText(AlarmProfiles.label(this, Prefs.activeAlarmId(this)));')
s=s.replace('note.setText("歩数センサーで実際に歩いた分だけカウントします");', 'note.setText("歩数ミッション完了までアラームは停止しません");')
p.write_text(s,encoding="utf-8")

# Scheduled Activity passes alarm id to the Service and uses the correct target steps.
p=java/"AlarmActivity.java"
s=p.read_text(encoding="utf-8")
s=s.replace(
'''        // Mark active before the Service is created so Back/Home handling cannot race
        // the foreground-service startup on slower OEM builds.
        Prefs.active(this, true);''',
'''        // Mark active before the Service is created so Back/Home handling cannot race.
        Prefs.active(this, true);
        if (!alreadyRunning) Prefs.activeAlarmId(this, alarmId);''')
s=s.replace('.putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay);', '.putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay)\n                .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId);')
s=s.replace('Prefs.currentSteps(this) >= Prefs.steps(this)', 'Prefs.currentSteps(this) >= AlarmProfiles.steps(this, Prefs.activeAlarmId(this))')
s=s.replace('int target = Prefs.steps(this);', 'int target = AlarmProfiles.steps(this, Prefs.activeAlarmId(this));')
start=s.index('    private void buildUi() {')
end=s.index('    private void render() {', start)
new='''    private void buildUi() {
        Ui.statusBar(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this,28), Ui.dp(this,54), Ui.dp(this,28), Ui.dp(this,44));
        root.setBackgroundColor(Ui.BG);

        TextView eyebrow = Ui.text(this, "WAKE MISSION", 13, Ui.ACCENT);
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(eyebrow);
        TextView title = Ui.title(this, AlarmProfiles.label(this, Prefs.activeAlarmId(this)), 30);
        title.setGravity(Gravity.CENTER); root.addView(title, Ui.gapTop(this,8));
        TextView now = Ui.title(this, java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), 54);
        now.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD); root.addView(now, Ui.gapTop(this,18));

        LinearLayout mission = Ui.card(this);
        mission.setGravity(Gravity.CENTER_HORIZONTAL);
        count = Ui.title(this, "0 / 0 歩", 44); count.setGravity(Gravity.CENTER); mission.addView(count);
        TextView note = Ui.text(this, "歩数ミッション完了まで停止できません", 14, Ui.MUTED);
        note.setGravity(Gravity.CENTER); mission.addView(note, Ui.gapTop(this,8));
        root.addView(mission, Ui.gapTop(this,24));

        stop = Ui.button(this, "歩数を達成すると停止できます", true);
        stop.setEnabled(false);
        stop.setOnClickListener(v -> {
            if (Prefs.currentSteps(this) >= AlarmProfiles.steps(this, Prefs.activeAlarmId(this))) {
                try { startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP)); }
                catch (Throwable t) { try { Prefs.lastAlarmError(this, "StopUI: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {} }
                try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); }
            }
        });
        root.addView(stop, Ui.gapTop(this,24));
        TextView footer = Ui.text(this, "Home / 戻るでは解除されません", 12, Ui.MUTED);
        footer.setGravity(Gravity.CENTER); root.addView(footer, Ui.gapTop(this,18));
        setContentView(root);
    }

'''
s=s[:start]+new+s[end:]
p.write_text(s,encoding="utf-8")

# Backup/legacy receiver path must also carry alarm id.
p=java/"AlarmReceiver.java"
s=p.read_text(encoding="utf-8")
s=s.replace('.putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay);', '.putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay)\n                        .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId);')
p.write_text(s,encoding="utf-8")

# Streak success uses the schedule that actually fired instead of always the old main alarm.
p=java/"StreakTracker.java"
s=p.read_text(encoding="utf-8")
s=s.replace('int mask = Prefs.dayMask(c);', 'int mask = AlarmProfiles.get(c, Prefs.activeAlarmId(c)).dayMask;', 1)
s=s.replace('int mask = Prefs.dayMask(c);', 'int mask = AlarmProfiles.get(c, Prefs.activeAlarmId(c)).dayMask;', 1)
s=s.replace('ZonedDateTime due = d.atTime(Prefs.hour(c), Prefs.minute(c)).atZone(zone);', 'AlarmStore.Entry activeAlarm = AlarmProfiles.get(c, Prefs.activeAlarmId(c));\n            ZonedDateTime due = d.atTime(activeAlarm.hour, activeAlarm.minute).atZone(zone);')
p.write_text(s,encoding="utf-8")

# Restyle world clock / stopwatch / timer without changing their timing engines.
p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")
s=s.replace('super.onCreate(b);\n        TimerReceiver.ensureChannel(this);', 'super.onCreate(b);\n        Ui.statusBar(this);\n        TimerReceiver.ensureChannel(this);')
s=s.replace('''    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b;
    }''', '    private Button button(String s) { return Ui.button(this, s, false); }')
s=s.replace('outer.setPadding(24, 20, 24, 24);', 'outer.setPadding(Ui.dp(this,20), Ui.dp(this,16), Ui.dp(this,20), Ui.dp(this,24));\n        outer.setBackgroundColor(Ui.BG);')
s=s.replace('Color.BLACK','Ui.TEXT').replace('0xFF555555','Ui.MUTED').replace('0xFFF3F4F6','Ui.SURFACE')
s=s.replace('card.setBackgroundColor(Ui.SURFACE);', 'card.setBackground(Ui.round(Ui.SURFACE, 22, this));')
s=s.replace('body.setPadding(4, 12, 4, 80);', 'body.setPadding(0, Ui.dp(this,12), 0, Ui.dp(this,80));')
p.write_text(s,encoding="utf-8")

# Register new screens.
p=root/"app/src/main/AndroidManifest.xml"
s=p.read_text(encoding="utf-8")
s=s.replace('''        <activity
            android:name=".MultiAlarmActivity"
            android:exported="false" />''', '''        <activity android:name=".MultiAlarmActivity" android:exported="false" />
        <activity android:name=".AlarmEditorActivity" android:exported="false" />
        <activity android:name=".SystemSettingsActivity" android:exported="false" />''')
p.write_text(s,encoding="utf-8")

# Version bump.
p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+', 'versionCode = 24', s)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "0.8.0"', s)
p.write_text(s,encoding="utf-8")
print("WakeGuard v0.8.0: per-alarm settings and complete UI overhaul applied")
