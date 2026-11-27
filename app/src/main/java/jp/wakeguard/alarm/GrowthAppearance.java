package jp.wakeguard.alarm;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import java.util.concurrent.CancellationException;

/** Daily changes to the original sprite palettes and the two six-pose dragon forms. */
final class GrowthAppearance {
    static final int[] ADULT_POSES = {
            R.drawable.ignido_stage_adult_pose_01, R.drawable.ignido_stage_adult_pose_02,
            R.drawable.ignido_stage_adult_pose_03, R.drawable.ignido_stage_adult_pose_04,
            R.drawable.ignido_stage_adult_pose_05
    };
    private static final int[] STELLAR = {
            R.drawable.ignido_stage_stellar_flap_01, R.drawable.ignido_stage_stellar_flap_02,
            R.drawable.ignido_stage_stellar_flap_03, R.drawable.ignido_stage_stellar_flap_04,
            R.drawable.ignido_stage_stellar_flap_05, R.drawable.ignido_stage_stellar_flap_06
    };
    private static final int[] INFINITE = {
            R.drawable.ignido_stage_infinite_flap_01, R.drawable.ignido_stage_infinite_flap_02,
            R.drawable.ignido_stage_infinite_flap_03, R.drawable.ignido_stage_infinite_flap_04,
            R.drawable.ignido_stage_infinite_flap_05, R.drawable.ignido_stage_infinite_flap_06
    };

    private GrowthAppearance() {}

