"""10: печи пиролиза и конверсии, закалка, адсорберы, газогенератор, хлор-щелочное производство,
полимеризация и переработка пластмасс, брожение; иконки полимеров, реагентов, катализаторов; жидкости."""
from artkit2 import *

P2 = lambda n: f"block/common/petro_{n}.png"


def scale_coil():  # змеевик: красно-коричневая окалина
    a = canvas("#7a3e2a", noise=10)
    for y in range(0, 16, 4):
        kbox(a, 0, y, 15, y, 0.75)
    return a


def fin_rows():  # ряды оребрённых труб в разрезе
    a = canvas("#4a4e52", noise=2)
    for y in range(2, 16, 5):
        for x in range(1, 16, 4):
            circ(a, x + 1, y + 1, 1.8, "#9aa0a4")
            px(a, x + 1, y + 1, "#2b2b2b")
    return a


def bms_front(a):  # щит печи: лампы и ключ
    for i, c in enumerate(("#3fae5a", "#e8b923", "#e07a1f", "#2c5ea8", "#c0392b")):
        box(a, 2 + i * 2 + i, 3, 3 + i * 3, 4, c)
    box(a, 4, 8, 11, 12, "#e6e2d2")
    circ(a, 12, 13, 1.2, "#3a3d40")


def riveted_steel():
    a = canvas("#5e6266", noise=4)
    for y in (1, 14):
        for x in range(1, 16, 3):
            rivet(a, y, x)
    for x in (1, 14):
        for y in range(1, 16, 3):
            rivet(a, y, x)
    return a


def enamel():  # эмалированная сталь реактора
    a = canvas("#e4e8ea", noise=2)
    for y in range(16):
        kbox(a, 0, y, 2, y, 0.92)
    return a


def foam():  # пена бродильного чана
    a = canvas("#ede4c8", noise=6)
    for _ in range(14):
        x, y = rng.randrange(16), rng.randrange(16)
        circ(a, x, y, 1.3, "#f8f2de", 0.5)
    return a


def pvc_blue():
    return paint("#7fb2d4", 2)()


def oily_steel():
    a = canvas("#585c5e", noise=4)
    for _ in range(5):
        x = rng.randrange(16)
        box(a, x, rng.randrange(8), x, 15, "#3a3020")
    return a


def heater_bands():  # цилиндр экструдера с хомутными нагревателями
    a = canvas("#8e9296", noise=3)
    for x in range(0, 16, 5):
        box(a, x, 0, x + 2, 15, "#5a3a2a")
    return a


def graphite_black():
    a = canvas("#232425", noise=3)
    frame(a, 1.25, 0.75)
    return a


# --- иконки ---
def pellets_bag(fill, mark, clear=False):
    def draw(a):
        sack(fill, mark)(a)
        if clear:
            for (x, y) in ((6, 8), (8, 9)):
                px(a, x, y, "#ffffff")
    return draw


def disc_blank(a):
    circ(a, 7.5, 8, 6, "#f4f4f2")
    circ(a, 7.5, 8, 6, "#d8d8d4", 5)
    px(a, 5, 5, "#ffffff")


def brick_dark(col, hl):
    def draw(a):
        box(a, 2, 5, 13, 11, col)
        box(a, 2, 5, 13, 5, hl)
    return draw


def membrane(a):
    circ(a, 5, 9, 4, "#cfe6e8", 1.5)
    box(a, 5, 5, 13, 12, "#cfe6e8")
    for x in range(6, 13, 2):
        kbox(a, x, 5, x, 12, 0.92)
    circ(a, 5, 9, 1.4, "#9aa0a4")


def diaphragm(a):
    box(a, 2, 2, 13, 13, "#8e9296")
    grille(a, 2, 2, 13, 13, step=2, k=0.7)
    box(a, 4, 4, 11, 11, "#b8b4a8")


def fiber(a):
    lump(("#8a9a82", "#9aa88e", "#7a8a72"), hl="#c0c8b8", n=4)(a)


