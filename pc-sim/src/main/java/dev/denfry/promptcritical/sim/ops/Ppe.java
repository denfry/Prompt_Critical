package dev.denfry.promptcritical.sim.ops;

/**
 * Общепромышленные СИЗ (13 §13.7): коэффициенты защиты и ослабления, по которым мод меняет действие газа, дозы,
 * шума, падения и огня на игрока. Радиационные СИЗ 04b §4b.7.3 здесь не дублируются.
 */
public final class Ppe {
    /** Кратность фильтрующего противогаза по газу своего фильтра (13 §13.7). */
    public static final double GAS_MASK_FACTOR = 50;
    /** Кратность изолирующего дыхательного аппарата (13 §13.7). */
    public static final double SCBA_FACTOR = 10_000;
    /** Ниже этой доли кислорода фильтрующий противогаз не защищает вовсе (13 §13.7, §13.2.6). */
    public static final double MASK_MIN_O2 = 0.17;
    /** Каска: урон от падающих предметов и блоков ×0,3 (13 §13.7). */
    public static final double HARD_HAT_FALLING = 0.3;
    /** Огнезащитный костюм: тепловое излучение ×0,1, горение ×0,2 (13 §13.7). */
    public static final double FIRE_SUIT_RADIANT = 0.1, FIRE_SUIT_BURN = 0.2;
    /** Огнезащитный костюм: перегрев игрока после 5 мин активной работы (13 §13.7). */
    public static final double FIRE_SUIT_OVERHEAT_S = 300;
    /** Длина фала страховочной привязи, м (13 §13.7). */
    public static final double LIFELINE_M = 16;
    /** Свободное падение на привязи с закреплённым фалом, м: урона нет (13 §13.7). */
    public static final double ARRESTED_FALL_M = 2;
    /** Лёгочная вентиляция при умеренной работе, л/мин (испытательный расход EN 14387). */
    public static final double BREATHING_LPM = FilterType.TEST_FLOW_LPM;

    private Ppe() {
    }

    /**
     * Фильтрующая маска вообще может защитить: при нехватке кислорода и от удушающих газов — нет (13 §13.2.6: «почему
     * фильтрующий противогаз не спасает»).
     */
    public static boolean maskWorks(Contaminant c, double o2) {
        return c != Contaminant.ASPHYXIANT && o2 >= MASK_MIN_O2;
    }

    /** Группа фильтра для токсичного облака выброса (04c): хлор и H₂S — B, SO₂ и HF — E. */
    public static Contaminant contaminant(dev.denfry.promptcritical.sim.consequence.ToxicGas gas) {
        return switch (gas) {
            case CHLORINE, HYDROGEN_SULFIDE -> Contaminant.INORGANIC_GAS;
            case HYDROGEN_FLUORIDE, SULFUR_DIOXIDE -> Contaminant.ACID_GAS;
        };
    }
}
