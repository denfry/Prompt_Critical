package dev.denfry.promptcritical.sim.wind;

/**
 * Теория элемента лопасти и количества движения (BEM) для установившегося осевого потока.
 *
 * <p>На каждом кольце уравнения количества движения и элемента лопасти сведены к одному уравнению по углу
 * притекания φ и решаются методом Брента на заведомо найденной скобке — схема Ning («A simple solution method
 * for the blade element momentum equations with guaranteed convergence», Wind Energy 17, 2014, 1327–1345):
 * R(φ) = sin φ/(1 − a) − cos φ·(1 − κ′)/λ_r, κ = σ′C_n/(4F sin²φ), κ′ = σ′C_t/(4F sin φ cos φ),
 * a = κ/(1 + κ), a′ = κ′/(1 − κ′). При a &gt; 0,4 (κ &gt; 2/3) — эмпирическая поправка Глауэрта в форме Buhl
 * (NREL/TP-500-36834, 2005), непрерывная с теорией количества движения; при φ &lt; 0 — режим «пропеллерного
 * тормоза». Концевые и корневые потери — множитель Прандтля F = (2/π)·arccos(exp(−B(R − r)/(2r sin φ))).
 *
 * <p>Неподвижное колесо (λ = 0) теория количества движения не описывает: индукции нет, φ = 90°, и пусковой
 * момент — сумма сил сечений при скорости ветра (Manwell et al., 2009, §3.12).
 *
 * <p>Если корень на скобке Ning неприменим (a ≥ 1, a &lt; −0,5 или окружная индуцированная скорость λ_r·a′ больше
 * скорости ветра), сечение считается по краю применимости теории:
 * тяжело нагруженное (турбулентный след, a ≥ 1 — концы лопастей разогнанного колеса при отрицательном угле) — с
 * a = {@link #A_HEAVY}, слабо нагруженное (пропеллерный тормоз у сильно повёрнутой лопасти на малой λ) — без
 * индукции. У корня оптимальной лопасти Глауэрта a′ велико, но λ_r·a′ → (1 − 3a)/(4a − 1)·λ_r ≈ 0,43, и критерий её
 * не задевает. Результат безразмерный:
 * C_P = P/(½ρAV³), C_T = T/(½ρAV²), C_Q = Q/(½ρAV²R).
 */
public final class BladeElementMomentum {
    private static final double EPS = 1e-6;
    /** Нижняя граница осевой индукции, при которой решение количества движения ещё принимается. */
    private static final double A_MIN = -0.5;
    /**
     * Осевая индукция сечения в глубоком турбулентном следе, где поправка Buhl даёт a ≥ 1: эмпирическая кривая
     * C_T(a) построена по данным до a ≈ 0,95 (Buhl, 2005, рис. 3) — на этой границе индукцию и держим [моё].
     */
    private static final double A_HEAVY = 0.95;
    /** Невязка, при которой точка Брента считается корнем. */
    private static final double ROOT_TOLERANCE = 1e-6;

    private BladeElementMomentum() {
    }

    /** Безразмерные характеристики колеса. */
    public record Result(double cp, double ct, double cq) {
    }

    /**
     * @param tsr      быстроходность λ = ΩR/V (≥ 0)
     * @param pitchRad угол установки лопасти β (к флюгеру — положительный), рад
     * @param tipLoss  учитывать потери Прандтля
     */
    public static Result solve(RotorGeometry g, Airfoil foil, double tsr, double pitchRad, boolean tipLoss) {
        if (!(tsr >= 0)) throw new IllegalArgumentException("tsr");
        double[] coef = new double[2];
        double q = 0, t = 0;
        double big = g.radiusM();
        for (int i = 0; i < g.sections(); i++) {
            double r = g.r()[i], c = g.chord()[i], dr = g.dr()[i];
            double theta = g.twistRad()[i] + pitchRad;
            double sigma = g.blades() * c / (2 * Math.PI * r);
            double w2, cn, ctan;
            if (tsr == 0) {
                // неподвижное колесо: поток по оси, W = V, φ = 90°
                foil.coefficients(Math.PI / 2 - theta, coef);
                w2 = 1;
                cn = coef[1]; // C_l·cos 90° + C_d·sin 90°
                ctan = coef[0]; // C_l·sin 90° − C_d·cos 90°
            } else {
                Section s = new Section(g, foil, tipLoss, r, sigma, theta, tsr * r / big, coef);
                if (!s.solve()) {
                    if (s.heavy) s.withFixedInduction(A_HEAVY);
                    else s.withFixedInduction(0);
                }
                double vx = 1 - s.a, vy = tsr * r / big * (1 + s.ap);
                w2 = vx * vx + vy * vy;
                cn = s.cn;
                ctan = s.ct;
            }
            q += w2 * g.blades() * c * ctan * r * dr;
            t += w2 * g.blades() * c * cn * dr;
        }
        double cq = q / (Math.PI * big * big * big);
        double ct = t / (Math.PI * big * big);
        return new Result(tsr * cq, ct, cq);
    }

    /** Одно кольцо: невязка Ning и индукции при найденном φ. */
    private static final class Section {
        final RotorGeometry g;
        final Airfoil foil;
        final boolean tipLoss;
        final double r, sigma, theta, lr;
        final double[] coef;
        double a, ap, cn, ct;
        /** Корень Ning — в турбулентном следе за пределом поправки (a ≥ 1). */
        boolean heavy;

        Section(RotorGeometry g, Airfoil foil, boolean tipLoss, double r, double sigma, double theta, double lr, double[] coef) {
            this.g = g;
            this.foil = foil;
            this.tipLoss = tipLoss;
            this.r = r;
            this.sigma = sigma;
            this.theta = theta;
            this.lr = lr;
            this.coef = coef;
        }