def u_coil(a):
    for d in (0, 1):
        line(a, 3 + d, 2, 3 + d, 12, "#7a3e2a")
        line(a, 11 + d, 2, 11 + d, 12, "#7a3e2a")
    for x in range(4, 12):
        px(a, x, 13, "#7a3e2a")
        px(a, x, 12, "#6a2e1a")


def pcl3_ampoule(a):
    box(a, 6, 3, 9, 13, "#dfe8ec")
    box(a, 7, 1, 8, 2, "#dfe8ec")
    box(a, 6, 7, 9, 13, "#e8d24a")


def phosphorus_jar(a):
    box(a, 4, 4, 11, 14, "#bcd6e0")
    box(a, 5, 3, 10, 3, "#3a3d40")
    for (x, y) in ((5, 9), (8, 10), (6, 12)):
        box(a, x, y, x + 2, y + 1, "#f2ecc0")


def oxidizer_bag(a):
    sack("#f4f2ec", "#e8b923")(a)
    px(a, 7, 8, "#1d1d1f")
    px(a, 8, 9, "#1d1d1f")


def silver_gauze(a):
    box(a, 2, 2, 13, 13, "#cfd3d6")
    grille(a, 2, 2, 13, 13, step=2, k=0.7)
    grille(a, 2, 2, 13, 13, step=2, k=0.85, vertical=True)


def pyro_can(a):
    ic_jar("#7d8288", "#2c5ea8", "#e8b923")(a)
    px(a, 7, 9, "#c0392b")
    px(a, 8, 10, "#c0392b")


TEXTURES.update({
    P2("scale_coil"): scale_coil,
    P2("fin_rows"): fin_rows,
    P2("riveted"): riveted_steel,
    P2("enamel"): enamel,
    P2("foam"): foam,
    P2("pvc_blue"): pvc_blue,
    P2("oily"): oily_steel,
    P2("heater_bands"): heater_bands,
    P2("graphite"): graphite_black,
    P2("glow_violet"): liquid("#c8a0ff", 0.2),
    "block/tube_furnace_controller_front.png": front16("#cfd1ca", bms_front),
    "item/polyethylene.png": icon(pellets_bag("#f4f2ec", "#2c5ea8")),
    "item/polypropylene.png": icon(pellets_bag("#e8ecec", "#3f8f4a", True)),
    "item/pvc.png": icon(sack("#f4f2ec", "#2c5ea8")),
    "item/ptfe.png": icon(disc_blank),
    "item/synthetic_rubber.png": icon(brick_dark("#4a2e1e", "#6a4a36")),
    "item/epoxy_resin.png": icon(ic_jar("#b0b4b8", "#5a5e62", "#d89a2a")),
    "item/epoxy_hardener.png": icon(ic_jar("#b0b4b8", "#c0392b", "#e6e2d2")),
    "item/bakelite.png": icon(brick_dark("#4a2c1d", "#7a4a2d")),
    "item/bakelite_molding_powder.png": icon(sack("#d8cba0", "#6b4a2a")),
    "item/ion_exchange_resin.png": icon(sack("#e8dcc0", "#d89a2a")),
    "item/ion_exchange_resin_sba.png": icon(sack("#e8dcc0", "#f0e08a")),
    "item/ion_exchange_resin_chelating.png": icon(sack("#e8dcc0", "#d8ccb0")),
    "item/ion_exchange_membrane.png": icon(membrane),
    "item/diaphragm_pack.png": icon(diaphragm),
    "item/mineral_fiber.png": icon(fiber),
    "item/furnace_coil.png": icon(u_coil),
    "item/hexamine.png": icon(ic_flakes("#f4f4f2", "#dcdcd8")),
    "item/bisphenol_a.png": icon(ic_flakes("#f2f0e8", "#e0dccc")),
    "item/white_phosphorus.png": icon(phosphorus_jar),
    "item/ammonium_nitrate.png": icon(oxidizer_bag),
    "item/urea.png": icon(sack("#f4f2ec", "#7d8288")),
    "item/carbon_black.png": icon(powder("#1d1d1f", "#3a3d40")),
    "item/zinc_oxide_sorbent.png": icon(ic_pellets(("#f4f2ec", "#e6e4dc"), "extrudate")),
    "item/molecular_sieve.png": icon(ic_pellets(("#d8ccb0", "#c8bc9e"))),
    "item/catalyst_steam_reforming.png": icon(ic_pellets(("#9aa0a4", "#7d8288"), "ring")),
    "item/catalyst_shift_ht.png": icon(ic_pellets(("#8a5a3a", "#7a4a2c"), "tablet")),
    "item/catalyst_shift_lt.png": icon(ic_pellets(("#2b2b2b", "#3a3d40"), "tablet")),
    "item/catalyst_methanol.png": icon(ic_pellets(("#2b2b2b", "#7a4a2c"), "tablet")),
    "item/catalyst_formaldehyde.png": icon(silver_gauze),
    "item/catalyst_acetylene_hydrogenation.png": icon(ic_pellets(("#8a5a3a", "#3a2a1e"))),
    "item/catalyst_oxychlorination.png": icon(ic_pellets(("#4fa8a0", "#3f8f8a"))),
    "item/catalyst_styrene.png": icon(ic_pellets(("#8a3a2a", "#a04a32"), "extrudate")),
    "item/catalyst_ziegler.png": icon(pyro_can),
    "item/catalyst_fluorination.png": icon(pcl3_ampoule),
})

