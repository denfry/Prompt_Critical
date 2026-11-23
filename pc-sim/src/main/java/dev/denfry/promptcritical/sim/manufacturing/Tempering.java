package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;

/** Отпуск закалённой заготовки по параметру Холломона-Яффе. */
public final class Tempering {
    private Tempering() {}

    /** P ниже которого отпуск ещё не снижает твёрдость [сверить по кривым отпуска ASM для 1045]. */
    public static final double P_LOW = 9000;
    /** P, с которого мартенсит полностью распался до нормализованной твёрдости [сверить по кривым отпуска ASM для 1045]. */
    public static final double P_HIGH = 18500;

    /** P = T·(20 + lg t), T в К, t в часах (Hollomon, Jaffe, 1945). */
    public static double hollomonJaffe(double tK, double hours) {
        return tK * (20 + Math.log10(hours));
    }

    /**
     * Отпуск при {@code tK} в течение {@code seconds}. Только для закалки (QUENCHED), иначе та же заготовка.
     * Твёрдость падает линейно по P от закалённой к нормализованной, состояние становится QUENCHED_TEMPERED.
     */
    public static Workpiece temper(Workpiece w, double tK, double seconds) {
        if (w.grade().state() != HeatTreatState.QUENCHED || !Double.isFinite(tK) || !(tK > 0)
                || !Double.isFinite(seconds) || !(seconds > 0)) {
            return w;
        }
        double p = hollomonJaffe(tK, seconds / 3600);
        double f = Math.max(0, Math.min(1, (p - P_LOW) / (P_HIGH - P_LOW)));
        SteelGrade g = w.grade();
        double hq = w.hardnessHrc();
        double hn = g.withState(HeatTreatState.NORMALIZED).hardnessHrc();
        return w.withGrade(g.withState(HeatTreatState.QUENCHED_TEMPERED)).withHardness(hq - (hq - hn) * f);
    }
}
