package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.GasSpecies;
import org.junit.jupiter.api.Test;

/** Модель горения (07 §7.8) и формула Гурвича (07 §7.9.2): T7.11, T7.12, T7.23 и проверочные составы §7.8.1. */
class CombustionValidationTest {
    /** Каменный уголь марки Г (07 §7.8.1); r_S = 0, как в примере §7.8.2 (SO₂ 3890 мг/нм³). */
    static final FuelSpec COAL_G = FuelSpec.ofElemental("coal_g", FuelPhase.SOLID,
            0.60, 0.042, 0.07, 0.013, 0.015, 0.20, 0.06, 0, 0.15);
    static final FuelSpec METHANE = gas(FuelGas.CH4, 1.0);

    static FuelSpec gas(Object... componentFraction) {
        double[] x = new double[FuelGas.values().length];
        for (int i = 0; i < componentFraction.length; i += 2)
            x[((FuelGas) componentFraction[i]).ordinal()] = ((Number) componentFraction[i + 1]).doubleValue();
        return FuelSpec.fromGasComposition("test_gas", x);
    }

    static CombustionResult burn(FuelSpec fuel, BurnerKind kind, double alpha, double airK) {
        Combustor c = new Combustor(fuel, BurnerSpec.of(kind, 1.0));
        CombustionResult r = new CombustionResult();
        c.burn(1.0, c.airFor(1.0, alpha), airK, 298.15, r);
        return r;
    }

    /** CO₂ на единицу тепла топлива, г/МДж; углерод в CO считается как CO₂ (окислится в атмосфере). */
    static double co2PerMJ(CombustionResult r, FuelSpec fuel) {
        double co2 = r.co2KgS + r.coKgS * GasSpecies.CO2.molarMass() / GasSpecies.CO.molarMass();
        return co2 * 1e3 / (fuel.lhv() / 1e6);
    }

    @Test
    void t7_11_coalGradeG() {
        assertThat(COAL_G.lhv()).isCloseTo(23.916e6, within(0.005 * 23.92e6));
        assertThat(COAL_G.stoichAirKgPerKg()).isCloseTo(8.11, within(0.005 * 8.11));
        CombustionResult r = burn(COAL_G, BurnerKind.PULVERIZED_COAL, 1.25, 298.15);
        assertThat(r.xO2Dry).isCloseTo(0.0426, within(0.0005));
        assertThat(co2PerMJ(r, COAL_G)).isCloseTo(92.0, within(0.005 * 92.0));
        // остальные числа примера §7.8.2
        assertThat(r.fluegasKgS).isCloseTo(10.94, within(0.02));
        assertThat(r.dryFluegasNm3S).isCloseTo(7.71, within(0.02));
        double xCo2Dry = r.xCO2 / (1 - r.xH2O);
        assertThat(xCo2Dry).isCloseTo(0.145, within(0.001));
        assertThat(r.so2KgS * 1e6 / r.dryFluegasNm3S).isCloseTo(3890, within(20.0));
        assertThat(21 / (21 - 100 * r.xO2Dry)).as("α по O₂").isCloseTo(1.25, within(0.01));
    }

    @Test
    void t7_12_methane() {
        assertThat(METHANE.stoichAirKgPerKg()).isCloseTo(17.29, within(0.005 * 17.29));
        assertThat(METHANE.lhv()).isCloseTo(50.0e6, within(0.005 * 50.0e6));
        CombustionResult r = burn(METHANE, BurnerKind.GAS, 1.0, 298.15);
        assertThat(co2PerMJ(r, METHANE)).isCloseTo(54.9, within(0.005 * 54.9));
        // при α = 1 горелка не дожигает CO (α_кр 1,02); при 1,1 — полное сгорание
        assertThat(r.xCO).isGreaterThan(0.01);
        CombustionResult lean = burn(METHANE, BurnerKind.GAS, 1.1, 298.15);
        assertThat(lean.xCO).isLessThan(1e-3);
        assertThat(co2PerMJ(lean, METHANE)).isCloseTo(54.9, within(0.005 * 54.9));
    }

