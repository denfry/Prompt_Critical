"""07 после M3: автотрансформатор 220 кВ, БСК, щит ОДУ, АКБ и ВАЗП, ГТУ и котёл-утилизатор,
ДГ и поршневые двигатели, огневой подогреватель, градирни, водозабор, ГЭС, сетевой подогреватель.
Мультиблоки 3×3×3 — сегменты с якорем в центральном блоке (−16…32); валы Create — вдоль Z."""
from artkit2 import *
from spec_grid import insulator

G2 = lambda n: f"block/common/grid2_{n}.png"
OWN = {**{f"jar{i}": G2(f"battery_jar_{i}") for i in range(5)},
       "fill": G2("fill"), "twb": G2("tower_band"), "twc": G2("tower_concrete"), "gate": G2("gate"),
       "eng": G2("engine"), "exh": G2("exhaust"), "can": G2("cap_can"),
       "odu": B("grid_dispatch_center_board"), "chg": B("battery_charger_front")}


def m(id_, cat, els, kind="block"):
    mk2(id_, cat, els, OWN, kind)


# ======================================================================
# АТДЦТН-125000/220: сегмент бака с вводами 220 кВ и сегмент охладителей ОДЦ
# ======================================================================
def at220_tank():
    els = [el("rails", [-15, -16, -12], [31, -15, 28], "d"),
           el("tank", [-14, -15, -10], [30, 8, 26], "r32"),
           el("cover", [-15, 8, -11], [31, 9.5, 27], "r32"),
           el("ladder", [28, -15, -12], [30, 8, -11], "gal")]
    for i, x in enumerate((-8, 8, 24)):
        els += insulator(f"hv_bush_{i}", x, 2, 9.5, 20, "por", 2.5)
    for i, x in enumerate((-8, 0, 8)):
        els += insulator(f"mv_bush_{i}", x, 20, 9.5, 9, "por", 1.75)
    els += hcyl("conservator", "x", 3.5, -14, 22, "r32", c=(4, 26, 22), sides=8)
    els += [el("cons_leg", [14, 9.5, 21], [15, 23, 23], "d")]
    return els


m("power_transformer_220", "grid", at220_tank())
m("power_transformer_220_cooler", "grid", [
    el("header_top", [-14, 10, -4], [30, 12, -2], "r32"),
    el("header_bot", [-14, -14, -4], [30, -12, -2], "r32"),
] + [el(f"cooler_{i}", [-13 + i * 15, -12, -2], [-2 + i * 15, 10, 20], "rad", faces={"north": "vent", "south": "vent"})
     for i in range(3)]
  + [el(f"oil_pump_{i}", [-10 + i * 15, -16, 0], [-6 + i * 15, -12, 4], "mb") for i in range(3)])
m("power_transformer_220/fan", "grid", [
    el("ring", [2, 2, 0], [14, 14, 1], "d"),
    el("blade_x", [2.5, 7.25, 0.25], [13.5, 8.75, 0.75], "gal"),
    el("blade_y", [7.25, 2.5, 0.25], [8.75, 13.5, 0.75], "gal"),
    el("hub", [7, 7, -0.5], [9, 9, 1.5], "d"),
], kind="partial")


# БСК 10 кВ: стеллаж банок конденсаторов RAL 7035
def cap_bank(on):
    els = [el("frame_w", [1, 0, 2], [2, 15, 14], "ral"), el("frame_e", [14, 0, 2], [15, 15, 14], "ral")]
    for j, y in enumerate((1, 8)):
        els.append(el(f"shelf_{j}", [1, y - 1, 2], [15, y, 14], "ral"))
        for i in range(3):
            els.append(el(f"can_{j}_{i}", [2.5 + i * 4, y, 4], [5.5 + i * 4, y + 5.5, 12], "can"))
            els.append(el(f"bush_{j}_{i}", [3.5 + i * 4, y + 5.5, 7], [4.5 + i * 4, y + 6.5, 9], "por"))
    els.append(lamp("lamp_on", 8, 15.5, 2, on, "lr", 0.5))
    return els


m("capacitor_bank", "grid", cap_bank(False))
m("capacitor_bank_on", "grid", cap_bank(True))


