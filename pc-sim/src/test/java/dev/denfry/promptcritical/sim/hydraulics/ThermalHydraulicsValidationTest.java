package dev.denfry.promptcritical.sim.hydraulics;

import static dev.denfry.promptcritical.sim.hydraulics.Nets.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable;
import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.VoidFraction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Тест-кейсы 01 §1.16: T-1.3-08 и группа §1.2 (теплогидравлика). */
class ThermalHydraulicsValidationTest {
    private static final double G = VoidFraction.GRAVITY;

    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.abs(e);
    }

    @Test
    @Tag("T-1.3-08")
    void closedVesselConservesMassAndEnergy() {
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.hem(1, 1.0, 0, 1)), List.of(),
                StateCarryOver.uniform(15.5e6, W.enthalpyPT(15.5e6, 573.15)));
        FluidNode v = n.node(1);
        double m0 = v.mass(), u0 = v.internalEnergy();
        for (int k = 0; k < 10_000; k++) n.stepFast(DT);
        assertThat(rel(v.mass(), m0)).isLessThanOrEqualTo(1e-12);
        assertThat(rel(v.internalEnergy(), u0)).isLessThanOrEqualTo(1e-12);
        assertThat(v.pressure()).isCloseTo(15.5e6, within(2e4));
    }

    @Test
    @Tag("T-1.2-01")
    void uTubeOscillationPeriod() {
        double p = 1e5, a = 0.01, height = 4, column = 2.2 + 1.8 + 1.0;
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.separated(1, 0, height, a), FluidNodeSpec.separated(2, 0, height, a)),
                // паровые подушки ног соединены: иначе равновесная конденсация в закрытой
                // подушке греет жидкость и добавляет «паровую пружину» (+6 % жёсткости)
                List.of(pipe(10, 1, 2, column / a, a, 0, 0, 0), pipe(11, 1, 2, 100, a, 0.5, height, height)),
                new Init(p, 0).node(1, p, enthalpyWithLiquidVolume(p, height * a, 2.2 * a))
                        .node(2, p, enthalpyWithLiquidVolume(p, height * a, 1.8 * a)));
        FluidNode leg = n.node(1);
        List<Double> up = new ArrayList<>();
        double prev = leg.collapsedLevel() - 2.0;
        for (int k = 1; k <= 400; k++) {
            n.stepFast(DT);
            double y = leg.collapsedLevel() - 2.0;
            if (prev < 0 && y >= 0) up.add(DT * (k - 1 + prev / (prev - y)));
            prev = y;
        }
        assertThat(up).hasSizeGreaterThanOrEqualTo(4);
        double period = (up.get(up.size() - 1) - up.get(0)) / (up.size() - 1);
        assertThat(rel(period, 2 * Math.PI * Math.sqrt(column / (2 * G)))).isLessThan(0.02);
    }

    @Test
    @Tag("T-1.2-02")
    void contractedChainMatchesPipeByPipeModel() {
        double d = 0.4, p = 15.5e6, temp = 563.15, mdot = 889;
        List<PipeChain.Segment> chain = new ArrayList<>();
        for (int k = 0; k < 60; k++) {
            double zeta = k % 15 == 7 ? PipeChain.ZETA_ELBOW_90 : 0;
            chain.add(PipeChain.Segment.circular(1.0, d, Friction.ROUGHNESS_STEEL, 0, zeta));
        }
        PipeChain.Geometry g = PipeChain.contract(chain);
        double a = area(d);
        assertThat(g.inertia()).isCloseTo(60 / a, within(1e-9));
        assertThat(g.formLossSum()).isCloseTo(4 * PipeChain.ZETA_ELBOW_90, within(1e-12));

        ThermoState s = new ThermoState();
        W.stateFromPressureEnthalpy(p, W.enthalpyPT(p, temp), s);
        double rho = s.density, mu = s.viscosity;
        double perPipe = 0;
        for (PipeChain.Segment seg : chain) {
            double re = mdot * seg.hydraulicDiameter() / (seg.area() * mu);
            double f = Friction.darcy(re, seg.roughness() / seg.hydraulicDiameter());
            perPipe += (f * seg.length() / seg.hydraulicDiameter() + seg.formLoss()) / (seg.area() * seg.area());
        }
        perPipe *= mdot * mdot / (2 * rho);

        // сеть: границы с этим перепадом -> установившийся расход 889 кг/с; связь двух границ
        // запрещена, поэтому цепочка режется пополам узлом с объёмом труб
        FluidNetwork n = network();
        double h = W.enthalpyPT(p, temp), half = perPipe / 2;
        List<PipeChain.Segment> first = chain.subList(0, 30), second = chain.subList(30, 60);
        PipeChain.Geometry g1 = PipeChain.contract(first), g2 = PipeChain.contract(second);
        n.rebuild(List.of(FluidNodeSpec.boundary(1, 0), FluidNodeSpec.hem(3, g1.volume() / 2 + g2.volume() / 2, -0.2, 0.4),
                        FluidNodeSpec.boundary(2, 0)),
                List.of(FlowLinkSpec.of(11, LinkKind.PIPE, 1, 3, g1, 0, null, null),
                        FlowLinkSpec.of(12, LinkKind.PIPE, 3, 2, g2, 0, null, null)),
                new Init(p, h).node(2, p - perPipe, h).node(3, p - half, h).flow(11, mdot).flow(12, mdot));
        for (int k = 0; k < 2400; k++) n.stepFast(DT);
        assertThat(rel(n.link(11).massFlow(), mdot)).isLessThan(0.01);
        assertThat(rel(n.link(12).massFlow(), mdot)).isLessThan(0.01);
        assertThat(rel(n.link(11).pressureDrop() + n.link(12).pressureDrop(), perPipe)).isLessThan(0.01);
    }

    @Test
    @Tag("T-1.2-03")
    void closedPrimaryLoopConservesMassAndEnergyForAnHour() {
        double p = 15.5e6, mdot = 1778, rho = 745, area = 0.5;
        double k = 2 * rho * 0.40e6 / (mdot * mdot); // контур теряет напор ГЦН при номинальном расходе
        double zeta = k * area * area / 4;
        double h300 = W.enthalpyPT(p, 573.15);
        PumpModel.Spec pump = new PumpModel.Spec(157, mdot / rho, 0.40e6 / (rho * G), 1.2e6 / 157, rho, 600,
                HomologousCurve.DEFAULT_ID);
        FluidNetwork n = network();
        n.rebuild(List.of(
                        FluidNodeSpec.hem(1, 6.0, 0, 2),          // активная зона
                        FluidNodeSpec.hem(2, 3.0, 2, 1),          // горячая нитка
                        FluidNodeSpec.hem(3, 5.3, 6, 2),          // ПГ (первый контур)
                        FluidNodeSpec.hem(4, 4.5, 2, 1),          // холодная нитка, всас ГЦН
                        FluidNodeSpec.separated(5, 4, 4, 1.5)),   // КД
                List.of(pipe(11, 1, 2, 10, area, zeta, 2, 2),
                        pipe(12, 2, 3, 20, area, zeta, 2.5, 7),
                        pipe(13, 3, 4, 20, area, zeta, 7, 2.5),
                        link(14, LinkKind.PUMP, 4, 1, 10, area, zeta, 2, 0, pump, null),
                        pipe(15, 2, 5, 200, 0.05, 2, 2.5, 4)),
                new Init(p, h300).node(5, p, enthalpyWithLiquidVolume(p, 6.0, 3.5))
                        .flow(11, mdot).flow(12, mdot).flow(13, mdot).flow(14, mdot).speed(14, 157));
        n.link(14).pump().setDrivingTorque(1.2e6 / 157);
        double m0 = n.totalMass(), u0 = n.totalEnergy(), heatIn = 0, lastWork = 0;
        for (int tick = 0; tick < 72_000; tick++) {
            double time = tick * DT;
            double core = 300e6 * (1 + 0.03 * Math.sin(2 * Math.PI * time / 300));
            double sg = -300e6 - (n.pumpWorkInput() - lastWork) / DT; // ПГ снимает и тепло насоса
            lastWork = n.pumpWorkInput();
            n.node(1).addHeat(core);
            n.node(3).addHeat(sg);
            heatIn += (core + sg) * DT;
            n.stepFast(DT);
        }
        assertThat(n.externalMassInput()).isZero();
        assertThat(rel(n.totalMass(), m0)).isLessThan(1e-9);
        double balance = n.totalEnergy() - u0 - (heatIn + n.pumpWorkInput());
        assertThat(Math.abs(balance) / u0).isLessThan(1e-9);
        // физическая разумность: расход держится, давление в пределах таблиц
        assertThat(n.link(14).massFlow()).isBetween(0.8 * mdot, 1.2 * mdot);
        assertThat(n.node(5).pressure()).isBetween(10e6, 20e6);
    }

    @Test
    @Tag("T-1.2-04")
    void pumpCoastdownHalvesSpeedAtTauP() {
        double p = 15.5e6, rho = 745, mdot = 889, area = 0.2;
        double headPa = 0.40e6, k = 2 * rho * headPa / (mdot * mdot);
        double h = W.enthalpyPT(p, 563.15);
        double tR = 617e3 / 157;
        PumpModel.Spec spec = new PumpModel.Spec(157, mdot / rho, headPa / (rho * G), tR, rho, 300,
                HomologousCurve.DEFAULT_ID);
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.hem(1, 5, 0, 1), FluidNodeSpec.hem(2, 5, 0, 1), FluidNodeSpec.boundary(9, 0.5)),
                List.of(link(1, LinkKind.PUMP, 1, 2, 10, area, 0, 0.5, 0.5, spec, null),
                        pipe(2, 2, 1, 100, area, k * area * area, 0.5, 0.5),
                        pipe(3, 9, 1, 1000, 0.001, 1, 0.5, 0.5)),
                new Init(p, h).flow(1, mdot).flow(2, mdot).speed(1, 157));
        PumpModel pm = n.link(1).pump();
        pm.setDrivingTorque(0);
        for (int tick = 0; tick < 240; tick++) n.stepFast(DT); // 12 с
        assertThat(rel(pm.speed(), 157 / 2.0)).isLessThan(0.05);
        assertThat(n.link(1).massFlow()).isBetween(0.4 * mdot, 0.6 * mdot);
    }

    @Test
    @Tag("T-1.2-05")
    void instantGateClosureRaisesJoukowskyEvent() {
        double p = 15.5e6, d = 0.4, a = area(d), mdot = 889;
        double h = W.enthalpyPT(p, 573.15);
        ThermoState s = new ThermoState();
        W.stateFromPressureEnthalpy(p, h, s);
        double zUp = 0.5, zValve = 0.2, k = (zUp + zValve) / (a * a);
        double dp = k * mdot * mdot / (2 * s.density);
        FluidNetwork n = network();
        List<double[]> events = new ArrayList<>();
        n.setEventSink(new HydraulicEventSink() {
            @Override
            public void waterHammer(long linkId, double deltaPressure, double riseTime) {
                events.add(new double[] {linkId, deltaPressure, riseTime});
            }
        });
        n.rebuild(List.of(FluidNodeSpec.boundary(1, 0), FluidNodeSpec.hem(2, 2, -0.5, 1), FluidNodeSpec.boundary(3, 0)),
                List.of(pipe(1, 1, 2, 50, a, zUp, 0, 0),
                        link(2, LinkKind.VALVE_GATE, 2, 3, 50, a, 0, 0, 0, null, ValveModel.Spec.gate(zValve, 0))),
                new Init(p + 0.5 * dp, h).node(2, p, h).node(3, p - 0.5 * dp, h).flow(1, mdot).flow(2, mdot));
        for (int tick = 0; tick < 100; tick++) n.stepFast(DT);
        double v = n.link(2).velocity();
        assertThat(rel(n.link(2).massFlow(), mdot)).isLessThan(0.01);
        n.link(2).setValvePosition(0);
        n.stepFast(DT);
        assertThat(events).hasSize(1);
        double c = W.soundSpeed(p, h);
        assertThat(rel(events.get(0)[1], s.density * c * v)).isLessThan(0.05);
        assertThat(rel(events.get(0)[1], 6.9e6)).isLessThan(0.05);
        assertThat(n.link(2).regime()).isEqualTo(LinkRegime.CLOSED);
        for (int tick = 0; tick < 200; tick++) {
            n.stepFast(DT);
            assertThat(Double.isFinite(n.node(2).pressure())).isTrue();
            assertThat(Double.isFinite(n.link(1).massFlow())).isTrue();
        }
        assertThat(n.link(2).massFlow()).isZero();
        assertThat(Math.abs(n.link(1).massFlow())).isLessThan(1);
    }

    @Test
    @Tag("T-1.2-06")
    void naturalCirculationFlow() {
        double p = 7e6, temp = 523.15, q = 10e6, dz = 5, a = 0.2, zeta = 5;
        double h = W.enthalpyPT(p, temp);
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.hem(1, 2, 0, 1), FluidNodeSpec.hem(2, 2, dz, 1), FluidNodeSpec.boundary(9, dz + 0.5)),
                List.of(pipe(1, 1, 2, 30, a, zeta, 0.5, dz + 0.5),
                        pipe(2, 2, 1, 30, a, zeta, dz + 0.5, 0.5),
                        pipe(3, 2, 9, 1000, 0.001, 1, dz + 0.5, dz + 0.5)),
                // симметричный контур — неустойчивое равновесие: затравка задаёт направление
                new Init(p, h).flow(1, 10).flow(2, 10));
        double sum = 0;
        int samples = 0;
        for (int tick = 0; tick < 30_000; tick++) {
            n.node(1).addHeat(q);
            n.node(2).addHeat(-q);
            n.stepFast(DT);
            if (tick >= 28_000) {
                sum += n.link(1).massFlow();
                samples++;
            }
        }
        double mdot = sum / samples;
        ThermoState s = new ThermoState();
        W.stateFromPressureEnthalpy(p, h, s);
        double rho = s.density, cp = s.cp;
        double rhoHot = 1 / volumeAt(p, temp + 1), rhoCold = 1 / volumeAt(p, temp - 1);
        double beta = (rhoCold - rhoHot) / (2 * rho);
        double k = 2 * zeta / (a * a);
        double expected = Math.cbrt(2 * rho * rho * G * beta * dz * q / (k * cp));
        assertThat(rel(mdot, expected)).isLessThan(0.15);
        assertThat(n.link(2).massFlow()).isCloseTo(mdot, within(0.02 * mdot));
    }

    private static double volumeAt(double p, double t) {
        ThermoState s = new ThermoState();
        W.stateFromPressureEnthalpy(p, W.enthalpyPT(p, t), s);
        return 1 / s.density;
    }

    @Test
    @Tag("T-1.2-07")
    void vesselBlowdownThroughSmallBreak() {
        double p = 15.5e6, area = 1e-4;
        double h = W.enthalpyPT(p, 573.15);
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.hem(1, 1, 0, 1), FluidNodeSpec.boundary(2, 0.5)),
                List.of(link(1, LinkKind.BREAK, 1, 2, 0.05 / area, area, 0, 0.5, 0.5, null, null)),
                new Init(p, h).node(2, 1e5, W.enthalpyPT(1e5, 300)));
        n.stepFast(DT);
        double expected = area * CriticalFlowTable.of(CriticalFlowTable.Model.HEM).criticalMassFlux(p, h);
        assertThat(n.link(1).regime()).isEqualTo(LinkRegime.CHOKED);
        assertThat(rel(n.link(1).massFlow(), expected)).isLessThan(0.03);
        assertThat(rel(n.link(1).massFlow(), 10.2)).isLessThan(0.03);

        FluidNode v = n.node(1);
        double flashPressure = Double.NaN;
        for (int tick = 0; tick < 400 && Double.isNaN(flashPressure); tick++) {
            assertThat(v.voidFraction()).isZero();
            n.stepFast(DT);
            if (v.voidFraction() > 0) flashPressure = v.pressure();
        }
        // 01 §1.16 ждёт p_sat(300 °C) = 8,59 МПа, но оставшаяся в сосуде вода расширяется
        // изоэнтропически (dU = h dM) и остывает на ≈ 2,5 К: вскипание наступает при
        // s_f(p) = s₀, то есть при 8,22 МПа. Проверяем против этого эталона.
        double s0 = W.entropyPH(p, h), lo = 5e6, hi = 9e6;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (W.saturationP(mid).sF() > s0) hi = mid;
            else lo = mid;
        }
        assertThat(flashPressure).isCloseTo(lo, within(0.1e6));
        assertThat(flashPressure).isLessThan(W.saturationPressure(573.15));
    }
}
