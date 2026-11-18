"""Детальные модели M2 (06 §6.2, статус MODELED): ещё не перерисованные вручную
в Blockbench (котельная I0 — в art/blockbench/boilers/*.author.js). Заменяют плейсхолдеры `spec_grid`/`spec_states`."""
import math

from artkit import *

V = lambda n: f"block/common/m2_{n}.png"
S = lambda n: f"block/common/state_{n}.png"
OWN = {
    "shell": V("boiler_shell"), "front": V("boiler_front"), "door": V("fire_door"), "doorl": V("glow_fire_door"),
    "ash": V("ash_door"), "br": V("brass"), "gg": V("gauge_glass"), "sb": V("setting_brick"), "ci": V("cast_iron"),
    "pol": V("steel_polished"), "hw": V("handwheel"), "vb": V("valve_body"), "smk": V("smokebox"), "lag": V("lagging"),
    "eng": V("engine_paint"), "engp": V("engine_plain"), "chain": V("chain"), "coal": V("coal_bed"), "coall": V("glow_coal_bed"),
    "chb": V("chimney_brick"), "band": V("iron_band"), "sootc": V("soot_cap"),
    "gle": S("gauge_glass_empty"), "tint": S("heat_tint"), "crown": S("glow_crown"), "torn": S("riveted_torn"),
    "soot": S("soot"), "dw": S("boiler_door_wrecked"),
}


def m(id_, cat, els, kind="block"):
    mk(id_, cat, els, OWN, kind)


def ring(name, axis, r, w, a0, a1, tex, c=(8, 8, 8), n=16):
    """Обод (маховик, штурвал): n касательных брусков на радиусе r, толщина w, по оси — a0…a1.

    Брусок ставится в одно из четырёх базовых положений (0°, 90°, 180°, 270°) и доворачивается на остаток
    из {0, ±22,5, ±45} — Java допускает только эти углы на одной оси."""
    plane = [ax for ax in "xyz" if ax != axis]
    half_len = r * math.tan(math.pi / n) + w * 0.35
    out = []
    for k in range(n):
        ang = 360.0 * k / n
        base = int(round(ang / 90.0)) % 4
        rest = ang - 90.0 * base
        if rest > 180:
            rest -= 360
        # базовое положение: центр бруска на радиусе по направлению base (0: +p0, 1: +p1, 2: −p0, 3: −p1)
        d0, d1 = [(1, 0), (0, 1), (-1, 0), (0, -1)][base]
        ctr = {plane[0]: c["xyz".index(plane[0])] + d0 * r, plane[1]: c["xyz".index(plane[1])] + d1 * r}
        radial = {plane[0]: w / 2 if d0 else half_len, plane[1]: w / 2 if d1 else half_len}
        f, t = [0, 0, 0], [0, 0, 0]
        for i, ax in enumerate("xyz"):
            if ax == axis:
                f[i], t[i] = a0, a1
            else:
                f[i], t[i] = ctr[ax] - radial[ax], ctr[ax] + radial[ax]
        rot = (axis, rest, list(c)) if rest else None
        out.append(el(f"{name}_{k}", [round(v, 3) for v in f], [round(v, 3) for v in t], tex, rot=rot))
    return out


def wheel(name, axis, r, a0, a1, c, spokes_tex="hw"):
    """Штурвал вентиля: обод и крестовина."""
    i = "xyz".index(axis)
    els = ring(name, axis, r, 0.8, a0, a1, "hw", c=c, n=8)
    plane = [k for k in range(3) if k != i]
    for j, p in enumerate(plane):
        f, t = list(c), list(c)
        f[i], t[i] = a0, a1
        f[p], t[p] = c[p] - r, c[p] + r
        q = plane[1 - j]
        f[q], t[q] = c[q] - 0.4, c[q] + 0.4
        els.append(el(f"{name}_spoke_{j}", f, t, spokes_tex))
    return els


# ======================================================================
# жаротрубный котёл Ланкашир 3×3×6 нарисован вручную в Blockbench:
# art/blockbench/boilers/fire_tube_boiler.author.js (модели, текстуры textures/block/fire_tube_boiler/)
# ======================================================================


