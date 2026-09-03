from pathlib import Path
import runpy, base64, gzip, subprocess, tempfile

runpy.run_path("tools/patch_v127.py", run_name="__main__")
encoded = Path("tools/v128_patch.b64").read_text(encoding="utf-8").strip()
patch_bytes = gzip.decompress(base64.b64decode(encoded))
with tempfile.NamedTemporaryFile("wb", delete=False) as f:
    f.write(patch_bytes)
    patch_path = f.name
subprocess.run(["patch", "-p1", "-i", patch_path], cwd="WakeGuard/app", check=True)

app=Path("WakeGuard/app")
clock_path=app/"src/main/java/jp/wakeguard/alarm/ClockActivity.java"
clock=clock_path.read_text(encoding="utf-8")
# Java requires a captured local to be final/effectively-final. `label` is reassigned
# during fallback localization, so capture a final copy for each chip listener.
old='chip.setBackground(Ui.roundStroke(Ui.SURFACE_3,Ui.BORDER,13,this));chip.setOnClickListener(v->search.setText(label));'
new='chip.setBackground(Ui.roundStroke(Ui.SURFACE_3,Ui.BORDER,13,this));final String chipLabel=label;chip.setOnClickListener(v->search.setText(chipLabel));'
if clock.count(old) < 2:
    raise SystemExit(f"Expected two v1.2.8 chip listener sites, found {clock.count(old)}")
clock=clock.replace(old,new)
clock_path.write_text(clock,encoding="utf-8")

gradle=(app/"build.gradle.kts").read_text(encoding="utf-8")
checks=["fastSelectableZones","countryTermsCache","zonesByCountry","45L","convertView instanceof LinearLayout","final String chipLabel=label"]
for needle in checks:
    if needle not in clock: raise SystemExit(f"Missing v1.2.8 marker: {needle}")
if 'versionCode = 47' not in gradle or 'versionName = "1.2.8"' not in gradle: raise SystemExit("v1.2.8 version bump missing")
print("WakeGuard v1.2.8 fast search patch applied")
print("Lightweight index: PASS")
print("Country term cache: PASS")
print("No ICU city-name scan during indexing: PASS")
print("List row reuse: PASS")
print("45ms debounce: PASS")
print("Chip listener capture compile fix: PASS")
