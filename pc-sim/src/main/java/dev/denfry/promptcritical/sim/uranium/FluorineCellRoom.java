package dev.denfry.promptcritical.sim.uranium;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Среднетемпературные фторные электролизёры: расплав KF·2HF при 80–100 °C, угольные аноды, стальные катоды, юбка
 * разделяет фтор и водород (Benedict–Pigford–Levi, гл. 5.8; Rudge, «The Manufacture and Use of Fluorine and its
 * Compounds», 1962; Groult et al., J. Fluorine Chem. 128 (2007) 285). 2 HF → H₂ + F₂, HF подают в расплав, чтобы держать
 * его состав 40–42 % HF.
 *
 * <p>Напряжение ванны — 8–11 В при 1–1,5 кА/м²: напряжение разложения HF ≈ 2,9 В, перенапряжение фтора на угле велико
 * (тонкая плёнка фторграфита (CF)ₓ на аноде), омика расплава и пузырей. Выход по току 90–95 %; расход ≈ 13–15 кВт·ч
 * постоянного тока на кг F₂. Если HF в расплаве меньше ≈ 37 %, плёнка (CF)ₓ перестаёт смачиваться — анодный эффект:
 * напряжение подскакивает на 20 В и больше, ток проваливается, фтор почти не идёт, пока не добавят HF.
 */
public final class FluorineCellRoom {
    public static final int CELLS = 12;
    public static final double ANODE_M2 = 5, MIN_KA = 1, MAX_KA = 8, NOMINAL_KA = 6;
    public static final double F = 96485.33, M_F2 = 0.037997, M_H2 = 0.002016, M_HF = 0.020006, M_KF = 0.058097;
    public static final double E_DECOMP = 2.9, ANODE_B = 0.5, ANODE_I0 = 10, OHMIC_OHM_M2 = 3.5e-3, ANODE_EFFECT_V = 22;
    /** Расплав одной ванны: KF, кг (≈ 1 т электролита на ванну) [BALANCE]. */
    public static final double BATH_KF_KG = 600;
    /** HF в расплаве: норма (KF·2HF — 40,8 %) и анодный эффект. */
    public static final double HF_NOMINAL = 2 * M_HF / (2 * M_HF + M_KF), HF_ANODE_EFFECT = 0.37, HF_MAX = 0.45;

    private boolean on;
    private double currentKa = NOMINAL_KA, bathHfKg = BATH_KF_KG * HF_NOMINAL / (1 - HF_NOMINAL) * CELLS;
    private double voltageV, efficiency, f2KgS, h2KgS, hfUseKgS, dcW;
    private boolean anodeEffect;

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    public double currentKa() {
        return currentKa;
    }

    public void setCurrentKa(double ka) {
        currentKa = Math.clamp(ka, MIN_KA, MAX_KA);
    }

    /** Массовая доля HF в расплаве. */
    public double hfFraction() {
        double kf = BATH_KF_KG * CELLS;
        return bathHfKg / (bathHfKg + kf);
    }

    /** HF, который расплав примет до верхней границы, кг. */
    public double hfRoomKg() {
        double kf = BATH_KF_KG * CELLS;
        return Math.max(0, HF_MAX / (1 - HF_MAX) * kf - bathHfKg);
    }

    public void addHf(double kg) {
        bathHfKg += Math.clamp(kg, 0, hfRoomKg());
    }

    public boolean anodeEffect() {
        return anodeEffect;
    }

    /** Напряжение ванны без анодного эффекта, В. */
    public static double cellVoltage(double ampsPerM2) {
        if (ampsPerM2 <= 0) return E_DECOMP;
        return E_DECOMP + ANODE_B * Math.log(Math.max(1, ampsPerM2 / ANODE_I0)) + OHMIC_OHM_M2 * ampsPerM2;
    }

    /** Удельный расход постоянного тока, Дж на кг F₂. */
    public static double specificEnergyJPerKg(double voltageV, double efficiency) {
        return voltageV * 2 * F / (M_F2 * efficiency);
    }

    /** Мощность постоянного тока при токе ванны {@code amps}, Вт. */
    public double dcDemandW(double amps) {
        double u = cellVoltage(amps / ANODE_M2) + (hfFraction() < HF_ANODE_EFFECT ? ANODE_EFFECT_V : 0);
        return amps * CELLS * u;
    }

    /** Шаг: ток от выпрямителя, А. */
    public void step(double dtS, double dcAmpsLimit) {
        double amps = on ? Math.min(currentKa * 1000, Math.max(0, dcAmpsLimit)) : 0;
        anodeEffect = amps > 0 && hfFraction() < HF_ANODE_EFFECT;
        if (anodeEffect) amps *= 0.15; // плёнка на аноде: ток проваливается
        voltageV = cellVoltage(amps / ANODE_M2) + (anodeEffect ? ANODE_EFFECT_V : 0);
        efficiency = amps > 0 ? (anodeEffect ? 0.3 : 0.93) : 0;
        double cellsAmps = amps * CELLS;
        double f2Mol = cellsAmps * efficiency / (2 * F);
        f2KgS = f2Mol * M_F2;
        h2KgS = f2Mol * M_H2;
        hfUseKgS = 2 * f2Mol * M_HF;
        bathHfKg = Math.max(0, bathHfKg - hfUseKgS * dtS);
        dcW = cellsAmps * voltageV;
    }

    public double voltageV() {
        return voltageV;
    }

    public double efficiency() {
        return efficiency;
    }

    public double f2KgS() {
        return f2KgS;
    }

    public double h2KgS() {
        return h2KgS;
    }

    public double hfUseKgS() {
        return hfUseKgS;
    }

    public double dcW() {
        return dcW;
    }

    public void save(StateWriter out) {
        out.putBoolean("on", on);
        out.putDouble("ka", currentKa);
        out.putDouble("hf", bathHfKg);
    }

    public void load(StateReader in) {
        on = in.optInt("on", 0) != 0;
        setCurrentKa(in.optDouble("ka", NOMINAL_KA));
        bathHfKg = in.optDouble("hf", BATH_KF_KG * HF_NOMINAL / (1 - HF_NOMINAL) * CELLS);
    }
}
