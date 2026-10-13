"""10: нефтехимия — трубчатые печи и закалка, адсорберы, газогенератор, хлор-щелочное производство,
полимеризация и переработка, брожение, карбамид. Высота 3 блока — якорь средний блок (y −16…32)."""
from artkit2 import *
from spec_metitems import tube

P2 = lambda n: f"block/common/petro_{n}.png"
OWN = {"coil": P2("scale_coil"), "fins": P2("fin_rows"), "rvt": P2("riveted"), "ena": P2("enamel"),
       "foam": P2("foam"), "pvc": P2("pvc_blue"), "oil": P2("oily"), "htr": P2("heater_bands"),
       "gph": P2("graphite"), "gviol": P2("glow_violet"), "bmsf": B("tube_furnace_controller_front")}


def m(id_, cat, els, kind="block"):
    mk2(id_, cat, els, OWN, kind)


# ======================================================================
# трубчатые печи
# ======================================================================
def radiant(catalyst=False, lit=False):
    els = [el("wall_n", [-16, -16, -16], [32, 32, -13], "ref"), el("wall_s", [-16, -16, 29], [32, 32, 32], "ref"),
           el("wall_w", [-16, -16, -13], [-13, 32, 29], "ref"), el("wall_e", [29, -16, -13], [32, 32, 29], "ref"),
           el("steel_band", [-16, -2, -16.0], [32, 0, -15], "d")]
    for i, x in enumerate((-4, 4, 12, 20)):
        if catalyst:
            els += vcyl(f"tube_{i}", x, 8, 1.5, -14, 31.9, "coil", 8)
        else:
            els.append(el(f"tube_{i}", [x - 1, -14, 7], [x + 1, 30, 9], "coil"))
    if not catalyst:
        els.append(el("return_bend", [-5, 28, 7], [21, 30, 9], "coil"))
    for i, x in enumerate((-8, 8, 24)):
        els.append(el(("glow_" if lit else "") + f"peep_{i}", [x - 2, 6, -16], [x + 2, 10, -15.5], "gfl" if lit else "gls"))
    return els


m("tube_furnace_radiant", "petrochem", radiant())
m("tube_furnace_radiant_lit", "petrochem", radiant(lit=True))
m("tube_furnace_radiant_catalyst", "petrochem", radiant(True))
m("tube_furnace_radiant_catalyst_lit", "petrochem", radiant(True, True))
m("tube_furnace_convection", "petrochem", [
    el("casing_n", [-16, -16, -16], [32, 32, -14], "st", faces={"north": "fins"}, full=["north"]),
    el("casing_s", [-16, -16, 30], [32, 32, 32], "st"),
    el("casing_w", [-16, -16, -14], [-14, 32, 30], "st"), el("casing_e", [30, -16, -14], [32, 32, 30], "st"),
    el("tube_bank", [-14, -10, -14], [30, 20, 30], "fins"),
])


def bms(state="cold"):
    col = {"cold": None, "hot": "ly", "cracking": "lg", "decoking": "lb", "tripped": "lr"}[state]
    els = cabinet("ral", "bmsf", z0=3)
    if col:
        els.append(lamp("lamp_state", 13, 14, 3, True, col, 0.7))
    return els


for _s in ("cold", "hot", "cracking", "decoking", "tripped"):
    m("tube_furnace_controller" + ("" if _s == "cold" else "_" + _s), "petrochem", bms(_s))

# ЗИА Borsig 1×3×1: толстостенный теплообменник, паровой барабан сверху
m("quench_exchanger", "petrochem", vcyl("exchanger", 8, 8, 5, -16, 20, "st") + vcyl("drum", 8, 8, 6.5, 20, 31.9, "st")
  + [el("steam_band", [1.4, 26, 1.4], [14.6, 28, 14.6], "steam")] + nozzle("riser", "x", 16, (8, 24, 8), 1.2, "steam"))

m("pyrolysis_quench_tower", "petrochem", [el("skirt", [-10, -16, -10], [26, -10, 26], "d")]
  + vcyl("shell", 8, 8, 16, -10, 31.9, "st")
  + nozzle("water_in", "x", 32, (8, 24, 8), 2, "wat") + nozzle("water_out", "x", -16, (8, -6, 8), 2, "wat"))


