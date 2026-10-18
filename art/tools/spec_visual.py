"""Детальные модели M2 (06 §6.2, статус MODELED): котельная I0 — жаротрубный котёл Ланкашир в обмуровке, цепная
решётка, кирпичная труба, горизонтальная паровая машина. Заменяют плейсхолдеры `spec_grid`/`spec_states`.

Кинематика, на которую завязаны рендереры, сохранена: стекло котла — `FireTubeBoilerRenderer`, ось цилиндра
y = 4 и крейцкопф z ≈ 16, коленвал рамы на (y, z) = (8, 8), кривошип 5 px, шатун 20 px — `SteamEngineRenderer`,
шаг цепи 4 px — `GrateStokerRenderer`; выход пара клапана (8, 28, 6) и дверца (8, −4, −17) — частицы котла."""
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
# жаротрубный котёл Ланкашир 3×3×6 в кирпичной обмуровке
# ======================================================================

def setting(z0, z1):
    """Обмуровка: боковые стенки и под с нижним газоходом."""
    return [el("setting_w", [-16, -16, z0], [-7, 4, z1], "sb"),
            el("setting_e", [23, -16, z0], [32, 4, z1], "sb"),
            el("setting_floor", [-7, -16, z0], [23, -11, z1], "sb"),
            el("coping_w", [-16, 4, z0], [-6, 5, z1], "ci"),
            el("coping_e", [22, 4, z0], [32, 5, z1], "ci")]


def boiler_front(state="cold"):
    """Передний сегмент. state: cold, lit, low, low_lit, exploded."""
    lit = state in ("lit", "low_lit")
    wrecked = state == "exploded"
    shell_tex = "torn" if wrecked else "shell"
    els = disc("shell", "z", 15, -14, 31.9, shell_tex, faces={"north": "front"})
    els += disc("end_plate", "z", 15.6, -15, -14, "torn" if wrecked else "front")
    els += setting(-13, 32)
    els += [el("front_wall", [-16, -16, -15], [32, -7, -13], "sb"),
            el("front_wall_cap", [-16, -7, -15.5], [32, -6, -13], "ci")]
    for side, x in (("l", 0.5), ("r", 15.5)):
        door = "dw" if wrecked else ("doorl" if lit else "door")
        name = ("glow_" if lit else "") + f"door_{side}"
        els += [el(f"furnace_ring_{side}", [x - 6.5, -4.5, -15.5], [x + 6.5, 8.5, -15], "ci"),
                el(name, [x - 5.5, -3.5, -16], [x + 5.5, 7.5, -15.5], door, full=["north"],
                   rot=("y", 45, [x + 5.5, 2, -16]) if wrecked and side == "r" else None),
                el(f"ash_door_{side}", [x - 4, -13.5, -16], [x + 4, -8.5, -15], "ash", full=["north"])]
    if lit:
        els.append(el("glow_ash_glow", [-4, -13, -15.2], [20, -9, -15.1], "coall"))
    if state in ("low", "low_lit"):
        els.append(el("glow_crown_heat" if lit else "crown_tint", [-9, 8.6, -15.4], [25, 10.4, -15.3], "crown" if lit else "tint"))
    # водоуказательные стёкла (два, по правилам) — столбик воды рисует FireTubeBoilerRenderer
    glass = "soot" if wrecked else ("gle" if state.startswith("low") else "gg")
    for side, x in (("w", -3), ("e", 16)):
        els.append(el(f"glass_{side}", [x, 9, -16], [x + 3, 19, -15], glass, full=["north"]))
    if not wrecked:
        # манометр на сифонной трубке над фронтом
        els += [el("gauge_siphon", [7.5, 23, -15], [8.5, 25, -14], "br"),
                el("gauge_case", [4.5, 25, -15.5], [11.5, 32, -14], "br"),
                el("gauge_dial", [5, 25.5, -15.6], [11, 31.5, -15.5], "gauge", full=["north"])]
        # предохранительный клапан: выход пара (8, 28, 6)
        els += [el("safety_seat", [4.5, 22, 3], [11.5, 24, 9], "vb"),
                el("safety_body", [5.5, 24, 4], [10.5, 27, 8], "br"),
                el("safety_outlet", [7, 27, 5], [9, 28, 7], "br"),
                el("safety_lever", [7.5, 26, 8], [8.5, 27, 20], "pol"),
                el("safety_weight", [6.5, 24.5, 17], [9.5, 27.5, 20], "ci")]
        # стопорный вентиль на куполе
        els += [el("dome", [3, 22, 24], [13, 24, 32], "shell"),
                el("stop_valve", [5, 24, 25], [11, 28, 31], "vb"),
                el("stop_spindle", [7.5, 28, 27.5], [8.5, 30, 28.5], "pol")]
        els += wheel("stop_wheel", "y", 3.5, 30, 31, (8, 30.5, 28))
    else:
        els += [el("rupture", [0, 22.9, -8], [16, 23.3, 26], "soot"),
                el("flap_w", [-2, 22, -6], [1, 30, 24], "torn", rot=("z", 22.5, [0, 23, 8])),
                el("flap_e", [15, 22, -6], [18, 30, 24], "torn", rot=("z", -22.5, [16, 23, 8]))]
    return els


