"""HQ: декор завода, волна «электрооборудование и интерьер цеха».

Габарит каждой модели совпадает с формой блока в PcBlocks (DecorBlock, фасад на север; настенные прижаты к югу).
"""
import math

from hqkit import Model
from hq_ironmaking import bolt_ring


def only(*keep):
    return {d: False for d in ("north", "south", "east", "west", "up", "down") if d not in keep}


def frame(s):
    s.frame()


def lightning(s, x, y, mat="black", lvl=1.0):
    """Стрела-молния 3×5 (знак W08)."""
    for px, py in ((2, 0), (1, 1), (0, 2), (1, 2), (2, 2), (1, 3), (0, 4)):
        s.px(x + px, y + py, mat=mat, lvl=lvl)


def warn_triangle(s, cx, y0, h=4):
    """Жёлтый треугольник с чёрной каймой и молнией (ГОСТ 12.4.026, W08)."""
    for i in range(h):
        s.hline(y0 + i, cx - i, cx + i + 1, mat="yellow" if i < h - 1 else "black", lvl=4.5 if i < h - 1 else 1.0)
        s.px(cx - i, y0 + i, mat="black", lvl=1.0).px(cx + i, y0 + i, mat="black", lvl=1.0)
    for k in range(2, h - 1):
        s.px(cx, y0 + k, mat="black", lvl=1.0)


# ======================================================================
# электрооборудование
# ======================================================================
def junction_box():
    m = Model("junction_box", "decor", cullface=True)

    def lid(s):
        s.frame()
        for x, y in ((0, 0), (s.w - 1, 0), (0, s.h - 1), (s.w - 1, s.h - 1)):
            s.px(x, y, mat="steel", lvl=4.0)
        warn_triangle(s, 3, 1, 5)
    m.box("box", [4, 4, 14], [12, 12, 16], "pgrey", paint={"north": lid})
    for i, (f, t) in enumerate((([7, 12, 14.5], [9, 13, 15.5]), ([7, 3, 14.5], [9, 4, 15.5]),
                                ([3, 7, 14.5], [4, 9, 15.5]), ([12, 7, 14.5], [13, 9, 15.5]))):
        m.box(f"gland{i}", f, t, "black", share="gland" if i < 2 else "gland_s")
    return m.done()


def wall_socket():
    """Силовая розетка 380 В с откидной крышкой и вилкой-заглушкой (красная) рядом с бытовой 220 В (синяя)."""
    m = Model("wall_socket", "decor", cullface=True)
    m.box("plate", [3, 3, 15], [13, 13, 16], "pgrey", paint={"north": lambda s: (s.frame(), [s.rivet(x, y) for x, y in ((1, 1), (8, 1), (1, 8), (8, 8))])})
    for i, (x, mat) in enumerate(((5.5, "red"), (10.5, "p_air"))):
        b = m.cyl(f"socket{i}", "z", (x, 8), 1.0, 13, 15, mat, n=8, crisp=(True, False))
        b.cap0.disc(b.cap0.w / 2, b.cap0.h / 2, 1.0, mat="black", lvl=1.5)
        m.box(f"lid{i}", [x - 1.8, 10.3, 12.4], [x + 1.8, 10.9, 15], mat, share="lid" if i == 0 else "lid_b")
    m.box("label", [4, 4, 14.9], [12, 5.5, 15], "white", paint=only("north"))
    return m.done()


def knife_switch():
    """Рубильник на изолирующей панели: три ножа, губки, рукоятка, щиток-ограждение."""
    m = Model("knife_switch", "decor", cullface=True)
    m.box("panel", [3, 2, 15], [13, 14, 16], "black", paint={"north": lambda s: [s.rivet(x, y) for x, y in ((1, 1), (8, 1), (1, 10), (8, 10))]})
    for i, x in enumerate((5, 8, 11)):
        m.box(f"jaw{i}", [x - 0.6, 11, 14], [x + 0.6, 12.5, 15], "copper", share="jaw")
        m.box(f"hinge{i}", [x - 0.6, 3.5, 14], [x + 0.6, 5, 15], "copper", share="jaw")
        m.box(f"blade{i}", [x - 0.3, 4, 13.6], [x + 0.3, 12, 14], "copper", share="blade")
    m.box("bar", [4, 9.5, 12.8], [12, 10.5, 13.6], "black")
    m.box("handle", [7.4, 9.7, 10], [8.6, 10.3, 12.8], "red")
    m.box("guard", [3.5, 10.5, 12.5], [12.5, 13.5, 13], "pgrey", paint={"north": lambda s: warn_triangle(s, 4, 0, 3)})
    return m.done()


