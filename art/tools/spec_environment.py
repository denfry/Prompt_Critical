"""14: табло биржи и товарная станция, пост наблюдения, газоочистка ТЭС (САКВ, газоходы, электрофильтр,
рукавный фильтр, сероочистка, SCR, ГГП), ликвидация разливов, загрязнённые блоки мира.
Сегменты 3×2×3: якорь — средний блок по X/Z (−16…32), по Y 0…32."""
from artkit2 import *

E = lambda n: f"block/common/env_{n}.png"
OWN = {"mkt": B("market_terminal_board"), "dep": B("trade_depot_door"), "lvw": E("louvre_white"), "flue": E("flue"),
       "gyp": E("gypsum"), "boom": E("boom"),
       **{k: B(k) for k in ("oiled_sand", "oiled_sand_1", "oiled_sand_2", "oiled_gravel", "oiled_gravel_1",
                            "oiled_gravel_2", "withered_leaves", "pond_liner")}}


def m(id_, cat, els, kind="block"):
    mk2(id_, cat, els, OWN, kind)


# ======================================================================
# экономика
# ======================================================================
def market(on):
    return [el("post", [6, 0, 10], [10, 8, 14], "rd"),
            el("board", [0, 8, 10], [16, 30, 15], "rd"),
            el(("glow_" if on else "") + "display", [1, 10, 9.9], [15, 29, 10], "mkt" if on else "pblk", full=["north"])]


m("market_terminal", "economy", market(False))
m("market_terminal_on", "economy", market(True))


def depot(pending):
    return [el("frame", [0, 0, 12], [16, 16, 16], "c"),
            el("door", [1, 0, 11.5], [15, 13, 12], "dg", faces={"north": "dep"}, full=["north"]),
            el("nozzle", [12, 13.5, 10], [14, 15.5, 12], "st"),
            el("floor_mark", [0, 0, 0], [16, 0.25, 11.5], "ye"),
            lamp("lamp_pending", 3, 14.5, 12, pending, "ly", 0.6)]


m("trade_depot", "economy", depot(False))
m("trade_depot_pending", "economy", depot(True))

# ======================================================================
# мониторинг и газоочистка
# ======================================================================
def aqs(on):
    return [el("plinth", [1, 0, 1], [15, 1, 15], "c"),
            el("booth", [2, 1, 2], [14, 14, 14], "ws", faces={"north": "lvw", "east": "lvw"}, full=["north", "east"]),
            el("roof", [1.5, 14, 1.5], [14.5, 15, 14.5], "ws"),
            el("mast", [12, 15, 12], [13, 30, 13], "gal"),
            lamp("lamp_on", 4, 12, 2, on, "lg", 0.5)]


m("air_quality_station", "environment", aqs(False))
m("air_quality_station_on", "environment", aqs(True))
m("air_quality_station/anemometer", "environment", vcyl("hub", 12.5, 12.5, 0.75, 29, 31, "st", 8)
  + [el(f"arm_{i}", [12.25, 30, 12.25], [15.5, 30.5, 12.75], "alu", rot=("y", a, [12.5, 30, 12.5]) if a else None)
     for i, a in enumerate((0, 45, -45))], kind="partial")


def cems(on=False, alarm=False):
    return [el("cabinet", [2, 2, 10], [14, 14, 16], "ral"),
            el("probe", [7, 7, 16], [9, 9, 16.01], "st"),
            lamp("lamp_on", 4, 12.5, 10, on, "lg", 0.5),
            lamp("lamp_alarm", 6, 12.5, 10, alarm, "lr", 0.5)]


m("cems_analyzer", "flue_gas", cems())
m("cems_analyzer_on", "flue_gas", cems(True))
m("cems_analyzer_alarm", "flue_gas", cems(True, True))

m("flue_duct", "flue_gas", [el("duct", [1, 1, 0], [15, 15, 16], "flue")]
  + [el(f"stiffener_{i}", [0.5, 0.5, z], [15.5, 15.5, z + 1], "d") for i, z in enumerate((3, 12))])
