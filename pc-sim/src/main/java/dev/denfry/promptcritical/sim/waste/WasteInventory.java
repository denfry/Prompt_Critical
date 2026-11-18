package dev.denfry.promptcritical.sim.waste;

import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Радионуклидный состав упаковки или отсека хранилища: γ-излучатели в {@link ActivityInventory} (их распад с
 * дочерними, источник γ и тепло — общие с остальным модом) плюс долгоживущие {@link LongLivedNuclide}, объём и масса
 * упаковки. Неизменяемый.
 */
public final class WasteInventory {
    public static final WasteInventory EMPTY = new WasteInventory(new ActivityInventory(Map.of(), 0, 1), Map.of(), 0);

    /** Группы ОСПОРБ-99/2010 для категории: β (кроме трития), α (кроме трансурановых), трансурановые. */
    public enum Group { BETA, ALPHA, TRANSURANIC }

    /**
     * Один нуклид с точки зрения миграции.
     *
     * @param name        имя (для отчёта о доминирующем вкладе)
     * @param element     элемент — сорбция и накопление в рыбе
     * @param lambdaPerY  постоянная распада, 1/год
     * @param bq          активность, Бк
     * @param dose        дозовый коэффициент, Зв/Бк
     * @param daughterIdx индекс дочернего нуклида в списке или −1
     */
    public record Line(String name, Element element, double lambdaPerY, double bq, double dose, int daughterIdx) {
    }

    private final ActivityInventory emitters;
    private final EnumMap<LongLivedNuclide, Double> longLived = new EnumMap<>(LongLivedNuclide.class);
    private final double volumeM3;

    public WasteInventory(ActivityInventory emitters, Map<LongLivedNuclide, Double> longLived, double volumeM3) {
        this.emitters = emitters;
        for (Map.Entry<LongLivedNuclide, Double> e : longLived.entrySet()) if (e.getValue() > 0) this.longLived.put(e.getKey(), e.getValue());
        this.volumeM3 = Math.max(0, volumeM3);
    }

    public ActivityInventory emitters() {
        return emitters;
    }

    public Map<LongLivedNuclide, Double> longLived() {
        return new EnumMap<>(longLived);
    }

    public double bq(LongLivedNuclide n) {
        return longLived.getOrDefault(n, 0.0);
    }

    public double massKg() {
        return emitters.massKg();
    }

    public double volumeM3() {
        return volumeM3;
    }

    public boolean empty() {
        return totalBq() <= 0 && massKg() <= 0;
    }

    public double totalBq() {
        double s = emitters.totalBq();
        for (double v : longLived.values()) s += v;
        return s;
    }

    /** Остаточное тепловыделение, Вт. */
    public double heatW() {
        double q = emitters.decayHeatW();
        for (Map.Entry<LongLivedNuclide, Double> e : longLived.entrySet()) q += e.getValue() * e.getKey().heatMeV * DoseRate.MEV_J;
        return q;
    }

    /** Тепловыделение на объём, Вт/м³ (0 при нулевом объёме). */
    public double heatDensityWm3() {
        return volumeM3 > 0 ? heatW() / volumeM3 : 0;
    }

    /** Удельная активность группы, Бк/кг. */
    public double specificBqKg(Group g) {
        if (massKg() <= 0) return 0;
        double s = 0;
        for (Map.Entry<Emitter, Double> e : emitters.activities().entrySet()) if (group(e.getKey()) == g) s += e.getValue();
        for (Map.Entry<LongLivedNuclide, Double> e : longLived.entrySet()) if (group(e.getKey()) == g) s += e.getValue();
        return s / massKg();
    }

    /**
     * Удельная активность долгоживущих (T½ &gt; 31 года — Cs-137 и Sr-90 ещё короткоживущие, как в IAEA GSG-1) α-излучателей,
     * Бк/г: критерий IAEA GSG-1 §2.2 для приповерхностного захоронения — в среднем не больше 400 Бк/г.
     */
    public double longLivedAlphaBqG() {
        if (massKg() <= 0) return 0;
        double s = emitters.bq(Emitter.PU238);
        for (Map.Entry<LongLivedNuclide, Double> e : longLived.entrySet()) if (e.getKey().transuranic) s += e.getValue();
        return s / (massKg() * 1000);
    }

    static Group group(Emitter e) {
        return EmitterData.transuranic(e) ? Group.TRANSURANIC : Group.BETA;
    }

