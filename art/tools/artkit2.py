"""Помощники второй волны (03c/03d M6+, 07–14 после M3): общие окраски, лицевые
панели, сосуды, шкафы, иконки предметов, иллюстрации справочника.

Общие текстуры волны регистрируются здесь (`block/common/w2_*`) при первом
импорте — его делает первый по порядку модуль группы 2 в build.py.
"""
import math
from artkit import *
from artkit import mk as _mk
from tex_plant import trefoil_px, dial_round32, armor, faces, fill, fill_box, HEAD, HAT, BODY, ARM, LEG
from tex_metitems import lump, powder, sack, ingot, coil
from tex_content import sheet, PAPER, INK

W = lambda n: f"block/common/w2_{n}.png"
X_ = lambda n: f"block/common/metal_{n}.png"
G_ = lambda n: f"block/common/grid_{n}.png"
A_ = lambda n: f"block/common/auto_{n}.png"

RED, GREEN, BLUE, YEL = (192, 57, 43, 255), (46, 125, 50, 255), (44, 94, 168, 255), (232, 185, 35, 255)
GREY = (125, 130, 136, 255)


# ======================================================================
# текстуры-заготовки
# ======================================================================
def paint(base, noise=3, fr=True, k=(1.1, 0.8)):
    def fn():
        a = canvas(base, noise=noise)
        if fr:
            frame(a, *k)
        return a
    return fn


def welded(base, seams=(5, 11), noise=3):  # обечайка сосуда: горизонтальные сварные швы
    def fn():
        a = canvas(base, noise=noise)
        for y in seams:
            kbox(a, 0, y, 15, y, 0.82)
            kbox(a, 0, y + 1, 15, y + 1, 1.08)
        return a
    return fn


def clad():  # алюминиевая обшивка изоляции: внахлёст полосы и бандажи
    a = canvas("#c3c7c9", noise=2)
    for y in (0, 8):
        kbox(a, 0, y, 15, y, 0.75)
    for x in (4, 12):
        kbox(a, x, 0, x, 15, 1.12)
    return a


def checker():  # рифлёный настил
    a = canvas("#7d8288", noise=3)
    for y in range(1, 16, 4):
        for x in range(1, 16, 4):
            kbox(a, x, y, x + 1, y, 1.3)
            kbox(a, x + 2, y + 2, x + 2, y + 3, 1.3)
    frame(a, 1.05, 0.85)
    return a


def lattice(col):  # решётчатая ферма из уголка (прозрачные окна)
    def fn():
        a = blank()
        for i in range(16):
            for (x, y) in ((i, 0), (i, 15), (0, i), (15, i), (i, i), (i, 15 - i)):
                a[y, x] = C(col)
                a[y, x, :3] *= rng.uniform(0.9, 1.05)
        for i in range(16):
            for (x, y) in ((i, 1), (1, i)):
                a[y, x, :3] = a[y, x, :3] * 0.8 if a[y, x, 3] else a[y, x, :3]
        return a
    return fn


def screen(col="#46d18a", bg="#0f1a14"):  # светящийся экран: строки и график
    def fn():
        a = canvas(bg, noise=1)
        for y in (3, 5, 7):
            box(a, 2, y, 9, y, col)
        for x in range(2, 14):
            px(a, x, 12 - int(2 * math.sin(x / 2)), col)
        frame(a, 1.3, 1.3)
        return a
    return fn


def vents():  # решётка вентиляции
    a = canvas("#bfc2bc", noise=2)
    grille(a, 2, 2, 13, 13, step=2, k=0.55)
    frame(a, 1.1, 0.8)
    return a


def pcb_tex():
    a = canvas("#2f6b3a", noise=3)
    for y in (3, 7, 11):
        line(a, 1, y, 14, y, "#c9a14a")
    for (x, y) in ((4, 4), (10, 8), (6, 12)):
        box(a, x, y - 1, x + 2, y, "#1d1d1f")
    return a


