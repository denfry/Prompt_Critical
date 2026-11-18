package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.fuel.MoxBlend;
import dev.denfry.promptcritical.sim.nuclide.PlutoniumCharge;
import dev.denfry.promptcritical.sim.neutronics.CellComposition;
import dev.denfry.promptcritical.sim.neutronics.CellFeedbackState;
import dev.denfry.promptcritical.sim.neutronics.CoolantKind;
import dev.denfry.promptcritical.sim.neutronics.DecayHeat;
import dev.denfry.promptcritical.sim.neutronics.DelayedGroups;
import dev.denfry.promptcritical.sim.neutronics.DelayedNeutronData;
import dev.denfry.promptcritical.sim.neutronics.FissionPoisons;
import dev.denfry.promptcritical.sim.neutronics.LatticeDesigns;
import dev.denfry.promptcritical.sim.neutronics.LatticeModel;
import dev.denfry.promptcritical.sim.neutronics.ModeratorKind;
import dev.denfry.promptcritical.sim.neutronics.PeriodMeter;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.neutronics.ReactorShape;
import dev.denfry.promptcritical.sim.neutronics.SixFactor;
import dev.denfry.promptcritical.sim.neutronics.Westcott;
import dev.denfry.promptcritical.sim.nuclide.NuclideVector;
import dev.denfry.promptcritical.sim.reactor.FuelDepletion;

/**
 * Активная зона легководного корпусного реактора (T2a ВВЭР/PWR, T2b BWR): 19 ТВС в гексагональной раскладке, ячейка
 * {@link LatticeDesigns#T2_HEX} по среднему составу, водяной отражатель, точечная кинетика, Xe/Sm, остаточное тепло и
 * выгорание по ТВС. Состояние обратных связей (T топлива, плотность и T воды, бор) даёт установка; зона пересчитывает
 * ячейку раз в секунду и между пересчётами идёт линейно по коэффициентам полного k_eff — в малой зоне плотность воды
 * меняет утечку сильнее, чем k∞ ячейки, и производные одной ячейки не годятся.
 *
 * <p>Стержни, контур и защита — у установки: зона получает реактивность стержней числом.
 *
 * <p><b>МОКС.</b> ТВС (канал CANDU) может нести плутоний ({@link PlutoniumCharge}): зона ведёт Pu-239…Pu-242 по
 * позициям, и ячейка видит их сечения как есть — без пересчёта в эквивалентный U-235. Отсюда сами собой: меньшая β_eff
 * (доли делений на Pu-239 с β = 0,0021 и Pu-241 с 0,0054 против 0,0065 у U-235; Keepin, 1965), более жёсткий спектр и
 * меньшая по модулю ценность бора и плотности воды. Ценность стержней установки (числом, Δk/k для урановой зоны)
 * умножается на {@link #rodWorthFactor()}: у МОКС тепловое поглощение больше (σ_a Pu-239 1017 б, Pu-240 290 б,
 * Pu-241 1373 б), и по теории возмущений ценность поглотителя падает с ростом Σ_a зоны
 * ({@link PlutoniumCharge#rodWorthFactor}).
 */
public final class LightWaterCore {
    public static final int POSITIONS = 19;
    public static final double CONVERSION = 0.5, SOURCE_N_S = 1e8, REFLECTOR_M = 0.3;
    /** Доля энергии деления, выделяющаяся с запаздыванием (распад ПД). */
    public static final double DECAY_SHARE = DecayHeat.afterInfiniteOperation(0);

    /**
     * Что за зона: решётка, число позиций (ТВС или каналов), раскладка, отражатель, сборок по высоте в позиции.
     *
     * @param squareGrid         позиции по квадратной сетке от центра (каналы CANDU), иначе — гексагональные кольца ВВЭР
     * @param stackedPerPosition сборок одна над другой в позиции (пучков в канале CANDU — 12)
     */
    public record Spec(String lattice, int positions, boolean squareGrid, CellComposition reflector, double reflectorM,
            int stackedPerPosition, double fallbackEnrichment, FuelDesign defaultDesign, java.util.Set<FuelDesign> designs) {
    }

