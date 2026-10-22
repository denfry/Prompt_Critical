package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Objects;

/**
 * Водотрубный барабанный котёл I1 (07 §7.9.5) — газовая сторона с BMS (07 §7.9.6). Вода и пар — узлы гидросети:
 * экраны (испарительный контур барабана) получают {@link BoilerOutputs#heatToWaterW}, пароперегреватель и
 * экономайзер — {@link BoilerOutputs#superheaterHeatW} и {@link BoilerOutputs#economizerHeatW}.
 *
 * <p>Путь газов за тик:
 * <ol>
 *   <li>BMS ({@link BurnerManagementSystem}) по датчикам пламени прошлого тика двигает отсечные клапаны. Топливо
 *       коллектора ({@link BoilerInputs#fuelFeedKgS}) делится между горелками поровну и проходит через их клапаны;
 *       запальник берёт {@link #IGNITER_FUEL_FRACTION} номинала горелки.</li>
 *   <li>Факелы: запальник зажигается искрой пробы (если не неисправен); основной факел — от запальника своей
 *       горелки, от любого другого факела или от стен топки горячее {@link #AUTOIGNITION_K} (540 °C —
 *       самовоспламенение метана). Факел срывается при α горелки (воздух короба делится поровну между горелками)
 *       выше {@link #GAS_BLOWOUT_ALPHA} (газ), без воздуха и от пара разорванной трубы.</li>
 *   <li>Топливо без факела копится в топке массой m: dm/dt = ṁ − m·Q_в/V_т (вентиляция воздухом). Если в топке
 *       есть факел, накопленное догорает за {@link #ACCUMULATED_BURN_S}. Воспламенение облака (искра, загоревшийся
 *       факел, горячие стены) при концентрации между пределами {@link FlammabilityLimits} — взрыв топки
 *       E = m·Q_н·f_blast ({@code boiler.furnace_explosion_blast_fraction} = 0,1).</li>
 *   <li>Топка — формула Гурвича ({@link FurnaceHeatTransfer}); дальше пароперегреватель, экономайзер и
 *       воздухоподогреватель — противоточные ε-NTU, UA ∝ (ṁ_газ/ṁ_расч)^0,6. Горячий воздух идёт в горелки на
 *       следующем тике.</li>
 *   <li>Давление в топке: (ṁ_газ − ṁ_дымосос)·{@link #FURNACE_PRESSURE_GAIN}/ṁ_расч.</li>
 * </ol>
 *
 * <p>Экраны: по потоку пояса горелок q = {@link #SCREEN_PEAK_FACTOR}·Q_т/F_ст
 * T_ст = T_нас + q(1/h + δ_т/λ_т) + q·δ_н/λ_н (07 §7.9.3), h — кипение по Куперу или
 * {@link #STARVED_H}, если уровень в барабане ниже срыва циркуляции. В {@link #stepSlow} растёт накипь (жёсткость
 * котловой воды), утоняется стенка (O₂, Cl⁻) и копится повреждение ползучести D = ∫dt/t_r(σ, T_ст) по
 * Ларсону–Миллеру при окружном напряжении σ = p(D_н − δ)/(2δ); D ≥ 1 или стенка тоньше
 * {@link #MIN_WALL_FRACTION} — разрыв трубы ({@code BOILER_TUBE_LEAK}).
 */
