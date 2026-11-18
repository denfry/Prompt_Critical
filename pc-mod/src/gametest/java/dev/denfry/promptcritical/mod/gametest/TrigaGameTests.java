package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.isotopes.TrigaBlockEntity;
import dev.denfry.promptcritical.mod.isotopes.TrigaPanels;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.reactor.TrigaCore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * TRIGA в мире (12 §12.6.1): привод на мосту над бассейном даёт импульс больше 100 МВт без повреждения топлива, пульт
 * показывает его итоги, стержень на модели выброшен; без бассейна защиту не снять.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrigaGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos DRIVE = new BlockPos(5, 1 + TrigaBlockEntity.POOL_DEPTH, 5);

    private TrigaGameTests() {
    }

    /** Колодец воды глубиной в бассейн, обложенный камнем, мост — сверху. */
    private static void pool(GameTestHelper h) {
        for (int y = 1; y <= TrigaBlockEntity.POOL_DEPTH; y++) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    h.setBlock(new BlockPos(5 + dx, y, 5 + dz), dx == 0 && dz == 0 ? Blocks.WATER : Blocks.STONE);
        }
    }

    private static int[] panel(GameTestHelper h) {
        int[] out = new int[TrigaPanels.T_CHANNELS];
        ((TrigaBlockEntity) h.getBlockEntity(DRIVE)).readPanel(out);
        return out;
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void threeDollarPulseInThePoolIsAboveAHundredMegawatts(GameTestHelper h) {
        pool(h);
        h.setBlock(DRIVE, PcBlocks.TRANSIENT_ROD_DRIVE.get());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((TrigaBlockEntity) h.getBlockEntity(DRIVE)).pool(), "drive sees the pool"))
                .thenExecute(() -> {
                    TrigaCore c = ((TrigaBlockEntity) h.getBlockEntity(DRIVE)).core();
                    h.assertTrue(c.reset(), "scram reset over the pool");
                    // критичность на ваттах: регулирующие на критическом положении
                    c.setControlInsertion((TrigaCore.CORE_EXCESS - TrigaCore.TRANSIENT_ROD_WORTH) / TrigaCore.CONTROL_ROD_WORTH);
                    c.setSteady(50);
                    h.assertTrue(c.setPulseMode(true), "pulse mode below 1 kW");
                    c.setPulseReactivity(TrigaCore.TRANSIENT_ROD_WORTH);
                    h.assertTrue(c.arm(), "armed");
                })
                .thenWaitUntil(() -> h.assertBlockProperty(DRIVE, BlockStateProperties.ENABLED, true))
                .thenExecute(() -> h.assertTrue(((TrigaBlockEntity) h.getBlockEntity(DRIVE)).core().fire(), "fired"))
                .thenWaitUntil(() -> h.assertBlockProperty(DRIVE, BlockStateProperties.TRIGGERED, true))
                .thenIdle(30)
                .thenExecute(() -> {
                    TrigaCore c = ((TrigaBlockEntity) h.getBlockEntity(DRIVE)).core();
                    h.assertTrue(c.pulsePeakW() > TrigaBlockEntity.PULSE_ACHIEVEMENT_W, "peak " + c.pulsePeakW() / 1e6 + " MW");
                    h.assertTrue(!c.fuelDamaged(), "fuel intact at " + (c.pulsePeakFuelK() - 273.15) + " °C");
                    int[] out = panel(h);
                    h.assertTrue(out[TrigaPanels.T_PEAK_LOG100] >= 800, "desk shows the peak");
                    h.assertTrue((out[TrigaPanels.T_FLAGS] & 1 << TrigaPanels.TF_PULSE_MODE) != 0, "pulse mode lamp");
                    h.assertTrue(out[TrigaPanels.T_FWHM_MS10] > 0, "desk shows the width");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void driveWithoutAPoolStaysScrammed(GameTestHelper h) {
        h.setBlock(DRIVE, PcBlocks.TRANSIENT_ROD_DRIVE.get());
        h.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    TrigaBlockEntity be = h.getBlockEntity(DRIVE);
                    h.assertTrue(!be.pool(), "no water under the bridge");
                    h.assertTrue((panel(h)[TrigaPanels.T_FLAGS] & 1 << TrigaPanels.TF_NO_POOL) != 0, "no-pool lamp");
                    be.core().reset();
                })
                .thenIdle(2)
                .thenExecute(() -> h.assertTrue(((TrigaBlockEntity) h.getBlockEntity(DRIVE)).core().scrammed(), "dry core scrams again"))
                .thenSucceed();
    }
}
