"""Fuel cycle models (GDD 02 §2.5–2.9): enrichment, fuel fabrication, spent fuel, radiochemistry, waste.

Plus flat items for every item of section 02 and all section-02 fluids/gases.
"""
from pclib import *
from tex_fuelcycle import ITEM_TEX

FC = lambda n: f"block/common/fuelcycle_{n}.png"

# короткие ключи текстур, общие для всех моделей области
COM = {
    "p": T("steel_plate"), "d": T("steel_dark"), "ds": T("steel_dark_smooth"), "st": T("steel_bare"),
    "sm": T("steel_smooth"), "b": T("brass"), "bs": T("brass_smooth"), "h": T("hazard"), "r": T("rubber"),
    "red": T("paint_red"), "mot": T("motor_blue"), "lr": T("lamp_red"), "ly": T("lamp_yellow"),
    "lg": T("lamp_green"), "end": T("pipe_end"), "con": T("concrete"), "lead": T("lead"), "tre": T("trefoil"),
    "cab": T("cabinet"), "gr": T("graphite"), "zr": T("zirconium"),
    "wpipe": "block/pipe_feedwater.png", "apipe": "block/pipe_chemical.png",
    "ss": FC("ss"), "sss": FC("ss_smooth"), "ref": FC("refractory"), "ins": FC("insul"),
    "hc": FC("heavy_concrete"), "hot": FC("glow_hot"), "lgl": FC("lead_glass"), "gl": FC("glass"),
    "bor": FC("boral"), "frost": FC("frost"), "gw": FC("grinding_wheel"), "he": FC("bottle_he"),
    "en": FC("enamel_blue"), "cu": FC("copper"), "cus": FC("copper_smooth"), "bit": FC("bitumen"),
    "uf6": FC("pipe_uf6"), "air": FC("pipe_air"), "grat": FC("grating"), "charge": FC("charge"),
    "il": FC("impact_limiter"), "cs": FC("concrete_smooth"), "yp": FC("yellow_paint"), "gp": FC("green_paint"),
    "water": FC("water"), "arc": FC("arc"), "alk": FC("pipe_alkali"), "drum": FC("drum"),
}


def mk(id_, cat, els, kind="block", extra=None):
    """Register a model; key 'f' = own texture block/<block_id>.png; only used keys are emitted."""
    base = id_.split("/")[0]
    tex = dict(COM, f=f"block/{base}.png")
    if extra:
        tex.update(extra)
    used = []
    for e in els:
        for k in [e["tex"], *e["faces"].values()]:
            if k not in used:
                used.append(k)
    textures = {k: tex[k] for k in used}
    if kind == "partial":
        java = f"block/{base}/partial/{id_.split('/', 1)[1]}"
    else:
        java = f"block/{id_}"
    model(id_, cat, textures, els, java, kind)


def cube(id_, cat, top=None):
    faces = {"up": top, "down": top} if top else None
    mk(id_, cat, [el("block", [0, 0, 0], [16, 16, 16], "f", faces=faces)])


SIDES = ["north", "south", "east", "west"]


def dsc(name, axis, r, a0, a1, tex, sides=16, cap=None, c=(8, 8, 8)):
    """disc() whose end faces get a plain `cap` texture (side textures with bands look wrong on caps)."""
    ends = {"y": ("up", "down"), "x": ("east", "west"), "z": ("north", "south")}[axis]
    return disc(name, axis, r, a0, a1, tex, sides, faces={e: cap for e in ends} if cap else None, c=c)

# =====================================================================
# enrichment (§2.5)
# =====================================================================
ENR = "enrichment"

# центрифуга 1×1×2: тонкий высокий кожух, ротор скрыт (partial не нужен)
mk("gas_centrifuge", ENR,
   [el("base", [1, 0, 1], [15, 2, 15], "d")]
   + disc("motor", "y", 5, 2, 5, "ds", 8)
   + disc("casing", "y", 4, 5, 29, "f")
   + disc("top_flange", "y", 4.8, 29, 30.5, "ds", 8)
   + [el("cap", [6, 30.5, 6], [10, 32, 10], "st"),
      el("line_feed", [7.5, 30.75, 1], [8.5, 31.75, 6], "uf6"),
      el("line_product", [1, 30.75, 7.5], [6, 31.75, 8.5], "uf6"),
      el("line_tails", [10, 30.75, 7.5], [15, 31.75, 8.5], "uf6")])

hdr = [
    el("leg_w", [1.5, 0, 7], [3, 8, 9], "d"),
    el("leg_e", [13, 0, 7], [14.5, 8, 9], "d"),
    el("beam_w", [1.5, 8, 0.5], [3, 9, 15.5], "d"),
    el("beam_e", [13, 8, 0.5], [14.5, 9, 15.5], "d"),
]
for i, (z0, tag) in enumerate(((1, "cab"), (6, "red"), (11, "mot"))):
    nm = ("feed", "light", "heavy")[i]
    hdr += [el(f"line_{nm}", [0, 9, z0], [16, 13, z0 + 4], "f", faces={"east": "end", "west": "end"}, full=["east", "west"]),
            el(f"tag_{nm}", [7, 13, z0 + 0.5], [9, 13.5, z0 + 3.5], tag)]
mk("cascade_header", ENR, hdr)

mk("cascade_interstage_pipe", ENR, [
    el("body", [5, 5, 1.5], [11, 11, 14.5], "f"),
    el("flange_n", [4, 4, 0], [12, 12, 1.5], "d", faces={"north": "end"}, full=["north"]),
    el("flange_s", [4, 4, 14.5], [12, 12, 16], "d", faces={"south": "end"}, full=["south"]),
    el("saddle", [6.5, 0, 7], [9.5, 5, 9], "d"),
    el("saddle_foot", [5, 0, 6], [11, 0.5, 10], "d"),
])


def station(id_, top):
    els = [
        el("plinth", [0, 0, 0], [16, 1, 16], "d"),
        el("body", [1, 1, 1], [15, 13, 15], "p"),
        el("panel", [2, 2, 0.5], [14, 12, 1], "f", full=["north"]),
    ]
    mk(id_, ENR, els + top)


