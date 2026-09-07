from pathlib import Path
import base64, hashlib

asset = Path('tools/v171_icon_parts/foreground.b64')
raw = base64.b64decode(asset.read_text(encoding='utf-8').strip())
assert len(raw) == 30926, f'IGNIDO foreground byte length mismatch: {len(raw)}'
assert raw[:4] == b'RIFF' and raw[8:12] == b'WEBP', 'IGNIDO foreground is not WebP'
assert hashlib.sha256(raw).hexdigest() == 'b45911d900f10bed2faccdb8417471aaeb0db706f8a4d0b16132937124061a4d', 'IGNIDO foreground SHA-256 mismatch'

p = Path('tools/patch_v171.py')
s = p.read_text(encoding='utf-8')
old = 'parts=Path("tools/v171_icon_parts"); fg="".join((parts/f"part{i:02d}.txt").read_text(encoding="utf-8").strip() for i in range(7)); bw(res/"drawable-nodpi/ic_ignido_wake_foreground.png",fg)'
new = 'fg=Path("tools/v171_icon_parts/foreground.b64").read_text(encoding="utf-8").strip(); bw(res/"drawable-nodpi/ic_ignido_wake_foreground.webp",fg)'
old_assert = 'assert (res/"drawable-nodpi/ic_ignido_wake_foreground.png").stat().st_size>50000'
new_assert = 'assert (res/"drawable-nodpi/ic_ignido_wake_foreground.webp").stat().st_size>25000'
assert old in s, 'v1.7.1 foreground anchor missing'
assert old_assert in s, 'v1.7.1 foreground assert anchor missing'
s = s.replace(old, new, 1).replace(old_assert, new_assert, 1)
exec(compile(s, 'patch_v171.py', 'exec'), {'__name__':'__main__'})
