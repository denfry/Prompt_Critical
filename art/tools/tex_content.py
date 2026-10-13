"""11: справочник и документы, канарейка, электроника; иллюстрации справочника (GUI)."""
import math
import numpy as np
from PIL import Image, ImageDraw
from artkit import *

K = lambda n: f"block/common/content_{n}.png"


def folder_top():  # папка регламента на наклонной полке
    a = canvas("#7d8288", noise=3)
    box(a, 2, 2, 13, 13, "#e6e2d2")
    for y in range(4, 12, 2):
        box(a, 4, y, 11, y, "#9aa0a4")
    box(a, 2, 2, 13, 3, "#c0392b")
    return a


def cage_bars():  # латунные прутья клетки с прозрачностью
    a = blank()
    for x in range(0, 16, 3):
        box(a, x, 0, x, 15, "#c9a14a")
    box(a, 0, 0, 15, 0, "#b8913e")
    box(a, 0, 15, 15, 15, "#b8913e")
    return a


def bird():
    return canvas("#f2d43a", noise=6)


TEXTURES.update({
    K("folder"): folder_top,
    K("cage_bars"): cage_bars,
    K("bird"): bird,
    K("bird_dark"): lambda: canvas("#8c7a2a", noise=4),
})


# ======================================================================
# иконки предметов
# ======================================================================
def book(a, cover, emb):
    box(a, 3, 2, 12, 14, cover)
    box(a, 3, 2, 4, 14, "#1d1d1f")
    box(a, 12, 3, 12, 13, "#e6e2d2")
    circ(a, 8, 7, 2.6, emb, 1.6)
    px(a, 8, 7, emb)
    box(a, 6, 11, 10, 11, emb)


def handbook(a):
    book(a, "#1f3a6b", "#d4af37")


def procedure(a):
    box(a, 2, 3, 13, 14, "#8e9498")
    box(a, 3, 4, 12, 13, "#e6e2d2")
    for y in range(6, 12, 2):
        box(a, 4, y, 11, y, "#7d8288")
    box(a, 9, 1, 12, 3, "#c0392b")


def geomap(a):
    box(a, 1, 1, 14, 14, "#d8c79a")
    for r in (2, 4, 6):
        circ(a, 8, 8, r + 0.6, "#8a6a3a", r)
    px(a, 8, 8, "#c0392b")
    line(a, 1, 12, 6, 14, "#2f6fb3")


def canary(a):
    for x in range(3, 13, 2):
        box(a, x, 3, x, 14, "#c9a14a")
    box(a, 2, 2, 13, 3, "#b8913e")
    box(a, 2, 14, 13, 14, "#b8913e")
    box(a, 6, 7, 9, 10, "#f2d43a")
    px(a, 10, 7, "#f2d43a")
    px(a, 11, 8, "#e67e22")
    px(a, 9, 7, "#1d1d1f")


def relay_coil(a):
    box(a, 4, 2, 11, 3, "#3a3d40")
    box(a, 4, 12, 11, 13, "#3a3d40")
    box(a, 5, 4, 10, 11, "#c27a43")
    for y in range(4, 12, 2):
        box(a, 5, y, 10, y, "#9a5a2a")
    box(a, 7, 14, 7, 15, "#c0c4c8")


def vacuum_tube(a):
    box(a, 5, 2, 10, 11, "#cfe3ea")
    box(a, 6, 1, 9, 1, "#cfe3ea")
    box(a, 6, 4, 9, 9, "#7d8288")
    box(a, 7, 5, 8, 7, "#ffb04a")
    box(a, 4, 12, 11, 13, "#2a2c2e")
    for x in (5, 7, 9):
        px(a, x, 14, "#c0c4c8")


def transistor(a):
    circ(a, 7.5, 6.5, 4, "#b4b8bb")
    box(a, 11, 4, 12, 5, "#b4b8bb")
    for x in (5, 7, 9):
        box(a, x, 10, x, 14, "#c0c4c8")


def pcb(a):
    box(a, 1, 3, 14, 12, "#2f7a3a")
    for x, y in ((3, 5), (7, 5), (11, 5)):
        box(a, x, y, x + 1, y + 2, "#1d1d1f")
    line(a, 2, 10, 13, 10, "#d4af37")
    line(a, 4, 8, 4, 10, "#d4af37")
    line(a, 9, 8, 9, 10, "#d4af37")


