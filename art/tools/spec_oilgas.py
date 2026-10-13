"""09: нефть и газ — геология, разведка, буровая, добыча, транспорт и хранение, НПЗ, газопереработка.
Валы Create — по оси X (выход на west), pivot на оси вала. Крупные агрегаты: по оси размера 2
элементы 0…32 от якоря, по оси размера 3 якорь — средний блок (−16…32)."""
from artkit2 import *

O = lambda n: f"block/common/oil_{n}.png"
OWN = {
    "rs": B("reservoir_sandstone"), "rsg": B("reservoir_sandstone_gas"), "rsw": B("reservoir_sandstone_water"),
    "shl": B("cap_rock_shale"), "salt": B("rock_salt"), "seep": B("oil_seep"), "bar": B("barite_ore"),
    "bsand": B("bituminous_sand"), "osh": B("oil_shale"), "slick": B("oil_slick"),
    "deck": O("deck"), "rope": O("rope_drum"), "scrn": O("screen"), "bop": O("bop_red"), "frac": O("frac_red"),
    "tank": O("tank"), "ofin": O("fins"), "refg": O("refractory"), "sdrip": O("sulfur_drips"), "cfr": O("coke_frame"),
    "cdrum": O("drum"), "owood": O("wood"), "pgb": O("pump_grey_blue"), "ggr": O("green_grey"),
    "wind": B("drillers_console_indicator"), "accg": B("bop_accumulator_gauge"), "seis": B("seismic_recorder_face"),
    "espf": B("esp_control_panel_face"),
}


def m(id_, cat, els, kind="block"):
    mk2(id_, cat, els, OWN, kind)


def full(tex):
    return [el("block", [0, 0, 0], [16, 16, 16], tex)]


# ======================================================================
# геология
# ======================================================================
m("reservoir_sandstone", "oil_geology", full("rs"))
m("reservoir_sandstone_gas", "oil_geology", full("rsg"))
m("reservoir_sandstone_water", "oil_geology", full("rsw"))
m("cap_rock_shale", "oil_geology", full("shl"))
m("rock_salt", "oil_geology", full("salt"))
m("oil_seep", "oil_geology", [el("pool", [0, 0, 0], [16, 4, 16], "seep")])
m("barite_ore", "oil_geology", full("bar"))
m("bituminous_sand", "oil_geology", full("bsand"))
m("oil_shale", "oil_geology", full("osh"))
m("oil_slick", "oil_geology", [el("film", [0, 0, 0], [16, 0.5, 16], "slick")])

# ======================================================================
# сейсморазведка и каротаж
# ======================================================================
# дебалансный вибратор 2×1×2: опорная плита, корпус дебалансов; ротор — partial (ось X)
m("seismic_vibrator", "oil_exploration", [
    el("baseplate", [0, 0, 0], [32, 2, 32], "st"),
    el("frame_w", [2, 2, 8], [6, 12, 24], "ye"), el("frame_e", [26, 2, 8], [30, 12, 24], "ye"),
    el("housing", [6, 2, 6], [26, 13, 26], "bs"),
    el("top_beam", [2, 12, 12], [30, 14, 20], "ye"),
    el("isolator_1", [3, 14, 13], [6, 16, 19], "r"), el("isolator_2", [26, 14, 13], [29, 16, 19], "r"),
])
m("seismic_vibrator/eccentric_rotor", "oil_exploration", shaft("x", 1.25, 0, 32, c=(16, 8, 16))
  + [el("weight_1", [9, 8, 13], [13, 13, 19], "d"), el("weight_2", [19, 8, 13], [23, 13, 19], "d")], kind="partial")

m("geophone", "oil_exploration", [el("spike", [7.5, 0, 7.5], [8.5, 3, 8.5], "st"),
                                  el("case", [6, 3, 6], [10, 8, 10], "or"),
                                  el("connector", [10, 6, 7], [11.5, 7.5, 9], "dg")])


def recorder(on):
    return [el("case", [1, 0, 2], [15, 12, 15], "r32"),
            el("lid", [1, 12, 13], [15, 22, 15], "r32", faces={"north": "seis"}, full=["north"],
               rot=("x", -22.5, [8, 12, 14])),
            el("keyboard", [3, 12, 4], [13, 12.5, 10], "dg"),
            lamp("lamp_power", 13, 10, 2, on, "lg", 0.6)]


m("seismic_recorder", "oil_exploration", recorder(False))
m("seismic_recorder_on", "oil_exploration", recorder(True))

# каротажный подъёмник 3×2×2: синий кузов, барабан с кабелем (partial, ось X), счётчик глубины
m("logging_unit", "oil_exploration", [
    el("chassis", [-16, 0, 0], [32, 4, 32], "d"),
    el("cab", [16, 4, 2], [32, 22, 30], "blu"),
    el("cab_window", [31.9, 12, 6], [32, 19, 26], "gls"),
    el("drum_bay", [-16, 4, 2], [12, 26, 30], "blu"),
    el("bay_opening", [-14, 6, 1.5], [10, 24, 2], "dg"),
    el("depth_counter", [-4, 24, 0], [4, 28, 4], "dg"),
] + [el(f"wheel_{i}", [x, -0, -0.5], [x + 6, 5, 1.5], "r") for i, x in enumerate((-12, 20))])
m("logging_unit/cable_drum", "oil_exploration", drum("x", 6, -12, 8, "cbl", c=(8, 15, 16)), kind="partial")

def core_lab(busy):
    return [el("bench", [0, 9, 2], [32, 10, 15], "lab"), el("cabinet", [0.5, 0, 2.5], [31.5, 9, 14.5], "lab"),
            el("porosimeter", [3, 10, 6], [11, 17, 13], "ws"),
            el(("glow_" if busy else "") + "porosimeter_face", [4, 12, 5.9], [10, 16, 6], "scr" if busy else "pblk"),
            el("permeameter", [14, 10, 8], [22, 14, 13], "st")] +         vcyl("core_holder", 18, 6, 1.5, 10, 16, "sss", 8) + vcyl("he_bottle", 28, 10, 2, 10, 26, "kh", 8)


m("core_analysis_station", "oil_exploration", core_lab(False))
m("core_analysis_station_busy", "oil_exploration", core_lab(True))


