package dev.denfry.promptcritical.sim.grid;

import java.util.Objects;

/**
 * ZIP-нагрузка с частотной зависимостью (07 §7.1.2, §7.6.1):
 * P = P₀(a_Z v² + a_I v + a_P)(1 + D_f Δf/f₀), Q = Q₀(a_Z v² + a_I v + a_P).
 * Отрицательная P₀ — источник без инерции (FE-интерфейс, выпрямитель в инверторном режиме).
 *
 * @param dF регулирующий эффект; NaN — {@code grid.load_damping}
 */
public record LoadModel(double p0W, double q0Var, double aZ, double aI, double aP, double dF, UflsStage uflsStage,
        LoadKind kind) {
    public LoadModel {
        if (!Double.isFinite(p0W) || !Double.isFinite(q0Var)) throw new IllegalArgumentException("power");
        if (!(aZ >= 0 && aI >= 0 && aP >= 0) || Math.abs(aZ + aI + aP - 1) > 1e-9)
            throw new IllegalArgumentException("ZIP fractions must sum to 1");
        if (!(Double.isNaN(dF) || dF >= 0)) throw new IllegalArgumentException("dF");
        Objects.requireNonNull(uflsStage, "uflsStage");
        Objects.requireNonNull(kind, "kind");
    }

    /** Постоянная мощность с регулирующим эффектом по умолчанию. */
    public static LoadModel constantPower(double pW, double qVar, LoadKind kind) {
        return new LoadModel(pW, qVar, 0, 0, 1, Double.NaN, UflsStage.NONE, kind);
    }

    /** Постоянное сопротивление (нагреватель): P ∝ v², частота не влияет. */
    public static LoadModel constantImpedance(double pW, double qVar, LoadKind kind) {
        return new LoadModel(pW, qVar, 1, 0, 0, 0, UflsStage.NONE, kind);
    }

    /**
     * Нагрузка вида {@code kind} с ZIP, cos φ и D_f по таблице §7.6.1. Двигатели — отдельная модель
     * ({@link InductionMotorSpec}), дуга ЭДП (§7.6.5) пока не моделируется.
     */
    public static LoadModel typical(LoadKind kind, double pW) {
        return switch (kind) {
            case HEATER -> new LoadModel(pW, 0, 1, 0, 0, 0, UflsStage.NONE, kind);
            case LIGHTING -> new LoadModel(pW, reactive(pW, 0.95), 0.5, 0.5, 0, 0, UflsStage.NONE, kind);
            case RECTIFIER -> new LoadModel(pW, reactive(pW, 0.92), 0, 0, 1, 0, UflsStage.NONE, kind);
            case PROCESS -> new LoadModel(pW, reactive(pW, 0.9), 0.2, 0, 0.8, 0.5, UflsStage.NONE, kind);
            case NPC_CONSUMER -> new LoadModel(pW, reactive(pW, 0.9), 0.3, 0.2, 0.5, 1.5, UflsStage.NONE, kind);
            case FE_IMPORT -> new LoadModel(pW, 0, 0, 0, 1, 0, UflsStage.NONE, kind);
            case ARC_FURNACE -> throw new UnsupportedOperationException("arc furnace load (§7.6.5) is not modelled yet");
        };
    }

    private static double reactive(double p, double cosPhi) {
        return p * Math.tan(Math.acos(cosPhi));
    }

    public LoadModel withUflsStage(UflsStage stage) {
        return new LoadModel(p0W, q0Var, aZ, aI, aP, dF, stage, kind);
    }

    public LoadModel withDamping(double dF) {
        return new LoadModel(p0W, q0Var, aZ, aI, aP, dF, uflsStage, kind);
    }
}
