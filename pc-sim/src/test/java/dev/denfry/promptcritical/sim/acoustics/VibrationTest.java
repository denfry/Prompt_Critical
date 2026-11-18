package dev.denfry.promptcritical.sim.acoustics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum.Band;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Вибродиагностика против 04a §4a.12 (T-4a-12, T-4a-13, T-4a-15) и справочных данных: дефектные частоты 6205-2RS
 * стенда CWRU, границы зон ISO 10816-1, ресурс ISO 281.
 */
class VibrationTest {
    private static final double NOMINAL_RPM = 1000;

    @Test
    void defectFrequenciesFromGeometry() {
        // T-4a-12: n = 10, d/D = 0,2, 1000 об/мин
        BearingGeometry g = BearingGeometry.LARGE;
        double fr = 1000 / 60.0;
        assertThat(g.bpfo(fr)).isCloseTo(66.67, within(0.01));
        assertThat(g.bpfi(fr)).isCloseTo(100.0, within(0.01));
        assertThat(g.bsf(fr)).isCloseTo(40.0, within(0.01));
        assertThat(g.ftf(fr)).isCloseTo(6.667, within(0.01));
        // CWRU Bearing Data Center, 6205-2RS JEM SKF: множители частоты вала
        BearingGeometry c = BearingGeometry.SKF_6205;
        assertThat(c.bpfo(1)).isCloseTo(3.5848, within(0.002));
        assertThat(c.bpfi(1)).isCloseTo(5.4152, within(0.002));
        assertThat(c.ftf(1)).isCloseTo(0.3983, within(0.0005));
        // CWRU даёт 4,7135 — удвоенную BSF (тело бьёт по обоим кольцам за оборот)
        assertThat(2 * c.bsf(1)).isCloseTo(4.7135, within(0.003));
    }

    private static RotatingMachine pump(MachineClass c, double damage) {
        return new RotatingMachine(c, BearingGeometry.LARGE, NOMINAL_RPM, 6, 0, 1, RotatingMachine.DELTA0_MM,
                new RollingBearing(1, damage));
    }

    @Test
    void healthyClassThreePumpIsInZoneA() {
        // T-4a-13: √(1 + 0,16 + 0,36 + 0,09) = 1,27 мм/с
        VibrationSpectrum s = pump(MachineClass.III, 0).spectrum(NOMINAL_RPM, 1);
        assertThat(s.overallRms() * 1e3).isCloseTo(1.269, within(0.01));
        assertThat(s.zone()).isEqualTo(IsoZone.A);
        assertThat(s.peak().band()).isEqualTo(Band.X1);
        assertThat(s.v(Band.BPF)).isCloseTo(0.6e-3, within(1e-9));
        // лопаточная частота — 6 лопаток × 16,67 Гц
        assertThat(s.line(Band.BPF).hz()).isCloseTo(100, within(1e-9));
    }

    @Test
    void everyClassStartsAtTheSameShareOfZoneA() {
        for (MachineClass c : MachineClass.values()) {
            VibrationSpectrum s = pump(c, 0).spectrum(NOMINAL_RPM, 1);
            assertThat(s.overallRms() / c.ab).isCloseTo(1.269 / 1.8, within(0.01));
            assertThat(s.zone()).isEqualTo(IsoZone.A);
        }
    }

    @Test
    void zonesFollowIsoBoundaries() {
        assertThat(MachineClass.I.zoneOf(0.70e-3)).isEqualTo(IsoZone.A);
        assertThat(MachineClass.I.zoneOf(0.72e-3)).isEqualTo(IsoZone.B);
        assertThat(MachineClass.II.zoneOf(2.9e-3)).isEqualTo(IsoZone.C);
        assertThat(MachineClass.IV.zoneOf(18.1e-3)).isEqualTo(IsoZone.D);
        assertThat(MachineClass.forPower(11e3, false)).isEqualTo(MachineClass.I);
        assertThat(MachineClass.forPower(55e3, true)).isEqualTo(MachineClass.II);
        assertThat(MachineClass.forPower(1e6, false)).isEqualTo(MachineClass.III);
        assertThat(MachineClass.forPower(1e6, true)).isEqualTo(MachineClass.IV);
    }

    @Test
    void imbalanceGrowsAsCubeOfSpeedBelowCritical() {
        RotatingMachine m = pump(MachineClass.III, 0);
        // s³ при r_c = s/1,5: на половине скорости — 1/8 с поправкой на резонансный множитель
        double r = m.v1x(500) / m.v1x(1000);
        double resp = (1 - Math.pow(1 / 1.5, 2)) / (1 - Math.pow(0.5 / 1.5, 2));
        assertThat(r).isCloseTo(0.125 * Math.abs(resp), within(0.002));
        m.balance();
        assertThat(m.v1x(1000)).isCloseTo(0.2e-3, within(1e-9));
        assertThat(m.spectrum(0, 1).overallRms()).isZero();
    }

