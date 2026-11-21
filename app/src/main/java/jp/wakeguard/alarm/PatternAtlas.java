package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Tactile pattern swatches used as a subtle, theme-independent layer beneath the accent linework. */
final class PatternAtlas {
    private static final Bitmap[] atlases = new Bitmap[3];

    private PatternAtlas() {}

    static boolean draw(Context context, Canvas canvas, String pattern, String designId,
                        RectF bounds, int sourceAlpha) {
        int tile = tileFor(pattern, designId);
        if (tile < 0 || context == null || canvas == null || bounds == null || bounds.isEmpty()) return false;
        int atlasIndex = tile / 16;
        int localTile = tile % 16;
        Bitmap bitmap = load(context, atlasIndex);
        if (bitmap == null || bitmap.isRecycled()) return false;

        int column = localTile % 4, row = localTile / 4;
        float cellWidth = bitmap.getWidth() / 4f, cellHeight = bitmap.getHeight() / 4f;
        int insetX = Math.max(3, Math.round(cellWidth * .018f));
        int insetY = Math.max(3, Math.round(cellHeight * .018f));
        float sourceLeft = Math.round(column * cellWidth) + insetX;
        float sourceTop = Math.round(row * cellHeight) + insetY;
        float sourceRight = Math.round((column + 1) * cellWidth) - insetX;
        float sourceBottom = Math.round((row + 1) * cellHeight) - insetY;
        float sourceWidth = sourceRight - sourceLeft, sourceHeight = sourceBottom - sourceTop;
        float sourceAspect = sourceWidth / sourceHeight, targetAspect = bounds.width() / bounds.height();
        if (sourceAspect > targetAspect) {
            float croppedWidth = sourceHeight * targetAspect;
            sourceLeft += (sourceWidth - croppedWidth) * .5f;
            sourceRight = sourceLeft + croppedWidth;
        } else {
            float croppedHeight = sourceWidth / targetAspect;
            sourceTop += (sourceHeight - croppedHeight) * .5f;
            sourceBottom = sourceTop + croppedHeight;
        }
        Rect src = new Rect(Math.round(sourceLeft), Math.round(sourceTop),
                Math.round(sourceRight), Math.round(sourceBottom));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        // Keep the motif restrained on live screens, while letting selection cards show
        // the artwork at full strength. Picker previews pass an opaque source color.
        paint.setAlpha(sourceAlpha >= 192 ? 220
                : sourceAlpha <= 0 ? 0 : Math.max(24, Math.min(112, sourceAlpha)));
        int save = canvas.save();
        canvas.clipRect(bounds);
        canvas.drawBitmap(bitmap, src, bounds, paint);
        canvas.restoreToCount(save);
        return true;
    }

    private static Bitmap load(Context context, int atlasIndex) {
        if (atlasIndex < 0 || atlasIndex >= atlases.length) return null;
        Bitmap cached = atlases[atlasIndex];
        if (cached != null && !cached.isRecycled()) return cached;
        synchronized (PatternAtlas.class) {
            cached = atlases[atlasIndex];
            if (cached == null || cached.isRecycled()) {
                int resource = atlasIndex == 0 ? R.drawable.wakeguard_pattern_atlas
                        : atlasIndex == 1 ? R.drawable.wakeguard_pattern_atlas_02
                        : R.drawable.wakeguard_pattern_atlas_03;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inScaled = false;
                atlases[atlasIndex] = cached = BitmapFactory.decodeResource(
                        context.getApplicationContext().getResources(), resource, options);
            }
        }
        return cached;
    }

    private static int tileFor(String pattern, String id) {
        if (id != null) {
            switch (id) {
                case "paper-craft": return 0;
                case "origami-atlas": return 1;
                case "field-notes": return 2;
                case "ink-etching": return 8;
                case "linen-stitch": return 4;
                case "tapestry-weave": return 34;
                case "herbarium": return 5;
                case "petal-orbit": return 6;
                case "moonlit-stars": return 7;
                case "aurora-contour": return 16;
                case "topographic-map": return 17;
                case "ocean-current": return 9;
                case "tidepool": return 18;
                case "terrazzo": return 14;
                case "halftone-pop": return 10;
                case "magazine-cover": return 24;
                case "scanline-vhs": return 11;
                case "vapor-grid": return 12;
                case "cassette-deck": return 13;
                case "blueprint": return 3;
                case "circuit-trace": return 20;
                case "transit-barcode": return 33;
                case "swiss-poster": return 25;
                case "editorial-ink": return 23;
                case "newspaper-archive": return 27;
                case "terminal-green": return 21;
                case "ceramic-glaze": return 15;
                case "library-classic": return 26;
                case "checker-club": return 32;
                case "concrete-brutal": return 28;
                case "bubblegum-soft": return 29;
                case "geometric-tiles": return 30;
                case "memphis-confetti": return 31;
                case "pixel-arcade": return 22;
                case "space-console": return 19;
                case "solar-orbit": return 35;
            }
        }
        if (pattern == null) return -1;
        switch (pattern) {
            case "rules": return 0;
            case "diagonal": return 1;
            case "dots": return 2;
            case "grid": return 3;
            case "stitch": return 4;
            case "botanical": return 5;
            case "contour": return 8;
            case "waves": return 9;
            case "halftone": return 10;
            case "scanline": return 11;
            case "film": return 13;
            case "mosaic": return 14;
            case "radial": return 7;
            case "circuit": return 3;
            default: return -1;
        }
    }
}
