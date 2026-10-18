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

VERSION = 9
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
    f.setb(x, G, z, f"create:creative_motor[facing=up]{{ScrollValue:{rpm}}}")


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
    for x in (-4, 16, 36, 56):
        f.fill(x, -60, 31, x, -55, 31, "polished_blackstone_wall")
        f.setb(x, -54, 31, "polished_blackstone")
        f.setb(x, -54, 32, PC + "industrial_light_street[facing=north,lit=true]")
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
        f.setb(x, TOP - 1, z, PC + "industrial_light[lit=true]")

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
            f.setb(x, TOP - 1, z, PC + "industrial_light[lit=true]")

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


# ------------------------------------------------------------------------------------------------ prime / refresh

def prime(f):
    f("execute as @a run function filmset:kit")
    f("function filmset:refresh")
    for bx in BOILERS:
        for _ in range(3):
            f(f"pc use {bx} -59 49 minecraft:coal")
        f(f"pc use {bx} -59 49 minecraft:flint_and_steel")
    f("schedule function filmset:open_valve 200t")


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
    load(f"execute unless score #built filmset matches {VERSION} run schedule function filmset:build 40t")
    fns["load"] = load

    build = Fn()
    for part in ("clear", "yard", "boiler_house", "foundry"):
        build(f"function filmset:{part}")
    build(f"scoreboard players set #built filmset {VERSION}")
    build("schedule function filmset:prime 40t")
    fns["build"] = build

    for name, fn in (("clear", clear), ("yard", yard), ("boiler_house", boiler_house), ("foundry", foundry),
                     ("prime", prime), ("refresh", refresh)):
        f = Fn()
        fn(f)
        fns[name] = f

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
