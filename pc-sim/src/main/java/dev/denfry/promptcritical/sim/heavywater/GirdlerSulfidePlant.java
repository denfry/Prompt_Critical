package dev.denfry.promptcritical.sim.heavywater;

/**
 * Сероводородный (Girdler-Sulfide, GS) завод: двухтемпературный изотопный обмен H₂O + HDS ⇄ HDO + H₂S. Вода сверху
 * вниз проходит холодную колонну (30 °C), где забирает дейтерий из встречного H₂S (α_х ≈ 2,32), затем горячую
 * (130 °C), где отдаёт его обратно газу слабее (α_г ≈ 1,80); газ циркулирует по кругу, и между колоннами
 * накапливается обогащённая вода — её отбирают продуктом. Природная вода идёт питанием и уходит обеднённой в озеро —
 * поэтому заводы GS стоят у больших водоёмов (Брюс — на озере Гурон, Глейс-Бей — у моря).
 *
 * <p>Отвал не беднее равновесия горячей колонны: отношение изотопов x_w/(1 − x_w) ≥ (α_г/α_х)·x_f/(1 − x_f), отсюда
 * предельное извлечение дейтерия 1 − α_г/α_х ≈ 22 %; реальные заводы извлекали около 18–20 % (Benedict, Pigford,
 * Levi, «Nuclear Chemical Engineering», 1981, гл. 13; Miller, CNS Bulletin 22 (2001) 1). Пара «холодная + горячая
 * тарелка» обогащает в α_х/α_г ≈ 1,29 раза; разделение каскада — {@link IsotopeColumn#separation} по этому отношению.
 * Блок — завод из трёх ступеней GS с {@value #PLATE_PAIRS} идеальными парами тарелок; продукт — вода
 * {@link HeavyWaterGrade#ENRICHED} 15 % D. Сероводород — внутренняя загрузка завода, наружу не выходит.
 *
 * <p>Питание блока — {@value #FEED_KG_S} кг/с озёрной воды [BALANCE: один блок — целый завод]: проектные 800 т D₂O в
 * год завода Брюс при извлечении ~18 % требуют порядка 750 кг/с питания (Miller 2001).
 */
public final class GirdlerSulfidePlant {
    public static final double COLD_K = 303.15, HOT_K = 403.15, PLATE_PAIRS = 60, FEED_KG_S = 1000;
    /** Пределы горячей колонны: ниже 100 °C разделение мало, выше 140 °C — давление и коррозия H₂S. */
    public static final double HOT_MIN_K = 373.15, HOT_MAX_K = 413.15;

    private double hotK = HOT_K;
    private double feedKg, productKg, lastRecovery, lastWasteX = Double.NaN;

    public double hotK() {
        return hotK;
    }

    public void setHotK(double t) {
        hotK = Math.max(HOT_MIN_K, Math.min(HOT_MAX_K, t));
    }

    /** Предельное извлечение дейтерия при текущих температурах. */
    public double maxRecovery() {
        return 1 - HeavyWater.gsAlpha(hotK) / HeavyWater.gsAlpha(COLD_K);
    }

    /** Разделение завода (отношение изотопов продукта к отвалу) по рабочим тарелкам. */
    public double separation() {
        return IsotopeColumn.separation(HeavyWater.gsAlpha(COLD_K) / HeavyWater.gsAlpha(hotK), PLATE_PAIRS);
    }

    /** Разделение {@code xf} на продукт {@link HeavyWaterGrade#ENRICHED}. */
    public IsotopeColumn.Split split(double xf) {
        double floor = HeavyWater.fromRatio(HeavyWater.ratio(xf) * HeavyWater.gsAlpha(hotK) / HeavyWater.gsAlpha(COLD_K));
        return IsotopeColumn.split(xf, HeavyWaterGrade.ENRICHED.x, separation(), floor);
    }

    /**
     * Переработать {@code feedKgIn} кг воды с долей D {@code xf}; возвращает массу продукта 15 % D, кг.
     */
    public double process(double feedKgIn, double xf) {
        if (!(feedKgIn > 0)) return 0;
        IsotopeColumn.Split s = split(xf);
        lastRecovery = s.recovery();
        lastWasteX = s.wasteX();
        if (!s.works()) return 0;
        double mol = HeavyWater.moles(feedKgIn, xf) * s.productPerFeed();
        double out = HeavyWater.kg(mol, HeavyWaterGrade.ENRICHED.x);
        feedKg += feedKgIn;
        productKg += out;
        return out;
    }

    public double lastRecovery() {
        return lastRecovery;
    }

    public double lastWasteX() {
        return lastWasteX;
    }

    public double totalFeedKg() {
        return feedKg;
    }

    public double totalProductKg() {
        return productKg;
    }

    public void restoreTotals(double feed, double product, double hot) {
        feedKg = Math.max(0, feed);
        productKg = Math.max(0, product);
        setHotK(hot);
    }
}
