package dev.denfry.promptcritical.sim.core;

/**
 * Внешнее воздействие на домен (05 §5.2.6, решение A6). Команды применяются только в начале FAST-тика в порядке
 * {@link CommandQueue#drainSorted}. Записи добавляются вместе с подсистемами, которые их исполняют
 * (таблицы методов 03b §3b.2).
 */
public sealed interface SimCommand permits SimCommand.SetValveDemand, SimCommand.ValveOpenClose,
        SimCommand.SetDeviceMode, SimCommand.ReloadConfig, SimCommand.ChargeFuel, SimCommand.Operate {

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

    /**
     * Загрузить твёрдое топливо в топку (руками или забрасывателем), кг. {@code fuel} — ключ топлива подсистемы
     * (например, {@code coal_g}, {@code charcoal}); домен не знает предметов MC.
     */
    record ChargeFuel(SimId target, String fuel, double kg) implements SimCommand {
    }

    /**
     * Местное действие оператора у машины без программирования (A0): розжиг, установка детали, ручной вентиль.
     * Набор {@code action} определяет подсистема-адресат.
     */
    record Operate(SimId target, String action) implements SimCommand {
    }

    /** Новые значения конфига (05 §5.3.6); применяется на границе тика. */
    record ReloadConfig(SimId target, SimConfig config) implements SimCommand {
        public ReloadConfig(SimConfig config) {
            this(SimId.NONE, config);
        }
    }
}
