"""Plant area models: rest of reactor island (00 §0.7.1), 03a turbine hall / fuel handling /
ventilation / piping, 03b/03c control room, 04b radiation safety, 04a/04c/04d extras."""
from pclib import *

P = lambda n: f"block/common/plant_{n}.png"
B = lambda n: f"block/{n}.png"

# общие ключи текстур; модели берут подмножество через tx()
_TX = {
    "d": T("steel_dark"), "dsm": T("steel_dark_smooth"), "p": T("steel_plate"), "sm": T("steel_smooth"),
    "st": T("steel_bare"), "b": T("brass"), "bsm": T("brass_smooth"), "g": T("graphite"),
    "con": T("concrete"), "pb": T("lead"), "h": T("hazard"), "r": T("rubber"), "red": T("paint_red"),
    "mb": T("motor_blue"), "lr": T("lamp_red"), "ly": T("lamp_yellow"), "lg": T("lamp_green"),
    "ab": T("absorber"), "cab": T("cabinet"), "tre": T("trefoil"), "end": T("pipe_end"),
    "ins": P("insulation"), "inss": P("insulation_smooth"), "gal": P("galvanized"), "cu": P("copper"),
    "cus": P("copper_smooth"), "mon": P("monel"), "pn": P("panel"), "pns": P("panel_smooth"),
    "pnd": P("panel_dark"), "gls": P("glass_dark"), "lw": P("lamp_white"), "lb": P("lamp_blue"),
    "lo": P("lamp_orange"), "loff": P("lamp_off"), "cy": P("crane_yellow"), "cys": P("crane_yellow_smooth"),
    "rope": P("rope"), "spr": P("spring"), "tile": P("tile"), "louv": P("louver"), "bl": P("blades"),
    "cbl": P("cable"), "air": P("air_blue"), "wat": P("water_green"), "stm": P("steam_red"),
    "acid": P("acid_orange"), "rack": P("rack"), "coil": P("glow_coil"), "redsm": P("red_smooth"),
    "pprim": "block/pipe_primary.png", "psteam": "block/pipe_steam.png", "pfeed": "block/pipe_feedwater.png",
}


def tx(*keys, **extra):
    d = {k: _TX[k] for k in keys}
    d.update(extra)
    return d


def ring_xy(name, r, w, z0, z1, tex, cx=8, cy=8):
    """Octagonal handwheel ring in the XY plane (axis Z)."""
    out = []
    for ang in (0, 45):
        o = [cx, cy, (z0 + z1) / 2]
        bars = [
            ([cx - r, cy - r, z0], [cx + r, cy - r + w, z1]),
            ([cx - r, cy + r - w, z0], [cx + r, cy + r, z1]),
            ([cx - r, cy - r + w, z0], [cx - r + w, cy + r - w, z1]),
            ([cx + r - w, cy - r + w, z0], [cx + r, cy + r - w, z1]),
        ]
        for i, (f, t) in enumerate(bars):
            out.append(el(f"{name}_{ang}_{i}", f, t, tex, rot=("z", ang, o) if ang else None))
    return out


def flanges_z(tex="d", endtex="end", r=5, depth=1.5):
    return [
        el("flange_n", [8 - r, 8 - r, 0], [8 + r, 8 + r, depth], tex, faces={"north": endtex}, full=["north"]),
        el("flange_s", [8 - r, 8 - r, 16 - depth], [8 + r, 8 + r, 16], tex, faces={"south": endtex}, full=["south"]),
    ]


def panel_tile():  # тонкая плита щита/пульта, к которой крепятся органы управления
    return el("panel", [0, 0, 13], [16, 16, 16], "pn")


def wall_box(x0, y0, x1, y1, z0, front, body="d"):
    """Wall-mounted box against the south side with a full-texture north face."""
    return [
        el("box", [x0, y0, z0], [x1, y1, 16], body),
        el("front", [x0 + 0.5, y0 + 0.5, z0 - 0.1], [x1 - 0.5, y1 - 0.5, z0], front, full=["north"]),
    ]


# ======================================================================
# core
# ======================================================================
model("moderator_tank", "core", tx("d", "st", "end", wall=B("moderator_tank")), [
    el("shell", [0.5, 0.01, 0.5], [15.5, 15.99, 15.5], "wall", faces={"up": "d", "down": "d"}),
    el("rib_nw", [0, 0, 0], [2, 16, 2], "d"),
    el("rib_ne", [14, 0, 0], [16, 16, 2], "d"),
    el("rib_sw", [0, 0, 14], [2, 16, 16], "d"),
    el("rib_se", [14, 0, 14], [16, 16, 16], "d"),
    el("nozzle", [4.5, 9, -1], [10.5, 15, 1], "d", faces={"north": "end"}, full=["north"]),
    el("top_flange", [3, 15.5, 3], [13, 16.5, 13], "st"),
], "block/moderator_tank")

model("control_rod_channel", "core", tx("g", "st", "ab", top=B("control_rod_channel_top")), [
    el("graphite", [0, 0, 0], [16, 16, 16], "g", faces={"up": "top"}),
    el("collar", [4.5, 16, 4.5], [11.5, 16.5, 11.5], "st"),
    el("rod_head", [6.5, 16.5, 6.5], [9.5, 17.5, 9.5], "ab"),
], "block/control_rod_channel")

model("startup_source", "core", tx("d", "st", "h", cap=B("startup_source_capsule")), [
    el("base", [3, 0, 3], [13, 2, 13], "d"),
    el("hazard_rim", [2.9, 1.5, 2.9], [13.1, 2.25, 13.1], "h"),
    el("holder", [6, 2, 6], [10, 12, 10], "cap", full=["north", "south", "east", "west"]),
    el("cap", [5.5, 12, 5.5], [10.5, 13, 10.5], "d"),
    el("bail_w", [6.5, 13, 7.5], [7.5, 15.5, 8.5], "st"),
    el("bail_e", [8.5, 13, 7.5], [9.5, 15.5, 8.5], "st"),
    el("bail_top", [6.5, 15.5, 7.5], [9.5, 16, 8.5], "st"),
], "block/startup_source")

# ---------- vessels of the reactor island ----------
model("pressurizer", "core", tx("d", "st", "end", "psteam", "inss", sh=B("pressurizer_shell"), top=B("tank_top"), ht=B("pressurizer_heaters")),
      disc("shell", "y", 7.5, 0, 12.5, "sh", faces={"up": "top", "down": "ht"})
      + disc("head", "y", 5.5, 12.5, 14.5, "inss", 8, faces={"up": "top"})
      + [el("spray_nozzle", [6, 14.5, 6], [10, 16, 10], "d", faces={"up": "end"}, full=["up"]),
         el("relief_line", [12, 13, 7], [16, 15, 9], "psteam", faces={"east": "end"}),
         el("lug_w", [-0.5, 4, 6.5], [1.5, 7, 9.5], "d"),
         el("lug_e", [14.5, 4, 6.5], [16.5, 7, 9.5], "d")],
      "block/pressurizer")

model("steam_generator", "core", tx("d", "pprim", "end", "inss", sh=B("steam_generator_shell"), top=B("tank_top"), col=B("steam_generator_collector")),
      disc("shell", "x", 7.5, 0, 16, "sh", faces={"east": "top", "west": "top"})
      + [el("collector", [4.5, 12, 4.5], [11.5, 16.5, 11.5], "pprim", faces={"up": "col"}, full=["up"]),
         el("saddle_w", [2, -0.5, 1.5], [4, 3, 14.5], "d"),
         el("saddle_e", [12, -0.5, 1.5], [14, 3, 14.5], "d"),
         el("feed_nozzle", [6, 3, -0.5], [10, 7, 1.5], "d", faces={"north": "end"}, full=["north"])],
      "block/steam_generator")

model("steam_drum", "core", tx("d", "stm", "wat", "end", "inss", sh=B("steam_drum_shell"), top=B("tank_top")),
      disc("shell", "x", 6.5, 0, 16, "sh", faces={"east": "top", "west": "top"})
      + [el("riser_w", [2.5, 13.5, 6.5], [5.5, 16.5, 9.5], "stm", faces={"up": "end"}, full=["up"]),
         el("riser_e", [10.5, 13.5, 6.5], [13.5, 16.5, 9.5], "stm", faces={"up": "end"}, full=["up"]),
         el("downcomer", [6.5, -0.5, 6.5], [9.5, 2.5, 9.5], "wat", faces={"down": "end"}, full=["down"]),
         el("saddle_w", [1, 0, 2.5], [3, 3, 13.5], "d"),
         el("saddle_e", [13, 0, 2.5], [15, 3, 13.5], "d")],
      "block/steam_drum")

# ======================================================================
# instruments (в стиле instrument_pressure: 32×32 подложка, без делений)
# ======================================================================
model("instrument_temperature", "instruments", tx("d", "st", "b", dial=B("instrument_temperature_dial"), cu=P("copper_smooth")),
      [el("mount_plate", [4, 2, 15], [12, 14, 16], "d")]
      + disc("case", "z", 6, 11, 14.5, "cu", faces={"north": "dial"})
      + [el("nut", [6.5, 6.5, 14.4], [9.5, 9.5, 15.2], "b"),
         el("thermowell", [7.25, 7.25, 15.1], [8.75, 8.75, 16.5], "st")],
      "block/instrument_temperature")

