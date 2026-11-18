package dev.denfry.promptcritical.sim.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.environment.Tide.Calendar;
import dev.denfry.promptcritical.sim.environment.Tide.Constituent;
import dev.denfry.promptcritical.sim.environment.Tide.Site;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Гармоники прилива против таблиц: скорости составляющих из аргументов Дудсона с настоящим календарём совпадают с
 * NOAA; Бостон (NOAA 8443970) — полусуточный по форм-фактору; на игровом календаре сизигийная вода — в полнолуние и
 * новолуние Minecraft, квадратурная — в четверти; закон Грина.
 */
class TideTest {
    /** Скорости NOAA CO-OPS, °/ч. */
    @ParameterizedTest
    @CsvSource({"M2, 28.984104", "S2, 30.0", "N2, 28.43973", "K1, 15.041069", "O1, 13.943035"})
    void constituentSpeedsMatchNoaa(Constituent c, double noaaDegPerHour) {
        assertThat(Calendar.REAL.speedDegPerHour(c)).isCloseTo(noaaDegPerHour, within(2e-5));
    }

    @Test
    void argumentsAdvanceAtTheConstituentSpeeds() {
        Tide t = new Tide(Site.boston(), Calendar.REAL);
        for (Constituent c : Constituent.values())
            assertThat(t.argumentDeg(c, 101) - t.argumentDeg(c, 100)).isCloseTo(Calendar.REAL.speedDegPerHour(c), within(1e-9));
    }

    @Test
    void bostonIsSemidiurnalWithSpringAndNeapRanges() {
        Site b = Site.boston();
        assertThat(b.formFactor()).isCloseTo(0.164, within(0.001)).isLessThan(0.25);
        assertThat(b.springRangeM()).isCloseTo(3.158, within(1e-9));
        assertThat(b.neapRangeM()).isCloseTo(2.326, within(1e-9));
    }

    @Test
    void springNeapBeatIsHalfASynodicMonth() {
        Calendar real = Calendar.REAL;
        double beatDays = 360 / (real.speedDegPerHour(Constituent.S2) - real.speedDegPerHour(Constituent.M2)) / 24;
        assertThat(beatDays).isCloseTo(14.765, within(0.001));
        Calendar game = Calendar.game(96);
        assertThat(360 / (game.speedDegPerHour(Constituent.S2) - game.speedDegPerHour(Constituent.M2)) / 24).isCloseTo(4, within(1e-9));
    }

    /** Наибольшая величина прилива за сутки, начиная с часа {@code fromHours}. */
    private static double dailyRange(Tide t, double fromHours) {
        double hi = -1e9, lo = 1e9;
        for (double h = fromHours; h < fromHours + 25; h += 0.05) {
            double z = t.heightM(h);
            hi = Math.max(hi, z);
            lo = Math.min(lo, z);
        }
        return hi - lo;
    }

    /** Состав Бостона без эллиптической N2: сизигия и квадратура в чистом виде. */
    private static Site withoutN2(double m2) {
        Site s = Site.semidiurnal(m2);
        java.util.Map<Constituent, Double> a = new java.util.EnumMap<>(s.amplitudeM());
        a.remove(Constituent.N2);
        return new Site(a, java.util.Map.of());
    }

    @Test
    void gameSpringTidesComeAtFullAndNewMoonAndNeapsAtTheQuarters() {
        Tide t = new Tide(withoutN2(2), Calendar.game(96));
        double full = dailyRange(t, 0), quarter = dailyRange(t, 2 * 24), fresh = dailyRange(t, 4 * 24), last = dailyRange(t, 6 * 24);
        assertThat(full).isGreaterThan(1.1 * quarter); // (M2 + S2)/(M2 − S2) ≈ 1,36 минус суточное неравенство
        assertThat(fresh).isGreaterThan(1.1 * last);
        // сизигийная величина ≈ 2(M2 + S2) плюс вклад N2 и суточных
        assertThat(full).isCloseTo(t.site().springRangeM(), within(1.0));
        assertThat(quarter).isCloseTo(t.site().neapRangeM(), within(1.0));
    }

    @Test
    void ellipticN2MakesPerigeanSpringsLargerThanApogeanOnes() {
        // с N2 сизигии неравны: полнолуние у перигея (аномалия 0°), новолуние — у апогея (≈ 193°)
        Tide t = new Tide(Site.semidiurnal(2), Calendar.game(96));
        assertThat(dailyRange(t, 0)).isGreaterThan(1.2 * dailyRange(t, 4 * 24));
    }

    @Test
    void dayTimeZeroIsSixInTheMorningOfTheFullMoonDay() {
        assertThat(Tide.hoursFromDayTime(0)).isEqualTo(6);
        assertThat(Tide.hoursFromDayTime(24_000 * 4)).isEqualTo(4 * 24 + 6); // новолуние Minecraft — четвёртые сутки
    }

    @Test
    void greensLawAmplifiesTowardsShallowNarrowWater() {
        // глубина с 4000 до 50 м — в 80^{1/4} ≈ 2,99 раза; сужение вчетверо — ещё вдвое
        assertThat(Tide.greensLaw(4000, 50, 1, 1)).isCloseTo(Math.pow(80, 0.25), within(1e-9));
        assertThat(Tide.greensLaw(4000, 50, 4, 1)).isCloseTo(2 * Math.pow(80, 0.25), within(1e-9));
        assertThat(Tide.greensLaw(4000, 1, 100, 1)).isEqualTo(Tide.MAX_AMPLIFICATION);
    }
}
