"""Ores, exploration, ore processing, hydrometallurgy, reagents, conversion (GDD 02 §2.1–2.4, §2.12)."""
from pclib import *

M_ = lambda n: T("mining_" + n)
POOL = {
    "d": T("steel_dark"), "ds": T("steel_dark_smooth"), "p": T("steel_plate"), "sm": T("steel_smooth"),
    "st": T("steel_bare"), "b": T("brass"), "bs": T("brass_smooth"), "h": T("hazard"), "c": T("concrete"),
    "r": T("rubber"), "red": T("paint_red"), "mb": T("motor_blue"), "lr": T("lamp_red"), "ly": T("lamp_yellow"),
    "lg": T("lamp_green"), "tre": T("trefoil"), "end": T("pipe_end"), "cab": T("cabinet"), "g": T("graphite"),
    "lead": T("lead"), "dial": "block/instrument_dial.png",
    "and": M_("andesite"), "cu": M_("copper"), "fb": M_("firebrick"), "hdpe": M_("hdpe"), "frp": M_("frp"),
    "lab": M_("lab_white"), "gl": M_("glass"), "fire": M_("glow_fire"), "coil": M_("glow_coil"),
    "pulp": M_("pulp"), "frost": M_("frost"), "clad": M_("clad"), "yel": M_("paint_yellow"),
    "acid": M_("pipe_acid"), "gas": M_("pipe_gas"), "h2": M_("pipe_h2"), "alk": M_("pipe_alkali"),
    "air": M_("pipe_air"), "fuel": M_("pipe_fuel"), "wat": M_("pipe_water"), "steam": M_("pipe_steam"),
}


def mk(id_, cat, els, own=None, kind="block"):
    """Model with texture dict pruned to the keys actually used."""
    pool = dict(POOL, **(own or {}))
    used = set()
    for e in els:
        used.add(e["tex"])
        used.update(e["faces"].values())
    if "/" in id_:
        blk, part = id_.split("/")
        java = f"block/{blk}/partial/{part}"
    else:
        java = f"block/{id_}"
    model(id_, cat, {k: pool[k] for k in sorted(used)}, els, java, kind)


def nozzle(name, f, t, tex, side):
    """Pipe stub ending in a flanged opening on `side`."""
    return el(name, f, t, tex, faces={side: "end"}, full=[side])


def ring_y(name, r, w, y0, y1, tex, c=(8, 8)):
    """Octagonal ring around Y with staggered tops (no z-fighting)."""
    out = []
    cx, cz = c
    for j, ang in enumerate((0, 45)):
        o = [cx, (y0 + y1) / 2, cz]
        dy = 0.01 * j
        bars = [
            ([cx - r, y0 - dy, cz - r], [cx + r, y1 + dy, cz - r + w]),
            ([cx - r, y0 - dy, cz + r - w], [cx + r, y1 + dy, cz + r]),
            ([cx - r, y0 - dy, cz - r + w], [cx - r + w, y1 + dy, cz + r - w]),
            ([cx + r - w, y0 - dy, cz - r + w], [cx + r, y1 + dy, cz + r - w]),
        ]
        for i, (f, t) in enumerate(bars):
            out.append(el(f"{name}_{j}_{i}", f, t, tex, rot=("y", ang, o) if ang else None))
    return out


