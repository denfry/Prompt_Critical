package dev.denfry.promptcritical.sim.neutronics;

import dev.denfry.promptcritical.sim.nuclide.Nuclide;
import dev.denfry.promptcritical.sim.nuclide.NuclideView;
import java.util.Objects;

/**
 * Модель ячейки (01 §1.6.2–1.6.3): k∞ = η f p ε, утечка 1,5-групповая (L², τ), производные k∞
 * по обратным связям центральными разностями (§1.6.4) и параметры кинетики (§1.5.2–1.5.3).
 *
 * <p>Ячейка Вигнера–Зейтца из двух областей: «топливная» (таблетка для открытой решётки и
 * блочков, весь канал с содержимым для кластера) и «замедлитель» (всё остальное в узле).
 * Сечения — тепловые, усреднённые по Максвеллу при T_n = T_m + 50 К, с факторами Весткотта.
 *
 * <p>Отклонения от прототипа GDD (числа §1.6.2 получены, по-видимому, без части этих эффектов):
 * <ul>
 *   <li>η считается с g(T_n) U-235 &lt; 1, поэтому для UO₂ 4 % при 305 °C η = 1,88, а не 1,91
 *       (значение 2200 м/с);</li>
 *   <li>в ε хорда «топливной области» делится на (1 − C_D) с <b>быстрым</b> фактором Данкова
 *       (сечение выведения воды вместо эпитеплового рассеяния) — только так одна константа
 *       c_ε даёт и 1,03–1,05 для блочков T0, и ≈ 1,06 для ВВЭР;</li>
 *   <li>Σ_t быстрых для UO₂ включает кислород (0,28 см⁻¹ против 0,33 для металла).</li>
 * </ul>
 */
public final class LatticeModel {
    /** N_A ×10⁻²⁴: ядер в 1 см³ на (г/см³)/(г/моль), в единицах б⁻¹·см⁻¹. */
    public static final double AVOGADRO_BARN = 0.602214076;
    /** T_n = T_m + 50 К (§1.5.3). */
    public static final double NEUTRON_GAS_OFFSET = 50;
    /** {@code nuclide.lumped_fp_absorption_b} — сечение пары осколков, б. */
    public static final double LFP_ABSORPTION_B = 40;

    private static final double OXYGEN_SIGMA_A = 1.9e-4, OXYGEN_SIGMA_S = 3.8, HEAVY_SIGMA_S = 9.0;
    /** Полные сечения для нейтронов деления (≈ 2 МэВ), б: Σ_t^fast(U-метал) = 0,33 см⁻¹. */
    private static final double HEAVY_FAST_SIGMA_T = 6.9, OXYGEN_FAST_SIGMA_T = 2.5;
    /** ν деления U-238 быстрыми нейтронами (для долей делений). */
    private static final double NU_U238_FAST = 2.8;
    /** τ_d/τ_p: запаздывающие нейтроны рождаются с ~0,4 МэВ против ~2 МэВ (§1.5.2). */
    private static final double DELAYED_AGE_RATIO = 0.6;
    private static final double AGE_NO_MODERATOR = 1e4;
    private static final double SQRT_300 = Math.sqrt(300);

    // Тепловые данные тяжёлых металлов при 2200 м/с (ENDF/B-VII.1, округлено; §1.6.2).
    private static final Nuclide[] HEAVY = {Nuclide.U235, Nuclide.U236, Nuclide.U238, Nuclide.NP237, Nuclide.NP239,
            Nuclide.PU238, Nuclide.PU239, Nuclide.PU240, Nuclide.PU241, Nuclide.PU242, Nuclide.AM241, Nuclide.AM243,
            Nuclide.CM242, Nuclide.CM244};
    private static final double[] SIGMA_A = {680.9, 5.1, 2.68, 175.9, 45, 558, 1017.3, 289.5, 1373, 18.5, 603, 79,
            20, 15};
    private static final double[] SIGMA_F = {585.1, 0, 0, 0.02, 0, 17.9, 747.4, 0.06, 1011.1, 0, 3.1, 0.1, 5, 1};
    private static final double[] NU = {2.43, 0, 0, 2.6, 0, 2.9, 2.88, 2.8, 2.93, 0, 3.2, 3.3, 3.4, 3.4};
    private static final Westcott.Factor[] G_A = {Westcott.Factor.U235_A, null, Westcott.Factor.U238_A, null, null,
            null, Westcott.Factor.PU239_A, Westcott.Factor.PU240_A, Westcott.Factor.PU241_A, null, null, null, null,
            null};
    private static final Westcott.Factor[] G_F = {Westcott.Factor.U235_F, null, null, null, null, null,
            Westcott.Factor.PU239_F, null, Westcott.Factor.PU241_F, null, null, null, null, null};
    private static final int I_U235 = 0, I_U238 = 2, I_PU239 = 6, I_PU241 = 8;

