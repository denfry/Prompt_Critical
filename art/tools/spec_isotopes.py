"""12: экспериментальные каналы, пневмопочта, импульсный стержень, гамма-облучатель, НЛК кремния,
детритизация, РИТЭГи, транспортные упаковки, уровнемер, КЛТ; объёмные предметы."""
from artkit2 import *

I = lambda n: f"block/common/iso_{n}.png"
OWN = {"hc": I("heavy_concrete"), "hcs": B("gamma_irradiator_cell_wall_sign"), "edge": I("door_edge"),
       "pool": I("pool_liner"), "fins": I("rtg_fins"), "gfins": I("glow_rtg_fins"), "ycase": I("yellow_case"),
       "lcase": I("lead_case"), "lblb": B("source_container_type_b_label"), "rab": B("pneumatic_rabbit_terminal_face"),
       "zr": T("zirconium"), "g": T("graphite"), "lead": T("lead")}


def m(id_, cat, els, kind="block"):
    mk2(id_, cat, els, OWN, kind)


# ======================================================================
# облучательные каналы
# ======================================================================
def irr_channel(loaded):
    els = [el("graphite_w", [0, 0, 0], [5, 16, 16], "g"), el("graphite_e", [11, 0, 0], [16, 16, 16], "g"),
           el("graphite_n", [5, 0, 0], [11, 16, 5], "g"), el("graphite_s", [5, 0, 11], [11, 16, 16], "g"),
           el("tube_w", [5, 0, 5], [6, 16.5, 11], "zr"), el("tube_e", [10, 0, 5], [11, 16.5, 11], "zr"),
           el("tube_n", [6, 0, 5], [10, 16.5, 6], "zr"), el("tube_s", [6, 0, 10], [10, 16.5, 11], "zr"),
           el("ring", [4.5, 14, 4.5], [11.5, 15, 11.5], "ye"),
           el("plug", [6, 15.5, 6], [10, 17, 10], "sss")]
    if loaded:
        els.append(el("capsule_tag", [7, 17, 7], [9, 18, 9], "lb"))
    return els


m("irradiation_channel", "core", irr_channel(False))
m("irradiation_channel_loaded", "core", irr_channel(True))


def reflector_port(loaded):
    els = [el("graphite", [0, 0, 0], [16, 16, 16], "g", faces={"up": "g"}),
           el("sleeve", [5, 0, 5], [11, 16.25, 11], "alu"),
           el("rim", [4.5, 16, 4.5], [11.5, 16.5, 11.5], "ye")]
    if loaded:
        els.append(el("plug", [6, 16.5, 6], [10, 17.5, 10], "sss"))
    return els


m("reflector_irradiation_port", "core", reflector_port(False))
m("reflector_irradiation_port_loaded", "core", reflector_port(True))


def rabbit(state="idle"):
    return [el("body", [2, 0, 4], [14, 14, 14], "sss", faces={"north": "rab"}, full=["north"]),
            el("tube_in", [6, 14, 7], [10, 16, 11], "alu"),
            el("window", [5, 9, 3.8], [11, 12, 4], "gls"),
            lamp("lamp_send", 5, 2, 4, state == "sending", "ly", 0.6),
            lamp("lamp_return", 11, 2, 4, state == "returned", "lg", 0.6)]


for _s in ("idle", "sending", "returned"):
    m("pneumatic_rabbit_terminal" + ("" if _s == "idle" else "_" + _s), "isotopes", rabbit(_s))
m("pneumatic_rabbit_terminal/hatch", "isotopes", [el("hatch", [4, 12, 3], [12, 14, 9], "sss"),
                                                  el("handle", [7, 12.5, 2.4], [9, 13.5, 3], "d")], kind="partial")
m("pneumatic_tube", "isotopes", hcyl("tube", "z", 2, 0, 16, "alu", sides=8))
m("pneumatic_tube_corner", "isotopes", hcyl("tube_z", "z", 2, 0, 10, "alu", sides=8)
  + hcyl("tube_x", "x", 2, 6, 16, "alu", sides=8) + [el("elbow", [5.5, 5.5, 5.5], [10.5, 10.5, 10.5], "alu")])
m("pneumatic_tube_tee", "isotopes", hcyl("tube_z", "z", 2, 0, 16, "alu", sides=8)
  + hcyl("tube_x", "x", 2, 8, 16, "alu", sides=8) + [el("junction", [5.5, 5.5, 5.5], [10.5, 10.5, 10.5], "alu")])


