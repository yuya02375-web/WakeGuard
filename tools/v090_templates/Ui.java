package jp.wakeguard.alarm;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG = 0xFF060A16;
    public static final int BG_2 = 0xFF0D1026;
    public static final int SURFACE = 0xFF11172A;
    public static final int SURFACE_2 = 0xFF18203A;
    public static final int SURFACE_3 = 0xFF202A49;
    public static final int CYAN = 0xFF63F2FF;
    public static final int BLUE = 0xFF5E8BFF;
    public static final int VIOLET = 0xFFA277FF;
    public static final int PINK = 0xFFFF78C7;
    public static final int ACCENT = CYAN;
    public static final int ACCENT_2 = VIOLET;
    public static final int TEXT = 0xFFF7FAFF;
    public static final int MUTED = 0xFF9CA8C3;
    public static final int MUTED_2 = 0xFF6D7895;
    public static final int SUCCESS = 0xFF60F5B5;
    public static final int DANGER = 0xFFFF6F91;
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
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(a, 24));
        return d;
    }

    public static GradientDrawable gradientRound(Activity a, float radius, int... colors) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadius(dp(a, (int)radius));
        return d;
    }

    public static GradientDrawable screenGradient(Activity a) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF050814, 0xFF0A1026, 0xFF111033});
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

    public static TextView overline(Activity a, String s) {
        TextView v = text(a, s, 11, CYAN);
        v.setTypeface(null, Typeface.BOLD);
        v.setLetterSpacing(0.16f);
        return v;
    }

    public static Button button(Activity a, String s, boolean accent) {
        Button b = new Button(a);
        b.setText(s); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(accent ? 0xFF07111C : TEXT);
        b.setBackground(accent ? gradientRound(a, 18, CYAN, BLUE, VIOLET) : roundStroke(SURFACE_2, 0xFF2A3658, 18, a));
        b.setMinHeight(dp(a, 54));
        b.setPadding(dp(a,14),0,dp(a,14),0);
        return b;
    }

    public static Button ghostButton(Activity a, String s) {
        Button b = button(a,s,false);
        b.setBackground(roundStroke(0x00000000,0xFF344161,18,a));
        return b;
    }

    public static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,16));
        l.setBackground(roundStroke(SURFACE,0xFF26314F,24,a));
        return l;
    }

    public static LinearLayout glowCard(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,20),dp(a,18),dp(a,20),dp(a,18));
        l.setBackground(gradient(a, 0xFF15243A, 0xFF171A3C, 0xFF251A42));
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
        TextView v = text(a,text,12,TEXT);
        v.setTypeface(null,Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,10),dp(a,6),dp(a,10),dp(a,6));
        v.setBackground(roundStroke(0xFF171F35,0xFF31405F,14,a));
        return v;
    }

    public static TextView accentPill(Activity a, String text) {
        TextView v = text(a,text,12,0xFF08111C);
        v.setTypeface(null,Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,10),dp(a,6),dp(a,10),dp(a,6));
        v.setBackground(gradientRound(a,14,CYAN,BLUE));
        return v;
    }

    public static View divider(Activity a) {
        View v = new View(a); v.setBackgroundColor(0xFF26314F);
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
