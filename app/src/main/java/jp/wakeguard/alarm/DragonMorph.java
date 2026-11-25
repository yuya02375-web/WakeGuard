package jp.wakeguard.alarm;

import android.graphics.Bitmap;
import java.util.concurrent.CancellationException;

/**
 * Creates one continuously evolving dragon image from two neighboring forms.
 * A signed-distance blend moves the silhouette itself, so the next dragon
 * never appears as a translucent second figure behind the current one.
 */
final class DragonMorph {
    private static final float INF = 1.0e6f;
    private static final float DIAGONAL = 1.41421356f;

    private DragonMorph() {}

    static Bitmap morph(Bitmap from, Bitmap to, float fraction) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (from == null || to == null) throw new IllegalArgumentException("Dragon images are required");
        if (from.getWidth() != to.getWidth() || from.getHeight() != to.getHeight()) {
            throw new IllegalArgumentException("Dragon forms must share a canvas");
        }
        float t = Math.max(0f, Math.min(1f, fraction));
        if (t <= 0f) return from.copy(Bitmap.Config.ARGB_8888, false);
        if (t >= 1f) return to.copy(Bitmap.Config.ARGB_8888, false);

        int width = from.getWidth();
        int height = from.getHeight();
        int count = width * height;
        int[] a = new int[count];
        int[] b = new int[count];
        int[] out = new int[count];
        from.getPixels(a, 0, width, 0, 0, width, height);
        to.getPixels(b, 0, width, 0, 0, width, height);

        boolean[] maskA = alphaMask(a);
        boolean[] maskB = alphaMask(b);
        float[] distanceA = signedDistance(maskA, width, height);
        float[] distanceB = signedDistance(maskB, width, height);
        for (int i = 0; i < count; i++) {
            // A blend of two outside distances cannot put this pixel inside the silhouette.
            if (!maskA[i] && !maskB[i]) continue;
            float distance = distanceA[i] + (distanceB[i] - distanceA[i]) * t;
            float edge = smoothStep(distance + 0.5f);
            if (edge <= 0f) continue;

            int color;
            if (maskA[i] && maskB[i]) {
                color = blendRgb(a[i], b[i], t);
            } else if (maskA[i]) {
                color = a[i];
            } else {
                color = b[i];
            }
            int alpha = Math.round(edge * 255f);
            out[i] = (alpha << 24) | (color & 0x00ffffff);
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(out, 0, width, 0, 0, width, height);
        return result;
    }

    private static boolean[] alphaMask(int[] pixels) {
        boolean[] mask = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) mask[i] = (pixels[i] >>> 24) > 16;
        return mask;
    }

    private static float[] signedDistance(boolean[] inside, int width, int height) {
        float[] toOutside = distanceTo(inside, false, width, height);
        float[] toInside = distanceTo(inside, true, width, height);
        for (int i = 0; i < inside.length; i++) {
            toOutside[i] = inside[i] ? toOutside[i] - 0.5f : -(toInside[i] - 0.5f);
        }
        return toOutside;
    }

    private static float[] distanceTo(boolean[] mask, boolean target, int width, int height) {
        float[] distance = new float[mask.length];
        for (int i = 0; i < mask.length; i++) distance[i] = mask[i] == target ? 0f : INF;

        for (int y = 0; y < height; y++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int i = row + x;
                if (x > 0) relax(distance, i, i - 1, 1f);
                if (y > 0) relax(distance, i, i - width, 1f);
                if (x > 0 && y > 0) relax(distance, i, i - width - 1, DIAGONAL);
                if (x + 1 < width && y > 0) relax(distance, i, i - width + 1, DIAGONAL);
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            int row = y * width;
            for (int x = width - 1; x >= 0; x--) {
                int i = row + x;
                if (x + 1 < width) relax(distance, i, i + 1, 1f);
                if (y + 1 < height) relax(distance, i, i + width, 1f);
                if (x + 1 < width && y + 1 < height) relax(distance, i, i + width + 1, DIAGONAL);
                if (x > 0 && y + 1 < height) relax(distance, i, i + width - 1, DIAGONAL);
            }
        }
        return distance;
    }

    private static void relax(float[] distance, int at, int from, float cost) {
        float candidate = distance[from] + cost;
        if (candidate < distance[at]) distance[at] = candidate;
    }

    private static float smoothStep(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }

    private static int blendRgb(int a, int b, float t) {
        int r = Math.round(((a >>> 16) & 255) + (((b >>> 16) & 255) - ((a >>> 16) & 255)) * t);
        int g = Math.round(((a >>> 8) & 255) + (((b >>> 8) & 255) - ((a >>> 8) & 255)) * t);
        int blue = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | blue;
    }
}
