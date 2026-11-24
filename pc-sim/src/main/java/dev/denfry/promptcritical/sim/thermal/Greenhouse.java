package dev.denfry.promptcritical.sim.thermal;

/**
 * Теплица на сбросном тепле (17 §17.3.6). Теплопотери — через ограждение и с воздухообменом:
 * Q = U·A·(T_вн − T_a) + 0,33·n·V·(T_вн − T_a), где U = 5,8 Вт/(м²·К) для одинарного стекла и 3,0 для двойного,
 * n = 0,5 1/ч, V — объём, м³ (0,33 Вт·ч/(м³·К) — объёмная теплоёмкость воздуха). Тепло даёт регистр
 * {@code greenhouse_coil} из охлаждающей воды конденсатора: при охлаждении воды на 10 К нужен расход Q/(c·10).
 * Регистр держит уставку 18 °C, пока хватает тепла; без него внутри остывает к наружной температуре.
 *
 * <p>Рост — по кардинальным температурам (Yin и др., 1995): f(T) с T_min = 5, T_opt = 24, T_max = 36 °C,
 * множитель роста m = 1 + 0,5·f(T_вн) [BALANCE]; ниже 5 °C рост останавливается.
 *
 * <p>Теплоёмкость объёма — 10 кДж/(м³·К) (воздух, грунт гряд и каркас) [оценка]: от неё зависит только, как быстро
 * теплица остывает и прогревается, а не равновесие. Глухая стена (не стекло) — U = 0,5 Вт/(м²·К) [оценка, кладка
 * с утеплением].
 */
public final class Greenhouse {
    public static final double U_SINGLE_GLASS = 5.8, U_DOUBLE_GLASS = 3.0, U_OPAQUE = 0.5;
    public static final double AIR_CHANGES_PER_H = 0.5, AIR_WH_M3K = 0.33;
    public static final double T_MIN_C = 5, T_OPT_C = 24, T_MAX_C = 36, SETPOINT_C = 18;
    public static final double HEAT_CAPACITY_J_M3K = 10e3;
    /** Парник 1×1 без обогрева теплее наружного воздуха днём [BALANCE]. */
    public static final double COLD_FRAME_DAY_GAIN_K = 8;
    public static final double WATER_CP = 4186, WATER_COOLING_K = 10;
    /** Наибольший объём теплицы, блоков (как у вент-зоны 03a §3a.9). */
    public static final int MAX_VOLUME = 4096;

    private final double uaWK, volumeM3;
    private double insideC;
    private double heatW, lossW;

    /**
     * @param uaWK      сумма U·A по ограждению, Вт/К
     * @param volumeM3  объём, м³
     * @param startC    начальная температура внутри, °C
     */
    public Greenhouse(double uaWK, double volumeM3, double startC) {
        if (uaWK < 0 || volumeM3 <= 0) throw new IllegalArgumentException("UA >= 0 and V > 0");
        this.uaWK = uaWK;
        this.volumeM3 = volumeM3;
        this.insideC = startC;
    }

    /** Полный коэффициент потерь, Вт/К: ограждение плюс воздухообмен. */
    public static double lossCoefficientWK(double uaWK, double volumeM3) {
        return uaWK + AIR_WH_M3K * AIR_CHANGES_PER_H * volumeM3;
    }

    /** Теплопотери, Вт, при температурах внутри и снаружи. */
    public static double heatLossW(double uaWK, double volumeM3, double insideC, double ambientC) {
        return lossCoefficientWK(uaWK, volumeM3) * (insideC - ambientC);
    }

    /** Кардинальная функция температуры f(T), 0…1; вне (T_min, T_max) — 0. */
    public static double cardinal(double tC) {
        if (tC <= T_MIN_C || tC >= T_MAX_C) return 0;
        double a = (tC - T_MIN_C) / (T_OPT_C - T_MIN_C);
        double b = (T_MAX_C - tC) / (T_MAX_C - T_OPT_C);
        return a * Math.pow(b, (T_MAX_C - T_OPT_C) / (T_OPT_C - T_MIN_C));
    }

    /** Множитель роста в теплице; 0 — рост остановлен (холоднее 5 °C или жарче 36 °C). */
    public static double growthMultiplier(double tC) {
        double f = cardinal(tC);
        return f <= 0 ? 0 : 1 + 0.5 * f;
    }

    /** Температура под парником 1×1: днём на 8 K выше наружной, ночью — как снаружи. */
    public static double coldFrameC(double ambientC, boolean day) {
        return ambientC + (day ? COLD_FRAME_DAY_GAIN_K : 0);
    }

    /** Расход воды через регистр, кг/с, чтобы отдать {@code heatW} при охлаждении воды на 10 К. */
    public static double coilWaterKgS(double heatW) {
        return Math.max(0, heatW) / (WATER_CP * WATER_COOLING_K);
    }

    /**
     * Шаг: регистр берёт из воды не больше {@code availableW} — сколько нужно, чтобы держать уставку.
     *
     * @return тепло, взятое регистром за шаг, Вт
     */
    public double step(double dtS, double ambientC, double availableW) {
        double k = lossCoefficientWK(uaWK, volumeM3), c = HEAT_CAPACITY_J_M3K * volumeM3;
        // сколько нужно, чтобы к концу шага прийти ровно к уставке (потери — при уставке, как в неявном шаге ниже)
        double need = k * (SETPOINT_C - ambientC) + c * (SETPOINT_C - insideC) / dtS;
        heatW = Math.max(0, Math.min(Math.max(0, availableW), need));
        // неявный шаг по потерям: устойчив при любом dt
        insideC = (insideC * c / dtS + heatW + k * ambientC) / (c / dtS + k);
        lossW = k * (insideC - ambientC);
        return heatW;
    }

    public double insideC() {
        return insideC;
    }

    /** Задать температуру внутри, °C (восстановление и испытания). */
    public void setInsideC(double c) {
        insideC = c;
    }

    public double heatW() {
        return heatW;
    }

    public double lossW() {
        return lossW;
    }

    public double uaWK() {
        return uaWK;
    }

    public double volumeM3() {
        return volumeM3;
    }
}
