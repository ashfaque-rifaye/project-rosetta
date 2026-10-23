"""Household state: one DynamoDB item per household (keyed by its join code).

The document mirrors the TV's Kotlin model (Household / Quest / LostThing / Glyph) so the TV can
render it without translation. Photos stay in S3; the item only holds their keys.
"""
from __future__ import annotations

import copy
import json
import os
import time

import boto3

from . import config, family

_ddb = boto3.resource("dynamodb", region_name=config.REGION)
_s3 = boto3.client("s3", region_name=config.REGION)


def _table():
    return _ddb.Table(config.TABLE)


def load(code: str) -> dict:
    item = _table().get_item(Key={"code": code}).get("Item")
    if not item:
        raise KeyError(code)
    return family.ensure(json.loads(item["doc"]))


def save(h: dict) -> None:
    h["updatedAt"] = int(time.time() * 1000)
    _table().put_item(Item={"code": h["code"], "doc": json.dumps(h)})


def quest(h: dict, quest_id: str) -> dict:
    return next(q for q in h["quests"] if q["id"] == quest_id)


def kid(h: dict, kid_id: str) -> dict:
    return next(k for k in h["kids"] if k["id"] == kid_id)


def photo_bytes(key: str) -> bytes:
    return _s3.get_object(Bucket=config.BUCKET, Key=key)["Body"].read()


def presign_get(key: str) -> str:
    return _s3.generate_presigned_url("get_object", Params={"Bucket": config.BUCKET, "Key": key}, ExpiresIn=900)


def presign_put(key: str, content_type: str = "image/jpeg") -> str:
    return _s3.generate_presigned_url(
        "put_object", Params={"Bucket": config.BUCKET, "Key": key, "ContentType": content_type}, ExpiresIn=600,
    )


def keep(src_key: str, dst_key: str) -> str:
    """Copy an upload (deleted after a day) to memories/ (kept 90 days) so parents can replay it."""
    _s3.copy_object(Bucket=config.BUCKET, Key=dst_key, CopySource={"Bucket": config.BUCKET, "Key": src_key})
    return dst_key


def put_voice(key: str, wav: bytes) -> None:
    _s3.put_object(Bucket=config.BUCKET, Key=key, Body=wav, ContentType="audio/wav")


def voice_exists(key: str) -> bool:
    try:
        _s3.head_object(Bucket=config.BUCKET, Key=key)
        return True
    except _s3.exceptions.ClientError:
        return False


def public_view(h: dict) -> dict:
    """What the TV and phones receive: photo and audio keys swapped for short-lived URLs, TV time worked out."""
    v = copy.deepcopy(h)
    v["today"] = family.today()
    v["tvTime"] = family.tv_status(h)
    for k in v["kids"]:
        k["xpForNext"] = family.xp_for_next(k["level"])
    for m in v.get("memories", []):
        if m.get("audioKey"):
            m["audioUrl"] = presign_get(m.pop("audioKey"))
        if m.get("photoKey"):
            m["photoUrl"] = presign_get(m.pop("photoKey"))
    v.pop("parentPin", None)  # never sent to devices
    for p in v.get("parents", []):
        p.pop("push", None); p.pop("email", None); p.pop("phone", None)
    for q in v["quests"]:
        if q.get("beforeKey"):
            q["beforeUrl"] = presign_get(q.pop("beforeKey"))
        if q.get("afterKey"):
            q["afterUrl"] = presign_get(q.pop("afterKey"))
        c = q.get("checkin") or {}
        if c.get("audioKey"):
            c["audioUrl"] = presign_get(c.pop("audioKey"))
        if c.get("photoKey"):
            c["photoUrl"] = presign_get(c.pop("photoKey"))
        voice = q.pop("keeperVoice", None)  # a clip only counts for the exact line it speaks
        if voice and voice["line"] == q.get("keeperLine"):
            q["keeperAudioUrl"] = presign_get(voice["key"])
    for ev in v.get("feed", []):
        if ev.get("data", {}).get("audioKey"):
            ev["data"]["audioUrl"] = presign_get(ev["data"].pop("audioKey"))
    return v


