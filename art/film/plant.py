"""The whole plant for the overview film: every machine of the mod, grouped into districts by industry.

The block list and each block's cells (facing north, controller at the origin) come from plant_catalog.json, which
the dev client writes (pc-mod/src/gametest/.../film/FilmCatalog.java, trigger file catalog.request in run-film).
Districts follow the sections of PcBlocks.java. Inside a district machines are packed in rows, low ones in front so the
camera sees every front (facing north, towards -z). Running and lit states are set where a block has them.

Called from filmset.write (PARTS); the plant stands east of the I0 set, x 170.., z 0..
"""
import json
import os
import re

from filmset import G, PC

HERE = os.path.dirname(os.path.abspath(__file__))
PCBLOCKS = os.path.join(HERE, "..", "..", "pc-mod", "src", "main", "java", "dev", "denfry", "promptcritical", "mod",
                        "registry", "PcBlocks.java")
KINDS = {"machine", "containment", "movable"}  # material helpers of machines in PcBlocks
IC_SECTIONS = (17, 18, 19, 43)  # automation: relays, analog blocks and switches are instruments

# district id, label (RU / EN, for overlays), section numbers of PcBlocks.java "// --- ... ---" headers
DISTRICTS = [
    ("coal", "Уголь и пар", "Coal and steam", (2, 11)),
    ("power", "Энергетика", "Power", (3, 37)),
    ("metal", "Металлургия", "Metallurgy", (32, 33, 34, 35)),
    ("oil", "Нефть и газ", "Oil and gas", (12, 13, 14, 21, 22)),
    ("chem", "Химия", "Chemistry", (7, 20, 24, 25, 26)),
    ("fuel", "Ядерное топливо", "Nuclear fuel", (15, 16, 23, 27)),
    ("npp", "АЭС", "Nuclear power", (5, 6, 9, 28, 29, 30, 31)),
    ("ic", "Автоматика", "Automation", (17, 18, 19, 43)),
    ("ops", "Эксплуатация", "Operations", (8, 10, 36, 38)),
]
OX, OZ = 170, 0          # plant origin
ROW_W = 54               # district inner width
GAP = 2                  # between machines
ROAD = 9                 # between districts
COLS = 3


def sections():
    """block id -> (section number, material helper) from the registration statements of PcBlocks.java."""
    src = open(PCBLOCKS, encoding="utf-8").read()
    heads = [m.start() for m in re.finditer(r"// --- .* ---", src)]
    out = {}
    for m in re.finditer(r'\(\s*"([a-z0-9_]+)"', src):
        stmt = src[m.start():src.find(";", m.start())]
        k = re.search(r"\b(machine|containment|movable|instrument|plate|sheet|shell|brick|graphite|wood|ore)\(\)", stmt)
        sec = sum(1 for h in heads if h < m.start()) - 1
        out.setdefault(m.group(1), (sec, k.group(1) if k else ""))
    return out


def bbox(cells):
    xs, ys, zs = zip(*cells)
    return min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)


def state(e):
    props = []
    if e["facing"]:
        props.append("facing=north")
    for p in ("running", "lit"):
        if p in e["props"]:
            props.append(p + "=true")
    return e["id"] + ("[" + ",".join(props) + "]" if props else "")


def machines():
    cat = json.load(open(os.path.join(HERE, "plant_catalog.json"), encoding="utf-8"))
    sec = sections()
    by = {d[0]: [] for d in DISTRICTS}
    for e in cat:
        name = e["id"].split(":")[1]
        s, kind = sec.get(name, (-1, ""))
        if kind not in KINDS and not (kind == "instrument" and s in IC_SECTIONS):
            continue
        for d in DISTRICTS:
            if s in d[3]:
                by[d[0]].append(e)
                break
    return by


def pack(items):
    """Shelf packing, low machines first: [(entry, dx, dz)] in district coordinates and the district size."""
    items = sorted(items, key=lambda e: (bbox(e["cells"])[3] - bbox(e["cells"])[2], e["id"]))
    placed, x, z, row_d = [], 0, 0, 0
    for e in items:
        x0, x1, _, _, z0, z1 = bbox(e["cells"])
        w, d = x1 - x0 + 1, z1 - z0 + 1
        if x and x + w > ROW_W:
            x, z, row_d = 0, z + row_d + GAP + 1, 0
        placed.append((e, x - x0, z - z0))
        x += w + GAP
        row_d = max(row_d, d)
    return placed, ROW_W, z + row_d


def layout():
    """[(district, ox, oz, w, d, placed)] with districts on a COLS-wide grid."""
    by = machines()
    packed = [(d, *pack(by[d[0]])) for d in DISTRICTS]
    out, col_w = [], ROW_W + 2 * 3
    z = OZ
    for r in range(0, len(packed), COLS):
        row = packed[r:r + COLS]
        depth = max(p[3] for p in row) + 6
        for c, (d, placed, w, dd) in enumerate(row):
            out.append((d, OX + c * (col_w + ROAD), z, col_w, depth, placed))
        z += depth + ROAD
    return out


def bounds():
    lay = layout()
    x2 = max(ox + w for _, ox, _, w, _, _ in lay)
    z2 = max(oz + d for _, _, oz, _, d, _ in lay)
    return OX - ROAD, OZ - ROAD, x2 + ROAD, z2 + ROAD


def clear(f):
    x1, z1, x2, z2 = bounds()
    f.fill(x1, -60, z1, x2, -20, z2, "air")
    f.fill(x1, G, z1, x2, G, z2, "andesite")  # roads


def build(f):
    for d, ox, oz, w, dd, placed in layout():
        f.fill(ox, G, oz, ox + w - 1, G, oz + dd - 1, PC + "painted_concrete")
        f.fill(ox, G, oz, ox + w - 1, G, oz, PC + "painted_concrete_yellow")
        for e, dx, dz in placed:
            miny = bbox(e["cells"])[2]
            f.setb(ox + 3 + dx, -60 - miny, oz + 3 + dz, state(e))


def forceload(f):
    x1, z1, x2, z2 = bounds()
    for x in range(x1, x2 + 1, 240):
        for z in range(z1, z2 + 1, 240):
            f(f"forceload add {x} {z} {min(x + 239, x2)} {min(z + 239, z2)}")


def report():
    for d, ox, oz, w, dd, placed in layout():
        print(f"{d[0]:6} {len(placed):3} machines  x {ox}..{ox + w - 1}  z {oz}..{oz + dd - 1}")


PARTS = (("plant_clear", clear), ("plant", build))

if __name__ == "__main__":
    report()
