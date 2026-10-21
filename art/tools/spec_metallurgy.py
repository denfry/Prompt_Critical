"""08: уголь и металлургия.

Габариты больше 1 блока: по осям размера 2 модель идёт 0…32 от блока-якоря, по осям
размера 3 якорь — средний блок (−16…32). Выход вала Create — на north, pivot на оси вала.
"""
from artkit import *

X = lambda n: f"block/common/metal_{n}.png"
G = lambda n: f"block/common/grid_{n}.png"
OWN = {
    "hs": X("host_stone"), "hd": X("host_deepslate"), "ht": X("host_tuff"),
    "tim": X("timber"), "rust": X("rust"), "or": X("orange"), "din": X("dinas"), "soot": X("soot"),
    "crust": X("crust"), "pg": X("panel_green"), "froth": X("froth"), "sand": X("sand"),
    "slur": X("slurry"), "elec": X("electrolyte"), "oil": X("oil"), "rol": X("roller"),
    "bed": X("cooling_bed"), "mesh": X("mesh"), "mach": X("machine"), "machs": X("machine_smooth"),
    "grind": X("grinding"), "fro": X("frost"), "cdoor": X("coke_door"), "grn": X("green"),
    "hot": F_("glow_hot"), "water": F_("water"), "pulp": M_("pulp"), "lab": M_("lab_white"),
    "riv": G("riveted"), "ci": G("cast_iron"), "brk": G("brick"), "r32": G("ral7032"),
    "rad": G("radiator"), "por": G("porcelain"), "al": G("aluminium"), "r35": G("ral7035"), "dg": G("dark_grey"),
    "agz": B("mine_dispatch_cabinet_face"), "bfc": B("blast_furnace_controller_face"),
    "clay": B("fireclay"), "qtz": B("vein_quartz"),
}


def m(id_, cat, els, kind="block"):
    mk(id_, cat, els, OWN, kind)


def _fit(a0, a1):
    """Торцы цилиндра disc() разнесены на 0,01·i; у границ −16/32 поджимаем, чтобы не выйти за предел."""
    return max(a0, -15.92), min(a1, 31.92)


def vcyl(name, cx, cz, r, y0, y1, tex, sides=16, top=None):
    y0, y1 = _fit(y0, y1)
    return disc(name, "y", r, y0, y1, tex, sides, faces={"up": top} if top else None, c=(cx, 8, cz))


def hcyl(name, axis, r, a0, a1, tex, c=(8, 8, 8), sides=16, ends=None):
    a0, a1 = _fit(a0, a1)
    f = None
    if ends:
        f = {{"x": "east", "z": "south"}[axis]: ends, {"x": "west", "z": "north"}[axis]: ends}
    return disc(name, axis, r, a0, a1, tex, sides, faces=f, c=c)


def lamp(name, x, y, z, on, col, s=0.75):
    return el(("glow_" if on else "") + name, [x - s, y - s, z - 0.6], [x + s, y + s, z], col if on else "loff")


def shaft_x(r=1, x0=-0.01, x1=16.01, c=(8, 8, 8)):
    x0, x1 = _fit(x0, x1)
    return disc("shaft", "x", r, x0, x1, "st", 8, c=c)


def shaft_z(r=1, z0=-0.01, z1=16.01, c=(8, 8, 8)):
    z0, z1 = _fit(z0, z1)
    return disc("shaft", "z", r, z0, z1, "st", 8, c=c)


def impeller_z(z0, z1, r=5, tex="gal", c=(8, 8, 8)):
    x, y, _ = c
    return [el("blade_a", [x - r, y - 0.75, z0], [x + r, y + 0.75, z1], tex),
            el("blade_b", [x - 0.75, y - r, z0], [x + 0.75, y + r, z1], tex),
            el("blade_c", [x - r, y - 0.75, z0], [x + r, y + 0.75, z1], tex, rot=("z", 45, list(c))),
            el("blade_d", [x - r, y - 0.75, z0], [x + r, y + 0.75, z1], tex, rot=("z", -45, list(c)))]


def ladder(x, z0, y0, y1, tex="d"):
    return [el("ladder_l", [x, y0, z0], [x + 0.5, y1, z0 + 0.5], tex),
            el("ladder_r", [x + 3, y0, z0], [x + 3.5, y1, z0 + 0.5], tex)] + [
        el(f"rung_{i}", [x, y, z0], [x + 3.5, y + 0.4, z0 + 0.4], tex) for i, y in enumerate(range(int(y0) + 2, int(y1), 3))]


# ======================================================================
# руды: подложка host + наложение <id>_overlay (отдельные модели на stone/deepslate/tuff)
# ======================================================================
ORES = ["magnetite_ore", "hematite_ore", "pyrolusite_ore", "pentlandite_ore", "chalcopyrite_ore",
        "sperrylite_ore", "molybdenite_ore", "sphalerite_ore", "chromite_ore", "cassiterite_ore", "coal_seam"]
for _o in ORES:
    for _h, sfx in (("hs", ""), ("hd", "_deepslate"), ("ht", "_tuff")):
        mk(_o + sfx, "ores", [el("host", [0, 0, 0], [16, 16, 16], _h),
                              el("ore", [-0.01, -0.01, -0.01], [16.01, 16.01, 16.01], "o")],
           dict(OWN, o=B(_o + "_overlay")))
for _r in ("lignite", "anthracite"):
    mk(f"coal_seam_{_r}", "ores", [el("host", [0, 0, 0], [16, 16, 16], "hs"),
                                   el("ore", [-0.01, -0.01, -0.01], [16.01, 16.01, 16.01], "o")],
       dict(OWN, o=B(f"coal_seam_{_r}_overlay")))
m("fireclay", "ores", [el("block", [0, 0, 0], [16, 16, 16], "clay")])
m("vein_quartz", "ores", [el("block", [0, 0, 0], [16, 16, 16], "qtz")])


# ======================================================================
# шахта
# ======================================================================
# шахтная крепь: арка СВП (деревянный оклад и мехкрепь — spec_visual)
def support_arch():
    return [el("arch_w", [0, -16, 7], [2, 18, 9], "rust"),
            el("arch_e", [14, -16, 7], [16, 18, 9], "rust"),
            el("arch_nw", [0, 16, 7], [2, 28, 9], "rust", rot=("z", -22.5, [1, 18, 8])),
            el("arch_ne", [14, 16, 7], [16, 28, 9], "rust", rot=("z", 22.5, [15, 18, 8])),
            el("arch_top", [3, 28, 7], [13, 30, 9], "rust"),
            el("clamp_w", [-0.5, 16, 6.5], [2.5, 18, 9.5], "d"),
            el("clamp_e", [13.5, 16, 6.5], [16.5, 18, 9.5], "d"),
            el("mesh", [2, 29, 0], [14, 29.5, 16], "mesh")]


m("mine_support_arch", "mining", support_arch())

# главный вентилятор ВОД-21 3×3×3: кожух с жалюзи, электродвигатель; крыльчатка — partial (ось Z)
def main_fan(running=False):
    return hcyl("housing", "z", 15, -15.5, 26, "p") + [
        el("louver_face", [-12, -12, -16], [28, 28, -15.5], "louv", full=["north"]),
        el("diffuser", [-10, -10, 26], [26, 26, 32], "p"),
        el("motor", [-16, -16, 20], [0, -4, 32], "mb"),
        el("foundation", [-16, -16, -16], [32, -14, 20], "c"),
        lamp("lamp_run", 28, 26, -15.4, running, "lg", 1.25)]


m("main_mine_fan", "mining", main_fan())
m("main_mine_fan_running", "mining", main_fan(True))
m("main_mine_fan/impeller", "mining", disc("hub", "z", 3, -10, 6, "st", 8) + impeller_z(-6, 2, 13), kind="partial")


# вентиляционная дверь 1×2×1: дощатая с резиной / стальной регулятор с окном
def vent_door(kind="door", opened=False):
    els = [el("frame_w", [0, 0, 6], [2, 32, 10], "tim"),
           el("frame_e", [14, 0, 6], [16, 32, 10], "tim"),
           el("lintel", [0, 29, 6], [16, 32, 10], "tim")]
    if kind == "door":
        leaf = el("leaf", [2, 0, 7], [14, 29, 9], "tim", faces={"north": "tim"})
        if opened:
            leaf = el("leaf", [2, 0, 9], [4, 29, 21], "tim")
        els += [leaf, el("seal", [2, 0, 6.8], [14, 0.5, 9.2], "r")]
    else:
        els += [el("plate", [2, 0, 7.5], [14, 29, 8.5], "p"),
                el("window_frame", [5, 14, 7.25], [11, 22, 8.75], "d"),
                el("window", [6, 15, 7], [10, 21, 9], "gl")]
    return els


