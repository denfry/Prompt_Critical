"""11 после M3: доска показателей, тренажёры и место инструктора, электроника (руднотермическая печь,
поликремний, Чохральский, резка пластин, чистые комнаты, установщик компонентов, ЧРП), мир."""
from artkit2 import *

K2 = lambda n: f"block/common/content2_{n}.png"
OWN = {"rec": B("station_record_board_face"), "sim": B("training_simulator_front"), "trs": K2("trainer_stripe"),
       "vfd": B("variable_frequency_drive_front"), "cr": B("cleanroom_panel"), "bit": B("natural_bitumen"),
       "seep": B("gas_seep_vent"), "asm": B("component_assembler_top"), "si": K2("silicon"),
       "cglow": K2("crucible_glow")}


def m(id_, cat, els, kind="block"):
    mk2(id_, cat, els, OWN, kind)


# ======================================================================
# БЩУ и учебный центр
# ======================================================================
m("station_record_board", "control", [
    el("frame", [0, 2, 14.5], [32, 14, 16], "wood"),
    el("face", [1, 3, 14.25], [31, 13, 14.5], "wood", faces={"north": "rec"}, full=["north"]),
    el("glass", [1, 3, 14], [31, 13, 14.1], "gls"),
])


def sim_cabinet(running):
    return cabinet("r32", "sim", y1=31, z0=1) + [
        lamp(f"lamp_{i}", 3.5 + i * 3, 28, 1, running and i % 2 == 0, "ly" if i % 2 else "lg", 0.6) for i in range(4)]


m("training_simulator", "control", sim_cabinet(False))
m("training_simulator_running", "control", sim_cabinet(True))


def sim_console(active):  # секция пульта как control_desk с жёлтой полосой «ТРЕНАЖЁР»
    return [el("plinth", [0.5, 0, 3], [15.5, 1, 15.5], "pnd"),
            el("body", [0.5, 1, 2], [15.5, 9, 15.5], "pn", faces={"north": "trs"}, full=["north"]),
            el("back", [0.25, 1, 14], [15.75, 15.5, 16], "pns"),
            el("desktop", [0, 3, 1], [16, 10, 15], "pns", rot=("x", -22.5, [8, 9, 1])),
            el("armrest", [0, 8, 0], [16, 9.5, 2], "pnd"),
            lamp("lamp_active", 8, 14, 14, active, "ly", 0.75)]


m("simulator_console", "control", sim_console(False))
m("simulator_console_active", "control", sim_console(True))


def instructor(active):
    return [el("desk_top", [0, 10, 2], [32, 11, 14], "pns"),
            el("leg_w", [0.5, 0, 3], [1.5, 10, 13], "pnd"),
            el("leg_e", [30.5, 0, 3], [31.5, 10, 13], "pnd"),
            el("pedestal", [22, 0, 3], [30, 10, 13], "pn"),
            el("monitor_l", [3, 12.5, 9], [14, 20, 10], "scr" if active else "pblk"),
            el("monitor_r", [18, 12.5, 9], [29, 20, 10], "scr" if active else "pblk"),
            el("stand_l", [8, 11, 9.5], [9, 12.5, 11], "d"),
            el("stand_r", [23, 11, 9.5], [24, 12.5, 11], "d"),
            el("keyboard", [8, 11, 3], [18, 11.5, 6], "dg"),
            el(("glow_" if active else "") + "pause_button", [22, 11, 3.5], [24, 12, 5.5], "lr" if active else "red")]


m("instructor_station", "control", instructor(False))
m("instructor_station_active", "control", instructor(True))


# ======================================================================
# руднотермическая печь 3×4×3: ванна 3×3×3 и свод 3×1×3; электроды — partial (ход по Y)
# ======================================================================
def saf(running):
    return [el("shell", [-16, -16, -15], [32, 14, 32], "ref"),
            el("band", [-16, -4, -15.5], [32, -2, 32], "d"),
            el("taphole_lip", [4, -13, -16], [12, -12, -15], "cus"),
            el("glow_taphole" if running else "taphole", [5, -12, -16], [11, -7, -15], "hot" if running else "soot")]


