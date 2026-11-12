package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Displays the transparent character artwork without recoloring its pixels.
 * A shared, downsampled bitmap cache serves the home and picker previews.
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

    private static final Bitmap[] BITMAP_CACHE = new Bitmap[Mascot.values().length];
    private static final Paint STATIC_BITMAP_PAINT = newBitmapPaint();
    private static final Paint STATIC_GLOW_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint bitmapPaint = newBitmapPaint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Mascot mascot = Mascot.DRAGON;
    private boolean mascotVisible = true;
    private int primaryOverride = Color.TRANSPARENT;
    private int secondaryOverride = Color.TRANSPARENT;
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

    /** Stable ids suitable for a separate preset/catalog model. */
    public static String[] supportedCharacterIds() {
        return SUPPORTED_CHARACTER_IDS.toArray(new String[0]);
    }

    public Mascot getMascot() { return mascot; }

    public void setMascot(Mascot value) {
        mascot = value == null ? Mascot.DRAGON : value;
        mascotVisible = true;
        cacheBitmap(getContext(), mascot);
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
     * Optional colors tint only the soft halo behind the art. The PNG itself
     * remains pixel-faithful. Pass Color.TRANSPARENT to follow the current palette.
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
        String name = MascotCatalog.get(value.id).label(getContext());
        if ("en".equals(language)) return name + " character illustration";
        if ("ko".equals(language)) return name + " 캐릭터 일러스트";
        return name + "のキャラクターイラスト";
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
                : blend(primary, Ui.SURFACE, .58f);
        int outline = outlineOverride != Color.TRANSPARENT ? outlineOverride : Ui.TEXT;
        drawCachedCharacter(canvas, mascot, box, primary, secondary, outline,
                bitmapPaint, glowPaint);
    }

    /**
     * Draws a character using the shared cache. Constructing a MascotArtView first
     * loads the requested artwork; callers that need a first-use draw can use the
     * Context overload below.
     */
    public static void drawCharacter(Canvas canvas, String characterId, RectF box, int accent) {
        Mascot selected = mascotForDrawing(characterId);
        if (selected == null) return;
        int secondary = blend(accent, Ui.SURFACE, .58f);
        drawCachedCharacter(canvas, selected, box, accent, secondary, Ui.TEXT,
                STATIC_BITMAP_PAINT, STATIC_GLOW_PAINT);
    }

    /** Context-aware form of the shared renderer, also usable before a view exists. */
    public static void drawCharacter(Context context, Canvas canvas, String characterId,
                                     RectF box, int accent) {
        Mascot selected = mascotForDrawing(characterId);
        if (selected == null) return;
        Bitmap bitmap = cacheBitmap(context, selected);
        int secondary = blend(accent, Ui.SURFACE, .58f);
        drawCharacterBitmap(canvas, bitmap, box, accent, secondary, Ui.TEXT,
                STATIC_BITMAP_PAINT, STATIC_GLOW_PAINT);
    }

    private static Mascot mascotForDrawing(String id) {
        if (id != null && "none".equalsIgnoreCase(id)) return null;
        return Mascot.fromId(id);
    }

    private static void drawCachedCharacter(Canvas canvas, Mascot selected, RectF box,
                                            int primary, int secondary, int outline,
                                            Paint bitmapPaint, Paint glowPaint) {
        drawCharacterBitmap(canvas, cachedBitmap(selected), box, primary, secondary, outline,
                bitmapPaint, glowPaint);
    }

    private static void drawCharacterBitmap(Canvas canvas, Bitmap bitmap, RectF box,
                                            int primary, int secondary, int outline,
                                            Paint bitmapPaint, Paint glowPaint) {
        if (canvas == null || bitmap == null || box == null
                || box.width() <= 0f || box.height() <= 0f) return;

        float size = Math.min(box.width(), box.height());
        int[] glowColors = {
                withAlpha(primary, 19),
                withAlpha(secondary, 10),
                withAlpha(outline, 0)
        };
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setShader(new RadialGradient(box.centerX(), box.centerY(), size * .5f,
                glowColors, new float[]{0f, .68f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(box.centerX(), box.centerY(), size * .5f, glowPaint);
        glowPaint.setShader(null);

        drawGroundShadow(canvas, box, size, secondary, glowPaint);

        float inset = size * .04f;
        float availableWidth = Math.max(0f, box.width() - inset * 2f);
        float availableHeight = Math.max(0f, box.height() - inset * 2f);
        float scale = Math.min(availableWidth / bitmap.getWidth(),
                availableHeight / bitmap.getHeight());
        float drawWidth = bitmap.getWidth() * scale;
        float drawHeight = bitmap.getHeight() * scale;
        float left = box.centerX() - drawWidth * .5f;
        float top = box.centerY() - drawHeight * .5f;
        RectF destination = new RectF(left, top, left + drawWidth, top + drawHeight);

        bitmapPaint.setStyle(Paint.Style.FILL);
        bitmapPaint.setShader(null);
        bitmapPaint.setColorFilter(null);
        bitmapPaint.setColor(Color.WHITE);
        bitmapPaint.setAlpha(255);
        bitmapPaint.setFilterBitmap(true);
        bitmapPaint.setDither(true);
        canvas.drawBitmap(bitmap, null, destination, bitmapPaint);
    }

    /** Draws a low-opacity, flattened accent shadow under the transparent artwork. */
    private static void drawGroundShadow(Canvas canvas, RectF box, float size,
                                        int accent, Paint paint) {
        float centerX = box.centerX();
        float centerY = box.bottom - size * .075f;
        float radius = size * .26f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(centerX, centerY, radius,
                new int[]{withAlpha(accent, 22), withAlpha(accent, 0)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        int save = canvas.save();
        canvas.scale(1f, .23f, centerX, centerY);
        canvas.drawCircle(centerX, centerY, radius, paint);
        canvas.restoreToCount(save);
        paint.setShader(null);
    }

    private static Bitmap cachedBitmap(Mascot mascot) {
        if (mascot == null) return null;
        synchronized (BITMAP_CACHE) {
            return BITMAP_CACHE[mascot.ordinal()];
        }
    }

    private static Bitmap cacheBitmap(Context context, Mascot mascot) {
        if (context == null || mascot == null) return null;
        synchronized (BITMAP_CACHE) {
            Bitmap cached = BITMAP_CACHE[mascot.ordinal()];
            if (cached != null && !cached.isRecycled()) return cached;

            BitmapFactory.Options options = new BitmapFactory.Options();
            // 1024px source art becomes 512px, enough for the 120dp hero on dense screens.
            options.inSampleSize = 2;
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded = BitmapFactory.decodeResource(context.getResources(),
                    drawableFor(mascot), options);
            BITMAP_CACHE[mascot.ordinal()] = decoded;
            return decoded;
        }
    }

    private static int drawableFor(Mascot mascot) {
        switch (mascot) {
            case FOX: return R.drawable.mascot_fox_premium_v2;
            case OWL: return R.drawable.mascot_owl_premium_v2;
            case ROBOT: return R.drawable.mascot_robot_premium_v2;
            default: return R.drawable.mascot_dragon_premium_v2;
        }
    }

    private static Paint newBitmapPaint() {
        return new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
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
}
