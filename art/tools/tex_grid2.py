"""07 после M3: АТ 220 кВ, БСК, диспетчерский щит, АКБ и ВАЗП, ГТУ и КУ, поршневые агрегаты,
градирни и водозабор, ГЭС; иконки комплектующих."""
from artkit2 import *

G2 = lambda n: f"block/common/grid2_{n}.png"


def battery_jar(level):  # прозрачная банка СК: электролит до уровня, пластины
    def fn():
        a = blank()
        a[:, :] = C("#d6e4e6")
        a[:, :, 3] = 110
        top = 15 - int(level * 3)
        for y in range(top, 16):
            for x in range(1, 15):
                a[y, x] = C("#9fbfa8")
                a[y, x, 3] = 170
        for x in range(3, 14, 3):
            box(a, x, 4, x, 14, "#6b5a4a")
        box(a, 0, 0, 15, 1, "#3a3d40")
        frame(a, 1.0, 0.8)
        return a
    return fn


def fill_pack():  # плёночный ороситель: соты
    a = canvas("#d9dcd6", noise=2)
    for y in range(0, 16, 3):
        for x in range(16):
            if (x + (y // 3) * 2) % 4 == 0:
                kbox(a, x, y, x, y + 2, 0.7)
        kbox(a, 0, y, 15, y, 0.85)
    return a


def tower_band():  # бетон градирни с красно-белым поясом
    a = canvas("#a7a7a1", noise=5)
    for y in range(0, 6):
        box(a, 0, y, 15, y, "#c0392b" if (y // 3) % 2 == 0 else "#e8e6dc")
    return a


def tower_concrete():
    a = canvas("#a7a7a1", noise=5)
    for y in range(3, 16, 5):
        kbox(a, 0, y, 15, y, 0.9)
    return a


def dispatch32(a):  # мнемощит ОДУ: линии, подстанции-кружки, табло частоты
    for (x0, y0, x1, y1) in ((3, 8, 28, 8), (3, 16, 28, 16), (8, 8, 8, 24), (20, 8, 20, 24), (8, 24, 20, 24)):
        line(a, x0, y0, x1, y1, "#c0392b" if y0 == 8 else "#2c5ea8")
    for (x, y) in ((8, 8), (20, 8), (8, 16), (20, 16), (8, 24), (20, 24), (28, 16)):
        box(a, x - 1, y - 1, x + 1, y + 1, "#2b2b2b")
    box(a, 11, 1, 20, 5, "#1d1d1f")
    box(a, 12, 2, 19, 4, "#e05a3a")


def charger_front(a):
    box(a, 3, 2, 12, 6, "#1d1d1f")
    circ(a, 7.5, 4, 1.6, "#ece8dc")
    for x in (4, 7, 10):
        box(a, x, 9, x + 1, 10, "#3a3d40")
    grille(a, 2, 12, 13, 14, step=2, k=0.6)


def gate_steel():  # затвор: ригели и обшивка
    a = canvas("#5f6c72", noise=4)
    for y in (3, 8, 13):
        box(a, 0, y, 15, y, "#4a555b")
    frame(a, 1.1, 0.75)
    return a


def engine_block():  # литой блок цилиндров
    a = canvas("#5d7a5a", noise=6)
    for x in (3, 8, 13):
        circ(a, x, 5, 1.6, "#4b6349")
    frame(a, 1.1, 0.8)
    return a


def exhaust():  # выпускной коллектор: окалина
    a = canvas("#6b4a36", noise=10)
    for y in range(0, 16, 4):
        kbox(a, 0, y, 15, y, 0.8)
    return a


# --- иконки ---
def insulator_post(a):
    for y in range(2, 13, 2):
        box(a, 5, y, 10, y, "#e8e4d8")
        box(a, 6, y + 1, 9, y + 1, "#b8b2a2")
    box(a, 5, 13, 10, 14, "#7d8288")
    box(a, 6, 1, 9, 1, "#7d8288")


def bushing(a):
    box(a, 7, 1, 8, 2, "#c9a14a")
    for y in range(3, 10, 2):
        box(a, 5, y, 10, y, "#a65a32")
        box(a, 6, y + 1, 9, y + 1, "#7a3e22")
    box(a, 4, 11, 11, 12, "#7d8288")
    box(a, 6, 13, 9, 14, "#7d8288")
    px(a, 10, 9, "#e8b923")


def jacket_heater(a):
    box(a, 3, 5, 10, 11, "#9aa0a4")
    box(a, 10, 7, 13, 9, "#3a3d40")
    for y in (6, 8, 10):
        line(a, 4, y, 9, y, "#c0392b")
    box(a, 1, 7, 2, 8, "#3a3d40")


def module_icon(body, accent):
    def draw(a):
        box(a, 3, 3, 12, 13, body)
        box(a, 5, 5, 10, 7, "#1d1d1f")
        box(a, 6, 6, 9, 6, accent)
        for x in (5, 7, 9):
            px(a, x, 10, accent)
    return draw


def oil_bottle(filled):
    def draw(a):
        box(a, 5, 5, 10, 14, "#cfe3ea")
        box(a, 6, 2, 9, 4, "#3a3d40")
        if filled:
            box(a, 5, 8, 10, 14, "#d89a2a")
        box(a, 6, 10, 9, 12, "#ece8da")
    return draw


def burner_kit(a):
    box(a, 2, 9, 13, 13, "#6b4a2a")
    for x in (4, 8, 11):
        box(a, x - 1, 4, x, 9, "#9aa0a4")
        px(a, x, 3, "#5a5e62")


def soft_starter(a):
    box(a, 3, 2, 12, 13, "#cfd1ca")
    for x in range(3, 13, 2):
        box(a, x, 13, x, 14, "#5a5e62")
    box(a, 5, 4, 10, 6, "#1d1d1f")
    box(a, 6, 5, 7, 5, "#3fae5a")
    circ(a, 7.5, 9.5, 1.5, "#2c5ea8")


TEXTURES.update({
    **{G2(f"battery_jar_{i}"): battery_jar(i) for i in range(5)},
    G2("fill"): fill_pack,
    G2("tower_band"): tower_band,
    G2("tower_concrete"): tower_concrete,
    G2("gate"): gate_steel,
    G2("engine"): engine_block,
    G2("exhaust"): exhaust,
    G2("cap_can"): paint("#b9bcb5", 2),
    "block/grid_dispatch_center_board.png": front32("#cfd1ca", dispatch32),
    "block/battery_charger_front.png": front16("#cfd1ca", charger_front),
    "item/insulator_post.png": icon(insulator_post),
    "item/bushing_hv.png": icon(bushing),
    "item/jacket_heater.png": icon(jacket_heater),
    "item/load_sequencer.png": icon(module_icon("#cfd1ca", "#3fae5a")),
    "item/relay_auxiliary_islanding.png": icon(module_icon("#4a2c1d", "#e8b923")),
    "item/oil_sample_bottle.png": icon(oil_bottle(False)),
    "item/oil_sample_bottle_filled.png": icon(oil_bottle(True)),
    "item/low_nox_burner_kit.png": icon(burner_kit),
    "item/soft_starter.png": icon(soft_starter),
})

for _i in ("insulator_post", "bushing_hv", "jacket_heater", "load_sequencer", "relay_auxiliary_islanding",
           "oil_sample_bottle", "low_nox_burner_kit", "soft_starter"):
    item(_i, f"item/{_i}.png")
