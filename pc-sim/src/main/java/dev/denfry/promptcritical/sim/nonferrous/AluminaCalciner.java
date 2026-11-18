package dev.denfry.promptcritical.sim.nonferrous;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Прокалка гидроксида во вращающейся печи (08 §8.8.1: {@code rotary_calciner}, 1000–1100 °C):
 * 2Al(OH)₃ → Al₂O₃ + 3H₂O. Теплота реакции с паром 1,81 МДж/кг Al₂O₃ (ΔH°f: Al(OH)₃ −1293, Al₂O₃ −1675,7,
 * H₂O(г) −241,8 кДж/моль); с явным теплом продуктов при 1050 °C — 3,1 ГДж/т глинозёма, как у печей с циклонными
 * теплообменниками (Hudson et al., «Aluminum Oxide», Ullmann's, 2005: 3,0–3,3 ГДж/т). Ниже 1000 °C глинозём не
 * доходит до металлургического (остаётся гидратированным, лишняя ППП): производительность падает линейно до нуля
 * при 500 °C, где кончается дегидратация гиббсита. Термохимия — SLOW (08 §8.0.3).
 */
public final class AluminaCalciner {
    public static final double REACTION_J_PER_KG = 1.81e6, TOTAL_J_PER_KG_1050 = 3.1e6;
    public static final double LOW_K = 1273.15, HIGH_K = 1373.15, DEAD_K = 773.15, AMBIENT_K = 298.15;
    public static final double MIN_K = 773.15, MAX_K = 1473.15;
    /** Производительность по глинозёму, кг/с физ. [BALANCE]. */
    public static final double CAPACITY_KG_S = 0.02;

    private double tempK = 1323.15;
    private double lastAl2o3KgS, lastHeatW;
    private double totalKg;

    public double tempK() {
        return tempK;
    }

    public void setTempK(double k) {
        tempK = Math.clamp(k, MIN_K, MAX_K);
    }

    /** Доля паспортной производительности при температуре печи. */
    public static double conversion(double tempK) {
        return Math.clamp((tempK - DEAD_K) / (LOW_K - DEAD_K), 0, 1);
    }

    /** Тепло на кг глинозёма при температуре печи, Дж. */
    public static double heatJPerKg(double tempK) {
        return REACTION_J_PER_KG + (TOTAL_J_PER_KG_1050 - REACTION_J_PER_KG) * (tempK - AMBIENT_K) / (1323.15 - AMBIENT_K);
    }

    /**
     * Шаг: гидроксид в печи (кг) и тепло, которое может дать топливо (Дж). Возвращает глинозём, кг; гидроксида ушло
     * {@code глинозём × }{@link BayerProcess#HYDROXIDE_PER_AL2O3}.
     */
    public double step(double dtS, double hydroxideKg, double heatAvailJ) {
        double kg = CAPACITY_KG_S * conversion(tempK) * dtS;
        kg = Math.min(kg, Math.max(0, hydroxideKg) / BayerProcess.HYDROXIDE_PER_AL2O3);
        kg = Math.min(kg, Math.max(0, heatAvailJ) / heatJPerKg(tempK));
        lastAl2o3KgS = dtS > 0 ? kg / dtS : 0;
        lastHeatW = lastAl2o3KgS * heatJPerKg(tempK);
        totalKg += kg;
        return kg;
    }

    public double lastAl2o3KgS() {
        return lastAl2o3KgS;
    }

    public double lastHeatW() {
        return lastHeatW;
    }

    public double totalKg() {
        return totalKg;
    }

    public void save(StateWriter out) {
        out.putDouble("t", tempK);
        out.putDouble("made", totalKg);
    }

    public void load(StateReader in) {
        setTempK(in.optDouble("t", 1323.15));
        totalKg = in.optDouble("made", 0);
    }
}
