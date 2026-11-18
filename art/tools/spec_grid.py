"""07: энергосеть. Валы Create — вдоль Z (выход вала на north), pivot [8,8,8].
Мультиблоки 3×3×3 — модель-сегмент с якорем в центральном блоке (элементы −16…32)."""
from artkit import *

G = lambda n: f"block/common/grid_{n}.png"
OWN = {
    "en": G("enamel_green"), "enf": G("fins_green"), "mf": G("motor_fins"), "por": G("porcelain"),
    "porg": G("porcelain_grey"), "pol": G("polymer"), "al": G("aluminium"), "r32": G("ral7032"),
    "cor": G("corrugated"), "rad": G("radiator"), "wood": G("wood_pole"), "cpol": G("concrete_pole"),
    "lat": G("lattice"), "cblk": G("cable_black"), "cub": G("copper_bar"), "riv": G("riveted"),
    "brk": G("brick"), "rcb": G("rc_band"), "rcw": G("rc_white"), "grate": G("glow_grate"),
    "ci": G("cast_iron"), "clad": G("cladding"), "ww": G("waterwall"), "wws": G("waterwall_slag"),
    "cst": G("coils_steam"), "cwt": G("coils_water"), "cair": G("coils_air"), "arm": G("armour"),
    "r35": G("ral7035"), "dg": G("dark_grey"), "hg": G("glow_mercury"),
    "swd": B("switchgear_cell_door"), "lvf": B("lv_switchboard_front"), "rcd": B("relay_cabinet_door"),
    "fef": B("grid_fe_interface_front"), "emf": B("energy_meter_face"), "syn": B("instrument_synchroscope_dial"),
    "ldb": B("lv_distribution_box_front"), "bms": B("water_tube_boiler_panel"),
    "fdoor": B("fire_tube_boiler_door"), "fdoorl": B("fire_tube_boiler_door_lit"),
    "csign": B("grid_consumer_sign"), "win": B("grid_consumer_windows"),
}


def m(id_, cat, els, kind="block"):
    mk(id_, cat, els, OWN, kind)


def lamp(name, x, y, z, on, col, s=0.75, side="north"):
    """Лампа на грани: north — плоскость z (выступ к −z), up — плоскость y."""
    tex = col if on else "loff"
    pre = "glow_" if on else ""
    if side == "up":
        return el(pre + name, [x - s, y, z - s], [x + s, y + 0.75, z + s], tex)
    return el(pre + name, [x - s, y - s, z - 0.6], [x + s, y + s, z], tex)


def insulator(name, x, z, y0, h, tex="por", r=1.5):
    """Опорный изолятор: стопка юбок + стальной колпак."""
    els = disc(f"{name}_core", "y", r * 0.6, y0, y0 + h, tex, 8, c=(x, 8, z))
    for i, y in enumerate(range(int(y0) + 1, int(y0 + h) - 1, 2)):
        els.append(el(f"{name}_shed_{i}", [x - r, y, z - r], [x + r, y + 0.75, z + r], tex))
    els.append(el(f"{name}_cap", [x - r * 0.7, y0 + h, z - r * 0.7], [x + r * 0.7, y0 + h + 1, z + r * 0.7], "st"))
    return els


# ======================================================================
# генераторы и двигатели на валу Create
# ======================================================================
# синхронный генератор, возбудитель и асинхронный двигатель нарисованы вручную в Blockbench:
# art/blockbench/grid/rotating_machines.author.js (варианты возбудителя ниже — бэклог, блоков нет)


# возбудитель ВТ-450: машинный (коллектор — partial), тиристорный шкаф, бесщёточный
def exciter(kind="machine", forcing=False):
    if kind == "thyristor":
        els = [el("cabinet", [1, 0, 2], [15, 15, 15], "r35"),
               el("vent", [3, 10, 1.5], [13, 14, 2], "louv"),
               el("plate", [5, 5, 1.5], [11, 7, 2], "pns")]
        z = 1.5
    else:
        els = [el("feet", [2, 0, 2], [14, 2, 14], "d"),
               el("frame", [2, 2, 3], [14, 14, 13], "en", faces={"north": "d", "south": "d"}),
               el("brush_gear", [3, 13, 5], [13, 15, 11], "bs" if kind == "machine" else "en")]
        if kind == "machine":
            els += [el(f"brush_{i}", [4 + i * 3, 14, 2], [5 + i * 3, 15.5, 3], "bs") for i in range(3)]
        else:
            els.append(el("diode_wheel", [4, 4, 13], [12, 12, 15], "cus"))
        z = 3
    els.append(lamp("lamp_forcing", 12.5, 12, z, forcing, "lo"))
    return els


