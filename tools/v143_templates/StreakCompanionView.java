package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/**
 * WakeGuard v1.4.3: one permanent companion that begins as living fire and
 * continuously becomes a high-detail flame dragon. 100% local/offline renderer.
 * No downloadable art, no network dependency, no gacha/rarity/collection logic.
 */
public class StreakCompanionView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private long level = 1L;
    private int streak = 0;
    private boolean animate = true;

    public StreakCompanionView(Context c) {
        super(c);
        fill.setDither(true);
        line.setDither(true);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setGrowth(long growthLevel, int currentStreak) {
        level = Math.max(1L, growthLevel);
        streak = Math.max(0, currentStreak);
        invalidate();
    }

    public void setAnimationEnabled(boolean enabled) {
        animate = enabled;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w < 2 || h < 2) return;

        float scale = Math.min(w / 390f, h / 430f);
        float phase = animate ? (SystemClock.uptimeMillis() % 9000L) / 9000f : 0f;
        double power = StreakGrowth.visualPower(level);
        float dragon = smooth((float)((power - 3.15) / 3.55));
        float wings = smooth((float)((power - 4.45) / 2.25));
        float mythic = smooth((float)((power - 6.65) / 2.75));
        float infinite = (float)Math.log1p(Math.max(0L, level - 999L)) / 8.0f;
        infinite = Math.max(0f, Math.min(2.6f, infinite));
        float breathe = animate ? (float)Math.sin(phase * Math.PI * 2.0) * (1.4f + mythic) : 0f;

        c.save();
        c.translate(w * .5f, h * .55f + breathe);
        c.scale(scale, scale);
        drawAura(c, power, dragon, mythic, infinite, phase);
        drawEmbers(c, power, mythic, infinite, phase);
        drawLivingFireBase(c, power, phase);
        if (dragon > .02f) drawDragon(c, power, dragon, wings, mythic, infinite, phase);
        c.restore();

        if (animate && isAttachedToWindow()) postInvalidateOnAnimation();
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private int alpha(int col, int a) {
        a = Math.max(0, Math.min(255, a));
        return Color.argb(a, Color.red(col), Color.green(col), Color.blue(col));
    }

    private int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.argb(
            (int)(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
            (int)(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
            (int)(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
            (int)(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private int deep(double power) {
        return blend(0xff8d0900, 0xff481065, (float)Math.min(1.0, power / 11.0));
    }
    private int red(double power) {
        return blend(0xffdf1600, 0xffff145b, (float)Math.min(1.0, power / 12.0) * .48f);
    }
    private int orange(double power) {
        return blend(0xffff5a00, 0xffff7a16, (float)Math.min(1.0, power / 12.0));
    }
    private int gold(double power) {
        return blend(0xffffb000, 0xffffed7a, (float)Math.min(1.0, power / 12.0));
    }
    private int core(double power) {
        return blend(0xfffffff0, 0xffd9fbff, smooth((float)((power - 6.3) / 4.0)));
    }
    private int energy(double power) {
        return blend(0xffffd24a, 0xff76eaff, smooth((float)((power - 7.2) / 3.2)));
    }

    private void drawAura(Canvas c, double power, float dragon, float mythic, float infinite, float phase) {
        float r = 112f + (float)Math.log1p(power) * 16f + mythic * 18f + infinite * 9f;
        fill.setShader(new RadialGradient(0, 8, r,
            new int[]{alpha(core(power), 22 + (int)(mythic * 15)), alpha(gold(power), 36), alpha(red(power), 25), 0x00000000},
            new float[]{0f, .28f, .60f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(0, 8, r, fill);
        fill.setShader(null);

        int rings = Math.min(9, (int)Math.floor(Math.max(0.0, power - 6.1) / 1.1) + (int)infinite);
        for (int i = 0; i < rings; i++) {
            float rr = 112f + i * 12f + infinite * 4f;
            oval.set(-rr, -rr * .72f, rr, rr * .72f);
            line.setStrokeWidth(.9f + (i % 3) * .28f);
            line.setColor(alpha(i % 2 == 0 ? energy(power) : gold(power), 48 - i * 3));
            float rot = phase * (i % 2 == 0 ? 180f : -150f) + i * 31f;
            int segments = 5 + i % 4;
            for (int s = 0; s < segments; s++) c.drawArc(oval, rot + s * (360f / segments), 18f + (s % 3) * 7f, false, line);
        }
    }

    private void drawEmbers(Canvas c, double power, float mythic, float infinite, float phase) {
        int count = 18 + Math.min(58, (int)(power * 4.0)) + Math.min(18, streak) + (int)(infinite * 5f);
        Random rng = new Random(level * 1103515245L + 12345L);
        for (int i = 0; i < count; i++) {
            float a = (float)(rng.nextDouble() * Math.PI * 2.0 + phase * (i % 2 == 0 ? 2.2 : -1.3));
            float rr = 72f + rng.nextFloat() * (92f + mythic * 38f + infinite * 12f);
            float x = (float)Math.cos(a) * rr;
            float y = (float)Math.sin(a) * rr * .84f - rng.nextFloat() * 24f;
            float sz = .8f + rng.nextFloat() * (2.0f + mythic * 1.4f);
            int col = i % 6 == 0 ? energy(power) : (i % 2 == 0 ? gold(power) : red(power));
            fill.setShader(null);
            fill.setColor(alpha(col, 75 + rng.nextInt(170)));
            c.drawCircle(x, y, sz, fill);
            if (i % 7 == 0) {
                line.setColor(alpha(col, 95));
                line.setStrokeWidth(Math.max(.8f, sz * .5f));
                c.drawLine(x, y + sz * 3f, x, y - sz * 6f, line);
            }
        }
    }

    private void drawLivingFireBase(Canvas c, double power, float phase) {
        float grow = (float)Math.min(42.0, power * 3.35);
        float h = 190f + grow;
        float w = 62f + (float)Math.min(25.0, power * 1.9);
        drawFlameShape(c, h + 22f, w + 18f, phase, 0, alpha(deep(power), 160), alpha(red(power), 80));
        drawFlameShape(c, h, w, phase, 1, red(power), deep(power));
        drawFlameShape(c, h * .78f, w * .70f, phase, 2, orange(power), red(power));
        drawFlameShape(c, h * .55f, w * .43f, phase, 3, core(power), gold(power));
    }

    private void drawFlameShape(Canvas c, float height, float width, float phase, int layer, int top, int bottom) {
        buildFlame(path, height, width, 8 + layer * 2, phase, layer);
        fill.setShader(new LinearGradient(0, -height * .72f, 0, 116f,
            new int[]{top, layer >= 2 ? (layer == 3 ? coreColorSafe(top) : orangeFrom(top)) : blend(top, bottom, .34f), bottom},
            new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
    }

    private int coreColorSafe(int c) { return blend(c, Color.WHITE, .12f); }
    private int orangeFrom(int c) { return blend(c, 0xffff9b1a, .22f); }

    private void buildFlame(Path out, float height, float maxWidth, int lobes, float phase, int layer) {
        out.reset();
        int n = 24;
        float[] y = new float[n + 1], l = new float[n + 1], r = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float t = i / (float)n;
            float yy = 112f - height * t;
            float profile = (float)Math.pow(Math.sin(Math.PI * Math.min(.985f, t * .93f + .035f)), .66);
            profile *= 1f - .19f * t;
            float wave = (float)Math.sin((t * lobes + phase * 1.2f + layer * .18f) * Math.PI * 2f) * .078f;
            float detail = (float)Math.sin((t * lobes * .53f + phase * .71f + layer) * Math.PI * 2f) * .042f;
            float asym = (float)Math.sin((t * 3.1f + phase * .39f + layer) * Math.PI * 2f) * .052f;
            float width = maxWidth * Math.max(0f, profile + wave + detail);
            if (i == 0) width = maxWidth * .42f;
            if (i == n) width = 0f;
            y[i] = yy;
            l[i] = -width * (1f + asym);
            r[i] = width * (1f - asym);
        }
        out.moveTo(l[0], y[0]);
        for (int i = 1; i <= n; i++) out.quadTo(l[i - 1], y[i - 1], (l[i - 1] + l[i]) * .5f, (y[i - 1] + y[i]) * .5f);
        out.lineTo(0, y[n] - 7f);
        for (int i = n; i >= 1; i--) out.quadTo(r[i], y[i], (r[i] + r[i - 1]) * .5f, (y[i] + y[i - 1]) * .5f);
        out.quadTo(0, 132f, l[0], y[0]);
        out.close();
    }

    private void drawDragon(Canvas c, double power, float dragon, float wings, float mythic, float infinite, float phase) {
        c.saveLayerAlpha(-190, -190, 190, 150, Math.max(18, (int)(255 * dragon)));

        if (wings > .02f) drawWings(c, power, wings, mythic, phase);
        if (dragon > .18f) drawTail(c, power, dragon, mythic, phase);
        drawBody(c, power, dragon, mythic);
        drawNeckAndHead(c, power, dragon, mythic, phase);
        if (wings > .20f) drawWingVeins(c, power, wings, mythic);
        if (mythic > .02f) drawMythicDetails(c, power, mythic, infinite, phase);

        c.restore();
    }

    private void drawBody(Canvas c, double power, float dragon, float mythic) {
        float shoulder = 34f + dragon * 9f + mythic * 4f;
        float waist = 19f + dragon * 5f;
        path.reset();
        path.moveTo(-shoulder, -25f);
        path.cubicTo(-43f, 8f, -36f, 58f, -waist, 92f);
        path.cubicTo(-14f, 112f, -8f, 121f, 0, 126f);
        path.cubicTo(8f, 121f, 14f, 112f, waist, 92f);
        path.cubicTo(36f, 58f, 43f, 8f, shoulder, -25f);
        path.cubicTo(22f, -42f, 12f, -48f, 0, -50f);
        path.cubicTo(-12f, -48f, -22f, -42f, -shoulder, -25f);
        path.close();
        drawPremiumPath(c, path, power, .96f, true);

        // chest core / sternum flame
        path.reset();
        path.moveTo(0, -38f);
        path.cubicTo(-16f, -9f, -14f, 35f, 0, 91f);
        path.cubicTo(14f, 35f, 16f, -9f, 0, -38f);
        path.close();
        fill.setShader(new LinearGradient(0, -42, 0, 96,
            new int[]{alpha(core(power), 235), alpha(gold(power), 210), alpha(red(power), 50)},
            null, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);

        // segmented hot scales
        int rows = 4 + (int)(mythic * 4f);
        for (int i = 0; i < rows; i++) {
            float yy = -4f + i * 19f;
            float ww = 19f - i * 1.3f;
            line.setStrokeWidth(1.2f);
            line.setColor(alpha(energy(power), 80 + (int)(mythic * 50)));
            c.drawArc(new RectF(-ww, yy, ww, yy + 13f), 12f, 156f, false, line);
        }
    }

    private void drawNeckAndHead(Canvas c, double power, float dragon, float mythic, float phase) {
        float headScale = .76f + dragon * .24f + mythic * .08f;
        c.save();
        c.scale(headScale, headScale);
        c.translate(0, -52f);

        // neck flame collar
        path.reset();
        path.moveTo(-24, 18);
        path.cubicTo(-31, -2, -32, -26, -23, -43);
        path.cubicTo(-14, -56, -7, -59, 0, -60);
        path.cubicTo(7, -59, 14, -56, 23, -43);
        path.cubicTo(32, -26, 31, -2, 24, 18);
        path.close();
        drawPremiumPath(c, path, power, .95f, true);

        // head silhouette
        path.reset();
        path.moveTo(-30, -42);
        path.cubicTo(-40, -56, -37, -77, -24, -88);
        path.lineTo(-17, -99);
        path.lineTo(-9, -93);
        path.cubicTo(-5, -104, -3, -111, 0, -119);
        path.cubicTo(3, -111, 5, -104, 9, -93);
        path.lineTo(17, -99);
        path.lineTo(24, -88);
        path.cubicTo(37, -77, 40, -56, 30, -42);
        path.lineTo(22, -27);
        path.lineTo(12, -19);
        path.lineTo(7, -8);
        path.lineTo(0, -3);
        path.lineTo(-7, -8);
        path.lineTo(-12, -19);
        path.lineTo(-22, -27);
        path.close();
        drawPremiumPath(c, path, power, 1f, true);

        // sweeping flame horns (not rigid black horns)
        drawHorn(c, -1, power, mythic, phase);
        drawHorn(c, 1, power, mythic, phase);

        // eyes
        int eye = mythic > .55f ? energy(power) : core(power);
        fill.setShader(new RadialGradient(-12, -57, 8,
            new int[]{Color.WHITE, eye, alpha(red(power), 0)}, new float[]{0f, .35f, 1f}, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(-20, -61, -6, -53), fill);
        fill.setShader(new RadialGradient(12, -57, 8,
            new int[]{Color.WHITE, eye, alpha(red(power), 0)}, new float[]{0f, .35f, 1f}, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(6, -61, 20, -53), fill);
        fill.setShader(null);

        // brow / cheek lines create a real dragon face rather than a mascot face
        line.setStrokeWidth(2.0f);
        line.setColor(alpha(gold(power), 170));
        path.reset(); path.moveTo(-23,-66); path.quadTo(-14,-72,-5,-65); c.drawPath(path,line);
        path.reset(); path.moveTo(23,-66); path.quadTo(14,-72,5,-65); c.drawPath(path,line);
        line.setStrokeWidth(1.4f);
        line.setColor(alpha(energy(power), 100));
        path.reset(); path.moveTo(-18,-45); path.quadTo(-8,-37,0,-36); path.quadTo(8,-37,18,-45); c.drawPath(path,line);

        c.restore();
    }

    private void drawHorn(Canvas c, int side, double power, float mythic, float phase) {
        float sway = animate ? (float)Math.sin((phase + (side < 0 ? .1f : .6f)) * Math.PI * 2f) * 2f : 0f;
        float len = 35f + mythic * 25f;
        path.reset();
        path.moveTo(side * 18f, -91f);
        path.cubicTo(side * 29f, -111f, side * (36f + sway), -117f - len * .35f, side * (43f + sway), -117f - len);
        path.cubicTo(side * (31f + sway), -111f - len * .55f, side * 19f, -102f, side * 12f, -91f);
        path.close();
        fill.setShader(new LinearGradient(0, -117f - len, 0, -88f,
            new int[]{alpha(core(power), 230), alpha(gold(power), 220), alpha(red(power), 60)},
            null, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
    }

    private void drawWings(Canvas c, double power, float wings, float mythic, float phase) {
        for (int side = -1; side <= 1; side += 2) {
            c.save();
            c.scale(side, 1);
            float span = 86f + wings * 66f + mythic * 18f;
            float lift = animate ? (float)Math.sin((phase + (side < 0 ? .08f : .55f)) * Math.PI * 2f) * 3f : 0f;
            path.reset();
            path.moveTo(25, -24);
            path.cubicTo(48, -55, 72, -80, span, -97 + lift);
            path.lineTo(span - 21, -51 + lift);
            path.lineTo(span - 5, -61 + lift);
            path.lineTo(span - 31, -20 + lift);
            path.lineTo(span - 12, -26 + lift);
            path.lineTo(span - 45, 7 + lift);
            path.lineTo(span - 26, 6 + lift);
            path.cubicTo(98, 28, 58, 19, 29, 9);
            path.close();
            drawPremiumPath(c, path, power, .86f + mythic * .1f, false);

            // bright leading edge
            line.setStrokeWidth(2.0f + mythic * .7f);
            line.setColor(alpha(energy(power), 105 + (int)(mythic * 70)));
            Path edge = new Path();
            edge.moveTo(27,-23); edge.cubicTo(54,-58,86,-83,span,-97+lift);
            c.drawPath(edge,line);
            c.restore();
        }
    }

    private void drawWingVeins(Canvas c, double power, float wings, float mythic) {
        for (int side = -1; side <= 1; side += 2) {
            line.setStrokeWidth(1.0f + mythic * .5f);
            line.setColor(alpha(gold(power), 72 + (int)(mythic * 55)));
            for (int i = 0; i < 4 + (int)(mythic * 3); i++) {
                float yy = -18f + i * 13f;
                path.reset();
                path.moveTo(side * 31f, -17f + i * 4f);
                path.quadTo(side * (58f + i * 9f), yy - 18f, side * (90f + wings * 28f), yy - 9f);
                c.drawPath(path, line);
            }
        }
    }

    private void drawTail(Canvas c, double power, float dragon, float mythic, float phase) {
        float sway = animate ? (float)Math.sin(phase * Math.PI * 2f) * 8f : 0f;
        line.setStrokeWidth(16f + dragon * 5f + mythic * 4f);
        line.setColor(alpha(deep(power), 175));
        path.reset();
        path.moveTo(0, 96);
        path.cubicTo(31, 109, 61, 117, 79 + sway, 93);
        path.cubicTo(99 + sway, 67, 82 + sway, 44, 66, 56);
        c.drawPath(path, line);
        line.setStrokeWidth(7f + mythic * 2f);
        line.setColor(alpha(orange(power), 210));
        c.drawPath(path, line);
        line.setStrokeWidth(2.2f);
        line.setColor(alpha(core(power), 175));
        c.drawPath(path, line);
    }

    private void drawMythicDetails(Canvas c, double power, float mythic, float infinite, float phase) {
        int spines = 4 + (int)(mythic * 7f) + (int)Math.min(4f, infinite);
        for (int i = 0; i < spines; i++) {
            float q = (i - (spines - 1) / 2f) / Math.max(1f, (spines - 1) / 2f);
            float x = q * (31f + mythic * 10f);
            float top = -153f - (1f - Math.abs(q)) * (18f + infinite * 8f);
            path.reset();
            path.moveTo(x - 4, -122);
            path.quadTo(x - 7, top + 17, x, top);
            path.quadTo(x + 7, top + 17, x + 4, -122);
            path.close();
            fill.setShader(new LinearGradient(0, top, 0, -118,
                new int[]{alpha(energy(power), 220), alpha(gold(power), 185), alpha(red(power), 20)},
                null, Shader.TileMode.CLAMP));
            c.drawPath(path, fill);
            fill.setShader(null);
        }

        // chest sigil becomes denser forever after the mythic stage
        int marks = 3 + (int)(mythic * 5) + (int)Math.min(7f, infinite * 2f);
        line.setStrokeWidth(1.1f);
        line.setColor(alpha(energy(power), 75 + (int)(mythic * 70)));
        for (int i = 0; i < marks; i++) {
            float rr = 15f + i * 4.5f;
            oval.set(-rr, 21f - rr * .58f, rr, 21f + rr * .58f);
            c.drawArc(oval, phase * 80f + i * 29f, 38f + (i % 3) * 15f, false, line);
        }
    }

    private void drawPremiumPath(Canvas c, Path pth, double power, float opacity, boolean central) {
        int a = Math.max(0, Math.min(255, (int)(255 * opacity)));
        // wide translucent flame rim
        line.setStrokeWidth(8.0f);
        line.setColor(alpha(red(power), (int)(42 * opacity)));
        c.drawPath(pth, line);
        line.setStrokeWidth(3.0f);
        line.setColor(alpha(gold(power), (int)(110 * opacity)));
        c.drawPath(pth, line);

        fill.setShader(new LinearGradient(0, -150, 0, 128,
            central
                ? new int[]{alpha(deep(power), a), alpha(red(power), a), alpha(orange(power), (int)(a * .93f)), alpha(deep(power), (int)(a * .88f))}
                : new int[]{alpha(red(power), (int)(a * .88f)), alpha(orange(power), a), alpha(deep(power), (int)(a * .82f))},
            central ? new float[]{0f,.32f,.66f,1f} : new float[]{0f,.46f,1f},
            Shader.TileMode.CLAMP));
        c.drawPath(pth, fill);
        fill.setShader(null);
    }
}
