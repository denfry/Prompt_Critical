package dev.denfry.promptcritical.sim.ops;

/** Что мир сообщает о выполненной проверке (13 §13.6.6): условия и показания. */
public sealed interface CheckInput {
    CommissioningCheck check();

    /**
     * Гидроиспытание.
     *
     * @param pressurePa поднятое давление, Па
     * @param heldPhysS  выдержка под этим давлением, с физики
     */
    record PressureTest(boolean fireOut, double metalTempK, double pressurePa, double heldPhysS)
            implements CheckInput {
        @Override public CommissioningCheck check() { return CommissioningCheck.PRESSURE_TEST; }
    }

    /** Подъём паром до подрыва ПК; {@code controlGauge} — {@code test_pressure_gauge} на контрольном фланце. */
    record ReliefValveSet(boolean controlGauge) implements CheckInput {
        @Override public CommissioningCheck check() { return CommissioningCheck.RELIEF_VALVE_SET; }
    }

    record InstrumentCheck() implements CheckInput {
        @Override public CommissioningCheck check() { return CommissioningCheck.INSTRUMENT_CHECK; }
    }

    /** «Толчок»: {@code reversed} — фактическое направление вала Create сейчас. */
    record RotationCheck(boolean reversed) implements CheckInput {
        @Override public CommissioningCheck check() { return CommissioningCheck.ROTATION_CHECK; }
    }

    record Priming(boolean pumpStopped) implements CheckInput {
        @Override public CommissioningCheck check() { return CommissioningCheck.PRIMING; }
    }

    /** {@code loadFraction} — груз клети в долях номинального. */
    record BrakeTest(double loadFraction) implements CheckInput {
        @Override public CommissioningCheck check() { return CommissioningCheck.BRAKE_TEST; }
    }

    /**
     * Пробный пуск.
     *
     * @param runS               наработка, с реального времени
     * @param loadFraction       минимальная нагрузка за пуск в долях номинальной
     * @param protectionTripped  срабатывала защита
     * @param loadRejected       был сброс нагрузки
     * @param baseline           базовые показатели в паспорт
     */
    record TrialRun(double runS, double loadFraction, boolean protectionTripped, boolean loadRejected,
            Baseline baseline) implements CheckInput {
        @Override public CommissioningCheck check() { return CommissioningCheck.TRIAL_RUN; }
    }
}
