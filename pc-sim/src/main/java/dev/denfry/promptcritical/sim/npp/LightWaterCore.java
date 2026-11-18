package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
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
 */
public final class LightWaterCore {
    public static final int POSITIONS = 19;
    public static final double CONVERSION = 0.5, SOURCE_N_S = 1e8, REFLECTOR_M = 0.3;
    /** Доля энергии деления, выделяющаяся с запаздыванием (распад ПД). */
    public static final double DECAY_SHARE = DecayHeat.afterInfiniteOperation(0);

    private final double nominalW, dt;
    private final double[] hm0 = new double[POSITIONS], u5 = new double[POSITIONS], u8 = new double[POSITIONS],
            pu9 = new double[POSITIONS], fp = new double[POSITIONS], burnup = new double[POSITIONS], u6 = new double[POSITIONS],
            pu0 = new double[POSITIONS], x234 = new double[POSITIONS], share = new double[POSITIONS], radius = new double[POSITIONS];
    private final boolean[] failed = new boolean[POSITIONS];
    private FuelDesign design;
    private int loaded;

    private final LatticeModel lattice = new LatticeModel();
    private final CellComposition cell = LatticeDesigns.referenceCell(LatticeDesigns.T2_HEX);
    private final CellComposition reflector = CellComposition.nonFuel(ModeratorKind.NONE, 0, CoolantKind.H2O, 1, 0);
    private final FissionPoisons poisons = new FissionPoisons();
    private final DecayHeat decay = new DecayHeat();
    private final PeriodMeter period;
    private final PointKinetics kinetics = new PointKinetics(DelayedNeutronData.keepin(0), 2e-5, PointKinetics.MIN_POWER);
    private DelayedGroups groups = DelayedNeutronData.keepin(0);
    private double staticRho = -1, keff, betaEff, generation, sigmaA, sigmaF;
    private CellFeedbackState eval = new CellFeedbackState(573, 720, 573, 0, 0, 0);
    private double alphaDoppler, alphaDensity, alphaModerator, alphaBoron;
    private double totalRho, fissionW, slowTimer = 1e9;

    public LightWaterCore(double nominalW, double dt) {
        this.nominalW = nominalW;
        this.dt = dt;
        this.period = new PeriodMeter(dt);
        // гексагональная решётка: центр, кольцо из 6 на шаге, кольцо из 12 (6 на √3 шага, 6 на 2 шагах)
        for (int i = 0; i < POSITIONS; i++) radius[i] = i == 0 ? 0 : i <= 6 ? 1 : i % 2 == 1 ? Math.sqrt(3) : 2;
    }

