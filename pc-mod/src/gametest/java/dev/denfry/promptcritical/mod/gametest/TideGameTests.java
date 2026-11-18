package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.TideService;
import dev.denfry.promptcritical.mod.powerhouse.TidalPanels;
import dev.denfry.promptcritical.mod.powerhouse.TidalUnitBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.TideGaugeBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.environment.Tide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Прилив в мире: мареограф показывает фазу Луны Minecraft и уровень по той же модели, что и сервис прилива; приливный
 * агрегат без перекрытого бассейна и без моря не вращает вал.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TideGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos GAUGE = new BlockPos(2, 1, 2), UNIT = new BlockPos(6, 2, 6);

    private TideGameTests() {
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void tideGaugeShowsTheMoonPhaseAndTheModelSeaLevel(GameTestHelper h) {
        h.setBlock(GAUGE, north(PcBlocks.TIDE_GAUGE.get().defaultBlockState()));
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    TideGaugeBlockEntity g = h.getBlockEntity(GAUGE);
                    int[] out = new int[TidalPanels.CHANNELS];
                    g.readPanel(out);
                    h.assertTrue(out[TidalPanels.MOON] == h.getLevel().getMoonPhase(), "moon phase of the sky");
                    BlockPos at = h.absolutePos(GAUGE);
                    double m2 = TideService.m2AmplitudeM(h.getLevel(), at);
                    h.assertTrue(out[TidalPanels.M2_CM] == (int) Math.round(m2 * 100), "M2 amplitude of the place");
                    Tide t = TideService.tide(h.getLevel(), at);
                    double expected = t == null ? 0 : t.heightM(Tide.hoursFromDayTime(h.getLevel().getDayTime()));
                    h.assertTrue(Math.abs(out[TidalPanels.SEA_CM] - expected * 100) <= 1, "sea level from the harmonics");
                    // уровень не выходит за сумму амплитуд составляющих
                    double bound = t == null ? 0 : 2.5 * m2;
                    h.assertTrue(Math.abs(expected) <= bound + 1e-9, "level within the constituent amplitudes");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void tidalUnitWithoutAClosedBasinStandsStill(GameTestHelper h) {
        h.setBlock(UNIT, north(PcBlocks.TIDAL_UNIT.get().defaultBlockState()));
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    TidalUnitBlockEntity u = h.getBlockEntity(UNIT);
                    h.assertTrue(u.plant() == null && u.basinArea() == 0, "no basin behind the dam in an empty template");
                    h.assertTrue(u.outputRpm() == 0, "the shaft stands still");
                    int[] out = new int[TidalPanels.CHANNELS];
                    u.readPanel(out);
                    h.assertTrue((out[TidalPanels.FLAGS] & 1 << TidalPanels.F_CLOSED) == 0, "panel reports the open basin");
                })
                .thenSucceed();
    }
}