m("ventilation_door", "mining", vent_door())
m("ventilation_door_open", "mining", vent_door(opened=True))
m("ventilation_door_regulator", "mining", vent_door("regulator"))

# сланцевый заслон (полки с инертной пылью) / водяной заслон (подвесные сосуды)
m("explosion_barrier", "mining", [el("hanger_w", [1, 10, 7], [2, 16, 9], "d"), el("hanger_e", [14, 10, 7], [15, 16, 9], "d")]
  + [x for y in (10, 12.5) for x in (el(f"shelf_{y}", [1, y, 4], [15, y + 0.5, 12], "tim"),
                                       el(f"dust_{y}", [2, y + 0.5, 5], [14, y + 1.5, 11], "lab"))])
m("explosion_barrier_water", "mining", [el("rail", [0, 14.5, 7], [16, 15.5, 9], "d")]
  + [x for i, xx in enumerate((1, 6, 11)) for x in (
      el(f"vessel_{i}", [xx, 8, 4], [xx + 4, 13, 12], "gl"),
      el(f"water_{i}", [xx + 0.5, 8.5, 4.5], [xx + 3.5, 11.5, 11.5], "water"),
      el(f"hook_{i}", [xx + 1.5, 13, 7.5], [xx + 2.5, 14.5, 8.5], "d"))])

# дегазационная скважина: устье с задвижкой, жёлтая газовая труба
m("degasification_borehole", "mining", [
    el("collar", [4, 0, 4], [12, 2, 12], "c"),
    el("casing", [6, 2, 6], [10, 6, 10], "st"),
    el("valve_body", [5, 6, 5], [11, 10, 11], "d"),
    el("handwheel", [3, 8, 7.5], [5, 12, 8.5], "red"),
    el("riser", [6.5, 10, 6.5], [9.5, 13, 9.5], "gas"),
    el("elbow", [6.5, 13, 6.5], [16, 16, 9.5], "gas", faces={"east": "end"}),
])
# шкаф АГЗ «Метан» 1×2×1: табло газа (подложка 32×32)
def agz(alarm=False):
    return [el("body", [0, 0, 2], [16, 31.5, 16], "r35"),
            el("face", [1, 14, 1.5], [15, 30, 2], "agz", full=["north"]),
            el("door_low", [1, 1, 1.5], [15, 13, 2], "r35"),
            el("handle", [13, 5, 0.5], [14, 9, 1.5], "d"),
            el(("glow_" if alarm else "") + "beacon", [6, 31.5, 6], [10, 33.5 - 1.5, 10], "lr" if alarm else "loff")]


m("mine_dispatch_cabinet", "mining", agz())
m("mine_dispatch_cabinet_alarm", "mining", agz(True))

# копёр: секции клёпаной фермы 3×3×3, верхняя — подшкивная площадка; шкив — partial (ось X)
def headframe_section(top=False):
    els = []
    for i, (x, z) in enumerate(((-15, -15), (29, -15), (-15, 29), (29, 29))):
        els.append(el(f"leg_{i}", [x, -16, z], [x + 2, 32, z + 2], "riv"))
    for y in (-16, 8):
        els += [el(f"brace_n_{y}", [-13, y, -15], [29, y + 2, -14], "riv"),
                el(f"brace_s_{y}", [-13, y, 30], [29, y + 2, 31], "riv"),
                el(f"brace_w_{y}", [-15, y, -13], [-14, y + 2, 29], "riv"),
                el(f"brace_e_{y}", [30, y, -13], [31, y + 2, 29], "riv")]
    if top:
        els += [el("deck", [-16, 26, -16], [32, 28, 32], "grat"),
                el("rail_n", [-16, 28, -16], [32, 31, -15.5], "red"),
                el("bearing_w", [-12, 28, 4], [-6, 32, 12], "d"),
                el("bearing_e", [22, 28, 4], [28, 32, 12], "d")]
    return els


m("headframe", "mining", headframe_section())
m("headframe_top", "mining", headframe_section(True))
m("headframe/sheave", "mining", hcyl("rim", "x", 13, 4, 12, "red", c=(8, 16, 8)) + hcyl("hub", "x", 3, 2, 14, "st", c=(8, 16, 8), sides=8)
  + [el("spoke_v", [6.5, 4, 7], [9.5, 28, 9], "red"), el("spoke_h", [6.5, 15, -4], [9.5, 17, 20], "red")],
  kind="partial")


# шахтная клеть 2×3×2 (якорь — нижний северо-западный по X/Z, средний по Y)
def cage(door_open=False):
    els = [el("floor", [0, -16, 0], [32, -14, 32], "grat"),
           el("roof", [0, 30, 0], [32, 32, 32], "p"),
           el("bridle", [12, 32 - 0.01, 12], [20, 32, 20], "d"),
           el("wall_w", [0, -14, 0], [0.5, 30, 32], "mesh"),
           el("wall_e", [31.5, -14, 0], [32, 30, 32], "mesh"),
           el("wall_s", [0, -14, 31.5], [32, 30, 32], "mesh")]
    for i, (x, z) in enumerate(((0, 0), (30, 0), (0, 30), (30, 30))):
        els.append(el(f"post_{i}", [x, -14, z], [x + 2, 30, z + 2], "d"))
    els.append(el("rail", [0.5, 4, 1], [31.5, 5, 2], "cy"))
    els.append(el("door", [2, -14, 0], [30, 2, 0.5], "mesh") if not door_open else el("door", [2, 12, 0], [30, 28, 0.5], "mesh"))
    return els


m("mine_cage", "mining", cage())
m("mine_cage_open", "mining", cage(True))

# ======================================================================
# углеобогащение
# ======================================================================
# отсадочная машина ОМ — корпус в spec_visual; пульсатор (ось X на y 27, z 8)
m("coal_jig/pulsator", "coal_prep", shaft_x(1.5, 12, 28, (8, 27, 8)) + [el("eccentric", [18, 23, 5], [22, 31, 11], "st")], kind="partial")

# тяжелосредный сепаратор СКВ 2×2×2: опорная рама, жёлоб суспензии; барабан — partial (ось X)
m("dense_medium_separator", "coal_prep", [
    el("frame_w", [0, 0, 2], [3, 14, 30], "d"), el("frame_e", [29, 0, 2], [32, 14, 30], "d"),
    el("tyre_rollers", [3, 0, 6], [29, 3, 26], "d"),
    el("feed_chute", [-0.01, 18, 12], [6, 26, 20], "p"),
    el("medium", [6, 2, 8], [26, 6, 24], "slur"),
    el("discharge", [26, 22, 10], [32, 28, 22], "p"),
])
m("dense_medium_separator/drum", "coal_prep", hcyl("drum", "x", 12, 3, 29, "p", c=(16, 16, 16)) + hcyl("tyre_w", "x", 13, 6, 9, "st", c=(16, 16, 16))
  + hcyl("tyre_e", "x", 13, 23, 26, "st", c=(16, 16, 16)), kind="partial")


# флотомашина ФМР: бак, пеногон; импеллер — partial (ось Y)
def flot(froth=False):
    return [el("tank_w", [0, 0, 0], [1.5, 15, 16], "p"), el("tank_e", [14.5, 0, 0], [16, 15, 16], "p"),
            el("tank_n", [1.5, 0, 0], [14.5, 15, 1.5], "p"), el("tank_s", [1.5, 0, 14.5], [14.5, 15, 16], "p"),
            el("bottom", [1.5, 0, 1.5], [14.5, 1, 14.5], "p"),
            el("pulp", [1.5, 1, 1.5], [14.5, 13, 14.5], "froth" if froth else "pulp"),
            el("launder", [-1, 12, 2], [1, 14, 14], "p"),
            el("bridge", [0, 15, 7], [16, 16, 9], "d"),
            el("drive", [6, 16, 5.5], [10, 16.01, 10.5], "mb")]


m("flotation_cell", "coal_prep", flot())
m("flotation_cell_froth", "coal_prep", flot(True))
m("flotation_cell/impeller", "coal_prep", vcyl("stem", 8, 8, 0.75, 2, 16, "st", 8)
  + [el("rotor_a", [5, 2, 7.5], [11, 4, 8.5], "r"), el("rotor_b", [7.5, 2, 5], [8.5, 4, 11], "r")], kind="partial")

