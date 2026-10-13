"""03c/03d после M3: ПЛК и модули, ПАЗ, полевые шины, телемеханика, анализаторы, серверные; иконки карт."""
from artkit2 import *

A2 = lambda n: f"block/common/auto2_{n}.png"


def plc_face(stripe):  # лицевая панель модуля ввода-вывода: окно светодиодов, клеммник, цветная полоса
    def draw(a):
        box(a, 1, 1, 14, 2, stripe)
        box(a, 3, 4, 12, 8, "#2b2f33")
        for y in (5, 7):
            for x in range(4, 12, 2):
                px(a, x, y, "#5a7a5a")
        for y in range(10, 15, 2):
            box(a, 2, y, 13, y, "#7d8288")
            for x in range(3, 13, 2):
                px(a, x, y, "#c9a14a")
    return draw


def cpu_face(a):  # ЦП ПЛК: светодиоды режима, слот карты, замок ключа
    box(a, 2, 2, 13, 3, "#3a6fb0")
    for i, c in enumerate(("#3fae5a", "#e8b923", "#c0392b", "#e07a1f")):
        box(a, 3, 5 + i * 2, 4, 5 + i * 2, c)
    box(a, 7, 5, 12, 6, "#1d1d1f")
    circ(a, 9.5, 11, 2.2, "#9aa0a4")
    box(a, 9, 10, 10, 12, "#3a3d40")


def comm_face(a):
    box(a, 2, 2, 13, 3, "#7b3fa0")
    box(a, 3, 5, 7, 8, "#3a3d40")
    box(a, 9, 5, 13, 9, "#5a5e62")
    box(a, 10, 6, 12, 8, "#1d1d1f")
    for x in (4, 6):
        px(a, x, 11, "#3fae5a")


def sis_door(a):  # дверь шкафа ПАЗ: красно-жёлтая маркировка, окно модулей
    hazard = lambda y: [px(a, x, y, "#c0392b" if (x // 2) % 2 else "#e8b923") for x in range(16)]
    hazard(1)
    hazard(14)
    box(a, 3, 4, 12, 10, "#2b2f33")
    for x in range(4, 12, 2):
        box(a, x, 5, x, 9, "#7d8288")
    box(a, 5, 11, 10, 12, "#e6e2d2")


def rtu_door(a):
    box(a, 2, 2, 13, 3, "#7d8288")
    box(a, 5, 6, 10, 8, "#e6e2d2")
    box(a, 12, 7, 13, 10, "#3a3d40")


def chroma_front(a):  # хроматограф: экран, клавиши, окно термостата
    box(a, 2, 2, 13, 6, "#1d1d1f")
    box(a, 3, 3, 12, 5, "#2d6b46")
    for x in range(3, 13, 3):
        box(a, x, 8, x + 1, 9, "#5a5e62")
    box(a, 3, 11, 12, 14, "#9aa0a4")


def server_front(a):  # дисковая полка: ряды корзин
    for y in range(1, 15, 3):
        box(a, 1, y, 14, y + 1, "#2b2b2b")
        for x in range(2, 14, 3):
            px(a, x, y, "#5a5e62")


def ups_front(a):
    box(a, 3, 2, 12, 5, "#1d1d1f")
    box(a, 4, 3, 11, 4, "#2d6b46")
    for i in range(5):
        box(a, 3 + i * 2, 7, 4 + i * 2, 8, "#5a5e62")
    grille(a, 2, 10, 13, 14, step=2, k=0.6)


def cartogram_glass():  # тёмно-серое стекло картограммы (ячейки рисует BER)
    a = canvas("#2a2e31", noise=1)
    for i in range(2, 14):
        px(a, i, 15 - i, "#3a4045")
    return a


def cpu_icon(chips, gold=False, discrete=False):
    def draw(a):
        box(a, 2, 4, 13, 12, "#2f6b3a")
        box(a, 3, 12, 12, 13, "#d4af37" if gold else "#c9a14a")
        if discrete:
            for (x, y) in ((4, 6), (7, 6), (10, 6), (4, 9), (7, 9), (10, 9)):
                box(a, x, y, x + 1, y + 1, "#1d1d1f")
                px(a, x, y + 2, "#c9a14a")
        else:
            for (x, y, w) in chips:
                box(a, x, y, x + w, y + w, "#1d1d1f")
                px(a, x, y, "#5a5e62")
    return draw


def memory_card(a):
    box(a, 4, 2, 11, 14, "#3a3d40")
    box(a, 5, 3, 10, 7, "#e6e2d2")
    box(a, 6, 4, 9, 4, "#2c5ea8")
    for x in range(5, 11, 2):
        box(a, x, 12, x, 14, "#d4af37")


def hart(a):
    box(a, 4, 1, 11, 12, "#e0b51e")
    box(a, 5, 2, 10, 5, "#1d1d1f")
    box(a, 6, 3, 9, 4, "#9fd8a8")
    for (x, y) in ((5, 7), (7, 7), (9, 7), (5, 9), (7, 9), (9, 9)):
        px(a, x, y, "#3a3d40")
    line(a, 6, 12, 3, 15, "#c0392b")
    line(a, 9, 12, 12, 15, "#1d1d1f")


def crypto_key(a):
    circ(a, 5, 5, 3, "#9aa0a4", 1.5)
    box(a, 7, 7, 13, 10, "#2b2b2b")
    box(a, 9, 8, 11, 9, "#3fae5a")
    box(a, 13, 8, 14, 9, "#d4af37")


TEXTURES.update({
    A2("io_di"): front16("#cfd1ca", plc_face("#9aa0a4")),
    A2("io_do"): front16("#cfd1ca", plc_face("#e07a1f")),
    A2("io_ai"): front16("#cfd1ca", plc_face("#2c5ea8")),
    A2("io_ao"): front16("#cfd1ca", plc_face("#7fb8e0")),
    A2("cpu"): front16("#b9c4cc", cpu_face),
    A2("comm"): front16("#cfd1ca", comm_face),
    A2("sis_door"): front16("#cfd1ca", sis_door),
    A2("rtu_door"): front16("#a9acaa", rtu_door),
    A2("chroma"): front16("#cfd1ca", chroma_front),
    A2("server"): front16("#1f2022", server_front),
    A2("ups"): front16("#a9acaa", ups_front),
    A2("plastic_blue"): paint("#9fb2c0", 2),
    A2("violet"): paint("#6a3d8f", 3),
    A2("cartogram_glass"): cartogram_glass,
    A2("diode_a"): paint("#b8322a", 2),
    A2("diode_b"): paint("#3f8f4a", 2),
    "item/plc_cpu_basic.png": icon(cpu_icon((), discrete=True)),
    "item/plc_cpu_standard.png": icon(cpu_icon(((4, 6, 2), (8, 6, 2), (6, 9, 1)))),
    "item/plc_cpu_advanced.png": icon(cpu_icon(((5, 5, 5),), gold=True)),
    "item/plc_memory_card.png": icon(memory_card),
    "item/hart_communicator.png": icon(hart),
    "item/radio_crypto_key.png": icon(crypto_key),
})

for _i in ("plc_cpu_basic", "plc_cpu_standard", "plc_cpu_advanced", "plc_memory_card",
           "hart_communicator", "radio_crypto_key"):
    item(_i, f"item/{_i}.png")
