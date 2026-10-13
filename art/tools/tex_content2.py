"""11 после M3: тренажёры, электроника (кремний, чистые комнаты, ЧРП), мир (битум, газовые выходы);
иконки; иллюстрации справочника M4–M12 и схемы инженерных уроков (GUI)."""
import math
from artkit2 import *

K2 = lambda n: f"block/common/content2_{n}.png"


def record_board(a):  # табло «дней без аварий»: деревянная рамка, стекло, окна цифр (цифры — BER)
    box(a, 2, 2, 13, 4, "#c0392b")
    for x in (3, 6, 9, 12):
        box(a, x - 1, 7, x, 11, "#1d1d1f")
    box(a, 3, 13, 12, 13, "#e6e2d2")


def sim_cabinet(a):  # вычислительный шкаф тренажёра: перфорация и ряды ламп
    grille(a, 2, 2, 13, 7, step=2, k=0.6)
    for x in range(3, 13, 3):
        box(a, x, 10, x, 10, "#e8b923")
        box(a, x, 12, x, 12, "#3fae5a")


def trainer_stripe(a):  # жёлтая полоса «ТРЕНАЖЁР»
    box(a, 0, 6, 15, 9, "#e8b923")
    for x in range(2, 14, 2):
        px(a, x, 7, "#1d1d1f")


def vfd_front(a):
    box(a, 4, 2, 11, 5, "#1d1d1f")
    box(a, 5, 3, 10, 4, "#46d18a")
    for x in (4, 7, 10):
        px(a, x, 7, "#5a5e62")
    grille(a, 2, 10, 13, 14, step=2, k=0.6)


def cleanroom():  # белая гладкая панель, тонкие швы
    a = canvas("#f0f1ee", noise=1)
    kbox(a, 0, 0, 15, 0, 0.9)
    kbox(a, 0, 0, 0, 15, 0.9)
    return a


def bitumen():  # чёрная блестящая масса с пузырями
    a = canvas("#141210", noise=4)
    for (x, y, r) in ((4, 4, 1.6), (11, 7, 2.2), (6, 12, 1.4), (13, 13, 1)):
        circ(a, x, y, r, "#2a2622", r - 0.8)
        px(a, int(x - r / 2), int(y - r / 2), "#5a5650")
    return a


def cracked_rock():  # потрескавшаяся обожжённая порода
    a = canvas("#6b5f55", noise=8)
    for (x0, y0, x1, y1) in ((1, 3, 7, 8), (7, 8, 14, 6), (7, 8, 9, 15), (3, 13, 9, 15)):
        line(a, x0, y0, x1, y1, "#2e2622")
    for _ in range(6):
        px(a, rng.randrange(16), rng.randrange(16), "#a0523a")
    return a


def assembler_top(a):  # стол установщика: зелёная плата в жёлтой рамке
    box(a, 1, 1, 14, 14, "#e8b923")
    box(a, 3, 3, 12, 12, "#2f6b3a")
    for (x, y) in ((4, 4), (8, 5), (5, 9), (10, 10)):
        box(a, x, y, x + 1, y + 1, "#1d1d1f")


# --- иконки ---
def license_icon(a):
    box(a, 2, 4, 13, 12, "#a32a24")
    box(a, 2, 4, 13, 4, "#c0392b")
    box(a, 6, 7, 9, 8, "#d4af37")
    px(a, 7, 9, "#d4af37")


def si_lump(a):
    lump(("#7d8288", "#9aa0a4", "#6b7074"), hl="#d8dde2")(a)


def si_ingot(a):
    box(a, 5, 2, 10, 14, "#8e9399")
    box(a, 6, 2, 6, 14, "#c9ced3")
    box(a, 6, 1, 9, 1, "#6e7378")
    box(a, 7, 14, 8, 15, "#6e7378")


def wafer(doped):
    def draw(a):
        circ(a, 7.5, 7.5, 6.5, "#6e7a8a")
        for i in range(3, 12):
            px(a, i, 14 - i, "#b8c8e8")
            px(a, i + 1, 14 - i, "#d8b8e0")
        box(a, 5, 14, 10, 14, "#6e7a8a")
        if doped:
            box(a, 10, 3, 12, 5, "#7b3fa0")
    return draw


