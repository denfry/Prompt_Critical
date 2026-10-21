package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.SimId;

/**
 * Монтажный дефект (13 §13.6.6). Поле {@code limit} в GDD нет: шву нужны два числа.
 *
 * @param magnitude величина дефекта: k_w шва, δ уставки ПК, кратность подрыва прикипевшего ПК, смещение нуля
 *                  манометра (доля шкалы), доля тормозного момента; у бинарных дефектов — 1
 * @param limit     k_b шва — давление разрыва в долях p_раб; у прочих NaN
 * @param found     дефект найден проверкой и виден на щите
 */
public record CommissioningDefect(CommissioningDefectKind kind, SimId component, double magnitude, double limit,
        boolean found) {
    public static CommissioningDefect of(CommissioningDefectKind kind, SimId component, double magnitude) {
        return new CommissioningDefect(kind, component, magnitude, Double.NaN, false);
    }

    public CommissioningDefect asFound() {
        return found ? this : new CommissioningDefect(kind, component, magnitude, limit, true);
    }
}
