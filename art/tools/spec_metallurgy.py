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
# арка СВП нарисована вручную в Blockbench: art/blockbench/mining/mine_support.author.js

# главный вентилятор нарисован вручную в Blockbench: art/blockbench/mining/main_mine_fan.author.js


# вентиляционная дверь и жалюзийный регулятор нарисованы вручную в Blockbench: art/blockbench/mining/ventilation_door.author.js

# сланцевый и водяной заслоны нарисованы вручную в Blockbench: art/blockbench/mining/explosion_barrier.author.js

# дегазационная скважина нарисована вручную в Blockbench: art/blockbench/mining/degasification_pump.author.js
# шкаф АГЗ «Метан» нарисован вручную в Blockbench: art/blockbench/mining/mine_dispatch_cabinet.author.js

# копёр, шкив и клеть нарисованы вручную в Blockbench: art/blockbench/mining/headframe.author.js
# (текстуры textures/block/shaft/)

# ======================================================================
# углеобогащение
# ======================================================================
# отсадочная машина ОМ — корпус в spec_visual; пульсатор (ось X на y 27, z 8)
# пульсатор отсадочной машины — art/blockbench/coal_prep/coal_jig.author.js

# тяжелосредный сепаратор СКВ 2×2×2: опорная рама, жёлоб суспензии; барабан — partial (ось X)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js


# флотомашина ФМР: бак, пеногон; импеллер — partial (ось Y)
def flot(froth=False):
    return [el("tank_w", [0, 0, 0], [1.5, 15, 16], "p"), el("tank_e", [14.5, 0, 0], [16, 15, 16], "p"),
            el("tank_n", [1.5, 0, 0], [14.5, 15, 1.5], "p"), el("tank_s", [1.5, 0, 14.5], [14.5, 15, 16], "p"),
            el("bottom", [1.5, 0, 1.5], [14.5, 1, 14.5], "p"),
            el("pulp", [1.5, 1, 1.5], [14.5, 13, 14.5], "froth" if froth else "pulp"),
            el("launder", [-1, 12, 2], [1, 14, 14], "p"),
            el("bridge", [0, 15, 7], [16, 16, 9], "d"),
            el("drive", [6, 16, 5.5], [10, 16.01, 10.5], "mb")]


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js

# магнитный сепаратор ПБМ 2×1×1: ванна с пульпой; барабан — partial (ось X)
# нарисовано вручную в Blockbench: art/blockbench/water_treatment/plant_utilities.author.js

# ======================================================================
# коксохим
# ======================================================================


# простенок коксовой батареи: динас, смотровые лючки (раскалены при hot)
def coke_wall(hot=False):
    return [el("wall", [0, 0, 0], [16, 16, 16], "din")] + [
        el(("glow_" if hot else "") + f"peep_{i}", [x, 15.9, 6], [x + 3, 16.5, 10], "hot" if hot else "ci")
        for i, x in enumerate((2, 11))]


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js
# коксовыталкиватель: корпус привода; штанга с башмаком — partial (ход по Z)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js
# газосборник: толстая жёлтая труба, стояк сверху
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js

# тушильная башня 3×3×3: бетонная шахта, вытяжной раструб, рельсы тушильного вагона
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js


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


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js


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


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js

# нагнетатель коксового газа 2×2×1: серая спираль, жёлтые патрубки; ротор — partial (ось Z)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js

# электродный пресс 3×1×1: контейнер, мундштук, гидроцилиндр; шнек — partial (ось X)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js


# кольцевая обжиговая печь 3×2×3: кирпичные камеры, крышки, горелочный мост
def baking(lit=False):
    els = [el("pit", [-16, 0, -16], [32, 14, 32], "brk")]
    for i, x in enumerate((-12, 2, 16)):
        els.append(el(f"lid_{i}", [x, 14, -12], [x + 12, 15, 28], "ci"))
    els += [el("bridge", [-16, 15, 2], [32, 20, 14], "cy"),
            el(("glow_" if lit else "") + "burner_ports", [-10, 14.5, 6], [26, 15.5, 10], "hot" if lit else "ci")]
    return els


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js

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
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js

# кислородная фурма: каретка с шлангами; фурма — partial (ход вниз)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js

# установка доводки 3×2×3: стенд с бункерами ферросплавов
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js


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


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js

# электрододержатель 1×4×1: стойка с консолью и медной ошиновкой (сегмент 1×3×1); электрод — partial
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js
# печной трансформатор ЭТЦП-12500 2×2×2: бак RAL 7033, радиаторы, вводы
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js


