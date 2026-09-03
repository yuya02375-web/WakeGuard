from pathlib import Path
import runpy

runpy.run_path("tools/patch_v119_fix.py", run_name="__main__")
p=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/ClockFaceActivity.java")
s=p.read_text(encoding="utf-8")
s=s.replace('r*0.48,h*30','r*0.48f,h*30')
s=s.replace('r*0.68,m*6','r*0.68f,m*6')
s=s.replace('r*0.78,s*6','r*0.78f,s*6')
p.write_text(s,encoding="utf-8")
print("WakeGuard v1.1.9 compile fix 2: analog hand lengths use float math")
