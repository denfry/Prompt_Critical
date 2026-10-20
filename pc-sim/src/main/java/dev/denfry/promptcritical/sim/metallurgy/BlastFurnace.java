package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.FuelGas;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;
import dev.denfry.promptcritical.sim.mining.CoalAssay;

/**
 * Доменная печь {@code blast_furnace_controller} (08 §8.5). Две лумпед-зоны SLOW:
 * <ul>
 *   <li><b>шахта</b> — столб колош FIFO (кольцевой буфер до 48 слоёв): нижний слой расходуется, верхние ждут —
 *       смена шихты сказывается через время пребывания (6–8 ч физ. при номинале, P2);</li>
 *   <li><b>горн</b> — горение кокса у фурм, прямое восстановление, плавление; одна температура T_г и запас
 *       жидкого чугуна и шлака.</li>
 * </ul>
 * Темп задаёт дутьё (§8.0.3): фурмы сжигают C_ф = 2·x_O₂·V̇/22,4·12 (+ C + H₂O → CO + H₂), нижний слой опускается
 * так, чтобы его углерод покрыл C_ф. Углерод слоя: C_кокс·(1 − 0,08 пыли) = C_ф + C_прям + C_ч + C_Si,Mn (§8.5.3) ⇒
 * железо на кг углерода фурм растёт, если кокса в колоше мало: ход «на пустой» холодит горн. Если кокса не
 * хватает даже на восстановление, недовосстановленное железо уходит в шлак FeO (моё).
 *
 * <p>Тепло горна (§8.5.4): приход — C → CO 9,2 МДж/кг C, физтепло дутья; расход — прямое восстановление
 * 12,7 МДж/кг C, чугун 1,25 ГДж/т и шлак 1,8 ГДж/т при 1500 °C (+c_p·ΔT), обжиг известняка, потери кладки и
 * физтепло колошникового газа (T_кол = 200 °C + 2·(T_г − 1500 °C)) — последнее даёт горну отрицательную обратную
 * связь (моё). Сверху T_г ограничен min(T_ф − 300 К, 1650 °C): избыток уходит с газом.
 *
 * <p>Состав: Si = 0,6 % + 0,01 %/К·(T_г − 1773), C = 4,5 % (± по T и Si), Mn 0,3 % [допущения]; сера —
 * распределение L_S (§8.5.3), 5 % в газ; η_CO = 0,47 при ровном ходе, падает при подвисании [BALANCE]; r_d растёт
 * при M₂₅ &lt; 85 (+0,01 на единицу) [BALANCE].
 */
