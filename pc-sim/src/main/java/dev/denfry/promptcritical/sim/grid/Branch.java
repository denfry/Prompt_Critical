package dev.denfry.promptcritical.sim.grid;

import java.util.Objects;

/**
 * Ветвь сети (07 §7.1.2). Сопротивления линий и кабелей — в о.е. на S_b и базисное напряжение
 * класса; трансформатор задаётся паспортом и пересчитывается на S_b при добавлении.
 */
public sealed interface Branch permits Branch.Line, Branch.Transformer, Branch.Cable, Branch.Tie {
    BusId from();

    BusId to();

    /** П-схема: z = r + jx, b/2 на каждом конце; b — полная ёмкостная проводимость. */
    record Line(BusId from, BusId to, double rPu, double xPu, double bPu, double iMaxA, ConductorSpec spec,
            double lengthM) implements Branch {
        public Line {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (!(rPu >= 0 && xPu >= 0 && rPu + xPu > 0)) throw new IllegalArgumentException("impedance");
            if (!(bPu >= 0)) throw new IllegalArgumentException("bPu");
        }

        /**
         * ВЛ из провода {@code spec} длиной {@code lengthM} в классе {@code vc}: r₀ — паспорт провода,
         * x₀ = 0,1445·lg(D_ср/r_э) Ом/км задаёт опора, b₀ ≈ 7,58·10⁻⁶/lg(D_ср/r_э) См/км (§7.4.1).
         */
        public static Line overhead(BusId from, BusId to, ConductorSpec spec, double x0OhmPerKm, double lengthM,
                VoltageClass vc, double baseVa) {
            if (!(x0OhmPerKm > 0 && lengthM > 0)) throw new IllegalArgumentException("line data");
            double zb = vc.volts * vc.volts / baseVa, km = lengthM / 1e3;
            double b0 = 7.58e-6 / (x0OhmPerKm / 0.1445);
            return new Line(from, to, spec.r20OhmPerKm() * km / zb, x0OhmPerKm * km / zb, b0 * km * zb,
                    spec.ampacityA(), spec, lengthM);
        }
    }

    record Cable(BusId from, BusId to, double rPu, double xPu, double bPu, double iMaxA, double lengthM)
            implements Branch {
        public Cable {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (!(rPu >= 0 && xPu >= 0 && rPu + xPu > 0)) throw new IllegalArgumentException("impedance");
            if (!(bPu >= 0)) throw new IllegalArgumentException("bPu");
        }
    }

    /**
     * Идеальный трансформатор с отпайкой на стороне ВН + z_k, шунт холостого хода на выводах ВН.
     *
     * @param ukPu напряжение КЗ, о.е. на S_ном
     * @param pkW  потери КЗ, Вт
     * @param p0W  потери холостого хода, Вт
     * @param i0Pu ток холостого хода, о.е. на S_ном
     */
    record Transformer(BusId hv, BusId lv, double sRatedVa, double ukPu, double pkW, double p0W, double i0Pu,
            TapChanger tap, TransformerThermal thermal) implements Branch {
        public Transformer {
            Objects.requireNonNull(hv, "hv");
            Objects.requireNonNull(lv, "lv");
            Objects.requireNonNull(tap, "tap");
            if (!(sRatedVa > 0)) throw new IllegalArgumentException("sRatedVa");
            if (!(ukPu > 0 && ukPu < 1)) throw new IllegalArgumentException("ukPu");
            if (!(pkW >= 0 && pkW < ukPu * sRatedVa)) throw new IllegalArgumentException("pkW");
            if (!(p0W >= 0 && i0Pu >= 0)) throw new IllegalArgumentException("no-load");
        }

        @Override
        public BusId from() {
            return hv;
        }

        @Override
        public BusId to() {
            return lv;
        }
    }

    /** Связь с сетью другой станции (контракт 14), со своим счётчиком. */
    record Tie(BusId from, BusId to, double xPu, long contractId) implements Branch {
        public Tie {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (!(xPu > 0)) throw new IllegalArgumentException("xPu");
        }
    }
}
