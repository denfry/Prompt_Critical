"""11 §11.6.2: декор. Полные блоки семейств, фигурный декор (настил, перила, лотки, светильники).
Вариант семейства — <семейство>_<вариант>; slab/stairs/wall — datagen по ванильным шаблонам."""
from artkit import *

D = lambda n: f"block/decor/{n}.png"
OWN = {}


def m(id_, els, own=None):
    mk(id_, "decor", els, dict(OWN, **(own or {})))


CUBES = ["tile_white", "tile_blue", "tile_green", "porcelain_tile", "porcelain_tile_beige",
         "linoleum_control", "linoleum_control_beige", "linoleum_control_parquet",
         "raised_floor", "raised_floor_solid", "ceiling_tile",
         "painted_concrete_two_tone", "painted_concrete_two_tone_blue", "painted_concrete_two_tone_beige",
         "painted_concrete", "painted_concrete_yellow", "painted_concrete_red",
         "glass_block", "glass_block_frosted",
         "corrugated_steel", "corrugated_steel_blue", "corrugated_steel_green", "corrugated_steel_red",
         "corrugated_steel_white", "sandwich_panel", "sandwich_panel_grey", "sandwich_panel_blue", "asphalt"]
for _c in CUBES:
    top = {}
    if _c.startswith("painted_concrete_two_tone"):  # верх/низ — без границы покраски
        top = {"up": "w", "down": "w"}
    m(_c, [el("block", [0, 0, 0], [16, 16, 16], "t", faces=top)],
      {"t": D(_c), "w": D("ceiling_tile")} if top else {"t": D(_c)})

# светящаяся панель подвесного потолка (висит под перекрытием)
m("ceiling_light_panel", [el("frame", [0, 15, 0], [16, 16, 16], "ct"),
                          el("glow_diffuser", [1, 14.5, 1], [15, 15, 15], "lp")],
  {"ct": D("ceiling_tile"), "lp": D("ceiling_light_panel")})

# разметка асфальта: наложение поверх асфальта
for _k in ("", "_yellow", "_arrow"):
    m(f"asphalt_marking{_k}", [el("asphalt", [0, 0, 0], [16, 16, 16], "a"),
                               el("paint", [0, 16, 0], [16, 16.01, 16], "mk")],
      {"a": D("asphalt"), "mk": D(f"asphalt_marking{_k}")})

# бордюр
m("curb", [el("curb", [0, 0, 5], [16, 6, 11], "c", faces={"up": "cb", "north": "cb"})], {"cb": D("curb")})

# решётчатый настил и ступень
m("grating", [el("plate", [0, 14, 0], [16, 16, 16], "grat"),
              el("bearer_w", [0, 12, 0], [1, 14, 16], "gal"), el("bearer_e", [15, 12, 0], [16, 14, 16], "gal")])
m("grating_step", [el("tread", [0, 7, 0], [16, 8, 8], "grat"), el("nosing", [0, 7, -0.01], [16, 8, 1], "yel"),
                   el("tread_2", [0, 15, 8], [16, 16, 16], "grat"), el("nosing_2", [0, 15, 7.99], [16, 16, 9], "yel"),
                   el("stringer_w", [0, 0, 0], [1, 16, 16], "gal"), el("stringer_e", [15, 0, 0], [16, 16, 16], "gal")])


# перила: жёлтые и оцинкованные, с бортиком
def handrail(tex, toe=True):
    els = [el("post_w", [0, 0, 7], [1.5, 16, 8.5], tex), el("post_e", [14.5, 0, 7], [16, 16, 8.5], tex),
           el("top_rail", [0, 15, 7], [16, 16.5, 8.5], tex), el("mid_rail", [0, 8, 7.25], [16, 9, 8.25], tex)]
    if toe:
        els.append(el("toe_board", [0, 0, 7.5], [16, 2.5, 8], tex))
    return els


m("handrail", handrail("cy"))
m("handrail_galvanized", handrail("gal"))

# вертикальная лестница с ограждением
m("cage_ladder", [el("stile_w", [3, 0, 14], [4, 16, 15], "gal"), el("stile_e", [12, 0, 14], [13, 16, 15], "gal")]
  + [el(f"rung_{i}", [4, y, 14.25], [12, y + 0.75, 14.75], "gal") for i, y in enumerate((2, 6, 10, 14))]
  + [el("hoop", [1, 8, 0], [15, 9, 1], "gal"), el("hoop_w", [1, 8, 1], [2, 9, 14], "gal"),
     el("hoop_e", [14, 8, 1], [15, 9, 14], "gal")]
  + [el(f"bar_{i}", [x, 0, z], [x + 0.75, 16, z + 0.75], "gal") for i, (x, z) in enumerate(((1, 2), (14.25, 2), (7.6, 0.1)))])


