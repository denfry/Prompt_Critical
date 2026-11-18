package dev.denfry.promptcritical.sim.enrichment;

/**
 * Идеальный (симметричный, без смешения) каскад одинаковых ступеней с коэффициентом разделения α при срезе ½:
 * коэффициент обогащения отбора β = √α, отношение изотопов растёт в β раз на ступень. Ступеней в обогатительной
 * части над ступенью питания n_о = ln(R_p/R_f)/ln β − 1, в исчерпывающей n_и = ln(R_f/R_w)/ln β. Поток питания
 * ступени с составом x — L = P·(β + 1)/(β − 1)·(x_p − x)/(x(1 − x)) в обогатительной части и
 * L = W·(β + 1)/(β − 1)·(x − x_w)/(x(1 − x)) в исчерпывающей (Benedict, Pigford, Levi, 1981, §12.8). Сумма
 * L·(α − 1)²/8 по ступеням равна разделительной работе каскада — это проверяется в тестах.
 */
public final class IdealCascade {
    private final double alpha, beta, xp, xf, xw;
    private final double enriching, stripping;
    /** Потоки питания ступеней на кг/с продукта, снизу (отвал) вверх (отбор); ступень питания — {@link #feedStage}. */
    private final double[] flows;
    private final int feedStage;

    public IdealCascade(double alpha, double xp, double xf, double xw) {
        if (!(alpha > 1)) throw new IllegalArgumentException("separation factor must exceed 1: " + alpha);
        Separation.feedPerProduct(xp, xf, xw);
        this.alpha = alpha;
        this.beta = Math.sqrt(alpha);
        this.xp = xp;
        this.xf = xf;
        this.xw = xw;
        double lb = Math.log(beta);
        this.enriching = Math.max(0, Math.log(Separation.ratio(xp) / Separation.ratio(xf)) / lb - 1);
        this.stripping = Math.log(Separation.ratio(xf) / Separation.ratio(xw)) / lb;
        int ne = (int) Math.ceil(enriching - 1e-9), ns = (int) Math.ceil(stripping - 1e-9);
        this.feedStage = ns;
        this.flows = new double[ns + 1 + ne];
        double wPerP = Separation.tailsPerProduct(xp, xf, xw), k = (beta + 1) / (beta - 1);
        for (int i = 0; i < flows.length; i++) {
            int n = i - ns;
            double x = Separation.fromRatio(Separation.ratio(xf) * Math.pow(beta, n));
            flows[i] = n >= 0 ? k * (xp - x) / (x * (1 - x)) : k * wPerP * (x - xw) / (x * (1 - x));
            flows[i] = Math.max(flows[i], 0);
        }
    }

    public double alpha() {
        return alpha;
    }

    public double enrichingStages() {
        return enriching;
    }

    public double strippingStages() {
        return stripping;
    }

    /** Ступеней всего (с округлением вверх). */
    public int stages() {
        return flows.length;
    }

    public int feedStage() {
        return feedStage;
    }

    /** Поток питания ступени {@code i} на кг/с продукта. */
    public double flowPerProduct(int i) {
        return flows[i];
    }

    /** Суммарный поток питания всех ступеней на кг/с продукта. */
    public double totalFlowPerProduct() {
        double s = 0;
        for (double f : flows) s += f;
        return s;
    }

    /** Разделительная работа каскада на кг продукта по потокам ступеней. */
    public double swuPerProductFromFlows() {
        return totalFlowPerProduct() * (alpha - 1) * (alpha - 1) / 8;
    }

    /** Машин на каждой ступени при выработке {@code productKgS} и питании машины {@code machineFeedKgS}. */
    public int[] machines(double productKgS, double machineFeedKgS) {
        int[] m = new int[flows.length];
        for (int i = 0; i < m.length; i++) m[i] = Math.max(1, (int) Math.ceil(flows[i] * productKgS / machineFeedKgS - 1e-9));
        return m;
    }

    /**
     * Наибольшая выработка, кг/с, которую дают {@code machines} машин: каждой ступени хотя бы одна машина, остальные —
     * по потокам. 0 — машин меньше, чем ступеней.
     */
    public double productFor(int machines, double machineFeedKgS) {
        if (machines < flows.length) return 0;
        double lo = 0, hi = machines * machineFeedKgS / totalFlowPerProduct() * 1.01;
        for (int i = 0; i < 60; i++) {
            double p = 0.5 * (lo + hi);
            int n = 0;
            for (int m : machines(p, machineFeedKgS)) n += m;
            if (n <= machines) lo = p;
            else hi = p;
        }
        return lo;
    }

    /**
     * Доля U-234 в отвале. Разность масс с U-238 у него 4 против 3 у U-235, коэффициент обеднения исчерпывающей части
     * — β^(−4n/3): доля U-234 питания, оставшаяся в отвале, — та же доля для U-235 в степени 4/3. Для 4,5 % из природного
     * урана это даёт ≈ 0,04 % U-234 в продукте, как у реального НОУ.
     */
    public double tailsU234(double feedU234) {
        double f = Separation.feedPerProduct(xp, xf, xw), w = f - 1;
        double left235 = w * xw / (f * xf);
        return feedU234 * f / w * Math.pow(left235, 4.0 / 3);
    }

    /** Доля U-234 в отборе — по балансу. */
    public double productU234(double feedU234) {
        double f = Separation.feedPerProduct(xp, xf, xw);
        return f * feedU234 - (f - 1) * tailsU234(feedU234);
    }
}
