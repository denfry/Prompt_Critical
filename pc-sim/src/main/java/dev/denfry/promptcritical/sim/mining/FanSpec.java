package dev.denfry.promptcritical.sim.mining;

/**
 * Характеристика вентилятора (08 §8.2.7): h_f = h₀[1 − (Q/Q₀)²] на номинальной частоте, подобие Q ∝ n, h ∝ n²,
 * т. е. h_f(Q, n) = h₀(n/n₀)² − (h₀/Q₀²)·Q|Q|.
 *
 * @param h0Pa               давление при нулевой подаче на n₀, Па
 * @param q0M3s              подача при нулевом давлении на n₀, м³/с
 * @param ratedRpm           n₀, об/мин
 * @param efficiency         η_f (0,6–0,8)
 * @param reverseFlowFrac    доля подачи при реверсе (ПБ: не менее 60 % нормальной)
 */
public record FanSpec(double h0Pa, double q0M3s, double ratedRpm, double efficiency, double reverseFlowFrac) {
    public FanSpec {
        if (!(h0Pa > 0 && q0M3s > 0 && ratedRpm > 0)) throw new IllegalArgumentException("fan curve");
        if (!(efficiency > 0 && efficiency <= 1)) throw new IllegalArgumentException("efficiency: " + efficiency);
        if (!(reverseFlowFrac >= 0 && reverseFlowFrac <= 1)) throw new IllegalArgumentException("reverseFlowFrac");
    }

    /** Вентилятор с реверсом на 60 % подачи. */
    public static FanSpec of(double h0Pa, double q0M3s, double ratedRpm, double efficiency) {
        return new FanSpec(h0Pa, q0M3s, ratedRpm, efficiency, 0.6);
    }

    /** Внутреннее сопротивление характеристики h₀/Q₀², Н·с²/м⁸. */
    public double internalResistance() {
        return h0Pa / (q0M3s * q0M3s);
    }

    /**
     * Напор «источника» при частоте n (знак — направление вращения; реверс даёт меньший напор).
     */
    public double sourcePressurePa(double rpm) {
        double s = rpm / ratedRpm;
        return rpm >= 0 ? h0Pa * s * s : -h0Pa * s * s * reverseFlowFrac * reverseFlowFrac;
    }
}