station("cascade_feed_station", [
    el("riser", [7, 13, 7], [9, 16, 9], "uf6"),
    el("valve_body", [6.5, 14, 6.5], [9.5, 15, 9.5], "d"),
    el("stem", [7.75, 15, 7.75], [8.25, 16.5, 8.25], "st"),
    el("wheel_x", [5.5, 16.5, 7.75], [10.5, 17, 8.25], "red"),
    el("wheel_z", [7.75, 16.5, 5.5], [8.25, 17, 10.5], "red"),
])
station("cascade_product_station", disc("cold_trap", "y", 3, 13, 16, "frost") + [
    el("trap_head", [6.5, 16, 6.5], [9.5, 17, 9.5], "d"),
    el("line_in", [12, 10, 7], [16, 12, 9], "uf6"),
])
station("cascade_tails_station", [
    el("line_out", [15, 5, 6], [16, 9, 10], "uf6"),
    el("line_up", [11, 13, 6], [15, 15, 10], "uf6"),
    el("hazard_band", [1, 12.5, 0.75], [15, 13, 1], "h"),
])

mk("cascade_isolation_valve", ENR, [
    el("line", [0, 3, 5], [16, 9, 11], "uf6", faces={"east": "end", "west": "end"}, full=["east", "west"]),
    el("body", [5, 2, 4], [11, 10, 12], "ss"),
    el("actuator", [4.5, 10, 4.5], [11.5, 15, 11.5], "f", full=SIDES),
    el("actuator_cap", [5.5, 15, 5.5], [10.5, 16, 10.5], "d"),
    el("solenoid", [11.5, 11, 6], [13.5, 14, 10], "d"),
    el("glow_lamp", [12, 12, 5.5], [13, 13, 6], "lg"),
])

mk("trace_heater", ENR, [
    el("jacket", [4, 4, 0], [12, 12, 16], "f", faces={"north": "end", "south": "end"}, full=["north", "south"]),
    el("junction_box", [6, 12, 6], [10, 15, 10], "d"),
    el("glow_lamp", [7.5, 13, 5.5], [8.5, 14, 6], "ly"),
    el("gland", [7.5, 15, 7.5], [8.5, 16, 8.5], "r"),
])

# =====================================================================
# fuel fabrication (§2.6)
# =====================================================================
FUEL = "fuel_fabrication"

mk("defluorination_kiln", FUEL,
   [el("base", [0, 0, 2], [16, 1, 14], "d"),
    el("roller_w", [2, 1, 4], [5, 4, 12], "p"),
    el("roller_e", [11, 1, 4], [14, 4, 12], "p")]
   + dsc("drum", "x", 4.5, 0.5, 15.5, "f", cap="ref")
   + disc("tyre_w", "x", 5.2, 3, 4.5, "ds", 8)
   + disc("tyre_e", "x", 5.2, 11.5, 13, "ds", 8)
   + [el("feed_hood", [0, 2, 2.5], [2, 14, 13.5], "p"),
      el("hf_offgas", [0.25, 14, 7], [1.75, 16, 9], "apipe"),
      el("discharge_hood", [14, 1, 4], [16, 12, 12], "p")])

mk("pellet_press", FUEL, [
    el("base", [0, 0, 0], [16, 3, 16], "d"),
    el("die_table", [4, 3, 3], [12, 6, 13], "st"),
    el("column_w", [1, 3, 5], [4, 13, 11], "gp"),
    el("column_e", [12, 3, 5], [15, 13, 11], "gp"),
    el("crown", [0, 13, 3], [16, 16, 13], "gp", faces={"north": "f", "south": "f"}, full=["north", "south"]),
    el("feed_shoe", [5, 6, 12], [11, 8, 15], "ss"),
    el("powder_hopper", [6, 8, 13], [10, 12, 16], "ss"),
])
mk("pellet_press/ram", FUEL, [
    el("ram", [5, 8, 5], [11, 13, 11], "st"),
    el("punch_plate", [6, 7, 6], [10, 8, 10], "d"),
], kind="partial")

mk("sintering_furnace", FUEL, [
    el("casing", [0, 0, 1], [16, 16, 16], "d"),
    el("door", [2, 2, 0.5], [14, 14, 1], "f", full=["north"]),
    el("glow_peephole", [7, 8.5, 0.25], [9, 10, 0.5], "hot"),
    el("hinge_t", [1, 10, 0.25], [2, 12, 1], "st"),
    el("hinge_b", [1, 4, 0.25], [2, 6, 1], "st"),
    el("h2_line", [2, 16, 3], [10, 17, 4], "uf6"),
    el("flare_stub", [11, 16, 11], [13, 17.5, 13], "st"),
    el("glow_flare", [11.5, 17.5, 11.5], [12.5, 18, 12.5], "hot"),
])

mk("centerless_grinder", FUEL, [
    el("bed", [0, 0, 1], [16, 3, 15], "d"),
    el("housing", [1, 3, 12], [15, 10, 15], "f", full=["north"]),
    el("guard", [2.5, 11, 3.5], [13.5, 14, 12.5], "p", faces={"north": "f"}, full=["north"]),
    el("trough", [0, 5.5, 1], [16, 6.5, 3], "st"),
    el("work_rest", [0, 3, 1.5], [16, 5.5, 2.5], "ds"),
    el("motor", [10, 10, 12.5], [15, 13, 16], "mot"),
])
mk("centerless_grinder/wheel", FUEL,
   disc("wheel", "z", 5, 4.5, 11.5, "gw") + disc("hub", "z", 1.5, 4, 12, "st", 8), kind="partial")

mk("pellet_inspection_station", FUEL, [
    el("desk", [0, 0, 2], [16, 7, 16], "cab"),
    el("belt", [0, 7, 3], [16, 7.5, 8], "r"),
    el("arch_n", [5, 7, 2.5], [6.5, 12, 3.5], "ss"),
    el("arch_s", [5, 7, 7.5], [6.5, 12, 8.5], "ss"),
    el("arch_top", [5, 12, 2.5], [6.5, 13, 8.5], "ss"),
    el("glow_laser", [5.5, 11.5, 3.5], [6, 12, 7.5], "lr"),
    el("pellet_1", [1.5, 7.5, 5], [2.5, 8.5, 6], "gr"),
    el("pellet_2", [3, 7.5, 5], [4, 8.5, 6], "gr"),
    el("pellet_3", [11, 7.5, 5], [12, 8.5, 6], "gr"),
    el("monitor", [8, 7, 9.5], [15, 14, 12], "d"),
    el("glow_screen", [8.5, 7.5, 9], [14.5, 13.5, 9.5], "f", full=["north"]),
])

