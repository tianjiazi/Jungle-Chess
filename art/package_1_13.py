"""Package sky-only immersion build, preserving genuine animal audio licenses."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
out = root.parent
prefix = "林间斗兽棋-1.13"
apk = root / "app/build/outputs/apk/debug/app-debug.apk"
manifest = json.loads((root / "art/animal_calls_manifest.json").read_text())
with zipfile.ZipFile(apk) as archive:
    for item in manifest["calls"]:
        assert hashlib.sha256(archive.read("assets/calls/" + item["animal"] + ".wav")).hexdigest() == item["sha256"]
    assert archive.read("assets/ANIMAL_AUDIO_LICENSES.txt")

files = []
for suffix, path in {
    "体验版.apk": apk,
    "修改与验证说明.md": root / "docs/验证记录.md",
    "录音来源与许可.md": root / "docs/真实动物音频来源与许可.md",
}.items():
    target = out / f"{prefix}-{suffix}"
    shutil.copy2(path, target)
    files.append(target)

source = out / f"{prefix}-源码.zip"
blocked = {".git", ".gradle", ".kotlin", ".art-venv", "__pycache__", "build", ".private", ".signing", ".idea"}
with zipfile.ZipFile(source, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if not path.is_file() or any(part in blocked for part in relative.parts):
            continue
        if (path.name in {"local.properties", ".DS_Store"} or path.name.startswith(".env")
                or path.suffix in {".jks", ".keystore", ".key", ".pem", ".log", ".pyc"}):
            continue
        archive.write(path, str(Path("JungleChess") / relative))
files.append(source)
(out / f"{prefix}-SHA256.txt").write_text("".join(
    f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n" for path in files))
print("Eight genuine recording hashes and licenses verified.")
for path in files:
    print(path, path.stat().st_size)