# магнитный сепаратор ПБМ 2×1×1: ванна с пульпой; барабан — partial (ось X)
m("magnetic_separator", "coal_prep", [
    el("tank", [0, 0, 2], [32, 8, 14], "p"),
    el("pulp", [1, 7, 3], [31, 7.5, 13], "pulp"),
    el("feed_box", [2, 8, 0], [30, 12, 3], "p"),
    el("bearing_w", [-0.01, 6, 6], [2, 12, 10], "d"),
    el("bearing_e", [30, 6, 6], [32, 12, 10], "d"),
])
m("magnetic_separator/drum", "coal_prep", hcyl("drum", "x", 5.5, 2, 30, "mb", c=(8, 10, 8)) + shaft_x(1, 0, 32, (8, 10, 8)), kind="partial")

# ======================================================================
# коксохим
# ======================================================================


# простенок коксовой батареи: динас, смотровые лючки (раскалены при hot)
def coke_wall(hot=False):
    return [el("wall", [0, 0, 0], [16, 16, 16], "din")] + [
        el(("glow_" if hot else "") + f"peep_{i}", [x, 15.9, 6], [x + 3, 16.5, 10], "hot" if hot else "ci")
        for i, x in enumerate((2, 11))]


m("coke_battery_wall", "coke", coke_wall())
m("coke_battery_wall_hot", "coke", coke_wall(True))
# коксовыталкиватель: корпус привода; штанга с башмаком — partial (ход по Z)
m("coke_pusher_ram", "coke", [
    el("housing", [1, 0, 4], [15, 14, 16], "cy"),
    el("rack_guide", [5, 4, 0], [11, 10, 4], "d"),
    el("motor", [2, 14, 8], [8, 16, 14], "mb"),
])
m("coke_pusher_ram/ram", "coke", [el("beam", [6, 5, -16], [10, 9, 16], "soot"), el("shoe", [3, 2, -16], [13, 12, -14], "ci")], kind="partial")
# газосборник: толстая жёлтая труба, стояк сверху
m("gas_collector_main", "coke", hcyl("main", "x", 6, 2, 14, "gas", sides=8) + [el("standpipe", [6, 14, 6], [10, 16, 10], "gas")])
m("gas_collector_main_side", "coke", hcyl("arm", "x", 6, 0, 2, "gas", sides=8))

# тушильная башня 3×3×3: бетонная шахта, вытяжной раструб, рельсы тушильного вагона
m("quench_tower", "coke", [
    el("rails", [-16, -16, -6], [32, -15, 22], "d"),
    el("wall_w", [-16, -15, -16], [-12, 24, 32], "c"),
    el("wall_e", [28, -15, -16], [32, 24, 32], "c"),
    el("wall_s", [-12, -15, 28], [28, 24, 32], "c"),
    el("wall_n_top", [-12, 4, -16], [28, 24, -12], "c"),
    el("stack", [-6, 24, -6], [22, 32, 22], "c"),
    el("spray_header", [-12, 2, -4], [28, 4, -2], "wat"),
])


# УСТК: секции клёпаной камеры 3×3×3 (футеровка внутри), верхняя — загрузочное устройство
def cdq(top=False):
    els = hcyl("shell", "y", 15, -16, 32, "riv")
    if top:
        els += [el("charging_hood", [-6, 28, -6], [22, 32, 22], "d"),
                el("gas_off", [26, 8, 4], [32, 16, 12], "gas", faces={"east": "end"})]
    else:
        els += [el("platform", [-16, 12, -16], [32, 13, 0], "grat"),
                el("rail", [-16, 13, -16], [32, 16, -15.5], "red")]
    return els


m("cdq_chamber", "coke", cdq())
m("cdq_chamber_top", "coke", cdq(True))


# цех улавливания 2×3×2: холодильник / электрофильтр / сатуратор / скруббер
def byproduct(kind="cooler"):
    els = vcyl("vessel", 16, 16, 12, -16, 26, "p" if kind != "precipitator" else "dg", 16, top="d")
    els += [el("gas_in", [26, -4, 12], [32, 4, 20], "gas", faces={"east": "end"}),
            el("gas_out", [12, 26, 12], [20, 32, 20], "gas", faces={"up": "end"}),
            el("skirt", [6, -16, 6], [26, -12, 26], "d")]
    if kind == "cooler":
        els.append(el("water_in", [0, 16, 12], [6, 20, 16], "wat", faces={"west": "end"}))
    elif kind == "precipitator":
        els += [el("insulator_box", [10, 26, 4], [22, 30, 10], "r35"),
                el("hv_lead", [14, 30, 6], [18, 32, 8], "por")]
    elif kind == "saturator":
        els.append(el("acid_in", [0, 10, 18], [6, 14, 22], "acid", faces={"west": "end"}))
    else:
        els += ladder(4, 3, -12, 24) + [el("spray_in", [0, 20, 18], [6, 24, 22], "wat", faces={"west": "end"})]
    return els


m("byproduct_plant", "coke", byproduct())
for _k in ("precipitator", "saturator", "scrubber"):
    m(f"byproduct_plant_{_k}", "coke", byproduct(_k))

# нагнетатель коксового газа 2×2×1: серая спираль, жёлтые патрубки; ротор — partial (ось Z)
m("gas_exhauster", "coke", hcyl("volute", "z", 13, 2, 14, "dg", c=(16, 16, 8)) + [
    el("outlet", [16, 22, 2.5], [32, 32, 13.5], "dg"),
    el("inlet", [10, 10, 14], [22, 22, 16], "gas", faces={"south": "end"}),
    el("base", [2, 0, 0], [30, 3, 16], "d"),
    el("outlet_flange", [28, 22, 2], [32, 32, 14], "gas", faces={"east": "end"}),
])
m("gas_exhauster/rotor", "coke", impeller_z(4, 12, 10, c=(16, 16, 8)) + shaft_z(1.5, -0.01, 16, (16, 16, 8)), kind="partial")

# электродный пресс 3×1×1: контейнер, мундштук, гидроцилиндр; шнек — partial (ось X)
m("electrode_extruder", "coke", [
    el("frame", [-16, 0, 2], [32, 2, 14], "d"),
    el("ram_cylinder", [-16, 2, 3], [-2, 14, 13], "dg"),
    el("container", [-2, 3, 3], [20, 13, 13], "p"),
    el("die", [20, 5, 5], [28, 11, 11], "st"),
    el("green_electrode", [28, 6, 6], [32, 10, 10], "g"),
    el("hopper", [2, 13, 4], [10, 16, 12], "p"),
])
m("electrode_extruder/screw", "coke", hcyl("flight", "x", 4, -2, 20, "st", sides=8) + shaft_x(1, -6, 20), kind="partial")


# кольцевая обжиговая печь 3×2×3: кирпичные камеры, крышки, горелочный мост
def baking(lit=False):
    els = [el("pit", [-16, 0, -16], [32, 14, 32], "brk")]
    for i, x in enumerate((-12, 2, 16)):
        els.append(el(f"lid_{i}", [x, 14, -12], [x + 12, 15, 28], "ci"))
    els += [el("bridge", [-16, 15, 2], [32, 20, 14], "cy"),
            el(("glow_" if lit else "") + "burner_ports", [-10, 14.5, 6], [26, 15.5, 10], "hot" if lit else "ci")]
    return els


m("baking_furnace", "coke", baking())
m("baking_furnace_lit", "coke", baking(True))

# ======================================================================
# доменное производство
# ======================================================================


# агломашина АКМ: сегмент 3×2×3 — рама, вакуум-камеры, зажигательный горн; паллеты — partial
def sinter(lit=False):
    return [el("frame_w", [-16, 0, -16], [-12, 20, 32], "d"),
            el("frame_e", [28, 0, -16], [32, 20, 32], "d"),
            el("windbox", [-12, 0, -16], [28, 10, 32], "ci"),
            el("hood", [-12, 22, -16], [28, 32, 0], "ref"),
            el(("glow_" if lit else "") + "burners", [-10, 21.5, -14], [26, 22, -2], "fire" if lit else "ci"),
            el("main_duct", [-16, 2, 8], [32, 8, 14], "gas")]


m("sinter_strand", "ironmaking", sinter())
m("sinter_strand_lit", "ironmaking", sinter(True))
m("sinter_strand/pallets", "ironmaking", [el("pallets", [-12, 10, -16], [28, 14, 32], "ci"),
                                          el("sinter_bed", [-11, 14, -16], [27, 19, 32], "crust")], kind="partial")


