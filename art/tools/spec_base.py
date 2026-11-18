"""Reactor island, mechanics, piping, instruments (first batch)."""
from pclib import *

# ---------------- core ----------------
model("reactor_vessel_wall", "core", {"wall": "block/reactor_vessel_wall.png"},
      [el("wall", [0, 0, 0], [16, 16, 16], "wall")], "block/reactor_vessel_wall")

# стена гермооболочки нарисована вручную в Blockbench: art/blockbench/core/containment.author.js

# нарисовано вручную в Blockbench: art/blockbench/core/reactor_hall.author.js

# графитовая колонна с циркониевой трубой канала
# нарисовано вручную в Blockbench: art/blockbench/core/reactor_hall.author.js

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
# нарисовано вручную в Blockbench: art/blockbench/core/reactor_hall.author.js
model("control_rod_drive/winch_drum", "mechanics",
      {"st": T("steel_bare"), "b": T("brass_smooth")},
      disc("drum", "x", 4.5, 3.5, 12.5, "st") + disc("rim_w", "x", 5.5, 3, 4, "b") + disc("rim_e", "x", 5.5, 12, 13, "b"),
      "block/control_rod_drive/partial/winch_drum", kind="partial")
model("control_rod_drive/rod", "mechanics", {"a": T("absorber"), "st": T("steel_bare")}, [
    el("absorber", [6.5, 1, 6.5], [9.5, 15, 9.5], "a"),
    el("tip", [7, 0, 7], [9, 1, 9], "st"),
    el("head", [6, 15, 6], [10, 16, 10], "st"),
], "block/control_rod_drive/partial/rod", kind="partial")

# главный циркуляционный насос нарисован вручную в Blockbench: art/blockbench/mechanics/primary_pump.author.js

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
# нарисовано вручную в Blockbench: art/blockbench/control/panel_devices.author.js

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

# нарисовано вручную в Blockbench: art/blockbench/control/panel_devices.author.js

# ---------------- items ----------------
# нарисовано вручную в Blockbench: art/blockbench/items/items3d.author.js

