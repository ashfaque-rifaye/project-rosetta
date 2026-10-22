"""Lambda entry points.

api(event)       HTTP API (TV, kid phone, parent phone)
on_upload(event) S3 ObjectCreated for uploads/{code}/{questId}/{phase}-{n}.jpg  ->  agent crew
"""
from __future__ import annotations

import json
import logging
import hmac
import re
import time
import urllib.parse

from . import crew, learning, notify, store, vision

log = logging.getLogger()
log.setLevel(logging.INFO)

# Kid-safe, fixed set: phones can only send these.
ALLOWED_EMOTES = {"🔥", "💀", "🫶", "⚡", "🏆", "😭"}

KEEPER_OPEN = "Snap the room with your phone, {name}. Let's see who got lost today."
KEEPER_OPEN_STUDY = "Snap your homework with your phone, {name}. Tutor will take a look."


CORS = {"access-control-allow-origin": "*", "access-control-allow-methods": "GET,POST,OPTIONS",
        "access-control-allow-headers": "content-type,x-parent-pin"}


def _resp(status: int, body) -> dict:
    return {"statusCode": status, "headers": {"content-type": "application/json", **CORS}, "body": json.dumps(body)}


def _is_parent(event, h) -> bool:
    """Parent-only actions need the household's parent PIN (header x-parent-pin). Kids only know the join code."""
    pin = (event.get("headers") or {}).get("x-parent-pin", "")
    return bool(pin) and hmac.compare_digest(str(pin), str(h.get("parentPin", "")))


QUICK_CHEERS = {"Proud of you! 🫶", "Great job! 🏆", "Snack time after this 🍪", "You've got this ⚡", "Movie night soon 🍿"}


