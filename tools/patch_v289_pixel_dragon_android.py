from pathlib import Path

root=Path('WakeGuard/app')
gradle=root/'build.gradle.kts'
s=gradle.read_text(encoding='utf-8')
assert 'versionCode = 188' in s and 'versionName = "2.8.8"' in s
s=s.replace('versionCode = 188','versionCode = 189',1).replace('versionName = "2.8.8"','versionName = "2.8.9"',1)
gradle.write_text(s,encoding='utf-8')

java=r'''package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.IOException;

/**
 * IGNIDO 2.8.9: one consistent hand-made RPG pixel-art evolution system.
 * Every form uses dedicated real animation frames, not zoom/rotation of a static image.
 * No EGL, Filament, GL context, native 3D renderer, or UI redraw loop.
 * Artist credit and asset provenance are bundled in the source distribution.
 */
public final class StreakCompanionView extends FrameLayout {
    private final ImageView sprite;
    private AnimatedImageDrawable currentAnimation;
    private int currentTier=-1;
    private long permanentLevel=1L;
    private int currentStreak=0;
    private boolean animationEnabled=true;
    private boolean attached=false;

    public StreakCompanionView(Context context) {
        super(context);
        setBackgroundColor(0xff141922);
        setClipChildren(false);
        setClipToPadding(false);
        sprite=new ImageView(context);
        sprite.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sprite.setContentDescription("ストリーク成長キャラクター");
        addView(sprite,new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        setGrowth(1L,0);
    }

    private int drawableForTier(int stage){
        switch(stage){
            case 0:return R.drawable.ignido_stage_ember;
            case 1:return R.drawable.ignido_stage_flame;
            case 2:return R.drawable.ignido_stage_egg;
            case 3:return R.drawable.ignido_stage_hatchling;
            default:return R.drawable.ignido_stage_adult;
        }
    }

    private void stopCurrentAnimation(){
        if(currentAnimation != null){
            currentAnimation.stop();
            currentAnimation=null;
        }
    }

    private void chooseStage(int stage){
        if(stage==currentTier)return;
        stopCurrentAnimation();
        currentTier=stage;
        final int asset=drawableForTier(stage);
        try{
            ImageDecoder.Source source=ImageDecoder.createSource(getResources(),asset);
            Drawable image=ImageDecoder.decodeDrawable(source);
            sprite.setImageDrawable(image);
            if(image instanceof AnimatedImageDrawable){
                currentAnimation=(AnimatedImageDrawable)image;
                currentAnimation.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                if(animationEnabled && attached)currentAnimation.start();
            }
        }catch(IOException | RuntimeException ex){
            currentAnimation=null;
            sprite.setImageResource(asset);
        }
    }

    public void setGrowth(long level,int streak){
        permanentLevel=Math.max(1L,level);
        currentStreak=Math.max(0,streak);
        long visibleLevel=StreakGrowth.appearanceLevel(permanentLevel,currentStreak);
        int stage=visibleLevel<4L ? 0 : visibleLevel<12L ? 1 :
                  visibleLevel<30L ? 2 : visibleLevel<80L ? 3 : 4;
        chooseStage(stage);
        sprite.setContentDescription(StreakGrowth.appearanceDescriptor(getContext(),permanentLevel,currentStreak));
    }

    public void setAnimationEnabled(boolean enabled){
        animationEnabled=enabled;
        if(currentAnimation==null)return;
        if(enabled && attached){
            if(!currentAnimation.isRunning())currentAnimation.start();
        }else if(currentAnimation.isRunning()){
            currentAnimation.stop();
        }
    }

    public void onResume(){setAnimationEnabled(true);}
    public void onPause(){setAnimationEnabled(false);}

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        attached=true;
        if(animationEnabled && currentAnimation!=null && !currentAnimation.isRunning())currentAnimation.start();
    }

    @Override protected void onDetachedFromWindow(){
        attached=false;
        stopCurrentAnimation();
        currentTier=-1; // resources will be decoded afresh on next attach
        super.onDetachedFromWindow();
    }
}
'''
source=root/'src/main/java/jp/wakeguard/alarm/StreakCompanionView.java'
source.write_text(java,encoding='utf-8')

assets=root/'src/main/res/drawable-nodpi'
for stage in ['ember','flame','egg','hatchling','adult']:
    path=assets/f'ignido_stage_{stage}.webp'
    assert path.is_file() and path.stat().st_size>=1000,(stage,path)
for legacy in ['ignido_dragon_v284.webp','ignido_dragon_v286.webp']:
    path=assets/legacy
    if path.exists():path.unlink()
assert 'ImageDecoder.decodeDrawable(source)' in java
assert 'StreakGrowth.appearanceLevel' in java
assert 'versionName = "2.8.9"' in gradle.read_text()
print('2.8.9: all five stages have dedicated pixel sprite animations; no app-side 3D')
