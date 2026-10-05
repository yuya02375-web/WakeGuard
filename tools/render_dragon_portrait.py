# Blender headless script: render the licensed dragon mesh into a stable high-detail portrait.
# Usage: blender -b --python tools/render_dragon_portrait.py -- input.glb output.png
import bpy, math, sys
from mathutils import Vector

args=sys.argv
args=args[args.index("--")+1:] if "--" in args else []
if len(args)<2:
    raise SystemExit("usage: input.glb output.png")
src,out=args[0],args[1]

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=src)

meshes=[o for o in bpy.context.scene.objects if o.type=="MESH"]
if not meshes:
    raise SystemExit("no mesh imported")

# Parent imported geometry to one root and normalize its bounds.
root=bpy.data.objects.new("IGNIDO_Dragon_Root",None)
bpy.context.scene.collection.objects.link(root)
for o in meshes:
    o.parent=root

def world_bounds(objects):
    pts=[]
    for o in objects:
        for corner in o.bound_box:
            pts.append(o.matrix_world @ Vector(corner))
    mins=Vector((min(p.x for p in pts),min(p.y for p in pts),min(p.z for p in pts)))
    maxs=Vector((max(p.x for p in pts),max(p.y for p in pts),max(p.z for p in pts)))
    return mins,maxs

bmin,bmax=world_bounds(meshes)
center=(bmin+bmax)*0.5
extent=bmax-bmin
scale=3.9/max(extent.x,extent.y,extent.z)
root.location=-center
root.scale=(scale,scale,scale)
root.rotation_euler=(math.radians(8),math.radians(-10),math.radians(-18))

# Realistic dark ember material: replace the original caustic/glass material.
mat=bpy.data.materials.new("IGNIDO_Ember_Scales")
mat.use_nodes=True
nodes=mat.node_tree.nodes
links=mat.node_tree.links
for n in list(nodes): nodes.remove(n)
outn=nodes.new("ShaderNodeOutputMaterial")
bsdf=nodes.new("ShaderNodeBsdfPrincipled")
bsdf.inputs["Base Color"].default_value=(0.055,0.006,0.0025,1)
bsdf.inputs["Roughness"].default_value=0.34
bsdf.inputs["Metallic"].default_value=0.10
noise=nodes.new("ShaderNodeTexNoise")
noise.inputs["Scale"].default_value=22
noise.inputs["Detail"].default_value=5
noise.inputs["Roughness"].default_value=0.72
ramp=nodes.new("ShaderNodeValToRGB")
ramp.color_ramp.elements[0].position=0.20
ramp.color_ramp.elements[0].color=(0.008,0.001,0.0005,1)
ramp.color_ramp.elements[1].position=0.82
ramp.color_ramp.elements[1].color=(0.19,0.016,0.004,1)
links.new(noise.outputs["Fac"],ramp.inputs["Fac"])
links.new(ramp.outputs["Color"],bsdf.inputs["Base Color"])
fine=nodes.new("ShaderNodeTexNoise")
fine.inputs["Scale"].default_value=95
fine.inputs["Detail"].default_value=3
fine.inputs["Roughness"].default_value=0.64
bump=nodes.new("ShaderNodeBump")
bump.inputs["Strength"].default_value=0.34
bump.inputs["Distance"].default_value=0.07
links.new(fine.outputs["Fac"],bump.inputs["Height"])
links.new(bump.outputs["Normal"],bsdf.inputs["Normal"])
links.new(bsdf.outputs["BSDF"],outn.inputs["Surface"])
for o in meshes:
    if hasattr(o.data,"materials"):
        o.data.materials.clear()
        o.data.materials.append(mat)

# Camera.
cam_data=bpy.data.cameras.new("Camera")
cam=bpy.data.objects.new("Camera",cam_data)
bpy.context.scene.collection.objects.link(cam)
bpy.context.scene.camera=cam
cam.location=(5.0,-7.1,3.4)
cam.data.lens=58
cam.data.sensor_width=36

def look_at(obj, target):
    direction=Vector(target)-obj.location
    obj.rotation_euler=direction.to_track_quat("-Z","Y").to_euler()
look_at(cam,(0,0,0.12))

# Cinematic three-point lighting plus ember underlight.
def area(name,location,color,energy,size):
    data=bpy.data.lights.new(name,type="AREA")
    data.color=color; data.energy=energy; data.shape="DISK"; data.size=size
    o=bpy.data.objects.new(name,data); bpy.context.scene.collection.objects.link(o); o.location=location
    look_at(o,(0,0,0)); return o
area("Warm_Key",(-4.2,-4.6,6.0),(1.0,0.19,0.045),1350,4.2)
area("Cool_Fill",(4.8,-1.0,2.8),(0.12,0.24,1.0),720,4.0)
area("Red_Rim",(0.6,4.5,3.8),(1.0,0.025,0.008),1200,3.2)
point_data=bpy.data.lights.new("Ember_Under","POINT")
point_data.color=(1.0,0.08,0.01); point_data.energy=900; point_data.shadow_soft_size=1.3
point=bpy.data.objects.new("Ember_Under",point_data); bpy.context.scene.collection.objects.link(point); point.location=(0,-0.4,-1.5)

scene=bpy.context.scene
scene.render.engine="BLENDER_EEVEE"
scene.render.resolution_x=1200
scene.render.resolution_y=1200
scene.render.resolution_percentage=100
scene.render.image_settings.file_format="PNG"
scene.render.image_settings.color_mode="RGBA"
scene.render.film_transparent=True
scene.render.filepath=out
scene.render.use_file_extension=True
scene.render.image_settings.color_depth="8"
scene.world.color=(0.003,0.004,0.008)

# Render.
bpy.ops.render.render(write_still=True)
print("rendered",out)
