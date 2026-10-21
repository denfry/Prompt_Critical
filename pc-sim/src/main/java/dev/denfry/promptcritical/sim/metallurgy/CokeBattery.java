package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.combustion.CombustionResult;
import dev.denfry.promptcritical.sim.combustion.Combustor;
import dev.denfry.promptcritical.sim.combustion.FuelGas;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.mining.CoalRank;

/**
 * Коксовая батарея {@code coke_oven} (08 §8.4, R16): 3–12 камер между динасовыми простенками, общий обогрев
 * {@link Combustor} на обратном коксовом или доменном газе. Лумпед-модель SLOW:
 * <ul>
 *   <li>простенки — одна температура T_ст: C·dT/dt = η_рег·Q_гор − Σ q_кокс − k_пот·(T − T₀); η_рег = 0,75
 *       (регенераторы) [допущение];</li>
 *   <li>камера — автомат §8.4.1 и степень коксования p: dp/dt = f(T)/τ_кокс, τ_кокс = k_c·b²·(1 + 0,004·(1573 −
 *       T))·(1 + 0,015·(W − 8 %)); W = 8 % — влага шихты, к которой отнесено k_c (моё: в GDD опорная влага не
 *       названа); f(T) линейно растёт от 0 при 950 °C до 1 при 1100 °C — холоднее центр не дойдёт до 950 °C (моё);</li>
 *   <li>тепло коксования 1,6 МДж/кг сухой шихты + 2,6 МДж/кг влаги (= 2,4 ГДж/т при η_рег, §8.4.2), газ и
 *       продукты улавливания — пропорционально dp.</li>
 * </ul>
 * Выход: Y = 1 − 0,9·V^d, A_к = A^d/Y, S_к = 0,65·S/Y, M₂₅ — §8.4.2; газ 1,29·V^d нм³/кг сухой шихты (320 нм³/т при
 * V^daf 27 %, A^d 8 %), смола/бензол/NH₃ — средние §8.4.2, отнесённые к V^d; H₂S — остаток серы шихты
 * (35 %), надсмольная вода — влага шихты + 3 % пирогенетической (моё). Мелочь {@code coke_breeze} — 4 % кокса.
 *
 * <p>Динас: пока простенки ни разу не нагрелись до 1000 °C, нагрев быстрее (1000 − 20)/{@code heatup_days_phys}
 * копит повреждение (200 К превышения — разрушение) [BALANCE]; разогретая батарея, остывшая ниже
 * {@code min_wall_temp_k}, разрушается сразу ({@code REFRACTORY_DAMAGE}).
 */
public final class CokeBattery {
    /** Опорная температура простенков k_c, К. */
    public static final double REF_WALL_K = 1573.15;
    /** +4 % времени на каждые −10 К. */
    public static final double TEMP_COEFF_PER_K = 0.004;
    /** +1,5 % на процент влаги сверх опорной. */
    public static final double MOISTURE_COEFF = 0.015, REF_MOISTURE = 0.08;
    /** Центр пирога готов; полная скорость коксования с этой температуры простенка, К. */
    public static final double CENTER_READY_K = 1223.15, FULL_RATE_WALL_K = 1373.15;
    /** Передержка без пережога, с физ. */
    public static final double OVERCOOK_GRACE_S = 7_200;
    /** Пережог за час сверх допуска: −M₂₅ и угар кокса [BALANCE]. */
    public static final double OVERCOOK_M25_PER_H = 1.0, OVERCOOK_BURNOFF_PER_H = 0.005;
    /** Коксовый газ на кг сухой шихты на единицу V^d, нм³. */
    public static final double GAS_NM3_PER_KG_VD = 1.29;
    /** Мелочь &lt; 25 мм от кокса. */
    public static final double BREEZE_FRACTION = 0.04;
    /** Тепло коксования: сухая масса и влага, Дж/кг. */
    public static final double DRY_HEAT_J_KG = 1.6e6, WATER_HEAT_J_KG = 2.6e6;
    public static final double HEATING_EFFICIENCY = 0.75;
    /** Теплоёмкость простенков на камеру, Дж/К, и их потери, Вт/К [BALANCE]. */
    public static final double WALL_HEAT_CAPACITY_J_K = 5e7, WALL_LOSS_W_K = 19.5;
    /** Простенки «разогреты» — с этого момента их нельзя охлаждать, К. */
    public static final double WALL_HOT_K = 1273.15;
    /** Превышение нагрева, разрушающее неразогретый динас, К. */
    public static final double REFRACTORY_EXCESS_K = 200;
    /** Пековый кокс: выход, газ нм³/кг, зола и сера кокса. */
    public static final double PITCH_COKE_YIELD = 0.62, PITCH_GAS_NM3_KG = 0.15, PITCH_COKE_ASH = 0.002,
            PITCH_COKE_SULFUR = 0.005;
    /** Сера шихты, оставшаяся в коксе. */
    public static final double SULFUR_TO_COKE = 0.65;
    /** Продукты на кг сухой шихты при V^d = {@link #REF_VD}: смола, сырой бензол, NH₃; пирогенетическая вода. */
    public static final double REF_VD = 0.27 * 0.92, TAR_KG = 0.035, BENZOL_KG = 0.0095, AMMONIA_KG = 0.00275,
            PYRO_WATER_KG = 0.03;
    /** Остаточные летучие и состав органической массы кокса. */
    public static final double COKE_VDAF = 0.01, COKE_HDAF = 0.004, COKE_NDAF = 0.010, COKE_ODAF = 0.006;
    /** Коксовый газ (07 §7.8.1): H₂ 58, CH₄ 26, CO 6, C₂H₄ 3, CO₂ 2, N₂ 5 % — 18,1 МДж/нм³. */
    public static final FuelSpec COKE_OVEN_GAS = FuelSpec.fromGasComposition("coke_oven_gas", cokeOvenGas());

