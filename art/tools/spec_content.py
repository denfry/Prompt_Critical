"""11: пюпитр с регламентом, клетка с канарейкой."""
from artkit import *

K = lambda n: f"block/common/content_{n}.png"
OWN = {"fold": K("folder"), "bars": K("cage_bars"), "bird": K("bird"), "birdd": K("bird_dark")}


def m(id_, cat, els, kind="block"):
    mk(id_, cat, els, OWN, kind)


# пюпитр с регламентом у рабочего места: стойка, наклонная полка (папка — при has_document)
def lectern(doc):
    els = [el("foot", [4, 0, 4], [12, 1.5, 12], "d"),
           el("post", [7, 1.5, 7], [9, 11, 9], "sm"),
           el("shelf", [2, 11, 3], [14, 12, 13], "p", rot=("x", -22.5, [8, 11.5, 8])),
           el("lip", [2, 10, 2.5], [14, 12, 3.5], "d", rot=("x", -22.5, [8, 11.5, 8])),
           el("plate", [5, 6, 6.8], [11, 9, 7], "pns")]
    if doc:
        els.append(el("folder", [3, 12, 4], [13, 12.5, 12.5], "p", faces={"up": "fold"},
                      rot=("x", -22.5, [8, 11.5, 8])))
    return els


m("document_lectern", "control", lectern(False))
m("document_lectern_document", "control", lectern(True))


# клетка шахтёрской канарейки: латунные прутья, кольцо; птица на жёрдочке — partial (качание по X)
def cage():
    return [el("base", [3, 0, 3], [13, 1.5, 13], "bs"),
            el("bars_n", [3.5, 1.5, 3.5], [12.5, 12, 3.6], "bars"),
            el("bars_s", [3.5, 1.5, 12.4], [12.5, 12, 12.5], "bars"),
            el("bars_w", [3.5, 1.5, 3.5], [3.6, 12, 12.5], "bars"),
            el("bars_e", [12.4, 1.5, 3.5], [12.5, 12, 12.5], "bars"),
            el("roof", [4, 12, 4], [12, 13, 12], "bs"),
            el("ring", [7.5, 13, 7.75], [8.5, 15.5, 8.25], "bs"),
            el("perch", [4, 5, 7.75], [12, 5.5, 8.25], "st")]


m("canary_cage", "world", cage())
m("canary_cage/bird", "world", [
    el("body", [6.5, 5.5, 7], [9.5, 8, 9], "bird"),
    el("head", [8.5, 7.5, 7.25], [10.5, 9.5, 8.75], "bird"),
    el("beak", [10.5, 8, 7.75], [11.25, 8.75, 8.25], "lo"),
    el("tail", [5, 6, 7.5], [6.5, 7, 8.5], "bird"),
], kind="partial")
m("canary_cage/bird_fainted", "world", [
    el("body", [6, 1.5, 7], [9, 3.5, 9], "birdd"),
    el("head", [9, 1.5, 7.25], [11, 3, 8.75], "birdd"),
], kind="partial")
