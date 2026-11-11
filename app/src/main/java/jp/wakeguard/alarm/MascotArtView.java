package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Small, scalable, palette-aware character illustrations for visual-design previews.
 * The drawings are vector paths and need no bitmap assets or animation runtime.
 */
public final class MascotArtView extends View {
    public static final List<String> SUPPORTED_CHARACTER_IDS = Collections.unmodifiableList(
            Arrays.asList("dragon", "fox", "owl", "robot"));

    public enum Mascot {
        DRAGON("dragon"), FOX("fox"), OWL("owl"), ROBOT("robot");

        public final String id;
        Mascot(String id) { this.id = id; }

        public static Mascot fromId(String id) {
            if (id != null) for (Mascot mascot : values()) {
                if (mascot.id.equalsIgnoreCase(id)) return mascot;
            }
            return DRAGON;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Mascot mascot = Mascot.DRAGON;
    private int primaryOverride = Color.TRANSPARENT;
    private int secondaryOverride = Color.TRANSPARENT;
    private int outlineOverride = Color.TRANSPARENT;

    public MascotArtView(Context context) { this(context, (AttributeSet) null); }

    public MascotArtView(Context context, String characterId) {
        this(context, null, 0);
        setMascot(characterId);
    }

    public MascotArtView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public MascotArtView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setMascot(Mascot.DRAGON);
    }

    public static MascotArtView create(Context context, String characterId) {
        return new MascotArtView(context, characterId);
    }

    /** Stable ids suitable for a separate preset/catalog model. */
    public static String[] supportedCharacterIds() {
        return SUPPORTED_CHARACTER_IDS.toArray(new String[0]);
    }

    public Mascot getMascot() { return mascot; }

    public void setMascot(Mascot value) {
        mascot = value == null ? Mascot.DRAGON : value;
        setContentDescription(descriptionFor(mascot));
        invalidate();
    }

    /** Selects an illustration by stable id; unknown ids fall back to dragon. */
    public void setMascot(String id) { setMascot(Mascot.fromId(id)); }

    /**
     * Optional explicit colors. Pass Color.TRANSPARENT for any slot to follow the
     * current app palette. Palette colors are looked up at draw time, so a later
     * theme change takes effect after invalidate().
     */
    public void setMascotColors(int primary, int secondary, int outline) {
        primaryOverride = primary;
        secondaryOverride = secondary;
        outlineOverride = outline;
        invalidate();
    }

    public void clearMascotColors() {
        primaryOverride = Color.TRANSPARENT;
        secondaryOverride = Color.TRANSPARENT;
        outlineOverride = Color.TRANSPARENT;
        invalidate();
    }

    private String descriptionFor(Mascot value) {
        String language = I18n.language(getContext());
        if (value == Mascot.FOX) return "en".equals(language) ? "Fox mascot illustration"
                : "ko".equals(language) ? "여우 마스코트 그림" : "キツネのマスコットイラスト";
        if (value == Mascot.OWL) return "en".equals(language) ? "Owl mascot illustration"
                : "ko".equals(language) ? "부엉이 마스코트 그림" : "フクロウのマスコットイラスト";
        if (value == Mascot.ROBOT) return "en".equals(language) ? "Robot mascot illustration"
                : "ko".equals(language) ? "로봇 마스코트 그림" : "ロボットのマスコットイラスト";
        return "en".equals(language) ? "Dragon mascot illustration"
                : "ko".equals(language) ? "드래곤 마스코트 그림" : "ドラゴンのマスコットイラスト";
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom());
        if (side <= 0f) return;

        int primary = primaryOverride != Color.TRANSPARENT ? primaryOverride : Ui.ACCENT;
        int surface = Ui.SURFACE;
        int secondary = secondaryOverride != Color.TRANSPARENT ? secondaryOverride
                : blend(primary, surface, .56f);
        int outline = outlineOverride != Color.TRANSPARENT ? outlineOverride : Ui.TEXT;

        int save = canvas.save();
        canvas.translate(getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f,
                getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f);
        // The shared character coordinate box is deliberately portrait-shaped.
        // A modest horizontal expansion keeps the mascots legible in square cards.
        canvas.scale(side / 130f * 1.2f, side / 130f);
        canvas.translate(-52f, -19.5f);
        switch (mascot) {
            case FOX: drawFox(canvas, primary, secondary, outline); break;
            case OWL: drawOwl(canvas, primary, secondary, outline); break;
            case ROBOT: drawRobot(canvas, primary, secondary, outline); break;
            default: drawDragon(canvas, primary, secondary, outline); break;
        }
        canvas.restoreToCount(save);
    }

