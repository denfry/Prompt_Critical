"""Plant area textures: reactor island (rest of 00 §0.7.1), 03a mechanics/turbine/
ventilation/fuel handling, 03b/03c control room, 04b radiation safety (+ PPE armor
layers), 04a maintenance items."""
import math
from pclib import *


# ======================================================================
# helpers
# ======================================================================
def blank(n=16, h=None):
    return np.zeros((h or n, n, 4))


def C(h):
    return hexc(h)


def px(a, x, y, col):
    if 0 <= y < a.shape[0] and 0 <= x < a.shape[1]:
        a[y, x] = C(col) if isinstance(col, str) else col


def box(a, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(a, x, y, col)


def kbox(a, x0, y0, x1, y1, k):
    for y in range(max(0, y0), min(a.shape[0], y1 + 1)):
        for x in range(max(0, x0), min(a.shape[1], x1 + 1)):
            a[y, x, :3] *= k


def line(a, x0, y0, x1, y1, col):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        px(a, x0, y0, col)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def circ(a, cx, cy, r, col, r0=-1.0):
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            d = math.hypot(x - cx, y - cy)
            if r0 <= d < r:
                px(a, x, y, col)


def outline(a, col="#18191b"):
    """Vanilla-style dark outline around opaque pixels of an icon."""
    m = a[:, :, 3] > 0
    n = a.shape[0]
    out = a.copy()
    for y in range(n):
        for x in range(a.shape[1]):
            if m[y, x]:
                continue
            for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                yy, xx = y + dy, x + dx
                if 0 <= yy < n and 0 <= xx < a.shape[1] and m[yy, xx]:
                    out[y, x] = C(col)
                    break
    return out


def lit(a, k_top=1.15, k_bot=0.82):
    """Icon shading: top-left pixels of each opaque run lighter, bottom-right darker."""
    m = a[:, :, 3] > 0
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            if not m[y, x]:
                continue
            if y == 0 or not m[y - 1, x] or x == 0 or not m[y, x - 1]:
                a[y, x, :3] *= k_top
            elif y == a.shape[0] - 1 or not m[y + 1, x] or x == a.shape[1] - 1 or not m[y, x + 1]:
                a[y, x, :3] *= k_bot
    return a


def icon(fn):
    def wrap():
        a = blank()
        fn(a)
        return outline(lit(a))
    return wrap


def lampc(base, n=16):
    a = canvas(base, n, noise=0)
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c)
            shade(a, y, x, max(0.75, 1.35 - d / (0.75 * n)))
    return a


def grille(a, x0, y0, x1, y1, step=2, k=0.5, vertical=False):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if ((x - x0) if vertical else (y - y0)) % step == 0:
                a[y, x, :3] *= k


