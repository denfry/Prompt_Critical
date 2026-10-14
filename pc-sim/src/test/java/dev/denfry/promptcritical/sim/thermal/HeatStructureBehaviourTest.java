package dev.denfry.promptcritical.sim.thermal;

import static dev.denfry.promptcritical.sim.thermal.HeatTransferCorrelations.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.hydraulics.FluidNetwork;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;
import dev.denfry.promptcritical.sim.hydraulics.FluidNodeSpec;
import dev.denfry.promptcritical.sim.hydraulics.HydraulicsConfig;
import dev.denfry.promptcritical.sim.hydraulics.StateCarryOver;
import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterPackage;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Поведение тепловых структур: баланс энергии со сетью, плавление, зазор, гистерезис DNB. */
class HeatStructureBehaviourTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final ThermalConfig CFG = ThermalConfig.defaults();

    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.abs(e);
    }

    @Test
    void wallBetweenClosedVesselsConservesEnergyWithSubsteps() {
        double p = 15.5e6;
        FluidNetwork net = FluidNetwork.create(WaterPackage.of(CriticalFlowTable.Model.HEM), HydraulicsConfig.defaults());
        net.rebuild(List.of(FluidNodeSpec.hem(1, 0.02, 0, 1), FluidNodeSpec.hem(2, 0.02, 0, 1)), List.of(),
                new StateCarryOver() {
                    @Override
                    public double initialPressure(long id) {
                        return p;
                    }

                    @Override
                    public double initialEnthalpy(long id) {
                        return W.enthalpyPT(p, id == 1 ? 573.15 : 473.15);
                    }
                });
        FluidNode hot = net.node(1), cold = net.node(2);
        // hA = 2,5 МВт/К на узел с C ≈ 8·10⁴ Дж/К: явная связь без подшагов разошлась бы
        Wall w = Wall.slab(1, 5e-3, 50, 3, WallMaterial.STAINLESS_STEEL,
                ConvectiveSurface.builder(hot, 0.01, 1).fixedCoefficient(5e4).build(),
                ConvectiveSurface.builder(cold, 0.01, 1).fixedCoefficient(5e4).build(), CFG);
        w.setUniformTemperature(523.15);
        double e0 = hot.internalEnergy() + cold.internalEnergy() + w.storedEnergy();
        double exchanged = 0;
        for (int k = 0; k < 400; k++) {
            w.step(0.05);
            net.stepFast(0.05);
            exchanged += Math.abs(w.heatRate(1)) * 0.05;
            assertThat(hot.temperature()).isGreaterThanOrEqualTo(cold.temperature() - 1e-6);
        }
        double e1 = hot.internalEnergy() + cold.internalEnergy() + w.storedEnergy();
        assertThat(exchanged).isGreaterThan(1e6);
        assertThat(Math.abs(e1 - e0) / exchanged).isLessThan(1e-9);
        assertThat(hot.temperature() - cold.temperature()).isLessThan(1.0);
    }

    @Test
    void fuelMeltsAtMeltingPointAndConservesEnergy() {
        ConvectiveSurface s = ConvectiveSurface.builder(FixedFluid.liquid(15.5e6, 573.15), 10.6e-3, 1)
                .fixedCoefficient(2.5e4).build();
        FuelRod rod = new FuelRod(1, FuelRodSpec.pr300(4), RodRole.AVERAGE, s, CFG);
        rod.setUniformTemperature(900);
        rod.setLinearPower(120e3);
        double e0 = rod.storedEnergy(), out = 0, dt = 0.05;
        boolean sawPartialMelt = false;
        for (int k = 0; k < 2000; k++) {
            rod.step(dt);
            out += rod.heatRate(1) * dt;
            double f = rod.meltFraction();
            if (f > 0 && f < 0.25) {
                sawPartialMelt = true;
                assertThat(rod.radialTemperatures()[0]).isCloseTo(UO2_MELTING_POINT, within(1e-6));
            }
        }
        assertThat(sawPartialMelt).isTrue();
        assertThat(rod.meltFraction()).isGreaterThan(0);
        double generated = 120e3 * rod.spec().activeLength() * dt * 2000;
        assertThat(rel(rod.storedEnergy() - e0 + out, generated)).isLessThan(1e-9);
    }

    @Test
    void burnupClosesGapAndFissionGasRaisesRodPressure() {
        FuelRod fresh = steadyRod(15e3, 0), closed = steadyRod(15e3, 40);
        assertThat(closed.gapWidth()).isLessThan(0.3 * fresh.gapWidth());
        assertThat(closed.gapConductance()).isGreaterThan(1.5 * fresh.gapConductance());
        assertThat(closed.fissionGasRelease()).isZero();

        // при перегрузке Xe/Kr из таблетки портят газ зазора: h_gap падает, хотя зазор закрыт
        FuelRod burned = steadyRod(30e3, 40);
        assertThat(burned.fissionGasRelease()).isBetween(0.01, 0.3);
        assertThat(burned.gapConductance()).isLessThan(closed.gapConductance());
        double[] t = burned.radialTemperatures();
        double tGas = 0.5 * (t[t.length - 2] + t[t.length - 1]);
        double fillOnly = burned.spec().fillGasPressure() * tGas / 293.15;
        assertThat(burned.rodInternalPressure()).isGreaterThan(1.05 * fillOnly);
        assertThat(rel(fresh.rodInternalPressure(),
                fresh.spec().fillGasPressure() * 0.5 * (fresh.radialTemperatures()[4] + fresh.radialTemperatures()[5])
                        / 293.15)).isLessThan(1e-9);
    }

    private static FuelRod steadyRod(double linearPower, double burnup) {
        ConvectiveSurface s = ConvectiveSurface.builder(FixedFluid.liquid(15.5e6, 578.15), 10.6e-3, 1)
                .fixedCoefficient(2.5e4).build();
        FuelRod rod = new FuelRod(1, FuelRodSpec.pr300(4), RodRole.AVERAGE, s, CFG);
        rod.setBurnup(burnup);
        rod.setUniformTemperature(600);
        rod.setLinearPower(linearPower);
        for (int k = 0; k < 400; k++) rod.step(1.0);
        return rod;
    }

    @Test
    void hotRodNominalDnbrHasMargin() {
        ConvectiveSurface s = ConvectiveSurface.builder(FixedFluid.liquid(15.5e6, 578.15), 10.6e-3, 1)
                .bundleFactor(weismanFactor(12.75 / 9.1)).multiplicity(0).build();
        s.setMassFlux(1564);
        FuelRod hot = new FuelRod(1, FuelRodSpec.pr300(4), RodRole.HOT, s, CFG);
        hot.setUniformTemperature(600);
        hot.setLinearPower(20e3);
        for (int k = 0; k < 200; k++) hot.step(1.0);
        assertThat(hot.dnbr()).isBetween(2.5, 8.0);
        assertThat(hot.dnbOccurred()).isFalse();
        assertThat(hot.mode(1)).isEqualTo(HeatTransferMode.SINGLE_PHASE_LIQUID);
        assertThat(hot.heatFlux(1)).isCloseTo(20e3 / (2 * Math.PI * 4.55e-3), within(1.0));
    }

    @Test
    void dnbLatchesUntilWallRewets() {
        ConvectiveSurface s = ConvectiveSurface.builder(FixedFluid.saturatedLiquid(7e6), 0.02, 1).build();
        Wall w = Wall.slab(1, 5e-3, 1, 3, WallMaterial.STAINLESS_STEEL, null, s, CFG);
        w.setUniformTemperature(560);
        double qChf = chfZuber(W.saturationP(7e6));
        w.setVolumetricPower(1.5 * qChf / 5e-3);
        int k = 0;
        while (!w.dnbOccurred() && k++ < 2000) {
            w.step(0.05);
            assertThat(w.mode(1)).isIn(HeatTransferMode.SINGLE_PHASE_LIQUID, HeatTransferMode.NUCLEATE_BOILING);
        }
        assertThat(w.dnbOccurred()).isTrue();
        double tAtChf = w.surfaceTemperature(1);
        for (int j = 0; j < 40; j++) w.step(0.05);
        assertThat(w.mode(1)).isIn(HeatTransferMode.TRANSITION_BOILING, HeatTransferMode.FILM_BOILING);
        assertThat(w.surfaceTemperature(1)).isGreaterThan(tAtChf + 100);

        w.setVolumetricPower(0);
        boolean rewetted = false;
        for (int j = 0; j < 20_000 && !rewetted; j++) {
            w.step(0.05);
            if (w.surfaceTemperature(1) > tAtChf) {
                assertThat(w.mode(1)).isIn(HeatTransferMode.TRANSITION_BOILING, HeatTransferMode.FILM_BOILING);
            }
            rewetted = !s.dnbLatched();
        }
        assertThat(rewetted).isTrue();
        assertThat(w.surfaceTemperature(1)).isLessThanOrEqualTo(tAtChf);
        assertThat(w.dnbOccurred()).isTrue();
    }

    @Test
    void condensationOnColdTubesDegradesWithNoncondensables() {
        SaturationState sat = W.saturationP(5e3);
        FixedFluid steam = new FixedFluid(5e3, sat.hG());
        ConvectiveSurface clean = ConvectiveSurface.builder(steam, 0.016, 1).horizontalTubes().build();
        ConvectiveSurface air = ConvectiveSurface.builder(steam, 0.016, 1).horizontalTubes().build();
        air.setNoncondensableFraction(0.1);
        Wall a = Wall.tube(1, 6.5e-3, 8e-3, 1, 1, 3, WallMaterial.STAINLESS_STEEL, null, clean, CFG);
        Wall b = Wall.tube(2, 6.5e-3, 8e-3, 1, 1, 3, WallMaterial.STAINLESS_STEEL, null, air, CFG);
        a.setUniformTemperature(sat.tSat() - 10);
        b.setUniformTemperature(sat.tSat() - 10);
        a.step(1e-3);
        b.step(1e-3);
        assertThat(a.mode(1)).isEqualTo(HeatTransferMode.CONDENSATION);
        assertThat(a.heatFlux(1)).isNegative();
        double h = -a.heatFlux(1) / (sat.tSat() - a.surfaceTemperature(1));
        assertThat(h).isBetween(5e3, 3e4);
        assertThat(b.heatFlux(1) / a.heatFlux(1)).isCloseTo(1 - 0.7 * Math.pow(0.1, 0.3), within(0.01));
    }

    @Test
    void crisisMultiplierIsDeterministicLognormal() {
        assertThat(FuelRod.sampleCrisisDnbr(42)).isEqualTo(FuelRod.sampleCrisisDnbr(42));
        int n = 20_001;
        double[] ln = new double[n];
        for (int i = 0; i < n; i++) ln[i] = Math.log(FuelRod.sampleCrisisDnbr(0x9E3779B97F4A7C15L * (i + 1)));
        double mean = Arrays.stream(ln).average().orElseThrow();
        double sd = Math.sqrt(Arrays.stream(ln).map(x -> (x - mean) * (x - mean)).sum() / (n - 1));
        Arrays.sort(ln);
        assertThat(Math.exp(ln[n / 2])).isCloseTo(1.0, within(0.01));
        assertThat(sd).isCloseTo(FuelRod.CRISIS_DNBR_SIGMA, within(0.003));
    }

    @Test
    void correlationReferenceValues() {
        assertThat(tMinFilmBoiling(5) - 273.15).isCloseTo(685, within(1.0));
        assertThat(tMinFilmBoiling(22.064)).isCloseTo(CRITICAL_TEMPERATURE, within(1e-9));
        assertThat(rel(cooperPool(6.4e6 / CRITICAL_PRESSURE, WATER_MOLAR_MASS, 150e3), 4.6e4)).isLessThan(0.02);
        double pr = 6.4e6 / CRITICAL_PRESSURE, h = cooperPool(pr, WATER_MOLAR_MASS, 150e3);
        assertThat(rel(cooperPoolFlux(pr, WATER_MOLAR_MASS, 150e3 / h), 150e3)).isLessThan(1e-9);

        assertThat(uo2HeatCapacity(600)).isCloseTo(293, within(1.0));
        assertThat(uo2HeatCapacity(1000)).isCloseTo(311, within(1.0));
        assertThat(uo2HeatCapacity(2000)).isCloseTo(375, within(1.5));
        assertThat(uo2HeatCapacity(3000)).isCloseTo(726, within(2.0));
        assertThat(zircaloyConductivity(600)).isCloseTo(16.5, within(0.1));
        assertThat(zircaloyConductivity(900)).isCloseTo(20.2, within(0.1));
        assertThat(zircaloyConductivity(1200)).isCloseTo(25.0, within(0.1));
        assertThat(integral(1090, 1250) - integral360(1090, 1250)).isCloseTo(44e3, within(1.0));

        assertThat(vitanzaThreshold(20) - 273.15).isCloseTo(1182, within(1.0));
        assertThat(rel(kIntegral(600, 1000), 1684)).isLessThan(0.01);
        // зазор ТВЭЛа ВВЭР-1000-подобного, свежий, q' = 10 кВт/м (§1.4.6)
        assertThat(rel(gapConductance(688, 607, 41e-6, 1e-5, 1, 0, 0), 5.1e3)).isLessThan(0.03);
        assertThat(gapGasConductivity(600, 0.5, 0.5, 0)).isLessThan(gapGasConductivity(600, 1, 0, 0));
    }

    @Test
    void chfHierarchyIsContinuous() {
        SaturationState sat = W.saturationP(15.5e6);
        double xe = -0.1, de = 0.0118, dh = 360e3;
        assertThat(rel(chf(sat, 100, xe, de, dh), chfZuber(sat))).isLessThan(1e-12);
        assertThat(rel(chf(sat, 100.01, xe, de, dh), chf(sat, 100, xe, de, dh))).isLessThan(1e-4);
        assertThat(rel(chf(sat, 1355.99, xe, de, dh), chf(sat, 1356, xe, de, dh))).isLessThan(1e-4);
        assertThat(rel(chf(sat, 3500, xe, de, dh), chfW3(15.5, 3500, xe, de, 360))).isLessThan(1e-12);
        assertThat(rel(chf(sat, 3500, 0.1501, de, dh), chf(sat, 3500, 0.15, de, dh))).isLessThan(1e-3);
        assertThat(chf(sat, 3500, 1.0, de, dh)).isZero();
    }

    private static double kIntegral(double from, double to) {
        int n = 4000;
        double s = 0, d = (to - from) / n;
        for (int i = 0; i < n; i++) s += uo2Conductivity(from + (i + 0.5) * d, 0, 0.05) * d;
        return s;
    }

    private static double integral(double from, double to) {
        int n = 16_000;
        double s = 0, d = (to - from) / n;
        for (int i = 0; i < n; i++) s += zircaloyHeatCapacity(from + (i + 0.5) * d) * d;
        return s;
    }

    private static double integral360(double from, double to) {
        return 360 * (to - from);
    }
}
