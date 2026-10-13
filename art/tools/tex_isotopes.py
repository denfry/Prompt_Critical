"""12: облучательные каналы, пневмопочта, гамма-облучатель, НЛК кремния, детритизация, РИТЭГи,
транспортные упаковки, радиоизотопный уровнемер; иконки источников и дефектоскопии."""
from artkit2 import *

I = lambda n: f"block/common/iso_{n}.png"


def heavy_concrete(sign=False):
    def fn():
        a = canvas("#8e8c86", noise=6)
        for _ in range(10):
            px(a, rng.randrange(16), rng.randrange(16), "#6e6c66")
        if sign:
            for y in range(3, 13):
                for x in range(3, 13):
                    a[y, x] = C("#e8b923")
            trefoil_px(a, 7.5, 7.5, 4, bg=None, fg="#7b2a8f")
        return a
    return fn


def hazard_edge():  # жёлто-чёрная окантовка двери
    a = canvas("#8e8c86", noise=5)
    for y in range(16):
        for x in list(range(0, 2)) + list(range(14, 16)):
            a[y, x] = C("#e8b923") if ((x + y) // 2) % 2 == 0 else C("#1d1d1f")
    return a


def pool_liner():  # облицовка бассейна нержавейкой: листы
    a = canvas("#b8c0c4", noise=2)
    for i in (0, 8):
        kbox(a, 0, i, 15, i, 0.85)
        kbox(a, i, 0, i, 15, 0.85)
    return a


def rtg_fins(hot=False):
    def fn():
        a = canvas("#3a3d40", noise=3)
        for x in range(0, 16, 3):
            box(a, x, 0, x, 15, "#5a5e62")
            if hot:
                box(a, x, 0, x, 1, "#d0503a")
        return a
    return fn


def yellow_case():
    a = canvas("#e0b51e", noise=3)
    frame(a, 1.1, 0.75)
    box(a, 5, 5, 10, 10, "#1d1d1f")
    trefoil_px(a, 7.5, 7.5, 2.4, bg="#e8b923")
    return a


def lead_case():
    a = canvas("#6e7276", noise=4)
    for y in (3, 12):
        kbox(a, 0, y, 15, y, 0.8)
    return a


def label_b(a):
    box(a, 2, 4, 13, 11, "#e6e2d2")
    trefoil_px(a, 5, 7.5, 2, bg="#e8b923")
    box(a, 9, 6, 12, 6, "#1d1d1f")
    box(a, 9, 9, 12, 9, "#1d1d1f")


def rabbit_face(a):
    box(a, 3, 3, 12, 8, "#2b2f33")
    box(a, 4, 4, 11, 7, "#3a4a52")
    for x in (4, 8, 12):
        px(a, x, 11, "#5a5e62")


# --- иконки ---
def capsule(band):
    def draw(a):
        box(a, 6, 2, 9, 13, "#c4c8cc")
        box(a, 6, 2, 9, 2, "#e6eaec")
        box(a, 6, 7, 9, 8, band)
        box(a, 7, 13, 8, 14, "#9aa0a4")
    return draw


def sealed_source(a):
    box(a, 5, 6, 10, 10, "#9aa0a4")
    box(a, 10, 7, 13, 9, "#7d8288")
    box(a, 5, 6, 10, 6, "#c9ced3")
    box(a, 4, 7, 4, 9, "#c0392b")
    trefoil_px(a, 7.5, 8, 1.4, bg="#e8b923")


def crank(a):
    circ(a, 7, 9, 5, "#3a3d40", 3)
    circ(a, 7, 9, 2, "#7d8288")
    line(a, 7, 9, 13, 3, "#7d8288")
    box(a, 13, 2, 14, 3, "#c0392b")
    line(a, 2, 13, 0, 15, "#1d1d1f")


def film(exposed):
    def draw(a):
        box(a, 3, 3, 12, 13, "#e8e2c8" if not exposed else "#1d1d1f")
        if exposed:
            box(a, 4, 7, 11, 8, "#f2f2ee")
        else:
            box(a, 3, 3, 12, 4, "#c0392b")
            box(a, 5, 7, 10, 9, "#a0988a")
    return draw


def pu_source(a):
    box(a, 3, 4, 12, 12, "#2b2b2b")
    circ(a, 7.5, 8, 2.5, "#c9ced3")
    circ(a, 7.5, 8, 1.2, "#ff9a5a")


def triga(a):
    for d in (-1, 0, 1):
        line(a, 3 + d, 13, 12 + d, 4, "#b8bcc0")
    box(a, 11, 2, 13, 4, "#7d8288")
    box(a, 2, 12, 4, 14, "#7d8288")


TEXTURES.update({
    I("heavy_concrete"): heavy_concrete(),
    "block/gamma_irradiator_cell_wall_sign.png": heavy_concrete(True),
    I("door_edge"): hazard_edge,
    I("pool_liner"): pool_liner,
    I("rtg_fins"): rtg_fins(),
    I("glow_rtg_fins"): rtg_fins(True),
    I("yellow_case"): yellow_case,
    I("lead_case"): lead_case,
    "block/source_container_type_b_label.png": front16("#6e7276", label_b),
    "block/pneumatic_rabbit_terminal_face.png": front16("#c4c8cc", rabbit_face),
    "item/irradiation_capsule.png": icon(capsule("#2c5ea8")),
    "item/sealed_source.png": icon(sealed_source),
    "item/radiography_crank.png": icon(crank),
    "item/radiography_film.png": icon(film(False)),
    "item/radiography_film_exposed.png": icon(film(True)),
    "item/well_logging_tool.png": icon(ic_tube("#9aa0a4", "#5a5e62")),
    "item/pu238_heat_source.png": icon(pu_source),
    "item/sr90_heat_source.png": icon(capsule("#7d8288")),
    "item/npo2_powder.png": icon(ic_jar("#e6e2d2", "#3a3d40", "#7a8a3a")),
    "item/xlpe.png": icon(ic_pellets(("#eef4f8", "#d8e6f0"))),
    "item/doped_silicon_ingot.png": icon(ingot("#8e9399", "#7b3fa0")),
    "item/mo99_target.png": icon(ic_box("#b8bcc0", "#7d8288")),
    "item/triga_fuel_element.png": icon(triga),
})

for _i in ("irradiation_capsule", "sealed_source", "radiography_crank", "radiography_film", "radiography_film_exposed",
           "well_logging_tool", "pu238_heat_source", "sr90_heat_source", "npo2_powder", "xlpe", "doped_silicon_ingot",
           "mo99_target", "triga_fuel_element"):
    item(_i, f"item/{_i}.png")
