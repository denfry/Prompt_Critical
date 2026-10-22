"""13: блокировки и допуск (LOTO), заглушки, расцепная муфта, люк-лаз, спасательная тренога, вентилятор,
стенды; факельное хозяйство; пожарная сигнализация и тушение; знаки; связь и оповещение.
Настенные изделия — у стены south, лицо — north."""
from artkit2 import *

Q = lambda n: f"block/common/ops_{n}.png"
OWN = {"lbf": B("lockout_box_front"), "cork": Q("cork"), "pbf": B("permit_board_face"), "rw": Q("red_white"),
       "fap": B("fire_alarm_panel_front"), "mcp": B("manual_call_point_face"),
       **{f"sg_{k}": B(f"hazard_sign_{k}") for k in ("warn", "forbid", "mandatory", "ghs")},
       "nfpa": B("hazard_diamond"), "un": B("un_placard"), "icm": B("intercom_station_front"),
       "and": M_("andesite"), "cas": T("brass")}


def m(id_, cat, els, kind="block"):
    mk2(id_, cat, els, OWN, kind)


# ======================================================================
# LOTO и оборудование газоопасных работ
# ======================================================================
def lockout_box(locks=0):
    els = [el("box", [3, 2, 12], [13, 14, 16], "rd", faces={"north": "lbf"}, full=["north"])]
    els += [el(f"lock_{i}", [3.5 + i * 1.6, 1, 11.5], [4.5 + i * 1.6, 3, 12.5], "lr") for i in range(locks)]
    return els


m("lockout_box", "ops", lockout_box())
for _n in (1, 3, 6):
    m(f"lockout_box_locks{_n}", "ops", lockout_box(_n))
m("lockout_box/lid", "ops", [el("lid", [3, 13, 11.5], [13, 14, 16], "rd")], kind="partial")


def line_blind(blind=False):
    els = hcyl("pipe_w", "x", 3, 0, 6.5, "st") + hcyl("pipe_e", "x", 3, 9.5, 16, "st")
    els += hcyl("flange_w", "x", 5, 5.5, 7, "d", sides=8) + hcyl("flange_e", "x", 5, 9, 10.5, "d", sides=8)
    els += [el(f"stud_{i}", [5, y, z], [11, y + 1, z + 1], "st") for i, (y, z) in enumerate(((3, 3), (12, 3), (3, 12), (12, 12)))]
    return els


m("line_blind", "piping", line_blind())
m("line_blind/plate", "piping", hcyl("open_ring", "x", 4, 7, 9, "st", c=(8, 8, 8), sides=8)
  + [el("web", [7.25, 12, 7], [8.75, 16, 9], "st")] + hcyl("blind_disc", "x", 4, 7, 9, "rd", c=(8, 20, 8), sides=8)
  + [el("blind_mark", [7, 19, 3.5], [9, 21, 4], "lr")], kind="partial")


def disconnect(engaged=True, locked=False):
    els = [el("housing", [3, 3, 4], [13, 13, 12], "and"), el("bracket", [2, 0, 4], [14, 3, 12], "and")]
    els += [el("lever_pivot", [12.5, 9, 7], [14, 11, 9], "cas"),
            el("lever", [13, 10, 7.5], [14, 16, 8.5], "cas", rot=("z", -45 if engaged else 22.5, [13.5, 10, 8]))]
    if locked:
        els.append(el("padlock", [13.5, 13, 6.5], [15.5, 15.5, 9.5], "lr"))
    return els


m("mechanical_disconnect", "ops", disconnect())
m("mechanical_disconnect_disengaged", "ops", disconnect(False))
m("mechanical_disconnect_locked", "ops", disconnect(False, True))
m("mechanical_disconnect/shaft_in", "ops", shaft("z", 1, -0.01, 4), kind="partial")
m("mechanical_disconnect/shaft_out", "ops", shaft("z", 1, 12, 16.01), kind="partial")

