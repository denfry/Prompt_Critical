"""Reactor island, mechanics, piping, instruments (first batch)."""
from pclib import *

# ---------------- core ----------------
model("reactor_vessel_wall", "core", {"wall": "block/reactor_vessel_wall.png"},
      [el("wall", [0, 0, 0], [16, 16, 16], "wall")], "block/reactor_vessel_wall")

model("containment_wall", "core", {"wall": "block/containment_wall.png"},
      [el("wall", [0, 0, 0], [16, 16, 16], "wall")], "block/containment_wall")

model("moderator_block_graphite", "core", {"g": "block/moderator_block_graphite.png"},
      [el("block", [0, 0, 0], [16, 16, 16], "g")], "block/moderator_block_graphite")

# графитовая колонна с циркониевой трубой канала
model("fuel_channel", "core", {"g": T("graphite"), "zr": T("zirconium"), "d": T("steel_dark")}, [
    el("graphite_w", [0, 0, 0], [5, 16, 16], "g"),
    el("graphite_e", [11, 0, 0], [16, 16, 16], "g"),
    el("graphite_n", [5, 0, 0], [11, 16, 5], "g"),
    el("graphite_s", [5, 0, 11], [11, 16, 16], "g"),
    el("tube_w", [5, 0, 5], [6, 16.5, 11], "zr"),
    el("tube_e", [10, 0, 5], [11, 16.5, 11], "zr"),
    el("tube_n", [6, 0, 5], [10, 16.5, 6], "zr"),
    el("tube_s", [6, 0, 10], [10, 16.5, 11], "zr"),
    el("plug", [6, 0, 6], [10, 1, 10], "d"),
], "block/fuel_channel")

# ---------------- mechanics ----------------
crd = [
    el("base_flange", [0, 0, 0], [16, 3, 16], "d"),
    el("cheek_w", [1, 3, 2], [3, 15, 14], "p"),
    el("cheek_e", [13, 3, 2], [15, 15, 14], "p"),
    el("boss_w", [0, 6, 6], [1, 10, 10], "b"),
    el("boss_e", [15, 6, 6], [16, 10, 10], "b"),
    el("crossbar", [1, 14, 5], [15, 16, 11], "h"),
    el("sign_plate", [6, 14.25, 4.9], [10, 15.75, 5], "s", full=["north"]),
    el("cable_guide", [7, 0, 7], [9, 3.01, 9], "r"),
]
model("control_rod_drive", "mechanics",
      {"d": T("steel_dark"), "p": T("steel_plate"), "b": T("brass"), "h": T("hazard"),
       "s": T("trefoil"), "r": T("rubber")}, crd, "block/control_rod_drive")
model("control_rod_drive/winch_drum", "mechanics",
      {"st": T("steel_bare"), "b": T("brass_smooth")},
      disc("drum", "x", 4.5, 3.5, 12.5, "st") + disc("rim_w", "x", 5.5, 3, 4, "b") + disc("rim_e", "x", 5.5, 12, 13, "b"),
      "block/control_rod_drive/partial/winch_drum", kind="partial")
model("control_rod_drive/rod", "mechanics", {"a": T("absorber"), "st": T("steel_bare")}, [
    el("absorber", [6.5, 1, 6.5], [9.5, 15, 9.5], "a"),
    el("tip", [7, 0, 7], [9, 1, 9], "st"),
    el("head", [6, 15, 6], [10, 16, 10], "st"),
], "block/control_rod_drive/partial/rod", kind="partial")

