package dev.denfry.promptcritical.sim.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.environment.Environment.Sample;
import dev.denfry.promptcritical.sim.environment.SeasonProvider.Season;
import dev.denfry.promptcritical.sim.grid.ConductorSpec;
import org.junit.jupiter.api.Test;

class EnvironmentTest {
    private static final long DAY = SeasonProvider.TICKS_PER_DAY;
    private static final Environment ENV = new Environment(42, SeasonProvider.Calendar.DEFAULT);
    private static final double OPEN = Environment.roughness(Environment.Surface.OPEN);

    private static Sample at(long t) {
        return ENV.sample(t, 64, 10, OPEN, 15, false, false);
    }

    @Test
    void sameSeedSameWeatherWhateverTheQueryOrder() {
        Environment other = new Environment(42, SeasonProvider.Calendar.DEFAULT);
        Sample late = other.sample(5_000_000, 64, 10, OPEN, 15, false, false);
        assertThat(at(5_000_000)).isEqualTo(late);
        assertThat(new Environment(43, SeasonProvider.Calendar.DEFAULT).sample(5_000_000, 64, 10, OPEN, 15, false, false))
                .isNotEqualTo(late);
    }

    @Test
    void calendarSeasons() {
        SeasonProvider.Calendar c = SeasonProvider.Calendar.DEFAULT;
        assertThat(SeasonProvider.season(c.yearFraction(0))).isEqualTo(Season.SPRING);
        assertThat(SeasonProvider.season(c.yearFraction(24 * DAY))).isEqualTo(Season.SUMMER);
        assertThat(SeasonProvider.season(c.yearFraction(80 * DAY))).isEqualTo(Season.WINTER);
        assertThat(c.yearFraction(96 * DAY)).isZero();
    }

    /** Лето теплее зимы на ≈ 2×10 К, день теплее ночи на ≈ 2×5 К: средние по многим годам. */
    @Test
    void temperatureFollowsSeasonAndDay() {
        double summer = 0, winter = 0, afternoon = 0, dawn = 0;
        int n = 0;
        for (int year = 0; year < 40; year++) {
            long y0 = year * 96 * DAY;
            for (int d = 0; d < 6; d++) {
                long mid = 9 * DAY / 24; // 15:00 = 9000 тиков от 6:00
                summer += at(y0 + (33 + d) * DAY).airTempC();
                winter += at(y0 + (81 + d) * DAY).airTempC();
                afternoon += at(y0 + d * 4 * DAY + mid).airTempC();
                dawn += at(y0 + d * 4 * DAY + 23 * DAY / 24).airTempC(); // 5:00
                n++;
            }
        }
        assertThat((summer - winter) / n).isCloseTo(20, within(2.5));
        assertThat((afternoon - dawn) / n).isCloseTo(10, within(2.5));
    }

