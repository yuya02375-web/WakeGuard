from pathlib import Path
import re
root=Path('WakeGuard/app')

def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=s.replace('versionCode = 122','versionCode = 130',1).replace('versionName = "2.2.2"','versionName = "2.3.0"',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/Ui.java'; s=read(p)
if 'import android.graphics.Color;' not in s: s=s.replace('import android.graphics.Canvas;','import android.graphics.Canvas;\nimport android.graphics.Color;',1)
repls={
'public static final int BG = 0xFF080706;':'public static final int BG = 0xFF0B0C0E;',
'public static final int BG_2 = 0xFF0D0B09;':'public static final int BG_2 = 0xFF101114;',
'public static final int SURFACE = 0xFF15110D;':'public static final int SURFACE = 0xFF151619;',
'public static final int SURFACE_2 = 0xFF1C160F;':'public static final int SURFACE_2 = 0xFF1B1C20;',
'public static final int SURFACE_3 = 0xFF261C13;':'public static final int SURFACE_3 = 0xFF222329;',
'public static final int BORDER = 0xFF3A2A1F;':'public static final int BORDER = 0xFF2A2C31;',
'public static final int BORDER_HOT = 0x887A351B;':'public static final int BORDER_HOT = 0x665C321C;',
'public static final int ACCENT = 0xFFFF3A14;':'public static final int ACCENT = 0xFFFF5A1F;',
'public static final int ACCENT_2 = 0xFFFF7A00;':'public static final int ACCENT_2 = 0xFFFF6A00;',
'public static final int TEXT = 0xFFFFF8F1;':'public static final int TEXT = 0xFFF5F5F7;',
'public static final int MUTED = 0xFFB1A59B;':'public static final int MUTED = 0xFFAAAAB2;',
'public static final int MUTED_2 = 0xFF968A80;':'public static final int MUTED_2 = 0xFF7E8088;',
}
for a,b in repls.items():
    if a not in s: raise SystemExit('Ui constant missing: '+a)
    s=s.replace(a,b,1)
s=s.replace('this.radius = 12f * density;','this.radius = 0f;',1)
old='''            paint.setStyle(Paint.Style.STROKE);
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
            paint.setShader(null);'''
new='''            if (!active) return;

            float top = r.top + 12f * density;
            float bottom = r.bottom - 12f * density;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(ACCENT_2);
            canvas.drawRect(r.left, top, r.left + edge, bottom, paint);'''
if old not in s: raise SystemExit('BurnCard block missing')
s=s.replace(old,new,1)
old='''            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(b.left, y, b.right, y,
                    new int[]{ACCENT, ACCENT_2, 0xFFFFC857},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(b.left + line * 2, y, b.right - line * 2, b.bottom, paint);
            paint.setShader(null);'''
new='''            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(ACCENT_2);
            canvas.drawRect(b.left + line * 3, y, b.right - line * 3, b.bottom, paint);'''
if old not in s: raise SystemExit('tab block missing')
s=s.replace(old,new,1)
old='''        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF080706, 0xFF0A0806, 0xFF0D0B09});'''
new='''        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG, BG, BG_2});'''
if old not in s: raise SystemExit('screenGradient block missing')
s=s.replace(old,new,1)
old='''        b.setTextColor(accent ? 0xFFFFFFFF : TEXT);
        b.setBackground(accent ? round(ACCENT_2, 10, a) : roundStroke(SURFACE_2, BORDER, 10, a));
        b.setMinHeight(dp(a, 52));
        b.setPadding(dp(a,16),0,dp(a,16),0);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(dp(a, accent ? 3 : 1));'''
new='''        b.setTextColor(accent ? 0xFFFFFFFF : TEXT);
        b.setBackground(accent ? round(ACCENT_2, 8, a) : round(SURFACE_2, 8, a));
        b.setMinHeight(dp(a, 48));
        b.setPadding(dp(a,16),0,dp(a,16),0);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(0);'''
if old not in s: raise SystemExit('button block missing')
s=s.replace(old,new,1)
old='''        b.setTextColor(TEXT); b.setBackground(round(0x0012161D, 14, a));
        b.setMinHeight(dp(a,44));'''
new='''        b.setTextColor(TEXT); b.setBackground(new ColorDrawable(Color.TRANSPARENT));
        b.setMinHeight(dp(a,44));'''
if old not in s: raise SystemExit('ghost block missing')
s=s.replace(old,new,1)
old='''        l.setBackground(roundStroke(SURFACE, BORDER, 12, a));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(dp(a,1));'''
new='''        l.setBackground(round(SURFACE, 9, a));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(0);'''
if old not in s: raise SystemExit('card style block missing')
s=s.replace(old,new,1)
old='''        l.setBackground(roundStroke(0xFF19120D, BORDER_HOT, 12, a));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(dp(a,2));'''
new='''        l.setBackground(burnCard(a, true));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(0);'''
if old not in s: raise SystemExit('glow card block missing')
s=s.replace(old,new,1)
s=s.replace('p.setMargins(0,0,0,dp(a,12)); return p;','p.setMargins(0,0,0,dp(a,2)); return p;',1)
s=s.replace('v.setBackground(roundStroke(SURFACE_2, BORDER, 7, a));','v.setBackground(round(SURFACE_2, 7, a));',1)
s=s.replace('v.setBackground(roundStroke(0x331E1008, BORDER_HOT, 7, a));','v.setBackground(round(0x332A1A10, 7, a));',1)
s=s.replace('View v = new View(a); v.setBackgroundColor(0x992C3442);','View v = new View(a); v.setBackgroundColor(0xFF24262B);',1)
old='''        TextView v = text(a, s, 12, ACCENT_2);'''
new='''        TextView v = text(a, s, 12, MUTED);'''
if old not in s: raise SystemExit('section header missing')
s=s.replace(old,new,1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/MainActivity.java'; s=read(p)
s=s.replace('TextView title=Ui.title(this,"アラーム",30);','TextView title=Ui.title(this,"アラーム",28);',1)
s=s.replace('list.setPadding(Ui.dp(this,22),Ui.dp(this,4),Ui.dp(this,22),Ui.dp(this,36));','list.setPadding(Ui.dp(this,18),Ui.dp(this,4),Ui.dp(this,18),Ui.dp(this,36));',1)
s=s.replace('row.setPadding(Ui.dp(this,17),Ui.dp(this,17),Ui.dp(this,17),Ui.dp(this,17));','row.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14));',1)
s=s.replace('TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),43);','TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),40);',1)
s=s.replace('TextView statsLabel=Ui.text(this,"🔥  ストリーク",15,Ui.TEXT);','TextView statsLabel=Ui.text(this,"ストリーク",15,Ui.TEXT);',1)
s=s.replace('I18n.tr(this,"🔥 "+StreakTracker.displayCurrent(this)+"日  ·  最高 "+Prefs.bestStreak(this)+"日")','I18n.tr(this,StreakTracker.displayCurrent(this)+"日  ·  最高 "+Prefs.bestStreak(this)+"日")',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/TimeLogActivity.java'; s=read(p)
intro='''        TextView intro=text("フォルダーごとに時間を記録・集計",13,Ui.MUTED);intro.setPadding(0,0,0,Ui.dp(this,14));body.addView(intro);
'''
if intro not in s: raise SystemExit('TimeLog intro missing')
s=s.replace(intro,'',1)
s=s.replace('card.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,14));','card.setPadding(Ui.dp(this,14),Ui.dp(this,13),Ui.dp(this,14),Ui.dp(this,13));',1)
s=s.replace('lp.setMargins(0,0,0,Ui.dp(this,10));','lp.setMargins(0,0,0,Ui.dp(this,2));',1)
s=s.replace('"まだ記録がありません。上のストップウォッチで計測するか、右上の＋から開始・終了時刻を追加できます。"','"まだ記録がありません"',1)
s=s.replace('TextView label=text("ストップウォッチ計測",12,Ui.MUTED);','TextView label=text("計測",12,Ui.MUTED);',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/SystemSettingsActivity.java'; s=read(p)
s=s.replace('IGNIDO Wake 設定','設定')
write(p,s)

assert 'versionName = "2.3.0"' in read('build.gradle.kts')
assert 'versionCode = 130' in read('build.gradle.kts')
assert 'this.radius = 0f;' in read('src/main/java/jp/wakeguard/alarm/Ui.java')
assert 'new ColorDrawable(Color.TRANSPARENT)' in read('src/main/java/jp/wakeguard/alarm/Ui.java')
assert '🔥  ストリーク' not in read('src/main/java/jp/wakeguard/alarm/MainActivity.java')
assert 'フォルダーごとに時間を記録・集計' not in read('src/main/java/jp/wakeguard/alarm/TimeLogActivity.java')
print('Android 2.3.0 native-polish patch applied')
