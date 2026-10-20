package dev.denfry.promptcritical.sim.automation;

/**
 * Вид реле процесса (03d §3d.3.6). Погрешность и зона возврата заданы либо долей шкалы, либо
 * абсолютно (уровень — м), либо долей уставки (расход); возврат термостата — в K.
 */
public enum ProcessSwitchKind {
    /** Сильфонное (ДЕМ/РД): 1,5 % шкалы, τ 0,3 с, возврат 5 % шкалы. */
    PRESSURE(Basis.SPAN, 0.015, 0.3, 0.05),
    /** Поплавковое: ±10 мм, τ 1 с, возврат 20 мм. */
    LEVEL(Basis.ABSOLUTE, 0.010, 1.0, 0.020),
    /** Манометрический термостат (ТАМ/ТКП): 2 % шкалы, τ 15 с, возврат 3 K. */
    TEMPERATURE(Basis.SPAN, 0.02, 15, 3),
    /** Лопаточное реле потока: 10 % уставки, τ 2 с, возврат 15 % уставки. */
    FLOW(Basis.SETPOINT, 0.10, 2.0, 0.15);

    /** К чему отнесены погрешность и зона возврата. */
    public enum Basis { SPAN, ABSOLUTE, SETPOINT }

    public final Basis basis;
    public final double accuracy;
    public final double tauS;
    public final double defaultReturn;

    ProcessSwitchKind(Basis basis, double accuracy, double tauS, double defaultReturn) {
        this.basis = basis;
        this.accuracy = accuracy;
        this.tauS = tauS;
        this.defaultReturn = defaultReturn;
    }

    /** Погрешность в единицах процесса. */
    double accuracyAbs(double span, double setpoint) {
        return scaled(accuracy, span, setpoint);
    }

    /** Зона возврата по умолчанию в единицах процесса. */
    double defaultReturnAbs(double span, double setpoint) {
        return this == TEMPERATURE ? defaultReturn : scaled(defaultReturn, span, setpoint);
    }

    private double scaled(double v, double span, double setpoint) {
        return switch (basis) {
            case SPAN -> v * span;
            case ABSOLUTE -> v;
            case SETPOINT -> v * Math.abs(setpoint);
        };
    }
}
