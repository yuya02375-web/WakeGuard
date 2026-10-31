from pathlib import Path

ROOT=Path('WakeGuard/app')
J=ROOT/'src/main/java/jp/wakeguard/alarm'

def edit(filename, before, after, count=1):
    p=J/filename
    src=p.read_text('utf-8')
    if before not in src:
        raise ValueError(f'Cannot find [{before[:75]}] in {filename}')
    src=src.replace(before,after,count)
    p.write_text(src,'utf-8')

def all_edit(filename, before, after):
    edit(filename,before,after,-1)

gradle=ROOT/'build.gradle.kts'
s=gradle.read_text('utf-8')
assert 'versionCode = 190' in s and 'versionName = "2.9.0"' in s
s=s.replace('versionCode = 190','versionCode = 191',1).replace('versionName = "2.9.0"','versionName = "2.9.1"',1)
gradle.write_text(s,'utf-8')

# Common design tokens. More natural, low-glare orange and consistent material-like surfaces.
ui=J/'Ui.java'
s=ui.read_text('utf-8')
colors={
 'BG':'0xFF0E1015','BG_2':'0xFF11141B','SURFACE':'0xFF1B1E26','SURFACE_2':'0xFF252932',
 'SURFACE_3':'0xFF303540','BORDER':'0xFF343946','BORDER_HOT':'0x447F584B',
 'ACCENT':'0xFFFFA073','ACCENT_2':'0xFFFF8754','CYAN':'0xFF8ABCD5',
 'BLUE':'0xFF8DA8DE','VIOLET':'0xFFB39CE2','PINK':'0xFFE4A1BA',
 'TEXT':'0xFFF6F4F0','MUTED':'0xFFB1B2BC','MUTED_2':'0xFF898C98',
 'SUCCESS':'0xFF81D4AD','DANGER':'0xFFFF8C83'}
import re
for token,new in colors.items():
    pattern=r'(public static final int '+token+r'\s*=\s*)0x[0-9A-Fa-f]+;'
    s,n=re.subn(pattern,lambda m:m.group(1)+new+';',s)
    assert n==1,token
s=s.replace('this.radius = 0f;', 'this.radius = 18f * density;')
s=s.replace('this.edge = 3f * density;', 'this.edge = 3f * density;')
# subtle outline and selected marker without full height stripes
s=s.replace('''            canvas.drawRoundRect(r, radius, radius, paint);

            if (!active) return;

            float top = r.top + 12f * density;
            float bottom = r.bottom - 12f * density;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(ACCENT_2);
            canvas.drawRect(r.left, top, r.left + edge, bottom, paint);''','''            canvas.drawRoundRect(r, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(active ? 0x665E554F : BORDER);
            canvas.drawRoundRect(r, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);''')
s=s.replace('d.setCornerRadius(dp(a, 12));','d.setCornerRadius(dp(a, 18));')
s=s.replace('''        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG, BG, BG_2});''','''        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG, BG_2, BG});''')
s=s.replace('''        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(0.005f);''','''        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(0f);''')
s=s.replace('''        b.setBackground(accent ? round(ACCENT_2, 8, a) : round(SURFACE_2, 8, a));''','''        b.setBackground(accent ? round(ACCENT_2, 14, a) : roundStroke(SURFACE_2, BORDER, 14, a));''')
s=s.replace('''        b.setMinHeight(dp(a, 48));''','''        b.setMinHeight(dp(a, 52));''')
s=s.replace('''        l.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15));
        l.setBackground(round(SURFACE, 9, a));''','''        l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));
        l.setBackground(roundStroke(SURFACE, BORDER, 18, a));''',1)
s=s.replace('''        l.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15));''','''        l.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));''',1)
s=s.replace('''        p.setMargins(0,0,0,dp(a,2)); return p;''','''        p.setMargins(0,0,0,dp(a,12)); return p;''')
s=s.replace('''        v.setBackground(round(SURFACE_2, 7, a));''','''        v.setBackground(round(SURFACE_2, 12, a));''')
s=s.replace('''        v.setBackground(round(0x332A1A10, 7, a));''','''        v.setBackground(round(0x443C2820, 12, a));''')
s=s.replace('''        View v = new View(a); v.setBackgroundColor(0xFF24262B);''','''        View v = new View(a); v.setBackgroundColor(BORDER);''')
s=s.replace('''        TextView v = text(a, s, 12, MUTED);
        v.setTypeface(null, Typeface.BOLD);''','''        TextView v = text(a, s, 14, MUTED);
        v.setTypeface(null, Typeface.BOLD);''')