def shell(name, axis, r, a0, a1, tex, cap="ds", sides=16, faces=None, c=(8, 8, 8)):
    """disc() with plain end caps, so banded shell textures don't show as radial stripes."""
    ends = {"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}[axis]
    f = {e: cap for e in ends}
    f.update(faces or {})
    return disc(name, axis, r, a0, a1, tex, sides, f, c)


def legs4(y1, inset=2, w=1.5, tex="d", y0=0):
    a, b = inset, 16 - inset - w
    return [el(f"leg_{i}", [x, y0, z], [x + w, y1, z + w], tex)
            for i, (x, z) in enumerate(((a, a), (b, a), (a, b), (b, b)))]


# =====================================================================
# ores — простые полные кубы
# =====================================================================
ORES = ["uraninite_ore", "deepslate_uraninite_ore", "coffinite_ore", "carnotite_ore", "monazite_sand",
        "zircon_sand", "fluorite_ore", "pyrite_ore", "deepslate_pyrite_ore", "galena_ore",
        "deepslate_galena_ore", "bauxite_ore", "borax_ore", "thorite_ore"]
for o in ORES:
    mk(o, "ores", [el("ore", [0, 0, 0], [16, 16, 16], "o")], {"o": f"block/{o}.png"})

# =====================================================================
# exploration
# =====================================================================
# колонковый бур: верхний блок мультиблока 1×1×3 — вращатель на мачте, вал Create сверху
mk("core_drill", "exploration", [
    el("skid_n", [0, 0, 1], [16, 2, 4], "d"),
    el("skid_s", [0, 0, 12], [16, 2, 15], "d"),
    el("skid_w", [0, 0, 4], [3, 2, 12], "d"),
    el("skid_e", [13, 0, 4], [16, 2, 12], "d"),
    el("hazard_n", [0, 0.01, 0.5], [16, 1.99, 1], "h"),
    el("table_n", [3, 2, 4], [13, 3.5, 6], "and"),
    el("table_s", [3, 2, 10], [13, 3.5, 12], "and"),
    el("table_w", [3, 2, 6], [6, 3.5, 10], "and"),
    el("table_e", [10, 2, 6], [13, 3.5, 10], "and"),
    el("mast_w", [3, 2, 12], [5, 16, 14], "p"),
    el("mast_e", [11, 2, 12], [13, 16, 14], "p"),
    el("feed_cyl_w", [4, 3.5, 6.5], [5, 11, 7.5], "st"),
    el("feed_cyl_e", [11, 3.5, 6.5], [12, 11, 7.5], "st"),
    el("swivel_head", [3, 11, 4], [13, 15.5, 12], "and", faces={"north": "gb"}, full=["north"]),
    el("shaft_coupling", [6, 15.5, 6], [10, 16, 10], "b"),
], {"gb": "block/core_drill_gearbox.png"})
mk("core_drill/drill_string", "exploration", [
    el("rod", [7.25, 0.05, 7.25], [8.75, 11, 8.75], "st"),
    el("collar_1", [6.75, 4.5, 6.75], [9.25, 5.5, 9.25], "b"),
    el("collar_2", [6.75, 8, 6.75], [9.25, 9, 9.25], "b"),
    el("crown", [6.5, 0, 6.5], [9.5, 1.5, 9.5], "d"),
], kind="partial")

# лаборатория: флуориметр/РФА на лабораторном столе, ящик с керном
mk("assay_station", "exploration", [
    el("cabinet", [0, 0, 1], [16, 9, 16], "lab", faces={"north": "cab_front"}, full=["north"]),
    el("worktop", [0, 9, 0.5], [16, 10, 16], "d"),
    el("xrf_body", [1, 10, 5], [9.5, 15, 15], "lab"),
    el("glow_xrf_screen", [2, 11, 4.9], [8.5, 14.5, 5], "scr", full=["north"]),
    el("xrf_lid", [2, 15, 7], [8.5, 15.5, 13], "d"),
    el("fluorimeter", [10.5, 10, 8], [15, 13.5, 14], "cab"),
    el("glow_fluor_lamp", [11, 12, 7.9], [12, 13, 8], "lg"),
    el("core_box", [10, 10, 1.5], [15.5, 10.75, 6.5], "d", faces={"up": "cores"}, full=["up"]),
], {"cab_front": "block/assay_station_cabinet.png", "scr": "block/assay_station_screen.png",
    "cores": "block/assay_station_cores.png"})

# =====================================================================
# ore processing
# =====================================================================
# шаровая мельница: сегмент барабана на цапфах; барабан — partial (ось X)
mk("ball_mill", "ore_processing", [
    el("slab", [0, 0, 2], [16, 1, 14], "c"),
    el("pedestal_w", [0, 1, 4], [2.5, 9, 12], "and"),
    el("pedestal_e", [13.5, 1, 4], [16, 9, 12], "and"),
    el("bearing_w", [0, 9, 5], [2.5, 11, 11], "d"),
    el("bearing_e", [13.5, 9, 5], [16, 11, 11], "d"),
    el("feed_chute", [0.25, 11, 6], [2.25, 14, 10], "p"),
    el("feed_hopper", [0, 14, 5], [3, 16, 11], "p"),
    el("pinion_guard", [9, 1, 13.5], [11.5, 7, 16], "drv"),
], {"drv": "block/ball_mill_drive.png"})
mk("ball_mill/drum", "ore_processing",
   shell("shell", "x", 6, 2.5, 13.5, "shell")
   + disc("girth_gear", "x", 7, 9.5, 11, "bs", sides=8)
   + [el("manhole", [5, 13.8, 6.5], [8, 14.4, 9.5], "d")],
   {"shell": "block/ball_mill_shell.png"}, kind="partial")

# радиометрический сортировщик: портал над лентой Create
mk("radiometric_sorter", "ore_processing", [
    el("leg_w", [0, 0, 2], [1.5, 9, 14], "d"),
    el("leg_e", [14.5, 0, 2], [16, 9, 14], "d"),
    el("housing", [0, 9, 0.5], [16, 16, 15.5], "p", faces={"north": "front"}, full=["north"]),
    el("detector", [3, 8, 3], [13, 9, 11], "lead"),
    el("ejector_bar", [2, 7.5, 12], [14, 9, 13.5], "d"),
    el("air_line", [16, 10, 7], [17.5, 12, 9], "air", faces={"east": "end"}, full=["east"]),
    el("trefoil", [11.5, 9.75, 0.25], [14.5, 12.75, 0.5], "tre", full=["north"]),
    el("glow_count_lamp", [13, 14, 0.25], [14.5, 15, 0.5], "lg"),
], {"front": "block/radiometric_sorter_front.png"})

mk("heap_leach_liner", "ore_processing", [
    el("liner", [0, 0, 0], [16, 16, 16], "side", faces={"up": "top"}),
], {"top": "block/heap_leach_liner_top.png", "side": "block/heap_leach_liner_side.png"})

mk("ore_heap", "ore_processing", [el("heap", [0, 0, 0], [16, 16, 16], "o")], {"o": "block/ore_heap.png"})

# капельный ороситель: коллектор (кислота — оранжевый) + капельные линии
mk("drip_irrigator", "ore_processing", [
    el("header", [0, 0, 1], [16, 2, 3], "acid"),
    el("line_1", [2.5, 0, 3], [4, 1, 16], "drip"),
    el("line_2", [7.25, 0, 3], [8.75, 1, 16], "drip"),
    el("line_3", [12, 0, 3], [13.5, 1, 16], "drip"),
    el("riser", [7, 2, 1], [9, 5, 3], "acid"),
    el("valve", [6.5, 5, 0.75], [9.5, 6.5, 3.25], "b"),
    el("lever", [7.5, 6.5, 1.5], [12, 7, 2.5], "red"),
], {"drip": "block/drip_irrigator_line.png"})

# периметральная дренажная канава с PLS
mk("pls_collector", "ore_processing", [
    el("floor", [0, 0, 0], [16, 1, 16], "c"),
    el("wall_n", [0, 1, 0], [16, 8, 3], "c"),
    el("wall_s", [0, 1, 13], [16, 8, 16], "c"),
    el("liquid", [0, 1, 3], [16, 5, 13], "pls"),
    el("grate", [0, 7.5, 3], [7, 8, 13], "grate"),
    nozzle("outlet", [10, 2, 16], [13, 5, 17.5], "acid", "south"),
], {"pls": "block/pls_pond_surface.png", "grate": "block/pls_collector_grate.png"})

mk("pls_pond", "ore_processing", [
    el("floor", [0, 0, 0], [16, 1, 16], "hdpe"),
    el("wall_n", [0, 1, 0], [16, 12, 1], "hdpe"),
    el("wall_s", [0, 1, 15], [16, 12, 16], "hdpe"),
    el("wall_w", [0, 1, 1], [1, 12, 15], "hdpe"),
    el("wall_e", [15, 1, 1], [16, 12, 15], "hdpe"),
    el("liquid", [1, 1, 1], [15, 10, 15], "pls"),
    el("level_float", [7.5, 9.5, 7.5], [8.5, 11, 8.5], "yel"),
], {"pls": "block/pls_pond_surface.png"})


def wellhead():
    return [
        el("pad", [0, 0, 0], [16, 1, 16], "c"),
        el("casing_collar", [5, 1, 5], [11, 3, 11], "d"),
        el("casing", [6, 3, 6], [10, 10, 10], "st"),
        el("tag", [6.5, 4, 5.9], [9.5, 7, 6], "tag", full=["north"]),
    ]


mk("isl_injection_well", "ore_processing", wellhead() + [
    nozzle("inject_line", [6.5, 7.5, 1], [9.5, 10.5, 6], "acid", "north"),
    el("inject_flange", [5.5, 6.5, 0], [10.5, 11.5, 1], "d", faces={"north": "end"}, full=["north"]),
    el("master_valve", [5.5, 10, 5.5], [10.5, 12, 10.5], "b"),
    el("stem", [7.5, 12, 7.5], [8.5, 13.75, 8.5], "st"),
] + ring_y("wheel", 3.5, 0.75, 13, 13.75, "red"), {"tag": "block/isl_injection_well_tag.png"})

mk("isl_production_well", "ore_processing", wellhead() + [
    el("discharge_head", [4.5, 10, 4.5], [11.5, 12.5, 11.5], "p"),
    el("cable_gland", [7, 12.5, 7], [9, 14, 9], "r"),
    nozzle("discharge_line", [6.5, 10.25, 0], [9.5, 12.25, 4.5], "acid", "north"),
    el("flowmeter", [6, 9.75, 1.5], [10, 12.75, 3.5], "d"),
    el("pump_control", [11, 1, 6], [14.5, 8, 10], "cab"),
    el("glow_run_lamp", [14.5, 6, 7.5], [14.75, 7, 8.5], "lg"),
], {"tag": "block/isl_production_well_tag.png"})

mk("isl_monitor_well", "ore_processing", [
    el("pad", [0, 0, 0], [16, 1, 16], "c"),
    el("protective_casing", [5, 1, 5], [11, 12, 11], "mon", full=["north", "south", "east", "west"]),
    el("cap", [4.5, 12, 4.5], [11.5, 13, 11.5], "d"),
    el("logger", [11, 5, 6.5], [13.5, 9, 9.5], "cab"),
    el("antenna", [12, 9, 7.75], [12.5, 15, 8.25], "st"),
    el("glow_logger_lamp", [13.5, 7.5, 7.5], [13.75, 8.5, 8.5], "ly"),
    el("sample_tap", [7.5, 6, 3.5], [8.5, 7, 5], "b"),
], {"mon": "block/isl_monitor_well_casing.png"})

# =====================================================================
# hydrometallurgy
# =====================================================================
# чан выщелачивания: открытый сверху, мостик с приводом мешалки; мешалка — partial (ось Y)
mk("leach_tank", "hydrometallurgy",
   shell("tank", "y", 7, 0, 10, "shell", faces={"up": "pulp"})
   + ring_y("rim", 7.3, 1, 9.5, 12, "ds")
   + [
       el("bridge", [0, 12, 7], [16, 13.5, 9], "d"),
       el("gearbox", [5, 13.5, 5], [11, 16, 11], "and"),
       nozzle("feed", [6.5, 6, 0], [9.5, 9, 1.5], "acid", "north"),
       nozzle("discharge", [14.5, 1, 6.5], [16, 4, 9.5], "acid", "east"),
   ], {"shell": "block/leach_tank_shell.png"})
mk("leach_tank/agitator", "hydrometallurgy", [
    el("shaft", [7.5, 1, 7.5], [8.5, 13.5, 8.5], "st"),
    el("blade_x", [3, 2.5, 7.25], [13, 3.5, 8.75], "bs"),
    el("blade_z", [7.25, 2.49, 3], [8.75, 3.51, 13], "bs"),
] + disc("hub", "y", 2, 10.5, 11.5, "ds", sides=8), kind="partial")

# автоклав: горизонтальный сосуд в обшивке на сёдлах
mk("pressure_leach_autoclave", "hydrometallurgy",
   [el("saddle_w", [2, 0, 3], [5, 3, 13], "c"), el("saddle_e", [11, 0, 3], [14, 3, 13], "c")]
   + shell("shell", "x", 6, 1, 15, "shell")
   + disc("head_w", "x", 4.5, 0, 1, "sm", sides=8)
   + disc("head_e", "x", 4.5, 15, 16, "sm", sides=8)
   + [
       el("manway", [6, 13.5, 6], [10, 15.5, 10], "d"),
       el("manway_cover", [5.5, 15.5, 5.5], [10.5, 16, 10.5], "d"),
       el("agitator_motor", [2.5, 13.5, 6.5], [4.5, 16, 9.5], "mb"),
       el("safety_valve", [11, 13.5, 7], [12.5, 16, 8.5], "red"),
       el("oxygen_inlet", [12.5, 12.5, 2], [14, 14.5, 4], "air"),
   ], {"shell": "block/pressure_leach_autoclave_shell.png"})

# сгуститель: центральный сегмент — мостик с приводом; гребки — partial (ось Y)
mk("thickener", "hydrometallurgy", [
    el("floor", [0, 0, 0], [16, 1, 16], "c"),
    el("slurry", [0, 1, 0], [16, 9, 16], "pulp", faces={"up": "surf"}),
] + disc("feedwell", "y", 3, 7, 10.5, "sm") + [
    el("bridge", [0, 12, 6.5], [16, 13.5, 9.5], "p"),
    el("rail_n", [0, 15, 6.5], [16, 15.5, 7], "yel"),
    el("rail_s", [0, 15, 9], [16, 15.5, 9.5], "yel"),
    el("post_nw", [0.5, 13.5, 6.5], [1, 15, 7], "yel"),
    el("post_sw", [0.5, 13.5, 9], [1, 15, 9.5], "yel"),
    el("post_ne", [15, 13.5, 6.5], [15.5, 15, 7], "yel"),
    el("post_se", [15, 13.5, 9], [15.5, 15, 9.5], "yel"),
    el("drive_head", [4.5, 13.5, 4.5], [11.5, 16, 11.5], "and",
       faces={"north": "drv", "south": "drv"}, full=["north", "south"]),
], {"surf": "block/thickener_surface.png", "drv": "block/thickener_drive.png"})
mk("thickener/rake", "hydrometallurgy", [
    el("torque_column", [7, 1.5, 7], [9, 12, 9], "st"),
    el("arm_x", [0.5, 2, 7.5], [15.5, 3, 8.5], "d"),
    el("arm_z", [7.5, 1.99, 0.5], [8.5, 3.01, 15.5], "d"),
    el("blade_w", [1.5, 1.2, 6.5], [3.5, 2, 9.5], "st", rot=("y", 45, [2.5, 1.6, 8])),
    el("blade_e", [12.5, 1.2, 6.5], [14.5, 2, 9.5], "st", rot=("y", 45, [13.5, 1.6, 8])),
    el("blade_n", [6.5, 1.2, 1.5], [9.5, 2, 3.5], "st", rot=("y", 45, [8, 1.6, 2.5])),
    el("blade_s", [6.5, 1.2, 12.5], [9.5, 2, 14.5], "st", rot=("y", 45, [8, 1.6, 13.5])),
    el("cage_n", [6, 10.5, 6], [10, 11.25, 6.75], "d"),
    el("cage_s", [6, 10.5, 9.25], [10, 11.25, 10], "d"),
], kind="partial")

# ионообменная колонна: сегмент 1×1×3
mk("ion_exchange_column", "hydrometallurgy",
   shell("shell", "y", 5.5, 0.5, 15.5, "shell")
   + disc("flange_b", "y", 6.5, 0, 1, "ds", sides=8)
   + disc("flange_t", "y", 6.5, 15, 16, "ds", sides=8)
   + [
       el("sight_glass", [6, 4, 2.2], [10, 12, 2.6], "resin", full=["north"]),
       nozzle("inlet", [13.5, 12, 7], [16, 14, 9], "acid", "east"),
       nozzle("outlet", [0, 2, 7], [2.5, 4, 9], "acid", "west"),
       el("valve", [14, 11.5, 6.5], [15.5, 14.5, 9.5], "b"),
   ], {"shell": "block/ion_exchange_column_shell.png", "resin": "block/ion_exchange_column_resin.png"})

# смеситель-отстойник: смесительная камера (сегмент 2×1×1), мешалка — partial
mk("mixer_settler", "hydrometallurgy", [
    el("mixer_box", [3, 0, 3], [13, 10, 13], "frp", faces={"up": "emul"}),
    el("window", [5, 2, 2.9], [11, 8.5, 3], "win", full=["north"]),
    el("rim_n", [2.75, 9.5, 2.75], [13.25, 11, 3.25], "d"),
    el("rim_s", [2.75, 9.5, 12.75], [13.25, 11, 13.25], "d"),
    el("bridge", [3, 11, 7], [13, 12.5, 9], "d"),
    el("gearbox", [5.5, 12.5, 5.5], [10.5, 15.5, 10.5], "and"),
    el("launder", [13, 7, 4], [16, 9.5, 12], "frp", faces={"up": "emul"}),
    nozzle("aqueous_in", [0, 3, 5], [3, 5, 7], "acid", "west"),
    nozzle("organic_in", [0, 5.5, 9], [3, 7.5, 11], "fuel", "west"),
], {"emul": "block/mixer_settler_emulsion.png", "win": "block/mixer_settler_window.png"})
mk("mixer_settler/impeller", "hydrometallurgy", [
    el("shaft", [7.5, 1.5, 7.5], [8.5, 16, 8.5], "st"),
    el("blade_x", [4.5, 2, 7.25], [11.5, 3, 8.75], "bs"),
    el("blade_z", [7.25, 1.99, 4.5], [8.75, 3.01, 11.5], "bs"),
] + disc("hub", "y", 1.8, 9.5, 10.75, "ds", sides=8), kind="partial")

# осадительный бак с коническим днищем на опорах
mk("precipitation_tank", "hydrometallurgy",
   legs4(7, inset=2.5)
   + disc("cone_low", "y", 3, 3, 5, "sm", sides=8)
   + disc("cone_mid", "y", 5, 5, 7, "sm", sides=8)
   + shell("body", "y", 6.5, 7, 14, "shell")
   + disc("head", "y", 5, 14, 15, "sm", sides=8)
   + [
       el("agitator_motor", [6, 15, 6], [10, 16, 10], "mb"),
       el("nh3_dosing", [11, 14.2, 7], [12.5, 16, 8.5], "gas"),
       el("bottom_valve", [7, 1.5, 7], [9, 3, 9], "b"),
       nozzle("slurry_out", [7, 1.5, 1], [9, 3, 7], "acid", "north"),
   ], {"shell": "block/precipitation_tank_shell.png"})

# вращающаяся печь прокалки: кожух — partial (ось X)
mk("rotary_calciner", "hydrometallurgy", [
    el("base", [0, 0, 2], [16, 1, 14], "c"),
    el("rollers_w", [3, 1, 4], [6, 2.4, 12], "and"),
    el("rollers_e", [10, 1, 4], [13, 2.4, 12], "and"),
    el("feed_hood", [0, 1, 2], [2, 14, 14], "p"),
    el("feed_chute", [0.25, 14, 5], [1.75, 16, 11], "p"),
    el("discharge_hood", [14, 1, 2], [16, 15, 14], "fb", faces={"east": "hood"}, full=["east"]),
    el("glow_hood_port", [14.5, 4, 1.9], [15.5, 7, 2], "fire"),
    nozzle("burner", [16, 9, 7], [17.5, 11, 9], "fuel", "east"),
], {"hood": "block/rotary_calciner_hood.png"})
mk("rotary_calciner/shell", "hydrometallurgy",
   shell("kiln", "x", 5, 2, 14, "shell")
   + disc("tyre_w", "x", 5.8, 3.5, 5.5, "ds", sides=8)
   + disc("tyre_e", "x", 5.8, 10.5, 12.5, "ds", sides=8)
   + disc("girth_gear", "x", 6.3, 7.5, 8.5, "bs", sides=8),
   {"shell": "block/rotary_calciner_shell.png"}, kind="partial")

# дамба хвостохранилища: насыпь с каменной наброской и водомерной рейкой
mk("tailings_dam", "hydrometallurgy", [
    el("embankment", [0, 0, 0], [16, 16, 16], "side", faces={"up": "top"}),
    el("staff_gauge", [7, 1, -0.5], [9, 15, 0], "gauge", full=["north"]),
], {"side": "block/tailings_dam_side.png", "top": "block/tailings_dam_top.png",
    "gauge": "block/tailings_dam_gauge.png"})

mk("tailings_liner", "hydrometallurgy", [
    el("liner", [0, 0, 0], [16, 16, 16], "side", faces={"up": "top"}),
], {"top": "block/tailings_liner_top.png", "side": "block/tailings_liner_side.png"})

# нейтрализатор: бак с пульпой + бункер извести (щёлочь — фиолетовый)
mk("tailings_neutralizer", "hydrometallurgy",
   disc("tank", "y", 6.5, 0, 8, "c", faces={"up": "pulp"})
   + legs4(10, inset=3.5, w=1, y0=7.5)
   + [
       el("hopper", [4, 10, 4], [12, 15, 12], "hop", full=["north", "south", "east", "west"]),
       el("hopper_cone", [6, 8.5, 6], [10, 10, 10], "lab"),
       el("lid", [3.5, 15, 3.5], [12.5, 16, 12.5], "d"),
       el("vent_filter", [6.5, 16, 6.5], [9.5, 17, 9.5], "st"),
       nozzle("lime_line", [13, 3, 7], [16, 5, 9], "alk", "east"),
   ], {"hop": "block/tailings_neutralizer_hopper.png"})

# =====================================================================
# reagents
# =====================================================================
mk("pyrite_roaster", "reagents", [
    el("body", [1, 0, 1], [15, 12, 15], "d", faces={"north": "front"}, full=["north"]),
    el("glow_peephole", [6, 7, 0.75], [10, 9, 1], "fire"),
    el("roof", [2, 12, 2], [14, 13.5, 14], "ds"),
    el("so2_duct", [5.5, 13.5, 5.5], [10.5, 16, 10.5], "gas"),
    el("feeder", [0, 8, 5], [1, 12, 11], "and"),
    el("calcine_chute", [6, 0, 0], [10, 2, 1], "d"),
], {"front": "block/pyrite_roaster_front.png"})

# сернокислотная установка: щит управления мультиблоком (печь + контактный аппарат + башня)
mk("acid_plant", "reagents", [
    el("body", [0, 0, 1], [16, 16, 16], "p"),
    el("panel", [1, 1, 0.5], [15, 15, 1], "panel", full=["north"]),
    el("glow_lamp_run", [11, 13, 0.25], [12.5, 14, 0.5], "lg"),
    el("glow_lamp_alarm", [13, 13, 0.25], [14.5, 14, 0.5], "lr"),
    el("so2_in", [3, 16, 7], [5, 17.5, 9], "gas", faces={"up": "end"}, full=["up"]),
    el("acid_out", [11, 16, 7], [13, 17.5, 9], "acid", faces={"up": "end"}, full=["up"]),
], {"panel": "block/acid_plant_panel.png"})

mk("sulfur_burner", "reagents",
   [el("saddle_w", [2, 0, 3], [5, 3, 13], "c"), el("saddle_e", [11, 0, 3], [14, 3, 13], "c")]
   + shell("shell", "x", 6, 1, 15, "shell")
   + disc("burner_plate", "x", 3, 0, 1, "ds", sides=8)
   + [
       el("glow_flame_port", [-0.25, 7, 7], [0.25, 9, 9], "fire"),
       el("sulfur_feed", [4, 13.5, 7], [6, 16, 9], "fuel"),
       nozzle("so2_out", [15, 6, 6], [16.5, 10, 10], "gas", "east"),
   ], {"shell": "block/sulfur_burner_shell.png"})

mk("contact_converter", "reagents",
   shell("shell", "y", 7, 0, 16, "shell")
   + [el(f"thermowell_{i}", [7, y, 0.5], [9, y + 1, 1.2], "b") for i, y in enumerate((2, 6, 10, 13.5))]
   + [
       el("gas_in", [6, 16, 6], [10, 17.5, 10], "gas"),
       nozzle("gas_out", [14, 1, 6], [16, 5, 10], "gas", "east"),
   ], {"shell": "block/contact_converter_shell.png"})

mk("absorption_tower", "reagents",
   shell("shell", "y", 5, 0, 16, "shell")
   + disc("flange_1", "y", 5.8, 5, 5.75, "ds", sides=8)
   + disc("flange_2", "y", 5.8, 10.5, 11.25, "ds", sides=8)
   + disc("distributor", "y", 3, 15.5, 16.5, "ds", sides=8)
   + [
       nozzle("gas_in", [0, 1.5, 6], [3.5, 5.5, 10], "gas", "west"),
       nozzle("acid_in", [12.5, 13, 7], [16, 15, 9], "acid", "east"),
       nozzle("acid_out", [12.5, 0.5, 7], [16, 2.5, 9], "acid", "east"),
   ], {"shell": "block/absorption_tower_shell.png"})

# Габер–Бош: колонна синтеза + компрессор на валу Create (маховик компрессора — partial, ось X)
mk("ammonia_synthesis_reactor", "reagents",
   [el("base", [0, 0, 0], [16, 1.5, 16], "d"),
    el("compressor", [1.5, 1.5, 4], [4.5, 12, 12], "and")]
   + shell("vessel", "y", 5.5, 1.5, 13.5, "shell", c=(10, 8, 8))
   + disc("closure", "y", 6.2, 13.5, 15, "ds", sides=8, c=(10, 8, 8))
   + [
       el("head_nozzle", [8.5, 15, 6.5], [11.5, 16.5, 9.5], "d"),
       el("syngas_riser", [2.5, 12, 7], [4, 14.5, 9], "h2"),
       el("syngas_line", [4, 13, 7], [5.5, 14.5, 9], "h2"),
       nozzle("nh3_out", [15.5, 3, 7], [17, 5, 9], "gas", "east"),
   ], {"shell": "block/ammonia_synthesis_reactor_shell.png"})
mk("ammonia_synthesis_reactor/compressor_wheel", "reagents",
   disc("wheel", "x", 5, 0, 1.4, "ds") + disc("hub", "x", 1.5, -0.5, 1.5, "st", sides=8)
   + [el("spoke", [0.6, 3.5, 7.5], [1.48, 12.5, 8.5], "red")],
   kind="partial")

# щелочной электролизёр: фильтр-прессный пакет + газосепараторы H₂/O₂
mk("electrolyzer", "reagents", [
    el("skid", [0, 0, 1], [16, 2, 15], "d"),
    el("stack", [1.5, 2, 3], [14.5, 11, 13], "stack"),
    el("end_plate_w", [0, 2, 2], [1.5, 12, 14], "p"),
    el("end_plate_e", [14.5, 2, 2], [16, 12, 14], "p"),
    el("tie_rod_n", [1.5, 11, 3], [14.5, 11.75, 3.75], "st"),
    el("tie_rod_s", [1.5, 11, 12.25], [14.5, 11.75, 13], "st"),
    el("busbar_w", [0.25, 12, 7], [1.25, 15, 9], "cu"),
    el("busbar_e", [14.75, 12, 7], [15.75, 15, 9], "cu"),
] + disc("h2_separator", "x", 1.8, 2, 14, "h2", sides=8, c=(8, 13.8, 5.5))
  + disc("o2_separator", "x", 1.8, 2, 14, "air", sides=8, c=(8, 13.8, 10.5)),
   {"stack": "block/electrolyzer_stack.png"})

# воздухоразделительная установка: холодильный блок, на фасаде — вентилятор забора воздуха (partial, ось Z)
mk("air_separator", "reagents", [
    el("cold_box", [1, 0, 3], [15, 16, 16], "box"),
    el("shroud_b", [1, 1, 1], [15, 2, 3], "and"),
    el("shroud_t", [1, 14, 1], [15, 15, 3], "and"),
    el("shroud_w", [1, 2, 1], [2, 14, 3], "and"),
    el("shroud_e", [14, 2, 1], [15, 14, 3], "and"),
    el("n2_out", [3, 16, 8], [5, 17.5, 10], "gas", faces={"up": "end"}, full=["up"]),
    el("o2_out", [11, 16, 8], [13, 17.5, 10], "air", faces={"up": "end"}, full=["up"]),
], {"box": "block/air_separator_coldbox.png"})
mk("air_separator/fan", "reagents",
   disc("hub", "z", 1.5, 1, 3, "ds", sides=8)
   + [
       el("blade_v", [7.4, 2.5, 1.75], [8.6, 13.5, 2.25], "sm"),
       el("blade_h", [2.5, 7.4, 1.73], [13.5, 8.6, 2.27], "sm"),
       el("blade_d1", [7.4, 2.5, 1.74], [8.6, 13.5, 2.24], "sm", rot=("z", 45, [8, 8, 8])),
       el("blade_d2", [7.4, 2.5, 1.76], [8.6, 13.5, 2.26], "sm", rot=("z", -45, [8, 8, 8])),
   ], kind="partial")

mk("ostwald_converter", "reagents",
   shell("boiler", "y", 6.5, 0, 7, "shell")
   + disc("gauze_flange", "y", 7, 7, 8, "ds", sides=8)
   + shell("hood", "y", 5.5, 8, 12, "shell")
   + disc("cone", "y", 3.5, 12, 14, "sm", sides=8)
   + [
       el("feed_in", [6, 14, 6], [10, 16, 10], "gas"),
       el("glow_gauze_port", [6.5, 7.1, 0.75], [9.5, 7.9, 1.05], "fire"),
       nozzle("nox_out", [13.5, 2, 6.5], [16, 5, 9.5], "gas", "east"),
   ], {"shell": "block/ostwald_converter_shell.png"})

mk("peroxide_reactor", "reagents",
   [el("base", [1, 0, 1], [15, 1, 15], "d")]
   + shell("vessel", "y", 5, 1, 12, "shell")
   + disc("dome", "y", 3.5, 12, 13.5, "sm", sides=8)
   + [
       nozzle("h2_in", [0, 4, 7], [3.5, 6, 9], "h2", "west"),
       nozzle("o2_in", [12.5, 4, 7], [16, 6, 9], "air", "east"),
       el("relief_valve", [7.25, 13.5, 7.25], [8.75, 15.5, 8.75], "red"),
       nozzle("product_out", [7, 1, 1], [9, 3, 3.5], "acid", "north"),
   ], {"shell": "block/peroxide_reactor_shell.png"})

mk("solvay_tower", "reagents",
   shell("shell", "y", 5.5, 0, 16, "shell")
   + [x for y in (3.5, 7.5, 11.5) for x in disc(f"flange_{int(y)}", "y", 6.2, y, y + 0.75, "ds", sides=8)]
   + [
       nozzle("co2_in", [0, 1.5, 7], [3, 3.5, 9], "gas", "west"),
       nozzle("brine_in", [13, 13, 7], [16, 15, 9], "alk", "east"),
       nozzle("slurry_out", [13, 1, 7], [16, 3, 9], "alk", "east"),
   ], {"shell": "block/solvay_tower_shell.png"})

mk("coke_oven", "reagents", [
    el("brickwork", [0, 0, 0.5], [16, 16, 16], "fb"),
    el("door", [2, 1, 0], [14, 13, 0.5], "door", full=["north"]),
    el("buckstay_w", [0.25, 0, -0.5], [2, 16, 0.5], "d"),
    el("buckstay_e", [14, 0, -0.5], [15.75, 16, 0.5], "d"),
    el("glow_door_seam", [7, 13, 0.1], [9, 13.5, 0.5], "fire"),
    el("standpipe", [6, 16, 10], [9, 17.5, 13], "fuel"),
    el("gooseneck", [6, 16.5, 7], [9, 18, 10], "fuel"),
], {"door": "block/coke_oven_door.png"})

mk("fractionating_column", "reagents",
   shell("shell", "y", 5, 0, 16, "shell")
   + [nozzle(f"draw_{i}", [12.5, y, 7], [16, y + 2, 9], "fuel", "east") for i, y in enumerate((2, 7, 12))]
   + [el("ladder_n", [2, 0, 5], [2.5, 16, 5.5], "d"), el("ladder_s", [2, 0, 10.5], [2.5, 16, 11], "d")]
   + [el(f"rung_{y}", [2.01, y, 5.5], [2.49, y + 0.5, 10.5], "d") for y in (2, 5, 8, 11, 14)],
   {"shell": "block/fractionating_column_shell.png"})

mk("brine_extractor", "reagents", [
    el("pad", [0, 0, 0], [16, 1.5, 16], "c"),
    el("motor", [1, 1.5, 4], [8, 7, 9], "mb"),
    el("coupling_guard", [8, 2, 5], [9.5, 6, 8], "yel"),
    el("pump", [9.5, 1.5, 4], [13.5, 7, 9], "and", faces={"north": "cas"}, full=["north"]),
    el("discharge_riser", [10.5, 7, 5.5], [12.5, 12, 7.5], "wat"),
    nozzle("discharge", [12.5, 10, 5.5], [16, 12, 7.5], "wat", "east"),
    el("suction_pipe", [10.5, 1.5, 9], [12.5, 4, 12], "wat"),
    el("intake_head", [9.5, 1.5, 12], [13.5, 3, 15], "d"),
    el("control_box", [1, 1.5, 11], [5, 8, 15], "cab"),
    el("glow_lamp", [2.5, 6, 10.75], [3.5, 7, 11], "lg"),
], {"cas": "block/brine_extractor_casing.png"})

mk("mg_electrolysis_cell", "reagents", [
    el("cell", [0, 0, 1], [16, 11, 15], "side"),
    el("lid", [0.5, 11, 1.5], [15.5, 12, 14.5], "d"),
    el("anode_1", [3, 12, 4], [5, 15, 11], "g"),
    el("anode_2", [7, 12, 4], [9, 15, 11], "g"),
    el("anode_3", [11, 12, 4], [13, 15, 11], "g"),
    el("busbar", [2, 15, 7], [14, 16, 9], "cu"),
    el("cl2_offtake", [1, 12, 12], [3, 15, 14], "gas"),
    el("tap_frame", [5.5, 3.5, 0.75], [10.5, 6.5, 1], "d"),
    el("glow_tap_port", [6.5, 4.25, 0.5], [9.5, 5.75, 0.75], "fire"),
], {"side": "block/mg_electrolysis_cell_side.png"})

mk("chlor_alkali_cell", "reagents",
   [el("skid", [0, 0, 0.5], [16, 1.5, 15.5], "d"),
    el("cells", [1, 1.5, 2], [15, 11, 14], "cells"),
    el("busbar_w", [0, 4, 5], [1, 10, 11], "cu"),
    el("busbar_e", [15, 4, 5], [16, 10, 11], "cu")]
   + disc("cl2_header", "x", 1.2, 0.5, 15.5, "gas", sides=8, c=(8, 12.3, 4.5))
   + disc("h2_header", "x", 1.2, 0.5, 15.5, "h2", sides=8, c=(8, 12.3, 8))
   + disc("naoh_header", "x", 1.2, 0.5, 15.5, "alk", sides=8, c=(8, 12.3, 11.5)),
   {"cells": "block/chlor_alkali_cell_front.png"})

mk("process_heater", "reagents", [
    el("body", [0, 0, 0.5], [16, 16, 16], "p"),
    el("front", [1, 1, 0.25], [15, 15, 0.5], "front", full=["north"]),
    el("glow_coil_window", [4, 6, 0], [12, 11, 0.25], "coil", full=["north"]),
    el("terminal_box", [3, 16, 4], [13, 17, 12], "d"),
    nozzle("inlet", [16, 3, 7], [17.5, 5, 9], "wat", "east"),
    nozzle("outlet", [-1.5, 11, 7], [0, 13, 9], "steam", "west"),
], {"front": "block/process_heater_front.png"})

mk("process_steam_port", "reagents", [
    el("body", [1, 1, 4], [15, 15, 16], "p"),
    el("wall_plate", [0, 0, 3], [16, 16, 4], "d", faces={"north": "front"}, full=["north"]),
    el("steam_stub", [5, 5, 0.5], [11, 11, 3], "steam"),
    el("flange", [4, 4, 0], [12, 12, 0.5], "d", faces={"north": "end"}, full=["north"]),
    el("gauge", [11.5, 12, 2.5], [14.5, 15, 3], "dial", full=["north"]),
    el("valve_stem", [7.5, 15, 9], [8.5, 17, 10], "st"),
] + ring_y("handwheel", 2.5, 0.6, 17, 17.6, "red", c=(8, 9.5)),
   {"front": "block/process_steam_port_front.png"})

# =====================================================================
# conversion
# =====================================================================
mk("dissolver", "conversion",
   [el("base_ring", [2, 0, 2], [14, 1.9, 14], "d")]
   + shell("vessel", "y", 6, 2, 13, "shell")
   + disc("head", "y", 4.5, 13, 14, "st", sides=8)
   + [
       el("charge_hopper", [5.5, 14, 5.5], [10.5, 16, 10.5], "p"),
       el("nox_offgas", [11, 13.2, 7], [13, 16, 9], "gas"),
       nozzle("hno3_in", [0, 10, 7], [2.5, 12, 9], "acid", "west"),
       nozzle("unh_out", [13.5, 3, 7], [16, 5, 9], "acid", "east"),
   ], {"shell": "block/dissolver_shell.png"})

# пульсационная колонна: сегмент 1×1×N, пульсатор — шток (partial, поступательный)
mk("pulsed_column", "conversion",
   shell("column", "y", 4, 0, 16, "shell")
   + disc("flange_b", "y", 5, 0.05, 1, "ds", sides=8)
   + disc("flange_t", "y", 5, 15, 15.95, "ds", sides=8)
   + [
       el("pulse_leg", [12, 0, 6.5], [14.5, 11, 9.5], "st"),
       el("pulse_link", [11.5, 1, 7], [12.5, 3, 9], "st"),
       el("pulser", [11.5, 11, 6], [15, 15, 10], "and"),
       el("pulser_cap", [12, 15.02, 6.5], [14.5, 15.9, 9.5], "d"),
       nozzle("aqueous_in", [0, 13, 7], [4.5, 14.5, 9], "acid", "west"),
       nozzle("solvent_in", [0, 2, 7], [4.5, 3.5, 9], "fuel", "west"),
   ], {"shell": "block/pulsed_column_shell.png"})
mk("pulsed_column/piston", "conversion", [
    el("rod", [12.75, 14, 7.5], [13.75, 17.5, 8.5], "st"),
    el("crosshead", [12, 17, 7], [14.5, 18, 9], "d"),
], kind="partial")

mk("evaporator", "conversion",
   shell("calandria", "y", 4, 0, 8, "shell")
   + shell("vapour_body", "y", 6, 8, 15, "shell")
   + disc("dome", "y", 4, 15, 16, "sm", sides=8)
   + [
       el("sight_glass", [6.5, 10, 1.8], [9.5, 13, 2.1], "gl", full=["north"]),
       nozzle("steam_in", [11.5, 5, 7], [16, 7, 9], "steam", "east"),
       el("vapour_out", [6.5, 16, 6.5], [9.5, 17.5, 9.5], "steam", faces={"up": "end"}, full=["up"]),
       nozzle("concentrate_out", [0, 1, 7], [4.5, 3, 9], "acid", "west"),
   ], {"shell": "block/evaporator_shell.png"})

mk("denitration_reactor", "conversion",
   [el("base", [1, 0, 1], [15, 1.5, 15], "d")]
   + shell("reactor", "y", 5, 1.5, 14, "shell")
   + [
       el("no2_offgas", [6.5, 14, 6.5], [9.5, 16, 9.5], "gas"),
       nozzle("unh_in", [0, 10, 7], [3.5, 12, 9], "acid", "west"),
       el("uo3_chute", [6.5, 1.5, 1], [9.5, 4, 3.5], "d"),
       el("heater_box", [12.5, 6, 5.5], [15.5, 10, 10.5], "cab"),
       el("glow_heater_lamp", [13.5, 8, 5.25], [14.5, 9, 5.5], "ly"),
   ], {"shell": "block/denitration_reactor_shell.png"})

mk("reduction_furnace", "conversion", [
    el("body", [1, 0, 1], [15, 13, 15], "fb", faces={"north": "front"}, full=["north"]),
    el("glow_peephole", [7, 8, 0.75], [9, 9.5, 1], "fire"),
    nozzle("retort_w", [-1, 3, 6.5], [1, 6, 9.5], "st", "west"),
    nozzle("retort_e", [15, 3, 6.5], [17, 6, 9.5], "st", "east"),
    el("h2_line", [-0.5, 6, 7.25], [0.5, 13, 8.75], "h2"),
    el("h2_feed", [-0.5, 13, 7.3], [3, 14.5, 8.7], "h2"),
    el("thermocouple", [7, 13, 7], [9, 15, 9], "d"),
], {"front": "block/reduction_furnace_front.png"})

# печь HF: вращающийся барабан в неподвижной обогревательной рубашке; барабан — partial (ось X)
mk("hf_kiln", "conversion", [
    el("base", [0, 0, 2], [16, 1, 14], "c"),
    el("rollers_w", [2.5, 1, 4], [4.5, 2.4, 12], "and"),
    el("rollers_e", [11.5, 1, 4], [13.5, 2.4, 12], "and"),
    el("jacket", [5, 1.5, 1.5], [11, 14.5, 14.5], "fb", faces={"north": "jk", "south": "jk"}, full=["north", "south"]),
    el("glow_burner", [7, 3, 1.25], [9, 5, 1.5], "fire"),
    el("feed_hood", [0, 1, 2], [2, 14, 14], "p"),
    el("discharge_hood", [14, 1, 2], [16, 14, 14], "p"),
    el("hf_offgas", [0.5, 14, 6.5], [1.75, 16, 9.5], "gas"),
    nozzle("acid_feed", [-1.5, 9, 7], [0, 11, 9], "acid", "west"),
], {"jk": "block/hf_kiln_jacket.png"})
mk("hf_kiln/shell", "conversion",
   shell("kiln", "x", 5, 2, 14, "shell")
   + disc("tyre_w", "x", 5.8, 2.8, 4.3, "ds", sides=8)
   + disc("tyre_e", "x", 5.8, 11.7, 13.2, "ds", sides=8),
   {"shell": "block/hf_kiln_shell.png"}, kind="partial")

mk("hf_condenser", "conversion",
   [el("saddle_w", [2, 0, 4], [4.5, 3.5, 12], "c"), el("saddle_e", [11.5, 0, 4], [14, 3.5, 12], "c")]
   + shell("shell", "x", 4.5, 2, 14, "shell")
   + disc("head_w", "x", 5.2, 0.5, 2, "ds", sides=8)
   + disc("head_e", "x", 5.2, 14, 15.5, "ds", sides=8)
   + disc("cap_w", "x", 3.5, 0, 0.5, "ds", sides=8)
   + disc("cap_e", "x", 3.5, 15.5, 16, "ds", sides=8)
   + [
       el("water_out", [9.5, 12, 7], [11.5, 14.5, 9], "wat"),
       el("water_in", [4.75, 12, 7], [6.75, 14.5, 9], "wat"),
       el("hf_gas_in", [0.75, 12.5, 7], [1.75, 15.5, 9], "gas"),
       el("hf_liquid_out", [14.25, 1, 7], [15.25, 3.5, 9], "acid"),
   ], {"shell": "block/hf_condenser_shell.png"})

mk("hydrofluorination_reactor", "conversion",
   [el("base", [1, 0, 1], [15, 1.5, 15], "d")]
   + shell("tube", "y", 4, 1.5, 15, "shell")
   + [
       el("uo2_hopper", [5, 14, 5], [11, 16, 11], "p"),
       el("uf4_chute", [6.5, 1.5, 1.5], [9.5, 4, 4.5], "d"),
       nozzle("hf_in", [11.5, 3, 7], [16, 5, 9], "gas", "east"),
       nozzle("offgas_out", [0, 12, 7], [4.5, 14, 9], "gas", "west"),
       el("heater_terminal", [3, 6, 3], [5, 9, 5], "cab"),
   ], {"shell": "block/hydrofluorination_reactor_shell.png"})

# фтористая ячейка: электролизёр KF·2HF, угольные аноды, раздельные отводы F₂ и H₂
mk("fluorine_cell", "conversion", [
    el("tank", [1, 0, 2], [15, 10, 14], "side"),
    el("lid", [0.5, 10, 1.5], [15.5, 11, 14.5], "d"),
    el("anode_1", [3, 11, 4], [5, 13, 7], "g"),
    el("anode_2", [7, 11, 4], [9, 13, 7], "g"),
    el("anode_3", [11, 11, 4], [13, 13, 7], "g"),
    el("busbar", [2, 13, 5], [14, 14, 6], "cu"),
    el("f2_outlet", [3, 11, 10], [5, 15, 12], "gas"),
    el("h2_outlet", [11, 11, 10], [13, 15, 12], "h2"),
    nozzle("cooling_in", [0, 2, 7], [1, 4, 9], "wat", "west"),
    el("hazard_plinth", [1, 0, 1.5], [15, 1, 2], "h"),
], {"side": "block/fluorine_cell_side.png"})

# пламенная башня фторирования
mk("fluorination_reactor", "conversion",
   shell("tower", "y", 4.5, 0, 15, "shell")
   + [
       el("uf4_hopper", [5.5, 15, 5.5], [10.5, 17, 10.5], "p"),
       nozzle("f2_in", [12, 12.8, 7], [16, 14.8, 9], "gas", "east"),
       nozzle("uf6_out", [0, 2, 7], [4, 4, 9], "gas", "west"),
       el("glow_flame_port", [7, 9, 3.25], [9, 11, 3.55], "fire"),
       el("ash_pot", [6, 0, 1.5], [10, 2, 3.75], "d"),
   ], {"shell": "block/fluorination_reactor_shell.png"})

mk("cold_trap", "conversion",
   shell("vessel", "y", 5, 0, 13, "shell", cap="frost")
   + disc("frost_collar", "y", 4, 13, 14, "frost", sides=8)
   + [
       el("refrigerant_in", [12.5, 3, 7], [16, 4.5, 8.5], "cu"),
       el("refrigerant_out", [12.5, 9, 7], [16, 10.5, 8.5], "cu"),
       el("uf6_in", [7, 14, 7], [9, 16, 9], "gas"),
       el("inlet_valve", [6.5, 14.5, 6.5], [9.5, 15.5, 9.5], "b"),
   ], {"shell": "block/cold_trap_shell.png"})

# автоклав отбора UF₆: горизонтальный сосуд, дверь — на фасаде (ось Z)
mk("uf6_autoclave", "conversion",
   [el("skid_w", [1, 0, 2], [3, 2, 15], "d"), el("skid_e", [13, 0, 2], [15, 2, 15], "d")]
   + shell("body", "z", 7, 1, 16, "shell")
   + disc("door", "z", 6.5, 0, 1, "sm")
   + disc("lock_ring", "z", 7.3, 0.5, 1.25, "ds", sides=8)
   + [
       el("door_hub", [6.5, 6.5, -0.5], [9.5, 9.5, 0], "ds"),
       el("spoke_h", [3, 7.6, -0.4], [13, 8.4, -0.1], "red"),
       el("spoke_v", [7.6, 3, -0.41], [8.4, 13, -0.11], "red"),
       el("hinge", [14, 6, -0.25], [15.5, 10, 1.5], "d"),
       el("heater_line", [7, 15, 9], [9, 17, 11], "steam"),
       el("uf6_outlet", [11, 13, 12], [13, 16.5, 14], "gas"),
   ], {"shell": "block/uf6_autoclave_shell.png"})

mk("bomb_reduction_vessel", "conversion",
   [el("stand", [2, 0, 2], [14, 2, 14], "fb")]
   + shell("bomb", "y", 5, 2, 12, "shell")
   + disc("flange", "y", 6, 12, 13.5, "ds", sides=8)
   + disc("lid", "y", 5, 13.5, 14.5, "ds", sides=8)
   + [
       el("stud_n", [7.5, 12.2, 1.6], [8.5, 14.2, 2.6], "st"),
       el("stud_s", [7.5, 12.2, 13.4], [8.5, 14.2, 14.4], "st"),
       el("stud_w", [1.6, 12.2, 7.5], [2.6, 14.2, 8.5], "st"),
       el("stud_e", [13.4, 12.2, 7.5], [14.4, 14.2, 8.5], "st"),
       el("lifting_lug", [7, 14.5, 7.5], [9, 16, 8.5], "d"),
       el("thermowell", [10, 14.5, 7], [11, 16, 8], "st"),
   ], {"shell": "block/bomb_reduction_vessel_shell.png"})

mk("inert_glovebox", "conversion",
   legs4(6, inset=1.5, w=1)
   + [
       el("box", [0.5, 6, 2], [15.5, 15, 15], "st"),
       el("window", [1, 7, 1.5], [15, 14.5, 2], "win", full=["north"]),
       el("glove_l", [3.5, 8.5, 0.5], [6, 11, 1.5], "r"),
       el("glove_r", [10, 8.5, 0.5], [12.5, 11, 1.5], "r"),
       el("argon_line", [3, 15, 7], [5, 16.5, 9], "gas"),
   ]
   + disc("airlock", "x", 2.5, 15.5, 17.5, "st", sides=8, c=(8, 10.5, 8.5))
   + disc("airlock_door", "x", 2, 17.5, 18, "ds", sides=8, c=(8, 10.5, 8.5)),
   {"win": "block/inert_glovebox_front.png"})
