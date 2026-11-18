package dev.denfry.promptcritical.sim.automation.twin;

/**
 * Отказ, вводимый в двойник инструктором или сценарием (03d §3d.9.1, 11 §11.3.3; каталог — общий с 03b §3b.4.3.5).
 * Отказы действуют только на копию: на показания, механизмы и ПЛК двойника.
 */
public sealed interface Fault {
    /** Короткое имя для пульта инструктора и журнала. */
    String label();

    /** Отказ датчика. */
    enum SensorMode {
        /** Показание застыло на значении в момент отказа. */
        FROZEN,
        /** Уход нуля: {@code param} единиц сигнала в секунду. */
        DRIFT,
        /** Обрыв линии 4–20 мА: показания нет (NaN, качество BAD). */
        OPEN_LOOP,
        /** Шум: нормальный с СКО {@code param}. */
        NOISY
    }

    record SensorFault(String signal, SensorMode mode, double param) implements Fault {
        @Override
        public String label() {
            return mode.name() + " " + signal;
        }
    }

    /** Заклинивание механизма. */
    enum StuckMode {
        /** На положении {@code at}; NaN — там, где был в момент отказа. */
        STUCK_AT,
        STUCK_OPEN,
        STUCK_CLOSED,
        /** Отказ насоса или компрессора: стоит и не запускается. */
        TRIP
    }

    record StuckActuator(String actuator, StuckMode mode, double at) implements Fault {
        public static StuckActuator trip(String actuator) {
            return new StuckActuator(actuator, StuckMode.TRIP, 0);
        }

        @Override
        public String label() {
            return mode.name() + " " + actuator;
        }
    }

    /**
     * Неверная индикация ({@code COMMAND}): сигнал положения показывает команду механизму, а не его фактическое
     * положение (урок {@code stuck_porv}).
     */
    record IndicationCommand(String signal, String actuator) implements Fault {
        @Override
        public String label() {
            return "COMMAND " + signal;
        }
    }

    /** Отказ ПЛК: сторожевой таймер, выходы сброшены в безопасное состояние. */
    record PlcWatchdog() implements Fault {
        @Override
        public String label() {
            return "WATCHDOG";
        }
    }

    /** Форсированный вход ПЛК, оставленный после наладки (урок {@code forced_bit}). */
    record ForcedInput(int channel, boolean analog, double value) implements Fault {
        @Override
        public String label() {
            return (analog ? "FORCE %IW" + channel : "FORCE %IX0." + channel) + " = " + value;
        }
    }
}
