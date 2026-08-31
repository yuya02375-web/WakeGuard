package jp.wakeguard.alarm;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG = 0xFF0B1020;
    public static final int SURFACE = 0xFF151B2D;
    public static final int SURFACE_2 = 0xFF1C2438;
    public static final int ACCENT = 0xFF58E0C4;
    public static final int ACCENT_2 = 0xFF87A7FF;
    public static final int TEXT = 0xFFF7F9FC;
    public static final int MUTED = 0xFFA7B0C0;
    public static final int DANGER = 0xFFFF6B6B;
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

    public static TextView text(Activity a, String s, float sp, int color) {
        TextView v = new TextView(a);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        return v;
    }

    public static TextView title(Activity a, String s, float sp) {
        TextView v = text(a, s, sp, TEXT);
        v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    public static Button button(Activity a, String s, boolean accent) {
        Button b = new Button(a);
        b.setText(s); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(accent ? BG : TEXT);
        b.setBackground(round(accent ? ACCENT : SURFACE_2, 18, a));
        b.setMinHeight(dp(a, 52));
        return b;
    }

    public static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,16));
        l.setBackground(round(SURFACE, 22, a));
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

    public static TextView pill(Activity a, String text) {
        TextView v = text(a,text,13,TEXT);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,10),dp(a,5),dp(a,10),dp(a,5));
        v.setBackground(round(SURFACE_2, 14, a));
        return v;
    }

    public static void statusBar(Activity a) {
        try { a.getWindow().setStatusBarColor(BG); a.getWindow().setNavigationBarColor(BG); }
        catch (Throwable ignored) {}
    }
}