    /** Корпус ВВЭР/PWR/BWR: 19 ТВС, вода, водяной отражатель 0,3 м. */
    public static final Spec LWR = new Spec(LatticeDesigns.T2_HEX, POSITIONS, false,
            CellComposition.nonFuel(ModeratorKind.NONE, 0, CoolantKind.H2O, 1, 0), REFLECTOR_M, 1, 0.04, FuelDesign.VVER_440,
            java.util.EnumSet.of(FuelDesign.VVER_440, FuelDesign.VVER_1000, FuelDesign.PWR_17X17));

    private final Spec spec;
    private final int count;
    private final double nominalW, dt;
    private final double[] hm0, u5, u8, pu9, fp, burnup, u6, pu0, pu1, pu2, x234, share, radius;
    private final boolean[] failed;
    private FuelDesign design;
    private int loaded;

    private final LatticeModel lattice = new LatticeModel();
    private final CellComposition cell, reflector;
    private final FissionPoisons poisons = new FissionPoisons();
    private final DecayHeat decay = new DecayHeat();
    private final PeriodMeter period;
    private final PointKinetics kinetics = new PointKinetics(DelayedNeutronData.keepin(0), 2e-5, PointKinetics.MIN_POWER);
    private DelayedGroups groups = DelayedNeutronData.keepin(0);
    private double staticRho = -1, keff, betaEff, generation, sigmaA, sigmaF;
    private CellFeedbackState eval = new CellFeedbackState(573, 720, 573, 0, 0, 0);
    private double alphaDoppler, alphaDensity, alphaModerator, alphaBoron, rodWorthFactor = 1;
    private double totalRho, fissionW, slowTimer = 1e9;

    public LightWaterCore(double nominalW, double dt) {
        this(LWR, nominalW, dt);
    }

    public LightWaterCore(Spec spec, double nominalW, double dt) {
        this.spec = spec;
        this.count = spec.positions();
        this.nominalW = nominalW;
        this.dt = dt;
        this.period = new PeriodMeter(dt);
        this.cell = LatticeDesigns.referenceCell(spec.lattice());
        this.reflector = spec.reflector();
        hm0 = new double[count];
        u5 = new double[count];
        u8 = new double[count];
        pu9 = new double[count];
        fp = new double[count];
        burnup = new double[count];
        u6 = new double[count];
        pu0 = new double[count];
        pu1 = new double[count];
        pu2 = new double[count];
        x234 = new double[count];
        share = new double[count];
        radius = new double[count];
        failed = new boolean[count];
        if (spec.squareGrid()) {
            // каналы по квадратной сетке, ближайшие к оси первыми (радиус — в шагах)
            java.util.List<Double> r = new java.util.ArrayList<>();
            int half = (int) Math.ceil(Math.sqrt(count)) + 1;
            for (int i = -half; i < half; i++)
                for (int j = -half; j < half; j++) r.add(Math.hypot(i + 0.5, j + 0.5));
            java.util.Collections.sort(r);
            for (int i = 0; i < count; i++) radius[i] = r.get(i);
        } else {
            // гексагональная решётка: центр, кольцо из 6 на шаге, кольцо из 12 (6 на √3 шага, 6 на 2 шагах)
            for (int i = 0; i < count; i++) radius[i] = i == 0 ? 0 : i <= 6 ? 1 : i % 2 == 1 ? Math.sqrt(3) : 2;
        }
    }

    public Spec spec() {
        return spec;
    }

    public int positions() {
        return count;
    }

    /** Шаг ТВС под ключ, м (ВВЭР-440 — 147 мм, ВВЭР-1000 — 236 мм, PWR 17×17 — 215 мм). */
    public static double pitch(FuelDesign d) {
        return switch (d) {
            case VVER_440 -> 0.147;
            case VVER_1000 -> 0.236;
            case PWR_17X17 -> 0.215;
            case CANDU_37 -> 0.286;
            default -> 0.147;
        };
    }

    /** Конструкции ТВС легководного корпуса. */
    public static boolean accepts(FuelDesign d) {
        return d == FuelDesign.VVER_440 || d == FuelDesign.VVER_1000 || d == FuelDesign.PWR_17X17;
    }

    // ------------------------------------------------------------------ топливо