# ======================================================================
# буровая установка
# ======================================================================
def derrick(part="middle", formed=True):
    """Сегмент вышки 3×3×3: четыре решётчатые ноги, сужение к верху, лестница; площадка верхового — middle."""
    taper = {"bottom": (-14, -10), "middle": (-10, -6), "top": (-6, -2)}[part]
    lo, hi = taper
    els = []
    for i, (sx, sz) in enumerate(((1, 1), (-1, 1), (1, -1), (-1, -1))):
        x0 = lo if sx > 0 else 16 - lo - 2
        z0 = lo if sz > 0 else 16 - lo - 2
        x1 = hi if sx > 0 else 16 - hi - 2
        z1 = hi if sz > 0 else 16 - hi - 2
        els.append(el(f"leg_{i}_lo", [x0, -16, z0], [x0 + 2, 8, z0 + 2], "ye"))
        els.append(el(f"leg_{i}_hi", [x1, 8, z1], [x1 + 2, 32, z1 + 2], "ye"))
    span_lo, span_hi = lo, 16 - lo
    els += [el("lattice_n", [span_lo + 2, -16, span_lo], [span_hi - 2, 32, span_lo + 0.5], "laty"),
            el("lattice_w", [span_lo, -16, span_lo + 2], [span_lo + 0.5, 32, span_hi - 2], "laty"),
            el("lattice_e", [span_hi - 0.5, -16, span_lo + 2], [span_hi, 32, span_hi - 2], "laty")]
    els += ladder("ladder", span_hi - 6, span_hi, -16, 32, "ye", side="south")
    if part == "middle":
        els += [el("monkey_board", [span_lo - 4, 14, 4], [span_lo + 2, 15, 12], "chk")]
    if part == "top":
        els += [el("water_table", [-6, 30, -6], [22, 32, 22], "ye")]
    if not formed:
        els = els[:8]
    return els


for _p in ("bottom", "middle", "top"):
    m("derrick_segment" + ("" if _p == "middle" else "_" + _p), "drilling", derrick(_p))
m("derrick_segment_unformed", "drilling", derrick("bottom", False))

m("crown_block", "drilling", [el("frame_w", [-16, 0, -8], [-12, 8, 24], "ye"), el("frame_e", [28, 0, -8], [32, 8, 24], "ye"),
                              el("beam_n", [-12, 6, -8], [28, 8, -4], "ye"), el("beam_s", [-12, 6, 20], [28, 8, 24], "ye"),
                              el("deck", [-16, 0, -16], [32, 1, -8], "chk")])
m("crown_block/sheaves", "drilling", shaft("x", 1, -12, 28) + [
    x for i, xx in enumerate((-8, -2, 4, 10, 16, 22)) for x in hcyl(f"sheave_{i}", "x", 5, xx, xx + 3, "st", sides=8)],
  kind="partial")


def floor(formed):
    els = [el("deck", [0, 12, 0], [16, 16, 16], "deck", faces={"up": "deck"})]
    els += [el("beam_w", [0, 0, 6], [2, 12, 10], "ye"), el("beam_e", [14, 0, 6], [16, 12, 10], "ye")] if formed else []
    els += [el("kick_n", [0, 16, 0], [16, 17, 1], "ye")]
    return els


m("drill_floor", "drilling", floor(False))
m("drill_floor_formed", "drilling", floor(True))

m("rotary_table", "drilling", [el("base", [0, 0, 0], [16, 12, 16], "dg"),
                               el("drive_housing", [12, 2, 4], [16, 10, 12], "dg")])
m("rotary_table/turntable", "drilling", vcyl("table", 8, 8, 7, 12, 15, "st") + [
    el("bushing", [5, 15, 5], [11, 15.5, 11], "d")], kind="partial")
m("rotary_table/traveling_block", "drilling", [
    el("block", [4, 4, 5], [12, 16, 11], "ye"), el("hook_shank", [7, 0, 7], [9, 4, 9], "st"),
    el("hook", [6, -3, 7], [10, 0, 9], "st")] + hcyl("sheaves", "x", 4, 4.5, 11.5, "st", c=(8, 11, 8), sides=8), kind="partial")
m("rotary_table/kelly", "drilling", [el("kelly", [6.5, 0, 6.5], [9.5, 32, 9.5], "st"),
                                     el("swivel", [5, 32 - 4, 5], [11, 32, 11], "ye")], kind="partial")
m("rotary_table/drill_stand", "drilling", vcyl("pipe", 8, 8, 1.5, 0, 31.9, "blu", 8)
  + vcyl("tool_joint", 8, 8, 2, 28, 31.9, "st", 8), kind="partial")
m("top_drive/motor", "drilling", [el("motor", [3, 6, 3], [13, 28, 13], "rd"),
                                  el("gearbox", [4, 0, 4], [12, 6, 12], "dg"),
                                  el("guide", [13, 4, 6], [16, 30, 10], "ye")] + vcyl("quill", 8, 8, 1.5, -6, 0, "st", 8),
  kind="partial")

# лебёдка ЛБУ-1200 3×2×2: жёлтый корпус; барабан — partial (ось X), ленточный тормоз — partial
m("drawworks", "drilling", [
    el("skid", [-16, 0, 0], [32, 2, 32], "d"),
    el("housing_w", [-16, 2, 2], [-10, 24, 30], "ye"), el("housing_e", [26, 2, 2], [32, 24, 30], "ye"),
    el("guard", [-10, 20, 18], [26, 26, 30], "ye"),
    el("motor", [26, 2, 30 - 12], [32, 14, 30], "mb"),
    el("brake_lever", [-10, 18, 2], [-8, 30, 4], "rd"),
])
m("drawworks/drum", "drilling", drum("x", 7, -10, 26, "rope", c=(8, 12, 14)), kind="partial")
m("drawworks/brake_band", "drilling", hcyl("band_w", "x", 9.5, -10, -8, "d", c=(8, 12, 14), sides=8)
  + hcyl("band_e", "x", 9.5, 24, 26, "d", c=(8, 12, 14), sides=8), kind="partial")


# буровой насос УНБ-600 3×2×2: красная рама, гидрочасть стальная, компенсаторы
def triplex(frame_tex="rd", fluid_tex="st", extra=()):
    return [el("skid", [-16, 0, 0], [32, 2, 32], "d"),
            el("power_end", [-14, 2, 8], [10, 18, 28], frame_tex),
            el("fluid_end", [10, 2, 6], [30, 14, 30], fluid_tex),
            el("discharge", [12, 14, 10], [28, 18, 14], fluid_tex)] + \
        vcyl("dampener_1", 16, 22, 2.5, 14, 24, "rd", 8) + vcyl("dampener_2", 24, 22, 2.5, 14, 22, "st", 8) + list(extra)


m("mud_pump", "drilling", triplex())
m("mud_pump/crankshaft", "drilling", shaft("x", 1.5, -16, 10, c=(8, 10, 18))
  + hcyl("bull_gear", "x", 6, -12, -10, "d", c=(8, 10, 18)), kind="partial")
m("mud_pump/piston", "drilling", hcyl("rod", "z", 1, 0, 10, "sss", c=(16, 8, 8), sides=8)
  + hcyl("crosshead", "z", 2, 0, 3, "d", c=(16, 8, 8), sides=8), kind="partial")


