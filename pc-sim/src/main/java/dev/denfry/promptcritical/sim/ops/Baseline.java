package dev.denfry.promptcritical.sim.ops;

/**
 * Базовые показатели пробного пуска в паспорт (13 §13.6.6); неприменимое — NaN.
 *
 * @param flueGasK   температура уходящих газов, K
 * @param headM      напор при номинальной подаче, м
 * @param bearingK   температура корпуса подшипника, K
 * @param speedRadS  обороты, рад/с
 * @param dpPa       перепад давления (депрессия вентилятора), Па
 */
public record Baseline(double efficiency, double flueGasK, double headM, double bearingK, double speedRadS,
        double dpPa) {
    public static final Baseline NONE = new Baseline(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            Double.NaN);
}
