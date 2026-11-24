package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** NPD: природный уран критичен, мощность держит уровень замедлителя, сброс глушит, перегрузка на ходу машинами. */
class NpdReactorTest {
    static final double BUNDLE_KG = FuelDesign.NPD_19.uKgPerAssembly(0.96, 0.00711);

    static NpdReactor loaded() {
        NpdReactor r = new NpdReactor();
        for (int i = 0; i < NpdReactor.CHANNELS; i++) assertTrue(r.loadChannel(i, BUNDLE_KG, 0.00711, 0.000054));
        return r;
    }

    static void run(NpdReactor r, double s) {
        for (int i = 0; i < Math.round(s / NpdReactor.DT); i++) r.step(NpdReactor.DT);
    }

    /** Пучок 19 элементов ⌀15,25 мм: около 13–15 кг урана; полная зона — около 16 т природного урана. */
    @Test
    void bundleAndCoreMass() {
        assertTrue(BUNDLE_KG > 12.5 && BUNDLE_KG < 15.5, "bundle " + BUNDLE_KG);
        NpdReactor r = loaded();
        assertTrue(r.core().heavyMetalKg() > 14e3 && r.core().heavyMetalKg() < 18e3, "core " + r.core().heavyMetalKg());
        // каландр ⌀5,15 × 4,6 м: около 100 т D₂O
        assertTrue(r.moderatorCapacityKg() > 90e3 && r.moderatorCapacityKg() < 110e3, "moderator " + r.moderatorCapacityKg());
    }

    /**
     * Свежий природный уран без ксенона с большим запасом: критичен при неполном каландре. Зона «равновесного»
     * выгорания (U-235 около 0,45 %) критична у почти полного уровня — запас держит только перегрузка на ходу.
     */
    @Test
    void freshCoreIsCriticalBelowFullLevel() {
        NpdReactor r = loaded();
        r.hotStart(1);
        double lvl = r.criticalLevel();
        assertTrue(lvl > 0.2 && lvl < 0.9, "fresh critical level " + lvl);
        NpdReactor burnt = new NpdReactor();
        for (int i = 0; i < NpdReactor.CHANNELS; i++) assertTrue(burnt.loadChannel(i, BUNDLE_KG, 0.0045, 0.00005));
        burnt.hotStart(1);
        double eq = burnt.criticalLevel();
        assertTrue(eq > lvl && eq <= 1, "burnt critical level " + eq);
        assertTrue(r.core().generationTime() > 4e-4 && r.core().generationTime() < 2e-3, "Λ " + r.core().generationTime());
    }

    /** Пустотный коэффициент положительный, как у всех CANDU. */
    @Test
    void coolantVoidIsPositive() {
        NpdReactor r = loaded();
        r.hotStart(1);
        double rho = r.evaluateReactivity();
        r.setCoolantVoid(1);
        double voided = (r.evaluateReactivity() - rho) * 1e3;
        assertTrue(voided > 3 && voided < 25, "void " + voided + " mk");
    }

    /** Автомат уровня держит номинал и поднимает мощность с 10 % до 100 % без срабатывания защиты по периоду. */
    @Test
    void levelControlRaisesAndHoldsPower() {
        NpdReactor r = loaded();
        r.hotStart(1);
        run(r, 300);
        assertEquals(NpdReactor.Trip.NONE, r.trip());
        assertEquals(1, r.power(), 0.03);

        NpdReactor low = loaded();
        low.hotStart(0.1);
        low.setPowerSetpoint(1);
        run(low, 900);
        assertEquals(NpdReactor.Trip.NONE, low.trip(), "trip " + low.trip());
        assertEquals(1, low.power(), 0.05);
    }

    /** Сброс замедлителя: мощность падает ниже 5 % за 10 с, D₂O не теряется, а подъём насосами занимает минуты. */
    @Test
    void moderatorDumpShutsDown() {
        NpdReactor r = loaded();
        r.hotStart(1);
        double total = r.moderator().kg() + r.dumpTank().kg();
        r.scram();
        run(r, 10);
        assertTrue(r.dumping());
        assertTrue(r.power() < 0.05, "power " + r.power());
        run(r, 120);
        assertTrue(r.moderatorLevel() < 0.05, "level " + r.moderatorLevel());
        assertEquals(total, r.moderator().kg() + r.dumpTank().kg(), 1e-6 * total);
        assertTrue(r.resetTrip());
        r.driveLevel(1);
        run(r, 120);
        assertTrue(r.moderatorLevel() > 0.05 && r.moderatorLevel() < 0.25, "pumped " + r.moderatorLevel());
        r.setPumpsPowered(false);
        double held = r.moderatorLevel();
        run(r, 30);
        assertEquals(held, r.moderatorLevel(), 1e-9);
    }