def mud_tank(level=0):
    els = [el("floor", [0, 0, 0], [16, 1, 16], "st"),
           el("wall_n", [0, 1, 0], [16, 14, 1], "st"), el("wall_s", [0, 1, 15], [16, 14, 16], "st"),
           el("wall_w", [0, 1, 1], [1, 14, 15], "st"), el("wall_e", [15, 1, 1], [16, 14, 15], "st"),
           el("grating", [0, 14, 0], [16, 15, 16], "grat")]
    els += railing(0, 16, 0, 16, 15)
    if level:
        els.append(el("mud", [1, 1, 1], [15, 1 + level * 1.8, 15], "pulp"))
    return els


m("mud_tank", "drilling", mud_tank(4))
m("mud_tank_empty", "drilling", mud_tank(0))
m("mud_tank_full", "drilling", mud_tank(7))
m("mud_tank/agitator", "drilling", vcyl("shaft", 8, 8, 0.75, 2, 18, "st", 8) + fan_rotor("y", 5, 3, "st"), kind="partial")

m("shale_shaker", "drilling", [
    el("base", [0, 0, 1], [32, 4, 15], "blu"),
    el("side_n", [2, 4, 1], [30, 12, 2], "blu"), el("side_s", [2, 4, 14], [30, 12, 15], "blu"),
    el("feeder", [24, 10, 2], [32, 16, 14], "blu"),
    el("chute", [-0, 2, 3], [4, 6, 13], "st"),
    el("motor_n", [12, 12, 0], [16, 16, 2], "mb"),
])
m("shale_shaker/screen_deck", "drilling", [el("screen", [3, 8, 2], [25, 9, 14], "scrn", rot=("z", -22.5, [14, 8.5, 8]))],
  kind="partial")

m("mud_degasser", "drilling", legs(2, 14, 2, 14, 4, 1) + vvessel("vessel", 5.5, 4, 28, "dg", skirt=False)
  + [el("vacuum_pump", [12, 0, 1], [16, 6, 7], "mb")] + nozzle("gas_vent", "x", 16, (8, 24, 8), 1, "gas"))
m("mud_mixing_hopper", "drilling", [el("stand", [3, 0, 3], [13, 6, 13], "st")]
  + [el("hopper", [2, 8, 2], [14, 14, 14], "st"), el("cone", [5, 6, 5], [11, 8, 11], "st")]
  + hcyl("eductor", "x", 1.5, 0, 16, "st", c=(8, 3, 8), sides=8)
  + [el("sack_1", [9, 14, 3], [13, 16, 8], "bei"), el("sack_2", [3, 14, 8], [8, 15.5, 13], "bei")])
m("mud_gas_separator", "drilling", vvessel("vessel", 5, -16, 26, "st")
  + nozzle("inlet", "z", 0, (8, -4, 8), 1.2, "st") + vcyl("vent_line", 8, 8, 1.2, 26, 31.9, "gasr", 8))


# превенторы
def bop_annular(closed):
    els = vcyl("lower_flange", 8, 8, 7.5, 0, 2, "d", 8)
    els += vcyl("body", 8, 8, 7, 2, 12, "bop") + vcyl("cap", 8, 8, 6, 12, 15, "bop") + vcyl("top_flange", 8, 8, 4, 15, 16, "d", 8)
    els += [el("hyd_open", [0, 4, 7], [1, 6, 9], "blk"), el("hyd_close", [0, 8, 7], [1, 10, 9], "blk")]
    if closed:
        els.append(el("closed_flag", [15, 10, 7], [16, 12, 9], "lr"))
    return els


m("bop_annular", "drilling", bop_annular(False))
m("bop_annular_closed", "drilling", bop_annular(True))


def bop_ram(ram="pipe", closed=False):
    mark = {"pipe": "ws", "blind": "ye", "shear": "rd"}[ram]
    stroke = 1.5 if closed else 0
    els = vcyl("bore_flange", 8, 8, 5, 0, 2, "d", 8) + vcyl("bore_flange_top", 8, 8, 5, 14, 16, "d", 8)
    els += [el("body", [2, 2, 3], [14, 14, 13], "bop"),
            el("bonnet_w", [-4 + stroke, 4, 4], [2, 12, 12], "bop"), el("bonnet_e", [14, 4, 4], [20 - stroke, 12, 12], "bop"),
            el("mark_w", [-4.2 + stroke, 6, 6], [-4 + stroke, 10, 10], mark), el("mark_e", [20 - stroke, 6, 6], [20.2 - stroke, 10, 10], mark)]
    return els


m("bop_ram", "drilling", bop_ram())
m("bop_ram_closed", "drilling", bop_ram(closed=True))
m("bop_ram_blind", "drilling", bop_ram("blind"))
m("bop_ram_shear", "drilling", bop_ram("shear"))

# установка управления ПВО 2×1×1: красные баллоны, пульт с рукоятками (partial), манометр 32×32
m("bop_accumulator", "drilling", [el("skid", [0, 0, 1], [32, 2, 15], "d"),
                                  el("panel", [18, 2, 1], [31, 15, 4], "rd"),
                                  el("gauge", [20, 9, 0.9], [26, 14.5, 1], "accg", full=["north"]),
                                  el("pump", [18, 2, 8], [30, 8, 14], "mb")]
  + [x for i, xx in enumerate((3, 8, 13)) for x in vcyl(f"bottle_{i}", xx, 8, 2.2, 2, 15, "rd", 8)])
m("bop_accumulator/levers", "drilling", [
    el(f"lever_{i}", [20 + i * 2.75, 4, 0], [20.75 + i * 2.75, 8, 1], "blk") for i in range(4)], kind="partial")


def manifold(width=32, chokes=True):
    els = [el("skid", [0, 0, 1], [width, 1.5, 15], "d")]
    els += hcyl("header", "x", 1.5, 0, width, "st", c=(8, 6, 8))
    for i, x in enumerate(range(4, width - 4, 8)):
        els += [el(f"gate_{i}", [x, 3, 6], [x + 4, 10, 10], "st"), el(f"bonnet_{i}", [x + 1, 10, 7], [x + 3, 12, 9], "dg")]
        els += vcyl(f"wheel_{i}", x + 2, 8, 2.2, 12, 12.6, "rd", 8)
    if chokes:
        els += [el("choke", [width - 8, 2, 1], [width - 3, 8, 6], "dg"),
                el("gauge_post", [2, 6, 2], [3, 14, 3], "st"), el("gauge", [0.5, 12, 0.5], [4.5, 16, 1.5], "gauge", full=["north"])]
    return els


m("choke_manifold", "drilling", manifold())
m("choke_manifold/handwheel", "drilling", hcyl("stem", "z", 0.5, 0, 2, "st", c=(26.5, 5, 0), sides=8)
  + hcyl("wheel", "z", 2.5, -0.5, 0, "rd", c=(26.5, 5, 0), sides=8), kind="partial")
m("kill_manifold", "drilling", manifold(16, False) + [el("check_valve", [10, 2, 10], [15, 6, 14], "st")])


