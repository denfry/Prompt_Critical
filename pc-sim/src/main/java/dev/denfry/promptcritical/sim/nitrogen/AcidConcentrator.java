package dev.denfry.promptcritical.sim.nitrogen;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.refining.Fractionation;

/**
 * Концентрирование азотной кислоты экстрактивной ректификацией с плавом нитрата магния. Вода и HNO₃ образуют азеотроп
 * 68,4 %, простой перегонкой крепче не получить. Плав Mg(NO₃)₂ (72 %) связывает воду, азеотроп исчезает, и колонна
 * даёт сверху 97–99 % HNO₃; разбавленный плав упаривают и возвращают (Атрощенко, Каргин, «Технология азотной
 * кислоты», 1970, гл. 9; Thiemann et al., «Nitric Acid», Ullmann's, §2.3).
 *
 * <p>Колонна — {@link Fractionation#effectiveStagesForAlpha}: 10 теоретических тарелок, флегмовое число и
 * относительная летучесть HNO₃ к воде, растущая с кратностью плава s (кг плава на кг кислоты, рабочая 4–5):
 * α = 1 + 2·(1 − e^(−s/3)). Отношение воды к кислоте в верхнем продукте — отношение в питании, делённое на
 * α^N_эфф. Тепло — упаривание отнятой воды из плава (2,26 МДж/кг с КПД выпарного аппарата 0,85) и кипятильник колонны
 * (теплота испарения HNO₃ 0,48 МДж/кг на (1 + R) кг пара на кг продукта). Товарная кислота — от 97 %.
 */
public final class AcidConcentrator {
    public static final int STAGES = 10;
    /** Слабая кислота трубопровода — 56 %; товарная концентрированная — 98 %. */
    public static final double WEAK = 0.56, PRODUCT = 0.98, MIN_PRODUCT = 0.97, RECOVERY = 0.985;
    public static final double WATER_LATENT_J_KG = 2.26e6, HNO3_LATENT_J_KG = 0.48e6, EVAPORATOR_EFFICIENCY = 0.85;

    private double saltRatio = 4.5, reflux = 1.0;

    /** Итог: верхний продукт (массовая доля HNO₃), выход HNO₃ сверху кг/с, тепло Вт, в норме ли продукт. */
    public record Result(double topStrength, double hno3KgS, double heatW, boolean onSpec) {
    }

    public double saltRatio() {
        return saltRatio;
    }

    public void setSaltRatio(double s) {
        saltRatio = Math.max(1, Math.min(7, s));
    }

    public double reflux() {
        return reflux;
    }

    public void setReflux(double r) {
        reflux = Math.max(0.2, Math.min(3, r));
    }

    public static double alpha(double saltRatio) {
        return 1 + 2.0 * (1 - Math.exp(-saltRatio / 3));
    }

    /** Колонна на питании {@code weakKgS} кислоты крепостью {@link #WEAK}. */
    public Result run(double weakKgS) {
        double alpha = alpha(saltRatio);
        double nEff = Fractionation.effectiveStagesForAlpha(STAGES, reflux, alpha);
        double feedRatio = (1 - WEAK) / WEAK;
        double topRatio = feedRatio / Math.pow(alpha, nEff);
        double top = 1 / (1 + topRatio);
        double hno3 = weakKgS * WEAK * RECOVERY;
        double water = weakKgS * (1 - WEAK) - hno3 * topRatio;
        double heat = Math.max(0, water) * WATER_LATENT_J_KG / EVAPORATOR_EFFICIENCY
                + hno3 / top * (1 + reflux) * HNO3_LATENT_J_KG;
        return new Result(top, hno3, heat, top >= MIN_PRODUCT);
    }

    public void save(StateWriter out) {
        out.putDouble("salt", saltRatio);
        out.putDouble("reflux", reflux);
    }

    public void load(StateReader in) {
        setSaltRatio(in.optDouble("salt", 4.5));
        setReflux(in.optDouble("reflux", 1.0));
    }
}
