package dev.denfry.promptcritical.sim.wind;

/**
 * Поляра профиля лопасти на всём круге углов атаки −180…180°.
 *
 * <p>Безотрывное обтекание (α между отрицательным и положительным срывом): прямая подъёмной силы
 * C_l = a₀·(α − α₀) и параболическая поляра C_d = C_d0 + k·(C_l − C_l,md)². Выше срыва — экстраполяция
 * Viterna–Corrigan (Viterna &amp; Corrigan, «Fixed pitch rotor performance of large horizontal axis wind turbines»,
 * NASA CP-2230, 1982): C_d = B₁·sin²α + B₂·cos α, C_l = A₁·sin 2α + A₂·cos²α / sin α, B₁ = C_d,max =
 * 1,11 + 0,018·AR, коэффициенты A₂, B₂ сшивают её со срывной точкой; за 90° — плоская пластина
 * C_l = C_d,max·sin α·cos α, C_d = C_d,max·sin²α. Обе ветви непрерывны в точках срыва и на 90°.
 *
 * @param liftSlopePerRad   a₀, 1/рад
 * @param zeroLiftRad       α₀ — угол нулевой подъёмной силы, рад
 * @param stallRad          угол положительного срыва, рад
 * @param negativeStallRad  угол отрицательного срыва (&lt; 0), рад
 * @param cd0               минимальное профильное сопротивление
 * @param dragFactor        k параболической поляры
 * @param clMinDrag         C_l при минимальном сопротивлении
 * @param aspectRatio       удлинение лопасти R/c̄ для C_d,max (Viterna: не больше 50)
 */
public record Airfoil(double liftSlopePerRad, double zeroLiftRad, double stallRad, double negativeStallRad, double cd0,
        double dragFactor, double clMinDrag, double aspectRatio) {

    public Airfoil {
        if (!(liftSlopePerRad > 0)) throw new IllegalArgumentException("liftSlopePerRad");
        if (!(stallRad > zeroLiftRad && stallRad < Math.PI / 2)) throw new IllegalArgumentException("stallRad");
        if (!(negativeStallRad < 0 && negativeStallRad > -Math.PI / 2 && negativeStallRad < zeroLiftRad))
            throw new IllegalArgumentException("negativeStallRad");
        if (!(cd0 >= 0 && dragFactor >= 0)) throw new IllegalArgumentException("drag");
        if (!(aspectRatio > 0)) throw new IllegalArgumentException("aspectRatio");
    }

    /**
     * Толстый выпуклый профиль ветроколеса 1930-х (класс NACA 4415 / Gö 623) с шероховатой поверхностью:
     * a₀ = 0,1 на градус, α₀ = −4°, срыв на 10° при C_l,max = 1,4, отрицательный срыв на −10°, C_d0 = 0,012.
     * Порядок величин — Abbott &amp; von Doenhoff, «Theory of Wing Sections», 1959, прил. IV (NACA 4415 со
     * стандартной шероховатостью: C_l,max ≈ 1,3–1,4, C_d,min ≈ 0,010–0,012 при Re ≈ 3·10⁶).
     */
    public static Airfoil roughCambered(double aspectRatio) {
        return new Airfoil(Math.toDegrees(0.1), Math.toRadians(-4), Math.toRadians(10), Math.toRadians(-10), 0.012, 0.01,
                0.4, aspectRatio);
    }

    /** Идеальный профиль для проверки теории: подъёмная сила без срыва и без сопротивления. */
    public static Airfoil ideal() {
        return new Airfoil(2 * Math.PI, 0, Math.toRadians(80), Math.toRadians(-80), 0, 0, 0, 50);
    }

    /** C_d,max = 1,11 + 0,018·AR (Viterna–Corrigan), AR не больше 50. */
    public double cdMax() {
        return 1.11 + 0.018 * Math.min(aspectRatio, 50);
    }

    /** {C_l, C_d} при угле атаки α (рад, любой); {@code out} длиной 2. */
    public void coefficients(double alpha, double[] out) {
        double a = Math.IEEEremainder(alpha, 2 * Math.PI); // −π…π
        if (a >= negativeStallRad && a <= stallRad) {
            double cl = liftSlopePerRad * (a - zeroLiftRad);
            out[0] = cl;
            out[1] = attachedDrag(cl);
            return;
        }
        double cdMax = cdMax();
        double abs = Math.abs(a);
        if (abs >= Math.PI / 2) { // за 90°: плоская пластина, обтекание с задней кромки
            out[0] = cdMax * Math.sin(a) * Math.cos(a);
            out[1] = cdMax * Math.sin(a) * Math.sin(a);
            return;
        }
        // Viterna–Corrigan от точки срыва своего знака; для отрицательных углов — зеркально
        double as = a > 0 ? stallRad : -negativeStallRad;
        double cls = Math.abs(liftSlopePerRad * ((a > 0 ? stallRad : negativeStallRad) - zeroLiftRad));
        double cds = attachedDrag(liftSlopePerRad * ((a > 0 ? stallRad : negativeStallRad) - zeroLiftRad));
        double ss = Math.sin(as), cs = Math.cos(as);
        double b1 = cdMax, b2 = (cds - cdMax * ss * ss) / cs;
        double a1 = b1 / 2, a2 = (cls - cdMax * ss * cs) * ss / (cs * cs);
        double sa = Math.sin(abs), ca = Math.cos(abs);
        double cl = a1 * Math.sin(2 * abs) + a2 * ca * ca / sa;
        out[0] = a > 0 ? cl : -cl;
        out[1] = b1 * sa * sa + b2 * ca;
    }

    private double attachedDrag(double cl) {
        double d = cl - clMinDrag;
        return cd0 + dragFactor * d * d;
    }
}
