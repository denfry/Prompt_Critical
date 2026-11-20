"""Shot list for the plant overview video in the Plant world (seed 62): writes film.json for FilmRecorder.

    python art/film/shots_plant.py pc-mod/run-film/film.json [--shorts] [--start] [--stills] [shot ...]

`--shorts` writes the vertical shots of the two shorts instead (out: film/shorts); `--start` prepends a warm shot
that runs plant_start, so a fresh client start needs no manual command.

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
TALL = [1080, 1920]
MORNING, NOON, DUSK = 1500, 6000, 12300
RIG = (-355, 67, 805)                    # driller's console of the oil set


def key(t, pos, look, fov=60):
    return {"t": t, "pos": pos, "look": look, "fov": fov, "roll": 0}


def move(name, dur, a, b, look_a, look_b=None, fov=(60, 60), ease="inOut", time=MORNING, size=WIDE, **kw):
    s = {"name": name, "duration": dur, "size": size, "scale": size, "time": time, "ease": ease, "gamma": 0.6,
         "mode": "spectator", "keys": [key(0, a, look_a, fov[0]), key(dur, b, look_b or look_a, fov[1])]}
    s.update(kw)
    return s


def tall(name, dur, a, b, look_a, look_b=None, fov=(75, 75), **kw):
    """A vertical 1080x1920 shot. Minecraft's FOV is vertical, so a tall frame needs a wider one to keep the width."""
    return move(name, dur, a, b, look_a, look_b, fov=fov, size=TALL, **kw)


def warm():
    """Starts every unit after a restart (plant_start.txt waits 5100 server ticks; sprinted) before the first shot."""
    return {"name": "warm", "duration": 0.1, "fps": 10, "stills": True, "size": [640, 360], "scale": [640, 360],
            "time": MORNING, "mode": "spectator", "settle": 900,
            "setup": ["film run plant_start", "tick sprint 6000"],
            "keys": [key(0, [-200, 120, 760], [-200, 70, 780])]}


def shorts():
    """Two vertical shorts: sa_* the plant from the mine to the reactor, sb_* the rig drilling into a gas cap."""
    cx, cy, cz = RIG
    return [
        # A. coal to atom: one shot per era, each a little longer than the cut takes
        tall("sa01_aerial", 6, [-330, 185, 690], [-280, 150, 735], [-260, 70, 790], [-240, 70, 800], fov=(80, 75)),
        tall("sa02_mine", 4, [-402, 90, 880], [-396, 96, 884], [-388, 85, 902], [-388, 95, 902]),
        tall("sa03_boilers", 4, [-498.5, 67.4, 874.0], [-490.5, 67.4, 874.0], [-495, 66.8, 878], [-487, 66.8, 878],
             ease="linear"),
        tall("sa04_furnace", 5, [-82, 76, 786], [-84, 84, 776], [-101, 74, 778], [-101, 86, 769]),
        tall("sa05_rig", 5, [-338, 71, 796], [-340, 74, 798], [-350, 72, 810], [-350, 80, 810],
             setup=[f"film prime drill {cx} {cy} {cz}"]),
        tall("sa06_refinery", 5, [-370, 75, 798], [-410, 75, 798], [-380, 72, 814], [-415, 72, 814], ease="linear"),
        tall("sa07_chem", 4, [-222, 88, 706], [-212, 84, 712], [-185, 70, 735]),
        tall("sa08_npp", 5, [-140, 96, 576], [-128, 104, 586], [-99, 80, 610], [-99, 90, 610]),
        tall("sa09_reactor", 4, [-105, 80, 612], [-99, 82, 611], [-97, 70, 621], fov=(70, 65)),
        tall("sa10_dusk", 7, [-80, 120, 690], [-180, 160, 640], [-150, 70, 760], [-220, 70, 780], time=DUSK),
        # B. the blowout: steady drilling, the console as the bit enters the gas cap and the pits gain, the wellhead
        tall("sb01_derrick", 6, [-344, 70, 798], [-346, 73, 799], [-350, 70, 808], [-350, 80, 810], fov=(80, 80),
             setup=[f"film prime drill {cx} {cy} {cz}"]),
        tall("sb02_rotary", 4, [-347, 69.5, 806], [-348, 69.5, 808], [-350, 68, 810], fov=(65, 60)),
        tall("sb03_console", 12, [cx, cy + 1.6, cz - 0.6], [cx, cy + 1.6, cz - 0.2], [cx, cy + 0.6, cz + 2],
             # the kick grows into a blowout ~4.5 s after the spud, so record almost at once; the feed loop would
             # respud the well (film prime drill), so it is stopped until sb05
             mode="creative", guiScale=3, blur=6, settle=20,
             setup=["film feed stop", f"film prime kick {cx} {cy} {cz}"],
             events=[{"t": 0.15, "panel": [cx, cy, cz]}]),
        tall("sb04_blowout", 14, [-343, 70, 801], [-345.5, 72, 803.5], [-350, 69, 810], [-350, 74, 810], fov=(70, 70),
             setup=[f"film prime kick {cx} {cy} {cz}"], settle=100),
        tall("sb05_after", 6, [-330, 80, 790], [-320, 95, 780], [-355, 76, 810], [-355, 80, 810], time=DUSK,
             setup=[f"film prime drill {cx} {cy} {cz}", "film feed plant_feed 30"]),
    ]


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
    tall_shots = "--shorts" in args
    names = [a for a in args if not a.startswith("--")]
    plan = shorts() if tall_shots else shots()
    if names:
        plan = [s for s in plan if s["name"] in names]
    for s in plan:
        if stills:
            s["stills"] = True
    if "--start" in args:
        plan.insert(0, warm())
    total = sum(s["duration"] for s in plan)
    out_dir = "film/shorts" if tall_shots else "film_plant"
    doc = {"ffmpeg": shutil.which("ffmpeg") or "ffmpeg", "out": out_dir, "quit": True, "fps": 60, "shots": plan}
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(doc, fh, indent=1)
    print(f"{out}: {len(plan)} shots, {total:.0f} s")


if __name__ == "__main__":
    main()
