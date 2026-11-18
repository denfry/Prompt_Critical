"""HQ: доменная печь и её окружение (GDD 08 §8.5) — лётка, засыпной аппарат, кауперы, щит,
пылеуловитель, машины литейного двора, жёлоб, ковш, агломашина, грануляция шлака.

Габариты — как у плейсхолдеров (spec_metallurgy.py), фасад на север.
"""
import math

from hqkit import Model
from hq_ironmaking import bolt_ring, gauge, plating, round_shade, shell_cube


def no_faces(*keep):
    """Рисовать только перечисленные грани."""
    return {d: False for d in ("north", "south", "east", "west", "up", "down") if d not in keep}


def frame(s):
    s.frame()


def seams(wrap, fw, n=16, step=8, rivets=True, stagger=2):
    """Обечайка из листов: горизонтальные стыки через step, вертикальные вперевязку, заклёпки по стыкам."""
    for y in range(step, wrap.h, step):
        wrap.seam_h(y)
        if rivets:
            wrap.rivets_h(y - 1, 1, wrap.w, 2)
    for row, y0 in enumerate(range(0, wrap.h, step)):
        for k in range(0, n, stagger):
            x = int((k + (row % 2)) * fw) % wrap.w
            wrap.vline(x, y0 + 1, min(y0 + step, wrap.h), add=-1.2)
            if rivets:
                wrap.rivets_v((x + 1) % wrap.w, y0 + 2, min(y0 + step, wrap.h), 2)


