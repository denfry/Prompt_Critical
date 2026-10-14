package dev.denfry.promptcritical.sim.core;

/**
 * Режим устройства — положение ключа «местное/дистанционное» (03b §3b.7). Команды с пульта и Lua требуют
 * {@link #REMOTE}; в {@link #MAINTENANCE} устройство выведено в ремонт и команды не принимает.
 */
public enum DeviceMode {
    LOCAL,
    REMOTE,
    MAINTENANCE
}
