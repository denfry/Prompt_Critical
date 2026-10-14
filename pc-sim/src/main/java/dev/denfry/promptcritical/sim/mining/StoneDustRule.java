package dev.denfry.promptcritical.sim.mining;

/**
 * Взрывчатость угольной пыли и осланцевание (08 §8.2.6). Пыль взрывоопасна, если поднятая концентрация
 * c_d ≥ c_НКПР и инертная доля x_ин &lt; x_ин,треб; обе границы — по таблице от V^daf с линейной интерполяцией
 * между столбцами (до 15 % — по столбцу 15 %, с запасом; до 10 % — неопасна). Метан снижает нижний предел пыли
 * по Бартнехту: c_НКПР,гибр = c_НКПР·(1 − x_CH4/x_НКПР,CH4)².
 */
public final class StoneDustRule {
    /** Нижний и верхний концентрационные пределы метана в воздухе. */
    public static final double CH4_LEL = 0.05, CH4_UEL = 0.15;
    /** Ниже этого выхода летучих пыль неопасна. */
    public static final double NON_EXPLOSIVE_VDAF = 0.10;

    private static final double[] VDAF = {0.15, 0.20, 0.25, 0.30, 0.35, 0.40};
    private static final double[] INERT_REQUIRED = {0.60, 0.65, 0.70, 0.75, 0.78, 0.80};
    private static final double[] LEL_G_M3 = {120, 80, 60, 45, 35, 30};

    private StoneDustRule() {}

    /** Требуемая инертная доля x_ин,треб; 0 — пыль неопасна. */
    public static double requiredInertFraction(double volatileDaf) {
        return volatileDaf < NON_EXPLOSIVE_VDAF ? 0 : interpolate(INERT_REQUIRED, volatileDaf);
    }

    /** Нижний предел взрываемости пыли c_НКПР, г/м³; бесконечность — неопасна. */
    public static double lowerLimitGm3(double volatileDaf) {
        return volatileDaf < NON_EXPLOSIVE_VDAF ? Double.POSITIVE_INFINITY : interpolate(LEL_G_M3, volatileDaf);
    }

    /** Нижний предел пыли в присутствии метана (гибридная смесь), г/м³. */
    public static double hybridLowerLimitGm3(double volatileDaf, double xCh4) {
        double lel = lowerLimitGm3(volatileDaf);
        double k = 1 - Math.min(1, Math.max(0, xCh4) / CH4_LEL);
        return lel * k * k;
    }

    /** Концентрация пыли, поднятой волной: c_d = m_d·P/S, г/м³. */
    public static double raisedConcentrationGm3(double settledGm2, double perimeterM, double sectionM2) {
        return settledGm2 * perimeterM / sectionM2;
    }

    /**
     * Пыль поддерживает фронт.
     *
     * @param dustGm3       поднятая концентрация угольной пыли c_d, г/м³
     * @param inertFraction x_ин — доля инертной в осевшей пыли
     * @param volatileDaf   V^daf угля пыли
     * @param xCh4          метан в узле
     */
    public static boolean dustExplosive(double dustGm3, double inertFraction, double volatileDaf, double xCh4) {
        if (volatileDaf < NON_EXPLOSIVE_VDAF || dustGm3 <= 0) return false;
        return dustGm3 >= hybridLowerLimitGm3(volatileDaf, xCh4) && inertFraction < requiredInertFraction(volatileDaf);
    }

    /** Метан в пределах взрываемости 5–15 %. */
    public static boolean methaneExplosive(double xCh4) {
        return xCh4 >= CH4_LEL && xCh4 <= CH4_UEL;
    }

    private static double interpolate(double[] y, double v) {
        if (v <= VDAF[0]) return y[0];
        for (int i = 1; i < VDAF.length; i++)
            if (v <= VDAF[i]) return y[i - 1] + (y[i] - y[i - 1]) * (v - VDAF[i - 1]) / (VDAF[i] - VDAF[i - 1]);
        return y[y.length - 1];
    }
}
