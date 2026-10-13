"""08 после M3: ильменитовая россыпь, смолоперегонный куб, аффинажный стол, расточной и обрабатывающий
центр; объёмные предметы — рабочее колесо, фурма, трубы нефтяного сортамента."""
from artkit2 import *
from spec_metitems import tube

X2 = lambda n: f"block/common/met2_{n}.png"
OWN = {"ilm": B("ilmenite_sand"), "tar": X2("tar"), "porw": X2("porcelain"), "bm": X2("beige_machine"),
       "cnc": B("cnc_machining_center_screen"), "mach": X_("machine"), "machs": X_("machine_smooth"),
       "cu_r": X_("orange")}


def m(id_, cat, els, kind="block"):
    mk2(id_, cat, els, OWN, kind)


m("ilmenite_sand", "ores", [el("block", [0, 0, 0], [16, 16, 16], "ilm")])

# смолоперегонный куб 1×3×1: куб с топкой, колонна, коричневые смоляные трубы
def tar_still(running):
    return ([el("firebox", [1, -16, 1], [15, -8, 15], "brk")]
            + vcyl("still", 8, 8, 7, -8, 6, "tar", top="d") + vcyl("column", 8, 8, 4, 6, 31.9, "tar", top="d")
            + hcyl("vapour_line", "x", 1, 8, 16, "fuel", c=(8, 28, 8), sides=8)
            + [el("glow_fire_door" if running else "fire_door", [6, -14, 0.6], [10, -10, 1], "fire" if running else "d")])


m("tar_still", "coke", tar_still(False))
m("tar_still_running", "coke", tar_still(True))


# аффинажный стол 2×2×1: лабораторный стол, фарфоровые сосуды, вытяжной зонт
def pgm(running):
    els = [el("bench", [0, 9, 2], [32, 10, 15], "lab"),
           el("cabinet", [0.5, 0, 2.5], [31.5, 9, 14.5], "lab"),
           el("hood_back", [0, 10, 14], [32, 28, 16], "lab"),
           el("hood", [0, 24, 1.5], [32, 31, 16], "ss"),
           el("sash", [0, 18, 2], [32, 24, 2.5], "gls")]
    els += vcyl("dish_1", 6, 9, 2.5, 10, 13, "porw", 8) + vcyl("dish_2", 14, 9, 2, 10, 14, "porw", 8)
    els += vcyl("crucible", 24, 9, 2, 10, 13, "porw", 8)
    if running:
        els.append(el("glow_burner", [22.5, 10, 7.5], [25.5, 10.5, 10.5], "fire"))
    return els


m("pgm_refinery", "nonferrous", pgm(False))
m("pgm_refinery_running", "nonferrous", pgm(True))

# расточной станок 2А622 3×2×2: станина, стойка со шпиндельной бабкой, стол (якорь — средний блок по X)
m("boring_mill", "machine_shop", [
    el("bed", [-16, 0, 0], [32, 5, 32], "mach"),
    el("table", [6, 5, 6], [26, 9, 26], "machs"),
    el("column", [-16, 5, 4], [-6, 32, 28], "mach"),
    el("headstock", [-6, 14, 8], [2, 24, 22], "mach"),
    el("pendant", [28, 10, 28], [32, 20, 32], "pnd"),
])
m("boring_mill/spindle", "machine_shop", hcyl("spindle", "x", 2, 2, 12, "st", c=(8, 19, 15), sides=8)
  + hcyl("quill", "x", 3, 0, 2, "d", c=(8, 19, 15), sides=8), kind="partial")


# обрабатывающий центр 2×2×2: бежевый кожух, окно, экран; шпиндель — partial (ось Y)
def cnc(running):
    return [el("base", [0, 0, 0], [32, 4, 32], "dg"),
            el("enclosure", [0, 4, 4], [32, 30, 32], "bm"),
            el("roof", [0, 30, 3.5], [32, 32, 32], "bm"),
            el("door_window", [6, 8, 3.5], [22, 26, 4], "gls"),
            el("control_panel", [24, 10, 1], [31, 26, 4], "bm", faces={"north": "cnc"}, full=["north"]),
            el("glow_beacon" if running else "beacon", [26.5, 26, 1.5], [28.5, 28, 3.5], "lg" if running else "loff"),
            el("table", [4, 8, 10], [22, 10, 26], "st")]


m("cnc_machining_center", "machine_shop", cnc(False))
m("cnc_machining_center_running", "machine_shop", cnc(True))
m("cnc_machining_center/spindle", "machine_shop", vcyl("spindle_head", 13, 18, 3, 18, 28, "dg", 8)
  + vcyl("tool", 13, 18, 1, 12, 18, "st", 8), kind="partial")


# --- объёмные предметы ---
def it(id_, els):
    m(id_, "items", els, kind="item")


it("pump_impeller", hcyl("shroud_back", "z", 6, 6, 7, "b", sides=16) + hcyl("shroud_front", "z", 5, 9.5, 10.5, "b", sides=16)
   + hcyl("eye", "z", 2.5, 10.5, 11, "b", sides=8)
   + [el(f"vane_{i}", [7.5, 2.5, 7], [8.5, 13.5, 9.5], "bs", rot=("z", a, [8, 8, 8]) if a else None)
      for i, a in enumerate((0, 45, -45))] + [el("vane_3", [2.5, 7.5, 7], [13.5, 8.5, 9.5], "bs")])
it("tuyere_spare", hcyl("nose", "z", 2, 2, 6, "cus", sides=8) + hcyl("body", "z", 3, 6, 10, "cus", sides=8)
   + hcyl("flange", "z", 4, 10, 12, "cus", sides=8) + hcyl("water_in", "x", 0.75, 8, 13, "st", c=(8, 11, 9), sides=8))
it("drill_pipe", hcyl("body", "z", 1.5, 2, 14, "blu", sides=8) + hcyl("pin", "z", 2, 0, 2.5, "st", sides=8)
   + hcyl("box", "z", 2, 13.5, 16, "st", sides=8))
it("casing_pipe", tube("body", "z", 2.5, 0, 13, "st", 0.75) + tube("coupling", "z", 3, 13, 16, "d", 0.75)
   + [el("grade_band", [5.5, 10.4, 5], [10.5, 10.6, 6], "lg")])
it("tubing_pipe", hcyl("body", "z", 1, 0, 15, "st", sides=8) + hcyl("upset", "z", 1.4, 13, 16, "d", sides=8))
