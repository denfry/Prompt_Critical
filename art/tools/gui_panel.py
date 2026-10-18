"""Спрайты местного щита A0 (GUI машин): окрашенная молотковой эмалью сталь, латунная табличка, циферблаты,
водомерное стекло, лампы, бакелитовые кнопки, слоты, окна табло.

Пишет PNG и .mcmeta (nine_slice) в assets/promptcritical/textures/gui/sprites/panel/. Детерминирован (seed),
повторный прогон даёт те же файлы.

    python art/tools/gui_panel.py <repo>
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

OUT = "pc-mod/src/main/resources/assets/promptcritical/textures/gui/sprites/panel"

PAINT = (92, 108, 100)          # молотковая эмаль, серо-зелёная
EDGE = (22, 27, 25)
HI = (140, 156, 147)
LO = (52, 62, 57)
STEEL = (176, 182, 178)
BRASS_HI, BRASS, BRASS_LO, BRASS_EDGE = (236, 208, 132), (198, 162, 82), (156, 120, 50), (72, 52, 18)
FACE, FACE_EDGE = (238, 231, 208), (214, 203, 172)
INK = (24, 24, 24)


def clamp(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def mul(c, k):
    return clamp(v * k for v in c[:3]) + tuple(c[3:])


def tile_noise(w, h, period, seed, cells=5):
    """Молотковая эмаль: сглаженные ячейки, периодичные с шагом period (центр nine_slice повторяется)."""
    rnd = random.Random(seed)
    pts = [(rnd.uniform(0, period), rnd.uniform(0, period), rnd.uniform(-1, 1)) for _ in range(cells * cells)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            px, py = x % period, y % period
            best, second, val = 1e9, 1e9, 0.0
            for cx, cy, v in pts:
                dx = min(abs(px - cx), period - abs(px - cx))
                dy = min(abs(py - cy), period - abs(py - cy))
                d = dx * dx + dy * dy
                if d < best:
                    best, second, val = d, best, v
                elif d < second:
                    second = d
            rim = 1.0 if math.sqrt(second) - math.sqrt(best) < 0.9 else 0.0
            out[y][x] = val * 0.55 - rim * 0.45
    return out


def save(img, name, nine=None):
    path = ROOT / OUT / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    meta = path.with_suffix(".png.mcmeta")
    if nine is not None:
        w, h = img.size
        border = nine if isinstance(nine, int) else dict(zip(("left", "top", "right", "bottom"), nine))
        meta.write_text(json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h,
                                                        "border": border}}}, indent=2) + "\n", encoding="utf-8")
    elif meta.exists():
        meta.unlink()


def screw(px, x, y):
    """Винт с прямым шлицем 3×3."""
    for dx, dy in ((0, 0), (1, 0), (2, 0), (0, 1), (2, 1), (0, 2), (1, 2), (2, 2), (1, 1)):
        px[x + dx, y + dy] = STEEL + (255,)
    px[x, y] = mul(STEEL, 1.1) + (255,)
    px[x + 2, y + 2] = mul(STEEL, 0.6) + (255,)
    px[x, y + 2] = mul(STEEL, 0.75) + (255,)
    px[x + 2, y] = mul(STEEL, 0.85) + (255,)
    px[x + 1, y + 1] = (60, 64, 62, 255)
    px[x + 2, y] = (70, 74, 72, 255)
    px[x, y + 2] = (70, 74, 72, 255)


def background():
    s, b = 32, 7
    img = Image.new("RGBA", (s, s))
    px = img.load()
    noise = tile_noise(s, s, s - 2 * b, seed=7)
    for y in range(s):
        for x in range(s):
            n = noise[(y - b) % s][(x - b) % s]
            px[x, y] = mul(PAINT, 1 + 0.07 * n) + (255,)
    for i in range(s):
        for (x, y) in ((i, 0), (i, s - 1), (0, i), (s - 1, i)):
            px[x, y] = EDGE + (255,)
    for i in range(1, s - 1):
        px[i, 1] = HI + (255,)
        px[1, i] = HI + (255,)
        px[i, s - 2] = LO + (255,)
        px[s - 2, i] = LO + (255,)
    for i in range(2, s - 2):
        px[i, 2] = mul(HI, 0.86) + (255,)
        px[2, i] = mul(HI, 0.86) + (255,)
        px[i, s - 3] = mul(LO, 1.12) + (255,)
        px[s - 3, i] = mul(LO, 1.12) + (255,)
    px[1, s - 2] = px[s - 2, 1] = mul(PAINT, 0.9) + (255,)
    for x, y in ((3, 3), (s - 6, 3), (3, s - 6), (s - 6, s - 6)):
        screw(px, x, y)
    save(img, "background", b)


def inset():
    s = 16
    img = Image.new("RGBA", (s, s), (26, 31, 29, 255))
    px = img.load()
    for i in range(s):
        px[i, 0] = (12, 15, 14, 255)
        px[0, i] = (12, 15, 14, 255)
        px[i, 1] = (18, 22, 20, 255)
        px[1, i] = (18, 22, 20, 255)
        px[i, s - 1] = (128, 144, 136, 255)
        px[s - 1, i] = (128, 144, 136, 255)
    px[0, s - 1] = px[s - 1, 0] = (70, 80, 75, 255)
    save(img, "inset", 3)


def plate():
    w, h = 24, 16
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        k = y / (h - 1)
        c = tuple(BRASS_HI[i] * (1 - k) * 0.25 + (BRASS[i] * (1 - k) + BRASS_LO[i] * k) * 0.75 for i in range(3))
        for x in range(w):
            px[x, y] = clamp(c) + (255,)
    for x in range(w):
        px[x, 0] = BRASS_EDGE + (255,)
        px[x, h - 1] = BRASS_EDGE + (255,)
        px[x, 1] = BRASS_HI + (255,)
        px[x, h - 2] = mul(BRASS_LO, 0.85) + (255,)
    for y in range(h):
        px[0, y] = BRASS_EDGE + (255,)
        px[w - 1, y] = BRASS_EDGE + (255,)
        px[1, y] = mul(BRASS_HI, 0.95) + (255,) if 0 < y < h - 1 else px[1, y]
        px[w - 2, y] = mul(BRASS_LO, 0.9) + (255,) if 0 < y < h - 1 else px[w - 2, y]
    for x in (2, w - 5):
        screw(px, x, h // 2 - 1)
    save(img, "plate", (6, 3, 6, 3))


def ss_disc(draw, cx, cy, r, fill, k):
    draw.ellipse([(cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k], fill=fill)


def dial(size, name, major_len, minor_len, minor_every):
    k = 8
    img = Image.new("RGBA", (size * k, size * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    r = size / 2 - 0.25
    # обод: чёрная эмаль с бликом сверху-слева
    ss_disc(d, c, c, r, (18, 18, 20, 255), k)
    ss_disc(d, c - 0.4, c - 0.4, r - 0.9, (58, 60, 64, 255), k)
    ss_disc(d, c + 0.3, c + 0.3, r - 1.4, (30, 31, 34, 255), k)
    ss_disc(d, c, c, r - 2.2, BRASS_LO + (255,), k)
    ss_disc(d, c - 0.2, c - 0.2, r - 2.6, BRASS + (255,), k)
    # шкала: кремовая, темнее к краю
    face_r = r - 3.2
    steps = 24
    for i in range(steps):
        t = i / (steps - 1)
        col = clamp(FACE_EDGE[j] * (1 - t) + FACE[j] * t for j in range(3))
        ss_disc(d, c, c, face_r * (1 - 0.35 * t), col + (255,), k)
    # дуга и риски: 270°, от -135° до +135° от вертикали, по часовой
    arc_r = face_r - 1.4
    box = [(c - arc_r) * k, (c - arc_r) * k, (c + arc_r) * k, (c + arc_r) * k]
    d.arc(box, start=135, end=405, fill=INK + (255,), width=max(1, int(0.7 * k)))
    n = 10 * minor_every
    for i in range(n + 1):
        a = math.radians(-135 + 270 * i / n)
        major = i % minor_every == 0
        ln = major_len if major else minor_len
        w = 1.1 if major else 0.75
        r0, r1 = arc_r, arc_r - ln
        x0, y0 = c + r0 * math.sin(a), c - r0 * math.cos(a)
        x1, y1 = c + r1 * math.sin(a), c - r1 * math.cos(a)
        d.line([x0 * k, y0 * k, x1 * k, y1 * k], fill=INK + (255,), width=max(1, int(w * k)))
    # стекло: блик
    glare = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glare)
    gd.pieslice([(c - face_r + 1) * k, (c - face_r + 1) * k, (c + face_r - 1) * k, (c + face_r - 1) * k],
                start=200, end=250, fill=(255, 255, 255, 34))
    img = Image.alpha_composite(img, glare)
    save(img.resize((size, size), Image.LANCZOS), name)


def hub():
    k, s = 8, 6
    img = Image.new("RGBA", (s * k, s * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    ss_disc(d, 3, 3, 2.9, (20, 20, 20, 255), k)
    ss_disc(d, 3, 3, 2.3, BRASS_LO + (255,), k)
    ss_disc(d, 2.6, 2.6, 1.5, BRASS + (255,), k)
    ss_disc(d, 2.2, 2.2, 0.6, BRASS_HI + (255,), k)
    save(img.resize((s, s), Image.LANCZOS), "hub")


def glass():
    w, h = 12, 32
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            if 3 <= x <= w - 4:
                px[x, y] = (30, 38, 44, 255)
            elif x in (2, w - 3):
                px[x, y] = (70, 80, 86, 255)
            elif x in (1, w - 2):
                px[x, y] = (120, 128, 130, 255)
            else:
                px[x, y] = (34, 38, 40, 255)
    for top in (True, False):
        for i in range(5):
            y = i if top else h - 1 - i
            for x in range(w):
                edge = x in (0, w - 1) or i == 0
                band = i == 2
                col = BRASS_EDGE if edge else (BRASS_LO if band else (BRASS_HI if x < w // 2 and not top else BRASS))
                px[x, y] = col + (255,)
    save(img, "glass", (3, 5, 3, 5))


LAMPS = {"green": (70, 220, 90), "red": (240, 60, 48), "amber": (250, 176, 40), "white": (244, 240, 222)}


def lamp(color, rgb, on):
    k, s = 8, 14
    img = Image.new("RGBA", (s * k, s * k), (0, 0, 0, 0))
    if on:
        halo = Image.new("RGBA", img.size, (0, 0, 0, 0))
        hd = ImageDraw.Draw(halo)
        for i in range(12):
            t = i / 11
            hd.ellipse([(7 - 7 + t * 3) * k, (7 - 7 + t * 3) * k, (7 + 7 - t * 3) * k, (7 + 7 - t * 3) * k],
                       fill=rgb + (int(10 + 40 * t),))
        img = Image.alpha_composite(img, halo)
    d = ImageDraw.Draw(img)
    ss_disc(d, 7, 7, 5.0, (16, 16, 18, 255), k)
    ss_disc(d, 6.8, 6.8, 4.5, (150, 156, 156, 255), k)
    ss_disc(d, 7.2, 7.2, 4.0, (70, 74, 76, 255), k)
    lens = rgb if on else mul(rgb, 0.32)
    ss_disc(d, 7, 7, 3.4, mul(lens, 0.8 if on else 1.0) + (255,), k)
    ss_disc(d, 6.8, 6.8, 2.6, lens + (255,), k)
    if on:
        ss_disc(d, 6.6, 6.6, 1.5, clamp(v * 0.5 + 127 for v in rgb) + (255,), k)
    ss_disc(d, 5.7, 5.6, 0.8, (255, 255, 255, 170 if on else 60), k)
    save(img.resize((s, s), Image.LANCZOS), f"lamp_{color}_{'on' if on else 'off'}")


def button(name, frame, top, body, bottom, pressed=False):
    s = 20
    img = Image.new("RGBA", (s, s))
    px = img.load()
    for y in range(s):
        for x in range(s):
            px[x, y] = body + (255,)
    for i in range(s):
        for (x, y) in ((i, 0), (i, s - 1), (0, i), (s - 1, i)):
            px[x, y] = frame + (255,)
    a, b = (bottom, top) if pressed else (top, bottom)
    for i in range(1, s - 1):
        px[i, 1] = a + (255,)
        px[1, i] = a + (255,)
        px[i, s - 2] = b + (255,)
        px[s - 2, i] = b + (255,)
        px[i, 2] = mul(a, 0.85) + (255,) if not pressed else px[i, 2]
    save(img, name, 3)


def slot(name, output):
    s = 18
    img = Image.new("RGBA", (s, s), (40, 47, 44, 255))
    px = img.load()
    for i in range(s):
        px[i, 0] = (16, 20, 18, 255)
        px[0, i] = (16, 20, 18, 255)
        px[i, s - 1] = (146, 160, 152, 255)
        px[s - 1, i] = (146, 160, 152, 255)
    px[0, s - 1] = px[s - 1, 0] = (84, 96, 90, 255)
    if output:
        for x, y in ((1, 1), (2, 1), (1, 2), (16, 1), (15, 1), (16, 2), (1, 16), (2, 16), (1, 15), (16, 16), (15, 16), (16, 15)):
            px[x, y] = BRASS + (255,)
    save(img, name)


def track():
    w, h = 16, 8
    img = Image.new("RGBA", (w, h), (20, 24, 22, 255))
    px = img.load()
    for x in range(w):
        px[x, 0] = (10, 12, 11, 255)
        px[x, h - 1] = (128, 144, 136, 255)
    for y in range(h):
        px[0, y] = (10, 12, 11, 255)
        px[w - 1, y] = (128, 144, 136, 255)
    save(img, "track", 2)


def window():
    w, h = 16, 12
    img = Image.new("RGBA", (w, h), (10, 13, 11, 255))
    px = img.load()
    for x in range(w):
        px[x, 0] = (40, 44, 42, 255)
        px[x, 1] = (70, 76, 72, 255)
        px[x, h - 1] = (150, 160, 154, 255)
        px[x, h - 2] = (96, 104, 100, 255)
    for y in range(h):
        px[0, y] = (40, 44, 42, 255)
        px[1, y] = (70, 76, 72, 255) if 0 < y < h - 1 else px[1, y]
        px[w - 1, y] = (150, 160, 154, 255)
        px[w - 2, y] = (96, 104, 100, 255) if 0 < y < h - 1 else px[w - 2, y]
    for x in range(2, w - 2):
        px[x, 2] = (4, 6, 5, 255)
    save(img, "window", 3)


def main():
    background()
    inset()
    plate()
    dial(64, "dial_large", major_len=5.0, minor_len=2.6, minor_every=2)
    dial(48, "dial_medium", major_len=3.6, minor_len=2.0, minor_every=2)
    hub()
    glass()
    for color, rgb in LAMPS.items():
        lamp(color, rgb, False)
        lamp(color, rgb, True)
    button("button", (96, 104, 100), (88, 90, 94), (36, 37, 40), (12, 12, 13))
    button("button_hover", BRASS, (110, 112, 116), (46, 47, 51), (14, 14, 15))
    button("button_pressed", BRASS_LO, (88, 90, 94), (28, 29, 31), (10, 10, 11), pressed=True)
    button("button_disabled", (70, 76, 72), (66, 68, 70), (44, 46, 48), (30, 32, 33))
    slot("slot", False)
    slot("slot_output", True)
    track()
    window()


if __name__ == "__main__":
    ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    main()
