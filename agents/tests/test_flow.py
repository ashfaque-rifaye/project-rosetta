"""Offline end-to-end test of the quest flow: API + upload events + crew policy, with in-memory storage
and stubbed vision (no AWS calls). Run:  python -m unittest discover -s agents/tests
"""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rosetta_agents import crew, handlers, store, vision  # noqa: E402

DB: dict = {}
PHOTOS: dict = {}


def _install_fakes():
    store.load = lambda code: json.loads(DB[code])
    store.save = lambda h: DB.__setitem__(h["code"], json.dumps(h))
    store.photo_bytes = lambda key: PHOTOS[key]
    store.presign_get = lambda key: f"https://example/{key}?sig"
    store.presign_put = lambda key: f"https://example/put/{key}?sig"
    crew.Agent = None  # exercise the deterministic policy (same rules as the Strands prompt)

    def scout(photo, age):
        return {"is_room": photo != b"cat", "things": [
            {"id": "sock", "name": "A lonely sock", "character": "sock", "box": [0.1, 0.8, 0.2, 0.9], "home": "basket", "stars": 1, "returned": False},
            {"id": "jacket", "name": "The jacket", "character": "jacket", "box": [0.5, 0.3, 0.6, 0.5], "home": "hook", "stars": 1, "returned": False},
        ]}

    def judge(before, after, things):
        done = {"after-partial": {"sock"}, "after-done": {"sock", "jacket"}, "after-unsure": {"sock", "jacket"}}.get(after.decode(), set())
        return {"same_room": after != b"other-room", "changed": after != before,
                "returned": {t["id"]: t["id"] in done for t in things},
                "confidence": 0.4 if after == b"after-unsure" else 0.9}

    vision.scout, vision.judge = scout, judge
    from rosetta_agents import notify
    notify._push = lambda sub, ev: SENT.append(("push", ev["kind"])) or True
    notify._email = lambda to, ev: SENT.append(("email", ev["kind"])) or True
    notify._sms = lambda to, ev: SENT.append(("sms", ev["kind"])) or True
    vision.keeper = lambda name, age, situation: f"[{name}] {situation[:40]}"


SENT = []


def call(method, path, body=None, pin=None):
    ev = {"requestContext": {"http": {"method": method}}, "rawPath": path, "body": json.dumps(body) if body else None,
          "headers": {"x-parent-pin": pin} if pin else {}}
    r = handlers.api(ev, None)
    return r["statusCode"], json.loads(r["body"])


def upload(code, qid, phase, n, data):
    key = f"uploads/{code}/{qid}/{phase}-{n}.jpg"
    PHOTOS[key] = data
    handlers.on_upload({"Records": [{"s3": {"object": {"key": key}}}]}, None)


