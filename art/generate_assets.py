"""Original procedural animal sculptures and board. Metres, Y-up, +Z faces viewer.
Run with numpy, trimesh, pillow, pygltflib. No downloaded art or external assets.
"""
from pathlib import Path
import math, json, struct
import numpy as np
import trimesh as tm
from pygltflib import GLTF2

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets"
OUT.mkdir(parents=True, exist_ok=True)
RED = "#F42F4D"
BLUE = "#2465F5"
GOLD = "#FFBE19"
IVORY = "#FFF1CA"
DARK = "#172940"
PINK = "#FF719B"

def rgba(c):
    return np.array([int(c[i:i+2],16) for i in (1,3,5)]+[255],dtype=np.uint8)

class Sculpture:
    def __init__(self): self.parts=[]
    def add(self, mesh, color):
        # Bake a soft studio key into linear vertex colours. glTF COLOR_0 is
        # linear, not sRGB; feeding display bytes directly washed out the old art.
        light=np.array([-.45,.8,.55]); light/=np.linalg.norm(light)
        shade=.60+.40*np.maximum(0,mesh.vertex_normals@light)
        rgb=rgba(color)[:3]/255.
        linear=np.where(rgb<=.04045,rgb/12.92,((rgb+.055)/1.055)**2.4)
        colors=np.ones((len(mesh.vertices),4))
        colors[:,:3]=linear*shade[:,None]
        mesh.visual.vertex_colors=np.round(colors*255).astype(np.uint8)
        self.parts.append(mesh)
        return mesh
    def ball(self,p,s,c,sub=2):
        m=tm.creation.icosphere(subdivisions=sub,radius=1)
        m.apply_scale(s); m.apply_translation(p)
        return self.add(m,c)
    def box(self,p,s,c):
        m=tm.creation.box(s); m.apply_translation(p)
        return self.add(m,c)
    def cylinder(self,p,r,h,c,sections=24):
        m=tm.creation.cylinder(radius=r,height=h,sections=sections)
        m.apply_transform(tm.transformations.rotation_matrix(math.pi/2,[1,0,0]))
        m.apply_translation(p)
        return self.add(m,c)
    def cone(self,p,r,h,c):
        m=tm.creation.cone(radius=r,height=h,sections=16)
        m.apply_transform(tm.transformations.rotation_matrix(-math.pi/2,[1,0,0]))
        m.apply_translation(p)
        return self.add(m,c)
    def tube(self,pts,r,c):
        pts=np.array(pts)
        for a,b in zip(pts[:-1],pts[1:]):
            d=b-a
            m=tm.creation.cylinder(radius=r,height=np.linalg.norm(d),sections=8)
            m.apply_transform(tm.geometry.align_vectors([0,0,1],d))
            m.apply_translation((a+b)/2); self.add(m,c)
        for p in pts[1:-1]: self.ball(p,[r]*3,c,1)
    def ring(self,p,r,t,c):
        m=tm.creation.torus(major_radius=r,minor_radius=t,major_sections=24,minor_sections=6)
        m.apply_transform(tm.transformations.rotation_matrix(math.pi/2,[1,0,0]))
        m.apply_translation(p); return self.add(m,c)
    def ear(self,x,y,z,s,c,inner):
        vertices=np.array([[-.5,0,0],[.5,0,0],[0,1,0],[-.35,0,-.35],[.35,0,-.35],[0,.85,-.22]])*s+[x,y,z]
        m=tm.Trimesh(vertices=vertices,faces=[[0,1,2],[3,5,4],[0,3,4],[0,4,1],[1,4,5],[1,5,2],[2,5,3],[2,3,0]],process=False)
        self.add(m,c)
        vertices=np.array([[-.24,.12,.012],[.24,.12,.012],[0,.72,.012]])*s+[x,y,z]
        self.add(tm.Trimesh(vertices=vertices,faces=[[0,1,2]],process=False),inner)
    def ribbon(self,pts,width,c):
        vertices=[]; faces=[]
        for x,y,z in pts: vertices.extend([(x,y,z-width/2),(x,y,z+width/2)])
        for i in range(len(pts)-1):
            a=2*i; faces.extend([(a,a+1,a+2),(a+1,a+3,a+2)])
        self.add(tm.Trimesh(vertices=vertices,faces=faces,process=False),c)
    def export(self,name,unlit=True):
        mesh=tm.util.concatenate(self.parts)
        # Preserve independent primitive normals across seams; one material/draw per sculpture.
        mesh.vertex_normals
        scene=tm.Scene(mesh)
        path=OUT/f"{name}.glb"
        path.write_bytes(scene.export(file_type="glb"))
        doc=GLTF2().load(str(path))
        for material in doc.materials:
            material.pbrMetallicRoughness.metallicFactor=.04
            material.pbrMetallicRoughness.roughnessFactor=.72
            material.doubleSided=False
            material.alphaMode="OPAQUE"
            if unlit:
                material.extensions={"KHR_materials_unlit":{}}
                doc.extensionsUsed=["KHR_materials_unlit"]
        # Trimesh's color-only export may omit material; add one explicitly.
        if not doc.materials:
            from pygltflib import Material, PbrMetallicRoughness
            mat=Material(pbrMetallicRoughness=PbrMetallicRoughness(baseColorFactor=[1,1,1,1],metallicFactor=0,roughnessFactor=1),doubleSided=False,alphaMode="OPAQUE")
            if unlit:
                mat.extensions={"KHR_materials_unlit":{}}
                doc.extensionsUsed=["KHR_materials_unlit"]
            doc.materials=[mat]
            for m in doc.meshes:
                for primitive in m.primitives: primitive.material=0
        doc.save(str(path))
        return {"file":path.name,"triangles":len(mesh.faces),"vertices":len(mesh.vertices),"bytes":path.stat().st_size}

