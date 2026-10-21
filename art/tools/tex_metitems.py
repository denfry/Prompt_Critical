"""08: иконки сырья, полупродуктов и металлов; жидкости и газы металлургии."""
from artkit import *

Y = lambda n: f"block/common/metitem_{n}.png"


def lump(cols, hl=None, spots=None, n=4):
    """Кусок руды/угля: неровный многоугольник из нескольких кругов."""
    def draw(a):
        for i, (x, y, r) in enumerate(((7, 8, 5), (10, 6, 3.5), (5, 10, 3.5), (9, 11, 3))[:n]):
            circ(a, x, y, r, cols[i % len(cols)])
        if spots:
            for (x, y) in ((5, 7), (9, 9), (7, 11), (11, 6), (6, 9)):
                px(a, x, y, spots)
        if hl:
            px(a, 6, 5, hl)
            px(a, 10, 4, hl)
    return draw


def powder(col, col2=None):
    def draw(a):
        for y in range(7, 14):
            w = y - 6
            box(a, 7 - w, y, 8 + w, y, col)
        if col2:
            for (x, y) in ((5, 11), (9, 10), (7, 12), (10, 12), (3, 13)):
                px(a, x, y, col2)
    return draw


def sack(fill, mark):
    def draw(a):
        box(a, 3, 4, 12, 14, "#d8cba0")
        box(a, 5, 2, 10, 3, "#c8bb90")
        box(a, 5, 7, 10, 10, fill)
        box(a, 6, 8, 9, 9, mark)
    return draw


def ingot(col, dark=None):
    def draw(a):
        box(a, 2, 7, 13, 11, col)
        box(a, 4, 5, 11, 6, col)
        box(a, 4, 5, 11, 5, "#ffffff" if not dark else col)
        if dark:
            box(a, 2, 11, 13, 11, dark)
    return draw


def plate(col, lugs=False):
    def draw(a):
        box(a, 3, 3, 12, 14, col)
        if lugs:
            box(a, 1, 3, 2, 5, col)
            box(a, 13, 3, 14, 5, col)
        box(a, 3, 3, 12, 3, "#ffffff")
    return draw


def coil(col, core="#3a3d40"):
    def draw(a):
        circ(a, 7.5, 8, 6.5, col, 2.5)
        circ(a, 7.5, 8, 2.5, core)
        for y in range(3, 14, 2):
            kbox(a, 2, y, 4, y, 0.75)
    return draw


def brick_icon(col, joint):
    def draw(a):
        box(a, 1, 5, 14, 11, col)
        box(a, 1, 5, 14, 5, joint)
        px(a, 5, 8, joint)
        px(a, 10, 9, joint)
    return draw


def rod(a):
    line(a, 2, 13, 13, 2, "#7d8288")
    line(a, 3, 13, 13, 3, "#9aa0a4")
    line(a, 2, 12, 12, 2, "#5a5e62")


def scrap(a):
    box(a, 2, 9, 9, 12, "#7a4a2c")
    line(a, 4, 3, 12, 11, "#8a5a3a")
    line(a, 5, 3, 13, 11, "#6b4a36")
    box(a, 10, 4, 13, 7, "#5a5e62")


def pattern(a):  # деревянная модель для формовки
    box(a, 3, 5, 12, 11, "#c8963e")
    box(a, 6, 2, 9, 5, "#b8863a")
    box(a, 3, 11, 12, 11, "#8a6024")
    px(a, 7, 8, "#8a6024")


def cutting_tool(a):
    box(a, 2, 7, 11, 9, "#5a5e62")
    box(a, 11, 6, 13, 9, "#d4af37")
    px(a, 13, 6, "#f0e0a0")


def methanometer(a):
    box(a, 4, 3, 11, 13, "#1d1d1f")
    circ(a, 7.5, 6, 2, "#7fd3ff")
    box(a, 6, 10, 9, 11, "#7d8288")


