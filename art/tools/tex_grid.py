"""07: энергосеть — генераторы, двигатели, ОРУ и КРУ, ЛЭП, котлы и паровые машины; иконки РЗА."""
import math
from artkit import *

G = lambda n: f"block/common/grid_{n}.png"


def ribs(base, step=2, k=1.3, vertical=False, noise=3):
    def fn():
        a = canvas(base, noise=noise)
        for i in range(0, 16, step):
            for j in range(16):
                shade(a, j if vertical else i, i if vertical else j, k)
        return a
    return fn


def flat(base, noise=3, fr=True):
    def fn():
        a = canvas(base, noise=noise)
        if fr:
            frame(a, 1.1, 0.8)
        return a
    return fn


def porcelain(glaze):  # юбки изолятора: светлые рёбра, тёмные впадины
    def fn():
        a = canvas(glaze, noise=2)
        for y in range(16):
            k = 1.25 if y % 4 == 0 else (0.7 if y % 4 == 2 else 1.0)
            for x in range(16):
                shade(a, y, x, k)
        for y in range(16):
            shade(a, y, 3, 1.15)
        return a
    return fn


def aluminium():  # трубчатая шина: блик вдоль
    a = canvas("#c3c8cc", noise=2)
    for x in range(16):
        shade(a, 4, x, 1.2)
        shade(a, 11, x, 0.8)
    return a


def lattice():  # решётка уголков опоры ЛЭП с прозрачностью
    a = blank()
    for i in range(16):
        for d in (0, 1):
            for (x, y) in ((i, min(15, i + d)), (15 - i, min(15, i + d))):
                px(a, x, y, "#a7adb1")
    box(a, 0, 0, 1, 15, "#9aa0a4")
    box(a, 14, 0, 15, 15, "#9aa0a4")
    box(a, 0, 0, 15, 1, "#b3b8bb")
    return a


def riveted():  # клёпаная обечайка котла
    a = canvas("#5b5f62", noise=4)
    for x in range(16):
        shade(a, 7, x, 0.7)
    for x in range(1, 16, 3):
        rivet(a, 5, x)
        rivet(a, 9, x)
    return a


