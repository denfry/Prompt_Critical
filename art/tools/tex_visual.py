"""Текстуры детальных моделей M2 (доработка плейсхолдеров котельной, шахты, литейки): клёпаные листы, чугунное
литьё, топочные дверцы, латунная арматура, деревянная обшивка, крашеная эмаль станков, цепь решётки.

У каждой текстуры свой генератор шума (seed по имени), поэтому модуль не трогает общий `pclib.rng` и не сдвигает
шум готовых PNG других областей. Грузится после всех (`build.py`: группа 2, имя `visual`)."""
import math
import random
import zlib

from artkit import *

V = lambda n: f"block/common/m2_{n}.png"


def cv(name, base, noise=6):
    """Холст со своим шумом: тот же результат при любом порядке загрузки модулей."""
    r = random.Random(zlib.crc32(name.encode()))
    a = np.zeros((16, 16, 4))
    a[:, :] = hexc(base)
    for y in range(16):
        for x in range(16):
            a[y, x, :3] += r.uniform(-noise, noise)
    return a, r


def mix(a, x, y, col, k):
    c = C(col)
    a[y, x, :3] = a[y, x, :3] * (1 - k) + c[:3] * k


def rivet_hi(a, x, y):
    """Заклёпка 2×2: блик сверху-слева, тень снизу-справа."""
    shade(a, y, x, 1.45)
    if x + 1 < 16:
        shade(a, y, x + 1, 1.05)
    if y + 1 < 16:
        shade(a, y + 1, x, 0.95)
        if x + 1 < 16:
            shade(a, y + 1, x + 1, 0.55)


# --- котёл ---

def boiler_shell():  # обечайка: листы внахлёст, двойной ряд заклёпок по шву, потёки
    a, r = cv("boiler_shell", "#3e4744", 5)
    for x in range(16):
        shade(a, 7, x, 0.62)
        shade(a, 8, x, 1.16)
    for x in range(1, 16, 3):
        rivet_hi(a, x, 4)
        rivet_hi(a, x + 1 if x + 1 < 16 else x, 10)
    for y in range(16):
        shade(a, y, 0, 0.7)
    for _ in range(3):
        x = r.randrange(2, 15)
        for y in range(r.randrange(9, 12), 16):
            mix(a, x, y, "#6a4a32", 0.35)
    return a


def boiler_front():  # фронтовая плита: чугун с отбортовкой и болтами по краю
    a, r = cv("boiler_front", "#2b2d2f", 5)
    frame(a, 1.35, 0.55)
    frame(a, 1.12, 0.8, inset=1)
    for i in (2, 6, 9, 13):
        rivet_hi(a, i, 2)
        rivet_hi(a, i, 13)
        rivet_hi(a, 2, i)
        rivet_hi(a, 13, i)
    return a


def fire_door(lit):  # топочная дверца: литая рамка, петли слева, щеколда справа, поддувальные прорези
    a, r = cv("fire_door_lit" if lit else "fire_door", "#232527", 5)
    box(a, 0, 0, 15, 0, "#111214")
    box(a, 0, 15, 15, 15, "#111214")
    box(a, 0, 0, 0, 15, "#111214")
    box(a, 15, 0, 15, 15, "#111214")
    frame(a, 1.4, 0.6, inset=1)
    box(a, 4, 3, 11, 8, "#2f3236")
    frame_k = [(4, 3), (11, 3), (4, 8), (11, 8)]
    for x in range(4, 12):
        shade(a, 3, x, 1.3)
        shade(a, 8, x, 0.65)
    for x, y in frame_k:
        rivet_hi(a, x, y)
    for y in (3, 4, 11, 12):
        box(a, 1, y, 2, y, "#5a5d60")
    box(a, 12, 6, 14, 7, "#8a8c8e")
    px(a, 14, 6, "#c8c8c8")
    slot = "#ff9a2a" if lit else "#0b0b0c"
    for x in (4, 6, 8, 10):
        box(a, x, 10, x, 13, slot)
        if lit:
            px(a, x, 13, "#ffd27a")
    if lit:
        for x in range(2, 14):
            mix(a, x, 14, "#ff6a1a", 0.35)
    return a


def ash_door():  # дверца зольника: жалюзи
    a, r = cv("ash_door", "#26282a", 4)
    frame(a, 1.35, 0.55)
    for y in range(3, 14, 3):
        box(a, 2, y, 13, y, "#0d0e0f")
        for x in range(2, 14):
            shade(a, y - 1, x, 1.25)
    return a