    /** Загрузка ТВС; {@code shutDown} — состояние установки. Конструкция — одна на корпус. */
    public boolean load(boolean shutDown, int pos, FuelDesign d, double hmKg, double u235, double u234) {
        return load(shutDown, pos, d, hmKg, u235, u234, PlutoniumCharge.NONE);
    }

    /**
     * Загрузка ТВС МОКС: уран {@code uKg} с долями U-235 и U-234 в уране и плутоний {@code pu}. Доля Pu в ТМ — не выше
     * {@link MoxBlend#MAX_PU}: дальше коэффициент по пустотам положительный, такое топливо в тепловой реактор не
     * лицензируют.
     */
    public boolean load(boolean shutDown, int pos, FuelDesign d, double uKg, double u235, double u234, PlutoniumCharge pu) {
        double hmKg = uKg + pu.totalKg();
        if (!shutDown || hm0[pos] > 0 || uKg <= 0 || !spec.designs().contains(d)) return false;
        if (!pu.thermalLicensable(uKg)) return false;
        if (design != null && loaded > 0 && design != d) return false;
        design = d;
        hm0[pos] = hmKg;
        u5[pos] = uKg * u235;
        u8[pos] = uKg * (1 - u235 - u234);
        x234[pos] = u234 * uKg / hmKg;
        fp[pos] = burnup[pos] = u6[pos] = 0;
        pu9[pos] = pu.pu239Kg();
        pu0[pos] = pu.pu240Kg();
        pu1[pos] = pu.pu241Kg();
        pu2[pos] = pu.pu242Kg();
        failed[pos] = false;
        countLoaded();
        slowTimer = 1e9;
        return true;
    }

    /**
     * Состав ТВС: начальный ТМ, U-235, U-238, U-236, Pu-239, Pu-240, Pu-241, Pu-242 (с Pu-238 МОКС), ПД, кг; U-234 —
     * доля исходного ТМ; выгорание.
     */
    public record AssemblyFuel(FuelDesign design, double hm0Kg, double u235Kg, double u238Kg, double u236Kg, double pu239Kg,
            double pu240Kg, double pu241Kg, double pu242Kg, double fpKg, double u234, double burnupMwdKg, boolean failed) {
        public double uraniumKg() {
            return u235Kg + u238Kg + u236Kg + hm0Kg * u234;
        }

        public double plutoniumKg() {
            return pu239Kg + pu240Kg + pu241Kg + pu242Kg;
        }
    }

    public AssemblyFuel fuel(int pos) {
        return new AssemblyFuel(design, hm0[pos], u5[pos], u8[pos], u6[pos], pu9[pos], pu0[pos], pu1[pos], pu2[pos], fp[pos], x234[pos],
                burnup[pos], failed[pos]);
    }

    public AssemblyFuel unload(boolean shutDown, int pos) {
        if (!shutDown || hm0[pos] <= 0) return null;
        AssemblyFuel f = fuel(pos);
        hm0[pos] = u5[pos] = u8[pos] = pu9[pos] = fp[pos] = burnup[pos] = u6[pos] = pu0[pos] = pu1[pos] = pu2[pos] = 0;
        failed[pos] = false;
        countLoaded();
        if (loaded == 0) design = null;
        slowTimer = 1e9;
        return f;
    }

    private void countLoaded() {
        loaded = 0;
        for (double m : hm0) if (m > 0) loaded++;
    }

    public boolean loaded(int pos) {
        return hm0[pos] > 0;
    }

    public int loadedAssemblies() {
        return loaded;
    }

    public FuelDesign design() {
        return design;
    }

    public double heavyMetalKg() {
        double s = 0;
        for (double m : hm0) s += m;
        return s;
    }

    /** Суммарная длина твэлов, м (теплопередача топливо → вода). */
    public double rodLengthM() {
        return design == null ? 0 : loaded * design.rods * design.stackM * spec.stackedPerPosition();
    }

    public double share(int pos) {
        return share[pos];
    }

    public double maxShare() {
        double m = 0;
        for (double s : share) m = Math.max(m, s);
        return m;
    }

    public boolean failed(int pos) {
        return failed[pos];
    }

    void fail(int pos) {
        failed[pos] = true;
    }

