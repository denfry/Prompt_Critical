package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.nuclide.PlutoniumCharge;
import dev.denfry.promptcritical.sim.neutronics.CellFeedbackState;
import dev.denfry.promptcritical.sim.neutronics.DelayedGroups;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.reactor.ChannelReactor;
import dev.denfry.promptcritical.sim.reactor.FuelDepletion;
import org.junit.jupiter.api.Test;

/**
 * МОКС в тепловых реакторах T2b (BWR), T1 (АМ-1) и T3 (CANDU): плутоний в зоне по изотопам, β_eff смеси, ценность
 * стержней и бора, выгорание Pu-241.
 *
 * <p>Опорные числа: β Pu-239 0,0021 против 0,0065 у U-235 (Keepin, 1965); β_eff полной МОКС-зоны ЛВР 0,0035–0,0045
 * против 0,006–0,0065 у UOX, ценность стержней и бора меньше на 20–30 % (OECD/NEA, «Physics of Plutonium Recycling»,
 * т. VI, 1995; IAEA TRS-415, 2003). Реакторный Pu выгрузки ЛВР 33 МВт·сут/кг: Pu-238 2,5 %, Pu-239 54,5 %, Pu-240
 * 23,8 %, Pu-241 11,5 %, Pu-242 7,7 % (OECD/NEA, там же).
 */
class MoxThermalReactorsTest {
    static final double F239 = 0.545, F240 = 0.238, F241 = 0.115, F242 = 0.077;
    static final double PU = 0.07, TAILS = 0.0025;

    static PlutoniumCharge reactorGrade(double puKg) {
        return PlutoniumCharge.of(puKg, F239, F240, F241, F242);
    }

    static LightWaterCore uoxCore() {
        LightWaterCore c = new LightWaterCore(1e8, 0.05);
        double kg = FuelDesign.VVER_1000.uKgPerAssembly(0.96, 0.04);
        for (int i = 0; i < LightWaterCore.POSITIONS; i++) assertTrue(c.load(true, i, FuelDesign.VVER_1000, kg, 0.04, 0.0003));
        return c;
    }

    static LightWaterCore moxCore(PlutoniumCharge perKgHm) {
        LightWaterCore c = new LightWaterCore(1e8, 0.05);
        double kg = FuelDesign.VVER_1000.uKgPerAssembly(0.96, 0.04);
        double pu = perKgHm.totalKg() * kg;
        for (int i = 0; i < LightWaterCore.POSITIONS; i++)
            assertTrue(c.load(true, i, FuelDesign.VVER_1000, kg - pu, TAILS, 0, perKgHm.scaled(kg)));
        return c;
    }

    static CellFeedbackState hot(LightWaterCore c, double boron) {
        return c.state(900, 740, 570, boron);
    }

    /** β_eff смеси падает на треть и больше: в МОКС делится в основном Pu-239 с β = 0,0021. */
    @Test
    void moxCoreHasSmallerDelayedNeutronFraction() {
        LightWaterCore uox = uoxCore(), mox = moxCore(reactorGrade(PU));
        uox.solveStatic(hot(uox, 0));
        mox.solveStatic(hot(mox, 0));
        double ratio = mox.betaEff() / uox.betaEff();
        assertTrue(uox.betaEff() > 0.0055 && uox.betaEff() < 0.0080, "UOX β " + uox.betaEff());
        assertTrue(mox.betaEff() > 0.0030 && mox.betaEff() < 0.0050, "MOX β " + mox.betaEff());
        assertTrue(ratio > 0.45 && ratio < 0.75, "β ratio " + ratio);
    }

    /**
     * Та же ступень реактивности (0,3 β урановой зоны) разгоняет МОКС-зону заметно быстрее: при меньшей β та же
     * реактивность ближе к мгновенной критичности.
     */
    @Test
    void sameReactivityStepGivesShorterPeriodWithMox() {
        LightWaterCore uox = uoxCore(), mox = moxCore(reactorGrade(PU));
        uox.solveStatic(hot(uox, 0));
        mox.solveStatic(hot(mox, 0));
        double rho = 0.3 * uox.betaEff();
        double nU = stepResponse(uox.delayedGroups(), uox.generationTime(), rho), nM = stepResponse(mox.delayedGroups(), mox.generationTime(), rho);
        assertTrue(nU > 1.2, "UOX power after 20 s " + nU);
        assertTrue(nM > 2 * nU, "MOX " + nM + " vs UOX " + nU);
        // мгновенный скачок n₀·β/(β − ρ) — тоже больше у МОКС
        assertTrue(mox.betaEff() / (mox.betaEff() - rho) > uox.betaEff() / (uox.betaEff() - rho));
    }

    private static double stepResponse(DelayedGroups g, double gen, double rho) {
        PointKinetics k = new PointKinetics(g, gen, 1);
        k.resetToEquilibrium(1);
        double n = 1;
        for (int i = 0; i < 2000; i++) n = k.step(0.01, rho, 0);
        return n;
    }

