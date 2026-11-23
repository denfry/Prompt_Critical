package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.ironworks.LadleBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import dev.denfry.promptcritical.sim.metallurgy.SteelMelt;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Часы выдержки (00 §0.4, {@code metallurgy.hold_k} = 4): ковш со сталью только ждёт, пока его везут к изложнице, и
 * остывает по ним, а не по K_slow = 72. 10 т стали теряют ≈ 6 К за минуту реального времени, а не ≈ 105 К.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LadleHoldGameTests {
    private LadleHoldGameTests() {}

    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 1300)
    public static void fullSteelLadleStaysLiquidForAMinuteOfRealTime(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        h.setBlock(p, PcBlocks.LADLE.get().defaultBlockState());
        LadleBlockEntity ladle = (LadleBlockEntity) h.getBlockEntity(p);
        // холодная футеровка снимает ≈ 110 К при наливе: в ковше ≈ 1880 К, сталь 0,1 % C застывает ниже ≈ 1762 К
        double took = ladle.receive(new SteelMelt(10_000, 1_990, Map.of(AlloyElement.C, 0.001)), true);
        h.assertTrue(took == 10_000, "the ladle takes the whole heat: " + took);
        double t0 = ladle.tempK();
        h.assertTrue(t0 > 1_850 && t0 < 1_900, "pour chill of a cold lining: " + t0);
        h.runAfterDelay(1_200, () -> {
            double drop = t0 - ladle.tempK();
            h.assertFalse(ladle.frozen(), "still liquid after a real minute at " + ladle.tempK() + " K");
            h.assertTrue(drop > 2 && drop < 15, "a real minute costs a few kelvin on the hold clock, not ~105: " + drop);
            h.succeed();
        });
    }
}
