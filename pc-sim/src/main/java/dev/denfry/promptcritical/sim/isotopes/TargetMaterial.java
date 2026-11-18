package dev.denfry.promptcritical.sim.isotopes;

import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.List;

/**
 * Мишени облучательных каналов (12 §12.2): цепочка звеньев, изотопный состав, плотность, форма для самоэкранирования и
 * предел температуры капсулы. Сечения — тепловые (2200 м/с) по ENDF/B-VIII и Mughabghab, Atlas of Neutron Resonances
 * (2006); периоды — ENSDF.
 *
 * <ul>
 *   <li>Co-59 (n,γ) 37,2 б → Co-60 (5,271 г), выгорание Co-60 2,0 б;</li>
 *   <li>Ir-191 (37,3 % природного) (n,γ) 954 б → Ir-192 (73,83 сут), выгорание Ir-192 ≈ 1420 б;</li>
 *   <li>Np-237 (n,γ) 175,9 б → Np-238 (2,117 сут, деление 2088 б) → β⁻ → Pu-238 (87,7 г; (n,γ) 540 б, (n,f) 17 б).</li>
 * </ul>
 *
 * Самоэкранирование f = (1 − e^{−Σd})/(Σd) по эффективной толщине d = 2V/S формы: кобальт — таблетки Ø 6 мм
 * (d ≈ 0,3 см, f = 0,63 — пример §12.1.3), иридий — диски 0,25 мм (так делают источники дефектоскопов: иридий
 * поглощает слишком сильно), NpO₂ — таблетки в алюминиевой оболочке, d ≈ 0,1 см.
 */
public enum TargetMaterial {
    COBALT_METAL(58.933, 1.0, 8.9, 37.2, 0.30, 700,
            List.of(new ChainLink("Co-59", Double.POSITIVE_INFINITY, 37.2, 0, 0),
                    new ChainLink("Co-60", 5.2714 * ChainLink.YEAR, 2.0, 0, 2.600)), 1, Emitter.CO60),
    IRIDIUM_METAL(192.217, 0.373, 22.56, 425, 0.025, 700,
            List.of(new ChainLink("Ir-191", Double.POSITIVE_INFINITY, 954, 0, 0),
                    new ChainLink("Ir-192", 73.829 * ChainLink.DAY, 1420, 0, 1.00)), 1, Emitter.IR192),
    /** Масса — NpO₂ (молярная 269,05 г/моль), ядра — Np-237. */
    NEPTUNIUM_OXIDE(269.05, 1.0, 11.1, 175.9, 0.10, 900,
            List.of(new ChainLink("Np-237", 2.144e6 * ChainLink.YEAR, 175.9, 0, 0),
                    new ChainLink("Np-238", 2.117 * ChainLink.DAY, 0, 2088, 0.9),
                    new ChainLink("Pu-238", 87.7 * ChainLink.YEAR, 540, 17, 5.593)), 2, Emitter.PU238);

    public static final double AVOGADRO = 6.02214076e23, BARN_CM2 = 1e-24;

    /** Молярная масса вещества мишени, г/моль. */
    public final double molarMass;
    /** Доля ядер мишени (первого звена) среди атомов элемента. */
    public final double abundance;
    public final double densityGcc;
    /** Сечение поглощения элемента (природная смесь), б: для самоэкранирования и реактивности. */
    public final double elementAbsorptionB;
    /** Эффективная толщина формы d = 2V/S, см. */
    public final double thicknessCm;
    /** Предел температуры капсулы, К: выше — разгерметизация (алюминиевая оболочка). */
    public final double capsuleLimitK;
    public final List<ChainLink> chain;
    /** Индекс звена-продукта. */
    public final int productLink;
    /** Нуклид продукта в таблице излучателей. */
    public final Emitter product;

    TargetMaterial(double molarMass, double abundance, double densityGcc, double elementAbsorptionB, double thicknessCm,
            double capsuleLimitK, List<ChainLink> chain, int productLink, Emitter product) {
        this.molarMass = molarMass;
        this.abundance = abundance;
        this.densityGcc = densityGcc;
        this.elementAbsorptionB = elementAbsorptionB;
        this.thicknessCm = thicknessCm;
        this.capsuleLimitK = capsuleLimitK;
        this.chain = List.copyOf(chain);
        this.productLink = productLink;
        this.product = product;
    }

    /** Ядер мишени в {@code massKg} вещества. */
    public double targetAtoms(double massKg) {
        return massKg * 1000 / molarMass * AVOGADRO * abundance;
    }

    /** Атомов элемента в {@code massKg} вещества. */
    public double elementAtoms(double massKg) {
        return massKg * 1000 / molarMass * AVOGADRO;
    }

    /** Макросечение поглощения вещества, 1/см. */
    public double sigmaAMacro() {
        return densityGcc / molarMass * AVOGADRO * elementAbsorptionB * BARN_CM2;
    }

    /** Коэффициент самоэкранирования формы мишени. */
    public double selfShielding() {
        return selfShielding(sigmaAMacro() * thicknessCm);
    }

    /** f = (1 − e^{−x})/x для оптической толщины x = Σ_a·d (T12.12: x = 1 → 0,632). */
    public static double selfShielding(double x) {
        return x < 1e-9 ? 1 : -Math.expm1(-x) / x;
    }

    /** Начальные ядра звеньев для свежей мишени. */
    public double[] freshAtoms(double massKg) {
        double[] a = new double[chain.size()];
        a[0] = targetAtoms(massKg);
        return a;
    }

    /**
     * Облучение за шаг: тепловой поток {@code phi} (н/(см²·с)) с самоэкранированием {@code fss}, затем распад. Мишень
     * вне реактора — {@code phi = 0}: только распад и накопление дочернего (Np-238 → Pu-238).
     */
    public void step(double[] atoms, double phi, double fss, double dtS, double[] out) {
        int n = chain.size();
        double[] loss = new double[n], feed = new double[n];
        for (int i = 0; i < n; i++) {
            ChainLink l = chain.get(i);
            double f = i == 0 ? fss : 1;
            loss[i] = l.lambda() + (l.sigmaCaptureB() + l.sigmaFissionB()) * BARN_CM2 * phi * f;
            if (i > 0) {
                ChainLink prev = chain.get(i - 1);
                // захват на мишени ведёт к следующему звену; распад промежуточного (Np-238 β⁻) — тоже
                feed[i] = i == 1 ? prev.sigmaCaptureB() * BARN_CM2 * phi * fss : prev.lambda();
            }
        }
        BatemanStepper.step(loss, feed, atoms, dtS, out);
    }

    /** Активность звена, Бк. */
    public double activityBq(double[] atoms, int link) {
        return chain.get(link).lambda() * atoms[link];
    }

    /** Тепло распада всех звеньев, Вт (вся энергия поглощается в капсуле — оценка сверху для γ). */
    public double decayHeatW(double[] atoms) {
        double w = 0;
        for (int i = 0; i < chain.size(); i++) w += activityBq(atoms, i) * chain.get(i).meanDecayEnergyMeV() * ChainLink.MEV_J;
        return w;
    }

    /** Поглощение мишени, см² (N·σ_a·f): для реактивности канала. */
    public double absorptionCm2(double massKg) {
        return elementAtoms(massKg) * elementAbsorptionB * BARN_CM2 * selfShielding();
    }

    /** Масса продукта, кг. */
    public double productKg(double[] atoms, double productMolarMass) {
        return atoms[productLink] / AVOGADRO * productMolarMass / 1000;
    }
}