def boiler_rear(exploded=False):
    t = "torn" if exploded else "shell"
    els = disc("shell", "z", 15, -15.9, 28, t)
    els += setting(-16, 28)
    els += [el("back_wall", [-16, -16, 28], [32, 5, 32], "sb"),
            el("smokebox", [-8, 5, 27], [24, 22, 32], "torn" if exploded else "smk"),
            el("smokebox_door", [-2, 7, 26.5], [18, 20, 27], "ci"),
            el("uptake", [1, 22, 17], [15, 32, 31], "sb", rot=("x", 22.5, [8, 22, 29]) if exploded else None),
            el("uptake_flange", [0, 31, 16], [16, 32, 32], "ci")]
    if exploded:
        els.append(el("rupture", [0, 22.9, -16], [16, 23.3, 20], "soot"))
        return els
    els += [el("feed_check", [21, 12, 2], [27, 17, 8], "br"),
            el("feed_check_cap", [22, 17, 3], [26, 18, 7], "vb"),
            el("feed_pipe", [24, -2, 4], [26, 12, 6], "vb"),
            el("manhole", [3, 22, -10], [13, 23.5, -1], "smk"),
            el("blowdown", [6, -11, 24], [10, -7, 28], "vb")]
    els += [el(f"manhole_bolt_{i}", [3.5 + 2.5 * i, 23.5, -6], [4.5 + 2.5 * i, 24, -5], "br") for i in range(4)]
    return els


m("fire_tube_boiler", "boilers", boiler_front())
m("fire_tube_boiler_lit", "boilers", boiler_front("lit"))
m("fire_tube_boiler_low_water", "boilers", boiler_front("low"))
m("fire_tube_boiler_lit_low_water", "boilers", boiler_front("low_lit"))
m("fire_tube_boiler_exploded", "boilers", boiler_front("exploded"))
m("fire_tube_boiler_rear", "boilers", boiler_rear())
m("fire_tube_boiler_rear_exploded", "boilers", boiler_rear(True))


# ======================================================================
# цепная решётка ЛЦР: бункер с северной стороны, слой идёт к котлу (юг); вал Create слева (запад)
# ======================================================================

def stoker(running=False):
    bed = "coall" if running else "coal"
    return [el("frame_w", [0, 0, 0], [2, 10, 16], "ci"),
            el("frame_e", [14, 0, 0], [16, 10, 16], "ci"),
            el("foot_w", [-0.01, 0, 0], [2.5, 1.5, 16], "engp"),
            el("foot_e", [13.5, 0, 0], [16.01, 1.5, 16], "engp"),
            el("hopper_low", [2, 9, 0], [14, 12, 6], "engp"),
            el("hopper_high", [1, 12, 0], [15, 16, 7.5], "engp"),
            el("hopper_coal", [2, 15, 1], [14, 15.6, 6.5], "coal"),
            el("gate", [2, 8.6, 6], [14, 12, 7], "ci"),
            el("gate_handle", [7, 12, 6.25], [9, 14, 6.75], "br"),
            el(("glow_" if running else "") + "fire_bed", [2, 7.5, 7], [14, 8.6, 16], bed),
            el("arch", [2, 11, 8], [14, 13, 12], "sb"),
            el("sprocket_housing", [0.5, 2, 0], [15.5, 6, 3], "ci"),
            el("bearing_w", [-0.01, 5.5, 5.5], [1, 10.5, 10.5], "ci"),
            el("bearing_cap_w", [-0.02, 6.5, 6.5], [0.5, 9.5, 9.5], "br")]


m("grate_stoker", "boilers", stoker())
m("grate_stoker_running", "boilers", stoker(True))
m("grate_stoker/chain", "boilers", [
    el("chain_top", [2, 6.5, 0], [14, 7.5, 16], "chain"),
    el("chain_bottom", [2, 1, 0], [14, 2, 16], "chain"),
] + [el(f"bar_{i}", [2, 6.2, i * 4 + 0.5], [14, 7.8, i * 4 + 1.5], "pol") for i in range(4)]
  + [el(f"bar_low_{i}", [2, 0.7, i * 4 + 2.5], [14, 2.3, i * 4 + 3.5], "pol") for i in range(4)], kind="partial")


# ======================================================================
# кирпичная дымовая труба I0: цоколь с газоходом, ствол с обручами, оголовок с карнизом
# ======================================================================

def chimney(part):
    els = disc("stack", "y", 6, 0, 16, "chb")
    if part == "middle":
        els += ring("band", "y", 6.2, 0.8, 7, 8.5, "band", c=(8, 8, 8))
    if part == "base":
        els += [el("plinth", [0.5, 0, 0.5], [15.5, 4, 15.5], "sb"),
                el("plinth_cap", [1, 4, 1], [15, 5, 15], "ci"),
                el("flue_inlet", [4.5, 1, -0.01], [11.5, 9, 2], "smk"),
                el("cleanout", [5.5, 0.5, 13.99], [10.5, 3.5, 16.01], "ci")]
    if part == "top":
        els += disc("cornice", "y", 7, 12, 14, "chb", sides=8) + disc("cap", "y", 7.3, 14, 15, "band", sides=8)
        els += [el("soot_mouth", [3, 15.01, 3], [13, 15.02, 13], "sootc"),
                el("lightning_rod", [7.75, 15, 7.75], [8.25, 24, 8.25], "pol")]
    return els


m("chimney", "boilers", chimney("middle"))
m("chimney_base", "boilers", chimney("base"))
m("chimney_top", "boilers", chimney("top"))


# ======================================================================
# горизонтальная паровая машина 3×3×5: цилиндр в деревянной обшивке, золотник, параллели крейцкопфа, рама
# ======================================================================

