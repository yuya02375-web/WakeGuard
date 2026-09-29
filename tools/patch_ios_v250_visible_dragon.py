from pathlib import Path
root=Path('ios')
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='IGNIDOWake/Info.plist'; s=read(p)
s=s.replace('<string>2.4.2</string>','<string>2.5.0</string>',1).replace('<string>142</string>','<string>150</string>',1)
write(p,s)
p='project.yml'; s=read(p)
s=s.replace('CFBundleShortVersionString: "2.4.2"','CFBundleShortVersionString: "2.5.0"',1).replace('CFBundleVersion: "142"','CFBundleVersion: "150"',1)
write(p,s)

p='IGNIDOWake/StreakParity.swift'; s=read(p)
start=s.find('struct GrowthCompanionView: View {')
if start<0: raise SystemExit('iOS GrowthCompanionView not found')
end=s.find('\\n}', start)
# find matching by simple brace count
depth=0; pos=start; end=None
while pos < len(s):
    if s[pos]=='{': depth+=1
    elif s[pos]=='}':
        depth-=1
        if depth==0:
            end=pos+1; break
    pos+=1
if end is None: raise SystemExit('iOS GrowthCompanionView end not found')
new='''struct GrowthCompanionView: View {
    let level:Int
    let streak:Int
    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            let power = log1p(Double(max(1,level))) / log(2.0)
            let dragon = smoothGrowth((Double(level) - 8.0) / 24.0)
            let wings = smoothGrowth((Double(level) - 18.0) / 28.0)
            ZStack {
                ForEach(0..<min(7,max(1,Int(power))),id:\\.self){i in
                    IgnidoFlameShape()
                        .fill(i%2==0 ? IgnidoTheme.ember.opacity(0.55):IgnidoTheme.flame.opacity(0.48))
                        .frame(width:s*(0.25+CGFloat(i)*0.035),height:s*(0.45+CGFloat(i)*0.045))
                        .rotationEffect(.degrees(Double(i-3)*9))
                        .offset(x:CGFloat(i-3)*s*0.055,y:s*0.10)
                }
                IgnidoFlameShape().fill(IgnidoTheme.flameGradient).frame(width:s*0.56,height:s*0.82)
                IgnidoFlameShape().fill(IgnidoTheme.hot.opacity(0.84)).frame(width:s*0.22,height:s*0.36).offset(y:s*0.18)

                if dragon > 0.02 {
                    IgnidoDragonShape(dragon: dragon, wings: wings)
                        .fill(Color.black.opacity(0.78))
                        .overlay(
                            IgnidoDragonShape(dragon: dragon, wings: wings)
                                .stroke(IgnidoTheme.ember.opacity(0.95), lineWidth:max(2,s*0.012))
                        )
                        .frame(width:s*0.82,height:s*0.78)
                        .offset(y:s*0.045)

                    HStack(spacing:s*0.075) {
                        Circle().fill(IgnidoTheme.hot).frame(width:max(3,s*0.022),height:max(3,s*0.022))
                        Circle().fill(IgnidoTheme.hot).frame(width:max(3,s*0.022),height:max(3,s*0.022))
                    }
                    .offset(y:-s*0.18)
                }
                if streak>=30 { Circle().fill(IgnidoTheme.hot).frame(width:7,height:7).offset(x:s*0.33,y:-s*0.24) }
            }
            .frame(maxWidth:.infinity,maxHeight:.infinity)
        }
    }

    private func smoothGrowth(_ raw: Double) -> CGFloat {
        let t = max(0.0,min(1.0,raw))
        return CGFloat(t*t*(3.0-2.0*t))
    }
}

private struct IgnidoDragonShape: Shape {
    let dragon: CGFloat
    let wings: CGFloat

    func path(in r: CGRect) -> Path {
        let w=r.width, h=r.height, cx=w*0.5
        var p=Path()

        // Left wing: already a visible bud in the high 20s, then expands continuously.
        if wings > 0.02 {
            let span=w*(0.16+0.20*wings)
            p.move(to:CGPoint(x:cx-w*0.10,y:h*0.39))
            p.addCurve(to:CGPoint(x:cx-span,y:h*(0.21-0.05*wings)),control1:CGPoint(x:cx-w*0.18,y:h*0.27),control2:CGPoint(x:cx-span*0.82,y:h*0.18))
            p.addCurve(to:CGPoint(x:cx-w*0.12,y:h*0.55),control1:CGPoint(x:cx-span*0.92,y:h*0.44),control2:CGPoint(x:cx-w*0.22,y:h*0.53))
            p.closeSubpath()
            p.move(to:CGPoint(x:cx+w*0.10,y:h*0.39))
            p.addCurve(to:CGPoint(x:cx+span,y:h*(0.21-0.05*wings)),control1:CGPoint(x:cx+w*0.18,y:h*0.27),control2:CGPoint(x:cx+span*0.82,y:h*0.18))
            p.addCurve(to:CGPoint(x:cx+w*0.12,y:h*0.55),control1:CGPoint(x:cx+span*0.92,y:h*0.44),control2:CGPoint(x:cx+w*0.22,y:h*0.53))
            p.closeSubpath()
        }

        // Body and neck.
        p.move(to:CGPoint(x:cx-w*0.12,y:h*0.34))
        p.addCurve(to:CGPoint(x:cx-w*0.10,y:h*0.78),control1:CGPoint(x:cx-w*0.19,y:h*0.48),control2:CGPoint(x:cx-w*0.18,y:h*0.68))
        p.addCurve(to:CGPoint(x:cx,y:h*0.88),control1:CGPoint(x:cx-w*0.07,y:h*0.84),control2:CGPoint(x:cx-w*0.03,y:h*0.88))
        p.addCurve(to:CGPoint(x:cx+w*0.10,y:h*0.78),control1:CGPoint(x:cx+w*0.03,y:h*0.88),control2:CGPoint(x:cx+w*0.07,y:h*0.84))
        p.addCurve(to:CGPoint(x:cx+w*0.12,y:h*0.34),control1:CGPoint(x:cx+w*0.18,y:h*0.68),control2:CGPoint(x:cx+w*0.19,y:h*0.48))
        p.addCurve(to:CGPoint(x:cx,y:h*0.28),control1:CGPoint(x:cx+w*0.08,y:h*0.29),control2:CGPoint(x:cx+w*0.04,y:h*0.28))
        p.addCurve(to:CGPoint(x:cx-w*0.12,y:h*0.34),control1:CGPoint(x:cx-w*0.04,y:h*0.28),control2:CGPoint(x:cx-w*0.08,y:h*0.29))
        p.closeSubpath()

        // Dragon head + muzzle.
        p.move(to:CGPoint(x:cx-w*0.13,y:h*0.28))
        p.addCurve(to:CGPoint(x:cx-w*0.08,y:h*0.13),control1:CGPoint(x:cx-w*0.17,y:h*0.22),control2:CGPoint(x:cx-w*0.14,y:h*0.16))
        p.addLine(to:CGPoint(x:cx-w*0.035,y:h*0.11))
        p.addLine(to:CGPoint(x:cx,y:h*0.055))
        p.addLine(to:CGPoint(x:cx+w*0.035,y:h*0.11))
        p.addLine(to:CGPoint(x:cx+w*0.08,y:h*0.13))
        p.addCurve(to:CGPoint(x:cx+w*0.13,y:h*0.28),control1:CGPoint(x:cx+w*0.14,y:h*0.16),control2:CGPoint(x:cx+w*0.17,y:h*0.22))
        p.addLine(to:CGPoint(x:cx+w*0.075,y:h*0.32))
        p.addLine(to:CGPoint(x:cx+w*0.13,y:h*0.35))
        p.addLine(to:CGPoint(x:cx,y:h*0.38))
        p.addLine(to:CGPoint(x:cx-w*0.13,y:h*0.35))
        p.addLine(to:CGPoint(x:cx-w*0.075,y:h*0.32))
        p.closeSubpath()

        // Horns.
        let horn = h*(0.055+0.045*dragon)
        p.move(to:CGPoint(x:cx-w*0.07,y:h*0.15))
        p.addLine(to:CGPoint(x:cx-w*0.16,y:h*0.15-horn))
        p.addLine(to:CGPoint(x:cx-w*0.09,y:h*0.20))
        p.closeSubpath()
        p.move(to:CGPoint(x:cx+w*0.07,y:h*0.15))
        p.addLine(to:CGPoint(x:cx+w*0.16,y:h*0.15-horn))
        p.addLine(to:CGPoint(x:cx+w*0.09,y:h*0.20))
        p.closeSubpath()

        // Tail.
        if dragon > 0.18 {
            p.move(to:CGPoint(x:cx-w*0.05,y:h*0.74))
            p.addCurve(to:CGPoint(x:cx-w*(0.32+0.10*dragon),y:h*0.88),control1:CGPoint(x:cx-w*0.18,y:h*0.84),control2:CGPoint(x:cx-w*0.28,y:h*0.82))
            p.addCurve(to:CGPoint(x:cx-w*0.09,y:h*0.68),control1:CGPoint(x:cx-w*0.24,y:h*0.76),control2:CGPoint(x:cx-w*0.14,y:h*0.69))
            p.closeSubpath()
        }
        return p
    }
}'''
s=s[:start]+new+s[end:]
write(p,s)

assert '<string>2.5.0</string>' in read('IGNIDOWake/Info.plist')
assert 'IgnidoDragonShape(dragon: dragon, wings: wings)' in read('IGNIDOWake/StreakParity.swift')
assert 'fill(Color.black.opacity(0.78))' in read('IGNIDOWake/StreakParity.swift')
assert 'let wings = smoothGrowth((Double(level) - 18.0) / 28.0)' in read('IGNIDOWake/StreakParity.swift')
print('iOS 2.5.0 distinct dragon renderer applied')
