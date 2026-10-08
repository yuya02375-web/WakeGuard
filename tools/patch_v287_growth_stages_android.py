from pathlib import Path

root=Path('WakeGuard/app')
def edit(path, func):
    p=root/path
    old=p.read_text()
    new=func(old)
    if new==old:
        raise RuntimeError('Unchanged source: '+path)
    p.write_text(new)
    print('patched',path)

def version(s):
    assert 'versionCode = 186' in s and 'versionName = "2.8.6"' in s
    return s.replace('versionCode = 186','versionCode = 187',1).replace('versionName = "2.8.6"','versionName = "2.8.7"',1)
edit('build.gradle.kts',version)

def growth(s):
    anchor='    /** A compact, non-gamey description of how far the permanent form has grown. */'
    assert anchor in s
    addition='''    /**
     * Appearance follows the current consecutive-day streak, without deleting the
     * separately stored permanent experience level. A 3-day streak never shows
     * an adult dragon, even when older imported stats contain many wake-ups.
     */
    public static long appearanceLevel(long permanentLevel,int currentStreak){
        return Math.min(Math.max(1L,permanentLevel),Math.max(1L,(long)currentStreak));
    }

    public static String appearanceDescriptor(Context c,long permanentLevel,int currentStreak){
        long lv=appearanceLevel(permanentLevel,currentStreak);
        if(lv<4L)return I18n.tr(c,"火種");
        if(lv<12L)return I18n.tr(c,"小さな炎");
        if(lv<30L)return I18n.tr(c,"炎竜の兆し");
        if(lv<80L)return I18n.tr(c,"幼炎竜");
        if(lv<180L)return I18n.tr(c,"炎竜");
        if(lv<400L)return I18n.tr(c,"天炎竜");
        if(lv<1000L)return I18n.tr(c,"恒星炎竜");
        return I18n.tr(c,"無限炎竜");
    }

'''
    return s.replace(anchor,addition+anchor,1)
edit('src/main/java/jp/wakeguard/alarm/StreakGrowth.java',growth)

def companion(s):
    s0=s
    s=s.replace('    private AnimatedImageDrawable animatedDragon;',
                '    private AnimatedImageDrawable animatedDragon;\n    private int appearanceTier=-1;',1)
    s=s.replace('glow.setImageResource(R.drawable.ignido_dragon_v284);',
                'glow.setImageResource(R.drawable.ignido_stage_ember);',1)
    s=s.replace('        loadAnimatedDragon();\n        addView(dragon,',
                '        dragon.setImageResource(R.drawable.ignido_stage_ember);\n        addView(dragon,',1)
    anchor='    public void setGrowth(long lv,int currentStreak){'
    assert anchor in s
    method='''    /** Select dedicated art for each life stage; the adult animation is never
     * decoded or displayed in the ember, flame, egg or hatchling stages. */
    private void showAppearance(long displayLevel){
        int next=displayLevel<4L ? 0 : displayLevel<12L ? 1 :
                 displayLevel<30L ? 2 : displayLevel<80L ? 3 : 4;
        if(next==appearanceTier)return;
        if(animatedDragon!=null && animatedDragon.isRunning())animatedDragon.stop();
        animatedDragon=null;
        appearanceTier=next;
        int res;
        switch(next){
            case 0: res=R.drawable.ignido_stage_ember;break;
            case 1: res=R.drawable.ignido_stage_flame;break;
            case 2: res=R.drawable.ignido_stage_egg;break;
            case 3: res=R.drawable.ignido_stage_hatchling;break;
            default: res=R.drawable.ignido_dragon_v284;break;
        }
        glow.setImageResource(res);
        if(next==4){
            loadAnimatedDragon();
            if(animationEnabled && attached && animatedDragon!=null)animatedDragon.start();
        }else{
            dragon.setImageResource(res);
        }
        dragon.setContentDescription(StreakGrowth.appearanceDescriptor(getContext(),level,streak));
    }

'''
    s=s.replace(anchor,method+anchor,1)
    s=s.replace('        double power=StreakGrowth.visualPower(level);\n        growth=clamp((float)((power-1.0)/9.0));',
                '        long appearanceLevel=StreakGrowth.appearanceLevel(level,streak);\n        showAppearance(appearanceLevel);\n        double power=StreakGrowth.visualPower(appearanceLevel);\n        growth=clamp((float)((power-1.0)/9.0));',1)
    s=s.replace('        float scale=.69f+.35f*growth;',
                '        float scale=appearanceTier<2 ? .98f : appearanceTier<4 ? .90f : .69f+.35f*growth;',1)
    assert s!=s0
    assert 'showAppearance(appearanceLevel);' in s
    return s
edit('src/main/java/jp/wakeguard/alarm/StreakCompanionView.java',companion)

def stats(s):
    old='growthForm.setText(StreakGrowth.growthDescriptor(this));'
    assert old in s
    return s.replace(old,'growthForm.setText(StreakGrowth.appearanceDescriptor(this,lv,streak));',1)
edit('src/main/java/jp/wakeguard/alarm/StatsActivity.java',stats)

p=root/'src/main/res/drawable-nodpi'
for stage in ('ember','flame','egg','hatchling'):
    filename=p/f'ignido_stage_{stage}.webp'
    assert filename.is_file() and filename.stat().st_size>50000,str(filename)
print('v2.8.7: stage resources and growth switch verified')
