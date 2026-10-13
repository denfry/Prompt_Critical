"""03c/03d после M3: картограмма, ПЛК и модули, ПАЗ, ESD, полевая шина, телемеханика,
поточные анализаторы, инженерная станция, серверная, шкафное оборудование.
Модули ПЛК — на DIN-рейке у задней стены (south), лицо — north."""
from artkit2 import *

A2 = lambda n: f"block/common/auto2_{n}.png"
OWN = {k: A2(v) for k, v in {
    "di": "io_di", "do": "io_do", "ai": "io_ai", "ao": "io_ao", "cpu": "cpu", "comm": "comm",
    "sisd": "sis_door", "rtud": "rtu_door", "chr": "chroma", "srv": "server", "ups": "ups",
    "pb": "plastic_blue", "vio": "violet", "cg": "cartogram_glass", "da": "diode_a", "db": "diode_b"}.items()}


def m(id_, cat, els, kind="block"):
    mk2(id_, cat, els, OWN, kind)


# ======================================================================
# 03c: картограмма СВРК — панель 1×1, стыкуется в табло до 4×4
# ======================================================================
def cartogram(part="single", powered=False):
    edge = {"single": "nsew", "edge": "n", "corner": "nw", "center": ""}[part]
    els = [el("glass", [0, 0, 13], [16, 16, 14], "cg", faces={"north": "cg"}, full=["north"]),
           el("back", [0.5, 0.5, 14], [15.5, 15.5, 16], "rals")]
    if "n" in edge:
        els.append(el("frame_top", [0, 15, 12], [16, 16, 14], "ral"))
    if "s" in edge or part == "single":
        els.append(el("frame_bottom", [0, 0, 12], [16, 1.5, 14], "ral"))
        els.append(el(("glow_" if powered else "") + "legend", [2, 0.4, 11.8], [12, 1.1, 12], "lw" if powered else "loff"))
        els.append(lamp("lamp_power", 14, 0.75, 12, powered, "lg", 0.5))
    if "w" in edge:
        els.append(el("frame_w", [0, 0, 12], [1, 16, 14], "ral"))
    if "e" in edge:
        els.append(el("frame_e", [15, 0, 12], [16, 16, 14], "ral"))
    return els


for _p in ("single", "edge", "corner", "center"):
    m(f"core_cartogram{'' if _p == 'single' else '_' + _p}", "control", cartogram(_p))
m("core_cartogram_powered", "control", cartogram("single", True))


# ======================================================================
# ПЛК: ЦП с ключом режима, модули ввода-вывода, коммуникационный модуль
# ======================================================================
def rack_module(face, w=(3, 13), leds=()):
    els = din_rail() + [box_("module", [w[0], 2, 9], [w[1], 14, 14.5], "pb", face)]
    els += [lamp(n, x, y, 9, on, c, 0.45) for (n, x, y, on, c) in leds]
    return els


def plc(mode="off"):
    on = {"run": "lg", "stop": "ly", "fault": "lr", "forced": "lo"}
    els = din_rail() + [
        box_("psu", [1, 2, 9], [5, 14, 14.5], "pb", faces={"north": "rals"}),
        box_("cpu", [5, 2, 9], [15, 14, 14.5], "pb", "cpu"),
        el("card_slot", [10, 9.5, 8.8], [14, 10.5, 9], "d"),
    ]
    for i, (k, y) in enumerate((("run", 12), ("stop", 10.5), ("fault", 9), ("forced", 7.5))):
        els.append(lamp(f"led_{k}", 6.5, y, 9, mode == k, on[k], 0.45))
    els.append(lamp("led_psu", 3, 12, 9, mode != "off", "lg", 0.45))
    return els


for _s in ("off", "run", "stop", "fault", "forced"):
    m("plc_controller" + ("" if _s == "off" else "_" + _s), "automation", plc(_s))
m("plc_controller/key", "automation", [
    el("barrel", [11, 5, 8.4], [13, 7, 9], "st"),
    el("key", [11.75, 4, 7.4], [12.25, 8, 8.4], "b"),
], kind="partial")

for _id, _f in (("plc_io_di", "di"), ("plc_io_do", "do"), ("plc_io_ai", "ai"), ("plc_io_ao", "ao")):
    m(_id, "automation", rack_module(_f, leds=(("led_bus", 4, 13, True, "lg"),)))
    m(_id + "_fault", "automation", rack_module(_f, leds=(("led_bus", 4, 13, True, "lr"),)))


