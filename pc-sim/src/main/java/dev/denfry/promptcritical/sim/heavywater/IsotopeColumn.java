package dev.denfry.promptcritical.sim.heavywater;

/**
 * Противоточная колонна разделения изотопов водорода: питание F с долей D x_f делится на продукт P (x_p) и отвал W
 * (x_w). Баланс по молям воды и по дейтерию точный: P/F = (x_f − x_w)/(x_p − x_w).
 *
 * <p>Достижимое разделение — по Фенске: при полном орошении N идеальных тарелок дают отношение изотопов продукта к
 * отвалу α^N. При конечном отборе тарелок нужно примерно вдвое больше минимума (эмпирика Гиллиленда при флегмовом
 * числе ≈ 1,3 минимального; Seader, Henley, Roper, «Separation Process Principles», 3rd ed., 2011, §9.2), поэтому
 * рабочее разделение колонны берём α^(N/2). Отвал не может быть беднее {@code xwFloor} — для двухтемпературной
 * схемы это равновесие горячей колонны (см. {@link GirdlerSulfidePlant}).
 */
public final class IsotopeColumn {
    private IsotopeColumn() {
    }

    /**
     * @param productPerFeed моль продукта на моль питания
     * @param wasteX         доля D в отвале
     * @param recovery       доля дейтерия питания, ушедшая в продукт
     */
    public record Split(double productPerFeed, double wasteX, double recovery) {
        public static final Split NONE = new Split(0, Double.NaN, 0);

        public boolean works() {
            return productPerFeed > 0;
        }
    }

    /**
     * Разделение питания {@code xf} на продукт {@code xp} колонной с рабочим разделением {@code separation}
     * (отношение изотопов продукта к отвалу); отвал не беднее {@code xwFloor}.
     */
    public static Split split(double xf, double xp, double separation, double xwFloor) {
        if (!(xf > 0) || !(xp > xf) || !(xp < 1) || !(separation > 1)) return Split.NONE;
        double xw = Math.max(HeavyWater.fromRatio(HeavyWater.ratio(xp) / separation), Math.max(0, xwFloor));
        if (!(xw < xf)) return Split.NONE;
        double p = (xf - xw) / (xp - xw);
        return new Split(p, xw, p * xp / xf);
    }

    /** Рабочее разделение колонны из {@code idealPlates} тарелок с коэффициентом {@code alpha}: α^(N/2). */
    public static double separation(double alpha, double idealPlates) {
        return Math.exp(0.5 * idealPlates * Math.log(alpha));
    }
}
