package dev.denfry.promptcritical.sim.waste;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.waste.WasteClassification.Route;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Пункт захоронения радиоактивных отходов: приём упаковок по критериям приемлемости, учёт инвентаря по формам
 * отходов, распад, тепловыделение и оценка долговременного облучения населения после закрытия.
 *
 * <p><b>Критерии приёма.</b> Компаунд соответствует нормам (прочность цемента, включение солей битума). Приповерхностный
 * пункт принимает только классы 3 и 4 ({@link WasteClassification}): без тепловыделяющих и долгоживущих α-отходов;
 * мощность дозы на поверхности упаковки — не выше 2 мЗв/ч (IAEA SSR-6, предел для упаковки при неисключительном
 * использовании; измеряется на 0,3 м от оси бочки). Пункт глубинного захоронения (ПГЗРО) принимает всё твёрдое, но
 * стеклоблоки — только в медном контейнере (концепция KBS-3: медь 5 см, срок службы 10⁵ лет) и с тепловыделением не выше
 * {@value #MAX_DEEP_PACKAGE_W} Вт на упаковку (KBS-3: 1700 Вт — бентонит буфера не выше 100 °C).
 *
 * <p><b>Модель выхода</b> (схема безопасности уровня IAEA SSR-5 / ISAM, консервативная и одномерная):
 * <ol>
 *   <li>до разрушения контейнеров (t_c: приповерхностный — 300 лет инженерных барьеров и институционального контроля,
 *   глубинный — 1000 лет стали, 10⁵ лет меди) выхода нет;</li>
 *   <li>после — первый порядок k из {@link WasteForm#releasePerYear}, запас в матрице убывает и распадом, и выходом;</li>
 *   <li>перенос к реке — по пути L с поровой скоростью v и задержкой R = 1 + ρ_b·K_d/n; время пути τ = L·R/v, за путь
 *   нуклид распадается в e^{−λτ} раз; дисперсия не учтена (пик острее и выше — консервативно);</li>
 *   <li>в реке разбавляется годовым стоком Q водосбора; критическая группа пьёт {@value #DRINK_M3_Y} м³ воды и ест
 *   {@value #FISH_KG_Y} кг рыбы в год; доза — дозовыми коэффициентами ICRP 72.</li>
 * </ol>
 * Сравнивать — с граничной дозой 0,3 мЗв/год (IAEA SSR-5 §2.15). Приповерхностный пункт: 100 м песка, v = 5 м/год,
 * n = 0,25, ρ_b = 1600 кг/м³ — I-129, Tc-99 и C-14 приходят за десятки лет, Cs-137 и Sr-90 распадаются в пути.
 * Глубинный: 500 м глины, v = 1 см/год, n = 0,1, ρ_b = 2200 кг/м³ — даже иод идёт миллионы лет.
 */
public final class Repository {
    public enum Type {
        NEAR_SURFACE(300, 100, 5, 0.25, 1600, false, 2e-3, 4096),
        DEEP_GEOLOGICAL(1000, 500, 0.01, 0.10, 2200, true, Double.POSITIVE_INFINITY, 4096);

        public final double containerLifeY, pathM, poreVelocityMY, porosity, bulkDensityKgM3, maxSurfaceSvH;
        public final boolean clay;
        public final int capacity;

        Type(double containerLifeY, double pathM, double poreVelocityMY, double porosity, double bulkDensityKgM3, boolean clay,
                double maxSurfaceSvH, int capacity) {
            this.containerLifeY = containerLifeY;
            this.pathM = pathM;
            this.poreVelocityMY = poreVelocityMY;
            this.porosity = porosity;
            this.bulkDensityKgM3 = bulkDensityKgM3;
            this.clay = clay;
            this.maxSurfaceSvH = maxSurfaceSvH;
            this.capacity = capacity;
        }
    }

    public enum Verdict { ACCEPTED, NON_COMPLIANT, WRONG_CLASS, DOSE_RATE, TOO_HOT, NEEDS_OVERPACK, FULL, EMPTY }

    /**
     * Упаковка на приёме.
     *
     * @param form       форма отходов
     * @param inventory  полный состав (с трудноизмеряемыми)
     * @param compliant  компаунд соответствует нормам
     * @param surfaceSvH мощность дозы у поверхности, Зв/ч
     * @param overpack   есть медный контейнер (для стекла в ПГЗРО)
     */
    public record Package(WasteForm form, WasteInventory inventory, boolean compliant, double surfaceSvH, boolean overpack) {
    }

    /** Пик годовой дозы: Зв/год, через сколько лет после закрытия, какой нуклид даёт больше всего. */
    public record Peak(double svPerY, double years, String nuclide) {
    }

    public static final double OVERPACK_LIFE_Y = 1e5, MAX_DEEP_PACKAGE_W = 1700, DRINK_M3_Y = 0.73, FISH_KG_Y = 20,
            DOSE_CONSTRAINT_SV_Y = 3e-4;
    static final int GRID_PER_DECADE = 24, GRID_DECADES = 7;

    private final Type type;
    private final EnumMap<WasteForm, WasteInventory> cells = new EnumMap<>(WasteForm.class);
    private final EnumMap<WasteForm, Integer> counts = new EnumMap<>(WasteForm.class);

    public Repository(Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public int packages() {
        int n = 0;
        for (int c : counts.values()) n += c;
        return n;
    }

    public int packages(WasteForm f) {
        return counts.getOrDefault(f, 0);
    }

    public WasteInventory inventory(WasteForm f) {
        return cells.getOrDefault(f, WasteInventory.EMPTY);
    }

    public WasteInventory total() {
        WasteInventory t = WasteInventory.EMPTY;
        for (WasteInventory w : cells.values()) t = t.plus(w);
        return t;
    }

    public double heatW() {
        double q = 0;
        for (WasteInventory w : cells.values()) q += w.heatW();
        return q;
    }

    public double totalBq() {
        double a = 0;
        for (WasteInventory w : cells.values()) a += w.totalBq();
        return a;
    }

    /** Проверка упаковки без приёма. */
    public Verdict check(Package p) {
        if (p.inventory().empty()) return Verdict.EMPTY;
        if (!p.compliant()) return Verdict.NON_COMPLIANT;
        if (packages() >= type.capacity) return Verdict.FULL;
        WasteClassification.Result c = WasteClassification.classify(p.inventory(), false);
        if (type == Type.NEAR_SURFACE) {
            if (c.route() != Route.NEAR_SURFACE || p.form().glass()) return Verdict.WRONG_CLASS;
            if (p.surfaceSvH() > type.maxSurfaceSvH) return Verdict.DOSE_RATE;
        } else {
            if (p.form().glass() && !p.overpack()) return Verdict.NEEDS_OVERPACK;
            if (p.inventory().heatW() > MAX_DEEP_PACKAGE_W) return Verdict.TOO_HOT;
        }
        return Verdict.ACCEPTED;
    }

    /** Принять упаковку, если проходит критерии. */
    public Verdict accept(Package p) {
        Verdict v = check(p);
        if (v != Verdict.ACCEPTED) return v;
        cells.merge(p.form(), p.inventory(), WasteInventory::plus);
        counts.merge(p.form(), 1, Integer::sum);
        return v;
    }

    /** Распад всего инвентаря за {@code dtS} секунд. */
    public void decay(double dtS) {
        if (dtS <= 0) return;
        cells.replaceAll((f, w) -> w.decayed(dtS));
    }

    /** Коэффициент задержки элемента на пути к реке. */
    public double retardation(Element e) {
        double kd = type.clay ? e.kdClayM3Kg : e.kdSandM3Kg;
        return 1 + type.bulkDensityKgM3 * kd / type.porosity;
    }

    /** Время пути элемента до реки, годы. */
    public double travelY(Element e) {
        return type.pathM * retardation(e) / type.poreVelocityMY;
    }

    /** Срок службы контейнера формы, годы. */
    public double containerLifeY(WasteForm f) {
        return type == Type.DEEP_GEOLOGICAL && f.glass() ? OVERPACK_LIFE_Y : type.containerLifeY;
    }

    /** Годовая доза критической группы через {@code years} после закрытия при разбавлении {@code dilutionM3Y}, Зв/год. */
    public double doseSvY(double years, double dilutionM3Y) {
        double[] byLine = new double[1];
        return dose(years, dilutionM3Y, null, byLine);
    }

    private double dose(double years, double dilutionM3Y, String[] dominant, double[] best) {
        if (dilutionM3Y <= 0) return Double.POSITIVE_INFINITY;
        double total = 0;
        best[0] = 0;
        for (Map.Entry<WasteForm, WasteInventory> c : cells.entrySet()) {
            WasteForm f = c.getKey();
            int n = Math.max(1, counts.getOrDefault(f, 1));
            double k = f.releasePerYear(c.getValue().massKg() / n), tc = containerLifeY(f);
            List<WasteInventory.Line> lines = c.getValue().lines();
            for (int i = 0; i < lines.size(); i++) {
                WasteInventory.Line l = lines.get(i);
                double d = flux(l, l.bq(), l.lambdaPerY(), k, tc, years, 0);
                for (WasteInventory.Line p : lines) {
                    if (p.daughterIdx() == i) d += flux(l, p.bq(), p.lambdaPerY(), k, tc, years, l.lambdaPerY());
                }
                double sv = d / dilutionM3Y * (DRINK_M3_Y + FISH_KG_Y * l.element().fishM3Kg) * l.dose();
                total += sv;
                if (sv > best[0]) {
                    best[0] = sv;
                    if (dominant != null) dominant[0] = l.name();
                }
            }
        }
        return total;
    }

    /**
     * Поток нуклида в реку в год {@code t}, Бк/год. Если {@code lParent} &gt; 0, это дочерний, накопленный в матрице из
     * родителя с начальной активностью {@code a0} и постоянной {@code lParent}; иначе — свой запас {@code a0}.
     */
    private double flux(WasteInventory.Line line, double a0, double lParent, double k, double tc, double t, double lDaughter) {
        if (a0 <= 0 || k <= 0) return 0;
        double tau = travelY(line.element());
        double leave = t - tau;
        if (leave <= tc) return 0;
        double inMatrix = lDaughter > 0 ? WasteInventory.bateman(a0, lParent, lDaughter, leave) : a0 * Math.exp(-lParent * leave);
        inMatrix *= Math.exp(-k * (leave - tc));
        return k * inMatrix * Math.exp(-line.lambdaPerY() * tau);
    }

    /** Пик годовой дозы на логарифмической сетке от 1 года до 10⁷ лет. */
    public Peak peak(double dilutionM3Y) {
        double bestSv = 0, bestY = 0;
        String bestN = "";
        String[] dom = new String[1];
        double[] b = new double[1];
        int n = GRID_PER_DECADE * GRID_DECADES;
        for (int i = 0; i <= n; i++) {
            double y = Math.pow(10, (double) i / GRID_PER_DECADE);
            dom[0] = "";
            double sv = dose(y, dilutionM3Y, dom, b);
            if (sv > bestSv) {
                bestSv = sv;
                bestY = y;
                bestN = dom[0];
            }
        }
        return new Peak(bestSv, bestY, bestN);
    }

    public void save(StateWriter out) {
        out.beginObject("cells");
        for (Map.Entry<WasteForm, WasteInventory> e : cells.entrySet()) {
            out.beginObject(e.getKey().name());
            out.putInt("count", counts.getOrDefault(e.getKey(), 0));
            WasteState.putInventory(out, "inv", e.getValue());
            out.endObject();
        }
        out.endObject();
    }

    public void load(StateReader in) {
        cells.clear();
        counts.clear();
        if (!in.has("cells")) return;
        in.beginObject("cells");
        for (String k : in.keys()) {
            WasteForm f;
            try {
                f = WasteForm.valueOf(k);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            in.beginObject(k);
            counts.put(f, in.optInt("count", 0));
            cells.put(f, WasteState.getInventory(in, "inv"));
            in.endObject();
        }
        in.endObject();
    }
}
