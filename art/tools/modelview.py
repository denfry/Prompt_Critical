"""Просмотр Java-моделей блоков без Blockbench: ортографический рендер с текстурами и светом граней как в игре.

usage: python art/tools/modelview.py <repo_root> <out.png> <model> [<model> ...] [--scale N] [--yaw A,B] [--row K]
<model> — путь модели от models/block без .json (например coal_mill или coal_mill/partial/drum);
`a+b` рисует модели a и b вместе в одной сцене (машина и её partial-детали).
Каждая модель — два ракурса (по умолчанию спереди-слева 325° и сзади-справа 145°); --row K — K моделей в ряд.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import hqkit  # noqa: E402
from hqview import BG, SHADE, hstack, vstack  # noqa: E402

ASSETS = "pc-mod/src/main/resources/assets/promptcritical"


def load(root, mid, cache):
    with open(os.path.join(root, ASSETS, "models/block", mid + ".json"), encoding="utf8") as f:
        j = json.load(f)
    if not j.get("elements") and j.get("parent", "").startswith("promptcritical:block/"):
        return load(root, j["parent"][len("promptcritical:block/"):], cache)
    tex = j.get("textures", {})

    def resolve(ref):
        while ref.startswith("#"):
            ref = tex.get(ref[1:], "")
        ns, p = ref.split(":") if ":" in ref else ("minecraft", ref)
        key = ns + ":" + p
        if key not in cache:
            base = os.path.join(root, ASSETS, "textures", p + ".png") if ns == "promptcritical" else None
            if base and os.path.exists(base):
                im = np.array(Image.open(base).convert("RGBA"))
                if im.shape[0] > im.shape[1]:          # animated strip: first frame
                    im = im[: im.shape[1]]
            else:
                im = np.full((16, 16, 4), (255, 0, 255, 255), np.uint8)
            cache[key] = im
        return cache[key]

    quads = []
    for e in j.get("elements", []):
        f, t = np.array(e["from"], float), np.array(e["to"], float)
        R, o = np.eye(3), np.zeros(3)
        r = e.get("rotation")
        if r and r.get("angle"):
            R, o = hqkit.rotmat(r["axis"], r["angle"]), np.array(r["origin"], float)
        for d, fc in e.get("faces", {}).items():
            na, ns, ua, us, va, vs = hqkit.FACES[d]
            p = np.zeros(3)
            p[na] = t[na] if ns > 0 else f[na]
            p[ua] = f[ua] if us > 0 else t[ua]
            p[va] = f[va] if vs > 0 else t[va]
            eu, ev, n = np.zeros(3), np.zeros(3), np.zeros(3)
            eu[ua], ev[va], n[na] = us * (t[ua] - f[ua]), vs * (t[va] - f[va]), ns
            im = resolve(fc["texture"])
            uv = fc.get("uv", [0, 0, 16, 16])
            k = im.shape[1] / 16.0
            quads.append(((p - o) @ R.T + o, R @ eu, R @ ev, R @ n, [u * k for u in uv], im,
                          e.get("name", "").startswith("glow_")))
    return quads


def render(qs, yaw, pitch=28.0, scale=6):
    cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw))
    cp, sp = np.cos(np.radians(pitch)), np.sin(np.radians(pitch))
    fwd = np.array([-sy * cp, -sp, cy * cp])
    right = np.cross(fwd, np.array([0, 1, 0.0]))
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    pts = np.concatenate([[q[0], q[0] + q[1], q[0] + q[2], q[0] + q[1] + q[2]] for q in qs])
    sx, sy_ = pts @ right, pts @ up
    x0, y1 = sx.min(), sy_.max()
    W, H = int((sx.max() - x0) * scale) + 3, int((y1 - sy_.min()) * scale) + 3
    img = np.tile(BG, (H, W, 1))
    zb = np.full((H, W), np.inf)
    for p, eu, ev, n, uv, atlas, glow in qs:
        if n @ fwd >= -1e-6:
            continue
        a = int(np.argmax(np.abs(n)))
        shade = 1.0 if glow else SHADE[(a, 1 if n[a] > 0 else -1)]
        o2 = np.array([(p @ right - x0) * scale + 1, (y1 - p @ up) * scale + 1])
        m = np.array([[eu @ right * scale, ev @ right * scale], [-(eu @ up) * scale, -(ev @ up) * scale]])
        if abs(np.linalg.det(m)) < 1e-9:
            continue
        inv = np.linalg.inv(m)
        cs = np.array([o2, o2 + m[:, 0], o2 + m[:, 1], o2 + m[:, 0] + m[:, 1]])
        bx0, by0 = np.floor(cs.min(axis=0)).astype(int)
        bx1, by1 = np.ceil(cs.max(axis=0)).astype(int)
        bx0, by0, bx1, by1 = max(bx0, 0), max(by0, 0), min(bx1, W), min(by1, H)
        if bx1 <= bx0 or by1 <= by0:
            continue
        yy, xx = np.mgrid[by0:by1, bx0:bx1]
        d = np.stack([xx + 0.5 - o2[0], yy + 0.5 - o2[1]], axis=-1) @ inv.T
        ia, ib = d[..., 0], d[..., 1]
        depth = p @ fwd + ia * (eu @ fwd) + ib * (ev @ fwd)
        ok = (ia >= 0) & (ia < 1) & (ib >= 0) & (ib < 1) & (depth < zb[by0:by1, bx0:bx1])
        if not ok.any():
            continue
        u = uv[0] + ia * (uv[2] - uv[0])
        v = uv[1] + ib * (uv[3] - uv[1])
        tx = np.clip(np.floor(u).astype(int), 0, atlas.shape[1] - 1)
        ty = np.clip(np.floor(v).astype(int), 0, atlas.shape[0] - 1)
        col = atlas[ty, tx].astype(float)
        ok &= col[..., 3] > 127
        col[..., :3] *= shade
        sub, zs = img[by0:by1, bx0:bx1], zb[by0:by1, bx0:bx1]
        sub[ok] = np.clip(col[ok], 0, 255).astype(np.uint8)
        zs[ok] = depth[ok]
    return img


def main():
    argv = sys.argv[1:]
    opt = {}
    for k in ("--scale", "--yaw", "--row"):
        if k in argv:
            i = argv.index(k)
            opt[k] = argv[i + 1]
            del argv[i:i + 2]
    root, out, ids = argv[0], argv[1], argv[2:]
    scale = int(opt.get("--scale", 6))
    yaws = [float(a) for a in opt.get("--yaw", "325,145").split(",")]
    per_row = int(opt.get("--row", 1))
    cache, tiles = {}, []
    for mid in ids:
        qs = []
        for part in mid.split("+"):
            qs += load(root, part, cache)
        if not qs:
            print(mid, "no elements")
            continue
        tiles.append(hstack([render(qs, y, 28, scale) for y in yaws]))
    rows = [hstack(tiles[i:i + per_row]) for i in range(0, len(tiles), per_row)]
    Image.fromarray(vstack(rows)).save(out)
    print("wrote", out, len(tiles), "models")


if __name__ == "__main__":
    main()