def console(on):
    return [el("base", [1, 0, 3], [15, 14, 15], "r32"),
            el("panel", [0, 14, 2], [16, 30, 15], "r32", faces={"north": "wind"}, full=["north"]),
            el("hood", [0, 30, 1], [16, 32, 15], "r32"),
            el("handle_1", [3, 10, 1], [4, 14, 3], "blk"), el("handle_2", [12, 10, 1], [13, 14, 3], "blk")] + [
        lamp(f"lamp_{i}", 3 + i * 3.3, 12, 3, on, ("lg", "ly", "lr", "lw")[i], 0.6) for i in range(4)]


m("drillers_console", "drilling", console(False))
m("drillers_console_on", "drilling", console(True))


def capping(closed=False, sooty=False):
    t = "soot" if sooty else "bop"
    els = vcyl("connector", 8, 8, 6, 0, 4, "d", 8) + vcyl("ram_body", 8, 8, 6.5, 4, 20, t) + vcyl("spool", 8, 8, 4, 20, 28, t, 8)
    els += [el("lug_w", [0, 26, 7], [2, 31, 9], "d"), el("lug_e", [14, 26, 7], [16, 31, 9], "d"),
            el("bonnet_w", [-2 + (1 if closed else 0), 8, 5], [1.5, 16, 11], t),
            el("bonnet_e", [14.5, 8, 5], [18 - (1 if closed else 0), 16, 11], t)]
    return els


m("capping_stack", "drilling", capping())
m("capping_stack_closed", "drilling", capping(True))
m("capping_stack_sooty", "drilling", capping(sooty=True))


def pump_unit(body, tank):
    return [el("chassis", [-16, 0, 0], [32, 3, 32], "d"),
            el("engine", [16, 3, 4], [32, 20, 28], body),
            el("pump", [-6, 3, 6], [14, 16, 26], "st"),
            el("tank", [-16, 3, 2], [-6, 18, 30], tank)]


m("cementing_unit", "drilling", pump_unit("w", "blu"))
m("cementing_unit/pump_crank", "drilling", shaft("x", 1.5, -6, 14, c=(8, 10, 16)), kind="partial")
m("frac_pump", "drilling", pump_unit("frac", "frac"))
m("frac_pump/pump_crank", "drilling", shaft("x", 1.5, -6, 14, c=(8, 10, 16)), kind="partial")

# ударно-канатный станок Дрейка: нижний сегмент (балансир, лебёдка), верхний — деревянная вышка
m("cable_tool_rig", "drilling", [
    el("sill_n", [-16, 0, -16], [32, 2, -14], "owood"), el("sill_s", [-16, 0, 30], [32, 2, 32], "owood"),
    el("samson_post", [12, 2, 6], [16, 24, 10], "owood"),
    el("bull_wheel_post", [-14, 2, 4], [-12, 14, 12], "owood"),
] + derrick("bottom")[:8] + hcyl("bull_wheel", "z", 6, 2, 10, "owood", c=(-8, 12, 8), sides=8))
m("cable_tool_rig_derrick", "drilling", [el(f"leg_{i}", [x, -16, z], [x + 2, 32, z + 2], "owood")
                                         for i, (x, z) in enumerate(((-6, -6), (20, -6), (-6, 20), (20, 20)))]
  + [el(f"girt_{i}", [-6, y, -6], [22, y + 1, -5], "owood") for i, y in enumerate((-8, 4, 16, 28))])
m("cable_tool_rig/walking_beam", "drilling", [el("beam", [-14, 22, 7], [30, 25, 9], "owood"),
                                              el("horsehead", [-16, 18, 6.5], [-12, 26, 9.5], "owood")], kind="partial")

# ======================================================================
# добыча
# ======================================================================
m("wellhead", "oil_production", vcyl("casing_head", 8, 8, 6, 0, 8, "st") + vcyl("flange", 8, 8, 7, 8, 10, "d", 8)
  + vcyl("tubing_head", 8, 8, 4.5, 10, 16, "st", 8)
  + nozzle("outlet_w", "x", 0, (8, 5, 8), 1.4, "st") + nozzle("outlet_e", "x", 16, (8, 5, 8), 1.4, "st"))


def xmas(master_closed=False, wing_closed=False):
    els = vcyl("adapter", 8, 8, 4, 0, 3, "d", 8)
    els += [el("master_body", [5, 3, 5], [11, 10, 11], "st"), el("master_bonnet", [11, 5, 6], [14, 8, 10], "st"),
            el("cross", [5, 10, 5], [11, 16, 11], "st"), el("swab_body", [5, 16, 5], [11, 22, 11], "st"),
            el("top_cap", [6, 22, 6], [10, 25, 10], "d"), el("gauge", [6.5, 25, 6.5], [9.5, 28, 7], "gauge", full=["north"]),
            el("wing", [11, 11, 6.5], [16, 15, 9.5], "st"), el("wing_bonnet", [12, 15, 7], [15, 17, 9], "st")]
    els += hcyl("master_wheel", "x", 2.5, 14, 14.6, "rd", c=(8, 6.5, 8), sides=8)
    els += vcyl("wing_wheel", 13.5, 8, 2.5, 17, 17.6, "rd", 8)
    if master_closed:
        els.append(el("master_flag", [14.6, 6, 7.5], [15, 7, 8.5], "lr"))
    if wing_closed:
        els.append(el("wing_flag", [13, 17.6, 7.5], [14, 18, 8.5], "lr"))
    return els


m("christmas_tree", "oil_production", xmas())
m("christmas_tree_master_closed", "oil_production", xmas(master_closed=True))
m("christmas_tree_wing_closed", "oil_production", xmas(wing_closed=True))
m("christmas_tree/choke_handwheel", "oil_production", hcyl("wheel", "x", 2, 15.5, 16, "rd", c=(8, 19, 8), sides=8)
  + hcyl("stem", "x", 0.4, 14.5, 15.5, "st", c=(8, 19, 8), sides=8), kind="partial")

# станок-качалка СКД8: основание с редуктором и кривошипом, стойка, головка балансира
m("pumpjack_gearbox", "oil_production", [
    el("base", [-16, 0, -12], [32, 3, 28], "pgb"),
    el("gearbox", [0, 3, 0], [16, 16, 16], "pgb"),
    el("motor", [20, 3, 2], [30, 11, 12], "mb"),
    el("belt_guard", [16, 6, -2], [22, 14, 2], "ye"),
])
m("pumpjack_gearbox_samson", "oil_production", [
    el("leg_n", [4, -16, -10], [8, 28, -6], "pgb", rot=("x", 22.5, [6, -16, -8])),
    el("leg_s", [4, -16, 22], [8, 28, 26], "pgb", rot=("x", -22.5, [6, -16, 24])),
    el("saddle", [2, 28, 4], [10, 32, 12], "d"),
])
m("pumpjack_gearbox_head", "oil_production", [el("head", [-16, -8, 2], [-6, 16, 14], "pgb"),
                                              el("hanger", [-15, -16, 7], [-14, -8, 9], "rope")])
