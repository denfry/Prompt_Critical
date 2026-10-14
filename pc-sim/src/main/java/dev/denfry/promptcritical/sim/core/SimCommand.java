package dev.denfry.promptcritical.sim.core;

/**
 * Внешнее воздействие на домен (05 §5.2.6, решение A6). Команды применяются только в начале FAST-тика в порядке
 * {@link CommandQueue#drainSorted}. Записи добавляются вместе с подсистемами, которые их исполняют
 * (таблицы методов 03b §3b.2).
 */
public sealed interface SimCommand permits SimCommand.SetValveDemand, SimCommand.ValveOpenClose,
        SimCommand.SetDeviceMode, SimCommand.ReloadConfig {

    /** Объект-адресат; {@link SimId#NONE} — команда всему миру. */
    SimId target();

    /** Задание положения регулирующего органа, доля хода 0…1. */
    record SetValveDemand(SimId target, double position) implements SimCommand {
    }

    /** Открыть/закрыть запорный орган. */
    record ValveOpenClose(SimId target, boolean open) implements SimCommand {
    }

    /** Ключ режима устройства (03b §3b.7). */
    record SetDeviceMode(SimId target, DeviceMode mode) implements SimCommand {
    }

    /** Новые значения конфига (05 §5.3.6); применяется на границе тика. */
    record ReloadConfig(SimId target, SimConfig config) implements SimCommand {
        public ReloadConfig(SimConfig config) {
            this(SimId.NONE, config);
        }
    }
}
