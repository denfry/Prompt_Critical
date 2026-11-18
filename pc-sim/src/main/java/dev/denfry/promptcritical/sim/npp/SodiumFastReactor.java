package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.neutronics.DecayHeat;
import dev.denfry.promptcritical.sim.neutronics.DelayedGroups;
import dev.denfry.promptcritical.sim.neutronics.DelayedNeutronData;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;

/**
 * Натриевый реактор на быстрых нейтронах T4 (00 §0.5, тип БН-350/БН-600) в масштабе 100 МВт(т): точечная модель с
 * одной энергетической группой, МОКС-зона с U-238 экраном (воспроизводство Pu-239), бассейновый первый контур натрия при
 * ≈0,1 МПа (380 → 550 °C), промежуточный натриевый контур и парогенератор натрий–вода.
 *
 * <p><b>Кинетика.</b> Шесть групп Кипина, смешанных по долям делений Pu-239 и U-238 ({@link DelayedNeutronData#mix}),
 * ценность запаздывающих 0,9 (жёсткий спектр, Waltar &amp; Reynolds, «Fast Breeder Reactors», 1981, гл. 6):
 * β_eff ≈ 0,0035 для МОКС; время генерации Λ = 4·10⁻⁷ с (там же, табл. 6-1: 10⁻⁷…10⁻⁶ с против 2·10⁻⁵ у ВВЭР).
 *
 * <p><b>Баланс нейтронов (одна группа).</b> Микросечения, усреднённые по спектру оксидной зоны [Waltar &amp; Reynolds,
 * гл. 4, порядок величин; округлено]: Pu-239 σ_f = 1,81 б, σ_c = 0,46 б, ν = 2,95; U-238 σ_f = 0,042 б,
 * σ_c = 0,27 б, ν = 2,79. Паразитный захват натрием, сталью и кислородом — 8 % поглощения в топливе. Утечка из
 * критической зоны (k = 1) уходит в экран, где U-238 захватывает её долю {@link #BLANKET_CAPTURE}. Коэффициент
 * воспроизводства КВ = (C₂₈ зоны + C₂₈ экрана)/A₄₉; для 18 % Pu выходит ≈1,2 — в полосе 1,1…1,3 проектов БН/Phénix.
 *
 * <p><b>Обратные связи.</b> Доплер — K_D·ln(T_f/T_f0), постоянная Доплера K_D = −0,005 (оксидная зона с натрием:
 * −0,005…−0,009, Waltar &amp; Reynolds гл. 13; малая зона жёстче, берём нижний край по модулю). Осевое расширение
 * топлива −2,5·10⁻⁶ 1/К по средней температуре топлива; радиальное расширение (напорный коллектор) −6·10⁻⁶ 1/К по
 * температуре на входе. Натрий: ценность полного обезвоживания складывается из положительной центральной части
 * (ужесточение спектра, +0,0040) и отрицательной периферийной (рост утечки, −0,0055); для малой зоны итог
 * отрицателен (≈ −0,4 β), для больших зон (БН-600, Superphénix) центральная часть перевешивает и эффект положителен —
 * Waltar &amp; Reynolds гл. 13, IAEA-TECDOC-1531 (2006) [значения для 100 МВт(т) — оценка]. Изотермический
 * температурный коэффициент выходит ≈ −1,9 pcm/К (у больших БН — того же порядка, отрицательный [оценка]).
 *
 * <p><b>Течи.</b> Течь первого контура в помещение — натриевый пожар: горение до Na₂O, ≈ 9 МДж/кг натрия, аэрозоль
 * Na₂O 1,35 кг на кг натрия. Течь воды в парогенераторе — реакция натрий–вода Na + H₂O → NaOH + ½H₂, ≈ 7,8 МДж/кг
 * воды и 0,056 кг водорода на кг воды; давление водорода во втором контуре разрывает мембрану при 1 МПа, а детектор
 * водорода глушит реактор (АЗ «натрий–вода»). Эти потоки — сырьё для {@code DamageBudget} и эффекта дыма в моде.
 */
