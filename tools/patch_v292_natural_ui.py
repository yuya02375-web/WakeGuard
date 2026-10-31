from pathlib import Path
p=Path('app/src/main/java/jp/wakeguard/alarm')

def edit(name, old, new, count=1):
    f=p/name
    text=f.read_text('utf-8')
    assert old in text, f'{name}: missing {old[:100]}'
    f.write_text(text.replace(old,new,count),'utf-8')

gradle=Path('app/build.gradle.kts')
s=gradle.read_text('utf-8')
assert 'versionCode = 191' in s and 'versionName = "2.9.1"' in s
s=s.replace('versionCode = 191','versionCode = 192').replace('versionName = "2.9.1"','versionName = "2.9.2"')
gradle.write_text(s,'utf-8')

# Common icon and UI components. Vector paths come from official Google Material Icons, Apache-2.0.
ui=p/'Ui.java'
s=ui.read_text('utf-8')
s=s.replace('import android.widget.Button;', 'import android.widget.Button;\nimport android.widget.ImageButton;\nimport android.widget.ImageView;')
s=s.replace('public static final int ACCENT_2 = 0xFFFF8754;', 'public static final int ACCENT_2 = 0xFFFF956C;')
s=s.replace('public static final int SURFACE = 0xFF1B1E26;', 'public static final int SURFACE = 0xFF1B2028;')
s=s.replace('public static final int BORDER = 0xFF343946;', 'public static final int BORDER = 0xFF323741;')
start=s.index('    public static Button bottomTab(Activity a, String text, boolean selected) {')
end=s.index('    public static void styleSwitch(',start)
replacement='''    /** Material icon navigation. Both icon and label update when switching clock modes. */
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
        b.setTextSize(11);
        b.setTextColor(active?ACCENT_2:MUTED);
        b.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
        b.setBackground(round(active?0x353D2B29:Color.TRANSPARENT,18,a));
        int id=navIcon(name,active);
        Drawable icon=a.getDrawable(id).mutate();
        icon.setTint(active?ACCENT_2:MUTED);
        icon.setBounds(0,0,dp(a,24),dp(a,24));
        b.setCompoundDrawables(null,icon,null,null);
    }

    private static int navIcon(String name, boolean active) {
        if("アラーム".equals(name)) return active?R.drawable.ic_v292_nav_alarm_fill:R.drawable.ic_v292_nav_alarm_outline;
        if("時計".equals(name)) return active?R.drawable.ic_v292_nav_world_fill:R.drawable.ic_v292_nav_world_outline;
        if("タイマー".equals(name)) return active?R.drawable.ic_v292_nav_timer_fill:R.drawable.ic_v292_nav_timer_outline;
        if("ストップウォッチ".equals(name)) return active?R.drawable.ic_v292_nav_stopwatch_fill:R.drawable.ic_v292_nav_stopwatch_outline;
        return active?R.drawable.ic_v292_nav_streak_fill:R.drawable.ic_v292_nav_streak_outline;
    }

    /** Standard 48dp icon touch target with a restrained, stateful surface. */
    public static ImageButton iconButton(Activity a, int drawableId, boolean primary, String label) {
        ImageButton b=new ImageButton(a);
        b.setImageResource(drawableId);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setImageTintList(ColorStateList.valueOf(primary?0xFF251811:TEXT));
        b.setBackground(round(primary?ACCENT_2:SURFACE_2,16,a));
        b.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));
        b.setMinimumWidth(dp(a,48)); b.setMinimumHeight(dp(a,48));
        b.setContentDescription(I18n.tr(a,label));
        b.setFocusable(true);
        if(Build.VERSION.SDK_INT>=21)b.setElevation(0);
        return b;
    }

'''
s=s[:start]+replacement+s[end:]
# Reduce the intrusive underline used by old clock mode navigation (updated via updateBottomTab).
ui.write_text(s,'utf-8')

# The clock screen uses the same icon affordances and selected tab state as the alarm screen.
edit('ClockActivity.java','private Button screenAction, worldLayoutAction;', 'private ImageButton screenAction, worldLayoutAction;')
edit('ClockActivity.java','''worldLayoutAction = Ui.ghostButton(this,"▦");
        worldLayoutAction.setTextSize(22); worldLayoutAction.setMinWidth(Ui.dp(this,48)); worldLayoutAction.setVisibility(View.GONE);''','''worldLayoutAction = Ui.iconButton(this,R.drawable.ic_v292_action_grid,false,"時計のレイアウトを切り替え");
        worldLayoutAction.setVisibility(View.GONE);''')
edit('ClockActivity.java','''screenAction = Ui.ghostButton(this,"＋");
        screenAction.setTextSize(27); screenAction.setMinWidth(Ui.dp(this,52)); screenAction.setVisibility(View.GONE);''','''screenAction = Ui.iconButton(this,R.drawable.ic_v292_action_add,true,"追加する");
        screenAction.setVisibility(View.GONE);''')
