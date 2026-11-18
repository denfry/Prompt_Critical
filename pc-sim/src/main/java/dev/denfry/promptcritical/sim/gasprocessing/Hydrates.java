package dev.denfry.promptcritical.sim.gasprocessing;

/**
 * Газовые гидраты: условие образования и ингибирование метанолом.
 *
 * <p>Равновесная температура образования гидратов природного газа относительной плотности около 0,6 — корреляция
 * Хаммершмидта T = 8,9·p^0,285 (°F, psia; Hammerschmidt, Ind. Eng. Chem. 26 (1934) 851), согласующаяся с графиком
 * Катца (Katz et al., «Handbook of Natural Gas Engineering», 1959; GPSA, рис. 20-19) в пределах 1–2 °C от 0,7 до
 * 14 МПа. Более тяжёлый газ образует гидраты при более высокой температуре; график Катца даёт около +1,1 K на
 * каждые 0,1 относительной плотности сверх 0,6 при 3–7 МПа — так поправка и вводится [аппроксимация графика].
 *
 * <p>Понижение температуры гидратообразования ингибитором — уравнение Хаммершмидта ΔT = K·W/(M(100 − W)), K = 1297 для
 * °C (2335 для °F), M — молярная масса ингибитора, W — его массовая доля в водной фазе, %. Для метанола M = 32,04
 * (GPSA, разд. 20; Бекиров, гл. 5). Формула надёжна до W ≈ 20–25 %.
 */
public final class Hydrates {
    public static final double HAMMERSCHMIDT_K = 1297, METHANOL_M = 32.04;
    private static final double PSI = 6894.757;

    private Hydrates() {
    }

    /** Равновесная температура образования гидратов газа относительной плотности {@code gravity}, К. */
    public static double formationK(double pAbsPa, double gravity) {
        double f = 8.9 * Math.pow(pAbsPa / PSI, 0.285);
        double k = (f - 32) / 1.8 + 273.15;
        return k + 11 * Math.max(0, gravity - 0.6);
    }

    /** Понижение температуры гидратообразования метанолом с массовой долей {@code wtPercent} в воде, К. */
    public static double methanolDepressionK(double wtPercent) {
        double w = Math.clamp(wtPercent, 0, 99);
        return HAMMERSCHMIDT_K * w / (METHANOL_M * (100 - w));
    }

    /** Массовая доля метанола в водной фазе, %, для понижения на {@code depressionK}. */
    public static double methanolForDepression(double depressionK) {
        if (depressionK <= 0) return 0;
        return 100 * METHANOL_M * depressionK / (HAMMERSCHMIDT_K + METHANOL_M * depressionK);
    }
}
