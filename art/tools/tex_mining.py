"""Ores, exploration, ore processing, hydrometallurgy, reagents, conversion (GDD 02 §2.1–2.4)."""
from pclib import *


# ---------- small painting helpers ----------
def row(a, y, k):
    for x in range(a.shape[1]):
        shade(a, y, x, k)


def col(a, x, k):
    for y in range(a.shape[0]):
        shade(a, y, x, k)


def seam_h(a, y):  # сварной/монтажный шов поперёк высоты
    row(a, y, 0.72)
    if y > 0:
        row(a, y - 1, 1.16)


def seam_v(a, x):
    col(a, x, 0.72)
    if x > 0:
        col(a, x - 1, 1.16)


def band_h(a, y0, y1, c):  # цветная опознавательная полоса (ГОСТ 14202)
    for y in range(y0, y1 + 1):
        for x in range(16):
            a[y, x, :3] = hexc(c)[:3] * rng.uniform(0.94, 1.06)


def band_v(a, x0, x1, c):
    for x in range(x0, x1 + 1):
        for y in range(16):
            a[y, x, :3] = hexc(c)[:3] * rng.uniform(0.94, 1.06)


def ink(a, y, x0, x1, c="#333333", p=0.65):  # «надпись» точками
    for x in range(x0, x1 + 1):
        if rng.random() < p:
            put(a, y, x, c)


def label(a, x0, y0, x1, y1, bg="#e6e2d2", c="#333333"):
    rect(a, x0, y0, x1, y1, bg)
    for y in range(y0, y1 + 1):
        if (y - y0) % 2 == 0 and x1 - x0 >= 2:
            ink(a, y, x0 + 1, x1 - 1, c)


def circle(a, cx, cy, r, c, r0=-1.0):
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if r0 <= d < r:
                put(a, y, x, c)


def sight(a, x0, y0, x1, y1, fill, rim="#2b2f33"):  # смотровое стекло с бликом
    rect(a, x0, y0, x1, y1, rim)
    rect(a, x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)
    for i in range(min(x1 - x0, y1 - y0) - 2):
        if x0 + 1 + i <= x1 - 1:
            shade(a, y0 + 1 + i, x0 + 1 + i, 1.3)


def smooth(base, noise=4):
    return canvas(base, noise=noise)


def plate(base, noise=5, k=(1.18, 0.72)):
    a = canvas(base, noise=noise)
    frame(a, *k)
    return a


# ---------- ore hosts ----------
def host_stone():
    a = canvas("#7f7f7f", noise=7)
    for _ in range(18):
        y, x = rng.randrange(16), rng.randrange(15)
        k = rng.choice((0.8, 0.88, 1.12))
        shade(a, y, x, k)
        shade(a, y, x + 1, k)
    return a


def host_deepslate():
    a = canvas("#4f4f55", noise=5)
    for y in range(16):
        row(a, y, 1.07 if y % 4 == 0 else 0.95 if y % 4 == 2 else 1.0)
    for _ in range(14):
        shade(a, rng.randrange(16), rng.randrange(16), 0.78)
    return a


def host_sandstone(base="#d6c690"):
    def f():
        a = canvas(base, noise=4)
        for y in (3, 4, 9, 13):
            row(a, y, 0.9 if y != 4 else 1.06)
        return a
    return f


def host_sand(base="#dbcf9f"):
    def f():
        a = canvas(base, noise=9)
        for _ in range(20):
            shade(a, rng.randrange(16), rng.randrange(16), 0.85)
        return a
    return f


def host_granite():
    a = canvas("#a07462", noise=8)
    for _ in range(16):
        put(a, rng.randrange(16), rng.randrange(16), "#d9bcae")
    for _ in range(10):
        put(a, rng.randrange(16), rng.randrange(16), "#3d3330")
    return a


def host_laterite():
    a = canvas("#a3532f", noise=10)
    for _ in range(14):
        y, x = rng.randrange(15), rng.randrange(15)
        rect(a, x, y, x + 1, y, "#bd7a40")
    return a


DIRS = ((0, 1), (1, 0), (0, -1), (-1, 0))


def grain(a, y, x, c, size, hl=None, dk=None, halo=None):
    pts = [(y, x)]
    for _ in range(size * 6):
        if len(pts) >= size:
            break
        py, px = rng.choice(pts)
        dy, dx = rng.choice(DIRS)
        ny, nx = py + dy, px + dx
        if 0 <= ny < 16 and 0 <= nx < 16 and (ny, nx) not in pts and abs(ny - y) <= 1 and abs(nx - x) <= 1:
            pts.append((ny, nx))
    for py, px in pts:  # тёмная кайма вокруг зерна — читается как ванильная руда
        for dy, dx in DIRS:
            ny, nx = py + dy, px + dx
            if 0 <= ny < 16 and 0 <= nx < 16 and (ny, nx) not in pts:
                shade(a, ny, nx, 0.86)
    if halo:
        for py, px in pts:
            for dy, dx in DIRS:
                ny, nx = py + dy, px + dx
                if 0 <= ny < 16 and 0 <= nx < 16 and (ny, nx) not in pts and rng.random() < halo[1]:
                    put(a, ny, nx, halo[0])
                    shade(a, ny, nx, rng.uniform(0.85, 1.1))
    for py, px in pts:
        put(a, py, px, c)
        shade(a, py, px, rng.uniform(0.9, 1.08))
    if hl:
        put(a, *min(pts), hl)
    if dk and len(pts) > 1:
        put(a, *max(pts), dk)


