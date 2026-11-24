"""Create 1.9 APK, source, music audition and scene preview deliverables."""
from pathlib import Path
import shutil
import zipfile
import hashlib
import json

root=Path(__file__).resolve().parents[1]
out=root.parent
prefix="林间斗兽棋-1.9"
mapping={
    "体验版.apk":"app/build/outputs/apk/debug/app-debug.apk",
    "混合林预览.png":"art/mixed-forest-preview.png",
    "树木样式预览.png":"art/mixed-tree-lineup.png",
    "背景音乐-林间散步.wav":"app/src/main/assets/woodland_stroll.wav",
    "修改与验证说明.md":"docs/验证记录.md",
}
artifacts=[]
for suffix,relative in mapping.items():
    path=out/f"{prefix}-{suffix}"
    shutil.copy2(root/relative,path)
    artifacts.append(path)
source=out/f"{prefix}-源码.zip"
blocked={".git",".gradle",".kotlin",".art-venv","__pycache__","build",".private",".signing",".idea"}
with zipfile.ZipFile(source,"w",zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob("*")):
        rel=path.relative_to(root)
        if not path.is_file() or any(part in blocked for part in rel.parts):continue
        if path.name in {"local.properties",".DS_Store"} or path.suffix in {".jks",".keystore",".log",".pyc"}:continue
        archive.write(path,str(Path("JungleChess")/rel))
artifacts.append(source)
evidence=out/f"{prefix}-验证记录.zip"
names=[
    "jungle19-final-build.log","jungle19-music.log","jungle19-forest.log",
    "jungle19-device-tests.log","jungle19-exit-test.log","jungle19-app-info.json",
    "jungle19-exit-click.json","jungle19-result-ui.json","jungle19-runtime.log",
    "jungle19-final-ui.json","jungle19-swan.png","jungle19-result.png",
]
with zipfile.ZipFile(evidence,"w",zipfile.ZIP_DEFLATED) as archive:
    for name in names:
        path=Path("/tmp")/name
        if path.is_file():
            if path.suffix==".json":json.loads(path.read_text())
            archive.write(path,name)
    for rel in ["docs/验证记录.md","art/mixed_forest_manifest.json","art/woodland_music_manifest.json"]:
        archive.write(root/rel,Path(rel).name)
    for path in (root/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        archive.write(path,"host-tests/"+path.name)
artifacts.append(evidence)
with zipfile.ZipFile(source) as archive:
    names=archive.namelist()
    assert "JungleChess/app/src/main/assets/woodland_stroll.wav" in names
    assert not any("/build/" in n or "local.properties" in n or ".art-venv" in n for n in names)
lines=[]
for path in artifacts:
    lines.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}")
    print(path.name,path.stat().st_size)
(out/f"{prefix}-SHA256.txt").write_text("\n".join(lines)+"\n")