public final class WaterTubeBoiler implements Boiler {
    /** Запальник: доля номинального расхода горелки. */
    public static final double IGNITER_FUEL_FRACTION = 0.03;
    /** Срыв основного факела по бедности: газ / пыль и мазут [BALANCE]. */
    public static final double GAS_BLOWOUT_ALPHA = 3.0, OTHER_BLOWOUT_ALPHA = 2.5;
    /** Самовоспламенение от стен топки, К (540 °C). */
    public static final double AUTOIGNITION_K = 813.15;
    /** Постоянная догорания накопленного топлива при факеле, с. */
    public static final double ACCUMULATED_BURN_S = 1;
    /** {@code boiler.furnace_explosion_blast_fraction}. */
    public static final double FURNACE_BLAST_FRACTION = 0.1;
    /** Постоянная прогрева и остывания обмуровки и стен топки, с [BALANCE]. */
    public static final double REFRACTORY_TIME_S = 1200;
    /** Теплоотдача паром в экранах при срыве циркуляции, Вт/(м²·К) [BALANCE]. */
    public static final double STARVED_H = 500;
    /** Тепловой поток в поясе горелок к среднему по экранам: по нему считаются накипь и металл [BALANCE]. */
    public static final double SCREEN_PEAK_FACTOR = 2.0;
    /** Вентилятор «работает» выше этой доли номинального расхода. */
    public static final double FAN_RUNNING_FRACTION = 0.05;
    /** Давление в топке на единицу относительного избытка газов над дымососом, Па [BALANCE]. */
    public static final double FURNACE_PRESSURE_GAIN = 500;
    /** Утонение стенки: кислородная коррозия и хлориды, м/с физ. на мг/кг [BALANCE] (8 мг/кг O₂ ≈ 1 мм/год). */
    public static final double O2_CORROSION = 1e-3 / (8 * 3.15576e7), CHLORIDE_CORROSION = 0.5e-3 / (100 * 3.15576e7);
    /** Остаток стенки, при котором труба рвётся от давления, доля исходной. */
    public static final double MIN_WALL_FRACTION = 0.4;
    private static final double STEEL_K = 45, CP_AIR = 1010, R_AIR = 287.05, ATM_PA = 101_325;
    private static final double GAS_FLOW_EXPONENT = 0.6;
    private static final int FURNACE_ITERATIONS = 4;

    private final WaterTubeBoilerSpec spec;
    private final Combustor combustor;
    private final FlammabilityLimits limits;
    private final BoilerEvents events;
    private final BurnerManagementSystem bms;
    private final double designFluegasKgS, blowoutAlpha;
    private final boolean[] igniterFlame, mainFlame, igniterFault, disturbed, sparkCmd;
    private final double[] burnerFuel, igniterFuel;
    private double airPreheatK, refractoryK, gasK, accumulatedKg;
    private double scaleM, wallM, creepDamage, lastFluxWm2, lastMetalK, lastPressurePa;
    private boolean tubeLeak;
    private BoilerRupture rupture = BoilerRupture.NONE;
    private double explosionJ, admittedKgS;

