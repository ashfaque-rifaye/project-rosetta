#!/usr/bin/env bash
# Mirror the versioned source (this folder) into the Unity working project on E: and build the Fire OS APK.
#   bash arena-unity/sync.sh [--build]
set -euo pipefail
SRC="$(cd "$(dirname "$0")" && pwd)"
PROJ="${ROSETTA_ARENA_PROJECT:-/e/Unity/Projects/RosettaArena}"
UNITY="${UNITY_EXE:-/e/Unity/Editors/6000.3.27f1/Editor/Unity.exe}"
mkdir -p "$PROJ"
cp "$SRC/Assets/link.xml" "$PROJ/Assets/" 2>/dev/null || true
for d in Assets/Scripts Assets/Editor Assets/Resources Packages ProjectSettings; do
  mkdir -p "$PROJ/$d"
  cp -r "$SRC/$d/." "$PROJ/$d/"
done
if [[ "${1:-}" == "--build" ]]; then
  "$UNITY" -batchmode -quit -nographics -projectPath "$(cygpath -w "$PROJ")" -buildTarget Android \
    -executeMethod Rosetta.Arena.Editor.ArenaBuild.Apk -logFile "$(cygpath -w "$PROJ/build.log")"
  ls -la "$PROJ/Builds"
fi