def foil(col, mesh=False, cloth=False):
    def draw(a):
        circ(a, 5, 9, 4, col, 1.5)
        box(a, 5, 5, 13, 12, col)
        if mesh:
            for i in range(6, 13, 2):
                kbox(a, i, 5, i, 12, 0.7)
                kbox(a, 5, i - 1, 13, i - 1, 0.7)
        if cloth:
            for i in range(6, 13, 2):
                kbox(a, i, 5, i, 12, 0.88)
        circ(a, 5, 9, 1.4, "#3a3d40")
    return draw


# ======================================================================
# иллюстрации справочника
# ======================================================================
def cross_flows(d, w, h):  # граф межотраслевых потоков: узлы отраслей и стрелки
    nodes = {"coal": (40, 40), "steel": (128, 40), "grid": (216, 40), "oil": (40, 128),
             "chem": (128, 128), "nuke": (216, 128), "auto": (128, 216), "iso": (216, 216), "mine": (40, 216)}
    cols = {"coal": GREY, "steel": RED, "grid": YEL, "oil": (110, 80, 40, 255), "chem": (90, 60, 140, 255),
            "nuke": BLUE, "auto": GREEN, "iso": (232, 120, 35, 255), "mine": (150, 146, 136, 255)}
    for a_, b_ in (("coal", "steel"), ("steel", "grid"), ("coal", "grid"), ("oil", "chem"), ("chem", "nuke"),
                   ("grid", "nuke"), ("mine", "nuke"), ("auto", "chem"), ("auto", "grid"), ("nuke", "iso"),
                   ("oil", "grid"), ("mine", "steel")):
        (x0, y0), (x1, y1) = nodes[a_], nodes[b_]
        arrow(d, x0 + (x1 - x0) * 0.2, y0 + (y1 - y0) * 0.2, x0 + (x1 - x0) * 0.75, y0 + (y1 - y0) * 0.75)
    for k, (x, y) in nodes.items():
        d.ellipse([x - 20, y - 20, x + 20, y + 20], fill=cols[k], outline=INK, width=2)


def mud_window(d, w, h):  # давления по глубине: поровое, гидростатика, разрыв; окно между ними
    x0, y0, x1, y1 = plot_axes(d, w, h)
    d.polygon([(x0 + 40, y0), (x0 + 70, y0), (x1 - 20, y1), (x1 - 70, y1)], fill=(200, 230, 200, 255))
    curve(d, [(x0 + 30, y0), (x1 - 80, y1)], BLUE)
    curve(d, [(x0 + 80, y0), (x1 - 10, y1)], RED)
    curve(d, [(x0 + 55, y0), (x1 - 45, y1)], GREEN, 2)


def column_section(d, w, h):  # колонна: тарелки, флегма вниз, пар вверх
    d.rectangle([34, 16, 94, h - 16], fill=(220, 224, 228, 255), outline=INK, width=3)
    for i, y in enumerate(range(40, h - 30, 24)):
        x0, x1 = (34, 84) if i % 2 else (44, 94)
        d.line([x0, y, x1, y], fill=INK, width=3)
    arrow(d, 64, h - 30, 64, 30, RED, 2)
    arrow(d, 20, 40, 34, 40, BLUE)
    arrow(d, 94, 24, 116, 24, YEL)
    arrow(d, 94, h - 24, 116, h - 24, (110, 80, 40, 255))


def fission_chain(d, w, h):  # нейтрон → ядро → осколки + нейтроны; запаздывающий — пунктир
    def nuc(x, y, r=12, col=BLUE):
        d.ellipse([x - r, y - r, x + r, y + r], fill=col, outline=INK, width=2)
    nuc(40, 80)
    arrow(d, 8, 80, 26, 80, RED)
    for (x, y) in ((100, 36), (100, 124), (150, 80)):
        arrow(d, 52, 80, x - 14, y, RED)
        nuc(x, y, 9, GREEN)
    for x in range(110, 180, 8):
        d.line([x, 140, x + 4, 140], fill=(232, 120, 35, 255), width=2)


