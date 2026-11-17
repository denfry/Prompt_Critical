package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Одновальная газовая турбина простого цикла (ГТУ первых поколений, 1950–60-е).
 *
 * <p>Располагаемая мощность — цикл Брайтона: компрессор π = 7, η_к 0,85; температура перед турбиной 850 °C, η_т 0,87;
 * c_p воздуха 1005, газа 1148 Дж/(кг·К), k 1,4 и 1,333. Расход воздуха при постоянных оборотах — приведённый:
 * ṁ ∝ p₁/√T₁, а работа компрессора ∝ T₁, поэтому жарким летом ГТУ теряет мощность (≈ 0,7 % на кельвин), а зимой
 * набирает. КПД в расчётной точке (ISO, 15 °C) ≈ 28 %.
 *
 * <p>Динамика — упрощённая модель Rowen (1983) для ГТУ одного вала: момент, о.е., 1,3·(W_f − 0,23) + 0,5·(1 − N);
 * W_f — расход топлива, о.е. (0,23 — холостой ход на номинальных оборотах); топливная система — инерционное звено
 * {@link #FUEL_LAG_S}. Регулятор скорости со статизмом 4 %; потолок топлива — по располагаемой мощности при нынешнем
 * воздухе (регулятор температуры выхлопа). Пуск — пусковой двигатель до {@link #LIGHT_OFF} оборотов, затем зажигание.
 * Без топлива — погасание; на 110 % — автомат безопасности.
 */
public final class GasTurbine {
    public static final double PRESSURE_RATIO = 7, ETA_C = 0.85, ETA_T = 0.87, TIT_K = 1123.15;
    public static final double CP_AIR = 1005, CP_GAS = 1148, K_AIR = 1.4, K_GAS = 1.333;
    public static final double ISO_K = 288.15, ISO_PA = 101_325;
    public static final double NO_LOAD_FUEL = 0.23, DROOP = 0.04, FUEL_LAG_S = 0.4, LIGHT_OFF = 0.25, OVERSPEED = 1.10;
    /** Пусковой момент, о.е. (электродвигатель или детандер). */
    static final double STARTER_TORQUE = 0.15, H_S = 5;

    /** Удельная работа цикла, Дж/кг воздуха, при T₁; подвод тепла — в {@link #specificHeatIn}. */
    public static double specificWork(double t1K) {
        double t2 = t1K * (1 + (Math.pow(PRESSURE_RATIO, (K_AIR - 1) / K_AIR) - 1) / ETA_C);
        double wc = CP_AIR * (t2 - t1K);
        double t4 = TIT_K * (1 - ETA_T * (1 - Math.pow(PRESSURE_RATIO, -(K_GAS - 1) / K_GAS)));
        double f = CP_GAS * (TIT_K - t2) / (50e6 - CP_GAS * TIT_K); // доля топлива, Q_н метана 50 МДж/кг
        return (1 + f) * CP_GAS * (TIT_K - t4) - wc;
    }

    /** Тепло топлива на кг воздуха при T₁, Дж/кг. */
    public static double specificHeatIn(double t1K) {
        double t2 = t1K * (1 + (Math.pow(PRESSURE_RATIO, (K_AIR - 1) / K_AIR) - 1) / ETA_C);
        return CP_GAS * (TIT_K - t2);
    }

    /** КПД цикла при T₁. */
    public static double efficiency(double t1K) {
        return specificWork(t1K) / specificHeatIn(t1K);
    }

    /** Располагаемая мощность относительно ISO при воздухе T₁, p₁: приведённый расход × удельная работа. */
    public static double ambientFactor(double t1K, double p1Pa) {
        double flow = (p1Pa / ISO_PA) * Math.sqrt(ISO_K / t1K);
        return flow * specificWork(t1K) / specificWork(ISO_K);
    }

    /** Температура выхлопа при номинале, К (ISO). */
    public static double ratedExhaustK() {
        return TIT_K * (1 - ETA_T * (1 - Math.pow(PRESSURE_RATIO, -(K_GAS - 1) / K_GAS)));
    }

    private final double ratedPowerW, nominalOmega;
    private double speed, fuel;
    private final double speedRef = 1 + DROOP;
    private boolean run, lit, tripped;

    /** {@code ratedPowerW} — на валу в условиях ISO. */
    public GasTurbine(double ratedPowerW, double nominalOmega) {
        this.ratedPowerW = ratedPowerW;
        this.nominalOmega = nominalOmega;
    }

    /**
     * Шаг.
     *
     * @param loadTorque  момент нагрузки, Н·м
     * @param airK        температура наружного воздуха
     * @param airPa       давление
     * @param fuelAvailable топливо есть (иначе погасание)
     */
    public void step(double dt, double loadTorque, double airK, double airPa, boolean fuelAvailable) {
        double n = speed;
        double maxFuel = NO_LOAD_FUEL + ambientFactor(airK, airPa) / 1.3;
        if (!run || tripped || !fuelAvailable) lit = false;
        else if (!lit && n >= LIGHT_OFF) lit = true;
        // регулятор скорости: P_зад = (N_хх − N)/R, топливо по обращённому уравнению момента при N = 1
        double cmd = 0;
        if (lit) {
            double p = (speedRef - n) / DROOP;
            cmd = Math.max(0.1, Math.min(maxFuel, NO_LOAD_FUEL + p / 1.3));
        }
        fuel += (cmd - fuel) * (1 - Math.exp(-dt / FUEL_LAG_S));
        double torquePu = lit ? 1.3 * (fuel - NO_LOAD_FUEL) + 0.5 * (1 - n) : -0.05 * n * n; // без огня — сопротивление компрессора
        if (run && !tripped && n < LIGHT_OFF * 1.2) torquePu += STARTER_TORQUE;
        double ratedTorque = ratedPowerW / nominalOmega;
        double j = 2 * H_S * ratedPowerW / (nominalOmega * nominalOmega);
        double omega = n * nominalOmega + dt * (torquePu * ratedTorque - loadTorque) / j;
        speed = Math.max(0, omega / nominalOmega);
        if (speed >= OVERSPEED) tripped = true;
    }

    /** Пуск или останов (оператор). */
    public void setRun(boolean on) {
        run = on;
    }

    public boolean running() {
        return run;
    }

    public boolean lit() {
        return lit;
    }

    public boolean tripped() {
        return tripped;
    }

    /** Взвести автомат — только ниже номинальных оборотов. */
    public boolean resetTrip() {
        if (speed > 1) return false;
        tripped = false;
        return true;
    }

    public double speedPu() {
        return speed;
    }

    public double omega() {
        return speed * nominalOmega;
    }

    public double nominalOmega() {
        return nominalOmega;
    }

    public void setSpeedPu(double s) {
        speed = Math.max(0, s);
    }

    /** Расход топлива, о.е. номинального. */
    public double fuelPu() {
        return lit ? fuel : 0;
    }

    /** Тепло топлива, Вт: W_f × номинальный подвод тепла при ISO. */
    public double heatInputW() {
        return fuelPu() * ratedPowerW / efficiency(ISO_K);
    }

    /** Мощность на валу, Вт. */
    public double shaftPowerW() {
        return lit ? Math.max(0, (1.3 * (fuel - NO_LOAD_FUEL) + 0.5 * (1 - speed)) * ratedPowerW * speed) : 0;
    }

    /** Располагаемая мощность при номинальных оборотах и нынешнем воздухе, Вт. */
    public double capacityW(double airK, double airPa) {
        return run && !tripped ? ambientFactor(airK, airPa) * ratedPowerW : 0;
    }

    /** Температура выхлопа, К: Rowen, T_x = T_р − 700·(1 − W_f) + 550·(1 − N) °F, пересчитано в кельвины. */
    public double exhaustK(double airK) {
        if (!lit) return airK;
        return ratedExhaustK() - 700 * 5.0 / 9 * (1 - fuel) + 550 * 5.0 / 9 * (1 - speed);
    }

    public double ratedPowerW() {
        return ratedPowerW;
    }

    public void save(StateWriter out) {
        out.putDouble("speed", speed);
        out.putDouble("fuel", fuel);
        out.putBoolean("run", run);
        out.putBoolean("lit", lit);
        out.putBoolean("tripped", tripped);
    }

    public void load(StateReader in) {
        speed = in.getDouble("speed");
        fuel = in.getDouble("fuel");
        run = in.getBoolean("run");
        lit = in.getBoolean("lit");
        tripped = in.getBoolean("tripped");
    }
}
