package dev.denfry.promptcritical.sim.environment;

/**
 * Погода одного измерения: температура и плотность воздуха, ветер, солнце — чистая функция зерна мира, игрового
 * времени и места (доля года, высота, шероховатость, климат биома, ванильный дождь и гроза). Сохранять нечего.
 *
 * <p>Ветер — три масштаба с провалом между ними (спектр Ван дер Ховена): синоптический (дни, проходящие циклоны),
 * суточный и турбулентность (секунды–минуты). Средний ветер на 10 м логнормален около {@link #MEAN_WIND_MS}; дождь
 * и гроза его усиливают, зимой ветренее. Турбулентность — нормальная модель IEC 61400-1: σ = I_ref·(0,75·V + 5,6).
 * Высота — логарифмический профиль v(z) = v₁₀·ln(z/z₀)/ln(10/z₀).
 *
 * <p>Температура: средняя климата биома ± сезонная и суточная волны, синоптическая аномалия, дождь холоднее.
 * Солнце: ясное небо ≈ 1000 Вт/м² при солнце в зените, sin высоты; облачность дождя пропускает 20 %.
 * Константы помечены: [физ.] — справочные, [BALANCE] — подобраны для игры.
 */
public final class Environment {
    /** Средний ветер на 10 м, м/с [BALANCE: типичный умеренный климат 4–6]. */
    public static final double MEAN_WIND_MS = 5;
    /** СКО логарифма синоптического ветра [BALANCE: даёт распределение, близкое к Вейбуллу с k ≈ 2]. */
    static final double WIND_LOG_SIGMA = 0.45;
    /** Сезонная и суточная доли размаха ветра; множители дождя и грозы [BALANCE]. */
    static final double WIND_SEASONAL = 0.2, WIND_DIURNAL = 0.2, WIND_RAIN = 1.3, WIND_THUNDER = 2.0;
    /** Интенсивность турбулентности, класс B IEC 61400-1. */
    public static final double TURBULENCE_I_REF = 0.14;
    /** Синоптический масштаб, игровых суток (пик спектра около четырёх суток) [физ.]. */
    static final double SYNOPTIC_DAYS = 4;
    /** Корреляционный масштаб турбулентности, с (пик спектра около минуты) [физ.]. */
    static final double TURBULENCE_S = 30;
    /** Сезонная и суточная полуамплитуды температуры, К; синоптическая СКО, К; дождь, К [BALANCE, умеренный пояс]. */
    static final double TEMP_SEASONAL_K = 10, TEMP_DIURNAL_K = 5, TEMP_SYNOPTIC_K = 3, TEMP_RAIN_K = -3;
    /** Ясное небо при солнце в зените, Вт/м²; пропускание облаков дождя [физ., порядок]. */
    static final double CLEAR_SKY_WM2 = 1000, RAIN_TRANSMITTANCE = 0.2;
    /** Уровень моря мира, блок; шкала высот атмосферы, м [физ.]. */
    static final double SEA_LEVEL_Y = 63, SCALE_HEIGHT_M = 8400;
    static final double R_AIR = 287.05, P0 = 101_325;

    private final SeasonProvider seasons;
    private final TimeNoise synopticWind, synopticTemp, gust, direction;

    public Environment(long seed, SeasonProvider seasons) {
        this.seasons = seasons;
        synopticWind = new TimeNoise(seed ^ 0x57494E44L);
        synopticTemp = new TimeNoise(seed ^ 0x54454D50L);
        gust = new TimeNoise(seed ^ 0x47555354L);
        direction = new TimeNoise(seed ^ 0x44495252L);
    }

    public SeasonProvider seasons() {
        return seasons;
    }

