package jp.wakeguard.alarm;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Small, palette-driven landscape illustrations for design previews and screen accents.
 *
 * <p>This class owns no theme state and loads no assets. Callers provide all seven colors,
 * which keeps these scenes compatible with independently selectable color palettes.</p>
 */
public final class LandscapeArt {
    public static final String MOUNTAIN_RIDGE = "mountain-ridge";
    public static final String FOREST = "forest";
    public static final String VOLCANIC_NIGHT = "volcanic-night";
    public static final String DUNES = "dunes";
    public static final String CLOUD_SEA = "cloud-sea";

    private LandscapeArt() {}

    /** Colors are used semantically; alpha is adjusted only for translucent atmospheric details. */
    public static final class Palette {
        public final int skyTop;
        public final int skyBottom;
        public final int far;
        public final int middle;
        public final int foreground;
        public final int accent;
        public final int highlight;

        public Palette(int skyTop, int skyBottom, int far, int middle,
                       int foreground, int accent, int highlight) {
            this.skyTop = skyTop;
            this.skyBottom = skyBottom;
            this.far = far;
            this.middle = middle;
            this.foreground = foreground;
            this.accent = accent;
            this.highlight = highlight;
        }
    }

    /** Builds scene layers from an app theme while retaining its selected accent hue. */
    public static Palette paletteFromTheme(int bg, int bg2, int surface, int accent,
                                           int accent2, int text, int highlight) {
        return new Palette(
                mixColor(bg, accent, .10f),
                mixColor(bg2, accent2, .08f),
                mixColor(bg2, accent2, .34f),
                mixColor(surface, accent2, .24f),
                mixColor(surface, text, .14f),
                accent,
                highlight);
    }

    private static int mixColor(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.argb(
                Math.round(Color.alpha(from) * (1f - t) + Color.alpha(to) * t),
                Math.round(Color.red(from) * (1f - t) + Color.red(to) * t),
                Math.round(Color.green(from) * (1f - t) + Color.green(to) * t),
                Math.round(Color.blue(from) * (1f - t) + Color.blue(to) * t));
    }

    /** Draws one supported scene, clipped to {@code bounds}; returns false for invalid input/ID. */
    public static boolean draw(Canvas canvas, String id, RectF bounds, Palette palette, float density) {
        if (canvas == null || bounds == null || palette == null || id == null
                || bounds.width() < 1f || bounds.height() < 1f) return false;
        if (!isSupported(id)) return false;

        int save = canvas.save();
        try {
            canvas.clipRect(bounds);
            Painter p = new Painter(canvas, bounds, palette, density);
            if (MOUNTAIN_RIDGE.equals(id)) p.mountainRidge();
            else if (FOREST.equals(id)) p.forest();
            else if (VOLCANIC_NIGHT.equals(id)) p.volcanicNight();
            else if (DUNES.equals(id)) p.dunes();
            else p.cloudSea();
            return true;
        } finally {
            canvas.restoreToCount(save);
        }
    }

    public static boolean isSupported(String id) {
        return MOUNTAIN_RIDGE.equals(id) || FOREST.equals(id) || VOLCANIC_NIGHT.equals(id)
                || DUNES.equals(id) || CLOUD_SEA.equals(id);
    }

    private static final class Painter {
        private final Canvas canvas;
        private final RectF b;
        private final Palette c;
        private final float w, h, unit, density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Painter(Canvas canvas, RectF bounds, Palette palette, float density) {
            this.canvas = canvas;
            this.b = new RectF(bounds);
            this.c = palette;
            this.w = bounds.width();
            this.h = bounds.height();
            this.unit = Math.min(w, h);
            this.density = Float.isNaN(density) || Float.isInfinite(density) || density <= 0f
                    ? 1f : density;
        }

        private float x(float n) { return b.left + w * n; }
        private float y(float n) { return b.top + h * n; }
        private float r(float n) { return unit * n; }
        private float sw(float n) { return Math.max(.65f * density, unit * n); }

        private void fill(int color) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
        }