# изложница 1×2×1: чугунная изложница на поддоне
def ingot(filled=False):
    els = [el("stool", [0, 0, 0], [16, 2, 16], "ci"),
           el("mould", [2, 2, 2], [14, 30, 14], "ci", faces={"up": "soot"})]
    if filled:
        els.append(el("glow_top", [3, 30, 3], [13, 30.5, 13], "hot"))
    return els


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/steel_coke.author.js


# МНЛЗ: промковш на тележке 2×2×2; стопор — partial (ход Y)
def caster(casting=False):
    els = [el("car_w", [0, 0, 4], [4, 14, 28], "cy"), el("car_e", [28, 0, 4], [32, 14, 28], "cy"),
           el("tundish", [2, 14, 4], [30, 24, 28], "riv", faces={"up": "ref"}),
           el("shroud", [14, 4, 14], [18, 14, 18], "ref")]
    if casting:
        els.append(el("glow_stream", [15, 0, 15], [17, 4, 17], "hot"))
    return els


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js
# кристаллизатор: медная гильза, водяные шланги; гильза — partial (качание по Y)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js


# зона вторичного охлаждения: рама роликов, раскалённая заготовка
def strand(hot=False):
    return [el("frame_w", [0, 0, 0], [2, 16, 16], "d"), el("frame_e", [14, 0, 0], [16, 16, 16], "d"),
            el(("glow_" if hot else "") + "billet", [4, 0, 4], [12, 16, 12], "hot" if hot else "soot"),
            el("spray", [2, 7, 2], [14, 8, 3], "wat")]


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js

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


# нарисовано вручную в Blockbench: art/blockbench/rolling/rolling_mill.author.js
# клеть дуо 500 3×3×1: синяя станина, нажимные винты; валки — partial (ось X)
# нарисовано вручную в Blockbench: art/blockbench/rolling/rolling_mill.author.js
# рольганг / холодильник; ролики — partial (ось X)
# нарисовано вручную в Blockbench: art/blockbench/rolling/rolling_mill.author.js

# прошивной стан 3×2×1: станина; бочковидные валки — partial (ось X с перекосом 22,5°)
# нарисовано вручную в Blockbench: art/blockbench/rolling/rolling_mill.author.js


# трубоэлектросварочный стан 3×2×1: формовочные клети, индуктор ТВЧ
def welder(welding=False):
    return [el("bed", [-16, 0, 2], [32, 4, 14], "d"),
            el("stand_1", [-14, 4, 2], [-8, 18, 14], "mb"), el("stand_2", [-2, 4, 2], [4, 18, 14], "mb"),
            el("inductor", [10, 6, 4], [16, 14, 12], "cus"),
            el(("glow_" if welding else "") + "seam", [16, 9, 7], [26, 10, 9], "hot" if welding else "st"),
            el("pipe", [16, 6, 5], [32, 12, 11], "sm"),
            el("hf_cabinet", [10, 14, 2], [20, 28, 14], "r35")]


# нарисовано вручную в Blockbench: art/blockbench/rolling/rolling_mill.author.js


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


# нарисовано вручную в Blockbench: art/blockbench/rolling/rolling_mill.author.js


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


# нарисовано вручную в Blockbench: art/blockbench/rolling/rolling_mill.author.js


# закалочный бак 2×1×2: вода / масло
def quench(medium="water"):
    return [el("wall_n", [0, 0, 0], [32, 14, 1], "p"), el("wall_s", [0, 0, 31], [32, 14, 32], "p"),
            el("wall_w", [0, 0, 1], [1, 14, 31], "p"), el("wall_e", [31, 0, 1], [32, 14, 31], "p"),
            el("bottom", [1, 0, 1], [31, 1, 31], "p"),
            el("medium", [1, 1, 1], [31, 12, 31], "water" if medium == "water" else "oil"),
            el("rim", [0, 14, 0], [32, 15, 2], "d")]


# нарисовано вручную в Blockbench: art/blockbench/rolling/rolling_mill.author.js

# волочильный стан 2×1×1: волоки; барабан — partial (ось Y)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js
# канатовьющая машина 3×2×1: станина; ротор с катушками — partial (ось X)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js

# ======================================================================
# цветная металлургия
# ======================================================================
# автоклав выщелачивания 2×3×2: вертикальный сосуд, фиолетовые трубы (щёлочь)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js
# декомпозёр 2×3×2: бак, привод мешалки; мешалка — partial (ось Y)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js


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


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js