m("generator_exciter_forcing", "grid", exciter(forcing=True))
m("generator_exciter_thyristor", "grid", exciter("thyristor"))
m("generator_exciter_brushless", "grid", exciter("brushless"))
m("generator_exciter/commutator", "grid",
  disc("commutator", "z", 2.5, 3.5, 12.5, "cus") + disc("shaft", "z", 1, -0.01, 16.01, "st", 8), kind="partial")

# синхроноскоп Э-327: щитовой прибор, шкала 32×32; стрелку рисует BER
# нарисовано вручную в Blockbench: art/blockbench/control/panel_devices.author.js



# ======================================================================
# шины, кабели, изоляторы (multipart: центр + рукав к грани north)
# ======================================================================
# ОРУ 110 кВ: трубчатая шина (busbar, busbar_side) нарисован вручную в Blockbench: art/blockbench/grid/grid_lines.author.js
# ошиновка серии электролизёров: пакет алюминиевых полос
m("busbar_dc", "grid", [el(f"bar_{i}", [4 + i * 3, 4, 4], [6 + i * 3, 12, 12], "al") for i in range(3)]
  + [el("spacer", [3.5, 7, 7], [12.5, 9, 9], "dg")])
m("busbar_dc_side", "grid", [el(f"bar_{i}", [4 + i * 3, 4, 0], [6 + i * 3, 12, 4], "al") for i in range(3)])
# силовой кабель (power_cable, power_cable_side) нарисован вручную в Blockbench: art/blockbench/grid/grid_lines.author.js



# ограничитель перенапряжений ОПН-110 (полимер) / разрядник РВС (фарфор)
def arrester(tex="pol", failed=False):
    els = [el("base", [4, 0, 4], [12, 1.5, 12], "gal"),
           el("counter", [11.5, 1.5, 6.5], [14, 4.5, 9.5], "dg")]
    els += insulator("column", 8, 8, 1.5, 12, tex, 2)
    els.append(el("terminal", [7, 14.5, 7], [9, 16, 9], "al"))
    if failed:
        els += [el("soot", [5.8, 6, 5.8], [10.2, 9, 10.2], "ci"),
                el("disconnector_dropped", [8.5, 0, 9], [9.5, 2, 13], "d")]
    return els


m("surge_arrester", "grid", arrester())
m("surge_arrester_valve", "grid", arrester("porg"))
m("surge_arrester_failed", "grid", arrester(failed=True))

# заземлитель: медно-стальной стержень, хомут, полоса
m("ground_electrode", "grid", [
    el("rod", [7.25, 0, 7.25], [8.75, 12, 8.75], "cus"),
    el("clamp", [6.5, 9, 6.5], [9.5, 11, 9.5], "st"),
    el("strip", [8, 9.5, 7.5], [16, 10.5, 8.5], "gal"),
    el("bolt", [9.5, 8.5, 7], [10.5, 11.5, 9], "d"),
])

# роликовый и штыревой изоляторы (wire_connector_lv/mv) и щиток ЩРН (lv_distribution_box) нарисованы вручную в Blockbench: art/blockbench/grid/grid_lines.author.js


# ======================================================================
# коммутация и подстанции
# ======================================================================
# масляный выключатель ВМТ-110 (3 бака) / элегазовый ВГТ-110 (3 колонны); сегмент 3×3×1, якорь — центр
def hv_breaker(sf6=False, closed=True):
    els = [el("frame", [-15, -16, 4], [31, -14, 12], "gal"),
           el("drive_cabinet", [-14, -14, 1], [-6, -2, 6], "r35")]
    for i, x in enumerate((-8, 8, 24)):
        if sf6:
            els += disc(f"tank_{i}", "y", 3, -14, -6, "r35", 8, c=(x, 8, 8))
            els += insulator(f"col_{i}", x, 8, -6, 24, "pol", 2.5)
        else:
            els += disc(f"tank_{i}", "y", 5, -14, 4, "r32", 16, c=(x, 8, 8))
            els += insulator(f"bush_a{i}", x - 2.5, 8, 4, 16, "por", 1.5)
            els += insulator(f"bush_b{i}", x + 2.5, 8, 4, 16, "por", 1.5)
    els.append(lamp("indicator", -10, -6, 1, True, "lr" if closed else "lg", 1))
    return els


m("hv_circuit_breaker", "grid", hv_breaker())
m("hv_circuit_breaker_open", "grid", hv_breaker(closed=False))
m("hv_circuit_breaker_sf6", "grid", hv_breaker(sf6=True))


# разъединитель РДЗ-110: рама, три фарфоровые колонки; главный нож — partial
# разъединитель с ножом — art/blockbench/grid/switchgear.author.js


# ячейка КРУ — art/blockbench/grid/switchgear.author.js


# щит 0,4 кВ — art/blockbench/grid/switchgear.author.js


