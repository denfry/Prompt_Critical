package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Пропуск через клеть (08 §8.7.2).
 *
 * @param out     заготовка после пропуска; при отказе — входная
 * @param forceN  усилие на валки, Н
 * @param torqueNm момент на валках, Н·м
 * @param powerW  мощность на валу, Вт (мод делит на W_PER_SU)
 */
public record PassResult(Outcome outcome, StockState out, double forceN, double torqueNm, double powerW) {
    public enum Outcome {
        ROLLED,
        /** Обжатие больше μ²R: валки не захватили. */
        NOT_BITTEN,
        /** Момент выше предела: срезной штифт ({@code ROLL_OVERLOAD}). */
        SHEAR_PIN
    }
}
