package dev.denfry.promptcritical.sim.drilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class WellTest {
    private static final double G = 9.806_65;
    private static final double MUD = 1100, PITS = 60;
    /** Газовая залежь с умеренным АВПД 11,3 кПа/м: на своде ≈ 1,19 г/см³ — больше раствора 1,10. */
    private static final Trap GAS = new Trap(0, 0, 80, 1500, 60, 1560, 15, 0.2, 50, FormationFluid.GAS, 11_300);
    private static final double M3_PER_STROKE = 0.019;

    private static Well wellAboveGas() {
        return new Well(WellGeometry.STANDARD, new Prospect(GAS, 1500), 1495, MUD, PITS);
    }

    /** Бурить до сигнала о приросте в ёмкостях; возвращает физическое время, с. */
    private static double drillUntilKick(Well w) {
        double t = 0;
        while (!w.kickAlarm() && t < 6 * 3600) {
            w.step(10, 0.03, 100, 120e3);
            t += 10;
        }
        assertThat(w.kickAlarm()).as("pit gain alarm while drilling into the overpressured gas").isTrue();
        return t;
    }

    private static void shutIn(Well w, double seconds) {
        w.setBopClosed(true);
        for (double t = 0; t < seconds; t += 5) w.step(5, 0, 0, 0);
    }

    @Test
    void staticAndCirculatingPressuresWithoutReservoir() {
        Well w = new Well(WellGeometry.STANDARD, null, 1500, MUD, PITS);
        w.step(60, 0, 0, 0);
        assertThat(w.bottomholePressurePa()).isCloseTo(MUD * G * 1500, within(1.0));
        assertThat(w.drillPipePressurePa()).isZero();
        assertThat(w.ecdKgM3()).isCloseTo(MUD, within(1e-6));
        w.step(60, 0.03, 0, 0);
        assertThat(w.drillPipePressurePa()).isCloseTo(w.circulatingLossPa(0.03), within(1e3));
        assertThat(w.ecdKgM3()).isGreaterThan(MUD);
        assertThat(w.pitGainM3()).isCloseTo(0, within(1e-6));
        assertThat(w.kickAlarm()).isFalse();
    }

    /** Нормальное давление: раствор 1,10 держит пласт, скважина бурится сквозь коллектор без притока. */
    @Test
    void overbalancedMudDrillsThroughWithoutKick() {
        Trap normal = new Trap(0, 0, 80, 1500, 60, 1560, 15, 0.2, 50, FormationFluid.OIL, PetroleumField.HYDROSTATIC_PA_M);
        Well w = new Well(WellGeometry.STANDARD, new Prospect(normal, 1500), 1495, MUD, PITS);
        double t = 0;
        while (w.holeDepthM() < 1520 && t < 6 * 3600) {
            w.step(10, 0.03, 100, 120e3);
            t += 10;
        }
        assertThat(w.holeDepthM()).isGreaterThanOrEqualTo(1520);
        assertThat(w.totalInfluxM3()).isZero();
        assertThat(w.kickAlarm()).isFalse();
    }

    /**
     * Вскрытие газа с АВПД даёт приток; после закрытия давление в трубах устанавливается на SIDPP = Pₚ − ρgH (в трубах
     * чистый раствор), в затрубье — выше на разницу веса раствора и газа на высоте пачки.
     */
    @Test
    void shutInPressuresStabiliseAtTheFormationUnderbalance() {
        Well w = wellAboveGas();
        drillUntilKick(w);
        double depth = w.holeDepthM();
        assertThat(depth).isGreaterThan(1500);
        assertThat(w.totalInfluxM3()).isPositive();
        shutIn(w, 120);
        double mid = 1500 + w.exposedPayM() / 2;
        double expected = GAS.pressureAt(mid) - MUD * G * mid;
        assertThat(w.drillPipePressurePa()).isCloseTo(expected, within(0.04 * expected));
        assertThat(w.casingPressurePa()).isGreaterThan(w.drillPipePressurePa());
        assertThat(w.influxM3s()).isLessThan(1e-5);
        assertThat(w.blowout()).isFalse();
    }

    /** Всплытие газа в закрытой скважине несёт пластовое давление наверх: SICP и SIDPP растут без притока. */
    @Test
    void gasMigrationRaisesShutInPressures() {
        Well w = wellAboveGas();
        drillUntilKick(w);
        shutIn(w, 120);
        double sicp = w.casingPressurePa(), sidpp = w.drillPipePressurePa();
        shutIn(w, 600);
        assertThat(w.casingPressurePa() - sicp).isGreaterThan(0.2e6);
        assertThat(w.drillPipePressurePa() - sidpp).isGreaterThan(0.2e6);
    }

    /** Приток, не замеченный бурильщиком: газ всплывает и расширяется, разгружает ствол — открытый фонтан. */
    @Test
    void ignoredKickBecomesABlowout() {
        Well w = wellAboveGas();
        drillUntilKick(w);
        double t = 0;
        while (!w.blowout() && t < 8 * 3600) {
            w.step(10, 0, 0, 0);
            t += 10;
        }
        assertThat(w.blowout()).isTrue();
        w.step(60, 0, 0, 0);
        assertThat(w.blowoutRateM3s()).isPositive();
        assertThat(w.releasedM3()).isPositive();
    }

    /**
     * Глушение методом бурильщика: первый цикл выводит газ при постоянном ICP, забойное давление всё время не ниже
     * пластового; после утяжеления до KMW второй цикл ведёт давление от ICP к FCP; в конце SIDPP = SICP = 0 и после
     * открытия превентора перелива нет.
     */
    @Test
    void drillersMethodKillsTheWell() {
        Well w = wellAboveGas();
        drillUntilKick(w);
        shutIn(w, 120);
        DrillersMethod dm = new DrillersMethod(M3_PER_STROKE);
        assertThat(dm.advance(w)).isTrue();
        KillSheet k = dm.sheet();
        assertThat(k.sidppPa()).isPositive();
        assertThat(k.killMudKgM3()).isGreaterThan(MUD);
        assertThat(k.icpPa()).isCloseTo(k.scrPa() + k.sidppPa(), within(1e-6));
        double pp = w.porePressurePa();
        double minMargin = Double.MAX_VALUE, peakCasing = 0;
        double t = 0, dt = 2;
        while (dm.stage() == DrillersMethod.Stage.FIRST_CIRCULATION && t < 6 * 3600) {
            w.step(dt, dm.requiredPumpM3s(), 0, 0);
            dm.track(w, dt);
            t += dt;
            if (t > 30) minMargin = Math.min(minMargin, w.bottomholePressurePa() - pp);
            peakCasing = Math.max(peakCasing, w.casingPressurePa());
        }
        assertThat(dm.stage()).isEqualTo(DrillersMethod.Stage.WEIGHT_UP);
        assertThat(w.hasInflux()).isFalse();
        assertThat(w.ventedMol()).as("gas left through the choke and separator").isPositive();
        assertThat(minMargin).as("bottomhole pressure held at the formation pressure").isGreaterThan(-0.15e6);
        assertThat(peakCasing).as("casing pressure peaks as the gas expands near surface").isGreaterThan(k.sicpPa());
        assertThat(w.blowout()).isFalse();
        // насос стоит, скважина закрыта: газа нет — SICP = SIDPP
        for (int i = 0; i < 12; i++) w.step(5, 0, 0, 0);
        assertThat(w.casingPressurePa()).isCloseTo(w.drillPipePressurePa(), within(0.05e6));
        // утяжеление: барит на весь объём ёмкостей до KMW с запасом 3 кг/м³
        w.addBarite(KillSheet.bariteKg(w.pitVolumeM3(), w.pitDensityKgM3(), k.killMudKgM3() + 3));
        assertThat(w.pitGainM3()).as("barite volume is not a gain").isLessThan(Well.PIT_ALARM_M3);
        assertThat(dm.advance(w)).isTrue();
        double peakDpp = 0;
        while (dm.stage() == DrillersMethod.Stage.SECOND_CIRCULATION && t < 12 * 3600) {
            w.step(dt, dm.requiredPumpM3s(), 0, 0);
            dm.track(w, dt);
            t += dt;
            minMargin = Math.min(minMargin, w.bottomholePressurePa() - pp);
            // FCP держится, пока дроссель не открыт полностью: по формуле SCR·ρ_k/ρ₀ трение растёт с плотностью
            // линейно, на деле в ламинарном затрубье — нет, и под конец дроссель открыт, а давление чуть выше FCP
            if (dm.strokes() > k.strokesToBit() * 1.05 && w.casingPressurePa() > 0.05e6)
                peakDpp = Math.max(peakDpp, Math.abs(w.drillPipePressurePa() - k.fcpPa()));
        }
        assertThat(dm.stage()).isEqualTo(DrillersMethod.Stage.DONE);
        assertThat(minMargin).isGreaterThan(-0.15e6);
        assertThat(peakDpp).as("FCP held while kill mud fills the annulus").isLessThan(0.15e6);
        for (int i = 0; i < 24; i++) {
            w.step(5, 0, 0, 0);
            dm.track(w, 5);
        }
        assertThat(w.drillPipePressurePa()).isLessThan(0.05e6);
        assertThat(w.casingPressurePa()).isLessThan(0.05e6);
        w.setBopClosed(false);
        for (int i = 0; i < 120; i++) w.step(5, 0, 0, 0);
        assertThat(w.influxM3s()).isZero();
        assertThat(w.dead()).isTrue();
        assertThat(w.bottomholePressurePa() - pp).as("killed without a gross overbalance").isBetween(0.0, 0.4e6);
        assertThat(w.blowout()).isFalse();
        assertThat(w.shoeFractured()).isFalse();
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        Well w = wellAboveGas();
        drillUntilKick(w);
        shutIn(w, 30);
        Well r = Well.restore(WellGeometry.STANDARD, new Prospect(GAS, 1500), PITS, w.exportState());
        assertThat(r).isNotNull();
        assertThat(r.holeDepthM()).isEqualTo(w.holeDepthM());
        assertThat(r.casingPressurePa()).isEqualTo(w.casingPressurePa());
        assertThat(r.drillPipePressurePa()).isCloseTo(w.drillPipePressurePa(), within(1.0));
        assertThat(r.bopClosed()).isTrue();
        assertThat(r.hasInflux()).isTrue();
        assertThat(Well.restore(WellGeometry.STANDARD, null, PITS, new double[] {1})).isNull();
    }

    @Test
    void weightOnBitIsLimitedByCollarsAndShowsOnTheWeightIndicator() {
        Well w = new Well(WellGeometry.STANDARD, null, 1500, MUD, PITS);
        double max = w.maxWobN();
        assertThat(max).isCloseTo(0.85 * 136 * 180 * G * (1 - MUD / 7850), within(1.0));
        w.step(10, 0, 0, 0);
        double free = w.hookLoadN();
        w.step(10, 0.03, 100, 1e6);
        assertThat(w.wobN()).isEqualTo(max);
        // за 10 с колонна удлинилась на пройденные сантиметры: вес растёт на десятки ньютонов
        assertThat(free - w.hookLoadN()).isCloseTo(max, within(200.0));
        assertThat(w.ropMs()).isPositive();
        assertThat(w.rotaryTorqueNm()).isPositive();
    }
}
