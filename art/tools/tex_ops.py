"""13: допуск и блокировки (LOTO), обходы, газоопасные работы, факельное хозяйство, пожарная защита,
знаки безопасности, оповещение; слои брони СИЗ; иконки инструмента и ремкомплектов."""
import math
from artkit2 import *
from tex_plant import legs_l2

Q = lambda n: f"block/common/ops_{n}.png"


# ======================================================================
# блоки
# ======================================================================
def lockout_front(a):  # окно и проушины замков
    box(a, 3, 2, 12, 7, "#9fc3d6")
    for x in range(3, 13, 2):
        box(a, x, 10, x, 12, "#9aa0a4")


def cork():
    a = canvas("#b0885a", noise=10)
    frame(a, 1.0, 0.7)
    return a


def red_white():  # высотная маркировка: красно-белые полосы
    a = canvas("#c0392b", noise=3)
    for y in range(8, 16):
        box(a, 0, y, 15, y, "#e8e6dc")
    return a


def alarm_panel(a):  # ППК: табло зон
    box(a, 2, 2, 13, 4, "#1d1d1f")
    for i, y in enumerate(range(6, 14, 2)):
        box(a, 3, y, 4, y, "#5a5e62")
        box(a, 6, y, 12, y, "#e6e2d2")


def call_point(a):  # «нажать»: белое поле с кругом
    box(a, 3, 3, 12, 12, "#f2f2ee")
    circ(a, 7.5, 7.5, 3, "#c0392b", 2)


def permit_pockets():
    a = cork()
    for y in (3, 9):
        for x in (1, 6, 11):
            box(a, x, y, x + 3, y + 4, "#e6e2d2")
            box(a, x, y, x + 3, y, "#9aa0a4")
    return a


def sign_atlas32(kind):
    """Знак безопасности 32×32 (ГОСТ 12.4.026 / СГС): треугольник, запрещающий круг, предписывающий, ромб СГС."""
    def draw(a):
        a[:, :] = C("#f2f2ee")
        if kind == "warn":
            for y in range(4, 28):
                w = (y - 4) * 0.55
                box(a, int(15.5 - w), y, int(16 + w), y, "#e8b923")
            line(a, 2, 28, 15, 3, "#1d1d1f")
            line(a, 29, 28, 16, 3, "#1d1d1f")
            box(a, 2, 28, 29, 28, "#1d1d1f")
            box(a, 15, 10, 16, 20, "#1d1d1f")
            box(a, 15, 23, 16, 24, "#1d1d1f")
        elif kind == "forbid":
            for y in range(32):
                for x in range(32):
                    d = math.hypot(x - 15.5, y - 15.5)
                    if 10.5 < d < 14:
                        a[y, x] = C("#c0392b")
            line(a, 7, 7, 24, 24, "#c0392b")
            line(a, 8, 7, 25, 24, "#c0392b")
        elif kind == "mandatory":
            circ(a, 15.5, 15.5, 13.5, "#2c5ea8")
            circ(a, 15.5, 11, 3.5, "#f2f2ee")
            box(a, 12, 15, 19, 24, "#f2f2ee")
        else:  # ромб СГС: красная рамка, пиктограмма
            for y in range(32):
                for x in range(32):
                    d = abs(x - 15.5) + abs(y - 15.5)
                    if 12 < d < 15:
                        a[y, x] = C("#c0392b")
            circ(a, 15.5, 15.5, 4, "#1d1d1f")
    return draw


def diamond_nfpa():
    a = blank()
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 7.5:
                col = "#c0392b" if y < x and y < 15 - x else "#e8b923" if x > y and x > 15 - y else \
                    "#2c5ea8" if x < y and x < 15 - y else "#f2f2ee"
                a[y, x] = C(col)
    return a


def un_placard():
    a = canvas("#e07a1f", noise=2)
    box(a, 0, 0, 15, 0, "#1d1d1f")
    box(a, 0, 15, 15, 15, "#1d1d1f")
    box(a, 0, 0, 0, 15, "#1d1d1f")
    box(a, 15, 0, 15, 15, "#1d1d1f")
    box(a, 1, 7, 14, 8, "#1d1d1f")
    return a


