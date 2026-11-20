"""The overview film's plant world: one large works on real terrain (coast, river, hills), every machine of the mod.

    python art/film/plantworld.py <game dir>          # reads <game dir>/plant_site.json, writes <game dir>/plant.txt

The site comes from `/film heightmap` (pc-mod/src/gametest/.../film/FilmSite.java): surface, floor and biome of the
chosen window, sampled from the generator before any chunk exists. This script terraces the ground under each district,
builds the districts and writes one command per line; `/film run plant` (FilmBuild.java) executes them, generating the
chunks each command touches.

Units already designed for the superflat set (filmset I0, lateset, works) are reused through Shift, which moves every
coordinate of their commands to a terrace of the plant.
"""
import json
import math
import os
import re
import sys

from filmset import Fn

HERE = os.path.dirname(os.path.abspath(__file__))
FLAT_FLOOR = -60          # the superflat sets stand on y = -60 (floor layer -61)


# ------------------------------------------------------------------------------------------------------ site

class Site:
    """Ground of the chosen window: floor (terrain under water), surface (water top) and biome, with bilinear lookup."""

    def __init__(self, path):
        d = json.load(open(path, encoding="utf-8"))
        self.seed, self.sea = d["seed"], d["sea"]
        self.x1, self.z1, self.step, self.nx, self.nz = d["x1"], d["z1"], d["step"], d["nx"], d["nz"]
        self.surface, self.floor_, self.biome_ = d["surface"], d["floor"], d["biome"]
        self.biomes = d["biomes"]

    def _k(self, ix, iz):
        ix = min(max(ix, 0), self.nx - 1)
        iz = min(max(iz, 0), self.nz - 1)
        return ix * self.nz + iz

    def _lerp(self, arr, x, z):
        fx, fz = (x - self.x1) / self.step, (z - self.z1) / self.step
        ix, iz = math.floor(fx), math.floor(fz)
        tx, tz = fx - ix, fz - iz
        a, b = arr[self._k(ix, iz)], arr[self._k(ix + 1, iz)]
        c, e = arr[self._k(ix, iz + 1)], arr[self._k(ix + 1, iz + 1)]
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + e * tx) * tz

    def floor(self, x, z):
        """Top solid block (y of the highest terrain block)."""
        return round(self._lerp(self.floor_, x, z)) - 1

    def water(self, x, z):
        return self._lerp(self.surface, x, z) - self._lerp(self.floor_, x, z) > 0.5

    def biome(self, x, z):
        return self.biomes[self.biome_[self._k(round((x - self.x1) / self.step), round((z - self.z1) / self.step))]]

    def stats(self, x1, z1, x2, z2, step=4):
        hs = [self.floor(x, z) for x in range(x1, x2 + 1, step) for z in range(z1, z2 + 1, step)]
        wet = sum(self.water(x, z) for x in range(x1, x2 + 1, step) for z in range(z1, z2 + 1, step))
        hs.sort()
        return {"min": hs[0], "med": hs[len(hs) // 2], "max": hs[-1], "wet": wet / len(hs)}


class Sites:
    """Several sampled windows as one ground: each lookup goes to the first window that covers the point."""

    def __init__(self, sites):
        self.sites = sites
        self.sea = sites[0].sea

    def _pick(self, x, z):
        for s in self.sites:
            if s.x1 <= x <= s.x1 + (s.nx - 1) * s.step and s.z1 <= z <= s.z1 + (s.nz - 1) * s.step:
                return s
        return self.sites[0]

    def floor(self, x, z):
        return self._pick(x, z).floor(x, z)

    def water(self, x, z):
        return self._pick(x, z).water(x, z)

    def biome(self, x, z):
        return self._pick(x, z).biome(x, z)

    def stats(self, x1, z1, x2, z2, step=4):
        return self._pick((x1 + x2) // 2, (z1 + z2) // 2).stats(x1, z1, x2, z2, step)


# ------------------------------------------------------------------------------------------------- terraform

def terrace(f, site, x1, z1, x2, z2, y, top="grass_block", fill="dirt", margin=10, wall=None, protect=()):
    """Level ground at y (top block at y) over the rectangle; blend into the terrain over `margin` blocks around it.

    Inside: everything above y is cleared to air (hills, trees), everything below is filled down to the old ground.
    Around: each column ramps linearly from y to the natural ground, so the terrace sits in the land like a cut and
    a bank, not a box. With `wall`, sides where the cut or bank is steeper than 1:1 get a retaining wall instead.
    """
    f(f"# terrace {x1} {z1} {x2} {z2} at {y}")
    for x in range(x1 - margin, x2 + margin + 1):
        for z in range(z1 - margin, z2 + margin + 1):
            dx = max(x1 - x, 0, x - x2)
            dz = max(z1 - z, 0, z - z2)
            d = math.hypot(dx, dz)
            if d > margin or inside(protect, x, z):
                continue
            g = site.floor(x, z)
            if d == 0:
                h = y
            else:
                t = d / margin
                t = t * t * (3 - 2 * t)          # smoothstep: soft toe and crest
                h = round(y + (g - y) * t)
            if wall and 0 < d <= 1.5 and abs(g - y) > 3:
                lo, hi = min(g, y), max(g, y)
                f.fill(x, lo - 2, z, x, hi, z, wall)
                continue
            if h < g:                            # cut: clear above, new top below
                f.fill(x, h + 1, z, x, g + 12, z, "air")
            elif h > g:                          # bank: build up
                f.fill(x, g - 1, z, x, h - 1, z, fill)
            else:
                f.fill(x, h + 1, z, x, h + 12, z, "air")   # trees and plants on the level ground
            f.setb(x, h, z, top if d == 0 or h >= site.sea else "sand")


def inside(rects, x, z):
    """True when (x, z) lies in one of the rectangles (x1, z1, x2, z2) already built on."""
    return any(a <= x <= c and b <= z <= d for a, b, c, d in rects)


# ------------------------------------------------------------------------------------------------------ shift

# commands with block coordinates after a known prefix (setblock and fill go through Fn.setb / Fn.fill)
_COORD_CMDS = re.compile(r"^(pc use |pc panel |data merge block |place feature \S+ |film prime \S+ |tp @s |summon \S+ )"
                         r"(-?\d+) (-?\d+) (-?\d+)(.*)$")


class Shift(Fn):
    """Fn that moves every coordinate by (dx, dy, dz) into a target Fn: reuse of a set built elsewhere."""

    def __init__(self, target, dx, dy, dz):
        super().__init__()
        self.t, self.d = target, (dx, dy, dz)

    def __call__(self, line):
        m = _COORD_CMDS.match(line)
        if m:
            x, y, z = (int(m.group(i)) + self.d[i - 2] for i in (2, 3, 4))
            line = f"{m.group(1)}{x} {y} {z}{m.group(5)}"
        elif line.startswith(("setblock", "fill", "clone", "forceload", "kill", "execute")):
            raise ValueError("raw coordinate command, use setb/fill: " + line)
        self.t(line)

    def setb(self, x, y, z, block):
        dx, dy, dz = self.d
        self.t.setb(x + dx, y + dy, z + dz, block)

    def fill(self, x1, y1, z1, x2, y2, z2, block, mode=""):
        dx, dy, dz = self.d
        self.t.fill(x1 + dx, y1 + dy, z1 + dz, x2 + dx, y2 + dy, z2 + dz, block, mode)


# ------------------------------------------------------------------------------------------------- layout v1
# World coordinates on seed 62 (sea 63). The map: python art/film/sitemap.py <game>/plant_site.json out.png 32.
# North of z 600 is the sea; the central lowland (z 640..900) carries the works; the hills of x -200..-80, z 880..1100
# carry the wind farm; the river channel at x 90..116, z 1090..1152 feeds the hydro plant.

PC = "promptcritical:"
SITE_FILE = "plant_site.json"

# reused sets: (local rect x1 z1 x2 z2 incl. margins, world x of local x1, world z of local z1)
SETS = {
    "i0": ((-22, -2, 152, 112), -522, 828),
    "steel": ((-6, 122, 48, 148), -112, 758),
    "chem": ((106, 122, 144, 170), -202, 712),
    "oil": ((46, 122, 108, 150), -362, 798),
    "nuclear": ((-2, 182, 86, 206), -154, 608),
}


def ground_y(site, rect):
    return max(site.sea + 2, site.stats(*rect)["med"])


class Plant:
    def __init__(self, site):
        self.s = site
        self.f = Fn()
        self.prime = Fn()
        self.ys = {}
        self.computers = {}
        self.protect = []          # rectangles already built on: later terraces and roads leave them alone

    def terrace(self, x1, z1, x2, z2, y, **kw):
        terrace(self.f, self.s, x1, z1, x2, z2, y, protect=list(self.protect), **kw)
        self.protect.append((x1, z1, x2, z2))

    # -- helpers ------------------------------------------------------------------------------------------------
    def world_rect(self, key):
        (x1, z1, x2, z2), wx, wz = SETS[key]
        return wx, wz, wx + (x2 - x1), wz + (z2 - z1)

    def offset(self, key):
        (x1, z1, _, _), wx, wz = SETS[key]
        return wx - x1, self.ys[key] + 61, wz - z1      # superflat floor layer -61 -> terrace top y

    def terrace_set(self, key, top="grass_block"):
        r = self.world_rect(key)
        y = ground_y(self.s, r)
        self.ys[key] = y
        self.terrace(*r, y, top=top, margin=12)
        return y

    def column_top(self, x, z, y, block, clear=8):
        """One column: ground (or causeway over water) up to y with `block` on top, air above."""
        if inside(self.protect, x, z):
            return
        g = self.s.floor(x, z)
        if g < y:
            self.f.fill(x, g, z, x, y - 1, z, "stone" if self.s.water(x, z) else "dirt")
        self.f.fill(x, y + 1, z, x, max(y, g) + clear, z, "air")
        self.f.setb(x, y, z, block)

    def road(self, pts, width=5, block="gravel", edge="andesite"):
        """Road through the points, following the ground (smoothed), causeway over water."""
        half = width // 2
        for (ax, az), (bx, bz) in zip(pts, pts[1:]):
            n = max(abs(bx - ax), abs(bz - az))
            prev = None
            for i in range(n + 1):
                x = round(ax + (bx - ax) * i / n)
                z = round(az + (bz - az) * i / n)
                y = max(self.s.sea + 1, round(sum(self.s.floor(x + dx, z + dz) for dx in (-4, 0, 4) for dz in (-4, 0, 4)) / 9))
                if prev is not None:
                    y = max(prev - 1, min(prev + 1, y))
                prev = y
                for k in range(-half, half + 1):
                    cx, cz = (x, z + k) if abs(bx - ax) >= abs(bz - az) else (x + k, z)
                    self.column_top(cx, cz, y, edge if abs(k) == half else block)

    def pole_line(self, pts, step=24):
        """Overhead line on concrete poles beside a road (5 to the side)."""
        i = 0
        for (ax, az), (bx, bz) in zip(pts, pts[1:]):
            n = max(abs(bx - ax), abs(bz - az))
            for t in range(n):
                if i % step == 0:
                    x = round(ax + (bx - ax) * t / n)
                    z = round(az + (bz - az) * t / n)
                    if abs(bx - ax) >= abs(bz - az):
                        z += 5
                    else:
                        x += 5
                    if not self.s.water(x, z) and not inside(self.protect, x, z):
                        y = self.s.floor(x, z)
                        self.f.setb(x, y + 1, z, PC + "concrete_pole[part=base]")
                        for yy in range(y + 2, y + 9):
                            self.f.setb(x, yy, z, PC + "concrete_pole[part=middle]")
                        self.f.setb(x, y + 9, z, PC + "concrete_pole[part=top]")
                        self.f.setb(x, y + 10, z, PC + "wire_connector_mv")
                i += 1

    def low_lights(self, f, x1, z1, x2, z2, floor, ceiling, step=5, drop=6):
        """Pendant lamps hung `drop` above the floor on chains from the ceiling, a grid every `step`; only into air,
        so machines keep their place. Light falls off one level a block: lamps under the roof of a tall hall never
        reach the floor, these do."""
        ly = floor + drop
        for x in range(x1 + 2, x2 - 1, step):
            for z in range(z1 + 2, z2 - 1, step):
                f.fill(x, ly + 1, z, x, ceiling - 1, z, "chain[axis=y]", "keep")
                f.fill(x, ly, z, x, ly, z, PC + "industrial_light[lit=true]", "keep")

    def computer(self, x, y, z, facing, program):
        cid = 1 + len(self.computers)
        self.computers[cid] = program
        self.f.setb(x, y, z, f"computercraft:computer_advanced[facing={facing}]{{ComputerId:{cid},On:1b}}")
        self.f.setb(x, y + 1, z, f"computercraft:monitor_advanced[facing={facing}]")

    # -- reused sets --------------------------------------------------------------------------------------------
    def sets(self):
        import filmset
        import lateset
        for key in SETS:
            self.terrace_set(key, top="coarse_dirt" if key == "oil" else "grass_block")
        i0 = Shift(self.f, *self.offset("i0"))
        for part in (filmset.yard, filmset.boiler_house, filmset.foundry, filmset.mine):
            part(i0)
        st = Shift(self.f, *self.offset("steel"))
        lateset.steel(st)
        # coke battery: 3 ovens x 4, as IronworksGameTests.buildBattery, test origin (3, 1, 2) at local (10, -61, 139)
        for z in range(1, 8):
            for x in range(3, 7):
                for y in range(1, 4):
                    st.setb(x + 7, y - 62, z + 137, PC + ("coke_battery_wall" if z % 2 else "coke_oven"))
        for z in (2, 4, 6):
            st.setb(3 + 7, 4 - 62, z + 137, PC + "gas_collector_main")
        st.fill(17, -57, 143, 17, -45, 143, PC + "chimney")                       # battery stack
        # blast blower (08 §8.5.4) within LINK_RANGE of the furnace: cold blast lights the coke on the hearth.
        # Shaft enters at the front (north), so the creative motor sits just north of it, facing it.
        st.setb(0, -60, 136, PC + "blast_blower[facing=north,running=true]")
        st.setb(0, -60, 135, "create:creative_motor[facing=south]{ScrollValue:64}")
        self.steel_works(st)
        lateset.chem(Shift(self.f, *self.offset("chem")))
        self.chem_works(Shift(self.f, *self.offset("chem")))
        lateset.oil(Shift(self.f, *self.offset("oil")))
        # electrostatic desalter within its FURNACE_RADIUS (6) of the crude unit's tube furnace (96,-59,134)
        Shift(self.f, *self.offset("oil")).setb(93, -60, 130, PC + "electrostatic_desalter[facing=north]")
        n = Shift(self.f, *self.offset("nuclear"))
        lateset.nuclear(n)
        self.reactor_hall(n)
        for hx1, hz1, hx2, hz2, top in ((0, 184, 32, 202, -52), (44, 184, 66, 206, -44), (70, 186, 84, 198, -55)):
            self.low_lights(n, hx1, hz1, hx2, hz2, -61, top, step=6)
        # priming at the shifted positions, as filmset:prime and works.prime do on the flat set
        p = self.prime
        i0p = Shift(p, *self.offset("i0"))
        filmset.refresh(i0p)
        for bx in filmset.BOILERS:
            i0p(f"film prime boiler_water {bx} -59 49")
            for _ in range(3):
                i0p(f"pc use {bx} -59 49 minecraft:coal")
            i0p(f"pc use {bx} -59 49 minecraft:flint_and_steel")
        i0p(f"pc use {filmset.HOIST[0]} {filmset.HOIST[1]} {filmset.HOIST[2]} promptcritical:wire_rope 16")
        i0p(f"pc panel {filmset.BOILERS[-1] + 3} -59 49 0")
        lateset.chem_prime(Shift(p, *self.offset("chem")))
        for i in range(3):                                                       # then propane on: ethylene, not decoke
            Shift(p, *self.offset("chem"))(f"film prime pyrolysis {115 + 5 * i} -59 133")
        Shift(p, *self.offset("chem"))("film prime turbine 122 -59 146")
        Shift(p, *self.offset("nuclear"))("film prime reactor_fuel 55 -59 195")
        p("wait 100")                                                            # pumps up to flow before the rods move
        Shift(p, *self.offset("nuclear"))("film prime reactor_power 55 -59 195")
        Shift(p, *self.offset("nuclear"))("film prime cascade 16 -60 186")
        for i in range(3):
            Shift(p, *self.offset("steel"))(f"film prime generator {32 + 4 * i} -60 133")
        Shift(p, *self.offset("oil"))("film prime drill 53 -60 129")             # kick is for a single shot: it blows out
        Shift(p, *self.offset("oil"))("film prime unit 93 -60 130")
        Shift(p, *self.offset("oil"))("film prime refinery 100 -60 134")
        Shift(p, *self.offset("steel"))("film prime coke 10 -61 139")
        Shift(p, *self.offset("steel"))("film prime blast_furnace 2 -58 133")

    def steel_works(self, st):
        """Dress the I1 steel set (lateset.steel, local superflat coords: pad top -61, machines from -60) as an
        ironworks rather than a test stand: cast house over the taphole and runner, bustle pipe and hot-blast main
        from the stoves, an inclined skip gallery from the stockhouse to the furnace top, a downcomer to a dust
        catcher (a real device: the furnace controller takes it within LINK_RANGE), and a converter shop."""
        steel, dark, frame = "gray_concrete", "polished_deepslate", "polished_blackstone"
        # cast house: open-sided steel frame over the taphole (5,-60,131) and the iron runner (x 4..6, z 125..130)
        x1, z1, x2, z2, top = 0, 121, 10, 130, -53
        for x in range(x1, x2 + 1, 5):
            for z in (z1, z2):
                st.fill(x, -60, z, x, top - 1, z, frame)
        st.fill(x1, top, z1, x2, top, z2, "dark_oak_planks")
        st.fill(x1, top + 1, z1 + 1, x2, top + 1, z2 - 1, "deepslate_tile_slab")        # low pitched roof
        st.fill(x1, top, z1, x2, top, z1, "red_terracotta")
        st.fill(x1, top - 2, z1, x2, top - 1, z1, steel)                               # deep fascia over the open side
        st.fill(x2, -60, z1 + 1, x2, top - 1, z2 - 1, steel)                           # closed east wall
        st.fill(x2, -57, z1 + 2, x2, -56, z2 - 2, PC + "glass_block_frosted")
        self.low_lights(st, x1 + 1, z1 + 1, x2 - 1, z2 - 1, -61, top - 1, step=4, drop=2)
        st.setb(3, -60, 129, PC + "cast_house_machine[facing=north,kind=drill]")        # drill and mud gun at the taphole
        st.setb(7, -60, 129, PC + "cast_house_machine[facing=north,kind=mud_gun]")
        # bustle pipe one above the tuyeres (y -59), with a stock down to each, and the hot-blast main from the stoves
        for x in range(2, 9):
            for z in range(130, 137):
                if x in (2, 8) or z in (130, 136):
                    st.setb(x, -57, z, dark)
        for x, z in ((5, 130), (5, 136), (8, 133)):                                     # west one is the controller
            st.setb(x, -58, z, frame)
        st.fill(8, -57, 130, 20, -57, 130, dark)
        for x in (11, 15, 19):                                                          # stove outlets
            st.setb(x, -57, 131, dark)
        for x in range(9, 21, 4):                                                       # pipe trestles
            st.fill(x, -60, 130, x, -58, 130, "iron_bars")
        # skip gallery: stockhouse at the foot (z 147..150), incline up to the throat (shell ring at y -50, z 135)
        st.fill(2, -60, 147, 8, -55, 151, "light_gray_concrete")
        st.fill(3, -60, 148, 7, -56, 150, "air")
        st.fill(2, -54, 147, 8, -54, 151, "dark_oak_planks")
        st.fill(3, -60, 147, 7, -57, 147, "air")                                        # loading door under the gallery
        for x in range(3, 8, 2):
            st.fill(x, -60, 151, x, -58, 151, PC + "glass_block_frosted")
        for z in range(136, 147):
            y = -50 - round((z - 136) * 6 / 10)                                        # 6 down over 10: a steep skip
            st.fill(4, y, z, 5, y, z, "smooth_stone_slab[type=top]" if z % 2 else "iron_block")
            for x in (3, 6):
                if not (x == 6 and z == 146):                                           # overhead line pole there
                    st.fill(x, y + 1, z, x, y + 2, z, "iron_bars")
            st.fill(4, y + 3, z, 5, y + 3, z, "smooth_stone_slab")                      # gallery roof
            if z % 4 == 0 and z > 136:                                                  # z 136 is over the bustle pipe
                st.fill(4, -60, z, 4, y - 1, z, "iron_bars")
                st.fill(5, -60, z, 5, y - 1, z, "iron_bars")
        # downcomer: up from the throat, west over the cast house side, down into the dust catcher
        st.fill(3, -47, 133, -1, -47, 133, dark)
        st.fill(-1, -47, 128, -1, -47, 132, dark)
        st.fill(-1, -57, 128, -1, -48, 128, dark)
        st.setb(-2, -59, 127, PC + "dust_catcher[facing=north]")                        # cells x -2..-1, y -60..-58, z 127..128
        for y in (-46, -45):                                                            # bleeder valves on the uptake
            st.setb(3, y, 133, frame)
        st.setb(3, -44, 133, "campfire[lit=true,signal_fire=false]")
        # converter shop around the BOF (25,-60,133; 3x3x2) and its lance
        bx1, bz1, bx2, bz2, btop = 21, 126, 29, 140, -46
        st.fill(bx1, -60, bz1, bx2, btop, bz2, steel)
        st.fill(bx1 + 1, -60, bz1 + 1, bx2 - 1, btop - 1, bz2 - 1, "air")
        st.fill(bx1 + 1, -61, bz1 + 1, bx2 - 1, -61, bz2 - 1, PC + "painted_concrete")
        for x in range(bx1, bx2 + 1, 4):
            st.fill(x, -60, bz1, x, btop + 1, bz1, frame)
            st.fill(x, -60, bz2, x, btop + 1, bz2, frame)
        for z in range(bz1 + 1, bz2):
            if (z - bz1) % 4:
                st.fill(bx1, -52, z, bx1, -50, z, PC + "glass_block_frosted")
                st.fill(bx2, -52, z, bx2, -50, z, PC + "glass_block_frosted")
        st.fill(bx1, btop + 1, bz1, bx2, btop + 1, bz2, "light_gray_concrete")
        st.fill(bx1 + 3, -60, bz1, bx2 - 3, -55, bz1, "air")                           # ladle door, north
        st.fill(bx1 + 1, -50, bz1 + 1, bx2 - 1, -50, bz1 + 1, "yellow_concrete")       # crane runway
        st.fill(bx1 + 1, -50, bz2 - 1, bx2 - 1, -50, bz2 - 1, "yellow_concrete")
        st.fill(bx1 + 1, -49, bz1 + 1, bx1 + 1, -49, bz2 - 1, "yellow_concrete")       # crane bridge
        st.setb(25, -60, 133, PC + "bof_vessel[facing=north,running=true]")             # re-place: the shell carved it
        st.setb(25, -58, 133, PC + "oxygen_lance[lowered=true]")
        st.fill(25, -57, 133, 25, btop, 133, "chain")
        st.fill(25, btop + 1, 133, 25, btop + 6, 133, PC + "chimney")                    # off-gas stack
        self.low_lights(st, bx1 + 1, bz1 + 1, bx2 - 1, bz2 - 1, -61, btop - 1, step=4, drop=3)

    def chem_works(self, c):
        """Dress the I3 chemistry set (lateset.chem, local coords: pad top -61) as an ethylene plant: stacks over the
        pyrolysis furnaces (x 114..126, z 132..134), the cold end of fractionation columns beside the cracked gas
        unit, ethylene and propylene spheres, gas bullets, a power block over the gas turbine (x 121..123,
        z 145..153; the HRSG at z 154..156 stays outside) and a flare."""
        for x in (115, 120, 125):                                                       # furnace stacks
            c.fill(x, -57, 133, x, -44, 133, PC + "chimney")
        c.fill(113, -60, 136, 127, -57, 137, "bricks")                                  # convection section behind
        c.fill(113, -56, 136, 127, -56, 137, "nether_brick_slab[type=bottom]")
        # cold end: quench tower, demethanizer, deethanizer, C2 splitter (tray-section shells, tallest last)
        n = "[facing=north]"
        for x, z, k in ((136, 132, 4), (139, 136, 6), (136, 140, 7), (140, 142, 9)):
            c.setb(x, -60, z, "polished_andesite")
            for i in range(k):
                c.setb(x, -59 + 2 * i, z, PC + "column_tray_section" + n)
            c.setb(x, -59 + 2 * k, z, PC + "column_top" + n)
        c.fill(130, -56, 136, 138, -56, 136, "polished_deepslate")                      # overhead line to the columns
        c.fill(130, -60, 136, 130, -57, 136, "iron_bars")
        # ethylene and propylene spheres on legs, and a row of gas bullets
        for sx, sz, r in ((112, 158, 3), (120, 160, 3), (129, 160, 2)):
            cy = -55
            for dx in range(-r, r + 1):
                for dy in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if r - 0.9 <= (dx * dx + dy * dy + dz * dz) ** 0.5 <= r + 0.3:
                            c.setb(sx + dx, cy + dy, sz + dz, "white_concrete")
            for dx, dz in ((-r + 1, -r + 1), (r - 1, -r + 1), (-r + 1, r - 1), (r - 1, r - 1)):
                c.fill(sx + dx, -60, sz + dz, sx + dx, cy - r + 1, sz + dz, "iron_bars")
        for x in (110, 114, 118):
            c.setb(x, -60, 138, PC + "gas_storage_bullet[facing=north,alarm=false]")      # cells z 138..139
        # power block: hall over the gas turbine, open to the HRSG on the south
        x1, z1, x2, z2, top = 118, 142, 126, 153, -51
        c.fill(x1, -60, z1, x2, top, z2, "light_gray_concrete")
        c.fill(x1 + 1, -60, z1 + 1, x2 - 1, top - 1, z2, "air")
        c.fill(x1, top + 1, z1, x2, top + 1, z2, PC + "corrugated_steel")
        c.fill(x1, -55, z1 + 2, x1, -54, z2 - 2, PC + "glass_block_frosted")
        c.fill(x2, -55, z1 + 2, x2, -54, z2 - 2, PC + "glass_block_frosted")
        c.fill(121, -60, z1, 123, -57, z1, "air")                                       # door in front of the gearbox
        c.setb(122, -59, 146, PC + "gas_turbine[facing=north]")                         # re-place: the shell carved it
        c.setb(122, -59, 144, PC + "turbine_gearbox[facing=north]")
        self.low_lights(c, x1, z1, x2, z2, -61, top - 1, step=4, drop=4)
        # flare in the far corner, its knock-out drum at the foot
        c.fill(141, -60, 166, 141, -41, 166, PC + "flare_stack")
        c.setb(141, -40, 166, PC + "flare_tip[lit=true]")
        c.setb(139, -60, 166, PC + "flare_knockout_drum[facing=north,level=1]")

    def npp_turbine_hall(self):
        """Turbine hall of the VVER north of the containment (world coords): turbine casings and the generator on a
        long axis, condensers under them, the main transformer outside, steam lines over a bridge to the ring."""
        f = self.f
        y = self.ys["nuclear"]
        x1, z1, x2, z2, h = -126, 584, -76, 600, 14
        self.hall_box(x1, z1, x2, z2, y, h, "white_concrete", "light_gray_concrete", "light_gray_concrete")
        ax = (z1 + z2) // 2                                                             # turbine axis along x
        f.fill(x1 + 4, y + 1, ax - 2, x2 - 10, y + 2, ax + 2, "gray_concrete")          # turbine pedestal
        for i, (xa, xb) in enumerate(((-120, -115), (-113, -108), (-106, -101), (-99, -94))):
            blk = "light_blue_terracotta" if i == 0 else "cyan_terracotta"              # HP cylinder, then LP
            f.fill(xa, y + 3, ax - 1, xb, y + 5, ax + 1, blk)
            f.fill(xa + 1, y + 6, ax, xb - 1, y + 6, ax, blk)
        f.fill(-92, y + 3, ax - 2, -84, y + 6, ax + 2, "green_terracotta")              # generator
        f.fill(-83, y + 3, ax - 1, -81, y + 5, ax + 1, "gray_terracotta")               # exciter
        f.setb(-88, y + 7, ax, PC + "vent_stack")
        for x in (-115, -101):                                                          # condensers below the LP
            f.fill(x - 2, y + 1, ax - 6, x + 2, y + 3, ax - 4, "iron_block")
        f.fill(x1 + 1, y + h - 2, z1 + 1, x2 - 1, y + h - 2, z1 + 1, "yellow_concrete")  # crane runway
        f.fill(x1 + 1, y + h - 2, z2 - 1, x2 - 1, y + h - 2, z2 - 1, "yellow_concrete")
        f.fill(-104, y + h - 1, z1 + 1, -104, y + h - 1, z2 - 1, "yellow_concrete")    # crane bridge
        # main transformer east of the hall, its line poles going out
        f.fill(-73, y + 1, ax - 2, -69, y + 4, ax + 2, "gray_concrete")
        f.fill(-72, y + 5, ax - 1, -70, y + 6, ax + 1, PC + "chimney")                  # bushings
        for zz in (ax - 3, ax + 3):
            f.fill(-74, y + 1, zz, -68, y + 1, zz, "stone_brick_wall")                  # fire walls
        # steam lines over a bridge from the containment (ring at z 603) into the hall
        for x in (-104, -96):
            f.fill(x, y + 7, z2 + 1, x, y + 7, 604, "polished_deepslate")
            f.fill(x, y + 1, z2 + 2, x, y + 6, z2 + 2, "iron_bars")
        self.low_lights(f, x1, z1, x2, z2, y, y + h, step=6, drop=9)               # over the casings, to the floor
        self.protect.append((x1 - 6, z1 - 6, x2 + 10, z2 + 3))

    def reactor_hall(self, n):
        """VVER hall of the nuclear set (lateset local coordinates, reactor at 55 -59 195): four loops with steam
        generators and main coolant piping, ECCS accumulators, hydrogen recombiners on the walls, the spent fuel pool
        with racks under water, a refuelling bridge over the pool."""
        pipe_x = "create:fluid_pipe[east=true,west=true,north=false,south=false,up=false,down=false]"
        pipe_z = "create:fluid_pipe[north=true,south=true,east=false,west=false,up=false,down=false]"
        for sx, sz in ((47, 189), (63, 189), (63, 201), (57, 203)):
            n.setb(sx, -60, sz, PC + "steam_generator[facing=north]")
            n.fill(sx, -59, sz, sx, -56, sz, "iron_block")                      # SG shell
            n.setb(sx, -55, sz, "polished_andesite")
        for x in range(48, 54):                                                 # hot legs west and east
            n.setb(x, -60, 189, pipe_x)
        for x in range(57, 63):
            n.setb(x, -60, 189, pipe_x)
        for z in range(197, 201):
            n.setb(63, -60, z, pipe_z)
        for z in range(197, 203):
            n.setb(57, -60, z, pipe_z)
        for ax in (47, 63):
            n.setb(ax, -60, 195, PC + "eccs_accumulator")
            n.fill(ax, -59, 195, ax, -58, 195, "white_concrete")
        n.setb(45, -56, 186, PC + "hydrogen_recombiner")
        n.setb(65, -56, 186, PC + "hydrogen_recombiner")
        # spent fuel pool in the floor of the north-west corner, racks on the bottom
        n.fill(45, -64, 199, 52, -61, 205, "white_concrete")
        n.fill(46, -63, 200, 51, -61, 204, "water")
        for x in range(46, 52, 2):
            for z in range(200, 205, 2):
                n.setb(x, -63, z, PC + "fuel_rack")
        n.fill(45, -57, 202, 52, -57, 202, "yellow_concrete")                  # refuelling bridge

    # -- thermal power plant by the lake --------------------------------------------------------------------------
    def tpp(self):
        x1, z1, x2, z2 = -306, 682, -226, 726
        y = ground_y(self.s, (x1, z1, x2, z2))
        self.ys["tpp"] = y
        self.terrace(x1, z1, x2, z2, y, top="smooth_stone", margin=12)
        f = self.f
        # main building: boiler house and turbine hall under one roof, brick with window bands
        bx1, bz1, bx2, bz2, top = x1 + 30, z1 + 6, x2 - 6, z1 + 26, y + 16
        f.fill(bx1, y + 1, bz1, bx2, top, bz2, "bricks")
        f.fill(bx1 + 1, y + 1, bz1 + 1, bx2 - 1, top - 1, bz2 - 1, "air")
        for yy in (y + 5, y + 6, y + 11, y + 12):
            f.fill(bx1, yy, bz1, bx2, yy, bz1, PC + "glass_block_frosted")
            f.fill(bx1, yy, bz2, bx2, yy, bz2, PC + "glass_block_frosted")
        f.fill(bx1, top + 1, bz1, bx2, top + 1, bz2, PC + "corrugated_steel")
        f.fill(bx1 + 20, y + 1, bz1, bx1 + 23, y + 5, bz1, "air")            # gate
        f.fill(bx1 + 1, y, bz1 + 1, bx2 - 1, y, bz2 - 1, "polished_andesite")
        # boiler bay along the north wall: two boiler units, each a clad furnace column with its fans and drum
        for i, bx in enumerate((bx1 + 4, bx1 + 24)):
            f.fill(bx, y + 1, bz1 + 2, bx + 7, y + 13, bz1 + 7, "gray_concrete")
            f.fill(bx + 1, y + 1, bz1 + 1, bx + 6, y + 13, bz1 + 1, "iron_bars")              # platforms and stairs
            for yy in (y + 5, y + 9):
                f.fill(bx - 1, yy, bz1 + 1, bx + 8, yy, bz1 + 8, "smooth_stone_slab[type=top]", "keep")
            f.fill(bx + 1, y + 14, bz1 + 3, bx + 6, y + 15, bz1 + 6, "iron_block")               # steam drum
            f.setb(bx + 3, y + 1, bz1 + 8, PC + "water_tube_boiler[facing=south]")
            f.setb(bx + 1, y + 1, bz1 + 8, PC + "forced_draft_fan[facing=south]")
            f.setb(bx + 6, y + 1, bz1 + 8, PC + "induced_draft_fan[facing=south]")
            f.setb(bx + 4, y + 1, bz1 + 8, PC + "grate_stoker[facing=south]")
        f.setb(bx1 + 16, y + 10, bz1 + 4, PC + "deaerator[facing=south]")
        f.fill(bx1 + 14, y + 9, bz1 + 2, bx1 + 19, y + 9, bz1 + 7, "smooth_stone_slab[type=top]")
        # turbine hall along the south wall: two turbine-generator sets on the shaft line z = sz
        sz = bz2 - 5
        for i, tx in enumerate((bx1 + 3, bx1 + 23)):
            f.setb(tx, y + 1, sz, PC + "steam_turbine[facing=east]")
            f.fill(tx + 1, y + 1, sz - 1, tx + 3, y + 3, sz + 1, "light_gray_concrete")      # HP cylinder
            f.fill(tx + 4, y + 1, sz - 1, tx + 7, y + 3, sz + 1, "light_gray_concrete")      # IP cylinder
            f.fill(tx + 8, y + 1, sz - 2, tx + 12, y + 4, sz + 2, "light_gray_concrete")     # LP cylinder
            f.fill(tx + 9, y + 5, sz - 1, tx + 11, y + 5, sz + 1, "polished_andesite")       # LP hood
            f.fill(tx + 13, y + 1, sz - 1, tx + 16, y + 3, sz + 1, "cyan_terracotta")        # generator
            f.setb(tx + 17, y + 1, sz, PC + "shaft_generator[facing=east]")
            f.setb(tx + 17, y + 2, sz, PC + "generator_exciter[facing=east]")
            f.setb(tx + 6, y + 1, sz + 2, PC + "turbine_turning_gear[facing=north]")
            f.setb(tx + 10, y + 1, sz - 3, PC + "condenser[facing=north]")
            for k in range(1, 9):                                                          # steam line from the drum
                f.setb(tx + 3, y + 3 + k, sz - 3, "create:fluid_pipe[up=true,down=true,north=false,south=false,east=false,west=false]")
            f.setb(tx + 18, y + 1, sz - 3, PC + "distribution_transformer[facing=north]")
        # control room with a CC computer on the alarm screen, by the gate
        f.setb(bx2 - 4, y + 1, bz1 + 10, PC + "control_desk[facing=north]")
        f.setb(bx2 - 6, y + 1, bz1 + 10, PC + "annunciator_panel[facing=north]")
        self.computer(bx2 - 5, y + 1, bz1 + 10, "north", "alarm_screen")
        self.low_lights(f, bx1, bz1, bx2, bz2, y, top, step=8, drop=8)
        # two chimneys behind the boiler house
        for cx in (bx1 + 10, bx1 + 30):
            f.fill(cx - 1, y + 1, bz2 + 2, cx + 1, top + 1, bz2 + 4, "bricks")
            f.fill(cx, top + 2, bz2 + 3, cx, top + 40, bz2 + 3, PC + "chimney")
        # natural draft cooling towers west of the building
        for cx, cz in ((x1 + 12, z1 + 12), (x1 + 12, z1 + 34)):
            self.cooling_tower(cx, y, cz, 10, 34)
        # open switchyard: transformers on the east side, poles carry the line out
        for k in range(4):
            f.setb(x2 - 3, y + 1, z1 + 30 + 3 * k, PC + "distribution_transformer[facing=west]")

    def cooling_tower(self, cx, y, cz, r0, h):
        """Hyperboloid shell narrowing to a throat at 3/4 height, flaring at the top, open legs at the base."""
        f = self.f
        for k in range(h):
            t = k / h
            r = r0 * (0.62 + 0.38 * ((t - 0.75) / 0.75) ** 2) if t < 0.75 else r0 * (0.62 + 0.5 * (t - 0.75))
            for dx in range(-int(r) - 1, int(r) + 2):
                for dz in range(-int(r) - 1, int(r) + 2):
                    d = math.hypot(dx, dz)
                    if r - 1 <= d <= r:
                        if k < 3 and (math.atan2(dz, dx) * 8 / math.pi) % 1 < 0.6:
                            continue
                        f.setb(cx + dx, y + 1 + k, cz + dz, "light_gray_concrete")
        f.fill(cx - r0 + 3, y, cz - r0 + 3, cx + r0 - 3, y, cz + r0 - 3, "water")
        for dx, dz in ((0, 0), (2, 1), (-2, -1)):
            f.setb(cx + dx, y + h - 2, cz + dz, "campfire[lit=true,signal_fire=true]")

    # -- hydro plant ---------------------------------------------------------------------------------------------
    def hydro(self):
        """Concrete gravity dam across the river valley with a stepped spillway; the reservoir fills the valley behind
        it up to W wherever the ground is lower, earth dikes close the low ends; the powerhouse sits low at the toe so
        the dam stands above it."""
        f, s = self.f, self.s
        W = 72                                 # reservoir water level (top water block)
        dz = 1112                              # dam axis, reservoir to the south
        rx1, rx2, rz2 = 64, 150, 1152
        self.protect.append((rx1, dz - 18, rx2, rz2))
        for x in range(rx1, rx2 + 1):
            for z in range(dz + 1, rz2 + 1):
                g = s.floor(x, z)
                if g >= W:
                    continue                                       # natural bank above the water
                if x in (rx1, rx2) or z == rz2:
                    f.fill(x, g - 3, z, x, W, z, "dirt")           # dike across a low end
                    f.setb(x, W + 1, z, "grass_block")
                    continue
                f.fill(x, g + 1, z, x, W, z, "water")
                f.fill(x, W + 1, z, x, W + 8, z, "air")
        # dam: only across the valley (where the ground is below the crest), crest road between parapets
        span = [x for x in range(rx1, rx2 + 1) if s.floor(x, dz) < W + 3]
        dx1, dx2 = min(span), max(span)
        for x in range(dx1 - 1, dx2 + 2):
            g = s.floor(x, dz)
            spill = 116 <= x <= 124
            crest = W if spill else W + 2
            f.fill(x, min(g, 58) - 2, dz - 3, x, crest, dz, "light_gray_concrete")
            f.fill(x, min(g, 58) - 2, dz - 6, x, min(crest, 66), dz - 4, "light_gray_concrete")   # downstream batter
            if spill:
                for k in range(6):                                                    # stepped chute
                    f.setb(x, crest - k, dz - 4 - k, "stone_brick_stairs[facing=south]")
            else:
                f.setb(x, crest + 1, dz - 3, "stone_brick_wall")
                f.setb(x, crest + 1, dz, "stone_brick_wall")
        gates, turbines = [], []
        for i in range(3):
            x = 94 + 8 * i
            gate = (x, W - 3, dz + 1)
            f.setb(*gate, PC + "dam_gate[facing=south,open=true]")
            gates.append(gate)
            f.fill(x - 1, W + 3, dz - 1, x + 1, W + 5, dz + 1, "gray_concrete")       # gate hoist house on the crest
        # powerhouse at the toe: low, so the dam stands over it
        py = 63
        px1, pz1, px2, pz2 = 86, dz - 18, 116, dz - 7
        for x in range(px1 - 3, px2 + 4):
            for z in range(pz1 - 3, dz - 6):
                g = s.floor(x, z)
                f.fill(x, min(g, py) - 4, z, x, py - 1, z, "stone")
                f.fill(x, py + 1, z, x, max(g, py) + 8, z, "air")
                f.setb(x, py, z, "smooth_stone")
        f.fill(px1, py + 1, pz1, px2, py + 7, pz2, "light_gray_concrete")
        f.fill(px1 + 1, py + 1, pz1 + 1, px2 - 1, py + 6, pz2 - 1, "air")
        for yy in (py + 4, py + 5):
            f.fill(px1, yy, pz1, px2, yy, pz1, PC + "glass_block_frosted")
        f.fill(px1, py + 8, pz1, px2, py + 8, pz2, PC + "corrugated_steel")
        f.fill(px1 + 13, py + 1, pz1, px1 + 16, py + 3, pz1, "air")              # door
        for i in range(3):
            x = 94 + 8 * i
            t = (x, py + 2, pz2 - 3)                                              # controller on the upper layer
            f.setb(*t, PC + "hydro_turbine[facing=north]")
            turbines.append(t)
            f.setb(x, py + 1, pz1 + 3, PC + "shaft_generator[facing=north]")
            f.setb(x, py + 2, pz1 + 3, PC + "generator_exciter[facing=north]")
            for zz in range(pz1 + 4, pz2 - 4):
                f.setb(x, py + 1, zz, "create:shaft[axis=z]")
            for k in range(0, dz - 4 - pz2):                                      # penstock through the wall
                f.setb(x, py + 2, pz2 + 1 + k, "iron_block")
        f.fill(px1 + 1, py + 6, pz1 + 2, px2 - 1, py + 6, pz1 + 2, "yellow_concrete")  # overhead crane
        self.computer(px2 - 3, py + 1, pz1 + 2, "south", "alarm_screen")
        self.low_lights(f, px1, pz1, px2, pz2, py, py + 7, step=6, drop=5)
        return gates, turbines

    def hydro_gorge(self, site):
        """High-head plant in the gorge 1250 blocks east of the works (seed 62, found by /film gorge): a concrete
        gravity dam across the gorge at z 664 (crest 92), the reservoir north up to z 590 at level 90 held by the
        gorge walls and an earth dike at its far end, gates low on the upstream face, penstocks through the dam to a
        powerhouse at the toe, a stepped spillway, a switchyard on the plateau."""
        f, s = self.f, site
        W, CREST, DZ = 90, 92, 664
        xa, xb = 1040, 1150
        self.protect.append((xa, 586, xb, 712))
        # reservoir
        for x in range(xa, xb + 1):
            for z in range(590, DZ - 4):
                g = s.floor(x, z)
                if g >= W:
                    continue
                if z == 590:
                    f.fill(x, g - 3, z, x, W + 1, z, "dirt")
                    f.setb(x, W + 2, z, "grass_block")
                    continue
                f.fill(x, g + 1, z, x, W, z, "water")
                f.fill(x, W + 1, z, x, W + 6, z, "air")
        # dam: upstream face vertical at z = DZ - 4, downstream face battered 0.35, only across the gorge
        span = [x for x in range(xa, xb + 1) if s.floor(x, DZ) < CREST]
        dx1, dx2 = min(span) - 1, max(span) + 1
        for x in range(dx1, dx2 + 1):
            bed = min(s.floor(x, z) for z in range(DZ - 4, DZ + 30, 2)) - 2
            spill = 1074 <= x <= 1079
            crest = W if spill else CREST
            for y in range(bed, crest + 1):
                zd = DZ + int((CREST - y) * 0.35)
                f.fill(x, y, DZ - 4, x, y, zd, "light_gray_concrete")
            if spill:
                for y in range(crest, 63, -1):
                    f.setb(x, y, DZ + int((CREST - y) * 0.35) + 1, "stone_brick_stairs[facing=south]")
            else:
                f.setb(x, crest + 1, DZ - 4, "stone_brick_wall")
                f.setb(x, crest + 1, DZ, "stone_brick_wall")
                f.setb(x, crest, DZ - 2, "gray_concrete")
        gates, turbines = [], []
        py = 64
        px1, pz1, px2, pz2 = 1080, DZ + 26, 1106, DZ + 40
        for i in range(3):
            x = 1085 + 8 * i
            gate = (x, 62, DZ - 5)
            f.setb(*gate, PC + "dam_gate[facing=north,open=true]")
            gates.append(gate)
            f.fill(x - 1, CREST + 1, DZ - 4, x + 1, CREST + 3, DZ - 2, "gray_concrete")   # gate hoist house
            for z in range(DZ - 3, pz1):                                                    # penstock
                yy = 62 + (z - DZ + 3) * (py + 2 - 62) // (pz1 - DZ + 3)
                f.fill(x, yy, z, x, yy + 1, z, "iron_block")
        # powerhouse at the toe
        for x in range(px1 - 2, px2 + 3):
            for z in range(pz1 - 2, pz2 + 3):
                g = s.floor(x, z)
                f.fill(x, min(g, py) - 3, z, x, py - 1, z, "stone")
                f.fill(x, py + 1, z, x, max(g, py) + 6, z, "air")
                f.setb(x, py, z, "smooth_stone")
        f.fill(px1, py + 1, pz1, px2, py + 11, pz2, "light_gray_concrete")
        f.fill(px1 + 1, py + 1, pz1 + 1, px2 - 1, py + 10, pz2 - 1, "air")
        for yy in (py + 6, py + 7, py + 8):
            f.fill(px1, yy, pz2, px2, yy, pz2, PC + "glass_block_frosted")
        f.fill(px1, py + 12, pz1, px2, py + 12, pz2, PC + "corrugated_steel")
        f.fill(px1 + 11, py + 1, pz2, px1 + 14, py + 4, pz2, "air")                       # door to the tailrace road
        for i in range(3):
            x = 1085 + 8 * i
            t = (x, py + 2, pz1 + 3)
            f.setb(*t, PC + "hydro_turbine[facing=south]")
            turbines.append(t)
            f.setb(x, py + 1, pz1 + 9, PC + "shaft_generator[facing=south]")
            f.setb(x, py + 2, pz1 + 9, PC + "generator_exciter[facing=south]")
            for zz in range(pz1 + 5, pz1 + 9):
                f.setb(x, py + 1, zz, "create:shaft[axis=z]")
            f.fill(x - 1, py + 1, pz1 + 6, x + 1, py + 4, pz1 + 7, "cyan_terracotta", "keep")  # generator stator
        f.fill(px1 + 1, py + 9, pz1 + 2, px2 - 1, py + 9, pz1 + 2, "yellow_concrete")      # crane girder
        self.computer(px2 - 3, py + 1, pz2 - 2, "north", "alarm_screen")
        self.low_lights(f, px1, pz1, px2, pz2, py, py + 11, step=6, drop=6)
        # switchyard on the east plateau
        sy = s.floor(1120, 690)
        self.terrace(1114, 682, 1140, 704, sy, top="gravel", margin=6)
        # the yard lies inside the hydro's protected rectangle, which the terrace skips: level it explicitly
        f.fill(1114, sy + 1, 682, 1140, sy + 24, 704, "air")
        f.fill(1114, sy - 3, 682, 1140, sy - 1, 704, "stone")
        f.fill(1114, sy, 682, 1140, sy, 704, "gravel")
        for i in range(4):
            f.setb(1118 + 6 * i, sy + 1, 688, PC + "distribution_transformer[facing=south]")
            f.setb(1118 + 6 * i, sy + 1, 698, PC + "disconnector[facing=south]")
        return gates, turbines

    # -- wind farm -----------------------------------------------------------------------------------------------
    def wind(self, n=8, spacing=22):
        s, f = self.s, self.f
        cands = sorted(((s.floor(x, z), x, z) for x in range(-200, -60, 4) for z in range(880, 1110, 4)
                        if not s.water(x, z)), reverse=True)
        picked = []
        for h, x, z in cands:
            if all(math.hypot(x - a, z - b) >= spacing for _, a, b in picked):
                picked.append((h, x, z))
            if len(picked) == n:
                break
        for h, x, z in picked:
            self.terrace(x - 3, z - 3, x + 3, z + 3, h, top="gravel", margin=5)
            f.fill(x - 2, h, z - 2, x + 2, h, z + 2, "smooth_stone")
            f.setb(x, h + 2, z, PC + "wind_turbine[facing=west]")       # controller one above the base cell
        return picked

    def wind_grid(self, mills):
        """Buried collector for the wind farm, as a real one: each turbine (0.4 kV, WindGameTests) feeds its own
        pad transformer 6/0.4 kV in a vault under the pad, a 6 kV cable runs at depth to a substation whose
        grid-forming generator holds the voltage the turbines need to close their contactors three 400 kW sets
        carry a 1000 kW load (a step of grid_consumer) until the turbines take part of it over; a load below the
        farm's export drives the surplus into generators whose Create drive cannot brake, and the island overspeeds. Cables are laid so LV and HV runs never touch (a cable joins every cable beside it)."""
        s, f = self.s, self.f
        cable = PC + "power_cable"
        self.wind_starts = []
        zb = min(z for _, _, z in mills) - 8                   # collector bus line, north of the farm
        xs = [x for _, x, _ in mills]
        gx = max(xs) + 6                                       # substation generator past the last spur, bus's east end
        route = [(x, z) for _, x, z in mills for z in range(zb, z + 5)] + [(x, zb) for x in range(min(xs), gx + 3)]
        yc = min(min(s.floor(x, z) for x, z in route), min(h for h, _, _ in mills) - 8) - 6
        for h, x, z in mills:
            # LV: base cell's DOWN face -> down through the pad -> west of the transformer's LV cell
            f.fill(x, h - 6, z, x, h, z, cable)
            for c in ((x - 1, h - 6, z), (x - 1, h - 6, z + 1), (x - 1, h - 6, z + 2)):
                f.setb(*c, cable)
            f.fill(x, h - 6, z + 2, x, h - 5, z + 2, "air")       # the footprint forms only into free cells
            f.setb(x, h - 6, z + 2, PC + "distribution_transformer[facing=north]")   # LV cell; HV cell above
            # HV: south face of the HV cell -> 2 south -> down to the collector depth -> north to the bus
            f.setb(x, h - 5, z + 3, cable)
            f.fill(x, yc, z + 4, x, h - 5, z + 4, cable)
            f.fill(x, yc, zb, x, yc, z + 4, cable)
            self.protect.append((x - 3, z - 3, x + 3, z + 5))
            self.wind_starts.append(f"film prime wind {x} {h + 2} {z}")
        # substation vault: generator on a Create motor (gear 64, field on by `film prime generator`)
        f.fill(gx - 3, yc - 2, zb - 6, gx + 6, yc + 3, zb + 3, "stone")
        f.fill(gx - 2, yc - 1, zb - 5, gx + 5, yc + 2, zb + 2, "air")
        f.fill(min(xs), yc, zb, gx - 1, yc, zb, cable)                                # bus, after the vault is cut
        f.setb(gx - 1, yc, zb - 1, cable)                                            # bus -> load's south face
        f.setb(gx - 1, yc, zb - 3, PC + "grid_consumer[facing=north]")               # 3x3x3: z zb-4..zb-2
        f.setb(gx, yc, zb, PC + "shaft_generator[facing=north]")
        f.setb(gx, yc + 1, zb, PC + "generator_exciter[facing=north]")
        f.setb(gx, yc, zb + 1, "create:creative_motor[facing=north]{ScrollValue:64}")
        # two more sets beside it (3 x 400 kW): they carry the 1000 kW load before the wind is in, and the farm's
        # 800 kW at most still leaves them loaded. A Create drive only pushes: with export above the load the island
        # speeds up and the turbines trip on overspeed (WindTurbineUnitTest-style probe, 2026-11-20)
        for k in (2, 4):
            f.setb(gx + k - 1, yc, zb, cable)
            f.setb(gx + k, yc, zb, PC + "shaft_generator[facing=north]")
            f.setb(gx + k, yc + 1, zb, PC + "generator_exciter[facing=north]")
            f.setb(gx + k, yc, zb + 1, "create:creative_motor[facing=north]{ScrollValue:64}")
        self.protect.append((gx - 3, zb - 6, gx + 3, zb + 3))
        self.prime(f"film prime generator {gx} {yc} {zb}")
        self.prime(f"film prime generator {gx + 2} {yc} {zb}")
        self.prime(f"film prime generator {gx + 4} {yc} {zb}")
        self.prime(f"film prime load {gx - 1} {yc} {zb - 3}")
        print(f"wind grid: {len(mills)} turbines, collector at y {yc}, substation ({gx}, {yc}, {zb})")

    # -- tidal lagoon --------------------------------------------------------------------------------------------
    def tidal(self):
        """Ring dike in the shallow estuary; the units sit in the north dike, sea in front, lagoon behind."""
        s, f = self.s, self.f
        x1, z1, x2, z2 = -16, 702, 56, 756
        crest = s.sea + 4
        self.protect.append((x1, z1, x2, z2))
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                g = s.floor(x, z)
                if x in (x1, x1 + 1, x2 - 1, x2) or z in (z1, z1 + 1, z2 - 1, z2):
                    f.fill(x, g - 1, z, x, crest - 1, z, "stone")
                    f.setb(x, crest, z, "andesite" if z in (z1, z1 + 1) else "grass_block")
                    f.fill(x, crest + 1, z, x, crest + 8, z, "air")
                elif g < s.sea:
                    f.fill(x, g + 1, z, x, s.sea, z, "water")     # lagoon at mean sea level
        units = []
        for i in range(4):
            x = x1 + 14 + 12 * i
            # sluice bay through the dike (z1..z1+2): the unit fills it; sea stays 2 north of the controller
            # (z1-1), the lagoon 2 south (z1+3)
            f.fill(x - 1, s.sea - 1, z1, x + 1, s.sea + 3, z1 + 2, "air")
            f.setb(x, s.sea, z1 + 1, PC + "tidal_unit[facing=north]")
            units.append((x, s.sea, z1 + 1))
            f.fill(x - 2, s.sea - 1, z1, x - 2, s.sea + 3, z1 + 2, "light_gray_concrete")
            f.fill(x + 2, s.sea - 1, z1, x + 2, s.sea + 3, z1 + 2, "light_gray_concrete")
            f.fill(x - 1, s.sea + 2, z1, x + 1, s.sea + 3, z1 + 2, "light_gray_concrete")
        f.setb(x1 + 4, crest + 1, z1, PC + "tide_gauge[facing=north]")
        return units

    # -- connections ---------------------------------------------------------------------------------------------
    HYDRO_ROAD = [(70, 800), (72, 900), (74, 1000), (78, 1080), (90, 1088)]

    def roads(self):
        main = [(-500, 794), (-380, 794), (-260, 790), (-120, 790), (0, 790), (70, 800)]
        self.road(main, width=7, block="dirt_path")
        self.road([(-260, 790), (-260, 728)])                  # to the power plant
        self.road([(-118, 790), (-118, 636)])                  # to the nuclear plant, west of the steel works
        self.road([(-430, 794), (-430, 828)])                  # into the coal and steam works
        self.road([(-180, 790), (-180, 760)])                  # chemistry
        self.road([(0, 790), (10, 760)])                       # to the tidal lagoon
        self.pole_line(main)
        if hasattr(self, "street_lights"):
            self.street_lights(main)

    def rail(self):
        """Create standard gauge north of the main road, on its own level formation."""
        z = 778
        xs = range(-500, -130)
        y = max(self.s.sea + 3, round(sum(self.s.floor(x, z) for x in xs) / len(xs)))
        for x in xs:
            for k in (-2, -1, 0, 1, 2):
                self.column_top(x, z + k, y, "gravel" if abs(k) < 2 else "andesite", clear=6)
            self.f.setb(x, y + 1, z, "create:track[shape=xo]")
        for x in (-430, -150):
            self.f.setb(x, y + 1, z + 1, "create:track_station")
        for x in range(-500, -130, 40):
            self.f.setb(x, y + 1, z - 1, "create:track_signal")


# ------------------------------------------------------------------------------------------------- layout v2
# Built over v1: every machine of the catalog in a production hall of its district, NPP containment and towers,
# tank farms, pipe racks, street lights. Positions avoid the v1 sets, roads and rail (see the v1 coordinates above).

HALLS = {  # district -> hall corner (x1, z1); size from plant.pack, doors on the north side
    "coal": (-340, 860), "power": (-222, 650), "metal": (-120, 716), "oil": (-300, 830),
    "chem": (-222, 686), "fuel": (-104, 640), "npp": (-104, 660), "ic": (-40, 800), "ops": (-40, 812),
}
HALL_STYLE = {  # wall, accent, roof
    "coal": ("bricks", "stone_bricks", "dark_oak_planks"), "power": ("bricks", "polished_andesite", PC + "corrugated_steel"),
    "metal": (PC + "corrugated_steel", "gray_concrete", PC + "corrugated_steel"),
    "oil": ("light_gray_concrete", "gray_concrete", PC + "corrugated_steel"),
    "chem": ("white_concrete", "light_blue_concrete", PC + "corrugated_steel"),
    "fuel": ("white_concrete", "light_gray_concrete", "light_gray_concrete"),
    "npp": ("white_concrete", "light_gray_concrete", "light_gray_concrete"),
    "ic": ("smooth_sandstone", "cut_sandstone", "stone_bricks"), "ops": ("smooth_sandstone", "cut_sandstone", "stone_bricks"),
}


AISLE = 5
BAY_LENGTH = 72


def rotated(cells, facing):
    """Footprint cells for a facing (catalog cells face north): south turns them 180 degrees."""
    return [(-x, y, -z) for x, y, z in cells] if facing == "south" else [tuple(c) for c in cells]


def state_facing(e, facing):
    import plant as catalog
    s = catalog.state(e)
    return s.replace("facing=north", "facing=" + facing) if e["facing"] else s


def shop_bays(items):
    """Pack machines into bays: a north row facing south and a south row facing north across an aisle. Returns
    [{machines: [(entry, dx, row, facing)], length, depth, dn, off_n, off_s, height}]."""
    items = sorted(items, key=lambda e: -(max(c[0] for c in e["cells"]) - min(c[0] for c in e["cells"])))
    bays, cur = [], None
    for e in items:
        if cur is None:
            cur = {"rows": {"n": [], "s": []}, "x": {"n": 0, "s": 0}}
            bays.append(cur)
        row = "n" if cur["x"]["n"] <= cur["x"]["s"] else "s"
        facing = "south" if row == "n" else "north"
        cells = rotated(e["cells"], facing if e["facing"] else "north")
        xs = [c[0] for c in cells]
        w = max(xs) - min(xs) + 1
        if cur["x"][row] + w > BAY_LENGTH:
            cur = {"rows": {"n": [], "s": []}, "x": {"n": 0, "s": 0}}
            bays.append(cur)
            row, facing = "n", "south"
            cells = rotated(e["cells"], facing if e["facing"] else "north")
        cur["rows"][row].append((e, cur["x"][row] - min(c[0] for c in cells), facing, cells))
        cur["x"][row] += w + 3
    out = []
    for b in bays:
        zs_n = [c[2] for _, _, _, cells in b["rows"]["n"] for c in cells] or [0]
        zs_s = [c[2] for _, _, _, cells in b["rows"]["s"] for c in cells] or [0]
        # north row: controllers on one z line; cells reach from min to max z around it
        off_n = max(zs_n)                      # cells south of the controller line (towards the aisle)
        dn = max(zs_n) - min(zs_n) + 2
        off_s = -min(zs_s)                     # cells north of the controller line (towards the aisle)
        ds = max(zs_s) - min(zs_s) + 2
        hs = [c[1] for r in ("n", "s") for _, _, _, cells in b["rows"][r] for c in cells] or [0]
        machines = [(e, dx, r, f) for r in ("n", "s") for e, dx, f, _ in b["rows"][r]]
        out.append({"machines": machines, "length": max(b["x"]["n"], b["x"]["s"]), "dn": dn, "off_n": off_n,
                    "off_s": off_s, "depth": dn + AISLE + ds, "height": max(hs) - min(hs) + 1})
    return out


class Plant2(Plant):
    def hall_box(self, x1, z1, x2, z2, y, h, wall, accent, roof):
        """Production hall: pilasters every 6, a window band, a parapet roof with vents, lights, a north gate."""
        f = self.f
        self.terrace(x1 - 4, z1 - 5, x2 + 3, z2 + 3, y, top="gray_concrete", margin=8)
        f.fill(x1, y + 1, z1, x2, y + h, z2, wall)
        f.fill(x1 + 1, y + 1, z1 + 1, x2 - 1, y + h, z2 - 1, "air")
        f.fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, PC + "painted_concrete")
        for x in range(x1, x2 + 1, 6):
            f.fill(x, y + 1, z1, x, y + h + 1, z1, accent)
            f.fill(x, y + 1, z2, x, y + h + 1, z2, accent)
        for z in range(z1, z2 + 1, 6):
            f.fill(x1, y + 1, z, x1, y + h + 1, z, accent)
            f.fill(x2, y + 1, z, x2, y + h + 1, z, accent)
        wy = y + h - 3
        for x in range(x1 + 1, x2):
            if (x - x1) % 6:
                f.fill(x, wy, z1, x, wy + 1, z1, PC + "glass_block_frosted")
                f.fill(x, wy, z2, x, wy + 1, z2, PC + "glass_block_frosted")
        for z in range(z1 + 1, z2):
            if (z - z1) % 6:
                f.fill(x1, wy, z, x1, wy + 1, z, PC + "glass_block_frosted")
                f.fill(x2, wy, z, x2, wy + 1, z, PC + "glass_block_frosted")
        f.fill(x1, y + h + 1, z1, x2, y + h + 1, z2, roof)
        f.fill(x1, y + h + 2, z1, x2, y + h + 2, z1, accent)
        f.fill(x1, y + h + 2, z2, x2, y + h + 2, z2, accent)
        f.fill(x1, y + h + 2, z1, x1, y + h + 2, z2, accent)
        f.fill(x2, y + h + 2, z1, x2, y + h + 2, z2, accent)
        for x in range(x1 + 4, x2 - 3, 10):
            f.setb(x, y + h + 2, (z1 + z2) // 2, PC + "vent_stack")
        for x in range(x1 + 3, x2 - 2, 5):
            for z in range(z1 + 3, z2 - 2, 5):
                f.setb(x, y + h, z, PC + "industrial_light[lit=true]")
        mid = (x1 + x2) // 2
        f.fill(mid - 2, y + 1, z1, mid + 2, y + 5, z1, "air")                  # gate for vehicles
        f.fill(mid - 2, y, z1 - 4, mid + 2, y, z1 - 1, "gray_concrete")        # apron

    def catalog_halls(self):
        """Every catalog machine in a shop of its district: bays of two rows facing a central aisle."""
        import plant as catalog
        by = catalog.machines()
        for d in catalog.DISTRICTS:
            key = d[0]
            bays = shop_bays(by[key])
            x1, z1 = HALLS[key]
            length = max(b["length"] for b in bays) + 6
            depth = sum(b["depth"] for b in bays) + 2 * (len(bays) - 1) + 4
            x2, z2 = x1 + length, z1 + depth
            y = ground_y(self.s, (x1, z1, x2, z2))
            h = max(9, max(b["height"] for b in bays) + 6)
            self.hall_box(x1, z1, x2, z2, y, h, *HALL_STYLE[key])
            self.dress_hall(x1, z1, x2, z2, y, h)
            bz = z1 + 2
            for b in bays:
                self.dress_bay(x1, bz, x2, b, y, h)
                for e, dx, row, facing in b["machines"]:
                    x = x1 + 3 + dx
                    z = bz + (b["dn"] - 1 - b["off_n"] if row == "n" else b["dn"] + AISLE + b["off_s"])
                    self.f.setb(x, y + 1 - catalog.bbox(e["cells"])[2], z, state_facing(e, facing))
                bz += b["depth"] + 2
            self.low_lights(self.f, x1, z1, x2, z2, y, y + h, step=6, drop=7)
            self.ys["hall_" + key] = y

    def dress_bay(self, x1, bz, x2, b, y, h):
        """Aisle with yellow edge lines between the rows, wall pipes, low lamps over the aisle, an operator desk."""
        f = self.f
        az1 = bz + b["dn"]
        az2 = az1 + AISLE - 1
        f.fill(x1 + 1, y, az1, x2 - 1, y, az2, "light_gray_concrete")
        f.fill(x1 + 1, y, az1, x2 - 1, y, az1, PC + "painted_concrete_yellow")
        f.fill(x1 + 1, y, az2, x2 - 1, y, az2, PC + "painted_concrete_yellow")
        for x in range(x1 + 4, x2 - 2, 4):
            f.fill(x, y + 7, (az1 + az2) // 2, x, y + h - 1, (az1 + az2) // 2, "chain[axis=y]", "keep")
            f.setb(x, y + 6, (az1 + az2) // 2, PC + "industrial_light[lit=true]")
        f.setb(x1 + 1, y + 1, az1 + 1, PC + "control_desk[facing=east]")
        f.setb(x1 + 1, y + 1, az2 - 1, PC + "annunciator_panel[facing=east]")

    def dress_hall(self, x1, z1, x2, z2, y, h):
        """Crane on rails, pipes along the long walls, pallets by the gate."""
        f = self.f
        ry = y + h - 1
        f.fill(x1 + 1, ry, z1 + 1, x2 - 1, ry, z1 + 1, "iron_bars")
        f.fill(x1 + 1, ry, z2 - 1, x2 - 1, ry, z2 - 1, "iron_bars")
        gx = x1 + (x2 - x1) // 3
        f.fill(gx, ry, z1 + 1, gx, ry, z2 - 1, "yellow_concrete")
        f.fill(gx, ry - 3, (z1 + z2) // 2, gx, ry - 1, (z1 + z2) // 2, "chain[axis=y]")
        for z, side in ((z1 + 1, "south"), (z2 - 1, "north")):
            for yy in (y + 4, y + 5):
                f.fill(x1 + 1, yy, z, x2 - 1, yy, z,
                       "create:fluid_pipe[east=true,west=true,north=false,south=false,up=false,down=false]")
        mid = (x1 + x2) // 2
        for dx in (-6, -5, 5, 6):
            f.fill(mid + dx, y + 1, z1 + 1, mid + dx, y + 2, z1 + 1, "barrel[facing=up]")

    def containment(self):
        """Cylindrical prestressed-concrete containment with a dome around the VVER hall of the nuclear set."""
        f = self.f
        y0 = self.ys["nuclear"]
        cx, cz, r, wall_top = -99, 619, 16, y0 + 18
        for dx in range(-r - 1, r + 2):
            for dz in range(-r - 1, r + 2):
                d = math.hypot(dx, dz)
                if r - 1 <= d <= r:
                    if dz < -r + 3 and abs(dx) <= 2:
                        f.fill(cx + dx, y0 + 6, cz + dz, cx + dx, wall_top, cz + dz, "light_gray_concrete")
                        f.fill(cx + dx, y0 + 1, cz + dz, cx + dx, y0 + 5, cz + dz, "air")   # equipment hatch
                    else:
                        f.fill(cx + dx, y0 + 1, cz + dz, cx + dx, wall_top, cz + dz, "light_gray_concrete")
        for k in range(r + 1):
            rr = math.sqrt(max(0, r * r - k * k))
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    d = math.hypot(dx, dz)
                    if rr - 1.2 <= d <= rr:
                        f.setb(cx + dx, wall_top + k // 2 + 1, cz + dz, "white_concrete")
        f.setb(cx, wall_top + r // 2 + 2, cz, PC + "industrial_light_street[facing=north,lit=true]")

    def tank(self, cx, y, cz, r, h, block="white_concrete", roof="light_gray_concrete"):
        f = self.f
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                d = math.hypot(dx, dz)
                if d <= r:
                    f.setb(cx + dx, y, cz + dz, "smooth_stone")
                    if d >= r - 1:
                        f.fill(cx + dx, y + 1, cz + dz, cx + dx, y + h, cz + dz, block)
                    f.setb(cx + dx, y + h + 1, cz + dz, roof)
        f.fill(cx, y + 1, cz - r - 1, cx, y + h + 1, cz - r - 1, "ladder[facing=north]")
        for dx in (-r // 2, r // 2):
            f.setb(cx + dx, y + 2, cz - r - 1, "create:fluid_pipe[north=false,south=true,east=false,west=false,up=false,down=false]")

    def tank_farm(self, x1, z1, cols, rows, r, h, block):
        y = ground_y(self.s, (x1, z1, x1 + cols * (2 * r + 4), z1 + rows * (2 * r + 4)))
        x2, z2 = x1 + cols * (2 * r + 4), z1 + rows * (2 * r + 4)
        self.terrace(x1 - 2, z1 - 2, x2 + 2, z2 + 2, y, top="gravel", margin=8)
        self.f.fill(x1 - 2, y + 1, z1 - 2, x2 + 2, y + 1, z1 - 2, "stone_brick_wall")     # bund wall
        self.f.fill(x1 - 2, y + 1, z2 + 2, x2 + 2, y + 1, z2 + 2, "stone_brick_wall")
        self.f.fill(x1 - 2, y + 1, z1 - 2, x1 - 2, y + 1, z2 + 2, "stone_brick_wall")
        self.f.fill(x2 + 2, y + 1, z1 - 2, x2 + 2, y + 1, z2 + 2, "stone_brick_wall")
        for i in range(cols):
            for j in range(rows):
                self.tank(x1 + r + 2 + i * (2 * r + 4), y, z1 + r + 2 + j * (2 * r + 4), r, h, block)

    def pipe_rack(self, ax, az, bx, bz, y, avoid=()):
        """Steel posts every 6 with two Create pipes on top; straight along x or z."""
        f = self.f
        along_x = az == bz
        a, b = sorted((ax, bx) if along_x else (az, bz))
        pipe = ("create:fluid_pipe[east=true,west=true,north=false,south=false,up=false,down=false]" if along_x
                else "create:fluid_pipe[north=true,south=true,east=false,west=false,up=false,down=false]")
        for t in range(a, b + 1):
            x, z = (t, az) if along_x else (ax, t)
            for k in (0, 1):
                px, pz = (x, z + k) if along_x else (x + k, z)
                f.setb(px, y + 7, pz, pipe)
            if (t - a) % 6 == 0 and not any(lo <= (z if along_x else x) <= hi for lo, hi in avoid):
                g = self.s.floor(x, z)
                for k in (0, 1):
                    px, pz = (x, z + k) if along_x else (x + k, z)
                    f.fill(px, g + 1, pz, px, y + 6, pz, "iron_bars")
                    f.setb(px, y + 6, pz, "polished_blackstone")

    def refinery(self):
        """Secondary units of the refinery on the strip west of the oil set, between the road (z 790..796) and the
        coal set (z 828): a process train east to west, each mod unit (one block) inside a shell that reads as the
        real plant, fed by `film prime unit` as its GameTest does (ConversionGameTests, RefineryUpgradingGameTests)."""
        f = self.f
        x1, z1, x2, z2 = -440, 805, -368, 822
        y = ground_y(self.s, (x1, z1, x2, z2))
        self.terrace(x1, z1, x2, z2, y, top="gray_concrete", margin=5)
        self.protect.append((x1, z1, x2, z2))
        for x in range(x1, x2 + 1, 9):                                         # paving joints
            f.fill(x, y, z1, x, y, z2, "light_gray_concrete")
        cz, b = 814, y + 1                                                      # process line, base level
        n = "[facing=north]"
        tower = lambda x, z, h: [f.setb(x, b + 1 + 2 * k, z, PC + "column_tray_section" + n) for k in range(h)] +             [f.setb(x, b + 1 + 2 * h, z, PC + "column_top" + n)]
        # vacuum column: steam ejectors either side pull the flash zone to 5 kPa; a tray-section shell above it
        f.setb(-376, b, cz, PC + "vacuum_column" + n)
        f.setb(-375, b, cz, PC + "steam_ejector" + n)
        f.setb(-377, b, cz, PC + "steam_ejector" + n)
        tower(-376, cz, 7)
        self.prime(f"film prime unit -376 {b} {cz}")
        # delayed coker: twin coke drums on a concrete table, a cutting derrick over them
        f.fill(-389, b, cz - 3, -383, b + 4, cz + 3, "gray_concrete")
        f.fill(-388, b, cz - 2, -384, b + 3, cz + 2, "air")
        for dz in (-2, 2):
            for dx in (-1, 0, 1):
                for ddz in (-1, 0, 1):
                    if abs(dx) + abs(ddz) < 2:
                        f.fill(-386 + dx, b + 5, cz + dz + ddz, -386 + dx, b + 14, cz + dz + ddz, "light_gray_terracotta")
        f.fill(-387, b + 15, cz - 3, -385, b + 22, cz - 3, "iron_bars")
        f.fill(-387, b + 15, cz + 3, -385, b + 22, cz + 3, "iron_bars")
        f.fill(-387, b + 22, cz - 3, -385, b + 22, cz + 3, "polished_blackstone")
        f.setb(-386, b, cz, PC + "delayed_coker" + n)
        self.prime(f"film prime unit -386 {b} {cz}")
        # FCC: reactor (riser) and the bigger regenerator side by side, a crossover between their tops
        f.setb(-396, b, cz, PC + "fcc_unit" + n)
        for (cx, r, h, blk) in ((-398, 1, 14, "white_concrete"), (-394, 2, 11, "light_gray_concrete")):
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if dx * dx + dz * dz <= r * r + 1:
                        f.fill(cx + dx, b + 1, cz + 4 + dz, cx + dx, b + h, cz + 4 + dz, blk)
        f.fill(-397, b + 12, cz + 4, -395, b + 12, cz + 4, "polished_deepslate")
        f.fill(-394, b + 12, cz + 4, -394, b + 20, cz + 4, PC + "chimney")      # flue gas stack
        # hydrotreater with the Claus unit east of it (it takes the H2S, RefineryUpgradingGameTests), sulfur pit
        f.setb(-406, b, cz, PC + "hydrotreater" + n)
        f.setb(-405, b, cz, PC + "claus_unit" + n)
        f.fill(-407, b + 1, cz, -407, b + 7, cz, "white_concrete")             # reactor vessel
        f.fill(-406, b + 1, cz - 2, -406, b + 4, cz - 2, "bricks")             # charge heater
        f.fill(-406, b + 5, cz - 2, -406, b + 9, cz - 2, PC + "chimney")
        f.fill(-405, b + 1, cz + 1, -403, b + 1, cz + 3, "yellow_concrete")    # sulfur pit
        f.fill(-404, b + 1, cz + 2, -404, b + 2, cz + 2, "yellow_concrete_powder")
        self.prime(f"film prime unit -406 {b} {cz}")
        # catalytic reformer: three reactors in a row and the fired heater box with its stacks
        f.setb(-415, b, cz, PC + "catalytic_reformer" + n)
        for i in range(3):
            f.fill(-418 + 3 * i, b + 1, cz + 3, -418 + 3 * i, b + 6 + i, cz + 3, "white_concrete")
        f.fill(-419, b, cz - 4, -411, b + 4, cz - 2, "bricks")
        for x in (-418, -415, -412):
            f.fill(x, b + 5, cz - 3, x, b + 11, cz - 3, PC + "chimney")
        self.prime(f"film prime unit -415 {b} {cz}")
        # amine regenerator and absorber: two short columns of tray sections
        f.setb(-424, b, cz, "polished_andesite")
        tower(-424, cz, 3)
        f.setb(-426, b, cz, PC + "amine_unit" + n)
        tower(-426, cz, 4)
        # acid plant on Claus sulfur, gasoline blender with its run-down tanks
        f.setb(-431, b, cz, PC + "acid_plant" + n)
        f.fill(-432, b + 1, cz + 2, -430, b + 9, cz + 2, "light_blue_terracotta")
        self.prime(f"film prime unit -431 {b} {cz}")
        f.setb(-437, b, cz, PC + "gasoline_blender" + n)
        for x in range(-436, -369, 9):                                          # product tanks along the south edge
            self.tank(x, y, 819, 2, 5, "white_concrete")
        # pipe rack along the north side of the line, on to the crude unit of the oil set
        self.pipe_rack(x1, 807, -362, 807, y)
        for x in range(x1 + 4, x2, 12):                                         # yard lights
            f.fill(x, b, 810, x, b + 5, 810, "polished_blackstone_wall")
            f.setb(x, b + 6, 810, "polished_blackstone")
            f.setb(x, b + 6, 811, PC + "industrial_light_street[facing=south,lit=true]")
        self.computer(-372, b, 820, "north", "device_logger")
        print(f"refinery: strip at y {y}")

    def street_lights(self, pts, step=16, side=5):
        for (ax, az), (bx, bz) in zip(pts, pts[1:]):
            n = max(abs(bx - ax), abs(bz - az))
            for t in range(0, n, step):
                x = round(ax + (bx - ax) * t / n)
                z = round(az + (bz - az) * t / n) - side
                g = self.s.floor(x, z)
                if self.s.water(x, z):
                    continue
                self.f.fill(x, g + 1, z, x, g + 6, z, "polished_blackstone_wall")
                self.f.setb(x, g + 7, z, "polished_blackstone")
                self.f.setb(x, g + 7, z + 1, PC + "industrial_light_street[facing=south,lit=true]")

    def build(self):
        self.catalog_halls()
        self.npp_turbine_hall()                 # before the containment: its terrace must not cut the ring
        self.containment()
        y = ground_y(self.s, (-101, 691, -51, 713))
        self.terrace(-103, 689, -49, 715, y, margin=8)
        for cx in (-90, -62):
            self.cooling_tower(cx, y, 702, 11, 44)
        self.tank_farm(-360, 832, 4, 2, 5, 9, "white_concrete")          # crude and products
        self.refinery()
        self.tank_farm(-150, 712, 2, 3, 4, 7, "light_gray_concrete")     # chemicals
        self.pipe_rack(-300, 812, -200, 812, 66, avoid=((774, 798),))    # refinery -> chemistry, over the road
        self.pipe_rack(-200, 762, -200, 812, 66, avoid=((774, 798),))


def write2(game):
    site = Sites([Site(os.path.join(game, f)) for f in (SITE_FILE, "gorge_site.json", "gorge_north.json", "line_site.json")])
    p = Plant2(site)
    p.build()
    lines = [l for l in p.f.lines if l]
    with open(os.path.join(game, "plant2.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print(f"plant2.txt: {len(lines)} commands; {p.ys}")


LAMP = re.compile(r"^(?:setblock|fill) (-?\d+) (-?\d+) (-?\d+) (?:-?\d+ -?\d+ -?\d+ )?promptcritical:(industrial_light\w*|lamp_\w+)")


def lighting(p, cell=32):
    """Mains for every lamp: the mod's lamps light only from a lighting board on a live 0.4 kV network. Under each
    32x32 group of lamps, 4+ blocks under the lowest floor, a sealed chamber holds the chain of GridGameTests
    lighting test: 6.3 kV shaft generator on a Create motor -> cable -> transformer 6/0.4 kV -> cable -> board. The
    board feeds every lamp within 24 blocks; `film prime generator` puts the field on."""
    groups = {}
    for line in p.f.lines:
        m = LAMP.match(line)
        if m:
            x, y, z = int(m.group(1)), int(m.group(2)), int(m.group(3))
            groups.setdefault((x // cell, z // cell), []).append((x, y, z))
    f = p.f
    for (gx, gz), lamps in sorted(groups.items()):
        bx, bz = gx * cell + cell // 2, gz * cell + cell // 2
        ys = [l[1] for l in lamps]
        by = min(max(ys) - 22, min(ys) - 6, p.s.floor(bx, bz) - 5)
        ax, ay, az = bx + 3, by, bz - 2            # generator; the board sits at (ax - 3, ay, az + 2)
        f.fill(ax - 5, ay - 1, az - 2, ax + 2, ay + 3, az + 4, "stone")
        f.fill(ax - 4, ay, az - 1, ax + 1, ay + 2, az + 3, "air")
        f.setb(ax, ay, az, PC + "shaft_generator[facing=north]")
        f.setb(ax, ay + 1, az, PC + "generator_exciter[facing=north]")
        f.setb(ax, ay, az + 1, "create:creative_motor[facing=north]{ScrollValue:64}")
        for c in ((ax - 1, ay, az), (ax - 1, ay + 1, az), (ax - 2, ay + 1, az), (ax - 3, ay, az + 1)):
            f.setb(*c, PC + "power_cable")
        f.setb(ax - 3, ay, az, PC + "distribution_transformer[facing=north]")
        f.setb(ax - 3, ay, az + 2, PC + "lighting_board[facing=south]")
        p.prime(f"film prime generator {ax} {ay} {az}")
        p.protect.append((ax - 5, az - 2, ax + 2, az + 4))   # its chunk must stay loaded or the lamps go dark
    print(f"lighting: {len(groups)} boards for {sum(len(v) for v in groups.values())} lamps")


def write(game):
    site = Site(os.path.join(game, SITE_FILE))
    p = Plant2(site)
    p.sets()
    p.tpp()
    gates, turbines = p.hydro_gorge(site)
    p.pole_line([(70, 800), (400, 760), (800, 720), (1110, 700)], step=28)
    mills = p.wind()
    p.wind_grid(mills)
    units = p.tidal()
    p.build()
    p.roads()
    p.rail()
    lighting(p)
    # wind turbines last and one by one, 30 s apart: eight induction generators closing onto the bus in the same
    # tick pull it below the live threshold, drop off again and trip on overspeed
    # (first park them all, so turbines left enabled from an earlier run do not start together)
    p.prime.lines[:0] = [c.replace("film prime wind ", "film prime wind_park ") for c in p.wind_starts]
    for cmd in p.wind_starts:
        p.prime("wait 600")
        p.prime(cmd)
    lines = [l for l in p.f.lines if l]
    with open(os.path.join(game, "plant.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    # forceload only the built-on areas: machines there tick without the player nearby (simulation distance 6)
    chunks = set()
    for x1, z1, x2, z2 in p.protect:
        for cx in range(x1 >> 4, (x2 >> 4) + 1):
            for cz in range(z1 >> 4, (z2 >> 4) + 1):
                chunks.add((cx, cz))
    with open(os.path.join(game, "plant_load.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(f"forceload add {cx * 16} {cz * 16}" for cx, cz in sorted(chunks)) + "\n")
    print(f"plant_load.txt: {len(chunks)} chunks")
    with open(os.path.join(game, "plant_prime.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(l for l in p.prime.lines if l) + "\n")
    # what `/film feed plant_feed <s>` repeats: the units the GameTests top up every tick (refinery feeds, coke ovens,
    # blast furnace charge); scenes that only switch things on are left out
    feed = [l for l in p.prime.lines if len(l.split()) > 2 and l.split()[2] in ("unit", "refinery", "coke", "blast_furnace", "drill", "pyrolysis")]
    with open(os.path.join(game, "plant_feed.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(feed) + "\n")
    # one command after every launch: `/film run plant_start` = forceload, let block entities attach, prime, keep fed
    with open(os.path.join(game, "plant_start.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(["gamemode spectator @a", "gamerule doDaylightCycle false", "time set 6000"]
                           + [f"forceload add {cx * 16} {cz * 16}" for cx, cz in sorted(chunks)] + ["wait 200"]
                           + [l for l in p.prime.lines if l] + ["film feed plant_feed 30"]) + "\n")
    layout = {"ys": p.ys, "gates": gates, "turbines": turbines, "wind": mills, "tidal": units, "computers": p.computers}
    with open(os.path.join(game, "plant_layout.json"), "w", encoding="utf-8") as fh:
        json.dump(layout, fh, indent=1)
    print(f"plant.txt: {len(lines)} commands, prime {len(p.prime.lines)}; terraces {p.ys}")
    return p


def startup(world, computers):
    """CC: Tweaked computers boot into their pcui program."""
    for cid, prog in computers.items():
        d = os.path.join(world, "computercraft", "computer", str(cid))
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "startup.lua"), "w") as fh:
            fh.write(f'shell.run("/rom/programs/pc/{prog}")\n')


if __name__ == "__main__":
    game = sys.argv[1]
    if len(sys.argv) > 2 and sys.argv[2] == "v2":
        write2(game)
        sys.exit()
    plant = write(game)
    startup(os.path.join(game, "saves", "Plant"), {0: "alarm_screen", **plant.computers})
