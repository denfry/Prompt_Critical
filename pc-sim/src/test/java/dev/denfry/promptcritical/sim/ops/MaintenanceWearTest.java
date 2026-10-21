package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.SimId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Износ среза I0 и панель обслуживания (13 §13.6.5, 13 §13.6.7, 04a §4a.7). */
class MaintenanceWearTest {
    private static final OpsConfig CFG = OpsConfig.defaults();
    private static final double K_SLOW = 72;
    private static final SimId UNIT = new SimId(1);

    private static final class Log implements OpsEvents {
        final List<int[]> events = new ArrayList<>();

        @Override public void maintenanceRequired(SimId unit, MaintenanceFaultKind kind, int severity, int prev) {
            events.add(new int[] {kind.ordinal(), severity, prev});
        }
    }

    /** Реальные секунды до D_b ≥ x при шаге SLOW 1 с реального времени. */
    private static double realSecondsTo(PlainBearing b, double target, double aLub) {
        double t = 0;
        while (b.damage() < target && t < 1e6) {
            b.step(K_SLOW, CFG.bearingWearMultiplier(), aLub);
            t += 1;
        }
        return t;
    }

    /** T-13-34: L10h 30 000 ч, u_b 1, k_wear 20, K_slow 72, tallow — отказ через 12,5 ч, стадия 1 через 7,5 ч. */
    @Test
    void bearingLifeInRealTime() {
        PlainBearing b = new PlainBearing(PlainBearing.L10H_I0, 1);
        double aLub = new Lubrication(CFG.tallowIntervalH(), Lubricant.TALLOW).factor(false);
        assertThat(realSecondsTo(b, 0.6, aLub) / 3600).isCloseTo(7.5, within(0.1));
        assertThat(b.stage()).isEqualTo(1);
        assertThat(realSecondsTo(b, 1.0, aLub) / 3600).isCloseTo(5.0, within(0.1));
        assertThat(b.seized()).isTrue();
        assertThat(b.step(K_SLOW, 20, aLub)).isFalse();
        b.replace(1.3);
        assertThat(b.damage()).isZero();
        assertThat(b.seized()).isFalse();
    }

    /** T-4a-14: Вейбулл β = 1,5 — 10 % отказов до L10, медиана/L10 = 3,51. */
    @Test
    void weibullLifeFactor() {
        SplittableRandom rnd = new SplittableRandom(42);
        int n = 100_000, early = 0;
        double[] u = new double[n];
        for (int i = 0; i < n; i++) {
            u[i] = Weibull.bearingLifeFactor(rnd);
            if (u[i] < 1) early++;
        }
        Arrays.sort(u);
        assertThat((double) early / n).isCloseTo(0.10, within(0.003));
        assertThat(u[n / 2]).isCloseTo(3.51, within(0.05));
        assertThat(Weibull.scaleForL10(1.5)).isCloseTo(4.483, within(0.001));
    }

    /** Стадии 3–4 греют корпус: рука чувствует «тёплый / горячий / обжигает». */
    @Test
    void bearingStagesHeatTheHousing() {
        PlainBearing b = new PlainBearing(30_000, 1);
        double amb = 293;
        assertThat(PlainBearing.feel(b.housingTempK(amb))).isEqualTo(PlainBearing.HandFeel.WARM);
        realSecondsTo(b, 0.97, 1);
        assertThat(b.stage()).isEqualTo(4);
        assertThat(b.housingTempK(amb)).isCloseTo(amb + 40, within(0.5));
        assertThat(PlainBearing.feel(b.housingTempK(amb))).isEqualTo(PlainBearing.HandFeel.HOT);
        realSecondsTo(b, 0.995, 1);
        assertThat(PlainBearing.feel(b.housingTempK(amb))).isEqualTo(PlainBearing.HandFeel.SCALDING);
    }

