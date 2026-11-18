package dev.denfry.promptcritical.sim.nitrogen;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Узел получения азотоводородной смеси до очистки: трубчатая печь (первичный риформинг природного газа с паром на
 * никелевом катализаторе, 3,2 МПа), шахтный реактор вторичного риформинга на воздухе — его расход держит регулятор
 * соотношения (H₂ + CO)/N₂ (после конверсии CO и метанирования это H₂:N₂ ≈ 3), котёл-утилизатор и двухступенчатая
 * конверсия CO. Схема агрегата АМ-1360 и его аналогов (Kellogg): «Справочник азотчика», т. 1, 1986, гл. 3–4; Appl,
 * «Ammonia», 1999, §4.
 *
 * <p>Природный газ — товарный газ газопереработки, в расчёте — метан с 1 % азота. Расход катализатора трубчатой печи —
 * 0,7 кг на нм³/ч газа, как на промышленных печах (20 м³ на 1000 т/сут NH₃). Пар ниже S/C = 2,5 даёт углерод на
 * никеле (нитевидный углерод, Rostrup-Nielsen, «Catalytic Steam Reforming», 1984, гл. 6): активность падает
 * пропорционально недостатку пара, по игровому времени. Расчёт равновесий тяжёл, поэтому решение пересчитывается
 * при смене режима и затем масштабируется на фактический расход газа.
 */
public final class ReformerTrain {
    /** Давление на выходе печи, бар; вход в печь, воздух вторичного риформинга, К. */
    public static final double PRESSURE_BAR = 32, MIX_INLET_K = 773.15, AIR_K = 773.15;
    /** Входы конверторов CO: ВТК на Fe–Cr, НТК на Cu–Zn, К; приближение к равновесию, К. */
    public static final double HTS_IN_K = 643.15, LTS_IN_K = 478.15, SHIFT_APPROACH_K = 8, SECONDARY_APPROACH_K = 10;
    /** Номинальный расход метана, моль/с (≈ 800 нм³/ч; ≈ 30 т/сут NH₃) [BALANCE: опытный агрегат]. */
    public static final double RATED_CH4_MOL_S = 10;
    public static final double CATALYST_KG = 0.7 * RATED_CH4_MOL_S / Species.MOL_PER_NM3 * 3600;
    /** КПД радиантной секции: остальное тепло дымовых газов идёт на подогрев сырья, воздуха и пар. */
    public static final double RADIANT_EFFICIENCY = 0.55;
    public static final double MIN_STEAM_TO_CARBON = 2.5, CARBON_LOSS_PER_H = 0.05;
    /** Холодильник после НТК: газ охлаждают до 40 °C, конденсат воды отделяют. */
    public static final double COOLER_K = 313.15, WATER_LATENT_J_MOL = 43.99e3;

    private double steamToCarbon = 3.5, primaryOutK = 1073.15, ratio = 3.10;
    private double primaryActivity, rateFraction = 1;
    private boolean hts, lts;
    // кэш решения на 1 моль/с метана
    private Solution cached;
    private String cacheKey = "";

    /** Решение узла на 1 моль/с метана. */
    public record Solution(double[] shifted, double airMolS, double primaryCh4Dry, double secondaryK, double secondaryCh4Dry,
            double htsOutK, double htsCoDry, double ltsOutK, double ltsCoDry, double primaryDutyW, double processHeatW,
            double steamMolS, double ratioOut) {
    }

    public double steamToCarbon() {
        return steamToCarbon;
    }

    public void setSteamToCarbon(double v) {
        steamToCarbon = Math.max(2.0, Math.min(5.0, v));
    }

    public double primaryOutK() {
        return primaryOutK;
    }

    public void setPrimaryOutK(double k) {
        primaryOutK = Math.max(973.15, Math.min(1123.15, k));
    }

    public double ratio() {
        return ratio;
    }

    public void setRatio(double r) {
        ratio = Math.max(2.5, Math.min(3.6, r));
    }

    public double primaryActivity() {
        return primaryActivity;
    }

    /** Загрузка свежего никелевого катализатора в трубы печи. */
    public void loadPrimaryCatalyst() {
        primaryActivity = 1;
    }

    public boolean hts() {
        return hts;
    }

    public boolean lts() {
        return lts;
    }

    public void loadHts() {
        hts = true;
    }

    public void loadLts() {
        lts = true;
    }

