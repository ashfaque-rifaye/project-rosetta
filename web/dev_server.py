"""Local preview of the phone pages against the real API handlers, with in-memory storage and stubbed AI (no AWS,
no Google). Everything stays on this machine.

  python web/dev_server.py   ->  http://localhost:8765/kid.html?code=R4KX  and  /parent.html?code=R4KX  (PIN 2468)
"""
import importlib.util
import json
import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
AGENTS = HERE.parent / "agents"
sys.path.insert(0, str(AGENTS))

# The offline test suite's fakes: in-memory DynamoDB/S3, stubbed vision and voice.
spec = importlib.util.spec_from_file_location("test_flow", AGENTS / "tests" / "test_flow.py")
fakes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fakes)
fakes._install_fakes()

from rosetta_agents import handlers, store  # noqa: E402

store.presign_get = lambda key: f"/dev-media/{key}"
store.seed("R4KX")
# ROSETTA_DEV_SEED=household.json loads a real household (e.g. the public DEMO family's GET) to preview its pages.
if os.environ.get("ROSETTA_DEV_SEED"):
    seeded = json.loads(Path(os.environ["ROSETTA_DEV_SEED"]).read_text(encoding="utf-8"))
    seeded["parentPin"] = "2468"
    fakes.DB[seeded["code"]] = json.dumps(seeded)


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(HERE), **kw)

    def _api(self, method):
        n = int(self.headers.get("content-length") or 0)
        ev = {"requestContext": {"http": {"method": method}}, "rawPath": self.path.split("?")[0],
              "body": self.rfile.read(n).decode() if n else None,
              "headers": {"x-parent-pin": self.headers.get("x-parent-pin", "")}}
        r = handlers.api(ev, None)
        body = r["body"].encode()
        self.send_response(r["statusCode"])
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/config.js"):
            # ?devParent=mom signs in as a demo parent on this local preview only (the PIN never leaves localhost).
            body = (b'window.ROSETTA_CONFIG = { apiUrl: "" };'
                    b'if (/^(localhost|127[.]0[.]0[.]1)$/.test(location.hostname) && new URLSearchParams(location.search).get("devParent"))'
                    b' localStorage.setItem("rosetta.parent", JSON.stringify({id: "mom", name: "Mom", pin: "2468"}));')
            self.send_response(200); self.send_header("content-type", "text/javascript"); self.end_headers(); self.wfile.write(body)
        elif self.path.startswith(("/household/", "/vapid-public-key")):
            self._api("GET")
        else:
            super().do_GET()

    def do_POST(self):
        self._api("POST")


if __name__ == "__main__":
    print("http://localhost:8765/parent.html?code=R4KX  (PIN 2468)")
    ThreadingHTTPServer(("127.0.0.1", 8765), Handler).serve_forever()
