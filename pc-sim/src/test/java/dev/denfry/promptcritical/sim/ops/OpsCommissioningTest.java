package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.combustion.BoilerInputs;
import dev.denfry.promptcritical.sim.combustion.BoilerOutputs;
import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.combustion.FireTubeBoiler;
import dev.denfry.promptcritical.sim.combustion.FireTubeBoilerSpec;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimRandom;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.mining.MineSump;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Ввод в эксплуатацию (13 §13.6.6): T-13-26…33 в доменной части. */
class OpsCommissioningTest {
    private static final OpsConfig CFG = OpsConfig.defaults();
    private static final UUID ME = new UUID(0, 1);
    private static final double PW = 1.2e6;
    private static final SimId UNIT = new SimId(100), SHELL = new SimId(101), PSV = new SimId(102),
            GAUGE = new SimId(103), GLASS = new SimId(104);
    private static final UnitAssembly BOILER = UnitAssembly.boiler(UNIT, PW, SHELL, PSV, GAUGE, GLASS);
    private static final CheckInput.PressureTest HYDROTEST =
            new CheckInput.PressureTest(true, 293, CFG.testPressurePa(PW), CFG.hydrotestHoldPhysS());
    private static final CheckInput.TrialRun TRIAL = new CheckInput.TrialRun(CFG.trialRunS(), 0.8, false, true,
            new Baseline(0.71, 608, Double.NaN, 323, Double.NaN, 124));

    private static final class Log implements OpsEvents {
        final List<CommissioningState> states = new ArrayList<>();
        final List<Set<CommissioningCheck>> refused = new ArrayList<>();
        int unaccepted;

        @Override public void commissioningStateChanged(SimId unit, CommissioningState from, CommissioningState to) {
            states.add(to);
        }
        @Override public void startUnaccepted(SimId unit) { unaccepted++; }
        @Override public void startRefused(SimId unit, Set<CommissioningCheck> missing) { refused.add(missing); }
    }

    private static CommissioningDefect seam(double kw, double kb) {
        return new CommissioningDefect(CommissioningDefectKind.SEAM_WEEP, SHELL, kw, kb, false);
    }

    /** Котёл без дефектов, прошедший весь чек-лист. */
    private static CommissioningService accepted(OpsConfig cfg, Log log) {
        CommissioningService s = new CommissioningService(cfg, 7, log);
        s.register(BOILER, List.of(), 0);
        assertThat(s.perform(UNIT, HYDROTEST, ME, 1).passed()).isTrue();
        assertThat(s.perform(UNIT, new CheckInput.InstrumentCheck(), ME, 2).passed()).isTrue();
        assertThat(s.perform(UNIT, new CheckInput.ReliefValveSet(false), ME, 3).passed()).isTrue();
        assertThat(s.perform(UNIT, TRIAL, ME, 4).passed()).isTrue();
        return s;
    }

    /** T-13-26: p_исп = max(1,25·p_раб, p_раб + 0,3 МПа). */
    @Test
    void testPressure() {
        assertThat(CFG.testPressurePa(1.2e6)).isCloseTo(1.5e6, within(1.0));
        assertThat(CFG.testPressurePa(0.8e6)).isCloseTo(1.1e6, within(1.0));
    }

    /** T-13-27: шов k_w 1,2 при 1,5 МПа течёт — проверка не пройдена; после подчеканки пройдена. */
    @Test
    void seamWeepFoundByHydrotestAndCaulked() {
        CommissioningService s = new CommissioningService(CFG, 7, OpsEvents.NONE);
        s.register(BOILER, List.of(seam(1.2, 2.0)), 0);
        CheckResult r = s.perform(UNIT, HYDROTEST, ME, 1);
        assertThat(r.outcome()).isEqualTo(CheckResult.Outcome.FAILED);
        assertThat(r.found()).extracting(CommissioningDefect::kind).containsExactly(CommissioningDefectKind.SEAM_WEEP);
        assertThat(r.readings()).doesNotContainKey(CheckResult.READ_BURST);
        assertThat(s.state(UNIT)).contains(CommissioningState.TESTING);
        assertThat(s.defects(UNIT).getFirst().found()).isTrue();

        assertThat(s.caulk(UNIT, false)).isFalse();
        assertThat(s.caulk(UNIT, true)).isTrue();
        assertThat(s.perform(UNIT, HYDROTEST, ME, 2).passed()).isTrue();
        assertThat(s.burstFactor(UNIT, 4)).isEqualTo(4);
    }