mk("dry_storage_cabinet", FUEL, [
    el("body", [1, 0, 2], [15, 16, 16], "cab"),
    el("door", [1.5, 0.5, 1.5], [14.5, 15.5, 2], "f", full=["north"]),
    el("handle", [12.75, 6, 1], [13.5, 10, 1.5], "d"),
    el("vent", [5, 16, 7], [11, 16.5, 12], "d"),
])

mk("mineral_separator", FUEL,
   [el("base", [0, 0, 1], [16, 2, 15], "d"),
    el("cheek_w", [0.5, 2, 2], [2, 12, 14], "f", full=["west", "north"]),
    el("cheek_e", [14, 2, 2], [15.5, 12, 14], "f", full=["east", "north"])]
   + disc("magnetic_drum", "x", 4, 2, 14, "st", c=(8, 7, 8))
   + [el("hopper_low", [4, 12, 5], [12, 13.5, 11], "p"),
      el("hopper_top", [2, 13.5, 3], [14, 16, 13], "p"),
      el("chute_mag", [2.5, 3.5, 0.5], [13.5, 4.5, 5], "ss", rot=("x", -22.5, [8, 4, 2.75])),
      el("chute_tail", [2.5, 3.5, 11], [13.5, 4.5, 15.5], "ss", rot=("x", 22.5, [8, 4, 13.25]))])

mk("chlorinator", FUEL,
   [el("base", [1, 0, 1], [15, 2, 15], "d")]
   + dsc("shell", "y", 5.5, 2, 14, "f", cap="ds")
   + disc("head", "y", 4.5, 14, 15.5, "ds", 8)
   + [el("cl2_inlet", [7, 3, 0], [9, 5, 3], "uf6"),
      el("offgas", [7, 15.5, 7], [9, 17.5, 9], "apipe"),
      el("coke_hopper", [12, 10, 6], [15.5, 13, 10], "p")])

mk("hafnium_separation_column", FUEL,
   [el("base", [2, 0, 2], [14, 1, 14], "d")]
   + disc("column", "y", 4, 1, 15, "f")
   + disc("flange_1", "y", 4.8, 5, 5.7, "ds", 8)
   + disc("flange_2", "y", 4.8, 10.5, 11.2, "ds", 8)
   + disc("cap", "y", 3, 15, 16, "ds", 8)
   + [el("crystallizer_e", [12, 3, 6], [14.5, 7, 10], "ss"),
      el("crystallizer_w", [1.5, 8, 6], [4, 12, 10], "ss")])

mk("kroll_reactor", FUEL,
   [el("plate", [0, 0, 0], [16, 1, 16], "d"),
    el("furnace", [1, 1, 1], [15, 12, 15], "f", full=SIDES),
    el("top_ring", [2, 12, 2], [14, 13, 14], "d")]
   + disc("retort_lid", "y", 5, 13, 14.5, "sss")
   + [el("clamp_w", [1.5, 12, 7], [2.5, 14, 9], "b"),
      el("clamp_e", [13.5, 12, 7], [14.5, 14, 9], "b"),
      el("ar_line", [7.5, 14.5, 7.5], [8.5, 16, 8.5], "uf6"),
      el("ar_elbow", [8.5, 15, 7.5], [14, 16, 8.5], "uf6")])

mk("vacuum_arc_furnace", FUEL,
   [el("base", [0, 0, 0], [16, 2, 16], "d")]
   + dsc("crucible", "y", 4.5, 2, 10, "f", cap="cus")
   + [el("chamber", [3, 10, 3], [13, 13, 13], "ss"),
      el("stinger", [7, 13, 7], [9, 18, 9], "st"),
      el("post_w", [2, 2, 7], [3, 15, 9], "p"),
      el("post_e", [13, 2, 7], [14, 15, 9], "p"),
      el("crossbar", [2, 15, 7], [14, 16, 9], "p"),
      el("vacuum_pump", [12.5, 2, 0.5], [16, 6, 4.5], "mot"),
      el("water_in", [3.5, 3, 0.5], [5, 9, 2], "wpipe")])

mk("pilger_mill", FUEL, [
    el("bed", [0, 0, 2], [16, 3, 14], "d"),
    el("tube", [0, 7.5, 7.5], [16, 8.5, 8.5], "zr"),
    el("chuck", [0, 3, 5], [3, 10, 11], "p"),
    el("crank_housing", [11, 3, 2], [16, 12, 14], "f", full=["north", "south"]),
    el("guide", [3, 3, 6], [5, 5, 10], "ds"),
])
mk("pilger_mill/stand", FUEL,
   [el("cheek_n", [5, 3.5, 3], [10, 13, 4.5], "gp"),
    el("cheek_s", [5, 3.5, 11.5], [10, 13, 13], "gp"),
    el("top_beam", [5, 13, 3], [10, 14, 13], "gp")]
   + disc("roll_upper", "z", 2.5, 4.5, 11.5, "st", 8, c=(7.5, 11, 8))
   + disc("roll_lower", "z", 2.5, 4.5, 11.5, "st", 8, c=(7.5, 5, 8)), kind="partial")

mk("ultrasonic_tester", FUEL, [
    el("console", [0, 0, 0.5], [6, 10, 5], "cab"),
    el("glow_screen", [0.5, 4, 0], [5.5, 9.5, 0.5], "f", full=["north"]),
    el("tank", [0, 0, 5], [16, 5.5, 15], "ss"),
    el("rim_n", [0, 5.5, 5], [16, 6.5, 6], "ss"),
    el("rim_s", [0, 5.5, 14], [16, 6.5, 15], "ss"),
    el("water", [0, 5.5, 6], [16, 6, 14], "water"),
    el("tube", [0, 7, 9.5], [16, 8, 10.5], "zr"),
    el("post_n", [9, 6.5, 5], [12, 12, 6], "p"),
    el("post_s", [9, 6.5, 14], [12, 12, 15], "p"),
    el("beam", [9, 12, 5], [12, 13, 15], "p"),
    el("probe", [10, 8, 9.5], [11, 12, 10.5], "st"),
])

