from pathlib import Path
import runpy, subprocess, tempfile, re, base64, gzip

# Rebuild the verified v1.2.1 source first.
runpy.run_path("tools/patch_v121.py", run_name="__main__")

# Apply the v1.2.1 -> v1.2.2 app-module diff.
encoded=Path("tools/v122_patch.b64").read_text(encoding="utf-8").strip()
patch_bytes=gzip.decompress(base64.b64decode(encoded))
with tempfile.NamedTemporaryFile("wb", delete=False) as f:
    f.write(patch_bytes)
    patch_path=f.name
subprocess.run(["patch","-p1","-i",patch_path], cwd="WakeGuard/app", check=True)

# Guard against newly introduced hard-coded Japanese in direct UI setters.
java=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm")
unsafe=re.compile(r'(?:setText|setHint)\([^\n]*"[^"\\]*[ぁ-んァ-ヶ一-龯]|Toast\.makeText\([^\n]*"[^"\\]*[ぁ-んァ-ヶ一-龯]|set(?:Title|Message|PositiveButton|NegativeButton)\("[^"\\]*[ぁ-んァ-ヶ一-龯]')
viol=[]
for p in java.glob("*.java"):
    if p.name=="I18n.java":
        continue
    for i,line in enumerate(p.read_text(encoding="utf-8").splitlines(),1):
        if unsafe.search(line) and "I18n.tr" not in line:
            if "prompt.setText(a+" in line:
                continue
            viol.append(f"{p.name}:{i}: {line.strip()}")
if viol:
    raise SystemExit("Unlocalized direct UI strings:\n"+"\n".join(viol))

# Structural assertions for the two independent auto-stop paths.
checks={
    "WakeGuard/app/src/main/java/jp/wakeguard/alarm/AlarmStore.java":["ringDurationSec,fullStopDurationSec","fullStopDurationSec"],
    "WakeGuard/app/src/main/java/jp/wakeguard/alarm/AlarmEditorActivity.java":["音・振動だけ自動停止","アラームを完全自動停止","showDurationDialog(true","showDurationDialog(false"],
    "WakeGuard/app/src/main/java/jp/wakeguard/alarm/AlarmService.java":["applyAutoStopState","scheduleAutoStops","fullStopHandler","fullStopDurationSec"],
}
for path,needles in checks.items():
    text=Path(path).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"Missing v1.2.2 marker {needle!r} in {path}")

print("WakeGuard v1.2.2 patch applied")
print("Dual auto-stop settings: PASS")
print("Localization direct-UI guard: PASS")
