package dev.denfry.promptcritical.sim.nonferrous;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Электролизёр Холла–Эру {@code reduction_pot} (08 §8.8.2): криолит-глинозёмный расплав 950–965 °C,
 * 2Al₂O₃ + 3C → 4Al + 3CO₂. Электротехнология — шаг в реальных секундах, Фарадей без ×72 (08 §8.0.3):
 * ṁ = η·I·M/(3F), E = 3FU/(M·η); T8.14: 150 кА, 0,93, 4,3 В → 46,8 кг/ч и 13,8 кВт·ч/кг.
 *
 * <p>Напряжение ванны U = {@value #BACK_EMF_V} В (ЭДС разложения с перенапряжением анода) + R·I; R подобрано по
 * T8.14. Тепло: джоулево I·U минус энтальпия процесса {@value #REACTION_KWH_PER_KG} кВт·ч/кг (теоретический расход с
 * нагревом реагентов, Grjotheim, Kvande, «Introduction to Aluminium Electrolysis», 1993); потери через кожух
 * пропорциональны перегреву над цехом, коэффициент подобран так, что при T8.14 ванна стоит на 960 °C. Теплоёмкость
 * ванны с металлом, подиной и футеровкой {@value #POT_HEAT_CAPACITY_J_K} Дж/К [BALANCE] умножена на
 * {@code aluminium.pot_heat_capacity_scale} = {@value #HEAT_CAPACITY_SCALE} (08 §8.0.2): без тока ванна доходит до
 * ликвидуса {@value #LIQUIDUS_K} К за ≈ 4 мин реальных (T8.20: 3–6 мин).
 *
 * <p>Автомат состояний §8.8.2: {@code COLD} → обжиг подины током → {@code STARTUP} → залит криолит, ванна горячая →
 * {@code NORMAL} ⇄ {@code ANODE_EFFECT} (Al₂O₃ ниже {@value #ANODE_EFFECT_ALUMINA}, конфиг
 * {@code aluminium.anode_effect_alumina_frac}); без тока дольше {@value #FREEZE_GRACE_S} с ({@code freeze_grace_s}) —
 * {@code FREEZING}, ниже ликвидуса — {@code FROZEN} (капремонт: подовые блоки и огнеупор); износ подины —
 * {@code CATHODE_FAILURE} (металл уходит сквозь подину).
 *
 * <p>При анодном эффекте напряжение +{@value #ANODE_EFFECT_V} В (§8.8.2: 20–40 В), выход по току падает, идут
 * перфторуглероды: CF₄ — по методу наклона IPCC 2006 (т. 3, гл. 4.4, табл. 4.16, ванны с обожжёнными анодами и
 * точечным питанием): {@value #CF4_SLOPE} кг CF₄ на тонну Al на минуту анодного эффекта в сутки ванны; C₂F₆ —
 * {@value #C2F6_PER_CF4} от CF₄.
 */
public final class ReductionPot {
    public enum State { COLD, STARTUP, NORMAL, ANODE_EFFECT, FREEZING, FROZEN, CATHODE_FAILURE }

    public static final double F = 96485.33, M_AL = 0.026982;
    /** Точка T8.14. */
    public static final double NOMINAL_A = 150e3, NOMINAL_EFFICIENCY = 0.93, NOMINAL_V = 4.3, NOMINAL_K = 1233.15;
    public static final double MIN_A = 10e3, MAX_A = 500e3;
    public static final double BACK_EMF_V = 1.65, RESISTANCE_OHM = (NOMINAL_V - BACK_EMF_V) / NOMINAL_A;
    public static final double REACTION_KWH_PER_KG = 6.34, AMBIENT_K = 298.15;
    public static final double LOSS_W_K = (NOMINAL_A * NOMINAL_V - nominalRateKgS() * REACTION_KWH_PER_KG * 3.6e6) / (NOMINAL_K - AMBIENT_K);
    public static final double POT_HEAT_CAPACITY_J_K = 80e6, HEAT_CAPACITY_SCALE = 0.05;
    public static final double LIQUIDUS_K = 1213.15, BAKED_K = 1173.15, RUN_K = 1223.15;
    public static final double FREEZE_GRACE_S = 60;
    /** Глинозём, кг на кг Al (§8.8.2: 1,93 т на т, со сдувом), аноды 0,40–0,45 т/т, фториды 15–25 кг/т. */
    public static final double ALUMINA_PER_AL = 1.93, ANODE_PER_AL = 0.42, FLUORIDE_PER_AL = 0.020;
    /** Расплав: масса электролита рабочей ванны и пусковая заливка криолита, кг [BALANCE]. */
    public static final double BATH_KG = 8000, BATH_START_KG = 1000;
    public static final double ALUMINA_TARGET = 0.03, ANODE_EFFECT_ALUMINA = 0.018, ANODE_EFFECT_END = 0.02, ALUMINA_MAX = 0.05;
    /** Точечный питатель, кг/с. */
    public static final double FEEDER_KG_S = 0.5;
    public static final double ANODE_EFFECT_V = 30, ANODE_EFFECT_EFFICIENCY = 0.5, LOW_FLUORIDE_EFFICIENCY = 0.85;
    public static final double CF4_SLOPE = 0.143, C2F6_PER_CF4 = 0.121;
    /** Ресурс подины, А·с: 200 ч реальных на 150 кА [BALANCE]; замораживание — ещё десятая часть ресурса. */
    public static final double CATHODE_LIFE_AS = NOMINAL_A * 200 * 3600, FREEZE_WEAR = 0.1;

    private State state = State.COLD;
    private double tempK = AMBIENT_K;
    private double currentSetA = NOMINAL_A;
    private double bathKg, aluminaKg, anodeKg, fluorideKg, metalKg;
    private double aluminaTarget = ALUMINA_TARGET;
    private double cathodeWear, noCurrentS;
    private double lastAmps, lastVoltage, lastEfficiency, lastAlKgS, lastCf4KgS, lastC2f6KgS;
    private double totalAlKg, totalCf4Kg, totalC2f6Kg, totalDcJ;

    /** Выпуск при T8.14, кг/с. */
    public static double nominalRateKgS() {
        return rateKgS(NOMINAL_A, NOMINAL_EFFICIENCY);
    }

    /** Закон Фарадея для алюминия, кг/с. */
    public static double rateKgS(double amps, double efficiency) {
        return efficiency * amps * M_AL / (3 * F);
    }

    /** Удельный расход постоянного тока, Дж/кг. */
    public static double specificEnergyJPerKg(double volts, double efficiency) {
        return 3 * F * volts / (M_AL * efficiency);
    }

    /** Напряжение ванны без анодного эффекта, В. */
    public static double cellVoltage(double amps) {
        return amps > 0 ? BACK_EMF_V + RESISTANCE_OHM * amps : 0;
    }

    public State state() {
        return state;
    }

    /** Ванна проводит ток серии (замёрзшая и пробитая шунтируются). */
    public boolean conducting() {
        return state != State.FROZEN && state != State.CATHODE_FAILURE && anodeKg > 0;
    }

    public double currentSetA() {
        return currentSetA;
    }

    public void setCurrentSetA(double a) {
        currentSetA = Math.clamp(a, MIN_A, MAX_A);
    }

    public double aluminaTarget() {
        return aluminaTarget;
    }

    public void setAluminaTarget(double f) {
        aluminaTarget = Math.clamp(f, 0, ALUMINA_MAX);
    }

    /** Напряжение, которое ванна покажет при токе {@code amps} в нынешнем состоянии, В. */
    public double voltageAt(double amps) {
        if (!conducting() || amps <= 0) return 0;
        return cellVoltage(amps) + (state == State.ANODE_EFFECT ? ANODE_EFFECT_V : 0);
    }

    /** Мощность постоянного тока при заданном токе, Вт. */
    public double dcDemandW(double amps) {
        return amps * voltageAt(amps);
    }

    /**
     * Масса электролита, кг: после пусковой заливки — рабочая {@value #BATH_KG} (остальное — дроблёный электролит и
     * сода, которые в игре не носят), до неё — сколько залито.
     */
    public double bathMassKg() {
        return bathCharged() ? BATH_KG : bathKg;
    }

    public double aluminaFraction() {
        double b = bathMassKg();
        return b > 0 ? aluminaKg / b : 0;
    }

    /** Глинозём, который питатель возьмёт из бункера сейчас, кг. */
    public double aluminaWanted() {
        return Math.max(0, aluminaTarget * bathMassKg() - aluminaKg);
    }

    public void addAlumina(double kg) {
        aluminaKg += Math.max(0, kg);
    }

    public void addAnodeKg(double kg) {
        anodeKg += Math.max(0, kg);
    }

    public void addFluorideKg(double kg) {
        fluorideKg += Math.max(0, kg);
    }

    /** Криолит: сперва пусковая заливка ванны, затем подпитка фторидами. */
    public void addCryoliteKg(double kg) {
        double toBath = Math.min(Math.max(0, kg), Math.max(0, BATH_START_KG - bathKg));
        bathKg += toBath;
        fluorideKg += Math.max(0, kg) - toBath;
    }

    public boolean bathCharged() {
        return bathKg >= BATH_START_KG;
    }

    /**
     * Шаг на {@code dtS} реальных секунд при токе серии {@code amps}, А. Темп кормления глинозёмом — не быстрее
     * {@value #FEEDER_KG_S} кг/с, его отдаёт мир через {@link #addAlumina} до шага.
     */
    public void step(double dtS, double amps) {
        double a = conducting() ? Math.max(0, amps) : 0;
        if (state == State.FROZEN || state == State.CATHODE_FAILURE) a = 0;
        double eff = 0;
        boolean producing = state == State.NORMAL || state == State.ANODE_EFFECT || state == State.FREEZING;
        if (producing && a > 0) {
            eff = state == State.ANODE_EFFECT ? ANODE_EFFECT_EFFICIENCY : fluorideKg > 0 ? NOMINAL_EFFICIENCY : LOW_FLUORIDE_EFFICIENCY;
            // глинозёма в расплаве может не хватить и на этот шаг
            double rate = rateKgS(a, eff);
            double maxByAlumina = aluminaKg / ALUMINA_PER_AL / Math.max(dtS, 1e-9);
            if (rate > maxByAlumina) rate = maxByAlumina;
            lastAlKgS = rate;
        } else lastAlKgS = 0;
        double alKg = lastAlKgS * dtS;
        aluminaKg = Math.max(0, aluminaKg - alKg * ALUMINA_PER_AL);
        anodeKg = Math.max(0, anodeKg - alKg * ANODE_PER_AL);
        fluorideKg = Math.max(0, fluorideKg - alKg * FLUORIDE_PER_AL);
        metalKg += alKg;
        totalAlKg += alKg;
        lastAmps = a;
        lastVoltage = voltageAt(a);
        lastEfficiency = lastAlKgS > 0 ? eff : 0;
        totalDcJ += a * lastVoltage * dtS;
        cathodeWear += a * dtS / CATHODE_LIFE_AS;
        // тепло: джоуль минус энтальпия процесса минус потери
        double heatW = a * lastVoltage - lastAlKgS * REACTION_KWH_PER_KG * 3.6e6 - LOSS_W_K * (tempK - AMBIENT_K);
        tempK = Math.max(AMBIENT_K, tempK + heatW * dtS / (POT_HEAT_CAPACITY_J_K * HEAT_CAPACITY_SCALE));
        // перфторуглероды во время анодного эффекта
        if (state == State.ANODE_EFFECT && a > 0) {
            double tonnesPerDay = rateKgS(a, NOMINAL_EFFICIENCY) * 86400 / 1000;
            lastCf4KgS = CF4_SLOPE * tonnesPerDay / 60;
            lastC2f6KgS = lastCf4KgS * C2F6_PER_CF4;
        } else {
            lastCf4KgS = 0;
            lastC2f6KgS = 0;
        }
        totalCf4Kg += lastCf4KgS * dtS;
        totalC2f6Kg += lastC2f6KgS * dtS;
        transition(dtS, a);
    }

    private void transition(double dtS, double a) {
        switch (state) {
            case COLD -> {
                if (tempK >= BAKED_K) state = State.STARTUP;
            }
            case STARTUP -> {
                if (tempK < BAKED_K - 50) state = State.COLD;
                else if (bathCharged() && tempK >= RUN_K) state = State.NORMAL;
            }
            case NORMAL, ANODE_EFFECT, FREEZING -> {
                if (cathodeWear >= 1) {
                    state = State.CATHODE_FAILURE;
                    metalKg = 0;
                    return;
                }
                if (tempK <= LIQUIDUS_K) {
                    state = State.FROZEN;
                    cathodeWear += FREEZE_WEAR;
                    return;
                }
                if (a <= 0) {
                    noCurrentS += dtS;
                    if (noCurrentS > FREEZE_GRACE_S) state = State.FREEZING;
                    return;
                }
                noCurrentS = 0;
                double x = aluminaFraction();
                if (state == State.ANODE_EFFECT) {
                    if (x >= ANODE_EFFECT_END) state = State.NORMAL;
                } else state = x < ANODE_EFFECT_ALUMINA ? State.ANODE_EFFECT : State.NORMAL;
            }
            default -> {
            }
        }
    }

    /** Металл, который можно выбрать вакуумным ковшом, не оголяя подину: всё сверх слоя {@code heelKg}. */
    public double tappableKg(double heelKg) {
        return Math.max(0, metalKg - heelKg);
    }

    public void tap(double kg) {
        metalKg = Math.max(0, metalKg - Math.max(0, kg));
    }

    /** Капитальный ремонт замёрзшей или пробитой ванны: новая подина, пустая холодная ванна. */
    public boolean reline() {
        if (state != State.FROZEN && state != State.CATHODE_FAILURE) return false;
        state = State.COLD;
        tempK = AMBIENT_K;
        bathKg = aluminaKg = fluorideKg = metalKg = 0;
        cathodeWear = 0;
        noCurrentS = 0;
        return true;
    }

    public double tempK() {
        return tempK;
    }

    /** Для испытаний и пуска из готового состояния. */
    public void forceState(State s, double tempK) {
        this.state = s;
        this.tempK = tempK;
    }

    public double bathKg() {
        return bathKg;
    }

    public double aluminaKg() {
        return aluminaKg;
    }

    public double anodeKg() {
        return anodeKg;
    }

    public double fluorideKg() {
        return fluorideKg;
    }

    public double metalKg() {
        return metalKg;
    }

    public double cathodeWear() {
        return cathodeWear;
    }

    public double lastAmps() {
        return lastAmps;
    }

    public double lastVoltage() {
        return lastVoltage;
    }

    public double lastEfficiency() {
        return lastEfficiency;
    }

    public double lastAlKgS() {
        return lastAlKgS;
    }

    public double lastCf4KgS() {
        return lastCf4KgS;
    }

    public double lastC2f6KgS() {
        return lastC2f6KgS;
    }

    public double totalAlKg() {
        return totalAlKg;
    }

    public double totalCf4Kg() {
        return totalCf4Kg;
    }

    public double totalC2f6Kg() {
        return totalC2f6Kg;
    }

    /** Удельный расход постоянного тока за всё время, кВт·ч/кг; NaN — металла ещё нет. */
    public double specificKwhPerKg() {
        return totalAlKg > 0 ? totalDcJ / 3.6e6 / totalAlKg : Double.NaN;
    }

    public void save(StateWriter out) {
        out.putString("state", state.name());
        out.putDouble("t", tempK);
        out.putDouble("set", currentSetA);
        out.putDouble("bath", bathKg);
        out.putDouble("al2o3", aluminaKg);
        out.putDouble("anode", anodeKg);
        out.putDouble("fluoride", fluorideKg);
        out.putDouble("metal", metalKg);
        out.putDouble("target", aluminaTarget);
        out.putDouble("wear", cathodeWear);
        out.putDouble("off", noCurrentS);
        out.putDouble("made", totalAlKg);
        out.putDouble("cf4", totalCf4Kg);
        out.putDouble("c2f6", totalC2f6Kg);
        out.putDouble("dcj", totalDcJ);
    }

    public void load(StateReader in) {
        try {
            state = State.valueOf(in.has("state") ? in.getString("state") : State.COLD.name());
        } catch (IllegalArgumentException e) {
            state = State.COLD;
        }
        tempK = in.optDouble("t", AMBIENT_K);
        setCurrentSetA(in.optDouble("set", NOMINAL_A));
        bathKg = in.optDouble("bath", 0);
        aluminaKg = in.optDouble("al2o3", 0);
        anodeKg = in.optDouble("anode", 0);
        fluorideKg = in.optDouble("fluoride", 0);
        metalKg = in.optDouble("metal", 0);
        setAluminaTarget(in.optDouble("target", ALUMINA_TARGET));
        cathodeWear = in.optDouble("wear", 0);
        noCurrentS = in.optDouble("off", 0);
        totalAlKg = in.optDouble("made", 0);
        totalCf4Kg = in.optDouble("cf4", 0);
        totalC2f6Kg = in.optDouble("c2f6", 0);
        totalDcJ = in.optDouble("dcj", 0);
    }
}