public final class BlastFurnace {
    static final double M_C = 12.011, M_FE = 55.845, M_SI = 28.086, M_MN = 54.938, M_SIO2 = 60.084, M_FEO = 71.844,
            M_H2O = 18.015, M_H2 = 2.016, M_CO2 = 44.009, VM = 22.414;
    public static final double FE_RECOVERY = 0.995;
    /** Углерод кокса, унесённый пылью и мелочью [BALANCE: даёт «потери» ≈ 34 кг/т T8.9]. */
    public static final double DUST_LOSS = 0.08;
    public static final double HM_MN = 0.003, HM_S_GUESS = 0.0003;
    /** C → CO у фурм; C + H₂O → CO + H₂ (поглощение); прямое восстановление, Дж/кг C. */
    public static final double C_TUYERE_HEAT = 9.2e6, C_STEAM_HEAT = 10.93e6, DIRECT_HEAT = 12.7e6;
    /** Чугун и шлак при 1500 °C, Дж/кг, и их c_p, Дж/(кг·К). */
    public static final double HM_HEAT = 1.25e6, HM_CP = 850, SLAG_HEAT = 1.8e6, SLAG_CP = 1200;
    public static final double REF_HEARTH_K = 1773.15;
    /** Обжиг CaCO₃, Дж/кг. */
    public static final double CALCINATION_HEAT = 1.78e6;
    public static final double LIMESTONE_CAO = 56.077 / 100.087, LIMESTONE_CO2 = 44.009 / 100.087;
    /** Зола кокса: доли SiO₂ и CaO; остальное (Al₂O₃, MgO…) — в шлак как есть. */
    public static final double ASH_SIO2 = 0.5, ASH_CAO = 0.05;
    /** Газ фурменного очага c̄_p и колошниковый газ c̄_p, Дж/(нм³·К) [сверить]. */
    public static final double RACEWAY_GAS_CP = 1_460, TOP_GAS_CP = 1_400;
    /** Кокс входит в фурменный очаг нагретым до 1500 °C: c_p 1,6 кДж/(кг·К). */
    public static final double COKE_RACEWAY_HEAT = 1_600 * (1773.15 - 298.15);
    /** H₂O + C → CO + H₂, Дж/кг H₂O. */
    public static final double H2O_DISSOCIATION = 131.3e3 / 0.018015;
    public static final double TOP_GAS_REF_K = 473.15, TOP_GAS_SLOPE = 2;
    /** Теплоёмкость горна и заплечиков на м³ V_u, Дж/(К·м³); потери кладки при 1500 °C, Вт/м³ [BALANCE]. */
    public static final double HEARTH_HEAT_CAPACITY = 0.6e6, LOSS_W_PER_M3 = 12_000;
    public static final double BANKED_LOSS_FACTOR = 0.1;
    /** Горн застыл (выпуск невозможен, закозление); отогрет; потолок, К. */
    public static final double CHILL_K = 1573.15, FREE_K = 1723.15, MAX_HEARTH_K = 1923.15, RAFT_MARGIN_K = 300;
    public static final double BLOW_IN_MIN_S = 2 * 86_400;
    public static final double BANK_BLAST_FRAC = 0.3, BANK_DELAY_S = 3_600, NO_BLAST_CHILL_S = 1_800,
            NO_COOLING_CHILL_S = 1_800;
    /** При закозлении работает четверть фурм. */
    public static final double CHILLED_BLAST_FACTOR = 0.25;
    /** Застывший горн при закозлении — на столько ниже {@link #CHILL_K}, К [BALANCE]. */
    public static final double CHILL_DROP_K = 100;
    /** hangRisk: вклад T_ф выше 2200 °C (1 на +200 К), производительности выше 0,9 предела (×5), M₂₅ ниже 80. */
    public static final double HANG_RAFT_K = 2473.15, HANG_RAFT_SPAN = 200, HANG_PROD_START = 0.9,
            HANG_PROD_GAIN = 5, HANG_M25 = 80, HANG_M25_SPAN = 10;
    public static final double HANG_RELEASE = 0.5, HANG_MAX_S = 3_600, SLIP_CHILL_K = 50;
    public static final double ETA_CO_BASE = 0.47, ETA_CO_HANG_LOSS = 0.4;
    public static final double RD_STRONG_M25 = 85, RD_PER_M25 = 0.01;
    public static final double RHO_IRON = 6_980, RHO_SLAG = 2_800;
    public static final int MAX_LAYERS = 48;
    /** Лётка: C_d, ⌀ после бура, размыв, предел ⌀ [BALANCE]. */
    public static final double TAP_CD = 0.6, DRILL_D_M = 0.05, EROSION_M_S = 3e-4, MAX_TAPHOLE_D_M = 0.12;
    /** Давление дутья: колошник и перепад столба при номинале, Па изб. */
    public static final double TOP_PRESSURE_PA = 0.01e6, COLUMN_DP_PA = 0.15e6, HANG_DP_FACTOR = 1.3;
    /** Кампания горна, т чугуна на м³ V_u (≈ 20 лет при 2 т/(м³·сут)) [BALANCE]. */
    public static final double LINING_LIFE_T_PER_M3 = 15_000, NO_COOLING_WEAR_FACTOR = 50;
    public static final double GAS_SULFUR = 0.05;

    private static final double T0 = 298.15, G = 9.81;
    private static final int CO = FuelGas.CO.ordinal(), CO2 = FuelGas.CO2.ordinal(), H2 = FuelGas.H2.ordinal(),
            N2 = FuelGas.N2.ordinal(), O2 = FuelGas.O2.ordinal();

    private final BlastFurnaceSpec spec;
    private final IronmakingConfig cfg;
    private IronmakingEvents events = IronmakingEvents.NONE;

    // столб шихты: кольцевой буфер, head — нижний слой
    private final double[] ore = new double[MAX_LAYERS], fe = new double[MAX_LAYERS], sio2 = new double[MAX_LAYERS],
            cao = new double[MAX_LAYERS], coke = new double[MAX_LAYERS], cokeC = new double[MAX_LAYERS],
            cokeAsh = new double[MAX_LAYERS], cokeS = new double[MAX_LAYERS], cokeH = new double[MAX_LAYERS],
            m25 = new double[MAX_LAYERS], lime = new double[MAX_LAYERS];
    private int head, count;

