"""03d: реле, аналоговые блоки, станции управления, газоанализатор, воздух КИП."""
import math
from artkit import *

A = lambda n: f"block/common/auto_{n}.png"


def bakelite(smooth=False):  # тёмно-коричневый бакелит, лёгкие разводы
    a = canvas("#4a2c1d", noise=2 if smooth else 6)
    if not smooth:
        for y in range(0, 16, 5):
            for x in range(16):
                shade(a, y, x, 1.08)
    frame(a, 1.12, 0.8)
    return a


def cap_clear():  # прозрачный колпак реле: голубоватое стекло с бликом
    a = blank()
    a[:, :] = C("#cfe3ea")
    a[:, :, 3] = 90
    for i in range(3, 9):
        a[i, i + 2] = C("#ffffff")
        a[i, i + 2, 3] = 170
    frame(a, 1.0, 0.85)
    a[0, :, 3] = a[15, :, 3] = a[:, 0, 3] = a[:, 15, 3] = 160
    return a


def ral7035():  # светло-серая окраска шкафов и модулей
    a = canvas("#cfd1ca", noise=3)
    frame(a, 1.08, 0.82)
    return a


def ral7035_smooth():
    return canvas("#cfd1ca", noise=1)


def panel_black():  # лицевая панель аналоговых блоков
    a = canvas("#26282a", noise=3)
    frame(a, 1.3, 0.7)
    return a


def knob_cap():  # ручка: чёрная с белой риской
    a = canvas("#151617", noise=2)
    box(a, 7, 2, 8, 7, "#e8e6dc")
    frame(a, 1.4, 0.7)
    return a


def lamels():  # медные ламели шагового искателя на гетинаксе
    a = canvas("#6b4a2a", noise=3)
    for x in range(1, 16, 3):
        box(a, x, 2, x + 1, 13, "#c7834a")
        shade(a, 2, x, 1.3)
    return a


def wiring():  # монтажная панель с жгутами проводов
    a = canvas("#b9bbb4", noise=3)
    for x, col in ((3, "#c0392b"), (5, "#2c5ea8"), (7, "#e0e0e0"), (12, "#2b2b2b")):
        box(a, x, 0, x, 15, col)
    box(a, 2, 6, 13, 7, "#3a3d40")
    for x in range(1, 15, 3):
        box(a, x, 11, x + 1, 13, "#7d8288")
    return a


def tube_glow():  # радиолампы за решёткой: оранжевый накал
    a = canvas("#2b1f17", noise=2)
    for cx in (3, 8, 13):
        for y in range(3, 14):
            for x in range(cx - 1, cx + 2):
                put(a, y, x, "#ffb04a" if 6 <= y <= 9 else "#9c6a3a")
    grille(a, 0, 0, 15, 15, step=3, k=0.55, vertical=True)
    return a


def ex_yellow():  # взрывозащищённый корпус: жёлтая эмаль, винты
    a = canvas("#e0b51e", noise=4)
    frame(a, 1.15, 0.72)
    for y, x in ((2, 2), (2, 13), (13, 2), (13, 13)):
        rivet(a, y, x)
    return a


def sensor_mesh():  # сетка сенсора (огнепреградитель)
    a = canvas("#8b9095", noise=2)
    for y in range(16):
        for x in range(16):
            if (x + y) % 2 == 0:
                shade(a, y, x, 0.6)
    return a


def compressor_blue(smooth=False):  # синяя окраска компрессора, палитра Create
    a = canvas("#3c5f8f", noise=2 if smooth else 5)
    if not smooth:
        frame(a, 1.15, 0.75)
    return a


def fins():  # рёбра цилиндра компрессора
    a = canvas("#5a6066", noise=2)
    for y in range(0, 16, 2):
        for x in range(16):
            shade(a, y, x, 1.35)
    return a


def vessel_air():  # ресивер: синий по ГОСТ 14202, сварные швы
    a = canvas("#2f6fb3", noise=4)
    for x in range(16):
        shade(a, 5, x, 0.75)
    return a


