"""Common materials and reactor-island textures (first batch)."""
from pclib import *


# ---------- common materials ----------
def steel_plate():  # окрашенная сталь, серо-зелёная «атомная» эмаль
    a = canvas("#8c9a8e", noise=5)
    frame(a)
    for y, x in ((2, 2), (2, 12), (12, 2), (12, 12)):
        rivet(a, y, x)
    return a


def steel_smooth():  # та же эмаль без рамки — для тел вращения
    return canvas("#8c9a8e", noise=4)


def steel_dark():
    a = canvas("#4b5157", noise=6)
    frame(a, 1.25, 0.7)
    return a


def steel_bare():  # нержавейка, шлифовка
    a = canvas("#b4b8bb", noise=3)
    for y in range(16):
        k = 1.0 + rng.uniform(-0.07, 0.07)
        for x in range(16):
            shade(a, y, x, k)
    return a


def brass():
    a = canvas("#d3a246", noise=6)
    frame(a, 1.2, 0.75)
    for i in range(3, 13):
        shade(a, 3, i, 1.15)
    return a


def graphite():
    a = canvas("#2e2f33", noise=4)
    for _ in range(6):
        shade(a, rng.randrange(16), rng.randrange(16), 1.25)
    return a


def graphite_block():  # кладка: четыре графитовых блока на грань
    a = graphite()
    for i in range(16):
        shade(a, 7, i, 0.55)
        shade(a, i, 7, 0.55)
        shade(a, 8, i, 1.25)
        shade(a, i, 8, 1.25)
    return a


def zirconium():
    a = canvas("#c9c4b6", noise=4)
    for y in range(16):
        shade(a, y, 4, 1.15)
        shade(a, y, 11, 0.85)
    return a


def concrete():
    a = canvas("#9d9b95", noise=10)
    for _ in range(10):
        shade(a, rng.randrange(16), rng.randrange(16), 0.65)
    for i in range(16):  # шов опалубки
        shade(a, 10, i, 0.85)
    return a


def lead():
    a = canvas("#5c6272", noise=5)
    frame(a, 1.15, 0.8)
    return a


