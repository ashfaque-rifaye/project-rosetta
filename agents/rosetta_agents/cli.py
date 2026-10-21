"""Try the vision specialists on local photos (needs AWS credentials with Bedrock access).

  python -m rosetta_agents.cli scout  <before.jpg> [age]
  python -m rosetta_agents.cli judge  <before.jpg> <after.jpg>      (runs scout first)
"""
import json
import sys
from pathlib import Path

from . import vision


def main(argv: list[str]) -> None:
    cmd, *args = argv
    if cmd == "scout":
        out = vision.scout(Path(args[0]).read_bytes(), int(args[1]) if len(args) > 1 else 8)
        print(json.dumps(out, indent=2))
        print("keeper:", vision.keeper("Arjun", 8, f"Scout found: {[t['name'] for t in out['things']]}"))
    elif cmd == "judge":
        before, after = Path(args[0]).read_bytes(), Path(args[1]).read_bytes()
        things = vision.scout(before, 8)["things"]
        print("scout:", json.dumps(things, indent=2))
        print("judge:", json.dumps(vision.judge(before, after, things), indent=2))
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