# ======================================================================
# цепная решётка нарисована вручную в Blockbench: art/blockbench/boilers/grate_stoker.author.js
# ======================================================================


# ======================================================================
# кирпичная дымовая труба нарисована вручную в Blockbench: art/blockbench/boilers/chimney.author.js
# (модели chimney, chimney_base, chimney_top, текстуры textures/block/chimney/)
# ======================================================================


# ======================================================================
# горизонтальная паровая машина 3×3×5 нарисована вручную в Blockbench:
# art/blockbench/boilers/steam_engine.author.js (модели, подвижные части, текстуры textures/block/steam_engine/)
# ======================================================================


# ======================================================================
# шахта: крепь, мехкрепь, комбайн, насосы, подъёмная машина
# ======================================================================
OWN.update({
    "log": V("timber_log"), "plank": V("plank"), "yel": V("mining_yellow"), "org": V("mining_orange"),
    "haz": V("hazard_band"), "ribs": V("motor_ribs"), "vane": V("drum_vane"), "pick": V("pick"),
    "mgrn": "block/common/metal_green.png", "dg": "block/common/grid_dark_grey.png", "r35": "block/common/grid_ral7035.png",
})


def hcyl(name, axis, r, a0, a1, tex, c=(8, 8, 8), sides=16):
    """Горизонтальный цилиндр с торцами, поджатыми к пределу −16…32 (disc разносит торцы на 0,01·i)."""
    return disc(name, axis, r, max(a0, -15.92), min(a1, 31.92), tex, sides, c=c)


# деревянный оклад (целый и сломанный) нарисован вручную в Blockbench: art/blockbench/mining/mine_support.author.js

# механизированная крепь нарисована вручную в Blockbench: art/blockbench/mining/powered_roof_support.author.js

# очистной комбайн нарисован вручную в Blockbench: art/blockbench/mining/longwall_shearer.author.js

# вакуум-насос дегазации нарисован вручную в Blockbench: art/blockbench/mining/degasification_pump.author.js

# насос водоотлива нарисован вручную в Blockbench: art/blockbench/mining/mine_drainage_pump.author.js

# подъёмная машина нарисована вручную в Blockbench: art/blockbench/mining/mine_hoist.author.js
# (модель, барабан и курсор указателя глубины — partial, текстуры textures/block/mine_hoist/)


# ======================================================================
# литейка, механический цех, углеобогащение
# ======================================================================
OWN.update({
    "mach": V("machine"), "machs": V("machine_smooth"), "refr": V("refractory"), "fsand": V("foundry_sand"),
    "tslot": V("tslot"), "patw": V("pattern_wood"), "melt": V("glow_metal"), "shellb": V("shell_black"),
    "graph": "block/common/graphite.png", "water": "block/common/fuelcycle_water.png",
})


def vcyl(name, cx, cz, r, y0, y1, tex, sides=16):
    return disc(name, "y", r, max(y0, -15.92), min(y1, 31.92), tex, sides, c=(cx, 8, cz))


# вагранка нарисована вручную в Blockbench: art/blockbench/foundry/cupola_furnace.author.js


# тигельная печь нарисована вручную в Blockbench: art/blockbench/foundry/crucible_furnace.author.js


# песчаная форма, формовочная машина и выбивная решётка нарисованы вручную в Blockbench:
# art/blockbench/foundry/molding.author.js

# токарный, сверлильный, фрезерный, строгальный и плоскошлифовальный станки нарисованы вручную в Blockbench:
# art/blockbench/machine_shop/machine_tools.author.js


# отсадочная машина нарисована вручную в Blockbench: art/blockbench/coal_prep/coal_jig.author.js

