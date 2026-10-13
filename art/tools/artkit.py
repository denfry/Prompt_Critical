"""Общие помощники новых областей (03d, 07, 08, 11): пул текстур, mk, рисование.

Рисовалки берутся из tex_plant (он уже загружен build.py до новых модулей).
"""
from pclib import *
from tex_plant import (blank, C, px, box, kbox, line, circ, outline, lit, icon, lampc, grille, plate,
                       stripe_h, hazard_rows, dial_round32)

P = lambda n: f"block/common/plant_{n}.png"
M_ = lambda n: f"block/common/mining_{n}.png"
F_ = lambda n: f"block/common/fuelcycle_{n}.png"
B = lambda n: f"block/{n}.png"

POOL = {
    "d": T("steel_dark"), "ds": T("steel_dark_smooth"), "p": T("steel_plate"), "sm": T("steel_smooth"),
    "st": T("steel_bare"), "b": T("brass"), "bs": T("brass_smooth"), "h": T("hazard"), "c": T("concrete"),
    "r": T("rubber"), "red": T("paint_red"), "mb": T("motor_blue"), "lr": T("lamp_red"), "ly": T("lamp_yellow"),
    "lg": T("lamp_green"), "end": T("pipe_end"), "cab": T("cabinet"), "g": T("graphite"), "lead": T("lead"),
    "dial": "block/instrument_dial.png", "gauge": "block/analog_gauge_dial.png",
    "and": M_("andesite"), "cu": P("copper"), "cus": P("copper_smooth"), "fb": M_("firebrick"),
    "gl": M_("glass"), "fire": M_("glow_fire"), "coil": P("glow_coil"), "yel": M_("paint_yellow"),
    "acid": M_("pipe_acid"), "gas": M_("pipe_gas"), "h2": M_("pipe_h2"), "alk": M_("pipe_alkali"),
    "air": M_("pipe_air"), "fuel": M_("pipe_fuel"), "wat": M_("pipe_water"), "steam": M_("pipe_steam"),
    "pn": P("panel"), "pns": P("panel_smooth"), "pnd": P("panel_dark"), "gls": P("glass_dark"),
    "lw": P("lamp_white"), "lb": P("lamp_blue"), "lo": P("lamp_orange"), "loff": P("lamp_off"),
    "gal": P("galvanized"), "ins": P("insulation"), "inss": P("insulation_smooth"), "cbl": P("cable"),
    "louv": P("louver"), "rack": P("rack"), "cy": P("crane_yellow"), "cys": P("crane_yellow_smooth"),
    "spr": P("spring"), "rope": P("rope"), "redsm": P("red_smooth"), "grat": F_("grating"),
    "ref": F_("refractory"), "ss": F_("ss"), "sss": F_("ss_smooth"), "glass": F_("glass"),
}


def mk(id_, cat, els, own=None, kind="block"):
    """Модель со словарём текстур, урезанным до реально использованных ключей."""
    pool = dict(POOL, **(own or {}))
    used = set()
    for e in els:
        used.add(e["tex"])
        used.update(e["faces"].values())
    if kind == "item":
        java = f"item/{id_}"
    elif "/" in id_:
        blk, part = id_.split("/")
        java = f"block/{blk}/partial/{part}"
    else:
        java = f"block/{id_}"
    model(id_, cat, {k: pool[k] for k in sorted(used)}, els, java, kind)


def front(name, f, t, tex, base, side="north"):
    """Коробка с полной текстурой на лицевой грани."""
    return el(name, f, t, base, faces={side: tex}, full=[side])


def legs4(y1, inset=2, w=1.5, tex="d", y0=0):
    a, b = inset, 16 - inset - w
    return [el(f"leg_{i}", [x, y0, z], [x + w, y1, z + w], tex)
            for i, (x, z) in enumerate(((a, a), (b, a), (a, b), (b, b)))]


def knob(name, x, y, z=0.0, r=0.75, tex="r", depth=1.0):
    """Ручка на лицевой (north) панели с плоскостью z."""
    return el(name, [x - r, y - r, z - depth], [x + r, y + r, z], tex)


def din_rail(z0=14.5, y=7, x0=0.5, x1=15.5):
    return [el("back_plate", [1, 1, 15], [15, 15, 16], "pnd"),
            el("din_rail", [x0, y, z0], [x1, y + 2, 15], "st")]


def flat_icon(draw):
    """Плоская иконка 16×16: draw(a) рисует силуэт, затем светотень и контур."""
    return icon(draw)
