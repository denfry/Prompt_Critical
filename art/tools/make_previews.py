"""Contact sheets for every model render, texture, item icon and fluid.

usage: python art/tools/make_previews.py <repo_root> <registry.json>
Inputs:  art/preview/models/<category>/<id>.png  (written by blockbench_build.js)
         pc-mod/src/main/resources/assets/promptcritical/textures/**.png
Outputs: art/preview/sheets/*.png
"""
import json, os, sys
from collections import defaultdict
from PIL import Image, ImageDraw, ImageFont

ROOT = sys.argv[1]
REG = json.load(open(sys.argv[2], encoding="utf8"))
TEX = os.path.join(ROOT, "pc-mod/src/main/resources/assets/promptcritical/textures")
OUT = os.path.join(ROOT, "art/preview/sheets")
os.makedirs(OUT, exist_ok=True)
BG, INK, MUTED = (232, 236, 229), (28, 36, 31), (95, 108, 99)


def font(size):
    for name in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


F_TITLE, F_LABEL = font(22), font(12)


def sheet(title, tiles, cell, cols, img_size, out):
    """tiles: [(label, PIL image)]; images are scaled with NEAREST to img_size."""
    if not tiles:
        return
    rows = (len(tiles) + cols - 1) // cols
    top = 44
    im = Image.new("RGB", (cols * cell[0] + 16, top + rows * cell[1] + 8), BG)
    d = ImageDraw.Draw(im)
    d.text((12, 12), f"{title}  ({len(tiles)})", fill=INK, font=F_TITLE)
    for i, (label, tile) in enumerate(tiles):
        x, y = 8 + (i % cols) * cell[0], top + (i // cols) * cell[1]
        t = tile.convert("RGBA")
        if t.size != img_size:
            t = t.resize(img_size, Image.NEAREST)
        im.paste(t, (x + (cell[0] - img_size[0]) // 2, y), t)
        text = label if len(label) <= 34 else label[:33] + "…"
        tw = d.textlength(text, font=F_LABEL)
        d.text((x + max(2, (cell[0] - tw) / 2), y + img_size[1] + 3), text, fill=MUTED, font=F_LABEL)
    im.save(out, optimize=True)
    return out


written = []

# models, one sheet per category
models = os.path.join(ROOT, "art/preview/models")
for cat in sorted(os.listdir(models)) if os.path.isdir(models) else []:
    files = sorted(f for f in os.listdir(os.path.join(models, cat)) if f.endswith(".png"))
    tiles = [(f[:-4].replace("__", "/"), Image.open(os.path.join(models, cat, f))) for f in files]
    written.append(sheet(f"Модели · {cat}", tiles, (200, 220), 6, (192, 192), os.path.join(OUT, f"models_{cat}.png")))

# block textures grouped by the module that registered them
owner = REG.get("texture_owner", {})
groups = defaultdict(list)
for rel, mod in owner.items():
    if rel.startswith("block/") and not rel.startswith("block/fluid/"):
        groups[mod.replace("tex_", "")].append(rel)
for area, rels in sorted(groups.items()):
    tiles = []
    for rel in sorted(rels):
        p = os.path.join(TEX, rel)
        if os.path.exists(p):
            img = Image.open(p)
            if img.size[1] > img.size[0]:  # animated strip: first frame
                img = img.crop((0, 0, img.size[0], img.size[0]))
            tiles.append((rel[len("block/"):-4], img))
    written.append(sheet(f"Текстуры блоков · {area}", tiles, (120, 128), 10, (96, 96), os.path.join(OUT, f"textures_{area}.png")))

# item icons
tiles = []
for it in REG.get("items", []):
    p = os.path.join(TEX, it["tex"])
    if os.path.exists(p):
        tiles.append((it["id"], Image.open(p)))
written.append(sheet("Предметы", tiles, (120, 128), 10, (96, 96), os.path.join(OUT, "items.png")))

# armor layers
arm = os.path.join(TEX, "models/armor")
if os.path.isdir(arm):
    tiles = [(f[:-4], Image.open(os.path.join(arm, f))) for f in sorted(os.listdir(arm)) if f.endswith(".png")]
    written.append(sheet("Слои брони / СИЗ", tiles, (272, 160), 4, (256, 128), os.path.join(OUT, "armor.png")))

# fluids: first frame of still + flow
tiles = []
for fl in REG.get("fluids", []):
    for suffix in ("still", "flow"):
        p = os.path.join(TEX, f"block/fluid/{fl['id']}_{suffix}.png")
        if os.path.exists(p):
            img = Image.open(p)
            tiles.append((f"{fl['id']} {suffix}", img.crop((0, 0, img.size[0], img.size[0]))))
written.append(sheet("Жидкости и газы", tiles, (120, 128), 10, (96, 96), os.path.join(OUT, "fluids.png")))

print("\n".join(os.path.relpath(w, ROOT) for w in written if w))