def self_rescuer(a):
    box(a, 4, 3, 11, 13, "#b4b8bb")
    box(a, 4, 6, 11, 6, "#7d8288")
    box(a, 2, 8, 3, 9, "#5a3a1a")
    box(a, 12, 8, 13, 9, "#5a3a1a")
    box(a, 6, 9, 9, 11, "#e0b51e")


def stone_duster(a):
    box(a, 3, 4, 10, 13, "#c0392b")
    box(a, 5, 2, 8, 3, "#3a3d40")
    line(a, 10, 6, 14, 2, "#3a3d40")
    px(a, 14, 1, "#e6e2d2")


ICONS = {
    "raw_coal": lump(["#1b1b1d", "#2a2a2e"], "#5a5a60"),
    "washed_coal": lump(["#141416", "#222226"], "#9aa0b0"),
    "coal_middlings": lump(["#3a3a3e", "#55555a"], None, "#1b1b1d"),
    "coal_refuse": lump(["#7a7a74", "#66665f"], None, "#3a3a3e"),
    "coke_breeze": powder("#8a8e94", "#b4b8bc"),
    "pitch_coke": lump(["#3a3a3c", "#2a2a2c"], None, "#555558"),
    "ammonium_sulfate": sack("#f0f0ea", "#c8c8c0"),
    "raw_magnetite": lump(["#1c1d20", "#2a2c30"], "#6b6f78"),
    "raw_hematite": lump(["#7a2a24", "#8c3a30"], "#b04a52"),
    "raw_pyrolusite": lump(["#202022", "#2c2c30"], None, "#3a3a3e"),
    "raw_pentlandite": lump(["#b89a4a", "#a08440"], "#e0c878"),
    "raw_chalcopyrite": lump(["#c8a83a", "#b0922e"], "#a080c0"),
    "raw_sperrylite": lump(["#7f7f7f", "#6e6e6e"], None, "#f0f0f0"),
    "raw_molybdenite": lump(["#7a7e86", "#8a8e96"], "#c4c8d0"),
    "raw_sphalerite": lump(["#5a3a1e", "#4a2e18"], "#a06a32"),
    "raw_chromite": lump(["#4f7a52", "#18191a"], None, "#18191a"),
    "raw_cassiterite": lump(["#4a2e1c", "#5a3a24"], "#c8a070"),
    "raw_bauxite": lump(["#a3532f", "#bd6a40"], None, "#7a3a20"),
    "iron_ore_concentrate": powder("#1c1d20", "#4a4c52"),
    "flotation_concentrate": powder("#a8a8a0", "#d0d0c8"),
    "sulfide_matte": ingot("#8a7a5a", "#5a4a3a"),
    "anode_slime": powder("#1d1d1f", "#3a3a3e"),
    "sinter": lump(["#6a5a4a", "#7a6a58"], None, "#3a2e24"),
    "burnt_lime": lump(["#f0eee6", "#e0ded6"], None, "#c8c6be"),
    "stone_dust": sack("#f4f4f0", "#d8d8d0"),
    "pig_iron": ingot("#6e7176", "#4a4d52"),
    "steel_scrap": scrap,
    "ferroalloy": lump(["#b4b8bc", "#a0a4a8"], "#e8ecf0"),
    "taphole_clay": lump(["#7a5a3a", "#6a4a2e"], None, "#5a3a20"),
    "steel_wire": coil("#9aa0a4"),
    "alumina": powder("#f4f4f0", "#dcdcd6"),
    "aluminium_hydroxide": powder("#e8ecec", "#c8d0d0"),
    "cryolite": lump(["#eef2f2", "#dce4e4"], "#ffffff"),
    "blister_copper": lump(["#b8642e", "#a0562a"], None, "#7a3a1a"),
    "copper_anode": plate("#b8642e", True),
    "copper_cathode": plate("#e0907a"),
    "nickel_ingot": ingot("#c8c4a8", "#9a9680"),
    "tin_ingot": ingot("#e0e2e4", "#b4b8bc"),
    "foundry_sand": powder("#5a4a34", "#3a2e20"),
    "casting_pattern": pattern,
    "refractory_brick": brick_icon("#c89a5a", "#a07a44"),
    "silica_brick": brick_icon("#e0cc8a", "#b8a46a"),
    "cutting_tool": cutting_tool,
    "methanometer": methanometer,
    "self_rescuer": self_rescuer,
    "stone_duster": stone_duster,
    "coke": lump(["#8a8e94", "#7a7e84"], "#c4c8cc", "#4a4c50"),
    "coal_tar_pitch": lump(["#141416", "#1d1d20"], "#8a8e96"),
    "steel_ingot": ingot("#5a5e62", "#3a3d40"),
    "steel_rod": rod,
    "wire_rope": coil("#7d8288", "#4a2e18"),
    "aluminium_ingot": ingot("#d0d4d8", "#a4a8ac"),
    "copper_wire": coil("#c27a43"),
    # граншлак доменного шлака (этап 10 M3; ID не из реестра 08 — продукт slag_granulator)
    "granulated_slag": powder("#c8c49a", "#e8e4c0"),
}
for _id, draw in ICONS.items():
    TEXTURES[f"item/{_id}.png"] = icon(draw)
    item(_id, f"item/{_id}.png")