    static Group group(LongLivedNuclide n) {
        return n.transuranic ? Group.TRANSURANIC : Group.BETA;
    }

    /** Состав через {@code tS} секунд: γ-излучатели — по {@link ActivityInventory#decayed}, долгоживущие — с Am-241 → Np-237. */
    public WasteInventory decayed(double tS) {
        if (tS <= 0) return this;
        EnumMap<LongLivedNuclide, Double> out = new EnumMap<>(LongLivedNuclide.class);
        for (Map.Entry<LongLivedNuclide, Double> e : longLived.entrySet()) {
            LongLivedNuclide p = e.getKey();
            double l1 = p.lambda();
            out.merge(p, e.getValue() * Math.exp(-l1 * tS), Double::sum);
            if (p.daughter != null) out.merge(p.daughter, bateman(e.getValue(), l1, p.daughter.lambda(), tS), Double::sum);
        }
        return new WasteInventory(emitters.decayed(tS), out, volumeM3);
    }

    /** Активность дочернего, накопленная из родителя активностью {@code a1} за {@code t}. */
    static double bateman(double a1, double l1, double l2, double t) {
        return Math.abs(l2 - l1) < 1e-30 ? a1 * l2 * t * Math.exp(-l1 * t) : a1 * l2 / (l2 - l1) * (Math.exp(-l1 * t) - Math.exp(-l2 * t));
    }

    /** Сумма двух составов: масса и объём складываются, плотность — средняя. */
    public WasteInventory plus(WasteInventory o) {
        EnumMap<Emitter, Double> e = new EnumMap<>(Emitter.class);
        emitters.activities().forEach((k, v) -> e.merge(k, v, Double::sum));
        o.emitters.activities().forEach((k, v) -> e.merge(k, v, Double::sum));
        EnumMap<LongLivedNuclide, Double> l = new EnumMap<>(longLived);
        o.longLived.forEach((k, v) -> l.merge(k, v, Double::sum));
        double m = massKg() + o.massKg(), v = volumeM3 + o.volumeM3;
        return new WasteInventory(new ActivityInventory(e, m, v > 0 ? m / (v * 1000) : emitters.densityGcc()), l, v);
    }

    /** Доля {@code k} состава (часть упаковки или раствора). */
    public WasteInventory scaled(double k) {
        EnumMap<Emitter, Double> e = new EnumMap<>(Emitter.class);
        emitters.activities().forEach((n, v) -> e.put(n, v * k));
        EnumMap<LongLivedNuclide, Double> l = new EnumMap<>(LongLivedNuclide.class);
        longLived.forEach((n, v) -> l.put(n, v * k));
        return new WasteInventory(new ActivityInventory(e, massKg() * k, emitters.densityGcc()), l, volumeM3 * k);
    }

    /** Тот же состав с другими массой и объёмом (после кондиционирования матрицей). */
    public WasteInventory repacked(double massKg, double volumeM3) {
        return new WasteInventory(new ActivityInventory(emitters.activities(), massKg, volumeM3 > 0 ? massKg / (volumeM3 * 1000) : 1),
                longLived, volumeM3);
    }

    /** Нуклиды для модели миграции; Am-241 указывает на Np-237, если тот в списке. */
    public List<Line> lines() {
        List<Line> out = new ArrayList<>();
        for (Map.Entry<Emitter, Double> e : emitters.activities().entrySet()) {
            Emitter n = e.getKey();
            out.add(new Line(n.name(), EmitterData.element(n), Math.log(2) / EmitterData.halfLifeY(n), e.getValue(), EmitterData.doseSvPerBq(n), -1));
        }
        int np = -1;
        int am = -1;
        for (LongLivedNuclide n : LongLivedNuclide.values()) {
            double a = longLived.getOrDefault(n, 0.0);
            // Np-237 нужен в списке и при нулевой активности: он нарастает из Am-241
            if (a <= 0 && !(n == LongLivedNuclide.NP237 && longLived.containsKey(LongLivedNuclide.AM241))) continue;
            if (n == LongLivedNuclide.NP237) np = out.size();
            if (n == LongLivedNuclide.AM241) am = out.size();
            out.add(new Line(n.name(), n.element, Math.log(2) / n.halfLifeY, a, n.doseSvPerBq, -1));
        }
        if (am >= 0 && np >= 0) {
            Line a = out.get(am);
            out.set(am, new Line(a.name(), a.element(), a.lambdaPerY(), a.bq(), a.dose(), np));
        }
        return out;
    }
}
