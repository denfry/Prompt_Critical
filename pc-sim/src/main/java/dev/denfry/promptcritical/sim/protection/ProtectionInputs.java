package dev.denfry.promptcritical.sim.protection;

/**
 * Входы защиты — ИЗМЕРЕНИЯ (с погрешностью и отказами; формирует слой датчиков 03b), снятые в конце
 * прошлого тика (01 §1.13.4).
 *
 * <p>Каналы функции нумеруются по группам: канал {@code ch} принадлежит группе {@code ch / n}, где n —
 * число каналов в логике голосования. Если каналов меньше, чем групп × n, недостающие — {@code FAULT};
 * если их нет совсем, функция не установлена (нет датчика — нет защиты, §1.11.1).
 */
public interface ProtectionInputs {
    /** Сколько каналов установлено для причины. */
    int channelCount(TripReason r);

    /** Показание канала; NaN — отказ (обрыв, нет питания, выход за шкалу). */
    double channelValue(TripReason r, int ch);

    double measuredPowerFrac();

    /** Период по камерам, с; бесконечность или отрицательное значение — мощность не растёт. */
    double measuredPeriod();

    boolean luaTripRequested();

    boolean manualTrip();

    /** Пропал момент на приводах СУЗ (стержни падают сами; 1 из 1). */
    boolean driveTorqueLost();

    /** Групп голосования (петель, ПГ, насосов) у причины; не более {@link ProtectionSystem#MAX_GROUPS}. */
    default int groupCount(TripReason r) {
        return 1;
    }

    /** Вход вычислителя DNBR номер {@code calculator}; число вычислителей — {@code channelCount(LOW_DNBR)}. */
    default double dnbrSignal(DnbrSignal s, int calculator) {
        return Double.NaN;
    }

    /** Отключена (выключателем) хотя бы одна ГЦН — условие ПЗ-1. */
    default boolean pumpTripped() {
        return false;
    }

    /** Все группы стержней внизу ({@code AT_BOTTOM}) — условие разрешения сброса (03b §3b.2.3). */
    default boolean rodsAtBottom() {
        return true;
    }

    /** Оператор начал вывод стержней — переход {@code RESET_ARMED → NORMAL}. */
    default boolean rodWithdrawalStarted() {
        return false;
    }
}
