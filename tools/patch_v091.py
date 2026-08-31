from pathlib import Path
import re, runpy, shutil

runpy.run_path("tools/patch_v090.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"
templates=Path("tools/v091_templates")

for name in ["Ui.java","MainActivity.java","MultiAlarmActivity.java","AlarmEditorActivity.java","SystemSettingsActivity.java"]:
    shutil.copyfile(templates/name, java/name)

# Simplify the active alarm screen: no techno/AI labels or neon presentation.
p=java/"AlarmActivity.java"
s=p.read_text(encoding="utf-8")
start=s.index('    private void buildUi() {')
end=s.index('    private String sessionType()', start)
new='''    private void buildUi() {
        Ui.statusBar(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this,24), Ui.dp(this,48), Ui.dp(this,24), Ui.dp(this,38));
        root.setBackground(Ui.screenGradient(this));

        TextView title = Ui.title(this, AlarmProfiles.label(this, Prefs.activeAlarmId(this)), 25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView now = Ui.title(this, java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), 58);
        now.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        now.setGravity(Gravity.CENTER);
        root.addView(now, Ui.gapTop(this,10));

        String type=sessionType();
        LinearLayout badgeRow=new LinearLayout(this);badgeRow.setGravity(Gravity.CENTER);
        missionName=Ui.pill(this,AlarmProfiles.missionIcon(type)+"  "+AlarmProfiles.missionName(type));
        badgeRow.addView(missionName);root.addView(badgeRow,Ui.gapTop(this,20));

        missionCard = Ui.card(this);
        missionCard.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(missionCard, Ui.gapTop(this,18));
        buildMission(type);

        stop = Ui.button(this, "ミッション完了後に解除", true);
        stop.setEnabled(false);
        stop.setOnClickListener(v -> { if (missionDone()) { try { startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP)); } catch (Throwable ignored) {} try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); } } });
        root.addView(stop, Ui.gapTop(this,24));
        TextView footer = Ui.text(this, "Home / 戻るでは解除されません", 12, Ui.MUTED);
        footer.setGravity(Gravity.CENTER); root.addView(footer, Ui.gapTop(this,16));
        setContentView(root);
    }

'''
s=s[:start]+new+s[end:]
s=s.replace('0xFF3A4A71','Ui.BORDER')
p.write_text(s,encoding="utf-8")

# Clean up the clock tools shell and spacing while preserving detailed world-clock data.
p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")
start=s.index('    private void buildShell() {')
end=s.index('    private void showMode(String next) {', start)
new='''    private void buildShell() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(Ui.dp(this,22), Ui.dp(this,20), Ui.dp(this,22), Ui.dp(this,24));
        outer.setBackground(Ui.screenGradient(this));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Ui.ghostButton(this,"←");
        back.setMinWidth(Ui.dp(this,56));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = text("時計", 27, Ui.TEXT);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(Ui.dp(this,16),0,0,0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        outer.addView(top);

        localClock = text("--:--:--", 52, Ui.TEXT);
        localClock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        localClock.setGravity(Gravity.CENTER);
        localClock.setPadding(0, Ui.dp(this,28), 0, 0);
        outer.addView(localClock, new LinearLayout.LayoutParams(-1, -2));

        localDetail = text("", 13, Ui.MUTED);
        localDetail.setGravity(Gravity.CENTER);
        localDetail.setPadding(0, Ui.dp(this,4), 0, Ui.dp(this,22));
        outer.addView(localDetail, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        alarmTab = Ui.ghostButton(this,"アラーム");
        worldTab = Ui.ghostButton(this,"世界時計");
        stopwatchTab = Ui.ghostButton(this,"ストップウォッチ");
        timerTab = Ui.ghostButton(this,"タイマー");
        Button[] tabButtons={alarmTab,worldTab,stopwatchTab,timerTab};
        for(Button b:tabButtons){
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,Ui.dp(this,52));
            tp.setMargins(0,0,Ui.dp(this,10),0);tabs.addView(b,tp);
        }
        tabScroll.addView(tabs);outer.addView(tabScroll);
        alarmTab.setOnClickListener(v -> startActivity(new Intent(this, MultiAlarmActivity.class)));
        worldTab.setOnClickListener(v -> showMode("world"));
        stopwatchTab.setOnClickListener(v -> showMode("stopwatch"));
        timerTab.setOnClickListener(v -> showMode("timer"));

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Ui.dp(this,26), 0, Ui.dp(this,90));
        scroll.addView(body);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root = outer;
        setContentView(outer);
    }

'''
s=s[:start]+new+s[end:]
s=s.replace('TextView heading = text("世界時計（秒までリアルタイム表示）", 21, Ui.TEXT);','TextView heading = text("世界時計", 22, Ui.TEXT);')
s=s.replace('TextView desc = text("各都市を HH:mm:ss で毎秒更新。日付・曜日・UTC差・端末との時差・夏時間も表示します。通信は使いません。", 14, Ui.MUTED);','TextView desc = text("秒・日付・UTC差・端末との時差・夏時間を表示します。", 13, Ui.MUTED);')
s=s.replace('card.setPadding(18, 14, 18, 14);','card.setPadding(Ui.dp(this,20), Ui.dp(this,18), Ui.dp(this,20), Ui.dp(this,18));')
s=s.replace('lapList = text("", 16, 0xFF333333);','lapList = text("", 15, Ui.MUTED);')
s=s.replace('TextView note=text("時・分・秒で設定。アプリを閉じても時刻を保持し、終了時は通知・アラーム音・振動で知らせます。",14,Ui.MUTED);','TextView note=text("アプリを閉じても継続し、終了時に音・振動・通知で知らせます。",13,Ui.MUTED);')
s=s.replace('private EditText numberBox(String hint) {\n        EditText e=new EditText(this); e.setHint(hint); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); e.setGravity(Gravity.CENTER); e.setTextSize(20); return e;\n    }','''private EditText numberBox(String hint) {
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(Ui.MUTED_2); e.setTextColor(Ui.TEXT);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); e.setGravity(Gravity.CENTER); e.setTextSize(20);
        e.setPadding(Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10));
        e.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,16,this));
        return e;
    }''')
p.write_text(s,encoding="utf-8")

# Version bump.
p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+', 'versionCode = 26', s)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "0.9.1"', s)
p.write_text(s,encoding="utf-8")
print("WakeGuard v0.9.1: calmer spacing and non-AI visual language applied")