# The demo family starts with nothing earned: every star, coin, streak and memory on screen is real.
def seed(code: str = "R4KX") -> dict:
    h = {
        "code": code,
        "name": "The Rao family",
        "kids": [{"id": "arjun", "name": "Arjun", "age": 8, "grade": "Grade 3"},
                 {"id": "meera", "name": "Meera", "age": 6, "grade": "Grade 1"}],
        "rooms": [
            {"id": "arjun_room", "name": "Arjun's room", "rect": [0.282, 0.280, 0.500, 0.521], "ownerId": "arjun"},
            {"id": "meera_room", "name": "Meera's room", "rect": [0.509, 0.280, 0.723, 0.521], "ownerId": "meera"},
            {"id": "kitchen", "name": "Kitchen", "rect": [0.282, 0.540, 0.500, 0.820]},
            {"id": "living", "name": "Living room", "rect": [0.509, 0.540, 0.723, 0.820]},
            {"id": "balcony", "name": "Balcony", "rect": [0.736, 0.378, 0.807, 0.540]},
        ],
        "quests": [
            {"id": "q_arjun_homework", "kind": "study", "kidId": "arjun", "roomId": "arjun_room", "title": "Fractions homework", "mustDo": True},
            {"id": "q_meera_homework", "kind": "study", "kidId": "meera", "roomId": "meera_room", "title": "Adding and taking away", "mustDo": True},
            {"id": "q_arjun_room", "kind": "tidy", "kidId": "arjun", "roomId": "arjun_room", "title": "Tidy your room", "mustDo": False},
            {"id": "q_meera_room", "kind": "tidy", "kidId": "meera", "roomId": "meera_room", "title": "Tidy your room", "mustDo": False},
            {"id": "q_kitchen", "kind": "tidy", "kidId": "arjun", "roomId": "kitchen", "title": "Kitchen crew: clear the table", "mustDo": False},
            {"id": "q_balcony", "kind": "tidy", "kidId": "meera", "roomId": "balcony", "title": "Water the balcony garden", "mustDo": False},
        ],
        # Everyday tasks a family starts with; parents edit or remove them from their phone. Progress still starts at zero.
        "habits": [
            {"id": "hb_bed", "title": "Make your bed", "icon": "🛏️", "kidIds": ["arjun", "meera"], "days": "daily", "proof": "photo",
             "coins": 10, "money": 2, "needsParent": False, "mustDo": True, "streaks": {}},
            {"id": "hb_read", "title": "Read 10 pages", "icon": "📖", "kidIds": ["arjun", "meera"], "days": "daily", "proof": "voice",
             "coins": 15, "money": 5, "needsParent": False, "mustDo": True, "streaks": {}},
            {"id": "hb_prayer", "title": "Prayer time", "icon": "🙏", "kidIds": ["arjun", "meera"], "days": "daily", "proof": "none",
             "coins": 5, "money": 0, "needsParent": False, "mustDo": False, "streaks": {}},
        ],
        # Parent PIN gates approvals, settings and parent messages; change it from Mission Control in a real home.
        "parentPin": os.environ.get("ROSETTA_PARENT_PIN", "2468"),
        "parents": [
            {"id": "mom", "name": "Mom", "avatar": "mom", "push": [], "alerts": {"push": True, "email": False, "sms": False}},
            {"id": "dad", "name": "Dad", "avatar": "dad", "push": [], "alerts": {"push": True, "email": False, "sms": False}},
        ],
        "feed": [], "emotes": [], "history": [],
    }
    for q in h["quests"]:
        q.update(stage="Ready", things=[], keeperLine="", confidence=1.0, attempt=0, proof="photo", due=family.today())
    family.ensure(h)
    family.daily(h)
    save(h)
    return h
