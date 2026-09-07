from pathlib import Path

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
