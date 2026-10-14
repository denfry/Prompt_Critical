package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.thermo.GasSpecies;

/**
 * Результат горения за шаг (07 §7.8.5); изменяемый буфер, переиспользуется без аллокаций. Мольные доли — во
 * влажных газах; {@code xO2Dry} — в сухих (то, что показывает газоанализатор, α ≈ 21/(21 − O₂)).
 */
public final class CombustionResult {
    /** Тепловыделение B·Q_н·(1 − q₃ − q₄), Вт. */
    public double heatReleaseW;
    /** Адиабатная температура горения, К. */
    public double adiabaticFlameK;
    /** Коэффициент избытка воздуха α. */
    public double excessAir;
    /** Расход дымовых газов (без золы), кг/с. */
    public double fluegasKgS;
    public double ashKgS, flyAshKgS;
    /** Мольные доли во влажных газах. */
    public double xCO2, xH2O, xN2, xO2, xSO2, xCO;
    /** O₂ в сухих газах, мольная доля. */
    public double xO2Dry;
    /** NOx (как NO₂) в сухих газах, приведённый к 6 % O₂, мг/нм³. */
    public double noxMgPerNm3Dry6;
    /** Расход сухих газов, нм³/с. */
    public double dryFluegasNm3S;
    /** Химический q₃ и механический q₄ недожог, доли тепла топлива. */
    public double q3, q4;
    /** Выбросы в {@code EmissionLedger} (14), кг/с; NOx — в пересчёте на NO₂, CH₄ — несгоревшее газовое топливо. */
    public double co2KgS, so2KgS, noxKgS, coKgS, ch4KgS, pmKgS;

    /** Мольные расходы продуктов, моль/с, по {@link GasSpecies#ordinal()}. */
    final double[] productMolS = new double[GasSpecies.values().length];

    /** Мольный расход продукта, моль/с. */
    public double molarFlow(GasSpecies s) {
        return productMolS[s.ordinal()];
    }

    /**
     * Поток физического тепла газов при температуре {@code tK} относительно 298,15 К, Вт: Σ ṅ_k (h_k(T) − h_k(T₀)).
     * Даёт среднюю суммарную теплоёмкость V̄c для формулы Гурвича и тепло на поверхностях нагрева.
     */
    public double fluegasHeatRateW(double tK) {
        return Combustor.sensibleHeat(productMolS, tK);
    }

    void clear(double airTempK) {
        heatReleaseW = fluegasKgS = ashKgS = flyAshKgS = excessAir = 0;
        xCO2 = xH2O = xN2 = xO2 = xSO2 = xCO = xO2Dry = noxMgPerNm3Dry6 = dryFluegasNm3S = q3 = q4 = 0;
        co2KgS = so2KgS = noxKgS = coKgS = ch4KgS = pmKgS = 0;
        adiabaticFlameK = airTempK;
        java.util.Arrays.fill(productMolS, 0);
    }
}