def sign_tri(fg="#1d1d1f", bg="#e8b923", glyph="bolt"):  # треугольный знак на светлом поле
    def fn():
        a = canvas("#e6e2d2", noise=1)
        for y in range(3, 14):
            w = (y - 3) * 0.6
            box(a, int(7.5 - w), y, int(8 + w), y, bg)
        for x in range(1, 15):
            px(a, x, 14, fg)
        line(a, 1, 14, 7, 2, fg)
        line(a, 14, 14, 8, 2, fg)
        if glyph == "bolt":
            line(a, 8, 6, 7, 9, fg)
            line(a, 7, 9, 9, 9, fg)
            line(a, 9, 9, 7, 12, fg)
        elif glyph == "rad":
            trefoil_px(a, 7.5, 10, 2.5, bg=None)
        elif glyph == "flame":
            circ(a, 7.5, 10.5, 2, fg)
            px(a, 8, 7, fg)
        else:
            box(a, 7, 6, 8, 10, fg)
            box(a, 7, 12, 8, 12, fg)
        return a
    return fn


def ring_band(base, ring):  # окраска трубы с предупреждающим кольцом посередине
    def fn():
        a = canvas(base, noise=3)
        box(a, 0, 6, 15, 9, ring)
        kbox(a, 0, 6, 15, 6, 1.1)
        return a
    return fn


def liquid(base, k=0.12):  # неподвижная поверхность жидкости
    def fn():
        a = canvas(base, noise=4)
        for y in range(0, 16, 4):
            for x in range(16):
                shade(a, y, x, 1 + k * math.sin(x / 2.0 + y))
        return a
    return fn


def front32(bg, draw):
    """Лицевая подложка 32×32 (приборы, табло, мнемосхемы): draw(a) рисует поверх."""
    def fn():
        a = blank(32)
        a[:, :] = C(bg)
        for y in range(32):
            for x in range(32):
                a[y, x, :3] *= rng.uniform(0.97, 1.03)
        draw(a)
        return a
    return fn


def dial32(face="#f1eee4", bezel="#1f2224", ring=None):
    return lambda: dial_round32(face=face, bezel=bezel, bg=bezel, ring=ring)


def front16(bg, draw):
    def fn():
        a = canvas(bg, noise=2)
        draw(a)
        frame(a, 1.1, 0.8)
        return a
    return fn


TEXTURES.update({
    W("white"): paint("#e4e5e0", 2),
    W("white_smooth"): paint("#e4e5e0", 1, fr=False),
    W("silver"): welded("#c4c8cc"),
    W("silver_smooth"): paint("#c4c8cc", 1, fr=False),
    W("sblue"): welded("#8aa0ad"),
    W("sblue_smooth"): paint("#8aa0ad", 1, fr=False),
    W("orange"): paint("#e07a1f"),
    W("green6011"): paint("#5d7a5a"),
    W("beige"): paint("#d8ccb0", 2),
    W("khaki"): paint("#6f6a3e"),
    W("black"): paint("#26282a", 2, k=(1.3, 0.75)),
    W("yellow"): paint("#e8b923"),
    W("blue"): paint("#2c5ea8"),
    W("red"): paint("#b8322a"),
    W("clad"): clad,
    W("checker"): checker,
    W("lattice_yellow"): lattice("#e0b51e"),
    W("lattice_galv"): lattice("#9aa3a6"),
    W("glow_screen"): screen(),
    W("glow_screen_amber"): screen("#ffb04a", "#1a120a"),
    W("vents"): vents,
    W("pcb"): pcb_tex,
    W("sign_hv"): sign_tri(glyph="bolt"),
    W("sign_rad"): sign_tri(glyph="rad"),
    W("sign_fire"): sign_tri(glyph="flame"),
    W("sign_warn"): sign_tri(glyph="!"),
    W("gas_ring"): ring_band("#d9b21f", "#c0392b"),
    W("oil_ring"): ring_band("#6b4a2a", "#c0392b"),
    W("tox_ring"): ring_band("#7d8288", "#e8b923"),
    W("water_dark"): liquid("#3d6b5a"),
    W("sulfur"): liquid("#e8c228"),
    W("glow_sulfur"): liquid("#f0a830", 0.18),
    W("glow_flame"): liquid("#ff9a2a", 0.25),
    W("glow_cherenkov"): liquid("#5ab8ff", 0.2),
    W("oily"): paint("#5e6266", 4),
    W("soot"): paint("#2e2b28", 6, fr=False),
    W("wood"): paint("#8a6a42", 6),
})

