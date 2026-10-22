"""The Rosetta agent crew.

Quest Master is a Strands agent. It doesn't look at photos itself: it decides which specialist to
call and in what order, based on the quest's state, and it handles the edge cases (wrong room,
no change, low confidence, everything done). Specialists are plain functions wrapped as tools:

  scout_room      Scout + Coach: find Lost Things in the before photo, give each a home and stars
  check_after     Judge: compare before/after item by item, with confidence
  ask_parent      Router: low confidence goes to the parent instead of auto-approving
  complete_quest  Carve the glyph when every Lost Thing is home
  keeper_says     Keeper: the one sentence the TV speaks
  mark_homework   Tutor: read a homework photo, mark each answer (maths double-checked in code), hint, never answer
  make_practice   Practice: write practice questions on exactly the skills that went wrong

Every tool writes straight to the household document, so the TV (polling) sees each step land.
"""
from __future__ import annotations

import logging
import time

from . import config, notify, store, tutor, vision, voice

log = logging.getLogger(__name__)

try:  # Strands is bundled in Lambda; locally the deterministic pipeline below is used if it's missing.
    from strands import Agent, tool
    from strands.models import BedrockModel
except ImportError:  # pragma: no cover
    Agent = None

    def tool(f):
        return f


class _Ctx:
    """The household being worked on during one crew run."""

    def __init__(self, code: str, quest_id: str):
        self.code = code
        self.quest_id = quest_id
        self.h = store.load(code)

    @property
    def q(self) -> dict:
        return store.quest(self.h, self.quest_id)

    @property
    def kid(self) -> dict:
        return store.kid(self.h, self.q["kidId"])

    def save(self):
        store.save(self.h)


_ctx: _Ctx | None = None


def _log(agent: str, message: str, confidence: float | None = None, tool_name: str | None = None) -> None:
    """Append one Mission Control step (agent names match the TV's Agent enum)."""
    import time
    q = _ctx.q
    entry = {"agent": agent, "message": message[:120], "atMs": int(time.time() * 1000) % 3_600_000}
    if confidence is not None:
        entry["confidence"] = round(confidence, 2)
    if tool_name:
        entry["tool"] = tool_name
    q["log"] = (q.get("log", []) + [entry])[-30:]


@tool
def scout_room() -> str:
    """Scout + Coach: look at the quest's BEFORE photo, find the Lost Things (out-of-place items), and put them
    on the TV with where each belongs. Returns a short summary, or 'NOT_A_ROOM' if the photo isn't a room."""
    q = _ctx.q
    _log("QuestMaster", "Photo received from phone · routing to Scout", tool_name="scout_room")
    found = vision.scout(store.photo_bytes(q["beforeKey"]), _ctx.kid["age"])
    if not found["is_room"] or not found["things"]:
        q["stage"] = "AwaitingBefore"
        _ctx.save()
        return "NOT_A_ROOM" if not found["is_room"] else "NOTHING_FOUND"
    q.update(stage="Found", things=found["things"], confidence=1.0)
    _log("Scout", f"{len(found['things'])} out-of-place objects found")
    _log("Coach", "Homes assigned · " + ", ".join(f"{t['id']}→{t['home']}" for t in found["things"])[:100], tool_name="plan_homes")
    _ctx.save()
    return "found: " + ", ".join(f"{t['name']} (home: {t['home']})" for t in found["things"])


@tool
def check_after() -> str:
    """Judge: compare the BEFORE and the latest AFTER photo item by item. Marks returned items on the TV.
    Returns 'WRONG_ROOM', 'NO_CHANGE', or a summary with the confidence and what is still lost."""
    q = _ctx.q
    _log("QuestMaster", "New photo · routing to Judge", tool_name="check_after")
    verdict = vision.judge(store.photo_bytes(q["beforeKey"]), store.photo_bytes(q["afterKey"]), q["things"])
    if not verdict["same_room"]:
        q["stage"] = "Found"
        _ctx.save()
        return "WRONG_ROOM"
    if not verdict["changed"]:
        q["stage"] = "Found"
        _ctx.save()
        return "NO_CHANGE"
    for t in q["things"]:
        t["returned"] = t["returned"] or verdict["returned"].get(t["id"], False)
    q["confidence"] = verdict["confidence"]
    left = [t["name"] for t in q["things"] if not t["returned"]]
    q["stage"] = "Partial" if left else "Done"
    _log("Judge", f"{len(q['things']) - len(left)} of {len(q['things'])} home", confidence=verdict["confidence"])
    _ctx.save()
    return f"confidence={verdict['confidence']:.2f}; still lost: {', '.join(left) if left else 'none'}"


