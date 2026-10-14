package dev.denfry.promptcritical.sim.grid;

/**
 * Первичный двигатель машины (07 §7.19.1). Состояние (положение клапана) хранит {@link PowerGrid}
 * в своих массивах; записи — только параметры.
 *
 * <p>Каркас M1: паровая турбина 03a ({@code TurbineModel}), ГТУ §7.10 и дизель §7.11 ещё не написаны,
 * поэтому {@link Governed} — общая модель «клапан + регулятор со статизмом» 03a §3a.7.4 без динамики
 * пара. Мощность первичного двигателя пропорциональна открытию: P_m = y·P_ном.
 */
public sealed interface PrimeMover permits PrimeMover.Governed, PrimeMover.CreateShaft {
    double ratedPowerW();

    /**
     * Регулятор 03a §3a.7.4: y_ref = y₀ − Δω/R (Δω — за мёртвой зоной), сервомотор T_s с
     * ограничением скорости открытия и закрытия. {@code droop} = ∞ — регулятор выведен
     * (момент постоянен).
     *
     * @param minLoadFrac технический минимум открытия
     */
    record Governed(double ratedPowerW, double droop, double servoTimeS, double openRatePerS, double closeRatePerS,
            double fastCloseRatePerS, double minLoadFrac) implements PrimeMover {
        /** Ускоренное закрытие при ω > 1,05 ω₀ (03a §3a.7.4). */
        public static final double FAST_CLOSE_SPEED = 1.05;

        public Governed {
            if (!(ratedPowerW > 0)) throw new IllegalArgumentException("ratedPowerW");
            if (!(droop > 0)) throw new IllegalArgumentException("droop");
            if (!(servoTimeS > 0)) throw new IllegalArgumentException("servoTimeS");
            if (!(openRatePerS > 0 && closeRatePerS > 0 && fastCloseRatePerS >= closeRatePerS))
                throw new IllegalArgumentException("rates");
            if (!(minLoadFrac >= 0 && minLoadFrac < 1)) throw new IllegalArgumentException("minLoadFrac");
        }

        /** Паровая турбина 03a: R = 0,04, T_s = 0,1 с, открытие 0,1 с⁻¹, закрытие 0,5 (5) с⁻¹. */
        public static Governed steamTurbine(double ratedPowerW) {
            return new Governed(ratedPowerW, 0.04, 0.1, 0.1, 0.5, 5, 0);
        }

        /** Без регулятора: момент держится на заданной мощности. */
        public static Governed constantPower(double ratedPowerW) {
            return new Governed(ratedPowerW, Double.POSITIVE_INFINITY, 0.1, 1, 1, 1, 0);
        }
    }

    /**
     * {@code shaft_generator}: мощность приходит из сети Create через {@code CreateKineticPort}
     * (03a §3a.1) — {@link GridInputs#createShaftPowerW}.
     */
    record CreateShaft(double gearRatio, double ratedPowerW) implements PrimeMover {
        public CreateShaft {
            if (!(gearRatio > 0)) throw new IllegalArgumentException("gearRatio");
            if (!(ratedPowerW > 0)) throw new IllegalArgumentException("ratedPowerW");
        }
    }
}
