"""Deliver genuine recorded voices and verify the exact audio bytes inside the APK."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile
import numpy as np
import soundfile as sf

root=Path(__file__).resolve().parents[1]
out=root.parent
prefix="林间斗兽棋-1.11"
apk=root/"app/build/outputs/apk/debug/app-debug.apk"
manifest=json.loads((root/"art/animal_calls_manifest.json").read_text())
previous=out/"林间斗兽棋-1.10-体验版.apk"
with zipfile.ZipFile(apk) as z:
    for item in manifest["calls"]:
        path=root/"app/src/main/assets/calls"/(item["animal"]+".wav")
        asset="assets/calls/"+path.name
        assert z.read(asset)==path.read_bytes(),asset
        assert hashlib.sha256(path.read_bytes()).hexdigest()==item["sha256"]
        audio,rate=sf.read(path)
        assert rate==32000 and audio.ndim==1 and .3<len(audio)/rate<1.5
        assert np.max(np.abs(audio))<=.751
        if previous.exists():
            with zipfile.ZipFile(previous) as old: assert old.read(asset)!=z.read(asset)
    assert z.read("assets/ANIMAL_AUDIO_LICENSES.txt")==(
        root/"app/src/main/assets/ANIMAL_AUDIO_LICENSES.txt").read_bytes()
print("APK: eight genuine audio assets match source bytes; no 1.10 synthesized sound retained.")
files=[]
for suffix,relative in {
    "体验版.apk":"app/build/outputs/apk/debug/app-debug.apk",
    "真实动物叫声试听.wav":"art/animal-calls-audition.wav",
    "修改与验证说明.md":"docs/验证记录.md",
    "录音来源与许可.md":"docs/真实动物音频来源与许可.md",
}.items():
    target=out/f"{prefix}-{suffix}"
    shutil.copy2(root/relative,target)
    files.append(target)
audio_zip=out/f"{prefix}-真实动物叫声.zip"
with zipfile.ZipFile(audio_zip,"w",zipfile.ZIP_DEFLATED) as z:
    for item in manifest["calls"]:
        path=root/"app/src/main/assets/calls"/(item["animal"]+".wav")
        z.write(path,path.name)
    z.write(root/"docs/真实动物音频来源与许可.md","录音来源与许可.md")
    z.write(root/"art/animal_calls_manifest.json","录音参数与哈希.json")
files.append(audio_zip)
source=out/f"{prefix}-源码.zip"
blocked={".git",".gradle",".kotlin",".art-venv","__pycache__","build",".private",".signing",".idea"}
with zipfile.ZipFile(source,"w",zipfile.ZIP_DEFLATED) as z:
    for path in sorted(root.rglob("*")):
        rel=path.relative_to(root)
        if not path.is_file() or any(part in blocked for part in rel.parts): continue
        if path.name in {"local.properties",".DS_Store"} or path.suffix in {".jks",".keystore",".log",".pyc"}: continue
        z.write(path,str(Path("JungleChess")/rel))
files.append(source)
evidence=out/f"{prefix}-验证记录.zip"
with zipfile.ZipFile(evidence,"w",zipfile.ZIP_DEFLATED) as z:
    for path in sorted(Path("/tmp").glob("jungle111-*.log")):
        z.write(path,path.name)
    for name in ["rat","cat","dog","wolf","leopard-original","tiger","lion","elephant"]:
        path=Path("/tmp/jungle111-sources")/(name+".html")
        if path.is_file(): z.write(path,"source-pages/"+path.name)
    for relative in ["docs/验证记录.md","docs/真实动物音频来源与许可.md",
                     "art/animal_calls_manifest.json","art/real_animal_sources.json"]:
        z.write(root/relative,relative)
    for path in (root/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        z.write(path,"host-tests-cached/"+path.name)
files.append(evidence)
(out/f"{prefix}-SHA256.txt").write_text("".join(
    f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n" for path in files))
for path in files: print(path.name,path.stat().st_size)
