package dev.denfry.promptcritical.sim.utilities;

/**
 * Качество воды (08 §8.12, компонент {@code water_quality}). Единицы — СИ: жёсткость и щёлочность в моль-экв/м³
 * (численно равны мг-экв/л), растворённые примеси — массовые доли (кг/кг), взвесь — кг/м³, проводимость — См/м.
 * В узлах гидросети величины переносятся пассивными скалярами ({@link WaterScalars}).
 *
 * @param hardnessMolEqM3    общая жёсткость (Ca²⁺ + Mg²⁺)
 * @param o2KgKg             растворённый кислород
 * @param chlorideKgKg       хлориды Cl⁻
 * @param silicaKgKg         кремнекислота SiO₂
 * @param sodiumKgKg         натрий Na⁺
 * @param conductivitySm     удельная электропроводность κ
 * @param ph                 водородный показатель
 * @param suspendedKgM3      взвешенные вещества
 * @param alkalinityMolEqM3  щёлочность (бикарбонатная)
 */
public record WaterQuality(double hardnessMolEqM3, double o2KgKg, double chlorideKgKg, double silicaKgKg,
        double sodiumKgKg, double conductivitySm, double ph, double suspendedKgM3, double alkalinityMolEqM3) {
    /** Грамм-эквивалент CaCO₃, кг/моль-экв: перевод жёсткости и щёлочности «в CaCO₃» для индекса Ланжелье. */
    public static final double CACO3_KG_PER_EQ = 0.05004;
    /** Эквивалент Na⁺, кг/моль-экв. */
    public static final double SODIUM_KG_PER_EQ = 0.02299;
    /** Солесодержание по проводимости: TDS, мг/л = 0,64·κ, мкСм/см — то есть 6400·κ в См/м. */
    public static final double TDS_MG_L_PER_SM = 6400;
    /** Эквивалентная электропроводность нейтральных солей и сильной кислоты (H⁺), См·м²/моль-экв. */
    public static final double SALT_CONDUCTANCE = 0.01, ACID_CONDUCTANCE = 0.035;
    /** Плотность воды для перевода кг/кг ↔ кг/м³, кг/м³ (без поправки на температуру). */
    public static final double WATER_DENSITY = 1000;

    /**
     * Сырая речная вода средней жёсткости: 5 мг-экв/л (опорная вода накипи 07 §7.9.3), щёлочность 4 мг-экв/л,
     * кислород у насыщения 9 мг/кг, Cl⁻ 30, SiO₂ 10, Na⁺ 20 мг/кг, взвесь 30 мг/л, pH 7,6.
     */
    public static final WaterQuality RAW_RIVER = of(5, 9e-6, 30e-6, 10e-6, 20e-6, 7.6, 0.03, 4);

    public WaterQuality {
        if (!(hardnessMolEqM3 >= 0 && o2KgKg >= 0 && chlorideKgKg >= 0 && silicaKgKg >= 0 && sodiumKgKg >= 0
                && conductivitySm >= 0 && suspendedKgM3 >= 0 && alkalinityMolEqM3 >= 0))
            throw new IllegalArgumentException("water quality components must be >= 0");
        if (!(ph >= 0 && ph <= 14)) throw new IllegalArgumentException("ph: " + ph);
    }

    /** Вода с проводимостью, оценённой по составу ({@link #estimateConductivity}). */
    public static WaterQuality of(double hardnessMolEqM3, double o2KgKg, double chlorideKgKg, double silicaKgKg,
            double sodiumKgKg, double ph, double suspendedKgM3, double alkalinityMolEqM3) {
        return new WaterQuality(hardnessMolEqM3, o2KgKg, chlorideKgKg, silicaKgKg, sodiumKgKg,
                estimateConductivity(hardnessMolEqM3, sodiumKgKg, 0), ph, suspendedKgM3, alkalinityMolEqM3);
    }

    /**
     * Оценка κ по эквивалентам: катионы солей (жёсткость + Na⁺) × {@link #SALT_CONDUCTANCE} + свободная минеральная
     * кислотность (H⁺ после H-катионита) × {@link #ACID_CONDUCTANCE}. Речная вода 5,9 мг-экв/л → ≈ 590 мкСм/см.
     */
    public static double estimateConductivity(double hardnessMolEqM3, double sodiumKgKg, double acidityMolEqM3) {
        double na = sodiumKgKg * WATER_DENSITY / SODIUM_KG_PER_EQ;
        return SALT_CONDUCTANCE * (hardnessMolEqM3 + na) + ACID_CONDUCTANCE * Math.max(0, acidityMolEqM3);
    }

    /** Солесодержание, мг/л (по проводимости). */
    public double tdsMgL() {
        return TDS_MG_L_PER_SM * conductivitySm;
    }

    /** Натрий в эквивалентах, моль-экв/м³. */
    public double sodiumMolEqM3() {
        return sodiumKgKg * WATER_DENSITY / SODIUM_KG_PER_EQ;
    }

    /**
     * Индекс Ланжелье (Carrier, 1965): LSI = pH − pH_s, pH_s = (9,3 + A + B) − (C + D), A = (lg TDS − 1)/10,
     * B = −13,12·lg T_K + 34,55, C = lg[Ca²⁺ в CaCO₃, мг/л] − 0,4, D = lg[щёлочность в CaCO₃, мг/л]. Больше 0 — вода
     * откладывает накипь, меньше 0 — коррозионная. Вся жёсткость считается кальциевой (Mg отдельно не ведётся);
     * нулевые концентрации ограничены снизу 0,001 мг/л.
     */
    public double langelier(double tK) {
        double tds = Math.max(1, tdsMgL());
        double ca = Math.max(1e-3, hardnessMolEqM3 * CACO3_KG_PER_EQ * 1000);
        double alk = Math.max(1e-3, alkalinityMolEqM3 * CACO3_KG_PER_EQ * 1000);
        double a = (Math.log10(tds) - 1) / 10;
        double b = -13.12 * Math.log10(tK) + 34.55;
        double c = Math.log10(ca) - 0.4;
        double d = Math.log10(alk);
        return ph - ((9.3 + a + b) - (c + d));
    }

    public WaterQuality withHardness(double v) {
        return new WaterQuality(v, o2KgKg, chlorideKgKg, silicaKgKg, sodiumKgKg, conductivitySm, ph, suspendedKgM3,
                alkalinityMolEqM3);
    }

    public WaterQuality withO2(double v) {
        return new WaterQuality(hardnessMolEqM3, v, chlorideKgKg, silicaKgKg, sodiumKgKg, conductivitySm, ph,
                suspendedKgM3, alkalinityMolEqM3);
    }

    public WaterQuality withPh(double v) {
        return new WaterQuality(hardnessMolEqM3, o2KgKg, chlorideKgKg, silicaKgKg, sodiumKgKg, conductivitySm, v,
                suspendedKgM3, alkalinityMolEqM3);
    }

    public WaterQuality withSuspended(double v) {
        return new WaterQuality(hardnessMolEqM3, o2KgKg, chlorideKgKg, silicaKgKg, sodiumKgKg, conductivitySm, ph, v,
                alkalinityMolEqM3);
    }
}
