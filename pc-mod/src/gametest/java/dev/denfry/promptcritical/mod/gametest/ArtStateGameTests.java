package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Свойства блоков, по которым выбираются модели и эффекты нарисованных вручную машин: огни пульта кипящего реактора,
 * сброс пара его клапанами, тёплый воздух над хранилищем стекла, открытый гермолюк.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArtStateGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private ArtStateGameTests() {
    }

    /** Реактор без топлива: пульт тёмный, клапаны закрыты; сверху стоит сегмент сепараторов со своей моделью. */
    @GameTest(template = LARGE)
    public static void coldBwrShowsDarkDeskAndSeparatorsSegment(GameTestHelper h) {
        BlockPos c = new BlockPos(5, 2, 5);
        h.setBlock(c, PcBlocks.BWR_REACTOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.runAfterDelay(40, () -> {
            BlockState s = h.getBlockState(c);
            h.assertFalse(s.getValue(BlockStateProperties.LIT), "no fuel, no power: the desk stays dark");
            h.assertFalse(s.getValue(BlockStateProperties.OPEN), "relief valves shut");
            BlockState top = h.getBlockState(c.above(3));
            h.assertTrue(top.is(PcBlocks.BWR_REACTOR.get()) && top.getValue(PcProperties.BWR_SEGMENT) == PcProperties.BwrSegment.SEPARATORS,
                    "separators segment anchor three blocks up");
            h.succeed();
        });
    }

    /** Пустое хранилище не греет воздух. */
    @GameTest(template = LARGE)
    public static void emptyHlwVaultStaysCold(GameTestHelper h) {
        BlockPos v = new BlockPos(5, 2, 5);
        h.setBlock(v, PcBlocks.HLW_VAULT.get().defaultBlockState());
        h.runAfterDelay(45, () -> {
            h.assertFalse(h.getBlockState(v).getValue(BlockStateProperties.LIT), "nothing stored, no warm air");
            h.succeed();
        });
    }

    /** Гермолюк открывается и закрывается рукой — открытое состояние рисуется своей моделью. */
    @GameTest(template = LARGE)
    public static void containmentHatchTogglesOpen(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        h.setBlock(p, PcBlocks.CONTAINMENT_HATCH.get().defaultBlockState());
        var player = TestPlayers.of(h);
        h.useBlock(p, player);
        h.assertTrue(h.getBlockState(p).getValue(BlockStateProperties.OPEN), "opened");
        h.useBlock(p, player);
        h.assertFalse(h.getBlockState(p).getValue(BlockStateProperties.OPEN), "closed again");
        h.succeed();
    }
}