# щит домны: подложка 32×32 с табло
def bf_ctrl(alarm=False):
    return [el("desk", [0, 0, 4], [16, 9, 16], "r35"),
            el("board", [0, 9, 10], [16, 16, 16], "r35"),
            el("face", [0.5, 9.5, 9.5], [15.5, 15.5, 10], "bfc", full=["north"]),
            lamp("lamp_alarm", 13, 7, 4, alarm, "lr")]


m("blast_furnace_controller", "ironmaking", bf_ctrl())
m("blast_furnace_controller_alarm", "ironmaking", bf_ctrl(True))


# кожух домны: сегменты 3×3×3 — шахта (прямой) и заплечики (конус); холодильники-плиты
def bf_shell(bosh=False):
    # кожух свободной сборки (кольцо 5×5): клёпаный лист, пояс холодильников; у заплечиков — труба охлаждения
    els = [el("shell", [0, 0, 0], [16, 16, 16], "riv"),
           el("stave_band", [-0.5, 6, -0.5], [16.5, 8, 16.5], "rust")]
    if bosh:
        els.append(el("cooling_pipe", [6, 0, -1.5], [8, 16, -0.5], "wat"))
    return els


m("blast_furnace_shell", "ironmaking", bf_shell())
m("blast_furnace_shell_bosh", "ironmaking", bf_shell(True))

# засыпной аппарат 3×2×3: приёмная воронка, газоотводы; малый конус — partial (ход по Y)
m("blast_furnace_top", "ironmaking", hcyl("throat", "y", 10, 0, 8, "riv")
  + hcyl("hopper", "y", 7, 8, 20, "p") + [
      el("uptake_n", [4, 0, -16], [12, 28, -10], "gas"),
      el("uptake_s", [4, 0, 26], [12, 28, 32], "gas"),
      el("uptake_w", [-16, 0, 4], [-10, 28, 12], "gas"),
      el("uptake_e", [26, 0, 4], [32, 28, 12], "gas"),
      el("skip_bridge", [-16, 20, 6], [8, 22, 10], "d"),
      el("platform", [-12, 8, -12], [28, 9, 28], "grat")])
m("blast_furnace_top/cone", "ironmaking", [el("cone_low", [2, -2, 2], [14, 2, 14], "st"),
                                           el("cone_high", [5, 2, 5], [11, 5, 11], "st"),
                                           el("rod", [7.5, 5, 7.5], [8.5, 20, 8.5], "st")], kind="partial")


# фурменный прибор: медная фурма, колено, глазок
def tuyere(burnt=False):
    return [el("nose", [6, 6, 0], [10, 10, 4], "cus" if not burnt else "soot"),
            el("cooler", [4.5, 4.5, 4], [11.5, 11.5, 7], "cus"),
            el("blowpipe", [6, 6, 7], [10, 10, 14], "d"),
            el("elbow", [6, 10, 10], [10, 16, 14], "d"),
            el("glow_peep", [7, 7, 14], [9, 9, 15], "hot"),
            el("clamp", [5, 5, 9], [11, 11, 10], "st")]


m("blast_furnace_tuyere", "ironmaking", tuyere())
m("blast_furnace_tuyere_burnt", "ironmaking", tuyere(True))


# чугунная лётка: огнеупорная кладка с глиняной пробкой / струя чугуна
def taphole(opened=False):
    els = [el("brick", [0, 0, 1], [16, 16, 16], "fb"),
           el("frame", [4, 4, 0], [12, 12, 1], "ci")]
    if opened:
        els.append(el("glow_stream", [6.5, -4, -6], [9.5, 7, 1], "hot"))
    else:
        els.append(el("clay_plug", [6, 6, -0.5], [10, 10, 0], "clay"))
    return els


m("blast_furnace_taphole", "ironmaking", taphole())
m("blast_furnace_taphole_open", "ironmaking", taphole(True))


# воздухонагреватель Каупера: секции 3×3×3 — клёпаный цилиндр и купол
def stove(dome=False):
    if dome:
        return hcyl("shell", "y", 15, -16, 4, "riv") + hcyl("dome_1", "y", 12, 4, 14, "riv") + hcyl("dome_2", "y", 7, 14, 22, "riv", sides=8)\
            + [el("hot_blast_valve", [22, -10, 4], [32, -2, 12], "d")]
    return hcyl("shell", "y", 15, -16, 32, "riv") + [el("gas_burner", [-16, -12, 4], [-7, -4, 12], "gas"),
                                                    el("cold_blast", [23, -14, 4], [32, -8, 12], "air")]


m("hot_blast_stove", "ironmaking", stove())
m("hot_blast_stove_dome", "ironmaking", stove(True))

# турбовоздуходувка К-3250 2×2×2: синяя спираль (воздух); ротор — partial (ось Z)
m("blast_blower", "ironmaking", hcyl("volute", "z", 14, 4, 26, "mb", c=(16, 16, 16)) + [
    el("outlet", [16, 24, 6], [32, 32, 24], "air", faces={"east": "end"}),
    el("inlet", [10, 10, 26], [22, 22, 32], "air", faces={"south": "end"}),
    el("base", [0, 0, 0], [32, 3, 32], "d"),
    el("bearing", [12, 12, 0], [20, 20, 4], "d"),
])
m("blast_blower/rotor", "ironmaking", impeller_z(8, 22, 11, c=(16, 16, 16)) + shaft_z(1.5, -0.01, 26, (16, 16, 16)), kind="partial")


# бурмашина и пушка литейного двора 2×1×1: красная станина
def cast_house(kind="drill"):
    els = [el("pedestal", [24, 0, 4], [32, 10, 12], "red"),
           el("boom", [4, 8, 6], [28, 12, 10], "red")]
    if kind == "drill":
        els.append(el("drill_motor", [12, 4, 4], [20, 12, 12], "dg"))
    else:
        els += hcyl("barrel", "x", 3, 2, 22, "d", c=(8, 6, 8)) + [el("clay_hopper", [10, 9, 5], [16, 14, 11], "clay")]
    return els


m("cast_house_machine", "ironmaking", cast_house())
m("cast_house_machine_mud_gun", "ironmaking", cast_house("mud_gun"))
m("cast_house_machine/bit", "ironmaking", hcyl("bit", "x", 0.75, -6, 12, "st", c=(8, 8, 8), sides=8), kind="partial")
m("cast_house_machine/piston", "ironmaking", hcyl("piston", "x", 2.5, 2, 10, "sm", c=(8, 6, 8), sides=8), kind="partial")


# жёлоб литейного двора: огнеупорный жёлоб, при filled — расплав
def runner(filled=False):
    els = [el("bed", [0, 0, 2], [16, 3, 14], "ref"), el("lip_w", [0, 3, 2], [16, 7, 5], "ref"),
           el("lip_e", [0, 3, 11], [16, 7, 14], "ref")]
    if filled:
        els.append(el("glow_melt", [0, 3, 5], [16, 5.5, 11], "hot"))
    return els


m("iron_runner", "ironmaking", runner())
m("iron_runner_filled", "ironmaking", runner(True))
m("iron_runner_side", "ironmaking", [el("bed", [2, 0, 0], [14, 3, 2], "ref")])

# сталеразливочный ковш 2×2×2: стойки цапф; корпус и зеркало металла — partial (ось цапф X)
m("ladle", "ironmaking", [el("stand_w", [-0.01, 0, 12], [4, 22, 20], "d"), el("stand_e", [28, 0, 12], [32, 22, 20], "d"),
                          el("base", [0, 0, 8], [32, 2, 24], "d")])
m("ladle/body", "ironmaking", hcyl("shell", "y", 11, 2, 28, "riv", c=(16, 8, 16)) + [
    el("trunnion_w", [4, 18, 14], [6, 22, 18], "st"), el("trunnion_e", [26, 18, 14], [28, 22, 18], "st"),
    el("lining_top", [6, 28, 6], [26, 29, 26], "ref")], kind="partial")
m("ladle/melt", "ironmaking", [el("glow_melt", [7, 24, 7], [25, 26, 25], "hot")], kind="partial")

# грануляционный жёлоб 2×1×1: жёлоб с водой, сопла
m("slag_granulator", "ironmaking", [
    el("trough", [0, 0, 3], [32, 6, 13], "c"),
    el("water", [0.5, 4, 4], [31.5, 5, 12], "water"),
    el("nozzle_bar", [0, 6, 3], [6, 9, 13], "wat"),
    el("slag_spout", [0, 9, 6], [8, 11, 10], "ref"),
])
# пылеуловитель 2×3×2: конический бункер, жёлтые газоходы
m("dust_catcher", "ironmaking", hcyl("drum", "y", 13, 4, 30, "p", c=(16, 8, 16)) + hcyl("cone", "y", 8, -8, 4, "p", c=(16, 8, 16))
  + hcyl("cone_2", "y", 4, -14, -8, "p", c=(16, 8, 16), sides=8)
  + [el("gas_in", [12, 28, 0], [20, 32, 10], "gas"), el("gas_out", [26, 18, 12], [32, 26, 20], "gas", faces={"east": "end"})]
  + [el(f"leg_{i}", [x, -16, z], [x + 2, 4, z + 2], "d") for i, (x, z) in enumerate(((5, 5), (25, 5), (5, 25), (25, 25)))])