def label_front(base, slots=True, name="#e6e2d2"):
    """Фасад модуля на DIN-рейке: прорези, шильдик, клеммы."""
    def fn():
        a = canvas(base, noise=3)
        frame(a, 1.15, 0.75)
        if slots:
            for y in (2, 4, 6):
                box(a, 3, y, 12, y, "#2a2c2e")
        plate(a, 4, 9, 11, 11, name)
        for x in (2, 5, 10, 13):
            box(a, x, 14, x, 14, "#c7834a")
        return a
    return fn


def time_relay_dial32():  # белая шкала выдержки; стрелку рисует BER
    a = dial_round32(face="#f4f1e8", bezel="#4a2c1d", bg="#4a2c1d", face_r=12, bez_r=14.5)
    return a


def profile_scale32():  # профильный прибор: вертикальная шкала в рамке, деления — клиент
    n = 32
    a = blank(n)
    a[:, :] = C("#1f2224")
    box(a, 6, 3, 25, 28, "#f1eee4")
    box(a, 14, 4, 17, 27, "#d9d5c8")
    kbox(a, 6, 3, 25, 3, 0.8)
    return a


def function_front():  # БНФ: 11 пар ручек точек излома
    a = canvas("#26282a", noise=3)
    frame(a, 1.3, 0.7)
    for i in range(11):
        x = 1 + (i % 6) * 2 + (i // 6)
        for y in ((3, 5) if i < 6 else (9, 11)):
            put(a, y, x + (0 if i < 6 else 1), "#d8d6cc")
    box(a, 2, 13, 13, 14, "#e6e2d2")
    return a


def converter_front():
    a = canvas("#cfd1ca", noise=2)
    frame(a, 1.1, 0.75)
    box(a, 2, 1, 13, 2, "#2c5ea8")
    plate(a, 3, 6, 12, 9)
    for x in (3, 6, 9, 12):
        box(a, x, 13, x + 1, 14, "#c7834a")
    return a


def cam_drum_side():  # барабан командоаппарата: латунные кулачки по окружности
    a = canvas("#6e7175", noise=4)
    for x in range(1, 16, 4):
        box(a, x, 3, x + 1, 12, "#b8913e")
    return a


def calibration_gas_icon(a):
    box(a, 6, 3, 9, 14, "#d9dde0")
    box(a, 7, 1, 8, 2, "#7d8288")
    box(a, 6, 6, 9, 7, "#e8b923")
    box(a, 6, 9, 9, 12, "#f4f1e8")
    px(a, 7, 10, "#2c5ea8")
    px(a, 8, 11, "#2c5ea8")


TEXTURES.update({
    A("bakelite"): bakelite,
    A("bakelite_smooth"): lambda: bakelite(True),
    A("cap_clear"): cap_clear,
    A("ral7035"): ral7035,
    A("ral7035_smooth"): ral7035_smooth,
    A("panel_black"): panel_black,
    A("knob"): knob_cap,
    A("lamels"): lamels,
    A("wiring"): wiring,
    A("glow_tubes"): tube_glow,
    A("ex_yellow"): ex_yellow,
    A("sensor_mesh"): sensor_mesh,
    A("compressor_blue"): compressor_blue,
    A("compressor_blue_smooth"): lambda: compressor_blue(True),
    A("fins"): fins,
    A("vessel_air"): vessel_air,
    "block/time_relay_dial.png": time_relay_dial32,
    "block/manual_station_scale.png": profile_scale32,
    "block/analog_function_front.png": function_front,
    "block/signal_converter_front.png": converter_front,
    "block/relay_power_supply_front.png": label_front("#cfd1ca"),
    "block/relay_rack_door.png": label_front("#cfd1ca", slots=False),
    "block/cam_controller_drum.png": cam_drum_side,
    "item/calibration_gas.png": icon(calibration_gas_icon),
})

fluid("instrument_air", "#c9d8e6", "gas")
item("calibration_gas", "item/calibration_gas.png")
