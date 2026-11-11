"""HQ-арт: свой атлас на модель и живопись по палитрам вместо заливок шумом.

Чем отличается от плейсхолдеров (pclib/artkit):
  * у каждой модели свой атлас `textures/block/hq/<id>.png`, грани разложены 1 тексель = 1 единица
    модели, без растянутых пикселей;
  * рисуем не цветом, а «материал + уровень» (Sheet): уровень 0…6 идёт по рампе из семи опорных
    цветов со сдвигом тона, итог квантуется до полушага — мазки остаются пиксельными;
  * светотень берётся из геометрии: блик/тень только на открытых выпуклых рёбрах и запечённое
    затенение (AO) в стыках деталей;
  * грани, утопленные в соседних деталях, выбрасываются сами.

Модель: m = Model(id, категория); m.box(...), m.cyl(...); m.done(). Грань рисуется функцией
paint={"north": lambda s: ...} по листу размером с грань: (0, 0) — левый верхний угол, если
смотреть на грань снаружи. build.py подменяет плейсхолдер с тем же id.
"""
import math
import zlib

import numpy as np

AX = {"x": 0, "y": 1, "z": 2}
# грань: ось нормали, знак, ось u, знак u, ось v, знак v — раскладка Minecraft без поворота UV
FACES = {
    "north": (2, -1, 0, -1, 1, -1),
    "south": (2, 1, 0, 1, 1, -1),
    "west": (0, -1, 2, 1, 1, -1),
    "east": (0, 1, 2, -1, 1, -1),
    "up": (1, 1, 0, 1, 2, 1),
    "down": (1, -1, 0, 1, 2, -1),
}

# ---------------------------------------------------------------- палитры
# семь опорных цветов от тени к блику; тени уходят в холодный тон, блики — в тёплый
_RAMPS = {
    "shell": "1a1c23 252931 333842 444a55 57606b 6e7781 8a929a",     # клёпаный кожух
    "cast": "161518 222125 2f2e32 3f3e41 515053 676669 818083",      # чугунное литьё
    "steel": "3a3e47 535962 6e7580 8a929c a6aeb6 c1c8ce dce1e5",     # обработанная сталь
    "mblue": "1a2835 243748 2f4a5f 3e6078 51788f 6b93a7 8eb0bf",     # машинная серо-голубая
    "mgreen": "1b2921 26382c 334b39 436149 56795c 6e9373 8dad8e",    # станочная зелёная
    "oxide": "2b110e 421914 5d241b 793223 95442e af5b3f c67959",     # сурик
    "rust": "291307 3f1f0b 592e11 764119 935925 ac7335 c3904d",
    "brass": "38280b 553e11 765819 987625 b99635 d4b54f ebd479",
    "copper": "391910 55271a 753923 965131 b46d45 ce8d61 e3af88",
    "brick": "39150f 532119 6f2f23 8b402f a4543e ba6b51 ce876b",     # шамот
    "dinas": "4a3a1a 665126 83693a a2844f bd9f68 d3b986 e5d0a6",     # динас
    "mortar": "49453e 5e5950 767064 8f897b a7a193 beb8aa d2cdc0",
    "concrete": "3d3e3f 4f5051 636465 797a7a 909190 a7a8a6 bebfbc",
    "yellow": "493305 6a4b07 8e680b b4870f d5a617 edc332 fadf68",    # сигнальная жёлтая
    "red": "390b0b 560f0f 761515 991d1b ba2b25 d44639 e86d5b",
    "black": "0b0b0d 131316 1c1c20 27272c 34343a 44444b 57575e",     # резина, сажа
    "white": "5d6165 7b7f83 999da0 b5b8ba ced0d1 e2e3e3 f3f3f1",     # эмаль, шкалы
    "zinc": "495157 616a71 7a848b 949ea4 aeb7bc c7ced2 dee3e5",      # оцинковка
    "ins": "595345 736c5a 8e8671 a79f88 beb7a0 d2ccb7 e3decc",       # изоляция
    "glass": "0d1a22 142a36 1d3d4d 2a5568 3f7387 5f95a6 8dbac6",
    "hot": "590d03 8e1b05 c33509 ed590f ff891d ffb73b ffe179",       # раскал
    "lamp_g": "06250f 0b3d17 125c22 1d8231 33ab45 63cf6d a5eaa4",
    "lamp_r": "350505 5a0808 890d0c bb1611 e3302a f4685c fbaa9c",
    # опознавательная окраска по ГОСТ 14202
    "p_water": "0c2a17 124021 1a592e 24743d 359050 54aa6c 82c794",
    "p_steam": "3a0a0a 571010 781717 9b201d bc2f28 d54b3d e97360",
    "p_air": "0d1d3a 142b55 1c3c73 285193 3b6bb1 5a8bca 86afdf",
    "p_gas": "4a3605 6b4e07 8f6b0b b58a0f d6a917 eec632 fbe268",
}
NAMES = list(_RAMPS)
RID = {n: i for i, n in enumerate(NAMES)}
STEPS = 13  # полушаги между семью опорными цветами


