package dev.denfry.promptcritical.sim.environment;

/**
 * Очистка дымовых газов (14 §14.2), минимальная модель степени очистки.
 *
 * <ul>
 *   <li><b>Циклон</b> — кривая Лаппла η(d) = 1/(1 + (d₅₀/d)²) для характерной частицы летучей золы PM₁₀ (d = 10 мкм)
 *   при d₅₀ = 5 мкм стандартного циклона: η ≈ 0,8 по PM₁₀ (Lapple 1951; Cooper & Alley, «Air Pollution Control», гл. 6).</li>
 *   <li><b>Электрофильтр</b> — Дойч–Андерсон η = 1 − exp(−w·A/Q), w = w₀·f_УЭС·(U/U_ном)², w₀ = 0,10 м/с, f_УЭС = 0,5 при
 *   сере угля &lt; 0,5 %, 1,0 при ≥ 1 % (между — линейно). Энергия 720 Дж/м³ газа.</li>
 *   <li><b>Мокрая известняковая сероочистка</b> — число единиц переноса растёт с отношением жидкость/газ: η = 1 − e^{−NTU},
 *   NTU = 0,25·(L/G) при L/G в л/м³ (L/G = 10–20 л/м³ дают 92–99 %, EPRI FGD guidelines). На 1 кг SO₂ — 1,562 кг
 *   известняка × Ca/S (1,05), 2,687 кг гипса и 0,687 кг CO₂.</li>
 * </ul>
 */
public final class GasCleaning {
    public static final double CYCLONE_D50_UM = 5, PM10_UM = 10, ESP_W0 = 0.10, ESP_J_M3 = 720, FGD_NTU_PER_LG = 0.25;
    public static final double LIMESTONE_PER_SO2 = 1.562, GYPSUM_PER_SO2 = 2.687, CO2_PER_SO2 = 0.687, CA_S = 1.05;

    private GasCleaning() {
    }

    /** Степень очистки циклона по PM₁₀. */
    public static double cyclone() {
        double r = CYCLONE_D50_UM / PM10_UM;
        return 1 / (1 + r * r);
    }

    /** Коэффициент удельного сопротивления золы по сере угля, %. */
    public static double resistivityFactor(double sulfurPct) {
        if (sulfurPct <= 0.5) return 0.5;
        if (sulfurPct >= 1) return 1;
        return 0.5 + (sulfurPct - 0.5);
    }

    /** Электрофильтр: площадь осадительных электродов, м², расход газа, м³/с, сера угля, %, напряжение, о.е. */
    public static double esp(double areaM2, double flowM3S, double sulfurPct, double voltagePu) {
        if (flowM3S <= 0) return 1;
        double w = ESP_W0 * resistivityFactor(sulfurPct) * voltagePu * voltagePu;
        return 1 - Math.exp(-w * areaM2 / flowM3S);
    }

    /** Мощность электрофильтра, Вт. */
    public static double espPowerW(double flowM3S) {
        return ESP_J_M3 * Math.max(0, flowM3S);
    }

    /** Сероочистка при отношении жидкость/газ {@code lgLitresPerM3}. */
    public static double fgd(double lgLitresPerM3) {
        return 1 - Math.exp(-FGD_NTU_PER_LG * Math.max(0, lgLitresPerM3));
    }
}
