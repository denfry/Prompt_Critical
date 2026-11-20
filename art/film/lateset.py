"""Film set of the later eras for the video guide: steel and power (I1), oil and gas (I2), chemistry (I3),
nuclear (I4) with a control room. Called from filmset.write; south of the I0 set, z 120..230.

Machines are placed as in their game tests: one setblock of the controller builds the footprint (PcBlock.onPlace),
gametest coordinates map to the set as (ox + x, y - 62, oz + z) — gametest floor y = 1 is the set floor y = -61.
Rotation comes from Create creative motors with ScrollValue set in NBT.
"""
from filmset import G, PC, rng

Z1, Z2 = 120, 232
X1, X2 = -12, 150


def motor(f, x, y, z, facing, rpm=64):
    f.setb(x, y, z, f"create:creative_motor[facing={facing}]{{ScrollValue:{rpm}}}")


def pad(f, x1, z1, x2, z2, block="smooth_stone", scatter=("polished_andesite", "andesite", "gravel"), n=60):
    f.fill(x1, G, z1, x2, G, z2, block)
    for _ in range(n):
        f.setb(rng.randint(x1, x2), G, rng.randint(z1, z2), rng.choice(scatter))


def lights(f, x1, z1, x2, z2, y, step=4):
    for x in range(x1, x2 + 1, step):
        for z in range(z1, z2 + 1, step):
            f.setb(x, y, z, PC + "industrial_light[lit=true]")


def hall(f, x1, z1, x2, z2, top, wall=PC + "corrugated_steel", open_side=None):
    """Steel-clad hall with a flat roof, roof lights and window bands; open_side ('north'...) left without wall."""
    pad(f, x1, z1, x2, z2, PC + "painted_concrete", (PC + "painted_concrete",), 0)
    sides = {"west": (x1, z1, x1, z2), "east": (x2, z1, x2, z2), "north": (x1, z1, x2, z1), "south": (x1, z2, x2, z2)}
    for name, (a, b, c, d) in sides.items():
        if name == open_side:
            continue
        f.fill(a, -60, b, c, top, d, wall)
        f.fill(a, -56, b, c, -55, d, PC + "glass_block_frosted")
    f.fill(x1, top + 1, z1, x2, top + 1, z2, wall)
    lights(f, x1 + 2, z1 + 2, x2 - 2, z2 - 2, top, 5)
    for x in range(x1 + 2, x2 - 1, 4):  # wall lights low on the long walls: the halls read at any time of day
        for z in (z1 + 1, z2 - 1):
            if not (z == z1 + 1 and open_side == "north"):
                f.setb(x, -55, z, PC + "industrial_light[lit=true]")


# ------------------------------------------------------------------------------------------------ I1 steel & power

def steel(f):
    ox, oz = 0, 128
    pad(f, ox - 4, oz - 4, ox + 44, oz + 22)
    # blast furnace as in IronworksGameTests.buildFurnace
    def at(x, y, z):
        return ox + x, y - 62, oz + z
    for y in range(1, 13):
        for x in range(3, 8):
            for z in range(3, 8):
                if y == 1 or x in (3, 7) or z in (3, 7):
                    f.setb(*at(x, y, z), PC + "blast_furnace_shell")
    f.setb(*at(5, 2, 3), PC + "blast_furnace_taphole[facing=north,open=true]")
    for x, z, d in ((5, 3, "north"), (5, 7, "south"), (3, 5, "west"), (7, 5, "east")):
        f.setb(*at(x, 3, z), PC + f"blast_furnace_tuyere[facing={d},burnt=false]")
    f.setb(*at(5, 13, 5), PC + "blast_furnace_top")
    f.setb(*at(2, 4, 5), PC + "blast_furnace_controller[facing=west]")
    # cast house runner: molten iron from the taphole in a refractory trench
    tx, _, tz = at(5, 2, 3)
    for z in range(tz - 6, tz):
        f.setb(tx - 1, G, z, "nether_bricks")
        f.setb(tx + 1, G, z, "nether_bricks")
        f.setb(tx, G - 1, z, "nether_bricks")
        f.setb(tx, G, z, "lava")
    # top gas: signal smoke over the throat
    for x, z in ((3, 3), (7, 7), (3, 7)):
        f.setb(*at(x, 12, z), PC + "blast_furnace_shell")
        f.setb(*at(x, 13, z), "campfire[lit=true,signal_fire=true]")
    # three hot blast stoves in a row east of the furnace
    for i in range(3):
        f.setb(ox + 11 + 4 * i, -59, oz + 5, PC + "hot_blast_stove[facing=north]")
    # BOF converter with its oxygen lance
    f.setb(ox + 25, -60, oz + 5, PC + "bof_vessel[facing=north,running=true]")
    f.setb(ox + 25, -58, oz + 5, PC + "oxygen_lance[lowered=true]")
    # generator row: shaft generators with exciters, driven from the back
    for i in range(3):
        x = ox + 32 + 4 * i
        f.setb(x, -60, oz + 5, PC + "shaft_generator[facing=north]")
        f.setb(x, -59, oz + 5, PC + "generator_exciter[facing=north]")
        motor(f, x, -60, oz + 6, "north")
        f.setb(x, -60, oz + 1, PC + "distribution_transformer[facing=north]")
    # overhead line on concrete poles along the back of the pad
    for x in range(ox - 2, ox + 46, 8):
        z = oz + 18
        f.setb(x, -60, z, PC + "concrete_pole[part=base]")
        for y in range(-59, -52):
            f.setb(x, y, z, PC + "concrete_pole[part=middle]")
        f.setb(x, -52, z, PC + "concrete_pole[part=top]")
        f.setb(x, -51, z, PC + "wire_connector_mv")


