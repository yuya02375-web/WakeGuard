package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Small, hand-drawn field-guide mascots. The animal bodies keep their natural
 * ink-and-paper colors; the chosen palette is reserved for each mascot's badge
 * and a quiet halo so the art can sit with any appearance set.
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

    private static final int INK = Color.rgb(48, 48, 39);
    private static final int PAPER = Color.rgb(242, 230, 198);
    private static final int PAPER_SHADE = Color.rgb(217, 201, 160);
    private static final int DRAGON_GREEN = Color.rgb(87, 119, 77);
    private static final int DRAGON_DARK = Color.rgb(56, 81, 55);
    private static final int DRAGON_LIGHT = Color.rgb(139, 157, 104);
    private static final int FOX_RUSSET = Color.rgb(184, 91, 48);
    private static final int FOX_DARK = Color.rgb(112, 57, 38);
    private static final int OWL_BROWN = Color.rgb(111, 79, 53);
    private static final int OWL_LIGHT = Color.rgb(177, 128, 72);
    private static final int METAL = Color.rgb(190, 174, 142);
    private static final int METAL_DARK = Color.rgb(102, 91, 73);
    private static final int COPPER = Color.rgb(157, 91, 53);

    private static final Painter STATIC_PAINTER = new Painter();

    private final Painter painter = new Painter();
    private Mascot mascot = Mascot.DRAGON;
    private boolean mascotVisible = true;
    private int primaryOverride = Color.TRANSPARENT;
    private int secondaryOverride = Color.TRANSPARENT;

    public MascotArtView(Context context) { this(context, (AttributeSet) null); }

    public MascotArtView(Context context, String characterId) {
        super(context);
        setWillNotDraw(false);
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
        mascotVisible = true;
        setContentDescription(descriptionFor(mascot));
        invalidate();
    }

    /** Selects by stable id. The saved "none" choice intentionally draws nothing. */
    public void setMascot(String id) {
        if (id != null && "none".equalsIgnoreCase(id)) {
            mascot = Mascot.DRAGON;
            mascotVisible = false;
            setContentDescription(null);
            invalidate();
            return;
        }
        setMascot(Mascot.fromId(id));
    }

    /**
     * The first two colors accent only the badge and halo. The third parameter
     * remains for source compatibility; outlines intentionally stay ink colored.
     */
    public void setMascotColors(int primary, int secondary, int outline) {
        primaryOverride = primary;
        secondaryOverride = secondary;
        invalidate();
    }

    public void clearMascotColors() {
        primaryOverride = Color.TRANSPARENT;
        secondaryOverride = Color.TRANSPARENT;
        invalidate();
    }

    private String descriptionFor(Mascot value) {
        String language = I18n.language(getContext());
        String name = MascotCatalog.get(value.id).label(getContext());
        if ("en".equals(language)) return name + " field-guide character illustration";
        if ("ko".equals(language)) return name + " 캐릭터 그림";
        return name + "のフィールドガイド風イラスト";
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mascotVisible) return;

        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0f || height <= 0f) return;
        RectF box = new RectF(getPaddingLeft(), getPaddingTop(),
                getPaddingLeft() + width, getPaddingTop() + height);
        int primary = primaryOverride != Color.TRANSPARENT ? primaryOverride : Ui.ACCENT;
        int secondary = secondaryOverride != Color.TRANSPARENT ? secondaryOverride
                : blend(primary, Ui.SURFACE, .42f);
        drawMascot(canvas, mascot, box, primary, secondary, painter);
    }

    /** Draws the selected character for existing static preview callers. */
    public static void drawCharacter(Canvas canvas, String characterId, RectF box, int accent) {
        Mascot selected = mascotForDrawing(characterId);
        if (selected == null) return;
        int secondary = blend(accent, Ui.SURFACE, .42f);
        synchronized (STATIC_PAINTER) {
            drawMascot(canvas, selected, box, accent, secondary, STATIC_PAINTER);
        }
    }

    /** Context-aware form retained for callers that draw before creating a view. */
    public static void drawCharacter(Context context, Canvas canvas, String characterId,
                                     RectF box, int accent) {
        drawCharacter(canvas, characterId, box, accent);
    }

    private static Mascot mascotForDrawing(String id) {
        if (id != null && "none".equalsIgnoreCase(id)) return null;
        return Mascot.fromId(id);
    }

    private static void drawMascot(Canvas canvas, Mascot selected, RectF box,
                                   int accent, int secondary, Painter p) {
        if (canvas == null || selected == null || box == null
                || box.width() <= 0f || box.height() <= 0f) return;

        float size = Math.min(box.width(), box.height());
        drawHalo(canvas, box, size, accent, p);
        int save = canvas.save();
        canvas.clipRect(box);
        float scale = size / 100f;
        canvas.translate(box.centerX() - size * .5f, box.centerY() - size * .5f);
        canvas.scale(scale, scale);
        switch (selected) {
            case FOX: drawFox(canvas, accent, secondary, p); break;
            case OWL: drawOwl(canvas, accent, secondary, p); break;
            case ROBOT: drawRobot(canvas, accent, secondary, p); break;
            default: drawDragon(canvas, accent, secondary, p); break;
        }
        canvas.restoreToCount(save);
    }

    private static void drawHalo(Canvas canvas, RectF box, float size, int accent, Painter p) {
        p.halo.setShader(new RadialGradient(box.centerX(), box.centerY(), size * .5f,
                new int[]{withAlpha(accent, 18), withAlpha(accent, 0)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(box.centerX(), box.centerY(), size * .5f, p.halo);
        p.halo.setShader(null);
        float y = box.bottom - size * .065f;
        p.halo.setShader(new RadialGradient(box.centerX(), y, size * .25f,
                new int[]{withAlpha(INK, 22), withAlpha(INK, 0)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        int save = canvas.save();
        canvas.scale(1f, .2f, box.centerX(), y);
        canvas.drawCircle(box.centerX(), y, size * .25f, p.halo);
        canvas.restoreToCount(save);
        p.halo.setShader(null);
    }

    /** Side-sitting dragon, with a curled tail and a broad, veined wing. */
    private static void drawDragon(Canvas c, int accent, int secondary, Painter p) {
        Path tail = new Path();
        tail.moveTo(48, 74); tail.cubicTo(37, 79, 39, 91, 27, 89);
        tail.cubicTo(12, 88, 10, 75, 18, 69); tail.cubicTo(25, 64, 33, 70, 29, 76);
        tail.cubicTo(27, 79, 23, 77, 24, 74);
        p.stroke(c, tail, INK, 13f);
        p.stroke(c, tail, DRAGON_GREEN, 9f);
        p.stroke(c, tail, DRAGON_LIGHT, 2.2f);

        Path wing = new Path();
        wing.moveTo(48, 55); wing.cubicTo(40, 40, 32, 26, 22, 19);
        wing.lineTo(25, 31); wing.quadTo(19, 30, 13, 27);
        wing.cubicTo(16, 39, 25, 49, 39, 58); wing.close();
        p.fill(c, wing, DRAGON_DARK); p.ink(c, wing, 2.2f);
        Path membrane = new Path();
        membrane.moveTo(42, 54); membrane.lineTo(24, 25); membrane.quadTo(28, 43, 42, 54);
        p.stroke(c, membrane, DRAGON_LIGHT, 1.4f);
        line(c, p, 40, 54, 25, 35, INK, 1.1f);
        line(c, p, 40, 55, 19, 33, INK, 1.1f);

        Path body = new Path();
        body.moveTo(42, 82); body.cubicTo(34, 72, 36, 57, 42, 51);
        body.cubicTo(48, 44, 62, 45, 69, 55); body.cubicTo(76, 66, 71, 82, 62, 87);
        body.cubicTo(55, 91, 47, 89, 42, 82); body.close();
        p.fill(c, body, DRAGON_GREEN); p.ink(c, body, 2.2f);

        Path farLeg = new Path();
        farLeg.moveTo(48, 74); farLeg.quadTo(45, 80, 47, 86); farLeg.quadTo(52, 89, 56, 85);
        farLeg.lineTo(55, 75); farLeg.close();
        p.fill(c, farLeg, DRAGON_DARK); p.ink(c, farLeg, 1.7f);
        Path nearLeg = new Path();
        nearLeg.moveTo(61, 72); nearLeg.quadTo(59, 80, 62, 86);
        nearLeg.quadTo(68, 89, 71, 84); nearLeg.lineTo(68, 72); nearLeg.close();
        p.fill(c, nearLeg, DRAGON_GREEN); p.ink(c, nearLeg, 1.7f);
        line(c, p, 47, 86, 46, 89, INK, 1.2f);
        line(c, p, 52, 86, 52, 89, INK, 1.2f);
        line(c, p, 65, 85, 64, 88, INK, 1.2f);
        line(c, p, 69, 84, 70, 87, INK, 1.2f);

        Path neck = new Path();
        neck.moveTo(47, 67); neck.cubicTo(43, 52, 48, 37, 55, 31);
        neck.cubicTo(61, 26, 71, 29, 75, 36); neck.cubicTo(74, 43, 66, 47, 61, 43);
        neck.cubicTo(58, 53, 61, 62, 65, 70); neck.cubicTo(60, 76, 52, 75, 47, 67);
        neck.close();
        p.fill(c, neck, DRAGON_GREEN); p.ink(c, neck, 2.2f);

        Path belly = new Path();
        belly.moveTo(54, 67); belly.cubicTo(51, 57, 52, 47, 56, 41);
        belly.quadTo(59, 37, 64, 38); belly.quadTo(66, 41, 62, 47);
        belly.cubicTo(60, 55, 63, 64, 67, 71); belly.quadTo(61, 75, 54, 67);
        p.fill(c, belly, PAPER); p.ink(c, belly, 1.45f);
        line(c, p, 55, 51, 62, 52, PAPER_SHADE, 1.1f);
        line(c, p, 55, 57, 63, 58, PAPER_SHADE, 1.1f);
        line(c, p, 57, 63, 65, 64, PAPER_SHADE, 1.1f);

        Path head = new Path();
        head.moveTo(54, 34); head.cubicTo(51, 26, 55, 17, 63, 15);
        head.cubicTo(71, 12, 79, 17, 81, 24); head.lineTo(82, 29);
        head.quadTo(88, 29, 92, 33); head.quadTo(94, 35, 91, 37);
        head.lineTo(81, 39); head.cubicTo(75, 44, 65, 42, 59, 38); head.close();
        p.fill(c, head, DRAGON_GREEN); p.ink(c, head, 2.1f);

        Path rearHorn = new Path();
        rearHorn.moveTo(59, 19); rearHorn.quadTo(56, 10, 62, 7);
        rearHorn.quadTo(63, 13, 68, 16); rearHorn.close();
        p.fill(c, rearHorn, PAPER_SHADE); p.ink(c, rearHorn, 1.6f);
        Path frontHorn = new Path();
        frontHorn.moveTo(73, 17); frontHorn.quadTo(78, 8, 84, 10);
        frontHorn.quadTo(81, 16, 80, 21); frontHorn.close();
        p.fill(c, frontHorn, PAPER_SHADE); p.ink(c, frontHorn, 1.6f);
        line(c, p, 63, 12, 65, 16, INK, .8f);
        line(c, p, 81, 13, 79, 18, INK, .8f);

        Path crest = new Path();
        crest.moveTo(54, 31); crest.lineTo(48, 25); crest.lineTo(56, 26);
        crest.moveTo(57, 27); crest.lineTo(54, 20); crest.lineTo(61, 24);
        crest.moveTo(63, 23); crest.lineTo(62, 16); crest.lineTo(67, 22);
        p.stroke(c, crest, DRAGON_DARK, 1.8f);
        fillCircle(c, p, 73, 27, 2.2f, PAPER);
        fillCircle(c, p, 74, 27, 1f, INK);
        line(c, p, 87, 34, 89, 33, INK, .9f);

        // A small trail badge is the only strong set-color mark on the dragon.
        Path scarf = new Path();
        scarf.moveTo(59, 43); scarf.quadTo(66, 47, 74, 42); scarf.lineTo(72, 47);
        scarf.quadTo(65, 52, 57, 47); scarf.close();
        p.fill(c, scarf, accent); p.ink(c, scarf, 1.3f);
        Path tag = new Path();
        tag.moveTo(64, 48); tag.lineTo(69, 48); tag.lineTo(68, 55); tag.lineTo(65, 53); tag.close();
        p.fill(c, tag, secondary); p.ink(c, tag, 1f);
        fillCircle(c, p, 66.5f, 50.5f, 1f, INK);
    }

    /** Seated fox with a curled brush, cream chest, and side-looking profile. */
    private static void drawFox(Canvas c, int accent, int secondary, Painter p) {
        Path tail = new Path();
        tail.moveTo(53, 70); tail.cubicTo(41, 68, 39, 83, 27, 83);
        tail.cubicTo(13, 83, 9, 71, 16, 62); tail.cubicTo(21, 55, 31, 58, 34, 65);
        p.stroke(c, tail, FOX_DARK, 19f);
        p.stroke(c, tail, FOX_RUSSET, 15f);
        Path tailTip = new Path();
        // A separate taper keeps the pale tip graphic at small sizes.
        tailTip.moveTo(16, 63); tailTip.cubicTo(12, 68, 13, 75, 19, 78);
        p.stroke(c, tailTip, PAPER, 10f);
        line(c, p, 29, 67, 35, 72, FOX_DARK, 1.2f);
        line(c, p, 28, 73, 34, 77, FOX_DARK, 1.2f);

        Path body = new Path();
        body.moveTo(42, 77); body.cubicTo(35, 68, 38, 54, 45, 47);
        body.cubicTo(53, 39, 67, 44, 70, 55); body.cubicTo(74, 67, 68, 81, 58, 85);
        body.cubicTo(51, 88, 44, 84, 42, 77); body.close();
        p.fill(c, body, FOX_RUSSET); p.ink(c, body, 2.2f);
        Path chest = new Path();
        chest.moveTo(48, 50); chest.quadTo(55, 45, 61, 51);
        chest.cubicTo(65, 61, 63, 73, 58, 81); chest.quadTo(51, 83, 46, 78);
        chest.cubicTo(50, 68, 50, 58, 48, 50); chest.close();
        p.fill(c, chest, PAPER); p.ink(c, chest, 1.35f);
        line(c, p, 51, 59, 58, 60, PAPER_SHADE, 1f);
        line(c, p, 50, 65, 59, 66, PAPER_SHADE, 1f);
        line(c, p, 49, 71, 58, 72, PAPER_SHADE, 1f);

        Path farEar = new Path();
        farEar.moveTo(47, 25); farEar.lineTo(43, 8); farEar.quadTo(55, 13, 59, 24); farEar.close();
        p.fill(c, farEar, FOX_DARK); p.ink(c, farEar, 1.8f);
        Path nearEar = new Path();
        nearEar.moveTo(57, 25); nearEar.lineTo(67, 7); nearEar.quadTo(72, 17, 68, 29); nearEar.close();
        p.fill(c, nearEar, FOX_RUSSET); p.ink(c, nearEar, 1.8f);
        Path innerEar = new Path();
        innerEar.moveTo(49, 22); innerEar.lineTo(47, 13); innerEar.quadTo(54, 17, 56, 23); innerEar.close();
        p.fill(c, innerEar, PAPER_SHADE);
        innerEar.reset(); innerEar.moveTo(61, 24); innerEar.lineTo(66, 13);
        innerEar.quadTo(68, 20, 65, 26); innerEar.close();
        p.fill(c, innerEar, PAPER);

        Path head = new Path();
        head.moveTo(42, 37); head.cubicTo(40, 27, 46, 18, 55, 18);
        head.cubicTo(63, 18, 68, 23, 72, 30); head.lineTo(84, 34);
        head.quadTo(90, 36, 87, 39); head.lineTo(78, 41);
        head.cubicTo(71, 48, 59, 47, 51, 43); head.cubicTo(46, 42, 43, 40, 42, 37);
        head.close();
        p.fill(c, head, FOX_RUSSET); p.ink(c, head, 2.1f);
        Path mask = new Path();
        mask.moveTo(46, 34); mask.quadTo(53, 30, 60, 34); mask.quadTo(68, 38, 79, 36);
        mask.quadTo(72, 43, 63, 41); mask.quadTo(55, 44, 47, 40); mask.close();
        p.fill(c, mask, PAPER); p.ink(c, mask, 1.2f);
        fillCircle(c, p, 65, 29, 2.1f, INK);
        fillCircle(c, p, 85, 35, 1.5f, FOX_DARK);
        line(c, p, 72, 32, 78, 34, PAPER_SHADE, .9f);

        // A simple neckerchief carries the active palette without recoloring fur.
        Path kerchief = new Path();
        kerchief.moveTo(47, 45); kerchief.quadTo(55, 50, 64, 45);
        kerchief.lineTo(62, 50); kerchief.quadTo(55, 54, 46, 50); kerchief.close();
        p.fill(c, kerchief, accent); p.ink(c, kerchief, 1.25f);
        Path knot = new Path();
        knot.moveTo(57, 50); knot.lineTo(63, 51); knot.lineTo(60, 58); knot.close();
        p.fill(c, knot, secondary); p.ink(c, knot, .9f);
        fillCircle(c, p, 57, 48, 1.1f, PAPER);
    }

    /** Perched owl with feathered tufts, a small gaze, and clearly layered wings. */
    private static void drawOwl(Canvas c, int accent, int secondary, Painter p) {
        line(c, p, 10, 82, 90, 82, INK, 3.2f);
        line(c, p, 14, 79, 85, 79, PAPER_SHADE, .9f);

        Path body = new Path();
        body.moveTo(31, 72); body.cubicTo(25, 59, 26, 42, 33, 31);
        body.lineTo(30, 19); body.quadTo(38, 21, 44, 27);
        body.quadTo(50, 23, 56, 26); body.quadTo(65, 19, 73, 20);
        body.lineTo(69, 32); body.cubicTo(77, 44, 77, 60, 68, 75);
        body.cubicTo(56, 82, 42, 81, 31, 72); body.close();
        p.fill(c, body, OWL_BROWN); p.ink(c, body, 2.2f);

        Path leftWing = new Path();
        leftWing.moveTo(34, 40); leftWing.cubicTo(25, 46, 26, 63, 36, 73);
        leftWing.quadTo(43, 71, 47, 64); leftWing.quadTo(40, 53, 44, 39); leftWing.close();
        p.fill(c, leftWing, FOX_DARK); p.ink(c, leftWing, 1.6f);
        Path rightWing = new Path();
        rightWing.moveTo(65, 39); rightWing.cubicTo(76, 45, 75, 63, 66, 73);
        rightWing.quadTo(59, 71, 55, 64); rightWing.quadTo(63, 52, 58, 39); rightWing.close();
        p.fill(c, rightWing, OWL_LIGHT); p.ink(c, rightWing, 1.6f);
        line(c, p, 35, 52, 43, 61, PAPER_SHADE, 1.1f);
        line(c, p, 34, 58, 41, 66, PAPER_SHADE, 1.1f);
        line(c, p, 66, 51, 59, 61, PAPER, 1.1f);
        line(c, p, 67, 58, 61, 66, PAPER, 1.1f);

        Path face = new Path();
        face.moveTo(34, 34); face.quadTo(39, 27, 47, 29);
        face.quadTo(53, 25, 59, 29); face.quadTo(67, 26, 72, 35);
        face.cubicTo(72, 47, 66, 54, 53, 57); face.cubicTo(40, 54, 34, 47, 34, 34);
        face.close();
        p.fill(c, face, PAPER); p.ink(c, face, 1.5f);
        Path leftDisk = new Path();
        leftDisk.moveTo(38, 38); leftDisk.quadTo(45, 31, 51, 38);
        leftDisk.quadTo(48, 48, 42, 48); leftDisk.close();
        p.fill(c, leftDisk, PAPER_SHADE);
        Path rightDisk = new Path();
        rightDisk.moveTo(53, 38); rightDisk.quadTo(61, 31, 68, 38);
        rightDisk.quadTo(64, 48, 58, 48); rightDisk.close();
        p.fill(c, rightDisk, PAPER_SHADE);
        fillCircle(c, p, 45, 40, 3.6f, OWL_LIGHT);
        fillCircle(c, p, 61, 40, 3.2f, OWL_LIGHT);
        fillCircle(c, p, 46, 40, 1.65f, INK);
        fillCircle(c, p, 61.5f, 40, 1.45f, INK);

        Path beak = new Path();
        beak.moveTo(52, 43); beak.lineTo(56, 48); beak.lineTo(51, 50); beak.lineTo(49, 47); beak.close();
        p.fill(c, beak, COPPER); p.ink(c, beak, 1f);
        line(c, p, 43, 61, 47, 64, PAPER, 1.2f);
        line(c, p, 48, 62, 52, 65, PAPER, 1.2f);
        line(c, p, 54, 62, 58, 65, PAPER, 1.2f);
        line(c, p, 60, 61, 63, 64, PAPER, 1.2f);

        // A tiny explorer's pin is the set-color accent.
        fillCircle(c, p, 53, 70, 4.1f, INK);
        fillCircle(c, p, 53, 70, 3.1f, accent);
        line(c, p, 53, 68, 53, 72, secondary, 1.2f);
        line(c, p, 51, 70, 55, 70, secondary, 1.2f);

        Path feet = new Path();
        feet.moveTo(43, 76); feet.quadTo(40, 80, 37, 81);
        feet.moveTo(50, 77); feet.quadTo(49, 80, 47, 81);
        feet.moveTo(58, 77); feet.quadTo(59, 80, 61, 81);
        feet.moveTo(65, 76); feet.quadTo(68, 80, 71, 81);
        p.stroke(c, feet, COPPER, 1.8f);
    }

    /** Wind-up alarm-clock robot; its dial, bells, and key make the silhouette distinct. */
    private static void drawRobot(Canvas c, int accent, int secondary, Painter p) {
        // Top bells and the small bridge between them.
        Path leftBell = new Path();
        leftBell.moveTo(34, 21); leftBell.quadTo(35, 10, 43, 9);
        leftBell.quadTo(51, 10, 51, 21); leftBell.close();
        p.fill(c, leftBell, COPPER); p.ink(c, leftBell, 2f);
        Path rightBell = new Path();
        rightBell.moveTo(51, 21); rightBell.quadTo(52, 10, 60, 9);
        rightBell.quadTo(68, 10, 68, 21); rightBell.close();
        p.fill(c, rightBell, METAL); p.ink(c, rightBell, 2f);
        line(c, p, 42, 14, 59, 14, PAPER, 1f);
        line(c, p, 49, 21, 53, 21, INK, 2.4f);

        // Short wind-up key arms, not animal ears or wings.
        line(c, p, 28, 46, 18, 44, INK, 3f);
        line(c, p, 18, 44, 14, 39, INK, 2.2f);
        line(c, p, 14, 39, 10, 41, INK, 2.2f);
        line(c, p, 72, 45, 82, 43, INK, 3f);
        line(c, p, 82, 43, 87, 38, INK, 2.2f);
        line(c, p, 87, 38, 91, 40, INK, 2.2f);
        fillCircle(c, p, 28, 46, 3.2f, COPPER); p.inkCircle(c, 28, 46, 3.2f, 1.1f);
        fillCircle(c, p, 72, 45, 3.2f, COPPER); p.inkCircle(c, 72, 45, 3.2f, 1.1f);

        Path body = new Path();
        body.moveTo(39, 23); body.quadTo(50, 18, 62, 23);
        body.cubicTo(70, 26, 74, 33, 74, 43); body.lineTo(72, 64);
        body.quadTo(71, 75, 61, 77); body.lineTo(39, 77);
        body.quadTo(29, 75, 28, 64); body.lineTo(26, 43);
        body.cubicTo(26, 33, 31, 26, 39, 23); body.close();
        p.fill(c, body, METAL); p.ink(c, body, 2.4f);
        Path facePlate = new Path();
        facePlate.moveTo(36, 27); facePlate.quadTo(50, 22, 64, 27);
        facePlate.cubicTo(69, 31, 70, 36, 68, 42); facePlate.quadTo(50, 48, 32, 42);
        facePlate.cubicTo(30, 36, 31, 31, 36, 27); facePlate.close();
        p.fill(c, facePlate, PAPER); p.ink(c, facePlate, 1.6f);
        fillCircle(c, p, 50, 36, 9.5f, PAPER_SHADE);
        p.inkCircle(c, 50, 36, 9.5f, 1.4f);
        // Simple hour marks and hands read at icon size, unlike a pair of eyes.
        line(c, p, 50, 28.5f, 50, 30.5f, INK, 1.2f);
        line(c, p, 57.5f, 36, 55.5f, 36, INK, 1.2f);
        line(c, p, 50, 43.5f, 50, 41.5f, INK, 1.2f);
        line(c, p, 42.5f, 36, 44.5f, 36, INK, 1.2f);
        line(c, p, 50, 36, 50, 31, COPPER, 1.8f);
        line(c, p, 50, 36, 54, 39, COPPER, 1.8f);
        fillCircle(c, p, 50, 36, 1.7f, accent);

        // Body plate, set-color alarm mark, and small screw details.
        Path chest = new Path();
        chest.moveTo(39, 52); chest.quadTo(50, 48, 61, 52);
        chest.lineTo(63, 67); chest.quadTo(50, 72, 37, 67); chest.close();
        p.fill(c, chest, METAL_DARK); p.ink(c, chest, 1.5f);
        Path bellMark = new Path();
        bellMark.moveTo(46, 61); bellMark.quadTo(46, 57, 50, 56);
        bellMark.quadTo(54, 57, 54, 61); bellMark.lineTo(56, 63);
        bellMark.lineTo(44, 63); bellMark.close();
        p.fill(c, bellMark, accent); p.ink(c, bellMark, 1f);
        line(c, p, 48, 65, 52, 65, secondary, 1.4f);
        fillCircle(c, p, 34, 49, 1.3f, COPPER);
        fillCircle(c, p, 66, 49, 1.3f, COPPER);

        // Two planted feet and a visible winding key at the rear.
        Path legs = new Path();
        legs.moveTo(41, 75); legs.lineTo(39, 84); legs.moveTo(59, 75); legs.lineTo(61, 84);
        p.stroke(c, legs, INK, 3.2f);
        Path feet = new Path();
        feet.moveTo(33, 86); feet.quadTo(39, 82, 45, 85); feet.lineTo(46, 89);
        feet.quadTo(39, 91, 32, 89); feet.close();
        p.fill(c, feet, COPPER); p.ink(c, feet, 1.6f);
        Path rightFoot = new Path();
        rightFoot.moveTo(55, 85); rightFoot.quadTo(61, 82, 67, 86);
        rightFoot.lineTo(68, 89); rightFoot.quadTo(61, 91, 54, 89); rightFoot.close();
        p.fill(c, rightFoot, COPPER); p.ink(c, rightFoot, 1.6f);
        line(c, p, 68, 58, 79, 58, INK, 2.4f);
        line(c, p, 79, 58, 83, 54, INK, 2.4f);
        line(c, p, 79, 58, 83, 62, INK, 2.4f);
        fillCircle(c, p, 68, 58, 3f, COPPER); p.inkCircle(c, 68, 58, 3f, 1.1f);
    }

    private static void line(Canvas c, Painter p, float x1, float y1,
                             float x2, float y2, int color, float width) {
        Path path = new Path(); path.moveTo(x1, y1); path.lineTo(x2, y2);
        p.stroke(c, path, color, width);
    }

    private static void fillCircle(Canvas c, Painter p, float x, float y, float radius, int color) {
        p.fill.setColor(color); p.fill.setShader(null);
        c.drawCircle(x, y, radius, p.fill);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int blend(int from, int to, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(Math.round(Color.red(from) * inverse + Color.red(to) * amount),
                Math.round(Color.green(from) * inverse + Color.green(to) * amount),
                Math.round(Color.blue(from) * inverse + Color.blue(to) * amount));
    }

    /** Paint helpers are private to avoid color/style state leaking between shapes. */
    private static final class Painter {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);

        Painter() {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        void fill(Canvas c, Path path, int color) {
            fill.setStyle(Paint.Style.FILL); fill.setColor(color); fill.setShader(null);
            c.drawPath(path, fill);
        }

        void ink(Canvas c, Path path, float width) { stroke(c, path, INK, width); }

        void stroke(Canvas c, Path path, int color, float width) {
            stroke.setStyle(Paint.Style.STROKE); stroke.setColor(color); stroke.setShader(null);
            stroke.setStrokeWidth(width); stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND); c.drawPath(path, stroke);
        }

        void inkCircle(Canvas c, float x, float y, float radius, float width) {
            stroke.setStyle(Paint.Style.STROKE); stroke.setColor(INK); stroke.setShader(null);
            stroke.setStrokeWidth(width); c.drawCircle(x, y, radius, stroke);
        }
    }
}