# щит ОДУ 3×2×1: мнемощит 32×32 на стене, пульт диспетчера перед ним; якорь — средний блок
def dispatch(alarm):
    return [el("board", [-16, 0, 13], [32, 32, 16], "ral", faces={"north": "odu"}, full=["north"]),
            el("cornice", [-16, 31, 12], [32, 32, 13], "rals"),
            el("desk", [-12, 0, 2], [28, 7, 9], "pn"),
            el("desk_top", [-13, 7, 1], [29, 8, 10], "pns", rot=("x", -22.5, [8, 7.5, 1])),
            lamp("lamp_alarm", 8, 30, 13, alarm, "lr", 1.0)]


m("grid_dispatch_center", "control", dispatch(False))
m("grid_dispatch_center_alarm", "control", dispatch(True))


# АКБ СК-16: стеллаж с двумя прозрачными банками; уровень электролита по soc
def battery(soc):
    return [el("rack_base", [0, 0, 2], [16, 2, 14], "wood"),
            el("rack_rail", [0, 2, 2], [16, 3, 3], "wood"),
            el("jar_w", [1, 3, 3], [7.5, 13, 13], f"jar{soc}", faces={"up": "d"}),
            el("jar_e", [8.5, 3, 3], [15, 13, 13], f"jar{soc}", faces={"up": "d"}),
            el("strap", [4, 13, 7.5], [12, 13.75, 8.5], "cus"),
            el("post_w", [3.5, 13, 7], [5, 14.5, 9], "lead"),
            el("post_e", [11, 13, 7], [12.5, 14.5, 9], "lead")]


m("station_battery", "grid", battery(4))
for _s in range(4):
    m(f"station_battery_soc{_s}", "grid", battery(_s))


# ВАЗП-380/260: шкаф, амперметр — BER
def charger(mode="off"):
    return cabinet("ral", "chg", z0=3) + [
        lamp("lamp_charge", 4, 14, 3, mode == "charging", "ly", 0.6),
        lamp("lamp_float", 6.5, 14, 3, mode == "float", "lg", 0.6)]


for _s in ("off", "charging", "float"):
    m("battery_charger" + ("" if _s == "off" else "_" + _s), "grid", charger(_s))

# обдувочный аппарат на стене котла: корпус с редуктором, паровой подвод (красный), трубка — partial
m("soot_blower", "boilers", [
    el("wall_flange", [3, 3, 14.5], [13, 13, 16], "d"),
    el("housing", [4, 4, 6], [12, 12, 14.5], "st"),
    el("gearbox", [5, 12, 7], [11, 15, 12], "mb"),
    el("motor", [11, 12.5, 8], [15, 15, 11], "mb"),
] + hcyl("steam_feed", "x", 1, 0, 4, "steam", c=(8, 8, 10)))
m("soot_blower/lance", "boilers", hcyl("lance", "z", 1, -12, 6, "st", c=(8, 8, 8), sides=8)
  + hcyl("nozzle", "z", 1.4, -13, -12, "d", c=(8, 8, 8), sides=8), kind="partial")


# ======================================================================
# ГТУ 3×3×9: компрессор (воздухозаборник с фильтрами), камера сгорания, турбина (жёлтый выхлоп)
# ось агрегата — Z; ротор — partial с осью по Z
# ======================================================================
def gt_enclosure(extra):
    return [el("base", [-16, -16, -16], [32, -14, 32], "d"),
            el("enclosure", [-14, -14, -16], [30, 18, 32], "ral", faces={"east": "louv", "west": "louv"}),
            el("roof", [-15, 18, -16], [31, 20, 32], "rals")] + extra


m("gas_turbine", "boilers", gt_enclosure([
    el("intake_house", [-12, 20, -14], [28, 31.5, 18], "ral", faces={"north": "vent", "east": "vent", "west": "vent"}),
    el("intake_duct", [0, 18, 10], [16, 20, 26], "rals")]))
m("gas_turbine_combustor", "boilers", gt_enclosure([
    el("fuel_line", [-16, 0, 6], [-14, 2, 10], "gas"),
    el("vent_fan", [2, 20, 2], [14, 22, 14], "d")]))
m("gas_turbine_turbine", "boilers", gt_enclosure(
    vcyl("exhaust_stack", 8, 16, 7, 20, 31.9, "gasr", 8) + [el("diffuser", [-6, 18, 6], [22, 20, 26], "rals")]))
m("gas_turbine/rotor", "boilers", hcyl("rotor", "z", 3, -16, 32, "st", sides=8)
  + [el(f"stage_{i}", [2, 2, z], [14, 14, z + 1], "gal") for i, z in enumerate((-12, -6, 0, 6, 22, 28))], kind="partial")