# ------------------------------------------------------------------------------------------------ I2 oil & gas

def oil(f):
    ox, oz = 52, 128
    pad(f, ox - 4, oz - 4, ox + 54, oz + 22, "coarse_dirt", ("gravel", "dirt", "packed_mud"), 120)

    def at(x, y, z):
        return ox + x, y - 62, oz + z
    n = "[facing=north]"
    # drilling rig as in DrillingGameTests.buildRig
    f.setb(*at(1, 2, 1), PC + "drillers_console[facing=north,lit=true]")
    f.setb(*at(6, 2, 6), PC + "rotary_table" + n)
    motor(f, *at(5, 2, 6), "east")
    for k in (3, 6, 9):
        f.setb(*at(6, 2 + k, 6), PC + "derrick_segment" + n)
    f.setb(*at(6, 2, 9), PC + "drawworks" + n)
    f.setb(*at(3, 2, 9), PC + "mud_pump" + n)
    motor(f, *at(1, 2, 10), "east")
    f.setb(*at(8, 2, 6), PC + "bop_annular[facing=north,shut=false]")
    f.setb(*at(8, 2, 3), PC + "choke_manifold" + n)
    for x in range(1, 7):
        f.setb(*at(x, 2, 4), PC + "mud_tank[facing=north,level=2]")
    f.setb(*at(10, 2, 1), PC + "mud_mixing_hopper" + n)
    f.setb(*at(4, 2, 1), PC + "bop_accumulator" + n)
    # oil field: three pumpjacks on wellheads, motors on the port side
    for i in range(3):
        x, z = ox + 20 + 7 * i, oz + 8 + (i % 2) * 4
        f.setb(x + 2, -60, z, PC + "wellhead" + n)
        f.setb(x + 2, -59, z, PC + "christmas_tree[facing=north,open=true]")
        f.setb(x, -60, z, PC + "pumpjack" + n)
        motor(f, x - 2, -60, z, "east")
    # crude unit: fired heater and the column of tray sections
    f.setb(ox + 44, -59, oz + 6, PC + "tube_furnace[facing=north,lit=true]")
    f.setb(ox + 48, -60, oz + 6, PC + "crude_column" + n)
    for y in range(-58, -44, 2):
        f.setb(ox + 48, y, oz + 6, PC + "column_tray_section" + n)
    f.setb(ox + 48, -44, oz + 6, PC + "column_top" + n)        # the column counts its sections only under a top
    # flare stack with a burning tip
    for y in range(-60, -42):
        f.setb(ox + 52, y, oz + 16, PC + "flare_stack")
    f.setb(ox + 52, -42, oz + 16, PC + "flare_tip[lit=true]")


# ------------------------------------------------------------------------------------------------ I3 chemistry

