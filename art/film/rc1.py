"""Shot plans for the 1.0 overview video (1920x1080, 60 fps): writes film.json for FilmRecorder.

    python art/film/rc1.py pc-mod/run-film/film.json <group|shot> ...

Groups: i0 (boiler house, foundry, mine, power, outro: the I0 set), eras (later-era heroes). Shot names and lengths
follow the edit's shot list (Prompt_Critical-youtube/06-long-overview-rc1/shots.json); footage lands in film/rc1.
"""
import json
import sys

from shots import FFMPEG, key, panel

DUSK, NIGHT, DAY = 12700, 18000, 6000
WIDE = [1920, 1080]


def mv(name, dur, a, b, la, lb=None, fov=(55, 55), time=DUSK, ease="inOut", **kw):
    s = {"name": name, "duration": dur, "size": WIDE, "scale": WIDE, "time": time, "ease": ease, "gamma": 0.6,
         "setup": ["function filmset:refresh"],
         "keys": [key(0, a, la, fov[0]), key(dur, b, lb or la, fov[1])]}
    s.update(kw)
    return s


def i0():
    return [
        # 01 intro
        mv("i02_yard_dusk", 9, [44, -50, 16], [34, -54.5, 27], [10, -48, 52], [8, -50, 50], fov=(50, 45)),
        panel("i03_panel_boiler", 9, None, [9.0, -58.4, 44.2], [9.0, -59.0, 48.0], [9, -59, 49], [
            {"t": 1.2, "at": [26, 36], "dur": 0.9},
            {"t": 3.2, "cursor": "Fire", "dur": 0.8},
            {"t": 4.3, "click": True},
            {"t": 5.6, "at": [112, 36], "dur": 0.9}]),
        mv("i04_night_wide", 10, [46, -44, 6], [22, -46, 10], [30, -56, 45], [24, -56, 47], fov=(65, 60), time=NIGHT),
        # 02 mine
        mv("m01_hill", 6, [155, -39, 72], [148, -40, 62], [112, -40, 64], [112, -39, 64], fov=(45, 40)),
        mv("m02_cage", 6, [120, -40.5, 56], [119, -41.5, 58], [112, -41, 64], [112, -43.5, 64], settle=200,
           setup=["function filmset:refresh", "pc hoist 112 -44 72 0"], events=[{"t": 0.8, "cmd": "pc hoist 112 -44 72 1"}]),
        {"name": "m03_drift", "duration": 6, "size": WIDE, "scale": WIDE, "time": DUSK, "ease": "inOut", "gamma": 0.6,
         "keys": [key(0, [112.5, -57.5, 24], [112.5, -58.5, 40], 60), key(3, [112.5, -58.6, 36], [112.5, -59, 48], 60),
                  key(6, [112.5, -59, 47], [112.5, -59.2, 60], 60)]},
        mv("m04_longwall", 6, [118, -59.0, 62.5], [131, -59.0, 62.5], [121, -59.4, 65], [134, -59.4, 65], fov=(60, 60), ease="linear"),
        mv("m05_shearer", 5, [133, -59.2, 66.3], [130, -59.3, 66.3], [126, -59.6, 66.3], fov=(60, 60)),
        mv("m06_fan", 6, [94, -43.5, 55], [101, -43.0, 57], [98, -44, 64.5], fov=(55, 50)),
        # 03 boiler house
        mv("b01_facade", 6, [12, -59.0, 32], [12, -58.0, 36], [12, -56, 40], [9, -40, 53], fov=(60, 60)),
        mv("b02_firing_floor", 7, [1.5, -58.6, 44.0], [14.5, -58.6, 44.0], [5, -59.2, 48], [16, -59.2, 48], fov=(60, 60), ease="linear"),
        mv("b03_stoker", 5, [10.4, -58.9, 44.4], [9.2, -59.1, 45.2], [9, -59.4, 47.5], fov=(50, 45)),
        panel("b04_panel_boiler", 8, None, [4.0, -58.4, 44.2], [4.0, -59.0, 48.0], [4, -59, 49], [
            {"t": 1.0, "at": [26, 36], "dur": 0.9},
            {"t": 4.0, "at": [112, 36], "dur": 0.9}]),
        mv("b05_engine", 7, [17, -57.6, 45.5], [17.8, -57.8, 52], [12, -59, 49.5], fov=(55, 55)),
        panel("b06_relief", 9, None, [12.0, -58.4, 44.4], [12.0, -59.0, 48.0], [12, -59, 49], [
            {"t": 1.0, "at": [24, 40], "dur": 0.8},
            {"t": 3.5, "at": [80, 36], "dur": 0.8},
            {"t": 6.0, "at": [120, 40], "dur": 0.8}]),
        # 04 foundry
        mv("f01_aisle", 7, [33.5, -57.5, 43.5], [55, -57.5, 43.5], [38, -59.5, 49], [59, -59.5, 49], fov=(60, 60), ease="linear"),
        mv("f02_cupola", 5, [39.0, -58.0, 45.5], [38.2, -57.6, 46.0], [36, -58.5, 49], fov=(50, 50)),
        mv("f03_lathe", 5, [55.5, -59.0, 46.3], [53.0, -59.0, 46.0], [53.5, -59.8, 49], fov=(50, 50)),
        mv("f04_drill", 4, [60.5, -58.8, 46.0], [59.0, -58.8, 45.6], [58, -59.5, 49], fov=(50, 50)),
        # 06 power: the set's own power plant
        mv("p01_generator", 7, [31.5, -57.0, 46.5], [30.5, -57.5, 55.5], [26, -58.5, 51], fov=(55, 50)),
        mv("p02_line", 8, [38, -57, 29], [68, -56, 30], [60, -51, 36], [96, -51, 36], fov=(60, 60), ease="linear"),
        mv("p03_substation", 6, [51, -58.2, 32.5], [44.5, -58.2, 33], [47, -58.8, 37.5], fov=(55, 55)),
        mv("p04_lights_on", 10, [34, -57, 42], [45, -57, 43], [55, -58, 50], [60, -58, 50], fov=(60, 60), time=NIGHT),
        # 12 outro
        mv("e01_pullback", 18, [22, -55, 33], [44, -30, -12], [24, -55, 48], [40, -52, 60], fov=(55, 65), time=NIGHT),
        mv("e02_night", 18, [146, -36, 28], [132, -37, 20], [110, -48, 55], [104, -50, 50], fov=(55, 55), time=NIGHT),
    ]


