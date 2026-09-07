from pathlib import Path
from urllib.request import urlopen

# Reuse the last known-good v1.8.3 localization/sound patch by immutable commit,
# then apply the compile-safe AlarmKit type fixes below. This keeps the patch
# reproducible while avoiding another huge embedded payload.
BASE_URL = (
    'https://raw.githubusercontent.com/yuya02375-web/WakeGuard/'
    '08e51f8da0cff797fc25a05b435388363b4675fb/'
    'tools/patch_ios_v183_language_sound.py'
)
base = urlopen(BASE_URL, timeout=30).read().decode('utf-8')
exec(compile(base, 'patch_ios_v183_base.py', 'exec'), globals(), globals())

root = Path('ios/IGNIDOWake')

def patch_file(name, replacements):
    p = root / name
    s = p.read_text(encoding='utf-8')
    for old, new in replacements:
        s = s.replace(old, new)
    p.write_text(s, encoding='utf-8')
    return s

# AlarmKit AlarmButton requires LocalizedStringResource, not String.
# Convert the already-selected JA/EN/KO string into a resource explicitly.
alarm = patch_file('AlarmStore.swift', [
    ('AlarmButton(\n                text: AppText.localized("解除"),',
     'AlarmButton(\n                text: LocalizedStringResource(stringLiteral: AppText.localized("解除")),'),
    ('AlarmButton(text: AppText.localized("解除"),',
     'AlarmButton(text: LocalizedStringResource(stringLiteral: AppText.localized("解除")),'),
    ('AlarmManager.AlarmConfiguration.alarm(',
     'AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.alarm('),
])

multi = patch_file('MultiTimer.swift', [
    ('AlarmButton(text: AppText.localized("開く"),',
     'AlarmButton(text: LocalizedStringResource(stringLiteral: AppText.localized("開く")),'),
    ('AlarmManager.AlarmConfiguration.timer(',
     'AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer('),
])

feature = patch_file('FeatureStores.swift', [
    ('title: "タイマー終了",',
     'title: LocalizedStringResource(stringLiteral: AppText.localized("タイマー終了")),'),
    ('AlarmButton(text: AppText.localized("開く"),',
     'AlarmButton(text: LocalizedStringResource(stringLiteral: AppText.localized("開く")),'),
    ('let countdown = AlarmPresentation.Countdown(title: "タイマー")',
     'let countdown = AlarmPresentation.Countdown(title: LocalizedStringResource(stringLiteral: AppText.localized("タイマー")))'),
    ('AlarmManager.AlarmConfiguration.timer(',
     'AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer('),
])

assert 'sound: .default' in alarm
assert '.named(soundName)' not in alarm
assert alarm.count('AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.alarm(') >= 2
assert 'LocalizedStringResource(stringLiteral: AppText.localized("解除"))' in alarm
assert 'sound: .default' in multi
assert 'AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer(' in multi
assert 'LocalizedStringResource(stringLiteral: AppText.localized("開く"))' in multi
assert 'AlarmManager.AlarmConfiguration<EmptyWakeMetadata>.timer(' in feature
assert 'LocalizedStringResource(stringLiteral: AppText.localized("開く"))' in feature
print('IGNIDO Wake iOS v1.8.3 AlarmKit compile-safe localization fix applied')
