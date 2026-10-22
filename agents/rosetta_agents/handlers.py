"""Lambda entry points.

api(event)       HTTP API (TV, kid phone, parent phone)
on_upload(event) S3 ObjectCreated for uploads/{code}/{questId}/{phase}-{n}.{ext}: photos -> agent crew,
                 voice notes -> transcript + memory, habit photos -> honest proof check
"""
from __future__ import annotations

import json
import logging
import hmac
import re
import time
import urllib.parse

from . import config, crew, family, learning, notify, store, vision, voice

log = logging.getLogger()
log.setLevel(logging.INFO)

# Kid-safe, fixed set: phones can only send these.
ALLOWED_EMOTES = {"🔥", "💀", "🫶", "⚡", "🏆", "😭"}

KEEPER_OPEN = "Snap the room with your phone, {name}. Let's see who got lost today."
KEEPER_OPEN_STUDY = "Snap your homework with your phone, {name}. The AI Tutor will take a look."
KEEPER_OPEN_HABIT = "Time for {title}, {name}. Tell me on your phone when it's done."
AUDIO_EXT = {"audio/webm": "webm", "audio/ogg": "ogg", "audio/mp4": "m4a", "audio/mpeg": "mp3", "audio/wav": "wav", "audio/x-m4a": "m4a"}


