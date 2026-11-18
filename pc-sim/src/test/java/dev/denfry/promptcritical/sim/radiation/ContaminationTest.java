package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;

/**
 * Поверхностное загрязнение: нормы НРБ-99/2009 в Бк/см², показания зонда по ISO 7503-1 и обратный пересчёт, предел
 * обнаружения ниже нормы для кожи, перенос касанием с сохранением активности, КД способов дезактивации, ожидаемая доза
 * от α-загрязнения против β.
 */
class ContaminationTest {
    @Test
    void nrbLimitsInBecquerelsPerSquareCentimetre() {
        // 2 α/(см²·мин) на коже при ε_s 0,25 — 0,133 Бк/см²; 200 β при 0,5 — 6,67 Бк/см²
        assertThat(ContaminationLimits.Surface.SKIN.alphaBqCm2()).isCloseTo(2 / 15.0, within(1e-9));
        assertThat(ContaminationLimits.Surface.SKIN.betaBqCm2()).isCloseTo(200 / 30.0, within(1e-9));
        assertThat(ContaminationLimits.Surface.PERIODIC_ROOM.alphaBqCm2()).isCloseTo(50 / 15.0, within(1e-9));
        // нормы растут от кожи к помещениям периодического пребывания
        double prev = 0;
        for (ContaminationLimits.Surface s : new ContaminationLimits.Surface[] {ContaminationLimits.Surface.SKIN,
                ContaminationLimits.Surface.OVERALLS, ContaminationLimits.Surface.PPE_OUTER}) {
            assertThat(s.betaBqCm2()).isGreaterThan(prev);
            prev = s.betaBqCm2();
        }
        assertThat(ContaminationLimits.flux(ContaminationLimits.bqCm2(7, 0.5), 0.5)).isCloseTo(7, within(1e-12));
    }

    @Test
    void roomLimitsCountOnlyLooseContamination() {
        // фиксированное на полу помещения нормой не считается, на коже — считается
        SurfaceContamination fixedOnly = new SurfaceContamination(0, 1, 0, 0);
        assertThat(ContaminationLimits.Surface.PERMANENT_ROOM.ratio(fixedOnly)).isZero();
        assertThat(ContaminationLimits.Surface.SKIN.ratio(fixedOnly)).isCloseTo(7.5, within(1e-9));
    }

    @Test
    void probeReadsBackWhatIsOnTheSurface() {
        ContaminationProbe p = ContaminationProbe.STANDARD;
        SurfaceContamination c = SurfaceContamination.of(0.5, 20, 0.7);
        assertThat(p.alphaBqCm2(p.alphaCps(c))).isCloseTo(0.5, within(1e-9));
        assertThat(p.betaBqCm2(p.betaCps(c))).isCloseTo(20, within(1e-9));
        // 0,5 Бк/см² α под окном 100 см²: 0,5·100·0,35·0,25 = 4,4 имп/с сверх фона
        assertThat(p.alphaCps(c) - p.bgAlpha()).isCloseTo(4.375, within(1e-9));
        // фон не превращается в загрязнение
        assertThat(p.alphaBqCm2(p.alphaCps(SurfaceContamination.NONE))).isZero();
        // прибор годен для контроля кожи: МДА ниже нормы по обоим каналам
        assertThat(p.alphaMda()).isLessThan(ContaminationLimits.Surface.SKIN.alphaBqCm2());
        assertThat(p.betaMda()).isLessThan(ContaminationLimits.Surface.SKIN.betaBqCm2());
    }

    @Test
    void contactMovesLooseActivityAndConservesIt() {
        SurfaceContamination floor = SurfaceContamination.of(1, 100, 0.8);
        ContaminationTransfer.Pair p = ContaminationTransfer.contact(SurfaceContamination.NONE, 500, floor, 1e4, 250, 0.02);
        // обувь получила рыхлое, фиксированное осталось на полу
        assertThat(p.a().alphaLoose()).isGreaterThan(0);
        assertThat(p.a().alphaFixed()).isZero();
        assertThat(p.b().alphaFixed()).isEqualTo(floor.alphaFixed());
        // Бк до = Бк после
        double before = floor.beta() * 1e4;
        double after = p.a().beta() * 500 + p.b().beta() * 1e4;
        assertThat(after).isCloseTo(before, within(before * 1e-12));
        // прогулка по большому полу тянет обувь к его плотности: за шаг разница уходит на S·k/S_обуви = 1 %
        ContaminationTransfer.Pair w = ContaminationTransfer.walk(SurfaceContamination.NONE, floor, 1e9, 70);
        assertThat(w.a().betaLoose()).isCloseTo(floor.betaLoose() * (1 - Math.pow(0.99, 100)), within(floor.betaLoose() * 1e-3));
    }

