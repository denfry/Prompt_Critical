"""Fuel cycle (GDD 02 §2.5–2.9): enrichment, fuel fabrication, spent fuel, radiochemistry, waste.

Block textures, common `fuelcycle_*` materials and flat item icons for every item of section 02.
"""
import math
from pclib import *


# =====================================================================
# helpers: block faces
# =====================================================================
def _c(col):
    return hexc(col) if isinstance(col, str) else col


def panel(base="#8c9a8e", noise=5, fr=True, rivets=False, n=16):
    a = canvas(base, n=n, noise=noise)
    if fr:
        frame(a)
    if rivets:
        for y, x in ((1, 1), (1, n - 2), (n - 2, 1), (n - 2, n - 2)):
            rivet(a, y, x)
    return a


def smooth(base, noise=4):
    return canvas(base, noise=noise)


def win(a, x0, y0, x1, y1, col="#24343c", glint=True, rim="#2b2e31"):
    rect(a, x0 - 1, y0 - 1, x1 + 1, y1 + 1, rim)
    rect(a, x0, y0, x1, y1, col)
    if glint:
        for i in range(min(x1 - x0, y1 - y0) + 1):
            if x0 + i + 1 <= x1:
                shade(a, y0 + i, x0 + i + 1, 1.7)
    return a


def scr(a, x0, y0, x1, y1, fg="#47d16a", bg="#0f2a17", rows=None, rim="#2b2e31"):
    rect(a, x0 - 1, y0 - 1, x1 + 1, y1 + 1, rim)
    rect(a, x0, y0, x1, y1, bg)
    for y in (rows or range(y0 + 1, y1, 2)):
        for x in range(x0 + 1, x1):
            if rng.random() < 0.7:
                put(a, y, x, fg)
    return a


def plate(a, x0, y0, x1, y1, col="#e6e2d2", ink="#333333"):
    rect(a, x0, y0, x1, y1, col)
    for y in range(y0 + 1, y1, 2):
        for x in range(x0 + 1, x1, 2):
            put(a, y, x, ink)
    return a


def dial(a, cx, cy, face="#ece8dc", rim="#2b2e31"):
    rect(a, cx - 1, cy - 1, cx + 1, cy + 1, rim)
    put(a, cy, cx, face)
    put(a, cy - 1, cx, face)
    put(a, cy, cx + 1, face)
    return a


def lamps(a, y, xs, cols):
    for x, c in zip(xs, cols):
        put(a, y, x, c)
        shade(a, y, x, 1.1)
    return a


def grille(a, x0, y0, x1, y1, dark="#1f2225"):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (y - y0) % 2 == 0:
                put(a, y, x, dark)
            else:
                shade(a, y, x, 1.15)
    return a


def band(a, y0, y1, col, k=None):
    n = a.shape[1]
    for y in range(y0, y1 + 1):
        for x in range(n):
            put(a, y, x, col)
            if k:
                shade(a, y, x, k)
            a[y, x, :3] += rng.uniform(-4, 4)
    return a


def vband(a, x0, x1, col):
    for y in range(a.shape[0]):
        for x in range(x0, x1 + 1):
            put(a, y, x, col)
            a[y, x, :3] += rng.uniform(-4, 4)
    return a