    /** Смазка: интервал tallow 500 ч, затем a_lub 0,05; сухой насос — 0,05 сразу, сальник ×10. */
    @Test
    void lubricationAndDryRunning() {
        Lubrication l = new Lubrication(CFG.tallowIntervalH(), Lubricant.TALLOW);
        assertThat(l.factor(false)).isEqualTo(0.6);
        assertThat(l.factor(true)).isEqualTo(0.05);
        l.step(400 * 3600);
        assertThat(l.remaining()).isCloseTo(0.2, within(1e-9));
        assertThat(MaintenanceFaultKind.LUBRICATION.severity(l.value())).isEqualTo(3);
        l.step(100 * 3600);
        assertThat(l.factor(false)).isEqualTo(0.05);
        l.apply(Lubricant.LUBE_OIL);
        assertThat(l.factor(false)).isEqualTo(1.0);
        assertThat(l.intervalH()).isEqualTo(2000);

        GlandPacking wet = new GlandPacking(), dry = new GlandPacking();
        wet.step(300 * 3600, false);
        dry.step(300 * 3600, true);
        assertThat(dry.wear()).isCloseTo(10 * wet.wear(), within(1e-9));
        assertThat(dry.tighten()).isTrue();
        assertThat(dry.wear()).isCloseTo(0.5, within(1e-9));
        dry.tighten();
        dry.tighten();
        assertThat(dry.tighten()).isFalse();
        dry.replace();
        assertThat(dry.tightenings()).isZero();
    }

    /** Кислая шахтная вода разъедает колесо втрое быстрее. */
    @Test
    void impellerErodesFasterInAcidWater() {
        ImpellerWear acid = new ImpellerWear(), neutral = new ImpellerWear();
        acid.step(4000 * 3600, 3.0);
        neutral.step(4000 * 3600, 7.0);
        assertThat(neutral.erosion()).isCloseTo(0.1, within(1e-9));
        assertThat(acid.erosion()).isCloseTo(0.3, within(1e-9));
        assertThat(acid.headFactor(false)).isCloseTo(0.7, within(1e-9));
        assertThat(MaintenanceFaultKind.IMPELLER_EROSION.severity(acid.value())).isEqualTo(2);
    }

    /** Пороги и нормировка каталога: растущие, падающие, логарифм изоляции. */
    @Test
    void catalogScales() {
        assertThat(MaintenanceFaultKind.values()).hasSize(21);
        MaintenanceFaultKind b = MaintenanceFaultKind.BEARING_WEAR;
        assertThat(b.severity(0.49)).isZero();
        assertThat(b.severity(0.7)).isEqualTo(2);
        assertThat(b.normalized(0.45)).isCloseTo(0.5, within(1e-9));
        assertThat(MaintenanceFaultKind.LUBRICATION.normalized(0.6)).isCloseTo(0.5, within(1e-9));
        MaintenanceFaultKind ins = MaintenanceFaultKind.INSULATION_AGING;
        assertThat(ins.severity(50)).isEqualTo(1);
        assertThat(ins.severity(0.5)).isEqualTo(3);
        assertThat(ins.normalized(31.62)).isCloseTo(0.5, within(1e-3));
        for (MaintenanceFaultKind k : MaintenanceFaultKind.values())
            for (int s = 1; s <= 3; s++) assertThat(k.severity(k.threshold(s))).as(k + " " + s).isEqualTo(s);
    }