m("steam_engine_cylinder", "boilers", [
    el("bedplate", [-6, -16, -14], [22, -12, 32], "eng"),
    el("cylinder_foot", [0, -12, -10], [16, -4, 2], "engp"),
] + disc("cylinder", "z", 8, -11, 3, "lag", c=(8, 4, 8))
  + disc("cover_front", "z", 9, -13, -11, "ci", c=(8, 4, 8))
  + disc("cover_back", "z", 9, 3, 6, "ci", c=(8, 4, 8))
  + [el("gland", [6, 2, 6], [10, 6, 9], "br"),
     el("valve_chest", [2, 11.5, -10], [14, 17, 2], "ci"),
     el("chest_cover", [1.5, 17, -10.5], [14.5, 18, 2.5], "eng"),
     el("steam_pipe", [6, 18, -7], [10, 22, -3], "vb"),
     el("throttle_valve", [5, 22, -8], [11, 25, -2], "vb"),
     el("throttle_spindle", [7.5, 25, -5.5], [8.5, 27, -4.5], "pol")]
  + wheel("throttle_wheel", "y", 3, 27, 28, (8, 27.5, -5))
  + [el("steam_main", [6.5, 25, -15], [9.5, 28, -8], "vb"),
     el("exhaust", [-2, 13, -6], [2, 16, -2], "ci"),
     el("exhaust_stack", [-2, 16, -5.5], [1, 32, -2.5], "ci"),
     el("guide_top", [4, 8, 6], [12, 9, 32], "pol"),
     el("guide_bottom", [4, -1, 6], [12, 0, 32], "pol"),
     el("guide_post_w", [3, -12, 28], [5, 9, 31], "engp"),
     el("guide_post_e", [11, -12, 28], [13, 9, 31], "engp"),
     el("governor_post", [16, 12, -2], [18, 24, 0], "br"),
     el("governor_head", [15, 24, -3], [19, 26, 1], "br"),
     el("governor_ball_w", [13, 21, -2], [15, 23, 0], "pol"),
     el("governor_ball_e", [19, 21, -2], [21, 23, 0], "pol"),
     el("drain_cock_f", [7, -5, -12], [9, -2, -10], "br"),
     el("lubricator", [7, 12, 3], [9, 15, 5], "br")])

m("steam_engine_cylinder_frame", "boilers", [
    el("bedplate", [-6, -16, 0], [30, -12, 26], "eng"),
    el("crank_pit", [2, -12, 0], [14, -9, 22], "ci"),
    el("pedestal_w", [-2, -12, 2], [2, 10, 14], "engp"),
    el("pedestal_e", [14, -12, 2], [18, 10, 14], "engp"),
    el("cap_w", [-2.5, 10, 3], [2.5, 12, 13], "br"),
    el("cap_e", [13.5, 10, 3], [18.5, 12, 13], "br"),
    el("cap_nut_w", [-1, 12, 4], [1, 13, 5], "ci"),
    el("cap_nut_w2", [-1, 12, 11], [1, 13, 12], "ci"),
    el("cap_nut_e", [15, 12, 4], [17, 13, 5], "ci"),
    el("cap_nut_e2", [15, 12, 11], [17, 13, 12], "ci"),
    el("outboard_bearing", [26, -12, 3], [30, 10, 13], "engp"),
    el("port_bearing", [30, 4, 4], [31.95, 12, 12], "ci"),
    el("outboard_cap", [25.5, 10, 4], [30.5, 12, 12], "br"),
    el("flywheel_pit", [18, -12, -2], [26, -11, 18], "ci"),
])

m("steam_engine_cylinder/flywheel", "boilers",
  ring("rim", "x", 13.5, 3, 20, 24, "ci") + disc("hub", "x", 2.6, 19, 25, "pol", 8)
  + [el("spoke_v", [21, -5, 7], [23, 21, 9], "ci"), el("spoke_h", [21, 7, -5], [23, 9, 21], "ci"),
     el("spoke_d1", [21, -5, 7], [23, 21, 9], "ci", rot=("x", 45, [22, 8, 8])),
     el("spoke_d2", [21, -5, 7], [23, 21, 9], "ci", rot=("x", -45, [22, 8, 8]))],
  kind="partial")
m("steam_engine_cylinder/crank", "boilers", disc("shaft", "x", 2, -6, 31.9, "pol", 8) + [
    el("web_w", [4.5, 6, 6], [6, 15.5, 10], "ci"),
    el("web_e", [10, 6, 6], [11.5, 15.5, 10], "ci"),
    el("pin", [6, 12, 7], [10, 14, 9], "pol"),
    el("eccentric", [12, 5, 5], [13.5, 12, 12], "br"),
], kind="partial")
m("steam_engine_cylinder/con_rod", "boilers", [
    el("rod", [7.25, -0.75, 1.5], [8.75, 0.75, 18.5], "pol"),
    el("big_end", [6.25, -2, -2], [9.75, 2, 2], "br"),
    el("big_end_cap", [6, -2.5, -0.5], [10, 2.5, 0.5], "ci"),
    el("small_end", [6.75, -1.5, 18.5], [9.25, 1.5, 21.5], "br"),
], kind="partial")
m("steam_engine_cylinder/piston_rod", "boilers", [
    el("rod", [7.5, 3.5, -4], [8.5, 4.5, 14], "pol"),
    el("crosshead", [5.5, 1.5, 14], [10.5, 6.5, 18], "br"),
    el("slipper_top", [5, 6.5, 13.5], [11, 8, 18.5], "pol"),
    el("slipper_bottom", [5, 0, 13.5], [11, 1.5, 18.5], "pol"),
], kind="partial")


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


