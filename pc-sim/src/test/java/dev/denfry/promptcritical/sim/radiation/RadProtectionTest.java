package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Дозиметрия и радиационные СИЗ 04b §4b.5.2, §4b.7, §4b.8.3, §4b.12: числа из примеров GDD. */
class RadProtectionTest {

    // ------------------------------------------------------------------ счётчик Гейгера

    @Test
    void geigerFoldsBackInAStrongFieldAsTheGddExampleSays() {
        GeigerCounter g = new GeigerCounter(2.9, 0, 190e-6);
        // 1 мЗв/ч: n = 2900, m = 2900·e^{−0,551} = 1672 → 576 мкЗв/ч
        assertThat(g.readingUSvH(1000)).isCloseTo(576.6, within(1.0));
        // 10 мЗв/ч: m = 118 → 41 мкЗв/ч
        assertThat(g.readingUSvH(10_000)).isCloseTo(40.7, within(0.5));
        // 1 Зв/ч: почти ноль
        assertThat(g.readingUSvH(1e6)).isLessThan(1e-100);
        assertThat(g.maxObservedCps()).isCloseTo(1936, within(1.0));
    }

    @Test
    void geigerIsHonestAtBackgroundButWanders() {
        GeigerCounter g = GeigerCounter.SBM20;
        // фон 0,1 мкЗв/ч + собственный фон трубки: 0,49 имп/с, за 2 с разброс около ±50 %
        assertThat(g.readingUSvH(0.1)).isCloseTo(0.1 + 0.2 / 2.9, within(1e-3));
        assertThat(g.relativeError(0.1, 2)).isCloseTo(1.0, within(0.05));
        // поправка по непродлевающейся модели возвращает счёт в своём диапазоне
        double m = g.observedCps(100);
        assertThat(g.correctedCps(m)).isCloseTo(g.trueCps(100), within(g.trueCps(100) * 0.01));
    }

    // ------------------------------------------------------------------ ЭПД и учёт дозы

    @Test
    void epdAlarmsOnRateAndOnDoseSeparately() {
        var p = PersonalDosimeter.Preset.ROUTINE;
        assertThat(PersonalDosimeter.alarm(p, 0, 1e-6)).isEqualTo(PersonalDosimeter.Alarm.NONE);
        assertThat(PersonalDosimeter.alarm(p, 0, 2e-4)).isEqualTo(PersonalDosimeter.Alarm.RATE);
        assertThat(PersonalDosimeter.alarm(p, 1.5e-3, 1e-6)).isEqualTo(PersonalDosimeter.Alarm.DOSE);
        assertThat(PersonalDosimeter.alarm(p, 1.5e-3, 2e-4)).isEqualTo(PersonalDosimeter.Alarm.BOTH);
        // аварийные уставки — уровни 04b §4b.5.2
        assertThat(PersonalDosimeter.Preset.EMERGENCY_100.doseSv).isEqualTo(0.1);
        assertThat(PersonalDosimeter.Preset.LIFESAVING_500.doseSv).isEqualTo(0.5);
        assertThat(PersonalDosimeter.Preset.LIFESAVING_500.next()).isEqualTo(PersonalDosimeter.Preset.ROUTINE);
        assertThat(PersonalDosimeter.overRange(11)).isTrue();
        assertThat(PersonalDosimeter.displayedRateSvH(11)).isEqualTo(10);
    }

    @Test
    void ledgerForgetsDoseAfterAYear() {
        DoseLedger l = new DoseLedger();
        l.add(0, 0.01);
        l.add(100, 0.005);
        assertThat(l.annualSv(200)).isCloseTo(0.015, within(1e-12));
        assertThat(l.overWorkerLimit(200)).isFalse();
        l.add(300, 0.006);
        assertThat(l.overWorkerLimit(300)).isTrue();
        // через год после первой записи она уходит из окна, за жизнь остаётся
        assertThat(l.annualSv(366)).isCloseTo(0.011, within(1e-12));
        assertThat(l.annualSv(800)).isZero();
        assertThat(l.lifetimeSv()).isCloseTo(0.021, within(1e-12));
        // сохранение и восстановление
        DoseLedger r = new DoseLedger(l.buckets(), l.lastBucket(), l.lifetimeSv());
        r.add(801, 0.002);
        assertThat(r.annualSv(801)).isCloseTo(0.002, within(1e-12));
    }

    // ------------------------------------------------------------------ СИЗ

    @Test
    void iodinePassesAParticleFilterButNotACharcoalCartridge() {
        assertThat(RadRespirator.HALF_MASK_P3.inhaledFraction(Emitter.CS137)).isEqualTo(0.1);
        assertThat(RadRespirator.HALF_MASK_P3.inhaledFraction(Emitter.I131)).isEqualTo(1);
        assertThat(RadRespirator.FULL_FACE.inhaledFraction(Emitter.I131)).isEqualTo(1);
        assertThat(RadRespirator.FULL_FACE_IODINE.inhaledFraction(Emitter.I131)).isEqualTo(1 / 50.0);
        assertThat(RadRespirator.PNEUMOSUIT.inhaledFraction(Emitter.CS137)).isEqualTo(1 / 2000.0);
        assertThat(RadRespirator.PNEUMOSUIT.skinRetained()).isEqualTo(0.99);
        assertThat(RadRespirator.HALF_MASK_P3.best(RadRespirator.FULL_FACE_IODINE)).isEqualTo(RadRespirator.FULL_FACE_IODINE);
    }

