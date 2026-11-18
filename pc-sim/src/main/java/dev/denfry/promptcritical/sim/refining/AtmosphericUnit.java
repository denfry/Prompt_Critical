package dev.denfry.promptcritical.sim.refining;

/**
 * Атмосферная трубчатка (АТ): трубчатая печь нагревает нефть до температуры на выходе, в зоне питания колонны
 * испаряется часть сырья, ректификационная колонна делит пары на бензин (н.к.–180 °C, верх), керосин (180–240 °C) и
 * дизельное топливо (240–350 °C) боковыми отборами, остаток — мазут (&gt; 350 °C, низ) (Ахметов С.А., «Технология
 * глубокой переработки нефти и газа», гл. 6; Watkins, «Petroleum Refinery Distillation»; Gary, Handwerk, «Petroleum
 * Refining», гл. 4).
 *
 * <p><b>Испарение в зоне питания.</b> Доля отгона по ИТК: узкая фракция испаряется, если её температура кипения ниже
 * эквивалентной температуры однократного испарения — температура на выходе печи плюс {@link #STRIPPING_GAIN_K}
 * (водяной пар и перегрев снижают парциальное давление углеводородов) [допущение: кривая ОИ не строится,
 * переход сглажен на ±{@link #FLASH_WIDTH_K}]. Не испарившееся уходит в мазут.
 *
 * <p><b>Ректификация.</b> Три секции между отборами по {@code stagesPerSection} теоретических тарелок (КПД тарелок не
 * входит: это уже теоретические ступени), флегмовое число на всех секциях одно. Чёткость каждой секции —
 * {@link Fractionation#effectiveStages}, распределение узких фракций — по Фенске. Наложение фракций — разность
 * температур 95 % отгона лёгкого продукта и 5 % тяжёлого по ИТК (положительная — «перекрытие», отрицательная —
 * «зазор», Watkins, гл. 2).
 *
 * <p><b>Тепло.</b> Печь: Q = c_p(T_вых − T_вх) + e·λ на кг нефти, плюс испарение флегмы R·D·λ, c_p = 2,5 кДж/(кг·К),
 * λ = 250 кДж/кг (средние для нефти и фракций, Ахметов, гл. 6). Сырьё — из резервуара без рекуперации тепла.
 *
 * @param stagesPerSection теоретических тарелок между отборами
 * @param reflux           флегмовое число
 * @param furnaceOutletK   температура на выходе печи, К
 */
public record AtmosphericUnit(double stagesPerSection, double reflux, double furnaceOutletK) {
    public enum Product {
        GASOLINE(180, 735), KEROSENE(240, 790), DIESEL(350, 840), RESIDUE(Double.NaN, 950);

        /** Конец кипения по ИТК (точка раздела с соседом снизу), °C. */
        public final double cutC;
        public final double densityKgM3;

        Product(double cutC, double densityKgM3) {
            this.cutC = cutC;
            this.densityKgM3 = densityKgM3;
        }
    }

    public static final double STRIPPING_GAIN_K = 20, FLASH_WIDTH_K = 8;
    public static final double CP_J_KG_K = 2_500, LATENT_J_KG = 250e3, FEED_K = 293.15;

    /**
     * @param yields       выходы продуктов по массе (сумма 1)
     * @param overlapK     наложение соседних продуктов: бензин/керосин, керосин/дизель, дизель/мазут, К
     * @param vaporized    доля отгона в зоне питания
     * @param dutyJPerKg   тепло печи на кг нефти
     */
    public record Result(double[] yields, double[] overlapK, double vaporized, double dutyJPerKg) {
        public double yield(Product p) {
            return yields[p.ordinal()];
        }
    }

    public Result distill(CrudeAssay crude) {
        double[] tb = crude.sliceTemperaturesK(), w = crude.sliceFractions();
        int n = tb.length, m = Product.values().length;
        double[][] split = new double[m][n];
        double flash = furnaceOutletK + STRIPPING_GAIN_K, vaporized = 0;
        double[] neff = new double[m - 1];
        for (int k = 0; k < m - 1; k++)
            neff[k] = Fractionation.effectiveStages(stagesPerSection, reflux, Product.values()[k].cutC + 273.15);
        for (int i = 0; i < n; i++) {
            double v = 1 / (1 + Math.exp((tb[i] - flash) / FLASH_WIDTH_K));
            vaporized += v * w[i];
            double rest = v;
            for (int k = 0; k < m - 1; k++) {
                double f = Fractionation.toLight(tb[i], Product.values()[k].cutC + 273.15, neff[k]);
                split[k][i] = rest * f;
                rest *= 1 - f;
            }
            split[m - 1][i] = rest + (1 - v);
        }
        double[] yields = new double[m];
        for (int k = 0; k < m; k++) for (int i = 0; i < n; i++) yields[k] += split[k][i] * w[i];
        double[] overlap = new double[m - 1];
        for (int k = 0; k < m - 1; k++) overlap[k] = percentile(tb, w, split[k], 0.95) - percentile(tb, w, split[k + 1], 0.05);
        double distillate = yields[Product.GASOLINE.ordinal()];
        double duty = CP_J_KG_K * (furnaceOutletK - FEED_K) + vaporized * LATENT_J_KG + reflux * distillate * LATENT_J_KG;
        return new Result(yields, overlap, vaporized, duty);
    }

    /** Температура, до которой выкипает доля {@code q} продукта (по его узким фракциям), К. */
    private static double percentile(double[] tb, double[] w, double[] split, double q) {
        double total = 0;
        for (int i = 0; i < tb.length; i++) total += w[i] * split[i];
        if (total <= 0) return Double.NaN;
        double acc = 0;
        for (int i = 0; i < tb.length; i++) {
            double d = w[i] * split[i] / total;
            if (acc + d >= q) {
                double half = CrudeAssay.SLICE_K / 2;
                return tb[i] - half + 2 * half * (q - acc) / Math.max(d, 1e-12);
            }
            acc += d;
        }
        return tb[tb.length - 1];
    }
}
