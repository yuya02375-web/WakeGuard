package jp.wakeguard.alarm;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Displays the same seven dragon portraits as the standalone REP LIFT app. */
public final class StreakCompanionView extends FrameLayout {
    private static final int[] ARTWORK = {
            R.drawable.ignido_burst_egg, R.drawable.ignido_burst_ember,
            R.drawable.ignido_burst_flame, R.drawable.ignido_burst_hatchling,
            R.drawable.ignido_burst_adult, R.drawable.ignido_burst_stellar,
            R.drawable.ignido_burst_infinite
    };

    private final ImageView portrait;
    private int displayedStage = -1;
    private boolean animationEnabled = true;
    private boolean entrancePending;

    public StreakCompanionView(Context context) {
        super(context);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(0xff1c0a11);
        frame.setCornerRadius(dp(12));
        frame.setStroke(dp(1), 0xff773c42);
        setBackground(frame);
        setClipToOutline(true);

        portrait = new ImageView(context);
        portrait.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(portrait, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        setGrowth(1L, 0);
    }

    public void setGrowth(long level, int streak) {
        long validLevel = Math.max(1L, level);
        int stage = StreakGrowth.artStage(validLevel);
        portrait.setContentDescription(StreakGrowth.appearanceDescriptor(
                getContext(), validLevel, Math.max(0, streak)) + ", Lv." + validLevel);
        if (displayedStage != stage) {
            displayedStage = stage;
            portrait.setImageResource(ARTWORK[stage]);
            entrancePending = true;
            playEntrance();
        }
    }

    public void setAnimationEnabled(boolean enabled) {
        animationEnabled = enabled;
        if (enabled) playEntrance();
        else settlePortrait();
    }

    public void onResume() { setAnimationEnabled(true); }
    public void onPause() { setAnimationEnabled(false); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        playEntrance();
    }

    @Override protected void onDetachedFromWindow() {
        settlePortrait();
        super.onDetachedFromWindow();
    }

    private void playEntrance() {
        if (!entrancePending || !animationEnabled || !isAttachedToWindow()) return;
        entrancePending = false;
        settlePortrait();
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        portrait.setAlpha(0.7f);
        portrait.setScaleX(0.95f);
        portrait.setScaleY(0.95f);
        portrait.setTranslationY(dp(6));
        portrait.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(320).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void settlePortrait() {
        portrait.animate().cancel();
        portrait.setAlpha(1f);
        portrait.setScaleX(1f);
        portrait.setScaleY(1f);
        portrait.setTranslationY(0f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
