"""Состояния M2, дорисованные после волн: котёл 07 §7.23 — `low_water` (сухое стекло, цвета побежалости над
топкой), `exploded` (рваная закопчённая обечайка). Модуль грузится последним — шум готовых PNG не сдвигается."""
from artkit import *

S = lambda n: f"block/common/state_{n}.png"


def gauge_glass_empty():  # водоуказательное стекло без воды: светлая трубка, красная черта НУВ
    a = canvas("#b9c7cf", noise=3)
    for y in range(16):
        shade(a, y, 0, 0.6)
        shade(a, y, 15, 0.6)
        shade(a, y, 5, 1.12)
    box(a, 0, 11, 15, 11, "#c0302a")
    return a


def heat_tint():  # цвета побежалости: соломенный → пурпурный → синий снизу вверх
    a = canvas("#5b5f62", noise=3)
    bands = ["#2f4f8a", "#3b5fa0", "#6a3f86", "#8a4a6a", "#a07a3a", "#b89a52"]
    for y in range(16):
        c = C(bands[min(len(bands) - 1, y * len(bands) // 16)])
        for x in range(16):
            a[y, x, :3] = 0.55 * a[y, x, :3] + 0.45 * c[:3]
    for x in range(1, 16, 3):
        rivet(a, 9, x)
    return a


def glow_crown():  # перегретое огневое днище просвечивает сквозь лист: тёмно-вишнёвый
    a = canvas("#8a2a18", noise=10)
    for y in range(16):
        for x in range(16):
            k = 0.75 + 0.35 * (1 - abs(y - 8) / 8)
            a[y, x, :3] *= k
    return a


def riveted_torn():  # обечайка после разрыва: копоть, рваные кромки, сорванные заклёпки
    a = canvas("#34363a", noise=7)
    for x in range(1, 16, 3):
        if x % 2:
            rivet(a, 5, x)
    for x, y in ((2, 9), (3, 10), (4, 9), (5, 11), (6, 12), (7, 11), (8, 12), (9, 13), (10, 12), (11, 13), (12, 14)):
        px(a, x, y, "#121214")
        px(a, x, y - 1, "#6e5a44")
    return a


def soot():  # внутренность разорванного котла: сажа и окалина
    a = canvas("#18181a", noise=6)
    for x, y in ((3, 4), (9, 2), (12, 10), (5, 12), (14, 6)):
        px(a, x, y, "#5a3a26")
    return a


def door_wrecked():  # сорванная топочная дверца: погнутая, без огня
    a = canvas("#26282a", noise=6)
    frame(a, 1.1, 0.6)
    line(a, 2, 3, 13, 12, "#121214")
    box(a, 4, 6, 7, 9, "#3a2a20")
    return a


TEXTURES.update({
    S("gauge_glass_empty"): gauge_glass_empty,
    S("heat_tint"): heat_tint,
    S("glow_crown"): glow_crown,
    S("riveted_torn"): riveted_torn,
    S("soot"): soot,
    S("boiler_door_wrecked"): door_wrecked,
})