    private BlastFurnaceState state = BlastFurnaceState.COLD;
    private double hearthK = T0;
    private double ironKg, ironC, ironSi, ironS, slagKg, slagCaO, slagSiO2, slagS;
    private double blowInS, lowBlastS, noBlastS, noCoolingS, hangS;
    private double raftK, etaCo = ETA_CO_BASE, hang, rd, hmRate, productivity, topK = T0, blastFrac;
    private final double[] topGas = new double[FuelGas.values().length];
    private double topGasRate;
    private boolean tapOpen, overflowLatched, tuyereLeak;
    private double tapholeD;
    private double wear;
    private int burntTuyeres;

    // накопители шага
    private double sFeRed, sFeLost, sCBurnt, sCDirect, sCSiMn, sCHm, sSiO2, sCaO, sOther, sS, sLime, sCokeH, sM25,
            sCokeKg;

    public BlastFurnace(BlastFurnaceSpec spec, IronmakingConfig cfg) {
        this.spec = spec;
        this.cfg = cfg;
        rd = cfg.directReductionDefault();
    }

    public void setEventSink(IronmakingEvents sink) {
        events = sink == null ? IronmakingEvents.NONE : sink;
    }

    /** Загрузить колошу; {@code false} — засыпь у уровня (нет объёма) или печь прорвана. */
    public boolean charge(BurdenLayer l) {
        if (state == BlastFurnaceState.BURNTHROUGH || count >= MAX_LAYERS) return false;
        if (stockVolumeM3() + l.volumeM3() > spec.usefulVolumeM3()) return false;
        int i = (head + count) % MAX_LAYERS;
        ore[i] = l.oreKg();
        fe[i] = l.oreKg() * l.oreFe();
        sio2[i] = l.oreKg() * l.oreSiO2();
        cao[i] = l.oreKg() * l.oreCaO();
        coke[i] = l.cokeKg();
        CoalAssay a = l.coke();
        if (a != null && l.cokeKg() > 0) {
            cokeC[i] = l.cokeKg() * a.cDaf() * a.dafWorking();
            cokeAsh[i] = l.cokeKg() * a.ashWorking();
            cokeS[i] = l.cokeKg() * a.sulfurDryFrac() * (1 - a.moistureFrac());
            cokeH[i] = l.cokeKg() * a.hDaf() * a.dafWorking();
            m25[i] = Double.isNaN(a.strengthM25()) ? HANG_M25 : a.strengthM25();
        } else {
            cokeC[i] = cokeAsh[i] = cokeS[i] = cokeH[i] = 0;
            m25[i] = RD_STRONG_M25;
        }
        lime[i] = l.limestoneKg();
        count++;
        return true;
    }

