"""HQ: освещение завода — щиток освещения и светильники, каждый во включённом и выключенном виде (`<id>_off`).

Габариты — как формы блоков в PcBlocks (фасад на север, настенные прижаты к югу, потолочные — к верху).
Светящиеся части называются glow_* только во включённом виде.
"""
import math

from hqkit import Model
from hq_ironmaking import bolt_ring


def nm(base, lit):
    return base if lit else base + "_off"


def glow(name, lit):
    return ("glow_" if lit else "") + name


def lens(lit, mat="light"):
    """Материал и уровень светящейся части: горит — светлый, погашен — серое стекло."""
    return (mat, 4.5) if lit else ("glass", 2.5)


def only(*keep):
    return {d: False for d in ("north", "south", "east", "west", "up", "down") if d not in keep}


def frame(s):
    s.frame()


def running_man(s, x, y, mat="white", lvl=5.0):
    """Пиктограмма «бегущий человек» 4×5 и стрелка вправо (ГОСТ 12.4.026, упрощённо)."""
    for px, py in ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (3, 2), (1, 3), (2, 3), (0, 4), (3, 4),
                   (5, 2), (6, 2), (7, 2), (6, 1), (6, 3)):
        s.px(x + px, y + py, mat=mat, lvl=lvl)


# ----------------------------------------------------------------------
# щиток освещения ЩО: шкаф на стене, дверца с ручкой и замком, окно на автоматы групп,
# лампа «сеть», знак «Осторожно, электрическое напряжение», сальники кабеля сверху и снизу
# ----------------------------------------------------------------------
def lighting_board(lit):
    m = Model(nm("lighting_board", lit), "grid", cullface=True)

    def door(s):
        s.frame()
        s.frame(1, 1, s.w - 1, s.h - 1, -0.6, 0.4)
        # окно на автоматы
        s.fill(2, 6, 8, 10, mat="glass", lvl=2.0)
        for x in range(2, 8, 2):
            s.fill(x, 7, x + 1, 9, mat="white", lvl=4.5).px(x, 7, mat="black", lvl=1.5)
        s.frame(2, 6, 8, 10, -0.8, 0.6)
        # знак напряжения
        for i in range(4):
            s.hline(1 + i, 4 - i, 5 + i, mat="yellow", lvl=4.5)
        s.px(5, 2, mat="black", lvl=1.0).px(4, 3, mat="black", lvl=1.0)
        # ручка и замок
        s.vline(8, 2, 5, mat="black", lvl=2.0)
        s.px(8, 11, mat="steel", lvl=4.0)
    m.box("cabinet", [3, 1, 13], [13, 15, 16], "pgrey", paint={"north": door})
    m.box("hinge_a", [12.5, 3, 12.6], [13.2, 5, 13], "steel", share="hinge")
    m.box("hinge_b", [12.5, 11, 12.6], [13.2, 13, 13], "steel", share="hinge")
    m.cyl(glow("lamp", lit), "z", (5, 13), 0.5, 12.5, 13, "lamp_g" if lit else "black", n=8, lvl=4.5 if lit else 2.0,
          crisp=(True, False))
    for i, (y0, y1) in enumerate(((15, 16), (0, 1))):
        m.box(f"gland{i}", [7, y0, 13.5], [9, y1, 15.5], "black", share="gland")
    return m.done()


# ----------------------------------------------------------------------
# ГСП на штанге: потолочная чашка, штанга, корпус ПРА, отражатель, лампа ДРЛ
# ----------------------------------------------------------------------
def industrial_light(lit):
    m = Model(nm("industrial_light", lit), "decor", cullface=True)
    cup = m.cyl("canopy", "y", (8, 8), 1.6, 15, 16, "pgrey", n=8, caps=(True, False))
    bolt_ring(cup.cap0, cup.cap0.w / 2, cup.cap0.h / 2, 1.6, 4)
    m.cyl("rod", "y", (8, 8), 0.4, 12, 15, "steel", n=8, caps=(False, False))
    ballast = m.cyl("ballast", "y", (8, 8), 1.4, 9.5, 12, "pgrey", caps=(False, True))
    ballast.wrap.louvres(0, 1, ballast.wrap.w, 2, 2)
    m.cyl("reflector_top", "y", (8, 8), 1.6, 8.5, 9.5, "pgrey", caps=(False, True))
    r = m.cyl("reflector", "y", (8, 8), 2, 5.5, 8.5, "pgrey", caps=(False, True))
    r.wrap.hline(r.wrap.h - 1, add=-0.8)
    mat, lvl = lens(lit, "lamp_y")
    lz = m.cyl(glow("lens", lit), "y", (8, 8), 1.8, 5.3, 5.5, mat, lvl=lvl, caps=(True, False))
    lz.cap0.disc(lz.cap0.w / 2, lz.cap0.h / 2, 1.6, add=0.6)
    return m.done()


