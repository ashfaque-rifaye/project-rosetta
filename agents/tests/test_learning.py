"""Offline tests for the learning flow: homework marking policy, practice answers, the family Quiz Battle, the exact
maths double-check and question validation. Vision/tutor calls are stubbed; no AWS calls.
Run:  python -m unittest discover -s agents/tests
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rosetta_agents import gemini, mathcheck, store, tutor  # noqa: E402
from test_flow import DB, PHOTOS, SENT, VOICES, _install_fakes, call, upload  # noqa: E402

ITEMS = [
    {"id": "q1", "n": 1, "question": "1/2 + 1/4 =", "kidAnswer": "3/4", "correct": True, "box": [0, 0, 1, 1], "skill": "Adding fractions", "hint": "", "checkedBy": "math"},
    {"id": "q3", "n": 3, "question": "3/8 + 2/8 =", "kidAnswer": "5/16", "correct": False, "box": [0, 0, 1, 1], "skill": "Adding fractions", "hint": "Keep the bottom number.", "checkedBy": "math"},
]
PRACTICE = [{"id": f"p{i}", "prompt": f"Q{i}", "choices": ["a", "b", "c", "d"], "answer": 2, "why": "because", "skill": "Adding fractions", "forKid": ""}
            for i in range(1, 4)]


def _tutor_fakes(confidence=0.9, items=ITEMS, is_homework=True):
    tutor.tutor_check = lambda photo, age: {"isHomework": is_homework, "subject": "Maths", "topic": "Fractions",
                                            "items": [dict(i) for i in items], "confidence": confidence}
    tutor.practice = lambda age, topic, mistakes, count=5: [dict(p) for p in PRACTICE]
    tutor.quiz = lambda kids, studied, count=8: [dict(p, id=f"z{i}", forKid=kids[i % len(kids)]["id"]) for i, p in enumerate(PRACTICE)]


class HomeworkFlow(unittest.TestCase):
    def setUp(self):
        _install_fakes()
        _tutor_fakes()
        DB.clear(); PHOTOS.clear(); SENT.clear()
        store.seed("R4KX")
        for parent in ("mom", "dad"):
            call("POST", f"/household/R4KX/parents/{parent}", {"subscription": {"endpoint": f"https://push.example/{parent}", "keys": {}}}, pin="2468")

    def q(self):
        return store.quest(store.load("R4KX"), "q_arjun_homework")

    def test_homework_marked_then_practice_then_done(self):
        call("POST", "/household/R4KX/quests/q_arjun_homework/open")
        self.assertIn("homework", self.q()["keeperLine"])
        status, up = call("POST", "/household/R4KX/upload", {"questId": "q_arjun_homework"})
        self.assertEqual((status, up["phase"]), (200, "before"))
        upload("R4KX", "q_arjun_homework", "before", 0, b"worksheet")
        q = self.q()
        self.assertEqual(q["stage"], "Practising")
        self.assertEqual(len(q["practice"]), 3)
        self.assertEqual([e["agent"] for e in q["log"]][:3], ["QuestMaster", "Tutor", "Tutor"])
        # first answer counts; a wrong one is still recorded and explained
        self.assertFalse(call("POST", "/household/R4KX/quests/q_arjun_homework/answer", {"questionId": "p1", "choice": 0})[1]["correct"])
        self.assertTrue(call("POST", "/household/R4KX/quests/q_arjun_homework/answer", {"questionId": "p1", "choice": 2})[1].get("repeat"))
        call("POST", "/household/R4KX/quests/q_arjun_homework/answer", {"questionId": "p2", "choice": 2})
        r = call("POST", "/household/R4KX/quests/q_arjun_homework/answer", {"questionId": "p3", "choice": 2})[1]
        self.assertTrue(r["done"])
        h = store.load("R4KX")
        self.assertEqual(self.q()["stage"], "Done")
        row = h["history"][-1]
        self.assertEqual((row["kind"], row["right"], row["total"], row["practiceRight"], row["skills"]),
                         ("study", 1, 2, 2, ["Adding fractions"]))
        self.assertIn(("push", "quest_done"), SENT)
        self.assertEqual(sum(1 for g in h["glyphs"] if g["earnedBy"]), 9)  # homework completes the Family Stone

    def test_all_right_completes_without_practice(self):
        _tutor_fakes(items=[ITEMS[0]])
        upload("R4KX", "q_arjun_homework", "before", 0, b"worksheet")
        self.assertEqual(self.q()["stage"], "Done")
        self.assertFalse(self.q().get("practice"))

    def test_unsure_tutor_asks_a_parent(self):
        _tutor_fakes(confidence=0.5)
        upload("R4KX", "q_arjun_homework", "before", 0, b"blurry")
        self.assertTrue(self.q()["needsParent"])
        self.assertIn(("push", "needs_approval"), SENT)
        self.assertEqual(call("POST", "/household/R4KX/quests/q_arjun_homework/approve")[0], 403)
        self.assertEqual(call("POST", "/household/R4KX/quests/q_arjun_homework/approve", pin="2468")[1]["stage"], "Done")

    def test_not_homework_asks_for_a_new_photo(self):
        _tutor_fakes(is_homework=False)
        upload("R4KX", "q_arjun_homework", "before", 0, b"cat")
        self.assertEqual(self.q()["stage"], "AwaitingBefore")

    def test_answer_without_practice_is_rejected(self):
        self.assertEqual(call("POST", "/household/R4KX/quests/q_arjun_homework/answer", {"questionId": "p1", "choice": 1})[0], 400)


class ParentPage(unittest.TestCase):
    def setUp(self):
        _install_fakes()
        DB.clear(); SENT.clear()
        store.seed("R4KX")

    def test_sign_in_settings_and_test_alert(self):
        self.assertEqual(call("GET", "/household/R4KX/parents/mom")[0], 403)
        self.assertEqual(call("GET", "/household/R4KX/parents/mom", pin="1111")[0], 403)
        st, me = call("GET", "/household/R4KX/parents/mom", pin="2468")
        self.assertEqual((st, me["name"], me["devices"]), (200, "Mom", 0))
        call("POST", "/household/R4KX/parents/mom", {"email": "mom@example.com", "alerts": {"push": True, "email": True, "sms": False},
                                                       "subscription": {"endpoint": "https://push.example/m", "keys": {}}}, pin="2468")
        st, me = call("GET", "/household/R4KX/parents/mom", pin="2468")
        self.assertEqual((me["email"], me["alerts"]["email"], me["devices"]), ("mom@example.com", True, 1))
        self.assertNotIn("email", [k for p in call("GET", "/household/R4KX")[1]["parents"] for k in p])  # never public
        st, r = call("POST", "/household/R4KX/parents/mom/test", pin="2468")
        self.assertEqual(r["delivered"], ["push:mom", "email:mom"])


class KeeperVoice(unittest.TestCase):
    """Gemini voice for the Keeper's line and for parents' messages (TTS stubbed)."""

    def setUp(self):
        _install_fakes()
        _tutor_fakes()
        DB.clear(); PHOTOS.clear(); VOICES.clear()
        store.seed("R4KX")

    def test_keeper_line_gets_a_voice_after_the_text(self):
        call("POST", "/household/R4KX/quests/q_arjun_homework/open")
        call("POST", "/household/R4KX/upload", {"questId": "q_arjun_homework"})
        upload("R4KX", "q_arjun_homework", "before", 0, b"worksheet")
        q = store.quest(store.load("R4KX"), "q_arjun_homework")
        key = q["keeperVoice"]["key"]
        self.assertTrue(key.startswith("voice/R4KX/q_arjun_homework-"))
        self.assertEqual(VOICES[key], b"RIFF:" + q["keeperLine"].encode())
        view = next(x for x in call("GET", "/household/R4KX")[1]["quests"] if x["id"] == "q_arjun_homework")
        self.assertNotIn("keeperVoice", view)
        self.assertIn(key, view["keeperAudioUrl"])
        # a newer line without its own clip must not borrow the old one
        h = store.load("R4KX"); store.quest(h, "q_arjun_homework")["keeperLine"] = "Something new"; store.save(h)
        view = next(x for x in call("GET", "/household/R4KX")[1]["quests"] if x["id"] == "q_arjun_homework")
        self.assertNotIn("keeperAudioUrl", view)

    def test_parent_message_carries_its_voice(self):
        st, ev = call("POST", "/household/R4KX/message", {"from": "mom", "text": "Proud of you!"}, pin="2468")
        self.assertEqual(st, 200)
        self.assertEqual(VOICES[ev["data"]["audioKey"]], b"RIFF:A message from Mom. Proud of you!")
        feed = call("GET", "/household/R4KX")[1]["feed"]
        self.assertIn("audioUrl", feed[-1]["data"])


class GeminiAdapter(unittest.TestCase):
    def test_boxes_are_asked_in_gemini_order_and_returned_as_x1_y1_x2_y2(self):
        schema = gemini._gemini_schema(tutor.TUTOR_TOOL["toolSpec"]["inputSchema"]["json"])
        box = schema["properties"]["items"]["items"]["properties"]["box"]
        self.assertTrue(box["description"].startswith("[ymin, xmin, ymax, xmax]"))
        self.assertTrue(box["description"].endswith("around the question and its answer"))
        out = gemini._boxes_back({"items": [{"box": [100, 200, 300, 400], "n": 1}]})
        self.assertEqual(out["items"][0]["box"], [200, 100, 400, 300])


class QuizBattle(unittest.TestCase):
    def setUp(self):
        _install_fakes()
        _tutor_fakes()
        DB.clear(); SENT.clear()
        store.seed("R4KX")

    def test_quiz_lights_glyphs_and_finishes(self):
        quiz = call("POST", "/household/R4KX/quiz/start", {"count": 3})[1]
        self.assertEqual((quiz["goal"], quiz["lit"], quiz["done"]), (3, 0, False))
        call("POST", "/household/R4KX/quiz/answer", {"questionId": "z0", "kidId": "arjun", "choice": 2})
        call("POST", "/household/R4KX/quiz/answer", {"questionId": "z1", "kidId": "meera", "choice": 1})
        self.assertTrue(call("POST", "/household/R4KX/quiz/answer", {"questionId": "z1", "kidId": "meera", "choice": 2})[1]["repeat"])
        r = call("POST", "/household/R4KX/quiz/answer", {"questionId": "z2", "kidId": "arjun", "choice": 2})[1]
        self.assertEqual((r["done"], r["lit"]), (True, 2))
        self.assertEqual(call("POST", "/household/R4KX/quiz/answer", {"questionId": "z0", "kidId": "zed", "choice": 2})[0], 400)

    def test_quiz_uses_this_weeks_homework(self):
        from rosetta_agents import learning
        h = store.load("R4KX")
        q = store.quest(h, "q_arjun_homework")
        q["homework"] = {"subject": "Maths", "topic": "Fractions", "items": ITEMS, "confidence": 0.9}
        studied = learning.studied_this_week(h)
        self.assertEqual(studied, [{"kidId": "arjun", "topics": ["Maths: Fractions"], "needs_work": ["Adding fractions"]}])


class ExactMaths(unittest.TestCase):
    def test_verify(self):
        cases = [("1/2 + 1/4 =", "3/4", True), ("Which is bigger: 2/3 or 3/5 ?", "2/3", True), ("3/8 + 2/8 =", "5/16", False),
                 ("Half of 12 is", "6", True), ("Write 4/8 in its simplest form.", "2/4", False),
                 ("Write 4/8 in its simplest form.", "1/2", True), ("15 - 6 = ___", "8", False), ("What is 2/7 + 3/7?", "5/7", True),
                 ("Name a mammal", "dog", None)]
        for q, a, want in cases:
            self.assertEqual(mathcheck.verify(q, a), want, q)

    def test_slip_hints_never_contain_the_answer(self):
        self.assertIn("bottom numbers", mathcheck.slip("3/8 + 2/8 =", "5/16"))
        self.assertIn("simplified more", mathcheck.slip("Write 4/8 in its simplest form.", "2/4"))
        self.assertIn("one away", mathcheck.slip("15 - 6 =", "8"))
        self.assertIsNone(mathcheck.slip("1/2 + 1/4 =", "2/6"))
        for q, a, right in [("3/8 + 2/8 =", "5/16", "5/8"), ("Write 4/8 in its simplest form.", "2/4", "1/2"), ("15 - 6 =", "8", "9")]:
            self.assertNotIn(right, mathcheck.slip(q, a))

    def test_questions_drop_ambiguous_and_shuffle(self):
        raw = {"questions": [
            {"prompt": "What is 2/3 + 1/3?", "choices": ["1", "3/3", "2/6", "1/6"], "answer": 1, "why": "", "skill": ""},  # two right
            {"prompt": "What is 7 + 2?", "choices": ["8", "9", "10", "5"], "answer": 0, "why": "", "skill": ""},  # marked wrong
            {"prompt": "What is 3 + 4?", "choices": ["7", "6", "5", "8"], "answer": 0, "why": "", "skill": ""},
            {"prompt": "Capital of France?", "choices": ["Paris", "Rome", "Oslo", "Bern"], "answer": 0, "why": "", "skill": ""},
        ]}
        qs = tutor._questions(raw, "z")
        self.assertEqual([q["prompt"] for q in qs], ["What is 3 + 4?", "Capital of France?"])
        for q in qs:
            self.assertIn(q["choices"][q["answer"]], ("7", "Paris"))


if __name__ == "__main__":
    unittest.main()