# котёл-утилизатор П-90 3×6×3: нижний сегмент (газовый вход жёлтый), верхний (барабан, паровые линии)
m("hrsg", "boilers", [
    el("casing", [-14, -16, -14], [30, 32, 30], "clad"),
    el("gas_inlet", [-16, -12, -6], [-14, 12, 22], "gasr", faces={"west": "gasr"}),
    el("stair", [30, -16, -14], [32, 32, -10], "gal"),
])
m("hrsg_top", "boilers", [
    el("casing", [-14, -16, -14], [30, 6, 30], "clad"),
    el("roof", [-15, 6, -15], [31, 8, 31], "d"),
] + hcyl("drum", "z", 5, -14, 30, "st", c=(8, 14, 8), ends="d")
  + vcyl("steam_out", 8, -10, 2, 18, 31.9, "steam", 8)
  + vcyl("stack", 8, 20, 6, 8, 31.9, "st", 8))


# ======================================================================
# ДГ АСД-5600 3×3×6: сегмент дизеля и сегмент генератора; маховик — partial (ось Z)
# ======================================================================
m("diesel_generator", "grid", [
    el("bedplate", [-16, -16, -16], [32, -12, 32], "d"),
    el("block", [-10, -12, -14], [26, 12, 32], "eng"),
    el("head", [-8, 12, -12], [24, 16, 30], "g6"),
    el("exhaust_manifold", [26, 4, -12], [30, 10, 30], "exh"),
    el("turbo", [24, 14, 24], [30, 22, 30], "exh"),
] + vcyl("exhaust_pipe", 27, 27, 2, 22, 31.9, "exh", 8)
  + vcyl("air_bottle_1", -13, -10, 2.5, -12, 12, "red", 8)
  + vcyl("air_bottle_2", -13, -2, 2.5, -12, 12, "red", 8))
m("diesel_generator_alternator", "grid", [
    el("bedplate", [-16, -16, -16], [32, -12, 32], "d"),
    el("stator", [-12, -12, -10], [28, 22, 26], "r35", faces={"up": "r32"}),
    el("terminal_box", [-4, 22, 0], [20, 28, 16], "r35"),
    el("bearing", [0, -12, 26], [16, 6, 32], "ci"),
])
m("diesel_generator/flywheel", "grid", hcyl("flywheel", "z", 10, -16, -13, "ci", c=(8, 0, 8))
  + hcyl("hub", "z", 2.5, -16.5 + 0.6, -12, "st", c=(8, 0, 8), sides=8), kind="partial")


# бензиновый двигатель ЗИЛ 1×1×1: блок, красный бак, выхлоп; коленвал — partial (ось Z)
m("gasoline_engine", "grid", [
    el("base", [1, 0, 1], [15, 1.5, 15], "d"),
    el("block", [3, 1.5, 3], [13, 10, 13], "eng"),
    el("head", [3.5, 10, 3.5], [12.5, 12, 12.5], "dg"),
    el("carburettor", [6.5, 12, 6.5], [9.5, 14, 9.5], "st"),
    el("fuel_tank", [13, 6, 4], [16, 12, 12], "red"),
    el("exhaust", [0, 4, 6], [3, 6, 8], "exh"),
    el("exhaust_up", [0, 6, 6], [2, 15, 8], "exh"),
])
m("gasoline_engine/crankshaft", "grid", shaft("z", 1) + hcyl("pulley", "z", 3, 0, 1.5, "d", sides=8), kind="partial")


def piston_engine(gas=False):
    els = [el("bedplate", [-16, -16, -14], [32, -13, 30], "d"),
           el("block", [-12, -13, -10], [28, 8, 26], "g6" if not gas else "ral"),
           el("head", [-10, 8, -8], [26, 13, 24], "eng" if not gas else "rals"),
           el("rocker_cover", [-8, 13, -6], [24, 16, 22], "dg")]
    if gas:
        els += [el("gas_rail", [-12, 10, -12], [28, 12, -10], "gas"),
                el("gas_ring", [8, 10, -12.2], [10, 12, -10], "red"),
                el("hood", [-14, 16, -12], [30, 18, 28], "ral")]
    else:
        els += [el("exhaust_manifold", [28, 2, -6], [31, 7, 22], "exh"),
                el("turbo", [22, 16, 18], [30, 24, 26], "exh")]
    return els