        private void stroke(int color, float weight) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(sw(weight));
            paint.setColor(color);
        }

        private int alpha(int color, int a) {
            int sourceAlpha = Color.alpha(color);
            int combinedAlpha = sourceAlpha * Math.max(0, Math.min(255, a)) / 255;
            return Color.argb(combinedAlpha, Color.red(color),
                    Color.green(color), Color.blue(color));
        }

        private void sky() {
            fill(Color.WHITE);
            paint.setShader(new LinearGradient(b.left, b.top, b.left, b.bottom,
                    c.skyTop, c.skyBottom, Shader.TileMode.CLAMP));
            canvas.drawRect(b, paint);
            paint.setShader(null);
        }

        private void glow(float cx, float cy, float radius, int color, int maxAlpha) {
            fill(Color.WHITE);
            paint.setShader(new RadialGradient(x(cx), y(cy), r(radius),
                    new int[]{alpha(color, maxAlpha), alpha(color, 0)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(x(cx), y(cy), r(radius), paint);
            paint.setShader(null);
        }

        private void disk(float cx, float cy, float radius, int color) {
            fill(color);
            canvas.drawCircle(x(cx), y(cy), r(radius), paint);
        }

        private void polygon(int color, float... points) {
            if (points.length < 6 || (points.length & 1) != 0) return;
            Path path = new Path();
            path.moveTo(x(points[0]), y(points[1]));
            for (int i = 2; i < points.length; i += 2) path.lineTo(x(points[i]), y(points[i + 1]));
            path.close();
            fill(color);
            canvas.drawPath(path, paint);
        }

        private Path curve(float... points) {
            Path path = new Path();
            if (points.length < 4 || (points.length & 1) != 0) return path;
            path.moveTo(x(points[0]), y(points[1]));
            int count = points.length / 2;
            for (int i = 0; i < count - 1; i++) {
                int p0 = Math.max(0, i - 1) * 2;
                int p1 = i * 2;
                int p2 = (i + 1) * 2;
                int p3 = Math.min(count - 1, i + 2) * 2;
                float c1x = points[p1] + (points[p2] - points[p0]) / 6f;
                float c1y = points[p1 + 1] + (points[p2 + 1] - points[p0 + 1]) / 6f;
                float c2x = points[p2] - (points[p3] - points[p1]) / 6f;
                float c2y = points[p2 + 1] - (points[p3 + 1] - points[p1 + 1]) / 6f;
                path.cubicTo(x(c1x), y(c1y), x(c2x), y(c2y), x(points[p2]), y(points[p2 + 1]));
            }
            return path;
        }

        private void linePath(int color, float weight, float... points) {
            stroke(color, weight);
            canvas.drawPath(curve(points), paint);
        }

        private void mountainRidge() {
            sky();
            glow(.77f, .27f, .28f, c.accent, 80);
            disk(.77f, .27f, .075f, c.highlight);

            // High, angular peaks establish a clear alpine silhouette even in a tiny sample.
            polygon(c.far,
                    0f,.66f, .12f,.52f, .20f,.57f, .37f,.27f, .48f,.52f,
                    .58f,.39f, .72f,.61f, .86f,.43f, 1f,.62f, 1f,1f, 0f,1f);
            polygon(alpha(c.highlight, 225),
                    .305f,.387f, .37f,.27f, .425f,.391f, .382f,.367f, .358f,.405f);
            polygon(alpha(c.highlight, 195),
                    .815f,.493f, .86f,.43f, .904f,.495f, .869f,.478f, .851f,.509f);

            polygon(c.middle,
                    0f,.73f, .16f,.62f, .28f,.68f, .45f,.48f, .59f,.69f,
                    .77f,.55f, 1f,.72f, 1f,1f, 0f,1f);
            // A narrow switchback trail adds a human scale to the scene.
            linePath(alpha(c.highlight, 210), .004f,
                    .43f,.91f, .49f,.84f, .45f,.80f, .51f,.74f,
                    .48f,.70f, .55f,.63f, .53f,.59f);
            polygon(c.foreground, 0f,.86f, .22f,.79f, .42f,.88f, .64f,.78f,
                    .82f,.86f, 1f,.77f, 1f,1f, 0f,1f);
            linePath(alpha(c.accent, 170), .003f, .05f,.90f, .20f,.85f, .34f,.89f);
        }

        private void forest() {
            sky();
            glow(.72f,.24f,.33f,c.highlight,68);
            disk(.72f,.24f,.055f,c.highlight);

            polygon(c.far, 0f,.66f, .13f,.51f, .25f,.67f, .39f,.45f,
                    .55f,.68f, .73f,.49f, .88f,.67f, 1f,.53f, 1f,1f, 0f,1f);
            // Three staggered pine rows create depth without overwhelming preview text.
            pineRow(c.middle, .63f, new float[]{.04f,.18f,.34f,.52f,.69f,.86f,1.02f}, .18f);
            fill(alpha(c.highlight, 65));
            canvas.drawRect(b.left, y(.69f), b.right, y(.75f), paint);
            pineRow(c.foreground, .78f, new float[]{-.06f,.13f,.32f,.51f,.70f,.89f,1.08f}, .26f);
            // Foreground trunks and small clearings give the forest a readable base line.
            fill(alpha(c.foreground, 220));
            canvas.drawRect(x(.10f), y(.86f), x(.115f), y(.98f), paint);
            canvas.drawRect(x(.42f), y(.84f), x(.44f), y(1f), paint);
            canvas.drawRect(x(.80f), y(.86f), x(.82f), y(1f), paint);
            linePath(alpha(c.highlight, 150), .003f,
                    0f,.92f, .20f,.89f, .39f,.92f, .61f,.89f, .82f,.92f, 1f,.89f);
        }

        private void pineRow(int color, float base, float[] centers, float height) {
            for (int i = 0; i < centers.length; i++) {
                float cx = centers[i];
                float scale = (i % 3 == 0) ? .84f : (i % 3 == 1 ? 1f : .72f);
                float hh = height * scale;
                float half = .082f * scale;
                polygon(color, cx,base-hh, cx-half,base-hh*.53f, cx-half*.54f,base-hh*.54f,
                        cx-half*.92f,base-hh*.25f, cx,base-hh*.31f,
                        cx+half*.92f,base-hh*.25f, cx+half*.54f,base-hh*.54f,
                        cx+half,base-hh*.53f, cx,base-hh, cx,base);
            }
        }

        private void volcanicNight() {
            sky();
            // Deterministic star positions make repeated previews stable.
            float[][] stars = {{.08f,.13f,.008f},{.20f,.27f,.006f},{.34f,.12f,.007f},
                    {.48f,.23f,.005f},{.63f,.10f,.008f},{.84f,.14f,.006f},{.93f,.31f,.007f},
                    {.13f,.39f,.005f},{.78f,.36f,.005f},{.55f,.34f,.006f}};
            for (int i = 0; i < stars.length; i++)
                disk(stars[i][0], stars[i][1], stars[i][2], alpha(c.highlight, i % 3 == 0 ? 235 : 175));

            glow(.51f,.54f,.34f,c.accent,105);
            // Smoke rises in broken, layered curves from the crater.
            linePath(alpha(c.far, 170), .008f,
                    .47f,.38f, .40f,.31f, .48f,.24f, .43f,.17f, .50f,.10f);
            linePath(alpha(c.far, 125), .006f,
                    .54f,.39f, .61f,.32f, .53f,.26f, .59f,.20f, .56f,.14f);
            polygon(c.far, 0f,.74f, .18f,.65f, .32f,.53f, .43f,.44f,
                    .50f,.48f, .58f,.43f, .69f,.55f, .82f,.66f, 1f,.73f, 1f,1f, 0f,1f);
            // Crater rim and branching lava are the focal graphic, all using caller accents.
            linePath(c.highlight, .012f, .43f,.45f, .47f,.48f, .52f,.48f, .57f,.44f);
            linePath(c.accent, .016f,
                    .49f,.51f, .48f,.62f, .43f,.69f, .45f,.78f, .38f,.88f);
            linePath(c.accent, .012f,
                    .53f,.52f, .56f,.64f, .61f,.71f, .58f,.80f, .66f,.90f);
            linePath(alpha(c.highlight, 190), .006f,
                    .45f,.70f, .48f,.73f, .51f,.72f, .56f,.76f);
            polygon(c.foreground, 0f,.86f, .20f,.82f, .38f,.88f, .56f,.83f,
                    .78f,.88f, 1f,.82f, 1f,1f, 0f,1f);
        }

        private void dunes() {
            sky();
            glow(.75f,.25f,.30f,c.accent,60);
            disk(.75f,.25f,.075f,c.highlight);
            linePath(alpha(c.highlight, 125), .003f,
                    .08f,.26f, .22f,.23f, .36f,.25f, .46f,.22f);
            linePath(alpha(c.highlight, 105), .003f,
                    .15f,.34f, .27f,.32f, .38f,.34f);

            Path farDune = new Path();
            farDune.moveTo(x(0f),y(.69f));
            farDune.cubicTo(x(.27f),y(.47f), x(.47f),y(.65f), x(.68f),y(.56f));
            farDune.cubicTo(x(.82f),y(.51f), x(.92f),y(.57f), x(1f),y(.53f));
            farDune.lineTo(x(1f),y(1f)); farDune.lineTo(x(0f),y(1f)); farDune.close();
            fill(c.far); canvas.drawPath(farDune,paint);

            Path middleDune = new Path();
            middleDune.moveTo(x(0f),y(.80f));
            middleDune.cubicTo(x(.18f),y(.73f), x(.28f),y(.57f), x(.50f),y(.65f));
            middleDune.cubicTo(x(.68f),y(.72f), x(.78f),y(.84f), x(1f),y(.68f));
            middleDune.lineTo(x(1f),y(1f)); middleDune.lineTo(x(0f),y(1f)); middleDune.close();
            fill(c.middle); canvas.drawPath(middleDune,paint);

            Path nearDune = new Path();
            nearDune.moveTo(x(0f),y(.88f));
            nearDune.cubicTo(x(.23f),y(.82f), x(.43f),y(.94f), x(.62f),y(.84f));
            nearDune.cubicTo(x(.78f),y(.75f), x(.87f),y(.77f), x(1f),y(.83f));
            nearDune.lineTo(x(1f),y(1f)); nearDune.lineTo(x(0f),y(1f)); nearDune.close();
            fill(c.foreground); canvas.drawPath(nearDune,paint);
            linePath(alpha(c.highlight, 185), .006f,
                    .02f,.80f, .21f,.73f, .34f,.67f, .49f,.68f);
            linePath(alpha(c.highlight, 160), .005f,
                    .55f,.83f, .70f,.78f, .84f,.77f, .97f,.81f);
            linePath(alpha(c.accent, 165), .003f,
                    .08f,.92f, .22f,.89f, .36f,.91f);
        }

        private void cloudSea() {
            sky();
            glow(.50f,.35f,.36f,c.accent,75);
            disk(.50f,.35f,.065f,c.highlight);

            // Far cloud islands sit low, leaving open sky for a calm composition.
            polygon(alpha(c.far, 220), 0f,.63f, .10f,.59f, .20f,.62f, .31f,.56f,
                    .43f,.62f, .58f,.57f, .71f,.62f, .84f,.56f, 1f,.61f, 1f,1f, 0f,1f);
            cloud(.11f,.69f,.13f,.045f,alpha(c.highlight, 190));
            cloud(.38f,.65f,.16f,.055f,alpha(c.highlight, 205));
            cloud(.73f,.68f,.19f,.060f,alpha(c.highlight, 185));
            cloud(.95f,.64f,.12f,.045f,alpha(c.highlight, 205));

            polygon(c.middle, 0f,.78f, .11f,.73f, .24f,.79f, .39f,.71f,
                    .53f,.78f, .70f,.72f, .84f,.78f, 1f,.71f, 1f,1f, 0f,1f);
            cloud(.00f,.81f,.22f,.07f,alpha(c.foreground, 225));
            cloud(.35f,.84f,.22f,.075f,alpha(c.foreground, 235));
            cloud(.74f,.82f,.25f,.085f,alpha(c.foreground, 230));
            linePath(alpha(c.accent, 180), .004f,
                    .04f,.92f, .22f,.89f, .39f,.92f, .59f,.89f, .77f,.92f, .98f,.89f);
        }

        private void cloud(float cx, float cy, float rx, float ry, int color) {
            fill(color);
            // Ellipse lobes make soft, layered cloud banks without bitmap assets.
            canvas.drawOval(new RectF(x(cx-rx), y(cy-ry*.42f), x(cx+rx), y(cy+ry*.58f)), paint);
            canvas.drawCircle(x(cx-rx*.48f), y(cy), r(ry*.72f), paint);
            canvas.drawCircle(x(cx-rx*.08f), y(cy-ry*.24f), r(ry), paint);
            canvas.drawCircle(x(cx+rx*.40f), y(cy-ry*.06f), r(ry*.78f), paint);
        }
    }
}
