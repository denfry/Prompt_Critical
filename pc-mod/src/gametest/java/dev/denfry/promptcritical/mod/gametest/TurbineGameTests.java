package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.powerhouse.SteamTurbineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Паровая турбина без котла: двойник есть, пара нет — вал Create стоит, клапан не открыть. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurbineGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";
    private static final BlockPos AT = new BlockPos(1, 1, 1);

    private TurbineGameTests() {
    }

    @GameTest(template = SMALL)
    public static void turbineWithoutBoilerStandsStill(GameTestHelper h) {
        h.setBlock(AT, PcBlocks.STEAM_TURBINE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((SteamTurbineBlockEntity) h.getBlockEntity(AT)).unit() != null, "turbine twin"))
                .thenIdle(40)
                .thenExecute(() -> {
                    SteamTurbineBlockEntity t = h.getBlockEntity(AT);
                    h.assertTrue(t.unit().boiler().isNone(), "no boiler nearby");
                    h.assertTrue(t.unit().isTurbine(), "the twin is a turbine, not an engine");
                    h.assertTrue(t.outputRpm() == 0, "no steam, no rotation");
                    h.assertFalse(h.getBlockState(AT).getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING), "not running");
                })
                .thenSucceed();
    }

    /** Конденсатор под турбиной у воды — выхлоп в вакуум (единицы кПа); без воды — атмосфера. */
    @GameTest(template = SMALL)
    public static void condenserByWaterGivesVacuum(GameTestHelper h) {
        BlockPos at = new BlockPos(1, 2, 1), cond = at.below();
        h.setBlock(at, PcBlocks.STEAM_TURBINE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(cond, PcBlocks.CONDENSER.get().defaultBlockState());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((SteamTurbineBlockEntity) h.getBlockEntity(at)).unit() != null, "turbine twin"))
                .thenIdle(25)
                .thenExecute(() -> {
                    double p = ((SteamTurbineBlockEntity) h.getBlockEntity(at)).unit().exhaustPressurePa();
                    h.assertTrue(p > 0.1e6, "a dry condenser keeps no vacuum: " + p);
                    h.setBlock(cond.east(), net.minecraft.world.level.block.Blocks.WATER);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    double p = ((SteamTurbineBlockEntity) h.getBlockEntity(at)).unit().exhaustPressurePa();
                    h.assertTrue(p > 2e3 && p < 10e3, "a condenser by the water holds vacuum: " + p);
                })
                .thenSucceed();
    }
}
