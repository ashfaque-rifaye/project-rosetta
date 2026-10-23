"""The learning specialists: Tutor (marks a homework photo), Practice (questions from the mistakes) and Quizmaster
(the family Quiz Battle). Same pattern as vision.py: one Bedrock Converse call each, forced JSON tool schema.

Rules every prompt carries:
  - never give a child the answer to their own homework; hints point at the method
  - kind, specific, no shaming, no comparing siblings
"""
from __future__ import annotations

import json
import random
import re

from . import mathcheck, vision

# ---------------------------------------------------------------- Tutor
TUTOR_TOOL = {
    "toolSpec": {
        "name": "report_marking",
        "description": "Mark a child's homework sheet question by question.",
        "inputSchema": {"json": {
            "type": "object",
            "required": ["what_i_see", "is_homework", "subject", "topic", "items", "confidence"],
            "properties": {
                "what_i_see": {"type": "string", "description": "What the photo actually shows, plain words a child understands, max 14 words."},
                "is_homework": {"type": "boolean", "description": "True if the photo shows a worksheet or exercise book with answers."},
                "subject": {"type": "string", "description": "e.g. Maths, English, Science"},
                "topic": {"type": "string", "description": "Specific topic, e.g. 'Adding fractions with the same denominator'"},
                "items": {
                    "type": "array", "maxItems": 12,
                    "items": {
                        "type": "object",
                        "required": ["n", "question", "kid_answer", "correct", "box", "skill"],
                        "properties": {
                            "n": {"type": "integer", "description": "Question number as printed"},
                            "question": {"type": "string", "description": "The question, transcribed"},
                            "kid_answer": {"type": "string", "description": "What the child wrote, transcribed exactly"},
                            "working": {"type": "string", "description": "Your own step-by-step working, BEFORE deciding. Never shown to the child."},
                            "expected": {"type": "string", "description": "The correct answer from your working. Never shown to the child."},
                            "correct": {"type": "boolean", "description": "True only if kid_answer matches expected"},
                            "box": {"type": "array", "items": {"type": "integer"}, "minItems": 4, "maxItems": 4,
                                    "description": "[x1, y1, x2, y2] in 0-1000 image coordinates around the child's handwritten answer only"},
                            "skill": {"type": "string", "description": "The skill this question practises, 2-5 words"},
                            "hint": {"type": "string",
                                     "description": "Only when wrong: one short hint that names the child's actual slip and the method. Never state the right answer."},
                        },
                    },
                },
                "confidence": {"type": "number", "minimum": 0, "maximum": 1},
            },
        }},
    }
}

TUTOR_SYSTEM = (
    "You are Tutor, part of Rosetta, a family app on the TV. You look at a photo of a {age}-year-old's homework: maths, "
    "English, science or coding (for code, trace the program line by line, keeping track of each variable). "
    "Transcribe the questions the child answered (numbered or not, up to 12, in reading order; skip the name, date and "
    "class fields and blanks the child left empty) and exactly what the child wrote. The line after = is the answer blank: "
    "a number written on or above it is a whole-number answer, not a fraction (read an answer as a fraction only when "
    "the printed question has fractions). Then, for each question, write your own working "
    "step by step and the expected answer BEFORE you compare it with the child's answer. "
    "An answer that is right but not in the form the question asks for (for example not "
    "simplified when asked to simplify) is not correct. For each wrong answer give ONE short hint (max 20 words) that "
    "explains the method to use, written for the child. Do not guess what the child did wrong unless it is obvious. NEVER reveal the correct answer, not even partly. "
    "Be warm and specific; never shame. Each box must tightly cover the child's handwritten answer only. Give an honest confidence: "
    "lower it if handwriting is unclear or the photo is cut off."
)