# материалы 3D-предметов
TEXTURES.update({
    Y("electrode_green"): lambda: canvas("#1d1d1f", noise=4),
    Y("graphite"): lambda: canvas("#55585c", noise=5),
    Y("scale"): lambda: canvas("#3a3634", noise=10),
    Y("hot_scale"): lambda: canvas("#a0482a", noise=12),
    Y("carbon"): lambda: canvas("#232325", noise=6),
    Y("cast"): lambda: canvas("#6e7176", noise=8),
    Y("ground"): lambda: canvas("#b4b8bc", noise=2),
    Y("beam_paint"): lambda: canvas("#7a3a2a", noise=4),
    Y("pipe_black"): lambda: canvas("#2a2c2e", noise=4),
    Y("lamp_mesh"): lambda: canvas("#9aa0a4", noise=12),
    Y("glow_flame"): lambda: canvas("#ffd26a", noise=14),
})

# жидкости и газы (цвет среды; цвет трубы — ГОСТ 14202 в моделях трубопроводов)
for _id, col, kind in (
    ("molten_iron", "#ff8a2a", "liquid"), ("molten_steel", "#ffe08a", "liquid"),
    ("molten_slag", "#e0502a", "liquid"), ("molten_copper", "#ff7a1a", "liquid"),
    ("molten_aluminium", "#d8d4cc", "liquid"),
    ("mine_methane", "#d8dcc0", "gas"), ("coke_oven_gas", "#c8c49a", "gas"),
    ("blast_furnace_gas", "#a8a48a", "gas"), ("converter_gas", "#9a9a8a", "gas"),
    ("argon", "#c8b8e8", "gas"), ("liquid_nitrogen", "#e8eef4", "liquid"),
    ("liquid_oxygen", "#a8d0f0", "liquid"), ("coal_tar", "#2a1e14", "liquid"),
    ("crude_benzol", "#c8902a", "liquid"), ("wash_oil", "#4a2e18", "liquid"),
    ("ammonia_liquor", "#c8c08a", "liquid"), ("sodium_aluminate_liquor", "#7a6a4a", "liquid"),
    ("red_mud", "#9a3a22", "liquid"), ("dense_medium", "#26282a", "liquid"),
    ("raw_water", "#5a7a6a", "liquid"), ("demineralized_water", "#9ad0e8", "liquid"),
    ("boiler_feedwater", "#7ab8d8", "liquid"), ("cooling_water", "#6a9ab0", "liquid"),
    ("mine_water", "#8a5a32", "liquid"),
):
    fluid(_id, col, kind)
