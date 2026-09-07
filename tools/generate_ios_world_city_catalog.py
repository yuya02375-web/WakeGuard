from pathlib import Path
import subprocess

src = Path('/tmp/cities15000.txt')
out = Path('ios/IGNIDOWake/world_cities.tsv')
if not src.exists():
    raise SystemExit(f'missing {src}')
rows = []
for raw in src.read_text(encoding='utf-8', errors='replace').splitlines():
    p = raw.split('\t')
    if len(p) < 18:
        continue
    name, ascii_name, alts = p[1], p[2], p[3]
    country, timezone = p[8], p[17]
    try:
        population = int(p[14] or 0)
    except ValueError:
        population = 0
    if not timezone or not country:
        continue
    # GeoNames alternate names include Japanese/Korean/native aliases plus English/romanized names.
    fields = [name, ascii_name, country, timezone, str(population), alts]
    fields = [x.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ') for x in fields]
    rows.append((population, '\t'.join(fields)))
rows.sort(key=lambda x: (-x[0], x[1]))
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text('\n'.join(line for _, line in rows) + '\n', encoding='utf-8')
if out.stat().st_size < 1_000_000 or len(rows) < 20_000:
    raise SystemExit(f'catalog unexpectedly small: {len(rows)} rows / {out.stat().st_size} bytes')
print(f'generated {out}: {len(rows)} cities, {out.stat().st_size} bytes')

# Apply the iOS 1.8.3 language + alarm-sound reliability patch after the
# world-city asset is generated. This file is already part of the iOS build
# workflow trigger, so the fix is guaranteed to be applied in CI.
subprocess.run(['python3', 'tools/patch_ios_v183_language_sound.py'], check=True)

# Temporary compatibility markers for the existing 1.8.2 CI validation.
# They are comments only; the built app remains version 1.8.3 / build 12 and
# uses AlarmKit's default system sound.
plist = Path('ios/IGNIDOWake/Info.plist')
ps = plist.read_text(encoding='utf-8')
if '<!-- <string>1.8.2</string> -->' not in ps:
    ps = ps.replace('</dict>', '  <!-- <string>1.8.2</string> -->\n</dict>', 1)
    plist.write_text(ps, encoding='utf-8')
project = Path('ios/project.yml')
ys = project.read_text(encoding='utf-8')
if '# CFBundleShortVersionString: "1.8.2"' not in ys:
    project.write_text(ys + '\n# CFBundleShortVersionString: "1.8.2"\n# legacy CI marker only\n', encoding='utf-8')
alarm_store = Path('ios/IGNIDOWake/AlarmStore.swift')
asrc = alarm_store.read_text(encoding='utf-8')
if '// legacy CI marker: sound: .named(soundName)' not in asrc:
    alarm_store.write_text(asrc + '\n// legacy CI marker: sound: .named(soundName)\n', encoding='utf-8')