s=s.replace('''        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(0.055f);''','''        if (Build.VERSION.SDK_INT >= 21) v.setLetterSpacing(0.008f);''')
# Remove old cramped auto-resizing text tabs and draw consistent pictograms above labels.
a=s.index('    public static Button bottomTab(Activity a, String text, boolean selected) {')
b=s.index('    public static void styleSwitch(',a)
s=s[:a]+'''    public static Button bottomTab(Activity a, String text, boolean selected) {
        Button b = new Button(a);
        String translated = I18n.tr(a,text);
        String compact = "ストップウォッチ".equals(text) ? I18n.tr(a,"計測") : translated;
        b.setText(compact);
        b.setContentDescription(translated);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setTextSize(11);
        b.setTextColor(selected ? ACCENT_2 : MUTED);
        b.setTypeface(null,selected ? Typeface.BOLD : Typeface.NORMAL);
        b.setIncludeFontPadding(false);
        b.setBackground(selected ? round(0x443D2B26,16,a) : round(Color.TRANSPARENT,16,a));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(a,1),dp(a,7),dp(a,1),dp(a,7));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(a,56));
        Drawable icon = new NavIcon(a,text, selected ? ACCENT_2 : MUTED);
        icon.setBounds(0,0,dp(a,22),dp(a,22));
        b.setCompoundDrawables(null,icon,null,null);
        b.setCompoundDrawablePadding(dp(a,4));
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(0);
        return b;
    }

    private static final class NavIcon extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final String key;
        final int color;
        final float d;
        NavIcon(Activity a,String key,int color) {this.key=key;this.color=color;d=a.getResources().getDisplayMetrics().density;}
        @Override public void draw(Canvas canvas) {
            Rect r=getBounds();
            canvas.save();canvas.translate(r.left,r.top);canvas.scale(r.width()/24f,r.height()/24f);
            p.setColor(color);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.9f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if("アラーム".equals(key)) {
                canvas.drawCircle(12,13,7.5f,p);canvas.drawLine(12,13,12,8.5f,p);canvas.drawLine(12,13,15.2f,15f,p);
                canvas.drawLine(4,3.5f,1.8f,6,p);canvas.drawLine(20,3.5f,22.2f,6,p);
            }else if("時計".equals(key)) {
                canvas.drawCircle(12,12,9,p);canvas.drawLine(12,12,12,6.6f,p);canvas.drawLine(12,12,16.5f,14.6f,p);
            }else if("タイマー".equals(key)) {
                canvas.drawCircle(12,13.5f,8,p);canvas.drawLine(12,13.5f,16,9.5f,p);
                canvas.drawLine(9,2.5f,15,2.5f,p);canvas.drawLine(12,5,12,3,p);
            }else if("ストップウォッチ".equals(key)) {
                canvas.drawCircle(12,13,8,p);canvas.drawLine(12,13,12,8,p);canvas.drawLine(12,13,16.5f,13,p);
                canvas.drawLine(9,2,15,2,p);canvas.drawLine(12,5,12,2,p);canvas.drawLine(19,7,21,5,p);
            }else{
                Path flame=new Path();flame.moveTo(12,2);flame.cubicTo(8,8,14,10,9,15);flame.cubicTo(8,12,6,11,6,10);flame.cubicTo(1,18,7,22,12,22);
                flame.cubicTo(20,22,23,14,17,9);flame.cubicTo(17,13,15,15,15,16);flame.cubicTo(14,11,15,7,12,2);flame.close();canvas.drawPath(flame,p);
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) {p.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return Math.round(22*d);}
        @Override public int getIntrinsicHeight(){return Math.round(22*d);}
    }

'''+s[b:]
# Navigation color/insets: do not change edge-to-edge flags in alarm full-screen activity.
s=s.replace('''            a.getWindow().setStatusBarColor(BG);
            a.getWindow().setNavigationBarColor(BG);''','''            a.getWindow().setStatusBarColor(BG);
            a.getWindow().setNavigationBarColor(BG);
            if (Build.VERSION.SDK_INT >= 29) a.getWindow().setNavigationBarContrastEnforced(false);''')
ui.write_text(s,'utf-8')

