package dev.denfry.promptcritical.sim.nuclide;

/** Нуклидный состав топливного узла только для чтения (01 §1.13.3). */
public interface NuclideView {
    /** Число ядер. */
    double atoms(Nuclide n);

    double heavyMetalMassKg();

    /** Суммарное число ядер тяжёлых металлов. */
    default double heavyMetalAtoms() {
        double s = 0;
        for (Nuclide n : Nuclide.values()) if (n.heavyMetal()) s += atoms(n);
        return s;
    }
}