# ----------------------------------------------------------------------
# консольный уличный светильник ЖКУ: настенный кронштейн, труба консоли, корпус с плафоном снизу
# ----------------------------------------------------------------------
def street(lit):
    m = Model(nm("industrial_light_street", lit), "decor", cullface=True)
    m.box("bracket", [6, 8.5, 15], [10, 12, 16], "cast", paint={"north": lambda s: (s.rivet(0, 0), s.rivet(3, 2))})
    m.cyl("arm", "z", (8, 11), 0.6, 9, 15, "steel", n=8)

    def top(s):
        s.frame()
        s.hline(2, 1, s.w - 1, add=-0.8)
    m.box("housing", [5.5, 10, 1], [10.5, 12, 9.5], "zinc", paint={"up": top})
    m.box("nose", [6, 9.5, 0.5], [10, 11, 1.5], "zinc")
    mat, lvl = lens(lit, "lamp_y")
    m.box(glow("bowl", lit), [5.8, 9, 1.5], [10.2, 10, 8.5], mat, lvl=lvl, paint=only("down", "north", "east", "west"))
    return m.done()


# ----------------------------------------------------------------------
# прожектор: опорная плита, лира, корпус с отражателем наклонён вниз на 22,5°, стекло
# ----------------------------------------------------------------------
def floodlight(lit):
    m = Model(nm("industrial_light_floodlight", lit), "decor", cullface=True)
    m.box("base", [4, 0, 4], [12, 1, 10], "cast", paint={"up": lambda s: [s.rivet(x, y) for x, y in ((1, 1), (6, 1), (1, 4), (6, 4))]})
    m.box("post", [7, 1, 6], [9, 3, 8], "cast")
    for i, x in enumerate((3, 12.5)):
        m.box(f"yoke{i}", [x, 3, 6.5], [x + 0.5, 9, 7.5], "cast", share="yoke")
    m.box("yoke_bottom", [3, 2.5, 6.5], [13, 3, 7.5], "cast")
    tilt = ("x", -22.5, [8, 7.5, 7])

    def back(s):
        s.frame()
        for y in range(2, s.h - 1, 2):
            s.hline(y, 1, s.w - 1, add=-1.2)
    m.box("housing", [3.5, 3.5, 5], [12.5, 11.5, 10], "mblue", rot=tilt, paint={"south": back})
    m.box("rim", [3.3, 3.3, 4.5], [12.7, 11.7, 5], "cast", rot=tilt)
    mat, lvl = lens(lit)

    def glass(s):
        for y in range(0, s.h, 2):
            s.hline(y, add=0.4)
        s.disc(s.w / 2, s.h / 2, 2.5, add=0.8 if lit else 0.3)
    m.box(glow("glass", lit), [3.8, 3.8, 4.4], [12.2, 11.2, 4.5], mat, lvl=lvl, rot=tilt, paint={**only("north"), "north": glass})
    return m.done()


# ----------------------------------------------------------------------
# световой указатель «Выход»: короб на стене, зелёное табло с пиктограммой
# ----------------------------------------------------------------------
def exit_sign(lit):
    m = Model(nm("industrial_light_exit", lit), "decor", cullface=True)
    m.box("box", [3, 5, 14.5], [13, 11, 16], "white")

    def face(s):
        s.fill(mat="p_water", lvl=4.0 if lit else 2.5)
        running_man(s, 1, 0, "white", 5.0 if lit else 3.5)
    m.box(glow("face", lit), [3.5, 5.5, 14.4], [12.5, 10.5, 14.5], "p_water", paint={**only("north"), "north": face})
    return m.done()