    private static final double T0 = 293.15, H2S_PER_S = 34.08 / 32.06;

    private final IronmakingConfig cfg;
    private final int ovens;
    private final double designKg;
    private final CombustionResult burn = new CombustionResult();
    private IronmakingEvents events = IronmakingEvents.NONE;

    private final OvenState[] state;
    private final boolean[] pitch;
    private final double[] wet, dry, water, ash, sulfur, vdaf, daf, plastic, progress, readyS;
    private final double[] byproducts = new double[CokeByproduct.values().length];
    private double wallK, damage, fuelKgS, airKgS, gasRate, extraHeatW, demandW, lossW;
    private boolean hot, failed;

    /**
     * @param ovens        число камер, 3–12
     * @param chargeKg     проектная загрузка камеры, кг (6 т — эталон §8.0.3)
     * @param initialWallK начальная температура простенков, К; ≥ 1000 °C — батарея уже разогрета
     */
    public CokeBattery(IronmakingConfig cfg, int ovens, double chargeKg, double initialWallK) {
        if (ovens < 3 || ovens > 12) throw new IllegalArgumentException("ovens: " + ovens);
        if (!(chargeKg > 0)) throw new IllegalArgumentException("chargeKg");
        this.cfg = cfg;
        this.ovens = ovens;
        this.designKg = chargeKg;
        state = new OvenState[ovens];
        java.util.Arrays.fill(state, OvenState.EMPTY);
        pitch = new boolean[ovens];
        wet = new double[ovens];
        dry = new double[ovens];
        water = new double[ovens];
        ash = new double[ovens];
        sulfur = new double[ovens];
        vdaf = new double[ovens];
        daf = new double[ovens];
        plastic = new double[ovens];
        progress = new double[ovens];
        readyS = new double[ovens];
        wallK = initialWallK;
        hot = initialWallK >= WALL_HOT_K;
    }

    public void setEventSink(IronmakingEvents sink) {
        events = sink == null ? IronmakingEvents.NONE : sink;
    }

    /** Подача отопительного газа и воздуха, кг/с. */
    public void setHeating(double fuelKgS, double airKgS) {
        this.fuelKgS = Math.max(0, fuelKgS);
        this.airKgS = Math.max(0, airKgS);
    }

    /**
     * Тепло в простенки помимо газовых горелок, Вт (топки разогрева на твёрдом топливе — пока своего газа нет).
     */
    public void setExtraHeat(double watts) {
        extraHeatW = Math.max(0, watts);
    }