    @Test
    void misalignmentShowsAtTwiceShaftSpeed() {
        RotatingMachine m = new RotatingMachine(MachineClass.III, BearingGeometry.LARGE, NOMINAL_RPM, 0, 0, 1, 0.2,
                new RollingBearing(1, 0));
        VibrationSpectrum s = m.spectrum(NOMINAL_RPM, 1);
        assertThat(s.peak().band()).isEqualTo(Band.X2);
        assertThat(s.v(Band.X2)).isCloseTo(1.6e-3, within(1e-9));
        m.align();
        assertThat(m.spectrum(NOMINAL_RPM, 1).v(Band.X2)).isCloseTo(0.4e-3, within(1e-9));
    }

    @Test
    void bearingStagesShowInTheirOwnBands() {
        RotatingMachine fresh = pump(MachineClass.III, 0);
        VibrationSpectrum s0 = fresh.spectrum(NOMINAL_RPM, 1);
        // T-4a-15: D_b = 0,7 — огибающая ВЧ выросла больше чем вдвое, дефектные пики ещё нет
        VibrationSpectrum s1 = pump(MachineClass.III, 0.7).spectrum(NOMINAL_RPM, 1);
        assertThat(s1.aHf()).isGreaterThanOrEqualTo(2 * s0.aHf());
        assertThat(s1.v(Band.BPFO)).isLessThan(0.1 * s0.overallRms());
        // конец стадии 2: пик наружного кольца 0,75 мм/с на 66,7 Гц
        VibrationSpectrum s2 = pump(MachineClass.III, 0.9).spectrum(NOMINAL_RPM, 1);
        assertThat(s2.v(Band.BPFO)).isCloseTo(0.75e-3, within(1e-9));
        assertThat(s2.line(Band.BPFO).hz()).isCloseTo(66.67, within(0.01));
        assertThat(s2.overallRms()).isGreaterThan(1.2 * s0.overallRms());
        // стадия 4 к концу — зона D, корпус горячий
        VibrationSpectrum s4 = pump(MachineClass.III, 0.999).spectrum(NOMINAL_RPM, 1);
        assertThat(s4.zone()).isEqualTo(IsoZone.D);
        assertThat(s4.bearingTempK()).isGreaterThan(s0.bearingTempK() + 35);
    }

    @Test
    void overallBandFollowsMachineSpeed() {
        // 300 об/мин: полоса с 2 Гц — 1x (5 Гц) входит; 3000 об/мин — с 10 Гц
        RotatingMachine m = pump(MachineClass.III, 0);
        VibrationSpectrum slow = m.spectrum(300, 1);
        assertThat(slow.bandLowHz()).isEqualTo(2);
        double withoutX1 = Math.sqrt(Math.pow(slow.overallRms(), 2) - Math.pow(slow.v(Band.X1), 2));
        assertThat(slow.overallRms()).isGreaterThan(withoutX1);
        // сепараторная 0,4·f_r = 2 Гц при 300 об/мин на границе, при 240 — ниже и не считается
        VibrationSpectrum slower = pump(MachineClass.III, 0.95).spectrum(240, 1);
        double sumSq = Math.pow(slower.vBackground(), 2);
        for (VibrationSpectrum.Line l : slower.lines()) if (l.band() != Band.FTF) sumSq += l.vRms() * l.vRms();
        assertThat(slower.overallRms()).isCloseTo(Math.sqrt(sumSq), within(1e-12));
    }

    @Test
    void wearFollowsIso281AndResetsOnReplacement() {
        // 50 000 ч на номинале при u_b = 1, k_wear = 1; вдвое быстрее — вдвое короче
        RollingBearing b = new RollingBearing(1, 0);
        b.step(25_000 * 3600.0, 1, 1, 1, 1);
        assertThat(b.damage()).isCloseTo(0.5, within(1e-9));
        RollingBearing fast = new RollingBearing(1, 0);
        fast.step(12_500 * 3600.0, 2, 1, 1, 1);
        assertThat(fast.damage()).isCloseTo(0.5, within(1e-9));
        // нагрузка в 1,26 раза — ресурс вдвое меньше (p = 3)
        assertThat(RollingBearing.l10h(1, Math.cbrt(2))).isCloseTo(25_000, within(1e-6));
        // стоящий вал не изнашивается
        assertThat(b.step(3600, 0, 1, 1, 1)).isFalse();
        assertThat(b.damage()).isCloseTo(0.5, within(1e-9));

        RotatingMachine m = RotatingMachine.installed(MachineClass.II, BearingGeometry.SKF_6205, NOMINAL_RPM, 0, 0,
                new SplittableRandom(7));
        boolean seized = false;
        for (int i = 0; i < 10_000 && !seized; i++) seized = m.stepWear(3600 * 100, NOMINAL_RPM, 20);
        assertThat(seized).isTrue();
        assertThat(m.bearing().stage()).isEqualTo(4);
        m.replaceBearing(new SplittableRandom(8));
        assertThat(m.bearing().damage()).isZero();
        assertThat(m.bearing().seized()).isFalse();
    }
}
