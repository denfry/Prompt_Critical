package dev.denfry.promptcritical.sim.grid;

/**
 * Привод машины с вводом 6,3 кВ (энергопроект 2, решение владельца 2026-11-23: активные машины мощнее 200 кВт берут
 * номинал на 6,3 кВ). Двигатели и печи такого ввода включает свой контактор или масляный выключатель с нулевой
 * защитой той же уставки, что у щитов 0,4 кВ ({@link PowerBoard#UV_TRIP_PU}, {@link PowerBoard#UV_DELAY_S}):
 *
 * <ul>
 *   <li>под напряжением машина работает со скоростью {@code f / 50} — асинхронные двигатели идут за частотой сети;</li>
 *   <li>если напряжение ниже 0,85 номинала дольше 0,5 с, выключатель отпадает и сам не включается, пока его не
 *       включат кнопкой «Пуск»;</li>
 *   <li>защита взводится, когда ввод хоть раз был в норме, поэтому машина, поставленная до пуска станции, не
 *       отпадает и пойдёт, как только появится напряжение.</li>
 * </ul>
 */
public final class MvDrive {
    private boolean on = true;
    private boolean armed;
    private boolean tripped;
    private double underS;
    private double factor;

    /** Выключатель включён (машина берёт мощность из сети). */
    public boolean on() {
        return on;
    }

    /** Выключатель отпал по нулевой защите и ждёт «Пуска». */
    public boolean tripped() {
        return tripped;
    }

    /** Доля номинальной скорости на прошлом шаге, 0…1. */
    public double factor() {
        return factor;
    }

    /** Мощность, которую машина заявляет сети при номинале {@code ratedW}: ноль, пока выключатель отключён. */
    public double demandW(double ratedW) {
        return on && Double.isFinite(ratedW) ? Math.max(0, ratedW) : 0;
    }

    /** Кнопка «Пуск»: включить выключатель (снимает отпадание). */
    public void start() {
        on = true;
        tripped = false;
        underS = 0;
    }

    /** Кнопка «Стоп». */
    public void stop() {
        on = false;
        tripped = false;
        underS = 0;
    }

    /**
     * Шаг: напряжение {@code vPu} (о.е. 6,3 кВ, 0 — ввод без напряжения) и частота {@code fHz} на вводе.
     *
     * @return доля номинальной скорости машины, 0…1
     */
    public double step(double dt, double vPu, double fHz) {
        if (!(dt > 0)) throw new IllegalArgumentException("dt must be positive");
        boolean healthy = vPu >= PowerBoard.UV_TRIP_PU;
        if (healthy) {
            armed = true;
            underS = 0;
        } else if (on && armed) {
            underS += dt;
            if (underS >= PowerBoard.UV_DELAY_S) {
                on = false;
                tripped = true;
                armed = false;
            }
        }
        factor = on && healthy ? Math.max(0, Math.min(1, fHz / PowerBoard.NOMINAL_HZ)) : 0;
        return factor;
    }

    /** Состояние для сохранения: бит 0 — включён, 1 — отпал, 2 — защита взведена. */
    public int bits() {
        return (on ? 1 : 0) | (tripped ? 2 : 0) | (armed ? 4 : 0);
    }

    /** Восстановить состояние из {@link #bits()}. */
    public void restore(int bits) {
        on = (bits & 1) != 0;
        tripped = (bits & 2) != 0;
        armed = (bits & 4) != 0;
        underS = 0;
        factor = 0;
    }
}
