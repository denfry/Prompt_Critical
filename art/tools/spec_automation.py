"""03d: релейная автоматика A1, аналоговые блоки A2, станции управления, газоанализатор, воздух КИП."""
from artkit import *

A = lambda n: f"block/common/auto_{n}.png"
OWN = {
    "bk": A("bakelite"), "bks": A("bakelite_smooth"), "cap": A("cap_clear"), "ral": A("ral7035"),
    "rals": A("ral7035_smooth"), "blk": A("panel_black"), "kn": A("knob"), "lam": A("lamels"),
    "wir": A("wiring"), "tube": A("glow_tubes"), "exy": A("ex_yellow"), "mesh": A("sensor_mesh"),
    "cb": A("compressor_blue"), "cbs": A("compressor_blue_smooth"), "fin": A("fins"), "va": A("vessel_air"),
    "trd": B("time_relay_dial"), "msc": B("manual_station_scale"), "aff": B("analog_function_front"),
    "scf": B("signal_converter_front"), "psf": B("relay_power_supply_front"), "rrd": B("relay_rack_door"),
    "cdr": B("cam_controller_drum"),
}


def m(id_, cat, els, kind="block"):
    mk(id_, cat, els, OWN, kind)


def lamp(name, x, y, on, col, z=0.0, s=1.0):
    """Сигнальная лампа на лицевой грани z: в состоянии «горит» — glow_ элемент."""
    return el(("glow_" if on else "") + name, [x - s, y - s, z - 0.6], [x + s, y + s, z], col if on else "loff")


# ======================================================================
# A1: реле на колодке (стена, DIN-рейка, лицо — north)
# ======================================================================
def relay_body(coils=1, picked=False, flag=False):
    els = din_rail() + [
        el("socket", [3, 3, 12.5], [13, 13, 14.5], "bks"),
        el("terminal_t", [4, 13, 12.5], [12, 14, 14.5], "cus"),
        el("terminal_b", [4, 2, 12.5], [12, 3, 14.5], "cus"),
    ]
    if coils == 1:
        els += [el("coil", [5.5, 5, 8.5], [10.5, 11, 12.5], "bk"),
                el("winding", [6, 5.5, 8.2], [10, 10.5, 8.5], "cu")]
    else:
        els += [el("coil_l", [4, 5, 8.5], [7.5, 11, 12.5], "bk"),
                el("coil_r", [8.5, 5, 8.5], [12, 11, 12.5], "bk")]
    els += [el("cap", [3.5, 4, 6.5], [12.5, 12, 12.5], "cap"),
            lamp("lamp_coil", 8, 11, picked, "lo", z=6.5, s=0.75)]
    if coils == 2:
        els.append(el("flag", [7.5, 5, 6] if not flag else [7.5, 9, 6], [8.5, 7, 6.5] if not flag else [8.5, 11, 6.5], "red"))
    return els


# промежуточное реле РП-21 / реле РЭП на колодке
m("relay", "automation", relay_body())
m("relay_picked", "automation", relay_body(picked=True))
# бистабильное реле РП-11: две катушки и флажок положения
m("relay_latching", "automation", relay_body(2))
m("relay_latching_set", "automation", relay_body(2, picked=True, flag=True))


# реле времени ВЛ: бакелитовый корпус, шкала 32×32; стрелку выдержки двигает код
def time_relay(running=False, done=False):
    return din_rail() + [
        el("body", [3, 2, 9], [13, 14, 14.5], "bk"),
        front("face", [3.5, 3.5, 8.9], [12.5, 12.5, 9], "trd", "bk"),
        el("knob", [7, 12.8, 8], [9, 13.8, 9], "kn"),
        lamp("lamp_run", 5, 2.8, running, "lg", z=9, s=0.6),
        lamp("lamp_done", 11, 2.8, done, "lr", z=9, s=0.6),
    ]


m("time_relay", "automation", time_relay())
m("time_relay_running", "automation", time_relay(running=True))
m("time_relay_done", "automation", time_relay(done=True))
m("time_relay/dial", "automation", [
    el("needle", [7.75, 8, 8.6], [8.25, 12, 8.8], "red"),
    el("hub", [7.5, 7.5, 8.5], [8.5, 8.5, 8.9], "d"),
], kind="partial")