@tool
def ask_parent(reason: str) -> str:
    """Router: when the Judge's confidence is low, flag the quest for a parent to approve on their phone
    instead of auto-approving. `reason` is shown to the parent."""
    q = _ctx.q
    q["needsParent"] = True
    q["parentReason"] = reason
    _log("Router", f"Sent to a parent: {reason}", tool_name="ask_parent")
    notify.emit(_ctx.h, "needs_approval", f"Check {_ctx.kid['name']}'s room?",
                f"The Judge is {q.get('confidence', 0):.0%} sure the Lost Things are home. Approve from here.",
                by="Router", data={"questId": q["id"], "kidId": q["kidId"]})
    _ctx.save()
    return "parent asked"


@tool
def complete_quest() -> str:
    """Carve the quest's glyph into the Family Stone. Only call when every Lost Thing is home."""
    q = _ctx.q
    if q.get("kind") != "study" and any(not t["returned"] for t in q["things"]):
        return "REFUSED: some Lost Things are still lost"
    q["stage"] = "Done"
    for g in _ctx.h["glyphs"]:
        if g["id"] == q["glyphId"]:
            g["earnedBy"] = q["kidId"]
    record_finish(_ctx.h, q)
    notify.emit(_ctx.h, "quest_done", f"{_ctx.kid['name']} finished “{q['title']}” ✅",
                f"{_done_detail(q)} A glyph was carved into the Family Stone.",
                by="QuestMaster", data={"questId": q["id"], "kidId": q["kidId"]})
    _ctx.save()
    done = sum(1 for g in _ctx.h["glyphs"] if g.get("earnedBy"))
    return f"glyph carved; stone {done}/{len(_ctx.h['glyphs'])}"


@tool
def keeper_says(situation: str) -> str:
    """Keeper: write and show the single warm sentence the TV speaks next. `situation` describes what just
    happened (what was found, what is still lost, or that the photo needs retaking)."""
    line = vision.keeper(_ctx.kid["name"], _ctx.kid["age"], situation)
    _ctx.q["keeperLine"] = line
    _log("Keeper", "Spoke to " + _ctx.kid["name"], tool_name="keeper_says")
    _ctx.save()
    _voice(line)
    return line


@tool
def mark_homework() -> str:
    """Tutor: read the quest's homework photo, mark every answer and add a hint to each wrong one (never the answer).
    Returns 'NOT_HOMEWORK', or a summary with the score, the confidence, the skills that went well and the ones to practise."""
    q = _ctx.q
    _log("QuestMaster", "Homework photo received · routing to Tutor", tool_name="mark_homework")
    marked = tutor.tutor_check(store.photo_bytes(q["beforeKey"]), _ctx.kid["age"])
    if not marked["isHomework"] or not marked["items"]:
        q["stage"] = "AwaitingBefore"
        _ctx.save()
        return "NOT_HOMEWORK"
    right = sum(1 for i in marked["items"] if i["correct"])
    q.update(stage="Checked", homework=marked, confidence=marked["confidence"])
    by_code = sum(1 for i in marked["items"] if i["checkedBy"] == "math")
    _log("Tutor", f"{right} of {len(marked['items'])} right · {marked['topic']}", confidence=marked["confidence"])
    if by_code:
        _log("Tutor", f"{by_code} answers double-checked with exact maths", tool_name="mathcheck")
    _ctx.save()
    wrong = sorted({i["skill"] for i in marked["items"] if not i["correct"]})
    # Only skills with no mistakes count as "went well", so the Keeper never praises what the child just got wrong.
    well = sorted({i["skill"] for i in marked["items"]} - set(wrong))
    return (f"score {right}/{len(marked['items'])}; confidence={marked['confidence']:.2f}; "
            f"went well: {', '.join(well) or 'effort'}; to practise: {', '.join(wrong) or 'none'}")