        /** Сечение с заданной осевой индукцией и без окружной: φ = arctg((1 − a)/λ_r). */
        void withFixedInduction(double axial) {
            double phi = Math.atan2(1 - axial, lr), sp = Math.sin(phi), cp = Math.cos(phi);
            foil.coefficients(phi - theta, coef);
            cn = coef[0] * cp + coef[1] * sp;
            ct = coef[0] * sp - coef[1] * cp;
            a = axial;
            ap = 0;
        }

        /**
         * Корень невязки на скобке Ning (рабочий режим, затем пропеллерный тормоз, затем φ &gt; 90°); {@code false} —
         * его индукции неприменимы, {@link #heavy} — потому что сечение в турбулентном следе. Состояние сечения
         * остаётся при найденном корне.
         */
        boolean solve() {
            double lo = EPS, hi = Math.PI / 2;
            if (residual(lo) * residual(hi) > 0) {
                if (residual(-Math.PI / 4) * residual(-EPS) < 0) {
                    lo = -Math.PI / 4;
                    hi = -EPS;
                } else {
                    lo = Math.PI / 2;
                    hi = Math.PI - EPS;
                }
            }
            double phi = brent(lo, hi);
            if (rootIsPhysical(phi)) return true;
            heavy = !Double.isNaN(phi) && a > 0.5; // полюс a → 1 или корень за пределом поправки
            return false;
        }

        /**
         * Найденная точка — корень (а не полюс невязки при a → 1, где она меняет знак через бесконечность) и
         * индукции при нём применимы; NaN — скобка без перемены знака.
         */
        private boolean rootIsPhysical(double phi) {
            if (Double.isNaN(phi)) return false;
            double r = residual(phi);
            return Math.abs(r) < ROOT_TOLERANCE && a > A_MIN && a < 1 && lr * Math.abs(ap) < 1;
        }

        double residual(double phi) {
            double sp = Math.sin(phi), cp = Math.cos(phi);
            foil.coefficients(phi - theta, coef);
            cn = coef[0] * cp + coef[1] * sp;
            ct = coef[0] * sp - coef[1] * cp;
            double f = 1;
            if (tipLoss) {
                double asp = Math.abs(sp);
                double ft = g.blades() / 2.0 * (g.radiusM() - r) / (r * asp);
                f = 2 / Math.PI * Math.acos(Math.exp(-ft));
                if (g.hubRadiusM() > 0) {
                    double fh = g.blades() / 2.0 * (r - g.hubRadiusM()) / (g.hubRadiusM() * asp);
                    f *= 2 / Math.PI * Math.acos(Math.exp(-fh));
                }
                f = Math.max(f, 1e-6);
            }
            double k = sigma * cn / (4 * f * sp * sp);
            double kp = sigma * ct / (4 * f * sp * cp);
            ap = kp / (1 - kp);
            if (phi > 0) {
                if (k <= 2.0 / 3) {
                    a = k / (1 + k);
                } else { // Buhl
                    double g1 = 2 * f * k - (10.0 / 9 - f);
                    double g2 = 2 * f * k - f * (4.0 / 3 - f);
                    double g3 = 2 * f * k - (25.0 / 9 - 2 * f);
                    a = Math.abs(g3) < 1e-6 ? 1 - 1 / (2 * Math.sqrt(g2)) : (g1 - Math.sqrt(g2)) / g3;
                }
                return sp / (1 - a) - cp / lr * (1 - kp);
            }
            a = k > 1 ? k / (k - 1) : 0; // пропеллерный тормоз
            return sp * (1 - k) - cp / lr * (1 - kp);
        }

        /** Метод Брента на скобке [x0, x1]; NaN — на её концах невязка одного знака. */
        private double brent(double x0, double x1) {
            double fa = residual(x0), fb = residual(x1);
            double a0 = x0, b = x1;
            if (fa * fb > 0) return Double.NaN; // в скобке нет корня
            if (Math.abs(fa) < Math.abs(fb)) {
                double tmp = a0; a0 = b; b = tmp;
                tmp = fa; fa = fb; fb = tmp;
            }
            double c = a0, fc = fa, d = b - a0;
            boolean bisected = true;
            for (int it = 0; it < 100; it++) {
                if (fb == 0 || Math.abs(b - a0) < 1e-10) return b;
                double s;
                if (fa != fc && fb != fc) {
                    s = a0 * fb * fc / ((fa - fb) * (fa - fc)) + b * fa * fc / ((fb - fa) * (fb - fc))
                            + c * fa * fb / ((fc - fa) * (fc - fb));
                } else {
                    s = b - fb * (b - a0) / (fb - fa);
                }
                double m = (3 * a0 + b) / 4;
                boolean bad = !((s > Math.min(m, b) && s < Math.max(m, b)))
                        || bisected && Math.abs(s - b) >= Math.abs(b - c) / 2
                        || !bisected && Math.abs(s - b) >= Math.abs(c - d) / 2;
                if (bad) {
                    s = (a0 + b) / 2;
                    bisected = true;
                } else {
                    bisected = false;
                }
                double fs = residual(s);
                d = c;
                c = b;
                fc = fb;
                if (fa * fs < 0) {
                    b = s;
                    fb = fs;
                } else {
                    a0 = s;
                    fa = fs;
                }
                if (Math.abs(fa) < Math.abs(fb)) {
                    double tmp = a0; a0 = b; b = tmp;
                    tmp = fa; fa = fb; fb = tmp;
                }
            }
            return b;
        }
    }
}
