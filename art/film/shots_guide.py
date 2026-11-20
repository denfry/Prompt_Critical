"""Shot list for the video guide, episode 0 (overview): writes film.json for FilmRecorder.

    python art/film/shots_guide.py pc-mod/run-film/film.json survey      — one still per set area, daylight
    python art/film/shots_guide.py pc-mod/run-film/film.json tour [shot ...]  — overview film of the whole plant
    python art/film/shots_guide.py pc-mod/run-film/film.json ep00 [shot ...]

Episode 0 reuses the I0 trailer shots from shots.py and adds the later-era set built by lateset.py.
"""
import json
import shutil
import sys

import plant
from shots import DUSK, WIDE, key, move, panel, trailer

FFMPEG = shutil.which("ffmpeg") or "ffmpeg"
DAY = 6000


def still(name, pos, look, fov=60, time=DAY):
    return {"name": name, "duration": 0.1, "fps": 10, "stills": True, "size": [1280, 720], "scale": [1280, 720],
            "time": time, "gamma": 0.6, "keys": [key(0, pos, look, fov)]}


def survey():
    return [
        still("sv_steel", [20, -44, 110], [15, -53, 134]),
        still("sv_furnace", [5, -54, 118], [5, -54, 133], 55),
        still("sv_oil", [78, -40, 108], [78, -55, 136]),
        still("sv_rig", [70, -52, 124], [58, -52, 134], 60),
        still("sv_chem", [125, -42, 112], [125, -56, 148]),
        still("sv_enrich", [16, -56, 185], [16, -59, 200], 70),
        still("sv_reactor", [55, -50, 186], [55, -57, 195], 70),
        still("sv_control", [77, -57, 188], [77, -58, 197], 70),
        still("sv_aerial", [70, -10, 90], [60, -58, 170], 60, DUSK),
        still("sv_plant", [268, -12, -45], [268, -58, 40], 70),
    ] + [still("pl_" + d[0], [ox + w / 2, -50, oz - 12], [ox + w / 2, -58, oz + 8], 75)
         for d, ox, oz, w, _, _ in plant.layout()]


I0 = ("tr03_firing_floor", "tr05_panel_boiler", "tr06_engine", "tr14_hill", "tr16_drift", "tr18_shearer", "tr02_facade")


GOLD = 11800  # low sun, long shadows


def tour():
    """Overview film: the hand-built sets, then a dolly along the front of every plant district."""
    m = lambda *a, **k: move(*a, time=GOLD, **k)
    shots = [
        m("tu01_aerial_in", 7.0, [120, -8, -70], [170, -30, -25], [230, -58, 50], [250, -58, 40], fov=(60, 55)),
        m("tu02_furnace_rise", 5.0, [5, -59.5, 122], [5, -44, 118], [5, -58, 133], [5, -47, 133], fov=(60, 55)),
        m("tu03_stoves", 4.0, [9, -57, 124], [24, -57, 124], [14, -55, 134], [27, -56, 134], ease="linear"),
        m("tu04_rig", 5.0, [68, -58, 122], [48, -50, 126], [58, -54, 134], fov=(55, 60)),
        m("tu05_pumpjacks", 4.0, [70, -58.5, 131], [92, -58.5, 131], [74, -59.5, 138], [96, -59.5, 138], ease="linear"),
        m("tu06_enrich", 5.0, [3, -58.3, 189], [29, -58.3, 189], [9, -59.5, 197], [35, -59.5, 197], fov=(70, 70), ease="linear"),
        m("tu07_reactor", 5.0, [55, -45, 189], [55, -54.5, 188], [55, -58, 195], [55, -58.5, 195], fov=(65, 60), gamma=1.0),
        m("tu08_control", 4.0, [72, -57, 188], [82, -57.5, 188.5], [78, -59, 196], [80, -59, 196], fov=(70, 70), gamma=1.0),
    ]
    for i, (d, ox, oz, w, _, _) in enumerate(plant.layout()):
        z, y = oz - 5, -57.2
        shots.append(m(f"tu{20 + i}_{d[0]}", 5.0, [ox + 1, y, z], [ox + w - 8, y, z], [ox + 9, -59.6, oz + 7],
                       [ox + w, -59.6, oz + 7], fov=(62, 62), ease="linear"))
    shots.append(m("tu40_aerial_out", 7.0, [268, -40, -20], [268, -2, -110], [268, -58, 40], [240, -58, 60], fov=(55, 65)))
    return shots


