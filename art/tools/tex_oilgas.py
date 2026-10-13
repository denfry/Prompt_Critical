"""09: геология нефти, сейсморазведка и каротаж, бурение, добыча, транспорт, НПЗ, газопереработка;
иконки бурового инструмента, катализаторов, продуктов; жидкости и газы (окраска ГОСТ 14202 — на трубах)."""
import math
from artkit2 import *

O = lambda n: f"block/common/oil_{n}.png"


# ======================================================================
# породы
# ======================================================================
def sandstone(base, spots=None, wet=False):
    def fn():
        a = canvas(base, noise=9)
        for y in range(2, 16, 5):
            kbox(a, 0, y, 15, y, 0.9)
        if spots:
            for _ in range(7):
                x, y = rng.randrange(1, 15), rng.randrange(1, 15)
                box(a, x - 1, y, x, y, spots)
                a[y, x, :3] *= 1.25
        if wet:
            for _ in range(10):
                kbox(a, rng.randrange(16), rng.randrange(16), 0, 0, 0.8)
        return a
    return fn


def shale(base, k=0.85):  # тонкослоистая порода
    def fn():
        a = canvas(base, noise=5)
        for y in range(0, 16, 2):
            kbox(a, 0, y, 15, y, k)
        for _ in range(3):
            y = rng.randrange(16)
            kbox(a, 0, y, 15, y, 1.12)
        return a
    return fn


def rock_salt():  # крупные кубические кристаллы галита
    a = canvas("#e8d6d0", noise=5)
    for (x, y, s) in ((1, 1, 5), (8, 2, 6), (2, 9, 5), (9, 10, 5)):
        box(a, x, y, x + s - 1, y, "#f6ecea")
        box(a, x, y, x, y + s - 1, "#f6ecea")
        box(a, x, y + s - 1, x + s - 1, y + s - 1, "#c8aaa2")
        box(a, x + s - 1, y, x + s - 1, y + s - 1, "#c8aaa2")
    return a