mk("rod_loading_station", FUEL,
   [el("cabinet", [0, 0, 4], [16, 7, 13], "p"),
    el("panel", [3, 1, 3.5], [13, 6.5, 4], "f", full=["north"]),
    el("top", [0, 7, 3], [16, 8, 14], "ss"),
    el("cladding", [0, 8, 6.5], [16, 9, 7.5], "zr"),
    el("feeder", [1, 8, 4], [6, 12, 9], "ss"),
    el("feeder_chute", [6, 8, 6], [8, 9.5, 8], "st")]
   + disc("he_bottle", "y", 1.6, 8, 15, "he", 8, c=(13, 8, 11.5))
   + [el("he_valve", [12.5, 15, 11], [13.5, 16, 12], "b")])

mk("rod_welder", FUEL, [
    el("base", [0, 0, 0], [16, 2, 16], "d"),
    el("chamber", [2, 2, 2], [14, 13, 14], "d"),
    el("front", [2.5, 3, 1.5], [13.5, 12, 2], "f", full=["north"]),
    el("glow_arc", [7, 7.5, 1.25], [9, 9, 1.5], "arc"),
    el("rod", [0, 7, 7.5], [16, 8, 8.5], "zr"),
    el("gas_line", [7, 13, 7], [9, 15, 9], "uf6"),
])

mk("gas_collector", FUEL, [
    el("skirt", [0, 0, 0], [16, 3, 16], "f"),
    el("hood_1", [2, 3, 2], [14, 6, 14], "p"),
    el("hood_2", [4, 6, 4], [12, 9, 12], "p"),
    el("neck", [6, 9, 6], [10, 12, 10], "ss"),
    el("he_line", [7, 12, 7], [9, 16, 9], "uf6"),
    el("flange", [6.5, 13, 6.5], [9.5, 14, 9.5], "d"),
])

jig = [el("base", [0, 0, 0], [16, 1, 16], "d"), el("seat", [4, 1, 4], [12, 2, 12], "st")]
for nm, (x, z) in {"nw": (0.5, 0.5), "ne": (13.5, 0.5), "sw": (0.5, 13.5), "se": (13.5, 13.5)}.items():
    jig.append(el(f"post_{nm}", [x, 1, z], [x + 2, 16, z + 2], "f", full=SIDES))
jig += [
    el("clamp_n", [2.5, 7, 0.75], [13.5, 8.5, 2.25], "b"),
    el("clamp_s", [2.5, 7, 13.75], [13.5, 8.5, 15.25], "b"),
    el("clamp_w", [0.75, 7, 2.5], [2.25, 8.5, 13.5], "b"),
    el("clamp_e", [13.75, 7, 2.5], [15.25, 8.5, 13.5], "b"),
]
mk("assembly_jig", FUEL, jig)

mk("acheson_furnace", FUEL,
   [el("trough", [0, 0, 2], [16, 10, 14], "f"),
    el("charge", [1, 10, 3], [15, 12, 13], "charge"),
    el("glow_ridge", [3, 12, 5], [13, 13, 11], "charge")]
   + disc("electrode_w", "x", 2, -1, 1, "gr", 8, c=(8, 6, 8))
   + disc("electrode_e", "x", 2, 15, 17, "gr", 8, c=(8, 6, 8))
   + [el("bus_w", [-1, 8.5, 7.25], [0.5, 11, 8.75], "cu"),
      el("bus_e", [15.5, 8.5, 7.25], [17, 11, 8.75], "cu")])

mk("gs_exchange_tower", FUEL,
   dsc("shell", "y", 6.5, 0, 16, "f", cap="sss")
   + [el("manway", [6, 6, 0.75], [10, 10, 2], "d"),
      el("h2s_line", [13.5, 0, 7], [15.5, 16, 9], "uf6"),
      el("platform", [0, 11, 0], [16, 11.5, 3], "grat")])

mk("vacuum_distillation_column", FUEL,
   dsc("shell", "y", 4.5, 0, 16, "f", cap="sss")
   + disc("flange", "y", 5.2, 7, 8, "ds", 8)
   + [el("vacuum_line", [7, 11, 0.5], [9, 13, 4], "st"),
      el("ladder_rail_n", [1, 0, 6], [1.5, 16, 6.5], "d"),
      el("ladder_rail_s", [1, 0, 9.5], [1.5, 16, 10], "d"),
      el("rung_1", [1, 3, 6.5], [1.5, 3.5, 9.5], "d"),
      el("rung_2", [1, 8, 6.5], [1.5, 8.5, 9.5], "d"),
      el("rung_3", [1, 13, 6.5], [1.5, 13.5, 9.5], "d")])

# =====================================================================
# spent fuel (§2.7)
# =====================================================================
SF = "spent_fuel"

cube("fuel_pool_liner", SF)

rack = [
    el("base_n", [0, 0, 0], [16, 1, 3], "f"),
    el("base_s", [0, 0, 13], [16, 1, 16], "f"),
    el("base_w", [0, 0, 3], [3, 1, 13], "f"),
    el("base_e", [13, 0, 3], [16, 1, 13], "f"),
]
for nm, (x, z) in {"nw": (0, 0), "ne": (14, 0), "sw": (0, 14), "se": (14, 14)}.items():
    rack.append(el(f"post_{nm}", [x, 1, z], [x + 2, 16, z + 2], "f"))
for y in (7, 14.5):
    rack += [
        el(f"tie_n_{y}", [2, y, 0.25], [14, y + 1, 1.25], "f"),
        el(f"tie_s_{y}", [2, y, 14.75], [14, y + 1, 15.75], "f"),
        el(f"tie_w_{y}", [0.25, y, 2], [1.25, y + 1, 14], "f"),
        el(f"tie_e_{y}", [14.75, y, 2], [15.75, y + 1, 14], "f"),
    ]
mk("fuel_pool_rack_open", SF, rack)

mk("fuel_pool_rack_borated", SF, [
    el("wall_n", [0, 0, 0], [16, 16, 1], "f"),
    el("wall_s", [0, 0, 15], [16, 16, 16], "f"),
    el("wall_w", [0, 0, 1], [1, 16, 15], "f"),
    el("wall_e", [15, 0, 1], [16, 16, 15], "f"),
    el("base", [1, 0, 1], [15, 1, 15], "bor"),
    el("lead_in", [1, 15, 1], [15, 15.5, 1.5], "st"),
])

