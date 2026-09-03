from pathlib import Path
import runpy

# Rebuild the verified v1.2.2 source first.
runpy.run_path("tools/patch_v122.py", run_name="__main__")

clock=Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm/ClockActivity.java")
s=clock.read_text(encoding="utf-8")
bad='stickyWorld.addView(Ui.divider(this),Ui.gapTop(this,8));'
good='View stickyDivider=Ui.divider(this); LinearLayout.LayoutParams stickyDividerLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,1)); stickyDividerLp.setMargins(0,Ui.dp(this,8),0,0); stickyWorld.addView(stickyDivider,stickyDividerLp);'
if bad not in s:
    raise SystemExit("Expected oversized sticky divider pattern not found")
s=s.replace(bad,good,1)
clock.write_text(s,encoding="utf-8")

# Bump version so v1.2.2 can be overwritten normally.
gradle=Path("WakeGuard/app/build.gradle.kts")
g=gradle.read_text(encoding="utf-8")
g=g.replace('versionCode = 41','versionCode = 42',1).replace('versionName = "1.2.2"','versionName = "1.2.3"',1)
gradle.write_text(g,encoding="utf-8")

# Structural regression guards.
final=clock.read_text(encoding="utf-8")
if bad in final:
    raise SystemExit("Oversized sticky divider bug still present")
if 'stickyDividerLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,1))' not in final:
    raise SystemExit("Fixed 1dp sticky divider missing")
if 'outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));' not in final:
    raise SystemExit("World-clock scroll region weight layout unexpectedly changed")

print("WakeGuard v1.2.3 world-clock layout hotfix applied")
print("Sticky divider fixed-height guard: PASS")