def tutor_check(photo: bytes, kid_age: int) -> dict:
    out = vision._converse([vision._image(photo), {"text": "Mark this homework."}], TUTOR_TOOL,
                           TUTOR_SYSTEM.format(age=kid_age), max_tokens=2500, think=True)
    items = []
    for it in out.get("items", [])[:12]:
        question = re.sub(r"^\s*(?:q?\d+\s*[.)]\s+)", "", str(it.get("question", ""))).strip()
        if not question or re.match(r"(name|date|class|roll)", question, re.I):
            continue  # header fields are not questions
        it["question"] = question
        correct = bool(it.get("correct"))
        # Exact maths beats the model's arithmetic whenever the question is a shape we can compute.
        checked = mathcheck.verify(str(it.get("question", "")), str(it.get("kid_answer", "")))
        if checked is not None:
            correct = checked
        items.append({
            "id": f"q{int(it.get('n', len(items) + 1))}",
            "n": int(it.get("n", len(items) + 1)),
            "question": str(it.get("question", ""))[:160],
            "kidAnswer": str(it.get("kid_answer", ""))[:60],
            "correct": correct,
            "box": vision._norm_box(it.get("box", [0, 0, 1000, 1000])),
            "skill": str(it.get("skill", ""))[:60],
            # Recognised slips get an exact hint; otherwise the model's method hint.
            "hint": "" if correct else (mathcheck.slip(question, str(it.get("kid_answer", "")))
                                        or str(it.get("hint", "")) or "Have another look at the method for this one.")[:160],
            "checkedBy": "math" if checked is not None else "ai",
        })
    return {
        "isHomework": bool(out.get("is_homework", True)),
        "whatISee": str(out.get("what_i_see", ""))[:120],
        "subject": str(out.get("subject", ""))[:40],
        "topic": str(out.get("topic", ""))[:80],
        "items": items,
        "confidence": float(out.get("confidence", 0.5)),
    }


# ---------------------------------------------------------------- Practice + Quizmaster share one question format
QUESTIONS_TOOL = {
    "toolSpec": {
        "name": "write_questions",
        "description": "Multiple-choice questions for a child.",
        "inputSchema": {"json": {
            "type": "object", "required": ["questions"],
            "properties": {"questions": {
                "type": "array", "minItems": 3, "maxItems": 12,
                "items": {
                    "type": "object", "required": ["prompt", "choices", "answer", "why", "skill"],
                    "properties": {
                        "prompt": {"type": "string", "description": "The question, max 140 characters, readable on a TV"},
                        "choices": {"type": "array", "items": {"type": "string"}, "minItems": 4, "maxItems": 4,
                                    "description": "Four short options (max 18 characters each), exactly one correct"},
                        "answer": {"type": "integer", "minimum": 0, "maximum": 3, "description": "Index of the correct choice"},
                        "why": {"type": "string", "description": "One sentence shown after answering, explaining the method"},
                        "skill": {"type": "string"},
                        "for_kid": {"type": "string", "description": "Child id this question is pitched at (quiz only)"},
                        "reason": {"type": "string", "description": "Quiz only: why this question, for the parents, e.g. 'Maths test on Friday: fractions' (max 60 characters)"},
                    },
                },
            }},
        }},
    }
}

PRACTICE_SYSTEM = (
    "You are Practice, part of Rosetta. Write {count} multiple-choice questions for a {age}-year-old that practise "
    "exactly the skills they got wrong, starting easy and getting a little harder. Use new numbers, never the same "
    "questions as the homework, and never bare drills like 3 + 4: wrap the skill in a tiny real situation, a pattern or "
    "a comparison so the child has to think one step further. For coding, use short code a child can trace (variables, "
    "a loop that repeats, an if). Wrong options should be the mistakes a child really makes (e.g. adding denominators, "
    "an off-by-one loop). Keep wording short and friendly."
)

QUIZ_SYSTEM = (
    "You write Family Quiz Night for a family TV: {count} multiple-choice questions, alternating between the children "
    "(mark each with for_kid). Pitch each question at that child's school grade and use, in this order of priority: "
    "their upcoming tests (subject and topics), the skills they got wrong in this week's homework, then their school "
    "subjects. When a child has subjects, every one of their questions must come from those subjects (for a Grade 1 "
    "child doing Maths and English: e.g. a two-step adding word problem, a missing number, a rhyming or spelling "
    "choice), never colours or trivia. "
    "Write them like very easy GRE/GMAT-style reasoning made for children of that grade, NOT bare sums like 1 + 1 or "
    "3 + 4: a two-step word problem, comparing two quantities (which is bigger, or are they equal?), a number or shape "
    "pattern, a 'what must be true' logic puzzle, reading a tiny table, estimating, and for children learning Coding, "
    "tracing a 3-line program (x = 2; repeat 3 times: x = x + 1; what is x?), spotting the bug or the order of steps. "
    "Moderate and fair: every question solvable in under a minute by a child of that grade, never trick questions, "
    "never too easy. Wrong options are the "
    "mistakes a child really makes. Give each question a short 'reason' for the parents. If nothing is known about a "
    "child, use grade-appropriate maths and general knowledge that works for a family anywhere: nature, space, animals, "
    "shapes, words. No country-specific history or politics."
)


