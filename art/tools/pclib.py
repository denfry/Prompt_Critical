"""Shared helpers for Prompt Critical placeholder art.

Textures: register with TEXTURES["block/<path>.png"] = fn  (fn() -> numpy RGBA array).
Models:   model(id, category, {key: texture_rel}, [elements], java_path, kind)
Items:    item(id, texture_rel)  -> flat item/generated model
Fluids:   fluid(id, base_hex, kind="liquid"|"gas")  -> still/flow animated textures
"""
import math, os, random
import numpy as np
from PIL import Image

TEX = None  # set by build.py
rng = random.Random(1986)
class _Registry(dict):
    """Texture registry that refuses silent overrides between modules."""

    def __setitem__(self, k, v):
        if k in self:
            raise KeyError(f"texture '{k}' already registered")
        super().__setitem__(k, v)

    def update(self, other=(), **kw):
        for k, v in dict(other, **kw).items():
            self[k] = v


TEXTURES = _Registry()
M = []
ITEMS = []
FLUIDS = []

T = lambda n: f"block/common/{n}.png"


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)] + [255], dtype=float)


def canvas(base, n=16, noise=8):
    a = np.zeros((n, n, 4))
    a[:, :] = hexc(base)
    for y in range(n):
        for x in range(n):
            d = rng.uniform(-noise, noise)
            a[y, x, :3] += d
    return a


def shade(a, y, x, k):
    a[y, x, :3] = a[y, x, :3] * k


def put(a, y, x, col):
    a[y, x] = hexc(col) if isinstance(col, str) else col


def rect(a, x0, y0, x1, y1, col, k=None):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if k is not None:
                shade(a, y, x, k)
            else:
                put(a, y, x, col)


def frame(a, k_light=1.18, k_dark=0.72, inset=0):
    n = a.shape[0]
    lo, hi = inset, n - 1 - inset
    for i in range(lo, hi + 1):
        shade(a, lo, i, k_light)
        shade(a, i, lo, k_light)
        shade(a, hi, i, k_dark)
        shade(a, i, hi, k_dark)


def rivet(a, y, x):
    shade(a, y, x, 1.35)
    if y + 1 < a.shape[0] and x + 1 < a.shape[1]:
        shade(a, y + 1, x + 1, 0.6)


def save(a, rel):
    p = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(p)
    return rel




def item(id_, tex_rel):
    ITEMS.append({"id": id_, "tex": tex_rel})


def fluid(id_, base, kind="liquid"):
    FLUIDS.append({"id": id_, "base": base, "kind": kind})


def el(name, f, t, tex, faces=None, rot=None, full=None):
    """tex: default texture key; faces: per-face override {dir: key}; rot: (axis, angle, origin)."""
    e = {"name": name, "from": f, "to": t, "tex": tex, "faces": faces or {}}
    if rot:
        e["rot"] = {"axis": rot[0], "angle": rot[1], "origin": rot[2]}
    if full:
        e["full"] = full  # faces mapped to whole texture (0..16)
    return e


def disc(name, axis, r, a0, a1, tex, sides=16, faces=None, c=(8, 8, 8)):
    """Regular n-gon prism (n=8|16) as a union of bars through the axis.

    A bar of half-width r*tan(pi/n) and half-length r, rotated in steps of 360/n,
    covers exactly the n-gon with inradius r. Java only allows ±22.5/±45 on one
    axis, so 67.5/90/112.5 come from bars with swapped dimensions. End faces are
    staggered by 0.01 to avoid z-fighting.
    """
    import math
    h = r * math.tan(math.pi / sides)
    plane = [ax for ax in "xyz" if ax != axis]
    if sides == 8:
        bars = [(0, 0), (1, 0), (0, 45), (0, -45)]
    else:
        bars = [(0, 0), (0, 22.5), (0, 45), (0, -22.5), (0, -45), (1, 0), (1, 22.5), (1, -22.5)]
    out = []
    for i, (swap, ang) in enumerate(bars):
        half = {plane[0]: (h if swap else r), plane[1]: (r if swap else h)}
        f, t = [0, 0, 0], [0, 0, 0]
        for k, ax in enumerate("xyz"):
            if ax == axis:
                f[k], t[k] = a0 - 0.01 * i, a1 + 0.01 * i
            else:
                f[k], t[k] = c[k] - half[ax], c[k] + half[ax]
        f = [round(v, 3) for v in f]
        t = [round(v, 3) for v in t]
        out.append(el(f"{name}_{i}", f, t, tex, faces, (axis, ang, list(c)) if ang else None))
    return out


def ring_xz(name, r, w, y0, y1, tex, c=8):
    """Flat octagonal ring around Y (handwheel)."""
    out = []
    for ang in (0, 45):
        o = [c, (y0 + y1) / 2, c]
        bars = [
            ([c - r, y0, c - r], [c + r, y1, c - r + w]),
            ([c - r, y0, c + r - w], [c + r, y1, c + r]),
            ([c - r, y0, c - r + w], [c - r + w, y1, c + r - w]),
            ([c + r - w, y0, c - r + w], [c + r, y1, c + r - w]),
        ]
        for i, (f, t) in enumerate(bars):
            out.append(el(f"{name}_{int(ang)}_{i}", f, t, tex, rot=("y", ang, o) if ang else None))
    return out


M = []


def model(id_, cat, textures, elements, java, kind="block"):
    M.append({"id": id_, "cat": cat, "textures": textures, "elements": elements, "java": java, "kind": kind})


