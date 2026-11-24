"""Extract short genuine animal recordings; never synthesize, pitch-shift or substitute species."""
from pathlib import Path
import hashlib
import json
import math
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/calls"
RATE = 32000
CLIPS = {
    "rat": (7.75, 8.17),
    "cat": (0, None),
    "dog": (0, .48),
    "wolf": (0, 1.35),
    "leopard": (0, None),
    "tiger": (0, None),
    "lion": (.42, 1.60),
    "elephant": (0, None),
}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def build():
    sources = json.loads((ROOT/"art/real_animal_sources.json").read_text())
    manifest = {
        "style": "Genuine animal recordings, species and provenance documented by original uploaders",
        "sample_rate": RATE, "channels": 1,
        "processing": "Crop, mono downmix, sample-rate conversion, constant gain and short endpoint fades only. No pitch shifting, time stretching, synthesis or added effects.",
        "calls": [],
    }
    credits = [
        "# 林间斗兽棋 1.11 真实动物录音来源与许可",
        "",
        "以下为真实动物录音的节选。改动仅为裁剪、单声道转换、重采样、统一音量与切口淡化。",
        "无变调、变速、合成或物种替代。Freesound采用公开分享的高品质MP3预览。",
        "猫与狗的处理后WAV继续以CC BY-SA 3.0发布；豹声以CC BY 3.0发布。",
        "这些音频资源的许可不用于变更其他代码或资源的许可。原作者不代表认可本游戏。",
        "",
    ]
    audition = []
    OUT.mkdir(parents=True, exist_ok=True)
    for animal, (start, end) in CLIPS.items():
        source = sources[animal]
        path = ROOT/"art/recordings"/(animal+Path(source["url"]).suffix)
        audio, rate = sf.read(path, always_2d=True)
        audio = audio.mean(axis=1)
        last = len(audio) if end is None else round(end*rate)
        audio = audio[round(start*rate):last]
        assert len(audio) > 0, animal
        divisor = math.gcd(RATE,rate)
        audio = resample_poly(audio,RATE//divisor,rate//divisor)
        # Short fades avoid cutting clicks while retaining the actual vocal timbre.
        attack, release = round(.005*RATE), round(.035*RATE)
        audio[:attack] *= np.linspace(0,1,attack)
        audio[-release:] *= np.linspace(1,0,release)
        peak = float(np.max(np.abs(audio)))
        rms = float(np.sqrt(np.mean(audio**2)))
        gain = min(.75/max(peak,1e-9), 10**(-18/20)/max(rms,1e-9))
        audio *= gain
        output = OUT/f"{animal}.wav"
        sf.write(output,audio,RATE,subtype="PCM_16")
        item = dict(source,animal=animal,seconds=len(audio)/RATE,
                    crop_start=start,crop_end=last/rate,source_sha256=digest(path),
                    sha256=digest(output),peak=float(np.max(np.abs(audio))),
                    rms_dbfs=float(20*np.log10(np.sqrt(np.mean(audio**2)))),
                    gain=gain)
        manifest["calls"].append(item)
        credits += [
            f"## {animal} — {source['title']}",
            f"- 作者：{source['author']}",
            f"- 原始来源：{source['page']}",
            f"- 许可：{source['license']} — {source['license_url']}",
            f"- 原始录音说明：{source['provenance']}",
            f"- 使用范围：{start:.3f}–{last/rate:.3f}秒；处理后{len(audio)/RATE:.3f}秒。",
            "",
        ]
        audition += [audio,np.zeros(round(.55*RATE))]
    sf.write(ROOT/"art/animal-calls-audition.wav",np.concatenate(audition),RATE,subtype="PCM_16")
    (ROOT/"art/animal_calls_manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n")
    text="\n".join(credits)+"\n"
    (ROOT/"docs/真实动物音频来源与许可.md").write_text(text)
    (ROOT/"app/src/main/assets/ANIMAL_AUDIO_LICENSES.txt").write_text(text)
    print(json.dumps(manifest,ensure_ascii=False,indent=2))

if __name__ == "__main__":
    build()
