package dev.denfry.promptcritical.sim.petrochem;

import java.util.EnumMap;
import java.util.Map;

/**
 * Сырьё пиролиза. Этан крекируется по молекулярной схеме {@link EthaneCracking}; остальное — по литературным выходам,
 * массовым долям на сырьё, при двух жёсткостях (Zimmermann, Walzl, «Ethylene», Ullmann's Encyclopedia of Industrial
 * Chemistry, 2009, табл. 4–6; Мухина Т.Н., Барабанов Н.Л. и др., «Пиролиз углеводородного сырья», 1987, гл. 3):
 * мягкий режим — больше пропилена, бутенов и пиробензина, жёсткий — больше этилена, метана и смолы. Между ними выходы
 * интерполируются по степени превращения сырья, которую даёт брутто-кинетика первого порядка k = A·exp(−E/RT) по
 * профилю температуры змеевика (пропан — сумма двух первичных реакций Sundaram, Froment 1977; н-бутан и бензины —
 * калибровка по типичной жёсткости промышленных печей [оценка]).
 *
 * <p>Выходы проверены по балансу водорода: доля H в продуктах равна доле H в сырье с точностью до процента.
 * Выходы для газового бензина — между бутаном и лёгкой нафтой [оценка].
 *
 * @param feedComponent    компонент самого сырья в продуктах (непревращённый остаток); {@code null} — бензины
 * @param hydrogenFraction доля водорода в сырье по массе
 * @param densityKgM3      плотность жидкого сырья (у этана — сжиженного), кг/м³
 * @param a                предэкспонента брутто-реакции, с⁻¹
 * @param eJ               энергия активации, Дж/моль
 * @param lowConversion    степень превращения мягкого режима
 * @param highConversion   степень превращения жёсткого режима
 * @param heatOfCrackingJKg тепловой эффект пиролиза на кг превращённого сырья, Дж/кг (эндотермический)
 * @param cokeFactor       относительная склонность к коксованию змеевика (этан — 1)
 * @param inletK           температура на входе в радиантную секцию, К
 */