    /**
     * Ветер на 10 м: средний за много лет около 5 м/с; разброс 10-минутных значений вокруг среднего — по IEC 61400-1
     * σ = 0,14·(0,75·V + 5,6); профиль по высоте над разной шероховатостью через высоту смешения 60 м.
     */
    @Test
    void windHasTheRightMeanTurbulenceAndProfile() {
        double sum = 0;
        int n = 0;
        for (long t = 0; t < 400 * DAY; t += DAY / 7) {
            sum += at(t).meanWindMs();
            n++;
        }
        assertThat(sum / n).isBetween(4.0, 6.0);

        long t0 = 1_234_567;
        double mean = at(t0).meanWindMs(), s2 = 0, m = 0;
        int k = 0;
        for (int s = 0; s < 3000; s++, k++) {
            double w = at(t0 + s * 20L).windMs(); // мгновенный ветер раз в секунду, 50 минут
            m += w;
            s2 += w * w;
        }
        double sigma = Math.sqrt(s2 / k - (m / k) * (m / k));
        assertThat(sigma).isCloseTo(Environment.TURBULENCE_I_REF * (0.75 * mean + 5.6), within(0.35 * sigma));

        // на высоте ступицы ВЭУ (30 м): над водой сильнее, чем в поле, над лесом слабее; выше стандартных 10 м — сильнее
        double water = ENV.sample(t0, 64, 30, Environment.roughness(Environment.Surface.WATER), 15, false, false).meanWindMs();
        double open = ENV.sample(t0, 64, 30, OPEN, 15, false, false).meanWindMs();
        double forest = ENV.sample(t0, 64, 30, Environment.roughness(Environment.Surface.FOREST), 15, false, false).meanWindMs();
        assertThat(water).isGreaterThan(open);
        assertThat(open).isGreaterThan(forest).isGreaterThan(mean);
        assertThat(open / mean).isCloseTo(Math.log(30 / 0.03) / Math.log(10 / 0.03), within(1e-9));
        // на 10 м лес тормозит ветер на четверть (European Wind Atlas: z₀ = 0,8 против 0,03)
        double forest10 = ENV.sample(t0, 64, 10, Environment.roughness(Environment.Surface.FOREST), 15, false, false).meanWindMs();
        assertThat(forest10 / mean).isBetween(0.7, 0.8);
        // на высоте смешения ветер от поверхности не зависит
        assertThat(ENV.sample(t0, 64, 60, Environment.roughness(Environment.Surface.FOREST), 15, false, false).meanWindMs())
                .isCloseTo(ENV.sample(t0, 64, 60, Environment.roughness(Environment.Surface.WATER), 15, false, false).meanWindMs(),
                        within(1e-9));
        assertThat(ENV.sample(t0, 64, 10, OPEN, 15, true, true).meanWindMs()).isCloseTo(2 * mean, within(1e-9));
    }

    @Test
    void airDensityAndSun() {
        Sample s = ENV.sample(0, 63, 10, OPEN, 15, false, false);
        assertThat(s.densityKgM3()).isCloseTo(101_325 / (287.05 * (s.airTempC() + 273.15)), within(1e-9));
        assertThat(ENV.sample(0, 63 + 1000, 10, OPEN, 15, false, false).pressurePa()).isCloseTo(89_760, within(300.0));
        assertThat(at(6000).solarWm2()).isBetween(600.0, 700.0); // полдень в начале весны: солнце на 40°
        assertThat(at(18_000).solarWm2()).isZero(); // полночь
        assertThat(ENV.sample(6000, 64, 10, OPEN, 15, true, false).solarWm2()).isCloseTo(0.2 * at(6000).solarWm2(), within(1e-9));
        assertThat(Environment.climateC(0.8)).isEqualTo(15);
        assertThat(Environment.climateC(2.0)).isEqualTo(30);
    }

    /** Вода теплее всего в конце лета и не замерзает ниже 0,5 °C; её размах меньше воздушного. */
    @Test
    void waterTemperatureLagsAndDampsTheAir() {
        double summer = ENV.waterTempC(42 * DAY, 15), winter = ENV.waterTempC(90 * DAY, 15);
        assertThat(summer - winter).isBetween(10.0, 14.1);
        assertThat(ENV.waterTempC(90 * DAY, -10)).isEqualTo(0.5);
    }

    /**
     * IEEE 738: в штиль — естественная конвекция ≈ 9 Вт/(м²·К) у АС-120 при перегреве 40 К; при 0,6 м/с
     * (расчётный ветер стандарта) — около 18; при 10 м/с (Re ≈ 9000, формула 3b) — около 85.
     */
    @Test
    void conductorConvectionGrowsWithWind() {
        double calm = ConductorSpec.convectionWm2K(0, 20, 100);
        double rating = ConductorSpec.convectionWm2K(0.6, 20, 100);
        double gale = ConductorSpec.convectionWm2K(10, 20, 100);
        assertThat(calm).isBetween(7.0, 11.0);
        assertThat(rating).isBetween(15.0, 22.0);
        assertThat(gale).isBetween(75.0, 100.0);
        assertThat(ConductorSpec.solarWPerM(1000)).isCloseTo(0.5 * 1000 * 0.0142, within(0.05)); // диаметр АС-120 с повивом
    }
}