def one_over_m(d, w, h):  # 1/M падает к нулю при подходе к критичности
    x0, y0, x1, y1 = plot_axes(d, w, h)
    pts = [(x0 + i * 20, y0 + 6 + (y1 - y0 - 6) * (i / 7) ** 0.8) for i in range(8)]
    curve(d, pts, BLUE)
    for (x, y) in pts:
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=RED)
    d.line([x0, y1, x1, y1], fill=RED, width=1)


def xenon_pit(d, w, h):  # провал мощности → рост Xe-135 → выход из ямы
    x0, y0, x1, y1 = plot_axes(d, w, h)
    pts = [(x0 + t, y1 - 20 - 60 * math.exp(-((t - 50) / 30) ** 2) * (t > 10)) for t in range(0, x1 - x0, 4)]
    curve(d, pts, (90, 60, 140, 255))
    curve(d, [(x0, y0 + 10), (x0 + 12, y0 + 10), (x0 + 14, y1 - 30), (x1 - 30, y1 - 30), (x1 - 20, y0 + 20)], BLUE, 2)


def fuel_cycle(d, w, h):  # кольцо: добыча → конверсия → обогащение → ТВС → реактор → бассейн → переработка
    cx, cy, r = w // 2, h // 2, 70
    labels = 7
    pts = [(cx + r * math.cos(2 * math.pi * i / labels - math.pi / 2), cy + r * math.sin(2 * math.pi * i / labels - math.pi / 2))
           for i in range(labels)]
    cols = [YEL, (232, 120, 35, 255), GREEN, GREY, RED, BLUE, (90, 60, 140, 255)]
    for i in range(labels):
        a_, b_ = pts[i], pts[(i + 1) % labels]
        arrow(d, a_[0] + (b_[0] - a_[0]) * 0.25, a_[1] + (b_[1] - a_[1]) * 0.25,
              a_[0] + (b_[0] - a_[0]) * 0.75, a_[1] + (b_[1] - a_[1]) * 0.75)
    for (x, y), c in zip(pts, cols):
        d.rectangle([x - 16, y - 12, x + 16, y + 12], fill=c, outline=INK, width=2)


def plc_scan(d, w, h):  # цикл сканирования: ввод → программа → вывод → диагностика
    cols = [GREEN, BLUE, (232, 120, 35, 255), GREY]
    for i, c in enumerate(cols):
        x = 14 + i * 44
        d.rectangle([x, 44, x + 34, 84], fill=c, outline=INK, width=2)
        if i < 3:
            arrow(d, x + 34, 64, x + 44, 64)
    d.line([170, 84, 170, 108, 30, 108, 30, 84], fill=INK, width=2)
    arrow(d, 30, 100, 30, 86)


def cause_effect(d, w, h):  # матрица причин и следствий: строки — датчики, столбцы — действия
    n, m_ = 6, 5
    for i in range(n + 1):
        d.line([50, 30 + i * 20, 50 + m_ * 26, 30 + i * 20], fill=INK, width=1)
    for j in range(m_ + 1):
        d.line([50 + j * 26, 30, 50 + j * 26, 30 + n * 20], fill=INK, width=1)
    for (i, j) in ((0, 0), (0, 2), (1, 1), (2, 3), (3, 3), (3, 4), (4, 0), (5, 2)):
        d.text((50 + j * 26 + 10, 30 + i * 20 + 4), "X", fill=RED)
    for i in range(n):
        d.rectangle([14, 34 + i * 20, 44, 46 + i * 20], fill=YEL, outline=INK)


