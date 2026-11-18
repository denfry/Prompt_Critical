package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.environment.SpentFuel;
import dev.denfry.promptcritical.mod.gas.HeliumRecoveryBlockEntity;
import dev.denfry.promptcritical.mod.radiation.FuelRackBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Гелиевая установка даёт гелий только с жидким азотом; облучённая сборка на стеллаже — источник излучения, вода над
 * стеллажом ослабляет дозу на порядки, тепло сборки видно стеллажу.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeliumActivityGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private HeliumActivityGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void heliumNeedsLiquidNitrogen(GameTestHelper h) {
        BlockPos at = new BlockPos(5, 2, 5);
        h.setBlock(at, PcBlocks.HELIUM_RECOVERY_UNIT.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.startSequence()
                .thenExecute(() -> {
                    HeliumRecoveryBlockEntity u = h.getBlockEntity(at);
                    u.fluids(Direction.NORTH).fill(new FluidStack(PcFluids.SALES_GAS.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    HeliumRecoveryBlockEntity u = h.getBlockEntity(at);
                    h.assertTrue(u.helium().amount() == 0, "no liquid nitrogen, no helium");
                })
                .thenWaitUntil(() -> {
                    HeliumRecoveryBlockEntity u = h.getBlockEntity(at);
                    IFluidHandler in = u.fluids(Direction.NORTH);
                    in.fill(new FluidStack(PcFluids.SALES_GAS.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
                    in.fill(new FluidStack(PcFluids.LIQUID_NITROGEN.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(u.helium().amount() > 0.01, "helium " + u.helium().amount() + " nm3");
                    h.assertTrue(u.unit().recovery() > 0.88, "helium recovery " + u.unit().recovery());
                    h.assertTrue(u.returned().amount() > 1, "gas back to the grid " + u.returned().amount());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void poolWaterShieldsSpentFuel(GameTestHelper h) {
        BlockPos rack = new BlockPos(6, 1, 6);
        h.setBlock(rack, PcBlocks.FUEL_RACK.get());
        Vec3 above = Vec3.atCenterOf(h.absolutePos(rack.above(5)));
        double[] dry = new double[1];
        h.startSequence()
                .thenExecute(() -> {
                    FuelRackBlockEntity r = h.getBlockEntity(rack);
                    ItemStack fuel = new ItemStack(PcItems.URANIUM_SLUG.get());
                    // выдержанная год ТВС: выгрузка «год назад» по игровому времени
                    long tick = h.getLevel().getGameTime() - (long) (365.25 * 86400 / ActivityData.GAME_S_PER_TICK);
                    fuel.set(ActivityData.ACTIVITY.get(), SpentFuel.discharge(460, 40, 38, 3, tick));
                    h.assertTrue(RadiationService.itemSource(fuel, h.getLevel().getGameTime()) != null, "item with activity is a source");
                    r.slots().insertItem(0, fuel, false);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    FuelRackBlockEntity r = h.getBlockEntity(rack);
                    dry[0] = RadiationService.rateSvH(h.getLevel(), above);
                    h.assertTrue(dry[0] > 0.1, "dry rack, 5 m above: " + dry[0] + " Sv/h");
                    h.assertTrue(!r.submerged() && r.heatW() > 1000, "dry rack and kilowatts of decay heat: " + r.heatW());
                    for (int y = 2; y <= 5; y++) h.setBlock(new BlockPos(6, y, 6), Blocks.WATER);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    FuelRackBlockEntity r = h.getBlockEntity(rack);
                    double wet = RadiationService.rateSvH(h.getLevel(), above);
                    h.assertTrue(r.submerged(), "rack under water");
                    h.assertTrue(wet < dry[0] * 1e-4, "four metres of water: " + wet + " vs " + dry[0] + " Sv/h");
                })
                .thenSucceed();
    }
}
