"""Shot list for the plant overview video in the Plant world (seed 62): writes film.json for FilmRecorder.

    python art/film/shots_plant.py pc-mod/run-film/film.json [--stills] [shot ...]

Order follows the overview: the whole plant from the air, then the eras I0..I4 with the power plants between them,
the mod integrations, one accident, and the plant at dusk. World coordinates come from art/film/plantworld.py
(plant_layout.json); a set's local superflat coordinates map to the world as noted per shot. Before recording, run
`/film run plant_start` so every unit is primed and fed. `--stills` writes JPEG frames instead of video, for a quick
storyboard check of every camera path.
"""
import json
import shutil
import sys

WIDE = [1920, 1080]
MORNING, NOON, DUSK = 1500, 6000, 12300
RIG = (-355, 67, 805)                    # driller's console of the oil set


def key(t, pos, look, fov=60):
    return {"t": t, "pos": pos, "look": look, "fov": fov, "roll": 0}


def move(name, dur, a, b, look_a, look_b=None, fov=(60, 60), ease="inOut", time=MORNING, **kw):
    s = {"name": name, "duration": dur, "size": WIDE, "scale": WIDE, "time": time, "ease": ease, "gamma": 0.6,
         "mode": "spectator", "keys": [key(0, a, look_a, fov[0]), key(dur, b, look_b or look_a, fov[1])]}
    s.update(kw)
    return s


def shots():
    return [
        # 1. the whole plant: a sweep east along the line of districts
        move("p01_aerial", 12, [-520, 170, 640], [-60, 150, 700], [-300, 70, 780], [-120, 70, 760], fov=(70, 65)),
        # 2. era I0 (set offset -500, +126, +830): the mine headframe, then the firing floor of the boiler house
        move("p02_mine", 6, [-400, 95, 880], [-380, 92, 886], [-388, 85, 902]),
        move("p03_boilers", 6, [-498.5, 67.4, 874.0], [-485.5, 67.4, 874.0], [-495, 66.8, 878], [-484, 66.8, 878],
             ease="linear"),
        # 3. era I1 steel (offset -106, +126, +636): the works, the skip up to the throat, the iron runner
        move("p04_steel", 8, [-132, 96, 738], [-120, 90, 748], [-100, 72, 770], fov=(65, 60)),
        move("p05_skip", 6, [-80, 80, 782], [-84, 84, 772], [-101, 74, 778], [-101, 79, 769]),     # the gallery in profile
        move("p06_cast_house", 5, [-94, 70, 752], [-98, 69, 758], [-101, 65, 763], fov=(55, 50)),
        # 4. power: thermal plant towers, the gorge dam, the wind farm on the ridge, the tidal lagoon
        move("p07_tpp", 7, [-40, 100, 660], [-60, 95, 670], [-76, 100, 702]),
        move("p08_hydro", 9, [1093, 118, 752], [1093, 108, 725], [1093, 85, 664], [1093, 80, 670], fov=(65, 60)),
        move("p09_wind", 7, [-60, 128, 960], [-80, 130, 940], [-150, 118, 960]),
        move("p10_tidal", 5, [20, 75, 685], [-5, 75, 690], [20, 63, 705], time=NOON),
        # 5. era I2 oil: the rig drilling, then the refinery strip from the road
        move("p11_rig", 6, [-340, 76, 790], [-345, 72, 795], [-354, 75, 811]),
        move("p12_refinery", 10, [-370, 75, 798], [-440, 75, 798], [-380, 72, 814], [-445, 72, 814], ease="linear"),
        # 6. era I3 chemistry: furnaces, cold end, spheres
        move("p13_chem", 7, [-222, 88, 706], [-210, 84, 712], [-185, 70, 735]),
        # 7. era I4 nuclear (offset -152, +129, +426): containment and turbine hall, the hall inside, the core,
        #    the control room with the CC: Tweaked alarm screen
        move("p14_npp", 8, [-150, 104, 566], [-130, 110, 580], [-99, 80, 610], fov=(65, 60)),
        move("p15_turbine_hall", 6, [-122, 76, 592], [-90, 76, 592], [-110, 72, 592], [-78, 72, 592], ease="linear"),
        move("p16_reactor", 6, [-105, 80, 612], [-97, 82, 610], [-97, 70, 621], fov=(60, 55)),
        move("p17_control_room", 6, [-75, 71, 615], [-75, 70.5, 617], [-75, 70, 621], fov=(55, 50)),
        # 8. integrations: Create railway between the works
        move("p18_rail", 6, [-200, 70, 774], [-160, 70, 774], [-130, 68, 778], ease="linear"),
        # 9. when it goes wrong: the bit hits the gas cap, the kick becomes a blowout (alarm in chat); drilling
        #    resumes afterwards (`film prime drill` spuds a fresh well)
        move("p19_blowout", 14, [-338, 74, 794], [-346, 76, 800], [-355, 72, 811], [-355, 78, 810],
             setup=[f"film prime kick {RIG[0]} {RIG[1]} {RIG[2]}"], settle=40),
        # 10. the plant at dusk, pulling back west
        move("p20_outro", 12, [-60, 140, 700], [-300, 170, 620], [-200, 70, 760], time=DUSK, fov=(65, 70),
             setup=[f"film prime drill {RIG[0]} {RIG[1]} {RIG[2]}"]),
    ]


def main():
    out, args = sys.argv[1], sys.argv[2:]
    stills = "--stills" in args
    names = [a for a in args if not a.startswith("--")]
    plan = shots()
    if names:
        plan = [s for s in plan if s["name"] in names]
    for s in plan:
        if stills:
            s["stills"] = True
    total = sum(s["duration"] for s in plan)
    doc = {"ffmpeg": shutil.which("ffmpeg") or "ffmpeg", "out": "film_plant", "quit": True, "fps": 60, "shots": plan}
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(doc, fh, indent=1)
    print(f"{out}: {len(plan)} shots, {total:.0f} s")


if __name__ == "__main__":
    main()
