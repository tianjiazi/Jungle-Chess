"""Package version 1.10 with reproducible calls and actual verification evidence."""
from pathlib import Path
import hashlib
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
out = root.parent
prefix = "林间斗兽棋-1.10"
files = []
for suffix, relative in {
    "体验版.apk":"app/build/outputs/apk/debug/app-debug.apk",
    "八种动物叫声试听.wav":"art/animal-calls-audition.wav",
    "修改与验证说明.md":"docs/验证记录.md",
}.items():
    target = out / f"{prefix}-{suffix}"
    shutil.copy2(root/relative,target)
    files.append(target)
blocked = {".git",".gradle",".kotlin",".art-venv","__pycache__","build",".private",".signing",".idea"}
source = out / f"{prefix}-源码.zip"
with zipfile.ZipFile(source,"w",zipfile.ZIP_DEFLATED) as z:
    for path in sorted(root.rglob("*")):
        rel = path.relative_to(root)
        if not path.is_file() or any(p in blocked for p in rel.parts): continue
        if path.name in {"local.properties",".DS_Store"} or path.suffix in {".jks",".keystore",".log",".pyc"}: continue
        z.write(path,str(Path("JungleChess")/rel))
files.append(source)
with zipfile.ZipFile(source) as z:
    names = z.namelist()
    assert sum(n.startswith("JungleChess/app/src/main/assets/calls/") for n in names)==8
    assert not any("/build/" in n or "local.properties" in n for n in names)
evidence = out / f"{prefix}-验证记录.zip"
with zipfile.ZipFile(evidence,"w",zipfile.ZIP_DEFLATED) as z:
    for path in sorted(Path("/tmp").glob("jungle110-*")):
        if path.is_file() and path.suffix in {".log",".json"}: z.write(path,path.name)
    for relative in ["docs/验证记录.md","art/animal_calls_manifest.json"]:
        z.write(root/relative,Path(relative).name)
    for path in (root/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        z.write(path,"host-tests/"+path.name)
files.append(evidence)
(out/f"{prefix}-SHA256.txt").write_text("".join(
    f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in files))
for path in files: print(path,path.stat().st_size)