m("pumpjack_gearbox/crank_counterweight", "oil_production", [
    el("crank_n", [-8, 4, -2], [24, 12, 0], "pgb"), el("crank_s", [-8, 4, 16], [24, 12, 18], "pgb"),
    el("weight_n", [-14, 2, -3], [-4, 14, 0], "h"), el("weight_s", [-14, 2, 16], [-4, 14, 19], "h"),
] + shaft("z", 1.5, -4, 20), kind="partial")
m("pumpjack_gearbox/walking_beam", "oil_production", [el("beam", [-16, 6, 6], [32, 10, 10], "pgb"),
                                                      el("equalizer", [24, 4, 2], [28, 6, 14], "pgb")], kind="partial")
m("pumpjack_gearbox/pitman_arm", "oil_production", [el("arm", [7, -16, 7], [9, 16, 9], "pgb")], kind="partial")
m("pumpjack_gearbox/polished_rod", "oil_production", vcyl("rod", 8, 8, 0.5, 0, 16, "sss", 8)
  + [el("carrier_bar", [5, 15, 7], [11, 16, 9], "st")], kind="partial")


def pj_frame(formed):
    if not formed:
        return [el("channel_n", [0, 0, 2], [16, 4, 4], "pgb"), el("channel_s", [0, 0, 12], [16, 4, 14], "pgb"),
                el("cross", [6, 0, 4], [10, 3, 12], "pgb")]
    return [el("frame", [0, 0, 0], [16, 4, 16], "pgb"), el("anchor_1", [1, 4, 1], [3, 5, 3], "st"),
            el("anchor_2", [13, 4, 13], [15, 5, 15], "st")]


m("pumpjack_frame", "oil_production", pj_frame(False))
m("pumpjack_frame_formed", "oil_production", pj_frame(True))


def esp_panel(on):
    return cabinet("r32", z0=2, y1=31) + [
        el("face", [2, 12, 1.9], [14, 28, 2], "espf", full=["north"]),
        lamp("lamp_run", 12.5, 9, 2, on, "lg", 0.6)]


m("esp_control_panel", "oil_production", esp_panel(False))
m("esp_control_panel_on", "oil_production", esp_panel(True))


def hseparator(tex="sb", extra=()):
    els = hvessel("vessel", 10, -16, 32, tex, cy=12, cz=16)
    els += vcyl("inlet", -10, 16, 2, 20, 26, "st", 8) + vcyl("gas_out", 24, 16, 1.6, 20, 28, "gasr", 8)
    els += [el("oil_out", [8, 0, 14], [11, 4, 18], "fuel"), el("water_out", [18, 0, 14], [21, 4, 18], "wat"),
            el("level_glass", [0, 6, 5], [2, 18, 6.5], "gls")]
    return els + list(extra)


m("three_phase_separator", "oil_production", hseparator())


def desalter(powered):
    return hseparator("sb", [el("transformer", [0, 20, 10], [14, 30, 22], "r32"),
                             el("sign_hv", [-0.1, 22, 12], [0, 28, 20], "shv", faces={"west": "shv"}, full=["west"])]
                      + insulator_stack(powered))


def insulator_stack(powered):
    els = vcyl("bushing", 7, 16, 1.5, 30, 31.9, "por", 8)
    if powered:
        els.append(lamp("lamp_hv", 2, 29, 12, True, "lr", 0.7))
    return els


m("electrostatic_desalter", "oil_production", desalter(False))
m("electrostatic_desalter_powered", "oil_production", desalter(True))

# ======================================================================
# транспорт и хранение
# ======================================================================
m("centrifugal_compressor", "oil_transport", [
    el("skid", [-16, 0, 0], [32, 3, 32], "d"),
    el("casing_lower", [-10, 3, 6], [22, 13, 26], "pgb"),
    el("casing_upper", [-10, 13, 6], [22, 22, 26], "pgb"),
    el("split_flange", [-11, 12.5, 5], [23, 13.5, 27], "d"),
    el("bearing_w", [-14, 6, 12], [-10, 18, 20], "dg"),
] + vcyl("suction", 0, 16, 3, 22, 31.9, "gasr", 8) + vcyl("discharge", 14, 16, 2.5, 22, 30, "gasr", 8))
m("centrifugal_compressor/rotor", "oil_transport", shaft("x", 1.25, -16, -10, c=(8, 12, 16))
  + hcyl("coupling", "x", 2.5, -16, -14, "d", c=(8, 12, 16), sides=8), kind="partial")

m("reciprocating_compressor", "oil_transport", [
    el("frame", [0, 0, 8], [32, 14, 24], "ggr"),
    el("cyl_n", [8, 6, -0], [20, 16, 8], "ggr"), el("cyl_s", [8, 6, 24], [20, 16, 32], "ggr"),
] + vcyl("bottle_n", 14, 3, 3, 16, 30, "ggr", 8) + vcyl("bottle_s", 14, 29, 3, 16, 30, "ggr", 8))
m("reciprocating_compressor/crankshaft", "oil_transport", shaft("x", 1.25, -0, 32, c=(16, 8, 16))
  + hcyl("flywheel", "x", 6, 0, 3, "ci", c=(16, 8, 16)), kind="partial")
m("reciprocating_compressor/pistons", "oil_transport", hcyl("rod", "z", 1, 4, 28, "sss", c=(14, 11, 16), sides=8),
  kind="partial")


def tank_shell(formed=False, edge=False, ladder_=False):
    els = [el("shell", [0, 0, 12], [16, 16, 16], "tank")]
    if edge:
        els.append(el("wind_girder", [0, 14, 10], [16, 15, 12], "tank"))
    if ladder_:
        els += ladder("ladder", 6, 12, 0, 16, "ye", side="south")
    if not formed:
        els.append(el("bracket", [6, 0, 10], [10, 2, 12], "d"))
    return els


m("tank_shell", "oil_transport", tank_shell())
m("tank_shell_formed", "oil_transport", tank_shell(True))
m("tank_shell_edge", "oil_transport", tank_shell(True, True))
m("tank_shell_ladder", "oil_transport", tank_shell(True, ladder_=True))
m("tank_roof", "oil_transport", [el("roof", [0, 0, 0], [16, 2, 16], "tank")])
m("tank_roof_formed", "oil_transport", [el("roof", [0, 0, 0], [16, 2, 16], "tank"),
                                        el("rafter", [7, 2, 0], [9, 3, 16], "tank")])
m("tank_roof_vent", "oil_transport", [el("roof", [0, 0, 0], [16, 2, 16], "tank")]
  + vcyl("breather", 8, 8, 2.5, 2, 6, "st", 8) + [el("cap", [4.5, 6, 4.5], [11.5, 7.5, 11.5], "dg")])