    /** Шаг ТВС под ключ, м (ВВЭР-440 — 147 мм, ВВЭР-1000 — 236 мм, PWR 17×17 — 215 мм). */
    public static double pitch(FuelDesign d) {
        return switch (d) {
            case VVER_440 -> 0.147;
            case VVER_1000 -> 0.236;
            case PWR_17X17 -> 0.215;
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
        if (!shutDown || hm0[pos] > 0 || hmKg <= 0 || !accepts(d)) return false;
        if (design != null && loaded > 0 && design != d) return false;
        design = d;
        hm0[pos] = hmKg;
        u5[pos] = hmKg * u235;
        u8[pos] = hmKg * (1 - u235 - u234);
        x234[pos] = u234;
        pu9[pos] = fp[pos] = burnup[pos] = u6[pos] = pu0[pos] = 0;
        failed[pos] = false;
        countLoaded();
        slowTimer = 1e9;
        return true;
    }

    /** Состав ТВС: начальный ТМ, U-235, U-238, U-236, Pu-239, Pu-240, ПД, кг; U-234 в исходном; выгорание. */
    public record AssemblyFuel(FuelDesign design, double hm0Kg, double u235Kg, double u238Kg, double u236Kg, double pu239Kg,
            double pu240Kg, double fpKg, double u234, double burnupMwdKg, boolean failed) {
        public double uraniumKg() {
            return u235Kg + u238Kg + u236Kg + hm0Kg * u234;
        }

        public double plutoniumKg() {
            return pu239Kg + pu240Kg;
        }
    }

    public AssemblyFuel fuel(int pos) {
        return new AssemblyFuel(design, hm0[pos], u5[pos], u8[pos], u6[pos], pu9[pos], pu0[pos], fp[pos], x234[pos], burnup[pos], failed[pos]);
    }

    public AssemblyFuel unload(boolean shutDown, int pos) {
        if (!shutDown || hm0[pos] <= 0) return null;
        AssemblyFuel f = fuel(pos);
        hm0[pos] = u5[pos] = u8[pos] = pu9[pos] = fp[pos] = burnup[pos] = u6[pos] = pu0[pos] = 0;
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
        return design == null ? 0 : loaded * design.rods * design.stackM;
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
        FuelDesign d = design == null ? FuelDesign.VVER_440 : design;
        double a = d.hexagonal ? Math.sqrt(3) / 2 * pitch(d) * pitch(d) : pitch(d) * pitch(d);
        return Math.sqrt(Math.max(loaded, 1) * a / Math.PI);
    }

    public double coreHeight() {
        return design == null ? FuelDesign.VVER_440.stackM : design.stackM;
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
        NuclideVector fuel = FuelDepletion.averageFuel(hm0, u5, u8, pu9, fp, 0.04);
        SixFactor sf = lattice.evaluate(cell, fuel, s);
        SixFactor refl = lattice.evaluate(reflector, fuel, s);
        double delta = lattice.reflectorSavings(sf, refl, REFLECTOR_M);
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
        double r = coreRadius() + delta, step = pitch(design == null ? FuelDesign.VVER_440 : design), sum = 0;
        for (int i = 0; i < POSITIONS; i++) {
            share[i] = hm0[i] > 0 ? Math.max(0.05, j0(2.405 * radius[i] * step / r)) : 0;
            sum += share[i];
        }
        for (int i = 0; i < POSITIONS; i++) share[i] = sum > 0 ? share[i] / sum : 0;
    }

    private double rhoAt(NuclideVector fuel, CellFeedbackState s) {
        SixFactor sf = lattice.evaluate(cell, fuel, s);
        double delta = lattice.reflectorSavings(sf, lattice.evaluate(reflector, fuel, s), REFLECTOR_M);
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
                + alphaModerator * (s.moderatorTemp() - eval.moderatorTemp()) + alphaBoron * (s.boronPpm() - eval.boronPpm()) + rods;
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
            for (int i = 0; i < POSITIONS; i++)
                if (share[i] > 0) FuelDepletion.deplete(i, fissionW * share[i], gameDt, CONVERSION, hm0, u5, u8, u6, pu9, pu0, fp, burnup);
        period.sample(Math.max(n, PointKinetics.MIN_POWER));
        return n;
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
        out.putDoubles("hm0", hm0, POSITIONS);
        out.putDoubles("u5", u5, POSITIONS);
        out.putDoubles("u8", u8, POSITIONS);
        out.putDoubles("pu9", pu9, POSITIONS);
        out.putDoubles("fp", fp, POSITIONS);
        out.putDoubles("bu", burnup, POSITIONS);
        out.putDoubles("u6", u6, POSITIONS);
        out.putDoubles("pu0", pu0, POSITIONS);
        out.putDoubles("x234", x234, POSITIONS);
        int[] f = new int[POSITIONS];
        for (int i = 0; i < POSITIONS; i++) f[i] = failed[i] ? 1 : 0;
        out.putInts("failed", f, POSITIONS);
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
        copy(in, "x234", x234);
        if (in.has("failed")) {
            int[] f = in.getInts("failed");
            for (int i = 0; i < Math.min(POSITIONS, f.length); i++) failed[i] = f[i] != 0;
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