edit('ClockActivity.java','''if(world){worldLayoutAction.setText(worldOverview()?"☷":"▦");worldLayoutAction.setContentDescription(I18n.tr(this,worldOverview()?"詳細表示に切り替え":"一覧表示に切り替え"));}''','''if(world){worldLayoutAction.setImageResource(worldOverview()?R.drawable.ic_v292_action_list:R.drawable.ic_v292_action_grid);worldLayoutAction.setContentDescription(I18n.tr(this,worldOverview()?"詳細表示に切り替え":"一覧表示に切り替え"));}''')
old='''            worldTab.setTextColor("world".equals(mode)?Ui.ACCENT_2:Ui.MUTED);
            timerTab.setTextColor("timer".equals(mode)?Ui.ACCENT_2:Ui.MUTED);
            stopwatchTab.setTextColor("stopwatch".equals(mode)?Ui.ACCENT_2:Ui.MUTED);
            worldTab.setBackground(Ui.tabChrome(this,"world".equals(mode)));
            timerTab.setBackground(Ui.tabChrome(this,"timer".equals(mode)));
            stopwatchTab.setBackground(Ui.tabChrome(this,"stopwatch".equals(mode)));'''
new='''            Ui.updateBottomTab(this,worldTab,"world".equals(mode));
            Ui.updateBottomTab(this,timerTab,"timer".equals(mode));
            Ui.updateBottomTab(this,stopwatchTab,"stopwatch".equals(mode));'''
edit('ClockActivity.java',old,new)
# Fix huge empty clock tiles by sizing them by content rather than viewport height.
edit('ClockActivity.java','''int usableH=Math.max(Ui.dp(this,240),screenH-Ui.dp(this,205)-gap*Math.max(0,rows-1));
        int cardH=Math.max(Ui.dp(this,34),usableH/rows);''','''int cardH=Ui.dp(this,columns<=2?184:columns==3?164:142);''')
edit('ClockActivity.java','''card.setPadding(Ui.dp(this,6),Ui.dp(this,4),Ui.dp(this,6),Ui.dp(this,4));card.setBackground(Ui.round(Ui.SURFACE,14,this));''','''card.setPadding(Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,12));card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,18,this));''')

# Main page: real settings/add icons, not text glyphs.
edit('MainActivity.java','''Button settings=Ui.ghostButton(this,"設定");settings.setContentDescription("アプリ設定");''','''ImageButton settings=Ui.iconButton(this,R.drawable.ic_v292_action_settings,false,"アプリ設定");''')
edit('MainActivity.java','''Button add=Ui.button(this,"＋",true);add.setTextSize(24);add.setContentDescription("アラームを追加");''','''ImageButton add=Ui.iconButton(this,R.drawable.ic_v292_action_add,true,"アラームを追加");''')
# Back icon on editor consistently sized for 48dp tap target.
edit('AlarmEditorActivity.java','''Button back=Ui.ghostButton(this,"←");''','''ImageButton back=Ui.iconButton(this,R.drawable.ic_v292_action_back,false,"戻る");''')
# Replace unreadable native checkboxes with 7 large day chips, preserving exact dayMask logic.
old='''LinearLayout dayRow=new LinearLayout(this);String[]dn={"月","火","水","木","金","土","日"};for(int i=0;i<7;i++){days[i]=new CheckBox(this);days[i].setText(I18n.tr(this,dn[i]));days[i].setTextColor(Ui.TEXT);days[i].setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));days[i].setGravity(Gravity.CENTER);dayRow.addView(days[i],new LinearLayout.LayoutParams(0,-2,1));}body.addView(dayRow,Ui.gapTop(this,4));'''
new='''LinearLayout dayRow=new LinearLayout(this);String[]dn={"月","火","水","木","金","土","日"};for(int i=0;i<7;i++){
            CheckBox day=new CheckBox(this);days[i]=day;
            day.setText(I18n.tr(this,dn[i]));day.setButtonDrawable(null);
            day.setGravity(Gravity.CENTER);day.setTextSize(13);day.setMinHeight(Ui.dp(this,48));
            day.setPadding(0,0,0,0);
            day.setTextColor(Ui.MUTED);
            day.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));
            day.setOnCheckedChangeListener((v,checked)->{
                day.setTextColor(checked?Ui.BG:Ui.MUTED);
                day.setBackground(checked?Ui.round(Ui.ACCENT_2,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));
            });
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1f);
            cell.setMargins(Ui.dp(this,2),0,Ui.dp(this,2),0);dayRow.addView(day,cell);
        }body.addView(dayRow,Ui.gapTop(this,10));'''
edit('AlarmEditorActivity.java',old,new)

# Align stats and editor surfaces with the unified language without modifying the companion artwork.
edit('StatsActivity.java','''companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,272)));''','''companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,252)));''')

print('2.9.2 natural Google Material icons, navigation, clock card sizing, settings/plus/back, weekday chips applied')
