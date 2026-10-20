package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Компонент {@code ladle_state} сталеразливочного ковша (08 §8.6.5, §8.17): ковш обязан быть сухим и разогретым.
 *
 * @param preheatedK     температура футеровки, К
 * @param wet            в ковше вода (после ремонта футеровки или дождя): расплав в воду — {@link MoltenWater}
 * @param refractoryWear износ футеровки 0…1; 1 — прогар ковша
 */
public record LadleState(double preheatedK, boolean wet, double refractoryWear) {
    /** Футеровка разогрета, К (800 °C); полная разогретость — 1100 °C. */
    public static final double READY_K = 1073.15, HOT_K = 1373.15;
    /** Захолаживание металла разогретым ковшом, К, и добавка на кельвин недогрева [BALANCE]. */
    public static final double CHILL_BASE_K = 25, CHILL_PER_K = 0.08;
    /** Износ за плавку: ≈ 100 плавок кампании [BALANCE]. */
    public static final double WEAR_PER_HEAT = 0.01;

    public LadleState {
        if (!(preheatedK > 0)) throw new IllegalArgumentException("preheatedK");
        if (!(refractoryWear >= 0)) throw new IllegalArgumentException("refractoryWear");
    }

    /** Новый ковш после футеровки: холодный и сырой. */
    public static LadleState fresh() {
        return new LadleState(293.15, true, 0);
    }

    /** Готов принять сталь: сухой, разогрет не ниже {@link #READY_K}, футеровка цела. */
    public boolean ready() {
        return !wet && preheatedK >= READY_K && refractoryWear < 1;
    }

    /** Захолаживание стали при наливе, К. */
    public double pourChillK() {
        return CHILL_BASE_K + CHILL_PER_K * Math.max(0, HOT_K - preheatedK);
    }

    /** Масса расплава, попавшая на воду за тик налива, кг: весь налив тика, если ковш сырой. */
    public double waterContactKg(double pouredKgThisTick) {
        return wet ? pouredKgThisTick : 0;
    }

    /** Сталь после налива в этот ковш. */
    public SteelMelt receive(SteelMelt melt) {
        return melt.withTemp(melt.tempK() - pourChillK());
    }

    /** Сушка и разогрев горелкой до температуры. */
    public LadleState preheated(double tempK) {
        return new LadleState(tempK, false, refractoryWear);
    }

    /** После плавки: износ футеровки, футеровка горячая. */
    public LadleState afterHeat(double steelTempK) {
        return new LadleState(Math.max(preheatedK, steelTempK - 300), wet, refractoryWear + WEAR_PER_HEAT);
    }
}
