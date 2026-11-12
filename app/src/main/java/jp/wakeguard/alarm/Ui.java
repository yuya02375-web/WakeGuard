package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * IGNIDO Dawn Ink design system.
 * Paper-like surfaces with emphasis colors derived from the selected palette.
 */
public final class Ui {
    // Mutable at runtime so the selected palette is shared by every Java-built screen.
    public static volatile int BG = 0xFFF6F2EB;
    public static volatile int BG_2 = 0xFFEDE5DA;
    public static volatile int SURFACE = 0xFFFFFCF7;
    public static volatile int SURFACE_2 = 0xFFF2EAE1;
    public static volatile int SURFACE_3 = 0xFFE8DED2;
    public static volatile int BORDER = 0xFFD4C8BA;
    public static volatile int BORDER_HOT = 0x70375164;
    public static volatile int ACCENT = 0xFF3E5C70;
    public static volatile int ACCENT_2 = 0xFF375164;
    public static volatile int CYAN = 0xFF277C83;
    public static volatile int BLUE = 0xFF3E66A0;
    public static volatile int VIOLET = 0xFF71599C;
    public static volatile int PINK = 0xFFAA526C;
    public static volatile int TEXT = 0xFF252E36;
    public static volatile int MUTED = 0xFF56636D;
    public static volatile int MUTED_2 = 0xFF5B5F69;
    public static volatile int SUCCESS = 0xFF2E745A;
    public static volatile int DANGER = 0xFFAD3F36;
    public static volatile int HERO_START = 0xFF3E5C70;
    public static volatile int HERO_END = 0xFF375164;
    public static volatile int HERO_TEXT = 0xFFFFFFFF;
    public static volatile int HORIZON_LINE = 0x66333B43;
    public static volatile int NAV_HIGHLIGHT = 0xFFDDDDD9;
    public static volatile int ON_ACCENT = 0xFFFFFFFF;
    public static volatile boolean DARK_THEME = false;
    public static volatile float CORNER_SCALE = 1f;
    public static volatile float UI_CORNER_SCALE = 1f;
    public static volatile float UI_DENSITY_SCALE = 1f;
    public static volatile String LAYOUT_MODE = "list";
    public static volatile String NAV_MODE = "pill";
    public static volatile String UI_CARD_MODE = "outlined";
    public static volatile String DESIGN_PATTERN = "none";
    public static volatile String DESIGN_FONT = "sans";
    public static volatile String DESIGN_CARD = "flat";
    public static volatile float DESIGN_LETTER_SPACING = 0f;
    private Ui() {}

    public static void applyThemePalette(AppThemes.Theme t) {
        if (t == null) return;
        BG=t.bg; BG_2=t.bg2; SURFACE=t.surface; SURFACE_2=t.surface2; SURFACE_3=t.surface3;
        BORDER=t.border; BORDER_HOT=t.borderHot; ACCENT=t.accent; ACCENT_2=t.accent2;
        CYAN=t.cyan; BLUE=t.blue; VIOLET=t.violet; PINK=t.pink; TEXT=t.text;
        MUTED=t.muted; MUTED_2=t.muted2; SUCCESS=t.success; DANGER=t.danger;
        HERO_START=t.heroStart; HERO_END=t.heroEnd; HERO_TEXT=t.heroText;
        HORIZON_LINE=t.horizonLine; NAV_HIGHLIGHT=t.navHighlight; ON_ACCENT=t.onAccent;
        // Color palettes are independent of geometry and typography.
        DARK_THEME=t.dark;
    }

    public static void applyUiProfile(UiProfiles.Design profile) {
        if (profile == null) return;
        UI_CORNER_SCALE = clamp(profile.cornerScale, .5f, 1.5f);
        UI_DENSITY_SCALE = clamp(profile.densityScale, .82f, 1.2f);
        LAYOUT_MODE = safe(profile.layoutMode, "list");
        NAV_MODE = safe(profile.navMode, "pill");
        UI_CARD_MODE = safe(profile.cardMode, "outlined");
        CORNER_SCALE = UI_CORNER_SCALE;
    }

