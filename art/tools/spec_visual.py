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

# ---- ветроэлектростанция Д-30 нарисована вручную в Blockbench: art/blockbench/grid/wind_turbine.author.js


# ---- ламповая (плейсхолдер до ручной модели): зарядный стеллаж на 12 головных светильников и сам светильник
def rack_lamp(name, x, y, z):
    """Светильник на зарядном месте: батарея (корпус), фара на крюке, лампочка заряда."""
    return [el(f"{name}_battery", [x, y, z], [x + 3, y + 4, z + 2.5], "d"),
            el(f"{name}_head", [x + 0.5, y + 4.5, z + 0.2], [x + 2.5, y + 6, z + 1.8], "b"),
            el(f"{name}_glass", [x + 1, y + 4.8, z - 0.01], [x + 2, y + 5.7, z + 0.2], "gl"),
            el(f"{name}_led", [x + 1.2, y + 2, z - 0.01], [x + 1.8, y + 2.6, z + 0.1], "lg")]


# нарисовано вручную в Blockbench: art/blockbench/turbine/power_misc.author.js
m("cap_lamp", "mining", [
    el("battery", [4, 0, 6], [12, 9, 10], "d"),
    el("battery_lid", [4.5, 9, 6.5], [11.5, 10, 9.5], "st"),
    el("cord", [7.5, 10, 7.5], [8.5, 13, 8.5], "r"),
    el("headpiece", [5.5, 12, 5], [10.5, 16, 10], "b"),
    el("reflector_glass", [6.5, 13, 4.5], [9.5, 15, 5], "gl"),
    el("belt_loop", [3.5, 3, 7], [4, 7, 9], "r"),
], kind="item")


# ---- приливная электростанция (плейсхолдер до ручной модели): капсульный агрегат 3×2×3 в бетонном водоводе плотины
# (фасад — море, тыл — бассейн), вал Create вверх из машинного зала; мареограф — футшток с поплавковым колодцем
# нарисовано вручную в Blockbench: art/blockbench/turbine/power_misc.author.js


# ---- накопители (плейсхолдер до ручной модели): обратимый агрегат ГАЭС 3×2×3 — спиральная камера, двигатель-генератор
# сверху, напорный водовод сзади; электродный котёл — вертикальный цилиндрический корпус с проходными изоляторами
# нарисовано вручную в Blockbench: art/blockbench/turbine/power_misc.author.js
