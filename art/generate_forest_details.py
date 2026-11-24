"""Original low-cost woodland dioramas; deterministic, metres, Y-up.
No collisions, textures, transparency, runtime lighting or external models.
"""
import math
import json
import numpy as np
import trimesh as tm
from generate_assets import Sculpture, ROOT
from render_preview import render, load
from PIL import ImageDraw

rng = np.random.default_rng(2411)

def leaf(s, base, direction, width, color):
    base, direction = np.array(base), np.array(direction)
    mesh = tm.creation.icosphere(subdivisions=0)
    mesh.apply_scale([width, np.linalg.norm(direction) / 2, width * .22])
    mesh.apply_transform(tm.geometry.align_vectors([0, 1, 0], direction))
    mesh.apply_translation(base + direction / 2)
    s.add(mesh, color)

def grass(s, x, z, scale=1):
    for i in range(5):
        a = i * 2.399
        h = float(rng.uniform(.10, .22)) * scale
        leaf(s, [x, .005, z], [math.cos(a)*h*.45, h, math.sin(a)*h*.45],
             .022*scale, ["#549E42", "#8DD15B", "#C2DF73"][i % 3])

def flower(s, x, z, color, h):
    s.tube([[x, .006, z], [x+.014, h*.65, z], [x, h, z]], .007, "#438644")
    leaf(s, [x, h*.32, z], [.07, .04, .012], .025, "#8AC45A")
    for i in range(5):
        a = i * math.tau / 5
        petal = s.ball([x+math.cos(a)*.036, h+.005, z+math.sin(a)*.036],
                      [.031, .012, .021], color, 0)
        petal.apply_transform(tm.transformations.rotation_matrix(-a, [0, 1, 0], point=[x, h, z]))
    s.ball([x, h+.016, z], [.018, .011, .018], "#F6CC5E", 1)

def rabbit():
    s = Sculpture()
    cream, pale = "#E5D6B8", "#FFF3D5"
    s.ball([0,.15,0], [.13,.16,.16], cream)
    s.ball([0,.19,.134], [.088,.125,.039], pale)
    for x in [-.073,.073]:
        s.ball([x,.035,.096], [.057,.037,.08], cream)
        s.ball([x,.152,.116], [.035,.058,.03], pale)
    s.ball([0,.327,.046], [.101,.096,.09], pale)
    for x in [-.048,.048]:
        ear=s.ball([x,.473,.025], [.028,.12,.033], cream)
        ear.apply_transform(tm.transformations.rotation_matrix(-x*2, [0,0,1], point=[x,.39,.025]))
        s.ball([x,.478,.054], [.014,.084,.01], "#DCA8A0", 1)
        s.ball([x,.349,.12], [.012,.014,.01], "#344344", 1)
        s.ball([x-.003,.354,.128], [.004,.004,.003], "#FFFFFF", 1)
        s.ball([x*1.22,.32,.12], [.017,.01,.006], "#E4B5A0", 1)
    s.ball([0,.316,.136], [.012,.009,.008], "#BE8378", 1)
    s.ball([0,.151,-.16], [.057,.055,.055], pale)
    return s

def squirrel():
    s = Sculpture()
    fur, belly = "#B96A35", "#F5CC8D"
    s.ball([0,.17,0], [.10,.145,.10], fur)
    s.ball([0,.171,.082], [.068,.10,.026], belly)
    for x in [-.058,.058]:
        s.ball([x,.028,.062], [.045,.028,.061], fur)
        s.ball([x,.192,.095], [.025,.053,.029], fur)
    s.ball([0,.31,.023], [.091,.085,.082], fur)
    s.ball([0,.284,.094], [.055,.033,.029], belly)
    for x in [-.061,.061]:
        s.ear(x,.365,.02,.063,fur,"#E8AB6A")
        s.ball([x*.80,.32,.094], [.011,.013,.009], "#303E3B", 1)
        s.ball([x*.80-.003,.325,.101], [.0035]*3, "#FFFFFF", 1)
    s.ball([0,.292,.123], [.012,.009,.007], "#49342C", 1)
    # A broad curled plume with a taper, visible above the body silhouette.
    for p, size in [
        ([0,.09,-.112],[.065,.074,.074]),
        ([0,.17,-.19],[.082,.107,.084]),
        ([0,.31,-.237],[.104,.126,.095]),
        ([0,.443,-.20],[.105,.087,.083]),
        ([0,.469,-.114],[.078,.054,.063]),
        ([0,.435,-.078],[.046,.046,.044]),
    ]:
        s.ball(p,size,"#D58845",2)
    s.ball([0,.167,.129],[.034,.04,.03],"#965637",1)
    s.ball([0,.195,.129],[.041,.015,.035],"#63482B",1)
    return s

