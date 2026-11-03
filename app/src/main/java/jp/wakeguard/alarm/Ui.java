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
 * IGNIDO Ember Night design system.
 * Near-black graphite surfaces, ember red/orange focus, restrained glow,
 * large readable clock typography, and compact navigation.
 */
public final class Ui {
    public static final int BG = 0xFF10141B;
    public static final int BG_2 = 0xFF10141B;
    public static final int SURFACE = 0xFF1B222C;
    public static final int SURFACE_2 = 0xFF282F3B;
    public static final int SURFACE_3 = 0xFF303540;
    public static final int BORDER = 0xFF353D49;
    public static final int BORDER_HOT = 0x447F584B;
    public static final int ACCENT = 0xFFFF9A74;
    public static final int ACCENT_2 = 0xFFFF9671;
    public static final int CYAN = 0xFF8ABCD5;
    public static final int BLUE = 0xFF8DA8DE;
    public static final int VIOLET = 0xFFB39CE2;
    public static final int PINK = 0xFFE4A1BA;
    public static final int TEXT = 0xFFF6F4F0;
    public static final int MUTED = 0xFFB1B2BC;
    public static final int MUTED_2 = 0xFF898C98;
    public static final int SUCCESS = 0xFF81D4AD;
    public static final int DANGER = 0xFFFF8C83;
    private Ui() {}

    public static int dp(Activity a, int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    public static GradientDrawable round(int color, float radiusDp, Activity a) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(a, (int)radiusDp));
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
            this.radius = 18f * density;
            this.edge = 3f * density;
            this.active = active;
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            RectF r = new RectF(b.left + 0.5f, b.top + 0.5f, b.right - 0.5f, b.bottom - 0.5f);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(SURFACE);
            canvas.drawRoundRect(r, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(active ? 0x665E554F : BORDER);
            canvas.drawRoundRect(r, radius, radius, paint);
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
        d.setCornerRadius(dp(a, 18));
        d.setStroke(dp(a,1), BORDER);
        return d;
    }