def intercom(a):
    grille(a, 3, 2, 12, 7, step=2, k=0.55)
    for x in (4, 7, 10):
        box(a, x, 10, x + 1, 11, "#3a3d40")


# ======================================================================
# слои брони СИЗ (64×32)
# ======================================================================
def hard_hat_l1():
    a = blank(64, 32)
    fill_box(a, HAT, "#f2f2ee", rows=(0, 4))
    x0, y0, x1, y1 = HAT["front"]
    box(a, x0, y0 + 3, x1 - 1, y0 + 3, "#c9c9c2")  # козырёк
    for k in ("right", "left", "back"):
        x0, y0, x1, y1 = HEAD[k]
        box(a, x0, y0 + 4, x1 - 1, y0 + 4, "#3a3d40")  # подбородочный ремень
    return a


def gas_mask_l1():
    a = blank(64, 32)
    fill_box(a, HEAD, "#5a5e5a", rows=(1, 8), skip=("top",))
    x0, y0, x1, y1 = HEAD["front"]
    box(a, x0 + 1, y0 + 2, x0 + 2, y0 + 3, "#9fc3d6")
    box(a, x1 - 3, y0 + 2, x1 - 2, y0 + 3, "#9fc3d6")
    box(a, x0 + 3, y0 + 5, x1 - 4, y1 - 1, "#3a3d40")  # клапанная коробка
    x0, y0, x1, y1 = HEAD["right"]
    box(a, x0 + 1, y0 + 4, x1 - 2, y1 - 1, "#6a6a3a")  # фильтрующая коробка сбоку
    return a


def scba_l1():
    a = gas_mask_l1()
    x0, y0, x1, y1 = HEAD["front"]
    box(a, x0 + 1, y0 + 1, x1 - 2, y0 + 4, "#9fc3d6")  # панорамное стекло
    box(a, x0 + 3, y0 + 5, x1 - 4, y1 - 1, "#c0392b")  # лёгочный автомат
    bx0, by0, bx1, by1 = BODY["back"]
    box(a, bx0 + 1, by0, bx1 - 2, by1 - 1, "#e0b51e")  # баллон
    box(a, bx0 + 3, by0, bx0 + 4, by0 + 1, "#7d8288")
    for k in ("front",):
        x0, y0, x1, y1 = BODY[k]
        box(a, x0 + 1, y0, x0 + 1, y1 - 1, "#2b2b2b")
        box(a, x1 - 2, y0, x1 - 2, y1 - 1, "#2b2b2b")
    return a


def fire_suit_l1():
    a = blank(64, 32)
    silver = "#cfd2d4"
    fill_box(a, HAT, silver)
    x0, y0, x1, y1 = HAT["front"]
    box(a, x0 + 1, y0 + 2, x1 - 2, y0 + 5, "#d4a84a")  # золочёный визор
    fill_box(a, BODY, silver)
    fill_box(a, ARM, silver)
    for k in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = ARM[k]
        box(a, x0, y1 - 2, x1 - 1, y1 - 1, "#8e9296")
    fill_box(a, LEG, silver, rows=(6, 12), skip=("top",))
    return a


def harness_l1():
    a = blank(64, 32)
    org = "#e07a1f"
    for k in ("front", "back"):
        x0, y0, x1, y1 = BODY[k]
        box(a, x0 + 1, y0, x0 + 1, y1 - 1, org)
        box(a, x1 - 2, y0, x1 - 2, y1 - 1, org)
        box(a, x0, y0 + 8, x1 - 1, y0 + 8, org)
    x0, y0, x1, y1 = BODY["back"]
    box(a, x0 + 3, y0 + 2, x0 + 4, y0 + 3, "#9aa0a4")  # D-кольцо
    return a


def harness_l2():
    a = blank(64, 32)
    for k in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = LEG[k]
        box(a, x0, y0 + 1, x1 - 1, y0 + 1, "#e07a1f")
    return a


def ear_l1():
    a = blank(64, 32)
    for k in ("right", "left"):
        x0, y0, x1, y1 = HEAD[k]
        box(a, x0 + 2, y0 + 2, x1 - 3, y0 + 6, "#e8b923")
    x0, y0, x1, y1 = HEAD["top"]
    box(a, x0 + 3, y0, x0 + 4, y1 - 1, "#2b2b2b")  # оголовье
    return a


