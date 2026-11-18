package dev.denfry.promptcritical.sim.battery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Батарея головного светильника против паспорта: разрядная кривая ламельного NiCd элемента при C/5 проходит через
 * свои паспортные точки и отдаёт номинальные 10 А·ч до 1,0 В; закон Пейкерта; светильник горит не меньше 10 ч
 * (БСЭ) и к концу второй смены тускнеет; заряд CC/CV за 6–7 ч с коэффициентом заряда ≈ 1,4; саморазряд, износ.
 */
class CapLampBatteryTest {
    private static final BatterySpec SPEC = BatterySpec.nickelCadmiumPocketPlate(3, 10);
    private static final double C5 = 2.0;

    /** Время разряда током {@code amps} до {@code cutoffPerCell}, ч. */
    private static double hoursTo(double amps, double cutoffPerCell) {
        Battery b = new Battery(SPEC);
        double t = 0, dt = 10;
        while (b.terminalVolts(amps) / 3 > cutoffPerCell && t < 200 * 3600) {
            b.discharge(dt, amps);
            t += dt;
        }
        return t / 3600;
    }

    @Test
    void dischargeCurvePassesThroughTheDatasheetPoints() {
        Battery b = new Battery(SPEC);
        assertThat(b.terminalVolts(C5) / 3).isCloseTo(1.34, within(0.01));
        b.discharge(0.8 * 10 * 3600 / C5, C5); // 80 % номинальной ёмкости
        assertThat(b.terminalVolts(C5) / 3).isCloseTo(1.19, within(0.02));
        // номинальная ёмкость: 10 А·ч за 5 ч до 1,0 В на элемент
        assertThat(hoursTo(C5, 1.0) * C5).isCloseTo(10, within(0.5));
    }

    @Test
    void higherCurrentGivesLessCapacityByPeukert() {
        double t1 = hoursTo(1, 1.0), t10 = hoursTo(10, 1.0);
        double k = Math.log(t1 / t10) / Math.log(10.0);
        assertThat(k).isBetween(1.05, 1.15); // щелочные NiCd: 1,05–1,15
        assertThat(t10 * 10).isLessThan(t1 * 1);
    }

    @Test
    void capLampBurnsAWholeShiftAndDimsBeforeTheNextOne() {
        CapLamp lamp = CapLamp.standard();
        double t = 0, dt = 60;
        assertThat(lamp.operate(dt, true, 15)).isGreaterThan(CapLamp.FLUX_LM); // свежезаряженная — выше номинала
        while (t < 10 * 3600) {
            lamp.operate(dt, true, 15);
            t += dt;
        }
        // паспорт: не меньше 10 ч горения при номинальном потоке (допуск — на полке 3,6–3,8 В)
        assertThat(lamp.relativeFlux()).isGreaterThan(0.6);
        double dimAt = 0;
        while (t < 20 * 3600 && dimAt == 0) {
            lamp.operate(dt, true, 15);
            t += dt;
            if (lamp.relativeFlux() < 0.2) dimAt = t / 3600;
        }
        // незаряженная батарея садится посреди второй смены: свет — меньше пятой части
        assertThat(dimAt).isBetween(11.0, 15.0);
        assertThat(lamp.battery().soc()).isLessThan(0.05);
    }

    @Test
    void incandescentFluxFollowsTheVoltageToThePower3point4() {
        assertThat(Math.pow(0.9, CapLamp.FLUX_EXPONENT)).isCloseTo(0.70, within(0.01));
        CapLamp lamp = CapLamp.standard();
        lamp.operate(1, true, 15);
        assertThat(lamp.relativeFlux())
                .isCloseTo(Math.pow(lamp.volts() / CapLamp.BULB_VOLTS, CapLamp.FLUX_EXPONENT), within(1e-9));
        assertThat(lamp.amps()).isCloseTo(CapLamp.BULB_AMPS * Math.pow(lamp.volts() / CapLamp.BULB_VOLTS, 0.55), within(1e-4));
    }

