package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.PortKineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Станочный цех I0 реального масштаба: пять станков под контрприводом — тот же BE и юнит, что у старой серии, вал
 * Create входит в западную грань клетки контрпривода (0, 2, zc), склад открыт только станочнику с фасада.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShopRealScaleGameTests {
    private ShopRealScaleGameTests() {}

    /** Станок реального масштаба, клетка порта (фасад на север) и объём x, z от контроллера. */
    private record Tool(DeferredBlock<PcDeviceBlock> block, String id, BlockPos port, int x1, int z1) {}

    private static List<Tool> tools() {
        return List.of(new Tool(PcBlocks.MACHINE_LATHE_REAL, "machine_lathe_real", new BlockPos(0, 2, 0), 2, 0),
                new Tool(PcBlocks.DRILL_PRESS_REAL, "drill_press_real", new BlockPos(0, 2, 0), 0, 0),
                new Tool(PcBlocks.MILLING_MACHINE_REAL, "milling_machine_real", new BlockPos(0, 2, 1), 1, 1),
                new Tool(PcBlocks.SHAPING_MACHINE_REAL, "shaping_machine_real", new BlockPos(0, 2, 0), 1, 0),
                new Tool(PcBlocks.SURFACE_GRINDER_REAL, "surface_grinder_real", new BlockPos(0, 2, 0), 1, 0));
    }

    /**
     * Каждый станок: стройплощадка принимает план, поставленный станок рисует цельную модель, клетка контрпривода —
     * вход вала Create вдоль x, контроллер вала не имеет, склад — только с фасада, юнит станка привязан.
     */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 100)
    public static void realMachineToolsTakeTheShaftAtTheCountershaft(GameTestHelper h) {
        List<Tool> tools = tools();
        var items = Capabilities.ItemHandler.BLOCK;
        for (int i = 0; i < tools.size(); i++) {
            Tool t = tools.get(i);
            var cells = t.block().get().spec().footprint().cells(BlockPos.ZERO, Direction.NORTH);
            h.assertTrue(cells.size() == (t.x1() + 1) * 3 * (t.z1() + 1)
                    && cells.stream().map(c -> c.pos()).distinct().count() == cells.size(), t.id() + " volume covered once");
            BlockPos p = new BlockPos(1 + (i % 3) * 4, 1, 1 + (i / 3) * 4);
            for (int x = 0; x <= t.x1(); x++)
                for (int z = 0; z <= t.z1(); z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
            h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(t.id());
            h.assertTrue(w == null, t.id() + " plan accepted: " + (w == null ? "" : w.getString()));
            h.setBlock(p, Blocks.AIR);
            h.setBlock(p, t.block().get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            h.assertTrue(h.getBlockEntity(p) instanceof PortKineticMachineBlockEntity<?> b && b.real() && t.id().equals(b.wholeModelId()),
                    t.id() + " draws its whole model");
            var port = h.getBlockState(p.offset(t.port()));
            h.assertTrue(port.is(PcBlocks.KINETIC_PORT.get()) && port.getValue(KineticPortBlock.INPUT)
                    && port.getValue(BlockStateProperties.AXIS) == Direction.Axis.X, t.id() + " countershaft cell takes the shaft along x");
            h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p), Direction.NORTH) != null, t.id() + " stock from the front");
            h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p), Direction.WEST) == null, t.id() + " no stock from the side");
        }
        BlockPos old = new BlockPos(10, 1, 10);
        h.setBlock(old, PcBlocks.MACHINE_LATHE.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof PortKineticMachineBlockEntity<?> b && !b.real()
                && "promptcritical:machine_lathe".equals(b.deviceType())
                && h.getLevel().getCapability(items, h.absolutePos(old), Direction.WEST) != null, "the old lathe keeps its shaft and open stock");
        h.assertTrue(h.getBlockEntity(new BlockPos(1, 1, 1)) instanceof PortKineticMachineBlockEntity<?> b
                && "promptcritical:machine_lathe".equals(b.deviceType()), "the real lathe runs as a lathe in the simulation");
        h.succeedWhen(() -> {
            for (int i = 0; i < tools.size(); i++) {
                BlockPos p = new BlockPos(1 + (i % 3) * 4, 1, 1 + (i / 3) * 4);
                h.assertTrue(h.getBlockEntity(p) instanceof PortKineticMachineBlockEntity<?> b && !b.simId().isNone(),
                        tools.get(i).id() + " is bound to its machining unit");
            }
        });
    }
}
