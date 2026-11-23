package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.WholeShapes;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Кузница и машиностроение реального масштаба (18 §18.6–18.7, art/blockbench/smithy/**&#47;*_real.author.js,
 * art/blockbench/rolling/real/rolling_shop_real.author.js): площадка принимает чертёж, машина — тот же BE, что у старой,
 * с цельной моделью, объём — как у модели, вал Create входит в контроллер с той же стороны, что у старой машины, заготовку
 * берут только нарисованные рабочие места.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmithyRealScaleGameTests {
    private static final String TEMPLATE = "gametest/empty_12x12x12";

    private SmithyRealScaleGameTests() {}

    /** Машина: id, блок реального масштаба и старый, объём (клетки), грань вала (null — без вала), порт и закрытая клетка. */
    private record M(String id, Supplier<? extends Block> real, Supplier<? extends Block> old, Footprint fp, int cells,
            @Nullable Direction shaft, BlockPos port, Direction portSide, BlockPos closed) {}

    private static final M[] MACHINES = {
        new M("power_hammer_real", PcBlocks.POWER_HAMMER_REAL, PcBlocks.POWER_HAMMER, PcBlocks.FP_POWER_HAMMER_REAL, 1 * 3 * 2,
                Direction.WEST, new BlockPos(0, 0, -1), Direction.NORTH, new BlockPos(0, 2, 0)),
        new M("bloomery_real", PcBlocks.BLOOMERY_REAL, PcBlocks.BLOOMERY, PcBlocks.FP_BLOOMERY_REAL, 2 * 2 * 1,
                null, new BlockPos(0, 1, 0), Direction.UP, new BlockPos(-1, 1, 0)),
        new M("forge_hearth_real", PcBlocks.FORGE_HEARTH_REAL, PcBlocks.FORGE_HEARTH, PcBlocks.FP_FORGE_HEARTH_REAL, 2 * 3 * 2,
                null, new BlockPos(0, 0, 0), Direction.NORTH, new BlockPos(1, 0, 0)),
        new M("crank_press_real", PcBlocks.CRANK_PRESS_REAL, PcBlocks.CRANK_PRESS, PcBlocks.FP_CRANK_PRESS_REAL, 1 * 3 * 2,
                Direction.SOUTH, new BlockPos(0, 0, -1), Direction.NORTH, new BlockPos(0, 2, 0)),
        new M("wire_mill_real", PcBlocks.WIRE_MILL_REAL, PcBlocks.WIRE_MILL, PcBlocks.FP_WIRE_MILL_REAL, 1 * 2 * 2,
                Direction.WEST, new BlockPos(0, 0, -1), Direction.NORTH, new BlockPos(0, 1, 0)),
        new M("sheet_mill_real", PcBlocks.SHEET_MILL_REAL, PcBlocks.SHEET_MILL, PcBlocks.FP_SHEET_MILL_REAL, 2 * 2 * 3,
                Direction.WEST, new BlockPos(1, 0, -1), Direction.NORTH, new BlockPos(0, 1, 0)),
        new M("notching_press_real", PcBlocks.NOTCHING_PRESS_REAL, PcBlocks.NOTCHING_PRESS, PcBlocks.FP_NOTCHING_PRESS_REAL, 1 * 2 * 2,
                Direction.SOUTH, new BlockPos(0, 0, -1), Direction.NORTH, new BlockPos(0, 1, 0)),
        new M("winding_machine_real", PcBlocks.WINDING_MACHINE_REAL, PcBlocks.WINDING_MACHINE, PcBlocks.FP_WINDING_MACHINE_REAL, 2 * 2 * 1,
                Direction.WEST, new BlockPos(1, 0, 0), Direction.NORTH, new BlockPos(1, 1, 0)),
        new M("heating_furnace_real", PcBlocks.HEATING_FURNACE_REAL, PcBlocks.HEATING_FURNACE, PcBlocks.FP_HEATING_FURNACE_REAL, 2 * 4 * 2,
                null, new BlockPos(0, 0, 0), Direction.NORTH, new BlockPos(1, 1, 1)),
        new M("hardening_furnace_real", PcBlocks.HARDENING_FURNACE_REAL, PcBlocks.HARDENING_FURNACE, PcBlocks.FP_HARDENING_FURNACE_REAL,
                2 * 2 * 2, null, new BlockPos(1, 0, 0), Direction.UP, new BlockPos(1, 1, 1)),
    };

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /** Площадка на каменном основании под нижними клетками объёма принимает чертёж машины. */
    private static void siteAccepts(GameTestHelper h, BlockPos p, M m) {
        for (BlockPos c : m.fp().cells(BlockPos.ZERO, Direction.NORTH).stream().map(Footprint.Cell::pos).toList())
            if (c.getY() == 0) h.setBlock(p.offset(c.getX(), -1, c.getZ()), Blocks.STONE);
        h.setBlock(p, north(SiteRegistry.SITE.get()));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(m.id());
        h.assertTrue(w == null, m.id() + " plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(p, Blocks.AIR);
        for (BlockPos c : m.fp().cells(BlockPos.ZERO, Direction.NORTH).stream().map(Footprint.Cell::pos).toList())
            if (c.getY() == 0) h.setBlock(p.offset(c.getX(), -1, c.getZ()), Blocks.AIR);
    }

    /** Каждая машина: чертёж, объём, BE старой машины с цельной моделью, вал, рабочее место и закрытая клетка. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void realSmithyMachinesAreTheOldMachines(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        var caps = Capabilities.ItemHandler.BLOCK;
        for (M m : MACHINES) {
            siteAccepts(h, p, m);
            h.assertTrue(m.fp().cells(BlockPos.ZERO, Direction.NORTH).size() == m.cells(), m.id() + ": " + m.cells() + " cells");
            h.setBlock(p, north(m.real().get()));
            BlockEntity be = h.getBlockEntity(p);
            h.assertTrue(be instanceof WholeModelHost w && m.id().equals(w.wholeModelId()), m.id() + " has its whole model");
            BlockState s = h.getBlockState(p);
            if (m.shaft() != null) {
                h.assertTrue(s.getBlock() instanceof KineticMachineBlock k && k.hasShaftTowards(h.getLevel(), h.absolutePos(p), s, m.shaft()),
                        m.id() + ": the Create shaft enters the " + m.shaft() + " face of the controller");
            }
            h.assertTrue(h.getLevel().getCapability(caps, h.absolutePos(p.offset(m.port())), m.portSide()) != null,
                    m.id() + ": the drawn work place takes items");
            h.assertTrue(h.getLevel().getCapability(caps, h.absolutePos(p.offset(m.closed())), Direction.NORTH) == null,
                    m.id() + ": a cell without a port takes nothing");
            h.setBlock(p, Blocks.AIR);
            BlockPos old = new BlockPos(1, 2, 1);
            h.setBlock(old, north(m.old().get()));
            h.assertTrue(h.getBlockEntity(old) instanceof WholeModelHost w && w.wholeModelId() == null,
                    m.id() + ": the old machine keeps its own model");
            h.setBlock(old, Blocks.AIR);
        }
        h.succeed();
    }

    /**
     * Каждая клетка объёма, где модель или движущаяся часть что-то рисует, твёрдая, и ничего не нарисовано вне объёма
     * (как {@code PlaytestI0GameTests#everyDrawnCellOfI0WholeModelsBlocksThePlayer}): сквозь барабан, мехи и маховик
     * не пройти, а воздух у трубы и над рольгангами остаётся проходимым.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void everyDrawnCellOfSmithyWholeModelsBlocksThePlayer(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        var level = h.getLevel();
        StringBuilder bad = new StringBuilder();
        for (M m : MACHINES) {
            h.setBlock(p, north(m.real().get()));
            BlockPos abs = h.absolutePos(p);
            java.util.Set<BlockPos> drawn = new java.util.HashSet<>(WholeShapes.occupied(m.id()));
            h.assertTrue(!drawn.isEmpty(), m.id() + " has cell shape data");
            for (Footprint.Cell c : m.fp().cells(abs, Direction.NORTH)) {
                BlockPos d = c.pos().subtract(abs);   // фасад на север: клетка мира = клетка модели
                if (!drawn.remove(d)) continue;
                if (level.getBlockState(c.pos()).getCollisionShape(level, c.pos()).isEmpty()) bad.append(' ').append(m.id()).append(d.toShortString());
            }
            for (BlockPos d : drawn) bad.append(' ').append(m.id()).append(d.toShortString()).append("(outside the volume)");
            h.setBlock(p, Blocks.AIR);
        }
        h.assertTrue(bad.isEmpty(), "drawn cells the player walks through:" + bad);
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void realSmithyMachinesHaveDrawingRecipes(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        for (M m : MACHINES)
            h.assertTrue(recipes.byKey(PromptCritical.id("smithy/" + m.id() + "_drawing")).isPresent(), "drawing recipe " + m.id());
        h.succeed();
    }
}