m("flue_duct_elbow", "flue_gas", [el("duct_z", [1, 1, 0], [15, 15, 15], "flue"), el("duct_x", [15, 1, 1], [16, 15, 15], "flue")])
m("flue_duct_tee", "flue_gas", [el("duct", [1, 1, 0], [15, 15, 16], "flue"), el("branch", [15, 1, 3], [16, 15, 13], "flue")])


def esp_seg(energized=False):
    els = [el("casing", [-16, 4, -15], [32, 32, 32], "r32"), el("roof", [-16, 31, -15], [32, 32, 32], "d"),
           el("sign_hv", [2, 14, -16], [14, 26, -15], "shv", full=["north"])]
    els += [el(f"hopper_{i}", [x, -16, -12], [x + 12, 4, 28], "r32") for i, x in enumerate((-14, 2, 18))]
    els += [el(f"insulator_house_{i}", [x, 32 - 0.01 - 4, 0], [x + 4, 31.99, 4], "r32") for i, x in enumerate((-8, 20))]
    if energized:
        els.append(el("glow_lamp_hv", [6, 28, -16], [10, 30, -15.5], "lr"))
    return els


m("electrostatic_precipitator", "flue_gas", esp_seg())
m("electrostatic_precipitator_energized", "flue_gas", esp_seg(True))
m("electrostatic_precipitator/rapper", "flue_gas", [el("hammer_arm", [7.5, 26, -14], [8.5, 30, -13], "d"),
                                                    el("hammer", [6.5, 24, -14.5], [9.5, 26, -12.5], "d")], kind="partial")


def baghouse(top=False, pulsing=False):
    if top:
        els = [el("plenum", [-16, 0, -15.4], [32, 10, 32], "gal")]
        els += [el(f"lid_{i}", [x, 10, -14], [x + 12, 11, 30], "gal") for i, x in enumerate((-14, 2, 18))]
        els += [el(f"pulse_valve_{i}", [x + 5, 11, -14], [x + 7, 14, -10], "dg") for i, x in enumerate((-14, 2, 18))]
        els += hcyl("header", "x", 2, -16, 32, "air", c=(8, 13, -16 + 4))
        if pulsing:
            els.append(lamp("lamp_pulse", 8, 6, -15.4, True, "lb", 0.8))
        return els
    els = [el("casing", [-16, 8, -16], [32, 32, 32], "gal")]
    els += [el(f"hopper_{i}", [x, 0, -12], [x + 12, 8, 28], "gal") for i, x in enumerate((-14, 2, 18))]
    els += [el(f"leg_{i}", [x, 0, z], [x + 2, 8, z + 2], "d") for i, (x, z) in enumerate(((-16, -16), (30, -16), (-16, 30), (30, 30)))]
    return els


m("baghouse_filter", "flue_gas", baghouse())
m("baghouse_filter_top", "flue_gas", baghouse(True))
m("baghouse_filter_top_pulsing", "flue_gas", baghouse(True, True))


def fgd(part="bottom"):
    if part == "bottom":
        return vcyl("oxidation_tank", 8, 8, 22, 0, 32, "gyp") + [el("agitator_motor", [-16, 10, 6], [-12, 16, 10], "mb")]
    if part == "middle":
        return vcyl("shell", 8, 8, 20, 0, 32, "gyp") + [x for i, y in enumerate((8, 20)) for x in
                                                       hcyl(f"spray_header_{i}", "x", 1.5, -16, -12, "wat", c=(8, y, 8), sides=8)]
    return vcyl("shell", 8, 8, 20, 0, 16, "gyp") + vcyl("outlet_cone", 8, 8, 12, 16, 22, "gyp") + \
        vcyl("outlet", 8, 8, 7, 22, 31.9, "flue")


m("fgd_absorber", "flue_gas", fgd())
m("fgd_absorber_middle", "flue_gas", fgd("middle"))
m("fgd_absorber_top", "flue_gas", fgd("top"))
m("fgd_absorber/agitator", "flue_gas", hcyl("shaft", "x", 0.75, -12, 4, "st", c=(8, 13, 8), sides=8)
  + [el("blade_1", [2, 9, 6], [4, 17, 10], "st"), el("blade_2", [2, 11, 4], [4, 15, 12], "st")], kind="partial")


