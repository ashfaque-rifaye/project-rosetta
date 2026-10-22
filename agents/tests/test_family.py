"""Offline tests for Rosetta v2: a fresh family with nothing invented, daily habits with notes, voice notes and photos,
streaks, pocket money and the wish list, earned TV time, learning profiles and honest replies to wrong photos.
Models are stubbed (see test_flow._install_fakes). Run:  python -m unittest discover -s agents/tests
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rosetta_agents import family, store, tutor  # noqa: E402
from test_flow import DB, PHOTOS, SENT, _install_fakes, call, upload  # noqa: E402

PIN = "2468"


class Base(unittest.TestCase):
    def setUp(self):
        _install_fakes()
        DB.clear(); PHOTOS.clear(); SENT.clear()
        store.seed("R4KX")
        call("POST", "/household/R4KX/parents/mom", {"subscription": {"endpoint": "https://push.example/mom", "keys": {}}}, pin=PIN)

    def h(self):
        return store.load("R4KX")

    def habit(self, **kw):
        body = {"title": "Read 10 pages", "icon": "📖", "kidIds": ["arjun"], "proof": "none", "coins": 10, "money": 5, **kw}
        st, hb = call("POST", "/household/R4KX/habits", body, pin=PIN)
        self.assertEqual(st, 200)
        q = next(q for q in self.h()["quests"] if q.get("habitId") == hb["id"] and q["kidId"] == "arjun")
        return hb, q


class FreshFamily(Base):
    def test_nothing_is_pre_filled(self):
        st, v = call("GET", "/household/R4KX")
        self.assertEqual(st, 200)
        self.assertEqual([p["name"] for p in v["parents"]], ["Mom", "Dad"])
        for k in v["kids"]:
            self.assertEqual((k["level"], k["xp"], k["coins"], k["money"], k["streak"]), (1, 0, 0, 0, 0))
        self.assertEqual((v["stars"], v["squad"], v["stamps"], v["memories"], v["history"]), ([], [], [], [], []))
        self.assertNotIn("glyphs", v)
        self.assertEqual(v["kids"][0]["grade"], "Grade 3")
        self.assertEqual(v["today"], family.today())

    def test_reset_needs_the_parent_pin(self):
        self.assertEqual(call("POST", "/household/R4KX/reset")[0], 403)
        self.assertEqual(call("POST", "/household/R4KX/reset", pin=PIN)[0], 200)


class Habits(Base):
    def test_parent_adds_a_daily_task_and_a_tick_finishes_it(self):
        self.assertEqual(call("POST", "/household/R4KX/habits", {"title": "Make the bed", "kidIds": ["arjun"]})[0], 403)
        hb, q = self.habit()
        self.assertEqual((q["kind"], q["stage"], q["due"], q["mustDo"]), ("habit", "Ready", family.today(), True))
        st, r = call("POST", f"/household/R4KX/quests/{q['id']}/checkin", {"kidId": "arjun"})
        self.assertEqual((st, r["stage"]), (200, "Done"))
        h = self.h()
        arjun = family.kid(h, "arjun")
        self.assertEqual((arjun["coins"], arjun["money"], arjun["streak"]), (10, 5.0, 1))
        self.assertEqual(next(x for x in h["habits"] if x["id"] == hb["id"])["streaks"]["arjun"], 1)
        self.assertEqual(len(h["stars"]), 1)  # all of Arjun's daily tasks done today
        self.assertIn(("push", "habit_done"), SENT)
        self.assertTrue(call("POST", f"/household/R4KX/quests/{q['id']}/checkin", {"kidId": "arjun"})[1]["repeat"])

    def test_note_proof_needs_a_note_and_becomes_a_memory(self):
        _, q = self.habit(title="Prayer time", proof="note")
        self.assertEqual(call("POST", f"/household/R4KX/quests/{q['id']}/checkin", {"kidId": "arjun"})[0], 400)
        call("POST", f"/household/R4KX/quests/{q['id']}/checkin", {"kidId": "arjun", "note": "I prayed for Grandma"})
        m = self.h()["memories"][-1]
        self.assertEqual((m["kind"], m["text"], m["kidId"]), ("note", "I prayed for Grandma", "arjun"))

    def test_voice_note_is_transcribed_kept_and_counts_only_if_about_the_task(self):
        _, q = self.habit(proof="voice")
        st, up = call("POST", "/household/R4KX/upload", {"questId": q["id"], "phase": "voice", "contentType": "audio/webm;codecs=opus"})
        self.assertEqual((st, up["phase"], up["contentType"]), (200, "voice", "audio/webm"))
        upload("R4KX", q["id"], "voice", 1, b"silence")
        q2 = store.quest(self.h(), q["id"])
        self.assertEqual(q2["stage"], "Ready")
        self.assertIn("nothing", q2["whatISee"] + "nothing")  # honest: nothing about reading was heard
        upload("R4KX", q["id"], "voice", 2, b"I read ten pages about dinosaurs")
        h = self.h()
        self.assertEqual(store.quest(h, q["id"])["stage"], "Done")
        m = h["memories"][-1]
        self.assertEqual((m["kind"], m["transcript"], m["summary"]), ("voice", "I read ten pages about dinosaurs", "Read 10 pages of a dinosaur book."))
        self.assertTrue(m["audioKey"].startswith("memories/R4KX/"))
        view = call("GET", "/household/R4KX")[1]
        self.assertTrue(view["memories"][-1]["audioUrl"].startswith("https://"))

    def test_photo_proof_is_checked_honestly(self):
        _, q = self.habit(title="Make the bed", proof="photo")
        upload("R4KX", q["id"], "proof", 1, b"cat")
        q2 = store.quest(self.h(), q["id"])
        self.assertEqual((q2["stage"], q2["whatISee"]), ("Ready", "a cat on a sofa"))
        self.assertIn("cat on a sofa", q2["keeperLine"])
        upload("R4KX", q["id"], "proof", 2, b"made-bed")
        self.assertEqual(store.quest(self.h(), q["id"])["stage"], "Done")

    def test_parent_check_waits_for_approval(self):
        _, q = self.habit(needsParent=True)
        self.assertEqual(call("POST", f"/household/R4KX/quests/{q['id']}/checkin", {"kidId": "arjun"})[1]["stage"], "Checking")
        self.assertIn(("push", "needs_approval"), SENT)
        call("POST", f"/household/R4KX/quests/{q['id']}/approve", pin=PIN)
        self.assertEqual(family.kid(self.h(), "arjun")["coins"], 10)

    def test_yesterdays_tasks_leave_the_board(self):
        _, q = self.habit()
        h = self.h()
        old = store.quest(h, q["id"])
        old.update(due="2000-01-01", id=q["id"][:-8] + "20000101")  # as if it was made yesterday
        store.save(h)
        call("GET", "/household/R4KX")
        ids = [x["id"] for x in self.h()["quests"]]
        self.assertNotIn(old["id"], ids)
        self.assertEqual(sum(1 for x in self.h()["quests"] if x.get("kind") == "habit"), 1)  # today's copy


class TvTime(Base):
    def test_unlocks_only_after_must_do_tasks(self):
        st = call("GET", "/household/R4KX")[1]["tvTime"]
        self.assertEqual((st["mustDo"], st["done"], st["unlocked"]), (2, 0, False))  # two homework quests
        self.assertEqual(call("POST", "/household/R4KX/tvtime/request")[0], 400)
        h = self.h()
        for q in h["quests"]:
            if q.get("mustDo"):
                q["stage"] = "Done"
        store.save(h)
        self.assertTrue(call("GET", "/household/R4KX/tvtime")[1]["unlocked"])

    def test_needs_parent_ok_when_set(self):
        call("POST", "/household/R4KX/settings", {"tvNeedsApproval": True, "tvMinutes": 30}, pin=PIN)
        h = self.h()
        for q in h["quests"]:
            q["stage"] = "Done"
        store.save(h)
        r = call("POST", "/household/R4KX/tvtime/request")[1]
        self.assertEqual((r["unlocked"], r["requested"]), (False, True))
        self.assertIn(("push", "tv_request"), SENT)
        self.assertEqual(call("POST", "/household/R4KX/tvtime/approve")[0], 403)
        r = call("POST", "/household/R4KX/tvtime/approve", pin=PIN)[1]
        self.assertEqual((r["unlocked"], r["minutesLeft"]), (True, 30))
        self.assertEqual(call("POST", "/household/R4KX/tvtime/use", {"minutes": 10})[1]["minutesLeft"], 20)


class WishList(Base):
    def test_save_up_ask_and_approve(self):
        st, w = call("POST", "/household/R4KX/wishlist", {"kidId": "arjun", "title": "LEGO set", "price": 10}, pin=PIN)
        self.assertEqual((st, w["status"]), (200, "saving"))
        self.assertEqual(call("POST", f"/household/R4KX/wishlist/{w['id']}/request", {"kidId": "arjun"})[0], 400)  # not enough yet
        _, q = self.habit(money=10)
        call("POST", f"/household/R4KX/quests/{q['id']}/checkin", {"kidId": "arjun"})
        self.assertIn(("push", "wish_ready"), SENT)
        self.assertEqual(call("POST", f"/household/R4KX/wishlist/{w['id']}/request", {"kidId": "arjun"})[1]["status"], "requested")
        self.assertIn(("push", "wish_request"), SENT)
        self.assertEqual(call("POST", f"/household/R4KX/wishlist/{w['id']}/approve", pin=PIN)[1]["status"], "approved")
        self.assertEqual(family.kid(self.h(), "arjun")["money"], 0)


class LearningProfile(Base):
    def test_profile_reaches_the_quiz(self):
        call("POST", "/household/R4KX/kids/arjun/profile",
             {"grade": "Grade 3", "subjects": ["Maths", "Science"], "upcomingTests": [{"subject": "Maths", "topics": "fractions", "date": "2026-10-30"}]}, pin=PIN)
        seen = {}

        def quiz(kids, studied, count=8):
            seen["kids"] = kids
            return [{"id": "z1", "prompt": "Q", "choices": ["a", "b", "c", "d"], "answer": 0, "why": "w", "skill": "s",
                     "forKid": "arjun", "reason": "Maths test on Friday"}]
        tutor.quiz = quiz
        q = call("POST", "/household/R4KX/quiz/start", {"count": 8})[1]
        self.assertEqual(q["title"], "Family Quiz Night")
        arjun = next(k for k in seen["kids"] if k["id"] == "arjun")
        self.assertEqual((arjun["grade"], arjun["upcomingTests"][0]["topics"]), ("Grade 3", "fractions"))
        call("POST", "/household/R4KX/quiz/answer", {"questionId": "z1", "kidId": "arjun", "choice": 0})
        h = self.h()
        self.assertTrue(h["quiz"]["done"])
        self.assertEqual(family.kid(h, "meera")["coins"], 5)  # co-op: everyone shares the family's right answers

    def test_quiz_turns_alternate(self):
        qs = [{"id": f"z{i}", "forKid": k} for i, k in enumerate(["arjun", "arjun", "meera", "meera"])]
        self.assertEqual([q["forKid"] for q in tutor._alternate(qs, ["arjun", "meera"])], ["arjun", "meera", "arjun", "meera"])


class HonestPhotos(Base):
    def test_wrong_photo_says_what_it_shows(self):
        call("POST", "/household/R4KX/quests/q_arjun_room/open")
        upload("R4KX", "q_arjun_room", "before", 0, b"cat")
        q = store.quest(self.h(), "q_arjun_room")
        self.assertEqual((q["stage"], q["whatISee"]), ("AwaitingBefore", "a cat on a sofa"))
        self.assertIn("cat on a sofa", q["keeperLine"])

    def test_already_tidy_room_is_a_win(self):
        call("POST", "/household/R4KX/quests/q_arjun_room/open")
        upload("R4KX", "q_arjun_room", "before", 0, b"tidy")
        q = store.quest(self.h(), "q_arjun_room")
        self.assertEqual(q["stage"], "Done")
        self.assertEqual(family.kid(self.h(), "arjun")["coins"], 30)


class CoachNotes(Base):
    def test_notes_only_from_real_activity(self):
        self.assertEqual(self.h()["digest"], {})
        for title in ("Read 10 pages", "Make the bed", "Brush teeth"):
            _, q = self.habit(title=title)
            call("POST", f"/household/R4KX/quests/{q['id']}/checkin", {"kidId": "arjun"})
        d = self.h()["digest"]["arjun"]
        self.assertEqual(d["headline"], "Arjun did 3 tasks")


if __name__ == "__main__":
    unittest.main()