pump = (
    [el("base", [0, 0, 0], [16, 2, 16], "d")]
    + disc("volute", "y", 5.5, 2, 10, "sm")
    + [
        el("inlet_nozzle", [5, 3, 1.5], [11, 9, 4], "pipe"),
        el("inlet_flange", [4, 2, 0], [12, 10, 1.5], "d", faces={"north": "end"}, full=["north"]),
        el("outlet_nozzle", [12, 3, 5], [14.5, 9, 11], "pipe"),
        el("outlet_flange", [14.5, 2, 4], [16, 10, 12], "d", faces={"east": "end"}, full=["east"]),
        el("seal_housing", [4.5, 10, 4.5], [11.5, 13, 11.5], "st"),
        el("hazard_band", [4.4, 12, 4.4], [11.6, 13, 11.6], "h"),
        el("coupling", [5.5, 13, 5.5], [10.5, 16, 10.5], "b"),
    ]
)
model("primary_pump", "mechanics",
      {"d": T("steel_dark"), "p": T("steel_plate"), "st": T("steel_bare"), "b": T("brass"),
       "h": T("hazard"), "pipe": "block/pipe_primary.png", "sm": T("steel_smooth"), "end": T("pipe_end")}, pump, "block/primary_pump")
model("primary_pump/impeller", "mechanics", {"b": T("brass_smooth"), "st": T("steel_bare")},
      disc("hub", "y", 2, 3, 9, "st", 8)
      + [el("blade_x", [2.5, 4, 7.5], [13.5, 8, 8.5], "b"),
         el("blade_z", [7.5, 4, 2.5], [8.5, 8, 13.5], "b"),
         el("blade_d1", [2.5, 4, 7.5], [13.5, 8, 8.5], "b", rot=("y", 45, [8, 8, 8])),
         el("blade_d2", [2.5, 4, 7.5], [13.5, 8, 8.5], "b", rot=("y", -45, [8, 8, 8]))]
      + disc("shroud", "y", 6, 3, 4, "b"),
      "block/primary_pump/partial/impeller", kind="partial")

model("pump_flywheel", "mechanics", {"d": T("steel_dark"), "p": T("steel_plate"), "b": T("brass"), "h": T("hazard")}, [
    el("base", [0, 0, 0], [16, 2, 16], "d"),
    el("pedestal", [3, 2, 3], [13, 4, 13], "p"),
    el("bearing_lower", [5, 4, 5], [11, 5.5, 11], "b"),
    el("guard_n", [0, 2, 0], [16, 3, 1], "h"),
    el("guard_s", [0, 2, 15], [16, 3, 16], "h"),
], "block/pump_flywheel")
model("pump_flywheel/flywheel", "mechanics", {"m": T("steel_dark_smooth"), "st": T("steel_bare"), "h": T("paint_red")},
      disc("rim", "y", 14, 6, 10, "m") + disc("web", "y", 11, 5.5, 10.5, "h") + disc("hub", "y", 3, 4, 14, "st", 8),
      "block/pump_flywheel/partial/flywheel", kind="partial")

# ---------------- piping ----------------
model("pipe", "piping", {"body": "block/pipe_primary.png", "d": T("steel_dark"), "end": T("pipe_end")}, [
    el("body", [4, 4, 1.5], [12, 12, 14.5], "body"),
    el("flange_n", [3, 3, 0], [13, 13, 1.5], "d", faces={"north": "end"}, full=["north"]),
    el("flange_s", [3, 3, 14.5], [13, 13, 16], "d", faces={"south": "end"}, full=["south"]),
], "block/pipe")


def valve(stem_top):
    return [
        el("pipe", [4, 4, 1.5], [12, 12, 14.5], "pipe"),
        el("flange_n", [3, 3, 0], [13, 13, 1.5], "d", faces={"north": "end"}, full=["north"]),
        el("flange_s", [3, 3, 14.5], [13, 13, 16], "d", faces={"south": "end"}, full=["south"]),
        el("body", [3, 2, 4], [13, 13, 12], "p"),
        el("bonnet", [5, 13, 6], [11, 15, 10], "d"),
        el("yoke_w", [5, 15, 7.5], [6, 21, 8.5], "d"),
        el("yoke_e", [10, 15, 7.5], [11, 21, 8.5], "d"),
        el("yoke_top", [5, 21, 7], [11, 22, 9], "d"),
        el("stem", [7.5, 15, 7.5], [8.5, stem_top, 8.5], "st"),
        el("hub", [7, 22, 7], [9, 23, 9], "red"),
        el("spoke_x", [3, 22.25, 7.75], [13, 22.75, 8.25], "red"),
        el("spoke_z", [7.75, 22.25, 3], [8.25, 22.75, 13], "red"),
    ] + ring_xz("wheel", 5.5, 1, 22, 23, "red")


