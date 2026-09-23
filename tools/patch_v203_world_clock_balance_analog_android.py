from pathlib import Path
import re

r=Path('WakeGuard/app')
def read(p): return (r/p).read_text()
def write(p,s): (r/p).write_text(s)

p='build.gradle.kts'
s=read(p)
s=re.sub(r'versionCode = \d+', 'versionCode = 103', s, count=1)
s=re.sub(r'versionName = "[^"]+"', 'versionName = "2.0.3"', s, count=1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/ClockActivity.java'
s=read(p)

old='TextView name=text(nameText,columns<=2?20:columns==3?17:columns==4?15:13,Ui.TEXT);name.setTypeface(null,Typeface.BOLD);name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);card.addView(name,new LinearLayout.LayoutParams(-1,-2));'
new='TextView name=text(nameText,columns<=2?18:columns==3?15:columns==4?13:11,Ui.TEXT);name.setTypeface(null,Typeface.BOLD);name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setMaxLines(1);name.setEllipsize(null);name.setAutoSizeTextTypeUniformWithConfiguration(columns<=2?12:10,columns<=2?18:columns==3?15:columns==4?13:11,1,android.util.TypedValue.COMPLEX_UNIT_SP);card.addView(name,new LinearLayout.LayoutParams(-1,-2));'
if old not in s: raise SystemExit('city text block missing')
s=s.replace(old,new,1)

old='FrameLayout stage=new FrameLayout(this);TextView time=text("--:--",columns<=2?42:columns==3?34:columns==4?28:23,Ui.TEXT);time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);time.setGravity(Gravity.CENTER);stage.addView(time,new FrameLayout.LayoutParams(-1,-1));card.addView(stage,new LinearLayout.LayoutParams(-1,0,1));'
new='FrameLayout stage=new FrameLayout(this);TextView time=text("--:--",columns<=2?36:columns==3?30:columns==4?24:20,Ui.TEXT);time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);time.setGravity(Gravity.CENTER);time.setSingleLine(true);time.setMaxLines(1);time.setEllipsize(null);time.setAutoSizeTextTypeUniformWithConfiguration(columns<=2?24:columns==3?20:16,columns<=2?36:columns==3?30:columns==4?24:20,1,android.util.TypedValue.COMPLEX_UNIT_SP);ClockFaceActivity.AnalogFace analog=new ClockFaceActivity.AnalogFace(this);time.setVisibility(worldAnalog()?View.GONE:View.VISIBLE);analog.setVisibility(worldAnalog()?View.VISIBLE:View.GONE);stage.addView(time,new FrameLayout.LayoutParams(-1,-1));stage.addView(analog,new FrameLayout.LayoutParams(-1,-1));card.addView(stage,new LinearLayout.LayoutParams(-1,0,1));'
if old not in s: raise SystemExit('time stage block missing')
s=s.replace(old,new,1)

old='TextView detail=text("",columns<=2?14:columns==3?13:columns==4?12:11,Ui.MUTED);detail.setGravity(Gravity.CENTER);detail.setSingleLine(true);detail.setEllipsize(TextUtils.TruncateAt.END);card.addView(detail,new LinearLayout.LayoutParams(-1,-2));'
new='TextView detail=text("",columns<=2?13:columns==3?11:columns==4?10:9,Ui.MUTED);detail.setGravity(Gravity.CENTER);detail.setSingleLine(true);detail.setMaxLines(1);detail.setEllipsize(null);detail.setAutoSizeTextTypeUniformWithConfiguration(8,columns<=2?13:columns==3?11:columns==4?10:9,1,android.util.TypedValue.COMPLEX_UNIT_SP);card.addView(detail,new LinearLayout.LayoutParams(-1,-2));'
if old not in s: raise SystemExit('detail text block missing')
s=s.replace(old,new,1)

old='card.setOnClickListener(v->openClockFace("world",zoneId,-1L));\n        worldRows.put(zoneId,new WorldRow(stage,time,null,detail));'
new='card.setContentDescription(I18n.tr(this,"タップでデジタル / アナログ切替。長押しで拡大表示"));card.setOnClickListener(v->toggleWorldClockMode());card.setOnLongClickListener(v->{openClockFace("world",zoneId,-1L);return true;});\n        worldRows.put(zoneId,new WorldRow(stage,time,analog,detail));'
if old not in s: raise SystemExit('card click/world row block missing')
s=s.replace(old,new,1)

old='private void applyWorldDisplayMode(){\n        if(worldOverview())return;\n        boolean analog=worldAnalog();'
new='private void applyWorldDisplayMode(){\n        boolean analog=worldAnalog();\n        if(worldOverview()){for(WorldRow r:worldRows.values()){r.time.setVisibility(analog?View.GONE:View.VISIBLE);if(r.analog!=null)r.analog.setVisibility(analog?View.VISIBLE:View.GONE);}return;}'
if old not in s: raise SystemExit('apply display block missing')
s=s.replace(old,new,1)

write(p,s)

assert 'versionName = "2.0.3"' in read('build.gradle.kts')
assert 'versionCode = 103' in read('build.gradle.kts')
assert 'columns==3?30' in read(p)
assert 'new ClockFaceActivity.AnalogFace(this)' in read(p)
assert 'setOnClickListener(v->toggleWorldClockMode())' in read(p)
assert 'setAutoSizeTextTypeUniformWithConfiguration' in read(p)
print('Android 2.0.3 balanced text + overview analog patch applied')
