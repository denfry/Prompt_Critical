"""Reactor gallery for the video "Why reactors get more dangerous the more power they make": writes film.json.

    python art/film/shots_reactors.py pc-mod/run-film/film.json [--stills] [shot ...]

The gallery stands on a fresh superflat (world "Reactors", made from the level.dat of Film-rs, floor y = -60) in the
order of the video's ladder of power, west to east along z = 0: the graphite pile, the TRIGA pool, AM-1, VVER-70 with
its loops, BWR-240, CANDU NPD, CANDU 6, BOR-60 with its loop, BN-600 and VVER-1000 with four steam generators. The
first shot builds it with commands (forceload, then fill/setblock: whole-model units lay out their own cells), so a
fresh world needs no datapack. Units stand cold: the shots show the machines, the charts show the physics
(`./gradlew :pc-sim:videoData`).
"""
import json
import math
import shutil
import sys

WIDE = [1920, 1080]
MORNING, NOON, DUSK = 1500, 6000, 12300
Y = -60                                   # the gallery floor (grass at -61)
Z0 = -300                                 # the line runs along z = Z0, away from the filmset I0 set near spawn
PC = "promptcritical:"

# unit, x, footprint half-width in x, half-depth in z, height (blocks): for the pads and the camera paths
UNITS = {
    "pile": (0, 4, 4, 8),
    "triga": (30, 3, 3, 7),
    "am1": (60, 3, 3, 10),
    "vver70": (100, 9, 9, 19),
    "bwr240": (135, 3, 3, 14),
    "npd": (165, 4, 4, 7),
    "candu6": (200, 5, 9, 13),
    "bor60": (240, 11, 6, 10),
    "bn600": (285, 8, 8, 19),
    "vver1000": (340, 13, 19, 20),
}


def build():
    """Commands that put up the gallery. Whole-model units go down by setblock like any block and lay out their
    cells themselves (RealScaleGameTests.wholeModelCellsFollowTheModel)."""
    c = ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear",
         "gamerule doMobSpawning false", "kill @e[type=!player]",
         f"forceload add -40 {Z0 - 48} 380 {Z0 + 48}"]
    # concrete pads under every unit, a road along the line
    c.append(f"fill -30 {Y - 1} {Z0 + 6} 375 {Y - 1} {Z0 + 8} minecraft:gray_concrete")
    for name, (x, hx, hz, _) in UNITS.items():
        c.append(f"fill {x - hx - 2} {Y - 1} {Z0 - hz - 2} {x + hx + 2} {Y - 1} {Z0 + hz + 2} minecraft:light_gray_concrete")

    # 1. graphite pile, as ReactorGameTests.buildPile lays it: graphite, a fuel core, rod channels, drives on top
    x0, z0, n = -3, Z0 - 3, 7
    c.append(f"fill {x0} {Y} {z0} {x0 + n - 1} {Y + n - 1} {z0 + n - 1} {PC}moderator_block_graphite")
    c.append(f"fill {x0 + 1} {Y + 1} {z0 + 1} {x0 + 4} {Y + 4} {z0 + 4} {PC}fuel_channel")
    for rx in (x0 + 2, x0 + 4):
        c.append(f"fill {rx} {Y} {z0 + 3} {rx} {Y + n - 1} {z0 + 3} {PC}control_rod_channel")
    c.append(f"setblock {x0 + 2} {Y + n} {z0 + 3} {PC}zip_rod_winch")
    c.append(f"setblock {x0 + 4} {Y + n} {z0 + 3} {PC}control_rod_drive")
    c.append(f"setblock {x0 + n} {Y + 2} {z0 + 3} {PC}neutron_detector[facing=east]")
    c.append(f"setblock {x0 + n + 2} {Y} {z0 + 3} {PC}reactor_controller[facing=west]")

    # 2. TRIGA: an open concrete tank, five blocks of water lit from the floor, the bridge drive over the centre
    #    (TrigaGameTests.pool: water under the drive for POOL_DEPTH = 5 blocks)
    tx, z = UNITS["triga"][0], Z0
    c.append(f"fill {tx - 3} {Y - 1} {z - 3} {tx + 3} {Y + 4} {z + 3} minecraft:white_concrete")
    c.append(f"fill {tx - 2} {Y - 1} {z - 2} {tx + 2} {Y - 1} {z + 2} minecraft:light_blue_concrete")
    c.append(f"fill {tx - 1} {Y - 1} {z - 1} {tx + 1} {Y - 1} {z + 1} minecraft:sea_lantern")
    c.append(f"fill {tx - 2} {Y} {z - 2} {tx + 2} {Y + 4} {z + 2} minecraft:water")
    c.append(f"fill {tx - 3} {Y + 5} {z} {tx + 3} {Y + 5} {z} minecraft:iron_block")
    c.append(f"setblock {tx} {Y + 5} {z} {PC}transient_rod_drive")

    def unit(block, x, z=0, facing="north", y=Y):
        c.append(f"setblock {x} {y} {Z0 + z} {PC}{block}[facing={facing}]")

    unit("am1_reactor", UNITS["am1"][0])
    # VVER-70: three loops north (steam generators) and south (pumps), the pressurizer east
    vx = UNITS["vver70"][0]
    unit("vver70_reactor_real", vx)
    for dx in (-5, 0, 5):
        unit("vver70_steam_generator_real", vx + dx, -7)
        unit("vver70_coolant_pump_real", vx + dx, 6)
    unit("vver70_pressurizer_real", vx + 7, 0)
    unit("bwr240_reactor", UNITS["bwr240"][0])
    unit("candu_npd_reactor_real", UNITS["npd"][0])
    unit("candu6_reactor", UNITS["candu6"][0])
    # BOR-60 with its intermediate heat exchanger, primary pump and steam generator
    bx = UNITS["bor60"][0]
    unit("bor60_reactor_real", bx)
    unit("bor60_ihx_real", bx + 5, -4)
    unit("bor60_primary_pump_real", bx + 5, 4)
    unit("bor60_steam_generator_real", bx + 10, 0)
    unit("bn600_reactor", UNITS["bn600"][0])
    # VVER-1000: four steam generators and four pumps, two loops a side, the pressurizer east
    wx = UNITS["vver1000"][0]
    unit("vver_reactor", wx)
    for z in (-10, -16, 10, 16):
        unit("vver_steam_generator", wx, z)
        unit("vver_coolant_pump", wx - 10, z)
    unit("vver_pressurizer", wx + 8, 0)
    return c


