from pathlib import Path
import re, shutil

r=Path('WakeGuard/app')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=re.sub(r'versionCode = \d+', 'versionCode = 120', s, count=1)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "2.2.0"', s, count=1)
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

shutil.copy2(Path('tools/v220/TimeLogActivity.java'),Path('WakeGuard/app/src/main/java/jp/wakeguard/alarm/TimeLogActivity.java'))

p='src/main/java/jp/wakeguard/alarm/AlarmEditorActivity.java'; s=read(p)
s=s.replace('import java.util.Locale;','import java.util.Locale;\nimport java.time.ZonedDateTime;',1)
needle='''        time=Ui.ghostButton(this,"07:00");time.setTextSize(52);time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);time.setGravity(Gravity.CENTER);time.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{draft.hour=h;draft.minute=m;time.setText(String.format(Locale.JAPAN,"%02d:%02d",h,m));},draft.hour,draft.minute,true).show());body.addView(time,new LinearLayout.LayoutParams(-1,Ui.dp(this,86)));

        enabled=new Switch(this);'''
replacement='''        time=Ui.ghostButton(this,"07:00");time.setTextSize(52);time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);time.setGravity(Gravity.CENTER);time.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{draft.hour=h;draft.minute=m;time.setText(String.format(Locale.JAPAN,"%02d:%02d",h,m));},draft.hour,draft.minute,true).show());body.addView(time,new LinearLayout.LayoutParams(-1,Ui.dp(this,86)));
        Button after=Ui.ghostButton(this,"○時間後に設定  ›");after.setTextColor(Ui.ACCENT);after.setGravity(Gravity.CENTER);after.setOnClickListener(v->showAfterDialog());body.addView(after,Ui.gapTop(this,2));

        enabled=new Switch(this);'''
if needle not in s: raise SystemExit('relative alarm insertion point not found')
s=s.replace(needle,replacement,1)
marker='    private void chooseMission(){'
methods='''    private void applyAfterMinutes(int total){
        if(total<=0||total>=1440){Toast.makeText(this,"1分〜23時間59分で設定してください",Toast.LENGTH_SHORT).show();return;}
        ZonedDateTime target=ZonedDateTime.now().plusMinutes(total);draft.hour=target.getHour();draft.minute=target.getMinute();draft.dayMask=0;
        time.setText(String.format(Locale.JAPAN,"%02d:%02d",draft.hour,draft.minute));for(CheckBox d:days)if(d!=null)d.setChecked(false);
        int h=total/60,m=total%60;String text=(h>0?h+"時間":"")+(m>0?m+"分":"")+"後 → "+String.format(Locale.JAPAN,"%02d:%02d",draft.hour,draft.minute);Toast.makeText(this,text,Toast.LENGTH_SHORT).show();
    }
    private void showAfterDialog(){
        final int[] vals={15,30,60,120,180,360,720,-1};
        final String[] labels={"15分後","30分後","1時間後","2時間後","3時間後","6時間後","12時間後","カスタム…"};
        new AlertDialog.Builder(this).setTitle("○時間後に設定").setItems(labels,(d,w)->{if(vals[w]>0)applyAfterMinutes(vals[w]);else showCustomAfterDialog();}).show();
    }
    private void showCustomAfterDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.CENTER);box.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),0);
        NumberPicker hours=new NumberPicker(this);hours.setMinValue(0);hours.setMaxValue(23);hours.setValue(1);
        NumberPicker mins=new NumberPicker(this);mins.setMinValue(0);mins.setMaxValue(59);mins.setValue(0);
        box.addView(hours,new LinearLayout.LayoutParams(Ui.dp(this,96),Ui.dp(this,130)));box.addView(Ui.text(this,"時間",14,Ui.MUTED));box.addView(mins,new LinearLayout.LayoutParams(Ui.dp(this,96),Ui.dp(this,130)));box.addView(Ui.text(this,"分",14,Ui.MUTED));
        new AlertDialog.Builder(this).setTitle("今からどれくらい後？").setView(box).setNegativeButton("キャンセル",null).setPositiveButton("設定",(d,w)->{int total=hours.getValue()*60+mins.getValue();if(total>0)applyAfterMinutes(total);else Toast.makeText(this,"1分以上に設定してください",Toast.LENGTH_SHORT).show();}).show();
    }

'''
if marker not in s: raise SystemExit('method insertion point not found')
s=s.replace(marker,methods+marker,1)
write(p,s)

assert 'versionName = "2.2.0"' in read('build.gradle.kts')
assert 'versionCode = 120' in read('build.gradle.kts')
assert '時間記録・集計' in read('src/main/java/jp/wakeguard/alarm/ClockActivity.java')
assert 'ストップウォッチで記録' in read('src/main/java/jp/wakeguard/alarm/TimeLogActivity.java')
assert 'Ui.applySystemBarInsets' in read('src/main/java/jp/wakeguard/alarm/TimeLogActivity.java')
assert '○時間後に設定' in read('src/main/java/jp/wakeguard/alarm/AlarmEditorActivity.java')
print('Android 2.2.0 patch applied')
