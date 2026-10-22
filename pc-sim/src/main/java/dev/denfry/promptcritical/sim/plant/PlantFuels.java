package dev.denfry.promptcritical.sim.plant;

import dev.denfry.promptcritical.sim.combustion.FuelPhase;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.mining.VeinField;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Твёрдое топливо котла I0 по ключу команды {@code ChargeFuel} (07 §7.9.1: уголь на решётке, дрова). Уголь — по
 * типичному анализу марки (08 §8.2.1); уголь с компонентом {@code coal_assay} — ключ {@link #assayKey} с квантованным
 * анализом.
 */
public final class PlantFuels {
    /** Ванильный уголь: Г, A^d 21 %, W 6 % (как эталонная котельная 07 §7.9.4). */
    public static final String COAL = "coal";
    /** Рядовой уголь шахты: Г, A^d 30 %, W 8 %. */
    public static final String RAW_COAL = "raw_coal";
    /** Мытый уголь (концентрат отсадки): Г, A^d 9 %, W 9 %. */
    public static final String WASHED_COAL = "washed_coal";
    /** Промпродукт: A^d 40 %. */
    public static final String MIDDLINGS = "coal_middlings";
    /** Древесный уголь. */
    public static final String CHARCOAL = "charcoal";
    /** Дрова воздушной сушки. */
    public static final String WOOD = "wood";
    /** Угольная пыль котла I1 (помол и сушка ванильного угля): Г, A^d 21 %, W 1,5 %. */
    public static final String COAL_DUST = "coal_dust";

    private static final Map<String, FuelSpec> FUELS = Map.of(
            COAL, CoalAssay.typical(0.37, 0.213, 0.06, 0.016, 10).toFuel(),
            RAW_COAL, CoalAssay.typical(0.37, 0.30, 0.08, 0.02, 10).toFuel(),
            WASHED_COAL, CoalAssay.typical(0.37, 0.09, 0.09, 0.012, 10).toFuel(),
            MIDDLINGS, CoalAssay.typical(0.37, 0.40, 0.08, 0.025, 10).toFuel(),
            CHARCOAL, FuelSpec.ofElemental(CHARCOAL, FuelPhase.SOLID, 0.84, 0.03, 0.06, 0.005, 0, 0.025, 0.04, 0, 0.1),
            WOOD, FuelSpec.ofElemental(WOOD, FuelPhase.SOLID, 0.40, 0.048, 0.337, 0.003, 0, 0.012, 0.20, 0, 0.1),
            COAL_DUST, CoalAssay.typical(0.37, 0.213, 0.015, 0.016, 10).toFuel());

    private static final String ASSAY_PREFIX = "coal_assay:";
    private static final Map<String, FuelSpec> ASSAYED = new HashMap<>();

    private PlantFuels() {
    }

    /** Ключ угля с анализом: V^daf, A^d, W^r в %, S_t^d в ‰ (квантование компонента {@code coal_assay}). */
    public static String assayKey(int volatilePct, int ashPct, int moisturePct, int sulfurPm) {
        return String.format(Locale.ROOT, "%s%d:%d:%d:%d", ASSAY_PREFIX, volatilePct, ashPct, moisturePct, sulfurPm);
    }

    /** Топливо по ключу; {@code null}, если ключ неизвестен или анализ невозможен. */
    public static FuelSpec get(String key) {
        if (key == null) return null;
        FuelSpec f = FUELS.get(key);
        if (f != null || !key.startsWith(ASSAY_PREFIX)) return f;
        synchronized (ASSAYED) {
            f = ASSAYED.get(key);
            if (f == null) {
                f = parseAssay(key.substring(ASSAY_PREFIX.length()));
                if (f != null) ASSAYED.put(key, f);
            }
        }
        return f;
    }

    private static FuelSpec parseAssay(String s) {
        String[] p = s.split(":");
        if (p.length != 4) return null;
        try {
            double v = Integer.parseInt(p[0]) / 100.0, a = Integer.parseInt(p[1]) / 100.0;
            double w = Integer.parseInt(p[2]) / 100.0, sulfur = Integer.parseInt(p[3]) / 1000.0;
            if (!(v > 0 && v < 1 && a >= 0 && a < 0.8 && w >= 0 && w < 0.6 && sulfur >= 0 && sulfur < 0.1)) return null;
            return CoalAssay.typical(v, a, w, sulfur, VeinField.plasticLayerMm(v)).toFuel();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
