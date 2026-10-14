package dev.denfry.promptcritical.sim.neutronics;

import static dev.denfry.promptcritical.sim.neutronics.LatticeDesigns.T2_HEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.nuclide.NuclideVector;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Поведение нодальной модели и квазистатики §1.6.5–1.6.7 вне оракулов §1.16. */
class NodalBehaviourTest {
    private static final LatticeModel MODEL = new LatticeModel();
    private static final NuclideVector FUEL = Cores.freshFuel(T2_HEX);
    private static final CellComposition CELL = LatticeDesigns.referenceCell(T2_HEX);
    private static final int NX = 5, NY = 5, NZ = 3, N = NX * NY * NZ;
    private static final int[] GRID = Cores.grid(NX, NY, NZ);

    private static CellFeedbackState[] states(CellFeedbackState s) {
        return Cores.filled(new CellFeedbackState[N], s);
    }

    private static SixFactor[] evaluate(CellFeedbackState[] s) {
        SixFactor[] k = new SixFactor[N];
        for (int i = 0; i < N; i++) k[i] = MODEL.evaluate(CELL, FUEL, s[i]);
        return k;
    }

    private static NodalFluxSolver solve(SixFactor[] k) {
        return Cores.solve(Cores.filled(new CellComposition[N], CELL), k, GRID, true);
    }

    @Test
    void symmetricCoreHasFlatIndicatorsAndNormalisedShape() {
        NodalFluxSolver s = solve(evaluate(states(LatticeDesigns.referenceState(T2_HEX).withBoron(1000))));
        assertThat(Arrays.stream(s.nodePowerFraction()).sum()).isCloseTo(1, within(1e-12));
        assertThat(Arrays.stream(s.importanceWeights()).sum()).isCloseTo(1, within(1e-12));
        assertThat(s.axialOffset()).isCloseTo(0, within(2e-3));
        for (int q = 0; q < 4; q++) assertThat(s.radialTilt(q)).isCloseTo(1, within(2e-3));
        assertThat(s.maxVolumetricPeaking()).isGreaterThan(1);
        double[] p = s.nodePowerFraction();
        assertThat(p[Cores.index(NX, NY, 2, 2, 1)]).isEqualTo(Arrays.stream(p).max().orElseThrow());
    }

    @Test
    void cleanerTopLayerShiftsPowerUp() {
        CellFeedbackState base = LatticeDesigns.referenceState(T2_HEX).withBoron(1000);
        CellFeedbackState[] s = states(base);
        for (int i = Cores.index(NX, NY, 0, 0, 2); i < N; i++) s[i] = base.withBoron(800);
        for (int i = 0; i < NX * NY; i++) s[i] = base.withBoron(1200);
        NodalFluxSolver solver = solve(evaluate(s));
        assertThat(solver.axialOffset()).isGreaterThan(0.05);
    }

    @Test
    void quasiStaticFeedbackTracksResolvedReactivity() {
        CellFeedbackState base = LatticeDesigns.referenceState(T2_HEX).withBoron(1000);
        CellFeedbackState[] s0 = states(base);
        SixFactor[] k0 = evaluate(s0);
        NodalFluxSolver solver = solve(k0);
        QuasiStaticFeedback qs = new QuasiStaticFeedback();
        qs.capture(solver, k0, s0);

        double[] tf = new double[N], rho = new double[N], tm = new double[N], ppm = new double[N];
        CellFeedbackState[] s1 = new CellFeedbackState[N];
        for (int i = 0; i < N; i++) {
            // Неравномерный разогрев: центр сильнее.
            tf[i] = base.fuelEffTemp() + 30 + 40 * solver.importanceWeights()[i] * N;
            rho[i] = base.coolantDensity() - 4;
            tm[i] = base.moderatorTemp() + 2;
            ppm[i] = base.boronPpm() - 10;
            s1[i] = new CellFeedbackState(tf[i], rho[i], tm[i], ppm[i], 0, 0);
        }
        double predicted = qs.doppler(tf) + qs.coolant(rho) + qs.moderator(tm) + qs.boron(ppm);
        double exact = 1 / solver.keff() - 1 / solve(evaluate(s1)).keff();
        assertThat(predicted).isCloseTo(exact, within(0.05 * Math.abs(exact)));

        // Малое поглощение в центральном узле — стержень по теории возмущений.
        double[] dSigma = new double[N];
        int center = Cores.index(NX, NY, 2, 2, 1);
        dSigma[center] = 1e-3;
        SixFactor c = k0[center];
        SixFactor[] k2 = k0.clone();
        k2[center] = new SixFactor(c.eta(), c.f(), c.p(), c.epsilon(), c.kInf(), c.migrationArea(),
                c.diffusionLength2(), c.age(), c.sigmaA() + 1e-3, c.nuSigmaF(), c.sigmaF(), 0, 0, 0, 0,
                c.thermalDiffusion(), c.slowingDownTime());
        double rodExact = 1 / solver.keff() - 1 / solve(k2).keff();
        assertThat(qs.rods(dSigma)).isCloseTo(rodExact, within(0.1 * Math.abs(rodExact)));
        assertThat(qs.rods(dSigma)).isNegative();
    }

    @Test
    void wielandtShiftSpeedsUpConvergence() {
        NodalFluxSolver s = solve(evaluate(states(LatticeDesigns.referenceState(T2_HEX))));
        assertThat(s.lastIterations()).isLessThan(40);
        assertThat(Arrays.stream(s.flux()).allMatch(v -> v > 0)).isTrue();
    }

    @Test
    void reflectorSavingsMatchOneGroupEstimates() {
        CellFeedbackState hot = LatticeDesigns.referenceState(T2_HEX);
        SixFactor core = MODEL.evaluate(CELL, FUEL, hot);
        SixFactor water = MODEL.evaluate(CellComposition.nonFuel(ModeratorKind.NONE, 0, CoolantKind.H2O, 1, 0), null, hot);
        CellComposition pile = LatticeDesigns.referenceCell(LatticeDesigns.T0_SLUG);
        CellFeedbackState cold = LatticeDesigns.referenceState(LatticeDesigns.T0_SLUG);
        SixFactor t0 = MODEL.evaluate(pile, Cores.freshFuel(LatticeDesigns.T0_SLUG), cold);
        SixFactor graphite = MODEL.evaluate(CellComposition.nonFuel(ModeratorKind.GRAPHITE, 1, CoolantKind.NONE, 0,
                LatticeDesigns.NUCLEAR_GRAPHITE_IMPURITY_PPM), null, cold);
        // GDD §1.6.3: графит 1 м — ≈ 50 см, вода ≥ 20 см — ≈ 7 см (порядок величины).
        assertThat(MODEL.reflectorSavings(t0, graphite, 1.0)).isBetween(0.35, 0.65);
        assertThat(MODEL.reflectorSavings(core, water, 0.3)).isBetween(0.03, 0.2);
        // T0 5×5×5 с отражателем: k_eff ≈ 1,01 (§1.14.4).
        double b2 = MODEL.geometricBuckling(ReactorShape.box(5, 5, 5), MODEL.reflectorSavings(t0, graphite, 1.0));
        assertThat(MODEL.estimateKeff(t0, b2)).isBetween(1.0, 1.03);
    }
}
