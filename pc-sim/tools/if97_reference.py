"""Эталонные точки IAPWS-IF97 для валидации sim.thermo (01 §1.16, T-1.3-01).

Источник — независимая реализация IF97 в CoolProp (бэкенд IF97::Water).
Запуск: python pc-sim/tools/if97_reference.py
Результат: pc-sim/src/test/resources/validation/if97_reference.csv
"""
import csv
import math
import pathlib
import random

import CoolProp.CoolProp as CP

FLUID = "IF97::Water"
OUT = pathlib.Path(__file__).resolve().parents[1] / "src/test/resources/validation/if97_reference.csv"
PROPS = ["T", "P", "D", "H", "U", "S", "C", "O", "A", "V", "L"]
HEADER = ["kind", "p", "t", "rho", "h", "u", "s", "cp", "cv", "w", "mu", "k"]


def props(name1, v1, name2, v2):
    return [CP.PropsSI(k, name1, v1, name2, v2, FLUID) for k in PROPS]


def row(kind, vals):
    t, p, d, h, u, s, cp, cv, w, mu, k = vals
    return [kind, p, t, d, h, u, s, cp, cv, w, mu, k]


def psat(t):
    return CP.PropsSI("P", "T", t, "Q", 0, FLUID)


def main():
    rnd = random.Random(19970801)
    rows = []

    # (p, T) по областям 1, 2, 3, 5 — вне окрестности линии насыщения (±0,5 К).
    while len(rows) < 600:
        p = math.exp(rnd.uniform(math.log(1e3), math.log(25e6)))
        t = rnd.uniform(275.0, 1073.15) if rnd.random() < 0.9 else rnd.uniform(1073.15, 2000.0)
        if p < 22.0e6 and abs(t - CP.PropsSI("T", "P", p, "Q", 0, FLUID)) < 0.5:
            continue
        if abs(p - 22.064e6) < 0.5e6 and abs(t - 647.096) < 5.0:
            continue
        try:
            rows.append(row("PT", props("P", p, "T", t)))
        except ValueError:
            pass

    # Насыщение: жидкость (Q=0) и пар (Q=1) по давлению.
    for i in range(80):
        p = math.exp(math.log(1e3) + (math.log(21.5e6) - math.log(1e3)) * i / 79)
        for q in (0, 1):
            vals = [CP.PropsSI(k, "P", p, "Q", q, FLUID) for k in PROPS]
            rows.append(row("SAT" + str(q), vals))

    # (p, h), включая двухфазную область: для неё проверяются только T и ρ.
    n = 0
    while n < 400:
        p = math.exp(rnd.uniform(math.log(1e3), math.log(25e6)))
        h = rnd.uniform(5e3, 4.0e6)
        try:
            t = CP.PropsSI("T", "P", p, "H", h, FLUID)
            d = CP.PropsSI("D", "P", p, "H", h, FLUID)
        except ValueError:
            continue
        if abs(p - 22.064e6) < 0.5e6 and abs(t - 647.096) < 5.0:
            continue
        rows.append(["PH", p, t, d, h] + ["" for _ in range(7)])
        n += 1

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(HEADER)
        for r in rows:
            w.writerow([r[0]] + [v if v == "" else repr(float(v)) for v in r[1:]])
    print(f"{len(rows)} rows -> {OUT}")


if __name__ == "__main__":
    main()
