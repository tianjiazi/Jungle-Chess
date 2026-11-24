"""Package the installed audio-control build and verify unchanged genuine recordings."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

root=Path(__file__).resolve().parents[1]
out=root.parent
prefix="林间斗兽棋-1.12"
manifest=json.loads((root/"art/animal_calls_manifest.json").read_text())
apk=root/"app/build/outputs/apk/debug/app-debug.apk"
with zipfile.ZipFile(apk) as z:
    for item in manifest["calls"]:
        content=z.read("assets/calls/"+item["animal"]+".wav")
        assert hashlib.sha256(content).hexdigest()==item["sha256"]
    assert z.read("assets/ANIMAL_AUDIO_LICENSES.txt")
files=[]
for suffix,path in {
    "体验版.apk":apk,
    "swan开关面板.png":Path("/tmp/jungle112-panel.png"),
    "修改与验证说明.md":root/"docs/验证记录.md",
    "录音来源与许可.md":root/"docs/真实动物音频来源与许可.md",
}.items():
    target=out/f"{prefix}-{suffix}"
    shutil.copy2(path,target)
    files.append(target)
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
    for path in sorted(Path("/tmp").glob("jungle112-*")):
        if path.is_file() and path.suffix in {".log",".json",".png"}: z.write(path,path.name)
    for relative in ["docs/验证记录.md","docs/真实动物音频来源与许可.md","art/animal_calls_manifest.json"]:
        z.write(root/relative,relative)
    for path in (root/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        z.write(path,"host-tests/"+path.name)
files.append(evidence)
(out/f"{prefix}-SHA256.txt").write_text("".join(
    f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n" for path in files))
print("APK audio hashes match all eight genuine recordings.")
for path in files: print(path.name,path.stat().st_size)
