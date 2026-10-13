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
# синхронный генератор СГ-500: оребрённый статор, медные выводы, ротор — partial
m("shaft_generator", "grid", [
    el("feet", [1, 0, 2], [15, 2, 14], "d"),
    el("stator_w", [1, 2, 2], [3.5, 14, 14], "enf"),
    el("stator_e", [12.5, 2, 2], [15, 14, 14], "enf"),
    el("stator_t", [3.5, 11.5, 2], [12.5, 14, 14], "enf", faces={"up": "en"}),
    el("stator_b", [3.5, 2, 2], [12.5, 4.5, 14], "en"),
    el("end_shield_n", [3, 3, 0.5], [13, 13, 2], "en"),
    el("end_shield_s", [3, 3, 14], [13, 13, 15.5], "en"),
    el("terminal_box", [5, 14, 6], [11, 16, 11], "en"),
    el("lead_1", [5.5, 15, 11], [6.5, 16, 12.5], "cub"),
    el("lead_2", [7.5, 15, 11], [8.5, 16, 12.5], "cub"),
    el("lead_3", [9.5, 15, 11], [10.5, 16, 12.5], "cub"),
])
m("shaft_generator/rotor", "grid",
  disc("rotor", "z", 3.5, 2.5, 13.5, "cus") + disc("shaft", "z", 1, -0.01, 16.01, "st", 8), kind="partial")


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


m("generator_exciter", "grid", exciter())
m("generator_exciter_forcing", "grid", exciter(forcing=True))
m("generator_exciter_thyristor", "grid", exciter("thyristor"))
m("generator_exciter_brushless", "grid", exciter("brushless"))
m("generator_exciter/commutator", "grid",
  disc("commutator", "z", 2.5, 3.5, 12.5, "cus") + disc("shaft", "z", 1, -0.01, 16.01, "st", 8), kind="partial")

# синхроноскоп Э-327: щитовой прибор, шкала 32×32; стрелку рисует BER
m("instrument_synchroscope", "instruments", [
    el("mount_plate", [2, 2, 15], [14, 14, 16], "d"),
    el("case", [3, 3, 11], [13, 13, 15], "dg"),
    front("dial", [3.5, 3.5, 10.9], [12.5, 12.5, 11], "syn", "dg"),
    el("lamp_w", [3.5, 13, 11.5], [5, 14, 12.5], "loff"),
    el("lamp_e", [11, 13, 11.5], [12.5, 14, 12.5], "loff"),
])


# асинхронный двигатель АИР: оребрённый корпус RAL 5010, клеммная коробка; вал — partial
def motor(mv=False):
    r0 = 1 if mv else 2
    return [
        el("feet", [r0, 0, 2], [16 - r0, 1.5, 14], "d"),
        el("body_w", [r0, 1.5, 2.5], [r0 + 2.5, 13.5, 13.5], "mf"),
        el("body_e", [13.5 - r0, 1.5, 2.5], [16 - r0, 13.5, 13.5], "mf"),
        el("body_t", [r0 + 2.5, 11, 2.5], [13.5 - r0, 13.5 + (1 if mv else 0), 13.5], "mf", faces={"up": "mb"}),
        el("body_b", [r0 + 2.5, 1.5, 2.5], [13.5 - r0, 4, 13.5], "mb"),
        el("flange", [3, 3, 1], [13, 13, 2.5], "mb"),
        el("fan_cowl", [3, 3, 13.5], [13, 13, 15.5], "mb", faces={"south": "louv"}, full=["south"]),
        el("terminal_box", [5, 13.5 + (1 if mv else 0), 6], [11, 15.5 + (1 if mv else 0), 11], "mb"),
        el("gland", [11, 13.5, 8], [12.5, 15, 9.5], "bs"),
    ]


m("electric_motor", "grid", motor())
m("electric_motor_mv", "grid", motor(True))
m("electric_motor/shaft", "grid", disc("shaft", "z", 1, -0.01, 3, "st", 8) + [
    el("key", [7.75, 8.75, 0], [8.25, 9.25, 1.5], "d")], kind="partial")

