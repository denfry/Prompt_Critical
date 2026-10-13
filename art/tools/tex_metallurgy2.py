"""08 после M3: ильменитовая россыпь, смолоперегонный куб, аффинаж МПГ, станки; иконки и жидкости коксохимии."""
from artkit2 import *

X2 = lambda n: f"block/common/met2_{n}.png"


def black_sand():  # чёрный тяжёлый песок с бликами ильменита
    a = canvas("#2e2c2a", noise=10)
    for _ in range(18):
        px(a, rng.randrange(16), rng.randrange(16), "#6b6f74")
    for _ in range(6):
        px(a, rng.randrange(16), rng.randrange(16), "#b0905a")
    return a


def tar():  # смоляные потёки на сером металле
    a = canvas("#6a6e70", noise=4)
    for x in (2, 6, 11, 13):
        h = rng.randrange(5, 14)
        box(a, x, 0, x, h, "#2a1f17")
        px(a, x, h + 1, "#3a2a1e")
    return a


def porcelain_white():
    a = canvas("#ecebe4", noise=2)
    frame(a, 1.05, 0.85)
    return a


def cnc_screen(a):
    box(a, 2, 2, 13, 9, "#1d1d1f")
    box(a, 3, 3, 12, 8, "#3c7fd0")
    for y in (4, 6):
        box(a, 4, y, 10, y, "#d6e8ff")
    for x in range(3, 13, 2):
        box(a, x, 11, x, 12, "#5a5e62")


# --- иконки ---
def impeller_icon(a):
    circ(a, 7.5, 7.5, 6.5, "#b08a4a")
    for (x, y) in ((7, 2), (12, 7), (7, 12), (2, 7), (4, 4), (11, 4), (11, 11), (4, 11)):
        px(a, x, y, "#7a5a2a")
    circ(a, 7.5, 7.5, 1.8, "#5a5e62")


def tuyere_icon(a):
    for y in range(3, 13):
        w = (y - 3) // 2
        box(a, 7 - w, y, 8 + w, y, "#c06a3a")
    circ(a, 7.5, 12, 2, "#5a2a1a")


TEXTURES.update({
    "block/ilmenite_sand.png": black_sand,
    X2("tar"): tar,
    X2("porcelain"): porcelain_white,
    X2("beige_machine"): paint("#d6ceb4", 3),
    "block/cnc_machining_center_screen.png": front16("#d6ceb4", cnc_screen),
    "item/naphthalene.png": icon(ic_flakes("#f4f2ec", "#d8d6cc")),
    "item/pgm_concentrate.png": icon(ic_jar("#e6e2d2", "#3a3d40", "#5a5e62")),
    "item/iron_pellets.png": icon(ic_pellets(("#7a4a32", "#8a5a3a", "#6b3e2a"))),
    "item/cobalt_ingot.png": icon(ingot("#7f9bb8", "#5f7b98")),
    "item/platinum_ingot.png": icon(ingot("#e4e6e8", "#b8bcc0")),
    "item/iridium_ingot.png": icon(ic_tube("#cdd2d6", "#9aa0a4", lo=(4, 11), hi=(11, 4))),
    "item/titanium_ingot.png": icon(ingot("#8e9296", "#6e7276")),
    "item/pump_impeller.png": icon(impeller_icon),
    "item/tuyere_spare.png": icon(tuyere_icon),
})

for _i in ("naphthalene", "pgm_concentrate", "iron_pellets", "cobalt_ingot", "platinum_ingot", "iridium_ingot",
           "titanium_ingot"):
    item(_i, f"item/{_i}.png")

fluid("benzene", "#e8e2c8")
fluid("toluene", "#e6dcc0")
fluid("xylenes", "#e2d6b4")
fluid("phenol", "#e8c8c0")
