package dev.denfry.promptcritical.sim.pipeline;

/**
 * Магистральный трубопровод (09 §9.8.1): допустимое давление по Барлоу с коэффициентами ASME B31.8 / СП 36.13330,
 * давление разрушения по остаточной стенке, запас газа в трубе (line pack), потери на трение по Дарси — Вейсбаху и
 * массовый баланс для обнаружения утечек.
 */
public final class Pipeline {
    /** Сталь трубы (08 {@code steel_grade}): предел текучести, Па. */
    public enum Grade {
        ST20(245e6), X52(359e6), X70(483e6);

        public final double yieldPa;

        Grade(double yieldPa) {
            this.yieldPa = yieldPa;
        }
    }

    /** Класс местности: коэффициент F (0,72 — класс 1, 0,6 / 0,5 / 0,4 — ближе к жилью). */
    public enum LocationClass {
        CLASS_1(0.72), CLASS_2(0.6), CLASS_3(0.5), CLASS_4(0.4);

        public final double factor;

        LocationClass(double factor) {
            this.factor = factor;
        }
    }

    public static final double R = 8.314462618;
    /** Шероховатость стальной трубы, м (Moody, коммерческая сталь) [сверить]. */
    public static final double STEEL_ROUGHNESS_M = 4.5e-5;

    private Pipeline() {
    }

    /**
     * Допустимое давление p = 2·S_y·t/D·F·E·T (Барлоу).
     *
     * @param e шовный коэффициент: 1 — бесшовная, 0,8–1 — сварная
     * @param t температурный коэффициент: 1 до 120 °C
     */
    public static double designPressurePa(Grade g, double wallM, double odM, LocationClass c, double e, double t) {
        return 2 * g.yieldPa * wallM / odM * c.factor * e * t;
    }

    /**
     * Давление разрушения: стенка течёт, когда кольцевое напряжение по остаточной толщине доходит до S_y
     * (превышение p_design/F, §9.8.1). Утонение от коррозии {@code lossFraction} — доля стенки.
     */
    public static double burstPressurePa(Grade g, double wallM, double odM, double lossFraction) {
        return 2 * g.yieldPa * wallM * (1 - Math.clamp(lossFraction, 0, 1)) / odM;
    }

    /** Внутренний объём участка, м³. */
    public static double volumeM3(double odM, double wallM, double lengthM) {
        double id = odM - 2 * wallM;
        return Math.PI * id * id / 4 * lengthM;
    }

    /** Запас газа в трубе при давлении {@code pPa}, кг. */
    public static double linepackKg(double pPa, double volumeM3, double molarKgMol, double z, double tK) {
        return pPa * volumeM3 * molarKgMol / (z * R * tK);
    }

    /** Коэффициент трения Дарси: ламинарный 64/Re, турбулентный — Swamee — Jain. */
    public static double frictionFactor(double re, double relRoughness) {
        if (re <= 0) return 0;
        if (re < 2300) return 64 / re;
        double l = Math.log10(relRoughness / 3.7 + 5.74 / Math.pow(re, 0.9));
        return 0.25 / (l * l);
    }

    /** Потеря давления на трение, Па: Δp = f·(L/D)·ρv²/2. */
    public static double pressureDropPa(double massKgS, double densityKgM3, double viscosityPaS, double idM, double lengthM) {
        if (massKgS <= 0 || idM <= 0) return 0;
        double area = Math.PI * idM * idM / 4, v = massKgS / (densityKgM3 * area);
        double re = densityKgM3 * v * idM / viscosityPaS;
        return frictionFactor(re, STEEL_ROUGHNESS_M / idM) * lengthM / idM * densityKgM3 * v * v / 2;
    }

    /** Скорость потока, м/с. */
    public static double velocityMs(double massKgS, double densityKgM3, double idM) {
        return idM <= 0 ? 0 : massKgS / (densityKgM3 * Math.PI * idM * idM / 4);
    }

    /**
     * Небаланс участка: вход − выход − прирост запаса в трубе, кг/с (§9.8.1: массовый баланс расходомеров на концах с
     * учётом изменения запаса). Больше нуля — где-то теряется продукт.
     */
    public static double imbalanceKgS(double inKgS, double outKgS, double linepackRateKgS) {
        return inKgS - outKgS - linepackRateKgS;
    }
}