for _i in ("polyethylene", "polypropylene", "pvc", "ptfe", "synthetic_rubber", "epoxy_resin", "epoxy_hardener", "bakelite",
           "bakelite_molding_powder", "ion_exchange_resin", "ion_exchange_membrane", "diaphragm_pack", "mineral_fiber",
           "furnace_coil", "hexamine", "bisphenol_a", "white_phosphorus", "ammonium_nitrate", "urea", "carbon_black",
           "zinc_oxide_sorbent", "molecular_sieve", "catalyst_steam_reforming", "catalyst_shift_ht", "catalyst_shift_lt",
           "catalyst_methanol", "catalyst_formaldehyde", "catalyst_acetylene_hydrogenation", "catalyst_oxychlorination",
           "catalyst_styrene", "catalyst_ziegler", "catalyst_fluorination"):
    item(_i, f"item/{_i}.png")

for _id, _c, _k in (
        ("syngas", "#dcdcd0", "gas"), ("methanol", "#e6eaee", "liquid"), ("ethylene", "#e0e2de", "gas"),
        ("propylene", "#e0e0da", "gas"), ("butadiene", "#e2e0d8", "gas"), ("pyrolysis_gasoline", "#f0e4a0", "liquid"),
        ("pyrolysis_fuel_oil", "#14100c", "liquid"), ("hydrochloric_acid", "#f2eab8", "liquid"),
        ("chloroform", "#e4e8ec", "liquid"), ("chlorodifluoromethane", "#e2e6e8", "gas"),
        ("tetrafluoroethylene", "#e0e4e6", "gas"), ("vinyl_chloride", "#e2e2dc", "gas"),
        ("ethylene_dichloride", "#e6e8e4", "liquid"), ("formaldehyde_solution", "#e8ecee", "liquid"),
        ("acetone", "#eaeef0", "liquid"), ("styrene", "#e8e6dc", "liquid"), ("n_butanol", "#eaece8", "liquid"),
        ("ethanol", "#ecf0f2", "liquid"), ("phosphorus_trichloride", "#e4e6e6", "liquid"),
        ("phosphoryl_chloride", "#e6e8e8", "liquid"), ("tbp", "#efe6b8", "liquid"), ("epichlorohydrin", "#e8eaea", "liquid"),
        ("sulfur_hexafluoride", "#d8dce0", "gas")):
    fluid(_id, _c, _k)