m("manway", "ops", hcyl("neck", "z", 6, 13, 16, "st") + hcyl("flange", "z", 7, 12, 13, "d", sides=16)
  + [el("davit", [14, 2, 10], [15, 14, 11], "st"), el("davit_arm", [8, 13, 10], [15, 14, 11], "st")])
m("manway/cover", "ops", hcyl("cover", "z", 7, 10.5, 12, "st", sides=16)
  + [el(f"bolt_{i}", [x, y, 10], [x + 1, y + 1, 10.5], "d") for i, (x, y) in
     enumerate(((7.5, 1.5), (7.5, 13.5), (1.5, 7.5), (13.5, 7.5), (3, 3), (12, 3), (3, 12), (12, 12)))]
  + [el("handle", [6, 7, 9.5], [10, 9, 10.5], "d")], kind="partial")

m("rescue_tripod", "ops", [
    el("leg_1", [7.25, 0, 7.25], [8.75, 30, 8.75], "alu", rot=("x", 22.5, [8, 30, 8])),
    el("leg_2", [7.25, 0, 7.25], [8.75, 30, 8.75], "alu", rot=("x", -22.5, [8, 30, 8])),
    el("leg_3", [7.25, 0, 7.25], [8.75, 30, 8.75], "alu", rot=("z", -22.5, [8, 30, 8])),
    el("head", [5, 29, 5], [11, 32, 11], "st"),
    el("winch_frame", [4, 14, 9.5], [12, 20, 11], "ye"),
])
m("rescue_tripod/winch_drum", "ops", drum("x", 1.8, 5, 11, "cbl", c=(8, 17, 12.5)), kind="partial")


def ventilator(on):
    return hcyl("shroud", "z", 6, 1, 13, "ye", c=(8, 8, 8)) + [
        el("frame_w", [1.5, 0, 3], [3, 2, 12], "d"), el("frame_e", [13, 0, 3], [14.5, 2, 12], "d"),
        el("duct_sock", [4, 4, 13], [12, 12, 16], "or"),
        lamp("lamp_on", 12, 13, 1, on, "lg", 0.5)]


m("portable_ventilator", "ops", ventilator(False))
m("portable_ventilator_on", "ops", ventilator(True))
m("portable_ventilator/fan", "ops", fan_rotor("z", 5, 4, "d"), kind="partial")

m("permit_board", "ops", [el("frame", [0, 2, 15], [32, 14, 16], "wood"),
                          el("board", [1, 3, 14.8], [31, 13, 15], "cork", faces={"north": "pbf"}, full=["north"])])
m("patrol_checkpoint", "ops", [el("tag", [6, 6, 15], [10, 10, 16], "ye")])


def valve_bench(occupied):
    els = [el("top", [0, 9, 1], [32, 10, 15], "st")] + legs(0, 32, 1, 15, 9, 1.5)
    els += [el("clamp", [4, 10, 5], [12, 12, 11], "dg"), el("gauge_post", [24, 10, 12], [25, 18, 13], "st"),
            el("gauge", [22, 16, 11.9], [27, 21, 12], "gauge", full=["north"])]
    if occupied:
        els += [el("psv_body", [5, 12, 6], [11, 17, 10], "rd"), el("psv_bonnet", [6.5, 17, 7], [9.5, 22, 9], "rd")]
    return els


m("valve_test_bench", "ops", valve_bench(False))
m("valve_test_bench_occupied", "ops", valve_bench(True))

# ======================================================================
# факельное хозяйство
# ======================================================================
m("flare_stack", "flare", [el("base_plate", [2, 0, 2], [14, 2, 14], "c")] + vcyl("stack", 8, 8, 3, 2, 16, "rw", 8)
  + [el(f"guy_lug_{i}", [x, 12, z], [x + 1, 14, z + 1], "d") for i, (x, z) in enumerate(((4.5, 7.5), (10.5, 7.5)))])
