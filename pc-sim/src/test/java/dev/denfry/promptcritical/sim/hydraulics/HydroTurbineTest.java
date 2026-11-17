package dev.denfry.promptcritical.sim.hydraulics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.environment.RiverCatchment;
import org.junit.jupiter.api.Test;

class HydroTurbineTest {
    /** Малая ГЭС: напор 30 м, 4 м³/с, водовод 80 м Ø 1,4 м, 500 об/мин, H = 3 с. */
    private static final HydroTurbine.Spec SITE = new HydroTurbine.Spec(30, 4, 80, 1.4, 0.9, 2 * Math.PI * 500 / 60, 3, 0.05);

    private static HydroTurbine running(double loadFraction) {
        HydroTurbine t = new HydroTurbine(SITE);
        double torque = loadFraction * SITE.ratedPowerW() / SITE.nominalOmega();
        t.setOmega(SITE.nominalOmega());
        for (int s = 0; s < 6000; s++) t.step(0.05, torque * Math.min(1, s / 600.0), true);
        return t;
    }

    @Test
    void ratedPowerAndWaterStartingTime() {
        assertThat(SITE.ratedPowerW()).isCloseTo(0.9 * 1000 * 9.80665 * 4 * 30, within(1.0)); // ≈ 1,06 МВт
        // T_w = 80·4/(9,81·1,539·30) ≈ 0,71 с
        assertThat(SITE.waterStartingTimeS()).isCloseTo(0.706, within(0.005));
        assertThat(SITE.kind()).isEqualTo("kaplan");
    }

    /** Регулятор с временным статизмом держит 70 % нагрузки: обороты в полосе статизма, без раскачки. */
    @Test
    void governorHoldsLoadWithoutHunting() {
        HydroTurbine t = running(0.7);
        double w0 = SITE.nominalOmega();
        assertThat(t.omega() / w0).isBetween(0.99, 1.05);
        assertThat(t.powerW() / SITE.ratedPowerW()).isCloseTo(0.7, within(0.02));
        double min = 9, max = 0;
        for (int s = 0; s < 400; s++) {
            t.step(0.05, 0.7 * SITE.ratedPowerW() / w0, true);
            min = Math.min(min, t.omega() / w0);
            max = Math.max(max, t.omega() / w0);
        }
        assertThat(max - min).isLessThan(1e-3);
    }

    /** Открыли аппарат — напор и мощность сперва проседают (вода в водоводе ещё не разогналась), потом растут. */
    @Test
    void openingTheGateFirstLowersThePower() {
        HydroTurbine t = running(0.5);
        double p0 = t.powerW(), h0 = t.headPu();
        // сброс уставки скорости вверх: регулятор открывает аппарат
        double w0 = SITE.nominalOmega();
        t.setOmega(0.97 * w0);
        double pMin = p0, hMin = h0;
        for (int s = 0; s < 20; s++) {
            t.step(0.05, 0.5 * SITE.ratedPowerW() / w0, true);
            pMin = Math.min(pMin, t.powerW());
            hMin = Math.min(hMin, t.headPu());
        }
        assertThat(hMin).isLessThan(h0 - 0.01);
        assertThat(pMin).isLessThan(p0);
    }

    /**
     * Сброс полной нагрузки: аппарат закрывается не быстрее 20 %/с, напор растёт, но водовод цел; с длинным водоводом
     * (T_w ≈ 6 с) тот же сброс рвёт его гидроударом.
     */
    @Test
    void loadRejectionRaisesHeadAndALongPenstockBursts() {
        HydroTurbine t = running(0.95);
        double peak = 0;
        for (int s = 0; s < 600; s++) {
            t.step(0.05, 0, true);
            peak = Math.max(peak, t.headPu());
        }
        assertThat(peak).isGreaterThan(1.0).isLessThan(HydroTurbine.BURST_HEAD);
        assertThat(t.burst()).isFalse();
        assertThat(t.omega() / SITE.nominalOmega()).isLessThan(1.5); // заброс оборотов ГЭС — десятки процентов

        HydroTurbine.Spec longPipe = new HydroTurbine.Spec(30, 4, 700, 1.4, 0.9, SITE.nominalOmega(), 3, 0.05);
        assertThat(longPipe.waterStartingTimeS()).isGreaterThan(6);
        HydroTurbine b = new HydroTurbine(longPipe);
        b.setOmega(longPipe.nominalOmega());
        double torque = 0.95 * longPipe.ratedPowerW() / longPipe.nominalOmega();
        for (int s = 0; s < 12_000; s++) b.step(0.05, torque * Math.min(1, s / 2000.0), true);
        for (int s = 0; s < 600; s++) b.step(0.05, 0, false); // затвор закрыли разом
        assertThat(b.burst()).isTrue();
    }

    /** Река не даёт больше своего расхода: аппарат упирается в предел, мощность — доля доступной воды. */
    @Test
    void lowRiverLimitsTheGate() {
        HydroTurbine t = new HydroTurbine(SITE);
        t.setAvailableFlow(2);
        assertThat(t.maxGate()).isCloseTo(0.5, within(1e-12));
        assertThat(t.capacityW()).isCloseTo((0.5 - 0.08) / 0.92 * SITE.ratedPowerW(), within(1.0));
    }

    /**
     * Водосбор: снег копится в мороз и тает весной — половодье запаздывает за оттепелью; после ливня паводок растёт
     * и спадает за неделю-две.
     */
    @Test
    void catchmentMakesASpringFloodAndARainFlood() {
        RiverCatchment c = new RiverCatchment();
        long day = 24_000;
        for (int d = 0; d < 20; d++) c.step(day, d % 2 == 0, false, -5); // зима: снег через день
        assertThat(c.snowMm()).isCloseTo(10 * 48, within(1e-9));
        double winter = c.dischargeM3s(100);
        assertThat(winter).isCloseTo(100e6 * RiverCatchment.BASEFLOW_MM_DAY * 1e-3 / 86_400, within(1e-6));
        double peak = 0;
        int peakDay = 0;
        for (int d = 1; d <= 40; d++) {
            c.step(day, false, false, 8); // оттепель: 24 мм/сут таяния
            if (c.dischargeM3s(100) > peak) {
                peak = c.dischargeM3s(100);
                peakDay = d;
            }
        }
        assertThat(peak).isGreaterThan(5 * winter);
        assertThat(peakDay).isBetween(15, 30);
        assertThat(c.snowMm()).isZero();
    }
}
