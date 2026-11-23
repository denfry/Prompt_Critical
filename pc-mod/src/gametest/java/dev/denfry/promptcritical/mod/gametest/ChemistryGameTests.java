package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.chlor.HclFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.chlor.HclSynthesis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Неорганическая и основная химия GDD 10: печь синтеза HCl (§10.3.4). Трубы заменяет заливка. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemistryGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private ChemistryGameTests() {
    }

    private static void fill(IFluidHandler h, Fluid f, int mb) {
        h.fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    private static void feed(HclFurnaceBlockEntity f) {
        IFluidHandler in = f.fluids(Direction.NORTH);
        fill(in, PcFluids.HYDROGEN.source.get(), 400);
        fill(in, PcFluids.CHLORINE.source.get(), 400);
        fill(in, Fluids.WATER, 2000);
    }

    /**
     * Печь гасят и разжигают рукой: кислота 33 % снизу, затем сухой хлороводород; при избытке водорода ниже 5 %
     * загорается авария, а хлор уходит в воздух.
     */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void hclFurnaceMakesAcidThenDryGasAndSlipsChlorineWithoutExcessHydrogen(GameTestHelper h) {
        BlockPos pos = new BlockPos(5, 2, 5);
        h.setBlock(pos, PcBlocks.HCL_SYNTHESIS_FURNACE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.startSequence()
                .thenExecute(() -> {
                    HclFurnaceBlockEntity f = h.getBlockEntity(pos);
                    h.assertFalse(f.unit().on(), "furnace starts out");
                    f.cycleMode();
                    h.assertTrue(f.unit().on() && f.unit().mode() == HclSynthesis.Mode.ACID, "first use lights it on acid");
                })
                .thenWaitUntil(() -> {
                    HclFurnaceBlockEntity f = h.getBlockEntity(pos);
                    feed(f);
                    h.assertTrue(f.acid().amount() > 5, "hydrochloric acid " + f.acid().amount() + " L");
                    h.assertBlockProperty(pos, BlockStateProperties.LIT, true);
                    h.assertBlockProperty(pos, PcProperties.ALARM, false);
                    h.assertTrue(f.unit().cl2SlipMolS() == 0, "no chlorine slip at 7 % excess");
                    // снизу берут кислоту, а в боковую грань её не залить
                    FluidStack out = f.fluids(Direction.DOWN).drain(1, IFluidHandler.FluidAction.SIMULATE);
                    h.assertTrue(out.getFluid().isSame(PcFluids.HYDROCHLORIC_ACID.source.get()), "acid out of the bottom: " + out.getFluid());
                    h.assertTrue(f.dryHcl().amount() == 0, "no dry gas on acid");
                })
                .thenExecute(() -> ((HclFurnaceBlockEntity) h.getBlockEntity(pos)).cycleMode())
                .thenWaitUntil(() -> {
                    HclFurnaceBlockEntity f = h.getBlockEntity(pos);
                    feed(f);
                    h.assertTrue(f.unit().mode() == HclSynthesis.Mode.DRY, "second use: dry gas");
                    h.assertTrue(f.dryHcl().amount() > 1, "dry hydrogen chloride " + f.dryHcl().amount() + " nm3");
                    FluidStack out = f.fluids(Direction.DOWN).drain(1, IFluidHandler.FluidAction.SIMULATE);
                    h.assertTrue(out.getFluid().isSame(PcFluids.HYDROGEN_CHLORIDE.source.get()), "dry HCl out of the bottom: " + out.getFluid());
                })
                .thenExecute(() -> ((HclFurnaceBlockEntity) h.getBlockEntity(pos)).unit().setExcess(1.00))
                .thenWaitUntil(() -> {
                    HclFurnaceBlockEntity f = h.getBlockEntity(pos);
                    feed(f);
                    h.assertTrue(f.unit().cl2SlipMolS() > 0, "chlorine slips at stoichiometric hydrogen");
                    h.assertBlockProperty(pos, PcProperties.ALARM, true);
                })
                .thenExecute(() -> ((HclFurnaceBlockEntity) h.getBlockEntity(pos)).cycleMode())
                .thenWaitUntil(() -> {
                    HclFurnaceBlockEntity f = h.getBlockEntity(pos);
                    h.assertFalse(f.unit().on(), "third use puts it out");
                    h.assertBlockProperty(pos, BlockStateProperties.LIT, false);
                })
                .thenSucceed();
    }
}
