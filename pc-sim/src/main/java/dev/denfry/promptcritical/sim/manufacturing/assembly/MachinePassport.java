package dev.denfry.promptcritical.sim.manufacturing.assembly;

/**
 * Паспорт машины (18 §18.8.3): свёртка качества деталей в начальное состояние модели обслуживания 13. Хранит не
 * детали, а уже выведенные начальные значения, квантованные, чтобы одинаково собранные машины стакались.
 *
 * @param bearingWearFactor множитель скорости роста {@code BEARING_WEAR}, 1 — номинал
 * @param imbalanceMmS      начальная виброскорость от дисбаланса, мм/с ({@code IMBALANCE})
 * @param misalignmentMm    начальная несоосность, мм ({@code MISALIGNMENT})
 * @param sealLeak          начальная течь уплотнения вала ({@code SEAL_LEAK})
 * @param flangeLeak        начальная течь по фланцам ({@code FLANGE_LEAK})
 * @param insulationMOhm    начальное сопротивление изоляции обмотки, МОм; NaN — машина без обмотки
 * @param efficiencyFactor  множитель КПД двигателя или генератора от потерь в пакете, 1 — номинал
 * @param score             общая оценка сборки 0…100 для подсказки и CC
 */
public record MachinePassport(double bearingWearFactor, double imbalanceMmS, double misalignmentMm, double sealLeak,
                              double flangeLeak, double insulationMOhm, double efficiencyFactor, int score) {
    private static final MachinePassport NOMINAL = new MachinePassport(1, 0, 0, 0, 0, Double.NaN, 1, 100);

    public MachinePassport {
        if (!(bearingWearFactor > 0) || !finiteNonNegative(imbalanceMmS) || !finiteNonNegative(misalignmentMm)
                || !finiteNonNegative(sealLeak) || !finiteNonNegative(flangeLeak)
                || !(efficiencyFactor > 0 && efficiencyFactor <= 1.05)
                || !(Double.isNaN(insulationMOhm) || insulationMOhm > 0) || score < 0 || score > 100) {
            throw new IllegalArgumentException("bad passport");
        }
        bearingWearFactor = Math.max(0.05, q(bearingWearFactor, 0.05));
        imbalanceMmS = q(imbalanceMmS, 0.1);
        misalignmentMm = q(misalignmentMm, 0.005);
        sealLeak = q(sealLeak, 0.001);
        flangeLeak = q(flangeLeak, 0.01);
        efficiencyFactor = q(efficiencyFactor, 0.005);
        if (!Double.isNaN(insulationMOhm)) insulationMOhm = qLog(insulationMOhm);
    }

    /** Машина без паспорта: креатив, мир rc.1, старый предмет (§18.15.3). Ничего не меняет в модели 13. */
    public static MachinePassport nominal() {
        return NOMINAL;
    }

    public boolean hasWinding() {
        return !Double.isNaN(insulationMOhm);
    }

    private static boolean finiteNonNegative(double v) {
        return Double.isFinite(v) && v >= 0;
    }

    private static double q(double v, double step) {
        return Math.round(v / step) * step;
    }

    /** Два значащих знака: изоляция меняется на порядки. */
    private static double qLog(double v) {
        double mag = Math.pow(10, Math.floor(Math.log10(v)) - 1);
        return Math.round(v / mag) * mag;
    }
}
