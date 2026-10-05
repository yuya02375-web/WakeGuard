import bpy, math, os
from mathutils import Vector

src="/tmp/dragon.glb"
out="/tmp/dragon_realistic_render.png"

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=src)

meshes=[o for o in bpy.context.scene.objects if o.type=="MESH"]
if not meshes:
    raise RuntimeError("dragon GLB has no mesh")

# Compute world-space bounds.
corners=[]
for o in meshes:
    for c in o.bound_box:
        corners.append(o.matrix_world @ Vector(c))
mn=Vector((min(v.x for v in corners),min(v.y for v in corners),min(v.z for v in corners)))
mx=Vector((max(v.x for v in corners),max(v.y for v in corners),max(v.z for v in corners)))
center=(mn+mx)*0.5
extent=mx-mn
scale=3.3/max(extent.x,extent.y,extent.z)

root=bpy.data.objects.new("IGNIDO_DragonRoot",None)
bpy.context.scene.collection.objects.link(root)
for o in list(bpy.context.scene.objects):
    if o.type=="MESH" and o.parent is None:
        o.parent=root
root.location=-center*scale
root.scale=(scale,scale,scale)
root.rotation_euler=(math.radians(-6),math.radians(-20),math.radians(4))

# Improve imported materials without discarding texture detail.
for o in meshes:
    for slot in o.material_slots:
        m=slot.material
        if not m: continue
        m.use_nodes=True
        bsdf=m.node_tree.nodes.get("Principled BSDF")
        if bsdf:
            bsdf.inputs["Roughness"].default_value=0.38
            bsdf.inputs["Metallic"].default_value=0.06
            bsdf.inputs["Specular IOR Level"].default_value=0.48

world=bpy.context.scene.world or bpy.data.worlds.new("World")
bpy.context.scene.world=world
world.use_nodes=True
bg=world.node_tree.nodes.get("Background")
bg.inputs["Color"].default_value=(0.006,0.008,0.014,1)
bg.inputs["Strength"].default_value=0.16

def area(name,loc,color,energy,size):
    light=bpy.data.lights.new(name,"AREA")
    light.energy=energy
    light.color=color
    light.shape="DISK"
    light.size=size
    obj=bpy.data.objects.new(name,light)
    bpy.context.scene.collection.objects.link(obj)
    obj.location=loc
    direction=Vector((0,0.2,0))-obj.location
    obj.rotation_euler=direction.to_track_quat("-Z","Y").to_euler()
    return obj

area("WarmKey",(-4.2,4.6,5.3),(1.0,0.19,0.045),1150,4.0)
area("CoolFill",(4.0,1.9,3.5),(0.12,0.28,1.0),750,4.5)
area("Rim",(1.1,5.0,-4.2),(1.0,0.045,0.012),1100,3.0)
area("SoftFront",(0.0,-1.5,5.8),(0.75,0.82,1.0),450,5.0)

camData=bpy.data.cameras.new("Camera")
cam=bpy.data.objects.new("Camera",camData)
bpy.context.scene.collection.objects.link(cam)
bpy.context.scene.camera=cam
cam.location=(0.15,0.35,6.0)
camData.lens=58
camData.sensor_width=36
direction=Vector((0,0.15,0))-cam.location
cam.rotation_euler=direction.to_track_quat("-Z","Y").to_euler()

scene=bpy.context.scene
scene.render.engine="BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in [i.identifier for i in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items] else "BLENDER_EEVEE"
scene.render.resolution_x=1200
scene.render.resolution_y=1200
scene.render.resolution_percentage=100
scene.render.image_settings.file_format="PNG"
scene.render.film_transparent=True
scene.render.filepath=out
scene.render.image_settings.color_mode="RGBA"
scene.view_settings.look="AgX - Medium High Contrast" if bpy.app.version >= (4,0,0) else "Medium High Contrast"

# Transparent ground-shadow catcher approximation: dark oval is added in app, not baked.
bpy.ops.render.render(write_still=True)
print(out)