public final class SodiumFastReactor {
    public static final double DT = 0.05;
    public static final double NOMINAL_W = 100e6;
    public static final double T_IN_NOM_K = 653.15, T_OUT_NOM_K = 823.15, P_PRIMARY_PA = 0.1e6;
    /** Теплоёмкость жидкого натрия ≈ 1270 Дж/(кг·К) в 400–550 °C (Fink, Leibowitz, ANL/RE-95/2, 1995). */
    public static final double CP_NA = 1270;
    public static final double NOMINAL_FLOW_KG_S = NOMINAL_W / (CP_NA * (T_OUT_NOM_K - T_IN_NOM_K));
    /** Кипение натрия при 0,1 МПа, К. */
    public static final double NA_BOIL_K = 1156;
    public static final double LAMBDA_GEN = 4e-7, DELAYED_IMPORTANCE = 0.9;
    public static final double SIGF_49 = 1.81, SIGC_49 = 0.46, NU_49 = 2.95;
    public static final double SIGF_28 = 0.042, SIGC_28 = 0.27, NU_28 = 2.79;
    public static final double PARASITIC = 0.08, BLANKET_CAPTURE = 0.8;
    public static final double DOPPLER_KD = -0.005, AXIAL_PER_K = -2.5e-6, RADIAL_PER_K = -6e-6;
    public static final double VOID_CENTRE = 0.0040, VOID_PERIPHERY = -0.0055;
    /** Средняя температура топлива на номинале и тепловое сопротивление топливо → натрий, К и К/Вт. */
    public static final double FUEL_NOM_K = 1500;
    static final double FUEL_R = (FUEL_NOM_K - (T_IN_NOM_K + T_OUT_NOM_K) / 2) / NOMINAL_W;
    /** Тепловая постоянная топлива ≈ 2,5 с (оксид), отсюда теплоёмкость. */
    static final double FUEL_C = 2.5 / FUEL_R;
    /** Натрий в зоне, в горячей и холодной частях бассейна, во втором контуре; Дж/К [масштаб БН на 100 МВт]. */
    static final double CORE_NA_C = 2_000 * CP_NA, HOT_POOL_C = 40_000 * CP_NA, COLD_POOL_C = 40_000 * CP_NA,
            INT_HOT_C = 15_000 * CP_NA, INT_COLD_C = 15_000 * CP_NA;
    /** Второй контур: 310 → 500 °C на номинале; эффективность ПТО; питательная вода и пар ПГ (14 МПа, 505 °C). */
    public static final double INT_COLD_NOM_K = 583.15, INT_HOT_NOM_K = 773.15, FEED_K = 513.15;
    static final double INT_FLOW_KG_S = NOMINAL_W / (CP_NA * (INT_HOT_NOM_K - INT_COLD_NOM_K));
    public static final double STEAM_DH_J_KG = 2.3e6;
    public static final double NATURAL_FLOW = 0.05, PUMP_COAST_S = 30;
    public static final double WORTH_SCRAM = 0.08, WORTH_CONTROL = 0.03, SCRAM_S = 1.0, DRIVE_FRAC_S = 1.0 / 100;
    public static final double TRIP_POWER = 1.15, TRIP_PERIOD_S = 10, TRIP_OUTLET_K = 853.15, TRIP_FLOW_RATIO = 1.3;
    public static final double NA_FIRE_J_KG = 9e6, NA2O_PER_NA = 1.35, NA_H2O_J_KG = 7.8e6, H2_PER_H2O = 0.056;
    public static final double RUPTURE_DISC_PA = 1.0e6;
    /** Водород во втором контуре, кг на 1 МПа избыточного давления газовой подушки [оценка]. */
    static final double H2_KG_PER_MPA = 0.5;
    static final double SEC_PER_DAY = 86_400, AVOGADRO = 6.022e23, MEV_PER_FISSION = 200, J_PER_MEV = 1.602e-13;

    public enum Trip { NONE, POWER, PERIOD, OUTLET_TEMP, FLOW, SODIUM_WATER, EXTERNAL, MANUAL }

    private final double puFraction0;
    private final PointKinetics kinetics;
    private final DecayHeat decay = new DecayHeat();
    private double puFraction, corePuKg, blanketPuKg;
    private final double heavyMetalKg;
    private double scram = 1, control = 1;
    private int controlDrive;
    private Trip trip = Trip.MANUAL;
    private boolean externalTrip;
    private double fuelK = T_IN_NOM_K, coreNaK = T_IN_NOM_K, hotPoolK = T_IN_NOM_K, coldPoolK = T_IN_NOM_K;
    private double intHotK = INT_COLD_NOM_K, intColdK = INT_COLD_NOM_K;
    private double primaryDrive, intermediateDrive, primaryFlow, intermediateFlow;
    private double steamDemandW = NOMINAL_W, qIhxW, qSgW, rho0;
    private double primaryLeakKgS, sgLeakKgS, fireW, aerosolKgS, sodiumWaterW, h2Kg, intGasPa;
    private boolean discBurst;