m("submerged_arc_furnace", "electronics", saf(False))
m("submerged_arc_furnace_running", "electronics", saf(True))
m("submerged_arc_furnace_roof", "electronics", [
    el("roof", [-14, 0, -14], [30, 4, 30], "ref"),
    el("hood", [-10, 4, -10], [26, 12, 26], "d"),
] + [x for i, (cx, cz) in enumerate(((0, 2), (16, 2), (8, 16))) for x in
     vcyl(f"bus_{i}", cx, cz, 2.5, 12, 14, "cus", 8)])
for _i, (_cx, _cz) in enumerate(((0, 2), (16, 2), (8, 16))):
    m(f"submerged_arc_furnace/electrode_{_i + 1}", "electronics",
      vcyl("electrode", _cx, _cz, 2, -10, 16, "g", 8) + vcyl("clamp", _cx, _cz, 3, 14, 17, "cus", 8), kind="partial")

# реактор синтеза трихлорсилана 1×2×1: стальной аппарат, жёлтые полосы, патрубки «горючие газы»
m("trichlorosilane_reactor", "electronics", legs(3, 13, 3, 13, 4, 1)
  + vvessel("shell", 5.5, 4, 28, "st", skirt=False) + vcyl("band_1", 8, 8, 5.8, 12, 13.5, "ye")
  + vcyl("band_2", 8, 8, 5.8, 22, 23.5, "ye")
  + nozzle("feed", "x", 0, (8, 8, 8), 1.2, "gas") + nozzle("off_gas", "x", 16, (8, 26, 8), 1.2, "gasr"))


def siemens(running):  # колокол осаждения поликремния, смотровое окно
    els = [el("baseplate", [1, 0, 1], [15, 3, 15], "ss")] + vcyl("bell", 8, 8, 6.5, 3, 28, "sss", top="ss")
    els += [el("port", [6, 14, 0.6], [10, 18, 2], "st"),
            el("glow_window" if running else "window", [6.5, 14.5, 0.4], [9.5, 17.5, 0.6], "hot" if running else "gls")]
    return els


m("siemens_reactor", "electronics", siemens(False))
m("siemens_reactor_running", "electronics", siemens(True))


def cz_puller(pulling):  # Чохральский 1×3×1 (якорь — средний блок): камера с тиглем, тянущая камера, шток сверху
    els = [el("base", [1, -16, 1], [15, -10, 15], "w")] + vcyl("melt_chamber", 8, 8, 6.5, -10, 4, "sss", top="ss")
    els += vcyl("pull_chamber", 8, 8, 3.5, 4, 31.9, "sss", top="ss")
    els += [el("port", [6, -6, 0.6], [10, -2, 2], "st"),
            el("glow_crucible" if pulling else "crucible", [6.5, -5.5, 0.4], [9.5, -2.5, 0.6], "cglow" if pulling else "gls")]
    return els


m("czochralski_puller", "electronics", cz_puller(False))
m("czochralski_puller_pulling", "electronics", cz_puller(True))
m("czochralski_puller/crystal", "electronics", vcyl("ingot", 8, 8, 2.5, -4, 4, "si", 8)
  + vcyl("neck", 8, 8, 0.75, 4, 6, "si", 8), kind="partial")
m("czochralski_puller/seed_shaft", "electronics", vcyl("shaft", 8, 8, 0.5, 6, 30, "st", 8), kind="partial")

# проволочная пила: белый корпус, прозрачный кожух; барабан — partial (ось X)
m("wafer_saw", "electronics", [el("base", [0, 0, 1], [16, 6, 15], "w"),
                               el("cover", [1, 6, 2], [15, 14, 14], "gls"),
                               el("frame", [0, 14, 1], [16, 15, 15], "w"),
                               el("panel", [12, 2, 0.5], [15, 5, 1], "scr")])