# ----------------------------------------------------------------------
# светильник подвесного потолка 4×18: рамка «армстронг», призматический рассеиватель
# ----------------------------------------------------------------------
def ceiling_panel(lit):
    m = Model(nm("ceiling_light_panel", lit), "decor", cullface=True)

    def rim(s):
        s.frame(0, 0, s.w, s.h, 0.5, -0.5)
    m.box("frame", [0, 15, 0], [16, 16, 16], "white")
    mat, lvl = lens(lit)

    def diffuser(s):
        for i in range(0, 14, 2):
            s.vline(i, add=0.5)
            s.hline(i, add=0.5)
        for x in (3, 6, 9, 12):  # лампы за рассеивателем
            s.vline(x, add=0.8 if lit else 0.3)
    m.box(glow("diffuser", lit), [1, 14.6, 1], [15, 15, 15], mat, lvl=lvl, paint={**only("down"), "down": diffuser})
    for i, (f, t) in enumerate((([0, 14.5, 0], [16, 15, 1]), ([0, 14.5, 15], [16, 15, 16]),
                                ([0, 14.5, 1], [1, 15, 15]), ([15, 14.5, 1], [16, 15, 15]))):
        m.box(f"rim{i}", f, t, "white", paint={"down": rim})
    return m.done()


# ----------------------------------------------------------------------
# люминесцентный ЛСП 2×36: эмалированный корпус под потолком, две лампы-трубки, патроны по торцам
# ----------------------------------------------------------------------
def fluorescent(lit):
    m = Model(nm("lamp_fluorescent", lit), "decor", cullface=True)
    m.box("body", [0, 14.5, 5], [16, 16, 11], "white", paint={"down": lambda s: s.hline(2, add=-0.6).hline(3, add=-0.6)})
    for i, x in enumerate((0.5, 14.5)):
        m.box(f"holder{i}", [x, 13.3, 5.5], [x + 1, 14.5, 10.5], "white", share="holder", lvl=2.5)
    mat, lvl = lens(lit)
    for i, z in enumerate((6.9, 9.1)):
        m.cyl(glow(f"tube{i}", lit), "x", (13.95, z), 0.7, 1.5, 14.5, mat, n=8, lvl=lvl, caps=(False, False), share="tube")
    return m.done()


# ----------------------------------------------------------------------
# взрывозащищённый ВЗГ: кронштейн от стены, литой корпус, колба в защитной сетке
# ----------------------------------------------------------------------
def explosion_proof(lit):
    m = Model(nm("lamp_explosion_proof", lit), "decor", cullface=True)
    m.box("wall_plate", [6, 10, 15], [10, 14, 16], "mgreen", paint={"north": lambda s: (s.rivet(0, 0), s.rivet(3, 0), s.rivet(0, 3), s.rivet(3, 3))})
    m.cyl("arm", "z", (12.5, 8), 0.6, 9.5, 15, "mgreen", n=8)
    h = m.cyl("housing", "y", (8, 9.5), 1.4, 10, 13.5, "mgreen", caps=(True, True))
    h.wrap.hline(1, add=-1.0)
    bolt_ring(h.cap1, h.cap1.w / 2, h.cap1.h / 2, h.r - 0.6, 6)
    mat, lvl = lens(lit, "lamp_y")
    m.cyl(glow("flask", lit), "y", (8, 9.5), 1.0, 4.5, 10, mat, n=8, lvl=lvl, caps=(True, False))
    for i, (dx, dz) in enumerate(((-3, 0), (3, 0), (0, -3), (0, 3))):
        m.box(f"cage{i}", [8 + dx - 0.3, 3.5, 9.5 + dz - 0.3], [8 + dx + 0.3, 10, 9.5 + dz + 0.3], "mgreen", share="cage")
    m.ring("cage_ring", "y", (8, 9.5), 3.3, 0.6, 6.5, 7, "mgreen", n=8)
    m.cyl("cage_foot", "y", (8, 9.5), 1.0, 2.5, 3.5, "mgreen", n=8)
    return m.done()