m("tank_gauge", "oil_transport", [el("mount", [5, 0, 5], [11, 2, 11], "d"),
                                  el("radar_head", [6, 2, 6], [10, 7, 10], "ws"),
                                  el("junction", [10, 3, 7], [12, 6, 9], "dg")])
m("tank_gauge/floating_roof", "oil_transport", [el("pontoon", [0, 0, 0], [16, 2, 16], "dg"),
                                                el("seal", [0, 2, 0], [16, 2.5, 1], "r")], kind="partial")

m("gas_storage_bullet", "oil_transport", hvessel("vessel", 9, -16, 32, "ws", cy=11, cz=16, heads="ws")
  + [el("stripe", [-6, 19.5, 15], [22, 20.2, 17], "gasr")]
  + vcyl("psv_1", 0, 16, 1, 20, 26, "rd", 8) + vcyl("psv_2", 16, 16, 1, 20, 26, "rd", 8))

m("odorizer", "oil_transport", cabinet("r32", z0=6, y1=14) + vcyl("odorant_tank", 8, 3.5, 3, 0, 12, "st", 8)
  + vcyl("tox_ring", 8, 3.5, 3.3, 6, 7.5, "ye", 8))


def pig_trap(door_open=False):
    els = [el("support_w", [-14, 0, 6], [-10, 6, 10], "d"), el("support_e", [20, 0, 6], [24, 6, 10], "d")]
    els += hcyl("barrel", "x", 5, -12, 28, "r32", c=(8, 10, 8)) + hcyl("reducer", "x", 3.5, 28, 32, "r32", c=(8, 10, 8))
    els += hcyl("closure_hub", "x", 6, -16, -12, "d", c=(8, 10, 8), sides=8)
    els += [el("gauge", [0, 15, 7], [3, 18, 7.5], "gauge", full=["north"])]
    return els


m("pig_launcher", "oil_transport", pig_trap())
m("pig_receiver", "oil_transport", pig_trap())
for _p in ("pig_launcher", "pig_receiver"):
    m(f"{_p}/door", "oil_transport", hcyl("door", "x", 5.5, -16.0 + 0.1, -15, "dg", c=(8, 10, 8), sides=8)
      + [el("handle", [-16, 9, 3], [-15.5, 11, 13], "st")], kind="partial")


def cp_rect(on):
    return cabinet("r32", z0=3, y1=30) + [el("vent", [3, 4, 2.9], [13, 10, 3], "vent"),
                                          lamp("lamp_on", 8, 26, 3, on, "lg", 0.7)]


m("cp_rectifier", "oil_transport", cp_rect(False))
m("cp_rectifier_on", "oil_transport", cp_rect(True))
m("cp_test_post", "oil_transport", [el("post", [6, 0, 6], [10, 12, 10], "ye"),
                                    el("cap", [5.5, 12, 5.5], [10.5, 13, 10.5], "ye"),
                                    el("number_plate", [6.5, 8, 5.9], [9.5, 11, 6], "ws")])
m("lng_tank", "oil_transport", vcyl("outer_tank", 8, 8, 23, -16, 26, "ws") + vcyl("dome", 8, 8, 18, 26, 31.9, "ws")
  + [el("stair", [30, -16, 6], [32, 26, 10], "gal")])

# ======================================================================
# НПЗ
# ======================================================================
def column_base(large=False, tex="sb"):
    if large:
        return [el("skirt", [-12, -16, -12], [28, -6, 28], "d"),
                el("access_hatch", [4, -14, -12.5], [12, -8, -12], "dg")] + vcyl("shell", 8, 8, 20, -6, 32, tex) + \
            nozzle("feed", "x", -16, (8, 4, 8), 2, "fuel") + nozzle("bottoms", "z", -16, (8, -2, 8), 2, "fuel")
    return vvessel("shell", 6, 0, 32, tex) + [el("manway", [6, 6, 1.4], [10, 10, 2.2], "d")] + \
        nozzle("bottoms", "z", 0, (8, 4, 8), 1.2, "fuel") + nozzle("feed", "x", 16, (8, 18, 8), 1.2, "fuel")


def column_top(large=False, tex="sb"):
    if large:
        return vcyl("shell", 8, 8, 20, -16, 6, tex) + vcyl("head", 8, 8, 16, 6, 12, tex) + \
            vcyl("vapour_line", 8, 8, 3, 12, 31.9, "fuel", 8) + vcyl("psv", 20, 8, 1.2, 8, 16, "rd", 8)
    return vcyl("shell", 8, 8, 6, 0, 8, tex) + vcyl("head", 8, 8, 4.5, 8, 11, tex) + \
        vcyl("vapour_line", 8, 8, 1.5, 11, 16, "fuel", 8) + vcyl("psv", 12, 8, 0.75, 9, 14, "rd", 8)


m("column_base", "refinery", column_base())
m("column_base_large", "refinery", column_base(True))
m("column_base_insulated", "refinery", column_base(tex="clad"))
m("column_top", "refinery", column_top())
m("column_top_large", "refinery", column_top(True))


def hx(length=32, tex="sb", r=5, channel="dg"):
    """Горизонтальный кожухотрубный аппарат длиной length вдоль X."""
    x0 = 0 if length == 32 else -16
    x1 = 32
    els = hvessel("shell", r, x0 + 4, x1, tex, cy=r + 2)
    els += hcyl("channel", "x", r + 0.5, x0, x0 + 4, channel, c=(8, r + 2, 8), sides=8)
    els += vcyl("nozzle_in", x0 + 8, 8, 1.2, 2 * r + 2, 2 * r + 4, "st", 8)
    els += vcyl("nozzle_out", x1 - 6, 8, 1.2, 2 * r + 2, 2 * r + 4, "st", 8)
    return els


m("column_reboiler", "refinery", hx(32, "sb", 6) + vcyl("steam_in", 6, 8, 1.2, 14, 16, "steam", 8))
m("reflux_drum", "refinery", hvessel("drum", 6, 0, 32, "sb", cy=8) + [el("sight_glass", [14, 5, 1.6], [18, 11, 2.2], "gls")])


def air_cooler(running=False):
    return [el(f"leg_{i}", [x, -16, z], [x + 2, 8, z + 2], "d") for i, (x, z) in
            enumerate(((-16, -16), (30, -16), (-16, 30), (30, 30)))] + [
        el("plenum", [-14, 4, -14], [30, 10, 30], "gal"),
        el("bundle", [-16, 10, -16], [32, 14, 32], "ofin"),
        el("header_w", [-16, 9, -16], [-12, 15, 32], "st"),
        el("header_e", [28, 9, -16], [32, 15, 32], "st")] + vcyl("fan_ring", 8, 8, 12, 0, 4, "gal")


