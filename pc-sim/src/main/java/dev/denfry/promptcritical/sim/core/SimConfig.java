package dev.denfry.promptcritical.sim.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Доменная проекция конфига (05 §5.3.6): ключи {@code time.*}, {@code storage.*} и {@code perf.*} плюс секции
 * подсистем ({@code MiningConfig} и т. п.), которые ядро хранит, не зная их типов. Меняется на лету только
 * командой {@link SimCommand.ReloadConfig} на границе тика; ключи структуры ({@code perf.snapshot_buffers},
 * ёмкости очередей) действуют только при создании мира.
 *
 * @param kSlow                 {@code time.k_slow}, физ. с на 1 с реального времени в SLOW [BALANCE]
 * @param kStorage              {@code storage.k_storage}, то же для STORAGE [BALANCE]
 * @param commandQueueCapacity  {@code perf.command_queue_capacity}
 * @param maxCommandsPerTick    {@code perf.max_commands_per_tick}
 * @param eventBufferCapacity   {@code perf.event_buffer_capacity}
 * @param snapshotBuffers       {@code perf.snapshot_buffers}
 * @param slowJoinTimeoutMs     {@code perf.slow_join_timeout_ms}: ожидание задания SLOW перед «кражей»
 * @param budgetMs              {@code perf.budget_ms}: бюджет FAST-тика для уровней деградации
 */
public record SimConfig(double kSlow, double kStorage, int commandQueueCapacity, int maxCommandsPerTick,
        int eventBufferCapacity, int snapshotBuffers, int slowJoinTimeoutMs, double budgetMs,
        Map<Class<?>, Object> sections) {

    public SimConfig {
        if (!(kSlow > 0 && kStorage > 0)) throw new IllegalArgumentException("k_slow/k_storage must be > 0");
        if (commandQueueCapacity < 2 || Integer.bitCount(commandQueueCapacity) != 1)
            throw new IllegalArgumentException("command_queue_capacity must be a power of two: " + commandQueueCapacity);
        if (maxCommandsPerTick < 1) throw new IllegalArgumentException("max_commands_per_tick < 1");
        if (eventBufferCapacity < 16) throw new IllegalArgumentException("event_buffer_capacity < 16");
        if (snapshotBuffers < 3) throw new IllegalArgumentException("snapshot_buffers < 3 (05 §5.2.7)");
        if (slowJoinTimeoutMs < 0) throw new IllegalArgumentException("slow_join_timeout_ms < 0");
        if (!(budgetMs > 0)) throw new IllegalArgumentException("budget_ms must be > 0");
        sections = Map.copyOf(sections);
    }

    public static SimConfig defaults() {
        return new SimConfig(72.0, 8766.0, 4096, 512, 4096, 4, 5, 2.0, Map.of());
    }

    /** Секция подсистемы или {@code fallback}, если биндинг её не передал. */
    public <T> T section(Class<T> type, T fallback) {
        Object v = sections.get(type);
        return v == null ? fallback : type.cast(v);
    }

    public SimConfig withSection(Object section) {
        Objects.requireNonNull(section, "section");
        Map<Class<?>, Object> m = new HashMap<>(sections);
        m.put(section.getClass(), section);
        return new SimConfig(kSlow, kStorage, commandQueueCapacity, maxCommandsPerTick, eventBufferCapacity,
                snapshotBuffers, slowJoinTimeoutMs, budgetMs, m);
    }

    public SimConfig withTime(double kSlow, double kStorage) {
        return new SimConfig(kSlow, kStorage, commandQueueCapacity, maxCommandsPerTick, eventBufferCapacity,
                snapshotBuffers, slowJoinTimeoutMs, budgetMs, sections);
    }
}
