"""Render plant_site.json as a shaded relief map with a coordinate grid, to lay out the plant by eye.

    python art/film/sitemap.py <plant_site.json> <out.png> [grid]
"""
import json
import sys

from PIL import Image, ImageDraw


def main(src, out, grid=64):
    d = json.load(open(src, encoding="utf-8"))
    nx, nz, step, sea = d["nx"], d["nz"], d["step"], d["sea"]
    fl, sf, bi, names = d["floor"], d["surface"], d["biome"], d["biomes"]
    img = Image.new("RGB", (nx, nz))
    px = img.load()
    for ix in range(nx):
        for iz in range(nz):
            k = ix * nz + iz
            h, s = fl[k], sf[k]
            name = names[bi[k]]
            if s - h > 0:
                depth = s - h
                c = (20, 60 + max(0, 90 - depth * 6), 150 + max(0, 80 - depth * 4))
                if "river" in name:
                    c = (40, 140, 220)
            else:
                e = h - sea
                west = fl[max(ix - 1, 0) * nz + iz]
                shade = max(-30, min(30, (h - west) * 6))
                if e < 3:
                    base = (205, 195, 140)
                elif e < 25:
                    base = (90 + e * 2, 150 + e, 70)
                elif e < 60:
                    base = (140 + e, 140 + e // 2, 100)
                else:
                    base = (230, 230, 230)
                if "forest" in name or "taiga" in name or "jungle" in name:
                    base = (base[0] - 30, base[1] - 10, base[2] - 30)
                c = tuple(max(0, min(255, v + shade)) for v in base)
            px[ix, iz] = c
    scale = max(1, 1024 // max(nx, nz))
    img = img.resize((nx * scale, nz * scale), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    x1, z1 = d["x1"], d["z1"]
    for gx in range((x1 // grid) * grid, x1 + nx * step, grid):
        X = (gx - x1) / step * scale
        dr.line([(X, 0), (X, img.height)], fill=(0, 0, 0), width=1)
        dr.text((X + 2, 2), str(gx), fill=(0, 0, 0))
    for gz in range((z1 // grid) * grid, z1 + nz * step, grid):
        Z = (gz - z1) / step * scale
        dr.line([(0, Z), (img.width, Z)], fill=(0, 0, 0), width=1)
        dr.text((2, Z + 2), str(gz), fill=(0, 0, 0))
    img.save(out)
    print(f"{out}: {img.width}x{img.height}, biomes {names}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], *(int(v) for v in sys.argv[3:]))