def asphalt_film(alpha=255):  # чёрный асфальт с радужной плёнкой
    def fn():
        a = canvas("#141210", noise=4)
        cols = ("#6a3fa0", "#2c7fa8", "#3fae5a", "#c9a14a")
        for i, y in enumerate(range(2, 16, 4)):
            for x in range(16):
                if (x + i * 3) % 7 < 3:
                    a[y, x] = C(cols[(x // 4 + i) % 4])
                    a[y, x, :3] *= 0.6
        a[:, :, 3] = alpha
        return a
    return fn


def barite():  # камень с таблитчатыми кристаллами
    a = canvas("#7d7a76", noise=8)
    for (x, y) in ((2, 3), (9, 2), (5, 9), (11, 10)):
        box(a, x, y, x + 3, y + 1, "#f0ead8")
        box(a, x, y + 2, x + 3, y + 2, "#c8c0a8")
    return a


def tar_sand():
    a = canvas("#3a2e22", noise=10)
    for _ in range(12):
        px(a, rng.randrange(16), rng.randrange(16), "#6a5a46")
    for _ in range(5):
        px(a, rng.randrange(16), rng.randrange(16), "#9a8a72")
    return a


# ======================================================================
# окраски и лицевые панели
# ======================================================================
def deck_plate():  # рифлёный настил буровой с жёлтым бортиком
    a = canvas("#6e7378", noise=3)
    for y in range(1, 16, 3):
        for x in range(1 + (y % 2), 16, 3):
            kbox(a, x, y, x + 1, y, 1.3)
    box(a, 0, 0, 15, 1, "#e0b51e")
    return a


def rope_drum():  # барабан с намоткой талевого каната
    a = canvas("#5a5e62", noise=2)
    for y in range(16):
        for x in range(16):
            if (x + y) % 3 == 0:
                a[y, x, :3] *= 1.3
    return a


def shaker_screen():
    a = canvas("#9aa0a4", noise=2)
    grille(a, 0, 0, 15, 15, step=2, k=0.6)
    grille(a, 0, 0, 15, 15, step=2, k=0.75, vertical=True)
    return a


def weight_indicator32(a):  # индикатор веса Martin-Decker: большая шкала с двумя стрелками (стрелки — BER)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if d < 14:
                a[y, x] = C("#f1eee4")
            if 12 < d < 14:
                a[y, x] = C("#c0392b")
            if d < 2:
                a[y, x] = C("#1d1d1f")
    for k in range(0, 360, 30):
        r = math.radians(k)
        px(a, round(15.5 + 11 * math.cos(r)), round(15.5 + 11 * math.sin(r)), "#1d1d1f")


def seismic32(a):  # сейсмостанция: экран с трассами
    box(a, 3, 3, 28, 20, "#0f1a14")
    for i, y in enumerate(range(5, 19, 3)):
        for x in range(4, 28):
            px(a, x, y + int(1.2 * math.sin(x * 0.9 + i)), "#46d18a")
    for x in range(4, 28, 4):
        box(a, x, 23, x + 2, 25, "#5a5e62")


def esp32(a):  # станция ЭЦН: экран, кнопки
    box(a, 4, 3, 27, 14, "#1d1d1f")
    box(a, 5, 4, 26, 13, "#2d6b46")
    for x in (6, 12, 18, 24):
        circ(a, x, 21, 2.2, "#3fae5a" if x == 6 else "#c0392b" if x == 12 else "#7d8288")


def frac_red():
    return paint("#a8322a", 3)()


def tank_silver():  # РВС: серебристая окраска, сварные пояса
    a = canvas("#c9ccce", noise=3)
    for y in (0, 8):
        kbox(a, 0, y, 15, y, 0.82)
    for x in (5, 13):
        kbox(a, x, 0, x, 7, 0.9)
        kbox(a, (x + 4) % 16, 8, (x + 4) % 16, 15, 0.9)
    return a


def fin_tubes():  # оребрённые трубы АВО
    a = canvas("#a6abae", noise=2)
    for y in range(0, 16, 4):
        box(a, 0, y + 1, 15, y + 2, "#7d8288")
    grille(a, 0, 0, 15, 15, step=2, k=0.85, vertical=True)
    return a


def refractory_grey():
    a = canvas("#8e8a84", noise=6)
    for y in range(0, 16, 4):
        kbox(a, 0, y, 15, y, 0.8)
        for x in range(((y // 4) % 2) * 4, 16, 8):
            kbox(a, x, y, x, y + 3, 0.8)
    return a


def sulfur_drips():  # кожух с жёлтыми подтёками серы
    a = canvas("#8aa0ad", noise=3)
    for x in (3, 7, 12):
        h = rng.randrange(6, 14)
        box(a, x, 0, x, h, "#e8c228")
    return a


def coke_frame():
    return lattice("#5a5e62")()


def drum_cutting():  # закопчённый сосуд камеры коксования
    a = canvas("#7a7e80", noise=6)
    for _ in range(10):
        x = rng.randrange(16)
        kbox(a, x, rng.randrange(8), x, 15, 0.8)
    return a


def wood_derrick():
    a = canvas("#7a5a36", noise=8)
    for x in (0, 7, 8, 15):
        kbox(a, x, 0, x, 15, 0.75)
    return a


# --- иконки ---
def tricone(tci=False, pdc=False):
    def draw(a):
        box(a, 6, 1, 9, 5, "#7d8288")
        if pdc:
            box(a, 3, 5, 12, 12, "#3a3d40")
            for (x, y) in ((4, 7), (7, 6), (10, 7), (5, 10), (9, 10)):
                px(a, x, y, "#cfd6dc")
            return
        for cx in (4.5, 10.5, 7.5):
            circ(a, cx, 9.5 if cx != 7.5 else 11.5, 3, "#9aa0a4")
        for (x, y) in ((3, 8), (5, 10), (10, 8), (12, 10), (7, 12), (8, 13)):
            px(a, x, y, "#d4af37" if not tci else "#e8eef4")
    return draw


def sonde(stripe=None, rings=False, rad=False):
    def draw(a):
        for d in (-1, 0, 1):
            line(a, 2 + d, 13, 13 + d, 2, "#9aa0a4")
        box(a, 12, 1, 14, 3, "#5a5e62")
        if stripe:
            for d in (-1, 0, 1):
                line(a, 6 + d, 9, 8 + d, 7, stripe)
        if rings:
            for t in (5, 8, 11):
                box(a, t - 1, 15 - t - 1, t, 15 - t, "#c7834a")
        if rad:
            box(a, 4, 9, 6, 11, "#e8b923")
            px(a, 5, 10, "#1d1d1f")
    return draw


def collar(a):
    for d in (-2, -1, 0, 1, 2):
        line(a, 3 + d, 13, 13 + d, 3, "#3a3d40")
    box(a, 1, 11, 4, 14, "#7d8288")
    box(a, 12, 1, 15, 4, "#7d8288")


def core_box(tint):
    def draw(a):
        box(a, 1, 5, 14, 12, "#a0784a")
        box(a, 2, 6, 13, 7, tint)
        box(a, 2, 9, 13, 10, tint)
        box(a, 1, 8, 14, 8, "#7a5a36")
    return draw


def cuttings(a):
    for (x, y, c) in ((4, 9, "#8a7a62"), (8, 8, "#6a5a46"), (11, 10, "#9a8a72"), (6, 12, "#7a6a52"), (10, 13, "#5a4a36")):
        box(a, x, y, x + 1, y + 1, c)


def charge(a):
    box(a, 4, 5, 11, 13, "#c0392b")
    box(a, 7, 2, 8, 5, "#3a3d40")
    line(a, 8, 2, 12, 0, "#e8b923")


def slotted(a):
    for d in (-1, 0, 1):
        line(a, 2 + d, 13, 13 + d, 2, "#7d8288")
    for t in (4, 6, 8, 10):
        px(a, t, 15 - t, "#1d1d1f")


def gas_lift(a):
    box(a, 6, 1, 9, 14, "#9aa0a4")
    for y in range(4, 10, 2):
        box(a, 5, y, 10, y, "#7d8288")
    box(a, 6, 12, 9, 13, "#c9a14a")


def rods(a):
    for k in (0, 2, 4):
        line(a, 2 + k, 13, 11 + k, 2, "#7d8288")
        box(a, 6 + k, 8, 7 + k, 7, "#c9a14a")


def rod_pump(a):
    box(a, 6, 2, 9, 14, "#7d8288")
    box(a, 7, 1, 8, 6, "#c9ced3")
    box(a, 6, 10, 9, 10, "#5a5e62")


def esp(a):
    box(a, 6, 1, 9, 6, "#2c5ea8")
    box(a, 6, 7, 9, 10, "#7d8288")
    box(a, 6, 11, 9, 15, "#3a6fb0")
    line(a, 10, 2, 11, 14, "#1d1d1f")


def perf_gun(a):
    for d in (-1, 0, 1):
        line(a, 2 + d, 13, 13 + d, 2, "#5a5e62")
    for t in (4, 7, 10):
        px(a, t, 15 - t, "#e07a1f")


def snuff(a):
    box(a, 3, 4, 12, 14, "#7a6a3a")
    for y in (6, 9, 12):
        box(a, 3, y, 12, y, "#c9c5b8")
    box(a, 6, 2, 9, 4, "#c0392b")


def pig(smart=False):
    def draw(a):
        box(a, 3, 5, 12, 11, "#5a5e62" if smart else "#7d8288")
        for x in (3, 6, 9, 12):
            box(a, x, 4, x, 12, "#c0392b" if not smart else "#2b2b2b")
        if smart:
            box(a, 7, 7, 8, 8, "#46d18a")
    return draw


def anode(a):
    box(a, 3, 6, 12, 11, "#b8bcc0")
    box(a, 3, 6, 12, 6, "#dcdfe2")
    line(a, 12, 8, 15, 4, "#c7834a")


def sample_bottle(filled):
    def draw(a):
        box(a, 5, 4, 10, 14, "#cfe3ea")
        box(a, 6, 2, 9, 3, "#7d8288")
        if filled:
            box(a, 5, 8, 10, 14, "#3a2a1e")
        box(a, 10, 6, 12, 8, "#e6e2d2")
    return draw


def pet_coke(needle=False):
    def draw(a):
        if needle:
            for k in range(0, 10, 2):
                line(a, 3 + k, 13, 6 + k, 4, "#4a4e52")
                px(a, 4 + k, 9, "#b8bec4")
        else:
            lump(("#1d1d1f", "#2b2b2b", "#141414"), hl="#6b7074", spots="#3a3d40")(a)
    return draw


def wax(a):
    box(a, 2, 6, 13, 12, "#f2efe4")
    box(a, 2, 6, 13, 6, "#ffffff")
    box(a, 2, 12, 13, 12, "#d8d4c6")


def identifier(a):  # флажок маркировки: цветное поле, белая стрелка
    box(a, 2, 4, 13, 11, "#d9b21f")
    box(a, 1, 3, 1, 14, "#5a5e62")
    line(a, 4, 7, 10, 7, "#ffffff")
    line(a, 8, 5, 10, 7, "#ffffff")
    line(a, 8, 9, 10, 7, "#ffffff")
    box(a, 12, 4, 13, 11, "#c0392b")


TEXTURES.update({
    "block/reservoir_sandstone.png": sandstone("#5a4430", "#2a1d12"),
    "block/reservoir_sandstone_gas.png": sandstone("#d2c4a2"),
    "block/reservoir_sandstone_water.png": sandstone("#a69c74", wet=True),
    "block/cap_rock_shale.png": shale("#4e5054"),
    "block/rock_salt.png": rock_salt,
    "block/oil_seep.png": asphalt_film(),
    "block/barite_ore.png": barite,
    "block/bituminous_sand.png": tar_sand,
    "block/oil_shale.png": shale("#6a5e50", 0.8),
    "block/oil_slick.png": asphalt_film(110),
    O("deck"): deck_plate,
    O("rope_drum"): rope_drum,
    O("screen"): shaker_screen,
    O("bop_red"): paint("#b02a22", 4),
    O("frac_red"): frac_red,
    O("tank"): tank_silver,
    O("fins"): fin_tubes,
    O("refractory"): refractory_grey,
    O("sulfur_drips"): sulfur_drips,
    O("coke_frame"): coke_frame,
    O("drum"): drum_cutting,
    O("wood"): wood_derrick,
    O("pump_grey_blue"): paint("#6a7f8e", 3),
    O("green_grey"): paint("#7a8a74", 3),
    "block/drillers_console_indicator.png": front32("#2b2f33", weight_indicator32),
    "block/bop_accumulator_gauge.png": dial32(ring="#c0392b"),
    "block/seismic_recorder_face.png": front32("#a9acaa", seismic32),
    "block/esp_control_panel_face.png": front32("#a9acaa", esp32),
    "item/drill_bit_milled_tooth.png": icon(tricone()),
    "item/drill_bit_tci.png": icon(tricone(tci=True)),
    "item/drill_bit_pdc.png": icon(tricone(pdc=True)),
    "item/drill_collar.png": icon(collar),
    "item/barite_powder.png": icon(sack("#f0ece0", "#7d8288")),
    "item/drill_cuttings.png": icon(cuttings),
    "item/core_barrel.png": icon(ic_tube("#7d8288", "#c9a14a")),
    "item/reservoir_core.png": icon(core_box("#3a2a1e")),
    "item/seismic_charge.png": icon(charge),
    "item/neutron_logging_sonde.png": icon(sonde(rad=True)),
    "item/gamma_logging_sonde.png": icon(sonde("#3fae5a")),
    "item/resistivity_logging_sonde.png": icon(sonde(rings=True)),
    "item/slotted_liner.png": icon(slotted),
    "item/gas_lift_valve.png": icon(gas_lift),
    "item/sucker_rod.png": icon(rods),
    "item/downhole_rod_pump.png": icon(rod_pump),
    "item/esp_unit.png": icon(esp),
    "item/perforating_gun.png": icon(perf_gun),
    "item/frac_proppant.png": icon(sack("#d8cba0", "#c8963e")),
    "item/snuffing_charge.png": icon(snuff),
    "item/pig.png": icon(pig()),
    "item/smart_pig.png": icon(pig(True)),
    "item/sacrificial_anode.png": icon(anode),
    "item/sample_bottle.png": icon(sample_bottle(False)),
    "item/sample_bottle_filled.png": icon(sample_bottle(True)),
    "item/catalyst_reforming.png": icon(ic_drum("#2c5ea8", "#9aa0a4")),
    "item/catalyst_fcc.png": icon(sack("#f4f2ec", "#2c5ea8")),
    "item/catalyst_hydrotreating.png": icon(ic_drum("#2c5ea8", "#3f8f8a")),
    "item/catalyst_claus.png": icon(ic_drum("#2c5ea8", "#f0ece0")),
    "item/catalyst_hydrocracking.png": icon(ic_drum("#2c5ea8", "#7d8288")),
    "item/petroleum_coke.png": icon(pet_coke()),
    "item/needle_coke.png": icon(pet_coke(True)),
    "item/sulfur.png": icon(ic_pellets(("#f2d43a", "#e8c228", "#ffe066"))),
    "item/paraffin_wax.png": icon(wax),
    "item/fluid_identifier.png": icon(identifier),
})

for _i in ("drill_bit_milled_tooth", "drill_bit_tci", "drill_bit_pdc", "drill_collar", "barite_powder", "drill_cuttings",
           "core_barrel", "reservoir_core", "seismic_charge", "neutron_logging_sonde", "gamma_logging_sonde",
           "resistivity_logging_sonde", "slotted_liner", "gas_lift_valve", "sucker_rod", "downhole_rod_pump", "esp_unit",
           "perforating_gun", "frac_proppant", "snuffing_charge", "pig", "smart_pig", "sacrificial_anode", "sample_bottle",
           "catalyst_reforming", "catalyst_fcc", "catalyst_hydrotreating", "catalyst_claus", "catalyst_hydrocracking",
           "petroleum_coke", "needle_coke", "sulfur", "paraffin_wax", "fluid_identifier"):
    item(_i, f"item/{_i}.png")

# жидкости и газы 09: цвет самой среды (окраска трубы по ГОСТ 14202 рисует клиент)
for _id, _c, _k in (
        ("crude_oil", "#2a1d12", "liquid"), ("natural_gas", "#d8dcd8", "gas"), ("associated_gas", "#e2dcb8", "gas"),
        ("sales_gas", "#e0e4e2", "gas"), ("ngl", "#e8ecea", "liquid"), ("ethane", "#e4e8e8", "gas"),
        ("propane", "#e6eaea", "liquid"), ("butane", "#e2e6e6", "liquid"), ("lpg", "#eaeeee", "liquid"),
        ("naphtha", "#efe6c0", "liquid"), ("gasoline", "#f0e08a", "liquid"), ("reformate", "#e6e28a", "liquid"),
        ("fcc_gasoline", "#f0b850", "liquid"), ("kerosene", "#dfe8ee", "liquid"), ("diesel_fuel", "#d89a2a", "liquid"),
        ("light_cycle_oil", "#a8661e", "liquid"), ("atmospheric_residue", "#17110c", "liquid"),
        ("fuel_oil", "#1a130d", "liquid"), ("vacuum_gas_oil", "#4a2c16", "liquid"), ("vacuum_residue", "#0f0b08", "liquid"),
        ("decant_oil", "#141a12", "liquid"), ("bitumen", "#0c0a08", "liquid"), ("lube_oil", "#d8a02a", "liquid"),
        ("transformer_oil", "#ead8a0", "liquid"), ("refinery_fuel_gas", "#d4d6cc", "gas"),
        ("drilling_mud", "#6e6252", "liquid"), ("produced_water", "#7a7e74", "liquid"),
        ("injection_water", "#7ab0d8", "liquid"), ("sour_water", "#c8c07a", "liquid"),
        ("mdea_solution", "#e8e0b0", "liquid"), ("teg_solution", "#e6e8ea", "liquid"),
        ("molten_sulfur", "#f0a830", "liquid"), ("odorant", "#e8eaec", "liquid")):
    fluid(_id, _c, _k)
