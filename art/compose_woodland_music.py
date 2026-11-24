"""Original 'Woodland Stroll': 32 bars in C major, 112 BPM, seamless stereo PCM.
Marimba melody, warm plucked chords, rounded bass, brushed shaker and soft kick.
No sampled recordings or third-party compositions. Reproducible with numpy.
"""
from pathlib import Path
import json
import wave
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
RATE=32000
BPM=112
BEAT=60/BPM
BEATS=128
SIZE=round(BEATS*BEAT*RATE)
mix=np.zeros((SIZE,2),dtype=np.float64)
rng=np.random.default_rng(1919)

def note(midi, seconds, voice):
    t=np.arange(round(seconds*RATE))/RATE
    f=440*2**((midi-69)/12)
    attack=1-np.exp(-t/ .006)
    if voice=="marimba":
        sound=(np.sin(2*np.pi*f*t)*np.exp(-t/ .30)
               +.27*np.sin(2*np.pi*f*2.002*t)*np.exp(-t/ .13)
               +.09*np.sin(2*np.pi*f*3.99*t)*np.exp(-t/ .07))
    elif voice=="pluck":
        sound=sum(np.sin(2*np.pi*f*k*t+.025*k)*np.exp(-t/(.52/k**.6))/k**1.7
                  for k in range(1,6))
    elif voice=="flute":
        phase=2*np.pi*f*t+.001*f*np.sin(2*np.pi*4.5*t)
        sound=(np.sin(phase)+.07*np.sin(2*phase))*np.exp(-t/1.2)
        attack=1-np.exp(-t/ .05)
    else:
        sound=(np.sin(2*np.pi*f*t)+.15*np.sin(4*np.pi*f*t))*np.exp(-t/ .26)
    release=np.minimum(1,np.maximum(0,(seconds-t)/ .035))
    return sound*attack*release

def add(sound, beat, level, pan=0):
    # Wrap note/reverb tails into the start: a periodic arrangement with no silent gap.
    start=round(beat*BEAT*RATE)
    indices=(start+np.arange(len(sound)))%SIZE
    gains=np.array([np.cos((pan+1)*np.pi/4),np.sin((pan+1)*np.pi/4)])
    mix[indices]+=sound[:,None]*level*gains

chords=[
    (48,[60,64,67,71]), (43,[59,62,67,69]),
    (45,[60,64,67,69]), (41,[57,60,64,67]),
    (48,[60,64,67,71]), (43,[59,62,67,69]),
    (41,[57,60,64,67]), (43,[59,62,67,69]),
]
# Authored motifs with rests and repeated answers; no well-known song quotation.
motifs=[
    [(0,76,.6),(.75,79,.35),(1.5,81,.7),(2.5,79,.45),(3.25,76,.5)],
    [(.25,74,.5),(1,76,.45),(2,79,.6),(3,74,.55)],
    [(0,76,.6),(1,79,.5),(1.75,81,.65),(3,84,.5)],
    [(.25,81,.65),(1.25,79,.6),(2.25,76,.5),(3,74,.5)],
    [(0,79,.65),(1,76,.4),(1.75,74,.65),(3,72,.55)],
    [(.5,74,.45),(1.25,76,.5),(2.25,79,.8)],
    [(0,76,.6),(1,74,.6),(2,72,.75)],
    [(.5,74,.6),(1.5,71,.6),(2.5,74,.65),(3.5,79,.35)],
]
for bar in range(32):
    root,chord=chords[bar%8]
    base=bar*4
    for beat in (0,2):
        add(note(root if beat==0 else root+7,.65,"bass"),base+beat,.14)
    # Light offbeat strumming with a slight ascending strum delay.
    for beat in (.5,1.5,2.5,3.5):
        for k,midi in enumerate(chord):
            add(note(midi,.82,"pluck"),base+beat+k*.018,.040,-.32)
    # A contrasting quiet B phrase halfway through the loop.
    motif=motifs[bar%8]
    for at,midi,length in motif:
        if 16<=bar<24:
            if at<2.5:
                add(note(midi-12,length*BEAT+.3,"flute"),base+at,.13,.18)
        else:
            add(note(midi,length*BEAT+.22,"marimba"),base+at,.19,.22)
    for eighth in range(8):
        seconds=.06
        noise=rng.normal(size=round(seconds*RATE))
        high=np.concatenate([[0],np.diff(noise)])
        t=np.arange(len(high))/RATE
        shaker=high*np.exp(-t/.014)*(1-np.exp(-t/.001))
        add(shaker,base+eighth*.5,.009 if eighth%2==0 else .013,.4)
    for beat in (0,2):
        t=np.arange(round(.18*RATE))/RATE
        kick=np.sin(2*np.pi*(48*t+11*.028*(1-np.exp(-t/.028))))*np.exp(-t/.035)
        add(kick,base+beat,.07)
    if bar%4==3:
        add(note(chord[-1]+12,.6,"marimba"),base+3.75,.065,-.4)

# Subtle stereo room taps, periodic at the same sample length.
dry=mix.copy()
for seconds,gain in [(.073,.075),(.137,.055),(.223,.035)]:
    mix+=np.roll(dry,round(seconds*RATE),axis=0)[:,::-1]*gain
mix-=mix.mean(axis=0)
mix*=.10/np.sqrt(np.mean(mix**2))
peak=float(np.abs(mix).max())
if peak>.82: mix*=.82/peak
# Both endpoints share the periodic seam; a five-sample click guard is inaudible.
seam=(mix[0]+mix[-1])/2
for i in range(5):
    blend=(5-i)/5
    mix[i]=mix[i]*(1-blend)+seam*blend
    mix[-i-1]=mix[-i-1]*(1-blend)+seam*blend
pcm=np.round(mix*32767).astype("<i2")
path=ROOT/"app/src/main/assets/woodland_stroll.wav"
with wave.open(str(path),"wb") as f:
    f.setnchannels(2);f.setsampwidth(2);f.setframerate(RATE);f.writeframes(pcm.tobytes())
manifest={
    "title":"林间散步 / Woodland Stroll","original":True,
    "bpm":BPM,"key":"C major","bars":32,"time_signature":"4/4",
    "seconds":SIZE/RATE,"sample_rate":RATE,"channels":2,
    "rms_dbfs":float(20*np.log10(np.sqrt(np.mean(mix**2)))),
    "peak_dbfs":float(20*np.log10(np.max(np.abs(mix)))),
    "seam_delta_pcm":int(np.max(np.abs(pcm[0].astype(int)-pcm[-1].astype(int)))),
    "bytes":path.stat().st_size,
}
(ROOT/"art/woodland_music_manifest.json").write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+"\n")
print(json.dumps(manifest,indent=2,ensure_ascii=False))
