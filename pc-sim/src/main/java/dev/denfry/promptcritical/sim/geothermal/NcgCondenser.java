package dev.denfry.promptcritical.sim.geothermal;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.thermo.if97.If97;

/**
 * Конденсатор геотермальной турбины с неконденсирующимися газами (15 §15.6.2). Пар конденсируется, а CO₂ и H₂S
 * остаются в паровом пространстве; их парциальное давление по уравнению идеального газа складывается с давлением
 * насыщения при температуре конденсации. Пароструйный эжектор {@code ncg_ejector} отсасывает газ, тратя рабочий пар;
 * без отсоса НКГ копятся, вакуум падает, теплоперепад турбины и мощность — тоже.
 */
public final class NcgCondenser {
    /** Газовая постоянная НКГ, Дж/(кг·К): смесь считается углекислым газом (CO₂ — 90–98 % НКГ, DiPippo гл. 5). */
    public static final double R_NCG = 8.314_462 / 0.044_01;
    /** Недогрев конденсата до температуры охлаждающей воды на выходе, К. */
    public static final double TTD_K = 5;
    /** [BALANCE] Рабочего пара на кг отсасываемого газа, кг/кг (двухступенчатый пароструйный эжектор). */
    public static final double MOTIVE_STEAM_PER_KG = 5;
    /** Постоянная отсоса накопленного газа при работающем эжекторе, с. */
    public static final double EVAC_TAU_S = 10;
    /** Давление в конденсаторе не выше атмосферного: дальше газ уходит через атмосферный клапан. */
    public static final double P_ATM = 101_325;

    private final double volumeM3;
    private final double ejectorKgS;
    private double ncgKg;
    private double motiveKgS;

    /**
     * @param volumeM3   паровой объём конденсатора, м³
     * @param ejectorKgS производительность эжектора по газу, кг/с
     */
    public NcgCondenser(double volumeM3, double ejectorKgS) {
        this.volumeM3 = Math.max(1, volumeM3);
        this.ejectorKgS = Math.max(0, ejectorKgS);
    }

    public double ncgKg() {
        return ncgKg;
    }

    /** Рабочий пар эжектора на последнем шаге, кг/с (его вычитают из пара на турбину). */
    public double motiveSteamKgS() {
        return motiveKgS;
    }

    /** Парциальное давление НКГ, Па. */
    public double ncgPressurePa(double coolingWaterK) {
        return ncgKg * R_NCG * (coolingWaterK + TTD_K) / volumeM3;
    }

    /** Давление в конденсаторе: насыщение при T_охл + недогрев плюс НКГ, Па. */
    public double pressurePa(double coolingWaterK) {
        double tc = Math.max(If97.T_MIN + 1, Math.min(373, coolingWaterK + TTD_K));
        return Math.min(P_ATM, If97.psat(tc) + ncgPressurePa(coolingWaterK));
    }

    /**
     * Шаг: в конденсатор пришло {@code ncgInKgS} газа; эжектор отсасывает приток и накопленное до своей
     * производительности, если включён и есть рабочий пар.
     */
    public void step(double dt, double ncgInKgS, boolean ejectorOn, double coolingWaterK) {
        if (!(dt > 0)) return;
        double in = Double.isFinite(ncgInKgS) ? Math.max(0, ncgInKgS) : 0;
        double out = ejectorOn ? Math.min(ejectorKgS, in + ncgKg / EVAC_TAU_S) : 0;
        motiveKgS = out * MOTIVE_STEAM_PER_KG;
        ncgKg = Math.max(0, ncgKg + (in - out) * dt);
        double cap = Math.max(0, P_ATM - If97.psat(Math.min(373, coolingWaterK + TTD_K))) * volumeM3
                / (R_NCG * (coolingWaterK + TTD_K));
        ncgKg = Math.min(ncgKg, cap);
    }

    /**
     * Удельная работа турбины от сухого насыщенного пара при {@code pInPa} до {@code pOutPa} с внутренним КПД
     * {@code eta}, Дж/кг.
     */
    public static double turbineWorkJkg(double pInPa, double pOutPa, double eta) {
        if (!(pInPa > pOutPa) || !(pOutPa >= If97.P_MIN)) return 0;
        var in = If97.satVapor(pInPa);
        double hs = If97.ps(pOutPa, in.s()).h();
        return Math.max(0, eta * (in.h() - hs));
    }

    public void save(StateWriter out) {
        out.putDouble("ncg_kg", ncgKg);
    }

    public void load(StateReader in) {
        double m = in.optDouble("ncg_kg", 0);
        ncgKg = Double.isFinite(m) ? Math.max(0, m) : 0;
    }
}