    /** Условия гидроиспытания: топка, металл, давление, выдержка. */
    @Test
    void hydrotestRefusedUnderBadConditions() {
        CommissioningService s = new CommissioningService(CFG, 7, OpsEvents.NONE);
        s.register(BOILER, List.of(seam(1.1, 2.0)), 0);
        CheckResult r = s.perform(UNIT, new CheckInput.PressureTest(false, 400, 1.3e6, 60), ME, 1);
        assertThat(r.outcome()).isEqualTo(CheckResult.Outcome.REFUSED);
        assertThat(r.unmet()).containsExactlyInAnyOrder(CheckCondition.FIRE_LIT, CheckCondition.METAL_HOT,
                CheckCondition.PRESSURE_LOW, CheckCondition.HOLD_SHORT);
        assertThat(r.found()).isEmpty();
        assertThat(s.state(UNIT)).contains(CommissioningState.NEW);
    }

    /**
     * T-13-28 (домен): без испытаний шов k_b 2,0 и прикипевший ПК 1,9 уставки — обечайка рвётся при 2,40 МПа
     * раньше подрыва ПК (2,51 МПа); пуск непринятого котла в {@code ADVISORY} пишет {@code START_UNACCEPTED}.
     */
    @Test
    void untestedBoilerBurstsBeforeStuckValveLifts() {
        Log log = new Log();
        CommissioningService s = new CommissioningService(CFG, 7, log);
        s.register(BOILER, List.of(seam(1.1, 2.0),
                CommissioningDefect.of(CommissioningDefectKind.PSV_STUCK, PSV, 1.9)), 0);
        assertThat(s.requestStart(UNIT).allowed()).isTrue();
        assertThat(log.unaccepted).isEqualTo(1);

        FireTubeBoilerSpec base = FireTubeBoilerSpec.defaults();
        FireTubeBoilerSpec spec = base.withBurstPressureFactor(s.burstFactor(UNIT, base.burstPressureFactor()));
        double lift = s.reliefLiftPa(UNIT, base.reliefSetPressure());
        assertThat(lift).isCloseTo(2.508e6, within(1e3));
        assertThat(s.seamWeeping(UNIT, base.reliefSetPressure())).isTrue();

        FuelSpec coal = CoalAssay.typical(0.37, 0.213, 0.06, 0.016, 10).toFuel();
        FireTubeBoiler b = new FireTubeBoiler(spec, coal, 293);
        BoilerInputs in = new BoilerInputs();
        in.waterTempK = 490;
        in.waterLevelM = spec.normalLevelM();
        BoilerOutputs out = new BoilerOutputs();
        double p = 2.0e6;
        while (b.rupture() == BoilerRupture.NONE && p < lift) {
            p += 1e3;
            in.waterPressurePa = p;
            b.stepFast(0.05, in, out);
        }
        assertThat(b.rupture()).isEqualTo(BoilerRupture.SHELL_OVERPRESSURE);
        assertThat(p).isCloseTo(2.40e6, within(0.02e6)).isLessThan(lift);
    }

    /** T-13-29: у каждого дефекта есть проверка, и она в чек-листе каждой установки, где дефект бывает. */
    @Test
    void everyDefectHasACheckInEveryChecklist() {
        Set<CommissioningDefectKind> covered = EnumSet.noneOf(CommissioningDefectKind.class);
        for (UnitKind k : UnitKind.values()) {
            for (CommissioningDefectKind d : k.possibleDefects()) {
                assertThat(d.revealedBy()).isNotNull();
                assertThat(k.checklist()).as(k + " / " + d).contains(d.revealedBy());
            }
            covered.addAll(k.possibleDefects());
        }
        assertThat(covered).containsExactlyInAnyOrder(CommissioningDefectKind.values());

        // Разыгранные дефекты — только из перечня установки; ожидание ≈ 1,2 на котёл.
        int n = 4000, total = 0;
        for (int i = 0; i < n; i++) {
            List<CommissioningDefect> d = CommissioningService.rollDefects(BOILER,
                    SimRandom.of(i, UNIT, CommissioningService.RANDOM_STREAM), 1.0);
            for (CommissioningDefect x : d) assertThat(UnitKind.FIRE_TUBE_BOILER.possibleDefects()).contains(x.kind());
            total += d.size();
        }
        assertThat((double) total / n).isCloseTo(1.205, within(0.05));
    }

    /** T-13-30: одно зерно мира — одинаковые дефекты; {@code defect_rate} = 0 — ни одного. */
    @Test
    void defectsAreDeterministicAndScaledByRate() {
        long seed = 0;
        List<CommissioningDefect> first = null;
        for (; seed < 100; seed++) {
            first = new CommissioningService(CFG, seed, OpsEvents.NONE).register(BOILER, 0);
            if (!first.isEmpty()) break;
        }
        assertThat(first).isNotEmpty();
        assertThat(new CommissioningService(CFG, seed, OpsEvents.NONE).register(BOILER, 5)).isEqualTo(first);
        assertThat(new CommissioningService(CFG.withDefectRate(0), seed, OpsEvents.NONE).register(BOILER, 0))
                .isEmpty();
        assertThat(new CommissioningService(CFG.withCommissioning(CommissioningMode.OFF), seed, OpsEvents.NONE)
                .register(BOILER, 0)).isEmpty();
    }