m("air_cooler", "refinery", air_cooler())
m("air_cooler/fan", "refinery", fan_rotor("y", 11, 2, "gal") + shaft("y", 1, -8, 2), kind="partial")
m("shell_tube_exchanger", "refinery", [el("support_w", [-12, 0, 4], [-10, 3, 12], "d"), el("support_e", [24, 0, 4], [26, 3, 12], "d")]
  + hcyl("shell", "x", 5.5, -12, 28, "sb", c=(8, 8, 8), ends="sbs") + hcyl("channel", "x", 6, -16, -12, "dg", c=(8, 8, 8), sides=8)
  + vcyl("n1", -6, 8, 1.2, 13.5, 16, "st", 8) + vcyl("n2", 20, 8, 1.2, 13.5, 16, "st", 8))
m("steam_ejector", "refinery", hcyl("diffuser", "x", 2.5, 8, 32, "st", c=(8, 8, 8), sides=8)
  + hcyl("suction_chamber", "x", 3.5, 2, 8, "st", c=(8, 8, 8), sides=8)
  + hcyl("steam_nozzle", "x", 1.2, 0, 2, "steam", c=(8, 8, 8), sides=8) + vcyl("suction", 5, 8, 1.5, 11.5, 16, "st", 8))


def fixed_bed(large=False, mode="run"):
    if large:
        els = [el("skirt", [-10, -16, -10], [26, -10, 26], "d")] + vcyl("shell", 8, 8, 16, -10, 32, "clad")
        els += [el(f"thermowell_{i}", [8 - 1, y, -9], [8 + 1, y + 1, -8], "st") for i, y in enumerate((-4, 6, 16, 26))]
    else:
        els = vvessel("shell", 6, 0, 48 - 16, "clad")
        els += [el(f"thermowell_{i}", [7.5, y, 1.2], [8.5, y + 1, 2.2], "st") for i, y in enumerate((8, 16, 24))]
    if mode == "regen":
        els.append(lamp("lamp_regen", 13, 4, 2, True, "lo", 0.7) if not large else lamp("lamp_regen", 20, -12, -10, True, "lo", 1))
    return els


m("fixed_bed_reactor", "refinery", fixed_bed())
m("fixed_bed_reactor_regen", "refinery", fixed_bed(mode="regen"))
m("fixed_bed_reactor_large", "refinery", fixed_bed(True))
m("fcc_riser", "refinery", vcyl("riser", 8, 8, 4, 0, 16, "refg", 8) + vcyl("bellows", 8, 8, 4.6, 6, 9, "st", 8))
m("fcc_reactor_vessel", "refinery", [el("structure", [-16, -16, -16], [32, -12, 32], "cfr")]
  + vcyl("shell", 8, 8, 20, -12, 24, "refg") + vcyl("head", 8, 8, 14, 24, 30, "refg")
  + vcyl("cyclone_outlet", 8, 8, 3, 30, 31.9, "st", 8))
m("fcc_reactor_vessel_unformed", "refinery", [el("structure", [-16, -16, -16], [32, -12, 32], "cfr")]
  + vcyl("cyclone_1", 0, 8, 3, -8, 20, "st", 8) + vcyl("cyclone_2", 16, 8, 3, -8, 20, "st", 8)
  + [el("cutaway_w", [-12, -12, -12], [-11, 24, 28], "refg"), el("cutaway_s", [-12, -12, 27], [28, 24, 28], "refg")])
m("fcc_regenerator", "refinery", [el("skirt", [-14, -16, -14], [30, -10, 30], "d")] + vcyl("shell", 8, 8, 22, -10, 32, "refg"))
m("fcc_regenerator_top", "refinery", vcyl("shell", 8, 8, 22, -16, 8, "refg") + vcyl("head", 8, 8, 16, 8, 16, "refg")
  + vcyl("flue", 8, 8, 4, 16, 31.9, "st", 8))


def slide(opening=0):
    return hcyl("pipe", "z", 4, 0, 16, "refg") + [el("body", [3, 3, 5], [16, 13, 11], "st"),
                                                  el("bonnet", [16, 5, 6], [24, 11, 10], "st"),
                                                  el("actuator", [24, 4, 5], [32, 12, 11], "ye"),
                                                  el("position", [24, 12, 7], [24 + opening * 2, 13, 9], "lg")]


m("slide_valve", "refinery", slide(2))
for _o in (0, 4):
    m(f"slide_valve_open{_o}", "refinery", slide(_o))
m("slide_valve/stem", "refinery", hcyl("stem", "x", 0.75, 12, 24, "sss", c=(8, 8, 8), sides=8)
  + [el("disc", [3, 4, 6.5], [12, 12, 9.5], "st")], kind="partial")


def claus(lit):
    els = [el("support_w", [-14, 0, 10], [-10, 6, 22], "d"), el("support_e", [22, 0, 10], [26, 6, 22], "d")]
    els += hcyl("furnace", "x", 9, -12, 14, "refg", c=(8, 14, 16)) + hcyl("waste_heat_boiler", "x", 7, 14, 32, "st", c=(8, 12, 16))
    els += hcyl("burner", "x", 3, -16, -12, "dg", c=(8, 14, 16), sides=8)
    els += [el("peep", [-6, 19, 6.6], [-2, 23, 7.5], "st"),
            el("glow_peep" if lit else "peep_glass", [-5.5, 19.5, 6.4], [-2.5, 22.5, 6.6], "gfl" if lit else "gls")]
    return els


m("claus_reaction_furnace", "refinery", claus(False))
m("claus_reaction_furnace_lit", "refinery", claus(True))
m("sulfur_condenser", "refinery", hvessel("shell", 5, 0, 32, "sdrip", cy=7) + vcyl("rundown", 26, 8, 1, 0, 2, "sul", 8))


def sulfur_pit(level=2):
    els = [el("floor", [-16, 0, -16], [32, 1, 32], "c"),
           el("wall_n", [-16, 1, -16], [32, 16, -14], "c"), el("wall_s", [-16, 1, 30], [32, 16, 32], "c"),
           el("wall_w", [-16, 1, -14], [-14, 16, 30], "c"), el("wall_e", [30, 1, -14], [32, 16, 30], "c"),
           el("cover", [-14, 15, -14], [6, 16, 30], "grat")]
    if level:
        els.append(el("molten_sulfur", [-14, 1, -14], [30, 1 + level * 4, 30], "gsul"))
    return els


m("sulfur_pit", "refinery", sulfur_pit())
m("sulfur_pit_empty", "refinery", sulfur_pit(0))
m("sulfur_pit_full", "refinery", sulfur_pit(3))
m("sulfur_granulator", "refinery", [el("base", [0, 0, 2], [32, 4, 30], "d"),
                                    el("tyre_frame_w", [4, 4, 4], [8, 10, 28], "d"), el("tyre_frame_e", [24, 4, 4], [28, 10, 28], "d"),
                                    el("feed_chute", [26, 20, 12], [32, 30, 20], "sul"),
                                    el("dust_hood", [0, 26, 6], [26, 32, 26], "sul")])
