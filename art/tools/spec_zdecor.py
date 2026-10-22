"""11 §11.6.2: декор I1 — таблички «Электрощитовая», «Насосная», «ГЩУ» (форма как у room_sign)."""
from artkit import *

D = lambda n: f"block/decor/{n}.png"

for _k in ("electrical", "pump", "control"):
    mk(f"room_sign_{_k}", "decor", [el("plate", [2, 5, 15.5], [14, 11, 16], "s", full=["north"])], {"s": D(f"room_sign_{_k}")})