# ======================================================================
# шины, кабели, изоляторы (multipart: центр + рукав к грани north)
# ======================================================================
# ОРУ 110 кВ: трубчатая шина на опорном изоляторе; цвет фазы — хомут
m("busbar", "grid", [el("pedestal", [5, 0, 5], [11, 1.5, 11], "gal")]
  + insulator("post", 8, 8, 1.5, 10)
  + [el("clamp", [6, 12.5, 6], [10, 14.5, 10], "yel"),
     el("node", [6.5, 13, 6.5], [9.5, 16, 9.5], "al")])
m("busbar_side", "grid", [el("tube", [6.5, 13, 0], [9.5, 16, 6.5], "al")])
# ошиновка серии электролизёров: пакет алюминиевых полос
m("busbar_dc", "grid", [el(f"bar_{i}", [4 + i * 3, 4, 4], [6 + i * 3, 12, 12], "al") for i in range(3)]
  + [el("spacer", [3.5, 7, 7], [12.5, 9, 9], "dg")])
m("busbar_dc_side", "grid", [el(f"bar_{i}", [4 + i * 3, 4, 0], [6 + i * 3, 12, 4], "al") for i in range(3)])
# силовой кабель ААБл / АПвП: чёрная оболочка
m("power_cable", "grid", [el("core", [5.5, 5.5, 5.5], [10.5, 10.5, 10.5], "cblk"),
                          el("band", [5.4, 7.5, 5.4], [10.6, 8.5, 10.6], "yel")])
m("power_cable_side", "grid", [el("arm", [6, 6, 0], [10, 10, 5.5], "cblk")])


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

# роликовый изолятор 0,4 кВ на кронштейне (стена south)
m("wire_connector_lv", "grid", [
    el("bracket", [7, 6, 9], [9, 8, 16], "d"),
    el("plate", [6, 5, 15], [10, 9, 16], "d"),
    el("roller", [6.5, 4, 5.5], [9.5, 10, 8.5], "porg"),
    el("band", [6.4, 8, 5.4], [9.6, 8.75, 8.6], "yel"),
])
# штыревой изолятор ШФ-20 на штыре (стоит на траверсе)
m("wire_connector_mv", "grid", [el("pin", [7.5, 0, 7.5], [8.5, 4, 8.5], "gal")] + insulator("shf", 8, 8, 4, 6, "porg", 2.5)
  + [el("neck", [6.5, 10, 6.5], [9.5, 12, 9.5], "porg")])


# щиток ЩРН с автоматами
def ldb(tripped=False):
    return [el("box", [2, 2, 11], [14, 14, 16], "r35", faces={"north": "ldb"}, full=["north"]),
            el("gland", [6, 0.5, 12], [10, 2, 14], "r"),
            lamp("lamp", 12, 12.5, 11, True, "lr" if tripped else "lg", 0.6)]


m("lv_distribution_box", "grid", ldb())
m("lv_distribution_box_tripped", "grid", ldb(True))


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
def disconnector(earthed=False):
    els = [el("frame", [-15, 0, 6], [31, 2, 10], "gal")]
    for x in (-10, 26):
        els += insulator(f"post_{x}", x, 8, 2, 14, "por", 2)
        els.append(el(f"contact_{x}", [x - 2, 17, 6.5], [x + 2, 19, 9.5], "cus"))
    els.append(el("drive_rod", [-11, 2, 11], [-10, 14, 12], "gal"))
    if earthed:
        els.append(el("earth_blade", [24.5, 2, 10], [25.5, 18, 11], "red"))
    else:
        els.append(el("earth_blade", [25.5, 2, 10], [31, 3, 11], "red"))
    return els


m("disconnector", "grid", disconnector())
m("disconnector_earthed", "grid", disconnector(True))
m("disconnector/blade", "grid", [
    el("hinge", [-12, 17, 6.5], [-8, 19.5, 9.5], "cus"),
    el("blade", [-10, 17.5, 7.25], [26, 18.5, 8.75], "cus"),
], kind="partial")