# ======================================================================
# сталеплавильное
# ======================================================================
# конвертер: опоры цапф (сегмент 3×2×3); корпус — partial (ось цапф X)
m("bof_vessel", "steelmaking", [
    el("pier_w", [-16, 0, 2], [-10, 20, 14], "c"), el("pier_e", [26, 0, 2], [32, 20, 14], "c"),
    el("bearing_w", [-14, 20, 4], [-8, 26, 12], "d"), el("bearing_e", [24, 20, 4], [30, 26, 12], "d"),
    el("pit", [-10, 0, -16], [26, 1, 32], "ref")])
m("bof_vessel/vessel", "steelmaking", hcyl("belly", "y", 13, 2, 22, "soot") + hcyl("cone", "y", 9, 22, 30, "soot")
  + hcyl("bottom", "y", 9, -6, 2, "soot") + [
      el("trunnion_ring", [-6, 13, -6], [22, 17, 22], "d"),
      el("trunnion_w", [-14, 13, 6], [-6, 17, 10], "st"), el("trunnion_e", [22, 13, 6], [30, 17, 10], "st"),
      el("glow_mouth", [1, 29.5, 1], [15, 30.5, 15], "hot")], kind="partial")

# кислородная фурма: каретка с шлангами; фурма — partial (ход вниз)
m("oxygen_lance", "steelmaking", [
    el("carriage", [3, 6, 3], [13, 16, 13], "cy"),
    el("guide", [13, 0, 6], [16, 16, 10], "d"),
    el("hose_o2", [-0.01, 10, 5], [3, 12, 7], "air"),
    el("hose_water", [-0.01, 12, 9], [3, 14, 11], "air"),
])
m("oxygen_lance/lance", "steelmaking", vcyl("lance", 8, 8, 1.5, -16, 6, "sm", 8), kind="partial")

# установка доводки 3×2×3: стенд с бункерами ферросплавов
m("ladle_station", "steelmaking", [el("deck", [-16, 18, -16], [32, 20, 32], "grat")]
  + [el(f"col_{i}", [x, 0, z], [x + 2, 18, z + 2], "d") for i, (x, z) in enumerate(((-16, -16), (30, -16), (-16, 30), (30, 30)))]
  + [x for i, xx in enumerate((-10, 4, 18)) for x in (
      el(f"bin_{i}", [xx, 20, -4], [xx + 10, 30, 6], "p"),
      el(f"chute_{i}", [xx + 4, 12, 0], [xx + 6, 20, 2], "d"))]
  + [el("argon_line", [-16, 2, 10], [32, 3, 11], "gas")])


# ДСП-12: сегменты 3×3×3 — корпус из водоохлаждаемых панелей и свод (дуга — glow)
def eaf(roof=False, arcing=False):
    if roof:
        els = hcyl("roof", "y", 15, -16, -8, "pg") + hcyl("delta", "y", 6, -8, -4, "ref")
        for i, (x, z) in enumerate(((4, 4), (12, 4), (8, 12))):
            els.append(el(f"port_{i}", [x - 1.5, -4, z - 1.5], [x + 1.5, -2, z + 1.5], "ci"))
        els.append(el("fume_elbow", [18, -8, 4], [32, 0, 12], "d"))
        if arcing:
            els.append(el("glow_arc", [2, -16.01 + 0.01, 2], [14, -15, 14], "hot"))
        return els
    return hcyl("shell", "y", 15, -16, 32, "pg") + [
        el("hearth", [-12, -16, -12], [28, -10, 28], "ref"),
        el("spout", [-4, 0, -16], [20, 4, -8], "ref"),
        el("slag_door", [-6, 6, -8.5], [22, 18, -7], "ci")]


m("electric_arc_furnace", "steelmaking", eaf())
m("electric_arc_furnace_roof", "steelmaking", eaf(True))
m("electric_arc_furnace_roof_arcing", "steelmaking", eaf(True, True))

# электрододержатель 1×4×1: стойка с консолью и медной ошиновкой (сегмент 1×3×1); электрод — partial
m("eaf_electrode_mast", "steelmaking", [
    el("mast", [5, -16, 10], [11, 32, 16], "d"),
    el("arm", [6, 24, -16], [10, 28, 10], "d"),
    el("bus_tube", [6.5, 28, -16], [9.5, 30, 10], "cus"),
    el("clamp", [5, 22, -15], [11, 30, -9], "cus"),
])
m("eaf_electrode_mast/electrode", "steelmaking", vcyl("electrode", 8, -12, 2.5, -16, 32, "g", 8), kind="partial")
# печной трансформатор ЭТЦП-12500 2×2×2: бак RAL 7033, радиаторы, вводы
m("arc_furnace_transformer", "steelmaking", [
    el("skid", [0, 0, 0], [32, 2, 32], "d"),
    el("tank", [2, 2, 4], [26, 24, 28], "r32"),
    el("radiators", [26, 4, 4], [32, 22, 28], "rad"),
    el("cover", [1.5, 24, 3.5], [26.5, 25, 28.5], "r32"),
] + [el(f"bushing_{i}", [6 + i * 6, 25, 14], [8 + i * 6, 31, 16], "por") for i in range(3)]
  + [el("lv_bus", [2, 16, 0], [26, 20, 4], "cus")])


# изложница 1×2×1: чугунная изложница на поддоне
def ingot(filled=False):
    els = [el("stool", [0, 0, 0], [16, 2, 16], "ci"),
           el("mould", [2, 2, 2], [14, 30, 14], "ci", faces={"up": "soot"})]
    if filled:
        els.append(el("glow_top", [3, 30, 3], [13, 30.5, 13], "hot"))
    return els


m("ingot_mold", "steelmaking", ingot())
m("ingot_mold_filled", "steelmaking", ingot(True))


# МНЛЗ: промковш на тележке 2×2×2; стопор — partial (ход Y)
def caster(casting=False):
    els = [el("car_w", [0, 0, 4], [4, 14, 28], "cy"), el("car_e", [28, 0, 4], [32, 14, 28], "cy"),
           el("tundish", [2, 14, 4], [30, 24, 28], "riv", faces={"up": "ref"}),
           el("shroud", [14, 4, 14], [18, 14, 18], "ref")]
    if casting:
        els.append(el("glow_stream", [15, 0, 15], [17, 4, 17], "hot"))
    return els


m("continuous_caster", "steelmaking", caster())
m("continuous_caster_casting", "steelmaking", caster(True))
m("continuous_caster/stopper", "steelmaking", vcyl("rod", 16, 16, 1.5, 18, 32, "ref", 8, ), kind="partial")
# кристаллизатор: медная гильза, водяные шланги; гильза — partial (качание по Y)
m("caster_mould", "steelmaking", [
    el("table", [0, 0, 0], [16, 3, 16], "d"),
    el("hose_in", [-0.01, 4, 2], [2, 6, 4], "wat"),
    el("hose_out", [14, 4, 12], [16.01, 6, 14], "wat"),
    el("frame", [1, 3, 1], [15, 4, 15], "d"),
])
m("caster_mould/mould", "steelmaking", [el("jacket", [3, 4, 3], [13, 16, 13], "cus", faces={"up": "hot"})], kind="partial")


# зона вторичного охлаждения: рама роликов, раскалённая заготовка
def strand(hot=False):
    return [el("frame_w", [0, 0, 0], [2, 16, 16], "d"), el("frame_e", [14, 0, 0], [16, 16, 16], "d"),
            el(("glow_" if hot else "") + "billet", [4, 0, 4], [12, 16, 12], "hot" if hot else "soot"),
            el("spray", [2, 7, 2], [14, 8, 3], "wat")]


m("caster_strand_roll", "steelmaking", strand())
m("caster_strand_roll_hot", "steelmaking", strand(True))
m("caster_strand_roll/rolls", "steelmaking", hcyl("roll_n", "x", 1.5, 2, 14, "st", c=(8, 8, 2.5), sides=8)
  + hcyl("roll_s", "x", 1.5, 2, 14, "st", c=(8, 8, 13.5), sides=8), kind="partial")