    /**
     * Время коксования τ = k_c·b²·(1 + 0,004·(1573 − T))·(1 + 0,015·(W − 8 %)), с физ.
     *
     * @param wallK    температура простенков, К
     * @param moisture влага шихты W^r
     */
    public double cokingTimeS(double wallK, double moisture) {
        double b = cfg.cokeChamberWidthM();
        double tf = Math.max(0.5, 1 + TEMP_COEFF_PER_K * (REF_WALL_K - wallK));
        double wf = Math.max(0.5, 1 + MOISTURE_COEFF * 100 * (moisture - REF_MOISTURE));
        return cfg.cokingConstantHM2() * b * b * 3600 * tf * wf;
    }

    /**
     * Загрузить шихту; при достижении проектной массы люки закрываются и камера коксуется.
     *
     * @return принятая масса, кг (0 — камера не принимает)
     */
    public double charge(int i, double kg, CoalAssay coal) {
        if (!(kg > 0) || coal == null || pitch[i]) return 0;
        if (state[i] != OvenState.EMPTY && state[i] != OvenState.CHARGING) return 0;
        double m = Math.min(kg, designKg - wet[i]);
        if (m <= 0) return 0;
        double d = m * (1 - coal.moistureFrac());
        wet[i] += m;
        dry[i] += d;
        water[i] += m - d;
        ash[i] += d * coal.ashDryFrac();
        sulfur[i] += d * coal.sulfurDryFrac();
        double dafKg = d * (1 - coal.ashDryFrac());
        daf[i] += dafKg;
        vdaf[i] += dafKg * coal.volatileDaf();
        plastic[i] += d * coal.plasticLayerMm();
        afterCharge(i);
        return m;
    }

    /** Загрузить каменноугольный пек — рецепт {@code pitch_coke} (08 §8.4.5); с углём не смешивается. */
    public double chargePitch(int i, double kg) {
        if (!(kg > 0)) return 0;
        if (state[i] == OvenState.CHARGING && !pitch[i] || state[i] != OvenState.EMPTY && state[i] != OvenState.CHARGING)
            return 0;
        double m = Math.min(kg, designKg - wet[i]);
        if (m <= 0) return 0;
        pitch[i] = true;
        wet[i] += m;
        dry[i] += m;
        afterCharge(i);
        return m;
    }

    private void afterCharge(int i) {
        state[i] = wet[i] >= designKg * (1 - 1e-9) ? OvenState.COKING : OvenState.CHARGING;
        progress[i] = 0;
    }

    /**
     * Выталкивание.
     *
     * @param force выталкивать неготовую камеру (без блокировки): пирог прилипает, поршень перегружен —
     *              {@code STICKER}, событие {@code COKE_PUSH_OVERLOAD}
     * @return кокс; {@code null} — камера не готова или пуста
     */
    public PushResult push(int i, boolean force) {
        OvenState s = state[i];
        if (s == OvenState.COKING || s == OvenState.STICKER) {
            if (force) {
                state[i] = OvenState.STICKER;
                events.cokePushOverload(i);
            }
            return null;
        }
        if (s != OvenState.READY) return null;
        double overH = Math.max(0, readyS[i] - OVERCOOK_GRACE_S) / 3600;
        double keep = Math.max(0.5, 1 - OVERCOOK_BURNOFF_PER_H * overH);
        PushResult r;
        if (pitch[i]) {
            double coke = dry[i] * PITCH_COKE_YIELD * keep;
            r = new PushResult(coke, 0, cokeAssay(PITCH_COKE_ASH, PITCH_COKE_SULFUR, Double.NaN), true, overH);
        } else {
            double a = ash[i] / dry[i], sd = sulfur[i] / dry[i];
            double y = cokeYield(i) * keep;
            double coke = dry[i] * y;
            double m25 = strengthM25(plastic[i] / dry[i], vdaf[i] / daf[i], a) - OVERCOOK_M25_PER_H * overH;
            CoalAssay assay = cokeAssay(Math.min(0.79, a / y), Math.min(0.14, SULFUR_TO_COKE * sd / y),
                    Math.max(30, Math.min(92, m25)));
            r = new PushResult(coke * (1 - BREEZE_FRACTION), coke * BREEZE_FRACTION, assay, false, overH);
        }
        clear(i);
        return r;
    }

    private void clear(int i) {
        state[i] = OvenState.EMPTY;
        pitch[i] = false;
        wet[i] = dry[i] = water[i] = ash[i] = sulfur[i] = vdaf[i] = daf[i] = plastic[i] = progress[i] = readyS[i] = 0;
    }