def api(event, _context):
    method = event["requestContext"]["http"]["method"]
    if method == "OPTIONS":  # the catch-all route also receives browser preflights
        return {"statusCode": 204, "headers": CORS, "body": ""}
    path = event["rawPath"].rstrip("/")
    body = json.loads(event.get("body") or "{}") if event.get("body") else {}
    try:
        if path == "/vapid-public-key":
            return _resp(200, {"key": notify.VAPID_PUBLIC})

        m = re.fullmatch(r"/household/(\w+)", path)
        if m and method == "GET":
            return _resp(200, store.public_view(store.load(m[1])))

        m = re.fullmatch(r"/household/(\w+)/reset", path)
        if m and method == "POST":
            return _resp(200, store.public_view(store.seed(m[1])))

        m = re.fullmatch(r"/household/(\w+)/quests/(\w+)/(open|close|approve|reject)", path)
        if m and method == "POST":
            code, qid, action = m.groups()
            h = store.load(code)
            q = store.quest(h, qid)
            if action in ("approve", "reject") and not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            if action == "open" and q["stage"] == "Ready":
                opener = KEEPER_OPEN_STUDY if q.get("kind") == "study" else KEEPER_OPEN
                q.update(stage="AwaitingBefore", openedAtMs=int(time.time() * 1000),
                         keeperLine=opener.format(name=store.kid(h, q["kidId"])["name"]))
            elif action == "reject":  # parent says "not yet": back to tidying, kid gets a kind nudge
                q.update(stage="Partial", needsParent=False, keeperLine="A grown-up says almost! One more look around 👀")
                notify.emit(h, "parent_reply", "Not quite yet", f"{store.kid(h, q['kidId'])['name']}, a grown-up wants one more look.", by="parent", notify=False)
            elif action == "close" and q["stage"] == "Done":
                h["quests"] = [x for x in h["quests"] if x["id"] != qid]
            elif action == "approve":  # parent overrides a low-confidence Judge or Tutor verdict
                for t in q.get("things", []):
                    t["returned"] = True
                q.update(stage="Done", needsParent=False,
                         keeperLine="A grown-up checked your homework. Well done!" if q.get("kind") == "study"
                         else "A grown-up checked it: every Lost Thing is home!")
                for g in h["glyphs"]:
                    if g["id"] == q["glyphId"]:
                        g["earnedBy"] = q["kidId"]
                crew.record_finish(h, q)
                notify.emit(h, "quest_done", f"{store.kid(h, q['kidId'])['name']} finished “{q['title']}” ✅", "Approved by a parent.", by="parent", notify=False)
            store.save(h)
            return _resp(200, {"ok": True, "stage": q["stage"]})

        m = re.fullmatch(r"/household/(\w+)/quests", path)
        if m and method == "POST":  # parent posts a new quest
            h = store.load(m[1])
            if not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            n = len(h["quests"]) + 1
            q = {
                "id": f"q_custom_{n}", "kidId": body["kidId"], "roomId": body.get("roomId", "living"),
                "kind": "study" if body.get("kind") == "study" else "tidy",
                "title": body["title"][:60], "glyphId": f"next_custom_{n}", "stage": "Ready", "things": [],
                "keeperLine": "", "confidence": 1.0, "attempt": 0,
            }
            h["quests"].append(q)
            notify.emit(h, "new_quest", f"New quest for {store.kid(h, q['kidId'])['name']}", q["title"], by="parent", notify=False)
            store.save(h)
            return _resp(200, q)

        m = re.fullmatch(r"/household/(\w+)/message", path)
        if m and method == "POST":  # family message -> feed + TV (the Keeper reads it aloud)
            h = store.load(m[1])
            sender = body.get("from", "")
            parent = next((p for p in h.get("parents", []) if p["id"] == sender), None)
            text = str(body.get("text", "")).strip()[:140]
            if parent:
                if not _is_parent(event, h):
                    return _resp(403, {"error": "parent PIN required"})
                ev = notify.emit(h, "message", parent["name"], text, by=parent["id"], data={"to": body.get("to", "all")}, notify=False)
            else:  # kids may only send the fixed cheers (no free text from kid phones)
                kid = next((k for k in h["kids"] if k["id"] == sender), None)
                if not kid or text not in QUICK_CHEERS:
                    return _resp(400, {"error": "kids can send quick cheers only"})
                ev = notify.emit(h, "message", kid["name"], text, by=kid["id"], notify=False)
            store.save(h)
            return _resp(200, ev)

        m = re.fullmatch(r"/household/(\w+)/done-check", path)
        if m and method == "POST":  # kid: "I'm done, can you check?"
            h = store.load(m[1])
            kid = store.kid(h, body["kidId"])
            ev = notify.emit(h, "done_check", f"{kid['name']} says: done! Check?", str(body.get("note", "Tap to see the room photos."))[:140],
                             by=kid["id"], data={"kidId": kid["id"], "questId": body.get("questId", "")})
            store.save(h)
            return _resp(200, ev)

        m = re.fullmatch(r"/household/(\w+)/parents/(\w+)/test", path)
        if m and method == "POST":  # parent: "send me a test alert" on every channel they turned on
            h = store.load(m[1])
            if not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            p = next(x for x in h.get("parents", []) if x["id"] == m[2])
            ev = {"id": f"t{int(time.time() * 1000)}", "kind": "test", "title": "Rosetta alerts are on ✅",
                  "body": "You'll hear about finished homework, anything that needs you, and the weekly digest.", "data": {}}
            delivered = notify._fan_out(p, ev)
            return _resp(200, {"delivered": delivered})

        m = re.fullmatch(r"/household/(\w+)/parents/(\w+)", path)
        if m and method in ("GET", "POST"):  # parent: sign-in check (GET), alert settings + push subscription (POST)
            h = store.load(m[1])
            if not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            parents = h.setdefault("parents", [])
            p = next((x for x in parents if x["id"] == m[2]), None)
            if method == "GET":
                if p is None:
                    return _resp(404, {"error": "unknown parent"})
                return _resp(200, {**{k: v for k, v in p.items() if k != "push"}, "devices": len(p.get("push", []))})
            if p is None:
                p = {"id": m[2], "name": body.get("name", m[2].title()), "push": [], "alerts": {"push": True, "email": False, "sms": False}}
                parents.append(p)
            for f in ("name", "email", "phone"):
                if f in body:
                    p[f] = str(body[f])[:80]
            if "alerts" in body:
                p["alerts"] = {k: bool(body["alerts"].get(k)) for k in ("push", "email", "sms")}
            if body.get("subscription"):
                subs = [x for x in p["push"] if x.get("endpoint") != body["subscription"].get("endpoint")]
                p["push"] = (subs + [body["subscription"]])[-5:]
            store.save(h)
            out = {k: v for k, v in p.items() if k != "push"}
            out["devices"] = len(p["push"])
            return _resp(200, out)

        m = re.fullmatch(r"/household/(\w+)/emote", path)
        if m and method == "POST":  # a phone sends a hype emote to the TV
            h = store.load(m[1])
            emoji = str(body.get("emoji", ""))[:8]
            if emoji not in ALLOWED_EMOTES:
                return _resp(400, {"error": "emote not allowed"})
            em = {"id": f"e{int(time.time() * 1000)}", "kidId": body.get("kidId", ""), "emoji": emoji, "atMs": int(time.time() * 1000)}
            h["emotes"] = (h.get("emotes", []) + [em])[-20:]
            store.save(h)
            return _resp(200, em)

        m = re.fullmatch(r"/household/(\w+)/quests/(\w+)/answer", path)
        if m and method == "POST":  # kid answers a practice question (TV remote or phone)
            h = store.load(m[1])
            r = learning.answer_practice(h, m[2], body["questionId"], int(body["choice"]))
            store.save(h)
            return _resp(200, r)

        m = re.fullmatch(r"/household/(\w+)/quiz/(start|answer)", path)
        if m and method == "POST":  # family Quiz Battle
            h = store.load(m[1])
            if m[2] == "start":
                r = learning.start_quiz(h, int(body.get("count", 8)))
            else:
                r = learning.answer_quiz(h, body["questionId"], body["kidId"], int(body["choice"]))
            store.save(h)
            return _resp(200, r)

        m = re.fullmatch(r"/household/(\w+)/upload", path)
        if m and method == "POST":  # kid's phone asks where to put the next photo
            code = m[1]
            h = store.load(code)
            q = store.quest(h, body["questId"])
            # A homework photo is always marked afresh; a room needs a before and then after photos.
            phase = "before" if q.get("kind") == "study" or q["stage"] in ("Ready", "AwaitingBefore") else "after"
            n = q.get("attempt", 0) + (1 if phase == "after" else 0)
            key = f"uploads/{code}/{q['id']}/{phase}-{n}.jpg"
            return _resp(200, {"uploadUrl": store.presign_put(key), "key": key, "phase": phase})

        return _resp(404, {"error": "not found"})
    except KeyError as e:
        return _resp(404, {"error": f"unknown {e}"})
    except (StopIteration, ValueError) as e:
        return _resp(400, {"error": str(e) or "bad request"})
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
        if q.get("kind") == "study":
            q.update(stage="Scanning", beforeKey=key, homework=None, practice=[], answers={}, keeperLine="Tutor is reading your work…")
        elif phase == "before":
            q.update(stage="Scanning", beforeKey=key, afterKey=None, things=[], attempt=0, keeperLine="Looking closely…")
        else:
            q.update(stage="Verifying", afterKey=key, attempt=n, keeperLine="Checking on everyone…")
        store.save(h)
        log.info("crew run %s %s %s", code, qid, phase)
        crew.run(code, qid, phase)



