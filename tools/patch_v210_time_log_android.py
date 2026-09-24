from pathlib import Path
import re, shutil

r=Path('WakeGuard/app')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=re.sub(r'versionCode = \d+', 'versionCode = 110', s, count=1)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "2.1.0"', s, count=1)
write(p,s)

manifest=Path('WakeGuard/app/src/main/AndroidManifest.xml')
s=manifest.read_text()
needle='        <activity android:name=".ClockSettingsActivity" android:exported="false" />'
if '.TimeLogActivity' not in s:
    s=s.replace(needle, needle+'\n        <activity android:name=".TimeLogActivity" android:exported="false" />',1)
manifest.write_text(s)

p='src/main/java/jp/wakeguard/alarm/ClockActivity.java'; s=read(p)
needle='''        stopwatchStartPause.setOnClickListener(v->toggleStopwatch()); stopwatchLapReset.setOnClickListener(v->lapOrReset());
        lapList=text("",15,Ui.MUTED);'''
replacement='''        stopwatchStartPause.setOnClickListener(v->toggleStopwatch()); stopwatchLapReset.setOnClickListener(v->lapOrReset());
        Button timeLog=Ui.button(this,"時間記録・集計",false);timeLog.setOnClickListener(v->startActivity(new Intent(this,TimeLogActivity.class)));LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,Ui.dp(this,50));tlp.setMargins(0,Ui.dp(this,12),0,0);body.addView(timeLog,tlp);
        lapList=text("",15,Ui.MUTED);'''
if needle not in s: raise SystemExit('stopwatch insertion point not found')
s=s.replace(needle,replacement,1)
write(p,s)

src=Path('tools/v210/TimeLogActivity.java')
dst=Path('WakeGuard/app/src/main/java/jp/wakeguard/alarm/TimeLogActivity.java')
shutil.copy2(src,dst)

assert 'versionName = "2.1.0"' in read('build.gradle.kts')
assert 'versionCode = 110' in read('build.gradle.kts')
assert '.TimeLogActivity' in manifest.read_text()
assert '時間記録・集計' in read(p)
assert dst.exists()
print('Android 2.1.0 time log folders patch applied')