mk("fuel_pool_cooling_port", SF, [
    el("wall", [0, 0, 1], [16, 16, 16], "lin"),
    el("strainer", [2, 2, 0.5], [14, 14, 1], "f", full=["north"]),
    el("bolt_nw", [2.5, 12.5, 0.25], [3.5, 13.5, 0.5], "d"),
    el("bolt_ne", [12.5, 12.5, 0.25], [13.5, 13.5, 0.5], "d"),
    el("bolt_sw", [2.5, 2.5, 0.25], [3.5, 3.5, 0.5], "d"),
    el("bolt_se", [12.5, 2.5, 0.25], [13.5, 3.5, 0.5], "d"),
], extra={"lin": "block/fuel_pool_liner.png"})

mk("pool_heat_exchanger", SF,
   [el("saddle_w", [2, 0, 3], [5, 4, 13], "d"),
    el("saddle_e", [11, 0, 3], [14, 4, 13], "d")]
   + dsc("shell", "x", 5, 1.5, 13, "f", cap="sm")
   + disc("end_cap", "x", 4.5, 1, 1.5, "ds", 8)
   + disc("channel_head", "x", 5.5, 13, 15.5, "sm")
   + [el("cover", [15.5, 3.5, 3.5], [16, 12.5, 12.5], "d", faces={"east": "end"}, full=["east"]),
      el("shell_nozzle", [7, 13, 7], [9, 15.5, 9], "wpipe"),
      el("channel_nozzle", [13.5, 7, 0.5], [15, 9, 2.5], "wpipe"),
      el("channel_flange", [13.25, 6.5, 0], [15.25, 9.5, 0.5], "d")])

mk("pool_cleanup_filter", SF,
   [el("plinth", [0, 0, 0], [16, 3, 16], "con")]
   + dsc("vessel", "y", 5.5, 3, 14, "f", cap="sss")
   + disc("head", "y", 4.5, 14, 15, "ds", 8)
   + [el("sign", [6, 7, 2.2], [10, 11, 2.45], "tre", full=["north"]),
      el("inlet", [7, 10, 0], [9, 12, 3], "wpipe"),
      el("outlet", [13, 5, 7], [16, 7, 9], "wpipe"),
      el("vent", [7.5, 15, 7.5], [8.5, 16, 8.5], "st")])

mk("pool_gate", SF, [
    el("gate", [0, 0, 6], [16, 16, 10], "f"),
    el("guide_wn", [0, 0, 5], [1, 16, 6], "d"),
    el("guide_ws", [0, 0, 10], [1, 16, 11], "d"),
    el("guide_en", [15, 0, 5], [16, 16, 6], "d"),
    el("guide_es", [15, 0, 10], [16, 16, 11], "d"),
    el("lug_w", [3, 16, 7], [5, 17.5, 9], "d"),
    el("lug_e", [11, 16, 7], [13, 17.5, 9], "d"),
])

mk("pool_boron_sampler", SF, [
    el("back", [1, 1, 15], [15, 15, 16], "d"),
    el("cabinet", [2, 2, 8], [14, 14, 15], "cab"),
    el("front", [2.5, 2.5, 7.5], [13.5, 13.5, 8], "f", full=["north"]),
    el("glow_display", [4.5, 10, 7.25], [11.5, 12, 7.5], "lr"),
    el("sample_in", [11, 0, 10], [12, 2, 11], "wpipe"),
    el("sample_out", [4, 0, 10], [5, 2, 11], "wpipe"),
])

mk("dry_cask", SF, [el("segment", [0, 0, 0], [16, 16, 16], "f", faces={"up": "cs", "down": "cs"})])

mk("cask_canister", SF,
   dsc("shell", "y", 7, 0, 14.5, "f", cap="sss")
   + disc("lid", "y", 6.5, 14.5, 15.5, "sss")
   + [el("lift_lug", [6.5, 15.5, 7], [9.5, 16, 9], "st")])

mk("cask_overpack", SF,
   disc("body", "y", 7.8, 0, 15, "f")
   + disc("lid", "y", 7.2, 15, 16, "ds")
   + [el("vent_in", [5.5, 1, 0], [10.5, 3, 1], "grat"),
      el("vent_out", [5.5, 12, 0], [10.5, 14, 1], "grat")])

mk("transport_cask", SF,
   [el("cradle", [3, 0, 4], [13, 2, 12], "d")]
   + disc("body", "x", 5.5, 2.5, 13.5, "f")
   + disc("limiter_w", "x", 7.5, 0, 2.5, "il")
   + disc("limiter_e", "x", 7.5, 13.5, 16, "il")
   + [el("trunnion_n", [7, 7, 1.5], [9, 9, 3], "st"),
      el("trunnion_s", [7, 7, 13], [9, 9, 14.5], "st")])

# =====================================================================
# radiochemistry (§2.8)
# =====================================================================
RC = "radiochemistry"

mk("hot_cell", RC, [
    el("wall", [0, 0, 0], [16, 16, 16], "f"),
    el("sign", [5.5, 9, -0.25], [10.5, 14, 0], "tre", full=["north"]),
])

mk("hot_cell_window", RC, [
    el("wall_top", [0, 13, 0], [16, 16, 16], "hc"),
    el("wall_bottom", [0, 0, 0], [16, 3, 16], "hc"),
    el("wall_w", [0, 3, 0], [3, 13, 16], "hc"),
    el("wall_e", [13, 3, 0], [16, 13, 16], "hc"),
    el("glass", [3, 3, 2], [13, 13, 14], "lgl"),
    el("frame_t", [2.5, 13, -0.5], [13.5, 13.5, 0], "f"),
    el("frame_b", [2.5, 2.5, -0.5], [13.5, 3, 0], "f"),
    el("frame_w", [2.5, 3, -0.5], [3, 13, 0], "f"),
    el("frame_e", [13, 3, -0.5], [13.5, 13, 0], "f"),
], extra={"hc": "block/hot_cell.png"})