    /** Зона из {@code heavyMetalKg} МОКС с массовой долей Pu {@code puFraction} (≈ Pu-239, Pu-240/241 не ведём). */
    public SodiumFastReactor(double heavyMetalKg, double puFraction) {
        if (heavyMetalKg <= 0 || puFraction <= 0 || puFraction >= 1) throw new IllegalArgumentException("fuel");
        this.heavyMetalKg = heavyMetalKg;
        this.puFraction0 = this.puFraction = puFraction;
        this.corePuKg = heavyMetalKg * puFraction;
        DelayedGroups g = groups(puFraction);
        kinetics = new PointKinetics(g, LAMBDA_GEN, 1e-6);
        rho0 = feedback(FUEL_NOM_K, (T_IN_NOM_K + T_OUT_NOM_K) / 2, T_IN_NOM_K);
    }

    // ------------------------------------------------------------------ нейтроника одной группы

    /** Скорость деления на одно ядро тяжёлого металла (отн.) Pu-239 и U-238. */
    static double[] fissions(double pu) {
        return new double[] {pu * SIGF_49, (1 - pu) * SIGF_28};
    }

    /** Доли делений {U-235, U-238, Pu-239, Pu-241} для смешения запаздывающих групп. */
    static DelayedGroups groups(double pu) {
        double[] f = fissions(pu);
        double s = f[0] + f[1];
        return DelayedNeutronData.mix(new double[] {0, f[1] / s, f[0] / s, 0}, DELAYED_IMPORTANCE);
    }

    /** k∞ зоны: рождение νΣ_f к поглощению в топливе плюс паразитное. */
    public static double kInfinity(double pu) {
        double prod = NU_49 * pu * SIGF_49 + NU_28 * (1 - pu) * SIGF_28;
        return prod / absorption(pu);
    }

    static double absorption(double pu) {
        return (pu * (SIGF_49 + SIGC_49) + (1 - pu) * (SIGF_28 + SIGC_28)) * (1 + PARASITIC);
    }

    /**
     * КВ критической зоны: утечка = рождение − поглощение, её доля {@link #BLANKET_CAPTURE} захватывается U-238
     * экрана; КВ = (захват U-238 в зоне + в экране) / поглощение Pu-239.
     */
    public static double breedingRatio(double pu) {
        double prod = NU_49 * pu * SIGF_49 + NU_28 * (1 - pu) * SIGF_28;
        double leak = Math.max(0, prod - absorption(pu));
        double a49 = pu * (SIGF_49 + SIGC_49);
        return ((1 - pu) * SIGC_28 + BLANKET_CAPTURE * leak) / a49;
    }

    /** Внутренний КВ зоны (без экрана). */
    public static double internalBreedingRatio(double pu) {
        return (1 - pu) * SIGC_28 / (pu * (SIGF_49 + SIGC_49));
    }

    /** Плотность жидкого натрия, кг/м³ (Fink, Leibowitz 1995). */
    public static double sodiumDensity(double tK) {
        double t = 1 - tK / 2503.7;
        return 219 + 275.32 * t + 511.58 * Math.sqrt(Math.max(0, t));
    }

    /** Реактивность обезвоживания: {@code centre} и {@code periphery} — доли пустот в центральной и краевой частях. */
    public static double voidReactivity(double centre, double periphery) {
        return VOID_CENTRE * centre + VOID_PERIPHERY * periphery;
    }

    /**
     * Обратные связи при температурах топлива, натрия в зоне и на входе, Δk/k относительно холодного (T = входу)
     * состояния; плотность натрия действует как равномерное «обезвоживание».
     */
    public static double feedback(double fuelK, double coreNaK, double inletK) {
        double doppler = DOPPLER_KD * Math.log(fuelK / T_IN_NOM_K);
        double axial = AXIAL_PER_K * (fuelK - T_IN_NOM_K);
        double radial = RADIAL_PER_K * (inletK - T_IN_NOM_K);
        double dens = 1 - sodiumDensity(coreNaK) / sodiumDensity(T_IN_NOM_K);
        return doppler + axial + radial + voidReactivity(dens, dens);
    }