    /** Шаг SLOW, с физ.; {@code heating} — горелки простенков (null — обогрев снят). */
    public void stepSlow(double dt, Combustor heating) {
        double heatIn = 0;
        if (heating != null && fuelKgS > 0) {
            heating.burn(fuelKgS, airKgS, T0, T0, burn);
            heatIn = HEATING_EFFICIENCY * burn.heatReleaseW;
        }
        heatIn += extraHeatW;
        double demand = 0;
        gasRate = 0;
        for (int i = 0; i < ovens; i++) {
            OvenState s = state[i];
            if (s == OvenState.COKING || s == OvenState.STICKER) {
                double moisture = wet[i] > 0 ? water[i] / wet[i] : 0;
                double rate = rateFactor(wallK) / cokingTimeS(wallK, moisture);
                double dp = Math.min(rate * dt, 1 - progress[i]);
                progress[i] += dp;
                double perS = dp / dt;
                demand += (DRY_HEAT_J_KG * dry[i] + WATER_HEAT_J_KG * water[i]) * perS;
                gasRate += gasTotalNm3(i) * perS;
                if (!pitch[i]) accumulateByproducts(i, dp);
                if (progress[i] >= 1 - 1e-12) {
                    progress[i] = 1;
                    state[i] = OvenState.READY;
                    readyS[i] = 0;
                }
            } else if (s == OvenState.READY) {
                readyS[i] += dt;
            }
        }
        double loss = WALL_LOSS_W_K * ovens * (wallK - T0);
        demandW = demand;
        lossW = loss;
        double before = wallK;
        wallK += (heatIn - demand - loss) * dt / (WALL_HEAT_CAPACITY_J_K * ovens);
        checkRefractory(before, dt);
    }

    private void checkRefractory(double before, double dt) {
        if (failed) return;
        if (!hot) {
            double allowed = (WALL_HOT_K - T0) / (cfg.heatupDaysPhys() * 86_400) * dt;
            double excess = wallK - before - allowed;
            if (excess > 0) damage += excess / REFRACTORY_EXCESS_K;
            if (wallK >= WALL_HOT_K) hot = true;
        } else if (wallK < cfg.minWallTempK()) {
            damage = 1;
        }
        if (damage >= 1) {
            damage = 1;
            failed = true;
            events.refractoryFailed();
        }
    }

    private static double rateFactor(double wallK) {
        return Math.max(0, Math.min(1, (wallK - CENTER_READY_K) / (FULL_RATE_WALL_K - CENTER_READY_K)));
    }

    private double gasTotalNm3(int i) {
        if (pitch[i]) return PITCH_GAS_NM3_KG * dry[i];
        return GAS_NM3_PER_KG_VD * volatileDry(i) * dry[i];
    }

    private void accumulateByproducts(int i, double dp) {
        double scale = volatileDry(i) / REF_VD * dry[i] * dp;
        byproducts[CokeByproduct.TAR.ordinal()] += TAR_KG * scale;
        byproducts[CokeByproduct.CRUDE_BENZOL.ordinal()] += BENZOL_KG * scale;
        byproducts[CokeByproduct.AMMONIA.ordinal()] += AMMONIA_KG * scale;
        byproducts[CokeByproduct.HYDROGEN_SULFIDE.ordinal()] += (1 - SULFUR_TO_COKE) * sulfur[i] * H2S_PER_S * dp;
        byproducts[CokeByproduct.AMMONIA_LIQUOR.ordinal()] += (water[i] + PYRO_WATER_KG * dry[i]) * dp;
    }

    /** V^d шихты камеры. */
    private double volatileDry(int i) {
        return dry[i] > 0 ? vdaf[i] / dry[i] : 0;
    }

    /** Выход кокса Y = 1 − 0,9·V^d. */
    private double cokeYield(int i) {
        return 1 - 0.9 * volatileDry(i);
    }

    /**
     * M₂₅ = 88 − 1,2·|y − 17| − 0,8·|V^daf − 27| − 0,6·(A^d − 8)₊ (проценты, мм; [BALANCE], 08 §8.4.2).
     *
     * @param plasticMm  ȳ шихты, мм
     * @param volatileDaf V̄^daf, доля
     * @param ashDry     A^d шихты, доля
     */
    public static double strengthM25(double plasticMm, double volatileDaf, double ashDry) {
        return 88 - 1.2 * Math.abs(plasticMm - 17) - 0.8 * Math.abs(volatileDaf * 100 - 27)
                - 0.6 * Math.max(0, ashDry * 100 - 8);
    }