m("diesel_engine", "grid", piston_engine())
m("diesel_engine/flywheel", "grid", hcyl("flywheel", "z", 9, -16, -13.5, "ci", c=(8, -2, 8)), kind="partial")
m("gas_engine", "grid", piston_engine(True))
m("gas_engine/flywheel", "grid", hcyl("flywheel", "z", 9, -16, -13.5, "ci", c=(8, -2, 8)), kind="partial")

# расходный бак дизтоплива 1×2×1: вертикальный цилиндр с коричневым поясом (топливо)
m("fuel_day_tank", "grid", legs(3, 13, 3, 13, 4, 1)
  + vcyl("tank", 8, 8, 6, 4, 30, "sil", top="sils")
  + vcyl("band", 8, 8, 6.3, 15, 18, "fuel")
  + vcyl("vent", 8, 8, 0.75, 30, 32, "st", 8))


# огневой подогреватель (печь АВТ): стена, свод, радиантный змеевик
def heater(part="wall", lit=False):
    if part == "roof":
        return [el("shell", [0, 12, 0], [16, 16, 16], "d"), el("lining", [0, 9, 0], [16, 12, 16], "ref")]
    if part == "coil":
        els = [el("lining", [0, 0, 13], [16, 16, 16], "ref")]
        els += [el(f"tube_{i}", [1 + i * 4, 0, 9], [3 + i * 4, 16, 11], "rust") for i in range(4)]
        if lit:
            els.append(el("glow_flame", [0, 0, 4], [16, 3, 8], "gfl"))
        return els
    els = [el("shell", [0, 0, 13], [16, 16, 16], "d"), el("lining", [0, 0, 11], [16, 16, 13], "ref"),
           el("peep_frame", [6, 6, 10.5], [10, 10, 11], "st")]
    els.append(el("glow_peep" if lit else "peep", [6.5, 6.5, 10.4], [9.5, 9.5, 10.6], "gfl" if lit else "gls"))
    return els


m("fired_heater_casing", "boilers", heater())
m("fired_heater_casing_lit", "boilers", heater(lit=True))
m("fired_heater_casing_roof", "boilers", heater("roof"))
m("fired_heater_casing_coil", "boilers", heater("coil"))
m("fired_heater_casing_coil_lit", "boilers", heater("coil", True))


# ======================================================================
# градирни
# ======================================================================
def tower_cell(iced=False):
    s = "fro" if iced else "frp"
    return [el("basin", [-16, -16, -16], [32, -12, 32], "c"),
            el("wall_n", [-16, -12, -16], [32, 16, -14], s),
            el("wall_s", [-16, -12, 30], [32, 16, 32], s),
            el("wall_w", [-16, -12, -14], [-14, 16, 30], s, faces={"west": "louv"}),
            el("wall_e", [30, -12, -14], [32, 16, 30], s, faces={"east": "louv"}),
            el("fill", [-14, -6, -14], [30, 4, 30], "fill"),
            el("deck", [-16, 16, -16], [32, 18, 32], s)] + vcyl("fan_stack", 8, 8, 13, 18, 30, s, 16)


m("cooling_tower_cell", "cooling", tower_cell())
m("cooling_tower_cell_iced", "cooling", tower_cell(True))
m("cooling_tower_cell/fan", "cooling", fan_rotor("y", 12, 24, "frp") + shaft("y", 1, 18, 24), kind="partial")


def shell(angle=0, top=False):
    t = "twb" if top else "twc"
    if not angle:
        return [el("wall", [0, 0, 12], [16, 16, 16], t)]
    return [el("wall", [0, 0, 12], [16, 16, 16], t, rot=("x", angle, [8, 0, 14]))]


m("cooling_tower_shell", "cooling", shell())
m("cooling_tower_shell_top", "cooling", shell(top=True))
for _a, _n in ((22.5, "in"), (45, "in_steep"), (-22.5, "out"), (-45, "out_steep")):
    m(f"cooling_tower_shell_{_n}", "cooling", shell(_a))
    m(f"cooling_tower_shell_{_n}_top", "cooling", shell(_a, True))
m("cooling_tower_fill", "cooling", [el("fill", [0, 0, 0], [16, 16, 16], "fill")])


def basin(level):
    els = [el("floor", [0, 0, 0], [16, 2, 16], "c"), el("rim_n", [0, 2, 0], [16, 16, 1.5], "c")]
    if level:
        els.append(el("water", [0, 2, 1.5], [16, 2 + level, 16], "wd"))
    return els