    /** Изотермический температурный коэффициент при {@code tK}, Δk/k на К. */
    public static double isothermalCoefficient(double tK) {
        double h = 1;
        return (feedback(tK + h, tK + h, tK + h) - feedback(tK - h, tK - h, tK - h)) / (2 * h);
    }

    /** Доплеровский коэффициент при {@code tK}, Δk/k на К. */
    public static double dopplerCoefficient(double tK) {
        return DOPPLER_KD / tK;
    }

    /** Реактивность в текущем состоянии, Δk/k: свежая зона на номинале, АЗ взведена, регулирующие на 1/2 — ноль. */
    public double evaluateReactivity() {
        double kInf = kInfinity(puFraction) / kInfinity(puFraction0);
        double fuelRho = 1 - 1 / kInf;
        double rods = -WORTH_SCRAM * scram - WORTH_CONTROL * (control - 0.5);
        return fuelRho + rods + feedback(fuelK, coreNaK, coldPoolK) - rho0 + voidReactivity(centreVoid(), 0);
    }

    /** Центральная зона закипает, когда натрий на выходе доходит до кипения. */
    private double centreVoid() {
        double out = 2 * coreNaK - coldPoolK;
        return Math.max(0, Math.min(1, (out - NA_BOIL_K) / 100));
    }

    // ------------------------------------------------------------------ управление

    public void driveControl(int dir) {
        controlDrive = Integer.signum(dir);
    }

    public void setRods(double scram, double control) {
        this.scram = scram;
        this.control = control;
    }

    public void setPumpDrive(double primary, double intermediate) {
        primaryDrive = Math.max(0, Math.min(1.1, primary));
        intermediateDrive = Math.max(0, Math.min(1.1, intermediate));
    }

    /** Отбор пара турбинами, Вт тепла. */
    public void setSteamDemand(double w) {
        steamDemandW = Math.max(0, w);
    }

    public void setExternalTrip(boolean on) {
        externalTrip = on;
    }

    public void scramButton() {
        if (trip == Trip.NONE) trip = Trip.MANUAL;
    }

    public boolean resetTrip() {
        if (trip == Trip.NONE) return true;
        if (externalTrip || sgLeakKgS > 0 || scram < 0.99) return false;
        trip = Trip.NONE;
        scram = 0; // взвод поднимает стержни АЗ
        return true;
    }

    /** Течь натрия первого контура в помещение, кг/с (0 — заглушено). */
    public void setPrimaryLeak(double kgS) {
        primaryLeakKgS = Math.max(0, kgS);
    }

    /** Течь воды в натрий в парогенераторе, кг/с. */
    public void setSteamGeneratorLeak(double kgS) {
        sgLeakKgS = Math.max(0, kgS);
    }

    /** Горячий пуск на долю мощности {@code frac}: равновесные температуры, насосы на номинале, АЗ взведена. */
    public void hotStart(double frac) {
        primaryDrive = intermediateDrive = primaryFlow = intermediateFlow = 1;
        double q = NOMINAL_W * frac;
        steamDemandW = q;
        coldPoolK = T_IN_NOM_K;
        hotPoolK = coldPoolK + q / (CP_NA * NOMINAL_FLOW_KG_S);
        coreNaK = (coldPoolK + hotPoolK) / 2;
        fuelK = coreNaK + q * FUEL_R;
        intColdK = INT_COLD_NOM_K;
        intHotK = intColdK + q / (CP_NA * INT_FLOW_KG_S);
        kinetics.resetToEquilibrium(Math.max(frac, 1e-6));
        decay.equilibrium(q);
        scram = 0;
        control = 0.5;
        trip = Trip.NONE;
    }

    // ------------------------------------------------------------------ шаг