def key(t, pos, look, fov=60):
    return {"t": t, "pos": [round(v, 2) for v in pos], "look": [round(v, 2) for v in look], "fov": fov, "roll": 0}


def shot(name, dur, keys, time=NOON, ease="inOut", **kw):
    s = {"name": name, "duration": dur, "size": WIDE, "scale": WIDE, "time": time, "ease": ease, "gamma": 0.6,
         "mode": "spectator", "keys": keys}
    s.update(kw)
    return s


def move(name, dur, a, b, look_a, look_b=None, fov=(60, 60), **kw):
    """A straight move; z of every point is given relative to the line (Z0 is added here)."""
    a, b, look_a = [a[0], a[1], a[2] + Z0], [b[0], b[1], b[2] + Z0], [look_a[0], look_a[1], look_a[2] + Z0]
    look_b = [look_b[0], look_b[1], look_b[2] + Z0] if look_b else None
    return shot(name, dur, [key(0, a, look_a, fov[0]), key(dur, b, look_b or look_a, fov[1])], **kw)


def orbit(name, dur, unit, radius, height, a0, a1, look_dy=None, fov=55, n=6, **kw):
    """A slow arc around a unit: angles in degrees, 0 = south of it (the side the road runs on)."""
    x, hx, hz, h = UNITS[unit]
    look = [x, Y + (look_dy if look_dy is not None else h * 0.45), Z0]
    keys = []
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        keys.append(key(dur * i / n, [x + radius * math.sin(a), Y + height, Z0 + radius * math.cos(a)], look, fov))
    return shot(name, dur, keys, ease="linear", **kw)