    /**
     * Ценность стержней и бора меньше: у МОКС больше тепловое поглощение и жёстче спектр. Бор в полной МОКС-зоне PWR
     * (8–9 % Pu) стоит 0,45–0,6 от урановой зоны; ячейка здесь даёт около 0,4 — Westcott-факторы Pu-239 и Pu-241 при
     * горячем спектре поднимают их поглощение, а резонансную часть спектра однозонная модель не разделяет. Стержни
     * теряют вдвое меньше ({@link PlutoniumCharge#ROD_TO_POISON_SENSITIVITY}).
     */
    @Test
    void controlAndBoronWorthAreReduced() {
        LightWaterCore uox = uoxCore(), mox = moxCore(reactorGrade(PU));
        uox.solveStatic(hot(uox, 600));
        mox.solveStatic(hot(mox, 600));
        assertEquals(1.0, uox.rodWorthFactor(), 1e-12, "fresh UOX keeps the installation's rod worth");
        assertTrue(mox.rodWorthFactor() > 0.6 && mox.rodWorthFactor() < 0.9, "rod worth factor " + mox.rodWorthFactor());
        double b = mox.boronCoefficient() / uox.boronCoefficient();
        assertTrue(uox.boronCoefficient() < 0 && mox.boronCoefficient() < 0, "boron coefficients negative");
        assertTrue(b > 0.3 && b < 0.7, "boron worth ratio " + b);
        assertTrue(mox.rodWorthFactor() > b, "a lumped rod loses less than dissolved boron");
        // стержни входят в реактивность с этим множителем
        CellFeedbackState s = hot(mox, 600);
        assertEquals(-0.05 * mox.rodWorthFactor(), mox.reactivity(s, -0.05) - mox.reactivity(s, 0), 1e-12);
    }

    /** Деградированный плутоний (больше Pu-240 и Pu-242, меньше Pu-239) при той же доле Pu даёт меньший k. */
    @Test
    void plutoniumVectorDrivesReactivity() {
        LightWaterCore good = moxCore(PlutoniumCharge.of(PU, 0.70, 0.22, 0.05, 0.03)), poor = moxCore(reactorGrade(PU));
        good.solveStatic(hot(good, 0));
        poor.solveStatic(hot(poor, 0));
        assertTrue(good.keffStatic() > poor.keffStatic() + 0.002, good.keffStatic() + " vs " + poor.keffStatic());
    }

    /** Pu-241 выгорает, из его захватов растёт Pu-242; масса ТМ уходит в продукты деления (с захваченными нейтронами). */
    @Test
    void depletionBurnsPu241AndBreedsPu242() {
        double[] hm0 = {100}, u5 = {100 * (1 - PU) * TAILS}, u8 = {100 * (1 - PU) * (1 - TAILS)}, u6 = {0}, fp = {0}, bu = {0};
        PlutoniumCharge p = reactorGrade(100 * PU);
        double[] pu9 = {p.pu239Kg()}, pu0 = {p.pu240Kg()}, pu1 = {p.pu241Kg()}, pu2 = {p.pu242Kg()};
        double before = u5[0] + u8[0] + pu9[0] + pu0[0] + pu1[0] + pu2[0];
        for (int i = 0; i < 100; i++) FuelDepletion.deplete(0, 1e6, 86400, 0.5, hm0, u5, u8, u6, pu9, pu0, pu1, pu2, fp, bu);
        assertEquals(1.0, bu[0], 1e-9, "100 MW·d over 100 kg");
        assertTrue(pu1[0] < p.pu241Kg(), "Pu-241 burns");
        assertTrue(pu2[0] > p.pu242Kg(), "Pu-242 grows");
        assertTrue(pu9[0] + pu1[0] < p.fissileKg(), "fissile Pu goes down in MOX");
        double after = u5[0] + u8[0] + u6[0] + pu9[0] + pu0[0] + pu1[0] + pu2[0] + fp[0];
        assertEquals(before, after, before * 2e-3, "heavy metal + fission products balance");
    }

