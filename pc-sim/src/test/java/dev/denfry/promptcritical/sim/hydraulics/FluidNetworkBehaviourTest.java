package dev.denfry.promptcritical.sim.hydraulics;

import static dev.denfry.promptcritical.sim.hydraulics.Nets.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.SaturationState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Поведение гидросети вне таблицы 01 §1.16: арматура, фазовый отбор, сон, пересборка. */
class FluidNetworkBehaviourTest {
    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.abs(e);
    }

    /** Барабан 7 МПа, наполовину с водой, и отбор через штуцер на отметке z к границе 6 МПа. */
    private static FluidNetwork drum(double z) {
        double p = 7e6;
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.separated(1, 0, 2, 1), FluidNodeSpec.boundary(2, z)),
                List.of(pipe(1, 1, 2, 1000, 0.002, 1, z, z)),
                new Init(p, enthalpyWithLiquidVolume(p, 2, 1)).node(2, 6e6, W.enthalpyPT(6e6, 500)));
        return n;
    }

    @Test
    void separatedNodeDrawsSteamFromTopAndWaterFromBottom() {
        SaturationState s = W.saturationP(7e6);
        FluidNetwork top = drum(1.9);
        assertThat(top.node(1).mixtureLevel()).isCloseTo(1.0, within(0.01));
        top.stepFast(DT);
        double hTop = -top.externalEnergyInput() / -top.externalMassInput();
        double carry = HydraulicsConfig.defaults().moistureCarryover();
        assertThat(rel(hTop, s.hG() - carry * (s.hG() - s.hF()))).isLessThan(0.005);

        FluidNetwork bottom = drum(0.1);
        bottom.stepFast(DT);
        double hBottom = bottom.externalEnergyInput() / bottom.externalMassInput();
        assertThat(rel(hBottom, s.hF())).isLessThan(0.005);
        // в установившемся истечении вода уходит быстрее пара: ≈ √(ρ_f/ρ_смеси над уровнем)
        for (int k = 0; k < 10; k++) { // 0,5 с: инерция связи (≈ 0,1 с) уже отработала, барабан ещё не опорожнён
            top.stepFast(DT);
            bottom.stepFast(DT);
        }
        assertThat(bottom.link(1).massFlow()).isGreaterThan(3 * top.link(1).massFlow());
    }

    @Test
    void checkValveBlocksReverseFlow() {
        double p = 1e6, h = W.enthalpyPT(p, 320);
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.boundary(1, 0), FluidNodeSpec.hem(2, 1, -0.5, 1), FluidNodeSpec.boundary(3, 0)),
                List.of(pipe(1, 1, 2, 100, 0.01, 1, 0, 0),
                        link(2, LinkKind.VALVE_CHECK, 2, 3, 100, 0.01, 0, 0, 0, null, ValveModel.Spec.check(2))),
                new Init(p, h).node(3, p + 1e5, h));
        for (int k = 0; k < 400; k++) n.stepFast(DT);
        assertThat(n.link(2).regime()).isEqualTo(LinkRegime.REVERSE_BLOCKED);
        // ζ_rev = 10⁶ (плюс ζ = 1 подводящей трубы): утечка ≈ A·√(2ρΔp/ζ) ≈ 0,14 кг/с
        double rho = n.node(2).density(), leak = 0.01 * Math.sqrt(2 * rho * 1e5 / (ValveModel.CHECK_REVERSE_ZETA + 1));
        assertThat(rel(-n.link(2).massFlow(), leak)).isLessThan(0.05);
        n.node(1).setBoundaryState(p + 3e5, h);
        for (int k = 0; k < 400; k++) n.stepFast(DT);
        assertThat(n.link(2).regime()).isEqualTo(LinkRegime.NORMAL);
        assertThat(n.link(2).massFlow()).isGreaterThan(1);
    }

    @Test
    void reliefValveLiftsAndReseatsWithBlowdown() {
        double p = 15.0e6, set = 16.2e6;
        List<Boolean> lifts = new ArrayList<>();
        FluidNetwork n = network();
        n.setEventSink(new HydraulicEventSink() {
            @Override
            public void reliefLift(long linkId, boolean open) {
                lifts.add(open);
            }
        });
        n.rebuild(List.of(FluidNodeSpec.separated(1, 0, 4, 1.5), FluidNodeSpec.boundary(2, 4)),
                List.of(link(1, LinkKind.VALVE_RELIEF, 1, 2, 100, 0.002, 0, 4, 4, null,
                        ValveModel.Spec.relief(set, 1.0))),
                new Init(p, enthalpyWithLiquidVolume(p, 6, 3.5)).node(2, 0.1e6, W.enthalpyPT(0.1e6, 350)));
        double pMax = 0;
        for (int k = 0; k < 2000; k++) {
            n.node(1).addHeat(k < 1000 ? 5e6 : 0);
            n.stepFast(DT);
            pMax = Math.max(pMax, n.node(1).pressure());
        }
        assertThat(lifts).isNotEmpty();
        assertThat(lifts.get(0)).isTrue();
        assertThat(pMax).isLessThan(set * 1.03);
        assertThat(n.link(1).regime()).isEqualTo(LinkRegime.CLOSED);
        assertThat(n.node(1).pressure()).isBetween(set * (1 - 0.05) * 0.98, set);
    }

    @Test
    void quietComponentSleepsAndWakesOnHeat() {
        double p = 1e6, h = W.enthalpyPT(p, 350);
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.hem(1, 1, 0, 1), FluidNodeSpec.hem(2, 1, 0, 1)),
                List.of(pipe(1, 1, 2, 100, 0.01, 1, 0.5, 0.5)), new Init(p, h));
        for (int k = 0; k < 41; k++) n.stepFast(DT);
        assertThat(n.isAsleep(0)).isTrue();
        double u0 = n.totalEnergy();
        n.node(1).addHeat(500); // ниже порога пробуждения: тепло копится, сеть спит
        n.stepFast(DT);
        assertThat(n.isAsleep(0)).isTrue();
        assertThat(n.totalEnergy() - u0).isCloseTo(500 * DT, within(1e-6));
        n.node(1).addHeat(1e6);
        assertThat(n.isAsleep(0)).isFalse();
        n.stepFast(DT);
        assertThat(n.node(1).temperature()).isGreaterThan(350);
        n.wake(1);
        assertThat(n.isAsleep(0)).isFalse();
    }

    @Test
    void rebuildCarriesStateAndRedistributesMergedNodes() {
        double p = 2e6;
        FluidNetwork n = network();
        n.registerScalar(PassiveScalar.BORON);
        n.rebuild(List.of(new FluidNodeSpec(1, NodeKind.HEM, 1, 0, 1, 1, new long[] {10, 11}),
                        new FluidNodeSpec(2, NodeKind.HEM, 1, 0, 1, 1, new long[] {12, 13}),
                        FluidNodeSpec.hem(5, 1, 0, 1)),
                List.of(pipe(1, 1, 2, 100, 0.01, 1, 0.5, 0.5), pipe(2, 2, 5, 100, 0.01, 1, 0.5, 0.5)),
                new Init(p, W.enthalpyPT(p, 400)).node(2, p, W.enthalpyPT(p, 450)));
        n.node(1).setScalar(PassiveScalar.BORON, 0.5);
        double m = n.node(1).mass() + n.node(2).mass(), u = n.node(1).internalEnergy() + n.node(2).internalEnergy();
        double m5 = n.node(5).mass();
        // узлы 1 и 2 сливаются в 3 (те же блоки), узел 5 сохраняется по id
        n.rebuild(List.of(new FluidNodeSpec(3, NodeKind.HEM, 2, 0, 1, 2, new long[] {10, 11, 12, 13}),
                        FluidNodeSpec.hem(5, 1, 0, 1)),
                List.of(pipe(2, 3, 5, 100, 0.01, 1, 0.5, 0.5)), new Init(p, 4e5));
        assertThat(n.node(3).mass()).isCloseTo(m, within(1e-9 * m));
        assertThat(n.node(3).internalEnergy()).isCloseTo(u, within(1e-9 * u));
        assertThat(n.node(3).scalar(PassiveScalar.BORON)).isCloseTo(0.5, within(1e-12));
        assertThat(n.node(5).mass()).isEqualTo(m5);
    }

    @Test
    void boronIsTransportedAndConserved() {
        double p = 15.5e6, rho = 745, mdot = 500, area = 0.1;
        double h = W.enthalpyPT(p, 563.15);
        PumpModel.Spec spec = new PumpModel.Spec(157, mdot / rho, 0.2e6 / (rho * 9.80665), 2000, rho, 50,
                HomologousCurve.DEFAULT_ID);
        FluidNetwork n = network();
        n.registerScalar(PassiveScalar.BORON);
        n.rebuild(List.of(FluidNodeSpec.hem(1, 2, 0, 1), FluidNodeSpec.hem(2, 2, 0, 1), FluidNodeSpec.hem(3, 2, 0, 1)),
                List.of(link(1, LinkKind.PUMP, 1, 2, 20, area, 0, 0.5, 0.5, spec, null),
                        pipe(2, 2, 3, 20, area, 1.0, 0.5, 0.5), pipe(3, 3, 1, 20, area, 1.0, 0.5, 0.5)),
                new Init(p, h).speed(1, 157).flow(1, mdot).flow(2, mdot).flow(3, mdot));
        n.link(1).pump().setDrivingTorque(2000);
        FluidNode a = n.node(1);
        a.setScalar(PassiveScalar.BORON, 3.0); // ≈ 2000 ppm в узле 1
        double total0 = 3.0;
        for (int k = 0; k < 2000; k++) {
            n.stepFast(DT);
            double total = n.node(1).scalar(PassiveScalar.BORON) + n.node(2).scalar(PassiveScalar.BORON)
                    + n.node(3).scalar(PassiveScalar.BORON);
            assertThat(total).isCloseTo(total0, within(1e-12));
        }
        // перемешалось: концентрации выровнялись
        assertThat(n.node(3).boronPpm()).isCloseTo(n.node(1).boronPpm(), within(1.0));
        assertThat(n.node(1).boronPpm()).isBetween(600.0, 720.0);
    }

    @Test
    void identicalRunsAreBitwiseEqual() {
        double[] r1 = blowdownTrace(), r2 = blowdownTrace();
        assertThat(r1).containsExactly(r2);
    }

    private static double[] blowdownTrace() {
        double p = 15.5e6, area = 2e-4;
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.separated(1, 0, 2, 0.5), FluidNodeSpec.hem(3, 0.5, 0, 1),
                        FluidNodeSpec.boundary(2, 0.5)),
                List.of(pipe(5, 1, 3, 100, 0.01, 1, 0.2, 0.5),
                        link(1, LinkKind.BREAK, 3, 2, 0.05 / area, area, 0, 0.5, 0.5, null, null)),
                new Init(p, W.enthalpyPT(p, 590)).node(2, 1e5, W.enthalpyPT(1e5, 300)));
        double[] trace = new double[600];
        for (int k = 0; k < 600; k++) {
            n.stepFast(DT);
            trace[k] = n.node(1).pressure() + n.link(1).massFlow();
        }
        return trace;
    }

    @Test
    void validatesSpecifications() {
        FluidNetwork n = network();
        StateCarryOver c = StateCarryOver.uniform(1e6, 4e5);
        assertThatThrownBy(() -> n.rebuild(List.of(new FluidNodeSpec(1, NodeKind.TWO_REGION, 1, 0, 1, 1, null)),
                List.of(), c)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> n.rebuild(List.of(FluidNodeSpec.boundary(1, 0), FluidNodeSpec.boundary(2, 0)),
                List.of(pipe(1, 1, 2, 1, 0.01, 1, 0, 0)), c)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> n.rebuild(List.of(FluidNodeSpec.hem(1, 1, 0, 1), FluidNodeSpec.hem(2, 1, 0, 1)),
                List.of(link(1, LinkKind.PUMP, 1, 2, 1, 0.01, 0, 0, 0, null, null)), c))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> n.rebuild(List.of(FluidNodeSpec.hem(1, 1, 0, 1)),
                List.of(pipe(1, 1, 7, 1, 0.01, 1, 0, 0)), c)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void componentsAreIndependent() {
        double p = 1e6, h = W.enthalpyPT(p, 350);
        FluidNetwork n = network();
        n.rebuild(List.of(FluidNodeSpec.hem(1, 1, 0, 1), FluidNodeSpec.hem(2, 1, 0, 1), FluidNodeSpec.hem(3, 1, 0, 1),
                        FluidNodeSpec.hem(4, 1, 0, 1), FluidNodeSpec.boundary(9, 0.5)),
                List.of(pipe(1, 1, 2, 100, 0.01, 1, 0.5, 0.5), pipe(2, 3, 4, 100, 0.01, 1, 0.5, 0.5),
                        pipe(3, 9, 1, 100, 0.01, 1, 0.5, 0.5), pipe(4, 9, 3, 100, 0.01, 1, 0.5, 0.5)),
                new Init(p, h));
        assertThat(n.componentCount()).isEqualTo(2);
        assertThat(n.componentOf(1)).isEqualTo(n.componentOf(2)).isNotEqualTo(n.componentOf(3));
        assertThat(n.componentOf(9)).isEqualTo(-1);
        for (int k = 0; k < 41; k++) n.stepFast(DT);
        assertThat(n.isAsleep(0)).isTrue();
        assertThat(n.isAsleep(1)).isTrue();
        n.node(3).addHeat(1e6);
        n.stepFast(DT);
        assertThat(n.isAsleep(n.componentOf(1))).isTrue();
        assertThat(n.isAsleep(n.componentOf(3))).isFalse();
    }
}