model("instrument_flow", "instruments", tx("d", "st", "b", dial=B("instrument_flow_dial")), [
    el("mount_plate", [2, 1, 15], [14, 15, 16], "d"),
    el("case", [3, 6, 11], [13, 15, 15], "d"),
    el("dial_face", [3.5, 6.5, 10.9], [12.5, 14.5, 11], "dial", full=["north"]),
    el("manifold", [4, 2.5, 11.5], [12, 5.5, 14.5], "b"),
    el("valve_hp", [4.5, 3, 10.5], [5.5, 5, 11.5], "st"),
    el("valve_eq", [7.5, 3, 10.5], [8.5, 5, 11.5], "st"),
    el("valve_lp", [10.5, 3, 10.5], [11.5, 5, 11.5], "st"),
    el("impulse_hp", [5, 0, 12.5], [6, 2.5, 13.5], "st"),
    el("impulse_lp", [10, 0, 12.5], [11, 2.5, 13.5], "st"),
], "block/instrument_flow")

model("instrument_level", "instruments", tx("d", "st", "b", dial=B("instrument_level_dial")), [
    el("mount_plate", [4, 0, 15], [12, 16, 16], "d"),
    el("case", [5, 1, 11], [11, 15, 15], "d"),
    el("scale", [5.5, 1.5, 10.9], [10.5, 14.5, 11], "dial"),  # позиционная выборка: окно шкалы
    el("tap_top", [7.25, 15, 12.25], [8.75, 16.5, 13.75], "b"),
    el("tap_bottom", [7.25, -0.5, 12.25], [8.75, 1, 13.75], "b"),
], "block/instrument_level")

model("instrument_dose_rate", "instruments", tx("d", "st", dial=B("instrument_dose_rate_dial"), body=B("instrument_dose_rate_body"), ch=B("neutron_detector_body")),
      [el("mount_plate", [2, 0, 15], [14, 15, 16], "d"),
       el("case", [3, 5, 11], [13, 15, 15], "body", faces={"north": "dial"}, full=["north"])]
      + disc("chamber", "y", 2.5, 0.5, 5, "ch", c=(8, 8, 13))
      + [el("chamber_cap", [6.5, 0, 11.5], [9.5, 0.5, 14.5], "d")],
      "block/instrument_dose_rate")

model("instrument_boron", "instruments", tx("d", "acid", body=B("instrument_boron_body"), dial=B("instrument_boron_dial")), [
    el("cabinet", [2, 0, 7], [14, 16, 16], "body"),
    el("face", [3.5, 5, 6.9], [12.5, 14, 7], "dial", full=["north"]),
    el("plinth", [1.5, 0, 6.5], [14.5, 1, 15.9], "d"),
    el("sample_in", [14, 2, 10], [16, 3, 11], "acid"),
    el("sample_out", [14, 4, 12], [16, 5, 13], "acid"),
], "block/instrument_boron")

model("instrument_vibration", "instruments", tx("d", "st", "cbl", body=B("instrument_vibration_body")),
      [el("pad", [5, 5, 15], [11, 11, 16], "d")]
      + disc("sensor", "z", 2.25, 11, 15, "body", 8)
      + [el("connector", [7.25, 4.5, 12], [8.75, 5.75, 13.5], "st"),
         el("cable", [7.5, 0, 12.25], [8.5, 4.5, 13.25], "cbl")],
      "block/instrument_vibration")

model("instrument_acoustic", "instruments", tx("d", "st", "cbl", body=B("instrument_acoustic_body")),
      [el("clamp", [3, 7, 14], [13, 9, 15.9], "st")]
      + disc("transducer", "z", 3, 12, 16, "body", 8)
      + [el("preamp", [6, 11, 12], [10, 14, 15], "d"),
         el("cable", [7.5, 14, 13], [8.5, 16, 14], "cbl")],
      "block/instrument_acoustic")

model("neutron_detector", "instruments", tx("d", "b", "cbl", body=B("neutron_detector_body")),
      disc("chamber", "y", 3.5, 0, 13, "body")
      + [el("head", [5, 13, 5], [11, 15, 11], "d"),
         el("connector", [7, 15, 7], [9, 16, 9], "b"),
         el("cable", [7.5, 15.5, 9], [8.5, 16.5, 15], "cbl")],
      "block/neutron_detector")

# ======================================================================
# piping
# ======================================================================
model("pipe_large", "piping", {"body": B("pipe_large"), "bt": B("pipe_large_t"), "d": T("steel_dark"), "end": B("pipe_large_end")}, [
    el("body", [1, 1, 1.5], [15, 15, 14.5], "body", faces={"up": "bt", "down": "bt"}),
    el("flange_n", [0, 0, 0], [16, 16, 1.5], "d", faces={"north": "end"}, full=["north"]),
    el("flange_s", [0, 0, 14.5], [16, 16, 16], "d", faces={"south": "end"}, full=["south"]),
], "block/pipe_large")


def through_pipe(tex="pprim"):
    return [el("pipe", [4, 4, 1.5], [12, 12, 14.5], tex)] + flanges_z()


model("valve_check", "piping", tx("pprim", "end", "d", "p", "st", arrow=B("valve_check_side")),
      through_pipe()
      + [el("body", [3, 3, 4], [13, 12.5, 12], "p", faces={"east": "arrow", "west": "arrow"}, full=["east", "west"]),
         el("cover", [4, 12, 5], [12, 13.5, 11], "d"),
         el("cover_boss", [6, 13.5, 6.5], [10, 14.5, 9.5], "d"),
         el("hinge_w", [2, 9, 6.5], [3, 11, 9.5], "st"),
         el("hinge_e", [13, 9, 6.5], [14, 11, 9.5], "st")],
      "block/valve_check")

model("valve_relief", "piping", tx("d", "p", "st", "end", "psteam", "spr", "red"), [
    el("inlet_flange", [3, 0, 3], [13, 1.5, 13], "d", faces={"down": "end"}, full=["down"]),
    el("body", [4, 1.5, 4], [12, 8, 12], "p"),
    el("outlet", [5, 2, 1.5], [11, 7, 4], "psteam"),
    el("outlet_flange", [4, 1, 0], [12, 8, 1.5], "d", faces={"north": "end"}, full=["north"]),
    el("bonnet", [5.5, 8, 5.5], [10.5, 9, 10.5], "d"),
    el("post_w", [5.5, 9, 7], [6.5, 14, 9], "d"),
    el("post_e", [9.5, 9, 7], [10.5, 14, 9], "d"),
] + disc("spring", "y", 2, 9, 14, "spr", 8) + [
    el("cap", [6, 14, 6], [10, 16, 10], "red"),
    el("lever", [8, 14.5, 7.5], [15, 15.5, 8.5], "d"),
], "block/valve_relief")

model("valve_control", "piping", tx("pprim", "end", "d", "p", "st", "air", pos=B("valve_control_positioner")),
      through_pipe()
      + [el("body", [3.5, 3.5, 4], [12.5, 12.5, 12], "p"),
         el("bonnet", [5.5, 12.5, 5.5], [10.5, 13.5, 10.5], "d"),
         el("yoke_w", [5.5, 13.5, 7.5], [6.5, 14.5, 8.5], "d"),
         el("yoke_e", [9.5, 13.5, 7.5], [10.5, 14.5, 8.5], "d")]
      + disc("diaphragm", "y", 4.5, 14.5, 17.5, "air")
      + [el("air_fitting", [7.25, 17.5, 7.25], [8.75, 18, 8.75], "st"),
         el("positioner", [12.5, 9, 5], [14.5, 13, 9], "pos", full=["east"])],
      "block/valve_control")
model("valve_control/stem", "piping", tx("st", "red"), [
    el("stem", [7.5, 12.5, 7.5], [8.5, 14.5, 8.5], "st"),
    el("indicator", [8.5, 13.25, 7.75], [9.75, 13.75, 8.25], "red"),
], "block/valve_control/partial/stem", kind="partial")

for _id, _tex, _fl in (("chem_pipe", B("chem_pipe"), T("steel_bare")), ("chem_pipe_monel", B("chem_pipe_monel"), P("monel"))):
    model(_id, "piping", {"body": _tex, "bt": _tex[:-4] + "_t.png", "fl": _fl, "end": T("pipe_end"), "d": T("steel_dark")}, [
        el("body", [5, 5, 1.5], [11, 11, 14.5], "body", faces={"up": "bt", "down": "bt"}),
        el("flange_n", [4, 4, 0], [12, 12, 1.5], "fl", faces={"north": "end"}, full=["north"]),
        el("flange_s", [4, 4, 14.5], [12, 12, 16], "fl", faces={"south": "end"}, full=["south"]),
        el("bolt_n", [3.5, 7.5, 0.25], [12.5, 8.5, 1.25], "d"),
        el("bolt_s", [3.5, 7.5, 14.75], [12.5, 8.5, 15.75], "d"),
    ], f"block/{_id}")


