package dev.denfry.promptcritical.mod.environment;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Аппарат очистки дымовых газов рядом с источником (14 §14.2): электрофильтр снимает пыль, сероочистка — SO₂.
 * Аппараты вписываются в реестр уровня при загрузке BE; {@link AirQualityService#emit} спрашивает аппараты в
 * {@value #REACH} блоках от устья трубы или топки. Газоход в мире не прокладывается: аппарат «стоит на тракте», если он
 * рядом с топкой.
 */
public interface FlueGasCleaner {
    int REACH = 8;

    /**
     * Обработать выбросы источника, кг/с: SO₂, NOx, PM (массив меняется на месте).
     *
     * @param flowM3S расход газа при рабочих условиях
     */
    void clean(double[] qKgS, double flowM3S, double dtS);

    Map<ServerLevel, Set<BlockPos>> REGISTRY = new WeakHashMap<>();

    static void add(ServerLevel level, BlockPos pos) {
        REGISTRY.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> s = REGISTRY.get(level);
        if (s != null) s.remove(pos);
    }

    /** Применить все аппараты рядом с {@code near}. */
    static void apply(ServerLevel level, BlockPos near, double[] q, double flowM3S, double dtS) {
        Set<BlockPos> s = REGISTRY.get(level);
        if (s == null) return;
        for (BlockPos p : s) {
            if (Math.abs(p.getX() - near.getX()) > REACH || Math.abs(p.getZ() - near.getZ()) > REACH
                    || Math.abs(p.getY() - near.getY()) > 4 * REACH) continue;
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof FlueGasCleaner c) c.clean(q, flowM3S, dtS);
        }
    }
}
