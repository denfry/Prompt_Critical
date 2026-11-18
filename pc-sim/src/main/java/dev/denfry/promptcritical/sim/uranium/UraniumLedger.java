package dev.denfry.promptcritical.sim.uranium;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Сквозной учёт урана на стадии передела: сколько килограммов U пришло, ушло дальше продуктом и потеряно (хвосты,
 * маточники, пыль, утечки). Разность — запас в аппарате. Учёт ведётся в килограммах элемента, а не соединения, — как в
 * балансе ядерных материалов (Benedict, Pigford, Levi, «Nuclear Chemical Engineering», 2nd ed., 1981, гл. 5).
 */
public final class UraniumLedger {
    public static final double M_U = 238.029, M_U3O8 = 842.08, M_UO2 = 270.03, M_UO3 = 286.03, M_UF4 = 314.02, M_UF6 = 352.02;

    private double inKg, outKg, lossKg;

    public void in(double kgU) {
        inKg += Math.max(0, kgU);
    }

    public void out(double kgU) {
        outKg += Math.max(0, kgU);
    }

    public void loss(double kgU) {
        lossKg += Math.max(0, kgU);
    }

    public double inKg() {
        return inKg;
    }

    public double outKg() {
        return outKg;
    }

    public double lossKg() {
        return lossKg;
    }

    /** Уран в аппарате (не вышедший ни продуктом, ни потерей), кг. */
    public double holdupKg() {
        return inKg - outKg - lossKg;
    }

    /** Извлечение стадии: продукт к сумме продукта и потерь. */
    public double recovery() {
        double done = outKg + lossKg;
        return done > 0 ? outKg / done : 0;
    }

    public void save(StateWriter out) {
        out.putDouble("u_in", inKg);
        out.putDouble("u_out", outKg);
        out.putDouble("u_loss", lossKg);
    }

    public void load(StateReader in) {
        inKg = in.optDouble("u_in", 0);
        outKg = in.optDouble("u_out", 0);
        lossKg = in.optDouble("u_loss", 0);
    }
}