    /** Шаг SLOW, с физ. */
    public void stepSlow(double dt, BlastConditions b) {
        if (state == BlastFurnaceState.BURNTHROUGH) {
            hearthK = T0 + (hearthK - T0) * Math.exp(-dt / 86_400);
            clearRates();
            return;
        }
        double v = b.blastNm3S();
        blastFrac = v / spec.ratedBlastNm3S();
        lowBlastS = blastFrac < BANK_BLAST_FRAC ? lowBlastS + dt : 0;
        noBlastS = v <= 0 ? noBlastS + dt : 0;
        noCoolingS = b.cooling() ? 0 : noCoolingS + dt;

        if (state == BlastFurnaceState.COLD && v > 0 && count > 0 && ore[head] <= 0 && coke[head] > 0) {
            setState(BlastFurnaceState.BLOWING_IN);
            blowInS = 0;
        }
        boolean lit = state != BlastFurnaceState.COLD;
        double vEff = state == BlastFurnaceState.CHILLED ? v * CHILLED_BLAST_FACTOR : v;
        double x = b.oxygenFrac(), w = b.moistureKgNm3();
        raftK = vEff > 0 && lit ? raftK(b.blastTempK(), x, w) : 0;
        double cO2 = lit ? 2 * x * vEff / VM * M_C * dt : 0;
        double cH2O = lit ? w * vEff / M_H2O * M_C * dt : 0;
        double cNeed = cO2 + cH2O;
        rd = cfg.directReductionDefault() + RD_PER_M25 * Math.max(0, RD_STRONG_M25 - bottomM25());

        resetSlice();
        double si = siliconFrac(hearthK), c = carbonFrac(hearthK, si);
        double feInHm = 1 - c - si - HM_MN - HM_S_GUESS;
        if (state == BlastFurnaceState.HANGING) burnCokeOnly(cNeed);
        else if (cNeed > 0) descend(cNeed, si, c, feInHm);
        double phi = cNeed > 0 ? sCBurnt / cNeed : 0;

        // чугун и шлак шага
        double hm = sFeRed / feInHm;
        double siKg = si * hm;
        double siO2Slag = Math.max(0, sSiO2 - siKg * M_SIO2 / M_SI);
        double slag = siO2Slag + sCaO + sOther + sFeLost * M_FEO / M_FE;
        double basicity = siO2Slag > 0 ? sCaO / siO2Slag : 0;
        double sNet = sS * (1 - GAS_SULFUR);
        double ls = cfg.sulfurPartition(basicity, hearthK);
        double sHm = hm + ls * slag > 0 ? sNet * hm / (hm + ls * slag) : 0;

        // колошниковый газ
        double gasC = (sCBurnt + sCDirect + sCSiMn) / M_C * VM;
        double hangLoss = Math.max(0, Math.min(1, hang - HANG_RELEASE));
        etaCo = ETA_CO_BASE * (1 - ETA_CO_HANG_LOSS * hangLoss);
        java.util.Arrays.fill(topGas, 0);
        topGas[CO] = gasC * (1 - etaCo);
        topGas[CO2] = gasC * etaCo + sLime * LIMESTONE_CO2 / M_CO2 * VM;
        topGas[H2] = phi * cH2O / M_C * VM + sCokeH / M_H2 * VM;
        topGas[N2] = (1 - x) * v * dt;
        topGas[O2] = lit ? (1 - phi) * x * vEff * dt + x * (v - vEff) * dt : x * v * dt;
        double gasNm3 = 0;
        for (double g : topGas) gasNm3 += g;
        topK = Math.max(373.15, TOP_GAS_REF_K + TOP_GAS_SLOPE * (hearthK - REF_HEARTH_K));

        // тепло горна
        double qIn = lit ? phi * (cO2 * C_TUYERE_HEAT - cH2O * C_STEAM_HEAT) + vEff * dt * airCp(b.blastTempK())
                * (b.blastTempK() - T0) : 0;
        double loss = LOSS_W_PER_M3 * spec.usefulVolumeM3() * Math.max(0, hearthK - T0) / (REF_HEARTH_K - T0) * dt
                * (state == BlastFurnaceState.BANKED ? BANKED_LOSS_FACTOR : 1);
        double qOut = sCDirect * DIRECT_HEAT + hm * (HM_HEAT + HM_CP * (hearthK - REF_HEARTH_K))
                + slag * (SLAG_HEAT + SLAG_CP * (hearthK - REF_HEARTH_K)) + sLime * CALCINATION_HEAT + loss
                + (lit ? gasNm3 * TOP_GAS_CP * (topK - T0) : 0);
        double before = hearthK;
        hearthK += (qIn - qOut) / (HEARTH_HEAT_CAPACITY * spec.usefulVolumeM3());
        // горн не горячее газа очага: избыток уходит вверх, но и скачком вниз потолок горн не охлаждает
        double cap = raftK > 0 ? Math.min(MAX_HEARTH_K, raftK - RAFT_MARGIN_K) : MAX_HEARTH_K;
        if (hearthK > before) hearthK = Math.min(hearthK, Math.max(cap, before));
        hearthK = Math.max(T0, hearthK);

        // в горн
        ironKg += hm;
        ironC += c * hm;
        ironSi += siKg;
        ironS += sHm;
        slagKg += slag;
        slagCaO += sCaO;
        slagSiO2 += siO2Slag;
        slagS += sNet - sHm;

        hmRate = hm / dt;
        productivity = hmRate * 86.4 / spec.usefulVolumeM3();
        topGasRate = gasNm3 / dt;
        for (int k = 0; k < topGas.length; k++) topGas[k] /= dt;

        double m25Now = sCokeKg > 0 ? sM25 / sCokeKg : bottomM25();
        hang = (raftK > 0 ? Math.max(0, (raftK - HANG_RAFT_K) / HANG_RAFT_SPAN) : 0)
                + HANG_PROD_GAIN * Math.max(0, productivity / cfg.maxProductivityTM3Day() - HANG_PROD_START)
                + (lit && cNeed > 0 ? Math.max(0, HANG_M25 - m25Now) / HANG_M25_SPAN : 0);

        wear += hm / (LINING_LIFE_T_PER_M3 * 1000 * spec.usefulVolumeM3())
                * (1 + Math.max(0, (hearthK - REF_HEARTH_K) / 100)) * (b.cooling() ? 1 : NO_COOLING_WEAR_FACTOR);
        checkOverflow();
        if (wear >= 1) {
            wear = 1;
            double iron = ironKg, s = slagKg;
            clearHearth();
            setState(BlastFurnaceState.BURNTHROUGH);
            events.burnthrough(iron, s);
            return;
        }
        transitions(dt, v);
    }

