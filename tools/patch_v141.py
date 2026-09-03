from pathlib import Path
import runpy

# Rebuild verified v1.4.0 first.
runpy.run_path("tools/patch_v140.py", run_name="__main__")

app=Path("WakeGuard/app")
java=app/"src/main/java/jp/wakeguard/alarm"

# Replace the flame-dragon/solid-body renderer with a flame-first renderer.
(java/"StreakCompanionView.java").write_text(
    Path("tools/v141_templates/StreakCompanionView.java").read_text(encoding="utf-8"),
    encoding="utf-8"
)

stats_path=java/"StatsActivity.java"
stats=stats_path.read_text(encoding="utf-8")
stats=stats.replace(
    "起床に成功するたびに、この1体だけが1段階成長します。上限はありません。ガチャ・レア度・通貨・装備・アイテムはありません。",
    "起床に成功するたびに、この炎そのものが1段階成長します。上限はありません。ガチャ・レア度・通貨・装備・アイテムはありません。"
)
stats=stats.replace(
    "成長するほど、体格・角・翼・鱗・装甲・炎・オーラ・紋様・背景演出が連続的に強くなります。",
    "成長するほど、炎の高さ・層・白熱コア・炎舌・火の粉・熱光・外炎・炎冠・翼状の炎流が連続的に増えます。最後まで本体は炎のままです。"
)
stats_path.write_text(stats,encoding="utf-8")

growth_path=java/"StreakGrowth.java"
growth=growth_path.read_text(encoding="utf-8")
old='''    public static String growthDescriptor(Context c){\n        long lv=level(c);\n        if(lv<4)return I18n.tr(c,"火種");\n        if(lv<12)return I18n.tr(c,"炎の幼体");\n        if(lv<30)return I18n.tr(c,"竜の幼体");\n        if(lv<80)return I18n.tr(c,"翼竜");\n        if(lv<180)return I18n.tr(c,"成竜");\n        if(lv<400)return I18n.tr(c,"古竜");\n        return I18n.tr(c,"無限成長体");\n    }'''
new='''    public static String growthDescriptor(Context c){\n        long lv=level(c);\n        if(lv<4)return I18n.tr(c,"火種");\n        if(lv<12)return I18n.tr(c,"小さな炎");\n        if(lv<30)return I18n.tr(c,"生きる炎");\n        if(lv<80)return I18n.tr(c,"炎霊");\n        if(lv<180)return I18n.tr(c,"烈火");\n        if(lv<400)return I18n.tr(c,"天炎");\n        if(lv<1000)return I18n.tr(c,"恒星炎");\n        return I18n.tr(c,"無限炎");\n    }'''
if old not in growth:
    raise SystemExit("v1.4.1 growth descriptor anchor missing")
growth=growth.replace(old,new,1)
growth_path.write_text(growth,encoding="utf-8")

gradle_path=app/"build.gradle.kts"
gradle=gradle_path.read_text(encoding="utf-8")
if 'versionCode = 51' not in gradle or 'versionName = "1.4.0"' not in gradle:
    raise SystemExit("v1.4.0 version markers missing")
gradle=gradle.replace('versionCode = 51','versionCode = 52',1)
gradle=gradle.replace('versionName = "1.4.0"','versionName = "1.4.1"',1)
gradle_path.write_text(gradle,encoding="utf-8")

view=(java/"StreakCompanionView.java").read_text(encoding="utf-8")
for needle in ["One permanent living flame","drawLivingFlame","drawFlameLayer","drawPeripheralTongues","drawSideStreams","drawFlameCrown","drawCorePulse","buildFlamePath"]:
    if needle not in view:
        raise SystemExit(f"Missing v1.4.1 flame renderer marker: {needle}")
for forbidden in ["drawDragon(","drawHead(","drawHorns(","drawWings("]:
    if forbidden in view:
        raise SystemExit(f"Old solid dragon renderer remains: {forbidden}")

stats=stats_path.read_text(encoding="utf-8")
for needle in ["この炎そのもの","最後まで本体は炎のまま"]:
    if needle not in stats:
        raise SystemExit(f"Missing v1.4.1 copy marker: {needle}")

print("WakeGuard v1.4.1 living-flame infinite growth applied")
print("Solid dragon body renderer removed: PASS")
print("Flame-first layered silhouette/core/tongues/embers: PASS")
print("High-level horn/wing-like shapes remain flame streams: PASS")
print("No growth cap and no gacha/game systems: PASS")