    public void step() {
        Trip cause = causeOf();
        if (trip == Trip.NONE && cause != Trip.NONE) trip = cause;
        if (trip != Trip.NONE) scram = Math.min(1, scram + DT / SCRAM_S);
        if (trip == Trip.NONE) control = Math.max(0, Math.min(1, control + controlDrive * DRIVE_FRAC_S * DT));

        double rho = evaluateReactivity();
        double n = kinetics.step(DT, rho, 0);
        double fissionW = n * NOMINAL_W;
        decay.step(DT, fissionW);
        double heatW = fissionW + decay.powerW();

        primaryFlow += (Math.max(NATURAL_FLOW, primaryDrive) - primaryFlow) * Math.min(1, DT / PUMP_COAST_S);
        intermediateFlow += (Math.max(NATURAL_FLOW, intermediateDrive) - intermediateFlow) * Math.min(1, DT / PUMP_COAST_S);
        double mP = primaryFlow * NOMINAL_FLOW_KG_S, mI = intermediateFlow * INT_FLOW_KG_S;

        double qFuel = (fuelK - coreNaK) / FUEL_R;
        fuelK += (heatW - qFuel) / FUEL_C * DT;
        double outK = 2 * coreNaK - coldPoolK;
        double qCore = mP * CP_NA * (outK - coldPoolK);
        coreNaK += (qFuel - qCore) / CORE_NA_C * DT;

        // ПТО: противоток, эффективность 0,9 от меньшей теплоёмкости потоков.
        double cMin = Math.min(mP, mI) * CP_NA;
        qIhxW = 0.9 * cMin * (hotPoolK - intColdK);
        double qSgMax = 0.95 * mI * CP_NA * Math.max(0, intHotK - FEED_K);
        qSgW = Math.min(steamDemandW, qSgMax);

        hotPoolK += mP * CP_NA * (outK - hotPoolK) / HOT_POOL_C * DT;
        double ihxOutK = hotPoolK - qIhxW / Math.max(1, mP * CP_NA);
        coldPoolK += mP * CP_NA * (ihxOutK - coldPoolK) / COLD_POOL_C * DT;

        sodiumWaterW = sgLeakKgS * NA_H2O_J_KG;
        intHotK += (qIhxW - mI * CP_NA * (intHotK - intColdK)) / INT_HOT_C * DT;
        intColdK += (mI * CP_NA * (intHotK - intColdK) - qSgW + sodiumWaterW) / INT_COLD_C * DT;
        if (!discBurst) {
            h2Kg += sgLeakKgS * H2_PER_H2O * DT;
            intGasPa = P_PRIMARY_PA + h2Kg / H2_KG_PER_MPA * 1e6;
            if (intGasPa > RUPTURE_DISC_PA) {
                discBurst = true;
                h2Kg = 0;
                intGasPa = P_PRIMARY_PA;
            }
        }

        // Натриевый пожар: горит всё вытекшее (подача ограничивает горение).
        fireW = primaryLeakKgS * NA_FIRE_J_KG;
        aerosolKgS = primaryLeakKgS * NA2O_PER_NA;

        // Выгорание и наработка Pu-239: делений в секунду на 200 МэВ.
        double fissPerS = fissionW / (MEV_PER_FISSION * J_PER_MEV);
        double[] f = fissions(puFraction);
        double fPu = f[0] / (f[0] + f[1]);
        double a49 = fissPerS * fPu * (SIGF_49 + SIGC_49) / SIGF_49;
        double kgPerAtom = 0.239 / AVOGADRO;
        double prod = NU_49 * puFraction * SIGF_49 + NU_28 * (1 - puFraction) * SIGF_28;
        double leakShare = Math.max(0, prod - absorption(puFraction)) / (puFraction * (SIGF_49 + SIGC_49));
        double coreGain = internalBreedingRatio(puFraction) * a49;
        corePuKg += (coreGain - a49) * kgPerAtom * DT;
        blanketPuKg += BLANKET_CAPTURE * leakShare * a49 * kgPerAtom * DT;
        puFraction = Math.max(1e-4, corePuKg / heavyMetalKg);
    }

    private Trip causeOf() {
        if (externalTrip) return Trip.EXTERNAL;
        if (sgLeakKgS > 0 && (h2Kg > 1e-3 || discBurst)) return Trip.SODIUM_WATER;
        if (kinetics.power() > TRIP_POWER) return Trip.POWER;
        double period = kinetics.periodInstant();
        if (period > 0 && period < TRIP_PERIOD_S && kinetics.power() > 1e-4) return Trip.PERIOD;
        if (2 * coreNaK - coldPoolK > TRIP_OUTLET_K) return Trip.OUTLET_TEMP;
        if (kinetics.power() > 0.1 && kinetics.power() / Math.max(1e-3, primaryFlow) > TRIP_FLOW_RATIO) return Trip.FLOW;
        return Trip.NONE;
    }

