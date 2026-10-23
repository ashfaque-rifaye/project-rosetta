"""Rosetta's 3D moments, built from code as glTF binaries (no modelling tool, no downloaded assets).

  python assets/models/make_models.py   ->  tv-app/app/src/main/assets/models/{star,chest}.glb

star.glb  : a chunky five-point star, bevelled faces, warm gold (the Family Goal star that flies in)
chest.glb : a treasure chest, wood body, gold bands and a domed lid (the wish list / rewards)
Materials glow a little (emissive) so they read well on TV without an environment map.
"""
import json
import math
import struct
from pathlib import Path

import numpy as np

OUT = Path(__file__).resolve().parents[2] / "tv-app/app/src/main/assets/models"


def normals(pos: np.ndarray, idx: np.ndarray) -> np.ndarray:
    n = np.zeros_like(pos)
    tri = pos[idx.reshape(-1, 3)]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    for k in range(3):
        np.add.at(n, idx.reshape(-1, 3)[:, k], fn)
    return n / np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)


def flat(tris: list) -> tuple[np.ndarray, np.ndarray]:
    """Unshared vertices per triangle: crisp faceted shading, like a cut gem or a toy."""
    pos = np.array([v for t in tris for v in t], dtype=np.float32)
    return pos, np.arange(len(pos), dtype=np.uint32)


def star() -> list:
    pts, depth = [], 0.22
    for i in range(10):
        a = math.pi / 2 + i * math.pi / 5
        r = 1.0 if i % 2 == 0 else 0.46
        pts.append((r * math.cos(a), r * math.sin(a)))
    front, back = (0, 0, depth), (0, 0, -depth)
    tris = []
    for i in range(10):
        p, q = pts[i], pts[(i + 1) % 10]
        a, b = (p[0], p[1], 0.0), (q[0], q[1], 0.0)
        tris.append((front, a, b))   # bevelled front: rim at z=0 rises to a point
        tris.append((back, b, a))
    return [("gold", *flat(tris))]


def box(x0, y0, z0, x1, y1, z1) -> list:
    v = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    f = [(0, 2, 1), (0, 3, 2), (4, 5, 6), (4, 6, 7), (0, 1, 5), (0, 5, 4), (3, 7, 6), (3, 6, 2), (0, 4, 7), (0, 7, 3), (1, 2, 6), (1, 6, 5)]
    return [(v[a], v[b], v[c]) for a, b, c in f]


def dome(x0, x1, y, z0, z1, segs=14, r=None) -> list:
    """Half-cylinder lid along x, curving up from the box top."""
    rz = (z1 - z0) / 2
    cz = (z0 + z1) / 2
    ring = [(cz + rz * math.cos(math.pi * i / segs), y + rz * 0.9 * math.sin(math.pi * i / segs)) for i in range(segs + 1)]
    tris = []
    for i in range(segs):
        (za, ya), (zb, yb) = ring[i], ring[i + 1]
        tris += [((x0, ya, za), (x1, ya, za), (x1, yb, zb)), ((x0, ya, za), (x1, yb, zb), (x0, yb, zb))]
        tris += [((x0, y, cz), (x0, ya, za), (x0, yb, zb)), ((x1, y, cz), (x1, yb, zb), (x1, ya, za))]
    return tris


def chest() -> list:
    wood = box(-1, -0.6, -0.65, 1, 0.25, 0.65) + dome(-1, 1, 0.25, -0.65, 0.65)
    gold = []
    for x in (-0.72, 0.62):  # two gold bands over body and lid
        gold += box(x, -0.62, -0.67, x + 0.1, 0.27, 0.67) + dome(x, x + 0.1, 0.27, -0.69, 0.69)
    gold += box(-0.16, -0.15, 0.64, 0.16, 0.22, 0.72)  # the lock
    return [("wood", *flat(wood)), ("gold", *flat(gold))]


MATERIALS = {  # glTF colours are linear: these read as warm gold and dark polished wood on screen
    "gold": {"pbrMetallicRoughness": {"baseColorFactor": [1.0, 0.56, 0.1, 1.0], "metallicFactor": 1.0, "roughnessFactor": 0.22},
             "emissiveFactor": [0.32, 0.17, 0.02]},
    "wood": {"pbrMetallicRoughness": {"baseColorFactor": [0.2, 0.065, 0.02, 1.0], "metallicFactor": 0.0, "roughnessFactor": 0.55},
             "emissiveFactor": [0.03, 0.01, 0.0]},
}


def write_glb(path: Path, parts: list) -> None:
    blob, views, accessors, prims, mats = b"", [], [], [], []
    for name, pos, idx in parts:
        pos = pos.astype(np.float32)
        nrm = normals(pos, idx).astype(np.float32)
        for j, (arr, target, comp, typ) in enumerate(((pos, 34962, 5126, "VEC3"), (nrm, 34962, 5126, "VEC3"), (idx.astype(np.uint32), 34963, 5125, "SCALAR"))):
            data = arr.tobytes()
            views.append({"buffer": 0, "byteOffset": len(blob), "byteLength": len(data), "target": target})
            acc = {"bufferView": len(views) - 1, "componentType": comp, "count": int(arr.shape[0]), "type": typ}
            if j == 0:  # POSITION needs its bounds
                acc["min"], acc["max"] = arr.min(0).tolist(), arr.max(0).tolist()
            accessors.append(acc)
            blob += data + b"\0" * ((4 - len(data) % 4) % 4)
        if name not in [m["name"] for m in mats]:
            mats.append({"name": name, **MATERIALS[name]})
        mi = [m["name"] for m in mats].index(name)
        a = len(accessors) - 3
        prims.append({"attributes": {"POSITION": a, "NORMAL": a + 1}, "indices": a + 2, "material": mi})
    gltf = {"asset": {"version": "2.0", "generator": "rosetta make_models.py"}, "scene": 0, "scenes": [{"nodes": [0]}],
            "nodes": [{"mesh": 0, "name": path.stem}], "meshes": [{"primitives": prims}], "materials": mats,
            "buffers": [{"byteLength": len(blob)}], "bufferViews": views, "accessors": accessors}
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * ((4 - len(js) % 4) % 4)
    out = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob))
    out += struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(blob), 0x004E4942) + blob
    path.write_bytes(out)
    print(path.name, len(out) // 1024, "KB")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    write_glb(OUT / "star.glb", star())
    write_glb(OUT / "chest.glb", chest())