m("flare_stack_middle", "flare", vcyl("stack", 8, 8, 3, 0, 16, "rw", 8))
m("flare_stack_guys", "flare", vcyl("stack", 8, 8, 3, 0, 16, "rw", 8)
  + [el("guy_ring", [3.5, 13, 3.5], [12.5, 14, 12.5], "d"),
     el("guy_n", [7.75, 13, 0], [8.25, 13.5, 4], "cbl"), el("guy_e", [12, 13, 7.75], [16, 13.5, 8.25], "cbl")])


def flare_tip(lit=False):
    els = vcyl("tip", 8, 8, 3.5, 0, 12, "rust", 8) + vcyl("steam_ring", 8, 8, 4.5, 10, 11.5, "steam", 8)
    for i, (x, z) in enumerate(((3, 8), (13, 5), (13, 11))):
        els += [el(f"pilot_{i}", [x - 0.5, 4, z - 0.5], [x + 0.5, 13, z + 0.5], "st"),
                el(("glow_" if lit else "") + f"pilot_flame_{i}", [x - 0.6, 13, z - 0.6], [x + 0.6, 14.5, z + 0.6],
                   "gfl" if lit else "soot")]
    return els


m("flare_tip", "flare", flare_tip())
m("flare_tip_lit", "flare", flare_tip(True))


def ko_drum(level=0):
    els = hvessel("drum", 6, -16, 32, "r32", cy=8)
    els.append(el("level_gauge", [-6, 3, 1.4], [-5, 13, 2], "gls"))
    if level:
        els.append(el("level", [-6.1, 3, 1.3], [-4.9, 3 + level * 2.5, 1.4], "fuel"))
    return els


m("flare_knockout_drum", "flare", ko_drum())
m("flare_knockout_drum_level2", "flare", ko_drum(2))
m("flare_knockout_drum_level4", "flare", ko_drum(4))
m("flare_seal_drum", "flare", vvessel("drum", 6, 0, 30, "r32") + [el("sight_glass", [7, 6, 1.4], [9, 22, 2], "gls")]
  + nozzle("inlet", "x", 0, (8, 20, 8), 1.5, "gasr") + nozzle("outlet", "x", 16, (8, 24, 8), 1.5, "gasr"))


def ignition(pilots=0):
    return cabinet("r32", z0=4) + [el("button", [3, 6, 3.4], [6, 9, 4], "lr")] + [
        lamp(f"lamp_pilot_{i}", 9 + i * 2, 12, 4, i < pilots, "lo", 0.6) for i in range(3)]


for _n in range(4):
    m("flare_ignition_panel" + ("" if _n == 0 else f"_pilots{_n}"), "flare", ignition(_n))

# ======================================================================
# пожарная сигнализация и тушение
# ======================================================================
def smoke(alarm):
    return [el("base", [5, 15, 5], [11, 16, 11], "w"), el("head", [5.5, 14, 5.5], [10.5, 15, 10.5], "w"),
            el(("glow_" if alarm else "") + "led", [7.5, 13.6, 7.5], [8.5, 14, 8.5], "lr" if alarm else "loff")]


m("smoke_detector", "fire", smoke(False))
m("smoke_detector_alarm", "fire", smoke(True))


def heat(alarm):
    return [el("base", [5, 15, 5], [11, 16, 11], "w"), el("element", [7, 13, 7], [9, 15, 9], "b"),
            el(("glow_" if alarm else "") + "led", [5.5, 14.6, 7.5], [6.5, 15, 8.5], "lr" if alarm else "loff")]


m("heat_detector", "fire", heat(False))
m("heat_detector_alarm", "fire", heat(True))


def flame_det(alarm):
    return [el("bracket", [6, 6, 15], [10, 10, 16], "d"), el("body", [4, 5, 9], [12, 11, 15], "dg"),
            el("window", [5.5, 6.5, 8.6], [10.5, 9.5, 9], "gls"),
            lamp("led", 11, 10, 9, alarm, "lr", 0.4)]


m("flame_detector", "fire", flame_det(False))
m("flame_detector_alarm", "fire", flame_det(True))


