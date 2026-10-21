package dev.denfry.promptcritical.sim.grid;

import java.util.Objects;

/**
 * Модуль РЗА в шкафу или в блоке потребителя (07 §7.5.2, предмет {@code relay_*} с компонентом
 * {@code relay_settings}): вид, поколение и две уставки. Смысл уставок зависит от вида — см. {@link Kind}.
 *
 * @param a первая уставка (ток срабатывания, частота, напряжение, температура, очередь АЧР)
 * @param b вторая уставка (множитель времени или выдержка, с)
 */
public record RelaySetting(Kind kind, RelayGeneration generation, double a, double b) {
    /** Виды модулей эпохи I1 и смысл уставок. */
    public enum Kind {
        /** 51 + 50: a — ток срабатывания I_s, А; b — множитель времени кривой IEC SI; отсечка 8·I_s. */
        OVERCURRENT(400, 0.1),
        /** 25: окно {@code grid.sync_max_*}; уставок нет. */
        SYNC_CHECK(0, 0),
        /** 32R: a — уставка, доля S_ном; b — выдержка, с. Только на генераторном присоединении. */
        REVERSE_POWER(0.02, 3),
        /** 81U: a — уставка, Гц; b — выдержка, с; отключает присоединение. */
        UNDERFREQUENCY(47.5, 2),
        /** 27: a — уставка, о.е.; b — выдержка, с. */
        UNDERVOLTAGE(0.7, 3),
        /** 49: a — температура отключения, °C. Только у двигателя. */
        THERMAL(140, 0),
        /** АЧР с ЧАПВ: a — номер очереди {@link UflsStage} (1…7). Только у нагрузки и двигателя. */
        UFLS(1, 0),
        /** 79: a — бестоковая пауза, с; b — число циклов. */
        AUTORECLOSE(1, 1),
        /** 50BF: a — выдержка, с. */
        BREAKER_FAILURE(0.25, 0);

        public final double defaultA, defaultB;

        Kind(double defaultA, double defaultB) {
            this.defaultA = defaultA;
            this.defaultB = defaultB;
        }

        /** Действует на нагрузку или двигатель (а не на выключатель). */
        public boolean consumerOnly() {
            return this == THERMAL || this == UFLS;
        }
    }

    public RelaySetting {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(generation, "generation");
        if (!Double.isFinite(a) || !Double.isFinite(b)) throw new IllegalArgumentException("settings");
    }

    /** Модуль A1 с заводскими уставками. */
    public static RelaySetting standard(Kind kind) {
        return new RelaySetting(kind, RelayGeneration.A1_ELECTROMECHANICAL, kind.defaultA, kind.defaultB);
    }

    /** Очередь АЧР уставки {@code a}. */
    public UflsStage uflsStage() {
        UflsStage[] all = UflsStage.values();
        return all[Math.max(1, Math.min(all.length - 1, (int) Math.round(a)))];
    }

    /** Реле домена; {@code null} — уставки вне допустимого (модуль не работает). */
    ProtectionRelay toRelay(GridConfig cfg) {
        try {
            return switch (kind) {
                case OVERCURRENT -> new ProtectionRelay.Overcurrent(ProtectionFunction.F51, generation, a, b,
                        IdmtCurve.IEC_SI, 8 * a);
                case SYNC_CHECK -> ProtectionRelay.SyncCheck.of(generation, cfg);
                case REVERSE_POWER -> new ProtectionRelay.ReversePower(ProtectionFunction.F32R, generation, a, b);
                case UNDERFREQUENCY -> new ProtectionRelay.Frequency(ProtectionFunction.F81U, generation, a, b);
                case UNDERVOLTAGE -> new ProtectionRelay.Voltage(ProtectionFunction.F27, generation, a, b);
                case THERMAL -> new ProtectionRelay.Thermal(ProtectionFunction.F49, generation, a);
                case UFLS -> ProtectionRelay.Ufls.standard(uflsStage(), generation);
                case AUTORECLOSE -> new ProtectionRelay.AutoReclose(ProtectionFunction.F79, generation, a,
                        Math.max(1, (int) Math.round(b)));
                case BREAKER_FAILURE -> new ProtectionRelay.BreakerFailure(ProtectionFunction.F50BF, generation, a);
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
