package dev.denfry.promptcritical.sim.chlor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Печь пиролиза дихлорэтана: C₂H₄Cl₂ → C₂H₃Cl + HCl, ΔH = +71 кДж/моль, в трубах печи при 480–530 °C и 2–3 МПа,
 * время пребывания 10–30 с. Превращение за проход держат 50–60 %: выше растут кокс, ацетилен, бутадиен и хлоропрен
 * (Ullmann, «Vinyl Chloride», разд. 4.2; Borsa, Herrera, Felder, Ind. Eng. Chem. Res. 40 (2001) 2428). Кинетика —
 * первый порядок, E = 200 кДж/моль, предэкспонента подобрана на 55 % при 500 °C и 15 с [калибровка]. После закалки
 * HCl отгоняют сверху колонны, винилхлорид (ВХ) — во второй колонне; непрореагировавший ЭДХ возвращается в печь.
 */
public final class EdcCracker {
    public static final double E_J_MOL = 200e3, R = 8.314462618, RESIDENCE_S = 15;
    public static final double A_PER_S = -Math.log(0.45) / RESIDENCE_S * Math.exp(E_J_MOL / (R * 773.15));
    public static final double MIN_K = 733.15, MAX_K = 823.15, HEAT_J_MOL = 71e3, M_VCM = 0.062498;
    /** Нагрев ЭДХ до температуры крекинга и испарение, Дж/моль (cp ≈ 130 Дж/(моль·К), теплота испарения 32 кДж/моль). */
    public static final double SENSIBLE_J_MOL = 130 * 480 + 32e3;
    /** КПД печи по топливу. */
    public static final double FURNACE_EFF = 0.85;
    /** Кокс в змеевике: прирост на моль крекированного ЭДХ при превращении 55 %, мм [BALANCE: пробег 3–6 месяцев]. */
    public static final double COKE_MM_PER_KMOL = 0.0005;
    public static final double COKE_LIMIT_MM = 6;

    private double cotK = 773.15;
    private boolean on;
    private double cokeMm;
    private double conversion, vcm, hcl, crackedEdc, fuelW, byproduct;

    public double cotK() {
        return cotK;
    }

    public void setCotK(double k) {
        cotK = Math.clamp(k, MIN_K, MAX_K);
    }

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    public double cokeMm() {
        return cokeMm;
    }

    public void decoke() {
        cokeMm = 0;
    }

    /** Превращение за проход при температуре выхода. */
    public static double conversion(double cotK) {
        return 1 - Math.exp(-A_PER_S * Math.exp(-E_J_MOL / (R * cotK)) * RESIDENCE_S);
    }

    /** Селективность по ВХ: побочные продукты растут с превращением выше 50 %. */
    public static double selectivity(double conversion) {
        return Math.clamp(0.995 - 0.10 * Math.max(0, conversion - 0.5), 0.90, 0.995);
    }

    /**
     * Шаг.
     *
     * @param edcMolS ЭДХ в змеевик (свежий плюс рецикл), моль/с
     */
    public void step(double dtS, double edcMolS) {
        conversion = vcm = hcl = crackedEdc = fuelW = byproduct = 0;
        if (!on || edcMolS <= 0 || cokeMm >= COKE_LIMIT_MM) return;
        // кокс сужает трубы: время пребывания и теплопередача падают
        double x = conversion(cotK) * (1 - 0.05 * cokeMm / COKE_LIMIT_MM);
        double sel = selectivity(x);
        conversion = x;
        crackedEdc = edcMolS * x;
        vcm = crackedEdc * sel;
        hcl = crackedEdc; // HCl выделяется и при побочных реакциях
        byproduct = crackedEdc - vcm;
        fuelW = (edcMolS * SENSIBLE_J_MOL + crackedEdc * HEAT_J_MOL) / FURNACE_EFF;
        cokeMm += crackedEdc * dtS / 1000 * COKE_MM_PER_KMOL * Math.exp(Math.max(0, x - 0.55) * 8);
    }

    public double conversion() {
        return conversion;
    }

    public double vcmMolS() {
        return vcm;
    }

    public double hclMolS() {
        return hcl;
    }

    /** Крекированный ЭДХ, моль/с; остаток подачи возвращается рециклом. */
    public double crackedMolS() {
        return crackedEdc;
    }

    /** Побочные продукты (тяжёлые хлорорганика, кокс), моль/с. */
    public double byproductMolS() {
        return byproduct;
    }

    public double fuelW() {
        return fuelW;
    }

    public void save(StateWriter out) {
        out.putDouble("t", cotK);
        out.putBoolean("on", on);
        out.putDouble("coke", cokeMm);
    }

    public void load(StateReader in) {
        setCotK(in.optDouble("t", 773.15));
        on = in.optInt("on", 0) != 0;
        cokeMm = in.optDouble("coke", 0);
    }
}
