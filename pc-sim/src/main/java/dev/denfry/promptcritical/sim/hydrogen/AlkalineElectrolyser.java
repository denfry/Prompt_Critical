package dev.denfry.promptcritical.sim.hydrogen;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Атмосферный щелочной электролизёр класса Nel A485 (17 §17.3.8, энергопроект «водород»): фильтр-прессная батарея
 * из {@value #CELLS} ячеек по {@value #CELL_M2} м² на одном выпрямителе, электролит — 30 % KOH при 80 °C. Номинал
 * 4 кА/м² — около 485 Нм³/ч водорода при 2,2 МВт постоянного тока.
 *
 * <p>Напряжение ячейки — модель Ulleberg (Int. J. Hydrogen Energy 28 (2003) 21–33) для щелочного электролизёра:
 * U = U_rev(T) + (r₁ + r₂T)·i·k(w) + s·lg((t₁ + t₂/T + t₃/T²)·i + 1), i — А/м², T — °C; U_rev = 1,229 В − 0,9 мВ/К.
 * Множитель k(w) учитывает электропроводность щёлочи: у KOH максимум около 30 %, при 20 и 40 % сопротивление выше на
 * 15 % (Gilliam 2007). Выход по току (Фарадея) η_F = f₂·i²/(f₁ + i²), i — мА/см²: на малой нагрузке ток утечки по
 * общему коллектору щёлочи съедает долю тока.
 *
 * <p>Водород в кислороде (HTO): водород диффундирует через диафрагму с почти постоянным потоком, а кислорода тем
 * меньше, чем меньше ток. На номинале HTO ≈ 0,3 %, у 15 % нагрузки — 2 %: здесь защита отключает батарею, как на
 * настоящих установках (НКПР водорода в кислороде — 4 %, отключение на половине). После отключения по HTO батарею
 * продувают азотом. Отсюда нижний предел нагрузки щелочных электролизёров: 15–25 % (Schalenbach 2016).
 *
 * <p>Тепло: всё, что выше термонейтральных 1,48 В, греет щёлочь; охладитель щёлочи держит 80 °C, без воды
 * охлаждения батарея за четверть часа доходит до отключения по 90 °C. Вода разлагается — 8,94 кг на кг водорода,
 * щёлочь крепчает, подпитка обессоленной водой возвращает концентрацию.
 */
public final class AlkalineElectrolyser {
    public static final int CELLS = 230;
    public static final double CELL_M2 = 1.31;
    /** Номинальная плотность тока, А/м². */
    public static final double NOMINAL_A_M2 = 4000;
    public static final double NOMINAL_A = NOMINAL_A_M2 * CELL_M2;
    public static final double THERMONEUTRAL_V = 1.481;
    /** Коэффициенты Ulleberg 2003, табл. 1. */
    static final double R1 = 8.05e-5, R2 = -2.5e-7, S = 0.185, T1 = -0.1002, T2 = 8.424, T3 = 247.3;
    static final double F1 = 250, F2 = 0.96;
    /** Поток водорода через диафрагму, моль/(с·м²) ячейки: HTO 0,3 % на номинале. */
    static final double CROSSOVER_MOL_M2 = 3.1e-5;
    public static final double HTO_ALARM = 0.01, HTO_TRIP = 0.02;
    public static final double SETPOINT_K = 353.15, TRIP_K = 363.15;
    /** Наибольшее давление в коллекторе водорода: газгольдер выше — отключение, Па абс. */
    public static final double MAX_OUTLET_PA = 1.30e5;
    /** Масса KOH в контуре, кг, и вода при 30 %. */
    public static final double KOH_KG = 2400, WATER_NOMINAL_KG = KOH_KG * 0.7 / 0.3;
    public static final double LYE_MIN = 0.20, LYE_MAX = 0.40;
    /** Теплоёмкость батареи со щёлочью, Дж/К; охладитель щёлочи, Вт; потери в цех, Вт/К. */
    static final double HEAT_CAPACITY = 4.0e7, COOLER_MAX_W = 600e3, LOSS_W_K = 500;
    /** Постоянная выравнивания HTO в сепараторе, с; продувка азотом, с. */
    static final double HTO_TAU_S = 60, PURGE_S = 120;

    public enum Trip { NONE, HTO, OVERTEMP, OUTLET_PRESSURE, LYE }

    private boolean on;
    private double load = 1;
    private double tempK = 293.15, waterKg = WATER_NOMINAL_KG, hto;
    private Trip trip = Trip.NONE;
    private double purgeLeftS;
    private double amps, voltage, faraday, h2MolS, o2MolS, dcW, waterUseKgS, coolerW;

    public boolean on() {
        return on;
    }

    /** Пуск и останов; пуск после отключения — только после продувки ({@link #purge()}). */
    public void setOn(boolean on) {
        this.on = on && trip == Trip.NONE;
    }

    /** Задание нагрузки, доля номинального тока 0…1. */
    public void setLoad(double fraction) {
        load = Math.max(0, Math.min(1, fraction));
    }

    public double load() {
        return load;
    }

    public Trip trip() {
        return trip;
    }

    public boolean purging() {
        return purgeLeftS > 0;
    }

    /** Продувка азотом: через {@value #PURGE_S} с сбрасывает HTO и отключение (если его причина ушла). */
    public void purge() {
        if (!on) purgeLeftS = PURGE_S;
    }

    public static double reversibleV(double tempK) {
        return 1.229 - 0.9e-3 * (tempK - 298.15);
    }

    /** Сопротивление щёлочи относительно 30 % KOH. */
    public static double lyeFactor(double kohFraction) {
        double d = kohFraction - 0.30;
        return 1 + 15 * d * d;
    }

    /** Напряжение ячейки при плотности тока {@code aM2}, А/м². */
    public static double cellVoltage(double aM2, double tempK, double kohFraction) {
        double tc = Math.max(10, tempK - 273.15);
        double ohm = (R1 + R2 * tc) * aM2 * lyeFactor(kohFraction);
        double t = T1 + T2 / tc + T3 / (tc * tc);
        return reversibleV(tempK) + ohm + S * Math.log10(Math.max(0, t) * aM2 + 1);
    }

    public static double faradayEfficiency(double aM2) {
        double i = aM2 / 10; // мА/см²
        return i <= 0 ? 0 : F2 * i * i / (F1 + i * i);
    }

    /** Мощность постоянного тока батареи при токе {@code a}, Вт. */
    public double dcDemandW(double a) {
        return a * CELLS * cellVoltage(a / CELL_M2, tempK, kohFraction());
    }

    /** Номинальная мощность при нынешней температуре и щёлочи, Вт. */
    public double ratedDcW() {
        return dcDemandW(NOMINAL_A * load);
    }

    public double kohFraction() {
        return KOH_KG / (KOH_KG + Math.max(1, waterKg));
    }

    /**
     * Шаг.
     *
     * @param dtS        шаг, с
     * @param dcAvailW   мощность, которую может дать выпрямитель, Вт
     * @param makeupKg   обессоленная вода подпитки, доступная на шаге, кг
     * @param cooling    есть ли вода охлаждения на охладителе щёлочи
     * @param outletPa   давление в коллекторе водорода (газгольдер), Па абс.
     * @param ambientK   температура в цехе, K
     * @return вода подпитки, взятая на шаге, кг
     */
    public double step(double dtS, double dcAvailW, double makeupKg, boolean cooling, double outletPa, double ambientK) {
        if (purgeLeftS > 0) {
            purgeLeftS = Math.max(0, purgeLeftS - dtS);
            hto *= Math.exp(-dtS / 20);
            if (purgeLeftS == 0) {
                hto = 0;
                trip = Trip.NONE;
            }
        }
        double target = on && trip == Trip.NONE ? NOMINAL_A * load : 0;
        amps = target > 0 ? Math.min(target, ampsFor(Math.max(0, dcAvailW), target)) : 0;
        double aM2 = amps / CELL_M2;
        voltage = amps > 0 ? cellVoltage(aM2, tempK, kohFraction()) : 0;
        faraday = faradayEfficiency(aM2);
        double cellAmps = amps * CELLS;
        h2MolS = cellAmps * faraday / (2 * HydrogenGas.F);
        o2MolS = h2MolS / 2;
        dcW = cellAmps * voltage;
        waterUseKgS = h2MolS * HydrogenGas.M_H2O;
        // HTO: поток через диафрагму растёт с температурой, кислород — с током; без тока газ стоит в сепараторе
        if (amps > 0) {
            double crossover = CROSSOVER_MOL_M2 * CELL_M2 * CELLS * (0.5 + 0.5 * tempK / SETPOINT_K);
            double steady = crossover / (o2MolS + crossover);
            hto += (steady - hto) * Math.min(1, dtS / HTO_TAU_S);
        }
        // вода: разложение и подпитка до 30 %
        waterKg -= waterUseKgS * dtS;
        double want = Math.max(0, WATER_NOMINAL_KG - waterKg);
        double taken = Math.min(want, Math.max(0, makeupKg));
        waterKg += taken;
        // тепло
        double heatW = Math.max(0, voltage - THERMONEUTRAL_V) * cellAmps;
        double lossW = LOSS_W_K * (tempK - ambientK);
        coolerW = cooling ? Math.max(0, Math.min(COOLER_MAX_W, heatW - lossW + (tempK - SETPOINT_K) * HEAT_CAPACITY / 600)) : 0;
        tempK += (heatW - lossW - coolerW) * dtS / HEAT_CAPACITY;
        if (on && trip == Trip.NONE) {
            if (hto >= HTO_TRIP) trip(Trip.HTO);
            else if (tempK >= TRIP_K) trip(Trip.OVERTEMP);
            else if (outletPa > MAX_OUTLET_PA) trip(Trip.OUTLET_PRESSURE);
            else if (kohFraction() > LYE_MAX) trip(Trip.LYE);
        }
        return taken;
    }

    private void trip(Trip t) {
        trip = t;
        on = false;
    }

    /** Ток, который выпрямитель вытянет при мощности {@code w}: бисекция по монотонной U·I. */
    private double ampsFor(double w, double max) {
        if (dcDemandW(max) <= w) return max;
        double lo = 0, hi = max;
        for (int k = 0; k < 30; k++) {
            double mid = (lo + hi) / 2;
            if (dcDemandW(mid) <= w) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    public double amps() {
        return amps;
    }

    public double cellVoltage() {
        return voltage;
    }

    public double stackVoltage() {
        return voltage * CELLS;
    }

    public double faraday() {
        return faraday;
    }

    public double h2MolS() {
        return h2MolS;
    }

    public double o2MolS() {
        return o2MolS;
    }

    public double h2Nm3h() {
        return HydrogenGas.nm3(h2MolS) * 3600;
    }

    public double dcW() {
        return dcW;
    }

    /** Удельный расход постоянного тока, кВт·ч/Нм³ (0 — стоит). */
    public double specificKwhPerNm3() {
        return h2MolS > 0 ? dcW / HydrogenGas.nm3(h2MolS) / 3.6e6 : 0;
    }

    public double waterUseKgS() {
        return waterUseKgS;
    }

    public double tempK() {
        return tempK;
    }

    /** Доля водорода в кислороде, объёмная. */
    public double hto() {
        return hto;
    }

    public double coolerW() {
        return coolerW;
    }

    public void save(StateWriter out) {
        out.putBoolean("on", on);
        out.putDouble("load", load);
        out.putDouble("t", tempK);
        out.putDouble("water", waterKg);
        out.putDouble("hto", hto);
        out.putInt("trip", trip.ordinal());
        out.putDouble("purge", purgeLeftS);
    }

    public void load(StateReader in) {
        on = in.optInt("on", 0) != 0;
        load = in.optDouble("load", 1);
        tempK = in.optDouble("t", 293.15);
        waterKg = in.optDouble("water", WATER_NOMINAL_KG);
        hto = in.optDouble("hto", 0);
        int t = in.optInt("trip", 0);
        trip = t >= 0 && t < Trip.values().length ? Trip.values()[t] : Trip.NONE;
        purgeLeftS = in.optDouble("purge", 0);
    }
}
