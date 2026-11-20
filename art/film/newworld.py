"""Create a singleplayer world with normal overworld generation and a given seed, from an existing level.dat.

    python art/film/newworld.py <template world dir> <new world dir> <seed> [<spawn x> <spawn z>]

Keeps game rules, enabled datapacks and other settings of the template; switches the overworld to the vanilla noise
generator, drops the saved player (it spawns fresh at the spawn point), sets creative mode with cheats. Refuses to
overwrite an existing world.
"""
import os
import sys
import time

import nbtlib
from nbtlib.tag import Byte, Compound, Int, Long, String


def main(src, dst, seed, sx=None, sz=None):
    if os.path.exists(dst):
        sys.exit(f"{dst} exists, refusing to overwrite")
    f = nbtlib.load(os.path.join(src, "level.dat"))
    d = f["Data"]
    w = d["WorldGenSettings"]
    w["seed"] = Long(seed)
    w["generate_features"] = Byte(1)
    w["dimensions"]["minecraft:overworld"]["generator"] = Compound({
        "type": String("minecraft:noise"),
        "settings": String("minecraft:overworld"),
        "biome_source": Compound({"type": String("minecraft:multi_noise"), "preset": String("minecraft:overworld")}),
    })
    for k in ("Player", "DragonFight", "CustomBossEvents", "ScheduledEvents"):
        d.pop(k, None)
    d["LevelName"] = String(os.path.basename(os.path.normpath(dst)))
    d["initialized"] = Byte(0)          # the server looks for a spawn point on first load
    d["GameType"] = Int(1)
    d["allowCommands"] = Byte(1)
    d["Time"] = Long(0)
    d["DayTime"] = Long(6000)
    d["LastPlayed"] = Long(int(time.time() * 1000))
    if sx is not None:
        d["SpawnX"], d["SpawnZ"], d["SpawnY"] = Int(sx), Int(sz), Int(100)
        d["initialized"] = Byte(1)
    os.makedirs(dst)
    f.save(os.path.join(dst, "level.dat"))
    print(f"world {dst}: seed {seed}")


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) not in (3, 5):
        sys.exit(__doc__)
    main(a[0], a[1], int(a[2]), *(int(v) for v in a[3:]))