# ячейка КРУ К-104 1×2×1: шкаф RAL 7035, мнемосхема на двери; выкатной элемент
def switchgear(state="closed"):
    els = [el("body", [0, 0, 1], [16, 31.5, 16], "r35"),
           el("relay_compartment", [0.5, 24, 0.5], [15.5, 31.5, 1], "pns"),
           el("door", [0.5, 1, 0.5], [15.5, 23, 1], "r35", faces={"north": "swd"}, full=["north"]),
           el("handle", [13, 9, -0.5], [14, 14, 0.5], "d"),
           el("bushing_box", [3, 31.5, 5], [13, 32, 13], "dg")]
    on = {"closed": "lr", "open": "lg", "racked_out": "lg", "alarm": "ly"}[state]
    els += [lamp("lamp_state", 4, 28, 0.5, True, on), lamp("lamp_off", 8, 28, 0.5, False, "lg"),
            el("ammeter", [10.5, 26.5, 0], [13.5, 29.5, 0.5], "dial", full=["north"])]
    if state == "racked_out":
        els = [e for e in els if e["name"] not in ("door", "handle")]
        els += [el("trolley", [2, 0, -6], [14, 10, 6], "r35"),
                el("contacts", [4, 7, 6], [12, 9, 8], "cus"),
                el("wheel_w", [2.5, 0, -5], [4, 1.5, -2], "r"),
                el("wheel_e", [12, 0, -5], [13.5, 1.5, -2], "r")]
    return els


m("switchgear_cell", "grid", switchgear())
m("switchgear_cell_open", "grid", switchgear("open"))
m("switchgear_cell_racked_out", "grid", switchgear("racked_out"))
m("switchgear_cell_alarm", "grid", switchgear("alarm"))


# щит 0,4 кВ ЩО-70 1×2×1: рубильники, амперметры, лампы
def lv_board(state="on"):
    return [el("body", [0, 0, 2], [16, 31.5, 16], "r35"),
            el("panel_low", [0.5, 1, 1.5], [15.5, 15, 2], "pns"),
            el("panel_high", [0.5, 15.5, 1.5], [15.5, 31, 2], "r35", faces={"north": "lvf"}, full=["north"]),
            el("cable_duct", [2, 31.5, 4], [14, 32, 14], "dg")]\
        + [el(f"handle_{i}", [3 + i * 5, 6, 0], [4 + i * 5, 10, 1.5], "red") for i in range(3)]\
        + [lamp(f"lamp_{i}", 3.5 + i * 4.5, 13, 1.5, state != "off",
                {"on": "lg", "off": "lg", "overload": "lr"}[state]) for i in range(3)]


m("lv_switchboard", "grid", lv_board())
m("lv_switchboard_off", "grid", lv_board("off"))
m("lv_switchboard_overload", "grid", lv_board("overload"))


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


m("relay_cabinet", "control", relay_cab())
m("relay_cabinet_picked", "control", relay_cab("picked"))
m("relay_cabinet_tripped", "control", relay_cab("tripped"))

# масляный трансформатор ТМГ-1000/10 1×2×1: гофробак RAL 7032, вводы ВН сверху, НН сбоку
m("distribution_transformer", "grid", [
    el("skid", [1, 0, 2], [15, 1.5, 14], "d"),
    el("tank", [2, 1.5, 3], [14, 18, 13], "cor", faces={"up": "r32", "down": "r32"}),
    el("cover", [1.5, 18, 2.5], [14.5, 19, 13.5], "r32"),
    el("lv_box", [4, 9, 1.5], [12, 15, 3], "r32"),
] + [x for i, xx in enumerate((4.5, 8, 11.5)) for x in insulator(f"hv_{i}", xx, 8, 19, 9, "por", 1.25)]
  + [el(f"lv_{i}", [4.5 + i * 2.2, 15, 1.75], [5.5 + i * 2.2, 17, 2.75], "porg") for i in range(4)]
  + [el("oil_gauge", [12, 15, 6], [14.5, 17, 9], "dial", faces={"east": "dial"})])


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


