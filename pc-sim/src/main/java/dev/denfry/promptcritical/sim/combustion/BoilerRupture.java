package dev.denfry.promptcritical.sim.combustion;

/** Причина разрушения котла — взрыв, {@code DamageRequest(BLAST)} (07 §7.9.4). */
public enum BoilerRupture {
    NONE,
    /** Давление выше прочности обечайки: заклинен или не установлен предохранительный клапан. */
    SHELL_OVERPRESSURE,
    /** Перегретый потолок жаровой трубы смят давлением: упуск воды без легкоплавкой пробки. */
    CROWN_COLLAPSE,
    /** Взрыв топки водотрубного котла: топливо, накопленное без факела, воспламенилось (07 §7.9.6). */
    FURNACE_EXPLOSION
}
