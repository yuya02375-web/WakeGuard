from pathlib import Path
import re, runpy, shutil

runpy.run_path("tools/patch_v091.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"
templates=Path("tools/v100_templates")

for name in [
    "Ui.java","MainActivity.java","MultiAlarmActivity.java","AlarmEditorActivity.java",
    "AlarmStore.java","AlarmProfiles.java","AlarmActivity.java"
]:
    shutil.copyfile(templates/name, java/name)

# Keep the detailed clock engines, but change their shell from hero+top tabs to a normal
# top-level clock screen with bottom navigation, matching MainActivity.
p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")
s=s.replace(
    '    private LinearLayout root, body;\n    private TextView localClock, localDetail;',
    '    private LinearLayout root, body;\n    private TextView localClock, localDetail, screenTitle;'
)
start=s.index('    private void buildShell() {')
end=s.index('    private void showMode(String next) {', start)
new='''    private void buildShell() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Ui.BG);

        LinearLayout top = Ui.row(this);
        top.setPadding(Ui.dp(this,22),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,8));
        screenTitle = Ui.title(this, "世界時計", 30);
        top.addView(screenTitle,new LinearLayout.LayoutParams(0,-2,1));
        outer.addView(top);

        // Old hero clock is no longer part of the shell. Hidden fields are kept because
        // the existing timing engine updates them.
        localClock = new TextView(this); localClock.setVisibility(View.GONE);
        localDetail = new TextView(this); localDetail.setVisibility(View.GONE);

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this,22), Ui.dp(this,6), Ui.dp(this,22), Ui.dp(this,36));
        scroll.addView(body);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        outer.addView(Ui.divider(this));
        LinearLayout nav=new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),Ui.dp(this,6));
        alarmTab=Ui.bottomTab(this,"アラーム",false);
        worldTab=Ui.bottomTab(this,"世界時計","world".equals(mode));
        timerTab=Ui.bottomTab(this,"タイマー","timer".equals(mode));
        stopwatchTab=Ui.bottomTab(this,"ストップウォッチ","stopwatch".equals(mode));
        nav.addView(alarmTab,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));
        nav.addView(worldTab,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));
        nav.addView(timerTab,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));
        nav.addView(stopwatchTab,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));
        outer.addView(nav);
        alarmTab.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        worldTab.setOnClickListener(v -> showMode("world"));
        timerTab.setOnClickListener(v -> showMode("timer"));
        stopwatchTab.setOnClickListener(v -> showMode("stopwatch"));

        root = outer;
        setContentView(outer);
    }

'''
s=s[:start]+new+s[end:]
old='''    private void showMode(String next) {
        mode = next;
        body.removeAllViews();
        worldRows.clear();
        worldTab.setEnabled(!"world".equals(mode));
        stopwatchTab.setEnabled(!"stopwatch".equals(mode));
        timerTab.setEnabled(!"timer".equals(mode));
        if ("stopwatch".equals(mode)) buildStopwatch();
        else if ("timer".equals(mode)) buildTimer();
        else buildWorldClock();
        updateLiveUi();
    }'''
rep='''    private void showMode(String next) {
        mode = next;
        body.removeAllViews();
        worldRows.clear();
        if(screenTitle!=null) screenTitle.setText("stopwatch".equals(mode)?"ストップウォッチ":"timer".equals(mode)?"タイマー":"世界時計");
        if(worldTab!=null){
            worldTab.setTextColor("world".equals(mode)?Ui.ACCENT:Ui.MUTED);
            timerTab.setTextColor("timer".equals(mode)?Ui.ACCENT:Ui.MUTED);
            stopwatchTab.setTextColor("stopwatch".equals(mode)?Ui.ACCENT:Ui.MUTED);
        }
        if ("stopwatch".equals(mode)) buildStopwatch();
        else if ("timer".equals(mode)) buildTimer();
        else buildWorldClock();
        updateLiveUi();
    }'''
s=s.replace(old,rep)
s=s.replace(
    '    private void updateLocalClock() {\n        ZonedDateTime now = ZonedDateTime.now();',
    '    private void updateLocalClock() {\n        if(localClock==null||localDetail==null)return;\n        ZonedDateTime now = ZonedDateTime.now();'
)
s=s.replace(
'''        TextView heading = text("世界時計", 22, Ui.TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        body.addView(heading);

        TextView desc = text("秒・日付・UTC差・端末との時差・夏時間を表示します。", 13, Ui.MUTED);
        desc.setPadding(0, 4, 0, 10);
        body.addView(desc);''',
'''        TextView desc = text("各都市の時刻を秒まで表示します。日付・UTC差・時差・夏時間も確認できます。", 13, Ui.MUTED);
        desc.setPadding(0, 0, 0, Ui.dp(this,12));
        body.addView(desc);''')
p.write_text(s,encoding="utf-8")

# Device permissions are ordinary settings rows, not stacks of cards.
p=java/"SystemSettingsActivity.java"
s=p.read_text(encoding="utf-8")
s=s.replace(
'''    private void addStatus(String name,boolean ok,Runnable r){
        LinearLayout c=Ui.card(this);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView t=Ui.title(this,name,17);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));TextView mark=Ui.title(this,ok?"✓":"!",21);mark.setTextColor(ok?Ui.SUCCESS:Ui.DANGER);row.addView(mark);c.addView(row);
        TextView state=Ui.text(this,ok?"設定済み":"確認が必要です",12,ok?Ui.SUCCESS:Ui.MUTED);c.addView(state,Ui.gapTop(this,4));Button b=Ui.ghostButton(this,ok?"設定を確認":"設定を開く");b.setOnClickListener(v->r.run());c.addView(b,Ui.gapTop(this,12));body.addView(c,Ui.gapTop(this,14));
    }
    private void addButton(String s,Runnable r){Button b=Ui.ghostButton(this,s);b.setOnClickListener(v->r.run());body.addView(b,Ui.gapTop(this,12));}''',
'''    private void addStatus(String name,boolean ok,Runnable r){
        LinearLayout row=Ui.row(this);row.setOnClickListener(v->r.run());
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(this,name,16,Ui.TEXT));
        text.addView(Ui.text(this,ok?"設定済み":"確認が必要です",12,ok?Ui.SUCCESS:Ui.MUTED),Ui.gapTop(this,3));
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(Ui.text(this,"›",24,ok?Ui.MUTED:Ui.ACCENT));
        body.addView(row,Ui.gapTop(this,8));body.addView(Ui.divider(this));
    }
    private void addButton(String s,Runnable r){
        LinearLayout row=Ui.row(this);row.setOnClickListener(v->r.run());
        TextView t=Ui.text(this,s,16,Ui.TEXT);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(Ui.text(this,"›",24,Ui.MUTED));body.addView(row,Ui.gapTop(this,2));body.addView(Ui.divider(this));
    }''')
p.write_text(s,encoding="utf-8")

# The foreground notification and OEM overlay must describe the mission that actually fired.
p=java/"AlarmService.java"
s=p.read_text(encoding="utf-8")
start=s.index('    private String notificationMissionText() {')
end=s.index('    private void createChannel()', start)
new='''    private String notificationMissionText() {
        String t = AlarmStore.normalizeMission(Prefs.sessionMissionType(this));
        long id = Prefs.activeAlarmId(this);
        int n = AlarmProfiles.missionCount(this,id);
        if ("STEPS".equals(t)) return "あと " + Math.max(0, AlarmProfiles.steps(this,id) - Prefs.currentSteps(this)) + " 歩で解除";
        if ("MATH".equals(t)) return "計算 " + n + "問で解除";
        if ("TAP".equals(t)) return "連打 " + n + "回で解除";
        if ("CODE".equals(t)) return "コード入力 " + n + "回で解除";
        if ("SHAKE".equals(t)) return "シェイク " + n + "回で解除";
        if ("MEMORY".equals(t)) return "記憶問題 " + n + "問で解除";
        if ("TYPE".equals(t)) return "文章入力 " + n + "文で解除";
        if ("HOLD".equals(t)) return "長押し " + Math.max(2,Math.min(30,n)) + "秒で解除";
        return "ミッションを完了すると解除できます";
    }

'''
s=s[:start]+new+s[end:]
start=s.index('    private void updateViews() {')
end=s.index('    private void requestStopIfComplete()', start)
new='''    private void updateViews() {
        String t = AlarmStore.normalizeMission(Prefs.sessionMissionType(this));
        boolean done = Prefs.missionComplete(this);
        String countText;
        String buttonText;
        if ("STEPS".equals(t)) {
            int s = Prefs.currentSteps(this);
            int target = AlarmProfiles.steps(this, Prefs.activeAlarmId(this));
            boolean available = Prefs.stepSensorAvailable(this);
            countText = available ? (s + " / " + target + " 歩") : "歩数センサーを利用できません";
            done = available && s >= target;
            if (done) Prefs.missionComplete(this, true);
            buttonText = done ? "停止する" : (available ? "あと " + Math.max(0, target - s) + " 歩" : "アプリを開いて確認");
        } else {
            countText = AlarmProfiles.missionName(t) + "を完了してください";
            buttonText = done ? "停止する" : "ミッション画面で解除";
        }
        if (overlayCount != null) overlayCount.setText(countText);
        if (overlayStop != null) { overlayStop.setEnabled(done); overlayStop.setText(buttonText); }
    }

'''
s=s[:start]+new+s[end:]
p.write_text(s,encoding="utf-8")

# Version bump.
p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+', 'versionCode = 27', s)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "1.0.0"', s)
p.write_text(s,encoding="utf-8")

print("WakeGuard v1.0.0: conventional clock structure + 8 wake missions applied")
