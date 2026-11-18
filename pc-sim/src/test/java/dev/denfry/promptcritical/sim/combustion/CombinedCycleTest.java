package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import org.junit.jupiter.api.Test;

/** Котёл-утилизатор по пинч-точке и ПГУ одного давления против GDD 07 §7.10 и Kehlhofer (2009). */
class CombinedCycleTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final double FEED_K = 377.15, CONDENSER_PA = 6.6e3;

    /** Брайтон: первое поколение совпадает с {@link GasTurbine}; энергетическая ГТУ I3 — пример T7.13 GDD. */
    @Test
    void braytonPointsMatchTheGasTurbineAndTheGddExample() {
        BraytonCycle g1 = BraytonCycle.FIRST_GENERATION;
        assertThat(g1.efficiency(GasTurbine.ISO_K)).isCloseTo(GasTurbine.efficiency(GasTurbine.ISO_K), within(1e-12));
        assertThat(g1.exhaustK()).isCloseTo(GasTurbine.ratedExhaustK(), within(1e-9));
        BraytonCycle i3 = BraytonCycle.HEAVY_DUTY_I3;
        assertThat(i3.compressorOutK(288)).isCloseTo(634, within(1.0));
        assertThat(i3.exhaustK()).isCloseTo(811, within(1.0));
        assertThat(i3.specificWork(288) / i3.lossFactor()).isCloseTo(311e3, within(2e3));
        assertThat(i3.efficiency(288.15)).isBetween(0.30, 0.35);
    }

    /** Пример GDD: 80 кг/с выхлопа при 538 °C, пар 4 МПа: баланс энергии по газу и по воде, пинч 10 К, уход 150–220 °C. */
    @Test
    void hrsgClosesTheEnergyBalanceAtThePinch() {
        HeatRecoverySteamGenerator.Result r = HeatRecoverySteamGenerator.solve(W, 80, 811.15, 4e6, FEED_K, true);
        double c = 80 * HeatRecoverySteamGenerator.CP_GAS;
        assertThat(r.dutyW()).isCloseTo(c * (811.15 - r.stackK()), within(1e-6 * r.dutyW()));
        double hSteam = W.enthalpyPT(4e6, r.steamK()), hFeed = W.enthalpyPT(4e6, FEED_K);
        assertThat(r.dutyW()).isCloseTo(r.steamKgS() * (hSteam - hFeed), within(1e-6 * r.dutyW()));
        assertThat(r.superheaterW() + r.evaporatorW() + r.economizerW()).isCloseTo(r.dutyW(), within(1e-6 * r.dutyW()));
        assertThat(r.coldEndPinch()).isFalse();
        assertThat(r.pinchGasK() - r.saturationK()).isCloseTo(HeatRecoverySteamGenerator.PINCH_K, within(1e-9));
        assertThat(r.saturationK() - r.economizerOutK()).isCloseTo(HeatRecoverySteamGenerator.APPROACH_K, within(0.2));
        assertThat(r.stackK() - 273.15).isBetween(150.0, 220.0);
        assertThat(r.steamK()).isCloseTo(811.15 - HeatRecoverySteamGenerator.SUPERHEAT_APPROACH_K, within(1e-9));
        // GDD оценивает 36,8 МВт при уходе 120 °C; пинч не пускает газ так низко при 4 МПа
        assertThat(r.dutyW()).isBetween(28e6, 36.8e6);
    }

    /** Газ холоднее насыщения + ΔT_pp пара не даёт; уходящий газ никогда не холоднее питательной воды + ΔT_pp. */
    @Test
    void noSteamBelowThePinchAndTheStackStaysAboveTheFeed() {
        assertThat(HeatRecoverySteamGenerator.solve(W, 10, 400, 4e6, FEED_K, true).steamKgS()).isZero();
        for (double p : new double[] {2e5, 1.5e6, 4e6, 8e6}) {
            HeatRecoverySteamGenerator.Result r = HeatRecoverySteamGenerator.solve(W, 10, 700, p, 313.15, false);
            assertThat(r.steamKgS()).isPositive();
            assertThat(r.stackK()).isGreaterThanOrEqualTo(313.15 + HeatRecoverySteamGenerator.PINCH_K - 1e-9);
            assertThat(r.stackK()).isLessThan(r.pinchGasK());
        }
    }

    /**
     * КПД: ГТУ I3 ≈ 33 %, ПГУ одного давления на ней ≈ 50 % (GDD: ≈ 47 % при η_ПТУ 0,30; Kehlhofer: 45–50 %);
     * ГТУ первого поколения (π = 7, 850 °C) с котлом даёт ≈ 43 %.
     */
    @Test
    void combinedCycleEfficiencyBands() {
        CombinedCycle.DesignPoint i3 = CombinedCycle.designPoint(W, BraytonCycle.HEAVY_DUTY_I3, 25e6, 288.15, 6e6, FEED_K, CONDENSER_PA);
        assertThat(i3.gasTurbineEfficiency()).isBetween(0.30, 0.35);
        assertThat(i3.efficiency()).isBetween(0.47, 0.53);
        assertThat(i3.exhaustKgS()).isBetween(75.0, 95.0);
        assertThat(i3.steamTurbineW() / i3.gasTurbineW()).isBetween(0.38, 0.52);
        CombinedCycle.DesignPoint g1 = CombinedCycle.designPoint(W, BraytonCycle.FIRST_GENERATION, 3e6, 288.15, 1.5e6, FEED_K, CONDENSER_PA);
        assertThat(g1.gasTurbineEfficiency()).isBetween(0.26, 0.30);
        assertThat(g1.efficiency()).isBetween(0.39, 0.46);
    }

    /** Расход выхлопа ГТУ: без огня — ноль, при номинале — расход воздуха 3 МВт / 190 кДж/кг ≈ 16 кг/с. */
    @Test
    void gasTurbineExhaustFlow() {
        GasTurbine t = new GasTurbine(3e6, 2 * Math.PI * 50);
        assertThat(t.exhaustKgS(GasTurbine.ISO_K, GasTurbine.ISO_PA)).isZero();
        assertThat(t.ratedAirKgS()).isCloseTo(3e6 / GasTurbine.specificWork(GasTurbine.ISO_K), within(1e-9));
        assertThat(t.ratedAirKgS()).isBetween(14.0, 18.0);
        t.setRun(true);
        for (int s = 0; s < 1200; s++) t.step(0.05, 0, GasTurbine.ISO_K, GasTurbine.ISO_PA, true);
        assertThat(t.lit()).isTrue();
        assertThat(t.exhaustKgS(GasTurbine.ISO_K, GasTurbine.ISO_PA)).isBetween(0.95 * t.ratedAirKgS(), 1.1 * t.ratedAirKgS());
        assertThat(t.exhaustKgS(308.15, GasTurbine.ISO_PA)).isLessThan(t.exhaustKgS(GasTurbine.ISO_K, GasTurbine.ISO_PA));
    }
}