mk("master_slave_manipulator", RC, [
    el("wall_plug", [2, 8, 6], [14, 16, 10], "hc"),
    el("through_tube", [5, 10, 2], [11, 15, 14], "f", full=SIDES),
    el("counterweight", [6, 15, 6], [10, 16, 10], "lead"),
    el("master_arm", [7, 3, 2.5], [9, 10, 4], "st"),
    el("master_handle", [6.5, 1, 1], [9.5, 3, 3.5], "r"),
    el("master_trigger", [7.5, 1.5, 0.5], [8.5, 2.5, 1], "red"),
    el("slave_arm", [7, 2, 12], [9, 10, 13.5], "st"),
    el("tong_w", [6.5, 0.5, 11.75], [7.5, 2, 13.75], "d"),
    el("tong_e", [8.5, 0.5, 11.75], [9.5, 2, 13.75], "d"),
])

mk("shearing_machine", RC, [
    el("base", [0, 0, 0], [16, 3, 16], "d"),
    el("cheek_w", [0, 3, 2], [3, 13, 14], "f", full=["north", "west"]),
    el("cheek_e", [13, 3, 2], [16, 13, 14], "f", full=["north", "east"]),
    el("crown", [0, 13, 2], [16, 16, 14], "f", full=["north", "south"]),
    el("magazine", [4, 3, 9], [12, 7, 16], "lead"),
    el("gag", [5, 7, 9], [11, 9, 12], "d"),
    el("discharge_chute", [5, 3, 0], [11, 4, 4], "ss"),
    el("hydraulic", [6, 16, 6], [10, 17.5, 10], "st"),
])
mk("shearing_machine/blade", RC, [
    el("blade", [3.5, 7.5, 5], [12.5, 12.5, 7], "st"),
    el("edge", [3.5, 7, 5.5], [12.5, 7.5, 6.5], "ds"),
], kind="partial")

mk("offgas_treatment", RC,
   [el("base", [0, 0, 0], [16, 1, 16], "d")]
   + dsc("scrubber", "y", 3.5, 1, 16, "f", cap="sss", c=(5, 8, 8))
   + [el("blower", [10, 1, 4], [15, 6, 12], "mot"),
      el("duct_up", [11.5, 6, 7], [13.5, 12.2, 9], "apipe"),
      el("duct_in", [8, 10, 7], [13.5, 12, 9], "apipe"),
      el("stack", [4.25, 16, 7.25], [5.75, 17.5, 8.75], "st")])

mk("iodine_filter_agz", RC, [
    el("plinth", [1, 0, 2], [15, 1, 14], "d"),
    el("housing", [2, 1, 3], [14, 14, 13], "gp", faces={"north": "f"}, full=["north"]),
    el("duct_w", [0, 8, 6], [2, 12, 10], "uf6", faces={"west": "end"}, full=["west"]),
    el("duct_e", [14, 8, 6], [16, 12, 10], "uf6", faces={"east": "end"}, full=["east"]),
    el("cover", [3, 14, 4], [13, 15, 12], "d"),
    el("sample_port", [11, 15, 6], [12, 16, 7], "b"),
])

mk("clarifier", RC, [
    el("base", [0, 0, 0], [16, 3, 16], "d"),
    el("wall_n", [2, 3, 2], [14, 11, 3.5], "f", full=["north"]),
    el("wall_s", [2, 3, 12.5], [14, 11, 14], "f"),
    el("wall_w", [2, 3, 3.5], [3.5, 11, 12.5], "f"),
    el("wall_e", [12.5, 3, 3.5], [14, 11, 12.5], "f"),
    el("belt_guard", [14, 3, 5], [16, 9, 11], "mot"),
])
mk("clarifier/bowl", RC,
   disc("bowl", "y", 4, 4, 10, "st") + disc("spindle", "y", 1, 10, 13, "ds", 8)
   + disc("nut", "y", 1.5, 12, 12.75, "b", 8), kind="partial")

mk("oxalate_precipitator", RC,
   [el("base", [2, 0, 2], [14, 2, 14], "d")]
   + dsc("vessel", "y", 3.5, 2, 11.5, "f", cap="sss")
   + disc("lid", "y", 3, 11.5, 12, "ds", 8)
   + [el("post_w", [2.5, 2, 7.5], [3.5, 15.5, 8.5], "d"),
      el("post_e", [12.5, 2, 7.5], [13.5, 15.5, 8.5], "d"),
      el("bracket", [3.5, 14.5, 7.5], [12.5, 15.5, 8.5], "d"),
      el("motor", [6, 15.5, 6], [10, 17.5, 10], "mot"),
      el("filtrate", [7, 3, 1], [9, 5, 4.6], "apipe")])
mk("oxalate_precipitator/agitator", RC,
   [el("shaft", [7.5, 3, 7.5], [8.5, 15.5, 8.5], "st"),
    el("paddle_x", [5, 4, 7.75], [11, 6, 8.25], "st"),
    el("paddle_z", [7.75, 4, 5], [8.25, 6, 11], "st")]
   + disc("coupling", "y", 1.25, 12.5, 13.5, "bs", 8), kind="partial")

mk("oxalic_acid_reactor", RC,
   [el(f"leg_{i}", [x, 0, z], [x + 1.5, 4, z + 1.5], "d") for i, (x, z) in enumerate(((2.5, 2.5), (12, 2.5), (2.5, 12), (12, 12)))]
   + dsc("vessel", "y", 6, 3, 13, "f", cap="en")
   + disc("head", "y", 5, 13, 13.75, "en")
   + [el("lantern_a", [6, 13.75, 6], [7, 15, 7], "d"),
      el("lantern_b", [9, 13.75, 9], [10, 15, 10], "d"),
      el("motor", [6, 15, 6], [10, 17.5, 10], "mot"),
      el("sugar_hopper", [11, 13, 3], [13.5, 15.5, 5.5], "ss"),
      el("hno3_in", [0.5, 9, 7], [3, 11, 9], "apipe"),
      el("nox_out", [11, 13.75, 10], [12, 16, 11], "apipe")])
mk("oxalic_acid_reactor/agitator", RC,
   [el("shaft", [7.5, 4, 7.5], [8.5, 15, 8.5], "st"),
    el("blade_x", [4, 5, 7.5], [12, 6.5, 8.5], "st"),
    el("blade_z", [7.5, 5, 4], [8.5, 6.5, 12], "st")]
   + disc("coupling", "y", 1.5, 13.9, 14.9, "bs", 8), kind="partial")