# ======================================================================
# иконки
# ======================================================================
def padlock(a):
    circ(a, 7.5, 6, 3.5, "#9aa0a4", 2.2)
    box(a, 3, 7, 12, 14, "#c0392b")
    box(a, 7, 9, 8, 11, "#1d1d1f")


def tag(a):
    box(a, 4, 2, 11, 14, "#f2f2ee")
    box(a, 4, 2, 11, 5, "#c0392b")
    circ(a, 7.5, 3.5, 1, "#3a3d40")
    for y in (8, 10, 12):
        box(a, 5, y, 10, y, "#c0392b")


def hasp(a):
    box(a, 2, 4, 13, 9, "#9aa0a4")
    for x in range(3, 13, 2):
        px(a, x, 6, "#1d1d1f")
    box(a, 6, 9, 9, 14, "#7d8288")
    circ(a, 7.5, 13, 1.5, "#7d8288", 0.7)


def tester(a):
    box(a, 3, 2, 6, 9, "#e8b923")
    box(a, 9, 6, 12, 13, "#e8b923")
    line(a, 6, 6, 9, 9, "#1d1d1f")
    box(a, 4, 9, 5, 13, "#c0392b")
    box(a, 10, 1, 11, 5, "#1d1d1f")


def patrol_reader(a):
    box(a, 4, 2, 11, 13, "#26282a")
    box(a, 5, 3, 10, 6, "#3a4a52")
    circ(a, 7.5, 10, 1.6, "#3fae5a")


def ut_gauge(a):
    ic_device("#e8b923")(a)
    line(a, 11, 13, 13, 15, "#1d1d1f")
    box(a, 13, 14, 14, 15, "#9aa0a4")


def seal(a):
    circ(a, 7.5, 7.5, 6, "#9aa0a4", 3.5)
    circ(a, 7.5, 7.5, 3.4, "#2b2b2b", 2.2)


def gaskets(a):
    for i in range(3):
        circ(a, 7.5, 6 + i * 2.5, 5, ("#7d8288", "#9aa0a4", "#c9a14a")[i], 3.2)


def packing(a):
    circ(a, 7.5, 7.5, 6, "#e6dcc0", 2.5)
    for k in range(0, 360, 45):
        r = math.radians(k)
        px(a, round(7.5 + 4 * math.cos(r)), round(7.5 + 4 * math.sin(r)), "#b8a888")


def trim_kit(a):
    box(a, 6, 2, 9, 9, "#9aa0a4")
    circ(a, 7.5, 11, 3, "#c9ced3", 1.5)
    box(a, 7, 1, 8, 1, "#5a5e62")


def cartridge(a):
    box(a, 5, 2, 10, 14, "#f2efe4")
    for y in range(4, 13, 2):
        box(a, 5, y, 10, y, "#d8d4c6")
    box(a, 5, 2, 10, 2, "#2c5ea8")
    box(a, 5, 14, 10, 14, "#2c5ea8")


def lifeline(a):
    circ(a, 7, 9, 5, "#e07a1f", 3.5)
    box(a, 11, 2, 13, 6, "#9aa0a4")
    px(a, 12, 3, "#2b2b2b")


def filter_box(band):
    def draw(a):
        box(a, 4, 3, 11, 13, "#4a4e4a")
        box(a, 4, 7, 11, 8, band)
        box(a, 6, 1, 9, 2, "#7d8288")
    return draw


def scba_cyl(a):
    box(a, 5, 3, 10, 14, "#e0b51e")
    box(a, 5, 3, 10, 3, "#2b2b2b")
    box(a, 7, 1, 8, 2, "#7d8288")
    for y in range(5, 13, 3):
        box(a, 5, y, 10, y, "#c8a01a")


