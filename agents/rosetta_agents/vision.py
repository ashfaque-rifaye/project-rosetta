"""The crew's eyes: model calls with images and forced JSON tool schemas (Gemini on Vertex by default, see gemini.py;
Bedrock Converse when ROSETTA_AI_PROVIDER=bedrock).

Each function is one specialist:
  scout()  - finds out-of-place items in the "before" photo, with boxes
  judge()  - compares "before" and "after" item by item, with a confidence score
  keeper() - writes the one warm sentence the TV speaks
Boxes use Amazon Nova's grounding convention (integers 0-1000) and are normalized to 0..1 here.
"""
from __future__ import annotations

import json
from typing import Any

import boto3

from . import config

_rt = boto3.client("bedrock-runtime", region_name=config.REGION)


def _converse(content: list[dict], tool: dict, system: str, max_tokens: int = 1500) -> dict[str, Any]:
    if config.AI_PROVIDER == "vertex":
        from . import gemini
        return gemini.structured(content, tool, system, purpose=tool["toolSpec"]["name"])
    resp = _rt.converse(
        modelId=config.MODEL_ID,
        system=[{"text": system}],
        messages=[{"role": "user", "content": content}],
        toolConfig={"tools": [tool], "toolChoice": {"tool": {"name": tool["toolSpec"]["name"]}}},
        inferenceConfig={"maxTokens": max_tokens, "temperature": 0.2},
    )
    for block in resp["output"]["message"]["content"]:
        if "toolUse" in block:
            return block["toolUse"]["input"]
    raise RuntimeError("model did not return structured output")


def _image(data: bytes) -> dict:
    fmt = "png" if data[:4] == b"\x89PNG" else ("webp" if data[8:12] == b"WEBP" else "jpeg")
    return {"image": {"format": fmt, "source": {"bytes": data}}}


def _norm_box(b: list[int | float]) -> list[float]:
    x1, y1, x2, y2 = (max(0.0, min(1000.0, float(v))) / 1000.0 for v in b[:4])
    return [round(min(x1, x2), 4), round(min(y1, y2), 4), round(max(x1, x2), 4), round(max(y1, y2), 4)]


# ---------------------------------------------------------------- Scout + Coach
SCOUT_TOOL = {
    "toolSpec": {
        "name": "report_lost_things",
        "description": "Report household objects that are out of place and a child could put away.",
        "inputSchema": {"json": {
            "type": "object",
            "required": ["same_kind_of_room", "items"],
            "properties": {
                "same_kind_of_room": {"type": "boolean", "description": "True if this is an indoor room in a home."},
                "items": {
                    "type": "array",
                    "maxItems": 6,
                    "items": {
                        "type": "object",
                        "required": ["id", "name", "character", "box", "home", "stars"],
                        "properties": {
                            "id": {"type": "string", "description": "short slug, e.g. sock, red_cup"},
                            "name": {"type": "string", "description": "playful 2-4 word name, e.g. 'A lonely sock'"},
                            "character": {"type": "string", "enum": list(config.CHARACTERS)},
                            "box": {"type": "array", "items": {"type": "integer"}, "minItems": 4, "maxItems": 4,
                                    "description": "[x1, y1, x2, y2] in 0-1000 image coordinates"},
                            "home": {"type": "string", "description": "where it belongs, e.g. 'the laundry basket'"},
                            "stars": {"type": "integer", "minimum": 1, "maximum": 2},
                        },
                    },
                },
            },
        }},
    }
}

SCOUT_SYSTEM = (
    "You are Scout, part of Rosetta, a family app that turns tidying into a gentle game for kids. "
    "Look at the photo of a child's room. List only objects that are clearly OUT OF PLACE (on the floor, "
    "thrown on furniture, left out) and that a {age}-year-old can put away safely. Ignore things that are "
    "already where they belong (books on shelves, toys in the toy box, clothes on hooks). Never list people, "
    "pets, or anything dangerous. Prefer the 3-6 most visible items. Choose the closest character for each: "
    + "; ".join(f"{k} = {v}" for k, v in config.CHARACTERS.items())
    + ". Boxes must tightly cover the object. Give 2 stars only for bigger jobs."
)


def scout(photo: bytes, kid_age: int) -> dict:
    out = _converse([_image(photo), {"text": "Find the Lost Things in this room."}], SCOUT_TOOL, SCOUT_SYSTEM.format(age=kid_age))
    items = []
    for it in out.get("items", [])[:6]:
        items.append({
            "id": it["id"], "name": it["name"],
            "character": it["character"] if it["character"] in config.CHARACTERS else "bricks",
            "box": _norm_box(it["box"]), "home": it["home"], "stars": int(it.get("stars", 1)), "returned": False,
        })
    return {"is_room": bool(out.get("same_kind_of_room", True)), "things": items}