# панель РЗА ЭПЗ-1636 1×2×1: стеклянная дверь, ряды реле, блинкеры
def relay_cab(state="normal"):
    els = [el("body", [0, 0, 1.5], [16, 32, 16], "r35"),
           el("plinth", [0, 0, 0.5], [16, 1, 16], "dg")]
    for row, y in enumerate((5, 13, 21)):
        for i in range(3):
            els += disc(f"relay_{row}_{i}", "z", 1.8, 3, 6, "dg", 8, c=(3 + i * 5, y, 8))
    els.append(el("glass_door", [0.5, 1, 1], [15.5, 31, 1.5], "rcd", full=["north"]))
    col = {"normal": None, "picked": "ly", "tripped": "lr"}[state]
    for i in range(4):
        els.append(lamp(f"blinker_{i}", 3 + i * 3.3, 28, 1, col is not None, col or "ly", 0.6))
    return els


# нарисовано вручную в Blockbench: art/blockbench/control/panel_devices.author.js

# масляный трансформатор ТМГ — art/blockbench/grid/switchgear.author.js
# силовой трансформатор ТДН-16000/110: сегмент «бак с вводами» и сегмент радиаторов; якорь — центр 3×3×3
def power_tx_tank(hv="por", hv_h=16, cons=True):
    els = [el("rails", [-14, -16, -10], [30, -15, 26], "d"),
           el("tank", [-12, -15, -8], [28, 10, 24], "r32"),
           el("cover", [-13, 10, -9], [29, 11.5, 25], "r32"),
           el("lv_box", [-12, 0, -10], [4, 8, -8], "r32"),
           el("ladder", [26, -15, -10], [28, 10, -9], "gal")]
    for i, x in enumerate((-6, 8, 22)):
        els += insulator(f"hv_bush_{i}", x, 0, 11.5, hv_h, hv, 2.5)
    for i, x in enumerate((-6, 2)):
        els += insulator(f"lv_bush_{i}", x, 18, 11.5, 6, "por", 1.5)
    if cons:
        els += disc("conservator", "x", 3.5, -12, 20, "r32", 8, c=(4, 26, 20))
        els += [el("cons_leg", [12, 11.5, 19], [13, 23, 21], "d"),
                el("pipe_to_buchholz", [10, 15, 15], [11.5, 23, 17], "r32")]
    return els


m("power_transformer", "grid", power_tx_tank())
m("power_transformer_radiators", "grid", [
    el("header_top", [-14, 6, -6], [30, 8, -4], "r32"),
    el("header_bot", [-14, -12, -6], [30, -10, -4], "r32"),
] + [el(f"fin_{i}", [-13 + i * 4, -12, -4], [-11 + i * 4, 8, 22], "rad") for i in range(11)]
  + [el("fan_mount", [-6, -14, 22], [22, -12, 24], "d")])
m("power_transformer/fan", "grid", [
    el("ring", [3, 3, 0], [13, 13, 1], "d"),
    el("blade_x", [3.5, 7.25, 0.25], [12.5, 8.75, 0.75], "gal"),
    el("blade_y", [7.25, 3.5, 0.25], [8.75, 12.5, 0.75], "gal"),
    el("hub", [7, 7, -0.5], [9, 9, 1.5], "d"),
], kind="partial")
# ТМН-6300/35: один сегмент 3×3×3, гофры, три ввода 35 кВ
m("power_transformer_35", "grid", [
    el("skid", [-12, -16, -8], [28, -14, 24], "d"),
    el("tank", [-10, -14, -6], [26, 8, 22], "cor", faces={"up": "r32", "down": "r32"}),
    el("cover", [-11, 8, -7], [27, 9.5, 23], "r32"),
] + [x for i, xx in enumerate((-4, 8, 20)) for x in insulator(f"hv_{i}", xx, 2, 9.5, 11, "porg", 2)]
  + disc("conservator", "x", 3, -6, 18, "r32", 8, c=(4, 24, 18))
  + [el("cons_leg", [6, 9.5, 17], [7, 21, 19], "d")])


# выпрямительный агрегат (rectifier_unit и состояния, ртутный вариант) нарисован вручную в Blockbench: art/blockbench/grid/grid_lines.author.js


# ======================================================================
# опоры ВЛ
# ======================================================================
# деревянная и бетонная опоры ВЛ (wooden_pole, concrete_pole: base/middle/top) нарисованы вручную в Blockbench: art/blockbench/grid/grid_lines.author.js


