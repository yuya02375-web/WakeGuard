package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
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
import java.io.IOException;

/**
 * Renders one dragon. Only artwork with the same pose is interpolated;
 * other forms grow within their original silhouette and animation.
 */
public final class StreakCompanionView extends FrameLayout {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ScheduledThreadPoolExecutor ARTWORK_WORKER = artworkWorker();
    // Lossless 64px extracts of the five poses in the original animated WebPs.
    private static final int[][] SITTING_FRAMES = {
            {
                    R.drawable.ignido_stage_hatchling_pose_01,
                    R.drawable.ignido_stage_hatchling_pose_02,
                    R.drawable.ignido_stage_hatchling_pose_03,
                    R.drawable.ignido_stage_hatchling_pose_04,
                    R.drawable.ignido_stage_hatchling_pose_05
            },
            {
                    R.drawable.ignido_stage_adult_pose_01,
                    R.drawable.ignido_stage_adult_pose_02,
                    R.drawable.ignido_stage_adult_pose_03,
                    R.drawable.ignido_stage_adult_pose_04,
                    R.drawable.ignido_stage_adult_pose_05
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

    private Drawable stageArtwork(int stage){
        int resource = drawableForTier(stage);
        if (stage < 5) {
            try {
                // BitmapFactory only supplies the first frame of these WebPs.
                return ImageDecoder.decodeDrawable(ImageDecoder.createSource(getResources(), resource));
            } catch (IOException ignored) {
                // Keep the packaged artwork usable if animated decoding fails.
            }
        }
        return getResources().getDrawable(resource,null);
    }

    private Bitmap decodeBitmap(int resource){
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inScaled=false;
        return BitmapFactory.decodeResource(getResources(),resource,options);
    }

    private Drawable loadSittingLoop(float progress) {
        AnimationDrawable animation = new AnimationDrawable();
        animation.setOneShot(false);
        DragonMorph.Alignment alignment = null;
        for (int frame = 0; frame < SITTING_FRAMES[0].length; frame++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Bitmap from = decodeBitmap(SITTING_FRAMES[0][frame]);
            Bitmap to = decodeBitmap(SITTING_FRAMES[1][frame]);
            try {
                if (from == null || to == null) return stageArtwork(3);
                if (alignment == null) alignment = new DragonMorph.Alignment(from, to, progress);
                BitmapDrawable image = new BitmapDrawable(getResources(), DragonMorph.morph(from, to, alignment));
                image.setFilterBitmap(false);
                animation.addFrame(image,180);
            } finally {
                if (from != null) from.recycle();
                if (to != null) to.recycle();
            }
        }
        return animation;
    }

    private Drawable loadGrowthDrawable(int stage,float progress){
        if (stage == 3 && progress > 0f) {
            try {
                return loadSittingLoop(progress);
            } catch (IllegalArgumentException incompatibleArtwork) {
                return stageArtwork(stage);
            }
        }
        return stageArtwork(stage);
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
        long level=stage==3?permanentLevel:Long.MIN_VALUE+stage;
        if(renderedLevel==level){
            if(pendingArtwork!=null)cancelPendingArtwork();
            return;
        }
        if(pendingLevel==level)return;

        cancelPendingArtwork();
        float progress=StreakGrowth.artTransitionProgress(permanentLevel);
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
            float targetWidth=widthScale(permanentLevel);
            float targetHeight=StreakGrowth.visualScale(permanentLevel);
            artLayer.animate().cancel();
            if(animateGrowth){
                artLayer.setScaleX(widthScale(previousLevel));
                artLayer.setScaleY(StreakGrowth.visualScale(previousLevel));
                artLayer.animate().scaleX(targetWidth).scaleY(targetHeight)
                        .setDuration(520L).setInterpolator(new DecelerateInterpolator()).start();
            }else{
                artLayer.setScaleX(targetWidth);
                artLayer.setScaleY(targetHeight);
            }
        }

        growthInitialized=true;
        sprite.setContentDescription(StreakGrowth.appearanceDescriptor(
                getContext(),permanentLevel,Math.max(0,streak))+", Lv."+permanentLevel);
        requestArtwork();
    }

    private static float widthScale(long level) {
        float growth = StreakGrowth.artStage(level) == 3 ? 0f : StreakGrowth.artTransitionProgress(level);
        return StreakGrowth.visualScale(level) * (1f + .055f * growth);
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
        artLayer.setScaleX(widthScale(permanentLevel));
        artLayer.setScaleY(StreakGrowth.visualScale(permanentLevel));
        super.onDetachedFromWindow();
    }
}