# ---- ветроэлектростанция Д-30 (плейсхолдер до ручной модели): решётчатая башня 1×9×1 из трёх секций, гондола
# и одна лопасть (рендер ставит её трижды через 120° и масштабирует колесо ×3, гондолу ×1,5 вокруг ступицы 8, 8, 8)
def wind_lattice(y0, y1, top_rail=True):
    """Секция решётчатой башни y0…y1 (пиксели): четыре уголка-пояса, раскосы зигзагом по граням, распорки."""
    els = [el(f"leg_{i}", [x, y0, z], [x + 2, y1, z + 2], "gal")
           for i, (x, z) in enumerate(((0, 0), (14, 0), (0, 14), (14, 14)))]
    half = 8 * math.sqrt(2) - 1
    for k, y in enumerate(range(int(y0), int(y1), 16)):
        ang = 45 if k % 2 == 0 else -45
        c = y + 8
        els += [
            el(f"brace_n{k}", [8 - half, c - 0.5, 0.4], [8 + half, c + 0.5, 1.2], "gal", rot=("z", ang, [8, c, 0.8])),
            el(f"brace_s{k}", [8 - half, c - 0.5, 14.8], [8 + half, c + 0.5, 15.6], "gal", rot=("z", -ang, [8, c, 15.2])),
            el(f"brace_w{k}", [0.4, c - 0.5, 8 - half], [1.2, c + 0.5, 8 + half], "gal", rot=("x", ang, [0.8, c, 8])),
            el(f"brace_e{k}", [14.8, c - 0.5, 8 - half], [15.6, c + 0.5, 8 + half], "gal", rot=("x", -ang, [15.2, c, 8])),
        ]
        if top_rail or y + 16 < y1:
            els += [el(f"strut_x{k}", [2, y + 15, 0.5], [14, y + 16, 1.5], "gal"),
                    el(f"strut_x2{k}", [2, y + 15, 14.5], [14, y + 16, 15.5], "gal"),
                    el(f"strut_z{k}", [0.5, y + 15, 2], [1.5, y + 16, 14], "gal"),
                    el(f"strut_z2{k}", [14.5, y + 15, 2], [15.5, y + 16, 14], "gal")]
    return els


m("wind_turbine", "grid", [
    el("foundation", [-2, -16, -2], [18, -14, 18], "c"),
] + wind_lattice(-16, 32) + [
    el("cabinet", [3, -14, 1.5], [13, -3, 5], "cab", faces={"north": "pn"}, full=["north"]),
    el("cabinet_lamp", [10, -5.5, 1], [11.5, -4, 1.5], "lr"),
    el("cable_entry", [6, -14, 5], [10, -12, 9], "d"),
])
m("wind_turbine_middle", "grid", wind_lattice(-16, 32))
m("wind_turbine_top", "grid", wind_lattice(-16, 28, top_rail=False) + [
    el("platform", [-1, 28, -1], [17, 30, 17], "p"),
    el("yaw_ring", [2, 30, 2], [14, 32, 14], "d"),
    el("rail_n", [-1, 30, -1], [17, 31, 0], "gal"), el("rail_s", [-1, 30, 16], [17, 31, 17], "gal"),
])
m("wind_turbine/nacelle", "grid", [
    el("bedplate", [4, 3, 8], [12, 5, 28], "d"),
    el("housing", [4.5, 5, 7], [11.5, 12, 27], "eng"),
    el("roof", [5.5, 12, 9], [10.5, 13, 25], "eng"),
    el("generator", [5.5, 6, 20], [10.5, 11, 30], "mb"),
    el("main_shaft", [7, 7, 3], [9, 9, 7], "st"),
    el("spinner", [5.5, 5.5, 2.5], [10.5, 10.5, 6], "st"),
    el("spinner_tip", [6.5, 6.5, 1], [9.5, 9.5, 2.5], "st"),
    el("vane_post", [7.5, 13, 24], [8.5, 17, 25], "gal"),
    el("anemometer", [6, 17, 23.5], [10, 17.5, 25.5], "d"),
], kind="partial")
m("wind_turbine/blade", "grid", [
    el("root", [7, 9, 5], [9, 11, 7], "st"),
    el("blade_inner", [6, 11, 5.5], [10, 19, 6.5], "p"),
    el("blade_mid", [6.5, 19, 5.6], [9.5, 26, 6.4], "p"),
    el("blade_tip", [7, 26, 5.7], [9, 32, 6.3], "p"),
    el("stabilizer", [9.5, 22, 6.4], [11, 26, 6.9], "red"),
], kind="partial")
