package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Изложница {@code ingot_mold} (08 §8.7.1, I0–I1): усадочная раковина уходит в обрезь 12–18 %. Раскисленная сталь
 * даёт раковину (с прибыльной надставкой — 12 %, без — 17 %), нераскисленная кипит: раковины почти нет (10 %), но
 * слиток пористый [моё по смыслу 08 §8.6.2]. Разливка ближе {@link #LAP_SUPERHEAT_K} к ликвидусу — поверхностные
 * плены.
 */
public final class IngotMold {
    public static final double CROP_HOT_TOP = 0.12, CROP_KILLED = 0.17, CROP_RIMMED = 0.10;
    public static final double LAP_SUPERHEAT_K = 30;

    private IngotMold() {}

    /** Слиток из ковша; {@code hotTop} — прибыльная надставка с утеплением. */
    public static SteelIngot cast(SteelMelt melt, boolean hotTop) {
        boolean killed = melt.killed();
        double crop = killed ? (hotTop ? CROP_HOT_TOP : CROP_KILLED) : CROP_RIMMED;
        boolean laps = melt.tempK() < melt.liquidusK() + LAP_SUPERHEAT_K;
        double m = melt.massKg();
        return new SteelIngot(m * (1 - crop), m * crop, melt.chem(), !killed, laps);
    }
}