EVE = 12300  # sunset: lit windows and flames read, the sky still has colour


def works_shots():
    """Finale: the plant at work. Panels are live machine screens; operator actions are panel commands."""
    import works
    m = lambda *a, **k: move(*a, time=EVE, **k)
    rx, ry, rz = works.REACTOR
    cx, cy, cz = works.CONSOLE
    pyro = [f"film prime pyrolysis {x} {y} {z}" for x, y, z in works.FURNACES]
    return [
        m("wk01_aerial", 6.0, [60, -22, 222], [64, -38, 238], [64, -56, 270], [64, -57, 272], fov=(60, 55)),
        m("wk02_pyrolysis", 4.0, [57, -57.5, 256], [81, -57.5, 256], [62, -58, 263], [81, -58, 263], ease="linear",
          setup=["function filmset:refresh"] + pyro, settle=80),
        m("wk03_combined", 4.0, [51, -59.2, 255], [52, -55, 261], [45, -55, 268], [45, -47, 268], fov=(60, 65)),
        panel("wk04_drill_panel", 6.0, None, [cx, cy + 1.6, cz - 0.6], [cx, cy + 0.6, cz + 2], list(works.CONSOLE),
              [{"t": 2.8, "cmd": f"pc panel {cx} {cy} {cz} 4"}]) | {"time": EVE, "settle": 240,
                                                                   "setup": [f"film prime kick {cx} {cy} {cz}"]},
        m("wk05_bop", 3.0, [101, -58.5, 252], [100, -58, 253.5], [98, -59.5, 262], fov=(55, 50)),
        panel("wk06_reactor_panel", 7.0, None, [rx, ry + 1.4, rz - 3], [rx, ry, rz], list(works.REACTOR),
              [{"t": 1.6, "cmd": f"pc panel {rx} {ry} {rz} 0"}]) | {"time": EVE, "settle": 4,
                                                                     "setup": [f"film prime reactor_power {rx} {ry} {rz}"]},
        m("wk07_reactor_hall", 4.0, [10, -57.5, 259], [10, -56.5, 263], [10, -57.5, 268], [10, -56, 268], fov=(70, 60), gamma=1.0),
        m("wk08_cascade", 4.0, [109.5, -58.5, 255], [123, -58.5, 255], [112, -59.5, 258], [123, -59.5, 258], ease="linear",
          fov=(70, 70), gamma=1.0),
        m("wk09_generators", 3.0, [24, -58.8, 262], [33, -58.8, 262], [26, -59.5, 264], [31, -59.5, 264], ease="linear"),
        m("wk10_aerial_out", 7.0, [64, -45, 240], [40, -12, 205], [64, -57, 270], [70, -58, 275], fov=(55, 70)),
    ] + [s for s in trailer() if s["name"] in ("tr05_panel_boiler", "tr15_cage")]


def ep00():
    return [s for s in trailer() if s["name"] in I0]


def main(path, mode, only=None):
    warm = {"name": "warm", "duration": 0.1, "fps": 10, "stills": True, "size": [640, 360], "scale": [640, 360],
            "time": DUSK, "settle": 600, "setup": ["function filmset:refresh", "tick sprint 2400"],
            "keys": [key(0, [12, -55, 30], [8, -50, 50])]}
    shots = {"survey": survey, "tour": tour, "works": works_shots, "ep00": ep00}[mode]()
    plan = {"ffmpeg": FFMPEG, "out": "film/" + mode, "quit": True, "fps": 60, "crf": 14,
            "shots": [warm] + [s for s in shots if not only or s["name"] in only]}
    with open(path, "w") as fh:
        json.dump(plan, fh, indent=1)
    print(len(plan["shots"]), "shots")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
