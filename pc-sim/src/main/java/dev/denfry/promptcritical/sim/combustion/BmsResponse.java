package dev.denfry.promptcritical.sim.combustion;

/**
 * Ответ BMS на команду оператора. Всё, кроме {@link #ACCEPTED}, для Lua — {@code E_INTERLOCK} с причиной
 * (07 §7.16, {@code requestPurge}/{@code requestIgniter}/{@code requestMainFuel}).
 */
public enum BmsResponse {
    ACCEPTED,
    /** Дымосос или дутьевой вентилятор не работают. */
    INTERLOCK_FANS,
    /** Клапаны топлива не закрыты (концевики). */
    INTERLOCK_FUEL_VALVES,
    /** Нет завершённой продувки. */
    INTERLOCK_PURGE,
    /** Команда не допустима в текущем состоянии (нет запальника на горелке, идёт останов). */
    INTERLOCK_STATE,
    /** Нет такой горелки. */
    INTERLOCK_BURNER;

    public boolean accepted() {
        return this == ACCEPTED;
    }
}