def animal(kind,team,rank):
    s=Sculpture()
    # Porcelain tokens, gilded lip, visible rank pips and a coloured collar.
    s.cylinder((0,.008,0),.062,.016,team)
    s.cylinder((0,.018,0),.057,.006,IVORY)
    s.ring((0,.021,0),.055,.0024,GOLD)
    for n in range(rank):
        angle=math.pi*.22+n*math.pi*.56/max(1,rank-1)
        s.ball((.051*math.cos(angle),.024,.051*math.sin(angle)),(.0025,.0014,.0025),team,1)
    skins={"rat":"#AB80EF","cat":"#FFBA4D","dog":"#DF7A32","wolf":"#448CE8","leopard":"#FFCF24","tiger":"#FF8B1F","lion":"#FFB52B","elephant":"#47BED7"}
    fur=skins[kind]
    if kind=="elephant":
        s.ball((0,.067,-.009),(.041,.042,.039),fur)
        for x in [-.022,.022]:
            for z in [-.025,.02]: s.ball((x,.039,z),(.015,.022,.016),fur)
        s.ball((0,.108,.018),(.035,.035,.029),fur,2)
        for x in [-.042,.042]:
            s.ball((x,.107,.007),(.022,.032,.010),fur)
            s.ball((x,.107,.016),(.013,.023,.003),PINK)
        pts=[(0,.109,.041),(0,.09,.048),(0,.07,.052),(0,.054,.062),(0,.057,.077)]
        for n,(a,b) in enumerate(zip(pts[:-1],pts[1:])): s.tube([a,b],.011-n*.0015,fur)
        for x in [-.02,.02]:
            s.tube([(x,.089,.038),(x,.079,.051),(x,.084,.062)],.004,IVORY)
        eye_y,eye_z,eye_x=.117,.039,.019
    else:
        bodyScale=(.031,.039,.026) if kind!="rat" else (.025,.031,.024)
        s.ball((0,.057,-.011),bodyScale,fur)
        s.ball((0,.055,.012),(.023,.030,.012),IVORY)
        for x in [-.023,.023]:
            s.ball((x,.030,.019),(.017,.01,.022),fur)
            s.ball((x*.9,.054,.011),(.009,.024,.012),fur)
        headY=.103 if kind!="rat" else .09
        headR=.033 if kind!="rat" else .028
        s.ball((0,headY,.009),(headR,headR*.94,headR*.88),fur,2)
        if kind=="lion":
            for a in np.linspace(0,2*math.pi,15,endpoint=False):
                s.ball((math.cos(a)*.035,headY+math.sin(a)*.036,-.004),(.016,.018,.018),"#CF5021")
            s.ball((0,headY,.020),(.031,.029,.020),fur,2)
        if kind in ("cat","wolf"):
            for x in [-.023,.023]:
                s.ear(x,headY+.019,.013,.030 if kind=="wolf" else .027,fur,PINK)
        elif kind=="dog":
            for x in [-.031,.031]: s.ball((x,headY-.002,.004),(.012,.030,.013),"#77563D")
            s.ball((-.013,headY+.003,.032),(.015,.019,.005),"#8B6241")
        else:
            for x in [-.027,.027]:
                scale=(.019,.021,.008) if kind=="rat" else (.012,.013,.008)
                s.ball((x,headY+.025,.002),scale,fur)
                s.ball((x,headY+.026,.009),np.array(scale)*[.64,.65,.3],PINK)
        if kind in ("wolf","dog","rat"):
            s.ball((0,headY-.009,.038),(.020,.014,.024 if kind=="wolf" else .019),IVORY)
            nose_z=.059 if kind=="wolf" else .054
        else:
            for x in [-.01,.01]: s.ball((x,headY-.009,.035),(.014,.011,.009),IVORY)
            nose_z=.044
        s.ball((0,headY-.003,nose_z),(.006,.004,.004),PINK if kind in ("cat","rat") else DARK)
        if kind=="rat":
            for x in [-.0035,.0035]: s.box((x,headY-.019,.05),(.006,.009,.003),IVORY)
        tail=[(0,.044,-.035),(.020,.036,-.051),(.043,.044,-.052),(.045,.061,-.035)]
        s.tube(tail,.004 if kind=="rat" else .006,PINK if kind=="rat" else fur)
        if kind=="lion": s.ball(tail[-1],(.009,.012,.008),"#CF5021")
        eye_y,eye_z,eye_x=headY+.003,.034,.014
        if kind=="tiger":
            for side in [-1,1]:
                for y in [.063,.076]:
                    s.ball((side*.027,y,.007),(.008,.003,.019),"#694931",1)
                for y in [headY+.014,headY-.014]:
                    s.ball((side*.025,y,.028),(.009,.0027,.005),"#694931",1)
            for x in [-.009,0,.009]: s.ball((x,headY+.022,.031),(.0025,.009,.003),"#694931",1)
        if kind=="leopard":
            for a in np.linspace(0,math.pi*2,12,endpoint=False):
                x=.030*math.cos(a); y=headY+.026*math.sin(a)
                s.ball((x,y,.026),(.0038,.0038,.0023),"#705333",1)
            for x in [-.026,.026]:
                for y in [.045,.060,.075]: s.ball((x,y,.006),(.003,.004,.008),"#705333",1)
        # Collar communicates faction even when the base is hidden by a hand.
        s.ring((0,.079 if kind!="rat" else .066,.001),.022,.003,team)
        s.ball((0,.077 if kind!="rat" else .065,.025),(.005,.006,.002),GOLD)
    for x in [-eye_x,eye_x]:
        s.ball((x,eye_y,eye_z),(.0052,.006,.0038),DARK)
        s.ball((x-.0015,eye_y+.002,eye_z+.0035),(.0017,.0018,.0009),"#FFFFFF",1)
    return s