m("wafer_saw/blade", "electronics", hcyl("guide_roller", "x", 3, 2, 14, "pblk", c=(8, 10, 8), sides=8)
  + hcyl("axle", "x", 0.75, 1, 15, "st", c=(8, 10, 8), sides=8), kind="partial")

# чистые комнаты: панель, шлюз 1×2×1 (дверь — partial), окно
m("cleanroom_panel", "electronics", [el("panel", [0, 0, 0], [16, 16, 16], "cr")])


def airlock(state="closed"):
    lam = {"closed": ("lg", False), "open": ("lg", True), "purging": ("lb", True)}[state]
    return [el("jamb_w", [0, 0, 6], [2, 32, 10], "cr"), el("jamb_e", [14, 0, 6], [16, 32, 10], "cr"),
            el("header", [0, 28, 6], [16, 32, 10], "cr"),
            lamp("lamp_state", 8, 30, 6, lam[1], lam[0], 0.6)]


for _s in ("closed", "open", "purging"):
    m("cleanroom_airlock" + ("" if _s == "closed" else "_" + _s), "electronics", airlock(_s))
m("cleanroom_airlock/door", "electronics", [
    el("leaf", [2, 0, 7.5], [14, 28, 8.5], "cr"),
    el("window", [5, 16, 7.4], [11, 24, 8.6], "gls"),
], kind="partial")
m("cleanroom_window", "electronics", [
    el("frame_t", [0, 14, 6], [16, 16, 10], "cr"), el("frame_b", [0, 0, 6], [16, 2, 10], "cr"),
    el("frame_w", [0, 2, 6], [2, 14, 10], "cr"), el("frame_e", [14, 2, 6], [16, 14, 10], "cr"),
    el("glass", [2, 2, 7.5], [14, 14, 8.5], "gls"),
])


# автомат установки компонентов: зелёный стол с платой в жёлтой рамке; голова — partial
def assembler(working):
    return [el("table", [0, 0, 0], [16, 8, 16], "w", faces={"up": "asm"}, full=["up"]),
            el("gantry_w", [0, 8, 0], [1.5, 15, 16], "ye"), el("gantry_e", [14.5, 8, 0], [16, 15, 16], "ye"),
            el("rail", [0, 14, 7], [16, 15, 9], "st"),
            lamp("lamp_work", 2, 6, 0, working, "lg", 0.6)]


m("component_assembler", "electronics", assembler(False))
m("component_assembler_working", "electronics", assembler(True))
m("component_assembler/head", "electronics", [
    el("carriage", [6, 12, 6], [10, 14.5, 10], "d"),
    el("nozzle", [7.5, 9, 7.5], [8.5, 12, 8.5], "st"),
], kind="partial")


def vfd(state="off"):
    return cabinet("ral", "vfd", y1=31, z0=1) + [
        el("vent_top", [3, 31, 4], [13, 32, 13], "vent"),
        lamp("lamp_on", 4, 28, 1, state in ("on", "fault"), "lg", 0.6),
        lamp("lamp_fault", 7, 28, 1, state == "fault", "lr", 0.6)]


m("variable_frequency_drive", "electronics", vfd())
m("variable_frequency_drive_on", "electronics", vfd("on"))
m("variable_frequency_drive_fault", "electronics", vfd("fault"))

# ======================================================================
# мир
# ======================================================================
m("natural_bitumen", "world", [el("block", [0, 0, 0], [16, 16, 16], "bit")])
m("gas_seep_vent", "world", [el("block", [0, 0, 0], [16, 16, 16], "seep")])
m("gas_seep_vent_burning", "world", [el("block", [0, 0, 0], [16, 16, 16], "seep"),
                                     el("glow_vent", [5, 15.9, 5], [11, 16.1, 11], "gfl")])