def support(damaged=False):
    """Деревянный оклад 1×3×1 (якорь — средний блок): две окорённые стойки, верхняк, затяжки по кровле."""
    els = disc("post_w", "y", 2, -15.9, 28, "log", 8, c=(3, 8, 8)) + disc("post_e", "y", 2, -15.9, 28, "log", 8, c=(13, 8, 8))
    cap = disc("cap", "x", 2.2, -0.9, 16.9, "log", 8, c=(8, 29.6, 8))
    if damaged:
        # верхняк переломлен: половины просели к середине
        cap = [el("cap_w", [-1, 27.6, 6], [8, 31.6, 10], "log", rot=("z", -22.5, [-1, 29.6, 8])),
               el("cap_e", [8, 27.6, 6], [17, 31.6, 10], "log", rot=("z", 22.5, [17, 29.6, 8]))]
    els += cap
    els += [el(f"lagging_{i}", [i * 4 + 0.2, 31.8, 0], [i * 4 + 3.8, 32, 16], "plank") for i in range(4)]
    els += [el("wedge_w", [1.5, 27, 6.5], [4.5, 28, 9.5], "plank"), el("wedge_e", [11.5, 27, 6.5], [14.5, 28, 9.5], "plank"),
            el("foot_w", [0.5, -16, 5.5], [5.5, -15, 10.5], "plank"), el("foot_e", [10.5, -16, 5.5], [15.5, -15, 10.5], "plank")]
    return els


m("mine_support", "mining", support())
m("mine_support_damaged", "mining", support(True))

# механизированная крепь 1×3×2 (x 0…16, y −16…32, z 0…32): основание, перекрытие, ограждение, лемнискатный механизм
m("powered_roof_support", "mining", [
    el("base_w", [1, -16, 1], [6.5, -11, 30], "yel"),
    el("base_e", [9.5, -16, 1], [15, -11, 30], "yel"),
    el("base_bridge", [6.5, -15, 18], [9.5, -12, 28], "yel"),
    el("toe_w", [1, -16, 0], [6.5, -13, 1], "haz"),
    el("toe_e", [9.5, -16, 0], [15, -13, 1], "haz"),
    el("canopy", [0, 28, -2], [16, 31, 30], "yel"),
    el("canopy_tip", [1, 28.5, -4], [15, 30.5, -2], "yel"),
    el("canopy_hinge", [3, 26, 26], [13, 28, 30], "d"),
    el("shield", [1, 9, 23], [15, 27, 26], "yel", rot=("x", -22.5, [8, 27, 26])),
    el("lemniscate_upper", [3, -2, 22], [13, 2, 29], "d", rot=("x", 22.5, [8, 0, 26])),
    el("lemniscate_lower", [3, -11, 21], [13, -6, 29], "d"),
    el("spill_plate", [2, -16, -2], [14, -9, 0], "yel"),
    el("push_ram", [7, -14, 0], [9, -12, 18], "sm"),
    el("valve_bank", [4, -11, 12], [12, -7, 17], "dg"),
    el("lever_1", [5, -7, 13], [6, -4, 14], "red"),
    el("lever_2", [7, -7, 13], [8, -4, 14], "gas"),
    el("lever_3", [10, -7, 13], [11, -4, 14], "mb"),
    el("hose_w", [2, -11, 15], [3, 26, 16], "r"),
    el("hose_e", [13, -11, 15], [14, 26, 16], "r"),
    el("leg_socket_w", [2, -12, 5], [7, -10, 11], "d"),
    el("leg_socket_e", [9, -12, 5], [14, -10, 11], "d"),
    el("leg_cap_w", [2.5, 26, 5.5], [6.5, 28, 10.5], "d"),
    el("leg_cap_e", [9.5, 26, 5.5], [13.5, 28, 10.5], "d"),
])
m("powered_roof_support/legs", "mining", [
    el("cyl_w", [2.5, -10, 6], [6.5, 9, 10], "yel"),
    el("collar_w", [2, 9, 5.5], [7, 10.5, 10.5], "d"),
    el("rod_w", [3.5, 10.5, 7], [5.5, 26, 9], "pol"),
    el("cyl_e", [9.5, -10, 6], [13.5, 9, 10], "yel"),
    el("collar_e", [9, 9, 5.5], [14, 10.5, 10.5], "d"),
    el("rod_e", [10.5, 10.5, 7], [12.5, 26, 9], "pol"),
], kind="partial")

# очистной комбайн 3×1×1: корпус с моторными отсеками, поворотные рукояти к шнекам на оси (y 16, z 8)
m("longwall_shearer", "mining", [
    el("body", [-9, 0.5, 2], [25, 9, 14], "org"),
    el("haulage", [1, 0.5, 0.5], [15, 7, 2], "org"),
    el("control_box", [3, 9, 4], [13, 13, 12], "dg"),
    el("control_panel", [4, 10, 3.5], [12, 12.5, 4], "gauge", full=["north"]),
    el("motor_w", [-9, 9, 3], [0, 12, 13], "ribs"),
    el("motor_e", [16, 9, 3], [25, 12, 13], "ribs"),
    el("stripe", [-9, 8, 1.9], [25, 9, 2], "haz"),
    el("arm_w", [-16, 5, 4.5], [-6, 10, 11.5], "org", rot=("z", 22.5, [-6, 7, 8])),
    el("arm_e", [22, 5, 4.5], [32, 10, 11.5], "org", rot=("z", -22.5, [22, 7, 8])),
    el("skid_w", [-9, -0.01, 3], [-3, 1, 13], "d"),
    el("skid_e", [19, -0.01, 3], [25, 1, 13], "d"),
    el("cable_handler", [-9, 2, 14], [25, 6, 16], "r"),
    el("spray_bar", [-9, 9.5, 14], [25, 10.5, 15], "gas"),
    el("lamp", [7, 13, 6], [9, 14.5, 10], "lw"),
])