def adapter(prv):
    els = [
        el("our_flange", [3, 3, 14.5], [13, 13, 16], "d", faces={"south": "end"}, full=["south"]),
        el("body", [3.5, 3.5, 5], [12.5, 12.5, 14.5], "body"),
        el("create_stub", [5, 5, 1], [11, 11, 5], "cus"),
        el("create_rim", [4, 4, 0], [12, 12, 1], "cu", faces={"north": "end"}, full=["north"]),
    ]
    if prv:
        els += [el("prv_body", [6, 12.5, 8], [10, 14, 12], "red"),
                el("prv_cap", [6.5, 14, 8.5], [9.5, 15.5, 11.5], "red"),
                el("prv_lever", [9.5, 14.5, 9.5], [12, 15, 10.5], "d")]
    else:
        els += [el("cut_stub", [7, 12.5, 9], [9, 13.5, 11], "st"),
                el("cut_end", [7.25, 13.5, 9.25], [8.75, 13.6, 10.75], "h")]
    return els


_ad = tx("d", "end", "cu", "cus", "red", "st", "h")
model("pipe_adapter_create", "piping", dict(_ad, body=B("pipe_adapter_create_body")), adapter(True), "block/pipe_adapter_create")
model("pipe_adapter_create_raw", "piping", dict(_ad, body=B("pipe_adapter_create_raw_body")), adapter(False),
      "block/pipe_adapter_create_raw")

# ======================================================================
# turbine hall (ось ротора — X)
# ======================================================================
model("turbine_casing", "turbine", tx("d", tc=B("turbine_casing"), tcs=B("turbine_casing_smooth")), [
    el("foot", [0, 0, 1], [16, 1, 15], "d"),
    el("lower", [0, 1, 2], [16, 7, 14], "tc"),
    el("joint_flange", [0, 7, 1], [16, 9, 15], "d"),
    el("upper", [0, 9, 3], [16, 12.5, 13], "tc"),
    el("crown", [0, 12.5, 5], [16, 14.5, 11], "tcs"),
    el("crown_rib", [7, 14.5, 6], [9, 15.5, 10], "d"),
], "block/turbine_casing")

model("turbine_rotor", "turbine", tx("d", "b", tc=B("turbine_casing")), [
    el("pedestal", [2, 0, 3], [14, 5, 13], "tc"),
    el("bearing_lower", [4, 5, 4], [12, 7.5, 12], "d"),
    el("bearing_cap", [4.5, 8.5, 4.5], [11.5, 11, 11.5], "d"),
    el("cap_bolt_n", [5, 7.5, 3.5], [11, 8.5, 4.5], "b"),
    el("cap_bolt_s", [5, 7.5, 11.5], [11, 8.5, 12.5], "b"),
    el("oil_line", [12, 1, 7], [16, 2, 9], "b"),
], "block/turbine_rotor")
model("turbine_rotor/stage_hp", "turbine", tx("st", "bl"),
      disc("shaft", "x", 1.5, -0.5, 16.5, "st", 8) + disc("hub", "x", 3, 4, 12, "st")
      + disc("blades_1", "x", 5, 5, 7, "bl") + disc("blades_2", "x", 5.5, 9, 11, "bl"),
      "block/turbine_rotor/partial/stage_hp", kind="partial")
model("turbine_rotor/stage_lp", "turbine", tx("st", "bl"),
      disc("shaft", "x", 1.5, -0.5, 16.5, "st", 8) + disc("hub", "x", 3.5, 3, 13, "st")
      + disc("blades", "x", 7.5, 6, 10, "bl"),
      "block/turbine_rotor/partial/stage_lp", kind="partial")

model("turbine_governor", "turbine", tx("d", "st", "b", tcs=B("turbine_casing_smooth"), front=B("turbine_governor_front")), [
    el("plinth", [1, 0, 1], [15, 1, 15], "d"),
    el("pedestal", [2, 1, 2], [14, 12, 14], "tcs"),
    el("front", [2.5, 1.5, 1.9], [13.5, 11.5, 2], "front", full=["north"]),
    el("top", [3, 12, 3], [13, 13, 13], "d"),
    el("spindle", [7.5, 13, 7.5], [8.5, 16, 8.5], "st"),
    el("ball_w", [4.5, 13.5, 7], [6.5, 15.5, 9], "b"),
    el("ball_e", [9.5, 13.5, 7], [11.5, 15.5, 9], "b"),
    el("arm_w", [5.5, 14.25, 7.75], [8, 14.75, 8.25], "st", rot=("z", -22.5, [8, 15.5, 8])),
    el("arm_e", [8, 14.25, 7.75], [10.5, 14.75, 8.25], "st", rot=("z", 22.5, [8, 15.5, 8])),
    el("oil_line", [14, 3, 7], [16, 4, 9], "b"),
], "block/turbine_governor")

model("turbine_stop_valve", "turbine", tx("d", "psteam", "end", body=B("turbine_stop_valve_body"), servo=B("turbine_stop_valve_servo")),
      [el("body", [3, 0, 3], [13, 10, 13], "body"),
       el("inlet", [5, 2, 1.5], [11, 8, 3], "psteam"),
       el("inlet_flange", [4, 1, 0], [12, 9, 1.5], "d", faces={"north": "end"}, full=["north"]),
       el("outlet", [13, 2, 5], [14.5, 8, 11], "psteam"),
       el("outlet_flange", [14.5, 1, 4], [16, 9, 12], "d", faces={"east": "end"}, full=["east"]),
       el("bonnet", [4, 10, 4], [12, 11.5, 12], "d")]
      + disc("servo", "y", 3, 11.5, 15.5, "servo", 8)
      + [el("servo_cap", [6.5, 15.5, 6.5], [9.5, 16, 9.5], "d")],
      "block/turbine_stop_valve")

model("turbine_gearbox", "turbine", tx("d", "b", hs=B("turbine_gearbox_housing")), [
    el("feet", [0, 0, 1], [16, 1.5, 15], "d"),
    el("housing", [1.5, 1.5, 2], [14.5, 14, 14], "hs"),
    el("boss_w", [0.5, 4.5, 4.5], [1.5, 11.5, 11.5], "d"),
    el("boss_e", [14.5, 4.5, 4.5], [15.5, 11.5, 11.5], "d"),
    el("breather", [7, 14, 7], [9, 15.5, 9], "b"),
], "block/turbine_gearbox")
model("turbine_gearbox/shaft_in", "turbine", tx("st", "bsm"),
      disc("shaft", "x", 1.5, -0.5, 1.5, "st", 8) + disc("coupling", "x", 3, -1, 0, "bsm", 8),
      "block/turbine_gearbox/partial/shaft_in", kind="partial")
model("turbine_gearbox/shaft_out", "turbine", tx("st", "bsm"),
      disc("shaft", "x", 1.5, 14.5, 16.5, "st", 8) + disc("coupling", "x", 2.5, 16, 17, "bsm", 8),
      "block/turbine_gearbox/partial/shaft_out", kind="partial")

model("electric_generator", "turbine", tx("d", "dsm", st=B("electric_generator_stator"), term=B("electric_generator_terminal")),
      [el("feet", [0.5, 0, 1], [15.5, 1, 15], "d"),
       el("stator", [1.5, 1, 1.5], [14.5, 13.5, 14.5], "st")]
      + disc("shield_w", "x", 6, 0, 1.5, "dsm", 8) + disc("shield_e", "x", 6, 14.5, 16, "dsm", 8)
      + [el("terminal_box", [5, 13.5, 4], [11, 16, 12], "term", full=["north", "up"])],
      "block/electric_generator")
model("electric_generator/rotor", "turbine", tx("st", "bsm"),
      disc("shaft", "x", 1.5, -0.75, 17, "st", 8) + disc("coupling_w", "x", 2.75, -1, -0.25, "bsm", 8),
      "block/electric_generator/partial/rotor", kind="partial")

model("turbine_turning_gear", "turbine", tx("d", "mb", cov=B("turbine_turning_gear_cover")),
      [el("base", [0, 0, 1], [16, 2, 15], "d"),
       el("housing", [2, 2, 3], [13, 12, 13], "cov")]
      + disc("motor", "x", 2, 3, 12, "mb", 8, c=(8, 14, 8))
      + [el("motor_fan_cover", [2, 12, 6], [3, 16, 10], "d")],
      "block/turbine_turning_gear")
model("turbine_turning_gear/gear", "turbine", tx("st", "bsm"),
      disc("gear", "x", 5.5, 13.5, 15.5, "bsm")
      + [el(f"tooth_{a}", [13.75, 1.5, 7.25], [15.25, 14.5, 8.75], "bsm", rot=("x", a, [8, 8, 8]) if a else None)
         for a in (0, 45, -45)]
      + [el("tooth_h", [13.8, 7.25, 1.5], [15.2, 8.75, 14.5], "bsm")]
      + disc("shaft", "x", 1.5, 12.5, 16.25, "st", 8),
      "block/turbine_turning_gear/partial/gear", kind="partial")

model("condenser", "turbine", tx("d", "wat", "stm", "end", sh=B("condenser_shell"), ts=B("condenser_tubesheet")), [
    el("shell", [0, 0, 1], [16, 15, 15], "sh", faces={"north": "ts", "south": "ts"}, full=["north", "south"]),
    el("steam_neck", [2, 15, 3], [14, 16, 13], "stm"),
    el("cw_in", [2.5, 10, -1.5], [6.5, 14, 1], "wat", faces={"north": "end"}, full=["north"]),
    el("cw_out", [9.5, 2, -1.5], [13.5, 6, 1], "wat", faces={"north": "end"}, full=["north"]),
    el("hotwell", [5, -0.5, 4], [11, 1, 12], "d"),
], "block/condenser")