    /**
     * Погода в точке.
     *
     * @param gameTime    игровое время, тики
     * @param y           высота блока над нулём мира
     * @param heightM     высота над землёй (ступица, провод), м
     * @param roughnessM  шероховатость z₀, м ({@link #roughness})
     * @param climateC    средняя годовая температура климата биома, °C ({@link #climateC})
     */
    public Sample sample(long gameTime, double y, double heightM, double roughnessM, double climateC, boolean raining,
            boolean thundering) {
        double year = seasons.yearFraction(gameTime);
        double days = gameTime / (double) SeasonProvider.TICKS_PER_DAY;
        double hour = (Math.floorMod(gameTime, SeasonProvider.TICKS_PER_DAY) / 1000.0 + 6) % 24; // 0 тиков — 6:00
        // середина лета — 0,375 года, середина зимы — 0,875
        double seasonal = Math.cos(2 * Math.PI * (year - 0.375));

        double tempC = climateC + TEMP_SEASONAL_K * seasonal
                + TEMP_DIURNAL_K * Math.cos(2 * Math.PI * (hour - 15) / 24)
                + TEMP_SYNOPTIC_K * synopticTemp.normal(days / SYNOPTIC_DAYS)
                + (raining ? TEMP_RAIN_K : 0);

        double weather = thundering ? WIND_THUNDER : raining ? WIND_RAIN : 1;
        double mean10 = MEAN_WIND_MS * weather * (1 - WIND_SEASONAL * seasonal)
                * (1 + WIND_DIURNAL * Math.cos(2 * Math.PI * (hour - 14) / 24))
                * Math.exp(WIND_LOG_SIGMA * synopticWind.normal(days / SYNOPTIC_DAYS) - WIND_LOG_SIGMA * WIND_LOG_SIGMA / 2);
        double z = Math.max(heightM, 2 * roughnessM + 0.5);
        double mean = mean10 * Math.log(z / roughnessM) / Math.log(10 / roughnessM);
        double sigma = TURBULENCE_I_REF * (0.75 * mean + 5.6);
        double seconds = gameTime / 20.0;
        double wind = Math.max(0, mean + sigma * gust.normal(seconds / TURBULENCE_S));
        double dirRad = Math.PI * direction.at(days / SYNOPTIC_DAYS) * 2;

        double sunElevation = Math.sin(2 * Math.PI * Math.floorMod(gameTime, SeasonProvider.TICKS_PER_DAY)
                / SeasonProvider.TICKS_PER_DAY) * (0.8 + 0.2 * seasonal);
        double solar = Math.max(0, sunElevation) * CLEAR_SKY_WM2 * (raining ? RAIN_TRANSMITTANCE : 1);

        double pressure = P0 * Math.exp(-(y - SEA_LEVEL_Y) / SCALE_HEIGHT_M);
        double density = pressure / (R_AIR * (tempC + 273.15));
        return new Sample(tempC, mean, wind, dirRad, density, pressure, solar, Math.max(0, sunElevation), year,
                SeasonProvider.season(year));
    }

    /**
     * Относительная влажность, %: 70 % в среднем, днём суше (минимум к 15 часам, ±15 %), в дождь 95 % [BALANCE,
     * умеренный пояс].
     */
    public double relativeHumidity(long gameTime, boolean raining) {
        if (raining) return 95;
        double hour = (Math.floorMod(gameTime, SeasonProvider.TICKS_PER_DAY) / 1000.0 + 6) % 24;
        return 70 - 15 * Math.cos(2 * Math.PI * (hour - 15) / 24);
    }

    /**
     * Температура мокрого термометра, °C — формула Stull (2011), J. Appl. Meteor. Climatol. 50: ±0,3 °C при
     * RH 5–99 % и T от −20 до 50 °C.
     */
    public static double wetBulbC(double tC, double rhPercent) {
        double rh = Math.max(5, Math.min(99, rhPercent));
        return tC * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(tC + rh) - Math.atan(rh - 1.676331)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035;
    }

    /**
     * Температура воды реки или пруда, °C: годовая волна воздуха, сглаженная (амплитуда 0,7) и запаздывающая на
     * ≈ 3 недели (0,06 года) из-за теплоёмкости воды; не ниже 0,5 °C — подо льдом вода не холоднее [BALANCE].
     */
    public double waterTempC(long gameTime, double climateC) {
        double year = seasons.yearFraction(gameTime);
        return Math.max(0.5, climateC + 0.7 * TEMP_SEASONAL_K * Math.cos(2 * Math.PI * (year - 0.375 - 0.06)));
    }

    /**
     * Средняя годовая температура климата по температуре биома Minecraft (равнины 0,8, пустыня 2,0, снежная
     * тундра 0,0): 15 °C у равнин, ±18 К на единицу, от −20 до +30 °C [BALANCE].
     */
    public static double climateC(double biomeTemperature) {
        return Math.max(-20, Math.min(30, 15 + 18 * (biomeTemperature - 0.8)));
    }

    /** Шероховатость подстилающей поверхности z₀, м [физ.]: вода 0,0002, трава 0,03, кустарник 0,1, лес 0,8. */
    public static double roughness(Surface s) {
        return switch (s) {
            case WATER -> 0.0002;
            case OPEN -> 0.03;
            case SHRUB -> 0.1;
            case FOREST -> 0.8;
        };
    }

    public enum Surface { WATER, OPEN, SHRUB, FOREST }

    /**
     * Погода в точке.
     *
     * @param airTempC     температура воздуха, °C
     * @param meanWindMs   средний ветер на заданной высоте, м/с
     * @param windMs       мгновенный ветер с турбулентностью, м/с
     * @param windDirRad   направление, откуда дует, рад
     * @param densityKgM3  плотность воздуха
     * @param pressurePa   давление
     * @param solarWm2     суммарная радиация на горизонтальную площадку
     * @param sunElevation синус высоты солнца
     * @param yearFraction доля года от начала весны
     */
    public record Sample(double airTempC, double meanWindMs, double windMs, double windDirRad, double densityKgM3,
            double pressurePa, double solarWm2, double sunElevation, double yearFraction, SeasonProvider.Season season) {
    }
}
