"""08: уголь и металлургия — руды (наложение на подложку host), шахта, кокс, домна, сталь, прокат,
цветная металлургия, литейка, мехцех, промгазы, водоподготовка."""
from artkit import *
from tex_mining import host_stone, host_deepslate, ore

X = lambda n: f"block/common/metal_{n}.png"


def host_tuff():
    a = canvas("#6d6e66", noise=6)
    for _ in range(20):
        put(a, rng.randrange(16), rng.randrange(16), rng.choice(("#85867c", "#5a5b54", "#7b6f62")))
    return a


def overlay(*specs):
    """Наложение руды: зёрна на прозрачном фоне (подложку даёт элемент host модели)."""
    return ore(lambda: blank(), *specs)


def coal_overlay(base, band, gloss=None):
    def fn():
        a = blank()
        for y in range(3, 13):
            for x in range(16):
                put(a, y, x, base)
                shade(a, y, x, rng.uniform(0.9, 1.08))
        for y in (5, 9):
            for x in range(16):
                put(a, y, x, band)
        if gloss:
            for _ in range(6):
                put(a, rng.randrange(3, 13), rng.randrange(16), gloss)
        return a
    return fn


G1 = lambda c, n, s, hl=None, dk=None, halo=None: (c, n, s, hl, dk, halo)
OVERLAYS = {
    "magnetite_ore": overlay(G1("#1c1d20", 7, (3, 6), "#6b6f78")),
    "hematite_ore": overlay(G1("#7a2a24", 7, (3, 6), "#b04a52", "#4a1a16")),
    "pyrolusite_ore": overlay(G1("#202022", 6, (2, 5), None, None, ("#3a3a3e", 0.5))),
    "pentlandite_ore": overlay(G1("#b89a4a", 7, (2, 5), "#e0c878", "#7a6430")),
    "chalcopyrite_ore": overlay(G1("#c8a83a", 6, (3, 5), "#f0dc80"), G1("#5a7ab0", 3, (1, 2), "#a080c0")),
    "sperrylite_ore": overlay(G1("#d6d8d4", 3, (1, 2), "#ffffff")),
    "molybdenite_ore": overlay(G1("#7a7e86", 6, (2, 4), "#b4b8c0")),
    "sphalerite_ore": overlay(G1("#5a3a1e", 7, (2, 5), "#a06a32", "#2a1a0e")),
    "chromite_ore": overlay(G1("#18191a", 8, (2, 4), "#4a4c50", None, ("#4f7a52", 0.6))),
    "cassiterite_ore": overlay(G1("#4a2e1c", 6, (2, 4), "#c8a070", "#22140a")),
    "coal_seam": coal_overlay("#1b1b1d", "#2c2c30"),
    "coal_seam_lignite": coal_overlay("#4a3a28", "#5c4a34"),
    "coal_seam_anthracite": coal_overlay("#101012", "#1e1e22", "#8a8e96"),
}


def fireclay():
    a = canvas("#b8b6ae", noise=6)
    for _ in range(12):
        put(a, rng.randrange(16), rng.randrange(16), "#a4a198")
    return a


def vein_quartz():
    a = canvas("#ebe8e2", noise=5)
    for i in range(16):
        shade(a, i, (i * 5) % 16, 0.88)
        shade(a, (i * 7) % 16, i, 1.05)
    return a


def timber():  # шахтный брус: пропитанная сосна, торец с кольцами
    a = canvas("#6a4a2c", noise=6)
    for x in (2, 7, 12):
        for y in range(16):
            shade(a, y, x, 0.8)
    return a


def rust():
    a = canvas("#6b4a36", noise=10)
    for _ in range(18):
        put(a, rng.randrange(16), rng.randrange(16), rng.choice(("#8a5a3a", "#4a3a30")))
    return a