# ======================================================================
# mechanics
# ======================================================================
model("feedwater_pump", "mechanics", tx("d", "wat", "end", cs=B("feedwater_pump_casing")),
      [el("baseplate", [0, 0, 1], [16, 2, 15], "d"),
       el("pedestal", [4, 2, 4], [12, 4, 12], "cs")]
      + disc("casing", "x", 5, 3.5, 12.5, "wat")
      + [el("suction", [6, 12, 6], [10, 15, 10], "wat"),
         el("suction_flange", [5, 15, 5], [11, 16, 11], "d", faces={"up": "end"}, full=["up"]),
         el("discharge", [6, 5, 1.5], [10, 9, 3.5], "wat"),
         el("discharge_flange", [5, 4, 0], [11, 10, 1.5], "d", faces={"north": "end"}, full=["north"]),
         el("bearing_w", [1, 4.5, 5], [3.5, 11, 11], "cs"),
         el("bearing_e", [12.5, 4.5, 5], [15, 11, 11], "cs")],
      "block/feedwater_pump")
model("feedwater_pump/shaft", "mechanics", tx("st", "bsm"),
      disc("shaft", "x", 1.25, -0.5, 16.5, "st", 8) + disc("coupling", "x", 2.5, 15, 16, "bsm", 8),
      "block/feedwater_pump/partial/shaft", kind="partial")

model("condensate_pump", "mechanics", tx("d", "wat", "end", "p", mot=B("condensate_pump_motor")),
      [el("base", [1, 0, 1], [15, 2, 15], "d")]
      + disc("pump_body", "y", 4.5, 2, 7, "wat")
      + [el("suction", [6, 3, 0], [10, 6.5, 3.5], "wat", faces={"north": "end"}, full=["north"]),
         el("discharge", [6, 3, 12.5], [10, 6.5, 16], "wat", faces={"south": "end"}, full=["south"]),
         el("stool_nw", [4.5, 7, 4.5], [5.5, 10, 5.5], "p"),
         el("stool_ne", [10.5, 7, 4.5], [11.5, 10, 5.5], "p"),
         el("stool_sw", [4.5, 7, 10.5], [5.5, 10, 11.5], "p"),
         el("stool_se", [10.5, 7, 10.5], [11.5, 10, 11.5], "p"),
         el("stool_ring", [4, 9.5, 4], [12, 10.4, 12], "d")]
      + disc("motor", "y", 4, 10.5, 15.5, "mot")
      + [el("motor_cap", [6, 15.5, 6], [10, 16, 10], "d")],
      "block/condensate_pump")
model("condensate_pump/coupling", "mechanics", tx("st", "bsm"),
      disc("shaft", "y", 1, 6.5, 10.5, "st", 8) + disc("coupling", "y", 2, 8, 9, "bsm", 8),
      "block/condensate_pump/partial/coupling", kind="partial")

model("zip_rod_winch", "mechanics", tx("d", "p", "b", "h", rat=B("zip_rod_winch_ratchet")), [
    el("base", [0, 0, 0], [16, 2, 16], "d"),
    el("rope_hole", [6.5, -0.01, 6.5], [9.5, 0.5, 9.5], "d"),
    el("cheek_w", [1, 2, 3], [3, 14, 13], "p"),
    el("cheek_e", [13, 2, 3], [15, 14, 13], "p"),
    el("ratchet", [15, 3.5, 3.5], [16, 12.5, 12.5], "d", faces={"east": "rat"}, full=["east"]),
    el("pawl", [15.5, 11, 10], [16.5, 14, 12], "b"),
    el("cross_tie", [3, 12.5, 6.5], [13, 14, 9.5], "h"),
], "block/zip_rod_winch")
model("zip_rod_winch/drum", "mechanics", tx("rope", "bsm", "st"),
      disc("drum", "x", 3.5, 3.5, 12.5, "rope") + disc("rim_w", "x", 5, 3, 4, "bsm") + disc("rim_e", "x", 5, 12, 13, "bsm")
      + disc("axle", "x", 1, 1, 15, "st", 8),
      "block/zip_rod_winch/partial/drum", kind="partial")
model("zip_rod_winch/rod", "mechanics", tx("ab", "st", "rope"), [
    el("absorber", [6.5, 0, 6.5], [9.5, 13, 9.5], "ab"),
    el("eye", [7, 13, 7.5], [9, 15, 8.5], "st"),
    el("rope", [7.5, 15, 7.5], [8.5, 16, 8.5], "rope"),
], "block/zip_rod_winch/partial/rod", kind="partial")

model("zip_rope", "mechanics", tx("rope", tag=B("zip_rope_tag")), [
    el("rope", [7.25, 0, 7.25], [8.75, 16, 8.75], "rope"),
    el("tag", [8.75, 9, 7.75], [11.75, 12.5, 8.25], "tag", full=["north", "south"]),
], "block/zip_rope")

model("control_rod_drive/rack", "mechanics", tx("st", "rack"), [
    el("rack", [7, 0, 9], [9, 16, 11], "rack"),
    el("guide", [7.5, 0, 11], [8.5, 16, 11.5], "st"),
], "block/control_rod_drive/partial/rack", kind="partial")

# ======================================================================
# fuel handling (перегрузочная машина, полярный кран)
# ======================================================================
model("refueling_machine", "fuel_handling", tx("d", "cy", "h", cab=B("refueling_machine_cab")), [
    el("cab", [1, 1, 1], [15, 14, 15], "cy"),
    el("window", [1.5, 1.5, 0.9], [14.5, 13.5, 1], "cab", full=["north"]),
    el("floor", [0, 0, 0], [16, 1, 16], "d"),
    el("roof", [0, 14, 0], [16, 16, 16], "d"),
    el("roof_stripe", [-0.01, 14.5, -0.01], [16.01, 15.25, 16.01], "h"),
], "block/refueling_machine")

model("refueling_bridge", "fuel_handling", tx("d", "louv", "cy", gd=B("refueling_bridge_girder")), [
    el("girder_n", [0, 5, 1], [16, 16, 4], "gd"),
    el("girder_s", [0, 5, 12], [16, 16, 15], "gd"),
    el("deck", [0, 13.5, 4], [16, 14.5, 12], "louv"),
    el("tie_w", [1, 5, 4], [3, 6.5, 12], "d"),
    el("tie_e", [13, 5, 4], [15, 6.5, 12], "d"),
    el("rail_cap_n", [0, 16, 2], [16, 16.5, 3], "d"),
    el("rail_cap_s", [0, 16, 13], [16, 16.5, 14], "d"),
], "block/refueling_bridge")

model("refueling_rail", "fuel_handling", tx("d", rl=B("refueling_rail")), [
    el("sole_plate", [0, 0, 3.5], [16, 1, 12.5], "d"),
    el("web", [0, 1, 7], [16, 3, 9], "rl"),
    el("head", [0, 3, 6], [16, 4.5, 10], "rl"),
    el("clip_w", [2, 1, 5], [4, 2, 11], "d"),
    el("clip_e", [12, 1, 5], [14, 2, 11], "d"),
], "block/refueling_rail")

model("refueling_trolley", "fuel_handling",
      tx("d", "r", "cy", sd=B("refueling_trolley_side")),
      [el("frame", [1.5, 2, 1], [14.5, 6, 15], "cy", faces={"north": "sd", "south": "sd"}, full=["north", "south"]),
       el("hoist", [4, 6, 4], [12, 10, 12], "cy"),
       el("mast_collar", [5.5, 10, 5.5], [10.5, 11.5, 10.5], "d"),
       el("mast_hole", [6.5, 1.99, 6.5], [9.5, 2, 9.5], "d")]
      + disc("wheel_nw", "x", 2, 0, 1.4, "r", 8, c=(8, 2, 3)) + disc("wheel_ne", "x", 2, 14.6, 16, "r", 8, c=(8, 2, 3))
      + disc("wheel_sw", "x", 2, 0, 1.4, "r", 8, c=(8, 2, 13)) + disc("wheel_se", "x", 2, 14.6, 16, "r", 8, c=(8, 2, 13)),
      "block/refueling_trolley")

model("refueling_mast", "fuel_handling", tx("d", tb=B("refueling_mast_tube")), [
    el("outer_tube", [5, 0, 5], [11, 16, 11], "tb", full=["north", "south", "east", "west"]),
    el("collar_top", [4.5, 14, 4.5], [11.5, 15.9, 11.5], "d"),
    el("collar_bottom", [4.5, 0.1, 4.5], [11.5, 1.5, 11.5], "d"),
    el("cable_duct", [11, 1.5, 7], [12, 14, 9], "d"),
], "block/refueling_mast")
model("refueling_mast/inner", "fuel_handling", tx("st", "d"), [
    el("inner_tube", [6, -16, 6], [10, 16, 10], "st"),
    el("stop_ring", [5.5, 15, 5.5], [10.5, 15.9, 10.5], "d"),
], "block/refueling_mast/partial/inner", kind="partial")

