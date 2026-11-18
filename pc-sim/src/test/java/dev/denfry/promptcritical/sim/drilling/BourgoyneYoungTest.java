package dev.denfry.promptcritical.sim.drilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class BourgoyneYoungTest {
    private static final double FT = 0.3048, PPG = 119.826_427, KLBF = 4448.222, INCH = 0.0254;

    /** Скорость при нормирующих условиях модели, фут/ч. */
    private static double normalized(BourgoyneYoung.Coefficients c, double depthFt, double gp, double ecd, double wd,
            double rpm, double jetLbf) {
        return BourgoyneYoung.ropMs(c, depthFt * FT, gp * PPG, ecd * PPG, wd * KLBF * 8.5, 8.5 * INCH, rpm, 0, jetLbf * 4.448_222)
                / FT * 3600;
    }

    /**
     * Нормирующие условия SPE 4238: D = 10 000 фут, gₚ = ρ_c = 9 фунт/галлон, W/d = 4, N = 60, h = 0, Fⱼ = 1000 —
     * все члены x₂…x₈ равны нулю и скорость равна 10^{a₁} (e^{2,303·a₁}).
     */
    @Test
    void normalizingConditionsGiveTenToTheA1() {
        var c = BourgoyneYoung.SHALE;
        assertThat(normalized(c, 10_000, 9, 9, 4, 60, 1000)).isCloseTo(Math.exp(2.303 * c.a1()), within(1e-9));
        assertThat(normalized(c, 10_000, 9, 9, 4, 60, 1000)).isCloseTo(Math.pow(10, c.a1()), within(0.01 * Math.pow(10, c.a1())));
    }

    /** Каждый член отдельно — по своей формуле книги (§5.10, уравнения 5.21–5.28). */
    @Test
    void eachTermFollowsItsFormula() {
        var c = BourgoyneYoung.SHALE;
        double base = normalized(c, 10_000, 9, 9, 4, 60, 1000);
        // x₂: на 6000 футах выше — e^{2,303·a₂·4000}
        assertThat(normalized(c, 6_000, 9, 9, 4, 60, 1000) / base).isCloseTo(Math.exp(2.303 * c.a2() * 4000), within(1e-9));
        // x₄: перевес 1 фунт/галлон на 10 000 футах — e^{−2,303·a₄·10 000}, проходка падает
        double over = normalized(c, 10_000, 9, 10, 4, 60, 1000) / base;
        assertThat(over).isCloseTo(Math.exp(-2.303 * c.a4() * 10_000), within(1e-9)).isLessThan(0.5);
        // x₃ и x₄ вместе: АВПД 10 фунт/галлон при той же плотности раствора 9 — бурение с депрессией, быстрее
        double under = normalized(c, 10_000, 10, 9, 4, 60, 1000) / base;
        assertThat(under).isCloseTo(Math.exp(2.303 * (c.a3() * Math.pow(10_000, 0.69) + c.a4() * 10_000)), within(1e-9))
                .isGreaterThan(1);
        // x₅: W/d = 7,8 при пороге 0,2 и a₅ = 1 — вдвое
        assertThat(normalized(c, 10_000, 9, 9, 7.8, 60, 1000) / base).isCloseTo(2.0, within(1e-9));
        // x₆: 120 об/мин — 2^{a₆}
        assertThat(normalized(c, 10_000, 9, 9, 4, 120, 1000) / base).isCloseTo(Math.pow(2, c.a6()), within(1e-9));
        // x₈: сила струй 2000 фунтов — 2^{a₈}
        assertThat(normalized(c, 10_000, 9, 9, 4, 60, 2000) / base).isCloseTo(Math.pow(2, c.a8()), within(1e-9));
    }

    @Test
    void belowThresholdWeightTheBitDoesNotDrill() {
        assertThat(normalized(BourgoyneYoung.SHALE, 5000, 9, 9, 0.15, 60, 1000)).isZero();
        assertThat(normalized(BourgoyneYoung.SHALE, 5000, 9, 9, 4, 0, 1000)).isZero();
    }

    /** Мягкий песчаник на 1500 м: десятки метров в час — порядок скоростей неглубоких скважин. */
    @Test
    void shallowSandstoneDrillsTensOfMetresPerHour() {
        double ms = BourgoyneYoung.ropMs(BourgoyneYoung.SANDSTONE, 1500, 1040, 1120, 120e3, 8.5 * INCH, 100, 0, 6000);
        assertThat(ms * 3600).isBetween(15.0, 80.0);
        double shale = BourgoyneYoung.ropMs(BourgoyneYoung.SHALE, 1500, 1040, 1120, 120e3, 8.5 * INCH, 100, 0, 6000);
        assertThat(ms / shale).isCloseTo(Math.pow(10, 0.25), within(0.02));
    }
}