def _week_stats(h: dict, kid_id: str) -> dict:
    now = time.time() * 1000
    week = 7 * 24 * 3600 * 1000
    rows = [r for r in h.get("history", []) if r["kidId"] == kid_id]
    this = [r for r in rows if now - r["atMs"] < week]
    last = [r for r in rows if week <= now - r["atMs"] < 2 * week]

    def avg(xs):
        return round(sum(r["minutes"] for r in xs) / len(xs), 1) if xs else None

    return {
        "quests_this_week": len(this), "quests_last_week": len(last),
        "avg_minutes_this_week": avg(this), "avg_minutes_last_week": avg(last),
        "self_started_this_week": sum(1 for r in this if r["selfStarted"]),
        "items_put_away_this_week": sum(r["items"] for r in this),
        "rooms": sorted({r["roomId"] for r in this}),
    }


def scheduled(event, _context):
    """EventBridge: 'streak' daily evening nudge; 'digest' weekly Growth digest written by the Digest agent."""
    job = event.get("job", "")
    for code in event.get("households", ["R4KX"]):
        try:
            h = store.load(code)
        except KeyError:
            continue
        day = 24 * 3600 * 1000
        now = time.time() * 1000
        if job == "streak":
            for kid in h["kids"]:
                if not any(r["kidId"] == kid["id"] and now - r["atMs"] < day for r in h.get("history", [])):
                    notify.emit(h, "streak_risk", f"{kid['name']}'s streak is at risk 🔥", "No quest yet today. One small quest keeps it alive.",
                                by="Coach", data={"kidId": kid["id"]})
        elif job == "digest":
            for kid in h["kids"]:
                stats = _week_stats(h, kid["id"])
                d = vision.digest(kid["name"], kid["age"], stats)
                h.setdefault("digests", {})[kid["id"]] = {"atMs": int(now), "stats": stats, **d}
                notify.emit(h, "weekly_digest", f"{kid['name']}'s week: {d['headline']}", " · ".join(d["notes"]), by="Digest",
                            data={"kidId": kid["id"]})
        store.save(h)