RIG = "1 -60 161"                    # driller's console of the drilling rig
FURNACE, DRILL = "16 -57 135", "18 -60 131"
VVER = "94 -59 166"
HOT = [f"filmset furnace {FURNACE} hot"]
AT_POWER = [f"filmset reactor {VVER} power 20"]


def eras():
    """Later-era heroes. The units are started inside each shot, seconds before the camera: a gas kick turns into a
    blowout in < 5 game seconds unless the BOP is closed, the VVER has no heat sink and the furnace's cold blast
    chills the hearth by ~40 C per 5 s (measured 2026-11-20)."""
    return [
        # 05 steel
        mv("s01_coke", 9, [-4, -55, 125], [12, -55, 125], [4, -58, 134], [4, -58, 134], fov=(55, 55)),
        mv("s02_blast_furnace", 10, [9, -58, 121], [25, -56, 120], [19, -57, 134], [19, -47, 134], fov=(60, 65),
           setup=["function filmset:refresh"] + HOT, settle=80),
        mv("s03_tap", 9, [23.5, -58, 126.5], [22, -58.6, 125.8], [19, -59.5, 130.5], fov=(55, 50),
           setup=["function filmset:refresh"] + HOT, settle=40, events=[{"t": 1.0, "cmd": f"pc use {DRILL}"}]),
        mv("s04_arc", 7, [24, -56, 126], [36, -56, 125.5], [31.5, -58, 133.5], fov=(55, 55)),
        # 07 oil and gas
        mv("o01_rig", 9, [-7, -54, 153], [14, -53, 152], [5.5, -56, 165.5], [5.5, -55, 165.5], fov=(60, 60)),
        mv("o02_kick", 12, [9, -57, 156], [7, -56.5, 157.5], [5, -58, 165], [5, -57, 165], fov=(55, 55),
           events=[{"t": 0.3, "cmd": f"filmset kick {RIG}"}, {"t": 2.8, "cmd": f"pc panel {RIG} 4"}]),
        panel("o03_bop", 8, None, [1.0, -58.4, 158.0], [1.0, -59.5, 161.0], [1, -60, 161], [
            {"t": 1.0, "at": [30, 40], "dur": 0.8}, {"t": 4.0, "at": [120, 40], "dur": 0.8}]),
        mv("o04_pumpjack", 8, [27.5, -57, 158], [25, -57, 157], [22, -59, 165], fov=(55, 55)),
        mv("o05_refinery", 9, [31, -56, 154], [43, -56, 154], [37.5, -58.5, 163], fov=(55, 55)),
        # 08 chemistry
        mv("h01_pyrolysis", 9, [44, -56, 154], [56, -55, 154], [51, -58, 163.5], fov=(55, 55)),
        mv("h02_gas_turbine", 9, [58, -57, 157], [58, -56, 171], [64.5, -58, 166], fov=(55, 55)),
        mv("h03_hrsg", 9, [72, -55, 164], [71, -54, 174], [64.5, -57, 168], fov=(55, 55)),
        # 09 nuclear
        mv("n01_centrifuges", 12, [72, -57, 155], [84, -57, 156], [78, -59.5, 164], fov=(55, 55), ease="linear"),
        mv("n02_fuel", 8, [76, -58.5, 158.5], [80, -58.5, 158.5], [78, -59.5, 163], fov=(50, 50)),
        mv("n03_vver_hall", 13, [85, -54, 154], [101, -54, 155], [93.5, -58, 164], fov=(60, 60),
           setup=["function filmset:refresh"] + AT_POWER, settle=60),
        dict(panel("n04_core", 13, None, [94, -57.5, 162.5], [94, -58.5, 166], [94, -59, 166], [
            {"t": 1.5, "at": [40, 50], "dur": 0.9}, {"t": 6.0, "at": [140, 50], "dur": 0.9}]),
            setup=["function filmset:refresh"] + AT_POWER, settle=60),
        # 10 control
        mv("k01_control_room", 11, [58, -58.2, 132.2], [58.5, -58.0, 133.5], [61.5, -58.5, 134.5], fov=(70, 65)),
        mv("k02_annunciator", 8, [59.5, -57.8, 134.4], [60.2, -57.8, 134.6], [62, -57.8, 134], fov=(55, 50)),
        panel("k03_relay", 7, None, [46, -59.4, 44.5], [46, -59.6, 42], [46, -60, 42], [
            {"t": 1.0, "at": [30, 40], "dur": 0.8}, {"t": 3.5, "at": [110, 40], "dur": 0.8}]),
        dict(panel("k04_scram", 8, None, [94, -57.5, 162.5], [94, -58.5, 166], [94, -59, 166], [
            {"t": 1.0, "at": [40, 50], "dur": 0.8}, {"t": 2.5, "cmd": f"pc panel {VVER} 0"}]),
            setup=["function filmset:refresh"] + AT_POWER, settle=60),
        # 11 accidents
        mv("a01_alarm", 9, [58.2, -58.0, 136.3], [59.0, -58.0, 135.8], [62, -58, 134], [62, -58.2, 134], fov=(65, 60), time=NIGHT),
        panel("a02_ops", 9, None, [112.0, -43.6, 74.2], [109.0, -44.5, 74.0], [109, -45, 74], [
            {"t": 1.0, "at": [30, 40], "dur": 0.8}, {"t": 4.0, "at": [120, 40], "dur": 0.8}]),
        mv("a03_fire", 10, [57.5, -58.4, 136.5], [58.5, -58.2, 135.5], [61, -58.8, 133], fov=(65, 60)),
        # 00 cold open and 01 flyover
        mv("c02_relief", 4, [11, -50.5, 44], [10, -50.5, 45], [9, -54, 50], fov=(55, 50),
           setup=["function filmset:refresh", "filmset boiler 9 -59 49 1.36 0.67"], settle=60),
        mv("c03_annunciator", 4, [60.5, -57.8, 134.8], [60.9, -57.8, 134.6], [62, -57.8, 134], fov=(45, 40), time=NIGHT),
        {"name": "i01_flyover", "duration": 14, "size": WIDE, "scale": WIDE, "time": DUSK, "ease": "inOut", "gamma": 0.6,
         "setup": ["function filmset:refresh"],
         "keys": [key(0, [140, -30, 40], [112, -42, 68], 60), key(7, [70, -38, 100], [40, -52, 60], 60),
                  key(14, [50, -36, 112], [50, -56, 160], 60)]},
        # last: the blowout (the kick left alone) wrecks the derrick, nothing on the rig can be filmed after it
        mv("c01_kick", 4, [12, -55, 150], [11, -55, 151], [5, -56, 165], fov=(60, 55),
           setup=["function filmset:refresh", f"filmset kick {RIG}"], settle=70),
    ]


def eras_lit():
    """Era exteriors in late afternoon sun (the dusk was too dark on the bare pads); night and panels as they are."""
    out = []
    for s in eras():
        if s.get("time") == DUSK and not s.get("events", [{}])[0].get("panel"):
            s = dict(s, time=11000)
        out.append(s)
    return out


GROUPS = {"i0": i0, "eras": eras_lit}


def warm():
    return {"name": "warm", "duration": 0.1, "fps": 10, "stills": True, "size": [640, 360], "scale": [640, 360],
            "time": DAY, "settle": 1800, "setup": ["function filmset:refresh"], "keys": [key(0, [12, -55, 30], [8, -50, 50])]}


def main(out, names):
    all_shots = {s["name"]: s for g in GROUPS.values() for s in g()}
    shots = []
    for n in names:
        shots += GROUPS[n]() if n in GROUPS else [all_shots[n]]
    plan = {"ffmpeg": FFMPEG, "out": "film/rc1", "quit": True, "fps": 60, "crf": 16, "shots": [warm()] + shots}
    with open(out, "w") as fh:
        json.dump(plan, fh, indent=1)
    print(f"{len(shots)} shots, {sum(s['duration'] for s in shots):.0f} s")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
