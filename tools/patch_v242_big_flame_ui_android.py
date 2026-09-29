from pathlib import Path
root=Path('WakeGuard/app')
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=s.replace('versionCode = 140','versionCode = 142',1).replace('versionName = "2.4.0"','versionName = "2.4.2"',1)
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
new='''        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(0,0,0,Ui.dp(this,10));
        companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,360)));
        LinearLayout summary=Ui.row(this);summary.setGravity(Gravity.BOTTOM);summary.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,12));
        LinearLayout currentBox=new LinearLayout(this);currentBox.setOrientation(LinearLayout.VERTICAL);
        currentValue=Ui.text(this,"0日",50,Ui.TEXT);currentValue.setTypeface(null,Typeface.BOLD);currentBox.addView(currentValue);
        TextView streakLabel=Ui.text(this,"現在のストリーク",13,Ui.MUTED);currentBox.addView(streakLabel);
        summary.addView(currentBox,new LinearLayout.LayoutParams(0,-2,1));
        growthLevel=Ui.text(this,"Lv.1",21,Ui.TEXT);growthLevel.setTypeface(null,Typeface.BOLD);growthLevel.setGravity(Gravity.END);summary.addView(growthLevel);
        growthForm=Ui.text(this,"",1,Ui.MUTED);growthForm.setVisibility(View.GONE);
        hero.addView(summary);body.addView(hero);
        body.addView(Ui.divider(this));

        LinearLayout metrics=Ui.row(this);metrics.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,8));
        LinearLayout bestBox=new LinearLayout(this);bestBox.setOrientation(LinearLayout.VERTICAL);bestBox.addView(Ui.text(this,"最高",12,Ui.MUTED));bestValue=Ui.text(this,"-",20,Ui.TEXT);bestValue.setTypeface(null,Typeface.BOLD);bestBox.addView(bestValue);metrics.addView(bestBox,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout totalBox=new LinearLayout(this);totalBox.setOrientation(LinearLayout.VERTICAL);totalBox.addView(Ui.text(this,"成功",12,Ui.MUTED));totalValue=Ui.text(this,"-",20,Ui.TEXT);totalValue.setTypeface(null,Typeface.BOLD);totalBox.addView(totalValue);metrics.addView(totalBox,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout protectMetric=new LinearLayout(this);protectMetric.setOrientation(LinearLayout.VERTICAL);protectMetric.addView(Ui.text(this,"保護",12,Ui.MUTED));TextView protectMetricValue=Ui.text(this,"最大3日",20,Ui.TEXT);protectMetricValue.setTypeface(null,Typeface.BOLD);protectMetric.addView(protectMetricValue);metrics.addView(protectMetric,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(metrics);
        body.addView(Ui.divider(this));

        TextView protectHeader=Ui.sectionHeader(this,"ストリーク保護");protectHeader.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,6));body.addView(protectHeader);'''
if old not in s: raise SystemExit('Android 2.4.0 hero block not found')
s=s.replace(old,new,1)
old='''        TextView streakHeader=Ui.sectionHeader(this,"記録");streakHeader.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,4));body.addView(streakHeader);
        bestValue=addStatRow("最高ストリーク");body.addView(Ui.divider(this));totalValue=addStatRow("成功した起床");body.addView(Ui.divider(this));

        TextView calendarHeader=Ui.sectionHeader(this,"カレンダー");calendarHeader.setPadding(0,Ui.dp(this,26),0,Ui.dp(this,8));body.addView(calendarHeader);'''
new='''        TextView calendarHeader=Ui.sectionHeader(this,"カレンダー");calendarHeader.setPadding(0,Ui.dp(this,24),0,Ui.dp(this,8));body.addView(calendarHeader);'''
if old not in s: raise SystemExit('Android record section not found')
s=s.replace(old,new,1)
s=s.replace('growthLevel.setText("Lv."+lv);growthForm.setText(StreakGrowth.growthDescriptor(this));',
            'growthLevel.setText("Lv."+lv);',1)
write(p,s)

assert 'versionName = "2.4.2"' in read('build.gradle.kts')
assert 'Ui.dp(this,360)' in read('src/main/java/jp/wakeguard/alarm/StatsActivity.java')
assert 'Ui.dp(this,170),Ui.dp(this,220)' not in read('src/main/java/jp/wakeguard/alarm/StatsActivity.java')
assert 'growthForm.setVisibility(View.GONE)' in read('src/main/java/jp/wakeguard/alarm/StatsActivity.java')
assert '停止時間を時間記録へ' in read('src/main/java/jp/wakeguard/alarm/ClockActivity.java') or '時間記録に追加' in read('src/main/java/jp/wakeguard/alarm/ClockActivity.java')
assert 'KEY_SW_LAP_WALLS' in read('src/main/java/jp/wakeguard/alarm/ClockActivity.java')
print('Android 2.4.2 big-flame UI applied')
