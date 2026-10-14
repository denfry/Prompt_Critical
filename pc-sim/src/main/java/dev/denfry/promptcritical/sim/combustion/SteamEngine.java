package dev.denfry.promptcritical.sim.combustion;

/**
 * Поршневая паровая машина двойного действия I0 (07 §7.9.7) — термодинамика цилиндров без состояния.
 *
 * <p>Идеальная индикаторная диаграмма с отсечкой и гиперболическим расширением:
 * p_mi = k_d·[p₁·ε·(1 + ln(1/ε)) − p_b], P_i = p_mi·V_s·n·z (z = 2 — двойное действие). Расход пара
 * ṁ = 2·n·ε·V_s·ρ''(p₁)·(1 + c_конд) на цилиндр. Динамика вала, маховик и регулятор — {@link SteamEngineUnit}.
 */
public final class SteamEngine {
    /** Ходов на оборот у машины двойного действия. */
    public static final int DOUBLE_ACTING = 2;
    /** Полнота индикаторной диаграммы k_d по умолчанию. */
    public static final double DIAGRAM_FACTOR = 0.8;
    /** Доля пара, конденсирующегося на холодных стенках цилиндра, c_конд по умолчанию. */
    public static final double CONDENSATION_LOSS = 0.25;
    /** Пределы степени наполнения (отсечки) ε, задаваемой кулисой. */
    public static final double CUTOFF_MIN = 0.1, CUTOFF_MAX = 0.7;

    private final double bore, stroke, sweptVolume, diagramFactor, condensationLoss;
    private final int cylinders;

    /**
     * @param boreM            диаметр цилиндра D, м
     * @param strokeM          ход поршня, м
     * @param cylinders        число цилиндров (кривошипы со сдвигом 180°/z)
     * @param diagramFactor    k_d
     * @param condensationLoss c_конд
     */
    public SteamEngine(double boreM, double strokeM, int cylinders, double diagramFactor, double condensationLoss) {
        if (!(boreM > 0 && strokeM > 0)) throw new IllegalArgumentException("geometry");
        if (cylinders < 1) throw new IllegalArgumentException("cylinders: " + cylinders);
        if (!(diagramFactor > 0 && diagramFactor <= 1)) throw new IllegalArgumentException("diagramFactor");
        if (!(condensationLoss >= 0)) throw new IllegalArgumentException("condensationLoss");
        this.bore = boreM;
        this.stroke = strokeM;
        this.cylinders = cylinders;
        this.diagramFactor = diagramFactor;
        this.condensationLoss = condensationLoss;
        this.sweptVolume = Math.PI / 4 * boreM * boreM * strokeM;
    }

    public double bore() {
        return bore;
    }

    public double stroke() {
        return stroke;
    }

    public int cylinders() {
        return cylinders;
    }

    /** Рабочий объём одного цилиндра V_s, м³. */
    public double sweptVolume() {
        return sweptVolume;
    }

    /** Среднее индикаторное давление p_mi, Па; отрицательно, если впуска не хватает на выхлоп (машина тормозит). */
    public double meanIndicatedPressure(double p1Pa, double pBackPa, double cutoff) {
        double e = clampCutoff(cutoff);
        return diagramFactor * (p1Pa * e * (1 + Math.log(1 / e)) - pBackPa);
    }

    /** Индикаторная мощность P_i = p_mi·V_s·n·z по всем цилиндрам, Вт. */
    public double indicatedPowerW(double p1Pa, double pBackPa, double cutoff, double revPerS) {
        return meanIndicatedPressure(p1Pa, pBackPa, cutoff) * sweptVolume * revPerS * DOUBLE_ACTING * cylinders;
    }

    /** Средний за оборот индикаторный момент p_mi·V_s·z/(2π) по всем цилиндрам, Н·м. */
    public double meanIndicatedTorqueNm(double p1Pa, double pBackPa, double cutoff) {
        return meanIndicatedPressure(p1Pa, pBackPa, cutoff) * sweptVolume * DOUBLE_ACTING * cylinders / (2 * Math.PI);
    }

    /**
     * Расход пара ṁ = 2·n·ε·V_s·ρ·(1 + c_конд) по всем цилиндрам, кг/с.
     *
     * @param rhoSteam плотность пара на впуске (после дросселя), кг/м³
     */
    public double steamFlowKgS(double rhoSteam, double cutoff, double revPerS) {
        return DOUBLE_ACTING * Math.abs(revPerS) * clampCutoff(cutoff) * sweptVolume * rhoSteam * (1 + condensationLoss)
                * cylinders;
    }

    /**
     * Форма момента по углу коленвала θ, средняя за оборот — 1. Ход цилиндра двойного действия даёт момент
     * ∝ |sin θ| (шатун бесконечной длины, давление на поршне по ходу не меняется); кривошипы цилиндров сдвинуты на
     * π/z. Одноцилиндровая машина стоит в мёртвой точке (θ = 0) без момента — её проворачивают вручную.
     */
    public double torqueShape(double crankAngle) {
        double s = 0;
        for (int k = 0; k < cylinders; k++) s += Math.abs(Math.sin(crankAngle + k * Math.PI / cylinders));
        return Math.PI / 2 * s / cylinders;
    }

    private static double clampCutoff(double cutoff) {
        return Math.max(CUTOFF_MIN, Math.min(CUTOFF_MAX, cutoff));
    }
}
