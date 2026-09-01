package jp.wakeguard.alarm;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Deliberately boring UI primitives: one background, one surface, one accent.
 * The app should look like a clock/settings app, not a themed dashboard.
 */
public final class Ui {
    public static final int BG = 0xFF101113;
    public static final int BG_2 = BG;
    public static final int SURFACE = 0xFF1A1B1E;
    public static final int SURFACE_2 = 0xFF202125;
    public static final int SURFACE_3 = 0xFF292A2F;
    public static final int BORDER = 0xFF34353A;
    public static final int ACCENT = 0xFF8AB4F8;
    public static final int ACCENT_2 = 0xFFB8C5D9;
    public static final int CYAN = ACCENT;
    public static final int BLUE = ACCENT;
    public static final int VIOLET = ACCENT;
    public static final int PINK = 0xFFE8A0A0;
    public static final int TEXT = 0xFFF1F1F1;
    public static final int MUTED = 0xFFB0B0B4;
    public static final int MUTED_2 = 0xFF77787E;
    public static final int SUCCESS = 0xFF8BCF9B;
    public static final int DANGER = 0xFFFF8A80;
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

    public static GradientDrawable gradient(Activity a, int... colors) {
        return round(SURFACE, 18, a);
    }

    public static GradientDrawable gradientRound(Activity a, float radius, int... colors) {
        return round(ACCENT, radius, a);
    }

    public static GradientDrawable screenGradient(Activity a) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(BG);
        return d;
    }

    public static TextView text(Activity a, String s, float sp, int color) {
        TextView v = new TextView(a);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setLineSpacing(0, 1.06f);
        return v;
    }

    public static TextView title(Activity a, String s, float sp) {
        TextView v = text(a, s, sp, TEXT);
        v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    public static TextView overline(Activity a, String s) {
        TextView v = text(a, s, 12, MUTED);
        v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    public static Button button(Activity a, String s, boolean accent) {
        Button b = new Button(a);
        b.setText(s); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(accent ? 0xFF0D1B2A : TEXT);
        b.setBackground(accent ? round(ACCENT, 14, a) : roundStroke(SURFACE_2, BORDER, 14, a));
        b.setMinHeight(dp(a, 52));
        b.setPadding(dp(a,16),0,dp(a,16),0);
        return b;
    }

    public static Button ghostButton(Activity a, String s) {
        Button b = button(a,s,false);
        b.setBackgroundColor(0x00000000);
        b.setTextColor(TEXT);
        return b;
    }

    public static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,16));
        l.setBackground(round(SURFACE, 16, a));
        return l;
    }

    public static LinearLayout glowCard(Activity a) { return card(a); }

    public static LinearLayout.LayoutParams cardParams(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(a,10)); return p;
    }

    public static LinearLayout.LayoutParams gapTop(Activity a, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(a,top),0,0); return p;
    }

    public static TextView pill(Activity a, String text) {
        TextView v = text(a,text,12,MUTED);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,8),dp(a,5),dp(a,8),dp(a,5));
        v.setBackground(round(SURFACE_2,10,a));
        return v;
    }

    public static TextView accentPill(Activity a, String text) {
        TextView v = text(a,text,12,ACCENT);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,8),dp(a,5),dp(a,8),dp(a,5));
        v.setBackground(round(SURFACE_2,10,a));
        return v;
    }

    public static View divider(Activity a) {
        View v = new View(a); v.setBackgroundColor(BORDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(a,1)));
        return v;
    }

    public static TextView sectionHeader(Activity a, String s) {
        TextView v = text(a, s, 13, MUTED);
        v.setTypeface(null, Typeface.BOLD);
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

    public static Button bottomTab(Activity a, String text, boolean selected) {
        Button b = new Button(a);
        b.setAllCaps(false); b.setText(text); b.setTextSize(12);
        b.setTextColor(selected ? ACCENT : MUTED);
        b.setBackgroundColor(0x00000000);
        b.setMinHeight(dp(a,56));
        b.setPadding(dp(a,4),0,dp(a,4),0);
        return b;
    }

    public static void statusBar(Activity a) {
        try {
            a.getWindow().setStatusBarColor(BG);
            a.getWindow().setNavigationBarColor(BG);
        } catch (Throwable ignored) {}
    }
}
