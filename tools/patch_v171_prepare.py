from pathlib import Path
import base64, hashlib

parts = Path('tools/v171_icon_parts')
expected_lengths = [8000,8000,8000,8000,8000,1236]
expected_hashes = [
'5371d71c83bda5d1d4293df5924f27045f6d3a00597b5c81f504dd262dbeff21',
'224b73116f7406c176f3a7ee9b05fca77048d6f58aaa5b8256d548736a92d320',
'b94578277f39791327d76dbabe771fb5e0325923f0198184b9c23e84084d384f',
'7d9f65edf6bafccae32550d9d42003e74580bc414d11b3225be9db279aa22280',
'dbeddf5bdb24a5e7affd6dc9ef49d2414d85208c3cb43c7f1572620c40284d88',
'89bd3f6cc8fdaefdb28c02d8a663f140b444e3a2bb3353a4385f91f288d23d41']
chunks=[]
for i in range(6):
    c=(parts/f'fg_{i:02d}.txt').read_text(encoding='utf-8').strip()
    h=hashlib.sha256(c.encode()).hexdigest()
    print(f'fg_{i:02d}: len={len(c)} sha256={h}')
    assert len(c)==expected_lengths[i], f'fg_{i:02d} length mismatch: {len(c)} != {expected_lengths[i]}'
    assert h==expected_hashes[i], f'fg_{i:02d} hash mismatch: {h} != {expected_hashes[i]}'
    chunks.append(c)
encoded=''.join(chunks)
raw = base64.b64decode(encoded, validate=True)
assert len(raw) == 30926, f'IGNIDO foreground byte length mismatch: {len(raw)}'
assert raw[:4] == b'RIFF' and raw[8:12] == b'WEBP', 'IGNIDO foreground is not WebP'
assert hashlib.sha256(raw).hexdigest() == 'b45911d900f10bed2faccdb8417471aaeb0db706f8a4d0b16132937124061a4d', 'IGNIDO foreground SHA-256 mismatch'

p = Path('tools/patch_v171.py')
s = p.read_text(encoding='utf-8')
old = 'parts=Path("tools/v171_icon_parts"); fg="".join((parts/f"part{i:02d}.txt").read_text(encoding="utf-8").strip() for i in range(7)); bw(res/"drawable-nodpi/ic_ignido_wake_foreground.png",fg)'
new = 'parts=Path("tools/v171_icon_parts"); fg="".join((parts/f"fg_{i:02d}.txt").read_text(encoding="utf-8").strip() for i in range(6)); bw(res/"drawable-nodpi/ic_ignido_wake_foreground.webp",fg)'
old_assert = 'assert (res/"drawable-nodpi/ic_ignido_wake_foreground.png").stat().st_size>50000'
new_assert = 'assert (res/"drawable-nodpi/ic_ignido_wake_foreground.webp").stat().st_size>25000'
assert old in s, 'v1.7.1 foreground anchor missing'
assert old_assert in s, 'v1.7.1 foreground assert anchor missing'
s = s.replace(old, new, 1).replace(old_assert, new_assert, 1)
exec(compile(s, 'patch_v171.py', 'exec'), {'__name__':'__main__'})
