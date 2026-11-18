package dev.denfry.promptcritical.sim.chlor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Дихлорэтан (ЭДХ, 1,2-C₂H₄Cl₂) сбалансированным методом (Ullmann, «Vinyl Chloride», разд. 4; Лебедев, 1988, гл. 3;
 * Dreher et al., «Chlorethanes and Chloroethylenes»):
 * <ul>
 *   <li>прямое хлорирование C₂H₄ + Cl₂ → C₂H₄Cl₂ в жидком ЭДХ на FeCl₃ при 50–70 °C, ΔH = −218 кДж/моль, селективность
 *   ≈ 99 % (побочный 1,1,2-трихлорэтан);</li>
 *   <li>оксихлорирование C₂H₄ + 2 HCl + ½ O₂ → C₂H₄Cl₂ + H₂O на CuCl₂/Al₂O₃ в кипящем слое при 220–250 °C,
 *   ΔH = −239 кДж/моль; превращение HCl 98–99,5 %, селективность по этилену 94–97 % — остальное сгорает до CO₂.
 *   Выше ≈ 260 °C растут горение этилена и унос меди.</li>
 * </ul>
 * HCl для оксихлорирования — с пиролиза ЭДХ ({@link EdcCracker}): так весь хлор уходит в винилхлорид.
 */
public final class EdcUnit {
    public static final double M_C2H4 = 0.028054, M_EDC = 0.098959, M_HCL = 0.036461, M_O2 = 0.031998, M_CL2 = DiaphragmCellRoom.M_CL2;
    public static final double MIN_K = 473.15, MAX_K = 553.15;
    /** Производительность по ЭДХ, моль/с, на которую рассчитаны реакторы [BALANCE]. */
    public static final double CAPACITY_MOL_S = 4;

    private double oxyK = 503.15;
    private boolean on = true;
    // последний шаг, моль/с
    private double edcDirect, edcOxy, c2h4Used, cl2Used, hclUsed, o2Used, co2Made, heatW;

    public double oxyK() {
        return oxyK;
    }

    public void setOxyK(double k) {
        oxyK = Math.clamp(k, MIN_K, MAX_K);
    }

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    /** Превращение HCl в оксихлораторе при температуре слоя. */
    public static double hclConversion(double tempK) {
        double k = 0.9 * Math.exp(9000 * (1 / 503.15 - 1 / tempK));
        return Math.min(0.995, 1 - Math.exp(-4.6 * k));
    }

    /** Селективность оксихлорирования по этилену. */
    public static double oxySelectivity(double tempK) {
        return Math.clamp(0.965 - 0.0015 * Math.max(0, tempK - 503.15) - 0.0006 * Math.max(0, tempK - 533.15), 0.80, 0.97);
    }

    /**
     * Шаг: доступные сырьё, моль/с. Сначала оксихлорирование (вся HCl с кислородом), остаток мощности — прямое
     * хлорирование.
     */
    public void step(double c2h4, double cl2, double hcl, double o2) {
        edcDirect = edcOxy = c2h4Used = cl2Used = hclUsed = o2Used = co2Made = heatW = 0;
        if (!on) return;
        double xh = hclConversion(oxyK), sel = oxySelectivity(oxyK);
        // HCl на моль ЭДХ: 2/xh; этилен: 1/sel; кислород: ½ на ЭДХ и 3 на сгоревший этилен
        double burnPerEdc = (1 - sel) / sel;
        double oxy = Math.min(CAPACITY_MOL_S, Math.min(hcl * xh / 2, Math.min(c2h4 / (1 + burnPerEdc), o2 / (0.5 + 3 * burnPerEdc))));
        oxy = Math.max(0, oxy);
        edcOxy = oxy;
        hclUsed = oxy * 2 / xh; // непрореагировавшая HCl отмывается водой и теряется [допущение]
        c2h4Used = oxy * (1 + burnPerEdc);
        o2Used = oxy * (0.5 + 3 * burnPerEdc);
        co2Made = oxy * burnPerEdc * 2;
        double direct = Math.max(0, Math.min(CAPACITY_MOL_S - oxy, Math.min(cl2, (c2h4 - c2h4Used) / 1.01)));
        edcDirect = direct * 0.99;
        c2h4Used += direct;
        cl2Used = direct;
        heatW = edcOxy * 239e3 + direct * 218e3 + oxy * burnPerEdc * 1323e3;
    }

    public double edcMolS() {
        return edcDirect + edcOxy;
    }

    public double edcDirectMolS() {
        return edcDirect;
    }

    public double edcOxyMolS() {
        return edcOxy;
    }

    public double c2h4UsedMolS() {
        return c2h4Used;
    }

    public double cl2UsedMolS() {
        return cl2Used;
    }

    public double hclUsedMolS() {
        return hclUsed;
    }

    public double o2UsedMolS() {
        return o2Used;
    }

    public double co2MolS() {
        return co2Made;
    }

    /** Тепло реакций, Вт (снимается кипящим ЭДХ и паром из оксихлоратора). */
    public double heatW() {
        return heatW;
    }

    public void save(StateWriter out) {
        out.putDouble("t", oxyK);
        out.putBoolean("on", on);
    }

    public void load(StateReader in) {
        setOxyK(in.optDouble("t", 503.15));
        on = in.optInt("on", 1) != 0;
    }
}