    /**
     * @param fuel         топливо горелок
     * @param limits       пределы воспламенения этого топлива ({@link FlammabilityLimits#ofGas} для газа)
     * @param initialTempK начальная температура топки, обмуровки и воздуха, К
     */
    public WaterTubeBoiler(WaterTubeBoilerSpec spec, FuelSpec fuel, FlammabilityLimits limits, BoilerEvents events,
            double initialTempK) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.combustor = new Combustor(Objects.requireNonNull(fuel, "fuel"),
                BurnerSpec.of(spec.burnerKind(), spec.ratedFuelKgS()));
        this.limits = Objects.requireNonNull(limits, "limits");
        this.events = Objects.requireNonNull(events, "events");
        this.bms = new BurnerManagementSystem(spec.burners(), spec.burnerKind(), events);
        this.designFluegasKgS = spec.ratedAirKgS() + spec.ratedFuelKgS();
        this.blowoutAlpha = spec.burnerKind() == BurnerKind.GAS || spec.burnerKind() == BurnerKind.LOW_BTU_GAS
                ? GAS_BLOWOUT_ALPHA : OTHER_BLOWOUT_ALPHA;
        int n = spec.burners();
        igniterFlame = new boolean[n];
        mainFlame = new boolean[n];
        igniterFault = new boolean[n];
        disturbed = new boolean[n];
        sparkCmd = new boolean[n];
        burnerFuel = new double[n];
        igniterFuel = new double[n];
        airPreheatK = refractoryK = gasK = initialTempK;
        lastMetalK = initialTempK;
        wallM = spec.screenWallM();
    }

    public WaterTubeBoilerSpec spec() {
        return spec;
    }

    public Combustor combustor() {
        return combustor;
    }

    /** Команды и состояние BMS. */
    public BurnerManagementSystem bmsControl() {
        return bms;
    }

    @Override
    public BmsState bms() {
        return bms.state();
    }

    /** Запальник горелки неисправен (электрод закоптился): искры нет. */
    public void setIgniterFault(int burner, boolean faulty) {
        igniterFault[burner] = faulty;
    }

    /** Возмущение факела горелки на тик (пробка в мазуте, перебой пыли): основной факел гаснет. */
    public void disturbFlame(int burner) {
        disturbed[burner] = true;
    }

    public boolean mainFlame(int burner) {
        return mainFlame[burner];
    }

    public boolean igniterFlame(int burner) {
        return igniterFlame[burner];
    }

    public double refractoryK() {
        return refractoryK;
    }

    /** Стены топки разогреты/остужены (пусконаладка, тесты), К. */
    public void setRefractoryTemperature(double kelvin) {
        refractoryK = kelvin;
    }

    /** Топливо, накопленное в топке без факела, кг. */
    public double accumulatedFuelKg() {
        return accumulatedKg;
    }

    /** Концентрация накопленного топлива, приведённая к 20 °C, кг/м³. */
    public double furnaceFuelConcentration() {
        return accumulatedKg / spec.furnaceVolumeM3() * gasK / 293.15;
    }

    /** Топливо, прошедшее отсечные клапаны и запальники за последний тик, кг/с (горит или копится в топке). */
    public double admittedFuelKgS() {
        return admittedKgS;
    }

    public BoilerRupture rupture() {
        return rupture;
    }

    /** Энергия ударной волны взрыва топки, Дж (0 — не было). */
    public double explosionEnergyJ() {
        return explosionJ;
    }

    public boolean tubeLeak() {
        return tubeLeak;
    }

    public double scaleM() {
        return scaleM;
    }

    /** Химическая или механическая очистка экранов: снимает до {@code thicknessM} накипи. */
    public void removeScale(double thicknessM) {
        if (!(thicknessM >= 0)) throw new IllegalArgumentException("thickness: " + thicknessM);
        scaleM = Math.max(0, scaleM - thicknessM);
    }

    public void setScale(double thicknessM) {
        if (!(thicknessM >= 0)) throw new IllegalArgumentException("thickness: " + thicknessM);
        scaleM = thicknessM;
    }

    public double creepDamage() {
        return creepDamage;
    }

    public double screenWallM() {
        return wallM;
    }

    /** Замена труб экрана: стенка новая, ползучести нет, течь устранена. */
    public void repairScreens() {
        wallM = spec.screenWallM();
        creepDamage = 0;
        tubeLeak = false;
    }

    public void save(StateWriter out) {
        out.beginObject("bms");
        bms.save(out);
        out.endObject();
        int n = spec.burners();
        byte[] f = new byte[n];
        for (int b = 0; b < n; b++) f[b] = (byte) ((igniterFlame[b] ? 1 : 0) | (mainFlame[b] ? 2 : 0) | (igniterFault[b] ? 4 : 0));
        out.putBytes("flames", f, n);
        out.putDouble("air_preheat_k", airPreheatK);
        out.putDouble("refractory_k", refractoryK);
        out.putDouble("gas_k", gasK);
        out.putDouble("accumulated_kg", accumulatedKg);
        out.putDouble("scale_m", scaleM);
        out.putDouble("wall_m", wallM);
        out.putDouble("creep", creepDamage);
        out.putDouble("flux", lastFluxWm2);
        out.putDouble("metal_k", lastMetalK);
        out.putDouble("pressure", lastPressurePa);
        out.putBoolean("tube_leak", tubeLeak);
        out.putInt("rupture", rupture.ordinal());
        out.putDouble("explosion_j", explosionJ);
    }

    public void load(StateReader in) {
        in.beginObject("bms");
        bms.load(in);
        in.endObject();
        byte[] f = in.getBytes("flames");
        for (int b = 0; b < spec.burners(); b++) {
            byte v = b < f.length ? f[b] : 0;
            igniterFlame[b] = (v & 1) != 0;
            mainFlame[b] = (v & 2) != 0;
            igniterFault[b] = (v & 4) != 0;
        }
        airPreheatK = in.getDouble("air_preheat_k");
        refractoryK = in.getDouble("refractory_k");
        gasK = in.getDouble("gas_k");
        accumulatedKg = in.getDouble("accumulated_kg");
        scaleM = in.getDouble("scale_m");
        wallM = in.getDouble("wall_m");
        creepDamage = in.getDouble("creep");
        lastFluxWm2 = in.getDouble("flux");
        lastMetalK = in.getDouble("metal_k");
        lastPressurePa = in.getDouble("pressure");
        tubeLeak = in.getBoolean("tube_leak");
        rupture = BoilerRupture.values()[in.getInt("rupture")];
        explosionJ = in.getDouble("explosion_j");
    }

    /** Перенос износа экранов (накипь, стенка, ползучесть, течь) и прогрева топки из прежнего котла (перестройка). */
    public void inheritFrom(WaterTubeBoiler old) {
        scaleM = old.scaleM;
        wallM = Math.min(old.wallM, spec.screenWallM());
        creepDamage = old.creepDamage;
        tubeLeak = old.tubeLeak;
        rupture = old.rupture;
        refractoryK = old.refractoryK;
        airPreheatK = old.airPreheatK;
        gasK = old.gasK;
        accumulatedKg = old.accumulatedKg;
    }

    /**
     * Шаг SLOW: накипь, коррозия и ползучесть экранов по режиму последнего тика FAST.
     *
     * @param hardnessMolEqM3 жёсткость котловой воды, моль-экв/м³
     * @param o2KgKg          растворённый кислород, кг/кг
     * @param chlorideKgKg    хлориды, кг/кг
     */
    public void stepSlow(double dtPhysS, double hardnessMolEqM3, double o2KgKg, double chlorideKgKg) {
        if (!(dtPhysS > 0) || tubeLeak) return;
        scaleM += BoilerScale.growthRate(hardnessMolEqM3, lastFluxWm2) * dtPhysS;
        wallM -= (O2_CORROSION * o2KgKg * 1e6 + CHLORIDE_CORROSION * chlorideKgKg * 1e6) * dtPhysS;
        double d = spec.screenTubeOuterM();
        double stress = lastPressurePa * (d - wallM) / (2 * wallM);
        double tr = spec.screenCreep().ruptureTimeH(stress, lastMetalK);
        creepDamage += dtPhysS / 3600 / tr;
        if (creepDamage >= 1 || wallM <= MIN_WALL_FRACTION * spec.screenWallM()) {
            tubeLeak = true;
            events.tubeLeak();
        }
    }

    @Override
    public void stepFast(double dt, BoilerInputs in, BoilerOutputs out) {
        CombustionResult res = out.combustion;
        int n = spec.burners();
        double air = Math.max(0, in.forcedAirKgS);
        double ta0 = in.ambientK, tw = in.waterTempK;
        boolean fans = air >= FAN_RUNNING_FRACTION * spec.ratedAirKgS()
                && in.inducedDraftKgS >= FAN_RUNNING_FRACTION * designFluegasKgS;
        double ventPerS = air / (ATM_PA / (R_AIR * gasK)) / spec.furnaceVolumeM3();
        if (rupture != BoilerRupture.NONE) {
            fans = false;
            air = 0;
        }
        // искра пробы запальника — по команде до шага BMS: с байпасом датчика проба кончается в этом же шаге
        for (int b = 0; b < n; b++) sparkCmd[b] = bms.spark(b);
        bms.step(dt, fans, air / spec.ratedAirKgS(), ventPerS, igniterFlame, mainFlame);

        // топливо по горелкам и факелы
        double header = Math.max(0, in.fuelFeedKgS) / n, fuel = 0;
        for (int b = 0; b < n; b++) {
            burnerFuel[b] = header * bms.mainValve(b);
            igniterFuel[b] = bms.igniterOpen(b) ? IGNITER_FUEL_FRACTION * spec.burnerRatedFuelKgS() : 0;
            fuel += burnerFuel[b] + igniterFuel[b];
        }
        if (rupture != BoilerRupture.NONE) fuel = 0;
        admittedKgS = fuel;
        double burnerAir = air / n;
        boolean flameBefore = anyFlame();
        boolean hotWalls = refractoryK >= AUTOIGNITION_K;
        boolean ignition = false;
        for (int b = 0; b < n; b++) {
            boolean quench = air <= 0 || tubeLeak || rupture != BoilerRupture.NONE;
            if (igniterFuel[b] <= 0 || quench) {
                igniterFlame[b] = false;
            } else if (!igniterFlame[b] && (bms.spark(b) || sparkCmd[b]) && !igniterFault[b]) {
                igniterFlame[b] = true;
                ignition = true;
            }
            boolean lean = burnerAir > blowoutAlpha * burnerFuel[b] * combustor.stoichAir();
            if (burnerFuel[b] <= 0 || quench || disturbed[b] || lean) {
                mainFlame[b] = false;
            } else if (!mainFlame[b] && (igniterFlame[b] || flameBefore || hotWalls || otherMain(b))) {
                mainFlame[b] = true;
                ignition = true;
            }
            disturbed[b] = false;
        }
        boolean flame = anyFlame();
        if (!flameBefore && (ignition || hotWalls)) checkExplosion();
        if (rupture != BoilerRupture.NONE) {
            flame = false;
            fuel = 0;
            java.util.Arrays.fill(mainFlame, false);
            java.util.Arrays.fill(igniterFlame, false);
        }

        // накопление и горение
        double burn;
        if (flame) {
            double burnt = accumulatedKg * Math.min(1, dt / ACCUMULATED_BURN_S);
            accumulatedKg -= burnt;
            burn = fuel + burnt / dt;
        } else {
            burn = 0;
            accumulatedKg = (accumulatedKg + fuel * dt) / (1 + dt * ventPerS);
        }
        combustor.burn(burn, air, airPreheatK, ta0, res);

        // топка: Гурвич
        double ta = res.adiabaticFlameK, exit = ta, qFurnace = 0;
        double hTa = res.fluegasHeatRateW(ta);
        if (burn > 0 && ta > tw + 1) {
            double phi = FurnaceHeatTransfer.PHI_DEFAULT;
            exit = Math.max(tw + 1, 0.6 * ta);
            for (int it = 0; it < FURNACE_ITERATIONS; it++) {
                double vc = (hTa - res.fluegasHeatRateW(exit)) / (ta - exit);
                double bo = FurnaceHeatTransfer.boltzmannNumber(phi, vc, spec.screenEfficiency(),
                        spec.furnaceWallAreaM2(), ta);
                exit = Math.max(tw + 1, ta * FurnaceHeatTransfer.exitTempRatio(bo, spec.furnaceEmissivity(),
                        FurnaceHeatTransfer.M_DEFAULT));
            }
            qFurnace = phi * (hTa - res.fluegasHeatRateW(exit));
        }

        // конвективные поверхности
        double flowScale = res.fluegasKgS > 0 ? Math.pow(res.fluegasKgS / designFluegasKgS, GAS_FLOW_EXPONENT) : 0;
        double hGas = res.fluegasHeatRateW(exit), tg = exit;
        double qSh = exchange(res, hGas, tg, spec.superheaterUA() * flowScale, in.steamFlowKgS * in.steamCpJKgK,
                in.steamInletK);
        hGas -= qSh;
        tg = qSh != 0 ? res.temperatureForHeatRate(hGas, tg - qSh / gasCapacity(res, tg, in.steamInletK)) : tg;
        double qEco = exchange(res, hGas, tg, spec.economizerUA() * flowScale,
                in.feedwaterFlowKgS * in.feedwaterCpJKgK, in.feedwaterInletK);
        hGas -= qEco;
        tg = qEco != 0 ? res.temperatureForHeatRate(hGas, tg - qEco / gasCapacity(res, tg, in.feedwaterInletK)) : tg;
        double qAph = exchange(res, hGas, tg, spec.airPreheaterUA() * flowScale, air * CP_AIR, ta0);
        hGas -= qAph;
        tg = qAph != 0 ? res.temperatureForHeatRate(hGas, tg - qAph / gasCapacity(res, tg, ta0)) : tg;
        airPreheatK = air > 0 ? ta0 + qAph / (air * CP_AIR) : ta0;

        // стены топки, экраны
        double wallTarget = flame ? exit : airPreheatK;
        refractoryK += (wallTarget - refractoryK) * Math.min(1, dt / REFRACTORY_TIME_S);
        gasK = flame ? exit : Math.max(airPreheatK, 0.5 * (airPreheatK + refractoryK));
        double q = SCREEN_PEAK_FACTOR * qFurnace / spec.furnaceWallAreaM2();
        boolean starved = in.waterLevelM < spec.screenStarveLevelM();
        double h = starved ? STARVED_H : FireTubeBoiler.boilingH(in.waterPressurePa, q);
        double metal = tw + q * (1 / h + wallM / STEEL_K + BoilerScale.resistance(scaleM));
        lastFluxWm2 = q;
        lastMetalK = metal;
        lastPressurePa = in.waterPressurePa;
        double qCasing = spec.casingLossWPerK() * (tw - ta0);

        out.furnaceHeatW = qFurnace;
        out.heatToWaterW = qFurnace - qCasing;
        out.superheaterHeatW = qSh;
        out.economizerHeatW = qEco;
        out.convectiveHeatW = qSh + qEco;
        out.shellLossW = qCasing;
        out.fuelBurnKgS = burn;
        out.airKgS = air;
        out.airPreheatK = airPreheatK;
        out.furnaceExitK = exit;
        out.fluegasExitK = tg;
        out.furnacePressurePa = FURNACE_PRESSURE_GAIN * (res.fluegasKgS - Math.max(0, in.inducedDraftKgS))
                / designFluegasKgS;
        out.draftPa = -out.furnacePressurePa;
        out.efficiency = burn > 0 ? (qFurnace + qSh + qEco - qCasing) / (burn * combustor.lhv()) : Double.NaN;
        out.screenFluxWm2 = q;
        out.screenMetalK = metal;
        out.furnaceFuelKg = accumulatedKg;
        out.flame = flame;
    }

    /**
     * Противоточный теплообменник газ → среда: ε-NTU с C_газ по средней теплоёмкости между T_газ и T_среды.
     * Нет газа или среды — 0.
     */
    private static double exchange(CombustionResult res, double hGas, double tg, double ua, double cCold, double tCold) {
        if (!(ua > 0) || !(cCold > 0) || res.fluegasKgS <= 0 || tg <= tCold + 1e-6) return 0;
        double cGas = gasCapacity(res, tg, tCold);
        double cMin = Math.min(cGas, cCold), cr = cMin / Math.max(cGas, cCold);
        double ntu = ua / cMin, eps;
        if (Math.abs(1 - cr) < 1e-9) {
            eps = ntu / (1 + ntu);
        } else {
            double e = Math.exp(-ntu * (1 - cr));
            eps = (1 - e) / (1 - cr * e);
        }
        return eps * cMin * (tg - tCold);
    }

    private static double gasCapacity(CombustionResult res, double tg, double tCold) {
        return (res.fluegasHeatRateW(tg) - res.fluegasHeatRateW(tCold)) / (tg - tCold);
    }

    /** Облако в топке воспламенилось: между пределами — взрыв, иначе догорит или выветрится. */
    private void checkExplosion() {
        if (accumulatedKg <= 0 || !limits.flammable(furnaceFuelConcentration())) return;
        explosionJ = accumulatedKg * combustor.lhv() * FURNACE_BLAST_FRACTION;
        accumulatedKg = 0;
        rupture = BoilerRupture.FURNACE_EXPLOSION;
        bms.trip(MftCause.PROTECTION);
        events.furnaceExplosion(explosionJ);
    }

    private boolean anyFlame() {
        for (int b = 0; b < mainFlame.length; b++) if (mainFlame[b] || igniterFlame[b]) return true;
        return false;
    }

    private boolean otherMain(int burner) {
        for (int b = 0; b < mainFlame.length; b++) if (b != burner && mainFlame[b]) return true;
        return false;
    }
}
