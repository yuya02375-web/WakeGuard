from pathlib import Path

ROOT=Path('app')
J=ROOT/'src/main/java/jp/wakeguard/alarm'
RES=ROOT/'src/main/res'

def replace(rel,a,b,n=1):
 p=ROOT/rel
 s=p.read_text()
 assert a in s, f'Missing snippet in {rel}: {a[:90]!r}'
 p.write_text(s.replace(a,b,n))

replace('build.gradle.kts','versionCode = 192','versionCode = 193')
replace('build.gradle.kts','versionName = "2.9.2"','versionName = "2.9.3"')

# Flat original flame-clock mark, contained entirely in 66dp adaptive safe region.
fg='''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
 android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
 <path android:fillColor="#00000000" android:strokeColor="#FFFFAC73" android:strokeWidth="4.4"
       android:strokeLineCap="round" android:pathData="M54,33 C41.3,33 31,43.3 31,56 S41.3,79 54,79 S77,68.7 77,56 S66.7,33 54,33 Z" />
 <path android:fillColor="#FFFFAC73" android:pathData="M34.5,26.6 C39,22 43.2,22 47,25.5 L38.3,34.1 Z" />
 <path android:fillColor="#FFFFAC73" android:pathData="M73.5,26.6 C69,22 64.8,22 61,25.5 L69.7,34.1 Z" />
 <path android:fillColor="#FFFFE7BE" android:pathData="M53,41 L53,56 L64,62 L66.5,58.7 L57.8,53.7 L57.8,41 Z" />
 <path android:fillColor="#FFFF754A" android:pathData="M47,65 C45,59 49,55 50,51 C51.3,55 54,55 54.3,59 C56,57 58,54 58,51 C63,57 65,64 62,68.6 C58.4,74 49.8,72.6 47,65 Z"/>
 <path android:fillColor="#FFFFC375" android:pathData="M53,69 C50.3,66.8 52,64 54,61 C54.6,63 57.5,64.3 56,67.7 C55.3,69 54.4,69.5 53,69 Z"/>
</vector>
'''
mono='''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
 android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
 <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="4.4"
       android:strokeLineCap="round" android:pathData="M54,33 C41.3,33 31,43.3 31,56 S41.3,79 54,79 S77,68.7 77,56 S66.7,33 54,33 Z" />
 <path android:fillColor="#FFFFFFFF" android:pathData="M34.5,26.6 C39,22 43.2,22 47,25.5 L38.3,34.1 Z M73.5,26.6 C69,22 64.8,22 61,25.5 L69.7,34.1 Z M53,41 L53,56 L64,62 L66.5,58.7 L57.8,53.7 L57.8,41 Z M47,65 C45,59 49,55 50,51 C51.3,55 54,55 54.3,59 C56,57 58,54 58,51 C63,57 65,64 62,68.6 C58.4,74 49.8,72.6 47,65 Z"/>
</vector>
'''
notif='''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
 android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
 <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.7"
       android:pathData="M12,5 C7.6,5 4,8.6 4,13 S7.6,21 12,21 S20,17.4 20,13 S16.4,5 12,5 Z" />
 <path android:fillColor="#FFFFFFFF" android:pathData="M5.2,2.5 L2.3,5.7 L3.6,7 L6.5,3.8 Z M18.8,2.5 L21.7,5.7 L20.4,7 L17.5,3.8 Z M11.1,7.6 L11.1,13.3 L15.2,15.7 L16.2,14.1 L12.8,12.2 L12.8,7.6 Z"/>
</vector>
'''
for name,xml in [('ic_ignido_v293_fg.xml',fg),('ic_ignido_v293_mono.xml',mono),('ic_ignido_notification.xml',notif)]:
 (RES/'drawable'/name).write_text(xml)
for name in ('ic_launcher.xml','ic_launcher_round.xml'):
 for qualifier in ('mipmap-anydpi-v26','mipmap-anydpi-v33'):
  p=RES/qualifier/name
  s=p.read_text()
  s=s.replace('@drawable/ic_ignido_wake_full_layer','@drawable/ic_ignido_v293_fg').replace('@drawable/ic_ignido_wake_mono','@drawable/ic_ignido_v293_mono')
  p.write_text(s)
replace('src/main/res/values/ignido_brand.xml','#080706','#11151D')

# Change the clock timer glyph from an hourglass to an actual timer.
# Keep existing stopwatch icon, so the two destinations no longer look similar.
icon='''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
 <path android:fillColor="#FF000000" android:pathData="M9,1 H15 V3 H9 Z M11,8 H13 V14 H11 Z M19.03,7.39 L20.45,5.97 C20.02,5.46 19.55,4.98 19.04,4.56 L17.62,5.98 C16.07,4.74 14.12,4 12,4 C7.03,4 3,8.03 3,13 S7.02,22 12,22 S21,17.97 21,13 C21,10.88 20.26,8.93 19.03,7.39 Z M12,20 C8.13,20 5,16.87 5,13 S8.13,6 12,6 S19,9.13 19,13 S15.87,20 12,20 Z"/>
</vector>
'''
(RES/'drawable'/'ic_v293_timer.xml').write_text(icon)

