package dev.denfry.promptcritical.sim.chlor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Серия диафрагменных электролизёров хлора: {@value #CELLS} ванн последовательно на одном выпрямителе. Анод — ОРТА
 * (оксид рутения на титане), катод — стальная сетка с асбестовой диафрагмой. Очищенный рассол (310 г/л NaCl) течёт с
 * анода через диафрагму к катоду; на аноде выделяется хлор, на катоде — водород, в католите — электролитическая
 * щёлочь: 10–12 % NaOH и 14–16 % NaCl.
 *
 * <p>Почему диафрагма, а не мембрана: эпоха I3 — 1950–1970-е, советский хлор того времени — диафрагменные ванны
 * БГК (Стерлитамак, Усолье-Сибирское, Новомосковск); перфторированные мембраны (Nafion, 1975) пришли позже.
 * Ртутный метод с амальгамным катодом давал чистую 50 %-ю щёлочь, но и ртуть в сбросах — здесь только упоминание.
 *
 * <p>Напряжение ванны — сумма (Florea, «Electrochemical Engineering of Chlor-Alkali»; O'Brien, Bommaraju, Hine,
 * «Handbook of Chlor-Alkali Technology», 2005, т. I, гл. 4–5):
 * <ul>
 *   <li>напряжение разложения E⁰ ≈ 2,19 В при 90 °C (2,25 В при 25 °C, −0,9 мВ/К);</li>
 *   <li>перенапряжение хлора на ОРТА по Тафелю, b = 40 мВ/дек, ≈ 0,05 В при 2 кА/м²;</li>
 *   <li>перенапряжение водорода на стали, b = 100 мВ/дек, ≈ 0,33 В;</li>
 *   <li>омика электролита с газом, диафрагмы и шин — 0,42 В на кА/м² при 90 °C, падает на 1,2 %/К.</li>
 * </ul>
 * При 2 кА/м² это 3,4 В; выход по току 95–96 %, удельный расход 2,6–2,8 МВт·ч постоянного тока на тонну хлора —
 * диафрагменный диапазон в O'Brien, табл. 4.x (мембрана — 2,2–2,5).
 *
 * <p>Выход по току падает с концентрацией щёлочи: ионы OH⁻ уходят через диафрагму к аноду и дают гипохлорит, хлорат
 * и кислород. Недостаток рассола поднимает щёлочь — и выход падает. Если анолит опустится до верха диафрагмы, водород
 * проходит в хлор; выше 4 % водорода хлоро-водородная смесь взрывается — это предел, на который настраивают защиту.
 *
 * <p>Жёсткость рассола (Ca, Mg) осаждается в порах диафрагмы гидроксидами: растёт сопротивление и падает выход;
 * диафрагму меняют (переосаждают асбест) — в жизни раз в 6–12 месяцев на очищенном рассоле.
 */
public final class DiaphragmCellRoom {
    public static final int CELLS = 20;
    /** Рабочая поверхность анода одной ванны, м² [BALANCE: ванна БГК-50 — около 34 м² на 50 кА]. */
    public static final double ANODE_M2 = 15;
    public static final double MIN_KA = 5, MAX_KA = 40, NOMINAL_KA = 30;
    public static final double F = 96485.33, M_CL2 = 0.070906, M_H2 = 0.002016, M_NAOH = 0.039997, M_NACL = 0.058443,
            M_H2O = 0.018015;
    /** Термонейтральное напряжение: выше него ванна греется. */
    public static final double THERMONEUTRAL_V = 2.31;
    public static final double E0_363 = 2.19, E0_SLOPE = -0.0009, ANODE_TAFEL = 0.040, ANODE_I0 = 100, CATHODE_TAFEL = 0.100,
            CATHODE_I0 = 1, OHMIC_OHM_M2 = 4.2e-4, OHMIC_PER_K = -0.012;
    /** Очищенный рассол: 310 г/л NaCl, 1200 кг/м³. */
    public static final double BRINE_NACL_KG_PER_L = 0.310, BRINE_KG_PER_L = 1.20;
    /** Щёлочь, на которую рассчитан расход рассола. */
    public static final double TARGET_NAOH = 0.11;
    /** Водород в хлоре, при котором срабатывает защита (нижний предел взрываемости ≈ 4 %). */
    public static final double H2_IN_CL2_LIMIT = 0.04;
    /** Прирост сопротивления диафрагмы на кг осевших Ca + Mg (на серию) [BALANCE]. */
    public static final double FOULING_PER_KG = 0.08;
    /** Ресурс диафрагмы по прошедшему заряду, А·ч на ванну, — около 9 месяцев на 30 кА [BALANCE]. */
    public static final double LIFE_AH = 30e3 * 24 * 270;

    private boolean on;
    private double currentKa = NOMINAL_KA, tempK = 298.15, fouling, ageAh;
    // последний шаг
    private double voltageV, efficiency, naohFraction, h2InCl2, cl2KgS, h2KgS, naohKgS, liquorKgS, brineLs, dcW, brineFraction;
    private boolean tripped;

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
        if (on) tripped = false;
    }

    public double currentKa() {
        return currentKa;
    }

    public void setCurrentKa(double ka) {
        currentKa = Math.clamp(ka, MIN_KA, MAX_KA);
    }

    public double tempK() {
        return tempK;
    }

    public double fouling() {
        return fouling;
    }

    public double ageFraction() {
        return ageAh / LIFE_AH;
    }

    public boolean tripped() {
        return tripped;
    }

    /** Замена диафрагм: сопротивление и ресурс — как у новой. */
    public void rebuildDiaphragms() {
        fouling = 0;
        ageAh = 0;
    }

    /** Напряжение разложения при температуре, В. */
    public static double reversibleV(double tempK) {
        return E0_363 + E0_SLOPE * (tempK - 363.15);
    }

    /**
     * Напряжение одной ванны, В.
     *
     * @param ampsPerM2 плотность тока, А/м²
     * @param fouling   прирост сопротивления диафрагмы, доли
     */
    public static double cellVoltage(double ampsPerM2, double tempK, double fouling) {
        if (ampsPerM2 <= 0) return reversibleV(tempK);
        double anode = ANODE_TAFEL * Math.log10(Math.max(1, ampsPerM2 / ANODE_I0));
        double cathode = CATHODE_TAFEL * Math.log10(Math.max(1, ampsPerM2 / CATHODE_I0));
        double r = OHMIC_OHM_M2 * Math.max(0.6, 1 + OHMIC_PER_K * (tempK - 363.15)) * (1 + fouling);
        return reversibleV(tempK) + anode + cathode + r * ampsPerM2;
    }

    /** Выход хлора по току при массовой доле щёлочи в католите. */
    public static double currentEfficiency(double naohFraction, double fouling, double ageFraction) {
        double ce = 0.975 - 0.25 * Math.max(0, naohFraction - 0.08) - 0.03 * Math.min(1, fouling) - 0.02 * Math.min(1.5, ageFraction);
        return Math.clamp(ce, 0.5, 0.98);
    }

    /** Удельный расход постоянного тока, Дж на кг хлора. */
    public static double specificEnergyJPerKg(double voltageV, double efficiency) {
        return voltageV * 2 * F / (M_CL2 * efficiency);
    }

    /** Рассол, л/с, на серию при токе для щёлочи {@link #TARGET_NAOH}. */
    public static double brineDemandLs(double currentKa) {
        double naohMol = currentKa * 1000 * 0.96 / F * CELLS;
        double naohKg = naohMol * M_NAOH;
        return (naohKg / TARGET_NAOH + naohMol / 2 * (M_CL2 + M_H2)) / BRINE_KG_PER_L;
    }

    /**
     * Шаг серии.
     *
     * @param dtS            шаг, с
     * @param dcAmpsLimit    ток, который может дать выпрямитель, А
     * @param brineAvailableL рассол, доступный на этом шаге, л
     * @param hardnessMgL    Ca + Mg в рассоле, мг/л
     * @return рассол, израсходованный на шаге, л
     */
    public double step(double dtS, double dcAmpsLimit, double brineAvailableL, double hardnessMgL) {
        double amps = on && !tripped ? Math.min(currentKa * 1000, Math.max(0, dcAmpsLimit)) : 0;
        double j = amps / ANODE_M2;
        double demandL = amps > 0 ? brineDemandLs(amps / 1000) * dtS : 0;
        brineFraction = demandL > 0 ? Math.min(1, brineAvailableL / demandL) : 1;
        double usedL = demandL * brineFraction;
        // щёлочь в католите: меньше рассола — крепче щёлочь
        naohFraction = amps > 0 ? Math.min(0.35, TARGET_NAOH / Math.max(0.05, brineFraction)) : 0;
        efficiency = amps > 0 ? currentEfficiency(naohFraction, fouling, ageFraction()) : 0;
        voltageV = cellVoltage(j, tempK, fouling);
        // анолит ниже верха диафрагмы — водород в хлор
        h2InCl2 = amps > 0 ? 0.001 + Math.max(0, 0.6 - brineFraction) * 0.25 : 0;
        if (h2InCl2 > H2_IN_CL2_LIMIT) tripped = true;
        double cellsAmps = amps * CELLS;
        double cl2Mol = cellsAmps * efficiency / (2 * F);
        cl2KgS = cl2Mol * M_CL2;
        h2KgS = cellsAmps / (2 * F) * M_H2;
        naohKgS = cellsAmps * efficiency / F * M_NAOH;
        liquorKgS = naohFraction > 0 ? naohKgS / naohFraction : 0;
        brineLs = dtS > 0 ? usedL / dtS : 0;
        dcW = cellsAmps * voltageV;
        // тепло: выше термонейтрального — в электролит; рассол уносит его, равновесие ≈ 90–95 °C на номинале
        double heatW = Math.max(0, voltageV - THERMONEUTRAL_V) * cellsAmps;
        double nominalHeat = (cellVoltage(NOMINAL_KA * 1000 / ANODE_M2, 363.15, 0) - THERMONEUTRAL_V) * NOMINAL_KA * 1000 * CELLS;
        double target = 298.15 + 68 * heatW / nominalHeat;
        tempK += (Math.min(378, target) - tempK) * Math.min(1, dtS / 3600);
        fouling += hardnessMgL * 1e-6 * usedL * FOULING_PER_KG;
        ageAh += amps * dtS / 3600;
        return usedL;
    }

    public double voltageV() {
        return voltageV;
    }

    public double efficiency() {
        return efficiency;
    }

    public double naohFraction() {
        return naohFraction;
    }

    public double h2InCl2() {
        return h2InCl2;
    }

    public double cl2KgS() {
        return cl2KgS;
    }

    public double h2KgS() {
        return h2KgS;
    }

    public double naohKgS() {
        return naohKgS;
    }

    /** Электролитическая щёлочь (католит), кг/с. */
    public double liquorKgS() {
        return liquorKgS;
    }

    public double brineLs() {
        return brineLs;
    }

    public double brineFraction() {
        return brineFraction;
    }

    /** Мощность постоянного тока серии, Вт. */
    public double dcW() {
        return dcW;
    }

    /** Мощность, которую серия возьмёт при токе {@code amps}, Вт (для уставки выпрямителя). */
    public double dcDemandW(double amps) {
        return amps * CELLS * cellVoltage(amps / ANODE_M2, tempK, fouling);
    }

    public void save(StateWriter out) {
        out.putBoolean("on", on);
        out.putDouble("ka", currentKa);
        out.putDouble("t", tempK);
        out.putDouble("foul", fouling);
        out.putDouble("age", ageAh);
        out.putBoolean("trip", tripped);
    }

    public void load(StateReader in) {
        on = in.optInt("on", 0) != 0;
        setCurrentKa(in.optDouble("ka", NOMINAL_KA));
        tempK = in.optDouble("t", 298.15);
        fouling = in.optDouble("foul", 0);
        ageAh = in.optDouble("age", 0);
        tripped = in.optInt("trip", 0) != 0;
    }
}