def oklo(d, w, h):  # разрез: пласт урановой руды в песчанике, грунтовые воды, зоны реакции
    for i, c in enumerate(((190, 170, 120, 255), (160, 140, 100, 255), (120, 110, 90, 255))):
        d.rectangle([2, 20 + i * 46, w - 3, 66 + i * 46], fill=c)
    d.polygon([(20, 80), (170, 70), (180, 96), (30, 104)], fill=(70, 70, 60, 255), outline=INK)
    for x in (60, 100, 140):
        d.ellipse([x - 9, 80, x + 9, 96], fill=(232, 120, 35, 255), outline=INK)
    for x in range(10, w, 24):
        arrow(d, x, 120, x + 14, 132, BLUE, 1)


# --- схемы инженерных уроков: валики-примитивы ---
def _tank(d, x, y, w_=40, h_=50, lvl=0.5, col=BLUE):
    d.rectangle([x, y, x + w_, y + h_], outline=INK, width=2, fill=PAPER)
    d.rectangle([x + 2, y + h_ - int(h_ * lvl), x + w_ - 2, y + h_ - 2], fill=col)


def _valve(d, x, y, col=INK):
    d.polygon([(x - 8, y - 6), (x - 8, y + 6), (x + 8, y - 6), (x + 8, y + 6)], fill=PAPER, outline=col)
    d.line([x, y, x, y - 12], fill=col, width=2)


def _lamp(d, x, y, col):
    d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=col, outline=INK, width=2)


def _cross(d, x, y, s=10):
    d.line([x - s, y - s, x + s, y + s], fill=RED, width=4)
    d.line([x - s, y + s, x + s, y - s], fill=RED, width=4)


def _flame(d, x, y):
    d.polygon([(x - 10, y), (x, y - 22), (x + 10, y)], fill=(232, 120, 35, 255), outline=RED)


def _trend(d, w, h, f, col=BLUE):
    x0, y0, x1, y1 = plot_axes(d, w, h)
    pts = [(x0 + t, y1 - f(t / (x1 - x0)) * (y1 - y0)) for t in range(0, x1 - x0, 3)]
    curve(d, pts, col)
    return x0, y0, x1, y1


def L_stuck_porv(d, w, h):
    _tank(d, 20, 30, 50, 70, 0.4)
    d.line([70, 45, 120, 45], fill=INK, width=3)
    _valve(d, 100, 45)
    _lamp(d, 150, 40, GREEN)
    _cross(d, 150, 40, 12)
    arrow(d, 120, 45, 170, 90, RED)


def L_iodine_pit(d, w, h):
    _trend(d, w, h, lambda t: 0.8 * math.exp(-((t - 0.45) / 0.2) ** 2) + 0.1, (90, 60, 140, 255))


def L_rod_withdrawal(d, w, h):
    for i, x in enumerate(range(40, 160, 24)):
        y = 30 if i == 2 else 70
        d.rectangle([x, y, x + 8, y + 50], fill=GREY if i != 2 else RED, outline=INK)
    d.rectangle([30, 90, 170, 120], outline=INK, width=2)


def L_wigner(d, w, h):
    d.rectangle([40, 30, 150, 110], fill=(70, 70, 70, 255), outline=INK, width=2)
    for x in (60, 95, 130):
        _lamp(d, x, 70, GREEN if x != 95 else (232, 120, 35, 255))
    _cross(d, 95, 70, 12)


def L_solution_crit(d, w, h):
    _tank(d, 20, 40, 30, 70, 0.6, YEL)
    d.ellipse([100, 50, 170, 110], outline=INK, width=2, fill=PAPER)
    d.ellipse([104, 70, 166, 106], fill=YEL)
    arrow(d, 50, 60, 100, 70, RED)


def L_permit(d, w, h):
    d.rectangle([20, 30, 70, 100], fill=PAPER, outline=INK, width=2)
    d.rectangle([20, 30, 70, 40], fill=RED)
    arrow(d, 75, 65, 115, 65)
    _cross(d, 95, 65, 10)
    d.ellipse([125, 45, 165, 85], outline=INK, width=3, fill=GREY)


def L_blowout(d, w, h):
    d.rectangle([80, 70, 120, 120], outline=INK, width=2, fill=RED)
    d.line([100, 120, 100, 10], fill=INK, width=4)
    for x in (85, 100, 115):
        arrow(d, 100, 60, x, 14, (40, 30, 20, 255), 3)