def board():
    s=Sculpture()
    s.box((0,-.045,0),(1.25,.085,1.57),"#147D67")
    s.box((0,-.006,0),(1.20,.02,1.52),GOLD)
    s.box((0,.005,0),(1.17,.016,1.49),"#37A36B")
    for y in range(9):
        for x in range(7):
            px=(x-3)*.16; pz=(y-4)*.16
            water=3<=y<=5 and (1<=x<=2 or 4<=x<=5)
            trap=(x,y) in [(2,0),(4,0),(3,1),(2,8),(4,8),(3,7)]
            den=(x,y) in [(3,0),(3,8)]
            c=("#169CE8" if (x+y)%2 else "#24BDEB") if water else (IVORY if (x+y)%2 else "#B4D978")
            if trap: c="#FFCD4B"
            if den: c=RED if y==8 else BLUE
            s.box((px,.012 if water else .019,pz),(.155,.020,.155),c)
            if water:
                for i in [-1,0,1]:
                    pts=[(px-.042+j*.014,.024,pz+i*.035+math.sin(j*1.2)*.004) for j in range(7)]
                    s.ribbon(pts,.003,"#AAFFFF")
            if trap:
                s.ring((px,.032,pz),.049,.003,"#AD4C18")
                for a in range(4):
                    ang=math.pi/4+math.pi*a/2
                    s.cone((px+.035*math.cos(ang),.032,pz+.035*math.sin(ang)),.008,.009,"#AD4C18")
            if den:
                s.ring((px,.032,pz),.052,.004,GOLD)
                s.ball((px,.034,pz+.009),(.020,.003,.016),IVORY)
                for xx,zz in [(-.025,-.008),(-.009,-.022),(.009,-.022),(.025,-.008)]:
                    s.ball((px+xx,.034,pz+zz),(.008,.003,.010),IVORY)
    # Four stout brass-capped table legs.
    for x in [-.51,.51]:
        for z in [-.66,.66]:
            s.cylinder((x,-.39,z),.035,.65,"#168566")
            s.cylinder((x,-.71,z),.037,.025,GOLD)
    for x in [-.617,.617]:
        s.box((x,-.025,0),(.009,.02,1.56),GOLD)
    return s

