from pathlib import Path
root=Path("ios")
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p="IGNIDOWake/Info.plist"; s=read(p)
s=s.replace('<string>2.8.0</string>','<string>2.8.1</string>',1).replace('<string>180</string>','<string>181</string>',1)
write(p,s)
p="project.yml"; s=read(p)
s=s.replace('CFBundleShortVersionString: "2.8.0"','CFBundleShortVersionString: "2.8.1"',1).replace('CFBundleVersion: "180"','CFBundleVersion: "181"',1)
write(p,s)

# Defensive SceneKit setup: if the imported model has an invalid/empty bounding box,
# keep the fallback geometry instead of propagating NaN/Inf transforms.
p="IGNIDOWake/StreakParity.swift"; s=read(p)
old='''            let (bmin, bmax) = dragon.boundingBox
            let dx = max(0.0001, bmax.x - bmin.x)
            let dy = max(0.0001, bmax.y - bmin.y)
            let dz = max(0.0001, bmax.z - bmin.z)
            let maxDimension = max(dx, max(dy, dz))
            normalizedScale = 2.65 / maxDimension
            let center = SCNVector3((bmin.x + bmax.x) * 0.5,
                                    (bmin.y + bmax.y) * 0.5,
                                    (bmin.z + bmax.z) * 0.5)
'''
new='''            let (bmin, bmax) = dragon.boundingBox
            let dx = bmax.x - bmin.x
            let dy = bmax.y - bmin.y
            let dz = bmax.z - bmin.z
            let maxDimension = max(dx, max(dy, dz))
            guard maxDimension.isFinite, maxDimension > 0.0001 else {
                dragon.childNodes.forEach { $0.removeFromParentNode() }
                let fallback = SCNSphere(radius: 0.65)
                fallback.segmentCount = 96
                fallback.materials = [makeDragonMaterial()]
                dragon.geometry = fallback
                normalizedScale = 1
                return
            }
            normalizedScale = 2.65 / maxDimension
            let center = SCNVector3((bmin.x + bmax.x) * 0.5,
                                    (bmin.y + bmax.y) * 0.5,
                                    (bmin.z + bmax.z) * 0.5)
'''
if old not in s: raise SystemExit("iOS dragon bounds marker missing")
s=s.replace(old,new,1)
write(p,s)

assert '<string>2.8.1</string>' in read("IGNIDOWake/Info.plist")
assert 'maxDimension.isFinite' in read("IGNIDOWake/StreakParity.swift")
print("iOS 2.8.1 defensive streak renderer patch applied")