model("refueling_grapple", "fuel_handling", tx("d", "lw", hd=B("refueling_grapple_head")), [
    el("mast_stub", [6, 13, 6], [10, 16, 10], "d"),
    el("head", [4, 6, 4], [12, 13, 12], "hd"),
    el("guide_skirt", [3.5, 5, 3.5], [12.5, 6.5, 12.5], "d"),
    el("glow_lamp", [12, 9, 7], [12.75, 11, 9], "lw"),
    el("camera", [3, 9, 7], [4, 11, 9], "d"),
], "block/refueling_grapple")
model("refueling_grapple/fingers", "fuel_handling", tx("st", "d"), [
    el("finger_n", [7, 0, 4.5], [9, 5, 5.5], "st"),
    el("finger_s", [7, 0, 10.5], [9, 5, 11.5], "st"),
    el("finger_w", [4.5, 0, 7], [5.5, 5, 9], "st"),
    el("finger_e", [10.5, 0, 7], [11.5, 5, 9], "st"),
    el("tip_n", [7, 0, 5.5], [9, 1, 6.5], "d"),
    el("tip_s", [7, 0, 9.5], [9, 1, 10.5], "d"),
    el("tip_w", [5.5, 0, 7], [6.5, 1, 9], "d"),
    el("tip_e", [9.5, 0, 7], [10.5, 1, 9], "d"),
], "block/refueling_grapple/partial/fingers", kind="partial")

model("polar_crane", "fuel_handling", tx("d", "louv", "h", gd=B("polar_crane_girder")), [
    el("girder_n", [0, 7, 1], [16, 16, 6], "gd"),
    el("girder_s", [0, 7, 10], [16, 16, 15], "gd"),
    el("walkway", [0, 7, 6], [16, 8, 10], "louv"),
    el("end_plate_w", [0, 6, 0.5], [1, 7, 15.5], "d"),
    el("buffer_e", [15, 9, 2.5], [16.5, 12, 4.5], "h"),
    el("buffer_e2", [15, 9, 11.5], [16.5, 12, 13.5], "h"),
], "block/polar_crane")
model("polar_crane/hook", "fuel_handling", tx("cy", "rope", "d", "h"), [
    el("rope_w", [6, 12, 7.5], [7, 16, 8.5], "rope"),
    el("rope_e", [9, 12, 7.5], [10, 16, 8.5], "rope"),
    el("sheave_block", [4.5, 7, 5.5], [11.5, 12, 10.5], "cy"),
    el("block_stripe", [4.4, 7.25, 5.4], [11.6, 8, 10.6], "h"),
    el("shank", [7.5, 4, 7.5], [8.5, 7, 8.5], "d"),
    el("hook_back", [5.5, 1, 7.5], [6.5, 4.5, 8.5], "d"),
    el("hook_bottom", [5.5, 0, 7.5], [10, 1, 8.5], "d"),
    el("hook_tip", [9, 1, 7.5], [10, 2.5, 8.5], "d"),
], "block/polar_crane/partial/hook", kind="partial")

model("heavy_load_path_marker", "fuel_handling", {"m": B("heavy_load_path_marker"), "d": T("steel_dark")}, [
    el("plate", [0, 0, 0], [16, 0.5, 16], "d", faces={"up": "m"}, full=["up"]),
], "block/heavy_load_path_marker")

# ======================================================================
# ventilation (воздуховод вдоль Z)
# ======================================================================
def duct_walls(z0=0, z1=16):
    return [
        el("wall_w", [1, 1, z0], [2, 15, z1], "du", faces={"east": "in"}),
        el("wall_e", [14, 1, z0], [15, 15, z1], "du", faces={"west": "in"}),
        el("wall_top", [2, 14, z0], [14, 15, z1], "du", faces={"down": "in"}),
        el("wall_bottom", [2, 1, z0], [14, 2, z1], "du", faces={"up": "in"}),
    ]


def duct_flange(z0, z1, tag):
    return [
        el(f"fl_{tag}_w", [0.5, 0.5, z0], [1, 15.5, z1], "gal"),
        el(f"fl_{tag}_e", [15, 0.5, z0], [15.5, 15.5, z1], "gal"),
        el(f"fl_{tag}_t", [1, 15, z0], [15, 15.5, z1], "gal"),
        el(f"fl_{tag}_b", [1, 0.5, z0], [15, 1, z1], "gal"),
    ]


_vd = {"du": B("vent_duct"), "in": B("vent_duct_inner"), "gal": P("galvanized")}
model("vent_duct", "ventilation", _vd, duct_walls() + duct_flange(0, 1, "n") + duct_flange(15, 16, "s"), "block/vent_duct")
model("vent_damper", "ventilation", dict(_vd, act=B("vent_damper_actuator"), d=T("steel_dark")),
      duct_walls() + duct_flange(0, 1, "n") + duct_flange(15, 16, "s")
      + [el("actuator", [15.5, 5, 4], [17.5, 11, 12], "act", full=["east"]),
         el("bearing_w", [0, 7, 7], [1, 9, 9], "d")],
      "block/vent_damper")
model("vent_damper/blade", "ventilation", {"gal": P("galvanized"), "st": T("steel_bare")}, [
    el("blade", [2, 7.6, 2.5], [14, 8.4, 13.5], "gal"),
    el("spindle", [0.5, 7.5, 7.5], [15.5, 8.5, 8.5], "st"),
], "block/vent_damper/partial/blade", kind="partial")


def filter_box(face):
    return [
        el("housing", [0, 0, 1], [16, 16, 16], "hs"),
        el("filter_face", [1, 1, 0.5], [15, 15, 1], "face", full=["north"]),
        el("handle_w", [2, 7, -0.25], [3, 9, 0.5], "d"),
        el("handle_e", [13, 7, -0.25], [14, 9, 0.5], "d"),
        el("dp_port", [12.5, 16, 12], [13.5, 16.75, 13], "st"),
    ]


model("hepa_filter", "ventilation", tx("d", "st", hs=B("hepa_filter_housing"), face=B("hepa_filter_face")), filter_box(0), "block/hepa_filter")
model("charcoal_filter", "ventilation", tx("d", "st", hs=B("charcoal_filter_housing"), face=B("charcoal_filter_face")),
      filter_box(0) + [el("sample_port", [3, 16, 3], [5, 16.75, 5], "st")], "block/charcoal_filter")

model("vent_zone_controller", "ventilation", tx("d", "st", "cbl", front=B("vent_zone_controller_front")),
      wall_box(2, 2, 14, 14, 13, "front", "d")
      + [el("impulse_tube", [11.5, 14, 14], [12.5, 16, 15], "st"),
         el("cable", [7.5, 0, 14], [8.5, 2, 15], "cbl")],
      "block/vent_zone_controller")

model("vent_stack", "ventilation", tx("d", "end", stk=B("vent_stack")),
      disc("stack", "y", 6, 0, 16, "stk", faces={"up": "end", "down": "end"})
      + [el("ladder_w", [4.5, 0, 1], [5.25, 16, 2], "d"),
         el("ladder_e", [10.75, 0, 1], [11.5, 16, 2], "d")]
      + [el(f"rung_{y}", [5.25, y, 1.25], [10.75, y + 0.5, 1.75], "d") for y in (2, 6, 10, 14)]
      + [el("probe", [13.5, 9, 7.5], [15.5, 10, 8.5], "d")],
      "block/vent_stack")

# ======================================================================
# safety: hermetic boundary, ECCS, hydrogen
# ======================================================================
model("containment_hatch", "safety", tx("d", "con", fr=B("containment_hatch_frame")), [
    el("lintel", [0, 13, 0], [16, 16, 16], "con", faces={"north": "fr", "south": "fr"}),
    el("sill", [0, 0, 0], [16, 3, 16], "con", faces={"north": "fr", "south": "fr"}),
    el("jamb_w", [0, 3, 0], [3, 13, 16], "con", faces={"north": "fr", "south": "fr"}),
    el("jamb_e", [13, 3, 0], [16, 13, 16], "con", faces={"north": "fr", "south": "fr"}),
    el("liner_t", [3, 12.5, 2.5], [13, 13, 16], "d"),
    el("liner_b", [3, 3, 2.5], [13, 3.5, 16], "d"),
    el("liner_w", [3, 3.5, 2.5], [3.5, 12.5, 16], "d"),
    el("liner_e", [12.5, 3.5, 2.5], [13, 12.5, 16], "d"),
    el("hinge_e", [13.1, 4, -0.5], [14.5, 6, 0.5], "d"),
    el("hinge_e2", [13.1, 10, -0.5], [14.5, 12, 0.5], "d"),
], "block/containment_hatch")
model("containment_hatch/door", "safety", tx("d", "red", dr=B("containment_hatch_door")),
      [el("door", [3, 3, 0.5], [13, 13, 2.5], "d", faces={"north": "dr", "south": "dr"}, full=["north", "south"]),
       el("hub", [7, 7, -0.5], [9, 9, 0.5], "red"),
       el("spoke_x", [3.75, 7.75, -0.25], [12.25, 8.25, 0.25], "red"),
       el("spoke_y", [7.75, 3.75, -0.25], [8.25, 12.25, 0.25], "red")]
      + ring_xy("wheel", 4.25, 0.75, -0.5, 0, "red"),
      "block/containment_hatch/partial/door", kind="partial")

