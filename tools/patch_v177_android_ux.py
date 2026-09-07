from pathlib import Path
import re

app = Path("WakeGuard/app")
java = app / "src/main/java/jp/wakeguard/alarm"

# Version bump.
p = app / "build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = re.sub(r'versionCode = \d+', 'versionCode = 88', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.7.7"', s)
p.write_text(s, encoding="utf-8")

# Improve Android text contrast. MUTED_2 is used for input hints/dividers and was
# too dark on the soot-black surfaces. Keep the warm palette but lift luminance.
ui = java / "Ui.java"
s = ui.read_text(encoding="utf-8")
s = s.replace('public static final int MUTED_2 = 0xFF786B61;',
              'public static final int MUTED_2 = 0xFF968A80;')
ui.write_text(s, encoding="utf-8")

# Alarm list UX:
# - do not fade the whole disabled row to 48% (this crushed secondary text contrast)
# - expose a direct delete action in the list for user-created alarms
# - long-press remains an alternate delete gesture
main = java / "MainActivity.java"
s = main.read_text(encoding="utf-8")
old = 'row.setBackground(Ui.burnCard(this,e.enabled));if(Build.VERSION.SDK_INT>=21)row.setElevation(0);row.setAlpha(e.enabled?1f:.48f);row.setOnClickListener(v->edit(e.id));'
new = 'row.setBackground(Ui.burnCard(this,e.enabled));if(Build.VERSION.SDK_INT>=21)row.setElevation(0);row.setAlpha(1f);row.setOnClickListener(v->edit(e.id));if(e.id>=1000)row.setOnLongClickListener(v->{confirmDelete(e);return true;});'
assert old in s, "MainActivity alarm row setup not found"
s = s.replace(old, new, 1)

old = 'Switch on=new Switch(this);on.setChecked(e.enabled);Ui.styleSwitch(on,this);first.addView(on);row.addView(first);'
new = '''if(e.id>=1000){
            Button del=Ui.ghostButton(this,"削除");del.setTextSize(12);del.setTextColor(Ui.DANGER);del.setContentDescription(I18n.tr(this,"アラームを削除"));del.setOnClickListener(v->confirmDelete(e));first.addView(del);
        }
        Switch on=new Switch(this);on.setChecked(e.enabled);Ui.styleSwitch(on,this);first.addView(on);row.addView(first);'''
assert old in s, "MainActivity switch insertion point not found"
s = s.replace(old, new, 1)

old = 'String label=(e.label==null||e.label.trim().isEmpty())?"アラーム":e.label.trim();TextView name=Ui.text(this,label,16,Ui.TEXT);row.addView(name,Ui.gapTop(this,3));\n        TextView detail=Ui.text(this,repeatText(e.dayMask)+"   ·   "+I18n.tr(this,AlarmProfiles.missionName(e.missionType))+" "+I18n.tr(this,AlarmProfiles.missionSummary(this,e.id)),13,Ui.MUTED);row.addView(detail,Ui.gapTop(this,4));'
new = 'String label=(e.label==null||e.label.trim().isEmpty())?"アラーム":e.label.trim();TextView name=Ui.text(this,label,16,e.enabled?Ui.TEXT:Ui.MUTED);row.addView(name,Ui.gapTop(this,3));\n        TextView detail=Ui.text(this,repeatText(e.dayMask)+"   ·   "+I18n.tr(this,AlarmProfiles.missionName(e.missionType))+" "+I18n.tr(this,AlarmProfiles.missionSummary(this,e.id)),13,Ui.MUTED);row.addView(detail,Ui.gapTop(this,4));'
assert old in s, "MainActivity label/detail block not found"
s = s.replace(old, new, 1)

needle = '    private void refreshNext(){long ms=AlarmScheduler.nextTriggerMillis(this);'
insert = '''    private void confirmDelete(AlarmStore.Entry e){
        if(e==null||e.id<1000)return;
        new AlertDialog.Builder(this)
                .setTitle(I18n.tr(this,"アラームを削除"))
                .setMessage(I18n.tr(this,"このアラームだけ削除します。"))
                .setNegativeButton(I18n.tr(this,"キャンセル"),null)
                .setPositiveButton(I18n.tr(this,"削除"),(d,w)->{
                    AlarmScheduler.cancelExtraAlarm(this,e.id);
                    AlarmProfiles.delete(this,e.id);
                    AlarmScheduler.reschedule(this);
                    render();
                }).show();
    }

'''+needle
assert needle in s, "MainActivity helper insertion point not found"
s = s.replace(needle, insert, 1)
main.write_text(s, encoding="utf-8")

# Validation.
assert 'versionName = "1.7.7"' in p.read_text(encoding="utf-8")
assert 'versionCode = 88' in p.read_text(encoding="utf-8")
assert 'MUTED_2 = 0xFF968A80' in ui.read_text(encoding="utf-8")
ms = main.read_text(encoding="utf-8")
assert 'row.setAlpha(1f)' in ms
assert 'Button del=Ui.ghostButton(this,"削除")' in ms
assert 'setOnLongClickListener' in ms
assert 'private void confirmDelete(AlarmStore.Entry e)' in ms
print("IGNIDO Wake Android v1.7.7 UX patch applied")
