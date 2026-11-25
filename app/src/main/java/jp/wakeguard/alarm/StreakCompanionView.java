package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Keeps each growth step as one solid dragon silhouette. Neighboring forms are
 * morphed as a single image, while the same six-frame wing loop continues at
 * the later stages.
 */
public final class StreakCompanionView extends FrameLayout {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ScheduledThreadPoolExecutor ARTWORK_WORKER = artworkWorker();
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
    private Future<?> pendingArtwork;
    private long artworkGeneration=0L, pendingLevel=-1L, renderedLevel=-1L;
    private long permanentLevel=1L;
    private boolean animationEnabled=true;
    private boolean growthInitialized=false;

    private static ScheduledThreadPoolExecutor artworkWorker(){
        ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1,task->{
            Thread thread=new Thread(()->{
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                task.run();
            },"dragon-artwork");
            thread.setDaemon(true);
            return thread;
        });
        worker.setRemoveOnCancelPolicy(true);
        return worker;
    }

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

    private Drawable stageArtwork(int stage){
        return getResources().getDrawable(drawableForTier(stage),null);
    }

    private Bitmap decodeBitmap(int resource){
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inScaled=false;
        return BitmapFactory.decodeResource(getResources(),resource,options);
    }

    private Drawable loadStaticMorph(int stage,int successor,float progress){
        Bitmap from=decodeBitmap(drawableForTier(stage));
        Bitmap to=decodeBitmap(drawableForTier(successor));
        try{
            if(from==null||to==null)return stageArtwork(stage);
            Bitmap result=DragonMorph.morph(from,to,progress);
            return new BitmapDrawable(getResources(),result);
        }finally{
            if(from!=null)from.recycle();
            if(to!=null)to.recycle();
        }
    }

    private Drawable loadFlappingMorph(int stage,int successor,float progress){
        Bitmap[] frames=new Bitmap[FLAP_FRAMES[0].length];
        Bitmap staticFrom=stage<5?decodeBitmap(drawableForTier(stage)):null;
        boolean complete=false;
        try{
            for(int frame=0;frame<frames.length;frame++){
                if(Thread.currentThread().isInterrupted())throw new CancellationException();
                Bitmap from=stage<5?staticFrom:decodeBitmap(flapFrame(stage,frame));
                Bitmap to=decodeBitmap(flapFrame(successor,frame));
                try{
                    if(from==null||to==null)return stageArtwork(stage);
                    frames[frame]=DragonMorph.morph(from,to,progress);
                }finally{
                    if(stage>=5&&from!=null)from.recycle();
                    if(to!=null)to.recycle();
                }
            }
            AnimationDrawable animation=new AnimationDrawable();
            animation.setOneShot(false);
            for(Bitmap frame:frames)animation.addFrame(new BitmapDrawable(getResources(),frame),180);
            complete=true;
            return animation;
        }finally{
            if(staticFrom!=null)staticFrom.recycle();
            if(!complete)for(Bitmap frame:frames)if(frame!=null)frame.recycle();
        }
    }

    private Drawable loadGrowthDrawable(int stage,float progress){
        if(progress<=0f)return stageArtwork(stage);
        try{
            if(stage<4)return loadStaticMorph(stage,stage+1,progress);
            return loadFlappingMorph(stage,stage+1,progress);
        }catch(IllegalArgumentException incompatibleArtwork){
            return stageArtwork(stage);
        }
    }

    private void cancelPendingArtwork(){
        artworkGeneration++;
        if(pendingArtwork!=null)pendingArtwork.cancel(true);
        pendingArtwork=null;
        pendingLevel=-1L;
    }

    private void showArtwork(Drawable artwork){
        setRunning(currentAnimation,false);
        sprite.setImageDrawable(artwork);
        currentAnimation=artwork instanceof Animatable?(Animatable)artwork:null;
        setRunning(currentAnimation,animationEnabled&&isAttachedToWindow());
    }

    private void requestArtwork(){
        if(!animationEnabled||!isAttachedToWindow())return;

        int stage=StreakGrowth.artStage(permanentLevel);
        long level=stage==6?Long.MAX_VALUE:permanentLevel;
        if(renderedLevel==level){
            if(pendingArtwork!=null)cancelPendingArtwork();
            return;
        }
        if(pendingLevel==level)return;

        cancelPendingArtwork();
        float progress=StreakGrowth.artTransitionProgress(permanentLevel);
        if(progress<=0f&&stage<5){
            showArtwork(stageArtwork(stage));
            renderedLevel=level;
            return;
        }
        if(sprite.getDrawable()==null){
            int still=stage<5?drawableForTier(stage):flapFrame(stage,0);
            sprite.setImageResource(still);
        }

        long generation=artworkGeneration;
        pendingLevel=level;
        pendingArtwork=ARTWORK_WORKER.submit(()->{
            Drawable artwork;
            try{
                artwork=loadGrowthDrawable(stage,progress);
            }catch(CancellationException canceled){
                return;
            }
            if(Thread.currentThread().isInterrupted())return;
            MAIN.post(()->{
                if(generation!=artworkGeneration||!isAttachedToWindow())return;
                pendingArtwork=null;
                pendingLevel=-1L;
                showArtwork(artwork);
                renderedLevel=level;
            });
        });
    }

    public void setGrowth(long level,int streak){
        long nextLevel=Math.max(1L,level);
        long previousLevel=permanentLevel;
        boolean animateGrowth=growthInitialized&&nextLevel>previousLevel&&nextLevel-previousLevel==1L;
        permanentLevel=nextLevel;
        if(!growthInitialized||nextLevel!=previousLevel){
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
        }

        growthInitialized=true;
        sprite.setContentDescription(StreakGrowth.appearanceDescriptor(
                getContext(),permanentLevel,Math.max(0,streak))+", Lv."+permanentLevel);
        requestArtwork();
    }

    public void setAnimationEnabled(boolean enabled){
        animationEnabled=enabled;
        setRunning(currentAnimation,enabled&&isAttachedToWindow());
        if(enabled)requestArtwork();
        else cancelPendingArtwork();
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
        setRunning(currentAnimation,animationEnabled);
        requestArtwork();
    }

    @Override protected void onDetachedFromWindow(){
        cancelPendingArtwork();
        setRunning(currentAnimation,false);
        artLayer.animate().cancel();
        float scale=StreakGrowth.visualScale(permanentLevel);
        artLayer.setScaleX(scale);
        artLayer.setScaleY(scale);
        super.onDetachedFromWindow();
    }
}
