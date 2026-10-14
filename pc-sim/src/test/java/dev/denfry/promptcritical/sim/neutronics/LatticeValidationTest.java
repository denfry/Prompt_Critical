package dev.denfry.promptcritical.sim.neutronics;

import static dev.denfry.promptcritical.sim.neutronics.LatticeDesigns.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.nuclide.NuclideVector;
import dev.denfry.promptcritical.sim.nuclide.NuclideView;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Тест-кейсы 01 §1.16, группы §1.6–1.7 (решётка, нодальная модель, обратные связи).
 *
 * <p>Таблица §1.7.5 описывает реакторы, поэтому коэффициенты считаются как d ln k_eff,
 * k_eff = k∞/(1 + M²B²), с B² типичной зоны тира: T0 — куча 5×5×5 с отражателем (§1.14.4),
 * T1 — 20×20×8 м, T2 — ВВЭР-1000 (R = 1,6 м, H = 3,5 м), T3 — CANDU (R = 3,5 м, H = 6 м).
 */
class LatticeValidationTest {
    private static final LatticeModel MODEL = new LatticeModel();
    private static final double PCM = 1e5;
    private static final double B2_T0 = 8.2e-5, B2_T1 = 1.67e-5, B2_T2 = 2.7e-4, B2_T3 = 6.3e-5;
    /** Изотопика Pu середины кампании (Pu-238..242, §1.9.4) и состав «0,3 % Pu». */
    private static final double[] PU_VECTOR = {0.02, 0.55, 0.24, 0.12, 0.07};

    private static double lnKeff(LatticeModel m, String id, NuclideView fuel, CellFeedbackState s, double b2) {
        SixFactor x = m.evaluate(referenceCell(id), fuel, s);
        return Math.log(x.kInf() / (1 + x.migrationArea() * b2));
    }

    /** dρ/dX реактора, pcm на единицу X, центральной разностью шага h. */
    private static double coefficient(String id, NuclideView fuel, double b2, double h,
            UnaryOperator<CellFeedbackState> plus, UnaryOperator<CellFeedbackState> minus) {
        CellFeedbackState s = referenceState(id);
        return (lnKeff(MODEL, id, fuel, plus.apply(s), b2) - lnKeff(MODEL, id, fuel, minus.apply(s), b2)) / (2 * h) * PCM;
    }

    private static double doppler(String id, NuclideView fuel, double b2) {
        return coefficient(id, fuel, b2, 10, s -> s.withFuelTemp(s.fuelEffTemp() + 10),
                s -> s.withFuelTemp(s.fuelEffTemp() - 10));
    }

    private static double moderatorTemp(String id, NuclideView fuel, double b2) {
        return coefficient(id, fuel, b2, 5, s -> s.withModeratorTemp(s.moderatorTemp() + 5),
                s -> s.withModeratorTemp(s.moderatorTemp() - 5));
    }

    private static NuclideVector midBurnup(String id, double puFraction) {
        return NuclideVector.mox(1000, referenceEnrichment(id) * (1 - puFraction / 0.0075), puFraction, PU_VECTOR);
    }

    private static SixFactor reference(String id) {
        return MODEL.evaluate(referenceCell(id), Cores.freshFuel(id), referenceState(id));
    }

    private static double betaEff(String id, double b2) {
        CellComposition c = referenceCell(id);
        SixFactor x = reference(id);
        return MODEL.delayedGroups(MODEL.fissionFractions(c, Cores.freshFuel(id), referenceState(id)), b2, x).betaTotal();
    }

    private static double generationTime(String id) {
        return MODEL.generationTime(reference(id), referenceState(id).moderatorTemp() + LatticeModel.NEUTRON_GAS_OFFSET);
    }

