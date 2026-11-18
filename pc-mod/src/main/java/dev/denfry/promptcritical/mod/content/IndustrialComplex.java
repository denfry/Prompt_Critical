package dev.denfry.promptcritical.mod.content;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Достижение {@code industrial_complex} (11 §11.2.2): в одном измерении одновременно работают домна, НПЗ, ГПЗ и
 * реактор на мощности выше 50 %. Каждая установка отмечается здесь, пока работает; когда все четыре отметились в
 * пределах {@link #WINDOW_TICKS}, событие получают игроки у каждой из них. Только серверный поток.
 */
public final class IndustrialComplex {
    public enum Part { BLAST_FURNACE, REFINERY, GAS_PLANT, REACTOR }

    /** Окно одновременности, тики: установки отмечаются каждый тик, 10 с хватает с запасом на выгрузку чанка. */
    static final long WINDOW_TICKS = 200;
    /** Реактор считается за часть комплекса выше этой доли номинала. */
    public static final double REACTOR_POWER = 0.5;

    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    private static final class State {
        final long[] last = new long[Part.values().length];
        final BlockPos[] pos = new BlockPos[Part.values().length];
        long fired = Long.MIN_VALUE;

        State() {
            java.util.Arrays.fill(last, Long.MIN_VALUE / 2);
        }
    }

    private IndustrialComplex() {
    }

    /** Установка {@code part} у {@code pos} сейчас работает. */
    public static void running(ServerLevel level, BlockPos pos, Part part) {
        State s = STATES.computeIfAbsent(level, k -> new State());
        long now = level.getGameTime();
        s.last[part.ordinal()] = now;
        s.pos[part.ordinal()] = pos.immutable();
        if (now - s.fired < WINDOW_TICKS) return;
        for (long t : s.last) if (now - t > WINDOW_TICKS) return;
        s.fired = now;
        for (BlockPos p : s.pos) SimAchievements.nearby(level, p, SimAchievements.INDUSTRIAL_COMPLEX, "parts", Part.values().length);
    }
}
