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
    private final FrameLayout artLayer;
    private final ImageView sprite;
    private final ImageView successorSprite;
    private AnimatedImageDrawable currentAnimation,successorAnimation;
    private int currentTier=-1,successorTier=-1;
    private long permanentLevel=1L;
    private boolean animationEnabled=true;
    private boolean attached=false;
    private boolean growthInitialized=false;

    public StreakCompanionView(Context context) {
        super(context);
        setBackgroundColor(0x00000000);
        setClipChildren(false);
        setClipToPadding(false);
        artLayer=new FrameLayout(context);
        artLayer.setClipChildren(false);
        artLayer.setClipToPadding(false);
        sprite=new ImageView(context);
        sprite.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sprite.setContentDescription("ストリーク成長キャラクター");
        successorSprite=new ImageView(context);
        successorSprite.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        successorSprite.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        artLayer.addView(sprite,new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        artLayer.addView(successorSprite,new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        addView(artLayer,new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
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

    private void stopSuccessorAnimation(){
        if(successorAnimation!=null){successorAnimation.stop();successorAnimation=null;}
    }

    private AnimatedImageDrawable loadStage(ImageView target,int stage){
        final int asset=drawableForTier(stage);
        try{
            ImageDecoder.Source source=ImageDecoder.createSource(getResources(),asset);
            Drawable image=ImageDecoder.decodeDrawable(source);
            target.setImageDrawable(image);
            if(image instanceof AnimatedImageDrawable){
                AnimatedImageDrawable animation=(AnimatedImageDrawable)image;
                animation.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                if(animationEnabled&&attached)animation.start();
                return animation;
            }
            return null;
        }catch(IOException | RuntimeException ex){
            target.setImageResource(asset);
            return null;
        }
    }

    private void chooseStages(int stage,int successor){
        if(stage!=currentTier){
            stopCurrentAnimation();
            currentTier=stage;
            currentAnimation=loadStage(sprite,stage);
        }
        if(successor!=successorTier){
            stopSuccessorAnimation();
            successorTier=successor;
            if(successor<0){successorSprite.setImageDrawable(null);}
            else successorAnimation=loadStage(successorSprite,successor);
        }
    }

    public void setGrowth(long level,int streak){
        long nextLevel=Math.max(1L,level);
        long previousLevel=permanentLevel;
        boolean animateGrowth=growthInitialized&&nextLevel>previousLevel&&nextLevel-previousLevel==1L;
        permanentLevel=nextLevel;
        int stage=StreakGrowth.artStage(permanentLevel);
        int successor=stage<4?stage+1:-1;
        boolean stagePairChanged=stage!=currentTier||successor!=successorTier;
        chooseStages(stage,successor);
        float blend=successor<0?0f:StreakGrowth.artTransitionProgress(permanentLevel);
        sprite.animate().cancel();successorSprite.animate().cancel();
        if(animateGrowth&&!stagePairChanged){
            sprite.animate().alpha(1f-blend).setDuration(520L).setInterpolator(new DecelerateInterpolator()).start();
            successorSprite.animate().alpha(blend).setDuration(520L).setInterpolator(new DecelerateInterpolator()).start();
        }else{
            sprite.setAlpha(1f-blend);
            successorSprite.setAlpha(blend);
        }
        float targetScale=StreakGrowth.visualScale(permanentLevel);
        artLayer.animate().cancel();
        if(animateGrowth){
            float startScale=StreakGrowth.visualScale(previousLevel);
            artLayer.setScaleX(startScale);artLayer.setScaleY(startScale);
            artLayer.animate().scaleX(targetScale).scaleY(targetScale)
                    .setDuration(520L).setInterpolator(new DecelerateInterpolator()).start();
        }else{
            artLayer.setScaleX(targetScale);artLayer.setScaleY(targetScale);
        }
        growthInitialized=true;
        sprite.setContentDescription(StreakGrowth.growthDescriptor(getContext(),permanentLevel)+", Lv."+permanentLevel);
    }

    public void setAnimationEnabled(boolean enabled){
        animationEnabled=enabled;
        setRunning(currentAnimation,enabled&&attached);
        setRunning(successorAnimation,enabled&&attached);
    }

    private void setRunning(AnimatedImageDrawable animation,boolean shouldRun){
        if(animation==null)return;
        if(shouldRun&&!animation.isRunning())animation.start();
        else if(!shouldRun&&animation.isRunning())animation.stop();
    }

    public void onResume(){setAnimationEnabled(true);}
    public void onPause(){setAnimationEnabled(false);}

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        attached=true;
        setRunning(currentAnimation,animationEnabled);
        setRunning(successorAnimation,animationEnabled);
    }

    @Override protected void onDetachedFromWindow(){
        attached=false;
        stopCurrentAnimation();
        stopSuccessorAnimation();
        currentTier=-1;successorTier=-1; // resources will be decoded afresh on next attach
        super.onDetachedFromWindow();
    }
}