    private void drawDragon(Canvas c, int main, int light, int ink) {
        // Broad, friendly wings behind the head.
        path.reset(); path.moveTo(35, 6); path.cubicTo(47, -7, 47, -24, 44, -31);
        path.cubicTo(34, -30, 27, -25, 22, -18); path.lineTo(30, -16);
        path.lineTo(22, -9); path.lineTo(34, -7); path.close(); fill(c, path, light); stroke(c, path, ink, 2.4f);
        path.reset(); path.moveTo(65, 6); path.cubicTo(53, -7, 53, -24, 56, -31);
        path.cubicTo(66, -30, 73, -25, 78, -18); path.lineTo(70, -16);
        path.lineTo(78, -9); path.lineTo(66, -7); path.close(); fill(c, path, light); stroke(c, path, ink, 2.4f);
        // Tail with a rounded arrow tip.
        path.reset(); path.moveTo(62, 39); path.cubicTo(80, 43, 81, 57, 72, 62);
        path.cubicTo(69, 55, 63, 53, 57, 55); stroke(c, path, ink, 3f);
        path.reset(); path.moveTo(73, 60); path.lineTo(84, 56); path.lineTo(80, 68); path.close(); fill(c, path, main);
        stroke(c, path, ink, 2.4f);
        // Little body and belly.
        oval(c, 31, 25, 69, 81, main);
        outlineOval(c, 31, 25, 69, 81, ink, 3f);
        oval(c, 39, 42, 61, 75, light);
        strokeLine(c, 50, 47, 50, 69, ink, 1.7f);
        for (int y = 52; y <= 65; y += 6) strokeLine(c, 43, y, 47, y + 2, ink, 1.3f);
        // Head, horns and snout.
        oval(c, 25, -35, 75, 20, main);
        outlineOval(c, 25, -35, 75, 20, ink, 3f);
        path.reset(); path.moveTo(34, -22); path.lineTo(27, -39); path.lineTo(43, -31); path.close(); fill(c, path, light); stroke(c, path, ink, 2.1f);
        path.reset(); path.moveTo(66, -22); path.lineTo(73, -39); path.lineTo(57, -31); path.close(); fill(c, path, light); stroke(c, path, ink, 2.1f);
        // Two small back spikes make the silhouette read as a dragon.
        for (int i = 0; i < 3; i++) {
            float x = 39 + i * 8;
            path.reset(); path.moveTo(x - 3, -28 + i * 2); path.lineTo(x, -36 + i * 2);
            path.lineTo(x + 3, -27 + i * 2); path.close(); fill(c, path, light);
        }
        oval(c, 35, -3, 65, 11, light);
        oval(c, 39, -17, 44, -10, ink); oval(c, 56, -17, 61, -10, ink);
        oval(c, 40, -16, 41.5f, -14, Color.WHITE); oval(c, 57, -16, 58.5f, -14, Color.WHITE);
        oval(c, 47.5f, 1, 52.5f, 5, ink);
        path.reset(); path.moveTo(43, 8); path.quadTo(50, 14, 57, 8); stroke(c, path, ink, 2f);
        strokeLine(c, 38, 1, 33, -1, ink, 1.3f); strokeLine(c, 62, 1, 67, -1, ink, 1.3f);
    }

    private void drawFox(Canvas c, int main, int light, int ink) {
        // Pointed ears and soft cheeks.
        path.reset(); path.moveTo(27, -8); path.lineTo(25, -40); path.lineTo(48, -25); path.close(); fill(c, path, main); stroke(c, path, ink, 3f);
        path.reset(); path.moveTo(73, -8); path.lineTo(75, -40); path.lineTo(52, -25); path.close(); fill(c, path, main); stroke(c, path, ink, 3f);
        path.reset(); path.moveTo(33, -17); path.lineTo(31, -32); path.lineTo(44, -23); path.close(); fill(c, path, light);
        path.reset(); path.moveTo(67, -17); path.lineTo(69, -32); path.lineTo(56, -23); path.close(); fill(c, path, light);
        oval(c, 23, -27, 77, 36, main); outlineOval(c, 23, -27, 77, 36, ink, 3f);
        // White cheek mask and muzzle.
        path.reset(); path.moveTo(26, 2); path.quadTo(34, -1, 41, 4); path.quadTo(50, 20, 50, 23);
        path.quadTo(50, 25, 47, 23); path.quadTo(35, 22, 26, 15); path.close(); fill(c, path, light);
        path.reset(); path.moveTo(74, 2); path.quadTo(66, -1, 59, 4); path.quadTo(50, 20, 50, 23);
        path.quadTo(50, 25, 53, 23); path.quadTo(65, 22, 74, 15); path.close(); fill(c, path, light);
        oval(c, 40, 7, 60, 21, light);
        oval(c, 38, -7, 43, -1, ink); oval(c, 57, -7, 62, -1, ink);
        oval(c, 47, 8, 53, 13, ink);
        path.reset(); path.moveTo(47, 17); path.quadTo(50, 21, 53, 17); stroke(c, path, ink, 1.8f);
        strokeLine(c, 27, 8, 17, 6, ink, 1.3f); strokeLine(c, 29, 13, 18, 14, ink, 1.3f);
        strokeLine(c, 73, 8, 83, 6, ink, 1.3f); strokeLine(c, 71, 13, 82, 14, ink, 1.3f);
        // Curled tail with a light tip.
        path.reset(); path.moveTo(62, 46); path.cubicTo(87, 41, 88, 66, 70, 69);
        path.cubicTo(58, 71, 57, 60, 66, 58); stroke(c, path, ink, 7f);
        path.reset(); path.moveTo(67, 58); path.cubicTo(62, 62, 66, 67, 72, 65); stroke(c, path, light, 4f);
    }

