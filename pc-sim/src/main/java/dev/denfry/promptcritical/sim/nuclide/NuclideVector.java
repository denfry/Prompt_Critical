package dev.denfry.promptcritical.sim.nuclide;

/**
 * Неизменяемый набор ядер по {@link Nuclide#ordinal()} — свежее топливо и тестовые составы,
 * пока нет {@code NuclideInventory} (01 §1.9.3).
 */
public final class NuclideVector implements NuclideView {
    public static final double AVOGADRO = 6.02214076e23;
    /** Изотопика плутония в {@link #mox}: Pu-238, 239, 240, 241, 242. */
    private static final Nuclide[] PU = {Nuclide.PU238, Nuclide.PU239, Nuclide.PU240, Nuclide.PU241, Nuclide.PU242};

    private final double[] atoms;
    private final double heavyMetalKg;

    private NuclideVector(double[] atoms) {
        this.atoms = atoms;
        double kg = 0;
        for (Nuclide n : Nuclide.values()) if (n.heavyMetal()) kg += atoms[n.ordinal()] * n.molarMass() / AVOGADRO / 1000;
        heavyMetalKg = kg;
    }

    /** Состав по числу ядер (массив по ordinal). */
    public static NuclideVector ofAtoms(double[] atoms) {
        if (atoms.length != Nuclide.values().length) throw new IllegalArgumentException("atoms length");
        return new NuclideVector(atoms.clone());
    }

    /** Уран с массовой долей U-235 {@code u235Fraction}, остальное U-238. */
    public static NuclideVector uranium(double heavyMetalKg, double u235Fraction) {
        return mox(heavyMetalKg, u235Fraction, 0, new double[] {0, 1, 0, 0, 0});
    }

    /**
     * Смесь урана и плутония: {@code puFraction} — массовая доля Pu в ТМ, {@code puVector} — массовые
     * доли Pu-238..242 в плутонии, {@code u235Fraction} — доля U-235 в уране.
     */
    public static NuclideVector mox(double heavyMetalKg, double u235Fraction, double puFraction, double[] puVector) {
        if (puVector.length != PU.length) throw new IllegalArgumentException("puVector");
        double[] a = new double[Nuclide.values().length];
        double grams = heavyMetalKg * 1000, uranium = grams * (1 - puFraction);
        a[Nuclide.U235.ordinal()] = uranium * u235Fraction / Nuclide.U235.molarMass() * AVOGADRO;
        a[Nuclide.U238.ordinal()] = uranium * (1 - u235Fraction) / Nuclide.U238.molarMass() * AVOGADRO;
        double sum = 0;
        for (double w : puVector) sum += w;
        for (int i = 0; i < PU.length; i++) {
            if (puVector[i] == 0) continue;
            a[PU[i].ordinal()] = grams * puFraction * puVector[i] / sum / PU[i].molarMass() * AVOGADRO;
        }
        return new NuclideVector(a);
    }

    /** Копия с изменённым числом ядер одного нуклида. */
    public NuclideVector with(Nuclide n, double count) {
        double[] a = atoms.clone();
        a[n.ordinal()] = count;
        return new NuclideVector(a);
    }

    @Override
    public double atoms(Nuclide n) {
        return atoms[n.ordinal()];
    }

    @Override
    public double heavyMetalMassKg() {
        return heavyMetalKg;
    }
}