# кабельный лоток: прямой, угол, тройник, вертикальный; с кабелями и пустой
def tray(cables=False, arms=("n", "s")):
    els = [el("bottom", [3, 0, 3], [13, 0.5, 13], "gal"),
           el("side_w", [3, 0, 3], [3.5, 3, 13], "gal") if "w" not in arms else None,
           el("side_e", [12.5, 0, 3], [13, 3, 13], "gal") if "e" not in arms else None,
           el("side_n", [3, 0, 3], [13, 3, 3.5], "gal") if "n" not in arms else None,
           el("side_s", [3, 0, 12.5], [13, 3, 13], "gal") if "s" not in arms else None]
    span = {"n": ([3, 0, 0], [13, 0.5, 3]), "s": ([3, 0, 13], [13, 0.5, 16]),
            "w": ([0, 0, 3], [3, 0.5, 13]), "e": ([13, 0, 3], [16, 0.5, 13])}
    for a in arms:
        f, t = span[a]
        els.append(el(f"floor_{a}", f, t, "gal"))
        if a in "ns":
            els += [el(f"rail_{a}_w", [3, 0, f[2]], [3.5, 3, t[2]], "gal"), el(f"rail_{a}_e", [12.5, 0, f[2]], [13, 3, t[2]], "gal")]
        else:
            els += [el(f"rail_{a}_n", [f[0], 0, 3], [t[0], 3, 3.5], "gal"), el(f"rail_{a}_s", [f[0], 0, 12.5], [t[0], 3, 13], "gal")]
    if cables:
        els.append(el("cables", [4, 0.5, 4], [12, 2, 12], "cbl"))
        for a in arms:
            f, t = span[a]
            els.append(el(f"cables_{a}", [max(f[0], 4) if a in "ns" else f[0], 0.5, max(f[2], 4) if a in "ew" else f[2]],
                          [min(t[0], 12) if a in "ns" else t[0], 2, min(t[2], 12) if a in "ew" else t[2]], "cbl"))
    return [e for e in els if e]


m("cable_tray", tray())
m("cable_tray_cables", tray(True))
m("cable_tray_corner", tray(True, ("n", "e")))
m("cable_tray_tee", tray(True, ("n", "s", "e")))
m("cable_tray_vertical", [el("back", [3, 0, 15.5], [13, 16, 16], "gal"), el("side_w", [3, 0, 12.5], [3.5, 16, 16], "gal"),
                          el("side_e", [12.5, 0, 12.5], [13, 16, 16], "gal"), el("cables", [4, 0, 13.5], [12, 16, 15.5], "cbl")])

# трубная эстакада: стойка и траверса
m("pipe_rack", [el("column", [6, 0, 6], [10, 16, 10], "gal"),
                el("beam", [0, 13, 6.5], [16, 16, 9.5], "gal"),
                el("base_plate", [5, 0, 5], [11, 0.5, 11], "d")])


# светильники: промышленный подвесной, консольный уличный, прожектор, аварийный «Выход»
def light(kind, lit=True):
    g = "glow_" if lit else ""
    lamp = "lw" if lit else "loff"
    if kind == "pendant":
        return [el("rod", [7.5, 10, 7.5], [8.5, 16, 8.5], "d"), el("shade", [3, 6, 3], [13, 10, 13], "mb"),
                el(g + "bulb", [5, 5, 5], [11, 6, 11], lamp)]
    if kind == "street":
        return [el("bracket", [7, 10, 8], [9, 12, 16], "gal"), el("head", [5, 9, 1], [11, 12, 9], "gal"),
                el(g + "lens", [5.5, 8.5, 2], [10.5, 9, 8], lamp)]
    if kind == "flood":
        return [el("yoke", [3, 0, 7], [13, 6, 9], "d"), el("body", [3, 4, 4], [13, 12, 10], "dg"),
                el(g + "glass", [3.5, 4.5, 3.5], [12.5, 11.5, 4], lamp)]
    return [el("box", [3, 5, 14], [13, 11, 16], "r35"),
            el(("glow_" if lit else "") + "sign", [3.5, 5.5, 13.9], [12.5, 10.5, 14], "ex", full=["north"])]


for _k, sfx in (("pendant", ""), ("street", "_street"), ("flood", "_floodlight"), ("exit", "_exit")):
    m(f"industrial_light{sfx}", light(_k), {"ex": D("industrial_light_exit"), "r35": "block/common/grid_ral7035.png",
                                           "dg": "block/common/grid_dark_grey.png"})
    m(f"industrial_light{sfx}_off", light(_k, False), {"ex": D("industrial_light_exit"), "r35": "block/common/grid_ral7035.png",
                                                      "dg": "block/common/grid_dark_grey.png"})

# таблички помещений и стрелки эвакуации (на стене south)
m("room_sign", [el("plate", [2, 5, 15.5], [14, 11, 16], "s", full=["north"])], {"s": D("room_sign")})
m("room_sign_evac", [el("plate", [2, 5, 15.5], [14, 11, 16], "s", full=["north"])], {"s": D("room_sign_evac")})
# самосветящийся знак
m("tritium_lamp", [el("glow_plate", [3, 5, 15.5], [13, 11, 16], "s", full=["north"])], {"s": D("tritium_lamp")})
# часы БЩУ: стрелки рисует BER
m("wall_clock", [el("case", [2, 2, 14.5], [14, 14, 16], "dg"),
                 el("face", [2.5, 2.5, 14.4], [13.5, 13.5, 14.5], "f", full=["north"])],
  {"f": D("wall_clock_face"), "dg": "block/common/grid_dark_grey.png"})
# плакаты ТБ 2×1 и 2×2
m("safety_poster", [el("poster", [0, 0, 15.75], [32, 16, 16], "p", full=["north"])], {"p": D("safety_poster")})
m("safety_poster_large", [el("poster", [0, 0, 15.75], [32, 32, 16], "p", full=["north"])], {"p": D("safety_poster_large")})
# декоративные массивные ворота: сегмент 2×2
m("blast_door_decor", [el("leaf", [0, 0, 4], [32, 32, 12], "bd", full=["north", "south"]),
                       el("frame_t", [0, 30, 2], [32, 32, 14], "d"),
                       el("wheel_hub", [14, 14, 2], [18, 18, 4], "d")], {"bd": D("blast_door_decor")})