def ic(a):
    box(a, 4, 3, 11, 12, "#1d1d1f")
    for y in range(4, 12, 2):
        px(a, 3, y, "#c0c4c8")
        px(a, 12, y, "#c0c4c8")
    px(a, 5, 4, "#7d8288")


def thyristor(a):
    circ(a, 7.5, 7.5, 6, "#c27a43")
    circ(a, 7.5, 7.5, 4.2, "#e8e4d8")
    circ(a, 7.5, 7.5, 2.5, "#9aa0a4")
    box(a, 12, 3, 14, 4, "#c0c4c8")


def resistor(a):
    line(a, 1, 14, 4, 11, "#c0c4c8")
    line(a, 11, 4, 14, 1, "#c0c4c8")
    for i in range(7):
        box(a, 4 + i, 10 - i, 5 + i, 11 - i, "#d8c79a")
    px(a, 6, 9, "#c0392b")
    px(a, 8, 7, "#1d1d1f")
    px(a, 10, 5, "#d35400")


def capacitor(a):
    box(a, 4, 2, 11, 11, "#2f5f9f")
    box(a, 4, 2, 5, 11, "#9ab0d0")
    box(a, 4, 2, 11, 2, "#b4b8bb")
    box(a, 6, 12, 6, 15, "#c0c4c8")
    box(a, 9, 12, 9, 14, "#c0c4c8")


ICONS = {
    "engineering_handbook": handbook, "operating_procedure": procedure, "geological_map": geomap,
    "canary": canary, "relay_coil": relay_coil, "vacuum_tube": vacuum_tube, "transistor": transistor,
    "printed_circuit_board": pcb, "integrated_circuit": ic, "power_thyristor": thyristor,
    "resistor": resistor, "capacitor": capacitor,
}
for _id, draw in ICONS.items():
    TEXTURES[f"item/{_id}.png"] = icon(draw)
    item(_id, f"item/{_id}.png")


# ======================================================================
# иллюстрации справочника: GUI PNG, плоская схема без текста (подписи — локализация GUI)
# ======================================================================
PAPER, INK, MUTE = (238, 233, 218, 255), (43, 45, 48, 255), (150, 146, 136, 255)
RED, GREEN, YEL, BLUE, ORNG = (192, 57, 43, 255), (43, 138, 62, 255), (224, 181, 30, 255), (47, 111, 179, 255), (230, 126, 34, 255)


def sheet(w, h, draw):
    def fn():
        im = Image.new("RGBA", (w, h), PAPER)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, w - 1, h - 1], outline=INK, width=2)
        draw(d, w, h)
        return np.array(im, dtype=float)
    return fn


def ladder_overview(d, w, h):  # пять ступеней эпох I0–I4, кликабельные зоны
    cols = [(90, 90, 90, 255), (140, 59, 42, 255), (110, 80, 40, 255), (90, 60, 140, 255), YEL]
    for i, c in enumerate(cols):
        x0, y1 = 14 + i * 46, h - 14 - i * 30
        d.rectangle([x0, y1 - 28, x0 + 44, h - 14], fill=c, outline=INK)
        d.rectangle([x0 + 14, y1 - 22, x0 + 30, y1 - 8], fill=PAPER, outline=INK)
        if i < 4:
            d.line([x0 + 44, y1 - 14, x0 + 54, y1 - 44], fill=INK, width=2)
    d.polygon([(22, h - 50), (30, h - 62), (38, h - 50)], fill=RED)  # «вы здесь»


def gauge_reading(d, w, h):  # шкала: норма, предупреждение, авария (ISA-101)
    for i, (cx, ang) in enumerate(((36, 200), (96, 300), (156, 335))):
        cy = h // 2 + 8
        box = [cx - 28, cy - 28, cx + 28, cy + 28]
        d.ellipse(box, fill=(250, 248, 240, 255), outline=INK, width=2)
        d.arc(box, 135, 315, fill=GREEN, width=4)
        d.arc(box, 315, 360, fill=YEL, width=4)
        d.arc(box, 0, 45, fill=RED, width=4)
        a = math.radians(ang)
        d.line([cx, cy, cx + 22 * math.cos(a), cy + 22 * math.sin(a)], fill=INK, width=2)
        d.rectangle([cx - 10, cy + 34, cx + 10, cy + 42], fill=[GREEN, YEL, RED][i])


