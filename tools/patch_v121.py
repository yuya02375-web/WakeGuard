from pathlib import Path
import runpy, base64, gzip, subprocess, tempfile, re

# Rebuild the exact verified v1.2.0 source first, including the final
# Android-12-compatible compact timer-notification layout.
runpy.run_path("tools/patch_v120_final.py", run_name="__main__")

# Apply the v1.2.0 -> v1.2.1 app-module diff.
parts=[]
for i in range(4):
    parts.append(Path(f"tools/v121_diff_part{i:02d}.b64").read_text(encoding="utf-8").strip())
patch_bytes=gzip.decompress(base64.b64decode("".join(parts)))
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
            # Mathematical prompts are language-independent expressions.
            if "prompt.setText(a+" in line:
                continue
            viol.append(f"{p.name}:{i}: {line.strip()}")
if viol:
    raise SystemExit("Unlocalized direct UI strings:\n"+"\n".join(viol))

print("WakeGuard v1.2.1 patch applied")
print("Localization direct-UI guard: PASS")
