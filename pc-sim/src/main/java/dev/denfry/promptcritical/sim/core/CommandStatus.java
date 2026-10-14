package dev.denfry.promptcritical.sim.core;

/** Итог применения команды; отдаётся отправителю в {@link SimEvent.CommandResult}. */
public enum CommandStatus {
    OK,
    /** Подсистема приняла команду к сведению, но отказала (режим устройства, блокировка). */
    REJECTED,
    /** Адресата нет: объект удалён или не существовал. */
    NO_TARGET,
    /** Ни одна подсистема не исполняет команду этого вида. */
    NOT_HANDLED
}
