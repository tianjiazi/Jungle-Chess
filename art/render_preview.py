"""Offline reference renders from the actual GLB geometry; not headset screenshots."""
from pathlib import Path
import numpy as np
import trimesh as tm
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/"app/src/main/assets"
OUT=ROOT/"art"
FONT="/System/Library/Fonts/STHeiti Medium.ttc"
def font(n):return ImageFont.truetype(FONT,n)

def load(name):
    sc=tm.load(ASSETS/f"{name}.glb",force="scene")
    for m in sc.geometry.values():
        if hasattr(m.visual,"vertex_attributes") and "color" in m.visual.vertex_attributes:
            colors=m.visual.vertex_attributes["color"]
            m.visual=tm.visual.ColorVisuals(mesh=m,vertex_colors=colors)
    return sc.to_mesh()

def render(mesh, size, eye, target, scale, bg="#E8EDE1"):
    im=Image.new("RGB",size,bg); draw=ImageDraw.Draw(im)
    eye=np.array(eye); target=np.array(target)
    front=(eye-target); front/=np.linalg.norm(front)
    right=np.cross([0,1,0],front); right/=np.linalg.norm(right)
    up=np.cross(front,right)
    basis=np.array([right,up,front])
    v=(mesh.vertices-target)@basis.T
    pts=np.stack([v[:,0]*scale+size[0]/2,-v[:,1]*scale+size[1]/2],axis=-1)
    f=mesh.faces
    normals=mesh.face_normals
    light=np.array([-.5,.8,.8]);light/=np.linalg.norm(light)
    linear=mesh.visual.vertex_colors[:,:3]/255.
    colors=np.clip(np.where(linear<=.0031308,linear*12.92,1.055*linear**(1/2.4)-.055)*255,0,255)
    pixels=np.array(im);depth=np.full((size[1],size[0]),-np.inf)
    for i in range(len(f)):
        if normals[i]@front < -.08: continue
        tri=pts[f[i]];zs=v[f[i],2];cs=colors[f[i]]
        xmin=max(0,int(np.floor(tri[:,0].min())));xmax=min(size[0]-1,int(np.ceil(tri[:,0].max())))
        ymin=max(0,int(np.floor(tri[:,1].min())));ymax=min(size[1]-1,int(np.ceil(tri[:,1].max())))
        if xmin>xmax or ymin>ymax:continue
        yy,xx=np.mgrid[ymin:ymax+1,xmin:xmax+1];xx=xx+.5;yy=yy+.5
        a,b,c=tri
        denom=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(denom)<1e-8:continue
        wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/denom
        wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/denom
        wc=1-wa-wb
        z=wa*zs[0]+wb*zs[1]+wc*zs[2]
        region=depth[ymin:ymax+1,xmin:xmax+1]
        mask=(wa>=0)&(wb>=0)&(wc>=0)&(z>region)
        region[mask]=z[mask]
        col=wa[:,:,None]*cs[0]+wb[:,:,None]*cs[1]+wc[:,:,None]*cs[2]
        pixels[ymin:ymax+1,xmin:xmax+1][mask]=col[mask].astype(np.uint8)
    return Image.fromarray(pixels)

def models():
    w,h=1920,1280
    canvas=Image.new("RGB",(w,h),"#E8EDE1");d=ImageDraw.Draw(canvas)
    d.text((72,40),"林间斗兽棋  /  原创动物雕塑",font=font(46),fill="#294C43")
    d.text((75,104),"16枚独立3D棋子 · 红蓝底座与项圈 · 底座金色圆点表示等级",font=font(22),fill="#6D8075")
    names=["elephant","lion","tiger","leopard","wolf","dog","cat","rat"]
    labels=["象 · 8","狮 · 7","虎 · 6","豹 · 5","狼 · 4","狗 · 3","猫 · 2","鼠 · 1"]
    for i,(name,label) in enumerate(zip(names,labels)):
        parts=[]
        for team,x in [("red",-.057),("blue",.064)]:
            m=load(f"{team}_{name}"); m.apply_scale(.83); m.apply_translation([x,0,0])
            parts.append(m)
        mesh=tm.util.concatenate(parts)
        image=render(mesh,(440,420),[.20,.22,.7],[0,.065,0],1800)
        x=55+(i%4)*465;y=165+(i//4)*530
        canvas.paste(image,(x,y));d.text((x+170,y+420),label,font=font(27),fill="#294C43")
    d.text((75,1216),"由交付GLB模型离线渲染；非头显实机截图。",font=font(21),fill="#6D8075")
    canvas.save(OUT/"animal-lineup.jpg",quality=95)

def overview():
    parts=[load("board")]
    setup=[(0,0,"lion"),(6,0,"tiger"),(1,1,"dog"),(5,1,"cat"),(0,2,"rat"),(2,2,"leopard"),(4,2,"wolf"),(6,2,"elephant")]
    for x,y,name in setup:
        for side,xx,yy in [("blue",x,y),("red",6-x,8-y)]:
            m=load(f"{side}_{name}");m.apply_translation([(xx-3)*.16,.032,(yy-4)*.16]);parts.append(m)
    mesh=tm.util.concatenate(parts)
    image=render(mesh,(1800,1500),[1.1,1.8,2.6],[0,-.10,0],830)
    d=ImageDraw.Draw(image)
    d.text((70,50),"林间斗兽棋",font=font(58),fill="#294C43")
    d.text((72,132),"FULLSPACE  /  经典7×9棋盘",font=font(27),fill="#607D70")
    d.text((70,1400),"实际模型离线预览 · 双河流 / 六陷阱 / 双兽穴",font=font(25),fill="#607D70")
    image.save(OUT/"board-preview.jpg",quality=95)
def launcher():
    image=render(load("red_lion"),(512,512),[.15,.16,.6],[0,.077,0],2300)
    image.save(ROOT/"app/src/main/res/mipmap-xxxhdpi/ic_spatial_launcher.png")

if __name__=="__main__":
    models();overview();launcher()
    print("Rendered animal-lineup.jpg, board-preview.jpg and launcher icon")
