package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Эксплуатация в мире (этап 11 M3): регистрация установок, пробный пуск, замена компонента, износ, сохранение. */
class OpsSubsystemTest {
    private static final double PW = 1.2e6;
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world(OpsConfig cfg) {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new OpsSubsystem(cfg, 7));
        worlds.add(w);
        return w;
    }

    private static OpsSubsystem ops(SimulationWorld w) {
        return w.subsystem(OpsSubsystem.class);
    }

    private static SimId device(SimulationWorld w, String type, int x) {
        return w.topology().addDevice(type, new GridPos(x, 64, 0));
    }

    private static void tick(SimulationWorld w, int n) {
        for (int i = 0; i < n; i++) w.tick(ExternalInputs.NONE);
    }

    /** Котёл без дефектов, прошедший все проверки, кроме пробного пуска. */
    private static void passStaticChecks(OpsSubsystem ops, SimId b) {
        double test = ops.config().testPressurePa(PW);
        assertThat(ops.perform(b, new CheckInput.PressureTest(true, 300, test, 600), null).passed()).isTrue();
        assertThat(ops.perform(b, new CheckInput.InstrumentCheck(), null).passed()).isTrue();
        assertThat(ops.perform(b, new CheckInput.ReliefValveSet(false), null).passed()).isTrue();
    }

    /** Пробный пуск: наработка с нагрузкой и сброс нагрузки — приёмка с паспортом; срабатывание защиты — провал. */
    @Test
    void trialRunAcceptsAfterRunAndLoadRejection() {
        SimulationWorld w = world(OpsConfig.defaults().withDefectRate(0).withTrialRunS(10));
        OpsSubsystem ops = ops(w);
        SimId b = device(w, "promptcritical:fire_tube_boiler", 0);
        ops.ensure(b, UnitKind.FIRE_TUBE_BOILER, PW, false, Set.of(), false);
        assertThat(ops.observeTrial(b, 1, true, 1, false, Baseline.NONE)).isNull();
        assertThat(ops.unit(b).trialRunS()).isZero();               // прочие проверки не пройдены
        passStaticChecks(ops, b);
        Baseline base = new Baseline(0.7, 600, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        for (int i = 0; i < 5; i++) assertThat(ops.observeTrial(b, 1, true, 0.9, false, base)).isNull();
        assertThat(ops.observeTrial(b, 1, true, 0.9, true, base).passed()).isFalse();
        assertThat(ops.log(b)).extracting(OpsLogEntry::kind).contains(OpsLogEntry.Kind.CHECK_FAILED);
        assertThat(ops.unit(b).trialRunS()).isZero();
        CheckResult r = null;
        for (int i = 0; i < 12 && r == null; i++) r = ops.observeTrial(b, 1, true, 0.9, false, base);
        assertThat(r).isNull();                                     // без сброса нагрузки не засчитан
        r = ops.observeTrial(b, 1, true, 0.1, false, base);         // наработка набрана — сброс завершает пуск
        assertThat(r.passed()).isTrue();
        assertThat(ops.commissioning().state(b)).contains(CommissioningState.ACCEPTED);
        assertThat(ops.commissioning().passport(b).orElseThrow().baseline()).isEqualTo(base);
    }

    /** T-13-32 через подсистему: замена ПК принятого котла — MODIFIED, сброшена только RELIEF_VALVE_SET. */
    @Test
    void replacingReliefValveResetsOnlyItsCheck() {
        SimulationWorld w = world(OpsConfig.defaults().withDefectRate(0).withTrialRunS(1));
        OpsSubsystem ops = ops(w);
        SimId b = device(w, "promptcritical:fire_tube_boiler", 0);
        ops.ensure(b, UnitKind.FIRE_TUBE_BOILER, PW, false, Set.of(), false);
        passStaticChecks(ops, b);
        ops.observeTrial(b, 2, true, 1, false, Baseline.NONE);
        ops.observeTrial(b, 1, false, 0, false, Baseline.NONE);
        assertThat(ops.commissioning().state(b)).contains(CommissioningState.ACCEPTED);
        SimId oldPsv = ops.commissioning().assembly(b).orElseThrow().components().get(UnitComponent.RELIEF_VALVE);
        ops.replaceComponent(b, UnitComponent.RELIEF_VALVE, null);
        assertThat(ops.commissioning().state(b)).contains(CommissioningState.MODIFIED);
        assertThat(ops.commissioning().passedChecks(b)).containsExactlyInAnyOrder(CommissioningCheck.PRESSURE_TEST,
                CommissioningCheck.INSTRUMENT_CHECK, CommissioningCheck.TRIAL_RUN);
        assertThat(ops.commissioning().assembly(b).orElseThrow().components().get(UnitComponent.RELIEF_VALVE))
                .isNotEqualTo(oldPsv);
        assertThat(ops.perform(b, new CheckInput.ReliefValveSet(false), null).passed()).isTrue();
        assertThat(ops.commissioning().state(b)).contains(CommissioningState.ACCEPTED);
    }

    /** Подшипник изнашивается только в работе, заплавляется (запись SEIZED), новый вкладыш снимает износ. */
    @Test
    void bearingWearsOnlyWhileRunningAndIsReplacedFromThePanel() {
        SimulationWorld w = world(OpsConfig.defaults());
        OpsSubsystem ops = ops(w);
        SimId fan = device(w, "promptcritical:main_mine_fan", 0);
        OpsSubsystem.Unit u = ops.ensure(fan, UnitKind.MAIN_MINE_FAN, 0, false, Set.of(), false);
        u.wear().bearing().setDamage(0.99995);
        tick(w, 400);
        assertThat(u.seized()).isFalse();
        u.setRunning(true, false, Double.NaN);
        tick(w, 400);
        assertThat(u.seized()).isTrue();
        assertThat(ops.log(fan)).extracting(OpsLogEntry::kind).contains(OpsLogEntry.Kind.SEIZED);
        assertThat(u.panel().faults().get(0).kind()).isEqualTo(MaintenanceFaultKind.BEARING_WEAR);
        ops.repair(fan, 0, null, false);
        assertThat(u.seized()).isFalse();
        assertThat(u.wear().bearing().damage()).isZero();
        assertThat(ops.log(fan)).extracting(OpsLogEntry::kind).contains(OpsLogEntry.Kind.MAINTENANCE_DONE);
    }

    /** Сохранение: дефекты, состояние, пройденные проверки, износ и замеры панели переживают перезагрузку. */
    @Test
    void saveAndLoadKeepCommissioningAndWear() {
        SimulationWorld w = world(OpsConfig.defaults());
        OpsSubsystem ops = ops(w);
        SimId b = device(w, "promptcritical:fire_tube_boiler", 0);
        SimId pump = device(w, "promptcritical:mine_drainage_pump", 4);
        ops.ensure(b, UnitKind.FIRE_TUBE_BOILER, PW, false, Set.of(), false);
        OpsSubsystem.Unit p = ops.ensure(pump, UnitKind.MINE_DRAINAGE_PUMP, 0, true, Set.of(), false);
        ops.perform(b, new CheckInput.InstrumentCheck(), null);
        ops.perform(pump, new CheckInput.Priming(true), null);
        p.wear().bearing().setDamage(0.85);
        p.wear().impeller().setErosion(0.3);
        assertThat(ops.measure(pump, 0, Probe.STETHOSCOPE)).isTrue();
        double[] scale = {0.4};
        FaultSource fouling = FaultSource.of(MaintenanceFaultKind.HX_FOULING, () -> scale[0], () -> scale[0] = 0);
        ops.attach(b, fouling);
        tick(w, 50);
        MutableStateTree saved = w.saveTree();
        SimulationWorld c = world(OpsConfig.defaults());
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        OpsSubsystem ops2 = ops(c);
        assertThat(ops2.commissioning().defects(b)).isEqualTo(ops.commissioning().defects(b));
        assertThat(ops2.commissioning().state(b)).isEqualTo(ops.commissioning().state(b));
        assertThat(ops2.commissioning().passedChecks(pump)).containsExactly(CommissioningCheck.PRIMING);
        assertThat(ops2.commissioning().primed(pump)).isTrue();
        assertThat(ops2.commissioning().defects(pump)).extracting(CommissioningDefect::kind)
                .containsExactly(CommissioningDefectKind.REVERSED_ROTATION);
        OpsSubsystem.Unit p2 = ops2.unit(pump);
        assertThat(p2.wear().bearing().damage()).isEqualTo(0.85);
        assertThat(p2.wear().bearing().lifeFactor()).isEqualTo(p.wear().bearing().lifeFactor());
        assertThat(p2.wear().impeller().erosion()).isEqualTo(0.3);
        assertThat(p2.panel().faults()).isEqualTo(p.panel().faults());
        // строка накипи от биндинга сохраняется и после загрузки снова получает владельца на том же месте
        assertThat(c.stateHash()).isEqualTo(w.stateHash());
        ops2.attach(b, fouling);
        assertThat(ops2.unit(b).panel().size()).isEqualTo(ops.unit(b).panel().size());
        assertThat(ops2.unit(b).panel().source(ops2.unit(b).panel().size() - 1)).isSameAs(fouling);
    }
}
