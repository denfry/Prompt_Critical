"""Эталонные теплоёмкости идеальных газов для валидации sim.thermo.IdealGasMixture (01 §1.3.10).

Источник — независимые от NASA-полиномов идеально-газовые части уравнений
состояния CoolProp (HEOS): c_p⁰(T) и Δh⁰ = ∫c_p⁰ dT от 298,15 К (Симпсон).
Верхняя граница по температуре — предел уравнения состояния CoolProp.
Запуск: python pc-sim/tools/ideal_gas_reference.py
Результат: pc-sim/src/test/resources/validation/ideal_gas_reference.csv
"""
import csv
import pathlib

import CoolProp.CoolProp as CP

OUT = pathlib.Path(__file__).resolve().parents[1] / "src/test/resources/validation/ideal_gas_reference.csv"
T_REF = 298.15
FLUIDS = {
    "N2": "Nitrogen",
    "O2": "Oxygen",
    "H2": "Hydrogen",
    "H2O_VAPOR": "Water",
    "AR": "Argon",
    "CO2": "CarbonDioxide",
    "HE": "Helium",
}


def cp0(fluid, t):
    # c_p⁰ зависит только от T; малая плотность — чтобы состояние было газом
    return CP.PropsSI("Cp0molar", "T", t, "Dmolar", 1e-3, fluid)


def dh0(fluid, t, n=400):
    a, b = T_REF, t
    step = (b - a) / n
    acc = cp0(fluid, a) + cp0(fluid, b)
    for i in range(1, n):
        acc += (4 if i % 2 else 2) * cp0(fluid, a + i * step)
    return acc * step / 3


def main():
    rows = []
    for species, fluid in FLUIDS.items():
        t_max = min(CP.PropsSI("Tmax", fluid), 2000.0)
        t = 250.0
        while t <= t_max + 1e-9:
            rows.append([species, t, cp0(fluid, t), dh0(fluid, t)])
            t += 50.0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["species", "t", "cp", "dh"])
        for r in rows:
            w.writerow([r[0]] + [repr(float(v)) for v in r[1:]])
    print(f"{len(rows)} rows -> {OUT}")


if __name__ == "__main__":
    main()
