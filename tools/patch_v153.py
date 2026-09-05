from pathlib import Path
import re, runpy

runpy.run_path('tools/patch_v152.py', run_name='__main__')
app=Path('WakeGuard/app'); j=app/'src/main/java/jp/wakeguard/alarm'

# v1.5.3
p=app/'build.gradle.kts'; s=p.read_text(encoding='utf-8')
s=re.sub(r'versionCode = \d+','versionCode = 64',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.5.3"',s)
p.write_text(s,encoding='utf-8')

p=j/'AlarmActivity.java'; s=p.read_text(encoding='utf-8')

# The alarm video's Japanese captions are intentionally near the bottom.
# In direct-stop video mode, keep that entire lower caption area clear:
# - no bottom warning/footer over video
# - no large bottom stop button
# - use a compact stop control in the top-right instead
old='''        root.addView(stop);stop.bringToFront();
        TextView footer = Ui.text(this, "戻る・ホームでは停止しません", 12, alarmVideoMode?Ui.TEXT:Ui.MUTED);if(alarmVideoMode)footer.setShadowLayer(7f,0,2,Color.BLACK); footer.setGravity(Gravity.CENTER); root.addView(footer, Ui.gapTop(this,12));
        frame.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);if(alarmVideoMode)startAlarmVideo();
'''
new='''        if(directVideoStop){
            stop.setText(I18n.tr(this,"停止"));
            stop.setMinHeight(Ui.dp(this,48));
            stop.setTextSize(16);
        }else{
            root.addView(stop);stop.bringToFront();
        }
        if(!alarmVideoMode){
            TextView footer = Ui.text(this, "戻る・ホームでは停止しません", 12, Ui.MUTED);footer.setGravity(Gravity.CENTER);root.addView(footer, Ui.gapTop(this,12));
        }
        frame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        if(directVideoStop){
            FrameLayout.LayoutParams stopLp=new FrameLayout.LayoutParams(Ui.dp(this,86),Ui.dp(this,48),Gravity.TOP|Gravity.END);
            stopLp.setMargins(0,Ui.dp(this,28),Ui.dp(this,16),0);
            frame.addView(stop,stopLp);stop.bringToFront();
        }
        setContentView(frame);if(alarmVideoMode)startAlarmVideo();
'''
if old not in s:
    raise SystemExit('bottom video stop/footer anchor missing')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')

# Localize the compact label.
p=j/'I18n.java'; s=p.read_text(encoding='utf-8')
needle='put("停止ボタンを押すとアラームを停止します","Tap the stop button to stop the alarm","중지 버튼을 누르면 알람이 중지됩니다");'
if needle in s and 'put("停止","Stop","중지")' not in s:
    s=s.replace(needle,needle+' put("停止","Stop","중지");',1)
p.write_text(s,encoding='utf-8')

# Validation: direct video stop must be floating, and no footer may be drawn over video.
t=(j/'AlarmActivity.java').read_text(encoding='utf-8')
for required in [
    'FrameLayout.LayoutParams stopLp=new FrameLayout.LayoutParams(Ui.dp(this,86),Ui.dp(this,48),Gravity.TOP|Gravity.END)',
    'if(!alarmVideoMode)',
    'stop.setText(I18n.tr(this,"停止"))'
]:
    if required not in t:
        raise SystemExit('validation missing: '+required)

print('WakeGuard v1.5.3 subtitle-safe direct video stop UI applied')
