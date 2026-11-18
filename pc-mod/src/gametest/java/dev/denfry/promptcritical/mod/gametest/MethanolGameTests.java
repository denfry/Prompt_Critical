package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.methanol.MethanolColumnBlockEntity;
import dev.denfry.promptcritical.mod.methanol.MethanolConverterBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Метанол: агрегат синтеза на валу Create с колонной вплотную набирает давление в контуре и отдаёт метанол марки AA
 * в тот же {@code methanol}, что берёт газопереработка; без колонны сырец остаётся в сепараторе агрегата. Трубы заменяет
 * заливка в патрубки в самом тесте.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MethanolGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** Агрегат 1×2×1 фасадом на север, вал сзади; колонна 1×3×1 — к западу вплотную. */
    private static final BlockPos UNIT = new BlockPos(6, 2, 5), COLUMN = new BlockPos(5, 2, 5);

    private MethanolGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void motorBehind(GameTestHelper h, BlockPos unit) {
        BlockPos motor = unit.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    private static void feed(MethanolConverterBlockEntity c) {
        IFluidHandler in = c.fluids(Direction.NORTH);
        in.fill(new FluidStack(PcFluids.SALES_GAS.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        in.fill(new FluidStack(PcFluids.BOILER_FEEDWATER.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
    }

    private static void start(GameTestHelper h) {
        MethanolConverterBlockEntity c = h.getBlockEntity(UNIT);
        c.reformer().loadCatalyst();
        c.loop().loadCatalyst();
        c.loop().setRunning(true);
        feed(c);
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void methanolUnitAndColumnMakeGradeAa(GameTestHelper h) {
        place(h, UNIT, PcBlocks.METHANOL_CONVERTER.get());
        place(h, COLUMN, PcBlocks.METHANOL_COLUMN.get());
        motorBehind(h, UNIT);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> start(h))
                .thenWaitUntil(() -> {
                    MethanolConverterBlockEntity c = h.getBlockEntity(UNIT);
                    feed(c);
                    MethanolColumnBlockEntity col = h.getBlockEntity(COLUMN);
                    h.assertFalse(c.noColumn(), "the column touching the unit is found");
                    h.assertTrue(c.ch4MolS() > 0, "the reformer burns and reforms gas");
                    h.assertTrue(c.loop().pressureBar() > 30, "loop pressure " + c.loop().pressureBar() + " bar");
                    h.assertTrue(c.loop().last().makeupSn() > 2.5, "reformed gas is hydrogen-rich: S " + c.loop().last().makeupSn());
                    h.assertTrue(col.lastResult() != null && col.lastResult().gradeAa(), "grade AA from the column");
                    h.assertTrue(col.methanol().amount() > 20, "methanol " + col.methanol().amount() + " L");
                    h.assertTrue(col.fluids(Direction.UP).drain(1, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.METHANOL.source.get(), "the same methanol fluid the gas plant uses");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1800)
    public static void withoutAColumnCrudeStaysInTheSeparator(GameTestHelper h) {
        place(h, UNIT, PcBlocks.METHANOL_CONVERTER.get());
        motorBehind(h, UNIT);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> start(h))
                .thenWaitUntil(() -> {
                    MethanolConverterBlockEntity c = h.getBlockEntity(UNIT);
                    feed(c);
                    h.assertTrue(c.noColumn(), "no column");
                    h.assertTrue(c.crudeMethanolMol() > 100, "crude in the separator: " + c.crudeMethanolMol() + " mol");
                    h.assertTrue(c.totalKg() > 3, "methanol made: " + c.totalKg() + " kg");
                })
                .thenSucceed();
    }
}
