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

    /** Ниже этой температуры отпуск не идёт, нагрев не засчитывается [моё: ниже цветов побежалости, 150 °C]. */
    public static final double MIN_TEMPER_K = 423.15;

    /**
     * Отпуск при {@code tK} в течение {@code seconds}: закалённая или уже отпущенная заготовка. Нагревы
     * складываются по правилу эквивалентного времени для параметра Холломона-Яффе: прежний P пересчитывается во
     * время при новой температуре t_экв = 10^(P/T − 20) ч, к нему добавляется новый нагрев. Поэтому второй, более
     * горячий отпуск снижает твёрдость дальше, а более холодный почти ничего не меняет. Твёрдость считается от
     * твёрдости после закалки ({@link Workpiece#quenchHrc()}) линейно по P к нормализованной.
     */
    public static Workpiece temper(Workpiece w, double tK, double seconds) {
        HeatTreatState st = w.grade().state();
        if ((st != HeatTreatState.QUENCHED && st != HeatTreatState.QUENCHED_TEMPERED) || !(w.quenchHrc() > 0)
                || !Double.isFinite(tK) || !(tK >= MIN_TEMPER_K) || !Double.isFinite(seconds) || !(seconds > 0)) {
            return w;
        }
        double prevHours = w.temperP() > 0 ? Math.pow(10, w.temperP() / tK - 20) : 0;
        double p = Math.max(w.temperP(), hollomonJaffe(tK, prevHours + seconds / 3600));
        double f = Math.max(0, Math.min(1, (p - P_LOW) / (P_HIGH - P_LOW)));
        SteelGrade g = w.grade();
        double hq = w.quenchHrc();
        double hn = g.withState(HeatTreatState.NORMALIZED).hardnessHrc();
        return w.withGrade(g.withState(HeatTreatState.QUENCHED_TEMPERED)).withHardness(hq - (hq - hn) * f)
                .withTreatment(hq, p);
    }
}