    private void transitions(double dt, double v) {
        boolean chill = noBlastS >= NO_BLAST_CHILL_S || noCoolingS >= NO_COOLING_CHILL_S || hearthK < CHILL_K;
        switch (state) {
            case BLOWING_IN -> {
                blowInS += dt;
                if (blowInS >= BLOW_IN_MIN_S && hearthK >= FREE_K) setState(BlastFurnaceState.RUNNING);
            }
            case RUNNING -> {
                if (chill) setState(BlastFurnaceState.CHILLED);
                else if (hang > 1) {
                    hangS = 0;
                    setState(BlastFurnaceState.HANGING);
                } else if (lowBlastS >= BANK_DELAY_S) setState(BlastFurnaceState.BANKED);
            }
            case HANGING -> {
                hangS += dt;
                if (chill) setState(BlastFurnaceState.CHILLED);
                else if (hang < HANG_RELEASE || hangS >= HANG_MAX_S) {
                    double severity = Math.min(1, hangS / HANG_MAX_S);
                    hearthK -= SLIP_CHILL_K * severity;
                    setState(BlastFurnaceState.SLIP);
                    events.slip(severity);
                }
            }
            case SLIP -> setState(BlastFurnaceState.RUNNING);
            case BANKED -> {
                if (hearthK < CHILL_K || noCoolingS >= NO_COOLING_CHILL_S) setState(BlastFurnaceState.CHILLED);
                else if (v > 0 && blastFrac >= BANK_BLAST_FRAC) setState(BlastFurnaceState.RUNNING);
            }
            case CHILLED -> {
                if (v > 0 && hearthK >= FREE_K && noCoolingS == 0) {
                    blowInS = 0;
                    setState(BlastFurnaceState.BLOWING_IN);
                }
            }
            default -> {}
        }
    }

    private void setState(BlastFurnaceState s) {
        if (s == state) return;
        BlastFurnaceState from = state;
        state = s;
        // закозление: горн застывает — отогрев до FREE_K идёт от застывшего, а не от прежней температуры
        if (s == BlastFurnaceState.CHILLED) hearthK = Math.min(hearthK, CHILL_K - CHILL_DROP_K);
        events.furnaceStateChanged(from, s);
    }

    /** Опускание шихты: нижние слои расходуются, пока их углерод не покроет C фурм. */
    private void descend(double cNeed, double si, double c, double feInHm) {
        double cOtherPerFe = rd * M_C / M_FE + (c + si * 2 * M_C / M_SI + HM_MN * M_C / M_MN) / feInHm;
        double remaining = cNeed;
        while (remaining > 1e-12 && count > 0) {
            int i = head;
            double cL = cokeC[i] * (1 - DUST_LOSS), feL = FE_RECOVERY * fe[i];
            double tuyL, feRedL;
            if (cL <= 0) {
                tuyL = 0;
                feRedL = 0;
            } else if (cL - cOtherPerFe * feL >= 0.05 * cL) {
                tuyL = cL - cOtherPerFe * feL;
                feRedL = feL;
            } else {
                tuyL = 0.05 * cL;
                feRedL = (cL - tuyL) / cOtherPerFe;
            }
            double g = tuyL > 0 ? Math.min(1, remaining / tuyL) : 1;
            sFeRed += g * feRedL;
            sFeLost += g * (fe[i] - feRedL);
            sCBurnt += g * tuyL;
            sCDirect += g * feRedL * rd * M_C / M_FE;
            sCSiMn += g * feRedL / feInHm * (si * 2 * M_C / M_SI + HM_MN * M_C / M_MN);
            sCHm += g * feRedL / feInHm * c;
            sSiO2 += g * (sio2[i] + ASH_SIO2 * cokeAsh[i]);
            sCaO += g * (cao[i] + LIMESTONE_CAO * lime[i] + ASH_CAO * cokeAsh[i]);
            sOther += g * ((1 - ASH_SIO2 - ASH_CAO) * cokeAsh[i] + Math.max(0, ore[i] - fe[i] * oxideFactor()
                    - sio2[i] - cao[i]));
            sS += g * cokeS[i];
            sLime += g * lime[i];
            sCokeH += g * cokeH[i];
            sM25 += g * coke[i] * m25[i];
            sCokeKg += g * coke[i];
            remaining -= g * tuyL;
            consume(i, g);
        }
        sCBurnt = cNeed - Math.max(0, remaining);
    }

