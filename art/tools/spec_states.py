"""Состояния M2 после волн: жаротрубный котёл 07 §7.23 — `low_water` и `exploded` (геометрия — как
`fire_tube` в spec_grid). Модуль грузится последним — индексы готовых моделей в spec.json не сдвигаются."""
from artkit import *

G = lambda n: f"block/common/grid_{n}.png"
S = lambda n: f"block/common/state_{n}.png"
OWN = {
    "riv": G("riveted"), "ci": G("cast_iron"), "brk": G("brick"),
    "fdoor": B("fire_tube_boiler_door"), "fdoorl": B("fire_tube_boiler_door_lit"),
    "gle": S("gauge_glass_empty"), "tint": S("heat_tint"), "crown": S("glow_crown"),
    "torn": S("riveted_torn"), "soot": S("soot"), "dw": S("boiler_door_wrecked"),
}


def m(id_, cat, els, kind="block"):
    mk(id_, cat, els, OWN, kind)


def saddles():
    return [el("saddle_w", [-10, -16, 0], [-4, -5, 4], "brk"),
            el("saddle_e", [20, -16, 0], [26, -5, 4], "brk")]


def low_water(lit):
    """Уровень ушёл из стекла: стекло сухое, над топкой — побежалость; при горящей топке днище светится."""
    door = "fdoorl" if lit else "fdoor"
    return disc("shell", "z", 15, -15.2, 31.9, "riv", faces={"north": "ci"}) + [
        el("front_plate", [-12, -12, -15.5], [28, 28, -15.2], "ci"),
        el("door_l", [-8, -10, -16], [6, 2, -15.5], door, full=["north"]),
        el(("glow_" if lit else "") + "door_r", [10, -10, -16], [24, 2, -15.5], door, full=["north"]),
        el(("glow_crown_heat" if lit else "crown_tint"), [-10, 3, -15.6], [26, 9, -15.5], "crown" if lit else "tint"),
        el("gauge_glass", [-6, 10, -16], [-4, 18, -15.5], "gle"),
        el("safety_valve", [6, 23, 4], [10, 28, 8], "bs"),
        el("manhole", [4, 22, 14], [12, 24, 22], "d"),
    ] + saddles()


m("fire_tube_boiler_low_water", "boilers", low_water(False))
m("fire_tube_boiler_lit_low_water", "boilers", low_water(True))

# разрыв обечайки: верх распорот, кромки отогнуты наружу, дверцу сорвало с петли, ПК и стекло снесены
m("fire_tube_boiler_exploded", "boilers", disc("shell", "z", 15, -15.2, 31.9, "torn", faces={"north": "torn"}) + [
    el("front_plate", [-12, -12, -15.5], [28, 28, -15.2], "torn"),
    el("rupture", [0, 22.9, -8], [16, 23.3, 26], "soot"),
    el("flap_w", [-2, 22, -6], [1, 30, 24], "torn", rot=("z", 22.5, [0, 23, 8])),
    el("flap_e", [15, 22, -6], [18, 30, 24], "torn", rot=("z", -22.5, [16, 23, 8])),
    el("door_l", [-8, -10, -16], [6, 2, -15.5], "dw", full=["north"]),
    el("door_r", [10, -10, -16], [24, 2, -15.5], "dw", full=["north"], rot=("y", 45, [24, -4, -16])),
    el("glass_stub", [-6, 10, -16], [-4, 12, -15.5], "soot"),
    el("manhole", [4, 22, 14], [12, 24, 22], "d", rot=("x", 22.5, [8, 23, 18])),
] + saddles())
m("fire_tube_boiler_rear_exploded", "boilers", disc("shell", "z", 15, -15.9, 28, "torn", faces={"south": "torn"}) + [
    el("rupture", [0, 22.9, -16], [16, 23.3, 20], "soot"),
    el("smokebox", [-10, -16, 28], [26, 20, 32], "torn"),
    el("uptake", [4, 20, 26], [12, 30, 32], "d", rot=("x", 22.5, [8, 20, 29])),
])
