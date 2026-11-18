package dev.denfry.promptcritical.sim.refining;

/**
 * Вакуумная колонна (ВТ, GDD 09 §9.9.2): мазут — остаток атмосферной колонны — греют в печи до 390–410 °C и подают в
 * колонну с давлением в зоне питания 3–8 кПа; вакуум создают пароэжекторы. Продукты — вакуумный газойль
 * (350–550 °C, сырьё каткрекинга и масел) сверху и гудрон (&gt; 550 °C, коксование, битум) снизу (Ахметов С.А.,
 * «Технология глубокой переработки нефти и газа», гл. 6; Gary, Handwerk, «Petroleum Refining», гл. 4).
 *
 * <p><b>Испарение под вакуумом.</b> Фракция с нормальной температурой кипения T_b при давлении p кипит при T, где по
 * Клаузиусу–Клапейрону с правилом Трутона (ΔS_исп/R = {@link Fractionation#TROUTON}):
 * <pre>ln(p/p₀) = (ΔS/R)·(1 − T_b/T)  ⇒  T_b,экв = T·(1 − ln(p/p₀)/(ΔS/R)),</pre>
 * то есть при 400 °C и 5 кПа выкипает всё, что кипит при атмосферном давлении до ≈ 590 °C. К эквивалентной температуре,
 * как и в {@link AtmosphericUnit}, прибавляется {@link AtmosphericUnit#STRIPPING_GAIN_K} — водяной пар снижает
 * парциальное давление углеводородов; переход сглажен на ±{@link AtmosphericUnit#FLASH_WIDTH_K}.
 *
 * <p><b>Разделение.</b> Пары делятся на газойль и тяжёлый остаток промывной секции по точке раздела
 * {@value #CUT_C} °C ({@link Fractionation#effectiveStages}: насадка, мало ступеней); не испарившееся уходит в
 * гудрон. Лёгкий «хвост» мазута (дизельные фракции, оставшиеся в кубе атмосферной колонны) уходит с газойлем
 * [допущение: отдельный отбор вакуумного дизеля не строится].
 *
 * <p><b>Коксуемость.</b> Смолы и асфальтены не испаряются: коксуемость Конрадсона мазута остаётся в гудроне, у
 * газойля она {@value #VGO_CCR} [BALANCE: у прямогонного вакуумного газойля обычно не больше 0,5 %]:
 * CCR_гудр = (CCR_мазута − y_ВГО·CCR_ВГО)/y_гудр.
 *
 * <p><b>Тепло.</b> Q = c_p·(T_вых − T_вх) + e·λ на кг мазута, c_p и λ — как у {@link AtmosphericUnit}, мазут
 * приходит горячим ({@value #FEED_C} °C).
 *
 * @param stages      теоретических ступеней в секции газойль/гудрон
 * @param reflux      флегмовое (орошение) число секции
 * @param heaterOutletK температура на выходе печи, К
 * @param flashZoneKpa давление в зоне питания, кПа
 */
public record VacuumUnit(double stages, double reflux, double heaterOutletK, double flashZoneKpa) {
    public static final double CUT_C = 550, P0_KPA = 101.325, VGO_CCR = 0.005, FEED_C = 70;
    /** Типичное ВТ: насадка ≈ 3 теоретических ступени, орошение 0,5 [BALANCE]. */
    public static final double STAGES = 3, REFLUX = 0.5;

    /**
     * @param vgo       выход газойля на мазут, доля по массе
     * @param residue   выход гудрона на мазут
     * @param vaporized доля отгона в зоне питания
     * @param flashTbK  нормальная температура кипения, до которой выкипает мазут в зоне питания, К
     * @param dutyJPerKg тепло печи на кг мазута
     */
    public record Result(double vgo, double residue, double vaporized, double flashTbK, double dutyJPerKg) {
        /** Коксуемость гудрона при коксуемости мазута {@code feedCcr}. */
        public double residueCcr(double feedCcr) {
            return residue <= 0 ? feedCcr : Math.max(0, (feedCcr - vgo * VGO_CCR) / residue);
        }
    }

    /** Нормальная температура кипения фракции, которая кипит при {@code tK} под давлением {@code pKpa}. */
    public static double atmosphericEquivalentK(double tK, double pKpa) {
        return tK * (1 - Math.log(pKpa / P0_KPA) / Fractionation.TROUTON);
    }

    /**
     * Перегонка мазута, состав которого — массовые доли узких фракций нефти {@code residueSlices} (на кг нефти, как
     * в {@link AtmosphericUnit.Result#residueSlices}); выходы — на кг мазута.
     */
    public Result distill(CrudeAssay crude, double[] residueSlices) {
        double[] tb = crude.sliceTemperaturesK();
        if (tb.length != residueSlices.length) throw new IllegalArgumentException("slices of another assay");
        double total = 0;
        for (double w : residueSlices) total += w;
        if (total <= 0) return new Result(0, 0, 0, 0, 0);
        double flash = atmosphericEquivalentK(heaterOutletK, flashZoneKpa) + AtmosphericUnit.STRIPPING_GAIN_K;
        double neff = Fractionation.effectiveStages(stages, reflux, CUT_C + 273.15);
        double vgo = 0, vaporized = 0;
        for (int i = 0; i < tb.length; i++) {
            double w = residueSlices[i] / total;
            double v = 1 / (1 + Math.exp((tb[i] - flash) / AtmosphericUnit.FLASH_WIDTH_K));
            vaporized += v * w;
            vgo += v * w * Fractionation.toLight(tb[i], CUT_C + 273.15, neff);
        }
        double duty = AtmosphericUnit.CP_J_KG_K * (heaterOutletK - (FEED_C + 273.15)) + vaporized * AtmosphericUnit.LATENT_J_KG;
        return new Result(vgo, 1 - vgo, vaporized, flash, duty);
    }
}