def adsorber(phase="adsorb"):
    els = vvessel("shell", 5.5, -16, 32, "sb")
    els += [el("arrow_up" if phase == "adsorb" else "arrow_down", [7, 0, 2.2], [9, 8, 2.4], "ws"),
            lamp("lamp_phase", 8, 12, 2.2, True, "lg" if phase == "adsorb" else "lo", 0.7)]
    return els


m("adsorber_vessel", "petrochem", adsorber())
m("adsorber_vessel_regen", "petrochem", adsorber("regen"))


def gasifier(lit=False):
    els = [el("base", [-16, -16, -16], [32, -12, 32], "c")] + vcyl("jacket", 8, 8, 18, -12, 32, "rvt")
    els += vcyl("gas_band", 8, 8, 18.3, 20, 23, "gasr") + [el("ash_door", [4, -8, -10.6], [12, -2, -10], "d")]
    els.append(el(("glow_" if lit else "") + "poke_hole", [6, 4, -10.6], [10, 8, -10], "gfl" if lit else "d"))
    return els


m("coal_gasifier", "petrochem", gasifier())
m("coal_gasifier_lit", "petrochem", gasifier(True))
m("coal_gasifier_top", "petrochem", vcyl("jacket", 8, 8, 18, -16, 4, "rvt") + vcyl("dome", 8, 8, 12, 4, 10, "rvt")
  + [el("charging_hopper", [2, 10, 2], [14, 20, 14], "d")] + vcyl("gas_off", 22, 8, 3, 4, 26, "gasr", 8))

# ======================================================================
# хлор-щелочное производство
# ======================================================================
m("chlor_alkali_end_plate", "chlor_alkali", [
    el("plate", [0, -16, 4], [16, 32, 12], "st"),
    el("bus_top", [2, 24, 0], [14, 28, 4], "cub"), el("bus_bot", [2, -12, 0], [14, -8, 4], "cub"),
    el("insulator_1", [1, -16, 12], [5, -12, 16], "por"), el("insulator_2", [11, -16, 12], [15, -12, 16], "por"),
])


def hcl_furnace(lit):
    return vcyl("shell", 8, 8, 6, -16, 28, "gph", top="gph") + vcyl("top", 8, 8, 3, 28, 31.9, "st", 8) + [
        el("peep_frame", [6, 2, 1.4], [10, 6, 2], "st"),
        el(("glow_" if lit else "") + "peep", [6.5, 2.5, 1.2], [9.5, 5.5, 1.4], "gviol" if lit else "gls")]


m("hcl_synthesis_furnace", "chlor_alkali", hcl_furnace(False))
m("hcl_synthesis_furnace_lit", "chlor_alkali", hcl_furnace(True))
m("chlorination_reactor", "chlor_alkali", legs(3, 13, 3, 13, 3, 1) + vcyl("shell", 8, 8, 5.5, 3, 28, "sils", top="sils")
  + [el("cutaway", [4, 8, 2.4], [8, 22, 2.5], "ref")] + vcyl("tox_ring", 8, 8, 5.8, 24, 25.5, "ye"))


def scrubber(active):
    return vcyl("sump", 8, 8, 6.5, -16, -6, "pvc") + vcyl("tower", 8, 8, 5, -6, 26, "pvc") + \
        vcyl("fan_housing", 8, 8, 6, 26, 31.9, "pvc") + nozzle("caustic", "x", 0, (8, 20, 8), 1, "alk") + [
        lamp("lamp_active", 8, 0, 3, active, "lg", 0.7)]


m("caustic_scrubber", "chlor_alkali", scrubber(False))
m("caustic_scrubber_active", "chlor_alkali", scrubber(True))
m("caustic_scrubber/fan", "chlor_alkali", fan_rotor("y", 5, 29, "pvc"), kind="partial")