def hazard():
    a = np.zeros((16, 16, 4))
    for y in range(16):
        for x in range(16):
            a[y, x] = hexc("#e8b923") if ((x + y) // 4) % 2 == 0 else hexc("#1d1d1f")
    frame(a, 1.1, 0.8)
    return a


def rubber():
    return canvas("#232325", noise=3)


def paint_red():
    a = canvas("#b2302a", noise=6)
    frame(a, 1.2, 0.75)
    return a


def motor_blue():
    a = canvas("#3e6b86", noise=5)
    for x in range(1, 16, 3):
        for y in range(16):
            shade(a, y, x, 0.7)
    return a


def lamp(base):
    a = canvas(base, noise=0)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            shade(a, y, x, max(0.75, 1.35 - d / 12))
    return a


def absorber():  # B4C в оболочке
    a = canvas("#3a3a3e", noise=4)
    for y in (0, 1, 14, 15):
        for x in range(16):
            put(a, y, x, "#a9adb0")
    return a


def cabinet():  # бежево-серая окраска щитов
    a = canvas("#c4bfaa", noise=4)
    frame(a)
    return a


# ---------- pipes (ГОСТ 14202 + предупреждающие кольца) ----------
def pipe(body, ring=None, ring2=None):
    a = canvas(body, noise=5)
    for x in range(16):  # цилиндрическое затенение поперёк трубы
        for y in range(16):
            k = 1.15 if y in (4, 5) else 0.75 if y in (13, 14, 15) else 1.0
            shade(a, y, x, k)
    if ring:
        for x in range(6, 10):
            for y in range(16):
                put(a, y, x, ring)
                shade(a, y, x, 1.1 if y in (4, 5) else 0.75 if y > 12 else 1.0)
        if ring2:
            for y in range(16):
                put(a, y, 6, ring2)
                put(a, y, 9, ring2)
    return a


def pipe_end():
    a = canvas("#4b5157", noise=4)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 4.2:
                put(a, y, x, "#141416")
            elif d < 5.5:
                shade(a, y, x, 1.3)
    return a


# ---------- block-specific ----------
def vessel_wall():  # толстостенная сталь, сварной шов, ряд шпилек
    a = canvas("#6f767a", noise=6)
    for x in range(16):
        put(a, 7, x, "#8a8f91")
        put(a, 8, x, "#55595c")
        if x % 2 == 0:
            shade(a, 7, x, 1.2)
    for x in (1, 5, 10, 14):
        rivet(a, 2, x)
        rivet(a, 12, x)
    frame(a, 1.1, 0.75)
    return a


def containment():
    a = concrete()
    for i in range(16):  # стальная облицовка-кромка сверху
        put(a, 0, i, "#7d847f")
    return a


def controller_front():
    a = canvas("#3b4046", noise=3)
    frame(a, 1.25, 0.65)
    rect(a, 2, 2, 13, 8, "#0f2a17")  # экран ЭЛТ
    for y in (3, 5, 7):
        for x in range(3, 12):
            if rng.random() < 0.7:
                put(a, y, x, "#47d16a")
    rect(a, 2, 10, 13, 13, "#2a2e33")
    for i, c in enumerate(("#d33b2c", "#e8b923", "#43b04a", "#e8e8e0")):
        put(a, 11, 3 + i * 3, c)
        put(a, 11, 4 + i * 3, c)
    return a


def cabinet_door():
    a = cabinet()
    rect(a, 4, 9, 11, 13, None, 0.8)
    for y in (10, 12):  # жалюзи
        for x in range(4, 12):
            put(a, y, x, "#3a3a36")
    rect(a, 4, 6, 11, 7, "#e6e2d2")  # табличка
    for x in range(5, 11, 2):
        put(a, 6, x, "#333333")
    return a


def dial32():  # подложка шкалы без делений (деления — клиентская текстура, R10)
    n = 32
    a = np.zeros((n, n, 4))
    a[:, :] = hexc("#2b2f33")
    for y in range(n):
        for x in range(n):
            d = ((x - 15.5) ** 2 + (y - 15.5) ** 2) ** 0.5
            if d < 13:
                a[y, x] = hexc("#ece8dc")
                if d > 11.5:
                    a[y, x, :3] *= 0.85
            elif d < 15:
                a[y, x] = hexc("#9ea3a6")
                a[y, x, :3] *= 1.15 if y < 16 else 0.8
    for y in range(14, 18):  # центральная шпилька стрелки
        for x in range(14, 18):
            a[y, x] = hexc("#1b1b1b")
    return a


def fuel_rods():  # пучок твэлов + дистанционирующие решётки
    a = np.zeros((16, 16, 4))
    for y in range(16):
        for x in range(16):
            a[y, x] = hexc("#c9c4b6") if x % 2 == 0 else hexc("#8d897e")
    for y in (3, 8, 13):
        for x in range(16):
            put(a, y, x, "#7a7f84")
    return a


def trefoil():  # знак радиационной опасности
    n = 16
    a = np.zeros((n, n, 4))
    a[:, :] = hexc("#e8b923")
    import math
    for y in range(n):
        for x in range(n):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            ang = (math.degrees(math.atan2(dy, dx)) + 360 + 90) % 120
            if d < 1.6 or (2.6 < d < 6.5 and ang < 60):
                a[y, x] = hexc("#1d1d1f")
    frame(a, 1.0, 0.75)
    return a



TEXTURES.update({
    "block/common/steel_plate.png": steel_plate,
    "block/common/steel_dark.png": steel_dark,
    "block/common/steel_smooth.png": steel_smooth,
    "block/common/steel_dark_smooth.png": lambda: canvas("#4b5157", noise=5),
    "block/common/brass_smooth.png": lambda: canvas("#d3a246", noise=6),
    "block/common/steel_bare.png": steel_bare,
    "block/common/brass.png": brass,
    "block/common/graphite.png": graphite,
    "block/common/zirconium.png": zirconium,
    "block/common/concrete.png": concrete,
    "block/common/lead.png": lead,
    "block/common/hazard.png": hazard,
    "block/common/rubber.png": rubber,
    "block/common/paint_red.png": paint_red,
    "block/common/motor_blue.png": motor_blue,
    "block/common/lamp_red.png": lambda: lamp("#ff4a36"),
    "block/common/lamp_yellow.png": lambda: lamp("#ffd23a"),
    "block/common/lamp_green.png": lambda: lamp("#52e06a"),
    "block/common/absorber.png": absorber,
    "block/common/cabinet.png": cabinet,
    "block/common/trefoil.png": trefoil,
    "block/common/pipe_end.png": pipe_end,
    "block/pipe_primary.png": lambda: pipe("#3f7a4a", "#e8b923", "#1d1d1f"),
    "block/pipe_steam.png": lambda: pipe("#9c2f27"),
    "block/pipe_feedwater.png": lambda: pipe("#3f7a4a"),
    "block/pipe_chemical.png": lambda: pipe("#c8692a", "#e8b923"),
    "block/reactor_vessel_wall.png": vessel_wall,
    "block/containment_wall.png": containment,
    "block/moderator_block_graphite.png": graphite_block,
    "block/reactor_controller_front.png": controller_front,
    "block/protection_cabinet_door.png": cabinet_door,
    "block/instrument_dial.png": dial32,
    "item/fuel_assembly_rods.png": fuel_rods,
})