def call_point(pressed):
    return [el("box", [5, 5, 14], [11, 11, 16], "rd", faces={"north": "mcp"}, full=["north"]),
            el("glass", [6, 6, 13.8 + (0.2 if pressed else 0)], [10, 10, 14], "gls")]


m("manual_call_point", "fire", call_point(False))
m("manual_call_point_pressed", "fire", call_point(True))


def alarm_panel(state="normal"):
    return [el("box", [2, 2, 12], [14, 14, 16], "rd", faces={"north": "fap"}, full=["north"]),
            lamp("lamp_fire", 4, 13, 12, state == "fire", "lr", 0.5),
            lamp("lamp_fault", 6, 13, 12, state == "fault", "ly", 0.5),
            lamp("lamp_power", 12, 13, 12, True, "lg", 0.5)]


for _s in ("normal", "fire", "fault"):
    m("fire_alarm_panel" + ("" if _s == "normal" else "_" + _s), "fire", alarm_panel(_s))


def sprinkler(burst):
    els = [el("pipe_drop", [7.25, 14, 7.25], [8.75, 16, 8.75], "b"), el("frame", [7.5, 12, 6.5], [8.5, 14, 9.5], "b"),
           el("deflector", [6, 11.5, 6], [10, 12, 10], "b")]
    if not burst:
        els.append(el("bulb", [7.6, 12.2, 7.6], [8.4, 13.8, 8.4], "lr"))
    return els


m("sprinkler_head", "fire", sprinkler(False))
m("sprinkler_head_burst", "fire", sprinkler(True))
m("deluge_nozzle", "fire", hcyl("pipe", "x", 2, 0, 16, "rd", sides=8) + vcyl("nozzle", 8, 8, 1.2, 4, 6, "b", 8)
  + [el("deflector", [6, 2.5, 6], [10, 3, 10], "b"), el("arm", [7.5, 3, 7.5], [8.5, 4, 8.5], "b")])
m("foam_chamber", "fire", [el("chamber", [4, 4, 8], [12, 12, 15], "rd"), el("flange", [3, 3, 15], [13, 13, 16], "d")]
  + hcyl("inlet", "x", 1.5, 0, 4, "rd", c=(8, 6, 11), sides=8))


def proportioner(ratio=3):
    return [el("skid", [1, 0, 1], [15, 2, 15], "d")] + vcyl("bladder_tank", 8, 8, 6, 2, 28, "rd", top="rd") + [
        el("proportioner", [12, 4, 6], [16, 8, 10], "b"),
        el("label", [5, 18, 1.9], [11, 22, 2], "ws" if ratio == 3 else "ye")]


m("foam_proportioner", "fire", proportioner(3))
m("foam_proportioner_6", "fire", proportioner(6))
m("fire_monitor", "fire", [el("base_flange", [4, 0, 4], [12, 1, 12], "d")] + vcyl("riser", 8, 8, 2, 1, 8, "rd", 8))
m("fire_monitor/turret", "fire", vcyl("swivel", 8, 8, 2.5, 8, 11, "rd", 8)
  + [el("yoke_w", [4.5, 10, 7], [5.5, 14, 9], "rd"), el("yoke_e", [10.5, 10, 7], [11.5, 14, 9], "rd")], kind="partial")
m("fire_monitor/barrel", "fire", hcyl("barrel", "z", 1.6, 1, 12, "rd", c=(8, 13, 8), sides=8)
  + hcyl("nozzle", "z", 1.1, -2, 1, "b", c=(8, 13, 8), sides=8), kind="partial")
m("fire_hydrant", "fire", vcyl("barrel", 8, 8, 3, 0, 12, "rd", 8) + vcyl("bonnet", 8, 8, 2.2, 12, 14, "rd", 8)
  + [el("nut", [7.25, 14, 7.25], [8.75, 15, 8.75], "b")]
  + hcyl("outlet_w", "x", 1.3, 3, 5, "b", c=(8, 7, 8), sides=8) + hcyl("outlet_e", "x", 1.3, 11, 13, "b", c=(8, 7, 8), sides=8))