    @Test
    void leadApronStopsSoftPhotonsOnlyAsInTheGddTable() {
        // 04b §4b.7.3: G0 0,043; G1 0,795; G2 0,939; G3 0,967; G4 0,974; G5 0,975
        assertThat(LeadApron.transmission(0.1)).isCloseTo(0.043, within(0.002));
        assertThat(LeadApron.transmission(0.3)).isCloseTo(0.795, within(0.003));
        assertThat(LeadApron.transmission(0.662)).isCloseTo(0.939, within(0.003));
        assertThat(LeadApron.transmission(1.25)).isCloseTo(0.967, within(0.002));
        assertThat(LeadApron.transmission(2.0)).isCloseTo(0.974, within(0.002));
        assertThat(LeadApron.transmission(6.0)).isCloseTo(0.975, within(0.002));
    }

    @Test
    void apronMultipliesTheGammaDoseButNotTheNeutrons() {
        PointSource cs = Emitter.CS137.source(1e9);
        double bare = DoseRate.gammaGyH(cs, 1, List.of());
        double worn = DoseRate.gammaGyH(cs, 1, List.of(), LeadApron::transmission);
        assertThat(worn / bare).isCloseTo(LeadApron.transmission(0.662), within(1e-3));
        PointSource n = PointSource.line(1, 0, 1e6);
        assertThat(DoseRate.totalSvH(n, 1, List.of(), LeadApron::transmission)).isEqualTo(DoseRate.totalSvH(n, 1, List.of()));
    }

    // ------------------------------------------------------------------ йодная профилактика

    @Test
    void tabletBlockingFollowsTheTimingTable() {
        assertThat(IodineProphylaxis.blocking(-30)).isZero();
        assertThat(IodineProphylaxis.blocking(-12)).isEqualTo(0.95);
        assertThat(IodineProphylaxis.blocking(1)).isEqualTo(0.80);
        assertThat(IodineProphylaxis.blocking(5)).isCloseTo(0.60, within(1e-12));
        assertThat(IodineProphylaxis.blocking(8)).isCloseTo(0.40, within(1e-12));
        assertThat(IodineProphylaxis.blocking(24)).isCloseTo(0.07, within(1e-12));
        assertThat(IodineProphylaxis.blocking(25)).isZero();
        assertThat(IodineProphylaxis.doseFactor(0.95)).isCloseTo(0.0975, within(1e-12));
    }

    @Test
    void tabletBeforeTheReleaseCutsTheDoseAtIntake() {
        IodineProphylaxis k = new IodineProphylaxis();
        k.takeTablet(10);
        assertThat(k.active(20)).isTrue();
        assertThat(k.intake(20, 1.0)).isCloseTo(0.0975, within(1e-12));
        // через сутки таблетка уже не действует
        assertThat(k.active(35)).isFalse();
        assertThat(k.intake(35, 1.0)).isEqualTo(1.0);
    }

    @Test
    void lateTabletRefundsPartOfTheDoseAndNeverTwice() {
        IodineProphylaxis k = new IodineProphylaxis();
        assertThat(k.intake(0.2, 1.0)).isEqualTo(1.0);
        // таблетка через 5 ч после середины часа поступления: f = 0,80 − 0,4·(4,5 − 2)/6
        double f = IodineProphylaxis.blocking(4.5);
        assertThat(k.takeTablet(5)).isCloseTo(0.95 * f, within(1e-12));
        // вторая таблетка позже не возвращает больше, чем уже вернула первая
        assertThat(k.takeTablet(6)).isZero();
    }

    // ------------------------------------------------------------------ зоны

    @Test
    void zonesClassifyByDesignRateAndGateTheDoor() {
        assertThat(RadZone.classify(0.1e-6)).isEqualTo(RadZone.FREE);
        assertThat(RadZone.classify(5e-6)).isEqualTo(RadZone.CONTROLLED_SERVICED);
        assertThat(RadZone.classify(10e-6)).isEqualTo(RadZone.CONTROLLED_PERIODIC);
        assertThat(RadZone.classify(1e-3)).isEqualTo(RadZone.CONTROLLED_UNSERVICED);
        assertThat(RadZone.CONTROLLED_SERVICED.exceeded(7e-6)).isTrue();

        assertThat(RadZone.FREE.entry(false, 1, DoseLedger.WORKER_ANNUAL_SV)).isEqualTo(RadZone.Verdict.OK);
        assertThat(RadZone.CONTROLLED_SERVICED.entry(false, 0, DoseLedger.WORKER_ANNUAL_SV)).isEqualTo(RadZone.Verdict.NO_DOSIMETER);
        assertThat(RadZone.CONTROLLED_SERVICED.entry(true, 0.021, DoseLedger.WORKER_ANNUAL_SV)).isEqualTo(RadZone.Verdict.OVER_ANNUAL_LIMIT);
        assertThat(RadZone.CONTROLLED_SERVICED.entry(true, 0.001, DoseLedger.WORKER_ANNUAL_SV)).isEqualTo(RadZone.Verdict.OK);
        assertThat(RadZone.exit(1, 4, RadZone.HAND_FOOT_LIMIT_BQ_CM2)).isEqualTo(RadZone.Verdict.CONTAMINATED);
        assertThat(RadZone.exit(1, 1, RadZone.HAND_FOOT_LIMIT_BQ_CM2)).isEqualTo(RadZone.Verdict.OK);
    }
}
