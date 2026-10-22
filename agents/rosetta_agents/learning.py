"""Answers and the family Quiz Battle. Plain logic over the household document (no model calls except building a quiz).

Practice: each homework quest that had mistakes gets practice questions (crew.make_practice). The kid answers on the
TV remote or the phone; when every question has an answer the quest is done and its glyph is carved.

Quiz Battle: a co-op round for the whole family built from what the kids studied this week. Every right answer lights
one glyph of the Stone Trial; nobody is ranked.
"""
from __future__ import annotations

import time

from . import crew, notify, store, tutor, voice


def _now() -> int:
    return int(time.time() * 1000)


def answer_practice(h: dict, quest_id: str, question_id: str, choice: int) -> dict:
    q = store.quest(h, quest_id)
    if q.get("stage") != "Practising":
        raise ValueError("this quest has no practice round open")
    pq = next(x for x in q["practice"] if x["id"] == question_id)
    answers = q.setdefault("answers", {})
    if question_id in answers:  # first answer counts; no guessing until it turns green
        return {"correct": answers[question_id]["correct"], "why": pq["why"], "done": q["stage"] == "Done", "repeat": True}
    correct = int(choice) == pq["answer"]
    answers[question_id] = {"choice": int(choice), "correct": correct, "atMs": _now()}
    done = len(answers) == len(q["practice"])
    if done:
        right = sum(1 for a in answers.values() if a["correct"])
        kid = store.kid(h, q["kidId"])
        q.update(stage="Done")
        voice.set_line(q, f"{right} of {len(q['practice'])} in practice. That's how skills grow, {kid['name']}!")
        for g in h["glyphs"]:
            if g["id"] == q["glyphId"]:
                g["earnedBy"] = q["kidId"]
        crew.record_finish(h, q)
        hw = q["homework"]["items"]
        notify.emit(h, "quest_done", f"{kid['name']} finished “{q['title']}” ✅",
                    f"Homework {sum(1 for i in hw if i['correct'])}/{len(hw)} right, then {right}/{len(q['practice'])} in practice "
                    f"on {', '.join(sorted({i['skill'] for i in hw if not i['correct']}))}.",
                    by="Coach", data={"questId": q["id"], "kidId": q["kidId"]})
    return {"correct": correct, "why": pq["why"], "done": done}


def studied_this_week(h: dict) -> list[dict]:
    """What each kid worked on, from finished homework rows and homework quests still on the board."""
    week = 7 * 24 * 3600 * 1000
    out: dict[str, dict] = {}
    rows = [r for r in h.get("history", []) if r.get("kind") == "study" and _now() - r["atMs"] < week]
    for r in rows:
        e = out.setdefault(r["kidId"], {"kidId": r["kidId"], "topics": set(), "needs_work": set()})
        e["topics"].add(f"{r['subject']}: {r['topic']}")
        e["needs_work"].update(r.get("skills", []))
    for q in h["quests"]:
        if q.get("kind") == "study" and q.get("homework"):
            e = out.setdefault(q["kidId"], {"kidId": q["kidId"], "topics": set(), "needs_work": set()})
            e["topics"].add(f"{q['homework']['subject']}: {q['homework']['topic']}")
            e["needs_work"].update(i["skill"] for i in q["homework"]["items"] if not i["correct"])
    return [{"kidId": e["kidId"], "topics": sorted(e["topics"]), "needs_work": sorted(e["needs_work"])} for e in out.values()]


def start_quiz(h: dict, count: int = 8) -> dict:
    kids = [{"id": k["id"], "name": k["name"], "age": k["age"]} for k in h["kids"]]
    questions = tutor.quiz(kids, studied_this_week(h), count)
    h["quiz"] = {"id": f"quiz_{_now()}", "title": "Family Quiz Battle", "questions": questions, "answers": [],
                 "lit": 0, "goal": len(questions), "done": False, "startedAtMs": _now()}
    notify.emit(h, "quiz_started", "Family Quiz Battle is on 📺", f"{len(questions)} questions from this week's homework.",
                by="Quizmaster", notify=False)
    return h["quiz"]


def answer_quiz(h: dict, question_id: str, kid_id: str, choice: int) -> dict:
    quiz = h.get("quiz") or {}
    if not quiz or quiz.get("done"):
        raise ValueError("no quiz running")
    qq = next(x for x in quiz["questions"] if x["id"] == question_id)
    if any(a["questionId"] == question_id for a in quiz["answers"]):
        return {"correct": None, "why": qq["why"], "done": quiz["done"], "repeat": True}
    store.kid(h, kid_id)  # must be a real kid of this household
    correct = int(choice) == qq["answer"]
    quiz["answers"].append({"questionId": question_id, "kidId": kid_id, "choice": int(choice), "correct": correct, "atMs": _now()})
    if correct:
        quiz["lit"] += 1
    if len(quiz["answers"]) == len(quiz["questions"]):
        quiz["done"] = True
        notify.emit(h, "quiz_done", f"Quiz Battle: {quiz['lit']} of {quiz['goal']} glyphs lit 🏆",
                    "The family played together tonight.", by="Quizmaster")
    return {"correct": correct, "why": qq["why"], "done": quiz["done"], "lit": quiz["lit"]}
