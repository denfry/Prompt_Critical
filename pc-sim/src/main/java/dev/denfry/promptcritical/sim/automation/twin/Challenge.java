package dev.denfry.promptcritical.sim.automation.twin;

import java.util.List;

/**
 * Задача наладчика (03d §3d.11.1): испытание программы игрока на эталонной установке двойника с жёсткими условиями и
 * метриками для таблицы лидеров. Сценарий — FAT-сценарий ({@link Scenario#fat()}): ПЛК двойника исполняет проект
 * игрока; {@code constraints} — его {@link Scenario#failure()} (нет помпажа, нет срабатываний защит — нарушение =
 * провал), критерии успеха — его {@link Scenario#success()}. Решение проверяется только на серверном двойнике с
 * эталонным зерном ({@link #SEED}), поэтому результат воспроизводим и клиентом не подделывается.
 *
 * @param industry  отрасль GDD (01, 07–10, 13)
 * @param level     минимальный уровень автоматизации (A1…A5)
 * @param metrics   метрики таблицы лидеров (§3d.11.3); у всех лучше — меньше
 * @param threshold критерий достижения 11 по первой метрике (например, себестоимость ≤ 1,05 оптимума ЛП) или NaN
 */
public record Challenge(String id, String industry, String level, Scenario scenario, List<Metric> metrics, double threshold) {
    /** Эталонное зерно серверного прогона. */
    public static final long SEED = 0x5EED_0D1AL;

    public Challenge {
        if (!scenario.fat()) throw new IllegalArgumentException("challenge scenario must run the player's program");
        if (metrics.isEmpty()) throw new IllegalArgumentException("challenge needs a metric");
        metrics = List.copyOf(metrics);
    }

    /** Откуда берётся значение метрики. */
    public enum Source {
        /** Значение сигнала установки в конце прогона. */
        SIGNAL,
        /** Момент, после которого критерии успеха выполнялись без перерыва, с. */
        SETTLED_S,
        /** Показатели контура {@link Scenario#loop()} (§3d.11.3). */
        IAE, ISE, ITAE, OVERSHOOT, SETTLING_S, VALVE_TRAVEL,
        /** Размер решения — инструкций IR. */
        SIZE
    }

    /**
     * Метрика: {@code par} — значение эталонного решения из справочника (стандартные настройки блоков); от него строится
     * эталонное распределение сервера ({@link ChallengeBoard#reference}).
     */
    public record Metric(String id, Source source, String signal, double par) {
        public Metric {
            if (!(par > 0)) throw new IllegalArgumentException("par must be positive: " + id);
        }
    }

    public Metric metric(String metricId) {
        for (Metric m : metrics) if (m.id().equals(metricId)) return m;
        return null;
    }
}
