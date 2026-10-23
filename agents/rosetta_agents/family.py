"""The family's progress, computed on the server so the TV and both phones always show the same real numbers.

  ensure(h)         upgrade an older household document in place (honest zero defaults, never sample data)
  daily(h)          today's habit tasks from the parents' habits; yesterday's leave the board (history keeps them)
  finish(h, q)      a task is done: XP, level, coins, pocket money, streaks, a family star, a stamp, the Squad
  tv_status(h)      today's must-do tasks -> TV time
  checkin / voice_note / photo_proof   how a child shows a habit is done
  wishlist and TV-time actions, learning profiles, settings

Nothing here invents a number: everything comes from something a child actually did.
"""
from __future__ import annotations

import datetime
import time

from . import notify

IST = datetime.timezone(datetime.timedelta(hours=5, minutes=30))
STAR_GOAL = 9
DEFAULT_REWARD = {"study": {"xp": 80, "coins": 35}, "tidy": {"xp": 75, "coins": 30}, "habit": {"xp": 20, "coins": 10}}
# Stamp art that ships with the apps: assets/flow/stamps/<art>.webp
STAMPS = {"study": ("s_homework", "Homework Hero", "homework"), "tidy": ("s_rescue", "Room Rescuer", "rescue"),
          "kitchen": ("s_kitchen", "Kitchen Crew", "kitchen"), "balcony": ("s_garden", "Garden Keeper", "garden"),
          "habit": ("s_daily", "Daily Hero", "night"), "quiz": ("s_quiz", "Quiz Night Star", "parcel")}
RARITY = [(12, "legendary"), (6, "epic"), (3, "rare"), (0, "common")]
MEMORY_DAYS = 90


def now_ms() -> int:
    return int(time.time() * 1000)


def today() -> str:
    return datetime.datetime.now(IST).date().isoformat()


def day_of(ms: int) -> str:
    return datetime.datetime.fromtimestamp(ms / 1000, IST).date().isoformat()


def xp_for_next(level: int) -> int:
    return 100 + (level - 1) * 50


def _new_id(prefix: str, existing: list[dict]) -> str:
    """Unique even when a parent taps several presets within the same millisecond."""
    taken = {x["id"] for x in existing}
    n = now_ms() % 10**9
    while f"{prefix}{n}" in taken:
        n += 1
    return f"{prefix}{n}"


def kid(h: dict, kid_id: str) -> dict:
    return next(k for k in h["kids"] if k["id"] == kid_id)


# ---------------------------------------------------------------- document shape
def ensure(h: dict) -> dict:
    for p in h.setdefault("parents", []):
        p.setdefault("avatar", p["id"])
    for k in h["kids"]:
        for f, v in (("grade", ""), ("avatar", k["id"]), ("level", 1), ("xp", 0), ("coins", 0), ("money", 0),
                     ("streak", 0), ("subjects", []), ("upcomingTests", [])):
            k.setdefault(f, v)
    s = h.setdefault("settings", {})
    for f, v in (("tvMinutes", 45), ("tvNeedsApproval", False), ("moneyPerCoin", 0), ("currency", "₹")):
        s.setdefault(f, v)
    for k in h["kids"]:
        k.setdefault("owned", [])
        k.setdefault("equipped", None)
    for f in ("habits", "memories", "wishlist", "stars", "squad", "stamps", "history", "feed", "emotes", "quests", "decor"):
        h.setdefault(f, [])
    h.setdefault("starsGoal", STAR_GOAL)
    h.setdefault("digest", {})
    h.pop("glyphs", None)  # v1's pre-filled Family Stone; v2 earns stars from nothing
    return h


def daily(h: dict) -> bool:
    """Make today's habit tasks; drop habit tasks from earlier days. Returns True if the document changed."""
    d = today()
    before = len(h["quests"])
    h["quests"] = [q for q in h["quests"] if q.get("kind") != "habit" or q.get("due") == d]
    changed = len(h["quests"]) != before
    weekend = datetime.datetime.now(IST).weekday() >= 5
    for hb in h["habits"]:
        if hb.get("days") == "weekdays" and weekend:
            continue
        for kid_id in hb.get("kidIds", []):
            qid = f"h_{hb['id']}_{kid_id}_{d.replace('-', '')}"
            if any(q["id"] == qid for q in h["quests"]):
                continue
            h["quests"].append(habit_quest(hb, kid_id, qid, d))
            changed = True
    t = h.get("tvTime") or {}
    if t.get("date") != d:
        h["tvTime"] = {"date": d, "approved": False, "requested": False, "minutesUsed": 0}
        changed = True
    return changed