# шаговый искатель ШИ-25: поле ламелей в три уровня, катушка храпового привода
m("stepping_switch", "automation", [
    el("base", [1, 0, 1], [15, 1.5, 15], "bks"),
    el("frame_w", [1, 1.5, 3], [2.5, 13, 13], "bs"),
    el("frame_e", [13.5, 1.5, 3], [15, 13, 13], "bs"),
    el("bank_1", [2.5, 3, 11], [13.5, 5, 14], "lam"),
    el("bank_2", [2.5, 6.5, 11], [13.5, 8.5, 14], "lam"),
    el("bank_3", [2.5, 10, 11], [13.5, 12, 14], "lam"),
    el("coil", [3, 1.5, 2], [7, 7, 6], "bk"),
    el("pawl", [7, 4, 3], [11, 5, 4], "st"),
    el("top_bar", [1, 13, 3], [15, 14.5, 13], "bs"),
])
m("stepping_switch/wiper", "automation", [
    el("shaft", [7.5, 1.5, 7.5], [8.5, 13, 8.5], "st"),
    el("wiper_1", [8, 3.5, 7.75], [13, 4.5, 8.25], "cus"),
    el("wiper_2", [8, 7, 7.75], [13, 8, 8.25], "cus"),
    el("wiper_3", [8, 10.5, 7.75], [13, 11.5, 8.25], "cus"),
], kind="partial")

# командоаппарат КЭП-12: чугунная станина, ряд контактов над кулачковым барабаном (вал по X)
m("cam_controller", "automation", [
    el("base", [0, 0, 1], [16, 2, 15], "d"),
    el("cheek_w", [0, 2, 2], [2, 14, 14], "d"),
    el("cheek_e", [14, 2, 2], [16, 14, 14], "d"),
    el("contact_bar", [2, 13, 10], [14, 15, 14], "bks"),
    el("cover", [2, 14, 2], [14, 15, 10], "pnd"),
] + [el(f"contact_{i}", [2.5 + i * 2, 11.5, 10.5], [3.5 + i * 2, 13, 12], "cus") for i in range(6)])
m("cam_controller/drum", "automation",
  disc("drum", "x", 3.5, 2, 14, "cdr")
  + [el(f"cam_{i}", [2.5 + i * 2, 11, 7], [3.5 + i * 2, 12, 9], "bs") for i in range(6)]
  + disc("shaft", "x", 1, 0, 16, "st", 8),
  kind="partial")


# реле давления ДЕМ-102 и сигнализаторы уровня, температуры, протока
def process_switch(kind="pressure", tripped=False):
    els = [
        el("mount", [4, 2, 15], [12, 14, 16], "d"),
        el("case", [4.5, 6, 10], [11.5, 14, 15], "ral"),
        el("cover", [4.5, 13.5, 9.5], [11.5, 14.5, 15], "rals"),
        el("adjust_screw", [7.5, 14.5, 12], [8.5, 15.5, 13], "bs"),
        lamp("lamp_trip", 8, 11, tripped, "lr", z=10, s=0.75),
    ]
    if kind == "pressure":
        els += [el("bellows", [6.5, 3, 11], [9.5, 6, 14], "st"), el("fitting", [7, 0, 11.5], [9, 3, 13.5], "bs")]
    elif kind == "level":
        els += [el("stem", [7.5, 0, 12], [8.5, 6, 13], "st"), el("float", [6, -3, 5], [10, 0, 14], "sm"),
                el("arm", [7.5, -1.5, 12], [8.5, -0.5, 14], "st")]
    elif kind == "temperature":
        els += [el("capillary", [7.5, 2, 12], [8.5, 6, 13], "cus"), el("bulb", [7, 0, 11.5], [9, 2, 13.5], "bs")]
    else:  # flow: лопасть в потоке
        els += [el("fitting", [7, 2, 11.5], [9, 6, 13.5], "bs"), el("paddle", [6, -4, 12.25], [10, 2, 12.75], "st")]
    return els


m("process_switch", "automation", process_switch())
m("process_switch_tripped", "automation", process_switch(tripped=True))
for _k in ("level", "temperature", "flow"):
    m(f"process_switch_{_k}", "automation", process_switch(_k))


# блок питания оперативного тока 24 В на DIN-рейке
def power_supply(powered=False, overload=False):
    return din_rail() + [
        el("body", [2.5, 2, 9], [13.5, 14, 14.5], "ral", faces={"north": "psf"}, full=["north"]),
        lamp("lamp_mains", 5, 12.5, powered, "lg", z=9, s=0.6),
        lamp("lamp_overload", 11, 12.5, overload, "lr", z=9, s=0.6),
    ]


m("relay_power_supply", "automation", power_supply())
m("relay_power_supply_powered", "automation", power_supply(True))
m("relay_power_supply_overload", "automation", power_supply(True, True))

