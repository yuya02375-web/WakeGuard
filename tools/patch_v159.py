from pathlib import Path
import re

ROOT = Path('WakeGuard')
XML = ROOT / 'app/src/main/res/xml'
GRADLE = ROOT / 'app/build.gradle.kts'

# Default drop sizes are intentionally different per widget.
# minResize* remains 40dp so users can still shrink aggressively on launchers that allow it.
SIZES = {
    'world_clock_widget_info.xml':       (2, 2, 130, 220),
    'local_clock_widget_info.xml':       (2, 2, 130, 220),
    'multi_world_clock_widget_info.xml': (4, 2, 276, 220),
    'next_alarm_widget_info.xml':        (2, 1, 130, 110),
    'alarm_countdown_widget_info.xml':   (2, 1, 130, 110),
    'alarm_list_widget_info.xml':        (4, 2, 276, 220),
    'timer_widget_info.xml':             (3, 1, 203, 110),
    'timer_preset_widget_info.xml':      (4, 2, 276, 220),
    'stopwatch_widget_info.xml':         (3, 1, 203, 110),
    'streak_widget_info.xml':            (2, 2, 130, 220),
    'wake_stats_widget_info.xml':        (4, 2, 276, 220),
    'quick_tools_widget_info.xml':       (4, 2, 276, 220),
    'today_widget_info.xml':             (4, 2, 276, 220),
}

for name, (cw, ch, mw, mh) in SIZES.items():
    p = XML / name
    text = p.read_text(encoding='utf-8')
    text = re.sub(r'android:minWidth="[^"]+"', f'android:minWidth="{mw}dp"', text, count=1)
    text = re.sub(r'android:minHeight="[^"]+"', f'android:minHeight="{mh}dp"', text, count=1)
    text = re.sub(r'android:targetCellWidth="[^"]+"', f'android:targetCellWidth="{cw}"', text, count=1)
    text = re.sub(r'android:targetCellHeight="[^"]+"', f'android:targetCellHeight="{ch}"', text, count=1)
    # Do not tie supported resize range to the default drop size.
    text = re.sub(r'android:minResizeWidth="[^"]+"', 'android:minResizeWidth="40dp"', text, count=1)
    text = re.sub(r'android:minResizeHeight="[^"]+"', 'android:minResizeHeight="40dp"', text, count=1)
    text = re.sub(r'android:maxResizeWidth="[^"]+"', 'android:maxResizeWidth="1200dp"', text, count=1)
    text = re.sub(r'android:maxResizeHeight="[^"]+"', 'android:maxResizeHeight="1200dp"', text, count=1)
    p.write_text(text, encoding='utf-8')

text = GRADLE.read_text(encoding='utf-8')
text = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 70', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.5.9"', text, count=1)
GRADLE.write_text(text, encoding='utf-8')

for name, vals in SIZES.items():
    print(name, vals)
print('WakeGuard v1.5.9 widget default sizes applied')
