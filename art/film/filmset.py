"""Film set for the Prompt Critical trailers: a boiler house, a foundry and machine shop, a coal mine in a hill.

Writes the datapack `filmset` into a world (a copy of the Showroom superflat world):

    python art/film/filmset.py pc-mod/run-film/saves/Film

On world load `filmset:load` force-loads the set area and, when the build version changed, rebuilds it
(`filmset:build`) and primes the machines (`filmset:prime`). Shots call `filmset:refresh` to top up water,
coal and pig iron so the machines keep running between takes.

Ground of the superflat world: bedrock -64, dirt -63..-62, grass -61; things stand at y = -60.
Machines without a visible drive get their rotation from a Create creative motor sunk in the floor (facing up)
under a Create gearbox next to the machine's shaft face — the gearbox is the only part in frame.
"""
import os
import random
import sys

VERSION = 26
BOILERS = (4, 9)
G = -61  # floor layer
rng = random.Random(1924)


class Fn:
    def __init__(self):
        self.lines = []

    def __call__(self, line):
        self.lines.append(line)

    def setb(self, x, y, z, block):
        self(f"setblock {x} {y} {z} {block}")

    def fill(self, x1, y1, z1, x2, y2, z2, block, mode=""):
        xa, xb = sorted((x1, x2))
        ya, yb = sorted((y1, y2))
        za, zb = sorted((z1, z2))
        # fill is capped at 32768 blocks: split along x
        span = (yb - ya + 1) * (zb - za + 1)
        step = max(1, 32768 // span)
        x = xa
        while x <= xb:
            xe = min(xb, x + step - 1)
            self(f"fill {x} {ya} {za} {xe} {yb} {zb} {block}{' ' + mode if mode else ''}")
            x = xe + 1


PC = "promptcritical:"


def motor_gearbox(f, x, y, z, axis, rpm=64, shaft_below=0):
    """Gearbox at (x, y, z) driven from a creative motor sunk in the floor under it (optionally via vertical shafts)."""
    f.setb(x, y, z, f"create:gearbox[axis={axis}]")
    for i in range(shaft_below):
        f.setb(x, y - 1 - i, z, "create:shaft[axis=y]")
    f.setb(x, y - 1 - shaft_below, z, f"create:creative_motor[facing=up]{{ScrollValue:{rpm}}}")


# ------------------------------------------------------------------------------------------------ terrain / yard

def clear(f):
    # wipe the whole set volume, then restore the superflat ground
    f.fill(-20, -60, 20, 150, -20, 110, "air")
    f.fill(-20, -63, 20, 150, -62, 110, "dirt")
    f.fill(-20, G, 20, 150, G, 110, "grass_block")


def yard(f):
    # paved works yard in front of the buildings, gravel margins, a road leading north
    f.fill(-8, G, 24, 74, G, 38, "andesite")
    for _ in range(260):
        x, z = rng.randint(-8, 74), rng.randint(24, 38)
        f.setb(x, G, z, rng.choice(["gravel", "tuff", "polished_andesite", "cobblestone", "coarse_dirt"]))
    f.fill(-8, G, 39, 74, G, 39, PC + "curb")
    f.fill(26, G, 0, 30, G, 23, "gravel")          # road to the gate
    f.fill(25, G, 0, 25, G, 23, "andesite")
    f.fill(31, G, 0, 31, G, 23, "andesite")
    # fence with a gate
    for x in list(range(-10, 25)) + list(range(32, 77)):
        f.setb(x, -60, 22, "iron_bars")
    f.fill(-10, -60, 22, -10, -60, 62, "iron_bars")
    f.fill(76, -60, 22, 76, -60, 62, "iron_bars")
    for x in (-10, 0, 10, 20, 24, 32, 40, 50, 60, 70, 76):
        f.fill(x, -60, 22, x, -59, 22, "stone_bricks")
    # street lights along the yard
    for x in (-2, 16, 36, 56):
        f.fill(x, -60, 31, x, -55, 31, "polished_blackstone_wall")
        f.setb(x, -54, 31, "polished_blackstone")
        f.setb(x, -54, 32, PC + "industrial_light_street[facing=north]")
    # coal heap and wagons on a siding
    for x in range(-6, 2):
        f.setb(x, -60, 30, "rail[shape=east_west]")
    for i, (x, z, h) in enumerate([(-6, 26, 3), (-5, 26, 4), (-4, 26, 3), (-6, 27, 2), (-5, 27, 3), (-4, 27, 2),
                                   (-3, 26, 2), (-7, 26, 2), (-5, 25, 2), (-4, 25, 1), (-6, 25, 1), (-3, 27, 1)]):
        f.fill(x, -60, z, x, -61 + h, z, "coal_block")
    # crates and barrels
    for x, z in [(70, 26), (71, 26), (70, 27), (72, 26)]:
        f.setb(x, -60, z, "barrel[facing=up]")
    f.setb(71, -59, 26, "barrel[facing=up]")


# ------------------------------------------------------------------------------------------------ boiler house

def gable_roof(f, x1, x2, z1, z2, y0, stair, ridge):
    """Gable roof with the ridge along x, eaves overhanging one block past the walls z1/z2."""
    k = 0
    while True:
        zn, zs = z1 - 1 + k, z2 + 1 - k
        y = y0 + k
        if zn >= zs:
            if zn == zs:
                f.fill(x1 - 1, y, zn, x2 + 1, y, zn, ridge)
            break
        f.fill(x1 - 1, y, zn, x2 + 1, y, zn, f"{stair}[facing=south,half=bottom]")
        f.fill(x1 - 1, y, zs, x2 + 1, y, zs, f"{stair}[facing=north,half=bottom]")
        k += 1


def gable_walls(f, x, z1, z2, y0, block):
    k = 0
    while z1 + k <= z2 - k:
        f.fill(x, y0 + k, z1 + k, x, y0 + k, z2 - k, block)
        k += 1


def boiler_house(f):
    X1, X2, Z1, Z2 = 0, 24, 40, 58
    TOP = -50
    # floor
    f.fill(X1, G, Z1, X2, G, Z2, "stone_bricks")
    for _ in range(80):
        f.setb(rng.randint(X1 + 1, X2 - 1), G, rng.randint(Z1 + 1, Z2 - 1),
               rng.choice(["cracked_stone_bricks", "mossy_stone_bricks", "polished_andesite"]))
    # walls: stone plinth, brick body
    for x in (X1, X2):
        f.fill(x, -60, Z1, x, TOP, Z2, "bricks")
    for z in (Z1, Z2):
        f.fill(X1, -60, z, X2, TOP, z, "bricks")
    f.fill(X1, -60, Z1, X2, -60, Z1, "stone_bricks")
    f.fill(X1, -60, Z2, X2, -60, Z2, "stone_bricks")
    f.fill(X1, -60, Z1, X1, -60, Z2, "stone_bricks")
    f.fill(X2, -60, Z1, X2, -60, Z2, "stone_bricks")
    f.fill(X1 + 1, -60, Z1 + 1, X2 - 1, TOP, Z2 - 1, "air")
    # pilasters and windows on the long walls
    for z, out in ((Z1, Z1 - 1), (Z2, Z2 + 1)):
        for x in range(X1, X2 + 1, 4):
            f.fill(x, -60, out, x, TOP, out, "bricks")
            f.setb(x, -60, out, "stone_bricks")
            f.setb(x, TOP, out, "stone_brick_slab[type=top]")
        for x in range(X1 + 1, X2, 4):
            if z == Z1 and x in (9, 13):
                continue  # entrance bay
            f.fill(x, -58, z, x + 2, -53, z, "light_gray_stained_glass_pane")
            f.fill(x, -52, z, x + 2, -52, z, "bricks")
            f.setb(x + 1, -52, z, "chiseled_stone_bricks")
            f.fill(x, -59, out, x + 2, -59, out, "stone_brick_slab[type=top]")
    # windows in the gable ends
    for x in (X1, X2):
        for z in (44, 48, 52):
            f.fill(x, -58, z, x, -53, z + 2, "light_gray_stained_glass_pane")
            f.setb(x, -52, z + 1, "chiseled_stone_bricks")
    # entrance: big arched doorway with timber doors open
    f.fill(10, -60, Z1, 14, -55, Z1, "air")
    f.fill(10, -54, Z1, 14, -54, Z1, "stone_brick_stairs[facing=north,half=top]")
    f.setb(12, -54, Z1, "chiseled_stone_bricks")
    f.fill(10, G, Z1 - 3, 14, G, Z1, "polished_andesite")
    # cornice
    f.fill(X1 - 1, TOP, Z1 - 1, X2 + 1, TOP, Z1 - 1, "stone_brick_slab[type=top]")
    f.fill(X1 - 1, TOP, Z2 + 1, X2 + 1, TOP, Z2 + 1, "stone_brick_slab[type=top]")
    # roof
    gable_walls(f, X1, Z1, Z2, TOP + 1, "bricks")
    gable_walls(f, X2, Z1, Z2, TOP + 1, "bricks")
    gable_roof(f, X1, X2, Z1, Z2, TOP + 1, "deepslate_tile_stairs", "deepslate_tile_slab[type=bottom]")
    # roof trusses: steel girders across the hall, lamps hanging under them
    for x in range(X1 + 4, X2, 4):
        f.fill(x, TOP, Z1 + 1, x, TOP, Z2 - 1, "create:metal_girder[axis=z]")
    for x, z in [(4, 44), (8, 44), (12, 44), (16, 44), (20, 44), (16, 53), (20, 53), (4, 54)]:
        f.setb(x, TOP - 1, z, PC + "industrial_light")

    # --- the machines (layout as in the Showroom, moved): a battery of two boilers with stokers, steam engine
    by, bz = -59, 49
    for bx in BOILERS:
        f.setb(bx, by, bz, PC + "fire_tube_boiler[facing=north]")
        f.fill(bx, by + 2, bz + 4, bx, -30, bz + 4, PC + "chimney")
        f.setb(bx, -60, bz - 2, PC + "grate_stoker[facing=north]")
        motor_gearbox(f, bx - 1, -60, bz - 2, "z", 32)
    bx = BOILERS[-1]
    f.setb(bx + 3, by, bz, PC + "steam_engine_cylinder[facing=north]")
    # engine line shaft east to the wall, on a bearing pedestal
    f.fill(bx + 5, by, bz + 2, X2 - 1, by, bz + 2, "create:shaft[axis=x]")
    for x in (17, 21):
        f.setb(x, -60, bz + 2, "create:andesite_casing")
    # stoking floor plates, handrails around the engine pit
    f.fill(2, G, 44, 15, G, 46, PC + "grating")
    for x in range(14, 23):
        f.setb(x, -60, 47, PC + "handrail[facing=south]")
    # coal bunker in the north-west corner, a coal cart on rails from the entrance
    f.fill(1, -60, 41, 5, -59, 41, "stone_bricks")
    f.fill(5, -60, 41, 5, -59, 43, "stone_bricks")
    for x, z, h in [(1, 42, 3), (2, 42, 3), (3, 42, 2), (4, 42, 1), (1, 43, 2), (2, 43, 2), (3, 43, 1)]:
        f.fill(x, -60, z, x, -61 + h, z, "coal_block")
    for z in range(Z1 - 3, Z1 + 3):
        f.setb(12, -60, z, "rail[shape=north_south]")
    f.setb(12, -60, Z1 + 3, "rail[shape=north_west]")
    for x in range(6, 12):
        f.setb(x, -60, Z1 + 3, "rail[shape=east_west]")
    # feed water tank and pump with copper pipes running to the boiler
    f.fill(19, -60, 52, 20, -58, 53, "create:fluid_tank")
    f.setb(18, -60, 52, "create:mechanical_pump[facing=west]")
    f.fill(15, -60, 52, 17, -60, 52, "create:fluid_pipe")
    f.fill(19, -57, 54, 20, -57, 54, "air")
    # gauge board, clock, posters, signs on the walls
    f.setb(12, -53, Z1 + 1, PC + "wall_clock[facing=south]")
    # steam main from the boilers to the engine along the back wall
    f.fill(4, -56, 56, 13, -56, 56, "create:fluid_pipe")
    f.setb(2, -57, Z2 - 1, PC + "safety_poster[facing=north]")
    f.setb(16, -57, Z2 - 1, PC + "safety_poster_large[facing=north]")
    f.setb(22, -56, Z1 + 1, PC + "room_sign[facing=south]")
    for z in (50, 51, 52):
        f.setb(X1 + 1, -57, z, PC + "instrument_pressure[facing=east]")
    f.setb(23, -60, 42, "create:depot")
    f.setb(22, -60, 41, "barrel[facing=up]")
    f.setb(23, -60, 41, "barrel[facing=up]")


# ------------------------------------------------------------------------------------------------ foundry and machine shop

def foundry(f):
    X1, X2, Z1, Z2 = 32, 62, 40, 54
    TOP = -53
    f.fill(X1, G, Z1, X2, G, Z2, "smooth_stone")
    f.fill(X1 + 1, G, Z1 + 2, X2 - 1, G, Z1 + 7, PC + "painted_concrete")       # aisle
    f.fill(X1 + 1, G, Z1 + 2, X2 - 1, G, Z1 + 2, PC + "painted_concrete_yellow")
    f.fill(X1 + 1, G, Z1 + 8, X2 - 1, G, Z1 + 8, PC + "painted_concrete_yellow")
    # walls: brick dado, corrugated steel above, steel posts
    for x in (X1, X2):
        f.fill(x, -60, Z1, x, TOP, Z2, PC + "corrugated_steel")
        f.fill(x, -60, Z1, x, -58, Z2, "bricks")
    for z in (Z1, Z2):
        f.fill(X1, -60, z, X2, TOP, z, PC + "corrugated_steel")
        f.fill(X1, -60, z, X2, -58, z, "bricks")
    for x in range(X1, X2 + 1, 6):
        for z in (Z1 - 1, Z2 + 1):
            f.fill(x, -60, z, x, TOP, z, "create:metal_girder[axis=y]")
    # ribbon windows
    for z in (Z1, Z2):
        for x in range(X1 + 1, X2, 6):
            f.fill(x + 1, -57, z, x + 4, -55, z, "light_gray_stained_glass_pane")
    for x in (X1, X2):
        f.fill(x, -57, Z1 + 2, x, -55, Z2 - 2, "light_gray_stained_glass_pane")
    # two big open bays to the yard
    for x0 in (39, 52):
        f.fill(x0, -60, Z1, x0 + 4, -56, Z1, "air")
        f.fill(x0, -55, Z1, x0 + 4, -55, Z1, "create:metal_girder[axis=x]")
    # roof: flat steel deck with a glazed clerestory along the middle
    f.fill(X1, TOP + 1, Z1, X2, TOP + 1, Z2, PC + "corrugated_steel")
    f.fill(X1 + 1, TOP + 1, Z1 + 5, X2 - 1, TOP + 1, Z2 - 5, "air")
    f.fill(X1 + 1, TOP + 1, Z1 + 5, X2 - 1, TOP + 2, Z1 + 5, PC + "glass_block_frosted")
    f.fill(X1 + 1, TOP + 1, Z2 - 5, X2 - 1, TOP + 2, Z2 - 5, PC + "glass_block_frosted")
    f.fill(X1 + 1, TOP + 3, Z1 + 5, X2 - 1, TOP + 3, Z2 - 5, PC + "corrugated_steel")
    f.fill(X1 + 1, -60, Z1 + 1, X2 - 1, TOP, Z2 - 1, "air")
    for x in range(X1 + 3, X2, 6):
        f.fill(x, TOP, Z1 + 1, x, TOP, Z2 - 1, "create:metal_girder[axis=z]")
        for z in (Z1 + 4, Z2 - 4):
            f.setb(x, TOP - 1, z, PC + "industrial_light")

    z = 49
    # cupola with a charging platform, crucible furnace, moulding, shakeout, lathe, drill press
    f.setb(36, -59, z, PC + "cupola_furnace")
    f.setb(35, -59, z, "create:gearbox[axis=z]")
    f.setb(35, -60, z, "create:shaft[axis=y]")
    f.setb(35, G, z, "create:creative_motor[facing=up]{ScrollValue:64}")
    f.fill(35, -57, z + 2, 38, -57, z + 3, PC + "grating")
    f.fill(38, -60, z + 3, 38, -58, z + 3, "create:metal_girder[axis=y]")
    f.setb(40, -60, z, PC + "crucible_furnace[facing=north]")
    f.setb(44, -60, z, PC + "molding_station[facing=north]")
    motor_gearbox(f, 43, -60, z, "z")
    f.setb(48, -60, z, PC + "shakeout_grate[facing=north]")
    motor_gearbox(f, 47, -60, z, "z")
    f.setb(53, -60, z, PC + "machine_lathe[facing=north]")
    motor_gearbox(f, 52, -60, z, "z")
    f.setb(58, -60, z, PC + "drill_press[facing=north]")
    motor_gearbox(f, 58, -60, z + 1, "x")
    # sand moulds on the floor, sand heaps, a workbench line, lanterns on chains
    for x in (42, 44, 46):
        f.setb(x, -60, 45, PC + "sand_mold")
    for x, zz in [(46, 52), (47, 52), (46, 53), (45, 53), (47, 53)]:
        f.setb(x, -60, zz, "sand")
    f.setb(46, -59, 53, "sand")
    for x in range(54, 62):
        f.setb(x, -60, Z2 - 1, "smooth_stone_slab[type=double]")
    f.setb(55, -59, Z2 - 1, "create:depot")
    f.setb(57, -59, Z2 - 1, "anvil[facing=east]")
    f.setb(60, -59, Z2 - 1, "smithing_table")
    for x in (37, 41, 45, 49, 53, 57):
        f.setb(x, -54, 47, "chain[axis=y]")
        f.setb(x, -55, 47, "lantern[hanging=true]")
    f.setb(X1 + 1, -60, Z2 - 1, "barrel[facing=up]")
    f.setb(X1 + 1, -60, Z2 - 2, "barrel[facing=up]")
    f.setb(X1 + 2, -60, Z2 - 1, "sand")
    f.setb(X1 + 3, -60, Z2 - 1, "sand")
    f.setb(X1 + 2, -59, Z2 - 1, "sand")
    f.setb(50, -57, Z2 - 1, PC + "safety_poster_large[facing=north]")
    f.setb(40, -57, Z2 - 1, PC + "safety_poster[facing=north]")
    f.setb(X2 - 1, -56, 47, PC + "wall_clock[facing=west]")


# ------------------------------------------------------------------------------------------------ coal mine in a hill

HILL_X, HILL_Z, HILL_RX, HILL_RZ = 112, 62, 30, 34
TOPY = -46            # plateau surface block
SHAFT = (112, 64)     # shaft axis under the headframe
CAGE_Y = -44          # cage controller at the top landing (cage stands on the plateau over the shaft)
HOIST = (112, -44, 72)
FAN = (98, -44, 64)


def hill_top(x, z):
    """Surface block y of the hill at (x, z), or None outside it."""
    import math
    dx, dz = (x - HILL_X) / HILL_RX, (z - HILL_Z) / HILL_RZ
    wob = 0.06 * math.sin(x * 0.37 + z * 0.11) + 0.05 * math.sin(z * 0.29 - x * 0.07) + 0.03 * math.sin(x * 0.9 + z * 0.8)
    d = math.hypot(dx, dz) + wob
    if d >= 1.0:
        return None
    if 107 <= x <= 117 and z < 37:      # cutting in front of the adit portal
        return None
    if d < 0.55:
        return TOPY
    return round(TOPY - (d - 0.55) / 0.45 * (TOPY - G))


def mine(f):
    import math
    tops = {}
    for x in range(HILL_X - HILL_RX - 1, HILL_X + HILL_RX + 2):
        for z in range(HILL_Z - HILL_RZ - 1, HILL_Z + HILL_RZ + 2):
            t = hill_top(x, z)
            if t is not None and t > G:
                tops[(x, z)] = t
    for (x, z), t in tops.items():
        steep = max(t - tops.get((x + dx, z + dz), G) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        rock = rng.choice(["stone", "stone", "andesite", "tuff"])
        f.fill(x, G, z, x, t - 1, z, rock)
        if steep >= 3:
            f.setb(x, t, z, rng.choice(["stone", "andesite", "gravel", "coarse_dirt"]))
        else:
            f.setb(x, t - 1, z, "dirt")
            f.setb(x, t, z, "grass_block")
    # retaining walls of the cutting and the portal
    for x in (106, 118):
        for z in range(28, 37):
            h = max(hill_top(x + (1 if x == 118 else -1), z) or G, -57)
            f.fill(x, -60, z, x, h, z, "stone_bricks")
    f.fill(107, G, 26, 117, G, 36, "gravel")
    sx, sz = SHAFT

    # --- adit: portal on the face of the cutting, drift south to the shaft bottom
    f.fill(111, -60, 37, 113, -58, sz - 2, "air")
    f.fill(111, G, 37, 113, G, sz - 2, "gravel")
    f.fill(110, -60, 37, 110, -56, 37, "dark_oak_log[axis=y]")
    f.fill(114, -60, 37, 114, -56, 37, "dark_oak_log[axis=y]")
    f.fill(110, -57, 37, 114, -57, 37, "dark_oak_log[axis=x]")
    f.fill(111, -60, 37, 113, -58, 37, "air")
    f.setb(112, -56, 36, PC + "room_sign[facing=north]")
    f.setb(109, -58, 36, PC + "industrial_light_floodlight[facing=north]")
    for z in range(39, sz - 2, 3):
        f.setb(112, -59, z, PC + "mine_support[facing=north]")
    for z in range(37, sz - 1):
        if (z - 39) % 3:
            f.setb(111, -60, z, "rail[shape=north_south]")
    for z in range(26, 37):
        f.setb(111, -60, z, "rail[shape=north_south]")
    for z in range(42, sz - 2, 8):
        f.setb(113, -58, z, PC + "industrial_light")
    for z in (47, 53):
        f.setb(111, -58, z, PC + "explosion_barrier[kind=water]")
    # coal seam showing in the drift walls
    for _ in range(70):
        x = rng.choice([110, 114])
        f.setb(x, rng.randint(-60, -58), rng.randint(38, sz - 2), PC + "coal_seam")

    # --- shaft 3x3 from the drift level up through the plateau
    f.fill(sx - 1, -60, sz - 1, sx + 1, TOPY, sz + 1, "air")
    f.fill(sx - 1, G, sz - 1, sx + 1, G, sz + 1, "stone_bricks")
    # shaft collar on the plateau
    f.fill(sx - 2, TOPY, sz - 2, sx + 2, TOPY, sz + 2, "polished_andesite")
    f.fill(sx - 1, TOPY, sz - 1, sx + 1, TOPY, sz + 1, "air")
    f.setb(sx, CAGE_Y, sz, PC + "mine_cage[facing=north]")
    for i in range(3):
        f.setb(sx, CAGE_Y + 3 + 3 * i, sz, PC + "headframe[facing=north]")

    # --- shaft bottom: crosscut east to the longwall, sump and drainage pump west
    f.fill(sx + 2, -60, sz - 1, 116, -58, sz + 1, "air")
    f.fill(108, -60, sz - 1, sx - 2, -58, sz + 1, "air")
    f.setb(109, -60, sz, PC + "mine_drainage_pump[facing=east]")
    motor_gearbox(f, 109, -60, sz - 1, "x")
    f.fill(108, G, sz + 1, 109, G, sz + 1, PC + "mine_water")
    f.setb(115, -60, sz - 1, PC + "canary_cage[bird=alive]")
    f.setb(sx + 2, -58, sz, PC + "industrial_light")

    # --- longwall: face of coal along z = sz + 3, powered roof supports, the shearer on the face
    f.fill(117, -60, sz - 2, 135, -58, sz + 2, "air")
    f.fill(117, G, sz - 2, 135, G, sz + 2, "deepslate")
    f.fill(116, -61, sz + 3, 136, -57, sz + 3, PC + "coal_seam")
    f.fill(117, -57, sz - 2, 135, -57, sz + 2, "deepslate")
    for x in range(118, 135):
        f.setb(x, -59, sz, PC + "powered_roof_support[facing=south,extended=true]")
    f.setb(126, -60, sz + 2, PC + "longwall_shearer[facing=south,running=true]")
    for x in (120, 127, 134):
        f.setb(x, -58, sz - 2, PC + "industrial_light")
    f.setb(135, -60, sz - 2, PC + "degasification_borehole[facing=west]")

    # --- surface: hoist house, the hoist on the plateau, main fan, degassing pump, trees
    hx, hy, hz = HOIST
    f.fill(hx - 4, TOPY, hz - 3, hx + 4, TOPY, hz + 4, "stone_bricks")
    for x in (hx - 4, hx + 4):
        f.fill(x, TOPY + 1, hz - 3, x, TOPY + 6, hz + 4, "bricks")
    f.fill(hx - 4, TOPY + 1, hz + 4, hx + 4, TOPY + 6, hz + 4, "bricks")
    for x in (hx - 4, hx - 2, hx + 2, hx + 4):
        f.fill(x, TOPY + 1, hz - 3, x, TOPY + 6, hz - 3, "bricks")
    f.fill(hx - 4, TOPY + 7, hz - 4, hx + 4, TOPY + 7, hz + 5, "deepslate_tile_slab[type=bottom]")
    f.fill(hx - 4, TOPY + 3, hz, hx - 4, TOPY + 5, hz + 2, "light_gray_stained_glass_pane")
    f.setb(hx, hy, hz, PC + "mine_hoist[facing=north]")
    f.setb(hx + 2, hy, hz, "create:creative_motor[facing=west]{ScrollValue:64}")
    f.setb(hx - 3, TOPY + 1, hz + 2, PC + "mine_dispatch_cabinet[facing=east]")
    f.setb(hx, TOPY + 6, hz + 1, PC + "industrial_light")
    fx_, fy, fz = FAN
    f.setb(fx_, fy, fz, PC + "main_mine_fan[facing=north]")
    f.setb(fx_, fy, fz + 2, "create:creative_motor[facing=north]{ScrollValue:64}")
    f.fill(fx_ - 1, TOPY + 1, fz + 2, fx_ + 1, TOPY + 3, fz + 3, "bricks")
    f.setb(fx_, fy, fz + 2, "create:creative_motor[facing=north]{ScrollValue:64}")
    f.setb(126, -45, 56, PC + "degasification_pump[facing=north]")
    motor_gearbox(f, 126, -45, 57, "x")
    f.setb(sx + 3, TOPY + 1, sz - 4, PC + "industrial_light_floodlight[facing=south]")
    for x, z in [(92, 52), (95, 78), (128, 80), (133, 66), (124, 48), (100, 84), (90, 66)]:
        t = hill_top(x, z)
        if t is not None:
            f(f"place feature minecraft:spruce {x} {t + 1} {z}")


# ------------------------------------------------------------------------------------------------ power (07 §7.3-7.6)

# The set's own power plant: the boiler house's steam engine turns a shaft generator (6.3 kV) on its line shaft, a
# 6 kV overhead line on concrete poles runs to package substations (transformer TM-630/6 + lighting board), and every
# lamp of the set is lit by the board within 24 blocks of it (LightingBoards.RANGE), not by a block state.
GEN = (26, -59, 51)
POLE_H = 9                                   # pole blocks; the MV connector sits on top at ground + POLE_H
# substations: transformer (x, ground y, z); LV cable path from the transformer's west face to the board; board facing
SUBS = {
    "boiler": ((26, -60, 44), [(25, -60, 44), (24, -60, 44), (23, -60, 44)], ((22, -60, 44), "west")),
    "foundry": ((47, -60, 37), [(46, -60, 37), (46, -60, 38), (46, -60, 39), (46, -60, 40), (46, -60, 41)],
                ((46, -60, 42), "south")),
    "portal": ((116, -60, 33), [(115, -60, 33)], ((114, -60, 33), "west")),
    "headframe": ((120, -45, 72), [(119, -45, 72)], ((118, -45, 72), "west")),
    # the later-era rows get their power from the same generator: steel row and control room, then the oil,
    # chemistry and nuclear row
    "steelworks": ((40, -60, 126), [(39, -60, 126)], ((38, -60, 126), "west")),
    "nuclear": ((84, -60, 155), [(83, -60, 155)], ((82, -60, 155), "west")),
}
# line poles (x, ground y, z) between the substations; spans <= 32 m
POLES = {"gen": (28, -60, 51), "yard_w": (30, -60, 36), "yard_e": (74, -60, 36), "fence": (96, -60, 36),
         "hill": (118, -45, 42), "north1": (28, -60, 80), "north2": (30, -60, 110), "eras_mid": (64, -60, 140)}
LINE = ["gen", "sub:boiler", "yard_w", "sub:foundry", "yard_e", "fence", "sub:portal", "hill", "sub:headframe"]
# branch north from the generator pole to the later eras (a second line from the same connector)
BRANCH = ["gen", "north1", "north2", "sub:steelworks", "eras_mid", "sub:nuclear"]


def _pole(f, x, y, z):
    f.fill(x, y, z, x, y + POLE_H - 1, z, PC + "concrete_pole")
    f.setb(x, y + POLE_H, z, PC + "wire_connector_mv")
    return (x, y + POLE_H, z)


def _connector(name):
    if name.startswith("sub:"):
        (tx, ty, tz), _, _ = SUBS[name[4:]]
        return (tx + 1, ty + POLE_H, tz)
    x, y, z = POLES[name]
    return (x, y + POLE_H, z)


def power(f):
    # generator on the engine line shaft, through the boiler house east wall, on a casing plinth
    gx, gy, gz = GEN
    f.fill(24, gy, gz, gx - 1, gy, gz, "create:shaft[axis=x]")
    f.fill(gx - 1, gy - 1, gz, gx, gy - 1, gz, "create:andesite_casing")
    f.fill(gx - 1, G, gz - 2, gx + 3, G, gz + 2, "gray_concrete")
    f.setb(gx, gy, gz, PC + "shaft_generator[facing=east]")
    f.setb(gx, gy + 1, gz, PC + "generator_exciter[facing=east]")
    # generator terminals (front) -> cable up beside the pole to its connector
    px, py, pz = POLES["gen"]
    f.fill(gx + 1, gy, gz, gx + 1, py + POLE_H, gz, PC + "power_cable")
    _pole(f, px, py, pz)
    for name in ("yard_w", "yard_e", "fence", "hill", "north1", "north2", "eras_mid"):
        x, y, z = POLES[name]
        _pole(f, x, y, z)
    for (tx, ty, tz), lv, ((bx, by, bz), facing) in SUBS.values():
        f.fill(tx - 2, ty - 1, tz - 1, tx + 1, ty - 1, tz + 1, "gray_concrete")
        f.setb(tx, ty, tz, PC + "distribution_transformer[facing=north]")
        # HV: the upper cell's top face -> cable up to the connector on the pole east of it
        f.fill(tx, ty + 2, tz, tx, ty + POLE_H, tz, PC + "power_cable")
        _pole(f, tx + 1, ty, tz)
        for x, y, z in lv:
            f.setb(x, y, z, PC + "power_cable")
        f.setb(bx, by, bz, PC + f"lighting_board[facing={facing}]")


def lighting(f):
    """More lamps on the boards' groups (within 24 blocks of a board): high bays in the halls, floodlights on the
    facades and the hoist house, street lights along the yard and the mine road. ~25 kW in all on the 130 kW engine."""
    def street(x, z, facing="north"):
        f.fill(x, -60, z, x, -55, z, "polished_blackstone_wall")
        f.setb(x, -54, z, "polished_blackstone")
        dz = {"north": 1, "south": -1}[facing]
        f.setb(x, -54, z + dz, PC + f"industrial_light_street[facing={facing}]")
    # boiler house: high bays under every truss (y -51), in three rows
    for x in range(4, 24, 4):
        for z in (43, 48, 53):
            f.setb(x, -51, z, PC + "lamp_high_bay")
    # foundry: high bays between the pendants under the trusses (y -54)
    for x in range(35, 62, 6):
        f.setb(x, -54, 47, PC + "lamp_high_bay")
    # floodlights on the facades over the yard
    for x, y in ((3, -54), (21, -54), (35, -55), (59, -55)):
        f.setb(x, y, 39, PC + "industrial_light_floodlight[facing=north]")
    # more street lights along the yard and the road to the adit
    for x in (6, 46, 66):
        street(x, 31)
    for x in (92, 102):
        street(x, 34)
    # hoist house: floodlights onto the headframe and the shaft collar
    for x in (109, 115):
        f.setb(x, -41, 68, PC + "industrial_light_floodlight[facing=north]")
    # later eras: the control room lit inside, floodlights on the heroes
    for x, z in ((58, 132), (61, 132), (58, 135), (61, 135)):
        f.setb(x, -57, z, PC + "lamp_fluorescent[facing=north]")
    # (only lamps within 24 blocks of the steelworks or nuclear board)
    for x, y, z, facing in ((19, -52, 127, "north"), (31, -56, 128, "north"),
                            (93, -54, 157, "north"), (65, -56, 158, "north"), (78, -57, 158, "north")):
        f.setb(x, y - 1, z, "polished_blackstone_wall")
        f.setb(x, y, z, PC + f"industrial_light_floodlight[facing={facing}]")
    # bulkhead lamp over the generator
    f.setb(GEN[0], GEN[1] + 3, GEN[2] - 1, PC + "lamp_bulkhead[facing=north]")


# Electric drives instead of the hidden creative motors (07 §7.6.3). The motor turns only on a horizontal axis: a
# drive sunk under a machine becomes motor (facing north) -> gearbox (axis x) -> vertical shaft up to where the
# creative motor stood. "sunk": (x, y of the old motor, z, gear); "inline": (x, y, z, facing, gear) in place.
# Each is fed from its substation's 0.4 kV bus by a cable in the ground (one cable group = one bus).
SUNK = {"foundry": [(35, G, 49, 64), (43, G, 49, 64), (47, G, 49, 64), (52, G, 49, 64), (58, G, 50, 64)],
        "headframe": [(126, -46, 57, 64)]}
# the hoist needs more than one 50 SU motor: it keeps its drive
INLINE = {"headframe": [(98, -44, 66, "north", 64)]}


def _cable(f, pts):
    """Cable along a polyline of axis-aligned points (inclusive)."""
    for (x1, y1, z1), (x2, y2, z2) in zip(pts, pts[1:]):
        f.fill(x1, y1, z1, x2, y2, z2, PC + "power_cable")


def _motor_positions():
    out = [(x, y - 1, z + 1, gear) for drives in SUNK.values() for x, y, z, gear in drives]
    out += [(x, y, z, gear) for drives in INLINE.values() for x, y, z, _, gear in drives]
    return out


def motors(f):
    for drives in SUNK.values():
        for x, y, z, _ in drives:
            f.setb(x, y, z, "create:shaft[axis=y]")
            f.setb(x, y - 1, z, "create:gearbox[axis=x]")
            f.setb(x, y - 1, z + 1, PC + "electric_motor[facing=north]")
    for drives in INLINE.values():
        for x, y, z, facing, _ in drives:
            f.setb(x, y, z, PC + f"electric_motor[facing={facing}]")
    # foundry: from the LV path inside the south wall down into the ground, along under the motors (y -63)
    _cable(f, [(46, -61, 41), (46, -63, 41), (46, -63, 50)])
    _cable(f, [(35, -63, 50), (58, -63, 50), (58, -63, 51)])
    # headframe: from the LV cable down under the plateau (y -47), west to the hoist and the fan, south to the pump
    _cable(f, [(119, -46, 72), (119, -47, 72), (98, -47, 72), (98, -47, 66), (98, -45, 66)])
    _cable(f, [(114, -47, 72), (114, -45, 72)])
    _cable(f, [(119, -47, 72), (126, -47, 72), (126, -47, 59)])


def power_start(f):
    for a, b in list(zip(LINE, LINE[1:])) + list(zip(BRANCH, BRANCH[1:])):
        (ax, ay, az), (bx, by, bz) = _connector(a), _connector(b)
        f(f"filmset wire {ax} {ay} {az} {bx} {by} {bz}")
    # 6.3 kV, the engine's 32 rpm gear, field on (the stop valve opens later, in open_valve)
    f(f"filmset gen {GEN[0]} {GEN[1]} {GEN[2]} mv 32 0")


MOTOR_STEP_TICKS = 60


def motors_start(f, fns):
    """Start the drives one by one, as a real plant does: eight direct-on-line starts at once draw ~1000 A at
    0.4 kV, more than the 500 kVA set can give, and the voltage collapses to 0.15 pu with every motor stalled."""
    for i, (x, y, z, gear) in enumerate(_motor_positions()):
        m = Fn()
        m(f"filmset motor {x} {y} {z} {gear}")
        fns[f"motor_{i}"] = m
        f(f"schedule function filmset:motor_{i} {1 + i * MOTOR_STEP_TICKS}t")


def check_spans():
    """Spans <= 32 m and clear of the hill and the buildings' roofs (warnings only)."""
    import math
    for a, b in list(zip(LINE, LINE[1:])) + list(zip(BRANCH, BRANCH[1:])):
        pa, pb = _connector(a), _connector(b)
        d = math.dist(pa, pb)
        if d > 32:
            print(f"WARNING span {a}-{b} {d:.1f} m > 32")
        for i in range(1, 20):
            u = i / 20
            x, y, z = (pa[k] + (pb[k] - pa[k]) * u for k in range(3))
            t = hill_top(round(x), round(z))
            if t is not None and y - 2 < t:
                print(f"WARNING span {a}-{b} touches the hill at {x:.0f},{z:.0f} (wire y {y:.1f}, ground {t})")


# ------------------------------------------------------------------------------------------------ later eras (I1-I4)

# Units ported from the GameTests (eras.py), on concrete pads north of the I0 set: row A steel and power,
# row B oil, chemistry and nuclear. (name, x, z) of the GameTest origin.
ERA_ROWS = [
    ("coke_battery", 0, 130), ("blast_furnace", 14, 130), ("arc_furnace", 28, 130), ("steam_turbine", 41, 130),
    ("control_room", 56, 130),
    ("drilling_rig", 0, 160), ("oil_field", 17, 160), ("refinery", 34, 160), ("pyrolysis", 47, 160),
    ("combined_cycle", 61, 160), ("enrichment", 74, 160), ("pwr", 88, 160),
]


def eras(f, start):
    from eras import ERAS, standing_oy
    f.fill(-12, -60, 118, 110, -30, 186, "air")
    f.fill(-12, -63, 118, 110, -62, 186, "dirt")
    f.fill(-12, G, 118, 110, G, 186, "grass_block")
    for name, x, z in ERA_ROWS:
        build, run, (sx, _sy, sz), _ = ERAS[name]
        f.fill(x - 2, G, z - 3, x + sx + 1, G, z + sz + 1, "gray_concrete")
        oy = standing_oy(name)
        build(f, x, oy, z)
        if run:
            run(start, x, oy, z)


# ------------------------------------------------------------------------------------------------ prime / refresh

def prime(f):
    f("execute as @a run function filmset:kit")
    f("function filmset:refresh")
    for bx in BOILERS:
        for _ in range(3):
            f(f"pc use {bx} -59 49 minecraft:coal")
        f(f"pc use {bx} -59 49 minecraft:flint_and_steel")
        # a cold Lancashire boiler takes ~20 game minutes to raise steam (91 -> 144 C in 5 min): start it hot
        f(f"filmset boiler {bx} -59 49 1.1 0.67")
    f("schedule function filmset:open_valve 200t")
    hx, hy, hz = HOIST
    f(f"pc use {hx} {hy} {hz} promptcritical:wire_rope 16")


def refresh(f):
    for bx in BOILERS:
        f(f"data merge block {bx} -59 49 {{feed:{{Fluid:{{id:\"minecraft:water\",amount:16000}}}}}}")
        f(f"data merge block {bx} -60 47 {{hopper:{{Items:[{{Slot:0,id:\"minecraft:coal\",count:64}}],Size:1}}}}")
    f("data merge block 36 -59 49 {inventory:{Items:[{Slot:0,id:\"promptcritical:pig_iron\",count:16},{Slot:1,id:\"minecraft:charcoal\",count:16}],Size:2}}")
    f("data merge block 40 -60 49 {inventory:{Items:[{Slot:1,id:\"minecraft:coal\",count:16}],Size:2}}")


def write(world):
    root = os.path.join(world, "datapacks", "filmset")
    fn_dir = os.path.join(root, "data", "filmset", "function")
    tag_dir = os.path.join(root, "data", "minecraft", "tags", "function")
    os.makedirs(fn_dir, exist_ok=True)
    os.makedirs(tag_dir, exist_ok=True)
    with open(os.path.join(root, "pack.mcmeta"), "w") as fh:
        fh.write('{"pack": {"pack_format": 48, "description": "Prompt Critical film set"}}\n')
    with open(os.path.join(tag_dir, "load.json"), "w") as fh:
        fh.write('{"values": ["filmset:load"]}\n')

    fns = {}
    load = Fn()
    load("scoreboard objectives add filmset dummy")
    load("gamerule doDaylightCycle false")
    load("gamerule doWeatherCycle false")
    load("gamerule doMobSpawning false")
    load("gamerule announceAdvancements false")
    load("gamerule sendCommandFeedback false")
    load("weather clear")
    load("forceload add -20 20 150 110")
    load("forceload add -20 115 110 190")
    load(f"execute unless score #built filmset matches {VERSION} run schedule function filmset:build 40t")
    fns["load"] = load

    build = Fn()
    # eras clears its own zone first: it must run before power, which puts substations and lamps into that zone
    for part in ("clear", "yard", "boiler_house", "foundry", "mine", "eras", "power"):
        build(f"function filmset:{part}")
    build(f"scoreboard players set #built filmset {VERSION}")
    build("schedule function filmset:prime 40t")
    # the units' sim twins appear on the first ticks; panel buttons before that are ignored (eras.py)
    build("schedule function filmset:eras_start 800t")
    # connectors and the generator get their sim twins on the first ticks; the engine starts in open_valve (240t)
    build("schedule function filmset:power_start 100t")
    # couple the line shaft to the running engine: Create joins a shaft to a source only when the shaft is placed
    # next to it, and the set's shaft was filled before the engine multiblock formed (prime 40t + valve 200t)
    build("schedule function filmset:couple 400t")
    # electric drives start once the generator is up on the coupled shaft
    build("schedule function filmset:motors_start 600t")
    fns["build"] = build

    for name, fn in (("clear", clear), ("yard", yard), ("boiler_house", boiler_house), ("foundry", foundry), ("mine", mine),
                     ("prime", prime), ("refresh", refresh)):
        f = Fn()
        fn(f)
        fns[name] = f

    pw, ps = Fn(), Fn()
    power(pw)
    lighting(pw)
    motors(pw)
    ms = Fn()
    motors_start(ms, fns)
    fns["motors_start"] = ms
    power_start(ps)
    fns["power"], fns["power_start"] = pw, ps
    check_spans()

    eb, es = Fn(), Fn()
    eras(eb, es)
    fns["eras"] = eb
    fns["eras_start"] = es

    couple = Fn()
    ex = BOILERS[-1] + 5
    couple(f"setblock {ex} -59 51 air")
    couple(f"setblock {ex} -59 51 create:shaft[axis=x]")
    fns["couple"] = couple

    valve = Fn()
    valve(f"pc panel {BOILERS[-1] + 3} -59 49 0")
    fns["open_valve"] = valve
    kit = Fn()
    kit("gamemode creative @s")
    kit("tp @s 12 -60 30 180 10")
    fns["kit"] = kit

    for name, f in fns.items():
        with open(os.path.join(fn_dir, name + ".mcfunction"), "w") as fh:
            fh.write("\n".join(l for l in f.lines if l) + "\n")
    print(f"filmset v{VERSION}: " + ", ".join(f"{n} {len(f.lines)}" for n, f in fns.items()))


if __name__ == "__main__":
    write(sys.argv[1])