    /**
     * Breaches the cladding of the {@code n} hottest intact loaded assemblies (largest power share first) — the
     * hot rods burst first when the whole core heats up uncovered.
     *
     * @return how many assemblies failed now
     */
    public int failHottest(int n) {
        int done = 0;
        while (done < n) {
            int best = -1;
            for (int i = 0; i < count; i++)
                if (loaded(i) && !failed[i] && (best < 0 || share[i] > share[best])) best = i;
            if (best < 0) break;
            failed[best] = true;
            done++;
        }
        return done;
    }

    public int failedAssemblies() {
        int n = 0;
        for (boolean f : failed) if (f) n++;
        return n;
    }

    public double burnup(int pos) {
        return burnup[pos];
    }

    public double maxBurnup() {
        double m = 0;
        for (double b : burnup) m = Math.max(m, b);
        return m;
    }

    // ------------------------------------------------------------------ геометрия

    public double coreRadius() {
        FuelDesign d = design == null ? spec.defaultDesign() : design;
        double a = d.hexagonal ? Math.sqrt(3) / 2 * pitch(d) * pitch(d) : pitch(d) * pitch(d);
        return Math.sqrt(Math.max(loaded, 1) * a / Math.PI);
    }

    public double coreHeight() {
        return (design == null ? spec.defaultDesign() : design).stackM * spec.stackedPerPosition();
    }

    // ------------------------------------------------------------------ нейтроника

    private static double sigmaXe(double tn) {
        return FissionPoisons.SIGMA_XE_B * Westcott.g(Westcott.Factor.XE135_A, tn) * Westcott.oneOverV(tn);
    }

    private static double sigmaSm(double tn) {
        return FissionPoisons.SIGMA_SM_B * Westcott.g(Westcott.Factor.SM149_A, tn) * Westcott.oneOverV(tn);
    }

    /** Состояние обратных связей с текущими ядами: T топлива, плотность воды, T воды, бор ppm. */
    public CellFeedbackState state(double fuelK, double waterDensity, double waterK, double boronPpm) {
        double tn = waterK + LatticeModel.NEUTRON_GAS_OFFSET;
        return new CellFeedbackState(Math.max(1, fuelK), Math.max(1, waterDensity), Math.max(1, waterK), Math.max(0, boronPpm),
                poisons.xenonAbs(sigmaXe(tn)), poisons.samariumAbs(sigmaSm(tn)));
    }

    /** Пересчёт ячейки и коэффициентов при состоянии {@code s}. */
    public void solveStatic(CellFeedbackState s) {
        countLoaded();
        NuclideVector fuel = FuelDepletion.averageFuel(hm0, u5, u8, pu9, pu0, pu1, pu2, fp, spec.fallbackEnrichment());
        SixFactor sf = lattice.evaluate(cell, fuel, s);
        rodWorthFactor = loaded > 0 ? rodWorthFactor(fuel, sf, s) : 1;
        SixFactor refl = lattice.evaluate(reflector, fuel, s);
        double delta = lattice.reflectorSavings(sf, refl, spec.reflectorM());
        double b2 = lattice.geometricBuckling(ReactorShape.cylinder(coreRadius(), coreHeight()), delta);
        keff = loaded > 0 ? lattice.estimateKeff(sf, b2) : 0;
        staticRho = loaded > 0 ? (keff - 1) / keff : -1;
        eval = s;
        if (loaded > 0) {
            double tf = s.fuelEffTemp(), rho = s.coolantDensity(), tm = s.moderatorTemp(), b = s.boronPpm();
            alphaDoppler = (rhoAt(fuel, s.withFuelTemp(tf + 10)) - rhoAt(fuel, s.withFuelTemp(tf - 10))) / 20;
            double rLo = Math.max(1, rho - 5);
            alphaDensity = (rhoAt(fuel, s.withCoolantDensity(rho + 5)) - rhoAt(fuel, s.withCoolantDensity(rLo))) / (rho + 5 - rLo);
            alphaModerator = (rhoAt(fuel, s.withModeratorTemp(tm + 5)) - rhoAt(fuel, s.withModeratorTemp(tm - 5))) / 10;
            double bLo = Math.max(0, b - 20);
            alphaBoron = (rhoAt(fuel, s.withBoron(b + 20)) - rhoAt(fuel, s.withBoron(bLo))) / (b + 20 - bLo);
        } else {
            alphaDoppler = alphaDensity = alphaModerator = alphaBoron = 0;
        }
        sigmaA = sf.sigmaA();
        sigmaF = sf.sigmaF();
        DelayedGroups g = lattice.delayedGroups(lattice.fissionFractions(cell, fuel, s), b2, sf);
        generation = lattice.generationTime(sf, s.moderatorTemp() + LatticeModel.NEUTRON_GAS_OFFSET);
        betaEff = g.betaTotal();
        groups = g;
        kinetics.setParameters(g, generation);
        double r = coreRadius() + delta, step = pitch(design == null ? spec.defaultDesign() : design), sum = 0;
        for (int i = 0; i < count; i++) {
            share[i] = hm0[i] > 0 ? Math.max(0.05, j0(2.405 * radius[i] * step / r)) : 0;
            sum += share[i];
        }
        for (int i = 0; i < count; i++) share[i] = sum > 0 ? share[i] / sum : 0;
    }

