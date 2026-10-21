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
    vision.keeper = lambda name, age, situation: f"[{name}] {situation[:40]}"


def call(method, path, body=None):
    ev = {"requestContext": {"http": {"method": method}}, "rawPath": path, "body": json.dumps(body) if body else None}
    r = handlers.api(ev, None)
    return r["statusCode"], json.loads(r["body"])


def upload(code, qid, phase, n, data):
    key = f"uploads/{code}/{qid}/{phase}-{n}.jpg"
    PHOTOS[key] = data
    handlers.on_upload({"Records": [{"s3": {"object": {"key": key}}}]}, None)


class QuestFlow(unittest.TestCase):
    def setUp(self):
        _install_fakes()
        DB.clear(); PHOTOS.clear()
        store.seed("R4KX")

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
        self.assertEqual(sum(1 for g in h["glyphs"] if g["earnedBy"]), 9)

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
        call("POST", "/household/R4KX/quests/q_arjun_room/approve")
        self.assertEqual(self.q()["stage"], "Done")
        self.assertEqual(sum(1 for g in store.load("R4KX")["glyphs"] if g["earnedBy"]), 9)

    def test_parent_posts_quest(self):
        status, q = call("POST", "/household/R4KX/quests", {"kidId": "meera", "roomId": "living", "title": "Fold the blankets"})
        self.assertEqual(status, 200)
        self.assertIn(q["id"], [x["id"] for x in store.load("R4KX")["quests"]])


if __name__ == "__main__":
    unittest.main()
