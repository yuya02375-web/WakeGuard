package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Keeps each growth step as one solid dragon silhouette. Neighboring forms are
 * morphed as a single image, while the same six-frame wing loop continues at
 * the later stages.
 */
public final class StreakCompanionView extends FrameLayout {
    private static final int[][] FLAP_FRAMES = {
            {
                    R.drawable.ignido_stage_stellar_flap_01,
                    R.drawable.ignido_stage_stellar_flap_02,
                    R.drawable.ignido_stage_stellar_flap_03,
                    R.drawable.ignido_stage_stellar_flap_04,
                    R.drawable.ignido_stage_stellar_flap_05,
                    R.drawable.ignido_stage_stellar_flap_06
            },
            {
                    R.drawable.ignido_stage_infinite_flap_01,
                    R.drawable.ignido_stage_infinite_flap_02,
                    R.drawable.ignido_stage_infinite_flap_03,
                    R.drawable.ignido_stage_infinite_flap_04,
                    R.drawable.ignido_stage_infinite_flap_05,
                    R.drawable.ignido_stage_infinite_flap_06
            }
    };

    private final FrameLayout artLayer;
    private final ImageView sprite;
    private Animatable currentAnimation;
    private int currentTier=-1,successorTier=-1;
    private long permanentLevel=1L,renderedLevel=-1L;
    private int currentStreak=0;
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
        artLayer.addView(sprite,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER));
        addView(artLayer,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER));
        setGrowth(1L,0);
    }

    private int drawableForTier(int stage){
        switch(stage){
            case 0:return R.drawable.ignido_stage_egg;
            case 1:return R.drawable.ignido_stage_ember;
            case 2:return R.drawable.ignido_stage_flame;
            case 3:return R.drawable.ignido_stage_hatchling;
            case 4:return R.drawable.ignido_stage_adult;
            case 5:return R.drawable.ignido_stage_stellar_idle;
            default:return R.drawable.ignido_stage_infinite_idle;
        }
    }

    private int flapFrame(int stage,int frame){
        return FLAP_FRAMES[stage==5?0:1][frame];
    }

    private void stopCurrentAnimation(){
        if(currentAnimation!=null){currentAnimation.stop();currentAnimation=null;}
    }

    private Bitmap decodeBitmap(int resource){
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inScaled=false;
        return BitmapFactory.decodeResource(getResources(),resource,options);
    }

    private Drawable loadStageDrawable(int stage){
        Drawable image=getContext().getDrawable(drawableForTier(stage));
        if(image instanceof Animatable){
            Animatable animation=(Animatable)image;
            if(animationEnabled&&attached)animation.start();
            currentAnimation=animation;
        }
        return image;
    }

    private Drawable loadStaticMorph(int stage,int successor,float progress){
        Bitmap from=decodeBitmap(drawableForTier(stage));
        Bitmap to=decodeBitmap(drawableForTier(successor));
        if(from==null||to==null){
            if(from!=null)from.recycle();
            if(to!=null)to.recycle();
            return loadStageDrawable(stage);
        }
        try{
            Bitmap result=DragonMorph.morph(from,to,progress);
            return new BitmapDrawable(getResources(),result);
        }finally{
            from.recycle();
            to.recycle();
        }
    }

    private Drawable loadFlappingMorph(int stage,int successor,float progress){
        AnimationDrawable animation=new AnimationDrawable();
        animation.setOneShot(false);
        Bitmap staticFrom=stage<5?decodeBitmap(drawableForTier(stage)):null;
        if(stage<5&&staticFrom==null)return loadStageDrawable(stage);
        for(int frame=0;frame<6;frame++){
            Bitmap from=stage<5?staticFrom:decodeBitmap(flapFrame(stage,frame));
            Bitmap to=decodeBitmap(flapFrame(successor,frame));
            if(from==null||to==null){
                if(stage>=5&&from!=null)from.recycle();
                if(to!=null)to.recycle();
                if(staticFrom!=null)staticFrom.recycle();
                return loadStageDrawable(stage);
            }
            Bitmap result;
            try{
                result=DragonMorph.morph(from,to,progress);
            }finally{
                if(stage>=5)from.recycle();
                to.recycle();
            }
            animation.addFrame(new BitmapDrawable(getResources(),result),180);
        }
        if(staticFrom!=null)staticFrom.recycle();
        return animation;
    }

    private Drawable loadGrowthDrawable(int stage,int successor,float progress){
        if(successor<0)return loadStageDrawable(stage);
        if(stage<4)return loadStaticMorph(stage,successor,progress);
        return loadFlappingMorph(stage,successor,progress);
    }

    public void setGrowth(long level,int streak){
        long nextLevel=Math.max(1L,level);
        long previousLevel=permanentLevel;
        boolean animateGrowth=growthInitialized&&nextLevel>previousLevel&&nextLevel-previousLevel==1L;
        permanentLevel=nextLevel;
        currentStreak=Math.max(0,streak);

        int stage=StreakGrowth.artStage(permanentLevel);
        int successor=stage<6?stage+1:-1;
        boolean needsNewArtwork=renderedLevel!=permanentLevel||stage!=currentTier
                ||successor!=successorTier;
        if(needsNewArtwork){
            stopCurrentAnimation();
            float blend=successor<0?0f:StreakGrowth.artTransitionProgress(permanentLevel);
            sprite.setImageDrawable(loadGrowthDrawable(stage,successor,blend));
            if(currentAnimation!=null&&animationEnabled&&attached&&!currentAnimation.isRunning()){
                currentAnimation.start();
            }

            float targetScale=StreakGrowth.visualScale(permanentLevel);
            artLayer.animate().cancel();
            if(animateGrowth){
                float startScale=StreakGrowth.visualScale(previousLevel);
                artLayer.setScaleX(startScale);
                artLayer.setScaleY(startScale);
                artLayer.animate().scaleX(targetScale).scaleY(targetScale)
                        .setDuration(520L).setInterpolator(new DecelerateInterpolator()).start();
            }else{
                artLayer.setScaleX(targetScale);
                artLayer.setScaleY(targetScale);
            }
            currentTier=stage;
            successorTier=successor;
            renderedLevel=permanentLevel;
        }

        growthInitialized=true;
        sprite.setContentDescription(StreakGrowth.appearanceDescriptor(
                getContext(),permanentLevel,currentStreak)+", Lv."+permanentLevel);
    }

    public void setAnimationEnabled(boolean enabled){
        animationEnabled=enabled;
        setRunning(currentAnimation,enabled&&attached);
    }

    private void setRunning(Animatable animation,boolean shouldRun){
        if(animation==null)return;
        if(shouldRun&&!animation.isRunning())animation.start();
        else if(!shouldRun&&animation.isRunning())animation.stop();
    }

    public void onResume(){setAnimationEnabled(true);}
    public void onPause(){setAnimationEnabled(false);}

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        attached=true;
        if(currentTier<0)setGrowth(permanentLevel,currentStreak);
        setRunning(currentAnimation,animationEnabled);
    }

    @Override protected void onDetachedFromWindow(){
        attached=false;
        stopCurrentAnimation();
        currentTier=-1;
        successorTier=-1;
        super.onDetachedFromWindow();
    }
}