# выпрямительный агрегат 2×2×1: тиристорные шкафы; ртутный колбовый — вариант
def rectifier(state="on", mercury=False):
    els = [el("plinth", [0, 0, 1], [32, 1, 16], "dg"),
           el("cab_w", [0, 1, 2], [15.5, 31, 16], "r35", faces={"north": "louv"}, full=["north"]),
           el("cab_e", [16.5, 1, 2], [32, 31, 16], "r35"),
           el("bus_out", [4, 31, 6], [28, 32, 10], "cub")]
    if mercury:
        els += disc("bulb", "y", 5, 6, 22, "gl", 8, c=(24, 8, 1))
        els += [el("glow_arc", [22, 9, -0.5], [26, 18, 2.5], "hg"),
                el("cathode_pool", [20, 4, -2], [28, 6, 4], "st")]
    else:
        els.append(el("door_e", [17, 2, 1.5], [31.5, 30, 2], "r35", faces={"north": "fef"}, full=["north"]))
    col = {"on": "lg", "off": None, "fault": "lr"}[state]
    els.append(lamp("lamp", 8, 28, 2, col is not None, col or "lg", 1))
    return els


m("rectifier_unit", "grid", rectifier())
m("rectifier_unit_off", "grid", rectifier("off"))
m("rectifier_unit_fault", "grid", rectifier("fault"))
m("rectifier_unit_mercury", "grid", rectifier(mercury=True))


# ======================================================================
# опоры ВЛ
# ======================================================================
def pole(tex, part, r=2.5):
    els = [el("shaft", [8 - r, 0, 8 - r], [8 + r, 16, 8 + r], tex)]
    if part == "bottom":
        els.append(el("butt_band", [8 - r - 0.25, 0, 8 - r - 0.25], [8 + r + 0.25, 3, 8 + r + 0.25], "ci"))
        els.append(el("ground_wire", [8 + r, 0, 7.75], [8 + r + 0.5, 16, 8.25], "gal"))
    if part == "top":
        els += [el("crossarm", [-6, 11, 7], [22, 13, 9], "gal"),
                el("brace_w", [1, 7, 7.25], [8 - r, 8, 8.75], "gal"),
                el("cap", [8 - r, 16, 8 - r], [8 + r, 16.5, 8 + r], "gal")]
        for i, x in enumerate((-4, 4, 20)):
            els += [el(f"pin_{i}", [x - 0.5, 13, 7.5], [x + 0.5, 15, 8.5], "gal"),
                    el(f"ins_{i}", [x - 1.5, 15, 6.5], [x + 1.5, 17, 9.5], "porg"),
                    el(f"ins_head_{i}", [x - 1, 17, 7], [x + 1, 18, 9], "porg")]
    return els


for _p in ("middle", "bottom", "top"):
    sfx = "" if _p == "middle" else f"_{_p}"
    # опора ВЛ 0,4–10 кВ: пропитанное дерево
    m(f"wooden_pole{sfx}", "grid", pole("wood", _p))
    # опора СВ-110: вибробетонная стойка
    m(f"concrete_pole{sfx}", "grid", pole("cpol", _p, 3))


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


# ТП 10/0,4 кВ потребителя: сегмент 3×3×3 — забор, киоск, «посёлок» с окнами
def consumer(dark=False):
    win = "win" if not dark else "gls"
    els = [el("ground", [-16, -16, -16], [32, -15, 32], "c"),
           el("fence_n", [-16, -15, -16], [32, -6, -15.5], "lat"),
           el("fence_w", [-16, -15, -16], [-15.5, -6, 32], "lat"),
           el("kiosk", [-12, -15, -12], [4, 2, 2], "r35"),
           el("kiosk_roof", [-13, 2, -13], [5, 3, 3], "dg"),
           el("sign", [-8, -4, -12.1], [0, 0, -12], "csign", full=["north"]),
           el("tx", [-10, -15, 6], [0, -4, 16], "cor")]
    for i, (x, z) in enumerate(((8, -10), (20, 4), (8, 18))):
        els += [el(f"house_{i}", [x, -15, z], [x + 10, -5, z + 8], "brk"),
                el(f"roof_{i}", [x - 0.5, -5, z - 0.5], [x + 10.5, -3, z + 8.5], "redsm"),
                el(("glow_" if not dark else "") + f"window_{i}", [x + 3, -11, z - 0.1], [x + 7, -8, z], win)]
    return els