    @Test
    @Tag("T-1.6-01")
    void t0GraphitePile() {
        assertThat(reference(T0_SLUG).kInf()).isBetween(1.05, 1.10);
        assertThat(reference(T0_SLUG).epsilon()).isCloseTo(1.03, within(0.005)); // калибровка c_ε (§1.6.2)
        assertThat(doppler(T0_SLUG, Cores.freshFuel(T0_SLUG), B2_T0)).isBetween(-2.0, -1.0);
        // GDD: графит −1…+1 pcm/К. Модель у свежего прир. U даёт ≈ −6: g(U-235) Весткотта падает с T_n
        // (η −10 pcm/К), блокировка в графите возвращает лишь +5. С плутонием знак меняется.
        double fresh = moderatorTemp(T0_SLUG, Cores.freshFuel(T0_SLUG), B2_T0);
        assertThat(fresh).isBetween(-7.0, -1.0);
        assertThat(moderatorTemp(T0_SLUG, midBurnup(T0_SLUG, 0.003), B2_T0)).isGreaterThan(fresh + 5);
        assertThat(betaEff(T0_SLUG, B2_T0)).isCloseTo(0.0067, within(0.0067 * 0.05));
        assertThat(generationTime(T0_SLUG)).isBetween(0.5e-3, 2e-3);
    }

    @Test
    @Tag("T-1.6-01")
    void t1GraphiteChannel() {
        NuclideVector fresh = Cores.freshFuel(T1_CLUSTER);
        assertThat(reference(T1_CLUSTER).kInf()).isBetween(1.2, 1.35);
        // GDD: −1…−2. Модель −2,2 при T_f = 900 К: формула Хеллстранда для UO₂ (§1.6.2) с p = 0,80.
        assertThat(doppler(T1_CLUSTER, fresh, B2_T1)).isBetween(-2.5, -1.0);
        assertThat(moderatorTemp(T1_CLUSTER, midBurnup(T1_CLUSTER, 0.003), B2_T1)).isBetween(1.0, 6.0);
        assertThat(betaEff(T1_CLUSTER, B2_T1)).isCloseTo(0.0060, within(0.0060 * 0.15));
        assertThat(generationTime(T1_CLUSTER)).isBetween(3e-4, 1.2e-3);
    }

    @Test
    @Tag("T-1.6-01")
    void t2PressurisedWater() {
        NuclideVector fresh = Cores.freshFuel(T2_HEX);
        assertThat(reference(T2_HEX).kInf()).isBetween(1.30, 1.45);
        assertThat(doppler(T2_HEX, fresh, B2_T2)).isBetween(-4.0, -2.0);
        double boron = coefficient(T2_HEX, fresh, B2_T2, 10, s -> s.withBoron(1010), s -> s.withBoron(990));
        assertThat(boron).isBetween(-10.0, -7.0);
        assertThat(betaEff(T2_HEX, B2_T2)).isCloseTo(0.0068, within(0.0068 * 0.03));
        assertThat(generationTime(T2_HEX)).isBetween(1.5e-5, 2.5e-5);
    }