    public void setRateFraction(double f) {
        rateFraction = Math.max(0.1, Math.min(1.2, f));
    }

    public double rateFraction() {
        return rateFraction;
    }

    /** Решение на 1 моль/с метана при текущем режиме (кэшируется). */
    public Solution solution() {
        String key = Math.round(steamToCarbon * 100) + ":" + Math.round(primaryOutK) + ":" + Math.round(ratio * 100) + ":"
                + Math.round(primaryActivity * 50) + ":" + hts + ":" + lts + ":" + Math.round(rateFraction * 20);
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
        Reforming.Result p = Reforming.primary(feed, PRESSURE_BAR, MIX_INLET_K, primaryOutK, CATALYST_KG, primaryActivity);
        double air = Reforming.airForRatio(p.n(), primaryOutK, AIR_K, PRESSURE_BAR - 1, SECONDARY_APPROACH_K, ratio);
        Reforming.Result s = Reforming.secondary(p.n(), primaryOutK, air, AIR_K, PRESSURE_BAR - 1, SECONDARY_APPROACH_K);
        Reforming.Result h = Reforming.shift(s.n(), HTS_IN_K, SHIFT_APPROACH_K, hts);
        Reforming.Result l = Reforming.shift(h.n(), LTS_IN_K, SHIFT_APPROACH_K, lts);
        // технологическое тепло: охлаждение газа после вторичного риформинга, между ступенями и после НТК до 40 °C с
        // конденсацией избыточного пара
        double heat = Species.enthalpy(s.n(), s.tK()) - Species.enthalpy(s.n(), HTS_IN_K)
                + Species.enthalpy(h.n(), h.tK()) - Species.enthalpy(h.n(), LTS_IN_K)
                + Species.enthalpy(l.n(), l.tK()) - Species.enthalpy(l.n(), COOLER_K)
                + l.n()[Species.H2O.ordinal()] * WATER_LATENT_J_MOL;
        double[] dry = l.n().clone();
        dry[Species.H2O.ordinal()] = 0;
        double inv = 1 / ch4;
        for (int i = 0; i < dry.length; i++) dry[i] *= inv;
        return new Solution(dry, air * inv, p.dry(Species.CH4), s.tK(), s.dry(Species.CH4), h.tK(), h.dry(Species.CO), l.tK(),
                l.dry(Species.CO), p.dutyW() * inv, heat * inv, feed[Species.H2O.ordinal()] * inv,
                Reforming.hydrogenToNitrogen(l.n()));
    }

    /** Топливо печи на 1 моль/с метана сырья, Вт. */
    public double fuelPerMolW() {
        return solution().primaryDutyW() / RADIANT_EFFICIENCY;
    }

    /**
     * Шаг по игровому времени {@code gameDtS} при сырьевом метане {@code ch4MolS}: углерод на катализаторе при нехватке
     * пара. Возвращает поток газа после конверсии CO (сухой), моль/с.
     */
    public double[] step(double gameDtS, double ch4MolS) {
        if (ch4MolS > 0 && steamToCarbon < MIN_STEAM_TO_CARBON && primaryActivity > 0) {
            double loss = CARBON_LOSS_PER_H * (MIN_STEAM_TO_CARBON - steamToCarbon) / 0.5 * gameDtS / 3600;
            primaryActivity = Math.max(0, primaryActivity - loss);
        }
        double[] out = solution().shifted().clone();
        for (int i = 0; i < out.length; i++) out[i] *= ch4MolS;
        return out;
    }

    public void save(StateWriter out) {
        out.putDouble("sc", steamToCarbon);
        out.putDouble("t_out", primaryOutK);
        out.putDouble("ratio", ratio);
        out.putDouble("ni", primaryActivity);
        out.putBoolean("hts", hts);
        out.putBoolean("lts", lts);
        out.putDouble("rate", rateFraction);
    }

    public void load(StateReader in) {
        setSteamToCarbon(in.optDouble("sc", steamToCarbon));
        setPrimaryOutK(in.optDouble("t_out", primaryOutK));
        setRatio(in.optDouble("ratio", ratio));
        primaryActivity = Math.max(0, Math.min(1, in.optDouble("ni", 0)));
        hts = in.has("hts") && in.getBoolean("hts");
        lts = in.has("lts") && in.getBoolean("lts");
        setRateFraction(in.optDouble("rate", 1));
        cached = null;
    }
}
