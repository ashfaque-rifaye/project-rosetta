"""Local preview of the phone pages with a mock API (no AWS). Mirrors the demo household's "Partial" moment.

  python web/dev_server.py   ->  http://localhost:8765/kid.html?code=R4KX  and  /parent.html?code=R4KX
"""
import json
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent

THINGS = [
    ("sock", "A lonely sock", "the laundry basket", True), ("dino", "Rex the dinosaur", "the toy box", True),
    ("bricks", "The brick gang", "the brick bin", True), ("books", "Five sleepy books", "the bookshelf", True),
    ("jacket", "The crumpled jacket", "the hooks by the window", False),
]
HOUSEHOLD = {
    "code": "R4KX", "name": "The Rao household",
    "kids": [{"id": "arjun", "name": "Arjun", "age": 8}, {"id": "meera", "name": "Meera", "age": 6}],
    "rooms": [{"id": "arjun_room", "name": "Arjun's room", "rect": [0, 0, 1, 1]}, {"id": "balcony", "name": "Balcony", "rect": [0, 0, 1, 1]}],
    "quests": [
        {"id": "q_arjun_room", "kidId": "arjun", "roomId": "arjun_room", "title": "Rescue the Lost Things", "glyphId": "g_rescuer",
         "stage": "Partial", "keeperLine": "Four are home! The jacket is still lost on the chair. Its hook by the window is waiting.",
         "confidence": 0.88, "things": [{"id": i, "name": n, "character": i, "box": [0, 0, 1, 1], "stars": 1, "home": h, "returned": r} for i, n, h, r in THINGS]},
        {"id": "q_balcony", "kidId": "meera", "roomId": "balcony", "title": "Water the balcony garden", "glyphId": "next",
         "stage": "Ready", "keeperLine": "", "confidence": 1, "things": []},
    ],
    "glyphs": [{"id": f"g{i}", "name": "", "asset": "", "earnedBy": "arjun" if i < 8 else ""} for i in range(9)],
}


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(HERE), **kw)

    def _json(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/config.js"):
            body = b'window.ROSETTA_CONFIG = { apiUrl: "" };'
            self.send_response(200); self.send_header("content-type", "text/javascript"); self.end_headers(); self.wfile.write(body)
        elif self.path.startswith("/household/"):
            self._json(HOUSEHOLD)
        else:
            super().do_GET()

    def do_POST(self):
        self._json({"ok": True})


if __name__ == "__main__":
    print("http://localhost:8765/kid.html?code=R4KX")
    ThreadingHTTPServer(("127.0.0.1", 8765), Handler).serve_forever()