# ======================================================================
# прокат
# ======================================================================


# методическая печь: сегмент 3×2×3, горелки, смотровые окна
def reheat(lit=False):
    els = [el("body", [-16, 0, -15.5], [32, 26, 32], "brk"),
           el("roof", [-16, 26, -16], [32, 28, 32], "ref"),
           el("skid_pipes", [-16, 6, -14], [32, 7, 30], "wat")]
    for i, x in enumerate((-10, 6, 22)):
        els += [el(f"burner_{i}", [x - 2, 28, -4], [x + 2, 32, 0], "d"),
                el(("glow_" if lit else "") + f"window_{i}", [x - 2, 8, -16], [x + 2, 12, -15.5], "hot" if lit else "ci")]
    return els


m("reheating_furnace", "rolling", reheat())
m("reheating_furnace_lit", "rolling", reheat(True))
# клеть дуо 500 3×3×1: синяя станина, нажимные винты; валки — partial (ось X)
m("rolling_mill_stand", "rolling", [
    el("housing_w", [-16, -16, 2], [-10, 30, 14], "mb"), el("housing_e", [26, -16, 2], [32, 30, 14], "mb"),
    el("cap", [-16, 26, 2], [32, 32, 14], "mb"),
    el("screw_w", [-14, 20, 6], [-12, 26, 10], "st"), el("screw_e", [28, 20, 6], [30, 26, 10], "st"),
    el("chock_w", [-10, -4, 4], [-6, 20, 12], "d"), el("chock_e", [22, -4, 4], [26, 20, 12], "d"),
    el("sole", [-16, -16, 0], [32, -14, 16], "d"),
])
m("rolling_mill_stand/roll_top", "rolling", hcyl("roll", "x", 5, -10, 26, "sm", c=(8, 14, 8)) + shaft_x(2, -16, 32, (8, 14, 8)), kind="partial")
m("rolling_mill_stand/roll_bottom", "rolling", hcyl("roll", "x", 5, -10, 26, "sm", c=(8, 2, 8)) + shaft_x(2, -16, 32, (8, 2, 8)), kind="partial")
# рольганг / холодильник; ролики — partial (ось X)
m("roller_table", "rolling", [el("frame_w", [0, 0, 0], [2, 8, 16], "d"), el("frame_e", [14, 0, 0], [16, 8, 16], "d")])
m("roller_table_cooling_bed", "rolling", [el("frame_w", [0, 0, 0], [2, 8, 16], "d"), el("frame_e", [14, 0, 0], [16, 8, 16], "d"),
                                          el("bed", [2, 7, 0], [14, 8, 16], "bed")])
m("roller_table/rollers", "rolling", hcyl("roll_n", "x", 1.5, 2, 14, "st", c=(8, 7, 4), sides=8)
  + hcyl("roll_s", "x", 1.5, 2, 14, "st", c=(8, 7, 12), sides=8), kind="partial")

# прошивной стан 3×2×1: станина; бочковидные валки — partial (ось X с перекосом 22,5°)
m("piercing_mill", "rolling", [
    el("bed", [-16, 0, 0], [32, 3, 16], "dg"),
    el("housing_n", [-6, 3, 0], [22, 26, 3], "dg"), el("housing_s", [-6, 3, 13], [22, 26, 16], "dg"),
    el("mandrel_bar", [22, 9, 7], [32, 11, 9], "st"),
    el("thrust_block", [26, 3, 5], [32, 15, 11], "d"),
])
m("piercing_mill/roll_l", "rolling", [el("barrel", [-4, 6, 2], [20, 12, 6], "sm", rot=("y", 22.5, [8, 9, 4]))], kind="partial")
m("piercing_mill/roll_r", "rolling", [el("barrel", [-4, 6, 10], [20, 12, 14], "sm", rot=("y", -22.5, [8, 9, 12]))], kind="partial")


# трубоэлектросварочный стан 3×2×1: формовочные клети, индуктор ТВЧ
def welder(welding=False):
    return [el("bed", [-16, 0, 2], [32, 4, 14], "d"),
            el("stand_1", [-14, 4, 2], [-8, 18, 14], "mb"), el("stand_2", [-2, 4, 2], [4, 18, 14], "mb"),
            el("inductor", [10, 6, 4], [16, 14, 12], "cus"),
            el(("glow_" if welding else "") + "seam", [16, 9, 7], [26, 10, 9], "hot" if welding else "st"),
            el("pipe", [16, 6, 5], [32, 12, 11], "sm"),
            el("hf_cabinet", [10, 14, 2], [20, 28, 14], "r35")]


m("pipe_welder", "rolling", welder())
m("pipe_welder_welding", "rolling", welder(True))
m("pipe_welder/forming_rolls", "rolling", hcyl("roll_a", "z", 3, 2, 14, "st", c=(-11, 11, 8), sides=8)
  + hcyl("roll_b", "z", 3, 2, 14, "st", c=(1, 11, 8), sides=8), kind="partial")


# гидропресс труб 3×1×1: заглушки, труба, манометр
def hydrotest(burst=False):
    els = [el("bed", [-16, 0, 2], [32, 3, 14], "d"),
           el("head_w", [-16, 3, 3], [-10, 14, 13], "dg"), el("head_e", [26, 3, 3], [32, 14, 13], "dg"),
           el("gauge", [-15, 14, 6], [-11, 18, 7], "dial", faces={"north": "dial"}, full=["north"])]
    if burst:
        els += [el("pipe_a", [-10, 5, 5], [6, 11, 11], "sm"), el("pipe_b", [10, 5, 5], [26, 11, 11], "sm"),
                el("split", [6, 4, 4], [10, 12, 12], "rust")]
    else:
        els.append(el("pipe", [-10, 5, 5], [26, 11, 11], "sm"))
    return els


m("hydrotest_bench", "rolling", hydrotest())
m("hydrotest_bench_burst", "rolling", hydrotest(True))


# термическая печь 2×2×2: футерованная камера, подъёмная дверь
def heat_treat(lit=False, door_open=False):
    els = [el("body", [0, 0, 2], [32, 28, 32], "p"),
           el("roof", [0, 28, 2], [32, 30, 32], "d"),
           el("door_frame", [4, 2, 0], [28, 26, 2], "d")]
    if door_open:
        els += [el("door", [6, 26, 0], [26, 32, 1.5], "ins"),
                el(("glow_" if lit else "") + "chamber", [6, 4, 1.9], [26, 22, 2], "hot" if lit else "ref")]
    else:
        els.append(el("door", [6, 4, 0], [26, 24, 1.5], "ins"))
    els.append(lamp("lamp", 29, 24, 2, lit, "lo"))
    return els


m("heat_treatment_furnace", "rolling", heat_treat())
m("heat_treatment_furnace_lit", "rolling", heat_treat(True))
m("heat_treatment_furnace_open", "rolling", heat_treat(True, True))


# закалочный бак 2×1×2: вода / масло
def quench(medium="water"):
    return [el("wall_n", [0, 0, 0], [32, 14, 1], "p"), el("wall_s", [0, 0, 31], [32, 14, 32], "p"),
            el("wall_w", [0, 0, 1], [1, 14, 31], "p"), el("wall_e", [31, 0, 1], [32, 14, 31], "p"),
            el("bottom", [1, 0, 1], [31, 1, 31], "p"),
            el("medium", [1, 1, 1], [31, 12, 31], "water" if medium == "water" else "oil"),
            el("rim", [0, 14, 0], [32, 15, 2], "d")]


m("quench_tank", "rolling", quench())
m("quench_tank_oil", "rolling", quench("oil"))

# волочильный стан 2×1×1: волоки; барабан — partial (ось Y)
m("wire_drawing_bench", "rolling", [
    el("bed", [0, 0, 2], [32, 8, 14], "mach"),
    el("die_box", [2, 8, 5], [8, 13, 11], "d"),
    el("wire", [8, 10, 7.75], [20, 10.5, 8.25], "st"),
    el("gearbox", [18, 0, 2], [30, 6, 14], "mach"),
])
m("wire_drawing_bench/drum", "rolling", vcyl("drum", 24, 8, 5, 8, 14, "st") + vcyl("flange", 24, 8, 6, 13, 14, "mach"), kind="partial")
# канатовьющая машина 3×2×1: станина; ротор с катушками — partial (ось X)
m("rope_stranding_machine", "rolling", [
    el("bed", [-16, 0, 0], [32, 3, 16], "mach"),
    el("bearing_w", [-16, 3, 4], [-12, 18, 12], "mach"), el("bearing_e", [24, 3, 4], [28, 18, 12], "mach"),
    el("closing_die", [28, 8, 6], [32, 12, 10], "d"),
    el("capstan", [28, 3, 2], [32, 8, 14], "mach"),
])
m("rope_stranding_machine/cradle", "rolling", hcyl("cage", "x", 9, -12, 24, "mesh", c=(8, 10, 8))
  + [x for i, xx in enumerate((-8, 4, 16)) for x in hcyl(f"bobbin_{i}", "x", 3, xx, xx + 6, "rope", c=(8, 10, 8), sides=8)],
  kind="partial")