# ======================================================================
# полимеры
# ======================================================================
def batch_reactor(relief=False):
    els = [el(f"leg_{i}", [x, -16, z], [x + 2, -6, z + 2], "d") for i, (x, z) in
           enumerate(((-8, -8), (22, -8), (-8, 22), (22, 22)))]
    els += vcyl("jacket", 8, 8, 15, -10, 18, "ena") + vcyl("head", 8, 8, 12, 18, 22, "ena")
    els += vcyl("motor", 8, 8, 3, 24, 31.9, "mb", 8) + [el("drive_stand", [4, 22, 4], [12, 24, 12], "d"),
                                                     el("manway", [14, 22, 0], [22, 23, 8], "st")]
    els += vcyl("rupture_disc", 0, 14, 1.6, 22, 24 if not relief else 23, "rd" if not relief else "soot", 8)
    return els


m("batch_reactor", "polymers", batch_reactor())
m("batch_reactor_relief", "polymers", batch_reactor(True))
m("batch_reactor/agitator", "polymers", vcyl("shaft", 8, 8, 0.75, -8, 24, "st", 8) + fan_rotor("y", 9, -6, "st"), kind="partial")
m("loop_reactor", "polymers", [el(f"leg_{i}", [x - 3, -16, 5], [x + 3, 32, 11], "gas") for i, x in enumerate((-10, 26))]
  + [el(f"jacket_{i}", [x - 3.5, -12, 4.5], [x + 3.5, 28, 11.5], "ws") for i, x in enumerate((-10, 26))]
  + [el("lower_bend", [-13, -16, 5], [29, -10, 11], "gas"),
     el("ring_1", [-13.6, 20, 4.4], [-6.4, 22, 11.6], "rd"), el("ring_2", [22.4, 20, 4.4], [29.6, 22, 11.6], "rd")])
m("loop_reactor_top", "polymers", [el(f"leg_{i}", [x - 3, -16, 5], [x + 3, 26, 11], "gas") for i, x in enumerate((-10, 26))]
  + [el("upper_bend", [-13, 26, 5], [29, 32, 11], "gas")])
m("loop_reactor/impeller", "polymers", hcyl("hub", "x", 1.5, 2, 14, "st", c=(8, -13, 8), sides=8)
  + [el("blade_1", [6, -16, 7], [10, -10, 9], "st"), el("blade_2", [6, -14, 5], [10, -12, 11], "st")], kind="partial")

m("high_pressure_autoclave", "polymers", vcyl("forging", 8, 8, 5.5, -16, 26, "oil") + vcyl("cap", 8, 8, 6, 26, 30, "d", 8)
  + [el("bunker_n", [0, -16, -0], [16, 10, 1.5], "c"), el("rupture_disc", [12, 28, 7], [15, 31, 9], "rd")]
  + vcyl("drive", 8, 8, 2, 30, 31.9, "mb", 8))
m("high_pressure_autoclave/agitator", "polymers", vcyl("shaft", 8, 8, 0.6, -14, 28, "st", 8) + fan_rotor("y", 4, -10, "st"),
  kind="partial")

m("hyper_compressor", "polymers", [el("frame", [-16, 0, 2], [32, 10, 14], "oil"),
                                   el("guard_w", [-14, 10, 3], [-2, 15, 13], "ye"), el("guard_e", [18, 10, 3], [30, 15, 13], "ye")]
  + hcyl("cyl_n", "z", 2.5, -0.0, 2, "sss", c=(-8, 6, 8), sides=8) + hcyl("cyl_s", "z", 2.5, 14, 16, "sss", c=(24, 6, 8), sides=8))
m("hyper_compressor/crankshaft", "polymers", shaft("x", 1.5, -16, 32, c=(8, 6, 8)), kind="partial")
m("hyper_compressor/plunger", "polymers", hcyl("plunger", "z", 1, 0, 6, "sss", c=(8, 6, 8), sides=8), kind="partial")

m("polymer_stripper", "polymers", legs(2, 14, 2, 14, 6, 1) + [el("cone", [4, 6, 4], [12, 10, 12], "st")]
  + vcyl("bin", 8, 8, 6.5, 10, 30, "st", top="d") + nozzle("steam", "x", 0, (8, 12, 8), 1, "steam"))


