"""Exact checking for simple primary-school arithmetic, so the Tutor's verdict never rests on the model's maths alone.

The model transcribes the question and the child's answer; when the question is one of the shapes below, Python works
out the true answer with exact fractions and overrides the model's "correct" flag. Anything else returns None and the
model's verdict stands (with its confidence).
"""
from __future__ import annotations

import re
from fractions import Fraction

NUM = r"(\d+(?:\s*/\s*\d+)?)"


def _f(s: str) -> Fraction | None:
    s = s.strip().replace(" ", "")
    try:
        if "/" in s:
            a, b = s.split("/", 1)
            return Fraction(int(a), int(b))
        return Fraction(int(s))
    except (ValueError, ZeroDivisionError):
        return None


def _is_simplest(s: str) -> bool:
    s = s.strip().replace(" ", "")
    if "/" not in s:
        return True
    a, b = s.split("/", 1)
    from math import gcd
    return gcd(int(a), int(b)) == 1


def expected(question: str) -> tuple[str, Fraction] | None:
    """('value', x) when any equal value is right; ('simplest', x) when it must also be fully simplified."""
    q = question.lower().replace("−", "-").replace("–", "-").replace("×", "*").replace("÷", "/")
    q = re.sub(r"^\s*(?:q?\d+\s*[.)]\s+)?", "", q)  # printed numbering like "1. " or "Q2) "
    q = re.sub(r"^\s*(what is|work out|solve|calculate)\s*", "", q)
    q = re.sub(r"[?_.]+|\s*=\s*$", " ", q).strip()
    m = re.fullmatch(rf"{NUM}\s*([+\-*])\s*{NUM}\s*=?", q)
    if m:
        a, op, b = _f(m[1]), m[2], _f(m[3])
        if a is None or b is None:
            return None
        return "value", (a + b if op == "+" else a - b if op == "-" else a * b)
    m = re.search(rf"half of\s*{NUM}", q)
    if m and _f(m[1]) is not None:
        return "value", _f(m[1]) / 2
    m = re.search(rf"(bigger|larger|greater|smaller)\D*?{NUM}\s*or\s*{NUM}", q)
    if m:
        a, b = _f(m[2]), _f(m[3])
        if a is None or b is None or a == b:
            return None
        pick = max(a, b) if m[1] != "smaller" else min(a, b)
        return "value", pick
    m = re.search(rf"{NUM}\s*in (?:its )?(?:simplest|lowest) (?:form|terms)", q) or re.search(rf"simplify\s*{NUM}", q)
    if m and _f(m[1]) is not None:
        return "simplest", _f(m[1])
    return None


def slip(question: str, kid_answer: str) -> str | None:
    """A hint for the classic slips we can recognise exactly. Never contains the answer."""
    exp = expected(question)
    got = _f(kid_answer)
    if exp is None or got is None:
        return None
    kind, value = exp
    if kind == "simplest" and got == value:
        return "That's equal, but it can be simplified more. Find a number that divides both the top and the bottom."
    q = re.sub(r"^\s*(?:q?\d+\s*[.)]\s+)?(what is\s*)?", "", question.lower()).replace("−", "-")
    m = re.search(rf"{NUM}\s*([+-])\s*{NUM}", q)
    if m and "/" in m[1] and "/" in m[3] and "/" in kid_answer:
        (a, b), (c, d) = (map(int, m[1].replace(" ", "").split("/")), map(int, m[3].replace(" ", "").split("/")))
        ka, kb = map(int, kid_answer.replace(" ", "").split("/"))
        if b == d and kb == b + d:
            return "You added the bottom numbers too. When the bottoms match, add the tops and keep the bottom."
    if m and "/" not in m[1] and abs(got - value) == 1:
        return "So close, you're one away. Count it again slowly."
    return None


def verify(question: str, kid_answer: str) -> bool | None:
    exp = expected(question)
    if exp is None:
        return None
    kind, value = exp
    got = _f(kid_answer)
    if got is None:
        return False
    if kind == "simplest":
        return got == value and _is_simplest(kid_answer)
    return got == value
