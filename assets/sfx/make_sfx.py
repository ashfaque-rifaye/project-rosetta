"""Synthesize Rosetta's UI sound effects (original, royalty-free, no samples) into the TV app's res/raw.

  python assets/sfx/make_sfx.py
Soft marimba/bell-like tones: additive sines with fast attack and exponential decay.
"""
import math
import struct
import wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parents[2] / "tv-app" / "app" / "src" / "main" / "res" / "raw"


def tone(freq, dur, amp=0.5, decay=6.0, partials=((1, 1.0), (2.01, 0.35), (3.98, 0.12)), attack=0.004):
    n = int(RATE * dur)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * math.exp(-decay * t)
        s = sum(a * math.sin(2 * math.pi * freq * m * t) for m, a in partials)
        out.append(amp * env * s / 1.5)
    return out


def mix(*tracks):
    n = max(len(start) + len(tr) for start, tr in tracks)
    buf = [0.0] * n
    for start, tr in tracks:
        for i, v in enumerate(tr):
            buf[len(start) + i] += v
    return buf


def at(sec):
    return [0.0] * int(RATE * sec)


def noise_sweep(dur, amp=0.25):
    import random
    rnd = random.Random(7)
    n = int(RATE * dur)
    prev = 0.0
    out = []
    for i in range(n):
        t = i / RATE
        a = 0.02 + 0.5 * (1 - t / dur)
        prev = prev + a * (rnd.uniform(-1, 1) - prev)  # low-passed noise = stone grit
        out.append(amp * prev * math.exp(-3 * t) * min(1, t / 0.01))
    return out


def save(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    peak = max(1e-6, max(abs(s) for s in samples))
    g = 0.85 / peak
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * g)) * 32767)) for s in samples))
    print("wrote", name)


C5, E5, G5, A5, C6, D6, E6, G6 = 523.25, 659.25, 783.99, 880.0, 1046.5, 1174.7, 1318.5, 1568.0
save("sfx_move", tone(G6, 0.08, decay=40, partials=((1, 1.0),)))
save("sfx_select", mix((at(0), tone(E6, 0.18, decay=18)), (at(0.05), tone(G6, 0.2, decay=16))))
save("sfx_found", mix(*[(at(i * 0.07), tone(f, 0.35, decay=9)) for i, f in enumerate([C5, E5, G5, C6])]))
save("sfx_home", mix((at(0), tone(A5, 0.3, decay=10)), (at(0.08), tone(E6, 0.4, decay=8))))
save("sfx_reward", mix(*[(at(i * 0.09), tone(f, 0.6, decay=5)) for i, f in enumerate([C6, E6, G6, C6 * 2])]))
save("sfx_carve", mix((at(0), noise_sweep(0.9)), (at(0.55), tone(C5, 1.2, decay=2.5)), (at(0.6), tone(G5, 1.2, decay=2.5, amp=0.35))))
save("sfx_levelup", mix(*[(at(i * 0.11), tone(f, 0.7, decay=4)) for i, f in enumerate([C5, E5, G5, C6, E6, G6])]))