def brass():  # полированная латунь: блик по диагонали
    a, r = cv("brass", "#b58d3c", 6)
    for y in range(16):
        for x in range(16):
            d = abs((x + y) - 12)
            if d < 3:
                shade(a, y, x, 1.32 - 0.08 * d)
    frame(a, 1.1, 0.75)
    return a


def gauge_glass():  # стекло на щите: латунные головки, трубка, ограждение; столбик рисует BER
    a, r = cv("gauge_glass", "#20282c", 3)
    box(a, 0, 0, 15, 2, "#b58d3c")
    box(a, 0, 13, 15, 15, "#b58d3c")
    for x in range(16):
        shade(a, 0, x, 1.3)
        shade(a, 15, x, 0.65)
    box(a, 5, 3, 10, 12, "#3a464c")
    box(a, 6, 3, 6, 12, "#7d9aa6")
    box(a, 3, 3, 3, 12, "#555a5e")
    box(a, 12, 3, 12, 12, "#555a5e")
    return a


def setting_brick():  # обмуровка котла: тёмный шамотный кирпич в копоти
    a, r = cv("setting_brick", "#7a3e2c", 6)
    for y in range(16):
        if y % 4 == 3:
            box(a, 0, y, 15, y, "#4a4038")
        else:
            off = 0 if (y // 4) % 2 == 0 else 4
            for x in range(off, 16, 8):
                px(a, x, y, "#4a4038")
    for y in range(16):
        for x in range(16):
            if y < 5:
                a[y, x, :3] *= 0.75 + 0.05 * y
    return a


def cast_iron():  # чугун: тёмный, зернистый, с отливочной кромкой
    a, r = cv("cast_iron", "#34373a", 7)
    frame(a, 1.22, 0.7)
    return a


def steel_polished():  # полированная сталь штоков и вала
    a, r = cv("steel_polished", "#aeb4b8", 3)
    for y in range(16):
        k = 1.18 if y in (4, 5) else (0.82 if y > 11 else 1.0)
        for x in range(16):
            shade(a, y, x, k)
    return a


def handwheel():  # красный маховичок вентиля
    a, r = cv("handwheel", "#9a2420", 5)
    frame(a, 1.3, 0.6)
    return a


def valve_body():  # корпус арматуры: чугун, окрашенный в цвет пара (ГОСТ 14202: красный)
    a, r = cv("valve_body", "#5a2a24", 5)
    frame(a, 1.25, 0.65)
    for x in (3, 12):
        rivet_hi(a, x, 3)
        rivet_hi(a, x, 12)
    return a


def smokebox():  # дымовая коробка: чёрная, жаростойкая, в копоти
    a, r = cv("smokebox", "#1d1e1f", 5)
    frame(a, 1.3, 0.6)
    for x in range(2, 15, 3):
        rivet_hi(a, x, 2)
        rivet_hi(a, x, 13)
    return a


def lagging():  # деревянная обшивка цилиндра: рейки с латунными бандажами
    a, r = cv("lagging", "#7a5432", 6)
    for x in range(0, 16, 4):
        for y in range(16):
            shade(a, y, x, 0.68)
            if x + 1 < 16:
                shade(a, y, x + 1, 1.12)
    for y in (1, 2, 13, 14):
        box(a, 0, y, 15, y, "#b58d3c")
    for x in range(16):
        shade(a, 1, x, 1.25)
        shade(a, 14, x, 0.75)
    return a


def engine_paint():  # эмаль станины машины: тёмно-зелёная с красной филёнкой
    a, r = cv("engine_paint", "#2f4a36", 4)
    for i in range(1, 15):
        px(a, i, 1, "#8a2a22")
        px(a, i, 14, "#8a2a22")
        px(a, 1, i, "#8a2a22")
        px(a, 14, i, "#8a2a22")
    frame(a, 1.2, 0.7)
    return a


def engine_plain():  # та же эмаль без филёнки — мелкие детали
    a, r = cv("engine_plain", "#2f4a36", 4)
    frame(a, 1.15, 0.78)
    return a


# --- решётка и труба ---

def chain():  # полотно цепной решётки: колосники поперёк, звенья по краям
    a, r = cv("chain", "#3a3532", 6)
    for y in range(0, 16, 4):
        box(a, 0, y, 15, y, "#191716")
        for x in range(16):
            shade(a, y + 1, x, 1.25)
    for y in range(16):
        for x in (0, 15):
            px(a, x, y, "#5a5550" if y % 4 else "#202020")
    return a


def coal_bed(lit):  # слой угля на решётке: тёмные куски или раскалённый слой
    a, r = cv("coal_bed_lit" if lit else "coal_bed", "#1b1a1a" if not lit else "#5a1a0a", 8)
    for _ in range(26):
        x, y = r.randrange(16), r.randrange(16)
        if lit:
            px(a, x, y, r.choice(["#ff8a1a", "#ffb02a", "#ff5a10", "#ffd060"]))
        else:
            px(a, x, y, r.choice(["#2c2b2b", "#0e0d0d", "#3a3836"]))
            shade(a, y, x, 1.0)
    return a


def chimney_brick():  # кирпич трубы: мелкая кладка, светлый раствор, копоть к верху
    a, r = cv("chimney_brick", "#8e4a34", 6)
    for y in range(16):
        if y % 3 == 2:
            box(a, 0, y, 15, y, "#a89a88")
        else:
            off = 0 if (y // 3) % 2 == 0 else 3
            for x in range(off, 16, 6):
                px(a, x, y, "#a89a88")
    return a


def iron_band():  # стяжной обруч трубы
    a, r = cv("iron_band", "#2a2b2c", 4)
    for x in range(16):
        shade(a, 2, x, 1.4)
        shade(a, 13, x, 0.6)
    for x in range(2, 16, 5):
        rivet_hi(a, x, 7)
    return a


def soot_cap():  # обрез трубы изнутри: сажа
    a, r = cv("soot_cap", "#121212", 4)
    return a


# --- шахта ---

def timber_log():  # окорённая стойка: продольные волокна, остатки коры, торцевые трещины
    a, r = cv("timber_log", "#9a7448", 7)
    for x in range(16):
        k = 0.85 + 0.25 * math.sin(x * 1.3 + 0.5)
        for y in range(16):
            shade(a, y, x, k)
    for _ in range(5):
        x, y = r.randrange(16), r.randrange(13)
        for d in range(3):
            mix(a, x, y + d, "#4a3420", 0.6)
    return a


def plank():  # затяжка: доска с сучком
    a, r = cv("plank", "#8a6a44", 6)
    for y in (0, 15):
        for x in range(16):
            shade(a, y, x, 0.6)
    for x in range(16):
        shade(a, 7, x, 0.85)
    px(a, 11, 4, "#4a3420")
    px(a, 12, 4, "#5a4028")
    return a


def mining_yellow():  # жёлтая эмаль мехкрепи: сварные швы, сколы
    a, r = cv("mining_yellow", "#d8a820", 6)
    frame(a, 1.15, 0.7)
    for x in range(16):
        shade(a, 8, x, 0.85)
    for _ in range(6):
        px(a, r.randrange(1, 15), r.randrange(1, 15), "#5a4a30")
    return a


def mining_orange():  # оранжевый комбайн: панели на болтах, полоса предупреждения
    a, r = cv("mining_orange", "#d8641c", 6)
    frame(a, 1.2, 0.65)
    for x in range(2, 14, 4):
        rivet_hi(a, x, 2)
        rivet_hi(a, x, 13)
    for _ in range(5):
        px(a, r.randrange(1, 15), r.randrange(3, 13), "#4a3a2a")
    return a


def hazard_band():  # жёлто-чёрная полоса
    a, r = cv("hazard_band", "#e0b020", 3)
    for y in range(16):
        for x in range(16):
            if ((x + y) // 4) % 2:
                a[y, x, :3] = C("#1a1a1a")[:3] + r.uniform(-4, 4)
    return a


def motor_ribs():  # корпус электродвигателя: продольные рёбра охлаждения
    a, r = cv("motor_ribs", "#46586a", 4)
    for x in range(0, 16, 3):
        for y in range(16):
            shade(a, y, x, 1.3)
            if x + 1 < 16:
                shade(a, y, x + 1, 0.7)
    return a


def drum_vane():  # шнек: стальной барабан со спиральной лопастью
    a, r = cv("drum_vane", "#6a6e72", 6)
    for y in range(16):
        x = (y * 2) % 16
        for d in range(2):
            px(a, (x + d) % 16, y, "#9aa0a6")
        px(a, (x + 2) % 16, y, "#3a3c3e")
    return a


def pick():  # резец: твердосплавная головка
    a, r = cv("pick", "#3a3c3e", 4)
    box(a, 5, 0, 10, 5, "#c8ccd0")
    return a


TEXTURES.update({
    V("boiler_shell"): boiler_shell, V("boiler_front"): boiler_front,
    V("fire_door"): lambda: fire_door(False), V("glow_fire_door"): lambda: fire_door(True),
    V("ash_door"): ash_door, V("brass"): brass, V("gauge_glass"): gauge_glass,
    V("setting_brick"): setting_brick, V("cast_iron"): cast_iron, V("steel_polished"): steel_polished,
    V("handwheel"): handwheel, V("valve_body"): valve_body, V("smokebox"): smokebox, V("lagging"): lagging,
    V("engine_paint"): engine_paint, V("engine_plain"): engine_plain, V("chain"): chain, V("coal_bed"): lambda: coal_bed(False),
    V("glow_coal_bed"): lambda: coal_bed(True), V("chimney_brick"): chimney_brick, V("iron_band"): iron_band,
    V("soot_cap"): soot_cap,
    V("timber_log"): timber_log, V("plank"): plank, V("mining_yellow"): mining_yellow, V("mining_orange"): mining_orange,
    V("hazard_band"): hazard_band, V("motor_ribs"): motor_ribs, V("drum_vane"): drum_vane, V("pick"): pick,
})


# --- литейка и механический цех ---

def machine_enamel(edge):  # станочная эмаль (серо-зелёная молотковая), с кромкой или гладкая
    a, r = cv("machine_enamel" if edge else "machine_smooth", "#5d7466", 5)
    for _ in range(18):
        x, y = r.randrange(16), r.randrange(16)
        shade(a, y, x, r.choice((0.9, 1.1)))
    if edge:
        frame(a, 1.25, 0.7)
    return a


def refractory():  # шамотный огнеупор: светлый, с оплавленными пятнами
    a, r = cv("refractory", "#c9a46a", 7)
    for y in range(16):
        if y % 4 == 3:
            box(a, 0, y, 15, y, "#8a7050")
        else:
            off = 0 if (y // 4) % 2 == 0 else 4
            for x in range(off, 16, 8):
                px(a, x, y, "#8a7050")
    for _ in range(4):
        x, y = r.randrange(16), r.randrange(16)
        mix(a, x, y, "#3a2a20", 0.5)
    return a


def foundry_sand():  # формовочная смесь: влажная, тёмная, зернистая
    a, r = cv("foundry_sand", "#6a5638", 12)
    for _ in range(20):
        px(a, r.randrange(16), r.randrange(16), r.choice(["#8a7450", "#4a3a26"]))
    return a


def tslot():  # стол станка: шлифованная плита с Т-пазами
    a, r = cv("tslot", "#8e959a", 3)
    for x in (3, 8, 13):
        for y in range(16):
            px(a, x, y, "#2a2c2e")
            shade(a, y, x - 1, 1.15)
    return a


def pattern_wood():  # модельное дерево: жёлтая сосна, лаковая
    a, r = cv("pattern_wood", "#d0a050", 5)
    for y in range(0, 16, 3):
        for x in range(16):
            shade(a, y, x, 0.88)
    frame(a, 1.15, 0.8)
    return a


def glow_metal():  # расплав: белое-жёлтое ядро, оранжевая корка
    a, r = cv("glow_metal", "#ff8a20", 10)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4:
                mix(a, x, y, "#fff0a0", 0.7)
            elif d < 7:
                mix(a, x, y, "#ffc040", 0.5)
    return a


def shell_black():  # чёрная клёпаная обечайка вагранки
    a, r = cv("shell_black", "#2a2b2d", 5)
    for x in range(16):
        shade(a, 7, x, 0.6)
    for x in range(1, 16, 3):
        rivet_hi(a, x, 4)
        rivet_hi(a, x, 10)
    for _ in range(4):
        x = r.randrange(16)
        for y in range(r.randrange(8, 14), 16):
            mix(a, x, y, "#6a3a24", 0.3)
    return a


TEXTURES.update({
    V("machine"): lambda: machine_enamel(True), V("machine_smooth"): lambda: machine_enamel(False),
    V("refractory"): refractory, V("foundry_sand"): foundry_sand, V("tslot"): tslot, V("pattern_wood"): pattern_wood,
    V("glow_metal"): glow_metal, V("shell_black"): shell_black,
})
