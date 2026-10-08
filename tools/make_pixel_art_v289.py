"""IGNIDO v2.8.9 RPG pixel sprites.
Early stage sprites: original deliberately pixel-placed art.
Hatchling/adult derived from kotnaszynce's CC0 smoku dragon sprites.
https://opengameart.org/content/dragon-9
No generative images and no non-CC0 external assets.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
import random, math, zipfile

BASE = Path(__file__).resolve().parent
SRC=BASE/'smoku'
OUT=BASE/'rendered'
OUT.mkdir(exist_ok=True)
S=64
P={
  'line':'#26171c', 'deep':'#352329','shade':'#503039','stone':'#694345',
  'rocklit':'#996057','bodydark':'#6e3039','body':'#aa4346','bodylight':'#d36a51',
  'wingdark':'#773534','wing':'#c05740','winglit':'#eb8f4b',
  'firedeep':'#9e302c','fire':'#ec6335','firelit':'#ffad50',
  'firecore':'#ffe2a1','ash':'#d6b4a0','glint':'#fff4cd'
}

def base():return Image.new('RGBA',(S,S),(0,0,0,0))
def poly(d,pts,key):d.polygon(pts,fill=P.get(key,key))
def rect(d,box,key):d.rectangle(box,fill=P.get(key,key))
def line(d,pts,key,width=1):d.line(pts, fill=P.get(key,key),width=width,joint='curve')

def coal(d,x,y,w=17):
    # asymmetrical outlines, never circular gradients
    poly(d,[(x-w//2,y),(x-w//2+3,y-5),(x+w//2-4,y-7),(x+w//2,y-4),(x+w//2,y+2),(x+w//2-4,y+5),(x-w//2+3,y+4)],'line')
    poly(d,[(x-w//2+2,y-1),(x-w//2+5,y-5),(x+w//2-4,y-5),(x+w//2-2,y-3),(x+w//2-2,y+1),(x-w//2+4,y+2)],'shade')
    line(d,[(x-w//2+5,y-3),(x-w//2+8,y-4),(x+w//2-5,y-3)],'rocklit',1)

def flicker(d,x,y,h,variation):
    """Hand placed angular flame shapes, with frame-to-frame posture changes."""
    sway=[0,1,2,1,0,-1,-2,-1][variation%8]
    k=max(4,h//4)
    # flame mid line deliberately zigzag
    pts=[(x-5,y),(x-7,y-5),(x-3,y-h//2+3),
         (x-5+sway,y-h+7),(x+sway+1,y-h),
         (x+sway+2,y-h+8),(x+5,y-h//2),(x+7,y-3),(x+4,y)]
    poly(d,pts,'line')
    poly(d,[(x-4,y-1),(x-5,y-5),(x-2,y-h//2+4),
            (x-2+sway,y-h+8),(x+1+sway,y-h+3),
            (x+1+sway,y-h+10),(x+4,y-h//2+1),(x+5,y-3),(x+3,y-1)],'fire')
    poly(d,[(x-2,y-2),(x-2,y-9),(x+1,y-h//2+3),(x+3,y-7),(x+3,y-2)],'firelit')
    poly(d,[(x,y-3),(x,y-9),(x+2,y-6),(x+2,y-2)],'firecore')

def ember_frame(t):
    im=base();d=ImageDraw.Draw(im)
    coal(d,24,53,16); coal(d,35,52,17);coal(d,29,57,14)
    # deliberately a modest ignition, not a mature beast
    line(d,[(26,50),(28,47),(33,49),(34,53)],'firedeep',2)
    flicker(d,30+(t%3-1),48,10+(t%4),t)
    for j in range(3):
        x=24+(j*7)+(t+j)%3;y=38-j*4-(t+j)%3
        rect(d,(x,y,x,y), 'firelit' if j==1 else 'fire')
    return im

def flame_frame(t):
    im=base();d=ImageDraw.Draw(im)
    coal(d,23,53,16);coal(d,36,52,20);coal(d,30,58,18)
    # large animated central flame with occasional licking tongues
    flicker(d,30+(t-3)//3,47,24+(t%3)*2,t)
    poly(d,[(27,49),(25,43),(23-(t%2),36),(21,34),(23,44),(28,47)],'firedeep')
    poly(d,[(34,49),(40,43),(41+(t%2),36),(38,34),(38,42),(32,47)],'fire')
    for j in range(4):
        x=16+(j*10)+(t+j)%2
        y=24+j*4-(t%3)
        rect(d,(x,y,x+1,y+1),'firelit' if j%2 else 'firedeep')
    return im

def egg_frame(t):
    im=base();d=ImageDraw.Draw(im)
    coal(d,22,53,20);coal(d,39,52,18);coal(d,30,58,19)
    # angular pixelated, no perfect elliptical generator
    outline=[(22,48),(19,43),(19,34),(21,27),(24,22),(27,18),(34,18),
             (39,23),(42,29),(43,38),(41,46),(37,51),(26,51)]
    poly(d,outline,'line')
    poly(d,[(23,46),(21,40),(22,31),(25,24),(29,21),(33,20),(37,25),
            (40,31),(41,39),(39,45),(35,49),(27,49)],'stone')
    poly(d,[(24,29),(28,23),(31,22),(34,23),(37,29),(32,28)],'rocklit')
    poly(d,[(21,38),(24,45),(28,48),(31,48),(26,40)],'shade')
    # story-bearing crack is persistent; glowing deep inside
    crack=[(32,24),(30,31),(34,35),(29,41),(33,45),(31,49)]
    line(d,crack,'line',4)
    line(d,crack,'firedeep',2)
    if t%6 in (2,3,4):line(d,[(31,31),(33,35),(29,41)],'firelit',2)
    else:line(d,[(31,31),(33,35),(29,41)],'fire',1)
    line(d,[(34,35),(39,36),(41,40)],'firedeep',2)
    line(d,[(29,41),(25,41),(23,45)],'firedeep',2)
    rect(d,(33,45,34,47),'firecore' if t%3==0 else 'firelit')
    # slight ember dust inside/around
    if t%2:rect(d,(46,34,46,35),'fire')
    return im

ORIGINAL={(99,155,255):(211,117,86),(91,110,225):(164,65,67),
          (63,63,116):(102,42,51),(41,8,0):(38,20,24),
          (217,160,102):(217,102,62),(242,219,146):(253,183,98)}

def recolor_original(im):
    im=im.convert('RGBA'); out=base(); source=im.load(); pix=out.load()
    for y in range(64):
        for x in range(64):
            r,g,b,a=source[x,y]
            if a==0:continue
            c=ORIGINAL.get((r,g,b), (r,g,b))
            pix[x,y]=(*c,255)
    return out

def dragon_original_frames():
    return [recolor_original(Image.open(SRC/f'smoku {i}.png')) for i in range(1,6)]

def hatchling_frame(t,original):
    # same artist's identity/palette, but distinctly smaller as a hatchling
    target=base()
    bb=original.getbbox()
    cropped=original.crop(bb)
    # flatten the proud tall adult into a young compact creature, but keep crisp pixels
    w=25;h=29
    child=cropped.resize((w,h),Image.Resampling.NEAREST)
    # wing is reduced in relative scale; cute bigger eye is an intentional edit
    target.paste(child,(18,26),child)
    d=ImageDraw.Draw(target)
    # bright amber eye; small horn and mouth distinguish the juvenile
    rect(d,(40,34,41,35),'glint')
    rect(d,(40,35,41,36),'firedeep')
    rect(d,(34,33,35,34),'bodylight')
    # tiny flame nimbus + shadow under its feet, classic RPG sprite styling
    if t%5 in (1,2):rect(d,(44,51,45,52),'firelit')
    return target

def emit(name,frames,ms):
    p=OUT/f'ignido_stage_{name}.webp'
    # lossless preserves pixels exactly, even for dark outline corners
    frames[0].save(p,'WEBP',save_all=True,append_images=frames[1:],lossless=True,quality=100,duration=ms,loop=0,method=6)
    im=Image.open(p)
    assert im.n_frames==len(frames),(name, im.n_frames,len(frames))
    assert im.size==(64,64)
    print(name,im.n_frames,p.stat().st_size)
    return p

adult=dragon_original_frames()
arts={
    'ember':[ember_frame(i) for i in range(8)],
    'flame':[flame_frame(i) for i in range(8)],
    'egg':[egg_frame(i) for i in range(6)],
    'hatchling':[hatchling_frame(i, adult[i%5]) for i in range(5)],
    'adult':adult,
}
for name,frames in arts.items():emit(name,frames,180 if name in ['ember','flame'] else 250 if name=='egg' else 300)

# Contact sheet resembles display use: pixelated images scaled NEAREST on neutral solid panels.
font_paths=['/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc','/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf']
font=next((ImageFont.truetype(p,22) for p in font_paths if Path(p).exists()),ImageFont.load_default())
small=next((ImageFont.truetype(p,15) for p in font_paths if Path(p).exists()),ImageFont.load_default())
titles=[('ember','火種','Lv.1–3'),('flame','小さな炎','Lv.4–11'),('egg','炎竜の兆し','Lv.12–29'),('hatchling','幼炎竜','Lv.30–79'),('adult','炎竜','Lv.80+')]
sheet=Image.new('RGB',(5*240,325),(15,18,26));d=ImageDraw.Draw(sheet)
for j,(name,kanji,sub) in enumerate(titles):
    x=j*240
    d.rounded_rectangle((x+8,8,x+232,317),radius=14,fill='#191e29',outline='#353d49',width=1)
    d.text((x+27,32),kanji,font=font,fill='#f1e7d4')
    d.text((x+27,64),sub,font=small,fill='#a6adbb')
    spr=arts[name][2%len(arts[name])].resize((192,192),Image.Resampling.NEAREST)
    sheet.paste(spr,(x+24,97),spr)
    d.rectangle((x+60,296,x+180,298),fill='#733e38')
sheet.save(BASE/'preview_v289.png')
(OUT/'LICENSE_AND_CREDITS.txt').write_text('''IGNIDO Wake 2.8.9 - sprite provenance\nOriginal adult dragon animations: kotnaszynce, "dragon" (2019), CC0 1.0\nhttps://opengameart.org/content/dragon-9\nThe five original 64x64 animation frames were recolored and adapted for young dragon.\nFire, ember, and egg forms are pixel-arranged original artwork for this project.\nSprites are rendered at 64x64 and scaled with nearest-neighbor, lossless WebP.\n''',encoding='utf8')
