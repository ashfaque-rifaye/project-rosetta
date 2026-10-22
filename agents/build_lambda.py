"""Package the agent crew for AWS Lambda (python3.12, x86_64) into agents/dist without Docker.

  python agents/build_lambda.py
Installs Linux wheels for requirements.txt next to the rosetta_agents package; CDK uploads agents/dist.
"""
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DIST = HERE / "dist"

shutil.rmtree(DIST, ignore_errors=True)
DIST.mkdir()
# pywebpush's own deps (py-vapid, http-ece) ship only as source but are pure Python: install them separately with
# --no-deps, and get everything compiled as Linux wheels.
PURE = ("pywebpush", "py-vapid", "http-ece")
reqs = [r for r in (HERE / "requirements.txt").read_text().splitlines() if r.strip() and not r.startswith("#")]
binary = [r for r in reqs if not r.startswith(PURE)] + ["cryptography>=42", "aiohttp>=3.9", "requests>=2.31"]
pip = [sys.executable, "-m", "pip", "install", "--target", str(DIST), "--upgrade", "--quiet", "--no-cache-dir"]
subprocess.check_call(pip + binary + ["--platform", "manylinux2014_x86_64", "--implementation", "cp",
                                       "--python-version", "3.12", "--only-binary=:all:"])
subprocess.check_call(pip + ["--no-deps", "pywebpush>=2.0", "py-vapid>=1.9", "http-ece>=1.1"])
shutil.copytree(HERE / "rosetta_agents", DIST / "rosetta_agents", ignore=shutil.ignore_patterns("__pycache__"))
# boto3/botocore are already in the Lambda runtime; drop them to keep the bundle small.
for name in ("boto3", "botocore", "s3transfer"):
    for p in DIST.glob(f"{name}*"):
        shutil.rmtree(p, ignore_errors=True)
print("lambda bundle:", DIST)
