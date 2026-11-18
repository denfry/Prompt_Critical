package dev.denfry.promptcritical.sim.alarm;

/** Отказ в действии с сигнализацией ({@code E_ACCESS}, 03b §3b.2.14): шельфинг приоритета 1 (T-3c-07) и т. п. */
public final class AlarmAccessException extends RuntimeException {
    public AlarmAccessException(String message) {
        super(message);
    }
}
