"""The working plant for the finale of the overview film: process units that really run.

Each unit keeps the exact relative layout of its game test, so the machines find each other (turbine exhaust into
the HRSG, HRSG into the boiler, furnace onto its separation unit, rig parts around the console, centrifuges within
the cascade radius). The dev command `/film prime <scene> <pos>` (pc-mod/src/gametest/.../film/FilmPlant.java)
loads fuel, gas and feed the way the tests do. Stands south of the later-era set: x -10..140, z 248..300.
"""
from filmset import G, PC

X1, Z1, X2, Z2 = -10, 246, 140, 300

REACTOR = (10, -59, 268)
GENERATORS = ((26, -60, 264), (30, -60, 264))
GT, HRSG, CC_BOILER = (45, -59, 259), (45, -59, 268), (42, -59, 265)
FURNACES = ((63, -59, 263), (75, -59, 263))
RIG = (90, 256)          # gametest origin of DrillingGameTests.buildRig
CONSOLE = (RIG[0] + 1, -60, RIG[1] + 1)
CASCADE = (116, -60, 262)


def motor(f, x, y, z, facing, rpm=64):
    f.setb(x, y, z, f"create:creative_motor[facing={facing}]{{ScrollValue:{rpm}}}")


def hall(f, x1, z1, x2, z2, top):
    f.fill(x1, G, z1, x2, G, z2, PC + "painted_concrete")
    for x in (x1, x2):
        f.fill(x, -60, z1, x, top, z2, PC + "corrugated_steel")
    f.fill(x1, -60, z2, x2, top, z2, PC + "corrugated_steel")
    f.fill(x1, -56, z2, x2, -55, z2, PC + "glass_block_frosted")
    f.fill(x1, top + 1, z1, x2, top + 1, z2, PC + "corrugated_steel")
    for x in range(x1 + 2, x2 - 1, 4):
        for z in range(z1 + 2, z2 - 1, 4):
            f.setb(x, top, z, PC + "industrial_light[lit=true]")
        f.setb(x, -55, z2 - 1, PC + "industrial_light[lit=true]")


def clear(f):
    f.fill(X1, -60, Z1, X2, -20, Z2, "air")
    f.fill(X1, -63, Z1, X2, -62, Z2, "dirt")
    f.fill(X1, G, Z1, X2, G, Z2, "andesite")
    f.fill(X1, G, Z1, X2, G, Z1 + 3, "gray_concrete")   # road along the front


def build(f):
    n = "[facing=north]"
    # reactor hall: VVER, two main coolant pumps, pressurizer (PwrReactorGameTests offsets from origin (4, 262))
    hall(f, 0, 258, 20, 278, -46)
    f.setb(*REACTOR, PC + "pwr_reactor" + n)
    for x in (5, 7):
        f.setb(x, -60, 263, PC + "primary_pump" + n)
        motor(f, x, -60, 264, "north")
    f.setb(14, -60, 264, PC + "pressurizer" + n)
    # generator bay
    f.fill(23, G, 260, 35, G, 268, PC + "painted_concrete")
    for x, y, z in GENERATORS:
        f.setb(x, y, z, PC + "shaft_generator" + n)
        f.setb(x, y + 1, z, PC + "generator_exciter" + n)
        motor(f, x, y, z + 1, "north")
        f.setb(x, y, z - 3, PC + "distribution_transformer" + n)
    # combined cycle: gas turbine -> HRSG -> fire-tube boiler (CombinedCycleGameTests offsets)
    f.fill(38, G, 256, 54, G, 274, "smooth_stone")
    f.setb(*GT, PC + "gas_turbine" + n)
    f.setb(GT[0], GT[1], GT[2] - 2, PC + "turbine_gearbox" + n)
    f.setb(*HRSG, PC + "hrsg" + n)
    f.fill(HRSG[0], -54, HRSG[2], HRSG[0], -40, HRSG[2], PC + "chimney")
    f.setb(*CC_BOILER, PC + "fire_tube_boiler" + n)
    f.fill(CC_BOILER[0], -57, CC_BOILER[2] + 4, CC_BOILER[0], -42, CC_BOILER[2] + 4, PC + "chimney")
    # pyrolysis: two furnaces on their cracked gas units (PyrolysisGameTests: separation 3 east of the furnace)
    f.fill(58, G, 256, 82, G, 272, "smooth_stone")
    for x, y, z in FURNACES:
        f.setb(x, y, z, PC + "pyrolysis_furnace" + n)
        f.setb(x + 3, y, z, PC + "cracked_gas_unit" + n)
        f.fill(x, -57, z + 3, x, -46, z + 3, PC + "chimney")
    # drilling rig exactly as DrillingGameTests.buildRig
    ox, oz = RIG
    f.fill(ox - 2, G, oz - 2, ox + 14, G, oz + 14, "coarse_dirt")

    def at(x, y, z):
        return ox + x, y - 62, oz + z
    f.setb(*at(1, 2, 1), PC + "drillers_console" + n)
    f.setb(*at(6, 2, 6), PC + "rotary_table" + n)
    motor(f, *at(5, 2, 6), "east")
    f.setb(*at(6, 5, 6), PC + "derrick_segment" + n)
    f.setb(*at(6, 2, 9), PC + "drawworks" + n)
    f.setb(*at(3, 2, 9), PC + "mud_pump" + n)
    motor(f, *at(1, 2, 10), "east")
    f.setb(*at(8, 2, 6), PC + "bop_annular" + n)
    f.setb(*at(8, 2, 3), PC + "choke_manifold" + n)
    for x in range(1, 7):
        f.setb(*at(x, 2, 4), PC + "mud_tank" + n)
    f.setb(*at(10, 2, 1), PC + "mud_mixing_hopper" + n)
    f.setb(*at(4, 2, 1), PC + "bop_accumulator" + n)
    # enrichment hall: cascade console and one section of 20 centrifuges (EnrichmentGameTests.placeHall)
    hall(f, 108, 254, 126, 270, -52)
    f.setb(*CASCADE, PC + "centrifuge_cascade" + n)
    motor(f, CASCADE[0], CASCADE[1], CASCADE[2] + 1, "north")
    placed = 0
    for x in range(CASCADE[0] - 5, CASCADE[0] + 5):
        for z in (CASCADE[2] - 5, CASCADE[2] - 4, CASCADE[2] - 3):
            if placed < 20:
                f.setb(x, -60, z, PC + "gas_centrifuge" + n)
                placed += 1
    # pipe rack along the back of the units: Create pipes on steel posts
    for x in range(-2, 132):
        f.setb(x, -55, 282, "create:fluid_pipe[east=true,west=true]")
        f.setb(x, -54, 282, "create:fluid_pipe[east=true,west=true]")
        if x % 6 == 0:
            f.fill(x, -60, 282, x, -56, 282, "iron_bars")
    for x in range(0, 132, 12):
        f.fill(x, -60, 290, x, -54, 290, "polished_blackstone_wall")
        f.setb(x, -53, 290, PC + "industrial_light_street[facing=north,lit=true]")


def prime(f):
    """Once, after the build: fuel, feed and water that the units keep between takes."""
    f("film prime reactor_fuel %d %d %d" % REACTOR)
    f("film prime boiler_water %d %d %d" % CC_BOILER)
    f("film prime turbine %d %d %d" % GT)
    for p in FURNACES:
        f("film prime pyrolysis %d %d %d" % p)
    f("film prime cascade %d %d %d" % CASCADE)
    for p in GENERATORS:
        f("film prime generator %d %d %d" % p)


PARTS = (("works_clear", clear), ("works", build))  # works_prime runs from filmset:prime, after the twins attach
