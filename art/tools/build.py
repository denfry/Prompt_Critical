"""Generate all placeholder textures, item models, fluid textures and the Blockbench spec.

usage: python art/tools/build.py <repo_root> <spec_out.json> [--only-check]
Blockbench step (models -> .bbmodel + Java JSON) reads spec_out.json.
"""
import glob, importlib, json, os, sys, zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pclib  # noqa: E402
import numpy as np  # noqa: E402

ROOT = sys.argv[1]
SPEC_OUT = sys.argv[2]
CHECK_ONLY = "--only-check" in sys.argv
ASSETS = os.path.join(ROOT, "pc-mod/src/main/resources/assets/promptcritical")
pclib.TEX = os.path.join(ASSETS, "textures")

# Шум текстур берётся из общего pclib.rng в порядке регистрации: исходные
# области грузятся первыми, волна M2–M3 — за ними, вторая волна (artkit2) —
# последней, чтобы новые модули не сдвигали шум уже готовых PNG.
LEGACY = ["base", "fuelcycle", "mining", "plant"]
WAVE1 = ["automation", "content", "decor", "grid", "metallurgy", "metitems"]


def module_order(path):
    area = os.path.basename(path)[:-3].split("_", 1)[1]
    if area in LEGACY:
        return (0, LEGACY.index(area))
    return (1, WAVE1.index(area)) if area in WAVE1 else (2, area)


errors = []
owner = {}
for kind in ("tex", "spec"):
    for path in sorted(glob.glob(os.path.join(HERE, f"{kind}_*.py")), key=module_order):
        mod = os.path.basename(path)[:-3]
        before_t, before_m = set(pclib.TEXTURES), len(pclib.M)
        before_i, before_f = len(pclib.ITEMS), len(pclib.FLUIDS)
        importlib.import_module(mod)
        for k in set(pclib.TEXTURES) - before_t:
            owner[k] = mod
        for m in pclib.M[before_m:]:
            m["_mod"] = mod

# ---- validation ----
ids = {}
for m in pclib.M:
    if m["id"] in ids:
        errors.append(f"duplicate model id {m['id']} ({ids[m['id']]} / {m['_mod']})")
    ids[m["id"]] = m["_mod"]
    for k, rel in m["textures"].items():
        if rel not in pclib.TEXTURES:
            errors.append(f"{m['id']}: texture '{rel}' not registered")
    for e in m["elements"]:
        for v in e["from"] + e["to"]:
            if v < -16 or v > 32:
                errors.append(f"{m['id']}/{e['name']}: coord {v} outside -16..32")
        if any(a > b for a, b in zip(e["from"], e["to"])):
            errors.append(f"{m['id']}/{e['name']}: from > to")
        if "rot" in e and e["rot"]["angle"] not in (-45, -22.5, 0, 22.5, 45):
            errors.append(f"{m['id']}/{e['name']}: bad angle {e['rot']['angle']}")
        for k in [e["tex"], *e["faces"].values()]:
            if k not in m["textures"]:
                errors.append(f"{m['id']}/{e['name']}: texture key '{k}' missing")
item_ids = set()
for it in pclib.ITEMS:
    if it["id"] in item_ids:
        errors.append(f"duplicate item {it['id']}")
    item_ids.add(it["id"])
    if it["tex"] not in pclib.TEXTURES:
        errors.append(f"item {it['id']}: texture '{it['tex']}' not registered")
fluid_ids = set()
for fl in pclib.FLUIDS:
    if fl["id"] in fluid_ids:
        errors.append(f"duplicate fluid {fl['id']}")
    fluid_ids.add(fl["id"])

if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"OK: {len(pclib.TEXTURES)} textures, {len(pclib.M)} models, {len(pclib.ITEMS)} items, {len(pclib.FLUIDS)} fluids")
if CHECK_ONLY:
    sys.exit(0)

# ---- write ----
for rel, fn in pclib.TEXTURES.items():
    pclib.save(fn(), rel)

for it in pclib.ITEMS:
    p = os.path.join(ASSETS, "models/item", it["id"] + ".json")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({"parent": "minecraft:item/generated",
               "textures": {"layer0": "promptcritical:" + it["tex"][:-4]}}, open(p, "w"), indent="\t")


def fluid_strip(base, n, frames, gas):
    out = np.zeros((n * frames, n, 4))
    c = pclib.hexc(base)
    rnd = np.random.default_rng(zlib.crc32(base.encode()))
    field = rnd.random((n, n))
    for f in range(frames):
        ph = 2 * np.pi * f / frames
        y, x = np.mgrid[0:n, 0:n]
        w = 0.5 + 0.25 * np.sin(2 * np.pi * x / n + ph) * np.cos(2 * np.pi * y / n - ph) + 0.25 * np.roll(field, f, axis=0)
        fr = np.zeros((n, n, 4))
        fr[..., :3] = c[:3] * (0.82 + 0.3 * w[..., None])
        fr[..., 3] = (120 + 60 * w) if gas else 255
        out[f * n:(f + 1) * n] = fr
    return out


for fl in pclib.FLUIDS:
    gas = fl["kind"] == "gas"
    for suffix, n in (("still", 16), ("flow", 32)):
        rel = f"block/fluid/{fl['id']}_{suffix}.png"
        pclib.save(fluid_strip(fl["base"], n, 16, gas), rel)
        json.dump({"animation": {"frametime": 2}}, open(os.path.join(pclib.TEX, rel + ".mcmeta"), "w"))

json.dump(pclib.M, open(SPEC_OUT, "w"), indent=1, ensure_ascii=False)
json.dump({"items": pclib.ITEMS, "fluids": pclib.FLUIDS,
           "texture_owner": owner}, open(os.path.join(os.path.dirname(SPEC_OUT), "registry.json"), "w"), indent=1)
print("written")
