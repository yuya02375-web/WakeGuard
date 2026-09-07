from pathlib import Path
import re

app = Path("WakeGuard/app")
res = app / "src/main/res"
java = app / "src/main/java/jp/wakeguard/alarm"

def rd(p): return p.read_text(encoding="utf-8")
def wr(p, s):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(s, encoding="utf-8")

p = app / "build.gradle.kts"
s = rd(p)
s = re.sub(r'versionCode = \d+', 'versionCode = 85', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.7.4"', s)
wr(p, s)

ui_path = java / "Ui.java"
s = rd(ui_path)

repls = {
    'public static final int BG = 0xFF080A0E;': 'public static final int BG = 0xFF080706;',
    'public static final int BG_2 = 0xFF0C0F15;': 'public static final int BG_2 = 0xFF0D0B09;',
    'public static final int SURFACE = 0xFF12161D;': 'public static final int SURFACE = 0xFF15110D;',
    'public static final int SURFACE_2 = 0xFF181D26;': 'public static final int SURFACE_2 = 0xFF1C160F;',
    'public static final int SURFACE_3 = 0xFF222936;': 'public static final int SURFACE_3 = 0xFF261C13;',
    'public static final int BORDER = 0xFF2C3442;': 'public static final int BORDER = 0xFF3A2A1F;',
    'public static final int BORDER_HOT = 0x665F3329;': 'public static final int BORDER_HOT = 0x887A351B;',
    'public static final int ACCENT = 0xFFFF4B2B;': 'public static final int ACCENT = 0xFFFF3A14;',
    'public static final int ACCENT_2 = 0xFFFF9A3C;': 'public static final int ACCENT_2 = 0xFFFF7A00;',
    'public static final int CYAN = 0xFFFF795E;': 'public static final int CYAN = 0xFFFF8A1F;',
    'public static final int BLUE = 0xFFFF6547;': 'public static final int BLUE = 0xFFFF5A1A;',
    'public static final int VIOLET = 0xFFEF6B57;': 'public static final int VIOLET = 0xFFFF6A1A;',
    'public static final int PINK = 0xFFFF7B6B;': 'public static final int PINK = 0xFFFF7A20;',
    'public static final int TEXT = 0xFFF7F7F8;': 'public static final int TEXT = 0xFFFFF8F1;',
    'public static final int MUTED = 0xFFA8AFBA;': 'public static final int MUTED = 0xFFB1A59B;',
    'public static final int MUTED_2 = 0xFF6F7886;': 'public static final int MUTED_2 = 0xFF786B61;',
}
for old, new in repls.items():
    assert old in s, old
    s = s.replace(old, new)

s = s.replace(
    'import android.graphics.Insets;\nimport android.graphics.Typeface;',
    'import android.graphics.Insets;\nimport android.graphics.Canvas;\nimport android.graphics.ColorFilter;\nimport android.graphics.LinearGradient;\nimport android.graphics.Paint;\nimport android.graphics.Path;\nimport android.graphics.PixelFormat;\nimport android.graphics.Rect;\nimport android.graphics.RectF;\nimport android.graphics.Shader;\nimport android.graphics.Typeface;'
)
s = s.replace(
    'import android.graphics.drawable.ColorDrawable;\nimport android.graphics.drawable.GradientDrawable;',
    'import android.graphics.drawable.ColorDrawable;\nimport android.graphics.drawable.Drawable;\nimport android.graphics.drawable.GradientDrawable;'
)

old_screen = '''    public static GradientDrawable screenGradient(Activity a) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF07090D, 0xFF0A0D13, 0xFF10141B});
        return d;
    }
'''
new_screen = '''    public static GradientDrawable screenGradient(Activity a) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF080706, 0xFF0A0806, 0xFF0D0B09});
        return d;
    }
'''
assert old_screen in s
s = s.replace(old_screen, new_screen)

s = s.replace('d.setCornerRadius(dp(a, 20));', 'd.setCornerRadius(dp(a, 12));')
s = s.replace('b.setBackground(accent ? gradientRound(a, 16, ACCENT, 0xFFFF6537) : roundStroke(SURFACE_2, BORDER, 16, a));',
              'b.setBackground(accent ? round(ACCENT_2, 10, a) : roundStroke(SURFACE_2, BORDER, 10, a));')
s = s.replace('l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));\n        l.setBackground(roundStroke(SURFACE, BORDER, 20, a));',
              'l.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15));\n        l.setBackground(roundStroke(SURFACE, BORDER, 12, a));')
s = s.replace('l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));\n        l.setBackground(gradient(a, 0xFF19161A, 0xFF12171F));',
              'l.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15));\n        l.setBackground(roundStroke(0xFF19120D, BORDER_HOT, 12, a));')
s = s.replace('v.setBackground(roundStroke(SURFACE_2, BORDER, 12, a));', 'v.setBackground(roundStroke(SURFACE_2, BORDER, 7, a));')
s = s.replace('v.setBackground(roundStroke(0x332A1815, 0x665F3329, 12, a));',
              'v.setBackground(roundStroke(0x331E1008, BORDER_HOT, 7, a));')

anchor = '''    public static GradientDrawable gradient(Activity a, int... colors) {
'''
assert anchor in s
custom = r'''    public static Drawable burnCard(Activity a, boolean active) {
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
            this.radius = 12f * density;
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
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setColor(active ? BORDER_HOT : BORDER);
            canvas.drawRoundRect(r, radius, radius, paint);

            if (!active) return;

            float top = r.top + 10f * density;
            float bottom = r.bottom - 10f * density;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(r.left, top, r.left, bottom,
                    new int[]{0xFFFFD05A, ACCENT_2, ACCENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(r.left, top, r.left + edge, bottom), edge, edge, paint);

            tongue.reset();
            float y = top + (bottom - top) * 0.30f;
            tongue.moveTo(r.left + edge, y + 10f * density);
            tongue.cubicTo(r.left + edge + 9f * density, y + 6f * density,
                    r.left + edge + 4f * density, y - 3f * density,
                    r.left + edge + 12f * density, y - 8f * density);
            tongue.cubicTo(r.left + edge + 11f * density, y,
                    r.left + edge + 6f * density, y + 5f * density,
                    r.left + edge, y + 14f * density);
            tongue.close();
            canvas.drawPath(tongue, paint);
            paint.setShader(null);
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
            paint.setShader(new LinearGradient(b.left, y, b.right, y,
                    new int[]{ACCENT, ACCENT_2, 0xFFFFC857},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(b.left + line * 2, y, b.right - line * 2, b.bottom, paint);
            paint.setShader(null);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

'''
s = s.replace(anchor, custom + anchor)

old_tab = '''        b.setTextColor(selected ? 0xFFFFFFFF : MUTED);
        b.setBackground(selected ? roundStroke(0x332A1815, 0x665F3329, 18, a) : round(0x00000000,18,a));
'''
new_tab = '''        b.setTextColor(selected ? ACCENT_2 : MUTED);
        b.setBackground(tabChrome(a, selected));
'''
assert old_tab in s
s = s.replace(old_tab, new_tab)

s = s.replace('new int[]{ACCENT,0xFF69717D}', 'new int[]{ACCENT_2,0xFF6D6258}')
s = s.replace('new int[]{0x88FF4B2B,0x66343B46}', 'new int[]{0x99FF5A16,0x66423931}')
wr(ui_path, s)

main = java / "MainActivity.java"
s = rd(main)
old = 'row.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,20,this));if(Build.VERSION.SDK_INT>=21)row.setElevation(Ui.dp(this,1));'
assert old in s
s = s.replace(old, 'row.setBackground(Ui.burnCard(this,e.enabled));if(Build.VERSION.SDK_INT>=21)row.setElevation(0);')
wr(main, s)

clock = java / "ClockActivity.java"
s = rd(clock)
pairs = {
    'worldTab.setBackground("world".equals(mode)?Ui.roundStroke(0x332A1815,0x665F3329,18,this):Ui.round(0x00000000,18,this));':
        'worldTab.setBackground(Ui.tabChrome(this,"world".equals(mode)));',
    'timerTab.setBackground("timer".equals(mode)?Ui.roundStroke(0x332A1815,0x665F3329,18,this):Ui.round(0x00000000,18,this));':
        'timerTab.setBackground(Ui.tabChrome(this,"timer".equals(mode)));',
    'stopwatchTab.setBackground("stopwatch".equals(mode)?Ui.roundStroke(0x332A1815,0x665F3329,18,this):Ui.round(0x00000000,18,this));':
        'stopwatchTab.setBackground(Ui.tabChrome(this,"stopwatch".equals(mode)));',
}
for old, new in pairs.items():
    assert old in s, old
    s = s.replace(old, new)
s = s.replace('?0xFFFFFFFF:Ui.MUTED', '?Ui.ACCENT_2:Ui.MUTED')
wr(clock, s)

wr(res / "drawable/widget_bg.xml", '''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#F50B0907" />
            <stroke android:width="1dp" android:color="#553A2A1F" />
            <corners android:radius="14dp" />
            <padding android:left="14dp" android:top="10dp" android:right="14dp" android:bottom="10dp" />
        </shape>
    </item>
    <item android:width="3dp" android:gravity="start|center_vertical" android:top="10dp" android:bottom="10dp">
        <shape android:shape="rectangle">
            <gradient android:angle="90" android:startColor="#FFFFC857"
                      android:centerColor="#FFFF7A00" android:endColor="#FFFF3A14" />
            <corners android:radius="3dp" />
        </shape>
    </item>
</layer-list>
''')

brand = res / "values/ignido_brand.xml"
s = rd(brand)
s = s.replace('#080A0E', '#080706').replace('#FF4B2B', '#FF3A14').replace('#FF9A3C', '#FF7A00').replace('#12161D', '#15110D')
wr(brand, s)

assert 'versionName = "1.7.4"' in rd(app / "build.gradle.kts")
assert 'versionCode = 85' in rd(app / "build.gradle.kts")
assert 'public static final int ACCENT = 0xFFFF3A14;' in rd(ui_path)
assert 'return new BurnCardDrawable(a, active);' in rd(ui_path)
assert 'row.setBackground(Ui.burnCard(this,e.enabled))' in rd(main)
assert 'Ui.tabChrome(this,"world".equals(mode))' in rd(clock)
assert '<layer-list' in rd(res / "drawable/widget_bg.xml")
print("IGNIDO Wake v1.7.4 fire pass applied")
