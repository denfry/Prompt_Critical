package dev.denfry.promptcritical.mod.binding;

import dev.denfry.promptcritical.mod.registry.PcTags;
import dev.denfry.promptcritical.sim.consequence.BlockInfo;
import dev.denfry.promptcritical.sim.consequence.DamageBudget;
import dev.denfry.promptcritical.sim.core.GridPos;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Применение решений {@link DamageBudget} к миру (04d §4d.2.2). Блоки в незагруженных чанках не трогаются (считаются
 * защищёнными), чтобы авария не грузила мир. Территорий (claims, радиус станции) в M2 нет: {@link #mayDamage}
 * разрешает везде — режим {@code contained} пока ведёт себя как {@code realistic}.
 */
public final class LevelDamageMutator implements DamageBudget.BlockMutator, DamageBudget.TerritoryOracle {
    private static final BlockInfo UNLOADED = new BlockInfo(false, true, Float.MAX_VALUE);

    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

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