    /** Множитель ценности стержней по Σ_a уранового двойника ({@link PlutoniumCharge#rodWorthFactor}); без Pu — ровно 1. */
    private double rodWorthFactor(NuclideVector fuel, SixFactor sf, CellFeedbackState s) {
        if (!PlutoniumCharge.hasPlutonium(fuel) || sf.sigmaA() <= 0) return 1;
        return PlutoniumCharge.rodWorthFactor(lattice.evaluate(cell, PlutoniumCharge.uraniumEquivalent(fuel), s).sigmaA(), sf.sigmaA());
    }

    /** Ценность стержней этой зоны к урановой зоне того же эквивалентного обогащения (1 — без плутония). */
    public double rodWorthFactor() {
        return rodWorthFactor;
    }

    private double rhoAt(NuclideVector fuel, CellFeedbackState s) {
        SixFactor sf = lattice.evaluate(cell, fuel, s);
        double delta = lattice.reflectorSavings(sf, lattice.evaluate(reflector, fuel, s), spec.reflectorM());
        double k = lattice.estimateKeff(sf, lattice.geometricBuckling(ReactorShape.cylinder(coreRadius(), coreHeight()), delta));
        return (k - 1) / k;
    }

    static double j0(double x) {
        double y = Math.min(3, Math.abs(x)) / 3, y2 = y * y;
        return 1 + y2 * (-2.2499997 + y2 * (1.2656208 + y2 * (-0.3163866 + y2 * (0.0444479 + y2 * (-0.0039444 + y2 * 0.0002100)))));
    }

    /** Реактивность обратных связей и стержней от последнего пересчёта, Δk/k. */
    public double reactivity(CellFeedbackState s, double rods) {
        return staticRho + alphaDoppler * (s.fuelEffTemp() - eval.fuelEffTemp())
                + alphaDensity * (s.coolantDensity() - eval.coolantDensity())
                + alphaModerator * (s.moderatorTemp() - eval.moderatorTemp()) + alphaBoron * (s.boronPpm() - eval.boronPpm())
                + rods * rodWorthFactor;
    }

    /**
     * Шаг: раз в секунду пересчёт ячейки, кинетика, остаточное тепло, яды, выгорание, период.
     *
     * @return мощность делений, доля номинала
     */
    public double step(double gameDt, CellFeedbackState s, double rods) {
        slowTimer += dt;
        if (slowTimer >= 1) {
            slowTimer = 0;
            solveStatic(s);
        }
        totalRho = reactivity(s, rods);
        double n = loaded > 0 ? kinetics.step(dt, totalRho, PointKinetics.normalizedSource(SOURCE_N_S, 2.43, generation, nominalW))
                : PointKinetics.MIN_POWER;
        fissionW = n * nominalW;
        decay.step(dt, fissionW);
        double f = 0, phi = 0;
        double vCm3 = Math.PI * Math.pow(coreRadius() * 100, 2) * coreHeight() * 100;
        if (loaded > 0 && sigmaF > 0) {
            f = fissionW / (PointKinetics.FISSION_ENERGY * vCm3);
            phi = f / sigmaF;
        }
        double tn = s.moderatorTemp() + LatticeModel.NEUTRON_GAS_OFFSET;
        poisons.step(gameDt, f, phi, sigmaXe(tn), sigmaSm(tn));
        if (fissionW > 0 && gameDt > 0)
            for (int i = 0; i < count; i++)
                if (share[i] > 0) FuelDepletion.deplete(i, fissionW * share[i], gameDt, CONVERSION, hm0, u5, u8, u6, pu9, pu0, pu1, pu2, fp,
                            burnup);
        period.sample(Math.max(n, PointKinetics.MIN_POWER));
        return n;
    }

