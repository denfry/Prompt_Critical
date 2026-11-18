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


# реле, реле времени, шаговый искатель, командоаппарат, реле процесса, блок питания реле, релейная стойка, аналоговые блоки, станция управления и преобразователь сигнала нарисованы вручную в Blockbench: art/blockbench/automation/relay_logic.author.js


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


# нарисовано вручную в Blockbench: art/blockbench/control/panel_devices.author.js


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