def plate(a, x0, y0, x1, y1, col="#e6e2d2", ink="#333333"):
    box(a, x0, y0, x1, y1, col)
    for x in range(x0 + 1, x1, 2):
        px(a, x, (y0 + y1) // 2, ink)


def stripe_h(a, y0, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(a.shape[1]):
            k = a[y, x, 0] / 160.0
            a[y, x] = C(col)
            a[y, x, :3] *= min(1.15, max(0.85, k))


def hazard_rows(a, y0, y1):
    for y in range(y0, y1 + 1):
        for x in range(a.shape[1]):
            a[y, x] = C("#e8b923") if ((x + y) // 2) % 2 == 0 else C("#1d1d1f")


def trefoil_px(a, cx, cy, r, bg="#e8b923", fg="#1d1d1f"):
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d > r + 0.5:
                continue
            ang = (math.degrees(math.atan2(dy, dx)) + 360 + 90) % 120
            if d < r * 0.22 or (r * 0.38 < d < r * 0.95 and ang < 60):
                px(a, x, y, fg)
            elif bg:
                px(a, x, y, bg)


# ======================================================================
# common plant materials
# ======================================================================
def insulation(smooth=False):  # теплоизоляция в оцинкованной/алюминиевой обшивке
    a = canvas("#c4c8c6", noise=3)
    for x in range(16):
        for y in (7, 15):
            shade(a, y, x, 0.78)
        if not smooth:
            for y in (0, 8):
                shade(a, y, x, 1.12)
    if not smooth:
        for y in (7, 15):
            for x in (2, 7, 12):
                rivet(a, y - 1, x)
    return a


def galvanized():
    a = canvas("#a7aeb2", noise=3)
    for _ in range(14):
        y, x = rng.randrange(16), rng.randrange(15)
        shade(a, y, x, 1.12)
        shade(a, y, x + 1, 1.08)
    frame(a, 1.1, 0.8)
    return a


def copper(smooth=False):
    a = canvas("#c47a4b", noise=6)
    if not smooth:
        frame(a, 1.2, 0.72)
        for y, x in ((2, 2), (2, 13), (13, 2), (13, 13)):
            rivet(a, y, x)
    return a


def panel(smooth=False, base="#a9b8b2"):
    a = canvas(base, noise=3)
    if not smooth:
        frame(a, 1.15, 0.78)
    return a


def glass_dark():
    a = canvas("#18211f", noise=2)
    for i in range(16):
        for j in range(3):
            if 0 <= i - j - 3 < 16:
                shade(a, i, min(15, i - j + 2), 1.5)
    frame(a, 1.3, 0.9)
    return a


def crane_yellow(smooth=False):
    a = canvas("#ddb032", noise=5)
    if not smooth:
        frame(a, 1.15, 0.72)
        for y, x in ((2, 2), (2, 13), (13, 2), (13, 13)):
            rivet(a, y, x)
    return a


def rope():
    a = canvas("#8e9088", noise=3)
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                shade(a, y, x, 0.65)
            elif (x + y) % 4 == 1:
                shade(a, y, x, 1.2)
    return a


def spring():
    a = canvas("#7a8a3c", noise=3)
    for y in range(16):
        k = (1.25, 1.05, 0.8, 0.55)[y % 4]
        for x in range(16):
            shade(a, y, x, k)
    return a


def tile():
    a = canvas("#e4e6e2", noise=3)
    for i in range(16):
        for g in (0, 4, 8, 12):
            shade(a, g, i, 0.8)
            shade(a, i, g, 0.8)
    return a


def louver():
    a = canvas("#5a6164", noise=3)
    for y in range(16):
        k = (1.25, 1.0, 0.45)[y % 3]
        for x in range(16):
            shade(a, y, x, k)
    frame(a, 1.1, 0.7)
    return a


def blades():  # кромки лопаток — для боковых граней дисков ступеней
    a = canvas("#b9bcbd", noise=3)
    for x in range(16):
        k = (1.25, 1.05, 0.7)[x % 3]
        for y in range(16):
            shade(a, y, x, k)
    return a


def cable():
    a = canvas("#2a2c2e", noise=3)
    for x in range(16):
        shade(a, 5, x, 1.5)
        shade(a, 6, x, 1.25)
        shade(a, 12, x, 0.7)
    return a


def epoxy():  # дезактивируемое эпоксидное покрытие
    a = canvas("#9fb3a1", noise=2)
    for i in range(3, 9):
        shade(a, i, i + 3, 1.18)
        shade(a, i + 1, i + 3, 1.08)
    return a


# ======================================================================
# reactor island
# ======================================================================
def moderator_tank():  # нержавеющий бак замедлителя (каландр), смотровой уровнемер
    a = steel_bare_like()
    for x in range(16):
        put(a, 0, x, "#8f9496")
        put(a, 15, x, "#6c7072")
    box(a, 11, 2, 13, 13, "#20302a")
    for y in range(7, 13):
        box(a, 12, y, 12, y, "#4a8fb5")  # уровень D2O/H2O
    kbox(a, 11, 2, 11, 13, 1.4)
    stripe_h(a, 4, 5, "#3f7a4a")  # маркировка «вода»
    return a


def steel_bare_like(base="#b4b8bb"):
    a = canvas(base, noise=3)
    for y in range(16):
        k = 1.0 + rng.uniform(-0.06, 0.06)
        for x in range(16):
            shade(a, y, x, k)
    return a


def control_rod_channel_top():  # графит + труба канала СУЗ с синей заглушкой
    a = canvas("#2e2f33", noise=4)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3.2:
                put(a, y, x, "#2f5f9e")
            elif d < 4.6:
                put(a, y, x, "#c9c4b6")
    shade(a, 6, 6, 1.4)
    return a


def neutron_detector_body():
    a = steel_bare_like("#c3c6c8")
    stripe_h(a, 2, 3, "#e8b923")
    stripe_h(a, 12, 13, "#e8b923")
    stripe_h(a, 7, 7, "#2b2f33")
    return a


def pipe_large():
    a = canvas("#3f7a4a", noise=5)
    for y in range(16):
        k = 1.15 if y in (2, 3, 4) else 0.78 if y > 12 else 1.0
        for x in range(16):
            shade(a, y, x, k)
    for x in range(5, 11):
        for y in range(16):
            put(a, y, x, "#e8b923")
            shade(a, y, x, 1.1 if y in (2, 3, 4) else 0.78 if y > 12 else 1.0)
    for y in range(16):
        put(a, y, 7, "#1d1d1f")
        put(a, y, 8, "#1d1d1f")
    for x in (1, 14):  # продольный сварной шов
        for y in range(16):
            shade(a, y, x, 0.9)
    return a


def pipe_large_end():
    a = canvas("#4b5157", noise=4)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5.6:
                put(a, y, x, "#141416")
            elif d < 6.8:
                shade(a, y, x, 1.3)
    for y, x in ((1, 1), (1, 13), (13, 1), (13, 13), (0, 7), (14, 7), (7, 0), (7, 14)):
        rivet(a, y, x)
    return a


def valve_check_side():
    a = canvas("#8c9a8e", noise=4)
    frame(a)
    for i in range(4, 12):  # стрелка направления потока
        put(a, 8, i, "#f2f2ea")
    for d in range(3):
        put(a, 8 - d, 11 - d, "#f2f2ea")
        put(a, 8 + d, 11 - d, "#f2f2ea")
    return a


def valve_control_positioner():
    a = canvas("#3e6b86", noise=3)
    frame(a, 1.2, 0.7)
    box(a, 4, 3, 11, 7, "#0f2a17")
    for x in range(5, 11):
        if x % 2:
            px(a, x, 5, "#47d16a")
    box(a, 6, 10, 9, 12, "#d6d2c4")
    return a


def pressurizer_shell():
    a = insulation(True)
    stripe_h(a, 2, 5, "#3f7a4a")
    stripe_h(a, 3, 4, "#e8b923")
    return a


def heaters_bottom():  # днище КД с вводами электронагревателей
    a = canvas("#4b5157", noise=4)
    for y in range(2, 15, 3):
        for x in range(2, 15, 3):
            put(a, y, x, "#b2302a")
            shade(a, y + 1 if y < 15 else y, x, 0.6)
    return a


def shell_h(bands):
    """Horizontal vessel shell (disc along X): features run as columns = rings around the vessel."""
    a = canvas("#c4c8c6", noise=3)
    for y in range(16):  # симметрично: грани north/south зеркальны по u
        shade(a, y, 0, 0.8)
        shade(a, y, 15, 0.8)
    for col, x0, x1 in bands + [(c, 15 - x1, 15 - x0) for c, x0, x1 in bands]:
        for x in range(x0, x1 + 1):
            for y in range(16):
                k = a[y, x, 0] / 196.0
                a[y, x] = C(col)
                a[y, x, :3] *= k
    return a


def steam_generator_shell():
    return shell_h([("#9c2f27", 3, 4)])


def steam_generator_collector():  # горловина коллектора 1К
    a = canvas("#5a6164", noise=4)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.5:
                put(a, y, x, "#7d847f")
                if d > 5.2:
                    shade(a, y, x, 1.25)
    for ang in range(0, 360, 45):
        rivet(a, int(7.5 + 4.3 * math.sin(math.radians(ang))), int(7.5 + 4.3 * math.cos(math.radians(ang))))
    trefoil_px(a, 7.5, 7.5, 2.5)
    return a


def steam_drum_shell():
    return shell_h([("#3f7a4a", 2, 3), ("#9c2f27", 7, 7)])


def turbine_casing():  # зелёно-серая окраска турбины, горизонтальный разъём
    a = canvas("#5e7b6c", noise=4)
    for x in range(16):
        put(a, 7, x, "#3b4a42")
        put(a, 8, x, "#7b9888")
        if x % 3 == 1:
            put(a, 6, x, "#a9b4ae")
            put(a, 9, x, "#a9b4ae")
    frame(a, 1.12, 0.75)
    return a


def turbine_casing_smooth():
    return canvas("#5e7b6c", noise=4)


def turbine_governor_front():
    a = canvas("#5e7b6c", noise=3)
    frame(a, 1.15, 0.72)
    circ(a, 7.5, 6, 4.2, "#2b2f33")
    circ(a, 7.5, 6, 3.4, "#ece8dc")
    px(a, 7, 6, "#1b1b1b")
    px(a, 8, 6, "#1b1b1b")
    for i, c in enumerate(("#d33b2c", "#e8b923", "#43b04a")):
        box(a, 3 + i * 4, 11, 4 + i * 4, 12, c)
    plate(a, 3, 13, 12, 14)
    return a


def condenser_tubesheet():
    a = canvas("#7d847f", noise=3)
    for y in range(1, 16, 3):
        for x in range(1 + (y // 3) % 2, 16, 3):
            put(a, y, x, "#1f2224")
            shade(a, y - 1, x, 1.2)
    frame(a, 1.15, 0.7)
    return a


def condenser_shell():
    a = canvas("#8c9a8e", noise=4)
    frame(a)
    for x in range(16):  # рёбра жёсткости
        for y in (4, 11):
            shade(a, y, x, 1.25)
            shade(a, y + 1, x, 0.7)
    stripe_h(a, 7, 8, "#3f7a4a")
    return a


def feedwater_pump_casing():
    a = canvas("#3f7a4a", noise=4)
    frame(a, 1.15, 0.72)
    for x in range(16):
        shade(a, 7, x, 0.7)
        shade(a, 8, x, 1.2)
    plate(a, 4, 11, 11, 13)
    return a


def hatch_door():  # круглая герметичная дверь шлюза
    a = canvas("#8c9a8e", noise=3)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 6.4 < d < 7.6:
                shade(a, y, x, 0.72)
            elif d < 2:
                put(a, y, x, "#b2302a")
    for ang in range(0, 360, 30):
        r = math.radians(ang)
        rivet(a, int(round(7.5 + 6 * math.sin(r))), int(round(7.5 + 6 * math.cos(r))))
    for i in range(16):
        shade(a, 0, i, 1.2)
    return a


def hatch_frame():
    a = canvas("#6f767a", noise=5)
    frame(a, 1.15, 0.7)
    hazard_rows(a, 0, 1)
    return a


def recombiner_front():  # ПАР: каталитические пластины за решёткой
    a = canvas("#b4b8bb", noise=3)
    frame(a, 1.2, 0.7)
    box(a, 2, 4, 13, 13, "#4a3f36")
    for x in range(3, 13, 2):
        for y in range(4, 14):
            put(a, y, x, "#8a7a64")
    plate(a, 4, 1, 11, 2)
    return a


def louver_top():
    a = louver()
    return a


def tank_shell(band, band2=None, marker=None):
    """Vertical tank shell for discs: every disc bar samples the middle columns, so
    bands run across the full width (band / band2 / band)."""
    a = canvas("#cfd2cd", noise=3)
    for x in range(16):
        put(a, 15, x, "#9a9e9b")
    stripe_h(a, 4, 7, band)
    if band2:
        stripe_h(a, 5, 6, band2)
    if marker:
        stripe_h(a, 10, 10, marker)
    return a


def tank_top():
    a = canvas("#bfc3be", noise=3)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.5 < d < 6.5:
                shade(a, y, x, 0.8)
    return a


def instrument_body(base, label):
    a = canvas(base, noise=3)
    frame(a, 1.2, 0.7)
    stripe_h(a, 2, 3, label)
    plate(a, 4, 9, 11, 11)
    return a


def igniter_front():
    a = canvas("#4b5157", noise=3)
    frame(a, 1.25, 0.65)
    box(a, 4, 4, 11, 11, "#1b1c1d")
    for y in range(4, 12, 2):
        for x in range(4, 12):
            put(a, y, x, "#6c7375")
    hazard_rows(a, 13, 14)
    return a


def startup_capsule():
    a = steel_bare_like("#c9cccd")
    stripe_h(a, 6, 9, "#e8b923")
    for y in (7, 8):
        for x in range(2, 14, 4):
            put(a, y, x, "#1d1d1f")
    return a


def penetration():  # проходка в гермооболочке: бетон + стальная гильза
    a = canvas("#9d9b95", noise=10)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4.5:
                put(a, y, x, "#141416")
            elif d < 6.3:
                put(a, y, x, "#7d847f")
                if d > 5.5:
                    shade(a, y, x, 1.25)
    for i in range(16):
        put(a, 0, i, "#7d847f")
    return a


def corium():
    a = canvas("#3a2620", noise=10)
    for _ in range(26):
        y, x = rng.randrange(16), rng.randrange(16)
        c = rng.choice(("#ff7a1a", "#ffb43a", "#d84a18"))
        put(a, y, x, c)
        if x + 1 < 16:
            put(a, y, x + 1, "#ff9a2e")
    for x in range(16):
        shade(a, (x * 7) % 16, x, 0.6)
    return a


def rack_teeth():
    a = canvas("#7c8184", noise=3)
    for y in range(16):
        if y % 2 == 0:
            for x in range(16):
                shade(a, y, x, 1.3)
    return a


# ======================================================================
# 03a — turbine hall / mechanics / ventilation / fuel handling / piping
# ======================================================================
def stop_valve_body():
    a = canvas("#5e7b6c", noise=4)
    frame(a, 1.15, 0.72)
    stripe_h(a, 11, 12, "#9c2f27")
    plate(a, 3, 3, 12, 5)
    return a


def servo_red():
    a = canvas("#b2302a", noise=4)
    for y in range(16):
        k = (1.2, 1.0, 1.0, 0.8)[y % 4]
        for x in range(16):
            shade(a, y, x, k)
    return a


def gearbox_housing():
    a = canvas("#5e7b6c", noise=4)
    frame(a, 1.15, 0.72)
    box(a, 3, 3, 12, 8, "#4c6457")  # смотровая крышка
    for y, x in ((3, 3), (3, 12), (8, 3), (8, 12)):
        rivet(a, y, x)
    circ(a, 7.5, 12, 2.2, "#2b2f33")  # маслоуказатель
    circ(a, 7.5, 12, 1.3, "#d3a246")
    return a


def generator_stator():
    a = canvas("#3e6b86", noise=4)
    for x in range(16):
        k = (1.25, 1.0, 0.65)[x % 3]
        for y in range(1, 15):
            shade(a, y, x, k)
    frame(a, 1.1, 0.7)
    plate(a, 4, 6, 11, 9)
    return a


def generator_terminal():
    a = canvas("#2f3133", noise=3)
    frame(a, 1.2, 0.7)
    for x in (4, 8, 12):
        circ(a, x - 0.5, 7.5, 1.6, "#c47a4b")
    hazard_rows(a, 12, 13)
    return a


def turning_gear_cover():
    a = canvas("#e2b230", noise=4)
    frame(a, 1.15, 0.72)
    for y in range(4, 12, 2):
        for x in range(3, 13):
            shade(a, y, x, 0.7)
    return a


def condensate_motor():
    a = canvas("#3e6b86", noise=4)
    for x in range(1, 16, 3):
        for y in range(16):
            shade(a, y, x, 0.7)
    stripe_h(a, 0, 1, "#2f3133")
    return a


def ratchet_face():
    a = canvas("#5a6164", noise=3)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            ang = math.degrees(math.atan2(y - 7.5, x - 7.5)) % 45
            if d < 7 and (d < 5.5 or ang < 22):
                put(a, y, x, "#d3a246")
                if d < 2:
                    put(a, y, x, "#3b3b3b")
    return a


def crane_cab():
    a = crane_yellow()
    box(a, 2, 2, 13, 8, "#203038")
    for i in range(2, 9):
        shade(a, i, i + 2, 1.6)
    box(a, 2, 10, 13, 13, "#2b2f33")
    for x in (3, 6, 9, 12):
        put(a, 11, x, rng.choice(("#d33b2c", "#43b04a", "#e8b923")))
    return a


def girder():  # коробчатая балка моста: полки, рёбра жёсткости
    a = canvas("#ddb032", noise=4)
    for x in range(16):
        for y in (0, 1):
            put(a, y, x, "#9a7a20")
        for y in (14, 15):
            put(a, y, x, "#8a6c1a")
        shade(a, 2, x, 1.15)
    for x in (1, 5, 9, 13):
        for y in range(2, 14):
            shade(a, y, x, 1.2)
            shade(a, y, x + 1, 0.72)
    for x in range(0, 16, 3):
        rivet(a, 0, x)
    return a


def rail():
    a = canvas("#5a6164", noise=4)
    for x in range(16):
        put(a, 5, x, "#c3c6c8")
        put(a, 6, x, "#9a9e9f")
        put(a, 10, x, "#34383b")
        if x % 4 == 0:
            rivet(a, 12, x)
    return a


def trolley_side():
    a = crane_yellow()
    for cx in (4, 11):
        circ(a, cx, 11.5, 2.6, "#2b2f33")
        circ(a, cx, 11.5, 1.2, "#9a9e9f")
    plate(a, 4, 3, 11, 5)
    return a


def mast_tube():
    a = steel_bare_like("#bfc3c5")
    for y in range(0, 16, 4):
        for x in range(4, 8):
            put(a, y, x, "#d33b2c" if y == 0 else "#1d1d1f")
    return a


def grapple_head():
    a = canvas("#7d847f", noise=3)
    frame(a, 1.2, 0.7)
    hazard_rows(a, 0, 1)
    box(a, 6, 6, 9, 9, "#d3a246")
    return a


def crane_girder_red():
    a = canvas("#c4552a", noise=5)
    frame(a, 1.15, 0.72)
    for x in range(16):
        shade(a, 3, x, 1.2)
        shade(a, 12, x, 0.75)
    plate(a, 5, 6, 10, 9, "#f2f2ea", "#c4552a")
    return a


def path_marker():
    a = canvas("#6c7072", noise=4)
    for y in range(16):
        for x in range(16):
            if abs(x - 7.5) + (15 - y) * 0.0 < 99 and 0 <= (y - abs(x - 7.5) * 1.0) - 2 <= 3 and 2 <= x <= 13:
                put(a, y, x, "#e8b923")
    for y in range(16):
        put(a, y, 0, "#e8b923")
        put(a, y, 15, "#e8b923")
    return a


def vent_duct():
    a = galvanized()
    for y in range(16):  # фальц
        shade(a, y, 7, 0.78)
        shade(a, y, 8, 1.15)
    stripe_h(a, 13, 13, "#3a6fb0")  # воздух — синий
    return a


def duct_inner():
    a = canvas("#3b3f42", noise=3)
    frame(a, 1.3, 0.7)
    return a


def damper_actuator():
    a = canvas("#2f3133", noise=3)
    frame(a, 1.2, 0.7)
    box(a, 4, 4, 11, 7, "#e8b923")
    px(a, 7, 5, "#1d1d1f")
    px(a, 8, 6, "#1d1d1f")
    return a


def hepa_face():
    a = canvas("#ece9de", noise=3)
    for x in range(16):
        k = 1.12 if x % 2 == 0 else 0.82
        for y in range(16):
            shade(a, y, x, k)
    for i in range(16):  # уплотнитель-рамка
        for j in (0, 15):
            put(a, j, i, "#8a8f91")
            put(a, i, j, "#8a8f91")
    for i in range(1, 15):
        put(a, 1, i, "#5c6164")
    return a


def charcoal_face():
    a = canvas("#2a2b2c", noise=4)
    for y in range(1, 15, 2):
        for x in range(1 + y % 4 // 2, 15, 2):
            put(a, y, x, "#0c0c0d")
            shade(a, y - 1, x, 1.35)
    for i in range(16):
        for j in (0, 15):
            put(a, j, i, "#8a8f91")
            put(a, i, j, "#8a8f91")
    return a


def filter_housing(label):
    a = galvanized()
    plate(a, 3, 3, 12, 5)
    stripe_h(a, 11, 12, label)
    return a


def vzc_front32():  # регистратор зоны: подложка дифманометра + светодиоды
    n = 32
    a = canvas("#a9b8b2", n, noise=3)
    frame(a, 1.15, 0.78)
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - 15.5, y - 13)
            if d < 9:
                a[y, x] = C("#ece8dc")
                if d > 8:
                    a[y, x, :3] *= 0.85
            elif d < 10.5:
                a[y, x] = C("#2b2f33")
    box(a, 15, 12, 16, 13, "#1b1b1b")
    for i, c in enumerate(("#43b04a", "#e8b923", "#d33b2c")):
        box(a, 8 + i * 7, 26, 10 + i * 7, 28, c)
    return a


def stack_stripes():
    a = canvas("#dcdcd6", noise=4)
    for y in range(8, 16):
        for x in range(16):
            put(a, y, x, "#c0392b")
            a[y, x, :3] *= rng.uniform(0.94, 1.04)
    for y in range(16):
        shade(a, y, 0, 0.85)
        shade(a, y, 15, 0.85)
    return a


def chem_pipe_tex(body, ring):
    a = canvas(body, noise=4)
    for x in range(16):
        for y in range(16):
            k = 1.15 if y in (4, 5) else 0.75 if y > 12 else 1.0
            shade(a, y, x, k)
    for x in range(6, 10):
        for y in range(16):
            put(a, y, x, ring)
            shade(a, y, x, 1.1 if y in (4, 5) else 0.75 if y > 12 else 1.0)
    return a


def adapter_body(prv=True):
    a = copper()
    if prv:
        box(a, 4, 3, 11, 5, "#b2302a")
        plate(a, 4, 9, 11, 11)
    else:
        hazard_rows(a, 3, 4)
        hazard_rows(a, 11, 12)
    return a


# ======================================================================
# control room (03b / 03c)
# ======================================================================
def dial_round32(face="#ece8dc", bezel="#9ea3a6", bg="#2b2f33", ring=None, face_r=13, bez_r=15):
    n = 32
    a = blank(n)
    a[:, :] = C(bg)
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - 15.5, y - 15.5)
            if d < face_r:
                a[y, x] = C(face)
                if d > face_r - 1.5:
                    a[y, x, :3] *= 0.85
                if ring and 9.5 < d < 11:
                    a[y, x] = C(ring)
            elif d < bez_r:
                a[y, x] = C(bezel)
                a[y, x, :3] *= 1.15 if y < 16 else 0.8
    box(a, 14, 14, 17, 17, "#1b1b1b")
    return a


def gauge270_32():  # щитовой прибор: квадратная рамка, круглая подложка, вырез 90° внизу
    a = dial_round32(face="#f1eee4", bezel="#1f2224", bg="#1f2224")
    for y in range(22, 30):
        for x in range(9, 23):
            d = math.hypot(x - 15.5, y - 15.5)
            if d < 11.5 and abs(x - 15.5) < (y - 15.5) * 0.95:
                a[y, x] = C("#c9c5b8")
    return a


def temp_dial32():
    a = dial_round32(face="#f0ead8", bezel="#c47a4b", face_r=10, bez_r=12.4)
    return a


def dose_dial32():
    a = dial_round32(face="#f3eed9", bezel="#e8b923", ring=None)
    trefoil_px(a, 15.5, 23.5, 3.2, bg=None)
    return a


def square_dial32(face="#ece8dc", frame_col="#2b2f33", window=(3, 3, 28, 28)):
    n = 32
    a = blank(n)
    a[:, :] = C(frame_col)
    x0, y0, x1, y1 = window
    box(a, x0, y0, x1, y1, face)
    kbox(a, x0, y0, x1, y0, 0.8)
    kbox(a, x0, y0, x0, y1, 0.8)
    for i in range(n):
        a[0, i, :3] *= 1.3
        a[i, 0, :3] *= 1.3
        a[n - 1, i, :3] *= 0.7
        a[i, n - 1, :3] *= 0.7
    return a


def flow_dial32():
    a = square_dial32()
    box(a, 14, 24, 17, 27, "#1b1b1b")  # ось секторной стрелки внизу
    return a


def level_dial32():
    a = square_dial32(frame_col="#3a3f44", window=(11, 2, 20, 29))
    for y in range(2, 30):
        a[y, 11, :3] *= 0.75
        a[y, 20, :3] *= 1.1
    return a


def boron_dial32():  # анализатор: окно цифрового табло + шкала-подложка
    a = square_dial32(face="#ece8dc", frame_col="#c8692a", window=(3, 14, 28, 28))
    box(a, 4, 3, 27, 11, "#14100c")
    for i in range(4, 28):
        a[3, i, :3] *= 1.4
    return a


def edgewise32():
    a = square_dial32(face="#f1eee4", frame_col="#1f2224", window=(10, 2, 21, 29))
    for y in range(2, 30):  # стекло
        a[y, 10, :3] *= 0.8
        a[y, 21, :3] *= 0.8
    return a


def chart_window32():
    a = square_dial32(face="#efe9d6", frame_col="#3a3f44", window=(3, 3, 28, 22))
    for y in range(24, 30):
        for x in range(3, 29):
            a[y, x] = C("#2b2f33")
    for i, c in enumerate(("#d33b2c", "#2f5f9e", "#43b04a")):
        box(a, 5 + i * 4, 26, 6 + i * 4, 27, c)
    for x in range(3, 29):  # перфорация ленты
        if x % 3 == 0:
            a[4, x, :3] *= 0.75
            a[21, x, :3] *= 0.75
    for i in range(3, 20):  # блик стекла
        if 3 + i < 29:
            a[i + 1, 3 + i, :3] = np.minimum(255, a[i + 1, 3 + i, :3] * 1.06)
    return a


def digital_glass32():
    a = square_dial32(face="#170b09", frame_col="#2b2f33", window=(2, 8, 29, 23))
    for x in range(2, 30):  # тёмное стекло с бликом
        a[8, x, :3] = C("#3a2522")[:3]
    return a


def annunciator32():
    n = 32
    a = blank(n)
    a[:, :] = C("#2b2f33")
    for r in range(3):
        for c in range(4):
            x0, y0 = 1 + c * 8, 1 + r * 8
            box(a, x0, y0, x0 + 6, y0 + 6, "#e9e4d4")
            kbox(a, x0, y0, x0 + 6, y0, 1.06)
            kbox(a, x0, y0 + 6, x0 + 6, y0 + 6, 0.82)
    for i, col in enumerate(("#d6d2c4", "#d6d2c4", "#d6d2c4")):  # КВИТ / ЗВУК / ПРОВ
        box(a, 3 + i * 10, 26, 8 + i * 10, 29, col)
        kbox(a, 3 + i * 10, 29, 8 + i * 10, 29, 0.7)
    return a


def mimic(form):
    a = canvas("#a9b8b2", noise=2)
    frame(a, 1.1, 0.85)
    ink = "#2b2f33"
    if form in ("pipe", "pump", "valve", "control_valve", "check_valve", "heat_exchanger", "turbine", "tee"):
        box(a, 0, 7, 15, 8, ink)
    if form == "bend":
        box(a, 0, 7, 8, 8, ink)
        box(a, 7, 7, 8, 15, ink)
    if form == "tee":
        box(a, 7, 8, 8, 15, ink)
    if form == "pump":
        circ(a, 7.5, 7.5, 4.6, ink)
        circ(a, 7.5, 7.5, 3.5, "#a9b8b2")
        box(a, 6, 4, 8, 4, ink)
        line(a, 5, 9, 10, 9, ink)
    if form in ("valve", "control_valve"):
        for i in range(5):
            box(a, 3 + i, 5 + i // 2, 3 + i, 10 - i // 2, ink)
            box(a, 12 - i, 5 + i // 2, 12 - i, 10 - i // 2, ink)
        if form == "control_valve":
            box(a, 7, 2, 8, 6, ink)
            box(a, 5, 1, 10, 2, ink)
    if form == "check_valve":
        for i in range(5):
            box(a, 4 + i, 5 + i // 2, 4 + i, 10 - i // 2, ink)
        box(a, 10, 4, 10, 11, ink)
    if form == "tank":
        box(a, 3, 2, 12, 13, ink)
        box(a, 4, 3, 11, 12, "#a9b8b2")
        box(a, 4, 8, 11, 12, "#7f9a95")
    if form == "heat_exchanger":
        box(a, 2, 4, 13, 11, ink)
        box(a, 3, 5, 12, 10, "#a9b8b2")
        for x in range(3, 13):
            px(a, x, 7 + (x % 2), ink)
    if form == "turbine":
        for x in range(4, 12):
            h = 2 + (x - 4) // 2
            box(a, x, 7 - h, x, 8 + h, ink)
    if form == "reactor":
        box(a, 4, 1, 11, 14, ink)
        box(a, 5, 2, 10, 13, "#a9b8b2")
        for x in (6, 9):
            box(a, x, 4, x, 11, ink)
    if form == "label":
        box(a, 2, 5, 13, 10, "#e6e2d2")
        kbox(a, 2, 10, 13, 10, 0.8)
    return a


def key_plate():
    a = canvas("#1f2224", noise=2)
    frame(a, 1.3, 0.8)
    for x, y in ((3, 4), (4, 3), (12, 4), (11, 3)):  # метки «ОТКЛ/ВКЛ»
        px(a, x, y, "#f2f2ea")
    box(a, 7, 2, 8, 3, "#f2f2ea")
    return a


def selector_plate():
    a = canvas("#1f2224", noise=2)
    frame(a, 1.3, 0.8)
    for ang in (-60, -20, 20, 60):
        r = math.radians(ang)
        px(a, int(round(7.5 + 6 * math.sin(r))), int(round(7.5 - 6 * math.cos(r))), "#f2f2ea")
    return a


def pushbutton_bezel():
    a = canvas("#2b2f33", noise=2)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5 < d < 7:
                put(a, y, x, "#b4b8bb")
                shade(a, y, x, 1.2 if y < 8 else 0.8)
    return a


def scram_plate():
    a = canvas("#e8b923", noise=3)
    for y in range(16):
        for x in range(16):
            if x < 2 or x > 13 or y < 2 or y > 13:
                if ((x + y) // 2) % 2:
                    put(a, y, x, "#1d1d1f")
    box(a, 4, 12, 11, 13, "#b2302a")
    return a


def bypass_plate():
    a = canvas("#e9e6da", noise=2)
    frame(a, 1.1, 0.75)
    box(a, 1, 1, 14, 3, "#e8b923")
    circ(a, 7.5, 9, 3.2, "#9ea3a6")
    box(a, 7, 7, 8, 11, "#1b1b1b")
    return a


def horn_grille():
    a = canvas("#2b2f33", noise=2)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if int(d) % 2 == 0:
                shade(a, y, x, 1.6)
    return a


def desk_top():
    a = panel(base="#a9b8b2")
    for x in range(16):  # окантовка нижнего края — поручень пульта
        put(a, 15, x, "#3c4447")
        put(a, 14, x, "#59656a")
    return a


def mcr_core_front():
    a = canvas("#3b4046", noise=3)
    frame(a, 1.25, 0.65)
    box(a, 2, 2, 13, 7, "#0f2a17")
    for x in range(3, 13):
        if rng.random() < 0.6:
            px(a, x, 4, "#47d16a")
    box(a, 4, 10, 11, 11, "#141416")  # щель для ключ-карты
    kbox(a, 4, 11, 11, 11, 1.8)
    for i, c in enumerate(("#43b04a", "#e8b923", "#d33b2c")):
        box(a, 4 + i * 3, 13, 5 + i * 3, 13, c)
    return a


def rsp32():  # резервный щит: мини-приборы, ключи, лампы, красная полоса РЩУ
    n = 32
    a = canvas("#a9b8b2", n, noise=3)
    frame(a, 1.15, 0.78)
    box(a, 1, 1, 30, 4, "#b2302a")
    for i in range(3):
        cx = 6 + i * 10
        for y in range(6, 18):
            for x in range(cx - 5, cx + 6):
                d = math.hypot(x - cx, y - 11.5)
                if d < 4.5:
                    px(a, x, y, "#ece8dc")
                elif d < 5.5:
                    px(a, x, y, "#1f2224")
    for i, c in enumerate(("#d33b2c", "#e8b923", "#43b04a", "#f4f1e0", "#d33b2c", "#43b04a")):
        box(a, 3 + i * 5, 20, 5 + i * 5, 22, c)
    for i in range(4):
        box(a, 4 + i * 7, 25, 7 + i * 7, 29, "#1f2224")
        box(a, 5 + i * 7, 26, 6 + i * 7, 28, "#d6d2c4")
    return a


def transfer_plate():
    a = canvas("#e9e6da", noise=2)
    frame(a, 1.1, 0.75)
    box(a, 2, 2, 6, 4, "#2f5f9e")  # БЩУ
    box(a, 9, 2, 13, 4, "#b2302a")  # РЩУ
    circ(a, 7.5, 9.5, 4, "#2b2f33")
    return a


def io_front():
    a = canvas("#c4bfaa", noise=3)
    frame(a)
    for r, y in enumerate((3, 7, 11)):
        box(a, 2, y, 13, y + 2, "#3a3a36")
        for x in range(2, 14, 2):
            put(a, y + 1, x, "#9ea3a6")
            put(a, y, x + 1, rng.choice(("#43b04a", "#43b04a", "#e8b923", "#3a3a36")))
    plate(a, 4, 14, 11, 14)
    return a


def watchdog_front():
    a = canvas("#cfcfc8", noise=2)
    frame(a, 1.1, 0.75)
    box(a, 3, 2, 12, 6, "#2f5f9e")
    for i, c in enumerate(("#43b04a", "#d33b2c")):
        box(a, 4 + i * 6, 8, 5 + i * 6, 9, c)
    for x in range(3, 13, 3):  # клеммы
        box(a, x, 12, x + 1, 13, "#9ea3a6")
    return a


def relay_ctrl_front():
    a = canvas("#cfcfc8", noise=2)
    frame(a, 1.1, 0.75)
    box(a, 2, 2, 13, 5, "#14100c")
    for x in (3, 5, 9, 11):
        px(a, x, 3, "#ff4a36")
    box(a, 3, 8, 6, 11, "#2b2f33")
    box(a, 9, 8, 12, 11, "#2b2f33")
    px(a, 4, 9, "#e8b923")
    px(a, 10, 10, "#e8b923")
    return a


def cable_tex():
    a = canvas("#5f6a70", noise=3)
    for x in range(16):
        shade(a, 6, x, 1.3)
        shade(a, 10, x, 0.7)
        if x % 5 == 0:
            for y in range(16):
                shade(a, y, x, 0.85)
    return a


def sensor_body(base, accent):  # для тел вращения — без рамки
    a = canvas(base, noise=3)
    stripe_h(a, 6, 7, accent)
    return a


# ======================================================================
# radiation safety (04b)
# ======================================================================
def lead_bricks():  # свинцовые кирпичи «ласточкин хвост»
    a = canvas("#5c6272", noise=4)
    for x in range(16):
        shade(a, 7, x, 0.62)
        shade(a, 15, x, 0.62)
        shade(a, 0, x, 1.15)
        shade(a, 8, x, 1.15)
    for y in range(0, 7):
        shade(a, y, 7, 0.62)
    for y in range(8, 15):
        shade(a, y, 2, 0.62)
        shade(a, y, 12, 0.62)
    return a


def lead_sheet():
    a = canvas("#626878", noise=3)
    for y in range(16):
        k = 1.0 + 0.08 * math.sin(y * 1.3)
        for x in range(16):
            shade(a, y, x, k)
    frame(a, 1.15, 0.8)
    return a


def lead_glass():
    a = blank()
    a[:, :] = C("#d9c46a")
    a[:, :, 3] = 150
    for i in range(16):
        for j in (0, 15):
            a[j, i] = C("#5c6272")
            a[i, j] = C("#5c6272")
    for i in range(3, 9):
        a[i, i + 2, :3] = C("#fff2b0")[:3]
        a[i, i + 2, 3] = 190
    return a


def heavy_concrete():
    a = canvas("#7b7a76", noise=9)
    for _ in range(22):
        y, x = rng.randrange(16), rng.randrange(16)
        put(a, y, x, rng.choice(("#3a3735", "#4a4440", "#2e2c2b")))
    for i in range(16):
        shade(a, 5, i, 0.85)
    return a


def polyethylene(speck=None):
    a = canvas("#ece9df", noise=3)
    if speck:
        for _ in range(16):
            put(a, rng.randrange(16), rng.randrange(16), speck)
    frame(a, 1.04, 0.9)
    return a


def borated_pe():
    a = polyethylene("#7fae8f")
    box(a, 5, 5, 6, 10, "#2f6b45")  # трафарет «B»
    box(a, 7, 5, 9, 5, "#2f6b45")
    box(a, 7, 7, 9, 7, "#2f6b45")
    box(a, 7, 10, 9, 10, "#2f6b45")
    px(a, 10, 6, "#2f6b45")
    px(a, 10, 8, "#2f6b45")
    px(a, 10, 9, "#2f6b45")
    return a


def tungsten():
    a = canvas("#575c63", noise=3)
    for y in range(16):
        for x in range(16):
            if (x // 2 + y // 2) % 2:
                shade(a, y, x, 1.08)
    frame(a, 1.25, 0.7)
    return a


def depleted_u():
    a = canvas("#38393b", noise=4)
    for _ in range(14):
        y, x = rng.randrange(16), rng.randrange(16)
        put(a, y, x, rng.choice(("#6f7a3a", "#8a9142", "#4d5530")))
    frame(a, 1.2, 0.7)
    return a


def steel_plate_block():  # пакет стальных листов, видны торцы
    a = canvas("#71777b", noise=3)
    for y in range(0, 16, 4):
        for x in range(16):
            shade(a, y, x, 1.25)
            shade(a, y + 3, x, 0.7)
    for y in range(2, 16, 4):
        rivet(a, y, 2)
        rivet(a, y, 13)
    return a


def portal_panel():
    a = canvas("#d8d6cc", noise=2)
    frame(a, 1.1, 0.75)
    box(a, 3, 2, 12, 4, "#14100c")
    for x in range(4, 11, 2):
        px(a, x, 3, "#47d16a")
    box(a, 3, 6, 12, 13, "#b9b7ad")
    grille(a, 3, 6, 12, 13, 2, 0.75)
    trefoil_px(a, 7.5, 14.3, 1.6)
    return a


def handfoot_front():
    a = canvas("#d8d6cc", noise=2)
    frame(a, 1.1, 0.75)
    box(a, 2, 2, 13, 5, "#14100c")
    for x in (3, 5, 10, 12):
        px(a, x, 3, "#47d16a")
    box(a, 2, 7, 6, 11, "#2b2f33")  # карманы для рук
    box(a, 9, 7, 13, 11, "#2b2f33")
    for i, c in enumerate(("#43b04a", "#d33b2c")):
        box(a, 6 + i * 2, 13, 6 + i * 2, 13, c)
    return a


def foot_plate():
    a = canvas("#b4b8bb", noise=2)
    for y in range(16):
        for x in range(16):
            if (x in range(2, 7) or x in range(9, 14)) and 2 <= y <= 13:
                put(a, y, x, "#3a3f44")
                if (x + y) % 2:
                    shade(a, y, x, 1.3)
    return a


def wbc_panel():
    a = lead()
    a2 = canvas("#d8d6cc", noise=2)
    a[2:7, 2:14] = a2[2:7, 2:14]
    box(a, 3, 3, 12, 5, "#14100c")
    for x in range(4, 12, 2):
        px(a, x, 4, "#47d16a")
    trefoil_px(a, 7.5, 10.5, 3.0)
    return a


def lead():
    a = canvas("#5c6272", noise=5)
    frame(a, 1.15, 0.8)
    return a


def stack_monitor_front():
    a = canvas("#c4bfaa", noise=3)
    frame(a)
    box(a, 2, 2, 13, 5, "#0f2a17")
    for x in range(3, 13):
        if rng.random() < 0.6:
            px(a, x, 3, "#47d16a")
    for i, c in enumerate(("#e8b923", "#7a8a3c", "#2f5f9e")):  # фильтры: аэрозоль/йод/газ
        circ(a, 3.5 + i * 4.5, 10, 1.8, "#2b2f33")
        circ(a, 3.5 + i * 4.5, 10, 1.0, c)
    trefoil_px(a, 12, 14, 1.5)
    return a


def tld_front():
    a = canvas("#d8d6cc", noise=2)
    frame(a, 1.1, 0.75)
    box(a, 2, 2, 9, 7, "#0f2a17")
    for x in range(3, 9):
        if x % 2:
            px(a, x, 4, "#47d16a")
    box(a, 2, 10, 13, 12, "#9ea3a6")  # лоток кассет
    kbox(a, 2, 12, 13, 12, 0.7)
    box(a, 11, 3, 13, 4, "#43b04a")
    return a


def medical_front():
    a = canvas("#e9e9e3", noise=2)
    frame(a, 1.05, 0.78)
    box(a, 6, 2, 9, 9, "#2f9a55")  # зелёный крест (первая помощь)
    box(a, 4, 4, 11, 7, "#2f9a55")
    for y in (11, 13):
        box(a, 2, y, 13, y, "#b9b9b1")
        px(a, 7, y, "#5c6164")
        px(a, 8, y, "#5c6164")
    return a


def zone_ctrl_front():
    a = canvas("#3b4046", noise=3)
    frame(a, 1.25, 0.65)
    trefoil_px(a, 7.5, 5.5, 3.6)
    for i, c in enumerate(("#43b04a", "#e8b923", "#ff9a2e", "#d33b2c")):  # классы зон
        box(a, 2 + i * 3, 11, 3 + i * 3, 12, c)
    box(a, 2, 14, 13, 14, "#14100c")
    return a


def door_half(top):
    a = canvas("#c9ccc4", noise=2)
    frame(a, 1.1, 0.75)
    if top:
        box(a, 4, 3, 11, 9, "#203038")
        for i in range(4, 9):
            px(a, i + 1, i, "#4a6a78")
        trefoil_px(a, 7.5, 13, 2.4)
    else:
        hazard_rows(a, 13, 14)
        box(a, 11, 2, 13, 3, "#2b2f33")  # ручка
        box(a, 2, 5, 5, 8, "#14100c")  # считыватель EPD
        px(a, 3, 6, "#43b04a")
    return a


def checkpoint_front():
    a = canvas("#d8d6cc", noise=2)
    frame(a, 1.1, 0.75)
    box(a, 2, 2, 13, 7, "#0f2a17")
    for y in (3, 5):
        for x in range(3, 13):
            if rng.random() < 0.6:
                px(a, x, y, "#47d16a")
    box(a, 2, 9, 7, 13, "#2f9a55")  # чистая зона
    box(a, 8, 9, 13, 13, "#e8b923")  # грязная зона
    trefoil_px(a, 10.5, 11, 1.8, bg=None)
    return a


def locker_front(clean):
    a = canvas("#e4e4dc" if clean else "#d9d2b8", noise=2)
    frame(a, 1.08, 0.78)
    for y in range(16):
        put(a, y, 7, "#8a8f91")
    for x in (2, 3, 4, 10, 11, 12):  # вентиляционные прорези
        for y in (2, 4):
            put(a, y, x, "#5c6164")
    stripe_h(a, 13, 14, "#2f9a55" if clean else "#e8b923")
    if not clean:
        trefoil_px(a, 3.5, 8.5, 2.2)
    for x in (5, 9):
        box(a, x, 8, x + 1, 8, "#3a3a36")
    return a


def shower_head():
    a = canvas("#c3c6c8", noise=2)
    for y in range(2, 14, 2):
        for x in range(2, 14, 2):
            if math.hypot(x - 7.5, y - 7.5) < 6.2:
                put(a, y, x, "#3b4046")
    return a


def drain_top():
    a = tile()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5:
                put(a, y, x, "#9ea3a6" if (x % 2 == 0) else "#1b1c1d")
            elif d < 6.5:
                put(a, y, x, "#e8b923")
    return a


def bath_front(accent):
    a = steel_bare_like("#c3c6c8")
    frame(a, 1.15, 0.75)
    box(a, 3, 3, 12, 7, "#2b2f33")
    box(a, 4, 4, 8, 6, "#0f2a17")
    px(a, 10, 5, accent)
    px(a, 11, 5, "#43b04a")
    stripe_h(a, 12, 12, accent)
    return a


def liquid_top(col):
    a = canvas(col, noise=6)
    for _ in range(8):
        shade(a, rng.randrange(16), rng.randrange(16), 1.25)
    return a


def scabbler_housing():
    a = canvas("#e2b230", noise=4)
    frame(a, 1.15, 0.72)
    plate(a, 3, 3, 12, 5)
    hazard_rows(a, 13, 14)
    return a


def laundry_front():
    a = canvas("#e4e4dc", noise=2)
    frame(a, 1.08, 0.78)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8.5)
            if d < 4.6:
                put(a, y, x, "#3b4a52")
                if (x + y) % 4 == 0:
                    shade(a, y, x, 1.5)
            elif d < 6:
                put(a, y, x, "#9ea3a6")
    box(a, 2, 1, 6, 1, "#14100c")
    px(a, 12, 1, "#e8b923")
    trefoil_px(a, 12.5, 14, 1.4, bg=None)
    return a


def drum_perforated():
    a = steel_bare_like("#b9bdbf")
    for y in range(1, 16, 3):
        for x in range(1 + y % 2, 16, 3):
            put(a, y, x, "#3b3f42")
    return a


def air_port_plate():
    a = canvas("#3a6fb0", noise=3)
    frame(a, 1.2, 0.72)
    circ(a, 7.5, 5, 3, "#2b2f33")
    circ(a, 7.5, 5, 2, "#ece8dc")
    px(a, 7, 5, "#1b1b1b")
    plate(a, 3, 11, 12, 13)
    return a


# ======================================================================
# items (flat icons 16x16)
# ======================================================================
@icon
def i_geiger(a):
    box(a, 3, 5, 12, 13, "#e0c34a")
    box(a, 4, 6, 9, 9, "#2b2f33")
    box(a, 5, 7, 8, 8, "#c9e3a4")
    circ(a, 10.5, 11, 1.5, "#2b2f33")
    box(a, 5, 2, 11, 4, "#5c6164")  # ручка
    box(a, 6, 3, 10, 4, blank_px())
    box(a, 1, 9, 2, 14, "#9ea3a6")  # зонд на кабеле
    line(a, 2, 13, 3, 12, "#2b2f33")


def blank_px():
    return np.zeros(4)


@icon
def i_dosimeter(a):
    box(a, 4, 2, 11, 14, "#3b4046")
    box(a, 5, 3, 10, 6, "#b9d6a0")
    for x in (6, 8):
        px(a, x, 4, "#1f2a1a")
    for y, x in ((8, 6), (8, 9), (10, 6), (10, 9)):
        box(a, x, y, x + 1, y, "#d6d2c4")
    box(a, 6, 12, 9, 13, "#e8b923")


@icon
def i_contam_meter(a):
    box(a, 1, 3, 7, 8, "#e0c34a")
    box(a, 2, 4, 5, 6, "#c9e3a4")
    box(a, 8, 10, 15, 13, "#5c6164")  # «блин»-зонд
    box(a, 9, 10, 14, 10, "#9ea3a6")
    line(a, 4, 9, 9, 12, "#2b2f33")


@icon
def i_epd(a):
    box(a, 5, 3, 11, 13, "#2f3133")
    box(a, 6, 4, 10, 7, "#9fc3d6")
    px(a, 7, 5, "#203038")
    px(a, 9, 5, "#203038")
    box(a, 7, 10, 9, 11, "#e8b923")
    box(a, 7, 1, 9, 2, "#9ea3a6")


@icon
def i_tld(a):
    box(a, 4, 3, 11, 12, "#f0eee6")
    box(a, 5, 6, 10, 10, "#c8692a")
    box(a, 6, 7, 7, 8, "#e8b923")
    box(a, 7, 1, 8, 2, "#9ea3a6")


@icon
def i_smear(a):
    box(a, 2, 6, 13, 13, "#cfc9b3")
    for i in range(4):
        circ(a, 4.5 + i * 2.5, 9.5, 1.2, "#f6f4ec")
    box(a, 2, 4, 13, 5, "#8f8a73")


@icon
def i_respirator(a):
    box(a, 4, 6, 11, 11, "#e6e3d9")
    box(a, 5, 11, 10, 12, "#c9c5b8")
    circ(a, 4, 10, 2.2, "#c0392b")
    circ(a, 11, 10, 2.2, "#c0392b")
    box(a, 1, 5, 3, 5, "#3b3f42")
    box(a, 12, 5, 14, 5, "#3b3f42")


@icon
def i_ff_respirator(a):
    box(a, 4, 2, 11, 12, "#2b2f33")
    box(a, 5, 3, 10, 8, "#9fc3d6")
    circ(a, 7.5, 12, 2.6, "#7a8a3c")
    px(a, 6, 4, "#e6f2f6")


@icon
def i_iodine_cart(a):
    circ(a, 7.5, 7.5, 5.5, "#7a8a3c")
    circ(a, 7.5, 7.5, 3.5, "#5a6a2a")
    circ(a, 7.5, 7.5, 1.5, "#2b2f33")
    box(a, 6, 0, 9, 2, "#9ea3a6")


@icon
def i_papr(a):
    box(a, 3, 2, 12, 12, "#e8b923")
    box(a, 5, 4, 10, 8, "#9fc3d6")
    box(a, 4, 13, 11, 14, "#2b2f33")  # блок вентилятора
    box(a, 1, 9, 2, 13, "#2b2f33")


@icon
def i_pn_helmet(a):
    circ(a, 7.5, 7.5, 6, "#e8e6de")
    circ(a, 7.5, 7.5, 3.6, "#9fc3d6")
    px(a, 6, 6, "#e6f2f6")
    box(a, 4, 13, 11, 14, "#3a6fb0")


@icon
def i_pn_jacket(a):
    box(a, 4, 2, 11, 13, "#e8e6de")
    box(a, 1, 3, 3, 10, "#e8e6de")
    box(a, 12, 3, 14, 10, "#e8e6de")
    box(a, 7, 2, 8, 13, "#c9c5b8")
    box(a, 9, 4, 10, 5, "#3a6fb0")


@icon
def i_pn_trousers(a):
    box(a, 3, 2, 12, 5, "#e8e6de")
    box(a, 3, 6, 6, 14, "#e8e6de")
    box(a, 9, 6, 12, 14, "#e8e6de")
    box(a, 3, 2, 12, 2, "#c9c5b8")


@icon
def i_pn_boots(a):
    box(a, 2, 5, 5, 12, "#e8e6de")
    box(a, 2, 12, 7, 13, "#2b2f33")
    box(a, 9, 5, 12, 12, "#e8e6de")
    box(a, 9, 12, 14, 13, "#2b2f33")


@icon
def i_overalls(a):
    box(a, 4, 1, 11, 9, "#f2f1ea")
    box(a, 1, 2, 3, 8, "#f2f1ea")
    box(a, 12, 2, 14, 8, "#f2f1ea")
    box(a, 4, 10, 7, 14, "#f2f1ea")
    box(a, 8, 10, 11, 14, "#f2f1ea")
    box(a, 7, 1, 8, 9, "#d8d6cc")
    box(a, 4, 9, 11, 9, "#2f9a55")


@icon
def i_bootcovers(a):
    box(a, 2, 7, 6, 12, "#7fb6d9")
    box(a, 2, 12, 7, 13, "#5f96b9")
    box(a, 9, 7, 13, 12, "#7fb6d9")
    box(a, 9, 12, 14, 13, "#5f96b9")


@icon
def i_lead_apron(a):
    box(a, 4, 3, 11, 14, "#3d5a8a")
    box(a, 3, 5, 3, 13, "#3d5a8a")
    box(a, 12, 5, 12, 13, "#3d5a8a")
    box(a, 5, 1, 6, 2, "#2b2f33")
    box(a, 9, 1, 10, 2, "#2b2f33")
    box(a, 6, 7, 9, 9, "#e8b923")


@icon
def i_ki(a):
    box(a, 3, 3, 12, 12, "#f0eee6")
    for y in (5, 9):
        for x in (5, 9):
            circ(a, x + 0.5, y + 0.5, 1.6, "#ffffff")
            px(a, x, y, "#d6d2c4")
    box(a, 3, 3, 12, 3, "#2f5f9e")


@icon
def i_prussian(a):
    box(a, 5, 3, 10, 13, "#f0eee6")
    box(a, 5, 6, 10, 10, "#1f3f8a")
    box(a, 6, 1, 9, 2, "#2b2f33")


@icon
def i_dtpa(a):
    box(a, 6, 2, 9, 13, "#dfe9ee")
    box(a, 6, 7, 9, 13, "#9fc3d6")
    box(a, 6, 0, 9, 1, "#5c6164")
    box(a, 6, 5, 9, 5, "#c0392b")


@icon
def i_antiemetic(a):
    box(a, 3, 4, 12, 11, "#c9d6e0")
    for x in (5, 9):
        circ(a, x + 0.5, 7.5, 1.7, "#f2f1ea")


@icon
def i_cytokine(a):
    box(a, 2, 7, 11, 9, "#dfe9ee")
    box(a, 4, 7, 9, 9, "#e8b0c0")
    box(a, 12, 8, 15, 8, "#9ea3a6")
    box(a, 0, 6, 1, 10, "#5c6164")


@icon
def i_blood(a):
    box(a, 4, 3, 11, 12, "#a01c22")
    box(a, 5, 4, 10, 6, "#f2f1ea")
    box(a, 7, 13, 8, 15, "#c9c5b8")
    box(a, 6, 1, 9, 2, "#c9c5b8")


@icon
def i_bmt(a):
    box(a, 3, 4, 12, 13, "#e9e9e3")
    box(a, 3, 4, 12, 5, "#2f9a55")
    box(a, 6, 7, 9, 11, "#c0392b")
    box(a, 5, 8, 10, 10, "#c0392b")


@icon
def i_record(a):
    box(a, 3, 2, 12, 14, "#efe9d6")
    for y in (5, 7, 9, 11):
        box(a, 5, y, 10, y, "#7a7466")
    box(a, 3, 2, 12, 3, "#2f9a55")


@icon
def i_sprayer(a):
    box(a, 3, 6, 8, 14, "#e8b923")
    box(a, 4, 3, 7, 5, "#5c6164")
    line(a, 7, 4, 13, 2, "#2b2f33")
    px(a, 14, 1, "#9fc3d6")
    px(a, 14, 3, "#9fc3d6")


@icon
def i_lance(a):
    line(a, 2, 13, 12, 3, "#9ea3a6")
    line(a, 3, 13, 13, 3, "#b4b8bb")
    box(a, 1, 12, 4, 14, "#b2302a")
    px(a, 14, 1, "#e9eef0")
    px(a, 15, 2, "#e9eef0")


@icon
def i_strippable(a):
    box(a, 4, 4, 11, 14, "#9fb3a1")
    box(a, 4, 4, 11, 6, "#5c6164")
    box(a, 6, 9, 9, 11, "#f2f1ea")
    box(a, 5, 2, 10, 3, "#9ea3a6")


@icon
def i_wiring_tool(a):
    line(a, 3, 12, 9, 6, "#e8b923")
    line(a, 4, 12, 10, 6, "#c89a1b")
    box(a, 10, 2, 13, 5, "#5c6164")
    px(a, 13, 1, "#b4b8bb")
    for i in range(4):
        px(a, 2 + i, 14 - i // 2, "#c0392b")


@icon
def i_bypass_key(a):
    circ(a, 4.5, 4.5, 3.6, "#e8b923")
    circ(a, 4.5, 4.5, 1.4, blank_px())
    line(a, 7, 7, 13, 13, "#c89a1b")
    line(a, 7, 8, 13, 14, "#c89a1b")
    px(a, 11, 13, "#c89a1b")
    px(a, 12, 11, "#c89a1b")


@icon
def i_operator_key(a):
    circ(a, 4.5, 4.5, 3.6, "#2f5f9e")
    circ(a, 4.5, 4.5, 1.4, blank_px())
    line(a, 7, 7, 13, 13, "#b4b8bb")
    line(a, 7, 8, 13, 14, "#b4b8bb")
    px(a, 11, 13, "#b4b8bb")


@icon
def i_access_card(a):
    box(a, 1, 4, 14, 12, "#e9e6da")
    box(a, 1, 4, 14, 5, "#2f5f9e")
    box(a, 3, 7, 5, 10, "#d3a246")
    for y in (8, 10):
        box(a, 7, y, 12, y, "#7a7466")


@icon
def i_calibrator(a):
    box(a, 3, 2, 12, 14, "#c8692a")
    box(a, 4, 3, 11, 7, "#14100c")
    for x in (5, 7, 9):
        px(a, x, 5, "#ff4a36")
    box(a, 5, 9, 6, 10, "#2b2f33")
    box(a, 9, 9, 10, 10, "#2b2f33")
    box(a, 6, 15, 6, 15, "#c0392b")
    box(a, 9, 15, 9, 15, "#1d1d1f")


@icon
def i_chart_paper(a):
    box(a, 3, 1, 12, 14, "#efe9d6")
    for y in range(2, 14, 3):
        box(a, 3, y, 12, y, "#d8cfb4")
    line(a, 4, 12, 7, 6, "#d33b2c")
    line(a, 7, 6, 11, 9, "#d33b2c")
    line(a, 4, 9, 11, 4, "#2f5f9e")


@icon
def i_vibrometer(a):
    box(a, 2, 3, 9, 13, "#e8b923")
    box(a, 3, 4, 8, 7, "#0f2a17")
    line(a, 3, 6, 5, 5, "#47d16a")
    line(a, 5, 5, 8, 6, "#47d16a")
    line(a, 9, 10, 13, 12, "#2b2f33")
    box(a, 13, 11, 15, 13, "#9ea3a6")


@icon
def i_stethoscope(a):
    line(a, 2, 13, 9, 6, "#b4b8bb")
    line(a, 2, 14, 9, 7, "#9ea3a6")
    box(a, 9, 3, 11, 6, "#5c6164")
    circ(a, 12.5, 3.5, 2.2, "#2b2f33")
    circ(a, 2.5, 13.5, 1.8, "#d3a246")


@icon
def i_lubricant(a):
    box(a, 4, 5, 11, 14, "#3a3f44")
    box(a, 5, 7, 10, 10, "#e8b923")
    box(a, 6, 2, 7, 4, "#b4b8bb")
    line(a, 7, 2, 11, 0, "#b4b8bb")


@icon
def i_bearing(a):
    circ(a, 7.5, 7.5, 6.5, "#b4b8bb")
    circ(a, 7.5, 7.5, 5, "#5c6164")
    for ang in range(0, 360, 45):
        r = math.radians(ang)
        px(a, int(round(7.5 + 4.2 * math.cos(r))), int(round(7.5 + 4.2 * math.sin(r))), "#e9eef0")
    circ(a, 7.5, 7.5, 3, "#9ea3a6")
    circ(a, 7.5, 7.5, 1.8, blank_px())


@icon
def i_weights(a):
    for i, (x, y) in enumerate(((2, 9), (6, 6), (10, 3))):
        box(a, x, y, x + 3, y + 3, "#d3a246" if i % 2 else "#b4b8bb")
        px(a, x + 1, y + 1, "#5c6164")


@icon
def i_alignment(a):
    box(a, 1, 6, 6, 10, "#c0392b")
    box(a, 9, 6, 14, 10, "#c0392b")
    box(a, 2, 7, 3, 8, "#ff4a36")
    line(a, 4, 8, 11, 8, "#ff6a5a")
    box(a, 6, 11, 9, 13, "#2b2f33")


@icon
def i_impeller(a):
    circ(a, 7.5, 7.5, 6.5, "#d3a246")
    for ang in range(0, 360, 60):
        r = math.radians(ang)
        line(a, 7, 7, int(round(7.5 + 6 * math.cos(r))), int(round(7.5 + 6 * math.sin(r))), "#8a6a2a")
    circ(a, 7.5, 7.5, 2, "#b4b8bb")


# ======================================================================
# armor layers (64x32 vanilla humanoid layout)
# ======================================================================
def armor(base_regions):
    a = blank(64, 32)
    for (x0, y0, x1, y1, col) in base_regions:
        for y in range(y0, y1):
            for x in range(x0, x1):
                a[y, x] = C(col)
                a[y, x, :3] *= rng.uniform(0.94, 1.05)
    return a


def faces(ox, oy, w, h, d):
    """Vanilla box UV: top, bottom, right, front, left, back -> (x0, y0, x1, y1)."""
    return {
        "top": (ox + d, oy, ox + d + w, oy + d),
        "bottom": (ox + d + w, oy, ox + d + 2 * w, oy + d),
        "right": (ox, oy + d, ox + d, oy + d + h),
        "front": (ox + d, oy + d, ox + d + w, oy + d + h),
        "left": (ox + d + w, oy + d, ox + 2 * d + w, oy + d + h),
        "back": (ox + 2 * d + w, oy + d, ox + 2 * d + 2 * w, oy + d + h),
    }


HEAD = faces(0, 0, 8, 8, 8)
HAT = faces(32, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)
LEG = faces(0, 16, 4, 12, 4)


def fill(a, rect, col, rows=None):
    x0, y0, x1, y1 = rect
    for y in range(y0, y1):
        if rows and not (rows[0] <= y - y0 < rows[1]):
            continue
        for x in range(x0, x1):
            a[y, x] = C(col)
            a[y, x, :3] *= rng.uniform(0.94, 1.05)


def fill_box(a, fc, col, rows=None, skip=()):
    """Paint a box; rows=(r0, r1) limits side faces to those rows (top/bottom only if covered)."""
    h = fc["front"][3] - fc["front"][1]
    for k, r in fc.items():
        if k in skip:
            continue
        if k == "top" and rows and rows[0] > 0:
            continue
        if k == "bottom" and rows and rows[1] < h:
            continue
        fill(a, r, col, rows if k not in ("top", "bottom") else None)


def pneumosuit_l1():
    a = blank(64, 32)
    white = "#e8e6de"
    fill_box(a, HEAD, white)
    x0, y0, x1, y1 = HEAD["front"]
    box(a, x0 + 1, y0 + 2, x1 - 2, y0 + 5, "#9fc3d6")  # визор
    px(a, x0 + 2, y0 + 3, "#e6f2f6")
    fill_box(a, BODY, white)
    fill_box(a, ARM, white)
    fx0, fy0, fx1, fy1 = BODY["front"]
    box(a, fx0 + 3, fy0, fx0 + 4, fy1 - 1, "#c9c5b8")  # молния
    box(a, fx0 + 5, fy0 + 2, fx0 + 6, fy0 + 3, "#3a6fb0")  # штуцер подачи воздуха
    bx0, by0, bx1, by1 = BODY["back"]
    box(a, bx0 + 2, by0 + 1, bx1 - 3, by0 + 4, "#3a6fb0")  # коллектор воздуха
    for k in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = ARM[k]
        box(a, x0, y1 - 2, x1 - 1, y1 - 1, "#e8b923")  # манжета перчатки
    fill_box(a, LEG, white, rows=(6, 12), skip=("top",))
    for k in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = LEG[k]
        box(a, x0, y1 - 2, x1 - 1, y1 - 1, "#2b2f33")  # подошва бот
    return a


def legs_l2(col, belt, trim=None):
    a = blank(64, 32)
    fill_box(a, BODY, col, rows=(7, 12), skip=("top",))
    for k in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = BODY[k]
        box(a, x0, y0 + 7, x1 - 1, y0 + 7, belt)
    fill_box(a, LEG, col, skip=("top",))
    if trim:
        for k in ("right", "front", "left", "back"):
            x0, y0, x1, y1 = LEG[k]
            box(a, x0, y0 + 5, x1 - 1, y0 + 5, trim)
    return a


def respirator_l1():
    a = blank(64, 32)
    x0, y0, x1, y1 = HEAD["front"]
    box(a, x0 + 2, y0 + 5, x1 - 3, y1 - 1, "#e6e3d9")  # полумаска
    px(a, x0 + 1, y0 + 6, "#c0392b")
    px(a, x1 - 2, y0 + 6, "#c0392b")
    for k in ("right", "left"):
        x0, y0, x1, y1 = HEAD[k]
        box(a, x0, y0 + 4, x1 - 1, y0 + 4, "#3b3f42")  # оголовье
    x0, y0, x1, y1 = HEAD["back"]
    box(a, x0, y0 + 4, x1 - 1, y0 + 4, "#3b3f42")
    return a


def ffr_l1():
    a = blank(64, 32)
    x0, y0, x1, y1 = HEAD["front"]
    box(a, x0, y0 + 1, x1 - 1, y1 - 1, "#2b2f33")
    box(a, x0 + 1, y0 + 2, x1 - 2, y0 + 4, "#9fc3d6")
    box(a, x0 + 3, y0 + 6, x0 + 4, y0 + 7, "#7a8a3c")  # йодный картридж
    for k in ("right", "left", "back"):
        x0, y0, x1, y1 = HEAD[k]
        box(a, x0, y0 + 2, x1 - 1, y0 + 2, "#3b3f42")
        box(a, x0, y0 + 5, x1 - 1, y0 + 5, "#3b3f42")
    return a


def papr_l1():
    a = blank(64, 32)
    fill_box(a, HEAD, "#e8b923")
    x0, y0, x1, y1 = HEAD["front"]
    box(a, x0 + 1, y0 + 2, x1 - 2, y0 + 6, "#9fc3d6")
    px(a, x0 + 2, y0 + 3, "#e6f2f6")
    fill(a, BODY["back"], "#2b2f33", rows=(8, 11))  # блок вентилятора на поясе
    x0, y0, x1, y1 = HEAD["back"]
    box(a, x0 + 3, y0 + 6, x0 + 4, y1 - 1, "#3b3f42")  # шланг
    return a


def overalls_l1():
    a = blank(64, 32)
    fill_box(a, BODY, "#f2f1ea")
    fill_box(a, ARM, "#f2f1ea")
    fx0, fy0, fx1, fy1 = BODY["front"]
    box(a, fx0 + 3, fy0, fx0 + 4, fy1 - 1, "#d8d6cc")
    box(a, fx0 + 5, fy0 + 2, fx0 + 6, fy0 + 3, "#2f9a55")  # нагрудный знак
    x0, y0, x1, y1 = HEAD["front"]
    fill_box(a, HEAD, "#f2f1ea", skip=("front",))  # капюшон
    box(a, x0, y0, x1 - 1, y0 + 1, "#f2f1ea")
    for y in range(y0 + 2, y1):
        px(a, x0, y, "#f2f1ea")
        px(a, x1 - 1, y, "#f2f1ea")
    return a


def bootcovers_l1():
    a = blank(64, 32)
    fill_box(a, LEG, "#7fb6d9", rows=(8, 12), skip=("top",))
    for k in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = LEG[k]
        box(a, x0, y0 + 8, x1 - 1, y0 + 8, "#f2f1ea")  # резинка
    return a


def apron_l1():
    a = blank(64, 32)
    fill(a, BODY["front"], "#3d5a8a")
    for k in ("right", "left"):
        fill(a, BODY[k], "#3d5a8a")
    x0, y0, x1, y1 = BODY["back"]
    box(a, x0 + 1, y0, x0 + 2, y1 - 1, "#2b2f33")  # лямки крест-накрест
    box(a, x1 - 3, y0, x1 - 2, y1 - 1, "#2b2f33")
    box(a, x0, y0 + 8, x1 - 1, y0 + 8, "#2b2f33")
    fx0, fy0, fx1, fy1 = BODY["front"]
    box(a, fx0 + 2, fy0 + 4, fx0 + 5, fy0 + 6, "#e8b923")
    box(a, fx0, fy0, fx1 - 1, fy0, "#2b2f33")
    return a


# ======================================================================
# registry
# ======================================================================
P = lambda n: f"block/common/plant_{n}.png"
TEXTURES.update({
    # common plant materials
    P("insulation"): insulation,
    P("insulation_smooth"): lambda: insulation(True),
    P("galvanized"): galvanized,
    P("copper"): copper,
    P("copper_smooth"): lambda: copper(True),
    P("monel"): lambda: canvas("#a9a490", noise=4),
    P("panel"): panel,
    P("panel_smooth"): lambda: panel(True),
    P("panel_dark"): lambda: canvas("#3c4447", noise=3),
    P("glass_dark"): glass_dark,
    P("lamp_white"): lambda: lampc("#f4f1e0"),
    P("lamp_blue"): lambda: lampc("#5fa8ff"),
    P("lamp_orange"): lambda: lampc("#ff9a2e"),
    P("lamp_off"): lambda: lampc("#5a4a3a"),
    P("crane_yellow"): crane_yellow,
    P("crane_yellow_smooth"): lambda: crane_yellow(True),
    P("rope"): rope,
    P("spring"): spring,
    P("tile"): tile,
    P("louver"): louver,
    P("blades"): blades,
    P("cable"): cable,
    P("epoxy"): epoxy,
    P("air_blue"): lambda: canvas("#3a6fb0", noise=4),
    P("water_green"): lambda: canvas("#3f7a4a", noise=4),
    P("steam_red"): lambda: canvas("#9c2f27", noise=4),
    P("acid_orange"): lambda: canvas("#c8692a", noise=4),
    P("rack"): rack_teeth,
    # reactor island
    "block/moderator_tank.png": moderator_tank,
    "block/control_rod_channel_top.png": control_rod_channel_top,
    "block/neutron_detector_body.png": neutron_detector_body,
    "block/pipe_large.png": pipe_large,
    "block/pipe_large_end.png": pipe_large_end,
    "block/valve_check_side.png": valve_check_side,
    "block/valve_control_positioner.png": valve_control_positioner,
    "block/pressurizer_shell.png": pressurizer_shell,
    "block/pressurizer_heaters.png": heaters_bottom,
    "block/steam_generator_shell.png": steam_generator_shell,
    "block/steam_generator_collector.png": steam_generator_collector,
    "block/steam_drum_shell.png": steam_drum_shell,
    "block/turbine_casing.png": turbine_casing,
    "block/turbine_casing_smooth.png": turbine_casing_smooth,
    "block/turbine_governor_front.png": turbine_governor_front,
    "block/condenser_tubesheet.png": condenser_tubesheet,
    "block/condenser_shell.png": condenser_shell,
    "block/feedwater_pump_casing.png": feedwater_pump_casing,
    "block/containment_hatch_door.png": hatch_door,
    "block/containment_hatch_frame.png": hatch_frame,
    "block/hydrogen_recombiner_front.png": recombiner_front,
    "block/boron_injection_tank_shell.png": lambda: tank_shell("#c8692a", "#e8b923", "#c8692a"),
    "block/eccs_accumulator_shell.png": lambda: tank_shell("#3f7a4a", "#e8b923", "#3f7a4a"),
    "block/borated_water_tank_shell.png": lambda: tank_shell("#3f7a4a", "#c8692a", "#4a8fb5"),
    "block/tank_top.png": tank_top,
    "block/instrument_temperature_dial.png": temp_dial32,
    "block/instrument_flow_dial.png": flow_dial32,
    "block/instrument_level_dial.png": level_dial32,
    "block/instrument_dose_rate_dial.png": dose_dial32,
    "block/instrument_boron_dial.png": boron_dial32,
    "block/instrument_dose_rate_body.png": lambda: instrument_body("#d8d6cc", "#e8b923"),
    "block/instrument_boron_body.png": lambda: instrument_body("#c4bfaa", "#c8692a"),
    "block/instrument_vibration_body.png": lambda: sensor_body("#3e6b86", "#e8b923"),
    "block/instrument_acoustic_body.png": lambda: sensor_body("#4b5157", "#43b04a"),
    "block/hydrogen_igniter_front.png": igniter_front,
    "block/startup_source_capsule.png": startup_capsule,
    "block/containment_penetration.png": penetration,
    "block/corium.png": corium,
    # 03a
    "block/turbine_stop_valve_body.png": stop_valve_body,
    "block/turbine_stop_valve_servo.png": servo_red,
    "block/turbine_gearbox_housing.png": gearbox_housing,
    "block/electric_generator_stator.png": generator_stator,
    "block/electric_generator_terminal.png": generator_terminal,
    "block/turbine_turning_gear_cover.png": turning_gear_cover,
    "block/condensate_pump_motor.png": condensate_motor,
    "block/zip_rod_winch_ratchet.png": ratchet_face,
    "block/refueling_machine_cab.png": crane_cab,
    "block/refueling_bridge_girder.png": girder,
    "block/refueling_rail.png": rail,
    "block/refueling_trolley_side.png": trolley_side,
    "block/refueling_mast_tube.png": mast_tube,
    "block/refueling_grapple_head.png": grapple_head,
    "block/polar_crane_girder.png": crane_girder_red,
    "block/heavy_load_path_marker.png": path_marker,
    "block/vent_duct.png": vent_duct,
    "block/vent_duct_inner.png": duct_inner,
    "block/vent_damper_actuator.png": damper_actuator,
    "block/hepa_filter_face.png": hepa_face,
    "block/hepa_filter_housing.png": lambda: filter_housing("#3a6fb0"),
    "block/charcoal_filter_face.png": charcoal_face,
    "block/charcoal_filter_housing.png": lambda: filter_housing("#7a8a3c"),
    "block/vent_zone_controller_front.png": vzc_front32,
    "block/vent_stack.png": stack_stripes,
    "block/chem_pipe.png": lambda: chem_pipe_tex("#b4b8bb", "#c8692a"),
    "block/chem_pipe_monel.png": lambda: chem_pipe_tex("#a9a490", "#e8b923"),
    "block/pipe_adapter_create_body.png": adapter_body,
    "block/pipe_adapter_create_raw_body.png": lambda: adapter_body(False),
    # control room
    "block/analog_gauge_dial.png": gauge270_32,
    "block/edgewise_meter_scale.png": edgewise32,
    "block/chart_recorder_window.png": chart_window32,
    "block/digital_display_glass.png": digital_glass32,
    "block/annunciator_panel_face.png": annunciator32,
    "block/key_switch_plate.png": key_plate,
    "block/selector_switch_plate.png": selector_plate,
    "block/pushbutton_bezel.png": pushbutton_bezel,
    "block/scram_button_plate.png": scram_plate,
    "block/bypass_keyswitch_plate.png": bypass_plate,
    "block/alarm_horn_grille.png": horn_grille,
    "block/control_desk_top.png": desk_top,
    "block/control_room_core_front.png": mcr_core_front,
    "block/remote_shutdown_panel_front.png": rsp32,
    "block/control_transfer_switch_plate.png": transfer_plate,
    "block/io_module_front.png": io_front,
    "block/watchdog_relay_front.png": watchdog_front,
    "block/relay_controller_front.png": relay_ctrl_front,
    "block/signal_cable.png": cable_tex,
    # radiation safety
    "block/lead_block.png": lead_bricks,
    "block/lead_sheet.png": lead_sheet,
    "block/lead_glass.png": lead_glass,
    "block/heavy_concrete.png": heavy_concrete,
    "block/polyethylene_block.png": polyethylene,
    "block/borated_polyethylene.png": borated_pe,
    "block/tungsten_block.png": tungsten,
    "block/depleted_uranium_block.png": depleted_u,
    "block/steel_plate.png": steel_plate_block,
    "block/decon_paint.png": epoxy,
    "block/portal_monitor_panel.png": portal_panel,
    "block/hand_foot_monitor_front.png": handfoot_front,
    "block/hand_foot_monitor_foot.png": foot_plate,
    "block/whole_body_counter_panel.png": wbc_panel,
    "block/stack_monitor_front.png": stack_monitor_front,
    "block/tld_reader_front.png": tld_front,
    "block/medical_station_front.png": medical_front,
    "block/zone_controller_front.png": zone_ctrl_front,
    "block/zone_access_door_bottom.png": lambda: door_half(False),
    "block/zone_access_door_top.png": lambda: door_half(True),
    "block/sanitary_checkpoint_front.png": checkpoint_front,
    "block/clean_locker_front.png": lambda: locker_front(True),
    "block/dirty_locker_front.png": lambda: locker_front(False),
    "block/decon_shower_head.png": shower_head,
    "block/decon_drain_top.png": drain_top,
    "block/ultrasonic_bath_front.png": lambda: bath_front("#3a6fb0"),
    "block/electropolish_bath_front.png": lambda: bath_front("#c8692a"),
    "block/ultrasonic_bath_liquid.png": lambda: liquid_top("#8fc3d6"),
    "block/electropolish_bath_liquid.png": lambda: liquid_top("#b9a24a"),
    "block/scabbler_housing.png": scabbler_housing,
    "block/decon_laundry_front.png": laundry_front,
    "block/decon_laundry_drum.png": drum_perforated,
    "block/air_supply_port_plate.png": air_port_plate,
    # armor layers
    "models/armor/pneumosuit_layer_1.png": pneumosuit_l1,
    "models/armor/pneumosuit_layer_2.png": lambda: legs_l2("#e8e6de", "#c9c5b8", "#3a6fb0"),
    "models/armor/respirator_layer_1.png": respirator_l1,
    "models/armor/full_face_respirator_layer_1.png": ffr_l1,
    "models/armor/papr_layer_1.png": papr_l1,
    "models/armor/protective_overalls_layer_1.png": overalls_l1,
    "models/armor/protective_overalls_layer_2.png": lambda: legs_l2("#f2f1ea", "#2f9a55"),
    "models/armor/boot_covers_layer_1.png": bootcovers_l1,
    "models/armor/lead_apron_layer_1.png": apron_l1,
})

TEXTURES.update({P("glow_coil"): lambda: lampc("#ff7a1a")})

ITEM_ICONS = {
    "geiger_counter": i_geiger, "dosimeter_radiometer": i_dosimeter,
    "contamination_meter": i_contam_meter, "personal_dosimeter_epd": i_epd,
    "tld_badge": i_tld, "smear_kit": i_smear, "respirator": i_respirator,
    "full_face_respirator": i_ff_respirator, "iodine_cartridge": i_iodine_cart,
    "papr": i_papr, "pneumosuit_helmet": i_pn_helmet, "pneumosuit_jacket": i_pn_jacket,
    "pneumosuit_trousers": i_pn_trousers, "pneumosuit_boots": i_pn_boots,
    "protective_overalls": i_overalls, "boot_covers": i_bootcovers, "lead_apron": i_lead_apron,
    "potassium_iodide": i_ki, "prussian_blue": i_prussian, "ca_dtpa": i_dtpa,
    "antiemetic": i_antiemetic, "cytokine_injection": i_cytokine, "blood_bag": i_blood,
    "bone_marrow_transplant": i_bmt, "medical_record": i_record, "decon_sprayer": i_sprayer,
    "steam_decon_lance": i_lance, "strippable_coating": i_strippable,
    "wiring_tool": i_wiring_tool, "bypass_key": i_bypass_key, "operator_key": i_operator_key,
    "access_card": i_access_card, "instrument_calibrator": i_calibrator, "chart_paper": i_chart_paper,
    "vibrometer": i_vibrometer, "mechanic_stethoscope": i_stethoscope, "lubricant": i_lubricant,
    "bearing_kit": i_bearing, "balancing_weights": i_weights, "alignment_tool": i_alignment,
    "impeller_kit": i_impeller,
}
TEXTURES.update({f"item/{k}.png": v for k, v in ITEM_ICONS.items()})


def transposed(fn):
    return lambda: np.ascontiguousarray(np.transpose(fn(), (1, 0, 2)))


def cap_clear():  # откидной прозрачный колпачок кнопки АЗ
    a = blank()
    a[:, :] = C("#d84a3a")
    a[:, :, 3] = 120
    for i in range(16):
        for j in (0, 15):
            a[j, i, 3] = 220
            a[i, j, 3] = 220
    for i in range(2, 7):
        a[i, i + 1, :3] = C("#ffd0c8")[:3]
        a[i, i + 1, 3] = 200
    return a


TEXTURES.update({
    P("red_smooth"): lambda: canvas("#b2302a", noise=5),
    "block/pipe_large_t.png": transposed(pipe_large),
    "block/chem_pipe_t.png": transposed(lambda: chem_pipe_tex("#b4b8bb", "#c8692a")),
    "block/chem_pipe_monel_t.png": transposed(lambda: chem_pipe_tex("#a9a490", "#e8b923")),
    "block/scram_button_cap.png": cap_clear,
})
TEXTURES.update({f"block/mimic_tile_{f}.png": (lambda f=f: mimic(f)) for f in (
    "pipe", "bend", "tee", "pump", "valve", "control_valve", "check_valve", "tank",
    "heat_exchanger", "turbine", "reactor", "label")})


def rope_tag():  # бирка на канате АЗ (легенда «SCRAM = axe man»)
    a = canvas("#c0392b", noise=3)
    frame(a, 1.2, 0.75)
    box(a, 4, 3, 11, 12, "#f2f2ea")
    line(a, 5, 11, 10, 4, "#1d1d1f")
    box(a, 8, 4, 10, 6, "#1d1d1f")
    return a


def lamp_label():
    a = canvas("#ece8dc", noise=2)
    frame(a, 1.05, 0.8)
    for x in range(3, 13, 2):
        put(a, 7, x, "#2b2b2b")
        put(a, 8, x + 1, "#2b2b2b")
    return a


TEXTURES.update({
    "block/zip_rope_tag.png": rope_tag,
    "block/indicator_lamp_label.png": lamp_label,
})