    @Test
    void referenceFuelsOfSection7_8_1() {
        // Состав М100 из §7.8.1 в сумме даёт 101,1 % (по нему Менделеев — 40,7 МДж/кг). С углеродом, сведённым
        // к 100 %, получается 40,3 — внутри справочных 39,8–40,5, которые там же указаны.
        FuelSpec m100 = FuelSpec.ofElemental("fuel_oil", FuelPhase.LIQUID, 0.844, 0.112, 0.005, 0.003, 0.025, 0.001,
                0.01, 0, 0.4);
        assertThat(m100.lhv()).isBetween(39.8e6, 40.5e6);
        FuelSpec diesel = FuelSpec.ofElemental("diesel", FuelPhase.LIQUID, 0.865, 0.133, 0, 0, 0.001, 0, 0, 0, 0);
        assertThat(diesel.lhv()).isCloseTo(43.0e6, within(0.005 * 43.0e6));

        double[] cokeOven = fractions(FuelGas.H2, 0.58, FuelGas.CH4, 0.26, FuelGas.CO, 0.06, FuelGas.C2H4, 0.03,
                FuelGas.CO2, 0.02, FuelGas.N2, 0.05);
        assertThat(FuelGas.mixtureLhvJPerNm3(cokeOven)).isCloseTo(18.1e6, within(0.05e6));
        double[] blastFurnace = fractions(FuelGas.CO, 0.22, FuelGas.H2, 0.03, FuelGas.CO2, 0.22, FuelGas.N2, 0.53);
        assertThat(FuelGas.mixtureLhvJPerNm3(blastFurnace)).isCloseTo(3.10e6, within(0.01e6));
        double[] mineMethane = fractions(FuelGas.CH4, 0.40, FuelGas.N2, 0.60 * 0.79, FuelGas.O2, 0.60 * 0.21);
        assertThat(FuelGas.mixtureLhvJPerNm3(mineMethane)).isCloseTo(14.3e6, within(0.05e6));
        // газ с балластом горит, его теплота сгорания на кг — из суммы по компонентам
        FuelSpec bfg = FuelSpec.fromGasComposition("bfg", blastFurnace);
        assertThat(bfg.lhv()).isCloseTo(FuelGas.mixtureLhvJPerNm3(blastFurnace) / FuelGas.mixtureDensityKgPerNm3(blastFurnace),
                within(1.0));
        assertThat(burn(bfg, BurnerKind.LOW_BTU_GAS, 1.2, 298.15).heatReleaseW).isCloseTo(bfg.lhv(), within(1e-4 * bfg.lhv()));
    }

    @Test
    void t7_23_gurvich() {
        double ratio = FurnaceHeatTransfer.exitTempRatio(1, 0.6, FurnaceHeatTransfer.M_DEFAULT);
        assertThat(ratio).isCloseTo(0.751, within(0.001));
        assertThat(ratio * 2050).isCloseTo(1540, within(3.0));
    }

    /**
     * 07 §7.8.4: уголь Г, α = 1,2, холодный воздух — ≈ 2050 К; подогрев воздуха до 300 °C — «≈ 250 К». Баланс по c_p
     * NASA даёт +190 К: 2,8 МДж/кг тепла воздуха на теплоёмкость газов ≈ 14,5 кДж/К при 2100 К; оценка GDD грубая.
     */
    @Test
    void adiabaticFlameTemperature() {
        double cold = burn(COAL_G, BurnerKind.PULVERIZED_COAL, 1.2, 298.15).adiabaticFlameK;
        assertThat(cold).isBetween(2000.0, 2120.0);
        double hot = burn(COAL_G, BurnerKind.PULVERIZED_COAL, 1.2, 573.15).adiabaticFlameK;
        assertThat(hot - cold).isBetween(170.0, 270.0);
        // энергетический баланс: физическое тепло газов при T_ad = тепловыделение + тепло воздуха
        CombustionResult r = burn(METHANE, BurnerKind.GAS, 1.1, 298.15);
        assertThat(r.fluegasHeatRateW(r.adiabaticFlameK)).isCloseTo(r.heatReleaseW, within(1e-6 * r.heatReleaseW));
    }

    /** 07 §7.8.4: уголь Г без подавления — 700–900 мг/нм³ (6 % O₂); малотоксичная горелка ×0,5–0,6. */
    @Test
    void noxOfCoalWithoutAbatement() {
        CombustionResult r = burn(COAL_G, BurnerKind.PULVERIZED_COAL, 1.25, 298.15);
        assertThat(r.noxMgPerNm3Dry6).isBetween(700.0, 900.0);
        Combustor lowNox = new Combustor(COAL_G, new BurnerSpec(BurnerKind.PULVERIZED_COAL, 1, 3, 0.55, 1));
        CombustionResult low = new CombustionResult();
        lowNox.burn(1, lowNox.airFor(1, 1.25), 298.15, 298.15, low);
        assertThat(low.noxKgS / r.noxKgS).isCloseTo(0.55, within(1e-9));
    }

