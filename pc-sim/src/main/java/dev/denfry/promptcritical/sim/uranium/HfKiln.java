package dev.denfry.promptcritical.sim.uranium;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Фтористый водород из плавикового шпата: CaF₂ + H₂SO₄ → CaSO₄ + 2 HF во вращающейся печи с внешним обогревом при
 * 200–250 °C, кислота с избытком 5–10 % (Ullmann, «Hydrogen Fluoride», разд. 4; Galkin, 1964). Выход HF по фтору
 * 95–98 %; ниже 150 °C реакция затягивается, выше 300 °C пастообразная масса спекается на стенках. Газ конденсируют
 * в безводный HF (кипит при 19,5 °C).
 */
public final class HfKiln {
    public static final double M_CAF2 = 78.075, M_HF = 20.006, M_H2SO4 = 98.079, ACID_EXCESS = 1.07;
    public static final double MIN_K = 393.15, MAX_K = 623.15;
    /** Шпат, кг CaF₂/с [BALANCE]. */
    public static final double CAPACITY_CAF2_KG_S = 0.05;
    /** Нагрев шихты и теплота реакции (+59 кДж/моль), Дж на кг CaF₂. */
    public static final double HEAT_J_PER_KG = 1.6e6;

    private double tempK = 503.15;
    private boolean on = true;
    private double hfKgS, caf2KgS, acidKgS;

    public double tempK() {
        return tempK;
    }

    public void setTempK(double k) {
        tempK = Math.clamp(k, MIN_K, MAX_K);
    }

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    /** Выход HF по фтору при температуре. */
    public static double hfYield(double tempK) {
        if (tempK < 423.15) return Math.max(0, 0.97 * (tempK - 393.15) / 30);
        if (tempK > 573.15) return Math.max(0.5, 0.97 - (tempK - 573.15) / 50 * 0.47);
        return 0.97;
    }

    /** Кислота на кг CaF₂ с избытком, кг. */
    public static double acidPerCaf2() {
        return M_H2SO4 / M_CAF2 * ACID_EXCESS;
    }

    /** Шаг: шпат, доступный на шаг, кг/с (кислоту уже проверил вызывающий). */
    public void step(double caf2AvailKgS) {
        caf2KgS = on ? Math.clamp(caf2AvailKgS, 0, CAPACITY_CAF2_KG_S) : 0;
        acidKgS = caf2KgS * acidPerCaf2();
        hfKgS = caf2KgS * 2 * M_HF / M_CAF2 * hfYield(tempK);
    }

    public double hfKgS() {
        return hfKgS;
    }

    public double caf2KgS() {
        return caf2KgS;
    }

    public double acidKgS() {
        return acidKgS;
    }

    public double heatW() {
        return caf2KgS * HEAT_J_PER_KG;
    }

    public void save(StateWriter out) {
        out.putDouble("t", tempK);
        out.putBoolean("on", on);
    }

    public void load(StateReader in) {
        setTempK(in.optDouble("t", 503.15));
        on = in.optInt("on", 1) != 0;
    }
}