def dinas():  # жёлтый динасовый кирпич
    a = canvas("#cdb57a", noise=5)
    for y in range(0, 16, 4):
        for x in range(16):
            put(a, y, x, "#9a8656")
        off = 0 if (y // 4) % 2 == 0 else 4
        for yy in range(y + 1, min(16, y + 4)):
            put(a, yy, off, "#9a8656")
            put(a, yy, off + 8, "#9a8656")
    return a


def soot():
    a = canvas("#2a2826", noise=6)
    for _ in range(10):
        put(a, rng.randrange(16), rng.randrange(16), "#4a4440")
    return a


def crust():  # корка электролита / шлака
    a = canvas("#cfcac0", noise=12)
    for _ in range(14):
        put(a, rng.randrange(16), rng.randrange(16), "#9a948a")
    return a


def panel_green():  # водоохлаждаемые панели ДСП
    a = canvas("#4a7a52", noise=4)
    for y in range(0, 16, 4):
        for x in range(16):
            shade(a, y, x, 1.3)
            shade(a, (y + 2) % 16, x, 0.75)
    return a


def froth():
    a = canvas("#8a8478", noise=8)
    for _ in range(26):
        y, x = rng.randrange(16), rng.randrange(16)
        put(a, y, x, "#d8d2c4")
    return a


def sand():
    return canvas("#9a7a52", noise=10)


def slurry():  # тяжёлая суспензия
    return canvas("#2e3234", noise=5)


def electrolyte():
    a = canvas("#5ab0d0", noise=5)
    a[:, :, 3] = 200
    return a


def oil():
    return canvas("#5a3a1a", noise=4)


def roller():  # рольганг: ролики поперёк
    a = canvas("#3f4245", noise=3)
    for y in range(1, 16, 4):
        box(a, 0, y, 15, y + 1, "#9aa0a4")
        for x in range(16):
            shade(a, y, x, 1.2)
    return a


def cooling_bed():  # решётка холодильника
    a = blank()
    for x in range(0, 16, 3):
        box(a, x, 0, x, 15, "#5a5e62")
    for y in range(0, 16, 5):
        box(a, 0, y, 15, y, "#6a6e72")
    return a


def mesh_cage():  # сетка клети
    a = blank()
    for i in range(16):
        for j in range(0, 16, 3):
            px(a, j, i, "#8e9498")
            px(a, i, j, "#8e9498")
    return a


def machine_enamel(smooth=False):  # станочная серо-зелёная эмаль
    a = canvas("#7a8a7a", noise=1 if smooth else 3)
    if not smooth:
        frame(a, 1.1, 0.8)
    return a


def grinding():
    a = canvas("#a49a8a", noise=12)
    return a


def frost_steel():  # серебристый кожух с инеем
    a = canvas("#c8ccd0", noise=4)
    for _ in range(30):
        put(a, rng.randrange(16), rng.randrange(16), "#f4f8fa")
    return a


def face32(bg, cells, accent):
    """Щит с подложкой 32×32: ряды табло (подписи и цифры — BER)."""
    def fn():
        n = 32
        a = blank(n)
        a[:, :] = C(bg)
        for i, (x, y) in enumerate(cells):
            box(a, x, y, x + 5, y + 3, "#1d1d1f")
            box(a, x + 1, y + 1, x + 4, y + 2, accent)
        box(a, 3, 24, 28, 28, "#e6e2d2")
        return a
    return fn


def coke_door():  # дверь коксовой печи с броней и смотровым лючком
    a = canvas("#3a3d40", noise=5)
    for y in (2, 13):
        box(a, 1, y, 14, y, "#5a5e62")
    box(a, 6, 5, 9, 7, "#ff9a3a")
    frame(a, 1.2, 0.6)
    return a


TEXTURES.update({
    X("host_stone"): host_stone,
    X("host_deepslate"): host_deepslate,
    X("host_tuff"): host_tuff,
    **{f"block/{k}_overlay.png": v for k, v in OVERLAYS.items()},
    "block/fireclay.png": fireclay,
    "block/vein_quartz.png": vein_quartz,
    X("timber"): timber,
    X("rust"): rust,
    X("orange"): lambda: canvas("#d0661e", noise=4),
    X("dinas"): dinas,
    X("soot"): soot,
    X("crust"): crust,
    X("panel_green"): panel_green,
    X("froth"): froth,
    X("sand"): sand,
    X("slurry"): slurry,
    X("electrolyte"): electrolyte,
    X("oil"): oil,
    X("roller"): roller,
    X("cooling_bed"): cooling_bed,
    X("mesh"): mesh_cage,
    X("machine"): machine_enamel,
    X("machine_smooth"): lambda: machine_enamel(True),
    X("grinding"): grinding,
    X("frost"): frost_steel,
    X("coke_door"): coke_door,
    X("green"): lambda: canvas("#3e6a3e", noise=4),
    "block/mine_dispatch_cabinet_face.png": face32("#cfd1ca", [(4, 4), (13, 4), (22, 4), (4, 12), (13, 12), (22, 12)], "#e0b51e"),
    "block/blast_furnace_controller_face.png": face32("#bfc3bd", [(4, 4), (13, 4), (22, 4), (4, 14), (13, 14), (22, 14)], "#ff9a3a"),
})