    /** Перегрузка на ходу машинами: полный цикл у канала, пучки сменились, из уплотнения ушло немного D₂O. */
    @Test
    void fuellingMachineRefuelsOnPower() {
        NpdReactor r = new NpdReactor();
        for (int i = 0; i < NpdReactor.CHANNELS; i++) assertTrue(r.loadChannel(i, BUNDLE_KG, 0.0045, 0.00005));
        r.hotStart(1);
        double before = r.evaluateReactivity();
        NpdFuellingMachine m = new NpdFuellingMachine();
        assertEquals(4, m.loadFresh(4, BUNDLE_KG, 0.00711, 0.000054));
        int ch = 0;
        assertTrue(m.start(r, ch, 4));
        for (int i = 0; i < 40 * 60 * 20 && m.phase() != NpdFuellingMachine.Phase.IDLE; i++) {
            m.step(NpdReactor.DT, r, true, true);
            r.step(NpdReactor.DT);
        }
        assertEquals(NpdFuellingMachine.Phase.IDLE, m.phase());
        assertEquals(4, r.refuelledBundles(), 1e-9);
        assertEquals(4, m.spentCount());
        assertEquals(0, m.fresh());
        assertTrue(m.leakedKg() > 1 && m.leakedKg() < 20, "leak " + m.leakedKg());
        assertEquals(NpdReactor.Trip.NONE, r.trip());
        r.setLevelMode(NpdReactor.LevelMode.MANUAL);
        assertTrue(r.evaluateReactivity() > before - 1e-3, "fresh fuel did not add reactivity");
        List<LightWaterCore.AssemblyFuel> out = m.takeSpent();
        assertEquals(4, out.size());
        assertFalse(out.get(0).failed());
    }

    /** Без подачи D₂O на головки машина стоит на опрессовке; без охлаждения выгоревшие в магазине перегреваются. */
    @Test
    void fuellingMachineNeedsHeadD2o() {
        NpdReactor r = loaded();
        r.hotStart(1);
        NpdFuellingMachine m = new NpdFuellingMachine();
        m.loadFresh(2, BUNDLE_KG, 0.00711, 0.000054);
        assertTrue(m.start(r, 5, 2));
        for (int i = 0; i < 20 * 60 * 20; i++) m.step(NpdReactor.DT, r, true, false);
        assertEquals(NpdFuellingMachine.Phase.PRESSURISE, m.phase());
        assertTrue(m.holdNoD2o());

        for (int i = 0; i < 40 * 60 * 20 && m.phase() != NpdFuellingMachine.Phase.IDLE; i++) m.step(NpdReactor.DT, r, true, true);
        assertEquals(2, m.spentCount());
        assertEquals(NpdFuellingMachine.Fault.NONE, m.fault());
        // магазин с выгоревшими без подачи D₂O
        for (int i = 0; i < 3 * 3600 * 20 && m.fault() == NpdFuellingMachine.Fault.NONE; i++) m.step(NpdReactor.DT, r, true, false);
        assertEquals(NpdFuellingMachine.Fault.SPENT_OVERHEAT, m.fault());
        assertTrue(m.takeSpent().get(0).failed());
        assertTrue(m.clearFault());
    }

    /** Решётка: 132 разных узла в круге ⌀3,4 м, встречное течение — примерно поровну с каждого торца. */
    @Test
    void channelLattice() {
        Set<String> seen = new HashSet<>();
        int north = 0;
        for (int ch = 0; ch < NpdReactor.CHANNELS; ch++) {
            assertTrue(seen.add(NpdChannels.label(ch)), "duplicate " + NpdChannels.label(ch));
            assertTrue(Math.hypot(NpdChannels.x(ch), NpdChannels.y(ch)) < 1.75, "outside " + ch);
            // мост и тележка перегрузочной машины достают каждый канал с пола реактора
            assertTrue(Math.abs(NpdChannels.y(ch)) <= NpdFuellingMachine.BRIDGE_REACH_M, "bridge cannot reach " + ch);
            assertTrue(Math.abs(NpdChannels.x(ch)) <= NpdFuellingMachine.CARRIAGE_REACH_M, "carriage cannot reach " + ch);
            if (NpdChannels.fuelledFromNorth(ch)) north++;
        }
        assertTrue(north >= 60 && north <= 72, "north " + north);
    }

    @Test
    void saveLoadRoundTrip() {
        NpdReactor r = loaded();
        r.hotStart(0.8);
        run(r, 20);
        NpdFuellingMachine m = new NpdFuellingMachine();
        m.loadFresh(3, BUNDLE_KG, 0.00711, 0.000054);
        m.start(r, 7, 3);
        for (int i = 0; i < 20 * 400; i++) m.step(NpdReactor.DT, r, true, true);
        TreeStateWriter w = new TreeStateWriter();
        r.save(w);
        m.save(w);
        NpdReactor r2 = new NpdReactor();
        NpdFuellingMachine m2 = new NpdFuellingMachine();
        TreeStateReader in = new TreeStateReader(w.root());
        r2.load(in);
        m2.load(in);
        assertEquals(r.power(), r2.power(), 1e-9);
        assertEquals(r.moderator().kg(), r2.moderator().kg(), 1e-6);
        assertEquals(r.levelMode(), r2.levelMode());
        assertEquals(m.phase(), m2.phase());
        assertEquals(m.spentCount(), m2.spentCount());
        assertEquals(m.x(), m2.x(), 1e-12);
        assertNotNull(m2.phase());
    }
}