def ore(host, *specs):
    """specs: (color, count, (min_size, max_size), hl, dk, halo)"""
    def f():
        a = host()
        taken = []
        for c, n, (s0, s1), hl, dk, halo in specs:
            for _ in range(n):
                for _try in range(30):
                    y, x = rng.randrange(1, 15), rng.randrange(1, 15)
                    if all(abs(y - ty) + abs(x - tx) >= 4 for ty, tx in taken):
                        break
                taken.append((y, x))
                grain(a, y, x, c, rng.randint(s0, s1), hl, dk, halo)
        return a
    return f


PITCH = ("#1b1a17", 6, (4, 7), "#4a4a42", None, ("#bccb3c", 0.45))
PYRITE = ("#cdb23e", 7, (3, 5), "#f7ea8f", "#7d6a24", None)

ORE_TEX = {
    "uraninite_ore": ore(host_stone, PITCH),
    "deepslate_uraninite_ore": ore(host_deepslate, PITCH),
    "coffinite_ore": ore(host_sandstone("#b2a784"),
                         ("#1f1e1c", 9, (2, 4), "#4a4844", None, None), ("#3a3835", 5, (1, 3), None, None, None)),
    "carnotite_ore": ore(host_sandstone("#d9c48c"),
                         ("#f0d02a", 8, (3, 6), "#fff27a", "#c8a514", None)),
    "monazite_sand": ore(host_sand("#d8c99a"),
                         ("#c0662c", 11, (1, 3), "#e8a050", None, None), ("#2b2725", 5, (1, 1), None, None, None)),
    "zircon_sand": ore(host_sand("#cdc3a5"),
                       ("#f4ece6", 7, (1, 3), "#ffffff", None, None), ("#d9968c", 7, (1, 3), None, None, None),
                       ("#24221f", 6, (1, 2), None, None, None)),
    "fluorite_ore": ore(host_stone,
                        ("#8b4fc8", 5, (3, 6), "#c89ff2", "#55298a", None), ("#47b07c", 2, (3, 5), "#a4e4c2", "#24704a", None)),
    "pyrite_ore": ore(host_stone, PYRITE),
    "deepslate_pyrite_ore": ore(host_deepslate, PYRITE),
    "galena_ore": ore(host_stone, ("#3e4550", 6, (3, 6), "#e3e9f0", "#22262b", None)),
    "deepslate_galena_ore": ore(host_deepslate, ("#8a939e", 6, (3, 6), "#f0f4f8", "#3a3e44", None)),
    "bauxite_ore": ore(host_laterite,
                       ("#cf8048", 8, (2, 4), "#eab07a", "#6e3219", None), ("#7e351d", 4, (1, 2), None, None, None)),
    "borax_ore": ore(host_sand("#d6cba0"),
                     ("#f4f2ea", 9, (2, 4), "#ffffff", "#c9c6b8", None), ("#d3e2ea", 3, (1, 2), None, None, None)),
    "thorite_ore": ore(host_granite, ("#3a2a20", 6, (3, 5), "#6a4a35", None, ("#b35f2a", 0.4))),
}


# ---------- common mining materials ----------
def andesite():  # корпус Create-машин
    a = canvas("#8e908a", noise=9)
    frame(a, 1.2, 0.7)
    frame(a, 0.85, 1.1, inset=2)
    for y, x in ((1, 1), (1, 13), (13, 1), (13, 13)):
        rivet(a, y, x)
    return a


def copper():
    a = plate("#c06b40", 6)
    for _ in range(5):
        put(a, rng.randrange(2, 14), rng.randrange(2, 14), "#5fa88a")
    return a