vt = {"pipe": "block/pipe_primary.png", "end": T("pipe_end"), "d": T("steel_dark"), "p": T("steel_plate"),
      "st": T("steel_bare"), "red": T("paint_red")}
model("valve_gate_closed", "piping", vt, valve(23.5), "block/valve_gate_closed")
model("valve_gate_open", "piping", vt, valve(29), "block/valve_gate_open")

# ---------------- instruments / control ----------------
model("instrument_pressure", "instruments",
      {"dial": "block/instrument_dial.png", "d": T("steel_dark"), "st": T("steel_bare"), "b": T("brass")}, [
    el("mount_plate", [3, 1, 15], [13, 15, 16], "d"),
    el("case", [2.5, 3, 11], [13.5, 14, 15], "d"),
    el("dial_face", [3, 3.5, 10.9], [13, 13.5, 11], "dial", full=["north"]),
    el("bezel_t", [2.5, 13.5, 10.5], [13.5, 14, 11], "st"),
    el("bezel_b", [2.5, 3, 10.5], [13.5, 3.5, 11], "st"),
    el("bezel_w", [2.5, 3.5, 10.5], [3, 13.5, 11], "st"),
    el("bezel_e", [13, 3.5, 10.5], [13.5, 13.5, 11], "st"),
    el("socket", [7, 0, 12], [9, 3, 14], "b"),
], "block/instrument_pressure")

model("protection_cabinet", "control",
      {"c": T("cabinet"), "door": "block/protection_cabinet_door.png", "d": T("steel_dark"),
       "lr": T("lamp_red"), "ly": T("lamp_yellow"), "lg": T("lamp_green"), "r": T("rubber"), "h": T("hazard")}, [
    el("body", [0, 1, 1], [16, 16, 16], "c"),
    el("plinth", [0, 0, 0], [16, 1, 16], "h"),
    el("door", [1, 2, 0.5], [15, 15, 1], "door", full=["north"]),
    el("glow_lamp_red", [3, 12.5, 0], [5, 14.5, 0.5], "lr"),
    el("glow_lamp_yellow", [7, 12.5, 0], [9, 14.5, 0.5], "ly"),
    el("glow_lamp_green", [11, 12.5, 0], [13, 14.5, 0.5], "lg"),
    el("key_switch", [12.5, 7, 0], [13.5, 8, 0.5], "r"),
    el("handle", [13.5, 4, -0.5], [14.25, 9, 0.5], "d"),
], "block/protection_cabinet")

model("reactor_controller", "control",
      {"p": T("steel_plate"), "front": "block/reactor_controller_front.png", "d": T("steel_dark")}, [
    el("body", [0, 0, 1], [16, 16, 16], "p"),
    el("glow_panel", [0.5, 0.5, 0.5], [15.5, 15.5, 1], "front", full=["north"]),
    el("frame_t", [0, 15.5, 0], [16, 16, 1], "d"),
    el("frame_b", [0, 0, 0], [16, 0.5, 1], "d"),
    el("frame_w", [0, 0.5, 0], [0.5, 15.5, 1], "d"),
    el("frame_e", [15.5, 0.5, 0], [16, 15.5, 1], "d"),
], "block/reactor_controller")

# ---------------- items ----------------
model("fuel_assembly", "items", {"rods": "item/fuel_assembly_rods.png", "st": T("steel_bare"), "d": T("steel_dark")}, [
    el("bottom_nozzle", [5, 0, 5], [11, 2, 11], "d"),
    el("bundle", [5.5, 2, 5.5], [10.5, 14, 10.5], "rods", full=["north", "south", "east", "west"]),
    el("grid_1", [5.25, 5, 5.25], [10.75, 5.75, 10.75], "st"),
    el("grid_2", [5.25, 9, 5.25], [10.75, 9.75, 10.75], "st"),
    el("top_nozzle", [5, 14, 5], [11, 15.5, 11], "d"),
    el("handle", [7, 15.5, 7.5], [9, 16, 8.5], "st"),
], "item/fuel_assembly", kind="item")

