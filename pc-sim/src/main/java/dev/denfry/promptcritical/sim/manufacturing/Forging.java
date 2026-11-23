package dev.denfry.promptcritical.sim.manufacturing;

/** Ковка: размерные пределы. Остальное содержимое добавляет следующая задача. */
public final class Forging {
    private Forging() {}

    /** Минимальное сечение заготовки, м: тоньше уже не поковка, а проволока [BALANCE]. */
    public static final double MIN_SECTION_M = 0.003;
}
