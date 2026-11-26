package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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
            GrowthAppearance.ADULT_POSES
    };

    // Home and Records normally show the same level. Share pixels, give each view its own animation clock.
    private record CachedLoop(long level, Drawable.ConstantState state) {}
    private static volatile CachedLoop lastLoop;
    private final Matrix portraitMatrix = new Matrix();
    private long displayedLevel = -1L;
    private final ImageView sprite;
    private Animatable currentAnimation;
    private Future<?> pendingArtwork;
    private long artworkGeneration=0L, pendingLevel=-1L, renderedLevel=-1L;
    private long permanentLevel=1L;
    private boolean animationEnabled=true;

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
        sprite=new ImageView(context);
        sprite.setScaleType(ImageView.ScaleType.MATRIX);
        sprite.setContentDescription("ストリーク成長キャラクター");
        addView(sprite,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER));
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
        if (progress > 0f) {
            try {
                switch (stage) {
                    case 3: return loadSittingLoop(progress);
                    case 4: return GrowthAppearance.adultLoop(getResources(),progress);
                    case 5: return GrowthAppearance.stellarLoop(getResources(),progress);
                    default: break;
                }
            } catch (IllegalArgumentException incompatibleArtwork) {
                // The original animated form also works on devices that cannot decode a pose.
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
        fitPortrait(permanentLevel);
        currentAnimation=artwork instanceof Animatable?(Animatable)artwork:null;
        setRunning(currentAnimation,animationEnabled&&isAttachedToWindow());
    }

    private void requestArtwork(){
        if(!animationEnabled||!isAttachedToWindow())return;

        int stage=StreakGrowth.artStage(permanentLevel);
        float progress=StreakGrowth.artTransitionProgress(permanentLevel);
        boolean morphs = stage >= 3 && stage <= 5 && progress > 0f;
        long level = morphs ? permanentLevel : Long.MIN_VALUE + stage;
        if(renderedLevel==level){
            if(pendingArtwork!=null)cancelPendingArtwork();
            if (displayedLevel != permanentLevel) fitPortrait(permanentLevel);
            return;
        }
        if(pendingLevel==level)return;

        cancelPendingArtwork();
        CachedLoop cached = lastLoop;
        if (morphs && cached != null && cached.level() == level) {
            showArtwork(cached.state().newDrawable(getResources()));
            renderedLevel = level;
            return;
        }
        long generation=artworkGeneration;
        pendingLevel=level;
        pendingArtwork=ARTWORK_WORKER.submit(()->{
            Drawable artwork;
            try{
                CachedLoop ready = lastLoop;
                if (morphs && ready != null && ready.level() == level) {
                    artwork = ready.state().newDrawable(getResources());
                } else {
                    artwork = loadGrowthDrawable(stage,progress);
                    Drawable.ConstantState state = artwork.getConstantState();
                    if (morphs && state != null && !Thread.currentThread().isInterrupted()) {
                        lastLoop = new CachedLoop(level, state);
                    }
                }
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
        permanentLevel=nextLevel;
        sprite.setContentDescription(StreakGrowth.appearanceDescriptor(
                getContext(),permanentLevel,Math.max(0,streak))+", Lv."+permanentLevel);
        requestArtwork();
    }

    private void fitPortrait(long level) {
        displayedLevel = level;
        sprite.setColorFilter(GrowthAppearance.forLevel(level));
        updatePortraitMatrix();
    }

    private void updatePortraitMatrix() {
        Drawable artwork = sprite.getDrawable();
        int width = sprite.getWidth(), height = sprite.getHeight();
        if (artwork == null || displayedLevel < 1 || width == 0 || height == 0) return;
        int imageWidth = artwork.getIntrinsicWidth(), imageHeight = artwork.getIntrinsicHeight();
        if (imageWidth <= 0 || imageHeight <= 0) return;
        DragonPortrait.Placement placement = DragonPortrait.place(displayedLevel, width, height, imageWidth, imageHeight);
        portraitMatrix.setScale(placement.scale(), placement.scale());
        portraitMatrix.postTranslate(placement.dx(), placement.dy());
        sprite.setImageMatrix(portraitMatrix);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) updatePortraitMatrix();
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
        super.onDetachedFromWindow();
    }
}
