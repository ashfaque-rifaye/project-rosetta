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

from . import config

_ddb = boto3.resource("dynamodb", region_name=config.REGION)
_s3 = boto3.client("s3", region_name=config.REGION)


def _table():
    return _ddb.Table(config.TABLE)


def load(code: str) -> dict:
    item = _table().get_item(Key={"code": code}).get("Item")
    if not item:
        raise KeyError(code)
    return json.loads(item["doc"])


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


def presign_put(key: str) -> str:
    return _s3.generate_presigned_url(
        "put_object", Params={"Bucket": config.BUCKET, "Key": key, "ContentType": "image/jpeg"}, ExpiresIn=600,
    )


def public_view(h: dict) -> dict:
    """What the TV and phones receive: photo keys swapped for short-lived URLs."""
    v = copy.deepcopy(h)
    v.pop("parentPin", None)  # never sent to devices
    for p in v.get("parents", []):
        p.pop("push", None); p.pop("email", None); p.pop("phone", None)
    for q in v["quests"]:
        if q.get("beforeKey"):
            q["beforeUrl"] = presign_get(q.pop("beforeKey"))
        if q.get("afterKey"):
            q["afterUrl"] = presign_get(q.pop("afterKey"))
    return v


# The same demo household the TV bundles, so live and demo modes look identical.
def seed(code: str = "R4KX") -> dict:
    glyphs = [
        ("g_bed", "Bed Maker", "bed", "meera"), ("g_books", "Book Keeper", "books", "arjun"),
        ("g_shirt", "Fold Master", "shirt", "meera"), ("g_plant", "Plant Guardian", "plant", "arjun"),
        ("g_broom", "Sweep Hero", "broom", "arjun"), ("g_dish", "Dish Hero", "dish", "meera"),
        ("g_package", "Package Detective", "package", "arjun"), ("g_shoes", "Shoe Parade", "shoes", "meera"),
        ("g_scholar", "Homework Hero", "scholar", ""),
    ]
    h = {
        "code": code,
        "name": "The Rao household",
        "kids": [{"id": "arjun", "name": "Arjun", "age": 8}, {"id": "meera", "name": "Meera", "age": 6}],
        "rooms": [
            {"id": "arjun_room", "name": "Arjun's room", "rect": [0.282, 0.280, 0.500, 0.521], "ownerId": "arjun"},
            {"id": "meera_room", "name": "Meera's room", "rect": [0.509, 0.280, 0.723, 0.521], "ownerId": "meera"},
            {"id": "kitchen", "name": "Kitchen", "rect": [0.282, 0.540, 0.500, 0.820]},
            {"id": "living", "name": "Living room", "rect": [0.509, 0.540, 0.723, 0.820]},
            {"id": "balcony", "name": "Balcony", "rect": [0.736, 0.378, 0.807, 0.540]},
        ],
        "quests": [
            {"id": "q_arjun_homework", "kind": "study", "kidId": "arjun", "roomId": "arjun_room", "title": "Fractions homework", "glyphId": "g_scholar"},
            {"id": "q_meera_homework", "kind": "study", "kidId": "meera", "roomId": "meera_room", "title": "Adding and taking away", "glyphId": "next_counter"},
            {"id": "q_arjun_room", "kidId": "arjun", "roomId": "arjun_room", "title": "Rescue the Lost Things", "glyphId": "next_rescue"},
            {"id": "q_balcony", "kidId": "meera", "roomId": "balcony", "title": "Water the balcony garden", "glyphId": "next_garden"},
            {"id": "q_kitchen", "kidId": "arjun", "roomId": "kitchen", "title": "Kitchen crew: clear the table", "glyphId": "next_crew"},
        ],
        "glyphs": [{"id": i, "name": n, "asset": a, "earnedBy": e} for i, n, a, e in glyphs],
        # Parent PIN gates approvals, settings and parent messages; change it from Mission Control in a real home.
        "parentPin": os.environ.get("ROSETTA_PARENT_PIN", "2468"),
        "parents": [
            {"id": "mom", "name": "Mom", "push": [], "alerts": {"push": True, "email": False, "sms": False}},
            {"id": "dad", "name": "Dad", "push": [], "alerts": {"push": True, "email": False, "sms": False}},
        ],
        "feed": [], "emotes": [], "history": [],
    }
    for q in h["quests"]:
        q.setdefault("kind", "tidy")
        q.update(stage="Ready", things=[], keeperLine="", confidence=1.0, attempt=0)
    save(h)
    return h