def scr(top=False, layers=3):
    els = [el("casing", [-16, 0, -15], [32, 32, 32], "alu")]
    for i in range(3):
        els.append(el(f"layer_hatch_{i}", [-12, 4 + i * 9, -16], [-4, 10 + i * 9, -15], "d" if i < layers else "lr"))
    if top:
        els += [el("nh3_grid", [-14, 30, -14], [30, 31.99, 30], "grat")]
    return els


m("scr_reactor", "flue_gas", scr())
m("scr_reactor_top", "flue_gas", scr(True))
m("scr_reactor_empty", "flue_gas", scr(layers=0))

m("gas_reheater", "flue_gas", [el("housing_n", [-16, 0, -16], [32, 32, -12], "flue"),
                               el("housing_s", [-16, 0, 28], [32, 32, 32], "flue"),
                               el("sector_plate", [-16, 14, -12], [32, 18, 28], "d")])
m("gas_reheater/rotor", "flue_gas", vcyl("rotor", 8, 8, 18, 2, 30, "rad") + vcyl("shaft", 8, 8, 1.5, 0, 31.9, "st", 8),
  kind="partial")

# ======================================================================
# разливы
# ======================================================================
def boom(axis="z"):
    els = [el("float", [5, 0, 0], [11, 6, 16], "boom")] if axis == "z" else [el("float", [0, 0, 5], [16, 6, 11], "boom")]
    els += [el("skirt", [7.5, -6, 0], [8.5, 0, 16], "blk")] if axis == "z" else [el("skirt", [0, -6, 7.5], [16, 0, 8.5], "blk")]
    return els


m("oil_boom", "spill", boom())
m("oil_boom_x", "spill", boom("x"))
m("oil_boom_corner", "spill", [el("float_z", [5, 0, 0], [11, 6, 11], "boom"), el("float_x", [11, 0, 5], [16, 6, 11], "boom"),
                               el("skirt", [7.5, -6, 0], [8.5, 0, 8.5], "blk")])
m("boom_anchor", "spill", [el("block", [3, 0, 3], [13, 6, 13], "c"), el("eye", [7, 6, 7], [9, 8, 9], "st"),
                           el("rope", [7.75, 8, 7.75], [8.25, 16, 8.25], "rope")])


def skimmer(on):
    return [el("pontoon_w", [0, 0, 1], [3, 6, 15], "or"), el("pontoon_e", [13, 0, 1], [16, 6, 15], "or"),
            el("deck", [0, 6, 1], [16, 7, 15], "or"), el("sump", [4, 2, 10], [12, 6, 15], "dg"),
            el(("glow_" if on else "") + "lamp", [7.5, 7, 7.5], [8.5, 8, 8.5], "lg" if on else "loff")]


m("oil_skimmer", "spill", skimmer(False))
m("oil_skimmer_on", "spill", skimmer(True))
m("oil_skimmer/drum", "spill", hcyl("drum", "x", 3, 3, 13, "blk", c=(8, 4, 5)) + shaft("x", 0.75, 2, 14, c=(8, 4, 5)), kind="partial")


def washer(on):
    return [el("skid", [1, 0, 2], [15, 2, 14], "d"), el("pump", [2, 2, 3], [10, 9, 13], "rd"),
            el("heater", [10, 2, 4], [15, 12, 12], "ws"),
            el("hose_reel", [3, 9, 6], [9, 13, 10], "blk"),
            el(("glow_" if on else "") + "lamp", [12, 12, 7.5], [13, 13, 8.5], "lg" if on else "loff")]


m("shore_washer", "spill", washer(False))
m("shore_washer_on", "spill", washer(True))

for _b in ("oiled_sand", "oiled_gravel"):
    m(_b, "environment", [el("block", [0, 0, 0], [16, 16, 16], _b)])
    for _a in (1, 2):
        m(f"{_b}_{_a}", "environment", [el("block", [0, 0, 0], [16, 16, 16], f"{_b}_{_a}")])
m("withered_leaves", "environment", [el("block", [0, 0, 0], [16, 16, 16], "withered_leaves")])
m("pond_liner", "environment", [el("film", [0, 0, 0], [16, 1, 16], "pond_liner")])
