package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.refinery.ClausUnitBlockEntity;
import dev.denfry.promptcritical.mod.refinery.HydrotreaterBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.AcidPlantBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Облагораживание на НПЗ: гидроочистка сернистого ДТ водородом с катализатором CoMo, сероводород в касающуюся
 * установку Клауса, сера — в сернокислотную установку вместо колчедана. Трубы заменяет заливка в патрубки.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RefineryUpgradingGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private RefineryUpgradingGameTests() {
    }

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static void fill(IFluidHandler h, Fluid f, int mb) {
        h.fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    /** ДТ 1,2 % S при 355 °C и LHSV 1,5 — на выходе меньше 100 ppm, H₂S уходит в Клаус, он делает серу. */
    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void hydrotreaterFeedsClausWithHydrogenSulfide(GameTestHelper h) {
        BlockPos hdt = new BlockPos(5, 2, 5), claus = hdt.east();
        h.setBlock(hdt, north(PcBlocks.HYDROTREATER.get()));
        h.setBlock(claus, north(PcBlocks.CLAUS_UNIT.get()));
        h.startSequence()
                .thenExecute(() -> {
                    HydrotreaterBlockEntity t = h.getBlockEntity(hdt);
                    t.input().insertItem(0, new ItemStack(PcItems.CATALYST_HYDROTREATING.get()), false);
                })
                .thenWaitUntil(() -> {
                    HydrotreaterBlockEntity t = h.getBlockEntity(hdt);
                    fill(t.fluids(Direction.NORTH), PcFluids.DIESEL_FUEL.source.get(), 4000);
                    fill(t.fluids(Direction.UP), PcFluids.HYDROGEN.source.get(), 8000);
                    h.assertTrue(t.product().amount() > 100, "treated diesel " + t.product().amount() + " L");
                    h.assertTrue(t.last().productSulfurPpm() < 100, "sulphur " + t.last().productSulfurPpm() + " ppm");
                    ClausUnitBlockEntity c = h.getBlockEntity(claus);
                    h.assertTrue(c.totalSulfurKg() > 0.5, "Claus sulphur " + c.totalSulfurKg() + " kg");
                    h.assertTrue(c.last().recovery() > 0.94, "Claus recovery " + c.last().recovery());
                })
                .thenSucceed();
    }

    /** Мешок серы Клауса заменяет колчедан в сернокислотной установке. */
    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void sulfurFeedsAcidPlant(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 2, 3);
        h.setBlock(at, north(PcBlocks.ACID_PLANT.get()));
        h.startSequence()
                .thenExecute(() -> ((AcidPlantBlockEntity) h.getBlockEntity(at)).input().insertItem(0, new ItemStack(PcItems.SULFUR.get(), 4), false))
                .thenWaitUntil(() -> {
                    AcidPlantBlockEntity a = h.getBlockEntity(at);
                    fill(a.fluids(Direction.NORTH), Fluids.WATER, 1000);
                    h.assertTrue(a.acid().amount() > 0.5, "sulphuric acid from sulphur " + a.acid().amount() + " L");
                })
                .thenSucceed();
    }
}