POOL2 = {
    "w": W("white"), "ws": W("white_smooth"), "sil": W("silver"), "sils": W("silver_smooth"),
    "sb": W("sblue"), "sbs": W("sblue_smooth"), "or": W("orange"), "g6": W("green6011"), "bei": W("beige"),
    "kh": W("khaki"), "blk": W("black"), "ye": W("yellow"), "blu": W("blue"), "rd": W("red"),
    "clad": W("clad"), "chk": W("checker"), "laty": W("lattice_yellow"), "latg": W("lattice_galv"),
    "scr": W("glow_screen"), "scra": W("glow_screen_amber"), "vent": W("vents"), "pcb": W("pcb"),
    "shv": W("sign_hv"), "srad": W("sign_rad"), "sfire": W("sign_fire"), "swarn": W("sign_warn"),
    "gasr": W("gas_ring"), "oilr": W("oil_ring"), "toxr": W("tox_ring"), "wd": W("water_dark"),
    "sul": W("sulfur"), "gsul": W("glow_sulfur"), "gfl": W("glow_flame"), "gch": W("glow_cherenkov"),
    "oily": W("oily"), "soot": W("soot"), "wood": W("wood"),
    "ral": A_("ral7035"), "rals": A_("ral7035_smooth"), "pblk": A_("panel_black"), "exy": A_("ex_yellow"),
    "fro": X_("frost"), "rust": X_("rust"), "ci": G_("cast_iron"), "por": G_("porcelain"), "alu": G_("aluminium"),
    "r32": G_("ral7032"), "r35": G_("ral7035"), "cub": G_("copper_bar"), "brk": G_("brick"), "rad": G_("radiator"), "en": G_("enamel_green"), "dg": G_("dark_grey"),
    "lat": G_("lattice"), "cor": G_("corrugated"), "tref": T("trefoil"), "frp": M_("frp"),
    "lab": M_("lab_white"), "hdpe": M_("hdpe"), "pulp": M_("pulp"), "water": F_("water"), "hot": F_("glow_hot"),
}


def mk2(id_, cat, els, own=None, kind="block"):
    _mk(id_, cat, els, dict(POOL2, **(own or {})), kind)


# ======================================================================
# геометрия
# ======================================================================
def _fit(a0, a1):
    return max(a0, -15.92), min(a1, 31.92)


def vcyl(name, cx, cz, r, y0, y1, tex, sides=16, top=None):
    y0, y1 = _fit(y0, y1)
    return disc(name, "y", r, y0, y1, tex, sides, faces={"up": top, "down": top} if top else None, c=(cx, 8, cz))


def hcyl(name, axis, r, a0, a1, tex, c=(8, 8, 8), sides=16, ends=None):
    a0, a1 = _fit(a0, a1)
    f = None
    if ends:
        f = {{"x": "east", "z": "south"}[axis]: ends, {"x": "west", "z": "north"}[axis]: ends}
    return disc(name, axis, r, a0, a1, tex, sides, faces=f, c=c)


def lamp(name, x, y, z, on, col, s=0.75):
    """Лампа на лицевой (north) грани с плоскостью z."""
    return el(("glow_" if on else "") + name, [x - s, y - s, z - 0.6], [x + s, y + s, z], col if on else "loff")


def box_(name, f, t, tex, front_=None, side="north", faces=None):
    """Коробка; front_ — текстура на лицевую грань целиком."""
    fc = dict(faces or {})
    if front_:
        fc[side] = front_
    return el(name, f, t, tex, faces=fc, full=[side] if front_ else None)