m("cooling_tower_basin", "cooling", basin(13))
m("cooling_tower_basin_low", "cooling", basin(6))
m("cooling_tower_basin_empty", "cooling", basin(0))

m("spray_pond_nozzle", "cooling", [el("base", [5, 0, 5], [11, 1, 11], "c")]
  + vcyl("riser", 8, 8, 1, 1, 11, "wat", 8) + vcyl("nozzle", 8, 8, 2, 11, 13, "b", 8)
  + [el("deflector", [6, 13, 6], [10, 13.5, 10], "b")])


def intake(fouled):
    return [el("wall_w", [0, 0, 0], [3, 32, 16], "c"), el("wall_e", [13, 0, 0], [16, 32, 16], "c"),
            el("deck", [0, 29, 0], [16, 32, 16], "c"),
            el("screen", [3, 2, 7], [13, 28, 8], "rust" if fouled else "grat"),
            el("pump_house", [3, 0, 10], [13, 10, 16], "c"),
            el("discharge", [6, 24, 12], [10, 28, 16], "wat")]


m("water_intake", "cooling", intake(False))
m("water_intake_fouled", "cooling", intake(True))

# уловитель шариков системы очистки конденсатора: сетчатый корпус на трубе
m("condenser_ball_cleaning", "cooling", hcyl("pipe", "x", 3, 0, 16, "wat")
  + hcyl("strainer", "x", 5, 4, 12, "st", ends="d") + vcyl("ball_line", 8, 8, 1, 13, 16, "wat", 8)
  + [el("window", [6, 7, 2.6], [10, 10, 3], "gls")])


# ======================================================================
# ГЭС: турбина Френсиса (спиральная камера), водовод, затвор; сетевой подогреватель
# ======================================================================
m("hydro_turbine", "grid", [
    el("pit", [-16, -16, -16], [32, -12, 32], "c"),
    el("stay_ring", [-6, -12, -6], [22, 4, 22], "gate"),
    el("cover", [-4, 4, -4], [20, 8, 20], "st"),
    el("servo_l", [-12, 4, 6], [-6, 10, 10], "ye"),
    el("servo_r", [22, 4, 6], [28, 10, 10], "ye"),
] + hcyl("spiral_in", "x", 7, -16, -4, "wat", c=(8, -4, -8), sides=8)
  + vcyl("spiral", 8, 8, 13, -12, 4, "gate", 16)
  + vcyl("guide_bearing", 8, 8, 4, 8, 16, "dg", 8))
m("hydro_turbine/runner", "grid", vcyl("crown", 8, 8, 7, 0, 3, "b", 16)
  + [el(f"blade_{i}", [7.5, -10, 1], [8.5, 0, 14], "b", rot=("y", a, [8, 8, 8]) if a else None)
     for i, a in enumerate((0, 45, -45))] + vcyl("shaft", 8, 8, 2, 3, 30, "st", 8), kind="partial")

m("penstock", "grid", hcyl("pipe", "z", 6, 0, 16, "wat") + hcyl("ring", "z", 6.5, 7, 9, "d", sides=8))
m("penstock_side", "grid", hcyl("arm", "x", 5.5, 0, 8, "wat", c=(8, 8, 8)))

m("dam_gate", "grid", [
    el("guide_w", [0, 0, 5], [3, 16, 11], "c"), el("guide_e", [13, 0, 5], [16, 16, 11], "c"),
    el("sill", [3, 0, 5], [13, 1, 11], "st"),
    el("hoist_beam", [0, 15, 4], [16, 16, 12], "ye"),
])
m("dam_gate/gate", "grid", [
    el("leaf", [3, 1, 7], [13, 15, 9], "gate"),
    el("lug", [7, 15, 7.5], [9, 16, 8.5], "st"),
], kind="partial")

m("district_heat_exchanger", "boilers", legs(3, 13, 3, 13, 3, 1)
  + vcyl("shell", 8, 8, 5.5, 3, 28, "en", top="d") + vcyl("channel", 8, 8, 6, 26, 31, "dg", 8)
  + nozzle("in_water", "z", 0, (8, 8, 8), 1.2, "wat") + nozzle("out_water", "z", 0, (8, 22, 8), 1.2, "wat")
  + nozzle("steam_in", "x", 16, (8, 18, 8), 1.2, "steam"))
