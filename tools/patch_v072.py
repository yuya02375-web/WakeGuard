from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v071.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"
main_path=java/"MainActivity.java"
multi_path=java/"MultiAlarmActivity.java"
alarm_path=java/"AlarmActivity.java"
build_path=root/"app/build.gradle.kts"

main=main_path.read_text(encoding="utf-8")
main=main.replace('multiAlarms.setText("⏰ アラームを追加・管理（何個でも）");','multiAlarms.setText("⏰ アラーム");',1)
old='''        enabled = new Switch(this); enabled.setText("アラームを有効にする"); root.addView(enabled);\n        root.addView(label("時刻"));\n        timeButton = new Button(this); root.addView(timeButton);\n        timeButton.setOnClickListener(v -> new TimePickerDialog(this, (view,hour,minute) -> {\n            Prefs.time(this, hour, minute); refresh();\n        }, Prefs.hour(this), Prefs.minute(this), true).show());\n\n        root.addView(label("繰り返し（毎日なら全部ON）"));\n        LinearLayout dayRow = new LinearLayout(this); dayRow.setOrientation(LinearLayout.HORIZONTAL);\n        String[] dn = {"月","火","水","木","金","土","日"};\n        for (int i=0;i<7;i++) { days[i] = new CheckBox(this); days[i].setText(dn[i]); dayRow.addView(days[i]); }\n        root.addView(dayRow);\n\n'''
new='''        // Time / weekday scheduling now lives only on the alarm list screen.\n        // Keep hidden controls so old persistence/refresh code stays compatible.\n        enabled = new Switch(this);\n        timeButton = new Button(this);\n        String[] dn = {"月","火","水","木","金","土","日"};\n        for (int i=0;i<7;i++) { days[i] = new CheckBox(this); days[i].setText(dn[i]); }\n\n        TextView commonTitle = new TextView(this);\n        commonTitle.setText("全アラーム共通設定");\n        commonTitle.setTextSize(22);\n        commonTitle.setTextColor(Color.BLACK);\n        commonTitle.setTypeface(null, android.graphics.Typeface.BOLD);\n        commonTitle.setPadding(0, 22, 0, 0);\n        root.addView(commonTitle);\n        TextView commonNote = new TextView(this);\n        commonNote.setText("解除歩数・音源・音量・振動は、登録したすべての起床ガードアラームに共通で使います。時刻や曜日は上の「アラーム」から管理します。");\n        commonNote.setTextSize(14);\n        commonNote.setTextColor(Color.DKGRAY);\n        commonNote.setPadding(0, 6, 0, 4);\n        root.addView(commonNote);\n\n'''
if old not in main: raise SystemExit("v072: legacy alarm UI block not found")
main=main.replace(old,new,1)
main_path.write_text(main,encoding="utf-8")

multi=multi_path.read_text(encoding="utf-8")
old_render='''        List<AlarmStore.Entry> alarms=AlarmStore.all(this);\n        if(alarms.isEmpty()){\n            TextView empty=text("追加アラームはまだありません。\\n上の「＋ アラームを追加」から作成できます。",17,0xFF666666);\n            empty.setGravity(Gravity.CENTER); empty.setPadding(0,60,0,0); list.addView(empty); return;\n        }\n        for(AlarmStore.Entry e:alarms) addCard(e);\n'''
new_render='''        addPrimaryCard();\n        List<AlarmStore.Entry> alarms=AlarmStore.all(this);\n        if(alarms.isEmpty()){\n            TextView empty=text("追加アラームはまだありません。\\n上の「＋ アラームを追加」から何個でも作成できます。",15,0xFF777777);\n            empty.setGravity(Gravity.CENTER); empty.setPadding(0,28,0,8); list.addView(empty);\n        } else {\n            for(AlarmStore.Entry e:alarms) addCard(e);\n        }\n'''
if old_render not in multi: raise SystemExit("v072: alarm list render block not found")
multi=multi.replace(old_render,new_render,1)

