from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v114.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"

p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")

old='''    private void buildWorldClock() {
        LinearLayout setting=Ui.row(this);
        TextView label=text("24時間表示",15,Ui.TEXT); setting.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch h24=new Switch(this); h24.setChecked(p().getBoolean(KEY_24H,true));
        h24.setOnCheckedChangeListener((v,checked)->{p().edit().putBoolean(KEY_24H,checked).apply();updateLiveUi();});
        setting.addView(h24); body.addView(setting); body.addView(Ui.divider(this));

        TextView cityHeader=Ui.sectionHeader(this,"都市"); cityHeader.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,6)); body.addView(cityHeader);
        LinkedHashSet<String> zones=loadZones();
        for(String zone:zones)addWorldCard(zone);
    }
'''
new='''    private void buildWorldClock() {
        // Always show the phone's current/default time zone first, large and easy to read.
        TextView here=Ui.text(this,"現在のタイムゾーン",12,Ui.MUTED);
        body.addView(here);

        localClock.setVisibility(View.VISIBLE);
        localClock.setTextColor(Ui.TEXT);
        localClock.setTextSize(52);
        localClock.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
        localClock.setGravity(Gravity.START);
        localClock.setPadding(0,Ui.dp(this,3),0,0);
        body.addView(localClock,new LinearLayout.LayoutParams(-1,-2));

        localDetail.setVisibility(View.VISIBLE);
        localDetail.setTextColor(Ui.MUTED);
        localDetail.setTextSize(13);
        localDetail.setLineSpacing(0,1.08f);
        localDetail.setPadding(0,Ui.dp(this,2),0,Ui.dp(this,14));
        body.addView(localDetail,new LinearLayout.LayoutParams(-1,-2));
        body.addView(Ui.divider(this));

        LinearLayout setting=Ui.row(this);
        setting.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));
        TextView label=text("24時間表示",15,Ui.TEXT); setting.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch h24=new Switch(this); h24.setChecked(p().getBoolean(KEY_24H,true));
        h24.setOnCheckedChangeListener((v,checked)->{p().edit().putBoolean(KEY_24H,checked).apply();updateLiveUi();});
        setting.addView(h24); body.addView(setting); body.addView(Ui.divider(this));

        TextView cityHeader=Ui.sectionHeader(this,"登録した都市"); cityHeader.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,6)); body.addView(cityHeader);
        LinkedHashSet<String> zones=loadZones();
        for(String zone:zones)addWorldCard(zone);
    }
'''
if old not in s:
    raise SystemExit("buildWorldClock block not found")
s=s.replace(old,new)

# Make the local detail identify the device time zone clearly without adding another card.
old_detail='''        localDetail.setText(now.format(DateTimeFormatter.ofPattern("yyyy年M月d日 (E)", Locale.JAPAN))
                + "  •  " + now.getZone().getId() + "  •  UTC" + offset);'''
new_detail='''        localDetail.setText(now.format(DateTimeFormatter.ofPattern("yyyy年M月d日 (E)", Locale.JAPAN))
                + "  ·  " + friendlyZoneName(now.getZone().getId())
                + "  ·  " + now.getZone().getId()
                + "  ·  UTC" + offset);'''
if old_detail not in s:
    raise SystemExit("local detail block not found")
s=s.replace(old_detail,new_detail)
p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+','versionCode = 34',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.1.5"',s)
p.write_text(s,encoding="utf-8")

print("WakeGuard v1.1.5: large current/default time-zone clock restored at top of world clock")
