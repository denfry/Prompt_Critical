package dev.denfry.promptcritical.sim.radiation;

/**
 * Допустимые уровни радиоактивного загрязнения поверхностей по НРБ-99/2009 (СанПиН 2.6.1.2523-09), табл. 8.9, в
 * частицах/(см²·мин); для α — столбец «отдельные» (Pu-239 и подобные, самые радиотоксичные). Для кожи, белья и
 * спецодежды норма относится ко всему загрязнению; для поверхностей помещений и оборудования — к снимаемому
 * (неснимаемое там контролируют по мощности дозы), как в примечании к таблице.
 *
 * <p>Поток частиц с поверхности переводится в активность через эффективность выхода ε_s (ISO 7503-1:2016, п. 7.2,
 * при отсутствии измеренной): 0,5 для β с E_max выше 0,4 МэВ, 0,25 для α и мягких β. Отсюда 2 α-частицы/(см²·мин)
 * на коже — 2 / (60 · 0,25) ≈ 0,13 Бк/см².
 */
public final class ContaminationLimits {
    /** Эффективность выхода с поверхности, ISO 7503-1. */
    public static final double EPS_S_ALPHA = 0.25, EPS_S_BETA = 0.5;

    /** Строки табл. 8.9 НРБ-99/2009: α, β в частицах/(см²·мин); {@code looseOnly} — норма на снимаемое. */
    public enum Surface {
        /** Неповреждённая кожа, спецбельё, полотенца, внутренняя поверхность лицевых частей СИЗ. */
        SKIN(2, 200, false),
        /** Основная спецодежда, внутренняя поверхность дополнительных СИЗ, наружная поверхность спецобуви. */
        OVERALLS(5, 2000, false),
        /** Наружная поверхность дополнительных СИЗ, снимаемых в саншлюзах. */
        PPE_OUTER(50, 10000, false),
        /** Помещения постоянного пребывания персонала и оборудование в них. */
        PERMANENT_ROOM(5, 2000, true),
        /** Помещения периодического пребывания персонала и оборудование в них. */
        PERIODIC_ROOM(50, 10000, true);

        public final double alphaFlux, betaFlux;
        public final boolean looseOnly;

        Surface(double alphaFlux, double betaFlux, boolean looseOnly) {
            this.alphaFlux = alphaFlux;
            this.betaFlux = betaFlux;
            this.looseOnly = looseOnly;
        }

        /** Норма по α, Бк/см². */
        public double alphaBqCm2() {
            return bqCm2(alphaFlux, EPS_S_ALPHA);
        }

        /** Норма по β, Бк/см². */
        public double betaBqCm2() {
            return bqCm2(betaFlux, EPS_S_BETA);
        }

        /** Кратность превышения по α (часть нормы, 1 — ровно на пределе). */
        public double alphaRatio(SurfaceContamination c) {
            return (looseOnly ? c.alphaLoose() : c.alpha()) / alphaBqCm2();
        }

        public double betaRatio(SurfaceContamination c) {
            return (looseOnly ? c.betaLoose() : c.beta()) / betaBqCm2();
        }

        /** Худшая из двух кратностей: больше 1 — загрязнено сверх нормы. */
        public double ratio(SurfaceContamination c) {
            return Math.max(alphaRatio(c), betaRatio(c));
        }
    }

    private ContaminationLimits() {
    }

    /** Активность, Бк/см², дающая поток {@code flux} частиц/(см²·мин) при эффективности выхода {@code epsS}. */
    public static double bqCm2(double flux, double epsS) {
        return flux / (60 * epsS);
    }

    /** Поток частиц, частиц/(см²·мин), от активности {@code bqCm2}. */
    public static double flux(double bqCm2, double epsS) {
        return bqCm2 * 60 * epsS;
    }
}