def transient(fired=False):
    els = [el("mount", [2, 0, 2], [14, 3, 14], "d")] + vcyl("cylinder", 8, 8, 4, 3, 26, "ss") + vcyl("cap", 8, 8, 4.5, 26, 29, "d", 8)
    els += [el("tag_1", [3.4, 18, 7], [3.5, 22, 9], "red"), el("tag_2", [12.5, 18, 7], [12.6, 22, 9], "red")]
    els.append(lamp("lamp_state", 8, 8, 3.9, True, "lr" if fired else "ly", 0.7))
    return els


m("transient_rod_drive", "core", transient())
m("transient_rod_drive_fired", "core", transient(True))
m("transient_rod_drive/rod", "core", vcyl("rod", 8, 8, 1.5, -16, 3, "zr", 8), kind="partial")

# ======================================================================
# гамма-облучатель
# ======================================================================
m("gamma_irradiator_cell_wall", "isotopes", [el("wall", [0, 0, 0], [16, 16, 16], "hc")])
m("gamma_irradiator_cell_wall_sign", "isotopes", [el("wall", [0, 0, 0], [16, 16, 16], "hc", faces={"north": "hcs"})])


def irr_door(state="closed"):
    lam = {"closed": ("lg", True), "open": ("ly", True), "interlocked": ("lr", True)}[state]
    return [el("frame_w", [0, 0, 10], [1.5, 32, 16], "hc"), el("frame_e", [14.5, 0, 10], [16, 32, 16], "hc"),
            el("lintel", [0, 28, 10], [16, 32, 16], "hc"),
            lamp("lamp_state", 8, 30.5, 10, lam[1], lam[0], 0.8)]


for _s in ("closed", "open", "interlocked"):
    m("gamma_irradiator_door" + ("" if _s == "closed" else "_" + _s), "isotopes", irr_door(_s))
m("gamma_irradiator_door/door", "isotopes", [el("plug", [1.5, 0, 10.5], [14.5, 28, 15.5], "hc", faces={"north": "edge"},
                                                full=["north"])], kind="partial")


def irr_pool(raised=False):
    els = [el("floor", [-16, -16, -16], [32, -14, 32], "pool"),
           el("wall_n", [-16, -14, -16], [32, 14, -14], "pool"), el("wall_s", [-16, -14, 30], [32, 14, 32], "pool"),
           el("wall_w", [-16, -14, -14], [-14, 14, 30], "pool"), el("wall_e", [30, -14, -14], [32, 14, 30], "pool"),
           el("glow_water" if not raised else "water", [-14, -14, -14], [30, 12, 30], "gch" if not raised else "water"),
           el("hoist_frame", [4, 14, -14], [12, 30, -12], "ye"), el("hoist_beam", [4, 28, -14], [12, 30, 16], "ye")]
    return els


m("gamma_irradiator_pool", "isotopes", irr_pool())
m("gamma_irradiator_pool_raised", "isotopes", irr_pool(True))
m("gamma_irradiator_pool/source_rack", "isotopes", [
    el("frame", [2, -12, 6], [14, 4, 10], "sss"),
] + [el(f"pencil_{i}", [3 + i * 2.2, -11, 7.5], [4 + i * 2.2, 3, 8.5], "sils") for i in range(5)], kind="partial")

m("ntd_silicon_rig", "isotopes", [el("base", [2, 0, 2], [14, 3, 14], "alu"),
                                  el("post", [12, 3, 7], [14, 30, 9], "alu"),
                                  el("arm", [8, 27, 7], [14, 29, 9], "alu")])
m("ntd_silicon_rig/ingot", "isotopes", vcyl("ingot", 8, 8, 3, 4, 26, "sils", 8) + vcyl("chuck", 8, 8, 1.5, 26, 27, "alu", 8),
  kind="partial")


def detrit(part="middle"):
    els = vcyl("shell", 8, 8, 4, 0, 16, "clad", 8)
    if part == "bottom":
        els = [el("base", [3, 0, 3], [13, 2, 13], "d")] + vcyl("shell", 8, 8, 4, 2, 16, "clad", 8) + \
            nozzle("bottoms", "x", 0, (8, 5, 8), 0.8, "sss")
    if part == "top":
        els = vcyl("shell", 8, 8, 4, 0, 10, "clad", 8) + vcyl("head", 8, 8, 2.5, 10, 13, "sss", 8) + \
            [el("tag_h3", [5, 4, 3.6], [11, 7, 3.9], "ws")]
    return els


m("detritiation_column", "isotopes", detrit())
m("detritiation_column_bottom", "isotopes", detrit("bottom"))
m("detritiation_column_top", "isotopes", detrit("top"))