# ----------------------------------------------------------------------
# аварийный светильник: белый короб на стене, полоса с пиктограммой, рассеиватель снизу и спереди
# ----------------------------------------------------------------------
def emergency(lit):
    m = Model(nm("lamp_emergency", lit), "decor", cullface=True)

    def front(s):
        s.frame()
        s.fill(1, 1, 5, s.h - 1, mat="p_water", lvl=4.0)
        s.px(2, 1, mat="white", lvl=5).px(3, 2, mat="white", lvl=5).px(2, 3, mat="white", lvl=5)
        s.px(8, 1, mat="lamp_g" if lit else "black", lvl=4.5 if lit else 2.0)
    m.box("body", [3, 7, 14], [13, 10, 16], "white", paint={"north": front})
    mat, lvl = lens(lit)
    m.box(glow("diffuser", lit), [3.5, 6, 14.2], [12.5, 7, 15.8], mat, lvl=lvl, paint=only("down", "north", "east", "west"))
    return m.done()


# ----------------------------------------------------------------------
# «колокол» РСП на цепи: крюк, цепь, корпус ПРА, алюминиевый отражатель, защитное стекло
# ----------------------------------------------------------------------
def high_bay(lit):
    m = Model(nm("lamp_high_bay", lit), "decor", cullface=True)
    m.box("hook_plate", [6.5, 15.5, 6.5], [9.5, 16, 9.5], "cast")
    for i, y in enumerate((13.5, 11.5)):
        m.box(f"link_a{i}", [7.6, y, 7.2], [8.4, y + 2, 8.8], "steel", share="link_a")
    for i, y in enumerate((14.5, 12.5)):
        m.box(f"link_b{i}", [7.2, y, 7.6], [8.8, y + 2, 8.4], "steel", share="link_b")
    bal = m.cyl("ballast", "y", (8, 8), 1.6, 8, 11.5, "pgrey", caps=(False, True))
    bal.wrap.louvres(0, 1, bal.wrap.w, 3, 2)
    for i, (fw, y0, y1) in enumerate(((1.4, 7, 8), (1.9, 5.5, 7), (2.4, 3.5, 5.5), (2.8, 1, 3.5))):
        c = m.cyl(f"bell{i}", "y", (8, 8), fw, y0, y1, "zinc", caps=(False, True))
        c.wrap.hline(c.wrap.h - 1, add=-0.6)
    m.ring("lip", "y", (8, 8), 7.3, 0.6, 0.6, 1.2, "zinc")
    mat, lvl = lens(lit, "lamp_y")
    lz = m.cyl(glow("glass", lit), "y", (8, 8), 2.6, 0.8, 1, mat, lvl=lvl, caps=(True, False))
    lz.cap0.disc(lz.cap0.w / 2, lz.cap0.h / 2, 2.5, add=0.8)
    return m.done()


# ----------------------------------------------------------------------
# проблесковый маячок: чёрное основание, оранжевый колпак, внутри — отражатель
# ----------------------------------------------------------------------
def beacon(lit):
    m = Model(nm("warning_beacon", lit), "decor", cullface=True)
    b = m.cyl("base", "y", (8, 8), 1.2, 0, 1.5, "black", caps=(False, True))
    b.wrap.hline(0, add=0.6)
    for i, (fw, y0, y1) in enumerate(((1.0, 1.5, 5), (0.7, 5, 6.2))):
        c = m.cyl(glow(f"dome{i}", lit), "y", (8, 8), fw, y0, y1, "amber", n=8, lvl=4.5 if lit else 2.5, caps=(False, True))
        for k in range(0, 8, 2):
            c.wrap.fill(k * fw, 0, k * fw + 1, None, add=0.6)
    return m.done()


for _lit in (True, False):
    lighting_board(_lit)
    industrial_light(_lit)
    street(_lit)
    floodlight(_lit)
    exit_sign(_lit)
    ceiling_panel(_lit)
    fluorescent(_lit)
    # bulkhead: нарисован вручную в Blockbench (art/blockbench/decor/plant_fittings.author.js)
    explosion_proof(_lit)
    emergency(_lit)
    high_bay(_lit)
    beacon(_lit)
