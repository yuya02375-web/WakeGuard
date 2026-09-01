from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v100.py", run_name="__main__")
root = Path("WakeGuard")
java = root / "app/src/main/java/jp/wakeguard/alarm"

# 1) Window setup: dark window background, no activity swipe animation,
#    and proper Android 15/16 system-bar/display-cutout insets.
p = java / "Ui.java"
s = p.read_text(encoding="utf-8")
s = s.replace('import android.app.Activity;\n', 'import android.app.Activity;\nimport android.content.Intent;\nimport android.graphics.Insets;\nimport android.graphics.drawable.ColorDrawable;\nimport android.os.Build;\nimport android.view.WindowInsets;\n')
insert = '''\n    public static void prepareActivity(Activity a) {\n        statusBar(a);\n        try { a.getWindow().setBackgroundDrawable(new ColorDrawable(BG)); } catch (Throwable ignored) {}\n        try { a.getWindow().setWindowAnimations(0); } catch (Throwable ignored) {}\n        if (Build.VERSION.SDK_INT >= 34) {\n            try { a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0); } catch (Throwable ignored) {}\n            try { a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0); } catch (Throwable ignored) {}\n        }\n    }\n\n    public static void applySystemBarInsets(Activity a, View root) {\n        final int baseL = root.getPaddingLeft();\n        final int baseT = root.getPaddingTop();\n        final int baseR = root.getPaddingRight();\n        final int baseB = root.getPaddingBottom();\n        root.setOnApplyWindowInsetsListener((v, wi) -> {\n            int left=0, top=0, right=0, bottom=0;\n            try {\n                if (Build.VERSION.SDK_INT >= 30) {\n                    Insets x = wi.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());\n                    left=x.left; top=x.top; right=x.right; bottom=x.bottom;\n                } else {\n                    left=wi.getSystemWindowInsetLeft(); top=wi.getSystemWindowInsetTop();\n                    right=wi.getSystemWindowInsetRight(); bottom=wi.getSystemWindowInsetBottom();\n                }\n            } catch (Throwable ignored) {}\n            v.setPadding(baseL + left, baseT + top, baseR + right, baseB + bottom);\n            return wi;\n        });\n        try { root.requestApplyInsets(); } catch (Throwable ignored) {}\n    }\n\n    public static void launchNoAnimation(Activity a, Intent i) {\n        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);\n        a.startActivity(i);\n        try { a.overridePendingTransition(0,0); } catch (Throwable ignored) {}\n    }\n\n    public static void finishNoAnimation(Activity a) {\n        a.finish();\n        try { a.overridePendingTransition(0,0); } catch (Throwable ignored) {}\n    }\n'''
idx = s.rfind('\n    public static void statusBar(Activity a) {')
s = s[:idx] + insert + s[idx:]
p.write_text(s, encoding="utf-8")

# Use the stronger window setup on normal app screens.
for name in ["MainActivity.java","ClockActivity.java","AlarmEditorActivity.java","SystemSettingsActivity.java","MultiAlarmActivity.java"]:
    p = java / name
    s = p.read_text(encoding="utf-8")
    s = s.replace('Ui.statusBar(this);', 'Ui.prepareActivity(this);')
    p.write_text(s, encoding="utf-8")

# 2) Main alarm screen: respect the status bar/cutout and open clock tools without animation.
p = java / "MainActivity.java"
s = p.read_text(encoding="utf-8")
s = s.replace('        setContentView(root);\n', '        setContentView(root);\n        Ui.applySystemBarInsets(this, root);\n')
s = s.replace('    private void openClock(String mode){startActivity(new Intent(this,ClockActivity.class).putExtra("mode",mode));}',
'''    private void openClock(String mode){\n        Ui.launchNoAnimation(this, new Intent(this,ClockActivity.class).putExtra("mode",mode));\n    }''')
p.write_text(s, encoding="utf-8")

# 3) Clock tools: stay on the same activity for world/timer/stopwatch; when returning to
#    alarms simply reveal the already-existing MainActivity underneath. No new activity,
#    no swipe animation, no white frame.
p = java / "ClockActivity.java"
s = p.read_text(encoding="utf-8")
s = s.replace('        setContentView(outer);\n', '        setContentView(outer);\n        Ui.applySystemBarInsets(this, outer);\n')
s = s.replace('        alarmTab.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));',
                    '        alarmTab.setOnClickListener(v -> Ui.finishNoAnimation(this));')
# Hardware/system back should behave the same way as tapping the Alarm tab.
marker = '    @Override protected void onPause() {\n        super.onPause();\n        handler.removeCallbacks(ticker);\n    }\n'
if marker in s:
    s = s.replace(marker, marker + '\n    @Override public void onBackPressed() { Ui.finishNoAnimation(this); }\n')
p.write_text(s, encoding="utf-8")

# 4) Other normal screens also avoid drawing their headers behind the status bar.
p = java / "AlarmEditorActivity.java"
s = p.read_text(encoding="utf-8")
s = s.replace('        setContentView(root);\n', '        setContentView(root);\n        Ui.applySystemBarInsets(this, root);\n')
p.write_text(s, encoding="utf-8")

p = java / "SystemSettingsActivity.java"
s = p.read_text(encoding="utf-8")
s = s.replace('body.addView(top);setContentView(sv);', 'body.addView(top);setContentView(sv);Ui.applySystemBarInsets(this, sv);')
p.write_text(s, encoding="utf-8")

# Legacy redirect activity should not animate either.
p = java / "MultiAlarmActivity.java"
s = p.read_text(encoding="utf-8")
s = s.replace('startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();',
              'Ui.launchNoAnimation(this,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));Ui.finishNoAnimation(this);')
p.write_text(s, encoding="utf-8")

# 5) Give every app window a dark preview/background before Activity.onCreate executes.
#    This removes the white flash even on devices that show a launch/transition preview.
res = root / "app/src/main/res/values"
res.mkdir(parents=True, exist_ok=True)
(res / "wakeguard_v101.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <style name="WakeGuardThemeNoFlash" parent="@style/AppTheme">\n        <item name="android:windowBackground">#101113</item>\n        <item name="android:windowDisablePreview">true</item>\n        <item name="android:windowContentTransitions">false</item>\n    </style>\n</resources>\n''', encoding="utf-8")

p = root / "app/src/main/AndroidManifest.xml"
s = p.read_text(encoding="utf-8")
s = s.replace('android:theme="@style/AppTheme"', 'android:theme="@style/WakeGuardThemeNoFlash"')
p.write_text(s, encoding="utf-8")

# Version bump.
p = root / "app/build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = re.sub(r'versionCode = \\d+', 'versionCode = 28', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.0.1"', s)
p.write_text(s, encoding="utf-8")

print("WakeGuard v1.0.1: no-flash tab navigation + system-bar safe insets applied")