def rtg_te(active=True):
    f = "gfins" if active else "fins"
    els = vcyl("core", 8, 8, 4, 1, 15, "dg", 8)
    for i, a in enumerate((0, 45, 90, 135)):
        if a == 90:
            els.append(el(("glow_" if active else "") + f"fin_{i}", [7.5, 1, 1], [8.5, 15, 15], f))
        elif a == 0:
            els.append(el(("glow_" if active else "") + f"fin_{i}", [1, 1, 7.5], [15, 15, 8.5], f))
        else:
            els.append(el(("glow_" if active else "") + f"fin_{i}", [1, 1, 7.5], [15, 15, 8.5], f, rot=("y", 45 if a == 45 else -45, [8, 8, 8])))
    els += [el("sign", [5.5, 9, 3.85], [10.5, 13, 4], "srad", faces={"north": "srad"}, full=["north"]),
            el("base", [3, 0, 3], [13, 1, 13], "d")]
    return els


m("rtg_thermoelectric", "isotopes", rtg_te())
m("rtg_thermoelectric_depleted", "isotopes", rtg_te(False))


def rtg_stirling(running=True):
    return [el("base", [2, 0, 1], [14, 2, 31], "d")] + hcyl("housing", "z", 5, 3, 29, "sils", c=(8, 8, 16)) + \
        vcyl("cyl_a", 8, 8, 3, 13, 15, "sils", 8) + vcyl("cyl_b", 8, 24, 3, 13, 15, "sils", 8) + \
        [el(f"fin_{i}", [2, 4, 6 + i * 4], [14, 12, 7 + i * 4], "fins") for i in range(6)] + \
        [el("sign", [12.9, 8, 13], [13.1, 12, 19], "srad", faces={"east": "srad"}, full=["east"]),
         lamp("lamp_run", 8, 12, 2.9, running, "lg", 0.6)]


m("rtg_stirling", "isotopes", rtg_stirling())
m("rtg_stirling_stalled", "isotopes", rtg_stirling(False))
m("rtg_stirling/shaft", "isotopes", shaft("z", 1, -0.01, 3, c=(8, 8, 8)) + hcyl("coupling", "z", 2, 0, 1.5, "d", sides=8),
  kind="partial")

m("source_container_type_a", "isotopes", [el("case", [2, 0, 3], [14, 9, 13], "ye", faces={"north": "ycase"}, full=["north"]),
                                          el("handle", [6, 9, 7.5], [10, 10, 8.5], "d")])
m("source_container_type_a/lid", "isotopes", [el("lid", [2, 9, 3], [14, 11, 13], "ye"),
                                              el("latch", [7, 9, 2.5], [9, 11, 3], "d")], kind="partial")
m("source_container_type_b", "isotopes", vcyl("drum", 8, 8, 6.5, 0, 15, "lcase", top="d")
  + [el("label", [3, 4, 1.4], [13, 11, 1.6], "lblb", faces={"north": "lblb"}, full=["north"]),
     el("lug_w", [0.5, 13, 7], [2, 15, 9], "d"), el("lug_e", [14, 13, 7], [15.5, 15, 9], "d")])

m("level_gauge_radiometric", "instruments", [el("bracket", [6, 0, 14], [10, 16, 16], "d"),
                                             el("source_holder", [4, 2, 8], [12, 8, 14], "lead"),
                                             el("shutter", [4, 8, 9], [12, 9, 13], "ye"),
                                             el("detector", [5, 10, 9], [11, 16, 14], "exy"),
                                             el("sign", [5, 3, 7.9], [11, 7, 8], "srad", full=["north"])])

# КЛТ-40С 3×3×3: стальной цилиндрический корпус, люки
def klt(operating):
    return [el("base", [-14, -16, -14], [30, -12, 30], "d")] + vcyl("vessel", 8, 8, 18, -12, 22, "st") + \
        vcyl("head", 8, 8, 13, 22, 28, "st") + [el("hatch_1", [0, 0, -10.6], [8, 8, -10], "d"),
                                                el("hatch_2", [10, 22, 0], [18, 29, 6], "d"),
                                                lamp("lamp_state", 8, 12, -10, True, "lg" if operating else "lo", 1.0)]


m("compact_reactor_module", "core", klt(True))
m("compact_reactor_module_shutdown", "core", klt(False))

# объёмные предметы
m("tc99m_generator", "isotopes", vcyl("body", 8, 8, 4, 0, 11, "lead") + vcyl("cap", 8, 8, 3, 11, 13, "sss", 8)
  + [el("handle", [5, 13, 7.5], [11, 15, 8.5], "d"), el("label", [5, 4, 3.9], [11, 8, 4.1], "srad", full=["north"])],
  kind="item")
m("radiography_camera", "isotopes", [el("body", [3, 2, 4], [13, 10, 12], "ye", faces={"north": "ycase"}, full=["north"]),
                                     el("handle", [5, 10, 7], [11, 13, 9], "d"),
                                     el("port", [13, 4, 6.5], [15, 7, 9.5], "st")], kind="item")
