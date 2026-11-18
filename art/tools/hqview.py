"""Быстрый просмотр HQ-моделей без Blockbench: ортографический рендер с освещением граней как в игре.

usage: python art/tools/hqview.py <repo_root> <out.png> <id> [<id> ...] [--scale N] [--atlas]
Каждая модель — два ракурса (спереди-слева и сзади-справа); --atlas добавляет увеличенный атлас.
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import hqkit  # noqa: E402

SHADE = {(1, 1): 1.0, (1, -1): 0.5, (2, 1): 0.8, (2, -1): 0.8, (0, 1): 0.6, (0, -1): 0.6}
BG = np.array([232, 235, 228, 255], np.uint8)


def quads(els):
    """Вершины граней в мире: (P00, E_u, E_v, нормаль, uv-прямоугольник)."""
    out = []
    for e in els:
        f, t = np.array(e["from"], float), np.array(e["to"], float)
        R, o = np.eye(3), np.zeros(3)
        if "rot" in e:
            R, o = hqkit.rotmat(e["rot"]["axis"], e["rot"]["angle"]), np.array(e["rot"]["origin"], float)
        for d, uv in e["uv"].items():
            na, ns, ua, us, va, vs = hqkit.FACES[d]
            p = np.zeros(3)
            p[na] = t[na] if ns > 0 else f[na]
            p[ua] = f[ua] if us > 0 else t[ua]
            p[va] = f[va] if vs > 0 else t[va]
            eu, ev, n = np.zeros(3), np.zeros(3), np.zeros(3)
            eu[ua], ev[va], n[na] = us * (t[ua] - f[ua]), vs * (t[va] - f[va]), ns
            out.append(((p - o) @ R.T + o, R @ eu, R @ ev, R @ n, uv))
    return out


def render(els, atlas, yaw, pitch=30.0, scale=10):
    """Ортографический кадр: yaw — откуда смотрим (градусы от севера по часовой), pitch — сверху."""
    cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw))
    cp, sp = np.cos(np.radians(pitch)), np.sin(np.radians(pitch))
    fwd = np.array([-sy * cp, -sp, cy * cp])          # от камеры в сцену
    right = np.cross(fwd, np.array([0, 1, 0.0]))
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    qs = quads(els)
    pts = np.concatenate([[p, p + eu, p + ev, p + eu + ev] for p, eu, ev, _, _ in qs])
    sx, sy_ = pts @ right, pts @ up
    x0, y1 = sx.min(), sy_.max()
    W, H = int((sx.max() - x0) * scale) + 3, int((y1 - sy_.min()) * scale) + 3
    img = np.tile(BG, (H, W, 1))
    zb = np.full((H, W), np.inf)
    for p, eu, ev, n, uv in qs:
        if n @ fwd >= -1e-6:
            continue
        a = np.argmax(np.abs(n))
        shade = SHADE[(int(a), 1 if n[a] > 0 else -1)]
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
        tx = np.clip((uv[0] + ia * (uv[2] - uv[0])).astype(int), 0, atlas.shape[1] - 1)
        ty = np.clip((uv[1] + ib * (uv[3] - uv[1])).astype(int), 0, atlas.shape[0] - 1)
        col = atlas[ty, tx].astype(float)
        ok &= col[..., 3] > 127
        col[..., :3] *= shade
        sub, zs = img[by0:by1, bx0:bx1], zb[by0:by1, bx0:bx1]
        sub[ok] = np.clip(col[ok], 0, 255).astype(np.uint8)
        zs[ok] = depth[ok]
    return img


def hstack(imgs, pad=12):
    h = max(i.shape[0] for i in imgs)
    out = np.tile(BG, (h, sum(i.shape[1] for i in imgs) + pad * (len(imgs) + 1), 1))
    x = pad
    for i in imgs:
        y = (h - i.shape[0]) // 2
        out[y:y + i.shape[0], x:x + i.shape[1]] = i
        x += i.shape[1] + pad
    return out


def vstack(imgs, pad=12):
    w = max(i.shape[1] for i in imgs)
    out = np.tile(BG, (sum(i.shape[0] for i in imgs) + pad * (len(imgs) + 1), w, 1))
    y = pad
    for i in imgs:
        out[y:y + i.shape[0], :i.shape[1]] = i
        y += i.shape[0] + pad
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    scale = int(sys.argv[sys.argv.index("--scale") + 1]) if "--scale" in sys.argv else 10
    if "--scale" in sys.argv:
        args.remove(str(scale))
    root, out, ids = args[0], args[1], args[2:]
    import glob
    import importlib
    for path in sorted(glob.glob(os.path.join(HERE, "hq_*.py"))):
        importlib.import_module(os.path.basename(path)[:-3])
    rows = []
    for m in hqkit.MODELS:
        if ids and m.id not in ids:
            continue
        els, atlas = m.build()
        row = [render(els, atlas, 325, 28, scale), render(els, atlas, 145, 28, scale)]
        if "--front" in sys.argv:
            row.append(render(els, atlas, 180, 0, scale))
        if "--atlas" in sys.argv:
            k = max(1, min(6, 480 // atlas.shape[0]))
            row.append(np.kron(atlas, np.ones((k, k, 1), np.uint8)))
        rows.append(hstack(row))
        print(f"{m.id}: {len(els)} boxes, {m.quads} faces, atlas {m.size}x{m.size}")
    Image.fromarray(vstack(rows)).save(out)


if __name__ == "__main__":
    main()
