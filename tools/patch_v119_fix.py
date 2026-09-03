from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v119.py", run_name="__main__")

p=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/ClockFaceActivity.java")
s=p.read_text(encoding="utf-8")
anchor='''        public void setHands(double hh,double mm,double ss){h=hh;m=mm;s=ss;invalidate();}\n'''
helper='''        public void setHands(double hh,double mm,double ss){h=hh;m=mm;s=ss;invalidate();}\n        private float dp(float value){return value*getResources().getDisplayMetrics().density;}\n'''
if anchor not in s:
    raise SystemExit("AnalogFace setHands anchor not found")
s=s.replace(anchor,helper,1)
s=re.sub(r'Ui\.dp\(getContext\(\),(\d+)\)',r'dp(\1)',s)
p.write_text(s,encoding="utf-8")

print("WakeGuard v1.1.9 compile fix: AnalogFace uses View-local density conversion")