def voltage_sign():
    """Плакат «Стой! Напряжение»: белое поле, красная кайма, стрела-молния, надпись."""
    m = Model("voltage_warning_sign", "decor", cullface=True)

    def face(s):
        s.fill(mat="white", lvl=5.0)
        s.frame(0, 0, s.w, s.h)
        for i in range(s.w):
            s.px(i, 0, mat="red", lvl=3.5).px(i, s.h - 1, mat="red", lvl=3.5)
        for j in range(s.h):
            s.px(0, j, mat="red", lvl=3.5).px(s.w - 1, j, mat="red", lvl=3.5)
        lightning(s, 4, 1, "black", 1.0)
        s.hline(s.h - 2, 2, s.w - 2, mat="red", lvl=3.5)
    m.box("plate", [3, 4, 15.6], [13, 12, 16], "white", paint={"north": face})
    return m.done()


def cable_drum():
    """Кабельный барабан на боку: щёки из досок, намотанный кабель, ось."""
    m = Model("cable_drum", "decor")

    def cheek(s):
        for x in range(0, s.w, 3):
            s.vline(x, add=-1.0)
        s.disc(s.w / 2, s.h / 2, 2.2, mat="steel", lvl=3.0)
        s.disc(s.w / 2, s.h / 2, 1.0, mat="black", lvl=1.5)
    for i, (x0, x1) in enumerate(((1, 2.5), (13.5, 15))):
        c = m.cyl(f"cheek{i}", "x", (8, 8), 3, x0, x1, "rust", crisp=(True, True), capmat="rust")
        cheek(c.cap0)
        cheek(c.cap1)
    core = m.cyl("cable", "x", (8, 8), 2.2, 2.5, 13.5, "black", caps=(False, False))
    for x in range(0, core.wrap.w, 2):
        core.wrap.vline(x, add=0.6)
    m.box("axle", [0, 7.5, 7.5], [16, 8.5, 8.5], "steel")
    return m.done()


def dielectric_mat():
    m = Model("dielectric_mat", "decor", cullface=True)

    def top(s):
        for y in range(1, s.h, 2):
            s.hline(y, 1, s.w - 1, add=-0.8)
    m.box("mat", [0, 0, 0], [16, 0.5, 16], "black", lvl=2.5, paint={"up": top})
    return m.done()


# ======================================================================
# интерьер цеха
# ======================================================================
def workbench():
    """Слесарный верстак: столешница с обкладкой, тумба с ящиками, тиски, инструмент."""
    m = Model("workbench", "decor", cullface=True)

    def top(s):
        for y in range(0, s.h, 4):
            s.hline(y, add=-0.6)
        s.frame()
    m.box("top", [0, 13, 0], [16, 15, 16], "steel", lvl=2.5, paint={"up": top})

    def drawers(s):
        s.frame()
        for y in (3, 7):
            s.seam_h(y, 1, s.w - 1)
        for y in (1, 5, 9):
            s.hline(y, 2, 4, mat="steel", lvl=4.0)
    m.box("pedestal", [9, 0, 1], [15.5, 13, 15], "mblue", paint={"north": drawers})
    for i, x in enumerate((0.5, 8)):
        m.box(f"leg{i}", [x, 0, 1], [x + 1.5, 13, 2.5], "mblue", share="leg")
        m.box(f"leg_b{i}", [x, 0, 13.5], [x + 1.5, 13, 15], "mblue", share="leg")
    m.box("shelf", [0.5, 3, 2.5], [9, 4, 13.5], "mblue")
    # тиски
    m.box("vise_base", [2, 15, 0.5], [6, 16, 4], "mblue")
    m.box("vise_jaw_a", [2.5, 16, 0.5], [5.5, 18, 1.5], "cast", share="jaw")
    m.box("vise_jaw_b", [2.5, 16, 2.5], [5.5, 18, 3.5], "cast", share="jaw")
    m.box("vise_screw", [3.6, 16.6, -1.5], [4.4, 17.4, 0.5], "steel")
    m.box("vise_bar", [2, 16.7, -1.5], [6, 17.3, -1], "steel")
    m.box("hammer_head", [9, 15, 4], [11, 16, 5], "cast")
    m.box("hammer_shaft", [10.2, 15, 5], [10.8, 15.6, 10], "rust")
    return m.done()


def locker():
    """Шкафчик раздевалки на двоих: две дверцы с вентиляцией, номера, замочки."""
    m = Model("locker", "decor", cullface=True)

    def doors(s):
        s.frame()
        s.seam_v(6, 1, s.h - 1)
        for x0 in (2, 9):
            s.louvres(x0, 2, x0 + 3, 5, 2)
            s.fill(x0, 7, x0 + 2, 8, mat="white", lvl=4.5)
        s.px(5, 9, mat="steel", lvl=4.5).px(8, 9, mat="steel", lvl=4.5)
    m.box("body", [1, 0, 2], [15, 16, 16], "mgreen", paint={"north": doors})
    return m.done()