# опора П110-3: решётчатая ферма 3×21×3, секции 3×3×3; якорь — центр секции
def tower_section(w0, w1):
    """Усечённая пирамида пояса: четыре угловых уголка + грани-решётки с прозрачностью."""
    els = []
    for i, (sx, sz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        x0 = 8 + sx * w0 - (1 if sx > 0 else 0)
        z0 = 8 + sz * w0 - (1 if sz > 0 else 0)
        els.append(el(f"leg_{i}", [x0, -16, z0], [x0 + 1, 32, z0 + 1], "gal"))
    els += [el("face_n", [8 - w1, -16, 8 - w1], [8 + w1, 32, 8 - w1 + 0.1], "lat"),
            el("face_s", [8 - w1, -16, 8 + w1 - 0.1], [8 + w1, 32, 8 + w1], "lat"),
            el("face_w", [8 - w1, -16, 8 - w1], [8 - w1 + 0.1, 32, 8 + w1], "lat"),
            el("face_e", [8 + w1 - 0.1, -16, 8 - w1], [8 + w1, 32, 8 + w1], "lat")]
    return els


m("transmission_tower", "grid", tower_section(14, 13))
m("transmission_tower_top", "grid", tower_section(6, 5)[:4] + [
    el("crossarm_low", [-16, -4, 6], [32, -2, 10], "gal"),
    el("crossarm_high", [-12, 14, 6], [28, 16, 10], "gal"),
    el("peak", [7, 16, 7], [9, 32, 9], "gal"),
    el("ground_wire_clamp", [6.5, 31, 6.5], [9.5, 32, 9.5], "d"),
] + [x for i, (xx, yy) in enumerate(((-14, -4), (30, -4), (-10, 14))) for x in (
    el(f"string_{i}", [xx - 1, yy - 10, 7], [xx + 1, yy, 9], "porg"),
    el(f"clamp_{i}", [xx - 1.5, yy - 11, 6.5], [xx + 1.5, yy - 10, 9.5], "al"))])
m("transmission_tower_base", "grid", tower_section(14, 13)[:4] + [
    el(f"footing_{i}", [x - 2, -16, z - 2], [x + 2, -12, z + 2], "c")
    for i, (x, z) in enumerate(((-6, -6), (22, -6), (-6, 22), (22, 22)))])


# ТП 10/0,4 кВ потребителя с посёлком (grid_consumer, grid_consumer_dark) нарисована вручную в Blockbench: art/blockbench/grid/grid_lines.author.js


# инверторный шкаф: медные шины сверху, индикатор направления
def fe_iface(mode="off"):
    col = {"off": None, "import": "lb", "export": "lo"}[mode]
    return [el("cabinet", [1, 0, 2], [15, 15, 15], "r35", faces={"north": "fef"}, full=["north"]),
            el("bus_a", [3, 15, 6], [6, 16.5, 11], "cub"),
            el("bus_b", [10, 15, 6], [13, 16.5, 11], "cub"),
            lamp("arrow", 8, 4, 2, col is not None, col or "lb", 1.25)]


m("grid_fe_interface", "grid", fe_iface())
m("grid_fe_interface_import", "grid", fe_iface("import"))
m("grid_fe_interface_export", "grid", fe_iface("export"))

# счётчик САЗУ / СЭТ-4ТМ: настенный корпус с окном; цифры рисует BER
# нарисовано вручную в Blockbench: art/blockbench/control/panel_devices.author.js

# ======================================================================
# котлы и паровые машины
# ======================================================================


# дымовая труба: железобетонная с поясами (I1+), звенья middle/top; кирпичная I0 — spec_visual
def chimney(tex, part, r=6):
    els = disc("stack", "y", r, 0, 16, tex, 8)
    if part == "top":
        els += disc("cornice", "y", r + 1, 14, 16, "rcb", 8)
        els.append(el("lightning_rod", [7.75, 16, 7.75], [8.25, 22, 8.25], "cus"))
    return els


m("chimney_concrete", "boilers", chimney("rcw", "middle"))
m("chimney_concrete_top", "boilers", chimney("rcb", "top"))


# щит котла, экраны топки, горелки, пароперегреватель, экономайзер, ТВП, дутьевой вентилятор и дымосос с крыльчатками нарисованы вручную в Blockbench: art/blockbench/boilers/water_tube_boiler.author.js

# шаровая барабанная мельница ШБМ 3×3×3: опоры, загрузочная и разгрузочная течки; барабан — partial (ось X)
m("coal_mill", "boilers", [
    el("foundation", [-16, -16, -12], [32, -12, 28], "c"),
    el("bearing_w", [-16, -12, 2], [-12, 10, 14], "ci"),
    el("bearing_e", [28, -12, 2], [32, 10, 14], "ci"),
    el("feed_chute", [-16, 10, 4], [-10, 28, 12], "d"),
    el("discharge", [28, 10, 4], [32, 30, 12], "d"),
    el("girth_guard", [18, -12, -10], [24, 22, -8], "h"),
])
m("coal_mill/drum", "boilers", disc("drum", "x", 12, -12, 28, "arm") + [
    el("girth_gear", [18, -5, -5], [22, 21, 21], "ci")], kind="partial")