def comm(fault=False, link=True, adapter=False):
    els = rack_module("comm", leds=(("led_bus", 4, 13, True, "lr" if fault else "lg"),
                                    ("led_link", 6, 13, link, "lg")))
    els += [el("rs485_plug", [4, 6.5, 7.5], [8, 8.5, 9], "vio"),
            el("eth_plug", [9.5, 6, 7.5], [12.5, 9, 9], "dg")]
    if adapter:
        els += [el(f"dip_{i}", [4 + i * 1.5, 10.5, 8.5], [5 + i * 1.5, 11.5, 9], "w") for i in range(4)]
    return els


m("plc_comm", "automation", comm())
m("plc_comm_fault", "automation", comm(fault=True))
m("plc_comm_link_down", "automation", comm(link=False))
m("remote_io_adapter", "automation", comm(adapter=True))
m("remote_io_adapter_fault", "automation", comm(fault=True, adapter=True))


# ======================================================================
# ПАЗ: шкаф логического контроллера безопасности 1×2×1 (TMR), дверь — partial
# ======================================================================
def sis(state="normal"):
    lam = {"normal": "lg", "tripped": "lr", "bypassed": "ly", "fault": "lo"}[state]
    return [el("plinth", [0, 0, 1], [16, 1, 16], "h"),
            el("body", [0, 1, 1], [16, 32, 16], "ral"),
            el("roof", [-0.25, 31, 0.75], [16.25, 32, 16.25], "rals"),
            box_("display_frame", [3, 25, 0.5], [13, 29, 1], "rals", faces={"north": "pblk"}),
            el("glow_status" if state != "normal" else "status", [4, 26, 0.3], [12, 28, 0.5], lam if state != "normal" else "lg")]


for _s in ("normal", "tripped", "bypassed", "fault"):
    m("sis_logic_solver" + ("" if _s == "normal" else "_" + _s), "automation", sis(_s))
m("sis_logic_solver/door", "automation", [
    el("door", [1, 2, 0], [15, 24, 1], "ral", faces={"north": "sisd"}, full=["north"]),
    el("handle", [13, 11, -0.6], [13.75, 15, 0], "d"),
], kind="partial")


# ======================================================================
# ESD-кран: шаровой кран на трубе (ось X) с жёлтым пневмоприводом
# ======================================================================
def esd(pos="open"):
    els = hcyl("pipe", "x", 3, 0, 16, "gas") + hcyl("body", "x", 4.5, 4, 12, "st", sides=8)
    els += hcyl("flange_w", "x", 5, 3.5, 4.5, "d", sides=8) + hcyl("flange_e", "x", 5, 11.5, 12.5, "d", sides=8)
    els += [el("bonnet", [6.5, 12.5, 6.5], [9.5, 15, 9.5], "st"),
            el("actuator", [3, 15, 4.5], [13, 21, 11.5], "ye"),
            el("solenoid", [13, 16, 6.5], [15, 19, 9.5], "d"),
            el("limit_box", [5.5, 21, 6], [10.5, 23, 10], "dg"),
            el(("glow_" if pos != "mid" else "") + "indicator", [7, 23, 7], [9, 24, 9],
               {"open": "lg", "closed": "lr", "mid": "ly"}[pos])]
    return els


for _s in ("open", "closed", "mid"):
    m("esd_valve" + ("" if _s == "open" else "_" + _s), "piping", esd(_s))
m("esd_valve/stem", "piping", [
    el("stem", [7.25, 12, 7.25], [8.75, 16, 8.75], "st"),
    el("flag", [5, 15, 7.6], [11, 15.5, 8.4], "ye"),
], kind="partial")


# ======================================================================
# полевая шина RS-485, повторитель, шлюз, диод данных
# ======================================================================
m("fieldbus_cable", "automation", [el("core", [6.5, 6.5, 6.5], [9.5, 9.5, 9.5], "vio")])
m("fieldbus_cable_side", "automation", [el("arm", [7, 7, 0], [9, 9, 6.5], "vio")])


def small_module(name_els, link=True, w=(5, 11), face="rals"):
    els = din_rail() + [box_("module", [w[0], 3, 10], [w[1], 13, 14.5], "rals", face)]
    els += name_els
    els.append(lamp("led_link", w[0] + 1.5, 11.5, 10, link, "lg", 0.45))
    return els


def repeater(link):
    return small_module([el("port_a", [5.5, 4, 9], [7.5, 6, 10], "vio"), el("port_b", [8.5, 4, 9], [10.5, 6, 10], "vio")], link)


