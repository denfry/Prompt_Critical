package dev.denfry.promptcritical.sim.isotopes;

/**
 * Точное решение цепочки облучения из 2–3 звеньев за шаг при постоянном потоке (12 §12.1.3): звено i теряет ядра со
 * скоростью {@code loss[i]} (распад плюс выгорание), звено i получает их из i − 1 со скоростью {@code feed[i]}·N_{i−1}
 * (захват на мишени или β-распад промежуточного звена). Матрица нижняя двухдиагональная, её экспонента — замкнутая
 * формула Бейтмана через разделённые разности функции e^{−xt}:
 *
 * <pre>
 * N₀' = N₀ e^{−l₀t}
 * N₁' = N₁ e^{−l₁t} + f₁N₀·φ(l₀,l₁)
 * N₂' = N₂ e^{−l₂t} + f₂N₁·φ(l₁,l₂) + f₁f₂N₀·ψ(l₀,l₁,l₂)
 * </pre>
 *
 * φ — первая разделённая разность (e^{−at} − e^{−bt})/(b − a), ψ — вторая. Обе считаются без вычитания близких чисел:
 * φ через expm1, ψ — рядом Тейлора при малом разбросе скоростей. Поэтому нет ни NaN при a = b (T12.3), ни переполнения
 * при λΔt ≫ 1 (T12.4): продукт выходит на равновесие aN/b. Без аллокаций.
 */
public final class BatemanStepper {
    private BatemanStepper() {
    }

    /** e^{−xt}. */
    private static double g(double x, double t) {
        return Math.exp(-x * t);
    }

    /** Первая разделённая разность (e^{−at} − e^{−bt})/(b − a) ≥ 0; предел при a = b — t·e^{−at}. */
    public static double phi(double a, double b, double t) {
        double lo = Math.min(a, b), hi = Math.max(a, b), h = (hi - lo) * t;
        if (h < 1e-12) return t * g(0.5 * (lo + hi), t);
        return g(lo, t) * t * (-Math.expm1(-h)) / h;
    }

    /** Вторая разделённая разность e^{−xt} по трём узлам (≥ 0); при малом разбросе — t²e^{−x̄t}/2. */
    public static double psi(double a, double b, double c, double t) {
        double x1 = Math.min(a, Math.min(b, c)), x3 = Math.max(a, Math.max(b, c)), x2 = a + b + c - x1 - x3;
        double spread = (x3 - x1) * t;
        if (spread < 1e-4) {
            double m = (x1 + x2 + x3) / 3;
            return 0.5 * t * t * g(m, t);
        }
        return (phi(x1, x2, t) - phi(x2, x3, t)) / (x3 - x1);
    }

    /**
     * Шаг цепочки длиной {@code in.length} (1…3).
     *
     * @param loss скорости убыли звеньев, 1/с
     * @param feed скорости перехода из предыдущего звена, 1/с ({@code feed[0]} не используется)
     * @param in   ядра на начало шага
     * @param dtS  шаг, с
     * @param out  ядра на конец шага (может совпадать с {@code in})
     */
    public static void step(double[] loss, double[] feed, double[] in, double dtS, double[] out) {
        int n = in.length;
        if (n < 1 || n > 3) throw new IllegalArgumentException("chain of 1..3 links");
        double n0 = in[0], n1 = n > 1 ? in[1] : 0, n2 = n > 2 ? in[2] : 0;
        if (!(dtS > 0)) {
            if (out != in) System.arraycopy(in, 0, out, 0, n);
            return;
        }
        out[0] = n0 * g(loss[0], dtS);
        if (n > 1) out[1] = n1 * g(loss[1], dtS) + feed[1] * n0 * phi(loss[0], loss[1], dtS);
        if (n > 2) out[2] = n2 * g(loss[2], dtS) + feed[2] * n1 * phi(loss[1], loss[2], dtS)
                + feed[1] * feed[2] * n0 * psi(loss[0], loss[1], loss[2], dtS);
    }
}