def instance(scene, sculpture, x, z, yaw, scale=1):
    mesh = tm.util.concatenate(sculpture.parts)
    mesh.apply_scale(scale)
    mesh.apply_transform(tm.transformations.rotation_matrix(yaw, [0,1,0]))
    mesh.apply_translation([x,0,z])
    scene.parts.append(mesh)

def details():
    s = Sculpture()
    for i in range(16):
        a = i * math.tau / 16
        radius = float(rng.uniform(2.55, 3.1))
        x,z = radius*math.cos(a), radius*math.sin(a)-.4
        # Moss cushions tie each group to the existing green forest floor.
        s.ball([x,.017,z],[.30,.027,.24],"#69AA54",1)
        for j in range(2 + i % 2):
            px,pz=x+float(rng.uniform(-.19,.19)),z+float(rng.uniform(-.15,.15))
            r=float(rng.uniform(.07,.17))
            s.ball([px,r*.45,pz],[r,r*.64,r*.78],["#899F95","#B4BCAF","#758F88"][j%3],1)
            s.ball([px-.015,r*.94,pz],[r*.68,.017,r*.48],"#8AAF5E",1)
        for j in range(5):
            grass(s,x+float(rng.uniform(-.34,.34)),z+float(rng.uniform(-.30,.30)))
        for j in range(3):
            flower(s,x+float(rng.uniform(-.27,.27)),z+float(rng.uniform(-.22,.22)),
                   ["#FFF2CA","#DAAEDB","#EEA887"][i%3],float(rng.uniform(.15,.23)))
    for x,z,yaw,size in [(-1.48,-1.65,.55,1), (2.15,.75,-.8,.82)]:
        instance(s,rabbit(),x,z,yaw,size)
        grass(s,x-.20,z+.05,.85)
        flower(s,x+.22,z-.04,"#FFF2CA",.17)
    for x,z,yaw,size in [(1.58,-2.0,-.65,1.1),(-2.12,1.0,.8,.9)]:
        instance(s,squirrel(),x,z,yaw,size)
        grass(s,x+.20,z+.05)
    return s

if __name__ == "__main__":
    manifest = details().export("forest_details")
    (ROOT/"art/forest_details_manifest.json").write_text(json.dumps(manifest, indent=2)+"\n")
    print(json.dumps(manifest))
    # Close view of the actual exported geometry, explicitly labelled offline.
    image = render(load("forest_details"), (1600,1100), [4,6,7], [0,.08,-.4], 160)
    ImageDraw.Draw(image).text((30,30), "FOREST DETAILS / GLB OFFLINE PREVIEW", fill="#294C43")
    image.save(ROOT/"art/forest-details-preview.png")
    critters= Sculpture()
    instance(critters,rabbit(),-.24,0,.15)
    instance(critters,squirrel(),.24,0,-.2)
    preview=render(tm.util.concatenate(critters.parts),(1200,900),[1,1.0,3],[0,.26,0],1250)
    ImageDraw.Draw(preview).text((30,30),"ORIGINAL WOODLAND CREATURES / OFFLINE PREVIEW",fill="#294C43")
    preview.save(ROOT/"art/woodland-creatures-preview.png")