def gateway(link):
    return small_module([el("port_bus", [4.5, 4, 9], [7, 6.5, 10], "vio"), el("port_eth", [8.5, 4, 9], [11.5, 7, 10], "dg")],
                        link, w=(3.5, 12.5))


def diode(link):
    return din_rail() + [
        el("half_a", [2, 3, 10], [8, 13, 14.5], "da"),
        el("half_b", [8, 3, 10], [14, 13, 14.5], "db"),
        el("arrow_shaft", [4, 7.75, 9.8], [11, 8.25, 10], "w"),
        el("arrow_head", [10, 7, 9.8], [11.5, 9, 10], "w"),
        lamp("led_flow", 12, 11.5, 10, link, "lg", 0.45),
    ]


for _ok, _sfx in ((True, ""), (False, "_link_down")):
    m("fieldbus_repeater" + _sfx, "automation", repeater(_ok))
    m("fieldbus_gateway" + _sfx, "automation", gateway(_ok))
    m("data_diode" + _sfx, "automation", diode(_ok))


# ======================================================================
# телемеханика: уличный шкаф КП, мачта с антенной, фронт-процессор ПУ
# ======================================================================
def rtu(power="grid"):
    lam = {"grid": ("lg", True), "battery": ("ly", True), "off": ("lg", False)}[power]
    return [el("plinth", [0, 0, 2], [16, 2, 16], "c"),
            el("body", [1, 2, 3], [15, 30, 15], "r32"),
            el("canopy", [0, 30, 1], [16, 31.5, 16], "r32"),
            el("plate", [5, 26, 2.8], [11, 28, 3], "w"),
            lamp("lamp_power", 3, 27, 3, lam[1], lam[0])]


for _s in ("grid", "battery", "off"):
    m("rtu_station" + ("" if _s == "grid" else "_" + _s), "automation", rtu(_s))
m("rtu_station/door", "automation", [
    el("door", [1.5, 3, 2], [14.5, 25, 3], "r32", faces={"north": "rtud"}, full=["north"]),
    el("handle", [12.5, 12, 1.4], [13.25, 16, 2], "d"),
], kind="partial")


def mast_section(base=False):
    els = [el(f"leg_{i}", [x, 0, z], [x + 1, 16, z + 1], "gal")
           for i, (x, z) in enumerate(((5, 5), (10, 5), (7.5, 10)))]
    els += [el(f"brace_{i}", [5, y, 5], [11, y + 0.5, 6], "gal") for i, y in enumerate((4, 12))]
    if base:
        els.insert(0, el("foundation", [3, 0, 3], [13, 2, 13], "c"))
    return els


m("radio_antenna", "automation", mast_section(True))
m("radio_antenna_section", "automation", mast_section())
m("radio_antenna_top", "automation", mast_section()[:3] + [el("mast_cap", [5, 15, 5], [11, 16, 11], "gal"),
                                                          el("whip", [7.75, 16, 7.75], [8.25, 32, 8.25], "alu")])
m("radio_antenna/yagi", "automation", [
    el("boom", [7.5, 10, 0], [8.5, 11, 16], "alu"),
    el("clamp", [7, 9, 7], [9, 12, 9], "d"),
] + [el(f"element_{i}", [3 + i * 0.5, 10.25, 2 + i * 4], [13 - i * 0.5, 10.75, 2.5 + i * 4], "alu") for i in range(4)],
  kind="partial")


def tele_master(link):
    return [el("desk", [0, 0, 2], [16, 6, 15], "pn"),
            el("rack", [2, 6, 6], [14, 15, 15], "ral"),
            el("modem", [3, 9, 5.5], [13, 12, 6], "pblk"),
            lamp("led_rx", 4, 13.5, 6, link, "lg", 0.5),
            lamp("led_tx", 6, 13.5, 6, link, "ly", 0.5),
            el("antenna_lead", [12, 15, 12], [13, 18, 13], "cbl")]


m("telemetry_master", "automation", tele_master(True))
m("telemetry_master_link_down", "automation", tele_master(False))


# ======================================================================
# поточные анализаторы (03d M8), инженерная станция, сервер, кондиционер, ИБП
# ======================================================================
def analyzer_shelter(front_tex, state="measuring", extra=()):
    lam = {"measuring": ("lg", True), "calibrating": ("ly", True), "fault": ("lr", True)}[state]
    return [el("plinth", [0, 0, 1], [16, 1, 16], "d"),
            el("body", [0.5, 1, 1], [15.5, 30, 16], "ral"),
            el("roof", [0, 30, 0], [16, 31.5, 16], "rals"),
            box_("panel", [3, 14, 0.5], [13, 24, 1], "ral", front_tex),
            lamp("lamp_cycle", 13.5, 27, 1, lam[1], lam[0])] + list(extra)


