package dev.denfry.promptcritical.mod.ops;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Пожар резервуара (13 §13.5.4, класс B): огонь на крыше резервуара Create с нефтепродуктом не гаснет сам, как
 * обычный огонь на негорючем блоке, — он держится, пока в резервуаре есть горючее и пока его не потушили пеной, и
 * выжигает содержимое с массовой скоростью выгорания m″ ≈ {@value #BURN_KG_M2_S} кг/(м²·с) с каждой горящей клетки
 * (нефть и дизтопливо 0,045, бензин 0,055 — Babrauskas, SFPE Handbook [сверить]). Вода на него не действует, а
 * разбрызгивает горящую жидкость ({@link FireService#apply}).
 */
public final class TankFires {
    public static final double BURN_KG_M2_S = 0.05;
    private static final Map<ResourceKey<Level>, Set<BlockPos>> BURNING = new HashMap<>();
    /** Доля литра, ещё не списанная с резервуара (резервуар меряет в целых мБ). */
    private static final Map<BlockPos, Double> REMAINDER = new HashMap<>();

    private TankFires() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TankFires::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(TankFires::onLevelTick);
    }

    /** Горит ли резервуар под клеткой {@code fire}. */
    public static boolean burning(Level level, BlockPos fire) {
        Set<BlockPos> s = BURNING.get(level.dimension());
        return s != null && s.contains(fire);
    }

    /** Горящие клетки резервуаров измерения (для излучения пожара пролива, 09 §9.11.4); {@code null} — нет. */
    public static Set<BlockPos> burningCells(Level level) {
        return BURNING.get(level.dimension());
    }

    /** Огонь на крыше резервуара — завести пожар резервуара. */
    public static void ignite(ServerLevel level, BlockPos fire) {
        if (FireService.tankBelow(level, fire) == null) return;
        BURNING.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>()).add(fire.immutable());
    }

    /** Клетку накрыла пена: пожар резервуара здесь погашен. */
    static void foamed(Level level, BlockPos fire) {
        Set<BlockPos> s = BURNING.get(level.dimension());
        if (s != null) s.remove(fire);
        REMAINDER.remove(fire);
    }

    private static void onNeighborNotify(BlockEvent.NeighborNotifyEvent e) {
        if (e.getLevel() instanceof ServerLevel sl && e.getState().getBlock() instanceof BaseFireBlock) ignite(sl, e.getPos());
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || sl.getGameTime() % 20 != 0) return;
        Set<BlockPos> s = BURNING.get(sl.dimension());
        if (s == null || s.isEmpty()) return;
        for (Iterator<BlockPos> it = s.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (!sl.isLoaded(p)) continue;
            var tank = FireService.tankBelow(sl, p);
            if (tank == null) {
                it.remove();
                REMAINDER.remove(p);
                continue;
            }
            // огонь держится на горючем: на негорючей крыше обычный огонь гас бы сам
            if (sl.getBlockState(p).isAir()) sl.setBlock(p, BaseFireBlock.getState(sl, p), 3);
            var inv = tank.getTankInventory();
            double densityKgL = Math.max(0.5, inv.getFluid().getFluidType().getDensity() / 1000.0);
            double litres = BURN_KG_M2_S / densityKgL + REMAINDER.getOrDefault(p, 0.0);
            int mb = (int) litres;
            REMAINDER.put(p, litres - mb);
            if (mb > 0) inv.drain(mb, IFluidHandler.FluidAction.EXECUTE);
        }
    }
}
