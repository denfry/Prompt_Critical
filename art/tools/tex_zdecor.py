"""11 §11.6.2: декор I1 — таблички помещений электростанции и цеха. Отдельный модуль в конце порядка
сборки, чтобы новые текстуры не сдвигали шум уже готовых PNG."""
from artkit import *

D = lambda n: f"block/decor/{n}.png"


def room_sign_i1(bg, fg, kind):
    def fn():
        a = canvas(bg, noise=1)
        frame(a, 1.1, 0.8)
        if kind == "electrical":  # молния
            for x, y0, y1 in ((9, 2, 4), (8, 4, 6), (7, 5, 7), (6, 7, 8), (7, 8, 8), (8, 8, 9), (7, 9, 11), (6, 11, 13)):
                rect(a, x, y0, x, y1, fg)
            rect(a, 5, 7, 9, 8, fg)
        elif kind == "pump":  # корпус насоса с патрубками
            circ(a, 7, 8, 3.2, fg)
            circ(a, 7, 8, 1.2, bg)
            rect(a, 6, 2, 8, 5, fg)
            rect(a, 10, 7, 13, 9, fg)
            rect(a, 3, 12, 11, 13, fg)
        else:  # щит управления: приборы и ключи
            rect(a, 3, 3, 12, 12, fg)
            for x in (5, 9):
                circ(a, x + 0.5, 5.5, 1.3, bg)
            for x in (5, 8, 11):
                rect(a, x - 1, 9, x - 1, 10, bg)
        return a
    return fn


TEXTURES.update({
    D("room_sign_electrical"): room_sign_i1("#f2c230", "#1a1a1a", "electrical"),
    D("room_sign_pump"): room_sign_i1("#f1eee4", "#1d4f91", "pump"),
    D("room_sign_control"): room_sign_i1("#f1eee4", "#1d4f91", "control"),
})
