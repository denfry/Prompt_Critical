package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import org.junit.jupiter.api.Test;

/** Накипь в жаротрубном котле I0 (07 §7.9.3, R31 {@code HX_FOULING}): перегрев потолка, уходящие газы, КПД. */
class FireTubeBoilerScaleTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final double DT = 0.05, P = 1.2e6, TSAT = W.saturationP(P).tSat();
    private static final FuelSpec COAL_G = CoalAssay.typical(0.37, 0.213, 0.06, 0.016, 10).toFuel();
    private static final FireTubeBoilerSpec SPEC = FireTubeBoilerSpec.defaults();

    private static FireTubeBoiler steady(double scaleM, BoilerOutputs out) {
        FireTubeBoiler b = new FireTubeBoiler(SPEC, COAL_G, TSAT);
        b.setScale(scaleM);
        b.chargeFuel(0.3 * SPEC.bedBurnoutTimeS());
        assertThat(b.ignite()).isTrue();
        BoilerInputs in = new BoilerInputs();
        in.waterTempK = TSAT;
        in.waterPressurePa = P;
        in.waterLevelM = SPEC.normalLevelM();
        in.fuelFeedKgS = 0.3;
        for (int k = 0; k < 20 * 1200; k++) b.stepFast(DT, in, out);
        return b;
    }

    /** 1 мм накипи: потолок горячее на q·δ/λ_н, уходящие газы горячее, КПД ниже; пробка от накипи не греется. */
    @Test
    void scaleRaisesCrownAndStackTemperature() {
        BoilerOutputs clean = new BoilerOutputs(), scaled = new BoilerOutputs();
        steady(0, clean);
        steady(1e-3, scaled);
        double q = scaled.furnaceHeatW / SPEC.furnaceWallAreaM2();
        assertThat(scaled.crownTempK - clean.crownTempK).isCloseTo(q * 1e-3 / BoilerScale.CONDUCTIVITY, within(0.1 * q * 1e-3));
        assertThat(scaled.fluegasExitK).isGreaterThan(clean.fluegasExitK + 3);
        assertThat(scaled.efficiency).isLessThan(clean.efficiency - 0.003);
        assertThat(scaled.plugTempK).isCloseTo(clean.plugTempK, within(2.0));
        assertThat(scaled.crownTempK).isLessThan(FireTubeBoiler.CROWN_FAILURE_K);
    }

    /** Рост — по жёсткости и тепловому потоку жаровых труб; очистка снимает; толщина сохраняется. */
    @Test
    void scaleGrowsWithHardnessAndIsRemoved() {
        BoilerOutputs out = new BoilerOutputs();
        FireTubeBoiler b = steady(0, out);
        double q = out.furnaceHeatW / SPEC.furnaceWallAreaM2();
        for (int d = 0; d < 5; d++) b.stepSlow(86_400, 5);
        assertThat(b.scaleM()).isCloseTo(0.5e-3 * q / BoilerScale.REF_FLUX, within(1e-9));
        FireTubeBoiler soft = steady(0, out);
        for (int d = 0; d < 5; d++) soft.stepSlow(86_400, 0.01);
        assertThat(soft.scaleM()).isLessThan(b.scaleM() / 400);

        TreeStateWriter w = new TreeStateWriter();
        b.save(w);
        FireTubeBoiler copy = new FireTubeBoiler(SPEC, COAL_G, TSAT);
        copy.load(new TreeStateReader(w.root()));
        assertThat(copy.scaleM()).isEqualTo(b.scaleM());
        b.removeScale(1);
        assertThat(b.scaleM()).isZero();
    }

    /** Котёл без BMS: «горит» — RUNNING. */
    @Test
    void bmsStateOfFireTubeBoiler() {
        BoilerOutputs out = new BoilerOutputs();
        FireTubeBoiler b = steady(0, out);
        assertThat(b.bms()).isEqualTo(BmsState.RUNNING);
        b.extinguish();
        assertThat(b.bms()).isEqualTo(BmsState.OFF);
    }
}