m("sulfur_granulator/drum", "refinery", hcyl("drum", "x", 9, 0, 28, "sul", c=(16, 16, 16))
  + hcyl("tyre_w", "x", 10, 5, 7, "st", c=(16, 16, 16), sides=8) + hcyl("tyre_e", "x", 10, 25, 27, "st", c=(16, 16, 16), sides=8),
  kind="partial")

m("coke_drum", "refinery", [el(f"column_{i}", [x, -16, z], [x + 2, 32, z + 2], "cfr")
                            for i, (x, z) in enumerate(((-16, -16), (30, -16), (-16, 30), (30, 30)))]
  + vcyl("drum", 8, 8, 18, -12, 32, "cdrum") + vcyl("cone", 8, 8, 10, -16, -12, "cdrum", 8))
m("coke_drum_top", "refinery", vcyl("drum", 8, 8, 18, -16, 6, "cdrum") + vcyl("head", 8, 8, 12, 6, 10, "cdrum")
  + [el("deck", [-16, 10, -16], [32, 11, 32], "grat")] + derrick("top")[:8])
m("coke_drum/top_deheader", "refinery", vcyl("lid", 8, 8, 6, 10, 12, "st", 8) + [el("hinge", [14, 10, 6], [16, 13, 10], "d")],
  kind="partial")
m("coke_drum/bottom_deheader", "refinery", vcyl("slide_plate", 8, 8, 6, -16, -14, "st", 8), kind="partial")
m("coke_drum/cutting_stem", "refinery", vcyl("stem", 8, 8, 1, -8, 31.9, "sss", 8) + vcyl("tool", 8, 8, 2.5, -12, -8, "dg", 8),
  kind="partial")

m("dewaxing_chiller", "refinery", [el("tank", [-16, 0, 4], [32, 12, 28], "st"),
                                   el("hood", [-14, 12, 4], [30, 26, 28], "fro"),
                                   el("chiller_pipes", [-16, 2, 0], [32, 6, 4], "fro")])
m("dewaxing_chiller/drum_filter", "refinery", hcyl("drum", "x", 9, -14, 30, "scrn", c=(8, 12, 16)) + shaft("x", 1, -16, 32, c=(8, 12, 16)),
  kind="partial")


def quality_lab(busy):
    return [el("bench", [0, 9, 2], [32, 10, 15], "lab"), el("cabinet", [0.5, 0, 2.5], [31.5, 9, 14.5], "lab"),
            el("cfr_engine", [2, 10, 4], [12, 22, 14], "rd"), el("cfr_head", [3, 22, 5], [11, 25, 13], "dg"),
            el("still_mantle", [18, 10, 6], [24, 14, 12], "dg"), el("still_flask", [19, 14, 7], [23, 18, 11], "gls"),
            el("condenser", [23, 17, 8.5], [31, 18, 9.5], "gls"),
            lamp("lamp_busy", 16, 20, 2, busy, "lg", 0.6)]


m("quality_lab", "refinery", quality_lab(False))
m("quality_lab_busy", "refinery", quality_lab(True))
m("quality_lab/cfr_flywheel", "refinery", hcyl("flywheel", "z", 4, 2, 3.5, "ci", c=(7, 15, 8)), kind="partial")

m("bitumen_oxidizer", "refinery", vvessel("shell", 6, 0, 32, "soot", head="soot"))
m("bitumen_oxidizer_top", "refinery", vcyl("shell", 8, 8, 6, 0, 26, "soot", top="soot") + vcyl("vent", 8, 8, 1.5, 26, 31.9, "st", 8))
m("hf_alkylation_reactor", "refinery", [el("skirt", [2, 0, 2], [30, 4, 30], "d")] + vcyl("shell", 16, 16, 12, 4, 31.9, "sils", top="sils")
  + vcyl("tox_ring_1", 16, 16, 12.3, 14, 16, "ye") + vcyl("tox_ring_2", 16, 16, 12.3, 26, 28, "ye"))
m("acid_settler", "refinery", hvessel("settler", 6, -16, 32, "sils", cy=8) + nozzle("acid_out", "z", 0, (-8, 3, 8), 1.2, "acid")
  + [el("acid_line", [-16, 1, 1], [32, 3, 3], "acid")])

# ======================================================================
# газопереработка
# ======================================================================
def glycol_regen(running):
    return [el("skid", [-16, 0, 0], [32, 2, 16], "d"),
            el("firebox", [-14, 2, 2], [-2, 12, 14], "refg"),
            el("glow_burner" if running else "burner_port", [-8, 3, 1.4], [-4, 7, 2], "gfl" if running else "dg")] +         hcyl("reboiler", "x", 5, -2, 30, "sb", c=(8, 8, 8)) + vcyl("still_column", 4, 8, 2.5, 13, 31.9, "sb", 8)


m("glycol_regenerator", "gas_processing", glycol_regen(False))
m("glycol_regenerator_running", "gas_processing", glycol_regen(True))


def cold_box(cold):
    t = "fro" if cold else "st"
    return [el("box", [-16, -16, -15], [32, 32, 32], "st"),
            el("sign", [0, 10, -16], [16, 26, -15], "swarn", faces={"north": "swarn"}, full=["north"])] + \
        nozzle("n1", "x", 32, (8, 0, 0), 2, t) + nozzle("n2", "x", 32, (8, 0, 16), 2, t) + nozzle("n3", "x", -16, (8, 16, 8), 2, t)


m("cold_box", "gas_processing", cold_box(False))
m("cold_box_cold", "gas_processing", cold_box(True))
m("turboexpander", "gas_processing", [el("skid", [0, 0, 0], [32, 2, 16], "d"),
                                      el("expander", [14, 2, 3], [28, 14, 13], "sils"),
                                      el("compressor", [2, 2, 3], [14, 12, 13], "sils")]
  + vcyl("inlet", 21, 8, 2, 14, 16, "fro", 8))
m("turboexpander/rotor", "gas_processing", shaft("x", 1, -0.01, 28, c=(8, 8, 8)), kind="partial")


def he_purifier(cycle="a"):
    return [el("skid", [0, 0, 0], [32, 2, 16], "d")] + vvessel("adsorber_a", 4, 2, 30, "sb", 6, 8, skirt=False) + \
        vvessel("adsorber_b", 4, 2, 30, "sb", 16, 8, skirt=False) + \
        [x for i, xx in enumerate((24, 28)) for x in vcyl(f"he_bottle_{i}", xx, 8, 1.6, 2, 24, "bei", 8)] + [
        lamp("lamp_a", 6, 28, 3.5, cycle == "a", "lg", 0.6), lamp("lamp_b", 16, 28, 3.5, cycle == "b", "lg", 0.6)]


m("helium_purifier", "gas_processing", he_purifier("a"))
m("helium_purifier_b", "gas_processing", he_purifier("b"))
