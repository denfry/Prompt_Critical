package dev.denfry.promptcritical.sim.mining;

/**
 * Обогащение угля по плотности (08 §8.3), кривая Тромпа:
 * ε(ρ) = [1 + exp((ρ − ρ_p)/(0,91·E_p))]⁻¹ — доля фракции плотности ρ, ушедшая в лёгкий продукт;
 * γ_k = Σw_iε_i, A_k = Σw_iε_iA_i/γ_k.
 *
 * <p>Два разделения подряд: по ρ_чист — концентрат ({@code washed_coal}), тяжёлое по ρ_пор — промпродукт
 * ({@code coal_middlings}) и порода ({@code coal_refuse}). Плотности фракций 5/15/35/75 % золы — смесь органики
 * 1,30 и минеральной части 2,6 г/см³ по объёму [допущение; дают 1,33 / 1,41 / 1,58 / 2,08, порода &gt; 1,8 как
 * в §8.3]. Пиритная сера идёт с минеральной массой фракций (∝ w_iA_i), органическая — с органикой (∝ w_i(1 − A_i)):
 * пирит (5,0 г/см³) уходит в тяжёлое, органическая сера остаётся.
 */
public final class DensitySeparation {
    /** Плотности фракций {@link CoalAssay#FRACTION_ASH}, г/см³. */
    public static final double[] FRACTION_DENSITY = new double[CoalAssay.FRACTION_ASH.length];
    /** Предел зольности продукта (порода). */
    static final double MAX_ASH = 0.79;
    /** Ниже этого выхода продукт не образуется. */
    public static final double MIN_YIELD = 1e-6;

    static {
        for (int i = 0; i < FRACTION_DENSITY.length; i++) {
            double a = CoalAssay.FRACTION_ASH[i];
            FRACTION_DENSITY[i] = 1 / ((1 - a) / 1.30 + a / 2.6);
        }
    }

    /** Аппарат и его погрешность разделения E_p. */
    public enum Separator {
        /** {@code coal_jig} — отсадочная машина, I0. */
        JIG(0.10),
        /** {@code dense_medium_separator} — тяжелосредный сепаратор, I1. */
        DENSE_MEDIUM(0.03);

        public final double ep;

        Separator(double ep) {
            this.ep = ep;
        }
    }

    /**
     * Продукты обогащения; выходы — доли сухой массы питания, продукт с нулевым выходом — {@code null}.
     */
    public record Result(double cleanYield, CoalAssay clean, double middlingsYield, CoalAssay middlings,
            double refuseYield, CoalAssay refuse) {}

    private DensitySeparation() {}

    /** ε(ρ) — доля в лёгкий продукт. */
    public static double partition(double rho, double rhoP, double ep) {
        return 1 / (1 + Math.exp((rho - rhoP) / (0.91 * ep)));
    }

    /**
     * Разделение питания на три продукта.
     *
     * @param feed       питание
     * @param rhoClean   плотность разделения концентрата, г/см³
     * @param rhoRefuse  плотность отделения породы, г/см³ (≥ ρ_чист); равная ρ_чист — двухпродуктовая схема без
     *                   промпродукта
     * @param separator  аппарат
     */
    public static Result wash(CoalAssay feed, double rhoClean, double rhoRefuse, Separator separator) {
        if (!(rhoRefuse >= rhoClean)) throw new IllegalArgumentException("rhoRefuse < rhoClean");
        int n = FRACTION_DENSITY.length;
        float[] w = feed.densityFractions();
        double sumW = 0, ashFeed = 0;
        for (int i = 0; i < n; i++) {
            sumW += w[i];
            ashFeed += w[i] * CoalAssay.FRACTION_ASH[i];
        }
        double sPyr = feed.sulfurDryFrac() * feed.pyriticShare(), sOrg = feed.sulfurDryFrac() - sPyr;
        boolean twoProduct = rhoRefuse == rhoClean;
        double[] toClean = new double[n], toMid = new double[n], toRef = new double[n];
        for (int i = 0; i < n; i++) {
            double wi = w[i] / sumW;
            double e1 = partition(FRACTION_DENSITY[i], rhoClean, separator.ep);
            double e2 = twoProduct ? 0 : partition(FRACTION_DENSITY[i], rhoRefuse, separator.ep);
            toClean[i] = wi * e1;
            toMid[i] = wi * (1 - e1) * e2;
            toRef[i] = wi * (1 - e1) * (1 - e2);
        }
        double orgFeed = 1 - ashFeed / sumW, minFeed = ashFeed / sumW;
        // профиль фракций — приближение; зола продуктов масштабируется к фактической A^d питания
        double k = minFeed > 0 ? feed.ashDryFrac() / minFeed : 0;
        return new Result(total(toClean), product(feed, toClean, sOrg, sPyr, orgFeed, minFeed, k),
                total(toMid), product(feed, toMid, sOrg, sPyr, orgFeed, minFeed, k),
                total(toRef), product(feed, toRef, sOrg, sPyr, orgFeed, minFeed, k));
    }

    private static double total(double[] m) {
        double y = 0;
        for (double v : m) y += v;
        return y;
    }

    private static CoalAssay product(CoalAssay feed, double[] m, double sOrg, double sPyr, double orgFeed,
            double minFeed, double k) {
        double gamma = total(m);
        if (gamma < MIN_YIELD) return null;
        double ash = 0, org = 0;
        float[] frac = new float[m.length];
        for (int i = 0; i < m.length; i++) {
            double a = CoalAssay.FRACTION_ASH[i];
            ash += m[i] * a;
            org += m[i] * (1 - a);
            frac[i] = (float) (m[i] / gamma);
        }
        double sulfurOrg = orgFeed > 0 ? sOrg * org / orgFeed : 0;
        double sulfurPyr = minFeed > 0 ? sPyr * ash / minFeed : 0;
        double sulfur = (sulfurOrg + sulfurPyr) / gamma;
        double share = sulfur > 0 ? sulfurPyr / gamma / sulfur : 0;
        return feed.withMineralMatter(Math.min(MAX_ASH, k * ash / gamma), sulfur, share, frac);
    }
}
