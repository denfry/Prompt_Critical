package dev.denfry.promptcritical.sim.nonferrous;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Декомпозёр {@code alumina_precipitator} (08 §8.8.1): алюминатный раствор на затравке гидроксида при 50–70 °C,
 * 30–60 ч физ. Бак — проточный с временем пребывания {@value #RESIDENCE_H} ч (нижняя граница §8.8.1): за шаг
 * перерабатывается доля {@code dt/τ} содержимого. Степень разложения пересыщения — {@code 1 − exp(−τ/τₖ)} при
 * τₖ = {@value #KINETIC_H} ч [BALANCE]; вне окна температур выход падает линейно до половины в 20 К от края: ниже —
 * медленнее кристаллизация, выше — растёт растворимость (Лайнер, гл. «Декомпозиция»). Отработанный раствор
 * (αк {@value BayerProcess#MODULUS_SPENT}) возвращается щёлочью на выщелачивание; непрореагировавший глинозём
 * остаётся в нём — потеря с мелкой фракцией.
 */
public final class AluminaPrecipitator {
    public static final double RESIDENCE_H = 30, KINETIC_H = 10;
    public static final double LOW_K = 323.15, HIGH_K = 343.15, FALLOFF_K = 20;
    public static final double MIN_K = 293.15, MAX_K = 373.15;

    private double tempK = 333.15;
    private double lastLiquorLs, lastHydroxideKgS, lastAl2o3KgS;
    private double totalHydroxideKg;

    public double tempK() {
        return tempK;
    }

    public void setTempK(double k) {
        tempK = Math.clamp(k, MIN_K, MAX_K);
    }

    /** Доля пересыщения, которая выпадает на затравку за время пребывания. */
    public static double yieldAt(double tempK) {
        double kinetic = 1 - Math.exp(-RESIDENCE_H / KINETIC_H);
        double off = tempK < LOW_K ? LOW_K - tempK : tempK > HIGH_K ? tempK - HIGH_K : 0;
        return kinetic * Math.max(0.5, 1 - 0.5 * off / FALLOFF_K);
    }

    /** Шаг на {@code dtS} физ. секунд при {@code holdupL} литрах раствора в баке. Возвращает переработанные литры. */
    public double step(double dtS, double holdupL) {
        double litres = Math.max(0, holdupL) * Math.min(1, dtS / (RESIDENCE_H * 3600));
        double al2o3 = litres * BayerProcess.NET_AL2O3_KG_L * yieldAt(tempK);
        lastLiquorLs = dtS > 0 ? litres / dtS : 0;
        lastAl2o3KgS = dtS > 0 ? al2o3 / dtS : 0;
        lastHydroxideKgS = lastAl2o3KgS * BayerProcess.HYDROXIDE_PER_AL2O3;
        totalHydroxideKg += lastHydroxideKgS * dtS;
        return litres;
    }

    public double lastLiquorLs() {
        return lastLiquorLs;
    }

    public double lastHydroxideKgS() {
        return lastHydroxideKgS;
    }

    public double lastAl2o3KgS() {
        return lastAl2o3KgS;
    }

    public double totalHydroxideKg() {
        return totalHydroxideKg;
    }

    public void save(StateWriter out) {
        out.putDouble("t", tempK);
        out.putDouble("made", totalHydroxideKg);
    }

    public void load(StateReader in) {
        setTempK(in.optDouble("t", 333.15));
        totalHydroxideKg = in.optDouble("made", 0);
    }
}
