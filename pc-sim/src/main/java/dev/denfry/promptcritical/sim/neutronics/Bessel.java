package dev.denfry.promptcritical.sim.neutronics;

/**
 * Модифицированные функции Бесселя I₀, I₁, K₀, K₁ (Abramowitz &amp; Stegun 9.8.1–9.8.8,
 * относительная погрешность ≤ 2·10⁻⁷) — для коэффициентов F и E ячейки Вигнера–Зейтца.
 */
final class Bessel {
    private Bessel() {}

    static double i0(double x) {
        double ax = Math.abs(x);
        if (ax < 3.75) {
            double y = (x / 3.75) * (x / 3.75);
            return 1 + y * (3.5156229 + y * (3.0899424 + y * (1.2067492 + y * (0.2659732 + y * (0.0360768
                    + y * 0.0045813)))));
        }
        double y = 3.75 / ax;
        return Math.exp(ax) / Math.sqrt(ax) * (0.39894228 + y * (0.01328592 + y * (0.00225319 + y * (-0.00157565
                + y * (0.00916281 + y * (-0.02057706 + y * (0.02635537 + y * (-0.01647633 + y * 0.00392377))))))));
    }

    static double i1(double x) {
        double ax = Math.abs(x), r;
        if (ax < 3.75) {
            double y = (x / 3.75) * (x / 3.75);
            r = ax * (0.5 + y * (0.87890594 + y * (0.51498869 + y * (0.15084934 + y * (0.02658733 + y * (0.00301532
                    + y * 0.00032411))))));
        } else {
            double y = 3.75 / ax;
            r = Math.exp(ax) / Math.sqrt(ax) * (0.39894228 + y * (-0.03988024 + y * (-0.00362018 + y * (0.00163801
                    + y * (-0.01031555 + y * (0.02282967 + y * (-0.02895312 + y * (0.01787654 - y * 0.00420059))))))));
        }
        return x < 0 ? -r : r;
    }

    static double k0(double x) {
        if (x <= 2) {
            double y = x * x / 4;
            return -Math.log(x / 2) * i0(x) + (-0.57721566 + y * (0.42278420 + y * (0.23069756 + y * (0.03488590
                    + y * (0.00262698 + y * (0.00010750 + y * 0.0000074))))));
        }
        double y = 2 / x;
        return Math.exp(-x) / Math.sqrt(x) * (1.25331414 + y * (-0.07832358 + y * (0.02189568 + y * (-0.01062446
                + y * (0.00587872 + y * (-0.00251540 + y * 0.00053208))))));
    }

    static double k1(double x) {
        if (x <= 2) {
            double y = x * x / 4;
            return Math.log(x / 2) * i1(x) + (1 / x) * (1 + y * (0.15443144 + y * (-0.67278579 + y * (-0.18156897
                    + y * (-0.01919402 + y * (-0.00110404 - y * 0.00004686))))));
        }
        double y = 2 / x;
        return Math.exp(-x) / Math.sqrt(x) * (1.25331414 + y * (0.23498619 + y * (-0.03655620 + y * (0.01504268
                + y * (-0.00780353 + y * (0.00325614 - y * 0.00068245))))));
    }
}