    /**
     * Панель: η_eff и λ_trip по истинному износу; подшипник виден только замером (стетоскоп — со стадии 1, рука —
     * со стадии 3), накипь — только при паспорте; событие при росте тяжести с дебаунсом 10 мин.
     */
    @Test
    void panelShowsOnlyWhatIsDiagnosable() {
        Log log = new Log();
        MaintenancePanel panel = new MaintenancePanel(UNIT, CFG, log);
        PlainBearing bearing = new PlainBearing(30_000, 1);
        double[] scaleMm = {0};
        int ib = panel.add(bearing);
        int is = panel.add(FaultSource.of(MaintenanceFaultKind.HX_FOULING, () -> scaleMm[0], () -> scaleMm[0] = 0));

        realSecondsTo(bearing, 0.55, 1);
        scaleMm[0] = 0.6;
        panel.update(0);
        assertThat(panel.faults()).allMatch(f -> !f.known() && f.severity() == 0);
        assertThat(panel.maxSeverity()).isZero();
        assertThat(log.events).isEmpty();

        // η_eff = η·(1 − 0,03·0,61)(1 − 0,10·0,6); λ = λ₀(1 + 5·0,61²).
        double sb = bearing.damage() / 0.9;
        assertThat(panel.efficiency(0.7)).isCloseTo(0.7 * (1 - 0.03 * sb) * (1 - 0.10 * 0.6), within(1e-9));
        assertThat(panel.tripRate(1e-4)).isCloseTo(1e-4 * (1 + 5 * sb * sb), within(1e-12));

        assertThat(panel.measure(ib, Probe.HAND, 10)).isTrue();
        assertThat(panel.faults().get(ib).known()).isTrue();
        assertThat(panel.faults().get(ib).severity()).isZero(); // рукой стадию 0 не почувствовать
        assertThat(panel.measure(ib, Probe.STETHOSCOPE, 20)).isTrue();
        assertThat(panel.faults().get(ib).severity()).isZero(); // 0,55 < стадии 1: «ровный гул»
        assertThat(panel.measure(is, Probe.STETHOSCOPE, 20)).isFalse();

        realSecondsTo(bearing, 0.62, 1);
        panel.measure(ib, Probe.STETHOSCOPE, 30);
        assertThat(panel.faults().get(ib).severity()).isEqualTo(1);
        assertThat(log.events).hasSize(1);

        panel.setPassport(true);
        panel.update(40);
        assertThat(panel.faults().get(is).severity()).isEqualTo(2);
        assertThat(log.events).hasSize(2);
        scaleMm[0] = 1.2;
        panel.update(50); // рост до 3 раньше 10 мин — молчим
        assertThat(log.events).hasSize(2);
        panel.update(40 + MaintenancePanel.DEBOUNCE_TICKS);
        assertThat(log.events).hasSize(3);
        assertThat(log.events.get(2)).containsExactly(MaintenanceFaultKind.HX_FOULING.ordinal(), 3, 2);
        assertThat(panel.maxSeverity()).isEqualTo(3);

        panel.repair(is, 100_000);
        assertThat(scaleMm[0]).isZero();
        assertThat(panel.faults().get(is).severity()).isZero();
        assertThat(RepairSpec.slice(MaintenanceFaultKind.HX_FOULING).getFirst().needs())
                .contains(RepairCondition.STOP, RepairCondition.COLD, RepairCondition.DRAINED);
    }

    /** Машина I0: шаг только на ходу; сухой насос — смазка и сальник; стетоскоп по стадии. */
    @Test
    void machineWearBundle() {
        MachineWear m = MachineWear.newI0(CFG, new SplittableRandom(1), true, true);
        assertThat(m.sources()).extracting(FaultSource::kind).containsExactly(MaintenanceFaultKind.BEARING_WEAR,
                MaintenanceFaultKind.LUBRICATION, MaintenanceFaultKind.PACKING_LEAK,
                MaintenanceFaultKind.IMPELLER_EROSION);
        m.stepSlow(3600, false, false, 3);
        assertThat(m.bearing().damage()).isZero();
        assertThat(m.stethoscope()).isEqualTo(MachineWear.StethoscopeSound.HUM);
        m.stepSlow(3600, true, true, 3);
        assertThat(m.packing().wear()).isCloseTo(10.0 / GlandPacking.LIFE_H, within(1e-12));
        assertThat(m.impeller().erosion()).isCloseTo(3 * ImpellerWear.NEUTRAL_RATE_PER_H, within(1e-12));
        double dry = m.bearing().damage();
        assertThat(dry).isCloseTo(20 / (m.bearing().lifeFactor() * 30_000 * 0.05), within(1e-12));
        while (m.bearing().damage() < 0.85) m.stepSlow(60, true, false, 7);
        assertThat(m.stethoscope()).isEqualTo(MachineWear.StethoscopeSound.SCRAPE);
    }
}
