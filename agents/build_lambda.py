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
subprocess.check_call([
    sys.executable, "-m", "pip", "install", "-r", str(HERE / "requirements.txt"),
    "--target", str(DIST), "--platform", "manylinux2014_x86_64", "--implementation", "cp",
    "--python-version", "3.12", "--only-binary=:all:", "--upgrade", "--quiet", "--no-cache-dir",
])
shutil.copytree(HERE / "rosetta_agents", DIST / "rosetta_agents", ignore=shutil.ignore_patterns("__pycache__"))
# boto3/botocore are already in the Lambda runtime; drop them to keep the bundle small.
for name in ("boto3", "botocore", "s3transfer"):
    for p in DIST.glob(f"{name}*"):
        shutil.rmtree(p, ignore_errors=True)
print("lambda bundle:", DIST)