def rail(m, name, f, t, mat="yellow"):
    """Перила: стойки через 6 пикселей, поручень и средняя полоса. f, t — линия по низу (одна ось длинная)."""
    ax = 0 if abs(t[0] - f[0]) > abs(t[2] - f[2]) else 2
    a0, a1 = f[ax], t[ax]
    y = f[1]
    pos = sorted({p for p in [a0 + k * 6 for k in range(int((a1 - a0) // 6) + 1)] if p <= a1 - 0.5} | {a1 - 0.5})
    for i, p in enumerate(pos):
        lo = list(f)
        lo[ax] = p
        hi = [lo[0] + 0.5, y + 6, lo[2] + 0.5]
        m.box(f"{name}_post{i}", lo, hi, mat, share=f"{name}_post")
    for nm, yy in (("top", y + 6), ("mid", y + 3)):
        lo, hi = list(f), [t[0] + (0.5 if ax == 2 else 0), yy + 0.5, t[2] + (0.5 if ax == 0 else 0)]
        lo[1] = yy
        m.box(f"{name}_{nm}", lo, hi, mat)


def ladder_y(m, name, x0, z, y0, y1, mat="steel", face="north"):
    """Вертикальная лестница: две тетивы и ступени через 3 пикселя, плоскостью вдоль x."""
    m.box(f"{name}_l", [x0, y0, z], [x0 + 0.5, y1, z + 0.5], mat)
    m.box(f"{name}_r", [x0 + 3, y0, z], [x0 + 3.5, y1, z + 0.5], mat)
    for i, y in enumerate(range(int(y0) + 2, int(y1), 3)):
        m.box(f"{name}_rung{i}", [x0 + 0.5, y, z + 0.1], [x0 + 3, y + 0.5, z + 0.4], mat, share=f"{name}_rung")


def handwheel(m, name, c, axis, r=2.0, mat="red"):
    """Штурвал задвижки: обод из четырёх перекладин и ступица."""
    a = "xyz".index(axis)
    m.ring(name, axis, tuple(v for i, v in enumerate(c) if i != a), r, 0.5, *_span(c, axis, 0.5), mat, n=8)


def _span(c, axis, w):
    return {"x": (c[0] - w / 2, c[0] + w / 2), "y": (c[1] - w / 2, c[1] + w / 2), "z": (c[2] - w / 2, c[2] + w / 2)}[axis]


# ----------------------------------------------------------------------
# чугунная лётка: участок кожуха с литой рамой, зонт над лёткой; закрыта — глиняная пробка,
# открыта — струя чугуна падает в главный жёлоб
# ----------------------------------------------------------------------
def blast_furnace_taphole(opened=False):
    m = Model("blast_furnace_taphole_open" if opened else "blast_furnace_taphole", "ironmaking", cullface=True)

    def wall(s):
        plating(s, hot=True)
        for r, d in ((7.5, -0.4), (5.5, -0.8)):  # копоть над лёткой
            s.disc(8, 8, r, add=d)
    shell_cube(m, north=wall)

    def frame_face(s):
        s.frame()
        s.fill(3, 2, 7, 6, mat="black", lvl=1.0)
        s.frame(3, 2, 7, 6, -1.0, 0.6)
        for x, y in ((1, 1), (8, 1), (1, 6), (8, 6)):
            s.rivet(x, y)
        if opened:
            s.fill(4, 3, 6, 5, mat="hot", lvl=5.0, glow=True)
    m.box("frame", [3, 2, -1.5], [13, 10, 0], "cast", paint={"north": frame_face})
    # зонт-искрогаситель
    m.box("hood", [2, 11, -4], [14, 12, 0], "shell", paint={"up": lambda s: s.rivets_h(1, 1, 12, 3)})
    for i, x in enumerate((2, 13)):
        m.box(f"hood_brace{i}", [x, 10, -1.5], [x + 1, 11, 0], "shell")
    if opened:
        def jet(s):
            for y in range(s.h):
                s.hline(y, add=0.6 if y % 3 == 0 else -0.3)
            s.vline(0, add=0.8)
        m.box("glow_jet", [6.5, 3.5, -5], [9.5, 6.5, -1.5], "hot", lvl=4.5, paint={"north": False})
        m.box("glow_fall", [6.5, -6, -6.5], [9.5, 4, -3.5], "hot", lvl=4.5,
              paint={"north": jet, "east": jet, "west": jet, "south": jet})
        m.box("glow_splash", [4.5, -6.5, -8.5], [11.5, -5.5, -1.5], "hot", lvl=5.0, paint={"up": lambda s: s.grain(7, 1.2)})
    else:
        plug = m.cyl("plug", "z", (8, 6), 1.5, -2.5, -1.5, "clay", n=8, crisp=(True, False))
        plug.cap0.grain(3, 1.0)
        plug.cap0.px(2, 2, add=-1.5)
    return m.done()


# ----------------------------------------------------------------------
# колошник 3×2×3: засыпной аппарат с большим конусом, колошниковая площадка с перилами,
# четыре газоотвода с перекидными газопроводами и свечами, скиповый мост с запада
# ----------------------------------------------------------------------
def blast_furnace_top():
    m = Model("blast_furnace_top", "ironmaking")

    def grating(s):
        for x in range(0, s.w, 2):
            s.vline(x, add=-1.3)
        for y in range(0, s.h, 6):
            s.hline(y, add=-0.8)
    m.box("platform", [-16, 9, -16], [32, 10, 32], "zinc", paint={"up": grating, "down": grating}, lvl=2.5)
    for nm, f, t in (("n", [-16, 10, -16], [32, 10, -16]), ("s", [-16, 10, 31.5], [32, 10, 31.5]),
                     ("w", [-16, 10, -16], [-16, 10, 32]), ("e", [31.5, 10, -16], [31.5, 10, 32])):
        rail(m, f"rail_{nm}", f, t)

    throat = m.cyl("throat", "y", (8, 8), 5, 0, 9, "shell", caps=(False, False))
    seams(throat.wrap, 5, step=9)
    collar = m.cyl("collar", "y", (8, 8), 5.5, 10, 12, "cast", caps=(False, True))
    bolt_ring(collar.cap1, collar.cap1.w / 2, collar.cap1.h / 2, collar.r - 1, 24)
    hop = m.cyl("bell_hopper", "y", (8, 8), 4, 12, 18, "shell", caps=(False, True))
    seams(hop.wrap, 4, step=6)
    hop.cap1.disc(hop.cap1.w / 2, hop.cap1.h / 2, 6.5, mat="sinter", lvl=2.0)
    hop.cap1.grain(11, 1.2, 1, 1, 4, 4, hop.cap1.w - 4, hop.cap1.h - 4)

    def recv(s):
        s.frame()
        s.rivets_h(1, 1, s.w, 3)
    m.box("receiving_hopper", [3, 18, 3], [13, 24, 13], "shell",
          paint={"north": recv, "south": recv, "east": recv, "west": recv,
                 "up": lambda s: (s.frame(), s.fill(2, 2, 8, 8, mat="sinter", lvl=2.5).grain(5, 1.3, 1, 1, 2, 2, 8, 8))})

    # газоотводы и перекидные газопроводы
    for i, (x, z) in enumerate(((-5, 8), (21, 8), (8, -5), (8, 21))):
        u = m.cyl(f"uptake{i}", "y", (x, z), 2, 10, 30, "p_gas", n=8, caps=(False, True), share="uptake")
        u.wrap.rivets_h(4, 1, u.wrap.w, 2)
        m.cyl(f"uptake_fl{i}", "y", (x, z), 2.5, 10, 11.5, "cast", n=8, share="uptake_fl")
        m.cyl(f"bleeder{i}", "y", (x, z), 1.2, 30, 31.5, "red", n=8, share="bleeder")
    hx = m.cyl("header_x", "x", (27, 8), 2, -5, 31.95, "p_gas", n=8, caps=(False, True))
    hx.cap1.disc(2.5, 2.5, 2.2, ring=0.8, add=-1.0)
    m.cyl("header_z", "z", (27, 8), 2, -5, 21, "p_gas", n=8, caps=(False, False))
    m.cyl("downcomer_fl", "x", (27, 8), 2.6, 30, 31.95, "cast", n=8, caps=(False, False))

    # скиповый мост
    for i, z in enumerate((3, 12)):
        m.box(f"skip_girder{i}", [-16, 15, z], [8, 17, z + 1], "oxide", rot=("z", 45, [-4, 16, z + 0.5]),
              paint={"north": lambda s: s.rivets_h(0, 1, s.w, 3), "south": lambda s: s.rivets_h(0, 1, s.w, 3)})
        m.box(f"skip_rail{i}", [-16, 17, z + 0.25], [8, 17.5, z + 0.75], "steel", rot=("z", 45, [-4, 16, z + 0.5]))
    for i, x in enumerate((-12, -6, 0)):
        m.box(f"skip_tie{i}", [x, 15.5, 4], [x + 1, 16.5, 12], "oxide", rot=("z", 45, [-4, 16, 8]))
    return m.done()


def blast_furnace_top_cone():
    m = Model("blast_furnace_top/cone", "ironmaking", kind="partial")
    m.cyl("bell_low", "y", (8, 8), 2.5, -2, 0, "cast", caps=(True, False))
    m.cyl("bell_mid", "y", (8, 8), 2, 0, 2, "cast", caps=(False, False))
    m.cyl("bell_top", "y", (8, 8), 1.2, 2, 4, "cast", caps=(False, True))
    m.cyl("rod", "y", (8, 8), 1, 4, 20, "steel", n=8, caps=(False, True))
    return m.done()


# ----------------------------------------------------------------------
# воздухонагреватель Каупера 3×9×3: клёпаный цилиндр ⌀ 2,5 блока на бетонном фундаменте.
# Низ — горелка коксового газа, дымовой клапан и холодное дутьё; середина — горячее дутьё
# с водоохлаждаемым шибером; купол — ступенчатый свод с люком. Лестница на южной стороне.
# ----------------------------------------------------------------------
STOVE_FW = 8


def stove_shell(m, y0, y1, top=False):
    sh = m.cyl("shell", "y", (8, 8), STOVE_FW, y0, y1, "shell", caps=(False, top))
    seams(sh.wrap, STOVE_FW, step=12)
    sh.wrap.streaks(41, 10, length=(3, 9), y0=1)
    return sh


def stove_ladder(m, y0, y1):
    ladder_y(m, "ladder", 6, 29, y0, y1)


def hot_blast_stove(part):
    m = Model({"bottom": "hot_blast_stove", "middle": "hot_blast_stove_middle", "dome": "hot_blast_stove_dome"}[part],
              "ironmaking")
    if part == "bottom":
        stove_shell(m, -13, 32)
        base = m.cyl("plinth", "y", (8, 8), STOVE_FW + 0.6, -16, -13, "concrete", caps=(False, True))
        base.wrap.hline(0, add=0.8)
        # горелка: газопровод с задвижкой и штурвалом, воздух горения — с востока
        m.cyl("burner_neck", "z", (8, -6), 3, -15.9, -10, "cast", n=8, crisp=(True, False))
        m.cyl("gas_pipe", "x", (-6, -14), 1.5, -15.9, 8, "p_gas", n=8)
        m.box("gas_valve", [-6, -9.5, -16], [-2, -2.5, -12], "cast",
              paint={d: frame for d in ("north", "east", "west", "up")})
        m.box("valve_stem", [-4.5, -2.5, -14.5], [-3.5, 1, -13.5], "steel")
        handwheel(m, "valve_wheel", (-4, 1.25, -13.6), "y", 2.2)
        fan = m.cyl("air_duct", "x", (-6, -6), 1.8, 8, 20, "p_air", n=8)
        round_shade(fan.wrap, 1.8, 8)
        # холодное дутьё — с востока
        cb = m.cyl("cold_blast", "x", (-8.3, 8), 3, 26, 31.95, "p_air", crisp=(False, True))
        round_shade(cb.wrap, 3)
        cb.cap1.disc(cb.cap1.w / 2, cb.cap1.h / 2, cb.r - 0.5, ring=1.0, add=0.8)
        bolt_ring(cb.cap1, cb.cap1.w / 2, cb.cap1.h / 2, cb.r - 1.5, 12)
        # дымовой клапан — на запад
        ch = m.cyl("chimney_valve", "x", (-9.6, 8), 2.5, -15.95, -10, "cast", crisp=(True, False))
        ch.cap0.disc(ch.cap0.w / 2, ch.cap0.h / 2, ch.r - 1, mat="black", lvl=1.5)
        stove_ladder(m, -13, 32)
    elif part == "middle":
        stove_shell(m, -16, 32)
        for i, y in enumerate((-4, 20)):
            band = m.cyl(f"band{i}", "y", (8, 8), STOVE_FW + 0.4, y, y + 1.5, "cast", caps=(True, True), share="band")
            band.wrap.rivets_h(0, 1, band.wrap.w, 3)
        # патрубок горячего дутья с водоохлаждаемым шибером
        hb = m.cyl("hot_blast", "z", (6, 8), 3.4, -15.95, -10, "shell", crisp=(True, False))
        round_shade(hb.wrap, 3.4)
        hb.cap0.disc(hb.cap0.w / 2, hb.cap0.h / 2, hb.r - 0.4, ring=1.2, add=0.7)
        hb.cap0.disc(hb.cap0.w / 2, hb.cap0.h / 2, hb.r - 2.4, mat="brick", lvl=1.5)
        hb.cap0.disc(hb.cap0.w / 2, hb.cap0.h / 2, hb.r - 4.5, mat="black", lvl=1.0)
        bolt_ring(hb.cap0, hb.cap0.w / 2, hb.cap0.h / 2, hb.r - 1.0, 12)
        m.box("hot_valve", [2, 14, -14], [14, 20, -10.5], "cast",
              paint={d: frame for d in ("north", "east", "west", "up")})
        for i, x in enumerate((3, 12)):
            m.box(f"valve_water{i}", [x, 20, -13], [x + 1, 26, -12], "p_water")
        m.box("valve_actuator", [6, 20, -14], [10, 25, -11], "mblue", paint={"north": frame})
        # люк
        mh = m.cyl("manhole", "z", (-6, 8), 2, -14, -12.5, "cast", n=8, crisp=(True, False))
        bolt_ring(mh.cap0, mh.cap0.w / 2, mh.cap0.h / 2, mh.r - 0.6, 8)
        stove_ladder(m, -16, 32)
    else:
        stove_shell(m, -16, 8, top=True)
        tiers = ((STOVE_FW - 1, 8, 14), (STOVE_FW - 2.5, 14, 19), (STOVE_FW - 4, 19, 23), (2.4, 23, 26))
        for i, (fw, y0, y1) in enumerate(tiers):
            t = m.cyl(f"dome{i}", "y", (8, 8), fw, y0, y1, "shell", caps=(False, True))
            seams(t.wrap, fw, step=y1 - y0, rivets=True)
            bolt_ring(t.cap1, t.cap1.w / 2, t.cap1.h / 2, t.r - 0.8, 24 if i < 2 else 12)
        top = m.cyl("top_hatch", "y", (8, 8), 1.6, 26, 28.5, "cast", caps=(False, True))
        bolt_ring(top.cap1, top.cap1.w / 2, top.cap1.h / 2, top.r - 0.5, 8)
        m.cyl("bleeder", "y", (16, 8), 1, 19, 27, "steel", n=8, caps=(False, True))
        m.box("bleeder_weight", [15, 25, 4], [17, 27, 12], "red")
        stove_ladder(m, -16, 8)
    return m.done()


# ----------------------------------------------------------------------
# щит домны: пульт с наклонной панелью ключей и вертикальная панель с мнемосхемой печи,
# приборами и маяком тревоги
# ----------------------------------------------------------------------
def blast_furnace_controller(alarm=False):
    m = Model("blast_furnace_controller_alarm" if alarm else "blast_furnace_controller", "ironmaking", cullface=True)
    m.box("plinth", [0, 0, 4.5], [16, 1, 16], "black")
    m.box("desk", [0, 1, 5], [16, 8, 16], "pgrey", paint={"north": lambda s: (s.frame(), s.seam_v(7, 1, 7),
                                                                            s.px(6, 3, add=1.5), s.px(9, 3, add=1.5))})

    def keys(s):
        s.frame()
        for x in range(2, 15, 3):
            s.px(x, 2, mat="black", lvl=1.5).px(x, 3, mat="steel", lvl=4.0)
        for i, x in enumerate(range(2, 15, 3)):
            s.px(x, 5, mat=("lamp_g", "lamp_g", "red", "yellow", "lamp_g")[i], lvl=4.0)
    m.box("desk_top", [0, 7.6, 5], [16, 8.6, 12], "pgrey", rot=("x", -22.5, [8, 8, 8.5]),
          paint={"up": keys})
    m.box("board", [0, 8, 12], [16, 16, 16], "pgrey")

    def mimic(s):
        s.frame()
        # печь: шахта, заплечики, горн
        s.vline(6, 1, 5, mat="white", lvl=4.5).vline(10, 1, 5, mat="white", lvl=4.5)
        s.px(5, 5, mat="white", lvl=4.5).px(11, 5, mat="white", lvl=4.5)
        s.hline(6, 5, 12, mat="white", lvl=4.5)
        s.hline(1, 7, 10, mat="p_gas", lvl=4.0)
        s.hline(4, 1, 5, mat="p_air", lvl=4.0).hline(4, 12, 15, mat="p_air", lvl=4.0)
        s.px(2, 2, mat="white", lvl=5).px(13, 2, mat="white", lvl=5)
    m.box("board_face", [0.5, 8.5, 11.5], [15.5, 15.5, 12], "pgrey", paint={"north": mimic})
    for i, (x, y) in enumerate(((3, 10.5), (13, 10.5))):
        gauge(m, f"gauge{i}", x, y + 3, 11.5, (1, -1) if i else (-1, -1))
    lamps = ((7, 11, "lamp_g", True), (9, 11, "lamp_g", True), (8, 9.5, "lamp_r", alarm))
    for i, (x, y, mat, on) in enumerate(lamps):
        m.box(("glow_" if on else "") + f"lamp{i}", [x - 0.5, y - 0.5, 11.25], [x + 0.5, y + 0.5, 11.5],
              mat if on else "black", lvl=4.5 if on else 2.5, bevel=False, paint=no_faces("north"))
    m.cyl("beacon_base", "y", (13.5, 14), 1, 16, 16.5, "black", n=8, caps=(False, True))
    m.cyl(("glow_" if alarm else "") + "beacon", "y", (13.5, 14), 0.8, 16.5, 18, "lamp_r",
          n=8, lvl=4.5 if alarm else 2.0, caps=(False, True))
    return m.done()


# ----------------------------------------------------------------------
# пылеуловитель 2×3×2: цилиндр с коническим днищем на четырёх ногах с раскосами,
# пылевой затвор, вход газа сверху с севера, выход на восток
# ----------------------------------------------------------------------
def dust_catcher():
    m = Model("dust_catcher", "ironmaking")
    for i, (x, z) in enumerate(((5, 5), (25, 5), (5, 25), (25, 25))):
        m.box(f"leg{i}", [x, -16, z], [x + 2, 6, z + 2], "shell", share="leg")
        m.box(f"foot{i}", [x - 1, -16, z - 1], [x + 3, -15, z + 3], "concrete", share="foot")
    for i, (z, ang) in enumerate(((5.5, 45), (5.5, -45), (25.5, 45), (25.5, -45))):
        m.box(f"brace_z{i}", [4, -6.5, z], [28, -5.5, z + 1], "shell", rot=("z", ang, [16, -6, z + 0.5]), share="brace")
    for i, (x, ang) in enumerate(((5.5, 45), (5.5, -45), (25.5, 45), (25.5, -45))):
        m.box(f"brace_x{i}", [x, -6.5, 4], [x + 1, -5.5, 28], "shell", rot=("x", ang, [x + 0.5, -6, 16]), share="brace_x")
    m.cyl("cone0", "y", (16, 16), 1.6, -12, -8, "shell", caps=(True, False))
    m.cyl("cone1", "y", (16, 16), 2.4, -8, -4, "shell", caps=(False, False))
    m.cyl("cone2", "y", (16, 16), 3.3, -4, 0, "shell", caps=(False, False))
    m.cyl("cone3", "y", (16, 16), 4.2, 0, 3, "shell", caps=(False, False))
    ring = m.cyl("girder", "y", (16, 16), 5.4, 3, 5, "cast")
    ring.wrap.rivets_h(0, 1, ring.wrap.w, 3)
    d = m.cyl("drum", "y", (16, 16), 5, 5, 27, "shell", caps=(False, True))
    seams(d.wrap, 5, step=11)
    d.wrap.streaks(9, 8, length=(3, 8), y0=1)
    bolt_ring(d.cap1, d.cap1.w / 2, d.cap1.h / 2, d.r - 0.8, 24)
    m.cyl("roof", "y", (16, 16), 3, 27, 28.5, "shell", caps=(False, True))
    m.box("dust_valve", [13.5, -15, 13.5], [18.5, -12, 18.5], "cast", paint={d_: frame for d_ in ("north", "east", "south", "west")})
    m.box("valve_lever", [18.5, -14, 15.5], [23, -13, 16.5], "red")
    gi = m.cyl("gas_in", "z", (29.5, 16), 1.6, -0.01, 16, "p_gas", n=8, crisp=(True, False))
    gi.cap0.disc(gi.cap0.w / 2, gi.cap0.h / 2, 1.2, ring=0.8, add=-1.0)
    m.cyl("gas_in_down", "y", (16, 16), 1.6, 27.5, 31.6, "p_gas", n=8, caps=(False, False))
    go = m.cyl("gas_out", "x", (18, 16), 2, 27, 31.95, "p_gas", n=8, crisp=(False, True))
    go.cap1.disc(go.cap1.w / 2, go.cap1.h / 2, 1.6, ring=0.8, add=-1.0)
    mh = m.cyl("manhole", "z", (14, 16), 1.6, 2.5, 4, "cast", n=8, crisp=(True, False))
    bolt_ring(mh.cap0, mh.cap0.w / 2, mh.cap0.h / 2, mh.r - 0.5, 8)
    return m.done()


# ----------------------------------------------------------------------
# машины литейного двора 2×1×1: поворотная колонна на востоке, стрела на запад к лётке.
# Бурмашина — каретка с мотором (бур — partial), пушка — цилиндр с бункером лёточной массы.
# ----------------------------------------------------------------------
def cast_house_machine(kind):
    m = Model("cast_house_machine" if kind == "drill" else "cast_house_machine_mud_gun", "ironmaking")
    m.box("base", [22, 0, 2], [32, 1, 14], "cast", paint={"up": lambda s: [s.rivet(x, y) for x, y in ((1, 1), (8, 1), (1, 10), (8, 10))]})
    col = m.cyl("column", "y", (25.4, 8), 2, 1, 9, "red", n=16, caps=(False, False))
    col.wrap.hline(0, add=0.8)
    head = m.cyl("slew", "y", (25.4, 8), 2.6, 9, 11, "cast", caps=(False, True))
    bolt_ring(head.cap1, head.cap1.w / 2, head.cap1.h / 2, head.r - 0.6, 12)

    def boom_side(s):
        s.frame()
        for x in range(3, s.w - 2, 5):
            s.fill(x, 1, x + 2, s.h - 1, mat="black", lvl=1.5)
    m.box("boom", [2, 11, 6], [30, 14, 10], "red", paint={"north": boom_side, "south": boom_side})
    m.box("hydraulic", [20, 7, 9.5], [27, 8, 10.5], "steel", rot=("z", 22.5, [24, 9, 10]))
    m.box("hose", [6, 14, 7.5], [24, 14.5, 8.5], "black")
    if kind == "drill":
        m.box("carriage", [4, 8, 4], [14, 11, 12], "red", paint={"north": frame, "south": frame})
        mot = m.cyl("motor", "x", (6, 8), 2.2, 9, 15, "mblue", n=8)
        mot.wrap.louvres(0, 1, mot.wrap.w, mot.wrap.h - 1, 2)
        m.box("chuck", [2, 4.5, 6.5], [4, 7.5, 9.5], "steel")
    else:
        b = m.cyl("barrel", "x", (6, 8), 2.4, 2, 22, "steel", n=8)
        round_shade(b.wrap, 2.4, 8)
        for i, x in enumerate((4, 12, 20)):
            m.cyl(f"barrel_band{i}", "x", (6, 8), 2.8, x, x + 1, "red", n=8, share="band")

        def hopper_top(s):
            s.frame()
            s.fill(1, 1, s.w - 1, s.h - 1, mat="clay", lvl=2.5).grain(2, 1.0, 1, 1)
        m.box("clay_hopper", [10, 9, 5], [16, 14, 11], "red", paint={"up": hopper_top, "north": frame, "south": frame})
    return m.done()


def cast_house_bit():
    m = Model("cast_house_machine/bit", "ironmaking", kind="partial")
    m.cyl("bit", "x", (6, 8), 0.8, -6, 2, "steel", n=8)
    m.cyl("tip", "x", (6, 8), 1.2, -6, -5, "cast", n=8)
    return m.done()


def cast_house_piston():
    m = Model("cast_house_machine/piston", "ironmaking", kind="partial")
    noz = m.cyl("nozzle", "x", (6, 8), 1.4, -1, 2, "cast", n=8, crisp=(True, False))
    noz.cap0.disc(noz.cap0.w / 2, noz.cap0.h / 2, 0.9, mat="clay", lvl=2.5)
    return m.done()


# ----------------------------------------------------------------------
# главный жёлоб литейного двора: огнеупорные борта, песчаная постель; при заливке — чугун с коркой
# ----------------------------------------------------------------------
def iron_runner(filled=False):
    m = Model("iron_runner_filled" if filled else "iron_runner", "ironmaking", cullface=True)

    def lining(s):
        s.streaks(13, 4, mat="sinter", length=(2, 4), y0=0)
    for i, (z0, z1, inner) in enumerate(((1, 5, "south"), (11, 15, "north"))):
        m.box(f"wall{i}", [0, 0, z0], [16, 7, z1], "concrete", lvl=2.5,
              paint={inner: lambda s: (s.fill(mat="clay", lvl=2.5).grain(17, 0.9, 1, 1), lining(s)),
                     "up": lambda s: [s.px(x, y, mat="sinter", lvl=2.5) for x, y in ((2, 1), (7, 2), (12, 0), (14, 3), (5, 3))]})
    m.box("bed", [0, 0, 5], [16, 3, 11], "sinter", paint={"up": lambda s: s.grain(29, 1.0, 1, 1)})
    if filled:
        def melt(s):
            for y in range(s.h):
                s.hline(y, add=(0.6 if y in (1, 4) else 0.0))
            rnd = [(3, 2), (9, 4), (13, 1), (6, 5)]
            for x, y in rnd:
                s.px(x, y, mat="sinter", lvl=3.0)
        m.box("glow_melt", [0, 3, 5], [16, 5.5, 11], "hot", lvl=4.0, paint={"up": melt, "down": False})
    return m.done()


# ----------------------------------------------------------------------
# ковш 2×2×2: стойки с подшипниками цапф; корпус (partial) — клёпаная обечайка с поясами, цапфы,
# носок, футеровка; зеркало металла (partial) — расплав с коркой
# ----------------------------------------------------------------------
def ladle_stand():
    m = Model("ladle", "ironmaking")
    m.box("base", [0, 0, 8], [32, 2, 24], "concrete")
    for nm, x0 in (("w", 0), ("e", 28)):
        m.box(f"stand_{nm}", [x0, 2, 12], [x0 + 4, 17, 20], "oxide", paint={"north": frame, "south": frame}, share="stand")
        m.box(f"strut_{nm}", [x0 + 0.5, 2, 9], [x0 + 3.5, 12, 10.5], "oxide", rot=("x", 22.5, [x0 + 2, 7, 12]), share="strut")
        m.box(f"strut2_{nm}", [x0 + 0.5, 2, 21.5], [x0 + 3.5, 12, 23], "oxide", rot=("x", -22.5, [x0 + 2, 7, 20]), share="strut")
        m.box(f"bearing_{nm}", [x0 - 0.01 if x0 == 0 else x0, 17, 13], [x0 + 4, 22, 19], "cast",
              paint={"up": lambda s: (s.rivet(1, 1), s.rivet(2, 4))}, share="bearing")
    return m.done()


def ladle_body():
    m = Model("ladle/body", "ironmaking", kind="partial")

    def shell_out(s):
        s.seam_h(9), s.seam_h(18)
        s.rivets_h(8, 0, s.w, 2), s.rivets_h(17, 0, s.w, 2)
        s.streaks(19, 2, length=(3, 7), y0=1)
    m.ring("wall", "y", (16, 16), 11.5, 1.5, 3, 28, "shell", inner="brick", paint_out=shell_out)
    bot = m.cyl("bottom", "y", (16, 16), 4.2, 2, 4, "shell", caps=(True, False))
    lining = m.cyl("lining", "y", (16, 16), 3.9, 4, 22, "brick", caps=(False, True))
    lining.cap1.bricks(31, 4, 2)
    for i, y in enumerate((8, 16, 24)):
        b = m.cyl(f"band{i}", "y", (16, 16), 4.8, y, y + 1.5, "cast", caps=(True, True), share="band")
        b.wrap.rivets_h(0, 1, b.wrap.w, 3)
    m.ring("lip", "y", (16, 16), 12.2, 2.2, 27.5, 29, "cast", inner="brick")
    m.box("spout", [13, 26, 1.5], [19, 28.5, 5], "cast", paint={"up": lambda s: s.fill(1, 0, 5, 3, mat="brick", lvl=2.5)})
    tr = m.cyl("trunnion", "x", (20, 16), 1.6, 1, 31, "steel", n=8, crisp=(True, True))
    for cap in (tr.cap0, tr.cap1):
        cap.px(int(cap.w / 2), int(cap.h / 2), add=-1.2)
    for nm, x0 in (("w", 3.5), ("e", 27.5)):
        m.box(f"trunnion_plate_{nm}", [x0, 15, 12], [x0 + 1, 25, 20], "cast", share="tplate",
              paint={"west": lambda s: [s.rivet(x, y) for x, y in ((1, 1), (6, 1), (1, 8), (6, 8))],
                     "east": lambda s: [s.rivet(x, y) for x, y in ((1, 1), (6, 1), (1, 8), (6, 8))]})
    return m.done()


def ladle_melt():
    m = Model("ladle/melt", "ironmaking", kind="partial")
    mt = m.cyl("glow_melt", "y", (16, 16), 3.85, 22, 24, "hot", lvl=4.0, caps=(False, True))
    c = mt.cap1
    for x, y in ((5, 6), (12, 4), (14, 12), (6, 13), (9, 9)):
        c.px(x, y, mat="sinter", lvl=3.0).px(x + 1, y, mat="sinter", lvl=3.5)
    c.disc(c.w / 2, c.h / 2, 3.5, add=0.8)
    return m.done()


# ----------------------------------------------------------------------
# агломашина 3×2×3, лента вдоль z: рама, вакуум-камеры над коллектором, зажигательный горн
# над северной частью. Паллеты с шихтой — partial.
# ----------------------------------------------------------------------
def sinter_strand(lit=False):
    m = Model("sinter_strand_lit" if lit else "sinter_strand", "ironmaking")
    for nm, x0 in (("w", -16), ("e", 28)):
        for i, z in enumerate((-16, -1, 14, 29)):
            m.box(f"col_{nm}{i}", [x0 + 1, 0, z], [x0 + 3, 20 if z < 0 else 12, z + 3], "shell", share=f"col{z < 0}")
        m.box(f"girder_{nm}", [x0, 9, -16], [x0 + 4, 12, 32], "shell", paint={"north": frame, "south": frame,
                                                                               "east": lambda s: s.rivets_h(1, 0, s.w, 3),
                                                                               "west": lambda s: s.rivets_h(1, 0, s.w, 3)})
        m.box(f"rail_{nm}", [x0 + (4 if nm == "w" else -1), 9, -16], [x0 + (5 if nm == "w" else 0), 10, 32], "steel")
    for i, z in enumerate(range(-14, 24, 9)):
        m.box(f"windbox{i}", [-11, 4, z], [27, 9, z + 8], "cast", share="windbox",
              paint={"east": frame, "west": frame, "down": frame})
        m.box(f"leg{i}", [6, 3, z + 3], [10, 4, z + 5], "cast", share="wleg")
    duct = m.cyl("main_duct", "z", (8, 3.5), 2.8, -15.95, 31.95, "shell", n=8, crisp=(True, True))
    round_shade(duct.wrap, 2.8, 8)
    for cap in (duct.cap0, duct.cap1):
        cap.disc(cap.w / 2, cap.h / 2, cap.w / 2 - 1, ring=0.8, add=-1.0)

    def hood_side(s):
        s.frame()
        s.rivets_h(1, 1, s.w, 3)
        s.rivets_h(s.h - 2, 1, s.w, 3)
        s.streaks(3, 6, mat="black", length=(2, 5), y0=s.h - 6)
    m.box("hood", [-12, 20, -16], [28, 29, -2], "shell",
          paint={"north": hood_side, "south": hood_side, "east": hood_side, "west": hood_side,
                 "down": lambda s: s.fill(mat="brick", lvl=2.0).grain(3, 0.8)})
    m.cyl("gas_header", "x", (30, -9), 1.4, -15.95, 31.95, "p_gas", n=8)
    for i, x in enumerate(range(-9, 27, 6)):
        m.box(f"burner{i}", [x, 29, -10], [x + 2, 31, -8], "cast", share="burner")
    m.box("air_header", [-12, 29, -5], [28, 31, -3], "p_air")
    if lit:
        m.box("glow_flame", [-11, 19.9, -15], [27, 20, -3], "hot", lvl=4.5, paint=no_faces("down"),
              )
    return m.done()


def sinter_pallets():
    m = Model("sinter_strand/pallets", "ironmaking", kind="partial")

    def car_side(s):
        for x in range(0, s.w, 6):
            s.vline(x, add=-1.5)
        s.hline(0, add=0.6)
    m.box("pallets", [-12, 10, -16], [28, 13, 32], "cast", paint={"east": car_side, "west": car_side})
    for nm, x0 in (("w", -12), ("e", 27)):
        m.box(f"skirt_{nm}", [x0, 13, -16], [x0 + 1, 17, 32], "cast", share="skirt",
              paint={"east": car_side, "west": car_side})
    for i, z in enumerate(range(-15, 32, 6)):
        for nm, x0 in (("w", -13), ("e", 28)):
            m.box(f"wheel_{nm}{i}", [x0, 9.5, z + 1], [x0 + 1, 12.5, z + 4], "steel", share="wheel",
                  paint={"east": lambda s: s.px(1, 1, mat="black", lvl=1.5), "west": lambda s: s.px(1, 1, mat="black", lvl=1.5)})

    def bed(s):
        s.grain(37, 1.4, 1, 1)
        for x, y in ((5, 9), (21, 30), (33, 14), (11, 40), (28, 4)):
            s.px(x, y, add=1.5).px(x + 1, y + 1, add=-1.0)
    m.box("sinter_bed", [-11, 13, -16], [27, 17, 32], "sinter", paint={"up": bed})
    return m.done()


# ----------------------------------------------------------------------
# грануляция шлака 2×1×1: бетонный жёлоб с водой, коллектор с соплами, шлаковый носок
# ----------------------------------------------------------------------
def slag_granulator():
    m = Model("slag_granulator", "ironmaking")
    m.box("floor", [0, 0, 2], [32, 2, 14], "concrete")
    for i, (z0, z1) in enumerate(((2, 4), (12, 14))):
        m.box(f"wall{i}", [0, 2, z0], [32, 7, z1], "concrete", share="gwall",
              paint={"up": lambda s: s.hline(0, add=0.6)})
    m.box("end_wall", [30, 2, 4], [32, 7, 12], "concrete")

    def water(s):
        s.e[:] = False
        for y in range(s.h):
            for x in range((y * 3) % 5, s.w, 5):
                s.px(x, y, add=1.0)
        for x, y in ((4, 3), (9, 5), (12, 2), (17, 4), (22, 6), (26, 3), (14, 6)):
            s.px(x, y, mat="sinter", lvl=3.5)
    m.box("water", [0, 2, 4], [30, 5.5, 12], "water", paint={"up": water})
    hdr = m.cyl("header", "z", (4, 8.5), 1.4, 1, 15, "p_water", n=8, crisp=(True, True))
    round_shade(hdr.wrap, 1.4, 8)
    for i, z in enumerate((5, 8, 11)):
        m.box(f"nozzle{i}", [5.5, 7.5, z - 0.5], [7, 8.5, z + 0.5], "steel", share="nozzle")
    m.box("riser", [3, 0, 14.5], [5, 7.5, 16], "p_water")
    m.box("spout", [0, 9.5, 5.5], [9, 11.5, 10.5], "cast",
          paint={"up": lambda s: (s.fill(0, 1, s.w, 4, mat="brick", lvl=2.0), s.streaks(41, 4, mat="sinter", length=(1, 3), y0=1))})
    m.box("spout_post", [1, 2, 7], [3, 9.5, 9], "cast")
    return m.done()


for _o in (False, True):
    blast_furnace_taphole(_o)
blast_furnace_top()
blast_furnace_top_cone()
for _p in ("bottom", "middle", "dome"):
    hot_blast_stove(_p)
for _a in (False, True):
    blast_furnace_controller(_a)
dust_catcher()
cast_house_machine("drill")
cast_house_machine("mud_gun")
cast_house_bit()
cast_house_piston()
iron_runner()
iron_runner(True)
ladle_stand()
ladle_body()
ladle_melt()
sinter_strand()
sinter_strand(True)
sinter_pallets()
slag_granulator()
