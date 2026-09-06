from pathlib import Path
import subprocess

PATCH = Path(__file__).with_name("v164.patch")
if not PATCH.is_file():
    raise SystemExit("v164.patch not found")
subprocess.run(["patch","-p1","--forward","-i",str(PATCH.resolve())],cwd="WakeGuard/app",check=True)
print("WakeGuard v1.6.4 stopwatch + pre-alarm notification patch applied")
