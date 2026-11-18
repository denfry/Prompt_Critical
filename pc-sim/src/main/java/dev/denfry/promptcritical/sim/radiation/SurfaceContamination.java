package dev.denfry.promptcritical.sim.radiation;

/**
 * Поверхностное радиоактивное загрязнение, Бк/см², раздельно по α и β и по прочности связи с поверхностью: снимаемое
 * (нефиксированное — пыль, капли, рыхлые отложения: уходит мазком, переносится касанием, поднимается в воздух) и
 * неснимаемое (фиксированное — вросло в оксидную плёнку, краску, поры бетона: мазком не берётся, снимается только
 * химией или вместе со слоем материала). Терминология и деление — ISO 7503-1:2016 и НРБ-99/2009 (п. 3.6, табл. 8.9).
 *
 * <p>Нуклидный состав сведён к двум представителям: α — плутоний-239 (выделение 24 110 лет), β — цезий-137 с
 * Ba-137m (30 лет); распад за игровое время пренебрежим и не считается. Дозовые коэффициенты — {@link ContaminationExposure}.
 */
public record SurfaceContamination(double alphaLoose, double alphaFixed, double betaLoose, double betaFixed) {
    public static final SurfaceContamination NONE = new SurfaceContamination(0, 0, 0, 0);
    /** Ниже этого (Бк/см² по α + β) загрязнение не храним: на порядок ниже предела МДА зонда. */
    public static final double NEGLIGIBLE_BQ_CM2 = 1e-4;
    /**
     * Доля α в загрязнении от отработанного топлива: ТВС лёгководного реактора при 40 МВт·сут/кг после 1–10 лет выдержки
     * несёт ≈ 1,5·10¹⁴ Бк α на тонну (Pu-238, Pu-239/240, Am-241, Cm-244) против ≈ 10¹⁶ Бк β (OECD/NEA, «Spent Nuclear
     * Fuel Assay Data», 2011; порядок по ORIGEN) — около 1 %.
     */
    public static final double FUEL_ALPHA_PER_BETA = 0.01;

    public SurfaceContamination {
        if (alphaLoose < 0 || alphaFixed < 0 || betaLoose < 0 || betaFixed < 0)
            throw new IllegalArgumentException("negative contamination");
    }

    /** Свежий пролив: {@code looseFraction} снимаемой части (капли и пыль на чистой поверхности — почти всё). */
    public static SurfaceContamination of(double alphaBqCm2, double betaBqCm2, double looseFraction) {
        double f = Math.max(0, Math.min(1, looseFraction));
        return new SurfaceContamination(alphaBqCm2 * f, alphaBqCm2 * (1 - f), betaBqCm2 * f, betaBqCm2 * (1 - f));
    }

    /** Пролив активности {@code alphaBq}, {@code betaBq} ровно по площади {@code areaCm2}. */
    public static SurfaceContamination spill(double alphaBq, double betaBq, double areaCm2, double looseFraction) {
        if (areaCm2 <= 0) throw new IllegalArgumentException("area");
        return of(alphaBq / areaCm2, betaBq / areaCm2, looseFraction);
    }

    public double alpha() {
        return alphaLoose + alphaFixed;
    }

    public double beta() {
        return betaLoose + betaFixed;
    }

    public double total() {
        return alpha() + beta();
    }

    /** Только снимаемая часть. */
    public SurfaceContamination loose() {
        return new SurfaceContamination(alphaLoose, 0, betaLoose, 0);
    }

    public boolean negligible() {
        return total() < NEGLIGIBLE_BQ_CM2;
    }

    public SurfaceContamination plus(SurfaceContamination o) {
        return new SurfaceContamination(alphaLoose + o.alphaLoose, alphaFixed + o.alphaFixed, betaLoose + o.betaLoose, betaFixed + o.betaFixed);
    }

    public SurfaceContamination minus(SurfaceContamination o) {
        return new SurfaceContamination(Math.max(0, alphaLoose - o.alphaLoose), Math.max(0, alphaFixed - o.alphaFixed),
                Math.max(0, betaLoose - o.betaLoose), Math.max(0, betaFixed - o.betaFixed));
    }

    public SurfaceContamination scaled(double k) {
        return new SurfaceContamination(alphaLoose * k, alphaFixed * k, betaLoose * k, betaFixed * k);
    }

    /** После одного прохода дезактивации: снимаемое делится на КД снимаемого, фиксированное — на свой КД. */
    public SurfaceContamination after(DeconMethod m) {
        return after(m, 1);
    }

    /**
     * После доли {@code fraction} полного прохода (душ, начатый и брошенный на полпути): КД^доля — прогресс
     * дезактивации экспоненциален по времени, пока раствор свежий (НЕА 1999, гл. 3).
     */
    public SurfaceContamination after(DeconMethod m, double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        double kl = Math.pow(m.dfLoose, -f), kf = Math.pow(m.dfFixed, -f);
        return new SurfaceContamination(alphaLoose * kl, alphaFixed * kf, betaLoose * kl, betaFixed * kf);
    }

    /** Площадно-взвешенное среднее: две поверхности слились в одну (стопка предметов, мусор в бочке). */
    public static SurfaceContamination mix(SurfaceContamination a, double areaA, SurfaceContamination b, double areaB) {
        double s = areaA + areaB;
        if (s <= 0) return NONE;
        return a.scaled(areaA / s).plus(b.scaled(areaB / s));
    }
}