    /** Средний тепловой поток в зоне при текущей мощности, н/(см²·с). */
    public double averageFlux() {
        double vCm3 = Math.PI * Math.pow(coreRadius() * 100, 2) * coreHeight() * 100;
        return loaded > 0 && sigmaF > 0 ? fissionW / (PointKinetics.FISSION_ENERGY * vCm3) / sigmaF : 0;
    }

    /** Объём активной зоны, м³. */
    public double coreVolumeM3() {
        return Math.PI * coreRadius() * coreRadius() * coreHeight();
    }

    /**
     * Перегрузка на ходу (CANDU): в позицию вдвигается {@code kgIn} свежего топлива, с другого конца выходит столько же,
     * если позиция полна ({@code fullKg}). Выходящее — среднего состава позиции [упрощение: в жизни выходят самые
     * выгоревшие пучки у выходного торца]. Возвращает выгруженное или null, если позиция не была полна.
     */
    public AssemblyFuel shift(int pos, FuelDesign d, double kgIn, double u235, double u234, double fullKg) {
        return shift(pos, d, kgIn, u235, u234, PlutoniumCharge.NONE, fullKg);
    }

    /**
     * Перегрузка на ходу топливом МОКС: {@code uKgIn} урана (доли U-235 и U-234 в уране) и плутоний {@code pu}; позиция
     * полна при {@code fullKg} тяжёлого металла.
     */
    public AssemblyFuel shift(int pos, FuelDesign d, double uKgIn, double u235, double u234, PlutoniumCharge pu, double fullKg) {
        double kgIn = uKgIn + pu.totalKg();
        if (uKgIn <= 0 || !spec.designs().contains(d) || design != null && loaded > 0 && design != d) return null;
        if (!pu.thermalLicensable(uKgIn)) return null;
        design = d;
        double m = hm0[pos], out = Math.max(0, m + kgIn - fullKg);
        AssemblyFuel discharged = null;
        if (out > 0 && m > 0) {
            double f = Math.min(1, out / m);
            discharged = new AssemblyFuel(d, m * f, u5[pos] * f, u8[pos] * f, u6[pos] * f, pu9[pos] * f, pu0[pos] * f, pu1[pos] * f,
                    pu2[pos] * f, fp[pos] * f, x234[pos], burnup[pos], failed[pos]);
            hm0[pos] *= 1 - f;
            u5[pos] *= 1 - f;
            u8[pos] *= 1 - f;
            u6[pos] *= 1 - f;
            pu9[pos] *= 1 - f;
            pu0[pos] *= 1 - f;
            pu1[pos] *= 1 - f;
            pu2[pos] *= 1 - f;
            fp[pos] *= 1 - f;
        }
        double left = hm0[pos], x234In = u234 * uKgIn / kgIn;
        burnup[pos] = left + kgIn > 0 ? burnup[pos] * left / (left + kgIn) : 0;
        x234[pos] = left + kgIn > 0 ? (x234[pos] * left + x234In * kgIn) / (left + kgIn) : x234In;
        hm0[pos] += kgIn;
        u5[pos] += uKgIn * u235;
        u8[pos] += uKgIn * (1 - u235 - u234);
        pu9[pos] += pu.pu239Kg();
        pu0[pos] += pu.pu240Kg();
        pu1[pos] += pu.pu241Kg();
        pu2[pos] += pu.pu242Kg();
        countLoaded();
        slowTimer = 1e9;
        return discharged;
    }

    /** Тепло в топливо: мгновенная часть энергии деления и остаточное тепло, Вт. */
    public double heatW() {
        return fissionW * (1 - DECAY_SHARE) + decay.powerW();
    }

    /** Кинетика в равновесии на мощности {@code n} (горячий старт, загрузка): яды не трогаются. */
    public void equilibrate(CellFeedbackState s, double n) {
        solveStatic(s);
        kinetics.resetToEquilibrium(n);
        period.reset(); // скачок мощности горячего старта — не разгон
        decay.equilibrium(n * nominalW);
    }

