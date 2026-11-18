package dev.denfry.promptcritical.sim.nonferrous;

/**
 * Аффинаж платиноидов {@code pgm_refinery} (08 §8.8.4): анодный шлам медного и никелевого рафинирования → обезмеживание
 * серной кислотой → {@code pgm_concentrate} → растворение в царской водке (HNO₃ 02 + HCl 10) → платина осаждается
 * хлоридом аммония, иридий — из маточника; осмий и рутений — (B). Сквозное извлечение {@value #RECOVERY} (§8.8.4:
 * 70–85 %). Доли в сумме платиноидов: иридий {@value #IR_SHARE} (§8.8.4: 2–4 %), платина {@value #PT_SHARE}
 * [BALANCE: в норильских рудах Pd:Pt ≈ 3:1, в садберийских ≈ 1:1]; палладий и родий отдельным металлом в игре не
 * выделяются (нет ID в GDD) и остаются в учёте аффинажа.
 *
 * <p>Порция: один предмет шлама или концентрата несёт {@value #PGM_PER_LOT_KG} кг платиноидов [BALANCE]. Реагенты на
 * порцию [BALANCE]: серная кислота {@value #ACID_L_PER_LOT} л, азотная {@value #NITRIC_L_PER_LOT} л, хлороводород
 * {@value #HCL_NM3_PER_LOT} нм³. Длительность стадии — {@value #STAGE_S} с физ. Термохимия — SLOW.
 */
public final class PgmRefinery {
    public static final double PGM_PER_LOT_KG = 0.05, RECOVERY = 0.775, PT_SHARE = 0.35, IR_SHARE = 0.03;
    public static final double ACID_L_PER_LOT = 5, NITRIC_L_PER_LOT = 1, HCL_NM3_PER_LOT = 3;
    public static final double STAGE_S = 600;

    private PgmRefinery() {
    }

    /** Платина с одной порции концентрата, кг. */
    public static double platinumPerLot() {
        return PGM_PER_LOT_KG * RECOVERY * PT_SHARE;
    }

    /** Иридий с одной порции концентрата, кг. */
    public static double iridiumPerLot() {
        return PGM_PER_LOT_KG * RECOVERY * IR_SHARE;
    }
}
