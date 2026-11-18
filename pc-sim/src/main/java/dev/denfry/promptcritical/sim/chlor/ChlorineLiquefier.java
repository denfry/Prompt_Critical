package dev.denfry.promptcritical.sim.chlor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Осушка и сжижение хлора. Влажный хлор из ванн сушат 98 %-й серной кислотой (сухой хлор не разъедает сталь),
 * компримируют до 0,6–1,0 МПа и конденсируют фреоновым или аммиачным холодом при −10…−35 °C. Несконденсированный
 * «абгаз» несёт инерты (воздух, CO₂, O₂ из анодного побочного процесса) и водород; хлор в нём равен упругости пара при
 * температуре конденсации. Чем глубже сжижение, тем больше водорода в абгазе: выше 4 % (об.) смесь H₂–Cl₂ взрывается,
 * поэтому степень сжижения ограничивают по водороду (Якименко, 1974; O'Brien et al., т. II, гл. 9 «Chlorine
 * liquefaction»; Euro Chlor GEST 76/55). Абгаз поглощают щёлочью в санитарной колонне (гипохлорит); без щёлочи он
 * уходит в атмосферу.
 *
 * <p>Упругость пара хлора — Клаузиус–Клапейрон от нормальной точки кипения 239,1 К с теплотой испарения
 * 20,4 кДж/моль: 1,2 бар при −30 °C, 6,7 бар при 20 °C (Perry, табл. 2-8: 1,2 и 6,8).
 */
public final class ChlorineLiquefier {
    public static final double TB_K = 239.11, HVAP_J_MOL = 20.4e3, R = 8.314462618, P_ATM = 101_325;
    public static final double MIN_K = 238.15, MAX_K = 283.15, MIN_MPA = 0.3, MAX_MPA = 1.2;
    /** Инерты в сухом хлоре из диафрагменных ванн, мольная доля (воздух, CO₂, O₂ ≈ 2 %) [Якименко]. */
    public static final double INERTS = 0.02;
    /** Водород в хлоре из ванн при нормальном уровне анолита. */
    public static final double H2_NORMAL = 0.001;
    public static final double H2_LIMIT = 0.04;
    /** Холод: работа компрессора и холодильной машины, Дж на кг сжиженного хлора (≈ 70 кВт·ч/т) [O'Brien]. */
    public static final double POWER_J_PER_KG = 0.25e6;

    private double condK = 253.15, pressureMpa = 0.8;
    private boolean on, tripped;
    private double liquidKgS, tailCl2KgS, tailH2Fraction, degree;

    /** Упругость пара хлора, Па. */
    public static double vapourPressurePa(double tempK) {
        return P_ATM * Math.exp(HVAP_J_MOL / R * (1 / TB_K - 1 / tempK));
    }

    public double condK() {
        return condK;
    }

    public void setCondK(double k) {
        condK = Math.clamp(k, MIN_K, MAX_K);
    }

    public double pressureMpa() {
        return pressureMpa;
    }

    public void setPressureMpa(double mpa) {
        pressureMpa = Math.clamp(mpa, MIN_MPA, MAX_MPA);
    }

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
        if (on) tripped = false;
    }

    public boolean tripped() {
        return tripped;
    }

    /**
     * Сжижение.
     *
     * @param cl2KgS     хлор на входе, кг/с
     * @param h2Fraction водород в хлоре, мольная доля
     */
    public void step(double cl2KgS, double h2Fraction) {
        liquidKgS = tailCl2KgS = degree = 0;
        tailH2Fraction = 0;
        if (!on || tripped || cl2KgS <= 0) {
            tailCl2KgS = on && !tripped ? 0 : cl2KgS;
            return;
        }
        double cl2Mol = cl2KgS / DiaphragmCellRoom.M_CL2;
        double inertMol = cl2Mol * INERTS / (1 - INERTS);
        double h2Mol = cl2Mol * h2Fraction;
        double p = pressureMpa * 1e6, pv = vapourPressurePa(condK);
        // хлор, оставшийся в газе: упругость пара над жидкостью
        double gasCl2 = pv >= p ? cl2Mol : (inertMol + h2Mol) * pv / (p - pv);
        gasCl2 = Math.min(cl2Mol, gasCl2);
        degree = 1 - gasCl2 / cl2Mol;
        tailH2Fraction = h2Mol / (inertMol + h2Mol + gasCl2);
        if (tailH2Fraction > H2_LIMIT) tripped = true;
        liquidKgS = (cl2Mol - gasCl2) * DiaphragmCellRoom.M_CL2;
        tailCl2KgS = gasCl2 * DiaphragmCellRoom.M_CL2;
    }

    public double liquidKgS() {
        return liquidKgS;
    }

    /** Хлор в абгазе (или весь вход, если установка стоит), кг/с. */
    public double tailCl2KgS() {
        return tailCl2KgS;
    }

    public double tailH2Fraction() {
        return tailH2Fraction;
    }

    /** Степень сжижения. */
    public double degree() {
        return degree;
    }

    public double powerW() {
        return liquidKgS * POWER_J_PER_KG;
    }

    public void save(StateWriter out) {
        out.putDouble("t", condK);
        out.putDouble("p", pressureMpa);
        out.putBoolean("on", on);
        out.putBoolean("trip", tripped);
    }

    public void load(StateReader in) {
        setCondK(in.optDouble("t", 253.15));
        setPressureMpa(in.optDouble("p", 0.8));
        on = in.optInt("on", 0) != 0;
        tripped = in.optInt("trip", 0) != 0;
    }
}