    // ------------------------------------------------------------------ показания

    public Trip trip() { return trip; }
    public double power() { return kinetics.power(); }
    public double thermalW() { return kinetics.power() * NOMINAL_W + decay.powerW(); }
    public double betaEff() { return kinetics.betaEff(); }
    public double generationTime() { return kinetics.generationTime(); }
    public double fuelK() { return fuelK; }
    public double inletK() { return coldPoolK; }
    public double outletK() { return 2 * coreNaK - coldPoolK; }
    public double intermediateHotK() { return intHotK; }
    public double intermediateColdK() { return intColdK; }
    public double primaryFlow() { return primaryFlow; }
    public double ihxW() { return qIhxW; }
    public double steamGeneratorW() { return qSgW; }
    public double steamKgS() { return qSgW / STEAM_DH_J_KG; }
    public double puFraction() { return puFraction; }
    public double corePuKg() { return corePuKg; }
    public double blanketPuKg() { return blanketPuKg; }
    public double breedingRatio() { return breedingRatio(puFraction); }
    public double scramPosition() { return scram; }
    public double controlPosition() { return control; }
    public double primaryPressurePa() { return P_PRIMARY_PA; }
    public double intermediateGasPa() { return intGasPa; }
    public boolean ruptureDiscBurst() { return discBurst; }
    /** Тепло натриевого пожара, Вт, и аэрозоль Na₂O, кг/с — для {@code DamageBudget} и дыма. */
    public double sodiumFireW() { return fireW; }
    public double aerosolKgS() { return aerosolKgS; }
    /** Тепло реакции натрий–вода во втором контуре, Вт. */
    public double sodiumWaterW() { return sodiumWaterW; }

    public double heavyMetalKg() { return heavyMetalKg; }
    public double initialPuFraction() { return puFraction0; }
    public boolean shutDown() { return kinetics.power() < 1e-3 && scram > 0.99; }
    public int controlDrive() { return controlDrive; }

    // ------------------------------------------------------------------ сохранение (зона задаётся конструктором)

    public void save(StateWriter out) {
        out.putDouble("n", kinetics.power());
        out.putDouble("scram", scram);
        out.putDouble("ctrl", control);
        out.putDouble("tf", fuelK);
        out.putDouble("tna", coreNaK);
        out.putDouble("thot", hotPoolK);
        out.putDouble("tcold", coldPoolK);
        out.putDouble("tih", intHotK);
        out.putDouble("tic", intColdK);
        out.putDouble("pu", corePuKg);
        out.putDouble("bpu", blanketPuKg);
        out.putDouble("h2", h2Kg);
        out.putString("trip", trip.name());
        out.putString("disc", discBurst ? "1" : "0");
        out.beginObject("decay");
        decay.save(out);
        out.endObject();
    }

    public void load(StateReader in) {
        scram = in.optDouble("scram", 1);
        control = in.optDouble("ctrl", 1);
        fuelK = in.optDouble("tf", fuelK);
        coreNaK = in.optDouble("tna", coreNaK);
        hotPoolK = in.optDouble("thot", hotPoolK);
        coldPoolK = in.optDouble("tcold", coldPoolK);
        intHotK = in.optDouble("tih", intHotK);
        intColdK = in.optDouble("tic", intColdK);
        corePuKg = in.optDouble("pu", corePuKg);
        blanketPuKg = in.optDouble("bpu", 0);
        h2Kg = in.optDouble("h2", 0);
        puFraction = Math.max(1e-4, corePuKg / heavyMetalKg);
        discBurst = "1".equals(in.optString("disc", "0"));
        try {
            trip = Trip.valueOf(in.optString("trip", Trip.MANUAL.name()));
        } catch (IllegalArgumentException e) {
            trip = Trip.MANUAL;
        }
        kinetics.resetToEquilibrium(Math.max(PointKinetics.MIN_POWER, in.optDouble("n", 1e-6)));
        if (in.has("decay")) {
            in.beginObject("decay");
            decay.load(in);
            in.endObject();
        }
    }
}
