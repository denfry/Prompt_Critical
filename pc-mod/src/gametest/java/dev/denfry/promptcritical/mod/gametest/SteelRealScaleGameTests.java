package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.multiblock.WholePorts;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import dev.denfry.promptcritical.mod.steelworks.BofVesselRealBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.ReheatingFurnaceRealBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Сталь и прокат реального масштаба: кислородный конвертер {@code bof_vessel_real} и методическая печь
 * {@code reheating_furnace_real} — цельные модели на тех же юнитах симуляции, что старые блоки; порты только у
 * нарисованных патрубков; клеть прокатного стана берёт вал Create со стороны клети шестерён (запад).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteelRealScaleGameTests {
    private static final String TALL = "gametest/empty_12x24x12";

    private SteelRealScaleGameTests() {}

    /** Каменное основание под объёмом x0…x1, z0…z1 от {@code p} (фасад на север) и стройплощадка на {@code p}. */
    private static ConstructionSiteBlockEntity site(GameTestHelper h, BlockPos p, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        return (ConstructionSiteBlockEntity) h.getBlockEntity(p);
    }

    /**
     * Конвертер: 7 × 6 × 5, стройплощадка его принимает; вал наклона входит в клетку редуктора (−3, 2, 0) соосно
     * цапфе; газ — только из газохода сверху, лом и известь — только с края площадки; фурма над камином в (0, 6, 0) —
     * фурма конвертера; юнит — тот же конвертер, что у старого блока.
     */
    @GameTest(template = TALL, timeoutTicks = 100)
    public static void realConverterTakesTheTiltShaftAtTheTrunnionAndRunsTheConverterUnit(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        var w = site(h, p, -3, 3, -2, 2).accept("bof_vessel_real");
        h.assertTrue(w == null, "converter plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(PcBlocks.FP_BOF_VESSEL_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 7 * 6 * 5, "7x6x5 converter");
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, PcBlocks.BOF_VESSEL_REAL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.assertTrue(h.getBlockEntity(p) instanceof BofVesselRealBlockEntity b && "bof_vessel_real".equals(b.wholeModelId()),
                "the real converter draws its whole model");
        BlockPos port = p.offset(-3, 2, 0);
        h.assertTrue(h.getBlockState(port).is(PcBlocks.KINETIC_PORT.get()) && h.getBlockState(port).getValue(KineticPortBlock.INPUT),
                "the tilt reducer cell takes the Create shaft");
        h.assertTrue(!h.getBlockState(p.offset(-1, 0, 0)).is(PcBlocks.KINETIC_PORT.get()), "the old port cell is a plain part here");
        var fluids = Capabilities.FluidHandler.BLOCK;
        var items = Capabilities.ItemHandler.BLOCK;
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(p.offset(0, 5, 1)), Direction.UP) != null, "converter gas from the duct");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(p.offset(0, 5, 1)), Direction.NORTH) == null, "the duct side is closed");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p.offset(3, 2, 1)), Direction.EAST) != null, "scrap and lime from the platform");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p.offset(0, 0, -2)), Direction.NORTH) == null, "no charging into the vessel side");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p), Direction.DOWN) == null, "the controller under the vessel is closed");
        h.assertTrue(WholePorts.allows("bof_vessel_real", Direction.EAST, -1, 5, 0, Direction.UP), "the gas port turns with the converter");
        h.setBlock(p.offset(0, 6, 0), PcBlocks.OXYGEN_LANCE.get());
        h.assertTrue(((BofVesselRealBlockEntity) h.getBlockEntity(p)).lance() != null, "the lance over the hood serves the converter");
        h.succeedWhen(() -> h.assertTrue(h.getBlockEntity(p) instanceof BofVesselRealBlockEntity b && b.unit() != null,
                "the real converter is bound to a ConverterUnit"));
    }

    /**
     * Методическая печь: 3 × 2 × 3, садка — с юга через загрузочное окно, газ — к горелке на востоке, фасад (окно
     * выдачи) закрыт для воронок; юнит — та же камерная печь, что у старого блока; заслонки стоят опущенными.
     */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 100)
    public static void realReheatingFurnaceChargesFromTheBackAndRunsTheFurnaceUnit(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        var w = site(h, p, -1, 1, -1, 1).accept("reheating_furnace_real");
        h.assertTrue(w == null, "furnace plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, PcBlocks.REHEATING_FURNACE_REAL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.assertTrue(h.getBlockEntity(p) instanceof ReheatingFurnaceRealBlockEntity b && "reheating_furnace_real".equals(b.wholeModelId())
                && b.dischargeDoorLift(0) == 0 && b.chargeDoorLift(0) == 0, "the real furnace draws its whole model with the doors down");
        var fluids = Capabilities.FluidHandler.BLOCK;
        var items = Capabilities.ItemHandler.BLOCK;
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p.offset(0, 0, 1)), Direction.SOUTH) != null, "charging at the back");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p.offset(0, 0, -1)), Direction.NORTH) == null, "no hopper at the discharge door");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(p.offset(1, 0, 0)), Direction.EAST) != null, "gas to the burner");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(p.offset(-1, 0, 0)), Direction.WEST) == null, "the west wall has no burner");
        BlockPos old = new BlockPos(1, 1, 1);
        h.setBlock(old, PcBlocks.REHEATING_FURNACE.get().defaultBlockState());
        h.assertTrue(!(h.getBlockEntity(old) instanceof ReheatingFurnaceRealBlockEntity), "the old furnace keeps its own block entity");
        h.succeedWhen(() -> h.assertTrue(h.getBlockEntity(p) instanceof ReheatingFurnaceRealBlockEntity b && b.unit() != null,
                "the real furnace is bound to a BatchFurnaceUnit"));
    }

    /** Клеть прокатного стана: вал Create — в клетку (−1, 0, 0) клети шестерён вдоль оси валков (x при фасаде на север). */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void millStandTakesTheShaftAtThePinionStand(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        h.setBlock(p, PcBlocks.ROLLING_MILL_STAND.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var port = h.getBlockState(p.offset(-1, 0, 0));
        h.assertTrue(port.is(PcBlocks.KINETIC_PORT.get()) && port.getValue(KineticPortBlock.INPUT)
                && port.getValue(BlockStateProperties.AXIS) == Direction.Axis.X, "the pinion stand cell takes the shaft along x");
        h.assertTrue(!h.getBlockState(p.offset(1, 0, 0)).is(PcBlocks.KINETIC_PORT.get()), "no drive on the east side");
        h.succeed();
    }
}