def shearer_drum(x0):
    """Шнек: барабан на оси (y 16, z 8), фланец, два венца резцов (второй повёрнут на 45°); ширина 6,8 px от x0."""
    els = hcyl("core", "x", 5, x0, x0 + 6.8, "vane", c=(8, 16, 8))
    els += hcyl("flange", "x", 7, x0, x0 + 1, "d", c=(8, 16, 8), sides=8)
    s = math.sqrt(0.5)
    for i, xx in enumerate((x0 + 2, x0 + 4.5)):
        dirs = ((1, 0), (-1, 0), (0, 1), (0, -1)) if i == 0 else ((s, s), (-s, s), (s, -s), (-s, -s))
        for k, (dy, dz) in enumerate(dirs):
            yy, zz = 16 + 7 * dy, 8 + 7 * dz
            els.append(el(f"pick_{i}_{k}", [xx, round(yy - 0.7, 3), round(zz - 0.7, 3)],
                          [xx + 1.3, round(yy + 0.7, 3), round(zz + 0.7, 3)], "pick"))
    return els


m("longwall_shearer/drum_a", "mining", shearer_drum(-15.9), kind="partial")
m("longwall_shearer/drum_b", "mining", shearer_drum(25.1), kind="partial")

# вакуум-насос дегазации ВВН (вал сзади): корпус на раме, сепаратор, газовые патрубки; ротор и муфта — partial
m("degasification_pump", "mining", [
    el("frame", [0.5, 0, 0.5], [15.5, 2, 15.5], "d"),
    el("foot_n", [3, 2, 2], [13, 4, 4], "dg"),
    el("foot_s", [3, 2, 11], [13, 4, 13], "dg"),
] + hcyl("casing", "z", 5.2, 2.5, 13, "mb")
  + hcyl("end_n", "z", 5.8, 1.5, 2.5, "d", sides=8)
  + hcyl("end_s", "z", 5.8, 13, 14, "d", sides=8)
  + [el("inlet", [6.5, 13, 5.5], [9.5, 16, 8.5], "gas", faces={"up": "end"}),
     el("inlet_flange", [5.5, 14, 4.5], [10.5, 15, 9.5], "gas"),
     el("outlet", [13, 5, 6.5], [16, 8, 9.5], "gas", faces={"east": "end"}),
     el("separator", [0.5, 2, 9], [3.5, 12, 13], "mb"),
     el("separator_cap", [0.25, 12, 8.75], [3.75, 13, 13.25], "d"),
     el("sight_glass", [0.3, 6, 10], [0.5, 10, 12], "gl"),
     el("gauge", [12, 11, 2], [15, 14, 2.5], "gauge", full=["north"]),
     el("guard", [5, 3, 14], [11, 4, 16], "haz")])
m("degasification_pump/rotor", "mining", hcyl("rotor", "z", 3.5, 3.5, 12.5, "sm")
  + hcyl("shaft", "z", 1, 12.5, 16, "pol", sides=8) + hcyl("coupling", "z", 2.5, 14.2, 15.6, "d", sides=8), kind="partial")

# насос водоотлива ЦНС 2×1×1: секционный корпус на стяжках, всасывающий и напорный патрубки, задвижка; вал — partial
m("mine_drainage_pump", "mining", [
    el("frame", [0, 0, 2], [32, 2, 14], "d"),
    el("frame_rail_n", [0, 0, 1.5], [32, 1, 2], "haz"),
] + [x for i in range(5) for x in hcyl(f"section_{i}", "x", 5.5 - (i % 2) * 0.5, 5 + i * 4, 9 + i * 4, "mb", sides=8)]
  + hcyl("suction_cover", "x", 6, 3.5, 5, "d", sides=8) + hcyl("discharge_cover", "x", 6, 25, 26.5, "d", sides=8)
  + [el(f"tie_rod_{i}", [3.5, y, z], [26.5, y + 0.6, z + 0.6], "pol") for i, (y, z) in enumerate(((13, 3.5), (13, 11.9), (2.4, 3.5), (2.4, 11.9)))]
  + [el("suction", [5, 13, 5], [9, 16, 9], "wat", faces={"up": "end"}),
     el("discharge", [23, 13, 7], [27, 16, 11], "wat", faces={"up": "end"}),
     el("gate_valve", [22.5, 15, 6.5], [27.5, 16, 11.5], "d"),
     el("bearing_w", [0.5, 3, 5], [3.5, 11, 11], "d"),
     el("bearing_e", [28, 3, 5], [32, 11, 11], "d"),
     el("gauge", [16, 13, 10], [19, 16, 10.5], "gauge", full=["south"]),
     el("drain", [14, 2, 12], [15, 4, 13], "br")])
m("mine_drainage_pump/shaft", "mining", hcyl("shaft", "x", 1, -0.01, 31.99, "pol", sides=8)
  + hcyl("coupling", "x", 2.5, 0.5, 2.5, "d", sides=8), kind="partial")

