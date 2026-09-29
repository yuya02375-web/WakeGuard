from pathlib import Path
root=Path('WakeGuard/app')
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=s.replace('versionCode = 142','versionCode = 150',1).replace('versionName = "2.4.2"','versionName = "2.5.0"',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/StreakCompanionView.java'; s=read(p)

old='''            double power = StreakGrowth.visualPower(level);
            float dragon = smooth((float)((power - 3.15) / 3.55));
            float wings = smooth((float)((power - 4.45) / 2.25));
            float mythic = smooth((float)((power - 6.65) / 2.75));
            float infinite = (float)Math.log1p(Math.max(0L, level - 999L)) / 8.0f;
            infinite = Math.max(0f, infinite);

            c.save();
            float sx = MASK_W / 390f;
            float sy = MASK_H / 430f;
            c.translate(MASK_W * .5f, MASK_H * .55f);
            c.scale(sx, sy);
            drawFlameMask(c, p, power);
            if (dragon > .02f) drawDragonMask(c, p, power, dragon, wings, mythic, infinite);
            c.restore();'''
new='''            double power = StreakGrowth.visualPower(level);
            // Growth is based on the actual level here, not only logarithmic visualPower.
            // By the high 20s the dragon must be unmistakably visible, not merely a faint mask.
            float dragon = smooth((level - 8f) / 24f);
            float wings = smooth((level - 18f) / 28f);
            float mythic = smooth((level - 80f) / 120f);
            float infinite = (float)Math.log1p(Math.max(0L, level - 999L)) / 8.0f;
            infinite = Math.max(0f, infinite);

            c.save();
            float sx = MASK_W / 390f;
            float sy = MASK_H / 430f;
            c.translate(MASK_W * .5f, MASK_H * .55f);
            c.scale(sx, sy);

            // Alpha channel = overall flame/dragon silhouette.
            // Red channel = dragon identity mask.  Keeping these separate lets
            // the shader render a visible dragon INSIDE the flame rather than
            // losing it in one undifferentiated orange blob.
            p.setColor(0xff000000);
            p.setAlpha(255);
            drawFlameMask(c, p, power);
            if (dragon > .02f) {
                p.setColor(0xffffffff);
                p.setAlpha(255);
                drawDragonMask(c, p, power, dragon, wings, mythic, infinite);
            }
            c.restore();'''
if old not in s: raise SystemExit('Android buildMask growth block not found')
s=s.replace(old,new,1)

old='''        private static void drawDragonMask(Canvas c, Paint p, double power, float dragon, float wings, float mythic, float infinite) {
            p.setAlpha(Math.max(18, (int)(255f * dragon)));
            if (wings > .02f) drawWingMask(c, p, -1f, wings, mythic, infinite);
            if (wings > .02f) drawWingMask(c, p, 1f, wings, mythic, infinite);
            drawBodyMask(c, p, dragon, mythic);
            drawHeadMask(c, p, dragon, mythic);
            if (dragon > .18f) drawTailMask(c, p, dragon, mythic);
            if (mythic > .15f) drawMythicMask(c, p, mythic, infinite);
        }'''
new='''        private static void drawDragonMask(Canvas c, Paint p, double power, float dragon, float wings, float mythic, float infinite) {
            // Shape growth is controlled geometrically; do not hide the dragon
            // by lowering alpha.  This also fixes the old bug where wing-tip
            // alpha leaked into the later body/head/tail drawing.
            p.setAlpha(255);
            if (wings > .02f) drawWingMask(c, p, -1f, wings, mythic, infinite);
            if (wings > .02f) drawWingMask(c, p, 1f, wings, mythic, infinite);
            p.setAlpha(255);
            drawBodyMask(c, p, dragon, mythic);
            drawHeadMask(c, p, dragon, mythic);
            if (dragon > .18f) drawTailMask(c, p, dragon, mythic);
            if (mythic > .15f) drawMythicMask(c, p, mythic, infinite);
            p.setAlpha(255);
        }'''
if old not in s: raise SystemExit('Android drawDragonMask block not found')
s=s.replace(old,new,1)

# Wing tips should remain visible at early wing stages.
s=s.replace('p.setAlpha(Math.max(12, (int)(230f * wings)));',
            'p.setAlpha(Math.max(150, (int)(235f * Math.max(.25f,wings))));',1)

old='"float maskAt(vec2 uv){ return texture2D(uMask,clamp(uv,vec2(0.001),vec2(0.999))).a; }\\n" +'
new='''"float maskAt(vec2 uv){ return texture2D(uMask,clamp(uv,vec2(0.001),vec2(0.999))).a; }\\n" +
            "float dragonAt(vec2 uv){ return texture2D(uMask,clamp(uv,vec2(0.001),vec2(0.999))).r; }\\n" +'''
if old not in s: raise SystemExit('Android shader maskAt marker not found')
s=s.replace(old,new,1)

old='''            "  float m=maskAt(uv+warp);\\n" +
            "  float plume=0.0;\\n" +'''
new='''            "  float m=maskAt(uv+warp);\\n" +
            "  float d=dragonAt(uv+warp*0.30);\\n" +
            "  float plume=0.0;\\n" +'''
if old not in s: raise SystemExit('Android shader m marker not found')
s=s.replace(old,new,1)

old='''            "  vec2 r2=uTexel*11.0;\\n" +
            "  float blur2=(maskAt(uv+vec2(r2.x,0.0))+maskAt(uv-vec2(r2.x,0.0))+maskAt(uv+vec2(0.0,r2.y))+maskAt(uv-vec2(0.0,r2.y)))*0.25;\\n" +
            "  float halo=max(blur*0.82+blur2*0.42-shape*0.48,0.0);\\n" +'''
new='''            "  vec2 r2=uTexel*11.0;\\n" +
            "  float blur2=(maskAt(uv+vec2(r2.x,0.0))+maskAt(uv-vec2(r2.x,0.0))+maskAt(uv+vec2(0.0,r2.y))+maskAt(uv-vec2(0.0,r2.y)))*0.25;\\n" +
            "  float db=(dragonAt(uv+vec2(r.x,0.0))+dragonAt(uv-vec2(r.x,0.0))+dragonAt(uv+vec2(0.0,r.y))+dragonAt(uv-vec2(0.0,r.y)))*0.25;\\n" +
            "  float dragonEdge=clamp(db-d,0.0,1.0);\\n" +
            "  float halo=max(blur*0.82+blur2*0.42-shape*0.48,0.0);\\n" +'''
if old not in s: raise SystemExit('Android shader blur marker not found')
s=s.replace(old,new,1)

old='''            "  vec3 color=bg+flame*alpha*(0.90+0.18*n2)+vec3(1.0,0.19,0.018)*halo*0.70+vec3(1.0,0.52,0.06)*edge*0.28;\\n" +
            "  float region=''' 
new='''            "  vec3 color=bg+flame*alpha*(0.90+0.18*n2)+vec3(1.0,0.19,0.018)*halo*0.70+vec3(1.0,0.52,0.06)*edge*0.28;\\n" +
            "  float dragonBody=smoothstep(0.10,0.72,d);\\n" +
            "  vec3 dragonDeep=vec3(0.075,0.006,0.003);\\n" +
            "  vec3 dragonEmber=vec3(0.46,0.055,0.012);\\n" +
            "  vec3 dragonColor=mix(dragonDeep,dragonEmber,0.30+0.30*n2);\\n" +
            "  color=mix(color,dragonColor+vec3(0.22,0.025,0.004)*n3,dragonBody*0.88);\\n" +
            "  color+=vec3(1.0,0.48,0.04)*dragonEdge*1.25;\\n" +
            "  float region=''' 
if old not in s: raise SystemExit('Android shader color marker not found')
s=s.replace(old,new,1)

write(p,s)

assert 'versionName = "2.5.0"' in read('build.gradle.kts')
assert 'float dragon = smooth((level - 8f) / 24f);' in read('src/main/java/jp/wakeguard/alarm/StreakCompanionView.java')
assert 'float dragonAt(vec2 uv)' in read('src/main/java/jp/wakeguard/alarm/StreakCompanionView.java')
assert 'dragonBody*0.88' in read('src/main/java/jp/wakeguard/alarm/StreakCompanionView.java')
assert 'p.setAlpha(255);\\n            drawBodyMask' in read('src/main/java/jp/wakeguard/alarm/StreakCompanionView.java')
print('Android 2.5.0 distinct dragon renderer applied')
