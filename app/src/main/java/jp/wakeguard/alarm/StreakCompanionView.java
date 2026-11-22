package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
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
    private boolean growthInitialized=false;

    public StreakCompanionView(Context context) {
        super(context);
        setBackgroundColor(0x00000000);
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
            case 0:return R.drawable.ignido_stage_egg;
            case 1:return R.drawable.ignido_stage_ember;
            case 2:return R.drawable.ignido_stage_flame;
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
        long nextLevel=Math.max(1L,level);
        long previousLevel=permanentLevel;
        boolean animateGrowth=growthInitialized&&nextLevel>previousLevel&&nextLevel-previousLevel==1L;
        permanentLevel=nextLevel;
        currentStreak=Math.max(0,streak);
        int stage=StreakGrowth.artStage(permanentLevel);
        chooseStage(stage);
        float targetScale=StreakGrowth.visualScale(permanentLevel);
        sprite.animate().cancel();
        if(animateGrowth){
            float startScale=StreakGrowth.visualScale(previousLevel);
            sprite.setScaleX(startScale);sprite.setScaleY(startScale);
            sprite.animate().scaleX(targetScale).scaleY(targetScale)
                    .setDuration(520L).setInterpolator(new DecelerateInterpolator()).start();
        }else{
            sprite.setScaleX(targetScale);sprite.setScaleY(targetScale);
        }
        growthInitialized=true;
        sprite.setContentDescription(StreakGrowth.growthDescriptor(getContext(),permanentLevel)+", Lv."+permanentLevel);
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