m("grid_consumer", "grid", consumer())
m("grid_consumer_dark", "grid", consumer(True))


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
m("energy_meter", "instruments", [
    el("plate", [3, 1, 15], [13, 15, 16], "d"),
    el("case", [3.5, 3, 11], [12.5, 14, 15], "dg"),
    front("face", [4, 5, 10.9], [12, 13.5, 11], "emf", "dg"),
    el("terminal_cover", [4, 1, 11.5], [12, 3, 15], "dg"),
    el("seal", [7.5, 0.5, 11], [8.5, 1.5, 12], "red"),
])

# ======================================================================
# котлы и паровые машины
# ======================================================================


# жаротрубный котёл Ланкашир 3×3×6: передний сегмент (топочная дверца) и задний; якорь — центр
def fire_tube(lit=False):
    return disc("shell", "z", 15, -15.2, 31.9, "riv", faces={"north": "ci"}) + [
        el("front_plate", [-12, -12, -15.5], [28, 28, -15.2], "ci"),
        el("door_l", [-8, -10, -16], [6, 2, -15.5], "fdoorl" if lit else "fdoor", full=["north"]),
        el(("glow_" if lit else "") + "door_r", [10, -10, -16], [24, 2, -15.5], "fdoorl" if lit else "fdoor", full=["north"]),
        el("gauge_glass", [-6, 10, -16], [-4, 18, -15.5], "gl"),
        el("safety_valve", [6, 23, 4], [10, 28, 8], "bs"),
        el("manhole", [4, 22, 14], [12, 24, 22], "d"),
        el("saddle_w", [-10, -16, 0], [-4, -5, 4], "brk"),
        el("saddle_e", [20, -16, 0], [26, -5, 4], "brk"),
    ]


m("fire_tube_boiler", "boilers", fire_tube())
m("fire_tube_boiler_lit", "boilers", fire_tube(True))
m("fire_tube_boiler_rear", "boilers", disc("shell", "z", 15, -15.9, 28, "riv", faces={"south": "ci"}) + [
    el("smokebox", [-10, -16, 28], [26, 20, 32], "ci"),
    el("uptake", [4, 20, 26], [12, 32, 32], "d"),
    el("feed_check", [24, 4, 6], [29, 8, 10], "bs"),
])


# цепная решётка ЛЦР: чугунные колосники, раскалённый слой; полотно — partial
def stoker(running=False):
    return [el("frame_w", [0, 0, 0], [2, 9, 16], "ci"),
            el("frame_e", [14, 0, 0], [16, 9, 16], "ci"),
            el("hopper", [2, 9, 0], [14, 16, 4], "d"),
            el("gate", [2, 9, 4], [14, 11, 5], "ci"),
            el(("glow_" if running else "") + "fire_bed", [2, 7.5, 5], [14, 8.5, 16], "grate" if running else "ci"),
            el("sprocket_housing", [0.5, 2, 13], [15.5, 6, 16], "d")]


m("grate_stoker", "boilers", stoker())
m("grate_stoker_running", "boilers", stoker(True))
m("grate_stoker/chain", "boilers", [
    el("chain_top", [2, 6.5, 0], [14, 7.5, 16], "ci"),
    el("chain_bottom", [2, 1, 0], [14, 2, 16], "ci"),
] + [el(f"link_{i}", [2, 6.25, i * 4], [14, 7.75, i * 4 + 1], "st") for i in range(4)], kind="partial")


# дымовая труба: кирпичная (I0) и железобетонная с поясами (I1+), звенья base/middle/top
def chimney(tex, part, r=6):
    els = disc("stack", "y", r, 0, 16, tex, 8)
    if part == "base":
        els += [el("plinth", [1, 0, 1], [15, 3, 15], "c"),
                el("flue_inlet", [5, 3, -0.5], [11, 9, 2], "ci")]
    if part == "top":
        els += disc("cornice", "y", r + 1, 14, 16, "ci" if tex == "brk" else "rcb", 8)
        els.append(el("lightning_rod", [7.75, 16, 7.75], [8.25, 22, 8.25], "cus"))
    return els


