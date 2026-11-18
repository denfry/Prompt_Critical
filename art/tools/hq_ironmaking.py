"""HQ: доменный цех (GDD 08 §8.5) — воздуходувка, кожух, фурма.

Фасад моделей — на север. Размеры в пикселях модели, 16 = блок.
"""
import math

from hqkit import Model


def bolt_ring(s, cx, cy, r, n, phase=0.0):
    """Болты по окружности на торце."""
    for i in range(n):
        a = phase + 2 * math.pi * i / n
        s.rivet(int(cx + r * math.sin(a)), int(cy - r * math.cos(a)))


def round_shade(wrap, fw, n=16):
    """Объём горизонтального цилиндра: блик на верхних гранях, тень на нижних (грань 0 — верхняя)."""
    for k, d in ((n - 1, 0.5), (0, 0.5), (1, 0.5), (n // 2 - 1, -0.5), (n // 2, -1.0), (n // 2 + 1, -0.5)):
        wrap.fill(k * fw, 0, (k + 1) * fw, None, add=d)


def gauge(m, name, cx, cy, z, needle=(1, -1)):
    """Манометр ⌀5 на северной плоскости z: чёрный корпус, белая шкала, красная стрелка."""
    g = m.cyl(name, "z", (cx, cy), 2, z - 1, z, "black", n=8, crisp=(True, False))
    s = g.cap0
    s.disc(3.5, 3.5, 2.6, mat="white", lvl=5.0)
    s.px(3, 3, mat="black", lvl=2.0)
    s.px(3 + needle[0], 3 + needle[1], mat="red", lvl=4.0)
    s.px(1, 3, add=-1.0), s.px(5, 3, add=-1.0), s.px(3, 1, add=-1.0)
    return g


# ----------------------------------------------------------------------
# турбовоздуходувка К-3250, 2×2×2: фундамент с плитой, мультипликатор на входе вала Create (ось 8, 8),
# муфта, корпус сжатия с горизонтальным разъёмом на лапах, всас с торца, нагнетание вверх.
# Ротор — partial: вал с муфтой, ось z через (16, 16).
# ----------------------------------------------------------------------
def blast_blower():
    m = Model("blast_blower", "ironmaking")
    # фундамент и плита с анкерными болтами
    m.box("foundation", [0, 0, 0], [32, 2, 32], "concrete")

    def plate_top(s):
        for x, y in ((1, 1), (26, 1), (1, 26), (26, 26), (13, 1), (13, 26)):
            s.rivet(x, y)
    m.box("bedplate", [2, 2, 2], [30, 3, 30], "cast", paint={"up": plate_top})

    # мультипликатор: вал Create входит в нижнюю бобышку (8, 8), выход — на оси ротора (16, 16)
    def gear_front(s):
        s.frame()
        s.seam_h(9, 1, 17)
        s.rivets_h(7, 2, 17, 3)
        s.fill(2, 14, 4, 16, mat="glass", lvl=4.0)
        s.px(2, 14, mat="glass", lvl=5.5)

    def gear_top(s):
        s.frame()
        s.fill(5, 1, 13, 4, add=0.5)
        s.frame(5, 1, 13, 4)
        s.rivet(6, 2), s.rivet(11, 2)

    def gear_side(s):
        s.frame()
        s.seam_h(9, 1, 4)
    m.box("gearbox", [3, 3, 2], [21, 21, 7], "mblue",
          paint={"north": gear_front, "up": gear_top, "east": gear_side, "west": gear_side, "south": lambda s: s.frame()})
    for nm, cx, cy in (("boss_in", 8, 8), ("boss_out", 16, 16)):
        b = m.cyl(nm, "z", (cx, cy), 3, 1, 2, "mblue", n=8, lvl=3.5, crisp=(True, False))
        b.cap0.disc(4, 4, 3.7, ring=1.0, add=0.7)
        b.cap0.disc(4, 4, 1.9, mat="cast", lvl=3.0)
        b.cap0.disc(4, 4, 1.0, add=1.0)
        bolt_ring(b.cap0, 4, 4, 2.8, 4, math.pi / 4)
    m.cyl("boss_rear", "z", (8, 8), 3, 7, 8.5, "mblue", n=8)
    m.box("breather", [11, 21, 4], [13, 22, 6], "steel")

    # корпус сжатия: бочка между двумя фланцами, ребро жёсткости посередине
    brg = m.cyl("bearing", "z", (16, 16), 2, 11, 13, "cast", crisp=(True, False))
    round_shade(brg.wrap, 2)
    brg.cap0.disc(6, 6, 4.6, ring=1.0, add=0.7)
    brg.cap0.disc(6, 6, 1.6, mat="black", lvl=1.5)
    bolt_ring(brg.cap0, 6, 6, 3.4, 6)
    bar = m.cyl("barrel", "z", (16, 16), 4, 15, 24, "mblue")
    round_shade(bar.wrap, 4)
    fn = m.cyl("flange_n", "z", (16, 16), 4.5, 13, 15, "mblue", lvl=3.5, crisp=(True, False))
    fs = m.cyl("flange_s", "z", (16, 16), 4.5, 24, 26, "mblue", lvl=3.5, crisp=(False, True))
    for f in (fn, fs):
        round_shade(f.wrap, 4.5)
        for k in range(16):
            f.wrap.rivet(int(k * 4.5 + 2), 0)
    for cap in (fn.cap0, fs.cap1):
        cap.disc(12, 12, 11.0, ring=1.0, add=0.5)
        bolt_ring(cap, 12, 12, 9.4, 16, math.pi / 16)
    m.cyl("rib", "z", (16, 16), 4.5, 19, 20, "mblue", lvl=3.5, share="rib")

    # горизонтальный разъём на болтах и литые лапы на подушках
    def split_top(s):
        s.rivets_v(1, 1, 8, 2)
    for nm, x0 in (("w", 4), ("e", 25)):
        m.box(f"split_{nm}", [x0, 15, 15], [x0 + 3, 17, 24], "mblue", paint={"up": split_top}, lvl=3.5)
        m.box(f"pad_{nm}", [x0 - 1, 3, 14], [x0 + 4, 4, 25], "cast", share="pad")
        for j, z in enumerate((15, 21)):
            m.box(f"foot_{nm}{j}", [x0, 4, z], [x0 + 3, 15, z + 3], "cast", share="foot")

    # нагнетательный патрубок вверх, шильдик завода
    def plate(s):
        s.fill(1, 1, 7, 4, mat="brass", lvl=4.0)
        s.frame(1, 1, 7, 4, 0.8, -0.8)
        s.hline(2, 2, 6, add=-1.5)
    m.box("nozzle", [12, 25, 16], [20, 30, 23], "mblue", paint={"north": plate})

    def flange_top(s):
        s.fill(2, 2, 8, 7, mat="black", lvl=1.0)
        s.frame(2, 2, 8, 7, -1.0, 0.5)
        for x, y in ((0, 0), (9, 0), (0, 8), (9, 8), (4, 0), (5, 8), (0, 4), (9, 4)):
            s.px(x, y, add=1.2)

    def flange_side(s):
        s.rivets_h(0, 1, s.w, 2)
    m.box("nozzle_flange", [11, 30, 15], [21, 32, 24], "steel",
          paint={"up": flange_top, "north": flange_side, "south": flange_side, "east": flange_side, "west": flange_side})

    # всас с торца: конфузор и фланец с защитной решёткой
    suc = m.cyl("suction", "z", (16, 16), 3, 26, 30, "mblue")
    round_shade(suc.wrap, 3)
    fl = m.cyl("inlet_flange", "z", (16, 16), 3.5, 30, 32, "steel", crisp=(False, True))
    round_shade(fl.wrap, 3.5)
    g = fl.cap1
    g.disc(9, 9, 6.6, mat="black", lvl=1.0)
    for x in (3, 5, 7, 10, 12, 14):
        g.vline(x, 2, 16, mat="zinc", lvl=2.5)
    g.disc(9, 9, 20, ring=13.4, mat="steel", lvl=3.5)
    g.disc(9, 9, 6.6, ring=1.0, add=-1.5)
    bolt_ring(g, 9, 9, 7.8, 12, math.pi / 12)

    # маслобак с указателем уровня и маслопровод к корпусу
    def tank_face(s):
        s.frame()
        s.fill(2, 1, 3, 4, mat="glass", lvl=4.5)
    m.box("oil_tank", [2, 3, 26], [8, 8, 31], "mgreen",
          paint={"west": tank_face, "south": lambda s: s.frame(), "up": lambda s: s.frame()})
    m.box("oil_cap", [4, 8, 28], [6, 9, 30], "brass")
    m.box("oil_riser", [4.5, 9, 28.5], [5.5, 16.5, 29.5], "copper")
    m.box("oil_run", [5.5, 15.5, 28.5], [9, 16.5, 29.5], "copper")

    # щиток с манометрами дутья и масла
    m.box("gauge_post", [27, 3, 4], [28, 8, 5], "steel")
    m.box("gauge_board", [24, 8, 4], [31, 21, 5], "mgreen", paint={"north": lambda s: s.frame()})
    gauge(m, "gauge_a", 27.5, 17.5, 4, (1, -1))
    gauge(m, "gauge_b", 27.5, 11.5, 4, (-1, -1))
    return m.done()


def blast_blower_rotor():
    m = Model("blast_blower/rotor", "ironmaking", kind="partial")
    m.cyl("shaft", "z", (16, 16), 1, 7, 11, "steel", n=8)
    c = m.cyl("coupling", "z", (16, 16), 3, 8, 10, "yellow", n=8)
    for k in range(0, 8, 2):
        c.wrap.fill(k * 3, 0, k * 3 + 3, None, mat="black", lvl=2.0)
    bolt_ring(c.cap0, 4, 4, 2.4, 4)
    bolt_ring(c.cap1, 4, 4, 2.4, 4)
    return m.done()


# ----------------------------------------------------------------------
# кожух домны: блок кольца свободной сборки. Клёпаные листы в два пояса вперевязку, на каждой
# стороне плитовый холодильник с подводом и отводом воды. Заплечики — горячая зона: холодильники
# чаще, поверх идёт кольцевой водовод с опусками.
# ----------------------------------------------------------------------
SIDES = ("north", "east", "south", "west")


def side_box(m, name, side, f, t, mat, **kw):
    """Деталь, заданная у северной стороны блока, на любой из четырёх сторон."""
    tr = {"north": lambda x, y, z: (x, y, z), "east": lambda x, y, z: (16 - z, y, x),
          "south": lambda x, y, z: (16 - x, y, 16 - z), "west": lambda x, y, z: (z, y, 16 - x)}[side]
    a, b = tr(*f), tr(*t)
    return m.box(f"{name}_{side[0]}", [min(p, q) for p, q in zip(a, b)], [max(p, q) for p, q in zip(a, b)], mat, **kw)


def plating(s, hot=False):
    """Сторона кожуха 16×16: верхний пояс со стыком слева, нижний — со стыком посередине."""
    s.fill(0, 9, 16, 16, add=-0.3)
    s.hline(0, add=-1.5), s.hline(8, add=-1.5)
    s.vline(0, 1, 8, add=-1.5), s.vline(1, 1, 8, add=0.4)
    s.vline(8, 9, 16, add=-1.5), s.vline(9, 9, 16, add=0.4)
    for x in (3, 7, 11, 15):
        s.rivet(x, 6)
    for x in (1, 5, 11, 15):
        s.rivet(x, 14)
    s.streaks(77 if hot else 31, 5 if hot else 2, length=(2, 5), y0=9)
    if hot:
        s.streaks(78, 3, length=(2, 4), y0=1)
        s.fill(0, 12, 16, 16, add=-0.4)
    return s


def plate_top(s):
    s.hline(0, add=-1.0), s.vline(0, add=-1.0)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        s.rivet(x, y)


def cooler(m, name, side, x0, y0, w=6, h=4):
    """Плитовый холодильник: литая крышка на болтах и два штуцера воды."""
    def face(s):
        s.frame()
        for x, y in ((0, 0), (s.w - 1, 0), (0, s.h - 1), (s.w - 1, s.h - 1)):
            s.px(x, y, add=0.8)
    side_box(m, name, side, [x0, y0, -1], [x0 + w, y0 + h, 0], "cast", paint={side: face}, share=f"cooler{w}{h}")
    y = y0 + h / 2 - 0.5
    for i, x in enumerate((x0 + 1, x0 + w - 2)):
        side_box(m, f"{name}_stub{i}", side, [x, y, -2], [x + 1, y + 1, -1], "p_water", share="stub", lvl=3.0)


def shell_cube(m, hot=False, north=None):
    paint = {d: (lambda s: plating(s, hot)) for d in SIDES}
    paint.update(up=plate_top, down=plate_top)
    if north:
        paint["north"] = north
    m.box("shell", [0, 0, 0], [16, 16, 16], "shell", paint=paint, bevel=False)


def blast_furnace_shell(bosh=False):
    m = Model("blast_furnace_shell_bosh" if bosh else "blast_furnace_shell", "ironmaking", cullface=True)
    shell_cube(m, bosh)
    for side in SIDES:
        if not bosh:
            cooler(m, "cooler", side, 5, 2)
            continue
        cooler(m, "cooler_a", side, 1, 2, 5, 3)
        cooler(m, "cooler_b", side, 10, 2, 5, 3)

        # кольцевой водовод: рисунок с периодом в блок, концы заходят за угол кольца
        def main_face(s):
            s.e[:] = True
            s.hline(0, lvl=3.5), s.hline(1, lvl=2.0)
            for x in range(1, s.w, 8):
                s.vline(x, mat="steel", lvl=3.0)

        def main_flat(lv):
            def f(s):
                s.e[:] = True
                s.fill(lvl=lv)
            return f
        side_box(m, "water_main", side, [-2, 9, -2], [18, 11, 0], "p_water",
                 paint={side: main_face, "up": main_flat(3.5), "down": main_flat(1.5)}, bevel=False)
        for i, x in enumerate((3, 12)):
            side_box(m, f"drop{i}", side, [x, 5, -1], [x + 1, 9, 0], "p_water", share="drop", lvl=3.5)
    return m.done()


# ----------------------------------------------------------------------
# фурменный прибор: участок кожуха с амбразурой, медная фурма в холодильнике, сопло, колено с
# глазком и подвод от кольцевого воздухопровода сверху. Фасад (север) — наружу печи.
# ----------------------------------------------------------------------
def blast_furnace_tuyere(burnt=False):
    m = Model("blast_furnace_tuyere_burnt" if burnt else "blast_furnace_tuyere", "ironmaking", cullface=True)

    def wall(s):
        plating(s)
        if burnt:  # прогар: копоть и окалина вокруг амбразуры
            for r, d in ((7.5, -0.5), (6.2, -1.0)):
                s.disc(8, 9, r, add=d)
            s.streaks(5, 4, mat="black", length=(3, 6), x0=4, x1=12, y0=2)
    shell_cube(m, north=wall)
    emb = m.cyl("embrasure", "z", (8, 7), 2, -1, 0, "cast", crisp=(True, False))
    emb.cap0.disc(6, 6, 5.0, ring=1.0, add=0.7)
    bolt_ring(emb.cap0, 6, 6, 4.3, 8, math.pi / 8)
    col = m.cyl("tuyere", "z", (8, 7), 3, -2, -1, "black" if burnt else "copper", n=8, crisp=(True, False),
                lvl=2.5 if burnt else 3.5)
    col.cap0.disc(4, 4, 3.7, ring=1.0, add=0.7)
    pipe = m.cyl("blowpipe", "z", (8, 7), 2, -7, -2, "shell", n=8)
    round_shade(pipe.wrap, 2, 8)
    m.cyl("clamp", "z", (8, 7), 2.5, -5, -4, "steel", n=8)
    # колено с глазком и опуск от кольцевого воздухопровода
    m.box("elbow", [5.5, 4.5, -10], [10.5, 10, -6], "cast", paint={d: (lambda s: s.frame()) for d in ("north", "east", "west")})
    m.cyl("downleg", "y", (8, -8), 2, 10, 16, "shell", n=8, caps=(False, True))
    m.cyl("downleg_flange", "y", (8, -8), 2.5, 15, 16, "cast", n=8, lvl=3.5)
    peep = m.cyl("peep", "z", (8, 7), 1.5, -11, -10, "brass", n=8, crisp=(True, False))
    peep.cap0.disc(2.5, 2.5, 1.2, mat="black", lvl=1.5)
    if burnt:
        m.box("glow_leak", [4, 3.5, -1.03], [6, 4.5, -1.0], "hot", lvl=4.5, bevel=False,
              paint={d: False for d in ("south", "east", "west", "up", "down")})
    else:
        m.box("glow_peep", [7.5, 6.5, -11.13], [8.5, 7.5, -11.1], "hot", lvl=5.0, bevel=False,
              paint={d: False for d in ("south", "east", "west", "up", "down")})
    # подвод и отвод воды к фурме
    for i, x in enumerate((1, 13)):
        m.box(f"water_{i}", [x, 6.5, -1], [x + 2, 7.5, 0], "p_water", lvl=3.5)
        m.box(f"water_riser_{i}", [x + (0 if i == 0 else 1), 7.5, -1], [x + (1 if i == 0 else 2), 16, 0], "p_water", lvl=3.5)
    return m.done()


blast_blower()
blast_blower_rotor()
blast_furnace_shell()
blast_furnace_shell(True)
blast_furnace_tuyere()
blast_furnace_tuyere(True)
