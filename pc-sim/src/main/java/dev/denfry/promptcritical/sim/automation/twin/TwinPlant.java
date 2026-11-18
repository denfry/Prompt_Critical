package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.core.SimClock;
import java.util.List;

/**
 * Установка цифрового двойника и тренажёра (03d §3d.9.1, 11 §11.3.2): изолированная копия домена. Ничего не пишет в
 * мир — ни {@code DamageRequest}, ни событий реальных устройств; разрушение установки — только {@link #failure()}.
 * Сигналы — истинные значения процесса (отказы датчиков накладывает {@link TwinSession}), исполнительные механизмы —
 * команды, которые установка ограничивает своим диапазоном.
 */
public interface TwinPlant extends AutoCloseable {
    /** Шаг двойника — FAST-шаг домена. */
    double DT = SimClock.FAST_DT;

    /** Шаблон установки, {@link TwinPlants}. */
    String template();

    /** Имена сигналов в порядке показа на пульте. */
    List<String> signals();

    /** Истинное значение сигнала; NaN — нет такого сигнала. */
    double read(String signal);

    /** Имена исполнительных механизмов в порядке показа на пульте. */
    List<String> actuators();

    /** Диапазон механизма {min, max}. */
    double[] range(String actuator);

    /** Механизм двухпозиционный (насос, выключатель, отсечной клапан): команда 0 или 1. */
    boolean discrete(String actuator);

    /** Фактическое положение механизма. */
    double actuator(String actuator);

    /** Команда механизму на ближайший шаг; вне диапазона — обрезается. */
    void actuate(String actuator, double value);

    /** Один шаг {@link #DT}. */
    void step();

    /** Авария установки (разрушение, перелив), {@code null} — цела. Однажды наступив, не сбрасывается. */
    String failure();

    @Override
    default void close() {
    }

    static double clamp(double v, double[] range) {
        return Double.isNaN(v) ? range[0] : Math.max(range[0], Math.min(range[1], v));
    }
}