    /** T2b: BWR берёт МОКС, β падает; выгрузка свежей ТВС возвращает весь уран и весь плутоний по изотопам. */
    @Test
    void bwrLoadsMoxAndReturnsEveryKilogram() {
        BoilingWaterReactor r = new BoilingWaterReactor();
        double kg = FuelDesign.VVER_1000.uKgPerAssembly(0.96, 0.036), pu = kg * PU;
        PlutoniumCharge charge = reactorGrade(pu);
        for (int i = 0; i < BoilingWaterReactor.POSITIONS; i++)
            assertTrue(r.load(i, FuelDesign.VVER_1000, kg - pu, TAILS, 0, charge));
        r.hotStart(1);
        r.evaluateReactivity();
        assertTrue(r.core().betaEff() < 0.005, "BWR MOX β " + r.core().betaEff());
        assertTrue(r.core().rodWorthFactor() < 0.95, "BWR MOX rods " + r.core().rodWorthFactor());

        BoilingWaterReactor cold = new BoilingWaterReactor();
        assertTrue(cold.load(0, FuelDesign.VVER_1000, kg - pu, TAILS, 0.0001, charge));
        LightWaterCore.AssemblyFuel f = cold.unload(0);
        assertNotNull(f);
        assertEquals(kg - pu, f.uraniumKg(), 1e-9);
        assertEquals(pu, f.plutoniumKg(), 1e-9);
        assertEquals(charge.pu241Kg(), f.pu241Kg(), 1e-12);
        // Pu-238 ушёл в Pu-242
        assertEquals(pu * (F242 + 1 - F239 - F240 - F241 - F242), f.pu242Kg(), 1e-12);
    }

    /** Больше {@code MoxBlend.MAX_PU} плутония в тепловой реактор не берётся. */
    @Test
    void overRichMoxIsRefused() {
        BoilingWaterReactor r = new BoilingWaterReactor();
        assertFalse(r.load(0, FuelDesign.VVER_1000, 80, TAILS, 0, reactorGrade(20)));
        ChannelReactor t1 = new ChannelReactor();
        t1.setRods(1, 1, 1);
        assertFalse(t1.load(0, 3.5, TAILS, 0, reactorGrade(0.8)));
    }

    /** T3: CANDU на МОКС (1,5 % Pu в обеднённом уране): β ниже, чем на природном уране; перегрузка на ходу ведёт Pu. */
    @Test
    void canduBurnsMoxBundles() {
        double bundle = FuelDesign.CANDU_37.uKgPerAssembly(0.96, 0.00711), pu = bundle * 0.015;
        CanduReactor nu = CanduReactorTest.loaded(), mox = new CanduReactor();
        for (int i = 0; i < CanduReactor.CHANNELS; i++)
            assertTrue(mox.loadChannel(i, bundle - pu, TAILS, 0, reactorGrade(pu)));
        for (CanduReactor r : new CanduReactor[] {nu, mox}) {
            r.setRods(0, 0);
            r.hotStart(1);
            r.evaluateReactivity();
        }
        assertTrue(mox.core().betaEff() < 0.8 * nu.core().betaEff(), mox.core().betaEff() + " vs " + nu.core().betaEff());
        assertTrue(mox.core().rodWorthFactor() < 1, "adjusters lose worth");
        double puBefore = mox.core().fuel(0).plutoniumKg();
        assertEquals(CanduReactor.BUNDLES_PER_CHANNEL * pu, puBefore, 1e-9);
        LightWaterCore.AssemblyFuel out = mox.refuel(0, CanduReactor.SHIFT_BUNDLES, bundle - pu, TAILS, 0, reactorGrade(pu));
        assertNotNull(out, "a full channel pushes out as many bundles as went in");
        assertEquals(CanduReactor.SHIFT_BUNDLES * bundle, out.hm0Kg(), 1e-6);
        assertEquals(CanduReactor.SHIFT_BUNDLES * pu, out.plutoniumKg(), 1e-6);
        assertEquals(puBefore, mox.core().fuel(0).plutoniumKg(), 1e-6);
    }

    /** T1: АМ-1 с каналами МОКС — β и ценность стержней меньше, выгрузка возвращает плутоний по изотопам. */
    @Test
    void am1ChannelsTakeMox() {
        ChannelReactor uox = new ChannelReactor(), mox = new ChannelReactor();
        uox.setRods(1, 1, 1);
        mox.setRods(1, 1, 1);
        double pu = 4.3 * PU;
        for (int i = 0; i < ChannelReactor.CHANNELS; i++) {
            assertTrue(uox.load(i, 4.3, 0.05, 0.0004));
            assertTrue(mox.load(i, 4.3 - pu, TAILS, 0, reactorGrade(pu)));
        }
        uox.equilibrate(1e-9);
        mox.equilibrate(1e-9);
        assertTrue(mox.betaEff() < 0.75 * uox.betaEff(), mox.betaEff() + " vs " + uox.betaEff());
        assertEquals(1.0, uox.rodWorthFactor(), 1e-12);
        assertTrue(mox.rodWorthFactor() > 0.5 && mox.rodWorthFactor() < 0.95, "AM-1 rods " + mox.rodWorthFactor());
        assertTrue(mox.rodReactivity() > uox.rodReactivity(), "the same rods hold less in MOX");
        ChannelReactor.ChannelFuel f = mox.unload(0);
        assertNotNull(f);
        assertEquals(pu, f.plutoniumKg(), 1e-9);
        assertEquals(4.3, f.heavyMetalKg(), 1e-9);
    }
}
