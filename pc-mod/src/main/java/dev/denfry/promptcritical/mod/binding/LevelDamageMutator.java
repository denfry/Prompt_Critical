package dev.denfry.promptcritical.mod.binding;

import dev.denfry.promptcritical.mod.registry.PcTags;
import dev.denfry.promptcritical.sim.consequence.BlockInfo;
import dev.denfry.promptcritical.sim.consequence.DamageBudget;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.mod.fx.WorldFx;
import dev.denfry.promptcritical.sim.core.GridPos;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Применение решений {@link DamageBudget} к миру (04d §4d.2.2). Блоки в незагруженных чанках не трогаются (считаются
 * защищёнными), чтобы авария не грузила мир. Территорий (claims, радиус станции) в M2 нет: {@link #mayDamage}
 * разрешает везде — режим {@code contained} пока ведёт себя как {@code realistic}.
 */
public final class LevelDamageMutator implements DamageBudget.BlockMutator, DamageBudget.TerritoryOracle {
    private static final BlockInfo UNLOADED = new BlockInfo(false, true, Float.MAX_VALUE);
    /** Обломком вылетает каждый такой разрушенный взрывом блок; остальные рассыпаются. */
    static final int DEBRIS_EVERY = 3;
    /** Не больше обломков за тик: падающие блоки — сущности. */
    static final int DEBRIS_PER_TICK = 48;

    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private long debrisTick, destroyed;
    private int debrisThisTick;

    public LevelDamageMutator(ServerLevel level) {
        this.level = level;
    }

    private BlockPos at(GridPos p) {
        return cursor.set(p.x(), p.y(), p.z());
    }

    @Override
    public BlockInfo inspect(GridPos p) {
        BlockPos pos = at(p);
        if (!level.isLoaded(pos)) return UNLOADED;
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return BlockInfo.AIR;
        boolean immune = s.is(PcTags.Blocks.DAMAGE_IMMUNE) || s.getDestroySpeed(level, pos) < 0;
        return new BlockInfo(false, immune, s.getBlock().getExplosionResistance());
    }

    @Override
    public void destroy(GridPos p, boolean dropItems) {
        level.destroyBlock(at(p).immutable(), dropItems);
    }

    /**
     * Взрыв не стирает блоки, а рвёт их: каждый {@link #DEBRIS_EVERY}-й разрушенный блок без блок-сущности и не часть
     * машины вылетает обломком от центра, тем быстрее, чем ближе к нему; остальные рассыпаются в куски.
     */
    @Override
    public void destroy(GridPos p, boolean dropItems, GridPos origin, DamageKind kind) {
        BlockPos pos = at(p).immutable();
        BlockState s = level.getBlockState(pos);
        boolean blast = kind == DamageKind.BLAST || kind == DamageKind.OVERPRESSURE_BREACH || kind == DamageKind.MISSILE;
        long tick = level.getGameTime();
        if (tick != debrisTick) {
            debrisTick = tick;
            debrisThisTick = 0;
        }
        if (blast && !s.hasBlockEntity() && !isMachineCell(s) && ++destroyed % DEBRIS_EVERY == 0 && debrisThisTick < DEBRIS_PER_TICK) {
            debrisThisTick++;
            Vec3 c = pos.getCenter();
            Vec3 from = new Vec3(origin.x() + 0.5, origin.y() + 0.5, origin.z() + 0.5);
            Vec3 d = c.subtract(from);
            double dist = Math.max(1, d.length());
            double speed = 1.6 / (1 + dist / 4) * (0.7 + 0.6 * level.random.nextDouble());
            Vec3 v = d.scale(1 / dist).scale(speed).add(0, 0.25 + 0.35 * level.random.nextDouble(), 0);
            WorldFx.shards(level, s, c, 12);
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, s);
            fb.dropItem = false;
            fb.setDeltaMovement(v);
            return;
        }
        if (blast) WorldFx.shards(level, s, pos.getCenter(), 6);
        level.destroyBlock(pos, dropItems);
    }

    /** Клетка цельной модели или машины с сегментами: её не вырвать отдельно от машины. */
    private static boolean isMachineCell(BlockState s) {
        for (var prop : s.getProperties()) if (prop.getName().equals("segment") || prop.getName().equals("part")) return true;
        return false;
    }

    @Override
    public void replace(GridPos p, String blockId) {
        Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
        level.setBlock(at(p).immutable(), b.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public boolean mayDamage(UUID responsibleTeam, GridPos p) {
        return true;
    }
}