    public static GradientDrawable gradientRound(Activity a, float radius, int... colors) {
        int[] c = (colors != null && colors.length >= 2) ? colors : new int[]{ACCENT, ACCENT_2};
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, c);
        d.setCornerRadius(dp(a, (int)radius));
        return d;
    }

    public static GradientDrawable screenGradient(Activity a) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG, BG, BG});
        return d;
    }

    public static TextView text(Activity a, String s, float sp, int color) {
        TextView v = new TextView(a);
        v.setText(I18n.tr(a,s)); v.setTextSize(sp); v.setTextColor(color);
        v.setLineSpacing(0, 1.08f);
        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(0f);
        return v;
    }

    public static TextView title(Activity a, String s, float sp) {
        TextView v = text(a, s, sp, TEXT);
        v.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(-0.015f);
        return v;
    }

    public static TextView overline(Activity a, String s) {
        TextView v = text(a, s, 11, ACCENT_2);
        v.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(0.08f);
        return v;
    }

    public static Button button(Activity a, String s, boolean accent) {
        Button b = new Button(a);
        b.setText(I18n.tr(a,s)); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(accent ? 0xFF251913 : TEXT);
        b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), accent ? round(ACCENT_2, 16, a) : roundStroke(SURFACE_2, BORDER, 16, a), null));
        b.setMinHeight(dp(a, 54));
        b.setPadding(dp(a,16),0,dp(a,16),0);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(0);
        return b;
    }

    public static Button ghostButton(Activity a, String s) {
        Button b = new Button(a);
        b.setText(I18n.tr(a,s)); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(TEXT); b.setBackground(new ColorDrawable(Color.TRANSPARENT));
        b.setMinHeight(dp(a,44)); b.setMinWidth(0);
        b.setPadding(dp(a,10),0,dp(a,10),0);
        return b;
    }

    public static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));
        l.setBackground(roundStroke(SURFACE, BORDER, 18, a));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(0);
        return l;
    }

    public static LinearLayout glowCard(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));
        l.setBackground(burnCard(a, true));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(0);
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
        TextView v = text(a,text,12,ACCENT_2);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(null,Typeface.BOLD);
        v.setPadding(dp(a,9),dp(a,6),dp(a,9),dp(a,6));
        v.setBackground(round(0x443C2820, 12, a));
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
        b.setMinHeight(dp(a,64));
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
        b.setText("ストップウォッチ".equals(name)? I18n.tr(a,"計測"):I18n.tr(a,name));
        b.setTextSize(11.5f);
        b.setTextColor(active?ACCENT_2:MUTED);
        b.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
        b.setBackground(new NavigationIndicatorDrawable(a,active));
        int id=navIcon(name,active);
        Drawable icon=a.getDrawable(id).mutate();
        icon.setTint(active?ACCENT_2:MUTED);
        icon.setBounds(0,0,dp(a,24),dp(a,24));
        b.setCompoundDrawables(null,icon,null,null);
    }

    /** Only the icon gets the M3 indicator, never the text below it. */
    private static final class NavigationIndicatorDrawable extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean selected;
        private final float density;
        NavigationIndicatorDrawable(Activity a,boolean selected){this.selected=selected;this.density=a.getResources().getDisplayMetrics().density;}
        @Override public void draw(Canvas c){
            if(!selected)return;
            Rect r=getBounds();
            float cx=r.exactCenterX(),top=r.top+3*density;
            paint.setColor(0xFF503A32);paint.setStyle(Paint.Style.FILL);
            float half=Math.min(r.width()/2f-3*density,27*density);
            c.drawRoundRect(cx-half,top,cx+half,top+31*density,16*density,16*density,paint);
        }
        @Override public void setAlpha(int a){paint.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    private static int navIcon(String name, boolean active) {
        if("アラーム".equals(name)) return active?R.drawable.ic_v292_nav_alarm_fill:R.drawable.ic_v292_nav_alarm_outline;
        if("時計".equals(name)) return active?R.drawable.ic_v292_nav_world_fill:R.drawable.ic_v292_nav_world_outline;
        if("タイマー".equals(name)) return R.drawable.ic_v293_timer;
        if("ストップウォッチ".equals(name)) return active?R.drawable.ic_v292_nav_stopwatch_fill:R.drawable.ic_v292_nav_stopwatch_outline;
        return active?R.drawable.ic_v292_nav_streak_fill:R.drawable.ic_v292_nav_streak_outline;
    }

    /** Standard 48dp icon touch target with a restrained, stateful surface. */
    public static ImageButton iconButton(Activity a, int drawableId, boolean primary, String label) {
        ImageButton b=new ImageButton(a);
        b.setImageResource(drawableId);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setImageTintList(ColorStateList.valueOf(primary?0xFF251811:TEXT));
        b.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF),
            round(primary?ACCENT_2:SURFACE_2,16,a),
            round(Color.WHITE,16,a)));
        b.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));
        b.setMinimumWidth(dp(a,48)); b.setMinimumHeight(dp(a,48));
        b.setContentDescription(I18n.tr(a,label));
        b.setFocusable(true);
        if(Build.VERSION.SDK_INT>=21)b.setElevation(0);
        return b;
    }

    public static void styleSwitch(android.widget.Switch s, Activity a) {
        try { s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT_2,0xFF6D6258})); } catch(Throwable ignored){}
        try { s.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0x99FF5A16,0x66423931})); } catch(Throwable ignored){}
    }

    public static void prepareActivity(Activity a) {
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
            a.getWindow().setStatusBarColor(BG);
            a.getWindow().setNavigationBarColor(BG);
            if (Build.VERSION.SDK_INT >= 29) a.getWindow().setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 23) a.getWindow().getDecorView().setSystemUiVisibility(0);
        } catch (Throwable ignored) {}
    }
}