def boiler_schematic(d, w, h):  # барабан, топка, питательный насос, предохранительный клапан
    d.rounded_rectangle([50, 18, 150, 46], 12, fill=(200, 205, 208, 255), outline=INK, width=2)
    d.rectangle([60, 70, 140, 140], fill=(140, 59, 42, 255), outline=INK, width=2)
    d.polygon([(80, 135), (100, 95), (120, 135)], fill=ORNG)
    for x in (70, 100, 130):
        d.line([x, 46, x, 70], fill=BLUE, width=3)
    d.ellipse([12, 110, 36, 134], fill=BLUE, outline=INK, width=2)
    d.line([36, 122, 46, 122, 46, 34, 50, 34], fill=GREEN, width=3)
    d.line([130, 18, 130, 6], fill=RED, width=3)
    d.rectangle([124, 2, 136, 8], fill=RED)
    d.line([150, 30, 186, 30], fill=RED, width=3)


def blast_furnace(d, w, h):  # разрез домны: колошник, шахта, распар, горн, фурмы, лётка
    cx = w // 2
    prof = [(cx - 22, 30), (cx - 40, 120), (cx - 44, 150), (cx - 34, 200), (cx - 34, 236),
            (cx + 34, 236), (cx + 34, 200), (cx + 44, 150), (cx + 40, 120), (cx + 22, 30)]
    d.polygon(prof, fill=(120, 110, 100, 255), outline=INK)
    inner = [(x + (6 if x < cx else -6), y + 4) for x, y in prof]
    d.polygon(inner, fill=(200, 180, 140, 255))
    for i, y in enumerate(range(40, 140, 14)):
        d.line([cx - 30 + i, y, cx + 30 - i, y], fill=(90, 80, 60, 255), width=3)
    d.rectangle([cx - 28, 200, cx + 28, 232], fill=ORNG)
    for s in (-1, 1):
        d.line([cx + s * 44, 196, cx + s * 60, 186], fill=BLUE, width=4)
    d.line([cx - 34, 228, cx - 56, 236], fill=RED, width=3)
    d.rectangle([cx - 24, 14, cx + 24, 30], fill=(90, 90, 90, 255), outline=INK)


def generator_sync(d, w, h):  # синхроноскоп и векторы напряжений сети и генератора
    cx, cy, r = 52, h // 2, 38
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(250, 248, 240, 255), outline=INK, width=3)
    d.line([cx, cy - r, cx, cy - r + 8], fill=RED, width=3)
    d.line([cx, cy, cx + 20, cy - 26], fill=INK, width=2)
    vx, vy = 140, cy
    d.ellipse([vx - 3, vy - 3, vx + 3, vy + 3], fill=INK)
    for ang, col in ((-90, BLUE), (-70, GREEN)):
        a = math.radians(ang)
        d.line([vx, vy, vx + 40 * math.cos(a), vy + 40 * math.sin(a)], fill=col, width=3)
    d.arc([vx - 26, vy - 26, vx + 26, vy + 26], -90, -70, fill=RED, width=2)


def ladder_logic(d, w, h):  # пример LD: две шины, ступени с контактами и катушками
    d.line([12, 10, 12, h - 10], fill=INK, width=3)
    d.line([w - 12, 10, w - 12, h - 10], fill=INK, width=3)
    for i, y in enumerate((30, 64, 98)):
        d.line([12, y, w - 12, y], fill=INK, width=2)
        for x in (40, 80):
            d.rectangle([x - 6, y - 8, x + 6, y + 8], fill=PAPER)
            d.line([x - 6, y - 8, x - 6, y + 8], fill=INK, width=2)
            d.line([x + 6, y - 8, x + 6, y + 8], fill=INK, width=2)
            if i == 1 and x == 80:
                d.line([x - 6, y + 8, x + 6, y - 8], fill=INK, width=2)
        d.ellipse([w - 52, y - 9, w - 34, y + 9], fill=PAPER, outline=GREEN if i != 2 else RED, width=2)
    d.line([30, 30, 30, 64], fill=INK, width=2)
    d.line([100, 30, 100, 64], fill=INK, width=2)


TEXTURES.update({
    "gui/handbook/ladder_overview.png": sheet(256, 192, ladder_overview),
    "gui/handbook/gauge_reading.png": sheet(192, 128, gauge_reading),
    "gui/handbook/boiler_schematic.png": sheet(192, 160, boiler_schematic),
    "gui/handbook/blast_furnace_section.png": sheet(160, 256, blast_furnace),
    "gui/handbook/generator_sync.png": sheet(192, 128, generator_sync),
    "gui/handbook/ladder_logic_example.png": sheet(192, 128, ladder_logic),
})