m("chimney", "boilers", chimney("brk", "middle"))
m("chimney_base", "boilers", chimney("brk", "base"))
m("chimney_top", "boilers", chimney("brk", "top"))
m("chimney_concrete", "boilers", chimney("rcw", "middle"))
m("chimney_concrete_top", "boilers", chimney("rcb", "top"))

# паровая машина 3×3×5: цилиндр с золотниковой коробкой + станина; движущиеся части — partial (вал по X)
m("steam_engine_cylinder", "boilers", [
    el("bedplate", [-14, -16, -14], [30, -12, 30], "ci"),
] + disc("cylinder", "z", 8, -14, 6, "riv", faces={"north": "ci", "south": "ci"}, c=(8, 4, 8))
  + [el("valve_chest", [-2, 12, -12], [18, 18, 4], "ci"),
     el("steam_inlet", [5, 18, -6], [11, 28, 0], "steam"),
     el("governor_post", [14, 12, -10], [16, 26, -8], "bs"),
     el("guide_top", [2, 7, 6], [14, 8, 30], "st"),
     el("guide_bottom", [2, 0, 6], [14, 1, 30], "st"),
     el("cylinder_drain", [7, -12, -10], [9, -4, -8], "bs")])
m("steam_engine_cylinder_frame", "boilers", [
    el("bedplate", [-14, -16, -14], [30, -12, 18], "ci"),
    el("bearing_w", [-14, -12, 2], [-8, 10, 14], "ci"),
    el("bearing_e", [24, -12, 2], [30, 10, 14], "ci"),
    el("cap_w", [-14, 10, 4], [-8, 12, 12], "bs"),
    el("cap_e", [24, 10, 4], [30, 12, 12], "bs"),
    el("crank_pit", [-6, -12, -2], [22, -10, 18], "d"),
])
m("steam_engine_cylinder/flywheel", "boilers",
  disc("rim", "x", 15, 20, 24, "ci") + disc("hub", "x", 2.5, 18, 26, "st", 8)
  + [el("spoke_v", [21, -6, 7], [23, 22, 9], "ci"), el("spoke_h", [21, 7, -6], [23, 9, 22], "ci")],
  kind="partial")
m("steam_engine_cylinder/crank", "boilers", disc("shaft", "x", 2, -14, 30, "st", 8) + [
    el("web_w", [-4, 6, 6], [-2, 16, 10], "ci"),
    el("web_e", [6, 6, 6], [8, 16, 10], "ci"),
    el("pin", [-2, 12, 7], [6, 14, 9], "st"),
], kind="partial")
m("steam_engine_cylinder/con_rod", "boilers", [
    el("rod", [1, -1, 0], [3, 1, 20], "st"),
    el("big_end", [0, -2, -1], [4, 2, 1], "bs"),
    el("small_end", [0.5, -1.5, 19], [3.5, 1.5, 21], "bs"),
], kind="partial")
m("steam_engine_cylinder/piston_rod", "boilers", [
    el("rod", [7.5, 3.5, -10], [8.5, 4.5, 14], "st"),
    el("crosshead", [5, 1, 14], [11, 7, 18], "bs"),
], kind="partial")


# водотрубный котёл ТП-35 / БКЗ-75: щит котла (контроллер) с лампами BMS
def wt_boiler(state="standby"):
    lamps = {"standby": (None, None), "firing": ("lg", None), "trip": (None, "lr")}[state]
    return [el("body", [0, 0, 2], [16, 16, 16], "r35"),
            el("panel", [0.5, 0.5, 1.5], [15.5, 15.5, 2], "bms", full=["north"]),
            lamp("lamp_flame", 4, 2.5, 1.5, lamps[0] is not None, "lg"),
            lamp("lamp_trip", 8, 2.5, 1.5, lamps[1] is not None, "lr"),
            lamp("lamp_ready", 12, 2.5, 1.5, state == "standby", "lw"),
            el("estop", [12, 12, 0.5], [14.5, 14.5, 1.5], "red")]


