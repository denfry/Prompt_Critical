package dev.denfry.promptcritical.sim.metallurgy;

import static dev.denfry.promptcritical.sim.metallurgy.BlastFurnace.M_C;
import static dev.denfry.promptcritical.sim.metallurgy.BlastFurnace.M_FE;
import static dev.denfry.promptcritical.sim.metallurgy.BlastFurnace.M_H2;
import static dev.denfry.promptcritical.sim.metallurgy.BlastFurnace.M_H2O;
import static dev.denfry.promptcritical.sim.metallurgy.BlastFurnace.M_MN;
import static dev.denfry.promptcritical.sim.metallurgy.BlastFurnace.M_SI;
import static dev.denfry.promptcritical.sim.metallurgy.BlastFurnace.VM;

import dev.denfry.promptcritical.sim.mining.CoalAssay;

/**
 * Статический баланс углерода и газа домны на тонну чугуна (08 §8.5.3, тест T8.9) — для справочника, пульта и
 * проверки динамической модели {@link BlastFurnace}. Все массы — кг/т, объёмы — нм³/т.
 */
public record CarbonBalance(double cokeKg, double tuyereC, double directC, double hotMetalC, double siMnC,
        double dustC, double burdenKg, double n2Nm3, double coNm3, double co2Nm3, double h2Nm3) {
    /** Чугун: C 4,5, Si 0,6, Mn 0,3, S 0,03 %. */
    public static final double HM_C = 0.045, HM_SI = 0.006, HM_MN = 0.003, HM_S = 0.0003;

    /**
     * @param oreFe        Fe агломерата
     * @param blastNm3PerT удельное дутьё, нм³/т
     * @param rd           степень прямого восстановления
     * @param oxygenFrac   O₂ дутья
     * @param moistureKgNm3 влага дутья
     * @param etaCo        η_CO
     * @param coke         анализ кокса
     */
    public static CarbonBalance of(double oreFe, double blastNm3PerT, double rd, double oxygenFrac,
            double moistureKgNm3, double etaCo, CoalAssay coke) {
        double feKg = 1000 * (1 - HM_C - HM_SI - HM_MN - HM_S);
        double cf = (2 * oxygenFrac / VM * M_C + moistureKgNm3 / M_H2O * M_C) * blastNm3PerT;
        double cd = rd * feKg * M_C / M_FE;
        double chm = 1000 * HM_C;
        double csi = 1000 * (HM_SI * 2 * M_C / M_SI + HM_MN * M_C / M_MN);
        double cokeC = (cf + cd + chm + csi) / (1 - BlastFurnace.DUST_LOSS);
        double cokeKg = cokeC / (coke.cDaf() * coke.dafWorking());
        double gasC = (cf + cd + csi) / M_C * VM;
        double h2 = moistureKgNm3 * blastNm3PerT / M_H2O * VM + cokeKg * coke.hDaf() * coke.dafWorking() / M_H2 * VM;
        return new CarbonBalance(cokeKg, cf, cd, chm, csi, cokeC * BlastFurnace.DUST_LOSS,
                feKg / BlastFurnace.FE_RECOVERY / oreFe, (1 - oxygenFrac) * blastNm3PerT, gasC * (1 - etaCo),
                gasC * etaCo, h2);
    }

    public double topGasNm3() {
        return n2Nm3 + coNm3 + co2Nm3 + h2Nm3;
    }

    public double coFraction() {
        return coNm3 / topGasNm3();
    }
}