def _message_voice(code: str, sender: str, text: str) -> str | None:
    """A parent's message in the Keeper's voice (Gemini TTS), stored next to the household's other audio."""
    if config.AI_PROVIDER != "vertex" or not text:
        return None
    try:
        from . import gemini
        key = f"voice/{code}/msg-{int(time.time() * 1000)}.wav"
        store.put_voice(key, gemini.speak(f"A message from {sender}. {text}", purpose="message-voice"))
        return key
    except Exception:  # the TV reads it with its own voice instead
        log.exception("message voice failed")
        return None


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
            h = store.load(m[1])
            if family.daily(h):  # a new day: today's habit tasks, fresh TV time
                store.save(h)
            return _resp(200, store.public_view(h))

        m = re.fullmatch(r"/household/(\w+)/reset", path)
        if m and method == "POST":  # start the family over from nothing (parents only)
            try:
                if not _is_parent(event, store.load(m[1])):
                    return _resp(403, {"error": "parent PIN required"})
            except KeyError:
                pass  # a brand-new household can be created by anyone with the code
            return _resp(200, store.public_view(store.seed(m[1])))

        m = re.fullmatch(r"/household/(\w+)/quests/(\w+)/(open|close|approve|reject)", path)
        if m and method == "POST":
            code, qid, action = m.groups()
            h = store.load(code)
            q = store.quest(h, qid)
            if action in ("approve", "reject") and not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            if action == "open" and q["stage"] == "Ready":
                opener = {"study": KEEPER_OPEN_STUDY, "habit": KEEPER_OPEN_HABIT}.get(q.get("kind"), KEEPER_OPEN)
                line = opener.format(name=store.kid(h, q["kidId"])["name"], title=q["title"].lower())
                audio = voice.fixed(line)
                h = store.load(code)  # fresh after the voice (only slow the first time a line is heard)
                q = store.quest(h, qid)
                q.update(stage="AwaitingBefore" if q.get("kind") != "habit" else "Ready", openedAtMs=int(time.time() * 1000))
                voice.set_line(q, line, audio)
            elif action == "reject":  # parent says "not yet": back to it, kid gets a kind nudge
                line = ("A grown-up says: not quite yet. Give it one more go!" if q.get("kind") == "habit"
                        else "A grown-up says almost! One more look around 👀")
                audio = voice.fixed(line)
                h = store.load(code)
                q = store.quest(h, qid)
                q.update(stage="Ready" if q.get("kind") == "habit" else "Partial", needsParent=False)
                voice.set_line(q, line, audio)
                notify.emit(h, "parent_reply", "Not quite yet", f"{store.kid(h, q['kidId'])['name']}, a grown-up wants one more look.", by="parent", notify=False)
            elif action == "close" and q["stage"] == "Done":
                h["quests"] = [x for x in h["quests"] if x["id"] != qid]
            elif action == "approve":  # parent overrides a low-confidence Judge or Tutor verdict
                for t in q.get("things", []):
                    t["returned"] = True
                q.update(stage="Done", needsParent=False)
                voice.set_line(q, {"study": "A grown-up checked your homework. Well done!",
                                   "habit": "A grown-up says well done!"}.get(q.get("kind"), "A grown-up checked it: every Lost Thing is home!"))
                family.finish(h, q)
                notify.emit(h, "quest_done", f"{store.kid(h, q['kidId'])['name']} finished “{q['title']}” ✅", "Approved by a parent.", by="parent", notify=False)
            store.save(h)
            return _resp(200, {"ok": True, "stage": q["stage"]})

        m = re.fullmatch(r"/household/(\w+)/quests", path)
        if m and method == "POST":  # parent posts a new quest
            h = store.load(m[1])
            if not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            store.kid(h, body["kidId"])
            study = body.get("kind") == "study"
            q = {
                "id": f"q_custom_{int(time.time() * 1000) % 10**9}", "kidId": body["kidId"], "roomId": body.get("roomId", "living"),
                "kind": "study" if study else "tidy", "title": str(body["title"])[:60], "proof": "photo",
                "mustDo": bool(body.get("mustDo", study)), "due": family.today(), "stage": "Ready", "things": [],
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
                audio = _message_voice(m[1], parent["name"], text)
                h = store.load(m[1])  # fresh after the few seconds the voice takes, so nothing saved meanwhile is lost
                data = {"to": body.get("to", "all"), **({"audioKey": audio} if audio else {})}
                ev = notify.emit(h, "message", parent["name"], text, by=parent["id"], data=data, notify=False)
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
        if m and method == "POST":  # kid's phone asks where to put the next photo or voice note
            code = m[1]
            h = store.load(code)
            q = store.quest(h, body["questId"])
            content_type = "image/jpeg"
            if q.get("kind") == "habit":  # proof for a daily task: a voice note or a photo
                if body.get("phase") == "voice":
                    content_type = str(body.get("contentType", "audio/webm")).split(";")[0]
                    if content_type not in AUDIO_EXT:
                        return _resp(400, {"error": f"unsupported audio type {content_type}"})
                    phase, ext = "voice", AUDIO_EXT[content_type]
                else:
                    phase, ext = "proof", "jpg"
                n = int(time.time())
            else:
                # A homework photo is always marked afresh; a room needs a before and then after photos.
                phase = "before" if q.get("kind") == "study" or q["stage"] in ("Ready", "AwaitingBefore") else "after"
                n, ext = q.get("attempt", 0) + (1 if phase == "after" else 0), "jpg"
            key = f"uploads/{code}/{q['id']}/{phase}-{n}.{ext}"
            return _resp(200, {"uploadUrl": store.presign_put(key, content_type), "key": key, "phase": phase,
                               "contentType": content_type})

        m = re.fullmatch(r"/household/(\w+)/quests/(\w+)/checkin", path)
        if m and method == "POST":  # kid: "done!" (a tick or a typed note) for a daily task
            h = store.load(m[1])
            r = family.checkin(h, store.quest(h, m[2]), body["kidId"], str(body.get("note", "")))
            store.save(h)
            return _resp(200, r)

        m = re.fullmatch(r"/household/(\w+)/habits(?:/(\w+)/(delete))?", path)
        if m and method == "POST":  # parent: add or delete a daily task
            h = store.load(m[1])
            if not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            if m[3] == "delete":
                family.delete_habit(h, m[2])
                r = {"ok": True}
            else:
                r = family.add_habit(h, body)
            store.save(h)
            return _resp(200, r)

        m = re.fullmatch(r"/household/(\w+)/kids/(\w+)/profile", path)
        if m and method == "POST":  # parent: grade, subjects and upcoming tests shape Family Quiz Night
            h = store.load(m[1])
            if not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            r = family.set_profile(h, m[2], body)
            store.save(h)
            return _resp(200, r)

        m = re.fullmatch(r"/household/(\w+)/settings", path)
        if m and method == "POST":  # parent: TV minutes, TV needs approval, pocket money per coin
            h = store.load(m[1])
            if not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            r = family.set_settings(h, body)
            store.save(h)
            return _resp(200, r)

        m = re.fullmatch(r"/household/(\w+)/wishlist(?:/(\w+)/(request|approve|bought|delete))?", path)
        if m and method == "POST":  # wish list: parents add/approve/mark bought; a kid asks when they've saved enough
            h = store.load(m[1])
            action = m[3] or "add"
            if action != "request" and not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            if action == "add":
                r = family.wish_add(h, body)
            elif action == "request":
                r = family.wish_request(h, m[2], body["kidId"])
            elif action == "approve":
                r = family.wish_approve(h, m[2])
            elif action == "bought":
                r = family.wish_bought(h, m[2])
            else:
                family.wish_delete(h, m[2])
                r = {"ok": True}
            store.save(h)
            return _resp(200, r)

        m = re.fullmatch(r"/household/(\w+)/tvtime(?:/(request|approve|use))?", path)
        if m:  # TV time: earned when today's must-do tasks are done
            h = store.load(m[1])
            family.daily(h)
            action = m[2]
            if method == "GET" or action is None:
                return _resp(200, family.tv_status(h))
            if action == "approve" and not _is_parent(event, h):
                return _resp(403, {"error": "parent PIN required"})
            r = {"request": family.tv_request, "approve": family.tv_approve}.get(action, None)
            r = r(h) if r else family.tv_use(h, int(body.get("minutes", 0)))
            store.save(h)
            return _resp(200, r)

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
        m = re.fullmatch(r"uploads/(\w+)/(\w+)/(before|after|voice|proof)-(\d+)\.(\w+)", key)
        if not m:
            continue
        code, qid, phase, n, ext = m[1], m[2], m[3], int(m[4]), m[5]
        h = store.load(code)
        q = store.quest(h, qid)
        if phase in ("voice", "proof"):
            _habit_proof(code, h, q, phase, key, ext)
            continue
        if q.get("kind") == "study":
            q.update(stage="Scanning", beforeKey=key, homework=None, practice=[], answers={})
            voice.set_line(q, "The AI Tutor is reading your work…")
        elif phase == "before":
            q.update(stage="Scanning", beforeKey=key, afterKey=None, things=[], attempt=0)
            voice.set_line(q, "Looking closely…")
        else:
            q.update(stage="Verifying", afterKey=key, attempt=n)
            voice.set_line(q, "Checking on everyone…")
        store.save(h)
        log.info("crew run %s %s %s", code, qid, phase)
        crew.run(code, qid, phase)



def _habit_proof(code: str, h: dict, q: dict, phase: str, key: str, ext: str) -> None:
    """A daily task's voice note or photo: kept as a memory, checked honestly, and the task finishes only if it's real."""
    k = store.kid(h, q["kidId"])
    q["stage"] = "Checking"
    voice.set_line(q, "Listening to your voice note…" if phase == "voice" else "Looking at your photo…")
    store.save(h)
    kept = store.keep(key, f"memories/{code}/{q['id']}-{int(time.time())}.{ext}")
    data = store.photo_bytes(key)
    if phase == "voice":
        mime = next((t for t, e in AUDIO_EXT.items() if e == ext), "audio/webm")
        try:
            heard = vision.listen(data, mime, q["title"], k["age"], k["name"])
        except Exception:  # noqa: BLE001 - keep the note for the parents even if the model can't hear it
            log.exception("listen failed")
            heard = {"transcript": "", "summary": "", "about": False, "mood": ""}
        h = store.load(code)
        q = store.quest(h, q["id"])
        r = family.voice_note(h, q, heard, kept)
        line = (f"I heard you, {k['name']}. {heard['summary']}" if r["stage"] != "Ready"
                else f"I couldn't hear anything about {q['title'].lower()} in that note. Try again?")
    else:
        verdict = vision.check_proof(data, q["title"])
        h = store.load(code)
        q = store.quest(h, q["id"])
        r = family.photo_proof(h, q, verdict, kept)
        line = (f"Well done, {k['name']}!" if r["stage"] != "Ready"
                else f"That photo shows {verdict['what_i_see'].rstrip('.').lower() or 'something else'}. Can you snap {q['title'].lower()} instead?")
    voice.set_line(q, line[:200])
    store.save(h)


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
        family.daily(h)
        if job == "streak":
            for kid in h["kids"]:
                left = [q["title"] for q in h["quests"] if q["kidId"] == kid["id"] and q.get("mustDo") and q["stage"] != "Done"]
                if left and kid.get("streak", 0) > 0:
                    notify.emit(h, "streak_risk", f"{kid['name']}'s {kid['streak']}-day streak is at risk 🔥",
                                f"Still to do today: {', '.join(left[:3])}.", by="Rosetta", data={"kidId": kid["id"]})
        elif job == "digest":
            for kid in h["kids"]:
                if not any(r["kidId"] == kid["id"] for r in h.get("history", [])):
                    continue  # nothing real to say yet
                d = family.refresh_coach(h, kid["id"])
                if d:
                    notify.emit(h, "weekly_digest", f"{kid['name']}'s week: {d['headline']}", " · ".join(d["notes"]),
                                by="Rosetta", data={"kidId": kid["id"]})
        store.save(h)