mk("annular_tank", RC,
   ring_xz("ring", 7.5, 3, 0, 16, "f")
   + [el("nozzle_in", [1.5, 16, 7], [2.5, 17.5, 9], "apipe"),
      el("nozzle_out", [13.5, 16, 7], [14.5, 17.5, 9], "apipe")])

mk("slab_tank", RC, [
    el("slab", [0, 1, 5], [16, 16, 11], "f"),
    el("rib_nw", [3, 1, 4.5], [4, 16, 5], "ss"),
    el("rib_ne", [12, 1, 4.5], [13, 16, 5], "ss"),
    el("rib_sw", [3, 1, 11], [4, 16, 11.5], "ss"),
    el("rib_se", [12, 1, 11], [13, 16, 11.5], "ss"),
    el("foot_w", [1, 0, 3], [3, 1, 13], "d"),
    el("foot_e", [13, 0, 3], [15, 1, 13], "d"),
    el("nozzle", [7, 16, 7], [9, 17.5, 9], "apipe"),
])

cube("raschig_rings", RC)

mk("fissile_batch_counter", RC, [
    el("pedestal", [3, 0, 4], [13, 6, 14], "cab"),
    el("console", [1, 6, 2], [15, 14, 12], "cab"),
    el("glow_panel", [1.5, 6.5, 1.5], [14.5, 13.5, 2], "f", full=["north"]),
    el("shelf", [2, 6, 0], [14, 7, 2], "d"),
])

mk("criticality_alarm", RC, [
    el("back", [1, 1, 15], [15, 15, 16], "d"),
    el("body", [2, 2, 10], [14, 12, 15], "red", faces={"north": "f"}, full=["north"]),
    el("glow_beacon", [6, 12, 11], [10, 15, 14], "lr"),
    el("beacon_cap", [5.5, 15, 10.5], [10.5, 15.5, 14.5], "d"),
    el("siren", [14, 6, 11], [15.5, 10, 14], "d"),
])

gb = [el(f"leg_{i}", [x, 0, z], [x + 1.5, 6, z + 1.5], "d")
      for i, (x, z) in enumerate(((1, 3), (13.5, 3), (1, 13), (13.5, 13)))]
gb += [
    el("box", [0, 6, 2], [16, 15, 16], "ss", faces={"north": "f"}, full=["north"]),
    el("sleeve_w", [3.5, 8.75, 0.5], [5.5, 10.75, 2], "r"),
    el("sleeve_e", [10.5, 8.75, 0.5], [12.5, 10.75, 2], "r"),
    el("hepa", [10, 15, 6], [14, 16.5, 12], "d"),
]
mk("glovebox", RC, gb)

mk("hlw_tank", RC,
   [el("plinth", [0, 0, 0], [16, 1, 16], "con")]
   + dsc("tank", "y", 7, 1, 14, "f", cap="sss")
   + disc("head", "y", 6, 14, 14.5, "sss")
   + [el("sign", [6, 6, 0.7], [10, 10, 0.95], "tre", full=["north"]),
      el("lantern_a", [6, 14.5, 6], [7, 15.5, 7], "d"),
      el("lantern_b", [9, 14.5, 9], [10, 15.5, 10], "d"),
      el("motor", [6, 15.5, 6], [10, 17.5, 10], "mot"),
      el("coil_in", [4, 11, 0.25], [6, 13, 3], "wpipe"),
      el("coil_out", [10, 4, 0.25], [12, 6, 3], "wpipe"),
      el("purge", [11, 14.5, 3], [13, 16, 5], "air")])
mk("hlw_tank/agitator", RC,
   [el("shaft", [7.5, 2, 7.5], [8.5, 15.5, 8.5], "st"),
    el("blade_x", [3, 3, 7.5], [13, 4.5, 8.5], "st"),
    el("blade_z", [7.5, 3, 3], [8.5, 4.5, 13], "st")]
   + disc("coupling", "y", 1.5, 14.6, 15.4, "bs", 8), kind="partial")

mk("hlw_tank_purge", RC, [
    el("body", [1, 0, 3], [15, 12, 15], "p"),
    el("front", [1.5, 0.5, 2.5], [14.5, 11.5, 3], "f", full=["north"]),
    el("duct_up", [6, 12, 6], [10, 16, 10], "air"),
    el("duct_out", [10, 13, 7], [16, 15, 9], "air", faces={"east": "end"}, full=["east"]),
])

mk("solvent_wash", RC, [
    el("plinth", [0, 0, 1], [16, 1, 15], "d"),
    el("box", [0, 1, 2], [16, 12, 14], "ss"),
    el("front", [0.5, 1.5, 1.5], [15.5, 11.5, 2], "f", full=["north"]),
    el("mixer_motor", [2, 12, 5], [6, 15, 9], "mot"),
    el("carbonate_line", [9, 12, 7], [16, 14, 9], "alk"),
])

# =====================================================================
# waste (§2.9)
# =====================================================================
WS = "waste"

mk("waste_compactor", WS,
   [el("base", [0, 0, 0], [16, 3, 16], "d")]
   + disc("drum", "y", 4, 3, 9, "drum")
   + [el("column_w", [0, 3, 6], [2, 13, 10], "yp"),
      el("column_e", [14, 3, 6], [16, 13, 10], "yp"),
      el("crown", [0, 13, 3], [16, 16, 13], "yp", faces={"north": "f", "south": "f"}, full=["north", "south"]),
      el("cylinder", [6, 16, 6], [10, 17.5, 10], "st")])
mk("waste_compactor/ram", WS, [
    el("platen", [3, 10, 3], [13, 12, 13], "d"),
    el("rod", [7, 12, 7], [9, 13, 9], "st"),
], kind="partial")

mk("cementation_mixer", WS,
   [el("base", [0, 0, 0], [16, 2, 16], "d")]
   + disc("drum", "y", 5.5, 2, 11, "drum")
   + [el("post_w", [0.5, 2, 6], [2.5, 14, 10], "f"),
      el("post_e", [13.5, 2, 6], [15.5, 14, 10], "f"),
      el("beam", [0.5, 14, 5], [15.5, 16, 11], "f", full=["north", "south"]),
      el("mixer_head", [5, 12, 5], [11, 14, 11], "mot")])
