package dev.denfry.promptcritical.sim.alarm;

/**
 * Вид сигнализации (03c §3c.6.2). Аналоговые виды сравнивают измеренное значение тега с пределом {@code limit}:
 * {@link #HIGH}/{@link #HIGH_HIGH} — выше, {@link #LOW}/{@link #LOW_LOW} — ниже, {@link #DEVIATION} — модуль
 * значения (тег уже отклонение от задания) выше предела, {@link #RATE_OF_CHANGE} — модуль скорости изменения, ед./с,
 * выше предела. {@link #DISCRETE} — значение ≥ 0,5; {@link #BAD_QUALITY} — значения нет (NaN, качество {@code BAD});
 * {@link #SOFT} поднимает и снимает программа ({@code raise}/{@code clear}, 03b §3b.2.14).
 */
public enum AlarmKind {
    HIGH, LOW, HIGH_HIGH, LOW_LOW, DISCRETE, DEVIATION, RATE_OF_CHANGE, BAD_QUALITY, SOFT;

    /** Сравнение с пределом: нужен конечный {@code limit}. */
    public boolean analog() {
        return this == HIGH || this == LOW || this == HIGH_HIGH || this == LOW_LOW || this == DEVIATION || this == RATE_OF_CHANGE;
    }
}
