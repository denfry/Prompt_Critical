package dev.denfry.promptcritical.sim.core;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Согласованный срез одного тика для потоков CC, сетевого кодировщика и GUI (05 §5.2.7): без блокировок, с
 * оптимистической проверкой. Читать через {@link #readConsistent}: буфер переиспользуется через
 * {@code perf.snapshot_buffers} тиков.
 */
public interface SimSnapshot {
    long tick();

    double simTime();

    /** Поколение буфера на момент получения; нечётное — буфер пишется. */
    long stamp();

    /** Буфер не переиспользован с момента {@code stamp}. */
    boolean validate(long stamp);

    TagIndex tags();

    double measured(int slot);

    Quality quality(int slot);

    default boolean discrete(int slot) {
        return measured(slot) != 0.0;
    }

    /**
     * Прочитать срез целиком из одного тика: до трёх оптимистических попыток, затем копия под коротким замком
     * (05 §5.2.7).
     */
    static <T> T readConsistent(Supplier<SimSnapshot> latest, Supplier<SimSnapshot> lockedCopy,
            Function<SimSnapshot, T> reader) {
        for (int attempt = 0; attempt < 3; attempt++) {
            SimSnapshot s = latest.get();
            long st = s.stamp();
            if ((st & 1) != 0) continue;
            T result = reader.apply(s);
            if (s.validate(st)) return result;
        }
        return reader.apply(lockedCopy.get());
    }
}
