package dev.denfry.promptcritical.sim.grid;

/**
 * Измерения реле на тик (07 §7.1.4, §7.5.2). Один объект на сеть, перезаполняется для каждого реле —
 * без аллокаций в горячем цикле. Неприменимые к месту установки величины — NaN или 0.
 */
public final class RelayInputs {
    /** Наибольший фазный ток в месте установки, А (с обратной и нулевой последовательностями КЗ). */
    double currentA;
    /** Напряжение прямой последовательности шины, о.е. */
    double voltagePu;
    /** Частота шины (§7.1.4), Гц. */
    double frequencyHz;
    /** P и Q генератора на S_ном. */
    double powerPu, reactivePu;
    /** Кажущееся сопротивление V₁/I₁: присоединение — Ом, генератор — о.е. на S_ном; без тока — бесконечность. */
    double zRe, zIm;
    /** Дифференциальный и тормозной ток зоны, о.е. номинального тока защищаемого объекта. */
    double diffPu, restraintPu;
    /** Доля 2-й гармоники броска намагничивания; 0 — броска нет. */
    double inrushH2;
    /** Этот бросок отключил бы дифзащиту без гармонической блокировки (разыгран при включении, p = 0,3). */
    boolean inrushTrip;
    /** Провороты машины с начала работы. */
    int poleSlips;
    /** Наиболее нагретая точка (обмотка трансформатора, провод, обмотка двигателя), °C; NaN — не моделируется. */
    double hotSpotC;
    /** Дуга внутри бака (КЗ внутри трансформатора) — газовая защита. */
    boolean internalArc;
    /** Ускорение после АПВ: выдержки МТЗ присоединения не работают. */
    boolean accelerated;
    /** Защита подала команду отключения, а контакты выключателя ещё замкнуты (УРОВ). */
    boolean tripPending;
    /** Выключатель присоединения отключён и последней его отключила защита (пуск АПВ). */
    boolean breakerOpen, protectionTripped;
    /** Контакты нагрузки или двигателя замкнуты. */
    boolean connected;

    void clear() {
        currentA = voltagePu = frequencyHz = powerPu = reactivePu = diffPu = restraintPu = inrushH2 = 0;
        zRe = zIm = Double.POSITIVE_INFINITY;
        hotSpotC = Double.NaN;
        poleSlips = 0;
        inrushTrip = internalArc = accelerated = tripPending = breakerOpen = protectionTripped = connected = false;
    }
}