model("containment_penetration", "safety", tx("con", "d", "end", pen=B("containment_penetration")), [
    el("wall", [0, 0, 0], [16, 16, 16], "con", faces={"north": "pen", "south": "pen"}, full=["north", "south"]),
    el("sleeve_n", [3, 3, -1], [13, 13, 0], "d", faces={"north": "end"}, full=["north"]),
    el("sleeve_s", [3, 3, 16], [13, 13, 17], "d", faces={"south": "end"}, full=["south"]),
], "block/containment_penetration")

model("hydrogen_recombiner", "safety", tx("d", "st", "louv", front=B("hydrogen_recombiner_front")), [
    el("leg_nw", [2, 0, 3], [3, 3, 4], "d"),
    el("leg_ne", [13, 0, 3], [14, 3, 4], "d"),
    el("leg_sw", [2, 0, 12], [3, 3, 13], "d"),
    el("leg_se", [13, 0, 12], [14, 3, 13], "d"),
    el("housing", [2, 3, 3], [14, 14, 13], "st"),
    el("front", [2.5, 3.5, 2.9], [13.5, 13.5, 3], "front", full=["north"]),
    el("hood", [1.5, 14, 2.5], [14.5, 16, 13.5], "louv"),
    el("wall_bracket", [5, 6, 13], [11, 11, 16], "d"),
], "block/hydrogen_recombiner")


def tank(shell, r, h, extra):
    return disc("shell", "y", r, 0, h, shell, faces={"up": "top", "down": "top"}) + extra


model("boron_injection_tank", "safety", tx("d", "st", "end", sh=B("boron_injection_tank_shell"), top=B("tank_top")),
      tank("sh", 6.5, 13.5, disc("head", "y", 4.5, 13.5, 15, "st", faces={"up": "top"}) + [
          el("top_nozzle", [7, 15, 7], [9, 16, 9], "d"),
          el("outlet", [6.5, 2, -0.5], [9.5, 5, 2], "d", faces={"north": "end"}, full=["north"]),
          el("skirt", [2, 0, 2], [14, 1, 14], "d")]),
      "block/boron_injection_tank")

model("eccs_accumulator", "safety", tx("d", "st", "end", "cys", sh=B("eccs_accumulator_shell"), top=B("tank_top")),
      tank("sh", 7, 12.5, disc("head_1", "y", 5.5, 12.5, 14, "st") + disc("head_2", "y", 3.5, 14, 15, "st", 8) + [
          el("n2_nozzle", [7, 15, 7], [9, 16, 9], "cys"),
          el("outlet", [5.5, 1, -0.5], [10.5, 6, 1.5], "d", faces={"north": "end"}, full=["north"]),
          el("lug_w", [0, 8, 7], [1.5, 10, 9], "d"),
          el("lug_e", [14.5, 8, 7], [16, 10, 9], "d")]),
      "block/eccs_accumulator")

model("borated_water_tank", "safety", tx("d", "p", sh=B("borated_water_tank_shell")), [
    el("wall", [0, 0, 1], [16, 16, 16], "sh", faces={"up": "p", "down": "d"}),
    el("rib_w", [2.5, 0, 0], [3.5, 16, 1], "d"),
    el("rib_e", [12.5, 0, 0], [13.5, 16, 1], "d"),
    el("ring_beam", [0, 15, 0.5], [16, 15.9, 1], "d"),
], "block/borated_water_tank")


def igniter(on):
    els = wall_box(3, 3, 13, 13, 12, "front", "d") + [el("conduit", [7, 0, 13], [9, 3, 15], "d")]
    els.append(el("glow_coil" if on else "coil", [6, 6, 11.3], [10, 10, 11.9], "coil" if on else "loff"))
    return els


_ig = tx("d", "coil", "loff", front=B("hydrogen_igniter_front"))
model("hydrogen_igniter", "safety", _ig, igniter(False), "block/hydrogen_igniter")
model("hydrogen_igniter_on", "safety", _ig, igniter(True), "block/hydrogen_igniter_on")

model("corium", "safety", {"c": B("corium")}, [el("glow_corium", [0, 0, 0], [16, 16, 16], "c")], "block/corium")

# ======================================================================
# control room (03b / 03c): органы щита — плиты 3 px у задней (южной) стороны
# ======================================================================
model("analog_gauge", "control", tx("pn", "pnd", "st", dial=B("analog_gauge_dial")), [
    panel_tile(),
    el("case", [2, 2, 11], [14, 14, 13], "pnd"),
    el("dial_face", [2.5, 2.5, 10.9], [13.5, 13.5, 11], "dial", full=["north"]),
    el("bezel_t", [2, 13.5, 10.6], [14, 14, 11], "st"),
    el("bezel_b", [2, 2, 10.6], [14, 2.5, 11], "st"),
], "block/analog_gauge")

model("edgewise_meter", "control", tx("pn", "pnd", sc=B("edgewise_meter_scale")), [
    panel_tile(),
    el("case", [5, 1, 10], [11, 15, 13], "pnd"),
    el("scale", [5.5, 1.5, 9.9], [10.5, 14.5, 10], "sc"),
    el("zero_adj", [7.5, 0.25, 10.5], [8.5, 1, 11.5], "pnd"),
], "block/edgewise_meter")

model("chart_recorder", "control", tx("pn", "pnd", "st", win=B("chart_recorder_window")), [
    panel_tile(),
    el("case", [1, 1, 9], [15, 15, 13], "pnd"),
    el("door", [1.5, 1.5, 8.9], [14.5, 14.5, 9], "win", full=["north"]),
    el("latch", [13.5, 7, 8.4], [14.5, 9, 8.9], "st"),
], "block/chart_recorder")

model("digital_display", "control", tx("pn", "pnd", gl=B("digital_display_glass")), [
    panel_tile(),
    el("bezel", [1, 4, 11], [15, 12, 13], "pnd"),
    el("glass", [1.5, 4.5, 10.9], [14.5, 11.5, 11], "gl"),
], "block/digital_display")


def lamp_model(lit):
    return [
        panel_tile(),
        el("bezel", [4.5, 4.5, 12], [11.5, 11.5, 13], "pnd"),
        el("glow_lens" if lit else "lens", [5.5, 5.5, 10.5], [10.5, 10.5, 12], "lw" if lit else "loff"),
        el("cap_ring", [5, 5, 11.5], [11, 11, 12], "pnd"),
        el("label", [4, 1.5, 12.5], [12, 3.5, 13], "lbl", full=["north"]),
    ]


_lp = tx("pn", "pnd", "lw", "loff", lbl=B("indicator_lamp_label"))
model("indicator_lamp", "control", _lp, lamp_model(True), "block/indicator_lamp")
model("indicator_lamp_off", "control", _lp, lamp_model(False), "block/indicator_lamp_off")

model("annunciator_panel", "control", tx("pn", "pnd", face=B("annunciator_panel_face")), [
    panel_tile(),
    el("housing", [0, 0, 11], [16, 16, 13], "pnd"),
    el("face", [0.5, 0.5, 10.9], [15.5, 15.5, 11], "face", full=["north"]),
], "block/annunciator_panel")

for _form in ("pipe", "bend", "tee", "pump", "valve", "control_valve", "check_valve", "tank",
              "heat_exchanger", "turbine", "reactor", "label"):
    _mid = "mimic_tile" if _form == "pipe" else f"mimic_tile_{_form}"
    model(_mid, "control", {"m": B(f"mimic_tile_{_form}"), "pn": P("panel")}, [
        el("tile", [0, 0, 15], [16, 16, 16], "pn", faces={"north": "m"}, full=["north"]),
    ], f"block/{_mid}")

model("key_switch", "control", tx("pn", "pnd", pl=B("key_switch_plate")), [
    panel_tile(),
    el("plate", [4, 4, 12.5], [12, 12, 13], "pnd", faces={"north": "pl"}, full=["north"]),
], "block/key_switch")
model("key_switch/key", "control", tx("pnd", "st", "r"),
      disc("boss", "z", 1.75, 11.5, 12.5, "st", 8)
      + [el("handle", [7.25, 4.5, 10], [8.75, 11.5, 11.5], "r"),
         el("pointer", [7.75, 11.5, 10.5], [8.25, 12.5, 11], "st")],
      "block/key_switch/partial/key", kind="partial")

model("selector_switch", "control", tx("pn", "pnd", pl=B("selector_switch_plate")), [
    panel_tile(),
    el("plate", [3, 3, 12.5], [13, 13, 13], "pnd", faces={"north": "pl"}, full=["north"]),
], "block/selector_switch")
model("selector_switch/knob", "control", tx("r", "st"),
      disc("base", "z", 2.5, 11.5, 12.5, "r", 8)
      + [el("grip", [7.25, 5, 10], [8.75, 11, 11.5], "r"),
         el("pointer", [7.75, 9.5, 9.9], [8.25, 11, 10], "st")],
      "block/selector_switch/partial/knob", kind="partial")


def pushbutton(lit):
    return [panel_tile(),
            el("bezel", [4, 4, 12.5], [12, 12, 13], "pnd", faces={"north": "bz"}, full=["north"])] + \
        disc("glow_button" if lit else "button", "z", 2.5, 11, 12.5, "lg", 8)


