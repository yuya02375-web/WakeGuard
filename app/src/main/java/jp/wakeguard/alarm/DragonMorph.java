package jp.wakeguard.alarm;

import android.graphics.Bitmap;
import java.util.concurrent.CancellationException;

/**
 * Aligns the two sitting dragon illustrations before interpolating their outline.
 * Both have the same pose, but their head, wing and feet occupy different areas
 * of the source canvas. Do not use this for unrelated silhouettes or animation poses.
 */
final class DragonMorph {
    private static final float INF = 1.0e6f;
    private static final float DIAGONAL = 1.41421356f;
    private static final int SIZE = 384;

    private DragonMorph() {}

    static Bitmap morph(Bitmap from, Bitmap to, Alignment alignment) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        int width = SIZE;
        int height = SIZE;
        int count = width * height;
        int[] out = new int[count];
        float t = alignment.progress;
        int[] a = register(from, alignment.from, alignment.target);
        int[] b = register(to, alignment.to, alignment.target);

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
            int alpha = Math.round(edge * (color >>> 24));
            out[i] = (alpha << 24) | (color & 0x00ffffff);
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(out, 0, width, 0, 0, width, height);
        return result;
    }

    static final class Alignment {
        private final Bounds from, to, target;
        private final float progress;

        Alignment(Bitmap fromRest, Bitmap toRest, float progress) {
            int width = fromRest.getWidth(), height = fromRest.getHeight();
            if (width != toRest.getWidth() || height != toRest.getHeight()) {
                throw new IllegalArgumentException("Dragon forms must share a canvas");
            }
            this.progress = Math.max(0f, Math.min(1f, progress));
            from = opaqueBounds(fromRest);
            to = opaqueBounds(toRest);
            float sx = SIZE / (float) width;
            float sy = SIZE / (float) height;
            // Every pose shares the rest frame's registration. Using each pose's
            // outermost wing pixel as an anchor would make the torso wobble.
            target = new Bounds(
                    Math.round(sx * (from.left + (to.left - from.left) * this.progress)),
                    Math.round(sy * (from.top + (to.top - from.top) * this.progress)),
                    Math.round(sx * (from.right + (to.right - from.right) * this.progress)),
                    Math.round(sy * (from.bottom + (to.bottom - from.bottom) * this.progress)));
        }
    }

    private static Bounds opaqueBounds(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int left = width, top = height, right = 0, bottom = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) <= 16) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x + 1);
                bottom = Math.max(bottom, y + 1);
            }
        }
        if (right <= left || bottom <= top) {
            throw new IllegalArgumentException("Dragon forms must contain visible artwork");
        }
        return new Bounds(left, top, right, bottom);
    }

    private static int[] register(Bitmap bitmap, Bounds source, Bounds target) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        int[] result = new int[SIZE * SIZE];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        float xScale = source.width() / (float) target.width();
        float yScale = source.height() / (float) target.height();
        for (int y = 0; y < SIZE; y++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            int sourceY = source.top + (int) Math.floor((y - target.top + .5f) * yScale);
            if (sourceY < 0 || sourceY >= height) continue;
            int sourceRow = sourceY * width;
            for (int x = 0; x < SIZE; x++) {
                int sourceX = source.left + (int) Math.floor((x - target.left + .5f) * xScale);
                if (sourceX >= 0 && sourceX < width) {
                    result[y * SIZE + x] = pixels[sourceRow + sourceX];
                }
            }
        }
        return result;
    }

    private static int mix(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    private record Bounds(int left, int top, int right, int bottom) {
        int width() { return right - left; }
        int height() { return bottom - top; }
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
        int alpha = mix(a >>> 24, b >>> 24, t);
        int r = mix((a >>> 16) & 255, (b >>> 16) & 255, t);
        int g = mix((a >>> 8) & 255, (b >>> 8) & 255, t);
        int blue = mix(a & 255, b & 255, t);
        return (alpha << 24) | (r << 16) | (g << 8) | blue;
    }
}
