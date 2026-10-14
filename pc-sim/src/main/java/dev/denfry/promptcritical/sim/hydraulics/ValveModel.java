package dev.denfry.promptcritical.sim.hydraulics;

import java.util.Objects;

/** Арматура (01 §1.2.2): характеристики регулирующих клапанов, обратный и предохранительный клапаны. */
public final class ValveModel {
    public enum Characteristic { LINEAR, EQUAL_PERCENTAGE, QUICK_OPENING }

    /** ζ обратного клапана при обратном токе. */
    public static final double CHECK_REVERSE_ZETA = 1e6;
    /** Обратный клапан садится при ṁ &lt; −0,1 кг/с и открывается при ṁ &gt; 0. */
    public static final double CHECK_HYSTERESIS = 0.1;

    /**
     * @param zetaOpen     ζ полностью открытой арматуры (у обратного клапана — прямой ток)
     * @param rangeability R равнопроцентной характеристики
     * @param setPressure  уставка открытия, Па (только relief)
     * @param blowdown     доля посадки (только relief); NaN — {@code hydraulics.relief_blowdown}
     * @param strokeTime   время полного хода, с; 0 — мгновенно
     */
    public record Spec(Characteristic ch, double zetaOpen, double rangeability, double setPressure,
            double blowdown, double strokeTime) {
        public Spec {
            Objects.requireNonNull(ch, "ch");
            if (!(zetaOpen >= 0)) throw new IllegalArgumentException("zetaOpen < 0");
            if (!(strokeTime >= 0)) throw new IllegalArgumentException("strokeTime < 0");
            if (ch == Characteristic.EQUAL_PERCENTAGE && !(rangeability > 1)) {
                throw new IllegalArgumentException("rangeability must be > 1");
            }
        }

        public static Spec gate(double zetaOpen, double strokeTime) {
            return new Spec(Characteristic.LINEAR, zetaOpen, 50, Double.NaN, Double.NaN, strokeTime);
        }

        public static Spec control(Characteristic ch, double zetaOpen, double strokeTime) {
            return new Spec(ch, zetaOpen, 50, Double.NaN, Double.NaN, strokeTime);
        }

        public static Spec check(double zetaForward) {
            return new Spec(Characteristic.LINEAR, zetaForward, 50, Double.NaN, Double.NaN, 0);
        }

        public static Spec relief(double setPressure, double zetaOpen) {
            return new Spec(Characteristic.QUICK_OPENING, zetaOpen, 50, setPressure, Double.NaN, 0);
        }
    }

    private ValveModel() {}

    /** Относительная пропускная способность C_v(θ): линейная θ, равнопроцентная R^(θ−1), быстрооткрывающая √θ. */
    public static double flowCoefficient(Characteristic ch, double theta, double rangeability) {
        if (theta <= 0) return 0;
        double t = Math.min(theta, 1);
        return switch (ch) {
            case LINEAR -> t;
            case EQUAL_PERCENTAGE -> Math.pow(rangeability, t - 1);
            case QUICK_OPENING -> Math.sqrt(t);
        };
    }

    /** ζ(θ) = ζ_open / C_v(θ)²; при θ ≤ 0 — бесконечность (связь закрыта). */
    public static double zeta(Spec spec, double theta) {
        double cv = flowCoefficient(spec.ch(), theta, spec.rangeability());
        return cv > 0 ? spec.zetaOpen() / (cv * cv) : Double.POSITIVE_INFINITY;
    }
}