@tool
def make_practice() -> str:
    """Practice: write 5 practice questions on the skills the child got wrong, and put them on the TV and phone.
    Only call after mark_homework found at least one wrong answer."""
    q = _ctx.q
    mistakes = [i for i in q["homework"]["items"] if not i["correct"]]
    if not mistakes:
        return "NOTHING_TO_PRACTISE"
    qs = tutor.practice(_ctx.kid["age"], q["homework"]["topic"], mistakes)
    q.update(stage="Practising", practice=qs, answers={})
    _log("Coach", f"{len(qs)} practice questions on " + ", ".join(sorted({m['skill'] for m in mistakes}))[:80],
         tool_name="make_practice")
    _ctx.save()
    return f"{len(qs)} practice questions ready"


QUEST_MASTER_PROMPT = f"""You are Quest Master, the coordinator of Rosetta's agent crew on a family Fire TV.
A child's phone just uploaded a photo for a tidy-up quest. Decide which tools to call, then stop.

- New BEFORE photo: call scout_room. If it returns NOT_A_ROOM or NOTHING_FOUND, call keeper_says to ask kindly
  for a new photo of the room. Otherwise call keeper_says to invite the child to help the Lost Things home.
- New AFTER photo: call check_after.
  - WRONG_ROOM or NO_CHANGE: keeper_says asking for a fresh photo of the same room.
  - confidence below {config.AUTO_APPROVE_CONFIDENCE}: call ask_parent with a short reason, then keeper_says that a grown-up
    will take a look.
  - nothing still lost: call complete_quest, then keeper_says celebrating and mentioning the Family Stone.
  - otherwise: keeper_says naming exactly what is still lost and where it belongs.
For a HOMEWORK quest (a photo of a worksheet):
  - call mark_homework. If it returns NOT_HOMEWORK, keeper_says asking kindly for a clear photo of the whole sheet.
  - confidence below {config.AUTO_APPROVE_CONFIDENCE}: call ask_parent (the handwriting may have been misread), then keeper_says.
  - every answer right: call complete_quest, then keeper_says celebrating something specific.
  - otherwise call make_practice, then keeper_says praising what went well and inviting them to the practice round.
    Never tell the child a correct answer.
Always finish with exactly one keeper_says call. Never invent items the tools did not report."""


def _deterministic(phase: str) -> None:
    """Same policy without the LLM orchestrator (used when Strands isn't available)."""
    if _ctx.q.get("kind") == "study":
        r = mark_homework()
        if r == "NOT_HOMEWORK":
            keeper_says("The photo didn't show a homework sheet clearly; ask kindly for a clear photo of the whole page.")
        elif _ctx.q["confidence"] < config.AUTO_APPROVE_CONFIDENCE:
            ask_parent(f"Tutor is only {_ctx.q['confidence']:.0%} sure it read the handwriting")
            keeper_says("Say a grown-up will take a quick look at the homework.")
        elif all(i["correct"] for i in _ctx.q["homework"]["items"]):
            complete_quest()
            keeper_says(f"Tutor result: {r}. Everything is right; celebrate and mention the Family Stone.")
        else:
            make_practice()
            keeper_says(f"Tutor result: {r}. Praise what went well and invite them to a short practice round. "
                        "Do not give any answers.")
        return
    if phase == "before":
        r = scout_room()
        keeper_says("The photo didn't show the room clearly; ask for another photo." if r.startswith("NO")
                    else f"Scout found these Lost Things: {r}. Invite the child to bring them home.")
        return
    r = check_after()
    if r in ("WRONG_ROOM", "NO_CHANGE"):
        keeper_says("The new photo doesn't show the same room changing; ask kindly for a fresh photo.")
        return
    conf = _ctx.q["confidence"]
    if conf < config.AUTO_APPROVE_CONFIDENCE:
        ask_parent(f"Judge is only {conf:.0%} sure")
        keeper_says("Say a grown-up will take a quick look.")
    elif _ctx.q["stage"] == "Done":
        complete_quest()
        keeper_says("Every Lost Thing is home; a new glyph is carved into the Family Stone.")
    else:
        keeper_says(f"Judge result: {r}. Name what is still lost and where it belongs.")


