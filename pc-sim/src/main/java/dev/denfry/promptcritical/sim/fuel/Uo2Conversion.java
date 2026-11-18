package dev.denfry.promptcritical.sim.fuel;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.enrichment.Criticality;
import dev.denfry.promptcritical.sim.uranium.AduPrecipitation;
import dev.denfry.promptcritical.sim.uranium.DryConversion;

/**
 * Конверсия обогащённого UF₆ в керамический порошок UO₂ по «мокрой» схеме через диуранат аммония (АДУ): UF₆
 * испаряют и гидролизуют водой (UF₆ + 2H₂O → UO₂F₂ + 4HF), аммиаком осаждают ДУА и нейтрализуют HF
 * ({@link AduPrecipitation}: тот же реагент, та же полнота осаждения), ДУА фильтруют и в печи восстанавливают водородом
 * до UO₂ при 600–700 °C ({@link DryConversion.Step#REDUCE}).
 *
 * <p>Почему АДУ, а не сухая ИДР: АДУ-порошок мелкий и рыхлый (удельная поверхность 4–6 м²/г) и спекается до 95–96 %
 * без добавок, процесс простой и периодический — так работали первые заводы ТВЭЛ (Электросталь, Новосибирск) и
 * американские до 1980-х; аммиак и водород в игре уже есть (азотное производство, хлорный электролиз). Сухая схема
 * (IDR) экономнее по отходам — фтористого аммония нет, — но требует перегретого пара и печи с псевдоожиженным
 * слоем, и её порошок хуже прессуется без доработки (Olander, гл. 14; Benedict–Pigford–Levi, гл. 5.9).
 *
 * <p>Ядерная безопасность: раствор UO₂F₂ — лучший замедлитель из всех форм, поэтому U-235 в аппаратах участка держат
 * ниже безопасной массы {@link Criticality#safeU235Kg}: питание уменьшают, пока запас не уйдёт продуктом. Обогащение
 * выше 20 % цех не принимает: это уже ВОУ.
 */
public final class Uo2Conversion {
    /** Производительность линии, кг U/с [BALANCE: опытный участок ≈ 70 кг U/ч]. */
    public static final double RATED_KG_U_S = 0.02, MAX_ENRICHMENT = 0.20;
    /** Вода на гидролиз и разбавление, кг на кг U (стехиометрия 0,15, раствор ≈ 300 г U/л). */
    public static final double WATER_KG_PER_KG_U = 3.3;

    private final AduPrecipitation adu = new AduPrecipitation();
    private final DryConversion reduce = new DryConversion(DryConversion.Step.REDUCE);
    private final MaterialBalance balance = new MaterialBalance();
    /** ДУА на фильтре, ожидающий восстановления: уран, кг. */
    private double cakeKg;
    private double lastX;
    private Result last = Result.IDLE;

    public record Result(double feedKgU, double powderKgU, double ammoniaKg, double hydrogenKg, double waterKg, boolean limitedBySafety,
            boolean refused) {
        static final Result IDLE = new Result(0, 0, 0, 0, 0, false, false);
    }

    public MaterialBalance balance() {
        return balance;
    }

    public DryConversion reduction() {
        return reduce;
    }

    public double cakeKg() {
        return cakeKg;
    }

    public Result last() {
        return last;
    }

    /**
     * Шаг на {@code dtS}: UF₆ доступно {@code availableKgU} урана с обогащением {@code x}; аммиака, водорода и воды —
     * {@code nh3Kg}, {@code h2Kg}, {@code waterKg}. Возвращает израсходованное и порошок UO₂ (кг урана).
     */
    public Result step(double dtS, double availableKgU, double x, double nh3Kg, double h2Kg, double waterKg) {
        if (x > MAX_ENRICHMENT + 1e-12) {
            last = new Result(0, 0, 0, 0, 0, false, true);
            return last;
        }
        double feed = Math.min(RATED_KG_U_S * dtS, Math.max(0, availableKgU));
        feed = Math.min(feed, waterKg / WATER_KG_PER_KG_U);
        // безопасная масса: запас U-235 в аппаратах плюс новое питание
        boolean limited = false;
        double room = Criticality.safeU235Kg(x) - balance.holdupU235Kg();
        if (x > 0 && feed * x > room) {
            feed = Math.max(0, room / x);
            limited = true;
        }
        double nh3 = Math.min(nh3Kg, feed * AduPrecipitation.NH3_KG_PER_KG_U);
        double cake = 0;
        if (feed > 0) {
            balance.in(feed, x);
            cake = adu.step(feed, nh3);
            balance.loss(feed - cake, x);
            cakeKg += cake;
        }
        lastX = cakeKg > 0 && feed > 0 ? x : lastX;
        double toReduce = Math.min(cakeKg, reduce.capacityKg(dtS, h2Kg, Double.POSITIVE_INFINITY));
        double powder = reduce.process(toReduce, dtS);
        cakeKg -= toReduce;
        if (toReduce > 0) {
            balance.loss(toReduce - powder, lastX);
            balance.out(powder, lastX);
        }
        double h2 = toReduce * DryConversion.Step.REDUCE.reagentWithExcess();
        last = new Result(feed, powder, nh3, h2, feed * WATER_KG_PER_KG_U, limited, false);
        return last;
    }

    public void save(StateWriter out) {
        out.putDouble("cake", cakeKg);
        out.putDouble("x", lastX);
        balance.save(out);
        out.beginObject("reduce");
        reduce.save(out);
        out.endObject();
    }

    public void load(StateReader in) {
        cakeKg = Math.max(0, in.optDouble("cake", 0));
        lastX = in.optDouble("x", 0);
        balance.load(in);
        if (in.has("reduce")) {
            in.beginObject("reduce");
            reduce.load(in);
            in.endObject();
        }
    }
}