def brick():  # дымовая труба I0: красный кирпич
    a = canvas("#8c3b2a", noise=6)
    for y in range(0, 16, 4):
        for x in range(16):
            put(a, y, x, "#b3a596")
        off = 0 if (y // 4) % 2 == 0 else 4
        for yy in range(y + 1, min(16, y + 4)):
            put(a, yy, (off + 0) % 16, "#b3a596")
            put(a, yy, (off + 8) % 16, "#b3a596")
    return a


def rc_band():  # железобетонная труба с красно-белым поясом
    a = canvas("#e8e6e0", noise=3)
    box(a, 0, 0, 15, 7, "#c0392b")
    for x in range(16):
        shade(a, 8, x, 0.85)
    return a


def chain_grate():  # раскалённый слой на цепной решётке
    a = canvas("#ff7a1a", noise=10)
    for y in range(0, 16, 3):
        for x in range(16):
            shade(a, y, x, 0.55)
    for y, x in ((2, 4), (6, 11), (11, 6), (13, 13)):
        put(a, y, x, "#ffe08a")
    return a


def waterwall():  # газоплотный экран: вертикальные трубы с плавником
    a = canvas("#5d6164", noise=3)
    for x in range(16):
        k = 1.3 if x % 4 == 1 else (0.6 if x % 4 == 3 else 1.0)
        for y in range(16):
            shade(a, y, x, k)
    return a


def slagged():
    a = waterwall()
    for y in range(16):
        for x in range(16):
            if (x * 7 + y * 13) % 5 < 3:
                a[y, x, :3] = a[y, x, :3] * 0.4 + C("#3a2c22")[:3] * 0.6
    return a


def coils(col):  # змеевики экономайзера/пароперегревателя с маркировкой среды
    def fn():
        a = canvas("#6a6e71", noise=3)
        for y in range(1, 16, 3):
            box(a, 0, y, 15, y, "#9da2a6")
            shade(a, y + 1, 0, 0.7)
        box(a, 0, 0, 15, 0, col)
        box(a, 0, 15, 15, 15, col)
        return a
    return fn


def armour():  # бронеплиты барабана мельницы
    a = canvas("#4f5356", noise=4)
    for y in range(0, 16, 5):
        for x in range(16):
            shade(a, y, x, 0.6)
    for y in range(2, 16, 5):
        for x in range(2, 16, 5):
            rivet(a, y, x)
    kbox(a, 0, 12, 15, 15, 1.25)
    return a


def mimic_door(lamps=True):  # дверь ячейки КРУ: мнемосхема, окно, рукоятка
    def fn():
        a = canvas("#cfd1ca", noise=3)
        frame(a, 1.1, 0.75)
        box(a, 3, 2, 12, 5, "#2b2f33")
        line(a, 7, 6, 7, 12, "#1d4f91")
        line(a, 5, 9, 9, 9, "#1d4f91")
        box(a, 6, 8, 8, 10, "#1d4f91")
        plate(a, 3, 13, 9, 14)
        return a
    return fn


def switchboard_front():  # ЩО-70: рубильники и амперметры
    a = canvas("#bfc3bd", noise=3)
    frame(a, 1.1, 0.75)
    for x in (2, 7, 12):
        box(a, x, 3, x + 2, 9, "#2a2c2e")
        box(a, x + 1, 4, x + 1, 8, "#c7834a")
    for x in (3, 8, 13):
        circ(a, x, 12, 1.6, "#f1eee4")
    return a


def relay_glass_door():  # шкаф РЗА: стеклянная дверь, ряды круглых реле
    a = blank()
    a[:, :] = C("#9eb6bf")
    a[:, :, 3] = 110
    for y in (3, 8, 13):
        for x in (3, 8, 13):
            circ(a, x, y, 1.8, "#2a2c2e")
            px(a, x, y, "#e6e2d2")
    for i in range(16):
        a[0, i] = a[15, i] = a[i, 0] = a[i, 15] = C("#bfc3bd")
    return a


def fe_front():  # шкаф преобразователя: решётки и индикатор направления
    a = canvas("#cfd1ca", noise=3)
    frame(a, 1.1, 0.75)
    grille(a, 2, 2, 13, 6, step=2, k=0.55)
    plate(a, 4, 9, 11, 11)
    return a


def meter_face32():  # счётчик САЗУ: окно барабанного счётного механизма (цифры — BER)
    n = 32
    a = blank(n)
    a[:, :] = C("#2d2f31")
    box(a, 3, 3, 28, 28, "#e9e6dc")
    box(a, 6, 8, 25, 13, "#1d1d1f")
    circ(a, 15.5, 21, 4, "#9aa0a4")
    box(a, 13, 20, 18, 21, "#2d2f31")
    box(a, 27, 27, 28, 28, "#c0392b")  # пломба
    return a


def synchroscope32():  # Э-327: круглая шкала, сектор «FAST» и «SLOW»; стрелку рисует BER
    a = dial_round32(face="#f1eee4", bezel="#151617", bg="#151617", face_r=12.5, bez_r=15)
    box(a, 15, 3, 16, 7, "#c0392b")
    box(a, 21, 6, 24, 7, "#2b8a3e")
    box(a, 7, 6, 10, 7, "#2b8a3e")
    return a


def ldb_front():  # щиток ЩРН: окно с рукоятками автоматов
    a = canvas("#bfc3bd", noise=3)
    frame(a, 1.1, 0.75)
    box(a, 2, 5, 13, 10, "#2a2c2e")
    for x in range(3, 13, 2):
        box(a, x, 6, x, 8, "#e6e2d2")
    plate(a, 4, 12, 11, 13)
    return a


def bms_panel():  # щит котла: мнемосхема барабана и топки
    a = canvas("#bfc3bd", noise=3)
    frame(a, 1.1, 0.75)
    box(a, 3, 2, 12, 4, "#2f6fb3")
    box(a, 4, 6, 11, 12, "#8c3b2a")
    box(a, 6, 8, 9, 10, "#ff9a3a")
    return a


def firebox_door(lit):
    def fn():
        a = canvas("#2f3133", noise=4)
        frame(a, 1.25, 0.6)
        box(a, 4, 6, 11, 9, "#ffb04a" if lit else "#1a1a1a")
        for y, x in ((2, 2), (2, 13), (13, 2), (13, 13)):
            rivet(a, y, x)
        return a
    return fn


def consumer_sign():  # табличка ТП: «посёлок»
    a = canvas("#2f6fb3", noise=2)
    frame(a, 1.2, 0.7)
    for x in (3, 7, 11):
        box(a, x, 9, x + 2, 12, "#f1eee4")
        px(a, x + 1, 8, "#f1eee4")
    line(a, 2, 4, 13, 4, "#f1eee4")
    return a


def windows_glow():
    a = canvas("#ffd27a", noise=6)
    for x in range(16):
        shade(a, 7, x, 0.5)
    for y in range(16):
        shade(a, y, 7, 0.5)
    return a


def wire(cols):  # провод пролёта: скрутка, тайлится по X
    def fn():
        a = blank()
        for x in range(16):
            for y in range(6, 10):
                px(a, x, y, cols[(x + y) % len(cols)])
        return a
    return fn


TEXTURES.update({
    G("enamel_green"): flat("#6f7f6a"),
    G("fins_green"): ribs("#6f7f6a"),
    G("motor_fins"): ribs("#1f4f8f", vertical=False),
    G("porcelain"): porcelain("#7a3b22"),
    G("porcelain_grey"): porcelain("#b9bcb7"),
    G("polymer"): porcelain("#8a8f93"),
    G("aluminium"): aluminium,
    G("ral7032"): flat("#b5b09a"),
    G("corrugated"): ribs("#b5b09a", step=2, k=0.75, vertical=True),
    G("radiator"): ribs("#a9a48e", step=3, k=0.6, vertical=True),
    G("wood_pole"): ribs("#3e2a1c", step=5, k=0.8, vertical=True, noise=6),
    G("concrete_pole"): flat("#a3a39c", noise=5, fr=False),
    G("lattice"): lattice,
    G("cable_black"): flat("#1e1f21", noise=2, fr=False),
    G("copper_bar"): flat("#c27a43", noise=3),
    G("riveted"): riveted,
    G("brick"): brick,
    G("rc_band"): rc_band,
    G("rc_white"): flat("#e8e6e0", noise=3, fr=False),
    G("glow_grate"): chain_grate,
    G("cast_iron"): flat("#3f4245", noise=6),
    G("cladding"): flat("#8e9089", noise=4),
    G("waterwall"): waterwall,
    G("waterwall_slag"): slagged,
    G("coils_steam"): coils("#b2302a"),
    G("coils_water"): coils("#2b8a3e"),
    G("coils_air"): coils("#2f6fb3"),
    G("armour"): armour,
    G("ral7035"): flat("#cfd1ca"),
    G("dark_grey"): flat("#4a4d50"),
    G("glow_mercury"): flat("#7fd3ff", noise=10, fr=False),
    "block/switchgear_cell_door.png": mimic_door(),
    "block/lv_switchboard_front.png": switchboard_front,
    "block/relay_cabinet_door.png": relay_glass_door,
    "block/grid_fe_interface_front.png": fe_front,
    "block/energy_meter_face.png": meter_face32,
    "block/instrument_synchroscope_dial.png": synchroscope32,
    "block/lv_distribution_box_front.png": ldb_front,
    "block/water_tube_boiler_panel.png": bms_panel,
    "block/fire_tube_boiler_door.png": firebox_door(False),
    "block/fire_tube_boiler_door_lit.png": firebox_door(True),
    "block/grid_consumer_sign.png": consumer_sign,
    "block/grid_consumer_windows.png": windows_glow,
    "block/wire/copper.png": wire(["#b8642e", "#8f4a20", "#d07a3e"]),
    "block/wire/aluminium.png": wire(["#c3c8cc", "#9ea4a8", "#d6dadd"]),
    "block/wire/acsr.png": wire(["#b9bec2", "#8e9498", "#7a8084"]),
    "block/wire/insulated_lv.png": wire(["#1e1f21", "#1e1f21", "#e0b51e"]),
})

fluid("flue_gas", "#a59a6a", "gas")


# ======================================================================
# иконки предметов
# ======================================================================
def coil_icon(core, stripe=None):
    def draw(a):
        circ(a, 7.5, 8, 6.5, core)
        circ(a, 7.5, 8, 2.2, "#7a5a3a")
        circ(a, 7.5, 8, 1.2, "#2a2a2a")
        if stripe:
            for y in range(2, 14, 3):
                px(a, 12, y, stripe)
    return draw


def fuse_icon(blown):
    def draw(a):
        box(a, 4, 5, 11, 10, "#e8e4d8")
        box(a, 1, 7, 3, 8, "#c0c4c8")
        box(a, 12, 7, 14, 8, "#c0c4c8")
        box(a, 7, 4, 8, 4, "#c0392b" if blown else "#2b8a3e")
    return draw


def starter_icon(a):
    box(a, 2, 4, 6, 12, "#3a3d40")
    box(a, 7, 4, 13, 12, "#3a3d40")
    box(a, 3, 5, 5, 6, "#e6e2d2")
    box(a, 8, 5, 12, 6, "#e6e2d2")
    box(a, 9, 9, 10, 10, "#2b8a3e")
    box(a, 11, 9, 12, 10, "#c0392b")


def insulator_pin_icon(a):
    box(a, 7, 9, 8, 15, "#8e9498")
    for y, w in ((2, 2), (4, 4), (6, 5), (8, 3)):
        box(a, 7 - w, y, 8 + w, y + 1, "#e8e4d8")


def insulator_disc_icon(col):
    def draw(a):
        for y, w in ((5, 6), (7, 7), (9, 5)):
            box(a, 7 - w, y, 8 + w, y + 1, col)
        box(a, 7, 2, 8, 4, "#8e9498")
        box(a, 7, 11, 8, 14, "#8e9498")
    return draw


def coupling_icon(sheared):
    def draw(a):
        circ(a, 7.5, 7.5, 6, "#8e9498")
        circ(a, 7.5, 7.5, 2, "#3a3d40")
        box(a, 11, 7, 14, 8, "#d0a63a")
        if sheared:
            line(a, 2, 13, 13, 2, "#1d1d1f")
    return draw


def fusible_plug_icon(a):
    box(a, 5, 2, 10, 4, "#b07a3a")
    box(a, 6, 5, 9, 13, "#c08a46")
    box(a, 7, 6, 8, 13, "#d6dadd")
    for y in range(6, 13, 2):
        px(a, 6, y, "#8a5a2a")


def dashpot_icon(a):
    box(a, 4, 6, 11, 14, "#c9a14a")
    box(a, 7, 1, 8, 6, "#9aa0a4")
    box(a, 5, 4, 10, 5, "#9aa0a4")
    box(a, 4, 9, 11, 9, "#8a6a2a")


def breather_icon(sat):
    def draw(a):
        box(a, 5, 2, 10, 3, "#3a3d40")
        box(a, 5, 4, 10, 12, "#dfe8ec")
        box(a, 6, 5, 9, 11, "#e6a0b8" if sat else "#3a6ad0")
        box(a, 6, 13, 9, 14, "#3a3d40")
    return draw


GEN_BODY = {"a1": "#2a2c2e", "a2": "#bfc3bd", "a3": "#3a4f66"}


def relay_icon(code_col, gen, extra=None):
    """РЗА: A1 — круглый корпус с диском, A2 — плата, A3 — терминал с экраном."""
    def draw(a):
        if gen == "a1":
            circ(a, 7.5, 7.5, 6.5, GEN_BODY[gen])
            circ(a, 7.5, 7.5, 4.5, "#d9dde0")
            box(a, 4, 7, 11, 8, "#9aa0a4")
            box(a, 6, 12, 9, 13, code_col)
        elif gen == "a2":
            box(a, 2, 2, 13, 13, "#2f7a3a")
            for x in (4, 7, 10):
                box(a, x, 4, x + 1, 6, "#1d1d1f")
            box(a, 3, 10, 12, 12, code_col)
        else:
            box(a, 2, 2, 13, 13, GEN_BODY[gen])
            box(a, 4, 4, 11, 8, "#7fd3a0")
            box(a, 4, 10, 11, 11, code_col)
        if extra:
            extra(a)
    return draw


def mini_sync(a):
    circ(a, 7.5, 7.5, 2, "#f1eee4")
    line(a, 7, 7, 8, 5, "#c0392b")


def buchholz_icon(a):
    box(a, 1, 6, 14, 11, "#c9a14a")
    box(a, 5, 4, 10, 13, "#b8913e")
    circ(a, 7.5, 8.5, 2, "#bfe0ea")
    box(a, 7, 2, 8, 3, "#9aa0a4")


def interlock_icon(a):
    box(a, 4, 6, 11, 14, "#3a3d40")
    box(a, 5, 2, 10, 6, "#9aa0a4")
    box(a, 6, 3, 9, 6, (0, 0, 0, 0))
    box(a, 7, 9, 8, 11, "#e0b51e")


def powder_icon(col, col2):
    def draw(a):
        for y in range(8, 14):
            w = (y - 7)
            box(a, 7 - w, y, 8 + w, y, col)
        for y, x in ((10, 5), (11, 9), (12, 7), (9, 8)):
            px(a, x, y, col2)
    return draw


def slag_icon(a):
    for (x, y, r) in ((5, 8, 3), (10, 9, 3), (7, 12, 2.5), (9, 5, 2)):
        circ(a, x, y, r, "#2a2d33")
        px(a, int(x) - 1, int(y) - 1, "#6a7a8a")


ICONS = {
    "wire_coil_lv": coil_icon("#1e1f21", "#e0b51e"),
    "lv_fuse": fuse_icon(False), "lv_fuse_blown": fuse_icon(True),
    "star_delta_starter": starter_icon,
    "overhead_conductor": coil_icon("#b8642e"), "overhead_conductor_acsr": coil_icon("#b9bec2"),
    "insulator_pin": insulator_pin_icon,
    "insulator_disc": insulator_disc_icon("#7a3b22"), "insulator_disc_glass": insulator_disc_icon("#8fc7a8"),
    "generator_coupling": coupling_icon(False), "generator_coupling_sheared": coupling_icon(True),
    "fusible_plug": fusible_plug_icon,
    "governor_dashpot": dashpot_icon,
    "silica_gel_breather": breather_icon(False), "silica_gel_breather_saturated": breather_icon(True),
    "relay_buchholz": buchholz_icon,
    "interlock_module": interlock_icon,
    "fly_ash": powder_icon("#9a9a94", "#c4c4bd"),
    "boiler_slag": slag_icon,
}

# цвет маркировки функции защиты (ANSI-код на шильдике) и доступные поколения
RELAYS = {
    "relay_overcurrent": ("#c0392b", ("a1", "a2", "a3"), None),
    "relay_differential": ("#e0b51e", ("a1", "a2", "a3"), None),
    "relay_distance": ("#2f6fb3", ("a2", "a3"), None),
    "relay_frequency": ("#2b8a3e", ("a1", "a2", "a3"), None),
    "relay_voltage": ("#8e44ad", ("a1", "a2", "a3"), None),
    "relay_reverse_power": ("#d35400", ("a1", "a2", "a3"), None),
    "relay_loss_of_field": ("#16a085", ("a2", "a3"), None),
    "relay_out_of_step": ("#7f8c8d", ("a2", "a3"), None),
    "relay_sync_check": ("#f1eee4", ("a1", "a2", "a3"), mini_sync),
    "relay_breaker_failure": ("#c0392b", ("a1", "a2", "a3"), None),
    "relay_autoreclose": ("#27ae60", ("a1", "a2", "a3"), None),
    "relay_ufls": ("#e67e22", ("a1", "a2", "a3"), None),
    "relay_tap_changer": ("#3498db", ("a1", "a2", "a3"), None),
    "relay_thermal": ("#e74c3c", ("a1", "a2", "a3"), None),
}
for _id, (col, gens, extra) in RELAYS.items():
    for i, g in enumerate(gens):
        ICONS[_id if i == 0 else f"{_id}_{g}"] = relay_icon(col, g, extra)

for _id, draw in ICONS.items():
    TEXTURES[f"item/{_id}.png"] = icon(draw)
    item(_id, f"item/{_id}.png")