# подъёмная машина 3×2×3 (y −16…16): станина, коренные подшипники, колодочный тормоз, указатель глубины, пульт
m("mine_hoist", "mining", [
    el("foundation", [-16, -16, -16], [32, -12, 32], "c"),
    el("bearing_w", [-16, -12, 3], [-12, 10, 13], "mgrn"),
    el("bearing_e", [28, -12, 3], [32, 10, 13], "mgrn"),
    el("cap_w", [-16, 10, 4], [-12, 11.5, 12], "br"),
    el("cap_e", [28, 10, 4], [32, 11.5, 12], "br"),
    el("brake_post_nw", [-12, -12, -9], [-9, 14, -7], "mgrn"),
    el("brake_post_ne", [25, -12, -9], [28, 14, -7], "mgrn"),
    el("brake_post_sw", [-12, -12, 23], [-9, 14, 25], "mgrn"),
    el("brake_post_se", [25, -12, 23], [28, 14, 25], "mgrn"),
    el("brake_shoe_nw", [-12, 2, -7], [-9, 14, -5.5], "plank"),
    el("brake_shoe_ne", [25, 2, -7], [28, 14, -5.5], "plank"),
    el("brake_shoe_sw", [-12, 2, 21.5], [-9, 14, 23], "plank"),
    el("brake_shoe_se", [25, 2, 21.5], [28, 14, 23], "plank"),
    el("brake_beam_n", [-12, 14, -9], [28, 15.5, -7], "mgrn"),
    el("brake_beam_s", [-12, 14, 23], [28, 15.5, 25], "mgrn"),
    el("brake_engine", [2, -12, 26], [14, -2, 32], "dg"),
    el("brake_rod", [7, -2, 24], [9, 14, 26], "pol"),
    el("indicator_column", [-14, -12, -15], [-8, 12, -11], "mgrn"),
    el("indicator_face", [-14, 0, -15.5], [-8, 12, -15], "gauge", full=["north"]),
    el("console", [18, -12, -16], [30, -2, -11], "r35"),
    el("console_top", [18, -2, -16], [30, -1, -9], "d"),
    el("lever_speed", [21, -1, -12], [22, 6, -11], "red"),
    el("lever_brake", [26, -1, -12], [27, 6, -11], "gas"),
    el("motor", [-16, -12, 16], [-4, 0, 30], "ribs"),
])
m("mine_hoist/drum", "mining", hcyl("drum", "x", 11, -10, 26, "st") + hcyl("rope", "x", 11.3, -9, 25, "rope")
  + ring("brake_path_w", "x", 12.6, 1.6, -12, -10, "pol") + ring("brake_path_e", "x", 12.6, 1.6, 26, 28, "pol")
  + hcyl("flange_w", "x", 11.8, -12, -10, "mgrn") + hcyl("flange_e", "x", 11.8, 26, 28, "mgrn")
  + hcyl("shaft", "x", 1.5, -16, 32, "pol", sides=8),
  kind="partial")


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


# вагранка 1×3×1 (y −16…32): клёпаная шахта на ножках, воздушная коробка с дутьём от вала (запад), лётка и
# жёлоб на север, загрузочное окно, искрогаситель
def cupola(lit=False):
    els = [el(f"leg_{i}", [x, -16, z], [x + 2, -8, z + 2], "d") for i, (x, z) in enumerate(((1, 1), (13, 1), (1, 13), (13, 13)))]
    els += [el("bottom_plate", [0.5, -8, 0.5], [15.5, -6, 15.5], "d")]
    els += disc("shaft", "y", 6.5, -6, 29, "shellb", 16)
    els += ring("wind_belt", "y", 7.4, 2, -3, 2, "ci", n=16)
    els += ring("band_mid", "y", 6.8, 0.8, 11, 12.5, "d", n=16)
    els += ring("band_top", "y", 6.8, 0.8, 23, 24.5, "d", n=16)
    els += [el("blower", [-0.01, 3, 4.5], [3, 13, 11.5], "dg"),
            el("blast_pipe", [1, -1, 7], [3, 3, 9], "d"),
            el("bearing_e", [13.5, 5.5, 5.5], [16.01, 10.5, 10.5], "ci"),
            el(("glow_" if lit else "") + "taphole", [6.5, -5.5, 1], [9.5, -3, 1.6], "melt" if lit else "ci"),
            el("spout", [6, -6.5, -3], [10, -5.5, 2], "refr"),
            el("spout_lip_w", [6, -5.5, -3], [6.6, -4.5, 2], "refr"),
            el("spout_lip_e", [9.4, -5.5, -3], [10, -4.5, 2], "refr"),
            el("slag_hole", [14.4, -4, 7], [15, -2, 9], "ci"),
            el("charge_frame", [4.5, 16.5, 1], [11.5, 24.5, 2], "ci"),
            el(("glow_" if lit else "") + "charge_door", [5.5, 17.5, 0.6], [10.5, 23.5, 1.2], "melt" if lit else "d"),
            el("peep_n", [7.5, -1, 0.4], [8.5, 0, 0.8], "br")]
    els += disc("arrester", "y", 5, 29, 31.5, "d", 8) + disc("arrester_cap", "y", 6, 31.4, 31.9, "ci", 8)
    return els


m("cupola_furnace", "foundry", cupola())
m("cupola_furnace_lit", "foundry", cupola(True))


# тигельная печь (фасад — север): кирпичная шахта в бандажах, крышка с летком, горелка сзади; тигель — partial
def crucible_furnace(lit=False):
    els = disc("body", "y", 7, 0, 12, "sb", 8)
    els += ring("band_low", "y", 7.1, 0.6, 2, 3.2, "d", n=8) + ring("band_high", "y", 7.1, 0.6, 9, 10.2, "d", n=8)
    lid = [el("lid_n", [2, 12, 2], [14, 13.5, 6], "ci"), el("lid_s", [2, 12, 10], [14, 13.5, 14], "ci"),
           el("lid_w", [2, 12, 6], [6, 13.5, 10], "ci"), el("lid_e", [10, 12, 6], [14, 13.5, 10], "ci"),
           el("lid_handle", [7, 13.5, 2.5], [9, 15, 3.5], "d")]
    els += lid + [el("burner", [6.5, 2, 14], [9.5, 5, 16], "d"),
                  el("air_pipe", [7, 5, 14.5], [9, 9, 15.5], "d"),
                  el("tap_notch", [7, 11, 0.8], [9, 12, 1.2], "refr")]
    if lit:
        els.append(el("glow_flue", [6, 13.4, 6], [10, 13.45, 10], "melt"))
    return els


m("crucible_furnace", "foundry", crucible_furnace())
m("crucible_furnace_lit", "foundry", crucible_furnace(True))
m("crucible_furnace/crucible", "foundry", disc("crucible", "y", 4, 3, 12, "graph", 8)
  + [el("glow_melt", [5, 11.5, 5], [11, 12, 11], "melt"), el("lip", [7, 11.5, 3.6], [9, 12.2, 4.2], "graph")], kind="partial")