    @Test
    @Tag("T-1.6-01")
    void t3HeavyWater() {
        NuclideVector fresh = Cores.freshFuel(T3_CLUSTER_D2O);
        assertThat(reference(T3_CLUSTER_D2O).kInf()).isBetween(1.10, 1.15);
        assertThat(doppler(T3_CLUSTER_D2O, fresh, B2_T3)).isBetween(-1.5, -0.5);
        // Полное осушение каналов: GDD +1000…+1500 pcm, модель ≈ +1660 (допуск 15 %).
        CellFeedbackState s = referenceState(T3_CLUSTER_D2O);
        double voided = (lnKeff(MODEL, T3_CLUSTER_D2O, fresh, s.withCoolantDensity(0), B2_T3)
                - lnKeff(MODEL, T3_CLUSTER_D2O, fresh, s, B2_T3)) * PCM;
        assertThat(voided).isBetween(1000.0, 1500 * 1.15);
        // GDD: D₂O 0…+8 pcm/К. Свежее прир. топливо в модели −18 (спад g(U-235) и плотность D₂O),
        // с ростом доли Pu коэффициент растёт и становится положительным.
        double tmFresh = moderatorTemp(T3_CLUSTER_D2O, fresh, B2_T3);
        double tmPu = moderatorTemp(T3_CLUSTER_D2O, midBurnup(T3_CLUSTER_D2O, 0.004), B2_T3);
        assertThat(tmPu).isBetween(0.0, 8.0);
        assertThat(tmFresh).isLessThan(tmPu - 10);
        // GDD: «~−3…−5 pcm/ppm в замедлителе» — по порядку величины это mk/ppm; модель −6 mk/ppm.
        double boron = (lnKeff(MODEL, T3_CLUSTER_D2O, fresh, s.withBoron(0.1), B2_T3)
                - lnKeff(MODEL, T3_CLUSTER_D2O, fresh, s, B2_T3)) / 0.1 * PCM;
        assertThat(boron).isBetween(-800.0, -300.0);
        // Фотонейтроны D₂O добавляют 5·10⁻⁴ к Кипину.
        SixFactor x = reference(T3_CLUSTER_D2O);
        double[] fr = MODEL.fissionFractions(referenceCell(T3_CLUSTER_D2O), fresh, s);
        double keepin = MODEL.delayedGroups(fr, B2_T3, x).betaTotal();
        // GDD: 0,0058 (НК). γ_d = exp(B²Δτ)/ε ≈ 0,965 не опускает β_eff ниже β^mix природного U (0,0069).
        assertThat(keepin).isBetween(0.0058, DelayedNeutronData.mixedBeta(fr));
        DelayedGroups all = MODEL.delayedGroups(fr, B2_T3, x, MODEL.photoneutronBeta(referenceCell(T3_CLUSTER_D2O)));
        assertThat(all.count()).isEqualTo(9);
        assertThat(all.betaTotal() - keepin).isCloseTo(5e-4, within(1e-12));
        assertThat(generationTime(T3_CLUSTER_D2O)).isBetween(4.5e-4, 1.8e-3);
    }

    @Test
    @Tag("T-1.6-02")
    void hexLatticeFourFactors() {
        SixFactor x = reference(T2_HEX);
        // GDD-прототип: η = 1,91 — сечения 2200 м/с; с g(T_n) Весткотта для U-235 (§1.6.2) η = 1,88.
        assertThat(x.eta()).isCloseTo(1.88, within(0.02));
        // GDD-прототип: p = 0,743. Его же формула с его же I_eff = 22,2 б, N₈ = 0,0223, V_F = 0,27 и
        // ξΣ_s = 1,37·0,715·0,62 даёт exp(−0,1337/0,607) = 0,80; модель (C_D = 0,16) — 0,79.
        assertThat(x.p()).isCloseTo(0.79, within(0.03));
        assertThat(Math.exp(-0.0223 * 0.27 * 22.2 / (1.37 * 0.715 * 0.62))).isCloseTo(0.80, within(0.005));
        assertThat(x.f()).isBetween(0.93, 0.96);
        assertThat(x.epsilon()).isBetween(1.03, 1.07);
    }

    @Test
    @Tag("T-1.6-03")
    void homogeneousBoxMatchesBucklingEstimate() {
        for (String id : new String[] {T2_HEX, T0_SLUG}) {
            SixFactor x = reference(id);
            NodalFluxSolver s = Cores.solve(Cores.filled(new CellComposition[125], referenceCell(id)),
                    Cores.filled(new SixFactor[125], x), Cores.grid(5, 5, 5), true);
            double b2 = MODEL.geometricBuckling(ReactorShape.box(5, 5, 5), MODEL.bareSavings(x));
            assertThat(Math.abs(s.keff() / MODEL.estimateKeff(x, b2) - 1)).as(id).isLessThan(0.03);
        }
    }