    /** Масса окисла на кг Fe в шихте (Fe₂O₃/Fe₃O₄ — 1,38–1,43); остаток массы руды сверх него — порода. */
    private static double oxideFactor() {
        return 1.40;
    }

    /**
     * Подвисание: шихта стоит, фурмы выжигают кокс из столба — после осадки весь столб беднее коксом (кокс
     * снимается со всех слоёв поровну — моё; снятие только снизу оставляло голую руду и сразу закозляло печь).
     */
    private void burnCokeOnly(double cNeed) {
        double total = 0;
        for (int k = 0; k < count; k++) total += cokeC[(head + k) % MAX_LAYERS];
        total *= 1 - DUST_LOSS;
        if (!(total > 0) || !(cNeed > 0)) return;
        double g = Math.min(1, cNeed / total), keep = 1 - g;
        for (int k = 0; k < count; k++) {
            int i = (head + k) % MAX_LAYERS;
            sCBurnt += g * cokeC[i] * (1 - DUST_LOSS);
            sOther += g * (1 - ASH_SIO2 - ASH_CAO) * cokeAsh[i];
            sSiO2 += g * ASH_SIO2 * cokeAsh[i];
            sCaO += g * ASH_CAO * cokeAsh[i];
            sS += g * cokeS[i];
            sCokeH += g * cokeH[i];
            coke[i] *= keep;
            cokeC[i] *= keep;
            cokeAsh[i] *= keep;
            cokeS[i] *= keep;
            cokeH[i] *= keep;
        }
        while (count > 0 && ore[head] <= 1e-9 && coke[head] <= 1e-9 && lime[head] <= 1e-9) pop();
    }

    private void consume(int i, double g) {
        if (g >= 1 - 1e-12) {
            pop();
            return;
        }
        double keep = 1 - g;
        ore[i] *= keep;
        fe[i] *= keep;
        sio2[i] *= keep;
        cao[i] *= keep;
        coke[i] *= keep;
        cokeC[i] *= keep;
        cokeAsh[i] *= keep;
        cokeS[i] *= keep;
        cokeH[i] *= keep;
        lime[i] *= keep;
    }

    private void pop() {
        head = (head + 1) % MAX_LAYERS;
        count--;
    }

    private void resetSlice() {
        sFeRed = sFeLost = sCBurnt = sCDirect = sCSiMn = sCHm = sSiO2 = sCaO = sOther = sS = sLime = sCokeH = sM25
                = sCokeKg = 0;
    }

    private double bottomM25() {
        for (int k = 0; k < count; k++) {
            int i = (head + k) % MAX_LAYERS;
            if (coke[i] > 0) return m25[i];
        }
        return RD_STRONG_M25;
    }

    private void checkOverflow() {
        double level = liquidLevelM();
        if (level >= spec.tuyereLevelM() && !overflowLatched) {
            overflowLatched = true;
            tuyereLeak = true;
            burntTuyeres++;
            events.tuyereBurnt(burntTuyeres);
        } else if (level < spec.tuyereLevelM() - 0.2) {
            overflowLatched = false;
        }
    }

    private void clearHearth() {
        ironKg = ironC = ironSi = ironS = slagKg = slagCaO = slagSiO2 = slagS = 0;
        clearRates();
    }

    private void clearRates() {
        hmRate = productivity = topGasRate = 0;
        java.util.Arrays.fill(topGas, 0);
    }

    /** Si чугуна — «термометр горна». */
    static double siliconFrac(double hearthK) {
        return Math.max(0.002, Math.min(0.015, 0.006 + 1e-4 * (hearthK - REF_HEARTH_K)));
    }