anchor='''    private String daysText(AlarmStore.Entry e){\n        if(e.dayMask==0)return "1回のみ";\n        if((e.dayMask&0x7F)==0x7F)return "毎日";\n        StringBuilder b=new StringBuilder();\n        for(int i=0;i<7;i++) if((e.dayMask&(1<<i))!=0){ if(b.length()>0)b.append(' '); b.append(DAY_NAMES[i]); }\n        return b.toString();\n    }\n\n'''
insert=anchor+'''    private String daysText(int mask){\n        if(mask==0)return "1回のみ";\n        if((mask&0x7F)==0x7F)return "毎日";\n        StringBuilder b=new StringBuilder();\n        for(int i=0;i<7;i++) if((mask&(1<<i))!=0){ if(b.length()>0)b.append(' '); b.append(DAY_NAMES[i]); }\n        return b.toString();\n    }\n\n    private void addPrimaryCard(){\n        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);\n        card.setPadding(20,16,20,16); card.setBackgroundColor(0xFFEAF2FF);\n        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,10,0,0);\n        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);\n        TextView time=text(String.format("%02d:%02d",Prefs.hour(this),Prefs.minute(this)),34,Color.BLACK);\n        time.setTypeface(Typeface.MONOSPACE,Typeface.BOLD); row.addView(time,new LinearLayout.LayoutParams(0,-2,1));\n        Switch sw=new Switch(this); sw.setChecked(Prefs.enabled(this)); row.addView(sw); card.addView(row);\n        TextView detail=text("メインアラーム  •  "+daysText(Prefs.dayMask(this)),15,0xFF445566);\n        detail.setPadding(0,2,0,10); card.addView(detail);\n        Button edit=button("編集"); card.addView(edit);\n        sw.setOnCheckedChangeListener((v,on)->{ Prefs.enabled(this,on); AlarmScheduler.reschedule(this); });\n        edit.setOnClickListener(v->editPrimaryDialog());\n        list.addView(card,cp);\n    }\n\n    private void editPrimaryDialog(){\n        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int pad=24; box.setPadding(pad,8,pad,0);\n        TimePicker tp=new TimePicker(this); tp.setIs24HourView(true); tp.setHour(Prefs.hour(this)); tp.setMinute(Prefs.minute(this)); box.addView(tp);\n        TextView rep=text("繰り返し（未選択＝1回のみ）",15,0xFF555555); rep.setPadding(0,10,0,4); box.addView(rep);\n        LinearLayout drow=new LinearLayout(this); drow.setOrientation(LinearLayout.HORIZONTAL);\n        CheckBox[] checks=new CheckBox[7]; int mask=Prefs.dayMask(this);\n        for(int i=0;i<7;i++){ checks[i]=new CheckBox(this); checks[i].setText(DAY_NAMES[i]); checks[i].setChecked((mask&(1<<i))!=0); drow.addView(checks[i],new LinearLayout.LayoutParams(0,-2,1)); }\n        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.addView(drow); box.addView(hsv);\n        new AlertDialog.Builder(this).setTitle("メインアラームを編集").setView(box).setNegativeButton("キャンセル",null).setPositiveButton("保存",(d,w)->{\n            int m=0; for(int i=0;i<7;i++)if(checks[i].isChecked())m|=1<<i;\n            Prefs.time(this,tp.getHour(),tp.getMinute()); Prefs.dayMask(this,m);\n            AlarmScheduler.reschedule(this); render();\n        }).show();\n    }\n\n'''
if anchor not in multi: raise SystemExit("v072: daysText anchor not found")
multi=multi.replace(anchor,insert,1)
multi_path.write_text(multi,encoding="utf-8")

aa=alarm_path.read_text(encoding="utf-8")
old_launch='''        long alarmId = i.getLongExtra(EXTRA_ALARM_ID, AlarmScheduler.PRIMARY_ALARM_ID);\n        try { AlarmStore.markFiredIfOneShot(this, alarmId); } catch (Throwable ignored) {}\n'''
new_launch='''        long alarmId = i.getLongExtra(EXTRA_ALARM_ID, AlarmScheduler.PRIMARY_ALARM_ID);\n        try {\n            AlarmStore.markFiredIfOneShot(this, alarmId);\n            if (alarmId == AlarmScheduler.PRIMARY_ALARM_ID && Prefs.dayMask(this) == 0)\n                Prefs.enabled(this, false);\n        } catch (Throwable ignored) {}\n'''
if old_launch not in aa: raise SystemExit("v072: scheduled launch block not found")
aa=aa.replace(old_launch,new_launch,1)
alarm_path.write_text(aa,encoding="utf-8")

build=build_path.read_text(encoding="utf-8")
build,n1=re.subn(r"versionCode\s*=\s*\d+","versionCode = 23",build,count=1)
build,n2=re.subn(r'versionName\s*=\s*"[^"]+"','versionName = "0.7.2"',build,count=1)
if n1!=1 or n2!=1: raise SystemExit("v072: version bump failed")
build_path.write_text(build,encoding="utf-8")
print("Applied WakeGuard v0.7.2 alarm UI cleanup")
