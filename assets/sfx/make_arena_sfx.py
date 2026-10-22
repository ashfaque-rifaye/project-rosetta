"""Synthesize the Unity raid arena's impact sounds (original, no samples) into arena-unity/Assets/Resources/Sfx.

  python assets/sfx/make_arena_sfx.py
hit = pitched-down thump + short noise crack; crit = heavier thump, sub drop and a bright shimmer tail.
The victory fanfare reuses the TV app's sfx_reward.
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parents[2] / "arena-unity" / "Assets" / "Resources" / "Sfx"


def thump(dur, f0, f1, amp, decay):
    out, phase = [], 0.0
    for i in range(int(RATE * dur)):
        t = i / RATE
        f = f1 + (f0 - f1) * math.exp(-t * 28)
        phase += 2 * math.pi * f / RATE
        out.append(amp * math.exp(-decay * t) * math.sin(phase))
    return out


def crack(dur, amp, decay, seed):
    rnd, out, lp = random.Random(seed), [], 0.0
    for i in range(int(RATE * dur)):
        lp += 0.45 * (rnd.uniform(-1, 1) - lp)
        out.append(amp * math.exp(-decay * i / RATE) * lp)
    return out


def shimmer(dur, amp):
    return [amp * math.exp(-5 * i / RATE) * sum(math.sin(2 * math.pi * f * i / RATE) for f in (1568, 2093, 2637)) / 3
            for i in range(int(RATE * dur))]


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


def write(name, buf):
    peak = max(1e-6, max(abs(v) for v in buf))
    with wave.open(str(OUT / f"{name}.wav"), "w") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(32000 * v / peak * 0.9)) for v in buf))


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    write("hit", mix(thump(0.35, 240, 70, 1.0, 11), crack(0.12, 0.6, 40, 1)))
    write("crit", mix(thump(0.7, 300, 45, 1.0, 6), crack(0.2, 0.8, 22, 2), shimmer(0.8, 0.35)))
    print("wrote", OUT)