def shots():
    end = UNITS["vver1000"][0]
    return [
        # build the gallery and let the chunks and the whole-model cells settle before the first take
        {"name": "build", "duration": 0.1, "fps": 10, "stills": True, "size": [640, 360], "scale": [640, 360],
         "time": NOON, "mode": "spectator", "settle": 200, "setup": build(),
         "keys": [key(0, [150, 40, Z0 + 90], [150, Y, Z0])]},
        # hook: the whole ladder from the pile to VVER-1000, low and sideways, then the same line from above
        move("r01_line", 16, [-25, Y + 6, 30], [end + 10, Y + 14, 45], [10, Y + 3, 0], [end + 5, Y + 10, 0],
             fov=(55, 60), ease="linear"),
        move("r02_line_high", 10, [-40, Y + 60, 70], [end - 20, Y + 70, 90], [60, Y, 0], [end, Y, 0], fov=(60, 65)),
        # rung 1: the pile, the size of a room, in silence
        orbit("r10_pile", 10, "pile", 11, 3, -40, 40, look_dy=3, fov=50),
        move("r11_pile_drives", 7, [-1, Y + 9.5, 6], [3, Y + 9.5, 6], [1, Y + 7, 0], fov=(45, 45), ease="linear"),
        # rung 2: TRIGA, looking down into the pool past the drive
        move("r20_triga", 8, [30, Y + 12, 9], [30, Y + 9, 4], [30, Y, 0], [30, Y - 1, 0], fov=(55, 50)),
        orbit("r21_triga_bridge", 9, "triga", 8, 7, 30, -30, look_dy=5, fov=50),
        # rung 3: AM-1, the world's first grid reactor, then its shield top from above
        orbit("r30_am1", 10, "am1", 14, 4, -35, 35, fov=55),
        move("r31_am1_top", 8, [56, Y + 16, 8], [64, Y + 16, 8], [60, Y + 8, 0], fov=(55, 55), ease="linear"),
        # rung 4 (xenon): AM-1 again, closer and slower, under the charts
        move("r40_am1_close", 12, [66, Y + 5, 9], [54, Y + 6, 9], [60, Y + 5, 0], fov=(45, 45), ease="linear"),
        # rung 5: VVER-70 with its loops, BWR-240, then VVER-1000 towering over its steam generators
        orbit("r50_vver70", 12, "vver70", 24, 10, -50, 50, fov=55),
        orbit("r51_bwr240", 9, "bwr240", 13, 6, 30, -30, fov=55),
        orbit("r52_vver1000", 14, "vver1000", 36, 14, -60, 20, look_dy=8, fov=55),
        move("r53_vver1000_up", 9, [348, Y + 2, 7], [348, Y + 3, 7], [340, Y + 4, 0], [340, Y + 20, 0],
             fov=(65, 65)),
        # rung 6: CANDU NPD and CANDU 6, the calandria end on
        orbit("r60_npd", 9, "npd", 13, 5, -30, 30, fov=55),
        orbit("r61_candu6", 12, "candu6", 22, 8, 40, -40, fov=55),
        # rung 7: BOR-60 with its sodium loop, BN-600 in its tank
        orbit("r70_bor60", 11, "bor60", 20, 8, -40, 40, fov=55),
        orbit("r71_bn600", 12, "bn600", 28, 14, 40, -30, look_dy=8, fov=55),
        # ending: the line at dusk, pulling back from VVER-1000 to the pile
        move("r90_line_dusk", 18, [end + 20, Y + 20, 50], [-30, Y + 40, 80], [end - 20, Y + 6, 0], [40, Y + 4, 0],
             time=DUSK, fov=(55, 65), ease="linear"),
    ]


def main():
    out, args = sys.argv[1], sys.argv[2:]
    names = [a for a in args if not a.startswith("--")]
    plan = shots()
    if names:
        plan = [s for s in plan if s["name"] in names or s["name"] == "build"]
    if "--stills" in args:
        for s in plan:
            s["stills"] = True
    total = sum(s["duration"] for s in plan)
    doc = {"ffmpeg": shutil.which("ffmpeg") or "ffmpeg", "out": "film/reactors", "quit": True, "fps": 60,
           "shots": plan}
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(doc, fh, indent=1)
    print(f"{out}: {len(plan)} shots, {total:.0f} s")


if __name__ == "__main__":
    main()
