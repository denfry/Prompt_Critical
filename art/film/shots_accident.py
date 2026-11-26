"""The accident short: one VVER-70 built as a plant in the Reactors world, its loss-of-coolant accident in two runs.

    python art/film/shots_accident.py <world dir> functions       write the datapack functions (build, open, reset)
    python art/film/shots_accident.py pc-mod/run-film/film.json [--stills] [--run closed|open] [shot ...]

The plant replaces the reactor gallery of shots_reactors.py on the superflat (floor y -60, grass at -61): the VVER-70
(Rheinsberg) hangs by its nozzle ring in a concrete shaft over a dry cavity, its three loops stand on the operating
slab and are piped nozzle to nozzle as the models draw their flanges (art/blockbench/core/vver70/*.author.js):
hot leg -> steam generator in (north face of the SG) -> SG out -> pump suction -> pump discharge -> cold leg. The
pressurizer's surge line joins the hot leg of loop 3. Two ECCS pumps stand without power. A corridor under the slab
looks into the cavity, where the melt falls when the head fails.

Run "closed": the hall is a closed shell of containment wall (about 4500 m3 of gas), the accident stays inside.
Run "open": the roof is taken off (Rheinsberg had no full containment), the release goes out with the wind and leaves
a fallout trace (04b 4b.9). Both start the same way: `film prime melt_ready` and `film prime loca` (LOCA with no
station power: pumps, ECCS and heat sink are gone), then `tick sprint` through the hours the melt takes.
"""
import json
import os
import shutil
import sys

TALL = [1080, 1920]
MORNING, NOON, DUSK = 1500, 6000, 12300
PC = "promptcritical:"
R = (100, -54, -300)                  # reactor controller: nozzles at y -47, bottom of the vessel at -54
SLAB = -48                            # operating floor; the loops stand on it at y -47
HALL = (85, -61, -315, 115, -38, -289)  # containment shell, inclusive; its roof is at -38
TOWER = (96, -38, -304, 104, -34, -296)  # the vessel head stands up to -36: a tower of shell over the reactor
# concrete compartments on the slab (y -47..-39) where nothing stands, as the boxes round a VVER's steam generators
# and pumps: they take the free volume from about 8000 m3 down to about 4500 m3. South of the reactor stays open for
# the camera. (x1, z1, x2, z2) inclusive, from the floor map of the loops.
BOXES = [(86, -314, 92, -310), (93, -314, 101, -313), (102, -314, 114, -309), (86, -309, 90, -301),
         (86, -300, 87, -295), (108, -307, 110, -301), (101, -297, 106, -290), (109, -297, 114, -290),
         (86, -290, 100, -290)]
PIPE = "create:fluid_pipe"


def clear():
    """Take the reactor gallery away (x -48..383, z -352..-249, up to y -22: the tallest unit topped out at -40) and give the
    superflat its floor back."""
    c = ["kill @e[type=!player]"]
    for x0 in range(-48, 384, 32):
        for z0 in range(-352, -248, 26):
            c.append(f"fill {x0} -60 {z0} {x0 + 31} -22 {z0 + 25} minecraft:air")   # 32 x 39 x 26 < 32768
            c.append(f"fill {x0} -61 {z0} {x0 + 31} -61 {z0 + 25} minecraft:grass_block")
            c.append(f"fill {x0} -63 {z0} {x0 + 31} -62 {z0 + 25} minecraft:dirt")
    c.append("kill @e[type=item]")
    return c


def pipe_line(c, a, b):
    """Straight pipe from a to b inclusive (one axis changes); Create connects neighbouring pipes and ports itself."""
    (x1, y1, z1), (x2, y2, z2) = a, b
    assert sum(v1 != v2 for v1, v2 in zip(a, b)) <= 1, (a, b)
    c.append(f"fill {min(x1, x2)} {min(y1, y2)} {min(z1, z2)} {max(x1, x2)} {max(y1, y2)} {max(z1, z2)} {PIPE}")


