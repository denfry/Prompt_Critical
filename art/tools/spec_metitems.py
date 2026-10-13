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
m("green_electrode", disc("body", "y", 4, 1, 15, "eg", 8))
m("graphite_electrode", disc("body", "y", 4, 0, 13, "gr", 8) + disc("nipple", "y", 2, 13, 16, "gr", 8))
# заготовка и гильза
m("steel_billet", [el("billet", [5, 5, 0], [11, 11, 16], "sc")])
m("pipe_hollow", tube("shell", "z", 4, 0, 16, "hsc"))
# анод и подовый блок электролизёра
m("carbon_anode", [el("block", [2, 2, 4], [14, 9, 12], "cb"), el("stub", [7, 9, 7], [9, 13, 9], "st"),
                   el("yoke", [5, 13, 7], [11, 14, 9], "st")])
m("cathode_block", [el("block", [1, 4, 5], [15, 10, 11], "cb"), el("collector_bar", [0, 5, 7], [16, 6.5, 9], "st")])
# отливка с литниковой системой
m("casting", [el("body", [3, 2, 3], [13, 9, 13], "cast"), el("sprue", [7, 9, 7], [9, 13, 9], "cast"),
              el("runner", [9, 9, 7.5], [13, 10, 8.5], "cast"), el("riser", [11, 10, 7], [13, 12, 9], "cast")])
# комплект прокатных валков: два валка с шейками
m("mill_roll_set", disc("roll_top", "x", 2.5, 3, 13, "gnd", 8, c=(8, 11, 8)) + disc("neck_top", "x", 1.25, 0, 16, "st", 8, c=(8, 11, 8))
  + disc("roll_bot", "x", 2.5, 3, 13, "gnd", 8, c=(8, 5, 8)) + disc("neck_bot", "x", 1.25, 0, 16, "st", 8, c=(8, 5, 8)))
# двутавровая балка
m("steel_beam", [el("flange_t", [5, 12, 0], [11, 13, 16], "bp"), el("flange_b", [5, 3, 0], [11, 4, 16], "bp"),
                 el("web", [7.5, 4, 0], [8.5, 12, 16], "bp")])
# бесшовная и сварная трубы
m("seamless_pipe", tube("pipe", "z", 3.5, 0, 16, "pb"))
m("welded_pipe", tube("pipe", "z", 3.5, 0, 16, "sm") + [el("seam", [7.5, 11.4, 0], [8.5, 11.7, 16], "d")])
# бензиновая лампа Дэви: латунь, сетка, пламя
m("flame_safety_lamp", [el("base", [5, 0, 5], [11, 4, 11], "bs"),
                        el("glass", [5.5, 4, 5.5], [10.5, 7, 10.5], "gl"),
                        el("glow_flame", [7.25, 4.5, 7.25], [8.75, 6.5, 8.75], "flame"),
                        el("gauze", [6, 7, 6], [10, 12, 10], "lmesh"),
                        el("bonnet", [5.5, 12, 5.5], [10.5, 13, 10.5], "bs"),
                        el("hook", [7.5, 13, 7.5], [8.5, 15.5, 8.5], "bs")] + [
    el(f"pillar_{i}", [x, 4, z], [x + 0.75, 12, z + 0.75], "bs") for i, (x, z) in enumerate(((5, 5), (10.25, 5), (5, 10.25), (10.25, 10.25)))])