public record PyrolysisFeed(String id, PyroComponent feedComponent, double hydrogenFraction, double densityKgM3, double a,
        double eJ, double lowConversion, double highConversion, Map<PyroComponent, Double> low, Map<PyroComponent, Double> high,
        double heatOfCrackingJKg, double cokeFactor, double inletK) {

    public static final PyrolysisFeed ETHANE = new PyrolysisFeed("ethane", PyroComponent.C2H6, 6 * 1.008 / 30.07, 546,
            4.652e13, 272.8e3, 0.55, 0.70, ethaneUllmann(), ethaneUllmann(), 4.6e6, 1.0, 923);
    public static final PyrolysisFeed PROPANE = new PyrolysisFeed("propane", PyroComponent.C3H8, 8 * 1.008 / 44.097, 507,
            1.06e11, 213.0e3, 0.80, 0.92, propane(), propane(), 2.9e6, 1.3, 913);
    public static final PyrolysisFeed BUTANE = new PyrolysisFeed("butane", PyroComponent.C4, 10 * 1.008 / 58.123, 575,
            7.0e12, 249.5e3, 0.85, 0.96, butane(), butane(), 2.6e6, 1.6, 903);
    public static final PyrolysisFeed NATURAL_GASOLINE = new PyrolysisFeed("natural_gasoline", null, 0.162, 660,
            2.0e12, 225.0e3, 0.80, 0.95, slate(0.9, 13.5, 28.0, 4.0, 17.0, 0.5, 4.5, 7.5, 22.0, 2.1),
            slate(1.1, 17.5, 34.0, 3.8, 14.0, 0.3, 4.6, 3.2, 17.5, 4.0), 2.2e6, 2.2, 873);
    public static final PyrolysisFeed NAPHTHA = new PyrolysisFeed("naphtha", null, 0.150, 735,
            2.0e12, 225.0e3, 0.80, 0.95, slate(0.8, 12.0, 25.0, 3.7, 16.0, 0.5, 4.5, 7.0, 27.0, 3.5),
            slate(1.0, 16.0, 32.0, 3.5, 13.0, 0.3, 4.7, 3.0, 21.0, 5.5), 2.1e6, 2.5, 873);

    public static final PyrolysisFeed[] ALL = {ETHANE, PROPANE, BUTANE, NATURAL_GASOLINE, NAPHTHA};

    /** Этан при 65 % превращения (Ullmann, «Ethylene», табл. 4) — для сверки молекулярной схемы и балансов. */
    public static Map<PyroComponent, Double> ethaneUllmann() {
        Map<PyroComponent, Double> m = new EnumMap<>(PyroComponent.class);
        m.put(PyroComponent.H2, 4.0);
        m.put(PyroComponent.CH4, 3.7);
        m.put(PyroComponent.C2H4, 52.0);
        m.put(PyroComponent.C2H6, 35.0);
        m.put(PyroComponent.C3H6, 1.2);
        m.put(PyroComponent.C4H6, 1.5);
        m.put(PyroComponent.C4, 0.5);
        m.put(PyroComponent.PYGAS, 1.7);
        m.put(PyroComponent.FUEL_OIL, 0.4);
        return m;
    }

    /** Пропан при 92 % превращения. */
    private static Map<PyroComponent, Double> propane() {
        Map<PyroComponent, Double> m = new EnumMap<>(PyroComponent.class);
        m.put(PyroComponent.H2, 1.2);
        m.put(PyroComponent.CH4, 23.8);
        m.put(PyroComponent.C2H4, 41.0);
        m.put(PyroComponent.C2H6, 3.5);
        m.put(PyroComponent.C3H6, 15.5);
        m.put(PyroComponent.C3H8, 8.0);
        m.put(PyroComponent.C4H6, 2.8);
        m.put(PyroComponent.C4, 1.2);
        m.put(PyroComponent.PYGAS, 2.5);
        m.put(PyroComponent.FUEL_OIL, 0.5);
        return m;
    }

    /** Н-бутан при 96 % превращения. */
    private static Map<PyroComponent, Double> butane() {
        return slate(1.0, 20.5, 37.5, 4.5, 17.0, 0.5, 3.5, 5.5, 8.0, 2.0);
    }

    private static Map<PyroComponent, Double> slate(double h2, double ch4, double c2h4, double c2h6, double c3h6, double c3h8,
            double c4h6, double c4, double pygas, double fuelOil) {
        Map<PyroComponent, Double> m = new EnumMap<>(PyroComponent.class);
        m.put(PyroComponent.H2, h2);
        m.put(PyroComponent.CH4, ch4);
        m.put(PyroComponent.C2H4, c2h4);
        m.put(PyroComponent.C2H6, c2h6);
        m.put(PyroComponent.C3H6, c3h6);
        m.put(PyroComponent.C3H8, c3h8);
        m.put(PyroComponent.C4H6, c4h6);
        m.put(PyroComponent.C4, c4);
        m.put(PyroComponent.PYGAS, pygas);
        m.put(PyroComponent.FUEL_OIL, fuelOil);
        return m;
    }

    /** Брутто-константа превращения сырья при T, с⁻¹. */
    public double rate(double tK) {
        return a * Math.exp(-eJ / (EthaneCracking.R * tK));
    }

    /**
     * Выходы, массовые доли на сырьё, при степени превращения {@code x}: интерполяция между мягким и жёстким режимом,
     * за их пределами — непревращённое сырьё уходит в продукты как есть (у бензинов — в пиробензин).
     */
    public double[] yields(double x) {
        double t = highConversion == lowConversion ? 1 : Math.max(0, Math.min(1, (x - lowConversion) / (highConversion - lowConversion)));
        double xRef = lowConversion + t * (highConversion - lowConversion);
        double[] y = new double[PyroComponent.N];
        for (PyroComponent c : PyroComponent.values())
            y[c.ordinal()] = (low.getOrDefault(c, 0.0) * (1 - t) + high.getOrDefault(c, 0.0) * t) / 100;
        // приведение к нужной степени превращения: продукты превращения ∝ x/x_ref, остаток сырья — 1 − x
        PyroComponent rest = feedComponent != null ? feedComponent : PyroComponent.PYGAS;
        double restRef = feedComponent != null ? 1 - xRef : 0;
        double scale = xRef > 0 ? Math.min(1 / xRef, x / xRef) : 0;
        double sum = 0;
        for (int i = 0; i < y.length; i++) {
            double v = y[i];
            if (i == rest.ordinal()) v -= restRef;
            y[i] = Math.max(0, v) * scale;
            sum += y[i];
        }
        y[rest.ordinal()] += Math.max(0, 1 - sum);
        return y;
    }
}
