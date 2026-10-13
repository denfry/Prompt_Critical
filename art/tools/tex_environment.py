"""14: биржа и товарная станция, экомониторинг, газоочистка ТЭС, ликвидация разливов, загрязнённые
блоки мира; иконки реагентов и сорбентов; жидкости."""
from artkit2 import *

E = lambda n: f"block/common/env_{n}.png"


def market_board32(a):  # табло котировок: строки, бегущая строка снизу
    box(a, 2, 2, 29, 22, "#0f0f10")
    for i, y in enumerate(range(4, 21, 4)):
        box(a, 4, y, 14, y + 1, "#ffb04a")
        box(a, 18, y, 27, y + 1, "#46d18a" if i % 2 else "#e05a3a")
    box(a, 2, 25, 29, 29, "#0f0f10")
    for x in range(4, 28, 3):
        box(a, x, 27, x + 1, 27, "#ffb04a")


def depot_door(a):
    for y in range(1, 15, 3):
        box(a, 1, y, 14, y, "#7d8288")
    box(a, 0, 13, 15, 15, "#e8b923")


def louvre_white():
    a = canvas("#f0f0ec", noise=2)
    for y in range(1, 16, 3):
        box(a, 0, y, 15, y, "#c8c8c2")
    return a


def sooty_steel():
    a = canvas("#3e3c3a", noise=6)
    for y in range(0, 16, 4):
        kbox(a, 0, y, 15, y, 1.2)
    box(a, 0, 6, 15, 8, "#d9b21f")
    return a


def gypsum_streaks():
    a = canvas("#9aa0a4", noise=4)
    for x in (2, 7, 12):
        box(a, x, rng.randrange(4, 10), x, 15, "#ecebe4")
    return a


def boom_float():
    a = canvas("#e07a1f", noise=3)
    box(a, 0, 12, 15, 15, "#2b2b2b")
    return a


def oiled(base, n=12):
    def fn():
        a = canvas(base, noise=8)
        for _ in range(n):
            x, y = rng.randrange(16), rng.randrange(16)
            circ(a, x, y, rng.uniform(0.8, 2.0), "#14110e")
        return a
    return fn


def withered():
    a = blank()
    for y in range(16):
        for x in range(16):
            if rng.random() < 0.82:
                a[y, x] = C(rng.choice(("#7a5a32", "#8a6a3a", "#6a4a2a", "#9a7a42")))
    return a


def liner():
    a = canvas("#1d1e1f", noise=2)
    for x in range(0, 16, 8):
        kbox(a, x, 0, x, 15, 1.3)
    return a


TEXTURES.update({
    "block/market_terminal_board.png": front32("#3a3d40", market_board32),
    "block/trade_depot_door.png": front16("#9aa0a4", depot_door),
    E("louvre_white"): louvre_white,
    E("flue"): sooty_steel,
    E("gypsum"): gypsum_streaks,
    E("boom"): boom_float,
    "block/oiled_sand.png": oiled("#d8c89a", 8),
    "block/oiled_sand_1.png": oiled("#d8c89a", 14),
    "block/oiled_sand_2.png": oiled("#c8b88a", 22),
    "block/oiled_gravel.png": oiled("#8a8680", 8),
    "block/oiled_gravel_1.png": oiled("#8a8680", 14),
    "block/oiled_gravel_2.png": oiled("#7a7670", 22),
    "block/withered_leaves.png": withered,
    "block/pond_liner.png": liner,
    "item/limestone_powder.png": icon(powder("#c8c8c0", "#a8a8a0")),
    "item/fgd_gypsum.png": icon(powder("#f2f0ea", "#d8d6cc")),
    "item/filter_bag.png": icon(ic_tube("#e6e2d2", "#9aa0a4", w=2)),
    "item/catalyst_denox.png": icon(ic_box("#d8d4b4", None, None)),
    "item/sorbent_pad.png": icon(ic_box("#f4f4f0", "#e0e0d8")),
    "item/oiled_sorbent.png": icon(ic_box("#3a2a1e", "#1d1410")),
    "item/supply_contract.png": icon(ic_doc("#2c5ea8", "#2c5ea8")),
})

for _i in ("limestone_powder", "fgd_gypsum", "filter_bag", "catalyst_denox", "sorbent_pad", "oiled_sorbent",
           "supply_contract"):
    item(_i, f"item/{_i}.png")

fluid("limestone_slurry", "#d8d8d0")
fluid("recovered_oil", "#5a3a1e")