def ppe_icon(kind):
    def draw(a):
        if kind == "hard_hat":
            circ(a, 7.5, 9, 5.5, "#f2f2ee")
            box(a, 1, 9, 14, 11, "#c9c9c2")
            box(a, 0, 11, 15, 15, (0, 0, 0, 0))
        elif kind == "gas_mask":
            circ(a, 7.5, 7, 5, "#5a5e5a")
            circ(a, 5, 6, 1.4, "#9fc3d6")
            circ(a, 10, 6, 1.4, "#9fc3d6")
            box(a, 6, 10, 9, 14, "#6a6a3a")
        elif kind == "scba":
            box(a, 6, 2, 10, 14, "#e0b51e")
            box(a, 3, 4, 5, 12, "#2b2b2b")
            box(a, 11, 4, 13, 12, "#2b2b2b")
        elif kind == "fire_suit":
            box(a, 3, 3, 12, 14, "#cfd2d4")
            box(a, 5, 4, 10, 7, "#d4a84a")
        elif kind == "safety_harness":
            line(a, 4, 2, 7, 13, "#e07a1f")
            line(a, 11, 2, 8, 13, "#e07a1f")
            box(a, 4, 8, 11, 9, "#e07a1f")
            circ(a, 7.5, 12, 1.5, "#9aa0a4", 0.6)
        else:
            circ(a, 7.5, 7, 6, "#2b2b2b", 5)
            box(a, 1, 7, 4, 12, "#e8b923")
            box(a, 11, 7, 14, 12, "#e8b923")
    return draw


TEXTURES.update({
    "block/lockout_box_front.png": front16("#c0392b", lockout_front),
    Q("cork"): cork,
    "block/permit_board_face.png": permit_pockets,
    Q("red_white"): red_white,
    "block/fire_alarm_panel_front.png": front16("#b8322a", alarm_panel),
    "block/manual_call_point_face.png": front16("#c0392b", call_point),
    **{f"block/hazard_sign_{k}.png": front32("#f2f2ee", sign_atlas32(k)) for k in ("warn", "forbid", "mandatory", "ghs")},
    "block/hazard_diamond.png": diamond_nfpa,
    "block/un_placard.png": un_placard,
    "block/intercom_station_front.png": front16("#9aa0a4", intercom),
    "models/armor/hard_hat_layer_1.png": hard_hat_l1,
    "models/armor/gas_mask_layer_1.png": gas_mask_l1,
    "models/armor/scba_layer_1.png": scba_l1,
    "models/armor/fire_suit_layer_1.png": fire_suit_l1,
    "models/armor/fire_suit_layer_2.png": lambda: legs_l2("#cfd2d4", "#8e9296"),
    "models/armor/safety_harness_layer_1.png": harness_l1,
    "models/armor/safety_harness_layer_2.png": harness_l2,
    "models/armor/hearing_protection_layer_1.png": ear_l1,
    "item/lockout_padlock.png": icon(padlock),
    "item/lockout_tag.png": icon(tag),
    "item/work_permit.png": icon(ic_doc("#c0392b", "#2c5ea8")),
    "item/lockout_hasp.png": icon(hasp),
    "item/voltage_tester.png": icon(tester),
    "item/portable_gas_detector.png": icon(ic_device("#e8b923")),
    "item/patrol_reader.png": icon(patrol_reader),
    "item/ultrasonic_thickness_gauge.png": icon(ut_gauge),
    "item/mechanical_seal.png": icon(seal),
    "item/gasket_set.png": icon(gaskets),
    "item/gland_packing.png": icon(packing),
    "item/valve_trim_kit.png": icon(trim_kit),
    "item/filter_cartridge.png": icon(cartridge),
    "item/lifeline.png": icon(lifeline),
    "item/gas_mask_filter.png": icon(filter_box("#7a4a2a")),
    "item/scba_cylinder.png": icon(scba_cyl),
    **{f"item/{k}.png": icon(ppe_icon(k)) for k in ("hard_hat", "gas_mask", "scba", "fire_suit", "safety_harness",
                                                     "hearing_protection")},
})

# огнетушитель — объёмный предмет (spec_ops)
for _i in ("lockout_padlock", "lockout_tag", "work_permit", "lockout_hasp", "voltage_tester", "portable_gas_detector",
           "patrol_reader", "ultrasonic_thickness_gauge", "mechanical_seal", "gasket_set", "gland_packing", "valve_trim_kit",
           "filter_cartridge", "lifeline", "gas_mask_filter", "scba_cylinder", "hard_hat", "gas_mask", "scba", "fire_suit",
           "safety_harness", "hearing_protection"):
    item(_i, f"item/{_i}.png")

fluid("foam_concentrate", "#efe2a0")