mk("cementation_mixer/paddle", WS, [
    el("shaft", [7.5, 3, 7.5], [8.5, 12, 8.5], "st"),
    el("paddle_x", [3.5, 4, 7.5], [12.5, 7, 8.5], "st"),
    el("paddle_z", [7.5, 4, 3.5], [8.5, 7, 12.5], "st"),
], kind="partial")

mk("cement_kiln", WS,
   [el("base", [0, 0, 1], [16, 2, 15], "con"),
    el("support", [6, 2, 3], [10, 4, 13], "d")]
   + dsc("shell", "x", 6, 0, 16, "f", cap="ref", c=(8, 9, 8))
   + disc("tyre", "x", 6.7, 6.5, 9.5, "ds", c=(8, 9, 8)))

mk("bitumenizer", WS,
   [el("base", [0, 0, 2], [16, 2, 14], "d"),
    el("gearbox", [0, 2, 4], [3, 10, 12], "mot")]
   + dsc("barrel", "x", 3.5, 3, 14, "f", cap="ds", c=(8, 7, 8))
   + [el("hopper", [5, 10, 6], [9, 14, 10], "p"),
      el("spout", [14, 4, 7], [15.5, 7, 9], "d"),
      el("glow_drip", [14.25, 2, 7.75], [15.25, 4, 8.25], "bit"),
      el("steam_line", [3, 11, 3], [14, 12, 4], "red")])

mk("vitrification_melter", WS, [
    el("leg_nw", [0, 0, 1], [2, 6, 3], "d"),
    el("leg_ne", [14, 0, 1], [16, 6, 3], "d"),
    el("leg_sw", [0, 0, 13], [2, 6, 15], "d"),
    el("leg_se", [14, 0, 13], [16, 6, 15], "d"),
    el("melter", [0, 6, 1], [16, 16, 15], "p", faces={"up": "ref"}),
    el("front", [1, 7, 0.5], [15, 15, 1], "f", full=["north"]),
    el("glow_view", [6, 10.5, 0.25], [10, 12.5, 0.5], "hot"),
    el("spout", [7, 5, 7], [9, 6, 9], "d"),
    el("glow_stream", [7.6, 4.5, 7.6], [8.4, 5, 8.4], "hot"),
    el("electrode_w", [3, 16, 6], [5, 17.5, 10], "cu"),
    el("electrode_e", [11, 16, 6], [13, 17.5, 10], "cu"),
] + disc("canister", "y", 3, 0, 4.5, "sss"))

mk("repository_controller", WS, [
    el("pedestal", [2, 0, 5], [14, 7, 15], "d"),
    el("desk", [1, 7, 1], [15, 8, 6], "d"),
    el("housing", [1, 8, 6], [15, 16, 15], "p"),
    el("glow_screen", [1.5, 8.5, 5.5], [14.5, 15.5, 6], "f", full=["north"]),
])

cube("bentonite_buffer", WS)
mk("near_surface_vault", WS, [el("block", [0, 0, 0], [16, 16, 16], "f", faces={"up": "cs", "down": "cs"})])

# =====================================================================
# items — every item of section 02 (§2.12)
# =====================================================================
for _id in ITEM_TEX:
    item(_id, f"item/{_id}.png")

# =====================================================================
# fluids / gases (§2.12)
# =====================================================================
for _id, col, kind in (
    ("sulfuric_acid", "#d9cf9e", "liquid"),           # техническая H2SO4: маслянистая, соломенная
    ("pregnant_leach_solution", "#b8c64a", "liquid"), # сульфат уранила: жёлто-зелёный
    ("barren_solution", "#9fae8c", "liquid"),         # обеднённый раствор: бледный серо-зелёный
    ("uranium_tailings", "#7a6e5c", "liquid"),        # пульпа хвостов: серо-бурая
    ("ammonia_solution", "#c8dce6", "liquid"),
    ("ammonia", "#e2e8f0", "gas"),
    ("hydrogen_peroxide", "#cfe3f2", "liquid"),       # слабо-голубоватая
    ("nitric_acid", "#e8dc8c", "liquid"),             # желтоватая от NO2
    ("uranyl_nitrate_solution", "#d4e04a", "liquid"), # жёлто-зелёная
    ("tbp_solvent", "#e8dfb4", "liquid"),             # бесцветно-соломенный
    ("loaded_tbp_solvent", "#e6d250", "liquid"),      # органика с U — жёлтая
    ("amine_solvent", "#d7b977", "liquid"),           # янтарный
    ("diluent_dodecane", "#eeeadb", "liquid"),
    ("hydrofluoric_acid", "#dfe9e6", "liquid"),
    ("hydrogen_fluoride", "#e6eedd", "gas"),
    ("fluorine", "#e3e89a", "gas"),                   # бледно-жёлто-зелёный
    ("hydrogen", "#f0f2f8", "gas"),
    ("oxygen", "#bcd6f0", "gas"),
    ("nitrogen", "#e4e6ec", "gas"),
    ("helium", "#f2eef6", "gas"),
    ("chlorine", "#c8d84a", "gas"),                   # жёлто-зелёный
    ("sulfur_dioxide", "#e8e6dc", "gas"),
    ("hydrogen_sulfide", "#e6e8d0", "gas"),
    ("brine", "#b4c8cc", "liquid"),
    ("sodium_hydroxide_solution", "#dfe6ea", "liquid"),
    ("soda_solution", "#e2e8e4", "liquid"),
    ("boric_acid_solution", "#d6e4ec", "liquid"),
    ("heavy_water", "#9fc6e8", "liquid"),             # как вода
    ("plutonium_nitrate_solution", "#3b5a2c", "liquid"),  # Pu(IV) в HNO3: тёмно-зелёный
    ("uranous_nitrate_solution", "#4f8a46", "liquid"),    # U(IV): зелёный
    ("hydrazine_solution", "#e4eae6", "liquid"),
    ("hlw_liquor", "#4a3423", "liquid"),              # ВАО: тёмно-коричневый
    ("raffinate", "#9c7f4a", "liquid"),               # рафинат: буровато-жёлтый
    ("decon_solution", "#c4a6c8", "liquid"),          # дезактивирующий (KMnO4-следы): бледно-сиреневый
):
    fluid(_id, col, kind)