def habit_quest(hb: dict, kid_id: str, qid: str, d: str) -> dict:
    return {"id": qid, "kind": "habit", "habitId": hb["id"], "kidId": kid_id, "roomId": "", "title": hb["title"],
            "icon": hb.get("icon", ""), "proof": hb.get("proof", "none"), "mustDo": bool(hb.get("mustDo", True)),
            "parentCheck": bool(hb.get("needsParent", False)), "needsParent": False,
            "reward": {"xp": DEFAULT_REWARD["habit"]["xp"], "coins": int(hb.get("coins", 10)), "money": float(hb.get("money", 0))},
            "due": d, "stage": "Ready", "things": [], "keeperLine": "", "confidence": 1.0, "attempt": 0}


# ---------------------------------------------------------------- finishing a task
def finish(h: dict, q: dict) -> dict:
    """Pay out a finished task once: XP and level, coins, pocket money, streaks, a family star, a stamp, the Squad."""
    if q.get("rewardedAt"):
        return q.get("lastReward", {})
    kind = q.get("kind", "tidy")
    base = DEFAULT_REWARD.get(kind, DEFAULT_REWARD["tidy"])
    r = q.get("reward") or {}
    xp, coins = int(r.get("xp", base["xp"])), int(r.get("coins", base["coins"]))
    money = float(r.get("money") or 0) or round(coins * float(h["settings"].get("moneyPerCoin", 0)), 2)
    k = kid(h, q["kidId"])
    level_up = grant(k, xp, coins, money)
    _history(h, q, xp, coins, money)
    k["streak"] = _day_streak(h, k["id"])
    habit_streak = None
    if kind == "habit":
        hb = next((x for x in h["habits"] if x["id"] == q.get("habitId")), None)
        if hb is not None:
            habit_streak = _day_streak(h, k["id"], habit_id=hb["id"])
            hb.setdefault("streaks", {})[k["id"]] = habit_streak
    star = _star(h, q, k)
    stamp = _stamp(h, q, k)
    if kind == "tidy":
        _squad(h, q, k)
    _wish_ready(h, k)
    q["lastReward"] = {"xp": xp, "coins": coins, "money": money, "star": star, "levelUp": level_up,
                       "level": k["level"], "streak": k["streak"], "habitStreak": habit_streak, "stamp": stamp}
    q["rewardedAt"] = now_ms()
    if coach_due(h, k["id"]):
        refresh_coach(h, k["id"])
    return q["lastReward"]


def grant(k: dict, xp: int, coins: int, money: float = 0) -> bool:
    k["xp"] += xp
    level_up = False
    while k["xp"] >= xp_for_next(k["level"]):
        k["xp"] -= xp_for_next(k["level"])
        k["level"] += 1
        level_up = True
    k["coins"] += coins
    k["money"] = round(float(k["money"]) + money, 2)
    return level_up


