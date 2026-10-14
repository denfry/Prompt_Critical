package dev.denfry.promptcritical.sim.core;

/**
 * Подсистема мира с фазами (05 §5.2.1). Порядок регистрации в {@link SimulationWorld} — это порядок FAST-шага
 * (05 §5.2.3) и фаза SLOW/STORAGE. Задание SLOW, подготовленное на тике t, сливается на тике t + 20 — даже если
 * готово раньше (05 §5.4.2).
 */
public interface Subsystem {
    /** Устойчивый ключ подсистемы в сохранении. */
    String id();

    default void stepFast(FastContext ctx) {
    }

    /** Копирует входы и возвращает задание для воркера. Серверный поток. */
    default SlowJob prepareSlow(SlowContext ctx) {
        return SlowJob.NONE;
    }

    default void mergeSlow(SlowResult r) {
    }

    default SlowJob prepareStorage(SlowContext ctx) {
        return SlowJob.NONE;
    }

    default void mergeStorage(SlowResult r) {
    }

    /**
     * Восстановить задание SLOW, которое выполнялось при сохранении (его входы записал {@link SlowJob#save}).
     * {@link SlowJob#NONE} — шаг SLOW будет потерян; подсистемы с SLOW обязаны это реализовать.
     */
    default SlowJob restoreSlow(StateReader in) {
        return SlowJob.NONE;
    }

    default SlowJob restoreStorage(StateReader in) {
        return SlowJob.NONE;
    }

    /** Исполнить команду; {@link CommandStatus#NOT_HANDLED} — команда не этой подсистеме. */
    default CommandStatus apply(SimCommand command, CommandSource source) {
        return CommandStatus.NOT_HANDLED;
    }

    /** Устройство удалено из топологии (начало тика после разрушения блока). */
    default void onDeviceRemoved(SimId device) {
    }

    /** Новые значения конфига (граница тика). */
    default void onConfigReload(SimConfig config) {
    }

    void save(StateWriter out);

    void load(StateReader in);

    /**
     * Перевести объекты в безопасное состояние (реактор — {@code TRIPPED}, стержни внизу): сохранение подсистемы
     * отсутствует или не читается (05 §5.2.8).
     */
    default void resetToSafeState() {
    }
}
