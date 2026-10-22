"""Voice the Keeper's repeating lines ahead of time, so the first family to hear each one doesn't wait for it.

  ROSETTA_BUCKET=<uploads bucket> python agents/warm_voice.py
Needs Google ADC (gcloud auth application-default login) for Vertex AI and AWS credentials for the bucket.
Lines must match handlers.py / learning.py exactly: each clip is keyed by a hash of its text (see voice.py).
"""
import time

from rosetta_agents import handlers, voice

KIDS = ["Arjun", "Meera"]
LINES = [
    *[handlers.KEEPER_OPEN.format(name=k) for k in KIDS],
    *[handlers.KEEPER_OPEN_STUDY.format(name=k) for k in KIDS],
    "Tutor is reading your work…", "Looking closely…", "Checking on everyone…",
    "A grown-up checked your homework. Well done!", "A grown-up checked it: every Lost Thing is home!",
    "A grown-up says almost! One more look around 👀",
    *[f"{right} of {total} in practice. That's how skills grow, {k}!"
      for k in KIDS for total in (4, 5) for right in range(total + 1)],
]

if __name__ == "__main__":
    # One at a time: Gemini TTS has a small per-minute quota; wait it out when it's hit.
    for line in LINES:
        for _ in range(6):
            if key := voice.fixed(line):
                break
            time.sleep(20)
        print("ok " if key else "FAIL", line, flush=True)
