package dev.denfry.promptcritical.sim.methanol;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.nitrogen.ReformerTrain;
import dev.denfry.promptcritical.sim.nitrogen.Reforming;
import dev.denfry.promptcritical.sim.nitrogen.Species;
import dev.denfry.promptcritical.sim.thermo.if97.If97;

/**
 * Синтез-газ для метанола: только трубчатая печь паровой конверсии ({@link Reforming#primary}, тот же никелевый
 * катализатор и та же загрузка, что у {@link ReformerTrain}), без вторичного риформинга на воздухе и без конверсии CO —
 * азот метанолу не нужен, а CO нужен. Газ охлаждают до 40 °C, избыточный пар конденсируют. Давление печи 2 МПа,
 * выход 850–880 °C, S/C 2,5–3,5 (Supp, «How to Produce Methanol from Coal», 1990; Hansen, Højlund Nielsen, «Methanol
 * Synthesis», Handbook of Heterogeneous Catalysis, 2008). Такой газ даёт модуль S ≈ 2,8–3,0: водорода больше, чем
 * нужно, его избыток уходит с продувкой топливом печи.
 *
 * <p>Расчёт печи тяжёл: решение на 1 моль/с метана кэшируется по режиму и масштабируется на фактический расход.
 */
public final class MethanolReformer {
    public static final double PRESSURE_BAR = 20, MIX_INLET_K = 773.15, COOLER_K = 313.15;
    /** Номинальный расход метана, моль/с (≈ 800 нм³/ч, ≈ 25 т/сут метанола) [BALANCE: опытный агрегат]. */
    public static final double RATED_CH4_MOL_S = ReformerTrain.RATED_CH4_MOL_S;
    public static final double WATER_LATENT_J_MOL = ReformerTrain.WATER_LATENT_J_MOL;

    private double steamToCarbon = 3.0, outK = 1133.15, activity, rateFraction = 1;
    private Solution cached;
    private String cacheKey = "";

    /**
     * Решение на 1 моль/с метана: газ после холодильника (с паром по насыщению), тепло через стенки труб, тепло
     * охлаждения газа до 40 °C с конденсацией (котёл-утилизатор и подогреватели), модуль S, метан в сухом газе.
     */
    public record Solution(double[] syngas, double dutyW, double wasteHeatW, double sn, double ch4Dry) {
    }

    public double steamToCarbon() {
        return steamToCarbon;
    }

    public void setSteamToCarbon(double v) {
        steamToCarbon = Math.max(2.0, Math.min(5.0, v));
    }

    public double outK() {
        return outK;
    }

    public void setOutK(double k) {
        outK = Math.max(1073.15, Math.min(1153.15, k));
    }

    public double activity() {
        return activity;
    }

    public void loadCatalyst() {
        activity = 1;
    }

    public double rateFraction() {
        return rateFraction;
    }

    public void setRateFraction(double f) {
        rateFraction = Math.max(0.1, Math.min(1.2, f));
    }

    public Solution solution() {
        String key = Math.round(steamToCarbon * 100) + ":" + Math.round(outK) + ":" + Math.round(activity * 50) + ":"
                + Math.round(rateFraction * 20);
        if (cached == null || !key.equals(cacheKey)) {
            cached = solve();
            cacheKey = key;
        }
        return cached;
    }

    private Solution solve() {
        double ch4 = RATED_CH4_MOL_S * rateFraction;
        double[] feed = Species.flow();
        feed[Species.CH4.ordinal()] = ch4;
        feed[Species.N2.ordinal()] = ch4 * 0.01 / 0.99;
        feed[Species.H2O.ordinal()] = steamToCarbon * ch4;
        Reforming.Result p = Reforming.primary(feed, PRESSURE_BAR, MIX_INLET_K, outK, ReformerTrain.CATALYST_KG, activity);
        double[] n = p.n().clone();
        int h2o = Species.H2O.ordinal();
        double dry = Species.total(n) - n[h2o];
        double ySat = If97.psat(COOLER_K) / 1e5 / (PRESSURE_BAR - 1);
        double vapour = Math.min(n[h2o], ySat / (1 - ySat) * dry);
        double condensed = n[h2o] - vapour;
        double waste = Species.enthalpy(n, outK) - Species.enthalpy(n, COOLER_K) + condensed * WATER_LATENT_J_MOL;
        n[h2o] = vapour;
        double inv = 1 / ch4;
        for (int i = 0; i < n.length; i++) n[i] *= inv;
        return new Solution(n, p.dutyW() * inv, waste * inv, MethanolSynthesis.stoichiometricNumber(n), p.dry(Species.CH4));
    }

    /** Топливо печи на 1 моль/с метана сырья, Вт. */
    public double fuelPerMolW() {
        return solution().dutyW() / ReformerTrain.RADIANT_EFFICIENCY;
    }

    /**
     * Шаг по игровому времени при сырьевом метане {@code ch4MolS}: при S/C ниже {@link ReformerTrain#MIN_STEAM_TO_CARBON}
     * на никеле растёт углерод. Возвращает поток синтез-газа, моль/с.
     */
    public double[] step(double gameDtS, double ch4MolS) {
        if (ch4MolS > 0 && steamToCarbon < ReformerTrain.MIN_STEAM_TO_CARBON && activity > 0) {
            double loss = ReformerTrain.CARBON_LOSS_PER_H * (ReformerTrain.MIN_STEAM_TO_CARBON - steamToCarbon) / 0.5 * gameDtS / 3600;
            activity = Math.max(0, activity - loss);
        }
        double[] out = solution().syngas().clone();
        for (int i = 0; i < out.length; i++) out[i] *= ch4MolS;
        return out;
    }

    public void save(StateWriter out) {
        out.putDouble("sc", steamToCarbon);
        out.putDouble("t_out", outK);
        out.putDouble("ni", activity);
        out.putDouble("rate", rateFraction);
    }

    public void load(StateReader in) {
        setSteamToCarbon(in.optDouble("sc", 3.0));
        setOutK(in.optDouble("t_out", 1133.15));
        activity = Math.max(0, Math.min(1, in.optDouble("ni", 0)));
        setRateFraction(in.optDouble("rate", 1));
        cached = null;
    }
}