def L_column_overfill(d, w, h):
    _tank(d, 70, 14, 40, 100, 0.95)
    d.line([110, 60, 140, 60], fill=INK, width=2)
    _lamp(d, 150, 60, GREEN)
    arrow(d, 110, 20, 170, 20, RED)


def L_tank_overfill(d, w, h):
    _tank(d, 40, 40, 90, 70, 1.0, (110, 80, 40, 255))
    _lamp(d, 150, 40, GREY)
    _cross(d, 150, 40, 10)
    d.polygon([(30, 112), (140, 112), (150, 120), (20, 120)], fill=(110, 80, 40, 255))


def L_bypass(d, w, h):
    _tank(d, 14, 40, 40, 60)
    _tank(d, 140, 40, 40, 60)
    d.line([54, 60, 140, 60], fill=INK, width=3)
    d.line([54, 90, 80, 90, 90, 110, 110, 110, 120, 90, 140, 90], fill=RED, width=3)


def L_hydro_vibration(d, w, h):
    _trend(d, w, h, lambda t: 0.1 + 0.8 * t ** 2 * (1 + 0.15 * math.sin(60 * t)), RED)


def L_coal_dust(d, w, h):
    d.rectangle([10, 40, w - 10, 100], fill=(60, 55, 50, 255))
    for x in range(20, w - 20, 9):
        d.ellipse([x, 70 + (x * 7) % 20, x + 3, 73 + (x * 7) % 20], fill=(30, 28, 26, 255))
    _flame(d, 60, 90)
    arrow(d, 70, 70, 160, 70, (232, 120, 35, 255), 3)


def L_bleve(d, w, h):
    d.ellipse([40, 30, 150, 80], fill=PAPER, outline=INK, width=2)
    for x in (70, 95, 120):
        _flame(d, x, 110)
    d.line([95, 30, 95, 12], fill=INK, width=2)
    arrow(d, 95, 12, 120, 4, RED)


def L_an_storage(d, w, h):
    for i in range(4):
        for j in range(3):
            d.rectangle([30 + i * 26, 50 + j * 20, 52 + i * 26, 68 + j * 20], fill=(238, 238, 230, 255), outline=INK)
    _flame(d, 150, 110)


def L_out_of_phase(d, w, h):
    cx, cy = 60, h // 2
    d.ellipse([cx - 36, cy - 36, cx + 36, cy + 36], outline=INK, width=3, fill=PAPER)
    d.line([cx, cy, cx, cy + 30], fill=RED, width=3)
    arrow(d, 130, cy, 130, cy - 34, BLUE)
    arrow(d, 130, cy, 130, cy + 34, RED)


def L_compressor_surge(d, w, h):
    x0, y0, x1, y1 = plot_axes(d, w, h)
    curve(d, [(x0 + 20, y1 - 30), (x0 + 60, y0 + 20), (x1 - 20, y0 + 50)], BLUE)
    curve(d, [(x0 + 10, y1 - 10), (x0 + 50, y0 + 10)], RED, 2)


def L_black_start(d, w, h):
    for i, x in enumerate((30, 90, 150)):
        d.rectangle([x - 18, 50, x + 18, 86], fill=(GREY, YEL, BLUE)[i], outline=INK, width=2)
        if i < 2:
            arrow(d, x + 18, 68, x + 42, 68)


def L_forced_bit(d, w, h):
    d.line([20, 20, 20, h - 20], fill=INK, width=3)
    d.line([w - 20, 20, w - 20, h - 20], fill=INK, width=3)
    d.line([20, 64, w - 20, 64], fill=INK, width=2)
    d.rectangle([70, 52, 90, 76], fill=(232, 120, 35, 255), outline=INK, width=2)
    d.ellipse([w - 60, 54, w - 40, 74], outline=RED, width=3, fill=PAPER)


