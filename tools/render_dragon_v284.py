import bpy, math
from mathutils import Vector

SRC="/tmp/Dragon.glb"
OUT="/tmp/ignido_dragon_v284.png"

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=SRC)

meshes=[o for o in bpy.context.scene.objects if o.type=="MESH"]
arms=[o for o in bpy.context.scene.objects if o.type=="ARMATURE"]
if not meshes:
    raise RuntimeError("Dragon GLB has no mesh")

# Try to use a strong readable pose from the imported animation, but fall back cleanly.
actions=list(bpy.data.actions)
if arms and actions:
    arm=arms[0]
    if arm.animation_data is None:
        arm.animation_data_create()
    preferred=None
    for key in ("attack","idle","fly","soar","hover"):
        preferred=next((a for a in actions if key in a.name.lower()), None)
        if preferred: break
    if preferred is None:
        preferred=actions[0]
    arm.animation_data.action=preferred
    start,end=preferred.frame_range
    bpy.context.scene.frame_set(int(start + (end-start)*0.32))

# Smooth, slightly round the low-poly source without changing silhouette too much.
for o in meshes:
    for p in o.data.polygons:
        p.use_smooth=True
    if len(o.data.vertices) < 120000:
        sub=o.modifiers.new("IGNIDO_Subdivision","SUBSURF")
        sub.subdivision_type='CATMULL_CLARK'
        sub.levels=1
        sub.render_levels=1
    bev=o.modifiers.new("IGNIDO_MicroBevel","BEVEL")
    bev.width=0.006
    bev.segments=2
    bev.limit_method='ANGLE'
    bev.angle_limit=math.radians(42)

# Cinematic dark dragon palette.
def tune_material(mat, idx):
    mat.use_nodes=True
    nodes=mat.node_tree.nodes
    bsdf=nodes.get("Principled BSDF")
    if not bsdf:
        return
    name=mat.name.lower()
    # Preserve texture input if present, but tint it darker via base color default.
    if "wing" in name or "membrane" in name:
        base=(0.16,0.008,0.006,1)
        metallic=0.04
        rough=0.34
    elif "eye" in name:
        base=(1.0,0.12,0.01,1)
        metallic=0.0
        rough=0.22
        if "Emission Color" in bsdf.inputs:
            bsdf.inputs["Emission Color"].default_value=(1.0,0.035,0.004,1)
            bsdf.inputs["Emission Strength"].default_value=7.0
        elif "Emission" in bsdf.inputs:
            bsdf.inputs["Emission"].default_value=(1.0,0.035,0.004,1)
            if "Emission Strength" in bsdf.inputs:
                bsdf.inputs["Emission Strength"].default_value=7.0
    else:
        base=(0.022,0.028,0.04,1) if idx % 2 == 0 else (0.055,0.008,0.006,1)
        metallic=0.22
        rough=0.29
    bsdf.inputs["Base Color"].default_value=base
    if "Metallic" in bsdf.inputs: bsdf.inputs["Metallic"].default_value=metallic
    if "Roughness" in bsdf.inputs: bsdf.inputs["Roughness"].default_value=rough
    if "Specular IOR Level" in bsdf.inputs: bsdf.inputs["Specular IOR Level"].default_value=0.56

seen=[]
for o in meshes:
    for slot in o.material_slots:
        if slot.material and slot.material not in seen:
            seen.append(slot.material)
for i,m in enumerate(seen):
    tune_material(m,i)

# Collect deformed/world-space bounds.
bpy.context.view_layer.update()
corners=[]
for o in meshes:
    for c in o.bound_box:
        corners.append(o.matrix_world @ Vector(c))
mn=Vector((min(v.x for v in corners),min(v.y for v in corners),min(v.z for v in corners)))
mx=Vector((max(v.x for v in corners),max(v.y for v in corners),max(v.z for v in corners)))
center=(mn+mx)*0.5
extent=mx-mn
largest=max(extent.x,extent.y,extent.z)

# Root object for framing/orientation.
root=bpy.data.objects.new("IGNIDO_DragonRoot",None)
bpy.context.scene.collection.objects.link(root)
for o in list(bpy.context.scene.objects):
    if o == root or o.type in {"LIGHT","CAMERA"}: continue
    if o.parent is None:
        o.parent=root
root.location=-center
root.rotation_euler=(math.radians(-4), math.radians(-24), math.radians(3))
scale=3.9/max(largest,1e-5)
root.scale=(scale,scale,scale)
bpy.context.view_layer.update()

# World stays dark for reflections, film stays transparent.
world=bpy.context.scene.world or bpy.data.worlds.new("World")
bpy.context.scene.world=world
world.use_nodes=True
bg=world.node_tree.nodes.get("Background")
bg.inputs["Color"].default_value=(0.002,0.003,0.006,1)
bg.inputs["Strength"].default_value=0.08

def area(name, loc, color, energy, size):
    light=bpy.data.lights.new(name,"AREA")
    light.energy=energy
    light.color=color
    light.shape="DISK"
    light.size=size
    obj=bpy.data.objects.new(name,light)
    bpy.context.scene.collection.objects.link(obj)
    obj.location=loc
    direction=Vector((0,0.15,0))-obj.location
    obj.rotation_euler=direction.to_track_quat("-Z","Y").to_euler()
    return obj

area("FireKey",(-4.6,-3.4,4.5),(1.0,0.055,0.008),1350,3.2)
area("FireRim",(4.5,1.8,3.8),(1.0,0.18,0.025),1750,3.0)
area("ColdRim",(-1.0,4.6,2.4),(0.08,0.16,0.62),900,4.2)
area("FaceFill",(0.2,-4.3,2.4),(0.72,0.78,1.0),520,4.0)
area("UnderGlow",(0.0,0.6,-3.2),(1.0,0.035,0.006),700,3.0)

cam_data=bpy.data.cameras.new("Camera")
cam=bpy.data.objects.new("Camera",cam_data)
bpy.context.scene.collection.objects.link(cam)
bpy.context.scene.camera=cam
cam.location=(0.0,-7.1,2.6)
cam_data.lens=62
cam_data.sensor_width=36
cam_data.dof.use_dof=False
target=Vector((0,0.05,0.18))
cam.rotation_euler=(target-cam.location).to_track_quat("-Z","Y").to_euler()

scene=bpy.context.scene
engines=[i.identifier for i in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items]
scene.render.engine="BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines else "BLENDER_EEVEE"
scene.render.resolution_x=1600
scene.render.resolution_y=1600
scene.render.resolution_percentage=100
scene.render.image_settings.file_format="PNG"
scene.render.image_settings.color_mode="RGBA"
scene.render.film_transparent=True
scene.render.filepath=OUT

# Render quality, version compatible.
if hasattr(scene, "eevee"):
    if hasattr(scene.eevee, "taa_render_samples"): scene.eevee.taa_render_samples=128
if hasattr(scene, "render") and hasattr(scene.render, "film_transparent"):
    scene.render.film_transparent=True
try:
    scene.view_settings.look="AgX - Medium High Contrast"
except Exception:
    try: scene.view_settings.look="Medium High Contrast"
    except Exception: pass

bpy.ops.render.render(write_still=True)
print("rendered",OUT,"actions",[a.name for a in actions])