    /** Анализ кокса: сухой, летучих 1 %, органическая масса почти чистый углерод. */
    static CoalAssay cokeAssay(double ashDry, double sulfurDry, double m25) {
        return cokeAssay(ashDry, sulfurDry, m25, 0);
    }

    /** Анализ кокса с влагой (после мокрого тушения ≈ 4 %), для колош домны. */
    public static CoalAssay cokeAssay(double ashDry, double sulfurDry, double m25, double moisture) {
        double sDaf = sulfurDry / (1 - ashDry);
        double c = 1 - COKE_HDAF - COKE_NDAF - COKE_ODAF - sDaf;
        return new CoalAssay(moisture, ashDry, sulfurDry, 0, COKE_VDAF, 0, c, COKE_HDAF, COKE_ODAF, COKE_NDAF,
                CoalAssay.densityProfile(ashDry), m25, CoalRank.classify(COKE_VDAF, 0));
    }

    private static double[] cokeOvenGas() {
        double[] x = new double[FuelGas.values().length];
        x[FuelGas.H2.ordinal()] = 0.58;
        x[FuelGas.CH4.ordinal()] = 0.26;
        x[FuelGas.CO.ordinal()] = 0.06;
        x[FuelGas.C2H4.ordinal()] = 0.03;
        x[FuelGas.CO2.ordinal()] = 0.02;
        x[FuelGas.N2.ordinal()] = 0.05;
        return x;
    }

    /** Забрать накопленный продукт улавливания, кг. */
    public double takeByproduct(CokeByproduct p) {
        double v = byproducts[p.ordinal()];
        byproducts[p.ordinal()] = 0;
        return v;
    }

    public double byproductKg(CokeByproduct p) {
        return byproducts[p.ordinal()];
    }

    /** Выход коксового газа последнего шага, нм³/с физ. (состав — {@link #COKE_OVEN_GAS}). */
    public double gasMakeNm3S() {
        return gasRate;
    }

    public OvenState oven(int i) {
        return state[i];
    }

    public int ovenCount() {
        return ovens;
    }

    public double designChargeKg() {
        return designKg;
    }

    public double chargeKg(int i) {
        return wet[i];
    }

    /** Степень коксования камеры 0…1. */
    public double progress(int i) {
        return progress[i];
    }

    /**
     * Температура центра пирога, К (для пульта): прогрев до 100 °C, «полка» испарения влаги до середины цикла,
     * затем рост до 950 °C к концу; готовый пирог догоняет простенок с τ = 1 ч (моё).
     */
    public double centerTempK(int i) {
        OvenState s = state[i];
        if (s == OvenState.EMPTY || s == OvenState.CHARGING) return T0;
        if (s == OvenState.READY) {
            double t = Math.max(wallK, CENTER_READY_K);
            return t - (t - CENTER_READY_K) * Math.exp(-readyS[i] / 3600);
        }
        double p = progress[i];
        if (p < 0.1) return T0 + (373.15 - T0) * p / 0.1;
        if (p < 0.5) return 373.15;
        return 373.15 + (CENTER_READY_K - 373.15) * (p - 0.5) / 0.5;
    }

    /** Передержка готовой камеры, с физ. */
    public double readySeconds(int i) {
        return readyS[i];
    }

    /** Тепло коксования и потери простенков последнего шага, Вт (для регулятора обогрева). */
    public double coolingW() {
        return demandW + lossW;
    }

    /** Теплоёмкость простенков всей батареи, Дж/К. */
    public double wallHeatCapacityJK() {
        return WALL_HEAT_CAPACITY_J_K * ovens;
    }

    public double wallTempK() {
        return wallK;
    }

    /** Повреждение кладки 0…1. */
    public double refractoryDamage() {
        return damage;
    }

    /** Кладка разрушена ({@code REFRACTORY_DAMAGE}). */
    public boolean refractoryFailed() {
        return failed;
    }

    /** Простенки разогреты: охлаждать ниже {@code min_wall_temp_k} нельзя. */
    public boolean heatedUp() {
        return hot;
    }

    /** Последнее горение обогрева. */
    public CombustionResult heatingResult() {
        return burn;
    }

    // --- перестройка батареи и сохранение ---

