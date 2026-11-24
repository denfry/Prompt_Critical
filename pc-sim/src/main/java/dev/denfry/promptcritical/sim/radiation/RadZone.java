package dev.denfry.promptcritical.sim.radiation;

/**
 * Классы помещений станции и правила санпропускника (04b §4b.12). Зона свободного доступа — до 0,5 мкЗв/ч; зона
 * контролируемого доступа (ЗКД) делится на обслуживаемые (до 6 мкЗв/ч: 20 мЗв за 1700 ч с запасом 2), периодически
 * обслуживаемые (до 12 мкЗв/ч, половина времени пребывания) и необслуживаемые боксы (выше, вход только по наряду).
 * В ЗКД входят с электронным дозиметром и только пока годовая доза ниже предела; выходят через душ и мониторы.
 */
public enum RadZone {
    FREE(0.5e-6), CONTROLLED_SERVICED(6e-6), CONTROLLED_PERIODIC(12e-6), CONTROLLED_UNSERVICED(Double.POSITIVE_INFINITY);

    /** Проектная мощность дозы класса, Зв/ч. */
    public final double designSvH;

    RadZone(double designSvH) {
        this.designSvH = designSvH;
    }

    /** Зона контролируемого доступа: вход по дозиметру и годовой дозе. */
    public boolean controlled() {
        return this != FREE;
    }

    /** Фактическая мощность выше проектной для класса. */
    public boolean exceeded(double rateSvH) {
        return rateSvH > designSvH;
    }

    /** Самый мягкий класс, проектная мощность которого покрывает {@code rateSvH}. */
    public static RadZone classify(double rateSvH) {
        for (RadZone z : values()) if (!z.exceeded(rateSvH)) return z;
        return CONTROLLED_UNSERVICED;
    }

    public RadZone next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Почему дверь зоны не пускает. */
    public enum Verdict {
        OK, NO_DOSIMETER, OVER_ANNUAL_LIMIT, CONTAMINATED;

        public boolean ok() {
            return this == OK;
        }
    }

    /** Проверка на входе в зону класса {@code this}: надет ли ЭПД и не набран ли годовой предел. */
    public Verdict entry(boolean epdWorn, double annualSv, double limitSv) {
        if (!controlled()) return Verdict.OK;
        if (!epdWorn) return Verdict.NO_DOSIMETER;
        if (annualSv >= limitSv) return Verdict.OVER_ANNUAL_LIMIT;
        return Verdict.OK;
    }

    /**
     * Проверка на выходе: загрязнение рук и подошв не выше уровня монитора {@code limitBqCm2}
     * (3,3 Бк/см² ≈ 200 β/(см²·мин), 04b §4b.7.2).
     */
    public static Verdict exit(double handsBqCm2, double shoesBqCm2, double limitBqCm2) {
        return handsBqCm2 > limitBqCm2 || shoesBqCm2 > limitBqCm2 ? Verdict.CONTAMINATED : Verdict.OK;
    }

    /** Уровень монитора рук и ног, Бк/см². */
    public static final double HAND_FOOT_LIMIT_BQ_CM2 = 3.3;
}