def build():
    x1, y1, z1, x2, y2, z2 = HALL
    rx, ry, rz = R
    c = ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear", "time set 6000",
         f"forceload add {x1 - 48} {z1 - 48} {x2 + 48} {z2 + 48}"]
    # the shell (hollow: its inside becomes air), then the basement under the slab, solid concrete
    c.append("kill @e[type=minecraft:falling_block]")
    # the melt of the last take may have eaten down through the basemat into the ground: give it back
    c.append(f"fill {rx - 4} -63 {rz - 4} {rx + 4} -62 {rz + 4} minecraft:dirt")
    c.append(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {PC}containment_wall hollow")
    c.append(f"fill {x1 + 1} {y1 + 1} {z1 + 1} {x2 - 1} {SLAB} {z2 - 1} minecraft:gray_concrete")
    tx1, ty1, tz1, tx2, ty2, tz2 = TOWER
    c.append(f"fill {tx1} {ty1} {tz1} {tx2} {ty2} {tz2} {PC}containment_wall hollow")
    c.append(f"fill {tx1 + 1} {ty1} {tz1 + 1} {tx2 - 1} {ty1} {tz2 - 1} minecraft:air")
    for bx1, bz1, bx2, bz2 in BOXES:
        c.append(f"fill {bx1} {SLAB + 1} {bz1} {bx2} {y2 - 1} {bz2} minecraft:gray_concrete")
    # the reactor shaft: 7 x 7 around the 5 x 5 vessel, from the cavity floor up through the slab
    # (the shell under it stays: the basemat is the containment's floor, the cavity is lined on top of it)
    c.append(f"fill {rx - 3} {y1 + 2} {rz - 3} {rx + 3} {SLAB} {rz + 3} minecraft:air")
    c.append(f"fill {rx - 3} {y1 + 1} {rz - 3} {rx + 3} {y1 + 1} {rz + 3} minecraft:smooth_stone")     # cavity floor
    # inspection corridor from the south into the cavity, for the camera
    c.append(f"fill {rx - 1} {y1 + 2} {rz + 4} {rx + 1} {y1 + 5} {z2 - 2} minecraft:air")
    # the reactor and its three loops
    c.append(f"setblock {rx} {ry} {rz} {PC}vver70_reactor_real[facing=north]")
    y = SLAB + 1
    loops = [  # (SG pos, SG facing, pump pos, pump facing)
        ((100, y, -310), "south", (94, y, -308), "north"),
        ((90, y, -299), "east", (88, y, -293), "north"),
        ((110, y, -299), "west", (112, y, -306), "south"),
    ]
    for sg, sf, pu, pf in loops:
        c.append(f"setblock {sg[0]} {sg[1]} {sg[2]} {PC}vver70_steam_generator_real[facing={sf}]")
        c.append(f"setblock {pu[0]} {pu[1]} {pu[2]} {PC}vver70_coolant_pump_real[facing={pf}]")
    c.append(f"setblock 106 {y} -304 {PC}vver70_pressurizer_real[facing=north]")
    # loop 1: hot leg north to SG1, SG1 out north and round to pump 1, pump 1 down to the cold nozzle (west)
    pipe_line(c, (100, y, -303), (100, y, -308))
    pipe_line(c, (100, y, -312), (94, y, -312))
    pipe_line(c, (94, y, -311), (94, y, -310))
    pipe_line(c, (94, y + 1, -306), (94, y + 1, -301))
    pipe_line(c, (95, y + 1, -301), (97, y + 1, -301))
    pipe_line(c, (97, y, -301), (97, y, -301))
    # loop 2: hot leg west to SG2, SG2 out west and south to pump 2, pump 2 east and north to the cold nozzle (south)
    pipe_line(c, (97, y, -299), (92, y, -299))
    pipe_line(c, (88, y, -299), (88, y, -295))
    pipe_line(c, (88, y + 1, -291), (100, y + 1, -291))
    pipe_line(c, (100, y + 1, -292), (100, y + 1, -297))
    pipe_line(c, (100, y, -297), (100, y, -297))
    # loop 3: hot leg east to SG3, SG3 out east and north to pump 3, pump 3 north, west and south to the cold nozzle
    pipe_line(c, (103, y, -299), (108, y, -299))
    pipe_line(c, (112, y, -299), (112, y, -304))
    pipe_line(c, (112, y + 1, -308), (103, y + 1, -308))
    pipe_line(c, (103, y + 1, -307), (103, y + 1, -301))
    pipe_line(c, (103, y, -301), (103, y, -301))
    # pressurizer surge line into the hot leg of loop 3
    pipe_line(c, (106, y, -302), (106, y, -300))
    # ECCS: two pumps on the slab with no power to them
    c.append(f"setblock 108 {y} -292 {PC}eccs_pump[facing=north]")
    c.append(f"setblock 92 {y} -306 {PC}eccs_pump[facing=north]")
    # light: invisible light blocks under the roof and in the cavity
    # (only into air: a light block must not replace a cell of a machine model)
    for ly in (y2 - 1, SLAB + 4):
        for lx in range(x1 + 3, x2 - 1, 5):
            for lz in range(z1 + 3, z2 - 1, 5):
                c.append(f"execute if block {lx} {ly} {lz} minecraft:air run setblock {lx} {ly} {lz} minecraft:light[level=15]")
    for lx, lz in ((TOWER[0] + 1, TOWER[2] + 1), (TOWER[3] - 1, TOWER[5] - 1)):
        c.append(f"execute if block {lx} {TOWER[4] - 1} {lz} minecraft:air run setblock {lx} {TOWER[4] - 1} {lz} minecraft:light[level=15]")
    for lx, lz in ((rx - 3 + 1, rz - 2), (rx + 2, rz + 2), (rx, rz + 6)):
        c.append(f"execute if block {lx} {y1 + 4} {lz} minecraft:air run setblock {lx} {y1 + 4} {lz} minecraft:light[level=12]")
    c.append("kill @e[type=item]")
    return c


def open_roof():
    """Run 2: no roof over the hall, as at Rheinsberg there was no full containment."""
    x1, y1, z1, x2, y2, z2 = HALL
    tx1, ty1, tz1, tx2, ty2, tz2 = TOWER
    # only the shell goes: the vessel head stands up through the roof into the tower
    wall = f"minecraft:air replace {PC}containment_wall"
    return [f"fill {x1} {y2} {z1} {x2} {y2} {z2} {wall}", f"fill {tx1} {ty1} {tz1} {tx2} {ty2} {tz2} {wall}"]


def functions(world):
    out = os.path.join(world, "datapacks", "filmset", "data", "filmset", "function")
    os.makedirs(out, exist_ok=True)
    for name, cmds in (("accident_clear", clear()), ("accident_build", build()), ("accident_open", open_roof())):
        with open(os.path.join(out, name + ".mcfunction"), "w", encoding="utf-8") as fh:
            fh.write("\n".join(cmds) + "\n")
        print(f"{name}: {len(cmds)} commands")


# ---- shots ---------------------------------------------------------------------------------------------------------
# Every take rebuilds the plant (the last take melted it) and brings the core to power; the hot leg tears 0.5 s into
# the take and the take runs on through the accident, recording at 1:1 game time. The edit cuts the moments out of the
# takes (settling the world before a take does not run the accident by a known number of ticks). Measured after the
# break: claddings burst at about 1 s, the head fails and the hydrogen burns at 12..22 s (the takes differ), the melt
# pours for 12 s more.
RX, RY, RZ = R


def key(t, pos, look, fov=70):
    return {"t": t, "pos": [round(v, 2) for v in pos], "look": [round(v, 2) for v in look], "fov": fov, "roll": 0}


def take(name, dur, keys, run="closed", brk=True, time=NOON, ease="inOut"):
    setup = ["function filmset:accident_build"]
    if run == "open":
        setup.append("function filmset:accident_open")
    setup += [f"film prime melt_ready {RX} {RY} {RZ}", "kill @e[type=minecraft:item]"]
    ev = [{"t": 0.5, "cmd": f"film prime loca {RX} {RY} {RZ}"}] if brk else []
    # concrete the burn knocks off lies about as items: not in the picture
    ev += [{"t": t, "cmd": "kill @e[type=minecraft:item]"} for t in range(1, int(dur) + 1)]
    return {"name": name, "duration": dur, "size": TALL, "scale": TALL, "time": time, "ease": ease, "gamma": 0.6,
            "mode": "spectator", "settle": 60, "setup": setup, "keys": keys, "events": sorted(ev, key=lambda e: e["t"])}


def hold(dur, t0, a, b, look_a, look_b=None, fov=(70, 70)):
    """Keys that stand at a until t0, then move to b by the end."""
    return [key(0, a, look_a, fov[0]), key(t0, a, look_a, fov[0]), key(dur, b, look_b or look_a, fov[1])]


def shots():
    c = [RX + 0.5, RY + 8, RZ + 0.5]            # the vessel at the nozzle ring
    return [
        # the plant as it runs: three loops round the vessel in the hall
        take("a01_hall", 6, [key(0, [93, -40, -291.5], c, 75), key(6, [95.5, -41, -293.5], c, 70)], brk=False),
        # the hot leg tears off its nozzle: the jet
        take("a02_break", 6, [key(0, [103.5, -44, -307.5], [100.5, -46.5, -304], 65),
                              key(6, [103, -44.5, -307], [100.5, -46.5, -304.5], 60)]),
        # the hall fills with steam, the core is bared
        take("a03_steam", 10, [key(0, [96, -42, -293], c, 70), key(10, [93.5, -40, -291.5], c, 75)]),
        # under the vessel: the head fails and the melt pours into the dry cavity
        take("a04_pour", 34, hold(34, 24, [100.5, -57, -296.6], [100.5, -57.2, -295.4], [100.5, -56.5, -300],
                                  [100.5, -58, -300], (80, 75))),
        # the hydrogen burns in the hall
        take("a05_burn", 30, hold(30, 24, [91.5, -40, -291.5], [92.2, -40.4, -292.2], c, None, (80, 80))),
        # the corium on the cavity floor eats into the concrete
        take("a06_corium", 44, hold(44, 34, [101.5, -58, -295], [99.5, -58.3, -296], [100, -59.5, -300], None, (60, 55))),
        # outside: the shell held, nothing comes out
        take("a07_held", 36, hold(36, 26, [130, -30, -270], [124, -32, -266], [100, -45, -302], None, (60, 60))),
        # run 2: no roof. Steam and smoke stand over the open hall, then the release leaves with the wind
        take("a10_open", 34, [key(0, [100.5, -10, -270], [100.5, -45, -300], 70),
                              key(34, [100.5, -14, -280], [100.5, -45, -302], 70)], run="open"),
        take("a11_plume", 44, hold(44, 30, [60, -15, -250], [55, -5, -245], [100, -40, -300], [110, -40, -300], (70, 75)),
             run="open"),
        # teaser: dusk over the open hall, the camera rises away
        take("a90_teaser", 44, hold(44, 34, [100.5, -30, -285], [100.5, 10, -250], [100.5, -45, -300], None, (70, 75)),
             run="open", time=DUSK),
    ]


def main():
    if len(sys.argv) > 2 and sys.argv[2] == "functions":
        functions(sys.argv[1])
        return
    out, args = sys.argv[1], sys.argv[2:]
    names = [a for a in args if not a.startswith("--")]
    plan = shots()
    if names:
        plan = [s for s in plan if s["name"] in names]
    if "--stills" in args:
        for s in plan:
            s["stills"] = True
            s["fps"] = 2         # a storyboard: two frames a second are enough to judge the framing
    total = sum(s["duration"] for s in plan)
    doc = {"ffmpeg": shutil.which("ffmpeg") or "ffmpeg", "out": "film/accident", "quit": True, "fps": 60, "shots": plan}
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(doc, fh, indent=1)
    print(f"{out}: {len(plan)} shots, {total:.0f} s")


if __name__ == "__main__":
    main()
