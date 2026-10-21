package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Кислородный конвертер {@code bof_vessel} (08 §8.6.1), SLOW. Ванна — одна лумпед-зона: масса жидкого, C, Si, Mn,
 * P, S и температура; шлак — FeO, SiO₂, MnO, P₂O₅, CaO.
 *
 * <p>Обезуглероживание: dC/dt = −(2·M_C/V_m)·V̇_O₂·η_C, η_C = η·min(1, C/C_кр). На шаге считается точно: до C_кр
 * линейно, ниже — экспонента (шаг SLOW бывает больше постоянной времени доводки). Остаток кислорода
 * (1 − η_C)·V̇ идёт сначала на Si, затем на Mn и P (к целям {@link #MN_RETAINED} и дефосфорации по основности, τ
 * {@link #REFINE_TAU_S}), остальное делится между дожиганием CO → CO₂ (не больше {@link #POST_COMBUSTION_MAX} CO
 * шага) и FeO шлака: доля FeO {@link #FEO_SHARE} растёт с высотой фурмы (мягкая продувка) [моё]. Когда углерода
 * мало, CO нет — весь кислород уходит в FeO: передув снижает выход.
 *
 * <p>Тепло — теплоты окисления 08 §8.6.1 (C→CO 9,2, C→CO₂ 32,8, Si 32,4, Mn 7,0, Fe→FeO 4,9, P 24,0 МДж/кг) как
 * нетто в ванну; расход — нагрев жидкого (c_p 820), лом 1,35 ГДж/т до 1650 °C (тает с τ {@link #SCRAP_TAU_S}),
 * известь (c_p 950, τ {@link #LIME_TAU_S}) и потери корпуса. С этими теплотами чугун 4,5 % C, 0,5 % Si при 1350 °C
 * принимает ≈ 20 % лома (типично 15–25 %).
 */
public final class Converter {
    static final double M_C = 12.011, M_SI = 28.086, M_MN = 54.938, M_P = 30.974, M_FE = 55.845, M_SIO2 = 60.084,
            M_MNO = 70.937, M_P2O5 = 141.945, M_FEO = 71.844;
    /** Молярный объём, м³/моль. */
    static final double VM = 0.022414;
    /** Углерод на нм³ O₂ при 2C + O₂ → 2CO, кг (24/22,4 в 08 §8.6.1). */
    public static final double C_PER_NM3_O2 = 2 * M_C / 1000 / VM;
    /** Теплоты окисления, Дж/кг элемента (08 §8.6.1). */
    public static final double HEAT_C_CO = 9.2e6, HEAT_C_CO2 = 32.8e6, HEAT_SI = 32.4e6, HEAT_MN = 7.0e6,
            HEAT_FE = 4.9e6, HEAT_P = 24.0e6;
    /** Лом до 1650 °C, Дж/кг; c_p жидкой стали и извести, Дж/(кг·К). */
    public static final double SCRAP_HEAT = 1.35e6, SCRAP_REF_K = 1923.15, STEEL_CP = 820, LIME_CP = 950;
    /** Постоянные растворения лома и извести, с физ. [BALANCE]. */
    public static final double SCRAP_TAU_S = 150, LIME_TAU_S = 300;
    /** Состав лома [допущение]. */
    public static final double SCRAP_C = 0.0015, SCRAP_MN = 0.005;
    /** Mn, оставшийся в металле; предел дефосфорации и основность, при которой он достигается [BALANCE]. */
    public static final double MN_RETAINED = 0.3, DEPHOS_MAX = 0.9, DEPHOS_BASICITY = 3, REFINE_TAU_S = 180;
    /** Доля остатка O₂ в FeO при номинальной фурме; номинальная высота фурмы над ванной, м [моё]. */
    public static final double FEO_SHARE = 0.4, LANCE_NOMINAL_M = 1.0;
    /** Дожигание: не больше этой доли CO шага [BALANCE; в конвертере 10–15 %]. */
    public static final double POST_COMBUSTION_MAX = 0.15;
    /** Доля тепла дожигания, переданная ванне (остальное уходит с газом) [BALANCE; 40–80 %]. */
    public static final double POST_COMBUSTION_TRANSFER = 0.5;
    /** Потери корпуса, Вт на т^(2/3) металла (10 т — ≈ 0,75 МВт) [BALANCE]. */
    public static final double LOSS_W_PER_T23 = 1.6e5;
    /** [C][O] = 0,0025 %² в долях; потолок растворённого O. */
    public static final double CO_PRODUCT = 2.5e-7, MAX_DISSOLVED_O = 0.001;
    /** Выбросы: Si чугуна выше, фурма ниже; теряется доля шлака и металла [моё]. */
    public static final double SLOP_SI = 0.01, SLOP_LANCE_M = 0.8, SLOP_SLAG_LOSS = 0.3, SLOP_METAL_LOSS = 0.01;
    /** Передув — продувка ниже этой доли C. */
    public static final double OVERBLOW_C = 0.0004;
    /** Фурма ниже — в ванне. */
    public static final double LANCE_MIN_M = 0.2;

    private final SteelmakingConfig cfg;
    private SteelmakingEvents events = SteelmakingEvents.NONE;
    private ConverterPhase phase = ConverterPhase.EMPTY;
    private double metalKg, c, si, mn, p, s, tempK = 298.15;
    private double scrapKg, limeKg, mnCharged, pCharged, chargeSi;
    private double feo, sio2, mno, p2o5, cao;
    private double blowS, o2Blown, coMolS, co2MolS;
    private boolean slopped, overblownLatched, lanceLatched;

    public Converter(SteelmakingConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    public void setEventSink(SteelmakingEvents sink) {
        events = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Завалка (в пустой или в завалку до продувки). Сигнатура GDD {@code charge(hmKg, Composition hm, scrapKg,
     * limeKg)} — масса, температура и химия чугуна в одном {@link SteelMelt}.
     */
    public void charge(SteelMelt hotMetal, double scrapKg, double limeKg) {
        if (phase != ConverterPhase.EMPTY && phase != ConverterPhase.CHARGED)
            throw new IllegalStateException("charge during " + phase);
        if (!(scrapKg >= 0 && limeKg >= 0)) throw new IllegalArgumentException("scrap/lime");
        double m = hotMetal.massKg();
        if (m > 0) {
            tempK = metalKg > 0 ? (metalKg * tempK + m * hotMetal.tempK()) / (metalKg + m) : hotMetal.tempK();
            metalKg += m;
            c += m * hotMetal.frac(AlloyElement.C);
            si += m * hotMetal.frac(AlloyElement.SI);
            mn += m * hotMetal.frac(AlloyElement.MN);
            p += m * hotMetal.frac(AlloyElement.P);
            s += m * hotMetal.frac(AlloyElement.S);
            mnCharged += m * hotMetal.frac(AlloyElement.MN);
            pCharged += m * hotMetal.frac(AlloyElement.P);
        }
        if (metalKg <= 0) throw new IllegalArgumentException("no hot metal in the vessel");
        this.scrapKg += scrapKg;
        mnCharged += scrapKg * SCRAP_MN;
        this.limeKg += limeKg;
        chargeSi = si / metalKg;
        setPhase(ConverterPhase.CHARGED);
    }

    /** Шаг SLOW: {@code o2Nm3S} — дутьё O₂, нм³/с; {@code lanceM} — высота фурмы над ванной, м. */
    public void stepSlow(double dt, double o2Nm3S, double lanceM) {
        if (!(dt > 0) || !(o2Nm3S >= 0)) throw new IllegalArgumentException("dt/o2");
        coMolS = co2MolS = 0;
        if (phase == ConverterPhase.EMPTY) return;
        boolean blowing = o2Nm3S > 0;
        if (blowing && phase != ConverterPhase.BLOWING) setPhase(ConverterPhase.BLOWING);
        else if (!blowing && phase == ConverterPhase.BLOWING) setPhase(ConverterPhase.BLOWN);

        double q = 0, sink = 0;
        // лом и известь тают при любой фазе
        double dScrap = scrapKg * -Math.expm1(-dt / SCRAP_TAU_S);
        if (dScrap > 0) {
            scrapKg -= dScrap;
            sink += dScrap * (SCRAP_HEAT + STEEL_CP * (tempK - SCRAP_REF_K));
            metalKg += dScrap;
            c += dScrap * SCRAP_C;
            mn += dScrap * SCRAP_MN;
        }
        double dLime = limeKg * -Math.expm1(-dt / LIME_TAU_S);
        if (dLime > 0) {
            limeKg -= dLime;
            cao += dLime;
            sink += dLime * LIME_CP * (tempK - 298.15);
        }

        if (blowing) {
            blowS += dt;
            double o2 = o2Nm3S * dt;
            o2Blown += o2;
            // углерод: линейно до C_кр, ниже — экспонента
            double rate = C_PER_NM3_O2 * o2Nm3S * cfg.decarbEfficiency();
            double cCrit = cfg.criticalCarbon() * metalKg, cOld = c;
            double left = dt;
            if (c > cCrit) {
                double t1 = (c - cCrit) / rate;
                if (t1 >= dt) {
                    c -= rate * dt;
                    left = 0;
                } else {
                    c = cCrit;
                    left = dt - t1;
                }
            }
            if (left > 0 && c > 0) c *= Math.exp(-rate / cCrit * left);
            double dc = cOld - c;
            double rest = Math.max(0, o2 - dc / C_PER_NM3_O2);
            // кремний — первым
            double o2Si = Math.min(rest, si * 1000 / M_SI * VM);
            double dSi = o2Si / VM / 1000 * M_SI;
            rest -= o2Si;
            si = Math.max(0, si - dSi);
            // Mn и P — к целям, когда кремний выгорел
            double dMn = 0, dP = 0;
            if (si <= 1e-9 * metalKg && rest > 0) {
                double f = -Math.expm1(-dt / REFINE_TAU_S);
                double basicity = sio2 > 0 ? cao / sio2 : DEPHOS_BASICITY;
                double pTarget = pCharged * (1 - DEPHOS_MAX * Math.min(1, basicity / DEPHOS_BASICITY));
                dMn = Math.max(0, mn - MN_RETAINED * mnCharged) * f;
                dP = Math.max(0, p - pTarget) * f;
                double need = (dMn * 1000 / M_MN * 0.5 + dP * 1000 / M_P * 1.25) * VM;
                if (need > rest) {
                    double k = rest / need;
                    dMn *= k;
                    dP *= k;
                    need = rest;
                }
                rest -= need;
                mn -= dMn;
                p -= dP;
            }
            // остаток: дожигание CO и FeO
            double coMol = dc * 1000 / M_C;
            double feoShare = FEO_SHARE * Math.max(0.5, Math.min(2, lanceM / LANCE_NOMINAL_M));
            double o2Pc = Math.min(rest * (1 - feoShare), 0.5 * POST_COMBUSTION_MAX * coMol * VM);
            double co2Mol = 2 * o2Pc / VM;
            double o2Fe = rest - o2Pc;
            double dFe = Math.min(o2Fe / VM * 2 * M_FE / 1000, Math.max(0, metalKg - c - si - mn - p - s) * 0.5);
            metalKg -= dc + dSi + dMn + dP + dFe;
            sio2 += dSi * M_SIO2 / M_SI;
            mno += dMn * M_MNO / M_MN;
            p2o5 += dP * M_P2O5 / (2 * M_P);
            feo += dFe * M_FEO / M_FE;
            q += dc * HEAT_C_CO + co2Mol * M_C / 1000 * (HEAT_C_CO2 - HEAT_C_CO) * POST_COMBUSTION_TRANSFER
                    + dSi * HEAT_SI + dMn * HEAT_MN
                    + dP * HEAT_P + dFe * HEAT_FE;
            coMolS = (coMol - co2Mol) / dt;
            co2MolS = co2Mol / dt;

            if (!slopped && chargeSi > SLOP_SI && lanceM < SLOP_LANCE_M && si > 0) {
                slopped = true;
                double slagLost = SLOP_SLAG_LOSS * slagKg(), metalLost = SLOP_METAL_LOSS * metalKg;
                scaleSlag(1 - SLOP_SLAG_LOSS);
                scaleMetal(1 - SLOP_METAL_LOSS);
                events.slopping(slagLost, metalLost);
            }
            if (!overblownLatched && carbonFrac() < OVERBLOW_C) {
                overblownLatched = true;
                events.overblown();
            }
            if (!lanceLatched && lanceM < LANCE_MIN_M) {
                lanceLatched = true;
                events.lanceDamaged();
            }
        }
        double loss = LOSS_W_PER_T23 * Math.pow(metalKg / 1000, 2.0 / 3) * dt;
        tempK += (q - sink - loss) / (metalKg * STEEL_CP);
    }

    /** Выпуск в ковш: жидкая сталь с растворённым O ([C][O] = 0,0025 %²); шлак и нерасплавленный лом уходят. */
    public SteelMelt tap() {
        if (phase == ConverterPhase.EMPTY) return null;
        Map<AlloyElement, Double> chem = new EnumMap<>(AlloyElement.class);
        chem.put(AlloyElement.C, carbonFrac());
        chem.put(AlloyElement.SI, si / metalKg);
        chem.put(AlloyElement.MN, mn / metalKg);
        chem.put(AlloyElement.P, p / metalKg);
        chem.put(AlloyElement.S, s / metalKg);
        chem.put(AlloyElement.O, dissolvedOxygenFrac());
        SteelMelt melt = new SteelMelt(metalKg, tempK, chem);
        if (scrapKg > 1) events.scrapUnmelted(scrapKg);
        metalKg = c = si = mn = p = s = scrapKg = limeKg = mnCharged = pCharged = chargeSi = 0;
        feo = sio2 = mno = p2o5 = cao = 0;
        blowS = o2Blown = coMolS = co2MolS = 0;
        slopped = overblownLatched = lanceLatched = false;
        tempK = 298.15;
        setPhase(ConverterPhase.EMPTY);
        return melt;
    }

    public void save(StateWriter out) {
        out.putInt("phase", phase.ordinal());
        out.putDoubles("bath", new double[] {metalKg, c, si, mn, p, s, tempK, scrapKg, limeKg, mnCharged, pCharged,
            chargeSi}, 12);
        out.putDoubles("slag", new double[] {feo, sio2, mno, p2o5, cao}, 5);
        out.putDoubles("blow", new double[] {blowS, o2Blown}, 2);
        out.putInt("latches", (slopped ? 1 : 0) | (overblownLatched ? 2 : 0) | (lanceLatched ? 4 : 0));
    }

    public void load(StateReader in) {
        phase = ConverterPhase.values()[in.getInt("phase")];
        double[] b = in.getDoubles("bath");
        metalKg = b[0];
        c = b[1];
        si = b[2];
        mn = b[3];
        p = b[4];
        s = b[5];
        tempK = b[6];
        scrapKg = b[7];
        limeKg = b[8];
        mnCharged = b[9];
        pCharged = b[10];
        chargeSi = b[11];
        double[] sl = in.getDoubles("slag");
        feo = sl[0];
        sio2 = sl[1];
        mno = sl[2];
        p2o5 = sl[3];
        cao = sl[4];
        double[] bl = in.getDoubles("blow");
        blowS = bl[0];
        o2Blown = bl[1];
        int l = in.getInt("latches");
        slopped = (l & 1) != 0;
        overblownLatched = (l & 2) != 0;
        lanceLatched = (l & 4) != 0;
    }

    private void scaleSlag(double k) {
        feo *= k;
        sio2 *= k;
        mno *= k;
        p2o5 *= k;
        cao *= k;
    }

    private void scaleMetal(double k) {
        metalKg *= k;
        c *= k;
        si *= k;
        mn *= k;
        p *= k;
        s *= k;
    }

    private void setPhase(ConverterPhase to) {
        if (to == phase) return;
        ConverterPhase from = phase;
        phase = to;
        events.converterPhaseChanged(from, to);
    }

    public ConverterPhase phase() {
        return phase;
    }

    public double carbonFrac() {
        return metalKg > 0 ? c / metalKg : 0;
    }

    public double siliconFrac() {
        return metalKg > 0 ? si / metalKg : 0;
    }

    public double manganeseFrac() {
        return metalKg > 0 ? mn / metalKg : 0;
    }

    public double phosphorusFrac() {
        return metalKg > 0 ? p / metalKg : 0;
    }

    /** Растворённый кислород по [C][O] = 0,0025 %², не выше {@link #MAX_DISSOLVED_O}. */
    public double dissolvedOxygenFrac() {
        return Math.min(MAX_DISSOLVED_O, CO_PRODUCT / Math.max(carbonFrac(), 1e-6));
    }

    public double tempK() {
        return tempK;
    }

    /** Жидкий металл, кг. */
    public double metalKg() {
        return metalKg;
    }

    /** Нерасплавленный лом, кг. */
    public double scrapKg() {
        return scrapKg;
    }

    public double slagKg() {
        return feo + sio2 + mno + p2o5 + cao;
    }

    public double slagFeOFrac() {
        double m = slagKg();
        return m > 0 ? feo / m : 0;
    }

    /** CaO/SiO₂ шлака; без SiO₂ — 0. */
    public double slagBasicity() {
        return sio2 > 0 ? cao / sio2 : 0;
    }

    /** Кислород за плавку, нм³. */
    public double o2BlownNm3() {
        return o2Blown;
    }

    /** Время продувки за плавку, с физ. */
    public double blowTimeS() {
        return blowS;
    }

    /** Конвертерный газ из горловины до подсоса воздуха (CO + CO₂), нм³/с. */
    public double offgasNm3S() {
        return (coMolS + co2MolS) * VM;
    }

    /** Доля CO в CO + CO₂ отходящего газа; без газа — 0. */
    public double offgasCoFrac() {
        double t = coMolS + co2MolS;
        return t > 0 ? coMolS / t : 0;
    }
}