def tree(style, height, width):
    """Four complementary silhouettes, scaled in metres with all roots on the ground."""
    s=Sculpture()
    trunk=height*.56 if style!="pine" else height*.83
    s.cylinder((0,trunk/2,0),.065+height*.012,trunk,"#986638",12)
    if style=="pine":
        for j in range(3):
            base=height*(.22+j*.19)
            s.cone((0,base,0),width*(1-j*.24),height*(.52-j*.075),
                   ["#247E62","#39985E","#6FB96C"][j])
    elif style=="round":
        for j,(x,y,z) in enumerate([(-.20,.64,.02),(.22,.74,-.09),(0,.85,.03)]):
            s.ball((x*width,y*height,z), (width*.75,height*.23,width*.70),
                   ["#2F9463","#62B55C","#8FCC68"][j],2)
    elif style=="slender":
        for j in range(3):
            s.ball(((j-1)*width*.10,height*(.57+j*.13),0),
                   (width*(.60-j*.09),height*.26,width*.53),
                   ["#278564","#53A566","#97C46C"][j],2)
    else:  # spreading, branching broadleaf canopy
        for x,z,y in [(-.40,.12,.65),(.36,-.14,.70),(0,.02,.89)]:
            s.tube([[0,height*.42,0],[x*width,height*y,z*width]],.042,"#986638")
            s.ball((x*width,height*y,z*width),(width*.64,height*.19,width*.59),
                   "#80BD69" if y>.8 else "#459D62",2)
    return s

def environment(seed=19):
    s=Sculpture()
    s.cylinder((0,-.015,0),5,.03,"#279976",64)
    s.ring((0,.009,-1),2.05,.008,"#B2DF73")
    s.ring((0,.007,-1),2.12,.006,"#5BBD88")
    rng=np.random.default_rng(seed)
    styles=list(rng.permutation(["pine","round","slender","spreading"]*6))
    inventory=[]
    for i,style in enumerate(styles):
        angle=(i+.15+float(rng.uniform(-.13,.13)))/len(styles)*math.tau
        r=float(rng.uniform(3.35,3.95))
        x,z=r*math.cos(angle),r*math.sin(angle)-.4
        h=float(rng.uniform(1.65,3.65))
        width=float(rng.uniform(.43,.69))
        specimen=tree(style,h,width)
        for part in specimen.parts: part.apply_translation([x,0,z])
        s.parts.extend(specimen.parts)
        inventory.append({"style":str(style),"height":round(h,3),"width":round(width,3),
                          "x":round(x,3),"z":round(z,3)})
        for j in range(2):
            s.ball((x+.2*j,.07,z+.3),(.19,.10,.22),"#88B675",1)
    s.tree_inventory=inventory
    return s

def sky():
    s=Sculpture()
    mesh=tm.creation.icosphere(subdivisions=3,radius=35)
    cols=[]
    for x,y,z in mesh.vertices:
        t=np.clip((y/35+.2)/1.2,0,1)
        low=np.array([143,231,199]); high=np.array([23,119,200])
        rgb=(low*(1-t)+high*t)/255
        linear=np.where(rgb<=.04045,rgb/12.92,((rgb+.055)/1.055)**2.4)
        cols.append(list(np.round(linear*255).astype(int))+[255])
    mesh.visual.vertex_colors=cols
    mesh.invert()
    s.parts.append(mesh)
    return s

def ibl():
    # Linear HDR equirectangular sky. Native SDK generates the filtered cubemap.
    w,h=256,128
    data=bytearray(f"#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y {h} +X {w}\n".encode())
    for y in range(h):
        row=[]
        for x in range(w):
            up=max(0,math.cos(math.pi*y/(h-1)))
            base=np.array([.30,.38,.32])+up*np.array([.65,.62,.55])
            sun=math.exp(-((x/w-.28)**2+(y/h-.24)**2)/.012)*2
            rgb=base+sun*np.array([1,.9,.7])
            _,exponent=math.frexp(float(rgb.max()))
            row.append([*np.clip(rgb*2**(8-exponent),0,255).astype(np.uint8),exponent+128])
        channels=np.array(row,dtype=np.uint8).T
        data.extend([2,2,w>>8,w&255])
        for channel in channels:
            for start in range(0,w,128):
                block=channel[start:start+128]
                # Literal packets must be <=128 bytes.
                data.append(len(block));data.extend(block.tobytes())
    (OUT/"forest.hdr").write_bytes(data)

if __name__=="__main__":
    manifest=[]
    for rank,kind in enumerate(["rat","cat","dog","wolf","leopard","tiger","lion","elephant"],1):
        for team,color in [("red",RED),("blue",BLUE)]:
            manifest.append(animal(kind,color,rank).export(f"{team}_{kind}"))
    manifest.append(board().export("board"))
    manifest.append(environment().export("forest"))
    manifest.append(sky().export("sky",True))
    ibl()
    (ROOT/"art/asset_manifest.json").write_text(json.dumps(manifest,indent=2))
    print(json.dumps(manifest,indent=2))