m("water_tube_boiler", "boilers", wt_boiler())
m("water_tube_boiler_firing", "boilers", wt_boiler("firing"))
m("water_tube_boiler_trip", "boilers", wt_boiler("trip"))

# экран топки: снаружи обмуровка RAL 7004, внутри газоплотные трубы
m("boiler_wall", "boilers", [el("block", [0, 0, 0], [16, 16, 16], "clad")])
m("boiler_wall_inner", "boilers", [el("block", [0, 0, 0], [16, 16, 16], "ww")])
m("boiler_wall_slagged", "boilers", [el("block", [0, 0, 0], [16, 16, 16], "wws")])


# горелка ГМП / пылеугольная вихревая: амбразура, регистр воздуха, факел (north — в топку)
def burner(fuel="coal", lit=False):
    pipe = {"coal": "d", "gas": "gas", "oil": "fuel", "lowbtu": "gas"}[fuel]
    els = [el("wall", [0, 0, 8], [16, 16, 16], "clad"),
           el("register", [3, 3, 4], [13, 13, 8], "d", faces={"north": "louv"}),
           el("embrasure", [4, 4, 0], [12, 12, 4], "fb"),
           el("fuel_pipe", [6.5, 6.5, 16], [9.5, 9.5, 16.01], pipe),
           el("air_box", [2, 12, 9], [14, 16, 15], "air")]
    if lit:
        els.append(el("glow_flame", [5, 5, -0.5], [11, 11, 0.5], "fire"))
    return els


m("boiler_burner", "boilers", burner())
m("boiler_burner_lit", "boilers", burner(lit=True))
m("boiler_burner_gas", "boilers", burner("gas"))
m("boiler_burner_gas_lit", "boilers", burner("gas", True))


# пароперегреватель, экономайзер, ТВП: пакеты змеевиков с коллекторами
def coils_block(tex, header):
    return [el("pack", [0.5, 0.5, 0.5], [15.5, 15.5, 15.5], tex),
            el("header_in", [-0.5, 12, 6], [16.5, 14.5, 10], header),
            el("header_out", [-0.5, 1.5, 6], [16.5, 4, 10], header)]


m("boiler_superheater", "boilers", coils_block("cst", "steam"))
m("boiler_economizer", "boilers", coils_block("cwt", "wat"))
m("air_preheater", "boilers", [el("casing", [0, 0, 0], [16, 16, 16], "cair", faces={"up": "louv", "down": "louv"}),
                               el("duct_n", [3, 3, -0.5], [13, 13, 0.5], "air", faces={"north": "louv"})])


# дутьевой вентилятор ВДН / дымосос ДН: улитка, крыльчатка — partial (вал по Z)
def fan(tex, mark):
    return disc("volute", "z", 7, 2, 12, tex, faces={"north": tex}) + [
        el("outlet", [9, 8, 2.5], [15.5, 16, 11.5], tex),
        el("mark", [9, 13, 2.4], [15.5, 14, 11.6], mark),
        el("inlet_cone", [5, 5, 12], [11, 11, 15], tex),
        el("feet", [2, 0, 3], [14, 1.5, 11], "d"),
        el("bearing", [6, 6, 0], [10, 10, 2], "d"),
    ]


m("forced_draft_fan", "boilers", fan("mb", "air"))
m("induced_draft_fan", "boilers", fan("dg", "gas"))
_imp = disc("hub", "z", 1.5, -0.01, 12, "st", 8) + [
    el("blade_x", [2, 7.25, 4], [14, 8.75, 10], "gal"),
    el("blade_y", [7.25, 2, 4], [8.75, 14, 10], "gal"),
    el("blade_d1", [2, 7.25, 4], [14, 8.75, 10], "gal", rot=("z", 45, [8, 8, 8])),
    el("blade_d2", [2, 7.25, 4], [14, 8.75, 10], "gal", rot=("z", -45, [8, 8, 8])),
]
m("forced_draft_fan/impeller", "boilers", _imp, kind="partial")
m("induced_draft_fan/impeller", "boilers", _imp, kind="partial")

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