# ---------------------------------------------------------------- Judge
JUDGE_TOOL = {
    "toolSpec": {
        "name": "report_verdict",
        "description": "Report which Lost Things are back home in the after photo.",
        "inputSchema": {"json": {
            "type": "object",
            "required": ["same_room", "changed", "results", "confidence"],
            "properties": {
                "same_room": {"type": "boolean", "description": "Both photos show the same room."},
                "changed": {"type": "boolean", "description": "The after photo is a new photo, not identical to before."},
                "results": {"type": "array", "items": {
                    "type": "object", "required": ["id", "returned"],
                    "properties": {"id": {"type": "string"}, "returned": {"type": "boolean"}},
                }},
                "confidence": {"type": "number", "minimum": 0, "maximum": 1},
            },
        }},
    }
}

JUDGE_SYSTEM = (
    "You are Judge in Rosetta. You get a BEFORE photo, an AFTER photo of a child's room, and a list of Lost Things "
    "found in BEFORE (with where they were). For each item decide if it is no longer left out in AFTER (put away, "
    "moved to its home). Be fair and kind: if an item is clearly gone from its old spot, count it as returned. "
    "If an item is still in the same place, it is not returned. Also check the photos show the same room and that "
    "AFTER is a genuinely new photo. Give an honest confidence."
)


def judge(before: bytes, after: bytes, things: list[dict]) -> dict:
    listing = json.dumps([{"id": t["id"], "name": t["name"], "box_0_1": t["box"]} for t in things])
    out = _converse(
        [{"text": "BEFORE:"}, _image(before), {"text": "AFTER:"}, _image(after), {"text": f"Lost Things: {listing}"}],
        JUDGE_TOOL, JUDGE_SYSTEM, max_tokens=800,
    )
    verdict = {r["id"]: bool(r["returned"]) for r in out.get("results", [])}
    return {
        "same_room": bool(out.get("same_room", True)),
        "changed": bool(out.get("changed", True)),
        "returned": {t["id"]: verdict.get(t["id"], t.get("returned", False)) for t in things},
        "confidence": float(out.get("confidence", 0.5)),
    }


# ---------------------------------------------------------------- Keeper
KEEPER_TOOL = {
    "toolSpec": {
        "name": "say",
        "description": "The single sentence the Keeper speaks on the TV.",
        "inputSchema": {"json": {"type": "object", "required": ["line"], "properties": {"line": {"type": "string"}}}},
    }
}

KEEPER_SYSTEM = (
    "You are the Keeper of the Family Stone in Rosetta: a calm, warm storyteller voice on the family TV. "
    "Write ONE sentence (max 24 words) for a {age}-year-old named {name}. For a tidy-up, messy objects are 'Lost Things' "
    "that miss their homes. For homework, praise only a skill listed under 'went well'; skills under 'to practise' are "
    "the ones they got wrong, so call them what the practice round is for and never praise them. Never give an answer "
    "and never mention Lost Things. Be specific about what happened, encouraging, a little magical, never shaming, "
    "never sarcastic, never about the child's character. No emojis."
)


def keeper(kid_name: str, kid_age: int, situation: str) -> str:
    out = _converse([{"text": situation}], KEEPER_TOOL, KEEPER_SYSTEM.format(age=kid_age, name=kid_name), max_tokens=200)
    return out.get("line", "").strip()


# ---------------------------------------------------------------- Digest (weekly Growth summary for parents)
DIGEST_TOOL = {
    "toolSpec": {
        "name": "write_digest",
        "description": "Weekly habit digest for parents.",
        "inputSchema": {"json": {
            "type": "object", "required": ["headline", "notes"],
            "properties": {
                "headline": {"type": "string", "description": "One upbeat sentence, max 14 words."},
                "notes": {"type": "array", "maxItems": 3, "items": {"type": "string"},
                          "description": "Specific, numeric, kind observations and one practical suggestion; max 22 words each."},
            },
        }},
    }
}

DIGEST_SYSTEM = (
    "You are the Digest agent in Rosetta, writing a short weekly note to parents about how a child's tidying habits are "
    "growing. Use only the numbers given. Praise effort and progress, never compare siblings, never shame. Give at most "
    "one practical suggestion (e.g. a better time of day or pairing a quest with a sibling)."
)


def digest(kid_name: str, kid_age: int, stats: dict) -> dict:
    out = _converse([{"text": f"Child: {kid_name}, age {kid_age}. Stats: {json.dumps(stats)}"}], DIGEST_TOOL, DIGEST_SYSTEM, max_tokens=400)
    return {"headline": out.get("headline", ""), "notes": out.get("notes", [])[:3]}