def _lut():
    out = np.zeros((len(NAMES), STEPS, 3))
    for i, n in enumerate(NAMES):
        k = np.array([[int(h[j:j + 2], 16) for j in (0, 2, 4)] for h in _RAMPS[n].split()], float)
        out[i, 0::2] = k
        out[i, 1::2] = (k[:-1] + k[1:]) / 2
    return out


LUT = _lut()

# зерно материала: (размах в уровнях, шаг по x, шаг по y)
GRAIN = {
    "shell": (0.33, 3, 3), "cast": (0.38, 2, 2), "steel": (0.3, 6, 1.4), "zinc": (0.33, 2, 4),
    "concrete": (0.5, 2, 2), "rust": (0.7, 2, 3), "brass": (0.27, 5, 1.4), "copper": (0.3, 4, 2),
    "black": (0.27, 3, 3), "ins": (0.4, 4, 2), "white": (0.2, 4, 4), "glass": (0.22, 4, 4),
}
GRAIN_DEFAULT = (0.26, 4, 4)  # крашеные поверхности: почти ровно, редкие пятна


def seed_of(*parts):
    return zlib.crc32("/".join(str(p) for p in parts).encode())


# ---------------------------------------------------------------- шрифт 3×5 для трафаретов и шильдиков
_F = {
    "0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111",
    "4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001010010010",
    "8": "111101111101111", "9": "111101111001111", "-": "000000111000000", ".": "000000000000010",
    "/": "001001010100100", " ": "000000000000000",
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110",
    "E": "111100110100111", "F": "111100110100100", "G": "011100101101011", "H": "101101111101101",
    "I": "111010010010111", "K": "101101110101101", "L": "100100100100111", "M": "101111111101101",
    "N": "110101101101101", "O": "010101101101010", "P": "110101110100100", "R": "110101110101101",
    "S": "011100010001110", "T": "111010010010010", "U": "101101101101111", "V": "101101101101010",
    "X": "101101010101101", "Y": "101101010010010", "Z": "111001010100111",
    "Б": "111100110101110", "Г": "111100100100100", "Д": "010101101111101", "Ж": "101111010111101",
    "З": "110001010001110", "И": "101101111111101", "Л": "011101101101101", "П": "111101101101101",
    "У": "101101011001110", "Ф": "010111111010010", "Ц": "101101101111001", "Ч": "101101111001001",
    "Ш": "101101111111111", "Э": "110001011001110", "Ю": "101111111111101", "Я": "011101011101101",
}
for _c, _l in zip("АВЕКМНОРСТХ", "ABEKMHOPCTX"):
    _F[_c] = _F[_l]


