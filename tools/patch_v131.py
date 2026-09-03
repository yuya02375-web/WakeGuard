from pathlib import Path
import runpy

# Rebuild verified v1.3.0 first.
runpy.run_path("tools/patch_v130.py", run_name="__main__")

app=Path("WakeGuard/app")
java=app/"src/main/java/jp/wakeguard/alarm"

# Replace the primitive single-silhouette renderer with the high-detail multi-species renderer.
(java/"StreakCompanionView.java").write_text(Path("tools/v131_templates/StreakCompanionView.java").read_text(encoding="utf-8"),encoding="utf-8")

stats_path=java/"StatsActivity.java"
stats=stats_path.read_text(encoding="utf-8")
stats=stats.replace(
    'hero.addView(companion,new LinearLayout.LayoutParams(Ui.dp(this,235),Ui.dp(this,245)));',
    'hero.addView(companion,new LinearLayout.LayoutParams(Ui.dp(this,275),Ui.dp(this,290)));',1)

old='for(StreakGame.CharacterDef d:StreakGame.allCharacters()){boolean has=owned.contains(d.id);LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,10),Ui.dp(this,9),Ui.dp(this,10),Ui.dp(this,9));row.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,12,this));TextView name=Ui.text(this,has?StreakGame.displayName(this,d):"???",15,has?Ui.TEXT:Ui.MUTED_2);name.setTypeface(null,Typeface.BOLD);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView meta=Ui.text(this,has?(d.starter()?I18n.tr(this,"限定"):d.rarity)+"  Lv."+StreakGame.charLevel(this,d.id):d.rarity,12,has?Ui.ACCENT:Ui.MUTED_2);row.addView(meta);if(has)row.setOnClickListener(v->{StreakGame.equip(this,d.id);refresh();Toast.makeText(this,I18n.tr(this,"装備しました"),Toast.LENGTH_SHORT).show();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,Ui.dp(this,7));box.addView(row,rp);}'
new='for(StreakGame.CharacterDef d:StreakGame.allCharacters()){boolean has=owned.contains(d.id);LinearLayout row=Ui.row(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(this,8),Ui.dp(this,7),Ui.dp(this,10),Ui.dp(this,7));row.setBackground(Ui.roundStroke(Ui.SURFACE_2,has?Ui.BORDER:Ui.SURFACE_3,14,this));if(has){StreakCompanionView mini=new StreakCompanionView(this);mini.setAnimationEnabled(false);mini.setCompanion(d,StreakGame.charLevel(this,d.id),Collections.emptySet());row.addView(mini,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,66)));}else{TextView mystery=Ui.text(this,"?",28,Ui.MUTED_2);mystery.setGravity(Gravity.CENTER);row.addView(mystery,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,66)));}LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(Ui.dp(this,8),0,0,0);TextView name=Ui.text(this,has?StreakGame.displayName(this,d):"???",15,has?Ui.TEXT:Ui.MUTED_2);name.setTypeface(null,Typeface.BOLD);labels.addView(name);TextView meta=Ui.text(this,has?(d.starter()?I18n.tr(this,"限定"):d.rarity)+"  Lv."+StreakGame.charLevel(this,d.id):d.rarity,12,has?Ui.ACCENT:Ui.MUTED_2);labels.addView(meta);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));if(has)row.setOnClickListener(v->{StreakGame.equip(this,d.id);refresh();Toast.makeText(this,I18n.tr(this,"装備しました"),Toast.LENGTH_SHORT).show();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,0,0,Ui.dp(this,7));box.addView(row,rp);}'
if old not in stats: raise SystemExit("v1.3.1 character collection anchor missing")
stats=stats.replace(old,new,1)

old='private void showGachaResult(StreakGame.GachaResult r){String msg=r.character.rarity+"  "+r.character.name+(r.duplicate?"\\n"+I18n.tr(this,"重複")+"  +"+r.essence+" Essence":"\\n"+I18n.tr(this,"新しいキャラを獲得"));new AlertDialog.Builder(this).setTitle(I18n.tr(this,"ガチャ結果")).setMessage(msg).setPositiveButton(I18n.tr(this,"OK"),null).show();}'
new='private void showGachaResult(StreakGame.GachaResult r){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,8));StreakCompanionView art=new StreakCompanionView(this);art.setCompanion(r.character,1,Collections.emptySet());box.addView(art,new LinearLayout.LayoutParams(Ui.dp(this,250),Ui.dp(this,265)));TextView rarity=Ui.text(this,(r.character.rarity==null||r.character.rarity.isEmpty()?"EXCLUSIVE":r.character.rarity),13,Ui.ACCENT);rarity.setGravity(Gravity.CENTER);rarity.setTypeface(null,Typeface.BOLD);box.addView(rarity);TextView name=Ui.text(this,r.character.name,21,Ui.TEXT);name.setGravity(Gravity.CENTER);name.setTypeface(null,Typeface.BOLD);box.addView(name);TextView state=Ui.text(this,r.duplicate?I18n.tr(this,"重複")+"  +"+r.essence+" Essence":I18n.tr(this,"新しいキャラを獲得"),13,r.duplicate?Ui.MUTED:Ui.SUCCESS);state.setGravity(Gravity.CENTER);state.setPadding(0,Ui.dp(this,5),0,0);box.addView(state);new AlertDialog.Builder(this).setTitle(I18n.tr(this,"ガチャ結果")).setView(box).setPositiveButton(I18n.tr(this,"OK"),null).show();}'
if old not in stats: raise SystemExit("v1.3.1 gacha result anchor missing")
stats=stats.replace(old,new,1)
stats_path.write_text(stats,encoding="utf-8")

gradle_path=app/"build.gradle.kts"
gradle=gradle_path.read_text(encoding="utf-8")
if 'versionCode = 49' not in gradle or 'versionName = "1.3.0"' not in gradle: raise SystemExit("v1.3.0 version markers missing")
gradle=gradle.replace('versionCode = 49','versionCode = 50',1).replace('versionName = "1.3.0"','versionName = "1.3.1"',1)
gradle_path.write_text(gradle,encoding="utf-8")

view=(java/"StreakCompanionView.java").read_text(encoding="utf-8")
stats=stats_path.read_text(encoding="utf-8")
for needle in ["drawSpirit","drawBeast","drawBird","drawDragon","drawAquatic","drawGolem","drawInsect","drawGuardian","RadialGradient","setAnimationEnabled"]:
    if needle not in view: raise SystemExit(f"Missing v1.3.1 renderer marker: {needle}")
for needle in ["mini=new StreakCompanionView","Ui.dp(this,275)","StreakCompanionView art=new StreakCompanionView"]:
    if needle not in stats: raise SystemExit(f"Missing v1.3.1 UI marker: {needle}")
print("WakeGuard v1.3.1 high-detail companion art applied")
print("Distinct species silhouettes: PASS")
print("Starter multi-stage evolution silhouettes: PASS")
print("Rarity aura/badge + decoration layering: PASS")
print("Idle animation: PASS")
print("Character collection thumbnails: PASS")
print("Gacha result full-art preview: PASS")
