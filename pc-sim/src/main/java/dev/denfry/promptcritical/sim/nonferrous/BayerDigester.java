package dev.denfry.promptcritical.sim.nonferrous;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Автоклав выщелачивания {@code bayer_digester} со сгустителем красного шлама (08 §8.8.1). Оборотная щёлочь
 * ({@value BayerProcess#NAOH_G_L} г/л NaOH) прокачивается через автоклав и снимает с боксита
 * {@link BayerProcess#NET_AL2O3_KG_L} кг Al₂O₃ на литр; часть щёлочи уходит в шлам с кремнезёмом. Температуру держит
 * острый пар (в игре — пар своей котельной на топливном газе): нагрев пульпы до температуры автоклава с рекуперацией
 * тепла самоиспарителей {@value #HEAT_RECOVERY} [BALANCE]. Шлам после промывки в сгустителе — пульпа
 * {@value #MUD_SOLIDS} твёрдого [BALANCE: сгущённый продукт 35–50 %].
 *
 * <p>Процесс термохимический — состояние в SLOW (08 §8.0.3): шаг принимает физические секунды.
 */
public final class BayerDigester {
    /** Прокачка оборотного раствора, л/с физ. [BALANCE]. */
    public static final double LIQUOR_LS = 0.2;
    /** Теплоёмкость пульпы, Дж/(кг·К), плотность раствора, кг/л; температура раствора на входе, К. */
    public static final double CP_J_KGK = 3300, LIQUOR_KG_L = 1.30, FEED_K = 343.15;
    public static final double HEAT_RECOVERY = 0.5, MUD_SOLIDS = 0.40, MUD_KG_L = 1.35;
    /** Пределы уставки температуры, К. */
    public static final double MIN_K = 373.15, MAX_K = 543.15;

    private double tempK = BayerProcess.GIBBSITE_LOW_K + 5;
    private double lastLiquorLs, lastBauxiteKgS, lastNaohLossKgS, lastMudKgS, lastHeatW, lastAl2o3KgS;
    private double totalAl2o3Kg, totalBauxiteKg;

    public double tempK() {
        return tempK;
    }

    public void setTempK(double k) {
        tempK = Math.clamp(k, MIN_K, MAX_K);
    }

    /**
     * Шаг: сколько раствора можно прокачать (по щёлочи и месту для продукта, л), сколько боксита в загрузке (кг) и его
     * содержание Al₂O₃. Возвращает прокачанный раствор, л; остальное — в {@code last*}.
     */
    public double step(double dtS, double liquorAvailL, double bauxiteAvailKg, double al2o3) {
        double perKg = BayerProcess.netAl2o3PerKg(al2o3, tempK);
        double litres = Math.max(0, Math.min(LIQUOR_LS * dtS, liquorAvailL));
        if (perKg <= 0) litres = 0;
        double bauxite = litres > 0 ? litres * BayerProcess.NET_AL2O3_KG_L / perKg : 0;
        if (bauxite > bauxiteAvailKg) {
            bauxite = Math.max(0, bauxiteAvailKg);
            litres = bauxite * perKg / BayerProcess.NET_AL2O3_KG_L;
        }
        lastLiquorLs = dtS > 0 ? litres / dtS : 0;
        lastBauxiteKgS = dtS > 0 ? bauxite / dtS : 0;
        lastAl2o3KgS = lastBauxiteKgS * perKg;
        lastNaohLossKgS = lastBauxiteKgS * BayerProcess.naohLossPerKg();
        lastMudKgS = lastBauxiteKgS * BayerProcess.redMudPerKg(al2o3, tempK);
        lastHeatW = lastLiquorLs * LIQUOR_KG_L * CP_J_KGK * Math.max(0, tempK - FEED_K) * (1 - HEAT_RECOVERY);
        totalAl2o3Kg += lastAl2o3KgS * dtS;
        totalBauxiteKg += bauxite;
        return litres;
    }

    /** Литры шламовой пульпы на кг сухого шлама. */
    public static double mudLitresPerKg() {
        return 1 / (MUD_SOLIDS * MUD_KG_L);
    }

    public double lastLiquorLs() {
        return lastLiquorLs;
    }

    public double lastBauxiteKgS() {
        return lastBauxiteKgS;
    }

    public double lastAl2o3KgS() {
        return lastAl2o3KgS;
    }

    public double lastNaohLossKgS() {
        return lastNaohLossKgS;
    }

    public double lastMudKgS() {
        return lastMudKgS;
    }

    public double lastHeatW() {
        return lastHeatW;
    }

    public double totalAl2o3Kg() {
        return totalAl2o3Kg;
    }

    public double totalBauxiteKg() {
        return totalBauxiteKg;
    }

    public void save(StateWriter out) {
        out.putDouble("t", tempK);
        out.putDouble("al2o3", totalAl2o3Kg);
        out.putDouble("bauxite", totalBauxiteKg);
    }

    public void load(StateReader in) {
        setTempK(in.optDouble("t", BayerProcess.GIBBSITE_LOW_K + 5));
        totalAl2o3Kg = in.optDouble("al2o3", 0);
        totalBauxiteKg = in.optDouble("bauxite", 0);
    }
}
