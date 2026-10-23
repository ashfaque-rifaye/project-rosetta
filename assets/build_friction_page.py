"""Render FRICTION_LOG.md as a page on the Rosetta site (web/friction-log.html), so the friction log has a public URL.

  python assets/build_friction_page.py
"""
import re
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parents[1]
md = (ROOT / "FRICTION_LOG.md").read_text(encoding="utf-8")
md = re.sub(r"^Also published at .*\n", "", md, flags=re.M)
body = markdown.markdown(md, extensions=["tables"])
# give each entry an anchor (#F-07) and colour the severity
body = re.sub(r"<h3>(F-\d+): ", r'<h3 id="\1"><span class="id">\1</span> ', body)
body = re.sub(r"(<strong>Severity:</strong>) (Blocker|Major|Minor|Papercut)", r'\1 <span class="sev \2">\2</span>', body)
body = re.sub(r"<td>(F-\d+)</td>", r'<td><a href="#\1">\1</a></td>', body)

page = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Rosetta Friction Log</title>
  <link rel="icon" href="icon-192.png">
  <link rel="stylesheet" href="style.css">
  <style>
    main.wide {{ max-width: 900px; }}
    h1 {{ font-size: 34px; }}
    h2 {{ margin-top: 34px; color: var(--gold); }}
    h3 {{ font: 600 19px/1.3 system-ui, "Segoe UI", sans-serif; margin: 26px 0 8px; padding-top: 14px; border-top: 1px solid var(--line); }}
    h3 .id {{ color: #ffb547; margin-right: 6px; }}
    ul {{ padding-left: 20px; }} li {{ margin: 5px 0; }}
    table {{ width: 100%; border-collapse: collapse; font-size: 14.5px; margin: 14px 0; }}
    th, td {{ text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--line); vertical-align: top; }}
    th {{ color: var(--gold); font-size: 12px; letter-spacing: .08em; text-transform: uppercase; }}
    a {{ color: var(--gold); }}
    code {{ background: var(--panel); border: 1px solid var(--line); border-radius: 6px; padding: 1px 6px; font-size: 13.5px; word-break: break-word; }}
    .sev {{ font-weight: 700; padding: 2px 9px; border-radius: 99px; font-size: 13px; }}
    .sev.Blocker {{ background: #ff5a6a; color: #1a0508; }} .sev.Major {{ background: #ff9a4d; color: #1d0d00; }}
    .sev.Minor {{ background: #f4c56a; color: #1d1400; }} .sev.Papercut {{ background: #a9b4cc; color: #0a1020; }}
    hr {{ border: 0; border-top: 1px solid var(--line); margin: 24px 0; }}
  </style>
</head>
<body>
<main class="wide">
<div class="eyebrow">Project Rosetta · Amazon Developer Build, Ship, Shape hackathon</div>
{body}
<p class="muted">Source: <a href="https://github.com/ashfaque-rifaye/project-rosetta/blob/main/FRICTION_LOG.md">FRICTION_LOG.md on GitHub</a> · <a href="tv.html">Install the TV app</a></p>
</main>
</body>
</html>
"""
(ROOT / "web" / "friction-log.html").write_text(page, encoding="utf-8", newline="\n")
print("web/friction-log.html")
