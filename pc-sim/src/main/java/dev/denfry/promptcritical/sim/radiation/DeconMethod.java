package dev.denfry.promptcritical.sim.radiation;

/**
 * Способы дезактивации: коэффициент дезактивации (КД, DF = загрязнение до / после) отдельно для снимаемого и
 * фиксированного загрязнения и вторичные отходы на квадратный метр обработанной поверхности. Значения — середины
 * диапазонов из обзоров OECD/NEA «Decontamination Techniques Used in Decommissioning Activities» (1999) и IAEA TRS-401
 * «Methods for the Minimization of Radioactive Waste from Decontamination and Decommissioning» (2001):
 * <ul>
 *   <li>вода смывает рыхлое (КД 2–10), в оксидную плёнку не проникает;</li>
 *   <li>мыло и тёплая вода на коже — 90–95 % снимаемого за помывку (NCRP Report 161, 2008);</li>
 *   <li>лимонная + щавелевая кислота (комплексоны) растворяют часть оксидов железа: КД 5–50 по рыхлому, 2–5 по фиксированному;</li>
 *   <li>двухстадийная окислительно-восстановительная обработка — щелочной перманганат (KMnO₄ + NaOH, окисляет хром
 *       плёнки до растворимого хромата; сам почти ничего не снимает — {@link #ALKALINE_PERMANGANATE}), затем
 *       щавелевая/лимонная кислота растворяет разрыхлённый оксид (семейство AP-Citrox, CORD): КД 10–100 и по
 *       фиксированному — {@link #PERMANGANATE_OXALIC}, только по поверхности, уже прошедшей окислительную стадию;</li>
 *   <li>снимаемое покрытие (латекс ПВС, 0,3–0,6 мм): 90–99 % рыхлого уходит с плёнкой, жидких отходов нет;</li>
 *   <li>ультразвук усиливает раствор кавитацией у поверхности: для мелких деталей КД выше в 3–10 раз, чем у той же химии без него.</li>
 * </ul>
 * Литры на м² — расход раствора с промывкой: всё, что прошло по поверхности, уходит в жидкие радиоактивные отходы.
 */
public enum DeconMethod {
    WATER_RINSE(3, 1.0, 20, 0),
    SOAP_SHOWER(10, 1.1, 20, 0),
    CITRIC_OXALIC(10, 3, 10, 0),
    /** Окислительная стадия: щелочной перманганат. Готовит оксидную плёнку к кислоте, сам снимает только рыхлое. */
    ALKALINE_PERMANGANATE(2, 1.0, 10, 0),
    /** Кислотная стадия по окисленной поверхности: вместе с окислительной КД 60 по рыхлому и 10 по фиксированному. */
    PERMANGANATE_OXALIC(30, 10, 20, 0),
    STRIPPABLE_COATING(20, 1.5, 0, 0.5),
    ULTRASONIC_WATER(5, 1.5, 0, 0),
    ULTRASONIC_CITRIC_OXALIC(50, 5, 0, 0),
    ULTRASONIC_PERMANGANATE(100, 30, 0, 0);

    /** КД снимаемого и фиксированного загрязнения за проход. */
    public final double dfLoose, dfFixed;
    /** Жидкие отходы, л/м²; у ванны расход на деталь — {@link #BATH_L_PER_ITEM}. */
    public final double liquidLPerM2;
    /** Твёрдые отходы (содранная плёнка), кг/м². */
    public final double solidKgPerM2;

    /** Раствора на одну деталь в ультразвуковой ванне, л: ванна 20–40 л, раствор меняют раз в 10–20 загрузок. */
    public static final double BATH_L_PER_ITEM = 2;

    DeconMethod(double dfLoose, double dfFixed, double liquidLPerM2, double solidKgPerM2) {
        this.dfLoose = dfLoose;
        this.dfFixed = dfFixed;
        this.liquidLPerM2 = liquidLPerM2;
        this.solidKgPerM2 = solidKgPerM2;
    }

    /** Общий КД по суммарной активности для данного загрязнения. */
    public double effectiveDf(SurfaceContamination c) {
        SurfaceContamination after = c.after(this);
        return after.total() <= 0 ? Double.POSITIVE_INFINITY : c.total() / after.total();
    }
}
