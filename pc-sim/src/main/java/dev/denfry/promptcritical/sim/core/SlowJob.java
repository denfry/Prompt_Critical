package dev.denfry.promptcritical.sim.core;

/**
 * Задание SLOW или STORAGE (05 §5.4.2). Работает только с копией входов, снятой в {@code prepare*}, и пишет только в
 * свой буфер результата. Разбито на порции (обычно по станциям): если воркер не успел к сроку, серверный поток
 * выполняет оставшиеся порции сам. Порции независимы, результат не зависит от того, кто их выполнил.
 */
public interface SlowJob {
    SlowJob NONE = new SlowJob() {
        @Override
        public int portions() {
            return 0;
        }

        @Override
        public void run(int portion) {
        }

        @Override
        public SlowResult result() {
            return SlowResult.NONE;
        }
    };

    default int portions() {
        return 1;
    }

    void run(int portion);

    /** Результат после выполнения всех порций. */
    SlowResult result();

    /**
     * Сохранить <b>входы</b> задания (копию, снятую в {@code prepare*}), чтобы после загрузки мира подсистема
     * восстановила его через {@link Subsystem#restoreSlow} и слияние произошло в тот же тик, что и без сохранения.
     * Вызывается, пока задание может выполняться на воркере: читать только неизменяемые входы.
     */
    default void save(StateWriter out) {
    }
}
