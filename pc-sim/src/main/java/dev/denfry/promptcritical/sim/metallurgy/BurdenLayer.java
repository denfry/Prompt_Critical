package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.mining.CoalAssay;

/**
 * Колоша (слой шихты) домны (08 §8.5.3): железосодержащая часть (агломерат, руда, концентрат), кокс и флюс.
 *
 * @param oreKg       агломерат или руда, кг
 * @param oreFe       Fe, доля
 * @param oreSiO2     SiO₂, доля
 * @param oreCaO      CaO, доля
 * @param cokeKg      кокс, кг
 * @param coke        анализ кокса (зола, сера, M₂₅); {@code null} только без кокса
 * @param limestoneKg известняк, кг
 */
public record BurdenLayer(double oreKg, double oreFe, double oreSiO2, double oreCaO, double cokeKg, CoalAssay coke,
        double limestoneKg) {
    public BurdenLayer {
        if (!(oreKg >= 0 && cokeKg >= 0 && limestoneKg >= 0 && oreKg + cokeKg + limestoneKg > 0))
            throw new IllegalArgumentException("burden masses");
        if (!(oreFe >= 0 && oreSiO2 >= 0 && oreCaO >= 0 && oreFe + oreSiO2 + oreCaO <= 1 + 1e-9))
            throw new IllegalArgumentException("ore chemistry");
        if (cokeKg > 0 && coke == null) throw new IllegalArgumentException("coke assay");
    }

    /** Холостая колоша (только кокс) — задувка, промывка горна. */
    public static BurdenLayer cokeBlank(double cokeKg, CoalAssay coke) {
        return new BurdenLayer(0, 0, 0, 0, cokeKg, coke, 0);
    }

    /** Колоша из агломерата и кокса без флюса. */
    public static BurdenLayer of(OreDressing.Sinter sinter, double cokeKg, CoalAssay coke) {
        return new BurdenLayer(sinter.sinterKg(), sinter.feFrac(), sinter.sio2Frac(), sinter.caoFrac(), cokeKg, coke, 0);
    }

    /** Насыпной объём, м³: агломерат 1,8, кокс 0,5, известняк 1,5 т/м³. */
    public double volumeM3() {
        return oreKg / 1800 + cokeKg / 500 + limestoneKg / 1500;
    }
}
