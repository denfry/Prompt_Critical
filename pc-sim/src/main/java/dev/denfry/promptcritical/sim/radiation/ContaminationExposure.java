package dev.denfry.promptcritical.sim.radiation;

/**
 * Внутреннее облучение от поверхностного загрязнения — ожидаемая эффективная доза (на 50 лет), набранная за время
 * пребывания. Два пути, как в сценарии «пребывание в здании» NUREG/CR-5512 Vol. 1 (1992):
 * <ul>
 *   <li><b>ингаляция</b> — снимаемое загрязнение пола поднимается в воздух: C = K · A_s, коэффициент ресуспензии
 *       K = {@value #RESUSPENSION_M} 1/м — типичное значение для помещения с обычной активностью людей (литературный
 *       разброс 10⁻⁷–10⁻⁴ 1/м, обзор Sehmel, 1980; сама NUREG/CR-5512 для скрининга берёт консервативнее); дыхание
 *       1,2 м³/ч ({@link AirborneDose#BREATHING_M3_S}, ICRP 66);</li>
 *   <li><b>заглатывание</b> — «рука — рот»: эффективная скорость {@value #SECONDARY_INGESTION_M2_H} м²/ч снимаемого
 *       загрязнения (NUREG/CR-5512 Vol. 1), с пола и с собственных рук.</li>
 * </ul>
 * Дозовые коэффициенты для работников (ICRP 68, 1994; они же в НРБ-99/2009, прил. П-1): α — Pu-239, ингаляция тип M,
 * АМАД 5 мкм, 3,2·10⁻⁵ Зв/Бк, заглатывание (f₁ 5·10⁻⁴) 2,5·10⁻⁷; β — Cs-137, ингаляция тип F 6,7·10⁻⁹, заглатывание
 * 1,3·10⁻⁸ Зв/Бк. Ожидаемая доза записывается сразу при поступлении (так и в учёте доз), острой лучевой болезни она не даёт.
 */
public final class ContaminationExposure {
    public static final double RESUSPENSION_M = 1e-6;
    public static final double SECONDARY_INGESTION_M2_H = 1.1e-4;
    public static final double INH_ALPHA_SV_BQ = 3.2e-5, INH_BETA_SV_BQ = 6.7e-9;
    public static final double ING_ALPHA_SV_BQ = 2.5e-7, ING_BETA_SV_BQ = 1.3e-8;
    private static final double CM2_PER_M2 = 1e4;

    private ContaminationExposure() {
    }

    /** Объёмная активность над полом с загрязнением {@code floor}: {α, β}, Бк/м³. */
    public static double[] airBqM3(SurfaceContamination floor) {
        return new double[] {RESUSPENSION_M * floor.alphaLoose() * CM2_PER_M2, RESUSPENSION_M * floor.betaLoose() * CM2_PER_M2};
    }

    /** Скорость набора ожидаемой дозы от вдыхания, Зв/ч. */
    public static double inhalationSvH(SurfaceContamination floor) {
        double[] c = airBqM3(floor);
        return (c[0] * INH_ALPHA_SV_BQ + c[1] * INH_BETA_SV_BQ) * AirborneDose.BREATHING_M3_S * 3600;
    }

    /** Скорость набора ожидаемой дозы от заглатывания с поверхности {@code s}, Зв/ч. */
    public static double ingestionSvH(SurfaceContamination s) {
        double m2 = SECONDARY_INGESTION_M2_H * CM2_PER_M2;
        return m2 * (s.alphaLoose() * ING_ALPHA_SV_BQ + s.betaLoose() * ING_BETA_SV_BQ);
    }

    /** Всё вместе: пол под ногами (вдох и заглатывание) и собственные руки (заглатывание), Зв/ч. */
    public static double committedSvH(SurfaceContamination floor, SurfaceContamination hands) {
        return inhalationSvH(floor) + ingestionSvH(floor) + ingestionSvH(hands);
    }
}