class QuestFlow(unittest.TestCase):
    def setUp(self):
        _install_fakes()
        DB.clear(); PHOTOS.clear(); SENT.clear()
        store.seed("R4KX")
        for parent in ("mom", "dad"):  # each parent's phone has installed the app and allowed notifications
            call("POST", f"/household/R4KX/parents/{parent}", {"subscription": {"endpoint": f"https://push.example/{parent}", "keys": {}}}, pin="2468")

    def q(self):
        return store.quest(store.load("R4KX"), "q_arjun_room")

    def test_full_quest_carves_last_glyph(self):
        self.assertEqual(call("POST", "/household/R4KX/quests/q_arjun_room/open")[1]["stage"], "AwaitingBefore")
        status, up = call("POST", "/household/R4KX/upload", {"questId": "q_arjun_room"})
        self.assertEqual((status, up["phase"]), (200, "before"))

        upload("R4KX", "q_arjun_room", "before", 0, b"room")
        self.assertEqual(self.q()["stage"], "Found")
        self.assertEqual(len(self.q()["things"]), 2)

        self.assertEqual(call("POST", "/household/R4KX/upload", {"questId": "q_arjun_room"})[1]["phase"], "after")
        upload("R4KX", "q_arjun_room", "after", 1, b"after-partial")
        q = self.q()
        self.assertEqual(q["stage"], "Partial")
        self.assertEqual([t["returned"] for t in q["things"]], [True, False])

        upload("R4KX", "q_arjun_room", "after", 2, b"after-done")
        h = store.load("R4KX")
        self.assertEqual(store.quest(h, "q_arjun_room")["stage"], "Done")
        self.assertEqual(sum(1 for g in h["glyphs"] if g["earnedBy"]), 8)  # homework carves the 9th (test_learning)

        _, view = call("GET", "/household/R4KX")
        self.assertTrue(store.quest(view, "q_arjun_room")["afterUrl"].startswith("https://"))
        self.assertNotIn("beforeKey", store.quest(view, "q_arjun_room"))

        call("POST", "/household/R4KX/quests/q_arjun_room/close")
        self.assertNotIn("q_arjun_room", [x["id"] for x in store.load("R4KX")["quests"]])

    def test_not_a_room_asks_again(self):
        call("POST", "/household/R4KX/quests/q_arjun_room/open")
        upload("R4KX", "q_arjun_room", "before", 0, b"cat")
        self.assertEqual(self.q()["stage"], "AwaitingBefore")
        self.assertTrue(self.q()["keeperLine"])

    def test_same_photo_is_not_progress(self):
        call("POST", "/household/R4KX/quests/q_arjun_room/open")
        upload("R4KX", "q_arjun_room", "before", 0, b"room")
        upload("R4KX", "q_arjun_room", "after", 1, b"room")
        self.assertEqual(self.q()["stage"], "Found")

    def test_low_confidence_goes_to_parent(self):
        call("POST", "/household/R4KX/quests/q_arjun_room/open")
        upload("R4KX", "q_arjun_room", "before", 0, b"room")
        upload("R4KX", "q_arjun_room", "after", 1, b"after-unsure")
        self.assertTrue(self.q()["needsParent"])
        self.assertEqual(sum(1 for g in store.load("R4KX")["glyphs"] if g["earnedBy"]), 8)
        self.assertEqual(call("POST", "/household/R4KX/quests/q_arjun_room/approve")[0], 403)  # kids can't approve
        self.assertIn(("push", "needs_approval"), SENT)
        call("POST", "/household/R4KX/quests/q_arjun_room/approve", pin="2468")
        self.assertEqual(self.q()["stage"], "Done")
        self.assertEqual(sum(1 for g in store.load("R4KX")["glyphs"] if g["earnedBy"]), 8)

    def test_emotes_are_allow_listed(self):
        self.assertEqual(call("POST", "/household/R4KX/emote", {"kidId": "meera", "emoji": "🔥"})[0], 200)
        self.assertEqual(call("POST", "/household/R4KX/emote", {"kidId": "meera", "emoji": "<script>"})[0], 400)
        self.assertEqual(store.load("R4KX")["emotes"][-1]["emoji"], "🔥")

    def test_crew_writes_mission_control_log(self):
        call("POST", "/household/R4KX/quests/q_arjun_room/open")
        upload("R4KX", "q_arjun_room", "before", 0, b"room")
        agents = [e["agent"] for e in self.q()["log"]]
        for a in ("QuestMaster", "Scout", "Coach", "Keeper"):
            self.assertIn(a, agents)

    def test_quest_done_alerts_parents_and_lands_in_feed(self):
        call("POST", "/household/R4KX/quests/q_arjun_room/open")
        upload("R4KX", "q_arjun_room", "before", 0, b"room")
        upload("R4KX", "q_arjun_room", "after", 1, b"after-done")
        h = store.load("R4KX")
        self.assertEqual(h["feed"][-1]["kind"], "quest_done")
        self.assertEqual(SENT.count(("push", "quest_done")), 2)  # mom + dad
        self.assertEqual(len(h["history"]), 1)

    def test_messages_and_done_check(self):
        self.assertEqual(call("POST", "/household/R4KX/message", {"from": "mom", "text": "So proud!"})[0], 403)
        self.assertEqual(call("POST", "/household/R4KX/message", {"from": "mom", "text": "So proud!"}, pin="2468")[0], 200)
        self.assertEqual(call("POST", "/household/R4KX/message", {"from": "arjun", "text": "free text"})[0], 400)
        self.assertEqual(call("POST", "/household/R4KX/message", {"from": "arjun", "text": "Great job! 🏆"})[0], 200)
        call("POST", "/household/R4KX/done-check", {"kidId": "meera"})
        self.assertIn(("push", "done_check"), SENT)
        self.assertNotIn(("sms", "done_check"), SENT)  # sms is off by default

    def test_parent_settings_and_sms_only_when_urgent(self):
        call("POST", "/household/R4KX/parents/mom", {"phone": "+15550100", "alerts": {"push": False, "email": False, "sms": True}}, pin="2468")
        call("POST", "/household/R4KX/done-check", {"kidId": "arjun"})
        self.assertIn(("sms", "done_check"), SENT)
        _, view = call("GET", "/household/R4KX")
        self.assertNotIn("parentPin", view)
        self.assertNotIn("phone", view["parents"][0])

    def test_parent_posts_quest(self):
        self.assertEqual(call("POST", "/household/R4KX/quests", {"kidId": "meera", "title": "x"})[0], 403)
        status, q = call("POST", "/household/R4KX/quests", {"kidId": "meera", "roomId": "living", "title": "Fold the blankets"}, pin="2468")
        self.assertEqual(status, 200)
        self.assertIn(q["id"], [x["id"] for x in store.load("R4KX")["quests"]])


if __name__ == "__main__":
    unittest.main()
