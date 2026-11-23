package dev.denfry.promptcritical.sim.grid;

/**
 * Молния в ЛЭП (07 §7.15.1): частота ударов, ток, перенапряжения и вероятность перекрытия. Чистые
 * функции; розыгрыш ударов и событие перекрытия — у мира, перекрытие в сети —
 * {@link PowerGrid#applyBranchFault} с самоустранением.
 */
public final class Lightning {
    /** Медианный ток молнии, кА ({@code lightning.median_current_ka}, IEEE Std 1243). */
    public static final double MEDIAN_CURRENT_KA = 31;
    /** Волновое сопротивление провода, Ом. */
    public static final double SURGE_IMPEDANCE_OHM = 400;
    /** Критическое импульсное напряжение гирлянды на метр, кВ/м (приближение IEEE Std 1243, [сверить]). */
    public static final double CFO_KV_PER_M = 585;
    /** Длина изоляционного промежутка на диск ПС-70, м. */
    public static final double DISC_LENGTH_M = 0.146;

    private Lightning() {}

    /** Ширина полосы сбора ударов W = b + 28·h^0,6 (Eriksson, 1987), м. */
    public static double collectionWidthM(double phaseSpacingM, double heightM) {
        return phaseSpacingM + 28 * Math.pow(heightM, 0.6);
    }

    /** λ = n_g·W·l, ударов/с; n_g — км⁻²·с⁻¹ ({@code lightning.flash_density_storm}). */
    public static double strokeRatePerS(double flashDensityPerKm2S, double widthM, double lengthM) {
        return flashDensityPerKm2S * widthM * 1e-3 * lengthM * 1e-3;
    }

    /** P(I > i) = 1/(1 + (i/31 кА)^2,6). */
    public static double probabilityCurrentAbove(double iKa) {
        if (!(iKa > 0)) return 1;
        return 1 / (1 + Math.pow(iKa / MEDIAN_CURRENT_KA, 2.6));
    }

    /** Ток с заданной вероятностью превышения — обращение распределения (розыгрыш по u ∈ (0, 1)). */
    public static double currentKa(double u) {
        if (!(u > 0 && u < 1)) throw new IllegalArgumentException("u");
        return MEDIAN_CURRENT_KA * Math.pow(1 / u - 1, 1 / 2.6);
    }

    /** U₅₀% гирлянды из {@code discs} дисков ПС-70, кВ. */
    public static double insulatorCfoKv(int discs) {
        return CFO_KV_PER_M * DISC_LENGTH_M * discs;
    }

    /** Удар в фазный провод без троса: U ≈ I·Z_c/2, кВ. */
    public static double directStrokeKv(double iKa) {
        return iKa * SURGE_IMPEDANCE_OHM / 2;
    }

    /** Удар в опору или трос: U ≈ I·R_з (без индуктивной составляющей), кВ. */
    public static double towerStrokeKv(double iKa, double groundOhm) {
        return iKa * groundOhm;
    }

    /** Удар рядом с линией: ≈ 30·I·h/d, кВ (опасно только для 6–10 кВ). */
    public static double inducedKv(double iKa, double heightM, double distanceM) {
        return 30 * iKa * heightM / distanceM;
    }

    /** Вероятность обратного перекрытия при ударе в опору: P(I > U₅₀%/R_з) (тест T7.17). */
    public static double backFlashoverProbability(double cfoKv, double groundOhm) {
        return probabilityCurrentAbove(cfoKv / groundOhm);
    }

    /** Сопротивление вертикального стержня (формула Дуайта): R = ρ/(2πL)·(ln(8L/d) − 1), Ом (тест T7.18). */
    public static double rodResistanceOhm(double soilOhmM, double lengthM, double diameterM) {
        return soilOhmM / (2 * Math.PI * lengthM) * (Math.log(8 * lengthM / diameterM) - 1);
    }

    /** Пробой изоляции трансформатора волной без ОПН: p = min(1, U/BIL − 0,8). */
    public static double transformerBreakdownProbability(double surgeKv, double bilKv) {
        return Math.max(0, Math.min(1, surgeKv / bilKv - 0.8));
    }

    /** Куда пришёлся удар: в фазный провод, в опору (через её заземлитель) или в землю рядом с линией. */
    public enum Stroke { PHASE, TOWER, NEARBY }

    /**
     * Исход удара для изоляции линии с U₅₀% {@code cfoKv}: перекрытие — если перенапряжение выше U₅₀%; волна, ушедшая по
     * проводу к концам пролёта, после перекрытия срезана до U₅₀%, без перекрытия — само перенапряжение (удар в опору
     * без обратного перекрытия волны на проводе не даёт).
     */
    public record Outcome(boolean flashover, double overvoltageKv, double surgeKv) {
    }

    /**
     * Перенапряжение и перекрытие по таблице §7.15.1. {@code groundOhm} — заземлитель опоры (бесконечность — опора без
     * заземления: ток идёт в провод); {@code heightM}, {@code distanceM} — подвес провода и расстояние удара рядом.
     */
    public static Outcome stroke(double iKa, Stroke kind, double cfoKv, double groundOhm, double heightM, double distanceM) {
        double u = switch (kind) {
            case PHASE -> directStrokeKv(iKa);
            case TOWER -> Double.isFinite(groundOhm) ? towerStrokeKv(iKa, groundOhm) : directStrokeKv(iKa);
            case NEARBY -> inducedKv(iKa, heightM, Math.max(distanceM, 1));
        };
        boolean flash = u > cfoKv;
        double surge = flash ? cfoKv : kind == Stroke.TOWER && Double.isFinite(groundOhm) ? 0 : u;
        return new Outcome(flash, u, surge);
    }

    /** U₅₀% штыревой изоляции линии класса {@code vc}, кВ: у 6–10 кВ — грозовой импульс класса, у 0,4 кВ [BALANCE] 30 кВ. */
    public static double lineCfoKv(VoltageClass vc) {
        return Double.isNaN(vc.bilKv) ? LV_LINE_CFO_KV : vc.bilKv;
    }

    /** U₅₀% изоляции линии 0,4 кВ (штыревые изоляторы на крюках), кВ [BALANCE]. */
    public static final double LV_LINE_CFO_KV = 30;
    /** Стержень {@code ground_electrode}: 3 м, ⌀ 16 мм (тест T7.18). */
    public static final double ROD_LENGTH_M = 3, ROD_DIAMETER_M = 0.016;
    /** Коэффициент использования нескольких стержней рядом (взаимное экранирование) [BALANCE]. */
    public static final double ROD_UTILIZATION = 0.8;

    /** Заземлитель из {@code rods} стержней в грунте ρ, Ом: R₁/n, у нескольких — с коэффициентом использования. */
    public static double electrodeOhm(double soilOhmM, int rods) {
        if (rods <= 0) return Double.POSITIVE_INFINITY;
        double r1 = rodResistanceOhm(soilOhmM, ROD_LENGTH_M, ROD_DIAMETER_M);
        return rods == 1 ? r1 : r1 / (rods * ROD_UTILIZATION);
    }

    /** ОПН ограничивает волну до 2,5–3 U_ф,max; берём 2,75. */
    public static double arresterLimitKv(VoltageClass vc) {
        return 2.75 * vc.volts * 1.1 * Math.sqrt(2.0 / 3) / 1e3;
    }
}