    static AnimationDrawable adultLoop(Resources resources, float progress) {
        int[][] poses = new int[ADULT_POSES.length][];
        BitmapFactory.Options unscaled = new BitmapFactory.Options();
        unscaled.inScaled = false;
        for (int i = 0; i < poses.length; i++) {
            Bitmap pose = BitmapFactory.decodeResource(resources, ADULT_POSES[i], unscaled);
            if (pose == null) throw new IllegalArgumentException("Missing adult dragon pose");
            poses[i] = readPixelsAndRecycle(pose);
        }
        int[] mature = readPixelsAndRecycle(decodePixelArt(resources, STELLAR[0]));
        developScales(poses, mature, progress);
        AnimationDrawable loop = new AnimationDrawable();
        loop.setOneShot(false);
        for (int[] pose : poses) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Bitmap pixels = Bitmap.createBitmap(pose, 64, 64, Bitmap.Config.ARGB_8888);
            Bitmap enlarged;
            try {
                enlarged = Bitmap.createScaledBitmap(pixels, 384, 384, false);
            } finally {
                pixels.recycle();
            }
            BitmapDrawable frame = new BitmapDrawable(resources, enlarged);
            frame.setFilterBitmap(false);
            loop.addFrame(frame, 180);
        }
        return loop;
    }

    private static int[] readPixelsAndRecycle(Bitmap bitmap) {
        try {
            if (bitmap.getWidth() != 64 || bitmap.getHeight() != 64)
                throw new IllegalArgumentException("Unexpected dragon sprite dimensions");
            int[] pixels = new int[64 * 64];
            bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64);
            return pixels;
        } finally {
            bitmap.recycle();
        }
    }

    /** Transfer the authored ridges of the mature breast and haunch onto the same skin in all poses. */
    static void developScales(int[][] poses, int[] mature, float progress) {
        for (int y = 27; y <= 57; y++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            for (int x = 17; x <= 45; x++) {
                double chest = ellipse(x, y, 38, 35, 6, 10);
                double shoulder = ellipse(x, y, 32, 42, 7, 7);
                double haunch = ellipse(x, y, 27, 50, 9, 8);
                if (Math.min(chest, Math.min(shoulder, haunch)) > 1) continue;
                int index = y * 64 + x;
                // The original has two copper skin pigments. Ignore the face, claws,
                // moving wing, outline, and any point covered by them during a flap.
                boolean alwaysSkin = true;
                for (int[] pose : poses) {
                    int rgb = pose[index] & 0xffffff;
                    if (rgb != 0xd37556 && rgb != 0xa44143) {
                        alwaysSkin = false;
                        break;
                    }
                }
                if (!alwaysSkin) continue;
                int matureX = Math.round(1.05f * x - .3f);
                int matureY = Math.round(.95f * y - 5f);
                int relief = localRelief(mature, matureX, matureY);
                if (relief == 0) continue;
                // Detail comes in first at the breast and runs down onto the haunch.
                float alongBody = (y - 27) / 30f;
                float development = progress + (.8f - 1.6f * alongBody) * progress * (1f - progress);
                float amount = Math.min(.70f, Math.abs(relief) / 85f) * development;
                int shade = relief > 0 ? 0xfffdb762 : 0xff662a33;
                for (int[] pose : poses) pose[index] = mixPigment(pose[index], shade, amount);
            }
        }
    }

    private static double ellipse(int x, int y, int cx, int cy, double rx, double ry) {
        double dx = (x - cx) / rx, dy = (y - cy) / ry;
        return dx * dx + dy * dy;
    }

    private static int localRelief(int[] pixels, int x, int y) {
        int center = pixels[y * 64 + x];
        if ((center >>> 24) < 200) return 0;
        int sum = 0, count = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int pixel = pixels[(y + dy) * 64 + x + dx];
                if ((pixel >>> 24) >= 200) {
                    sum += luminance(pixel);
                    count++;
                }
            }
        }
        return luminance(center) - sum / count;
    }

    private static int luminance(int color) {
        return (((color >> 16) & 255) + 2 * ((color >> 8) & 255) + (color & 255)) / 4;
    }

    private static int mixPigment(int src, int dst, float amount) {
        int r = (src >>> 16) & 255, g = (src >>> 8) & 255, b = src & 255;
        r += Math.round((((dst >>> 16) & 255) - r) * amount);
        g += Math.round((((dst >>> 8) & 255) - g) * amount);
        b += Math.round(((dst & 255) - b) * amount);
        return (src & 0xff000000) | (r << 16) | (g << 8) | b;
    }

    static AnimationDrawable stellarLoop(Resources resources, float progress) {
        AnimationDrawable result = new AnimationDrawable();
        result.setOneShot(false);
        DragonMorph.Alignment registration = null;
        for (int pose = 0; pose < STELLAR.length; pose++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Bitmap from = decodePixelArt(resources, STELLAR[pose]);
            Bitmap to = null;
            try {
                to = decodePixelArt(resources, INFINITE[pose]);
                if (registration == null) registration = new DragonMorph.Alignment(from, to, progress);
                BitmapDrawable frame = new BitmapDrawable(resources, DragonMorph.morph(from, to, registration));
                frame.setFilterBitmap(false);
                result.addFrame(frame, 180);
            } finally {
                from.recycle();
                if (to != null) to.recycle();
            }
        }
        return result;
    }

    private static Bitmap decodePixelArt(Resources resources, int id) {
        // Each original logical pixel occupies an identical 12x12 block in its PNG.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inSampleSize = 4;
        Bitmap sample = BitmapFactory.decodeResource(resources, id, options);
        if (sample == null) throw new IllegalArgumentException("Missing dragon pose");
        Bitmap pixels = Bitmap.createScaledBitmap(sample, 64, 64, false);
        if (pixels != sample) sample.recycle();
        return pixels;
    }

    /** The same palette applies to every pose; the original alpha and timing are untouched. */
    static ColorMatrixColorFilter forLevel(long level) {
        int stage = StreakGrowth.artStage(level);
        float progress = StreakGrowth.artTransitionProgress(level);
        float warmth;
        switch (stage) {
            case 0: warmth = .45f * progress; break;
            case 1: warmth = .45f + .55f * progress; break;
            case 2: warmth = 1f - progress; break;
            case 4: warmth = progress; break;
            case 5: warmth = 1f - progress; break;
            case 6:
                // Keep changing the existing flame and copper scales beyond the final named form.
                double days = (double) level - StreakGrowth.finalFormLevel();
                warmth = (float) (days / (days + 21.0));
                break;
            default: return null;
        }
        if (warmth == 0f) return null;
        // The contour is #261418. Scales have more red than green, whereas
        // the fire and wing-edge gold have much more green than blue.
        // Both pigment measurements are zero on the contour itself.
        float scale = .12f * warmth;
        float fire = .26f * warmth;
        return new ColorMatrixColorFilter(new float[] {
                1f + scale, -scale, 0, 0, -18f * scale,
                0, 1f + fire, -fire, 0, 4f * fire,
                -scale, scale + fire, 1f - fire, 0, 18f * scale + 4f * fire,
                0, 0, 0, 1, 0
        });
    }
}