# релейный шкаф ЩАП 1×2×1: корпус без двери, дверь — partial на левой петле
m("relay_rack", "automation", [
    el("plinth", [0, 0, 0], [16, 1.5, 16], "pnd"),
    el("back", [0, 1.5, 15], [16, 31, 16], "ral"),
    el("side_w", [0, 1.5, 1], [1, 31, 15], "ral"),
    el("side_e", [15, 1.5, 1], [16, 31, 15], "ral"),
    el("roof", [0, 31, 0], [16, 32, 16], "ral"),
    el("mount_panel", [1, 2, 13], [15, 30, 15], "wir", full=["north"]),
] + [x for y in (6, 13, 20, 26) for x in (
    el(f"rail_{y}", [1.5, y, 12.5], [14.5, y + 1, 13], "st"),
    el(f"relays_{y}", [2, y + 1, 10], [14, y + 4, 12.5], "bk", faces={"north": "cap"}),
)])
m("relay_rack/door", "automation", [
    el("door", [0, 1.5, 0], [16, 31, 1], "ral", faces={"north": "rrd"}, full=["north"]),
    el("handle", [13.5, 14, -0.75], [14.5, 18, 0], "d"),
    el("hinge_t", [-0.25, 26, 0], [0.5, 28, 1], "st"),
    el("hinge_b", [-0.25, 4, 0], [0.5, 6, 1], "st"),
], kind="partial")


# ======================================================================
# A2: аналоговые блоки МН-7 / ЭР-III (лицо north, ламповое и транзисторное исполнение)
# ======================================================================
def analog_block(knobs, tube=True, gauge=True, switch=False, lamp_on=None, face=None):
    els = [
        el("chassis", [0, 0, 1], [16, 16, 16], "pnd"),
        el("face", [0.5, 0.5, 0.5], [15.5, 15.5, 1], "blk") if not face
        else front("face", [0.5, 0.5, 0.5], [15.5, 15.5, 1], face, "blk"),
        el("handle_w", [1, 6, -0.5], [2, 10, 0.5], "st"),
        el("handle_e", [14, 6, -0.5], [15, 10, 0.5], "st"),
    ]
    if gauge:
        els.append(front("gauge", [4, 8.5, 0.4], [12, 14.5, 0.5], "gauge", "blk"))
    for i, (x, y) in enumerate(knobs):
        els.append(knob(f"knob_{i}", x, y, 0.5, 0.8, "kn"))
    if switch:
        els += [el("switch_base", [11.5, 2, 0], [14.5, 4, 0.5], "st"),
                el("switch_lever", [12.75, 3, -1.5], [13.25, 3.5, 0], "st")]
    if tube:
        els.append(el("glow_tubes", [1, 15.5, 3], [15, 16.25, 14], "tube"))
    else:
        els.append(el("vent", [2, 15.5, 3], [14, 16.1, 14], "louv"))
    if lamp_on is not None:
        els.append(lamp("lamp_trip", 13, 13.5, lamp_on, "lr", z=0.5, s=0.75))
    return els


_k4 = [(3, 4), (6, 4), (10, 4), (13, 4)]
m("analog_summer", "automation", analog_block(_k4, tube=False))
for _id, kw in (
    ("analog_integrator", dict(knobs=[(3, 4), (6.5, 4)], switch=True)),
    ("analog_differentiator", dict(knobs=[(4, 4), (8, 4)])),
    ("analog_multiplier", dict(knobs=[(4, 4)], switch=True)),
    ("analog_function", dict(knobs=[], gauge=False, face="aff")),
):
    m(_id, "automation", analog_block(tube=True, **kw))
    m(f"{_id}_transistor", "automation", analog_block(tube=False, **kw))
m("analog_comparator", "automation", analog_block([(5, 5), (11, 5)], tube=False, gauge=False, lamp_on=False))
m("analog_comparator_tripped", "automation", analog_block([(5, 5), (11, 5)], tube=False, gauge=False, lamp_on=True))


# станция управления / задатчик ручной: два профильных прибора, задатчик, кнопки «А»/«Р»
def manual_station(manual=False):
    return [
        el("panel", [0, 0, 13], [16, 16, 16], "pn"),
        el("case", [2, 1, 6], [14, 15, 13], "ral"),
        front("scale_pv", [2.5, 6, 5.9], [7.5, 14.5, 6], "msc", "blk"),
        front("scale_out", [8.5, 6, 5.9], [13.5, 14.5, 6], "msc", "blk"),
        el("setter", [6.5, 2, 4.5], [9.5, 5, 6], "kn"),
        lamp("button_auto", 4, 3.5, not manual, "lw", z=6, s=1.0),
        lamp("button_manual", 12, 3.5, manual, "ly", z=6, s=1.0),
    ]


m("manual_station", "automation", manual_station())
m("manual_station_manual", "automation", manual_station(True))