# ---------------------------------------------------------------- лист: материал + уровень
class Sheet:
    """Холст в «индексном» виде: r — материал, l — уровень 0…6, a — непрозрачность, e — без светотени."""

    def __init__(self, w, h, mat="steel", lvl=3.0):
        self.w, self.h = int(w), int(h)
        self.r = np.full((self.h, self.w), RID[mat], np.int16)
        self.l = np.full((self.h, self.w), float(lvl), np.float32)
        self.a = np.ones((self.h, self.w), bool)
        self.e = np.zeros((self.h, self.w), bool)

    def _k(self, x0, y0, x1, y1):
        x1 = self.w if x1 is None else x1
        y1 = self.h if y1 is None else y1
        f = lambda v, n: max(0, min(n, int(round(v))))
        return slice(f(y0, self.h), f(y1, self.h)), slice(f(x0, self.w), f(x1, self.w))

    def fill(self, x0=0, y0=0, x1=None, y1=None, mat=None, lvl=None, add=None, glow=None, alpha=None):
        """Прямоугольник [x0, x1) × [y0, y1): сменить материал, задать или сдвинуть уровень."""
        k = self._k(x0, y0, x1, y1)
        if mat is not None:
            self.r[k] = RID[mat]
        if lvl is not None:
            self.l[k] = lvl
        if add is not None:
            self.l[k] += add
        if glow is not None:
            self.e[k] = glow
        if alpha is not None:
            self.a[k] = alpha
        return self

    def px(self, x, y, **kw):
        return self.fill(x, y, x + 1, y + 1, **kw)

    def hline(self, y, x0=0, x1=None, **kw):
        return self.fill(x0, y, x1, y + 1, **kw)

    def vline(self, x, y0=0, y1=None, **kw):
        return self.fill(x, y0, x + 1, y1, **kw)

    def grain(self, seed, amp=None, sx=None, sy=None, x0=0, y0=0, x1=None, y1=None, mat=None):
        """Зерно пятнами: крупный шум с билинейной растяжкой и четверть мелкого."""
        k = self._k(x0, y0, x1, y1)
        h, w = self.l[k].shape
        if not h or not w:
            return self
        a, gx, gy = GRAIN.get(mat or NAMES[int(self.r[k][0, 0])], GRAIN_DEFAULT)
        amp, sx, sy = amp or a, sx or gx, sy or gy
        rnd = np.random.default_rng(seed)
        g = rnd.random((int(h / sy) + 2, int(w / sx) + 2))
        yy, xx = np.arange(h) / sy, np.arange(w) / sx
        y0i, x0i = yy.astype(int), xx.astype(int)
        fy, fx = (yy - y0i)[:, None], (xx - x0i)[None, :]
        n = (g[y0i][:, x0i] * (1 - fy) * (1 - fx) + g[y0i][:, x0i + 1] * (1 - fy) * fx
             + g[y0i + 1][:, x0i] * fy * (1 - fx) + g[y0i + 1][:, x0i + 1] * fy * fx)
        n = 0.75 * n + 0.25 * rnd.random((h, w))
        self.l[k] += ((n - 0.5) * 2 * amp).astype(np.float32)
        return self

    def frame(self, x0=0, y0=0, x1=None, y1=None, hi=1.0, lo=-1.0):
        """Выпуклая накладка: свет сверху и слева, тень снизу и справа (hi < 0 — вдавленная)."""
        x1 = self.w if x1 is None else x1
        y1 = self.h if y1 is None else y1
        self.hline(y0, x0, x1, add=hi).vline(x0, y0 + 1, y1 - 1, add=hi * 0.5)
        return self.hline(y1 - 1, x0, x1, add=lo).vline(x1 - 1, y0 + 1, y1 - 1, add=lo * 0.5)

    def seam_h(self, y, x0=0, x1=None):
        """Горизонтальный стык листов: тёмный паз и освещённая нижняя кромка."""
        return self.hline(y, x0, x1, add=-1.5).hline(y + 1, x0, x1, add=0.5)

    def seam_v(self, x, y0=0, y1=None):
        return self.vline(x, y0, y1, add=-1.5).vline(x + 1, y0, y1, add=0.5)

    def rivet(self, x, y):
        """Головка заклёпки или болта: блик и тень под ним; на светлом металле — тёмная точка."""
        if 0 <= x < self.w and 0 <= y < self.h and self.l[int(y), int(x)] >= 4.0:
            return self.px(x, y, add=-2.0)
        return self.px(x, y, add=1.5).px(x, y + 1, add=-1.0)

    def rivets_h(self, y, x0, x1, step=3):
        for x in range(int(x0), int(x1), step):
            self.rivet(x, y)
        return self

    def rivets_v(self, x, y0, y1, step=3):
        for y in range(int(y0), int(y1), step):
            self.rivet(x, y)
        return self

    def streaks(self, seed, n=3, mat="rust", length=(3, 8), x0=0, x1=None, y0=0):
        """Потёки сверху вниз: к концу потёк рвётся."""
        rnd = np.random.default_rng(seed)
        x1 = self.w if x1 is None else x1
        for _ in range(n):
            x, ln = int(rnd.integers(x0, max(x0 + 1, x1))), int(rnd.integers(length[0], length[1] + 1))
            for j in range(ln):
                y = y0 + j
                if not (0 <= y < self.h and 0 <= x < self.w) or (j > ln * 0.6 and rnd.random() < 0.5):
                    continue
                self.r[y, x] = RID[mat]
                self.l[y, x] = max(self.l[y, x] - 1.2 + j * 0.1, 1.0)
        return self

    def text(self, x, y, s, mat=None, lvl=None, add=None):
        """Строка шрифтом 3×5, шаг 4."""
        for ch in s.upper():
            g = _F.get(ch, _F[" "])
            for i, bit in enumerate(g):
                if bit == "1":
                    self.px(x + i % 3, y + i // 3, mat=mat, lvl=lvl, add=add)
            x += 4
        return self

    def disc(self, cx, cy, r, ring=None, **kw):
        """Круг радиуса r с центром (cx, cy) в координатах листа; ring — толщина кольца."""
        yy, xx = np.mgrid[0:self.h, 0:self.w]
        d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
        m = d <= r if ring is None else (d <= r) & (d > r - ring)
        for y, x in zip(*np.nonzero(m)):
            self.px(int(x), int(y), **kw)
        return self

    def louvres(self, x0, y0, x1, y1, step=2):
        """Жалюзи: тёмная щель и светлая кромка ламели."""
        for y in range(int(y0), int(y1), step):
            self.hline(y, x0, x1, add=-2.0)
            if step > 1:
                self.hline(y + 1, x0, x1, add=0.5)
        return self

    def hazard(self, x0, y0, x1, y1, w=2):
        """Косая жёлто-чёрная разметка."""
        for y in range(int(y0), int(y1)):
            for x in range(int(x0), int(x1)):
                on = ((x + y) // w) % 2 == 0
                self.px(x, y, mat="yellow" if on else "black", lvl=4.0 if on else 2.0)
        return self

    def bricks(self, seed, bw=8, bh=4, mortar="mortar"):
        """Кладка вперевязку: шов шириной в пиксель, кирпичи разного тона."""
        rnd = np.random.default_rng(seed)
        for row in range(0, self.h, bh):
            off = (row // bh % 2) * (bw // 2)
            for col in range(-bw, self.w, bw):
                self.fill(col + off, row, col + off + bw - 1, row + bh - 1, add=float(rnd.choice([-0.5, 0, 0, 0.5])))
                self.hline(row, col + off, col + off + bw - 1, add=0.5)
                self.vline(col + off + bw - 1, row, row + bh, mat=mortar, lvl=2.5)
            self.hline(row + bh - 1, mat=mortar, lvl=2.5)
        return self

    def blit(self, other, x, y):
        k = self._k(x, y, x + other.w, y + other.h)
        h, w = self.l[k].shape
        for name in ("r", "l", "a", "e"):
            getattr(self, name)[k] = getattr(other, name)[:h, :w]
        return self


def base_sheet(w, h, mat, seed, lvl=3.0):
    """Лист материала с его зерном."""
    return Sheet(w, h, mat, lvl).grain(seed, mat=mat)


# ---------------------------------------------------------------- геометрия
def rotmat(axis, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Box:
    def __init__(self, name, f, t, mat, rot=None, paint=None, share=None, bevel=True, lvl=3.0):
        self.name, self.mat, self.share, self.bevel, self.lvl = name, mat, share, bevel, lvl
        self.f, self.t = np.array(f, float), np.array(t, float)
        self.rot = rot
        self.R = rotmat(rot[0], rot[1]) if rot else np.eye(3)
        self.o = np.array(rot[2], float) if rot else np.zeros(3)
        self.paint = dict(paint or {})

    def world(self, local):
        return (local - self.o) @ self.R.T + self.o

    def local(self, world):
        return (world - self.o) @ self.R + self.o


class Planar:
    """Лист, натянутый на грань опорной коробки [f, t]: общий рисунок для нескольких деталей."""

    def __init__(self, sheet, face, f, t):
        self.sheet, self.face, self.f, self.t = sheet, face, np.array(f, float), np.array(t, float)

    def sample(self, P, n):
        _, _, ua, us, va, vs = FACES[self.face]
        x = P[..., ua] - self.f[ua] if us > 0 else self.t[ua] - P[..., ua]
        y = P[..., va] - self.f[va] if vs > 0 else self.t[va] - P[..., va]
        return (np.clip(np.floor(x), 0, self.sheet.w - 1).astype(int),
                np.clip(np.floor(y), 0, self.sheet.h - 1).astype(int))


class Wrap:
    """Развёртка цилиндра: x — по окружности (грань k занимает столбцы k·fw…), y — вдоль оси.

    Угол 0 и направление обхода: ось y — от южной грани к восточной; ось z — от верхней к восточной;
    ось x — от верхней к южной. y листа: у вертикального цилиндра сверху вниз, у горизонтальных — от a0.
    """
    PLANE = {"y": (0, 2), "z": (0, 1), "x": (2, 1)}  # (ось синуса, ось косинуса)

    def __init__(self, sheet, axis, c, n, fw, a0, a1):
        self.sheet, self.axis, self.n, self.fw, self.a0, self.a1 = sheet, axis, n, fw, a0, a1
        self.c = np.array(c, float)

    def sample(self, P, n):
        i_s, i_c = self.PLANE[self.axis]
        k = int(round(math.atan2(n[i_s], n[i_c]) / (2 * math.pi / self.n))) % self.n
        th = k * 2 * math.pi / self.n
        s = (P[..., i_s] - self.c[i_s]) * math.cos(th) - (P[..., i_c] - self.c[i_c]) * math.sin(th)
        a = AX[self.axis]
        v = self.a1 - P[..., a] if self.axis == "y" else P[..., a] - self.a0
        x = np.floor(k * self.fw + self.fw / 2 + s).astype(int) % self.sheet.w
        return x, np.clip(np.floor(v), 0, self.sheet.h - 1).astype(int)


class Cyl:
    """Что вернул Model.cyl: листы развёртки и торцов, радиусы."""

    def __init__(self, wrap, cap0, cap1, r, rc):
        self.wrap, self.cap0, self.cap1, self.r, self.rc = wrap, cap0, cap1, r, rc


EPS = 0.004      # насколько точка над гранью должна уйти в соседа, чтобы тексель считался утопленным
AO_DIST = 5.0    # дальше этого расстояния деталь тени не даёт
AO_GAIN = 3.2    # уровней при полном закрытии
AO_MAX = 2.0
AO_RAYS = 24


class Model:
    def __init__(self, id_, cat, kind="block", cullface=False):
        """cullface — модель в один блок: грани на его границе и детали за ней прячутся у глухого соседа."""
        self.id, self.cat, self.kind, self.cullface = id_, cat, kind, cullface
        self.boxes = []

    # ---- детали
    def box(self, name, f, t, mat, **kw):
        b = Box(name, f, t, mat, **kw)
        self.boxes.append(b)
        return b

    def cyl(self, name, axis, c, fw, a0, a1, mat, n=16, caps=(True, True), lvl=3.0, capmat=None,
            crisp=(False, False), share=None):
        """Призма из n граней шириной fw пикселей (n = 8 или 16) вокруг оси через c.

        Собрана из брусков сквозь ось, как pclib.disc; торцы разнесены на 0,01, чтобы не мерцали.
        Возвращает Cyl: рисуйте по .wrap (развёртка n·fw × длина) и .cap0/.cap1 (торцы у a0 и a1).
        crisp — накрыть торец ровной накладкой: рисунок торца ложится в сетку пикселей, а не
        поворачивается вместе с брусками (шкалы, решётки, болтовые круги). share — одинаковые
        кольца делят одно место в атласе (рисунок развёртки тогда берётся с первой грани).
        """
        r = fw / (2 * math.tan(math.pi / n))
        rc = r / math.cos(math.pi / n)
        a = AX[axis]
        plane = [i for i in range(3) if i != a]
        cc = np.zeros(3)
        cc[plane[0]], cc[plane[1]] = c
        ln = int(math.ceil(a1 - a0 - 1e-4))
        wrap = base_sheet(int(round(n * fw)), ln, mat, seed_of(self.id, name, "wrap"), lvl)
        d = int(math.ceil(2 * rc - 1e-4))
        if abs((c[0] - d / 2) - round(c[0] - d / 2)) > 1e-6:
            d += 1  # край листа торца — на целом пикселе модели
        cf, ct = cc.copy(), cc.copy()
        cf[plane[0]] -= d / 2; cf[plane[1]] -= d / 2; ct[plane[0]] += d / 2; ct[plane[1]] += d / 2
        cf[a], ct[a] = a0, a1
        end0, end1 = {"x": ("west", "east"), "y": ("down", "up"), "z": ("north", "south")}[axis]
        cap0 = base_sheet(d, d, capmat or mat, seed_of(self.id, name, "cap0"), lvl)
        cap1 = base_sheet(d, d, capmat or mat, seed_of(self.id, name, "cap1"), lvl)
        w_s = Wrap(wrap, axis, cc, n, fw, a0, a1)
        bars = [(0, 0), (1, 0), (0, 45), (0, -45)] if n == 8 else \
            [(0, 0), (0, 22.5), (0, 45), (0, -22.5), (0, -45), (1, 0), (1, 22.5), (1, -22.5)]
        names = {0: ("west", "east"), 1: ("down", "up"), 2: ("north", "south")}
        for i, (swap, ang) in enumerate(bars):
            long_ax, thin_ax = (plane[1], plane[0]) if swap else (plane[0], plane[1])
            f, t = cc.copy(), cc.copy()
            f[long_ax] -= r; t[long_ax] += r; f[thin_ax] -= fw / 2; t[thin_ax] += fw / 2
            f[a], t[a] = max(a0 - 0.01 * i, -15.95), min(a1 + 0.01 * i, 31.95)
            paint = {nm: w_s for nm in names[long_ax]}
            paint.update({nm: False for nm in names[thin_ax]})
            paint[end0] = Planar(cap0, end0, cf, ct) if caps[0] else False
            paint[end1] = Planar(cap1, end1, cf, ct) if caps[1] else False
            self.box(f"{name}_{i}", f, t, mat, rot=(axis, ang, [round(float(v), 4) for v in cc]) if ang else None,
                     paint=paint, bevel=False, share=share)
        # ровные накладки на торцы: пиксельный круг из прямоугольников, вписанный в многоугольник
        ang_k = [2 * math.pi * k / n for k in range(n)]
        hw = []
        for j in range(d):
            row = [i for i in range(d) if all(
                (px - d / 2) * math.sin(th) + (py - d / 2) * math.cos(th) <= r + 1e-6
                for th in ang_k for px in (i, i + 1) for py in (j, j + 1))]
            hw.append(len(row) / 2)
        for end, on, sheet, face in ((0, crisp[0], cap0, end0), (1, crisp[1], cap1, end1)):
            if not on:
                continue
            for q, w_ in enumerate(sorted({w for w in hw if w > 0}, reverse=True)):
                rows = [j for j in range(d) if hw[j] >= w_]
                f, t = cc.copy(), cc.copy()
                f[plane[0]] -= w_; t[plane[0]] += w_
                yh = (rows[-1] - rows[0] + 1) / 2
                f[plane[1]] -= yh; t[plane[1]] += yh
                if end:
                    t[a] = min(a1 + 0.1, 32.0); f[a] = t[a] - 0.015
                else:
                    f[a] = max(a0 - 0.1, -16.0); t[a] = f[a] + 0.015
                paint = {nm: False for nm in FACES}
                paint[face] = Planar(sheet, face, cf, ct)
                self.box(f"{name}_cap{end}_{q}", f, t, mat, paint=paint, bevel=False)
        return Cyl(wrap, cap0, cap1, r, rc)

    # ---- сборка
    def _inside(self, P):
        """Лежит ли точка строго внутри хоть одной детали."""
        out = np.zeros(P.shape[:-1], bool)
        for b in self.boxes:
            q = b.local(P) if b.rot else P
            out |= np.all((q > b.f + 1e-6) & (q < b.t - 1e-6), axis=-1)
        return out

    def _occlusion(self, P, N, U, V):
        """Доля закрытого полушария над каждым текселем, ближние детали весят больше."""
        k = np.arange(AO_RAYS)
        rr, ph = np.sqrt((k + 0.5) / AO_RAYS), k * 2.399963
        a, b, c = rr * np.cos(ph), rr * np.sin(ph), np.sqrt(1 - rr * rr)
        D = (a[None, :, None] * U[:, None, :] + b[None, :, None] * V[:, None, :] + c[None, :, None] * N[:, None, :])
        O = np.repeat((P + N * 0.02)[:, None, :], AO_RAYS, axis=1).reshape(-1, 3).astype(np.float32)
        D = D.reshape(-1, 3).astype(np.float32)
        best = np.zeros(len(O), np.float32)
        lo, hi = O.min(axis=0) - AO_DIST, O.max(axis=0) + AO_DIST
        for bx in self.boxes:
            if not bx.rot and (np.any(bx.t < lo) or np.any(bx.f > hi)):
                continue
            o, d = (bx.local(O), D @ bx.R) if bx.rot else (O, D)
            d = np.where(np.abs(d) < 1e-7, 1e-7, d)
            t1, t2 = (bx.f - o) / d, (bx.t - o) / d
            tn, tf = np.minimum(t1, t2).max(axis=1), np.maximum(t1, t2).min(axis=1)
            hit = (tf > np.maximum(tn, 0)) & (tn < AO_DIST)
            w = np.where(hit, 1 - np.maximum(tn, 0) / AO_DIST, 0).astype(np.float32)
            np.maximum(best, w, out=best)
        return best.reshape(-1, AO_RAYS).mean(axis=1)

    def build(self):
        faces, shared = [], {}
        for b in self.boxes:
            for d, (na, ns, ua, us, va, vs) in FACES.items():
                src = b.paint.get(d)
                if src is False:
                    continue
                du, dv = b.t[ua] - b.f[ua], b.t[va] - b.f[va]
                if du < 1e-4 or dv < 1e-4:
                    continue
                w, h = max(1, math.ceil(du - 1e-4)), max(1, math.ceil(dv - 1e-4))
                iu = np.minimum(np.arange(w) + 0.5, du - 1e-3)
                iv = np.minimum(np.arange(h) + 0.5, dv - 1e-3)
                L = np.zeros((h, w, 3))
                L[..., ua] = (b.f[ua] + iu) if us > 0 else (b.t[ua] - iu)
                L[..., va] = ((b.f[va] + iv) if vs > 0 else (b.t[va] - iv))[:, None]
                L[..., na] = b.t[na] if ns > 0 else b.f[na]
                n, u, v = np.zeros(3), np.zeros(3), np.zeros(3)
                n[na], u[ua], v[va] = ns, us, vs
                P, N, U, V = b.world(L), b.R @ n, b.R @ u, b.R @ v
                vis = ~self._inside(P + N * EPS)
                if not vis.any():
                    continue
                fc = dict(box=b, dir=d, w=w, h=h, du=du, dv=dv, P=P, N=N, U=U, V=V, vis=vis, L=L)
                if b.share:
                    key = (b.share, d, w, h)
                    if key in shared:
                        fc["same"] = shared[key]
                    else:
                        shared[key] = fc
                faces.append(fc)

        own = [f for f in faces if "same" not in f]
        # ---- живопись
        for f in own:
            b, d, w, h = f["box"], f["dir"], f["w"], f["h"]
            src = b.paint.get(d)
            if isinstance(src, (Planar, Wrap)):
                x, y = src.sample(f["P"], f["N"])
                sh = src.sheet
                f["r"], f["l"], f["a"], f["e"] = sh.r[y, x].copy(), sh.l[y, x].copy(), sh.a[y, x].copy(), sh.e[y, x].copy()
                continue
            if isinstance(src, Sheet):
                sh = src
                if (sh.w, sh.h) != (w, h):
                    raise ValueError(f"{self.id}/{b.name}.{d}: лист {sh.w}×{sh.h}, грань {w}×{h}")
            else:
                sh = base_sheet(w, h, b.mat, seed_of(self.id, b.share or b.name, d), b.lvl)
                if callable(src):
                    src(sh)
            if b.name.startswith("glow_"):
                sh.e[:] = True  # светящиеся детали без теней
            f["r"], f["l"], f["a"], f["e"] = sh.r.copy(), sh.l.copy(), sh.a.copy(), sh.e.copy()

        # ---- светотень рёбер: только там, где ребро открыто и выпукло
        for f in own:
            b, d, w, h = f["box"], f["dir"], f["w"], f["h"]
            if not b.bevel:
                continue
            na, ns, ua, us, va, vs = FACES[d]
            flat = abs(f["N"][1]) > 0.7
            amt = {"top": 0.5 if flat else 1.0, "bottom": -0.5 if flat else -1.0, "left": 0.5, "right": -0.5}
            if h <= 2:
                amt["top"], amt["bottom"] = (0.5, 0.0) if h == 1 else (0.5, -0.5)
            if w <= 2:
                amt["left"], amt["right"] = (0.0, 0.0) if w == 1 else (0.5, -0.5)
            L = f["L"]
            for edge, val in amt.items():
                if not val:
                    continue
                if edge in ("left", "right"):
                    q = L[:, 0 if edge == "left" else w - 1].copy()
                    first = edge == "left"
                    lo_side = (us > 0) == first
                    q[:, ua] = (b.f[ua] - 0.25) if lo_side else (b.t[ua] + 0.25)
                else:
                    q = L[0 if edge == "top" else h - 1].copy()
                    first = edge == "top"
                    lo_side = (vs > 0) == first
                    q[:, va] = (b.f[va] - 0.25) if lo_side else (b.t[va] + 0.25)
                Q = b.world(q)
                open_ = ~(self._inside(Q + f["N"] * EPS) | self._inside(Q - f["N"] * 0.25))
                line = f["l"][:, 0 if edge == "left" else w - 1] if edge in ("left", "right") else \
                    f["l"][0 if edge == "top" else h - 1]
                e = f["e"][:, 0 if edge == "left" else w - 1] if edge in ("left", "right") else \
                    f["e"][0 if edge == "top" else h - 1]
                line += np.where(open_ & ~e, val, 0).astype(np.float32)

        # ---- запечённое затенение
        if own:
            cnt = [int(f["vis"].sum()) for f in own]
            P = np.concatenate([f["P"][f["vis"]] for f in own])
            N = np.concatenate([np.repeat(f["N"][None], c, axis=0) for f, c in zip(own, cnt)])
            U = np.concatenate([np.repeat(f["U"][None], c, axis=0) for f, c in zip(own, cnt)])
            V = np.concatenate([np.repeat(f["V"][None], c, axis=0) for f, c in zip(own, cnt)])
            occ = self._occlusion(P, N, U, V)
            pos = 0
            for f, c in zip(own, cnt):
                ao = np.zeros(f["vis"].shape, np.float32)
                ao[f["vis"]] = np.minimum(AO_GAIN * occ[pos:pos + c] ** 0.9, AO_MAX)
                pos += c
                f["l"] -= np.where(f["e"], 0, ao)

        # ---- раскладка атласа: полки по убыванию высоты, зазор 1 пиксель
        order = sorted(own, key=lambda f: (-f["h"], -f["w"]))
        size = 16
        while True:
            x = y = row = 0
            ok = True
            for f in order:
                if x + f["w"] > size:
                    x, y, row = 0, y + row + 1, 0
                if f["w"] > size or y + f["h"] > size:
                    ok = False
                    break
                f["x"], f["y"] = x, y
                x += f["w"] + 1
                row = max(row, f["h"])
            if ok:
                break
            size *= 2
        img = np.zeros((size, size, 4), np.uint8)
        for f in own:
            q = np.clip(np.rint(f["l"] * 2), 0, STEPS - 1).astype(int)
            k = (slice(f["y"], f["y"] + f["h"]), slice(f["x"], f["x"] + f["w"]))
            img[k][..., :3] = LUT[f["r"], q]
            img[k][..., 3] = np.where(f["a"], 255, 0)
        self.cutout = bool(any((~f["a"]).any() for f in own))
        img = _bleed(img, np.array([[f["x"], f["y"], f["w"], f["h"]] for f in own]), self.cutout)

        # ---- элементы для Blockbench
        by_box = {}
        for f in faces:
            g = f.get("same", f)
            by_box.setdefault(id(f["box"]), (f["box"], {}))[1][f["dir"]] = [
                g["x"], g["y"], round(g["x"] + f["du"], 4), round(g["y"] + f["dv"], 4)]
        els = []
        for b, uv in by_box.values():
            e = {"name": b.name, "from": [round(float(v), 4) for v in b.f], "to": [round(float(v), 4) for v in b.t],
                 "tex": "t", "faces": {}, "uv": uv}
            if b.rot:
                e["rot"] = {"axis": b.rot[0], "angle": b.rot[1], "origin": list(b.rot[2])}
            elif self.cullface:
                beyond = None
                for d, (na, ns, *_) in FACES.items():
                    if (b.t[na] <= 1e-6) if ns < 0 else (b.f[na] >= 16 - 1e-6):
                        beyond = d
                cull = {}
                for d in uv:
                    na, ns = FACES[d][:2]
                    edge = abs(b.f[na]) < 1e-6 if ns < 0 else abs(b.t[na] - 16) < 1e-6
                    if beyond or edge:
                        cull[d] = beyond or d
                if cull:
                    e["cull"] = cull
            els.append(e)
        self.size, self.quads = size, len(faces)
        return els, img

    def done(self):
        MODELS.append(self)
        return self


def _bleed(img, rects, cutout):
    """Заливает зазоры цветом соседних островов, чтобы мип-уровни не тянули чёрное."""
    filled = np.zeros(img.shape[:2], bool)
    for x, y, w, h in rects:
        filled[y:y + h, x:x + w] = True
    keep_alpha = img[..., 3].copy()
    rgb = img[..., :3].astype(float)
    for _ in range(64):
        if filled.all():
            break
        acc, cnt = np.zeros_like(rgb), np.zeros(filled.shape)
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            sf = np.roll(filled, (dy, dx), axis=(0, 1))
            sc = np.roll(rgb, (dy, dx), axis=(0, 1))
            if dy == 1: sf[0] = False
            if dy == -1: sf[-1] = False
            if dx == 1: sf[:, 0] = False
            if dx == -1: sf[:, -1] = False
            acc += sc * sf[..., None]
            cnt += sf
        new = ~filled & (cnt > 0)
        rgb[new] = acc[new] / cnt[new][:, None]
        filled |= new
    out = np.zeros_like(img)
    out[..., :3] = np.clip(np.rint(rgb), 0, 255)
    out[..., 3] = 255
    if cutout:
        island = np.zeros(img.shape[:2], bool)
        for x, y, w, h in rects:
            island[y:y + h, x:x + w] = True
        out[..., 3] = np.where(island, keep_alpha, 255)
    return out


MODELS = []


def java_path(id_, kind):
    if kind == "item":
        return f"item/{id_}"
    if "/" in id_:
        blk, part = id_.split("/")
        return f"block/{blk}/partial/{part}"
    return f"block/{id_}"


def tex_rel(id_):
    return f"block/hq/{id_.replace('/', '__')}.png"
