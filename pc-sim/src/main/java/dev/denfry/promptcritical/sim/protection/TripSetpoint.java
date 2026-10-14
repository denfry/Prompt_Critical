package dev.denfry.promptcritical.sim.protection;

import java.util.Objects;

/**
 * Уставка функции защиты (01 §1.11.3, §1.13.3).
 *
 * @param value                  уставка в единицах канала; NaN для дискретных причин ({@link TripReason#isDiscrete()})
 * @param delaySeconds           t_d — задержка функции после голосования
 * @param hysteresis             зона возврата: сработавший канал сбрасывается, только отойдя от уставки на эту величину
 * @param permissiveMinPowerFrac функция взведена только при измеренной мощности не ниже этой доли (разрешение)
 */
public record TripSetpoint(TripReason reason, double value, Comparison cmp, Voting voting,
        double delaySeconds, double hysteresis, double permissiveMinPowerFrac, boolean enabled) {
    public TripSetpoint {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(cmp, "cmp");
        Objects.requireNonNull(voting, "voting");
        if (!reason.isDiscrete() && !Double.isFinite(value)) throw new IllegalArgumentException("value");
        if (!(delaySeconds >= 0)) throw new IllegalArgumentException("delaySeconds");
        if (!(hysteresis >= 0)) throw new IllegalArgumentException("hysteresis");
        if (!(permissiveMinPowerFrac >= 0)) throw new IllegalArgumentException("permissiveMinPowerFrac");
    }

    /** Дискретная причина (кнопка, запрос Lua): срабатывает немедленно. */
    public static TripSetpoint discrete(TripReason reason, Voting voting) {
        return new TripSetpoint(reason, Double.NaN, Comparison.ABOVE, voting, 0, 0, 0, true);
    }

    /** Ужесточение — сдвиг уставки в безопасную сторону (или возврат к исходной). */
    boolean isTighteningOf(double newValue) {
        if (!Double.isFinite(newValue)) return false;
        return cmp == Comparison.ABOVE ? newValue <= value : newValue >= value;
    }
}
