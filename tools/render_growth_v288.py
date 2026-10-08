from pathlib import Path
from io import BytesIO
import cairosvg
from PIL import Image

art=Path('assets/dragon-growth-v288')
out=Path('WakeGuard/app/src/main/res/drawable-nodpi')
out.mkdir(parents=True,exist_ok=True)
for stage in ('ember','flame','egg','hatchling'):
    source=art/f'ignido_stage_{stage}.svg'
    assert source.is_file(), source
    png=cairosvg.svg2png(url=str(source),output_width=768,output_height=768)
    image=Image.open(BytesIO(png)).convert('RGBA')
    assert image.size==(768,768)
    a=image.getchannel('A')
    assert a.getextrema()==(0,255), f'{stage} not transparent'
    assert a.getbbox(), f'{stage} is blank'
    path=out/f'ignido_stage_{stage}.webp'
    image.save(path,'WEBP',quality=93,method=6)
    print(stage,path,path.stat().st_size)
