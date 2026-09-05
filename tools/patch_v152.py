from pathlib import Path
import re, runpy

runpy.run_path('tools/patch_v151.py', run_name='__main__')
app=Path('WakeGuard/app'); j=app/'src/main/java/jp/wakeguard/alarm'

# v1.5.2
p=app/'build.gradle.kts'; s=p.read_text(encoding='utf-8')
s=re.sub(r'versionCode = \d+','versionCode = 63',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.5.2"',s)
p.write_text(s,encoding='utf-8')

# A plain View measured with WRAP_CONTENT can consume the full available height.
# Ui.divider() sets a 1dp LayoutParams, but callers that passed Ui.gapTop()
# replaced that height with WRAP_CONTENT. Preserve the 1dp height explicitly.
p=j/'Ui.java'; s=p.read_text(encoding='utf-8')
anchor='''    public static LinearLayout.LayoutParams gapTop(Activity a, int top) {\n        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);\n        p.setMargins(0,dp(a,top),0,0); return p;\n    }\n'''
helper=anchor+'''\n    public static LinearLayout.LayoutParams dividerTop(Activity a, int top) {\n        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(a,1));\n        p.setMargins(0,dp(a,top),0,0); return p;\n    }\n'''
if 'dividerTop(Activity a, int top)' not in s:
    if anchor not in s:
        raise SystemExit('gapTop anchor missing')
    s=s.replace(anchor,helper,1)
p.write_text(s,encoding='utf-8')

# Fix every known divider call that discarded the divider's 1dp LayoutParams.
changed=0
for p in j.glob('*.java'):
    s=p.read_text(encoding='utf-8')
    out,n=re.subn(r'addView\(Ui\.divider\(this\),Ui\.gapTop\(this,(\d+)\)\)',
                  r'addView(Ui.divider(this),Ui.dividerTop(this,\1))',s)
    if n:
        changed+=n
        p.write_text(out,encoding='utf-8')
if changed < 6:
    raise SystemExit(f'expected divider fixes, got {changed}')

# Video alarm: remove the divider entirely over video, remove PlayerView shutter color,
# and make NONE mode a clean full-screen video with only time/title + stop control.
p=j/'AlarmActivity.java'; s=p.read_text(encoding='utf-8')
old='alarmVideoView.setBackgroundColor(Color.BLACK);alarmVideoView.setUseController(false);'
new='alarmVideoView.setBackgroundColor(Color.TRANSPARENT);alarmVideoView.setShutterBackgroundColor(Color.TRANSPARENT);alarmVideoView.setUseController(false);'
if old not in s:
    raise SystemExit('PlayerView background anchor missing')
s=s.replace(old,new,1)

old='''        TextView now = Ui.title(this, java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), 60);if(alarmVideoMode)now.setShadowLayer(10f,0,2,Color.BLACK);'''
new='''        TextView now = Ui.title(this, java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), alarmVideoMode?52:60);if(alarmVideoMode)now.setShadowLayer(10f,0,2,Color.BLACK);'''
if old not in s:
    raise SystemExit('clock size anchor missing')
s=s.replace(old,new,1)

old='''        root.addView(Ui.divider(this),Ui.dividerTop(this,24));'''
new='''        if(!alarmVideoMode)root.addView(Ui.divider(this),Ui.dividerTop(this,24));'''
if old not in s:
    raise SystemExit('alarm divider anchor missing')
s=s.replace(old,new,1)

old='''        String type=sessionType();
        missionName=Ui.title(this,AlarmProfiles.missionName(type),24);if(alarmVideoMode)missionName.setShadowLayer(8f,0,2,Color.BLACK);missionName.setGravity(Gravity.CENTER);root.addView(missionName,Ui.gapTop(this,28));
        TextView instruction=Ui.text(this,"NONE".equals(type)?"停止ボタンを押すとアラームを停止します":"このミッションを完了するとアラームを解除できます",13,alarmVideoMode?Ui.TEXT:Ui.MUTED);if(alarmVideoMode)instruction.setShadowLayer(7f,0,2,Color.BLACK);instruction.setGravity(Gravity.CENTER);root.addView(instruction,Ui.gapTop(this,6));

        missionCard = new LinearLayout(this); missionCard.setOrientation(LinearLayout.VERTICAL); missionCard.setGravity(Gravity.CENTER_HORIZONTAL);
        missionCard.setPadding(0,Ui.dp(this,28),0,Ui.dp(this,16)); root.addView(missionCard,new LinearLayout.LayoutParams(-1,0,1)); buildMission(type);
'''
new='''        String type=sessionType();
        boolean directVideoStop=alarmVideoMode&&"NONE".equals(type);
        missionCard = new LinearLayout(this); missionCard.setOrientation(LinearLayout.VERTICAL); missionCard.setGravity(Gravity.CENTER_HORIZONTAL);
        if(directVideoStop){
            Prefs.missionComplete(this,true);count=null;prompt=null;feedback=null;missionName=null;
            root.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));
        }else{
            missionName=Ui.title(this,AlarmProfiles.missionName(type),24);if(alarmVideoMode)missionName.setShadowLayer(8f,0,2,Color.BLACK);missionName.setGravity(Gravity.CENTER);root.addView(missionName,Ui.gapTop(this,28));
            TextView instruction=Ui.text(this,"NONE".equals(type)?"停止ボタンを押すとアラームを停止します":"このミッションを完了するとアラームを解除できます",13,alarmVideoMode?Ui.TEXT:Ui.MUTED);if(alarmVideoMode)instruction.setShadowLayer(7f,0,2,Color.BLACK);instruction.setGravity(Gravity.CENTER);root.addView(instruction,Ui.gapTop(this,6));
            missionCard.setPadding(0,Ui.dp(this,28),0,Ui.dp(this,16)); root.addView(missionCard,new LinearLayout.LayoutParams(-1,0,1)); buildMission(type);
        }
'''
if old not in s:
    raise SystemExit('mission block anchor missing')
s=s.replace(old,new,1)

old='''        stop.setOnClickListener(v -> { if (missionDone()) { try { startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP)); } catch (Throwable ignored) {} try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); } } });'''
new='''        stop.setOnClickListener(v -> { String current=sessionType();if("NONE".equals(current))Prefs.missionComplete(this,true);if("NONE".equals(current)||missionDone()) { try { startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP)); } catch (Throwable ignored) {} try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); } } });'''
if old not in s:
    raise SystemExit('stop listener anchor missing')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')

# Validation: the dangerous divider pattern must be gone everywhere.
bad=[]
for p in j.glob('*.java'):
    t=p.read_text(encoding='utf-8')
    if re.search(r'addView\(Ui\.divider\(this\),Ui\.gapTop\(this,\d+\)\)',t):
        bad.append(str(p))
if bad:
    raise SystemExit('unfixed divider calls: '+','.join(bad))

print('WakeGuard v1.5.2 giant-divider + video direct-stop fix applied; divider calls fixed:', changed)