def chem(f):
    ox, oz = 112, 128
    pad(f, ox - 4, oz - 4, ox + 30, oz + 40)
    for i in range(3):
        f.setb(ox + 3 + 5 * i, -59, oz + 5, PC + "pyrolysis_furnace[facing=north]")
    f.setb(ox + 20, -59, oz + 5, PC + "cracked_gas_unit[facing=north,running=true]")  # cells y -1..1: controller one above the floor
    # combined cycle: gas turbine exhausting into the HRSG (CombinedCycleGameTests: GT z+0, HRSG z+9)
    f.setb(ox + 10, -59, oz + 18, PC + "gas_turbine[facing=north]")
    f.setb(ox + 10, -59, oz + 16, PC + "turbine_gearbox[facing=north]")
    f.setb(ox + 10, -59, oz + 27, PC + "hrsg[facing=north]")
    f.fill(ox + 10, -54, oz + 27, ox + 10, -44, oz + 27, PC + "chimney")


def chem_prime(f):
    ox, oz = 112, 128
    for i in range(3):
        f(f"pc panel {ox + 3 + 5 * i} -59 {oz + 5} 1")  # decoke: furnace lit without feed


# ------------------------------------------------------------------------------------------------ I4 nuclear + control room

def nuclear(f):
    # enrichment hall: the cascade console and rows of centrifuges
    ox, oz = 0, 184
    hall(f, ox, oz, ox + 32, oz + 18, -52, open_side="north")
    f.setb(ox + 16, -60, oz + 2, PC + "centrifuge_cascade[facing=north,running=true]")
    motor(f, ox + 16, -60, oz + 3, "north")
    for x in range(ox + 3, ox + 30):
        for z in range(oz + 6, oz + 17, 2):
            f.setb(x, -60, z, PC + "gas_centrifuge[facing=north]")
    # reactor hall: PWR vessel, pressurizer, two main coolant pumps
    rx, rz = 44, 184
    hall(f, rx, rz, rx + 22, rz + 22, -44, wall="smooth_stone", open_side="north")
    f.setb(rx + 11, -59, rz + 11, PC + "pwr_reactor[facing=north]")
    f.setb(rx + 17, -60, rz + 8, PC + "pressurizer[facing=north,lit=true]")
    for x in (rx + 5, rx + 8):
        f.setb(x, -60, rz + 15, PC + "primary_pump[facing=north]")
        motor(f, x, -60, rz + 16, "north")
    f.fill(rx + 1, -46, rz + 1, rx + 21, -46, rz + 1, "iron_bars")      # crane rails
    f.fill(rx + 1, -46, rz + 21, rx + 21, -46, rz + 21, "iron_bars")
    f.fill(rx + 11, -46, rz + 1, rx + 11, -46, rz + 21, "yellow_concrete")  # polar crane bridge
    # control room east of the reactor hall
    cx, cz = 70, 186
    hall(f, cx, cz, cx + 14, cz + 12, -55, open_side="north")
    f.fill(cx + 2, -60, cz + 10, cx + 12, -60, cz + 10, PC + "control_desk[facing=north]")
    f.setb(cx + 7, -60, cz + 8, "computercraft:computer_advanced[facing=north]{ComputerId:0,On:1b}")
    f.setb(cx + 6, -60, cz + 8, PC + "annunciator_panel[facing=north]")
    f.setb(cx + 7, -59, cz + 8, "computercraft:monitor_advanced[facing=north]")
    for x in (cx + 1, cx + 13):
        f.setb(x, -60, cz + 4, PC + "relay_cabinet[facing=" + ("east" if x == cx + 1 else "west") + "]")
    f.setb(cx + 3, -60, cz + 6, PC + "plc[facing=north,running=true]")
    f.setb(cx + 7, -57, cz + 1, PC + "room_sign_control[facing=south]")


def startup(world):
    """CC: Tweaked computer 0 boots into the pcui alarm screen."""
    import os
    d = os.path.join(world, "computercraft", "computer", "0")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "startup.lua"), "w") as fh:
        fh.write('shell.run("/rom/programs/pc/alarm_screen")\n')


def clear(f):
    f.fill(X1, -60, Z1, X2, -20, Z2, "air")
    f.fill(X1, -63, Z1, X2, -62, Z2, "dirt")
    f.fill(X1, G, Z1, X2, G, Z2, "grass_block")


PARTS = (("late_clear", clear), ("steel", steel), ("oil", oil), ("chem", chem), ("nuclear", nuclear))