    public static void applyVisualDesign(VisualDesigns.Design design) {
        if (design == null) return;
        DESIGN_PATTERN = safe(design.pattern, "none");
        DESIGN_FONT = safe(design.fontFamily, "sans");
        DESIGN_CARD = safe(design.cardTreatment, "flat");
        DESIGN_LETTER_SPACING = Math.max(-.025f, Math.min(.08f, design.letterSpacing));
        CORNER_SCALE = UI_CORNER_SCALE * clamp(design.cornerScale, .06f, 1.4f);
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }

    public static int dp(Activity a, int v) {
        float scale = UI_DENSITY_SCALE;
        // Preserve common control/tap-target dimensions when a compact profile is selected.
        if (v >= 44 && v <= 56 && scale < 1f) scale = 1f;
        return Math.round(v * a.getResources().getDisplayMetrics().density * scale);
    }

    private static int touchDp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    private static Typeface designTypeface(int style) {
        String family;
        if ("serif".equals(DESIGN_FONT)) family = "serif";
        else if ("mono".equals(DESIGN_FONT)) family = "monospace";
        else if ("condensed".equals(DESIGN_FONT)) family = "sans-serif-condensed";
        else family = "sans-serif";
        return Typeface.create(family, style);
    }

    public static GradientDrawable round(int color, float radiusDp, Activity a) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(a, Math.max(0, (int)(radiusDp * CORNER_SCALE))));
        return d;
    }

    public static GradientDrawable roundStroke(int color, int stroke, float radiusDp, Activity a) {
        GradientDrawable d = round(color, radiusDp, a);
        d.setStroke(dp(a, 1), stroke);
        return d;
    }

    public static Drawable burnCard(Activity a, boolean active) {
        return new BurnCardDrawable(a, active);
    }

    public static Drawable tabChrome(Activity a, boolean selected) {
        return new BurnTabDrawable(a, selected);
    }

    private static final class BurnCardDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tongue = new Path();
        private final float radius;
        private final float edge;
        private final float density;
        private final boolean active;

        BurnCardDrawable(Activity a, boolean active) {
            this.density = a.getResources().getDisplayMetrics().density;
            this.radius = 18f * density * CORNER_SCALE;
            this.edge = 3f * density;
            this.active = active;
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            RectF r = new RectF(b.left + 0.5f, b.top + 0.5f, b.right - 0.5f, b.bottom - 0.5f);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setShader(active
                    ? new LinearGradient(0, r.top, 0, r.bottom,
                            SURFACE, SURFACE_2, Shader.TileMode.CLAMP)
                    : null);
            paint.setColor(active ? SURFACE : BG_2);
            canvas.drawRoundRect(r, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setShader(null);
            paint.setColor(active ? BORDER_HOT : BORDER);
            canvas.drawRoundRect(r, radius, radius, paint);
            if (active) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ACCENT_2);
                RectF marker = new RectF(r.left + 1, r.top + radius, r.left + edge + 1,
                        r.top + radius + 28f * density);
                canvas.drawRoundRect(marker, edge, edge, paint);
            }
            drawCardTreatment(canvas, r, radius, density);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class BurnTabDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float line;
        private final boolean selected;
        BurnTabDrawable(Activity a, boolean selected) {
            this.line = 2f * a.getResources().getDisplayMetrics().density;
            this.selected = selected;
        }
        @Override public void draw(Canvas canvas) {
            if (!selected) return;
            Rect b = getBounds();
            float y = b.bottom - line;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(ACCENT_2);
            canvas.drawRect(b.left + line * 3, y, b.right - line * 3, b.bottom, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public static GradientDrawable gradient(Activity a, int... colors) {
        int[] c = (colors != null && colors.length >= 2) ? colors : new int[]{SURFACE, SURFACE_2};
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, c);
        d.setCornerRadius(dp(a, (int)(22f * CORNER_SCALE)));
        d.setStroke(dp(a,1), BORDER);
        return d;
    }

    public static GradientDrawable gradientRound(Activity a, float radius, int... colors) {
        int[] c = (colors != null && colors.length >= 2) ? colors : new int[]{SURFACE_2, SURFACE_3};
        float radiusPx = dp(a, Math.max(0, (int)(radius * CORNER_SCALE)));
        boolean hero = c.length >= 2 && c[0] == HERO_START && c[1] == HERO_END;
        GradientDrawable d = hero
                ? new HeroGradientDrawable(c, a.getResources().getDisplayMetrics().density, radiusPx)
                : new GradientDrawable(GradientDrawable.Orientation.TL_BR, c);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** Adds the selected design's shared motif to the home hero gradient. */
    private static final class HeroGradientDrawable extends GradientDrawable {
        private final float density;
        private final float radius;
        HeroGradientDrawable(int[] colors, float density, float radius) {
            super(GradientDrawable.Orientation.TL_BR, colors);
            this.density = density;
            this.radius = radius;
        }
        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            if ("none".equals(DESIGN_PATTERN)) return;
            RectF bounds = new RectF(getBounds());
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            int save = canvas.save();
            Path clip = new Path();
            clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            String scene = VisualDesigns.landscapeSceneId(DESIGN_PATTERN);
            if (scene != null) {
                LandscapeArt.Palette palette = LandscapeArt.paletteFromTheme(
                        HERO_START, HERO_END, SURFACE, ACCENT, ACCENT_2, TEXT, HERO_TEXT);
                LandscapeArt.draw(canvas, scene, bounds, palette, density);
                canvas.restoreToCount(save);
                return;
            }
            Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
            ink.setColor(withAlpha(HERO_TEXT, 36));
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(.75f * density);
            VisualDesigns.drawPattern(canvas, DESIGN_PATTERN, bounds, ink, density);
            canvas.restoreToCount(save);
        }
    }

    private static Drawable patterned(Activity a, Drawable base, boolean card, float radiusDp) {
        if (base == null) return null;
        boolean pagePattern = !card && !"none".equals(DESIGN_PATTERN);
        boolean cardDecoration = card && (!"flat".equals(DESIGN_CARD) || "outlined".equals(UI_CARD_MODE));
        if (!pagePattern && !cardDecoration) return base;
        return new LayerDrawable(new Drawable[]{base,
                new PatternOverlayDrawable(a, card, radiusDp * a.getResources().getDisplayMetrics().density)});
    }

    /** Card texture stays quiet; only the selected edge treatment remains. */
    private static void drawCardTreatment(Canvas canvas, RectF bounds, float radius, float density) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setStyle(Paint.Style.STROKE);
        RectF outer = new RectF(bounds);
        if ("dashed".equals(DESIGN_CARD)) {
            ink.setPathEffect(new android.graphics.DashPathEffect(new float[]{4f*density, 3f*density}, 0));
        }
        if (!"flat".equals(DESIGN_CARD) || "outlined".equals(UI_CARD_MODE)) {
            ink.setColor(withAlpha(BORDER, DARK_THEME ? 105 : 145));
            ink.setStrokeWidth("wire".equals(DESIGN_CARD) ? 1.7f*density : density);
            canvas.drawRoundRect(outer, radius, radius, ink);
        }
        if ("double-border".equals(DESIGN_CARD)) {
            ink.setPathEffect(null);
            outer.inset(4f*density, 4f*density);
            canvas.drawRoundRect(outer, Math.max(0, radius - 4f*density),
                    Math.max(0, radius - 4f*density), ink);
        }
    }

    private static final class PatternOverlayDrawable extends Drawable {
        private final float density, radius;
        private final boolean card;
        PatternOverlayDrawable(Activity a, boolean card, float radius) {
            density = a.getResources().getDisplayMetrics().density;
            this.card = card;
            this.radius = radius;
        }
        @Override public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            if (card) {
                drawCardTreatment(canvas, bounds, radius, density);
                return;
            }
            if ("none".equals(DESIGN_PATTERN) || bounds.width() <= 0 || bounds.height() <= 0) return;
            Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
            ink.setColor(withAlpha(ACCENT, 36));
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(.75f * density);
            VisualDesigns.drawPattern(canvas, DESIGN_PATTERN, bounds, ink, density);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public static Drawable screenGradient(Activity a) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG, BG, BG_2});
        return patterned(a, d, false, 0f);
    }

    public static TextView text(Activity a, String s, float sp, int color) {
        TextView v = new TextView(a);
        v.setText(I18n.tr(a,s)); v.setTextSize(sp); v.setTextColor(color);
        v.setLineSpacing(0, 1.08f);
        v.setTypeface(designTypeface(Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(DESIGN_LETTER_SPACING);
        return v;
    }

    public static TextView title(Activity a, String s, float sp) {
        TextView v = text(a, s, sp, TEXT);
        v.setTypeface(designTypeface(Typeface.BOLD));
        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(DESIGN_LETTER_SPACING - 0.01f);
        return v;
    }

    public static TextView overline(Activity a, String s) {
        TextView v = text(a, s, 11, ACCENT_2);
        v.setTypeface(designTypeface(Typeface.BOLD));
        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(Math.max(.05f, DESIGN_LETTER_SPACING + .06f));
        return v;
    }

    public static Button button(Activity a, String s, boolean accent) {
        Button b = new Button(a);
        b.setText(I18n.tr(a,s)); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(designTypeface(Typeface.BOLD));
        b.setTextColor(accent ? ON_ACCENT : TEXT);
        b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(withAlpha(TEXT, 0x1A)), accent ? round(ACCENT_2, 16, a) : roundStroke(SURFACE_2, BORDER, 16, a), null));
        b.setMinHeight(touchDp(a, 54));
        b.setPadding(dp(a,16),0,dp(a,16),0);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(0);
        return b;
    }

    public static Button ghostButton(Activity a, String s) {
        Button b = new Button(a);
        b.setText(I18n.tr(a,s)); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(TEXT); b.setBackground(new ColorDrawable(Color.TRANSPARENT));
        b.setMinHeight(touchDp(a,48)); b.setMinWidth(0);
        b.setPadding(dp(a,10),0,dp(a,10),0);
        return b;
    }

    public static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));
        GradientDrawable surface = gradient(a, SURFACE, SURFACE_2);
        if ("flat".equals(UI_CARD_MODE) && "flat".equals(DESIGN_CARD)) surface.setStroke(0, Color.TRANSPARENT);
        l.setBackground(patterned(a, surface, true, 22f * CORNER_SCALE));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation("elevated".equals(UI_CARD_MODE) || "raised".equals(DESIGN_CARD) ? touchDp(a, 3) : 0);
        return l;
    }

    public static LinearLayout glowCard(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));
        l.setBackground(burnCard(a, true));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation("elevated".equals(UI_CARD_MODE) || "raised".equals(DESIGN_CARD) ? touchDp(a, 3) : 0);
        return l;
    }

    public static LinearLayout.LayoutParams cardParams(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(a,12)); return p;
    }

    public static LinearLayout.LayoutParams gapTop(Activity a, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(a,top),0,0); return p;
    }

    public static LinearLayout.LayoutParams dividerTop(Activity a, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(a,1));
        p.setMargins(0,dp(a,top),0,0); return p;
    }

    public static TextView pill(Activity a, String text) {
        TextView v = text(a,text,12,MUTED);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,9),dp(a,6),dp(a,9),dp(a,6));
        v.setBackground(round(SURFACE_2, 12, a));
        return v;
    }

    public static TextView accentPill(Activity a, String text) {
        TextView v = text(a,text,12,ON_ACCENT);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(null,Typeface.BOLD);
        v.setPadding(dp(a,9),dp(a,6),dp(a,9),dp(a,6));
        v.setBackground(round(ACCENT_2, 12, a));
        return v;
    }

    public static View divider(Activity a) {
        View v = new View(a); v.setBackgroundColor(BORDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(a,1)));
        return v;
    }

    public static TextView sectionHeader(Activity a, String s) {
        TextView v = text(a, s, 14, MUTED);
        v.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(0.008f);
        v.setPadding(0, dp(a,8), 0, dp(a,8));
        return v;
    }

    public static LinearLayout row(Activity a) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(a,4), dp(a,14), dp(a,4), dp(a,14));
        return r;
    }

    /** Material icon navigation. Both icon and label update when switching clock modes. */
    public static Button bottomTab(Activity a, String name, boolean active) {
        Button b=new Button(a);
        b.setTag(name);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setIncludeFontPadding(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(touchDp(a,48));
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(a,2),dp(a,8),dp(a,2),dp(a,8));
        b.setCompoundDrawablePadding(dp(a,4));
        b.setContentDescription(I18n.tr(a,name));
        b.setStateListAnimator(null);
        updateBottomTab(a,b,active);
        return b;
    }

    public static void updateBottomTab(Activity a, Button b, boolean active) {
        String name=(String)b.getTag();
        boolean compact = "compact".equals(NAV_MODE);
        boolean labels = "labels".equals(NAV_MODE);
        b.setText(compact ? "" : ("ストップウォッチ".equals(name)? I18n.tr(a,"計測"):I18n.tr(a,name)));
        b.setTextSize(labels ? 12.5f : 11.5f);
        b.setTextColor(active?ACCENT_2:MUTED);
        b.setTypeface(designTypeface(active?Typeface.BOLD:Typeface.NORMAL));
        b.setBackground(new NavigationIndicatorDrawable(a,active));
        int id=navIcon(name,active);
        Drawable icon=a.getDrawable(id).mutate();
        icon.setTint(active?ACCENT_2:MUTED);
        int iconSize = touchDp(a, labels ? 0 : 22);
        if (labels || iconSize == 0) b.setCompoundDrawables(null,null,null,null);
        else {
            icon.setBounds(0,0,iconSize,iconSize);
            b.setCompoundDrawables(null,icon,null,null);
        }
    }

    /** Only the icon gets the M3 indicator, never the text below it. */
    private static final class NavigationIndicatorDrawable extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean selected;
        private final float density;
        private final String mode;
        NavigationIndicatorDrawable(Activity a,boolean selected){this.selected=selected;this.density=a.getResources().getDisplayMetrics().density;this.mode=NAV_MODE;}
        @Override public void draw(Canvas c){
            if(!selected)return;
            Rect r=getBounds();
            paint.setColor(NAV_HIGHLIGHT);paint.setStyle(Paint.Style.FILL);
            if ("labels".equals(mode)) {
                float y=r.bottom-2*density;
                c.drawRoundRect(r.left+8*density,y,r.right-8*density,r.bottom,1*density,1*density,paint);
            } else if ("compact".equals(mode)) {
                float cx=r.exactCenterX(),cy=r.exactCenterY();
                float half=Math.min(r.width()/2f-4*density,18*density);
                c.drawRoundRect(cx-half,cy-18*density,cx+half,cy+18*density,18*density,18*density,paint);
            } else {
                float cx=r.exactCenterX(),top=r.top+3*density;
                float half=Math.min(r.width()/2f-3*density,27*density);
                c.drawRoundRect(cx-half,top,cx+half,top+31*density,16*density,16*density,paint);
            }
        }
        @Override public void setAlpha(int a){paint.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    private static int navIcon(String name, boolean active) {
        if("アラーム".equals(name)) return active?R.drawable.ic_v292_nav_alarm_fill:R.drawable.ic_v292_nav_alarm_outline;
        if("時計".equals(name)) return active?R.drawable.ic_v292_nav_world_fill:R.drawable.ic_v292_nav_world_outline;
        if("記録".equals(name)) return active?R.drawable.ic_v292_nav_streak_fill:R.drawable.ic_v292_nav_streak_outline;
        if("設定".equals(name)) return R.drawable.ic_v292_action_settings;
        if("タイマー".equals(name)) return R.drawable.ic_v293_timer;
        if("ストップウォッチ".equals(name)) return active?R.drawable.ic_v292_nav_stopwatch_fill:R.drawable.ic_v292_nav_stopwatch_outline;
        return active?R.drawable.ic_v292_nav_streak_fill:R.drawable.ic_v292_nav_streak_outline;
    }

    /** Standard 48dp icon touch target with a restrained, stateful surface. */
    public static ImageButton iconButton(Activity a, int drawableId, boolean primary, String label) {
        ImageButton b=new ImageButton(a);
        b.setImageResource(drawableId);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setImageTintList(ColorStateList.valueOf(primary?ON_ACCENT:ACCENT));
        b.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(withAlpha(TEXT, 0x1A)),
            round(primary?ACCENT_2:SURFACE_2,16,a),
            round(SURFACE,16,a)));
        b.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));
        b.setMinimumWidth(touchDp(a,48)); b.setMinimumHeight(touchDp(a,48));
        b.setContentDescription(I18n.tr(a,label));
        b.setFocusable(true);
        if(Build.VERSION.SDK_INT>=21)b.setElevation(0);
        return b;
    }

    public static void styleSwitch(android.widget.Switch s, Activity a) {
        try { s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT_2,MUTED_2})); } catch(Throwable ignored){}
        try { s.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{withAlpha(ACCENT_2,0x66),withAlpha(MUTED_2,0x66)})); } catch(Throwable ignored){}
    }

    public static void prepareActivity(Activity a) {
        AppThemes.apply(a);
        applyUiProfile(UiProfiles.current(a));
        applyVisualDesign(VisualDesigns.current(a));
        statusBar(a);
        try { a.getWindow().setBackgroundDrawable(screenGradient(a)); } catch (Throwable ignored) {}
        try { a.getWindow().setWindowAnimations(0); } catch (Throwable ignored) {}
        if (Build.VERSION.SDK_INT >= 34) {
            try { a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0); } catch (Throwable ignored) {}
            try { a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0); } catch (Throwable ignored) {}
        }
    }

    public static void applySystemBarInsets(Activity a, View root) {
        final int baseL = root.getPaddingLeft();
        final int baseT = root.getPaddingTop();
        final int baseR = root.getPaddingRight();
        final int baseB = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, wi) -> {
            int left=0, top=0, right=0, bottom=0;
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets x = wi.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    left=x.left; top=x.top; right=x.right; bottom=x.bottom;
                } else {
                    left=wi.getSystemWindowInsetLeft(); top=wi.getSystemWindowInsetTop();
                    right=wi.getSystemWindowInsetRight(); bottom=wi.getSystemWindowInsetBottom();
                }
            } catch (Throwable ignored) {}
            v.setPadding(baseL + left, baseT + top, baseR + right, baseB + bottom);
            return wi;
        });
        try { root.requestApplyInsets(); } catch (Throwable ignored) {}
        try {
            I18n.localizeTree(root);
            root.getViewTreeObserver().addOnGlobalLayoutListener(() -> I18n.localizeTree(root));
        } catch (Throwable ignored) {}
    }

    public static void launchNoAnimation(Activity a, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        a.startActivity(i);
        try { a.overridePendingTransition(0,0); } catch (Throwable ignored) {}
    }

    public static void finishNoAnimation(Activity a) {
        a.finish();
        try { a.overridePendingTransition(0,0); } catch (Throwable ignored) {}
    }

    public static void statusBar(Activity a) {
        try {
            AppThemes.apply(a);
            applyUiProfile(UiProfiles.current(a));
            applyVisualDesign(VisualDesigns.current(a));
            int statusColor = Build.VERSION.SDK_INT >= 23 ? BG : ACCENT;
            int navigationColor = Build.VERSION.SDK_INT >= 26 ? BG : ACCENT;
            a.getWindow().setStatusBarColor(statusColor);
            a.getWindow().setNavigationBarColor(navigationColor);
            if (Build.VERSION.SDK_INT >= 29) a.getWindow().setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 23) {
                int flags = DARK_THEME ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (!DARK_THEME && Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                a.getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {}
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
