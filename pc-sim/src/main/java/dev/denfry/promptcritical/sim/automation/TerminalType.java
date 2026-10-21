package dev.denfry.promptcritical.sim.automation;

/**
 * Тип клеммы блока (03d §3d.2.1): провод соединяет только клеммы одного типа. {@code PULSE} и {@code FIELDBUS}
 * появятся с A3.
 */
public enum TerminalType {
    /** Цепь оперативного тока 24 В DC: контакты, катушки, лампы, кнопки. */
    DISCRETE,
    /** Токовая петля 4–20 мА. */
    ANALOG_CURRENT,
    /** Вычислительное напряжение ±10 В (блоки A2). */
    ANALOG_VOLTAGE
}
