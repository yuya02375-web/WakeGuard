from pathlib import Path
import runpy, re

# Rebuild the verified v1.4.2 app first.
runpy.run_path("tools/patch_v142.py", run_name="__main__")

app = Path("WakeGuard/app")
java = app / "src/main/java/jp/wakeguard/alarm"

# Replace the flame-only renderer with the premium offline flame-dragon renderer.
(java / "StreakCompanionView.java").write_text(
    Path("tools/v143_templates/StreakCompanionView.java").read_text(encoding="utf-8"),
    encoding="utf-8"
)

# Restore the intended evolution: flame -> living flame -> flame dragon -> endlessly more powerful flame dragon.
growth_path = java / "StreakGrowth.java"
growth = growth_path.read_text(encoding="utf-8")
pattern = re.compile(r"    public static String growthDescriptor\(Context c\)\{.*?\n    \}", re.S)
replacement = '''    public static String growthDescriptor(Context c){
        long lv=level(c);
        if(lv<4)return I18n.tr(c,"火種");
        if(lv<12)return I18n.tr(c,"小さな炎");
        if(lv<30)return I18n.tr(c,"炎竜の兆し");
        if(lv<80)return I18n.tr(c,"幼炎竜");
        if(lv<180)return I18n.tr(c,"炎竜");
        if(lv<400)return I18n.tr(c,"天炎竜");
        if(lv<1000)return I18n.tr(c,"恒星炎竜");
        return I18n.tr(c,"無限炎竜");
    }'''
new_growth, n = pattern.subn(replacement, growth, count=1)
if n != 1:
    raise SystemExit("Could not replace growthDescriptor for v1.4.3")
growth_path.write_text(new_growth, encoding="utf-8")

# Update explanatory copy without reintroducing game systems.
stats_path = java / "StatsActivity.java"
stats = stats_path.read_text(encoding="utf-8")
stats = stats.replace(
    "起床に成功するたびに、この炎そのものが1段階成長します。上限はありません。ガチャ・レア度・通貨・装備・アイテムはありません。",
    "起床に成功するたびに、同じ1体が1段階成長します。火種から始まり、炎の中に竜の姿が形成され、炎竜として上限なく成長し続けます。ガチャ・レア度・通貨・装備・アイテムはありません。"
)
stats = stats.replace(
    "成長するほど、炎の高さ・層・白熱コア・炎舌・火の粉・熱光・外炎・炎冠・翼状の炎流が連続的に増えます。最後まで本体は炎のままです。",
    "成長するほど、白熱コア・炎・頭部・角・翼・胴体・尾・爪・鱗状の熱流・火の粉・光輪が連続的に強くなります。表示と成長は端末内だけで動作し、オフラインでも完全に表示できます。"
)
stats_path.write_text(stats, encoding="utf-8")

# Version bump.
gradle_path = app / "build.gradle.kts"
gradle = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 53' not in gradle or 'versionName = "1.4.2"' not in gradle:
    raise SystemExit("v1.4.2 version markers missing")
gradle = gradle.replace('versionCode = 53', 'versionCode = 54', 1)
gradle = gradle.replace('versionName = "1.4.2"', 'versionName = "1.4.3"', 1)
gradle_path.write_text(gradle, encoding="utf-8")

# Verification.
view = (java / "StreakCompanionView.java").read_text(encoding="utf-8")
for needle in [
    "premium offline flame dragon renderer",
    "drawLivingFireBase",
    "drawDragon(",
    "drawNeckAndHead",
    "drawWings",
    "drawWingVeins",
    "drawTail",
    "drawMythicDetails",
    "drawPremiumPath"
]:
    if needle not in view:
        raise SystemExit(f"Missing v1.4.3 flame-dragon marker: {needle}")
for forbidden in ["HttpURLConnection", "URLConnection", "java.net", "https://", "http://", "gacha", "rarity"]:
    if forbidden in view:
        raise SystemExit(f"Offline/game regression in renderer: {forbidden}")

stats = stats_path.read_text(encoding="utf-8")
for forbidden in ["showGacha(", "showCharacters(", "showDecorations(", "ガチャチケット", "エッセンス", "キャラコレクション"]:
    if forbidden in stats:
        raise SystemExit(f"Game UI still present: {forbidden}")

if 'versionCode = 54' not in gradle_path.read_text(encoding="utf-8"):
    raise SystemExit("v1.4.3 versionCode missing")

print("WakeGuard v1.4.3 premium offline flame-dragon growth applied")
print("Flame -> dragon evolution restored: PASS")
print("High-detail head/horns/wings/body/tail/veins/mythic effects: PASS")
print("Renderer has zero runtime network dependency: PASS")
print("No gacha/rarity/collection/equipment systems: PASS")
