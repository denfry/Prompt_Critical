"""Puff particle textures: four soft blobs of steam/smoke, 32 px, white with alpha (the particle tints them).

    python art/tools/puff_particles.py

Each blob is a few overlapping gaussian lumps with a value-noise edge, so the puffs of a cloud do not repeat one disc.
Writes textures/particle/puff_0..3.png and particles/{steam,smoke}_puff.json of the mod.
"""
import json
import math
import os
import random

from PIL import Image

ASSETS = os.path.join(os.path.dirname(__file__), "..", "..", "pc-mod", "src", "main", "resources", "assets", "promptcritical")
S = 32


def noise(rng, cells):
    g = [[rng.random() for _ in range(cells + 1)] for _ in range(cells + 1)]

    def at(x, y):
        fx, fy = x / S * cells, y / S * cells
        i, j = int(fx), int(fy)
        tx, ty = fx - i, fy - j
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = g[j][i] * (1 - tx) + g[j][i + 1] * tx
        b = g[j + 1][i] * (1 - tx) + g[j + 1][i + 1] * tx
        return a * (1 - ty) + b * ty
    return at


def puff(seed):
    rng = random.Random(seed)
    lumps = [(16 + rng.uniform(-3, 3), 16 + rng.uniform(-3, 3), rng.uniform(4.5, 6.5))]
    for _ in range(4):
        a = rng.uniform(0, 2 * math.pi)
        d = rng.uniform(3, 7)
        lumps.append((16 + d * math.cos(a), 16 + d * math.sin(a), rng.uniform(2.5, 4.5)))
    n1, n2 = noise(rng, 4), noise(rng, 8)
    im = Image.new("RGBA", (S, S))
    for y in range(S):
        for x in range(S):
            v = sum(math.exp(-((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) / (2 * r * r)) for cx, cy, r in lumps)
            v *= 0.75 + 0.5 * (0.65 * n1(x, y) + 0.35 * n2(x, y))
            edge = min(1.0, min(x + 0.5, y + 0.5, S - x - 0.5, S - y - 0.5) / 7)   # nothing reaches the quad's edge
            a = max(0.0, min(0.85, (v - 0.08) * 0.9)) ** 1.3 * edge
            shade = int(255 * (0.88 + 0.12 * n2(x, y)))
            im.putpixel((x, y), (shade, shade, shade, int(a * 255)))
    return im


def main():
    tex = os.path.join(ASSETS, "textures", "particle")
    os.makedirs(tex, exist_ok=True)
    for i in range(4):
        puff(7100 + i).save(os.path.join(tex, f"puff_{i}.png"))
    par = os.path.join(ASSETS, "particles")
    os.makedirs(par, exist_ok=True)
    for name in ("steam_puff", "smoke_puff"):
        with open(os.path.join(par, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"textures": [f"promptcritical:puff_{i}" for i in range(4)]}, fh, indent=2)
            fh.write("\n")
    print("4 puffs, 2 particle definitions")


if __name__ == "__main__":
    main()
