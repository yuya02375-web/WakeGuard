from pathlib import Path
root=Path('WakeGuard/app')
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=s.replace('versionCode = 140','versionCode = 141',1).replace('versionName = "2.4.0"','versionName = "2.4.1"',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/StatsActivity.java'; s=read(p)
old='''        LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,14));
        companion=new StreakCompanionView(this);LinearLayout.LayoutParams creatureLp=new LinearLayout.LayoutParams(Ui.dp(this,170),Ui.dp(this,220));hero.addView(companion,creatureLp);
        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(Ui.dp(this,18),Ui.dp(this,8),0,0);
        currentValue=Ui.text(this,"0日",48,Ui.TEXT);currentValue.setTypeface(null,Typeface.BOLD);summary.addView(currentValue);
        TextView streakLabel=Ui.text(this,"現在のストリーク",13,Ui.MUTED);summary.addView(streakLabel);
        growthLevel=Ui.text(this,"Lv.1",16,Ui.TEXT);growthLevel.setTypeface(null,Typeface.BOLD);growthLevel.setPadding(0,Ui.dp(this,18),0,0);summary.addView(growthLevel);
        growthForm=Ui.text(this,"火種",12,Ui.ACCENT_2);growthForm.setPadding(0,Ui.dp(this,2),0,0);summary.addView(growthForm);
        hero.addView(summary,new LinearLayout.LayoutParams(0,-2,1));body.addView(hero);
        body.addView(Ui.divider(this));

        TextView protectHeader=Ui.sectionHeader(this,"ストリーク保護");protectHeader.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,6));body.addView(protectHeader);'''
new='''        LinearLayout hero=Ui.row(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,8));
        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);summary.setGravity(Gravity.CENTER_VERTICAL);
        currentValue=Ui.text(this,"0日",54,Ui.TEXT);currentValue.setTypeface(null,Typeface.BOLD);summary.addView(currentValue);
        TextView streakLabel=Ui.text(this,"現在のストリーク",13,Ui.MUTED);summary.addView(streakLabel);
        growthForm=Ui.text(this,"火種",12,Ui.ACCENT_2);growthForm.setPadding(0,Ui.dp(this,14),0,0);summary.addView(growthForm);
        hero.addView(summary,new LinearLayout.LayoutParams(0,-2,1));
        companion=new StreakCompanionView(this);LinearLayout.LayoutParams creatureLp=new LinearLayout.LayoutParams(Ui.dp(this,108),Ui.dp(this,136));hero.addView(companion,creatureLp);
        body.addView(hero);
        body.addView(Ui.divider(this));

        LinearLayout metrics=Ui.row(this);metrics.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,10));
        LinearLayout bestBox=new LinearLayout(this);bestBox.setOrientation(LinearLayout.VERTICAL);TextView bestLabel=Ui.text(this,"最高",12,Ui.MUTED);bestBox.addView(bestLabel);bestValue=Ui.text(this,"-",20,Ui.TEXT);bestValue.setTypeface(null,Typeface.BOLD);bestBox.addView(bestValue);metrics.addView(bestBox,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout totalBox=new LinearLayout(this);totalBox.setOrientation(LinearLayout.VERTICAL);TextView totalLabel=Ui.text(this,"成功",12,Ui.MUTED);totalBox.addView(totalLabel);totalValue=Ui.text(this,"-",20,Ui.TEXT);totalValue.setTypeface(null,Typeface.BOLD);totalBox.addView(totalValue);metrics.addView(totalBox,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout levelBox=new LinearLayout(this);levelBox.setOrientation(LinearLayout.VERTICAL);TextView levelLabel=Ui.text(this,"成長",12,Ui.MUTED);levelBox.addView(levelLabel);growthLevel=Ui.text(this,"Lv.1",20,Ui.TEXT);growthLevel.setTypeface(null,Typeface.BOLD);levelBox.addView(growthLevel);metrics.addView(levelBox,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(metrics);
        body.addView(Ui.divider(this));

        TextView protectHeader=Ui.sectionHeader(this,"ストリーク保護");protectHeader.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,6));body.addView(protectHeader);'''
if old not in s: raise SystemExit('2.4.0 streak hero block not found')
s=s.replace(old,new,1)

old='''        TextView streakHeader=Ui.sectionHeader(this,"記録");streakHeader.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,4));body.addView(streakHeader);
        bestValue=addStatRow("最高ストリーク");body.addView(Ui.divider(this));totalValue=addStatRow("成功した起床");body.addView(Ui.divider(this));

        TextView calendarHeader=Ui.sectionHeader(this,"カレンダー");calendarHeader.setPadding(0,Ui.dp(this,26),0,Ui.dp(this,8));body.addView(calendarHeader);'''
new='''        TextView calendarHeader=Ui.sectionHeader(this,"カレンダー");calendarHeader.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,8));body.addView(calendarHeader);'''
if old not in s: raise SystemExit('old record section not found')
s=s.replace(old,new,1)

# Make the top unmistakably different: no giant outer card / no giant 390dp companion.
assert 'Ui.dp(this,108),Ui.dp(this,136)' in s
assert 'Ui.dp(this,170),Ui.dp(this,220)' not in s
assert '成長 Lv.' not in s
write(p,s)

# Make stopwatch import state explicit so it is obvious after stopping.
p='src/main/java/jp/wakeguard/alarm/ClockActivity.java'; s=read(p)
old='''        if(stopwatchImport!=null){stopwatchImport.setEnabled(!running&&elapsed>0L);stopwatchImport.setAlpha((!running&&elapsed>0L)?1f:.45f);}
        if(stopwatchLapImport!=null){boolean hasLap=!loadLaps().isEmpty();stopwatchLapImport.setEnabled(hasLap);stopwatchLapImport.setAlpha(hasLap?1f:.45f);}'''
new='''        if(stopwatchImport!=null){boolean canImport=!running&&elapsed>0L;stopwatchImport.setEnabled(canImport);stopwatchImport.setAlpha(canImport?1f:.45f);stopwatchImport.setText(canImport?"停止時間を時間記録へ":"時間記録に追加");}
        if(stopwatchLapImport!=null){boolean hasLap=!loadLaps().isEmpty();stopwatchLapImport.setEnabled(hasLap);stopwatchLapImport.setAlpha(hasLap?1f:.45f);stopwatchLapImport.setText(hasLap?"ラップを時間記録へ":"ラップから追加");}'''
if old not in s: raise SystemExit('stopwatch import state block not found')
s=s.replace(old,new,1)
write(p,s)

assert 'versionName = "2.4.1"' in read('build.gradle.kts')
assert '停止時間を時間記録へ' in read('src/main/java/jp/wakeguard/alarm/ClockActivity.java')
assert 'ラップを時間記録へ' in read('src/main/java/jp/wakeguard/alarm/ClockActivity.java')
print('Android 2.4.1 unmistakable streak redesign applied')
