package dev.denfry.promptcritical.sim.thermal;

import static dev.denfry.promptcritical.sim.thermal.HeatTransferCorrelations.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.hydraulics.FlowLinkSpec;
import dev.denfry.promptcritical.sim.hydraulics.FluidNetwork;
import dev.denfry.promptcritical.sim.hydraulics.FluidNodeSpec;
import dev.denfry.promptcritical.sim.hydraulics.HydraulicsConfig;
import dev.denfry.promptcritical.sim.hydraulics.LinkKind;
import dev.denfry.promptcritical.sim.hydraulics.StateCarryOver;
import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterPackage;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Тест-кейсы 01 §1.16, группа §1.4 (теплообмен). */
class HeatTransferValidationTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final ThermalConfig CFG = ThermalConfig.defaults();

    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.abs(e);
    }

    @Test
    @Tag("T-1.4-01")
    void dittusBoelterWithWeismanBundleFactor() {
        double c = weismanFactor(12.75 / 9.1);
        assertThat(c).isCloseTo(1.32, within(0.005));
        double h = c * dittusBoelter(1.87e5, 0.857, 0.564, 10.6e-3, true);
        assertThat(rel(h, 2.5e4)).isLessThan(0.03);
    }

    @Test
    @Tag("T-1.4-02")
    void chenReferencePoint() {
        SaturationState sat = W.saturationP(7e6);
        double tw = sat.tSat() + 5;
        double h = chen(1000, 0.1, 10.6e-3, tw, W.saturationPressure(tw), sat);
        assertThat(rel(h, 3.04e4)).isLessThan(0.03);
    }

    @Test
    @Tag("T-1.4-03")
    void fuelRodTemperatureProfile() {
        FuelRod rod = steadyRod(10e3);
        double integral = conductivityIntegral(rod.pelletSurfaceTemperature(), rod.centerlineTemperature());
        assertThat(rod.centerlineTemperature()).isCloseTo(602 + 273.15, within(15.0));
        assertThat(rel(integral, 10e3 / (4 * Math.PI))).isLessThan(0.015);
    }

    private static FuelRod steadyRod(double linearPower) {
        ConvectiveSurface s = ConvectiveSurface.builder(FixedFluid.liquid(15.5e6, 305 + 273.15), 10.6e-3, 1)
                .fixedCoefficient(2.5e4).build();
        FuelRod rod = new FuelRod(1, FuelRodSpec.pr300(4), RodRole.AVERAGE, s, CFG);
        rod.setUniformTemperature(600);
        rod.setLinearPower(linearPower);
        for (int k = 0; k < 400; k++) rod.step(1.0);
        return rod;
    }

    private static double conductivityIntegral(double from, double to) {
        int n = 2000;
        double s = 0, dt = (to - from) / n;
        for (int i = 0; i < n; i++) s += uo2Conductivity(from + (i + 0.5) * dt, 0, 0.05) * dt;
        return s;
    }

    @Test
    @Tag("T-1.4-04")
    void w3ReferencePoint() {
        assertThat(rel(chfW3(15.5, 3500, -0.1, 0.0118, 360), 3236e3)).isLessThan(0.005);
    }

    @Test
    @Tag("T-1.4-05")
    void zuberPoolChf() {
        assertThat(rel(chfZuber(W.saturationP(0.1013e6)), 1.11e6)).isLessThan(0.01);
        assertThat(rel(chfZuber(W.saturationP(7e6)), 3.96e6)).isLessThan(0.01);
        assertThat(rel(chfZuber(W.saturationP(15.5e6)), 2.79e6)).isLessThan(0.01);
    }

    @Test
    @Tag("T-1.4-06")
    void uo2ConductivityFink() {
        assertThat(rel(uo2Conductivity(600, 0, 0.05), 5.14)).isLessThan(0.005);
        assertThat(rel(uo2Conductivity(1000, 0, 0.05), 3.47)).isLessThan(0.005);
        assertThat(rel(uo2Conductivity(2000, 0, 0.05), 2.06)).isLessThan(0.005);
    }

    @Test
    @Tag("T-1.4-07")
    void steamGeneratorMatchesEpsilonNtu() {
        double pP = 15.5e6, pS = 6.4e6, tIn = 320 + 273.15;
        int tubes = 1874, segments = 3;
        double ri = 6.5e-3, ro = 8e-3, length = 1020 / (tubes * 2 * Math.PI * ro);
        double flowArea = tubes * Math.PI * ri * ri, segLength = length / segments;
        FluidNetwork net = FluidNetwork.create(WaterPackage.of(CriticalFlowTable.Model.HEM), HydraulicsConfig.defaults());
        List<FluidNodeSpec> nodes = new ArrayList<>();
        nodes.add(FluidNodeSpec.boundary(1, 0));
        for (int k = 0; k < segments; k++) nodes.add(FluidNodeSpec.hem(2 + k, flowArea * segLength, 0, 1));
        nodes.add(FluidNodeSpec.boundary(9, 0));
        nodes.add(FluidNodeSpec.boundary(20, 0));
        List<FlowLinkSpec> links = new ArrayList<>();
        long[] chain = {1, 2, 3, 4, 9};
        for (int k = 0; k < 4; k++) {
            links.add(new FlowLinkSpec(10 + k, LinkKind.PIPE, chain[k], chain[k + 1], segLength / flowArea, 0,
                    2 * ri, flowArea, 2.8, 0, 0.5, 0.5, null, null));
        }
        double hIn = W.enthalpyPT(pP, tIn);
        net.rebuild(nodes, links, new StateCarryOver() {
            @Override
            public double initialPressure(long id) {
                return id == 1 ? pP + 1e5 : id == 20 ? pS : pP;
            }

            @Override
            public double initialEnthalpy(long id) {
                return id == 20 ? W.saturationP(pS).hF() : hIn;
            }

            @Override
            public double initialMassFlow(long id) {
                return 889;
            }
        });
        List<ConvectiveSurface> inner = new ArrayList<>(), outer = new ArrayList<>();
        for (int k = 0; k < segments; k++) {
            inner.add(ConvectiveSurface.builder(net.node(2 + k), 2 * ri, segLength)
                    .flow(net.link(10 + k), flowArea).inlet(net.node(1 + k)).build());
            outer.add(ConvectiveSurface.builder(net.node(20), 2 * ro, segLength).poolBoiling().build());
        }
        TubeBundle sg = TubeBundle.of(1, ri, ro, length, tubes, 3, WallMaterial.STAINLESS_STEEL, inner, outer, CFG);
        sg.setUniformTemperature(570);
        double dt = 0.05;
        for (int k = 0; k < 1200; k++) {
            sg.step(dt);
            net.stepFast(dt);
        }
        double mdot = net.link(13).massFlow(), tOut = net.node(4).temperature(), hOut = net.node(4).enthalpy();
        double q = sg.heatRate(1), tSat = W.saturationP(pS).tSat();
        double c = mdot * (hIn - hOut) / (tIn - tOut); // ṁ·c_p, эффективная по перепаду энтальпии
        assertThat(mdot).isCloseTo(889, within(10.0));
        assertThat(rel(-sg.heatRate(0), q)).isLessThan(1e-3); // стенка в стационаре
        assertThat(rel(q, mdot * (hIn - hOut))).isLessThan(1e-3);
        assertThat(sg.mode(0)).isEqualTo(HeatTransferMode.SINGLE_PHASE_LIQUID);
        assertThat(sg.mode(1)).isEqualTo(HeatTransferMode.NUCLEATE_BOILING);

        // UA модели по сегментам: тепло на (средняя T сегмента − T_sat)
        double ua = 0, tPrev = tIn;
        for (int k = 0; k < segments; k++) {
            double tk = net.node(2 + k).temperature();
            ua += sg.segment(k).heatRate(1) / (0.5 * (tPrev + tk) - tSat);
            tPrev = tk;
        }
        assertThat(rel(ua, 6.85e6)).isLessThan(0.05);
        double epsNtu = (1 - Math.exp(-ua / c)) * c * (tIn - tSat);
        assertThat(rel(q, epsNtu)).isLessThan(0.02);
        assertThat(rel(q, 149.6e6)).isLessThan(0.02);
    }

    @Test
    @Tag("T-1.4-08")
    void filmBoilingHoldsUntilMinimumFilmTemperature() {
        FixedFluid pool = FixedFluid.saturatedLiquid(0.1e6);
        ConvectiveSurface s = ConvectiveSurface.builder(pool, 0.02, 1.0).build();
        Wall w = Wall.slab(1, 2e-3, 1, 3, WallMaterial.STAINLESS_STEEL, null, s, CFG);
        w.setUniformTemperature(900);
        double tMin = tMinFilmBoiling(0.1);
        assertThat(tMin).isCloseTo(835.3, within(0.5));
        w.step(0.05);
        double hFb = w.heatFlux(1) / (w.surfaceTemperature(1) - W.saturationP(0.1e6).tSat());
        assertThat(hFb).isBetween(200.0, 700.0);
        boolean rewetted = false;
        for (int k = 0; k < 20_000 && !rewetted; k++) {
            w.step(0.05);
            if (w.surfaceTemperature(1) > tMin) assertThat(w.mode(1)).isEqualTo(HeatTransferMode.FILM_BOILING);
            rewetted = w.mode(1) == HeatTransferMode.NUCLEATE_BOILING;
        }
        assertThat(rewetted).isTrue();
        assertThat(w.dnbOccurred()).isTrue();
    }
}
