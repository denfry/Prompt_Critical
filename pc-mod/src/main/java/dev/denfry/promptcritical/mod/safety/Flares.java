package dev.denfry.promptcritical.mod.safety;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Сбросы на факельные оголовки за секунду (13 §13.4.3). Сепаратор, которому некуда девать газ, отдаёт его на
 * {@code flare_tip}; если у оголовка есть щит розжига ({@link FlareIgnitionPanelBlockEntity}), горение, выбросы, свет
 * и излучение считает щит, иначе — сам сепаратор, как раньше (факел без системы: дежурные горелки «всегда горят»).
 */
public final class Flares {
    private static final Map<Level, Map<Long, Double>> OFFERED = new WeakHashMap<>();
    private static final Map<Level, Map<Long, Long>> MANAGED = new WeakHashMap<>();

    private Flares() {
    }

    /** Оголовок обслуживает щит розжига (отметка не старше 2 с). */
    public static boolean managed(Level level, BlockPos tip) {
        Map<Long, Long> m = MANAGED.get(level);
        Long until = m == null ? null : m.get(tip.asLong());
        return until != null && until >= level.getGameTime();
    }

    static void manage(Level level, BlockPos tip) {
        MANAGED.computeIfAbsent(level, k -> new HashMap<>()).put(tip.asLong(), level.getGameTime() + 40);
    }

    /** Сбросить на оголовок {@code nm3s} нм³/с газа (за текущую секунду). */
    public static void offer(Level level, BlockPos tip, double nm3s) {
        if (nm3s > 0) OFFERED.computeIfAbsent(level, k -> new HashMap<>()).merge(tip.asLong(), nm3s, Double::sum);
    }

    /** Забрать накопленный за секунду сброс на оголовок, нм³/с. */
    static double take(Level level, BlockPos tip) {
        Map<Long, Double> m = OFFERED.get(level);
        Double v = m == null ? null : m.remove(tip.asLong());
        return v == null ? 0 : v;
    }
}
