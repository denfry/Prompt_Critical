package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.grid.ConnectorBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridWorld;
import dev.denfry.promptcritical.mod.grid.TransformerBlockEntity;
import dev.denfry.promptcritical.mod.grid.WireCoilItem;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.TransformerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Молниезащита линии 6 кВ (07 §7.15.1): заземлитель опоры по грунту и ОПН у трансформатора. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightningProtectionGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos NEAR = new BlockPos(2, 4, 2), FAR = new BlockPos(9, 4, 2), TRAFO = new BlockPos(9, 3, 4);

    private LightningProtectionGameTests() {
    }

    /** Стержень 3 м в суглинке — 33,5 Ом (тест T7.18), два — меньше; без стержня опора не заземлена. */
    @GameTest(template = LARGE, timeoutTicks = 20)
    public static void poleEarthIsTheRodsInTheSoilUnderIt(GameTestHelper h) {
        for (BlockPos q : BlockPos.betweenClosed(new BlockPos(3, 1, 3), new BlockPos(7, 4, 7))) h.setBlock(q, Blocks.DIRT);
        BlockPos base = new BlockPos(5, 5, 5);
        for (int y = 0; y < 3; y++) h.setBlock(base.above(y), PcBlocks.WOODEN_POLE.get());
        BlockPos top = h.absolutePos(base.above(2));
        h.assertTrue(Double.isInfinite(GridWorld.poleGroundOhm(h.getLevel(), top)), "no rod, no earth");
        h.setBlock(base.below(), PcBlocks.GROUND_ELECTRODE.get());
        double one = GridWorld.poleGroundOhm(h.getLevel(), top);
        h.assertTrue(Math.abs(one - 33.5) < 0.5, "one rod in loam: " + one);
        h.setBlock(base.below().east(), PcBlocks.GROUND_ELECTRODE.get());
        double two = GridWorld.poleGroundOhm(h.getLevel(), top);
        h.assertTrue(two < one * 0.7, "two rods: " + two);
        h.succeed();
    }

    /**
     * Пролёт 6 кВ между двумя опорами, на дальнем конце — трансформатор. Удар в провод перекрывает изоляцию, волна
     * 60 кВ пробивает трансформатор с вероятностью 0,2 за удар; с разрядником рядом — ни разу из 30.
     */
    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void anArresterSavesTheTransformerFromLineSurges(GameTestHelper h) {
        for (BlockPos c : new BlockPos[] {NEAR, FAR})
            for (int y = 1; y < 4; y++) h.setBlock(new BlockPos(c.getX(), y, c.getZ()), PcBlocks.WOODEN_POLE.get());
        h.setBlock(NEAR, PcBlocks.WIRE_CONNECTOR_MV.get());
        h.setBlock(FAR, PcBlocks.WIRE_CONNECTOR_MV.get());
        for (BlockPos c : new BlockPos[] {FAR.above(), FAR.above().south(), FAR.above().south(2)})
            h.setBlock(c, PcBlocks.POWER_CABLE.get());
        h.setBlock(TRAFO, PcBlocks.DISTRIBUTION_TRANSFORMER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(TRAFO.west(), PcBlocks.SURGE_ARRESTER.get());
        var grids = SimBindings.get(h.getLevel()).grids();
        Vec3 mid = h.absoluteVec(new Vec3(5.5, 4.75, 2.5));
        int[] strikes = {0};
        h.startSequence()
                .thenExecute(() -> grids.addSpan(h.getBlockEntity(NEAR), h.getBlockEntity(FAR), WireCoilItem.CONDUCTOR, false))
                .thenIdle(40) // пересборка узлов и пролёта в сети
                .thenWaitUntil(() -> {
                    TransformerUnit u = ((TransformerBlockEntity) h.getBlockEntity(TRAFO)).unit();
                    h.assertTrue(u != null && u.arrester(), "the transformer sees its arrester");
                })
                .thenExecute(() -> {
                    for (int k = 0; k < 30; k++) grids.lightning(mid);
                    h.assertTrue(!((TransformerBlockEntity) h.getBlockEntity(TRAFO)).unit().damaged(), "the arrester clips every surge");
                    h.setBlock(TRAFO.west(), Blocks.AIR);
                })
                .thenWaitUntil(() -> h.assertTrue(!((TransformerBlockEntity) h.getBlockEntity(TRAFO)).unit().arrester(), "arrester removed"))
                .thenWaitUntil(() -> {
                    TransformerUnit u = ((TransformerBlockEntity) h.getBlockEntity(TRAFO)).unit();
                    if (!u.damaged() && strikes[0] < 60) {
                        strikes[0]++;
                        grids.lightning(mid);
                    }
                    h.assertTrue(u.damaged(), "a bare transformer is burnt by a surge after " + strikes[0] + " strokes");
                })
                .thenSucceed();
    }
}
