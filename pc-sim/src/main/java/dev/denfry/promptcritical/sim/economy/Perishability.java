package dev.denfry.promptcritical.sim.economy;

/**
 * Скоропортящийся товар (14 §14.6.4): цена за единицу пропорциональна активности в момент сдачи
 * p = p_spec·A(t)/A_spec; ниже доли {@code minFraction} (market.perishable_min_fraction = 0,25) партия не принимается.
 */
public sealed interface Perishability permits Perishability.None, Perishability.Decay {
    /** Доля ценности партии возраста {@code agePhysS} физических секунд от спецификации. */
    double valueFraction(double agePhysS);

    /** Принимает ли NPC-рынок партию этого возраста. */
    boolean accepts(double agePhysS);

    record None() implements Perishability {
        @Override
        public double valueFraction(double agePhysS) {
            return 1;
        }

        @Override
        public boolean accepts(double agePhysS) {
            return true;
        }
    }

    record Decay(String nuclide, double halfLifePhysS, double minFraction) implements Perishability {
        public Decay {
            if (!(halfLifePhysS > 0)) throw new IllegalArgumentException("halfLifePhysS must be > 0");
            if (minFraction < 0 || minFraction > 1) throw new IllegalArgumentException("minFraction must be in [0, 1]");
        }

        @Override
        public double valueFraction(double agePhysS) {
            return Math.pow(2, -Math.max(0, agePhysS) / halfLifePhysS);
        }

        @Override
        public boolean accepts(double agePhysS) {
            return valueFraction(agePhysS) >= minFraction;
        }
    }
}
