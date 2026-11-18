package dev.denfry.promptcritical.sim.ops;

/**
 * Тип сменного фильтра противогаза {@code gas_mask_filter} (13 §13.7, маркировка EN 14387 / ГОСТ 12.4.235): какую
 * примесь он задерживает и сколько её помещается в сорбент.
 *
 * <p>Ёмкость газовых фильтров — класс 1 по EN 14387: наименьшее время защитного действия при 1000 ppm (AX — 500 ppm) и
 * 30 л/мин, пересчитанное в моли при 24,45 л/моль: A — циклогексан 70 мин (0,086 моль), B — хлор 20 мин
 * (0,0245 моль, худший из газов группы, как у {@code PoweredRespirator}), E — SO₂ 20 мин, K — аммиак 50 мин, AX —
 * диметиловый эфир 50 мин. Гопкалитовый фильтр CO в EN 14387 не входит: 0,25 % CO 90 мин [сверить; ориентир паспортов
 * гопкалитовых коробок]. Противоаэрозольный P (EN 143) не насыщается по сорбции, а забивается пылью: ёмкость 1 г
 * задержанных частиц [BALANCE].
 */
public enum FilterType {
    A(Contaminant.ORGANIC_VAPOUR, 1000, 70),
    B(Contaminant.INORGANIC_GAS, 1000, 20),
    E(Contaminant.ACID_GAS, 1000, 20),
    K(Contaminant.AMMONIA, 1000, 50),
    CO(Contaminant.CARBON_MONOXIDE, 2500, 90),
    AX(Contaminant.LOW_BOILING_ORGANIC, 500, 50),
    P(Contaminant.PARTICLES, 0, 0);

    /** Мольный объём газа при 25 °C, л/моль. */
    public static final double MOLAR_VOLUME_L = 24.45;
    /** Расход воздуха испытания EN 14387 и лёгочная вентиляция при умеренной работе, л/мин. */
    public static final double TEST_FLOW_LPM = 30;
    /** Ёмкость противоаэрозольного фильтра, мг частиц [BALANCE]. */
    public static final double P_CAPACITY_MG = 1000;

    public final Contaminant stops;
    private final double testPpm, breakthroughMin;

    FilterType(Contaminant stops, double testPpm, double breakthroughMin) {
        this.stops = stops;
        this.testPpm = testPpm;
        this.breakthroughMin = breakthroughMin;
    }

    /** Ёмкость сорбента: моли газа, у фильтра P — миллиграммы частиц. */
    public double capacity() {
        if (this == P) return P_CAPACITY_MG;
        return testPpm * 1e-6 * TEST_FLOW_LPM * breakthroughMin / MOLAR_VOLUME_L;
    }

    /** Фильтр задерживает эту примесь. */
    public boolean stops(Contaminant c) {
        return c == stops;
    }
}