def shelving():
    """Стеллаж: уголковые стойки, три полки, ящики и коробки."""
    m = Model("shelving", "decor", cullface=True)
    for i, (x, z) in enumerate(((0, 1), (15, 1), (0, 14), (15, 14))):
        m.box(f"post{i}", [x, 0, z], [x + 1, 16, z + 1], "zinc", share="post")
    for i, y in enumerate((1, 6, 11)):
        m.box(f"shelf{i}", [0, y, 1], [16, y + 0.5, 15], "zinc", share="shelf", paint={"up": lambda s: s.vline(8, add=-0.8)})
    boxes = (([1.5, 1.5, 3], [6, 5, 12], "mgreen"), ([7, 1.5, 4], [14, 4, 13], "oxide"), ([2, 6.5, 2.5], [8, 9.5, 13], "concrete"),
             ([9, 6.5, 5], [14, 10, 12], "mblue"), ([1.5, 11.5, 4], [5.5, 14, 12], "rust"), ([7, 11.5, 3], [14.5, 13, 13], "pgrey"))
    for i, (f, t, mat) in enumerate(boxes):
        m.box(f"box{i}", f, t, mat, paint={"north": lambda s: (s.frame(), s.hline(1, 1, s.w - 1, mat="white", lvl=4.5))})
    return m.done()


def fire_extinguisher():
    """Огнетушитель ОП-5 на кронштейне: красный баллон, запорная головка, шланг, бирка."""
    m = Model("fire_extinguisher_wall", "decor", cullface=True)
    m.box("bracket", [6.5, 4, 15], [9.5, 11, 16], "black")
    b = m.cyl("body", "y", (8, 13), 1.6, 1.5, 10.5, "red", n=8, caps=(True, True))
    b.wrap.fill(0, 3, b.wrap.w, 5, mat="white", lvl=4.5)
    b.wrap.hline(4, 2, 5, mat="black", lvl=1.5)
    m.cyl("neck", "y", (8, 13), 0.6, 10.5, 11.5, "steel", n=8)
    m.box("head", [6.8, 11.5, 12], [9.2, 12.5, 14], "steel")
    m.box("lever", [7.6, 12.5, 11], [8.4, 13, 14], "black")
    m.box("hose", [9.3, 4, 12.5], [10.1, 12, 13.3], "black")
    m.box("tag", [5.5, 7, 11.3], [6.5, 9, 11.4], "yellow")
    return m.done()


def fire_hose_cabinet():
    """Пожарный шкаф ПК: красный короб, дверца с окном, рукав на кассете, надпись «ПК»."""
    m = Model("fire_hose_cabinet", "decor", cullface=True)

    def door(s):
        s.frame()
        s.fill(3, 3, 11, 9, mat="glass", lvl=2.5)
        s.frame(3, 3, 11, 9, -0.6, 0.6)
        s.text(4, 10, "ПК", mat="white", lvl=5.0)
        s.px(12, 7, mat="black", lvl=1.5)
    m.box("cabinet", [1, 1, 13], [15, 15, 16], "red", paint={"north": door})
    return m.done()


def oil_drum():
    """Бочка 200 л: обечайка с зигами, крышка с пробками, потёки."""
    m = Model("oil_drum", "decor", cullface=True)
    d = m.cyl("drum", "y", (8, 8), 2.4, 0, 15, "mblue", caps=(True, True))
    for y in (4, 10):
        d.wrap.seam_h(y)
    d.wrap.streaks(5, 4, mat="black", length=(2, 5), y0=0)
    d.wrap.fill(0, 6, d.wrap.w, 9, mat="white", lvl=4.0)
    for k in range(0, d.wrap.w, 6):
        d.wrap.fill(k, 6, k + 2, 9, mat="black", lvl=2.0)
    c = d.cap1
    c.disc(c.w / 2, c.h / 2, c.w / 2 - 0.5, ring=1.0, add=0.8)
    c.disc(4, 5, 1.0, mat="steel", lvl=3.5).disc(c.w - 4, c.h - 5, 0.8, mat="steel", lvl=3.5)
    for i, y in enumerate((0, 14.5)):
        m.ring(f"rim{i}", "y", (8, 8), 6.3, 0.6, y, y + 0.5, "mblue", n=16)
    return m.done()


def floor_marking(hazard=False):
    m = Model("floor_marking_hazard" if hazard else "floor_marking", "decor", cullface=True)

    def paint_top(s):
        if hazard:
            s.hazard(0, 5, s.w, 11, 2)
        else:
            s.fill(0, 6, s.w, 10, mat="yellow", lvl=4.0)
        s.grain(3, 0.4)
    m.box("paint", [0, 0, 0], [16, 0.1, 16], "concrete", paint={"up": paint_top, **{d: False for d in ("north", "south", "east", "west", "down")}})
    return m.done()


# шкаф ШР, трубы проводки, колонна, баллоны и аптечка нарисованы вручную в Blockbench:
# art/blockbench/decor/plant_fittings.author.js
for _f in (junction_box, wall_socket, knife_switch, voltage_sign, cable_drum, dielectric_mat,
           workbench, locker, shelving, fire_extinguisher, fire_hose_cabinet, oil_drum):
    _f()
floor_marking()
floor_marking(True)
