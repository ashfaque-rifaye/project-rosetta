"""Rosetta's 3D moments, built from code as glTF binaries (no modelling tool, no downloaded assets).

  python assets/models/make_models.py   ->  tv-app/app/src/main/assets/models/{star,chest}.glb

star.glb  : a chunky five-point star, bevelled faces, warm gold (the Family Goal star that flies in)
chest.glb : a treasure chest, wood body, gold bands and a domed lid (the wish list / rewards)
coin.glb  : a gold coin with a star on each face (coins earned)
padlock.glb / tv.glb : TV time, locked and unlocked
trophy.glb: Movie Night
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


def smooth(rings: list) -> tuple[np.ndarray, np.ndarray]:
    """A surface from rings of points (each ring the same length, closed around): smooth shading for round things."""
    n, m = len(rings), len(rings[0])
    pos = np.array([v for r in rings for v in r], dtype=np.float32)
    idx = []
    for i in range(n - 1):
        for j in range(m):
            a, b, c, d = i * m + j, i * m + (j + 1) % m, (i + 1) * m + (j + 1) % m, (i + 1) * m + j
            idx += [a, b, c, a, c, d]
    return pos, np.array(idx, dtype=np.uint32)


def lathe(profile: list, segs: int = 40) -> tuple[np.ndarray, np.ndarray]:
    """Revolve a (radius, y) profile around the y axis: cups, coins, bases."""
    return smooth([[(r * math.cos(2 * math.pi * k / segs), y, r * math.sin(2 * math.pi * k / segs)) for k in range(segs)] for r, y in profile])


def tube(path: list, radius: float, segs: int = 16) -> tuple[np.ndarray, np.ndarray]:
    """A round tube along a path of points: padlock shackles, trophy handles."""
    rings = []
    for i, p in enumerate(path):
        q = path[min(i + 1, len(path) - 1)] if i < len(path) - 1 else p
        o = path[max(i - 1, 0)]
        t = np.array(q) - np.array(o)
        t = t / max(np.linalg.norm(t), 1e-9)
        up = np.array([0.0, 0.0, 1.0]) if abs(t[2]) < 0.9 else np.array([1.0, 0.0, 0.0])
        u = np.cross(t, up); u /= np.linalg.norm(u)
        v = np.cross(t, u)
        rings.append([tuple(np.array(p) + radius * (math.cos(2 * math.pi * k / segs) * u + math.sin(2 * math.pi * k / segs) * v)) for k in range(segs)])
    return smooth(rings)


def coin() -> list:
    face = [(0.0, 0.12), (0.82, 0.12), (0.9, 0.1), (1.0, 0.06), (1.0, -0.06), (0.9, -0.1), (0.82, -0.12), (0.0, -0.12)]
    body = lathe(face, 48)
    body = (np.stack([body[0][:, 0], body[0][:, 2], body[0][:, 1]], 1), body[1])  # stand it up, facing the camera
    emblem = []
    pts = [(0.55 * math.cos(math.pi / 2 + i * math.pi / 5) * (1 if i % 2 == 0 else 0.45), 0.55 * math.sin(math.pi / 2 + i * math.pi / 5) * (1 if i % 2 == 0 else 0.45)) for i in range(10)]
    for z, sgn in ((0.12, 1), (-0.12, -1)):
        tip = (0.0, 0.0, z + 0.06 * sgn)
        for i in range(10):
            a, b = pts[i], pts[(i + 1) % 10]
            emblem.append((tip, (a[0], a[1], z), (b[0], b[1], z)) if sgn > 0 else (tip, (b[0], b[1], z), (a[0], a[1], z)))
    return [("gold", *body), ("goldbright", *flat(emblem))]


def padlock() -> list:
    body = box(-0.62, -0.75, -0.25, 0.62, 0.15, 0.25)
    keyhole = box(-0.09, -0.45, 0.24, 0.09, -0.15, 0.27)
    arc = [(0.42 * math.cos(math.pi * k / 24), 0.15 + 0.12 + 0.48 * math.sin(math.pi * k / 24), 0.0) for k in range(25)]
    shackle = tube([(0.42, 0.1, 0.0)] + arc + [(-0.42, 0.1, 0.0)], 0.11)
    return [("gold", *flat(body)), ("steel", *shackle), ("ink", *flat(keyhole))]


def tv() -> list:
    frame = box(-1.0, -0.45, -0.12, 1.0, 0.75, 0.12)
    screen = box(-0.88, -0.35, 0.11, 0.88, 0.65, 0.14)
    stand = box(-0.08, -0.75, -0.06, 0.08, -0.45, 0.06) + box(-0.45, -0.8, -0.22, 0.45, -0.74, 0.22)
    return [("ink", *flat(frame + stand)), ("screen", *flat(screen))]


def trophy() -> list:
    cup = lathe([(0.0, 1.0), (0.62, 1.0), (0.6, 0.75), (0.5, 0.45), (0.3, 0.2), (0.12, 0.05), (0.1, -0.3), (0.3, -0.42), (0.32, -0.5), (0.0, -0.5)], 40)
    handles = []
    for sgn in (1, -1):
        arc = [(sgn * (0.55 + 0.25 * math.sin(math.pi * k / 16)), 0.85 - 0.5 * k / 16, 0.0) for k in range(17)]
        handles.append(tube(arc, 0.06))
    base = box(-0.55, -0.75, -0.4, 0.55, -0.5, 0.4)
    hp = np.concatenate([handles[0][0], handles[1][0]])
    hi = np.concatenate([handles[0][1], handles[1][1] + len(handles[0][0])])
    return [("gold", *cup), ("gold", hp, hi), ("wood", *flat(base))]


MATERIALS = {  # glTF colours are linear: these read as warm gold and dark polished wood on screen
    "gold": {"pbrMetallicRoughness": {"baseColorFactor": [1.0, 0.56, 0.1, 1.0], "metallicFactor": 1.0, "roughnessFactor": 0.22},
             "emissiveFactor": [0.32, 0.17, 0.02]},
    "wood": {"pbrMetallicRoughness": {"baseColorFactor": [0.2, 0.065, 0.02, 1.0], "metallicFactor": 0.0, "roughnessFactor": 0.55},
             "emissiveFactor": [0.03, 0.01, 0.0]},
    "goldbright": {"pbrMetallicRoughness": {"baseColorFactor": [1.0, 0.75, 0.25, 1.0], "metallicFactor": 1.0, "roughnessFactor": 0.15},
                   "emissiveFactor": [0.55, 0.36, 0.06]},
    "steel": {"pbrMetallicRoughness": {"baseColorFactor": [0.75, 0.78, 0.85, 1.0], "metallicFactor": 1.0, "roughnessFactor": 0.25},
              "emissiveFactor": [0.12, 0.13, 0.16]},
    "ink": {"pbrMetallicRoughness": {"baseColorFactor": [0.02, 0.025, 0.05, 1.0], "metallicFactor": 0.3, "roughnessFactor": 0.4}},
    "screen": {"pbrMetallicRoughness": {"baseColorFactor": [0.2, 0.35, 1.0, 1.0], "metallicFactor": 0.0, "roughnessFactor": 0.2},
               "emissiveFactor": [0.25, 0.55, 1.0]},
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
            mats.append({"name": name, "doubleSided": True, **MATERIALS[name]})  # generated meshes: never lose a face to culling
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
    write_glb(OUT / "coin.glb", coin())
    write_glb(OUT / "padlock.glb", padlock())
    write_glb(OUT / "tv.glb", tv())
    write_glb(OUT / "trophy.glb", trophy())