def cabinet(tex, front_=None, x0=1, x1=15, y0=0, y1=16, z0=2, z1=15, plinth=True):
    """Шкаф у задней стены; лицо — north."""
    els = [box_("body", [x0, y0 + (1 if plinth else 0), z0], [x1, y1, z1], tex, front_)]
    if plinth:
        els.append(el("plinth", [x0 + 0.5, y0, z0 + 0.5], [x1 - 0.5, y0 + 1, z1 - 0.5], "d"))
    return els


def hvessel(name, r, x0, x1, tex, cy=8, cz=8, saddles=True, sy=0, heads="sbs"):
    """Горизонтальный сосуд вдоль X на сёдлах."""
    els = hcyl(name, "x", r, x0, x1, tex, c=(8, cy, cz), ends=heads)
    if saddles:
        for i, x in enumerate((x0 + 2, x1 - 4)):
            els.append(el(f"{name}_saddle_{i}", [x, sy, cz - r + 1], [x + 2, cy - r + 1, cz + r - 1], "d"))
    return els


def vvessel(name, r, y0, y1, tex, cx=8, cz=8, head="sbs", skirt=True, sides=16):
    els = vcyl(name, cx, cz, r, y0 + (2 if skirt else 0), y1, tex, sides, top=head)
    if skirt:
        els += vcyl(f"{name}_skirt", cx, cz, r - 0.5, y0, y0 + 2, "d", 8)
    return els


def nozzle(name, axis, at, c, r=1.0, tex="st", flange=True):
    """Штуцер: трубка длиной 2 от наружной координаты at внутрь, фланец — на наружном конце."""
    x, y, z = c
    lo, hi = sorted((at, at + (2 if at < 8 else -2)))
    lo, hi = _fit(lo, hi)
    els = disc(name, axis, r, lo, hi, tex, 8, c=(x, y, z))
    if flange:
        f0, f1 = _fit(*sorted((at, at + (0.5 if at < 8 else -0.5))))
        els += disc(name + "_fl", axis, r + 0.6, f0, f1, "d", 8, c=(x, y, z))
    return els


def skid(x0, x1, z0, z1, tex="d", h=1):
    return [el("skid_w", [x0, 0, z0], [x1, h, z0 + 1.5], tex), el("skid_e", [x0, 0, z1 - 1.5], [x1, h, z1], tex),
            el("skid_deck", [x0 + 0.5, h - 0.5, z0 + 1.5], [x1 - 0.5, h, z1 - 1.5], "chk")]


def legs(x0, x1, z0, z1, y1, w=1.5, tex="d"):
    return [el(f"leg_{i}", [x, 0, z], [x + w, y1, z + w], tex)
            for i, (x, z) in enumerate(((x0, z0), (x1 - w, z0), (x0, z1 - w), (x1 - w, z1 - w)))]


def ladder(name, x, z, y0, y1, tex="ye", side="north"):
    """Вертикальная лестница: две тетивы и ступени по оси X."""
    zz = (z, z + 0.5) if side == "north" else (z - 0.5, z)
    els = [el(f"{name}_l", [x, y0, zz[0]], [x + 0.5, y1, zz[1]], tex),
           el(f"{name}_r", [x + 3, y0, zz[0]], [x + 3.5, y1, zz[1]], tex)]
    for i, y in enumerate(range(int(y0) + 2, int(y1), 3)):
        els.append(el(f"{name}_rung_{i}", [x + 0.5, y, zz[0]], [x + 3, y + 0.5, zz[1]], tex))
    return els


