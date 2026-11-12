package jp.wakeguard.alarm;

import android.content.Context;
import android.content.res.Resources;
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
 * Hand-drawn field-guide illustrations for the existing mascot view.
 *
 * Art direction: irregular ink contours and natural pigments on a quiet paper
 * specimen plate. Palette accents stay on a small character accessory. There
 * is deliberately no glow, floating shadow, gradient, or raster effect.
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

    // Pigments are deliberately independent of the selected UI palette.
    private static final int INK = Color.rgb(48, 39, 31);
    private static final int PAPER = Color.rgb(247, 237, 211);
    private static final int PAPER_SHADE = Color.rgb(214, 195, 154);
    private static final int MAT_RULE = Color.argb(90, 48, 39, 31);
    private static final int LIZARD = Color.rgb(91, 117, 65);
    private static final int LIZARD_SHADE = Color.rgb(61, 82, 49);
    private static final int LIZARD_LIGHT = Color.rgb(151, 158, 101);
    private static final int FOX = Color.rgb(183, 83, 43);
    private static final int FOX_SHADE = Color.rgb(119, 52, 35);
    private static final int FOX_LIGHT = Color.rgb(214, 132, 80);
    private static final int OWL = Color.rgb(112, 78, 52);
    private static final int OWL_LIGHT = Color.rgb(164, 119, 70);
    private static final int UMBER_DARK = Color.rgb(77, 54, 38);
    private static final int AMBER = Color.rgb(176, 112, 50);
    private static final int COPPER = Color.rgb(150, 83, 48);
    private static final int BRASS = Color.rgb(168, 116, 47);
    private static final int BRASS_LIGHT = Color.rgb(204, 157, 78);
    private static final int STEEL = Color.rgb(103, 91, 76);

    private static final Art ART = new Art();
    private static final ThreadLocal<Painter> THREAD_PAINTER = new ThreadLocal<>();
    private final Painter painter = new Painter();
    private final RectF drawingBox = new RectF();
    private Mascot mascot = Mascot.DRAGON;
    private boolean mascotVisible = true;
    private int primaryOverride = Color.TRANSPARENT;
    private int outlineOverride = Color.TRANSPARENT;

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

    /** Stable ids suitable for the existing preset/catalog model. */
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

    /** The saved "none" selection intentionally draws nothing. */
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

    /** Palette colors stay on one accessory; the outline color is used only as a compact silhouette edge. */
    public void setMascotColors(int primary, int secondary, int outline) {
        primaryOverride = primary;
        outlineOverride = outline;
        invalidate();
    }

    public void clearMascotColors() {
        primaryOverride = Color.TRANSPARENT;
        outlineOverride = Color.TRANSPARENT;
        invalidate();
    }

    private String descriptionFor(Mascot value) {
        String name = MascotCatalog.get(value.id).label(getContext());
        String language = I18n.language(getContext());
        if ("en".equals(language)) return name + " field-guide illustration";
        if ("ko".equals(language)) return name + " 야생 도감 삽화";
        return name + "のフィールドガイド風イラスト";
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mascotVisible) return;
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0f || height <= 0f) return;
        drawingBox.set(getPaddingLeft(), getPaddingTop(),
                getPaddingLeft() + width, getPaddingTop() + height);
        int accent = primaryOverride != Color.TRANSPARENT ? primaryOverride : Ui.ACCENT;
        int contrastOutline = outlineOverride != Color.TRANSPARENT ? outlineOverride : Ui.TEXT;
        drawMascot(canvas, mascot, drawingBox, accent, contrastOutline,
                getResources().getDisplayMetrics().density, painter);
    }

    /** Draws the selected character for the existing static preview callers. */
    public static void drawCharacter(Canvas canvas, String characterId, RectF box, int accent) {
        Mascot selected = mascotForDrawing(characterId);
        if (selected == null) return;
        drawMascot(canvas, selected, box, accent, Ui.TEXT,
                Resources.getSystem().getDisplayMetrics().density, threadPainter());
    }

    /** Context-aware form retained for callers that draw before creating a view. */
    public static void drawCharacter(Context context, Canvas canvas, String characterId,
                                     RectF box, int accent) {
        Mascot selected = mascotForDrawing(characterId);
        if (selected == null) return;
        float density = context == null
                ? Resources.getSystem().getDisplayMetrics().density
                : context.getResources().getDisplayMetrics().density;
        drawMascot(canvas, selected, box, accent, Ui.TEXT, density, threadPainter());
    }

    private static Painter threadPainter() {
        Painter painter = THREAD_PAINTER.get();
        if (painter == null) {
            painter = new Painter();
            THREAD_PAINTER.set(painter);
        }
        return painter;
    }

    private static Mascot mascotForDrawing(String id) {
        if (id != null && "none".equalsIgnoreCase(id)) return null;
        return Mascot.fromId(id);
    }

    private static void drawMascot(Canvas canvas, Mascot selected, RectF box,
                                   int accent, int contrastOutline, float density, Painter p) {
        if (canvas == null || selected == null || box == null
                || box.width() <= 0f || box.height() <= 0f) return;
        int save = canvas.save();
        canvas.clipRect(box);
        float size = Math.min(box.width(), box.height());
        float unitScale = size / 100f;
        boolean fullDetails = density > 0f && size / density >= 64f;
        p.configure(unitScale, fullDetails, density);
        canvas.translate(box.centerX() - size * .5f, box.centerY() - size * .5f);
        canvas.scale(unitScale, unitScale);
        if (fullDetails) drawPlate(canvas, p);
        int figureSave = canvas.save();
        // Small catalog previews are artwork, not a framed sticker: give the
        // silhouette more room and let its card/background show through.
        float inset = fullDetails ? 5f : 1f;
        float figureScale = fullDetails ? .9f : .98f;
        canvas.translate(inset, inset);
        canvas.scale(figureScale, figureScale);
        p.configure(unitScale * figureScale, fullDetails, density);
        if (!fullDetails) drawCompactContrastEdge(canvas, selected, contrastOutline, p);
        switch (selected) {
            case FOX: drawFox(canvas, accent, fullDetails, p); break;
            case OWL: drawOwl(canvas, accent, fullDetails, p); break;
            case ROBOT: drawRobot(canvas, accent, fullDetails, p); break;
            default: drawDragon(canvas, accent, fullDetails, p); break;
        }
        canvas.restoreToCount(figureSave);
        canvas.restoreToCount(save);
    }

    /** One theme-aware outer contour keeps small figures legible without outlining every part. */
    private static void drawCompactContrastEdge(Canvas canvas, Mascot selected, int color, Painter p) {
        switch (selected) {
            case FOX:
                p.strokeDp(canvas, ART.foxCompactSilhouette, color, 5f);
                break;
            case OWL:
                p.strokeDp(canvas, ART.owlCompactSilhouette, color, 5f);
                p.strokeDp(canvas, ART.owlBranch, color, 8.5f);
                p.strokeDp(canvas, ART.owlFeet, color, 8.5f);
                break;
            case ROBOT:
                p.strokeDp(canvas, ART.robotCompactSilhouette, color, 5f);
                p.strokeDp(canvas, ART.robotFarArm, color, 8.5f);
                p.strokeDp(canvas, ART.robotLegs, color, 8.5f);
                p.strokeDp(canvas, ART.robotWindingKey, color, 8.5f);
                break;
            default:
                p.strokeDp(canvas, ART.dragonCompactSilhouette, color, 5f);
                p.strokeDp(canvas, ART.dragonTail, color, 8.5f);
                p.strokeDp(canvas, ART.dragonTailTip, color, 8.5f);
                break;
        }
    }

    /** A restrained, slightly irregular field-note plate shared by all species. */
    private static void drawPlate(Canvas canvas, Painter p) {
        p.fill(canvas, ART.plate, PAPER);
        p.hairline(canvas, ART.plate, MAT_RULE);
        p.hairline(canvas, ART.plateInnerRule, MAT_RULE);
    }

    /** Long-necked mountain dragon with a fan frill, pale belly, and curled tail. */
    private static void drawDragon(Canvas c, int accent, boolean fullDetails, Painter p) {
        // Back leg and tail sit behind the compact body.
        p.stroke(c, ART.dragonTail, INK, 6.0f);
        p.stroke(c, ART.dragonTail, LIZARD_SHADE, 3.8f);
        p.stroke(c, ART.dragonTailTip, INK, 4.2f);
        p.stroke(c, ART.dragonTailTip, LIZARD_LIGHT, 2.6f);
        p.shape(c, ART.dragonFrill, LIZARD_SHADE, 4.2f);
        if (fullDetails) p.stroke(c, ART.dragonFrillRibs, LIZARD_LIGHT, 2.1f);
        p.shape(c, ART.dragonBackFoot, LIZARD_SHADE, 4.4f);
        p.shape(c, ART.dragonBody, LIZARD, 5.2f);
        p.fill(c, ART.dragonBelly, PAPER);
        p.stroke(c, ART.dragonBelly, PAPER_SHADE, 1.2f);
        if (fullDetails) p.stroke(c, ART.dragonBellyMarks, PAPER_SHADE, 1.8f);
        p.shape(c, ART.dragonFarFoot, LIZARD_SHADE, 3.8f);
        p.shape(c, ART.dragonNearFoot, LIZARD, 3.8f);

        p.shape(c, ART.dragonNeck, LIZARD, 4.4f);
        if (fullDetails) p.stroke(c, ART.dragonNeckStripe, LIZARD_LIGHT, 2.6f);
        p.shape(c, ART.dragonHead, LIZARD, 4.6f);
        p.fill(c, ART.dragonJaw, LIZARD_LIGHT);
        p.ink(c, ART.dragonJaw, 1.5f);
        p.dot(c, 76, 25, 3.4f, INK);
        p.dot(c, 76, 25, 2.8f, PAPER);
        p.dot(c, 76.35f, 25, 1.0f, INK);
        p.stroke(c, ART.dragonMouth, INK, 1.6f);

        // The only set-colored mark is a tiny field-tag on the chest.
        p.shape(c, ART.dragonTag, accent, 4.5f);
        if (fullDetails) p.stroke(c, ART.dragonTagRidge, PAPER, 2.6f);
        p.dot(c, 64, 62, 1.4f, PAPER);
    }

    /** Fox in profile, with a broad brush tail and pale tail-tip/bib. */
    private static void drawFox(Canvas c, int accent, boolean fullDetails, Painter p) {
        p.shape(c, ART.foxTail, FOX, 5.2f);
        p.shape(c, ART.foxTailTip, PAPER, 4.5f);
        if (fullDetails) p.stroke(c, ART.foxTailHatch, FOX_SHADE, 2.0f);
        p.shape(c, ART.foxBody, FOX, 5.0f);
        p.shape(c, ART.foxFarLeg, FOX_SHADE, 3.7f);
        p.shape(c, ART.foxNearLeg, FOX, 3.7f);
        p.fill(c, ART.foxBib, PAPER);
        p.stroke(c, ART.foxBib, PAPER_SHADE, 1.3f);
        p.ink(c, ART.foxBib, 2.6f);
        p.fill(c, ART.foxFarEar, FOX_SHADE);
        p.ink(c, ART.foxFarEar, 2.3f);
        if (fullDetails) p.stroke(c, ART.foxFarEarFold, FOX_LIGHT, 2.1f);
        p.fill(c, ART.foxNearEar, FOX);
        p.ink(c, ART.foxNearEar, 2.3f);
        p.fill(c, ART.foxEarInset, PAPER_SHADE);
        p.shape(c, ART.foxHead, FOX, 4.6f);
        p.fill(c, ART.foxCheek, PAPER);
        p.ink(c, ART.foxCheek, 1.5f);
        p.dot(c, 47, 31, 2.0f, INK);
        p.dot(c, 20, 42, 2.4f, INK);
        if (fullDetails) {
            p.stroke(c, ART.foxMuzzleMark, PAPER_SHADE, 1.8f);
            p.stroke(c, ART.foxWhisker, INK, 1.8f);
            p.stroke(c, ART.foxTailHatch2, FOX_SHADE, 1.8f);
        }

        // One folded neckerchief is the sole appearance-color accent.
        p.shape(c, ART.foxScarf, accent, 4.5f);
    }

    /** Three-quarter perched owl; unequal eyes, shoulders, and tufts give a turn. */
    private static void drawOwl(Canvas c, int accent, boolean fullDetails, Painter p) {
        p.stroke(c, ART.owlBranch, INK, 4.7f);
        p.stroke(c, ART.owlBranch, COPPER, 2.6f);
        p.shape(c, ART.owlBody, OWL, 5.0f);
        p.shape(c, ART.owlFarWing, OWL_LIGHT, 3.7f);
        p.shape(c, ART.owlNearWing, UMBER_DARK, 3.7f);
        if (fullDetails) {
            p.stroke(c, ART.owlWingFeather1, PAPER_SHADE, 1.8f);
            p.stroke(c, ART.owlWingFeather2, PAPER_SHADE, 1.8f);
            p.stroke(c, ART.owlBreastBars, PAPER_SHADE, 1.8f);
        }
        p.shape(c, ART.owlFace, PAPER, 4.0f);
        if (fullDetails) p.stroke(c, ART.owlFaceFold, PAPER_SHADE, 1.8f);
        // Near eye is larger and lower; the far eye is partly hidden by the brow.
        p.fill(c, ART.owlNearDisk, PAPER_SHADE);
        p.ink(c, ART.owlNearDisk, 1.4f);
        p.fill(c, ART.owlFarDisk, PAPER_SHADE);
        p.ink(c, ART.owlFarDisk, 1.3f);
        p.dot(c, 43, 41, 4.2f, AMBER);
        p.dot(c, 61, 38, 3.0f, AMBER);
        p.dot(c, 44, 41, 1.8f, INK);
        p.dot(c, 61.5f, 38, 1.35f, INK);
        p.fill(c, ART.owlBrow, OWL);
        p.ink(c, ART.owlBrow, 1.1f);
        p.fill(c, ART.owlBeak, COPPER);
        p.ink(c, ART.owlBeak, 1.3f);
        p.stroke(c, ART.owlFeet, COPPER, 2.5f);

        // The small enamel pin is the only set-colored element.
        p.dot(c, 57, 69, 4.1f, INK);
        p.dot(c, 57, 69, 2.8f, accent);
        p.stroke(c, ART.owlPinMark, PAPER, 1.2f);
    }

    /** Hand-built alarm-clock robot with a lopsided bell, dial, and winding key. */
    private static void drawRobot(Canvas c, int accent, boolean fullDetails, Painter p) {
        p.stroke(c, ART.robotFarArm, INK, 4.8f);
        p.stroke(c, ART.robotFarArm, STEEL, 2.8f);
        p.shape(c, ART.robotBody, BRASS, 5.0f);
        p.shape(c, ART.robotChest, STEEL, 3.8f);
        p.fill(c, ART.robotHead, BRASS);
        p.ink(c, ART.robotHead, 2.4f);
        p.fill(c, ART.robotBellLeft, BRASS_LIGHT);
        p.ink(c, ART.robotBellLeft, 2.1f);
        p.fill(c, ART.robotBellRight, BRASS_LIGHT);
        p.ink(c, ART.robotBellRight, 2.0f);
        p.stroke(c, ART.robotBellStem, INK, 2.8f);
        p.fill(c, ART.robotDial, PAPER);
        p.ink(c, ART.robotDial, 2.6f);
        p.stroke(c, ART.robotTicks, INK, 2.6f);
        if (fullDetails) p.stroke(c, ART.robotExtraTicks, INK, 2.6f);
        p.stroke(c, ART.robotHands, INK, 2.8f);
        p.dot(c, 60, 36, 2.3f, COPPER);
        p.fill(c, ART.robotNearArm, STEEL);
        p.ink(c, ART.robotNearArm, 2.1f);
        p.stroke(c, ART.robotWindingKey, INK, 5.0f);
        p.stroke(c, ART.robotWindingKey, COPPER, 2.8f);
        p.dot(c, 84, 69, 3.0f, COPPER);
        p.stroke(c, ART.robotLegs, INK, 5.8f);
        p.stroke(c, ART.robotLegs, STEEL, 3.8f);

        // A single small pennant carries the active palette.
        p.shape(c, ART.robotPennant, accent, 4.2f);
    }

    private static Path path(float... xy) {
        Path path = new Path();
        if (xy.length < 4) return path;
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        path.close();
        return path;
    }

    private static final class Painter {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float unitScale = 1f;
        private float density = 1f;
        private float minimumStroke = 1.6f;

        Painter() {
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        void configure(float unitScale, boolean fullDetails, float density) {
            this.unitScale = Math.max(.01f, unitScale);
            this.density = Math.max(.1f, density);
            minimumStroke = fullDetails ? 1.6f : 2.6f;
        }

        void fill(Canvas canvas, Path path, int color) {
            fill.setColor(color);
            canvas.drawPath(path, fill);
        }

        void ink(Canvas canvas, Path path, float width) { stroke(canvas, path, INK, width); }

        void stroke(Canvas canvas, Path path, int color, float width) {
            stroke.setColor(color);
            stroke.setStrokeWidth(Math.max(minimumStroke,
                    Math.max(width, 1f / unitScale)));
            canvas.drawPath(path, stroke);
        }

        void strokeDp(Canvas canvas, Path path, int color, float widthDp) {
            stroke(canvas, path, color, widthDp * density / unitScale);
        }

        void hairline(Canvas canvas, Path path, int color) {
            stroke.setColor(color);
            stroke.setStrokeWidth(Math.max(.01f, 1f / unitScale));
            canvas.drawPath(path, stroke);
        }

        /** Pigment and ink only; adjacent parts do not get a repeated sticker halo. */
        void shape(Canvas canvas, Path path, int pigment, float keylineWidth) {
            float inkWidth = Math.max(minimumStroke, keylineWidth * .58f);
            fill(canvas, path, pigment);
            stroke(canvas, path, INK, inkWidth);
        }

        void dot(Canvas canvas, float x, float y, float radius, int color) {
            fill.setColor(color);
            // Clamp in device pixels rather than normalized art units. A fixed
            // 1.8-unit floor made small pupils as large as their outer disks.
            float minimumRadius = .7f / unitScale;
            canvas.drawCircle(x, y, Math.max(radius, minimumRadius), fill);
        }
    }

    /** All normalized geometry is allocated once and remains inside the 0..100 plate. */
    private static final class Art {
        final Path plate = new Path();
        final Path plateInnerRule = new Path();
        final Path dragonCompactSilhouette = new Path();
        final Path dragonTail = new Path();
        final Path dragonTailTip = new Path();
        final Path dragonBackFoot = path(37, 76, 48, 77, 50, 89, 35, 90, 32, 85);
        final Path dragonBody = new Path();
        final Path dragonBelly = new Path();
        final Path dragonBellyMarks = new Path();
        final Path dragonFarFoot = path(48, 78, 59, 78, 62, 88, 47, 90);
        final Path dragonNearFoot = path(62, 76, 75, 78, 77, 87, 63, 89);
        final Path dragonFrill = new Path();
        final Path dragonFrillRibs = new Path();
        final Path dragonNeck = new Path();
        final Path dragonNeckStripe = new Path();
        final Path dragonHead = new Path();
        final Path dragonJaw = path(77, 31, 91, 33, 88, 38, 77, 37);
        final Path dragonMouth = path(83, 35, 88, 35);
        final Path dragonTag = path(61, 61, 68, 60, 70, 67, 64, 70);
        final Path dragonTagRidge = path(62, 66, 64, 63, 66, 65, 68, 62);

        final Path foxTail = new Path();
        final Path foxCompactSilhouette = new Path();
        final Path foxTailTip = path(12, 83, 14, 88, 20, 86, 26, 81, 22, 77);
        final Path foxTailHatch = new Path();
        final Path foxTailHatch2 = new Path();
        final Path foxBody = new Path();
        final Path foxFarLeg = path(46, 76, 56, 76, 55, 88, 43, 89);
        final Path foxNearLeg = path(61, 74, 72, 75, 74, 88, 61, 89);
        final Path foxBib = new Path();
        final Path foxFarEar = path(54, 28, 49, 20, 42, 14, 39, 10, 45, 12, 49, 17, 58, 29);
        final Path foxFarEarFold = new Path();
        final Path foxNearEar = path(61, 25, 72, 8, 76, 31, 71, 35);
        final Path foxEarInset = path(69, 25, 72, 15, 73, 28);
        final Path foxHead = new Path();
        final Path foxCheek = new Path();
        final Path foxMuzzleMark = new Path();
        final Path foxWhisker = new Path();
        final Path foxScarf = path(48, 47, 61, 50, 69, 45, 65, 55, 56, 59, 48, 53);

        final Path owlBranch = new Path();
        final Path owlCompactSilhouette = new Path();
        final Path owlBody = new Path();
        final Path owlFarWing = new Path();
        final Path owlNearWing = new Path();
        final Path owlWingFeather1 = new Path();
        final Path owlWingFeather2 = new Path();
        final Path owlBreastBars = new Path();
        final Path owlFace = new Path();
        final Path owlFaceFold = new Path();
        final Path owlNearDisk = new Path();
        final Path owlFarDisk = new Path();
        final Path owlBrow = new Path();
        final Path owlBeak = path(51, 45, 57, 51, 50, 52);
        final Path owlFeet = new Path();
        final Path owlPinMark = path(54, 69, 60, 69);

        final Path robotFarArm = path(38, 58, 27, 68, 29, 76);
        final Path robotCompactSilhouette = new Path();
        final Path robotBody = new Path();
        final Path robotChest = path(48, 57, 72, 59, 75, 77, 47, 78);
        final Path robotHead = new Path();
        final Path robotBellLeft = new Path();
        final Path robotBellRight = new Path();
        final Path robotBellStem = path(41, 19, 38, 14, 31, 12);
        final Path robotDial = new Path();
        final Path robotTicks = new Path();
        final Path robotExtraTicks = new Path();
        final Path robotHands = new Path();
        final Path robotNearArm = new Path();
        final Path robotWindingKey = new Path();
        final Path robotLegs = new Path();
        final Path robotPennant = path(51, 62, 58, 64, 56, 72, 52, 69);

        Art() {
            plate.moveTo(12, 4); plate.lineTo(88, 5); plate.quadTo(96, 5, 96, 13);
            plate.lineTo(95, 87); plate.quadTo(95, 95, 87, 96); plate.lineTo(12, 95);
            plate.quadTo(4, 95, 4, 87); plate.lineTo(5, 13); plate.quadTo(5, 5, 12, 4);
            plate.close();
            plateInnerRule.moveTo(10, 11); plateInnerRule.quadTo(48, 10, 90, 11);
            plateInnerRule.lineTo(89, 89); plateInnerRule.quadTo(50, 90, 10, 89);
            plateInnerRule.close();
            owlBranch.moveTo(16, 81); owlBranch.quadTo(50, 82, 88, 77);
            robotHands.moveTo(60, 36); robotHands.lineTo(50, 36);
            robotHands.moveTo(60, 36); robotHands.lineTo(56.3f, 44.3f);
            robotWindingKey.moveTo(75, 56); robotWindingKey.cubicTo(81, 52, 87, 55, 85, 60);
            robotWindingKey.cubicTo(83, 66, 76, 64, 79, 59);
            dragonTail.moveTo(43, 73); dragonTail.cubicTo(32, 78, 28, 90, 18, 88);
            dragonTail.cubicTo(10, 85, 13, 74, 20, 72); dragonTail.cubicTo(27, 71, 28, 78, 23, 80);
            dragonTailTip.moveTo(13, 75); dragonTailTip.quadTo(10, 82, 17, 85);
            dragonBody.moveTo(34, 73); dragonBody.cubicTo(30, 62, 34, 48, 43, 43);
            dragonBody.cubicTo(52, 37, 68, 43, 71, 57); dragonBody.cubicTo(74, 71, 64, 82, 51, 84);
            dragonBody.cubicTo(43, 85, 37, 81, 34, 73); dragonBody.close();
            dragonBelly.moveTo(52, 48); dragonBelly.cubicTo(59, 48, 63, 55, 62, 64);
            dragonBelly.lineTo(59, 77); dragonBelly.quadTo(52, 81, 45, 76);
            dragonBelly.cubicTo(49, 66, 48, 57, 52, 48); dragonBelly.close();
            dragonBellyMarks.moveTo(49, 58); dragonBellyMarks.quadTo(54, 60, 60, 58);
            dragonBellyMarks.moveTo(48, 65); dragonBellyMarks.quadTo(54, 67, 61, 65);
            dragonBellyMarks.moveTo(47, 72); dragonBellyMarks.quadTo(53, 74, 60, 72);
            // Broad neck frill: a specific anatomical cue instead of generic bat wings/horns.
            dragonFrill.moveTo(66, 30); dragonFrill.cubicTo(58, 29, 54, 23, 51, 15);
            dragonFrill.lineTo(60, 18); dragonFrill.lineTo(58, 8); dragonFrill.lineTo(67, 15);
            dragonFrill.lineTo(71, 6); dragonFrill.lineTo(75, 16); dragonFrill.lineTo(84, 13);
            dragonFrill.lineTo(78, 21); dragonFrill.cubicTo(79, 26, 74, 30, 66, 30);
            dragonFrill.close();
            dragonFrillRibs.moveTo(66, 27); dragonFrillRibs.lineTo(56, 17);
            dragonFrillRibs.moveTo(67, 25); dragonFrillRibs.lineTo(63, 13);
            dragonFrillRibs.moveTo(70, 24); dragonFrillRibs.lineTo(72, 12);
            dragonFrillRibs.moveTo(73, 25); dragonFrillRibs.lineTo(80, 17);
            dragonNeck.moveTo(46, 61); dragonNeck.cubicTo(47, 52, 53, 46, 59, 41);
            dragonNeck.cubicTo(66, 35, 68, 29, 68, 26); dragonNeck.cubicTo(73, 25, 77, 28, 77, 32);
            dragonNeck.cubicTo(76, 42, 68, 49, 63, 53); dragonNeck.cubicTo(60, 59, 63, 66, 66, 71);
            dragonNeck.quadTo(58, 77, 52, 70); dragonNeck.cubicTo(48, 67, 46, 64, 46, 61); dragonNeck.close();
            dragonNeckStripe.moveTo(56, 58); dragonNeckStripe.quadTo(62, 60, 66, 58);
            dragonHead.moveTo(64, 27); dragonHead.quadTo(64, 19, 71, 18);
            dragonHead.cubicTo(77, 17, 82, 21, 84, 24); dragonHead.lineTo(92, 24);
            dragonHead.quadTo(94, 25, 92, 28); dragonHead.lineTo(82, 30);
            dragonHead.quadTo(77, 35, 70, 33); dragonHead.quadTo(65, 32, 64, 27);
            dragonHead.close();
            dragonMouth.moveTo(83, 31); dragonMouth.quadTo(87, 32, 91, 31);

            foxTail.moveTo(56, 68); foxTail.cubicTo(44, 62, 39, 70, 30, 77);
            foxTail.cubicTo(20, 84, 14, 91, 11, 85); foxTail.cubicTo(8, 78, 13, 68, 22, 62); foxTail.cubicTo(31, 56, 42, 58, 52, 62);
            foxTail.cubicTo(56, 63, 59, 61, 62, 60); foxTail.close();
            foxTailHatch.moveTo(21, 64); foxTailHatch.quadTo(29, 66, 35, 73);
            foxTailHatch2.moveTo(17, 70); foxTailHatch2.quadTo(21, 76, 28, 79);
            foxFarEarFold.moveTo(42, 14); foxFarEarFold.quadTo(47, 17, 50, 22);
            foxBody.moveTo(44, 78); foxBody.cubicTo(35, 69, 38, 54, 46, 47);
            foxBody.cubicTo(54, 40, 67, 46, 71, 57); foxBody.cubicTo(76, 70, 68, 83, 58, 86);
            foxBody.quadTo(48, 88, 44, 78); foxBody.close();
            foxBib.moveTo(48, 51); foxBib.quadTo(56, 47, 63, 54); foxBib.cubicTo(67, 63, 64, 76, 58, 82);
            foxBib.quadTo(51, 83, 47, 77); foxBib.cubicTo(52, 67, 51, 59, 48, 51); foxBib.close();
            foxHead.moveTo(45, 37); foxHead.cubicTo(43, 28, 48, 19, 57, 18);
            foxHead.cubicTo(67, 17, 72, 25, 73, 31); foxHead.lineTo(87, 37);
            foxHead.quadTo(92, 39, 87, 42); foxHead.lineTo(77, 42);
            foxHead.cubicTo(69, 49, 55, 47, 48, 42); foxHead.quadTo(44, 40, 45, 37); foxHead.close();
            foxCheek.moveTo(49, 36); foxCheek.quadTo(58, 31, 67, 36); foxCheek.quadTo(75, 41, 85, 39);
            foxCheek.quadTo(76, 46, 66, 42); foxCheek.quadTo(57, 45, 49, 41); foxCheek.close();
            foxMuzzleMark.moveTo(76, 35); foxMuzzleMark.quadTo(81, 38, 86, 38);
            foxWhisker.moveTo(75, 42); foxWhisker.lineTo(69, 46); foxWhisker.moveTo(79, 43); foxWhisker.lineTo(75, 48);

            owlBody.moveTo(35, 70); owlBody.cubicTo(28, 59, 28, 43, 35, 33);
            owlBody.lineTo(33, 21); owlBody.quadTo(42, 24, 48, 28); owlBody.quadTo(56, 21, 69, 25);
            owlBody.lineTo(66, 35); owlBody.cubicTo(77, 49, 73, 67, 63, 77);
            owlBody.cubicTo(52, 84, 40, 81, 35, 70); owlBody.close();
            owlFarWing.moveTo(37, 42); owlFarWing.cubicTo(31, 49, 34, 65, 43, 74);
            owlFarWing.quadTo(49, 69, 51, 63); owlFarWing.quadTo(43, 53, 47, 42); owlFarWing.close();
            owlNearWing.moveTo(64, 40); owlNearWing.cubicTo(76, 43, 76, 62, 66, 73);
            owlNearWing.quadTo(59, 72, 56, 65); owlNearWing.quadTo(64, 55, 58, 43); owlNearWing.close();
            owlWingFeather1.moveTo(37, 54); owlWingFeather1.quadTo(43, 60, 46, 67);
            owlWingFeather2.moveTo(68, 52); owlWingFeather2.quadTo(64, 60, 61, 67);
            owlBreastBars.moveTo(46, 61); owlBreastBars.quadTo(52, 63, 59, 61);
            owlBreastBars.moveTo(45, 68); owlBreastBars.quadTo(52, 70, 60, 68);
            owlFace.moveTo(36, 34); owlFace.quadTo(40, 28, 48, 31); owlFace.quadTo(58, 25, 67, 33);
            owlFace.cubicTo(72, 45, 66, 55, 54, 58); owlFace.cubicTo(42, 56, 36, 47, 36, 34); owlFace.close();
            owlFaceFold.moveTo(53, 31); owlFaceFold.quadTo(55, 40, 53, 51);
            owlNearDisk.moveTo(37, 39); owlNearDisk.quadTo(43, 33, 49, 39); owlNearDisk.quadTo(48, 48, 42, 49); owlNearDisk.close();
            owlFarDisk.moveTo(54, 37); owlFarDisk.quadTo(61, 32, 66, 38); owlFarDisk.quadTo(64, 45, 58, 46); owlFarDisk.close();
            owlBrow.moveTo(36, 37); owlBrow.quadTo(42, 31, 49, 37);
            owlBrow.moveTo(54, 36); owlBrow.quadTo(61, 31, 67, 37);
            owlFeet.moveTo(42, 73); owlFeet.quadTo(39, 79, 33, 79); owlFeet.moveTo(43, 77); owlFeet.lineTo(49, 78);
            owlFeet.moveTo(62, 73); owlFeet.quadTo(64, 79, 71, 78); owlFeet.moveTo(62, 77); owlFeet.lineTo(56, 79);

            robotBody.moveTo(42, 54); robotBody.lineTo(71, 53); robotBody.lineTo(78, 62);
            robotBody.lineTo(75, 81); robotBody.lineTo(46, 80); robotBody.lineTo(40, 69); robotBody.close();
            robotHead.moveTo(44, 18); robotHead.lineTo(73, 20); robotHead.lineTo(80, 27);
            robotHead.lineTo(77, 49); robotHead.lineTo(68, 55); robotHead.lineTo(43, 52); robotHead.lineTo(39, 43);
            robotHead.lineTo(40, 25); robotHead.close();
            robotBellLeft.moveTo(42, 18); robotBellLeft.quadTo(31, 18, 29, 11);
            robotBellLeft.quadTo(39, 7, 48, 13); robotBellLeft.close();
            robotBellRight.moveTo(66, 19); robotBellRight.quadTo(73, 16, 81, 18);
            robotBellRight.quadTo(82, 22, 74, 25); robotBellRight.close();
            robotDial.addCircle(60, 36, 10, Path.Direction.CW);
            robotTicks.moveTo(60, 27); robotTicks.lineTo(60, 29); robotTicks.moveTo(68, 36); robotTicks.lineTo(66, 36);
            robotTicks.moveTo(60, 45); robotTicks.lineTo(60, 43); robotTicks.moveTo(51, 36); robotTicks.lineTo(53, 36);
            robotExtraTicks.moveTo(65, 28); robotExtraTicks.lineTo(64, 30);
            robotExtraTicks.moveTo(68, 42); robotExtraTicks.lineTo(66, 41);
            robotExtraTicks.moveTo(55, 44); robotExtraTicks.lineTo(56, 42);
            robotExtraTicks.moveTo(52, 30); robotExtraTicks.lineTo(54, 31);
            robotExtraTicks.moveTo(67, 30); robotExtraTicks.lineTo(66, 32);
            robotExtraTicks.moveTo(67, 41); robotExtraTicks.lineTo(65, 40);
            robotExtraTicks.moveTo(53, 41); robotExtraTicks.lineTo(55, 40);
            robotExtraTicks.moveTo(53, 31); robotExtraTicks.lineTo(55, 32);
            robotNearArm.moveTo(73, 60); robotNearArm.lineTo(84, 66); robotNearArm.lineTo(78, 76); robotNearArm.close();
            robotLegs.moveTo(51, 80); robotLegs.lineTo(49, 89); robotLegs.lineTo(44, 91);
            robotLegs.moveTo(68, 80); robotLegs.lineTo(71, 89); robotLegs.lineTo(78, 90);

            addUnion(dragonCompactSilhouette, dragonFrill, dragonBackFoot, dragonBody,
                    dragonFarFoot, dragonNearFoot, dragonNeck, dragonHead, dragonJaw);
            addUnion(foxCompactSilhouette, foxTail, foxTailTip, foxBody, foxFarLeg,
                    foxNearLeg, foxFarEar, foxNearEar, foxHead);
            addUnion(owlCompactSilhouette, owlBody, owlFarWing, owlNearWing);
            addUnion(robotCompactSilhouette, robotBody, robotHead, robotBellLeft,
                    robotBellRight, robotNearArm);
        }

        private static void addUnion(Path target, Path... parts) {
            for (Path part : parts) {
                if (target.isEmpty()) {
                    target.set(part);
                    continue;
                }
                Path combined = new Path();
                if (combined.op(target, part, Path.Op.UNION)) target.set(combined);
                else target.addPath(part);
            }
        }
    }
}