    @Test
    void ccCvChargeRefillsAnEmptyBatteryInSixToSevenHoursAtAboutFortyPercentOvercharge() {
        Battery b = new Battery(SPEC);
        b.restore(SPEC.maxAh(), 0, 20); // глубоко разряжена
        double removed = b.removedAh();
        double t = 0, ah = 0, dt = 30;
        LampCharger.Step first = LampCharger.charge(b, dt, 1, 20);
        assertThat(first.currentA()).isCloseTo(2.5, within(1e-9)); // C/4
        assertThat(first.acPowerW()).isBetween(10.0, 17.0); // ≈ 3,4 В · 2,5 А / КПД 0,75
        ah += first.currentA() * dt / 3600;
        while (t < 12 * 3600) {
            LampCharger.Step s = LampCharger.charge(b, dt, 1, 20);
            if (s.full()) break;
            ah += s.currentA() * dt / 3600;
            t += dt;
        }
        assertThat(t / 3600).isBetween(5.5, 7.5);
        assertThat(b.soc()).isGreaterThan(0.94);
        assertThat(ah / removed).isBetween(1.2, 1.5); // щелочные: 140 % отданного
        assertThat(b.terminalVolts(-0.01) / 3).isLessThan(LampCharger.CV_VOLTS_PER_CELL + 0.01);
    }

    @Test
    void lowSupplyVoltageSlowsAndThenStopsCharging() {
        Battery b = new Battery(SPEC);
        b.restore(5, 0, 20);
        assertThat(LampCharger.charge(b, 1, 0.7, 20).currentA()).isCloseTo(2.5 * 0.7 / 0.85, within(1e-6));
        assertThat(LampCharger.charge(b, 1, 0.4, 20).currentA()).isZero();
    }

    @Test
    void selfDischargeDoublesEveryTenKelvin() {
        Battery cool = new Battery(SPEC), warm = new Battery(SPEC);
        warm.restore(0, 0, 30);
        for (int d = 0; d < 30; d++) {
            cool.selfDischarge(86400);
            warm.selfDischarge(86400);
        }
        assertThat(1 - cool.soc()).isCloseTo(0.09, within(0.01)); // 0,3 % в сутки
        assertThat((1 - warm.soc()) / (1 - cool.soc())).isCloseTo(2, within(0.05));
    }

    @Test
    void capacityFadesToEightyPercentOverTheCycleLife() {
        Battery b = new Battery(SPEC);
        for (int c = 0; c < 2000; c++) {
            b.discharge(3600 * 5, C5);
            b.restore(0, b.cycles(), 20);
        }
        assertThat(b.cycles()).isCloseTo(2000, within(1.0));
        assertThat(b.health()).isCloseTo(0.8, within(0.005));
    }

    @Test
    void brokenGlassOnALitLampExposesTheFilamentOnceAndGoesDark() {
        CapLamp lamp = CapLamp.standard();
        lamp.operate(1, true, 15);
        assertThat(lamp.explosionProof()).isTrue();
        assertThat(lamp.breakGlass(true)).isTrue();
        assertThat(lamp.explosionProof()).isFalse();
        assertThat(lamp.breakGlass(true)).isFalse(); // уже разбито
        assertThat(lamp.operate(60, true, 15)).isZero(); // нить перегорела
        lamp.repair();
        assertThat(lamp.operate(60, true, 15)).isPositive();
        CapLamp off = CapLamp.standard();
        assertThat(off.breakGlass(false)).isFalse(); // холодная лампа газ не зажжёт
    }

    @Test
    void chargingWarmsTheBatteryOnlyModerately() {
        Battery b = new Battery(SPEC);
        b.restore(8, 0, 20);
        for (int i = 0; i < 6 * 120; i++) LampCharger.charge(b, 30, 1, 20);
        assertThat(b.temperatureC()).isBetween(20.0, 35.0);
    }
}