def hz(a, x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(a, y, x, "#e8b923" if ((x + y) // 2) % 2 == 0 else "#1d1d1f")
    return a


def tref(a, cx, cy, r=3.4):
    """Small radiation mark (≈7×7) on a yellow square centred at (cx, cy)."""
    n = int(math.ceil(r))
    rect(a, max(0, cx - n), max(0, cy - n), min(15, cx + n), min(15, cy + n), "#e8b923")
    for y in range(max(0, cy - n), min(16, cy + n + 1)):
        for x in range(max(0, cx - n), min(16, cx + n + 1)):
            dx, dy = x - cx, y - cy
            d = (dx * dx + dy * dy) ** 0.5
            ang = (math.degrees(math.atan2(dy, dx)) + 360 + 90) % 120
            if d < 0.8 or (1.4 < d < r and ang < 60):
                put(a, y, x, "#1d1d1f")
    return a


def weld(a, y):
    for x in range(a.shape[1]):
        put(a, y, x, "#8b8f91" if x % 2 else "#a9adb0")
    return a


def bricks(base="#a8623c", mortar="#c9b79a", n=16):
    a = canvas(base, n=n, noise=10)
    for y in range(n):
        for x in range(n):
            row = y // 4
            if y % 4 == 3 or (x + (4 if row % 2 else 0)) % 8 == 7:
                put(a, y, x, mortar)
                a[y, x, :3] += rng.uniform(-6, 6)
    return a


def cyl_shade(a, x0=0, x1=15):
    """Horizontal light gradient for cylindrical bodies (texture wraps sides)."""
    for y in range(a.shape[0]):
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, x1 - x0)
            shade(a, y, x, 1.12 - 0.3 * abs(t - 0.35))
    return a


def bolts_row(a, y, xs):
    for x in xs:
        rivet(a, y, x)
    return a


# =====================================================================
# common fuel-cycle materials
# =====================================================================
def ss_plate():  # нержавейка листовая со швом
    a = panel("#aeb4b8", noise=4)
    weld(a, 8)
    return a


def refractory():
    a = bricks("#b56b3e", "#d8c8a8")
    return a


def insul():  # алюминиевый кожух теплоизоляции, внахлёст
    a = canvas("#c9cdd0", noise=3)
    for y in (0, 5, 10, 15):
        for x in range(16):
            shade(a, y, x, 0.78)
            if y + 1 < 16:
                shade(a, y + 1, x, 1.12)
    for y in range(16):
        shade(a, y, 15, 0.85)
    return a


def heavy_concrete():  # тяжёлый (баритовый) бетон горячей камеры
    a = canvas("#7d7b74", noise=9)
    for _ in range(14):
        shade(a, rng.randrange(16), rng.randrange(16), 0.7)
    for _ in range(6):
        shade(a, rng.randrange(16), rng.randrange(16), 1.25)
    for i in range(16):
        shade(a, 0, i, 1.15)
        shade(a, 15, i, 0.75)
    return a


def glow_hot():  # раскалённое нутро печи
    a = canvas("#ff9a2e", noise=10)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            a[y, x, :3] = a[y, x, :3] * (1.2 - d / 25)
            if rng.random() < 0.12:
                put(a, y, x, "#fff1b0")
    return a


def lead_glass():  # свинцовое стекло — жёлто-янтарное
    a = canvas("#b8913a", noise=3)
    for y in range(16):
        for x in range(16):
            a[y, x, 3] = 200
            if x - y in (3, 4, 9):
                shade(a, y, x, 1.35)
    return a


def clear_glass():
    a = np.zeros((16, 16, 4))
    a[:, :] = hexc("#cfe3ea")
    a[:, :, 3] = 90
    for y in range(16):
        for x in range(16):
            if x - y in (4, 5, 11):
                a[y, x] = hexc("#ffffff")
                a[y, x, 3] = 160
    for i in range(16):
        for y, x in ((0, i), (15, i), (i, 0), (i, 15)):
            a[y, x] = hexc("#8b9296")
    return a


def boral():  # пластины B4C/Al
    a = panel("#4f5357", noise=5)
    for x in (4, 8, 12):
        for y in range(1, 15):
            shade(a, y, x, 0.8)
    return a


def frost():  # иней на холодной ловушке
    a = canvas("#dfe9ef", noise=10)
    for _ in range(25):
        put(a, rng.randrange(16), rng.randrange(16), "#ffffff")
    return a


def grinding_wheel():
    a = canvas("#8a7b67", noise=14)
    for _ in range(30):
        shade(a, rng.randrange(16), rng.randrange(16), rng.choice((0.7, 1.3)))
    return a


def bottle_brown():  # гелиевый баллон (окраска ГОСТ — коричневая)
    a = canvas("#6b4a2e", noise=4)
    cyl_shade(a)
    return a


def enamel_blue():  # эмалированный реактор
    a = canvas("#2f5f9e", noise=4)
    cyl_shade(a)
    return a


def copper():
    a = canvas("#b8693d", noise=6)
    frame(a, 1.2, 0.75)
    return a


def bitumen():
    a = canvas("#1b1a19", noise=4)
    for _ in range(10):
        shade(a, rng.randrange(16), rng.randrange(16), 2.2)
    return a


def gas_pipe(body, ring, ring2=None):
    a = canvas(body, noise=4)
    for x in range(16):
        for y in range(16):
            shade(a, y, x, 1.15 if y in (4, 5) else 0.75 if y > 12 else 1.0)
    for x in range(6, 10):
        for y in range(16):
            put(a, y, x, ring)
            shade(a, y, x, 1.1 if y in (4, 5) else 0.75 if y > 12 else 1.0)
    if ring2:
        for y in range(16):
            put(a, y, 7, ring2)
            put(a, y, 8, ring2)
    return a


def drum_body():  # оцинкованная бочка с рёбрами — для дисков (без рамки)
    a = canvas("#9ea4a6", noise=3)
    for y in (4, 11):
        for x in range(16):
            shade(a, y, x, 0.7)
            shade(a, y - 1, x, 1.15)
    return a


def grating():
    a = canvas("#5a5f63", noise=4)
    for y in range(16):
        for x in range(16):
            if y % 3 == 1 and x % 4 != 0:
                put(a, y, x, "#1a1c1e")
    return a


# =====================================================================
# enrichment
# =====================================================================
def gas_centrifuge():  # кожух: алюминий, кольца жёсткости, жёлтая метка UF6
    a = canvas("#b9c0c6", noise=3)
    cyl_shade(a)
    for y in (0, 7, 15):
        for x in range(16):
            shade(a, y, x, 0.72)
    for y in (2, 3):
        for x in range(16):
            put(a, y, x, "#e8b923")
            shade(a, y, x, 1.0 - 0.25 * abs(x / 15 - 0.35))
    return a


def cascade_header():  # коллектор: нерж. труба, жёлтое кольцо газа + чёрная середина
    return gas_pipe("#b0b5b8", "#e8b923", "#1d1d1f")


def cascade_interstage_pipe():  # межступенчатая: стрелка направления
    a = gas_pipe("#a7adb1", "#e8b923")
    for i, x in enumerate((2, 3, 4)):
        for y in range(7 - i, 9 + i):
            put(a, y, x + 9, "#1d1d1f")
    return a


def station(label, lcol):
    def f():
        a = panel("#8c9a8e", noise=4, rivets=True)
        rect(a, 2, 2, 13, 4, lcol)  # цветная табличка назначения
        for x in range(4, 12, 2):
            put(a, 3, x, label)
        scr(a, 3, 7, 8, 10, fg="#ffd23a", bg="#2a2108")
        dial(a, 11, 8)
        dial(a, 11, 12)
        lamps(a, 13, (3, 5, 7), ("#52e06a", "#ffd23a", "#ff4a36"))
        return a
    return f


def cascade_isolation_valve():  # корпус пневмопривода
    a = panel("#e0b43a", noise=4)
    rect(a, 3, 5, 12, 9, "#1d1d1f")
    for x in range(4, 12, 2):
        put(a, 7, x, "#e8e8e0")
    rect(a, 3, 11, 12, 12, "#b2302a")
    return a


def trace_heater():  # теплоизоляция + спираль греющего кабеля
    a = insul()
    for y in range(16):
        for x in range(16):
            if (x + 2 * y) % 10 in (0, 1):
                put(a, y, x, "#d2591f")
                shade(a, y, x, 1.1 if y < 8 else 0.85)
    return a


# =====================================================================
# fuel fabrication
# =====================================================================
def defluorination_kiln():  # барабан: красно-коричневая термостойкая краска, бандаж
    a = canvas("#7d3f2a", noise=5)
    cyl_shade(a)
    for x in (3, 12):
        for y in range(16):
            put(a, y, x, "#5d6064")
    return a


def pellet_press():  # станина: зелёная эмаль, шильдик, предупреждение
    a = panel("#5f7f62", noise=5, rivets=True)
    plate(a, 4, 3, 11, 5)
    hz(a, 1, 12, 14, 14)
    return a


def sintering_furnace():  # дверца с гляделкой
    a = panel("#6c7175", noise=5)
    rect(a, 2, 2, 13, 13, "#585c60")
    frame(a, 1.1, 0.8, inset=2)
    rect(a, 6, 5, 9, 8, "#2b2e31")
    rect(a, 7, 6, 8, 7, "#ffb347")
    plate(a, 4, 10, 11, 11, "#e8b923", "#1d1d1f")
    return a


def centerless_grinder():
    a = panel("#4f7290", noise=5, rivets=True)
    plate(a, 3, 3, 12, 5)
    grille(a, 3, 8, 12, 12)
    return a


def pellet_inspection_station():  # экран профиля таблетки
    a = canvas("#14181c", noise=2)
    frame(a, 1.4, 0.6)
    for x in range(2, 14):
        put(a, 4, x, "#2f6a40")
        put(a, 11, x, "#2f6a40")
    for x in range(3, 13):
        y = 8 + int(round(1.2 * math.sin(x * 1.3)))
        put(a, y, x, "#52e06a")
    for x in range(4, 12, 3):
        rect(a, x, 6, x + 1, 9, None, 1.0)
        put(a, 6, x, "#47d16a")
    plate(a, 2, 13, 7, 13, "#ffd23a", "#1d1d1f")
    return a


def dry_storage_cabinet():  # стеклянная дверь, полки, индикатор влажности
    a = panel("#c4bfaa", noise=4)
    rect(a, 2, 2, 12, 13, "#2c3a40")
    for y in (5, 9, 13):
        for x in range(2, 13):
            put(a, y, x, "#9aa3a6")
    for y in (4, 8, 12):
        for x in range(3, 12, 2):
            put(a, y, x, "#3b3530")
    for i in range(10):
        shade(a, 2 + i, 3 + i, 1.4) if 3 + i <= 12 else None
    rect(a, 14, 3, 14, 6, "#3a6fd0")
    put(a, 9, 14, "#2b2e31")
    put(a, 10, 14, "#2b2e31")
    return a


def mineral_separator():
    a = panel("#7a6b4c", noise=6)
    plate(a, 3, 2, 12, 4)
    for x in range(2, 14):
        put(a, 9, x, "#2b2e31")
    lamps(a, 12, (4, 6), ("#52e06a", "#ff4a36"))
    hz(a, 9, 11, 14, 14)
    return a


def chlorinator():  # футерованная колонна, метка Cl2 (газ — жёлтый, ядовитый)
    a = canvas("#6a6e70", noise=4)
    cyl_shade(a)
    band(a, 6, 8, "#e8b923")
    for x in range(1, 16, 4):
        put(a, 7, x, "#2a6a2a")
    for y in (1, 14):
        for x in range(16):
            shade(a, y, x, 0.72)
    return a


def hafnium_separation_column():  # секции кристаллизаторов + смотровое окно
    a = canvas("#aeb4b8", noise=3)
    cyl_shade(a)
    for y in (0, 15):
        weld(a, y)
    rect(a, 6, 5, 9, 9, "#2b2e31")
    rect(a, 7, 6, 8, 8, "#cfe3ea")
    put(a, 6, 7, "#ffffff")
    return a


def kroll_reactor():  # кожух печи, нагревательная панель, термометр
    a = panel("#8f5a3a", noise=5, rivets=True)
    grille(a, 3, 3, 12, 7)
    plate(a, 3, 10, 8, 12)
    dial(a, 11, 11)
    return a


def vacuum_arc_furnace():  # водоохлаждаемый кристаллизатор (медь + зелёные рубашки)
    a = canvas("#b8693d", noise=6)
    cyl_shade(a)
    for y in (2, 3, 12, 13):
        for x in range(16):
            put(a, y, x, "#3f7a4a")
            shade(a, y, x, 1.0 - 0.25 * abs(x / 15 - 0.35))
    rect(a, 6, 6, 9, 9, "#2b2e31")
    rect(a, 7, 7, 8, 8, "#c6f0ff")
    return a


def pilger_mill():
    a = panel("#5d6f86", noise=5, rivets=True)
    plate(a, 3, 3, 12, 5)
    hz(a, 1, 12, 14, 14)
    return a


def ultrasonic_tester():  # экран A-скана
    a = canvas("#2a3036", noise=3)
    frame(a, 1.3, 0.6)
    rect(a, 2, 2, 13, 10, "#0b1a10")
    ys = [9, 9, 3, 9, 9, 8, 9, 6, 9, 9, 9, 7]
    for i, y in enumerate(ys):
        x = 2 + i
        for yy in range(y, 10):
            put(a, yy, x, "#52e06a")
    for i, c in enumerate(("#e8e8e0", "#e8e8e0", "#d33b2c", "#43b04a")):
        put(a, 12, 3 + i * 3, c)
        put(a, 12, 4 + i * 3, c)
    return a


def rod_loading_station():  # пульт стола снаряжения
    a = panel("#8c9a8e", noise=4)
    rect(a, 2, 2, 13, 6, "#3b4046")
    scr(a, 3, 3, 8, 5, fg="#7fd0ff", bg="#0b1a26")
    lamps(a, 4, (11, 12), ("#52e06a", "#ffd23a"))
    plate(a, 2, 9, 13, 11, "#6b4a2e", "#e8e2c8")  # He (коричн.)
    return a


def rod_welder():  # тёмное сварочное стекло с дугой
    a = panel("#4b5157", noise=5, rivets=True)
    rect(a, 3, 3, 12, 10, "#1d2a1d")
    rect(a, 7, 6, 8, 7, "#d9f2ff")
    put(a, 5, 7, "#7fb8ff")
    put(a, 8, 8, "#7fb8ff")
    plate(a, 4, 12, 11, 13, "#e8b923", "#1d1d1f")
    return a


def gas_collector():  # зонт: окраска + жёлтое кольцо (газ He)
    a = panel("#8c9a8e", noise=5)
    band(a, 9, 11, "#e8b923")
    for x in range(1, 15, 3):
        put(a, 10, x, "#6b4a2e")
    return a


def assembly_jig():  # стойка с линейкой высоты
    a = panel("#4e6f9a", noise=4)
    rect(a, 6, 0, 9, 15, "#d6d0b8")
    for y in range(0, 16, 2):
        put(a, y, 6, "#1d1d1f")
        put(a, y, 7, "#1d1d1f")
    for y in range(0, 16, 4):
        put(a, y, 8, "#1d1d1f")
    return a


def acheson_furnace():  # огнеупорная кладка, токоподвод
    a = bricks("#9b5a36", "#cdbb98")
    band(a, 0, 1, "#3a3b3f")
    return a


def acheson_charge():  # шихта сверху: песок/кокс с раскалёнными трещинами
    a = canvas("#6a6156", noise=14)
    for _ in range(30):
        put(a, rng.randrange(16), rng.randrange(16), rng.choice(("#2b2a28", "#c2b490")))
    for x in range(16):
        y = 8 + int(round(2 * math.sin(x * 0.7)))
        put(a, y, x, "#ff9a2e")
    return a


def gs_exchange_tower():  # колонна GS: метка H2S (жёлтый газ + красная полоса токсичности)
    a = canvas("#8e9497", noise=4)
    cyl_shade(a)
    band(a, 5, 9, "#e8b923")
    band(a, 7, 7, "#b2302a")
    for y in (0, 15):
        for x in range(16):
            shade(a, y, x, 0.72)
    return a


def vacuum_distillation_column():
    a = insul()
    band(a, 7, 8, "#3a6fd0")
    return a


# =====================================================================
# spent fuel
# =====================================================================
def fuel_pool_liner():  # плиты облицовки из нержавейки, сварные швы
    a = canvas("#b8bdc0", noise=3)
    for i in range(16):
        put(a, 0, i, "#8b8f91")
        put(a, i, 0, "#8b8f91")
        put(a, 8, i, "#9a9ea1")
        put(a, i, 8, "#9a9ea1")
        shade(a, 1, i, 1.12)
    return a


def fuel_pool_rack_open():  # нерж. уголок с облегчающими отверстиями
    a = panel("#b0b5b8", noise=3)
    for y in (3, 7, 11):
        rect(a, 6, y, 9, y + 1, "#2c3a40")
    return a


def fuel_pool_rack_borated():  # стенка ячейки: кожух + видимая пластина Boral
    a = panel("#b0b5b8", noise=3)
    rect(a, 2, 1, 13, 14, "#4f5357")
    frame(a, 1.1, 0.8, inset=2)
    for y in range(2, 14, 3):
        for x in range(3, 13):
            shade(a, y, x, 0.85)
    return a


def fuel_pool_cooling_port():  # решётка-сетка водозабора на облицовке
    a = fuel_pool_liner()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 5.5:
                put(a, y, x, "#1a2a24" if (x + y) % 2 else "#6f7a76")
            elif d < 6.8:
                put(a, y, x, "#5d6064")
    return a


def pool_heat_exchanger():  # кожух ТО, зелёная метка воды
    a = canvas("#8c9a8e", noise=4)
    cyl_shade(a)
    vband(a, 6, 9, "#3f7a4a")
    for x in range(16):
        shade(a, 0, x, 0.75)
        shade(a, 15, x, 0.75)
    return a


def pool_cleanup_filter():  # корпус фильтра + знак
    a = canvas("#aeb4b8", noise=3)
    cyl_shade(a)
    band(a, 3, 4, "#3f7a4a")
    band(a, 12, 13, "#3f7a4a")
    return a


def pool_gate():  # затвор: рёбра жёсткости, резиновое уплотнение по контуру
    a = canvas("#aab0b4", noise=3)
    for y in (4, 8, 12):
        for x in range(16):
            shade(a, y, x, 1.2)
            shade(a, y + 1, x, 0.7)
    for i in range(16):
        put(a, i, 0, "#232325")
        put(a, i, 15, "#232325")
    return a


def pool_boron_sampler():  # анализатор: дисплей ppm B
    a = panel("#c4bfaa", noise=4, rivets=True)
    scr(a, 3, 3, 12, 6, fg="#ff6a3a", bg="#1a0b06", rows=[4, 5])
    plate(a, 3, 9, 8, 10)
    lamps(a, 12, (10, 12), ("#52e06a", "#ffd23a"))
    for y in range(9, 14):
        put(a, y, 13, "#3f7a4a")  # пробоотборная линия
    return a


def dry_cask():  # бетонный кожух: стальные кольца, вентканал
    a = canvas("#a3a199", noise=8)
    for _ in range(8):
        shade(a, rng.randrange(16), rng.randrange(16), 0.7)
    band(a, 0, 0, "#7d847f")
    band(a, 15, 15, "#7d847f")
    rect(a, 4, 10, 11, 13, "#5d6064")
    grille(a, 5, 11, 10, 12)
    return a


def cask_canister():  # пенал: нерж., сварные швы, гравировка серийного номера
    a = canvas("#bcc1c4", noise=3)
    cyl_shade(a)
    weld(a, 1)
    weld(a, 14)
    for x in range(5, 11, 2):
        put(a, 7, x, "#6d7275")
    return a


def cask_overpack():
    a = canvas("#a7a59d", noise=7)
    cyl_shade(a)
    for _ in range(6):
        shade(a, rng.randrange(16), rng.randrange(16), 0.72)
    return a


def transport_cask():  # оребрённый корпус
    a = canvas("#9da3a6", noise=3)
    for x in range(16):
        k = 1.25 if x % 3 == 0 else 0.8 if x % 3 == 2 else 1.0
        for y in range(16):
            shade(a, y, x, k)
    return a


def impact_limiter():  # демпфер — окраска жёлтая, нерж. обечайка
    a = canvas("#d8b030", noise=5)
    cyl_shade(a)
    for y in (0, 15):
        for x in range(16):
            put(a, y, x, "#9da3a6")
    return a


# =====================================================================
# radiochemistry
# =====================================================================
def hot_cell():  # тяжёлый бетон + стальная закладная + знак
    a = heavy_concrete()
    rect(a, 0, 0, 15, 1, "#5d6064")
    frame(a, 1.0, 0.75)
    return a


def hot_cell_window_frame():
    a = canvas("#5d6064", noise=4)
    frame(a, 1.25, 0.65)
    for y, x in ((2, 2), (2, 13), (13, 2), (13, 13)):
        rivet(a, y, x)
    return a


def master_slave_manipulator():  # стальной корпус проходки, шильдик
    a = panel("#9aa0a4", noise=4, rivets=True)
    plate(a, 3, 3, 12, 5)
    rect(a, 5, 8, 10, 13, "#4b5157")
    frame(a, 1.2, 0.7, inset=0)
    return a


def shearing_machine():
    a = panel("#56636e", noise=5, rivets=True)
    plate(a, 3, 3, 12, 5)
    hz(a, 1, 11, 14, 14)
    return a


def offgas_treatment():  # скруббер NOx (оранжевый — кислые газы)
    a = canvas("#aeb4b8", noise=3)
    cyl_shade(a)
    band(a, 5, 7, "#c8692a")
    band(a, 6, 6, "#e8b923")
    for y in (0, 15):
        for x in range(16):
            shade(a, y, x, 0.72)
    return a


def iodine_filter_agz():  # корпус фильтра: табличка Ag-цеолит
    a = panel("#8c9a8e", noise=4, rivets=True)
    rect(a, 3, 3, 12, 8, "#c9cbd0")
    frame(a, 1.2, 0.75, inset=3)
    for x in range(4, 12, 2):
        put(a, 5, x, "#4a4c52")
        put(a, 6, x + 1, "#4a4c52")
    tref(a, 7, 11)
    return a


def clarifier():
    a = panel("#5f7f62", noise=5, rivets=True)
    plate(a, 3, 3, 12, 5)
    dial(a, 5, 10)
    lamps(a, 10, (9, 11), ("#52e06a", "#ff4a36"))
    return a


def oxalate_precipitator():  # узкий аппарат, смотровое стекло с розовой суспензией Pu(C2O4)2
    a = canvas("#aeb4b8", noise=3)
    cyl_shade(a)
    rect(a, 5, 4, 10, 11, "#2b2e31")
    rect(a, 6, 5, 9, 10, "#cfe3ea")
    rect(a, 6, 8, 9, 10, "#b0607a")
    band(a, 14, 14, "#3a6fd0")
    return a


def oxalic_acid_reactor():
    a = enamel_blue()
    band(a, 9, 10, "#c8692a")
    return a


def annular_tank():  # кольцевая ёмкость, маркировка геом. безопасности (синие полосы)
    a = canvas("#b4b9bc", noise=3)
    for y in (2, 13):
        band(a, y, y, "#3a6fd0")
    for x in range(16):
        put(a, 7, x, "#e8e8e0") if x % 4 else put(a, 7, x, "#3a6fd0")
    for y in range(16):
        shade(a, y, 0, 0.8)
        shade(a, y, 15, 0.8)
    return a


def slab_tank():  # плоская ёмкость: рёбра, мерное стекло
    a = canvas("#b4b9bc", noise=3)
    for x in (3, 7, 11):
        for y in range(16):
            shade(a, y, x, 1.2)
            shade(a, y, x + 1, 0.75)
    rect(a, 13, 1, 14, 14, "#2b2e31")
    rect(a, 13, 6, 14, 14, "#d4e04a")
    band(a, 0, 0, "#3a6fd0")
    return a


def raschig_rings():  # засыпка колец из боросиликатного стекла
    a = canvas("#5f7a70", noise=6)
    for cy in range(1, 16, 4):
        for cx in range(1 + (2 if (cy // 4) % 2 else 0), 16, 4):
            for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
                y, x = cy + dy, cx + dx
                if 0 <= y < 16 and 0 <= x < 16:
                    put(a, y, x, "#dceee8" if dy <= 0 else "#a9c6bc")
            put(a, cy, cx, "#2f3d38")
    return a


def fissile_batch_counter():  # терминал учёта массы
    a = panel("#c4bfaa", noise=4, rivets=True)
    scr(a, 2, 2, 13, 7, fg="#47d16a", bg="#0f2a17", rows=[3, 5])
    for y in range(9, 14, 2):
        for x in range(3, 10, 2):
            put(a, y, x, "#3b4046")
    put(a, 10, 12, "#ff4a36")
    put(a, 12, 12, "#52e06a")
    return a


def criticality_alarm():  # СЦР: три детектора и табличка
    a = panel("#b2302a", noise=4)
    for x in (3, 7, 11):
        rect(a, x, 3, x + 1, 9, "#d6dadc")
        put(a, 4, x, "#ffffff")
    plate(a, 2, 11, 13, 13, "#e8e2c8", "#b2302a")
    return a


def glovebox():  # смотровое стекло + перчаточные порты
    a = np.zeros((16, 16, 4))
    a[:, :] = hexc("#b0b5b8")
    for y in range(1, 15):
        for x in range(1, 15):
            a[y, x] = hexc("#9fc3cf")
            a[y, x, :3] *= 0.9 + 0.1 * (y / 15)
            if x - y in (5, 6):
                a[y, x] = hexc("#e8f4f8")
    for cx in (4.5, 11.5):
        for y in range(16):
            for x in range(16):
                d = ((x - cx) ** 2 + (y - 9.5) ** 2) ** 0.5
                if d < 1.8:
                    a[y, x] = hexc("#1b1b1d")
                elif d < 2.8:
                    a[y, x] = hexc("#3a3d40")
    frame(a, 1.15, 0.7)
    return a


def hlw_tank():  # бак ВАО: змеевики охлаждения (зелёные полосы), знак
    a = canvas("#aeb4b8", noise=3)
    cyl_shade(a)
    for y in (2, 5, 12):
        for x in range(16):
            put(a, y, x, "#3f7a4a")
            shade(a, y, x, 1.0 - 0.25 * abs(x / 15 - 0.35))
    for x in range(16):  # жёлтое кольцо радиационной опасности
        put(a, 8, x, "#e8b923")
        put(a, 9, x, "#e8b923")
    return a


def hlw_tank_purge():  # продувка воздухом: синяя метка, анализатор H2
    a = panel("#8c9a8e", noise=4, rivets=True)
    band(a, 2, 4, "#3a5f9a")
    scr(a, 3, 7, 8, 9, fg="#ffd23a", bg="#2a2108", rows=[8])
    lamps(a, 8, (11, 13), ("#52e06a", "#ff4a36"))
    grille(a, 3, 11, 12, 13)
    return a


def solvent_wash():  # окна: органика (соломенная) над водной фазой
    a = panel("#aeb4b8", noise=3, rivets=True)
    for x0 in (2, 9):
        rect(a, x0 - 1, 3, x0 + 5, 12, "#2b2e31")
        rect(a, x0, 4, x0 + 4, 7, "#e6d250")
        rect(a, x0, 8, x0 + 4, 11, "#c6dbe0")
        put(a, 4, x0 + 1, "#ffffff")
    band(a, 14, 14, "#7a4fa0")  # щёлочь Na2CO3 — фиолетовый
    return a


# =====================================================================
# waste
# =====================================================================
def waste_compactor():
    a = panel("#e0b43a", noise=5, rivets=True)
    plate(a, 3, 3, 12, 5)
    tref(a, 7, 10)
    return a


def cementation_mixer():
    a = panel("#8a8f88", noise=6, rivets=True)
    plate(a, 3, 3, 12, 5)
    for _ in range(12):
        put(a, rng.randrange(7, 14), rng.randrange(2, 14), "#c2c0b8")
    return a


def cement_kiln():
    a = canvas("#8a8478", noise=5)
    cyl_shade(a)
    for x in (4, 11):
        for y in range(16):
            put(a, y, x, "#55585c")
    return a


def bitumenizer():  # обогреваемый барабан-экструдер: горючее (коричневая метка)
    a = insul()
    band(a, 6, 9, "#6b3f22")
    band(a, 7, 8, "#d2591f")
    return a


def vitrification_melter():  # кожух плавителя, раскалённая гляделка
    a = panel("#6c7175", noise=5, rivets=True)
    rect(a, 4, 4, 11, 9, "#2b2e31")
    for y in range(5, 9):
        for x in range(5, 11):
            put(a, y, x, "#ffb347" if (x + y) % 3 else "#fff1b0")
    tref(a, 7, 12)
    return a


def repository_controller():  # пульт: схема барьеров
    a = canvas("#3b4046", noise=3)
    frame(a, 1.25, 0.65)
    rect(a, 2, 2, 13, 10, "#0f1a22")
    for y, c in ((3, "#5fb0d8"), (5, "#c9bfa0"), (6, "#c9bfa0"), (8, "#7d7b74"), (9, "#7d7b74")):
        for x in range(3, 13):
            put(a, y, x, c)
    rect(a, 7, 3, 8, 7, "#b8693d")  # медный контейнер в бентоните
    lamps(a, 12, (3, 6, 9, 12), ("#52e06a", "#52e06a", "#ffd23a", "#e8e8e0"))
    return a


def bentonite_buffer():  # уплотнённые бентонитовые блоки (кладка вперевязку)
    a = canvas("#c6bd99", noise=7)
    for i in range(16):
        shade(a, 7, i, 0.72)
        shade(a, 15, i, 0.72)
    for y in range(0, 7):
        shade(a, y, 4, 0.72)
        shade(a, y, 12, 0.72)
    for y in range(8, 15):
        shade(a, y, 0, 0.72)
        shade(a, y, 8, 0.72)
    for _ in range(8):
        shade(a, rng.randrange(16), rng.randrange(16), 0.85)
    return a


def near_surface_vault():  # монолитный ж/б отсек: швы, трафарет
    a = canvas("#a19f97", noise=9)
    for i in range(16):
        shade(a, i, 0, 0.75)
        shade(a, 0, i, 0.75)
        shade(a, i, 15, 1.12)
    for _ in range(6):
        shade(a, rng.randrange(16), rng.randrange(16), 0.7)
    tref(a, 8, 8)
    for y in range(6, 11):
        for x in range(6, 11):
            if a[y, x, 0] > 200:
                a[y, x, :3] = a[y, x, :3] * 0.8 + hexc("#a19f97")[:3] * 0.2
    return a


# =====================================================================
# items — flat 16×16 icons
# =====================================================================
def blank():
    return np.zeros((16, 16, 4))


def P(a, y, x, col, k=1.0, j=0):
    if 0 <= y < 16 and 0 <= x < 16:
        c = _c(col).copy()
        c[:3] = c[:3] * k + (rng.uniform(-j, j) if j else 0)
        a[y, x] = c


def finish(a, hi=1.18, lo=0.62):
    """Edge light/dark like vanilla items: top/left edges lit, bottom/right darkened."""
    op = a[:, :, 3] > 0
    out = a.copy()
    for y in range(16):
        for x in range(16):
            if not op[y, x]:
                continue
            up = y == 0 or not op[y - 1, x]
            lf = x == 0 or not op[y, x - 1]
            dn = y == 15 or not op[y + 1, x]
            rt = x == 15 or not op[y, x + 1]
            if dn or rt:
                out[y, x, :3] *= lo
            elif up or lf:
                out[y, x, :3] *= hi
    return out


def i_pile(col, speck=None, n_speck=10, wide=6.5, top=5):
    a = blank()
    c = _c(col)
    for y in range(top, 14):
        w = min(wide, (y - top + 1) * 1.25)
        for x in range(16):
            if abs(x - 7.5) <= w:
                k = 1.18 - 0.4 * (y - top) / (14 - top) + (0.08 if x < 7.5 else -0.05)
                P(a, y, x, c, k, 6)
    if speck:
        for _ in range(n_speck):
            y, x = rng.randrange(top + 2, 13), rng.randrange(3, 13)
            if a[y, x, 3] > 0:
                P(a, y, x, speck)
    return finish(a)


def i_ingot(col, mark=None):
    a = blank()
    c = _c(col)
    for y in range(5, 8):  # верхняя грань
        for x in range(4 - (y - 5), 13 - (7 - y) + 1):
            P(a, y, x, c, 1.25, 3)
    for y in range(8, 12):  # лицевая
        for x in range(1, 14):
            P(a, y, x, c, 0.95, 3)
    for y in range(8, 12):  # торец
        for x in range(14, 16 - (y - 8) // 2):
            P(a, y, x, c, 0.72, 2)
    for x in range(2, 13):
        P(a, 8, x, c, 1.05)
    if mark:
        for x in (5, 6, 8, 9):
            P(a, 10, x, mark)
    return finish(a)


def i_lump(col, speck=None, n_speck=8, speck2=None, r=5.2, cx=7.5, cy=8.5, seed_pores=None):
    a = blank()
    c = _c(col)
    for y in range(16):
        for x in range(16):
            ang = math.atan2(y - cy, x - cx)
            rr = r * (1 + 0.16 * math.sin(3 * ang + 1) + 0.1 * math.cos(5 * ang))
            d = ((x - cx) ** 2 + ((y - cy) * 1.1) ** 2) ** 0.5
            if d <= rr:
                k = 1.15 - 0.35 * ((y - cy + r) / (2 * r)) + (0.05 if x < cx else 0)
                P(a, y, x, c, k, 8)
    for s, nn in ((speck, n_speck), (speck2, n_speck // 2)):
        if s:
            for _ in range(nn):
                y, x = rng.randrange(3, 14), rng.randrange(3, 13)
                if a[y, x, 3] > 0:
                    P(a, y, x, s)
    if seed_pores:
        for _ in range(seed_pores):
            y, x = rng.randrange(4, 13), rng.randrange(4, 12)
            if a[y, x, 3] > 0:
                P(a, y, x, c, 0.45)
    return finish(a)


def i_chunks(col, speck):
    a = blank()
    for cx, cy, r in ((5, 6, 3.2), (11, 7, 2.6), (7, 11.5, 3.0), (12.5, 12, 2.0)):
        for y in range(16):
            for x in range(16):
                if abs(x - cx) + abs(y - cy) * 1.1 <= r:
                    P(a, y, x, col, 1.15 - 0.3 * (y - cy + r) / (2 * r), 8)
    for _ in range(8):
        y, x = rng.randrange(3, 15), rng.randrange(2, 15)
        if a[y, x, 3] > 0:
            P(a, y, x, speck)
    return finish(a)


def i_cyl_v(body, x0, x1, y0, y1, top=None, ribs=(), label=None, label_rows=None, cap=None):
    """Vertical cylinder seen slightly from above."""
    a = blank()
    b = _c(body)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, x1 - x0)
            P(a, y, x, b, 1.15 - 0.45 * abs(t - 0.3), 3)
    for x in range(x0 + 1, x1):  # эллипс верхней крышки
        P(a, y0, x, top or b, 1.2)
        P(a, y0 + 1, x, top or b, 1.05)
    for y in ribs:
        for x in range(x0, x1 + 1):
            a[y, x, :3] *= 0.7
    if label:
        r0, r1 = label_rows
        for y in range(r0, r1 + 1):
            for x in range(x0 + 1, x1):
                t = (x - x0) / max(1, x1 - x0)
                P(a, y, x, label, 1.1 - 0.35 * abs(t - 0.3))
    if cap:
        cx = (x0 + x1) // 2
        P(a, y0 - 1, cx, cap)
        P(a, y0 - 1, cx + 1, cap, 0.8)
    a[y0, x0, 3] = 0
    a[y0, x1, 3] = 0
    return finish(a)


def i_cyl_h(body, x0, x1, y0, y1, skirts=(), end=None, valve=None, label=None):
    """Horizontal cylinder (UF6 cylinders) with elliptical left end cap."""
    a = blank()
    b = _c(body)
    h = y1 - y0
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(1, h)
        for x in range(x0, x1 + 1):
            P(a, y, x, b, 1.2 - 0.55 * abs(t - 0.3), 3)
    for y in range(y0 + 1, y1):  # торец
        P(a, y, x0, end or b, 1.0)
    for x in skirts:
        for y in range(y0 - 1, y1 + 2):
            P(a, y, x, b, 0.75 if y in (y0 - 1, y1 + 1) else 0.85)
    if label:
        for y in range(y0 + 1, y0 + 3):
            for x in range((x0 + x1) // 2 - 1, (x0 + x1) // 2 + 2):
                P(a, y, x, label)
    if valve:
        vx, vy = valve
        P(a, vy, vx, "#d3a246")
        P(a, vy - 1, vx, "#d3a246", 1.2)
        P(a, vy - 1, vx + 1, "#b2302a")
    a[y0, x0, 3] = 0
    a[y1, x0, 3] = 0
    return finish(a)


def i_rod(col, w=2, x0=2, y0=13, x1=13, y1=2, cap=None, cap2=None, bands=(), hole=False, weave=None):
    a = blank()
    c = _c(col)
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        for k in range(w):
            colr = c
            if weave is not None and (i + k) % 2:
                colr = _c(weave)
            P(a, y + k, x, colr, 1.15 - 0.25 * k / max(1, w - 1), 3)
            P(a, y + k, x + 1, colr, 1.05 - 0.25 * k / max(1, w - 1), 3)
        if i in bands:
            for k in range(w):
                P(a, y + k, x, c, 0.65)
        if cap and i < 2:
            for k in range(w):
                P(a, y + k, x, cap)
        if cap2 and i > n - 2:
            for k in range(w):
                P(a, y + k, x + 1, cap2)
    if hole:
        P(a, y1, x1 + 1, "#1b1b1d")
    return finish(a)


def i_drum(body, top, band_col=None, mark=False, top_fill=None):
    a = i_cyl_v(body, 3, 12, 2, 14, top=top, ribs=(6, 10))
    if top_fill:
        for x in range(5, 11):
            P(a, 2, x, top_fill, 1.0, 5)
    if band_col:
        for y in (8,):
            for x in range(4, 12):
                P(a, y, x, band_col, 1.1 - 0.3 * abs((x - 3) / 9 - 0.3))
    if mark:
        for y in range(11, 14):
            for x in range(6, 9):
                P(a, y, x, "#e8b923")
        P(a, 12, 7, "#1d1d1f")
        P(a, 11, 6, "#1d1d1f")
        P(a, 11, 8, "#1d1d1f")
        P(a, 13, 7, "#1d1d1f")
    return a


def i_sack(col, label):
    a = blank()
    c = _c(col)
    for y in range(3, 15):
        w = 5 if y > 4 else 3
        if y > 12:
            w = 5.5
        for x in range(16):
            if abs(x - 7.5) <= w:
                P(a, y, x, c, 1.12 - 0.3 * (y - 3) / 12 + (0.05 if x < 8 else -0.05), 5)
    for x in range(6, 10):  # завязка
        P(a, 2, x, c, 0.7)
    for y in range(7, 11):
        for x in range(5, 11):
            P(a, y, x, label)
    for x in range(6, 10, 2):
        P(a, 8, x, "#333333")
    return finish(a)


def i_tray(pellet, rim):
    a = blank()
    for y in range(5, 13):
        for x in range(1, 15):
            P(a, y, x, rim, 0.85, 3)
    for y in (5, 12):
        for x in range(1, 15):
            P(a, y, x, rim, 1.2 if y == 5 else 0.7)
    for y in range(6, 12, 2):
        for x in range(2, 14, 2):
            P(a, y, x, pellet, 1.0, 6)
            P(a, y, x + 1, pellet, 0.8, 6)
            P(a, y + 1, x, pellet, 0.7, 6)
            P(a, y + 1, x + 1, pellet, 0.6, 6)
            P(a, y, x, pellet, 1.45)
    return finish(a, 1.1, 0.75)


def i_balls(col):
    a = blank()
    for cx, cy, r in ((5.5, 10.5, 3.2), (10.5, 10.5, 3.2), (8, 6, 3.2)):
        for y in range(16):
            for x in range(16):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d <= r:
                    k = 1.35 - 0.6 * (((x - cx + 1.2) ** 2 + (y - cy + 1.2) ** 2) ** 0.5) / (r + 1.5)
                    P(a, y, x, col, k, 2)
    return finish(a, 1.0, 0.7)


def i_mesh(col):
    a = blank()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.8 and d > 5.6:
                P(a, y, x, "#8a8a86", 1.15 - 0.4 * (y / 15), 3)
            elif d <= 5.6 and (x + y) % 2 == 0:
                P(a, y, x, col, 1.15 - 0.35 * (y / 15), 4)
    return a


def i_grid(col):
    a = blank()
    for y in range(2, 15):
        for x in range(2, 15):
            if (x - 2) % 3 == 0 or (y - 2) % 3 == 0:
                P(a, y, x, col, 1.1 - 0.3 * (y - 2) / 12, 3)
    return finish(a, 1.1, 0.75)


def i_nozzle():
    a = blank()
    s = "#b4b8bb"
    for y in range(3, 8):  # верхняя плита
        for x in range(2 + (7 - y) // 2, 15 - (y - 3) // 2):
            P(a, y, x, s, 1.2, 3)
    for y in range(8, 13):
        for x in range(2, 14):
            P(a, y, x, s, 0.9, 3)
    for y, x in ((4, 5), (4, 8), (4, 11), (6, 6), (6, 9), (6, 12)):
        P(a, y, x, "#2b2e31")
    for x in (3, 12):  # опоры
        for y in range(13, 15):
            P(a, y, x, s, 0.7)
    return finish(a)


def i_radiometer():  # СРП-68: корпус + выносной зонд на кабеле
    a = blank()
    for y in range(7, 14):
        for x in range(1, 9):
            P(a, y, x, "#6f7f5c", 1.1 - 0.3 * (y - 7) / 6, 4)
    for x in range(3, 7):  # ручка
        P(a, 5, x, "#2b2b2b")
    P(a, 6, 3, "#2b2b2b")
    P(a, 6, 6, "#2b2b2b")
    for y in range(8, 11):
        for x in range(2, 6):
            P(a, y, x, "#ece8dc")
    P(a, 10, 4, "#b2302a")
    P(a, 9, 3, "#1b1b1b")
    for i in range(6):  # зонд
        for k in range(2):
            P(a, 9 - i + k, 10 + i // 1 if 10 + i < 16 else 15, "#c9cdd0", 1.1 - 0.2 * k)
    P(a, 12, 9, "#1d1d1f")
    P(a, 11, 10, "#1d1d1f")
    for i in range(3):
        P(a, 2 + i, 13 + i // 2, "#c9cdd0", 1.0)
    return finish(a)


def i_spectrometer():  # полевой γ-спектрометр: жёлтый корпус, ЖК, толстый зонд
    a = blank()
    for y in range(4, 14):
        for x in range(2, 10):
            P(a, y, x, "#d8b03a", 1.1 - 0.3 * (y - 4) / 9, 4)
    for y in range(5, 9):
        for x in range(3, 9):
            P(a, y, x, "#9fb58a")
    for x in range(4, 8):
        P(a, 6, x, "#2b3a20")
    P(a, 7, 5, "#2b3a20")
    for y, x in ((10, 4), (10, 6), (12, 4), (12, 6)):
        P(a, y, x, "#3b3b3b")
    for y in range(6, 13):  # зонд NaI 3"
        for x in range(11, 15):
            P(a, y, x, "#9aa0a4", 1.15 - 0.4 * (x - 11) / 3, 3)
    P(a, 6, 11, "#3b3b3b")
    P(a, 12, 14, "#3b3b3b")
    return finish(a)


def i_core():
    a = blank()
    cols = ["#8a8a85", "#7d7b74", "#2e2a26", "#8a8a85", "#9a8c70", "#7d7b74"]
    for i in range(12):
        x, y = 2 + i, 12 - i
        c = cols[(i // 2) % len(cols)]
        for k in range(3):
            P(a, y + k, x, c, 1.15 - 0.2 * k, 6)
            P(a, y + k, x + 1, c, 1.0 - 0.2 * k, 6)
    for (y, x) in ((10, 6), (9, 7)):
        P(a, y, x, "#c8d84a")
    return finish(a)


def i_canister(body, x0, x1, neck=False, lid=None, lugs=False):
    a = i_cyl_v(body, x0, x1, 3 if neck else 2, 14, top=lid)
    if neck:
        for y in (1, 2):
            for x in range(7, 9):
                P(a, y, x, body, 1.2 if y == 1 else 0.9)
    if lugs:
        for x in (x0 + 1, x1 - 1):
            P(a, 1, x, "#4b5157")
    return a


def i_bottle(body, valve):
    a = i_cyl_v(body, 6, 10, 3, 14)
    P(a, 2, 7, body, 1.1)
    P(a, 2, 8, body, 0.9)
    P(a, 1, 7, valve, 1.1)
    P(a, 1, 8, valve, 0.9)
    P(a, 0, 7, valve)
    return a


def i_jar(content):
    a = i_cyl_v("#9aa0a4", 4, 11, 6, 13, top=content)
    for x in range(5, 11):
        P(a, 6, x, content, 1.1, 4)
        P(a, 7, x, content, 0.95, 4)
    for x in range(5, 11):
        P(a, 10, x, "#3a6fd0")
    return a


def i_slug():  # блочок U в алюминиевом чехле
    a = i_cyl_v("#c9cdd2", 5, 10, 4, 13, top="#c9cdd2", ribs=(5, 12))
    P(a, 4, 7, "#4b4a46")
    P(a, 4, 8, "#4b4a46")
    return a


ITEM_TEX = {
    # руды и рудоподготовка
    "raw_uraninite": lambda: i_lump("#1e1c1b", "#c8d84a", 10, "#6a7a3a"),
    "raw_coffinite": lambda: i_lump("#2e2620", "#7a5a3a", 8, "#a7b04a"),
    "raw_carnotite": lambda: i_lump("#c9a36a", "#f0dc30", 22, "#e8c020"),
    "raw_low_grade_uranium_ore": lambda: i_lump("#7d7b74", "#c8d84a", 4, "#3a3632"),
    "crushed_uranium_ore": lambda: i_chunks("#5f5c56", "#c8d84a"),
    "ground_uranium_ore": lambda: i_pile("#6e6a60", "#c8d84a", 8),
    "monazite_concentrate": lambda: i_pile("#b9783c", "#e0a860", 10),
    "zircon_concentrate": lambda: i_pile("#d9c9a6", "#f2ece0", 10),
    "scintillation_radiometer": i_radiometer,
    "gamma_spectrometer": i_spectrometer,
    "core_sample": i_core,
    "grinding_media": lambda: i_balls("#9aa0a4"),
    "flocculant": lambda: i_sack("#e6e4dc", "#3a6fd0"),
    # химия урана
    "yellowcake": lambda: i_pile("#f2c81e", "#ffe680", 10),
    "uo3_powder": lambda: i_pile("#e07a1e", "#f5a050", 8),
    "uo2_powder": lambda: i_pile("#2e2620", "#4a3b30", 10),
    "uf4_powder": lambda: i_pile("#5aa04a", "#88c870", 8),
    "uranium_slug": i_slug,
    "depleted_uranium_ingot": lambda: i_ingot("#4b4a46", "#6a6e3a"),
    "uf6_cylinder_48y": lambda: i_cyl_h("#a9aea8", 1, 14, 5, 12, skirts=(2, 13), valve=(14, 4), label="#e8b923"),
    "uf6_cylinder_30b": lambda: i_cyl_h("#7f8c96", 3, 12, 6, 11, skirts=(4, 11), valve=(12, 5), label="#b2302a"),
    "uf6_cylinder_5a": lambda: i_bottle("#c4c2b4", "#d3a246"),
    "centrifuge_rotor_aluminum": lambda: i_rod("#c8cdd2", 3, cap="#7d8287", cap2="#7d8287", bands=(4, 8)),
    "centrifuge_rotor_maraging": lambda: i_rod("#5f6a76", 3, cap="#c9cdd0", cap2="#c9cdd0", bands=(4, 8)),
    "centrifuge_rotor_cfrp": lambda: i_rod("#2a2a2e", 3, cap="#9aa0a4", cap2="#9aa0a4", weave="#46464c"),
    # топливо
    "pellet_lubricant": lambda: i_jar("#f4f2ea"),
    "fuel_pellet_tray": lambda: i_tray("#3b3530", "#9aa0a4"),
    "uo2_grinding_sludge": lambda: i_lump("#2f2721", "#7a8a96", 10, r=5.6, cy=9.5),
    "zirconium_sponge": lambda: i_lump("#b9b6a9", None, seed_pores=26),
    "zircaloy_ingot": lambda: i_ingot("#c9c4b6", "#8a8578"),
    "e110_ingot": lambda: i_ingot("#b5bac4", "#3a6fd0"),
    "cladding_tube": lambda: i_rod("#c9c4b6", 2, x0=1, y0=14, x1=13, y1=2, hole=True),
    "hafnium_ingot": lambda: i_ingot("#7a7f84", "#3b3e42"),
    "fuel_rod": lambda: i_rod("#c9c4b6", 2, x0=1, y0=14, x1=13, y1=2, cap="#7a7f84", cap2="#7a7f84", bands=(6,)),
    "spacer_grid": lambda: i_grid("#c9c4b6"),
    "fa_nozzle": i_nozzle,
    "boron_carbide": lambda: i_chunks("#2d2f34", "#8a96a8"),
    "pt_rh_gauze": lambda: i_mesh("#d9d9d6"),
    "vanadium_pentoxide": lambda: i_pile("#d9822b", "#f0a848", 8),
    "magnesium_ingot": lambda: i_ingot("#dcdedc", "#a8aaa8"),
    "portland_cement": lambda: i_sack("#8f8f8a", "#e8e2c8"),
    "bentonite": lambda: i_lump("#c6c09a", "#9a9470", 10),
    # радиохимия / отходы
    "puo2_powder": lambda: i_pile("#6b5a2c", "#8a7a40", 8),
    "mox_pellet_tray": lambda: i_tray("#4a4030", "#b2302a"),
    "hulls_drum": lambda: i_drum("#b9bec2", "#c9cdd0", band_col="#b2302a", mark=True),
    "waste_drum": lambda: i_drum("#9ea4a6", "#b4b8bb", band_col="#e8b923", mark=True),
    "cemented_waste_drum": lambda: i_drum("#6f7a6c", "#8a9488", top_fill="#b8b6ae", mark=True),
    "bitumen_waste_drum": lambda: i_drum("#3a3a3a", "#4a4a4a", band_col="#d2591f", top_fill="#151413", mark=True),
    "hlw_glass_canister": lambda: i_canister("#bcc1c4", 5, 10, neck=True),
    "copper_canister": lambda: i_canister("#c47a45", 4, 11, lid="#d48a55", lugs=True),
    "steel_overpack": lambda: i_canister("#5d6266", 3, 12, lid="#6f7478", lugs=True),
    "niobium_dust": lambda: i_pile("#9a9fae", "#c0c4d0", 8, wide=5, top=7),
    "tin_dust": lambda: i_pile("#c8c8c0", "#e8e8e2", 8, wide=5, top=7),
}


# =====================================================================
# registry
# =====================================================================
BLOCK_TEX = {
    # enrichment
    "gas_centrifuge": gas_centrifuge,
    "cascade_header": cascade_header,
    "cascade_interstage_pipe": cascade_interstage_pipe,
    "cascade_feed_station": station("#1d1d1f", "#e8e2c8"),
    "cascade_product_station": station("#e8e2c8", "#b2302a"),
    "cascade_tails_station": station("#e8e2c8", "#3a5f9a"),
    "cascade_isolation_valve": cascade_isolation_valve,
    "trace_heater": trace_heater,
    # fuel fabrication
    "defluorination_kiln": defluorination_kiln,
    "pellet_press": pellet_press,
    "sintering_furnace": sintering_furnace,
    "centerless_grinder": centerless_grinder,
    "pellet_inspection_station": pellet_inspection_station,
    "dry_storage_cabinet": dry_storage_cabinet,
    "mineral_separator": mineral_separator,
    "chlorinator": chlorinator,
    "hafnium_separation_column": hafnium_separation_column,
    "kroll_reactor": kroll_reactor,
    "vacuum_arc_furnace": vacuum_arc_furnace,
    "pilger_mill": pilger_mill,
    "ultrasonic_tester": ultrasonic_tester,
    "rod_loading_station": rod_loading_station,
    "rod_welder": rod_welder,
    "gas_collector": gas_collector,
    "assembly_jig": assembly_jig,
    "acheson_furnace": acheson_furnace,
    "gs_exchange_tower": gs_exchange_tower,
    "vacuum_distillation_column": vacuum_distillation_column,
    # spent fuel
    "fuel_pool_liner": fuel_pool_liner,
    "fuel_pool_rack_open": fuel_pool_rack_open,
    "fuel_pool_rack_borated": fuel_pool_rack_borated,
    "fuel_pool_cooling_port": fuel_pool_cooling_port,
    "pool_heat_exchanger": pool_heat_exchanger,
    "pool_cleanup_filter": pool_cleanup_filter,
    "pool_gate": pool_gate,
    "pool_boron_sampler": pool_boron_sampler,
    "dry_cask": dry_cask,
    "cask_canister": cask_canister,
    "cask_overpack": cask_overpack,
    "transport_cask": transport_cask,
    # radiochemistry
    "hot_cell": hot_cell,
    "hot_cell_window": hot_cell_window_frame,
    "master_slave_manipulator": master_slave_manipulator,
    "shearing_machine": shearing_machine,
    "offgas_treatment": offgas_treatment,
    "iodine_filter_agz": iodine_filter_agz,
    "clarifier": clarifier,
    "oxalate_precipitator": oxalate_precipitator,
    "oxalic_acid_reactor": oxalic_acid_reactor,
    "annular_tank": annular_tank,
    "slab_tank": slab_tank,
    "raschig_rings": raschig_rings,
    "fissile_batch_counter": fissile_batch_counter,
    "criticality_alarm": criticality_alarm,
    "glovebox": glovebox,
    "hlw_tank": hlw_tank,
    "hlw_tank_purge": hlw_tank_purge,
    "solvent_wash": solvent_wash,
    # waste
    "waste_compactor": waste_compactor,
    "cementation_mixer": cementation_mixer,
    "cement_kiln": cement_kiln,
    "bitumenizer": bitumenizer,
    "vitrification_melter": vitrification_melter,
    "repository_controller": repository_controller,
    "bentonite_buffer": bentonite_buffer,
    "near_surface_vault": near_surface_vault,
}

COMMON = {
    "ss": ss_plate,
    "ss_smooth": lambda: canvas("#b0b5b8", noise=3),
    "refractory": refractory,
    "insul": insul,
    "heavy_concrete": heavy_concrete,
    "glow_hot": glow_hot,
    "lead_glass": lead_glass,
    "glass": clear_glass,
    "boral": boral,
    "frost": frost,
    "grinding_wheel": grinding_wheel,
    "bottle_he": bottle_brown,
    "enamel_blue": enamel_blue,
    "copper": copper,
    "copper_smooth": lambda: canvas("#b8693d", noise=6),
    "bitumen": bitumen,
    "pipe_uf6": lambda: gas_pipe("#b0b5b8", "#e8b923"),
    "pipe_air": lambda: gas_pipe("#3a5f9a", "#3a5f9a"),
    "grating": grating,
    "charge": acheson_charge,
    "impact_limiter": impact_limiter,
    "concrete_smooth": lambda: canvas("#9d9b95", noise=8),
    "yellow_paint": lambda: panel("#e0b43a", noise=4),
    "green_paint": lambda: panel("#5f7f62", noise=5),
    "water": lambda: canvas("#3d7fa8", noise=8),
    "arc": lambda: canvas("#d9f2ff", noise=6),
    "pipe_alkali": lambda: gas_pipe("#7a4fa0", "#7a4fa0"),
    "drum": drum_body,
}

TEXTURES.update({f"block/common/fuelcycle_{k}.png": v for k, v in COMMON.items()})
TEXTURES.update({f"block/{k}.png": v for k, v in BLOCK_TEX.items()})
TEXTURES.update({f"item/{k}.png": v for k, v in ITEM_TEX.items()})