_pb = tx("pn", "pnd", "lg", bz=B("pushbutton_bezel"))
model("pushbutton", "control", _pb, pushbutton(False), "block/pushbutton")
model("pushbutton_lit", "control", _pb, pushbutton(True), "block/pushbutton_lit")

model("scram_button", "control", tx("pn", "pnd", "redsm", pl=B("scram_button_plate")),
      [panel_tile(),
       el("plate", [2, 2, 12.5], [14, 14, 13], "pnd", faces={"north": "pl"}, full=["north"])]
      + disc("stem", "z", 1.5, 11, 12.5, "pnd", 8)
      + disc("mushroom", "z", 3, 10, 11, "redsm"),
      "block/scram_button")
model("scram_button/cap", "control", {"c": B("scram_button_cap"), "d": T("steel_dark")}, [
    el("cover", [3, 3, 8.5], [13, 13, 9], "c"),
    el("side_t", [3, 12.5, 9], [13, 13, 12.5], "c"),
    el("side_b", [3, 3, 9], [13, 3.5, 12.5], "c"),
    el("side_w", [3, 3.5, 9], [3.5, 12.5, 12.5], "c"),
    el("side_e", [12.5, 3.5, 9], [13, 12.5, 12.5], "c"),
    el("hinge", [5, 13, 11.5], [11, 14, 12.5], "d"),
], "block/scram_button/partial/cap", kind="partial")

model("bypass_keyswitch", "control", tx("pn", "pnd", "bsm", "st", pl=B("bypass_keyswitch_plate")),
      [panel_tile(),
       el("plate", [3, 3, 12.5], [13, 13, 13], "pnd", faces={"north": "pl"}, full=["north"])]
      + disc("lock_cylinder", "z", 1.75, 11.5, 12.5, "bsm", 8, c=(8, 7, 8))
      + [el("seal_tag", [10.5, 3.5, 12], [12, 6, 12.5], "st")],
      "block/bypass_keyswitch")

model("alarm_horn", "control", tx("pnd", "red", gr=B("alarm_horn_grille")), [
    el("mount", [5, 5, 14], [11, 11, 16], "pnd"),
    el("body", [6, 6, 12], [10, 10, 14], "red"),
    el("flare_1", [5, 5, 10.5], [11, 11, 12], "red"),
    el("flare_2", [4, 4, 9], [12, 12, 10.5], "red", faces={"north": "gr"}, full=["north"]),
    el("conduit", [7.5, 0, 14.5], [8.5, 5, 15.5], "pnd"),
], "block/alarm_horn")

# секция пульта: наклонная столешница 22,5°, оператор стоит с севера
model("control_desk", "control", tx("pn", "pns", "pnd", top=B("control_desk_top")), [
    el("plinth", [0.5, 0, 3], [15.5, 1, 15.5], "pnd"),
    el("body", [0.5, 1, 2], [15.5, 9, 15.5], "pn"),
    el("back", [0.25, 1, 14], [15.75, 15.5, 16], "pns"),
    el("desktop", [0, 3, 1], [16, 10, 15], "pns", faces={"up": "top"}, full=["up"], rot=("x", -22.5, [8, 9, 1])),
    el("armrest", [0, 8, 0], [16, 9.5, 2], "pnd"),
], "block/control_desk")

model("control_room_core", "control", tx("pn", "pnd", "louv", front=B("control_room_core_front")), [
    el("cabinet", [1, 0, 1], [15, 16, 16], "pn"),
    el("front", [1.5, 1, 0.9], [14.5, 15, 1], "front", full=["north"]),
    el("vent", [3, 15.5, 4], [13, 16.25, 13], "louv"),
], "block/control_room_core")

model("remote_shutdown_panel", "control", tx("pn", "pnd", front=B("remote_shutdown_panel_front")), [
    el("cabinet", [0, 0, 2], [16, 16, 16], "pn"),
    el("face", [0.5, 0.5, 1.9], [15.5, 15, 2], "front", full=["north"]),
    el("hood", [0, 15, 0], [16, 16, 2], "pnd"),
    el("ledge", [0, 0, 0.5], [16, 1, 2], "pnd"),
], "block/remote_shutdown_panel")


def transfer(rsp):
    a = 45 if rsp else 0
    return wall_box(2, 2, 14, 14, 12, "front", "d") + [
        el("hub", [6.5, 6.5, 11], [9.5, 9.5, 11.9], "pnd"),
        el("handle", [7, 3.5, 10], [9, 12.5, 11], "red", rot=("z", a, [8, 8, 10.5]) if a else None),
    ]


_ts = tx("d", "pnd", "red", front=B("control_transfer_switch_plate"))
model("control_transfer_switch", "control", _ts, transfer(False), "block/control_transfer_switch")
model("control_transfer_switch_rsp", "control", _ts, transfer(True), "block/control_transfer_switch_rsp")

model("io_module", "control", tx("cab", "d", "cbl", front=B("io_module_front")), [
    el("cabinet", [0, 0, 1], [16, 16, 16], "cab"),
    el("door", [1, 1, 0.5], [15, 15, 1], "front", full=["north"]),
    el("handle", [13.25, 6, -0.25], [14, 10, 0.5], "d"),
    el("gland_1", [3, 16, 10], [5, 16.75, 12], "cbl"),
    el("gland_2", [7, 16, 10], [9, 16.75, 12], "cbl"),
    el("gland_3", [11, 16, 10], [13, 16.75, 12], "cbl"),
], "block/io_module")

model("watchdog_relay", "control", tx("pnd", "st", front=B("watchdog_relay_front")), [
    el("back_plate", [2, 1, 15], [14, 15, 16], "pnd"),
    el("din_rail", [1, 7, 14.5], [15, 9, 15], "st"),
    el("relay", [4, 3, 11], [12, 13, 14.5], "pnd", faces={"north": "front"}, full=["north"]),
], "block/watchdog_relay")

model("relay_controller", "control", tx("pnd", "st", front=B("relay_controller_front")), [
    el("back_plate", [1, 1, 15], [15, 15, 16], "pnd"),
    el("din_rail", [0.5, 7, 14.5], [15.5, 9, 15], "st"),
    el("relay", [2, 3, 11], [14, 13, 14.5], "pnd", faces={"north": "front"}, full=["north"]),
], "block/relay_controller")

_cb = {"c": B("signal_cable")}
model("signal_cable", "control", _cb, [el("core", [6.5, 6.5, 6.5], [9.5, 9.5, 9.5], "c")], "block/signal_cable")
model("signal_cable_side", "control", _cb, [el("arm", [7, 7, 0], [9, 9, 6.5], "c")], "block/signal_cable_side")

# ======================================================================
# radiation safety (04b)
# ======================================================================
for _id in ("lead_block", "heavy_concrete", "polyethylene_block", "borated_polyethylene", "tungsten_block",
            "depleted_uranium_block", "steel_plate", "lead_glass"):
    model(_id, "radiation", {"t": B(_id)}, [el("block", [0, 0, 0], [16, 16, 16], "t")], f"block/{_id}")

model("lead_sheet", "radiation", {"t": B("lead_sheet")}, [el("sheet", [0, 0, 0], [16, 0.5, 16], "t")], "block/lead_sheet")
model("decon_paint", "radiation", {"t": B("decon_paint")}, [el("coating", [0, 0, 0], [16, 0.25, 16], "t")], "block/decon_paint")

model("portal_monitor", "radiation", tx("d", "lg", "cab", pnl=B("portal_monitor_panel")), [
    el("base", [2, 0, 3], [14, 1, 13], "d"),
    el("column", [3, 1, 4], [13, 15.5, 12], "cab", faces={"north": "pnl"}, full=["north"]),
    el("cap", [2.5, 15.5, 3.5], [13.5, 16, 12.5], "d"),
    el("glow_status", [6.5, 16, 6.5], [9.5, 17, 9.5], "lg"),
], "block/portal_monitor")

model("hand_foot_monitor", "radiation", tx("d", "cab", front=B("hand_foot_monitor_front"), foot=B("hand_foot_monitor_foot")), [
    el("platform", [0, 0, 0], [16, 1.5, 16], "d", faces={"up": "foot"}, full=["up"]),
    el("column", [4, 1.5, 10], [12, 10, 15], "cab"),
    el("head", [1, 10, 5], [15, 16, 15], "cab", faces={"north": "front"}, full=["north"]),
], "block/hand_foot_monitor")

model("whole_body_counter", "radiation", tx("pb", "r", "st", "d", pnl=B("whole_body_counter_panel")), [
    el("back_shield", [1, 0, 12], [15, 16, 16], "pb", faces={"north": "pnl"}, full=["north"]),
    el("side_w", [0, 0, 4], [2, 12, 15.9], "pb"),
    el("side_e", [14, 0, 4], [16, 12, 15.9], "pb"),
    el("seat_base", [4, 0, 5], [12, 4, 12], "d"),
    el("seat", [3, 4, 4], [13, 5.5, 12], "r"),
    el("detector", [5, 12.5, 6], [11, 15, 12], "st"),
], "block/whole_body_counter")