def _history(h: dict, q: dict, xp: int, coins: int, money: float) -> None:
    now = now_ms()
    opened = q.get("openedAtMs") or now
    row = {"kidId": q["kidId"], "questId": q["id"], "kind": q.get("kind", "tidy"), "title": q.get("title", ""),
           "habitId": q.get("habitId", ""), "roomId": q.get("roomId", ""), "atMs": now, "day": day_of(now),
           "minutes": max(1, (now - opened) // 60000), "items": len(q.get("things", [])),
           "xp": xp, "coins": coins, "money": money}
    if q.get("kind") == "study" and q.get("homework"):
        items = q["homework"]["items"]
        row.update(subject=q["homework"]["subject"], topic=q["homework"]["topic"],
                   right=sum(1 for i in items if i["correct"]), total=len(items),
                   practiceRight=sum(1 for v in q.get("answers", {}).values() if v.get("correct")),
                   practiceTotal=len(q.get("practice", [])),
                   skills=sorted({i["skill"] for i in items if not i["correct"]}))
    h["history"] = (h["history"] + [row])[-1000:]


def _day_streak(h: dict, kid_id: str, habit_id: str | None = None) -> int:
    days = {r.get("day") or day_of(r["atMs"]) for r in h["history"]
            if r["kidId"] == kid_id and (habit_id is None or r.get("habitId") == habit_id)}
    d = datetime.datetime.now(IST).date()
    n = 0
    while (d - datetime.timedelta(days=n)).isoformat() in days:
        n += 1
    return n


def _star(h: dict, q: dict, k: dict) -> bool:
    """Homework and tidy-ups add a star each; habits add one when a child finishes all of today's habits."""
    if len(h["stars"]) >= h.get("starsGoal", STAR_GOAL):
        return False
    title = q.get("title", "")
    if q.get("kind") == "habit":
        mine = [x for x in h["quests"] if x.get("kind") == "habit" and x["kidId"] == k["id"] and x.get("due") == today()]
        if not mine or any(x["stage"] != "Done" for x in mine):
            return False
        title = f"All of {k['name']}'s daily tasks"
    h["stars"].append({"id": f"star{len(h['stars']) + 1}", "earnedBy": k["id"], "questId": q["id"], "title": title, "atMs": now_ms()})
    if len(h["stars"]) == h.get("starsGoal", STAR_GOAL):
        notify.emit(h, "goal_done", "Movie Night unlocked 🍿", "The family filled all the stars together.", by="Rosetta")
    return True


def _stamp(h: dict, q: dict, k: dict) -> dict | None:
    key = q.get("roomId") if q.get("roomId") in ("kitchen", "balcony") else q.get("kind", "tidy")
    sid, name, art = STAMPS.get(key, STAMPS["tidy"])
    if any(s["id"] == sid and s["earnedBy"] == k["id"] for s in h["stamps"]):
        return None
    s = {"id": sid, "name": name, "art": art, "earnedBy": k["id"], "date": today()}
    h["stamps"].append(s)
    return s


def _squad(h: dict, q: dict, k: dict) -> None:
    for t in q.get("things", []):
        if not t.get("returned"):
            continue
        e = next((x for x in h["squad"] if x["character"] == t["character"]), None)
        if e is None:
            e = {"character": t["character"], "name": t["name"], "rescues": 0, "firstBy": k["id"], "rarity": "common"}
            h["squad"].append(e)
        e["rescues"] += 1
        e["rarity"] = next(name for n, name in RARITY if e["rescues"] >= n)


# ---------------------------------------------------------------- TV time
def tv_status(h: dict) -> dict:
    d = today()
    must = [q for q in h["quests"] if q.get("mustDo") and (q.get("kind") != "habit" or q.get("due") == d)]
    done = [q for q in must if q["stage"] == "Done"]
    s, t = h["settings"], h.get("tvTime") or {}
    all_done = len(done) == len(must)
    approved = bool(t.get("approved")) or not s["tvNeedsApproval"]
    granted = bool(t.get("granted"))  # a parent unlocked today's TV time themselves
    unlocked = (all_done and approved) or granted
    used = int(t.get("minutesUsed", 0))
    return {"date": d, "mustDo": len(must), "done": len(done), "allDone": all_done, "unlocked": unlocked,
            "needsApproval": bool(s["tvNeedsApproval"]), "approved": bool(t.get("approved")),
            "requested": bool(t.get("requested")), "granted": granted, "tvMinutes": int(s["tvMinutes"]),
            "minutesLeft": max(0, int(s["tvMinutes"]) - used) if unlocked else int(s["tvMinutes"]),
            "waitingOn": [{"questId": q["id"], "kidId": q["kidId"], "title": q["title"]} for q in must if q["stage"] != "Done"]}


def tv_request(h: dict) -> dict:
    st = tv_status(h)
    if not st["allDone"]:
        raise ValueError("finish today's must-do tasks first")
    if st["needsApproval"] and not st["approved"] and not st["requested"]:
        h["tvTime"]["requested"] = True
        notify.emit(h, "tv_request", "TV time? 📺", f"All {st['mustDo']} must-do tasks are done today. Unlock {st['tvMinutes']} minutes?",
                    by="Rosetta", data={"action": "tv"})
    return tv_status(h)


def tv_approve(h: dict) -> dict:
    h["tvTime"].update(approved=True, requested=False)
    notify.emit(h, "tv_unlocked", "TV time unlocked 📺", f"{h['settings']['tvMinutes']} minutes, earned today.", by="parent", notify=False)
    return tv_status(h)


def tv_grant(h: dict, on: bool = True) -> dict:
    """A parent unlocks today's TV time from their phone (or locks it again), whatever the tasks say."""
    h["tvTime"].update(granted=on, requested=False)
    if on:
        notify.emit(h, "tv_unlocked", "TV time unlocked 📺", f"{h['settings']['tvMinutes']} minutes, from a grown-up.", by="parent", notify=False)
    return tv_status(h)


def tv_use(h: dict, minutes: int) -> dict:
    h["tvTime"]["minutesUsed"] = int(h["tvTime"].get("minutesUsed", 0)) + max(0, int(minutes))
    return tv_status(h)


# ---------------------------------------------------------------- habits: how a child shows it's done
def checkin(h: dict, q: dict, kid_id: str, note: str = "") -> dict:
    """Tick or typed note. Voice notes and photos come through the upload and land in voice_note / photo_proof."""
    if q.get("kind") != "habit":
        raise ValueError("check-ins are for daily tasks")
    if q["kidId"] != kid_id:
        raise ValueError("this task belongs to someone else")
    if q["stage"] in ("Done", "Checking"):
        return {"stage": q["stage"], "repeat": True}
    note = note.strip()[:500]
    if q["proof"] == "note" and not note:
        raise ValueError("write a short note first")
    if q["proof"] in ("voice", "photo") and not note:
        raise ValueError(f"send a {'voice note' if q['proof'] == 'voice' else 'photo'} for this one")
    if note:
        _memory(h, q, "note", text=note)
    q["checkin"] = {"note": note, "atMs": now_ms()}
    return complete_habit(h, q, note)


def voice_note(h: dict, q: dict, heard: dict, audio_key: str) -> dict:
    """A transcribed voice note (vision.listen). It only counts if it is really about the task."""
    _memory(h, q, "voice", transcript=heard.get("transcript", ""), summary=heard.get("summary", ""),
            mood=heard.get("mood", ""), audioKey=audio_key)
    q["checkin"] = {"transcript": heard.get("transcript", ""), "summary": heard.get("summary", ""),
                    "audioKey": audio_key, "atMs": now_ms()}
    if not heard.get("about"):
        q["stage"] = "Ready"
        q["whatISee"] = f"Voice note: {heard.get('transcript') or 'nothing I could hear'}"
        return {"stage": "Ready", "heard": False}
    q.pop("whatISee", None)
    return complete_habit(h, q, heard.get("summary", ""))


def photo_proof(h: dict, q: dict, verdict: dict, photo_key: str) -> dict:
    """A photo checked honestly (vision.check_proof)."""
    _memory(h, q, "photo", summary=verdict.get("what_i_see", ""), photoKey=photo_key)
    q["checkin"] = {"photoKey": photo_key, "summary": verdict.get("what_i_see", ""), "atMs": now_ms()}
    if not verdict.get("done"):
        q["stage"] = "Ready"
        q["whatISee"] = verdict.get("what_i_see", "")
        return {"stage": "Ready", "done": False}
    q.pop("whatISee", None)
    return complete_habit(h, q, verdict.get("what_i_see", ""))


def complete_habit(h: dict, q: dict, detail: str = "") -> dict:
    k = kid(h, q["kidId"])
    if q.get("parentCheck"):
        q.update(stage="Checking", needsParent=True, parentReason="This task needs a grown-up's OK")
        notify.emit(h, "needs_approval", f"Did {k['name']} “{q['title']}”?", detail or "Tap to check and approve.",
                    by=k["id"], data={"questId": q["id"], "kidId": k["id"]})
        return {"stage": "Checking"}
    q["stage"] = "Done"
    r = finish(h, q)
    streak = r.get("habitStreak") or 1
    notify.emit(h, "habit_done", f"{k['name']} did “{q['title']}” ✅",
                (detail + " · " if detail else "") + f"{streak}-day streak", by=k["id"],
                data={"questId": q["id"], "kidId": k["id"]})
    return {"stage": "Done", "reward": r}


def _memory(h: dict, q: dict, kind: str, **fields) -> dict:
    m = {"id": f"m{now_ms()}{len(h['memories'])}", "kidId": q["kidId"], "habitId": q.get("habitId", ""),
         "questId": q["id"], "kind": kind, "title": q.get("title", ""), "atMs": now_ms(), **fields}
    cutoff = now_ms() - MEMORY_DAYS * 24 * 3600 * 1000
    h["memories"] = [x for x in h["memories"] if x["atMs"] > cutoff][-300:] + [m]
    return m


# ---------------------------------------------------------------- parents: habits, profiles, settings, wish list
def add_habit(h: dict, body: dict) -> dict:
    proof = body.get("proof", "none")
    hb = {"id": _new_id("hb", h["habits"]), "title": str(body["title"]).strip()[:40], "icon": str(body.get("icon", ""))[:4],
          "kidIds": [k for k in body.get("kidIds", []) if any(x["id"] == k for x in h["kids"])],
          "days": "weekdays" if body.get("days") == "weekdays" else "daily",
          "proof": proof if proof in ("none", "note", "voice", "photo") else "none",
          "coins": max(0, int(body.get("coins", 10))), "money": max(0.0, float(body.get("money", 0))),
          "needsParent": bool(body.get("needsParent", False)), "mustDo": bool(body.get("mustDo", True)), "streaks": {}}
    if not hb["title"] or not hb["kidIds"]:
        raise ValueError("a habit needs a title and at least one child")
    h["habits"].append(hb)
    daily(h)
    names = ", ".join(kid(h, k)["name"] for k in hb["kidIds"])
    notify.emit(h, "new_habit", f"New daily task: {hb['title']}", f"For {names}", by="parent", notify=False)
    return hb


def delete_habit(h: dict, habit_id: str) -> None:
    h["habits"] = [x for x in h["habits"] if x["id"] != habit_id]
    h["quests"] = [q for q in h["quests"] if not (q.get("habitId") == habit_id and q["stage"] != "Done")]


def set_profile(h: dict, kid_id: str, body: dict) -> dict:
    k = kid(h, kid_id)
    if "grade" in body:
        k["grade"] = str(body["grade"])[:20]
    if "subjects" in body:
        k["subjects"] = [str(s)[:30] for s in body["subjects"]][:10]
    if "upcomingTests" in body:
        k["upcomingTests"] = [{"subject": str(t.get("subject", ""))[:30], "topics": str(t.get("topics", ""))[:120],
                               "date": str(t.get("date", ""))[:10]} for t in body["upcomingTests"]][:6]
    return k


def set_settings(h: dict, body: dict) -> dict:
    s = h["settings"]
    if "tvMinutes" in body:
        s["tvMinutes"] = max(0, min(240, int(body["tvMinutes"])))
    if "tvNeedsApproval" in body:
        s["tvNeedsApproval"] = bool(body["tvNeedsApproval"])
    if "moneyPerCoin" in body:
        s["moneyPerCoin"] = max(0.0, float(body["moneyPerCoin"]))
    return s


def wish_add(h: dict, body: dict) -> dict:
    kid(h, body["kidId"])
    w = {"id": _new_id("w", h["wishlist"]), "kidId": body["kidId"], "title": str(body["title"]).strip()[:60],
         "price": max(0.0, float(body["price"])), "link": str(body.get("link", ""))[:300], "status": "saving",
         "createdAt": now_ms()}
    if not w["title"]:
        raise ValueError("a wish needs a name")
    h["wishlist"].append(w)
    return w


def _wish(h: dict, wish_id: str) -> dict:
    return next(w for w in h["wishlist"] if w["id"] == wish_id)


def wish_request(h: dict, wish_id: str, kid_id: str) -> dict:
    w, k = _wish(h, wish_id), kid(h, kid_id)
    if w["kidId"] != k["id"]:
        raise ValueError("that wish belongs to someone else")
    if w["status"] != "saving":
        return w
    if k["money"] < w["price"]:
        raise ValueError(f"{h['settings']['currency']}{w['price'] - k['money']:.0f} more to go")
    w["status"] = "requested"
    notify.emit(h, "wish_request", f"{k['name']} saved up for “{w['title']}” 🎁",
                f"{h['settings']['currency']}{w['price']:.0f} of pocket money earned. Approve?", by=k["id"],
                data={"action": "wish", "wishId": w["id"], "kidId": k["id"]})
    return w


def wish_approve(h: dict, wish_id: str) -> dict:
    w = _wish(h, wish_id)
    if w["status"] in ("saving", "requested"):
        k = kid(h, w["kidId"])
        k["money"] = round(max(0.0, float(k["money"]) - w["price"]), 2)
        w["status"] = "approved"
        notify.emit(h, "wish_approved", f"{k['name']}'s wish is coming 🎁", w["title"], by="parent", notify=False)
    return w


def wish_bought(h: dict, wish_id: str) -> dict:
    w = _wish(h, wish_id)
    w["status"] = "bought"
    return w


def wish_delete(h: dict, wish_id: str) -> None:
    h["wishlist"] = [w for w in h["wishlist"] if w["id"] != wish_id]


def _wish_ready(h: dict, k: dict) -> None:
    for w in h["wishlist"]:
        if w["kidId"] == k["id"] and w["status"] == "saving" and k["money"] >= w["price"] and not w.get("readyNotified"):
            w["readyNotified"] = True
            notify.emit(h, "wish_ready", f"{k['name']} can afford “{w['title']}” 🎉",
                        "Their pocket money reached it. They can ask you from their phone.", by="Rosetta",
                        data={"action": "wish", "wishId": w["id"], "kidId": k["id"]})


# ---------------------------------------------------------------- the TV's coin shop (outfits and house decor)
def buy(h: dict, kid_id: str, body: dict) -> dict:
    """The TV owns the catalogue (art and prices); the server owns the coins, so a purchase is spent once, for real."""
    k = kid(h, kid_id)
    item, price = str(body["itemId"])[:30], max(0, int(body["price"]))
    owned = k.setdefault("owned", [])
    decor = h.setdefault("decor", [])
    if item in owned or item in decor:
        return k
    if k["coins"] < price:
        raise ValueError("not enough coins yet")
    k["coins"] -= price
    if body.get("kind") == "decor":
        decor.append(item)
    else:
        owned.append(item)
        k.update(equipped=item, avatar=str(body.get("avatar") or k["avatar"])[:40])
    return k


def equip(h: dict, kid_id: str, body: dict) -> dict:
    k = kid(h, kid_id)
    item = body.get("itemId")
    if item and item not in k.get("owned", []):
        raise ValueError("buy it first")
    k.update(equipped=item, avatar=str(body.get("avatar") or k["id"])[:40])
    return k


# ---------------------------------------------------------------- Coach's notes (from real history only)
def coach_stats(h: dict, kid_id: str) -> dict:
    week = now_ms() - 7 * 24 * 3600 * 1000
    rows = [r for r in h["history"] if r["kidId"] == kid_id and r["atMs"] > week]
    study = [r for r in rows if r.get("kind") == "study" and r.get("total")]
    k = kid(h, kid_id)
    return {
        "tasks_done_this_week": len(rows),
        "days_active_this_week": len({r.get("day") for r in rows}),
        "homework": [{"topic": r["topic"], "right": r["right"], "total": r["total"],
                      "practice": f"{r.get('practiceRight', 0)}/{r.get('practiceTotal', 0)}"} for r in study],
        "skills_to_practise": sorted({s for r in study for s in r.get("skills", [])}),
        "habits": {hb["title"]: {"streak_days": hb.get("streaks", {}).get(kid_id, 0),
                                 "done_this_week": sum(1 for r in rows if r.get("habitId") == hb["id"])}
                   for hb in h["habits"] if kid_id in hb.get("kidIds", [])},
        "tidy_ups_this_week": sum(1 for r in rows if r.get("kind") == "tidy"),
        "streak_days": k["streak"], "level": k["level"],
        "upcoming_tests": k.get("upcomingTests", []),
    }


def refresh_coach(h: dict, kid_id: str) -> dict | None:
    """✦ Coach's notes for parents, written by the model from coach_stats (never from sample data)."""
    from . import vision
    k = kid(h, kid_id)
    try:
        d = vision.digest(k["name"], k["age"], coach_stats(h, kid_id))
    except Exception:  # noqa: BLE001 - notes are a nice-to-have; the task still counts
        import logging
        logging.getLogger(__name__).exception("coach notes failed")
        return None
    h.setdefault("digest", {})[kid_id] = {"headline": d.get("headline", ""), "notes": d.get("notes", []), "atMs": now_ms()}
    return h["digest"][kid_id]


def coach_due(h: dict, kid_id: str) -> bool:
    """Notes only once there is something real to say: 3+ finished tasks, refreshed at most every 6 hours."""
    rows = [r for r in h["history"] if r["kidId"] == kid_id]
    last = (h.get("digest") or {}).get(kid_id, {}).get("atMs", 0)
    return len(rows) >= 3 and now_ms() - last > 6 * 3600 * 1000
