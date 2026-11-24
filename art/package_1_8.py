"""Package the verified 1.8 workspace build without caches or local configuration."""
from pathlib import Path
import shutil
import zipfile
import hashlib

root = Path(__file__).resolve().parents[1]
out = root.parent
prefix = "林间斗兽棋-1.8"
apk = out / f"{prefix}-体验版.apk"
shutil.copy2(root/"app/build/outputs/apk/debug/app-debug.apk", apk)
shutil.copy2(root/"docs/验证记录.md", out/f"{prefix}-修改与验证说明.md")
shutil.copy2(root/"art/forest-details-preview.png", out/f"{prefix}-林地装饰预览.png")
shutil.copy2(root/"art/woodland-creatures-preview.png", out/f"{prefix}-兔子松鼠预览.png")

source = out/f"{prefix}-源码.zip"
blocked = {".git", ".gradle", ".kotlin", ".art-venv", "__pycache__", "build",
           ".private", ".signing", ".idea"}
with zipfile.ZipFile(source,"w",zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob("*")):
        rel = path.relative_to(root)
        if not path.is_file() or any(part in blocked for part in rel.parts):
            continue
        if path.name in {"local.properties",".DS_Store"} or path.suffix in {".jks",".keystore",".log",".pyc"}:
            continue
        archive.write(path, str(Path("JungleChess")/rel))

evidence = out/f"{prefix}-验证记录.zip"
names = [
    "jungle18-build.log", "jungle18-final-build.log", "jungle18-lifecycle-tests.log",
    "jungle18-exit-test.log", "jungle18-runtime-evidence.log", "jungle18-app-info.json",
    "jungle18-result-ui.json", "jungle18-exit-click.json", "jungle18-loaded-ui.json",
    "jungle18-red-click.json", "jungle18-red-drop.json", "jungle18-after-move.json",
    "jungle18-blue-rejected.json", "jungle18-red-grab.json", "jungle18-red-move.json",
    "jungle18-red-release.json", "jungle18-after-drag.json", "jungle18-final-ui.json",
    "jungle18-reset-dialog.json", "jungle18-reset-confirm.json",
    "jungle18-camera-result.json", "jungle18-result.png", "jungle18-swan.png",
    "jungle18-final-swan.png",
]
with zipfile.ZipFile(evidence,"w",zipfile.ZIP_DEFLATED) as archive:
    for name in names:
        path=Path("/tmp")/name
        if path.is_file(): archive.write(path,name)
    archive.write(root/"docs/验证记录.md","验证说明.md")
    archive.write(root/"art/forest_details_manifest.json","forest_details_manifest.json")
    for path in sorted((root/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")):
        archive.write(path, "host-tests/"+path.name)

artifacts = [apk, source, evidence, out/f"{prefix}-修改与验证说明.md",
             out/f"{prefix}-林地装饰预览.png", out/f"{prefix}-兔子松鼠预览.png"]
lines=[]
for path in artifacts:
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    lines.append(f"{digest}  {path.name}")
    print(path.name, path.stat().st_size)
(out/f"{prefix}-SHA256.txt").write_text("\n".join(lines)+"\n")
with zipfile.ZipFile(source) as archive:
    entries=archive.namelist()
    assert any(name.endswith("forest_details.glb") for name in entries)
    assert not any("/build/" in name or "local.properties" in name or ".art-venv" in name for name in entries)
print("Verified source archive exclusions and forest decoration asset")