    /** 07 §7.8.3: недожог растёт при недостатке воздуха и плохом смешении; при α &lt; 1 — CH₄ в газах. */
    @Test
    void incompleteCombustion() {
        CombustionResult grate13 = burn(COAL_G, BurnerKind.GRATE, 1.3, 298.15);
        CombustionResult grate15 = burn(COAL_G, BurnerKind.GRATE, 1.5, 298.15);
        assertThat(grate13.q3).isBetween(0.005, 0.05);
        assertThat(grate15.q3).isLessThan(1e-4);
        assertThat(grate15.q4).isEqualTo(BurnerKind.GRATE.mechanicalLoss);
        assertThat(grate15.heatReleaseW).isCloseTo(COAL_G.lhv() * (1 - 0.05), within(1e-3 * COAL_G.lhv()));

        Combustor worn = new Combustor(METHANE, new BurnerSpec(BurnerKind.GAS, 1, 3, 1, 3));
        CombustionResult w = new CombustionResult();
        worn.burn(1, worn.airFor(1, 1.08), 298.15, 298.15, w);
        assertThat(w.xCO).isGreaterThan(burn(METHANE, BurnerKind.GAS, 1.08, 298.15).xCO);

        // α = 0,8: весь углерод в CO, кислорода на это хватает; α = 0,6 — часть газа уходит несгоревшей
        CombustionResult rich = burn(METHANE, BurnerKind.GAS, 0.8, 298.15);
        assertThat(rich.xCO).isGreaterThan(0.05);
        assertThat(rich.ch4KgS).isZero();
        assertThat(rich.heatReleaseW).isLessThan(0.8 * METHANE.lhv());
        CombustionResult starved = burn(METHANE, BurnerKind.GAS, 0.6, 298.15);
        assertThat(starved.xO2).isZero();
        assertThat(starved.ch4KgS).isPositive();

        CombustionResult flare = burn(METHANE, BurnerKind.FLARE, 1.5, 298.15);
        assertThat(flare.ch4KgS).isCloseTo(0.02 * (METHANE.c() + METHANE.h()), within(1e-9));
    }

    @Test
    void airOnlyThroughFurnace() {
        Combustor c = new Combustor(COAL_G, BurnerSpec.of(BurnerKind.GRATE, 1));
        CombustionResult r = new CombustionResult();
        c.burn(0, 5, 300, 300, r);
        assertThat(r.heatReleaseW).isZero();
        assertThat(r.xO2Dry).isCloseTo(0.21, within(0.002));
        assertThat(r.fluegasKgS).isEqualTo(5.0);
        assertThat(r.adiabaticFlameK).isEqualTo(300.0);
    }

    /** CO и SO₂ добавлены в {@link GasSpecies} для продуктов сгорания: c_p против JANAF, Дж/(моль·К). */
    @Test
    void productSpeciesHeatCapacity() {
        assertThat(GasSpecies.CO.cp(298.15)).isCloseTo(29.142, within(0.005 * 29.142));
        assertThat(GasSpecies.CO.cp(1000)).isCloseTo(33.183, within(0.005 * 33.183));
        assertThat(GasSpecies.CO.cp(2000)).isCloseTo(36.250, within(0.01 * 36.250));
        assertThat(GasSpecies.SO2.cp(298.15)).isCloseTo(39.842, within(0.005 * 39.842));
        assertThat(GasSpecies.SO2.cp(1000)).isCloseTo(54.484, within(0.005 * 54.484));
        assertThat(GasSpecies.CO.enthalpy(298.15)).isCloseTo(-110.53e3, within(0.2e3));
        assertThat(GasSpecies.SO2.enthalpy(298.15)).isCloseTo(-296.84e3, within(0.5e3));
    }

    private static double[] fractions(Object... componentFraction) {
        double[] x = new double[FuelGas.values().length];
        for (int i = 0; i < componentFraction.length; i += 2)
            x[((FuelGas) componentFraction[i]).ordinal()] = ((Number) componentFraction[i + 1]).doubleValue();
        return x;
    }
}
