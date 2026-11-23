package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.manufacturing.Workpiece.Section;
import dev.denfry.promptcritical.sim.metallurgy.AirCooling;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatment;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;

/**
 * Нагрев в горне, остывание, окалина и пережог. Нагрев — лумпед, излучение плюс конвекция от огня, фиксированный
 * шаг {@value #STEP_S} с, результат детерминирован. Окалина растёт по параболическому закону и съедает сечение,
 * на каждый нагрев счёт окалины начинается с нуля (она отлетает под молотом).
 */
public final class ForgeHeating {
    private ForgeHeating() {}

    /** Конвекция от пламени горна, Вт/(м²·К) [сверить]. */
    public static final double FIRE_CONVECTION_W_M2K = 50;
    /** Шаг интегрирования, с. */
    public static final double STEP_S = 0.5;
    /** Масса Fe на массу O в FeO: 55,85 / 16,00. */
    public static final double FE_PER_O = 3.49;
    /** Плотность стали, г/см³. */
    private static final double RHO_G_CM3 = 7.85;
    /** Солидус при C ≤ 0,17 % и при C = 2,11 %, °C (диаграмма Fe–C) [сверить]. */
    private static final double SOLIDUS_LOW_C = 1495, SOLIDUS_HIGH_C = 1147;
    private static final double C_LOW_PCT = 0.17, C_HIGH_PCT = 2.11;
    /** Запас пережога ниже солидуса, К [сверить]. */
    private static final double BURN_MARGIN_K = 100;
    private static final double ZERO_C = 273.15;

    /** Температура огня горна: 900–1350 °C по дутью 0..1, древесный уголь [сверить]. */
    public static double fireTempK(double blast) {
        return 1173.15 + 450 * Math.max(0, Math.min(1, blast));
    }

    /** Температура пережога: солидус минус 100 К, солидус линейно по углероду между 0,17 и 2,11 % [сверить]. */
    public static double burningK(SteelGrade g) {
        double c = Math.max(C_LOW_PCT, Math.min(C_HIGH_PCT, g.carbonPct()));
        double solidusC = SOLIDUS_LOW_C + (SOLIDUS_HIGH_C - SOLIDUS_LOW_C) * (c - C_LOW_PCT) / (C_HIGH_PCT - C_LOW_PCT);
        return solidusC + ZERO_C - BURN_MARGIN_K;
    }

    /**
     * Константа параболической окалины k_p, г²·см⁻⁴·с⁻¹ = 6,1·exp(−169 300/(8,314·T)); Paidassi 1958, в Birks,
     * Meier, Pettit «Introduction to High Temperature Oxidation of Metals», 2006 [сверить].
     */
    public static double scaleRateConstant(double tK) {
        return 6.1 * Math.exp(-169_300 / (8.314 * tK));
    }

    /**
     * Нагрев в огне {@code fireK} за {@code seconds}: температура, убыль сечения от окалины, пережог.
     * Окалина отсчитывается от нуля на каждый вызов (она отваливается на наковальне), поэтому heat
     * вызывают один раз на весь нагрев, а не каждый тик — иначе √Σ завышает угар.
     */
    public static Workpiece heat(Workpiece w, double fireK, double seconds) {
        if (!(seconds > 0)) {
            return w;
        }
        double heatCapacity = w.massKg() * AirCooling.STEEL_CP;
        double surface = w.surfaceM2();
        double burnK = burningK(w.grade());
        double t = w.tempK();
        boolean startedAbove = t > fireK;
        double oxideSq = 0;
        boolean burnt = false;
        double left = seconds;
        while (left > 0) {
            double dt = Math.min(STEP_S, left);
            left -= dt;
            double q = AirCooling.EMISSIVITY * AirCooling.SIGMA * (Math.pow(fireK, 4) - Math.pow(t, 4))
                    + FIRE_CONVECTION_W_M2K * (fireK - t);
            t += q * surface * dt / heatCapacity;
            // к температуре огня подходят со стороны старта: горячая деталь остывает постепенно, а не скачком
            t = startedAbove ? Math.max(t, fireK) : Math.min(t, fireK);
            oxideSq += scaleRateConstant(t) * dt;
            if (t > burnK) {
                burnt = true;
            }
        }
        // sqrt(oxideSq) — г кислорода на см²; потеря железа, затем глубина в см, затем в м
        double recessionM = Math.sqrt(oxideSq) * FE_PER_O / RHO_G_CM3 * 0.01;
        double a = Math.max(Forging.MIN_SECTION_M, w.aM() - 2 * recessionM);
        double b = w.section() == Section.FLAT ? Math.max(Forging.MIN_SECTION_M, w.bM() - 2 * recessionM) : a;
        var out = w.withSection(a, b, w.lengthM()).withTemp(t);
        // нагрев выше Ac3 + запас переводит в аустенит: закалка и улучшение стираются, деталь снова нормализована
        var state = w.grade().state();
        if (t >= HeatTreatment.ac3K(w.grade()) + HeatTreatment.AC3_MARGIN_K
                && (state == HeatTreatState.QUENCHED || state == HeatTreatState.QUENCHED_TEMPERED)) {
            var normalized = w.grade().withState(HeatTreatState.NORMALIZED);
            out = out.withGrade(normalized).withHardness(normalized.hardnessHrc());
        }
        return burnt ? out.withDefect(PartDefect.BURNT, 1.0) : out;
    }

    /** Остывание на воздухе: {@link AirCooling#tempAfter} по эквивалентной толщине. */
    public static Workpiece cool(Workpiece w, double ambientK, double seconds) {
        return w.withTemp(AirCooling.tempAfter(w.tempK(), ambientK, w.equivalentThicknessM(), seconds));
    }
}