    /**
     * Перенять у прежней батареи простенки (температура, повреждение, разогрев) и камеры по карте: камера {@code i}
     * новой батареи — камера {@code map[i]} прежней или пустая при −1. Шихта при другой проектной загрузке не
     * обрезается: камера коксует то, что в ней есть.
     */
    public void inherit(CokeBattery old, int[] map) {
        wallK = old.wallK;
        damage = old.damage;
        hot = old.hot;
        failed = old.failed;
        fuelKgS = old.fuelKgS;
        airKgS = old.airKgS;
        System.arraycopy(old.byproducts, 0, byproducts, 0, byproducts.length);
        for (int i = 0; i < ovens && i < map.length; i++) {
            int j = map[i];
            if (j < 0 || j >= old.ovens) continue;
            state[i] = old.state[j];
            pitch[i] = old.pitch[j];
            wet[i] = old.wet[j];
            dry[i] = old.dry[j];
            water[i] = old.water[j];
            ash[i] = old.ash[j];
            sulfur[i] = old.sulfur[j];
            vdaf[i] = old.vdaf[j];
            daf[i] = old.daf[j];
            plastic[i] = old.plastic[j];
            progress[i] = old.progress[j];
            readyS[i] = old.readyS[j];
        }
    }

    public void save(StateWriter out) {
        out.putDouble("wall_k", wallK);
        out.putDouble("damage", damage);
        out.putBoolean("hot", hot);
        out.putBoolean("failed", failed);
        out.putDouble("fuel_kg_s", fuelKgS);
        out.putDouble("air_kg_s", airKgS);
        out.putDoubles("byproducts", byproducts, byproducts.length);
        int[] st = new int[ovens];
        for (int i = 0; i < ovens; i++) st[i] = state[i].ordinal() | (pitch[i] ? 1 << 8 : 0);
        out.putInts("state", st, ovens);
        out.putDoubles("wet", wet, ovens);
        out.putDoubles("dry", dry, ovens);
        out.putDoubles("water", water, ovens);
        out.putDoubles("ash", ash, ovens);
        out.putDoubles("sulfur", sulfur, ovens);
        out.putDoubles("vdaf", vdaf, ovens);
        out.putDoubles("daf", daf, ovens);
        out.putDoubles("plastic", plastic, ovens);
        out.putDoubles("progress", progress, ovens);
        out.putDoubles("ready_s", readyS, ovens);
    }

    /** Загрузка в батарею того же числа камер (иначе лишние камеры остаются пустыми). */
    public void load(StateReader in) {
        wallK = in.getDouble("wall_k");
        damage = in.getDouble("damage");
        hot = in.getBoolean("hot");
        failed = in.getBoolean("failed");
        fuelKgS = in.getDouble("fuel_kg_s");
        airKgS = in.getDouble("air_kg_s");
        copy(in.getDoubles("byproducts"), byproducts);
        int[] st = in.getInts("state");
        OvenState[] all = OvenState.values();
        for (int i = 0; i < ovens && i < st.length; i++) {
            state[i] = all[Math.min(all.length - 1, st[i] & 0xFF)];
            pitch[i] = (st[i] & 1 << 8) != 0;
        }
        copy(in.getDoubles("wet"), wet);
        copy(in.getDoubles("dry"), dry);
        copy(in.getDoubles("water"), water);
        copy(in.getDoubles("ash"), ash);
        copy(in.getDoubles("sulfur"), sulfur);
        copy(in.getDoubles("vdaf"), vdaf);
        copy(in.getDoubles("daf"), daf);
        copy(in.getDoubles("plastic"), plastic);
        copy(in.getDoubles("progress"), progress);
        copy(in.getDoubles("ready_s"), readyS);
    }

    private static void copy(double[] from, double[] to) {
        System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
    }

    /**
     * Выданный кокс.
     *
     * @param cokeKg     кусковой кокс (или пековый), кг
     * @param breezeKg   мелочь {@code coke_breeze} &lt; 25 мм, кг
     * @param assay      анализ кокса (M₂₅ — в {@code strengthM25}, у пекового NaN)
     * @param pitchCoke  {@code pitch_coke}
     * @param overcookH  передержка сверх 2 ч, ч физ.
     */
    public record PushResult(double cokeKg, double breezeKg, CoalAssay assay, boolean pitchCoke, double overcookH) {}
}
