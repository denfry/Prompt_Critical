"""13 §13.6.5–13.6.7 (M3): срез обслуживания и пусконаладки I0–I1; сорта воды 08 §8.12. Отдельный модуль грузится
последним, чтобы не сдвигать шум готовых текстур."""
import math
from artkit2 import *
# ======================================================================
# срез обслуживания и пусконаладки I0–I1 (13 §13.6.5–13.6.7, M3)
# ======================================================================
def oil_can(a):  # латунная маслёнка с длинным носиком
    circ(a, 6, 10, 4.2, "#c9a14a")
    circ(a, 6, 10, 2.4, "#a8832e", 1.2)
    box(a, 5, 4, 7, 6, "#a8832e")
    line(a, 8, 7, 14, 1, "#b8913a")
    px(a, 14, 1, "#3a2a10")
    line(a, 2, 8, 1, 12, "#5a3e1c")


def tallow(a):  # брикет технического жира
    box(a, 3, 5, 12, 12, "#e8dca0")
    box(a, 3, 5, 12, 6, "#f4ecc0")
    box(a, 12, 6, 13, 12, "#c8b878")
    box(a, 4, 12, 13, 13, "#b8a868")
    px(a, 6, 8, "#d4c48a")
    px(a, 9, 10, "#d4c48a")


def bearing_shell(a):  # две бронзовые полувтулки с канавкой
    for dy, col in ((0, "#b0763a"), (5, "#9a6430")):
        box(a, 2, 4 + dy, 13, 7 + dy, col)
        box(a, 2, 4 + dy, 13, 4 + dy, "#d49a5a")
        box(a, 7, 5 + dy, 8, 6 + dy, "#5a3a1a")


def scraper(a):  # штанга со скребком, крошки накипи
    line(a, 2, 13, 11, 4, "#7d8288")
    line(a, 3, 13, 12, 4, "#5a5e62")
    box(a, 10, 1, 14, 4, "#3a3d40")
    for p in ((12, 7), (14, 6), (13, 9)):
        px(a, *p, "#f2efe4")


def caulking(a):  # молоток с плоской чеканкой
    line(a, 3, 14, 9, 6, "#8a6a42")
    line(a, 4, 14, 10, 6, "#6a4e2e")
    box(a, 7, 2, 13, 5, "#5a5e62")
    box(a, 13, 3, 14, 4, "#9aa0a4")
    box(a, 6, 3, 7, 4, "#3a3d40")


def wrench(a):  # гаечный ключ со шкалой
    line(a, 3, 13, 10, 6, "#9aa0a4")
    line(a, 4, 13, 11, 6, "#7d8288")
    circ(a, 11.5, 4.5, 3, "#9aa0a4", 1.5)
    box(a, 12, 2, 14, 4, (0, 0, 0, 0))
    for t in (0.3, 0.5, 0.7):
        px(a, round(3 + 7 * t), round(13 - 7 * t), "#e8b923")


def test_gauge(a):  # контрольный манометр с красной стрелкой
    circ(a, 7.5, 7, 5.5, "#2b2b2b")
    circ(a, 7.5, 7, 4.6, "#f2efe4")
    line(a, 7, 7, 10, 4, "#1d1d1f")
    line(a, 7, 7, 4, 5, "#c0392b")
    box(a, 6, 12, 9, 15, "#c9a14a")


def recorded_chart(a):  # снятая лента самописца: круг с пером
    circ(a, 7.5, 7.5, 6.5, "#f2efe4")
    circ(a, 7.5, 7.5, 6.5, "#b8b4a4", 0.6)
    for r in (2.5, 4.5):
        circ(a, 7.5, 7.5, r, "#d8d4c6", 0.5)
    for k in range(0, 300, 15):
        rr = 4 + 1.5 * math.sin(math.radians(k * 3))
        px(a, round(7.5 + rr * math.cos(math.radians(k))), round(7.5 + rr * math.sin(math.radians(k))), "#c0392b")


def safety_valve(a):  # рычажно-грузовой предохранительный клапан
    box(a, 6, 9, 9, 14, "#b8322a")
    box(a, 5, 13, 10, 14, "#7d8288")
    box(a, 7, 6, 8, 9, "#9aa0a4")
    line(a, 2, 6, 14, 6, "#5a5e62")
    box(a, 12, 4, 14, 8, "#3a3d40")


def salt(a):  # куча поваренной соли
    for i, (x, y) in enumerate(((5, 11), (8, 10), (10, 12), (7, 12), (6, 9), (9, 8), (4, 13), (11, 13))):
        box(a, x, y, x + 1, y + 1, "#f4f4f0" if i % 2 else "#d8dcdc")
    box(a, 3, 13, 12, 14, "#e8eaea")


def maint_front(a):  # щит обслуживания: табличка, 6 гнёзд ламп
    box(a, 3, 2, 12, 4, "#c9a14a")
    for i in range(6):
        x = 3 + (i % 3) * 4
        y = 7 + (i // 3) * 4
        circ(a, x + 0.5, y + 0.5, 1.4, "#1d1d1f")


def chart_face(a):  # стеклянная дверца самописца: лента и перо
    circ(a, 7.5, 7.5, 6, "#f2efe4")
    for r in (2, 4):
        circ(a, 7.5, 7.5, r, "#d8d4c6", 0.5)
    line(a, 7, 8, 13, 3, "#c0392b")


TEXTURES.update({
    "item/oil_can.png": icon(oil_can),
    "item/tallow.png": icon(tallow),
    "item/bronze_bearing_shell.png": icon(bearing_shell),
    "item/boiler_scraper.png": icon(scraper),
    "item/caulking_hammer.png": icon(caulking),
    "item/torque_wrench.png": icon(wrench),
    "item/test_pressure_gauge.png": icon(test_gauge),
    "item/recorded_chart.png": icon(recorded_chart),
    "item/safety_valve.png": icon(safety_valve),
    "item/salt.png": icon(salt),
    "block/maintenance_panel_front.png": front16("#4a5a4e", maint_front),
    "block/recording_gauge_face.png": front16("#a8832e", chart_face),
})
for _i in ("oil_can", "tallow", "bronze_bearing_shell", "boiler_scraper", "caulking_hammer", "torque_wrench",
           "test_pressure_gauge", "recorded_chart", "safety_valve", "salt"):
    item(_i, f"item/{_i}.png")

# сорта воды водоподготовки 08 §8.12 (этап 11 M3)
fluid("clarified_water", "#6f8f86")
fluid("filtered_water", "#7aa0a8")
fluid("softened_water", "#8ab8d0")
