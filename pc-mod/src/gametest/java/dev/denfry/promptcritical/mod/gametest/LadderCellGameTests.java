package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.WholeShapes;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Нарисованные лестницы цельных моделей (плейтест I1: по лестницам каупера, деаэратора и конвертера не залезть — клетка
 * лестницы была на 12/16 твёрдой, игрок в неё не входил, и isLadder не спрашивался): клетка лестницы входит в объём
 * машины, не сталкивается, как ванильная лестница, и по ней лезут.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LadderCellGameTests {
    /** Все цельные модели, в манифесте которых есть клетки {@code climb}. */
    private static final String[] WITH_LADDERS = {"am1_reactor", "bn600_reactor", "bof_vessel_real", "bwr240_reactor",
            "byproduct_plant_cooler_real", "byproduct_plant_precipitator_real", "byproduct_plant_saturator_real",
            "byproduct_plant_scrubber_real", "candu6_reactor", "crucible_furnace_real", "cupola_furnace_real", "deaerator_real",
            "dust_catcher_real", "headframe_steel", "hot_blast_stove_real", "lancashire_boiler", "sinter_strand_real",
            "vver_pressurizer", "vver_reactor", "vver_steam_generator"};

    private LadderCellGameTests() {}

    @GameTest(template = "gametest/empty_16x24x16", timeoutTicks = 60)
    public static void everyDrawnLadderCellCanBeEnteredAndClimbed(GameTestHelper h) {
        var level = h.getLevel();
        var player = TestPlayers.of(h);
        StringBuilder bad = new StringBuilder();
        for (String id : WITH_LADDERS) {
            Block b = BuiltInRegistries.BLOCK.get(PromptCritical.id(id));
            h.assertTrue(b instanceof PcBlock, id + " is a machine block");
            Set<BlockPos> ladders = WholeShapes.climbCells(id);
            h.assertTrue(!ladders.isEmpty(), id + " has drawn ladders");
            // машина в середине шаблона по рамке модели (фасад на север: клетка мира = клетка модели)
            Set<BlockPos> all = new HashSet<>(WholeShapes.occupied(id));
            all.addAll(ladders);
            int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
            for (BlockPos m : all) {
                x0 = Math.min(x0, m.getX()); x1 = Math.max(x1, m.getX()); y0 = Math.min(y0, m.getY());
                z0 = Math.min(z0, m.getZ()); z1 = Math.max(z1, m.getZ());
            }
            BlockPos p = new BlockPos(7 - (x0 + x1) / 2, 1 - y0, 7 - (z0 + z1) / 2);
            BlockState st = b.defaultBlockState();
            if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            h.setBlock(p, st);
            for (BlockPos m : ladders) {
                BlockPos w = h.absolutePos(p.offset(m));
                BlockState s = level.getBlockState(w);
                String at = ' ' + id + m.toShortString();
                if (s.isAir()) bad.append(at).append("(outside the volume)");
                else if (!s.getCollisionShape(level, w).isEmpty()) bad.append(at).append("(collides)");
                else if (!s.isLadder(level, w, player)) bad.append(at).append("(not a ladder)");
            }
            h.setBlock(p, Blocks.AIR);
        }
        h.assertTrue(bad.isEmpty(), "ladder cells the player cannot climb:" + bad);
        h.succeed();
    }
}