def firebrick():
    a = canvas("#b8754a", noise=8)
    for y in range(16):
        for x in range(16):
            off = 4 if (y // 4) % 2 else 0
            if y % 4 == 3 or (x + off) % 8 == 7:
                put(a, y, x, "#d6c6a2")
                shade(a, y, x, rng.uniform(0.92, 1.04))
    return a


def hdpe():
    a = canvas("#202224", noise=3)
    for y in range(16):
        put(a, y, 5, "#3c4044")
        put(a, y, 6, "#2e3134")
    return a


def frp():
    a = canvas("#d6cfae", noise=5)
    for i in range(0, 16, 3):
        for t in range(16):
            x = (i + t) % 16
            shade(a, t, x, 0.93)
    return a


def pipe_paint(c, stripe=None):
    def f():
        a = canvas(c, noise=5)
        if stripe:
            for y in range(16):
                for x in range(16):
                    if (x + y) % 8 < 2:
                        put(a, y, x, stripe)
                        shade(a, y, x, rng.uniform(0.92, 1.05))
        return a
    return f


def glow_fire():
    a = canvas("#ff8a2a", noise=0)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 4:
                put(a, y, x, "#ffd36a")
            shade(a, y, x, max(0.8, 1.25 - d / 14))
    return a


def glow_coil():
    a = canvas("#4a160e", noise=3)
    for y in range(1, 16, 3):
        for x in range(1, 15):
            put(a, y, x, "#ff7a2a" if x % 4 else "#ffc04a")
        put(a, y + 1 if y + 1 < 16 else y, 14 if (y // 3) % 2 == 0 else 1, "#ff7a2a")
    return a


def pulp():
    a = canvas("#7b6f5b", noise=6)
    for i in range(6):
        y, x = rng.randrange(16), rng.randrange(13)
        for t in range(4):
            put(a, (y + (t % 2)) % 16, x + t, "#93876f")
    return a


def frost():
    a = canvas("#dde8ee", noise=6)
    for _ in range(14):
        y, x = rng.randrange(16), rng.randrange(16)
        put(a, y, x, "#ffffff")
    return a


def clad():  # алюминиевая обшивка изоляции, перехлёсты и бандажи
    a = canvas("#b9bec2", noise=3)
    for y in (4, 9, 14):
        seam_h(a, y)
    for y in (4, 9, 14):
        for x in (2, 13):
            rivet(a, y - 2, x)
    return a


def lab_white():
    a = plate("#e2e4e0", 3, (1.08, 0.82))
    return a


def glass():
    a = canvas("#5f97a8", noise=3)
    for i in range(16):
        for j in (i, i + 1):
            if j < 16:
                shade(a, j, 15 - i, 1.25)
    frame(a, 0.7, 0.6)
    return a


def paint_yellow():
    return plate("#d9a91c", 5, (1.15, 0.72))


# ---------- exploration ----------
def core_drill_gearbox():
    a = andesite()
    rect(a, 3, 3, 12, 6, "#1b1d1f")
    for x in (4, 5, 7, 8, 10, 11):
        for y in (4, 5):
            if rng.random() < 0.8:
                put(a, y, x, "#47d16a")
    label(a, 4, 9, 11, 11, "#d3a246", "#5a3e10")
    put(a, 13, 7, "#b2302a")
    put(a, 13, 8, "#b2302a")
    return a


def assay_cabinet():
    a = lab_white()
    col(a, 7, 0.7)
    col(a, 8, 1.1)
    for y in (6, 7, 8):
        put(a, y, 5, "#5a5f63")
        put(a, y, 10, "#5a5f63")
    rect(a, 1, 14, 14, 14, "#3b3f43")
    for x in range(3, 13, 2):
        put(a, 2, x, "#8a8e8c")
    return a


def assay_screen():
    a = canvas("#0e1a14", noise=2)
    frame(a, 1.0, 0.6)
    ink(a, 2, 2, 10, "#2f8f50")
    for x in range(2, 14):
        put(a, 12, x, "#3fbf6a")
    for x, h in ((4, 4), (7, 8), (8, 5), (11, 3)):
        for y in range(12 - h, 12):
            put(a, y, x, "#8af0a0")
    return a


def assay_cores():
    a = canvas("#8a6a3e", noise=5)
    for y in (0, 4, 8, 12):
        row(a, y, 0.65)
    for y0 in (1, 5, 9, 13):
        for x in range(1, 15):
            c = "#2a2a28" if rng.random() < 0.18 else "#8c8c88"
            for y in range(y0, y0 + 3):
                put(a, y, x, c)
                shade(a, y, x, 1.15 if y == y0 else 0.8 if y == y0 + 2 else 1.0)
            if rng.random() < 0.08:
                put(a, y0 + 1, x, "#bccb3c")
        col(a, 7 + (y0 % 3), 0.6)
    return a


# ---------- ore processing ----------
def ball_mill_shell():  # барабан вдоль X: болты футеровки — столбцами
    a = smooth("#5d6b62", 5)
    for x in (5, 10):
        seam_v(a, x)
    for x in (2, 13):
        for y in range(1, 16, 3):
            rivet(a, y, x)
    return a


def ball_mill_drive():
    a = paint_yellow()
    for y in range(3, 13):
        for x in range(3, 13):
            if (x + y) % 2 == 0:
                put(a, y, x, "#3a3220")
    return a


def sorter_front():
    a = plate("#8c9a8e")
    rect(a, 2, 2, 9, 7, "#0f2a17")
    for x, h in ((3, 2), (4, 4), (5, 3), (6, 1), (7, 3), (8, 2)):
        for y in range(7 - h, 7):
            put(a, y, x, "#47d16a")
    rect(a, 11, 2, 12, 3, "#d33b2c")
    rect(a, 11, 5, 12, 6, "#43b04a")
    for y in (10, 12):
        for x in range(2, 14):
            put(a, y, x, "#3a3f43")
    return a


def heap_liner_top():
    a = hdpe()
    for y in (7, 8):
        for x in range(16):
            put(a, y, x, "#c8692a")
            shade(a, y, x, 1.12 if y == 7 else 0.82)
    for x in range(1, 16, 3):
        put(a, 8, x, "#1a1a1a")
    return a


def liner_side(clay, lifts=(6, 10, 14), cap=("#202224", "#202224", "#9a9a92", "#9a9a92")):
    def f():
        a = canvas(clay, noise=6)
        for y, c in enumerate(cap):
            for x in range(16):
                put(a, y, x, c)
                shade(a, y, x, rng.uniform(0.9, 1.08))
        for y in lifts:
            row(a, y, 0.82)
        return a
    return f


def ore_heap():
    a = canvas("#6e6a62", noise=14)
    for c, n in (("#8a857a", 9), ("#55524c", 8), ("#3a3833", 4), ("#a8b04a", 3), ("#b5702e", 3)):
        for _ in range(n):
            grain(a, rng.randrange(16), rng.randrange(16), c, rng.randint(2, 4), None, "#2c2a26")
    return a


def drip_line():
    a = canvas("#1d1f21", noise=3)
    for y in range(3, 16, 4):
        for x in range(16):
            put(a, y, x, "#4a4e52")
        put(a, y, 7, "#c8d27a")
    return a


def grate():
    a = canvas("#9da3a6", noise=4)
    for y in range(16):
        for x in range(16):
            if y % 3 != 0 and x % 2 == 1:
                put(a, y, x, "#1c1e20")
    return a


def pls_surface():
    a = canvas("#9fb236", noise=6)
    for _ in range(9):
        y, x = rng.randrange(16), rng.randrange(13)
        for t in range(3):
            put(a, y, x + t, "#c5d65e")
        if y + 1 < 16:
            for t in range(3):
                shade(a, y + 1, x + t, 0.85)
    return a


def well_tag(arrow, up):
    def f():
        a = plate("#e6e2d2", 3, (1.05, 0.75))
        for y in (range(5, 14) if up else range(2, 11)):
            put(a, y, 7, arrow)
            put(a, y, 8, arrow)
        tip = (2, 3, 4) if up else (13, 12, 11)
        for i, y in enumerate(tip):
            w = i + 1
            for x in range(8 - w, 8 + w):
                put(a, y, x, arrow)
        return a
    return f


def monitor_casing():
    a = paint_yellow()
    for y in range(0, 3):
        for x in range(16):
            if (x + y) % 4 < 2:
                put(a, y, x, "#1d1d1f")
    rect(a, 6, 6, 9, 8, "#4b5157")
    rect(a, 7, 7, 8, 7, "#1d1d1f")
    label(a, 3, 11, 12, 13)
    return a


# ---------- hydrometallurgy ----------
def leach_shell():
    a = smooth("#6f7a73")
    for y in (5, 12):
        seam_h(a, y)
    band_h(a, 7, 9, "#c8692a")
    label(a, 3, 2, 7, 3)
    return a


def autoclave_shell():  # горизонтальный: бандажи столбцами
    a = smooth("#a7adae", 3)
    for x in (2, 7, 12):
        band_v(a, x, x, "#7a8084")
        col(a, x + 1, 1.12)
    label(a, 9, 6, 11, 8)
    return a


def thickener_drive():
    a = plate("#5a6670")
    rect(a, 2, 4, 6, 12, "#3e6b86")
    for y in range(4, 13, 2):
        for x in range(2, 7):
            shade(a, y, x, 0.75)
    circle(a, 10.5, 8, 3, "#ece8dc")
    circle(a, 10.5, 8, 3.5, "#1d1d1f", 3)
    for i in range(3):
        put(a, 8 - i, 10 + i, "#c0302a")
    label(a, 4, 13, 11, 13, "#d3a246", "#5a3e10")
    return a


def thickener_surface():
    a = canvas("#668f84", noise=4)
    for r in (3, 5.5):
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if abs(d - r) < 0.5:
                    shade(a, y, x, 1.15)
    for _ in range(6):
        put(a, rng.randrange(16), rng.randrange(16), "#dfe8e2")
    return a


def ix_shell():
    a = frp()
    seam_h(a, 1)
    seam_h(a, 15)
    band_h(a, 11, 12, "#c8692a")
    label(a, 9, 3, 13, 4)
    return a


def ix_resin():
    a = np.zeros((16, 16, 4))
    a[:, :] = hexc("#7a4a14")
    for y in range(16):
        for x in range(16):
            if y < 4:
                a[y, x] = hexc("#a8b83a")
            elif (x + (y // 2) % 2) % 2 == 0 and y % 2 == 0:
                a[y, x] = hexc("#d98a2a")
            elif (x + y) % 5 == 0:
                a[y, x] = hexc("#f2c060")
    frame(a, 0.6, 0.55)
    return a


def settler_window():
    a = np.zeros((16, 16, 4))
    for y in range(16):
        for x in range(16):
            c = "#c08a2c" if y < 6 else "#b7a77a" if y < 8 else "#8fae6a"
            a[y, x] = hexc(c)
            a[y, x, :3] *= rng.uniform(0.93, 1.06)
    for x in range(1, 15, 3):
        put(a, 6, x, "#e8dcb0")
        put(a, 7, x + 1, "#e8dcb0")
    for i in range(2, 14):
        shade(a, i, 15 - i, 1.2)
    frame(a, 0.55, 0.5)
    return a


def emulsion():
    a = canvas("#b39a62", noise=8)
    for _ in range(12):
        put(a, rng.randrange(16), rng.randrange(16), "#e2d4a2")
    return a


def precip_shell():
    a = smooth("#b0b5b8", 3)
    for x in range(0, 16, 3):
        col(a, x, 1.05)
    for y in (3, 12):
        seam_h(a, y)
    band_h(a, 6, 8, "#e8c21a")
    ink(a, 7, 4, 11, "#2a2a2a")
    return a


def calciner_shell():  # вдоль X, нагрев к восточному (горячему) концу
    a = smooth("#6a5a52", 6)
    for x in range(16):
        t = x / 15
        for y in range(16):
            a[y, x, :3] = a[y, x, :3] * (1 - 0.35 * t) + hexc("#8e4a30")[:3] * 0.35 * t
    for x in (4, 11):
        seam_v(a, x)
    return a


def calciner_hood():
    a = firebrick()
    frame(a, 0.7, 0.6)
    frame(a, 0.7, 0.6, inset=1)
    circle(a, 7.5, 7.5, 3.5, "#1a1a1a")
    circle(a, 7.5, 7.5, 3.5, "#c8561e", 2.6)
    return a


def dam_side():
    a = canvas("#8a7a62", noise=9)
    for y in (3, 7, 11, 15):
        row(a, y, 0.84)
    for _ in range(8):
        put(a, rng.randrange(16), rng.randrange(16), "#9a9a94")
    return a


def riprap():
    a = canvas("#3e3c38", noise=4)
    for _ in range(14):
        grain(a, rng.randrange(16), rng.randrange(16), rng.choice(("#7a7a76", "#9a9a94", "#6a6862")),
              rng.randint(4, 7), "#b8b8b2", "#4a4844")
    return a


def staff_gauge():
    a = canvas("#efefe8", noise=2)
    for y0 in range(0, 16, 4):
        rect(a, 2, y0, 7, y0, "#c0302a")
        rect(a, 2, y0, 3, y0 + 2, "#c0302a")
        rect(a, 2, y0 + 2, 6, y0 + 2, "#c0302a")
        put(a, y0 + 1, 11, "#1d1d1f")
        put(a, y0 + 1, 12, "#1d1d1f")
    frame(a, 1.0, 0.75)
    return a


def tail_liner_top():
    a = canvas("#232527", noise=3)
    for y in range(16):
        for x in range(16):
            if (x + 2 * y) % 6 == 0:
                shade(a, y, x, 1.35)
    for x in range(16):
        put(a, 5, x, "#4a4e52")
        put(a, 7, x, "#4a4e52")
    return a


def neutralizer_hopper():
    a = plate("#e4e2da", 4, (1.06, 0.8))
    band_h(a, 4, 5, "#7b4fa0")
    label(a, 4, 8, 11, 10, "#f4f2ea", "#333333")
    for _ in range(6):
        put(a, rng.randrange(11, 15), rng.randrange(1, 15), "#f8f8f2")
    return a


# ---------- reagents ----------
def roaster_front():
    a = plate("#5b4d48", 6)
    rect(a, 3, 4, 12, 12, "#3f3633")
    frame_box(a, 3, 4, 12, 12, 1.2, 0.7)
    rect(a, 6, 7, 9, 8, "#141416")
    for y in (5, 11):
        rivet(a, y, 4)
        rivet(a, y, 11)
    rect(a, 1, 5, 2, 6, "#2a2e33")
    rect(a, 1, 10, 2, 11, "#2a2e33")
    label(a, 5, 1, 10, 2, "#d3a246", "#5a3e10")
    for x in (13, 14):
        for y in range(7, 14):
            if rng.random() < 0.5:
                put(a, y, x, "#7a3e22")
    return a


def frame_box(a, x0, y0, x1, y1, kl, kd):
    for x in range(x0, x1 + 1):
        shade(a, y0, x, kl)
        shade(a, y1, x, kd)
    for y in range(y0, y1 + 1):
        shade(a, y, x0, kl)
        shade(a, y, x1, kd)


def acid_panel():
    a = plate("#c4bfaa", 4)
    for x in range(2, 14):
        put(a, 4, x, "#d9b52c")
        put(a, 11, x, "#c8692a")
    for x in (3, 7, 11):
        rect(a, x, 3, x + 2, 5, "#3b4046")
        put(a, 7, x + 1, "#43b04a")
        for y in range(5, 11):
            put(a, y, x + 1, "#c8692a" if x == 11 else "#d9b52c")
    put(a, 13, 3, "#d33b2c")
    put(a, 13, 5, "#e8b923")
    put(a, 13, 7, "#43b04a")
    return a


def sulfur_shell():  # вдоль X, горелка на западе
    a = smooth("#6e6560", 5)
    for x in range(6):
        for y in range(16):
            a[y, x, :3] = a[y, x, :3] * 0.8 + hexc("#8a3e26")[:3] * 0.2 * (6 - x) / 6 + a[y, x, :3] * 0.2 * x / 6
    for x in (3, 12):
        seam_v(a, x)
    for _ in range(8):
        put(a, rng.randrange(16), rng.randrange(4, 16), "#d9c23a")
    return a


def converter_shell():
    a = smooth("#7b7f82", 4)
    for y in (2, 6, 10, 14):
        seam_h(a, y)
        put(a, y - 1, 4, "#d3a246")
        put(a, y - 1, 11, "#d3a246")
    band_h(a, 8, 8, "#d9b52c")
    return a


def absorber_shell():
    a = smooth("#4f5a55", 5)
    band_h(a, 3, 4, "#c8692a")
    for y in (8, 13):
        seam_h(a, y)
    label(a, 9, 10, 13, 11)
    return a


def ammonia_shell():
    a = smooth("#575c60", 5)
    for y in (1, 5, 9, 13):
        row(a, y, 1.2)
        row(a, y + 1, 0.8)
    band_h(a, 11, 11, "#d9b52c")
    return a


def ely_stack():
    a = canvas("#9fb0a2", noise=3)
    for x in range(16):
        if x % 3 == 2:
            col(a, x, 0.35)
        elif x % 3 == 0:
            col(a, x, 1.12)
    rect(a, 0, 0, 15, 0, None, 0.7)
    rect(a, 0, 15, 15, 15, None, 0.7)
    return a


def cold_box():
    a = canvas("#c6ccd0", noise=4)
    band_h(a, 2, 3, "#3f73b5")
    for x in (5, 10):
        seam_v(a, x)
    for _ in range(12):
        y, x = rng.randrange(8, 16), rng.randrange(16)
        put(a, y, x, "#f2f7fa")
    label(a, 11, 6, 14, 7)
    return a


def ostwald_shell():
    a = smooth("#a9aeb1", 3)
    for x in range(0, 16, 4):
        col(a, x, 1.06)
    band_h(a, 1, 2, "#d9b52c")
    for y, c in ((6, "#c8a860"), (7, "#a0703a"), (8, "#7a6aa0"), (9, "#6a7ab0")):
        for x in range(16):
            a[y, x, :3] = a[y, x, :3] * 0.5 + hexc(c)[:3] * 0.5
    seam_h(a, 12)
    return a


def peroxide_shell():
    a = smooth("#c8ccce", 3)
    seam_h(a, 13)
    rect(a, 6, 2, 9, 5, "#e8c21a")
    frame_box(a, 6, 2, 9, 5, 0.8, 0.6)
    put(a, 3, 7, "#1d1d1f")
    put(a, 4, 7, "#1d1d1f")
    put(a, 4, 8, "#1d1d1f")
    band_h(a, 9, 9, "#3f73b5")
    return a


def solvay_shell():
    a = smooth("#4a4d50", 6)
    for y in (0, 4, 8, 12):
        row(a, y, 1.3)
        for x in range(1, 16, 3):
            put(a, y, x, "#7f858a")
    for y in (10, 11):
        for x in (2, 3, 12, 13):
            put(a, y, x, "#7b4fa0")
    return a


def coke_door():
    a = plate("#3d3a38", 6)
    for x in (4, 11):
        col(a, x, 1.3)
        col(a, x + 1, 0.75)
    for y in (3, 12):
        rect(a, 2, y, 13, y, "#6a6460")
        rect(a, 2, y + 1, 13, y + 1, None, 0.6)
    for _ in range(5):
        x = rng.randrange(1, 15)
        for y in range(rng.randrange(1, 6), rng.randrange(8, 15)):
            put(a, y, x, "#2a1a10")
    return a


def frac_shell():
    a = clad()
    band_h(a, 6, 7, "#7a4a2a")
    rect(a, 10, 10, 13, 12, "#8f9599")
    frame_box(a, 10, 10, 13, 12, 1.15, 0.7)
    return a


def brine_plate():
    a = plate("#4f6f7f", 5)
    for _ in range(14):
        put(a, rng.randrange(10, 15), rng.randrange(1, 15), "#e8ece8")
    band_h(a, 3, 3, "#3f7a4a")
    label(a, 4, 6, 11, 8)
    return a


def mg_cell_side():
    a = canvas("#55504c", noise=5)
    for x in (1, 7, 14):
        col(a, x, 1.3)
        if x + 1 < 16:
            col(a, x + 1, 0.75)
    for y in (13, 14, 15):
        for x in range(16):
            a[y, x, :3] = a[y, x, :3] * 0.6 + hexc("#8a3a22")[:3] * 0.4
    label(a, 3, 3, 5, 5, "#d3a246", "#5a3e10")
    return a


def chloralkali_front():
    a = canvas("#e9e9e2", noise=3)
    for x in range(16):
        if x % 3 == 2:
            col(a, x, 0.3)
        elif x % 3 == 1:
            for y in range(2, 14):
                put(a, y, x, "#3f73b5")
    rect(a, 0, 0, 15, 1, "#8a9094")
    rect(a, 0, 14, 15, 15, "#3b3f43")
    return a


def heater_front():
    a = plate("#8c9a8e")
    rect(a, 3, 4, 12, 9, "#1d1d1f")
    for x, y in ((2, 2), (1, 3), (3, 3)):
        put(a, y, x, "#e8b923")
    put(a, 1, 2, "#e8b923")
    put(a, 2, 2, "#1d1d1f")
    rect(a, 9, 1, 13, 2, "#2a0f0f")
    ink(a, 1, 10, 12, "#ff4a36", 0.8)
    for y in (11, 13):
        for x in range(3, 13):
            put(a, y, x, "#3a3f43")
    return a


def steam_port_front():
    a = plate("#8c9a8e")
    circle(a, 7.5, 7.5, 7, "#9c2f27", 5.6)
    for i in range(8):
        import math
        ang = i * math.pi / 4
        put(a, int(round(7.5 + 6.3 * math.sin(ang))), int(round(7.5 + 6.3 * math.cos(ang))), "#e0e0d8")
    return a


# ---------- conversion ----------
def dissolver_shell():
    a = smooth("#b2b7ba", 3)
    for y in range(4, 11):
        row(a, y, 0.88)
    seam_h(a, 4)
    seam_h(a, 11)
    band_h(a, 13, 14, "#c8692a")
    rect(a, 3, 1, 4, 2, "#e8c21a")
    return a


def pulsed_shell():
    a = smooth("#b4b8bb", 3)
    sight(a, 5, 4, 10, 11, "#8fae6a")
    for y, x in ((6, 7), (8, 8), (9, 6), (7, 9)):
        put(a, y, x, "#c08a2c")
    seam_h(a, 15)
    return a


def evap_shell():
    a = smooth("#aab0b3", 3)
    band_h(a, 2, 3, "#9c2f27")
    seam_h(a, 8)
    seam_h(a, 14)
    label(a, 10, 10, 13, 11)
    return a


def denit_shell():
    a = smooth("#5b5f63", 4)
    for y0 in (2, 7, 12):
        band_h(a, y0, y0 + 2, "#b5622e")
        for x in range(1, 16, 4):
            put(a, y0 + 1, x, "#3a2a20")
    return a


def reduction_front():
    a = plate("#5b5f63", 5)
    rect(a, 3, 3, 12, 11, "#44484c")
    frame_box(a, 3, 3, 12, 11, 1.25, 0.7)
    rect(a, 6, 6, 9, 7, "#141416")
    for x in range(1, 15):
        put(a, 13, x, "#d9b52c" if (x // 2) % 2 else "#b2302a")
        put(a, 14, x, "#d9b52c" if (x // 2) % 2 else "#b2302a")
    return a


def hf_kiln_shell():  # вдоль X
    a = smooth("#6b6f63", 5)
    for x in (4, 11):
        seam_v(a, x)
    band_v(a, 7, 8, "#d9b52c")
    return a


def hf_jacket():
    a = firebrick()
    frame(a, 0.6, 0.55)
    rect(a, 1, 1, 4, 4, "#d9b52c")
    put(a, 2, 2, "#1d1d1f")
    put(a, 3, 3, "#1d1d1f")
    return a


def hf_condenser_shell():  # вдоль X
    a = smooth("#9aa3a6", 3)
    for x in (3, 12):
        seam_v(a, x)
    band_v(a, 6, 7, "#d9b52c")
    band_v(a, 9, 10, "#3f7a4a")
    for _ in range(8):
        put(a, rng.randrange(16), rng.randrange(0, 3), "#eef4f6")
    return a


def monel(base="#a39c86"):
    return smooth(base, 4)


def hydrofluor_shell():
    a = monel()
    for y0 in (3, 9):
        band_h(a, y0, y0 + 2, "#b5622e")
    seam_h(a, 14)
    rect(a, 11, 13, 12, 14, "#6a9a4a")
    return a


def fluorine_side():
    a = canvas("#596066", noise=5)
    for x in (3, 8, 13):
        col(a, x, 1.3)
        col(a, x + 1, 0.75)
    band_h(a, 1, 2, "#d9b52c")
    for x in range(0, 16, 3):
        put(a, 1, x, "#1d1d1f")
    band_h(a, 11, 12, "#3f7a4a")
    return a


def fluorination_shell():
    a = monel("#9e9680")
    for y in range(3, 14):
        row(a, y, 1.15 if y % 2 else 0.8)
    band_h(a, 0, 1, "#d9b52c")
    return a


def cold_trap_shell():
    a = frost()
    for x in (3, 8, 13):
        for y in range(16):
            if rng.random() < 0.8:
                put(a, y, x, "#9fb3c0")
    for _ in range(6):
        grain(a, rng.randrange(16), rng.randrange(16), "#f4f8fa", 3)
    return a


def uf6_shell():  # обшивка автоклава с маленькой табличкой «радиационная опасность»
    a = clad()
    rect(a, 6, 6, 9, 9, "#e8b923")
    put(a, 7, 7, "#1d1d1f")
    put(a, 8, 8, "#1d1d1f")
    put(a, 6, 7, "#1d1d1f")
    put(a, 7, 9, "#1d1d1f")
    return a


def bomb_shell():
    a = smooth("#3e4246", 5)
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 < 2 and y < 2:
                put(a, y, x, "#e8b923")
            elif y < 2:
                put(a, y, x, "#1d1d1f")
    for y, c in ((10, "#8a7a4a"), (11, "#7a5a3a"), (12, "#5a5a8a"), (13, "#4a5a8a")):
        for x in range(16):
            a[y, x, :3] = a[y, x, :3] * 0.55 + hexc(c)[:3] * 0.45
    for x in range(1, 16, 4):
        rivet(a, 4, x)
    return a


def glovebox_front():
    a = canvas("#4f7f8f", noise=3)
    for i in range(16):
        for j in (i, i + 1, i + 6):
            if j < 16:
                shade(a, 15 - j, i, 1.2)
    frame(a, 1.4, 0.6)
    band_h(a, 0, 1, "#d9b52c")
    for cx in (4.5, 11.5):
        circle(a, cx, 9.5, 3, "#1d1d1f", 1.8)
        circle(a, cx, 9.5, 1.8, "#2e3134")
    return a


TEXTURES.update({f"block/{k}.png": v for k, v in ORE_TEX.items()})
TEXTURES.update({
    # common (prefix mining_)
    "block/common/mining_andesite.png": andesite,
    "block/common/mining_copper.png": copper,
    "block/common/mining_firebrick.png": firebrick,
    "block/common/mining_hdpe.png": hdpe,
    "block/common/mining_frp.png": frp,
    "block/common/mining_lab_white.png": lab_white,
    "block/common/mining_glass.png": glass,
    "block/common/mining_glow_fire.png": glow_fire,
    "block/common/mining_glow_coil.png": glow_coil,
    "block/common/mining_pulp.png": pulp,
    "block/common/mining_frost.png": frost,
    "block/common/mining_clad.png": clad,
    "block/common/mining_paint_yellow.png": paint_yellow,
    "block/common/mining_pipe_acid.png": pipe_paint("#c8692a"),
    "block/common/mining_pipe_gas.png": pipe_paint("#d9b52c"),
    "block/common/mining_pipe_h2.png": pipe_paint("#d9b52c", "#b2302a"),
    "block/common/mining_pipe_alkali.png": pipe_paint("#7b4fa0"),
    "block/common/mining_pipe_air.png": pipe_paint("#3f73b5"),
    "block/common/mining_pipe_fuel.png": pipe_paint("#7a4a2a"),
    "block/common/mining_pipe_water.png": pipe_paint("#3f7a4a"),
    "block/common/mining_pipe_steam.png": pipe_paint("#9c2f27"),
    # exploration
    "block/core_drill_gearbox.png": core_drill_gearbox,
    "block/assay_station_cabinet.png": assay_cabinet,
    "block/assay_station_screen.png": assay_screen,
    "block/assay_station_cores.png": assay_cores,
    # ore processing
    "block/ball_mill_shell.png": ball_mill_shell,
    "block/ball_mill_drive.png": ball_mill_drive,
    "block/radiometric_sorter_front.png": sorter_front,
    "block/heap_leach_liner_top.png": heap_liner_top,
    "block/heap_leach_liner_side.png": liner_side("#8f6f52"),
    "block/ore_heap.png": ore_heap,
    "block/drip_irrigator_line.png": drip_line,
    "block/pls_collector_grate.png": grate,
    "block/pls_pond_surface.png": pls_surface,
    "block/isl_injection_well_tag.png": well_tag("#c8692a", False),
    "block/isl_production_well_tag.png": well_tag("#3f9a4a", True),
    "block/isl_monitor_well_casing.png": monitor_casing,
    # hydrometallurgy
    "block/leach_tank_shell.png": leach_shell,
    "block/pressure_leach_autoclave_shell.png": autoclave_shell,
    "block/thickener_drive.png": thickener_drive,
    "block/thickener_surface.png": thickener_surface,
    "block/ion_exchange_column_shell.png": ix_shell,
    "block/ion_exchange_column_resin.png": ix_resin,
    "block/mixer_settler_window.png": settler_window,
    "block/mixer_settler_emulsion.png": emulsion,
    "block/precipitation_tank_shell.png": precip_shell,
    "block/rotary_calciner_shell.png": calciner_shell,
    "block/rotary_calciner_hood.png": calciner_hood,
    "block/tailings_dam_side.png": dam_side,
    "block/tailings_dam_top.png": riprap,
    "block/tailings_dam_gauge.png": staff_gauge,
    "block/tailings_liner_top.png": tail_liner_top,
    "block/tailings_liner_side.png": liner_side("#7d8088", (5, 9, 13), ("#232527", "#232527")),
    "block/tailings_neutralizer_hopper.png": neutralizer_hopper,
    # reagents
    "block/pyrite_roaster_front.png": roaster_front,
    "block/acid_plant_panel.png": acid_panel,
    "block/sulfur_burner_shell.png": sulfur_shell,
    "block/contact_converter_shell.png": converter_shell,
    "block/absorption_tower_shell.png": absorber_shell,
    "block/ammonia_synthesis_reactor_shell.png": ammonia_shell,
    "block/electrolyzer_stack.png": ely_stack,
    "block/air_separator_coldbox.png": cold_box,
    "block/ostwald_converter_shell.png": ostwald_shell,
    "block/peroxide_reactor_shell.png": peroxide_shell,
    "block/solvay_tower_shell.png": solvay_shell,
    "block/coke_oven_door.png": coke_door,
    "block/fractionating_column_shell.png": frac_shell,
    "block/brine_extractor_casing.png": brine_plate,
    "block/mg_electrolysis_cell_side.png": mg_cell_side,
    "block/chlor_alkali_cell_front.png": chloralkali_front,
    "block/process_heater_front.png": heater_front,
    "block/process_steam_port_front.png": steam_port_front,
    # conversion
    "block/dissolver_shell.png": dissolver_shell,
    "block/pulsed_column_shell.png": pulsed_shell,
    "block/evaporator_shell.png": evap_shell,
    "block/denitration_reactor_shell.png": denit_shell,
    "block/reduction_furnace_front.png": reduction_front,
    "block/hf_kiln_shell.png": hf_kiln_shell,
    "block/hf_kiln_jacket.png": hf_jacket,
    "block/hf_condenser_shell.png": hf_condenser_shell,
    "block/hydrofluorination_reactor_shell.png": hydrofluor_shell,
    "block/fluorine_cell_side.png": fluorine_side,
    "block/fluorination_reactor_shell.png": fluorination_shell,
    "block/cold_trap_shell.png": cold_trap_shell,
    "block/uf6_autoclave_shell.png": uf6_shell,
    "block/bomb_reduction_vessel_shell.png": bomb_shell,
    "block/inert_glovebox_front.png": glovebox_front,
})
