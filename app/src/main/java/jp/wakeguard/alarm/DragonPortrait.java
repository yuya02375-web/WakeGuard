package jp.wakeguard.alarm;

/** Positions the entire idle loop using its opaque envelope on the original 64-cell canvas. */
final class DragonPortrait {
    // left, top, right, bottom, including the furthest tips of the moving wings and tail.
    private static final int[][] ENVELOPES = {
            {12, 18, 49, 64}, {16, 28, 44, 63}, {15, 19, 49, 64}, {18, 26, 46, 55},
            {0, 4, 49, 64}, {3, 1, 59, 59}, {1, 1, 63, 63}
    };

    record Placement(float scale, float dx, float dy) {}

    static Placement place(long level, float viewWidth, float viewHeight, float imageWidth, float imageHeight) {
        int stage = StreakGrowth.artStage(level);
        int[] start = ENVELOPES[stage];
        boolean morphs = stage == 3 || stage == 5;
        int[] end = morphs ? ENVELOPES[stage + 1] : start;
        float t = morphs ? StreakGrowth.artTransitionProgress(level) : 0f;
        float left = interpolate(start[0], end[0], t) * imageWidth / 64f;
        float top = interpolate(start[1], end[1], t) * imageHeight / 64f;
        float right = interpolate(start[2], end[2], t) * imageWidth / 64f;
        float bottom = interpolate(start[3], end[3], t) * imageHeight / 64f;
        float occupancy = StreakGrowth.visualScale(level);
        float scale = occupancy * Math.min(viewWidth / (right - left), viewHeight / (bottom - top));
        return new Placement(scale, (viewWidth - (left + right) * scale) * .5f,
                (viewHeight - (top + bottom) * scale) * .5f);
    }

    private static float interpolate(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private DragonPortrait() {}
}
