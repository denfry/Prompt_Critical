"""11 §11.6.2: декор промышленных интерьеров. Полные блоки — текстуры здесь; slab/stairs/wall
по ванильным шаблонам собирает datagen из тех же текстур."""
import math
from artkit import *

D = lambda n: f"block/decor/{n}.png"


def tiles(col, seam, size=15):  # плитка 15×15 со швом 1 px (шов на краю — стык соседнего блока)
    def fn():
        a = canvas(col, noise=2)
        box(a, 0, 15, 15, 15, seam)
        box(a, 15, 0, 15, 15, seam)
        if size < 15:
            box(a, 0, size, 15, size, seam)
            box(a, size, 0, size, 15, seam)
        kbox(a, 1, 1, 6, 1, 1.08)
        return a
    return fn


def speckle(col, dots):
    def fn():
        a = canvas(col, noise=3)
        for _ in range(16):
            put(a, rng.randrange(16), rng.randrange(16), dots)
        box(a, 0, 15, 15, 15, "#8a8a84")
        box(a, 15, 0, 15, 15, "#8a8a84")
        return a
    return fn


def parquet():
    a = canvas("#9a6a3a", noise=5)
    for y in range(0, 16, 4):
        for x in range(0, 16, 8):
            off = 4 if (y // 4) % 2 else 0
            box(a, (x + off) % 16, y, (x + off) % 16, y + 3, "#6a4422")
        box(a, 0, y, 15, y, "#7a5028")
    return a


def raised(perf):
    def fn():
        a = canvas("#b8bab6", noise=2)
        frame(a, 1.12, 0.72)
        if perf:
            for y in range(3, 13, 2):
                for x in range(3, 13, 2):
                    put(a, y, x, "#3a3d40")
        return a
    return fn


def ceiling():
    a = canvas("#ecebe6", noise=3)
    for _ in range(24):
        put(a, rng.randrange(16), rng.randrange(16), "#c8c6be")
    box(a, 0, 15, 15, 15, "#b4b4ae")
    box(a, 15, 0, 15, 15, "#b4b4ae")
    return a


def light_panel():
    a = canvas("#fbfbf4", noise=2)
    frame(a, 0.95, 0.7)
    for x in (5, 10):
        box(a, x, 1, x, 14, "#e2e2da")
    return a


def two_tone(low):  # масляная краска: низ цветной до 8 px, верх белый (побелка)
    def fn():
        a = canvas("#ecebe4", noise=3)
        box(a, 0, 8, 15, 15, low)
        box(a, 0, 8, 15, 8, "#3a3d40")
        for y in range(9, 16):
            for x in range(16):
                shade(a, y, x, rng.uniform(0.95, 1.04))
        return a
    return fn


def painted(col):
    def fn():
        a = canvas(col, noise=4)
        for _ in range(8):
            shade(a, rng.randrange(16), rng.randrange(16), 0.85)
        return a
    return fn


def glass_block(frost):
    def fn():
        a = canvas("#cfe3ea" if not frost else "#e4ecee", noise=6 if frost else 2)
        a[:, :, 3] = 150 if not frost else 215
        for i in range(16):
            for p in ((0, i), (15, i), (i, 0), (i, 15), (7, i), (i, 7)):
                a[p[0], p[1]] = C("#b4bcbe")
        return a
    return fn


def corrugated(col):
    def fn():
        a = canvas(col, noise=3)
        for x in range(16):
            k = (1.25, 1.0, 0.75, 1.0)[x % 4]
            for y in range(16):
                shade(a, y, x, k)
        return a
    return fn


def sandwich(col):
    def fn():
        a = canvas(col, noise=2)
        box(a, 0, 0, 15, 0, "#8a8e92")
        for y in (5, 10):
            for x in range(16):
                shade(a, y, x, 0.9)
        return a
    return fn


def asphalt():
    a = canvas("#3a3b3d", noise=9)
    for _ in range(20):
        put(a, rng.randrange(16), rng.randrange(16), rng.choice(("#55565a", "#2a2b2d")))
    return a


def marking(col, arrow=False):  # наложение разметки (прозрачный фон)
    def fn():
        a = blank()
        if arrow:
            box(a, 7, 6, 8, 14, col)
            for i in range(5):
                box(a, 7 - i, 2 + i, 8 + i, 2 + i, col)
        else:
            box(a, 6, 0, 9, 15, col)
        return a
    return fn


def curb():
    a = canvas("#a8a8a2", noise=5)
    box(a, 0, 0, 15, 1, "#c4c4be")
    return a


def poster(draw, w=32, h=16):
    def fn():
        a = blank(w, h)
        a[:, :] = C("#f1eee4")
        box(a, 0, 0, w - 1, 2, "#c0392b")
        draw(a, w, h)
        for i in range(w):
            a[h - 1, i] = a[0, i] = C("#8a8a84")
        return a
    return fn


def poster_ppe(a, w, h):  # «Работай в каске»
    circ(a, 8, 9, 4, "#e0b51e")
    box(a, 4, 9, 12, 10, "#e0b51e")
    for x in range(16, 28, 2):
        box(a, x, 6, x, 12, "#2b2d30")


def poster_shock(a, w, h):  # «Не влезай — убьёт»
    box(a, 4, 4, 28, 28, "#ffffff")
    line(a, 18, 6, 12, 16, "#c0392b")
    line(a, 12, 16, 20, 16, "#c0392b")
    line(a, 20, 16, 14, 26, "#c0392b")
    circ(a, 16, 16, 11, "#1d1d1f", 10)


def clock_face():
    a = dial_round32(face="#f8f6ee", bezel="#2b2d30", bg="#2b2d30", face_r=13, bez_r=15)
    for ang in range(0, 360, 30):
        x = 15.5 + 11 * math.cos(math.radians(ang))
        y = 15.5 + 11 * math.sin(math.radians(ang))
        px(a, int(round(x)), int(round(y)), "#1d1d1f")
    return a


def sign(bg, fg, kind):
    def fn():
        a = canvas(bg, noise=1)
        frame(a, 1.1, 0.8)
        if kind == "number":
            box(a, 4, 5, 6, 10, fg)
            box(a, 9, 5, 11, 10, fg)
        elif kind == "arrow":
            box(a, 3, 7, 10, 8, fg)
            for i in range(4):
                box(a, 10 + i, 4 + i, 10 + i, 11 - i, fg)
        else:  # «Выход»: бегущий человек
            circ(a, 6, 4, 1.5, fg)
            line(a, 6, 6, 5, 10, fg)
            line(a, 5, 10, 3, 13, fg)
            line(a, 5, 10, 8, 13, fg)
            line(a, 6, 7, 9, 8, fg)
            box(a, 11, 3, 13, 13, fg)
        return a
    return fn


def blast_door():
    a = canvas("#6a6e64", noise=4)
    frame(a, 1.2, 0.65)
    for y in (3, 12):
        for x in range(1, 15):
            shade(a, y, x, 0.8)
    for y, x in ((2, 2), (2, 13), (13, 2), (13, 13), (7, 2), (7, 13)):
        rivet(a, y, x)
    return a


TEXTURES.update({
    D("tile_white"): tiles("#eeeeea", "#b8b8b2"), D("tile_blue"): tiles("#8ab0cc", "#e8e8e4"),
    D("tile_green"): tiles("#8ab89a", "#e8e8e4"),
    D("porcelain_tile"): speckle("#9a9a96", "#7a7a76"), D("porcelain_tile_beige"): speckle("#c8b89a", "#a8987a"),
    D("linoleum_control"): painted("#8a8e88"), D("linoleum_control_beige"): painted("#c4b494"),
    D("linoleum_control_parquet"): parquet,
    D("raised_floor"): raised(True), D("raised_floor_solid"): raised(False),
    D("ceiling_tile"): ceiling, D("ceiling_light_panel"): light_panel,
    D("painted_concrete_two_tone"): two_tone("#4a7a5a"), D("painted_concrete_two_tone_blue"): two_tone("#3a6a9a"),
    D("painted_concrete_two_tone_beige"): two_tone("#c8b48a"),
    D("painted_concrete"): painted("#9a9a96"), D("painted_concrete_yellow"): painted("#d8b432"),
    D("painted_concrete_red"): painted("#a83228"),
    D("glass_block"): glass_block(False), D("glass_block_frosted"): glass_block(True),
    **{D(f"corrugated_steel{'' if c == 'grey' else '_' + c}"): corrugated(h) for c, h in (
        ("grey", "#8a8e92"), ("blue", "#3a5f8f"), ("green", "#4a7a52"), ("red", "#8a3a2e"), ("white", "#dcdcd6"))},
    D("sandwich_panel"): sandwich("#ecece6"), D("sandwich_panel_grey"): sandwich("#9a9ea2"),
    D("sandwich_panel_blue"): sandwich("#4a6a9a"),
    D("asphalt"): asphalt, D("asphalt_marking"): marking("#f0f0ea"),
    D("asphalt_marking_yellow"): marking("#e0b51e"), D("asphalt_marking_arrow"): marking("#f0f0ea", True),
    D("curb"): curb,
    D("safety_poster"): poster(poster_ppe), D("safety_poster_large"): poster(poster_shock, 32, 32),
    D("wall_clock_face"): clock_face,
    D("room_sign"): sign("#f1eee4", "#1d4f91", "number"), D("room_sign_evac"): sign("#2b8a3e", "#f1f1ea", "arrow"),
    D("industrial_light_exit"): sign("#2b8a3e", "#f1f1ea", "exit"),
    D("tritium_lamp"): sign("#7af08a", "#1d4a2a", "exit"),
    D("blast_door_decor"): blast_door,
})