# ======================================================================
# цветная металлургия
# ======================================================================
# автоклав выщелачивания 2×3×2: вертикальный сосуд, фиолетовые трубы (щёлочь)
m("bayer_digester", "nonferrous", hcyl("vessel", "y", 12, -12, 26, "inss", c=(16, 8, 16)) + hcyl("head", "y", 8, 26, 30, "inss", c=(16, 8, 16))
  + [el("skirt", [8, -16, 8], [24, -12, 24], "d"),
     el("liquor_in", [26, 16, 12], [32, 20, 16], "alk", faces={"east": "end"}),
     el("slurry_out", [0, -8, 14], [6, -4, 18], "alk", faces={"west": "end"}),
     el("steam_in", [14, 30, 14], [18, 32, 18], "steam")])
# декомпозёр 2×3×2: бак, привод мешалки; мешалка — partial (ось Y)
m("alumina_precipitator", "nonferrous", hcyl("tank", "y", 14, -16, 26, "p", c=(16, 8, 16)) + [
    el("bridge", [0, 26, 14], [32, 28, 18], "d"),
    el("drive", [12, 28, 12], [20, 32, 20], "mb"),
    el("overflow", [28, 20, 14], [32, 23, 18], "alk")])
m("alumina_precipitator/agitator", "nonferrous", vcyl("shaft", 16, 16, 1, -12, 28, "st", 8) + [
    el("blade_a", [6, -10, 15], [26, -8, 17], "st"), el("blade_b", [15, -10, 6], [17, -8, 26], "st")], kind="partial")


# электролизёр Холла–Эру 3×2×3: стальной кожух, корка электролита; анодная рама — partial
def pot(state="normal"):
    els = [el("shell", [-16, 0, -15], [32, 12, 32], "riv"),
           el("cradles", [-16, 0, -15.5], [32, 2, -15], "d"),
           el("bath", [-14, 11, -14], [30, 12, 30], "crust" if state != "anode_effect" else "hot")]
    if state == "anode_effect":
        els[-1]["name"] = "glow_bath"
    if state == "frozen":
        els.append(el("frozen_ledge", [-14, 12, -14], [30, 13, 30], "lab"))
    els += [el("bus_n", [-16, 6, -16], [32, 9, -15.5], "al"), el("superstructure", [-4, 12, -2], [20, 30, 2], "d")]
    return els


m("reduction_pot", "nonferrous", pot())
m("reduction_pot_anode_effect", "nonferrous", pot("anode_effect"))
m("reduction_pot_frozen", "nonferrous", pot("frozen"))
m("reduction_pot/anode_frame", "nonferrous", [el("beam", [-14, 22, -1], [30, 25, 1], "al")]
  + [el(f"anode_{i}", [x, 10, -10], [x + 6, 16, 26], "g") for i, x in enumerate((-12, -2, 8, 18))]
  + [el(f"rod_{i}", [x + 2, 16, -1], [x + 4, 22, 1], "al") for i, x in enumerate((-12, -2, 8, 18))], kind="partial")


# отражательная печь: сегмент 3×3×3, кирпичный свод, рабочие окна
def matte(lit=False):
    return [el("hearth", [-16, -16, -15.5], [32, 8, 32], "brk"),
            el("roof", [-14, 8, -14], [30, 14, 30], "ref"),
            el("buckstay_n", [-16, -16, -16], [-14, 16, -15], "d"),
            el("buckstay_s", [30, -16, -16], [32, 16, -15], "d"),
            el(("glow_" if lit else "") + "window", [-4, -6, -16], [20, 2, -15.5], "hot" if lit else "ci"),
            el("uptake", [-6, 14, -6], [22, 32, 22], "brk")]


m("matte_smelter", "nonferrous", matte())
m("matte_smelter_lit", "nonferrous", matte(True))
# конвертер Пирса–Смита 3×2×2: опорные ролики; бочка — partial (ось X)
m("peirce_smith_converter", "nonferrous", [
    el("roller_w", [-14, 0, 4], [-8, 6, 28], "d"), el("roller_e", [24, 0, 4], [30, 6, 28], "d"),
    el("drive", [28, 0, 22], [32, 10, 32], "mb"),
    el("tuyere_header", [-12, 14, 28], [28, 18, 32], "air")])
m("peirce_smith_converter/vessel", "nonferrous", hcyl("barrel", "x", 13, -14, 30, "soot", c=(8, 16, 16))
  + hcyl("ring_w", "x", 14, -10, -8, "st", c=(8, 16, 16)) + hcyl("ring_e", "x", 14, 24, 26, "st", c=(8, 16, 16))
  + [el("mouth", [2, 28, 10], [14, 30, 22], "ci"), el("glow_mouth_melt", [3, 27.5, 11], [13, 28, 21], "hot")],
  kind="partial")


# ванна электрорафинирования 2×1×1: ванна с электролитом, пластины анодов/катодов
def refining(ni=False):
    els = [el("tank", [0, 0, 1], [32, 10, 15], "dg"),
           el("electrolyte", [1, 9, 2], [31, 9.5, 14], "elec")]
    for i in range(7):
        x = 2 + i * 4
        els.append(el(f"plate_{i}", [x, 3, 3], [x + 0.75, 14, 13], ("cus" if i % 2 else "lead") if not ni else ("st" if i % 2 else "lead")))
        els.append(el(f"bar_{i}", [x - 0.5, 14, 2], [x + 1.25, 14.75, 14], "cus"))
    return els


m("electrorefining_cell", "nonferrous", refining())
m("electrorefining_cell_ni", "nonferrous", refining(True))

# ======================================================================
# литейное производство
# ======================================================================


# индукционная печь ИСТ-1 1×2×1: медный индуктор, площадка; тигель — partial
def induction(melting=False):
    return [el("frame", [0, 0, 0], [16, 4, 16], "d"),
            el("coil_box", [1, 4, 1], [15, 22, 15], "dg"),
            el("coil_band", [0.5, 8, 0.5], [15.5, 18, 15.5], "cus"),
            el("bus", [15.5, 10, 6], [16.01, 14, 10], "cus"),
            el("hood", [0, 22, 0], [16, 23, 16], "d"),
            lamp("lamp", 3, 20, 1, melting, "lo")]


m("induction_furnace", "foundry", induction())
m("induction_furnace_melting", "foundry", induction(True))
m("induction_furnace/crucible", "foundry", hcyl("crucible", "y", 5, 8, 24, "ref", sides=8) + [el("glow_melt", [4, 23.5, 4], [12, 24, 12], "hot")], kind="partial")


# кокиль: неподвижная половина; подвижная — partial на петле
def perm_mold(poured=False):
    els = [el("base", [0, 0, 0], [16, 2, 16], "d"),
           el("half_fixed", [2, 2, 8], [14, 13, 14], "ci"),
           el("hinge", [1, 2, 7], [2, 13, 9], "st")]
    if poured:
        els.append(el("glow_gate", [7, 13, 7], [9, 13.5, 9], "hot"))
    return els


m("permanent_mold", "foundry", perm_mold())
m("permanent_mold_poured", "foundry", perm_mold(True))
m("permanent_mold/half", "foundry", [el("half", [2, 2, 2], [14, 13, 8], "ci"), el("handle", [7, 6, 0.5], [9, 9, 2], "st")], kind="partial")