# нормирующий преобразователь 4–20 мА: узкий модуль на DIN-рейке
m("signal_converter", "automation", din_rail() + [
    el("body", [5.5, 2, 9], [10.5, 14, 14.5], "ral", faces={"north": "scf"}, full=["north"]),
    el("terminals_t", [5.5, 14, 10], [10.5, 15, 14], "cus"),
    el("terminals_b", [5.5, 1, 10], [10.5, 2, 14], "cus"),
])


# стационарный газоанализатор во взрывозащищённом исполнении (корпус 6×10×6 px на стене)
def gas_detector(alarm="none"):
    col = {"none": "lg", "warn": "ly", "alarm": "lr"}[alarm]
    return [
        el("bracket", [4, 3, 15], [12, 15, 16], "d"),
        el("body", [5, 4, 9], [11, 14, 15], "exy"),
        el("cover", [4.5, 11, 8.5], [11.5, 14.5, 15], "exy"),
        el("gland", [10.5, 6, 11], [12.5, 8, 13], "bs"),
        el("sensor", [6, 2, 10], [10, 4, 14], "st", faces={"down": "mesh"}),
        el("glow_status", [7, 12, 8], [9, 13.5, 8.5], col),
    ]


m("gas_detector", "instruments", gas_detector())
m("gas_detector_warn", "instruments", gas_detector("warn"))
m("gas_detector_alarm", "instruments", gas_detector("alarm"))


# ======================================================================
# воздух КИП
# ======================================================================
# поршневой компрессор 2×1×1 (по X): ребристый цилиндр, картер, маховик — partial на валу
m("instrument_air_compressor", "automation", [
    el("skid", [0, 0, 1], [32, 2, 15], "d"),
    el("crankcase", [2, 2, 3], [14, 9, 13], "cb"),
    el("cylinder", [4, 9, 4.5], [11, 15, 11.5], "fin", faces={"up": "cbs"}),
    el("head", [3.5, 15, 4], [11.5, 17, 12], "cbs"),
    el("discharge", [11.5, 15.5, 7], [20, 16.5, 9], "air"),
    el("aftercooler", [18, 10, 4], [30, 16, 12], "fin", faces={"up": "cb", "down": "cb"}),
    el("filter", [24, 2, 5], [28, 10, 9], "cbs"),
    el("bearing", [14, 5, 6], [16, 11, 10], "d"),
    el("guard", [15.5, 2, 1.5], [17, 14, 14.5], "h"),
])
m("instrument_air_compressor/flywheel", "automation",
  disc("rim", "x", 5.5, 0.5, 2.5, "ds") + disc("web", "x", 4.5, 0.75, 2.25, "red")
  + disc("hub", "x", 1.5, -0.5, 4, "st", 8),
  kind="partial")

# воздухосборник 1×2×1: вертикальный сосуд, манометр (подложка, стрелку рисует BER)
m("air_receiver", "automation",
  [el("base_ring", [2, 0, 2], [14, 2, 14], "d")]
  + disc("shell", "y", 6.5, 2, 28, "va")
  + disc("head", "y", 4.5, 28, 30, "va", 8)
  + [el("safety_valve", [7, 30, 7], [9, 32, 9], "bs"),
     el("inlet", [13, 20, 7], [16.5, 22, 9], "air", faces={"east": "end"}),
     el("outlet", [-0.5, 6, 7], [3, 8, 9], "air", faces={"west": "end"}),
     el("drain", [7.5, 1.5, 1], [8.5, 3, 3], "bs"),
     el("gauge_stem", [7.5, 22, 1], [8.5, 23, 2], "bs"),
     el("gauge_case", [5.5, 21, 0], [10.5, 26, 1], "d", faces={"north": "dial"}, full=["north"])])


# адсорбционный осушитель 1×2×1: две колонны, переключающие клапаны, индикатор точки росы
def air_dryer(wet=False):
    els = [el("skid", [0, 0, 0], [16, 1.5, 16], "d")]
    for i, x in enumerate((4.5, 11.5)):
        els += disc(f"column_{i}", "y", 3, 1.5, 28, "va", 8, c=(x, 8, 9))
        els.append(el(f"cap_{i}", [x - 2, 28, 7], [x + 2, 29.5, 11], "d"))
    els += [el("manifold_top", [3, 29.5, 8], [13, 31, 10], "air"),
            el("manifold_bot", [3, 3, 3], [13, 5, 5], "air"),
            el("valve_a", [3.5, 5, 2.5], [5.5, 7.5, 5.5], "bs"),
            el("valve_b", [10.5, 5, 2.5], [12.5, 7.5, 5.5], "bs"),
            el("controller", [6, 12, 1], [10, 18, 4], "ral"),
            el("glow_dewpoint", [7.25, 15.5, 0.5], [8.75, 17, 1], "lr" if wet else "lg")]
    return els


m("air_dryer", "automation", air_dryer())
m("air_dryer_wet", "automation", air_dryer(True))