def fire_pump(jockey=False):
    s = 0.6 if jockey else 1.0
    return [el("baseplate", [0, 0, 2], [32, 1.5, 14], "d"),
            el("motor", [18, 1.5, 8 - 5 * s], [30, 1.5 + 10 * s, 8 + 5 * s], "rd")] + \
        hcyl("volute", "z", 5 * s, 3, 13, "rd", c=(8, 1.5 + 5 * s, 8)) + \
        vcyl("discharge", 8, 8, 1.5 * s, 1.5 + 10 * s, 1.5 + 13 * s, "rd", 8)


m("fire_water_pump", "fire", fire_pump())
m("fire_water_pump_jockey", "fire", fire_pump(True))
m("fire_water_pump/impeller", "fire", hcyl("impeller", "z", 3.5, 6, 9, "b", c=(8, 6.5, 8), sides=8)
  + shaft("z", 1, 0, 16, c=(8, 6.5, 8)), kind="partial")


def gas_cyl(charged=True):
    return vcyl("cylinder", 8, 8, 4.5, 0, 26, "rd", top="rd") + vcyl("valve_head", 8, 8, 2, 26, 29, "b", 8) + [
        el("gauge", [6, 26.5, 5.8], [10, 29.5, 6], "gauge", full=["north"]),
        el("bracket", [2, 18, 12], [14, 20, 16], "d"),
        el("charge_tag", [6, 14, 3.4], [10, 16, 3.6], "lg" if charged else "lr")]


m("gas_suppression_cylinder", "fire", gas_cyl())
m("gas_suppression_cylinder_discharged", "fire", gas_cyl(False))
m("fire_station", "fire", [el("building", [0, 0, 1], [16, 16, 16], "brk"),
                           el("door", [2, 0, 0.5], [14, 12, 1], "rd"),
                           el("sign", [4, 13, 0.6], [12, 15, 1], "sfire", full=["north"])])

# ======================================================================
# знаки, маркировка, связь
# ======================================================================
for _k in ("warn", "forbid", "mandatory", "ghs"):
    m("hazard_sign" + ("" if _k == "warn" else "_" + _k), "signs", [
        el("plate", [1, 1, 15], [15, 15, 16], "ws", faces={"north": f"sg_{_k}"}, full=["north"])])
m("hazard_diamond", "signs", [el("plate", [3, 3, 15.5], [13, 13, 16], "nfpa", faces={"north": "nfpa"}, full=["north"],
                                 rot=("z", 45, [8, 8, 15.75]))])
m("un_placard", "signs", [el("plate", [0, 4, 15], [16, 12, 16], "un", faces={"north": "un"}, full=["north"])])
m("pipe_marker", "piping", hcyl("pipe", "x", 3, 0, 16, "st") + hcyl("ring_1", "x", 3.2, 4, 6, "gas", sides=8)
  + hcyl("ring_2", "x", 3.2, 10, 12, "rd", sides=8) + [el("arrow", [6.5, 11.2, 7], [9.5, 11.4, 9], "ws")])


def intercom(talking=False, fault=False):
    return [el("box", [2, 1, 12], [14, 15, 16], "dg", faces={"north": "icm"}, full=["north"]),
            el("handset", [12.5, 3, 10.5], [14, 10, 12], "blk"),
            lamp("lamp_talk", 4, 13.5, 12, talking, "lg", 0.5),
            lamp("lamp_fault", 6, 13.5, 12, fault, "lr", 0.5)]


m("intercom_station", "ops", intercom())
m("intercom_station_talking", "ops", intercom(True))
m("intercom_station_fault", "ops", intercom(fault=True))
m("intercom_station_desk", "ops", [el("base", [2, 0, 3], [14, 3, 13], "dg"),
                                   el("console", [2, 3, 6], [14, 9, 13], "dg", faces={"north": "icm"}, full=["north"],
                                      rot=("x", -22.5, [8, 3, 6]))])