# ======================================================================
# механический цех (серо-зелёная станочная эмаль); токарный и сверлильный станки — spec_visual
# ======================================================================
m("milling_machine", "machine_shop", [
    el("column", [3, 0, 8], [13, 28, 16], "mach"),
    el("knee", [2, 6, 0], [14, 12, 8], "mach"),
    el("table", [-0.01, 12, 1], [16.01, 14, 7], "machs"),
    el("overarm", [5, 28, 2], [11, 31, 16], "mach"),
    el("head", [5, 20, 2], [11, 28, 8], "mach"),
])
m("milling_machine/spindle", "machine_shop", vcyl("spindle", 8, 5, 1, 16, 20, "st", 8) + vcyl("cutter", 8, 5, 2, 15, 17, "d", 8), kind="partial")
m("surface_grinder", "machine_shop", [
    el("base", [0, 0, 2], [32, 8, 14], "mach"),
    el("table", [2, 8, 3], [26, 10, 13], "machs"),
    el("chuck", [6, 10, 4], [22, 11, 12], "st"),
    el("column", [26, 0, 4], [32, 22, 12], "mach"),
    el("wheel_head", [12, 16, 5], [28, 22, 11], "mach"),
    el("guard", [10, 13, 4], [16, 20, 12], "mach"),
])
m("surface_grinder/wheel", "machine_shop", hcyl("wheel", "z", 4, 5, 11, "grind", c=(13, 14, 8)), kind="partial")

# ======================================================================
# промышленные газы
# ======================================================================
# центробежный компрессор К-1500 3×2×2: спираль, синие патрубки воздуха; ротор — partial (ось X)
m("asu_main_compressor", "industrial_gases", [
    el("skid", [-16, 0, 0], [32, 3, 32], "d"),
    el("motor", [-16, 3, 6], [-2, 17, 26], "mb"),
    el("gearbox", [-2, 3, 8], [6, 15, 24], "mb"),
    el("stage_1", [6, 3, 6], [16, 21, 26], "p"),
    el("stage_2", [18, 3, 8], [28, 19, 24], "p"),
    el("inlet", [8, 21, 12], [14, 32, 20], "air", faces={"up": "end"}),
    el("intercooler", [16, 21, 4], [32, 29, 28], "air"),
])
m("asu_main_compressor/rotor", "industrial_gases", shaft_x(1.5, -2, 28, (8, 12, 16)), kind="partial")
# блок осушки (молекулярные сита) 1×3×1
m("asu_molsieve", "industrial_gases", hcyl("adsorber", "y", 6.5, -14, 28, "p") + hcyl("head", "y", 4.5, 28, 31, "p", sides=8) + [
    el("skirt", [3, -16, 3], [13, -14, 13], "d"),
    el("air_in", [13, -10, 6], [16.01, -6, 10], "air", faces={"east": "end"}),
    el("air_out", [13, 22, 6], [16.01, 26, 10], "air", faces={"east": "end"}),
    el("regen_heater", [-0.01, 0, 5], [3, 8, 11], "r35")])
m("asu_expander", "industrial_gases", [
    el("skid", [0, 0, 0], [16, 2, 16], "d"),
    el("casing", [3, 2, 3], [13, 12, 13], "fro"),
    el("inlet", [6, 12, 6], [10, 16, 10], "fro", faces={"up": "end"}),
    el("outlet", [13, 4, 6], [16.01, 8, 10], "fro", faces={"east": "end"}),
    el("brake_fan", [4, 4, -0.01], [12, 10, 3], "mb"),
])
m("asu_expander/rotor", "industrial_gases", shaft_z(1, -0.01, 13), kind="partial")


# ЦТК / газификатор 1×3×1: белый двустенный бак или оребрённый испаритель с инеем
def cryo(kind="storage"):
    if kind == "storage":
        return hcyl("tank", "y", 7, -12, 28, "lab") + hcyl("head", "y", 5, 28, 31, "lab", sides=8) + [
            el(f"leg_{i}", [x, -16, z], [x + 2, -12, z + 2], "d") for i, (x, z) in enumerate(((2, 2), (12, 2), (2, 12), (12, 12)))] + [
            el("gauge", [5.5, 10, 0.5], [10.5, 15, 1.5], "dial", faces={"north": "dial"}, full=["north"]),
            el("pressure_builder", [13, -10, 4], [16.01, 0, 12], "fro")]
    return [el("base", [0, -16, 0], [16, -14, 16], "d")] + [
        el(f"fin_{i}", [x, -14, z], [x + 2, 30, z + 2], "fro") for i, (x, z) in enumerate(((1, 1), (7, 1), (13, 1), (1, 7), (13, 7), (1, 13), (7, 13), (13, 13)))] + [
        el("core", [6, -14, 6], [10, 30, 10], "fro"), el("header", [0, 30, 0], [16, 32, 16], "st")]


m("cryogenic_tank", "industrial_gases", cryo())
m("cryogenic_tank_vaporizer", "industrial_gases", cryo("vaporizer"))

# ======================================================================
# водоподготовка
# ======================================================================
# осветлитель ВТИ 3×2×3: бетонная чаша, зелёная вода; ферма скребка — partial (ось Y)
m("water_clarifier", "water_treatment", [
    el("bowl_n", [-16, 0, -16], [32, 18, -14], "c"), el("bowl_s", [-16, 0, 30], [32, 18, 32], "c"),
    el("bowl_w", [-16, 0, -14], [-14, 18, 30], "c"), el("bowl_e", [30, 0, -14], [32, 18, 30], "c"),
    el("floor", [-14, 0, -14], [30, 2, 30], "c"),
    el("water", [-14, 2, -14], [30, 16, 30], "water"),
    el("centre_well", [2, 16, 2], [14, 22, 14], "p"),
    el("launder", [-14, 16, -14], [30, 18, -12], "c"),
])
m("water_clarifier/rake", "water_treatment", [el("bridge", [-14, 22, 6], [30, 24, 10], "d"),
                                              el("drive", [5, 24, 5], [11, 28, 11], "mb"),
                                              el("rake_arm", [-12, 3, 7], [28, 4, 9], "st")] + vcyl("shaft", 8, 8, 1, 4, 22, "st", 8), kind="partial")


# механический фильтр 1×2×1: вертикальный сосуд, зелёные трубы; при backwash — лампа
def sand_filter(backwash=False):
    return hcyl("vessel", "y", 7, 2, 28, "p") + hcyl("head", "y", 5, 28, 31, "p", sides=8) + [
        el(f"leg_{i}", [x, 0, z], [x + 2, 3, z + 2], "d") for i, (x, z) in enumerate(((2, 2), (12, 2), (2, 12), (12, 12)))] + [
        el("inlet", [6, 22, -0.01], [10, 26, 2], "wat", faces={"north": "end"}),
        el("outlet", [6, 4, -0.01], [10, 8, 2], "wat", faces={"north": "end"}),
        el("manhole", [5, 12, 0.5], [11, 18, 1.5], "d"),
        lamp("lamp_backwash", 13, 28, 2, backwash, "ly", 0.6)]


m("sand_filter", "water_treatment", sand_filter())
m("sand_filter_backwash", "water_treatment", sand_filter(True))
# декарбонизатор 1×3×1: колонна с насадкой, вентилятор
m("decarbonizer", "water_treatment", hcyl("column", "y", 6.5, -12, 28, "p") + [
    el("sump", [1, -16, 1], [15, -12, 15], "c"),
    el("fan_box", [12, -10, 4], [16.01, -2, 12], "mb"),
    el("water_in", [6, 28, 6], [10, 32, 10], "wat", faces={"up": "end"}),
    el("vent", [3, 26, 3], [6, 30, 6], "air")])
# деаэратор ДА-25 3×2×1: горизонтальный бак, колонка; красная (пар) и зелёная трубы
m("deaerator", "water_treatment", hcyl("tank", "x", 7, -16, 32, "p", c=(8, 9, 8), ends="d") + hcyl("column", "y", 4.5, 16, 30, "p", c=(-4, 8, 8))
  + [el("saddle_w", [-12, 0, 2], [-8, 4, 14], "d"), el("saddle_e", [24, 0, 2], [28, 4, 14], "d"),
     el("steam_in", [20, 16, 6], [24, 26, 10], "steam"),
     el("water_in", [-6, 30, 6], [-2, 32, 10], "wat", faces={"up": "end"}),
     el("outlet", [6, 0, 6], [10, 3, 10], "wat")])
# дозировочный комплекс 2×1×1: бачки, плунжерные насосы; головка насоса — partial (ход плунжера)
m("chemical_dosing_skid", "water_treatment", [
    el("skid", [0, 0, 0], [32, 2, 16], "d"),
    el("tank_a", [1, 2, 6], [9, 14, 15], "lab"),
    el("tank_b", [11, 2, 6], [19, 14, 15], "lab"),
    el("pump_motor", [22, 2, 4], [30, 8, 10], "mb"),
    el("dosing_line", [0, 3, 2], [32, 4, 3], "acid"),
])
m("chemical_dosing_skid/pump_head", "water_treatment", [el("head", [22, 8, 4], [26, 12, 8], "st"),
                                                         el("plunger", [23.5, 12, 5.5], [24.5, 15, 6.5], "sm")], kind="partial")
