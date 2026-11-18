package dev.denfry.promptcritical.sim.ops;

/**
 * Огневые работы (13 §13.2.5): действие, способное воспламенить горючую атмосферу, — открытый огонь, «сварка» трубы к
 * сети с горючей средой — в радиусе {@code ops.hot_work_radius_m} = 11 м от такой среды.
 *
 * <p>Два независимых вопроса. Разрешено ли действие ({@link #verdict}) — по наличию действующего наряда {@code HOT} и
 * уровню принуждения. Воспламенится ли атмосфера ({@link #ignites}) — физика, она от бумаг не зависит: домен
 * опрашивает 27 точек вокруг места (куб 3×3×3), и если хоть в одной горючие не ниже 100 % НКПР·k_ign — вспышка облака
 * или взрыв. С нарядом в {@code ACTIVE} (замер 0 % НКПР, пожарный пост, изоляция) остаётся риск только от того, что
 * изменилось после анализа, — поэтому при наряде контроль непрерывный.
 */
public final class HotWork {
    /** k_ign — порог воспламенения в долях НКПР (§13.2.5). */
    public static final double K_IGN = 1.0;
    /** Точек опроса атмосферы вокруг места (§13.2.5). */
    public static final int SAMPLE_POINTS = 27;

    /** Что делать с огневым действием. */
    public enum Verdict {
        /** Не огневые работы (рядом нет горючей среды) или есть действующий наряд. */
        ALLOW,
        /** Наряда нет, уровень {@code ADVISORY}: выполнить, предупредить и записать {@code UNAUTHORIZED_WORK}. */
        WARN,
        /** Наряда нет, уровень {@code ENFORCED}: не выполнять. */
        REFUSE
    }

    private HotWork() {
    }

    /**
     * @param flammableNear  в радиусе 11 м есть горючая среда или горючая атмосфера
     * @param permitActive   место в объёме действующего наряда {@code HOT}
     */
    public static Verdict verdict(PermitEnforcement mode, boolean flammableNear, boolean permitActive) {
        if (!flammableNear || permitActive || mode == PermitEnforcement.OFF) return Verdict.ALLOW;
        return mode == PermitEnforcement.ENFORCED ? Verdict.REFUSE : Verdict.WARN;
    }

    /** Воспламенение: наибольшая доля НКПР по точкам опроса не ниже k_ign. */
    public static boolean ignites(double[] lelFractions) {
        for (double x : lelFractions) if (x >= K_IGN) return true;
        return false;
    }
}