def loudspeaker(mount="wall", active=False):
    els = [el("horn_throat", [6, 6, 8], [10, 10, 12], "dg"),
           el("horn_mouth", [3, 3, 4], [13, 13, 8], "dg")]
    if mount == "wall":
        els += [el("bracket", [7, 7, 12], [9, 9, 16], "d"), el("plate", [5, 5, 15], [11, 11, 16], "d")]
    elif mount == "pole":
        els += [el("clamp", [6, 6, 12], [10, 10, 14], "d")] + vcyl("pole", 8, 16.0 - 1.5, 1.5, 0, 16, "gal", 8)
    else:
        els += [el("rod", [7.5, 10, 9], [8.5, 16, 10], "d")]
    if active:
        els.append(el("glow_active", [7.5, 5.4, 11.8], [8.5, 6, 12], "lg"))
    return els


m("plant_loudspeaker", "ops", loudspeaker())
m("plant_loudspeaker_pole", "ops", loudspeaker("pole"))
m("plant_loudspeaker_ceiling", "ops", loudspeaker("ceiling"))
m("plant_loudspeaker_active", "ops", loudspeaker(active=True))

# огнетушитель — объёмный предмет
m("fire_extinguisher", "items", vcyl("body", 8, 8, 3, 1, 12, "rd", 8) + vcyl("head", 8, 8, 1.4, 12, 14, "b", 8)
  + [el("handle", [6, 14, 7.5], [11, 14.75, 8.5], "d"), el("label", [6, 4, 4.9], [10, 9, 5.1], "ws"),
     el("hose", [10, 6, 7.5], [11, 13, 8.5], "blk")], kind="item")


# ======================================================================
# срез обслуживания I0–I1 (13 §13.6.5–13.6.7, M3)
# ======================================================================
OWN.update({"mpf": B("maintenance_panel_front"), "rgf": B("recording_gauge_face")})
_SEV = {0: None, 1: "ly", 2: "lo", 3: "lr"}


def maint_panel(sev=0):  # настенный щит 14×14×3: 6 ламп категорий цветом наивысшей тяжести
    els = [el("box", [1, 1, 13], [15, 15, 16], "en", faces={"north": "mpf"}, full=["north"])]
    for i in range(6):
        x = 3.5 + (i % 3) * 4
        y = 15 - (7.5 + (i // 3) * 4)
        els.append(lamp(f"lamp_{i}", x, y, 13, sev > 0 and i == 0, _SEV[sev] or "lg", 0.9))
    return els


m("maintenance_panel", "ops", maint_panel())
for _s in (1, 2, 3):
    m(f"maintenance_panel_severity{_s}", "ops", maint_panel(_s))


def hydrotest_pump(pumping=False):  # ручной опрессовочный насос: бачок, плунжер, манометр, шланг
    return [el("tank", [3, 0, 4], [13, 6, 13], "ci"),
            el("plunger", [6, 6, 6], [10, 11, 10], "st"),
            el("gauge", [11, 7, 7], [14, 10, 8], "blk"),
            el("hose", [7, 1, 13], [9, 3, 16], "r"),
            el("pivot", [7, 11, 7], [9, 12, 9], "d")]


m("hydrotest_pump", "ops", hydrotest_pump())
m("hydrotest_pump/lever", "ops", [el("lever", [7.5, 11, 1], [8.5, 12, 15], "d"),
                                  el("grip", [7, 10.5, 1], [9, 12.5, 3], "r")], kind="partial")


def recording_gauge(paper=True):  # самописец на установке 10×12×4: латунный корпус, дверца с лентой
    return [el("case", [3, 2, 12], [13, 14, 16], "b", faces={"north": "rgf" if paper else "b"},
               full=["north"] if paper else None),
            el("clock_knob", [7, 14, 13], [9, 15, 15], "d")]


m("recording_gauge", "instruments", recording_gauge())
m("recording_gauge_empty", "instruments", recording_gauge(False))
m("recording_gauge/drum", "instruments", [el("drum", [5, 4, 11.5], [11, 12, 12], "w")], kind="partial")