model("stack_monitor", "radiation", tx("cab", "st", "d", front=B("stack_monitor_front")), [
    el("cabinet", [1, 0, 2], [15, 16, 16], "cab"),
    el("door", [1.5, 0.5, 1.9], [14.5, 15.5, 2], "front", full=["north"]),
    el("sample_line", [15, 3, 7], [16, 16, 9], "st"),
    el("sample_bend", [13, 15.5, 7], [16, 16.5, 9], "st"),
], "block/stack_monitor")

model("tld_reader", "radiation", tx("cab", "d", "pns", front=B("tld_reader_front")), [
    el("table", [0, 0, 0], [16, 7, 16], "pns"),
    el("table_top", [-0.5, 7, -0.5], [16.5, 8, 16.5], "d"),
    el("reader", [2, 8, 4], [14, 14, 14], "cab", faces={"north": "front"}, full=["north"]),
], "block/tld_reader")

model("medical_station", "radiation", tx("d", "pns", front=B("medical_station_front")), [
    el("cabinet", [0, 1, 1], [16, 16, 16], "pns", faces={"north": "front"}, full=["north"]),
    el("plinth", [0.5, 0, 1.5], [15.5, 1, 15.5], "d"),
    el("handle", [13.5, 7, 0.25], [14.25, 10, 1], "d"),
], "block/medical_station")

model("zone_controller", "radiation", tx("d", "cbl", front=B("zone_controller_front")),
      wall_box(2, 2, 14, 14, 13, "front", "d") + [el("conduit", [7, 0, 14], [9, 2, 15], "cbl")],
      "block/zone_controller")

for _half, _y in (("bottom", ""), ("top", "_top")):
    model(f"zone_access_door{_y}", "radiation", {"t": B(f"zone_access_door_{_half}"), "d": T("steel_dark")}, [
        el("leaf", [0, 0, 13], [16, 16, 16], "d", faces={"north": "t", "south": "t"}, full=["north", "south"]),
    ], f"block/zone_access_door{_y}")

model("sanitary_checkpoint", "radiation", tx("cab", "pnd", "st", front=B("sanitary_checkpoint_front")), [
    el("pedestal", [2, 0, 3], [14, 12, 13], "cab", faces={"north": "front"}, full=["north"]),
    el("console", [1, 12, 2], [15, 14, 14], "pnd"),
    el("turnstile_hub", [14, 7, 6.5], [15, 10, 9.5], "st"),
    el("turnstile_arm", [15, 8, 7.5], [16.5, 9, 8.5], "st"),
], "block/sanitary_checkpoint")

for _id in ("clean_locker", "dirty_locker"):
    model(_id, "radiation", {"c": T("cabinet"), "f": B(f"{_id}_front"), "d": T("steel_dark")}, [
        el("locker", [1, 0, 2], [15, 16, 16], "c"),
        el("doors", [1.5, 0.5, 1.9], [14.5, 15.5, 2], "f", full=["north"]),
        el("vent_top", [3, 16, 5], [13, 16.5, 13], "d"),
    ], f"block/{_id}")

model("decon_shower", "radiation", tx("st", "red", "tile", hd=B("decon_shower_head")),
      [el("wall", [0, 0, 15], [16, 16, 16], "tile"),
       el("riser", [7.5, 2, 13.5], [8.5, 14, 14.5], "st"),
       el("arm", [7.4, 13, 9.5], [8.6, 14.1, 14.5], "st")]
      + disc("head", "y", 2.5, 12, 13, "st", 8, faces={"down": "hd"}, c=(8, 8, 9.5))
      + [el("valve_hot", [5, 6, 13], [6.5, 7.5, 15], "red"),
         el("valve_cold", [9.5, 6, 13], [11, 7.5, 15], "st")],
      "block/decon_shower")

model("decon_drain", "radiation", {"t": P("tile"), "top": B("decon_drain_top")}, [
    el("floor", [0, 0, 0], [16, 16, 16], "t", faces={"up": "top"}),
], "block/decon_drain")


def bath(front, liquid, extra):
    return [
        el("cabinet", [1, 0, 1], [15, 6, 15], "st", faces={"north": "front"}, full=["north"]),
        el("tub_n", [1, 6, 1], [15, 12, 2], "st"),
        el("tub_s", [1, 6, 14], [15, 12, 15], "st"),
        el("tub_w", [1, 6, 2], [2, 12, 14], "st"),
        el("tub_e", [14, 6, 2], [15, 12, 14], "st"),
        el("liquid", [2, 6, 2], [14, 10.5, 14], "liq"),
        el("rim", [0.5, 11.5, 0.5], [15.5, 12, 1], "d"),
    ] + extra


model("ultrasonic_bath", "radiation", tx("st", "d", "pnd", front=B("ultrasonic_bath_front"), liq=B("ultrasonic_bath_liquid")),
      bath("front", "liq", [el("transducer_box", [5, 12, 14.5], [11, 14, 15.5], "pnd")]), "block/ultrasonic_bath")
model("electropolish_bath", "radiation", tx("st", "d", "cu", "pb", front=B("electropolish_bath_front"), liq=B("electropolish_bath_liquid")),
      bath("front", "liq", [el("bus_n", [0.5, 12, 5], [15.5, 13, 6], "cu"),
                            el("bus_s", [0.5, 12, 10], [15.5, 13, 11], "cu"),
                            el("electrode_n", [3, 7, 5.25], [13, 12, 5.75], "pb"),
                            el("electrode_s", [3, 7, 10.25], [13, 12, 10.75], "pb")]),
      "block/electropolish_bath")

model("scabbler", "radiation", tx("d", "r", "st", hs=B("scabbler_housing")), [
    el("chassis", [1, 4, 1], [15, 12, 15], "hs"),
    el("shaft_boss", [6, 12, 6], [10, 14, 10], "d"),
    el("dust_hood", [1.5, 3, 1.5], [14.5, 4, 14.5], "d"),
    el("wheel_nw", [-0.5, 0, 2], [1, 3, 5], "r"),
    el("wheel_ne", [15, 0, 2], [16.5, 3, 5], "r"),
    el("wheel_sw", [-0.5, 0, 11], [1, 3, 14], "r"),
    el("wheel_se", [15, 0, 11], [16.5, 3, 14], "r"),
    el("hose_port", [12, 12, 12], [14, 14.5, 14], "st"),
], "block/scabbler")
model("scabbler/head", "radiation", tx("d", "st"), [
    el("piston_plate", [3, 1.5, 3], [13, 3, 13], "d"),
    el("bit_nw", [4, 0, 4], [6, 1.5, 6], "st"),
    el("bit_ne", [10, 0, 4], [12, 1.5, 6], "st"),
    el("bit_sw", [4, 0, 10], [6, 1.5, 12], "st"),
    el("bit_se", [10, 0, 10], [12, 1.5, 12], "st"),
    el("bit_c", [7, 0, 7], [9, 1.5, 9], "st"),
], "block/scabbler/partial/head", kind="partial")

model("decon_laundry", "radiation", tx("d", "pns", "st", front=B("decon_laundry_front")), [
    el("body", [0, 0, 1], [16, 16, 16], "pns", faces={"north": "front"}, full=["north"]),
    el("door_hinge", [2, 6, 0.5], [3, 11, 1], "d"),
    el("drain_pipe", [12, 0, 15.5], [14, 3, 16.5], "st"),
    el("feed_pipe", [2, 14, 15.5], [4, 15.9, 16.5], "st"),
], "block/decon_laundry")
model("decon_laundry/drum", "radiation", {"dr": B("decon_laundry_drum")},
      disc("drum", "z", 5, 2, 14, "dr"),
      "block/decon_laundry/partial/drum", kind="partial")

model("air_supply_port", "radiation", tx("b", "st", "air", pl=B("air_supply_port_plate")), [
    el("plate", [3, 3, 14], [13, 13, 16], "air", faces={"north": "pl"}, full=["north"]),
    el("coupling", [7, 3.5, 12], [9, 6, 14], "b"),
    el("barb", [7.5, 4, 11], [8.5, 5.5, 12], "st"),
    el("supply_pipe", [7, 13, 14.5], [9, 16, 15.5], "air"),
], "block/air_supply_port")

# ======================================================================
# items, fluids
# ======================================================================
for _it in ("geiger_counter", "dosimeter_radiometer", "contamination_meter", "personal_dosimeter_epd", "tld_badge",
            "smear_kit", "respirator", "full_face_respirator", "iodine_cartridge", "papr", "pneumosuit_helmet",
            "pneumosuit_jacket", "pneumosuit_trousers", "pneumosuit_boots", "protective_overalls", "boot_covers",
            "lead_apron", "potassium_iodide", "prussian_blue", "ca_dtpa", "antiemetic", "cytokine_injection",
            "blood_bag", "bone_marrow_transplant", "medical_record", "decon_sprayer", "steam_decon_lance",
            "strippable_coating",
            "wiring_tool", "bypass_key", "operator_key", "access_card", "instrument_calibrator", "chart_paper",
            "vibrometer", "mechanic_stethoscope", "lubricant", "bearing_kit", "balancing_weights", "alignment_tool",
            "impeller_kit"):
    item(_it, f"item/{_it}.png")

fluid("contaminated_decon_solution", "#7d8a3c", kind="liquid")