    @Test
    void cleanShoesSpreadContaminationOnACleanFloor() {
        SurfaceContamination shoes = SurfaceContamination.of(0, 50, 1);
        ContaminationTransfer.Pair p = ContaminationTransfer.walk(shoes, SurfaceContamination.NONE, 1e4, 7);
        assertThat(p.b().betaLoose()).isGreaterThan(0);
        assertThat(p.a().betaLoose()).isLessThan(50);
    }

    @Test
    void deconFactorsOrderedByAggressiveness() {
        SurfaceContamination c = SurfaceContamination.of(1, 100, 0.5);
        double water = DeconMethod.WATER_RINSE.effectiveDf(c);
        double citric = DeconMethod.CITRIC_OXALIC.effectiveDf(c);
        double kmno4 = DeconMethod.PERMANGANATE_OXALIC.effectiveDf(c);
        double us = DeconMethod.ULTRASONIC_PERMANGANATE.effectiveDf(c);
        assertThat(water).isLessThan(citric);
        assertThat(citric).isLessThan(kmno4);
        assertThat(kmno4).isLessThan(us);
        // вода фиксированное не трогает: половина загрязнения остаётся всегда
        assertThat(water).isLessThan(2);
        // покрытие снимает рыхлое, жидких отходов не даёт
        assertThat(DeconMethod.STRIPPABLE_COATING.liquidLPerM2).isZero();
        assertThat(DeconMethod.STRIPPABLE_COATING.solidKgPerM2).isPositive();
        assertThat(c.after(DeconMethod.STRIPPABLE_COATING).alphaLoose()).isCloseTo(0.5 / 20, within(1e-12));
        // половина прохода — корень из КД
        assertThat(c.after(DeconMethod.SOAP_SHOWER, 0.5).betaLoose()).isCloseTo(50 / Math.sqrt(10), within(1e-9));
    }

    @Test
    void twoStepOxidisingTreatmentReachesFixedContamination() {
        // въевшееся в оксидную плёнку: вода и одна окислительная стадия его не берут, окисление + кислота — берут
        SurfaceContamination fixed = new SurfaceContamination(0, 1, 0, 100);
        assertThat(fixed.after(DeconMethod.WATER_RINSE).total()).isEqualTo(fixed.total());
        assertThat(fixed.after(DeconMethod.ALKALINE_PERMANGANATE).total()).isEqualTo(fixed.total());
        SurfaceContamination twoStep = fixed.after(DeconMethod.ALKALINE_PERMANGANATE).after(DeconMethod.PERMANGANATE_OXALIC);
        assertThat(fixed.total() / twoStep.total()).isCloseTo(10, within(1e-9));
        assertThat(twoStep.total()).isLessThan(fixed.after(DeconMethod.CITRIC_OXALIC).total());
    }

    @Test
    void alphaIsFarWorseToBreatheThanBeta() {
        SurfaceContamination alpha = SurfaceContamination.of(1, 0, 1), beta = SurfaceContamination.of(0, 1, 1);
        assertThat(ContaminationExposure.inhalationSvH(alpha) / ContaminationExposure.inhalationSvH(beta)).isGreaterThan(1000);
        // 1 Бк/см² Pu на полу: 10⁻² Бк/м³ в воздухе, ≈ 0,4 мкЗв/ч ожидаемой дозы от вдоха
        assertThat(ContaminationExposure.airBqM3(alpha)[0]).isCloseTo(1e-2, within(1e-12));
        assertThat(ContaminationExposure.inhalationSvH(alpha)).isCloseTo(3.84e-7, within(1e-9));
        // фиксированное в воздух не поднимается и в рот не попадает
        SurfaceContamination fixed = new SurfaceContamination(0, 100, 0, 100);
        assertThat(ContaminationExposure.committedSvH(fixed, fixed)).isZero();
    }

    @Test
    void spillSpreadsOverArea() {
        SurfaceContamination s = SurfaceContamination.spill(1e6, 1e8, 1e5, 0.9);
        assertThat(s.alpha()).isCloseTo(10, within(1e-9));
        assertThat(s.betaLoose()).isCloseTo(900, within(1e-9));
        assertThat(SurfaceContamination.mix(s, 1, SurfaceContamination.NONE, 1).alpha()).isCloseTo(5, within(1e-9));
        assertThat(s.minus(s).negligible()).isTrue();
    }
}