def railing(x0, x1, z0, z1, y, tex="ye"):
    """Ограждение площадки на высоте y по периметру."""
    h = y + 4
    return [el("rail_n", [x0, h - 0.5, z0], [x1, h, z0 + 0.5], tex), el("rail_s", [x0, h - 0.5, z1 - 0.5], [x1, h, z1], tex),
            el("rail_w", [x0, h - 0.5, z0], [x0 + 0.5, h, z1], tex), el("rail_e", [x1 - 0.5, h - 0.5, z0], [x1, h, z1], tex),
            el("post_nw", [x0, y, z0], [x0 + 0.5, h, z0 + 0.5], tex), el("post_ne", [x1 - 0.5, y, z0], [x1, h, z0 + 0.5], tex),
            el("post_sw", [x0, y, z1 - 0.5], [x0 + 0.5, h, z1], tex), el("post_se", [x1 - 0.5, y, z1 - 0.5], [x1, h, z1], tex)]


def fan_rotor(axis="y", r=6, at=8, tex="d", c=(8, 8, 8)):
    """Крыльчатка: ступица и 4 лопасти в плоскости, перпендикулярной оси."""
    x, y, z = c
    els = disc("hub", axis, 1.2, at - 0.75, at + 0.75, "st", 8, c=c)
    t = 0.4
    if axis == "y":
        els += [el("blade_x", [x - r, at - t, z - 1], [x + r, at + t, z + 1], tex),
                el("blade_z", [x - 1, at - t, z - r], [x + 1, at + t, z + r], tex, rot=("y", 45, list(c)))]
    elif axis == "z":
        els += [el("blade_x", [x - r, y - 1, at - t], [x + r, y + 1, at + t], tex),
                el("blade_y", [x - 1, y - r, at - t], [x + 1, y + r, at + t], tex, rot=("z", 45, list(c)))]
    else:
        els += [el("blade_y", [at - t, y - r, z - 1], [at + t, y + r, z + 1], tex),
                el("blade_z", [at - t, y - 1, z - r], [at + t, y + 1, z + r], tex, rot=("x", 45, list(c)))]
    return els


def shaft(axis, r=1, a0=-0.01, a1=16.01, c=(8, 8, 8)):
    a0, a1 = _fit(a0, a1)
    return disc("shaft", axis, r, a0, a1, "st", 8, c=c)


def drum(axis, r, a0, a1, tex, c=(8, 8, 8), flange="d"):
    """Барабан с ребордами (лебёдка, канат)."""
    els = hcyl("drum", axis, r, a0 + 0.75, a1 - 0.75, tex, c=c, sides=8)
    els += hcyl("flange_a", axis, r + 1.2, a0, a0 + 0.75, flange, c=c, sides=8)
    els += hcyl("flange_b", axis, r + 1.2, a1 - 0.75, a1, flange, c=c, sides=8)
    return els


def plate_sign(name, tex, x0=3, x1=13, y0=3, y1=13, z=15.5):
    """Табличка на стене (south — стена)."""
    return [el(name, [x0, y0, z], [x1, y1, 16], tex, full=["north"])]


# ======================================================================
# иконки
# ======================================================================
def ic_drum(body, fill_, band="#5a5e62"):  # бочка с содержимым сверху
    def draw(a):
        box(a, 3, 5, 12, 14, body)
        box(a, 3, 8, 12, 8, band)
        box(a, 3, 11, 12, 11, band)
        box(a, 4, 3, 11, 5, fill_)
        for (x, y) in ((5, 3), (8, 4), (10, 3)):
            px(a, x, y, "#ffffff")
    return draw


def ic_jar(body, lid, label=None):
    def draw(a):
        box(a, 4, 5, 11, 14, body)
        box(a, 5, 3, 10, 4, lid)
        if label:
            box(a, 4, 8, 11, 11, label)
    return draw


def ic_pellets(cols, shape="round"):
    def draw(a):
        pts = ((4, 5), (8, 4), (11, 6), (5, 9), (9, 8), (12, 10), (3, 12), (7, 12), (10, 13))
        for i, (x, y) in enumerate(pts):
            c = cols[i % len(cols)]
            if shape == "round":
                circ(a, x + 0.5, y + 0.5, 1.6, c)
            elif shape == "extrudate":
                box(a, x - 1, y, x + 1, y, c)
            elif shape == "ring":
                circ(a, x + 0.5, y + 0.5, 1.8, c, 0.8)
            else:
                box(a, x - 1, y - 1, x + 1, y, c)
    return draw


