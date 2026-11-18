package dev.denfry.promptcritical.sim.enrichment;

/**
 * Разделительная работа (Benedict, Pigford, Levi, «Nuclear Chemical Engineering», 2nd ed., 1981, гл. 12): функция
 * ценности V(x) = (2x − 1)·ln(x/(1 − x)), материальный баланс F = P + W, F·x_f = P·x_p + W·x_w и работа на килограмм
 * продукта U/P = V(x_p) + (W/P)·V(x_w) − (F/P)·V(x_f) — в ЕРР (кг ЕРР) на кг урана. Оптимальный отвал минимизирует
 * стоимость продукта (F/P)·c_F + (U/P)·c_U при ценах природного урана c_F и ЕРР c_U.
 */
public final class Separation {
    private Separation() {
    }

    /** Функция ценности. */
    public static double value(double x) {
        return (2 * x - 1) * Math.log(x / (1 - x));
    }

    /** Отношение изотопов x/(1 − x). */
    public static double ratio(double x) {
        return x / (1 - x);
    }

    public static double fromRatio(double r) {
        return r / (1 + r);
    }

    private static void check(double xp, double xf, double xw) {
        if (!(xw > 0 && xw < xf && xf < xp && xp < 1)) throw new IllegalArgumentException("need 0 < xw < xf < xp < 1: " + xw + ", " + xf + ", " + xp);
    }

    /** Питание на кг продукта. */
    public static double feedPerProduct(double xp, double xf, double xw) {
        check(xp, xf, xw);
        return (xp - xw) / (xf - xw);
    }

    /** Отвал на кг продукта. */
    public static double tailsPerProduct(double xp, double xf, double xw) {
        return feedPerProduct(xp, xf, xw) - 1;
    }

    /** ЕРР на кг продукта. */
    public static double swuPerProduct(double xp, double xf, double xw) {
        double f = feedPerProduct(xp, xf, xw);
        return value(xp) + (f - 1) * value(xw) - f * value(xf);
    }

    /** Стоимость кг продукта при цене кг природного урана {@code feedCost} и ЕРР {@code swuCost}. */
    public static double productCost(double xp, double xf, double xw, double feedCost, double swuCost) {
        return feedPerProduct(xp, xf, xw) * feedCost + swuPerProduct(xp, xf, xw) * swuCost;
    }

    /** Оптимальный отвал (золотое сечение по x_w в (10⁻⁴, x_f)). */
    public static double optimalTails(double xp, double xf, double feedCost, double swuCost) {
        double lo = 1e-4, hi = xf * 0.999, g = (Math.sqrt(5) - 1) / 2;
        double a = hi - g * (hi - lo), b = lo + g * (hi - lo);
        double fa = productCost(xp, xf, a, feedCost, swuCost), fb = productCost(xp, xf, b, feedCost, swuCost);
        for (int i = 0; i < 100; i++) {
            if (fa < fb) {
                hi = b;
                b = a;
                fb = fa;
                a = hi - g * (hi - lo);
                fa = productCost(xp, xf, a, feedCost, swuCost);
            } else {
                lo = a;
                a = b;
                fa = fb;
                b = lo + g * (hi - lo);
                fb = productCost(xp, xf, b, feedCost, swuCost);
            }
        }
        return 0.5 * (lo + hi);
    }
}