# песчаная форма в опоке: низ и верх опоки с ушками, штыри, литниковая чаша и две прибыли
def sand_mold(state="empty"):
    els = [el("drag", [1, 0, 1], [15, 6, 15], "ci", faces={"up": "fsand"}),
           el("cope", [1, 6, 1], [15, 12, 15], "ci", faces={"up": "fsand"}),
           el("lug_w", [0, 4, 6], [1, 8, 10], "ci"), el("lug_e", [15, 4, 6], [16, 8, 10], "ci"),
           el("pin_n", [2, 3, 0.4], [3, 11, 1], "pol"), el("pin_s", [13, 3, 15], [14, 11, 15.6], "pol"),
           el("handle_n", [6, 9, 0.2], [10, 10, 1], "d"), el("handle_s", [6, 9, 15], [10, 10, 15.8], "d"),
           el("weight", [2, 12, 2], [6, 14, 6], "d")]
    hot = state == "poured"
    tex = "melt" if hot else ("soot" if state == "cooling" else "fsand")
    for name, (x, z, s) in (("sprue", (9, 9, 3)), ("riser_a", (3, 10, 2)), ("riser_b", (11, 3, 2))):
        els.append(el(("glow_" if hot else "") + name, [x, 12, z], [x + s, 12.6, z + s], tex))
    els.append(el("pouring_cup", [8.5, 12, 8.5], [12.5, 13, 9], "fsand"))
    return els


m("sand_mold", "foundry", sand_mold())
m("sand_mold_poured", "foundry", sand_mold("poured"))
m("sand_mold_cooling", "foundry", sand_mold("cooling"))

# формовочная машина 2×1×1 (x 0…32): станина со встряхивающим цилиндром, прессовая траверса, бункер смеси; стол — partial
m("molding_station", "foundry", [
    el("base", [0, 0, 2], [20, 5, 14], "mach"),
    el("jolt_cylinder", [6, 5, 5], [14, 6, 11], "pol"),
    el("yoke_post_n", [19, 0, 2], [23, 22, 5], "mach"),
    el("yoke_post_s", [19, 0, 11], [23, 22, 14], "mach"),
    el("yoke_beam", [2, 19, 5], [23, 22, 11], "mach"),
    el("squeeze_head", [4, 16.5, 4], [16, 19, 12], "machs"),
    el("hopper", [22, 10, 2], [32, 16, 14], "machs", faces={"up": "fsand"}),
    el("hopper_legs", [24, 0, 4], [31, 10, 12], "mach"),
    el("chute", [16, 13, 6], [22, 14.5, 10], "d"),
    el("drive_box", [-0.01, 5, 5], [1, 11, 11], "ci"),
    el("valve_box", [0.5, 5, 0.5], [6, 9, 2], "d"),
    el("lever_jolt", [1.5, 9, 1], [2.5, 13, 1.6], "red"),
    el("lever_squeeze", [4, 9, 1], [5, 13, 1.6], "gas"),
])
m("molding_station/table", "foundry", [
    el("table", [1, 6, 2], [19, 8, 14], "tslot"),
    el("pattern_plate", [3, 8, 4], [17, 9, 12], "patw"),
    el("flask_n", [3, 9, 3], [17, 12, 4], "ci"), el("flask_s", [3, 9, 12], [17, 12, 13], "ci"),
    el("flask_w", [3, 9, 4], [4, 12, 12], "ci"), el("flask_e", [16, 9, 4], [17, 12, 12], "ci"),
    el("sand_fill", [4, 9, 4], [16, 11.5, 12], "fsand"),
], kind="partial")

# выбивная решётка 2×1×2: основание на пружинах, колосниковое полотно, вибровозбудитель с дебалансами
m("shakeout_grate", "foundry", [
    el("base", [0, 0, 0], [32, 2, 32], "d"),
    el("sand_pile", [4, 2, 4], [28, 3.5, 28], "fsand"),
] + [el(f"spring_{i}", [x, 2, z], [x + 3, 5, z + 3], "pol") for i, (x, z) in enumerate(((1, 1), (28, 1), (1, 28), (28, 28)))]
  + [el("deck_n", [0, 5, 0], [32, 8, 2], "ci"), el("deck_s", [0, 5, 30], [32, 8, 32], "ci"),
     el("deck_w", [0, 5, 2], [2, 8, 30], "ci"), el("deck_e", [30, 5, 2], [32, 8, 30], "ci")]
  + [el(f"bar_{i}", [2, 6, 3 + i * 4], [30, 7.5, 4.5 + i * 4], "ci") for i in range(7)]
  + [el("cross_w", [10, 5.5, 2], [11, 6, 30], "d"), el("cross_e", [21, 5.5, 2], [22, 6, 30], "d"),
     el("vibrator", [-0.01, 5.5, 4], [5, 10.5, 12], "ribs"),
     el("unbalance_n", [0.5, 5, 2.5], [4.5, 11, 4], "haz"), el("unbalance_s", [0.5, 5, 12], [4.5, 11, 13.5], "haz"),
     el("vibrator_foot", [-0.01, 2, 3], [5, 5.5, 13], "d")])