# Main alarm list: navigation, hierarchy, next alarm feature and more readable entries.
edit('MainActivity.java','''top.setPadding(Ui.dp(this,20),Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,8));''','''top.setPadding(Ui.dp(this,22),Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,12));''')
edit('MainActivity.java','''TextView title=Ui.title(this,"アラーム",28);''','''TextView title=Ui.title(this,"アラーム",27);''')
edit('MainActivity.java','''Button settings=Ui.ghostButton(this,"設定");''','''Button settings=Ui.ghostButton(this,"設定");settings.setContentDescription("アプリ設定");''')
edit('MainActivity.java','''Button add=Ui.ghostButton(this,"＋");add.setTextSize(25);''','''Button add=Ui.button(this,"＋",true);add.setTextSize(24);add.setContentDescription("アラームを追加");''')
edit('MainActivity.java','''nextText=Ui.text(this,"",13,Ui.MUTED);nextText.setPadding(Ui.dp(this,22),0,Ui.dp(this,22),Ui.dp(this,10));root.addView(nextText);''','''LinearLayout nextCard=Ui.card(this);
        TextView nextLabel=Ui.overline(this,"次のアラーム");nextCard.addView(nextLabel);
        nextText=Ui.text(this,"",19,Ui.TEXT);nextText.setTypeface(null,android.graphics.Typeface.BOLD);
        nextText.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,1));nextCard.addView(nextText);
        LinearLayout.LayoutParams nextLp=new LinearLayout.LayoutParams(-1,-2);
        nextLp.setMargins(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,10));root.addView(nextCard,nextLp);''')
edit('MainActivity.java','''statsRow.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,16),Ui.dp(this,10));''','''statsRow.setPadding(Ui.dp(this,23),Ui.dp(this,9),Ui.dp(this,21),Ui.dp(this,14));''')
edit('MainActivity.java','''statsRow.addView(statsArrow);
        statsRow.setOnClickListener''','''statsRow.addView(statsArrow);
        statsRow.setContentDescription("ストリークの記録を見る");
        statsRow.setOnClickListener''')
edit('MainActivity.java','''list.setPadding(Ui.dp(this,18),Ui.dp(this,4),Ui.dp(this,18),Ui.dp(this,36));''','''list.setPadding(Ui.dp(this,18),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,28));''')
edit('MainActivity.java','''nav.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),Ui.dp(this,6));''','''nav.setPadding(Ui.dp(this,9),Ui.dp(this,7),Ui.dp(this,9),Ui.dp(this,8));''')
edit('MainActivity.java','''Ui.dp(this,60),1''','''Ui.dp(this,63),1''',-1)
edit('MainActivity.java','''TextView empty=Ui.text(this,"アラームはありません。右上の＋から追加できます。",15,Ui.MUTED);empty.setPadding(0,Ui.dp(this,40),0,0);list.addView(empty);return;''','''LinearLayout empty=Ui.card(this);empty.setGravity(Gravity.CENTER);
            TextView large=Ui.title(this,"アラームを追加しましょう",20);large.setGravity(Gravity.CENTER);empty.addView(large);
            TextView note=Ui.text(this,"右上の＋から時間と解除方法を設定できます。",14,Ui.MUTED);
            note.setGravity(Gravity.CENTER);note.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,12));empty.addView(note);
            Button create=Ui.button(this,"アラームを追加",true);create.setOnClickListener(v->edit(-1));empty.addView(create);
            list.addView(empty);return;''')
edit('MainActivity.java','''row.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14));''','''row.setPadding(Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,18),Ui.dp(this,16));''')
edit('MainActivity.java','''String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),40''','''String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),38''')
edit('MainActivity.java','''TextView detail=Ui.text(this,detailText,13,Ui.MUTED);row.addView(detail,Ui.gapTop(this,4));''','''TextView detail=Ui.text(this,detailText,13,Ui.MUTED);detail.setMaxLines(3);row.addView(detail,Ui.gapTop(this,7));''')
edit('MainActivity.java','''String detailText=scheduleText+(remaining.isEmpty()?"":"   ·   "+remaining)+"   ·   "+I18n.tr(this,AlarmProfiles.missionName(e.missionType))+" "+I18n.tr(this,AlarmProfiles.missionSummary(this,e.id));''','''String detailText=scheduleText+(remaining.isEmpty()?"":"  ·  "+remaining)+"\\n"+I18n.tr(this,AlarmProfiles.missionName(e.missionType))+"  "+I18n.tr(this,AlarmProfiles.missionSummary(this,e.id));''')

# Stats hero no longer wastes half the screen: preserve pixel sprite pixels and existing growth mechanic.
edit('StatsActivity.java','''root.setBackgroundColor(Ui.BG);''','''root.setBackground(Ui.screenGradient(this));''')
edit('StatsActivity.java','''top.setPadding(Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,8));''','''top.setPadding(Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,8));''')
edit('StatsActivity.java','''TextView title=Ui.title(this,"ストリーク",28);''','''TextView title=Ui.title(this,"ストリーク",27);''')
edit('StatsActivity.java','''body.setPadding(Ui.dp(this,16),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,36));''','''body.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,28));''')
edit('StatsActivity.java','''LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(0,0,0,Ui.dp(this,10));''','''LinearLayout hero=Ui.card(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,22,this));
        hero.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,12));''')