def extruder(die="pellets"):
    els = [el("base", [-16, 0, 2], [32, 5, 14], "d"), el("gearbox", [24, 5, 3], [32, 14, 13], "mb"),
           el("hopper", [14, 13, 4], [22, 18, 12], "st")]
    els += hcyl("barrel", "x", 3.5, -12, 24, "htr", c=(8, 9, 8), sides=8)
    if die == "pellets":
        els += [el("die_plate", [-14, 5, 3], [-12, 13, 13], "st"), el("cutter_hood", [-16, 5, 3], [-14, 13, 13], "dg")]
    elif die == "pipe":
        els += hcyl("die", "x", 4.5, -16, -12, "st", c=(8, 9, 8), sides=8) + hcyl("pipe_out", "x", 2.5, -16, -15, "dg", c=(8, 9, 8), sides=8)
    else:
        els += [el("sheet_die", [-16, 7, 1], [-12, 11, 15], "st")]
    return els


m("polymer_extruder", "polymers", extruder())
m("polymer_extruder_pipe", "polymers", extruder("pipe"))
m("polymer_extruder_sheet", "polymers", extruder("sheet"))
m("polymer_extruder/screw", "polymers", hcyl("screw", "x", 2.5, -12, 24, "sss", c=(8, 9, 8), sides=8), kind="partial")


def hot_press(hot=False):
    els = [el("base", [1, 0, 1], [15, 6, 15], "d"), el("crown", [1, 26, 1], [15, 31, 15], "d")]
    els += [el(f"column_{i}", [x, 6, z], [x + 2, 26, z + 2], "sss") for i, (x, z) in enumerate(((2, 2), (12, 2), (2, 12), (12, 12)))]
    els.append(el(("glow_" if hot else "") + "lower_platen", [3, 6, 3], [13, 8, 13], "hot" if hot else "st"))
    return els


m("hot_press", "polymers", hot_press())
m("hot_press_hot", "polymers", hot_press(True))
m("hot_press/platen", "polymers", [el("platen", [3, 14, 3], [13, 16, 13], "st"), el("ram", [6, 16, 6], [10, 26, 10], "sss")],
  kind="partial")


def vat(fermenting):
    els = [el("floor", [0, 0, 0], [32, 1, 32], "rvt"),
           el("wall_n", [0, 1, 0], [32, 28, 2], "rvt"), el("wall_s", [0, 1, 30], [32, 28, 32], "rvt"),
           el("wall_w", [0, 1, 2], [2, 28, 30], "rvt"), el("wall_e", [30, 1, 2], [32, 28, 30], "rvt"),
           el("mash", [2, 1, 2], [30, 22, 30], "fuel")]
    if fermenting:
        els.append(el("foam", [2, 22, 2], [30, 25, 30], "foam"))
    return els


m("fermentation_vat", "petrochem", vat(False))
m("fermentation_vat_fermenting", "petrochem", vat(True))
m("urea_reactor", "petrochem", vcyl("shell", 8, 8, 5.5, -16, 28, "st", top="d") + vcyl("top", 8, 8, 3, 28, 31.9, "d", 8)
  + nozzle("co2_in", "x", 0, (8, -10, 8), 1.2, "st") + nozzle("melt_out", "x", 16, (8, 20, 8), 1.2, "st"))
m("prilling_tower", "petrochem", vcyl("tower", 8, 8, 18, -16, 26, "c") + vcyl("top_house", 8, 8, 10, 26, 31.9, "c")
  + [el("fines_hood", [-12, -16, -12], [28, -12, 28], "c")])

# хлорный контейнер 800 л — объёмный предмет: защитный (хаки) цилиндр лёжа, жёлтая полоса
m("chlorine_cylinder", "items", hcyl("body", "x", 4, 1, 15, "kh", ends="kh")
  + hcyl("stripe", "x", 4.3, 7, 9, "ye", sides=8) + hcyl("valve_w", "x", 1, 0, 1, "b", sides=8)
  + hcyl("valve_e", "x", 1, 15, 16, "b", sides=8), kind="item")