_cyl = vcyl("carrier_bottle", 17.5, 8, 1.5, 1, 14, "lab", 8) + [el("bottle_valve", [17, 14, 7.5], [18, 15, 8.5], "b")]
for _s in ("measuring", "calibrating", "fault"):
    m("process_chromatograph" + ("" if _s == "measuring" else "_" + _s), "instruments", analyzer_shelter("chr", _s, _cyl))


def quality(kind="octane_nir", state="measuring"):
    extra = [el("window", [3, 3, 0.4], [13, 12, 0.6], "gls")]
    if kind == "octane_engine":
        extra += [el("engine_block", [4, 2, 2], [12, 9, 10], "red"), el("head", [5, 9, 3], [11, 11, 9], "d")]
    return analyzer_shelter("chr", state, extra)


m("quality_analyzer", "instruments", quality())
m("quality_analyzer_fault", "instruments", quality(state="fault"))
m("quality_analyzer_octane_engine", "instruments", quality("octane_engine"))
m("quality_analyzer/flywheel", "instruments",
  hcyl("flywheel", "x", 3, 12, 13.5, "d", c=(8, 6, 6)) + hcyl("crank", "x", 0.75, 10, 14, "st", c=(8, 6, 6), sides=8), kind="partial")

# инженерная станция 2×1×1: стол оператора, два монитора, клавиатура (якорь — левый блок)
m("simulation_workstation", "automation", [
    el("desk_top", [0, 10, 2], [32, 11, 14], "pns"),
    el("leg_w", [0.5, 0, 3], [1.5, 10, 13], "pnd"),
    el("leg_e", [30.5, 0, 3], [31.5, 10, 13], "pnd"),
    el("modesty", [1.5, 2, 12], [30.5, 10, 13], "pn"),
    el("monitor_l", [3, 12.5, 9], [14, 20, 10], "pblk"),
    el("monitor_r", [18, 12.5, 9], [29, 20, 10], "pblk"),
    el("stand_l", [8, 11, 9.5], [9, 12.5, 11], "d"),
    el("stand_r", [23, 11, 9.5], [24, 12.5, 11], "d"),
    el("keyboard", [10, 11, 3], [22, 11.5, 6], "dg"),
])


def historian(busy):
    return [el("rack", [0.5, 0, 1], [15.5, 31.25, 16], "blk"),
            box_("bays", [1.5, 2, 0.5], [14.5, 28, 1], "blk", "srv"),
            el("top_vent", [2, 31.25, 3], [14, 32, 14], "vent")] + [
        lamp(f"led_{i}", 3 + (i % 4) * 3, 4 + (i // 4) * 6, 0.5, busy and i % 3 != 1, "lg", 0.4) for i in range(16)]


m("historian_server", "automation", historian(False))
m("historian_server_busy", "automation", historian(True))


def cooler(mode="off"):
    return [el("body", [2, 1, 10], [14, 15, 16], "w"),
            el("grille", [4, 3, 9.5], [12, 11, 10], "vent"),
            lamp("led_mode", 12, 13, 10, mode != "off", "lb" if mode == "ac" else "lg", 0.5)]


for _s in ("off", "fan", "ac"):
    m("cabinet_cooler" + ("" if _s == "off" else "_" + _s), "automation", cooler(_s))
m("cabinet_cooler/fan", "automation", fan_rotor("z", 3.5, 9.6, "d", c=(8, 7, 9.6)), kind="partial")


def ups(state="online", charge=4):
    lam = {"online": "lg", "battery": "ly", "fault": "lr"}[state]
    els = [el("plinth", [0, 0, 1], [16, 1, 16], "d"),
           el("body", [0.5, 1, 1], [15.5, 31, 16], "r32"),
           box_("panel", [3, 20, 0.5], [13, 28, 1], "r32", "ups"),
           lamp("lamp_state", 12, 29.5, 1, True, lam)]
    els += [lamp(f"charge_{i}", 4 + i * 2, 18.5, 1, i < charge, "lg", 0.5) for i in range(5)]
    return els


m("ups_cabinet", "automation", ups())
m("ups_cabinet_battery", "automation", ups("battery", 2))
m("ups_cabinet_fault", "automation", ups("fault", 0))