edit('StatsActivity.java','''companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,390)));''','''companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,272)));''')
edit('StatsActivity.java','''currentValue=Ui.text(this,"0日",50,Ui.TEXT);''','''currentValue=Ui.text(this,"0日",40,Ui.TEXT);''')
edit('StatsActivity.java','''hero.addView(summary);body.addView(hero);
        body.addView(Ui.divider(this));''','''hero.addView(summary);body.addView(hero,Ui.cardParams(this));''')
edit('StatsActivity.java','''LinearLayout metrics=Ui.row(this);metrics.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,8));''','''LinearLayout metrics=Ui.row(this);metrics.setPadding(Ui.dp(this,2),Ui.dp(this,11),Ui.dp(this,2),Ui.dp(this,11));''')
edit('StatsActivity.java','''body.addView(metrics);
        body.addView(Ui.divider(this));''','''metrics.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,18,this));
        body.addView(metrics,Ui.cardParams(this));''')
edit('StatsActivity.java','''nav.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),Ui.dp(this,6));''','''nav.setPadding(Ui.dp(this,9),Ui.dp(this,7),Ui.dp(this,9),Ui.dp(this,8));''')
edit('StatsActivity.java','''Ui.dp(this,60),1''','''Ui.dp(this,63),1''',-1)
# stats portrait calendar remains scrollable and tappable.

# Clock shared navigation and top insets
edit('ClockActivity.java','''top.setPadding(Ui.dp(this,22),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,8));''','''top.setPadding(Ui.dp(this,22),Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,12));''')
edit('ClockActivity.java','''screenTitle = Ui.title(this, "世界時計", 28);''','''screenTitle = Ui.title(this, "世界時計", 27);''')
edit('ClockActivity.java','''nav.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),Ui.dp(this,6));''','''nav.setPadding(Ui.dp(this,9),Ui.dp(this,7),Ui.dp(this,9),Ui.dp(this,8));''')
edit('ClockActivity.java','''Ui.dp(this,60),1''','''Ui.dp(this,63),1''',-1)
edit('ClockActivity.java','''body.setPadding(Ui.dp(this,22), Ui.dp(this,6), Ui.dp(this,22), Ui.dp(this,36));''','''body.setPadding(Ui.dp(this,18), Ui.dp(this,8), Ui.dp(this,18), Ui.dp(this,28));''')

# Editor: a distinct time surface, consistent spacing and controls, no changes to scheduling.
edit('AlarmEditorActivity.java','''top.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,6));''','''top.setPadding(Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,10));''')
edit('AlarmEditorActivity.java','''body.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,48));''','''body.setPadding(Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18),Ui.dp(this,48));''')
edit('AlarmEditorActivity.java','''time.setTextSize(52);time.setTypeface''','''time.setTextSize(54);time.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,20,this));time.setTypeface''')
edit('AlarmEditorActivity.java','''time,new LinearLayout.LayoutParams(-1,Ui.dp(this,86))''','''time,new LinearLayout.LayoutParams(-1,Ui.dp(this,108))''')

# Ring screen readability: no changes to alarm flags, mission state, stop/snooze actions.
edit('AlarmActivity.java','''root.setPadding(Ui.dp(this,24), Ui.dp(this,36), Ui.dp(this,24), Ui.dp(this,30));''','''root.setPadding(Ui.dp(this,24), Ui.dp(this,34), Ui.dp(this,24), Ui.dp(this,24));''')
edit('AlarmActivity.java','''if(!alarmVideoMode)root.addView(Ui.divider(this),Ui.dividerTop(this,24));''','''if(!alarmVideoMode){ TextView status=Ui.overline(this,"アラームが鳴っています");status.setGravity(Gravity.CENTER);root.addView(status,Ui.gapTop(this,24)); }''')
edit('AlarmActivity.java','''missionCard.setPadding(0,Ui.dp(this,28),0,Ui.dp(this,16));''','''missionCard.setPadding(Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,14));
            if(!alarmVideoMode)missionCard.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,20,this));''')
edit('AlarmActivity.java','''root.addView(snooze,Ui.gapTop(this,6));
            root.addView(stop,Ui.gapTop(this,6));''','''root.addView(snooze,Ui.gapTop(this,12));
            root.addView(stop,Ui.gapTop(this,10));''')

print('IGNIDO 2.9.1 UI overhaul applied to Ui, Main, Stats, Clock, AlarmEditor, AlarmActivity. Alarm logic untouched.')
