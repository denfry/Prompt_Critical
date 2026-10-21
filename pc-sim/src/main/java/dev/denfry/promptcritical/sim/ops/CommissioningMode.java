package dev.denfry.promptcritical.sim.ops;

/** Режим {@code ops.commissioning} (13 §13.6.6). */
public enum CommissioningMode {
    /** Дефекты не разыгрываются, состояния нет. */
    OFF,
    /** Непринятую установку можно пускать: лампа «НЕ ПРИНЯТА», запись {@code START_UNACCEPTED}. */
    ADVISORY,
    /** Пуск заблокирован, пока не пройдены все проверки, кроме пробного пуска. */
    ENFORCED
}