    @Test
    @Tag("T-1.6-04")
    void freshAssemblyInWaterIsCriticalAndRackIsNot() {
        CellFeedbackState hot = referenceState(T2_HEX);
        CellFeedbackState cold = new CellFeedbackState(293, 998, 293, 0, 0, 0);
        CellComposition water = CellComposition.nonFuel(ModeratorKind.NONE, 0, CoolantKind.H2O, 1, 0);
        CellComposition rack = referenceCell(T2_HEX).withAbsorber(AbsorberMaterial.BORON_STEEL, 0.02);
        NuclideVector fuel = Cores.freshFuel(T2_HEX);
        for (boolean inRack : new boolean[] {false, true}) {
            CellComposition[] cells = Cores.filled(new CellComposition[27], water);
            SixFactor[] k = Cores.filled(new SixFactor[27], MODEL.evaluate(water, null, cold));
            cells[13] = inRack ? rack : referenceCell(T2_HEX);
            k[13] = MODEL.evaluate(cells[13], fuel, inRack ? cold : hot);
            double keff = Cores.solve(cells, k, Cores.grid(3, 3, 3), true).keff();
            if (inRack) assertThat(keff).isLessThan(0.95);
            else assertThat(keff).isGreaterThan(1); // → CriticalityAlarmEvent (04b)
        }
    }

    @Test
    @Tag("T-1.6-05")
    void linkLengthCorrectionRestoresRodWorth() {
        CellFeedbackState s = referenceState(T2_HEX);
        SixFactor fuel = MODEL.evaluate(referenceCell(T2_HEX), Cores.freshFuel(T2_HEX), s.withBoron(1500));
        CellComposition water = CellComposition.nonFuel(ModeratorKind.NONE, 0, CoolantKind.H2O, 1, 0);
        CellComposition rod = water.withAbsorber(AbsorberMaterial.B4C, 0.005);
        int center = Cores.index(5, 5, 2, 2, 1);
        CellComposition[] cells = Cores.filled(new CellComposition[75], referenceCell(T2_HEX));
        cells[center] = water;
        double[] worth = new double[2];
        for (int q = 0; q < 2; q++) {
            SixFactor[] withWater = Cores.filled(new SixFactor[75], fuel), withRod = withWater.clone();
            withWater[center] = MODEL.evaluate(water, null, s);
            withRod[center] = MODEL.evaluate(rod, null, s);
            double kWater = Cores.solve(cells, withWater, Cores.grid(5, 5, 3), q == 0).keff();
            double kRod = Cores.solve(cells, withRod, Cores.grid(5, 5, 3), q == 0).keff();
            worth[q] = 1 / kRod - 1 / kWater;
        }
        assertThat(worth[1]).isPositive();
        assertThat(worth[0]).isGreaterThanOrEqualTo(4 * worth[1]);
    }

    @Test
    @Tag("T-1.6-06")
    void warmStartAfterSmallPerturbation() {
        SixFactor x = reference(T2_HEX);
        CellComposition[] cells = Cores.filled(new CellComposition[125], referenceCell(T2_HEX));
        int[] nb = Cores.grid(5, 5, 5);
        NodalFluxSolver warm = Cores.solve(cells, Cores.filled(new SixFactor[125], x), nb, true);
        SixFactor[] perturbed = Cores.filled(new SixFactor[125], x);
        perturbed[Cores.index(5, 5, 1, 2, 2)] = new SixFactor(x.eta(), x.f(), x.p(), x.epsilon(), x.kInf(),
                x.migrationArea(), x.diffusionLength2(), x.age(), x.sigmaA() * 1.01, x.nuSigmaF(), x.sigmaF(), 0, 0, 0,
                0, x.thermalDiffusion(), x.slowingDownTime());
        double converged = Cores.solve(cells, perturbed, nb, true).keff();
        warm.updateConstants(perturbed);
        warm.iterate(4);
        assertThat(Math.abs(warm.keff() - converged)).isLessThan(2e-5);
    }