    private final NeutronicsConfig config;

    public LatticeModel() {
        this(NeutronicsConfig.defaults());
    }

    public LatticeModel(NeutronicsConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public NeutronicsConfig config() {
        return config;
    }

    // ------------------------------------------------------------------ константы узла, §1.6.2–1.6.4

    /**
     * Константы узла при состоянии {@code s} и производные k∞ (8 дополнительных вызовов модели).
     * {@code fuel} не нужен для узла без топлива (может быть null).
     */
    public SixFactor evaluate(CellComposition c, NuclideView fuel, CellFeedbackState s) {
        Fuel f = c.hasFuel() ? Fuel.of(fuel) : null;
        Cell base = cell(c, f, s);
        if (f == null) {
            return new SixFactor(0, 0, 0, 0, 0, base.l2 + base.age, base.l2, base.age, base.sigmaA, 0, 0, 0, 0, 0, 0,
                    base.thermalDiffusion, base.slowingDownTime);
        }
        double dTf = derivative(c, f, s, 10, s.fuelEffTemp(), CellFeedbackState::withFuelTemp);
        double dRho = derivative(c, f, s, Math.max(0.01 * s.coolantDensity(), 0.5), s.coolantDensity(),
                CellFeedbackState::withCoolantDensity);
        double dTm = derivative(c, f, s, 5, s.moderatorTemp(), CellFeedbackState::withModeratorTemp);
        double dB = derivative(c, f, s, 10, s.boronPpm(), CellFeedbackState::withBoron);
        double nuSigmaF = base.k * base.sigmaA;
        return new SixFactor(base.eta, base.f, base.p, base.epsilon, base.k, base.l2 + base.age, base.l2, base.age,
                base.sigmaA, nuSigmaF, base.sigmaFThermal * nuSigmaF / base.nuSigmaFThermal, dTf, dRho, dTm, dB,
                base.thermalDiffusion, base.slowingDownTime);
    }

    private interface Perturb {
        CellFeedbackState apply(CellFeedbackState s, double x);
    }

    /** Центральная разность; у нуля (x &lt; h: бор, осушение) — правая. */
    private double derivative(CellComposition c, Fuel f, CellFeedbackState s, double h, double x, Perturb with) {
        double lo = x >= h ? x - h : x;
        double kHi = cell(c, f, with.apply(s, x + h)).k;
        double kLo = cell(c, f, with.apply(s, lo)).k;
        return (kHi - kLo) / (x + h - lo);
    }

    /** Нуклидный состав, приведённый к долям атомов ТМ. */
    private record Fuel(double[] fraction, double meanMass, double lfpPerHeavy) {
        static Fuel of(NuclideView v) {
            Objects.requireNonNull(v, "fuel");
            double hm = v.heavyMetalAtoms();
            if (!(hm > 0)) throw new IllegalArgumentException("no heavy metal");
            double[] x = new double[HEAVY.length];
            double m = 0;
            for (int i = 0; i < HEAVY.length; i++) {
                x[i] = v.atoms(HEAVY[i]) / hm;
                m += x[i] * HEAVY[i].molarMass();
            }
            return new Fuel(x, m, v.atoms(Nuclide.LFP) / hm);
        }
    }

    /** Результат модели ячейки при одном состоянии. */
    private record Cell(double eta, double f, double p, double epsilon, double k, double sigmaA,
            double nuSigmaFThermal, double sigmaFThermal, double l2, double age, double thermalDiffusion,
            double slowingDownTime) {}

    private Cell cell(CellComposition c, Fuel fuel, CellFeedbackState s) {
        double tn = s.moderatorTemp() + NEUTRON_GAS_OFFSET, w = Westcott.oneOverV(tn);
        Medium cool = c.coolant().medium, mod = c.moderator().medium;
        double rhoW = s.coolantDensity() / 1000, rhoM = mod == null ? 0 : mod.densityAt(s.moderatorTemp());
        double ppm = s.boronPpm();
        LatticeDesign d = c.lattice();

        // Доли объёма и поглощение на единицу объёма узла (см⁻¹) по материалам вне топлива.
        double vF = d == null ? 0 : d.fuelFraction(), vC = d == null ? 0 : d.cladFraction();
        double vW = c.coolantFraction(), vM = c.moderatorFraction();
        double vS = c.structureFraction(), vA = c.absorberFraction();
        double sigCool = cool == null ? 0 : cool.absorption2200(rhoW, ppm) * w;
        double sigMod = mod == null ? 0
                : (mod.absorption2200(rhoM, mod.liquidWater ? ppm : 0)
                        + Medium.boronAbsorption(rhoM, c.moderatorImpurityPpmB())) * w;
        double sigStruct = c.structure().numberDensity() * c.structure().sigmaA() * w;
        double sigAbs = c.absorber().sigmaA();
        double trCool = cool == null ? 0 : 1 / (3 * cool.diffusion(rhoW));
        double trMod = mod == null ? 0 : 1 / (3 * mod.diffusion(rhoM));
        double trStruct = transport(c.structure()) + sigStruct, trAbs = sigAbs;
        double aCool = vW * sigCool, aMod = vM * sigMod, aStruct = vS * sigStruct, aAbs = vA * sigAbs;

        double age = age(cool, rhoW, vW, mod, rhoM, vM);
        double tsd = slowingDownTime(cool, rhoW, vW, mod, rhoM, vM);

        if (d == null) {
            double vol = vW + vM + vS + vA;
            double sigmaA = aCool + aMod + aStruct + aAbs;
            double tr = vW * trCool + vM * trMod + vS * trStruct + vA * trAbs;
            double dTh = vol / (3 * Math.max(tr, 1e-12));
            double l2 = sigmaA > 0 ? dTh / (sigmaA / vol) : AGE_NO_MODERATOR;
            return new Cell(0, 0, 0, 0, 0, sigmaA, 0, 0, l2, age, dTh, tsd);
        }

        // Топливо: ядра ТМ, кислород, сосредоточенные ПД; η.
        FuelMaterial fm = d.fuelMaterial();
        double nHeavy = fm.density() * AVOGADRO_BARN / (fuel.meanMass + 16.0 * fm.oxygenPerHeavyAtom());
        double nO = nHeavy * fm.oxygenPerHeavyAtom();
        double sa = 0, sf = 0, nsf = 0;
        for (int i = 0; i < HEAVY.length; i++) {
            double n = nHeavy * fuel.fraction[i];
            if (n == 0) continue;
            double ga = G_A[i] == null ? 1 : Westcott.g(G_A[i], tn), gf = G_F[i] == null ? 1 : Westcott.g(G_F[i], tn);
            sa += n * SIGMA_A[i] * ga * w;
            sf += n * SIGMA_F[i] * gf * w;
            nsf += NU[i] * n * SIGMA_F[i] * gf * w;
        }
        double eta = nsf / sa;
        double saOther = (nO * OXYGEN_SIGMA_A + nHeavy * fuel.lfpPerHeavy * LFP_ABSORPTION_B) * w;
        double trFuel = nHeavy * HEAVY_SIGMA_S * (1 - 2 / (3 * 238.0)) + nO * OXYGEN_SIGMA_S * (1 - 2 / (3 * 16.0))
                + sa + saOther;
        CladMaterial clad = d.cladMaterial();
        double sigClad = clad.numberDensity() * clad.sigmaA() * w, trClad = transport(clad) + sigClad;
        double aHeavy = vF * sa, aFuelOther = vF * saOther + s.xenonAbs() + s.samariumAbs(), aClad = vC * sigClad;

        // Две области ячейки Вигнера–Зейтца.
        boolean cluster = d.geometry() == FuelGeometry.CLUSTER_IN_TUBE;
        double aF, vRegF, trF, aM, vRegM, trM, a, b;
        if (cluster) {
            aF = aHeavy + aFuelOther + aClad + aCool;
            vRegF = vF + vC + vW;
            trF = vF * trFuel + vC * trClad + vW * trCool;
            aM = aMod + aStruct + aAbs;
            vRegM = vM + vS + vA;
            trM = vM * trMod + vS * trStruct + vA * trAbs;
            b = Math.sqrt(1e4 / (Math.PI * d.channelsPerCell()));
            a = Math.sqrt(vRegF * 1e4 / (Math.PI * d.channelsPerCell()));
        } else {
            aF = aHeavy + aFuelOther;
            vRegF = vF;
            trF = vF * trFuel;
            aM = aClad + aCool + aMod + aStruct + aAbs;
            vRegM = vC + vW + vM + vS + vA;
            trM = vC * trClad + vW * trCool + vM * trMod + vS * trStruct + vA * trAbs;
            b = Math.sqrt(1e4 / (Math.PI * d.rodsPerCell()));
            a = d.pelletRadius() * 100;
        }
        double kappaF = Math.sqrt((aF / vRegF) * 3 * trF / vRegF);
        double fLarge = selfShielding(kappaF * a);
        double eMinus1 = 0, lM2 = AGE_NO_MODERATOR;
        if (vRegM > 0 && aM > 0) {
            double sigM = aM / vRegM, dM = vRegM / (3 * trM);
            lM2 = dM / sigM;
            eMinus1 = excessAbsorption(1 / Math.sqrt(lM2), a, b);
        }
        double fCell = 1 / (1 + aM / aF * fLarge + eMinus1);
        double f = aHeavy / aF * fCell;

        // Резонансы U-238: Хеллстранд + Данков + доплер (Роуландс).
        double aPellet = d.pelletRadius() * 100;
        double surface = d.rodsPerCell() * 2 * Math.PI * aPellet / 1e4; // см² поверхности на см³ узла
        double vNear = cluster ? vW : vW + vM;
        double scatNear = cluster ? vW * scattering(cool, rhoW) : vW * scattering(cool, rhoW) + vM * scattering(mod, rhoM);
        double removalNear = cluster ? vW * removal(cool, rhoW) : vW * removal(cool, rhoW) + vM * removal(mod, rhoM);
        double chord = vNear > 0 ? 4 * vNear / surface : 0;
        double shadowed = cluster ? 1 - outwardSurface(d.rodsPerAssembly()) : 1;
        double dancoff = shadowed * (vNear > 0 ? Math.exp(-scatNear / vNear * chord) : 1);
        double dancoffFast = vNear > 0 ? Math.exp(-removalNear / vNear * chord) : 1;
        double sm = 2 / (aPellet * fm.density());
        double resonance = fm.resonanceIntegral300(sm * (1 - dancoff))
                * (1 + fm.dopplerCoefficient(sm) * (Math.sqrt(s.fuelEffTemp()) - SQRT_300));
        double gAdj = cluster ? config.coolantAdjacencyFactor() : 1;
        double slowing = vM * moderatingPower(mod, rhoM) + vW * moderatingPower(cool, rhoW) / gAdj;
        double n238 = nHeavy * fuel.fraction[I_U238];
        double p = Math.exp(-n238 * vF * resonance / Math.max(slowing, 1e-12));

        // Деление U-238 на быстрых нейтронах.
        double sigFast = nHeavy * HEAVY_FAST_SIGMA_T + nO * OXYGEN_FAST_SIGMA_T;
        // Кластер — сосредоточенное топливо: хорда канала по доле топлива в нём; открытая решётка —
        // хорда стержня, удлинённая затенением соседями сквозь тонкий для быстрых нейтронов слой воды.
        double fuelChord = cluster ? vF / vRegF * 2 * a : 2 * aPellet / Math.max(1 - dancoffFast, 1e-3);
        double epsilon = 1 + config.fastFissionCoeff() * fuel.fraction[I_U238] * -Math.expm1(-sigFast * fuelChord);

        // Гомогенизация с весом по потоку: φ_M/φ_F = F + (E−1)a_F/a_M (из 1/f), поглощение в ячейке
        // a_F φ_F / f_cell, средний поток v_F φ_F + v_M φ_M.
        double fluxRatio = aM > 0 ? fLarge + eMinus1 * aF / aM : 1;
        double meanFlux = vRegF + vRegM * fluxRatio;
        double sigmaA = aF / fCell / meanFlux;
        double dTh = (vRegF + vRegM) / (3 * (trF + trM));
        double l2 = lM2 * (1 - f);
        return new Cell(eta, f, p, epsilon, eta * f * p * epsilon, sigmaA, vF * nsf / meanFlux, vF * sf / meanFlux, l2,
                age, dTh, tsd);
    }

    /**
     * Доля поверхности стержней кластера, обращённая наружу — к трубе и отдельному замедлителю,
     * где затенения нет: половина поверхности внешнего кольца. Кольца гексагональной упаковки
     * 1, 6, 12, 18…: N ≈ 3k² + 3k + 1, внешнее кольцо — 6k стержней (18 → 12, 37 → 18).
     */
    static double outwardSurface(int rodsPerAssembly) {
        if (rodsPerAssembly <= 1) return 0.5;
        double k = Math.max(1, Math.round((-3 + Math.sqrt(9 - 12.0 * (1 - rodsPerAssembly))) / 6));
        return 0.5 * Math.min(1, 6 * k / rodsPerAssembly);
    }

    /** F = (κa/2) I₀(κa)/I₁(κa). */
    private static double selfShielding(double x) {
        return x < 1e-4 ? 1 + x * x / 8 : 0.5 * x * Bessel.i0(x) / Bessel.i1(x);
    }

    /** E − 1 для кольца замедлителя a..b (Lamarsh гл. 6). */
    private static double excessAbsorption(double kappa, double a, double b) {
        double ka = kappa * a, kb = kappa * b;
        if (kb < 1e-4) return 0;
        double num = Bessel.i0(ka) * Bessel.k1(kb) + Bessel.k0(ka) * Bessel.i1(kb);
        double den = Bessel.i1(kb) * Bessel.k1(ka) - Bessel.k1(kb) * Bessel.i1(ka);
        return kappa * (b * b - a * a) / (2 * a) * num / den - 1;
    }

    private static double transport(CladMaterial m) {
        return m.numberDensity() * m.sigmaS() * (1 - 2 / (3 * m.massNumber()));
    }

    private static double scattering(Medium m, double rho) {
        return m == null ? 0 : m.scattering(rho);
    }

    private static double removal(Medium m, double rho) {
        return m == null ? 0 : m.removal(rho);
    }

    private static double moderatingPower(Medium m, double rho) {
        return m == null ? 0 : m.moderatingPower(rho);
    }

    /** Возраст: 1/τ = Σ φ_j (ρ_j/ρ_j0)² / τ_j по замедляющим средам (§1.6.3). */
    private static double age(Medium cool, double rhoW, double vW, Medium mod, double rhoM, double vM) {
        double inv = 0;
        if (cool != null) inv += vW * sq(rhoW / cool.refDensity) / cool.age;
        if (mod != null) inv += vM * sq(rhoM / mod.refDensity) / mod.age;
        return inv > 1 / AGE_NO_MODERATOR ? 1 / inv : AGE_NO_MODERATOR;
    }

    /** t_sd, взвешенное по замедляющей способности. */
    private static double slowingDownTime(Medium cool, double rhoW, double vW, Medium mod, double rhoM, double vM) {
        double pw = cool == null ? 0 : vW * cool.moderatingPower(rhoW), pm = mod == null ? 0 : vM * mod.moderatingPower(rhoM);
        if (pw + pm <= 0) return Medium.GRAPHITE.slowingDownTime;
        return ((pw > 0 ? pw * cool.slowingDownTime : 0) + (pm > 0 ? pm * mod.slowingDownTime : 0)) / (pw + pm);
    }

    private static double sq(double x) {
        return x * x;
    }

    // ------------------------------------------------------------------ утечка и критичность, §1.6.3

    /** B_g², см⁻²; {@code reflectorSavings} δ — м, добавляется с каждой стороны. */
    public double geometricBuckling(ReactorShape shape, double reflectorSavings) {
        double delta = 2 * reflectorSavings * 100;
        if (shape.cylindrical()) {
            return sq(2.405 / (shape.equivalentRadius() * 100 + delta / 2)) + sq(Math.PI / (shape.activeHeight() * 100 + delta));
        }
        return sq(Math.PI / (shape.sizeX() * 100 + delta)) + sq(Math.PI / (shape.sizeY() * 100 + delta))
                + sq(Math.PI / (shape.sizeZ() * 100 + delta));
    }

    /** Экстраполированная добавка голой зоны δ = 0,71 λ_tr ≈ 2D, м. */
    public double bareSavings(SixFactor core) {
        return 2 * core.thermalDiffusion() / 100;
    }

    /** Добавка отражателя толщины {@code thicknessM}: δ = (D_c/D_r) M_r tanh(T/M_r), м (одногрупповая теория). */
    public double reflectorSavings(SixFactor core, SixFactor reflector, double thicknessM) {
        double m = Math.sqrt(reflector.migrationArea());
        return core.thermalDiffusion() / reflector.thermalDiffusion() * m * Math.tanh(thicknessM * 100 / m) / 100;
    }

    /** k_eff ≈ k∞/(1 + M²B²). */
    public double estimateKeff(SixFactor average, double buckling2) {
        return average.kInf() / (1 + average.migrationArea() * buckling2);
    }

    // ------------------------------------------------------------------ кинетика, §1.5.2–1.5.3

    /** Средняя скорость максвелловских нейтронов, см/с: (2/√π)·2200·√(T_n/293,6) м/с. */
    public static double meanThermalSpeed(double neutronTemperature) {
        return 2 / Math.sqrt(Math.PI) * 2200e2 * Math.sqrt(neutronTemperature / Westcott.T0);
    }

    /** Λ ≈ t_sd + 1/(v̄ νΣ_f) при k ≈ 1, с (§1.5.3). */
    public double generationTime(SixFactor coreAverage, double neutronTemperature) {
        return coreAverage.slowingDownTime() + 1 / (meanThermalSpeed(neutronTemperature) * coreAverage.nuSigmaF());
    }

    /** Группы с ценностью γ_d = exp(B²(τ_p − τ_d))/ε, без фотонейтронов. */
    public DelayedGroups delayedGroups(double[] fissionFractions, double buckling2, SixFactor avg) {
        return delayedGroups(fissionFractions, buckling2, avg, 0);
    }

    /** То же с фотонейтронными группами доли {@code photoneutronBeta} (см. {@link #photoneutronBeta}). */
    public DelayedGroups delayedGroups(double[] fissionFractions, double buckling2, SixFactor avg,
            double photoneutronBeta) {
        double ageP = avg.age();
        double gamma = DelayedNeutronData.importanceFactor(buckling2, ageP, DELAYED_AGE_RATIO * ageP, avg.epsilon());
        return DelayedNeutronData.withPhotoneutrons(DelayedNeutronData.mix(fissionFractions, gamma), photoneutronBeta,
                config.maxDelayedGroups());
    }

    /** β фотонейтронов решётки: D₂O (теплоноситель или замедлитель) или бериллий (§1.5.6). */
    public double photoneutronBeta(CellComposition c) {
        if (c.coolant() == CoolantKind.D2O || c.moderator() == ModeratorKind.D2O) return config.photoneutronBetaD2o();
        if (c.moderator() == ModeratorKind.BE) return config.photoneutronBetaBe();
        return 0;
    }

    /**
     * Доли делений {@code [U-235, U-238, Pu-239, Pu-241]}: тепловые — по νΣ_f нуклидов, U-238 на
     * быстрых — r = (ε−1)ν̄/(ν₈−1) на тепловое деление. Пока нет {@code NuclideInventory} (§1.9.3).
     */
    public double[] fissionFractions(CellComposition c, NuclideView fuel, CellFeedbackState s) {
        Fuel f = Fuel.of(fuel);
        double tn = s.moderatorTemp() + NEUTRON_GAS_OFFSET;
        double[] rate = new double[DelayedNeutronData.FISSILE_COUNT];
        int[] idx = {I_U235, -1, I_PU239, I_PU241};
        double thermal = 0, nuSum = 0;
        for (int k = 0; k < rate.length; k++) {
            int i = idx[k];
            if (i < 0) continue;
            rate[k] = f.fraction[i] * SIGMA_F[i] * Westcott.g(G_F[i], tn);
            thermal += rate[k];
            nuSum += rate[k] * NU[i];
        }
        double eps = cell(c, f, s).epsilon;
        rate[DelayedNeutronData.U238] = thermal * (eps - 1) * (nuSum / thermal) / (NU_U238_FAST - 1);
        double total = thermal + rate[DelayedNeutronData.U238];
        for (int k = 0; k < rate.length; k++) rate[k] /= total;
        return rate;
    }
}
