package dev.denfry.promptcritical.sim.waste;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.EnumMap;
import java.util.Map;

/**
 * Выпарная установка жидких радиоактивных отходов (СВО-3 АЭС с ВВЭР, IAEA TRS-370, 1994, «Treatment and
 * Conditioning of Radioactive Liquid Wastes»): трапные воды, регенерационные и дезактивационные растворы упаривают;
 * вода уходит конденсатом, соли и почти вся активность остаются в кубе.
 *
 * <ul>
 *   <li>Производительность — по мощности греющего пара {@value #HEATER_W} Вт: на литр нужно нагреть воду до 100 °C и
 *   испарить (2,257 МДж/кг) — ≈ 1 м³/ч, как у выпарного аппарата ВУ-1;</li>
 *   <li>куб упаривают до {@value #CONC_MAX_G_L} г/л солей: дальше бораты кристаллизуются в трубах греющей камеры;</li>
 *   <li>коэффициент очистки конденсата — {@value #DF} для нелетучих нуклидов (одна ступень с сепаратором брызг —
 *   10⁴–10⁶ по TRS-370), иод частью летуч — {@value #DF_IODINE};</li>
 *   <li>конденсат — практически чистая вода; его остаточная активность считается и показывается: сравнивать с
 *   уровнем вмешательства для питьевой воды НРБ-99/2009 (Cs-137 — 11 Бк/кг).</li>
 * </ul>
 * Активность раствора в кубе — {@link ActivityInventory}: распадается как всё остальное.
 */
public final class LrwEvaporator {
    public static final double HEATER_W = 700e3, CONC_MAX_G_L = 300, READY_FRACTION = 0.95, DF = 1e4, DF_IODINE = 100;
    /** Объём куба, л. */
    public static final double SUMP_L = 4000;

    /** Питание: соли, г/л, и активность нуклидов, Бк/л. */
    public record Feed(double saltGL, Map<Emitter, Double> bqPerL, double tempK) {
    }

    /**
     * Шаг: сколько питания взято, сколько конденсата выдано и его активность.
     *
     * @param feedL         взято питания, л
     * @param condensateL   выдано конденсата, л
     * @param condensateBq  активность конденсата, Бк
     * @param heatJ         затрачено тепла, Дж
     */
    public record Step(double feedL, double condensateL, double condensateBq, double heatJ) {
    }

    private double waterL, saltKg;
    private ActivityInventory liquor = new ActivityInventory(Map.of(), 0, 1);
    private double condensateBqTotal, condensateLTotal;

    public double waterL() {
        return waterL;
    }

    public double saltKg() {
        return saltKg;
    }

    /** Солесодержание куба, г/л. */
    public double saltGL() {
        return waterL > 0 ? saltKg * 1000 / waterL : 0;
    }

    public ActivityInventory liquor() {
        return liquor;
    }

    /** Куб упарен: можно отверждать. */
    public boolean ready() {
        return waterL > 1 && saltGL() >= CONC_MAX_G_L * READY_FRACTION;
    }

    /** Средняя активность конденсата за всё время, Бк/л. */
    public double condensateBqPerL() {
        return condensateLTotal > 0 ? condensateBqTotal / condensateLTotal : 0;
    }

    /**
     * Шаг выпарки длительностью {@code dtS}.
     *
     * @param feed       состав питания (null — питания нет)
     * @param availableL питания в баке, л
     * @param roomL      места под конденсат, л
     * @param heating    греющий пар подан
     */
    public Step step(double dtS, Feed feed, double availableL, double roomL, boolean heating) {
        liquor = liquor.decayed(dtS);
        if (!heating || dtS <= 0) return new Step(0, 0, 0, 0);
        double feedK = feed != null ? feed.tempK() : 293.15;
        double capacity = HEATER_W * dtS / Conditioning.evaporationJ(1, feedK);
        // питание — сколько уйдёт паром за шаг, пока в кубе есть место
        double take = feed == null ? 0 : Math.max(0, Math.min(availableL, Math.min(capacity, SUMP_L - waterL)));
        if (take > 0) {
            waterL += take;
            saltKg += take * feed.saltGL() / 1000;
            EnumMap<Emitter, Double> sum = new EnumMap<>(liquor.activities());
            for (Map.Entry<Emitter, Double> e : feed.bqPerL().entrySet()) sum.merge(e.getKey(), e.getValue() * take, Double::sum);
            liquor = new ActivityInventory(sum, waterL + saltKg, 1 + saltGL() / 1000 * 0.7);
        }
        double minWater = saltKg * 1000 / CONC_MAX_G_L;
        double evap = Math.max(0, Math.min(capacity, Math.min(roomL, waterL - minWater)));
        if (evap <= 0) return new Step(take, 0, 0, 0);
        double share = evap / waterL;
        EnumMap<Emitter, Double> left = new EnumMap<>(Emitter.class);
        double carried = 0;
        for (Map.Entry<Emitter, Double> e : liquor.activities().entrySet()) {
            double c = e.getValue() * share / (e.getKey() == Emitter.I131 ? DF_IODINE : DF);
            carried += c;
            left.put(e.getKey(), e.getValue() - c);
        }
        waterL -= evap;
        liquor = new ActivityInventory(left, waterL + saltKg, 1 + saltGL() / 1000 * 0.7);
        condensateBqTotal += carried;
        condensateLTotal += evap;
        return new Step(take, evap, carried, Conditioning.evaporationJ(evap, feedK));
    }

    /**
     * Слить из куба {@code litres} раствора на отверждение: соли и активность — пропорционально.
     *
     * @return слитая порция (масса — раствор, объём — литры / 1000 м³)
     */
    public WasteInventory draw(double litres) {
        double l = Math.min(litres, waterL);
        if (l <= 0) return WasteInventory.EMPTY;
        double share = l / waterL;
        EnumMap<Emitter, Double> part = new EnumMap<>(Emitter.class), rest = new EnumMap<>(Emitter.class);
        for (Map.Entry<Emitter, Double> e : liquor.activities().entrySet()) {
            part.put(e.getKey(), e.getValue() * share);
            rest.put(e.getKey(), e.getValue() * (1 - share));
        }
        double s = saltKg * share;
        waterL -= l;
        saltKg -= s;
        liquor = new ActivityInventory(rest, waterL + saltKg, liquor.densityGcc());
        return new WasteInventory(new ActivityInventory(part, l + s, 1 + s / l * 0.7), Map.of(), l / 1000);
    }

    public void save(StateWriter out) {
        out.putDouble("water", waterL);
        out.putDouble("salt", saltKg);
        out.putDouble("cond_bq", condensateBqTotal);
        out.putDouble("cond_l", condensateLTotal);
        WasteState.putActivities(out, "bq", liquor.activities());
    }

    public void load(StateReader in) {
        waterL = in.optDouble("water", 0);
        saltKg = in.optDouble("salt", 0);
        condensateBqTotal = in.optDouble("cond_bq", 0);
        condensateLTotal = in.optDouble("cond_l", 0);
        liquor = new ActivityInventory(WasteState.getActivities(in, "bq"), waterL + saltKg, 1 + saltGL() / 1000 * 0.7);
    }
}
