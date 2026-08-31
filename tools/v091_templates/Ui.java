package jp.wakeguard.alarm;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG = 0xFF101113;
    public static final int BG_2 = 0xFF141518;
    public static final int SURFACE = 0xFF1A1C20;
    public static final int SURFACE_2 = 0xFF22252A;
    public static final int SURFACE_3 = 0xFF2A2E34;
    public static final int BORDER = 0xFF34383F;
    public static final int ACCENT = 0xFFE6B565;
    public static final int ACCENT_2 = 0xFF9DB3A7;
    public static final int CYAN = ACCENT;
    public static final int BLUE = 0xFF8FA8C7;
    public static final int VIOLET = ACCENT_2;
    public static final int PINK = 0xFFD9998F;
    public static final int TEXT = 0xFFF3F1EC;
    public static final int MUTED = 0xFFA9A8A5;
    public static final int MUTED_2 = 0xFF777A80;
    public static final int SUCCESS = 0xFF8FC7A5;
    public static final int DANGER = 0xFFE18484;
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
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                colors == null || colors.length == 0 ? new int[]{SURFACE, SURFACE_2} : colors);
        d.setCornerRadius(dp(a, 24));
        return d;
    }

    public static GradientDrawable gradientRound(Activity a, float radius, int... colors) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                colors == null || colors.length == 0 ? new int[]{ACCENT, ACCENT} : colors);
        d.setCornerRadius(dp(a, (int)radius));
        return d;
    }

    public static GradientDrawable screenGradient(Activity a) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF121316, 0xFF0F1012});
    }

    public static TextView text(Activity a, String s, float sp, int color) {
        TextView v = new TextView(a);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setLineSpacing(0, 1.08f);
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
        b.setTextColor(accent ? 0xFF18140E : TEXT);
        b.setBackground(accent ? round(ACCENT, 18, a) : roundStroke(SURFACE_2, BORDER, 18, a));
        b.setMinHeight(dp(a, 56));
        b.setPadding(dp(a,16),0,dp(a,16),0);
        return b;
    }

    public static Button ghostButton(Activity a, String s) {
        Button b = button(a,s,false);
        b.setBackground(roundStroke(0x00000000,BORDER,18,a));
        return b;
    }

    public static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,20),dp(a,18),dp(a,20),dp(a,18));
        l.setBackground(roundStroke(SURFACE,BORDER,24,a));
        return l;
    }

    public static LinearLayout glowCard(Activity a) {
        LinearLayout l = card(a);
        l.setBackground(roundStroke(SURFACE_2, BORDER, 26, a));
        return l;
    }

    public static LinearLayout.LayoutParams cardParams(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(a,14)); return p;
    }

    public static LinearLayout.LayoutParams gapTop(Activity a, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(a,top),0,0); return p;
    }

    public static TextView pill(Activity a, String text) {
        TextView v = text(a,text,12,TEXT);
        v.setTypeface(null,Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,11),dp(a,7),dp(a,11),dp(a,7));
        v.setBackground(roundStroke(SURFACE_3,BORDER,14,a));
        return v;
    }

    public static TextView accentPill(Activity a, String text) {
        TextView v = text(a,text,12,0xFF201A10);
        v.setTypeface(null,Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,11),dp(a,7),dp(a,11),dp(a,7));
        v.setBackground(round(ACCENT,14,a));
        return v;
    }

    public static View divider(Activity a) {
        View v = new View(a); v.setBackgroundColor(BORDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(a,1)));
        return v;
    }

    public static void statusBar(Activity a) {
        try {
            a.getWindow().setStatusBarColor(BG);
            a.getWindow().setNavigationBarColor(BG);
        } catch (Throwable ignored) {}
    }
}