    /** T-13-31 (домен): насос при обратном вращении — напор 0,40 H; {@code ROTATION_CHECK} находит реверс. */
    @Test
    void reversedPumpGivesFortyPercentHead() {
        MineSump.PumpSpec pump = new MineSump.PumpSpec(0.05, 120, 0.6, 1450);
        ImpellerWear wheel = new ImpellerWear();
        double h = pump.headAt(1450, 0.05, wheel.headFactor(true));
        assertThat(h / 120).isCloseTo(0.40, within(0.01));
        assertThat(pump.flowAt(1450, 100, wheel.headFactor(true))).isZero();
        assertThat(pump.flowAt(1450, 100, wheel.headFactor(false))).isPositive();

        SimId unit = new SimId(200), body = new SimId(201), drive = new SimId(202);
        UnitAssembly asm = new UnitAssembly(unit, UnitKind.MINE_DRAINAGE_PUMP,
                Map.of(UnitComponent.PUMP, body, UnitComponent.DRIVE, drive), 0, true, Set.of(), false);
        CommissioningService s = new CommissioningService(CFG, 7, OpsEvents.NONE);
        assertThat(s.register(asm, 0)).extracting(CommissioningDefect::kind)
                .containsExactlyInAnyOrder(CommissioningDefectKind.DRY_PUMP, CommissioningDefectKind.REVERSED_ROTATION);
        assertThat(s.primed(unit)).isFalse();

        CheckResult r = s.perform(unit, new CheckInput.RotationCheck(true), ME, 1);
        assertThat(r.outcome()).isEqualTo(CheckResult.Outcome.FAILED);
        assertThat(r.found()).extracting(CommissioningDefect::kind)
                .containsExactly(CommissioningDefectKind.REVERSED_ROTATION);
        assertThat(s.perform(unit, new CheckInput.RotationCheck(false), ME, 2).passed()).isTrue();
        assertThat(s.perform(unit, new CheckInput.Priming(false), ME, 3).unmet())
                .containsExactly(CheckCondition.UNIT_RUNNING);
        assertThat(s.perform(unit, new CheckInput.Priming(true), ME, 4).passed()).isTrue();
        assertThat(s.primed(unit)).isTrue();
        assertThat(s.defects(unit)).isEmpty();
        s.losePrime(unit);
        assertThat(s.primed(unit)).isFalse();
    }

    /** T-13-32 (домен): замена ПК у принятого котла — {@code MODIFIED}, сброшена только {@code RELIEF_VALVE_SET}. */
    @Test
    void replacingReliefValveResetsOnlyItsCheck() {
        Log log = new Log();
        CommissioningService s = accepted(CFG, log);
        assertThat(s.state(UNIT)).contains(CommissioningState.ACCEPTED);
        UnitPassport passport = s.passport(UNIT).orElseThrow();
        assertThat(passport.baseline().flueGasK()).isEqualTo(608);
        assertThat(passport.acceptedBy()).isEqualTo(ME);

        SimId newPsv = new SimId(150);
        s.onModified(BOILER.withComponent(UnitComponent.RELIEF_VALVE, newPsv), Set.of(newPsv), ME, 10);
        assertThat(s.state(UNIT)).contains(CommissioningState.MODIFIED);
        assertThat(s.passedChecks(UNIT)).containsExactlyInAnyOrder(CommissioningCheck.PRESSURE_TEST,
                CommissioningCheck.INSTRUMENT_CHECK, CommissioningCheck.TRIAL_RUN);
        assertThat(s.defects(UNIT)).allMatch(d -> d.component().equals(newPsv));

        assertThat(s.perform(UNIT, new CheckInput.ReliefValveSet(false), ME, 11).passed()).isTrue();
        assertThat(s.state(UNIT)).contains(CommissioningState.ACCEPTED);
        assertThat(log.states).containsExactly(CommissioningState.TESTING, CommissioningState.ACCEPTED,
                CommissioningState.MODIFIED, CommissioningState.ACCEPTED);
        assertThat(s.passport(UNIT).orElseThrow().history()).extracting(PassportEntry::kind).containsExactly(
                PassportEntry.Kind.ACCEPTED, PassportEntry.Kind.REPLACEMENT, PassportEntry.Kind.MOC,
                PassportEntry.Kind.REACCEPTED);
    }