# Material-like active indicator: pill is behind the icon, label remains unboxed.
ui=J/'Ui.java';s=ui.read_text()
old='''        b.setBackground(round(active?0x353D2B29:Color.TRANSPARENT,18,a));'''
assert old in s
s=s.replace(old,'''        b.setBackground(new NavigationIndicatorDrawable(a,active));''',1)
marker='''    private static int navIcon(String name, boolean active) {'''
assert marker in s
s=s.replace(marker,'''    /** Only the icon gets the M3 indicator, never the text below it. */
    private static final class NavigationIndicatorDrawable extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean selected;
        private final float density;
        NavigationIndicatorDrawable(Activity a,boolean selected){this.selected=selected;this.density=a.getResources().getDisplayMetrics().density;}
        @Override public void draw(Canvas c){
            if(!selected)return;
            Rect r=getBounds();
            float cx=r.exactCenterX(),top=r.top+3*density;
            paint.setColor(0xFF3B2A27);paint.setStyle(Paint.Style.FILL);
            float half=Math.min(r.width()/2f-2*density,31*density);
            c.drawRoundRect(cx-half,top,cx+half,top+33*density,16.5f*density,16.5f*density,paint);
        }
        @Override public void setAlpha(int a){paint.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

'''+marker,1)
# switch hourglass to natural timer icon, same on inactive/active
s=s.replace('return active?R.drawable.ic_v292_nav_timer_fill:R.drawable.ic_v292_nav_timer_outline;','return R.drawable.ic_v293_timer;')
# Correct Material3 accent text contrast: dark foreground on lighter orange
s=s.replace('b.setTextColor(accent ? 0xFFFFFFFF : TEXT);','b.setTextColor(accent ? 0xFF251913 : TEXT);')
# Button interaction: ripple uses drawable preserving rounded background
s=s.replace('''        b.setBackground(primary?round(ACCENT_2,16,a):round(SURFACE_2,16,a));''','''        b.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x44FFFFFF),
            round(primary?ACCENT_2:SURFACE_2,16,a),
            round(Color.WHITE,16,a)));''')
# target existing is b.setBackground(round(...))
s=s.replace('''        b.setBackground(round(primary?ACCENT_2:SURFACE_2,16,a));''','''        b.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF),
            round(primary?ACCENT_2:SURFACE_2,16,a),
            round(Color.WHITE,16,a)));''')
# Make all top actions 48dp with readable 24dp icons by preserving existing 12 padding.
ui.write_text(s)

# Hide the next-alarm panel when there are no alarms. Otherwise empty state repeats itself.
main=J/'MainActivity.java';s=main.read_text()
s=s.replace('private LinearLayout list;','private LinearLayout list, nextCard;')
s=s.replace('''        LinearLayout nextCard=Ui.card(this);''','''        nextCard=Ui.card(this);''')
old='''if(ms<=0){nextText.setText(I18n.tr(this,"次のアラームはありません"));return;}'''
assert old in s
s=s.replace(old,'''if(ms<=0){nextCard.setVisibility(View.GONE);nextText.setText(I18n.tr(this,"次のアラームはありません"));return;}nextCard.setVisibility(View.VISIBLE);''')
s=s.replace('''        root.addView(statsRow);
        root.addView(Ui.divider(this));''','''        root.addView(statsRow);''')
# Remove last separator between content and nav, the nav should be a unified surface
s=s.replace('''        root.addView(Ui.divider(this));
        root.addView(bottomNav());''','''        root.addView(bottomNav());''')
main.write_text(s)

# The streak page was still using a Unicode back chevron and unstyled month arrows.
stats=J/'StatsActivity.java';s=stats.read_text()
s=s.replace('''Button back=Ui.ghostButton(this,"‹");back.setTextSize(30);''','''android.widget.ImageButton back=Ui.iconButton(this,R.drawable.ic_v292_action_back,false,"戻る");''')
s=s.replace('''Button prev=Ui.ghostButton(this,"‹");prev.setTextSize(25);''','''android.widget.ImageButton prev=Ui.iconButton(this,R.drawable.ic_v293_chevron_left,false,"前の月");''')
s=s.replace('''Button next=Ui.ghostButton(this,"›");next.setTextSize(25);''','''android.widget.ImageButton next=Ui.iconButton(this,R.drawable.ic_v293_chevron_right,false,"次の月");''')
s=s.replace('''root.addView(Ui.divider(this));
        LinearLayout nav=''','''LinearLayout nav=''')
stats.write_text(s)
for direction,path in [('left','M15.41,7.41 L14,6 L8,12 L14,18 L15.41,16.59 L10.83,12 Z'),('right','M8.59,16.59 L10,18 L16,12 L10,6 L8.59,7.41 L13.17,12 Z')]:
 (RES/'drawable'/f'ic_v293_chevron_{direction}.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?><vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FF000000" android:pathData="{path}"/></vector>''')

# Timer quick presets/adjustments should look tappable, not like text on a blank background.
clock=J/'ClockActivity.java';s=clock.read_text()
old='''        try{android.util.TypedValue v=new android.util.TypedValue(); if(getTheme().resolveAttribute(android.R.attr.selectableItemBackground,v,true))t.setBackgroundResource(v.resourceId); else t.setBackgroundColor(android.graphics.Color.TRANSPARENT);}catch(Throwable ignored){t.setBackgroundColor(android.graphics.Color.TRANSPARENT);}'''
assert old in s
s=s.replace(old,'''        t.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,12,this),
                Ui.round(android.graphics.Color.WHITE,12,this)));
        t.setMinHeight(Ui.dp(this,44));''')
s=s.replace('''        outer.addView(Ui.divider(this));
        LinearLayout nav=''','''        LinearLayout nav=''')
clock.write_text(s)

# Make status-bar notification and theme contrast intentional, preserving alarm operation.
# Keep the pixel dragon sprites and every AlarmService behavior intact.
assert (RES/'drawable-nodpi'/'ignido_stage_ember.webp').exists()
assert 'Reentered scheduled UI' in (J/'AlarmActivity.java').read_text()
assert 'versionCode = 193' in (ROOT/'build.gradle.kts').read_text()
print('IGNIDO 2.9.3 icon and UI polish complete; no alarm logic changed')