# отражательная печь: сегмент 3×3×3, кирпичный свод, рабочие окна
def matte(lit=False):
    return [el("hearth", [-16, -16, -15.5], [32, 8, 32], "brk"),
            el("roof", [-14, 8, -14], [30, 14, 30], "ref"),
            el("buckstay_n", [-16, -16, -16], [-14, 16, -15], "d"),
            el("buckstay_s", [30, -16, -16], [32, 16, -15], "d"),
            el(("glow_" if lit else "") + "window", [-4, -6, -16], [20, 2, -15.5], "hot" if lit else "ci"),
            el("uptake", [-6, 14, -6], [22, 32, 22], "brk")]


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js
# конвертер Пирса–Смита 3×2×2: опорные ролики; бочка — partial (ось X)
# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js


# ванна электрорафинирования 2×1×1: ванна с электролитом, пластины анодов/катодов
def refining(ni=False):
    els = [el("tank", [0, 0, 1], [32, 10, 15], "dg"),
           el("electrolyte", [1, 9, 2], [31, 9.5, 14], "elec")]
    for i in range(7):
        x = 2 + i * 4
        els.append(el(f"plate_{i}", [x, 3, 3], [x + 0.75, 14, 13], ("cus" if i % 2 else "lead") if not ni else ("st" if i % 2 else "lead")))
        els.append(el(f"bar_{i}", [x - 0.5, 14, 2], [x + 1.25, 14.75, 14], "cus"))
    return els


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js

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


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js


# кокиль: неподвижная половина; подвижная — partial на петле
def perm_mold(poured=False):
    els = [el("base", [0, 0, 0], [16, 2, 16], "d"),
           el("half_fixed", [2, 2, 8], [14, 13, 14], "ci"),
           el("hinge", [1, 2, 7], [2, 13, 9], "st")]
    if poured:
        els.append(el("glow_gate", [7, 13, 7], [9, 13.5, 9], "hot"))
    return els


# нарисовано вручную в Blockbench: art/blockbench/steelmaking/m3_metallurgy.author.js

# ======================================================================
# механический цех (серо-зелёная станочная эмаль); токарный и сверлильный станки — spec_visual
# ======================================================================
# фрезерный и плоскошлифовальный — art/blockbench/machine_shop/machine_tools.author.js

# ======================================================================
# промышленные газы
# ======================================================================
# центробежный компрессор К-1500 3×2×2: спираль, синие патрубки воздуха; ротор — partial (ось X)
# нарисовано вручную в Blockbench: art/blockbench/water_treatment/plant_utilities.author.js
# блок осушки (молекулярные сита) 1×3×1
# нарисовано вручную в Blockbench: art/blockbench/water_treatment/plant_utilities.author.js


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


# нарисовано вручную в Blockbench: art/blockbench/water_treatment/plant_utilities.author.js

# ======================================================================
# водоподготовка
# ======================================================================
# осветлитель ВТИ 3×2×3: бетонная чаша, зелёная вода; ферма скребка — partial (ось Y)
# нарисовано вручную в Blockbench: art/blockbench/water_treatment/plant_utilities.author.js


# механический фильтр 1×2×1: вертикальный сосуд, зелёные трубы; при backwash — лампа
def sand_filter(backwash=False):
    return hcyl("vessel", "y", 7, 2, 28, "p") + hcyl("head", "y", 5, 28, 31, "p", sides=8) + [
        el(f"leg_{i}", [x, 0, z], [x + 2, 3, z + 2], "d") for i, (x, z) in enumerate(((2, 2), (12, 2), (2, 12), (12, 12)))] + [
        el("inlet", [6, 22, -0.01], [10, 26, 2], "wat", faces={"north": "end"}),
        el("outlet", [6, 4, -0.01], [10, 8, 2], "wat", faces={"north": "end"}),
        el("manhole", [5, 12, 0.5], [11, 18, 1.5], "d"),
        lamp("lamp_backwash", 13, 28, 2, backwash, "ly", 0.6)]


# нарисовано вручную в Blockbench: art/blockbench/water_treatment/plant_utilities.author.js
# декарбонизатор 1×3×1: колонна с насадкой, вентилятор
# нарисовано вручную в Blockbench: art/blockbench/automation/m3_utilities.author.js
# деаэратор ДА-25 3×2×1: горизонтальный бак, колонка; красная (пар) и зелёная трубы
# нарисовано вручную в Blockbench: art/blockbench/water_treatment/plant_utilities.author.js
# дозировочный комплекс 2×1×1: бачки, плунжерные насосы; головка насоса — partial (ход плунжера)
# нарисовано вручную в Blockbench: art/blockbench/automation/m3_utilities.author.js
