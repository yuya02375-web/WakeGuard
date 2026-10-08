from pathlib import Path
p=Path('WakeGuard/app/build.gradle.kts')
s=p.read_text()
assert 'versionCode = 187' in s and 'versionName = "2.8.7"' in s
s=s.replace('versionCode = 187','versionCode = 188',1).replace('versionName = "2.8.7"','versionName = "2.8.8"',1)
p.write_text(s)
resources=Path('WakeGuard/app/src/main/res/drawable-nodpi')
for stage in ['ember','flame','egg','hatchling']:
    res=resources/f'ignido_stage_{stage}.webp'
    assert res.is_file() and res.stat().st_size>10000, res
src=Path('WakeGuard/app/src/main/java/jp/wakeguard/alarm/StreakCompanionView.java').read_text()
assert 'displayLevel<4L ? 0' in src and 'displayLevel<80L ? 3' in src
assert 'loadAnimatedDragon()' in src
assert 'android.opengl' not in src
print('v2.8.8: upgraded hand-authored 2D assets; behavior and streak progression preserved')
