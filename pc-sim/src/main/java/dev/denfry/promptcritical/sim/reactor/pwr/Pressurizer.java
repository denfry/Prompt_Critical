package dev.denfry.promptcritical.sim.reactor.pwr;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Компенсатор давления (КД) — узел {@code TWO_REGION} (01 §1.3.8): жидкость (M_l, U_l) и паровая подушка (M_v, U_v) с общим
 * давлением в постоянном объёме V. Давление — корень M_l·v_l(P, u_l) + M_v·v_v(P, u_v) = V; между областями —
 * работа сжатия P·ΔV, поэтому входящий скачок (insurge) сжимает подушку почти адиабатически и давление растёт быстрее,
 * чем при равновесном смешении. Межфазный обмен — релаксацией к насыщению: вскипание перегретой жидкости
 * (τ_flash = 0,2 с, опыты Edwards–O'Brien), конденсация влаги подушки (τ_cond = 1 с [оценка: время выпадения капель])
 * и конденсация на струях впрыска с эффективностью η = 0,9 (01 §1.3.8).
 *
 * <p>ТЭНы греют жидкость; впрыск — холодная вода из напорной нитки ГЦН; импульсное предохранительное устройство (ИПУ)
 * и предохранительный клапан (ПК) сбрасывают пар в барботёр. Расслоение жидкости не моделируется.
 *
 * <p><b>Газовый КД</b> ({@link #nitrogen}) — компенсатор объёма ВВЭР-70 (В-2, АЭС Райнсберг): над водой азотная подушка,
 * ТЭНов и впрыска нет. Давление задаёт газ: P = m_N₂·R·T/V_газ, газ изотермичен с водой под ним; вода не кипит
 * (подушка держит её недогретой), её объём — m·v(P, h). Входящий скачок сжимает газ, и давление растёт по объёму,
 * а не по теплу; ИПУ и ПК сбрасывают азот, и давление после сброса восстанавливают только баллоны
 * ({@link #addNitrogenKg}).
 */
public final class Pressurizer {
    public static final double TAU_FLASH_S = 0.2, TAU_COND_S = 1.0, SPRAY_EFFICIENCY = 0.9;
    private static final double P_MIN = 0.12e6, P_MAX = 24e6;

    /** Газовая постоянная азота, Дж/(кг·К). */
    public static final double R_N2 = 8.314_462 / 0.028_014;
    /** Наименьший объём подушки — доля объёма КД (дальше «твёрдый» КД, давление растёт на сжатии воды). */
    static final double MIN_GAS_FRACTION = 0.005;

    private final WaterProperties water;
    private final double volume;
    private final boolean gas;
    /** Газовый КД: масса азота, кг, и энтальпия воды, Дж/кг. */
    private double n2Kg, hGas;
    private double ml, ul, mv, uv, pressure, liquidVolume;
    private boolean reliefOpen, safetyOpen;
    private double relievedKg, lastReliefKgS;
    private final double[] satBuf = new double[17];
    private final SaturationState sat = new SaturationState();
    private final ThermoState ts = new ThermoState();

    public Pressurizer(WaterProperties water, double volumeM3) {
        this(water, volumeM3, false);
    }

    private Pressurizer(WaterProperties water, double volumeM3, boolean gas) {
        this.water = water;
        this.volume = volumeM3;
        this.gas = gas;
    }

    /** Газовый компенсатор объёма с азотной подушкой (без ТЭНов и впрыска). */
    public static Pressurizer nitrogen(WaterProperties water, double volumeM3) {
        return new Pressurizer(water, volumeM3, true);
    }

    public boolean gas() {
        return gas;
    }

    /** Азот в подушке, кг (у парового КД — 0). */
    public double nitrogenKg() {
        return n2Kg;
    }

    /** Подкачка азота из баллонов (плюс) или сдувка (минус), кг; у парового КД ничего не делает. */
    public void addNitrogenKg(double kg) {
        if (!gas || !Double.isFinite(kg) || kg == 0) return;
        n2Kg = Math.max(0.05 * n2Kg, n2Kg + kg);
        pressure = gasPressure(pressure);
    }

    /**
     * Газовый КД: вода с энтальпией {@code liquidH} до уровня {@code level}, азота столько, чтобы при температуре воды
     * давление было {@code p}. Паровой КД — как {@link #initialize(double, double)}.
     */
    public void initialize(double p, double level, double liquidH) {
        if (!gas) {
            initialize(p, level);
            return;
        }
        double vl = volume * Math.max(0, Math.min(1 - MIN_GAS_FRACTION, level));
        water.stateFromPressureEnthalpy(p, liquidH, ts);
        hGas = liquidH;
        ml = vl * ts.density;
        ul = ml * liquidH;
        mv = uv = 0;
        n2Kg = p * (volume - vl) / (R_N2 * ts.temperature);
        pressure = p;
        liquidVolume = vl;
        reliefOpen = safetyOpen = false;
    }

    /** Давление газового КД при текущих массах: неподвижная точка по плотности воды. */
    private double gasPressure(double guess) {
        double p = Math.max(P_MIN, guess);
        for (int i = 0; i < 4; i++) {
            water.stateFromPressureEnthalpy(p, hGas, ts);
            double vl = Math.min(volume * (1 - MIN_GAS_FRACTION), ml / ts.density);
            double vg = volume - vl;
            liquidVolume = vl;
            p = Math.max(P_MIN, Math.min(P_MAX, n2Kg * R_N2 * ts.temperature / vg));
        }
        return p;
    }

    private double stepGas(double dt, double surgeKg, double surgeH, double heaterW, double reliefOpenPa, double reliefKgS,
            double safetyOpenPa, double safetyKgS) {
        double outH = hGas;
        if (surgeKg > 0) {
            hGas = (hGas * ml + surgeKg * surgeH) / Math.max(1e-9, ml + surgeKg);
            ml += surgeKg;
        } else if (surgeKg < 0) {
            ml -= Math.min(ml, -surgeKg);
        }
        if (ml > 1e-9) hGas += heaterW * dt / ml;
        ul = ml * hGas;
        if (pressure >= reliefOpenPa) reliefOpen = true;
        else if (pressure < reliefOpenPa - 0.4e6) reliefOpen = false;
        if (pressure >= safetyOpenPa) safetyOpen = true;
        else if (pressure < safetyOpenPa - 0.3e6) safetyOpen = false;
        double vent = ((reliefOpen ? reliefKgS * pressure / reliefOpenPa : 0) + (safetyOpen ? safetyKgS * pressure / safetyOpenPa : 0)) * dt;
        lastReliefKgS = vent / dt;
        if (vent > 0) {
            // через клапан уходит азот; подушки почти нет — вода
            double reserve = 0.05 * n2Kg;
            double fromGas = Math.min(vent, Math.max(0, n2Kg - reserve));
            n2Kg -= fromGas;
            double fromLiquid = Math.min(ml, 3 * (vent - fromGas));
            ml -= fromLiquid;
            relievedKg += fromGas + fromLiquid;
        }
        ml = Math.max(0, ml);
        pressure = gasPressure(pressure);
        return outH;
    }

    /** Насыщенное состояние при давлении {@code p} и уровне {@code level} (доля объёма под водой). */
    public void initialize(double p, double level) {
        if (gas) {
            initialize(p, level, sat(p).hF() - 100e3);
            return;
        }
        sat(p);
        double vl = volume * Math.max(0, Math.min(1, level));
        ml = vl / sat.vF();
        ul = ml * sat.uF();
        mv = (volume - vl) / sat.vG();
        uv = mv * sat.uG();
        pressure = p;
        liquidVolume = vl;
        reliefOpen = safetyOpen = false;
    }

    private SaturationState sat(double p) {
        water.saturationP(p, satBuf);
        return sat.set(p, satBuf);
    }

    /** Удельный объём области массы m и энергии u при давлении p, м³/кг: h = u + Pv — простая итерация. */
    private double specificVolume(double p, double u) {
        double v = 1.5e-3, h = u + p * v;
        for (int i = 0; i < 6; i++) {
            water.stateFromPressureEnthalpy(p, h, ts);
            double vn = 1 / ts.density;
            double hn = u + p * vn;
            if (Math.abs(hn - h) < 1e-3 * Math.max(1, Math.abs(h)) * 1e-3) {
                v = vn;
                break;
            }
            h = hn;
            v = vn;
        }
        return v;
    }

    private double volumeError(double p, double uL, double uV) {
        double v = 0;
        if (ml > 1e-9) v += ml * specificVolume(p, uL);
        if (mv > 1e-9) v += mv * specificVolume(p, uV);
        return v - volume;
    }

    /** Давление из текущих масс и энергий (Иллинойс-вариант метода хорд в вилке, функция убывает по P). */
    private double solvePressure(double guess) {
        double uL = ml > 1e-9 ? ul / ml : 0, uV = mv > 1e-9 ? uv / mv : 0;
        double lo = Math.max(P_MIN, guess * 0.97), hi = Math.min(P_MAX, guess * 1.03);
        double flo = volumeError(lo, uL, uV), fhi = volumeError(hi, uL, uV);
        for (int i = 0; i < 40 && flo < 0 && lo > P_MIN; i++) {
            lo = Math.max(P_MIN, lo * 0.8);
            flo = volumeError(lo, uL, uV);
        }
        for (int i = 0; i < 40 && fhi > 0 && hi < P_MAX; i++) {
            hi = Math.min(P_MAX, hi * 1.2);
            fhi = volumeError(hi, uL, uV);
        }
        if (flo < 0) return lo;
        if (fhi > 0) return hi;
        int side = 0;
        double p = guess;
        for (int i = 0; i < 60; i++) {
            p = (lo * fhi - hi * flo) / (fhi - flo);
            double f = volumeError(p, uL, uV);
            if (Math.abs(f) < 1e-7 * volume || hi - lo < 1) break;
            if (f > 0) {
                lo = p;
                flo = f;
                if (side == 1) fhi /= 2;
                side = 1;
            } else {
                hi = p;
                fhi = f;
                if (side == -1) flo /= 2;
                side = -1;
            }
        }
        return p;
    }

    /**
     * Шаг.
     *
     * @param surgeKg       масса из контура в КД за шаг (> 0 — insurge с энтальпией {@code surgeH}, < 0 — outsurge жидкостью
     *                      КД), кг
     * @param sprayKgS      впрыск, кг/с, с энтальпией {@code sprayH}
     * @param heaterW       мощность ТЭНов, Вт
     * @param reliefOpenPa  давление открытия ИПУ (закрытие на 0,4 МПа ниже); capacity — {@code reliefKgS} при открытии
     * @param safetyOpenPa  давление открытия ПК (закрытие на 0,3 МПа ниже), пропускная способность {@code safetyKgS}
     * @return энтальпия жидкости, ушедшей в контур при outsurge, Дж/кг
     */
    public double step(double dt, double surgeKg, double surgeH, double sprayKgS, double sprayH, double heaterW,
            double reliefOpenPa, double reliefKgS, double safetyOpenPa, double safetyKgS) {
        if (gas) return stepGas(dt, surgeKg, surgeH, heaterW, reliefOpenPa, reliefKgS, safetyOpenPa, safetyKgS);
        double hl = ml > 1e-9 ? ul / ml + pressure * liquidVolume / ml : sat(pressure).hF();
        double outH = hl;
        // массообмен с контуром
        if (surgeKg > 0) {
            ml += surgeKg;
            ul += surgeKg * surgeH;
        } else if (surgeKg < 0) {
            double out = Math.min(ml, -surgeKg);
            ul -= out * (ul / Math.max(ml, 1e-9)) + out * pressure * liquidVolume / Math.max(ml, 1e-9);
            ml -= out;
        }
        SaturationState s = sat(pressure);
        double hf = s.hF(), hg = s.hG(), hfg = Math.max(1e3, hg - hf);
        // ТЭНы и впрыск
        ul += heaterW * dt;
        double spray = Math.max(0, sprayKgS) * dt;
        ml += spray;
        ul += spray * sprayH;
        double condSpray = SPRAY_EFFICIENCY * spray * Math.max(0, hf - sprayH) / hfg;
        // релаксация к насыщению
        double hlNow = ml > 1e-9 ? (ul + pressure * liquidVolume) / ml : hf;
        double vVol = volume - liquidVolume;
        double hvNow = mv > 1e-9 ? (uv + pressure * vVol) / mv : hg;
        double evap = ml * Math.max(0, hlNow - hf) / hfg * Math.min(1, dt / TAU_FLASH_S);
        // влага подушки выпадает жидкостью (уносит h_f); пар, сконденсированный на струях, уносит h_g — теплота
        // конденсации уходит в капли впрыска, которые уже учтены в жидкости
        double rain = Math.min(mv, mv * Math.max(0, hg - hvNow) / hfg * Math.min(1, dt / TAU_COND_S));
        condSpray = Math.min(condSpray, mv - rain);
        evap = Math.min(evap, ml);
        ml += rain + condSpray - evap;
        mv += evap - rain - condSpray;
        ul += rain * hf + condSpray * hg - evap * hg;
        uv += evap * hg - rain * hf - condSpray * hg;
        // ИПУ и ПК (гистерезис), пропускная способность — по давлению (критическое истечение ∝ P)
        if (pressure >= reliefOpenPa) reliefOpen = true;
        else if (pressure < reliefOpenPa - 0.4e6) reliefOpen = false;
        if (pressure >= safetyOpenPa) safetyOpen = true;
        else if (pressure < safetyOpenPa - 0.3e6) safetyOpen = false;
        double vent = ((reliefOpen ? reliefKgS * pressure / reliefOpenPa : 0) + (safetyOpen ? safetyKgS * pressure / safetyOpenPa : 0)) * dt;
        lastReliefKgS = vent / dt;
        if (vent > 0) {
            double fromVapor = Math.min(vent, mv);
            if (fromVapor > 0) {
                double hv = (uv + pressure * vVol) / mv;
                uv -= fromVapor * hv;
                mv -= fromVapor;
            }
            // подушки нет — через клапан идёт вода (пароводяная смесь), втрое больше по массе
            double fromLiquid = Math.min(ml, 3 * (vent - fromVapor));
            if (fromLiquid > 0) {
                double hlv = (ul + pressure * liquidVolume) / ml;
                ul -= fromLiquid * hlv;
                ml -= fromLiquid;
            }
            relievedKg += fromVapor + fromLiquid;
        }
        ml = Math.max(0, ml);
        mv = Math.max(0, mv);
        // давление и работа сжатия между областями: две итерации неподвижной точки
        double p = pressure;
        for (int k = 0; k < 2; k++) {
            p = solvePressure(p);
            double uL = ml > 1e-9 ? ul / ml : 0;
            double vl = ml > 1e-9 ? ml * specificVolume(p, uL) : 0;
            vl = Math.min(volume, vl);
            double dV = vl - liquidVolume;
            ul -= p * dV;
            uv += p * dV;
            liquidVolume = vl;
        }
        pressure = solvePressure(p);
        if (ml > 1e-9) liquidVolume = Math.min(volume, ml * specificVolume(pressure, ul / ml));
        else liquidVolume = 0;
        return outH;
    }

    public double pressure() {
        return pressure;
    }

    /** Уровень — доля объёма под водой. */
    public double level() {
        return liquidVolume / volume;
    }

    public double liquidKg() {
        return ml;
    }

    public double vaporKg() {
        return mv;
    }

    public double volume() {
        return volume;
    }

    /** Температура жидкости, К. */
    public double liquidTempK() {
        if (gas) return water.temperaturePH(pressure, hGas);
        if (ml <= 1e-9) return water.saturationP(pressure).tSat();
        return water.temperaturePH(pressure, (ul + pressure * liquidVolume) / ml);
    }

    public boolean reliefOpen() {
        return reliefOpen;
    }

    public boolean safetyOpen() {
        return safetyOpen;
    }

    /** Сброшено через ИПУ/ПК с начала (в барботёр), кг. */
    public double relievedKg() {
        return relievedKg;
    }

    public double reliefFlowKgS() {
        return lastReliefKgS;
    }

    /** Полная внутренняя энергия, Дж (для баланса в тестах). */
    public double internalEnergyJ() {
        return ul + uv;
    }

    public void save(StateWriter out) {
        out.putDouble("ml", ml);
        out.putDouble("ul", ul);
        out.putDouble("mv", mv);
        out.putDouble("uv", uv);
        out.putDouble("p", pressure);
        out.putDouble("vl", liquidVolume);
        out.putDouble("relieved", relievedKg);
        if (gas) {
            out.putDouble("n2", n2Kg);
            out.putDouble("hgas", hGas);
        }
    }

    public void load(StateReader in) {
        ml = in.optDouble("ml", 0);
        ul = in.optDouble("ul", 0);
        mv = in.optDouble("mv", 0);
        uv = in.optDouble("uv", 0);
        pressure = in.optDouble("p", 0.1e6);
        liquidVolume = in.optDouble("vl", 0);
        relievedKg = in.optDouble("relieved", 0);
        if (gas) {
            n2Kg = Math.max(0, in.optDouble("n2", 0));
            hGas = in.optDouble("hgas", ml > 0 ? ul / ml : 1.0e6);
            if (n2Kg > 0) pressure = gasPressure(Math.max(P_MIN, pressure));
            return;
        }
        if (ml + mv > 0) pressure = solvePressure(Math.max(P_MIN, pressure));
    }
}
