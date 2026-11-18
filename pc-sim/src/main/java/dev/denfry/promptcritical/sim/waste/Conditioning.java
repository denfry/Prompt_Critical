package dev.denfry.promptcritical.sim.waste;

/**
 * Отверждение кубового остатка и смол в 200-литровой бочке: рецептура на одну бочку, степень включения солей и
 * качество компаунда.
 *
 * <p><b>Цементирование.</b> Вода раствора — вода затворения: цемента кладут на отношение В/Ц = {@value #WC}. Прочность —
 * закон Абрамса (Abrams, 1918): f = A / B^{В/Ц}, A = 96,5 МПа, B = 7 (≈ 40 МПа при 0,45). Соли (бораты, нитраты)
 * тормозят гидратацию: прочность падает по 1 − (x/x₀)², x₀ = 0,25 — выше 15–20 % солей компаунд рыхлый (IAEA TRS-350,
 * 1993, «Improved Cement Solidification of Low and Intermediate Level Radioactive Wastes»; квадратичная форма — наша
 * аппроксимация). Требование ГОСТ Р 51883-2002 — не меньше {@value #MIN_STRENGTH_MPA} МПа на сжатие.
 * Объём — сумма абсолютных объёмов: цемент 3,15 кг/л, соль 2,2 кг/л, вода 1 кг/л; бочку заполняют на
 * {@value #FILL} (усадка, облой). Кубовый остаток 300 г/л даёт ≈ 10 % солей и ≈ 110 л остатка на бочку — порядок
 * цементирования на АЭС с ВВЭР.
 *
 * <p><b>Битумирование.</b> В роторно-плёночном битуматоре при 130–160 °C вода выпаривается целиком, соли
 * диспергируются в битуме до {@value #BITUMEN_LOADING} по массе (IAEA TRS-352, 1993: 40–60 %). Битум — 1,02 кг/л.
 * Объём компаунда на литр остатка втрое меньше цементного — зато битум горит: соли-нитраты с битумом при 200+ °C
 * дают экзотермическую реакцию (пожар на Токай-мура, 1997), поэтому теплонапряжённые и высокоактивные отходы в битум
 * не идут.
 *
 * <p><b>Смолы.</b> Отработанный ионит цементируют с объёмной долей смолы {@value #RESIN_FRACTION} (IAEA TECDOC-689,
 * 1993: 30–40 % — выше набухание смолы рвёт компаунд); вода смолы (≈ 0,5 кг/л) — вода затворения.
 */
public final class Conditioning {
    public static final double DRUM_L = 200, FILL = 0.9, WC = 0.45, ABRAMS_A_MPA = 96.5, ABRAMS_B = 7, SALT_LIMIT = 0.25;
    public static final double MIN_STRENGTH_MPA = 4.9, CEMENT_KG_L = 3.15, SALT_KG_L = 2.2, BITUMEN_KG_L = 1.02;
    public static final double BITUMEN_LOADING = 0.40, BITUMEN_MAX_LOADING = 0.5, RESIN_FRACTION = 0.35, RESIN_WATER_KG_L = 0.5;
    /** Теплоёмкость и теплота испарения воды для выпаривания в битуматоре, Дж/(кг·К) и Дж/кг. */
    public static final double CP_WATER = 4186, H_FG = 2.257e6;

    /**
     * Рецептура одной бочки.
     *
     * @param feedL        литров кубового остатка или смолы в бочке
     * @param binderKg     цемента или битума, кг
     * @param productKg    масса компаунда, кг
     * @param productL     объём компаунда, л
     * @param saltLoading  доля солей в компаунде по массе
     * @param strengthMPa  прочность на сжатие (у битума — NaN)
     * @param evaporatedKg вода, выпаренная в битуматоре, кг
     */
    public record Recipe(double feedL, double binderKg, double productKg, double productL, double saltLoading, double strengthMPa,
            double evaporatedKg) {
        public boolean compliant() {
            return Double.isNaN(strengthMPa) ? saltLoading <= BITUMEN_MAX_LOADING : strengthMPa >= MIN_STRENGTH_MPA;
        }
    }

    private Conditioning() {
    }

    /** Прочность цементного камня, МПа. */
    public static double strengthMPa(double wc, double saltLoading) {
        double salt = Math.max(0, 1 - Math.pow(saltLoading / SALT_LIMIT, 2));
        return ABRAMS_A_MPA / Math.pow(ABRAMS_B, wc) * salt;
    }

    /** Цементирование раствора с {@code saltGL} г/л солей. */
    public static Recipe cement(double saltGL) {
        double s = saltGL / 1000, w = Math.max(0.05, 1 - s / SALT_KG_L), c = w / WC;
        double perL = c / CEMENT_KG_L + w + s / SALT_KG_L;
        double feed = DRUM_L * FILL / perL;
        double loading = s / (c + w + s);
        return new Recipe(feed, c * feed, (c + w + s) * feed, DRUM_L * FILL, loading, strengthMPa(WC, loading), 0);
    }

    /** Битумирование раствора с {@code saltGL} г/л солей при проектной степени включения. */
    public static Recipe bitumen(double saltGL) {
        double s = Math.max(1e-3, saltGL / 1000), w = Math.max(0, 1 - s / SALT_KG_L);
        double b = s * (1 - BITUMEN_LOADING) / BITUMEN_LOADING;
        double perL = s / SALT_KG_L + b / BITUMEN_KG_L;
        double feed = DRUM_L * FILL / perL;
        return new Recipe(feed, b * feed, (s + b) * feed, DRUM_L * FILL, BITUMEN_LOADING, Double.NaN, w * feed);
    }

    /** Цементирование отработанной смолы: смола занимает {@link #RESIN_FRACTION} объёма компаунда. */
    public static Recipe cementResin() {
        double product = DRUM_L * FILL, resin = product * RESIN_FRACTION;
        // остальной объём — цементное тесто с В/Ц 0,45; вода внутри гранул смолы в затворение не идёт
        double cement = (product - resin) / (1 / CEMENT_KG_L + WC);
        double mass = resin * (1 + RESIN_WATER_KG_L * 0.2) + cement * (1 + WC);
        // смола — не соль, но тоже ослабляет камень: та же квадратичная поправка по её объёмной доле к пределу 0,6
        double strength = ABRAMS_A_MPA / Math.pow(ABRAMS_B, WC) * Math.max(0, 1 - Math.pow(RESIN_FRACTION / 0.6, 2));
        return new Recipe(resin, cement, mass, product, 0, strength, 0);
    }

    /** Тепло на выпаривание {@code kg} воды из раствора с {@code feedK}, Дж. */
    public static double evaporationJ(double kg, double feedK) {
        return kg * (CP_WATER * Math.max(0, 373.15 - feedK) + H_FG);
    }
}
