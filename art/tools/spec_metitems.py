"""08: объёмные предметы — электроды, заготовки, трубы, валки, отливки, бензиновая лампа."""
from artkit import *

Y = lambda n: f"block/common/metitem_{n}.png"
OWN = {"eg": Y("electrode_green"), "gr": Y("graphite"), "sc": Y("scale"), "hsc": Y("hot_scale"),
       "cb": Y("carbon"), "cast": Y("cast"), "gnd": Y("ground"), "bp": Y("beam_paint"),
       "pb": Y("pipe_black"), "lmesh": Y("lamp_mesh"), "flame": Y("glow_flame")}


def m(id_, els):
    mk(id_, "items", els, OWN, kind="item")


def tube(name, axis, r, a0, a1, tex, wall=1.5, c=(8, 8, 8)):
    """Полая труба: четыре стенки вокруг оси."""
    x, y, z = c
    if axis == "x":
        return [el(f"{name}_t", [a0, y + r - wall, z - r], [a1, y + r, z + r], tex),
                el(f"{name}_b", [a0, y - r, z - r], [a1, y - r + wall, z + r], tex),
                el(f"{name}_n", [a0, y - r + wall, z - r], [a1, y + r - wall, z - r + wall], tex),
                el(f"{name}_s", [a0, y - r + wall, z + r - wall], [a1, y + r - wall, z + r], tex)]
    return [el(f"{name}_w", [x - r, a0, z - r], [x - r + wall, a1, z + r], tex),
            el(f"{name}_e", [x + r - wall, a0, z - r], [x + r, a1, z + r], tex),
            el(f"{name}_n", [x - r + wall, a0, z - r], [x + r - wall, a1, z - r + wall], tex),
            el(f"{name}_s", [x - r + wall, a0, z + r - wall], [x + r - wall, a1, z + r], tex)]


# сырой (зелёный) электрод и графитированный: цилиндр с ниппельным гнездом
# нарисовано вручную в Blockbench: art/blockbench/items/items3d.author.js
# заготовка и гильза
# нарисовано вручную в Blockbench: art/blockbench/items/items3d.author.js
m("pipe_hollow", tube("shell", "z", 4, 0, 16, "hsc"))
# анод и подовый блок электролизёра
m("carbon_anode", [el("block", [2, 2, 4], [14, 9, 12], "cb"), el("stub", [7, 9, 7], [9, 13, 9], "st"),
                   el("yoke", [5, 13, 7], [11, 14, 9], "st")])
m("cathode_block", [el("block", [1, 4, 5], [15, 10, 11], "cb"), el("collector_bar", [0, 5, 7], [16, 6.5, 9], "st")])
# отливка с литниковой системой
# нарисовано вручную в Blockbench: art/blockbench/items/items3d.author.js
# комплект прокатных валков: два валка с шейками
m("mill_roll_set", disc("roll_top", "x", 2.5, 3, 13, "gnd", 8, c=(8, 11, 8)) + disc("neck_top", "x", 1.25, 0, 16, "st", 8, c=(8, 11, 8))
  + disc("roll_bot", "x", 2.5, 3, 13, "gnd", 8, c=(8, 5, 8)) + disc("neck_bot", "x", 1.25, 0, 16, "st", 8, c=(8, 5, 8)))
# двутавровая балка
# нарисовано вручную в Blockbench: art/blockbench/items/items3d.author.js
# бесшовная и сварная трубы
# нарисовано вручную в Blockbench: art/blockbench/items/items3d.author.js
# бензиновая лампа Дэви: латунь, сетка, пламя
# нарисовано вручную в Blockbench: art/blockbench/items/items3d.author.js
