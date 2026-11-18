package dev.denfry.promptcritical.sim.electronics;

/**
 * Планарный процесс (11 §11.5.3, 15 §15.6): окисление, литография, диффузия.
 *
 * <p><b>Дил–Гроув.</b> x² + A·x = B·(t + τ), x = (A/2)·(√(1 + 4B(t + τ)/A²) − 1). Константы — Аррениус по Plummer,
 * Deal, Griffin, «Silicon VLSI Technology» (2000), табл. 6-2, для (111): сухой O₂ — B = 7,72·10² мкм²/ч·e^{−1,23 эВ/kT},
 * B/A = 6,23·10⁶ мкм/ч·e^{−2,0 эВ/kT}; влажный (H₂O, 640 торр) — B = 3,86·10² мкм²/ч·e^{−0,78 эВ/kT},
 * B/A = 1,63·10⁸ мкм/ч·e^{−2,05 эВ/kT}; для (100) B/A меньше в 1,68 раза. Начальный слой сухого окисления — 25 нм
 * (τ из него), влажного — 0.
 *
 * <p><b>Литография.</b> Контактная и бесконтактная печать: тень маски размывается дифракцией Френеля,
 * CD ≈ 1,5·√(λ·(g + t/2)) (Mack, «Fundamental Principles of Optical Lithography», гл. 1), λ ртутной лампы ≈ 0,4 мкм,
 * резист t = 1 мкм. При «контакте» реальный зазор — микроны из-за неплоскости пластины и пыли, поэтому практический
 * предел контактной печати принят 2 мкм, а маска при контакте изнашивается и несёт дефекты. Проекционный степпер
 * g-line: по Рэлею CD = k₁·λ/NA, λ = 436 нм, NA = 0,35, k₁ = 0,8 → 1,0 мкм; глубина резкости k₂·λ/NA².
 *
 * <p><b>Диффузия.</b> Загонка из постоянного источника (поверхностная концентрация — предел растворимости ≈ 10²⁰ см⁻³):
 * профиль erfc, глубина p-n-перехода x_j = 2√(Dt)·erfc⁻¹(N_B/N_s). D = D₀·e^{−E_a/kT}, собственные значения Fair:
 * фосфор D₀ = 3,85 см²/с, E_a = 3,66 эВ; бор D₀ = 0,76 см²/с, E_a = 3,46 эВ.
 */
public final class PlanarProcess {
    public static final double K_EV = 8.617333262e-5;
    public static final double HG_LAMBDA_UM = 0.4, RESIST_UM = 1.0, CONTACT_FLOOR_UM = 2.0;
    public static final double G_LINE_UM = 0.436, G_LINE_NA = 0.35, K1 = 0.8, K2 = 0.5;
    public static final double DRY_INITIAL_UM = 0.025;
    public static final double SURFACE_CONC = 1e20;

    private PlanarProcess() {
    }

    public enum Ambient { DRY, WET }

    public enum Dopant {
        PHOSPHORUS(3.85, 3.66), BORON(0.76, 3.46);

        public final double d0Cm2S, eaEv;

        Dopant(double d0Cm2S, double eaEv) {
            this.d0Cm2S = d0Cm2S;
            this.eaEv = eaEv;
        }

        /** Коэффициент диффузии при {@code tempC}, см²/с. */
        public double diffusivity(double tempC) {
            return d0Cm2S * Math.exp(-eaEv / (K_EV * (tempC + 273.15)));
        }
    }

    /** Параболическая константа B, мкм²/ч. */
    public static double parabolicB(Ambient a, double tempC) {
        double kt = K_EV * (tempC + 273.15);
        return a == Ambient.DRY ? 7.72e2 * Math.exp(-1.23 / kt) : 3.86e2 * Math.exp(-0.78 / kt);
    }

    /** Линейная константа B/A, мкм/ч; {@code orientation100} — для пластин (100). */
    public static double linearBA(Ambient a, double tempC, boolean orientation100) {
        double kt = K_EV * (tempC + 273.15);
        double v = a == Ambient.DRY ? 6.23e6 * Math.exp(-2.0 / kt) : 1.63e8 * Math.exp(-2.05 / kt);
        return orientation100 ? v / 1.68 : v;
    }

    /** Толщина окисла после {@code hours} при {@code tempC}, мкм (с начальным слоем сухого окисления). */
    public static double oxideUm(Ambient a, double tempC, double hours, boolean orientation100) {
        double b = parabolicB(a, tempC), ba = linearBA(a, tempC, orientation100), aa = b / ba;
        double xi = a == Ambient.DRY ? DRY_INITIAL_UM : 0;
        double tau = (xi * xi + aa * xi) / b;
        double t = Math.max(0, hours) + tau;
        return aa / 2 * (Math.sqrt(1 + 4 * b * t / (aa * aa)) - 1);
    }

    /** Размер элемента контактной или бесконтактной печати с зазором {@code gapUm}, мкм. */
    public static double proximityCdUm(double gapUm) {
        return Math.max(CONTACT_FLOOR_UM, 1.5 * Math.sqrt(HG_LAMBDA_UM * (Math.max(0, gapUm) + RESIST_UM / 2)));
    }

    /** Рэлеевский предел проекционной печати, мкм. */
    public static double rayleighCdUm(double lambdaUm, double na, double k1) {
        return k1 * lambdaUm / na;
    }

    /** Глубина резкости, мкм. */
    public static double depthOfFocusUm(double lambdaUm, double na, double k2) {
        return k2 * lambdaUm / (na * na);
    }

    public static double gLineCdUm() {
        return rayleighCdUm(G_LINE_UM, G_LINE_NA, K1);
    }

    /** Глубина p-n-перехода после загонки, мкм. */
    public static double junctionDepthUm(Dopant d, double tempC, double hours, double backgroundCm3) {
        double dt = d.diffusivity(tempC) * Math.max(0, hours) * 3600;
        if (dt <= 0 || backgroundCm3 >= SURFACE_CONC) return 0;
        return 2 * Math.sqrt(dt) * erfcInv(backgroundCm3 / SURFACE_CONC) * 1e4;
    }

    /** erfc(x), Абрамовиц–Стиган 7.1.26 (погрешность 1,5·10⁻⁷), для x ≥ 0. */
    public static double erfc(double x) {
        if (x < 0) return 2 - erfc(-x);
        double t = 1 / (1 + 0.3275911 * x);
        double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
        return poly * Math.exp(-x * x);
    }

    /** Обратная erfc на (0; 1]: бисекция по монотонной erfc. */
    public static double erfcInv(double y) {
        if (y >= 1) return 0;
        double lo = 0, hi = 10;
        for (int i = 0; i < 100; i++) {
            double m = 0.5 * (lo + hi);
            if (erfc(m) > y) lo = m;
            else hi = m;
        }
        return 0.5 * (lo + hi);
    }
}