def _questions(out: dict, prefix: str) -> list[dict]:
    qs = []
    for q in out.get("questions", [])[:12]:
        prompt = str(q.get("prompt", ""))[:220]  # room for a 3-line program
        choices = [str(c)[:24] for c in q.get("choices", [])][:4]
        ans = int(q.get("answer", 0))
        if len(choices) != 4 or not 0 <= ans < 4 or len(set(choices)) != 4:
            continue  # drop malformed questions instead of showing a broken one on the TV
        # Arithmetic questions are checked exactly: the marked choice must be right and the only right one.
        right = [i for i, c in enumerate(choices) if mathcheck.verify(prompt, c)]
        if mathcheck.expected(prompt) is not None and right != [ans]:
            continue
        # Models put the right answer first far too often; shuffle so "always pick A" never works.
        order = list(range(4))
        random.Random(prompt).shuffle(order)
        qs.append({"id": f"{prefix}{len(qs) + 1}", "prompt": prompt, "choices": [choices[i] for i in order],
                   "answer": order.index(ans), "why": str(q.get("why", ""))[:200], "skill": str(q.get("skill", ""))[:60],
                   "forKid": str(q.get("for_kid", "")), "reason": str(q.get("reason", ""))[:80]})
    return qs


def _numbers(text: str) -> tuple[str, ...]:
    return tuple(re.findall(r"\d+", text))


def practice(kid_age: int, topic: str, mistakes: list[dict], count: int = 5) -> list[dict]:
    brief = {"topic": topic, "mistakes": [{"question": m["question"], "child_wrote": m["kidAnswer"], "skill": m["skill"]}
                                          for m in mistakes]}
    # Ask for a few extra and retry once: validation drops any question whose maths doesn't check out.
    # Practice must never be the homework itself: drop any question using the same numbers as a homework question.
    seen = {_numbers(m["question"]) for m in mistakes}
    qs: list[dict] = []
    for _ in range(2):
        out = vision._converse([{"text": json.dumps(brief)}], QUESTIONS_TOOL,
                               PRACTICE_SYSTEM.format(count=count + 2, age=kid_age), max_tokens=2200)
        qs = [q for q in _questions(out, "p") if _numbers(q["prompt"]) not in seen]
        if len(qs) >= count - 1:
            break
    return qs[:count]


def quiz(kids: list[dict], studied: list[dict], count: int = 8) -> list[dict]:
    """kids: [{id, name, age, grade, subjects, upcomingTests}]; studied: [{kidId, topics, needs_work}].
    Half the questions per child, alternating; retried once if validation drops too many."""
    brief = json.dumps({"children": kids, "studied_this_week": studied})
    qs: list[dict] = []
    for _ in range(2):
        out = vision._converse([{"text": brief}], QUESTIONS_TOOL, QUIZ_SYSTEM.format(count=min(12, count + 3)), max_tokens=3200)
        qs = _questions(out, "z")
        if len(qs) >= count:
            break
    return _alternate(qs, [k["id"] for k in kids])[:count]


def _alternate(qs: list[dict], kid_ids: list[str]) -> list[dict]:
    """Take turns fairly: kid A, kid B, kid A... (questions for unknown kids fill in at the end)."""
    queues = {k: [q for q in qs if q["forKid"] == k] for k in kid_ids}
    rest = [q for q in qs if q["forKid"] not in queues]
    out = []
    while any(queues.values()):
        for k in kid_ids:
            if queues[k]:
                out.append(queues[k].pop(0))
    return out + rest