    /** Пересчитать на следующем шаге (после загрузки ТВС, смены бора). */
    public void invalidate() {
        slowTimer = 1e9;
    }

    public double power() {
        return kinetics.power();
    }

    public double fissionW() {
        return fissionW;
    }

    public double decayHeatW() {
        return decay.powerW();
    }

    public double periodS() {
        return period.period();
    }

    public double reactivity() {
        return totalRho;
    }

    public double keffStatic() {
        return keff;
    }

    public double betaEff() {
        return betaEff;
    }

    public double generationTime() {
        return generation;
    }

    public DelayedGroups delayedGroups() {
        return groups;
    }

    public double dopplerCoefficient() {
        return alphaDoppler;
    }

    public double densityCoefficient() {
        return alphaDensity;
    }

    public double moderatorSpectralCoefficient() {
        return alphaModerator;
    }

    public double boronCoefficient() {
        return alphaBoron;
    }

    public double xenonReactivity(double waterK) {
        double tn = waterK + LatticeModel.NEUTRON_GAS_OFFSET;
        return sigmaA > 0 ? -poisons.xenonAbs(sigmaXe(tn)) / sigmaA : 0;
    }

    public FissionPoisons poisons() {
        return poisons;
    }

    // ------------------------------------------------------------------ сохранение

    public void save(StateWriter out) {
        out.putDoubles("hm0", hm0, count);
        out.putDoubles("u5", u5, count);
        out.putDoubles("u8", u8, count);
        out.putDoubles("pu9", pu9, count);
        out.putDoubles("fp", fp, count);
        out.putDoubles("bu", burnup, count);
        out.putDoubles("u6", u6, count);
        out.putDoubles("pu0", pu0, count);
        out.putDoubles("pu1", pu1, count);
        out.putDoubles("pu2", pu2, count);
        out.putDoubles("x234", x234, count);
        int[] f = new int[count];
        for (int i = 0; i < count; i++) f[i] = failed[i] ? 1 : 0;
        out.putInts("failed", f, count);
        out.putString("design", design == null ? "" : design.name());
        out.putDouble("n", power());
        out.beginObject("poisons");
        poisons.save(out);
        out.endObject();
        out.beginObject("decay");
        decay.save(out);
        out.endObject();
    }

    /** Загрузка; кинетика — в равновесии на сохранённой мощности при состоянии, которое даст {@code state}. */
    public void load(StateReader in, java.util.function.Supplier<CellFeedbackState> state) {
        copy(in, "hm0", hm0);
        copy(in, "u5", u5);
        copy(in, "u8", u8);
        copy(in, "pu9", pu9);
        copy(in, "fp", fp);
        copy(in, "bu", burnup);
        copy(in, "u6", u6);
        copy(in, "pu0", pu0);
        copy(in, "pu1", pu1);
        copy(in, "pu2", pu2);
        copy(in, "x234", x234);
        if (in.has("failed")) {
            int[] f = in.getInts("failed");
            for (int i = 0; i < Math.min(count, f.length); i++) failed[i] = f[i] != 0;
        }
        String d = in.optString("design", "");
        design = null;
        for (FuelDesign x : FuelDesign.values()) if (x.name().equals(d)) design = x;
        if (in.has("poisons")) {
            in.beginObject("poisons");
            poisons.load(in);
            in.endObject();
        }
        if (in.has("decay")) {
            in.beginObject("decay");
            decay.load(in);
            in.endObject();
        }
        countLoaded();
        solveStatic(state.get());
        kinetics.resetToEquilibrium(Math.max(PointKinetics.MIN_POWER, in.optDouble("n", 0)));
        period.reset(); // мощность восстановлена из сохранения, а не выросла
    }

    /** Сохранённая мощность — для защиты при загрузке. */
    public static double savedPower(StateReader in) {
        return Math.max(PointKinetics.MIN_POWER, in.optDouble("n", 0));
    }

    private static void copy(StateReader in, String key, double[] to) {
        if (!in.has(key)) return;
        double[] a = in.getDoubles(key);
        System.arraycopy(a, 0, to, 0, Math.min(a.length, to.length));
    }
}
