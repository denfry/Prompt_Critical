"""Станочный парк (08 §8.10): инструмент фрезерного и шлифовального станков и детали машин. Отдельный модуль грузится
последним и не берёт шум из общего rng — готовые текстуры не сдвигаются."""
import math
from artkit2 import *


def milling_cutter(a):  # дисковая фреза: диск с зубьями по окружности, посадочное отверстие со шпоночным пазом
    circ(a, 7.5, 7.5, 6.6, "#7d8288")
    circ(a, 7.5, 7.5, 5.2, "#a4aab0", 2.2)
    for k in range(12):
        ang = 2 * math.pi * k / 12
        px(a, round(7.5 + 6.9 * math.cos(ang)), round(7.5 + 6.9 * math.sin(ang)), "#d8dde2")
    circ(a, 7.5, 7.5, 2.2, "#1d1d1f")
    px(a, 7, 5, "#1d1d1f")
    px(a, 8, 5, "#1d1d1f")


def grinding_wheel(a):  # круг 24А: белый электрокорунд на керамической связке, стальной фланец
    circ(a, 7.5, 7.5, 7, "#d8d2c4")
    for (x, y) in ((3, 5), (11, 4), (12, 10), (4, 11), (8, 2), (7, 13), (2, 8), (13, 7)):
        px(a, x, y, "#b4ad9c")
    circ(a, 7.5, 7.5, 3, "#5a5e62")
    circ(a, 7.5, 7.5, 1.4, "#1d1d1f")


def spur_gear(a):  # прямозубое колесо: венец с 12 зубьями, ступица, отверстие со шпоночным пазом
    for k in range(12):
        ang = 2 * math.pi * (k + 0.5) / 12
        cx, cy = 7.5 + 6.3 * math.cos(ang), 7.5 + 6.3 * math.sin(ang)
        circ(a, cx, cy, 1.2, "#8e9399")
    circ(a, 7.5, 7.5, 5.8, "#8e9399")
    circ(a, 7.5, 7.5, 4.2, "#6e7378", 3.0)
    circ(a, 7.5, 7.5, 2.4, "#a4aab0")
    circ(a, 7.5, 7.5, 1.2, "#1d1d1f")
    px(a, 7, 5, "#1d1d1f")


def keyed_shaft(a):  # вал по диагонали, шпоночный паз посередине, шейки светлее
    for i in range(12):
        x, y = 2 + i, 13 - i
        box(a, x, y - 1, x + 1, y, "#8e9399")
    for i in range(4, 8):
        px(a, 2 + i, 12 - i, "#2a2c2e")
    for i in (0, 1, 10, 11):
        px(a, 2 + i, 13 - i, "#c8ccd0")


def slideway(a):  # чугунная направляющая типа «ласточкин хвост»: строганые грани светлее литой
    box(a, 1, 9, 14, 13, "#5f6266")
    box(a, 3, 6, 12, 8, "#6e7176")
    line(a, 2, 8, 13, 8, "#b8bcc0")
    line(a, 3, 6, 12, 6, "#c8ccd0")
    px(a, 2, 7, "#9aa0a4")
    px(a, 13, 7, "#9aa0a4")


def planed_plate(a):  # плита со следами строгания поперёк
    box(a, 2, 4, 13, 12, "#6e7176")
    for y in range(4, 12, 2):
        line(a, 2, y, 13, y, "#868a8e")
    box(a, 2, 12, 13, 13, "#4e5256")


def ground_plate(a):  # шлифованная плита: зеркальная, с перекрёстной сеткой следов круга
    box(a, 2, 4, 13, 12, "#b4b8bc")
    for x in range(3, 13, 3):
        line(a, x, 4, x, 11, "#c8ccd0")
    line(a, 3, 5, 9, 5, "#e4e8ec")
    box(a, 2, 12, 13, 13, "#4e5256")


_ICONS = {
    "milling_cutter": milling_cutter, "grinding_wheel": grinding_wheel, "spur_gear": spur_gear,
    "keyed_shaft": keyed_shaft, "machine_slideway": slideway, "planed_plate": planed_plate, "ground_plate": ground_plate,
}
TEXTURES.update({f"item/{k}.png": icon(fn) for k, fn in _ICONS.items()})
for _i in _ICONS:
    item(_i, f"item/{_i}.png")


# --- документы завода: оперативный журнал, плакат на приводе (иконка наряда — tex_ops) ---
def shift_log(a):  # сшитая книга в коленкоре с белой этикеткой
    box(a, 3, 2, 12, 13, "#2f4a3a")
    box(a, 3, 2, 4, 13, "#1f3328")
    box(a, 6, 4, 11, 7, "#e8e0c8")
    line(a, 7, 5, 10, 5, "#3a3a3a")
    box(a, 12, 3, 12, 12, "#d8d0b8")


def permit_poster():  # «Не включать! Работают люди»: белое поле, красная кайма, чёрные строки букв
    a = canvas("#f4f2ec", 16, noise=0)
    box(a, 0, 0, 15, 15, "#c0392b")
    box(a, 1, 1, 14, 14, "#f4f2ec")
    for y in (4, 5):
        line(a, 3, y, 12, y, "#1d1d1f")
    for y in (9, 11):
        line(a, 4, y, 11, y, "#1d1d1f")
    return a


TEXTURES.update({"item/shift_log.png": icon(shift_log), "block/permit_poster.png": permit_poster})
item("shift_log", "item/shift_log.png")