    static double carbonFrac(double hearthK, double si) {
        return Math.max(0.042, Math.min(0.048, 0.045 + 2e-5 * (hearthK - REF_HEARTH_K) - 0.3 * (si - 0.006)));
    }

    /** Средняя теплоёмкость воздуха от 25 °C, Дж/(нм³·К). */
    static double airCp(double tK) {
        return 1_297 + 0.1 * (tK - 273.15);
    }

    /**
     * Теоретическая температура горения у фурм (§8.5.4), К: тепло C → CO, физтепло дутья и кокса (1500 °C) минус
     * разложение влаги — на газ CO + N₂ + H₂.
     */
    public static double raftK(double blastK, double oxygenFrac, double moistureKgNm3) {
        double cO2 = 2 * oxygenFrac / VM * M_C, cH2O = moistureKgNm3 / M_H2O * M_C;
        double q = cO2 * C_TUYERE_HEAT + airCp(blastK) * (blastK - T0) - moistureKgNm3 * H2O_DISSOCIATION
                + (cO2 + cH2O) * COKE_RACEWAY_HEAT;
        double gas = 2 * oxygenFrac + (1 - oxygenFrac) + 2 * moistureKgNm3 / M_H2O * VM;
        return T0 + q / (gas * RACEWAY_GAS_CP);
    }

    /**
     * Работа политропного сжатия воздуха, Дж/кг: c_p·T₁·(π^((k−1)/(k·η)) − 1); π = 3, η 0,82 — ≈ 125 кДж/кг
     * (§8.5.4).
     */
    public static double blowerWorkJPerKg(double pressureRatio, double inletK, double polytropicEff) {
        return 1_005 * inletK * (Math.pow(pressureRatio, 0.4 / 1.4 / polytropicEff) - 1);
    }

    // --- выпуск (реальное время, FAST) ---

    /** Бур вскрыл лётку. */
    public boolean drillTaphole() {
        if (tapOpen || state == BlastFurnaceState.COLD || state == BlastFurnaceState.BURNTHROUGH) return false;
        tapOpen = true;
        tapholeD = DRILL_D_M;
        return true;
    }

    /** Пушка забила лётку ({@code taphole_clay}). */
    public void plugTaphole() {
        tapOpen = false;
    }

    /**
     * Истечение из открытой лётки за {@code dtReal} с реальных: V̇ = C_d·A·√(2gh + 2Δp/ρ), Δp — давление дутья в
     * горне; лётка размывается. Застывший горн не течёт.
     */
    public TapResult tap(double dtReal) {
        if (!tapOpen || state == BlastFurnaceState.BURNTHROUGH || hearthK < CHILL_K || !(dtReal > 0))
            return TapResult.NONE;
        double area = spec.hearthAreaM2(), hth = spec.tapholeHeightM();
        double hi = ironKg / RHO_IRON / area, top = hi + slagKg / RHO_SLAG / area;
        if (top <= hth) return new TapResult(0, 0, hearthK, 0, 0, 0, 0, true);
        double ironAbove = Math.max(0, hi - hth), slagAbove = top - Math.max(hi, hth);
        double fs = slagAbove / (top - hth);
        double rho = fs * RHO_SLAG + (1 - fs) * RHO_IRON;
        double head = G * (RHO_IRON * ironAbove + RHO_SLAG * slagAbove);
        double vel = Math.sqrt(2 * (head + blastPressurePa()) / rho);
        double vol = TAP_CD * Math.PI * tapholeD * tapholeD / 4 * vel * dtReal;
        double ironOut = Math.min((1 - fs) * vol, ironAbove * area) * RHO_IRON;
        double slagOut = Math.min(fs * vol, slagAbove * area) * RHO_SLAG;
        double cF = ironKg > 0 ? ironC / ironKg : 0, siF = ironKg > 0 ? ironSi / ironKg : 0,
                sF = ironKg > 0 ? ironS / ironKg : 0;
        double basicity = slagBasicity();
        double ki = ironKg > 0 ? 1 - ironOut / ironKg : 0, ks = slagKg > 0 ? 1 - slagOut / slagKg : 0;
        ironKg *= ki;
        ironC *= ki;
        ironSi *= ki;
        ironS *= ki;
        slagKg *= ks;
        slagCaO *= ks;
        slagSiO2 *= ks;
        slagS *= ks;
        tapholeD = Math.min(MAX_TAPHOLE_D_M, tapholeD + EROSION_M_S * dtReal);
        return new TapResult(ironOut, slagOut, hearthK, cF, siF, sF, basicity, false);
    }