    /** T-13-33 (домен): {@code ENFORCED} — розжиг непринятого котла отклонён с перечнем; {@code ADVISORY} — да. */
    @Test
    void enforcedModeBlocksStart() {
        Log log = new Log();
        CommissioningService s = new CommissioningService(CFG.withCommissioning(CommissioningMode.ENFORCED), 7, log);
        s.register(BOILER, List.of(), 0);
        StartPermission p = s.requestStart(UNIT);
        assertThat(p.allowed()).isFalse();
        assertThat(log.refused.getFirst()).containsExactlyInAnyOrder(CommissioningCheck.PRESSURE_TEST,
                CommissioningCheck.INSTRUMENT_CHECK, CommissioningCheck.RELIEF_VALVE_SET, CommissioningCheck.TRIAL_RUN);

        s.perform(UNIT, HYDROTEST, ME, 1);
        s.perform(UNIT, new CheckInput.InstrumentCheck(), ME, 2);
        s.perform(UNIT, new CheckInput.ReliefValveSet(false), ME, 3);
        p = s.requestStart(UNIT); // пробный пуск без приёмки разрешён
        assertThat(p.allowed()).isTrue();
        assertThat(p.accepted()).isFalse();
        assertThat(log.unaccepted).isEqualTo(1);

        Log advisory = new Log();
        CommissioningService a = new CommissioningService(CFG, 7, advisory);
        a.register(BOILER, List.of(), 0);
        assertThat(a.requestStart(UNIT).allowed()).isTrue();
        assertThat(advisory.unaccepted).isEqualTo(1);
        assertThat(advisory.refused).isEmpty();
    }

    /** Регулируемые дефекты исправляются в ходе проверки; ПК без контрольного манометра не регулируют. */
    @Test
    void adjustableDefectsFixedDuringChecks() {
        CommissioningService s = new CommissioningService(CFG, 7, OpsEvents.NONE);
        s.register(BOILER, List.of(CommissioningDefect.of(CommissioningDefectKind.PSV_OFFSET, PSV, 0.1),
                CommissioningDefect.of(CommissioningDefectKind.GAUGE_OFFSET, GAUGE, 0.05),
                CommissioningDefect.of(CommissioningDefectKind.GAUGE_GLASS_BLOCKED, GLASS, 1)), 0);
        assertThat(s.gaugeReading(UNIT, 1.2e6, 2.5e6)).isCloseTo(1.075e6, within(1.0));
        assertThat(s.perform(UNIT, new CheckInput.ReliefValveSet(false), ME, 1).unmet())
                .containsExactly(CheckCondition.NO_CONTROL_GAUGE);
        assertThat(s.perform(UNIT, TRIAL, ME, 1).unmet()).containsExactly(CheckCondition.PREREQUISITES);

        CheckResult inst = s.perform(UNIT, new CheckInput.InstrumentCheck(), ME, 2);
        assertThat(inst.passed()).isTrue();
        assertThat(inst.readings().get(CheckResult.READ_GAUGE_OFFSET)).isEqualTo(0.05);
        assertThat(inst.found()).hasSize(2);
        CheckResult psv = s.perform(UNIT, new CheckInput.ReliefValveSet(false), ME, 3);
        assertThat(psv.readings().get(CheckResult.READ_LIFT)).isCloseTo(1.1 * 1.32e6, within(1.0));
        assertThat(s.defects(UNIT)).isEmpty();
        assertThat(s.reliefLiftPa(UNIT, 1.32e6)).isEqualTo(1.32e6);

        // Сухой катаракт виден по качанию оборотов при сбросе нагрузки.
        SimId engine = new SimId(300), cyl = new SimId(301), gov = new SimId(302);
        UnitAssembly asm = new UnitAssembly(engine, UnitKind.STEAM_ENGINE,
                Map.of(UnitComponent.CYLINDER, cyl, UnitComponent.GOVERNOR, gov), 1.2e6, false,
                Set.of(UnitComponent.CYLINDER), true);
        s.register(asm, List.of(CommissioningDefect.of(CommissioningDefectKind.CASTING_POROSITY, cyl, 1),
                CommissioningDefect.of(CommissioningDefectKind.DASHPOT_DRY, gov, 1)), 0);
        assertThat(s.perform(engine, HYDROTEST, ME, 1).outcome()).isEqualTo(CheckResult.Outcome.FAILED);
        SimId cyl2 = new SimId(303);
        s.onModified(asm.withComponent(UnitComponent.CYLINDER, cyl2), Set.of(cyl2), ME, 2);
        assertThat(s.perform(engine, HYDROTEST, ME, 3).passed()).isTrue();
        CheckResult trial = s.perform(engine, TRIAL, ME, 4);
        assertThat(trial.passed()).isTrue();
        assertThat(trial.readings().get(CheckResult.READ_HUNTING)).isEqualTo(1.0);
        assertThat(s.state(engine)).contains(CommissioningState.ACCEPTED);
    }
}
