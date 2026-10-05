from pathlib import Path
import json, struct

root=Path("WakeGuard/app")
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

# Version
p="build.gradle.kts"; s=read(p)
s=s.replace('versionCode = 180','versionCode = 181',1).replace('versionName = "2.8.0"','versionName = "2.8.1"',1)
write(p,s)

# The 2.7/2.8 asset is Khronos DragonAttenuation: intentionally 100% transmissive glass.
# Rebuild only its JSON material chunk as an opaque PBR ember-red dragon. Geometry stays untouched.
asset=root/"src/main/assets/dragon_realistic.glb"
raw=asset.read_bytes()
magic,version,total=struct.unpack_from("<4sII",raw,0)
if magic != b"glTF" or version != 2: raise SystemExit("dragon GLB header invalid")
off=12; chunks=[]; doc=None
while off < len(raw):
    clen,ctype=struct.unpack_from("<II",raw,off); off += 8
    data=raw[off:off+clen]; off += clen
    chunks.append((ctype,data))
    if ctype == 0x4E4F534A:
        doc=json.loads(data.rstrip(b" \t\r\n\0").decode("utf-8"))
if doc is None or not doc.get("materials"): raise SystemExit("dragon GLB material missing")
for mat in doc["materials"]:
    mat["name"]="IGNIDO Opaque PBR Dragon"
    mat.pop("extensions",None)
    mat["doubleSided"]=True
    mat["alphaMode"]="OPAQUE"
    mat["pbrMetallicRoughness"]={
        "baseColorFactor":[0.22,0.018,0.007,1.0],
        "metallicFactor":0.06,
        "roughnessFactor":0.48
    }
    mat["emissiveFactor"]=[0.020,0.0015,0.0005]
doc.pop("extensionsUsed",None)
doc.pop("extensionsRequired",None)

j=json.dumps(doc,separators=(",",":"),ensure_ascii=False).encode("utf-8")
j += b" " * ((-len(j)) % 4)
out=bytearray(struct.pack("<4sII",b"glTF",2,0))
for ctype,data in chunks:
    if ctype == 0x4E4F534A: data=j
    out += struct.pack("<II",len(data),ctype) + data
struct.pack_into("<I",out,8,len(out))
asset.write_bytes(out)

# Renderer: TextureView composes correctly inside the scroll/UI hierarchy, explicit camera,
# and visible diagnostics instead of silently swallowing render failures.
p="src/main/java/jp/wakeguard/alarm/StreakCompanionView.java"; s=read(p)
s=s.replace("import android.view.SurfaceView;","import android.view.TextureView;",1)
s=s.replace("private final SurfaceView surface;","private final TextureView surface;",1)
s=s.replace("surface = new SurfaceView(context);","surface = new TextureView(context);",1)
s=s.replace('''        surface.setBackgroundColor(0xff0b0d13);
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));''',
'''        surface.setOpaque(true);
        surface.setBackgroundColor(0xff0b0d13);
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));''',1)

old='''            viewer.loadModelGlb(model);
            viewer.transformToUnitCube(new com.google.android.filament.utils.Float3(0f,0f,-4f));
            surface.setAlpha(0.04f);
            applyGrowthAppearance();'''
new='''            viewer.loadModelGlb(model);
            if (viewer.getAsset() == null) throw new IllegalStateException("GLB asset was not created");
            viewer.transformToUnitCube(new com.google.android.filament.utils.Float3(0f,0f,-4f));
            // A null manipulator means ModelViewer will not alter the camera, so set it explicitly.
            viewer.getCamera().lookAt(
                    0.0, 0.10, 0.35,
                    0.0, 0.00, -4.0,
                    0.0, 1.0, 0.0);
            surface.setAlpha(0.04f);
            loadError.setVisibility(View.GONE);
            applyGrowthAppearance();'''
if old not in s: raise SystemExit("Android load model block missing")
s=s.replace(old,new,1)

old='''                try {
                    if (viewer != null) viewer.render(frameTimeNanos);
                } catch (Throwable ignored) {}
                aura.invalidate();'''
new='''                try {
                    if (viewer != null) viewer.render(frameTimeNanos);
                } catch (Throwable t) {
                    loadError.setText("3D描画エラー");
                    loadError.setVisibility(View.VISIBLE);
                }
                aura.invalidate();'''
if old not in s: raise SystemExit("Android frame render block missing")
s=s.replace(old,new,1)

s=s.replace('''            loadError.setText("3Dモデルを読み込めませんでした");''',
'''            loadError.setText("3Dモデルを読み込めませんでした: "+t.getClass().getSimpleName());''',1)

# Make Lv.27 unambiguously visible and do not hide the model at normal growth levels.
old='''        float dragon = clamp((growthLevel - 7f) / 20f);
        // At Lv.27 this is fully visible. Lower levels emerge gradually from the fire.
        surface.setAlpha(0.06f + 0.94f * dragon);
        float scale = 0.82f + 0.18f * dragon + Math.min(0.10f, Math.max(0f, growthLevel - 30f) / 500f);'''
new='''        float dragon = clamp((growthLevel - 5f) / 16f);
        // From Lv.21 onward the real mesh is fully opaque. Lv.27 can never be hidden by UI alpha.
        surface.setAlpha(growthLevel >= 21L ? 1.0f : (0.18f + 0.82f * dragon));
        float scale = 0.86f + 0.14f * dragon + Math.min(0.10f, Math.max(0f, growthLevel - 30f) / 500f);'''
if old not in s: raise SystemExit("Android growth alpha block missing")
s=s.replace(old,new,1)
write(p,s)

# Verify rebuilt GLB really is opaque.
raw=asset.read_bytes(); off=12; verified=False
while off < len(raw):
    clen,ctype=struct.unpack_from("<II",raw,off); off += 8
    data=raw[off:off+clen]; off += clen
    if ctype == 0x4E4F534A:
        check=json.loads(data.rstrip(b" ").decode("utf-8"))
        m=check["materials"][0]
        assert m.get("alphaMode") == "OPAQUE"
        assert not m.get("extensions")
        assert m["pbrMetallicRoughness"]["baseColorFactor"][3] == 1.0
        verified=True
assert verified
assert 'versionName = "2.8.1"' in read("build.gradle.kts")
renderer=read("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java")
assert "TextureView" in renderer
assert "viewer.getCamera().lookAt" in renderer
assert "growthLevel >= 21L ? 1.0f" in renderer
print("Android 2.8.1 visible 3D dragon fix applied")