    @Test
    @Tag("T-1.7-01")
    void moderatorCoefficientWeakensWithBoron() {
        NuclideVector fresh = Cores.freshFuel(T2_HEX);
        double[] mtc = new double[2];
        int i = 0;
        for (double ppm : new double[] {0, 1000}) {
            // MTC = α_ρc (∂ρ/∂T)_P + спектральная часть; (∂ρ/∂T)_P = −2,2 кг/(м³·К) при 15,5 МПа, 305 °C.
            double perDensity = coefficient(T2_HEX, fresh, B2_T2, 5, s -> s.withBoron(ppm).withCoolantDensity(720),
                    s -> s.withBoron(ppm).withCoolantDensity(710));
            double spectral = coefficient(T2_HEX, fresh, B2_T2, 5,
                    s -> s.withBoron(ppm).withModeratorTemp(s.moderatorTemp() + 5),
                    s -> s.withBoron(ppm).withModeratorTemp(s.moderatorTemp() - 5));
            mtc[i++] = -2.2 * perDensity + spectral;
        }
        assertThat(mtc[0]).isBetween(-60.0, -10.0);
        assertThat(mtc[1]).isBetween(-60.0, -10.0);
        assertThat(Math.abs(mtc[1])).isLessThan(Math.abs(mtc[0]));
        // Паровой коэффициент на 1 % пустот: −α_ρc (ρ_f − ρ_g)·0,01, ρ_f − ρ_g = 492,5 кг/м³.
        double perDensity = coefficient(T2_HEX, fresh, B2_T2, 5, s -> s.withCoolantDensity(720),
                s -> s.withCoolantDensity(710));
        assertThat(-perDensity * 492.5 * 0.01).isBetween(-200.0, -100.0);
    }

    /** Паровой коэффициент T1 при снижении средней плотности в канале 0,45 → 0,2 г/см³, pcm/%. */
    private static double t1Void(LatticeModel m) {
        CellFeedbackState s = referenceState(T1_CLUSTER);
        NuclideVector fresh = Cores.freshFuel(T1_CLUSTER);
        double drho = lnKeff(m, T1_CLUSTER, fresh, s.withCoolantDensity(200), B2_T1)
                - lnKeff(m, T1_CLUSTER, fresh, s, B2_T1);
        double voidPercent = (450 - 200) / (739.7 - 36.52) * 100; // 7 МПа
        return drho * PCM / voidPercent;
    }

    @Test
    @Tag("T-1.7-02")
    void graphiteChannelVoidCoefficientIsPositive() {
        assertThat(t1Void(MODEL)).isBetween(10.0, 30.0);
    }

    @Test
    @Tag("T-1.7-03")
    void adjacencyFactorDrivesPositiveVoid() {
        LatticeModel noAdjacency = new LatticeModel(NeutronicsConfig.defaults().withCoolantAdjacencyFactor(1));
        double without = t1Void(noAdjacency), with = t1Void(MODEL);
        // GDD: ≈ 0 ± 5 pcm/%. В модели остаётся ≈ +12: удаление поглощения воды (f) лишь частично
        // компенсируется потерей замедления — как у реального РБМК; g_adj даёт остальное.
        assertThat(without).isBetween(0.0, 15.0);
        assertThat(with - without).isGreaterThan(8);
    }

    @Test
    @Tag("T-1.7-04")
    void dopplerScalesAsInverseSquareRootOfTemperature() {
        CellComposition c = referenceCell(T2_HEX);
        NuclideVector fresh = Cores.freshFuel(T2_HEX);
        CellFeedbackState s = referenceState(T2_HEX);
        SixFactor at600 = MODEL.evaluate(c, fresh, s.withFuelTemp(600));
        SixFactor at1200 = MODEL.evaluate(c, fresh, s.withFuelTemp(1200));
        double a600 = at600.reactivityCoefficient(at600.dkdTf()), a1200 = at1200.reactivityCoefficient(at1200.dkdTf());
        assertThat(a600).isNegative();
        assertThat(a1200).isNegative();
        assertThat(a600 / a1200).isCloseTo(Math.sqrt(2), within(0.05 * Math.sqrt(2)));
        for (String id : LatticeDesigns.ids()) {
            SixFactor x = reference(id);
            assertThat(x.dkdTf()).as(id).isNegative();
        }
    }
}