    // --- показания ---

    public BlastFurnaceState state() {
        return state;
    }

    /** Теоретическая температура у фурм последнего шага, К (0 — дутья нет). */
    public double raftK() {
        return raftK;
    }

    /** Степень использования CO = CO₂/(CO + CO₂). */
    public double etaCO() {
        return etaCo;
    }

    /** Риск подвисания: &gt; 1 — подвисание. */
    public double hangRisk() {
        return hang;
    }

    /** Степень прямого восстановления r_d последнего шага. */
    public double directReduction() {
        return rd;
    }

    public double hearthTempK() {
        return hearthK;
    }

    public double topGasTempK() {
        return topK;
    }

    /** Выход чугуна последнего шага, кг/с физ. */
    public double hotMetalRateKgS() {
        return hmRate;
    }

    /** Производительность, т/(м³·сут) физ. */
    public double productivityTM3Day() {
        return productivity;
    }

    /** Колошниковый газ, нм³/с физ. */
    public double topGasNm3S() {
        return topGasRate;
    }

    /** Состав колошникового газа — объёмные доли в порядке {@link FuelGas}. */
    public void topGasComposition(double[] out) {
        double sum = 0;
        for (double g : topGas) sum += g;
        for (int k = 0; k < out.length && k < topGas.length; k++) out[k] = sum > 0 ? topGas[k] / sum : 0;
    }

    /** Колошниковый газ как топливо воздухонагревателей и котлов (07 §7.8.1); {@code null} — газа нет. */
    public FuelSpec topGasFuel() {
        if (topGasRate <= 0) return null;
        double[] x = new double[topGas.length];
        topGasComposition(x);
        return FuelSpec.fromGasComposition("blast_furnace_gas", x);
    }

    /** Давление дутья у фурм, Па изб. (перепад столба ∝ V̇^1,8; при подвисании ×1,3). */
    public double blastPressurePa() {
        double r = Math.max(0, blastFrac);
        double dp = COLUMN_DP_PA * Math.pow(r, 1.8) * (state == BlastFurnaceState.HANGING ? HANG_DP_FACTOR : 1);
        return r > 0 ? TOP_PRESSURE_PA + dp : 0;
    }

    public double ironKg() {
        return ironKg;
    }

    public double slagKg() {
        return slagKg;
    }

    public double slagBasicity() {
        return slagSiO2 > 0 ? slagCaO / slagSiO2 : 0;
    }

    /** S чугуна в горне. */
    public double ironSulfurFrac() {
        return ironKg > 0 ? ironS / ironKg : 0;
    }

    public double ironSiliconFrac() {
        return ironKg > 0 ? ironSi / ironKg : 0;
    }

    /** Уровень жидкого над лещадью, м. */
    public double liquidLevelM() {
        return (ironKg / RHO_IRON + slagKg / RHO_SLAG) / spec.hearthAreaM2();
    }

    /** Объём шихты в шахте, м³. */
    public double stockVolumeM3() {
        double v = 0;
        for (int k = 0; k < count; k++) {
            int i = (head + k) % MAX_LAYERS;
            v += ore[i] / 1800 + coke[i] / 500 + lime[i] / 1500;
        }
        return v;
    }

    public int layerCount() {
        return count;
    }

    /** Руда нижнего слоя, кг (для проверки FIFO). */
    public double bottomOreKg() {
        return count > 0 ? ore[head] : 0;
    }

    public boolean tapholeOpen() {
        return tapOpen;
    }

    public double tapholeDiameterM() {
        return tapholeD;
    }

    /** Износ футеровки горна 0…1. */
    public double liningWear() {
        return wear;
    }

    /** Перефутеровка горна после прорыва: печь холодная и пустая. */
    public void relineHearth() {
        wear = 0;
        clearHearth();
        head = count = 0;
        hearthK = T0;
        tapOpen = false;
        state = BlastFurnaceState.COLD;
    }

    public int burntTuyeres() {
        return burntTuyeres;
    }

    /** Прогоревшая фурма течёт в горн ({@code BF_TUYERE_WATER_LEAK}). */
    public boolean tuyereLeak() {
        return tuyereLeak;
    }

    public void replaceTuyere() {
        tuyereLeak = false;
    }

    public BlastFurnaceSpec spec() {
        return spec;
    }
}
