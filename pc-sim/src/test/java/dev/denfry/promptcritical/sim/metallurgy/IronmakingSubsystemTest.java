package dev.denfry.promptcritical.sim.metallurgy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Коксохим и домна мира (этап 10 M3): регулятор разогрева батареи на топках, газосборник, перенос камер при
 * перестройке, бункер колошника и дутьё через воздухонагреватели, сохранение.
 */
class IronmakingSubsystemTest {
    private static final CoalAssay BLEND = CoalAssay.typical(0.27, 0.08, 0.08, 0.008, 17);
    private static final CoalAssay COKE = CokeBattery.cokeAssay(0.11, 0.006, 85, 0.04);
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();
    private int x;

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new IronmakingSubsystem());
        worlds.add(w);
        return w;
    }

    private <U extends IronmakingUnit> U unit(SimulationWorld w, String type, Class<U> cls) {
        SimId id = w.topology().addDevice(type, new GridPos(x++, 64, 0));
        return cls.cast(w.subsystem(IronmakingSubsystem.class).unit(w.topology(), id));
    }

    private static void run(SimulationWorld w, double realSeconds, Runnable perTick) {
        int n = (int) Math.round(realSeconds * 20);
        for (int i = 0; i < n; i++) {
            perTick.run();
            w.tick(ExternalInputs.NONE);
        }
    }

    /** Разогрев на топках по уставке 5 К/ч — динас цел; 20 К/ч топки не тянут, но и 9 К/ч рвут кладку. */
    @Test
    void batteryHeatsUpOnFireboxesAtTheRamp() {
        SimulationWorld w = world();
        CokeBatteryUnit slow = unit(w, IronmakingSubsystem.COKE_OVEN, CokeBatteryUnit.class);
        CokeBatteryUnit fast = unit(w, IronmakingSubsystem.COKE_OVEN, CokeBatteryUnit.class);
        IronmakingConfig cfg = IronmakingConfig.defaults();
        slow.form(cfg, 3, 6000, new int[] {-1, -1, -1});
        fast.form(cfg, 3, 6000, new int[] {-1, -1, -1});
        fast.setSetpoint(CokeBattery.REF_WALL_K, 20);
        double days = 9 * 86_400 / 72;
        run(w, days, () -> {
            if (slow.fireboxHungry()) slow.addFireboxHeat(2.5e9);
            if (fast.fireboxHungry()) fast.addFireboxHeat(2.5e9);
        });
        assertThat(slow.battery().heatedUp()).isTrue();
        assertThat(slow.battery().refractoryDamage()).isZero();
        assertThat(slow.battery().wallTempK()).isGreaterThan(1290);
        assertThat(fast.battery().refractoryFailed()).isTrue();
        assertThat(fast.log().poll().kind()).isEqualTo(IronmakingLog.Kind.REFRACTORY_FAILED);
    }

    /** Газосборник: без отсоса — свечи; отсос по характеристике; долгое разрежение — взрыв. */
    @Test
    void gasMainPressureFollowsTheExhausterCurve() {
        GasMain m = new GasMain();
        m.update(0.4, 0, 0, 10, 0.05);
        assertThat(m.pressurePa()).isEqualTo(GasMain.BLEEDER_PA);
        assertThat(m.ventedNm3S()).isEqualTo(0.4);

        double s = Math.sqrt(0.44);
        m.update(0.4, GasMain.EXHAUSTER_NM3S * s, s, 10, 0.05);
        assertThat(m.pressurePa()).isCloseTo(100, within(1e-6));
        assertThat(m.deliveredNm3S()).isEqualTo(0.4);

        m.update(0.4, GasMain.EXHAUSTER_NM3S * s, s, 0.1, 0.05);
        assertThat(m.pressurePa()).isEqualTo(GasMain.BLEEDER_PA);
        assertThat(m.deliveredNm3S()).isCloseTo(0.1, within(1e-12));

        for (int i = 0; i < 599; i++) m.update(0.4, GasMain.EXHAUSTER_NM3S, 1, 10, 0.05);
        assertThat(m.pressurePa()).isCloseTo(-180, within(1e-6));
        assertThat(m.takeExplosion()).isFalse();
        m.update(0.4, GasMain.EXHAUSTER_NM3S, 1, 10, 0.05);
        assertThat(m.takeExplosion()).isTrue();
    }

    /** Перестройка: камеры переезжают по карте вместе с шихтой, простенки сохраняют температуру. */
    @Test
    void reformingCarriesOvensAcross() {
        SimulationWorld w = world();
        CokeBatteryUnit u = unit(w, IronmakingSubsystem.COKE_OVEN, CokeBatteryUnit.class);
        IronmakingConfig cfg = IronmakingConfig.defaults();
        u.form(cfg, 4, 6000, new int[] {-1, -1, -1, -1});
        u.battery().charge(1, 6000, BLEND);
        u.battery().charge(3, 2000, BLEND);
        CokeBattery before = u.battery();
        u.form(cfg, 4, 6000, new int[] {0, 1, 2, 3});
        assertThat(u.battery()).isSameAs(before);
        u.form(cfg, 5, 6000, new int[] {-1, 0, 1, 2, 3});
        assertThat(u.battery().oven(2)).isEqualTo(OvenState.COKING);
        assertThat(u.battery().oven(4)).isEqualTo(OvenState.CHARGING);
        assertThat(u.battery().chargeKg(4)).isEqualTo(2000);
        assertThat(u.battery().oven(1)).isEqualTo(OvenState.EMPTY);
        assertThat(u.battery().wallTempK()).isEqualTo(before.wallTempK());
    }

    /** Бункер колошника складывает колоши по рудной нагрузке; домна сама гоняет кауперы и держит дутьё. */
    @Test
    void furnaceChargesLayersAndCyclesStoves() {
        SimulationWorld w = world();
        BlastFurnaceUnit bf = unit(w, IronmakingSubsystem.BLAST_FURNACE, BlastFurnaceUnit.class);
        List<HotBlastStoveUnit> stoves = new ArrayList<>();
        for (int i = 0; i < 3; i++) stoves.add(unit(w, IronmakingSubsystem.HOT_BLAST_STOVE, HotBlastStoveUnit.class));
        bf.form(IronmakingConfig.defaults(), BlastFurnaceSpec.of(99, 8));
        double coke = bf.cokeLayerKg();
        bf.addCoke(coke * 3, COKE);
        bf.addOre(coke * 3.5 * 2, 0.58, 0.08, 0.10);
        run(w, 0.05, () -> {});
        assertThat(bf.furnace().layerCount()).isEqualTo(1);
        run(w, 0.1, () -> {});
        assertThat(bf.furnace().layerCount()).isEqualTo(2);
        assertThat(bf.hopperCokeKg()).isCloseTo(coke, within(1e-6));
        bf.setBurden(3.5, 0, true);
        run(w, 0.05, () -> {});
        assertThat(bf.furnace().layerCount()).isEqualTo(3);
        assertThat(bf.hopperCokeKg()).isCloseTo(0, within(1e-6));

        double blower = 2 * BlastFurnaceUnit.BLOWER_NM3S;
        run(w, 4 * 3600 / 72.0, () -> {
            for (HotBlastStoveUnit s : stoves) {
                double want = s.gasDemandNm3S();
                s.setGasSupply(want > 0 ? ProcessGas.COKE_OVEN : null, want);
            }
            bf.couple(blower, stoves, true);
        });
        long onBlast = stoves.stream().filter(s -> s.stove().mode() == StoveMode.ON_BLAST).count();
        assertThat(onBlast).isEqualTo(1);
        assertThat(bf.blastK()).isGreaterThan(BlastFurnaceUnit.DEFAULT_HOT_BLAST_K - 30);
        assertThat(bf.blastNm3S()).isEqualTo(blower);
    }

    /** Сохранение: батарея с камерами и домна со столбом шихты переживают перезагрузку. */
    @Test
    void saveAndLoadKeepTheAggregates() {
        SimulationWorld w = world();
        CokeBatteryUnit cb = unit(w, IronmakingSubsystem.COKE_OVEN, CokeBatteryUnit.class);
        BlastFurnaceUnit bf = unit(w, IronmakingSubsystem.BLAST_FURNACE, BlastFurnaceUnit.class);
        HotBlastStoveUnit st = unit(w, IronmakingSubsystem.HOT_BLAST_STOVE, HotBlastStoveUnit.class);
        BakingFurnaceUnit bk = unit(w, IronmakingSubsystem.BAKING_FURNACE, BakingFurnaceUnit.class);
        cb.form(IronmakingConfig.defaults(), 3, 5000, new int[] {-1, -1, -1});
        cb.battery().charge(0, 5000, BLEND);
        cb.setSetpoint(1400, 12);
        bf.form(IronmakingConfig.defaults(), BlastFurnaceSpec.of(99, 8));
        bf.addCoke(bf.cokeLayerKg() * 2, COKE);
        bf.addOre(bf.cokeLayerKg() * 3.5 * 2, 0.58, 0.08, 0.10);
        st.setValve(0.9);
        st.stove().setMode(StoveMode.ON_GAS);
        bk.setSetpoint(1000, 6);
        bk.furnace().load(ElectrodeExtruder.extrude(75, 25, 433.15, 0.2));
        run(w, 2, () -> {});

        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        IronmakingSubsystem sub = c.subsystem(IronmakingSubsystem.class);
        List<IronmakingUnit> all = new ArrayList<>();
        for (int i = 0; i < 4; i++) all.add(sub.unit(c.topology(), w.topology().deviceAt(i)));
        CokeBatteryUnit cb2 = (CokeBatteryUnit) all.get(0);
        BlastFurnaceUnit bf2 = (BlastFurnaceUnit) all.get(1);
        HotBlastStoveUnit st2 = (HotBlastStoveUnit) all.get(2);
        BakingFurnaceUnit bk2 = (BakingFurnaceUnit) all.get(3);
        assertThat(cb2.battery().oven(0)).isEqualTo(OvenState.COKING);
        assertThat(cb2.battery().progress(0)).isEqualTo(cb.battery().progress(0));
        assertThat(cb2.setpointK()).isEqualTo(1400);
        assertThat(bf2.furnace().layerCount()).isEqualTo(2);
        assertThat(bf2.furnace().stockVolumeM3()).isEqualTo(bf.furnace().stockVolumeM3());
        assertThat(st2.valveNm3S()).isEqualTo(0.9);
        assertThat(st2.stove().mode()).isEqualTo(StoveMode.ON_GAS);
        assertThat(bk2.furnace().loaded()).isEqualTo(1);
        assertThat(bk2.rampKH()).isEqualTo(6);
    }
}
