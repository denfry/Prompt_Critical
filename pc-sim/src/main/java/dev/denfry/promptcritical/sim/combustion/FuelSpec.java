package dev.denfry.promptcritical.sim.combustion;

/**
 * Топливо: элементный состав на рабочую массу, массовые доли (07 §7.8.1, §7.19.2). Неизменяемый, кешируется на тип
 * топлива.
 *
 * @param fuelId           идентификатор топлива (для журнала и выбросов)
 * @param c                C^р
 * @param h                H^р
 * @param o                O^р
 * @param n                N^р
 * @param s                S^р
 * @param ash              A^р — зола
 * @param moisture         W^р — влага
 * @param lhvJPerKg        измеренная низшая теплота сгорания Q_н^р, Дж/кг; NaN — по формуле Менделеева
 * @param phase            агрегатное состояние
 * @param alphaCrit        порог полноты сгорания α_кр; NaN — по типу горелки ({@link BurnerKind#alphaCrit})
 * @param sulfurRetention  r_S — доля серы, связанной в золе (уголь 0,05–0,10, мазут 0)
 * @param fuelNConversion  η_N — доля топливного азота, перешедшая в NO (уголь 0,15–0,25, мазут 0,3–0,5)
 * @param alkaliPpm        щелочные металлы, ppm (коррозия лопаток ГТУ, 07 §7.10.3)
 * @param vanadiumPpm      ванадий, ppm (там же)
 * @param cetane           цетановое число; NaN — не дизельное топливо
 */
public record FuelSpec(String fuelId, double c, double h, double o, double n, double s, double ash, double moisture,
        double lhvJPerKg, FuelPhase phase, double alphaCrit, double sulfurRetention, double fuelNConversion,
        double alkaliPpm, double vanadiumPpm, double cetane) {
    /** Массовая доля O₂ в сухом воздухе. */
    public static final double AIR_O2_MASS_FRACTION = 0.2314;
    /** Кислород на сгорание, кг O₂ на кг элемента: C → CO₂, H → H₂O, S → SO₂, C → CO. */
    static final double O2_PER_C = 2 * FuelGas.M_O / FuelGas.M_C, O2_PER_H = FuelGas.M_O / (2 * FuelGas.M_H),
            O2_PER_S = 2 * FuelGas.M_O / FuelGas.M_S, O2_PER_C_TO_CO = FuelGas.M_O / FuelGas.M_C;

    public FuelSpec {
        if (fuelId == null || fuelId.isEmpty()) throw new IllegalArgumentException("fuelId");
        if (phase == null) throw new IllegalArgumentException("phase");
        double[] parts = {c, h, o, n, s, ash, moisture};
        double sum = 0;
        for (double v : parts) {
            if (!(v >= 0 && v <= 1)) throw new IllegalArgumentException("mass fractions must lie in [0, 1]: " + fuelId);
            sum += v;
        }
        if (Math.abs(sum - 1) > 5e-3) throw new IllegalArgumentException("mass fractions sum to " + sum + ": " + fuelId);
        if (!Double.isNaN(lhvJPerKg) && !(lhvJPerKg > 0)) throw new IllegalArgumentException("lhvJPerKg: " + lhvJPerKg);
        if (!Double.isNaN(alphaCrit) && !(alphaCrit > 0)) throw new IllegalArgumentException("alphaCrit: " + alphaCrit);
        if (!(sulfurRetention >= 0 && sulfurRetention <= 1)) throw new IllegalArgumentException("sulfurRetention");
        if (!(fuelNConversion >= 0 && fuelNConversion <= 1)) throw new IllegalArgumentException("fuelNConversion");
    }

    /** Твёрдое или жидкое топливо без примесей для ГТУ; теплота сгорания — по Менделееву. */
    public static FuelSpec ofElemental(String fuelId, FuelPhase phase, double c, double h, double o, double n, double s,
            double ash, double moisture, double sulfurRetention, double fuelNConversion) {
        return new FuelSpec(fuelId, c, h, o, n, s, ash, moisture, Double.NaN, phase, Double.NaN, sulfurRetention,
                fuelNConversion, 0, 0, Double.NaN);
    }

    /**
     * Низшая теплота сгорания на рабочую массу Q_н^р, Дж/кг. Без измерения — формула Д. И. Менделеева:
     * Q = 339 C + 1030 H − 108,9 (O − S) − 25,1 W, кДж/кг при составе в процентах.
     */
    public double lhv() {
        if (!Double.isNaN(lhvJPerKg)) return lhvJPerKg;
        return 33.9e6 * c + 103.0e6 * h - 10.89e6 * (o - s) - 2.51e6 * moisture;
    }

    /** Теоретически необходимый кислород O₂,st, кг/кг топлива (07 §7.8.2). */
    public double stoichOxygenKgPerKg() {
        return O2_PER_C * c + O2_PER_H * h + O2_PER_S * s - o;
    }

    /** Теоретически необходимый воздух L₀ = O₂,st / 0,2314, кг/кг топлива (07 §7.8.2). */
    public double stoichAirKgPerKg() {
        return stoichOxygenKgPerKg() / AIR_O2_MASS_FRACTION;
    }

    /**
     * Газовое топливо по объёмному составу (07 §7.8.1). Состав пересчитывается в элементный, водяной пар
     * относится к влаге W; теплота сгорания — сумма по компонентам ({@link FuelGas}), а не формула Менделеева: для
     * метана Менделеев даёт 51,3 МДж/кг вместо 50,0.
     *
     * @param molFractions объёмные (мольные) доли в порядке {@link FuelGas}; нормируются на сумму
     */
    public static FuelSpec fromGasComposition(String id, double[] molFractions) {
        FuelGas[] all = FuelGas.values();
        if (molFractions.length != all.length) throw new IllegalArgumentException("expected " + all.length + " fractions");
        double mc = 0, mh = 0, mo = 0, mn = 0, ms = 0, mw = 0;
        for (int i = 0; i < all.length; i++) {
            double x = molFractions[i];
            if (!(x >= 0)) throw new IllegalArgumentException("negative fraction of " + all[i]);
            FuelGas g = all[i];
            if (g == FuelGas.H2O) {
                mw += x * g.molarMass;
                continue;
            }
            mc += x * g.c * FuelGas.M_C;
            mh += x * g.h * FuelGas.M_H;
            mo += x * g.o * FuelGas.M_O;
            mn += x * g.n * FuelGas.M_N;
            ms += x * g.s * FuelGas.M_S;
        }
        double m = mc + mh + mo + mn + ms + mw;
        if (!(m > 0)) throw new IllegalArgumentException("empty gas composition");
        double lhv = FuelGas.mixtureLhvJPerNm3(molFractions) / FuelGas.mixtureDensityKgPerNm3(molFractions);
        return new FuelSpec(id, mc / m, mh / m, mo / m, mn / m, ms / m, 0, mw / m, lhv > 0 ? lhv : Double.NaN,
                FuelPhase.GAS, Double.NaN, 0, 0, 0, 0, Double.NaN);
    }
}
