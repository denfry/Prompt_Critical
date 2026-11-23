package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import java.util.Objects;

/**
 * Проволока или пруток под волочение (18 §18.6.7): металл, диаметр и длина, м, накопленная холодная деформация
 * ε = ln(A₀/A) с последнего отжига.
 */
public record Wire(Metal metal, double diameterM, double lengthM, double strain) {
    /** Пруток толще этого в волоку не заводят: сначала проковать, м [BALANCE]. */
    public static final double ROD_MAX_M = 0.012;

    /**
     * Металл волочения: кривая упрочнения Холломона σ = K·εⁿ, предел текучести отожжённого, предельная деформация
     * между отжигами и температура рекристаллизационного отжига (Сторожев, Попов «Теория обработки металлов
     * давлением», 1977; ASM Handbook vol. 14 [сверить]).
     */
    public enum Metal {
        COPPER(320e6, 0.54, 70e6, 3.0, 773.15),
        WROUGHT_IRON(450e6, 0.25, 180e6, 1.4, 973.15),
        MILD_STEEL(530e6, 0.26, 240e6, 1.6, 973.15),
        MEDIUM_CARBON(800e6, 0.18, 350e6, 1.2, 973.15);

        public final double kPa, n, yieldAnnealedPa, maxStrain, annealK;

        Metal(double kPa, double n, double yieldAnnealedPa, double maxStrain, double annealK) {
            this.kPa = kPa;
            this.n = n;
            this.yieldAnnealedPa = yieldAnnealedPa;
            this.maxStrain = maxStrain;
            this.annealK = annealK;
        }

        /** Сталь, из которой тянут проволоку; null — семейство не волочат. */
        public static Metal of(SteelFamily f) {
            return switch (f) {
                case WROUGHT_IRON -> WROUGHT_IRON;
                case CARBON -> MILD_STEEL;
                case MEDIUM_CARBON -> MEDIUM_CARBON;
                default -> null;
            };
        }

        /** Предел текучести при деформации ε, Па: не ниже отожжённого. */
        public double yieldPa(double strain) {
            return Math.max(yieldAnnealedPa, kPa * Math.pow(Math.max(strain, 0), n));
        }
    }

    public Wire {
        Objects.requireNonNull(metal, "metal");
        if (!(diameterM > 0) || !(lengthM > 0) || !(strain >= 0) || !Double.isFinite(diameterM)
                || !Double.isFinite(lengthM) || !Double.isFinite(strain)) {
            throw new IllegalArgumentException("diameter and length must be positive, strain non-negative");
        }
    }

    /** Пруток из кованой заготовки: круглый, не толще {@link #ROD_MAX_M}, из волочимой стали; иначе null. */
    public static Wire fromRod(Workpiece w) {
        Metal m = Metal.of(w.grade().family());
        if (m == null || w.section() != Workpiece.Section.ROUND || w.aM() > ROD_MAX_M) return null;
        return new Wire(m, w.aM(), w.lengthM(), 0);
    }

    public double areaM2() {
        return Math.PI * diameterM * diameterM / 4;
    }

    /** Предел текучести после наклёпа, Па. */
    public double strengthPa() {
        return metal.yieldPa(strain);
    }
}