def L_orphan_source(d, w, h):
    d.rectangle([30, 40, 90, 100], outline=INK, width=2, fill=(200, 200, 190, 255))
    d.rectangle([110, 40, 170, 100], outline=INK, width=2, fill=PAPER)
    _cross(d, 140, 70, 14)
    d.ellipse([52, 62, 68, 78], fill=YEL, outline=INK)


LESSONS = {
    "stuck_porv": L_stuck_porv, "iodine_pit": L_iodine_pit, "rod_withdrawal_maintenance": L_rod_withdrawal,
    "wigner_anneal": L_wigner, "solution_criticality": L_solution_crit, "permit_shift_change": L_permit,
    "blowout": L_blowout, "column_overfill": L_column_overfill, "tank_overfill": L_tank_overfill,
    "temporary_bypass": L_bypass, "hydro_vibration": L_hydro_vibration, "coal_dust": L_coal_dust,
    "lpg_bleve": L_bleve, "ammonium_nitrate": L_an_storage, "out_of_phase_sync": L_out_of_phase,
    "compressor_surge": L_compressor_surge, "black_start": L_black_start, "forced_bit": L_forced_bit,
    "orphan_source": L_orphan_source,
}

TEXTURES.update({
    "block/station_record_board_face.png": front16("#e6e2d2", record_board),
    "block/training_simulator_front.png": front16("#a9acaa", sim_cabinet),
    K2("trainer_stripe"): front16("#8e9498", trainer_stripe),
    "block/variable_frequency_drive_front.png": front16("#cfd1ca", vfd_front),
    "block/cleanroom_panel.png": cleanroom,
    "block/natural_bitumen.png": bitumen,
    "block/gas_seep_vent.png": cracked_rock,
    "block/component_assembler_top.png": front16("#e8b923", assembler_top),
    K2("silicon"): paint("#8e9399", 3),
    K2("crucible_glow"): liquid("#ffb04a", 0.2),
    "item/operator_license.png": icon(license_icon),
    "item/metallurgical_silicon.png": icon(si_lump),
    "item/polysilicon.png": icon(lump(("#9aa0a6", "#b8bec4", "#7d8288"), hl="#e8eef4", n=3)),
    "item/silicon_ingot.png": icon(si_ingot),
    "item/silicon_wafer.png": icon(wafer(False)),
    "item/doped_silicon_wafer.png": icon(wafer(True)),
    "item/dopant.png": icon(ic_jar("#e6e2d2", "#7b3fa0", "#b89ad0")),
    "item/copper_foil.png": icon(foil("#c7834a")),
    "item/glass_cloth.png": icon(foil("#e8e6dc", cloth=True)),
    "item/nickel_mesh.png": icon(foil("#b4b8ae", mesh=True)),
    "gui/handbook/cross_industry_flows.png": sheet(256, 256, cross_flows),
    "gui/handbook/mud_window.png": sheet(192, 192, mud_window),
    "gui/handbook/column_section.png": sheet(128, 256, column_section),
    "gui/handbook/fission_chain.png": sheet(192, 160, fission_chain),
    "gui/handbook/one_over_m_plot.png": sheet(192, 128, one_over_m),
    "gui/handbook/xenon_pit_curve.png": sheet(192, 128, xenon_pit),
    "gui/handbook/fuel_cycle.png": sheet(256, 192, fuel_cycle),
    "gui/handbook/plc_scan.png": sheet(192, 128, plc_scan),
    "gui/handbook/cause_effect_matrix.png": sheet(192, 160, cause_effect),
    "gui/handbook/oklo_section.png": sheet(192, 160, oklo),
    **{f"gui/handbook/lessons_{k}.png": sheet(192, 128, f) for k, f in LESSONS.items()},
})

for _i in ("operator_license", "metallurgical_silicon", "polysilicon", "silicon_ingot", "silicon_wafer",
           "doped_silicon_wafer", "dopant", "copper_foil", "glass_cloth", "nickel_mesh"):
    item(_i, f"item/{_i}.png")

fluid("trichlorosilane", "#d8dadc")
fluid("hydrogen_chloride", "#c8cacc", "gas")