    private void drawOwl(Canvas c, int main, int light, int ink) {
        path.reset(); path.moveTo(26, -15); path.lineTo(29, -36); path.lineTo(44, -23);
        path.moveTo(74, -15); path.lineTo(71, -36); path.lineTo(56, -23); fill(c, path, main);
        stroke(c, path, ink, 3f);
        oval(c, 24, -25, 76, 78, main); outlineOval(c, 24, -25, 76, 78, ink, 3f);
        // Wings and tummy feathers.
        oval(c, 17, 6, 38, 50, light); outlineOval(c, 17, 6, 38, 50, ink, 2f);
        oval(c, 62, 6, 83, 50, light); outlineOval(c, 62, 6, 83, 50, ink, 2f);
        oval(c, 35, 26, 65, 72, light);
        for (int i = 0; i < 3; i++) {
            float y = 40 + i * 8;
            path.reset(); path.moveTo(42, y); path.quadTo(50, y + 5, 58, y); stroke(c, path, ink, 1.4f);
        }
        // Big round eyes and a small beak.
        oval(c, 31, -13, 50, 8, light); oval(c, 50, -13, 69, 8, light);
        outlineOval(c, 31, -13, 50, 8, ink, 2.3f); outlineOval(c, 50, -13, 69, 8, ink, 2.3f);
        oval(c, 39, -6, 45, 1, ink); oval(c, 55, -6, 61, 1, ink);
        oval(c, 40, -5, 42, -3, Color.WHITE); oval(c, 56, -5, 58, -3, Color.WHITE);
        path.reset(); path.moveTo(46, 9); path.lineTo(54, 9); path.lineTo(50, 16); path.close(); fill(c, path, main);
        for (int x = 41; x <= 59; x += 9) oval(c, x - 1.5f, 31, x + 1.5f, 34, ink);
    }

    private void drawRobot(Canvas c, int main, int light, int ink) {
        // Antenna and compact rounded chassis.
        strokeLine(c, 50, -34, 50, -26, ink, 3f); oval(c, 45, -42, 55, -32, light); outlineOval(c, 45, -42, 55, -32, ink, 2.4f);
        roundRect(c, 25, -29, 75, 35, 12, main); outlineRoundRect(c, 25, -29, 75, 35, 12, ink, 3f);
        // Ear bolts and face panel.
        roundRect(c, 19, -9, 26, 9, 3, light); roundRect(c, 74, -9, 81, 9, 3, light);
        roundRect(c, 31, -19, 69, 16, 8, light);
        oval(c, 38, -8, 45, -1, ink); oval(c, 55, -8, 62, -1, ink);
        roundRect(c, 44, 5, 56, 8, 1.5f, ink);
        // Neck and shoulders.
        roundRect(c, 43, 35, 57, 42, 2, ink);
        roundRect(c, 30, 41, 70, 79, 9, main); outlineRoundRect(c, 30, 41, 70, 79, 9, ink, 3f);
        oval(c, 45, 51, 55, 61, light); oval(c, 45, 64, 55, 74, light);
        strokeLine(c, 37, 48, 37, 71, ink, 1.5f); strokeLine(c, 63, 48, 63, 71, ink, 1.5f);
    }

    private void fill(Canvas c, Path shape, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setShader(null); paint.setColor(color); c.drawPath(shape, paint);
    }

    private void stroke(Canvas c, Path shape, int color, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setShader(null); paint.setColor(color);
        paint.setStrokeWidth(width); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(shape, paint); paint.setStyle(Paint.Style.FILL);
    }

    private void oval(Canvas c, float l, float t, float r, float b, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setShader(null); paint.setColor(color);
        c.drawOval(new RectF(l, t, r, b), paint);
    }

    private void outlineOval(Canvas c, float l, float t, float r, float b, int color, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setShader(null); paint.setColor(color); paint.setStrokeWidth(width);
        c.drawOval(new RectF(l, t, r, b), paint); paint.setStyle(Paint.Style.FILL);
    }

    private void roundRect(Canvas c, float l, float t, float r, float b, float radius, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setShader(null); paint.setColor(color);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
    }

    private void outlineRoundRect(Canvas c, float l, float t, float r, float b, float radius, int color, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setShader(null); paint.setColor(color); paint.setStrokeWidth(width);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint); paint.setStyle(Paint.Style.FILL);
    }

    private void strokeLine(Canvas c, float x1, float y1, float x2, float y2, int color, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setShader(null); paint.setColor(color);
        paint.setStrokeWidth(width); paint.setStrokeCap(Paint.Cap.ROUND); c.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private int blend(int from, int to, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(Math.round(Color.red(from) * inverse + Color.red(to) * amount),
                Math.round(Color.green(from) * inverse + Color.green(to) * amount),
                Math.round(Color.blue(from) * inverse + Color.blue(to) * amount));
    }
}
