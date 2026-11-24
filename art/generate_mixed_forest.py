"""Rebuild forest only, preserving the existing board, tokens, sky and ground details."""
import json
import trimesh as tm
from generate_assets import environment, tree, ROOT
from render_preview import load, render, font
from PIL import Image, ImageDraw

forest=environment()
manifest=forest.export("forest")
manifest["seed"]=19
manifest["trees"]=forest.tree_inventory
(ROOT/"art/mixed_forest_manifest.json").write_text(json.dumps(manifest,indent=2)+"\n")
path=ROOT/"art/asset_manifest.json"
old=json.loads(path.read_text())
for entry in old:
    if entry["file"]=="forest.glb":
        entry.update({k:manifest[k] for k in ("triangles","vertices","bytes")})
path.write_text(json.dumps(old,indent=2)+"\n")

canvas=Image.new("RGB",(1600,1000),"#E8EDE1")
draw=ImageDraw.Draw(canvas)
draw.text((50,35),"林间斗兽棋 · 混合林树木",font=font(40),fill="#294C43")
for i,(style,label,height,width) in enumerate([
    ("round","圆冠树",2.1,.66),("pine","尖顶针叶树",3.3,.68),
    ("slender","细长树冠",2.7,.48),("spreading","舒展阔叶树",2.5,.67),
]):
    specimen=tree(style,height,width)
    image=render(tm.util.concatenate(specimen.parts),(390,760),[.6,2.5,6],[0,1.65,0],185)
    canvas.paste(image,(20+i*395,110))
    draw.text((70+i*395,865),label,font=font(28),fill="#294C43")
draw.text((50,950),"实际几何离线预览 · 固定随机种子 · 高低错落",font=font(22),fill="#607D70")
canvas.save(ROOT/"art/mixed-tree-lineup.png")
mesh=tm.util.concatenate([load("forest"),load("forest_details")])
image=render(mesh,(1800,1400),[7,7,9],[0,.9,-.4],155)
draw=ImageDraw.Draw(image)
draw.text((45,40),"林间斗兽棋 · 混合林环境",font=font(38),fill="#294C43")
draw.text((45,1330),"交付GLB离线渲染；不是头显截图",font=font(24),fill="#607D70")
image.save(ROOT/"art/mixed-forest-preview.png")
print(json.dumps({k:manifest[k] for k in ("file","triangles","vertices","bytes","seed")}))
