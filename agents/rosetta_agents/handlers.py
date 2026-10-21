"""Lambda entry points.

api(event)       HTTP API (TV, kid phone, parent phone)
on_upload(event) S3 ObjectCreated for uploads/{code}/{questId}/{phase}-{n}.jpg  ->  agent crew
"""
from __future__ import annotations

import json
import logging
import re
import urllib.parse

from . import crew, store

log = logging.getLogger()
log.setLevel(logging.INFO)

KEEPER_OPEN = "Snap the room with your phone, {name}. Let's see who got lost today."


def _resp(status: int, body) -> dict:
    return {
        "statusCode": status,
        "headers": {"content-type": "application/json", "access-control-allow-origin": "*"},
        "body": json.dumps(body),
    }


def api(event, _context):
    method = event["requestContext"]["http"]["method"]
    path = event["rawPath"].rstrip("/")
    body = json.loads(event.get("body") or "{}") if event.get("body") else {}
    try:
        m = re.fullmatch(r"/household/(\w+)", path)
        if m and method == "GET":
            return _resp(200, store.public_view(store.load(m[1])))

        m = re.fullmatch(r"/household/(\w+)/reset", path)
        if m and method == "POST":
            return _resp(200, store.public_view(store.seed(m[1])))

        m = re.fullmatch(r"/household/(\w+)/quests/(\w+)/(open|close|approve)", path)
        if m and method == "POST":
            code, qid, action = m.groups()
            h = store.load(code)
            q = store.quest(h, qid)
            if action == "open" and q["stage"] == "Ready":
                q.update(stage="AwaitingBefore", keeperLine=KEEPER_OPEN.format(name=store.kid(h, q["kidId"])["name"]))
            elif action == "close" and q["stage"] == "Done":
                h["quests"] = [x for x in h["quests"] if x["id"] != qid]
            elif action == "approve":  # parent overrides a low-confidence Judge verdict
                for t in q["things"]:
                    t["returned"] = True
                q.update(stage="Done", needsParent=False, keeperLine="A grown-up checked it: every Lost Thing is home!")
                for g in h["glyphs"]:
                    if g["id"] == q["glyphId"]:
                        g["earnedBy"] = q["kidId"]
            store.save(h)
            return _resp(200, {"ok": True, "stage": q["stage"]})

        m = re.fullmatch(r"/household/(\w+)/quests", path)
        if m and method == "POST":  # parent posts a new quest
            h = store.load(m[1])
            n = len(h["quests"]) + 1
            q = {
                "id": f"q_custom_{n}", "kidId": body["kidId"], "roomId": body.get("roomId", "living"),
                "title": body["title"][:60], "glyphId": f"next_custom_{n}", "stage": "Ready", "things": [],
                "keeperLine": "", "confidence": 1.0, "attempt": 0,
            }
            h["quests"].append(q)
            store.save(h)
            return _resp(200, q)

        m = re.fullmatch(r"/household/(\w+)/upload", path)
        if m and method == "POST":  # kid's phone asks where to put the next photo
            code = m[1]
            h = store.load(code)
            q = store.quest(h, body["questId"])
            phase = "before" if q["stage"] in ("Ready", "AwaitingBefore") else "after"
            n = q.get("attempt", 0) + (1 if phase == "after" else 0)
            key = f"uploads/{code}/{q['id']}/{phase}-{n}.jpg"
            return _resp(200, {"uploadUrl": store.presign_put(key), "key": key, "phase": phase})

        return _resp(404, {"error": "not found"})
    except KeyError as e:
        return _resp(404, {"error": f"unknown {e}"})
    except Exception as e:  # noqa: BLE001
        log.exception("api error")
        return _resp(500, {"error": str(e)})


def on_upload(event, _context):
    for rec in event.get("Records", []):
        key = urllib.parse.unquote_plus(rec["s3"]["object"]["key"])
        m = re.fullmatch(r"uploads/(\w+)/(\w+)/(before|after)-(\d+)\.jpg", key)
        if not m:
            continue
        code, qid, phase, n = m[1], m[2], m[3], int(m[4])
        h = store.load(code)
        q = store.quest(h, qid)
        if phase == "before":
            q.update(stage="Scanning", beforeKey=key, afterKey=None, things=[], attempt=0, keeperLine="Looking closely…")
        else:
            q.update(stage="Verifying", afterKey=key, attempt=n, keeperLine="Checking on everyone…")
        store.save(h)
        log.info("crew run %s %s %s", code, qid, phase)
        crew.run(code, qid, phase)
