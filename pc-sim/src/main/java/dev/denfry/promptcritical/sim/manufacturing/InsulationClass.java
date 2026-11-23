package dev.denfry.promptcritical.sim.manufacturing;

/**
 * Класс нагревостойкости изоляции обмотки (18 §18.6.9, ГОСТ 8865-93 / МЭК 60085): предельная температура, К, и
 * сопротивление изоляции новой чистой обмотки, МОм, — начальное значение {@code INSULATION_AGING} (13 §13.6.5, норма
 * 1000). Бумага класса A гигроскопична и начинает ниже [BALANCE]. Сериализуется по {@code name()}.
 */
public enum InsulationClass {
    A(378.15, 500),
    B(403.15, 1000),
    F(428.15, 1000);

    /** Перегрев на столько кельвинов вдвое сокращает срок службы изоляции (правило Монтзингера) [сверить: 8–10 К]. */
    public static final double HALVING_K = 10;

    public final double ratedK, nominalMOhm;

    InsulationClass(double ratedK, double nominalMOhm) {
        this.ratedK = ratedK;
        this.nominalMOhm = nominalMOhm;
    }

    /** Множитель скорости старения при температуре обмотки {@code tempK}: 1 на пределе класса. */
    public double agingFactor(double tempK) {
        return Math.pow(2, (tempK - ratedK) / HALVING_K);
    }
}
