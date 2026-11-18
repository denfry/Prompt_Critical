package dev.denfry.promptcritical.sim.heavywater;

/**
 * Сорта воды по доле дейтерия, как их различают на тяжеловодных заводах и АЭС. В трубах и баках сорт — отдельная
 * жидкость с номинальной долей D: смешивать разные партии в одном баке нельзя, как и на станции (у сортов свои
 * баки). Машины внутри ведут долю непрерывно ({@link HeavyWaterInventory}) и выдают продукт сортом не выше
 * фактической чистоты ({@link #atMost}) — дейтерий не возникает из ничего.
 *
 * <ul>
 *   <li>{@link #LIGHT} — природная вода VSMOW;</li>
 *   <li>{@link #ENRICHED} — продукт сероводородного (GS) завода, 15 ат. % D: на Брюсе три ступени GS доводили
 *       воду до 10–30 %, а дальше её добирала перегонка (Miller, CNS Bulletin 22 (2001) 1);</li>
 *   <li>{@link #DOWNGRADED} — «разбавленная» тяжёлая вода из реактора и систем сбора утечек, 90 ат. % D, сырьё
 *       установки повышения концентрации (upgrader) на станции;</li>
 *   <li>{@link #REACTOR} — реакторная, 99,75 ат. % D.</li>
 * </ul>
 */
public enum HeavyWaterGrade {
    LIGHT(HeavyWater.NATURAL_X),
    ENRICHED(0.15),
    DOWNGRADED(0.90),
    REACTOR(HeavyWater.REACTOR_GRADE_X);

    /** Номинальная доля D. */
    public final double x;

    HeavyWaterGrade(double x) {
        this.x = x;
    }

    /** Высший сорт, номинал которого не выше {@code x}. */
    public static HeavyWaterGrade atMost(double x) {
        HeavyWaterGrade best = LIGHT;
        for (HeavyWaterGrade g : values()) if (g.x <= x + 1e-12) best = g;
        return best;
    }
}