# токарный станок 2×1×1 (вал слева): тумбы, станина с направляющими, передняя бабка, суппорт, задняя бабка
m("machine_lathe", "machine_shop", [
    el("cabinet_w", [0, 0, 3], [9, 7, 13], "mach"),
    el("cabinet_e", [24, 0, 3], [32, 7, 13], "mach"),
    el("chip_tray", [0, 7, 1.5], [32, 7.5, 14.5], "d"),
    el("bed", [0, 7.5, 4], [32, 10, 12], "mach"),
    el("way_n", [8, 10, 5], [31, 10.6, 7], "pol"),
    el("way_s", [8, 10, 9], [31, 10.6, 11], "pol"),
    el("headstock", [0, 10, 3], [8, 19, 13], "mach"),
    el("gear_cover", [-0.01, 10.5, 3.5], [1, 18.5, 12.5], "machs"),
    el("drive_boss", [-0.02, 5.5, 5.5], [1, 10.5, 10.5], "ci"),
    el("gear_lever_a", [3, 19, 6], [4, 22, 7], "red"),
    el("gear_lever_b", [5, 19, 9], [6, 21, 10], "red"),
    el("feed_box", [1, 7.5, 2.5], [8, 10, 4], "machs"),
    el("carriage", [14, 10.6, 2], [20, 12.5, 14], "machs"),
    el("apron", [14, 6.5, 2], [20, 10.6, 3.5], "mach"),
    el("cross_slide", [15, 12.5, 4], [19, 13.5, 11], "pol"),
    el("toolpost", [15.5, 13.5, 5.5], [18.5, 16, 8.5], "d"),
    el("tool_bit", [16.5, 14, 8.5], [17.5, 14.8, 9.5], "pol"),
    el("tailstock", [25, 10.6, 5], [31, 17, 11], "mach"),
    el("tail_quill", [22, 13.5, 7.5], [25, 14.5, 8.5], "pol"),
    el("lead_screw", [0, 8.5, 1.5], [32, 9.3, 2.3], "pol"),
] + wheel("apron_wheel", "z", 2, 1.2, 2, (17, 8.5, 1.6)) + wheel("tail_wheel", "x", 2.2, 31, 32, (31.5, 14, 8)))
m("machine_lathe/spindle", "machine_shop", hcyl("chuck", "x", 3.5, 8, 10.5, "pol", c=(8, 14, 8)) + [
    el("jaw_top", [9.5, 16.5, 7.4], [11, 18, 8.6], "d"),
    el("jaw_bottom", [9.5, 10, 7.4], [11, 11.5, 8.6], "d"),
    el("jaw_side", [9.5, 13.4, 10.5], [11, 14.6, 12], "d"),
] + hcyl("work", "x", 1.6, 10.5, 15, "ci", c=(8, 14, 8), sides=8), kind="partial")

# вертикально-сверлильный станок 1×2×1 (вал сзади): плита с пазами, колонна, стол, головка с двигателем
m("drill_press", "machine_shop", [
    el("base", [1, 0, 1], [15, 2.5, 15], "mach", faces={"up": "tslot"}),
] + disc("column", "y", 2.2, 2.5, 26, "pol", 8, c=(8, 8, 13)) + [
    el("table_arm", [6, 9, 9], [10, 11.5, 15], "mach"),
    el("drive_box", [5.5, 5, 13], [10.5, 11, 16.01], "ci"),
    el("table", [3, 10, 2], [13, 11.5, 12], "tslot"),
    el("head", [4, 19, 3], [12, 28, 15], "mach"),
    el("head_front", [5, 20, 2.5], [11, 26, 3], "machs"),
    el("belt_guard", [3, 28, 3], [13, 30.5, 10], "mach"),
    el("motor", [5, 28, 10], [11, 32, 15], "ribs"),
    el("feed_hub", [12, 22, 6], [13.5, 24, 8], "d"),
    el("feed_arm_a", [13.5, 22.6, 6.6], [16, 23.4, 7.4], "pol"),
    el("feed_arm_b", [12.4, 24, 6.6], [13.2, 27, 7.4], "pol"),
    el("feed_arm_c", [12.4, 19, 6.6], [13.2, 22, 7.4], "pol"),
    el("knob_a", [15.5, 22.4, 6.4], [16.01, 23.6, 7.6], "red"),
])
m("drill_press/spindle", "machine_shop", vcyl("quill", 8, 7, 1, 14, 20, "pol", 8) + vcyl("chuck", 8, 7, 1.6, 13, 15, "d", 8)
  + vcyl("bit", 8, 7, 0.4, 11, 13, "d", 8), kind="partial")

# отсадочная машина ОМ 2×2×1: клёпаный бак с водой и постелью, воздушная камера с пульсатором, жёлоб питания, разгрузка
m("coal_jig", "coal_prep", [
    el("leg_w", [1, 0, 1], [4, 6, 15], "d"), el("leg_e", [28, 0, 1], [31, 6, 15], "d"),
    el("bearing_w", [-0.01, 5.5, 5.5], [2, 10.5, 10.5], "ci"),
    el("hopper", [2, 6, 1], [30, 12, 15], "ci"),
    el("tank_w", [0, 12, 0], [1, 25, 16], "shell"), el("tank_e", [31, 12, 0], [32, 25, 16], "shell"),
    el("tank_n", [1, 12, 0], [31, 25, 1], "shell"), el("tank_s", [1, 12, 15], [31, 25, 16], "shell"),
    el("rim", [0, 25, 0], [32, 26, 16], "d"),
    el("bed", [1, 18, 1], [31, 20, 15], "coal"),
    el("water", [1, 23, 1], [31, 24, 15], "water"),
    el("air_box", [4, 24, 3], [12, 30, 13], "mb"),
    el("valve_drive", [12, 25, 5], [14, 29, 11], "d"),
    el("feed_chute", [1, 26, 4], [10, 28, 12], "d", rot=("z", -22.5, [10, 26, 8])),
    el("spout_clean", [31, 21, 5], [32.0, 23, 11], "d"),
    el("spout_mid", [31, 14, 5], [32.0, 16, 11], "d"),
    el("spout_refuse", [14, 6, -0.01], [18, 9, 1], "d"),
    el("water_inlet", [24, 25, 6], [27, 31, 9], "wat", faces={"up": "end"}),
])
