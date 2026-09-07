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

# The original bitmap transfer was split into text chunks and one chunk was truncated by two
# characters in GitHub. Avoid fragile binary-in-text transport entirely: the exact Wake mark
# already exists as the traced vector used for Android 13 themed/notification icons. Reuse that
# vector as the adaptive-icon foreground, tinted with IGNIDO ember. This is lossless, offline,
# deterministic, and cannot be corrupted by base64 chunk boundaries.
s = s.replace(old_binary, '', 1)
s = s.replace(old_layer, '', 1)
s = s.replace(
    mono_write,
    mono_write + '\nwr(res/"drawable/ic_ignido_wake_foreground.xml", mono.replace(\'#FFFFFFFF\',\'#FFF13A24\'))',
    1,
)
s = s.replace('@drawable/ic_ignido_wake_foreground_layer', '@drawable/ic_ignido_wake_foreground')
s = s.replace(old_assert, 'assert (res/"drawable/ic_ignido_wake_foreground.xml").stat().st_size>5000', 1)

exec(compile(s, 'patch_v171.py', 'exec'), {'__name__':'__main__'})
