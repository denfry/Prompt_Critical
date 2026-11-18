package dev.denfry.promptcritical.sim.refining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class CatalyticCrackingTest {
    private static final double T520 = 520 + 273.15;

    /** T9.32: 520 °C, C/O = 6, 3 с — конверсия 0,7500, бензин 0,5129; C/O = 8 — 0,8000 / 0,5449 (±0,002). */
    @Test
    void weekmanReferenceCases() {
        CatalyticCracking.Kinetics a = CatalyticCracking.weekman(T520, 6, 3, CatalyticCracking.ACTIVITY_REF);
        CatalyticCracking.Kinetics b = CatalyticCracking.weekman(T520, 8, 3, CatalyticCracking.ACTIVITY_REF);
        assertThat(a.conversion()).isCloseTo(0.7500, within(0.002));
        assertThat(a.gasoline()).isCloseTo(0.5129, within(0.002));
        assertThat(b.conversion()).isCloseTo(0.8000, within(0.002));
        assertThat(b.gasoline()).isCloseTo(0.5449, within(0.002));
        assertThat(a.conversion()).isCloseTo(a.gasoline() + a.gasCoke(), within(1e-12));
    }

    /** T9.33: 10 кг/с при 250 °C, райзер 520 °C, C/O = 6 → T_рег 712 °C (±3 К), кокс 5,1 % на сырьё (±0,1 абс. %). */
    @Test
    void heatBalanceReference() {
        assertThat(CatalyticCracking.feedDutyJPerKg(250 + 273.15, T520)).isCloseTo(1.267e6, within(1e3));
        CatalyticCracking.HeatBalance hb = CatalyticCracking.steadyHeatBalance(250 + 273.15, T520, 6);
        assertThat(hb.regenK() - 273.15).isCloseTo(712, within(3.0));
        assertThat(hb.cokeKgPerKg() * 100).isCloseTo(5.1, within(0.1));
        assertThat(hb.deltaCoke() * 100).isCloseTo(0.86, within(0.02));
    }

    /** Кокс §9.9.5: 0,2·y₃ + 0,6·CCR; выходы складываются в единицу. */
    @Test
    void yieldsCloseTheBalance() {
        CatalyticCracking.Yields y = CatalyticCracking.yields(CatalyticCracking.weekman(T520, 6, 3, 0.7), 0.005);
        double sum = y.gasoline() + y.dryGas() + y.propanePropylene() + y.butaneButylene() + y.lightCycleOil() + y.decantOil() + y.coke();
        assertThat(sum).isCloseTo(1, within(1e-12));
        assertThat(y.coke()).isCloseTo(0.2 * 0.2371 + 0.003, within(0.002));
    }

    private static CatalyticCracking charged() {
        CatalyticCracking u = new CatalyticCracking();
        u.addFreshCatalyst(1000, 1000);
        u.restore(712 + 273.15, 0, 1000, 0.7, false);
        return u;
    }

    /** С воздухом установка выходит на режим около 700 °C в регенераторе; тяжёлое сырьё — горячее регенератор, ниже C/O и конверсия. */
    @Test
    void heavyFeedSelfLimits() {
        CatalyticCracking light = charged(), heavy = charged();
        CatalyticCracking.Step a = null, b = null;
        for (int i = 0; i < 20_000; i++) {
            a = light.step(1, 0.5, 250 + 273.15, 0.005, T520, 0.36, 0.36, 1000);
            b = heavy.step(1, 0.5, 250 + 273.15, 0.03, T520, 0.40, 0.36, 1000);
        }
        assertThat(a.regenK() - 273.15).isBetween(660.0, 760.0);
        assertThat(b.regenK()).isGreaterThan(a.regenK());
        assertThat(b.catToOil()).isLessThan(a.catToOil());
        assertThat(b.yields().conversion()).isLessThan(a.yields().conversion());
        assertThat(a.riserK()).isCloseTo(T520, within(1e-9));
    }

    /** Без воздуха кокс копится, активность падает; задвижки закрывает ПАЗ по перепаду. */
    @Test
    void lossOfAirTripsAndCokesTheCatalyst() {
        CatalyticCracking u = charged();
        assertThat(u.step(1, 0.5, 523.15, 0.005, T520, 0.5, 0.5, 1000).catKgS()).isPositive();
        CatalyticCracking.Step s = u.step(1, 0.5, 523.15, 0.005, T520, 0, 0.5, 1000);
        assertThat(s.trip()).isTrue();
        // без воздуха установка и не пускается: перепад — разрешающее условие
        CatalyticCracking cold = new CatalyticCracking();
        cold.addFreshCatalyst(1000, 1000);
        assertThat(cold.step(1, 0.5, 523.15, 0.005, T520, 0, 0.5, 1000).catKgS()).isZero();
        assertThat(cold.tripped()).isFalse();
        assertThat(s.catKgS()).isZero();
        assertThat(s.slideDpMpa()).isLessThan(CatalyticCracking.SLIDE_DP_MIN_MPA);
    }

    /** Подпитка свежим катализатором поднимает активность; без неё равновесная активность падает. */
    @Test
    void makeupHoldsActivity() {
        CatalyticCracking u = charged();
        for (int i = 0; i < 1000; i++) u.step(3600, 0.5, 523.15, 0.005, T520, 0.36, 0.36, 1000);
        double aged = u.activity();
        // 1000 ч физ.: 0,7·exp(−8,6·10⁻⁸·3,6·10⁶)
        assertThat(aged).isCloseTo(0.7 * Math.exp(-CatalyticCracking.DEACTIVATION_PER_S * 3.6e6), within(1e-3));
        assertThat(u.addFreshCatalyst(100, 1000)).isCloseTo(100, within(1e-12));
        assertThat(u.activity()).isCloseTo(aged * 0.9 + 0.1, within(1e-12));
        // первая загрузка не выводит ничего
        assertThat(new CatalyticCracking().addFreshCatalyst(500, 1000)).isZero();
    }
}
