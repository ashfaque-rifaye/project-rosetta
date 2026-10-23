"""Record the real phone pages (live site, DEMO family) as smooth phone-sized video, for the pocket-money scene.

  python assets/demo/phone.py money      ->  footage/p_kid_ask.mp4, footage/p_mom_approve.mp4, footage/x_treasure_live.webm

Chromium's screencast sends a frame on every repaint; the frames are timed by their own timestamps and turned into a
30 fps video. The TV is filmed at the same moment (film3.py), so the Treasure screen updates live on camera.
"""
import base64
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

import film
import film3

WEB = "https://dr8w3l96syzm7.cloudfront.net"
TMP = Path("E:/Temp/phone")


def record(name: str, url: str, actions, storage: dict | None = None):
    frames = []
    with sync_playwright() as p:
        b = p.chromium.launch()
        ctx = b.new_context(viewport={"width": 412, "height": 892}, device_scale_factor=2.5, is_mobile=True, has_touch=True)
        if storage:
            ctx.add_init_script("".join(f"localStorage.setItem({json.dumps(k)}, {json.dumps(v)});" for k, v in storage.items()))
        page = ctx.new_page()
        cdp = ctx.new_cdp_session(page)

        def on_frame(ev):
            frames.append((ev["metadata"]["timestamp"], ev["data"]))
            cdp.send("Page.screencastFrameAck", {"sessionId": ev["sessionId"]})

        cdp.on("Page.screencastFrame", on_frame)
        page.goto(url)
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(1500)
        cdp.send("Page.startScreencast", {"format": "jpeg", "quality": 92, "maxWidth": 1030, "maxHeight": 2230, "everyNthFrame": 1})
        t0 = time.time()
        actions(page)
        end = time.time() - t0
        cdp.send("Page.stopScreencast")
        b.close()
    d = TMP / name
    shutil.rmtree(d, ignore_errors=True)
    d.mkdir(parents=True)
    start = frames[0][0]
    lines = []
    for i, (ts, data) in enumerate(frames):
        (d / f"{i:05d}.jpg").write_bytes(base64.b64decode(data))
        nxt = frames[i + 1][0] if i + 1 < len(frames) else start + end
        lines.append(f"file '{(d / f'{i:05d}.jpg').as_posix()}'\nduration {max(0.001, nxt - ts):.4f}\n")
    lines.append(f"file '{(d / f'{len(frames) - 1:05d}.jpg').as_posix()}'\n")
    (d / "list.txt").write_text("".join(lines))
    out = film.OUT / f"{name}.mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(d / "list.txt"),
                    "-vf", "fps=30,scale=1030:-2:flags=lanczos,format=yuv420p", "-c:v", "libx264", "-crf", "14", str(out)], check=True)
    print("phone", name, len(frames), "frames", round(end, 1), "s", flush=True)


def kid_ask(page):
    """Meera scrolls to her wish list and asks for what she has saved for (waits pump the screencast; never time.sleep)."""
    page.wait_for_timeout(1200)
    page.evaluate("document.getElementById('wishes').scrollIntoView({behavior: 'smooth', block: 'center'})")
    page.wait_for_timeout(2200)
    page.locator("#wishes button[data-wish]").first.tap()
    page.wait_for_timeout(4000)


def mom_approve(page):
    """Mom sees 'Wish ready' under Needs you and approves it with one tap."""
    page.wait_for_timeout(1500)
    btn = page.locator("button[data-wish='approve']").first
    btn.wait_for(timeout=20000)
    page.evaluate("document.querySelector(\"button[data-wish='approve']\").scrollIntoView({behavior: 'smooth', block: 'center'})")
    page.wait_for_timeout(2500)
    btn.tap()
    page.wait_for_timeout(4500)


def money(kid="meera"):
    """Treasure on the TV, filmed from before the ask until after Mom's yes has landed."""
    film3.screen("treasure", "x_treasure_before", 4)
    if kid != "arjun":
        film.key("DPAD_RIGHT", 0.8)
        film.key("DPAD_CENTER", 2.5)
    rec = film3.HostRec("x_treasure_live", 10).__enter__()
    time.sleep(3)
    record("p_kid_ask", f"{WEB}/kid.html?code=DEMO&kid={kid}", kid_ask)
    record("p_mom_approve", f"{WEB}/parent.html?code=DEMO", mom_approve,
           {"rosetta.code": "DEMO", "rosetta.parent": json.dumps({"id": "mom", "name": "Mom", "pin": "2468"})})  # the public demo PIN (README)
    time.sleep(6)
    rec.__exit__()


if __name__ == "__main__":
    {"money": money}[sys.argv[1]]()
