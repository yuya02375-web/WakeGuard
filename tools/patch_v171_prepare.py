from pathlib import Path

p = Path('tools/patch_v171.py')
s = p.read_text(encoding='utf-8')

old_binary = 'parts=Path("tools/v171_icon_parts"); fg="".join((parts/f"part{i:02d}.txt").read_text(encoding="utf-8").strip() for i in range(7)); bw(res/"drawable-nodpi/ic_ignido_wake_foreground.png",fg)'
old_layer = 'wr(res/"drawable/ic_ignido_wake_foreground_layer.xml",\'<?xml version="1.0" encoding="utf-8"?><bitmap xmlns:android="http://schemas.android.com/apk/res/android" android:src="@drawable/ic_ignido_wake_foreground" android:gravity="fill" />\')'
mono_write = 'wr(res/"drawable/ic_ignido_wake_mono.xml",mono)'
old_assert = 'assert (res/"drawable-nodpi/ic_ignido_wake_foreground.png").stat().st_size>50000'

assert old_binary in s, 'v1.7.1 binary foreground anchor missing'
assert old_layer in s, 'v1.7.1 bitmap layer anchor missing'
assert mono_write in s, 'v1.7.1 monochrome write anchor missing'
assert old_assert in s, 'v1.7.1 foreground assert anchor missing'

# Avoid the fragile binary/base64 transfer path. The exact Wake mark already exists as the
# traced vector used for Android themed/notification icons, so use that vector directly as the
# adaptive-icon foreground and tint it with IGNIDO ember.
s = s.replace(old_binary, '', 1)
s = s.replace(old_layer, '', 1)
s = s.replace(
    mono_write,
    mono_write + '\nwr(res/"drawable/ic_ignido_wake_foreground.xml", mono.replace(\'#FFFFFFFF\',\'#FFF13A24\'))',
    1,
)
s = s.replace('@drawable/ic_ignido_wake_foreground_layer', '@drawable/ic_ignido_wake_foreground')
s = s.replace(
    old_assert,
    'fgp=res/"drawable/ic_ignido_wake_foreground.xml"; assert fgp.stat().st_size>3000 and "#FFF13A24" in rd(fgp)',
    1,
)

exec(compile(s, 'patch_v171.py', 'exec'), {'__name__':'__main__'})
