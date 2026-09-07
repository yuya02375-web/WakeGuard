from pathlib import Path
import re

app = Path("WakeGuard/app")
res = app / "src/main/res"
java = app / "src/main/java/jp/wakeguard/alarm"

def rd(p): return p.read_text(encoding="utf-8")
def wr(p, s):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(s, encoding="utf-8")

def replace_required(path, old, new):
    s = rd(path)
    assert old in s, f"missing expected text in {path}: {old[:80]}"
    wr(path, s.replace(old, new))

# v1.7.3: Ember Night — a darker, sharper IGNIDO visual identity.
p = app / "build.gradle.kts"
s = rd(p)
s = re.sub(r'versionCode = \d+', 'versionCode = 84', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.7.3"', s)
wr(p, s)

ui = r'''package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * IGNIDO Ember Night design system.
 * Near-black graphite surfaces, ember red/orange focus, restrained glow,
 * large readable clock typography, and compact navigation.
 */
public final class Ui {
    public static final int BG = 0xFF080A0E;
    public static final int BG_2 = 0xFF0C0F15;
    public static final int SURFACE = 0xFF12161D;
    public static final int SURFACE_2 = 0xFF181D26;
    public static final int SURFACE_3 = 0xFF222936;
    public static final int BORDER = 0xFF2C3442;
    public static final int BORDER_HOT = 0x665F3329;
    public static final int ACCENT = 0xFFFF4B2B;
    public static final int ACCENT_2 = 0xFFFF9A3C;
    public static final int CYAN = 0xFFFF795E;
    public static final int BLUE = 0xFFFF6547;
    public static final int VIOLET = 0xFFEF6B57;
    public static final int PINK = 0xFFFF7B6B;
    public static final int TEXT = 0xFFF7F7F8;
    public static final int MUTED = 0xFFA8AFBA;
    public static final int MUTED_2 = 0xFF6F7886;
    public static final int SUCCESS = 0xFF76D59A;
    public static final int DANGER = 0xFFFF6B63;
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
        int[] c = (colors != null && colors.length >= 2) ? colors : new int[]{SURFACE, SURFACE_2};
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, c);
        d.setCornerRadius(dp(a, 20));
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
                new int[]{0xFF07090D, 0xFF0A0D13, 0xFF10141B});
        return d;
    }

    public static TextView text(Activity a, String s, float sp, int color) {
        TextView v = new TextView(a);
        v.setText(I18n.tr(a,s)); v.setTextSize(sp); v.setTextColor(color);
        v.setLineSpacing(0, 1.08f);
        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(0.005f);
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
        b.setTextColor(accent ? 0xFFFFFFFF : TEXT);
        b.setBackground(accent ? gradientRound(a, 16, ACCENT, 0xFFFF6537) : roundStroke(SURFACE_2, BORDER, 16, a));
        b.setMinHeight(dp(a, 52));
        b.setPadding(dp(a,16),0,dp(a,16),0);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(dp(a, accent ? 3 : 1));
        return b;
    }

    public static Button ghostButton(Activity a, String s) {
        Button b = new Button(a);
        b.setText(I18n.tr(a,s)); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(TEXT); b.setBackground(round(0x0012161D, 14, a));
        b.setMinHeight(dp(a,44)); b.setMinWidth(0);
        b.setPadding(dp(a,10),0,dp(a,10),0);
        return b;
    }

    public static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));
        l.setBackground(roundStroke(SURFACE, BORDER, 20, a));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(dp(a,1));
        return l;
    }

    public static LinearLayout glowCard(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));
        l.setBackground(gradient(a, 0xFF19161A, 0xFF12171F));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(dp(a,2));
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
        v.setBackground(roundStroke(SURFACE_2, BORDER, 12, a));
        return v;
    }

    public static TextView accentPill(Activity a, String text) {
        TextView v = text(a,text,12,ACCENT_2);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(null,Typeface.BOLD);
        v.setPadding(dp(a,9),dp(a,6),dp(a,9),dp(a,6));
        v.setBackground(roundStroke(0x332A1815, 0x665F3329, 12, a));
        return v;
    }

    public static View divider(Activity a) {
        View v = new View(a); v.setBackgroundColor(0x992C3442);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(a,1)));
        return v;
    }

    public static TextView sectionHeader(Activity a, String s) {
        TextView v = text(a, s, 12, ACCENT_2);
        v.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(0.055f);
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
        b.setAllCaps(false); b.setText(I18n.tr(a,text)); b.setSingleLine(true);
        b.setTextColor(selected ? 0xFFFFFFFF : MUTED);
        b.setBackground(selected ? roundStroke(0x332A1815, 0x665F3329, 18, a) : round(0x00000000,18,a));
        b.setMinHeight(dp(a,52)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(a,3),0,dp(a,3),0); b.setTextScaleX(0.94f);
        b.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        if (Build.VERSION.SDK_INT >= 26) b.setAutoSizeTextTypeUniformWithConfiguration(8,12,1,TypedValue.COMPLEX_UNIT_SP);
        else b.setTextSize(9f);
        return b;
    }

    public static void styleSwitch(android.widget.Switch s, Activity a) {
        try { s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,0xFF69717D})); } catch(Throwable ignored){}
        try { s.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0x88FF4B2B,0x66343B46})); } catch(Throwable ignored){}
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
            if (Build.VERSION.SDK_INT >= 23) a.getWindow().getDecorView().setSystemUiVisibility(0);
        } catch (Throwable ignored) {}
    }
}
'''
wr(java / "Ui.java", ui)

# Brand resources and dark native controls/dialogs.
wr(res / "values/ignido_brand.xml", '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ignido_midnight">#080A0E</color>
    <color name="ignido_ember">#FF4B2B</color>
    <color name="ignido_amber">#FF9A3C</color>
    <color name="ignido_surface">#12161D</color>
</resources>\n''')

wr(res / "values/styles.xml", '''<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowActionModeOverlay">true</item>
        <item name="android:windowBackground">@color/ignido_midnight</item>
        <item name="android:colorAccent">@color/ignido_ember</item>
        <item name="android:navigationBarColor">@color/ignido_midnight</item>
        <item name="android:statusBarColor">@color/ignido_midnight</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar">false</item>
    </style>
</resources>\n''')

wr(res / "drawable/widget_bg.xml", '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="90" android:startColor="#F20A0D12" android:endColor="#F2181D26" />
    <stroke android:width="1dp" android:color="#55FF5A36" />
    <corners android:radius="24dp" />
    <padding android:left="14dp" android:top="10dp" android:right="14dp" android:bottom="10dp" />
</shape>\n''')

# Give the main alarm screen the more branded surface treatment while keeping
# the rest of the app intentionally quieter for fast sleepy-morning scanning.
main = java / "MainActivity.java"
s = rd(main)
s = s.replace('root.setBackgroundColor(Ui.BG);', 'root.setBackground(Ui.screenGradient(this));')
s = s.replace('for(int i=0;i<alarms.size();i++){addAlarmRow(alarms.get(i));if(i<alarms.size()-1)list.addView(Ui.divider(this));}',
              'for(int i=0;i<alarms.size();i++){addAlarmRow(alarms.get(i));}')
s = s.replace('LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,18));row.setAlpha(e.enabled?1f:.45f);row.setOnClickListener(v->edit(e.id));',
              'LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(this,17),Ui.dp(this,17),Ui.dp(this,17),Ui.dp(this,17));row.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,20,this));if(Build.VERSION.SDK_INT>=21)row.setElevation(Ui.dp(this,1));row.setAlpha(e.enabled?1f:.48f);row.setOnClickListener(v->edit(e.id));')
s = s.replace('list.addView(row);', 'list.addView(row,Ui.cardParams(this));')
s = s.replace('Switch on=new Switch(this);on.setChecked(e.enabled);first.addView(on);row.addView(first);', 'Switch on=new Switch(this);on.setChecked(e.enabled);Ui.styleSwitch(on,this);first.addView(on);row.addView(first);')
wr(main, s)

# Apply a matching selected-state capsule to the clock/timer/stopwatch tabs when mode changes.
clock = java / "ClockActivity.java"
s = rd(clock)
s = s.replace('outer.setBackgroundColor(Ui.BG);', 'outer.setBackground(Ui.screenGradient(this));')
s = s.replace('worldTab.setTextColor("world".equals(mode)?Ui.ACCENT:Ui.MUTED);\n            timerTab.setTextColor("timer".equals(mode)?Ui.ACCENT:Ui.MUTED);\n            stopwatchTab.setTextColor("stopwatch".equals(mode)?Ui.ACCENT:Ui.MUTED);',
'''worldTab.setTextColor("world".equals(mode)?0xFFFFFFFF:Ui.MUTED);
            timerTab.setTextColor("timer".equals(mode)?0xFFFFFFFF:Ui.MUTED);
            stopwatchTab.setTextColor("stopwatch".equals(mode)?0xFFFFFFFF:Ui.MUTED);
            worldTab.setBackground("world".equals(mode)?Ui.roundStroke(0x332A1815,0x665F3329,18,this):Ui.round(0x00000000,18,this));
            timerTab.setBackground("timer".equals(mode)?Ui.roundStroke(0x332A1815,0x665F3329,18,this):Ui.round(0x00000000,18,this));
            stopwatchTab.setBackground("stopwatch".equals(mode)?Ui.roundStroke(0x332A1815,0x665F3329,18,this):Ui.round(0x00000000,18,this));''')
wr(clock, s)

# Tint common switches on editor/settings screens without changing behavior.
editor = java / "AlarmEditorActivity.java"
s = rd(editor)
s = s.replace('enabled=new Switch(this);enabled.setText(I18n.tr(this,"アラームを有効にする"));enabled.setTextColor(Ui.TEXT);',
              'enabled=new Switch(this);enabled.setText(I18n.tr(this,"アラームを有効にする"));enabled.setTextColor(Ui.TEXT);Ui.styleSwitch(enabled,this);')
s = s.replace('ringAutoStop=new Switch(this);ringAutoStop.setText(I18n.tr(this,"音・振動だけ自動停止"));ringAutoStop.setTextColor(Ui.TEXT);',
              'ringAutoStop=new Switch(this);ringAutoStop.setText(I18n.tr(this,"音・振動だけ自動停止"));ringAutoStop.setTextColor(Ui.TEXT);Ui.styleSwitch(ringAutoStop,this);')
s = s.replace('fullAutoStop=new Switch(this);fullAutoStop.setText(I18n.tr(this,"アラームを完全自動停止"));fullAutoStop.setTextColor(Ui.TEXT);',
              'fullAutoStop=new Switch(this);fullAutoStop.setText(I18n.tr(this,"アラームを完全自動停止"));fullAutoStop.setTextColor(Ui.TEXT);Ui.styleSwitch(fullAutoStop,this);')
wr(editor, s)

assert 'versionName = "1.7.3"' in rd(app / "build.gradle.kts")
assert 'public static final int ACCENT = 0xFFFF4B2B;' in rd(java / "Ui.java")
assert 'row.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,20,this))' in rd(main)
assert '<gradient android:angle="90"' in rd(res / "drawable/widget_bg.xml")
print("IGNIDO Wake v1.7.3 Ember Night design patch applied")