def _done_detail(q: dict) -> str:
    if q.get("kind") == "study" and q.get("homework"):
        items = q["homework"]["items"]
        return f"Homework {sum(1 for i in items if i['correct'])}/{len(items)} right."
    return f"{len(q.get('things', []))} Lost Things are home."


def record_finish(h: dict, q: dict) -> None:
    """Habit history for Growth and the Digest agent: one row per finished quest."""
    import time
    opened = q.get("openedAtMs") or int(time.time() * 1000)
    h["history"] = (h.get("history", []) + [{
        "kidId": q["kidId"], "questId": q["id"], "roomId": q.get("roomId", ""), "atMs": int(time.time() * 1000),
        "minutes": max(1, (int(time.time() * 1000) - opened) // 60000), "selfStarted": bool(q.get("selfStarted")),
        "items": len(q.get("things", [])),
        **_study_row(q),
    }])[-500:]


def _study_row(q: dict) -> dict:
    """Learning fields for Growth: score, practice result and the skills that needed work."""
    if q.get("kind") != "study" or not q.get("homework"):
        return {"kind": "tidy"}
    items = q["homework"]["items"]
    return {"kind": "study", "subject": q["homework"]["subject"], "topic": q["homework"]["topic"],
            "right": sum(1 for i in items if i["correct"]), "total": len(items),
            "practiceRight": sum(1 for v in q.get("answers", {}).values() if v.get("correct")),
            "practiceTotal": len(q.get("practice", [])),
            "skills": sorted({i["skill"] for i in items if not i["correct"]})}


def _master_model():
    if config.AI_PROVIDER == "vertex":
        from strands.models.gemini import GeminiModel

        from . import gemini
        return GeminiModel(client_args=gemini.client_args(), model_id=config.GEMINI_MODEL,
                           params={"temperature": 0.1, "labels": gemini.labels("quest-master"), "thinking_config": gemini.thinking()})
    return BedrockModel(model_id=config.MODEL_ID, region_name=config.REGION, temperature=0.1)


def run(code: str, quest_id: str, phase: str) -> None:
    """Entry point from the S3-upload Lambda: phase is 'before' or 'after'."""
    global _ctx
    _ctx = _Ctx(code, quest_id)
    if Agent is None:
        _deterministic(phase)
        return
    master = Agent(
        model=_master_model(),
        system_prompt=QUEST_MASTER_PROMPT,
        tools=[mark_homework, make_practice, ask_parent, complete_quest, keeper_says] if _ctx.q.get("kind") == "study"
        else [scout_room, check_after, ask_parent, complete_quest, keeper_says],
        callback_handler=None,
    )
    kid = _ctx.kid
    kind = "HOMEWORK" if _ctx.q.get("kind") == "study" else "tidy-up"
    master(f"New {phase.upper()} photo for {kid['name']} (age {kid['age']}), {kind} quest '{_ctx.q['title']}'. "
           f"Current stage: {_ctx.q['stage']}.")


def _voice(line: str) -> None:
    """The Keeper's line as Gemini audio, made as soon as the line is (the text is already on the TV). It's written to
    a freshly loaded household, so answers given in the few seconds it takes are never overwritten."""
    if config.AI_PROVIDER != "vertex" or not line:
        return
    try:
        from . import gemini
        key = f"voice/{_ctx.code}/{_ctx.quest_id}-{int(time.time())}.wav"
        store.put_voice(key, gemini.speak(line))
        _ctx.h = store.load(_ctx.code)
        if _ctx.q.get("keeperLine") == line:
            voice.set_line(_ctx.q, line, key)
            _ctx.save()
    except Exception:  # the TV falls back to its own voice
        log.exception("keeper voice failed")