def ic_flakes(col, col2):
    def draw(a):
        for i, (x, y) in enumerate(((3, 4), (8, 3), (11, 7), (5, 8), (9, 10), (4, 12), (12, 12))):
            box(a, x, y, x + 2, y + 1, col if i % 2 else col2)
    return draw


def ic_tube(col, end, rings=(), lo=(2, 13), hi=(13, 2), w=1):
    """Длинный предмет по диагонали (труба, зонд, штанга)."""
    def draw(a):
        x0, y0 = lo
        x1, y1 = hi
        for d in range(-w, w + 1):
            line(a, x0 + d, y0, x1 + d, y1, col)
        for t in rings:
            x = round(x0 + (x1 - x0) * t)
            y = round(y0 + (y1 - y0) * t)
            box(a, x - 1, y - 1, x + 1, y + 1, end)
        box(a, x0 - 1, y0 - 1, x0 + 1, y0 + 1, end)
        box(a, x1 - 1, y1 - 1, x1 + 1, y1 + 1, end)
    return draw


def ic_doc(stripe, stamp=None, lines=4):
    def draw(a):
        box(a, 3, 2, 12, 14, "#ece8da")
        box(a, 3, 2, 12, 3, stripe)
        for i in range(lines):
            box(a, 5, 5 + i * 2, 10, 5 + i * 2, "#8e9498")
        if stamp:
            circ(a, 9.5, 12, 1.8, stamp, 0.9)
    return draw


def ic_cyl(body, band=None, valve="#7d8288"):  # баллон
    def draw(a):
        box(a, 5, 4, 10, 14, body)
        box(a, 6, 3, 9, 3, body)
        box(a, 7, 1, 8, 2, valve)
        if band:
            box(a, 5, 6, 10, 7, band)
    return draw


def ic_device(body, screen_="#46d18a", probe=None):  # ручной прибор
    def draw(a):
        box(a, 4, 2, 11, 14, body)
        box(a, 5, 3, 10, 7, "#1d1d1f")
        box(a, 6, 4, 9, 6, screen_)
        for (x, y) in ((5, 9), (7, 9), (9, 9), (5, 11), (7, 11), (9, 11)):
            px(a, x, y, "#3a3d40")
        if probe:
            line(a, 11, 13, 14, 15, probe)
    return draw


def ic_card(board="#2f6b3a", chip="#1d1d1f", gold=False):  # плата-карта
    def draw(a):
        box(a, 2, 4, 13, 12, board)
        box(a, 3, 12, 12, 13, "#d4af37" if gold else "#c9a14a")
        box(a, 6, 6, 9, 9, chip)
        for x in (3, 11):
            box(a, x, 6, x + 1, 7, "#3a3d40")
    return draw


def ic_box(body, mark=None, lid=None):
    def draw(a):
        box(a, 2, 5, 13, 13, body)
        if lid:
            box(a, 2, 4, 13, 5, lid)
        if mark:
            box(a, 6, 7, 9, 10, mark)
    return draw


def ic_bag(fill_, mark, cols=None):
    return sack(fill_, mark)


# ======================================================================
# иллюстрации справочника (GUI) — общие примитивы схем
# ======================================================================
def arrow(d, x0, y0, x1, y1, col=INK, w=2):
    d.line([x0, y0, x1, y1], fill=col, width=w)
    ang = math.atan2(y1 - y0, x1 - x0)
    for s in (-0.5, 0.5):
        d.line([x1, y1, x1 - 7 * math.cos(ang + s), y1 - 7 * math.sin(ang + s)], fill=col, width=w)


def plot_axes(d, w, h, m=18):
    d.line([m, h - m, w - m, h - m], fill=INK, width=2)
    d.line([m, m, m, h - m], fill=INK, width=2)
    return m, m, w - m, h - m


def curve(d, pts, col, w=3):
    d.line(pts, fill=col, width=w)
