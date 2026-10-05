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

p="IGNIDOWake/StreakParity.swift"; s=read(p)

old='''            loadDragon()
            addCamera()
            addLights()

            view.scene = scene
            view.backgroundColor = .clear
            view.autoenablesDefaultLighting = false
            view.antialiasingMode = .multisampling4X
            view.preferredFramesPerSecond = 60
            view.isPlaying = true
            view.rendersContinuously = true
            view.allowsCameraControl = true

            turntable.runAction(.repeatForever(.rotateBy(x: 0, y: .pi * 2, z: 0, duration: 24)))'''
new='''            loadDragon()
            let cameraNode = addCamera()
            addLights()

            view.scene = scene
            view.pointOfView = cameraNode
            view.backgroundColor = .clear
            view.autoenablesDefaultLighting = false
            view.antialiasingMode = .multisampling4X
            view.preferredFramesPerSecond = 60
            view.isPlaying = true
            view.rendersContinuously = true
            // Keep the creature framed; user gestures must not accidentally move it out of view.
            view.allowsCameraControl = false

            // Physically based materials need environment illumination in addition to punctual lights.
            scene.lightingEnvironment.contents = UIColor(white: 0.24, alpha: 1)
            scene.lightingEnvironment.intensity = 0.85

            turntable.runAction(.repeatForever(.rotateBy(x: 0, y: .pi * 2, z: 0, duration: 28)))'''
if old not in s: raise SystemExit("iOS configure block missing")
s=s.replace(old,new,1)

old='''        private func makeDragonMaterial() -> SCNMaterial {
            let m = SCNMaterial()
            m.name = "IGNIDO_Dragon_PBR"
            m.lightingModel = .physicallyBased
            m.diffuse.contents = UIColor(red: 0.115, green: 0.017, blue: 0.010, alpha: 1)
            m.metalness.contents = 0.22
            m.roughness.contents = 0.43
            m.specular.contents = UIColor(white: 0.92, alpha: 1)
            m.emission.contents = UIColor(red: 0.018, green: 0.0015, blue: 0.0008, alpha: 1)
            m.isDoubleSided = true
            return m
        }

        private func addCamera() {
            let cameraNode = SCNNode()
            let camera = SCNCamera()
            camera.fieldOfView = 27
            camera.zNear = 0.01
            camera.zFar = 100
            camera.wantsHDR = true
            camera.wantsExposureAdaptation = true
            camera.bloomIntensity = 0.65
            camera.bloomThreshold = 0.78
            camera.bloomBlurRadius = 10
            camera.vignettingIntensity = 0.46
            camera.vignettingPower = 0.9
            cameraNode.camera = camera
            cameraNode.position = SCNVector3(0, 0.15, 5.15)
            let look = SCNLookAtConstraint(target: turntable)
            look.isGimbalLockEnabled = true
            cameraNode.constraints = [look]
            scene.rootNode.addChildNode(cameraNode)
        }
'''
new='''        private func makeDragonMaterial() -> SCNMaterial {
            let m = SCNMaterial()
            m.name = "IGNIDO_Dragon_PBR_Opaque"
            m.lightingModel = .physicallyBased
            // The source model was authored as transmissive glass. Replace it with a truly opaque,
            // visible material so the geometry reads as a creature on a dark background.
            m.diffuse.contents = UIColor(red: 0.25, green: 0.022, blue: 0.010, alpha: 1)
            m.metalness.contents = 0.06
            m.roughness.contents = 0.48
            m.specular.contents = UIColor(white: 0.72, alpha: 1)
            m.emission.contents = UIColor(red: 0.020, green: 0.0015, blue: 0.0005, alpha: 1)
            m.transparency = 1.0
            m.blendMode = .replace
            m.isDoubleSided = true
            m.writesToDepthBuffer = true
            m.readsFromDepthBuffer = true
            return m
        }

        private func addCamera() -> SCNNode {
            let cameraNode = SCNNode()
            let camera = SCNCamera()
            camera.fieldOfView = 31
            camera.zNear = 0.01
            camera.zFar = 100
            camera.wantsHDR = true
            camera.wantsExposureAdaptation = true
            camera.bloomIntensity = 0.45
            camera.bloomThreshold = 0.9
            camera.bloomBlurRadius = 8
            camera.vignettingIntensity = 0.30
            camera.vignettingPower = 0.8
            cameraNode.camera = camera
            cameraNode.position = SCNVector3(0, 0.12, 4.65)
            let look = SCNLookAtConstraint(target: turntable)
            look.isGimbalLockEnabled = true
            cameraNode.constraints = [look]
            scene.rootNode.addChildNode(cameraNode)
            return cameraNode
        }
'''
if old not in s: raise SystemExit("iOS material/camera block missing")
s=s.replace(old,new,1)

# Brighter, more forgiving direct illumination.
s=s.replace('ambient.light?.intensity = 240','ambient.light?.intensity = 520',1)
s=s.replace('key.light?.intensity = 1750','key.light?.intensity = 2600',1)
s=s.replace('fill.light?.intensity = 820','fill.light?.intensity = 1150',1)
s=s.replace('rim.light?.intensity = 1200','rim.light?.intensity = 1500',1)

old='''            let p = max(0.0, min(1.0, Float(level - 7) / 20.0))
            dragon.opacity = CGFloat(0.08 + 0.92 * p)
            let growth = 0.82 + 0.18 * p + min(0.10, max(0, Float(level - 30)) / 500.0)'''
new='''            let p = max(0.0, min(1.0, Float(level - 5) / 16.0))
            // Lv.21+ is always fully opaque; Lv.27 cannot disappear because of growth alpha.
            dragon.opacity = level >= 21 ? 1.0 : CGFloat(0.18 + 0.82 * p)
            let growth = 0.86 + 0.14 * p + min(0.10, max(0, Float(level - 30)) / 500.0)'''
if old not in s: raise SystemExit("iOS growth block missing")
s=s.replace(old,new,1)
write(p,s)

assert '<string>2.8.1</string>' in read("IGNIDOWake/Info.plist")
src=read("IGNIDOWake/StreakParity.swift")
assert "view.pointOfView = cameraNode" in src
assert "scene.lightingEnvironment.intensity = 0.85" in src
assert "m.blendMode = .replace" in src
assert "dragon.opacity = level >= 21 ? 1.0" in src
print("iOS 2.8.1 visible 3D dragon fix applied")
